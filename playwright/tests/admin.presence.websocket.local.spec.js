const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://127.0.0.1:8081';
const wsURL = baseURL.replace(/^http/i, 'ws') + '/ws/chat';
const authBaseURL = process.env.AUTH_EMULATOR_URL || 'http://127.0.0.1:9099';
const localDir = path.resolve(__dirname, '..', '..', '.local');

const ADMIN_EMAIL = 'playwright-user1@local.dev';
const ADMIN_PASSWORD = 'Test1234!';
const TARGET_EMAIL = 'playwright-user2@local.dev';
const TARGET_PASSWORD = 'Test1234!';

function readJsonSafe(response) {
  return response.text().then((t) => {
    try { return JSON.parse(t); } catch { return t; }
  });
}

async function api(pathname, { method = 'GET', token, body, headers = {} } = {}) {
  const requestHeaders = { ...headers };
  if (token) requestHeaders.Authorization = `Bearer ${token}`;
  if (body !== undefined && !requestHeaders['Content-Type']) requestHeaders['Content-Type'] = 'application/json';
  const res = await fetch(`${baseURL}${pathname}`, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  return { status: res.status, body: await readJsonSafe(res) };
}

async function ensureEmulatorUserToken(email, password) {
  const signIn = async () => {
    const res = await fetch(`${authBaseURL}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, returnSecureToken: true })
    });
    return { status: res.status, body: await readJsonSafe(res) };
  };
  const signUp = async () => {
    const res = await fetch(`${authBaseURL}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, returnSecureToken: true })
    });
    return { status: res.status, body: await readJsonSafe(res) };
  };

  let result = await signIn();
  if (result.status === 200 && result.body?.idToken) return result.body.idToken;
  await signUp();
  result = await signIn();
  if (result.status !== 200 || !result.body?.idToken) {
    throw new Error(`Failed to get emulator token for ${email}`);
  }
  return result.body.idToken;
}

async function ensureBackendUser(token) {
  let login = await api('/api/auth/login', { method: 'POST', body: { firebaseIdToken: token } });
  if (login.status !== 200) {
    throw new Error(`backend login failed: ${JSON.stringify(login.body)}`);
  }
  if (login.body?.data?.id) {
    return login.body.data;
  }
  if (login.body?.data?.registrationStatus === 'PENDING_ONBOARDING') {
    const onboarding = await api('/api/auth/onboarding/complete', {
      method: 'POST',
      token,
      body: { x: 127.1086228, y: 37.4012191 }
    });
    if (onboarding.status !== 200) {
      throw new Error(`onboarding failed: ${JSON.stringify(onboarding.body)}`);
    }
    login = await api('/api/auth/login', { method: 'POST', body: { firebaseIdToken: token } });
    if (login.status !== 200 || !login.body?.data?.id) {
      throw new Error(`backend login retry failed: ${JSON.stringify(login.body)}`);
    }
    return login.body.data;
  }
  throw new Error(`unexpected login response: ${JSON.stringify(login.body)}`);
}

async function resolveAdminFirebaseToken() {
  const tokenCandidates = [];
  const candidateFiles = [
    'emulator-user1-firebase-id-token.txt',
    'emulator-firebase-id-token.txt'
  ];
  for (const name of candidateFiles) {
    const p = path.join(localDir, name);
    if (fs.existsSync(p)) {
      tokenCandidates.push(fs.readFileSync(p, 'utf8').trim());
    }
  }
  tokenCandidates.push(await ensureEmulatorUserToken(ADMIN_EMAIL, ADMIN_PASSWORD));

  for (const token of tokenCandidates) {
    if (!token) continue;
    const user = await ensureBackendUser(token);
    if (String(user?.email || '').toLowerCase() === ADMIN_EMAIL) {
      return token;
    }
  }
  throw new Error('No firebase token available for local admin email.');
}

test('admin presence topic receives PRESENCE_CHANGED without refresh', async ({ page }) => {
  const adminToken = await resolveAdminFirebaseToken();
  const adminUser = await ensureBackendUser(adminToken);
  expect(adminUser?.email?.toLowerCase()).toBe(ADMIN_EMAIL);
  const targetToken = await ensureEmulatorUserToken(TARGET_EMAIL, TARGET_PASSWORD);
  const targetUser = await ensureBackendUser(targetToken);

  await page.goto(`${baseURL}/login-flow.html`, { waitUntil: 'domcontentloaded' });

  const subscribePromise = page.evaluate(({ socketUrl, token }) => {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(socketUrl);
      const timeout = setTimeout(() => {
        try { ws.close(); } catch {}
        reject(new Error('timeout waiting for admin presence event'));
      }, 15000);

      const disconnect = () => {
        clearTimeout(timeout);
        try { ws.close(); } catch {}
      };

      const sendFrame = (command, headers = {}, body = '') => {
        const headerLines = Object.entries(headers).map(([k, v]) => `${k}:${v}`);
        const frame = `${command}\n${headerLines.join('\n')}\n\n${body}\u0000`;
        ws.send(frame);
      };

      ws.onopen = () => {
        sendFrame('CONNECT', {
          'accept-version': '1.2',
          host: location.host || 'localhost',
          Authorization: `Bearer ${token}`,
          'heart-beat': '0,0'
        });
      };

      ws.onerror = () => {
        disconnect();
        reject(new Error('websocket error'));
      };

      ws.onmessage = (event) => {
        const data = String(event.data || '');
        const frames = data.split('\u0000').filter(Boolean);
        for (const frame of frames) {
          const [head, ...bodyParts] = frame.split('\n\n');
          const lines = head.split('\n');
          const command = lines[0];
          const body = bodyParts.join('\n\n');
          if (command === 'CONNECTED') {
            sendFrame('SUBSCRIBE', { id: 'sub-admin-presence', destination: '/topic/admin/presence' });
            continue;
          }
          if (command === 'MESSAGE') {
            try {
              const payload = JSON.parse(body || '{}');
              disconnect();
              resolve(payload);
            } catch (e) {
              disconnect();
              reject(new Error(`invalid json payload: ${body}`));
            }
            return;
          }
          if (command === 'ERROR') {
            disconnect();
            reject(new Error(`stomp error: ${body}`));
            return;
          }
        }
      };
    });
  }, { socketUrl: wsURL, token: adminToken });

  const heartbeatRes = await api('/api/presence/heartbeat', {
    method: 'POST',
    token: targetToken,
    body: {
      clientSessionId: `pw-presence-${Date.now()}`,
      page: 'playwright-admin-presence-ws',
      reason: 'test'
    }
  });
  expect(heartbeatRes.status).toBe(200);

  const event = await subscribePromise;
  expect(event.type).toBe('PRESENCE_CHANGED');
  expect(event.userId).toBe(targetUser.id);
  expect(event).toHaveProperty('presence');
  expect(event).toHaveProperty('presenceConnectionState');
  expect(event).toHaveProperty('presenceLastActiveAt');
  expect(String(event.presenceConnectionState)).toBe('connected');
});

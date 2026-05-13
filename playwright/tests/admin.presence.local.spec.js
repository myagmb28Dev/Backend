const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const authBaseURL = process.env.AUTH_EMULATOR_URL || 'http://127.0.0.1:9099';
const localDir = path.resolve(__dirname, '..', '..', '.local');

const ADMIN_EMAIL = 'playwright-user1@local.dev';
const OTHER_USERS = ['playwright-user2@local.dev', 'playwright-user3@local.dev'];

function readJsonSafe(response) {
  return response.text().then((t) => {
    try { return JSON.parse(t); } catch { return t; }
  });
}

async function api(pathname, { method = 'GET', token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${baseURL}${pathname}`, {
    method,
    headers,
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

async function deleteEmulatorAccount(idToken) {
  const res = await fetch(`${authBaseURL}/identitytoolkit.googleapis.com/v1/accounts:delete?key=fake-api-key`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken })
  });
  return res.status;
}

async function ensureBackendUserId(token) {
  const first = await api('/api/auth/login', { method: 'POST', token, body: { firebaseIdToken: token } });
  if (first.status !== 200) {
    throw new Error(`Backend login failed: HTTP_${first.status}`);
  }
  const userId = first.body?.data?.id;
  if (userId) return userId;
  const registrationStatus = String(first.body?.data?.registrationStatus || '');
  if (registrationStatus !== 'PENDING_ONBOARDING') {
    throw new Error('Backend login did not return user id.');
  }
  const complete = await api('/api/auth/onboarding/complete', { method: 'POST', token, body: { x: 127.1086228, y: 37.4012191 } });
  if (complete.status !== 200) {
    throw new Error(`Onboarding complete failed: HTTP_${complete.status}`);
  }
  const second = await api('/api/auth/login', { method: 'POST', token, body: { firebaseIdToken: token } });
  if (second.status !== 200 || !second.body?.data?.id) {
    throw new Error('Backend login still missing user id after onboarding.');
  }
  return second.body.data.id;
}

async function installVirtualAuthenticator(page) {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('WebAuthn.enable');
  const { authenticatorId } = await cdp.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true
    }
  });
  return { cdp, authenticatorId };
}

async function createRegistrationCredential(page, rawOptions) {
  return page.evaluate(async (payload) => {
    const pick = (v) => (typeof v === 'string' ? v : v?.base64Url || v?.value);
    const toBuf = (base64url) => {
      const b64 = (base64url + '='.repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
      const bin = atob(b64);
      return Uint8Array.from(bin, (c) => c.charCodeAt(0)).buffer;
    };
    const toB64u = (input) => {
      const bytes = input instanceof ArrayBuffer ? new Uint8Array(input) : new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
      let binary = '';
      for (const byte of bytes) binary += String.fromCharCode(byte);
      return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
    };
    const credentialToJson = (value) => {
      if (value == null) return value;
      if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) return toB64u(value);
      if (value instanceof PublicKeyCredential) {
        return {
          id: value.id,
          type: value.type,
          rawId: credentialToJson(value.rawId),
          response: credentialToJson(value.response),
          authenticatorAttachment: value.authenticatorAttachment,
          clientExtensionResults: value.getClientExtensionResults()
        };
      }
      if (value instanceof AuthenticatorAttestationResponse) {
        return {
          clientDataJSON: credentialToJson(value.clientDataJSON),
          attestationObject: credentialToJson(value.attestationObject),
          transports: typeof value.getTransports === 'function' ? value.getTransports() : []
        };
      }
      if (Array.isArray(value)) return value.map(credentialToJson);
      if (typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, credentialToJson(v)]));
      return value;
    };
    const options = payload?.publicKey || payload;
    const publicKey = {
      ...options,
      challenge: toBuf(pick(options.challenge)),
      user: { ...options.user, id: toBuf(pick(options.user.id)) },
      excludeCredentials: (options.excludeCredentials || []).map((c) => ({ ...c, id: toBuf(pick(c.id)) }))
    };
    const cred = await navigator.credentials.create({ publicKey });
    return credentialToJson(cred);
  }, rawOptions);
}

async function createAssertionCredential(page, rawOptions) {
  return page.evaluate(async (payload) => {
    const pick = (v) => (typeof v === 'string' ? v : v?.base64Url || v?.value);
    const toBuf = (base64url) => {
      const b64 = (base64url + '='.repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
      const bin = atob(b64);
      return Uint8Array.from(bin, (c) => c.charCodeAt(0)).buffer;
    };
    const toB64u = (input) => {
      const bytes = input instanceof ArrayBuffer ? new Uint8Array(input) : new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
      let binary = '';
      for (const byte of bytes) binary += String.fromCharCode(byte);
      return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
    };
    const credentialToJson = (value) => {
      if (value == null) return value;
      if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) return toB64u(value);
      if (value instanceof PublicKeyCredential) {
        return {
          id: value.id,
          type: value.type,
          rawId: credentialToJson(value.rawId),
          response: credentialToJson(value.response),
          authenticatorAttachment: value.authenticatorAttachment,
          clientExtensionResults: value.getClientExtensionResults()
        };
      }
      if (value instanceof AuthenticatorAssertionResponse) {
        return {
          clientDataJSON: credentialToJson(value.clientDataJSON),
          authenticatorData: credentialToJson(value.authenticatorData),
          signature: credentialToJson(value.signature),
          userHandle: value.userHandle ? credentialToJson(value.userHandle) : null
        };
      }
      if (Array.isArray(value)) return value.map(credentialToJson);
      if (typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, credentialToJson(v)]));
      return value;
    };
    const options = payload?.publicKey || payload;
    const publicKey = {
      ...options,
      challenge: toBuf(pick(options.challenge)),
      allowCredentials: (options.allowCredentials || []).map((c) => ({ ...c, id: toBuf(pick(c.id)) }))
    };
    const cred = await navigator.credentials.get({ publicKey });
    return credentialToJson(cred);
  }, rawOptions);
}

test.describe.serial('admin presence local flow', () => {
  test.skip(({ browserName }) => browserName !== 'chromium', 'CDP WebAuthn virtual authenticator requires Chromium');

  let adminFirebaseIdToken;
  let bootstrapToken;
  let adminAccessToken;

  test.beforeAll(async () => {
    adminFirebaseIdToken = fs.readFileSync(path.join(localDir, 'emulator-firebase-id-token.txt'), 'utf8').trim();
  });

  test('seed: create other emulator users and send heartbeat', async () => {
    for (const email of OTHER_USERS) {
      const token = await ensureEmulatorUserToken(email, 'Test1234!');
      // ensure backend user exists
      try {
        await ensureBackendUserId(token);
      } catch (e) {
        console.warn('ensureBackendUserId failed', e.message);
      }
      // send heartbeat
      try {
        const res = await fetch(`${baseURL}/api/presence/heartbeat`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({ clientSessionId: `test-${email}` })
        });
        console.log('heartbeat', email, res.status);
      } catch (e) {
        console.warn('heartbeat call failed', e.message);
      }
    }
  });

  test('admin login via local passkey flow', async ({ page }) => {
    const login = await api('/api/admin/auth/local/login', {
      method: 'POST',
      body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: true }
    });
    expect(login.status).toBe(200);
    bootstrapToken = login.body.data.session.accessToken;
    expect(bootstrapToken).toBeTruthy();

    await page.goto(`${baseURL}/full_compact/pages/admin-flow.html`, { waitUntil: 'domcontentloaded' });
    const { cdp, authenticatorId } = await installVirtualAuthenticator(page);
    try {
      const options = await api('/api/admin/auth/passkeys/register/options', { method: 'POST', token: bootstrapToken });
      expect(options.status).toBe(200);
      const credential = await createRegistrationCredential(page, options.body.data.options ?? options.body.data.publicKey ?? options.body.data);
      const verify = await api('/api/admin/auth/passkeys/register/verify', { method: 'POST', token: bootstrapToken, body: { challengeId: options.body.data.challengeId, credential } });
      expect(verify.status).toBe(200);
      adminAccessToken = verify.body.data.accessToken;
      expect(adminAccessToken).toBeTruthy();
    } finally {
      await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId }).catch(() => {});
      await cdp.send('WebAuthn.disable').catch(() => {});
    }
  });

  test('admin sees presence fields for other users', async () => {
    const usersRes = await api('/api/admin/users?page=1&pageSize=100', { token: adminAccessToken });
    expect(usersRes.status).toBe(200);
    const items = Array.isArray(usersRes.body?.data?.items) ? usersRes.body.data.items : [];
    for (const email of OTHER_USERS) {
      const found = items.find(u => String(u?.email || '').toLowerCase() === email.toLowerCase());
      expect(found).toBeTruthy();
      expect(found).toHaveProperty('presence');
      expect(found).toHaveProperty('presenceConnectionState');
      expect(found).toHaveProperty('presenceLastActiveAt');
      console.log('presence for', email, JSON.stringify({ presence: found.presence, connection: found.presenceConnectionState, last: found.presenceLastActiveAt }));
    }
  });

  test.afterAll(async () => {
    // cleanup emulator accounts
    for (const email of OTHER_USERS) {
      try {
        const token = await ensureEmulatorUserToken(email, 'Test1234!');
        await api('/api/auth/withdraw', { method: 'DELETE', token }).catch(() => {});
        await deleteEmulatorAccount(token).catch(() => {});
      } catch (e) {
        console.warn('cleanup failed for', email, e.message);
      }
    }
  });
});

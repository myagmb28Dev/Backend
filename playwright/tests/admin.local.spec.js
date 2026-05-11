const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const authBaseURL = process.env.AUTH_EMULATOR_URL || 'http://127.0.0.1:9099';
const localDir = path.resolve(__dirname, '..', '..', '.local');

const ADMIN_EMAIL = 'playwright-user1@local.dev';

function readToken(fileName) {
  const tokenPath = path.join(localDir, fileName);
  if (!fs.existsSync(tokenPath)) throw new Error(`missing token file: ${tokenPath}`);
  return fs.readFileSync(tokenPath, 'utf8').trim();
}

async function readJsonSafe(response) {
  const text = await response.text();
  try { return JSON.parse(text); } catch { return text; }
}

async function api(pathname, { method = 'GET', token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${baseURL}${pathname}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  return { status: response.status, body: await readJsonSafe(response) };
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

test.describe.serial('admin local small-step flow', () => {
  test.skip(({ browserName }) => browserName !== 'chromium', 'CDP WebAuthn virtual authenticator requires Chromium');
  let adminFirebaseIdToken;
  let bootstrapToken;
  let adminAccessToken;

  test.beforeAll(async () => {
    adminFirebaseIdToken = readToken('emulator-user1-firebase-id-token.txt');
  });

  test('step1: local admin login returns passkey enrollment stage', async () => {
    const login = await api('/api/admin/auth/local/login', {
      method: 'POST',
      body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: true }
    });
    expect(login.status).toBe(200);
    expect(login.body?.data?.nextStep).toBe('PASSKEY_REGISTRATION_REQUIRED');
    bootstrapToken = login.body.data.session.accessToken;
    expect(bootstrapToken).toBeTruthy();
  });

  test('step2: passkey registration completes and authenticated session is issued', async ({ page }) => {
    await page.goto(`${baseURL}/full_compact/pages/admin-flow.html`, { waitUntil: 'domcontentloaded' });
    const { cdp, authenticatorId } = await installVirtualAuthenticator(page);
    try {
      const options = await api('/api/admin/auth/passkeys/register/options', {
        method: 'POST',
        token: bootstrapToken
      });
      expect(options.status).toBe(200);
      const credential = await createRegistrationCredential(page, options.body.data.options ?? options.body.data.publicKey ?? options.body.data);
      const verify = await api('/api/admin/auth/passkeys/register/verify', {
        method: 'POST',
        token: bootstrapToken,
        body: { challengeId: options.body.data.challengeId, credential }
      });
      expect(verify.status).toBe(200);
      expect(verify.body?.data?.stage).toBe('AUTHENTICATED');
      adminAccessToken = verify.body.data.accessToken;
      expect(adminAccessToken).toBeTruthy();
    } finally {
      await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId }).catch(() => {});
      await cdp.send('WebAuthn.disable').catch(() => {});
    }
  });

  test('step3: authenticated admin token can access admin APIs', async () => {
    const session = await api('/api/admin/auth/session', { token: adminAccessToken });
    expect(session.status).toBe(200);
    expect(session.body?.data?.authenticated).toBe(true);

    const reports = await api('/api/admin/reports?page=1&pageSize=5', { token: adminAccessToken });
    expect(reports.status).toBe(200);
    expect(Array.isArray(reports.body?.data?.items)).toBe(true);
  });

  test('step3b: admin refresh rotates token', async () => {
    const refresh = await api('/api/admin/auth/refresh', { method: 'POST', token: adminAccessToken });
    expect(refresh.status).toBe(200);
    expect(refresh.body?.data?.accessToken).toBeTruthy();
    adminAccessToken = refresh.body.data.accessToken;

    const session = await api('/api/admin/auth/session', { token: adminAccessToken });
    expect(session.status).toBe(200);
    expect(session.body?.data?.authenticated).toBe(true);
  });

  test('step3c: traffic logs include admin auth refresh req/res traces', async () => {
    const logs = await api('/api/admin/traffic/logs?limit=200', { token: adminAccessToken });
    expect(logs.status).toBe(200);
    const rows = Array.isArray(logs.body?.data) ? logs.body.data : [];
    const refreshRows = rows.filter((row) => String(row?.path || '').includes('/api/admin/auth/refresh'));
    expect(refreshRows.length).toBeGreaterThan(0);
    const inboundRefresh = refreshRows.find((row) => row?.direction === 'IN' && row?.method === 'POST');
    expect(Boolean(inboundRefresh)).toBe(true);
    console.log('[traffic-step3c] inboundRefresh=', JSON.stringify(inboundRefresh));
    // request/response body fields are expected to exist after traffic req/res capture enhancement.
    expect(Object.prototype.hasOwnProperty.call(inboundRefresh || {}, 'requestBody')).toBe(true);
    expect(Object.prototype.hasOwnProperty.call(inboundRefresh || {}, 'responseBody')).toBe(true);
  });

  test('step3d: promote by userId and load admin status', async ({ page }) => {
    await page.goto(`${baseURL}/full_compact/pages/admin-flow.html`, { waitUntil: 'domcontentloaded' });
    const { cdp, authenticatorId } = await installVirtualAuthenticator(page);
    try {
      // Ensure we have a fresh authenticator + passkey credential for step-up.
      const relogin = await api('/api/admin/auth/local/login', {
        method: 'POST',
        body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: true }
      });
      expect(relogin.status).toBe(200);
      const newBootstrapToken = relogin.body?.data?.session?.accessToken;
      expect(Boolean(newBootstrapToken)).toBe(true);

      const regOptions = await api('/api/admin/auth/passkeys/register/options', {
        method: 'POST',
        token: newBootstrapToken
      });
      expect(regOptions.status).toBe(200);
      const regCredential = await createRegistrationCredential(
        page,
        regOptions.body.data.options ?? regOptions.body.data.publicKey ?? regOptions.body.data
      );
      const regVerify = await api('/api/admin/auth/passkeys/register/verify', {
        method: 'POST',
        token: newBootstrapToken,
        body: { challengeId: regOptions.body.data.challengeId, credential: regCredential }
      });
      expect(regVerify.status).toBe(200);
      adminAccessToken = regVerify.body?.data?.accessToken;
      expect(Boolean(adminAccessToken)).toBe(true);

      const stepupOptions = await api('/api/admin/auth/stepup/options', {
        method: 'POST',
        token: adminAccessToken
      });
      expect(stepupOptions.status).toBe(200);
      const stepupCredential = await createAssertionCredential(
        page,
        stepupOptions.body.data.options ?? stepupOptions.body.data.publicKey ?? stepupOptions.body.data
      );
      const stepupVerify = await api('/api/admin/auth/stepup/verify', {
        method: 'POST',
        token: adminAccessToken,
        body: { challengeId: stepupOptions.body.data.challengeId, credential: stepupCredential }
      });
      expect(stepupVerify.status).toBe(200);
      adminAccessToken = stepupVerify.body?.data?.accessToken || adminAccessToken;

      const users = await api('/api/admin/users?page=1&pageSize=50', { token: adminAccessToken });
      expect(users.status).toBe(200);
      const items = Array.isArray(users.body?.data?.items) ? users.body.data.items : [];
      let promoteTarget = items.find((u) => String(u?.role || '').toUpperCase() !== 'ADMIN');
      if (!promoteTarget?.id) {
        const email = `playwright-promote-target-${Date.now()}@local.dev`;
        const token = await ensureEmulatorUserToken(email, 'Test1234!');
        let userId = null;
        try {
          userId = await ensureBackendUserId(token);

          const usersAgain = await api('/api/admin/users?page=1&pageSize=100', { token: adminAccessToken });
          expect(usersAgain.status).toBe(200);
          const itemsAgain = Array.isArray(usersAgain.body?.data?.items) ? usersAgain.body.data.items : [];
          promoteTarget = itemsAgain.find((u) => String(u?.id || '') === String(userId));
        } finally {
          await api('/api/auth/withdraw', { method: 'DELETE', token }).catch(() => {});
          await deleteEmulatorAccount(token).catch(() => {});
        }
      }
      expect(Boolean(promoteTarget?.id)).toBe(true);

      const promote = await api('/api/admin/users/promote', {
        method: 'PATCH',
        token: adminAccessToken,
        body: { userId: promoteTarget.id }
      });
      expect(promote.status).toBe(200);
      expect(String(promote.body?.data?.role || '').toUpperCase()).toBe('ADMIN');

      const status = await api('/api/admin/users/permissions/status', { token: adminAccessToken });
      expect(status.status).toBe(200);
      const admins = Array.isArray(status.body?.data?.admins) ? status.body.data.admins : [];
      expect(admins.some((admin) => admin?.userId === promoteTarget.id)).toBe(true);
    } finally {
      await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId }).catch(() => {});
      await cdp.send('WebAuthn.disable').catch(() => {});
    }
  });

  test('step3e: admin community detail endpoint returns 200', async () => {
    const create = await api('/api/community/posts', {
      method: 'POST',
      token: adminFirebaseIdToken,
      body: {
        title: `Admin detail test ${Date.now()}`,
        content: 'community detail 200 regression test',
        category: 'FREE',
        tags: ['admin', 'detail', 'test']
      }
    });
    expect(create.status).toBe(201);
    const postId = create.body?.data?.id;
    expect(Boolean(postId)).toBe(true);

    const list = await api('/api/admin/community/posts?page=1&pageSize=20', { token: adminAccessToken });
    expect(list.status).toBe(200);

    const detail = await api(`/api/admin/community/posts/${postId}`, { token: adminAccessToken });
    expect(detail.status).toBe(200);
    expect(detail.body?.data?.id).toBe(postId);
  });

  test('step4: logout then mfa passkey login works', async ({ page }) => {
    await page.goto(`${baseURL}/full_compact/pages/admin-flow.html`, { waitUntil: 'domcontentloaded' });
    const { cdp, authenticatorId } = await installVirtualAuthenticator(page);
    try {
      const logout = await api('/api/admin/auth/logout', { method: 'POST', token: adminAccessToken });
      expect(logout.status).toBe(200);

      const loginAgain = await api('/api/admin/auth/local/login', {
        method: 'POST',
        body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: false }
      });
      expect(loginAgain.status).toBe(200);
      const nextStep = loginAgain.body?.data?.nextStep;
      expect(['PASSKEY_REQUIRED', 'PASSKEY_REGISTRATION_REQUIRED']).toContain(nextStep);
      const pendingToken = loginAgain.body.data.session.accessToken;

      let verify;
      if (nextStep === 'PASSKEY_REQUIRED') {
        const options = await api('/api/admin/auth/mfa/options', {
          method: 'POST',
          token: pendingToken
        });
        expect(options.status).toBe(200);
        const credential = await createAssertionCredential(page, options.body.data.options ?? options.body.data.publicKey ?? options.body.data);
        verify = await api('/api/admin/auth/mfa/verify', {
          method: 'POST',
          token: pendingToken,
          body: { challengeId: options.body.data.challengeId, credential }
        });
      } else {
        const options = await api('/api/admin/auth/passkeys/register/options', {
          method: 'POST',
          token: pendingToken
        });
        expect(options.status).toBe(200);
        const credential = await createRegistrationCredential(page, options.body.data.options ?? options.body.data.publicKey ?? options.body.data);
        verify = await api('/api/admin/auth/passkeys/register/verify', {
          method: 'POST',
          token: pendingToken,
          body: { challengeId: options.body.data.challengeId, credential }
        });
      }
      expect(verify.status).toBe(200);
      expect(verify.body?.data?.stage).toBe('AUTHENTICATED');

      const session = await api('/api/admin/auth/session', { token: verify.body.data.accessToken });
      expect(session.status).toBe(200);
      expect(session.body?.data?.authenticated).toBe(true);
    } finally {
      await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId }).catch(() => {});
      await cdp.send('WebAuthn.disable').catch(() => {});
    }
  });
});

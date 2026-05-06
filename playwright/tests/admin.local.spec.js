const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
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
    await page.goto(`${baseURL}/admin-flow.html`, { waitUntil: 'domcontentloaded' });
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

  test('step4: logout then mfa passkey login works', async ({ page }) => {
    await page.goto(`${baseURL}/admin-flow.html`, { waitUntil: 'domcontentloaded' });
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

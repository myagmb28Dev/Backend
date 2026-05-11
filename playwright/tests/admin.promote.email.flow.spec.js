const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const ADMIN_EMAIL = process.env.ADMIN_LOCAL_EMAIL || 'myagmb28s@gmail.com';

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

test('admin promote-by-email flow works and target reaches passkey stage', async ({ browserName }) => {
  test.skip(browserName !== 'chromium', 'CDP WebAuthn virtual authenticator requires Chromium');
  const pageUrl = `${baseURL}/full_compact/pages/admin-flow.html`;

  const login = await api('/api/admin/auth/local/login', {
    method: 'POST',
    body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: true }
  });
  expect(login.status).toBe(200);
  expect(login.body?.data?.nextStep).toBe('PASSKEY_REGISTRATION_REQUIRED');
  const bootstrapToken = login.body?.data?.session?.accessToken;
  expect(Boolean(bootstrapToken)).toBe(true);

  const { chromium } = require('playwright');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  await page.goto(pageUrl, { waitUntil: 'domcontentloaded' });
  const { cdp, authenticatorId } = await installVirtualAuthenticator(page);
  let adminToken = null;
  try {
    const regOptions = await api('/api/admin/auth/passkeys/register/options', {
      method: 'POST',
      token: bootstrapToken
    });
    expect(regOptions.status).toBe(200);
    const regCredential = await createRegistrationCredential(
      page,
      regOptions.body?.data?.publicKey?.publicKey ?? regOptions.body?.data?.options ?? regOptions.body?.data?.publicKey ?? regOptions.body?.data
    );
    const regVerify = await api('/api/admin/auth/passkeys/register/verify', {
      method: 'POST',
      token: bootstrapToken,
      body: { challengeId: regOptions.body?.data?.challengeId, credential: regCredential }
    });
    expect(regVerify.status).toBe(200);
    adminToken = regVerify.body?.data?.accessToken;
    expect(Boolean(adminToken)).toBe(true);

    const stepupOptions = await api('/api/admin/auth/stepup/options', {
      method: 'POST',
      token: adminToken
    });
    expect(stepupOptions.status).toBe(200);
    const stepupCredential = await createAssertionCredential(
      page,
      stepupOptions.body?.data?.publicKey?.publicKey ?? stepupOptions.body?.data?.options ?? stepupOptions.body?.data?.publicKey ?? stepupOptions.body?.data
    );
    const stepupVerify = await api('/api/admin/auth/stepup/verify', {
      method: 'POST',
      token: adminToken,
      body: { challengeId: stepupOptions.body?.data?.challengeId, credential: stepupCredential }
    });
    expect(stepupVerify.status).toBe(200);
    adminToken = stepupVerify.body?.data?.accessToken || adminToken;
  } finally {
    await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId }).catch(() => {});
    await cdp.send('WebAuthn.disable').catch(() => {});
    await browser.close();
  }
  expect(Boolean(adminToken)).toBe(true);

  const targetEmail = `auto-promote-${Date.now()}@local.dev`;

  const promote = await api('/api/admin/users/promote/email', {
    method: 'POST',
    token: adminToken,
    body: { email: targetEmail }
  });
  expect(promote.status).toBe(200);
  expect(promote.body?.data?.email).toBe(targetEmail);
  expect(promote.body?.data?.role).toBe('ADMIN');
  expect(Boolean(promote.body?.data?.userId)).toBe(true);

  const targetLogin = await api('/api/admin/auth/local/login', {
    method: 'POST',
    body: { localTestEmail: targetEmail, forcePasskeyEnroll: false }
  });
  expect(targetLogin.status).toBe(200);
  const nextStep = targetLogin.body?.data?.nextStep;
  expect(nextStep).toBe('PASSKEY_REGISTRATION_REQUIRED');

  console.log('[admin-promote-email-flow]', JSON.stringify({
    targetEmail,
    targetUserId: promote.body?.data?.userId,
    nextStep
  }, null, 2));
});

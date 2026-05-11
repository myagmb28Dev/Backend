const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const authBaseURL = process.env.AUTH_EMULATOR_URL || 'http://127.0.0.1:9099';
const projectId = process.env.FIREBASE_AUTH_EMULATOR_PROJECT_ID || process.env.APP_FIREBASE_AUTH_EMULATOR_PROJECT_ID || 'pogun-local';
const ADMIN_EMAIL = process.env.ADMIN_LOCAL_EMAIL || 'playwright-user1@local.dev';
const TARGET_EMAIL = process.env.TARGET_PROMOTE_EMAIL || `auto-promote-flow-${Date.now()}@local.dev`;

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

async function authEmulator(pathname, body) {
  const response = await fetch(`${authBaseURL}${pathname}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return { status: response.status, body: await readJsonSafe(response) };
}

async function signInGoogle(email, { emailVerified = true } = {}) {
  const subject = `google-${email.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}`;
  const idpPayload = {
    sub: subject,
    email,
    email_verified: emailVerified,
    name: `Auto Promote ${email.split('@')[0]}`,
    picture: 'https://example.com/admin.png'
  };
  const postBody = `providerId=google.com&id_token=${encodeURIComponent(JSON.stringify(idpPayload))}`;
  const result = await authEmulator('/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=fake-api-key', {
    requestUri: baseURL,
    postBody,
    returnIdpCredential: true,
    returnSecureToken: true
  });
  expect(result.status).toBe(200);
  expect(result.body?.idToken).toBeTruthy();
  return result.body;
}

async function lookupEmailVerified(idToken) {
  const result = await authEmulator('/identitytoolkit.googleapis.com/v1/accounts:lookup?key=fake-api-key', { idToken });
  expect(result.status).toBe(200);
  return Boolean(result.body?.users?.[0]?.emailVerified);
}

async function applyLatestVerificationEmail(email) {
  const response = await fetch(`${authBaseURL}/emulator/v1/projects/${projectId}/oobCodes`);
  expect(response.status).toBe(200);
  const body = await readJsonSafe(response);
  const codes = Array.isArray(body?.oobCodes) ? body.oobCodes : [];
  const latest = codes
    .filter((code) => code?.email === email && code?.requestType === 'VERIFY_EMAIL')
    .at(-1);
  expect(latest?.oobLink).toBeTruthy();
  const verifyResponse = await fetch(latest.oobLink, { redirect: 'manual' });
  expect([200, 302, 303, 307, 308]).toContain(verifyResponse.status);
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

test('promoted google email must receive verification before passkey enrollment', async ({ page, browserName }) => {
  test.skip(browserName !== 'chromium', 'CDP WebAuthn virtual authenticator requires Chromium');
  const login = await api('/api/admin/auth/local/login', {
    method: 'POST',
    body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: true }
  });
  expect(login.status).toBe(200);
  expect(login.body?.data?.nextStep).toBe('PASSKEY_REGISTRATION_REQUIRED');
  let adminToken = login.body?.data?.session?.accessToken;
  expect(adminToken).toBeTruthy();

  await page.goto(`${baseURL}/full_compact/pages/admin-flow.html`, { waitUntil: 'domcontentloaded' });
  const { cdp, authenticatorId } = await installVirtualAuthenticator(page);
  try {
    const regOptions = await api('/api/admin/auth/passkeys/register/options', {
      method: 'POST',
      token: adminToken
    });
    expect(regOptions.status).toBe(200);
    const regCredential = await createRegistrationCredential(
      page,
      regOptions.body?.data?.publicKey?.publicKey ?? regOptions.body?.data?.options ?? regOptions.body?.data?.publicKey ?? regOptions.body?.data
    );
    const regVerify = await api('/api/admin/auth/passkeys/register/verify', {
      method: 'POST',
      token: adminToken,
      body: { challengeId: regOptions.body?.data?.challengeId, credential: regCredential }
    });
    expect(regVerify.status).toBe(200);
    adminToken = regVerify.body?.data?.accessToken;
    expect(adminToken).toBeTruthy();

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
  }

  const promote = await api('/api/admin/users/promote/email', {
    method: 'POST',
    token: adminToken,
    body: { email: TARGET_EMAIL }
  });
  expect(promote.status).toBe(200);
  expect(promote.body?.data?.email).toBe(TARGET_EMAIL);
  expect(promote.body?.data?.role).toBe('ADMIN');

  const firstGoogleLogin = await signInGoogle(TARGET_EMAIL, { emailVerified: true });
  expect(await lookupEmailVerified(firstGoogleLogin.idToken)).toBe(true);

  const firstAdminLogin = await api('/api/admin/auth/login', {
    method: 'POST',
    body: { firebaseIdToken: firstGoogleLogin.idToken }
  });
  expect(firstAdminLogin.status).toBe(200);
  expect(firstAdminLogin.body?.data?.nextStep).toBe('EMAIL_VERIFICATION_REQUIRED');
  expect(firstAdminLogin.body?.data?.requiresPassKey).toBe(false);
  expect(await lookupEmailVerified(firstGoogleLogin.idToken)).toBe(false);

  await applyLatestVerificationEmail(TARGET_EMAIL);

  const secondGoogleLogin = await signInGoogle(TARGET_EMAIL, { emailVerified: true });
  expect(await lookupEmailVerified(secondGoogleLogin.idToken)).toBe(true);
  const secondAdminLogin = await api('/api/admin/auth/login', {
    method: 'POST',
    body: { firebaseIdToken: secondGoogleLogin.idToken }
  });
  expect(secondAdminLogin.status).toBe(200);
  expect(secondAdminLogin.body?.data?.nextStep).toBe('PASSKEY_REGISTRATION_REQUIRED');
  expect(secondAdminLogin.body?.data?.requiresPassKey).toBe(true);

  console.log('[admin-promote-google-email-verification]', JSON.stringify({
    targetEmail: TARGET_EMAIL,
    targetUserId: promote.body?.data?.userId,
    firstStep: firstAdminLogin.body?.data?.nextStep,
    secondStep: secondAdminLogin.body?.data?.nextStep
  }, null, 2));
});

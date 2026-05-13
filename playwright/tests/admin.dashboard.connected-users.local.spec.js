const { test, expect } = require("@playwright/test");

const BASE_URL = process.env.BASE_URL || "http://localhost:8081";
const ADMIN_EMAIL = process.env.ADMIN_LOCAL_EMAIL || "playwright-user1@local.dev";
const ADMIN_PASSWORD = process.env.ADMIN_LOCAL_PASSWORD || "Test1234!";
const AUTH_EMULATOR_URL = process.env.AUTH_EMULATOR_URL || "http://127.0.0.1:9099";

async function readJsonSafe(response) {
  const text = await response.text();
  try { return JSON.parse(text); } catch { return text; }
}

async function api(request, pathname, { method = "GET", token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await request.fetch(`${BASE_URL}${pathname}`, {
    method,
    headers,
    data: body,
  });
  return { status: res.status(), body: await readJsonSafe(res) };
}

async function installVirtualAuthenticator(page) {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("WebAuthn.enable");
  const { authenticatorId } = await cdp.send("WebAuthn.addVirtualAuthenticator", {
    options: {
      protocol: "ctap2",
      transport: "internal",
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  });
  return { cdp, authenticatorId };
}

async function createRegistrationCredential(page, rawOptions) {
  return page.evaluate(async (payload) => {
    const pick = (v) => (typeof v === "string" ? v : v?.base64Url || v?.value);
    const toBuf = (base64url) => {
      const b64 = (base64url + "=".repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, "+").replace(/_/g, "/");
      const bin = atob(b64);
      return Uint8Array.from(bin, (c) => c.charCodeAt(0)).buffer;
    };
    const toB64u = (input) => {
      const bytes = input instanceof ArrayBuffer ? new Uint8Array(input) : new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
      let binary = "";
      for (const byte of bytes) binary += String.fromCharCode(byte);
      return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
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
          clientExtensionResults: value.getClientExtensionResults(),
        };
      }
      if (value instanceof AuthenticatorAttestationResponse) {
        return {
          clientDataJSON: credentialToJson(value.clientDataJSON),
          attestationObject: credentialToJson(value.attestationObject),
          transports: typeof value.getTransports === "function" ? value.getTransports() : [],
        };
      }
      if (Array.isArray(value)) return value.map(credentialToJson);
      if (typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, credentialToJson(v)]));
      return value;
    };
    const options = payload?.publicKey || payload;
    const publicKey = {
      ...options,
      challenge: toBuf(pick(options.challenge)),
      user: { ...options.user, id: toBuf(pick(options.user.id)) },
      excludeCredentials: (options.excludeCredentials || []).map((c) => ({ ...c, id: toBuf(pick(c.id)) })),
    };
    const cred = await navigator.credentials.create({ publicKey });
    return credentialToJson(cred);
  }, rawOptions);
}

async function createAssertionCredential(page, rawOptions) {
  return page.evaluate(async (payload) => {
    const pick = (v) => (typeof v === "string" ? v : v?.base64Url || v?.value);
    const toBuf = (base64url) => {
      const b64 = (base64url + "=".repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, "+").replace(/_/g, "/");
      const bin = atob(b64);
      return Uint8Array.from(bin, (c) => c.charCodeAt(0)).buffer;
    };
    const toB64u = (input) => {
      const bytes = input instanceof ArrayBuffer ? new Uint8Array(input) : new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
      let binary = "";
      for (const byte of bytes) binary += String.fromCharCode(byte);
      return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
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
          clientExtensionResults: value.getClientExtensionResults(),
        };
      }
      if (value instanceof AuthenticatorAssertionResponse) {
        return {
          clientDataJSON: credentialToJson(value.clientDataJSON),
          authenticatorData: credentialToJson(value.authenticatorData),
          signature: credentialToJson(value.signature),
          userHandle: value.userHandle ? credentialToJson(value.userHandle) : null,
        };
      }
      if (Array.isArray(value)) return value.map(credentialToJson);
      if (typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, credentialToJson(v)]));
      return value;
    };
    const options = payload?.publicKey || payload;
    const publicKey = {
      ...options,
      challenge: toBuf(pick(options.challenge)),
      allowCredentials: (options.allowCredentials || []).map((c) => ({ ...c, id: toBuf(pick(c.id)) })),
    };
    const cred = await navigator.credentials.get({ publicKey });
    return credentialToJson(cred);
  }, rawOptions);
}

async function authenticateAdminSession(request, page) {
  const login = await api(request, "/api/admin/auth/local/login", {
    method: "POST",
    body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: false },
  });
  expect(login.status).toBe(200);
  const nextStep = login.body?.data?.nextStep;
  const pendingToken = login.body?.data?.session?.accessToken;
  expect(Boolean(pendingToken)).toBe(true);

  const { cdp, authenticatorId } = await installVirtualAuthenticator(page);
  try {
    if (nextStep === "PASSKEY_REQUIRED") {
      const options = await api(request, "/api/admin/auth/mfa/options", {
        method: "POST",
        token: pendingToken,
      });
      expect(options.status).toBe(200);
      const credential = await createAssertionCredential(page, options.body.data.options ?? options.body.data.publicKey ?? options.body.data);
      const verify = await api(request, "/api/admin/auth/mfa/verify", {
        method: "POST",
        token: pendingToken,
        body: { challengeId: options.body.data.challengeId, credential },
      });
      expect(verify.status).toBe(200);
      expect(verify.body?.data?.stage).toBe("AUTHENTICATED");
      return verify.body?.data?.accessToken;
    }

    if (nextStep === "PASSKEY_REGISTRATION_REQUIRED") {
      const options = await api(request, "/api/admin/auth/passkeys/register/options", {
        method: "POST",
        token: pendingToken,
      });
      expect(options.status).toBe(200);
      const credential = await createRegistrationCredential(page, options.body.data.options ?? options.body.data.publicKey ?? options.body.data);
      const verify = await api(request, "/api/admin/auth/passkeys/register/verify", {
        method: "POST",
        token: pendingToken,
        body: { challengeId: options.body.data.challengeId, credential },
      });
      expect(verify.status).toBe(200);
      expect(verify.body?.data?.stage).toBe("AUTHENTICATED");
      return verify.body?.data?.accessToken;
    }

    throw new Error(`unexpected admin login step: ${nextStep}`);
  } finally {
    await cdp.send("WebAuthn.removeVirtualAuthenticator", { authenticatorId }).catch(() => {});
    await cdp.send("WebAuthn.disable").catch(() => {});
  }
}

async function ensureEmulatorFirebaseToken(email, password) {
  const signInUrl = `${AUTH_EMULATOR_URL}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`;
  const signUpUrl = `${AUTH_EMULATOR_URL}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`;
  const payload = { email, password, returnSecureToken: true };

  const signInRes = await fetch(signInUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const signInBody = await readJsonSafe(signInRes);
  if (signInRes.status === 200 && signInBody?.idToken) return signInBody.idToken;

  await fetch(signUpUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const retryRes = await fetch(signInUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const retryBody = await readJsonSafe(retryRes);
  if (retryRes.status === 200 && retryBody?.idToken) return retryBody.idToken;
  throw new Error(`failed to resolve firebase token: ${JSON.stringify(retryBody)}`);
}

test("dashboard summary returns connectedUsers >= 1 for authenticated admin", async ({ page, request }) => {
  await page.goto(`${BASE_URL}/full_compact/pages/admin-flow.html`, { waitUntil: "domcontentloaded" });
  const adminAccessToken = await authenticateAdminSession(request, page);
  expect(Boolean(adminAccessToken)).toBe(true);

  const firebaseIdToken = await ensureEmulatorFirebaseToken(ADMIN_EMAIL, ADMIN_PASSWORD);
  const backendLogin = await api(request, "/api/auth/login", {
    method: "POST",
    body: { firebaseIdToken },
    token: firebaseIdToken,
  });
  expect(backendLogin.status).toBe(200);
  const heartbeat = await api(request, "/api/presence/heartbeat", {
    method: "POST",
    token: firebaseIdToken,
    body: {
      clientSessionId: `pw-dashboard-${Date.now()}`,
      page: "playwright-dashboard-summary",
      reason: "test",
    },
  });
  expect(heartbeat.status).toBe(200);

  const summary = await api(request, "/api/admin/dashboard/summary", {
    method: "GET",
    token: adminAccessToken,
  });
  expect(summary.status).toBe(200);
  expect(summary.body?.ok).toBe(true);
  expect(Number(summary.body?.data?.connectedUsers ?? 0)).toBeGreaterThanOrEqual(1);
});

import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

function toB64Url(bytes) {
  const buf = Buffer.from(bytes);
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

const authBaseURL = process.env.AUTH_EMULATOR_URL || 'http://127.0.0.1:9099';

async function deleteEmulatorAccount(idToken) {
  const res = await fetch(`${authBaseURL}/identitytoolkit.googleapis.com/v1/accounts:delete?key=fake-api-key`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken })
  });
  // Emulator may return 200 even if already deleted; ignore failures in cleanup.
  return res.status;
}

async function ensureEmulatorUserToken(email, password) {
  const signIn = async () => {
    const res = await fetch(`${authBaseURL}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, returnSecureToken: true })
    });
    return { status: res.status, body: await res.json().catch(() => ({})) };
  };
  const signUp = async () => {
    const res = await fetch(`${authBaseURL}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password, returnSecureToken: true })
    });
    return { status: res.status, body: await res.json().catch(() => ({})) };
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

async function ensureBackendUserId(request, token) {
  const firstRes = await request.post('http://localhost:8081/api/auth/login', {
    headers: { Authorization: `Bearer ${token}` },
    data: { firebaseIdToken: token }
  });
  if (firstRes.status() !== 200) {
    throw new Error(`backend login failed: HTTP_${firstRes.status()}`);
  }
  const firstJson = await firstRes.json();
  if (firstJson?.data?.id) return firstJson.data.id;
  if (String(firstJson?.data?.registrationStatus || '') !== 'PENDING_ONBOARDING') {
    throw new Error('backend login missing id');
  }
  const complete = await request.post('http://localhost:8081/api/auth/onboarding/complete', {
    headers: { Authorization: `Bearer ${token}` },
    data: { x: 127.1086228, y: 37.4012191 }
  });
  if (complete.status() !== 200) {
    throw new Error(`onboarding complete failed: HTTP_${complete.status()}`);
  }
  const secondRes = await request.post('http://localhost:8081/api/auth/login', {
    headers: { Authorization: `Bearer ${token}` },
    data: { firebaseIdToken: token }
  });
  if (secondRes.status() !== 200) {
    throw new Error(`backend login retry failed: HTTP_${secondRes.status()}`);
  }
  const secondJson = await secondRes.json();
  if (!secondJson?.data?.id) {
    throw new Error('backend login still missing id after onboarding');
  }
  return secondJson.data.id;
}

test('admin notification target=all returns 200 after passkey verify', async ({ page, context, request, browserName }) => {
  test.skip(browserName !== 'chromium', 'CDP WebAuthn virtual authenticator requires Chromium');
  const loginRes = await request.post('http://localhost:8081/api/admin/auth/local/login', {
    data: {}
  });
  expect(loginRes.status()).toBe(200);
  const loginJson = await loginRes.json();
  const bootstrapToken = loginJson?.data?.session?.accessToken;
  expect(bootstrapToken).toBeTruthy();

  const optionsRes = await request.post('http://localhost:8081/api/admin/auth/passkeys/register/options', {
    headers: { Authorization: `Bearer ${bootstrapToken}` },
    data: {}
  });
  expect(optionsRes.status()).toBe(200);
  const optionsJson = await optionsRes.json();
  const challengeId = optionsJson?.data?.challengeId;
  const publicKey = optionsJson?.data?.publicKey?.publicKey || optionsJson?.data?.publicKey;
  expect(challengeId).toBeTruthy();
  expect(publicKey?.challenge).toBeTruthy();

  const cdp = await context.newCDPSession(page);
  await cdp.send('WebAuthn.enable');
  await cdp.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true
    }
  });
  await page.goto('http://localhost:8081/full_compact/pages/admin-flow.html', { waitUntil: 'domcontentloaded' });

  const credential = await page.evaluate(async (pk) => {
    const b64urlToBytes = (base64url) => {
      const pad = '='.repeat((4 - (base64url.length % 4)) % 4);
      const b64 = (base64url + pad).replace(/-/g, '+').replace(/_/g, '/');
      const bin = atob(b64);
      const out = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i += 1) out[i] = bin.charCodeAt(i);
      return out;
    };
    const normalized = {
      ...pk,
      challenge: b64urlToBytes(pk.challenge),
      user: { ...pk.user, id: b64urlToBytes(pk.user.id) },
      excludeCredentials: (pk.excludeCredentials || []).map((c) => ({ ...c, id: b64urlToBytes(c.id) }))
    };
    const cred = await navigator.credentials.create({ publicKey: normalized });
    return {
      id: cred.id,
      rawId: new Uint8Array(cred.rawId),
      type: cred.type,
      response: {
        clientDataJSON: new Uint8Array(cred.response.clientDataJSON),
        attestationObject: new Uint8Array(cred.response.attestationObject),
        transports: typeof cred.response.getTransports === 'function' ? cred.response.getTransports() : []
      },
      authenticatorAttachment: cred.authenticatorAttachment,
      clientExtensionResults: cred.getClientExtensionResults()
    };
  }, publicKey);

  const credentialPayload = {
    id: credential.id,
    rawId: toB64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: toB64Url(credential.response.clientDataJSON),
      attestationObject: toB64Url(credential.response.attestationObject),
      transports: credential.response.transports || []
    },
    authenticatorAttachment: credential.authenticatorAttachment,
    clientExtensionResults: credential.clientExtensionResults || {}
  };

  const verifyRes = await request.post('http://localhost:8081/api/admin/auth/passkeys/register/verify', {
    headers: { Authorization: `Bearer ${bootstrapToken}` },
    data: { challengeId, credential: credentialPayload }
  });
  expect(verifyRes.status()).toBe(200);
  const verifyJson = await verifyRes.json();
  const adminToken = verifyJson?.data?.accessToken || bootstrapToken;

  const myUserId = loginJson?.data?.session?.admin?.id;
  expect(myUserId).toBeTruthy();

  const tokenPath = path.resolve('D:/Codes/Pogun_Back/.local/emulator-user2-firebase-id-token.txt');
  const useFileToken = fs.existsSync(tokenPath);
  const user2Email = `playwright-user2-${Date.now()}@local.dev`;
  const user2Token = useFileToken
    ? fs.readFileSync(tokenPath, 'utf-8').trim()
    : await ensureEmulatorUserToken(user2Email, 'Test1234!');
  try {
    const normalUserId = await ensureBackendUserId(request, user2Token);
    expect(normalUserId).toBeTruthy();

  const sendRes = await request.post('http://localhost:8081/api/admin/notifications/send', {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: {
      target: 'all',
      title: 'Playwright 전체 알림 테스트',
      body: '500 회귀 테스트'
    }
  });

  expect(sendRes.status(), await sendRes.text()).toBe(200);
  const sendJson = await sendRes.json();
  expect(sendJson?.ok).toBe(true);
  expect(sendJson?.data?.targetCount).toBeGreaterThanOrEqual(1);
  expect(sendJson?.data?.deliveredUserCount).toBeGreaterThanOrEqual(0);
  expect(sendJson?.data?.skippedCount).toBeGreaterThanOrEqual(0);
  expect(sendJson?.data?.failedTokenCount).toBeGreaterThanOrEqual(0);
  expect(sendJson?.data?.failedCount).toBeGreaterThanOrEqual(0);

  const specificRes = await request.post('http://localhost:8081/api/admin/notifications/send', {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: {
      target: 'specific',
      title: 'Playwright 특정 알림 테스트',
      body: 'specific 200 회귀 테스트',
      userIds: [myUserId]
    }
  });
  expect(specificRes.status(), await specificRes.text()).toBe(200);
  const specificJson = await specificRes.json();
  expect(specificJson?.ok).toBe(true);
  expect(specificJson?.data?.targetCount).toBe(1);
  expect(specificJson?.data?.deliveredUserCount).toBeGreaterThanOrEqual(0);
  expect(specificJson?.data?.skippedCount).toBeGreaterThanOrEqual(0);
  expect(specificJson?.data?.failedTokenCount).toBeGreaterThanOrEqual(0);

  const specificNormalUserRes = await request.post('http://localhost:8081/api/admin/notifications/send', {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: {
      target: 'specific',
      title: 'Playwright 일반유저 특정 알림 테스트',
      body: '일반 사용자 ID 대상',
      userIds: [normalUserId]
    }
  });
  expect(specificNormalUserRes.status(), await specificNormalUserRes.text()).toBe(200);
  const specificNormalUserJson = await specificNormalUserRes.json();
  expect(specificNormalUserJson?.ok).toBe(true);
  expect(specificNormalUserJson?.data?.targetCount).toBe(1);
  expect(specificNormalUserJson?.data?.deliveredUserCount).toBeGreaterThanOrEqual(0);
  expect(specificNormalUserJson?.data?.skippedCount).toBeGreaterThanOrEqual(0);
  expect(specificNormalUserJson?.data?.failedTokenCount).toBeGreaterThanOrEqual(0);

  const specificMissingUsersRes = await request.post('http://localhost:8081/api/admin/notifications/send', {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: {
      target: 'specific',
      title: 'Playwright 특정 알림 실패 테스트',
      body: 'userIds 누락 검증'
    }
  });
  expect(specificMissingUsersRes.status(), await specificMissingUsersRes.text()).toBe(400);
  const specificMissingUsersJson = await specificMissingUsersRes.json();
  expect(specificMissingUsersJson?.error?.code).toBe('MISSING_NOTIFICATION_USERS');
  } finally {
    if (!useFileToken) {
      await request.delete('http://localhost:8081/api/auth/withdraw', {
        headers: { Authorization: `Bearer ${user2Token}` }
      }).catch(() => {});
      await deleteEmulatorAccount(user2Token).catch(() => {});
    }
  }
});

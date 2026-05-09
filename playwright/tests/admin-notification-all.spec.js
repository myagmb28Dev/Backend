import { test, expect } from '@playwright/test';
import fs from 'node:fs';

function toB64Url(bytes) {
  const buf = Buffer.from(bytes);
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

test('admin notification target=all returns 200 after passkey verify', async ({ page, context, request }) => {
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
  await page.goto('http://localhost:8081/admin-flow.html', { waitUntil: 'domcontentloaded' });

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

  const user2Token = fs.readFileSync('D:/Codes/Pogun_Back/.local/emulator-user2-firebase-id-token.txt', 'utf-8').trim();
  const user2LoginRes = await request.post('http://localhost:8081/api/auth/login', {
    headers: { Authorization: `Bearer ${user2Token}` },
    data: { firebaseIdToken: user2Token }
  });
  expect(user2LoginRes.status()).toBe(200);
  const user2LoginJson = await user2LoginRes.json();
  const normalUserId = user2LoginJson?.data?.id;
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
});

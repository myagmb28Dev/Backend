const fs = require('fs');
const path = require('path');
const { test, expect, request } = require('@playwright/test');

const repoRoot = path.resolve(__dirname, '..', '..');
const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const tokenPath = path.join(repoRoot, '.local', 'emulator-user1-firebase-id-token.txt');

function loadToken() {
  if (!fs.existsSync(tokenPath)) {
    throw new Error(`Token file not found: ${tokenPath}`);
  }
  return fs.readFileSync(tokenPath, 'utf8').trim();
}

test('user1 location update api works', async () => {
  const firebaseIdToken = loadToken();
  const api = await request.newContext({ baseURL });

  const loginRes = await api.post('/api/auth/login', {
    data: { firebaseIdToken }
  });
  expect(loginRes.status()).toBe(200);
  const loginJson = await loginRes.json();
  expect(loginJson?.ok).toBeTruthy();

  const updateRes = await api.patch('/api/users/me/location', {
    headers: {
      Authorization: `Bearer ${firebaseIdToken}`,
      'Content-Type': 'application/json'
    },
    data: {
      x: 127.1086228,
      y: 37.4012191
    }
  });
  expect(updateRes.status()).toBe(200);

  const updateJson = await updateRes.json();
  expect(updateJson?.ok).toBeTruthy();
  expect(updateJson?.data?.region).toBeTruthy();
  expect(updateJson?.data?.regionInfo).toBeTruthy();
  expect(updateJson?.data?.regionInfo?.region2DepthName).toBeTruthy();

  await api.dispose();
});


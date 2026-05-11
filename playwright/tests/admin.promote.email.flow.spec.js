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

test('admin promote-by-email flow works and target reaches passkey stage', async () => {
  const login = await api('/api/admin/auth/local/login', {
    method: 'POST',
    body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: false }
  });
  expect(login.status).toBe(200);
  const adminToken = login.body?.data?.session?.accessToken;
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


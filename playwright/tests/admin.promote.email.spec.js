const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const ADMIN_EMAIL = 'playwright-user1@local.dev';
const TARGET_EMAIL = 'playwright-user2@local.dev';

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

test('admin promote by email auto-creates user', async () => {
  const login = await api('/api/admin/auth/local/login', {
    method: 'POST',
    body: { localTestEmail: ADMIN_EMAIL, forcePasskeyEnroll: false }
  });
  expect(login.status).toBe(200);
  const bootstrapToken = login.body?.data?.session?.accessToken;
  expect(Boolean(bootstrapToken)).toBe(true);

  const promote = await api('/api/admin/users/promote/email', {
    method: 'POST',
    token: bootstrapToken,
    body: { email: TARGET_EMAIL }
  });
  expect(promote.status).toBe(200);
  expect(promote.body?.data?.email).toBe(TARGET_EMAIL);
  expect(promote.body?.data?.role).toBe('ADMIN');
});

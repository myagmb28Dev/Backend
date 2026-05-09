const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const repoRoot = path.resolve(__dirname, '..', '..');
const REAL_SESSION_KEY = 'pogun-real-firebase-session-v1';
const ACTIVE_ROLE_KEY = 'dm-test-active-role-v1';
const REAL_ROLE = 'google';

function loadToken(index) {
  const tokenPath = path.join(repoRoot, '.local', `emulator-user${index}-firebase-id-token.txt`);
  return fs.readFileSync(tokenPath, 'utf8').trim();
}

function loadRefreshToken(index) {
  const refreshPath = path.join(repoRoot, '.local', `emulator-user${index}-refresh-token.txt`);
  return fs.readFileSync(refreshPath, 'utf8').trim();
}

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function loginWithToken(token) {
  const response = await fetch(`${baseURL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firebaseIdToken: token })
  });
  return {
    status: response.status,
    body: await readJsonSafe(response)
  };
}

async function ensureReadySession(index) {
  const idToken = loadToken(index);
  const refreshToken = loadRefreshToken(index);
  const login = await loginWithToken(idToken);
  if (login.status !== 200) {
    throw new Error(`login failed: ${JSON.stringify(login.body)}`);
  }
  const data = login.body?.data || {};
  if (!data?.id) {
    throw new Error(`missing backend user id: ${JSON.stringify(login.body)}`);
  }
  return {
    firebaseIdToken: idToken,
    refreshToken,
    firebaseUid: data.firebaseUid || '',
    email: data.email || '',
    nickname: data.nickname || data.email || 'Google 사용자',
    profileImageUrl: data.profileImageUrl || '',
    userId: data.id,
    registrationStatus: data.registrationStatus || 'COMPLETED'
  };
}

async function seedSession(page, session) {
  await page.addInitScript(({ session, realSessionKey, activeRoleKey, role }) => {
    window.localStorage.setItem(realSessionKey, JSON.stringify(session));
    window.localStorage.setItem(activeRoleKey, role);
  }, {
    session,
    realSessionKey: REAL_SESSION_KEY,
    activeRoleKey: ACTIVE_ROLE_KEY,
    role: REAL_ROLE
  });
}

test.describe.serial('setting flow local', () => {
  test('loads and updates current account settings', async ({ page }) => {
    test.setTimeout(120000);
    const session = await ensureReadySession(1);
    await seedSession(page, session);

    await page.goto(`${baseURL}/full_compact/pages/setting-flow.html`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#status')).toContainText('설정 준비 완료', { timeout: 30000 });
    await expect(page.locator('#accountHint')).toContainText(session.email);

    const nicknameInput = page.locator('#nicknameInput');
    await expect(nicknameInput).toBeVisible();
    const originalNickname = await nicknameInput.inputValue();
    const patchedNickname = `playwright-setting-${Date.now()}`;
    await nicknameInput.fill(patchedNickname);
    await page.click('#saveProfileButton');
    await expect(page.locator('#status')).toContainText('프로필 저장 완료', { timeout: 15000 });

    await page.selectOption('#availabilitySelect', 'IDLE');
    await page.click('#saveAvailabilityButton');
    await expect(page.locator('#status')).toContainText('상태 저장 완료', { timeout: 15000 });
    await expect(page.locator('#effectivePresencePill')).toContainText(/IDLE|OFFLINE|ONLINE/);

    const notificationTypeItems = page.locator('#notificationTypeList .line');
    const settingsCount = await notificationTypeItems.count();
    expect(settingsCount).toBeGreaterThan(0);
    const firstToggle = page.locator('#notificationTypeList input[type="checkbox"]').first();
    const firstChecked = await firstToggle.isChecked();
    await firstToggle.setChecked(!firstChecked);
    await page.click('#saveNotificationSettingsButton');
    await expect(page.locator('#status')).toContainText('알림 설정 저장 완료', { timeout: 15000 });

    await expect(page.locator('#deviceRows tr').first()).toBeVisible({ timeout: 15000 });

    await nicknameInput.fill(originalNickname);
    await page.click('#saveProfileButton');
    await expect(page.locator('#status')).toContainText('프로필 저장 완료', { timeout: 15000 });
  });
});

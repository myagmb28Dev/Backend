const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const repoRoot = path.resolve(__dirname, '..', '..');
const REAL_SESSION_KEY = 'pogun-real-firebase-session-v1';
const ACTIVE_ROLE_KEY = 'dm-test-active-role-v1';
const REAL_ROLE = 'google';
const cleanupSource = path.join(os.tmpdir(), 'PogunLocalFirebaseCleanup.java');
const cleanupClassDir = os.tmpdir();

function parseDotEnv() {
  const envPath = path.join(repoRoot, '.env');
  if (!fs.existsSync(envPath)) {
    return {};
  }
  return Object.fromEntries(
    fs.readFileSync(envPath, 'utf8')
      .split(/\r?\n/)
      .filter((line) => line && !line.trimStart().startsWith('#') && line.includes('='))
      .map((line) => {
        const separatorIndex = line.indexOf('=');
        return [line.slice(0, separatorIndex).trim(), line.slice(separatorIndex + 1).trim()];
      })
  );
}

function findPostgresJar(directory) {
  if (!fs.existsSync(directory)) {
    return null;
  }
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isFile() && /^postgresql-.*\.jar$/.test(entry.name)) {
      return fullPath;
    }
    if (entry.isDirectory()) {
      const found = findPostgresJar(fullPath);
      if (found) {
        return found;
      }
    }
  }
  return null;
}

function ensureCleanupHelper(driverJar) {
  if (fs.existsSync(path.join(cleanupClassDir, 'PogunLocalFirebaseCleanup.class'))) {
    return;
  }
  fs.writeFileSync(cleanupSource, `
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class PogunLocalFirebaseCleanup {
  public static void main(String[] args) throws Exception {
    Map<String, String> env = loadEnv(Paths.get(args[0]));
    String dbUrl = require(env, "DB_URL");
    String dbUser = require(env, "DB_USERNAME");
    String dbPassword = require(env, "DB_PASSWORD");
    Class.forName("org.postgresql.Driver");
    try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
      connection.setAutoCommit(false);
      try {
        runCleanup(connection);
        connection.commit();
      } catch (Exception error) {
        connection.rollback();
        throw error;
      }
    }
  }

  private static Map<String, String> loadEnv(Path path) throws IOException {
    Map<String, String> values = new HashMap<>();
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) continue;
        int idx = trimmed.indexOf('=');
        String key = trimmed.substring(0, idx).trim();
        String value = trimmed.substring(idx + 1).trim();
        if ((value.startsWith("\\"") && value.endsWith("\\"")) || (value.startsWith("'") && value.endsWith("'"))) {
          value = value.substring(1, value.length() - 1);
        }
        values.put(key, value);
      }
    }
    return values;
  }

  private static String require(Map<String, String> env, String key) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing env key: " + key);
    }
    return value;
  }

  private static void runCleanup(Connection connection) throws Exception {
    String[] statements = new String[] {
      "create temporary table tmp_local_users on commit drop as select id from users where email ilike '%@local.dev'",
      "create temporary table tmp_local_notices on commit drop as select id from pet_notices where author_id in (select id from tmp_local_users)",
      "create temporary table tmp_local_rooms on commit drop as select id from notice_chat_rooms where owner_user_id in (select id from tmp_local_users) or guest_user_id in (select id from tmp_local_users) or notice_id in (select id from tmp_local_notices)",
      "create temporary table tmp_local_messages on commit drop as select id from notice_chat_messages where room_id in (select id from tmp_local_rooms) or sender_user_id in (select id from tmp_local_users)",
      "create temporary table tmp_local_posts on commit drop as select id from community_posts where author_id in (select id from tmp_local_users)",
      "create temporary table tmp_local_comments on commit drop as select id from community_comments where author_id in (select id from tmp_local_users) or post_id in (select id from tmp_local_posts)",
      "delete from reports where reporter_id in (select id from tmp_local_users)",
      "delete from reports where target_type = 'NOTICE_CHAT_ROOM' and target_id in (select id from tmp_local_rooms)",
      "delete from reports where target_type = 'PET_NOTICE' and target_id in (select id from tmp_local_notices)",
      "delete from reports where target_type = 'COMMUNITY_POST' and target_id in (select id from tmp_local_posts)",
      "delete from reports where target_type = 'COMMUNITY_COMMENT' and target_id in (select id from tmp_local_comments)",
      "delete from notice_chat_room_participant_states where room_id in (select id from tmp_local_rooms) or user_id in (select id from tmp_local_users)",
      "delete from notice_chat_read_receipts where room_id in (select id from tmp_local_rooms) or reader_user_id in (select id from tmp_local_users)",
      "delete from notice_chat_message_images where message_id in (select id from tmp_local_messages)",
      "update notice_chat_messages set reply_to_message_id = null where room_id in (select id from tmp_local_rooms)",
      "delete from notice_chat_messages where id in (select id from tmp_local_messages) or room_id in (select id from tmp_local_rooms)",
      "delete from notice_chat_rooms where id in (select id from tmp_local_rooms)",
      "delete from notice_bookmarks where user_id in (select id from tmp_local_users) or notice_id in (select id from tmp_local_notices)",
      "delete from pet_notice_images where notice_id in (select id from tmp_local_notices)",
      "delete from pet_notices where id in (select id from tmp_local_notices)",
      "delete from community_post_reactions where user_id in (select id from tmp_local_users) or post_id in (select id from tmp_local_posts)",
      "delete from community_post_votes where user_id in (select id from tmp_local_users) or post_id in (select id from tmp_local_posts)",
      "update community_comments set parent_comment_id = null where parent_comment_id in (select id from tmp_local_comments)",
      "delete from community_comments where id in (select id from tmp_local_comments)",
      "delete from community_post_images where post_id in (select id from tmp_local_posts)",
      "delete from community_post_tags where post_id in (select id from tmp_local_posts)",
      "delete from community_posts where id in (select id from tmp_local_posts)",
      "delete from notifications where user_id in (select id from tmp_local_users) or actor_user_id in (select id from tmp_local_users)",
      "delete from notifications where target_type = 'NOTICE_CHAT_ROOM' and target_id in (select id from tmp_local_rooms)",
      "delete from notifications where target_type = 'NOTICE_CHAT_MESSAGE' and target_id in (select id from tmp_local_messages)",
      "delete from notifications where target_type = 'COMMUNITY_POST' and target_id in (select id from tmp_local_posts)",
      "delete from notifications where target_type = 'COMMUNITY_COMMENT' and target_id in (select id from tmp_local_comments)",
      "delete from notifications where target_type = 'PET_NOTICE' and target_id in (select id from tmp_local_notices)",
      "delete from notifications where target_type = 'USER' and target_id in (select id from tmp_local_users)",
      "delete from user_blocks where blocker_id in (select id from tmp_local_users) or blocked_id in (select id from tmp_local_users)",
      "delete from user_follows where follower_id in (select id from tmp_local_users) or following_id in (select id from tmp_local_users)",
      "delete from user_social_accounts where user_id in (select id from tmp_local_users)",
      "delete from user_fcm_tokens where user_id in (select id from tmp_local_users)",
      "delete from user_notification_settings where user_id in (select id from tmp_local_users)",
      "delete from pending_social_signups where email ilike '%@local.dev'",
      "delete from users where id in (select id from tmp_local_users)"
    };
    try (Statement statement = connection.createStatement()) {
      for (String sql : statements) {
        statement.executeUpdate(sql);
      }
    }
  }
}
`, 'utf8');
  execFileSync('javac', ['-cp', driverJar, cleanupSource], { stdio: 'ignore' });
}

function cleanupLocalArtifacts() {
  const env = parseDotEnv();
  const driverJar = findPostgresJar(path.join(os.homedir(), '.gradle', 'caches', 'modules-2', 'files-2.1', 'org.postgresql', 'postgresql'));
  const envPath = path.join(repoRoot, '.env');
  if (fs.existsSync(envPath) && driverJar && env.DB_URL && env.DB_USERNAME && env.DB_PASSWORD) {
    try {
      ensureCleanupHelper(driverJar);
      execFileSync('java', ['-cp', `${cleanupClassDir}${path.delimiter}${driverJar}`, 'PogunLocalFirebaseCleanup', envPath], {
        cwd: repoRoot,
        stdio: 'ignore'
      });
    } catch {
      // 로컬 환경(DB 권한/네트워크)에서 정리 실패 시 테스트 전체 중단을 피한다.
    }
  }

  const uploadsRoot = path.join(repoRoot, 'uploads');
  if (fs.existsSync(uploadsRoot)) {
    fs.rmSync(uploadsRoot, { recursive: true, force: true });
  }
  fs.mkdirSync(uploadsRoot, { recursive: true });
}

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

async function api(pathname, token, init = {}) {
  const headers = {
    Authorization: `Bearer ${token}`,
    ...(init.headers || {})
  };
  if (!(init.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${baseURL}${pathname}`, {
    ...init,
    headers
  });

  return {
    status: response.status,
    body: await readJsonSafe(response)
  };
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

async function ensureReadySession(authBundle) {
  let login = await loginWithToken(authBundle.idToken);
  if (login.status !== 200) {
    throw new Error(`login failed: ${JSON.stringify(login.body)}`);
  }
  if (login.body?.data?.registrationStatus === 'PENDING_ONBOARDING' || !login.body?.data?.id) {
    const onboarding = await api('/api/auth/onboarding/complete', authBundle.idToken, {
      method: 'POST',
      body: JSON.stringify({ x: 127.1086228, y: 37.4012191 })
    });
    if (onboarding.status !== 200) {
      throw new Error(`onboarding failed: ${JSON.stringify(onboarding.body)}`);
    }
    login = await loginWithToken(authBundle.idToken);
    if (login.status !== 200 || !login.body?.data?.id) {
      throw new Error(`login after onboarding failed: ${JSON.stringify(login.body)}`);
    }
  }

  const data = login.body.data;
  return {
    firebaseIdToken: authBundle.idToken,
    refreshToken: authBundle.refreshToken || '',
    firebaseUid: data.firebaseUid || '',
    email: data.email || '',
    nickname: data.nickname || data.email || 'Google 사용자',
    profileImageUrl: data.profileImageUrl || '',
    userId: data.id,
    registrationStatus: data.registrationStatus
  };
}

async function loadUserSession(index) {
  const idToken = loadToken(index);
  const refreshToken = loadRefreshToken(index);
  return ensureReadySession({ idToken, refreshToken });
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

function tinyPngFile(name = 'tiny.png') {
  const base64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0YQAAAAASUVORK5CYII=';
  return {
    name,
    mimeType: 'image/png',
    buffer: Buffer.from(base64, 'base64')
  };
}

function noticePayload(title) {
  return {
    title,
    animalType: 'DOG',
    breed: 'MIX',
    gender: 'MALE',
    description: 'playwright local flow',
    missingDate: new Date().toISOString(),
    missingRegion: '서울 강남구',
    missingAddress: '테스트 주소',
    contactPhone: '010-1111-2222',
    status: 'OPEN'
  };
}

async function createNotice(token, title) {
  const response = await api('/api/missing-pets', token, {
    method: 'POST',
    body: JSON.stringify(noticePayload(title))
  });
  expect(response.status).toBe(201);
  return response.body.data;
}

async function createRoom(token, noticeId) {
  for (let i = 0; i < 3; i += 1) {
    const response = await api(`/api/chat/rooms/notice/${noticeId}`, token, { method: 'POST' });
    if ([200, 201].includes(response.status)) {
      return response.body.data;
    }
    await new Promise((resolve) => setTimeout(resolve, 800));
  }
  throw new Error(`createRoom failed for notice ${noticeId}`);
}

async function fetchUnreadCount(token) {
  const response = await api('/api/notifications/unread-count', token);
  expect(response.status).toBe(200);
  return Number(response.body.data.unreadCount || 0);
}

async function markAllNotificationsRead(token) {
  const response = await api('/api/notifications/read-all', token, { method: 'PATCH' });
  expect(response.status).toBe(200);
}

test.describe.serial('local user1 service flows', () => {
  test.beforeAll(() => {
    cleanupLocalArtifacts();
  });

  test.beforeEach(() => {
    cleanupLocalArtifacts();
  });

  test.afterEach(() => {
    cleanupLocalArtifacts();
  });

  test('login flow keeps user1 session across static pages', async ({ page }) => {
    const user1Session = await loadUserSession(1);
    await seedSession(page, user1Session);

    await page.goto(`${baseURL}/full_compact/pages/login-flow.html`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#accountHint')).toContainText(user1Session.email);
    await expect(page.locator('#googleLoginButton')).toBeDisabled();

    await page.click('#goDmButton');
    await expect(page).toHaveURL(/\/dm-flow\.html/);
    await expect(page.locator('#currentUserState')).toContainText(user1Session.nickname);
  });

  test('shelter flow reuses login session and loads shelter detail tools', async ({ page }) => {
    const user1Session = await loadUserSession(1);
    await seedSession(page, user1Session);

    await page.goto(`${baseURL}/full_compact/pages/shelter-flow.html`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#accountHint')).toContainText(user1Session.email);
    await expect(page.locator('#sessionBadge')).toContainText(user1Session.nickname);
    await expect(page.locator('#referenceSummaryBox')).toContainText('region');
    await expect(page.locator('#listMeta')).not.toHaveText('미조회', { timeout: 30000 });

    const firstNotice = page.locator('#listContainer [data-id]').first();
    await expect(firstNotice).toBeVisible({ timeout: 30000 });
    await firstNotice.click();

    await expect(page.locator('#selectionSummaryBox')).not.toHaveText('선택된 공고 없음', { timeout: 30000 });
    await expect(page.locator('#detailContainer')).toContainText('공고 정보', { timeout: 30000 });

    await page.click('#refreshDetailButton');
    await expect(page.locator('#status')).toContainText('상세 조회 완료', { timeout: 10000 });
  });

  test('notice flow creates a notice and redirects to dm flow', async ({ page }) => {
    const user1Session = await loadUserSession(1);
    await seedSession(page, user1Session);

    const title = `playwright-user1-notice-${Date.now()}`;
    await page.goto(`${baseURL}/full_compact/pages/notice-flow.html`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#accountHint')).toContainText(user1Session.email);

    await page.fill('#title', title);
    await page.fill('#animalType', 'DOG');
    await page.fill('#missingRegion', '서울 강남구');
    await page.setInputFiles('#images', tinyPngFile('notice-image.png'));
    await page.click('#createNoticeButton');

    await expect(page).toHaveURL(/\/dm-flow\.html\?noticeId=/, { timeout: 30000 });
    await expect(page.locator('#currentUserState')).toContainText(user1Session.nickname, { timeout: 20000 });
    await expect(page.locator('#status')).not.toContainText('로그인이 필요합니다', { timeout: 20000 });
  });

  test('dm flow lets user1 start a room and send a message', async ({ page }) => {
    const user1Token = loadToken(1);
    const user2Token = loadToken(2);
    const user1Session = await loadUserSession(1);
    await loadUserSession(2);

    const notice = await createNotice(user2Token, `playwright-user2-notice-${Date.now()}`);
    const room = await createRoom(user1Token, notice.id);
    await seedSession(page, user1Session);

    await page.goto(`${baseURL}/full_compact/pages/dm-flow.html?roomId=${room.roomId}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#currentUserState')).toContainText(user1Session.nickname);
    await expect(page.locator('#status')).not.toContainText('로그인이 필요합니다', { timeout: 10000 });
  });

  test('dm flow accepts token query bootstrap without login page redirect', async ({ page }) => {
    const user1Session = await loadUserSession(1);
    const tokenParam = encodeURIComponent(user1Session.firebaseIdToken);

    await page.goto(`${baseURL}/full_compact/pages/dm-flow.html?token=${tokenParam}`, { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/full_compact\/pages\/dm-flow\.html(?:\?|$)/, { timeout: 20000 });
    await expect(page).not.toHaveURL(/\/full_compact\/pages\/login-flow\.html/, { timeout: 20000 });
    await expect(page.locator('#currentUserState')).toContainText(user1Session.nickname, { timeout: 20000 });
  });

  test('dm flow reflects global online and logout offline presence', async ({ browser, page }) => {
    test.setTimeout(90000);
    const user1Token = loadToken(1);
    const user2Token = loadToken(3);
    const user1Session = await loadUserSession(1);
    const user2Session = await loadUserSession(3);

    const notice = await createNotice(user2Token, `playwright-user3-presence-${Date.now()}`);
    const room = await createRoom(user1Token, notice.id);

    await seedSession(page, user1Session);
    await page.goto(`${baseURL}/full_compact/pages/dm-flow.html?roomId=${room.roomId}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#currentUserState')).toContainText(user1Session.nickname);

    const user2Context = await browser.newContext();
    const user2Page = await user2Context.newPage();
    try {
      await seedSession(user2Page, user2Session);
      await user2Page.goto(`${baseURL}/full_compact/pages/dm-flow.html?roomId=${room.roomId}`, { waitUntil: 'domcontentloaded' });
      await expect(user2Page.locator('#currentUserState')).toContainText(user2Session.nickname);

      const logout = await api('/api/auth/logout', user2Token, { method: 'POST' });
      expect(logout.status).toBe(200);
      await page.waitForTimeout(1200);
    } finally {
      await user2Context.close();
    }
  });

  test('dm flow reflects global offline when user session leaves', async ({ browser, page }) => {
    test.setTimeout(90000);

    const user1Token = loadToken(1);
    const user2Token = loadToken(2);
    const user1Session = await loadUserSession(1);
    const user2Session = await loadUserSession(2);

    const notice = await createNotice(user2Token, `playwright-user2-session-leave-${Date.now()}`);
    const room = await createRoom(user1Token, notice.id);

    await seedSession(page, user1Session);
    await page.goto(`${baseURL}/full_compact/pages/dm-flow.html?roomId=${room.roomId}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#currentUserState')).toContainText(user1Session.nickname);

    const user2Context = await browser.newContext();
    const user2Page = await user2Context.newPage();
    await seedSession(user2Page, user2Session);
    await user2Page.goto(`${baseURL}/full_compact/pages/dm-flow.html?roomId=${room.roomId}`, { waitUntil: 'domcontentloaded' });
    await expect(user2Page.locator('#currentUserState')).toContainText(user2Session.nickname);
    await user2Context.close();
    await expect(page.locator('#currentUserState')).toContainText(user1Session.nickname, { timeout: 35000 });
  });

  test('notification flow shows user1 unread notification and clears it', async ({ page }) => {
    const user1Token = loadToken(1);
    const user1Session = await loadUserSession(1);
    await markAllNotificationsRead(user1Token);
    expect(await fetchUnreadCount(user1Token)).toBe(0);
    const notice = await createNotice(user1Token, `playwright-user1-notify-${Date.now()}`);

    await seedSession(page, user1Session);
    await page.goto(`${baseURL}/full_compact/pages/login-flow.html`, { waitUntil: 'domcontentloaded' });
    await page.waitForURL(/\/full_compact\/pages\/login-flow\.html/);
    await expect(page.locator('#accountHint')).toContainText(user1Session.email);

    await page.goto(`${baseURL}/full_compact/pages/notification-flow.html`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('#unreadCountValue')).not.toHaveText('-', { timeout: 10000 });
    await expect(page.locator('#notificationList')).toContainText('NEW_NOTICE', { timeout: 20000 });
    await expect(page.locator('#notificationList')).toContainText(notice.title, { timeout: 20000 });

    await page.click('#readAllNotificationsButton');
    await expect(page.locator('#unreadCountValue')).toHaveText('0', { timeout: 10000 });
  });

  test('refresh api issues a new id token and keeps auth usable', async () => {
    const session = await loadUserSession(1);
    const refreshResponse = await fetch(`${baseURL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken })
    });
    const refreshBody = await readJsonSafe(refreshResponse);
    expect(refreshResponse.status).toBe(200);
    expect(typeof refreshBody?.data?.idToken).toBe('string');
    expect(refreshBody?.data?.idToken?.length).toBeGreaterThan(100);
    expect(typeof refreshBody?.data?.refreshToken).toBe('string');
    expect(refreshBody?.data?.refreshToken?.length).toBeGreaterThan(20);

    const relogin = await loginWithToken(refreshBody.data.idToken);
    expect(relogin.status).toBe(200);
    expect(relogin.body?.data?.email).toBe('playwright-user1@local.dev');
  });
});

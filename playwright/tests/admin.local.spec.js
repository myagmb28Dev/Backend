const fs = require('fs');
const net = require('net');
const os = require('os');
const path = require('path');
const { execFileSync, spawn } = require('child_process');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const authEmulatorBase = process.env.AUTH_EMULATOR_BASE || 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const repoRoot = path.resolve(__dirname, '..', '..');
const localEnv = parseDotEnv();
const emulatorProjectId = process.env.FIREBASE_PROJECT_ID || process.env.GCLOUD_PROJECT || localEnv.FIREBASE_PROJECT_ID || localEnv.GCLOUD_PROJECT || 'pogeun-fire';
const cleanupSource = path.join(os.tmpdir(), 'PogunAdminPlaywrightDbHelper.java');
const cleanupClassDir = os.tmpdir();

const ADMIN_EMAIL = 'playwright-admin@local.dev';
const ADMIN_PASSWORD = 'Test1234!';
const NOTICE_OWNER_EMAIL = 'playwright-owner@local.dev';
const REPORTER_EMAIL = 'playwright-reporter@local.dev';
const USER_PASSWORD = 'Test1234!';

function parseDotEnv() {
  const envPaths = [path.join(repoRoot, '.env'), path.join(repoRoot, '.env.local')];
  const merged = {};
  for (const envPath of envPaths) {
    if (!fs.existsSync(envPath)) {
      continue;
    }
    Object.assign(merged, Object.fromEntries(
      fs.readFileSync(envPath, 'utf8')
        .split(/\r?\n/)
        .filter((line) => line && !line.trimStart().startsWith('#') && line.includes('='))
        .map((line) => {
          const separatorIndex = line.indexOf('=');
          return [line.slice(0, separatorIndex).trim(), line.slice(separatorIndex + 1).trim()];
        })
    ));
  }
  return merged;
}

function createMergedEnvFile(env) {
  const mergedEnvPath = path.join(repoRoot, '.local', 'admin-playwright.env');
  fs.mkdirSync(path.dirname(mergedEnvPath), { recursive: true });
  const lines = Object.entries(env).map(([key, value]) => `${key}=${value}`);
  fs.writeFileSync(mergedEnvPath, `${lines.join('\n')}\n`, 'utf8');
  return mergedEnvPath;
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

function ensureDbHelper(driverJar) {
  fs.writeFileSync(cleanupSource, `
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class PogunAdminPlaywrightDbHelper {
  public static void main(String[] args) throws Exception {
    Map<String, String> env = loadEnv(Paths.get(args[0]));
    String dbUrl = require(env, "DB_URL");
    String dbUser = require(env, "DB_USERNAME");
    String dbPassword = require(env, "DB_PASSWORD");
    String action = args[1];
    Class.forName("org.postgresql.Driver");
    try (Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
      connection.setAutoCommit(false);
      try {
        if ("cleanup".equals(action)) {
          runCleanup(connection);
        } else if ("promote".equals(action)) {
          promoteAdmin(connection, args[2]);
        } else if ("inspect".equals(action)) {
          inspectUser(connection, args[2]);
        } else {
          throw new IllegalArgumentException("Unknown action: " + action);
        }
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

  private static void promoteAdmin(Connection connection, String email) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(
        "update users set role = 'ADMIN', status = 'ACTIVE' where lower(email) = lower(?)")) {
      statement.setString(1, email);
      int updated = statement.executeUpdate();
      if (updated != 1) {
        throw new IllegalStateException("Admin promotion target not found: " + email);
      }
    }
  }

  private static void inspectUser(Connection connection, String email) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(
        "select u.id, u.email, u.role, u.status, coalesce(u.firebase_uid, '') as firebase_uid, " +
            "(select count(*) from admin_passkeys ap where ap.user_id = u.id) as passkey_count, " +
            "(select count(*) from admin_permission_assignments apa where apa.user_id = u.id) as permission_count " +
            "from users u where lower(u.email) = lower(?)")) {
      statement.setString(1, email);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw new IllegalStateException("User not found: " + email);
        }
        System.out.println(
            "id=" + rs.getString("id")
                + ",email=" + rs.getString("email")
                + ",role=" + rs.getString("role")
                + ",status=" + rs.getString("status")
                + ",firebaseUid=" + rs.getString("firebase_uid")
                + ",passkeys=" + rs.getLong("passkey_count")
                + ",permissions=" + rs.getLong("permission_count"));
      }
    }
  }

  private static void runCleanup(Connection connection) throws Exception {
    String[] statements = new String[] {
      "create temporary table tmp_local_users on commit drop as select id from users where email ilike '%@local.dev'",
      "create temporary table tmp_local_notices on commit drop as select id from pet_notices where author_id in (select id from tmp_local_users)",
      "create temporary table tmp_local_rooms on commit drop as select id from notice_chat_rooms where owner_user_id in (select id from tmp_local_users) or guest_user_id in (select id from tmp_local_users) or notice_id in (select id from tmp_local_notices)",
      "create temporary table tmp_local_messages on commit drop as select id from notice_chat_messages where room_id in (select id from tmp_local_rooms) or sender_user_id in (select id from tmp_local_users)",
      "create temporary table tmp_local_posts on commit drop as select id from community_posts where author_id in (select id from tmp_local_users)",
      "create temporary table tmp_local_comments on commit drop as select id from community_comments where author_id in (select id from tmp_local_users) or post_id in (select id from tmp_local_posts)",
      "delete from admin_auth_challenges where session_id in (select id from admin_sessions where user_id in (select id from tmp_local_users)) or user_id in (select id from tmp_local_users)",
      "delete from admin_passkeys where user_id in (select id from tmp_local_users)",
      "delete from admin_permission_assignments where user_id in (select id from tmp_local_users)",
      "delete from admin_sessions where user_id in (select id from tmp_local_users)",
      "delete from admin_audit_logs where actor_user_id in (select id from tmp_local_users)",
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

function runDbHelper(action, arg = null, options = {}) {
  const env = parseDotEnv();
  const driverJar = findPostgresJar(path.join(os.homedir(), '.gradle', 'caches', 'modules-2', 'files-2.1', 'org.postgresql', 'postgresql'))
    || findPostgresJar(path.join(repoRoot, '.gradle-local', 'caches', 'modules-2', 'files-2.1', 'org.postgresql', 'postgresql'));
  if (!driverJar || !env.DB_URL || !env.DB_USERNAME || !env.DB_PASSWORD) {
    throw new Error('DB helper prerequisites are missing.');
  }
  const envPath = createMergedEnvFile(env);
  ensureDbHelper(driverJar);
  const args = ['-cp', `${cleanupClassDir}${path.delimiter}${driverJar}`, 'PogunAdminPlaywrightDbHelper', envPath, action];
  if (arg) {
    args.push(arg);
  }
  return execFileSync('java', args, {
    cwd: repoRoot,
    stdio: options.capture ? ['ignore', 'pipe', 'pipe'] : 'ignore',
    encoding: options.capture ? 'utf8' : undefined
  });
}

function cleanupLocalArtifacts() {
  runDbHelper('cleanup');
  const uploadsRoot = path.join(repoRoot, 'uploads');
  if (fs.existsSync(uploadsRoot)) {
    fs.rmSync(uploadsRoot, { recursive: true, force: true });
  }
  fs.mkdirSync(uploadsRoot, { recursive: true });
}

function isPortOpen(port, host = '127.0.0.1') {
  return new Promise((resolve) => {
    const socket = new net.Socket();
    const finish = (result) => {
      socket.destroy();
      resolve(result);
    };

    socket.setTimeout(1000);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
    socket.connect(port, host);
  });
}

async function ensureAuthEmulatorRunning() {
  if (await isPortOpen(9099)) {
    return;
  }

  const xdgConfigHome = path.join(repoRoot, '.local', 'xdg');
  fs.mkdirSync(xdgConfigHome, { recursive: true });
  const child = spawn(process.env.ComSpec || 'cmd.exe', [
    '/c',
    'firebase.cmd',
    'emulators:start',
    '--only',
    'auth',
    '--project',
    'pogeun-fire'
  ], {
    cwd: repoRoot,
    detached: true,
    stdio: 'ignore',
    env: {
      ...process.env,
      XDG_CONFIG_HOME: xdgConfigHome
    }
  });
  child.unref();

  const deadline = Date.now() + 120000;
  while (Date.now() < deadline) {
    if (await isPortOpen(9099)) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error('Firebase auth emulator did not become ready on port 9099.');
}

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function postJson(url, body, headers = {}) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body)
  });
  return { status: response.status, body: await readJsonSafe(response) };
}

async function patchJson(url, body, headers = {}) {
  const response = await fetch(url, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body)
  });
  return { status: response.status, body: await readJsonSafe(response) };
}

async function getJson(url, headers = {}) {
  const response = await fetch(url, { headers });
  return { status: response.status, body: await readJsonSafe(response) };
}

async function authEmulator(pathname, body) {
  const response = await fetch(`${authEmulatorBase}${pathname}?key=fake-api-key`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  const payload = await readJsonSafe(response);
  if (!response.ok) {
    throw new Error(JSON.stringify(payload));
  }
  return payload;
}

async function getOobCode(email) {
  const response = await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${encodeURIComponent(emulatorProjectId)}/oobCodes`);
  const payload = await readJsonSafe(response);
  if (!response.ok) {
    throw new Error(`failed to read oob codes: ${JSON.stringify(payload)}`);
  }
  const code = (payload.oobCodes || [])
    .filter((entry) => entry.email === email && entry.requestType === 'VERIFY_EMAIL')
    .at(-1);
  if (!code?.oobCode) {
    throw new Error(`email verification code not found for ${email}`);
  }
  return code.oobCode;
}

async function ensureVerifiedEmulatorUser(email, password, displayName) {
  let signIn;
  try {
    signIn = await authEmulator('/accounts:signInWithPassword', {
      email,
      password,
      returnSecureToken: true
    });
  } catch (error) {
    if (!String(error.message).includes('EMAIL_NOT_FOUND')) {
      throw error;
    }
    await authEmulator('/accounts:signUp', {
      email,
      password,
      returnSecureToken: true
    });
    signIn = await authEmulator('/accounts:signInWithPassword', {
      email,
      password,
      returnSecureToken: true
    });
  }

  await authEmulator('/accounts:sendOobCode', {
    requestType: 'VERIFY_EMAIL',
    idToken: signIn.idToken
  });

  const oobCode = await getOobCode(email);
  await authEmulator('/accounts:update', { oobCode });

  const verifiedSignIn = await authEmulator('/accounts:signInWithPassword', {
    email,
    password,
    returnSecureToken: true
  });

  await authEmulator('/accounts:update', {
    idToken: verifiedSignIn.idToken,
    displayName,
    returnSecureToken: true
  });

  return {
    idToken: verifiedSignIn.idToken,
    localId: verifiedSignIn.localId,
    email: verifiedSignIn.email
  };
}

async function loginWithToken(idToken) {
  return postJson(`${baseURL}/api/auth/login`, { firebaseIdToken: idToken });
}

async function api(pathname, token, init = {}) {
  const method = init.method || 'GET';
  const headers = {
    Authorization: `Bearer ${token}`,
    ...(init.headers || {})
  };
  if (init.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(`${baseURL}${pathname}`, {
    method,
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body)
  });
  return { status: response.status, body: await readJsonSafe(response) };
}

async function ensureReadySession(idToken) {
  let login = await loginWithToken(idToken);
  if (login.status !== 200) {
    throw new Error(`login failed: ${JSON.stringify(login.body)}`);
  }
  if (login.body?.data?.registrationStatus === 'PENDING_ONBOARDING' || !login.body?.data?.id) {
    const onboarding = await api('/api/auth/onboarding/complete', idToken, {
      method: 'POST',
      body: { x: 127.1086228, y: 37.4012191 }
    });
    if (onboarding.status !== 200) {
      throw new Error(`onboarding failed: ${JSON.stringify(onboarding.body)}`);
    }
    login = await loginWithToken(idToken);
    if (login.status !== 200 || !login.body?.data?.id) {
      throw new Error(`login after onboarding failed: ${JSON.stringify(login.body)}`);
    }
  }
  return login.body.data;
}

async function createNotice(token, title) {
  const response = await api('/api/missing-pets', token, {
    method: 'POST',
    body: {
      title,
      animalType: 'DOG',
      breed: 'MIX',
      gender: 'MALE',
      description: 'playwright admin local flow',
      missingDate: new Date().toISOString(),
      missingRegion: '서울 강남구',
      missingAddress: '테스트 주소',
      contactPhone: '010-1111-2222',
      status: 'OPEN'
    }
  });
  expect(response.status).toBe(201);
  return response.body.data;
}

async function createReport(token, targetId) {
  const response = await api('/api/reports', token, {
    method: 'POST',
    body: {
      targetType: 'PET_NOTICE',
      targetId,
      reason: 'FALSE_INFORMATION',
      description: 'playwright admin review flow'
    }
  });
  expect(response.status).toBe(201);
  return response.body.data;
}

async function installVirtualAuthenticator(page) {
  const client = await page.context().newCDPSession(page);
  await client.send('WebAuthn.enable');
  const { authenticatorId } = await client.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true
    }
  });
  return { client, authenticatorId };
}

async function createRegistrationCredential(page, publicKey) {
  return page.evaluate(async (rawPublicKey) => {
    const toBuffer = (value) => {
      const padding = '='.repeat((4 - value.length % 4) % 4);
      const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/');
      const binary = atob(base64);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
      }
      return bytes.buffer;
    };

    const toBase64Url = (value) => {
      const bytes = value instanceof ArrayBuffer
        ? new Uint8Array(value)
        : new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
      let binary = '';
      for (const byte of bytes) {
        binary += String.fromCharCode(byte);
      }
      return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
    };

    const toCreationOptions = (value) => {
      const normalized = JSON.parse(JSON.stringify(value));
      const options = normalized && normalized.publicKey ? normalized.publicKey : normalized;
      options.challenge = toBuffer(options.challenge);
      options.user.id = toBuffer(options.user.id);
      options.excludeCredentials = (options.excludeCredentials || []).map((item) => ({
        ...item,
        id: toBuffer(item.id)
      }));
      return options;
    };

    const credentialToJson = (value) => {
      if (value == null) return value;
      if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) {
        return toBase64Url(value);
      }
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
      if (value instanceof AuthenticatorAssertionResponse) {
        return {
          clientDataJSON: credentialToJson(value.clientDataJSON),
          authenticatorData: credentialToJson(value.authenticatorData),
          signature: credentialToJson(value.signature),
          userHandle: value.userHandle ? credentialToJson(value.userHandle) : null
        };
      }
      if (Array.isArray(value)) {
        return value.map(credentialToJson);
      }
      if (typeof value === 'object') {
        return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, credentialToJson(entry)]));
      }
      return value;
    };

    const credential = await navigator.credentials.create({ publicKey: toCreationOptions(rawPublicKey) });
    if (!credential) {
      throw new Error('registration credential was not created');
    }
    return credentialToJson(credential);
  }, publicKey);
}

async function createAssertionCredential(page, publicKey) {
  return page.evaluate(async (rawPublicKey) => {
    const toBuffer = (value) => {
      const padding = '='.repeat((4 - value.length % 4) % 4);
      const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/');
      const binary = atob(base64);
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
      }
      return bytes.buffer;
    };

    const toBase64Url = (value) => {
      const bytes = value instanceof ArrayBuffer
        ? new Uint8Array(value)
        : new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
      let binary = '';
      for (const byte of bytes) {
        binary += String.fromCharCode(byte);
      }
      return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
    };

    const toRequestOptions = (value) => {
      const normalized = JSON.parse(JSON.stringify(value));
      const options = normalized && normalized.publicKey ? normalized.publicKey : normalized;
      options.challenge = toBuffer(options.challenge);
      options.allowCredentials = (options.allowCredentials || []).map((item) => ({
        ...item,
        id: toBuffer(item.id)
      }));
      return options;
    };

    const credentialToJson = (value) => {
      if (value == null) return value;
      if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) {
        return toBase64Url(value);
      }
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
      if (Array.isArray(value)) {
        return value.map(credentialToJson);
      }
      if (typeof value === 'object') {
        return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, credentialToJson(entry)]));
      }
      return value;
    };

    const credential = await navigator.credentials.get({ publicKey: toRequestOptions(rawPublicKey) });
    if (!credential) {
      throw new Error('assertion credential was not created');
    }
    return credentialToJson(credential);
  }, publicKey);
}

test.describe.serial('admin local auth and report review flows', () => {
  test.beforeEach(() => {
    cleanupLocalArtifacts();
  });

  test.afterEach(() => {
    if (!process.env.KEEP_LOCAL_ARTIFACTS) {
      cleanupLocalArtifacts();
    }
  });

  test('admin registers a passkey, reviews a report, and logs in again with passkey MFA', async ({ page }) => {
    test.setTimeout(120000);

    await ensureAuthEmulatorRunning();

    const ownerIdentity = await ensureVerifiedEmulatorUser(NOTICE_OWNER_EMAIL, USER_PASSWORD, 'Notice Owner');
    const reporterIdentity = await ensureVerifiedEmulatorUser(REPORTER_EMAIL, USER_PASSWORD, 'Reporter User');
    const adminIdentity = await ensureVerifiedEmulatorUser(ADMIN_EMAIL, ADMIN_PASSWORD, 'Admin User');

    await ensureReadySession(ownerIdentity.idToken);
    await ensureReadySession(reporterIdentity.idToken);
    await ensureReadySession(adminIdentity.idToken);
    runDbHelper('promote', ADMIN_EMAIL);
    const adminState = runDbHelper('inspect', ADMIN_EMAIL, { capture: true }).trim();
    expect(adminState).toContain('role=ADMIN');
    expect(adminState).toContain('status=ACTIVE');
    expect(adminState).toContain('passkeys=0');

    const notice = await createNotice(ownerIdentity.idToken, `playwright-admin-notice-${Date.now()}`);
    const report = await createReport(reporterIdentity.idToken, notice.id);

    await page.goto(`${baseURL}/admin-flow.html`, { waitUntil: 'domcontentloaded' });
    const { client, authenticatorId } = await installVirtualAuthenticator(page);

    try {
      const initialLogin = await postJson(`${baseURL}/api/admin/auth/login`, {
        email: ADMIN_EMAIL,
        password: ADMIN_PASSWORD
      });
      expect(initialLogin.status, `adminState=${adminState}\nresponse=${JSON.stringify(initialLogin.body)}`).toBe(200);
      expect(initialLogin.body.data.nextStep).toBe('PASSKEY_REGISTRATION_REQUIRED');
      expect(initialLogin.body.data.requiresPassKey).toBe(true);

      const bootstrapToken = initialLogin.body.data.session.accessToken;
      const registerOptions = await postJson(`${baseURL}/api/admin/auth/passkeys/register/options`, {}, {
        Authorization: `Bearer ${bootstrapToken}`
      });
      expect(registerOptions.status).toBe(200);
      const registrationPublicKey = registerOptions.body?.data?.publicKey ?? registerOptions.body?.data;
      if (!registrationPublicKey?.challenge && !registrationPublicKey?.publicKey?.challenge) {
        throw new Error(`unexpected passkey register options payload: ${JSON.stringify(registerOptions.body)}`);
      }

      const registrationCredential = await createRegistrationCredential(page, registrationPublicKey);
      const registrationVerify = await postJson(`${baseURL}/api/admin/auth/passkeys/register/verify`, {
        challengeId: registerOptions.body.data.challengeId,
        credential: registrationCredential
      }, {
        Authorization: `Bearer ${bootstrapToken}`
      });
      expect(registrationVerify.status).toBe(200);
      expect(registrationVerify.body.data.stage).toBe('AUTHENTICATED');
      expect(registrationVerify.body.data.permissions).toContain('REPORT_REVIEW');

      const adminAccessToken = registrationVerify.body.data.accessToken;
      const sessionState = await getJson(`${baseURL}/api/admin/auth/session`, {
        Authorization: `Bearer ${adminAccessToken}`
      });
      expect(sessionState.status).toBe(200);
      expect(sessionState.body.data.authenticated).toBe(true);
      expect(sessionState.body.data.admin.email).toBe(ADMIN_EMAIL);

      const adminReports = await getJson(`${baseURL}/api/admin/reports?page=1&pageSize=20&status=PENDING`, {
        Authorization: `Bearer ${adminAccessToken}`
      });
      expect(adminReports.status).toBe(200);
      const listedReport = (adminReports.body.data.items || []).find((item) => item.id === report.id);
      expect(listedReport).toBeTruthy();
      expect(listedReport.status).toBe('PENDING');

      const adminReportDetail = await getJson(`${baseURL}/api/admin/reports/${report.id}`, {
        Authorization: `Bearer ${adminAccessToken}`
      });
      expect(adminReportDetail.status).toBe(200);
      expect(adminReportDetail.body.data.targetId).toBe(notice.id);
      expect(adminReportDetail.body.data.reason).toBe('FALSE_INFORMATION');

      const reviewResponse = await patchJson(`${baseURL}/api/admin/reports/${report.id}/review`, {
        status: 'REVIEWING',
        processAction: 'NONE',
        processReason: 'playwright admin review'
      }, {
        Authorization: `Bearer ${adminAccessToken}`
      });
      expect(reviewResponse.status).toBe(200);
      expect(reviewResponse.body.data.status).toBe('REVIEWING');
      expect(reviewResponse.body.data.processReason).toBe('playwright admin review');

      const logout = await postJson(`${baseURL}/api/admin/auth/logout`, {}, {
        Authorization: `Bearer ${adminAccessToken}`
      });
      expect(logout.status).toBe(200);

      const secondLogin = await postJson(`${baseURL}/api/admin/auth/login`, {
        email: ADMIN_EMAIL,
        password: ADMIN_PASSWORD
      });
      expect(secondLogin.status).toBe(200);
      expect(secondLogin.body.data.nextStep).toBe('PASSKEY_REQUIRED');
      expect(secondLogin.body.data.requiresPassKey).toBe(true);

      const mfaBootstrapToken = secondLogin.body.data.session.accessToken;
      const assertionCredential = await createAssertionCredential(page, secondLogin.body.data.passkey.publicKey);
      const mfaVerify = await postJson(`${baseURL}/api/admin/auth/mfa/verify`, {
        challengeId: secondLogin.body.data.passkey.challengeId,
        credential: assertionCredential
      }, {
        Authorization: `Bearer ${mfaBootstrapToken}`
      });
      expect(mfaVerify.status).toBe(200);
      expect(mfaVerify.body.data.stage).toBe('AUTHENTICATED');

      const reviewedReports = await getJson(`${baseURL}/api/admin/reports?page=1&pageSize=20&status=REVIEWING`, {
        Authorization: `Bearer ${mfaVerify.body.data.accessToken}`
      });
      expect(reviewedReports.status).toBe(200);
      expect((reviewedReports.body.data.items || []).some((item) => item.id === report.id)).toBe(true);
    } finally {
      try {
        await client.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId });
      } catch {
      }
      try {
        await client.send('WebAuthn.disable');
      } catch {
      }
    }
  });
});

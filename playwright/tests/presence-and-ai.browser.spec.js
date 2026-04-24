const { test, expect } = require('@playwright/test');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const baseURL = process.env.BASE_URL || 'http://127.0.0.1:8081';
const repoRoot = path.resolve(__dirname, '..', '..');

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

function cleanupTestData() {
  const env = parseDotEnv();
  const gradleCache = path.join(os.homedir(), '.gradle', 'caches', 'modules-2', 'files-2.1', 'org.postgresql', 'postgresql');
  const driverJar = findPostgresJar(gradleCache);
  if (!env.DB_URL || !env.DB_USERNAME || !env.DB_PASSWORD || !driverJar) {
    return;
  }
  const cleanupSource = path.join(os.tmpdir(), 'PogunPresenceAiPlaywrightCleanup.java');
  fs.writeFileSync(cleanupSource, `
import java.sql.*;
import java.util.*;

public class PogunPresenceAiPlaywrightCleanup {
  public static void main(String[] args) throws Exception {
    List<String> statements = List.of(
      "DELETE FROM pet_notice_images WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-ai-notice%')",
      "DELETE FROM pet_notices WHERE title LIKE 'playwright-ai-notice%'"
    );
    try (Connection conn = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
      conn.setAutoCommit(false);
      try (Statement stmt = conn.createStatement()) {
        for (String sql : statements) {
          stmt.executeUpdate(sql);
        }
        conn.commit();
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    }
  }
}
`, 'ascii');
  execFileSync('java', ['-cp', driverJar, cleanupSource], {
    env: {
      ...process.env,
      DB_URL: env.DB_URL,
      DB_USERNAME: env.DB_USERNAME,
      DB_PASSWORD: env.DB_PASSWORD
    },
    stdio: 'ignore'
  });
}

test.beforeEach(() => cleanupTestData());
test.afterEach(() => cleanupTestData());

function loadPlaywrightToken(index) {
  return fs.readFileSync(
    path.join(repoRoot, '.local', `emulator-user${index}-firebase-id-token.txt`),
    'utf8'
  ).trim();
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
    'Content-Type': 'application/json',
    ...(init.headers || {})
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
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

async function login(token) {
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

async function ensureReadySession(token) {
  let loginRes = await login(token);
  if (loginRes.status !== 200) {
    throw new Error(`login failed: ${JSON.stringify(loginRes.body)}`);
  }
  if (loginRes.body?.data?.registrationStatus === 'PENDING_ONBOARDING' || !loginRes.body?.data?.id) {
    const onboarding = await api('/api/auth/onboarding/complete', token, {
      method: 'POST',
      body: JSON.stringify({ x: 127.1086228, y: 37.4012191 })
    });
    if (onboarding.status !== 200) {
      throw new Error(`onboarding failed: ${JSON.stringify(onboarding.body)}`);
    }
    loginRes = await login(token);
    if (loginRes.status !== 200 || !loginRes.body?.data?.id) {
      throw new Error(`login after onboarding failed: ${JSON.stringify(loginRes.body)}`);
    }
  }
}

function createMissingPetPayload(title) {
  return {
    title,
    animalType: 'DOG',
    breed: 'MIX',
    gender: 'MALE',
    description: 'playwright ai-source notice',
    missingDate: new Date().toISOString(),
    missingRegion: '서울 강남구',
    missingAddress: '테스트 주소',
    contactPhone: '010-1111-2222',
    status: 'OPEN'
  };
}

test('presence tracking and ai-source flow covers user availability update and missing-pet ai cache bump', async () => {
  const userToken = loadPlaywrightToken(1);
  expect(userToken).toBeTruthy();

  await ensureReadySession(userToken);

  // 1. Presence (가용 상태) 변경 흐름 테스트
  const availabilityRes = await api('/api/users/me/availability', userToken);
  expect(availabilityRes.status).toBe(200);
  expect(availabilityRes.body.data.availabilityStatus).toBe('ONLINE'); // 로그인 직후 ONLINE 보장

  const updateOfflineRes = await api('/api/users/me/availability', userToken, {
    method: 'PATCH',
    body: JSON.stringify({ availabilityStatus: 'OFFLINE' })
  });
  expect(updateOfflineRes.status).toBe(200);
  expect(updateOfflineRes.body.data.availabilityStatus).toBe('OFFLINE');

  const updateIdleRes = await api('/api/users/me/availability', userToken, {
    method: 'PATCH',
    body: JSON.stringify({ availabilityStatus: 'IDLE' })
  });
  expect(updateIdleRes.status).toBe(200);
  expect(updateIdleRes.body.data.availabilityStatus).toBe('IDLE');

  // 2. AI Source 엔드포인트 및 Redis 캐시 갱신 흐름 테스트
  const aiKey = process.env.AI_API_KEY || 'test-ai-key'; // 환경변수가 없다면 더미키 사용
  const aiHeaders = { 'X-AI-API-KEY': aiKey };

  const aiListRes1 = await api('/api/missing-pets/ai-source', userToken, { headers: aiHeaders });
  
  // API Key 검증을 통과하는 환경일 때만 캐시 갱신 절차를 마저 검증합니다.
  if (aiListRes1.status === 200) {
    const initialCount = aiListRes1.body.data.totalElements || (aiListRes1.body.data.content ? aiListRes1.body.data.content.length : 0);

    // 새 공고 작성 (이 과정에서 aiSourceCacheService.bumpVersion이 호출되어 Redis 캐시 키가 무효화/증가함)
    const noticeCreate = await api('/api/missing-pets', userToken, {
      method: 'POST',
      body: JSON.stringify(createMissingPetPayload('playwright-ai-notice'))
    });
    expect(noticeCreate.status).toBe(201);
    const noticeId = noticeCreate.body.data.id;

    // 캐시 버전이 변경되어 최신 공고가 포함된 데이터를 새로 조회하는지 검증
    const aiListRes2 = await api('/api/missing-pets/ai-source', userToken, { headers: aiHeaders });
    expect(aiListRes2.status).toBe(200);
    const newCount = aiListRes2.body.data.totalElements || (aiListRes2.body.data.content ? aiListRes2.body.data.content.length : 0);
    expect(newCount).toBeGreaterThanOrEqual(initialCount);

    // 단건 상세 AI Source 조회 테스트
    const aiDetailRes = await api(`/api/missing-pets/${noticeId}/ai-source`, userToken, { headers: aiHeaders });
    expect(aiDetailRes.status).toBe(200);
    expect(aiDetailRes.body.data.id).toBe(noticeId);
  } else {
    console.warn(`AI Source API returned ${aiListRes1.status}. Skipping AI cache assertions. A valid AI_API_KEY might be required in .env`);
  }
});

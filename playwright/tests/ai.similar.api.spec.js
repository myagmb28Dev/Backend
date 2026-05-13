const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const baseURL = process.env.BASE_URL || 'http://127.0.0.1:8081';
const repoRoot = path.resolve(__dirname, '..', '..');

function parseDotEnv() {
  const envPath = path.join(repoRoot, '.env');
  if (!fs.existsSync(envPath)) return {};
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
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${baseURL}${pathname}`, {
    ...init,
    headers
  });

  return { status: response.status, body: await readJsonSafe(response) };
}

async function login(token) {
  return api('/api/auth/login', null, {
    method: 'POST',
    body: JSON.stringify({ firebaseIdToken: token })
  });
}

function loadPlaywrightToken(index) {
  return fs.readFileSync(
    path.join(repoRoot, '.local', `emulator-user${index}-firebase-id-token.txt`),
    'utf8'
  ).trim();
}

async function resolveReadyToken() {
  const candidates = [1, 2, 3, 4];
  for (const index of candidates) {
    const tokenPath = path.join(repoRoot, '.local', `emulator-user${index}-firebase-id-token.txt`);
    if (!fs.existsSync(tokenPath)) continue;
    const token = loadPlaywrightToken(index);
    const loginRes = await login(token);
    if (loginRes.status === 200 && loginRes.body?.data?.id && loginRes.body?.data?.registrationStatus !== 'PENDING_ONBOARDING') {
      return token;
    }
  }
  throw new Error('No ready local emulator token found. Prepare an onboarded local user token first.');
}

function createMissingPetPayload(title) {
  return {
    title,
    animalType: 'CAT',
    breed: 'KOREAN_SHORTHAIR',
    gender: 'FEMALE',
    description: 'playwright ai similar test',
    missingDate: new Date().toISOString(),
    missingRegion: '서울 강남구',
    missingAddress: '테스트 주소',
    contactPhone: '010-1111-2222',
    status: 'OPEN'
  };
}

test('api/ai/similar returns inferred notice type and detail path', async () => {
  const env = parseDotEnv();
  const aiApiKey = env.APP_AI_API_KEY || env.AI_API_KEY || process.env.APP_AI_API_KEY || process.env.AI_API_KEY;
  expect(aiApiKey, 'AI API key is required for analysis-result callback test').toBeTruthy();

  const token = await resolveReadyToken();

  const createdNoticeIds = [];
  try {
    const first = await api('/api/missing-pets', token, {
      method: 'POST',
      body: JSON.stringify(createMissingPetPayload(`playwright-ai-similar-src-${Date.now()}`))
    });
    expect(first.status).toBe(201);
    const sourceNoticeId = first.body?.data?.id;
    expect(sourceNoticeId).toBeTruthy();
    createdNoticeIds.push(sourceNoticeId);

    const second = await api('/api/missing-pets', token, {
      method: 'POST',
      body: JSON.stringify(createMissingPetPayload(`playwright-ai-similar-ref-${Date.now()}`))
    });
    expect(second.status).toBe(201);
    const similarMissingNoticeId = second.body?.data?.id;
    expect(similarMissingNoticeId).toBeTruthy();
    createdNoticeIds.push(similarMissingNoticeId);

    const callback = await api(`/api/missing-pets/${sourceNoticeId}/analysis-result`, null, {
      method: 'POST',
      headers: { 'X-AI-API-KEY': aiApiKey },
      body: JSON.stringify({
        status: 'SUCCESS',
        similarNoticeIds: [similarMissingNoticeId, '9'],
        features: ['cat', 'tabby'],
        provider: 'playwright-test'
      })
    });
    expect(callback.status).toBe(201);

    const similar = await api(
      `/api/ai/similar?targetType=MISSING_PET&targetId=${encodeURIComponent(sourceNoticeId)}`,
      token
    );
    expect(similar.status).toBe(200);
    expect(similar.body?.data?.targetType).toBe('MISSING_PET');
    expect(similar.body?.data?.targetId).toBe(sourceNoticeId);
    expect(similar.body?.data?.count).toBe(2);

    const items = similar.body?.data?.items || [];
    const missingItem = items.find((item) => item.noticeId === similarMissingNoticeId);
    const shelterItem = items.find((item) => item.noticeId === '9');

    expect(missingItem?.noticeType).toBe('MISSING_PET');
    expect(missingItem?.detailPath).toBe(`/api/missing-pets/${similarMissingNoticeId}`);
    expect(shelterItem?.noticeType).toBe('SHELTER');
    expect(shelterItem?.detailPath).toBe('/api/shelter/9');
  } finally {
    for (const noticeId of createdNoticeIds) {
      await api(`/api/missing-pets/${noticeId}`, token, { method: 'DELETE' });
    }
  }
});

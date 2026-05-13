const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const BASE_URL = process.env.BASE_URL || "http://127.0.0.1:8081";
const AUTH_EMULATOR_URL = process.env.AUTH_EMULATOR_URL || "http://127.0.0.1:9099";
const REPO_ROOT = path.resolve(__dirname, "..", "..");

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return {};
  return Object.fromEntries(
    fs.readFileSync(filePath, "utf8")
      .split(/\r?\n/)
      .filter((line) => line && !line.trimStart().startsWith("#") && line.includes("="))
      .map((line) => {
        const idx = line.indexOf("=");
        return [line.slice(0, idx).trim(), line.slice(idx + 1).trim()];
      })
  );
}

const envLocal = loadEnvFile(path.join(REPO_ROOT, ".env.local"));
const AI_API_KEY =
  process.env.AI_API_KEY ||
  process.env.APP_AI_API_KEY ||
  envLocal.AI_API_KEY ||
  envLocal.APP_AI_API_KEY ||
  "";

const TEST_EMAIL = `playwright-user2@local.dev`;
const TEST_PASSWORD = "Test1234!";

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function api(request, path, { method = "GET", token, body, headers = {} } = {}) {
  const reqHeaders = { ...headers };
  if (token) reqHeaders.Authorization = `Bearer ${token}`;
  if (body !== undefined && !reqHeaders["Content-Type"]) {
    reqHeaders["Content-Type"] = "application/json";
  }
  const res = await request.fetch(`${BASE_URL}${path}`, {
    method,
    headers: reqHeaders,
    data: body
  });
  return { status: res.status(), body: await readJsonSafe(res) };
}

async function ensureEmulatorUserToken(email, password) {
  const signIn = async () => {
    const res = await fetch(
      `${AUTH_EMULATOR_URL}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, returnSecureToken: true })
      }
    );
    return { status: res.status, body: await readJsonSafe(res) };
  };
  const signUp = async () => {
    const res = await fetch(
      `${AUTH_EMULATOR_URL}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, returnSecureToken: true })
      }
    );
    return { status: res.status, body: await readJsonSafe(res) };
  };

  let result = await signIn();
  if (result.status === 200 && result.body?.idToken) return result.body.idToken;
  await signUp();
  result = await signIn();
  if (result.status !== 200 || !result.body?.idToken) {
    throw new Error(`failed to get emulator token: ${JSON.stringify(result.body)}`);
  }
  return result.body.idToken;
}

async function loginBackend(request, firebaseIdToken) {
  return api(request, "/api/auth/login", {
    method: "POST",
    body: { firebaseIdToken },
    token: firebaseIdToken
  });
}

async function ensureReadySession(request, firebaseIdToken) {
  let login = await loginBackend(request, firebaseIdToken);
  if (login.status !== 200) {
    throw new Error(`login failed: ${JSON.stringify(login.body)}`);
  }
  if (login.body?.data?.id) return login.body.data;
  if (String(login.body?.data?.registrationStatus || "") !== "PENDING_ONBOARDING") {
    throw new Error(`unexpected login response: ${JSON.stringify(login.body)}`);
  }

  const onboarding = await api(request, "/api/auth/onboarding/complete", {
    method: "POST",
    token: firebaseIdToken,
    body: { x: 127.1086228, y: 37.4012191 }
  });
  if (onboarding.status !== 200) {
    throw new Error(`onboarding failed: ${JSON.stringify(onboarding.body)}`);
  }

  login = await loginBackend(request, firebaseIdToken);
  if (login.status !== 200 || !login.body?.data?.id) {
    throw new Error(`login retry failed: ${JSON.stringify(login.body)}`);
  }
  return login.body.data;
}

function noticePayload(title) {
  return {
    title,
    animalType: "DOG",
    breed: "MIX",
    gender: "MALE",
    description: "playwright ai analysis test",
    missingDate: new Date().toISOString(),
    missingRegion: "서울 강남구",
    missingAddress: "테스트 주소",
    rewardAmount: 0,
    contactPhone: "010-1111-2222",
    status: "OPEN"
  };
}

test("ai analysis callback validates similar ids and deduplicates notification", async ({ request }) => {
  test.skip(!AI_API_KEY, "AI_API_KEY required");

  const firebaseToken = await ensureEmulatorUserToken(TEST_EMAIL, TEST_PASSWORD);
  await ensureReadySession(request, firebaseToken);

  const suffix = Date.now();
  const baseTitle = `playwright-ai-analysis-${suffix}`;
  let noticeAId = null;
  let noticeBId = null;

  try {
    const createA = await api(request, "/api/missing-pets", {
      method: "POST",
      token: firebaseToken,
      body: noticePayload(`${baseTitle}-a`)
    });
    expect(createA.status).toBe(201);
    noticeAId = createA.body?.data?.id;
    expect(Boolean(noticeAId)).toBeTruthy();

    const createB = await api(request, "/api/missing-pets", {
      method: "POST",
      token: firebaseToken,
      body: noticePayload(`${baseTitle}-b`)
    });
    expect(createB.status).toBe(201);
    noticeBId = createB.body?.data?.id;
    expect(Boolean(noticeBId)).toBeTruthy();

    const beforeUnread = await api(request, "/api/notifications/unread-count", {
      token: firebaseToken
    });
    expect(beforeUnread.status).toBe(200);
    const unreadBefore = Number(beforeUnread.body?.data?.unreadCount ?? 0);

    const callbackBody = {
      status: "done",
      breed: "MIX",
      color: "BROWN",
      confidence: 0.92,
      provider: "playwright-ai",
      similarNoticeIds: [
        noticeAId, // self -> filtered
        "00000000-0000-0000-0000-000000000000", // missing uuid -> filtered
        "999999999", // missing shelter id -> filtered
        noticeBId, // valid
        noticeBId // duplicate -> filtered
      ],
      analyzedAt: new Date().toISOString(),
      features: ["ear", "nose"]
    };

    const cb1 = await api(request, `/api/missing-pets/${noticeAId}/analysis-result`, {
      method: "POST",
      headers: { "X-AI-API-KEY": AI_API_KEY },
      body: callbackBody
    });
    expect(cb1.status).toBe(201);
    expect(cb1.body?.data?.targetId).toBe(String(noticeAId));

    const afterFirstUnread = await api(request, "/api/notifications/unread-count", {
      token: firebaseToken
    });
    expect(afterFirstUnread.status).toBe(200);
    const unreadAfterFirst = Number(afterFirstUnread.body?.data?.unreadCount ?? 0);
    expect(unreadAfterFirst).toBeGreaterThanOrEqual(unreadBefore + 1);

    const listFirst = await api(
      request,
      "/api/notifications?page=0&size=10&type=AI_SIMILAR_NOTICE_FOUND",
      { token: firebaseToken }
    );
    expect(listFirst.status).toBe(200);
    const firstItem = listFirst.body?.data?.items?.[0];
    expect(Boolean(firstItem)).toBeTruthy();
    expect(String(firstItem?.metadata?.similarCount)).toBe("1");

    const similarList = await api(request, `/api/missing-pets/${noticeAId}/similar-notices`, {
      token: firebaseToken
    });
    expect(similarList.status).toBe(200);
    expect(similarList.body?.data?.targetType).toBe("MISSING_PET");
    expect(similarList.body?.data?.targetId).toBe(String(noticeAId));
    expect(Number(similarList.body?.data?.count ?? 0)).toBe(1);
    expect(similarList.body?.data?.items?.[0]?.noticeId).toBe(String(noticeBId));
    expect(similarList.body?.data?.items?.[0]?.noticeType).toBe("MISSING_PET");

    const cb2 = await api(request, `/api/missing-pets/${noticeAId}/analysis-result`, {
      method: "POST",
      headers: { "X-AI-API-KEY": AI_API_KEY },
      body: callbackBody
    });
    expect(cb2.status).toBe(201);

    const afterSecondUnread = await api(request, "/api/notifications/unread-count", {
      token: firebaseToken
    });
    expect(afterSecondUnread.status).toBe(200);
    const unreadAfterSecond = Number(afterSecondUnread.body?.data?.unreadCount ?? 0);
    expect(unreadAfterSecond).toBe(unreadAfterFirst);
  } finally {
    if (noticeAId) {
      await api(request, `/api/missing-pets/${noticeAId}`, {
        method: "DELETE",
        token: firebaseToken
      });
    }
    if (noticeBId) {
      await api(request, `/api/missing-pets/${noticeBId}`, {
        method: "DELETE",
        token: firebaseToken
      });
    }
  }
});

const { test, expect } = require('@playwright/test');

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:8080';
const EMULATOR_BASE = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const REAL_SESSION_KEY = 'pogun-real-firebase-session-v1';
const ACTIVE_ROLE_KEY = 'dm-test-active-role-v1';
const REAL_ROLE = 'google';

const USER1 = { email: 'playwright-user1@local.dev', password: 'Test1234!' };
const USER2 = { email: 'playwright-user2@local.dev', password: 'Test1234!' };

const SAMPLES = Number(process.env.DM_LATENCY_SAMPLES || 6);
const WARMUP_SAMPLES = Number(process.env.DM_WARMUP_SAMPLES || 4);
const MAX_RECEIVE_MS = Number(process.env.DM_MAX_RECEIVE_MS || 1200);
const MAX_READ_MS = Number(process.env.DM_MAX_READ_MS || 1500);
const MAX_RECEIVE_P95_MS = Number(process.env.DM_MAX_RECEIVE_P95_MS || 2000);
const MAX_READ_P95_MS = Number(process.env.DM_MAX_READ_P95_MS || 2200);
const ALLOWED_OUTLIERS = Number(process.env.DM_ALLOWED_OUTLIERS || 2);

async function emulatorSignIn(request, email, password) {
  let response = await request.post(`${EMULATOR_BASE}/accounts:signInWithPassword?key=fake-api-key`, {
    data: { email, password, returnSecureToken: true }
  });
  if (response.status() !== 200) {
    const body = await response.json().catch(() => ({}));
    const msg = body?.error?.message || '';
    if (msg.includes('EMAIL_NOT_FOUND')) {
      const signUp = await request.post(`${EMULATOR_BASE}/accounts:signUp?key=fake-api-key`, {
        data: { email, password, returnSecureToken: true }
      });
      expect(signUp.status()).toBe(200);
      response = await request.post(`${EMULATOR_BASE}/accounts:signInWithPassword?key=fake-api-key`, {
        data: { email, password, returnSecureToken: true }
      });
    }
  }
  expect(response.status()).toBe(200);
  return response.json();
}

async function backendLogin(request, idToken) {
  const response = await request.post(`${BASE_URL}/api/auth/login`, {
    data: { firebaseIdToken: idToken }
  });
  return { status: response.status(), body: await response.json() };
}

async function ensureReadySession(request, account) {
  const emu = await emulatorSignIn(request, account.email, account.password);
  const idToken = emu.idToken;

  let login = await backendLogin(request, idToken);
  expect(login.status).toBe(200);

  if (login.body?.data?.registrationStatus === 'PENDING_ONBOARDING' || !login.body?.data?.id) {
    const onboard = await request.post(`${BASE_URL}/api/auth/onboarding/complete`, {
      headers: { Authorization: `Bearer ${idToken}` },
      data: { x: 127.1086228, y: 37.4012191 }
    });
    expect(onboard.status()).toBe(200);
    login = await backendLogin(request, idToken);
    expect(login.status).toBe(200);
  }

  const data = login.body.data;
  return {
    token: idToken,
    userId: data.id,
    session: {
      firebaseIdToken: idToken,
      refreshToken: emu.refreshToken || '',
      firebaseUid: data.firebaseUid || '',
      email: data.email || account.email,
      nickname: data.nickname || data.email || account.email,
      profileImageUrl: data.profileImageUrl || '',
      userId: data.id || null,
      registrationStatus: data.registrationStatus || 'COMPLETED'
    }
  };
}

async function api(request, path, token, method = 'GET', data = undefined) {
  const response = await request.fetch(`${BASE_URL}${path}`, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data
  });
  const body = await response.json().catch(() => null);
  return { status: response.status(), body };
}

function percentile(values, p) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[index];
}

function summary(values) {
  const avg = values.reduce((acc, n) => acc + n, 0) / values.length;
  return {
    min: Math.min(...values),
    p50: percentile(values, 50),
    avg: Math.round(avg),
    p95: percentile(values, 95),
    max: Math.max(...values)
  };
}

async function bootstrapDmPage(context, session, noticeTitle) {
  await context.addInitScript(
    ({ realSessionKey, activeRoleKey, role, userSession }) => {
      localStorage.setItem(realSessionKey, JSON.stringify(userSession));
      localStorage.setItem(activeRoleKey, role);
    },
    {
      realSessionKey: REAL_SESSION_KEY,
      activeRoleKey: ACTIVE_ROLE_KEY,
      role: REAL_ROLE,
      userSession: session
    }
  );
  const page = await context.newPage();
  page.on('console', (msg) => {
    const text = msg.text();
    if (text && (text.includes('[dm-') || text.includes('웹소켓') || text.includes('socket'))) {
      // eslint-disable-next-line no-console
      console.log(`[dm-page-console] ${text}`);
    }
  });
  page.on('pageerror', (error) => {
    // eslint-disable-next-line no-console
    console.log(`[dm-page-error] ${error?.message || error}`);
  });
  await page.goto(`${BASE_URL}/Full_Compact.html?view=dm`, {
    waitUntil: 'domcontentloaded'
  });
  await page.click('button[data-view="dm"]');

  const dmIframeSelector = '#view-dm iframe[data-view-frame="dm"]';
  const dmIframe = page.locator(dmIframeSelector);
  await expect(dmIframe).toBeVisible({ timeout: 20000 });
  const iframeHandle = await dmIframe.elementHandle();
  const frame = await iframeHandle?.contentFrame();
  if (!frame) {
    throw new Error('DM iframe을 로드하지 못했습니다.');
  }

  await expect(frame.locator('#accountHint')).toContainText(session.email, { timeout: 30000 });
  const roomItem = frame.locator('#roomList .room-item', { hasText: noticeTitle }).first();
  await expect(roomItem).toBeVisible({ timeout: 45000 });

  let roomReady = false;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await roomItem.click();
    try {
      await frame.waitForFunction(() => {
        const state = window.__dmTestState?.();
        const input = document.querySelector('#messageInput');
        return Boolean(state?.roomReady && state?.roomId && input && !input.disabled);
      }, undefined, { timeout: 10000 });
      roomReady = true;
      break;
    } catch {
      await page.waitForTimeout(400);
    }
  }

  if (!roomReady) {
    const state = await frame.evaluate(() => window.__dmTestState?.() || null);
    throw new Error(`DM 방 준비 실패: ${JSON.stringify(state)}`);
  }

  await expect(frame.locator('#messageInput')).toBeEnabled({ timeout: 10000 });
  return { page, frame };
}

test.describe.serial('dm realtime latency', () => {
  test('sender->receiver latency and read receipt latency are near-real-time', async ({ browser, request }) => {
    test.setTimeout(240000);

    const user1 = await ensureReadySession(request, USER1);
    const user2 = await ensureReadySession(request, USER2);

    const createdNotice = await api(
      request,
      '/api/missing-pets',
      user2.token,
      'POST',
      {
        title: `dm-latency-${Date.now()}`,
        animalType: 'DOG',
        breed: 'MIX',
        gender: 'MALE',
        description: 'dm latency measurement notice',
        missingDate: new Date().toISOString(),
        missingRegion: '서울 강남구',
        missingAddress: '역삼역',
        contactPhone: '010-0000-0000',
        status: 'OPEN'
      }
    );
    expect(createdNotice.status).toBe(201);
    const noticeId = createdNotice.body?.data?.id;
    expect(noticeId).toBeTruthy();

    const roomCreate = await api(request, `/api/chat/rooms/notice/${noticeId}`, user1.token, 'POST');
    expect([200, 201]).toContain(roomCreate.status);
    const roomId = roomCreate.body?.data?.roomId;
    expect(roomId).toBeTruthy();

    const senderContext = await browser.newContext();
    const receiverContext = await browser.newContext();

    const sender = await bootstrapDmPage(senderContext, user1.session, createdNotice.body?.data?.title);
    const receiver = await bootstrapDmPage(receiverContext, user2.session, createdNotice.body?.data?.title);

    const receiveLatencies = [];
    const readLatencies = [];
    const details = [];
    let senderDebugFrames = [];
    let receiverDebugFrames = [];

    const totalIterations = WARMUP_SAMPLES + SAMPLES;
    for (let i = 0; i < totalIterations; i += 1) {
      const isWarmup = i < WARMUP_SAMPLES;
      const measuredIndex = i - WARMUP_SAMPLES + 1;
      const token = `dm-latency-msg-${Date.now()}-${i}`;
      const startedAt = Date.now();

      await sender.frame.locator('#messageInput').fill(token);
      await sender.frame.locator('#messageInput').press('Enter');

      await expect(receiver.frame.locator('#feed')).toContainText(token, { timeout: 10000 });
      const receiveMs = Date.now() - startedAt;
      if (!isWarmup) {
        receiveLatencies.push(receiveMs);
      }

      const myRow = sender.frame.locator('#feed .message-row.mine', { hasText: token }).first();
      await expect(myRow.locator('.read-receipt')).toBeVisible({ timeout: 10000 });
      const readMs = Date.now() - startedAt;
      if (!isWarmup) {
        readLatencies.push(readMs);
      }
      details.push({
        sample: isWarmup ? `warmup-${i + 1}` : measuredIndex,
        warmup: isWarmup,
        token,
        receiveMs,
        readMs
      });
      senderDebugFrames = await sender.frame.evaluate(() => (window.__dmTestState?.().debugFrames || []).slice(-25));
      receiverDebugFrames = await receiver.frame.evaluate(() => (window.__dmTestState?.().debugFrames || []).slice(-25));
    }

    expect(receiveLatencies.length).toBeGreaterThan(0);
    expect(readLatencies.length).toBeGreaterThan(0);
    const receiveStats = summary(receiveLatencies);
    const readStats = summary(readLatencies);
    const receiveOutliers = receiveLatencies.filter((ms) => ms > MAX_RECEIVE_MS);
    const readOutliers = readLatencies.filter((ms) => ms > MAX_READ_MS);
    const result = {
      warmupSamples: WARMUP_SAMPLES,
      measuredSamples: SAMPLES,
      threshold: {
        receiveMs: MAX_RECEIVE_MS,
        readMs: MAX_READ_MS,
        receiveP95Ms: MAX_RECEIVE_P95_MS,
        readP95Ms: MAX_READ_P95_MS,
        allowedOutliers: ALLOWED_OUTLIERS
      },
      receive: receiveStats,
      read: readStats,
      outliers: {
        receiveCount: receiveOutliers.length,
        readCount: readOutliers.length,
        receive: receiveOutliers,
        read: readOutliers
      },
      samples: details,
      senderDebugFrames,
      receiverDebugFrames
    };
    console.log('[dm-latency-result]', JSON.stringify(result, null, 2));
    await test.info().attach('dm-latency-result.json', {
      body: JSON.stringify(result, null, 2),
      contentType: 'application/json'
    });

    await sender.page.close();
    await receiver.page.close();
    await senderContext.close();
    await receiverContext.close();

    expect(receiveStats.p50).toBeLessThanOrEqual(MAX_RECEIVE_MS);
    expect(receiveStats.p95).toBeLessThanOrEqual(MAX_RECEIVE_P95_MS);
    expect(readStats.p50).toBeLessThanOrEqual(MAX_READ_MS);
    expect(readStats.p95).toBeLessThanOrEqual(MAX_READ_P95_MS);
    expect(receiveOutliers.length).toBeLessThanOrEqual(ALLOWED_OUTLIERS);
    expect(readOutliers.length).toBeLessThanOrEqual(ALLOWED_OUTLIERS);
  });
});

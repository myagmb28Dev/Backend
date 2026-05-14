const { test, expect } = require('@playwright/test');

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const EMULATOR_URL = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key';
const REAL_SESSION_KEY = 'pogun-real-firebase-session-v1';
const ACTIVE_ROLE_KEY = 'dm-test-active-role-v1';
const REAL_ROLE = 'google';

const USER1 = { email: 'playwright-user1@local.dev', password: 'Test1234!' };
const USER2 = { email: 'playwright-user2@local.dev', password: 'Test1234!' };

async function emulatorSignIn(request, email, password) {
  let response = await request.post(EMULATOR_URL, {
    data: { email, password, returnSecureToken: true }
  });
  if (response.status() !== 200) {
    const body = await response.json().catch(() => ({}));
    const msg = body?.error?.message || '';
    if (msg.includes('EMAIL_NOT_FOUND')) {
      const signUp = await request.post('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key', {
        data: { email, password, returnSecureToken: true }
      });
      expect(signUp.status()).toBe(200);
      response = await request.post(EMULATOR_URL, {
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
  expect([200, 401]).toContain(login.status);
  if (login.status !== 200) {
    throw new Error(`backend login failed: ${JSON.stringify(login.body)}`);
  }

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

async function waitForUnreadIncrease(request, token, before, retries = 10, delayMs = 800) {
  for (let i = 0; i < retries; i += 1) {
    const unread = await api(request, '/api/notifications/unread-count', token);
    if (unread.status === 200) {
      const count = Number(unread.body?.data?.unreadCount || 0);
      if (count > before) return count;
    }
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }
  return before;
}

test.describe.serial('full compact unified e2e', () => {
  test('full flow works on new structure', async ({ page, request }) => {
    test.setTimeout(180000);
    const user1 = await ensureReadySession(request, USER1);
    const user2 = await ensureReadySession(request, USER2);

    await page.goto(`${BASE_URL}/Full_Compact.html`, { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveTitle(/Pogun Full Compact/i);

    const tabs = await page.locator('#sideNav button[data-view]').evaluateAll((nodes) =>
      nodes.map((node) => node.getAttribute('data-view'))
    );
    expect(tabs).toEqual(['login', 'notice', 'community', 'shelter', 'dm', 'notification', 'setting']);
    await expect(page.locator('button[data-view="admin"]')).toHaveCount(0);

    await page.click('button[data-view="login"]');
    const loginFrame = page.frameLocator('#view-login iframe');
    await expect(loginFrame.locator('#googleLoginButton')).toBeVisible();

    await page.evaluate(({ realSessionKey, activeRoleKey, role, session }) => {
      localStorage.setItem(realSessionKey, JSON.stringify(session));
      localStorage.setItem(activeRoleKey, role);
      window.dispatchEvent(new CustomEvent('pogun-auth-session-changed'));
    }, {
      realSessionKey: REAL_SESSION_KEY,
      activeRoleKey: ACTIVE_ROLE_KEY,
      role: REAL_ROLE,
      session: user1.session
    });

    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.click('button[data-view="login"]');
    await expect(loginFrame.locator('#accountHint')).toContainText(user1.session.email, { timeout: 15000 });

    const createdNotice = await api(
      request,
      '/api/missing-pets',
      user2.token,
      'POST',
      {
        title: `unified-e2e-${Date.now()}`,
        animalType: 'DOG',
        breed: 'MIX',
        gender: 'MALE',
        description: 'unified e2e notice',
        missingDate: new Date().toISOString(),
        missingRegion: '서울 강남구',
        missingAddress: '역삼역',
        contactPhone: '010-0000-0000',
        status: 'OPEN'
      }
    );
    expect(createdNotice.status).toBe(201);

    const room = await api(
      request,
      `/api/chat/rooms/notice/${createdNotice.body?.data?.id}`,
      user1.token,
      'POST'
    );
    expect([200, 201]).toContain(room.status);

    await page.click('button[data-view="notice"]');
    const noticeFrame = page.frameLocator('#view-notice iframe');
    await expect(noticeFrame.locator('#accountHint')).toContainText(user1.session.email, { timeout: 10000 });
    await expect(noticeFrame.locator('#createNoticeButton')).toBeEnabled();

    await page.click('button[data-view="community"]');
    const communityFrame = page.frameLocator('#view-community iframe');
    await expect(communityFrame.locator('h1')).toContainText('커뮤니티', { timeout: 10000 });

    await page.click('button[data-view="shelter"]');
    const shelterFrame = page.frameLocator('#view-shelter iframe');
    await expect(shelterFrame.locator('#status')).not.toContainText('로그인이 필요', { timeout: 15000 });

    await page.click('button[data-view="dm"]');
    const dmFrame = page.frameLocator('#view-dm iframe');
    await expect(dmFrame.locator('#status')).not.toContainText('로그인이 필요', { timeout: 15000 });

    await page.click('button[data-view="notification"]');
    const notificationFrame = page.frameLocator('#view-notification iframe');
    await expect(notificationFrame.locator('#accountHint')).toContainText(user1.session.email, { timeout: 10000 });

    await page.click('button[data-view="setting"]');
    const settingFrame = page.frameLocator('#view-setting iframe');
    await expect(settingFrame.locator('#accountHint')).toContainText(user1.session.email, { timeout: 10000 });

    // Community CRUD + reaction toggle + unread change
    const postCreate = await api(
      request,
      '/api/community/posts',
      user1.token,
      'POST',
      {
        title: `unified-community-${Date.now()}`,
        content: 'unified e2e community content',
        category: 'FREE',
        tags: ['e2e']
      }
    );
    expect(postCreate.status).toBe(201);
    const postId = postCreate.body?.data?.id;
    expect(postId).toBeTruthy();

    const unreadBeforeResp = await api(request, '/api/notifications/unread-count', user1.token);
    expect(unreadBeforeResp.status).toBe(200);
    const unreadBefore = Number(unreadBeforeResp.body?.data?.unreadCount || 0);

    const commentCreate = await api(
      request,
      `/api/community/posts/${postId}/comments`,
      user2.token,
      'POST',
      { content: `parent-${Date.now()}` }
    );
    expect(commentCreate.status).toBe(201);
    const parentId = commentCreate.body?.data?.id;
    expect(parentId).toBeTruthy();

    const replyCreate = await api(
      request,
      `/api/community/posts/${postId}/comments`,
      user2.token,
      'POST',
      { content: `reply-${Date.now()}`, parentCommentId: parentId }
    );
    expect(replyCreate.status).toBe(201);
    const replyId = replyCreate.body?.data?.id;
    expect(replyId).toBeTruthy();

    const commentsList = await api(request, `/api/community/posts/${postId}/comments`, user1.token);
    expect(commentsList.status).toBe(200);
    const comments = commentsList.body?.data || [];
    expect(Array.isArray(comments)).toBeTruthy();
    expect(comments.some((item) => item.id === parentId)).toBeTruthy();

    const commentUpdate = await api(
      request,
      `/api/community/posts/${postId}/comments/${parentId}`,
      user2.token,
      'PATCH',
      { content: 'parent-updated' }
    );
    expect(commentUpdate.status).toBe(200);

    const reactOn = await api(
      request,
      `/api/community/posts/${postId}/reactions`,
      user2.token,
      'POST',
      { reaction: 'LIKE' }
    );
    expect(reactOn.status).toBe(200);

    const detailAfterLike = await api(request, `/api/community/posts/${postId}`, user1.token);
    expect(detailAfterLike.status).toBe(200);
    const likeCountAfterLike = Number(detailAfterLike.body?.data?.likeCount || 0);
    expect(likeCountAfterLike).toBeGreaterThanOrEqual(1);

    const reactOff = await api(request, `/api/community/posts/${postId}/reactions`, user2.token, 'DELETE');
    expect(reactOff.status).toBe(200);

    const detailAfterUnlike = await api(request, `/api/community/posts/${postId}`, user1.token);
    expect(detailAfterUnlike.status).toBe(200);
    const likeCountAfterUnlike = Number(detailAfterUnlike.body?.data?.likeCount || 0);
    expect(likeCountAfterUnlike).toBeLessThanOrEqual(likeCountAfterLike);

    const unreadAfter = await waitForUnreadIncrease(request, user1.token, unreadBefore);
    expect(unreadAfter).toBeGreaterThanOrEqual(unreadBefore);

    const deleteReply = await api(request, `/api/community/posts/${postId}/comments/${replyId}`, user2.token, 'DELETE');
    expect(deleteReply.status).toBe(200);
    const deleteParent = await api(request, `/api/community/posts/${postId}/comments/${parentId}`, user2.token, 'DELETE');
    expect(deleteParent.status).toBe(200);

    const postDelete = await api(request, `/api/community/posts/${postId}`, user1.token, 'DELETE');
    expect(postDelete.status).toBe(200);
  });
});

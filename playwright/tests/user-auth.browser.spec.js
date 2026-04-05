const { test, expect } = require('@playwright/test');

const baseURL = 'http://localhost:8080';
const emulatorBaseURL = 'http://127.0.0.1:9099';
const emulatorApiKey = 'fake-api-key';

function createTestIdentity(prefix) {
  const nonce = `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
  return {
    email: `${prefix}-${nonce}@local.dev`,
    password: 'Test1234!'
  };
}

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function emulatorSignUp(identity) {
  const response = await fetch(
    `${emulatorBaseURL}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=${emulatorApiKey}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: identity.email,
        password: identity.password,
        returnSecureToken: true
      })
    }
  );

  return {
    status: response.status,
    body: await readJsonSafe(response)
  };
}

async function emulatorSignIn(identity) {
  const response = await fetch(
    `${emulatorBaseURL}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${emulatorApiKey}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: identity.email,
        password: identity.password,
        returnSecureToken: true
      })
    }
  );

  return {
    status: response.status,
    body: await readJsonSafe(response)
  };
}

function attachObservers(page) {
  const consoleErrors = [];
  const apiResponses = [];

  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });

  page.on('response', async (response) => {
    const url = response.url();
    if (!url.includes('/api/auth') && !url.includes('/api/users/me') && !url.includes('/api/community/posts')) {
      return;
    }

    let bodyPreview = '';
    try {
      bodyPreview = (await response.text()).slice(0, 300);
    } catch (error) {
      bodyPreview = `unavailable:${error.message}`;
    }

    apiResponses.push({
      url,
      method: response.request().method(),
      status: response.status(),
      bodyPreview
    });
  });

  return { consoleErrors, apiResponses };
}

test('user and auth browser flow covers login profile activity logout relogin unlink withdraw', async ({ page }) => {
  const identity = createTestIdentity('user-auth-flow');
  const signUp = await emulatorSignUp(identity);
  expect(signUp.status).toBe(200);

  const firstToken = signUp.body.idToken;
  expect(firstToken).toBeTruthy();

  const { consoleErrors, apiResponses } = attachObservers(page);

  await page.goto(`${baseURL}/swagger-ui/index.html#/Users`, { waitUntil: 'domcontentloaded' });

  const firstRun = await page.evaluate(async ({ baseURL, firstToken, identity }) => {
    const readJson = async (response) => {
      const text = await response.text();
      try {
        return JSON.parse(text);
      } catch {
        return text;
      }
    };

    const api = async (pathname, token, init = {}) => {
      const response = await fetch(`${baseURL}${pathname}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          ...(init.headers || {})
        }
      });

      return {
        status: response.status,
        body: await readJson(response)
      };
    };

    const loginResponse = await fetch(`${baseURL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ firebaseIdToken: firstToken })
    });

    const login = {
      status: loginResponse.status,
      body: await readJson(loginResponse)
    };

    const profile = await api('/api/users/me', firstToken);
    const profileUpdate = await api('/api/users/me', firstToken, {
      method: 'PATCH',
      body: JSON.stringify({
        nickname: `flow-${identity.email.split('@')[0].slice(0, 16)}`,
        phoneNumber: '010-5555-7777',
        profileImageUrl: 'https://cdn.example.com/profile/user-auth-flow.png'
      })
    });
    const createCommunityPost = await api('/api/community/posts', firstToken, {
      method: 'POST',
      body: JSON.stringify({
        title: `User Auth Flow Post ${identity.email}`,
        content: 'Created during the combined user/auth browser flow.',
        category: 'FREE',
        tags: ['user-auth', 'playwright']
      })
    });
    const postId = createCommunityPost.body && createCommunityPost.body.data ? createCommunityPost.body.data.id : null;
    const myCommunityPosts = await api('/api/users/me/community-posts', firstToken);
    const myPosts = await api('/api/users/me/posts', firstToken);
    const unlink = await api('/api/auth/link/EMAIL', firstToken, { method: 'DELETE' });
    const logout = await api('/api/auth/logout', firstToken, { method: 'POST' });
    const afterLogoutProfile = await api('/api/users/me', firstToken);

    return {
      login,
      profile,
      profileUpdate,
      createCommunityPost,
      postId,
      myCommunityPosts,
      myPosts,
      unlink,
      logout,
      afterLogoutProfile
    };
  }, { baseURL, firstToken, identity });

  expect(firstRun.login.status).toBe(200);
  expect(firstRun.login.body.data.email).toBe(identity.email);
  expect(firstRun.profile.status).toBe(200);
  expect(firstRun.profile.body.data.email).toBe(identity.email);
  expect(firstRun.profileUpdate.status).toBe(200);
  expect(firstRun.profileUpdate.body.data.phoneNumber).toBe('010-5555-7777');
  expect(firstRun.createCommunityPost.status).toBe(201);
  expect(firstRun.postId).toBeTruthy();
  expect(firstRun.myCommunityPosts.status).toBe(200);
  expect(firstRun.myCommunityPosts.body.data.some((entry) => entry.postId === firstRun.postId)).toBe(true);
  expect(firstRun.myPosts.status).toBe(200);
  expect(Array.isArray(firstRun.myPosts.body.data)).toBe(true);
  expect(firstRun.unlink.status).toBe(409);
  expect(firstRun.unlink.body.error.code).toBe('LAST_SOCIAL_ACCOUNT');
  expect(firstRun.logout.status).toBe(200);
  expect(firstRun.logout.body.data.revoked).toBe(true);
  expect(firstRun.afterLogoutProfile.status).toBe(401);
  expect(firstRun.afterLogoutProfile.body.error.code).toBe('INVALID_TOKEN');

  const secondSignIn = await emulatorSignIn(identity);
  expect(secondSignIn.status).toBe(200);
  const secondToken = secondSignIn.body.idToken;
  expect(secondToken).toBeTruthy();

  const secondRun = await page.evaluate(async ({ baseURL, secondToken, postId }) => {
    const readJson = async (response) => {
      const text = await response.text();
      try {
        return JSON.parse(text);
      } catch {
        return text;
      }
    };

    const api = async (pathname, token, init = {}) => {
      const response = await fetch(`${baseURL}${pathname}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          ...(init.headers || {})
        }
      });

      return {
        status: response.status,
        body: await readJson(response)
      };
    };

    const loginResponse = await fetch(`${baseURL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ firebaseIdToken: secondToken })
    });

    const login = {
      status: loginResponse.status,
      body: await readJson(loginResponse)
    };

    const profile = await api('/api/users/me', secondToken);
    const myCommunityPosts = await api('/api/users/me/community-posts', secondToken);
    const cleanupCommunityPost = await api(`/api/community/posts/${postId}`, secondToken, { method: 'DELETE' });
    const withdraw = await api('/api/auth/withdraw', secondToken, { method: 'DELETE' });
    const afterWithdrawProfile = await api('/api/users/me', secondToken);

    return {
      login,
      profile,
      myCommunityPosts,
      cleanupCommunityPost,
      withdraw,
      afterWithdrawProfile
    };
  }, { baseURL, secondToken, postId: firstRun.postId });

  expect(secondRun.login.status).toBe(200);
  expect(secondRun.login.body.data.email).toBe(identity.email);
  expect(secondRun.profile.status).toBe(200);
  expect(secondRun.myCommunityPosts.status).toBe(200);
  expect(secondRun.myCommunityPosts.body.data.some((entry) => entry.postId === firstRun.postId)).toBe(true);
  expect(secondRun.cleanupCommunityPost.status).toBe(200);
  expect(secondRun.withdraw.status).toBe(200);
  expect(secondRun.withdraw.body.data.status).toBe('WITHDRAWN');
  expect(secondRun.afterWithdrawProfile.status).toBe(401);
  expect(secondRun.afterWithdrawProfile.body.error.code).toBe('INVALID_TOKEN');

  const thirdSignIn = await emulatorSignIn(identity);
  expect(thirdSignIn.status).toBe(400);
  expect(thirdSignIn.body.error.message).toBe('EMAIL_NOT_FOUND');

  const serverErrors = apiResponses.filter((entry) => entry.status >= 500);
  expect(serverErrors, JSON.stringify(apiResponses, null, 2)).toEqual([]);
  const unexpectedConsoleErrors = consoleErrors.filter(
    (entry) => !/Failed to load resource: the server responded with a status of 40[019]/.test(entry)
  );
  expect(unexpectedConsoleErrors, unexpectedConsoleErrors.join('\n')).toEqual([]);
});

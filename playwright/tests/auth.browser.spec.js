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

function attachAuthObservers(page) {
  const consoleErrors = [];
  const apiResponses = [];

  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });

  page.on('response', async (response) => {
    const url = response.url();
    if (!url.includes('/api/auth') && !url.includes('/api/users/me')) {
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

test.describe('auth browser validation', () => {
  test('login and logout revoke the token, then a fresh login works again', async ({ page }) => {
    const identity = createTestIdentity('auth-login');
    const signUp = await emulatorSignUp(identity);
    expect(signUp.status).toBe(200);

    const firstToken = signUp.body.idToken;
    expect(firstToken).toBeTruthy();

    const { consoleErrors, apiResponses } = attachAuthObservers(page);
    await page.goto(`${baseURL}/swagger-ui/index.html#/Auth`, { waitUntil: 'domcontentloaded' });

    const result = await page.evaluate(async ({ baseURL, firstToken }) => {
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

      const login = await fetch(`${baseURL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ firebaseIdToken: firstToken })
      });

      const loginBody = await readJson(login);
      const profileBeforeLogout = await api('/api/users/me', firstToken);
      const logout = await api('/api/auth/logout', firstToken, { method: 'POST' });
      const profileAfterLogout = await api('/api/users/me', firstToken);

      return {
        login: { status: login.status, body: loginBody },
        profileBeforeLogout,
        logout,
        profileAfterLogout
      };
    }, { baseURL, firstToken });

    expect(result.login.status).toBe(200);
    expect(result.login.body.data.email).toBe(identity.email);
    expect(result.profileBeforeLogout.status).toBe(200);
    expect(result.logout.status).toBe(200);
    expect(result.logout.body.data.revoked).toBe(true);
    expect(result.profileAfterLogout.status).toBe(401);
    expect(result.profileAfterLogout.body.error.code).toBe('INVALID_TOKEN');

    const secondSignIn = await emulatorSignIn(identity);
    expect(secondSignIn.status).toBe(200);
    const secondToken = secondSignIn.body.idToken;
    expect(secondToken).toBeTruthy();

    const secondResult = await page.evaluate(async ({ baseURL, secondToken }) => {
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

      const login = await fetch(`${baseURL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ firebaseIdToken: secondToken })
      });

      const loginBody = await readJson(login);
      const profile = await api('/api/users/me', secondToken);
      const withdraw = await api('/api/auth/withdraw', secondToken, { method: 'DELETE' });
      const afterWithdraw = await api('/api/users/me', secondToken);

      return {
        login: { status: login.status, body: loginBody },
        profile,
        withdraw,
        afterWithdraw
      };
    }, { baseURL, secondToken });

    expect(secondResult.login.status).toBe(200);
    expect(secondResult.login.body.data.email).toBe(identity.email);
    expect(secondResult.profile.status).toBe(200);
    expect(secondResult.withdraw.status).toBe(200);
    expect(secondResult.withdraw.body.data.status).toBe('WITHDRAWN');
    expect(secondResult.afterWithdraw.status).toBe(401);
    expect(secondResult.afterWithdraw.body.error.code).toBe('INVALID_TOKEN');

    const thirdSignIn = await emulatorSignIn(identity);
    expect(thirdSignIn.status).toBe(400);
    expect(thirdSignIn.body.error.message).toBe('EMAIL_NOT_FOUND');

    const serverErrors = apiResponses.filter((entry) => entry.status >= 500);
    expect(serverErrors, JSON.stringify(apiResponses, null, 2)).toEqual([]);
    const unexpectedConsoleErrors = consoleErrors.filter(
      (entry) => !/Failed to load resource: the server responded with a status of 40[01]/.test(entry)
    );
    expect(unexpectedConsoleErrors, unexpectedConsoleErrors.join('\n')).toEqual([]);
  });

  test('social unlink blocks removing the last linked provider and withdraw removes the account', async ({ page }) => {
    const identity = createTestIdentity('auth-unlink');
    const signUp = await emulatorSignUp(identity);
    expect(signUp.status).toBe(200);

    const token = signUp.body.idToken;
    expect(token).toBeTruthy();

    const { consoleErrors, apiResponses } = attachAuthObservers(page);
    await page.goto(`${baseURL}/swagger-ui/index.html#/Auth`, { waitUntil: 'domcontentloaded' });

    const result = await page.evaluate(async ({ baseURL, token }) => {
      const readJson = async (response) => {
        const text = await response.text();
        try {
          return JSON.parse(text);
        } catch {
          return text;
        }
      };

      const api = async (pathname, tokenValue, init = {}) => {
        const response = await fetch(`${baseURL}${pathname}`, {
          ...init,
          headers: {
            Authorization: `Bearer ${tokenValue}`,
            'Content-Type': 'application/json',
            ...(init.headers || {})
          }
        });

        return {
          status: response.status,
          body: await readJson(response)
        };
      };

      const login = await fetch(`${baseURL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ firebaseIdToken: token })
      });

      const loginBody = await readJson(login);
      const profile = await api('/api/users/me', token);
      const unlink = await api('/api/auth/link/EMAIL', token, { method: 'DELETE' });
      const withdraw = await api('/api/auth/withdraw', token, { method: 'DELETE' });
      const afterWithdraw = await api('/api/users/me', token);

      return {
        login: { status: login.status, body: loginBody },
        profile,
        unlink,
        withdraw,
        afterWithdraw
      };
    }, { baseURL, token });

    expect(result.login.status).toBe(200);
    expect(result.login.body.data.email).toBe(identity.email);
    expect(result.profile.status).toBe(200);
    expect(result.profile.body.data.linkedProviders).toContain('EMAIL');
    expect(result.unlink.status).toBe(409);
    expect(result.unlink.body.error.code).toBe('LAST_SOCIAL_ACCOUNT');
    expect(result.withdraw.status).toBe(200);
    expect(result.withdraw.body.data.status).toBe('WITHDRAWN');
    expect(result.afterWithdraw.status).toBe(401);
    expect(result.afterWithdraw.body.error.code).toBe('INVALID_TOKEN');

    const serverErrors = apiResponses.filter((entry) => entry.status >= 500);
    expect(serverErrors, JSON.stringify(apiResponses, null, 2)).toEqual([]);
    const unexpectedConsoleErrors = consoleErrors.filter(
      (entry) => !/Failed to load resource: the server responded with a status of 40[019]/.test(entry)
    );
    expect(unexpectedConsoleErrors, unexpectedConsoleErrors.join('\n')).toEqual([]);
  });
});

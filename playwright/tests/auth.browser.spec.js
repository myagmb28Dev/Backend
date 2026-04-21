const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const emulatorBaseURL = 'http://127.0.0.1:9099';
const emulatorApiKey = 'fake-api-key';
const playwrightAuthIdentity = {
  email: 'playwright-user4@local.dev',
  password: 'Test1234!'
};

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function emulatorSignIn(identity = playwrightAuthIdentity) {
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
  test('fixed Playwright account logs in, rejects last-provider unlink, and recovers after logout', async ({ page }) => {
    const firstSignIn = await emulatorSignIn();
    expect(firstSignIn.status).toBe(200);

    const firstToken = firstSignIn.body.idToken;
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
      const unlink = await api('/api/auth/link/EMAIL', firstToken, { method: 'DELETE' });
      const logout = await api('/api/auth/logout', firstToken, { method: 'POST' });
      const profileAfterLogout = await api('/api/users/me', firstToken);

      return {
        login: { status: login.status, body: loginBody },
        profileBeforeLogout,
        unlink,
        logout,
        profileAfterLogout
      };
    }, { baseURL, firstToken });

    expect(result.login.status).toBe(200);
    expect(result.login.body.data.email).toBe(playwrightAuthIdentity.email);
    expect(result.profileBeforeLogout.status).toBe(200);
    expect(result.profileBeforeLogout.body.data.linkedProviders).toContain('EMAIL');
    expect(result.unlink.status).toBe(409);
    expect(result.unlink.body.error.code).toBe('LAST_SOCIAL_ACCOUNT');
    expect(result.logout.status).toBe(200);
    expect(result.logout.body.data.revoked).toBe(true);
    expect(result.profileAfterLogout.status).toBe(401);
    expect(result.profileAfterLogout.body.error.code).toBe('INVALID_TOKEN');

    const secondSignIn = await emulatorSignIn();
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

      const response = await fetch(`${baseURL}/api/users/me`, {
        headers: {
          Authorization: `Bearer ${secondToken}`,
          'Content-Type': 'application/json'
        }
      });

      return {
        status: response.status,
        body: await readJson(response)
      };
    }, { baseURL, secondToken });

    expect(secondResult.status).toBe(200);
    expect(secondResult.body.data.email).toBe(playwrightAuthIdentity.email);

    const serverErrors = apiResponses.filter((entry) => entry.status >= 500);
    expect(serverErrors, JSON.stringify(apiResponses, null, 2)).toEqual([]);
    const unexpectedConsoleErrors = consoleErrors.filter(
      (entry) => !/Failed to load resource: the server responded with a status of 40[019]/.test(entry)
    );
    expect(unexpectedConsoleErrors, unexpectedConsoleErrors.join('\n')).toEqual([]);
  });
});

const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const repoRoot = path.resolve(__dirname, '..', '..');

function loadToken() {
  return fs.readFileSync(
    path.join(repoRoot, '.local', 'emulator-firebase-id-token.txt'),
    'utf8'
  ).trim();
}

function attachUserObservers(page) {
  const consoleErrors = [];
  const apiResponses = [];

  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });

  page.on('response', async (response) => {
    const url = response.url();
    if (!url.includes('/api/auth/login') && !url.includes('/api/users/me')) {
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

test.describe('users browser validation', () => {
  test('users profile rejects malformed values with 400 and stays healthy', async ({ page }) => {
    const token = loadToken();
    const { consoleErrors, apiResponses } = attachUserObservers(page);

    await page.goto(`${baseURL}/swagger-ui/index.html#/Users`, { waitUntil: 'domcontentloaded' });

    const result = await page.evaluate(async ({ baseURL, token }) => {
      const readJson = async (response) => {
        const text = await response.text();
        try {
          return JSON.parse(text);
        } catch {
          return text;
        }
      };

      const api = async (pathname, init = {}) => {
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

      const rawApi = async (pathname, init = {}) => {
        const response = await fetch(`${baseURL}${pathname}`, {
          ...init,
          headers: {
            Authorization: `Bearer ${token}`,
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
        body: JSON.stringify({ firebaseIdToken: token })
      });

      const login = {
        status: loginResponse.status,
        body: await readJson(loginResponse)
      };

      const tooLongNickname = await api('/api/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ nickname: 'n'.repeat(51) })
      });

      const tooLongPhoneNumber = await api('/api/users/me', {
        method: 'PATCH',
        body: JSON.stringify({ phoneNumber: '1'.repeat(31) })
      });

      const tooLongProfileImageUrl = await api('/api/users/me', {
        method: 'PATCH',
        body: JSON.stringify({
          profileImageUrl: `https://cdn.example.com/${'p'.repeat(980)}`
        })
      });

      const invalidJson = await rawApi('/api/users/me', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: '{"nickname":'
      });

      const recoveryPatch = await api('/api/users/me', {
        method: 'PATCH',
        body: JSON.stringify({})
      });

      const recoveryGet = await api('/api/users/me');

      return {
        login,
        tooLongNickname,
        tooLongPhoneNumber,
        tooLongProfileImageUrl,
        invalidJson,
        recoveryPatch,
        recoveryGet
      };
    }, { baseURL, token });

    expect(result.login.status).toBe(200);

    expect(result.tooLongNickname.status).toBe(400);
    expect(result.tooLongNickname.body.error.code).toBe('VALIDATION_ERROR');
    expect(String(result.tooLongNickname.body.error.detail.nickname)).toContain('50자');

    expect(result.tooLongPhoneNumber.status).toBe(400);
    expect(result.tooLongPhoneNumber.body.error.code).toBe('VALIDATION_ERROR');
    expect(String(result.tooLongPhoneNumber.body.error.detail.phoneNumber)).toContain('30자');

    expect(result.tooLongProfileImageUrl.status).toBe(400);
    expect(result.tooLongProfileImageUrl.body.error.code).toBe('VALIDATION_ERROR');
    expect(String(result.tooLongProfileImageUrl.body.error.detail.profileImageUrl)).toContain('1000자');

    expect(result.invalidJson.status).toBe(400);
    expect(result.invalidJson.body.error.code).toBe('INVALID_JSON');

    expect(result.recoveryPatch.status).toBe(200);
    expect(result.recoveryGet.status).toBe(200);
    expect(result.recoveryGet.body.data.id).toBeTruthy();

    const serverErrors = apiResponses.filter((entry) => entry.status >= 500);
    expect(serverErrors, JSON.stringify(apiResponses, null, 2)).toEqual([]);
    const unexpectedConsoleErrors = consoleErrors.filter(
      (entry) => !/Failed to load resource: the server responded with a status of 400/.test(entry)
    );
    expect(unexpectedConsoleErrors, unexpectedConsoleErrors.join('\n')).toEqual([]);
  });

  test('users stress mix survives abnormal requests and returns to normal 200 responses', async ({ page }) => {
    test.slow();

    const token = loadToken();
    const { consoleErrors, apiResponses } = attachUserObservers(page);

    await page.goto(`${baseURL}/swagger-ui/index.html#/Users`, { waitUntil: 'domcontentloaded' });

    const result = await page.evaluate(async ({ baseURL, token }) => {
      const readJson = async (response) => {
        const text = await response.text();
        try {
          return JSON.parse(text);
        } catch {
          return text;
        }
      };

      const api = async (pathname, init = {}) => {
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

      const rawApi = async (pathname, init = {}) => {
        const response = await fetch(`${baseURL}${pathname}`, {
          ...init,
          headers: {
            Authorization: `Bearer ${token}`,
            ...(init.headers || {})
          }
        });

        return {
          status: response.status,
          body: await readJson(response)
        };
      };

      const summarizeStatuses = (entries) => entries.reduce((summary, entry) => {
        const key = String(entry.status);
        summary[key] = (summary[key] || 0) + 1;
        return summary;
      }, {});

      const loginResponse = await fetch(`${baseURL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ firebaseIdToken: token })
      });

      const login = {
        status: loginResponse.status,
        body: await readJson(loginResponse)
      };

      const startedAt = Date.now();

      const invalidLengthBurst = await Promise.all(
        Array.from({ length: 24 }, (_, index) => {
          const payload =
            index % 3 === 0
              ? { nickname: 'n'.repeat(51) }
              : index % 3 === 1
                ? { phoneNumber: '1'.repeat(31) }
                : { profileImageUrl: `https://cdn.example.com/${'p'.repeat(980)}` };

          return api('/api/users/me', {
            method: 'PATCH',
            body: JSON.stringify(payload)
          });
        })
      );

      const invalidJsonBurst = await Promise.all(
        Array.from({ length: 8 }, () => rawApi('/api/users/me', {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: '{"nickname":'
        }))
      );

      const validReadBurst = await Promise.all(
        Array.from({ length: 24 }, () => api('/api/users/me'))
      );

      const validNoOpPatchBurst = await Promise.all(
        Array.from({ length: 12 }, () => api('/api/users/me', {
          method: 'PATCH',
          body: JSON.stringify({})
        }))
      );

      const recoveryPatch = await api('/api/users/me', {
        method: 'PATCH',
        body: JSON.stringify({})
      });

      const recoveryGet = await api('/api/users/me');

      return {
        login,
        invalidLengthStatuses: summarizeStatuses(invalidLengthBurst),
        invalidJsonStatuses: summarizeStatuses(invalidJsonBurst),
        validReadStatuses: summarizeStatuses(validReadBurst),
        validNoOpPatchStatuses: summarizeStatuses(validNoOpPatchBurst),
        invalidValidationCodes: Array.from(new Set(invalidLengthBurst.map((entry) => entry.body.error.code))),
        invalidJsonCodes: Array.from(new Set(invalidJsonBurst.map((entry) => entry.body.error.code))),
        recoveryPatch,
        recoveryGet,
        elapsedMs: Date.now() - startedAt,
        totalRequests:
          invalidLengthBurst.length +
          invalidJsonBurst.length +
          validReadBurst.length +
          validNoOpPatchBurst.length +
          2
      };
    }, { baseURL, token });

    expect(result.login.status).toBe(200);
    expect(result.invalidLengthStatuses).toEqual({ '400': 24 });
    expect(result.invalidJsonStatuses).toEqual({ '400': 8 });
    expect(result.validReadStatuses).toEqual({ '200': 24 });
    expect(result.validNoOpPatchStatuses).toEqual({ '200': 12 });
    expect(result.invalidValidationCodes).toEqual(['VALIDATION_ERROR']);
    expect(result.invalidJsonCodes).toEqual(['INVALID_JSON']);
    expect(result.recoveryPatch.status).toBe(200);
    expect(result.recoveryGet.status).toBe(200);
    expect(result.recoveryGet.body.data.id).toBeTruthy();
    expect(result.totalRequests).toBe(70);
    expect(result.elapsedMs).toBeGreaterThan(0);

    const serverErrors = apiResponses.filter((entry) => entry.status >= 500);
    expect(serverErrors, JSON.stringify(apiResponses, null, 2)).toEqual([]);
    const unexpectedConsoleErrors = consoleErrors.filter(
      (entry) => !/Failed to load resource: the server responded with a status of 400/.test(entry)
    );
    expect(unexpectedConsoleErrors, unexpectedConsoleErrors.join('\n')).toEqual([]);
  });
});

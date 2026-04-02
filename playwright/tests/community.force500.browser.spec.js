const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

test('community browser flow can intentionally force 5xx responses in local test mode', async ({ page }) => {
  const baseURL = 'http://localhost:8080';
  const token = fs.readFileSync(path.join(process.cwd(), '.local', 'emulator-firebase-id-token.txt'), 'utf8').trim();

  const consoleErrors = [];
  const apiResponses = [];

  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });

  page.on('response', async (response) => {
    const url = response.url();
    if (!url.includes('/api/auth/login') && !url.includes('/api/community/posts')) {
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

  await page.goto(`${baseURL}/swagger-ui/index.html#/Community`, { waitUntil: 'domcontentloaded' });

  const result = await page.evaluate(async ({ baseURL, token }) => {
    const forceHeader = { 'X-Community-Test-Force-5xx': 'true' };

    const readJson = async (response) => {
      const text = await response.text();
      try {
        return JSON.parse(text);
      } catch {
        return text;
      }
    };

    const api = async (pathname, init = {}, force5xx = false) => {
      const response = await fetch(`${baseURL}${pathname}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          ...(force5xx ? forceHeader : {}),
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

    const createValidPost = await api('/api/community/posts', {
      method: 'POST',
      body: JSON.stringify({
        title: 'Force 5xx baseline',
        content: 'Valid setup post for forced 5xx checks.',
        category: 'FREE',
        pollQuestion: 'pick one',
        pollOptions: ['one', 'two'],
        tags: ['force', '5xx']
      })
    });

    const postId = createValidPost.body && createValidPost.body.data ? createValidPost.body.data.id : null;
    const createParentComment = await api(`/api/community/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content: 'parent comment for forced 5xx' })
    });
    const parentCommentId = createParentComment.body && createParentComment.body.data ? createParentComment.body.data.id : null;

    const forcedList = await api('/api/community/posts', {}, true);
    const forcedCreate = await api('/api/community/posts', {
      method: 'POST',
      body: JSON.stringify({ title: 'should fail', content: 'should fail', category: 'FREE' })
    }, true);
    const forcedDetail = await api(`/api/community/posts/${postId}`, {}, true);
    const forcedUpdate = await api(`/api/community/posts/${postId}`, {
      method: 'PATCH',
      body: JSON.stringify({ title: 'should fail update' })
    }, true);
    const forcedComment = await api(`/api/community/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content: 'should fail comment' })
    }, true);
    const forcedReply = await api(`/api/community/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content: 'should fail reply', parentCommentId })
    }, true);
    const forcedVote = await api(`/api/community/posts/${postId}/votes`, {
      method: 'POST',
      body: JSON.stringify({ option: 'one' })
    }, true);
    const forcedReaction = await api(`/api/community/posts/${postId}/reactions`, {
      method: 'POST',
      body: JSON.stringify({ reaction: 'LIKE' })
    }, true);
    const forcedDelete = await api(`/api/community/posts/${postId}`, {
      method: 'DELETE'
    }, true);

    const cleanupDelete = await api(`/api/community/posts/${postId}`, {
      method: 'DELETE'
    });

    return {
      login,
      createValidPost,
      createParentComment,
      forcedList,
      forcedCreate,
      forcedDetail,
      forcedUpdate,
      forcedComment,
      forcedReply,
      forcedVote,
      forcedReaction,
      forcedDelete,
      cleanupDelete,
      postId,
      parentCommentId
    };
  }, { baseURL, token });

  expect(result.login.status).toBe(200);
  expect(result.createValidPost.status).toBe(201);
  expect(result.postId).toBeTruthy();
  expect(result.createParentComment.status).toBe(201);
  expect(result.parentCommentId).toBeTruthy();

  for (const key of [
    'forcedList',
    'forcedCreate',
    'forcedDetail',
    'forcedUpdate',
    'forcedComment',
    'forcedReply',
    'forcedVote',
    'forcedReaction',
    'forcedDelete'
  ]) {
    expect(result[key].status).toBe(500);
    expect(result[key].body.error.code).toBe('INTERNAL_SERVER_ERROR');
  }

  expect(result.cleanupDelete.status).toBe(200);

  const unexpectedResponses = apiResponses.filter((entry) => entry.status >= 500 && !entry.bodyPreview.includes('INTERNAL_SERVER_ERROR'));
  expect(unexpectedResponses, JSON.stringify(apiResponses, null, 2)).toEqual([]);

  const unexpectedConsoleErrors = consoleErrors.filter((entry) => !/Failed to load resource: the server responded with a status of 500/.test(entry));
  expect(unexpectedConsoleErrors, unexpectedConsoleErrors.join('\n')).toEqual([]);
});
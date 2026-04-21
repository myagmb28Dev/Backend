const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { test, expect } = require('@playwright/test');

const repoRoot = path.resolve(__dirname, '..', '..');

test('community browser flow rejects invalid inputs without server errors', async ({ page }) => {
  const baseURL = process.env.BASE_URL || 'http://localhost:8081';
  const token = fs.readFileSync(path.join(repoRoot, '.local', 'emulator-firebase-id-token.txt'), 'utf8').trim();

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
    const randomUuid = () => {
      if (globalThis.crypto && typeof globalThis.crypto.randomUUID === 'function') {
        return globalThis.crypto.randomUUID();
      }
      return '00000000-0000-4000-8000-000000000001';
    };

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

    const loginResponse = await fetch(`${baseURL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ firebaseIdToken: token })
    });

    const login = {
      status: loginResponse.status,
      body: await readJson(loginResponse)
    };

    const invalidList = await api('/api/community/posts?page=oops');
    const invalidCreate = await api('/api/community/posts', {
      method: 'POST',
      body: JSON.stringify({
        title: '',
        content: '',
        category: 'FREE'
      })
    });
    const invalidPollCreate = await api('/api/community/posts', {
      method: 'POST',
      body: JSON.stringify({
        title: 'Bad poll',
        content: 'Bad poll body',
        pollQuestion: 'Only one option?',
        pollOptions: ['just-one']
      })
    });
    const invalidDetail = await api('/api/community/posts/not-a-uuid');
    const invalidUpdate = await api('/api/community/posts/not-a-uuid', {
      method: 'PATCH',
      body: JSON.stringify({ title: 'broken' })
    });

    const createValidPost = await api('/api/community/posts', {
      method: 'POST',
      body: JSON.stringify({
        title: 'Negative browser validation',
        content: 'Valid post used for invalid follow-up checks.',
        category: 'FREE',
        pollQuestion: 'Choose a valid option',
        pollOptions: ['alpha', 'beta'],
        tags: ['negative', 'playwright']
      })
    });

    const postId = createValidPost.body && createValidPost.body.data ? createValidPost.body.data.id : null;

    const invalidVote = await api(`/api/community/posts/${postId}/votes`, {
      method: 'POST',
      body: JSON.stringify({ option: 'gamma' })
    });
    const invalidReaction = await api(`/api/community/posts/${postId}/reactions`, {
      method: 'POST',
      body: JSON.stringify({ reaction: '' })
    });
    const invalidComment = await api(`/api/community/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content: '' })
    });
    const createParentComment = await api(`/api/community/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content: 'valid parent comment' })
    });
    const invalidReply = await api(`/api/community/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({
        content: 'reply with missing parent',
        parentCommentId: randomUuid()
      })
    });
    const invalidDelete = await api('/api/community/posts/not-a-uuid', {
      method: 'DELETE'
    });
    const cleanupDelete = await api(`/api/community/posts/${postId}`, {
      method: 'DELETE'
    });

    return {
      login,
      invalidList,
      invalidCreate,
      invalidPollCreate,
      invalidDetail,
      invalidUpdate,
      createValidPost,
      invalidVote,
      invalidReaction,
      invalidComment,
      createParentComment,
      invalidReply,
      invalidDelete,
      cleanupDelete,
      postId
    };
  }, { baseURL, token, seed: crypto.randomUUID() });

  expect(result.login.status).toBe(200);

  expect(result.invalidList.status).toBe(400);
  expect(result.invalidList.body.error.code).toBe('INVALID_REQUEST');

  expect(result.invalidCreate.status).toBe(400);
  expect(result.invalidCreate.body.error.code).toBe('VALIDATION_ERROR');

  expect(result.invalidPollCreate.status).toBe(400);
  expect(result.invalidPollCreate.body.error.code).toBe('INVALID_POLL_OPTIONS');

  expect(result.invalidDetail.status).toBe(400);
  expect(result.invalidDetail.body.error.code).toBe('INVALID_COMMUNITY_POST_ID');

  expect(result.invalidUpdate.status).toBe(400);
  expect(result.invalidUpdate.body.error.code).toBe('INVALID_COMMUNITY_POST_ID');

  expect(result.createValidPost.status).toBe(201);
  expect(result.postId).toBeTruthy();

  expect(result.invalidVote.status).toBe(400);
  expect(result.invalidVote.body.error.code).toBe('INVALID_POLL_OPTION');

  expect(result.invalidReaction.status).toBe(400);
  expect(result.invalidReaction.body.error.code).toBe('VALIDATION_ERROR');

  expect(result.invalidComment.status).toBe(400);
  expect(result.invalidComment.body.error.code).toBe('VALIDATION_ERROR');

  expect(result.createParentComment.status).toBe(201);

  expect(result.invalidReply.status).toBe(404);
  expect(result.invalidReply.body.error.code).toBe('COMMUNITY_COMMENT_NOT_FOUND');

  expect(result.invalidDelete.status).toBe(400);
  expect(result.invalidDelete.body.error.code).toBe('INVALID_COMMUNITY_POST_ID');

  expect(result.cleanupDelete.status).toBe(200);

  const serverErrors = apiResponses.filter((entry) => entry.status >= 500);
  expect(serverErrors, JSON.stringify(apiResponses, null, 2)).toEqual([]);
  const unexpectedConsoleErrors = consoleErrors.filter((entry) => !/Failed to load resource: the server responded with a status of 40[04]/.test(entry));
  expect(unexpectedConsoleErrors, unexpectedConsoleErrors.join('\\n')).toEqual([]);
});

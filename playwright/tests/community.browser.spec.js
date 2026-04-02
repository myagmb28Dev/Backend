const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

test('community browser flow covers create update vote reaction reply delete', async ({ page }) => {
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

    const create = await api('/api/community/posts', {
      method: 'POST',
      body: JSON.stringify({
        title: 'Playwright browser create',
        content: 'Created from a real Playwright browser session.',
        category: 'FREE',
        pollQuestion: 'Which browser flow should win?',
        pollOptions: ['chromium', 'webkit'],
        tags: ['playwright', 'browser']
      })
    });

    const postId = create.body && create.body.data ? create.body.data.id : null;

    const list = await api('/api/community/posts');
    const detailBeforeUpdate = await api(`/api/community/posts/${postId}`);
    const update = await api(`/api/community/posts/${postId}`, {
      method: 'PATCH',
      body: JSON.stringify({
        title: 'Playwright browser updated',
        content: 'Updated from the browser flow.',
        pollQuestion: 'Which browser flow should win?',
        pollOptions: ['chromium', 'webkit'],
        tags: ['playwright', 'updated']
      })
    });
    const vote = await api(`/api/community/posts/${postId}/votes`, {
      method: 'POST',
      body: JSON.stringify({
        option: 'chromium'
      })
    });
    const react = await api(`/api/community/posts/${postId}/reactions`, {
      method: 'POST',
      body: JSON.stringify({
        reaction: 'LIKE'
      })
    });
    const detailAfterReaction = await api(`/api/community/posts/${postId}`);
    const comment = await api(`/api/community/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({
        content: 'Playwright browser comment'
      })
    });
    const commentId = comment.body && comment.body.data ? comment.body.data.id : null;
    const reply = await api(`/api/community/posts/${postId}/comments`, {
      method: 'POST',
      body: JSON.stringify({
        content: 'Playwright browser reply',
        parentCommentId: commentId
      })
    });
    const comments = await api(`/api/community/posts/${postId}/comments`);
    const remove = await api(`/api/community/posts/${postId}`, {
      method: 'DELETE'
    });

    return {
      login,
      create,
      list,
      detailBeforeUpdate,
      update,
      vote,
      react,
      detailAfterReaction,
      comment,
      reply,
      comments,
      remove,
      postId,
      commentId,
      replyId: reply.body && reply.body.data ? reply.body.data.id : null
    };
  }, { baseURL, token });

  expect(result.login.status).toBe(200);
  expect(result.create.status).toBe(201);
  expect(result.postId).toBeTruthy();
  expect(result.list.status).toBe(200);
  expect(result.detailBeforeUpdate.status).toBe(200);
  expect(result.detailBeforeUpdate.body.data.id).toBe(result.postId);
  expect(result.detailBeforeUpdate.body.data.poll.question).toBe('Which browser flow should win?');
  expect(result.update.status).toBe(200);
  expect(result.update.body.data.updated).toBe(true);
  expect(result.vote.status).toBe(200);
  expect(result.vote.body.data.selection).toBe('chromium');
  expect(result.react.status).toBe(200);
  expect(result.react.body.data.reaction).toBe('LIKE');
  expect(result.detailAfterReaction.status).toBe(200);
  expect(result.detailAfterReaction.body.data.likeCount).toBeGreaterThanOrEqual(1);
  expect(result.comment.status).toBe(201);
  expect(result.commentId).toBeTruthy();
  expect(result.reply.status).toBe(201);
  expect(result.replyId).toBeTruthy();
  expect(result.comments.status).toBe(200);
  expect(Array.isArray(result.comments.body.data)).toBe(true);
  const parentComment = result.comments.body.data.find((entry) => entry.id === result.commentId);
  expect(parentComment).toBeTruthy();
  expect(Array.isArray(parentComment.replies)).toBe(true);
  expect(parentComment.replies.some((entry) => entry.id === result.replyId)).toBe(true);
  expect(result.remove.status).toBe(200);

  const failingResponses = apiResponses.filter((entry) => entry.status >= 400);
  expect(failingResponses, JSON.stringify(apiResponses, null, 2)).toEqual([]);
  expect(consoleErrors, consoleErrors.join('\n')).toEqual([]);
});
const { test, expect } = require("@playwright/test");

const BASE_URL = process.env.BASE_URL || "http://127.0.0.1:8081";
const AUTH_EMULATOR_URL = process.env.AUTH_EMULATOR_URL || "http://127.0.0.1:9099";
const TEST_EMAIL = "playwright-user2@local.dev";
const TEST_PASSWORD = "Test1234!";

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function api(request, path, { method = "GET", token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await request.fetch(`${BASE_URL}${path}`, {
    method,
    headers,
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

async function ensureReadySession(request, firebaseIdToken) {
  let login = null;
  for (let i = 0; i < 3; i += 1) {
    login = await api(request, "/api/auth/login", {
      method: "POST",
      body: { firebaseIdToken },
      token: firebaseIdToken
    });
    if (login.status === 200) break;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  if (login.status !== 200) {
    throw new Error(`login failed: ${JSON.stringify(login.body)}`);
  }
  if (login.body?.data?.id) return;
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
}

test("community comment and reply delete flow", async ({ request }) => {
  const token = await ensureEmulatorUserToken(TEST_EMAIL, TEST_PASSWORD);
  await ensureReadySession(request, token);

  let postId = null;
  try {
    const createPost = await api(request, "/api/community/posts", {
      method: "POST",
      token,
      body: {
        title: `playwright-comment-delete-${Date.now()}`,
        content: "comment delete regression",
        category: "FREE",
        tags: ["playwright", "delete"]
      }
    });
    expect(createPost.status).toBe(201);
    postId = createPost.body?.data?.id;
    expect(Boolean(postId)).toBeTruthy();

    const parent = await api(request, `/api/community/posts/${postId}/comments`, {
      method: "POST",
      token,
      body: { content: "parent-comment" }
    });
    expect(parent.status).toBe(201);
    const parentId = parent.body?.data?.id;
    expect(Boolean(parentId)).toBeTruthy();

    const reply = await api(request, `/api/community/posts/${postId}/comments`, {
      method: "POST",
      token,
      body: { content: "reply-comment", parentCommentId: parentId }
    });
    expect(reply.status).toBe(201);
    const replyId = reply.body?.data?.id;
    expect(Boolean(replyId)).toBeTruthy();

    const deleteReply = await api(request, `/api/community/posts/${postId}/comments/${replyId}`, {
      method: "DELETE",
      token
    });
    expect(deleteReply.status).toBe(200);

    const commentsAfterReplyDelete = await api(request, `/api/community/posts/${postId}/comments`, { token });
    expect(commentsAfterReplyDelete.status).toBe(200);
    const parentNode1 = commentsAfterReplyDelete.body?.data?.find((entry) => entry.id === parentId);
    expect(Boolean(parentNode1)).toBeTruthy();
    expect(Array.isArray(parentNode1.replies)).toBe(true);
    expect(parentNode1.replies.length).toBe(0);

    const deleteParent = await api(request, `/api/community/posts/${postId}/comments/${parentId}`, {
      method: "DELETE",
      token
    });
    expect(deleteParent.status).toBe(200);

    const commentsAfterParentDelete = await api(request, `/api/community/posts/${postId}/comments`, { token });
    expect(commentsAfterParentDelete.status).toBe(200);
    expect(Array.isArray(commentsAfterParentDelete.body?.data)).toBe(true);
    expect(commentsAfterParentDelete.body.data.length).toBe(0);
  } finally {
    if (postId) {
      await api(request, `/api/community/posts/${postId}`, {
        method: "DELETE",
        token
      });
    }
  }
});

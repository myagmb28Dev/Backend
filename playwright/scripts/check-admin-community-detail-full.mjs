const baseUrl = process.env.BASE_URL || "http://localhost:8081";
const adminLocalEmail = process.env.ADMIN_LOCAL_EMAIL || "myagmb28s@gmail.com";

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function api(path, { method = "GET", token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  return { status: response.status, body: await readJsonSafe(response) };
}

async function main() {
  const login = await api("/api/admin/auth/local/login", {
    method: "POST",
    body: { localTestEmail: adminLocalEmail, forcePasskeyEnroll: false }
  });
  if (login.status !== 200) {
    console.error("[admin-local-login-fail]", JSON.stringify(login, null, 2));
    process.exit(1);
  }

  const adminToken = login.body?.data?.session?.accessToken;
  if (!adminToken) {
    console.error("[admin-token-missing]", JSON.stringify(login, null, 2));
    process.exit(1);
  }

  const list = await api("/api/admin/community/posts?page=1&pageSize=1", { token: adminToken });
  if (list.status !== 200) {
    console.error("[admin-list-fail]", JSON.stringify(list, null, 2));
    process.exit(1);
  }

  const first = list.body?.data?.items?.[0];
  if (!first?.id) {
    console.error("[no-community-post-for-check]");
    process.exit(1);
  }

  const detail = await api(`/api/admin/community/posts/${first.id}`, { token: adminToken });
  const data = detail.body?.data ?? {};
  const comments = Array.isArray(data.comments) ? data.comments : [];
  const hasReply = comments.some((c) => Array.isArray(c.children) && c.children.length > 0);
  const votes = data.votes;
  const reactions = data.reactions;

  console.log(JSON.stringify({
    postId: first.id,
    detailStatus: detail.status,
    votesFieldPresent: votes !== undefined,
    reactionsFieldPresent: reactions !== undefined,
    commentsCount: comments.length,
    hasNestedReply: hasReply
  }, null, 2));

  if (detail.status !== 200) process.exit(1);
}

main().catch((e) => {
  console.error("[script-error]", e?.stack || String(e));
  process.exit(1);
});

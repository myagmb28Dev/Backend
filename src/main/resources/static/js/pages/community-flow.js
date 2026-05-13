import { request } from "/js/api-client.js";
import { loadSession, removeSession, saveSession } from "/js/session-store.js";
import { refreshFirebaseSession } from "/js/auth-refresh.js";
import { mountNotificationBell } from "/js/notification-web.js";

const REAL_SESSION_KEY = "pogun-real-firebase-session-v1";
const ACTIVE_ROLE_KEY = "dm-test-active-role-v1";
const BACKEND_BASE = window.location.origin;
const el = (id) => document.getElementById(id);
let currentPostId = null;
let currentPage = 0;
let currentTotalPages = 0;
let currentTotalElements = 0;
let activeRole = null;
let currentPost = null;

const users = { google: { label: "Google 사용자", email: "", token: null, userId: null } };

function setStatus(msg, tone = "") {
  const node = el("status");
  node.textContent = msg;
  node.className = `status${tone ? ` ${tone}` : ""}`;
}
function esc(v) { return String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;"); }

function loadRealSession() {
  return loadSession(REAL_SESSION_KEY);
}

function renderSessionInfo() {
  const user = activeRole ? users[activeRole] : null;
  const hint = document.createElement("div");
  hint.className = "muted";
  hint.style.marginTop = "8px";
  hint.textContent = user ? `${user.label} 님 환영합니다` : "로그인 세션이 없습니다. 로그인 탭에서 인증하세요.";
  const existing = document.querySelector(".session-hint");
  if (existing) existing.remove();
  hint.className = "session-hint muted";
  hint.style.marginTop = "8px";
  el("status").after(hint);
}

function requestLoginRecovery() {
  activeRole = null;
  renderSessionInfo();
  setStatus("세션이 없습니다. 로그인 탭에서 다시 인증하세요.", "bad");
  if (window.parent && window.parent !== window) {
    window.parent.postMessage({ type: "pogun-auth-required", view: "community" }, window.location.origin);
  }
}

async function authApi(path, init = {}) {
  if (!activeRole || !users[activeRole].token) throw new Error("먼저 로그인하세요.");
  const headers = { Authorization: `Bearer ${users[activeRole].token}`, ...(init.headers || {}) };
  if (!(init.body instanceof FormData) && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  let response = await request(`${BACKEND_BASE}${path}`, { ...init, headers });
  if (response.status !== 401) return response;
  const refreshed = await refreshFirebaseSession(REAL_SESSION_KEY, BACKEND_BASE);
  if (!refreshed?.firebaseIdToken) return response;
  users[activeRole].token = refreshed.firebaseIdToken;
  headers.Authorization = `Bearer ${users[activeRole].token}`;
  return request(`${BACKEND_BASE}${path}`, { ...init, headers });
}

async function login() {
  const role = "google";
  const session = loadRealSession();
  if (!session) { requestLoginRecovery(); return; }
  users[role].token = session.firebaseIdToken;
  users[role].email = session.email || "";
  users[role].label = session.nickname || session.email || "Google 사용자";
  setStatus(`${users[role].label} 로그인 중...`);
  let response = await request(`${BACKEND_BASE}/api/auth/login`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ firebaseIdToken: users[role].token })
  });
  if (response.status === 401) {
    const refreshed = await refreshFirebaseSession(REAL_SESSION_KEY, BACKEND_BASE);
    if (refreshed?.firebaseIdToken) {
      users[role].token = refreshed.firebaseIdToken;
      response = await request(`${BACKEND_BASE}/api/auth/login`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ firebaseIdToken: users[role].token })
      });
    }
  }
  if (response.status !== 200) {
    removeSession(REAL_SESSION_KEY); removeSession(ACTIVE_ROLE_KEY);
    requestLoginRecovery();
    return;
  }
  const data = response.body?.data || {};
  const latestSession = loadRealSession() || session;
  users[role].userId = data.id || session.userId || null;
  users[role].email = data.email || users[role].email;
  users[role].label = data.nickname || users[role].label;
  saveSession(REAL_SESSION_KEY, {
    ...latestSession,
    firebaseIdToken: users[role].token,
    firebaseUid: data.firebaseUid || latestSession.firebaseUid || "",
    email: data.email || latestSession.email || "",
    nickname: data.nickname || latestSession.nickname || "",
    profileImageUrl: data.profileImageUrl || latestSession.profileImageUrl || "",
    userId: users[role].userId,
    registrationStatus: data.registrationStatus
  });
  activeRole = role;
  try { window.localStorage.setItem(ACTIVE_ROLE_KEY, role); } catch {}
  renderSessionInfo();
  setStatus(`${users[role].label} 로그인 완료`, "good");
  await loadList();
}

function renderPagination() {
  const hasPages = currentTotalPages > 0;
  el("prevPageButton").disabled = !activeRole || !hasPages || currentPage <= 0;
  el("nextPageButton").disabled = !activeRole || !hasPages || currentPage >= currentTotalPages - 1;
  el("pageInfo").textContent = hasPages
    ? `${currentPage + 1} / ${currentTotalPages} 페이지 · 전체 ${currentTotalElements}건`
    : "페이지 정보 없음";
}

function isOwnerPost(post = currentPost) {
  return Boolean(activeRole && post?.authorId && users[activeRole]?.userId && post.authorId === users[activeRole].userId);
}

function renderPostList(items) {
  const list = el("postList");
  if (!items.length) {
    list.className = "post-list muted";
    list.textContent = "게시글이 없습니다.";
    renderPagination();
    return;
  }
  list.className = "post-list";
  list.innerHTML = items.map((post) => {
    const mine = isOwnerPost(post);
    return `
      <div class="post-item ${currentPostId === post.id ? "active" : ""}" data-post-id="${esc(post.id)}">
        <div class="top">
          <strong>${esc(post.title)}</strong>
          <span class="muted">[${esc(post.category || "FREE")}]</span>
        </div>
        <div class="post-meta">${esc(post.authorName || "-")} · 좋아요 ${post.likeCount ?? 0} · 조회 ${post.viewCount ?? 0} · 댓글 ${post.commentCount ?? 0}${mine ? " (내 글)" : ""}</div>
      </div>
    `;
  }).join("");
  list.querySelectorAll(".post-item").forEach((node) => {
    node.addEventListener("click", async () => {
      const id = node.getAttribute("data-post-id");
      if (!id) return;
      currentPostId = id;
      list.querySelectorAll(".post-item").forEach((n) => n.classList.toggle("active", n === node));
      await loadDetail(id);
    });
  });
  renderPagination();
}

async function loadList(page) {
  if (!activeRole) return;
  const q = encodeURIComponent(el("qInput").value.trim());
  const type = encodeURIComponent(el("typeInput").value);
  const category = encodeURIComponent(el("categoryInput").value);
  const size = 20;
  currentPage = page != null ? Math.max(Number(page) || 0, 0) : currentPage;
  const path = `/api/community/posts?type=${type}&category=${category}&q=${q}&page=${currentPage}&size=${size}`;
  setStatus("게시글 목록 조회 중...");
  const res = await authApi(path);
  if (res.status !== 200) return setStatus(`목록 조회 실패 (${res.status})`, "bad");
  const data = res.body?.data || {};
  currentPage = Number(data.filters?.page ?? currentPage) || 0;
  currentTotalPages = Number(data.totalPages || 0);
  currentTotalElements = Number(data.totalElements || 0);
  const items = Array.isArray(data.items) ? data.items : (Array.isArray(data.content) ? data.content : []);
  renderPostList(items);
  setStatus(`게시글 ${items.length}건 조회 완료`, "good");
}

async function loadListFirstPage() {
  currentPage = 0;
  await loadList(0);
}

function renderReactions(d) {
  const myReaction = d.myReaction || null;
  const reactions = d.reactions || {};
  const types = ["LIKE", "LOVE", "HAHA", "WOW", "SAD", "ANGRY"];
  const total = Object.values(reactions).reduce((a, b) => a + (b || 0), 0);
  return `
    <div class="reaction-buttons">
      ${types.map((t) => {
        const count = reactions[t] || 0;
        const active = myReaction === t;
        return `<button class="reaction-btn${active ? " active" : ""}" data-reaction="${t}">${t} ${count || ""}</button>`;
      }).join("")}
      <span class="muted" style="line-height:32px">좋아요 ${total}개 · 조회 ${d.viewCount ?? 0}</span>
    </div>
  `;
}

function renderPoll(d) {
  if (!d.poll?.question) return "";
  const options = Array.isArray(d.poll.options) ? d.poll.options : [];
  const votes = Array.isArray(d.poll.votes) ? d.poll.votes : [];
  const totalVotes = votes.reduce((a, b) => a + (b || 0), 0);
  const myVote = d.myVote || null;
  return `
    <div class="poll-section">
      <strong>📊 ${esc(d.poll.question)}</strong>
      ${options.map((opt, i) => {
        const count = votes[i] || 0;
        const pct = totalVotes > 0 ? Math.round(count / totalVotes * 100) : 0;
        const active = myVote === opt;
        return `
          <div class="poll-option">
            <button class="secondary" data-vote="${esc(opt)}" ${myVote ? "disabled" : ""} style="min-width:60px">${active ? "✓" : "투표"}</button>
            <span>${esc(opt)}</span>
            <div class="poll-bar"><div class="poll-bar-fill" style="width:${pct}%"></div></div>
            <span class="muted">${count}표 (${pct}%)</span>
          </div>
        `;
      }).join("")}
      <div class="muted" style="margin-top:4px">총 ${totalVotes}표</div>
    </div>
  `;
}

function renderComments(comments) {
  const list = Array.isArray(comments) ? comments : [];
  if (!list.length) return `<div class="muted" style="margin-top:8px">댓글이 없습니다. 첫 댓글을 남겨보세요!</div>`;
  const flatten = [];
  list.forEach((c) => {
    flatten.push({ ...c, depth: 0 });
    (c.replies || []).forEach((r) => flatten.push({ ...r, depth: 1 }));
  });
  return `
    <div class="comment-list">
      ${flatten.map((c) => `
        <div class="comment-item">
          <div class="meta">${"&nbsp;".repeat(c.depth * 2)}${esc(c.authorNickname || "-")} · ${esc(c.createdAt || "")}</div>
          <div>${esc(c.content || "")}</div>
        </div>
      `).join("")}
    </div>
  `;
}

async function loadDetail(postId) {
  if (!activeRole) return;
  const id = postId || currentPostId;
  if (!id) return setStatus("게시글을 선택하세요.", "bad");
  currentPostId = id;
  setStatus("게시글 상세 조회 중...");
  const res = await authApi(`/api/community/posts/${id}`);
  if (res.status !== 200) return setStatus(`상세 조회 실패 (${res.status})`, "bad");
  const d = res.body?.data || {};
  currentPost = d;
  const detail = el("detail");
  detail.className = "detail";
  detail.innerHTML = `
    <div><strong>${esc(d.title)}</strong> <span class="muted">[${esc(d.category || "FREE")}]</span></div>
    <div class="muted">작성자: ${esc(d.authorName || "-")} · ${esc(d.createdAt || "")}</div>
    ${d.tags?.length ? `<div class="muted">태그: ${esc(d.tags.join(", "))}</div>` : ""}
    <div style="margin-top:8px;white-space:pre-wrap">${esc(d.content || "")}</div>
    ${(d.imageUrls || []).length ? `<div class="detail-images">${d.imageUrls.map((u) => `<img src="${esc(u)}" alt="post image">`).join("")}</div>` : ""}
    ${renderPoll(d)}
    ${renderReactions(d)}
    <div style="margin-top:8px">
      <div class="actions">
        <input id="commentInput" placeholder="댓글을 입력하세요" style="flex:1">
        <button id="commentSubmit" class="secondary">댓글 등록</button>
        <button id="loadCommentsButton" class="secondary">댓글 새로고침</button>
        <button id="deletePostButton" class="danger" ${isOwnerPost(d) ? "" : "disabled"}>삭제</button>
      </div>
    </div>
    <div id="commentsArea">${renderComments(d.comments)}</div>
  `;

  detail.querySelector("#commentSubmit")?.addEventListener("click", () => createComment());
  detail.querySelector("#loadCommentsButton")?.addEventListener("click", () => loadComments());
  detail.querySelector("#deletePostButton")?.addEventListener("click", () => deletePost());

  detail.querySelectorAll("[data-reaction]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const reaction = btn.getAttribute("data-reaction");
      await react(reaction);
    });
  });
  detail.querySelectorAll("[data-vote]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const option = btn.getAttribute("data-vote");
      await vote(option);
    });
  });

  setStatus("게시글 상세 조회 성공", "good");
  await loadComments();
}

async function loadComments() {
  if (!currentPostId) return;
  const res = await authApi(`/api/community/posts/${currentPostId}/comments`);
  if (res.status !== 200) return;
  const comments = Array.isArray(res.body?.data) ? res.body.data : [];
  const area = document.querySelector("#commentsArea");
  if (area) area.innerHTML = renderComments(comments);
}

async function createComment() {
  const input = el("commentInput");
  const content = input?.value.trim();
  if (!currentPostId || !content) return setStatus("댓글 내용을 입력하세요.", "bad");
  const res = await authApi(`/api/community/posts/${currentPostId}/comments`, {
    method: "POST", body: JSON.stringify({ content })
  });
  if (res.status !== 201) return setStatus(`댓글 작성 실패 (${res.status})`, "bad");
  input.value = "";
  setStatus("댓글 작성 완료", "good");
  await loadDetail(currentPostId);
}

async function deletePost() {
  if (!currentPostId) return;
  if (!window.confirm("정말로 이 게시글을 삭제하시겠습니까?")) return;
  const res = await authApi(`/api/community/posts/${currentPostId}`, { method: "DELETE" });
  if (res.status !== 200) return setStatus(`삭제 실패 (${res.status})`, "bad");
  currentPostId = null;
  currentPost = null;
  el("detail").className = "detail muted";
  el("detail").textContent = "게시글이 삭제되었습니다.";
  setStatus("게시글 삭제 완료", "good");
  await loadList();
}

async function react(reaction) {
  if (!currentPostId) return;
  const res = await authApi(`/api/community/posts/${currentPostId}/reactions`, {
    method: "POST", body: JSON.stringify({ reaction })
  });
  if (res.status !== 200) return setStatus(`반응 실패 (${res.status})`, "bad");
  setStatus("반응 완료", "good");
  await loadDetail(currentPostId);
}

async function vote(option) {
  if (!currentPostId) return;
  const res = await authApi(`/api/community/posts/${currentPostId}/votes`, {
    method: "POST", body: JSON.stringify({ option })
  });
  if (res.status !== 200) return setStatus(`투표 실패 (${res.status})`, "bad");
  setStatus("투표 완료", "good");
  await loadDetail(currentPostId);
}

async function createPost() {
  const title = el("createTitle").value.trim();
  const content = el("createContent").value.trim();
  if (!title || !content) return setStatus("제목과 내용을 입력하세요.", "bad");
  const payload = {
    title,
    content,
    category: el("createCategory").value || "FREE",
    tags: String(el("createTags").value || "").split(",").map((s) => s.trim()).filter(Boolean),
    pollQuestion: el("createPollQuestion").value.trim() || null,
    pollOptions: String(el("createPollOptions").value || "").split(",").map((s) => s.trim()).filter(Boolean)
  };
  const files = el("createImages").files;
  let res;
  if (files?.length) {
    const form = new FormData();
    form.append("request", JSON.stringify(payload));
    for (const f of Array.from(files)) form.append("images", f);
    res = await authApi("/api/community/posts", { method: "POST", body: form });
  } else {
    res = await authApi("/api/community/posts", { method: "POST", body: JSON.stringify(payload) });
  }
  if (res.status !== 201) return setStatus(`게시글 작성 실패 (${res.status})`, "bad");
  el("createModal").classList.remove("show");
  el("createTitle").value = "";
  el("createContent").value = "";
  el("createTags").value = "";
  el("createPollQuestion").value = "";
  el("createPollOptions").value = "";
  el("createImages").value = "";
  setStatus("게시글 작성 완료", "good");
  await loadListFirstPage();
}

function bind() {
  el("loadListButton").onclick = () => loadListFirstPage();
  el("prevPageButton").onclick = () => { if (currentPage > 0) { currentPage--; loadList(currentPage); } };
  el("nextPageButton").onclick = () => { if (currentPage + 1 < currentTotalPages) { currentPage++; loadList(currentPage); } };
  el("showCreateButton").onclick = () => el("createModal").classList.add("show");
  el("createSubmitButton").onclick = () => createPost();
  el("createCancelButton").onclick = () => el("createModal").classList.remove("show");
  el("createModal").addEventListener("click", (e) => { if (e.target === el("createModal")) el("createModal").classList.remove("show"); });
  el("typeInput").addEventListener("change", () => loadListFirstPage());
  el("categoryInput").addEventListener("change", () => loadListFirstPage());
}

async function init() {
  mountNotificationBell("notificationBellMount");
  bind();
  renderSessionInfo();
  setStatus("세션을 연결하면 커뮤니티를 이용할 수 있습니다.");
  const savedRole = (() => { try { return window.localStorage.getItem(ACTIVE_ROLE_KEY) || ""; } catch { return ""; } })();
  const initialRole = savedRole === "google" ? "google" : (loadRealSession() ? "google" : "");
  if (initialRole) {
    await login();
  } else {
    requestLoginRecovery();
  }
}

window.addEventListener("storage", (event) => {
  if (event.key !== REAL_SESSION_KEY && event.key !== ACTIVE_ROLE_KEY) return;
  if (loadRealSession()?.firebaseIdToken) { login(); return; }
  requestLoginRecovery();
});
window.addEventListener("focus", () => {
  if (!activeRole && loadRealSession()?.firebaseIdToken) login();
});

init();

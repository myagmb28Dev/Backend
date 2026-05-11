import { createJsonAuthedRequest } from "/js/api-client.js";
import { loadSession } from "/js/session-store.js";
import { mountNotificationBell } from "/js/notification-web.js";

const REAL_SESSION_KEY = "pogun-real-firebase-session-v1";
const BACKEND_BASE = window.location.origin;
const el = (id) => document.getElementById(id);
let selectedPostId = null;
let currentPage = 0;
let currentTotalPages = 0;

const getToken = () => loadSession(REAL_SESSION_KEY)?.firebaseIdToken || null;
const authRequest = createJsonAuthedRequest({ baseUrl: BACKEND_BASE, getToken });

function setStatus(msg, tone = "") {
  const node = el("status");
  node.textContent = msg;
  node.className = `status${tone ? ` ${tone}` : ""}`;
}
function print(v) { el("output").textContent = JSON.stringify(v, null, 2); }
function esc(v) { return String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;"); }
function parseCsv(v) { return String(v || "").split(",").map((s) => s.trim()).filter(Boolean); }
function setSessionHint() {
  el("sessionHint").textContent = getToken() ? "로그인 세션 확인됨" : "로그인 세션 없음. login-flow에서 먼저 로그인";
}

async function loadList() {
  const q = encodeURIComponent(el("qInput").value.trim());
  const type = encodeURIComponent(el("typeInput").value.trim() || "LATEST");
  const category = encodeURIComponent(el("categoryInput").value.trim());
  const tag = encodeURIComponent(el("tagInput").value.trim());
  const page = Math.max(0, Number(el("pageInput").value || 0));
  const size = Math.max(1, Math.min(50, Number(el("sizeInput").value || 20)));
  const path = `/api/community/posts?type=${type}&category=${category}&tag=${tag}&q=${q}&page=${page}&size=${size}`;
  const res = await authRequest(path);
  print(res.body);
  if (!res.ok) return setStatus(`목록 조회 실패 (${res.status})`, "bad");

  const data = res.body?.data || {};
  const items = Array.isArray(data.items) ? data.items : [];
  currentPage = page;
  currentTotalPages = Number(data.totalPages || 0);
  el("pageInfo").textContent = `${page + 1} / ${Math.max(currentTotalPages, 1)} · total=${data.totalElements ?? 0}`;
  el("postList").innerHTML = items.length ? items.map((item) => `
    <div class="post-item ${selectedPostId === item.id ? "active" : ""}" data-id="${item.id}">
      <div><strong>${esc(item.title)}</strong></div>
      <div class="post-meta">id=${esc(item.id)} · ${esc(item.category)} · by ${esc(item.authorName)} · like=${item.likeCount} · view=${item.viewCount}</div>
    </div>
  `).join("") : `<div class="muted">목록 없음</div>`;
  el("postList").querySelectorAll(".post-item").forEach((node) => {
    node.addEventListener("click", () => {
      selectedPostId = node.getAttribute("data-id");
      el("postIdInput").value = selectedPostId || "";
      loadDetail();
    });
  });
  setStatus("목록 조회 성공", "good");
}

function buildPayload() {
  return {
    title: el("titleInput").value.trim(),
    content: el("contentInput").value.trim(),
    category: el("categorySelect").value.trim() || "FREE",
    tags: parseCsv(el("tagsInput").value),
    pollQuestion: el("pollQuestionInput").value.trim() || null,
    pollOptions: parseCsv(el("pollOptionsInput").value)
  };
}

async function createJson() {
  const res = await authRequest("/api/community/posts", { method: "POST", body: JSON.stringify(buildPayload()) });
  print(res.body);
  if (!res.ok) return setStatus(`생성 실패 (${res.status})`, "bad");
  selectedPostId = res.body?.data?.id || null;
  if (selectedPostId) el("postIdInput").value = selectedPostId;
  setStatus("생성 성공(JSON)", "good");
  await loadList();
}

async function createMultipart() {
  const form = new FormData();
  form.append("request", JSON.stringify(buildPayload()));
  for (const f of Array.from(el("imagesInput").files || [])) form.append("images", f);
  const token = getToken();
  const response = await fetch(`${BACKEND_BASE}/api/community/posts`, { method: "POST", headers: token ? { Authorization: `Bearer ${token}` } : {}, body: form });
  const body = await response.json().catch(() => ({}));
  print(body);
  if (response.status < 200 || response.status >= 300) return setStatus(`생성 실패 (${response.status})`, "bad");
  selectedPostId = body?.data?.id || null;
  if (selectedPostId) el("postIdInput").value = selectedPostId;
  setStatus("생성 성공(MULTIPART)", "good");
  await loadList();
}

async function loadDetail() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  if (!postId) return setStatus("postId 필요", "bad");
  selectedPostId = postId;
  const res = await authRequest(`/api/community/posts/${postId}`);
  print(res.body);
  if (!res.ok) return setStatus(`상세 실패 (${res.status})`, "bad");
  const d = res.body?.data || {};
  el("detailWrap").innerHTML = `
    <div><strong>${esc(d.title)}</strong></div>
    <div class="muted">author=${esc(d.authorName)} · category=${esc(d.category)} · like=${d.likeCount} · view=${d.viewCount}</div>
    <div>${esc(d.content || "")}</div>
    <div class="muted">tags=${esc((d.tags || []).join(", "))}</div>
    <div class="muted">poll=${esc(d.poll?.question || "-")} / options=${esc((d.poll?.options || []).join(", "))}</div>
    <div class="images">${(d.imageUrls || []).map((u) => `<img src="${esc(u)}" alt="community-image">`).join("")}</div>
  `;
  el("titleInput").value = d.title || "";
  el("contentInput").value = d.content || "";
  el("categorySelect").value = d.category || "FREE";
  el("tagsInput").value = Array.isArray(d.tags) ? d.tags.join(",") : "";
  el("pollQuestionInput").value = d.poll?.question || "";
  el("pollOptionsInput").value = Array.isArray(d.poll?.options) ? d.poll.options.join(",") : "";
  setStatus("상세 조회 성공", "good");
}

async function updateJson() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  if (!postId) return setStatus("postId 필요", "bad");
  const payload = buildPayload();
  const res = await authRequest(`/api/community/posts/${postId}`, { method: "PATCH", body: JSON.stringify(payload) });
  print(res.body);
  if (!res.ok) return setStatus(`수정 실패 (${res.status})`, "bad");
  setStatus("수정 성공(JSON)", "good");
  await loadDetail();
}

async function updateMultipart() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  if (!postId) return setStatus("postId 필요", "bad");
  const form = new FormData();
  form.append("request", JSON.stringify(buildPayload()));
  for (const f of Array.from(el("imagesInput").files || [])) form.append("images", f);
  const token = getToken();
  const response = await fetch(`${BACKEND_BASE}/api/community/posts/${postId}`, { method: "PATCH", headers: token ? { Authorization: `Bearer ${token}` } : {}, body: form });
  const body = await response.json().catch(() => ({}));
  print(body);
  if (response.status < 200 || response.status >= 300) return setStatus(`수정 실패 (${response.status})`, "bad");
  setStatus("수정 성공(MULTIPART)", "good");
  await loadDetail();
}

async function removePost() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  if (!postId) return setStatus("postId 필요", "bad");
  const res = await authRequest(`/api/community/posts/${postId}`, { method: "DELETE" });
  print(res.body);
  if (!res.ok) return setStatus(`삭제 실패 (${res.status})`, "bad");
  selectedPostId = null;
  el("postIdInput").value = "";
  el("detailWrap").textContent = "상세 없음";
  setStatus("삭제 성공", "good");
  await loadList();
}

async function loadComments() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  if (!postId) return setStatus("postId 필요", "bad");
  const res = await authRequest(`/api/community/posts/${postId}/comments`);
  print(res.body);
  if (!res.ok) return setStatus(`댓글 조회 실패 (${res.status})`, "bad");
  const rows = Array.isArray(res.body?.data) ? res.body.data : [];
  const flatten = [];
  rows.forEach((c) => {
    flatten.push({ ...c, depth: 0 });
    (c.replies || []).forEach((r) => flatten.push({ ...r, depth: 1 }));
  });
  el("commentList").innerHTML = flatten.map((c) => `
    <div class="comment-item">
      <div class="meta">${"&nbsp;".repeat(c.depth * 2)}id=${esc(c.id)} · ${esc(c.authorNickname)} · ${esc(c.createdAt)}</div>
      <div>${esc(c.content)}</div>
    </div>
  `).join("") || `<div class="muted">댓글 없음</div>`;
  setStatus("댓글 조회 성공", "good");
}

async function createComment() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  const content = el("commentInput").value.trim();
  if (!postId || !content) return setStatus("postId/댓글 내용 필요", "bad");
  const res = await authRequest(`/api/community/posts/${postId}/comments`, { method: "POST", body: JSON.stringify({ content }) });
  print(res.body);
  if (!res.ok) return setStatus(`댓글 작성 실패 (${res.status})`, "bad");
  setStatus("댓글 작성 성공", "good");
  el("commentInput").value = "";
  await loadComments();
}

async function deleteComment() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  const commentId = el("deleteCommentIdInput").value.trim();
  if (!postId || !commentId) return setStatus("postId/commentId 필요", "bad");
  const res = await authRequest(`/api/community/posts/${postId}/comments/${commentId}`, { method: "DELETE" });
  print(res.body);
  if (!res.ok) return setStatus(`댓글 삭제 실패 (${res.status})`, "bad");
  setStatus("댓글 삭제 성공", "good");
  await loadComments();
}

async function vote() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  const option = el("voteOptionInput").value.trim();
  if (!postId || !option) return setStatus("postId/option 필요", "bad");
  const res = await authRequest(`/api/community/posts/${postId}/votes`, { method: "POST", body: JSON.stringify({ option }) });
  print(res.body);
  if (!res.ok) return setStatus(`투표 실패 (${res.status})`, "bad");
  setStatus("투표 성공", "good");
}

async function react() {
  const postId = (el("postIdInput").value || selectedPostId || "").trim();
  const reaction = (el("reactionInput").value || "LIKE").trim().toUpperCase();
  if (!postId) return setStatus("postId 필요", "bad");
  const res = await authRequest(`/api/community/posts/${postId}/reactions`, { method: "POST", body: JSON.stringify({ reaction }) });
  print(res.body);
  if (!res.ok) return setStatus(`반응 실패 (${res.status})`, "bad");
  setStatus("반응 성공", "good");
}

function bind() {
  el("loadListButton").onclick = () => loadList();
  el("prevPageButton").onclick = async () => { if (currentPage > 0) { el("pageInput").value = String(currentPage - 1); await loadList(); } };
  el("nextPageButton").onclick = async () => { if (currentPage + 1 < currentTotalPages) { el("pageInput").value = String(currentPage + 1); await loadList(); } };
  el("createButton").onclick = () => createJson();
  el("createMultipartButton").onclick = () => createMultipart();
  el("detailButton").onclick = () => loadDetail();
  el("updateButton").onclick = () => updateJson();
  el("updateMultipartButton").onclick = () => updateMultipart();
  el("deleteButton").onclick = () => removePost();
  el("commentsButton").onclick = () => loadComments();
  el("commentCreateButton").onclick = () => createComment();
  el("commentDeleteButton").onclick = () => deleteComment();
  el("voteButton").onclick = () => vote();
  el("reactionButton").onclick = () => react();
}

async function init() {
  mountNotificationBell({ mountId: "notificationBellMount", mode: "full", forceShowToggle: true }).catch(() => {});
  setSessionHint();
  bind();
  if (getToken()) {
    setStatus("세션 확인됨. 목록 조회 가능", "good");
    await loadList();
  } else {
    setStatus("세션 없음. login-flow에서 로그인 후 사용", "bad");
  }
}

init();

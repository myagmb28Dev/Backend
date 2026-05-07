import { request } from "./js/api-client.js";
import { loadSession as loadStoredSession, removeSession } from "./js/session-store.js";
import { clearRefreshToken, refreshFirebaseSession } from "./js/auth-refresh.js";

const BACKEND_BASE = window.location.origin;
export const ACTIVE_ROLE_KEY = "dm-test-active-role-v1";
export const REAL_SESSION_KEY = "pogun-real-firebase-session-v1";
const NOTIFICATION_DEVICE_ID_KEY = "notification-web-device-id-v1";
const BELL_STYLE_ID = "notification-bell-style";
const mountedBellTargets = new Set();
let notificationBridgeInitialized = false;

function injectBellStyles() {
  if (document.getElementById(BELL_STYLE_ID)) {
    return;
  }
  const style = document.createElement("style");
  style.id = BELL_STYLE_ID;
  style.textContent = `
    .notification-bell {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 42px;
      height: 42px;
      border: 1px solid rgba(31,41,51,.12);
      border-radius: 8px;
      background: rgba(255,255,255,.82);
      color: #1f2933;
      text-decoration: none;
      box-shadow: 0 8px 20px rgba(31,41,51,.08);
    }
    .notification-bell:hover {
      background: #fff;
      border-color: rgba(192,95,44,.34);
    }
    .notification-bell svg {
      width: 20px;
      height: 20px;
      display: block;
    }
    .notification-bell__badge {
      position: absolute;
      top: -6px;
      right: -6px;
      min-width: 20px;
      height: 20px;
      padding: 0 5px;
      border-radius: 999px;
      background: #b42318;
      color: #fff;
      font-size: 11px;
      font-weight: 900;
      line-height: 20px;
      text-align: center;
      box-shadow: 0 6px 12px rgba(180,35,24,.22);
    }
    .notification-bell__badge[hidden] {
      display: none !important;
    }
  `;
  document.head.appendChild(style);
}

function resolveElement(target) {
  if (!target) {
    return null;
  }
  return typeof target === "string" ? document.getElementById(target) : target;
}

function notifyNotificationEvent(detail = null) {
  window.dispatchEvent(new CustomEvent("notification-web:message", { detail }));
}

function isEmbeddedInShell() {
  return window.parent && window.parent !== window;
}

function notificationHref() {
  return isEmbeddedInShell() ? "#" : "/Full_Compact.html?view=notification";
}

function bindNotificationBellNavigation(bell) {
  if (!bell || bell.dataset.shellNavigationBound === "true") {
    return;
  }
  bell.dataset.shellNavigationBound = "true";
  bell.addEventListener("click", (event) => {
    if (!isEmbeddedInShell()) {
      return;
    }
    event.preventDefault();
    window.parent.postMessage({ type: "pogun-open-view", view: "notification" }, window.location.origin);
  });
}

function initializeNotificationBridge() {
  if (notificationBridgeInitialized || typeof window === "undefined") {
    return;
  }
  notificationBridgeInitialized = true;

  if (navigator.serviceWorker) {
    navigator.serviceWorker.addEventListener("message", (event) => {
      if (event?.data?.type !== "notification-web:message") {
        return;
      }
      notifyNotificationEvent(event.data.payload || null);
      for (const target of mountedBellTargets) {
        refreshNotificationBell(target).catch(() => {});
      }
    });
  }

  window.addEventListener("storage", (event) => {
    if (event.key !== REAL_SESSION_KEY) {
      return;
    }
    for (const target of mountedBellTargets) {
      refreshNotificationBell(target).catch(() => {});
    }
  });
}

export function getStoredSession() {
  return loadStoredSession(REAL_SESSION_KEY);
}

export function clearStoredSession() {
  removeSession(REAL_SESSION_KEY);
  removeSession(ACTIVE_ROLE_KEY);
  clearRefreshToken();
}

export function getOrCreateNotificationDeviceId() {
  try {
    const existing = window.localStorage.getItem(NOTIFICATION_DEVICE_ID_KEY);
    if (existing) {
      return existing;
    }
    const generated = typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
      ? crypto.randomUUID()
      : `web-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
    window.localStorage.setItem(NOTIFICATION_DEVICE_ID_KEY, generated);
    return generated;
  } catch {
    return `web-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
  }
}

export function maskToken(token) {
  const value = String(token || "").trim();
  if (!value) {
    return "없음";
  }
  if (value.length <= 24) {
    return value;
  }
  return `${value.slice(0, 12)}...${value.slice(-8)}`;
}

export function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(date);
}

export async function authRequest(path, init = {}) {
  let session = getStoredSession();
  if (!session?.firebaseIdToken) {
    clearStoredSession();
    throw new Error("로그인이 필요합니다.");
  }

  const headers = new Headers(init.headers || {});
  if (!headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${session.firebaseIdToken}`);
  }
  let response = await request(`${BACKEND_BASE}${path}`, { ...init, headers });
  if (response.status !== 401) {
    return response;
  }

  const refreshedSession = await refreshFirebaseSession(REAL_SESSION_KEY, BACKEND_BASE);
  if (!refreshedSession?.firebaseIdToken) {
    clearStoredSession();
    return response;
  }
  session = refreshedSession;
  headers.set("Authorization", `Bearer ${session.firebaseIdToken}`);
  response = await request(`${BACKEND_BASE}${path}`, { ...init, headers });
  if (response.status === 401 || response.status === 403) {
    clearStoredSession();
  }
  return response;
}

export async function fetchUnreadCount() {
  const response = await authRequest("/api/notifications/unread-count");
  if (response.status !== 200) {
    throw new Error(`읽지 않은 알림 수 조회 실패: ${JSON.stringify(response.body)}`);
  }
  return Number(response.body?.data?.unreadCount || 0);
}

export async function fetchNotifications(page = 0, size = 20) {
  const response = await authRequest(`/api/notifications?page=${page}&size=${size}`);
  if (response.status !== 200) {
    throw new Error(`알림 목록 조회 실패: ${JSON.stringify(response.body)}`);
  }
  return response.body?.data;
}

export async function markNotificationRead(notificationId) {
  const response = await authRequest(`/api/notifications/${notificationId}/read`, {
    method: "PATCH"
  });
  if (response.status !== 200) {
    throw new Error(`알림 읽음 처리 실패: ${JSON.stringify(response.body)}`);
  }
  return response.body?.data;
}

export async function markAllNotificationsRead() {
  const response = await authRequest("/api/notifications/read-all", {
    method: "PATCH"
  });
  if (response.status !== 200) {
    throw new Error(`전체 알림 읽음 처리 실패: ${JSON.stringify(response.body)}`);
  }
  return response.body?.data;
}

export function mountNotificationBell(target, options = {}) {
  const container = resolveElement(target);
  if (!container) {
    return null;
  }

  initializeNotificationBridge();
  injectBellStyles();
  const href = options.href || notificationHref();
  container.innerHTML = `
    <a class="notification-bell" href="${href}" title="알림 보기" aria-label="알림 보기">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M12 3a4 4 0 0 0-4 4v1.1c0 .88-.24 1.74-.69 2.49L5.6 13.5A2 2 0 0 0 7.27 16h9.46a2 2 0 0 0 1.67-2.5l-1.71-2.91A4.97 4.97 0 0 1 16 8.1V7a4 4 0 0 0-4-4Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
        <path d="M9.5 18a2.5 2.5 0 0 0 5 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
      </svg>
      <span class="notification-bell__badge" hidden>0</span>
    </a>
  `;
  mountedBellTargets.add(target);
  const bell = container.querySelector(".notification-bell");
  bindNotificationBellNavigation(bell);
  return bell;
}

export async function refreshNotificationBell(target) {
  const container = resolveElement(target);
  const bell = container?.querySelector(".notification-bell");
  const badge = container?.querySelector(".notification-bell__badge");
  if (!bell || !badge) {
    return 0;
  }

  const session = getStoredSession();
  bell.href = notificationHref();
  bindNotificationBellNavigation(bell);
  if (!session?.firebaseIdToken) {
    badge.hidden = true;
    bell.title = "로그인 후 알림 보기";
    return 0;
  }

  try {
    const count = await fetchUnreadCount();
    bell.title = count > 0 ? `읽지 않은 알림 ${count}개` : "알림 보기";
    badge.hidden = count <= 0;
    badge.textContent = count > 99 ? "99+" : String(count);
    return count;
  } catch {
    badge.hidden = true;
    bell.title = "알림 보기";
    return 0;
  }
}

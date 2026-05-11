import { refreshFirebaseSession } from "./auth-refresh.js";

const REAL_SESSION_KEY = "pogun-real-firebase-session-v1";
const ACTIVE_ROLE_KEY = "dm-test-active-role-v1";
const REFRESH_TOKEN_KEY = "pogun-refresh-token-v1";
const CLIENT_SESSION_KEY = "pogun-global-presence-client-session-v1";
const HEARTBEAT_INTERVAL_MS = 8000;

let heartbeatTimer = null;
let inFlight = false;

function readSession() {
  try {
    const raw = window.localStorage.getItem(REAL_SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return parsed?.firebaseIdToken ? parsed : null;
  } catch {
    return null;
  }
}

function clientSessionId() {
  try {
    const existing = window.sessionStorage.getItem(CLIENT_SESSION_KEY);
    if (existing) return existing;
    const generated = crypto?.randomUUID
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    window.sessionStorage.setItem(CLIENT_SESSION_KEY, generated);
    return generated;
  } catch {
    return "session-storage-unavailable";
  }
}

function clearExpiredSession() {
  try {
    window.localStorage.removeItem(REAL_SESSION_KEY);
    window.localStorage.removeItem(ACTIVE_ROLE_KEY);
    window.sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch {}
}

async function sendGlobalPresenceHeartbeat(reason = "interval") {
  const session = readSession();
  if (!session?.firebaseIdToken || inFlight) return null;

  inFlight = true;
  try {
    const requestHeartbeat = async (token) => fetch("/api/presence/heartbeat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        clientSessionId: clientSessionId(),
        page: window.location.pathname.replace(/^\//, "") || "root",
        reason
      })
    });
    let response = await requestHeartbeat(session.firebaseIdToken);
    if (response.status === 401 || response.status === 403) {
      const refreshed = await refreshFirebaseSession(REAL_SESSION_KEY, window.location.origin);
      if (refreshed?.firebaseIdToken) {
        response = await requestHeartbeat(refreshed.firebaseIdToken);
      }
      if (response.status === 401 || response.status === 403) {
        clearExpiredSession();
        stopGlobalPresenceHeartbeat();
        window.dispatchEvent(new CustomEvent("pogun:presence-expired"));
        return null;
      }
    }
    const payload = await response.json().catch(() => null);
    window.dispatchEvent(new CustomEvent("pogun:presence-heartbeat", { detail: payload?.data || null }));
    return payload?.data || null;
  } catch (error) {
    console.debug("global presence heartbeat failed", { reason, message: error?.message || String(error) });
    return null;
  } finally {
    inFlight = false;
  }
}

export function startGlobalPresenceHeartbeat() {
  if (heartbeatTimer) return;
  sendGlobalPresenceHeartbeat("start");
  heartbeatTimer = setInterval(() => sendGlobalPresenceHeartbeat(), HEARTBEAT_INTERVAL_MS);
}

export function stopGlobalPresenceHeartbeat() {
  if (!heartbeatTimer) return;
  clearInterval(heartbeatTimer);
  heartbeatTimer = null;
}

window.addEventListener("pageshow", () => {
  startGlobalPresenceHeartbeat();
  sendGlobalPresenceHeartbeat("pageshow");
});
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") {
    startGlobalPresenceHeartbeat();
    sendGlobalPresenceHeartbeat("visible");
  }
});
window.addEventListener("focus", () => {
  startGlobalPresenceHeartbeat();
  sendGlobalPresenceHeartbeat("focus");
});
window.addEventListener("storage", (event) => {
  if (event.key === REAL_SESSION_KEY) {
    if (readSession()) {
      startGlobalPresenceHeartbeat();
      sendGlobalPresenceHeartbeat("storage-session");
    } else {
      stopGlobalPresenceHeartbeat();
    }
  }
});

startGlobalPresenceHeartbeat();

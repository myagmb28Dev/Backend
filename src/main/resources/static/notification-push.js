import { getApp, getApps, initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import { getMessaging, getToken, isSupported, onMessage } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-messaging.js";
import { FIREBASE_WEB_CONFIG, FIREBASE_WEB_VAPID_KEY } from "./firebase-web-config.js";
import { authRequest, getOrCreateNotificationDeviceId, getStoredSession } from "./notification-web.js";

let firebaseApp = null;
let foregroundInitialized = false;
const PUSH_TOKEN_STORAGE_KEY = "notification-web-fcm-token-v1";

function resolveFirebaseApp() {
  if (firebaseApp) {
    return firebaseApp;
  }
  firebaseApp = getApps().find((app) => app.name === "notification-web")
    || (getApps().length > 0 ? getApp() : initializeApp(FIREBASE_WEB_CONFIG));
  return firebaseApp;
}

async function resolveMessaging() {
  const supported = await isSupported().catch(() => false);
  if (!supported) {
    return null;
  }
  return getMessaging(resolveFirebaseApp());
}

function buildForegroundNotification(payload) {
  const data = payload?.data || {};
  return {
    title: data.title || "새 알림",
    body: data.body || "",
    tag: data.notificationId || undefined,
    data
  };
}

function storeRegisteredPushToken(token) {
  try {
    if (!token) {
      window.localStorage.removeItem(PUSH_TOKEN_STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(PUSH_TOKEN_STORAGE_KEY, token);
  } catch {
  }
}

export function getStoredRegisteredPushToken() {
  try {
    return window.localStorage.getItem(PUSH_TOKEN_STORAGE_KEY) || "";
  } catch {
    return "";
  }
}

async function resolveCurrentPushToken() {
  if (typeof Notification === "undefined") {
    throw new Error("이 브라우저는 Notification API를 지원하지 않습니다.");
  }
  if (!FIREBASE_WEB_VAPID_KEY) {
    throw new Error("VAPID 공개키가 설정되지 않았습니다.");
  }

  const messaging = await resolveMessaging();
  if (!messaging) {
    throw new Error("이 브라우저는 Firebase 웹 푸시를 지원하지 않습니다.");
  }

  const serviceWorkerRegistration = await navigator.serviceWorker.register("/firebase-messaging-sw.js", { type: "module" });
  const token = await getToken(messaging, {
    vapidKey: FIREBASE_WEB_VAPID_KEY,
    serviceWorkerRegistration
  });
  if (!token) {
    throw new Error("FCM 토큰을 발급받지 못했습니다.");
  }
  return token;
}

async function savePushToken(token) {
  const response = await authRequest("/api/notifications/fcm-token", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      token,
      platform: "WEB",
      deviceId: getOrCreateNotificationDeviceId()
    })
  });
  if (response.status !== 200) {
    throw new Error(`FCM 토큰 저장 실패: ${JSON.stringify(response.body)}`);
  }
  storeRegisteredPushToken(token);
  return response.body?.data || null;
}

export async function getPushSupportState() {
  const supported = await isSupported().catch(() => false);
  const session = getStoredSession();
  return {
    supported,
    permission: typeof Notification === "undefined" ? "unsupported" : Notification.permission,
    hasSession: Boolean(session?.firebaseIdToken),
    hasRegisteredUser: Boolean(session?.userId)
  };
}

export async function registerWebPushToken() {
  const session = getStoredSession();
  if (!session?.firebaseIdToken) {
    throw new Error("로그인 후 웹 알림을 등록할 수 있습니다.");
  }
  if (!session?.userId) {
    throw new Error("온보딩 완료 후 웹 알림을 등록할 수 있습니다.");
  }
  if (typeof Notification === "undefined") {
    throw new Error("이 브라우저는 Notification API를 지원하지 않습니다.");
  }
  const permission = Notification.permission === "granted"
    ? "granted"
    : await Notification.requestPermission();
  if (permission !== "granted") {
    throw new Error("브라우저 알림 권한이 허용되지 않았습니다.");
  }

  const token = await resolveCurrentPushToken();
  const data = await savePushToken(token);

  return {
    permission,
    token,
    data
  };
}

export async function syncWebPushTokenIfPossible() {
  const session = getStoredSession();
  if (!session?.firebaseIdToken || !session?.userId) {
    return { synced: false, token: getStoredRegisteredPushToken(), reason: "NO_SESSION" };
  }
  if (typeof Notification === "undefined" || Notification.permission !== "granted") {
    return { synced: false, token: getStoredRegisteredPushToken(), reason: "PERMISSION_NOT_GRANTED" };
  }

  const state = await getPushSupportState();
  if (!state.supported) {
    return { synced: false, token: getStoredRegisteredPushToken(), reason: "UNSUPPORTED" };
  }

  const token = await resolveCurrentPushToken();
  await savePushToken(token);
  return { synced: true, token, reason: "SYNCED" };
}

export async function initForegroundNotifications(options = {}) {
  const messaging = await resolveMessaging();
  if (!messaging || foregroundInitialized) {
    return;
  }
  foregroundInitialized = true;

  onMessage(messaging, (payload) => {
    const built = buildForegroundNotification(payload);
    window.dispatchEvent(new CustomEvent("notification-web:message", { detail: payload }));
    if (typeof Notification !== "undefined" && Notification.permission === "granted") {
      try {
        new Notification(built.title, {
          body: built.body,
          tag: built.tag,
          data: built.data
        });
      } catch {
      }
    }
    if (typeof options.onMessageReceived === "function") {
      options.onMessageReceived(payload, built);
    }
  });
}

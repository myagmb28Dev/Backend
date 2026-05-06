import { getApp, getApps, initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import { getMessaging, onBackgroundMessage } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-messaging-sw.js";
import { FIREBASE_WEB_CONFIG } from "./firebase-web-config.js";

const firebaseApp = getApps().length > 0 ? getApp() : initializeApp(FIREBASE_WEB_CONFIG);
const messaging = getMessaging(firebaseApp);
const defaultTargetUrl = new URL("/Full_Compact.html", self.location.origin).toString();

onBackgroundMessage(messaging, (payload) => {
  const data = payload?.data || {};
  const title = data.title || "새 알림";
  const body = data.body || "";
  const clientsPromise = clients.matchAll({ type: "window", includeUncontrolled: true })
    .then((clientList) => Promise.all(clientList.map((client) => client.postMessage({
      type: "notification-web:message",
      payload
    }))));

  const notificationPromise = self.registration.showNotification(title, {
    body,
    tag: data.notificationId || undefined,
    data: {
      url: data.url || defaultTargetUrl,
      ...data
    }
  });

  void Promise.allSettled([clientsPromise, notificationPromise]);
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = event.notification?.data?.url || defaultTargetUrl;
  event.waitUntil((async () => {
    const clientList = await clients.matchAll({ type: "window", includeUncontrolled: true });
    const matched = clientList.find((client) => client.url.startsWith(self.location.origin));
    if (matched) {
      await matched.focus();
      if ("navigate" in matched) {
        return matched.navigate(targetUrl);
      }
      return matched;
    }
    return clients.openWindow(targetUrl);
  })());
});

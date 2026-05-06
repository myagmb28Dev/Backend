import { request } from "./api-client.js";
import { loadSession, saveSession } from "./session-store.js";

export async function refreshFirebaseSession(storageKey, baseUrl = window.location.origin) {
  const session = loadSession(storageKey);
  if (!session?.refreshToken) {
    return null;
  }

  const response = await request(`${baseUrl}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: session.refreshToken })
  });
  if (response.status !== 200) {
    return null;
  }

  const data = response.body?.data || {};
  if (!data.idToken || !data.refreshToken) {
    return null;
  }

  const nextSession = {
    ...session,
    firebaseIdToken: data.idToken,
    refreshToken: data.refreshToken
  };
  saveSession(storageKey, nextSession);
  return nextSession;
}


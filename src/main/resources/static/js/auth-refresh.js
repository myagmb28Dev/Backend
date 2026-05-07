import { request } from "./api-client.js";
import { loadSession, saveSession } from "./session-store.js";

const REFRESH_TOKEN_KEY = "pogun-refresh-token-v1";

export function setRefreshToken(token) {
  try {
    if (!token) {
      window.sessionStorage.removeItem(REFRESH_TOKEN_KEY);
      return;
    }
    window.sessionStorage.setItem(REFRESH_TOKEN_KEY, token);
  } catch {
  }
}

export function clearRefreshToken() {
  try {
    window.sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch {
  }
}

export function getRefreshToken() {
  try {
    return window.sessionStorage.getItem(REFRESH_TOKEN_KEY) || "";
  } catch {
    return "";
  }
}

export async function refreshFirebaseSession(storageKey, baseUrl = window.location.origin) {
  const session = loadSession(storageKey);
  const refreshToken = getRefreshToken() || session?.refreshToken || "";

  const response = await request(`${baseUrl}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "same-origin",
    body: JSON.stringify({ refreshToken })
  });
  if (response.status !== 200) {
    return null;
  }

  const data = response.body?.data || {};
  if (!data.idToken) {
    return null;
  }
  if (data.refreshToken) {
    setRefreshToken(data.refreshToken);
  }

  const nextSession = {
    ...session,
    firebaseIdToken: data.idToken
  };
  saveSession(storageKey, nextSession);
  return nextSession;
}

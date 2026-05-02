export function loadSession(storageKey) {
  try {
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (!session?.firebaseIdToken) return null;
    return session;
  } catch {
    return null;
  }
}

export function saveSession(storageKey, session) {
  try {
    window.localStorage.setItem(storageKey, JSON.stringify(session));
  } catch {
  }
}

export function removeSession(storageKey) {
  try {
    window.localStorage.removeItem(storageKey);
  } catch {
  }
}

export function adoptTokenFromQuery({ storageKey, roleKey, roleValue, query }) {
  const token = (query.get("token") || query.get("firebaseIdToken") || "").trim();
  if (!token) {
    return loadSession(storageKey);
  }
  const existing = loadSession(storageKey);
  const session = { ...(existing || {}), firebaseIdToken: token };
  saveSession(storageKey, session);
  try {
    window.localStorage.setItem(roleKey, roleValue);
  } catch {
  }
  try {
    const nextUrl = new URL(window.location.href);
    nextUrl.searchParams.delete("token");
    nextUrl.searchParams.delete("firebaseIdToken");
    const nextQuery = nextUrl.searchParams.toString();
    const nextPath = `${nextUrl.pathname}${nextQuery ? `?${nextQuery}` : ""}${nextUrl.hash || ""}`;
    window.history.replaceState({}, "", nextPath);
  } catch {
  }
  return session;
}

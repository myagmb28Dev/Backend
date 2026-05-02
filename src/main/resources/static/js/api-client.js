export async function readJson(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function request(url, init = {}) {
  const response = await fetch(url, init);
  return { status: response.status, body: await readJson(response) };
}

export function createAuthedRequest({ baseUrl, getToken }) {
  return async function authedRequest(path, init = {}) {
    const token = getToken?.();
    const headers = { "Content-Type": "application/json", ...(init.headers || {}) };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    return request(`${baseUrl}${path}`, { ...init, headers });
  };
}

export function createJsonAuthedRequest({ baseUrl, getToken }) {
  const authed = createAuthedRequest({ baseUrl, getToken });
  return async function jsonAuthedRequest(path, init = {}) {
    const headers = { "Content-Type": "application/json", ...(init.headers || {}) };
    const result = await authed(path, { ...init, headers });
    return { ok: result.status >= 200 && result.status < 300, status: result.status, body: result.body };
  };
}

export async function fetchAuthedBlob(url, token) {
  return fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });
}

const DEFAULT_EMAIL = "playwright-user1@local.dev";
const DEFAULT_PASSWORD = "Test1234!";
const DEFAULT_AUTH_EMULATOR_BASE = "http://127.0.0.1:9099";

function isLocalHost() {
  const host = window.location.hostname;
  return host === "localhost" || host === "127.0.0.1";
}

function isLocalEmulatorTarget() {
  return window.location.port === "8081";
}

function toJsonOrNull(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function postJson(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  const text = await res.text();
  const json = toJsonOrNull(text);
  return { ok: res.ok, status: res.status, body: json, text };
}

async function getJson(url) {
  const res = await fetch(url);
  const text = await res.text();
  const json = toJsonOrNull(text);
  return { ok: res.ok, status: res.status, body: json, text };
}

async function ensureEmailUser(authBase, email, password) {
  const signinUrl = `${authBase}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`;
  const signupUrl = `${authBase}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`;

  let signIn = await postJson(signinUrl, { email, password, returnSecureToken: true });
  if (!signIn.ok) {
    const code = signIn.body?.error?.message || signIn.text || "";
    if (code.includes("EMAIL_NOT_FOUND")) {
      const signUp = await postJson(signupUrl, { email, password, returnSecureToken: true });
      if (!signUp.ok) {
        throw new Error(`에뮬레이터 계정 생성 실패: ${signUp.body?.error?.message || signUp.status}`);
      }
      signIn = await postJson(signinUrl, { email, password, returnSecureToken: true });
    }
  }

  if (!signIn.ok || !signIn.body?.idToken) {
    throw new Error(`에뮬레이터 로그인 실패: ${signIn.body?.error?.message || signIn.status}`);
  }
  return signIn.body.idToken;
}

async function verifyEmailOnAuthEmulator(authBase, projectId, idToken, email) {
  const sendOobUrl = `${authBase}/identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=fake-api-key`;
  const lookupUrl = `${authBase}/identitytoolkit.googleapis.com/v1/accounts:lookup?key=fake-api-key`;
  const oobCodesUrl = `${authBase}/emulator/v1/projects/${encodeURIComponent(projectId)}/oobCodes`;

  const before = await postJson(lookupUrl, { idToken });
  const beforeVerified = Boolean(before.body?.users?.[0]?.emailVerified);
  if (beforeVerified) {
    return { verified: true, alreadyVerified: true };
  }

  const send = await postJson(sendOobUrl, { requestType: "VERIFY_EMAIL", idToken });
  if (!send.ok) {
    throw new Error(`인증 메일 발급 실패: ${send.body?.error?.message || send.status}`);
  }

  const oobCodes = await getJson(oobCodesUrl);
  if (!oobCodes.ok) {
    throw new Error(`oob 코드 조회 실패: ${oobCodes.status}`);
  }

  const match = (oobCodes.body?.oobCodes || [])
    .slice()
    .reverse()
    .find((entry) => entry?.email === email && entry?.requestType === "VERIFY_EMAIL" && entry?.oobLink);

  if (!match?.oobLink) {
    throw new Error("이메일 인증 링크를 찾지 못했습니다.");
  }

  const verifyHit = await fetch(match.oobLink);
  if (!verifyHit.ok) {
    throw new Error(`인증 링크 처리 실패: ${verifyHit.status}`);
  }

  const after = await postJson(lookupUrl, { idToken });
  const afterVerified = Boolean(after.body?.users?.[0]?.emailVerified);
  if (!afterVerified) {
    throw new Error("인증 링크 처리 후에도 emailVerified=false 입니다.");
  }
  return { verified: true, alreadyVerified: false };
}

export async function ensureLocalAdminTestUserVerified(options = {}) {
  if (!isLocalHost()) {
    return { skipped: true, reason: "non-local-host" };
  }
  if (!isLocalEmulatorTarget()) {
    return { skipped: true, reason: "non-emulator-port" };
  }

  const email = options.email || DEFAULT_EMAIL;
  const password = options.password || DEFAULT_PASSWORD;
  const authBase = options.authBase || DEFAULT_AUTH_EMULATOR_BASE;
  const projectId =
    options.projectId ||
    window.localStorage.getItem("pogun-local-project-id") ||
    "pogun-local";

  const idToken = await ensureEmailUser(authBase, email, password);
  const verify = await verifyEmailOnAuthEmulator(authBase, projectId, idToken, email);
  return {
    skipped: false,
    email,
    projectId,
    verified: verify.verified,
    alreadyVerified: verify.alreadyVerified
  };
}

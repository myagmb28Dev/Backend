import fs from "node:fs";

const baseUrl = process.env.BASE_URL || "http://localhost:8081";
const tokenPath = "D:/Codes/Pogun_Back/.local/emulator-user1-firebase-id-token.txt";

function readToken() {
  if (!fs.existsSync(tokenPath)) {
    throw new Error(`token file not found: ${tokenPath}`);
  }
  return fs.readFileSync(tokenPath, "utf8").trim();
}

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function api(path, token) {
  const response = await fetch(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${token}` }
  });
  return { status: response.status, body: await readJsonSafe(response) };
}

async function main() {
  const token = readToken();
  const list = await api("/api/community/posts?page=0&size=1", token);
  if (list.status !== 200) {
    console.error("[list-fail]", JSON.stringify(list, null, 2));
    process.exit(1);
  }

  const postId = list.body?.data?.items?.[0]?.id;
  if (!postId) {
    console.error("[no-post] 커뮤니티 게시물이 없습니다.");
    process.exit(1);
  }

  const detail = await api(`/api/community/posts/${postId}`, token);
  console.log("[community-detail-check]", JSON.stringify({
    postId,
    status: detail.status,
    ok: detail.status === 200,
    title: detail.body?.data?.title ?? null
  }, null, 2));

  if (detail.status !== 200) {
    console.error("[detail-fail]", JSON.stringify(detail, null, 2));
    process.exit(1);
  }
}

main().catch((err) => {
  console.error("[script-error]", err?.stack || String(err));
  process.exit(1);
});

import fs from 'node:fs/promises';
import path from 'node:path';
import { performance } from 'node:perf_hooks';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080';
const AUTH_EMULATOR_HOST = process.env.AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';
const PASSWORD = process.env.STRESS_USER_PASSWORD ?? 'Test1234!';
const USER_COUNT = Number(process.env.STRESS_USER_COUNT ?? '8');
const REPORT_PATH = path.join(process.cwd(), '.local', 'community-stress-report.json');

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function readTokenFile() {
  const filePath = path.join(process.cwd(), '.local', 'emulator-firebase-id-token.txt');
  return (await fs.readFile(filePath, 'utf8')).trim();
}

async function parseJson(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function request(url, init = {}) {
  const startedAt = performance.now();
  const response = await fetch(url, init);
  const latencyMs = performance.now() - startedAt;
  const body = await parseJson(response);
  return {
    status: response.status,
    latencyMs,
    body
  };
}

async function backendLogin(firebaseIdToken) {
  return request(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firebaseIdToken })
  });
}

async function emulatorAuth(email) {
  const payload = {
    email,
    password: PASSWORD,
    returnSecureToken: true
  };
  const signUpUrl = `http://${AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`;
  const signInUrl = `http://${AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key`;

  const signUp = await request(signUpUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  if (signUp.status !== 200) {
    const code = signUp.body?.error?.message;
    if (code !== 'EMAIL_EXISTS') {
      throw new Error(`Emulator sign up failed for ${email}: ${JSON.stringify(signUp.body)}`);
    }
  }

  const signIn = await request(signInUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  if (signIn.status !== 200) {
    throw new Error(`Emulator sign in failed for ${email}: ${JSON.stringify(signIn.body)}`);
  }

  return signIn.body.idToken;
}

async function buildUsers() {
  const rootToken = await readTokenFile();
  const rootLogin = await backendLogin(rootToken);
  if (rootLogin.status !== 200) {
    throw new Error(`Primary emulator login failed: ${JSON.stringify(rootLogin.body)}`);
  }

  const users = [{ email: 'tester@local.dev', token: rootToken }];

  for (let index = 0; index < USER_COUNT - 1; index += 1) {
    const email = `stress-user-${index}@local.dev`;
    const token = await emulatorAuth(email);
    const login = await backendLogin(token);
    if (login.status !== 200) {
      throw new Error(`Backend login bootstrap failed for ${email}: ${JSON.stringify(login.body)}`);
    }
    users.push({ email, token });
  }

  return users;
}

async function api(token, pathname, init = {}) {
  return request(`${BASE_URL}${pathname}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',

      ...(init.headers ?? {})
    }
  });
}

function summarizeResults(results) {
  const latencies = results.map((item) => item.latencyMs).sort((a, b) => a - b);
  const statuses = {};
  for (const result of results) {
    statuses[result.status] = (statuses[result.status] ?? 0) + 1;
  }
  const pick = (ratio) => {
    if (latencies.length === 0) {
      return 0;
    }
    const index = Math.min(latencies.length - 1, Math.floor(latencies.length * ratio));
    return Number(latencies[index].toFixed(2));
  };
  return {
    totalRequests: results.length,
    statusCounts: statuses,
    avgLatencyMs: Number((latencies.reduce((sum, value) => sum + value, 0) / Math.max(latencies.length, 1)).toFixed(2)),
    p50LatencyMs: pick(0.5),
    p95LatencyMs: pick(0.95),
    maxLatencyMs: Number((latencies.at(-1) ?? 0).toFixed(2))
  };
}

async function runBounded(tasks, concurrency) {
  const queue = [...tasks];
  const results = [];

  const workers = Array.from({ length: Math.min(concurrency, queue.length) }, async () => {
    while (queue.length > 0) {
      const task = queue.shift();
      if (!task) {
        return;
      }
      results.push(await task());
    }
  });

  await Promise.all(workers);
  return results;
}

async function createPost(token, body) {
  return api(token, '/api/community/posts', {
    method: 'POST',
    body: JSON.stringify(body)
  });
}

async function deletePost(token, postId) {
  return api(token, `/api/community/posts/${postId}`, { method: 'DELETE' });
}

async function getDetail(token, postId) {
  return api(token, `/api/community/posts/${postId}`);
}

async function getList(token) {
  return api(token, '/api/community/posts');
}

async function createComment(token, postId, content, parentCommentId = null) {
  return api(token, `/api/community/posts/${postId}/comments`, {
    method: 'POST',
    body: JSON.stringify(parentCommentId ? { content, parentCommentId } : { content })
  });
}

async function getComments(token, postId) {
  return api(token, `/api/community/posts/${postId}/comments`);
}

async function react(token, postId, reaction) {
  return api(token, `/api/community/posts/${postId}/reactions`, {
    method: 'POST',
    body: JSON.stringify({ reaction })
  });
}

async function vote(token, postId, option) {
  return api(token, `/api/community/posts/${postId}/votes`, {
    method: 'POST',
    body: JSON.stringify({ option })
  });
}

function assertNoServerErrors(results, label) {
  const failures = results.filter((item) => item.status >= 500);
  if (failures.length > 0) {
    throw new Error(`${label} produced unexpected 5xx responses: ${JSON.stringify(failures.slice(0, 5), null, 2)}`);
  }
}

async function runLoadScenario(users) {
  const owner = users[0];
  const created = await createPost(owner.token, {
    title: 'Stress load baseline',
    content: 'Baseline post for load testing.',
    category: 'FREE',
    tags: ['stress', 'load']
  });
  const postId = created.body?.data?.id;
  if (created.status !== 201 || !postId) {
    throw new Error(`Load scenario seed creation failed: ${JSON.stringify(created.body)}`);
  }

  const tasks = [];
  for (let index = 0; index < 40; index += 1) {
    const user = users[index % users.length];
    tasks.push(() => getList(user.token));
    tasks.push(() => getDetail(user.token, postId));
  }

  const results = await runBounded(tasks, 8);
  assertNoServerErrors(results, 'load');
  await deletePost(owner.token, postId);
  return summarizeResults(results);
}

async function runCommentConcurrencyScenario(users) {
  const owner = users[0];
  const created = await createPost(owner.token, {
    title: 'Stress comment target',
    content: 'Comment concurrency target.',
    category: 'FREE',
    tags: ['stress', 'comments']
  });
  const postId = created.body?.data?.id;
  if (created.status !== 201 || !postId) {
    throw new Error(`Comment concurrency seed failed: ${JSON.stringify(created.body)}`);
  }

  const commentTasks = [];
  for (let index = 0; index < 24; index += 1) {
    const user = users[index % users.length];
    commentTasks.push(() => createComment(user.token, postId, `stress comment ${index}`));
  }
  const commentResults = await Promise.all(commentTasks.map((task) => task()));
  const parent = await createComment(owner.token, postId, 'reply parent');
  const parentCommentId = parent.body?.data?.id;
  if (parent.status !== 201 || !parentCommentId) {
    throw new Error(`Parent comment creation failed: ${JSON.stringify(parent.body)}`);
  }

  const replyTasks = [];
  for (let index = 0; index < 12; index += 1) {
    const user = users[index % users.length];
    replyTasks.push(() => createComment(user.token, postId, `stress reply ${index}`, parentCommentId));
  }
  const replyResults = await Promise.all(replyTasks.map((task) => task()));
  const tree = await getComments(owner.token, postId);
  if (tree.status !== 200) {
    throw new Error(`Comment tree fetch failed: ${JSON.stringify(tree.body)}`);
  }

  const rootComments = tree.body?.data ?? [];
  const replyParent = rootComments.find((item) => item.id === parentCommentId);
  const summary = summarizeResults([...commentResults, ...replyResults, tree]);
  summary.rootCommentCount = rootComments.length;
  summary.replyCountOnParent = replyParent?.replies?.length ?? 0;

  await deletePost(owner.token, postId);
  return summary;
}

async function runVoteReactionScenario(users) {
  const owner = users[0];
  const created = await createPost(owner.token, {
    title: 'Stress vote reaction target',
    content: 'Vote and reaction concurrency target.',
    category: 'FREE',
    pollQuestion: 'Pick one',
    pollOptions: ['alpha', 'beta'],
    tags: ['stress', 'vote']
  });
  const postId = created.body?.data?.id;
  if (created.status !== 201 || !postId) {
    throw new Error(`Vote/reaction seed failed: ${JSON.stringify(created.body)}`);
  }

  const voteResults = await Promise.all(users.map((user, index) => vote(user.token, postId, index % 2 === 0 ? 'alpha' : 'beta')));
  const reactionResults = await Promise.all(users.map((user) => react(user.token, postId, 'LIKE')));
  const detail = await getDetail(owner.token, postId);
  if (detail.status !== 200) {
    throw new Error(`Vote/reaction detail failed: ${JSON.stringify(detail.body)}`);
  }

  const summary = summarizeResults([...voteResults, ...reactionResults, detail]);
  summary.likeCount = detail.body?.data?.likeCount ?? null;

  await deletePost(owner.token, postId);
  return summary;
}

async function runSpikeScenario(users) {
  const owner = users[0];
  const created = await createPost(owner.token, {
    title: 'Stress spike target',
    content: 'Spike target.',
    category: 'FREE',
    tags: ['stress', 'spike']
  });
  const postId = created.body?.data?.id;
  if (created.status !== 201 || !postId) {
    throw new Error(`Spike seed failed: ${JSON.stringify(created.body)}`);
  }

  const tasks = [];
  for (let index = 0; index < 80; index += 1) {
    const user = users[index % users.length];
    if (index % 4 === 0) {
      tasks.push(() => getList(user.token));
    } else if (index % 4 === 1) {
      tasks.push(() => getDetail(user.token, postId));
    } else if (index % 4 === 2) {
      tasks.push(() => createComment(user.token, postId, `spike comment ${index}`));
    } else {
      tasks.push(() => getComments(user.token, postId));
    }
  }

  const startedAt = performance.now();
  const results = await Promise.all(tasks.map((task) => task()));
  const elapsedMs = performance.now() - startedAt;
  assertNoServerErrors(results, 'spike');

  const summary = summarizeResults(results);
  summary.wallClockMs = Number(elapsedMs.toFixed(2));

  await deletePost(owner.token, postId);
  return summary;
}

async function runSoakShortScenario(users) {
  const owner = users[0];
  const created = await createPost(owner.token, {
    title: 'Stress soak target',
    content: 'Soak target.',
    category: 'FREE',
    tags: ['stress', 'soak']
  });
  const postId = created.body?.data?.id;
  if (created.status !== 201 || !postId) {
    throw new Error(`Soak seed failed: ${JSON.stringify(created.body)}`);
  }

  const deadline = performance.now() + 15000;
  const results = [];

  const worker = async (workerIndex) => {
    let iteration = 0;
    while (performance.now() < deadline) {
      const user = users[(workerIndex + iteration) % users.length];
      const operation = iteration % 5;
      let result;
      if (operation === 0) {
        result = await getList(user.token);
      } else if (operation === 1) {
        result = await getDetail(user.token, postId);
      } else if (operation === 2) {
        result = await getComments(user.token, postId);
      } else if (operation === 3) {
        result = await createComment(user.token, postId, `soak comment ${workerIndex}-${iteration}`);
      } else {
        result = await react(user.token, postId, 'LIKE');
      }
      results.push(result);
      iteration += 1;
      await sleep(50);
    }
  };

  await Promise.all(Array.from({ length: 4 }, (_, index) => worker(index)));
  assertNoServerErrors(results, 'soak-short');

  const summary = summarizeResults(results);
  summary.durationMs = 15000;

  await deletePost(owner.token, postId);
  return summary;
}

async function runErrorMixScenario(users) {
  const owner = users[0];
  const created = await createPost(owner.token, {
    title: 'Stress error mix target',
    content: 'Error mix target.',
    category: 'FREE',
    pollQuestion: 'pick one',
    pollOptions: ['left', 'right'],
    tags: ['stress', 'error-mix']
  });
  const postId = created.body?.data?.id;
  if (created.status !== 201 || !postId) {
    throw new Error(`Error mix seed failed: ${JSON.stringify(created.body)}`);
  }

  const tasks = [];
  for (let index = 0; index < 30; index += 1) {
    const user = users[index % users.length];
    if (index % 3 === 0) {
      tasks.push(() => getList(user.token));
    } else if (index % 3 === 1) {
      tasks.push(() => api(user.token, '/api/community/posts?page=oops'));
    } else {
      tasks.push(() => api(user.token, '/api/community/posts/not-a-uuid'));
    }
  }

  const results = await Promise.all(tasks.map((task) => task()));
  const recovery = await getList(owner.token);
  if (recovery.status !== 200) {
    throw new Error(`Recovery after error mix failed: ${JSON.stringify(recovery.body)}`);
  }

  const summary = summarizeResults([...results, recovery]);
  summary.recoveryStatus = recovery.status;

  await deletePost(owner.token, postId);
  return summary;
}

async function main() {
  const users = await buildUsers();
  const report = {
    executedAt: new Date().toISOString(),
    baseUrl: BASE_URL,
    authEmulatorHost: AUTH_EMULATOR_HOST,
    userCount: users.length,
    scenarios: {}
  };

  report.scenarios.load = await runLoadScenario(users);
  report.scenarios.commentConcurrency = await runCommentConcurrencyScenario(users);
  report.scenarios.voteReactionConcurrency = await runVoteReactionScenario(users);
  report.scenarios.spike = await runSpikeScenario(users);
  report.scenarios.soakShort = await runSoakShortScenario(users);
  report.scenarios.errorMix = await runErrorMixScenario(users);

  await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), 'utf8');
  console.log(JSON.stringify(report, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

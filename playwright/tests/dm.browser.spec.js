const { test, expect } = require('@playwright/test');

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const repoRoot = path.resolve(__dirname, '..', '..');

function parseDotEnv() {
  const envPath = path.join(repoRoot, '.env');
  if (!fs.existsSync(envPath)) {
    return {};
  }
  return Object.fromEntries(
    fs.readFileSync(envPath, 'utf8')
      .split(/\r?\n/)
      .filter((line) => line && !line.trimStart().startsWith('#') && line.includes('='))
      .map((line) => {
        const separatorIndex = line.indexOf('=');
        return [line.slice(0, separatorIndex).trim(), line.slice(separatorIndex + 1).trim()];
      })
  );
}

function findPostgresJar(directory) {
  if (!fs.existsSync(directory)) {
    return null;
  }
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isFile() && /^postgresql-.*\.jar$/.test(entry.name)) {
      return fullPath;
    }
    if (entry.isDirectory()) {
      const found = findPostgresJar(fullPath);
      if (found) {
        return found;
      }
    }
  }
  return null;
}

function cleanupDmTestData() {
  const env = parseDotEnv();
  const gradleCache = path.join(os.homedir(), '.gradle', 'caches', 'modules-2', 'files-2.1', 'org.postgresql', 'postgresql');
  const driverJar = findPostgresJar(gradleCache);
  if (!env.DB_URL || !env.DB_USERNAME || !env.DB_PASSWORD || !driverJar) {
    return;
  }
  const cleanupSource = path.join(os.tmpdir(), 'PogunDmPlaywrightCleanup.java');
  fs.writeFileSync(cleanupSource, `
import java.sql.*;
import java.util.*;

public class PogunDmPlaywrightCleanup {
  public static void main(String[] args) throws Exception {
    List<String> statements = List.of(
      "DELETE FROM reports WHERE target_type = 'NOTICE_CHAT_ROOM' AND target_id IN (SELECT id FROM notice_chat_rooms WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%'))",
      "DELETE FROM notice_chat_read_receipts WHERE room_id IN (SELECT id FROM notice_chat_rooms WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%'))",
      "DELETE FROM notice_chat_room_participant_states WHERE room_id IN (SELECT id FROM notice_chat_rooms WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%'))",
      "DELETE FROM notice_chat_message_images WHERE message_id IN (SELECT id FROM notice_chat_messages WHERE room_id IN (SELECT id FROM notice_chat_rooms WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%')))",
      "UPDATE notice_chat_messages SET reply_to_message_id = NULL WHERE room_id IN (SELECT id FROM notice_chat_rooms WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%'))",
      "DELETE FROM notice_chat_messages WHERE room_id IN (SELECT id FROM notice_chat_rooms WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%'))",
      "DELETE FROM notice_chat_rooms WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%')",
      "DELETE FROM reports WHERE target_type = 'PET_NOTICE' AND target_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%')",
      "DELETE FROM notice_bookmarks WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%')",
      "DELETE FROM pet_notice_images WHERE notice_id IN (SELECT id FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%')",
      "DELETE FROM pet_notices WHERE title LIKE 'playwright-notice%' OR title LIKE '[DM TEST]%'",
      "DELETE FROM user_blocks WHERE blocker_id IN (SELECT id FROM users WHERE email LIKE 'playwright-user%@local.dev') OR blocked_id IN (SELECT id FROM users WHERE email LIKE 'playwright-user%@local.dev')",
      "DELETE FROM user_fcm_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'playwright-user%@local.dev')",
      "DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'playwright-user%@local.dev') OR actor_user_id IN (SELECT id FROM users WHERE email LIKE 'playwright-user%@local.dev')"
    );
    try (Connection conn = DriverManager.getConnection(System.getenv("DB_URL"), System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
      conn.setAutoCommit(false);
      try (Statement stmt = conn.createStatement()) {
        for (String sql : statements) {
          stmt.executeUpdate(sql);
        }
        conn.commit();
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    }
  }
}
`, 'ascii');
  execFileSync('java', ['-cp', driverJar, cleanupSource], {
    env: {
      ...process.env,
      DB_URL: env.DB_URL,
      DB_USERNAME: env.DB_USERNAME,
      DB_PASSWORD: env.DB_PASSWORD
    },
    stdio: 'ignore'
  });
  cleanupGeneratedNoticeChatUploads();
}

function cleanupGeneratedNoticeChatUploads() {
  try {
    const status = execFileSync('git', ['status', '--porcelain', '--', 'uploads/notice-chat/messages'], {
      cwd: repoRoot,
      encoding: 'utf8'
    });
    for (const line of status.split(/\r?\n/)) {
      if (!line.startsWith('?? ')) {
        continue;
      }
      const relativePath = line.slice(3).trim();
      const absolutePath = path.resolve(repoRoot, relativePath);
      const allowedRoot = path.resolve(repoRoot, 'uploads', 'notice-chat', 'messages');
      if (absolutePath.startsWith(allowedRoot)) {
        fs.rmSync(absolutePath, { recursive: true, force: true });
      }
    }
  } catch {
  }
}

test.beforeEach(() => cleanupDmTestData());
test.afterEach(() => cleanupDmTestData());

function loadPlaywrightToken(index) {
  return fs.readFileSync(
    path.join(repoRoot, '.local', `emulator-user${index}-firebase-id-token.txt`),
    'utf8'
  ).trim();
}

function messagePageMessages(response) {
  const data = response.body.data;
  return Array.isArray(data) ? data : data.messages;
}

function createMissingPetPayload(title) {
  return {
    title,
    animalType: 'DOG',
    breed: 'MIX',
    gender: 'MALE',
    description: 'playwright notice',
    missingDate: new Date().toISOString(),
    missingRegion: '서울 강남구',
    missingAddress: '테스트 주소',
    contactPhone: '010-1111-2222',
    status: 'OPEN'
  };
}

async function readJsonSafe(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function api(pathname, token, init = {}) {
  const response = await fetch(`${baseURL}${pathname}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...(init.headers || {})
    }
  });

  return {
    status: response.status,
    body: await readJsonSafe(response)
  };
}

async function multipartApi(pathname, token, formData) {
  const response = await fetch(`${baseURL}${pathname}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  });

  return {
    status: response.status,
    body: await readJsonSafe(response)
  };
}

async function login(token) {
  const response = await fetch(`${baseURL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firebaseIdToken: token })
  });

  return {
    status: response.status,
    body: await readJsonSafe(response)
  };
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function createTinyPngBlob() {
  const base64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0YQAAAAASUVORK5CYII=';
  return new Blob([Buffer.from(base64, 'base64')], { type: 'image/png' });
}

function createTinyGifBlob() {
  return new Blob([Buffer.from('R0lGODlhAQABAPAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw==', 'base64')], { type: 'image/gif' });
}

function createTinyMp4Blob() {
  return new Blob([Buffer.from('fake-mp4-body')], { type: 'video/mp4' });
}

async function waitForTypingEvent(client, senderClient, roomId, destination, timeoutMs = 5000) {
  const startedAt = Date.now();
  const pending = client.waitForMessage(destination, timeoutMs);

  while (Date.now() - startedAt < timeoutMs) {
    senderClient.sendJson('/app/chat/typing', { roomId, typing: true });

    const raceResult = await Promise.race([
      pending.then((payload) => ({ done: true, payload })),
      sleep(250).then(() => ({ done: false }))
    ]);

    if (raceResult.done) {
      return raceResult.payload;
    }
  }

  return pending;
}

async function waitForChatMessage(client, destination, predicate, timeoutMs = 5000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const remainingMs = Math.max(deadline - Date.now(), 1);
    const payload = await client.waitForMessage(destination, remainingMs);

    if (payload && payload.type === 'READ_RECEIPT') {
      continue;
    }
    if (!predicate || predicate(payload)) {
      return payload;
    }
  }

  throw new Error(`Timed out waiting for chat message on ${destination}`);
}

function parseFrame(rawFrame) {
  const [headerSection, ...bodyParts] = rawFrame.split('\n\n');
  const lines = headerSection.split('\n');
  const command = lines[0];
  const headers = {};

  for (const line of lines.slice(1)) {
    if (!line) {
      continue;
    }
    const separatorIndex = line.indexOf(':');
    if (separatorIndex < 0) {
      continue;
    }
    headers[line.slice(0, separatorIndex)] = line.slice(separatorIndex + 1);
  }

  return {
    command,
    headers,
    body: bodyParts.join('\n\n')
  };
}

async function createStompClient(token) {
  const wsURL = baseURL.replace(/^http/, 'ws') + '/ws/chat';

  return await new Promise((resolve, reject) => {
    const socket = new WebSocket(wsURL);
    const queuedMessages = new Map();
    const waitingResolvers = new Map();
    let buffer = '';
    let connected = false;
    let settled = false;

    const fail = (error) => {
      if (settled) {
        return;
      }
      settled = true;
      try {
        socket.close();
      } catch {
      }
      reject(error instanceof Error ? error : new Error(String(error)));
    };

    const pushMessage = (keys, payload) => {
      for (const key of keys.filter(Boolean)) {
        const waiting = waitingResolvers.get(key);
        if (waiting && waiting.length > 0) {
          const next = waiting.shift();
          next.resolve(payload);
          return;
        }
      }

      for (const key of keys.filter(Boolean)) {
        const queued = queuedMessages.get(key) || [];
        queued.push(payload);
        queuedMessages.set(key, queued);
      }
    };

    const waitForMessage = (destination, timeoutMs = 5000) => {
      const queued = queuedMessages.get(destination);
      if (queued && queued.length > 0) {
        return Promise.resolve(queued.shift());
      }

      return new Promise((resolveMessage, rejectMessage) => {
        const timeoutId = setTimeout(() => {
          const waiting = waitingResolvers.get(destination) || [];
          waitingResolvers.set(
            destination,
            waiting.filter((entry) => entry.resolve !== wrappedResolve)
          );
          rejectMessage(new Error(`Timed out waiting for ${destination}`));
        }, timeoutMs);

        const wrappedResolve = (payload) => {
          clearTimeout(timeoutId);
          resolveMessage(payload);
        };

        const waiting = waitingResolvers.get(destination) || [];
        waiting.push({ resolve: wrappedResolve });
        waitingResolvers.set(destination, waiting);
      });
    };

    const sendFrame = (command, headers = {}, body = '') => {
      const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
      socket.send(`${command}\n${headerLines.join('\n')}\n\n${body}\0`);
    };

    const client = {
      subscribe(destination, id) {
        sendFrame('SUBSCRIBE', { id, destination });
      },
      sendJson(destination, payload) {
        sendFrame(
          'SEND',
          {
            destination,
            'content-type': 'application/json'
          },
          JSON.stringify(payload)
        );
      },
      waitForMessage,
      close() {
        try {
          sendFrame('DISCONNECT');
        } catch {
        }
        socket.close();
      }
    };

    const connectTimeout = setTimeout(() => {
      fail(new Error('Timed out waiting for STOMP CONNECTED frame'));
    }, 5000);

    socket.addEventListener('open', () => {
      sendFrame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '0,0',
        Authorization: `Bearer ${token}`
      });
    });

    socket.addEventListener('message', async (event) => {
      const chunk = typeof event.data === 'string' ? event.data : await event.data.text();
      buffer += chunk;

      while (buffer.includes('\0')) {
        const frameEnd = buffer.indexOf('\0');
        const rawFrame = buffer.slice(0, frameEnd);
        buffer = buffer.slice(frameEnd + 1);

        if (!rawFrame.trim()) {
          continue;
        }

        const frame = parseFrame(rawFrame);

        if (frame.command === 'CONNECTED') {
          if (!settled) {
            clearTimeout(connectTimeout);
            connected = true;
            settled = true;
            resolve(client);
          }
          continue;
        }

        if (frame.command === 'MESSAGE') {
          let payload = frame.body;
          try {
            payload = JSON.parse(frame.body);
          } catch {
          }
          pushMessage([frame.headers.subscription, frame.headers.destination], payload);
          continue;
        }

        if (frame.command === 'ERROR') {
          clearTimeout(connectTimeout);
          fail(new Error(frame.headers.message || frame.body || 'Received STOMP ERROR frame'));
        }
      }
    });

    socket.addEventListener('error', () => {
      clearTimeout(connectTimeout);
      fail(new Error('WebSocket error'));
    });

    socket.addEventListener('close', (event) => {
      clearTimeout(connectTimeout);
      if (!connected) {
        fail(new Error(`Socket closed before CONNECTED (${event.code})`));
      }
    });
  });
}

test('dm flow covers notice-based 1:1 room reuse, room detail, typing, realtime send, image send, reply, preview, and read state', async () => {
  test.slow();

  const userOneToken = loadPlaywrightToken(1);
  const userTwoToken = loadPlaywrightToken(2);
  const userThreeToken = loadPlaywrightToken(3);

  expect(userOneToken).toBeTruthy();
  expect(userTwoToken).toBeTruthy();

  const userOneLogin = await login(userOneToken);
  const userTwoLogin = await login(userTwoToken);
  const userThreeLogin = await login(userThreeToken);

  expect(userOneLogin.status).toBe(200);
  expect(userTwoLogin.status).toBe(200);
  expect(userThreeLogin.status).toBe(200);
  const userOneUserId = userOneLogin.body?.data?.id;
  const userTwoUserId = userTwoLogin.body?.data?.id;
  const userThreeUserId = userThreeLogin.body?.data?.id;
  expect(userOneUserId).toBeTruthy();
  expect(userTwoUserId).toBeTruthy();
  expect(userThreeUserId).toBeTruthy();

  const noticeOneCreate = await api('/api/missing-pets', userOneToken, {
    method: 'POST',
    body: JSON.stringify(createMissingPetPayload('playwright-notice-one'))
  });
  const noticeTwoCreate = await api('/api/missing-pets', userOneToken, {
    method: 'POST',
    body: JSON.stringify(createMissingPetPayload('playwright-notice-two'))
  });

  expect(noticeOneCreate.status).toBe(201);
  expect(noticeTwoCreate.status).toBe(201);

  const noticeOneId = noticeOneCreate.body?.data?.id;
  const noticeTwoId = noticeTwoCreate.body?.data?.id;
  expect(noticeOneId).toBeTruthy();
  expect(noticeTwoId).toBeTruthy();

  const roomCreate = await api(`/api/chat/rooms/notice/${noticeOneId}`, userTwoToken, { method: 'POST' });
  const roomReuse = await api(`/api/chat/rooms/notice/${noticeOneId}`, userTwoToken, { method: 'POST' });
  const roomForSecondNotice = await api(`/api/chat/rooms/notice/${noticeTwoId}`, userTwoToken, { method: 'POST' });
  const selfNoticeAttempt = await api(`/api/chat/rooms/notice/${noticeOneId}`, userOneToken, { method: 'POST' });

  const roomId = roomCreate.body?.data?.roomId;
  const secondRoomId = roomForSecondNotice.body?.data?.roomId;

  expect(roomCreate.status).toBe(201);
  expect(roomReuse.status).toBe(200);
  expect(roomForSecondNotice.status).toBe(201);
  expect(selfNoticeAttempt.status).toBe(409);
  expect(roomId).toBeTruthy();
  expect(secondRoomId).toBeTruthy();
  expect(roomReuse.body.data.roomId).toBe(roomId);
  expect(secondRoomId).not.toBe(roomId);
  expect(roomCreate.body.data.noticeId).toBe(noticeOneId);
  expect(roomCreate.body.data.noticeTitle).toBe('playwright-notice-one');
  expect(roomCreate.body.data.opponentUserId).toBe(userOneUserId);
  expect(roomCreate.body.data.lastMessageType).toBeNull();

  const roomSettings = await api(`/api/chat/rooms/${roomId}/settings`, userTwoToken, {
    method: 'PATCH',
    body: JSON.stringify({
      customRoomName: 'playwright custom room'
    })
  });
  expect(roomSettings.status, JSON.stringify(roomSettings.body)).toBe(200);
  expect(roomSettings.body.data.customRoomName).toBe('playwright custom room');
  expect(roomSettings.body.data.displayRoomName).toBe('playwright custom room');
  expect(roomSettings.body.data.displayThumbnailUrl).toBeNull();

  const roomThumbnailForm = new FormData();
  roomThumbnailForm.append('image', createTinyPngBlob(), 'room-thumbnail.png');
  const roomThumbnailSettings = await multipartApi(`/api/chat/rooms/${roomId}/settings/thumbnail`, userTwoToken, roomThumbnailForm);
  expect(roomThumbnailSettings.status, JSON.stringify(roomThumbnailSettings.body)).toBe(200);
  expect(roomThumbnailSettings.body.data.customThumbnailUrl).toContain('/uploads/notice-chat/rooms/');
  expect(roomThumbnailSettings.body.data.displayThumbnailUrl).toContain('/uploads/notice-chat/rooms/');

  const roomSettingsClear = await api(`/api/chat/rooms/${roomId}/settings`, userTwoToken, {
    method: 'PATCH',
    body: JSON.stringify({ clearCustomRoomName: true, clearCustomThumbnailUrl: true })
  });
  expect(roomSettingsClear.status, JSON.stringify(roomSettingsClear.body)).toBe(200);
  expect(roomSettingsClear.body.data.customRoomName).toBeNull();
  expect(roomSettingsClear.body.data.displayRoomName).toContain('playwright-notice-one');

  const roomDetailBeforeMessage = await api(`/api/chat/rooms/${roomId}`, userTwoToken);
  expect(roomDetailBeforeMessage.status).toBe(200);
  expect(roomDetailBeforeMessage.body.data.roomId).toBe(roomId);
  expect(roomDetailBeforeMessage.body.data.lastMessagePreview).toBeNull();

  const userOneRoomsBeforeMessage = await api('/api/chat/rooms', userOneToken);
  expect(userOneRoomsBeforeMessage.status).toBe(200);
  expect(userOneRoomsBeforeMessage.body.data).toHaveLength(2);
  expect(userOneRoomsBeforeMessage.body.data.map((room) => room.noticeId)).toEqual(expect.arrayContaining([noticeOneId, noticeTwoId]));
  expect(userOneRoomsBeforeMessage.body.data.find((room) => room.noticeId === noticeOneId).lastMessagePreview).toBeNull();

  const userOneClient = await createStompClient(userOneToken);
  const userTwoClient = await createStompClient(userTwoToken);

  const userOneRoomDestination = `/topic/chat/users/${userOneUserId}/rooms/${roomId}`;
  const userOneTypingDestination = `/topic/chat/users/${userOneUserId}/rooms/${roomId}/typing`;
  const userTwoRoomDestination = `/topic/chat/users/${userTwoUserId}/rooms/${roomId}`;
  const userTwoTypingDestination = `/topic/chat/users/${userTwoUserId}/rooms/${roomId}/typing`;
  const userOneRoomSubscriptionId = 'user1-room';
  const userOneTypingSubscriptionId = 'user1-typing';
  const userTwoRoomSubscriptionId = 'user2-room';
  const userTwoTypingSubscriptionId = 'user2-typing';
  userOneClient.subscribe(userOneRoomDestination, userOneRoomSubscriptionId);
  userOneClient.subscribe(userOneTypingDestination, userOneTypingSubscriptionId);
  userTwoClient.subscribe(userTwoRoomDestination, userTwoRoomSubscriptionId);
  userTwoClient.subscribe(userTwoTypingDestination, userTwoTypingSubscriptionId);
  await sleep(500);

  const userOneTypingEvent = await waitForTypingEvent(
    userOneClient,
    userTwoClient,
    roomId,
    userOneTypingSubscriptionId,
    3000
  );
  expect(userOneTypingEvent.roomId).toBe(roomId);
  expect(userOneTypingEvent.senderUserId).toBe(userTwoUserId);
  expect(userOneTypingEvent.isTyping).toBe(true);

  const userTwoMessageText = '유저 2가 공고 작성자에게 보낸 첫 번째 실시간 제보 메시지입니다.';
  const userOneRealtimeMessagePromise = waitForChatMessage(
    userOneClient,
    userOneRoomSubscriptionId,
    (payload) => payload.senderUserId === userTwoUserId && payload.message === userTwoMessageText
  );
  const userTwoRealtimeEchoPromise = waitForChatMessage(
    userTwoClient,
    userTwoRoomSubscriptionId,
    (payload) => payload.senderUserId === userTwoUserId && payload.message === userTwoMessageText
  );
  userTwoClient.sendJson('/app/chat/send', { roomId, message: userTwoMessageText });
  const userOneRealtimeMessage = await userOneRealtimeMessagePromise;
  const userTwoRealtimeEcho = await userTwoRealtimeEchoPromise;

  expect(userOneRealtimeMessage.roomId).toBe(roomId);
  expect(userOneRealtimeMessage.senderUserId).toBe(userTwoUserId);
  expect(userOneRealtimeMessage.message).toBe(userTwoMessageText);
  expect(userOneRealtimeMessage.messageType).toBe('TEXT');
  expect(userOneRealtimeMessage.mine).toBe(false);
  expect(userTwoRealtimeEcho.roomId).toBe(roomId);
  expect(userTwoRealtimeEcho.senderUserId).toBe(userTwoUserId);
  expect(userTwoRealtimeEcho.message).toBe(userTwoMessageText);
  expect(userTwoRealtimeEcho.messageType).toBe('TEXT');
  expect(userTwoRealtimeEcho.mine).toBe(true);

  const userOneRoomsAfterUserTwoMessage = await api('/api/chat/rooms', userOneToken);
  const noticeOneRoomForUserOne = userOneRoomsAfterUserTwoMessage.body.data.find((room) => room.noticeId === noticeOneId);
  expect(userOneRoomsAfterUserTwoMessage.status).toBe(200);
  expect(noticeOneRoomForUserOne.lastMessagePreview).toBe(userTwoMessageText);
  expect(noticeOneRoomForUserOne.lastMessageType).toBe('TEXT');
  expect(noticeOneRoomForUserOne.unreadCount).toBe(1);

  const userOneMessagesAfterRead = await api(`/api/chat/rooms/${roomId}/messages`, userOneToken);
  expect(userOneMessagesAfterRead.status, JSON.stringify(userOneMessagesAfterRead.body)).toBe(200);
  const userOneMessagesAfterReadList = messagePageMessages(userOneMessagesAfterRead);
  expect(userOneMessagesAfterReadList).toHaveLength(1);
  expect(userOneMessagesAfterReadList[0].message).toBe(userTwoMessageText);
  expect(userOneMessagesAfterReadList[0].isRead).toBe(true);
  expect(userOneMessagesAfterReadList[0].mine).toBe(false);

  const userOneRoomsAfterRead = await api('/api/chat/rooms', userOneToken);
  expect(userOneRoomsAfterRead.status).toBe(200);
  expect(userOneRoomsAfterRead.body.data.find((room) => room.noticeId === noticeOneId).unreadCount).toBe(0);

  const userOneReplyText = '유저 1이 공고 채팅에서 확인 후 남긴 답장입니다.';
  const userTwoRealtimeReplyPromise = waitForChatMessage(
    userTwoClient,
    userTwoRoomSubscriptionId,
    (payload) => payload.senderUserId === userOneUserId && payload.message === userOneReplyText
  );
  userOneClient.sendJson('/app/chat/send', { roomId, message: userOneReplyText, replyToMessageId: userOneRealtimeMessage.id });
  const userTwoRealtimeReply = await userTwoRealtimeReplyPromise;

  expect(userTwoRealtimeReply.roomId).toBe(roomId);
  expect(userTwoRealtimeReply.senderUserId).toBe(userOneUserId);
  expect(userTwoRealtimeReply.message).toBe(userOneReplyText);
  expect(userTwoRealtimeReply.reply.messageId).toBe(userOneRealtimeMessage.id);
  expect(userTwoRealtimeReply.reply.preview).toBe(userTwoMessageText);
  expect(userTwoRealtimeReply.mine).toBe(false);

  const userTwoRoomsAfterUserOneReply = await api('/api/chat/rooms', userTwoToken);
  const noticeOneRoomForUserTwo = userTwoRoomsAfterUserOneReply.body.data.find((room) => room.noticeId === noticeOneId);
  expect(userTwoRoomsAfterUserOneReply.status).toBe(200);
  expect(noticeOneRoomForUserTwo.lastMessagePreview).toBe(userOneReplyText);
  expect(noticeOneRoomForUserTwo.lastMessageType).toBe('TEXT');
  expect(noticeOneRoomForUserTwo.unreadCount).toBe(1);

  const editedUserOneReplyText = '유저 1이 수정한 답장입니다.';
  const userTwoRealtimeUpdatePromise = waitForChatMessage(
    userTwoClient,
    userTwoRoomSubscriptionId,
    (payload) => payload.type === 'MESSAGE_UPDATED' && payload.message?.id === userTwoRealtimeReply.id
  );
  const updateReply = await api(`/api/chat/rooms/${roomId}/messages/${userTwoRealtimeReply.id}`, userOneToken, {
    method: 'PATCH',
    body: JSON.stringify({ message: editedUserOneReplyText })
  });
  expect(updateReply.status, JSON.stringify(updateReply.body)).toBe(200);
  const userTwoRealtimeUpdate = await userTwoRealtimeUpdatePromise;
  expect(userTwoRealtimeUpdate.message.message).toBe(editedUserOneReplyText);

  const userTwoMessagesAfterRead = await api(`/api/chat/rooms/${roomId}/messages`, userTwoToken);
  expect(userTwoMessagesAfterRead.status).toBe(200);
  const userTwoMessagesAfterReadList = messagePageMessages(userTwoMessagesAfterRead);
  expect(userTwoMessagesAfterReadList).toHaveLength(2);
  expect(userTwoMessagesAfterReadList[1].message).toBe(editedUserOneReplyText);
  expect(userTwoMessagesAfterReadList[1].reply.messageId).toBe(userOneRealtimeMessage.id);
  expect(userTwoMessagesAfterReadList[1].isRead).toBe(true);
  expect(userTwoMessagesAfterReadList[1].mine).toBe(false);

  const imageRealtimePromise = waitForChatMessage(
    userTwoClient,
    userTwoRoomSubscriptionId,
    (payload) => payload.senderUserId === userOneUserId && payload.messageType === 'IMAGE'
  );
  const imageForm = new FormData();
  const imageMessageText = '이미지와 같이 보내는 설명입니다';
  imageForm.append('images', createTinyPngBlob(), 'tiny.png');
  imageForm.append('images', createTinyPngBlob(), 'tiny-two.png');
  imageForm.append('message', imageMessageText);
  imageForm.append('replyToMessageId', userTwoRealtimeReply.id);
  const imageUpload = await multipartApi(`/api/chat/rooms/${roomId}/messages/images`, userOneToken, imageForm);
  expect(imageUpload.status).toBe(201);
  const imageRealtimeMessage = await imageRealtimePromise;
  expect(imageRealtimeMessage.messageType).toBe('IMAGE');
  expect(imageRealtimeMessage.message).toBe(imageMessageText);
  expect(imageRealtimeMessage.images).toHaveLength(2);
  expect(imageRealtimeMessage.images[0].imageUrl.endsWith('.webp')).toBe(true);
  expect(imageRealtimeMessage.reply.messageId).toBe(userTwoRealtimeReply.id);
  const authenticatedImageFetch = await fetch(`${baseURL}${imageRealtimeMessage.images[0].imageUrl}`, {
    headers: { Authorization: `Bearer ${userTwoToken}` }
  });
  expect(authenticatedImageFetch.status).toBe(200);
  expect(authenticatedImageFetch.headers.get('content-type')).toContain('image');
  const forbiddenImageFetch = await fetch(`${baseURL}${imageRealtimeMessage.images[0].imageUrl}`, {
    headers: { Authorization: `Bearer ${userThreeToken}` }
  });
  expect(forbiddenImageFetch.status).toBe(403);

  const userTwoRoomsAfterImage = await api('/api/chat/rooms', userTwoToken);
  expect(userTwoRoomsAfterImage.status).toBe(200);
  expect(userTwoRoomsAfterImage.body.data.find((room) => room.noticeId === noticeOneId).lastMessagePreview).toBe('사진을 보냈습니다');
  expect(userTwoRoomsAfterImage.body.data.find((room) => room.noticeId === noticeOneId).lastMessageType).toBe('IMAGE');

  const roomDetailAfterImage = await api(`/api/chat/rooms/${roomId}`, userTwoToken);
  expect(roomDetailAfterImage.status).toBe(200);
  expect(roomDetailAfterImage.body.data.lastMessagePreview).toBe('사진을 보냈습니다');
  expect(roomDetailAfterImage.body.data.lastMessageType).toBe('IMAGE');

  const userTwoMessagesAfterImageRead = await api(`/api/chat/rooms/${roomId}/messages`, userTwoToken);
  expect(userTwoMessagesAfterImageRead.status).toBe(200);
  const userTwoMessagesAfterImageReadList = messagePageMessages(userTwoMessagesAfterImageRead);
  expect(userTwoMessagesAfterImageReadList.at(-1).messageType).toBe('IMAGE');
  expect(userTwoMessagesAfterImageReadList.at(-1).message).toBe(imageMessageText);
  expect(userTwoMessagesAfterImageReadList.at(-1).isRead).toBe(true);

  const userTwoRealtimeDeletePromise = waitForChatMessage(
    userTwoClient,
    userTwoRoomSubscriptionId,
    (payload) => payload.type === 'MESSAGE_DELETED' && payload.messageId === imageRealtimeMessage.id
  );
  const deleteImageMessage = await api(`/api/chat/rooms/${roomId}/messages/${imageRealtimeMessage.id}`, userOneToken, {
    method: 'DELETE'
  });
  expect(deleteImageMessage.status, JSON.stringify(deleteImageMessage.body)).toBe(200);
  const userTwoRealtimeDelete = await userTwoRealtimeDeletePromise;
  expect(userTwoRealtimeDelete.messageId).toBe(imageRealtimeMessage.id);

  const userTwoMessagesAfterDelete = await api(`/api/chat/rooms/${roomId}/messages`, userTwoToken);
  expect(userTwoMessagesAfterDelete.status).toBe(200);
  const userTwoMessagesAfterDeleteList = messagePageMessages(userTwoMessagesAfterDelete);
  expect(userTwoMessagesAfterDeleteList.map((message) => message.id)).not.toContain(imageRealtimeMessage.id);
  expect(userTwoMessagesAfterDeleteList.at(-1).message).toBe(editedUserOneReplyText);

  const roomDetailAfterDelete = await api(`/api/chat/rooms/${roomId}`, userTwoToken);
  expect(roomDetailAfterDelete.status).toBe(200);
  expect(roomDetailAfterDelete.body.data.lastMessagePreview).toBe(editedUserOneReplyText);
  expect(roomDetailAfterDelete.body.data.lastMessageType).toBe('TEXT');

  const userTwoRoomsAfterRead = await api('/api/chat/rooms', userTwoToken);
  expect(userTwoRoomsAfterRead.status).toBe(200);
  expect(userTwoRoomsAfterRead.body.data.find((room) => room.noticeId === noticeOneId).unreadCount).toBe(0);

  const gifForm = new FormData();
  gifForm.append('images', createTinyGifBlob(), 'tiny.gif');
  const gifUpload = await multipartApi(`/api/chat/rooms/${roomId}/messages/images`, userOneToken, gifForm);
  expect(gifUpload.status, JSON.stringify(gifUpload.body)).toBe(201);
  expect(gifUpload.body.data.messageType).toBe('IMAGE');
  expect(gifUpload.body.data.images[0].imageUrl.endsWith('.gif')).toBe(true);

  const mp4Form = new FormData();
  mp4Form.append('images', createTinyMp4Blob(), 'tiny.mp4');
  const mp4Upload = await multipartApi(`/api/chat/rooms/${roomId}/messages/images`, userOneToken, mp4Form);
  expect(mp4Upload.status, JSON.stringify(mp4Upload.body)).toBe(201);
  expect(mp4Upload.body.data.messageType).toBe('VIDEO');
  expect(mp4Upload.body.data.images[0].imageUrl.endsWith('.mp4')).toBe(true);

  const oversizedForm = new FormData();
  oversizedForm.append('images', new Blob([Buffer.alloc(30 * 1024 * 1024 + 1)], { type: 'video/mp4' }), 'too-large.mp4');
  const oversizedUpload = await multipartApi(`/api/chat/rooms/${roomId}/messages/images`, userOneToken, oversizedForm);
  expect(oversizedUpload.status).toBe(400);

  userOneClient.close();
  userTwoClient.close();
});

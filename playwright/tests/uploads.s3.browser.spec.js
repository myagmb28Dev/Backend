const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8080';
const s3BaseUrl = (process.env.AWS_S3_PUBLIC_BASE_URL || 'https://2026capstone-ktw.s3.ap-northeast-2.amazonaws.com').replace(/\/$/, '');
const user1 = { email: 'playwright-user1@local.dev', password: 'Test1234!' };
const user2 = { email: 'playwright-user2@local.dev', password: 'Test1234!' };
const pngBuffer = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Zf1cAAAAASUVORK5CYII=',
  'base64'
);
const invalidBmpBuffer = Buffer.from('424d3e000000000000003600000028000000010000000100000001001800000000000800000000000000000000000000000000000000', 'hex');
const largeGifBuffer = Buffer.concat([
  Buffer.from('474946383961', 'hex'),
  Buffer.alloc(6 * 1024 * 1024, 0)
]);

function jsonBlob(payload) {
  return new Blob([JSON.stringify(payload)], { type: 'application/json' });
}

function imageFile(buffer, name, type) {
  return new File([buffer], name, { type });
}

async function readJson(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function ensureFirebaseToken(identity, options = {}) {
  const baseUrl = options.baseUrl || process.env.AUTH_EMULATOR_BASE_URL || 'http://127.0.0.1:9099';
  const apiKey = options.apiKey || process.env.AUTH_EMULATOR_API_KEY || 'fake-api-key';
  const payload = {
    email: identity.email,
    password: identity.password,
    returnSecureToken: true
  };

  const signUp = await fetch(`${baseUrl}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!signUp.ok) {
    const signUpBody = await readJson(signUp);
    const message = typeof signUpBody === 'string' ? signUpBody : signUpBody?.error?.message;
    if (!String(message).includes('EMAIL_EXISTS')) {
      throw new Error(`Auth emulator sign-up failed: ${JSON.stringify(signUpBody)}`);
    }
  }

  const signIn = await fetch(`${baseUrl}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  const signInBody = await readJson(signIn);
  if (!signIn.ok || !signInBody?.idToken) {
    throw new Error(`Auth emulator sign-in failed: ${JSON.stringify(signInBody)}`);
  }
  return signInBody.idToken;
}

function expectS3Url(url) {
  expect(url).toBeTruthy();
  expect(url.startsWith(`${s3BaseUrl}/uploads/`)).toBeTruthy();
}

async function fetchJson(path, init = {}) {
  const response = await fetch(`${baseURL}${path}`, init);
  return {
    status: response.status,
    body: await readJson(response)
  };
}

async function login(identity) {
  const token = await ensureFirebaseToken(identity);
  const loginResponse = await fetchJson('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ firebaseIdToken: token })
  });
  expect(loginResponse.status).toBe(200);
  return token;
}

async function authorizedJson(path, token, init = {}) {
  const headers = {
    Authorization: `Bearer ${token}`,
    ...(init.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
    ...(init.headers || {})
  };
  return fetchJson(path, {
    ...init,
    headers
  });
}

async function authorizedMultipart(path, token, formData, method = 'POST') {
  return fetchJson(path, {
    method,
    headers: {
      Authorization: `Bearer ${token}`
    },
    body: formData
  });
}

async function assertPublicObject(url) {
  const response = await fetch(url);
  expect(response.status).toBe(200);
}

test.describe.serial('S3 upload smoke', () => {
  test('stores profile, community, and missing-pet uploads on S3', async () => {
    const token = await login(user1);
    const suffix = Date.now().toString();

    const profileForm = new FormData();
    profileForm.append('request', jsonBlob({ nickname: `upload-user-${suffix}` }));
    profileForm.append('profileImage', imageFile(largeGifBuffer, 'large.gif', 'image/gif'));
    const profileResponse = await authorizedMultipart('/api/users/me', token, profileForm, 'PATCH');
    expect(profileResponse.status).toBe(200);
    expectS3Url(profileResponse.body.data.profileImageUrl);
    await assertPublicObject(profileResponse.body.data.profileImageUrl);

    const invalidProfileForm = new FormData();
    invalidProfileForm.append('request', jsonBlob({ nickname: `invalid-user-${suffix}` }));
    invalidProfileForm.append('profileImage', imageFile(invalidBmpBuffer, 'invalid.bmp', 'image/bmp'));
    const invalidProfileResponse = await authorizedMultipart('/api/users/me', token, invalidProfileForm, 'PATCH');
    expect(invalidProfileResponse.status).toBe(400);
    expect(invalidProfileResponse.body.error.code).toBe('INVALID_IMAGE');

    const communityCreateForm = new FormData();
    communityCreateForm.append('request', JSON.stringify({
      title: `S3 community ${suffix}`,
      content: 'S3 multipart create smoke',
      category: 'FREE',
      tags: ['s3', 'playwright']
    }));
    communityCreateForm.append('images', imageFile(pngBuffer, 'community.png', 'image/png'));
    const communityCreate = await authorizedMultipart('/api/community/posts', token, communityCreateForm);
    expect(communityCreate.status).toBe(201);
    const communityId = communityCreate.body.data.id;
    expect(communityId).toBeTruthy();

    const communityDetail = await authorizedJson(`/api/community/posts/${communityId}`, token);
    expect(communityDetail.status).toBe(200);
    expect(Array.isArray(communityDetail.body.data.imageUrls)).toBe(true);
    expect(communityDetail.body.data.imageUrls.length).toBeGreaterThan(0);
    expectS3Url(communityDetail.body.data.imageUrls[0]);
    await assertPublicObject(communityDetail.body.data.imageUrls[0]);

    const communityUpdateForm = new FormData();
    communityUpdateForm.append('request', JSON.stringify({
      title: `S3 community updated ${suffix}`,
      content: 'S3 multipart update smoke'
    }));
    communityUpdateForm.append('images', imageFile(pngBuffer, 'community-update.png', 'image/png'));
    const communityUpdate = await authorizedMultipart(`/api/community/posts/${communityId}`, token, communityUpdateForm, 'PATCH');
    expect(communityUpdate.status).toBe(200);

    const missingPetCreateForm = new FormData();
    missingPetCreateForm.append('request', jsonBlob({
      title: `S3 notice ${suffix}`,
      animalType: 'DOG',
      breed: 'MALTESE',
      gender: 'MALE',
      age: 3,
      color: 'WHITE',
      description: 'S3 smoke notice',
      missingDate: '2026-04-20T10:00:00Z',
      missingRegion: '서울 강남구',
      missingAddress: '역삼역 인근',
      rewardAmount: 100000,
      contactPhone: '010-1111-2222',
      status: 'OPEN'
    }));
    missingPetCreateForm.append('images', imageFile(pngBuffer, 'notice.png', 'image/png'));
    const missingPetCreate = await authorizedMultipart('/api/missing-pets', token, missingPetCreateForm);
    expect(missingPetCreate.status).toBe(201);
    const missingPetId = missingPetCreate.body.data.id;
    expect(missingPetId).toBeTruthy();
    expect(Array.isArray(missingPetCreate.body.data.imageUrls)).toBe(true);
    expect(missingPetCreate.body.data.imageUrls.length).toBeGreaterThan(0);
    expectS3Url(missingPetCreate.body.data.imageUrls[0]);
    await assertPublicObject(missingPetCreate.body.data.imageUrls[0]);

    const missingPetUpdateForm = new FormData();
    missingPetUpdateForm.append('request', jsonBlob({
      title: `S3 notice updated ${suffix}`,
      description: 'updated on S3 smoke'
    }));
    missingPetUpdateForm.append('images', imageFile(pngBuffer, 'notice-update.png', 'image/png'));
    const missingPetUpdate = await authorizedMultipart(`/api/missing-pets/${missingPetId}`, token, missingPetUpdateForm, 'PATCH');
    expect(missingPetUpdate.status).toBe(200);
    expect(Array.isArray(missingPetUpdate.body.data.imageUrls)).toBe(true);
    expect(missingPetUpdate.body.data.imageUrls.length).toBeGreaterThan(0);
    expectS3Url(missingPetUpdate.body.data.imageUrls[0]);
    await assertPublicObject(missingPetUpdate.body.data.imageUrls[0]);
  });

  test('stores notice chat image variants on S3 for two users', async () => {
    const ownerToken = await login(user1);
    const guestToken = await login(user2);
    const suffix = `${Date.now()}-chat`;

    const noticeForm = new FormData();
    noticeForm.append('request', jsonBlob({
      title: `S3 chat notice ${suffix}`,
      animalType: 'DOG',
      breed: 'POODLE',
      gender: 'FEMALE',
      age: 4,
      color: 'BLACK',
      description: 'chat upload notice',
      missingDate: '2026-04-20T10:00:00Z',
      missingRegion: '서울 송파구',
      missingAddress: '잠실역 인근',
      rewardAmount: 50000,
      contactPhone: '010-3333-4444',
      status: 'OPEN'
    }));
    noticeForm.append('images', imageFile(pngBuffer, 'chat-notice.png', 'image/png'));
    const noticeCreate = await authorizedMultipart('/api/missing-pets', ownerToken, noticeForm);
    expect(noticeCreate.status).toBe(201);
    const noticeId = noticeCreate.body.data.id;

    const roomResponse = await authorizedJson(`/api/chat/rooms/notice/${noticeId}`, guestToken, { method: 'POST' });
    expect([200, 201]).toContain(roomResponse.status);
    const roomId = roomResponse.body.data.roomId;
    expect(roomId).toBeTruthy();

    const chatForm = new FormData();
    chatForm.append('images', imageFile(pngBuffer, 'chat.png', 'image/png'));
    chatForm.append('message', 'S3 image message');
    const chatResponse = await authorizedMultipart(`/api/chat/rooms/${roomId}/messages/images`, guestToken, chatForm);
    expect(chatResponse.status).toBe(201);
    expect(Array.isArray(chatResponse.body.data.images)).toBe(true);
    expect(chatResponse.body.data.images.length).toBeGreaterThan(0);

    const image = chatResponse.body.data.images[0];
    expectS3Url(image.imageUrl);
    expectS3Url(image.originalUrl);
    expectS3Url(image.webpUrl);
    expectS3Url(image.mediumUrl);
    expectS3Url(image.thumbnailUrl);
    expectS3Url(image.previewUrl);
    await assertPublicObject(image.imageUrl);
  });
});

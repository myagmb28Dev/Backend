const { test, expect } = require('@playwright/test');
const baseURL = process.env.BASE_URL || 'http://localhost:8081';

function tinyPngFile() {
  const base64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0YQAAAAASUVORK5CYII=';
  return {
    name: 'notice-image.png',
    mimeType: 'image/png',
    buffer: Buffer.from(base64, 'base64')
  };
}

test('notice-flow creates notice then redirects to dm-flow with noticeId', async ({ page }) => {
  const title = `playwright-notice-flow-${Date.now()}`;

  await page.goto(`${baseURL}/full_compact/pages/login-flow.html`);
  await page.click('#user1LoginButton');
  await expect(page.locator('#status')).toContainText('로그인 완료', { timeout: 20000 });

  await page.click('#goNoticeButton');
  await expect(page).toHaveURL(/\/notice-flow\.html/);

  await page.fill('#title', title);
  await page.fill('#animalType', 'DOG');
  await page.fill('#missingRegion', '서울 강남구');
  await page.setInputFiles('#images', tinyPngFile());

  await page.click('#createNoticeButton');
  await expect(page).toHaveURL(/\/dm-flow\.html\?noticeId=/, { timeout: 30000 });
  await expect(page.locator('#status')).toContainText('로그인 완료', { timeout: 20000 });
});

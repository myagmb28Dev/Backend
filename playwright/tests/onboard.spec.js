const { test, expect } = require('@playwright/test');
const baseURL = process.env.BASE_URL || 'http://localhost:8081';

test('login-flow authenticates and redirects with shared role', async ({ page }) => {
  await page.goto(`${baseURL}/login-flow.html?redirect=/dm-flow.html`);

  await page.click('#user1LoginButton');
  await expect(page.locator('#status')).toContainText('로그인 완료', { timeout: 20000 });

  await page.click('#goRedirectButton');
  await expect(page).toHaveURL(/\/dm-flow\.html/);
  await expect(page.locator('#currentUserState')).toContainText('유저 1', { timeout: 20000 });
});

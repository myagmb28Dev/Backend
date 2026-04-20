const { test, expect } = require('@playwright/test');

test('login-flow authenticates and redirects with shared role', async ({ page }) => {
  await page.goto('http://localhost:8080/login-flow.html?redirect=/dm-test.html');

  await page.click('#user1LoginButton');
  await expect(page.locator('#status')).toContainText('로그인 완료', { timeout: 20000 });

  await page.click('#goRedirectButton');
  await expect(page).toHaveURL(/\/dm-test\.html/);
  await expect(page.locator('#currentUserState')).toContainText('유저 1', { timeout: 20000 });
});

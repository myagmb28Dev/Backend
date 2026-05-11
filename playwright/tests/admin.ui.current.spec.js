const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';

test('admin-flow current UI has csv section + user context menu flow', async ({ page }) => {
  await page.goto(`${baseURL}/full_compact/pages/admin-flow.html`, { waitUntil: 'domcontentloaded' });
  await page.evaluate(() => {
    const panel = document.querySelector('#adminPanel');
    if (panel) panel.style.display = 'block';
  });

  await expect(page.locator('h2:has-text("CSV 다운로드")')).toBeVisible();
  await expect(page.locator('#usersCsvButton')).toBeVisible();
  await expect(page.locator('#noticesCsvButton')).toBeVisible();
  await expect(page.locator('#reportsCsvButton')).toBeVisible();
  await expect(page.locator('#notificationsCsvButton')).toBeVisible();
  await expect(page.locator('#auditCsvButton')).toBeVisible();
  await expect(page.locator('h2:has-text("사용자 조회")')).toBeVisible();
  await expect(page.locator('text=행을 우클릭하면 관리자 승격/권한 조회 메뉴가 열립니다.')).toBeVisible();

  const menu = page.locator('#userRowContextMenu');
  await expect(menu).toBeAttached();
  await expect(menu.locator('button[data-action="promote"]')).toHaveText('관리자 승격');
  await expect(menu.locator('button[data-action="permissions"]')).toHaveText('관리자 권한 조회');

  await expect(page.locator('text=관리자로 승격할 이메일')).toHaveCount(0);
});

const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';

test.describe('full compact flow', () => {
  test('loads Full_Compact and switches all tabs with iframe content', async ({ page }) => {
    await page.goto(`${baseURL}/Full_Compact.html`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('text=Pogun Full Compact')).toBeVisible();

    const tabs = [
      { key: 'admin', expectTitle: /Admin Console|Pogun Admin Console|Admin Flow/i },
      { key: 'login', expectTitle: /Login Flow/i },
      { key: 'notice', expectTitle: /Notice Flow|Login Flow/i },
      { key: 'dm', expectTitle: /DM Flow|Login Flow/i },
      { key: 'notification', expectTitle: /Notification Flow|Login Flow/i },
      { key: 'shelter', expectTitle: /Shelter Flow|Login Flow/i }
    ];

    for (const item of tabs) {
      await page.click(`button[data-view="${item.key}"]`);
      await expect(page.locator(`button[data-view="${item.key}"]`)).toHaveClass(/active/);
      await expect(page.locator(`#view-${item.key}`)).toHaveClass(/active/);
      const frame = page.frameLocator(`#view-${item.key} iframe`);
      await expect(frame.locator('body')).toBeVisible({ timeout: 20000 });
      const title = await frame.locator('title').textContent().catch(() => '');
      expect(String(title || '')).toMatch(item.expectTitle);
    }
  });

  test('desktop/mobile hamburger toggles navigation', async ({ page }) => {
    await page.goto(`${baseURL}/Full_Compact.html`, { waitUntil: 'domcontentloaded' });

    // desktop collapse toggle
    await page.setViewportSize({ width: 1400, height: 900 });
    await page.click('#menuBtn');
    await expect(page.locator('body')).toHaveClass(/sidebar-collapsed/);
    await page.click('#menuBtn');
    const bodyClassDesktop = await page.locator('body').getAttribute('class');
    expect(bodyClassDesktop || '').not.toContain('sidebar-collapsed');

    // mobile overlay toggle
    await page.setViewportSize({ width: 390, height: 844 });
    await page.click('#menuBtn');
    await expect(page.locator('body')).toHaveClass(/menu-open/);
    await page.click('button[data-view="login"]');
    const bodyClassMobile = await page.locator('body').getAttribute('class');
    expect(bodyClassMobile || '').not.toContain('menu-open');
  });
});

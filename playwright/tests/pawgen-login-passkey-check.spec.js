import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

test.use({ ignoreHTTPSErrors: true });

test('pawgen login page passkey ui check', async ({ page }) => {
  const logs = [];

  page.on('console', (msg) => {
    logs.push(`[console:${msg.type()}] ${msg.text()}`);
  });

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('/api/admin/auth/') || url.includes('/login') || url.includes('/mfa')) {
      let body = '';
      try {
        body = await res.text();
      } catch {
        body = '';
      }
      logs.push(`[resp] ${res.status()} ${url} body=${body.slice(0, 300)}`);
    }
  });

  await page.goto('https://pawgen.kro.kr/login', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(5000);

  const snapshot = await page.evaluate(() => {
    const pick = (sel) => {
      const el = document.querySelector(sel);
      if (!el) return null;
      const style = window.getComputedStyle(el);
      return {
        text: (el.textContent || '').trim().slice(0, 200),
        display: style.display,
        visibility: style.visibility,
        opacity: style.opacity,
        disabled: el.disabled === true
      };
    };

    const buttons = Array.from(document.querySelectorAll('button')).map((b) => ({
      id: b.id || null,
      cls: b.className || null,
      text: (b.textContent || '').trim(),
      visible: !!(b.offsetWidth || b.offsetHeight || b.getClientRects().length),
      disabled: b.disabled === true
    }));

    return {
      title: document.title,
      url: location.href,
      h1: document.querySelector('h1')?.textContent?.trim() || null,
      passkeySection: pick('#passkeySection'),
      registerPasskeyButton: pick('#registerPasskeyButton'),
      statusText: pick('#status'),
      buttons
    };
  });

  const outDir = path.resolve('.local');
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'pawgen-login-snapshot.json'), JSON.stringify(snapshot, null, 2), 'utf-8');
  fs.writeFileSync(path.join(outDir, 'pawgen-login-logs.txt'), logs.join('\n'), 'utf-8');
  await page.screenshot({ path: path.join(outDir, 'pawgen-login.png'), fullPage: true });
  console.log(`snapshot-written: ${path.join(outDir, 'pawgen-login-snapshot.json')}`);

  expect(snapshot.url).toContain('pawgen.kro.kr');
});

const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const baseURL = process.env.BASE_URL || 'http://localhost:8081';
const localDir = path.resolve(__dirname, '..', '..', '.local');
const ADMIN_EMAIL = 'playwright-user1@local.dev';

function readToken(fileName) {
  const tokenPath = path.join(localDir, fileName);
  if (!fs.existsSync(tokenPath)) throw new Error(`missing token file: ${tokenPath}`);
  return fs.readFileSync(tokenPath, 'utf8').trim();
}

async function readJsonSafe(response) {
  const text = await response.text();
  try { return JSON.parse(text); } catch { return text; }
}

async function api(pathname, { method = 'GET', token, body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${baseURL}${pathname}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  return { status: response.status, body: await readJsonSafe(response) };
}

test.describe.serial('admin traffic console local', () => {
  test('shows all req/res console output', async ({ page }) => {
    test.setTimeout(60000);

    await page.goto(`${baseURL}/full_compact/pages/admin-flow.html`, { waitUntil: 'domcontentloaded' });

    const allConsole = page.locator('#trafficAllOutput');
    await expect(allConsole).toHaveCount(1);
    const text = await allConsole.textContent();
    expect(text).not.toBeNull();
    const content = await page.content();
    expect(content).toContain('[${dir}]');
    expect(content).toContain('function formatTrafficPayload');
    expect(content).toContain('${label.toLowerCase()}:');

    // grouped console remains
    await expect(page.locator('#trafficOutput')).toHaveCount(1);
    expect(content).toContain('id="trafficAllOutput"');
    expect(content).toContain('function printAllTraffic');
  });
});

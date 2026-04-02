// Minimal config so Playwright can discover tests in this Gradle repo.
/** @type {import('@playwright/test').PlaywrightTestConfig} */
const config = {
  testDir: './tests',
  outputDir: './test-results',
  reporter: [
    ['line'],
    ['html', { outputFolder: './playwright-report', open: 'never' }]
  ],
  use: {
    headless: true
  }
};

module.exports = config;
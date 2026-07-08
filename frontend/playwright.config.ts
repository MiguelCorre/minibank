import { defineConfig } from '@playwright/test';

/**
 * E2E tests drive the Angular dev server (started here) against the real
 * backend, which must already be running on :8080 (locally:
 * `.\mvnw spring-boot:run`; in CI: the packaged jar + a PostgreSQL service).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry'
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000
  }
});

import { defineConfig } from '@playwright/test';

/**
 * Playwright E2E config for CareQ.
 *
 * Requires the backend stack (eureka, gateway, auth, user, doctor, queue) and
 * the Vite dev server to be running. The `webServer.reuseExistingServer: true`
 * means it will attach to an already-running `npm run dev` on :3030 rather
 * than starting a second one.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:3030',
    // Use the system-installed Google Chrome instead of downloading Chromium.
    channel: 'chrome',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3030',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});

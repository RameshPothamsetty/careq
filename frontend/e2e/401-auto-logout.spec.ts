import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

/**
 * Day 6 regression test: an expired/invalid JWT must never leave the app
 * stuck on stale data. The chain under test:
 *
 *   protected query fires with bad token → backend 401
 *   → baseQuery clears localStorage + dispatches 'careq:unauthorized'
 *   → AuthContext.logout() (wipes RTK caches)
 *   → ProtectedRoute sees isAuthenticated=false → <Navigate to="/login" />
 *
 * PREREQUISITES (same as admin-happy-path.spec.ts): backend stack running,
 * seed data applied, dev server on :3030.
 */
test('expired/invalid JWT (401) forces logout and redirects to /login', async ({ page }) => {
  // ── 1. Login as admin through the UI ──────────────────────────────
  await loginAsAdmin(page);

  // ── 2. Simulate an expired/invalid JWT ────────────────────────────
  // Bypass the login form: corrupt the stored token directly, exactly like a
  // JWT whose signature/expiry no longer validates at the gateway.
  await page.evaluate(() => localStorage.setItem('careq_token', 'garbage-token-123'));

  // ── 3. Trigger a protected, data-driven screen ────────────────────
  // AdminQueueOverview fires GET /api/queue/live with the corrupted token.
  await page.goto('/admin/queue');

  // ── 4. Assert the auto-logout ─────────────────────────────────────
  await expect(page).toHaveURL(/\/login/, { timeout: 15_000 });

  // Session fully cleared — no stale token/user left behind.
  expect(await page.evaluate(() => localStorage.getItem('careq_token'))).toBeNull();
  expect(await page.evaluate(() => localStorage.getItem('careq_user'))).toBeNull();

  // The login page is actually rendered, not just a bare redirect.
  await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  await expect(page.getByPlaceholder('you@hospital.com')).toBeVisible();
});

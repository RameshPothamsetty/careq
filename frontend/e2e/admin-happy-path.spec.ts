import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

/**
 * Day 6 admin happy-path regression suite. Exercises the screens migrated to
 * RTK Query: department CRUD (create → rename → delete), the doctor catalog
 * list, and the live queue overview.
 *
 * PREREQUISITES (same as 401-auto-logout.spec.ts): backend stack running,
 * seed data applied (bash scripts/seed-data.sh), dev server on :3030.
 */
test.describe('admin happy path', () => {
  test('departments: create → rename → delete', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/departments');
    await expect(page.getByRole('heading', { name: /manage departments/i })).toBeVisible();

    // Unique name so re-runs never collide with the backend's duplicate check.
    const deptName = `QA E2E ${Date.now()}`;
    const renamed = `${deptName} v2`;

    // ── Create ──
    await page.getByRole('button', { name: /add department/i }).click();
    const form = page.locator('form');
    await form.getByPlaceholder('e.g., Cardiology').fill(deptName);
    await form.getByPlaceholder(/brief description/i).fill('Created by Playwright E2E');
    await form.getByRole('button', { name: /create/i }).click();

    await expect(page.getByText('Department created successfully')).toBeVisible();
    const createdRow = page.getByRole('row', { name: new RegExp(deptName) });
    await expect(createdRow).toBeVisible();
    // StatusTag renders the status in title case (e.g. "Active").
    await expect(createdRow).toContainText(/active/i);

    // ── Rename ──
    await createdRow.getByRole('button', { name: /edit/i }).click();
    await form.getByPlaceholder('e.g., Cardiology').fill(renamed);
    await form.getByRole('button', { name: /update/i }).click();

    await expect(page.getByText('Department updated successfully')).toBeVisible();
    await expect(page.getByRole('row', { name: new RegExp(renamed) })).toBeVisible();

    // ── Delete (accept the window.confirm) ──
    page.once('dialog', (dialog) => dialog.accept());
    await page
      .getByRole('row', { name: new RegExp(renamed) })
      .getByRole('button', { name: /delete/i })
      .click();

    await expect(page.getByText('Department deleted successfully')).toBeVisible();
    await expect(page.getByRole('row', { name: new RegExp(renamed) })).toHaveCount(0);
  });

  test('doctor catalog list renders seeded entries', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/doctors');
    await expect(page.getByRole('heading', { name: /manage doctor catalog/i })).toBeVisible();

    // The seeded catalog (scripts/seed-data.sh) includes 10 doctors — a known
    // specialization proves the table is populated with real data.
    await expect(page.getByText('Interventional Cardiology')).toBeVisible();
    await expect(page.locator('table tbody tr').first()).toBeVisible();
  });

  test('live queue overview renders stat cards + doctor table', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/queue');
    await expect(page.getByRole('heading', { name: /live queue overview/i })).toBeVisible();

    // Five summary stat cards. `.first()` is needed for 'In consultation',
    // which also appears as the table column header — the stat cards render
    // first in the DOM, so the first match is the card label.
    for (const label of [
      'Patients waiting',
      'In consultation',
      'Doctors online',
      'Delayed consultations',
      'Avg wait (min)',
    ]) {
      await expect(page.getByText(label).first()).toBeVisible();
    }

    // Per-doctor breakdown table.
    await expect(page.getByText('All doctors')).toBeVisible();
    await expect(page.locator('table tbody tr').first()).toBeVisible();
  });
});

import { expect, type Page } from '@playwright/test';

/**
 * Shared E2E credentials — the seeded accounts from scripts/seed-data.sh.
 * Overridable via env vars for non-seeded environments.
 */
export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@careq.com';
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin123';
export const DOCTOR_EMAIL = process.env.E2E_DOCTOR_EMAIL ?? 'dr.arjun@careq.com';
export const DOCTOR_PASSWORD = process.env.E2E_DOCTOR_PASSWORD ?? 'password123';

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN';

/**
 * Logs in through the real UI and waits for the role's dashboard to load.
 * The login form's labels are not htmlFor-associated, so inputs are targeted
 * by placeholder.
 */
export async function login(page: Page, email: string, password: string, role: Role): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('you@hospital.com').fill(email);
  await page.getByPlaceholder('Enter your password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(new RegExp(`/${role.toLowerCase()}/dashboard`), { timeout: 15_000 });
}

/** Logs in as the seeded admin. */
export async function loginAsAdmin(page: Page): Promise<void> {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD, 'ADMIN');
}

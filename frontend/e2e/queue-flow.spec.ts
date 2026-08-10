import { test, expect } from '@playwright/test';
import {
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
  DOCTOR_EMAIL,
  DOCTOR_PASSWORD,
  login,
} from './helpers';

/**
 * Queue happy path (Day 6 regression suite for the migrated queue screens):
 *
 *   patient joins dr. Arjun's queue via the UI (AI triage runs)
 *   → patient's live status view shows their position
 *   → doctor logs in, calls the patient next (IN_PROGRESS)
 *   → doctor completes the consultation → patient leaves the active queue
 *
 * PREREQUISITES (same as the other specs): backend stack running, seed data
 * applied, dev server on :3030.
 *
 * Setup is done via API so the UI assertions are deterministic regardless of
 * leftover queue state from earlier manual testing.
 */

const API_BASE = 'http://localhost:8080';

// Shared across the serial tests: the symptom text the doctor will look for.
let sharedSymptom = '';

test.describe.serial('queue happy path (patient join → doctor call-next → complete)', () => {
  test('patient joins dr. arjun queue and sees live status', async ({ page, request }) => {
    // ── API setup: deterministic starting state ─────────────────────
    // Doctor token: ensure dr. arjun is accepting patients.
    const doctorLogin = await (
      await request.post(`${API_BASE}/api/auth/login`, {
        data: { email: DOCTOR_EMAIL, password: DOCTOR_PASSWORD },
      })
    ).json();
    const doctorAuth = {
      Authorization: `Bearer ${doctorLogin.token as string}`,
      'Content-Type': 'application/json',
    };
    const avail = await request.put(`${API_BASE}/api/doctors/me/availability`, {
      headers: doctorAuth,
      data: { isAvailable: true },
    });
    expect(avail.ok(), 'dr. arjun is available').toBeTruthy();

    // Admin token: clear any leftover active entries in dr. arjun's queue so
    // the doctor test's call-next targets OUR patient deterministically.
    const adminLogin = await (
      await request.post(`${API_BASE}/api/auth/login`, {
        data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
      })
    ).json();
    const adminAuth = {
      Authorization: `Bearer ${adminLogin.token as string}`,
      'Content-Type': 'application/json',
    };

    // GET /api/doctors is paginated since Day 7a — read the .content array.
    const doctorsResponse = await request.get(`${API_BASE}/api/doctors?size=100`, { headers: adminAuth });
    const doctorsPage = (await doctorsResponse.json()) as {
      content: Array<{ id: number; specialization: string }>;
    };
    const arjun = doctorsPage.content.find((d) => d.specialization === 'Interventional Cardiology');
    expect(arjun, 'seeded dr. arjun catalog entry').toBeTruthy();

    const queue = await (
      await request.get(`${API_BASE}/api/queue/doctor/${arjun!.id}`, { headers: adminAuth })
    ).json();
    for (const entry of queue as Array<{ id: number; status: string }>) {
      if (entry.status === 'WAITING') {
        await request.put(`${API_BASE}/api/queue/${entry.id}/call-next`, { headers: adminAuth });
      }
      await request.put(`${API_BASE}/api/queue/${entry.id}/complete`, { headers: adminAuth });
    }

    // Fresh unique patient — no leftover active entry, no email collision.
    const email = `e2e.patient.${Date.now()}@careq.com`;
    const signup = await request.post(`${API_BASE}/api/auth/signup`, {
      data: { fullName: 'E2E Patient', email, password: 'password123', role: 'PATIENT' },
    });
    expect(signup.ok(), `signup fresh patient ${email}`).toBeTruthy();

    // ── UI: login as the patient and join the queue ────────────────
    await login(page, email, 'password123', 'PATIENT');
    await page.goto('/patient/queue');

    sharedSymptom = `E2E chest pain ${Date.now()}`;

    // The join flow is symptoms-first (AI auto-join is the primary CTA) —
    // reveal the manual doctor picker before selecting. The header's language
    // switcher is also a <select>, so target the picker explicitly.
    await page.getByText(/choose a doctor manually/i).click();

    // selectOption() rejects regex labels — resolve the option value first.
    const doctorSelect = page.locator('select.select-field');
    const doctorValue = await doctorSelect
      .locator('option', { hasText: 'Interventional Cardiology' })
      .getAttribute('value');
    expect(doctorValue, 'dr. arjun is in the available doctor list').toBeTruthy();
    await doctorSelect.selectOption(doctorValue!);
    await page.getByPlaceholder(/describe what you're experiencing/i).fill(sharedSymptom);
    await page.getByRole('button', { name: /join queue/i }).click();

    // The live status view takes over once AI triage completes.
    await expect(page.getByText('Your position in queue')).toBeVisible({ timeout: 20_000 });
    // exact: true — 'Position in queue' is a substring of the 'Your position in
    // queue' h2 above, so the loose match would resolve to 2 elements.
    await expect(page.getByText('Position in queue', { exact: true })).toBeVisible();
  });

  test('doctor calls next and completes the patient', async ({ page }) => {
    await login(page, DOCTOR_EMAIL, DOCTOR_PASSWORD, 'DOCTOR');
    await page.goto('/doctor/queue');

    // Our patient's entry is the only active one (queue was cleaned in setup).
    // `.first()` — the symptom text also appears in the "Next patient ready"
    // hero card, so it matches twice (strict mode would reject the assertion).
    await expect(page.getByText(sharedSymptom).first()).toBeVisible({ timeout: 20_000 });

    // Call next — the hero card and the row both have the button; use the first.
    await page.getByRole('button', { name: /call next/i }).first().click();
    await expect(page.getByText(/consultation started/i)).toBeVisible({ timeout: 10_000 });

    // Complete the now IN_PROGRESS consultation.
    await page.getByRole('button', { name: /complete/i }).click();
    await expect(page.getByText(/queue advanced/i)).toBeVisible({ timeout: 10_000 });

    // The patient has left the active queue — the kanban's Waiting column is
    // now empty (the patient sits in this session's Completed column, which
    // shows no symptom text).
    await expect(page.getByText(sharedSymptom)).toHaveCount(0);
    await expect(
      page.getByText('No patients waiting — new joins appear here with AI triage.'),
    ).toBeVisible();
  });
});

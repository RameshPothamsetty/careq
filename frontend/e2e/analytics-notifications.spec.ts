import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import { login, ADMIN_EMAIL, ADMIN_PASSWORD } from './helpers';

/**
 * Day 7b verification: Admin analytics dashboard renders real charts, and
 * client-side notifications fire on a REAL status transition (patient
 * joined → doctor calls next → toast + bell appear on the patient session).
 *
 * Requires the full backend stack + Vite dev server on :3030 (the standard
 * CareQ E2E setup). API calls below go straight to the gateway — the Vite
 * dev proxy targets :8080 which may hold an unrelated app.
 */

// Gateway base for direct API calls (the browser talks to it via
// VITE_API_BASE_URL; the Vite dev proxy is not used here).
// Overridable with E2E_GATEWAY_URL when the gateway is not on 8080
// (e.g. a custom port in CI).
const GATEWAY_URL = process.env.E2E_GATEWAY_URL ?? 'http://localhost:8080';

// Catalog entry id 1 is Dr. Arjun (per the seeded doctor catalog). Joining his
// queue lets the seeded doctor account call this exact patient next.
const TARGET_DOCTOR_CATALOG_ID = '1';
const PATIENT_EMAIL = 'john@careq.com';

/**
 * Repeated runs leave stale WAITING/IN_PROGRESS entries on the seed patient.
 * Because a patient can only hold ONE active entry per doctor, and the queue
 * has no cancel endpoint, the cleanest reset is: as admin, walk every doctor's
 * queue and call-next + complete any entry belonging to this patient.
 */
async function clearPatientActiveEntries(request: APIRequestContext) {
  const adminLogin = await request.post(`${GATEWAY_URL}/api/auth/login`, {
    data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD },
  });
  const { token } = await adminLogin.json();
  const headers = { Authorization: `Bearer ${token}` };

  const patientLogin = await request.post(`${GATEWAY_URL}/api/auth/login`, {
    data: { email: PATIENT_EMAIL, password: 'password123' },
  });
  const { userId } = await patientLogin.json();

  const doctors = await request.get(`${GATEWAY_URL}/api/doctors?size=100`, { headers });
  const page = await doctors.json();

  for (const doc of page.content) {
    const q = await request.get(`${GATEWAY_URL}/api/queue/doctor/${doc.id}`, {
      headers: { ...headers, 'X-User-Role': 'ADMIN', 'X-User-Id': 'admin-e2e' },
    });
    if (!q.ok()) continue;
    const entries = await q.json();
    for (const entry of entries) {
      if (entry.patientId !== userId) continue;
      // WAITING → IN_PROGRESS → COMPLETED (both steps admin-allowed).
      await request.put(`${GATEWAY_URL}/api/queue/${entry.id}/call-next`, {
        headers: { ...headers, 'X-User-Role': 'ADMIN', 'X-User-Id': 'admin-e2e' },
      });
      await request.put(`${GATEWAY_URL}/api/queue/${entry.id}/complete`, {
        headers: { ...headers, 'X-User-Role': 'ADMIN', 'X-User-Id': 'admin-e2e' },
      });
    }
  }
}

async function joinQueueAsPatient(page: Page) {
  await login(page, 'john@careq.com', 'password123', 'PATIENT');
  // My Queue page: join flow
  await page.goto('/patient/queue');
  await page.getByText('Join a queue', { exact: false }).waitFor({ timeout: 15_000 });
  // The join flow is symptoms-first (AI auto-join is the primary CTA) —
  // reveal the manual doctor picker before selecting a doctor. The header's
  // language switcher is also a <select>, so target the picker explicitly.
  await page.getByText(/choose a doctor manually/i).click();
  await page.locator('select.select-field').selectOption(TARGET_DOCTOR_CATALOG_ID);
  await page
    .locator('textarea')
    .fill('Mild chest pain for the past two hours, some shortness of breath');
  await page.getByRole('button', { name: /join queue/i }).click();
  // Live status view appears
  await expect(page.getByText(/your position in queue|it's your turn/i)).toBeVisible({
    timeout: 20_000,
  });
}

test('admin analytics dashboard renders charts with data', async ({ page }) => {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD, 'ADMIN');
  await page.goto('/admin/analytics');

  // Summary stat cards
  await expect(page.getByText('Patients handled (7d)')).toBeVisible({ timeout: 15_000 });
  // Chart section headings
  await expect(page.getByText('Patients handled per day')).toBeVisible();
  await expect(page.getByText('Average wait time trend')).toBeVisible();
  await expect(page.getByText('Queue distribution by department')).toBeVisible();
  // Recharts actually rendered SVG surfaces (not a blank page)
  await expect(page.locator('.recharts-wrapper').first()).toBeVisible();
  await expect(page.locator('.recharts-wrapper')).toHaveCount(3, { timeout: 10_000 });
  // No console errors on the page
  const errors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text());
  });
  await page.waitForTimeout(2_000);
  expect(errors.filter((e) => !e.includes('favicon'))).toEqual([]);
});

test('patient notification toast fires when doctor calls them', async ({ browser, request }) => {
  // Reset any stale active entries from prior runs so the join form shows.
  await clearPatientActiveEntries(request);

  // Two independent sessions: the patient polls my-status, the doctor calls next.
  const patientCtx = await browser.newContext();
  const doctorCtx = await browser.newContext();
  const patient = await patientCtx.newPage();
  const doctor = await doctorCtx.newPage();

  try {
    await joinQueueAsPatient(patient);

    // Doctor: open their live queue for the same doctor and call the patient next.
    await login(doctor, 'dr.arjun@careq.com', 'password123', 'DOCTOR');
    await doctor.goto('/doctor/queue');
    await expect(doctor.getByText(/live patient queue/i)).toBeVisible({ timeout: 15_000 });

    // The newly joined patient should be in the queue — call them.
    const callNextButton = doctor.getByRole('button', { name: /call next/i }).first();
    await callNextButton.waitFor({ timeout: 20_000 });
    await callNextButton.click();

    // Patient session: the polled status flips WAITING → IN_PROGRESS, which the
    // useQueueNotifications hook detects → toast + bell badge appear.
    // The hero card also says "It's your turn!" so target the toast (role=status).
    const toast = patient.getByRole('status').filter({ hasText: /has called you/i });
    await expect(toast).toBeVisible({ timeout: 30_000 });
    // Bell badge shows an unread count.
    await expect(patient.locator('button[aria-label^="Notifications"]')).toBeVisible();
  } finally {
    await patientCtx.close();
    await doctorCtx.close();
  }
});

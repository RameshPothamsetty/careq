// Scratch verification script for the light/dark theme toggle (not a Playwright spec).
// Requires: backend stack up (localhost:8080) + a frontend origin whose /api
// requests are accepted by the gateway (nginx :3030 or Vite :5173).
// Run: node e2e/theme-toggle-check.mjs
//      CAREQ_BASE=http://localhost:3030 node e2e/theme-toggle-check.mjs
import { chromium } from '@playwright/test';

const BASE = process.env.CAREQ_BASE ?? 'http://localhost:5173';
let pass = 0;
let fail = 0;
const failures = [];

function check(name, ok, detail) {
  if (ok) {
    pass += 1;
    console.log(`  PASS  ${name} — ${detail}`);
  } else {
    fail += 1;
    failures.push(name);
    console.log(`  FAIL  ${name} — ${detail}`);
  }
}

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage();
const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text());
});
page.on('pageerror', (e) => consoleErrors.push(String(e)));

const isDark = () =>
  page.evaluate(() => document.documentElement.classList.contains('dark'));

async function login(email, password, rolePath) {
  for (let attempt = 1; attempt <= 2; attempt += 1) {
    await page.getByPlaceholder('you@hospital.com').fill(email);
    await page.getByPlaceholder('Enter your password').fill(password);
    await page.getByRole('button', { name: /sign in/i }).click();
    try {
      await page.waitForURL(new RegExp(`/${rolePath}/dashboard`), { timeout: 15_000 });
      return true;
    } catch {
      if (attempt === 1) console.log('  (retrying login…)');
    }
  }
  return false;
}

console.log('PHASE 0 — login page (logged out, mode auto)');
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(800);
check('P0 login is light', !(await isDark()), 'no .dark on <html>');

console.log('PHASE A — patient (john@careq.com)');
if (await login('john@careq.com', 'password123', 'patient')) {
  check('A auto=light for patient', !(await isDark()), 'patient default is light');

  await page.getByRole('button', { name: 'Dark theme' }).click();
  await page.waitForTimeout(500);
  check('C dark on', await isDark(), '.dark present, background dark');

  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(800);
  check('C persisted across reload', await isDark(), 'still dark after F5');

  await page.getByRole('button', { name: 'Light theme' }).click();
  await page.waitForTimeout(500);
  check('D light on', !(await isDark()), '.dark removed');

  await page.getByRole('button', { name: 'Auto (role default)' }).click();
  await page.waitForTimeout(500);
  check('D auto returns to light', !(await isDark()), 'patient auto default is light');

  await page.getByRole('button', { name: 'Dark theme' }).click();
  await page.waitForTimeout(400);
  await page.getByRole('button', { name: /logout/i }).click();
  await page.waitForURL(/\/login/, { timeout: 15_000 });
  await page.waitForTimeout(600);
  check('E login follows saved dark', await isDark(), 'dark choice survived logout');
} else {
  check('A patient login', false, 'could not sign in');
}

console.log('PHASE B — doctor (dr.arjun@careq.com)');
if (await login('dr.arjun@careq.com', 'password123', 'doctor')) {
  check('F doctor starts dark', await isDark(), 'saved dark mode applies');

  await page.getByRole('button', { name: 'Auto (role default)' }).click();
  await page.waitForTimeout(500);
  check('G staff auto=dark', await isDark(), 'doctor role default is dark');

  await page.getByRole('button', { name: 'Light theme' }).click();
  await page.waitForTimeout(500);
  check('H doctor can opt to light', !(await isDark()), 'manual light wins over role');

  await page.getByRole('button', { name: 'Auto (role default)' }).click();
  await page.waitForTimeout(500);
  check('H auto restores dark', await isDark(), 'auto returns to staff dark');

  await page.getByRole('button', { name: /logout/i }).click();
  await page.waitForURL(/\/login/, { timeout: 15_000 });
  await page.waitForTimeout(600);
  check('I logout auto=light', !(await isDark()), 'auto + logged out resolves to light');
} else {
  check('F doctor login', false, 'could not sign in');
}

console.log('\nConsole errors:', consoleErrors.length ? consoleErrors.slice(0, 8) : 'none');
console.log(`\nRESULT: ${pass} passed, ${fail} failed`);
if (failures.length) console.log('FAILED:', failures.join(' | '));
await browser.close();
process.exit(fail ? 1 : 0);

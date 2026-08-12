#!/usr/bin/env node
/**
 * Day 15 — Live-Azure smoke test.
 *
 * Runs the same signup → login → browse → join → status journey as the
 * Day 11 Docker smoke, but against the deployed Azure URLs.
 *
 * Usage (after the first CD deploy run succeeds):
 *   API_BASE="https://careq-api-gateway.<env>.<region>.azurecontainerapps.io" \
 *   FRONTEND_BASE="https://careq-frontend.vercel.app" \
 *   node scripts/day15-azure-smoke.mjs
 *
 * Prereq (once): seed the live DB so doctors/departments exist:
 *   API_BASE="https://<gateway-fqdn>" bash scripts/seed-data.sh
 *
 * COLD-START NOTE: user/doctor/queue/notification services scale to zero
 * after ~5 min idle. The first request after idle can return 503/502 while
 * the app wakes up (10-60s) — this script retries the join path a few times
 * and reports what it observed, which is exactly what the Day 15 checklist
 * asks you to confirm.
 *
 * Exit code is non-zero if any check fails.
 */
const GATEWAY = process.env.API_BASE || 'https://careq-api-gateway.azurecontainerapps.io';
const FRONTEND = process.env.FRONTEND_BASE || 'https://careq-frontend.vercel.app';

const results = [];
let ok = 0;
let fail = 0;

function record(name, pass, detail) {
  results.push({ name, pass, detail });
  pass ? ok++ : fail++;
  console.log(`${pass ? '✅' : '❌'} ${name}${detail ? ` — ${detail}` : ''}`);
}

async function api(method, path, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(GATEWAY + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }
  return { status: res.status, data };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  console.log('CareQ — Day 15 live-Azure smoke test');
  console.log(`  gateway:  ${GATEWAY}`);
  console.log(`  frontend: ${FRONTEND}\n`);

  // 1. Vercel serves the SPA.
  const fe = await fetch(FRONTEND + '/');
  const feHtml = await fe.text();
  record('Vercel serves the SPA', fe.status === 200 && feHtml.includes('id="root"'), `status=${fe.status}`);

  // 2. Gateway health through the public HTTPS FQDN.
  const gw = await fetch(GATEWAY + '/actuator/health');
  record('Gateway reachable over HTTPS', gw.status === 200, `status=${gw.status}`);

  // 3. Per-service health through the gateway (all whitelisted).
  const healthRoutes = [
    ['auth-service', '/api/auth/health'],
    ['user-service', '/api/users/health'],
    ['doctor-service', '/api/doctors/health'],
    ['queue-service', '/api/queue/health'],
    ['notification-service', '/api/notifications/health'],
  ];
  for (const [name, path] of healthRoutes) {
    // Scale-to-zero services can 503 on the first hit — retry briefly.
    let r;
    for (let attempt = 1; attempt <= 5; attempt++) {
      r = await fetch(GATEWAY + path);
      if (r.status !== 503) break;
      console.log(`  (cold start: ${name} waking up — attempt ${attempt}/5)`);
      await sleep(15000);
    }
    record(`Gateway routes ${name}`, r.status === 200, `GET ${path} -> ${r.status}`);
  }

  // 4. Seeded admin can log in (proves MySQL + JWT on Azure).
  const adminLogin = await api('POST', '/api/auth/login', { body: { email: 'admin@careq.com', password: 'admin123' } });
  const adminToken = adminLogin.data?.token;
  record('Seeded admin can log in', adminLogin.status === 200 && !!adminToken, `status=${adminLogin.status}`);
  if (!adminToken) {
    console.error('\nAborting: seed the live DB first -> API_BASE=<gateway> bash scripts/seed-data.sh');
    process.exit(1);
  }
  const depts = await api('GET', '/api/departments', { token: adminToken });
  record('Departments seeded', depts.status === 200 && Array.isArray(depts.data) && depts.data.length >= 1,
    `count=${Array.isArray(depts.data) ? depts.data.length : 'n/a'}`);

  // 5. End-to-end patient journey.
  const email = `azure.smoke.${Date.now()}@careq.com`;
  const signup = await api('POST', '/api/auth/signup', { body: { fullName: 'Azure Smoke Patient', email, password: 'password123', role: 'PATIENT' } });
  record('Fresh patient signup', signup.status === 201 && !!signup.data?.token, `status=${signup.status}`);

  const patientLogin = await api('POST', '/api/auth/login', { body: { email, password: 'password123' } });
  const patientToken = patientLogin.data?.token;
  record('Patient login', patientLogin.status === 200 && !!patientToken, `status=${patientLogin.status}`);

  const doctors = await api('GET', '/api/doctors?page=0&size=20', { token: patientToken });
  const available = Array.isArray(doctors.data?.content) ? doctors.data.content.find((d) => d.isAvailable) : null;
  record('Patient browses doctors', doctors.status === 200 && !!available,
    `status=${doctors.status}, total=${doctors.data?.totalElements}, available=${available?.name ?? 'none'}`);

  // Cold-start aware join: 503s are expected while Eureka/Feign/scale-up settle.
  let join;
  for (let attempt = 1; attempt <= 8; attempt++) {
    join = await api('POST', '/api/queue/join', {
      token: patientToken,
      body: { doctorCatalogEntryId: available.id, patientName: 'Azure Smoke Patient', symptomText: 'persistent headache with blurred vision' },
    });
    if (join.status !== 503 && join.status !== 502) break;
    if (attempt < 8) {
      console.log(`  (cold start: join ${join.status}, retrying in 15s — attempt ${attempt}/8)`);
      await sleep(15000);
    }
  }
  const triageOk = ['EMERGENCY', 'HIGH', 'NORMAL', 'FOLLOW_UP'].includes(join.data?.effectiveTriage);
  record('Patient joins queue (AI triage assigned)', join.status === 201 && triageOk,
    `status=${join.status}, triage=${join.data?.effectiveTriage}${join.data?.aiSuggestedTriage !== join.data?.effectiveTriage ? ` (ai=${join.data?.aiSuggestedTriage})` : ''}`);
  record('Wait-time prediction present', join.status === 201 && join.data?.predictedWaitMinutes !== undefined && join.data?.predictedWaitMinutes !== null,
    `predictedWaitMinutes=${join.data?.predictedWaitMinutes}, position=${join.data?.position}`);

  const status = await api('GET', '/api/queue/my-status', { token: patientToken });
  record('Patient my-status active', status.status === 200 && status.data?.active === true, `status=${status.status}`);

  console.log(`\n===== ${ok} passed, ${fail} failed =====`);
  console.log('If any 503 checks passed only after retries, that delay was a scale-to-zero');
  console.log('cold start — record it in docs/10_DEPLOYMENT.md §4 per the Day 15 checklist.');
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((err) => { console.error('Smoke test crashed:', err); process.exit(1); });

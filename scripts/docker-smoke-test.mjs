#!/usr/bin/env node
/**
 * Dockerized-stack smoke test.
 *
 * Runs against the `docker compose` stack via the published host ports
 * (identical to local dev): gateway :8080, eureka :8761, frontend :3030.
 *
 * Prereq (once, after `docker compose up -d`):
 *   bash scripts/seed-data.sh    # creates admin + 10 doctors + catalog entries
 *
 * Exit code is non-zero if any check fails.
 */
const GATEWAY = process.env.API_BASE || 'http://localhost:8080';
const EUREKA = process.env.EUREKA_BASE || 'http://localhost:8761';
const FRONTEND = process.env.FRONTEND_BASE || 'http://localhost:3030';

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
  console.log('CareQ — Docker smoke test\n');

  // 1. Frontend serves the SPA on :3030.
  const fe = await fetch(FRONTEND + '/');
  const feHtml = await fe.text();
  record('Frontend serves the SPA on :3030',
    fe.status === 200 && feHtml.includes('id="root"'), `status=${fe.status}`);

  // 2. Eureka has every business service registered (proves service discovery).
  const eureka = await fetch(EUREKA + '/eureka/apps', { headers: { Accept: 'application/json' } });
  const apps = await eureka.json();
  const registered = (apps.applications?.application || []).map((a) => a.name).sort();
  const expected = ['API-GATEWAY', 'AUTH-SERVICE', 'USER-SERVICE', 'DOCTOR-SERVICE', 'QUEUE-SERVICE'];
  const missing = expected.filter((n) => !registered.includes(n));
  record('Eureka has all 5 business services registered',
    missing.length === 0, `registered=${registered.join(', ')}${missing.length ? ' MISSING=' + missing.join(',') : ''}`);

  // 3. Gateway routes to every service's health endpoint (proves lb:// routing).
  const healthRoutes = [
    ['auth-service via gateway', '/api/auth/health'],
    ['user-service via gateway', '/api/users/health'],
    ['doctor-service via gateway', '/api/doctors/health'],
    ['queue-service via gateway', '/api/queue/health'],
  ];
  for (const [name, path] of healthRoutes) {
    const r = await fetch(GATEWAY + path);
    record(`Gateway routes ${name}`, r.status === 200, `GET ${path} -> ${r.status}`);
  }

  // 4. Seed data present (admin login proves MySQL + JWT signing in Docker).
  const adminLogin = await api('POST', '/api/auth/login', { body: { email: 'admin@careq.com', password: 'admin123' } });
  const adminToken = adminLogin.data?.token;
  record('Seeded admin can log in (MySQL + JWT in Docker)', adminLogin.status === 200 && !!adminToken,
    `status=${adminLogin.status}`);
  if (!adminToken) {
    console.error('\nAborting: seed the stack first -> bash scripts/seed-data.sh');
    process.exit(1);
  }
  const depts = await api('GET', '/api/departments', { token: adminToken });
  record('Departments seeded (10)', depts.status === 200 && Array.isArray(depts.data) && depts.data.length >= 1,
    `count=${Array.isArray(depts.data) ? depts.data.length : 'n/a'}`);

  // 5. End-to-end patient journey: signup -> login -> browse -> join -> status.
  const email = `docker.smoke.${Date.now()}@careq.com`;
  const signup = await api('POST', '/api/auth/signup', { body: { fullName: 'Docker Smoke Patient', email, password: 'password123', role: 'PATIENT' } });
  record('Fresh patient signup', signup.status === 201 && !!signup.data?.token, `status=${signup.status}`);

  const patientLogin = await api('POST', '/api/auth/login', { body: { email, password: 'password123' } });
  const patientToken = patientLogin.data?.token;
  record('Patient login', patientLogin.status === 200 && !!patientToken, `status=${patientLogin.status}`);

  const doctors = await api('GET', '/api/doctors?page=0&size=20', { token: patientToken });
  const available = Array.isArray(doctors.data?.content) ? doctors.data.content.find((d) => d.isAvailable) : null;
  record('Patient browses doctors', doctors.status === 200 && !!available,
    `status=${doctors.status}, total=${doctors.data?.totalElements}, available=${available?.name ?? 'none'}`);

  // Right after a fresh `docker compose up`, the queue-service's Feign client
  // may not have refreshed its Eureka cache to see doctor-service yet (503) —
  // retry a few times instead of failing on a warm-up timing artifact.
  let join;
  for (let attempt = 1; attempt <= 4; attempt++) {
    join = await api('POST', '/api/queue/join', {
      token: patientToken,
      body: { doctorCatalogEntryId: available.id, patientName: 'Docker Smoke Patient', symptomText: 'persistent headache with blurred vision' },
    });
    if (join.status !== 503) break;
    if (attempt < 4) {
      console.log(`  (registry warm-up: join 503, retrying in 15s — attempt ${attempt}/4)`);
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
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((err) => { console.error('Smoke test crashed:', err); process.exit(1); });

#!/usr/bin/env node
/**
 * Manual testing checklist runner (executed against the LIVE stack).
 * Runs the three role journeys (Patient / Doctor / Admin) + error paths via
 * the API Gateway (http://localhost:8080) and prints PASS/FAIL per check.
 */
const BASE = process.env.API_BASE || 'http://localhost:8080';
const results = [];
let ok = 0;
let fail = 0;

async function api(method, path, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000),
  });
  let json = null;
  try { json = await res.json(); } catch { /* non-JSON */ }
  return { status: res.status, json };
}

function record(name, pass, detail = '') {
  results.push({ name, pass, detail });
  if (pass) ok++; else fail++;
  console.log(`${pass ? 'PASS' : 'FAIL'} | ${name}${detail ? `  [${detail}]` : ''}`);
}

const ts = Date.now();
const emailA = `manual.a.${ts}@careq.com`;
const emailB = `manual.b.${ts}@careq.com`;

console.log('==================================================');
console.log(` CareQ — Manual Testing (${new Date().toISOString()})`);
console.log(` Gateway: ${BASE}`);
console.log('==================================================\n');

// ─────────────────────────── DOCTOR SETUP ───────────────────────────
let r = await api('POST', '/api/auth/login', {
  body: { email: 'dr.arjun@careq.com', password: 'password123' },
});
record('D0. Doctor (Arjun) login', r.status === 200 && !!r.json?.token, `status=${r.status}`);
const docToken = r.json?.token;
const docUserId = r.json?.userId;

r = await api('PUT', '/api/doctors/me/availability', {
  token: docToken,
  body: { isAvailable: true },
});
record('D1. Doctor toggles availability ON', r.status === 200 && r.json?.isAvailable === true, `status=${r.status}`);

// Identify Dr. Arjun's catalog entry (the doctor used for the whole journey).
r = await api('GET', '/api/doctors?size=100', { token: docToken });
const allDoctors = r.json?.content || [];
const myDoctor = allDoctors.find((d) => d.userId === docUserId);
record('D2. Doctor catalog contains Arjun\u2019s entry', !!myDoctor, myDoctor ? `id=${myDoctor.id} spec=${myDoctor.specialization}` : 'not found');
const drId = myDoctor?.id;
const drAvg = myDoctor?.avgConsultationTimeMinutes;

// ─────────────────────────── PATIENT JOURNEY ───────────────────────────
r = await api('POST', '/api/auth/signup', {
  body: { fullName: 'Manual Patient A', email: emailA, password: 'password123', role: 'PATIENT' },
});
record('P1. Patient A signup → 201 + JWT', r.status === 201 && !!r.json?.token, `status=${r.status}`);
const tokenA = r.json?.token;

r = await api('POST', '/api/auth/signup', {
  body: { fullName: 'Manual Patient A', email: emailA, password: 'password123', role: 'PATIENT' },
});
record('P2. Duplicate email signup → 409', r.status === 409, `status=${r.status} msg=${r.json?.message}`);

r = await api('POST', '/api/auth/login', {
  body: { email: emailA, password: 'wrong-password' },
});
record('P3. Wrong-password login → 401', r.status === 401, `status=${r.status}`);

r = await api('POST', '/api/auth/login', {
  body: { email: emailA, password: 'password123' },
});
record('P4. Patient A login → 200', r.status === 200 && !!r.json?.token, `status=${r.status}`);
const tokenALogin = r.json?.token;

r = await api('GET', '/api/users/me', { token: tokenALogin });
record('P5. GET /api/users/me (lazy-create) → 200', r.status === 200 && !!r.json?.userId, `status=${r.status}`);

r = await api('GET', '/api/doctors?size=100', { token: tokenALogin });
record('P6. Patient browses doctor catalog → 200', r.status === 200 && (r.json?.content || []).length > 0, `status=${r.status} count=${r.json?.content?.length}`);

r = await api('POST', '/api/queue/join', {
  token: tokenALogin,
  body: { doctorCatalogEntryId: drId, patientName: 'Manual Patient A', symptomText: 'persistent headache with blurring' },
});
record('P7. Patient A joins queue → 201, position 1, wait 0', r.status === 201 && r.json?.position === 1 && r.json?.predictedWaitMinutes === 0, `status=${r.status} pos=${r.json?.position} triage=${r.json?.aiSuggestedTriage}`);
const entryA = r.json?.id;

r = await api('GET', '/api/queue/my-status', { token: tokenALogin });
record('P8. My queue status → active=true', r.status === 200 && r.json?.active === true, `status=${r.status}`);

// Patient B joins the same doctor → real wait-time calculation.
r = await api('POST', '/api/auth/signup', {
  body: { fullName: 'Manual Patient B', email: emailB, password: 'password123', role: 'PATIENT' },
});
const tokenB = r.json?.token;

r = await api('POST', '/api/queue/join', {
  token: tokenB,
  body: { doctorCatalogEntryId: drId, patientName: 'Manual Patient B', symptomText: 'mild cough' },
});
record('P9. Patient B joins → position 2, wait = avg consult time', r.status === 201 && r.json?.position === 2 && r.json?.predictedWaitMinutes === drAvg, `status=${r.status} pos=${r.json?.position} wait=${r.json?.predictedWaitMinutes} (avg=${drAvg})`);
const entryB = r.json?.id;

r = await api('POST', '/api/queue/join', {
  token: tokenB,
  body: { doctorCatalogEntryId: drId, patientName: 'Manual Patient B', symptomText: 'cough again' },
});
record('P10. Duplicate active queue join → 409', r.status === 409, `status=${r.status}`);

// ─────────────────────────── DOCTOR JOURNEY ───────────────────────────
r = await api('GET', `/api/queue/doctor/${drId}`, { token: docToken });
const queueBefore = r.json || [];
record('D3. Doctor sees live queue → 2 patients', r.status === 200 && queueBefore.length === 2, `status=${r.status} count=${queueBefore.length}`);

r = await api('GET', `/api/queue/doctor/${drId}?search=${encodeURIComponent('Manual Patient A')}`, { token: docToken });
record('D4. Patient-name search narrows the queue', r.status === 200 && r.json?.length === 1 && r.json?.[0]?.patientName === 'Manual Patient A', `status=${r.status} count=${r.json?.length}`);

r = await api('PUT', `/api/queue/${entryB}/override-triage`, {
  token: docToken,
  body: { triageLevel: 'EMERGENCY' },
});
record('D5. Doctor overrides triage → effectiveTriage EMERGENCY', r.status === 200 && r.json?.effectiveTriage === 'EMERGENCY', `status=${r.status}`);

r = await api('GET', `/api/queue/doctor/${drId}`, { token: docToken });
const queueAfter = r.json || [];
record('D6. Overridden patient jumps to position 1', queueAfter.length === 2 && queueAfter[0]?.id === entryB, `order=[${queueAfter.map((e) => `${e.patientName}#${e.position}`).join(', ')}]`);

// Non-owning doctor is rejected.
r = await api('POST', '/api/auth/login', { body: { email: 'dr.priya@careq.com', password: 'password123' } });
const priyaToken = r.json?.token;
r = await api('GET', `/api/queue/doctor/${drId}`, { token: priyaToken });
record('D7. Another doctor viewing Arjun\u2019s queue → 403', r.status === 403, `status=${r.status}`);

r = await api('PUT', `/api/queue/${entryA}/call-next`, { token: docToken });
record('D8. Call next → IN_PROGRESS + calledAt', r.status === 200 && r.json?.status === 'IN_PROGRESS' && !!r.json?.calledAt, `status=${r.status}`);

r = await api('PUT', `/api/queue/${entryA}/complete`, { token: docToken });
record('D9. Complete → COMPLETED + completedAt', r.status === 200 && r.json?.status === 'COMPLETED' && !!r.json?.completedAt, `status=${r.status}`);

r = await api('PUT', `/api/queue/${entryA}/call-next`, { token: docToken });
record('D10. Call-next on non-WAITING entry → 400', r.status === 400, `status=${r.status}`);

// ─────────────────────────── PATIENT HISTORY / CANCEL ───────────────────────────
r = await api('GET', '/api/queue/my-history', { token: tokenALogin });
const history = r.json || [];
record('P11. Patient A history shows COMPLETED visit', r.status === 200 && history.some((e) => e.id === entryA && e.status === 'COMPLETED'), `status=${r.status} entries=${history.length}`);

r = await api('PUT', `/api/queue/${entryB}/cancel`, { token: tokenB });
record('P12. Patient B cancels own entry → CANCELLED', r.status === 200 && r.json?.status === 'CANCELLED', `status=${r.status}`);

r = await api('GET', '/api/queue/my-status', { token: tokenB });
record('P13. Cancelled patient no longer active', r.status === 200 && r.json?.active === false, `status=${r.status}`);

r = await api('PUT', `/api/queue/${entryA}/cancel`, { token: tokenB });
record('P14. Patient B cannot cancel patient A\u2019s entry → 403', r.status === 403, `status=${r.status}`);

// ─────────────────────────── ADMIN JOURNEY ───────────────────────────
r = await api('POST', '/api/auth/login', { body: { email: 'admin@careq.com', password: 'admin123' } });
record('A1. Admin login', r.status === 200 && !!r.json?.token, `status=${r.status}`);
const adminToken = r.json?.token;

r = await api('GET', '/api/queue/live', { token: adminToken });
record('A2. Admin live queue overview → 200', r.status === 200 && Array.isArray(r.json?.doctors), `status=${r.status} doctors=${r.json?.doctors?.length} waiting=${r.json?.totalWaiting}`);

r = await api('GET', '/api/queue/analytics/summary', { token: adminToken });
record('A3. Admin analytics summary → 7-day window', r.status === 200 && (r.json?.patientsPerDay || []).length === 7, `status=${r.status} days=${r.json?.patientsPerDay?.length}`);

const deptName = `Manual Dept ${ts}`;
r = await api('POST', '/api/departments', { token: adminToken, body: { name: deptName } });
record('A4. Admin creates department → 201', r.status === 201 && r.json?.name === deptName, `status=${r.status}`);
const deptId = r.json?.id;

r = await api('PUT', `/api/departments/${deptId}`, { token: adminToken, body: { name: `${deptName} v2` } });
record('A5. Admin updates department → 200', r.status === 200 && r.json?.name === `${deptName} v2`, `status=${r.status}`);

r = await api('DELETE', `/api/departments/${deptId}`, { token: adminToken });
record('A6. Admin deletes department → 204', r.status === 204, `status=${r.status}`);

r = await api('GET', '/api/users?size=20', { token: adminToken });
record('A7. Admin user list → 200 page', r.status === 200 && Array.isArray(r.json?.content), `status=${r.status}`);

r = await api('GET', `/api/users/${docUserId}`, { token: adminToken });
record('A8. Admin reads any user profile → 200', r.status === 200, `status=${r.status}`);

r = await api('GET', '/api/users?size=20', { token: tokenALogin });
record('A9. Patient cannot list users → 403', r.status === 403, `status=${r.status}`);

// ─────────────────────────── SECURITY / EDGE PATHS ───────────────────────────
r = await api('GET', '/api/doctors?size=5', { token: 'not-a-real-jwt' });
record('S1. Invalid JWT rejected → 401', r.status === 401, `status=${r.status}`);

r = await api('GET', '/api/queue/live', { token: tokenALogin });
record('S2. Patient blocked from admin live overview → 403', r.status === 403, `status=${r.status}`);

r = await api('GET', '/api/auth/nonexistent-path', { token: tokenALogin });
record('S3. Unknown route handling (expect 404)', r.status === 404, `status=${r.status} (observed)`);

r = await api('GET', '/api/departments', { token: adminToken });
record('S4. Departments still browsable → 200', r.status === 200 && Array.isArray(r.json), `status=${r.status}`);

// ─────────────────────────── SUMMARY ───────────────────────────
console.log('\n==================================================');
console.log(` RESULT: ${ok} passed / ${fail} failed / ${results.length} total`);
console.log('==================================================');
const fs = await import('node:fs');
fs.writeFileSync('/tmp/manual-test-results.json', JSON.stringify({ date: new Date().toISOString(), ok, fail, results }, null, 2));
process.exit(fail === 0 ? 0 : 1);

#!/usr/bin/env node
/**
 * One-off demo-data repair.
 * The auth DB was re-created after the catalog was seeded, orphaning every
 * catalog entry (userId points at a deleted account). This script:
 *  1. ensures the 10 seed departments exist (creates any missing by name),
 *  2. deletes orphaned catalog entries,
 *  3. creates fresh entries for the current seed doctors.
 */
const BASE = process.env.API_BASE || 'http://localhost:8080';

// Seed doctor → department NAME (positions match scripts/seed-data.sh).
const SEED_DOCTORS = [
  { name: 'Dr. Arjun Sharma', email: 'dr.arjun@careq.com', dept: 'Cardiology', spec: 'Interventional Cardiology', qual: 'MD, DM Cardiology', exp: 15, fee: 800, avg: 20 },
  { name: 'Dr. Priya Patel', email: 'dr.priya@careq.com', dept: 'Cardiology', spec: 'Pediatric Cardiology', qual: 'MD, DM Pediatrics Cardiology', exp: 10, fee: 600, avg: 15 },
  { name: 'Dr. Vikram Reddy', email: 'dr.vikram@careq.com', dept: 'Neurology', spec: 'Stroke Neurology', qual: 'MD, DM Neurology', exp: 20, fee: 1000, avg: 25 },
  { name: 'Dr. Ananya Singh', email: 'dr.ananya@careq.com', dept: 'Orthopedics', spec: 'Joint Replacement Surgery', qual: 'MS Orthopedics', exp: 12, fee: 750, avg: 20 },
  { name: 'Dr. Rajesh Kumar', email: 'dr.rajesh@careq.com', dept: 'Pediatrics', spec: 'Neonatology', qual: 'MD Pediatrics', exp: 8, fee: 500, avg: 15 },
  { name: 'Dr. Meera Iyer', email: 'dr.meera@careq.com', dept: 'Dermatology', spec: 'Cosmetic Dermatology', qual: 'MD Dermatology', exp: 6, fee: 600, avg: 15 },
  { name: 'Dr. Suresh Nair', email: 'dr.suresh@careq.com', dept: 'Ophthalmology', spec: 'Cataract Surgery', qual: 'MS Ophthalmology', exp: 18, fee: 700, avg: 20 },
  { name: 'Dr. Deepa Menon', email: 'dr.deepam@careq.com', dept: 'ENT (Otorhinolaryngology)', spec: 'Head & Neck Surgery', qual: 'MS ENT', exp: 14, fee: 650, avg: 20 },
  { name: 'Dr. Karthik Joshi', email: 'dr.karthik@careq.com', dept: 'Gastroenterology', spec: 'Hepatology', qual: 'MD, DM Gastroenterology', exp: 11, fee: 850, avg: 25 },
  { name: 'Dr. Lakshmi Rao', email: 'dr.lakshmi@careq.com', dept: 'Pulmonology', spec: 'Sleep Medicine', qual: 'MD Pulmonology', exp: 9, fee: 550, avg: 15 },
];

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
  try { json = await res.json(); } catch { /* ignore */ }
  return { status: res.status, json };
}

// 1. Admin login
let r = await api('POST', '/api/auth/login', { body: { email: 'admin@careq.com', password: 'admin123' } });
if (r.status !== 200) { console.error('Admin login failed'); process.exit(1); }
const admin = r.json.token;

// 2. Ensure all seed departments exist (by name).
r = await api('GET', '/api/departments', { token: admin });
const depts = r.json || [];
const deptIdByName = new Map(depts.map((d) => [d.name, d.id]));
for (const d of SEED_DOCTORS) {
  if (!deptIdByName.has(d.dept)) {
    r = await api('POST', '/api/departments', { token: admin, body: { name: d.dept, description: `Seeded: ${d.dept}` } });
    if (r.status === 201) { deptIdByName.set(d.dept, r.json.id); console.log(`  created department: ${d.dept} (#${r.json.id})`); }
    else console.log(`  FAILED create department ${d.dept} -> ${r.status}`);
  }
}
console.log(`Departments ready: ${deptIdByName.size}`);

// 3. Current doctor userIds (login each seed doctor).
const current = new Map();
for (const d of SEED_DOCTORS) {
  r = await api('POST', '/api/auth/login', { body: { email: d.email, password: 'password123' } });
  if (r.status === 200) current.set(r.json.userId, d);
  else console.log(`  skip (no account): ${d.email}`);
}

// 4. Current catalog — delete orphaned, create missing.
r = await api('GET', '/api/doctors?size=100', { token: admin });
const entries = r.json?.content || [];
console.log(`Catalog before: ${entries.length} entries, current users: ${current.size}`);

let deleted = 0;
let created = 0;
const usedIds = new Set();

for (const e of entries) {
  if (current.has(e.userId)) {
    usedIds.add(e.userId);
  } else {
    r = await api('DELETE', `/api/doctors/${e.id}`, { token: admin });
    if (r.status === 204) { deleted++; console.log(`  deleted orphan entry #${e.id}`); }
    else console.log(`  FAILED delete #${e.id} -> ${r.status}`);
  }
}

for (const [userId, d] of current) {
  if (usedIds.has(userId)) continue;
  r = await api('POST', '/api/doctors', {
    token: admin,
    body: {
      name: d.name,
      userId,
      departmentId: deptIdByName.get(d.dept),
      specialization: d.spec,
      qualification: d.qual,
      experienceYears: d.exp,
      consultationFee: d.fee,
      avgConsultationTimeMinutes: d.avg,
    },
  });
  if (r.status === 201) { created++; console.log(`  created entry for ${d.name}`); }
  else console.log(`  FAILED create ${d.name} -> ${r.status} ${JSON.stringify(r.json)}`);
}

console.log(`Catalog after: deleted=${deleted} created=${created}`);

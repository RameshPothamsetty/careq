# CareQ — Prompt Archive

## Day 1: Project Initialization

_Not archived — project scaffolding prompt._

---

## Day 2: Authentication Module

_Not archived — authentication module prompt._

---

## Day 3: User Module

**Prompt:** CareQ — Day 3 Prompt (User Module)

**Date Executed:** 2026-07-29

**Branch:** `feature/user-module`

**Summary:** Built user-service with lazy profile creation pattern. Created UserProfile entity, DTOs, repository, service (with lazy-create on first GET /me), controller (GET/PUT /me for own profile, GET /{id} admin-only), validation, and global exception handler. Added React ProfilePage with view/edit form, API service functions, and navigation links from all dashboards. Updated gateway routing (pre-existing), database docs, architecture docs, and testing docs.

---

---

## Day 4: Doctor / Department Module

**Prompt:** CareQ — Day 4 Prompt (Doctor / Department Module)

**Date Executed:** 2026-07-30

**Branch:** `feature/doctor-service`

**Summary:** Built doctor-service with Department and DoctorCatalogEntry entities, Admin CRUD for both, public doctor browsing with filters (departmentId, specialization), doctor availability toggle via header-based identity. Created docs/05_API_CONTRACT.md with full API contract. Updated React: AdminDashboard with management cards, AdminDepartmentManager (CRUD), AdminDoctorManager (CRUD), PatientDoctorBrowser (filterable listing), DoctorDashboard (availability toggle). Updated database (departments + doctor_catalog_entries tables), architecture (service responsibility), and testing docs.

## Full Prompt Text

```
<insert Day 4 prompt text here — paste the full prompt as given>
```

---

## Day 5: Queue Module + AI (Wait-Time Prediction & Symptom Triage)

**Prompt:** CareQ — Day 5 Prompt (Queue Module + AI)

**Date Executed:** 2026-07-31

**Branch:** `feature/queue-service`

**Summary:** Built queue-service with AI-driven wait-time prediction and symptom triage — the flagship AI-differentiated module.

**Backend (queue-service, port 8084):**
- `QueueEntry` entity (`patientId`, `doctorCatalogEntryId`, `symptomText`, `aiSuggestedTriage`, `doctorOverrideTriage` nullable, `status` WAITING/IN_PROGRESS/COMPLETED/CANCELLED, `joinedAt`, `calledAt`, `completedAt`)
- `DoctorServiceClient` — Feign client to doctor-service via Eureka (single source of truth for `avgConsultationTimeMinutes`/`isAvailable`; no data duplication)
- `GroqTriageAiClient` + `AiTriageService` — Groq `llama-3.1-8b-instant` chat-completions triage with guaranteed fallback-to-NORMAL on any failure (timeout, bad key, 5xx, unparseable)
- `QueueOrderingService` — effective ordering (EMERGENCY > HIGH > NORMAL > FOLLOW_UP, then FIFO) and wait-time formula `(patients ahead) x avgConsultationTimeMinutes`, recalculated on every read
- Endpoints: join, my-status, doctor/{id}, {id}/override-triage, {id}/call-next, {id}/complete, live (admin)
- Gateway route `/api/queue/**` → queue-service (already present, verified)
- 19 unit tests covering ordering, wait-time, AI fallback-to-NORMAL (mocked client failure), and override logic

**Frontend (Tailwind CSS added):**
- Patient `My Queue` page — join flow (pick doctor + symptoms) and the flagship live status view (big position + wait, triage badge, pulse animation, last-updated indicator, 10s polling, joined→called→completed progress)
- Doctor `Live Patient Queue` page — color-coded triage badges, override-triage control, call-next/complete buttons, live stats
- Admin `Live Queue Overview` — summary cards (total waiting, doctors online, delayed, avg wait) + per-doctor breakdown table
- All screens handle loading / empty / error / populated states; Tailwind configured with a calm healthcare palette

**Docs:** API contract (7 queue endpoints), database (`queue_entries`), architecture (Feign inter-service call + AI integration diagram), testing (queue module), README.

## Full Prompt Text

```
CareQ — Day 5 Prompt (Queue Module + AI: Wait-Time Prediction & Symptom Triage)

Save as the fifth entry in docs/11_PROMPTS.md. Paste everything below the --- into Freebuff exactly as-is. Assumes: Days 1-4 merged into develop (auth, user profiles, doctor/department catalog all working, avgConsultationTimeMinutes populated on doctor catalog entries).

ROLE
You are acting as a Senior Software Architect and Full Stack Engineer continuing Day 5 of a 15-day project under the TrainingMug ADF v1.0 framework. Today is the core AI-differentiated module: queue-service. This is the most important day of the project — everything before it was infrastructure; this is where the product's actual value proposition gets built. Do not touch auth-service, user-service, or doctor-service — read from doctor-service only via the inter-service call described below, never modify it.

PROJECT CONTEXT (recap)
Project: CareQ — Intelligent Patient Flow Platform
Roles: PATIENT, DOCTOR, ADMIN
Identity propagation: X-User-Id / X-User-Role headers forwarded by api-gateway, as used since Day 3
What exists already: doctor-service has a DoctorCatalogEntry per doctor with avgConsultationTimeMinutes, departmentId, isAvailable

DESIGN DECISIONS FOR TODAY — read before generating anything
1. Inter-service communication via Feign, not data duplication. queue-service must fetch a doctor's avgConsultationTimeMinutes and current queue load by calling doctor-service through a declarative Feign client (registered via Eureka, not a hardcoded URL). Do not copy this field into queue-service's own tables.
2. AI symptom triage must have a fallback. Call the Groq API (OpenAI-compatible chat completions format, endpoint https://api.groq.com/openai/v1/chat/completions) with the patient's free-text symptoms, and get back one of EMERGENCY, HIGH, NORMAL, FOLLOW_UP. Use model llama-3.1-8b-instant. Read the API key from an environment variable named GROQ_API_KEY, never hardcode it. If the call fails, times out, or returns something unparseable, default to NORMAL, log the failure, and do not block the patient from joining the queue.
3. Doctor override is final. The AI's triage level is a suggestion stored alongside a doctor-settable override field. Whichever is set (override if present, otherwise AI's value) determines actual queue ordering.
4. Queue ordering logic: sort primarily by effective triage level (EMERGENCY > HIGH > NORMAL > FOLLOW_UP), and within the same level, FIFO by joinedAt. Recompute this ordering whenever a new patient joins, a triage level is overridden, or a patient is marked complete.
5. Wait-time prediction formula: predictedWaitMinutes = (number of patients ahead in effective queue order) × doctor's avgConsultationTimeMinutes, recalculated on every read of GET /api/queue/my-status — not cached statically.
6. Polling only, no WebSockets today — React should poll GET /api/queue/my-status every ~10 seconds.

TODAY'S DELIVERABLES (Day 5 — Queue Module + AI)
- QueueEntry entity: patientId, doctorCatalogEntryId, symptomText, aiSuggestedTriage, doctorOverrideTriage (nullable), status (WAITING / IN_PROGRESS / COMPLETED / CANCELLED), joinedAt, calledAt, completedAt
- AiTriageService — wraps the LLM call, includes the fallback-to-NORMAL logic, isolated so it can be unit-tested with a mocked AI client
- DoctorServiceClient — Feign client interface for calling doctor-service (fetch avgConsultationTimeMinutes, verify isAvailable)
- POST /api/queue/join, GET /api/queue/my-status, GET /api/queue/doctor/{doctorCatalogEntryId}, PUT /api/queue/{id}/override-triage, PUT /api/queue/{id}/call-next, PUT /api/queue/{id}/complete, GET /api/queue/live (admin)
- Validation + exception handling
- api-gateway route update: /api/queue/** → queue-service
- React: Patient Join Queue flow + live status view (polling), Doctor live queue view (triage badges, override, call-next/complete), Admin live overview dashboard (summary cards)
- docs/05_API_CONTRACT.md, docs/04_DATABASE.md (queue_entries), docs/03_ARCHITECTURE.md (Feign + AI integration), docs/09_TESTING.md (ordering, wait-time, AI fallback, override)

Explicitly OUT of scope today: load balancing across doctors, WebSocket real-time push, standalone AI assistant chat, notification delivery (email/SMS), any changes to auth-service/user-service/doctor-service beyond reading via Feign.

UI/UX QUALITY BAR — Tailwind CSS, calm healthcare palette (soft blues/teals + white), triage color-coded badges (EMERGENCY=red, HIGH=orange, NORMAL=blue/green, FOLLOW_UP=gray), live queue status screen must feel alive (large position + wait, last-updated indicator, pulse animation), every screen handles loading/empty/error/populated states, doctor view looks like a real clinical dashboard, admin overview uses summary cards, responsive by default, disabled-state styling during API calls, toast/inline confirmations.

GIT WORKFLOW: pull develop → branch feature/queue-service → build only today's scope → test locally (including the AI-failure fallback path) → commit in small increments → push → open PR (feature/queue-service → develop) titled "Day 5: Queue Module + AI (Wait-Time Prediction & Symptom Triage)" → self-review the diff against the Definition of Done → merge only after review.

HARD CONSTRAINTS: ADF Section 8 (no business logic in controllers, DTOs only, no hardcoded values), state all assumptions before generating code, read GROQ_API_KEY strictly from environment configuration (application.yml referencing ${GROQ_API_KEY}), do not implement anything from the out-of-scope list, stop after producing the listed files and docs.

VERIFICATION CHECKLIST: confirm each of the 17 Day 5 deliverables is addressed, output the exact git command sequence, output a ready-to-paste PR description, output the filled-in Definition of Done checklist, output the exact prompt text sent to Groq, and confirm GROQ_API_KEY is read from environment configuration only.
```

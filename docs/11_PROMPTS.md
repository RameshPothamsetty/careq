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

---

## Day 7a: Week 1 Stabilization & v0.1 Release

**Prompt:** CareQ — Day 7a Prompt (Week 1 Demo: Stabilization & v0.1 Release)

**Date Executed:** 2026-08-04

**Branch:** `chore/week1-stabilization`

**Summary:** Stabilization pass before the v0.1 tag. Set up GitHub collaboration artifacts (PR template, day-task issue template; labels/issues/project board created manually — `gh` CLI unavailable in session). Bug-fix pass across all three roles. Filled completeness gaps: pagination + sorting + search on `GET /api/doctors`, new paginated admin `GET /api/users` list, patient-name search on `GET /api/queue/doctor/{id}`, patient/doctor name plumbing (JWT `fullName` claim → `X-User-Name`/`X-User-Email` headers → profile columns; `name` added to doctor catalog; `patientName` captured at queue join). Code review pass (RoleGuard extraction, typed `DepartmentNotFoundException`, dead repository methods removed). Full README rewrite with Development Process section. Merged into `develop`; `develop → main` merge + `v0.1` tag deferred pending explicit confirmation.

## Full Prompt Text

```
CareQ — Day 7a Prompt (Week 1 Demo: Stabilization & v0.1 Release)

Save as the seventh entry in docs/11_PROMPTS.md.
Run this FIRST today, before the Advanced Features prompt (Day 7b) — advanced features should build on a stable, tagged base, not the other way around.
Freebuff has direct git access in this session — it should execute the git commands itself, not just print them, EXCEPT the final `develop → main` merge and tag, which requires your explicit go-ahead (see Git Workflow section).

ROLE
You are acting as a Senior Software Engineer doing a stabilization pass on an existing, working application (CareQ, Days 1-6 already built and merged into `develop`). Today is not about new features — it's about finding and fixing what's rough, filling small completeness gaps (pagination/search/sorting), and shipping a genuinely stable, demo-ready checkpoint.

PROJECT CONTEXT (recap)
- Project: CareQ — Intelligent Patient Flow Platform
- Current state: Auth, User/Profile, Doctor/Department catalog, Queue + AI triage/wait-prediction, and Frontend Integration (Context + RTK Query, route guards) all built and merged into `develop`
- You have live git access — inspect the actual current codebase (git log, file contents) rather than assuming what exists; earlier days' output may have drifted slightly from what was originally specified

TODAY'S DELIVERABLES
0. Collaboration & contribution setup (do this first — everything else today plugs into it)
- Retroactive Issues for Days 1-6: one per day, title `Day N: <Module Name>`, body = that day's deliverable list as a checked-off checklist, labeled day-N + type label; close each, linked to its merged PR where possible.
- Today's own Issue: `Day 7: Week 1 Stabilization & v0.1 Release`, referenced in the PR (`Closes #<n>`).
- Labels: backend, frontend, infra, ai, docs, bug, enhancement, day-1..day-15.
- GitHub Project board: `CareQ — 15-Day Build` (Backlog/In Progress/In Review/Done), all Day 1-15 issues, completed days in Done.
- `.github/PULL_REQUEST_TEMPLATE.md` and `.github/ISSUE_TEMPLATE/day-task.md`.
1. Bug fixing pass — run/trace the app across all three roles and all screens; list every bug found and fixed explicitly.
2. Pagination, search, sorting: `GET /api/doctors` page/size + sortBy(name, consultationFee, experienceYears)/sortDirection; NEW admin `GET /api/users` paginated + searchable by name/email; `GET /api/queue/doctor/{id}` search by patient name; frontend RTK hooks + screens (Admin doctor list, new Admin user list, Doctor's live queue).
3. Code review pass against ADF Section 8 (layered architecture, DTOs only, no business logic in controllers, no hardcoded values, constructor injection); flag and fix violations, list them explicitly.
4. README.md full rewrite: pitch, problem statement, role-grouped feature list, tech stack table, architecture link, local setup incl. GROQ_API_KEY, demo/screenshot placeholder, Project Status (15 days), Development Process section with links to the Project board and closed-Issues list.

Explicitly OUT of scope today: dashboard widgets/charts/analytics, notifications, any new feature beyond pagination/search/sorting.

GIT WORKFLOW: pull develop → branch chore/week1-stabilization → commit in small increments per fix (fix:/feat:/refactor:/docs:) → push → PR (Closes #today's issue) → self-review → merge into develop → STOP: do NOT merge develop→main or tag v0.1 without explicit confirmation; output the exact commands and wait.

Definition of Done: retroactive issues; today's issue + PR reference; labels/board/templates; every bug listed + fixed; pagination+sorting on doctors; admin user list; patient search on doctor queue; code review findings addressed; README fully rewritten; all existing tests passing.

HARD CONSTRAINTS: no out-of-scope features; no main merge or v0.1 tag without confirmation; state assumptions (pagination default page size, what counts as a bug) before making changes.

VERIFICATION CHECKLIST: retroactive issues (numbers/titles) + board URL + labels + templates; full bug list; full code-review findings; pagination/search/sorting confirmed with example request/response; README rewrite confirmed incl. Development Process; exact main merge + tag commands marked as not yet executed.
```

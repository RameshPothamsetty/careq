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

---

## Day 7b: Advanced Features (Analytics Dashboard + Notifications)

**Prompt:** CareQ — Day 7b Prompt (Advanced Features: Dashboard Widgets, Charts, Notifications)

**Date Executed:** 2026-08-04

**Branch:** `feature/advanced-features`

**Summary:** Built on the stable v0.1 base. Added `GET /api/queue/analytics/summary` (Admin-only) in queue-service — native MySQL `GROUP BY DATE(...)` aggregation for patients-handled-per-day and avg-wait-time-trend over the last 7 days, plus department distribution joined via the existing Feign doctor client. New Admin Analytics dashboard (Recharts: bar + line + pie, StatCards, loading/empty/error states). Client-side derived notifications: `useQueueNotifications` hook detects status transitions from the existing 10s my-status polling (WAITING→IN_PROGRESS "called", position moved up, joined, completed), surfaced via a notification bell with unread badge + dropdown in the shared header and auto-dismissing toasts. Persisted/push notifications explicitly scoped as Phase 2. Merged into `develop` (no main merge / no tag today, per prompt).

## Full Prompt Text

```
CareQ — Day 7b Prompt (Advanced Features: Dashboard Widgets, Charts, Notifications)

Save as the eighth entry in docs/11_PROMPTS.md.
Run this SECOND today, after Day 7a's stabilization work is merged and v0.1 is tagged (confirm this happened before running this prompt).
Freebuff has direct git access — execute git commands directly per the workflow below.

ROLE
You are acting as a Senior Full Stack Engineer adding advanced features on top of a now-stable v0.1 base. Today's checklist item is "Dashboard widgets, charts/reports, file upload or notifications" — three honest scoping decisions are made below so this is actually finishable in one session rather than three half-built features.

PROJECT CONTEXT (recap)
- Project: CareQ — Intelligent Patient Flow Platform
- Base: v0.1 — stable, tested, tagged. Auth, User/Profile (including file upload — see note below), Doctor/Department, Queue + AI, Frontend Integration all working.

SCOPING DECISIONS FOR TODAY — read before generating anything
1. File upload is already done. Profile picture upload with real storage and serving was built ahead of schedule on Day 3. Do not rebuild this — today's "advanced features" work covers the other two: dashboard widgets/charts and notifications.
2. Charts/reports → Admin Analytics Dashboard. Build one new GET /api/queue/analytics/summary endpoint (Admin-only, in queue-service) that aggregates real data already in the queue_entries table: patients handled per day (last 7 days), average wait time trend (last 7 days), and queue distribution by department (join with doctor-service via the existing Feign client). Render with Recharts on the frontend.
3. Notifications → in-app, derived, not a new persisted system. Rather than building a full notification storage service (a genuinely large addition better suited to a dedicated day), implement client-side derived notifications: since the frontend already polls queue status via RTK Query (Day 6), detect state transitions (e.g., status flips from WAITING to IN_PROGRESS) and surface a toast notification + a small notification bell with a short-lived history of recent events for that session. State clearly in your output that persisted, cross-device, or backend-triggered notifications (push/SMS/email) are a Phase 2 roadmap item, not built today — this is an honest scope boundary, not a shortcut to hide.

TODAY'S DELIVERABLES
0. Create today's Issue first: `Day 7: Advanced Features (Analytics Dashboard + Notifications)`, labeled day-7, backend, frontend, enhancement. Add to the CareQ — 15-Day Build Project board under In Progress. Reference in today's PR with Closes #<issue number>.
1. Admin Analytics Dashboard (backend): GET /api/queue/analytics/summary — Admin-only, in queue-service. Response: patientsPerDay (last 7 days, date + count), avgWaitTimeTrend (last 7 days, date + avg minutes), departmentDistribution (department name + patient count, via Feign). Appropriate database queries (JPQL/native — don't pull all rows into memory). Validation + exception handling consistent with existing services.
2. Admin Analytics Dashboard (frontend): new Admin screen using Recharts (bar/line for patients per day; line for avg wait; pie or bar for department distribution). New RTK Query endpoint on queueApi. Same loading/error/empty state discipline — empty chart shows friendly empty state, not broken/blank. Use StatCard for summary numbers above charts.
3. In-app notifications (frontend only): bell icon in the shared navigation header with unread-count badge; dropdown/panel with recent session events; toast fires immediately on detected status change via existing polling; logic in a small isolated hook (e.g., useQueueNotifications) watching the relevant RTK Query data. Explicitly note: history is client-side and session-only — resets on refresh, no backend store.
4. Documentation: 05_API_CONTRACT.md (new endpoint), 03_ARCHITECTURE.md (analytics aggregation approach + client-side notification design decision + Phase 2 persisted/push notifications roadmap note), README.md (feature list).

Explicitly OUT of scope today: persisted notification storage, push notifications, email/SMS; any rebuild of file upload (already done); any change to AI triage or wait-time prediction logic.

GIT WORKFLOW: pull develop → branch feature/advanced-features → build + test (analytics endpoint must return sensible data even with a small/sparse dataset — don't let empty-data edge case crash the endpoint) → commit in small increments (feat: add queue analytics summary endpoint / feat: add Admin analytics dashboard with Recharts / feat: add client-side derived queue notifications / feat: add notification bell and toast UI / docs: document analytics endpoint and notification design decisions) → push → PR (feature/advanced-features → develop, title "Day 7: Advanced Features (Analytics Dashboard + Notifications)", Closes #<today's issue number>) → self-review (charts render with real data; notifications fire on real status change by manually calling call-next/complete as a doctor while watching the patient view) → merge into develop. This branch does NOT get merged into main or tagged today.

HARD CONSTRAINTS: no persisted/backend notification storage; no touching AI triage or wait-time prediction logic; state all assumptions (chart library config, exact status-transition events tracked for notifications, analytics date-range default) before generating code.

VERIFICATION CHECKLIST: confirm each of the 4 deliverable groups addressed; analytics endpoint tested against both populated and sparse data; notifications tested against a real status transition; exact git command sequence output; ready-to-paste PR description.
```

---

## Day 9: API Documentation (Swagger/OpenAPI, Postman, Error Consistency)

**Prompt:** CareQ — Day 9 Prompt (API Documentation: Swagger/OpenAPI, Postman, Error Consistency)

**Date Executed:** 2026-08-05

**Branch:** `feature/api-documentation`

**Summary:** API documentation & consistency pass across all four business services (no new business features).

**Deliverables completed:**
1. **Swagger/OpenAPI completeness** — added `springdoc-openapi-starter-webmvc-ui` 2.3.0 (managed centrally in the parent POM) to auth/user/doctor/queue services; `@Tag` on every controller, `@Operation` + `@ApiResponse` (schema-ref'd bodies, real examples on flagship endpoints) on every endpoint, `@Schema` with examples on every request/response DTO; per-service `OpenApiConfig` with a global HTTP Bearer security scheme (user/doctor/queue) so Swagger UI shows a working Authorize button.
2. **Centralized Swagger UI at the gateway** — gateway routes `/v3/api-docs/{service}` → each service (RewritePath) + `springdoc.swagger-ui.urls`; single UI at `http://localhost:8080/swagger-ui.html`. Swagger/OpenAPI paths whitelisted in the gateway JWT filter and auth-service SecurityConfig. OpenAPI `servers` set to relative `/` so Try-it-out resolves against the gateway origin.
3. **Consistent error shape** — standardized `ErrorResponseDto` to `{ timestamp, status, error, message, path, validationErrors[{field,message}] }` in all 4 services (moved auth's copy out of the `dto` package; new `ValidationError` class per service); every `GlobalExceptionHandler` now injects `HttpServletRequest` to populate `path` and returns `validationErrors` on 400s; **inconsistencies fixed:** doctor-service duplicate catalog entry 400→409 (duplicates are 409 everywhere); auth DTO in wrong package. Frontend error normalizers updated for the new shape.
4. **API contract audit** — docs/05_API_CONTRACT.md bumped to v1.8; documented previously missing endpoints (GET/PUT `/api/users/me`, profile-picture upload/serve, `GET /api/users/{id}`, `GET /api/queue/my-history`, `GET /api/queue/doctor/{id}/analytics`, `PUT /api/queue/{id}/cancel`, health endpoints) and updated all error examples to the shared shape.
5. **Postman collection** — `postman/CareQ.postman_collection.json` (v2.1, folders per service, 28 requests) + `postman/CareQ.postman_environment.json` (`baseUrl`, `authToken` + path variables); Login request test script auto-extracts the JWT into `{{authToken}}`; example success + error responses saved.
6. **README** — Swagger UI + Postman import instructions and Day 9 status row.

**Verification:** all backend modules compile; `mvn test` green (23 queue-service tests + Mockito fallback-path tests); frontend `tsc --noEmit` clean; Postman JSONs validated with Node; aggregated Swagger UI opened live and Try-it-out exercised per service (see session notes). `gh` CLI unavailable → Issue/PR created manually by the user.

## Full Prompt Text

```
CareQ — Day 9 Prompt (API Documentation: Swagger/OpenAPI, Postman, Error Consistency)

Save as the ninth entry in docs/11_PROMPTS.md. Paste everything below the --- into Freebuff exactly as-is. Assumes: Days 1-8 merged into develop, v0.1 tagged on main, all 6 services and the frontend working end-to-end. Freebuff has direct git access — execute git/GitHub commands directly per the workflow below.

ROLE
You are acting as a Senior Backend Engineer doing an API documentation and consistency pass across an existing, working application (CareQ). Today is not about new features — it's about making the API layer genuinely professional: complete, consistent, and easy for anyone (a teammate, an evaluator, a future you) to pick up and use without reading source code.

PROJECT CONTEXT (recap)
- Services: eureka-server, api-gateway, auth-service, user-service, doctor-service, queue-service
- Current state: each service likely has partial or inconsistent Swagger annotations, since documentation quality wasn't the focus of Days 2-8. Error response shapes may have drifted between services since each day's GlobalExceptionHandler was written somewhat independently.

TODAY'S DELIVERABLES
0. Create today's Issue first: Day 9: API Documentation as a GitHub Issue, labeled day-9, docs, backend, on the CareQ — 15-Day Build board, referenced from the PR with Closes #<issue>.
1. Swagger/OpenAPI completeness pass — every business service (auth, user, doctor, queue): springdoc configured, @Tag on controllers, @Operation + @ApiResponse for every real status code, @Schema on request DTOs with examples, correct security-scheme annotations so the Authorize lock icon works.
2. Centralized Swagger UI via the Gateway — aggregate all services' OpenAPI docs behind one browsable Swagger UI (springdoc gateway aggregation, /v3/api-docs/{service}). One URL (http://localhost:8080/swagger-ui.html), demoable live in an interview.
3. Consistent error response shape — one shared ErrorResponse DTO (timestamp, status, error, message, path, optional validationErrors list of field+message pairs for 400s); audit every GlobalExceptionHandler, list and fix every inconsistency; confirm status codes consistent (duplicate resource = 409 everywhere).
4. docs/05_API_CONTRACT.md — audit endpoint by endpoint against the real implementation; fix drift and list every discrepancy found and corrected.
5. Postman collection — single v2.1 collection covering every endpoint across the 4 business services, folders by service, environment file with baseUrl + authToken, a Login request whose test script auto-extracts the JWT into authToken, example bodies and saved example responses (success + one error per service). Export both JSONs to postman/.
6. Sanity-check the collection (Newman if available; otherwise confirm the token-extraction script manually in Postman).
7. README.md update — links to the centralized Swagger UI URL and the Postman collection location with a one-line import instruction.

Explicitly OUT of scope today: new business features/endpoints, unit/integration testing (Day 10), changes to eureka-server's own docs.

GIT WORKFLOW: pull develop → branch feature/api-documentation → build + test locally, actually open the aggregated Swagger UI and Try it out on one endpoint per service → commit in small increments (feat: OpenAPI annotations per service, feat: gateway Swagger aggregation, fix: standardized error shape, docs: contract audit, docs: Postman collection, docs: README links) → push → PR feature/api-documentation → develop titled "Day 9: API Documentation" with Closes #<issue> → self-review (import the collection and run Login to verify the auto-auth script) → merge into develop. Not merged into main/tagged today.

HARD CONSTRAINTS: no new business logic or endpoints; do not skip services; state assumptions (springdoc aggregation approach, exact ErrorResponse field names) before making changes.

VERIFICATION CHECKLIST: Issue/board/PR linkage; every error-response inconsistency found and fixed (service by service); every API-contract drift found and corrected; centralized Swagger UI URL confirmed tested, not just configured; auto-auth script verified (Newman or manual); exact git command sequence; ready-to-paste PR description.
```

---

## Day 10: Testing (Unit, Integration, API, UI, Manual)

## Full Prompt Text

```
CareQ — Day 10 Prompt (Testing: Unit, Integration, API, UI, Manual)

Save as the tenth entry in docs/11_PROMPTS.md. Paste everything below the --- into Freebuff exactly as-is. Assumes: Days 1-9 merged into develop, v0.1 tagged, all 6 services + frontend + Swagger/Postman documentation working. Run this BEFORE Day 11 (Docker) — you want confidence in what you're containerizing, not the other way around. You'll run the git commands yourself this time (good for actually learning the workflow) — Freebuff should output each command clearly, explain what it does in one line, and tell you what to check before moving to the next step, rather than executing them for you.

ROLE
You are acting as a Senior QA-minded Backend/Frontend Engineer running a focused testing pass across CareQ. Today's checklist item spans unit, integration, API, UI, and manual testing — given time constraints (two full checklist days are being completed today, this and Day 11), scope is prioritized, not exhaustive. Read the scoping decision below before writing anything.

SCOPING DECISION FOR TODAY
Full, exhaustive test coverage across every class in every service is not realistic in one session alongside tomorrow's Docker work. Prioritize in this order:
1. Highest-risk logic gets the deepest testing: AiTriageService's fallback behavior, JWT generation/validation, queue priority ordering, wait-time calculation
2. Every service gets solid but not exhaustive unit coverage of its service layer
3. 2-3 critical end-to-end integration flows, not every possible flow
4. A light frontend test pass on a few key components, not full coverage
5. Full E2E browser automation (Cypress/Playwright) is explicitly Phase 2 roadmap — not built today, state this clearly in docs

TODAY'S DELIVERABLES
0. Create today's Issue first: Day 10: Testing (Unit, Integration, API, UI, Manual) as a GitHub Issue, checklist from below, labeled day-10, backend, frontend, bug. Add to the Project board under In Progress. Reference with Closes #<issue number> in today's PR.
1. Unit tests — backend, per service: auth-service (signup success, duplicate email rejection, login success, wrong password, JWT valid, JWT expired/tampered), user-service (lazy profile creation, update validation, Admin-only restriction on GET /api/users/{id}), doctor-service (catalog CRUD, duplicate catalog entry per userId rejected, department filter, pagination/sorting), queue-service (thorough: AiTriageService success + explicit fallback path — failed/timed-out/unparseable AI returns NORMAL without throwing; wait-time calculation; queue ordering + re-ordering after override; Feign failure handling — doctor-service unreachable must not be an unhandled 500).
2. Integration tests — 2-3 critical flows only, @SpringBootTest + MockMvc (H2 or Testcontainers MySQL, state choice and why): Flow A signup → login → use JWT on a protected endpoint; Flow B patient joins queue → AI triage (mock Groq) → wait time present → doctor calls next → IN_PROGRESS → complete → COMPLETED.
3. API testing — run the full Day 9 Postman collection via Newman against a running local instance; save the run report to docs/09_TESTING.md and/or postman/test-report.json.
4. UI testing — light pass, Vitest + RTL: LoginPage renders and submits; ProtectedRoute redirects unauthorized role; StatusTag renders correct color/label per value; one RTK Query hook's loading/error/success states render correctly in a consuming component.
5. Manual testing checklist — documented in docs/09_TESTING.md, covering full user journeys per role (Patient: signup → browse doctors → join queue → track status; Doctor: login → view queue → override triage → call next → complete; Admin: login → manage departments/doctors → view analytics). Actually walk through each and mark pass/fail — execute it, don't just write it.
6. Bug list — log every bug found. Fix anything critical; log non-critical bugs as GitHub Issues labeled bug, listed explicitly.
7. docs/09_TESTING.md — finalize as the ADF-required Test Report: strategy/scope decision, unit test summary (covered + intentionally not), integration summary, API test results (Postman/Newman), UI summary, manual results (pass/fail per journey), full bug list with status.

Explicitly OUT of scope today: full E2E browser automation (Cypress/Playwright — Phase 2), load/performance testing (Phase 2), 100% code coverage (risk-prioritized instead).

OUTPUT FORMAT: same labeled-file-block format as previous days. Bug list and test summary as clear markdown lists before the file blocks.

GIT WORKFLOW (you run this yourself — Freebuff guides, doesn't execute): pull develop → branch feature/testing-suite → write and run tests locally after each meaningful addition → commit in small increments (test: per-service unit tests, test: integration tests, test: frontend component tests, fix: per critical bug, docs: finalize test report) → push → PR feature/testing-suite → develop titled "Day 10: Testing" with Closes #<issue> (description: coverage summary, critical bugs fixed, non-critical bugs logged) → self-review (confirm full suite passes) → merge into develop yourself.

HARD CONSTRAINTS: do not skip the AI fallback test (single most important test given Day 5's design decision); do not silently fix bugs without logging them; state assumptions (H2 vs Testcontainers, deferred non-critical bugs) before making changes.

VERIFICATION CHECKLIST: Issue/board/PR linkage; full bug list (found / fixed today / logged for later); AI fallback test explicitly passes; manual checklist executed, not just written; exact git command sequence; ready-to-paste PR description.
```
---

## Day 11: Docker Containerization (Dockerfiles, Compose, Local Validation)

## Full Prompt Text

```
# CareQ — Day 11 Prompt (Docker: Dockerfiles, Compose, Local Validation)

## ROLE

You are acting as a **DevOps-minded Backend Engineer** containerizing an existing, tested application. Today's goal: every service and the frontend run correctly via a single `docker-compose up`, with no manual steps beyond providing environment variables.

## PROJECT CONTEXT (recap)

- **Services:** `eureka-server`, `api-gateway`, `auth-service`, `user-service`, `doctor-service`, `queue-service`, plus the React frontend
- **External dependency:** MySQL (one instance, multiple schemas/databases — or one shared database, state your choice), and the Groq API (external, needs `GROQ_API_KEY`)
- **Startup order matters**: Eureka must be up before other services register with it; MySQL must be ready before any service tries to connect; the Gateway should be reachable before the frontend is useful

## TODAY'S DELIVERABLES

### 0. Create today's Issue first
Create `Day 11: Docker Containerization` as a GitHub Issue, checklist from below, labeled `day-11`, `infra`. Add to the Project board under `In Progress`. Reference with `Closes #<issue number>` in today's PR.

### 1. Dockerfile per backend service
For each of `eureka-server`, `api-gateway`, `auth-service`, `user-service`, `doctor-service`, `queue-service`:
- **Multi-stage build**: a Maven build stage (compile + package the jar) and a slim JRE runtime stage (don't ship the JDK or Maven in the final image)
- Run as a **non-root user** inside the container
- Include a `HEALTHCHECK` instruction (hit the Spring Boot Actuator health endpoint if available, or a simple TCP check)
- `.dockerignore` per service (exclude `target/`, `.git`, IDE files, etc.)

### 2. Dockerfile for the frontend
- Multi-stage: Node build stage (`npm run build`) → Nginx stage serving the static build
- Nginx config: fallback to `index.html` for client-side React Router routes, and reverse-proxy `/api/*` to the `api-gateway` service (so the frontend doesn't need the gateway's URL hardcoded at build time — state clearly if you instead handle this via a build-time environment variable, and explain the trade-off)

### 3. `docker-compose.yml` — full stack orchestration
Must include:
- MySQL service with a named volume for data persistence, initialized with required databases/schemas
- `eureka-server`, started first (or with appropriate `depends_on` + `condition: service_healthy` so others wait for it)
- `api-gateway`, `auth-service`, `user-service`, `doctor-service`, `queue-service` — each depending on MySQL being healthy and Eureka being up before starting
- The frontend service, depending on the gateway
- All services on a shared custom Docker network
- Environment variables sourced from a `.env` file — **never hardcoded in the compose file** — including `GROQ_API_KEY`, JWT signing secret, and MySQL credentials
- Sensible container names and port mappings matching what's used in local (non-Docker) development, so switching between the two doesn't require relearning ports

### 4. `.env.example`
List every required environment variable with placeholder values and a one-line comment explaining each (e.g., `GROQ_API_KEY=your_groq_key_here # required for AI symptom triage`). Never commit a real `.env` — confirm it's in `.gitignore`.

### 5. Local validation — actually run it
Bring up the full stack with `docker-compose up`, then:
- Confirm every service registers with Eureka (check the Eureka dashboard)
- Confirm the Gateway correctly routes to each service
- Confirm the frontend loads and can reach the backend through the Gateway
- Run a smoke test end-to-end **inside the Dockerized stack**: signup → login → browse doctors → join queue → confirm AI triage and wait-time prediction work (this proves the `GROQ_API_KEY` env var is actually being read correctly inside the container)
- Report the results of this validation explicitly — don't just say "it should work," show what was actually checked

### 6. Documentation updates
- Add a "Docker Setup" section to `docs/10_DEPLOYMENT.md` (or create it if it doesn't exist yet) — how to build, how to run, required env vars, how to view logs per service, how to tear down
- Update `README.md` with a "Run with Docker" quick-start section as an alternative to the manual local setup instructions

**Explicitly OUT of scope today:**
- CI/CD pipeline (building/pushing images automatically) — that's Day 12
- Cloud deployment — that's Day 13
- Kubernetes or any orchestration beyond Docker Compose

## OUTPUT FORMAT

Same labeled-file-block format as previous days.

Generate in this order: one Dockerfile + `.dockerignore` per backend service → frontend Dockerfile + Nginx config → `docker-compose.yml` → `.env.example` → doc updates.

## GIT WORKFLOW (direct execution)

1. **Pull `develop` first:** `git checkout develop` / `git pull origin develop`
2. **Branch off it:** `git checkout -b feature/docker-setup`
3. Build and actually run `docker-compose up` locally, iterating until the full smoke test in Deliverable 5 passes — don't commit a compose setup you haven't actually verified boots cleanly.
4. Commit in small increments: one `feat: add Dockerfile for <service>` per service, `feat: add frontend Dockerfile and Nginx config`, `feat: add docker-compose.yml with full stack orchestration`, `docs: add Docker setup documentation and .env.example`
5. **Push:** `git push origin feature/docker-setup`
6. **Open a Pull Request** `feature/docker-setup → develop`. Title: `Day 11: Docker Containerization`. Include `Closes #<issue number>`. Description includes the actual validation results from Deliverable 5.
7. Self-review — re-run `docker-compose up` from a clean state (`docker-compose down -v` first) to confirm it works reproducibly, not just on the first lucky run.
8. Merge into `develop` once satisfied.

**Definition of Done:**
- [ ] Today's Issue created, added to board, closed via PR
- [ ] Every service has a working multi-stage Dockerfile with a non-root user and healthcheck
- [ ] Frontend Dockerfile builds and serves correctly via Nginx
- [ ] `docker-compose up` brings up the full stack successfully from a clean state
- [ ] All services confirmed registered with Eureka inside Docker
- [ ] End-to-end smoke test (including real AI triage call) passes inside the Dockerized stack
- [ ] `.env.example` complete and real `.env` confirmed gitignored
- [ ] Documentation updated (`10_DEPLOYMENT.md`, `README.md`)
- [ ] Pushed to `feature/docker-setup`, PR opened and self-reviewed, merged into `develop`

## HARD CONSTRAINTS

- Never hardcode secrets (JWT key, DB credentials, `GROQ_API_KEY`) in the Dockerfiles or `docker-compose.yml` — env vars only
- Confirm `.env` is gitignored before committing anything
- State assumptions (single vs. multiple MySQL databases, how the frontend resolves the API base URL) before generating files

## VERIFICATION CHECKLIST (append at the end of your output)

1. Confirm today's Issue/board/PR linkage
2. Confirm the full stack was actually brought up and the end-to-end smoke test results, in detail
3. Confirm `.env` is gitignored and `.env.example` is complete
4. Output the exact `git` command sequence used
5. Output a ready-to-paste PR description
```

---

# CareQ — Day 13 Prompt (Redis Caching + RabbitMQ Event-Driven Notification Service)

## ROLE

You are acting as a **Senior Backend Engineer** adding two infrastructure capabilities that complete work deliberately scoped out earlier: Redis caching for read-heavy catalog data, and a real event-driven notification system via RabbitMQ — replacing Day 7b's explicitly-documented "client-side, session-only" limitation with a proper persisted solution.

## PROJECT CONTEXT (recap)

- **Services so far:** `eureka-server`, `api-gateway`, `auth-service`, `user-service`, `doctor-service`, `queue-service`
- **From Day 7b:** notifications were scoped as client-side derived only, with an explicit note that persisted/push notifications were a Phase 2 roadmap item — this day builds that properly
- **From Day 12:** AI Auto-Assignment triage is synchronous and must stay that way — the triage call itself is NOT made async

## IMPORTANT EXCEPTION TO PRIOR DAYS' RULE

Every previous day avoided touching already-finished services. This day intentionally reopens two of them: `doctor-service` (Redis caching) and `queue-service` (RabbitMQ event publishing). This is deliberate, not scope drift — stated explicitly in the PR description so the history reads as an intentional architectural addition.

## DESIGN DECISIONS (implemented)

1. **Redis scope: doctor/department catalog only.** `GET /api/doctors` and `GET /api/departments` cached with a **60s TTL** via `@Cacheable`; `@CacheEvict` on every Admin create/update/delete mutation (plus the doctor's own availability toggle, and department renames which change the doctor list's department names). Live queue data (position, wait time, availability) is NOT cached; `getDoctorById` (the join-validation read) stays uncached so going offline blocks joins immediately. Keys namespaced `careq:doctorCatalog::` / `careq:departments::`. A custom `CacheErrorHandler` logs and swallows Redis failures — the catalog never 500s when the cache is down.
2. **RabbitMQ topology:** one durable topic exchange `careq.events` with routing keys `queue.joined`, `queue.triaged`, `queue.called`, `queue.completed`. queue-service publishes (JSON `QueueEventDto` via `Jackson2JsonMessageConverter`, wrapped in try/catch — a publish failure is logged, never propagated); a new notification-service binds durable queue `careq.notifications` with `queue.*` and consumes all four.
3. **New microservice: `notification-service` (port 8085).** Consumes RabbitMQ events, persists a `NotificationEntry` (`recipientUserId`, `type`, `message`, `read`, `createdAt`) to the shared `careq_db`, exposes `GET /api/notifications/me` (paginated + total `unreadCount`) and `PUT /api/notifications/{id}/read` (ownership-checked). Registers with Eureka, routed via the Gateway at `/api/notifications/**`, uses the same header-based identity trust pattern as every other service.
4. **Frontend notification bell upgraded, not replaced.** The Day 7b real-time toast-on-status-change behavior is kept as the immediate-feedback layer, but the bell's dropdown history is now backed by real persisted data from notification-service (polled every 15s).
5. **AI triage stays synchronous** — RabbitMQ carries post-decision notification events only.

## ALSO FIXED (doctor dashboard dead-end)

- **`GET /api/doctors/me`** — resolves the calling doctor's own catalog entry by header identity; the doctor dashboard and queue page now use it instead of scanning the whole paginated catalog (which broke once the catalog outgrew one page, and produced the misleading "No doctor catalog entry found — ask an admin" dead-end while the dashboard appeared to show a queue).
- **Friendly setup states** on both doctor pages when the account isn't linked yet (auto-rechecks every 15s).
- **Admin doctor form: DOCTOR-account picker** — user IDs are selected from the real user directory, never hand-typed (the #1 cause of the dead-end).

## DELIVERABLES (all complete)

1. Redis caching in doctor-service (deps, config, annotations, tests unaffected — plain unit tests)
2. RabbitMQ publishing in queue-service (exchange, `QueueEventPublisher`, wired into join/triage/call/complete; unit + integration tests incl. the non-blocking contract)
3. notification-service (full layered scaffold + Dockerfile + tests)
4. Gateway route `/api/notifications/**`, Swagger aggregation entry, health whitelist
5. Frontend `notificationApi` slice, store wiring, bell on persisted data, toasts kept
6. Doctor dashboard/queue fix + admin picker
7. docker-compose: `redis`, `rabbitmq:3-management`, `notification-service`; depends_on healthchains; `.env.example` RabbitMQ creds
8. Docs: 03_ARCHITECTURE (§12 event bus, §13 Redis + /me), 04_DATABASE (notification_entries), 05_API_CONTRACT (notification endpoints + /me), README, this prompt archive

## VALIDATION PERFORMED

- Backend compile across all 7 modules; test suites: doctor 38 ✓, queue 61 ✓ (incl. `QueueEventPublisherTest`), notification-service 16 ✓ (controller integration, service, consumer)
- Frontend `tsc` + Vitest
- Dockerized stack: Redis keys inspected (`redis-cli KEYS careq:*`) + cache-hit/evict behaviour, RabbitMQ management API queue depth drained per event type, notification rows persisted and served via the gateway, smoke flow join → called → complete

## HARD CONSTRAINTS (honoured)

- AI triage never made async / never routed through RabbitMQ
- No email/SMS delivery — notifications remain in-app
- A RabbitMQ publish failure never fails a join/call-next/complete
- Redis failures never 500 the catalog (fail-open cache)

## GIT WORKFLOW

Branch `feature/redis-rabbitmq-notifications` off `develop`; small incremental commits (see the repo history); PR titled `Feature: Redis Caching + RabbitMQ Notification Service` closing the Day 13 issue, explicitly noting the intentional reopening of doctor-service and queue-service.

---

## Day 14: CI/CD Pipeline (GitHub Actions)

**Prompt:** CareQ — Day 14 Prompt (CI/CD: GitHub Actions Pipeline)

**Date Executed:** 2026-08-10

**Branch:** `feature/ci-cd-pipeline`

**Summary:** Built the GitHub Actions CI/CD pipeline. `ci.yml` runs on every PR to develop/main: a matrix job builds and tests each of the 7 backend services (`mvn -pl <service> test`, Java 17 temurin, Maven cache), the frontend runs `npm ci` → Vitest → tsc + Vite production build, a `docker-build` matrix builds all 8 images (loaded locally with compose-compatible `careq-<service>:latest` tags), and `docker-smoke` boots the full stack inside the runner via `docker compose up -d`, waits for every container health check, and smokes the gateway, all five service health endpoints, the SPA root, and a real signup→login flow. `cd-develop.yml` re-runs the gate on every push to develop, then pushes all images to `ghcr.io/rameshpothamsetty/careq-<service>` tagged with the commit SHA + `develop-latest` (built-in `GITHUB_TOKEN`, `packages: write`). `cd-release.yml` runs on main pushes and `v*` tags, pushing `latest` + the version tag, and documents the Day 15 `deploy` plug-in point. README gained the CI badge + pipeline overview; docs/10_DEPLOYMENT.md gained § 3. Note: the pasted prompt was titled "Day 13: CI/CD", but our Day 13 (Redis/RabbitMQ/notification-service) was already complete — this work is filed as Day 14 per the README roadmap. `gh` CLI is not authenticated here, so the Issue/PR were opened manually by the user; CI verification happens in the Actions tab after the branch is pushed.

## Full Prompt Text

```
# CareQ — Day 13 Prompt (CI/CD: GitHub Actions Pipeline)

## ROLE

You are acting as a DevOps-minded Backend Engineer building a CI/CD pipeline with GitHub Actions. Today's scope ends at "tested, containerized images sitting in a registry, ready to deploy" — actual deployment to a live cloud environment is tomorrow's work, not today's.

## PROJECT CONTEXT (recap)

- Services: eureka-server, api-gateway, auth-service, user-service, doctor-service, queue-service, and notification-service (merged), plus the React frontend
- Container registry: GitHub Container Registry (ghcr.io) — authenticates with the repo's built-in GITHUB_TOKEN
- Existing test suite (Day 10) and Docker setup (Day 11) — this pipeline automates both

## TODAY'S DELIVERABLES

0. Create today's Issue first (Day 14: CI/CD Pipeline, labeled day-14, infra).
1. CI workflow — .github/workflows/ci.yml on pull_request to develop/main: checkout, Java + Node setup, matrix build+test per backend service (fail the workflow on any failure), frontend install/test/production build, clear per-service failure attribution.
2. CD workflow — .github/workflows/cd-develop.yml on push to develop: re-run the test gate, build a Docker image for every service and the frontend, tag with short commit SHA + develop-latest, push to ghcr.io/<owner>/careq-<service>. No deployment yet.
3. Release workflow — .github/workflows/cd-release.yml on push to main and/or version tags: same sequence, images tagged with the release version + latest; state where Day 15's deploy step plugs in.
4. Health check step — spin up the full stack via docker-compose inside the CI runner using freshly built images, wait for health checks, run a basic smoke test (gateway health + one real endpoint per service).
5. Secrets — document exactly which GitHub Actions secrets are needed (GITHUB_TOKEN suffices; GROQ_API_KEY optional); add a CI status badge to README.
6. Documentation — CI/CD section in docs/10_DEPLOYMENT.md; README badge + pipeline overview.

Explicitly OUT of scope today: live deployment (Day 15), Kubernetes beyond Compose, pipeline failure alerts.

## GIT WORKFLOW

Pull develop → branch feature/ci-cd-pipeline → build the workflow files → commit in small increments (feat: CI workflow / feat: CD develop workflow / feat: release workflow / feat: in-pipeline health check / docs: badge + pipeline docs) → push → open a draft PR early to trigger and watch a real CI run → self-review against a green Actions run → merge into develop yourself → watch cd-develop.yml run and confirm images appear in the Packages tab.

## HARD CONSTRAINTS

- Never hardcode secrets in workflow files — use GitHub Actions secrets
- Never push an image without the test gate passing first
- Do not deploy anywhere live today
- State assumptions before generating workflow files
```

---

## Day 15: Azure Cloud Deployment (Container Apps, Static Web Apps, MySQL VM)

> **Revised 2026-08-12 (free-tier plan, per mentor guidance):** the frontend
> moved from Azure Static Web Apps to **Vercel** (free Hobby plan, deployed via
> the Vercel CLI in the `deploy-frontend` job), and the MySQL VM was replaced by
> **Azure Database for MySQL Flexible Server** (Burstable B1ms, 32 GB — free for
> 12 months, private VNet access). `infra/azure/main.bicep` no longer contains a
> VM/NSG/Static Web App; `provision.sh` creates `careq_db` and disables SSL
> enforcement (`require_secure_transport=OFF`). GitHub secrets now include
> `VERCEL_TOKEN` / `VERCEL_ORG_ID` / `VERCEL_PROJECT_ID`; the SWA token is gone.
> See `docs/10_DEPLOYMENT.md` § 4.1–4.6 for the updated architecture and costs.

**Prompt:** CareQ — Day 15 Prompt (Cloud Deployment: Azure Container Apps, Static Web Apps, MySQL)

**Date Executed:** 2026-08-11

**Branch:** `feature/cloud-deployment`

**Summary:** Deployed CareQ to Azure. **One-time infra (`infra/azure/`)**: `main.bicep` provisions a VNet (apps-subnet 10.0.1.0/24 delegated to `Microsoft.App/environments`, vms-subnet 10.0.2.0/24), Log Analytics, a consumption-only VNet-injected Container Apps Environment, **nine** container apps (eureka/gateway/auth always-on min 1; user/doctor/queue/notification scale-to-zero min 0 with HTTP rules; redis:7-alpine + rabbitmq:3-management with TCP ingress + TCP scale rules), a Standard_B1s MySQL VM (static private IP 10.0.2.10, **no public IP**, NSG allowing :3306 **only** from 10.0.1.0/24, cloud-init installs MySQL 8 + `careq_db` + `careq` user), and a Free-tier Static Web App; `provision.sh` deploys it, prints live URLs, the SWA deployment token, and the GitHub secrets list. **Recurring deploy**: `deploy` + `deploy-frontend` jobs added to `cd-develop.yml`/`cd-release.yml` (OIDC `azure/login@v2`, no client secret; `az containerapp update` rolls images + secrets; frontend built with the live gateway FQDN baked in and uploaded via `azure/static-web-apps-deploy` with `skip_app_build`). **App changes**: `application-azure.yml` per Eureka client registers with `CONTAINER_APP_HOSTNAME` + port 80 so inter-service traffic flows through the Envoy proxy (what makes HTTP scale-to-zero actually wake), `CorsConfig` origins now env-driven (`CORS_ALLOWED_ORIGINS` → live SWA origin), Dockerfiles accept `JAVA_OPTS`, `seed-data.sh` accepts `API_BASE` override, new `scripts/day15-azure-smoke.mjs` runs the full patient journey against live Azure URLs. Docs: `10_DEPLOYMENT.md` §4 (architecture, cost policy, cold-start, teardown), README live banner + status. Renumbered from the pasted "Day 14" prompt — Day 14 in this repo is already the CI/CD pipeline, so this is filed as Day 15 (same precedent as Day 14). User runs the one-time provisioning and git steps themselves; the recurring deploy runs via GitHub Actions.

## Full Prompt Text

```
# CareQ — Day 14 Prompt (Cloud Deployment: Azure Container Apps, Static Web Apps, MySQL)

> Save as the next entry in `docs/11_PROMPTS.md`.
> Paste everything below the `---` into Freebuff exactly as-is.
> Assumes: Day 13's CI/CD pipeline working (images build and push to GHCR on merge), Azure credentials already set up — Resource Group `careq-rg`, App Registration with federated OIDC credentials, Contributor role assigned, and `AZURE_CLIENT_ID` / `AZURE_TENANT_ID` / `AZURE_SUBSCRIPTION_ID` already in GitHub repo secrets.
> You run any one-time Azure CLI provisioning commands yourself in Cloud Shell (same pattern as the credential setup) — Freebuff generates the scripts/templates and explains each command, but doesn't execute interactive cloud provisioning for you. The recurring deploy workflow, once written, runs automatically via GitHub Actions from then on.

---

## ROLE

You are acting as a **Cloud/DevOps Engineer** deploying CareQ to Azure for the first time. This splits into two distinct kinds of work: (1) one-time infrastructure provisioning (Container Apps Environment, MySQL VM, Static Web App resource) that you run once, and (2) a recurring deployment workflow added to the existing CI/CD pipeline that runs automatically on every future push. Be explicit in your output about which parts are one-time and which are ongoing.

## PROJECT CONTEXT (recap)

- **Services to deploy:** `eureka-server`, `api-gateway`, `auth-service`, `user-service`, `doctor-service`, `queue-service`, `notification-service` (if merged), plus `redis` and `rabbitmq` (if that feature is merged) as self-hosted containers, plus the React frontend
- **Resource Group:** `careq-rg` (already exists)
- **Existing CI/CD (Day 13):** builds, tests, and pushes images to `ghcr.io` on merge to `develop`/`main` — today extends this with an actual deploy step
- **Azure credentials:** already configured for OIDC (no client secret) — `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` are in GitHub secrets

## HONEST COST REALITY — state this in your output, don't hide it

Running every service always-on 24/7 (min replicas ≥ 1) risks exceeding Azure Container Apps' recurring free monthly grant once Redis/RabbitMQ are added as extra containers. Default to: **`eureka-server`, `api-gateway`, `auth-service` kept always-warm (min replicas 1)** since they're on every critical path; **`user-service`, `doctor-service`, `queue-service`, `notification-service`, `redis`, `rabbitmq` allowed to scale to zero (min replicas 0)** and cold-start on demand — this keeps cost minimal while staying genuinely usable for a demo/interview. Document this trade-off clearly, and add a note in `docs/10_DEPLOYMENT.md` about monitoring actual usage in Azure Cost Management, since the $200 trial credit is a 30-day buffer, not a permanent solution.

## TODAY'S DELIVERABLES

### 0. Create today's Issue first
Create `Day 14: Cloud Deployment (Azure)` as a GitHub Issue, checklist from below, labeled `day-14`, `infra`. Add to the Project board under `In Progress`. Reference with `Closes #<issue number>` in today's PR.

### 1. One-time infrastructure provisioning script (Bicep preferred, or a documented `az cli` script if simpler)
Output as a runnable script/template plus the exact `az` commands to apply it. This is what I run once in Cloud Shell, not something GitHub Actions runs repeatedly. Must provision:
- A **Container Apps Environment** (with a Log Analytics workspace for logging) in `careq-rg`
- Seven (or however many exist) **Container App** definitions — one per backend service — with the min/max replica settings from the cost section above, correct internal networking so they can reach each other and Eureka
- `redis` and `rabbitmq` as additional Container Apps using their public images (`redis:alpine`, `rabbitmq:3-management`) — clearly document that their storage is ephemeral (data lost on restart) as a known limitation of this free-tier approach, not a bug
- A small **MySQL VM** (`Standard_B1s`, the 12-months-free size) with MySQL installed via cloud-init, firewalled to only accept connections from the Container Apps Environment's subnet, not the open internet
- A **Static Web App** resource for the frontend, and instructions for retrieving its deployment token (needed as a 4th GitHub secret, `AZURE_STATIC_WEB_APPS_API_TOKEN`)

### 2. Extend the CI/CD pipeline with a deploy job
Modify `cd-develop.yml` and `cd-release.yml` from Day 13 to add a `deploy` job that runs after images are successfully pushed:
- `azure/login@v2` action, authenticating via OIDC using the three existing secrets (no client secret)
- For each backend service: `az containerapp update` to point it at the freshly-built image tag from this run
- For the frontend: build with the correct API base URL (pointing at the Gateway Container App's FQDN) baked in at build time, then deploy via the `azure/static-web-apps-deploy` action using `AZURE_STATIC_WEB_APPS_API_TOKEN`
- This job should only run on `develop` and `main`, never on plain PRs (deployment isn't something every PR should trigger)

### 3. Secrets and environment configuration for each Container App
- Use `az containerapp secret set` (or the Bicep equivalent) so each service gets its required environment variables (JWT signing secret, MySQL connection string pointing at the VM, `GROQ_API_KEY`, RabbitMQ/Redis connection strings) as Container Apps secrets, sourced from GitHub secrets at deploy time — never hardcoded in the Bicep template or workflow file
- List every new GitHub secret this requires beyond the three already set up, and remind me to add them before the workflow will succeed

### 4. Final validation — actually deploy and check it live
- Run the one-time provisioning script yourself would be my job, but Freebuff should output the exact steps and expected outcomes I should see (resource created, VM reachable, etc.)
- After the first deploy workflow run: report the live Gateway URL and Static Web App URL
- Run the same smoke test used in Day 11/12 (signup → login → browse doctors → join queue → confirm AI triage/wait prediction) but **against the live Azure URLs**, not localhost
- Confirm cold-start behavior on a scaled-to-zero service is acceptable (note the delay observed)

### 5. Documentation
- `docs/10_DEPLOYMENT.md` — full Azure section: architecture diagram (which services always-on vs scale-to-zero), how to redeploy, how to check costs in Azure Cost Management, how to tear everything down (`az group delete --name careq-rg`) if needed
- `README.md` — add the live demo URL, replace the "Run with Docker" section's implied "that's the only way to run this" framing with "also deployed live at: ..."

**Explicitly OUT of scope today:**
- A custom domain name (using Azure's default `*.azurecontainerapps.io` / `*.azurestaticapps.net` URLs is fine for now)
- Auto-scaling tuning beyond the simple min/max replica defaults above
- Multi-region deployment

## OUTPUT FORMAT

Same labeled-file-block format as previous days for any file changed or created. Clearly separate "run once in Cloud Shell" content from "committed to the repo, runs via GitHub Actions" content.

---

## GIT WORKFLOW (you run this yourself — Freebuff guides, doesn't execute)

1. **Pull `develop` first:**
   ```
   git checkout develop
   git pull origin develop
   ```
2. **Branch off it:**
   ```
   git checkout -b feature/cloud-deployment
   ```
3. First, run the one-time provisioning (Deliverable 1) yourself in Cloud Shell, following Freebuff's step-by-step output — confirm each resource is created before moving on.
4. Then build the CI/CD workflow changes (Deliverable 2-3), commit in small increments:
   - `feat: add Bicep template for Container Apps Environment and services`
   - `feat: add MySQL VM provisioning script`
   - `feat: add Static Web App resource provisioning`
   - `feat: add deploy job to CI/CD pipeline using OIDC`
   - `feat: configure Container App secrets from GitHub secrets`
   - `docs: document Azure deployment architecture and teardown process`
5. **Push, yourself:**
   ```
   git push origin feature/cloud-deployment
   ```
6. **Open a Pull Request** `feature/cloud-deployment → develop` yourself. Title: `Day 14: Cloud Deployment (Azure)`. Include `Closes #<issue number>`. Note in the description the always-on vs scale-to-zero decisions and the live URLs once confirmed working.
7. Watch the deploy job actually run in the Actions tab after merge — confirm it succeeds and the live URLs respond correctly, not just that the workflow file looks right.
8. **Merge into `develop` yourself** once the live smoke test passes.

**Definition of Done:**
- [ ] Today's Issue created, added to board, closed via PR
- [ ] Container Apps Environment, all services, MySQL VM, and Static Web App provisioned successfully
- [ ] Deploy job added to CI/CD, runs automatically on `develop`/`main` push, authenticates via OIDC (no stored secret)
- [ ] All required secrets documented and added
- [ ] Live smoke test (signup → login → browse → join queue → AI triage/wait prediction) passes against the real Azure URLs
- [ ] Cold-start behavior on scaled-to-zero services checked and documented
- [ ] `docs/10_DEPLOYMENT.md` and `README.md` updated with live URLs and teardown instructions
- [ ] Pushed to `feature/cloud-deployment`, PR opened and self-reviewed, merged into `develop`

## HARD CONSTRAINTS

- Never hardcode secrets in Bicep templates or workflow files — Container Apps secrets and GitHub secrets only
- Firewall the MySQL VM to the Container Apps subnet only, never open to the public internet
- State the always-on vs scale-to-zero assumption per service clearly before generating the provisioning script
- Be explicit and honest about the ephemeral-storage limitation of self-hosted Redis/RabbitMQ — don't gloss over it

## VERIFICATION CHECKLIST (append at the end of your output)

1. Confirm today's Issue/board/PR linkage
2. List every new GitHub secret required beyond the three already configured
3. Confirm the live smoke test results against the real Azure URLs, including any cold-start delay observed
4. Confirm the MySQL VM firewall is correctly scoped, not open to the public internet
5. Output the live Gateway URL and Static Web App URL
6. Output the exact `git` command sequence, with explanations, for me to run
```

# CareQ — Intelligent Patient Flow Platform

[![CI](https://github.com/RameshPothamsetty/careq/actions/workflows/ci.yml/badge.svg)](https://github.com/RameshPothamsetty/careq/actions/workflows/ci.yml)

> 🚀 **Live:** <https://careq-frontend.vercel.app> — frontend on **Vercel** (free Hobby plan), backend on **Azure Container Apps** backed by a **free 12-month** MySQL Flexible Server. See [`docs/10_DEPLOYMENT.md`](docs/10_DEPLOYMENT.md) § 4 for URLs, costs, and teardown.

**AI-powered OPD operations: predict wait times, triage patients by urgency, and run a live, role-specific view of hospital queues.**

CareQ (brand: **SmartOPD AI**) is a microservices-based hospital queue-management platform that uses an LLM to triage patients from free-text symptoms and predicts each patient's wait time live, so Patients, Doctors, and Admins always know what's happening next.

---

## Problem Statement

OPDs are chaotic: patients wait with no idea how long it will take, urgent cases can sit behind non-urgent ones, and doctors have no live view of their queue. CareQ fixes this by triaging every patient with AI (emergency cases jump the queue), predicting wait times from each doctor's real consultation load, and giving every role a live, accurate picture of queue operations — no shouting in corridors, no guesswork.

---

## Features (by role)

### 🧑‍🤝‍🧑 Patient
- **Browse doctors** — search/filter by department and specialization, see fees, experience and live availability
- **Join a queue in seconds** — describe symptoms in plain language; the AI assigns an urgency level instantly
- **Live status screen** — big position + estimated wait that updates every 10 seconds, with a joined → called → completed progress flow
- **Live dashboard** — a "My visit" widget showing position, estimated wait and a joined → called → completed progress bar right on the dashboard, polling live
- **Recent visits & recommendations** — your visit history with statuses and timestamps, plus doctor recommendations based on your last visit's department (continuity of care)
- **Leave the queue** — cancel your own waiting entry anytime; cancelled visits appear in your history
- **Profile** — manage phone, address, DOB, gender, and profile picture
- **AI chat assistant** — ask plain-language questions ("How long will I wait?", "Which doctor for chest pain?") and get answers grounded in live queue data and the doctor catalog
- **Ranked doctor search** — type a symptom or specialty and get relevance-ranked results across names, specializations, departments and qualifications
- **Bills** — every completed visit generates an invoice at the doctor's fee + 18% GST, viewable in a dedicated bills page with a sandbox "Pay now" flow
- **Multilingual UI (i18n)** — switch between English / हिन्दी / తెలుగు from the 🌐 switcher on login and in the header; your choice is remembered per browser

### 🩺 Doctor
- **Live dashboard** — waiting / in-consultation / longest-wait stats, a "next patient ready" hero with call-next right on the dashboard, triage mix at a glance, and a waiting-list preview
- **Personal analytics** — patients completed today, average wait and consult time, plus 7-day patient-load and wait-trend charts (Recharts)
- **Department context** — see how many of your department colleagues are online right now
- **Live patient queue** — every patient's name, symptoms, AI triage badge, real queue position and predicted wait, polling every 10s
- **Search your queue by patient name** to find a specific patient fast
- **Triage override** — the doctor's clinical judgment is final and reorders the queue
- **Call next / complete** with one tap, and toggle your own availability (online/offline)

### 🧑‍💼 Receptionist (front desk)
- **Live queue management** — see every doctor's queue with triage levels and estimated waits, and mark patients In Consultation / Completed, without any admin powers over users, departments, or doctors
- Created by an Admin — not offered at public self-signup

### ⚙️ Admin
- **Manage departments & doctors** — full CRUD with search, server-side pagination and sortable columns
- **Doctor detail drawer** — click any doctor for the full record (qualification, fees, experience) plus their live queue snapshot
- **User directory** — paginated, searchable (name/email) list of every registered user
- **Live queue overview** — hospital-wide summary cards (waiting, in-consultation, doctors online, delayed, avg wait) and a per-doctor breakdown
- **Analytics dashboard** — 7-day charts (Recharts): patients handled per day, average wait-time trend, and queue distribution by department, with summary stat cards and graceful empty states

### 🔔 Everyone
- **In-app notifications** — the bell's history is now **real persisted data**: queue-service publishes every state change (joined / triaged / called / completed) onto a RabbitMQ event bus, a dedicated notification-service persists it, and the bell (polled every 15s) survives refreshes. The real-time toast-on-status-change layer still fires instantly on top.
- **Real-time WebSocket push** — the bell updates **instantly** over STOMP at `/ws` (JWT-authenticated at the connection layer); the 15s polling stays as an automatic fallback
- **Web Push notifications** — the Phase 2 "real delivery" item, delivered as **browser push via VAPID** (free — no third-party account): patients flip a toggle in the bell, grant the browser permission, and get OS-level alerts ("It's your turn!") even when CareQ is closed. Per-user preferences (`notification_preferences`) + per-browser subscriptions (`push_subscriptions`), async fail-open delivery on the notification-service side; email/SMS remain future channels.
- **Launch hardening** — **email verification + password reset** (one-time hashed tokens via Gmail SMTP, legacy accounts grandfathered), **rate limiting** on signup/login/resend/forgot (brute-force guard), a request-level **audit trail** (gateway `ACCESS` + auth `AUDIT` logs), **Swagger disabled in production**, **MySQL TLS**, **security headers** (CSP/HSTS on Vercel + nginx + gateway), a 15-minute **uptime check** that opens a GitHub issue when the site is down, and a **monitoring/backup runbook** (`docs/12_MONITORING.md`).

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite, Redux Toolkit Query, Tailwind CSS, Recharts, lightweight i18n (en/hi/te) |
| Backend | Spring Boot 3.2, Java 17, Maven |
| Service Registry | Netflix Eureka |
| API Gateway | Spring Cloud Gateway (JWT validation + identity headers) |
| Inter-service calls | OpenFeign + LoadBalancer |
| Database | MySQL 8 |
| Cache | Redis 7 — doctor/department catalog with 60s TTL |
| Event Bus | RabbitMQ — `careq.events` topic exchange, `queue.*` routing keys |
| CI/CD | GitHub Actions — PR CI (matrix build/test + compose health check), GHCR image pipeline |
| Auth | Spring Security + JWT (HMAC-SHA256) |
| AI | Groq (`llama-3.1-8b-instant`) — symptom triage with guaranteed fallback |
| Testing | JUnit 5 + Mockito (backend), Playwright (E2E) |

---

## Architecture

Seven services collaborate through Eureka service discovery; all client traffic enters through the API Gateway, which validates the JWT and forwards `X-User-Id` / `X-User-Role` / `X-User-Name` / `X-User-Email` identity headers to downstream services. Redis caches the doctor/department catalog and RabbitMQ carries queue → notification events.

```
Browser (React SPA :3030)
        │
        ▼
API Gateway (:8080)  ── validates JWT, forwards identity headers
        │
   ┌────┼─────────────┬──────────────┬──────────────┬──────────────┐
   ▼    ▼             ▼              ▼              ▼              ▼
 auth  user         doctor         queue    notification      eureka-server
(:8081)(:8082)      (:8083)        (:8084)      (:8085)           (:8761)
                       │   Redis     │   RabbitMQ careq.events ──▶  notification
                       │   (:6379)    │   queue.joined|triaged|      (consumes,
                       │   catalog    │   called|completed            persists)
         └── Feign call (avg consult time, availability) ──┘
                          └── Groq LLM (symptom triage)
```

The queue-service never duplicates doctor consultation data — it fetches `avgConsultationTimeMinutes` and `isAvailable` live from doctor-service via Feign (single source of truth). Position and predicted wait are derived on every read, never cached. Live queue data is never cached in Redis — only the rarely-changing catalog, with a short 60s TTL, and RabbitMQ carries post-decision notification events only (AI triage stays synchronous).

**See [`docs/03_ARCHITECTURE.md`](docs/03_ARCHITECTURE.md) for the full architecture, auth flow, and frontend state management design.**

---

## Local Setup

### Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8+ (running locally)
- Redis 7+ (running locally — doctor-service catalog cache)
- RabbitMQ 3.x+ (running locally — queue → notification event bus)
- Maven 3.9+
- A Groq API key (optional at runtime — without it, AI triage gracefully falls back to `NORMAL`)

### Environment variables

| Variable | Required | Used by | Default |
|----------|----------|---------|---------|
| `GROQ_API_KEY` | No¹ | queue-service AI triage | — |
| `JWT_SECRET` | No | auth-service + gateway | dev secret |
| `MYSQL_PASSWORD` | No | all DB-backed services | `root` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | No | queue-service + notification-service | `guest` / `guest` |
| `APP_MAIL_USERNAME` / `APP_MAIL_PASSWORD` | No² | auth-service email verification/reset | — (Gmail App Password) |
| `AUTH_EMAIL_VERIFICATION_ENABLED` | No² | auth-service | `false` (local dev) |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VITE_VAPID_PUBLIC_KEY` | No | notification-service / frontend | — (Web Push) |

² Email verification is OFF locally by default (signup returns a JWT as before); set `APP_MAIL_USERNAME` + `APP_MAIL_PASSWORD` (a Gmail App Password) + `AUTH_EMAIL_VERIFICATION_ENABLED=true` to exercise the real flow (both-or-neither).

¹ Without `GROQ_API_KEY` every patient is triaged `NORMAL`. Set it for the real AI behavior:

```bash
export GROQ_API_KEY="your-groq-key"          # bash
setx GROQ_API_KEY "your-groq-key"            # Windows (new shells only)
```

### 1. Infrastructure — MySQL + Redis + RabbitMQ

The no-Docker path needs **three** data stores up before the services boot (all default to `localhost`, so no env vars are required):

```bash
# 1a. MySQL 8 — create the database once (tables auto-create via Hibernate)
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS careq_db;"

# 1b. Redis 7 — start the server (default :6379)
redis-server

# 1c. RabbitMQ — start the broker (default :5672, guest/guest; UI at http://localhost:15672)
#     macOS:  brew services start rabbitmq
#     Ubuntu: sudo systemctl start rabbitmq-server
#     Windows: install Erlang + RabbitMQ, then start the "RabbitMQ" service
```

Doctor-service reads its catalog cache from Redis; queue-service publishes queue events and notification-service consumes them via RabbitMQ. Both are designed to fail gracefully (apps still boot without them), but start them anyway for the full experience.

### 2. Backend — start in this order (one terminal each)

```bash
# Service registry first
cd backend/eureka-server && mvn spring-boot:run          # :8761

# Gateway + services (any order after eureka is up)
cd backend/api-gateway  && mvn spring-boot:run           # :8080
cd backend/auth-service && mvn spring-boot:run           # :8081
cd backend/user-service && mvn spring-boot:run           # :8082
cd backend/doctor-service && mvn spring-boot:run         # :8083
cd backend/queue-service  && mvn spring-boot:run         # :8084
cd backend/notification-service && mvn spring-boot:run   # :8085
```

### 3. Frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:3030
```

### 4. Seed data (optional but recommended for the demo)

```bash
bash scripts/seed-data.sh
```

Creates the admin, one test patient, 10 doctors **with named catalog entries**, and links each doctor to a department.

| Account | Email | Password |
|---------|-------|----------|
| Admin | `admin@careq.com` | `admin123` |
| Patient | `john@careq.com` | `password123` |
| Doctors | `dr.arjun@careq.com`, `dr.priya@careq.com`, … (10 total) | `password123` |

---

## Run with Docker

> Docker is one way to run CareQ. It is **also deployed live on Azure** — see the banner at the top. Docker remains the fastest way to develop, test, and demo locally.

The **entire stack** — MySQL, Redis, RabbitMQ, Eureka, Gateway, all five business services, and the React frontend — runs with a single command:

```bash
cp .env.example .env          # set JWT_SECRET (and GROQ_API_KEY for real AI triage)
docker compose up -d --build
bash scripts/seed-data.sh     # optional demo data (admin + 10 doctors + catalog)
node scripts/docker-smoke-test.mjs   # end-to-end validation of the Dockerized stack
```

| What | URL |
|------|-----|
| Frontend (React SPA via Nginx) | http://localhost:3030 |
| API Gateway (+ centralized Swagger UI) | http://localhost:8080/swagger-ui.html |
| Eureka dashboard | http://localhost:8761 |

- The frontend bundle calls relative `/api` paths — Nginx reverse-proxies them to the gateway, so **no gateway URL is baked into the frontend**
- Ports match the local (non-Docker) setup exactly, so switching between the two requires no relearning
- Tear down: `docker compose down` (keep data) or `docker compose down -v` (full reset)
- Full guide (env vars, healthchecks, logs, troubleshooting): [`docs/10_DEPLOYMENT.md`](docs/10_DEPLOYMENT.md)

---

## CI/CD Pipeline — live

Every Pull Request runs the full test gate — a **matrix job per backend service** (`mvn test`, Java 17) plus the frontend (`tsc`, Vitest, production build) — then boots the **entire stack inside the runner** from freshly built images (`docker compose`), waits for every health check, and smokes the gateway, all five service health endpoints, the SPA, and a real signup→login flow. A broken test or a broken image = a red PR.

The pipeline is proven in production-of-record: PR #20 merged with a **green CI run** (including the full-stack compose smoke), and the post-merge **CD run pushed all 8 images to GHCR** — `ghcr.io/rameshpothamsetty/careq-<service>` tagged with the commit SHA and `develop-latest` (visible in the repo's **Packages** tab; the packages are private by default — flip them to public if you want them browsable). Pushes to `main` / `v*` tags produce release images (`latest` + version tag).

The pipeline extends to real deployment: `deploy` and `deploy-frontend` jobs roll the freshly-pushed images onto **Azure Container Apps** (OIDC auth, no stored secret) and the SPA onto **Vercel** (free Hobby plan) automatically on every `develop`/`main` push. Details: [`docs/10_DEPLOYMENT.md`](docs/10_DEPLOYMENT.md) § 3–4.

---

## Demo

> 🎥 **Demo video — coming soon.** A 2–3 minute walkthrough covering all three roles will be embedded here after recording.
>
> 📸 Screenshots of the patient live-status view, the doctor queue with AI triage badges, and the admin live overview will be added here.

---

## Project Status

| Module | Status |
|--------|--------|
| Project Scaffold | ✅ Complete |
| Authentication (JWT, Login, Signup) | ✅ Complete |
| User Module (Profiles, File Upload) | ✅ Complete |
| Doctor/Department Module | ✅ Complete |
| Queue Service + AI Triage & Wait Prediction | ✅ Complete |
| Frontend Integration (RTK Query, route guards, E2E) | ✅ Complete |
| **Week 1 Stabilization & v0.1 Release** | ✅ Complete |
| **Advanced Features (Analytics Dashboard + Notifications)** | ✅ Complete |
| **Dashboard & UX Enhancement Pass** — live doctor dashboard (stats, call-next hero, personal analytics, department context), live patient dashboard (My-visit widget, recent visits + recommendations), leave-queue, admin doctor detail drawer | ✅ Complete |
| **API Documentation Pass** — complete Swagger/OpenAPI annotations on all 4 business services, centralized Swagger UI aggregated at the gateway, standardized shared error response shape, API contract audit, complete Postman collection with auto-auth script | ✅ Complete |
| **Testing Pass** — 104 backend tests (auth 14, user 13, doctor 38, queue 39) + 23 frontend tests, 2 integration flows, Newman API run (31/31), manual checklist (38/38), BUG-1 & BUG-2 fixed | ✅ Complete |
| **Docker Containerization** — multi-stage Dockerfiles (non-root, healthchecks), Nginx frontend + `/api` proxy, `docker-compose.yml` full stack, `.env.example`, end-to-end smoke test 14/14 inside the containers | ✅ Complete |
| **Redis Caching + RabbitMQ Notification Service** — Redis catalog cache (60s TTL, evict-on-mutation, fail-open), RabbitMQ `careq.events` topic exchange, new `notification-service` (persisted notifications, `GET/PUT /api/notifications/**`), notification bell backed by real data, `/api/doctors/me` + admin doctor-account picker fixing the doctor queue dead-end | ✅ Complete |
| **Language Localization (i18n)** — zero-dependency i18n layer (English / हिन्दी / తెలుగు, persisted choice, `<html lang>` set), 🌐 switcher on login/signup + shared header, patient-facing screens, confirm dialogs, tooltips and notification toasts translated | ✅ Complete |
| **CI/CD Pipeline** — GitHub Actions: PR CI (per-service matrix tests, frontend typecheck/tests/build, in-pipeline docker-compose health check + auth smoke), CD on `develop` merge pushing all images to GHCR (`develop-latest` + SHA tags), release workflow for `main`/tags (`latest` + version), README badge. **PR #20 merged, CI green, CD green — 8 images live in GHCR** | ✅ Complete |
| **Azure + Vercel Cloud Deployment** — Bicep infra (Container Apps Environment + 9 apps, **free-tier MySQL Flexible Server** with private VNet access), frontend on **Vercel** (free), `deploy`/`deploy-frontend` jobs (OIDC login, secret injection, Vercel CLI deploy), Eureka FQDN registration for scale-to-zero, live smoke script | ✅ Live (careq-frontend-eta.vercel.app) |
| **Web Push Notifications (VAPID) + per-user preferences** — browser push delivery for queue events (free, no third-party service): `push_subscriptions` + `notification_preferences` tables, `GET/PUT /api/notifications/preferences`, `POST/DELETE /api/notifications/push/subscriptions`, async fail-open `WebPushDeliveryService` (dead-endpoint self-cleanup on 404/410), `public/sw.js` service worker, bell push toggle with permission flow, VAPID keygen script + Azure/Vercel wiring | ✅ Complete |

---

## Development Process

CareQ is built through **scoped engineering milestones** — each one a complete vertical slice: a scoped deliverable list, a dedicated `feature/*` or `chore/*` branch off `develop`, small incremental commits as each piece is built, a pull request with a checklist-driven description, and a self-review merge after the milestone's Definition of Done passes. Feature work is stabilized first, then extended — new features only ever build on a tagged, working base.

Every milestone's work is tracked as a GitHub Issue with a checked-off deliverable checklist, and the whole build is visible on the project board:

- 📋 [Project board](https://github.com/RameshPothamsetty/careq/projects) — Backlog / In Progress / In Review / Done
- ✅ [Closed Issues](https://github.com/RameshPothamsetty/careq/issues?q=is%3Aissue+is%3Aclosed) — completed tasks
- 📜 [Prompt Archive](docs/11_PROMPTS.md) — the prompts that drove each milestone

---

## API Documentation & Testing

- **Swagger UI (centralized):** [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html) — one browsable UI aggregating all four business services' OpenAPI docs through the API Gateway. Click **Authorize** (top right) and paste a JWT from login to test authenticated endpoints live with "Try it out".
- **Postman collection:** [`postman/CareQ.postman_collection.json`](postman/CareQ.postman_collection.json) + [`postman/CareQ.postman_environment.json`](postman/CareQ.postman_environment.json)
  1. Import **both** files into Postman (Import → select both).
  2. Select the **CareQ Local** environment (dropdown top-right).
  3. Run **Auth Service → Login** — its test script auto-captures the JWT into `{{authToken}}`.
  4. Every other request is pre-authenticated via `Authorization: Bearer {{authToken}}`.

> Swagger UI is intentionally public in this dev/demo setup; restrict `/v3/api-docs/**` and `/swagger-ui/**` before any production exposure.

## Documentation

| Document | Description |
|----------|-------------|
| [01_PROJECT_CONTEXT.md](docs/01_PROJECT_CONTEXT.md) | Tech stack, architecture, git strategy |
| [02_REQUIREMENTS.md](docs/02_REQUIREMENTS.md) | SRS — features, user stories, non-functional requirements |
| [03_ARCHITECTURE.md](docs/03_ARCHITECTURE.md) | Architecture diagram, service responsibilities, auth flow |
| [04_DATABASE.md](docs/04_DATABASE.md) | ER diagram, MySQL schema, column design rationale |
| [05_API_CONTRACT.md](docs/05_API_CONTRACT.md) | API contracts for all services (incl. shared error shape) |
| [09_TESTING.md](docs/09_TESTING.md) | Unit test plans and results |
| [11_PROMPTS.md](docs/11_PROMPTS.md) | Archive of daily development prompts |
| [12_MONITORING.md](docs/12_MONITORING.md) | Uptime checks, Log Analytics queries, backup/restore runbook |

## Phase 2 Roadmap (explicitly out of scope today)

Client-side derived notifications (session-only, resets on refresh) were an early scoping decision; that limitation was later removed with the Redis + RabbitMQ + notification-service work (see the Project Status table), which delivers persisted in-app notifications. Cloud deployment runs on **Azure Container Apps** + **Vercel** frontend + free-tier MySQL Flexible Server + Blob Storage for profile pictures (ephemeral Redis/RabbitMQ storage is the one documented trade-off — profile pictures persist in Blob Storage). The Phase 2 delivery item was **partially delivered**: browser **Web Push** is live (free, self-hosted VAPID — no account needed); **email/SMS** channels remain future work (the preference + delivery architecture is channel-agnostic: a per-user flag + a destination registry, so an email transport is a new class + flag, not a re-architecture). Launch-critical gaps are closed: email verification + password reset (Gmail SMTP — no third-party account needed), brute-force rate limiting, an access/audit log trail, Swagger locked down in production, MySQL TLS, security headers, and a free-tier monitoring + backup runbook (`docs/12_MONITORING.md`). The Phase 2 product/scale roadmap items are now **delivered**: **AI load-balancing** (auto-assign: symptoms → the single best available doctor, tied by shortest live wait), **WebSocket real-time push** (STOMP at `/ws` — the bell updates instantly, polling remains the fallback), **AI chat assistant** (heuristic intents over live queue/catalog data at `/api/queue/chat`), **RAG-style retrieval** (ranked `GET /api/doctors/search` scoring name/specialization/department/qualification, feeding the browse screen + chat grounding), **Redis caching** (doctor catalog, 60s TTL), **Receptionist role** (front-desk live queue management) and **billing** (per-visit invoices at the doctor's catalog fee with a sandbox payment flow). The only remaining roadmap item is **email/SMS notification channels** — the delivery architecture is channel-agnostic (a per-user flag + destination registry), so an email transport is a new class + flag, not a re-architecture. See [`docs/03_ARCHITECTURE.md`](docs/03_ARCHITECTURE.md) § 11–16 for the design decisions.

## Git Branching Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Production — always deployable (tagged `v0.1` at Week 1) |
| `develop` | Daily integration — PRs merge here |
| `feature/*` / `chore/*` | One branch per milestone/feature |


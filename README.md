# CareQ — Intelligent Patient Flow Platform

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
- **Profile** — manage phone, address, DOB, gender, and profile picture

### 🩺 Doctor
- **Live patient queue** — every patient's name, symptoms, AI triage badge, real queue position and predicted wait, polling every 10s
- **Search your queue by patient name** to find a specific patient fast
- **Triage override** — the doctor's clinical judgment is final and reorders the queue
- **Call next / complete** with one tap, and toggle your own availability (online/offline)

### ⚙️ Admin
- **Manage departments & doctors** — full CRUD with search, server-side pagination and sortable columns
- **User directory** — paginated, searchable (name/email) list of every registered user
- **Live queue overview** — hospital-wide summary cards (waiting, in-consultation, doctors online, delayed, avg wait) and a per-doctor breakdown

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite, Redux Toolkit Query, Tailwind CSS |
| Backend | Spring Boot 3.2, Java 17, Maven |
| Service Registry | Netflix Eureka |
| API Gateway | Spring Cloud Gateway (JWT validation + identity headers) |
| Inter-service calls | OpenFeign + LoadBalancer |
| Database | MySQL 8 |
| Auth | Spring Security + JWT (HMAC-SHA256) |
| AI | Groq (`llama-3.1-8b-instant`) — symptom triage with guaranteed fallback |
| Testing | JUnit 5 + Mockito (backend), Playwright (E2E) |

---

## Architecture

Six services collaborate through Eureka service discovery; all client traffic enters through the API Gateway, which validates the JWT and forwards `X-User-Id` / `X-User-Role` / `X-User-Name` / `X-User-Email` identity headers to downstream services.

```
Browser (React SPA :3030)
        │
        ▼
API Gateway (:8080)  ── validates JWT, forwards identity headers
        │
   ┌────┼─────────────┬──────────────┬──────────────┐
   ▼    ▼             ▼              ▼              ▼
 auth  user         doctor         queue        eureka-server
(:8081)(:8082)      (:8083)        (:8084)          (:8761)
         │                            │
         └── Feign call (avg consult time, availability) ──┘
                          └── Groq LLM (symptom triage)
```

The queue-service never duplicates doctor consultation data — it fetches `avgConsultationTimeMinutes` and `isAvailable` live from doctor-service via Feign (single source of truth). Position and predicted wait are derived on every read, never cached.

**See [`docs/03_ARCHITECTURE.md`](docs/03_ARCHITECTURE.md) for the full architecture, auth flow, and frontend state management design.**

---

## Local Setup

### Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8+ (running locally)
- Maven 3.9+
- A Groq API key (optional at runtime — without it, AI triage gracefully falls back to `NORMAL`)

### Environment variables

| Variable | Required | Used by | Default |
|----------|----------|---------|---------|
| `GROQ_API_KEY` | No¹ | queue-service AI triage | — |
| `JWT_SECRET` | No | auth-service + gateway | dev secret |
| `MYSQL_PASSWORD` | No | all DB-backed services | `root` |

¹ Without `GROQ_API_KEY` every patient is triaged `NORMAL`. Set it for the real AI behavior:

```bash
export GROQ_API_KEY="your-groq-key"          # bash
setx GROQ_API_KEY "your-groq-key"            # Windows (new shells only)
```

### 1. Database

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS careq_db;"
```

Tables are created/updated automatically by Hibernate (`ddl-auto: update`).

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

## Demo

> 🎥 **Demo video — coming soon.** A 2–3 minute walkthrough covering all three roles will be embedded here after recording.
>
> 📸 Screenshots of the patient live-status view, the doctor queue with AI triage badges, and the admin live overview will be added here.

---

## Project Status

| Day | Module | Status |
|-----|--------|--------|
| Day 1 | Project Scaffold | ✅ Complete |
| Day 2 | Authentication (JWT, Login, Signup) | ✅ Complete |
| Day 3 | User Module (Profiles, File Upload) | ✅ Complete |
| Day 4 | Doctor/Department Module | ✅ Complete |
| Day 5 | Queue Service + AI Triage & Wait Prediction | ✅ Complete |
| Day 6 | Frontend Integration (RTK Query, route guards, E2E) | ✅ Complete |
| **Day 7a** | **Week 1 Stabilization & v0.1 Release** | ✅ Complete |
| Day 7b | Advanced Features (analytics, notifications, …) | 📅 Planned |
| Days 8–15 | Deployment, hardening, Phase 2 roadmap | 📅 Planned |

---

## Development Process

CareQ is built over **15 one-day engineering sprints** under the TrainingMug ADF v1.0 framework. Each day is a complete vertical slice: a scoped deliverable list, a dedicated `feature/*` or `chore/*` branch off `develop`, small incremental commits as each piece is built, a pull request with a checklist-driven description, and a self-review merge after the day's Definition of Done passes. Feature work is stabilized first (Day 7a), then extended (Day 7b) — new features only ever build on a tagged, working base.

Every day's work is tracked as a GitHub Issue with a checked-off deliverable checklist, and the whole 15-day build is visible on the **CareQ — 15-Day Build** project board:

- 📋 [CareQ — 15-Day Build](https://github.com/RameshPothamsetty/careq/projects) — project board (Backlog / In Progress / In Review / Done)
- ✅ [Closed Issues](https://github.com/RameshPothamsetty/careq/issues?q=is%3Aissue+is%3Aclosed) — completed day tasks
- 📜 [Prompt Archive](docs/11_PROMPTS.md) — the full daily prompts that drove each day

---

## Documentation

| Document | Description |
|----------|-------------|
| [01_PROJECT_CONTEXT.md](docs/01_PROJECT_CONTEXT.md) | Tech stack, architecture, git strategy |
| [02_REQUIREMENTS.md](docs/02_REQUIREMENTS.md) | SRS — features, user stories, non-functional requirements |
| [03_ARCHITECTURE.md](docs/03_ARCHITECTURE.md) | Architecture diagram, service responsibilities, auth flow |
| [04_DATABASE.md](docs/04_DATABASE.md) | ER diagram, MySQL schema, column design rationale |
| [05_API_CONTRACT.md](docs/05_API_CONTRACT.md) | API contracts for all services |
| [09_TESTING.md](docs/09_TESTING.md) | Unit test plans and results |
| [11_PROMPTS.md](docs/11_PROMPTS.md) | Archive of daily development prompts |

## Git Branching Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Production — always deployable (tagged `v0.1` at Week 1) |
| `develop` | Daily integration — PRs merge here |
| `feature/*` / `chore/*` | One branch per day's work |

---

*Built for educational purposes as part of the TrainingMug ADF v1.0 framework.*

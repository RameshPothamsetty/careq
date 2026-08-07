# CareQ — Deployment Documentation

**Version:** 1.0 (Day 11 — Docker Containerization)

This document covers running the full CareQ stack with Docker Compose. The
manual local (non-Docker) setup remains in the README and is fully supported —
the two setups use the **same ports**, so switching between them requires no
relearning.

---

## 1. Docker Setup (Day 11)

### 1.1 Prerequisites

- Docker Desktop (or any Docker Engine with Compose v2) — `docker compose version` must work
- A Groq API key (optional — without it AI triage falls back to `NORMAL` by design)

### 1.2 Configure environment

```bash
cp .env.example .env
# edit .env — at minimum set JWT_SECRET to a long random value
```

The real `.env` is gitignored (confirmed in `.gitignore`). Every secret is
sourced from it — nothing is hardcoded in `docker-compose.yml` or the
Dockerfiles.

### 1.3 Build and start

```bash
docker compose up -d --build
```

Startup order is enforced with `depends_on` + `condition: service_healthy`:
**mysql → eureka-server → gateway + business services → frontend**.

| Service | Container | Host URL | Purpose |
|---------|-----------|----------|---------|
| MySQL 8 | `careq-mysql` | internal (`careq_db`) | single shared database |
| Eureka | `careq-eureka` | http://localhost:8761 | service registry + dashboard |
| API Gateway | `careq-gateway` | http://localhost:8080 | JWT validation + routing + Swagger UI |
| Auth | `careq-auth` | http://localhost:8081 | signup / login / JWT |
| User | `careq-user` | http://localhost:8082 | profiles + file uploads |
| Doctor | `careq-doctor` | http://localhost:8083 | departments + doctor catalog |
| Queue | `careq-queue` | http://localhost:8084 | queues + AI triage + wait prediction |
| Frontend (Nginx) | `careq-frontend` | http://localhost:3030 | React SPA |

> The MySQL container is **not** published to the host by default (it would
> clash with a local MySQL on :3306). Services reach it over the internal
> `careq-net` network. Uncomment `ports: ["3306:3306"]` in `docker-compose.yml`
> if you want host tooling access.

### 1.4 Seed data (recommended)

```bash
bash scripts/seed-data.sh
```

Creates admin (`admin@careq.com` / `admin123`), a test patient, 10 doctors
with named catalog entries, and heals orphaned entries (idempotent).

### 1.5 Smoke test

```bash
node scripts/day11-smoke-test.mjs
```

Checks, in order: frontend serves the SPA, all 5 services registered in
Eureka, gateway routing to every service health endpoint, admin login
(MySQL + JWT inside Docker), departments seeded, and the full patient journey
(signup → login → browse doctors → join queue with AI triage + wait-time
prediction → my-status active). Exit code 0 = all green.

### 1.6 Logs and teardown

```bash
docker compose logs -f queue-service   # follow one service
docker compose ps                      # status + health of every container
docker compose down                    # stop (data kept in volumes)
docker compose down -v                 # stop AND delete volumes (full reset)
```

### 1.7 Environment variables (`.env`)

| Variable | Required | Used by | Notes |
|----------|----------|---------|-------|
| `MYSQL_PASSWORD` | Yes¹ | mysql + all DB services | root password / datasource password (one shared DB `careq_db`) |
| `JWT_SECRET` | Yes² | api-gateway + auth-service | must be identical in both; `openssl rand -base64 48` |
| `GROQ_API_KEY` | No | queue-service | AI symptom triage; empty → fallback `NORMAL` |

¹ defaults to `root` if absent. ² defaults to a dev-only secret if absent — set a real one.

### 1.8 Validation results (Day 11, executed against the Dockerized stack)

Validated on Docker Desktop (Docker 29.6.2, Compose v5.3.1) on Windows:

| Check | Result |
|-------|--------|
| `docker compose up -d --build` (clean state, `down -v` first) | ✅ all 7 containers healthy, reproducible from scratch |
| Eureka registration | ✅ API-GATEWAY, AUTH-SERVICE, USER-SERVICE, DOCTOR-SERVICE, QUEUE-SERVICE all UP |
| Gateway routing | ✅ `/api/auth|users|doctors|queue/health` through :8080 → 200 (4/4) |
| Nginx `/api` proxy → gateway | ✅ `GET :3030/api/auth/health` → 200 |
| Frontend SPA on :3030 | ✅ 200, `id="root"` present |
| Seed script against Docker gateway | ✅ admin login OK, 10 doctors + catalog created |
| End-to-end patient journey | ✅ signup → login → browse doctors → join queue → wait-time prediction → my-status (all inside the containers) |
| AI triage | ✅ **real Groq classification verified** — with `GROQ_API_KEY` set in `.env`, a join with "persistent headache with blurred vision" triaged `EMERGENCY` (live LLM call from inside the container); fallback `NORMAL` is only used when the key is absent |

One issue found and fixed during validation: the frontend healthcheck used `localhost`, which busybox `wget` resolves to IPv6 `::1` while Nginx binds IPv4 — now `127.0.0.1`. A second issue: `user-service`'s named upload volume blocked its non-root user — the Dockerfile now pre-creates `/app/uploads` with the right ownership so Docker seeds the volume correctly.

### 1.9 How the pieces fit

- **Multi-stage Dockerfiles** — Maven build stage → slim `eclipse-temurin:17-jre` runtime; final images run as a non-root user and carry a `HEALTHCHECK` on `/actuator/health` (actuator added to every service in Day 11).
- **Frontend / API resolution** — the bundle is built with an empty `VITE_API_BASE_URL`, so it issues relative `/api` calls; the Nginx container reverse-proxies `/api` to the `api-gateway` service (SPA fallback for React Router). No gateway URL is baked into the bundle — trade-off: the image is coupled to Nginx, so serving the build elsewhere requires rebuilding or an equivalent proxy.
- **Configuration** — every service reads `EUREKA_URI` / `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` / `JWT_SECRET` from the environment with localhost defaults, so the same application.yml works for local `mvn spring-boot:run` and Docker.

---

## 2. Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| `docker compose up` fails to bind ports 8080–8084 / 8761 | The local (non-Docker) stack is running — stop it first (`docker compose down` only affects the Docker stack; stop the `mvn spring-boot:run` / MySQL processes, or use `docker compose down` after stopping them). |
| Container shows unhealthy for a long time | First boot downloads Maven deps / MySQL initializes — wait; `docker compose ps` shows `(healthy)` once ready. |
| `admin@careq.com` login fails in the smoke test | Run `bash scripts/seed-data.sh` against the Docker gateway first. |
| Queue joins always `NORMAL` triage | `GROQ_API_KEY` is empty/unset — that is the designed fallback. Set it in `.env` and restart queue-service. |
| Uploads 413 from Nginx | `client_max_body_size 10m` is set in `frontend/nginx.conf`; requests above 10 MB are rejected by design (user-service allows 10 MB max request). |

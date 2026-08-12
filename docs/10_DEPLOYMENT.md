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

---

## 3. CI/CD Pipeline (Day 14)

**Version:** 1.1 (Day 14 — GitHub Actions)

Three GitHub Actions workflows automate build, test, containerization and registry push. Nothing deploys to a live environment yet — Day 15 adds the actual deployment step.

### 3.1 Workflows

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `.github/workflows/ci.yml` | Every PR to `develop`/`main` | Backend matrix tests (one job per service, Java 17 + Maven cache), frontend `npm ci` → Vitest → `tsc`+Vite build, then an **in-pipeline health check**: builds all 8 images (loaded locally), boots the full `docker-compose` stack inside the runner, waits for every container to be `healthy`, and smokes the gateway health endpoint, all five service health endpoints (`/api/{auth,users,doctors,queue,notifications}/health`), the SPA root, and a real signup→login flow through the gateway. |
| `.github/workflows/cd-develop.yml` | Push to `develop` (post-PR-merge) | Re-runs the test gate, then builds and pushes every image to `ghcr.io/<owner>/careq-<service>` with tags `<commit SHA>` and `develop-latest`, followed by the same full-stack smoke. |
| `.github/workflows/cd-release.yml` | Push to `main` + tags `v*` | Same gate + build, pushed with the release version (tag name, or `main`) and `latest`. |

### 3.2 Image naming & tags

```
ghcr.io/rameshpothamsetty/careq-<service>
  ├── :<commit-sha>        # immutable per commit (develop + release)
  ├── :develop-latest      # latest on develop
  ├── :<version|main>      # e.g. v0.2, or main for untagged main pushes
  └── :latest              # latest release
```

`<service>` is one of `eureka-server`, `api-gateway`, `auth-service`, `user-service`, `doctor-service`, `queue-service`, `notification-service`, `frontend`. Image names must be lowercase — the workflow lowercases `github.repository_owner` before building GHCR tags.

### 3.3 Secrets

Only the built-in `GITHUB_TOKEN` is required (the workflows request `packages: write` to push to GHCR). No other secrets are needed for the current smoke test, which exercises health endpoints and the auth flow without AI triage. To exercise the real AI triage path in the pipeline later, add `GROQ_API_KEY` as a repository secret and reference `${{ secrets.GROQ_API_KEY }}` in the smoke job's environment — never hardcode it.

### 3.4 Verifying a run

- PRs: open the **Actions** tab — `CI` must be green (all matrix jobs + `docker-smoke`).
- After merging to `develop`: `CD · develop images` runs and the **Packages** page of the repo (`https://github.com/RameshPothamsetty/careq/pkgs`) shows `careq-<service>` images with `develop-latest`.
- Releases: tag `v0.2` → `CD · release` pushes `latest` + `v0.2`.

---

## 4. Azure Deployment (Day 15) — Vercel frontend + Azure backend, free-tier

**Version:** 1.3 (Day 15 — Azure Container Apps + **Vercel** frontend + **Azure Database for MySQL Flexible Server** free tier)

Live at **<GATEWAY_URL>** (backend gateway) / **https://careq-frontend.vercel.app** (frontend on Vercel) once the first deploy run completes (filled in on the Day 15 checklist).

### 4.1 Target architecture

```
Internet ──▶ Vercel (careq-frontend.vercel.app)   [free Hobby plan, always-on CDN]
                │  HTTPS, CORS-enabled
                ▼
        careq-api-gateway  ── external HTTPS ingress (always-on, min 1)   ◀── the only public backend
                │  routes via Eureka (FQDN registration)
   ┌────────────┼──────────────┬──────────────┬─────────────────┐
   ▼            ▼              ▼              ▼                 ▼
 careq-auth  careq-user    careq-doctor   careq-queue    careq-notification
 (min 1)     (min 0)       (min 0)        (min 0)         (min 0)
   │            │              │  ┌─ careq-redis (tcp :6379, min 0)
   │            │              ▼  └─ 60s catalog cache (fail-open)
   │            └──────────┐   │
   ▼                       ▼   ▼
 careq-eureka-server  careq-rabbitmq (tcp :5672, min 0)  ── careq.events ──┐
 (min 1)                  │  careq.notifications queue ─────────────────────┘
   │                       │          ┌─ Azure Blob Storage (5 GB free)
   ▼                       ▼          │   careq-uploads: profile pictures
 Azure Database for MySQL Flexible Server (careq-mysql, Standard_B1ms)
 (private access, no public endpoint, 32 GB — FREE for 12 months)
```

All nine container apps live in one VNet-injected Container Apps Environment
(`careq-env`, consumption-only, `careq-vnet` 10.0.0.0/16): `apps-subnet`
10.0.1.0/24 (delegated `Microsoft.App/environments`) and `mysql-subnet`
10.0.3.0/24 (delegated `Microsoft.DBforMySQL/flexibleServers`, private DNS
zone `private.mysql.database.azure.com`).**Scale-to-zero everywhere (cost policy — read this):**

| App | min / max | Why |
|-----|-----------|-----|
| careq-eureka-server | 0 / 1 | registry — HTTP rule wakes it on internal traffic |
| careq-api-gateway | 0 / 3 | every request enters here — HTTP rule wakes it |
| careq-auth-service | 0 / 3 | login/JWT — HTTP rule wakes it |
| careq-user-service | 0 / 3 | scale-to-zero, HTTP rule |
| careq-doctor-service | 0 / 3 | scale-to-zero, HTTP rule |
| careq-queue-service | 0 / 3 | scale-to-zero, HTTP rule |
| careq-notification-service | 0 / 3 | scale-to-zero, HTTP rule |
| careq-redis | 0 / 1 | TCP rule — wakes on first connection |
| careq-rabbitmq | 0 / 1 | TCP rule — wakes on first connection |

**Honest cost reality (fully free).** The frontend is **free forever**
(Vercel Hobby). The database is **free for 12 months** (MySQL Flexible Server
Burstable B1ms + 32 GB + 750 hrs/month — enough for 24/7; no HA, no geo
backup, which is exactly what the free tier requires). Profile pictures live
in **Blob Storage (5 GB free for 12 months)**. And because **every** container
app scales to zero, the backend rides the Container Apps monthly free grant
(180k vCPU-seconds + 360k GiB-seconds + 2M requests). Java services use the
smallest safe pair (0.5 vCPU / 1 GiB — 0.5 GiB is too tight for a JVM), so a
couple of hours of demoing per day sits roughly at the 50 free vCPU-hours;
heavy daily demos may exceed it by a **few dollars/month** (still far inside
the $200 trial credit for the interview window). Set a **budget alert** in
Azure Cost Management (50%/90% on `careq-rg`) and **tear down after
interviews** (`az group delete --name careq-rg --yes --no-wait`).

### 4.2 Cold-start & scale-to-zero behavior (measured on the Day 15 checklist)

- A scale-to-zero service wakes **on traffic through its app FQDN** — that's why
  services register with `CONTAINER_APP_HOSTNAME` + port 80 on Azure (see the
  `application-azure.yml` profile): inter-service traffic flows through the
  environment's Envoy proxy, whose request counting drives the HTTP scale rules.
- **Observed delay: <placeholder — fill in from the first live run>.** Expect the
  first request after ~5 min idle to take 10–60 s extra (or 503 on the very first
  hit while Eureka leases expire — the smoke script retries and reports it). With
  **all** apps now scaling to zero, the first hit of a demo can wake several
  services in sequence (gateway → auth → service → eureka). Warm up once
  (~1–2 min) before an interview demo, or flip `minReplicas: 0 → 1` for a
  service in `infra/azure/main.bicep` if it must never feel cold.

### 4.3 Known limitations (deliberate, not bugs)

- **Redis & RabbitMQ storage is ephemeral** — they run as containers with no
  persistent volume, so data is lost on restart/scale-down. Redis is a 60s
  fail-open cache (a few extra DB reads after wake-up); RabbitMQ's durable queue
  is re-declared idempotently on boot, and queue-service's publisher is
  non-blocking by design — a broker outage logs a warning, never fails a join.
- **User profile pictures persist via Azure Blob Storage** (free 5 GB tier,
  public-read `careq-uploads` container). Uploads survive redeploys and scale
  downs. Trade-off: the container is public-read (fine for a demo; use SAS
  tokens or a private container + signed URLs before any production use).
- **No custom domains** — Vercel's default `*.vercel.app` and Azure's default
  `*.azurecontainerapps.io` URLs are used (explicitly out of scope today).
- **MySQL free tier is 750 hours/month and lasts 12 months** — a single
  24/7 server fits comfortably (744 h in a 31-day month), but after 12 months
  (or if you add a second server) billing starts (~$25/mo for B1ms + 32 GB).
  Plan to tear down after interviews, or accept the small cost if you keep
  demoing.
- **MySQL SSL is disabled (`require_secure_transport=OFF`)** — a demo
  convenience so the app's existing `useSSL=false` JDBC URLs work unchanged.
  For a hardened deployment turn it back ON and add `useSSL=true&requireSSL=true`
  to the datasource URL in `application.yml`.

### 4.4 One-time provisioning (run once, ~10–20 min)

Assumes: `careq-rg` exists; OIDC App Registration with Contributor on `careq-rg`
and `AZURE_CLIENT_ID` / `AZURE_TENANT_ID` / `AZURE_SUBSCRIPTION_ID` GitHub secrets.

```bash
cd infra/azure
MYSQL_PASSWORD="$(openssl rand -base64 24)" \
./provision.sh
```

What it provisions (`infra/azure/main.bicep`): VNet + subnets, **Azure Database
for MySQL Flexible Server** (free tier: B1ms burstable, 32 GB, private access),
Log Analytics, the Container Apps Environment, **nine** container apps (seven
GHCR images + `redis:7-alpine` + `rabbitmq:3-management`), then creates the
`careq_db` database and disables SSL enforcement. The script prints the live
gateway URL, the MySQL private FQDN, and the full GitHub secrets list.

> **Frontend is NOT provisioned here** — it deploys to Vercel (free). After
> provisioning, create the Vercel project once:
> `cd frontend && npx vercel link && npx vercel env add VITE_API_BASE_URL production`
> (value = `https://<gateway-fqdn>`), then copy `orgId` / `projectId` from
> `frontend/.vercel/project.json` into the `VERCEL_ORG_ID` / `VERCEL_PROJECT_ID`
> GitHub secrets.
>
> **Region capacity:** the free-tier MySQL SKU (Standard_B1ms) is
> capacity-restricted in some regions right now. `provision.sh` probes the
> requested region and auto-falls back to the first of 12 candidates that
> offers the SKU. (Preflight failures create nothing, so re-running is clean.)
> There is **no paid fallback needed anymore** — the free Flexible Server
> replaces the old Standard_B2s MySQL VM entirely.
>
> **GHCR image pulls:** the apps pull `ghcr.io/<owner>/careq-*` images. If those
> packages are **private**, pass `ghcrUsername=<your-gh-username>` to the deploy
> and add the `GHCR_PAT` GitHub secret (see § 4.5). If they're public (default
> for a public repo), leave both unset.

### 4.5 GitHub secrets (all required before the first deploy succeeds)

| Secret | Needed by | How to get it |
|--------|-----------|---------------|
| `AZURE_CLIENT_ID` | OIDC login (already present) | App Registration |
| `AZURE_TENANT_ID` | OIDC login (already present) | App Registration |
| `AZURE_SUBSCRIPTION_ID` | OIDC login (already present) | Subscription |
| `JWT_SECRET` | gateway + auth | `openssl rand -base64 48` (new value for Azure) |
| `MYSQL_PASSWORD` | all MySQL-backed services | **must equal** the `MYSQL_PASSWORD` used at provisioning (it is the Flexible Server admin password) |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | queue + notification + broker | generated at provisioning |
| `GROQ_API_KEY` | queue-service AI triage | optional — empty → triage falls back to `NORMAL` |
| `GHCR_PAT` | image pulls | **optional** — only if the ghcr.io packages are private (fine-grained PAT, `packages:read`) |
| `VERCEL_TOKEN` | frontend deploy | vercel.com → Account Settings → Tokens → Create |
| `VERCEL_ORG_ID` | frontend deploy | `orgId` in `frontend/.vercel/project.json` after `npx vercel link` |
| `VERCEL_PROJECT_ID` | frontend deploy | `projectId` in the same `frontend/.vercel/project.json` |
| `AZURE_STORAGE_CONNECTION_STRING` | user-service profile pictures | `az storage account show-connection-string -n <account> -g careq-rg --query connectionString -o tsv` (printed by `provision.sh`) |

> The old `AZURE_STATIC_WEB_APPS_API_TOKEN` is no longer needed — the frontend
> moved to Vercel (Day 15 revision).

### 4.6 Recurring deployment (automatic, no manual steps)

The `deploy` and `deploy-frontend` jobs in `cd-develop.yml` / `cd-release.yml`
run after images pass the full-stack smoke, on every **branch** push to
`develop`/`main` (tag-triggered release runs skip deployment):

1. `azure/login@v2` — OIDC, **no client secret stored anywhere**.
2. `az containerapp update` per service — new image (`ghcr.io/<owner>/careq-<svc>:<sha|main>`)
   + all secrets (GitHub secrets → Container App secrets; `secretref:` env vars pick
   them up automatically — no app restart needed beyond the revision roll).
3. Gateway CORS env pointed at `https://careq-frontend.vercel.app`.
4. Frontend: `deploy-frontend` runs the **Vercel CLI** in `frontend/`
   (`vercel pull` → `vercel build` → `vercel deploy --prebuilt --prod`).
   `VITE_API_BASE_URL` is set **once** in the Vercel project's production env
   (the gateway FQDN is stable), so no DNS lookup or URL baking is needed in
   the pipeline.

### 4.7 Verify a deployment

```bash
# URLs
GATEWAY_URL="https://$(az containerapp show -n careq-api-gateway -g careq-rg --query properties.configuration.ingress.fqdn -o tsv)"
FRONTEND_URL="https://careq-frontend.vercel.app"

# Seed the live DB (once):
API_BASE="$GATEWAY_URL" bash scripts/seed-data.sh

# Full smoke against the live URLs (signup → login → browse → join → AI triage → status):
API_BASE="$GATEWAY_URL" FRONTEND_BASE="$FRONTEND_URL" node scripts/day15-azure-smoke.mjs

# Logs (one app):
az containerapp logs show -n careq-queue-service -g careq-rg --type console

# Eureka dashboard (internal only — reachable from within the env):
az containerapp exec -n careq-eureka-server -g careq-rg --command curl -s http://localhost:8761
```

### 4.8 Teardown

Delete EVERYTHING (all apps, environment, Flexible Server, VNet, logs):

```bash
az group delete --name careq-rg --yes --no-wait
```

Then remove the GitHub secrets (or leave them for a future re-provision — the
OIDC federation can stay). The Vercel project can be deleted from the Vercel
dashboard (it costs nothing while dormant). Nothing else is created outside
`careq-rg`.

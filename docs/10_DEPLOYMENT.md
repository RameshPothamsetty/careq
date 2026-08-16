# CareQ — Deployment Documentation

**Version:** 1.0 — Docker Containerization

This document covers running the full CareQ stack with Docker Compose. The
manual local (non-Docker) setup remains in the README and is fully supported —
the two setups use the **same ports**, so switching between them requires no
relearning.

---

## 1. Docker Setup

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
node scripts/docker-smoke-test.mjs
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
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | No | notification-service | Web Push; generate with `bash scripts/generate-vapid-keys.sh` — empty → push disabled, in-app notifications unaffected |
| `VITE_VAPID_PUBLIC_KEY` | No | frontend build | = `VAPID_PUBLIC_KEY`; baked into the bundle (compose build arg / Vercel env) so the bell shows the push toggle — empty → toggle hidden |
| `SENDGRID_API_KEY` | No¹ | auth-service | email verification + password reset; https://app.sendgrid.com/settings/api_keys + verify a single sender (Settings → Sender Authentication → Single Sender Verification). Empty → verification stays OFF |
| `SENDGRID_FROM` | No¹ | auth-service | the **verified sender address** used as `APP_MAIL_FROM` on Azure (e.g. `rap53748@gmail.com`); unset → falls back to `careq@careq.com` (which SendGrid will reject unless you own it — set this secret!) |
| `APP_MAIL_FROM` / `APP_FRONTEND_BASE_URL` / `AUTH_EMAIL_VERIFICATION_ENABLED` | No¹ | auth-service | sender + email-link base URL + verification gate. ¹SendGrid key and `AUTH_EMAIL_VERIFICATION_ENABLED=true` are both-or-neither |

¹ defaults to `root` if absent. ² defaults to a dev-only secret if absent — set a real one.

### 1.8 Validation results (executed against the Dockerized stack)

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

- **Multi-stage Dockerfiles** — Maven build stage → slim `eclipse-temurin:17-jre` runtime; final images run as a non-root user and carry a `HEALTHCHECK` on `/actuator/health` (actuator enabled on every service).
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

## 3. CI/CD Pipeline

**Version:** 1.1 — GitHub Actions

Three GitHub Actions workflows automate build, test, containerization and registry push. The live deployment step (Azure Container Apps + Vercel) is described in § 4.

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

## 4. Azure Deployment — Vercel frontend + Azure backend, free-tier

**Version:** 1.4 — Azure Container Apps + **Vercel** frontend + **Azure Database for MySQL Flexible Server** free tier; live-run fixes 2026-08-13

**Live URLs (verified working end-to-end):**

- Frontend (Vercel): `https://careq-frontend-eta.vercel.app`
- Backend gateway (Azure): `https://careq-api-gateway.salmonforest-402be170.southindia.azurecontainerapps.io`

(§ 4.7 shows how to re-fetch the gateway FQDN dynamically; § 4.8 explains why the
frontend URL carries the `-eta` suffix.)

### 4.1 Target architecture

```
Internet ──▶ Vercel (careq-frontend-eta.vercel.app)   [free Hobby plan, always-on CDN]
                │  HTTPS, CORS-enabled
                ▼
        careq-api-gateway  ── external HTTPS ingress (wakes on traffic, min 0)   ◀── the only public backend
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
Azure Cost Management (50%/90% on `careq-rg-south`) and **tear down after
interviews** (`az group delete --name careq-rg-south --yes --no-wait`).

### 4.2 Cold-start & scale-to-zero behavior (measured on the live-run checklist)

- A scale-to-zero service wakes **on traffic through its app FQDN** — that's why
  services register with `CONTAINER_APP_HOSTNAME` + port 80 on Azure (see the
  `application-azure.yml` profile): inter-service traffic flows through the
  environment's Envoy proxy, whose request counting drives the HTTP scale rules.
- **Observed delay: ~6–8 s for a single gateway container to start (measured from
  live container logs); a fully cold chain (gateway → eureka → auth → MySQL) on
  the very first request after idle took 10–60 s+, long enough that the frontend
  now retries 502/503/504 + dropped connections for ~56 s (see § 4.8).** Expect the
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

Assumes: `careq-rg-south` exists; OIDC App Registration with Contributor on `careq-rg-south`
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
> `cd frontend && npx vercel link --project careq-frontend && npx vercel env add VITE_API_BASE_URL production`
> (value = `https://<gateway-fqdn>`), then copy `orgId` / `projectId` from
> `frontend/.vercel/project.json` into the `VERCEL_ORG_ID` / `VERCEL_PROJECT_ID`
> GitHub secrets.
>
> **Note (v1.4):** the CD pipeline now bakes `VITE_API_BASE_URL` into the build
> itself (see § 4.6), so the project env var is only needed for manual
> `npx vercel --prod` deploys — relying on the project env alone silently
> shipped a bundle with relative `/api` calls (see § 4.8, issue 4).
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
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | notification-service Web Push | **optional** — both must be set together; `bash scripts/generate-vapid-keys.sh` → paste the two values. Not set → push delivery disabled |
| `SENDGRID_API_KEY` | auth-service email verification/reset | **optional but both-or-neither** — with it, the pipeline also sets `AUTH_EMAIL_VERIFICATION_ENABLED=true`; without it, verification stays off so signups never get stuck. Free tier: 100 emails/day. Also verify a **single sender** in SendGrid and set `SENDGRID_FROM` to that exact address (the pipeline uses it as `APP_MAIL_FROM`; without it the default `careq@careq.com` will be rejected as unverified) |
| `SENDGRID_FROM` | auth-service `APP_MAIL_FROM` | **optional but strongly recommended** — your SendGrid-verified sender (e.g. `rap53748@gmail.com`). Unset → `careq@careq.com` is used, which only works if you verify that address |
| `GHCR_PAT` | image pulls | **optional** — only if the ghcr.io packages are private (fine-grained PAT, `packages:read`) |
| `VERCEL_TOKEN` | frontend deploy | vercel.com → Account Settings → Tokens → Create |
| `VERCEL_ORG_ID` | frontend deploy | `orgId` in `frontend/.vercel/project.json` after `npx vercel link` |
| `VERCEL_PROJECT_ID` | frontend deploy | `projectId` in the same `frontend/.vercel/project.json` |
| `AZURE_STORAGE_CONNECTION_STRING` | user-service profile pictures | `az storage account show-connection-string -n <account> -g careq-rg-south --query connectionString -o tsv` (printed by `provision.sh`) |

> The old `AZURE_STATIC_WEB_APPS_API_TOKEN` is no longer needed — the frontend
> moved to Vercel.

> **Launch hardening on the live site:**
> 1. Add `SENDGRID_API_KEY` to GitHub secrets (both-or-neither contract: the
>    pipeline then sets `AUTH_EMAIL_VERIFICATION_ENABLED=true` on Azure).
> 2. **Existing accounts stay verified** (no rows in the new `auth_tokens`
>    table → treated as verified) — the seed doctors/admin/patients keep
>    logging in. New signups must click the emailed link before their first
>    login.
> 3. Swagger/OpenAPI is **switched off in production** (`SPRINGDOC_ENABLED=false`)
>    on every service; it stays on for local dev.
> 4. **MySQL TLS:** the pipeline sets `MYSQL_CONN_PARAMS=sslMode=REQUIRED…` on
>    all DB services; the bicep sets `requireSecureTransport: true`. After a
>    deploy is confirmed healthy, flip the live server with
>    `az mysql flexible-server update -g careq-rg-south -n careq-mysql --require-secure-transport Enabled`
>    (never before — plain connections would be refused).
> 5. **Monitoring/backups:** see `docs/12_MONITORING.md` — uptime workflow
>    (GitHub issue on failure), Log Analytics queries, MySQL point-in-time
>    restore runbook.

> **Web Push on the live site:** after adding the two VAPID secrets,
> also set `VITE_VAPID_PUBLIC_KEY` (= the same public key) in the Vercel
> project's **production** env (`npx vercel env add VITE_VAPID_PUBLIC_KEY production`)
> and redeploy the frontend — without it the bell's push toggle is hidden.
> Push requires HTTPS, which Vercel provides; browsers then show the native
> permission prompt when a patient flips the toggle.

### 4.6 Recurring deployment (automatic, no manual steps)

The `deploy` and `deploy-frontend` jobs in `cd-develop.yml` / `cd-release.yml`
run after images pass the full-stack smoke, on every **branch** push to
`develop`/`main` (tag-triggered release runs skip deployment):

1. `azure/login@v2` — OIDC, **no client secret stored anywhere**.
2. `az containerapp update` per service — new image (`ghcr.io/<owner>/careq-<svc>:<sha|main>`)
   + all secrets (GitHub secrets → Container App secrets; `secretref:` env vars pick
   them up automatically — no app restart needed beyond the revision roll).
3. Gateway CORS env pointed at `https://careq-frontend-eta.vercel.app` (the
   real Vercel project's URL — see § 4.8 for why it is not the `careq-frontend`
   project).
4. Frontend: `deploy-frontend` runs the **Vercel CLI** in `frontend/`
   (`vercel pull` → `vercel build` → `vercel deploy --prebuilt --prod`), with
   `VITE_API_BASE_URL` **passed to `vercel build` directly** (hardcoded in the
   workflow — the gateway FQDN is stable). Baking it in guarantees the bundle
   always calls the gateway; relying on the Vercel project env alone shipped a
   bundle with relative `/api` calls (see § 4.8, issue 4).

### 4.7 Verify a deployment

```bash
# URLs
GATEWAY_URL="https://$(az containerapp show -n careq-api-gateway -g careq-rg-south --query properties.configuration.ingress.fqdn -o tsv)"
FRONTEND_URL="https://careq-frontend-eta.vercel.app"

# Seed the live DB (once):
API_BASE="$GATEWAY_URL" bash scripts/seed-data.sh

# Full smoke against the live URLs (signup → login → browse → join → AI triage → status):
API_BASE="$GATEWAY_URL" FRONTEND_BASE="$FRONTEND_URL" node scripts/azure-smoke.mjs

# Logs (one app):
az containerapp logs show -n careq-queue-service -g careq-rg-south --type console

# Eureka dashboard (internal only — reachable from within the env):
az containerapp exec -n careq-eureka-server -g careq-rg-south --command curl -s http://localhost:8761
```

### 4.8 Live-run fixes (verified on the first real deployment, 2026-08-13)

The first real deployment exposed five issues that were fixed and verified
end-to-end (live smoke test 15/15 — a fresh patient journey through the real
URLs: signup → login → browse doctors → join queue → AI triage → status):

| # | Symptom | Root cause | Fix |
|---|---------|-----------|-----|
| 1 | Signup crashed with `Failed to execute 'json' on 'Response': Unexpected end of JSON input` | `frontend/src/services/api.ts` called `response.json()` unconditionally; cold-start 502/503 responses have an **empty body** | Defensive body parsing (empty body → readable message) + retry 502/503/504 **and** dropped connections for ~56 s (`COLD_START_MAX_ATTEMPTS=8`, 8 s apart) |
| 2 | Browser got 403 on every API call while curl worked | Gateway read `@Value("${app.cors.allowed-origins}")` but Spring maps env var `CORS_ALLOWED_ORIGINS` to `cors.allowed-origins` — the value never reached the app, so the localhost defaults were used | `application.yml` now maps the env var explicitly; verified preflight from the real origin → 200 + `access-control-allow-origin` on both preflight **and** actual responses |
| 3 | `careq-frontend.vercel.app` served a **Next.js** app (not this repo) | That domain belongs to a different/older Vercel project; this repo's app lives in the `careq-frontend` project | Use `https://careq-frontend-eta.vercel.app`; workflows' CORS updated to match |
| 4 | Signup failed after deploys — frontend showed "Service is warming up" | Pipeline-deployed bundles had **no gateway URL baked in**: relative `/api` calls hit Vercel, which answers POST with **405 + empty body** → frontend showed the warm-up message (my curl checks passed because curl ignores that) | `VITE_API_BASE_URL` is now passed to `vercel build` in `cd-develop.yml` / `cd-release.yml` (was set only in the Vercel project env, which the pipeline's `vercel pull` didn't reliably deliver) |
| 5 | First request after ~5 min idle waits 10–60 s+ | All 9 container apps scale to zero by design (free-tier cost policy) | Frontend retries through the window (~56 s); for zero-latency demos flip `minReplicas: 0 → 1` on gateway + auth-service in `infra/azure/main.bicep` |

> **Correct frontend verification** — check the actual JS bundle, not Vercel's
> SPA fallback (a bare `curl .../assets/index-*.js` hits the fallback HTML):
>
> ```bash
> BUNDLE=$(curl -s https://careq-frontend-eta.vercel.app/ | grep -oE 'src="[^"]*\.js[^"]*"' | head -1 | grep -oE 'index-[^"]+\.js')
> curl -s "https://careq-frontend-eta.vercel.app/assets/$BUNDLE" | grep -c "salmonforest-402be170"   # expect: 1
> ```

### 4.9 Teardown

Delete EVERYTHING (all apps, environment, Flexible Server, VNet, logs):

```bash
az group delete --name careq-rg-south --yes --no-wait
```

Then remove the GitHub secrets (or leave them for a future re-provision — the
OIDC federation can stay). The Vercel project can be deleted from the Vercel
dashboard (it costs nothing while dormant). Nothing else is created outside
`careq-rg-south`.

# 12 — Monitoring, Alerts & Backups (Day 17)

The live CareQ deployment runs on **free tiers** (Azure Container Apps free
grant, Vercel Hobby, free-12-month MySQL). This page is the operational
runbook: what's monitored automatically, how to watch the live system
yourself, how to query the audit trail, and how to restore the database.

---

## 1. What's automatic (free, set-and-forget)

| Mechanism | What it does | Where it lives |
|---|---|---|
| **Uptime check (every 15 min)** | Pings the gateway health endpoint + the Vercel frontend. On failure it **opens a GitHub issue** ("⚠️ CareQ uptime") — which lands in your GitHub notification inbox. It auto-closes when the next check passes. | `.github/workflows/uptime-check.yml` + `scripts/uptime-check.mjs` |
| **CD pipeline test gate** | Every push runs the full backend test suite before deploying; a red suite never deploys. | `.github/workflows/ci.yml`, `cd-develop.yml`, `cd-release.yml` |
| **MySQL automatic backups** | Azure Flexible Server backs up the DB automatically (7-day retention, point-in-time restore). Nothing to configure; restore steps in §4. | Azure portal → `careq-mysql` → Backups |

Run the uptime workflow once manually to see it in action:

```bash
gh workflow run uptime-check.yml
```

---

## 2. Quick health checks (no portal needed)

```bash
# Gateway is alive
curl -s https://careq-api-gateway.salmonforest-402be170.southindia.azurecontainerapps.io/actuator/health

# Full patient-journey smoke (15 checks) — the strongest single signal
API_BASE="https://careq-api-gateway.salmonforest-402be170.southindia.azurecontainerapps.io" \
FRONTEND_BASE="https://careq-frontend-eta.vercel.app" \
node scripts/day15-azure-smoke.mjs
```

> The smoke runs as the **seeded smoke patient** (`patient.smoke@careq.com`
> / `password123`) because Day 17 email verification blocks fresh signups
> from logging in. If that account is ever missing, re-seed:
> `API_BASE=<gateway> bash scripts/seed-data.sh`.

---

## 3. Watching it yourself

### Azure portal (backend)

1. portal.azure.com → **Container Apps** → e.g. `careq-api-gateway`
   - **Logs** — the app's console output (your `ACCESS` and `AUDIT` lines
     live here).
   - **Revisions** — which build is live, whether a deploy finished.
   - **Console** — a shell inside the container.
2. **Resource groups → `careq-rg-south` → Activity log** — deploy failures,
   scale events.
3. **Cost Management → Cost analysis** — actual spend vs. the free grant.

### Vercel (frontend)

vercel.com → `careq-frontend` → **Deployments** (did the build pass) and
**Analytics** (visitors, errors).

### Log Analytics (queryable logs — one-time setup)

```bash
bash scripts/enable-diagnostics.sh    # routes all 9 apps' logs to careq-logs
```

Then in the portal: **Log Analytics → careq-logs → Logs**:

```kusto
// All 5xx responses in the last hour
ContainerAppConsoleLogs
| where TimeGenerated > ago(1h) and Log contains "status=5"
| project TimeGenerated, ContainerAppName, Log
| take 50

// Access-log audit trail (gateway ACCESS logger)
ContainerAppConsoleLogs
| where TimeGenerated > ago(1d)
| where Log startswith "method=" and Log contains "path=/api/queue"
| project TimeGenerated, Log

// Security events from auth-service (AUDIT logger)
ContainerAppConsoleLogs
| where TimeGenerated > ago(1d)
| where Log contains "event=login"
| project TimeGenerated, Log
| take 100
```

---

## 4. Backups & restore

### What's backed up

- **MySQL (`careq_db`)** — Azure automatic backups, **7-day retention**,
  point-in-time restore. This covers ALL business data (users, profiles,
  queue entries, notifications, push subscriptions, preferences).
- **Profile pictures** — live in Azure Blob Storage (`careq-uploads`),
  outside the DB. LRS redundancy within the region; no cross-region copy
  (free tier).
- **Redis + RabbitMQ** — **NOT durable** (documented Day 15 trade-off):
  queue position data is in MySQL, but in-flight RabbitMQ messages and
  Redis tokens reset on restart. By design.

### Point-in-time restore (MySQL)

1. Portal → **Azure Database for MySQL flexible servers** → `careq-mysql`.
2. Left menu → **Restore** → pick a restore point (≤7 days back) → create
   `careq-mysql-restore` (a NEW server, same region).
3. To make the restored server live: either
   - repoint the apps (`MYSQL_HOST=careq-mysql-restore.private.mysql.database.azure.com`
     via `az containerapp update --set-env-vars ...` for the 5 DB services), or
   - migrate data from the restore to the original with
     `mysqldump` (see below), or
   - simply keep the restore server as the new primary and delete the old.

### Manual dump (belt & braces, optional)

```bash
# mysqldump from inside the VNet (DB has no public endpoint):
az containerapp exec -n careq-auth-service -g careq-rg-south --command sh -c \
  "apt-get update >/dev/null 2>&1; apt-get install -y default-mysql-client >/dev/null 2>&1; \
   mysqldump -h careq-mysql.private.mysql.database.azure.com -u careqadmin -p'$MYSQL_PASSWORD' careq_db"
# Redirect the output to a file on your laptop to keep an off-Azure copy.
```

---

## 5. Alerting cheat-sheet

| Signal | How it reaches you |
|---|---|
| Site down (gateway or frontend) | GitHub issue via uptime workflow → GitHub notification |
| Deploy failed | GitHub Actions run failure notification |
| Backend 5xx spike | Not automatic — query Log Analytics (§3) or watch the smoke script |
| Cost running hot | Azure Cost Management (free grant ~$0/mo at demo usage) |

If you want email/SMS alerting beyond GitHub notifications, the natural next
step is an Azure Alert rule on the uptime or an external uptime service —
not built here to stay free.

---

*See also: [`docs/10_DEPLOYMENT.md`](10_DEPLOYMENT.md) (deployment +
Day 15 live-run fixes), [`docs/09_TESTING.md`](09_TESTING.md) (test
strategy), and `scripts/day15-azure-smoke.mjs`.*

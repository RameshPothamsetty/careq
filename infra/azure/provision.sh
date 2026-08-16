#!/usr/bin/env bash
# =====================================================================
# CareQ — ONE-TIME Azure provisioning (run yourself)
#
#   Run this ONCE. Everything after it is recurring and automated by the
#   GitHub Actions deploy job on every push to develop/main.
#
# Prerequisites:
#   1. az CLI logged in:            az login
#   2. Subscription set:            az account set --subscription "<name or id>"
#   3. Resource group exists:       az group create -n careq-rg-south -l <region>
#   4. Env vars below (or edit defaults)
#
# Usage:
#   cd infra/azure
#   MYSQL_PASSWORD='<long random>' ./provision.sh
#
# What it provisions (all FREE-eligible):
#   • Azure Database for MySQL Flexible Server (Burstable Standard_B1ms,
#     32 GB — FREE for 12 months, 750 hrs/month) with private access
#   • VNet + Container Apps Environment + 9 container apps
#   • careq_db database + non-SSL connections allowed (demo)
# The frontend is NOT provisioned here — it deploys to Vercel (free Hobby).
#
# The script prints the live Gateway URL, the MySQL private FQDN and the
# FULL list of GitHub secrets the deploy workflows need.
#
# Never commit this script's outputs or your passwords to git.
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Configuration ─────────────────────────────────────────────────────
RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-careq-rg-south}"
LOCATION="${AZURE_LOCATION:-eastus2}"
GHCR_OWNER="${GHCR_OWNER:-rameshpothamsetty}"
IMAGE_TAG="${IMAGE_TAG:-develop-latest}"
MYSQL_SERVER_NAME="${MYSQL_SERVER_NAME:-careq-mysql}"
MYSQL_ADMIN_USER="${MYSQL_ADMIN_USER:-careqadmin}"
STORAGE_ACCOUNT_NAME="${STORAGE_ACCOUNT_NAME:-carequploads}"
# Standard_B1ms = free 12-month tier. If burstable capacity is unavailable
# everywhere, switch to Standard_B2s (paid ≈$25-30/mo, inside the trial credit).
MYSQL_SKU="${MYSQL_SKU:-Standard_B1ms}"
# Set SKIP_REGION_CHECK=1 to skip the (flaky) list-skus preflight and just
# attempt the real deployment — Azure itself then decides if the SKU fits.
SKIP_REGION_CHECK="${SKIP_REGION_CHECK:-0}"

# Required secrets — generate them ONCE here, reuse the SAME values later
# when you add them to GitHub secrets.
MYSQL_PASSWORD="${MYSQL_PASSWORD:?Set me, e.g.  openssl rand -base64 24}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"
RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-careq_bus}"
RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-$(openssl rand -base64 24)}"

echo "============================================================"
echo "  CareQ — Azure provisioning (one-time, free-tier focused)"
echo "  Resource group : $RESOURCE_GROUP (requested $LOCATION)"
echo "  MySQL          : $MYSQL_SERVER_NAME (Flexible Server, B1ms — free 12 mo)"
echo "  Images         : ghcr.io/$GHCR_OWNER/careq-*:$IMAGE_TAG"
echo "  Frontend       : Vercel (not provisioned here)"
echo "============================================================"
echo ""

echo "▶ Ensuring Azure CLI + extensions..."
az extension add -n containerapp --yes 2>/dev/null || true
az extension add -n mysql --upgrade --yes 2>/dev/null || true
az config set extension.use_dynamic_install=yes_without_prompt

# ── Region check ──────────────────────────────────────────────────────
# Two constraints on this subscription:
#   1. the MySQL Flexible Server burstable SKU (Standard_B1ms) must be
#      provisionable in the region (burstable is capacity-restricted in
#      some regions right now), AND
#   2. the region must be ENABLED for the resource types we use
#      (Container Apps + MySQL Flexible Server).
# If the requested region fails, auto-fall back to the first candidate
# that passes both.
FALLBACK_REGIONS=(centralindia southindia jioindiawest jioindiacentral indiasouthcentral southeastasia eastus2 centralus westus2 westeurope northeurope eastus southcentralus westus3 uksouth germanywestcentral swedencentral polandcentral italynorth)

# Register the providers this stack needs (free) so the checks below are
# authoritative instead of fail-open.
echo "▶ Registering Azure resource providers (free, no charge)..."
for ns in Microsoft.App Microsoft.OperationalInsights Microsoft.DBforMySQL Microsoft.Network; do
  state="$(az provider show -n "$ns" --query registrationState -o tsv 2>/dev/null || true)"
  if [ "$state" != "Registered" ]; then
    az provider register -n "$ns" --accept-terms >/dev/null 2>&1 || true
    for _ in $(seq 1 15); do
      [ "$(az provider show -n "$ns" --query registrationState -o tsv 2>/dev/null || true)" = "Registered" ] && break
      sleep 2
    done
  fi
done

mysql_sku_available() {
  # list-skus can throw transient InternalServerError on capacity-stressed
  # regions — retry 3x before declaring the SKU unavailable.
  local result=""
  for _ in 1 2 3; do
    result="$(az mysql flexible-server list-skus --location "$1" \
      --query "[?name=='$MYSQL_SKU'] | length(@)" -o tsv 2>/dev/null || true)"
    [ "$result" = "1" ] && return 0
    sleep 3
  done
  return 1
}

region_ok() {
  mysql_sku_available "$1"
}

if [ "$SKIP_REGION_CHECK" != "1" ]; then
  echo "▶ Checking $MYSQL_SKU (MySQL) in $LOCATION..."
  if ! region_ok "$LOCATION"; then
    echo "❌ $LOCATION can't run $MYSQL_SKU right now (or the flaky list-skus API errored)."
    echo "   Trying fallback regions..."
    FOUND=""
    for candidate in "${FALLBACK_REGIONS[@]}"; do
      echo "   → probing $candidate ..."
      if region_ok "$candidate"; then
        FOUND="$candidate"
        break
      fi
    done
    if [ -n "$FOUND" ]; then
      LOCATION="$FOUND"
      echo "   → using '$FOUND'."
    else
      echo "❌ No fallback region currently offers $MYSQL_SKU for MySQL Flexible Server."
      echo ""
      echo "   The list-skus API is unreliable (transient errors / extension"
      echo "   breakage). Let the REAL deployment decide instead:"
      echo "     SKIP_REGION_CHECK=1 AZURE_LOCATION=centralindia ./provision.sh"
      echo ""
      echo "   Paid fallback (within the \$200 trial credit):"
      echo "     MYSQL_SKU=Standard_B2s SKIP_REGION_CHECK=1 AZURE_LOCATION=centralindia ./provision.sh"
      exit 1
    fi
  fi
  echo "   ✅ $LOCATION ok for $MYSQL_SKU"
else
  echo "▶ SKIP_REGION_CHECK=1 — attempting the real deployment in $LOCATION"
  echo "   with $MYSQL_SKU. Azure itself decides if the SKU fits."
fi

echo "▶ Checking resource group..."
az group show --name "$RESOURCE_GROUP" --output none \
  || { echo "❌ Resource group '$RESOURCE_GROUP' not found — create it first:"; \
       echo "   az group create -n $RESOURCE_GROUP -l $LOCATION"; exit 1; }

echo "▶ Deploying Bicep template (VNet, MySQL Flexible Server, Container Apps Environment + 9 apps) to $LOCATION..."
echo "   This takes 10-20 minutes. The container apps may crash-loop until"
echo "   MySQL is ready, then self-heal."
az deployment group create \
  --resource-group "$RESOURCE_GROUP" \
  --template-file "$SCRIPT_DIR/main.bicep" \
  --parameters \
      location="$LOCATION" \
      mysqlPassword="$MYSQL_PASSWORD" \
      mysqlAdminUser="$MYSQL_ADMIN_USER" \
      mysqlServerName="$MYSQL_SERVER_NAME" \
      mysqlSku="$MYSQL_SKU" \
      storageAccountName="$STORAGE_ACCOUNT_NAME" \
      ghcrOwner="$GHCR_OWNER" \
      imageTag="$IMAGE_TAG" \
  --name careq-azure-provision

echo ""
echo "▶ Waiting for the MySQL server to be Ready (can take a few minutes)..."
for _ in $(seq 1 60); do
  state="$(az mysql flexible-server show \
    --resource-group "$RESOURCE_GROUP" \
    --name "$MYSQL_SERVER_NAME" \
    --query state -o tsv 2>/dev/null || true)"
  [ "$state" = "Ready" ] && break
  sleep 10
done
[ "$state" = "Ready" ] || { echo "❌ MySQL server not Ready after 10 min — check the portal."; exit 1; }
echo "   ✅ server Ready"

echo "▶ Creating the careq_db database..."
az mysql flexible-server db create \
  --resource-group "$RESOURCE_GROUP" \
  --server-name "$MYSQL_SERVER_NAME" \
  --database-name careq_db \
  --output none

echo "▶ Allowing non-SSL connections (demo convenience — the app's JDBC URL"
echo "   uses useSSL=false; production should keep require_secure_transport=ON)..."
az mysql flexible-server parameter set \
  --resource-group "$RESOURCE_GROUP" \
  --server-name "$MYSQL_SERVER_NAME" \
  --name require_secure_transport \
  --value OFF \
  --output none

echo ""
echo "▶ Deployment outputs:"
GATEWAY_FQDN=$(az deployment group show \
  --resource-group "$RESOURCE_GROUP" \
  --name careq-azure-provision \
  --query "properties.outputs.gatewayFqdn.value" \
  --output tsv)
MYSQL_FQDN=$(az deployment group show \
  --resource-group "$RESOURCE_GROUP" \
  --name careq-azure-provision \
  --query "properties.outputs.mysqlFqdn.value" \
  --output tsv)

echo ""
echo "  ┌──────────────────────────────────────────────────────────────┐"
echo "  │  LIVE URLs (after the first CD deploy run)                  │"
echo "  │    API Gateway : https://$GATEWAY_FQDN"
echo "  │    Frontend    : https://careq-frontend.vercel.app  (Vercel)"
echo "  │    MySQL       : $MYSQL_FQDN (private, VNet only)"
echo "  └──────────────────────────────────────────────────────────────┘"
echo ""

# GHCR image pull auth (optional): if the ghcr.io/careq-* packages are
# private, pass --parameters ghcrUsername='<your-gh-username>' below so the
# apps get registry credentials, and add GHCR_PAT (fine-grained PAT,
# packages:read) to GitHub secrets. With public packages, leave ghcrUsername
# unset (default) and omit GHCR_PAT.

# To use private GHCR packages, add to the deployment command above:
#   ghcrUsername='<github-username>'

echo "▶ Fetching the Blob Storage connection string (profile pictures)..."
    STORAGE_CONNECTION_STRING=$(az storage account show-connection-string \
      --name "$STORAGE_ACCOUNT_NAME" \
      --resource-group "$RESOURCE_GROUP" \
      --query connectionString -o tsv)

    echo "============================================================"
    echo "  GITHUB SECRETS — add ALL of these to the repo before the"
    echo "  deploy workflow will succeed (Settings → Secrets → Actions):"
    echo "============================================================"
    echo "  Already present (OIDC):"
    echo "    AZURE_CLIENT_ID         (from App Registration)"
    echo "    AZURE_TENANT_ID"
    echo "    AZURE_SUBSCRIPTION_ID"
    echo ""
    echo "  New — add these now:"
    echo "    JWT_SECRET                      = $JWT_SECRET"
    echo "    MYSQL_PASSWORD                  = $MYSQL_PASSWORD"
    echo "    RABBITMQ_USERNAME               = $RABBITMQ_USERNAME"
    echo "    RABBITMQ_PASSWORD               = $RABBITMQ_PASSWORD"
    echo "    AZURE_STORAGE_CONNECTION_STRING = $STORAGE_CONNECTION_STRING"
    echo "    GROQ_API_KEY                    = <your groq key — optional, triage falls back to NORMAL without it>"
    echo "    VAPID_PUBLIC_KEY                = <optional — Web Push; run scripts/generate-vapid-keys.sh>"
    echo "    VAPID_PRIVATE_KEY               = <optional — Web Push; same script>"
    echo "    GMAIL_USERNAME                  = <optional but both-or-neither with email verification; the Gmail address that sends mail, e.g. rap53748@gmail.com>"
    echo "    GMAIL_APP_PASSWORD              = <optional; Google → Security → 2-Step Verification → App passwords (16 chars, no spaces)>"
    echo "    GHCR_PAT                        = <optional — fine-grained PAT, packages:read, ONLY if your ghcr packages are private>"
echo ""
    echo "  Vercel (create ONCE at https://vercel.com):"
    echo "    VERCEL_TOKEN                    = Account Settings → Tokens → Create"
    echo "    VERCEL_ORG_ID                   = orgId in frontend/.vercel/project.json after \`npx vercel link\`"
    echo "    VERCEL_PROJECT_ID               = projectId in the same file"
echo ""
echo "  Keep a copy of these values somewhere safe — they are needed"
echo "  for every future deploy (the workflow reads them from GitHub,"
echo "  never from this repo)."
echo ""
echo "▶ Next steps:"
echo "  1. Add the GitHub secrets above."
    echo "  2. Create the Vercel project (free Hobby) for the frontend:"
    echo "       cd frontend"
    echo "       npx vercel link           # create project 'careq-frontend'"
    echo "       npx vercel env add VITE_API_BASE_URL production"
    echo "         # value: https://$GATEWAY_FQDN"
    echo "       npx vercel env add VITE_VAPID_PUBLIC_KEY production"
    echo "         # value: the public key from scripts/generate-vapid-keys.sh (optional — Web Push)"
    echo "       # copy orgId + projectId from frontend/.vercel/project.json"
    echo "       #   → GitHub secrets VERCEL_ORG_ID and VERCEL_PROJECT_ID"
echo "  3. Push/merge this branch to develop — the deploy job rolls out"
echo "     the images and the deploy-frontend job ships the SPA to Vercel."
echo "  4. Seed + smoke the live deployment:"
echo "       API_BASE=\"https://$GATEWAY_FQDN\" bash scripts/seed-data.sh"
echo "       API_BASE=\"https://$GATEWAY_FQDN\" FRONTEND_BASE=\"https://careq-frontend.vercel.app\" \\"
echo "         node scripts/azure-smoke.mjs"
echo "  5. Watch cost: Azure Cost Management (budget alert recommended)."
echo "     Teardown when done:  az group delete -n $RESOURCE_GROUP --yes --no-wait"

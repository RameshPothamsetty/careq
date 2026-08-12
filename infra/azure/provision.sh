#!/usr/bin/env bash
# =====================================================================
# CareQ — Day 15 ONE-TIME Azure provisioning (run yourself, in Cloud Shell)
#
#   Run this ONCE. Everything after it is recurring and automated by the
#   GitHub Actions deploy job on every push to develop/main.
#
# Prerequisites:
#   1. az CLI logged in:            az login
#   2. Subscription set:            az account set --subscription "<name or id>"
#   3. Resource group exists:       az group create -n careq-rg -l <region>
#   4. Env vars below (or edit defaults)
#
# Usage:
#   cd infra/azure
#   MYSQL_PASSWORD='<long random>' VM_ADMIN_PASSWORD='<another>' ./provision.sh
#
# The script:
#   • deploys infra/azure/main.bicep into careq-rg
#   • auto-switches to a working region if Standard_B1s isn't available in
#     the requested one (see the region check below)
#   • prints the live Gateway URL, Static Web App URL, MySQL private IP
#   • prints the Static Web App deployment token to save as a GitHub secret
#   • prints the FULL list of GitHub secrets the deploy workflow needs
#
# Never commit this script's outputs or your passwords to git.
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Configuration ─────────────────────────────────────────────────────
RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-careq-rg}"
LOCATION="${AZURE_LOCATION:-eastus2}"
# Static Web Apps host the frontend; they land in a KNOWN-GOOD SWA region
# regardless of where the backend/VM end up (B1s and SWA Free aren't always
# available in the same regions). Override with SWA_LOCATION if needed.
SWA_LOCATION="${SWA_LOCATION:-eastus2}"
# MySQL VM size. Standard_B1s is the 12-months-free size but is currently
# capacity-restricted in most mainstream regions; if no region works, switch
# to the paid Standard_B2s (≈$25-30/mo, within the trial credit).
VM_SIZE="${VM_SIZE:-Standard_B1s}"
GHCR_OWNER="${GHCR_OWNER:-rameshpothamsetty}"
IMAGE_TAG="${IMAGE_TAG:-develop-latest}"

# Required secrets — generate them ONCE here, reuse the SAME values later
# when you add them to GitHub secrets.
MYSQL_PASSWORD="${MYSQL_PASSWORD:?Set me, e.g.  openssl rand -base64 24}"
VM_ADMIN_PASSWORD="${VM_ADMIN_PASSWORD:?Set me, e.g.  openssl rand -base64 24}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"
RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-careq_bus}"
RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-$(openssl rand -base64 24)}"

echo "============================================================"
echo "  CareQ — Azure provisioning (one-time)"
echo "  Resource group : $RESOURCE_GROUP (requested $LOCATION)"
echo "  Static Web App : $SWA_LOCATION (fixed, SWA-capable)"
echo "  MySQL VM size  : $VM_SIZE"
echo "  Images         : ghcr.io/$GHCR_OWNER/careq-*:$IMAGE_TAG"
echo "============================================================"
echo ""

echo "▶ Ensuring Azure CLI + extensions..."
az extension add -n containerapp --yes 2>/dev/null || true
az extension add -n staticwebapp --yes 2>/dev/null || true
az config set extension.use_dynamic_install=yes_without_prompt

# ── Region check ──────────────────────────────────────────────────────
# A region must satisfy TWO constraints on this subscription:
#   1. the VM size must be provisionable (Standard_B1s is capacity-restricted
#      in many regions right now → "SkuNotAvailable"), AND
#   2. the region must be ENABLED for the resource types we use here
#      (→ "LocationNotAvailableForResourceType", the error that lists the
#      exact available regions).
# If the requested region fails either check, auto-fall back to the first
# candidate that passes both. The Static Web App is decoupled (swaLocation,
# default eastus2) so the frontend region is unaffected.
FALLBACK_REGIONS=(eastus3 southeastus southwestus denmarkeast indiasouthcentral israelnorthwest saudiArabiaeast southcentralus2 northeastus5 southeastasia3 southeastus3 southeastus5 centralus westus2 westeurope)

# Authoritative per-subscription region lists — the same data Azure uses for
# the LocationNotAvailableForResourceType error. Empty result = provider not
# registered yet; treat as "can't verify, assume ok" (Azure auto-registers
# providers at deploy time).
provider_regions() {
  # az -o tsv can join the region array with TABS or commas depending on
  # CLI version — split on BOTH so the list is always one region per line.
  az provider show -n "$1" \
    --query "resourceTypes[?resourceType=='$2'].locations | [0]" -o tsv 2>/dev/null \
    | tr ',\t' '\n\n' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' | tr -d '\r' | grep -v '^$' || true
}

# Register the providers this stack needs (free) so the region lists above are
# authoritative instead of fail-open. Network/Compute are pre-registered on any
# account; Container Apps / Log Analytics / Web often are not.
echo "▶ Registering Azure resource providers (free, no charge)..."
for ns in Microsoft.App Microsoft.OperationalInsights Microsoft.Web; do
  state="$(az provider show -n "$ns" --query registrationState -o tsv 2>/dev/null || true)"
  if [ "$state" != "Registered" ]; then
    az provider register -n "$ns" --accept-terms >/dev/null 2>&1 || true
    for _ in $(seq 1 15); do
      [ "$(az provider show -n "$ns" --query registrationState -o tsv 2>/dev/null || true)" = "Registered" ] && break
      sleep 2
    done
  fi
done
VNET_REGIONS="$(provider_regions Microsoft.Network virtualNetworks)"
VM_REGIONS="$(provider_regions Microsoft.Compute virtualMachines)"
CAE_REGIONS="$(provider_regions Microsoft.App managedEnvironments)"
APP_REGIONS="$(provider_regions Microsoft.App containerApps)"
OPS_REGIONS="$(provider_regions Microsoft.OperationalInsights workspaces)"
SWA_REGIONS="$(provider_regions Microsoft.Web staticSites)"

in_list() { echo "$2" | grep -qix "$1"; }

vm_size_available() {
  [ "$(az vm list-skus --location "$1" --size "$2" \
        --query "[?name=='$2'] | length(@)" -o tsv 2>/dev/null)" = "1" ]
}

region_ok() {
  vm_size_available "$1" "$VM_SIZE" \
    && { [ -z "$VNET_REGIONS" ] || in_list "$1" "$VNET_REGIONS"; } \
    && { [ -z "$VM_REGIONS" ] || in_list "$1" "$VM_REGIONS"; } \
    && { [ -z "$CAE_REGIONS" ] || in_list "$1" "$CAE_REGIONS"; } \
    && { [ -z "$APP_REGIONS" ] || in_list "$1" "$APP_REGIONS"; } \
    && { [ -z "$OPS_REGIONS" ] || in_list "$1" "$OPS_REGIONS"; }
}

echo "▶ Checking $VM_SIZE + region support in $LOCATION..."
if ! region_ok "$LOCATION"; then
  echo "❌ $LOCATION can't run this stack (VM size unavailable or region not enabled)."
  echo "   Trying fallback regions (frontend SWA stays in $SWA_LOCATION regardless)..."
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
  elif [ "$VM_SIZE" = "Standard_B1s" ]; then
    echo "❌ No region currently has Standard_B1s AND is enabled for this stack."
    echo ""
    echo "   HONEST COST CHECK — the free path is exhausted. The only remaining"
    echo "   option for a live, always-on demo is the PAID Standard_B2s VM:"
    echo "     AZURE_LOCATION=centralus VM_SIZE=Standard_B2s ./provision.sh"
    echo ""
    echo "   Standard_B2s adds ≈ \$28-30/mo for the MySQL VM. Everything else is"
    echo "   already minimized: frontend = free (Static Web Apps), redis/rabbitmq"
    echo "   and 4 services scale to zero (bill only when used), 3 services stay"
    echo "   warm. Realistic all-in: ≈ \$100-115/mo. Your \$200 credit covers that"
    echo "   for the 30-day trial window (the credit EXPIRES after 30 days — after"
    echo "   that it's pay-as-you-go on the card on file)."
    echo ""
    echo "   Guardrails before you spend anything:"
    echo "     1. Set a budget alert:  portal → Cost Management → Budgets (alert at 50%/90%)"
    echo "     2. After your interviews, teardown everything:"
    echo "          az group delete -n $RESOURCE_GROUP --yes --no-wait"
    echo ""
    echo "   If you'd rather not spend, wait for B1s capacity to return and re-run"
    echo "   this same command later (it auto-retries all regions)."
    exit 1
  else
    echo "❌ No fallback region works with VM_SIZE=$VM_SIZE."
    echo "   Try a different AZURE_LOCATION / VM_SIZE and re-run."
    exit 1
  fi
fi
echo "   ✅ $LOCATION ok for $VM_SIZE"

# The SWA region itself must support Static Web Apps on this subscription.
if [ -n "$SWA_REGIONS" ] && ! in_list "$SWA_LOCATION" "$SWA_REGIONS"; then
  echo "❌ $SWA_LOCATION does not support Static Web Apps on this subscription."
  echo "   Available SWA regions: $(echo "$SWA_REGIONS" | tr '\n' ' ')"
  echo "   Re-run with SWA_LOCATION=<one of those> ./provision.sh"
  exit 1
fi

# NOTE on outbound internet for the VM: the MySQL VM has no public IP, so it
# relies on Azure's default outbound access to reach apt during cloud-init.
# If that's unavailable on this subscription (Azure has been retiring default
# outbound for new VMs), apt fails and MySQL never installs — in that case add
# a NAT gateway to vms-subnet, or temporarily attach a public IP + tighten the
# NSG, then re-run. Check: az vm run-command invoke -n careq-mysql-vm -g careq-rg --command-id RunShellScript --scripts "mysql --version"

echo "▶ Checking resource group..."
az group show --name "$RESOURCE_GROUP" --output none \
  || { echo "❌ Resource group '$RESOURCE_GROUP' not found — create it first:"; \
       echo "   az group create -n $RESOURCE_GROUP -l $LOCATION"; exit 1; }

echo "▶ Deploying Bicep template (VNet, Container Apps Environment + 9 apps, MySQL VM, Static Web App) to $LOCATION..."
echo "   This takes 10-20 minutes. The MySQL VM installs MySQL via cloud-init"
echo "   after boot; the container apps may crash-loop until it is ready,"
echo "   then self-heal."
az deployment group create \
  --resource-group "$RESOURCE_GROUP" \
  --template-file "$SCRIPT_DIR/main.bicep" \
  --parameters \
      location="$LOCATION" \
      swaLocation="$SWA_LOCATION" \
      vmSize="$VM_SIZE" \
      mysqlPassword="$MYSQL_PASSWORD" \
      mysqlVmAdminPassword="$VM_ADMIN_PASSWORD" \
      ghcrOwner="$GHCR_OWNER" \
      imageTag="$IMAGE_TAG" \
  --name careq-day15-provision

echo ""
echo "▶ Deployment outputs:"
OUTPUTS=$(az deployment group show \
  --resource-group "$RESOURCE_GROUP" \
  --name careq-day15-provision \
  --query properties.outputs \
  --output json)

GATEWAY_FQDN=$(echo "$OUTPUTS" | python3 -c "import sys,json;print(json.load(sys.stdin)['gatewayFqdn']['value'])")
SWA_HOST=$(echo "$OUTPUTS" | python3 -c "import sys,json;print(json.load(sys.stdin)['swaDefaultHostname']['value'])")
MYSQL_IP=$(echo "$OUTPUTS" | python3 -c "import sys,json;print(json.load(sys.stdin)['mysqlPrivateIp']['value'])")

echo ""
echo "  ┌─────────────────────────────────────────────────────────┐"
echo "  │  LIVE URLs (after the first CD deploy run)             │"
echo "  │    API Gateway : https://$GATEWAY_FQDN"
echo "  │    Frontend    : https://$SWA_HOST   (region: $SWA_LOCATION)"
echo "  │    MySQL       : $MYSQL_IP (private, VNet only)"
echo "  └─────────────────────────────────────────────────────────┘"
echo ""

# GHCR image pull auth (optional): if the ghcr.io/careq-* packages are
# private, pass --parameters ghcrUsername='<your-gh-username>' below so the
# apps get registry credentials, and add GHCR_PAT (fine-grained PAT,
# packages:read) to GitHub secrets. With public packages, leave ghcrUsername
# unset (default) and omit GHCR_PAT.

# To use private GHCR packages, add to the deployment command above:
#   ghcrUsername='<github-username>'

echo "▶ Fetching the Static Web App deployment token..."
SWA_TOKEN=$(az staticwebapp secrets list \
  --name careq-frontend \
  --resource-group "$RESOURCE_GROUP" \
  --query properties.apiKey \
  --output tsv)
echo "   → Add to GitHub secrets as:  AZURE_STATIC_WEB_APPS_API_TOKEN"
echo ""
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
echo "    AZURE_STATIC_WEB_APPS_API_TOKEN = $SWA_TOKEN"
echo "    JWT_SECRET                      = $JWT_SECRET"
echo "    MYSQL_PASSWORD                  = $MYSQL_PASSWORD"
echo "    RABBITMQ_USERNAME               = $RABBITMQ_USERNAME"
echo "    RABBITMQ_PASSWORD               = $RABBITMQ_PASSWORD"
echo "    GROQ_API_KEY                    = <your groq key — optional, triage falls back to NORMAL without it>"
echo "    GHCR_PAT                        = <optional — fine-grained PAT, packages:read, ONLY if your ghcr packages are private>"
echo ""
echo "  Keep a copy of these values somewhere safe — they are needed"
echo "  for every future deploy (the workflow reads them from GitHub,"
echo "  never from this repo)."
echo ""
echo "▶ Next steps:"
echo "  1. Add the GitHub secrets above."
echo "  2. Push/merge this branch to develop — the deploy job will roll"
echo "     out the images and deploy the frontend to the Static Web App."
echo "  3. Smoke test:"
echo "       API_BASE=\"https://$GATEWAY_FQDN\" bash scripts/seed-data.sh"
echo "       API_BASE=\"https://$GATEWAY_FQDN\" FRONTEND_BASE=\"https://$SWA_HOST\" \\"
echo "         node scripts/day15-azure-smoke.mjs"
echo "  4. Watch cost: Azure Cost Management (budget alert recommended)."
echo "     Teardown when done:  az group delete -n $RESOURCE_GROUP --yes --no-wait"

#!/bin/bash
# ================================================================
# Day 17 — Enable Log Analytics diagnostics for every Container App.
#
# Container Apps already send app logs to the `careq-logs` Log Analytics
# workspace (the CAE is wired to it), but the per-app "Diagnostic settings"
# that route console logs + request traces there are turned on per app in
# the portal. This script enables them for all 9 apps in one shot so the
# ACCESS / AUDIT log lines (Day 17) are queryable in Log Analytics.
#
# Usage: bash scripts/enable-diagnostics.sh
# Prereq: az login + subscription with the careq resources.
# ================================================================
set -euo pipefail

RESOURCE_GROUP="${RESOURCE_GROUP:-careq-rg-south}"
WORKSPACE="careq-logs"
APPS=(eureka-server api-gateway auth-service user-service doctor-service queue-service notification-service redis rabbitmq)

echo "── Enabling diagnostic settings → ${WORKSPACE} ──"
for app in "${APPS[@]}"; do
  name="careq-$app"
  echo "  $name ..."
  az monitor diagnostic-settings create \
    --name "careq-logs-to-la" \
    --resource "$name" \
    --resource-group "$RESOURCE_GROUP" \
    --workspace "$WORKSPACE" \
    --logs '[{"category":"ContainerAppConsoleLogs","enabled":true}]' \
    --metrics '[{"category":"AllMetrics","enabled":true}]' \
    --output none
done
echo "✅ Diagnostics enabled. Query in the portal:"
echo "   Log Analytics → careq-logs → Logs → search ContainerAppConsoleLogs"
echo "   (sample queries in docs/12_MONITORING.md)"

#!/usr/bin/env bash
#
# CareQ — Day 16: generate a Web Push (VAPID) keypair.
#
# Prints ready-to-paste env lines. The PUBLIC key is needed in TWO places:
#   * backend  → VAPID_PUBLIC_KEY (notification-service, Azure/GitHub secrets)
#   * frontend → VITE_VAPID_PUBLIC_KEY (baked into the bundle; Vercel project
#                env or the docker-compose frontend build arg)
# The PRIVATE key only ever goes to the backend (never to the frontend).
#
set -euo pipefail

if ! command -v npx > /dev/null 2>&1; then
  echo "❌ npx is required (Node.js) — install it first." >&2
  exit 1
fi

echo "▶ Generating VAPID keypair via the web-push npm CLI..." >&2
KEYS=$(npx -y web-push generate-vapid-keys --json)

PUB=$(printf '%s' "$KEYS" | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>console.log(JSON.parse(d).publicKey))")
PRIV=$(printf '%s' "$KEYS" | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>console.log(JSON.parse(d).privateKey))")

echo ""
echo "# ── Web Push (VAPID) — Day 16 ──────────────────────────────────"
echo "# Backend (root .env, and the GitHub secrets VAPID_PUBLIC_KEY /"
echo "# VAPID_PRIVATE_KEY for the Azure deploy):"
echo "VAPID_PUBLIC_KEY=$PUB"
echo "VAPID_PRIVATE_KEY=$PRIV"
echo "VAPID_SUBJECT=mailto:careq@example.com"
echo ""
echo "# Frontend (Vercel project env 'production', or the docker-compose"
echo "# frontend build arg):"
echo "VITE_VAPID_PUBLIC_KEY=$PUB"
echo ""
echo "⚠  The private key is shown above once — store it somewhere safe."
echo "   Anyone with it can impersonate CareQ to your users' push services."

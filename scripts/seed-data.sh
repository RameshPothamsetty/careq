#!/bin/bash
# ================================================================
# CareQ Seed Script — Creates doctors, admin, patient & catalog entries
# Run this AFTER all microservices are running (eureka, auth, user, doctor)
# Usage: bash scripts/seed-data.sh
#
# BUG-2 fix (Day 10 follow-up): this script is now idempotent across DB
# resets. Re-running it (or running it after an auth-DB reset) will:
#   1. reuse existing accounts (signup fails -> login to recover the userId),
#   2. skip catalog entries that already exist for a seed doctor's userId,
#   3. delete ORPHANED catalog entries whose userId no longer matches any
#      seed doctor (leftovers from a previous auth DB).
# ================================================================

# Day 15: overridable so the same script seeds the LIVE Azure deployment:
#   API_BASE="https://<gateway-fqdn>" bash scripts/seed-data.sh
API_BASE="${API_BASE:-http://localhost:8080}"

echo "=========================================="
echo "  CareQ — Seed Data Script"
echo "=========================================="
echo ""

# ─── Helper: signup a user and extract token ───
# Idempotent: if the account already exists, falls back to login so the
# caller still gets a response containing the (current) userId.
signup_user() {
  local name="$1" email="$2" password="$3" role="$4"
  # Diagnostics go to stderr so stdout carries ONLY the JSON response —
  # callers capture it and parse the userId from it.
  echo "  Creating $role: $name ($email)..." >&2

  RESPONSE=$(curl -s -X POST "$API_BASE/api/auth/signup" \
    -H "Content-Type: application/json" \
    -d "{\"fullName\":\"$name\",\"email\":\"$email\",\"password\":\"$password\",\"role\":\"$role\"}")

  # Day 17 — with email verification enabled, a SUCCESSFUL signup returns
  # { message, verificationRequired: true } and NO token (the account can't
  # log in until the emailed link is clicked). Detect that case explicitly so
  # we don't mistake success for an error.
  if echo "$RESPONSE" | grep -q '"verificationRequired":true'; then
    echo "     ⚠️  $email created but PENDING EMAIL VERIFICATION — seed accounts" >&2
    echo "        must exist BEFORE verification is enabled, or be verified" >&2
    echo "        via the emailed link. Proceeding without a token." >&2
    echo "$RESPONSE"
    return 0
  fi

  # Check for error (409 duplicate, 400 validation, 429 rate limit)
  if echo "$RESPONSE" | grep -q '"error"'; then
    echo "  ⚠️  $email may already exist — logging in to reuse the account" >&2
    LOGIN_RESP=$(login_user "$email" "$password")
    if echo "$LOGIN_RESP" | grep -q "token"; then
      echo "     ✅ Logged in as existing $role" >&2
      echo "$LOGIN_RESP"
      return 0
    fi
    echo "     ❌ Login failed: $LOGIN_RESP" >&2
    return 1
  fi

  echo "$RESPONSE"
  return 0
}

# ─── Helper: login and get token ───
login_user() {
  local email="$1" password="$2"
  curl -s -X POST "$API_BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}"
}

# ─── Helper: extract a field from JSON ───
get_json_value() {
  local json="$1" key="$2"
  echo "$json" | grep -o "\"$key\":\"[^\"]*\"" | head -1 | sed "s/\"$key\":\"//" | sed "s/\"//"
}

# ─── Step 1: Sign up Doctor users ───
echo "📋 Step 1/4: Creating DOCTOR accounts..."
echo "----------------------------------------"

declare -a DOCTOR_RESPONSES

# Doctor 1
RESP=$(signup_user "Arjun Sharma" "dr.arjun@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 2
RESP=$(signup_user "Priya Patel" "dr.priya@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 3
RESP=$(signup_user "Vikram Reddy" "dr.vikram@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 4
RESP=$(signup_user "Ananya Singh" "dr.ananya@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 5
RESP=$(signup_user "Rajesh Kumar" "dr.rajesh@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 6
RESP=$(signup_user "Meera Iyer" "dr.meera@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 7
RESP=$(signup_user "Suresh Nair" "dr.suresh@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 8
RESP=$(signup_user "Deepa Menon" "dr.deepam@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 9
RESP=$(signup_user "Karthik Joshi" "dr.karthik@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

# Doctor 10
RESP=$(signup_user "Lakshmi Rao" "dr.lakshmi@careq.com" "password123" "DOCTOR")
[ $? -eq 0 ] && DOCTOR_RESPONSES+=("$RESP")

echo "✅ ${#DOCTOR_RESPONSES[@]} doctor accounts ready (created or reused)"
echo ""

# ─── Step 2: Sign up Admin + Patient ───
echo "📋 Step 2/4: Creating ADMIN and PATIENT accounts..."
echo "--------------------------------------------------"

ADMIN_RESP=$(signup_user "Admin CareQ" "admin@careq.com" "admin123" "ADMIN")
PATIENT_RESP=$(signup_user "John Patient" "john@careq.com" "password123" "PATIENT")
# Day 17 — dedicated smoke-test patient (used by scripts/day15-azure-smoke.mjs).
SMOKE_PATIENT_RESP=$(signup_user "Smoke Patient" "patient.smoke@careq.com" "password123" "PATIENT")

echo "✅ Admin ready: admin@careq.com / admin123"
echo "✅ Patient ready: john@careq.com / password123"
echo "✅ Smoke patient ready: patient.smoke@careq.com / password123"
echo ""

# ─── Step 3: Get departments (to map department IDs) ───
echo "📋 Step 3/4: Fetching departments..."
echo "--------------------------------------"

# Login as admin to get a token
ADMIN_LOGIN=$(login_user "admin@careq.com" "admin123")
ADMIN_TOKEN=$(get_json_value "$ADMIN_LOGIN" "token")

if [ -z "$ADMIN_TOKEN" ]; then
  echo "❌ Could not get admin token. Make sure auth-service is running."
  exit 1
fi

# Fetch departments
DEPT_RESPONSE=$(curl -s "$API_BASE/api/departments" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

echo "   Departments loaded (doctor-service DataSeeder heals any missing ones)"
echo ""

# ─── Step 4: Reconcile doctor catalog entries (idempotent) ───
echo "📋 Step 4/4: Reconciling doctor catalog entries..."
echo "-------------------------------------------------"

# Doctor catalog data: name, departmentId, specialization, qualification, experience, fee, avgTime
# Department IDs match the seeded order (DataSeeder heals any missing on restart):
#   1=Cardiology, 2=Neurology, 3=Orthopedics, 4=Pediatrics, 5=Dermatology,
#   6=Ophthalmology, 7=ENT, 8=Gastroenterology, 9=Pulmonology, 10=Nephrology

CATALOG_DATA=(
  "Dr. Arjun Sharma:1:Interventional Cardiology:MD, DM Cardiology:15:800:20"
  "Dr. Priya Patel:1:Pediatric Cardiology:MD, DM Pediatrics Cardiology:10:600:15"
  "Dr. Vikram Reddy:2:Stroke Neurology:MD, DM Neurology:20:1000:25"
  "Dr. Ananya Singh:3:Joint Replacement Surgery:MS Orthopedics:12:750:20"
  "Dr. Rajesh Kumar:4:Neonatology:MD Pediatrics:8:500:15"
  "Dr. Meera Iyer:5:Cosmetic Dermatology:MD Dermatology:6:600:15"
  "Dr. Suresh Nair:6:Cataract Surgery:MS Ophthalmology:18:700:20"
  "Dr. Deepa Menon:7:Head & Neck Surgery:MS ENT:14:650:20"
  "Dr. Karthik Joshi:8:Hepatology:MD, DM Gastroenterology:11:850:25"
  "Dr. Lakshmi Rao:9:Sleep Medicine:MD Pulmonology:9:550:15"
)

# Fetch the current catalog so we can skip entries that already exist for a
# seed userId and delete orphans left over from a previous auth DB (BUG-2).
CATALOG_RESP=$(curl -s "$API_BASE/api/doctors?page=0&size=100" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

# Each catalog entry object carries "id" then "userId"; extract them in
# document order so the two arrays align by index.
EXISTING_IDS=($(echo "$CATALOG_RESP" | grep -o '"id":[0-9]*' | sed 's/"id"://'))
EXISTING_USER_IDS=($(echo "$CATALOG_RESP" | grep -o '"userId":"[^"]*"' | sed 's/"userId":"//;s/"$//'))

COUNT=0
SKIPPED=0
declare -a SEED_USER_IDS

for i in "${!DOCTOR_RESPONSES[@]}"; do
  RESP="${DOCTOR_RESPONSES[$i]}"
  USER_ID=$(get_json_value "$RESP" "userId")

  if [ -z "$USER_ID" ]; then
    echo "  ⚠️  Could not extract userId from response #$((i+1))"
    continue
  fi
  SEED_USER_IDS+=("$USER_ID")

  # Get catalog data for this doctor
  IFS=':' read -r NAME DEPT_ID SPECIALIZATION QUALIFICATION EXP FEE AVG_TIME <<< "${CATALOG_DATA[$i]}"

  # Idempotency: this doctor already has a catalog entry — leave it alone.
  if [[ " ${EXISTING_USER_IDS[*]} " == *" $USER_ID "* ]]; then
    echo "  ⏭️  $NAME already cataloged — skipping (idempotent)"
    SKIPPED=$((SKIPPED + 1))
    continue
  fi

  echo "  Creating: $NAME — $SPECIALIZATION (Dept #$DEPT_ID, ${EXP}yrs, ₹$FEE)..."

  CATALOG_RESP=$(curl -s -X POST "$API_BASE/api/doctors" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{
      \"name\": \"$NAME\",
      \"userId\": \"$USER_ID\",
      \"departmentId\": $DEPT_ID,
      \"specialization\": \"$SPECIALIZATION\",
      \"qualification\": \"$QUALIFICATION\",
      \"experienceYears\": $EXP,
      \"consultationFee\": $FEE,
      \"avgConsultationTimeMinutes\": $AVG_TIME
    }")

  if echo "$CATALOG_RESP" | grep -q "message\|error"; then
    echo "     ❌ Failed: $CATALOG_RESP"
  else
    COUNT=$((COUNT + 1))
  fi
done

# Orphan cleanup (BUG-2 fix): delete catalog entries whose userId is not one
# of the current seed doctors — e.g. leftovers from a previous auth DB that
# no longer has those accounts.
ORPHAN_COUNT=0
for idx in "${!EXISTING_USER_IDS[@]}"; do
  uid="${EXISTING_USER_IDS[$idx]}"
  if [[ ! " ${SEED_USER_IDS[*]} " == *" $uid "* ]]; then
    oid="${EXISTING_IDS[$idx]}"
    echo "  🧹 Removing orphaned catalog entry #$oid (userId $uid no longer exists)"
    curl -s -X DELETE "$API_BASE/api/doctors/$oid" \
      -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null
    ORPHAN_COUNT=$((ORPHAN_COUNT + 1))
  fi
done

echo ""
echo "=========================================="
echo "  ✅ Seeding Complete!"
echo "=========================================="
echo ""
echo "  Doctors created:      $COUNT"
echo "  Already cataloged:    $SKIPPED"
echo "  Orphans removed:      $ORPHAN_COUNT"
echo "  Departments:          10 (healed by doctor-service DataSeeder)"
echo ""
echo "  ── Login Credentials ──"
echo "  Admin:   admin@careq.com / admin123"
echo "  Patient: john@careq.com  / password123"
echo ""
echo "  Doctor accounts (login to check availability toggle):"
echo "  dr.arjun@careq.com   (Interventional Cardiology)"
echo "  dr.priya@careq.com   (Pediatric Cardiology)"
echo "  dr.vikram@careq.com  (Stroke Neurology)"
echo "  dr.ananya@careq.com  (Joint Replacement)"
echo "  dr.rajesh@careq.com  (Neonatology)"
echo "  dr.meera@careq.com   (Cosmetic Dermatology)"
echo "  dr.suresh@careq.com  (Cataract Surgery)"
echo "  dr.deepam@careq.com  (Head & Neck Surgery)"
echo "  dr.karthik@careq.com (Hepatology)"
echo "  dr.lakshmi@careq.com (Sleep Medicine)"
echo ""
echo "  All doctor passwords: password123"
echo "=========================================="

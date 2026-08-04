#!/bin/bash
# ================================================================
# CareQ Seed Script — Creates doctors, admin, patient & catalog entries
# Run this AFTER all microservices are running (eureka, auth, user, doctor)
# Usage: bash scripts/seed-data.sh
# ================================================================

API_BASE="http://localhost:8080"

echo "=========================================="
echo "  CareQ — Seed Data Script"
echo "=========================================="
echo ""

# ─── Helper: signup a user and extract token ───
signup_user() {
  local name="$1" email="$2" password="$3" role="$4"
  echo "  Creating $role: $name ($email)..."

  RESPONSE=$(curl -s -X POST "$API_BASE/api/auth/signup" \
    -H "Content-Type: application/json" \
    -d "{\"fullName\":\"$name\",\"email\":\"$email\",\"password\":\"$password\",\"role\":\"$role\"}")

  # Check for error
  if echo "$RESPONSE" | grep -q "message\|error"; then
    echo "  ⚠️  Could not create $email — may already exist"
    echo "     $RESPONSE"
    echo ""
    return 1
  fi

  echo "$RESPONSE"
  echo ""
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

echo "✅ Created ${#DOCTOR_RESPONSES[@]} doctor accounts"
echo ""

# ─── Step 2: Sign up Admin + Patient ───
echo "📋 Step 2/4: Creating ADMIN and PATIENT accounts..."
echo "--------------------------------------------------"

ADMIN_RESP=$(signup_user "Admin CareQ" "admin@careq.com" "admin123" "ADMIN")
PATIENT_RESP=$(signup_user "John Patient" "john@careq.com" "password123" "PATIENT")

echo "✅ Admin created: admin@careq.com / admin123"
echo "✅ Patient created: john@careq.com / password123"
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

echo "   Departments loaded"
echo ""

# ─── Step 4: Create doctor catalog entries ───
echo "📋 Step 4/4: Creating doctor catalog entries..."
echo "-------------------------------------------------"

# Doctor catalog data: name, departmentId, specialization, qualification, experience, fee, avgTime
# Department IDs match the seeded order:
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

COUNT=0
for i in "${!DOCTOR_RESPONSES[@]}"; do
  RESP="${DOCTOR_RESPONSES[$i]}"
  USER_ID=$(get_json_value "$RESP" "userId")

  if [ -z "$USER_ID" ]; then
    echo "  ⚠️  Could not extract userId from response #$((i+1))"
    continue
  fi

  # Get catalog data for this doctor
  IFS=':' read -r NAME DEPT_ID SPECIALIZATION QUALIFICATION EXP FEE AVG_TIME <<< "${CATALOG_DATA[$i]}"

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

echo ""
echo "=========================================="
echo "  ✅ Seeding Complete!"
echo "=========================================="
echo ""
echo "  Doctors created:  $COUNT"
echo "  Departments:      10 (auto-seeded by doctor-service)"
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

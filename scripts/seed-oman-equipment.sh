#!/usr/bin/env bash
#
# Seed the Oman road-project equipment catalogue + per-resource-type productivity norms.
# Mirrors the "Plant utilization" sheet of `2. Capacity_Utilization.xlsx`.
#
# Usage:
#   ./scripts/seed-oman-equipment.sh
#
# Requires: bash 3+, curl, jq. Backend running on :8080 with admin/admin123.
# Idempotent: skips entries that already exist by code/name.

set -euo pipefail

API="${API:-http://localhost:8080}"
USER="${USER_NAME:-admin}"
PASS="${PASSWORD:-admin123}"

echo "→ logging in as $USER"
TOKEN=$(curl -s -X POST "$API/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | jq -r '.data.accessToken')
[[ -z "$TOKEN" || "$TOKEN" == "null" ]] && { echo "❌ login failed"; exit 1; }
H_AUTH=(-H "Authorization: Bearer $TOKEN")
H_JSON=(-H "Content-Type: application/json")

# ─── Step 1: Equipment ResourceTypeDefs ────────────────────────────────────────
upsert_type() {
  # Args: code name prefix sort
  local code=$1 name=$2 prefix=$3 sort=$4
  local existing
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/resource-types" \
    | jq -r --arg c "$code" '.data[] | select(.code == $c) | .id')
  if [[ -n "$existing" && "$existing" != "null" ]]; then
    echo "  ✓ $name (existing $existing)"
  else
    local id
    id=$(curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/resource-types" \
      -d "{\"code\":\"$code\",\"name\":\"$name\",\"baseCategory\":\"NONLABOR\",\"codePrefix\":\"$prefix\",\"sortOrder\":$sort,\"active\":true}" \
      | jq -r '.data.id')
    echo "  + $name ($id)"
  fi
}

# Resolve a ResourceTypeDef id by code via the API.
type_id() {
  curl -s "${H_AUTH[@]}" "$API/v1/resource-types" \
    | jq -r --arg c "$1" '.data[] | select(.code == $c) | .id'
}

echo "→ Resource Types"
upsert_type BULL_DOZER         "Bull Dozer"         BD   40
upsert_type FRONT_END_LOADER   "Front End Loader"   FEL  41
upsert_type MOTOR_GRADER       "Motor Grader"       MG   42
upsert_type EXCAVATOR          "Excavator"          EXC  43
upsert_type VIBRATORY_ROLLER   "Vibratory Roller"   VR   44
upsert_type WATER_TANKER       "Water Tanker"       WT   45
upsert_type TIPPER             "Tipper"             TIP  46
upsert_type TIPPER_TRAILER     "Tipper Trailer"     TT   47
upsert_type JCB                "JCB"                JCB  48

# ─── Step 2: WorkActivities ───────────────────────────────────────────────────
upsert_wa() {
  local name=$1 unit=$2 sort=$3
  local existing
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/work-activities" \
    | jq -r --arg n "$name" '.data[] | select((.name | ascii_downcase) == ($n | ascii_downcase)) | .id')
  if [[ -n "$existing" && "$existing" != "null" ]]; then
    echo "  ✓ $name (existing $existing)"
  else
    local id
    id=$(curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/work-activities" \
      -d "{\"name\":\"$name\",\"defaultUnit\":\"$unit\",\"sortOrder\":$sort,\"active\":true}" \
      | jq -r '.data.id')
    echo "  + $name ($id)"
  fi
}

wa_id() {
  curl -s "${H_AUTH[@]}" "$API/v1/work-activities" \
    | jq -r --arg n "$1" '.data[] | select((.name | ascii_downcase) == ($n | ascii_downcase)) | .id'
}

echo "→ Work Activities"
upsert_wa "Clearing & Grubbing"                   "Sqm" 10
upsert_wa "Unclassified Excavation"               "Cum" 20
upsert_wa "Collection of material for EMB"        "Cum" 30
upsert_wa "Subgrade Preparation in cut"           "Sqm" 40
upsert_wa "Collection of material for GSB"        "Cum" 50
upsert_wa "Extraction of ABC material"            "Cum" 60
upsert_wa "BBC/BWC Aggregate borrow excavation"   "Cum" 70
upsert_wa "Maintenance and protection of Trafic"  "Cum" 80
upsert_wa "Mobile Crusher"                        "Cum" 90

# ─── Step 3: Per-(equipment-type × activity) norms ─────────────────────────────
upsert_norm() {
  local activity_name=$1 type_code=$2 unit=$3 output_per_day=$4 spec=$5
  local wa td
  wa=$(wa_id "$activity_name")
  td=$(type_id "$type_code")
  if [[ -z "$wa" || "$wa" == "null" || -z "$td" || "$td" == "null" ]]; then
    echo "  skip: missing wa($activity_name) or type($type_code)"
    return
  fi
  curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/productivity-norms" \
    -d "{\"normType\":\"EQUIPMENT\",\"workActivityId\":\"$wa\",\"resourceTypeDefId\":\"$td\",\"unit\":\"$unit\",\"outputPerDay\":$output_per_day,\"equipmentSpec\":\"$spec\"}" \
    | jq -r --arg n "$activity_name" --arg t "$type_code" \
        'if .error then "  ⚠ \($n) × \($t): \(.error.message)" else "  + \($n) × \($t): \(.data.outputPerDay) \(.data.unit)" end'
}

echo "→ Norms (Bull Dozer)"
upsert_norm "Clearing & Grubbing"                  BULL_DOZER Sqm 4000 ""
upsert_norm "Unclassified Excavation"              BULL_DOZER Cum  900 ""
upsert_norm "Collection of material for EMB"       BULL_DOZER Cum  833 ""
upsert_norm "Subgrade Preparation in cut"          BULL_DOZER Sqm 1000 ""
upsert_norm "Collection of material for GSB"       BULL_DOZER Cum  909 ""
upsert_norm "Extraction of ABC material"           BULL_DOZER Cum  857 ""
upsert_norm "BBC/BWC Aggregate borrow excavation"  BULL_DOZER Cum  577 ""
upsert_norm "Maintenance and protection of Trafic" BULL_DOZER Cum  833 ""
upsert_norm "Mobile Crusher"                       BULL_DOZER Cum  550 ""

echo "→ Norms (Front End Loader)"
upsert_norm "Clearing & Grubbing"                  FRONT_END_LOADER Sqm 3000 ""
upsert_norm "Unclassified Excavation"              FRONT_END_LOADER Cum  600 ""
upsert_norm "Subgrade Preparation in cut"          FRONT_END_LOADER Cum 1000 ""

echo "→ Norms (Motor Grader)"
upsert_norm "Clearing & Grubbing"                  MOTOR_GRADER Sqm 3000 ""
upsert_norm "Unclassified Excavation"              MOTOR_GRADER Cum  600 ""
upsert_norm "Subgrade Preparation in cut"          MOTOR_GRADER Sqm 1000 ""

echo "→ Norms (Excavator)"
upsert_norm "Unclassified Excavation"              EXCAVATOR    Cum  300 ""
upsert_norm "Extraction of ABC material"           EXCAVATOR    Cum  700 ""
upsert_norm "BBC/BWC Aggregate borrow excavation"  EXCAVATOR    Cum  385 ""

echo "→ Norms (Vibratory Roller)"
upsert_norm "Clearing & Grubbing"                  VIBRATORY_ROLLER Sqm 3000 ""
upsert_norm "Subgrade Preparation in cut"          VIBRATORY_ROLLER Cum 1000 ""

echo "→ Norms (Water Tanker)"
upsert_norm "Clearing & Grubbing"                  WATER_TANKER Sqm 3000 ""
upsert_norm "Subgrade Preparation in cut"          WATER_TANKER Cum 1000 ""

echo "→ Norms (Tipper)"
upsert_norm "Collection of material for EMB"       TIPPER Cum 120 "Standard tipper"
upsert_norm "Collection of material for GSB"       TIPPER Cum  80 ""

echo "→ Norms (JCB)"
upsert_norm "Unclassified Excavation"              JCB Cum 50 ""

echo "✅ Oman equipment + norms seed complete"

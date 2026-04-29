#!/usr/bin/env bash
#
# Seed the Oman Labour Master catalogue (44 designations) + optionally bind them
# as per-project deployments.
#
# Usage:
#   ./scripts/seed-oman-labour.sh
#   ./scripts/seed-oman-labour.sh --with-deployments <projectId>
#
# Requires: bash 3+, curl, jq. Backend running on :8080 with admin/admin123.
# Idempotent: skips designations/deployments that already exist.

set -euo pipefail

API="${API:-http://localhost:8080}"
USER_NAME="${USER_NAME:-admin}"
PASSWORD="${PASSWORD:-admin123}"

DATASET="$(cd "$(dirname "$0")/.." && pwd)/backend/bipros-api/src/main/resources/oman-labour-master.json"

# ─── Parse args ──────────────────────────────────────────────────────────────
WITH_DEPLOYMENTS=false
PROJECT_ID=""

while [[ $# -gt 0 ]]; do
  case $1 in
    --with-deployments)
      WITH_DEPLOYMENTS=true
      PROJECT_ID="${2:-}"
      [[ -z "$PROJECT_ID" ]] && { echo "❌ --with-deployments requires a <projectId>"; exit 1; }
      shift 2
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: $0 [--with-deployments <projectId>]"
      exit 1
      ;;
  esac
done

# ─── Login ────────────────────────────────────────────────────────────────────
echo "→ logging in as $USER_NAME"
TOKEN=$(curl -s -X POST "$API/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$PASSWORD\"}" | jq -r '.data.accessToken')
[[ -z "$TOKEN" || "$TOKEN" == "null" ]] && { echo "❌ login failed"; exit 1; }
H_AUTH=(-H "Authorization: Bearer $TOKEN")
H_JSON=(-H "Content-Type: application/json")

# ─── Step 1: Upsert designations ──────────────────────────────────────────────
echo "→ Labour Designations"
SEEDED_DESIG=0
SKIPPED_DESIG=0

total=$(jq 'length' "$DATASET")

for i in $(seq 0 $((total - 1))); do
  row=$(jq ".[$i]" "$DATASET")
  code=$(echo "$row" | jq -r '.code')

  # Check if already exists
  existing=$(curl -s "${H_AUTH[@]}" "$API/v1/labour-designations/by-code/$code" \
    | jq -r '.data.id // empty')

  if [[ -n "$existing" ]]; then
    echo "  ✓ $code (existing $existing)"
    SKIPPED_DESIG=$((SKIPPED_DESIG + 1))
    continue
  fi

  # Build the LabourDesignationRequest body (strip workerCount)
  body=$(echo "$row" | jq '{
    code,
    designation,
    category,
    trade,
    grade,
    nationality,
    experienceYearsMin,
    defaultDailyRate,
    currency: "OMR",
    skills,
    certifications,
    status: "ACTIVE",
    sortOrder
  }')

  result=$(curl -s "${H_AUTH[@]}" "${H_JSON[@]}" -X POST "$API/v1/labour-designations" \
    -d "$body")
  id=$(echo "$result" | jq -r '.data.id // empty')

  if [[ -n "$id" ]]; then
    echo "  + $code ($id)"
    SEEDED_DESIG=$((SEEDED_DESIG + 1))
  else
    err=$(echo "$result" | jq -r '.error.message // .message // "unknown error"')
    echo "  ⚠ $code: $err"
  fi
done

echo "Seeded $SEEDED_DESIG designations ($SKIPPED_DESIG already existed)"

# ─── Step 2 (optional): Per-project deployments ───────────────────────────────
if [[ "$WITH_DEPLOYMENTS" == "true" ]]; then
  echo "→ Labour Deployments for project $PROJECT_ID"
  SEEDED_DEPLOY=0
  SKIPPED_DEPLOY=0

  # Fetch existing deployments for this project to avoid duplication
  existing_deploys=$(curl -s "${H_AUTH[@]}" \
    "$API/v1/projects/$PROJECT_ID/labour-deployments" \
    | jq -r '[.data[] | .designation.id] // []')

  for i in $(seq 0 $((total - 1))); do
    row=$(jq ".[$i]" "$DATASET")
    code=$(echo "$row" | jq -r '.code')
    worker_count=$(echo "$row" | jq -r '.workerCount')

    # Resolve designationId by code
    desig_id=$(curl -s "${H_AUTH[@]}" "$API/v1/labour-designations/by-code/$code" \
      | jq -r '.data.id // empty')

    if [[ -z "$desig_id" ]]; then
      echo "  ⚠ $code: designation not found, skipping deployment"
      continue
    fi

    # Skip if already deployed
    already=$(echo "$existing_deploys" | jq -r --arg id "$desig_id" 'if . then map(select(. == $id)) | length else 0 end')
    if [[ "$already" -gt 0 ]]; then
      echo "  ✓ $code deployment (already exists)"
      SKIPPED_DEPLOY=$((SKIPPED_DEPLOY + 1))
      continue
    fi

    body=$(jq -n --arg desig_id "$desig_id" --argjson wc "$worker_count" \
      '{designationId: $desig_id, workerCount: $wc}')

    result=$(curl -s "${H_AUTH[@]}" "${H_JSON[@]}" \
      -X POST "$API/v1/projects/$PROJECT_ID/labour-deployments" \
      -d "$body")
    id=$(echo "$result" | jq -r '.data.id // empty')

    if [[ -n "$id" ]]; then
      echo "  + $code deployment ($id, workerCount=$worker_count)"
      SEEDED_DEPLOY=$((SEEDED_DEPLOY + 1))
    else
      err=$(echo "$result" | jq -r '.error.message // .message // "unknown error"')
      echo "  ⚠ $code deployment: $err"
    fi
  done

  echo "Seeded $SEEDED_DEPLOY deployments for project $PROJECT_ID ($SKIPPED_DEPLOY already existed)"
fi

echo "✅ Oman labour master seed complete"

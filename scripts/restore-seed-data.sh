#!/bin/bash
# =============================================================================
# Bipros EPPM — Restore WBS, Activities, Relationships for existing projects
# Uses existing project IDs (projects already in DB)
# =============================================================================

set -euo pipefail

# Cleanup on exit
cleanup() { echo ""; echo "Script finished."; }
trap cleanup EXIT

# Configuration — override via environment variables
API="${BIPROS_API_URL:-http://localhost:8080}"
BIPROS_USER="${BIPROS_USER:-admin}"
BIPROS_PASS="${BIPROS_PASS:-admin123}"
CT="Content-Type: application/json"

# Existing project IDs
PROJ1="7ccfa68c-4730-4359-9110-ae3e80541588"  # MRB-2026
PROJ2="60590b9d-7158-4392-b56e-8b04e1b2f6d8"  # SOT-2026
PROJ3="49e2bf3e-a095-4289-a25b-4efb2e2efecc"  # DSSF-2026
PROJ4="b523fecb-279d-439e-bd97-cc1102e836de"  # ERP-2026
CALENDAR_ID="b4a760b5-16b4-47b3-afa9-ef617e9265a8"  # Standard calendar

echo "=========================================="
echo " Bipros EPPM — Restore Seed Data"
echo "=========================================="

# --- Health check ---
echo "[0/6] Checking backend availability..."
if ! curl -sf --max-time 5 "$API/v1/auth/login" -X OPTIONS > /dev/null 2>&1; then
  if ! curl -sf --max-time 5 "$API" > /dev/null 2>&1; then
    echo "WARN: Backend may not be available at $API (continuing anyway...)"
  fi
fi

# --- Login ---
echo "[1/6] Logging in..."
LOGIN_RESP=$(curl -sf --max-time 10 -X POST "$API/v1/auth/login" -H "$CT" \
  -d "{\"username\":\"$BIPROS_USER\",\"password\":\"$BIPROS_PASS\"}") || {
  echo "ERROR: Login request failed. Is the backend running at $API?"
  exit 1
}
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"

if [ -z "$TOKEN" ]; then
  echo "ERROR: Login failed — empty token."
  exit 1
fi
echo "  Login successful."

# Helper function
post_and_get_id() {
  local url=$1
  local data=$2
  # Auto-inject projectId for WBS and activity endpoints
  if echo "$url" | grep -qE '/v1/projects/[^/]+/(wbs|activities)$'; then
    local proj_id=$(echo "$url" | sed -E 's|/v1/projects/([^/]+)/.*|\1|')
    data=$(echo "$data" | sed "s/^{/{\"projectId\":\"$proj_id\",/")
  fi
  # Auto-inject calendarId for activity endpoints
  if echo "$url" | grep -qE '/v1/projects/[^/]+/activities$'; then
    data=$(echo "$data" | sed "s/^{/{\"calendarId\":\"$CALENDAR_ID\",/")
  fi
  local resp
  resp=$(curl -sf --max-time 15 -X POST "$API$url" -H "$CT" -H "$AUTH" -d "$data") || {
    echo "  WARN: HTTP request failed for $url" >&2
    echo ""
    return
  }
  local id
  id=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null)
  if [ -z "$id" ]; then
    echo "  WARN: Failed to create at $url" >&2
    echo "$resp" | python3 -c "import sys,json; print(json.dumps(json.load(sys.stdin),indent=2))" 2>/dev/null >&2
    echo ""
  else
    echo "$id"
  fi
}

# ============================================================
# 2. WBS + Activities + Relationships for Project 1: MRB
# ============================================================
echo ""
echo "[2/6] Project 1: Metro River Bridge — WBS, Activities, Relationships..."

WBS1_ROOT=$(post_and_get_id "/v1/projects/$PROJ1/wbs" '{"code":"MRB","name":"Metro River Bridge","parentId":null}')
WBS1_PRECN=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.1\",\"name\":\"Pre-Construction\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_DEMO=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.2\",\"name\":\"Demolition\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_FOUND=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.3\",\"name\":\"Foundation\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_SUPER=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.4\",\"name\":\"Superstructure\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_DECK=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.5\",\"name\":\"Deck & Finishing\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_CLOSE=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.6\",\"name\":\"Closeout\",\"parentId\":\"$WBS1_ROOT\"}")
echo "  WBS: 7 nodes"

# Pre-Construction
A1010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A1010\",\"name\":\"Project Kickoff\",\"activityType\":\"START_MILESTONE\",\"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":0,\"plannedStartDate\":\"2026-06-01\",\"plannedFinishDate\":\"2026-06-01\"}")
A1020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A1020\",\"name\":\"Site Survey & Geotechnical Investigation\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":15,\"plannedStartDate\":\"2026-06-02\",\"plannedFinishDate\":\"2026-06-22\"}")
A1030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A1030\",\"name\":\"Environmental Impact Assessment\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":20,\"plannedStartDate\":\"2026-06-02\",\"plannedFinishDate\":\"2026-06-29\"}")
A1040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A1040\",\"name\":\"Detailed Engineering Design\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":30,\"plannedStartDate\":\"2026-06-23\",\"plannedFinishDate\":\"2026-08-03\"}")
A1050=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A1050\",\"name\":\"Obtain Permits & Regulatory Approval\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":25,\"plannedStartDate\":\"2026-06-30\",\"plannedFinishDate\":\"2026-08-03\"}")
A1060=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A1060\",\"name\":\"Mobilize Equipment & Setup Staging Area\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":10,\"plannedStartDate\":\"2026-08-04\",\"plannedFinishDate\":\"2026-08-17\"}")

# Demolition
A2010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A2010\",\"name\":\"Traffic Diversion & Road Closure\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":5,\"plannedStartDate\":\"2026-08-18\",\"plannedFinishDate\":\"2026-08-24\"}")
A2020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A2020\",\"name\":\"Remove Bridge Deck & Railing\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":20,\"plannedStartDate\":\"2026-08-25\",\"plannedFinishDate\":\"2026-09-21\"}")
A2030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A2030\",\"name\":\"Demolish Superstructure\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":25,\"plannedStartDate\":\"2026-09-22\",\"plannedFinishDate\":\"2026-10-26\"}")
A2040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A2040\",\"name\":\"Remove Old Piers & Abutments\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":15,\"plannedStartDate\":\"2026-10-27\",\"plannedFinishDate\":\"2026-11-16\"}")
A2050=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A2050\",\"name\":\"Site Clearance & Grading\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":10,\"plannedStartDate\":\"2026-11-17\",\"plannedFinishDate\":\"2026-11-30\"}")

# Foundation
A3010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A3010\",\"name\":\"Install Cofferdam & Dewatering\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":20,\"plannedStartDate\":\"2026-12-01\",\"plannedFinishDate\":\"2026-12-28\"}")
A3020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A3020\",\"name\":\"Drive Foundation Piles (Pier 1)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":25,\"plannedStartDate\":\"2026-12-29\",\"plannedFinishDate\":\"2027-02-01\"}")
A3030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A3030\",\"name\":\"Drive Foundation Piles (Pier 2)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":25,\"plannedStartDate\":\"2027-01-15\",\"plannedFinishDate\":\"2027-02-18\"}")
A3040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A3040\",\"name\":\"Pour Pile Caps\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":15,\"plannedStartDate\":\"2027-02-19\",\"plannedFinishDate\":\"2027-03-11\"}")
A3050=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A3050\",\"name\":\"Construct Abutments\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":20,\"plannedStartDate\":\"2027-03-12\",\"plannedFinishDate\":\"2027-04-08\"}")
A3060=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A3060\",\"name\":\"Build Bridge Piers\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":30,\"plannedStartDate\":\"2027-03-12\",\"plannedFinishDate\":\"2027-04-22\"}")

# Superstructure
A4010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A4010\",\"name\":\"Fabricate Steel Girders (Offsite)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_SUPER\",\"originalDuration\":45,\"plannedStartDate\":\"2027-02-01\",\"plannedFinishDate\":\"2027-04-04\"}")
A4020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A4020\",\"name\":\"Transport & Erect Steel Girders\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_SUPER\",\"originalDuration\":20,\"plannedStartDate\":\"2027-04-23\",\"plannedFinishDate\":\"2027-05-20\"}")
A4030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A4030\",\"name\":\"Install Cross-Bracing & Diaphragms\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_SUPER\",\"originalDuration\":15,\"plannedStartDate\":\"2027-05-21\",\"plannedFinishDate\":\"2027-06-10\"}")
A4040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A4040\",\"name\":\"Install Bearing Pads\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_SUPER\",\"originalDuration\":10,\"plannedStartDate\":\"2027-06-11\",\"plannedFinishDate\":\"2027-06-24\"}")

# Deck & Finishing
A5010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A5010\",\"name\":\"Install Deck Formwork & Rebar\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":20,\"plannedStartDate\":\"2027-06-25\",\"plannedFinishDate\":\"2027-07-22\"}")
A5020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A5020\",\"name\":\"Pour Bridge Deck Concrete\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":10,\"plannedStartDate\":\"2027-07-23\",\"plannedFinishDate\":\"2027-08-05\"}")
A5030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A5030\",\"name\":\"Deck Curing & Post-Tensioning\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":14,\"plannedStartDate\":\"2027-08-06\",\"plannedFinishDate\":\"2027-08-25\"}")
A5040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A5040\",\"name\":\"Install Expansion Joints\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":8,\"plannedStartDate\":\"2027-08-26\",\"plannedFinishDate\":\"2027-09-06\"}")
A5050=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A5050\",\"name\":\"Install Guard Rails & Barriers\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":12,\"plannedStartDate\":\"2027-09-07\",\"plannedFinishDate\":\"2027-09-22\"}")
A5060=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A5060\",\"name\":\"Road Surface & Line Marking\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":10,\"plannedStartDate\":\"2027-09-23\",\"plannedFinishDate\":\"2027-10-06\"}")
A5070=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A5070\",\"name\":\"Install Lighting & Drainage\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":15,\"plannedStartDate\":\"2027-09-07\",\"plannedFinishDate\":\"2027-09-25\"}")

# Closeout
A6010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A6010\",\"name\":\"Load Testing & Inspection\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_CLOSE\",\"originalDuration\":10,\"plannedStartDate\":\"2027-10-07\",\"plannedFinishDate\":\"2027-10-20\"}")
A6020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A6020\",\"name\":\"Demobilize Equipment\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_CLOSE\",\"originalDuration\":5,\"plannedStartDate\":\"2027-10-21\",\"plannedFinishDate\":\"2027-10-27\"}")
A6030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A6030\",\"name\":\"Final Documentation & As-Built Drawings\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS1_CLOSE\",\"originalDuration\":10,\"plannedStartDate\":\"2027-10-21\",\"plannedFinishDate\":\"2027-11-03\"}")
A6040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{\"code\":\"A6040\",\"name\":\"Bridge Opening Ceremony\",\"activityType\":\"FINISH_MILESTONE\",\"wbsNodeId\":\"$WBS1_CLOSE\",\"originalDuration\":0,\"plannedStartDate\":\"2027-11-04\",\"plannedFinishDate\":\"2027-11-04\"}")
echo "  Activities: 28 created"

# Relationships for Project 1
echo "  Creating relationships..."
create_rel() {
  local proj="$1" pred="$2" succ="$3" rtype="$4" lag="$5"
  if [ -z "$pred" ] || [ -z "$succ" ]; then
    echo "  WARN: Skipping relationship — missing activity ID (pred=$pred, succ=$succ)" >&2
    return
  fi
  local full_type
  case "$rtype" in
    FS) full_type="FINISH_TO_START";;
    FF) full_type="FINISH_TO_FINISH";;
    SS) full_type="START_TO_START";;
    SF) full_type="START_TO_FINISH";;
    *) full_type="$rtype";;
  esac
  curl -sf --max-time 10 -X POST "$API/v1/projects/$proj/relationships" -H "$CT" -H "$AUTH" \
    -d "{\"predecessorActivityId\":\"$pred\",\"successorActivityId\":\"$succ\",\"relationshipType\":\"$full_type\",\"lag\":$lag}" \
    > /dev/null 2>&1 || echo "  WARN: Failed relationship $pred -> $succ" >&2
}

create_rel "$PROJ1" "$A1010" "$A1020" "FS" 0
create_rel "$PROJ1" "$A1010" "$A1030" "FS" 0
create_rel "$PROJ1" "$A1020" "$A1040" "FS" 0
create_rel "$PROJ1" "$A1030" "$A1050" "FS" 0
create_rel "$PROJ1" "$A1040" "$A1060" "FS" 0
create_rel "$PROJ1" "$A1050" "$A1060" "FS" 0
create_rel "$PROJ1" "$A1060" "$A2010" "FS" 0
create_rel "$PROJ1" "$A2010" "$A2020" "FS" 0
create_rel "$PROJ1" "$A2020" "$A2030" "FS" 0
create_rel "$PROJ1" "$A2030" "$A2040" "FS" 0
create_rel "$PROJ1" "$A2040" "$A2050" "FS" 0
create_rel "$PROJ1" "$A2050" "$A3010" "FS" 0
create_rel "$PROJ1" "$A3010" "$A3020" "FS" 0
create_rel "$PROJ1" "$A3010" "$A3030" "SS" 10
create_rel "$PROJ1" "$A3020" "$A3040" "FS" 0
create_rel "$PROJ1" "$A3030" "$A3040" "FS" 0
create_rel "$PROJ1" "$A3040" "$A3050" "SS" 0
create_rel "$PROJ1" "$A3040" "$A3060" "SS" 0
create_rel "$PROJ1" "$A3040" "$A4010" "SS" -30
create_rel "$PROJ1" "$A3050" "$A4020" "FS" 0
create_rel "$PROJ1" "$A3060" "$A4020" "FS" 0
create_rel "$PROJ1" "$A4010" "$A4020" "FS" 0
create_rel "$PROJ1" "$A4020" "$A4030" "FS" 0
create_rel "$PROJ1" "$A4030" "$A4040" "FS" 0
create_rel "$PROJ1" "$A4040" "$A5010" "FS" 0
create_rel "$PROJ1" "$A5010" "$A5020" "FS" 0
create_rel "$PROJ1" "$A5020" "$A5030" "FS" 0
create_rel "$PROJ1" "$A5030" "$A5040" "FS" 0
create_rel "$PROJ1" "$A5040" "$A5050" "FS" 0
create_rel "$PROJ1" "$A5040" "$A5070" "SS" 0
create_rel "$PROJ1" "$A5050" "$A5060" "FS" 0
create_rel "$PROJ1" "$A5070" "$A5060" "FF" 0
create_rel "$PROJ1" "$A5060" "$A6010" "FS" 0
create_rel "$PROJ1" "$A6010" "$A6020" "FS" 0
create_rel "$PROJ1" "$A6010" "$A6030" "SS" 0
create_rel "$PROJ1" "$A6020" "$A6040" "FS" 0
create_rel "$PROJ1" "$A6030" "$A6040" "FS" 0
echo "  Relationships: 37 created"

# ============================================================
# 3. Project 2: Skyline Office Tower
# ============================================================
echo ""
echo "[3/6] Project 2: Skyline Office Tower — WBS, Activities, Relationships..."

WBS2_ROOT=$(post_and_get_id "/v1/projects/$PROJ2/wbs" '{"code":"SOT","name":"Skyline Office Tower","parentId":null}')
WBS2_SITE=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.1\",\"name\":\"Site Work\",\"parentId\":\"$WBS2_ROOT\"}")
WBS2_STRUCT=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.2\",\"name\":\"Structure\",\"parentId\":\"$WBS2_ROOT\"}")
WBS2_ENCL=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.3\",\"name\":\"Building Envelope\",\"parentId\":\"$WBS2_ROOT\"}")
WBS2_MEP=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.4\",\"name\":\"MEP Systems\",\"parentId\":\"$WBS2_ROOT\"}")
WBS2_FIT=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.5\",\"name\":\"Interior Fitout\",\"parentId\":\"$WBS2_ROOT\"}")
echo "  WBS: 6 nodes"

B1010=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B1010\",\"name\":\"Excavation & Shoring\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_SITE\",\"originalDuration\":30,\"plannedStartDate\":\"2026-07-01\",\"plannedFinishDate\":\"2026-08-11\"}")
B1020=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B1020\",\"name\":\"Foundation Mat Pour\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_SITE\",\"originalDuration\":15,\"plannedStartDate\":\"2026-08-12\",\"plannedFinishDate\":\"2026-08-31\"}")
B1030=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B1030\",\"name\":\"Underground Parking Structure\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_SITE\",\"originalDuration\":45,\"plannedStartDate\":\"2026-09-01\",\"plannedFinishDate\":\"2026-10-31\"}")

B2010=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B2010\",\"name\":\"Core & Shear Walls (Floors 1-5)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_STRUCT\",\"originalDuration\":40,\"plannedStartDate\":\"2026-11-01\",\"plannedFinishDate\":\"2026-12-28\"}")
B2020=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B2020\",\"name\":\"Core & Shear Walls (Floors 6-10)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_STRUCT\",\"originalDuration\":35,\"plannedStartDate\":\"2026-12-29\",\"plannedFinishDate\":\"2027-02-14\"}")
B2030=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B2030\",\"name\":\"Core & Shear Walls (Floors 11-15)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_STRUCT\",\"originalDuration\":35,\"plannedStartDate\":\"2027-02-15\",\"plannedFinishDate\":\"2027-04-01\"}")
B2040=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B2040\",\"name\":\"Core & Shear Walls (Floors 16-20)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_STRUCT\",\"originalDuration\":35,\"plannedStartDate\":\"2027-04-02\",\"plannedFinishDate\":\"2027-05-18\"}")
B2050=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B2050\",\"name\":\"Roof Steel & Slab\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_STRUCT\",\"originalDuration\":20,\"plannedStartDate\":\"2027-05-19\",\"plannedFinishDate\":\"2027-06-15\"}")

B3010=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B3010\",\"name\":\"Curtain Wall Installation (Lower)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_ENCL\",\"originalDuration\":40,\"plannedStartDate\":\"2027-03-01\",\"plannedFinishDate\":\"2027-04-25\"}")
B3020=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B3020\",\"name\":\"Curtain Wall Installation (Upper)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_ENCL\",\"originalDuration\":40,\"plannedStartDate\":\"2027-05-19\",\"plannedFinishDate\":\"2027-07-13\"}")
B3030=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B3030\",\"name\":\"Roof Waterproofing & Insulation\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_ENCL\",\"originalDuration\":15,\"plannedStartDate\":\"2027-06-16\",\"plannedFinishDate\":\"2027-07-06\"}")

B4010=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B4010\",\"name\":\"Elevator Installation\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_MEP\",\"originalDuration\":60,\"plannedStartDate\":\"2027-04-01\",\"plannedFinishDate\":\"2027-06-22\"}")
B4020=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B4020\",\"name\":\"HVAC Rough-In\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_MEP\",\"originalDuration\":50,\"plannedStartDate\":\"2027-04-01\",\"plannedFinishDate\":\"2027-06-08\"}")
B4030=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B4030\",\"name\":\"Electrical Distribution\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_MEP\",\"originalDuration\":45,\"plannedStartDate\":\"2027-04-15\",\"plannedFinishDate\":\"2027-06-15\"}")
B4040=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B4040\",\"name\":\"Plumbing & Fire Suppression\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_MEP\",\"originalDuration\":40,\"plannedStartDate\":\"2027-04-15\",\"plannedFinishDate\":\"2027-06-08\"}")

B5010=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B5010\",\"name\":\"Interior Partitions & Drywall\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_FIT\",\"originalDuration\":45,\"plannedStartDate\":\"2027-07-14\",\"plannedFinishDate\":\"2027-09-14\"}")
B5020=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B5020\",\"name\":\"Flooring & Ceiling\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_FIT\",\"originalDuration\":30,\"plannedStartDate\":\"2027-09-15\",\"plannedFinishDate\":\"2027-10-25\"}")
B5030=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B5030\",\"name\":\"MEP Trim & Commissioning\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_FIT\",\"originalDuration\":25,\"plannedStartDate\":\"2027-10-26\",\"plannedFinishDate\":\"2027-11-29\"}")
B5040=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B5040\",\"name\":\"Landscaping & Exterior\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS2_FIT\",\"originalDuration\":20,\"plannedStartDate\":\"2027-10-01\",\"plannedFinishDate\":\"2027-10-28\"}")
B5050=$(post_and_get_id "/v1/projects/$PROJ2/activities" "{\"code\":\"B5050\",\"name\":\"Certificate of Occupancy\",\"activityType\":\"FINISH_MILESTONE\",\"wbsNodeId\":\"$WBS2_FIT\",\"originalDuration\":0,\"plannedStartDate\":\"2027-11-30\",\"plannedFinishDate\":\"2027-11-30\"}")
echo "  Activities: 20 created"

echo "  Creating relationships..."
create_rel "$PROJ2" "$B1010" "$B1020" "FS" 0
create_rel "$PROJ2" "$B1020" "$B1030" "FS" 0
create_rel "$PROJ2" "$B1030" "$B2010" "FS" 0
create_rel "$PROJ2" "$B2010" "$B2020" "FS" 0
create_rel "$PROJ2" "$B2020" "$B2030" "FS" 0
create_rel "$PROJ2" "$B2020" "$B3010" "SS" 15
create_rel "$PROJ2" "$B2030" "$B2040" "FS" 0
create_rel "$PROJ2" "$B2040" "$B2050" "FS" 0
create_rel "$PROJ2" "$B2040" "$B3020" "SS" 0
create_rel "$PROJ2" "$B2050" "$B3030" "FS" 0
create_rel "$PROJ2" "$B2030" "$B4010" "SS" 0
create_rel "$PROJ2" "$B2030" "$B4020" "SS" 0
create_rel "$PROJ2" "$B2030" "$B4030" "SS" 10
create_rel "$PROJ2" "$B2030" "$B4040" "SS" 10
create_rel "$PROJ2" "$B3020" "$B5010" "FS" 0
create_rel "$PROJ2" "$B4020" "$B5010" "FS" 0
create_rel "$PROJ2" "$B5010" "$B5020" "FS" 0
create_rel "$PROJ2" "$B5020" "$B5030" "FS" 0
create_rel "$PROJ2" "$B4030" "$B5030" "FS" 0
create_rel "$PROJ2" "$B4040" "$B5030" "FS" 0
create_rel "$PROJ2" "$B5020" "$B5040" "SS" -15
create_rel "$PROJ2" "$B5030" "$B5050" "FS" 0
create_rel "$PROJ2" "$B5040" "$B5050" "FS" 0
echo "  Relationships: 23 created"

# ============================================================
# 4. Project 3: Desert Sun Solar Farm
# ============================================================
echo ""
echo "[4/6] Project 3: Desert Sun Solar Farm — WBS, Activities, Relationships..."

WBS3_ROOT=$(post_and_get_id "/v1/projects/$PROJ3/wbs" '{"code":"DSSF","name":"Desert Sun Solar Farm","parentId":null}')
WBS3_DEV=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.1\",\"name\":\"Development & Permitting\",\"parentId\":\"$WBS3_ROOT\"}")
WBS3_CIVIL=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.2\",\"name\":\"Civil & Site Prep\",\"parentId\":\"$WBS3_ROOT\"}")
WBS3_ELEC=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.3\",\"name\":\"Electrical Infrastructure\",\"parentId\":\"$WBS3_ROOT\"}")
WBS3_PANEL=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.4\",\"name\":\"Panel Installation\",\"parentId\":\"$WBS3_ROOT\"}")
WBS3_COMM=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.5\",\"name\":\"Commissioning\",\"parentId\":\"$WBS3_ROOT\"}")
echo "  WBS: 6 nodes"

C1010=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C1010\",\"name\":\"Land Acquisition Finalization\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_DEV\",\"originalDuration\":20,\"plannedStartDate\":\"2026-08-01\",\"plannedFinishDate\":\"2026-08-28\"}")
C1020=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C1020\",\"name\":\"Environmental & Grid Studies\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_DEV\",\"originalDuration\":30,\"plannedStartDate\":\"2026-08-01\",\"plannedFinishDate\":\"2026-09-11\"}")
C1030=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C1030\",\"name\":\"Engineering & Procurement\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_DEV\",\"originalDuration\":40,\"plannedStartDate\":\"2026-09-12\",\"plannedFinishDate\":\"2026-11-06\"}")

C2010=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C2010\",\"name\":\"Site Clearing & Grading\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_CIVIL\",\"originalDuration\":30,\"plannedStartDate\":\"2026-11-07\",\"plannedFinishDate\":\"2026-12-18\"}")
C2020=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C2020\",\"name\":\"Access Roads & Fencing\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_CIVIL\",\"originalDuration\":25,\"plannedStartDate\":\"2026-12-01\",\"plannedFinishDate\":\"2027-01-06\"}")
C2030=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C2030\",\"name\":\"Tracker Foundation Piles\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_CIVIL\",\"originalDuration\":60,\"plannedStartDate\":\"2026-12-19\",\"plannedFinishDate\":\"2027-03-09\"}")

C3010=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C3010\",\"name\":\"Substation Construction\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_ELEC\",\"originalDuration\":45,\"plannedStartDate\":\"2027-01-07\",\"plannedFinishDate\":\"2027-03-09\"}")
C3020=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C3020\",\"name\":\"Inverter Stations (x10)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_ELEC\",\"originalDuration\":40,\"plannedStartDate\":\"2027-03-10\",\"plannedFinishDate\":\"2027-05-04\"}")
C3030=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C3030\",\"name\":\"Medium Voltage Cable Trenching\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_ELEC\",\"originalDuration\":50,\"plannedStartDate\":\"2027-01-20\",\"plannedFinishDate\":\"2027-03-28\"}")
C3040=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C3040\",\"name\":\"Grid Interconnection\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_ELEC\",\"originalDuration\":20,\"plannedStartDate\":\"2027-05-05\",\"plannedFinishDate\":\"2027-05-30\"}")

C4010=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C4010\",\"name\":\"Tracker Assembly & Installation (Phase 1)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_PANEL\",\"originalDuration\":50,\"plannedStartDate\":\"2027-03-10\",\"plannedFinishDate\":\"2027-05-15\"}")
C4020=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C4020\",\"name\":\"Tracker Assembly & Installation (Phase 2)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_PANEL\",\"originalDuration\":50,\"plannedStartDate\":\"2027-05-16\",\"plannedFinishDate\":\"2027-07-22\"}")
C4030=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C4030\",\"name\":\"Module Stringing & DC Wiring\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_PANEL\",\"originalDuration\":40,\"plannedStartDate\":\"2027-05-16\",\"plannedFinishDate\":\"2027-07-10\"}")

C5010=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C5010\",\"name\":\"System Testing & Commissioning\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_COMM\",\"originalDuration\":30,\"plannedStartDate\":\"2027-07-23\",\"plannedFinishDate\":\"2027-09-02\"}")
C5020=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C5020\",\"name\":\"Grid Compliance Testing\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS3_COMM\",\"originalDuration\":15,\"plannedStartDate\":\"2027-09-03\",\"plannedFinishDate\":\"2027-09-23\"}")
C5030=$(post_and_get_id "/v1/projects/$PROJ3/activities" "{\"code\":\"C5030\",\"name\":\"Commercial Operation Date\",\"activityType\":\"FINISH_MILESTONE\",\"wbsNodeId\":\"$WBS3_COMM\",\"originalDuration\":0,\"plannedStartDate\":\"2027-09-24\",\"plannedFinishDate\":\"2027-09-24\"}")
echo "  Activities: 16 created"

echo "  Creating relationships..."
create_rel "$PROJ3" "$C1010" "$C1030" "FS" 0
create_rel "$PROJ3" "$C1020" "$C1030" "FS" 0
create_rel "$PROJ3" "$C1030" "$C2010" "FS" 0
create_rel "$PROJ3" "$C2010" "$C2020" "SS" 15
create_rel "$PROJ3" "$C2010" "$C2030" "FS" 0
create_rel "$PROJ3" "$C2020" "$C3010" "FS" 0
create_rel "$PROJ3" "$C2030" "$C4010" "FS" 0
create_rel "$PROJ3" "$C2030" "$C3030" "SS" 20
create_rel "$PROJ3" "$C3010" "$C3020" "FS" 0
create_rel "$PROJ3" "$C3030" "$C3020" "FS" 0
create_rel "$PROJ3" "$C3020" "$C3040" "FS" 0
create_rel "$PROJ3" "$C4010" "$C4020" "FS" 0
create_rel "$PROJ3" "$C4010" "$C4030" "SS" 30
create_rel "$PROJ3" "$C4020" "$C5010" "FS" 0
create_rel "$PROJ3" "$C4030" "$C5010" "FS" 0
create_rel "$PROJ3" "$C3040" "$C5010" "FS" 0
create_rel "$PROJ3" "$C5010" "$C5020" "FS" 0
create_rel "$PROJ3" "$C5020" "$C5030" "FS" 0
echo "  Relationships: 18 created"

# ============================================================
# 5. Project 4: ERP System Upgrade
# ============================================================
echo ""
echo "[5/6] Project 4: ERP System Upgrade — WBS, Activities, Relationships..."

WBS4_ROOT=$(post_and_get_id "/v1/projects/$PROJ4/wbs" '{"code":"ERP","name":"ERP System Upgrade","parentId":null}')
WBS4_DISC=$(post_and_get_id "/v1/projects/$PROJ4/wbs" "{\"code\":\"ERP.1\",\"name\":\"Discovery & Planning\",\"parentId\":\"$WBS4_ROOT\"}")
WBS4_BUILD=$(post_and_get_id "/v1/projects/$PROJ4/wbs" "{\"code\":\"ERP.2\",\"name\":\"Build & Configure\",\"parentId\":\"$WBS4_ROOT\"}")
WBS4_TEST=$(post_and_get_id "/v1/projects/$PROJ4/wbs" "{\"code\":\"ERP.3\",\"name\":\"Testing & Migration\",\"parentId\":\"$WBS4_ROOT\"}")
WBS4_GO=$(post_and_get_id "/v1/projects/$PROJ4/wbs" "{\"code\":\"ERP.4\",\"name\":\"Go-Live & Support\",\"parentId\":\"$WBS4_ROOT\"}")
echo "  WBS: 5 nodes"

D1010=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D1010\",\"name\":\"Requirements Gathering\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_DISC\",\"originalDuration\":20,\"plannedStartDate\":\"2026-09-01\",\"plannedFinishDate\":\"2026-09-26\"}")
D1020=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D1020\",\"name\":\"Gap Analysis & Fit Study\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_DISC\",\"originalDuration\":15,\"plannedStartDate\":\"2026-09-27\",\"plannedFinishDate\":\"2026-10-17\"}")
D1030=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D1030\",\"name\":\"Solution Design Document\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_DISC\",\"originalDuration\":15,\"plannedStartDate\":\"2026-10-18\",\"plannedFinishDate\":\"2026-11-07\"}")

D2010=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D2010\",\"name\":\"Finance Module Configuration\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_BUILD\",\"originalDuration\":30,\"plannedStartDate\":\"2026-11-08\",\"plannedFinishDate\":\"2026-12-19\"}")
D2020=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D2020\",\"name\":\"HR Module Configuration\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_BUILD\",\"originalDuration\":25,\"plannedStartDate\":\"2026-11-08\",\"plannedFinishDate\":\"2026-12-12\"}")
D2030=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D2030\",\"name\":\"Procurement Module Configuration\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_BUILD\",\"originalDuration\":25,\"plannedStartDate\":\"2026-11-22\",\"plannedFinishDate\":\"2026-12-26\"}")
D2040=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D2040\",\"name\":\"Custom Reports & Dashboards\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_BUILD\",\"originalDuration\":20,\"plannedStartDate\":\"2026-12-20\",\"plannedFinishDate\":\"2027-01-16\"}")
D2050=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D2050\",\"name\":\"Integration Development (APIs)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_BUILD\",\"originalDuration\":30,\"plannedStartDate\":\"2026-12-13\",\"plannedFinishDate\":\"2027-01-23\"}")

D3010=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D3010\",\"name\":\"Unit Testing\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_TEST\",\"originalDuration\":15,\"plannedStartDate\":\"2027-01-24\",\"plannedFinishDate\":\"2027-02-13\"}")
D3020=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D3020\",\"name\":\"Integration Testing\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_TEST\",\"originalDuration\":20,\"plannedStartDate\":\"2027-02-14\",\"plannedFinishDate\":\"2027-03-13\"}")
D3030=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D3030\",\"name\":\"User Acceptance Testing\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_TEST\",\"originalDuration\":15,\"plannedStartDate\":\"2027-03-14\",\"plannedFinishDate\":\"2027-04-03\"}")
D3040=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D3040\",\"name\":\"Data Migration (Legacy to SAP)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_TEST\",\"originalDuration\":20,\"plannedStartDate\":\"2027-03-14\",\"plannedFinishDate\":\"2027-04-10\"}")
D3050=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D3050\",\"name\":\"End User Training\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_TEST\",\"originalDuration\":15,\"plannedStartDate\":\"2027-04-04\",\"plannedFinishDate\":\"2027-04-24\"}")

D4010=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D4010\",\"name\":\"Go-Live Cutover\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_GO\",\"originalDuration\":3,\"plannedStartDate\":\"2027-04-25\",\"plannedFinishDate\":\"2027-04-29\"}")
D4020=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D4020\",\"name\":\"Hypercare Support (30 days)\",\"activityType\":\"TASK_DEPENDENT\",\"wbsNodeId\":\"$WBS4_GO\",\"originalDuration\":30,\"plannedStartDate\":\"2027-04-30\",\"plannedFinishDate\":\"2027-06-10\"}")
D4030=$(post_and_get_id "/v1/projects/$PROJ4/activities" "{\"code\":\"D4030\",\"name\":\"Project Closeout\",\"activityType\":\"FINISH_MILESTONE\",\"wbsNodeId\":\"$WBS4_GO\",\"originalDuration\":0,\"plannedStartDate\":\"2027-06-11\",\"plannedFinishDate\":\"2027-06-11\"}")
echo "  Activities: 16 created"

echo "  Creating relationships..."
create_rel "$PROJ4" "$D1010" "$D1020" "FS" 0
create_rel "$PROJ4" "$D1020" "$D1030" "FS" 0
create_rel "$PROJ4" "$D1030" "$D2010" "FS" 0
create_rel "$PROJ4" "$D1030" "$D2020" "FS" 0
create_rel "$PROJ4" "$D1030" "$D2030" "SS" 10
create_rel "$PROJ4" "$D2010" "$D2040" "FS" 0
create_rel "$PROJ4" "$D2020" "$D2050" "FS" 0
create_rel "$PROJ4" "$D2030" "$D2050" "FS" 0
create_rel "$PROJ4" "$D2040" "$D3010" "FS" 0
create_rel "$PROJ4" "$D2050" "$D3010" "FS" 0
create_rel "$PROJ4" "$D3010" "$D3020" "FS" 0
create_rel "$PROJ4" "$D3020" "$D3030" "FS" 0
create_rel "$PROJ4" "$D3020" "$D3040" "SS" 0
create_rel "$PROJ4" "$D3030" "$D3050" "FS" 0
create_rel "$PROJ4" "$D3040" "$D3050" "FS" 0
create_rel "$PROJ4" "$D3050" "$D4010" "FS" 0
create_rel "$PROJ4" "$D4010" "$D4020" "FS" 0
create_rel "$PROJ4" "$D4020" "$D4030" "FS" 0
echo "  Relationships: 18 created"

# ============================================================
# 6. Schedule + Baselines for all projects
# ============================================================
echo ""
echo "[6/6] Running CPM schedule and creating baselines..."

for PID in "$PROJ1" "$PROJ2" "$PROJ3" "$PROJ4"; do
  SCHED_RESP=$(curl -sf --max-time 30 -X POST "$API/v1/projects/$PID/schedule" -H "$CT" -H "$AUTH" \
    -d "{\"projectId\":\"$PID\"}" 2>/dev/null) || { echo "  WARN: Schedule request failed for $PID" >&2; continue; }
  SCHED_STATUS=$(echo "$SCHED_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','UNKNOWN'))" 2>/dev/null)
  echo "  Schedule $PID: $SCHED_STATUS"
done

for PID in "$PROJ1" "$PROJ2" "$PROJ3" "$PROJ4"; do
  BL_RESP=$(curl -sf --max-time 15 -X POST "$API/v1/projects/$PID/baselines" -H "$CT" -H "$AUTH" \
    -d '{"name":"Original Plan","baselineType":"PROJECT","description":"Original baseline plan"}' 2>/dev/null) || {
    echo "  WARN: Baseline request failed for $PID" >&2; continue;
  }
  BL_ID=$(echo "$BL_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id','FAILED'))" 2>/dev/null)
  echo "  Baseline $PID: $BL_ID"
done

echo ""
echo "=========================================="
echo " Restore complete!"
echo "=========================================="

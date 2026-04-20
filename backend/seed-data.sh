#!/bin/bash
# Bipros EPPM - Comprehensive Data Seeder
# Seeds realistic Government of Odisha infrastructure project data

set -euo pipefail

cleanup() { echo ""; echo "Seeder finished."; }
trap cleanup EXIT

# Configuration — override via environment variables
BASE_URL="${BIPROS_API_URL:-http://localhost:8080}"
BIPROS_USER="${BIPROS_USER:-admin}"
BIPROS_PASS="${BIPROS_PASS:-admin123}"

echo "=== Bipros EPPM Data Seeder ==="

# 1. Login and get token
echo "[1/15] Authenticating..."
TOKEN=$(curl -sf --max-time 10 -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$BIPROS_USER\",\"password\":\"$BIPROS_PASS\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])") || {
  echo "ERROR: Authentication request failed. Is the backend running at $BASE_URL?"
  exit 1
}

if [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to authenticate — empty token"
  exit 1
fi
echo "  Authenticated successfully"

AUTH="Authorization: Bearer $TOKEN"
CT="Content-Type: application/json"

# Helper: extract ID from JSON response (tolerant of errors)
extract_id_safe() {
  python3 -c "
import sys,json
try:
  d=json.load(sys.stdin); data=d.get('data',{})
  print(data.get('id','') if isinstance(data, dict) else '')
except: print('')
"
}

# 2. Create EPS Hierarchy
echo "[2/15] Creating EPS hierarchy..."
EPS_ROOT=$(curl -sf -X POST "$BASE_URL/v1/eps" -H "$AUTH" -H "$CT" -d '{
  "code": "GOO",
  "name": "Government of Odisha"
}' | extract_id_safe)
echo "  EPS Root: $EPS_ROOT"

EPS_INFRA=$(curl -sf -X POST "$BASE_URL/v1/eps" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"INFRA\",
  \"name\": \"Infrastructure Division\",
  \"parentId\": \"$EPS_ROOT\"
}" | extract_id_safe)
echo "  EPS Infra: $EPS_INFRA"

EPS_ROADS=$(curl -sf -X POST "$BASE_URL/v1/eps" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"ROADS\",
  \"name\": \"Roads & Highways\",
  \"parentId\": \"$EPS_INFRA\"
}" | extract_id_safe)
echo "  EPS Roads: $EPS_ROADS"

EPS_WATER=$(curl -sf -X POST "$BASE_URL/v1/eps" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"WATER\",
  \"name\": \"Water Resources\",
  \"parentId\": \"$EPS_INFRA\"
}" | extract_id_safe)
echo "  EPS Water: $EPS_WATER"

EPS_POWER=$(curl -sf -X POST "$BASE_URL/v1/eps" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"POWER\",
  \"name\": \"Power & Energy\",
  \"parentId\": \"$EPS_INFRA\"
}" | extract_id_safe)
echo "  EPS Power: $EPS_POWER"

# 3. Create OBS Hierarchy
echo "[3/15] Creating OBS hierarchy..."
OBS_ROOT=$(curl -sf -X POST "$BASE_URL/v1/obs" -H "$AUTH" -H "$CT" -d '{
  "code": "CE",
  "name": "Chief Engineer"
}' | extract_id_safe)
echo "  OBS Root: $OBS_ROOT"

OBS_SE=$(curl -sf -X POST "$BASE_URL/v1/obs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"SE-R1\",
  \"name\": \"Superintending Engineer - Roads Circle 1\",
  \"parentId\": \"$OBS_ROOT\"
}" | extract_id_safe)
echo "  OBS SE: $OBS_SE"

OBS_EE=$(curl -sf -X POST "$BASE_URL/v1/obs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"EE-R1D1\",
  \"name\": \"Executive Engineer - Roads Division 1\",
  \"parentId\": \"$OBS_SE\"
}" | extract_id_safe)
echo "  OBS EE: $OBS_EE"

OBS_SE_W=$(curl -sf -X POST "$BASE_URL/v1/obs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"SE-W1\",
  \"name\": \"Superintending Engineer - Water Circle 1\",
  \"parentId\": \"$OBS_ROOT\"
}" | extract_id_safe)
echo "  OBS SE Water: $OBS_SE_W"

# 4. Create Projects
echo "[4/15] Creating projects..."
PROJECT1=$(curl -sf -X POST "$BASE_URL/v1/projects" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"NH16-PKG3\",
  \"name\": \"NH-16 Bhubaneswar-Cuttack Expressway Package 3\",
  \"description\": \"Four-lane divided highway from Rasulgarh to Jagatpur including 2 flyovers, 3 ROBs, and service roads. Total length 18.5 km.\",
  \"epsNodeId\": \"$EPS_ROADS\",
  \"obsNodeId\": \"$OBS_EE\",
  \"plannedStartDate\": \"2026-01-15\",
  \"plannedFinishDate\": \"2028-06-30\",
  \"priority\": 1
}" | extract_id_safe)
echo "  Project 1 (NH16-PKG3): $PROJECT1"

PROJECT2=$(curl -sf -X POST "$BASE_URL/v1/projects" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"MBJK-DAM\",
  \"name\": \"Mahanadi Barrage Jokdia Dam Rehabilitation\",
  \"description\": \"Rehabilitation and modernization of Jokdia Dam spillway gates, upstream apron repair, and downstream energy dissipator reconstruction.\",
  \"epsNodeId\": \"$EPS_WATER\",
  \"obsNodeId\": \"$OBS_SE_W\",
  \"plannedStartDate\": \"2026-03-01\",
  \"plannedFinishDate\": \"2027-09-30\",
  \"priority\": 2
}" | extract_id_safe)
echo "  Project 2 (MBJK-DAM): $PROJECT2"

PROJECT3=$(curl -sf -X POST "$BASE_URL/v1/projects" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"SOLAR-BLS\",
  \"name\": \"Balasore Solar Power Park Phase-I\",
  \"description\": \"100 MW solar power park at Balasore including land development, solar panel installation, substation, and grid connectivity.\",
  \"epsNodeId\": \"$EPS_POWER\",
  \"obsNodeId\": \"$OBS_ROOT\",
  \"plannedStartDate\": \"2026-04-01\",
  \"plannedFinishDate\": \"2027-12-31\",
  \"priority\": 3
}" | extract_id_safe)
echo "  Project 3 (SOLAR-BLS): $PROJECT3"

# 5. Create WBS for Project 1 (NH-16 Expressway)
echo "[5/15] Creating WBS structure for NH16-PKG3..."
# Get calendar ID
CALENDAR_ID=$(curl -sf -H "$AUTH" "$BASE_URL/v1/calendars" | python3 -c "import sys,json; data=json.load(sys.stdin)['data']; print(data[0]['id'] if data else '')")
echo "  Calendar: $CALENDAR_ID"

WBS_DESIGN=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/wbs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"DES\",
  \"name\": \"Design & Engineering\",
  \"projectId\": \"$PROJECT1\"
}" | extract_id_safe)
echo "  WBS Design: $WBS_DESIGN"

WBS_PROCURE=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/wbs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"PROC\",
  \"name\": \"Procurement\",
  \"projectId\": \"$PROJECT1\"
}" | extract_id_safe)
echo "  WBS Procurement: $WBS_PROCURE"

WBS_ROAD=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/wbs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"ROAD\",
  \"name\": \"Road Construction\",
  \"projectId\": \"$PROJECT1\"
}" | extract_id_safe)
echo "  WBS Road: $WBS_ROAD"

WBS_ROAD_EARTH=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/wbs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"EARTH\",
  \"name\": \"Earthwork & Subgrade\",
  \"projectId\": \"$PROJECT1\",
  \"parentId\": \"$WBS_ROAD\"
}" | extract_id_safe)
echo "  WBS Earthwork: $WBS_ROAD_EARTH"

WBS_ROAD_PAVE=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/wbs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"PAVE\",
  \"name\": \"Pavement & Surface\",
  \"projectId\": \"$PROJECT1\",
  \"parentId\": \"$WBS_ROAD\"
}" | extract_id_safe)
echo "  WBS Pavement: $WBS_ROAD_PAVE"

WBS_STRUCT=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/wbs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"STRUCT\",
  \"name\": \"Structures (Flyovers & ROBs)\",
  \"projectId\": \"$PROJECT1\"
}" | extract_id_safe)
echo "  WBS Structures: $WBS_STRUCT"

WBS_QA=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/wbs" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"QA\",
  \"name\": \"Quality & Commissioning\",
  \"projectId\": \"$PROJECT1\"
}" | extract_id_safe)
echo "  WBS QA: $WBS_QA"

# 6. Create Activities for Project 1
echo "[6/15] Creating activities..."

# Design activities
A_SURVEY=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A1010\",
  \"name\": \"Topographical Survey & Soil Investigation\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_DESIGN\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 30,
  \"plannedStartDate\": \"2026-01-15\",
  \"plannedFinishDate\": \"2026-02-25\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Survey: $A_SURVEY"

A_DESIGN=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A1020\",
  \"name\": \"Geometric Design & DPR Preparation\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_DESIGN\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 45,
  \"plannedStartDate\": \"2026-02-26\",
  \"plannedFinishDate\": \"2026-04-28\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Design: $A_DESIGN"

A_APPROVAL=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A1030\",
  \"name\": \"Design Approval & NOC from Authorities\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_DESIGN\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 20,
  \"plannedStartDate\": \"2026-04-29\",
  \"plannedFinishDate\": \"2026-05-26\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Approval: $A_APPROVAL"

# Procurement
A_TENDER=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A2010\",
  \"name\": \"Tender Document Preparation & NIT\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_PROCURE\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 15,
  \"plannedStartDate\": \"2026-05-27\",
  \"plannedFinishDate\": \"2026-06-16\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Tender: $A_TENDER"

A_BIDEVAL=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A2020\",
  \"name\": \"Bid Evaluation & Contract Award\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_PROCURE\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 30,
  \"plannedStartDate\": \"2026-06-17\",
  \"plannedFinishDate\": \"2026-07-28\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity BidEval: $A_BIDEVAL"

# Mobilization milestone
A_MOBIL=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A2030\",
  \"name\": \"Contractor Mobilization Complete\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_PROCURE\",
  \"activityType\": \"FINISH_MILESTONE\",
  \"originalDuration\": 0,
  \"plannedStartDate\": \"2026-08-11\",
  \"plannedFinishDate\": \"2026-08-11\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Mobilization MS: $A_MOBIL"

# Earthwork activities
A_CLEARING=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A3010\",
  \"name\": \"Land Clearing & Tree Felling\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_ROAD_EARTH\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 40,
  \"plannedStartDate\": \"2026-08-12\",
  \"plannedFinishDate\": \"2026-10-06\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Clearing: $A_CLEARING"

A_EARTHWORK=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A3020\",
  \"name\": \"Embankment & Subgrade Formation\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_ROAD_EARTH\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 90,
  \"plannedStartDate\": \"2026-10-07\",
  \"plannedFinishDate\": \"2027-02-04\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Earthwork: $A_EARTHWORK"

A_DRAINAGE=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A3030\",
  \"name\": \"Cross Drainage & Culvert Construction\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_ROAD_EARTH\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 60,
  \"plannedStartDate\": \"2026-11-15\",
  \"plannedFinishDate\": \"2027-02-04\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Drainage: $A_DRAINAGE"

# Pavement activities
A_GSB=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A4010\",
  \"name\": \"GSB & WMM Layer Construction\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_ROAD_PAVE\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 75,
  \"plannedStartDate\": \"2027-02-05\",
  \"plannedFinishDate\": \"2027-05-14\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity GSB: $A_GSB"

A_DBM=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A4020\",
  \"name\": \"DBM & Bituminous Concrete Laying\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_ROAD_PAVE\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 60,
  \"plannedStartDate\": \"2027-05-15\",
  \"plannedFinishDate\": \"2027-08-04\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity DBM: $A_DBM"

# Structures
A_FLYOVER=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A5010\",
  \"name\": \"Flyover-1 Pile Foundation & Pier Construction\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_STRUCT\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 120,
  \"plannedStartDate\": \"2026-10-07\",
  \"plannedFinishDate\": \"2027-03-18\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Flyover: $A_FLYOVER"

A_FLYOVER_DECK=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A5020\",
  \"name\": \"Flyover-1 Superstructure & Deck Slab\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_STRUCT\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 90,
  \"plannedStartDate\": \"2027-03-19\",
  \"plannedFinishDate\": \"2027-07-22\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Flyover Deck: $A_FLYOVER_DECK"

A_ROB=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A5030\",
  \"name\": \"ROB-1 Construction at Railway Crossing\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_STRUCT\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 150,
  \"plannedStartDate\": \"2026-10-07\",
  \"plannedFinishDate\": \"2027-05-01\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity ROB: $A_ROB"

# QA & Commissioning
A_MARKING=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A6010\",
  \"name\": \"Road Marking, Signage & Safety Furniture\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_QA\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 30,
  \"plannedStartDate\": \"2027-08-05\",
  \"plannedFinishDate\": \"2027-09-15\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Marking: $A_MARKING"

A_TESTING=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A6020\",
  \"name\": \"Quality Testing & Load Testing of Structures\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_QA\",
  \"activityType\": \"TASK_DEPENDENT\",
  \"originalDuration\": 20,
  \"plannedStartDate\": \"2027-09-16\",
  \"plannedFinishDate\": \"2027-10-13\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Testing: $A_TESTING"

A_HANDOVER=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/activities" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"A6030\",
  \"name\": \"Project Handover to NHAI\",
  \"projectId\": \"$PROJECT1\",
  \"wbsNodeId\": \"$WBS_QA\",
  \"activityType\": \"FINISH_MILESTONE\",
  \"originalDuration\": 0,
  \"plannedStartDate\": \"2027-10-14\",
  \"plannedFinishDate\": \"2027-10-14\",
  \"calendarId\": \"$CALENDAR_ID\"
}" | extract_id_safe)
echo "  Activity Handover MS: $A_HANDOVER"

# 7. Create Relationships (predecessor-successor)
echo "[7/15] Creating activity relationships..."

create_rel() {
  local pred="$1" succ="$2" rtype="${3:-FINISH_TO_START}" lag="${4:-0}" pn="$5" sn="$6" proj="${7:-$PROJECT1}"
  [ -z "$pred" ] || [ -z "$succ" ] && { echo "  WARN: Skipping rel — missing ID ($pn -> $sn)" >&2; return; }
  curl -sf --max-time 10 -X POST "$BASE_URL/v1/projects/$proj/relationships" -H "$AUTH" -H "$CT" \
    -d "{\"predecessorActivityId\":\"$pred\",\"successorActivityId\":\"$succ\",\"relationshipType\":\"$rtype\",\"lag\":$lag}" \
    > /dev/null 2>&1 && echo "  $rtype: $pn -> $sn" || echo "  WARN: Failed $pn -> $sn" >&2
}

create_rel "$A_SURVEY" "$A_DESIGN" "FINISH_TO_START" 0 "Survey" "Design"
create_rel "$A_DESIGN" "$A_APPROVAL" "FINISH_TO_START" 0 "Design" "Approval"
create_rel "$A_APPROVAL" "$A_TENDER" "FINISH_TO_START" 0 "Approval" "Tender"
create_rel "$A_TENDER" "$A_BIDEVAL" "FINISH_TO_START" 0 "Tender" "BidEval"
create_rel "$A_BIDEVAL" "$A_MOBIL" "FINISH_TO_START" 10 "BidEval" "Mobilization"
create_rel "$A_MOBIL" "$A_CLEARING" "FINISH_TO_START" 0 "Mobilization" "Clearing"
create_rel "$A_CLEARING" "$A_EARTHWORK" "FINISH_TO_START" 0 "Clearing" "Earthwork"
create_rel "$A_CLEARING" "$A_FLYOVER" "FINISH_TO_START" 0 "Clearing" "Flyover"
create_rel "$A_CLEARING" "$A_ROB" "FINISH_TO_START" 0 "Clearing" "ROB"
create_rel "$A_EARTHWORK" "$A_DRAINAGE" "START_TO_START" 30 "Earthwork" "Drainage"
create_rel "$A_EARTHWORK" "$A_GSB" "FINISH_TO_START" 0 "Earthwork" "GSB"
create_rel "$A_GSB" "$A_DBM" "FINISH_TO_START" 0 "GSB" "DBM"
create_rel "$A_FLYOVER" "$A_FLYOVER_DECK" "FINISH_TO_START" 0 "Flyover" "FlyoverDeck"
create_rel "$A_DBM" "$A_MARKING" "FINISH_TO_START" 0 "DBM" "Marking"
create_rel "$A_FLYOVER_DECK" "$A_MARKING" "FINISH_TO_START" 0 "FlyoverDeck" "Marking"
create_rel "$A_ROB" "$A_MARKING" "FINISH_TO_START" 0 "ROB" "Marking"
create_rel "$A_MARKING" "$A_TESTING" "FINISH_TO_START" 0 "Marking" "Testing"
create_rel "$A_TESTING" "$A_HANDOVER" "FINISH_TO_START" 0 "Testing" "Handover"

# 8. Create Resources
echo "[8/15] Creating resources..."

RES_PM=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"PM-001\",
  \"name\": \"Project Manager - Senior\",
  \"resourceType\": \"LABOR\",
  \"email\": \"pm@bipros.local\",
  \"title\": \"Senior Project Manager\",
  \"maxUnitsPerDay\": 8.0
}" | extract_id_safe)
echo "  Resource PM: $RES_PM"

RES_CIVIL=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"CE-001\",
  \"name\": \"Civil Engineer\",
  \"resourceType\": \"LABOR\",
  \"email\": \"civil@bipros.local\",
  \"title\": \"Civil Engineer\",
  \"maxUnitsPerDay\": 8.0
}" | extract_id_safe)
echo "  Resource Civil: $RES_CIVIL"

RES_STRUCT_ENG=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"SE-001\",
  \"name\": \"Structural Engineer\",
  \"resourceType\": \"LABOR\",
  \"email\": \"struct@bipros.local\",
  \"title\": \"Structural Engineer\",
  \"maxUnitsPerDay\": 8.0
}" | extract_id_safe)
echo "  Resource Struct Eng: $RES_STRUCT_ENG"

RES_SURVEYOR=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"SUR-001\",
  \"name\": \"Land Surveyor\",
  \"resourceType\": \"LABOR\",
  \"email\": \"survey@bipros.local\",
  \"title\": \"Senior Surveyor\",
  \"maxUnitsPerDay\": 8.0
}" | extract_id_safe)
echo "  Resource Surveyor: $RES_SURVEYOR"

RES_EXCAVATOR=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" \
  -d '{"code":"EQ-001","name":"Hydraulic Excavator JCB 220","resourceType":"NONLABOR","maxUnitsPerDay":10.0}' | extract_id_safe)
RES_PAVER=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" \
  -d '{"code":"EQ-002","name":"Asphalt Paver Volvo P6820D","resourceType":"NONLABOR","maxUnitsPerDay":10.0}' | extract_id_safe)
echo "  Equipment: Excavator=$RES_EXCAVATOR, Paver=$RES_PAVER"

RES_CEMENT=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" \
  -d '{"code":"MAT-001","name":"OPC Cement Grade 53","resourceType":"MATERIAL"}' | extract_id_safe)
RES_STEEL=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" \
  -d '{"code":"MAT-002","name":"TMT Steel Bars Fe-500D","resourceType":"MATERIAL"}' | extract_id_safe)
RES_BITUMEN=$(curl -sf -X POST "$BASE_URL/v1/resources" -H "$AUTH" -H "$CT" \
  -d '{"code":"MAT-003","name":"VG-30 Bitumen","resourceType":"MATERIAL"}' | extract_id_safe)
echo "  Materials: Cement=$RES_CEMENT, Steel=$RES_STEEL, Bitumen=$RES_BITUMEN"

# 9. Create Resource Assignments
echo "[9/15] Creating resource assignments..."

assign_resource() {
  curl -sf --max-time 10 -X POST "$BASE_URL/v1/projects/$PROJECT1/resource-assignments" -H "$AUTH" -H "$CT" \
    -d "{\"activityId\":\"$1\",\"resourceId\":\"$2\",\"projectId\":\"$PROJECT1\",\"plannedUnits\":$3}" \
    > /dev/null 2>&1 && echo "  Assigned $4 to $5" || echo "  WARN: Failed $4 -> $5" >&2
}

assign_resource "$A_SURVEY" "$RES_SURVEYOR" 240 "Surveyor" "Survey"
assign_resource "$A_SURVEY" "$RES_PM" 60 "PM" "Survey"
assign_resource "$A_DESIGN" "$RES_CIVIL" 360 "CivilEng" "Design"
assign_resource "$A_DESIGN" "$RES_STRUCT_ENG" 180 "StructEng" "Design"
assign_resource "$A_CLEARING" "$RES_EXCAVATOR" 320 "Excavator" "Clearing"
assign_resource "$A_EARTHWORK" "$RES_EXCAVATOR" 720 "Excavator" "Earthwork"
assign_resource "$A_EARTHWORK" "$RES_CEMENT" 5000 "Cement" "Earthwork"
assign_resource "$A_GSB" "$RES_CIVIL" 600 "CivilEng" "GSB"
assign_resource "$A_DBM" "$RES_PAVER" 480 "Paver" "DBM"
assign_resource "$A_DBM" "$RES_BITUMEN" 3000 "Bitumen" "DBM"
assign_resource "$A_FLYOVER" "$RES_STRUCT_ENG" 960 "StructEng" "Flyover"
assign_resource "$A_FLYOVER" "$RES_STEEL" 15000 "Steel" "Flyover"
assign_resource "$A_FLYOVER" "$RES_CEMENT" 8000 "Cement" "Flyover"
assign_resource "$A_FLYOVER_DECK" "$RES_STRUCT_ENG" 720 "StructEng" "FlyoverDeck"
assign_resource "$A_ROB" "$RES_STRUCT_ENG" 1200 "StructEng" "ROB"
assign_resource "$A_ROB" "$RES_STEEL" 20000 "Steel" "ROB"

# 10. Create Cost Accounts & Expenses
echo "[10/15] Creating cost accounts and expenses..."

CA_LABOR=$(curl -sf -X POST "$BASE_URL/v1/cost-accounts" -H "$AUTH" -H "$CT" -d '{
  "code": "CA-LAB",
  "name": "Labor Costs",
  "description": "All labor-related costs"
}' | extract_id_safe)
echo "  Cost Account Labor: $CA_LABOR"

CA_MATERIAL=$(curl -sf -X POST "$BASE_URL/v1/cost-accounts" -H "$AUTH" -H "$CT" -d '{
  "code": "CA-MAT",
  "name": "Material Costs",
  "description": "All material procurement costs"
}' | extract_id_safe)
echo "  Cost Account Material: $CA_MATERIAL"

CA_EQUIPMENT=$(curl -sf -X POST "$BASE_URL/v1/cost-accounts" -H "$AUTH" -H "$CT" -d '{
  "code": "CA-EQP",
  "name": "Equipment Costs",
  "description": "Equipment hire and maintenance costs"
}' | extract_id_safe)
echo "  Cost Account Equipment: $CA_EQUIPMENT"

create_expense() {
  curl -sf --max-time 10 -X POST "$BASE_URL/v1/projects/$PROJECT1/expenses" -H "$AUTH" -H "$CT" \
    -d "{\"activityId\":\"$1\",\"projectId\":\"$PROJECT1\",\"costAccountId\":\"$2\",\"name\":\"$3\",\"budgetedCost\":$4,\"actualCost\":${5:-0},\"remainingCost\":${6:-$4}}" \
    > /dev/null 2>&1 && echo "  Expense: $3" || echo "  WARN: Failed expense $3" >&2
}

create_expense "$A_SURVEY" "$CA_LABOR" "Survey Team Wages" 1500000 0 1500000
create_expense "$A_DESIGN" "$CA_LABOR" "Design Consultancy Fee" 3500000 0 3500000
create_expense "$A_CLEARING" "$CA_EQUIPMENT" "Land Clearing Equipment Hire" 2000000 0 2000000
create_expense "$A_EARTHWORK" "$CA_MATERIAL" "Earth & Fill Material" 15000000 0 15000000
create_expense "$A_EARTHWORK" "$CA_EQUIPMENT" "Earthwork Equipment" 8000000 0 8000000
create_expense "$A_GSB" "$CA_MATERIAL" "Aggregates for GSB & WMM" 12000000 0 12000000
create_expense "$A_DBM" "$CA_MATERIAL" "Bitumen & Aggregates for BC" 18000000 0 18000000
create_expense "$A_FLYOVER" "$CA_MATERIAL" "RCC Materials for Flyover-1" 25000000 0 25000000
create_expense "$A_FLYOVER" "$CA_LABOR" "Flyover Construction Labor" 8000000 0 8000000
create_expense "$A_ROB" "$CA_MATERIAL" "ROB Construction Materials" 30000000 0 30000000
create_expense "$A_ROB" "$CA_LABOR" "ROB Construction Labor" 10000000 0 10000000
create_expense "$A_MARKING" "$CA_MATERIAL" "Road Marking & Signage Materials" 3000000 0 3000000

# 11. Create Funding Source
echo "[11/15] Creating funding sources..."

FUND_CENTRAL=$(curl -sf -X POST "$BASE_URL/v1/funding-sources" -H "$AUTH" -H "$CT" -d '{
  "name": "Central Road Fund (CRF)",
  "description": "Ministry of Road Transport & Highways funding under Bharatmala Pariyojana",
  "code": "CRF-BMP",
  "totalAmount": 100000000,
  "allocatedAmount": 0,
  "remainingAmount": 100000000
}' | extract_id_safe)
echo "  Funding CRF: $FUND_CENTRAL"

FUND_STATE=$(curl -sf -X POST "$BASE_URL/v1/funding-sources" -H "$AUTH" -H "$CT" -d '{
  "name": "State Plan Fund (SPF)",
  "description": "Government of Odisha Works Department Budget",
  "code": "SPF-OD",
  "totalAmount": 50000000,
  "allocatedAmount": 0,
  "remainingAmount": 50000000
}' | extract_id_safe)
echo "  Funding SPF: $FUND_STATE"

curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/funding" -H "$AUTH" -H "$CT" \
  -d "{\"projectId\":\"$PROJECT1\",\"fundingSourceId\":\"$FUND_CENTRAL\",\"allocatedAmount\":80000000}" > /dev/null 2>&1 && echo "  Assigned CRF to NH16-PKG3"
curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/funding" -H "$AUTH" -H "$CT" \
  -d "{\"projectId\":\"$PROJECT1\",\"fundingSourceId\":\"$FUND_STATE\",\"allocatedAmount\":40000000}" > /dev/null 2>&1 && echo "  Assigned SPF to NH16-PKG3"

# 12. Schedule the project
echo "[12/15] Scheduling project (CPM)..."
SCHEDULE_RESULT=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/schedule" -H "$AUTH" -H "$CT" -d "{
  \"projectId\": \"$PROJECT1\",
  \"option\": \"RETAINED_LOGIC\"
}")
echo "  Schedule result: $(echo "$SCHEDULE_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); data=d.get('data',{}); print(f'Activities: {data.get(\"totalActivities\",\"?\")}, Critical: {data.get(\"criticalActivities\",\"?\")}')" 2>/dev/null || echo "completed")"

# 13. Create Baseline
echo "[13/15] Creating project baseline..."
BASELINE=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/baselines" -H "$AUTH" -H "$CT" -d '{
  "name": "Original Baseline",
  "baselineType": "PROJECT",
  "description": "Initial project baseline capturing approved schedule and budget"
}' | extract_id_safe)
echo "  Baseline: $BASELINE"

# 14. Create Risks
echo "[14/15] Creating risks..."

RISK1=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/risks" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"R001\",
  \"title\": \"Monsoon Delay - Earthwork Season\",
  \"description\": \"Heavy monsoon rainfall June-September may halt earthwork operations and delay embankment construction by 4-6 weeks\",
  \"category\": \"MONSOON_IMPACT\",
  \"probability\": \"HIGH\",
  \"impact\": \"HIGH\",
  \"costImpact\": 5000000,
  \"scheduleImpactDays\": 30
}" | extract_id_safe)
echo "  Risk Monsoon: $RISK1"

RISK2=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/risks" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"R002\",
  \"title\": \"Land Acquisition Dispute at Km 12+500\",
  \"description\": \"Pending land acquisition for 3 plots at Km 12+500 may delay ROB approach road construction\",
  \"category\": \"LAND_ACQUISITION\",
  \"probability\": \"MEDIUM\",
  \"impact\": \"VERY_HIGH\",
  \"costImpact\": 8000000,
  \"scheduleImpactDays\": 60
}" | extract_id_safe)
echo "  Risk Land: $RISK2"

RISK3=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/risks" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"R003\",
  \"title\": \"Steel Price Escalation\",
  \"description\": \"Global steel prices may escalate 15-20% impacting structural steel costs for flyovers and ROBs\",
  \"category\": \"MARKET_PRICE\",
  \"probability\": \"MEDIUM\",
  \"impact\": \"HIGH\",
  \"costImpact\": 12000000,
  \"scheduleImpactDays\": 0
}" | extract_id_safe)
echo "  Risk Steel: $RISK3"

RISK4=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/risks" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"R004\",
  \"title\": \"Railway Block Availability for ROB\",
  \"description\": \"Limited traffic blocks from East Coast Railway may restrict ROB girder launching to night windows only\",
  \"category\": \"EXTERNAL\",
  \"probability\": \"HIGH\",
  \"impact\": \"MEDIUM\",
  \"costImpact\": 3000000,
  \"scheduleImpactDays\": 45
}" | extract_id_safe)
echo "  Risk Railway: $RISK4"

RISK5=$(curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/risks" -H "$AUTH" -H "$CT" -d "{
  \"code\": \"R005\",
  \"title\": \"Forest Clearance NOC Delay\",
  \"description\": \"Forest clearance for 2.3 hectares pending Stage-II approval from MoEFCC\",
  \"category\": \"FOREST_CLEARANCE\",
  \"probability\": \"LOW\",
  \"impact\": \"VERY_HIGH\",
  \"costImpact\": 2000000,
  \"scheduleImpactDays\": 90
}" | extract_id_safe)
echo "  Risk Forest: $RISK5"

# Add risk responses
if [ -n "$RISK1" ]; then
  curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/risks/$RISK1/responses" -H "$AUTH" -H "$CT" -d '{
    "responseType": "MITIGATE",
    "description": "Pre-monsoon acceleration of earthwork by deploying additional equipment fleet. Target 80% earthwork completion before June onset.",
    "estimatedCost": 2000000,
    "status": "PLANNED"
  }' > /dev/null 2>&1 && echo "  Response added for Monsoon risk"
fi

if [ -n "$RISK2" ]; then
  curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/risks/$RISK2/responses" -H "$AUTH" -H "$CT" -d '{
    "responseType": "AVOID",
    "description": "Initiate parallel construction on available land while pursuing LA through District Collector. Redesign approach road alignment if LA fails by Aug 2026.",
    "estimatedCost": 1500000,
    "status": "PLANNED"
  }' > /dev/null 2>&1 && echo "  Response added for Land risk"
fi

# 15. Create Portfolio
echo "[15/15] Creating portfolio..."
PORTFOLIO=$(curl -sf -X POST "$BASE_URL/v1/portfolios" -H "$AUTH" -H "$CT" -d '{
  "name": "Odisha Infrastructure Priority Portfolio FY26-27",
  "description": "Priority infrastructure projects under Odisha Works Department for FY 2026-27"
}' | extract_id_safe)
echo "  Portfolio: $PORTFOLIO"

if [ -n "$PORTFOLIO" ]; then
  for PDATA in "$PROJECT1:NH16-PKG3" "$PROJECT2:MBJK-DAM" "$PROJECT3:SOLAR-BLS"; do
    PID="${PDATA%%:*}"; PNAME="${PDATA##*:}"
    curl -sf -X POST "$BASE_URL/v1/portfolios/$PORTFOLIO/projects" -H "$AUTH" -H "$CT" \
      -d "{\"projectId\":\"$PID\"}" > /dev/null 2>&1 && echo "  Added $PNAME to portfolio"
  done
fi

echo "[BONUS] Creating financial periods..."
SORT=0
for QTR in "Q1 FY26-27:2026-04-01:2026-06-30" "Q2 FY26-27:2026-07-01:2026-09-30" \
           "Q3 FY26-27:2026-10-01:2026-12-31" "Q4 FY26-27:2027-01-01:2027-03-31"; do
  SORT=$((SORT + 1)); QN="${QTR%%:*}"; REST="${QTR#*:}"; QS="${REST%%:*}"; QE="${REST##*:}"
  FP=$(curl -sf -X POST "$BASE_URL/v1/financial-periods" -H "$AUTH" -H "$CT" \
    -d "{\"name\":\"$QN\",\"startDate\":\"$QS\",\"endDate\":\"$QE\",\"periodType\":\"QUARTERLY\",\"sortOrder\":$SORT}" | extract_id_safe)
  echo "  Period $QN: $FP"
done

# EVM Calculation
echo "[BONUS] Running EVM calculation..."
curl -sf -X POST "$BASE_URL/v1/projects/$PROJECT1/evm/calculate" -H "$AUTH" -H "$CT" -d '{
  "technique": "ACTIVITY_PERCENT_COMPLETE",
  "etcMethod": "CPI_BASED"
}' > /dev/null 2>&1 && echo "  EVM calculated for NH16-PKG3" || echo "  WARN: EVM calculation may need data"

cat <<'SUMMARY'

=========================================
  DATA SEEDING COMPLETE!
=========================================

Summary:
  - 5 EPS nodes (GOO > INFRA > ROADS/WATER/POWER)
  - 4 OBS nodes (CE > SE > EE)
  - 3 Projects (NH16 Expressway, Dam Rehab, Solar Park)
  - 7 WBS nodes for NH16-PKG3
  - 18 Activities with dependencies
  - 18 Predecessor/Successor relationships
  - 9 Resources (4 Labor, 2 Equipment, 3 Material)
  - 16 Resource assignments
  - 3 Cost accounts, 12 Activity expenses
  - 2 Funding sources with project allocation
  - CPM Schedule computed, 1 Project baseline
  - 5 Risks with 2 risk responses
  - 1 Portfolio with 3 projects
  - 4 Financial periods (FY26-27), EVM calculation
SUMMARY
echo "API: $BASE_URL/v1/"

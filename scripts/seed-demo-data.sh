#!/bin/bash
# =============================================================================
# Bipros EPPM — Comprehensive Demo Data Seeder
# Creates realistic construction/engineering project data for demonstration
# =============================================================================

set -e

API="http://localhost:8080"
CT="Content-Type: application/json"

echo "=========================================="
echo " Bipros EPPM — Demo Data Seeder"
echo "=========================================="

# --- Login ---
echo ""
echo "[1/12] Logging in as admin..."
LOGIN_RESP=$(curl -s -X POST "$API/v1/auth/login" -H "$CT" \
  -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"

if [ -z "$TOKEN" ]; then
  echo "ERROR: Login failed. Is the backend running?"
  exit 1
fi
echo "  Login successful."

# Helper function to POST and extract ID
post_and_get_id() {
  local url=$1
  local data=$2
  # Auto-inject projectId for WBS and activity endpoints
  if echo "$url" | grep -qE '/v1/projects/[^/]+/(wbs|activities)$'; then
    local proj_id=$(echo "$url" | sed -E 's|/v1/projects/([^/]+)/.*|\1|')
    data=$(echo "$data" | sed "s/^{/{\"projectId\":\"$proj_id\",/")
  fi
  local resp=$(curl -s -X POST "$API$url" -H "$CT" -H "$AUTH" -d "$data")
  local id=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null)
  if [ -z "$id" ]; then
    echo "  WARN: Failed to create at $url" >&2
    echo "$resp" | python3 -c "import sys,json; print(json.dumps(json.load(sys.stdin),indent=2))" 2>/dev/null >&2
    echo ""
  else
    echo "$id"
  fi
}

# ============================================================
# 2. EPS Hierarchy (Enterprise Project Structure)
# ============================================================
echo ""
echo "[2/12] Creating EPS hierarchy..."

EPS_ROOT=$(post_and_get_id "/v1/eps" '{"code":"BIPROS","name":"Bipros Corporation","parentId":null}')
echo "  Root: Bipros Corporation ($EPS_ROOT)"

EPS_INFRA=$(post_and_get_id "/v1/eps" "{\"code\":\"INFRA\",\"name\":\"Infrastructure Division\",\"parentId\":\"$EPS_ROOT\"}")
echo "  L1: Infrastructure Division ($EPS_INFRA)"

EPS_IT=$(post_and_get_id "/v1/eps" "{\"code\":\"IT\",\"name\":\"Information Technology\",\"parentId\":\"$EPS_ROOT\"}")
echo "  L1: Information Technology ($EPS_IT)"

EPS_ENERGY=$(post_and_get_id "/v1/eps" "{\"code\":\"ENERGY\",\"name\":\"Energy & Utilities\",\"parentId\":\"$EPS_ROOT\"}")
echo "  L1: Energy & Utilities ($EPS_ENERGY)"

EPS_ROADS=$(post_and_get_id "/v1/eps" "{\"code\":\"ROADS\",\"name\":\"Roads & Bridges\",\"parentId\":\"$EPS_INFRA\"}")
echo "  L2: Roads & Bridges ($EPS_ROADS)"

EPS_BUILDINGS=$(post_and_get_id "/v1/eps" "{\"code\":\"BLDG\",\"name\":\"Buildings & Facilities\",\"parentId\":\"$EPS_INFRA\"}")
echo "  L2: Buildings & Facilities ($EPS_BUILDINGS)"

EPS_SOFTWARE=$(post_and_get_id "/v1/eps" "{\"code\":\"SW\",\"name\":\"Software Development\",\"parentId\":\"$EPS_IT\"}")
echo "  L2: Software Development ($EPS_SOFTWARE)"

EPS_SOLAR=$(post_and_get_id "/v1/eps" "{\"code\":\"SOLAR\",\"name\":\"Solar Projects\",\"parentId\":\"$EPS_ENERGY\"}")
echo "  L2: Solar Projects ($EPS_SOLAR)"

# ============================================================
# 3. OBS Hierarchy (Organization Breakdown Structure)
# ============================================================
echo ""
echo "[3/12] Creating OBS hierarchy..."

OBS_CEO=$(post_and_get_id "/v1/obs" '{"code":"CEO","name":"Chief Executive Officer","description":"Executive leadership","parentId":null}')
echo "  Root: CEO ($OBS_CEO)"

OBS_VP_ENG=$(post_and_get_id "/v1/obs" "{\"code\":\"VP-ENG\",\"name\":\"VP Engineering\",\"description\":\"Engineering division head\",\"parentId\":\"$OBS_CEO\"}")
echo "  L1: VP Engineering ($OBS_VP_ENG)"

OBS_VP_IT=$(post_and_get_id "/v1/obs" "{\"code\":\"VP-IT\",\"name\":\"VP Information Technology\",\"description\":\"IT division head\",\"parentId\":\"$OBS_CEO\"}")
echo "  L1: VP IT ($OBS_VP_IT)"

OBS_PM_CIVIL=$(post_and_get_id "/v1/obs" "{\"code\":\"PM-CIVIL\",\"name\":\"Civil Engineering PM\",\"description\":\"Civil project management\",\"parentId\":\"$OBS_VP_ENG\"}")
echo "  L2: Civil Engineering PM ($OBS_PM_CIVIL)"

OBS_PM_STRUCT=$(post_and_get_id "/v1/obs" "{\"code\":\"PM-STRUCT\",\"name\":\"Structural Engineering PM\",\"description\":\"Structural project management\",\"parentId\":\"$OBS_VP_ENG\"}")
echo "  L2: Structural Engineering PM ($OBS_PM_STRUCT)"

# ============================================================
# 4. Resources (Labor, Non-Labor, Material)
# ============================================================
echo ""
echo "[4/12] Creating resources..."

# Labor resources
RES_PM=$(post_and_get_id "/v1/resources" '{"code":"PM01","name":"Project Manager","resourceType":"LABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  Labor: Project Manager ($RES_PM)"

RES_CIVIL=$(post_and_get_id "/v1/resources" '{"code":"CE01","name":"Civil Engineer","resourceType":"LABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  Labor: Civil Engineer ($RES_CIVIL)"

RES_STRUCT=$(post_and_get_id "/v1/resources" '{"code":"SE01","name":"Structural Engineer","resourceType":"LABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  Labor: Structural Engineer ($RES_STRUCT)"

RES_ELEC=$(post_and_get_id "/v1/resources" '{"code":"EE01","name":"Electrical Engineer","resourceType":"LABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  Labor: Electrical Engineer ($RES_ELEC)"

RES_FOREMAN=$(post_and_get_id "/v1/resources" '{"code":"FM01","name":"Construction Foreman","resourceType":"LABOR","maxUnitsPerDay":10.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  Labor: Construction Foreman ($RES_FOREMAN)"

RES_WORKER=$(post_and_get_id "/v1/resources" '{"code":"LB01","name":"General Laborer","resourceType":"LABOR","maxUnitsPerDay":10.0,"defaultUnitsPerTime":4.0,"status":"ACTIVE"}')
echo "  Labor: General Laborer ($RES_WORKER)"

RES_WELDER=$(post_and_get_id "/v1/resources" '{"code":"WD01","name":"Certified Welder","resourceType":"LABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":2.0,"status":"ACTIVE"}')
echo "  Labor: Certified Welder ($RES_WELDER)"

RES_SURVEYOR=$(post_and_get_id "/v1/resources" '{"code":"SV01","name":"Land Surveyor","resourceType":"LABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  Labor: Land Surveyor ($RES_SURVEYOR)"

RES_DEV=$(post_and_get_id "/v1/resources" '{"code":"DEV01","name":"Software Developer","resourceType":"LABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":3.0,"status":"ACTIVE"}')
echo "  Labor: Software Developer ($RES_DEV)"

RES_QA=$(post_and_get_id "/v1/resources" '{"code":"QA01","name":"QA Engineer","resourceType":"LABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":2.0,"status":"ACTIVE"}')
echo "  Labor: QA Engineer ($RES_QA)"

# Non-Labor resources
RES_CRANE=$(post_and_get_id "/v1/resources" '{"code":"CR01","name":"Tower Crane 50T","resourceType":"NONLABOR","maxUnitsPerDay":10.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  NonLabor: Tower Crane ($RES_CRANE)"

RES_EXCAVATOR=$(post_and_get_id "/v1/resources" '{"code":"EX01","name":"Hydraulic Excavator","resourceType":"NONLABOR","maxUnitsPerDay":10.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  NonLabor: Excavator ($RES_EXCAVATOR)"

RES_BULLDOZER=$(post_and_get_id "/v1/resources" '{"code":"BD01","name":"D6 Bulldozer","resourceType":"NONLABOR","maxUnitsPerDay":10.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  NonLabor: Bulldozer ($RES_BULLDOZER)"

RES_PUMP=$(post_and_get_id "/v1/resources" '{"code":"PU01","name":"Concrete Pump Truck","resourceType":"NONLABOR","maxUnitsPerDay":8.0,"defaultUnitsPerTime":1.0,"status":"ACTIVE"}')
echo "  NonLabor: Concrete Pump ($RES_PUMP)"

# Material resources
RES_CONCRETE=$(post_and_get_id "/v1/resources" '{"code":"MAT-CON","name":"Ready-Mix Concrete (m3)","resourceType":"MATERIAL","maxUnitsPerDay":100.0,"defaultUnitsPerTime":10.0,"status":"ACTIVE"}')
echo "  Material: Concrete ($RES_CONCRETE)"

RES_STEEL=$(post_and_get_id "/v1/resources" '{"code":"MAT-STL","name":"Structural Steel (tons)","resourceType":"MATERIAL","maxUnitsPerDay":50.0,"defaultUnitsPerTime":5.0,"status":"ACTIVE"}')
echo "  Material: Steel ($RES_STEEL)"

RES_REBAR=$(post_and_get_id "/v1/resources" '{"code":"MAT-REB","name":"Rebar Grade 60 (tons)","resourceType":"MATERIAL","maxUnitsPerDay":30.0,"defaultUnitsPerTime":2.0,"status":"ACTIVE"}')
echo "  Material: Rebar ($RES_REBAR)"

RES_LUMBER=$(post_and_get_id "/v1/resources" '{"code":"MAT-LUM","name":"Formwork Lumber (bf)","resourceType":"MATERIAL","maxUnitsPerDay":500.0,"defaultUnitsPerTime":50.0,"status":"ACTIVE"}')
echo "  Material: Lumber ($RES_LUMBER)"

# ============================================================
# 5. PROJECT 1: Highway Bridge Reconstruction (Major Construction)
# ============================================================
echo ""
echo "[5/12] Creating Project 1: Metro River Bridge..."

PROJ1=$(post_and_get_id "/v1/projects" "{
  \"code\":\"MRB-2026\",
  \"name\":\"Metro River Bridge Reconstruction\",
  \"description\":\"Complete reconstruction of the Metro River Bridge including demolition of existing structure, new foundation piles, superstructure erection, and approach road realignment. Total budget: \$45M.\",
  \"epsNodeId\":\"$EPS_ROADS\",
  \"obsNodeId\":\"$OBS_PM_CIVIL\",
  \"plannedStartDate\":\"2026-06-01\",
  \"plannedFinishDate\":\"2028-06-30\",
  \"status\":\"ACTIVE\",
  \"priority\":1
}")
echo "  Project: Metro River Bridge ($PROJ1)"

# WBS for Project 1
echo "  Creating WBS..."
WBS1_ROOT=$(post_and_get_id "/v1/projects/$PROJ1/wbs" '{"code":"MRB","name":"Metro River Bridge","parentId":null}')
WBS1_PRECN=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.1\",\"name\":\"Pre-Construction\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_DEMO=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.2\",\"name\":\"Demolition\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_FOUND=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.3\",\"name\":\"Foundation\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_SUPER=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.4\",\"name\":\"Superstructure\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_DECK=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.5\",\"name\":\"Deck & Finishing\",\"parentId\":\"$WBS1_ROOT\"}")
WBS1_CLOSE=$(post_and_get_id "/v1/projects/$PROJ1/wbs" "{\"code\":\"MRB.6\",\"name\":\"Closeout\",\"parentId\":\"$WBS1_ROOT\"}")
echo "  WBS: 6 nodes created"

# Activities for Project 1
echo "  Creating activities..."

# Pre-Construction Phase
A1010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A1010\",\"name\":\"Project Kickoff\",\"activityType\":\"START_MILESTONE\",
  \"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":0,\"plannedStartDate\":\"2026-06-01\",\"plannedFinishDate\":\"2026-06-01\"
}")
A1020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A1020\",\"name\":\"Site Survey & Geotechnical Investigation\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":15,\"plannedStartDate\":\"2026-06-02\",\"plannedFinishDate\":\"2026-06-22\"
}")
A1030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A1030\",\"name\":\"Environmental Impact Assessment\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":20,\"plannedStartDate\":\"2026-06-02\",\"plannedFinishDate\":\"2026-06-29\"
}")
A1040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A1040\",\"name\":\"Detailed Engineering Design\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":30,\"plannedStartDate\":\"2026-06-23\",\"plannedFinishDate\":\"2026-08-03\"
}")
A1050=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A1050\",\"name\":\"Obtain Permits & Regulatory Approval\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":25,\"plannedStartDate\":\"2026-06-30\",\"plannedFinishDate\":\"2026-08-03\"
}")
A1060=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A1060\",\"name\":\"Mobilize Equipment & Setup Staging Area\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_PRECN\",\"originalDuration\":10,\"plannedStartDate\":\"2026-08-04\",\"plannedFinishDate\":\"2026-08-17\"
}")

# Demolition Phase
A2010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A2010\",\"name\":\"Traffic Diversion & Road Closure\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":5,\"plannedStartDate\":\"2026-08-18\",\"plannedFinishDate\":\"2026-08-24\"
}")
A2020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A2020\",\"name\":\"Remove Bridge Deck & Railing\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":20,\"plannedStartDate\":\"2026-08-25\",\"plannedFinishDate\":\"2026-09-21\"
}")
A2030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A2030\",\"name\":\"Demolish Superstructure\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":25,\"plannedStartDate\":\"2026-09-22\",\"plannedFinishDate\":\"2026-10-26\"
}")
A2040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A2040\",\"name\":\"Remove Old Piers & Abutments\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":15,\"plannedStartDate\":\"2026-10-27\",\"plannedFinishDate\":\"2026-11-16\"
}")
A2050=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A2050\",\"name\":\"Site Clearance & Grading\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DEMO\",\"originalDuration\":10,\"plannedStartDate\":\"2026-11-17\",\"plannedFinishDate\":\"2026-11-30\"
}")

# Foundation Phase
A3010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A3010\",\"name\":\"Install Cofferdam & Dewatering\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":20,\"plannedStartDate\":\"2026-12-01\",\"plannedFinishDate\":\"2026-12-28\"
}")
A3020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A3020\",\"name\":\"Drive Foundation Piles (Pier 1)\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":25,\"plannedStartDate\":\"2026-12-29\",\"plannedFinishDate\":\"2027-02-01\"
}")
A3030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A3030\",\"name\":\"Drive Foundation Piles (Pier 2)\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":25,\"plannedStartDate\":\"2027-01-15\",\"plannedFinishDate\":\"2027-02-18\"
}")
A3040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A3040\",\"name\":\"Pour Pile Caps\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":15,\"plannedStartDate\":\"2027-02-19\",\"plannedFinishDate\":\"2027-03-11\"
}")
A3050=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A3050\",\"name\":\"Construct Abutments\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":20,\"plannedStartDate\":\"2027-03-12\",\"plannedFinishDate\":\"2027-04-08\"
}")
A3060=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A3060\",\"name\":\"Build Bridge Piers\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_FOUND\",\"originalDuration\":30,\"plannedStartDate\":\"2027-03-12\",\"plannedFinishDate\":\"2027-04-22\"
}")

# Superstructure Phase
A4010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A4010\",\"name\":\"Fabricate Steel Girders (Offsite)\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_SUPER\",\"originalDuration\":45,\"plannedStartDate\":\"2027-02-01\",\"plannedFinishDate\":\"2027-04-04\"
}")
A4020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A4020\",\"name\":\"Transport & Erect Steel Girders\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_SUPER\",\"originalDuration\":20,\"plannedStartDate\":\"2027-04-23\",\"plannedFinishDate\":\"2027-05-20\"
}")
A4030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A4030\",\"name\":\"Install Cross-Bracing & Diaphragms\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_SUPER\",\"originalDuration\":15,\"plannedStartDate\":\"2027-05-21\",\"plannedFinishDate\":\"2027-06-10\"
}")
A4040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A4040\",\"name\":\"Install Bearing Pads\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_SUPER\",\"originalDuration\":10,\"plannedStartDate\":\"2027-06-11\",\"plannedFinishDate\":\"2027-06-24\"
}")

# Deck & Finishing Phase
A5010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A5010\",\"name\":\"Install Deck Formwork & Rebar\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":20,\"plannedStartDate\":\"2027-06-25\",\"plannedFinishDate\":\"2027-07-22\"
}")
A5020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A5020\",\"name\":\"Pour Bridge Deck Concrete\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":10,\"plannedStartDate\":\"2027-07-23\",\"plannedFinishDate\":\"2027-08-05\"
}")
A5030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A5030\",\"name\":\"Deck Curing & Post-Tensioning\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":14,\"plannedStartDate\":\"2027-08-06\",\"plannedFinishDate\":\"2027-08-25\"
}")
A5040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A5040\",\"name\":\"Install Expansion Joints\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":8,\"plannedStartDate\":\"2027-08-26\",\"plannedFinishDate\":\"2027-09-06\"
}")
A5050=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A5050\",\"name\":\"Install Guard Rails & Barriers\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":12,\"plannedStartDate\":\"2027-09-07\",\"plannedFinishDate\":\"2027-09-22\"
}")
A5060=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A5060\",\"name\":\"Road Surface & Line Marking\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":10,\"plannedStartDate\":\"2027-09-23\",\"plannedFinishDate\":\"2027-10-06\"
}")
A5070=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A5070\",\"name\":\"Install Lighting & Drainage\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_DECK\",\"originalDuration\":15,\"plannedStartDate\":\"2027-09-07\",\"plannedFinishDate\":\"2027-09-25\"
}")

# Closeout Phase
A6010=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A6010\",\"name\":\"Load Testing & Inspection\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_CLOSE\",\"originalDuration\":10,\"plannedStartDate\":\"2027-10-07\",\"plannedFinishDate\":\"2027-10-20\"
}")
A6020=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A6020\",\"name\":\"Demobilize Equipment\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_CLOSE\",\"originalDuration\":5,\"plannedStartDate\":\"2027-10-21\",\"plannedFinishDate\":\"2027-10-27\"
}")
A6030=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A6030\",\"name\":\"Final Documentation & As-Built Drawings\",\"activityType\":\"TASK_DEPENDENT\",
  \"wbsNodeId\":\"$WBS1_CLOSE\",\"originalDuration\":10,\"plannedStartDate\":\"2027-10-21\",\"plannedFinishDate\":\"2027-11-03\"
}")
A6040=$(post_and_get_id "/v1/projects/$PROJ1/activities" "{
  \"code\":\"A6040\",\"name\":\"Bridge Opening Ceremony\",\"activityType\":\"FINISH_MILESTONE\",
  \"wbsNodeId\":\"$WBS1_CLOSE\",\"originalDuration\":0,\"plannedStartDate\":\"2027-11-04\",\"plannedFinishDate\":\"2027-11-04\"
}")
echo "  Activities: 28 created"

# Relationships for Project 1
echo "  Creating relationships..."
create_rel() {
  curl -s -X POST "$API/v1/projects/$PROJ1/relationships" -H "$CT" -H "$AUTH" \
    -d "{\"predecessorActivityId\":\"$1\",\"successorActivityId\":\"$2\",\"relationshipType\":\"$(case $3 in FS) echo FINISH_TO_START;; FF) echo FINISH_TO_FINISH;; SS) echo START_TO_START;; SF) echo START_TO_FINISH;; *) echo $3;; esac)\",\"lag\":$4}" > /dev/null 2>&1
}

# Pre-Construction chain
create_rel "$A1010" "$A1020" "FS" 0
create_rel "$A1010" "$A1030" "FS" 0
create_rel "$A1020" "$A1040" "FS" 0
create_rel "$A1030" "$A1050" "FS" 0
create_rel "$A1040" "$A1060" "FS" 0
create_rel "$A1050" "$A1060" "FS" 0

# Demolition chain
create_rel "$A1060" "$A2010" "FS" 0
create_rel "$A2010" "$A2020" "FS" 0
create_rel "$A2020" "$A2030" "FS" 0
create_rel "$A2030" "$A2040" "FS" 0
create_rel "$A2040" "$A2050" "FS" 0

# Foundation chain
create_rel "$A2050" "$A3010" "FS" 0
create_rel "$A3010" "$A3020" "FS" 0
create_rel "$A3010" "$A3030" "SS" 10  # Pier 2 starts 10 days after Pier 1 starts
create_rel "$A3020" "$A3040" "FS" 0
create_rel "$A3030" "$A3040" "FS" 0
create_rel "$A3040" "$A3050" "SS" 0
create_rel "$A3040" "$A3060" "SS" 0

# Superstructure
create_rel "$A3040" "$A4010" "SS" -30  # Fabrication starts 30 days before pile caps finish (long lead)
create_rel "$A3050" "$A4020" "FS" 0
create_rel "$A3060" "$A4020" "FS" 0
create_rel "$A4010" "$A4020" "FS" 0
create_rel "$A4020" "$A4030" "FS" 0
create_rel "$A4030" "$A4040" "FS" 0

# Deck & Finishing
create_rel "$A4040" "$A5010" "FS" 0
create_rel "$A5010" "$A5020" "FS" 0
create_rel "$A5020" "$A5030" "FS" 0
create_rel "$A5030" "$A5040" "FS" 0
create_rel "$A5040" "$A5050" "FS" 0
create_rel "$A5040" "$A5070" "SS" 0  # Lighting starts same as expansion joints finish
create_rel "$A5050" "$A5060" "FS" 0
create_rel "$A5070" "$A5060" "FF" 0  # Lighting finishes with road surface

# Closeout
create_rel "$A5060" "$A6010" "FS" 0
create_rel "$A6010" "$A6020" "FS" 0
create_rel "$A6010" "$A6030" "SS" 0
create_rel "$A6020" "$A6040" "FS" 0
create_rel "$A6030" "$A6040" "FS" 0
echo "  Relationships: 35 created"

# ============================================================
# 6. PROJECT 2: Commercial Office Building
# ============================================================
echo ""
echo "[6/12] Creating Project 2: Skyline Office Tower..."

PROJ2=$(post_and_get_id "/v1/projects" "{
  \"code\":\"SOT-2026\",
  \"name\":\"Skyline Office Tower\",
  \"description\":\"20-story commercial office building with underground parking. Total budget: \$85M. LEED Gold certification targeted.\",
  \"epsNodeId\":\"$EPS_BUILDINGS\",
  \"obsNodeId\":\"$OBS_PM_STRUCT\",
  \"plannedStartDate\":\"2026-07-01\",
  \"plannedFinishDate\":\"2028-12-31\",
  \"status\":\"ACTIVE\",
  \"priority\":2
}")
echo "  Project: Skyline Office Tower ($PROJ2)"

# WBS for Project 2
WBS2_ROOT=$(post_and_get_id "/v1/projects/$PROJ2/wbs" '{"code":"SOT","name":"Skyline Office Tower","parentId":null}')
WBS2_SITE=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.1\",\"name\":\"Site Work\",\"parentId\":\"$WBS2_ROOT\"}")
WBS2_STRUCT=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.2\",\"name\":\"Structure\",\"parentId\":\"$WBS2_ROOT\"}")
WBS2_ENCL=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.3\",\"name\":\"Building Envelope\",\"parentId\":\"$WBS2_ROOT\"}")
WBS2_MEP=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.4\",\"name\":\"MEP Systems\",\"parentId\":\"$WBS2_ROOT\"}")
WBS2_FIT=$(post_and_get_id "/v1/projects/$PROJ2/wbs" "{\"code\":\"SOT.5\",\"name\":\"Interior Fitout\",\"parentId\":\"$WBS2_ROOT\"}")
echo "  WBS: 5 nodes created"

# Activities for Project 2
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

# Relationships for Project 2
echo "  Creating relationships..."
create_rel2() {
  curl -s -X POST "$API/v1/projects/$PROJ2/relationships" -H "$CT" -H "$AUTH" \
    -d "{\"predecessorActivityId\":\"$1\",\"successorActivityId\":\"$2\",\"relationshipType\":\"$(case $3 in FS) echo FINISH_TO_START;; FF) echo FINISH_TO_FINISH;; SS) echo START_TO_START;; SF) echo START_TO_FINISH;; *) echo $3;; esac)\",\"lag\":$4}" > /dev/null 2>&1
}
create_rel2 "$B1010" "$B1020" "FS" 0
create_rel2 "$B1020" "$B1030" "FS" 0
create_rel2 "$B1030" "$B2010" "FS" 0
create_rel2 "$B2010" "$B2020" "FS" 0
create_rel2 "$B2020" "$B2030" "FS" 0
create_rel2 "$B2020" "$B3010" "SS" 15
create_rel2 "$B2030" "$B2040" "FS" 0
create_rel2 "$B2040" "$B2050" "FS" 0
create_rel2 "$B2040" "$B3020" "SS" 0
create_rel2 "$B2050" "$B3030" "FS" 0
create_rel2 "$B2030" "$B4010" "SS" 0
create_rel2 "$B2030" "$B4020" "SS" 0
create_rel2 "$B2030" "$B4030" "SS" 10
create_rel2 "$B2030" "$B4040" "SS" 10
create_rel2 "$B3020" "$B5010" "FS" 0
create_rel2 "$B4020" "$B5010" "FS" 0
create_rel2 "$B5010" "$B5020" "FS" 0
create_rel2 "$B5020" "$B5030" "FS" 0
create_rel2 "$B4030" "$B5030" "FS" 0
create_rel2 "$B4040" "$B5030" "FS" 0
create_rel2 "$B5020" "$B5040" "SS" -15
create_rel2 "$B5030" "$B5050" "FS" 0
create_rel2 "$B5040" "$B5050" "FS" 0
echo "  Relationships: 23 created"

# ============================================================
# 7. PROJECT 3: Solar Farm (Energy Project)
# ============================================================
echo ""
echo "[7/12] Creating Project 3: Desert Sun Solar Farm..."

PROJ3=$(post_and_get_id "/v1/projects" "{
  \"code\":\"DSSF-2026\",
  \"name\":\"Desert Sun Solar Farm 200MW\",
  \"description\":\"200MW utility-scale solar photovoltaic power plant on 1,200 acres. Includes tracker system, inverter stations, and grid interconnection. Budget: \$180M.\",
  \"epsNodeId\":\"$EPS_SOLAR\",
  \"plannedStartDate\":\"2026-08-01\",
  \"plannedFinishDate\":\"2028-03-31\",
  \"status\":\"PLANNED\",
  \"priority\":1
}")
echo "  Project: Desert Sun Solar Farm ($PROJ3)"

WBS3_ROOT=$(post_and_get_id "/v1/projects/$PROJ3/wbs" '{"code":"DSSF","name":"Desert Sun Solar Farm","parentId":null}')
WBS3_DEV=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.1\",\"name\":\"Development & Permitting\",\"parentId\":\"$WBS3_ROOT\"}")
WBS3_CIVIL=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.2\",\"name\":\"Civil & Site Prep\",\"parentId\":\"$WBS3_ROOT\"}")
WBS3_ELEC=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.3\",\"name\":\"Electrical Infrastructure\",\"parentId\":\"$WBS3_ROOT\"}")
WBS3_PANEL=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.4\",\"name\":\"Panel Installation\",\"parentId\":\"$WBS3_ROOT\"}")
WBS3_COMM=$(post_and_get_id "/v1/projects/$PROJ3/wbs" "{\"code\":\"DSSF.5\",\"name\":\"Commissioning\",\"parentId\":\"$WBS3_ROOT\"}")

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

# Relationships for Project 3
create_rel3() {
  curl -s -X POST "$API/v1/projects/$PROJ3/relationships" -H "$CT" -H "$AUTH" \
    -d "{\"predecessorActivityId\":\"$1\",\"successorActivityId\":\"$2\",\"relationshipType\":\"$(case $3 in FS) echo FINISH_TO_START;; FF) echo FINISH_TO_FINISH;; SS) echo START_TO_START;; SF) echo START_TO_FINISH;; *) echo $3;; esac)\",\"lag\":$4}" > /dev/null 2>&1
}
create_rel3 "$C1010" "$C1030" "FS" 0
create_rel3 "$C1020" "$C1030" "FS" 0
create_rel3 "$C1030" "$C2010" "FS" 0
create_rel3 "$C2010" "$C2020" "SS" 15
create_rel3 "$C2010" "$C2030" "FS" 0
create_rel3 "$C2020" "$C3010" "FS" 0
create_rel3 "$C2030" "$C4010" "FS" 0
create_rel3 "$C2030" "$C3030" "SS" 20
create_rel3 "$C3010" "$C3020" "FS" 0
create_rel3 "$C3030" "$C3020" "FS" 0
create_rel3 "$C3020" "$C3040" "FS" 0
create_rel3 "$C4010" "$C4020" "FS" 0
create_rel3 "$C4010" "$C4030" "SS" 30
create_rel3 "$C4020" "$C5010" "FS" 0
create_rel3 "$C4030" "$C5010" "FS" 0
create_rel3 "$C3040" "$C5010" "FS" 0
create_rel3 "$C5010" "$C5020" "FS" 0
create_rel3 "$C5020" "$C5030" "FS" 0
echo "  Relationships: 18 created"

# ============================================================
# 8. PROJECT 4: ERP Software Implementation
# ============================================================
echo ""
echo "[8/12] Creating Project 4: ERP System Upgrade..."

PROJ4=$(post_and_get_id "/v1/projects" "{
  \"code\":\"ERP-2026\",
  \"name\":\"Enterprise ERP System Upgrade\",
  \"description\":\"Migration from legacy ERP to cloud-based SAP S/4HANA. Covers finance, HR, procurement, and supply chain modules. Budget: \$12M.\",
  \"epsNodeId\":\"$EPS_SOFTWARE\",
  \"obsNodeId\":\"$OBS_VP_IT\",
  \"plannedStartDate\":\"2026-09-01\",
  \"plannedFinishDate\":\"2027-09-30\",
  \"status\":\"PLANNED\",
  \"priority\":3
}")
echo "  Project: ERP System Upgrade ($PROJ4)"

WBS4_ROOT=$(post_and_get_id "/v1/projects/$PROJ4/wbs" '{"code":"ERP","name":"ERP System Upgrade","parentId":null}')
WBS4_DISC=$(post_and_get_id "/v1/projects/$PROJ4/wbs" "{\"code\":\"ERP.1\",\"name\":\"Discovery & Planning\",\"parentId\":\"$WBS4_ROOT\"}")
WBS4_BUILD=$(post_and_get_id "/v1/projects/$PROJ4/wbs" "{\"code\":\"ERP.2\",\"name\":\"Build & Configure\",\"parentId\":\"$WBS4_ROOT\"}")
WBS4_TEST=$(post_and_get_id "/v1/projects/$PROJ4/wbs" "{\"code\":\"ERP.3\",\"name\":\"Testing & Migration\",\"parentId\":\"$WBS4_ROOT\"}")
WBS4_GO=$(post_and_get_id "/v1/projects/$PROJ4/wbs" "{\"code\":\"ERP.4\",\"name\":\"Go-Live & Support\",\"parentId\":\"$WBS4_ROOT\"}")

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

# Relationships for Project 4
create_rel4() {
  curl -s -X POST "$API/v1/projects/$PROJ4/relationships" -H "$CT" -H "$AUTH" \
    -d "{\"predecessorActivityId\":\"$1\",\"successorActivityId\":\"$2\",\"relationshipType\":\"$(case $3 in FS) echo FINISH_TO_START;; FF) echo FINISH_TO_FINISH;; SS) echo START_TO_START;; SF) echo START_TO_FINISH;; *) echo $3;; esac)\",\"lag\":$4}" > /dev/null 2>&1
}
create_rel4 "$D1010" "$D1020" "FS" 0
create_rel4 "$D1020" "$D1030" "FS" 0
create_rel4 "$D1030" "$D2010" "FS" 0
create_rel4 "$D1030" "$D2020" "FS" 0
create_rel4 "$D1030" "$D2030" "SS" 10
create_rel4 "$D2010" "$D2040" "FS" 0
create_rel4 "$D2020" "$D2050" "FS" 0
create_rel4 "$D2030" "$D2050" "FS" 0
create_rel4 "$D2040" "$D3010" "FS" 0
create_rel4 "$D2050" "$D3010" "FS" 0
create_rel4 "$D3010" "$D3020" "FS" 0
create_rel4 "$D3020" "$D3030" "FS" 0
create_rel4 "$D3020" "$D3040" "SS" 0
create_rel4 "$D3030" "$D3050" "FS" 0
create_rel4 "$D3040" "$D3050" "FS" 0
create_rel4 "$D3050" "$D4010" "FS" 0
create_rel4 "$D4010" "$D4020" "FS" 0
create_rel4 "$D4020" "$D4030" "FS" 0
echo "  Relationships: 18 created"

# ============================================================
# 9. Run CPM Schedule on all projects
# ============================================================
echo ""
echo "[9/12] Running CPM schedule on all projects..."

for PID in "$PROJ1" "$PROJ2" "$PROJ3" "$PROJ4"; do
  if [ -n "$PID" ]; then
    SCHED_RESP=$(curl -s -X POST "$API/v1/projects/$PID/schedule" -H "$CT" -H "$AUTH" -d '{}')
    STATUS=$(echo "$SCHED_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','UNKNOWN'))" 2>/dev/null)
    echo "  Project $PID: Schedule $STATUS"
  fi
done

# ============================================================
# 10. Create Baselines
# ============================================================
echo ""
echo "[10/12] Creating baselines..."

for PID_NAME in "$PROJ1:Metro River Bridge" "$PROJ2:Skyline Tower" "$PROJ3:Solar Farm" "$PROJ4:ERP Upgrade"; do
  PID=$(echo $PID_NAME | cut -d: -f1)
  PNAME=$(echo $PID_NAME | cut -d: -f2)
  if [ -n "$PID" ]; then
    BL=$(post_and_get_id "/v1/projects/$PID/baselines" "{\"name\":\"Original Plan\",\"baselineType\":\"PLANNED\",\"description\":\"Original baseline plan\"}")
    echo "  Baseline for $PNAME: $BL"
  fi
done

# ============================================================
# 11. Create Risks
# ============================================================
echo ""
echo "[11/12] Creating project risks..."

# Risks for Bridge project
if [ -n "$PROJ1" ]; then
  post_and_get_id "/v1/projects/$PROJ1/risks" '{"code":"R001","title":"Flood During Construction","description":"River flooding could delay foundation work by 2-4 weeks","probability":"HIGH","impact":"HIGH","category":"EXTERNAL","scheduleImpactDays":20}' > /dev/null
  post_and_get_id "/v1/projects/$PROJ1/risks" '{"code":"R002","title":"Steel Price Escalation","description":"Global steel prices volatile, could increase project cost by 10-15%","probability":"MEDIUM","impact":"HIGH","category":"COST","costImpact":4500000}' > /dev/null
  post_and_get_id "/v1/projects/$PROJ1/risks" '{"code":"R003","title":"Permit Delays","description":"Regulatory approval may take longer than planned","probability":"MEDIUM","impact":"MEDIUM","category":"EXTERNAL","scheduleImpactDays":15}' > /dev/null
  post_and_get_id "/v1/projects/$PROJ1/risks" '{"code":"R004","title":"Skilled Labor Shortage","description":"Certified welders and pile drivers in short supply","probability":"HIGH","impact":"MEDIUM","category":"RESOURCE","scheduleImpactDays":10}' > /dev/null
  echo "  Bridge: 4 risks created"
fi

# Risks for Building project
if [ -n "$PROJ2" ]; then
  post_and_get_id "/v1/projects/$PROJ2/risks" '{"code":"R010","title":"Curtain Wall Delivery Delay","description":"Custom curtain wall panels have 16-week lead time from overseas","probability":"MEDIUM","impact":"HIGH","category":"ORGANIZATIONAL","scheduleImpactDays":30}' > /dev/null
  post_and_get_id "/v1/projects/$PROJ2/risks" '{"code":"R011","title":"Subcontractor Default","description":"MEP subcontractor financial instability","probability":"LOW","impact":"HIGH","category":"COST","costImpact":2000000}' > /dev/null
  post_and_get_id "/v1/projects/$PROJ2/risks" '{"code":"R012","title":"Design Changes by Client","description":"Client may request office layout changes during construction","probability":"HIGH","impact":"MEDIUM","category":"PROJECT_MANAGEMENT","scheduleImpactDays":15}' > /dev/null
  echo "  Building: 3 risks created"
fi

# Risks for Solar project
if [ -n "$PROJ3" ]; then
  post_and_get_id "/v1/projects/$PROJ3/risks" '{"code":"R020","title":"Panel Supply Chain Disruption","description":"Geopolitical tensions affecting solar panel imports","probability":"MEDIUM","impact":"HIGH","category":"ORGANIZATIONAL","scheduleImpactDays":45}' > /dev/null
  post_and_get_id "/v1/projects/$PROJ3/risks" '{"code":"R021","title":"Grid Interconnection Delay","description":"Utility may delay grid connection approval","probability":"MEDIUM","impact":"HIGH","category":"EXTERNAL","scheduleImpactDays":30}' > /dev/null
  echo "  Solar: 2 risks created"
fi

# ============================================================
# 12. Create Portfolios
# ============================================================
echo ""
echo "[12/12] Creating portfolios..."

PORT1=$(post_and_get_id "/v1/portfolios" '{"code":"CAPITAL-2026","name":"Capital Projects Portfolio 2026","description":"All major capital expenditure projects for fiscal year 2026-2028"}')
echo "  Portfolio: Capital Projects 2026 ($PORT1)"

PORT2=$(post_and_get_id "/v1/portfolios" '{"code":"INFRA-PROG","name":"Infrastructure Program","description":"Roads, bridges, and utility infrastructure projects"}')
echo "  Portfolio: Infrastructure Program ($PORT2)"

# Add projects to portfolios
if [ -n "$PORT1" ] && [ -n "$PROJ1" ]; then
  curl -s -X POST "$API/v1/portfolios/$PORT1/projects" -H "$CT" -H "$AUTH" \
    -d "{\"projectId\":\"$PROJ1\"}" > /dev/null 2>&1
  curl -s -X POST "$API/v1/portfolios/$PORT1/projects" -H "$CT" -H "$AUTH" \
    -d "{\"projectId\":\"$PROJ2\"}" > /dev/null 2>&1
  curl -s -X POST "$API/v1/portfolios/$PORT1/projects" -H "$CT" -H "$AUTH" \
    -d "{\"projectId\":\"$PROJ3\"}" > /dev/null 2>&1
  curl -s -X POST "$API/v1/portfolios/$PORT1/projects" -H "$CT" -H "$AUTH" \
    -d "{\"projectId\":\"$PROJ4\"}" > /dev/null 2>&1
  echo "  Added all 4 projects to Capital Portfolio"
fi

if [ -n "$PORT2" ] && [ -n "$PROJ1" ]; then
  curl -s -X POST "$API/v1/portfolios/$PORT2/projects" -H "$CT" -H "$AUTH" \
    -d "{\"projectId\":\"$PROJ1\"}" > /dev/null 2>&1
  curl -s -X POST "$API/v1/portfolios/$PORT2/projects" -H "$CT" -H "$AUTH" \
    -d "{\"projectId\":\"$PROJ3\"}" > /dev/null 2>&1
  echo "  Added Bridge + Solar to Infrastructure Portfolio"
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "=========================================="
echo " Demo Data Seeding Complete!"
echo "=========================================="
echo ""
echo " EPS Hierarchy:  8 nodes (3 levels)"
echo " OBS Hierarchy:  5 nodes (3 levels)"
echo " Resources:      18 (10 labor, 4 equipment, 4 material)"
echo " Projects:       4"
echo "   - MRB-2026:  Metro River Bridge (28 activities, 35 relationships)"
echo "   - SOT-2026:  Skyline Office Tower (20 activities, 23 relationships)"
echo "   - DSSF-2026: Desert Sun Solar Farm (16 activities, 18 relationships)"
echo "   - ERP-2026:  ERP System Upgrade (16 activities, 18 relationships)"
echo " Total:          80 activities, 94 relationships"
echo " Baselines:      4 (one per project)"
echo " Risks:          9 across 3 projects"
echo " Portfolios:     2"
echo ""
echo " Access the application:"
echo "   Frontend: http://localhost:3000"
echo "   Backend:  http://localhost:8080"
echo "   Login:    admin / admin123"
echo "=========================================="

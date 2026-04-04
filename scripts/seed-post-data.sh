#!/bin/bash
# Post-seeding: Create relationships, run schedules, create baselines
# Run AFTER seed-demo-data.sh

API="http://localhost:8080"
CT="Content-Type: application/json"

echo "=== Post-Seed: Relationships, Schedules, Baselines ==="

# Login
TOKEN=$(curl -s -X POST "$API/v1/auth/login" -H "$CT" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
echo "[OK] Logged in"

# Clean existing relationships (for re-runs)
docker exec bipros-postgres psql -U bipros -d bipros -c "TRUNCATE activity.activity_relationships, baseline.baseline_activities, baseline.baseline_relationships, baseline.baselines CASCADE;" 2>/dev/null

# Helper: get activity ID by project_id + code
act_id() {
  docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT id FROM activity.activities WHERE project_id='$1' AND code='$2';" 2>/dev/null | tr -d ' \n'
}

# Helper: create relationship
rel() {
  local proj=$1 pred=$2 succ=$3 rtype=$4 lag=$5
  local pred_id=$(act_id "$proj" "$pred")
  local succ_id=$(act_id "$proj" "$succ")
  local full_type=""
  if [ "$rtype" = "FS" ]; then full_type="FINISH_TO_START"
  elif [ "$rtype" = "FF" ]; then full_type="FINISH_TO_FINISH"
  elif [ "$rtype" = "SS" ]; then full_type="START_TO_START"
  elif [ "$rtype" = "SF" ]; then full_type="START_TO_FINISH"
  fi
  curl -s -X POST "$API/v1/projects/$proj/relationships" -H "$CT" -H "$AUTH" \
    -d "{\"predecessorActivityId\":\"$pred_id\",\"successorActivityId\":\"$succ_id\",\"relationshipType\":\"$full_type\",\"lag\":$lag}" > /dev/null 2>&1
}

# Get project IDs
PROJ1=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT id FROM project.projects WHERE code='MRB-2026';" 2>/dev/null | tr -d ' \n')
PROJ2=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT id FROM project.projects WHERE code='SOT-2026';" 2>/dev/null | tr -d ' \n')
PROJ3=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT id FROM project.projects WHERE code='DSSF-2026';" 2>/dev/null | tr -d ' \n')
PROJ4=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT id FROM project.projects WHERE code='ERP-2026';" 2>/dev/null | tr -d ' \n')

echo "  MRB: $PROJ1"
echo "  SOT: $PROJ2"
echo "  DSSF: $PROJ3"
echo "  ERP: $PROJ4"

# ============================================================
# PROJECT 1: Metro River Bridge — 35 relationships
# ============================================================
echo ""
echo "[1/8] Creating relationships for Metro River Bridge..."

# Pre-Construction chain
rel $PROJ1 A1010 A1020 FS 0
rel $PROJ1 A1010 A1030 FS 0
rel $PROJ1 A1020 A1040 FS 0
rel $PROJ1 A1030 A1050 FS 0
rel $PROJ1 A1040 A1060 FS 0
rel $PROJ1 A1050 A1060 FS 0

# Demolition chain
rel $PROJ1 A1060 A2010 FS 0
rel $PROJ1 A2010 A2020 FS 0
rel $PROJ1 A2020 A2030 FS 0
rel $PROJ1 A2030 A2040 FS 0
rel $PROJ1 A2040 A2050 FS 0

# Foundation chain
rel $PROJ1 A2050 A3010 FS 0
rel $PROJ1 A3010 A3020 FS 0
rel $PROJ1 A3010 A3030 SS 10
rel $PROJ1 A3020 A3040 FS 0
rel $PROJ1 A3030 A3040 FS 0
rel $PROJ1 A3040 A3050 SS 0
rel $PROJ1 A3040 A3060 SS 0

# Superstructure
rel $PROJ1 A3050 A4020 FS 0
rel $PROJ1 A3060 A4020 FS 0
rel $PROJ1 A4010 A4020 FS 0
rel $PROJ1 A4020 A4030 FS 0
rel $PROJ1 A4030 A4040 FS 0

# Deck & Finishing
rel $PROJ1 A4040 A5010 FS 0
rel $PROJ1 A5010 A5020 FS 0
rel $PROJ1 A5020 A5030 FS 0
rel $PROJ1 A5030 A5040 FS 0
rel $PROJ1 A5040 A5050 FS 0
rel $PROJ1 A5040 A5070 SS 0
rel $PROJ1 A5050 A5060 FS 0
rel $PROJ1 A5070 A5060 FF 0

# Closeout
rel $PROJ1 A5060 A6010 FS 0
rel $PROJ1 A6010 A6020 FS 0
rel $PROJ1 A6010 A6030 SS 0
rel $PROJ1 A6020 A6040 FS 0
rel $PROJ1 A6030 A6040 FS 0

COUNT1=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT count(*) FROM activity.activity_relationships WHERE project_id='$PROJ1';" 2>/dev/null | tr -d ' \n')
echo "  Created $COUNT1 relationships"

# ============================================================
# PROJECT 2: Skyline Office Tower — 23 relationships
# ============================================================
echo ""
echo "[2/8] Creating relationships for Skyline Office Tower..."

rel $PROJ2 B1010 B1020 FS 0
rel $PROJ2 B1020 B1030 FS 0
rel $PROJ2 B1030 B2010 FS 0
rel $PROJ2 B2010 B2020 FS 0
rel $PROJ2 B2020 B2030 FS 0
rel $PROJ2 B2020 B3010 SS 15
rel $PROJ2 B2030 B2040 FS 0
rel $PROJ2 B2040 B2050 FS 0
rel $PROJ2 B2040 B3020 SS 0
rel $PROJ2 B2050 B3030 FS 0
rel $PROJ2 B2030 B4010 SS 0
rel $PROJ2 B2030 B4020 SS 0
rel $PROJ2 B2030 B4030 SS 10
rel $PROJ2 B2030 B4040 SS 10
rel $PROJ2 B3020 B5010 FS 0
rel $PROJ2 B4020 B5010 FS 0
rel $PROJ2 B5010 B5020 FS 0
rel $PROJ2 B5020 B5030 FS 0
rel $PROJ2 B4030 B5030 FS 0
rel $PROJ2 B4040 B5030 FS 0
rel $PROJ2 B5030 B5050 FS 0
rel $PROJ2 B5040 B5050 FS 0

COUNT2=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT count(*) FROM activity.activity_relationships WHERE project_id='$PROJ2';" 2>/dev/null | tr -d ' \n')
echo "  Created $COUNT2 relationships"

# ============================================================
# PROJECT 3: Solar Farm — 18 relationships
# ============================================================
echo ""
echo "[3/8] Creating relationships for Solar Farm..."

rel $PROJ3 C1010 C1030 FS 0
rel $PROJ3 C1020 C1030 FS 0
rel $PROJ3 C1030 C2010 FS 0
rel $PROJ3 C2010 C2020 SS 15
rel $PROJ3 C2010 C2030 FS 0
rel $PROJ3 C2020 C3010 FS 0
rel $PROJ3 C2030 C4010 FS 0
rel $PROJ3 C2030 C3030 SS 20
rel $PROJ3 C3010 C3020 FS 0
rel $PROJ3 C3030 C3020 FS 0
rel $PROJ3 C3020 C3040 FS 0
rel $PROJ3 C4010 C4020 FS 0
rel $PROJ3 C4010 C4030 SS 30
rel $PROJ3 C4020 C5010 FS 0
rel $PROJ3 C4030 C5010 FS 0
rel $PROJ3 C3040 C5010 FS 0
rel $PROJ3 C5010 C5020 FS 0
rel $PROJ3 C5020 C5030 FS 0

COUNT3=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT count(*) FROM activity.activity_relationships WHERE project_id='$PROJ3';" 2>/dev/null | tr -d ' \n')
echo "  Created $COUNT3 relationships"

# ============================================================
# PROJECT 4: ERP — 18 relationships
# ============================================================
echo ""
echo "[4/8] Creating relationships for ERP Upgrade..."

rel $PROJ4 D1010 D1020 FS 0
rel $PROJ4 D1020 D1030 FS 0
rel $PROJ4 D1030 D2010 FS 0
rel $PROJ4 D1030 D2020 FS 0
rel $PROJ4 D1030 D2030 SS 10
rel $PROJ4 D2010 D2040 FS 0
rel $PROJ4 D2020 D2050 FS 0
rel $PROJ4 D2030 D2050 FS 0
rel $PROJ4 D2040 D3010 FS 0
rel $PROJ4 D2050 D3010 FS 0
rel $PROJ4 D3010 D3020 FS 0
rel $PROJ4 D3020 D3030 FS 0
rel $PROJ4 D3020 D3040 SS 0
rel $PROJ4 D3030 D3050 FS 0
rel $PROJ4 D3040 D3050 FS 0
rel $PROJ4 D3050 D4010 FS 0
rel $PROJ4 D4010 D4020 FS 0
rel $PROJ4 D4020 D4030 FS 0

COUNT4=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT count(*) FROM activity.activity_relationships WHERE project_id='$PROJ4';" 2>/dev/null | tr -d ' \n')
echo "  Created $COUNT4 relationships"

# ============================================================
# Assign calendar to all activities
# ============================================================
echo ""
echo "[5/8] Assigning Standard calendar to all activities..."
CAL_ID=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT id FROM scheduling.calendars WHERE name='Standard' LIMIT 1;" 2>/dev/null | tr -d ' \n')
docker exec bipros-postgres psql -U bipros -d bipros -c "UPDATE activity.activities SET calendar_id = '$CAL_ID' WHERE calendar_id IS NULL;" 2>/dev/null
echo "  Calendar $CAL_ID assigned to all activities"

# ============================================================
# Run CPM Schedule on all projects
# ============================================================
echo ""
echo "[6/8] Running CPM schedules..."

for PROJ_ID in "$PROJ1" "$PROJ2" "$PROJ3" "$PROJ4"; do
  RESP=$(curl -s -X POST "$API/v1/projects/$PROJ_ID/schedule" -H "$CT" -H "$AUTH" \
    -d "{\"projectId\":\"$PROJ_ID\"}")
  STATUS=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status', d.get('error',{}).get('message','UNKNOWN')))" 2>/dev/null)
  echo "  $PROJ_ID: $STATUS"
done

# ============================================================
# Create Baselines
# ============================================================
echo ""
echo "[7/8] Creating baselines..."

for PROJ_ID in "$PROJ1" "$PROJ2" "$PROJ3" "$PROJ4"; do
  RESP=$(curl -s -X POST "$API/v1/projects/$PROJ_ID/baselines" -H "$CT" -H "$AUTH" \
    -d '{"name":"Original Plan","baselineType":"PLANNED","description":"Original baseline plan"}')
  BL_ID=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id', d.get('error',{}).get('message','FAILED')))" 2>/dev/null)
  echo "  $PROJ_ID: $BL_ID"
done

# ============================================================
# Summary
# ============================================================
echo ""
echo "=== Post-Seed Summary ==="
TOTAL_REL=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT count(*) FROM activity.activity_relationships;" 2>/dev/null | tr -d ' \n')
TOTAL_BL=$(docker exec bipros-postgres psql -U bipros -d bipros -t -c "SELECT count(*) FROM baseline.baselines;" 2>/dev/null | tr -d ' \n')
echo "  Relationships: $TOTAL_REL"
echo "  Baselines: $TOTAL_BL"
echo "  Done!"

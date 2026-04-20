#!/bin/bash
set -e

BASE="http://localhost:8080"
echo "========================================="
echo "  Bipros EPPM — End-to-End Use Case Test"
echo "========================================="

# ── 1. Login ──────────────────────────────────────────
echo ""
echo "=== 1. LOGIN ==="
LOGIN=$(curl -sf -X POST "$BASE/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "✅ Login successful — token obtained"

# ── 2. Create EPS hierarchy ───────────────────────────
echo ""
echo "=== 2. CREATE EPS HIERARCHY ==="

# Root node
EPS_ROOT=$(curl -sf -X POST "$BASE/v1/eps" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"INFRA","name":"Infrastructure Division"}')
EPS_ROOT_ID=$(echo "$EPS_ROOT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ Created EPS root: Infrastructure Division (id=$EPS_ROOT_ID)"

# Child node
EPS_CHILD=$(curl -sf -X POST "$BASE/v1/eps" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"HWY\",\"name\":\"Highway Projects\",\"parentId\":\"$EPS_ROOT_ID\"}")
EPS_CHILD_ID=$(echo "$EPS_CHILD" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ Created EPS child: Highway Projects (id=$EPS_CHILD_ID)"

# Verify tree
TREE=$(curl -sf "$BASE/v1/eps" -H "Authorization: Bearer $TOKEN")
TREE_COUNT=$(echo "$TREE" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']))")
echo "✅ EPS tree has $TREE_COUNT nodes"

# ── 3. Create a project ──────────────────────────────
echo ""
echo "=== 3. CREATE PROJECT ==="
PROJ=$(curl -sf -X POST "$BASE/v1/projects" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"code\":\"HWY-2026\",
    \"name\":\"NH-44 Highway Expansion\",
    \"description\":\"4-lane highway expansion project, 120km stretch\",
    \"epsNodeId\":\"$EPS_CHILD_ID\",
    \"plannedStartDate\":\"2026-04-01\",
    \"plannedFinishDate\":\"2026-12-31\",
    \"priority\":1
  }")
PROJ_ID=$(echo "$PROJ" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ Created project: NH-44 Highway Expansion (id=$PROJ_ID)"

# Verify project
PROJ_CHECK=$(curl -sf "$BASE/v1/projects/$PROJ_ID" -H "Authorization: Bearer $TOKEN")
echo "✅ Project retrieved: $(echo "$PROJ_CHECK" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d['name'])")"

# ── 4. Create WBS nodes ──────────────────────────────
echo ""
echo "=== 4. CREATE WBS ==="

WBS_ROOT=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/wbs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"PRJ\",\"name\":\"Project Level\",\"projectId\":\"$PROJ_ID\"}")
WBS_ROOT_ID=$(echo "$WBS_ROOT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ WBS root created (id=$WBS_ROOT_ID)"

WBS_EARTH=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/wbs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"EARTH\",\"name\":\"Earthwork\",\"parentId\":\"$WBS_ROOT_ID\",\"projectId\":\"$PROJ_ID\"}")
WBS_EARTH_ID=$(echo "$WBS_EARTH" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ WBS: Earthwork (id=$WBS_EARTH_ID)"

WBS_DRAIN=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/wbs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"DRAIN\",\"name\":\"Drainage\",\"parentId\":\"$WBS_ROOT_ID\",\"projectId\":\"$PROJ_ID\"}")
WBS_DRAIN_ID=$(echo "$WBS_DRAIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ WBS: Drainage (id=$WBS_DRAIN_ID)"

WBS_PAVE=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/wbs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"PAVE\",\"name\":\"Pavement\",\"parentId\":\"$WBS_ROOT_ID\",\"projectId\":\"$PROJ_ID\"}")
WBS_PAVE_ID=$(echo "$WBS_PAVE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ WBS: Pavement (id=$WBS_PAVE_ID)"

# Verify WBS tree
WBS_TREE=$(curl -sf "$BASE/v1/projects/$PROJ_ID/wbs" -H "Authorization: Bearer $TOKEN")
WBS_COUNT=$(echo "$WBS_TREE" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']))")
echo "✅ WBS tree has $WBS_COUNT nodes"

# ── 5. Create activities ─────────────────────────────
echo ""
echo "=== 5. CREATE ACTIVITIES ==="

ACT1=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/activities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"code\":\"A100\",
    \"name\":\"Clear & Grub\",
    \"wbsNodeId\":\"$WBS_EARTH_ID\",
    \"originalDuration\":10,
    \"calendarId\":null
  }")
ACT1_ID=$(echo "$ACT1" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ Activity: Clear & Grub — 10 days (id=$ACT1_ID)"

ACT2=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/activities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"code\":\"A200\",
    \"name\":\"Excavation\",
    \"wbsNodeId\":\"$WBS_EARTH_ID\",
    \"originalDuration\":20,
    \"calendarId\":null
  }")
ACT2_ID=$(echo "$ACT2" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ Activity: Excavation — 20 days (id=$ACT2_ID)"

ACT3=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/activities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"code\":\"A300\",
    \"name\":\"Drain Pipe Laying\",
    \"wbsNodeId\":\"$WBS_DRAIN_ID\",
    \"originalDuration\":15,
    \"calendarId\":null
  }")
ACT3_ID=$(echo "$ACT3" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ Activity: Drain Pipe Laying — 15 days (id=$ACT3_ID)"

ACT4=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/activities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"code\":\"A400\",
    \"name\":\"Base Course\",
    \"wbsNodeId\":\"$WBS_PAVE_ID\",
    \"originalDuration\":25,
    \"calendarId\":null
  }")
ACT4_ID=$(echo "$ACT4" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ Activity: Base Course — 25 days (id=$ACT4_ID)"

ACT5=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/activities" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"code\":\"A500\",
    \"name\":\"Asphalt Paving\",
    \"wbsNodeId\":\"$WBS_PAVE_ID\",
    \"originalDuration\":30,
    \"calendarId\":null
  }")
ACT5_ID=$(echo "$ACT5" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "✅ Activity: Asphalt Paving — 30 days (id=$ACT5_ID)"

# ── 6. Create relationships ──────────────────────────
echo ""
echo "=== 6. CREATE RELATIONSHIPS (Finish-to-Start) ==="

curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/relationships" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"predecessorId\":\"$ACT1_ID\",
    \"successorId\":\"$ACT2_ID\",
    \"type\":\"FS\",
    \"lag\":0
  }" > /dev/null
echo "✅ Clear & Grub → Excavation (FS)"

curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/relationships" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"predecessorId\":\"$ACT2_ID\",
    \"successorId\":\"$ACT3_ID\",
    \"type\":\"FS\",
    \"lag\":0
  }" > /dev/null
echo "✅ Excavation → Drain Pipe Laying (FS)"

curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/relationships" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"predecessorId\":\"$ACT2_ID\",
    \"successorId\":\"$ACT4_ID\",
    \"type\":\"FS\",
    \"lag\":0
  }" > /dev/null
echo "✅ Excavation → Base Course (FS)"

curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/relationships" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"predecessorId\":\"$ACT4_ID\",
    \"successorId\":\"$ACT5_ID\",
    \"type\":\"FS\",
    \"lag\":0
  }" > /dev/null
echo "✅ Base Course → Asphalt Paving (FS)"

# Verify relationships
RELS=$(curl -sf "$BASE/v1/projects/$PROJ_ID/relationships" -H "Authorization: Bearer $TOKEN")
RELS_COUNT=$(echo "$RELS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']))")
echo "✅ Total relationships: $RELS_COUNT"

# ── 7. Schedule (critical path) ──────────────────────
echo ""
echo "=== 7. RUN SCHEDULE (Critical Path Analysis) ==="

SCHEDULE=$(curl -sf -X POST "$BASE/v1/projects/$PROJ_ID/schedule" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"dataDate":"2026-04-01"}')
echo "$SCHEDULE" | python3 -m json.tool | head -30

# Critical path
echo ""
echo "=== 7a. CRITICAL PATH ==="
CP=$(curl -sf "$BASE/v1/projects/$PROJ_ID/schedule/critical-path" \
  -H "Authorization: Bearer $TOKEN")
CP_COUNT=$(echo "$CP" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']))")
echo "✅ Critical path has $CP_COUNT activities"
echo "$CP" | python3 -c "
import sys,json
data = json.load(sys.stdin)['data']
for a in data:
    print(f\"  {a.get('code','?'):6} {a.get('name','?'):25} ES={a.get('earlyStartDate','?')} EF={a.get('earlyFinishDate','?')} TF={a.get('totalFloat',0)}\")
"

# ── 8. Query schedule activities ─────────────────────
echo ""
echo "=== 8. SCHEDULED ACTIVITIES ==="
ACTS=$(curl -sf "$BASE/v1/projects/$PROJ_ID/schedule/activities" \
  -H "Authorization: Bearer $TOKEN")
echo "$ACTS" | python3 -c "
import sys,json
data = json.load(sys.stdin)['data']
print(f'  {'Code':6} {'Name':25} {'Dur':>4}  {'Start':12} {'Finish':12} {'Float':>5}')
print('  ' + '-'*72)
for a in data:
    print(f\"  {a.get('code','?'):6} {a.get('name','?'):25} {a.get('originalDuration',0):>4}d {str(a.get('earlyStartDate','?')):12} {str(a.get('earlyFinishDate','?')):12} {a.get('totalFloat',0):>5}\")
"

# ── 9. Summary ───────────────────────────────────────
echo ""
echo "========================================="
echo "  ✅ END-TO-END TEST COMPLETE"
echo "========================================="
echo "  EPS nodes:      $TREE_COUNT"
echo "  Project:        NH-44 Highway Expansion"
echo "  WBS nodes:      $WBS_COUNT"
echo "  Activities:     5"
echo "  Relationships:  $RELS_COUNT"
echo "  Critical path:  $CP_COUNT activities"
echo "========================================="

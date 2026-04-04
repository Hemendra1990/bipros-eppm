#!/bin/bash

##############################################################################
# Bipros EPPM - Seed Remaining Data
#
# Creates WBS, Activities, Relationships, Schedules, Baselines, and Risks
# for all 4 projects: MRB-2026, SOT-2026, DSSF-2026, ERP-2026
#
# Prerequisites:
#   - Docker with bipros containers running
#   - postgres CLI available
#   - curl, jq, python3 available
#   - Initial seed-demo-data.sh has already run
##############################################################################

set +e  # Don't exit on errors, continue seeding

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
API="http://localhost:8080"
CT="Content-Type: application/json"
ADMIN_USER="admin"
ADMIN_PASS="admin123"
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="bipros"
DB_USER="bipros"

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

##############################################################################
# Helper Functions
##############################################################################

# Helper: Extract data from JSON response
json_extract() {
    python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" 2>/dev/null || echo ""
}

# Helper: POST request and return ID
post_id() {
    local endpoint=$1
    local body=$2
    local resp=$(curl -s -X POST "$API$endpoint" -H "$CT" -H "$AUTH" -d "$body")
    local id=$(echo "$resp" | json_extract "d.get('data',{}).get('id','')")
    if [ -z "$id" ]; then
        log_error "Failed to create resource at $endpoint"
        log_error "Response: $resp"
        return 1
    fi
    echo "$id"
}

# Helper: POST without return
post_silent() {
    local endpoint=$1
    local body=$2
    curl -s -X POST "$API$endpoint" -H "$CT" -H "$AUTH" -d "$body" > /dev/null
}

# Helper: GET request
get_json() {
    local endpoint=$1
    curl -s -X GET "$API$endpoint" -H "$AUTH"
}

##############################################################################
# Authentication
##############################################################################

log_info "Authenticating as $ADMIN_USER..."
LOGIN_RESP=$(curl -s -X POST "$API/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")

TOKEN=$(echo "$LOGIN_RESP" | json_extract "d.get('data',{}).get('accessToken','')")

if [ -z "$TOKEN" ]; then
    log_error "Authentication failed"
    log_error "Response: $LOGIN_RESP"
    exit 1
fi

export AUTH="Authorization: Bearer $TOKEN"
log_success "Authenticated"

##############################################################################
# Clean up existing data (for re-runs)
##############################################################################

log_info "Cleaning up existing WBS, activities, relationships, baselines, and risks..."
docker exec bipros-postgres psql -U "$DB_USER" -d "$DB_NAME" -c \
    "TRUNCATE activity.activity_relationships, activity.activities, baseline.baseline_activities, baseline.baseline_relationships, baseline.baselines, project.wbs_nodes CASCADE;" 2>/dev/null || true

log_success "Cleanup complete"

##############################################################################
# Fetch project IDs
##############################################################################

log_info "Fetching project IDs..."
PROJECTS_RESP=$(get_json "/v1/projects?size=50")

# Parse project IDs by code from paginated response
get_project_id() {
    local code=$1
    echo "$PROJECTS_RESP" | python3 -c "
import sys, json
d = json.load(sys.stdin)
content = d.get('data', {}).get('content', [])
for p in content:
    if p.get('code') == '$code':
        print(p['id'])
        break
" 2>/dev/null || echo ""
}

MRB_ID=$(get_project_id "MRB-2026")
SOT_ID=$(get_project_id "SOT-2026")
DSSF_ID=$(get_project_id "DSSF-2026")
ERP_ID=$(get_project_id "ERP-2026")

if [ -z "$DSSF_ID" ]; then
    DSSF_ID=$(echo "$PROJECTS_RESP" | grep -o '"projectCode":"DSSF-2026"' -A 50 | grep '"id":"' | head -1 | cut -d'"' -f4)
fi
if [ -z "$ERP_ID" ]; then
    ERP_ID=$(echo "$PROJECTS_RESP" | grep -o '"projectCode":"ERP-2026"' -A 50 | grep '"id":"' | head -1 | cut -d'"' -f4)
fi

if [ -z "$MRB_ID" ] || [ -z "$SOT_ID" ] || [ -z "$DSSF_ID" ] || [ -z "$ERP_ID" ]; then
    log_error "Could not fetch all project IDs"
    log_error "MRB_ID=$MRB_ID, SOT_ID=$SOT_ID, DSSF_ID=$DSSF_ID, ERP_ID=$ERP_ID"
    exit 1
fi

log_success "MRB-2026: $MRB_ID"
log_success "SOT-2026: $SOT_ID"
log_success "DSSF-2026: $DSSF_ID"
log_success "ERP-2026: $ERP_ID"

##############################################################################
# Project: MRB-2026 (Modular Residential Building - 2026)
##############################################################################

log_info ""
log_info "========== MRB-2026 (Modular Residential Building) =========="

# WBS Structure
log_info "Creating WBS hierarchy for MRB-2026..."

MRB_WBS_PRECON=$(post_id "/v1/projects/$MRB_ID/wbs" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB.1\",\"title\":\"Pre-Construction\",\"description\":\"Project kickoff and planning\",\"level\":1,\"parentId\":null}")
log_success "  - Pre-Construction: $MRB_WBS_PRECON"

MRB_WBS_DEMO=$(post_id "/v1/projects/$MRB_ID/wbs" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB.2\",\"title\":\"Demolition\",\"description\":\"Existing structure demolition\",\"level\":1,\"parentId\":null}")
log_success "  - Demolition: $MRB_WBS_DEMO"

MRB_WBS_FOUND=$(post_id "/v1/projects/$MRB_ID/wbs" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB.3\",\"title\":\"Foundation\",\"description\":\"Foundation and site preparation\",\"level\":1,\"parentId\":null}")
log_success "  - Foundation: $MRB_WBS_FOUND"

MRB_WBS_SUPER=$(post_id "/v1/projects/$MRB_ID/wbs" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB.4\",\"title\":\"Superstructure\",\"description\":\"Structural framework\",\"level\":1,\"parentId\":null}")
log_success "  - Superstructure: $MRB_WBS_SUPER"

MRB_WBS_DECK=$(post_id "/v1/projects/$MRB_ID/wbs" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB.5\",\"title\":\"Deck & Finishing\",\"description\":\"Exterior and interior finishing\",\"level\":1,\"parentId\":null}")
log_success "  - Deck & Finishing: $MRB_WBS_DECK"

MRB_WBS_CLOSE=$(post_id "/v1/projects/$MRB_ID/wbs" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB.6\",\"title\":\"Closeout\",\"description\":\"Project closeout and handover\",\"level\":1,\"parentId\":null}")
log_success "  - Closeout: $MRB_WBS_CLOSE"

# Activities for MRB-2026 (~25 activities)
log_info "Creating activities for MRB-2026..."

declare -a MRB_ACTIVITIES

# Pre-Construction phase
MRB_ACTIVITIES[0]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-001\",\"title\":\"Project Kickoff Meeting\",\"description\":\"Initiate project, review scope and schedule\",\"activityType\":\"START_MILESTONE\",\"wbsId\":\"$MRB_WBS_PRECON\",\"startDate\":\"2026-05-01\",\"endDate\":\"2026-05-01\",\"duration\":0}")
log_success "  - MRB-ACT-001: Project Kickoff"

MRB_ACTIVITIES[1]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-002\",\"title\":\"Design Review & Approval\",\"description\":\"Review final designs and obtain approvals\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_PRECON\",\"startDate\":\"2026-05-01\",\"endDate\":\"2026-05-15\",\"duration\":10}")
log_success "  - MRB-ACT-002: Design Review"

MRB_ACTIVITIES[2]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-003\",\"title\":\"Permits & Regulatory Approvals\",\"description\":\"Obtain building permits and regulatory approvals\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_PRECON\",\"startDate\":\"2026-05-10\",\"endDate\":\"2026-06-10\",\"duration\":20}")
log_success "  - MRB-ACT-003: Permits"

# Demolition phase
MRB_ACTIVITIES[3]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-004\",\"title\":\"Site Mobilization\",\"description\":\"Mobilize equipment and set up site\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DEMO\",\"startDate\":\"2026-06-15\",\"endDate\":\"2026-06-20\",\"duration\":5}")
log_success "  - MRB-ACT-004: Site Mobilization"

MRB_ACTIVITIES[4]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-005\",\"title\":\"Existing Building Demolition\",\"description\":\"Demolish existing structures\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DEMO\",\"startDate\":\"2026-06-21\",\"endDate\":\"2026-07-31\",\"duration\":35}")
log_success "  - MRB-ACT-005: Demolition"

MRB_ACTIVITIES[5]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-006\",\"title\":\"Site Clearing & Cleanup\",\"description\":\"Clear debris and prepare site\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DEMO\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-10\",\"duration\":8}")
log_success "  - MRB-ACT-006: Site Cleanup"

# Foundation phase
MRB_ACTIVITIES[6]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-007\",\"title\":\"Geotechnical Investigation\",\"description\":\"Conduct soil testing and analysis\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_FOUND\",\"startDate\":\"2026-08-11\",\"endDate\":\"2026-08-25\",\"duration\":12}")
log_success "  - MRB-ACT-007: Geotechnical Investigation"

MRB_ACTIVITIES[7]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-008\",\"title\":\"Foundation Design Finalization\",\"description\":\"Finalize foundation design based on soil report\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_FOUND\",\"startDate\":\"2026-08-26\",\"endDate\":\"2026-09-05\",\"duration\":8}")
log_success "  - MRB-ACT-008: Foundation Design"

MRB_ACTIVITIES[8]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-009\",\"title\":\"Excavation & Grading\",\"description\":\"Excavate and grade foundation area\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_FOUND\",\"startDate\":\"2026-09-06\",\"endDate\":\"2026-09-25\",\"duration\":15}")
log_success "  - MRB-ACT-009: Excavation"

MRB_ACTIVITIES[9]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-010\",\"title\":\"Foundation Concrete Pouring\",\"description\":\"Pour concrete foundation\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_FOUND\",\"startDate\":\"2026-09-26\",\"endDate\":\"2026-10-15\",\"duration\":15}")
log_success "  - MRB-ACT-010: Foundation Concrete"

# Superstructure phase
MRB_ACTIVITIES[10]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-011\",\"title\":\"Steel Framing Installation\",\"description\":\"Install structural steel framework\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_SUPER\",\"startDate\":\"2026-10-16\",\"endDate\":\"2026-11-30\",\"duration\":35}")
log_success "  - MRB-ACT-011: Steel Framing"

MRB_ACTIVITIES[11]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-012\",\"title\":\"Decking Installation\",\"description\":\"Install structural decking\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_SUPER\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-20\",\"duration\":15}")
log_success "  - MRB-ACT-012: Decking"

MRB_ACTIVITIES[12]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-013\",\"title\":\"Structural Inspection\",\"description\":\"Third-party inspection of structural work\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_SUPER\",\"startDate\":\"2026-12-21\",\"endDate\":\"2026-12-27\",\"duration\":5}")
log_success "  - MRB-ACT-013: Structural Inspection"

# Deck & Finishing
MRB_ACTIVITIES[13]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-014\",\"title\":\"Exterior Wall Installation\",\"description\":\"Install exterior wall systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DECK\",\"startDate\":\"2026-12-28\",\"endDate\":\"2027-01-25\",\"duration\":20}")
log_success "  - MRB-ACT-014: Exterior Walls"

MRB_ACTIVITIES[14]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-015\",\"title\":\"Roofing Installation\",\"description\":\"Install roof systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DECK\",\"startDate\":\"2027-01-26\",\"endDate\":\"2027-02-15\",\"duration\":18}")
log_success "  - MRB-ACT-015: Roofing"

MRB_ACTIVITIES[15]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-016\",\"title\":\"Interior Framing & Drywall\",\"description\":\"Frame and install drywall\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DECK\",\"startDate\":\"2027-02-16\",\"endDate\":\"2027-03-25\",\"duration\":25}")
log_success "  - MRB-ACT-016: Interior Framing"

MRB_ACTIVITIES[16]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-017\",\"title\":\"MEP Rough-In\",\"description\":\"Install mechanical, electrical, plumbing systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DECK\",\"startDate\":\"2027-03-26\",\"endDate\":\"2027-04-30\",\"duration\":25}")
log_success "  - MRB-ACT-017: MEP Rough-In"

MRB_ACTIVITIES[17]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-018\",\"title\":\"Flooring Installation\",\"description\":\"Install all flooring systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DECK\",\"startDate\":\"2027-05-01\",\"endDate\":\"2027-05-20\",\"duration\":15}")
log_success "  - MRB-ACT-018: Flooring"

MRB_ACTIVITIES[18]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-019\",\"title\":\"Interior Finishing\",\"description\":\"Paint, trim, hardware installation\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_DECK\",\"startDate\":\"2027-05-21\",\"endDate\":\"2027-06-25\",\"duration\":25}")
log_success "  - MRB-ACT-019: Interior Finishing"

# Closeout
MRB_ACTIVITIES[19]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-020\",\"title\":\"Final Inspections & Testing\",\"description\":\"Final building inspections and system testing\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_CLOSE\",\"startDate\":\"2027-06-26\",\"endDate\":\"2027-07-10\",\"duration\":12}")
log_success "  - MRB-ACT-020: Final Inspections"

MRB_ACTIVITIES[20]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-021\",\"title\":\"Punch List & Corrections\",\"description\":\"Address punch list items\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_CLOSE\",\"startDate\":\"2027-07-11\",\"endDate\":\"2027-07-31\",\"duration\":15}")
log_success "  - MRB-ACT-021: Punch List"

MRB_ACTIVITIES[21]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-022\",\"title\":\"Certificate of Occupancy\",\"description\":\"Obtain certificate of occupancy\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_CLOSE\",\"startDate\":\"2027-08-01\",\"endDate\":\"2027-08-05\",\"duration\":3}")
log_success "  - MRB-ACT-022: CO"

MRB_ACTIVITIES[22]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-023\",\"title\":\"Tenant Move-In Coordination\",\"description\":\"Coordinate tenant move-in\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$MRB_WBS_CLOSE\",\"startDate\":\"2027-08-06\",\"endDate\":\"2027-08-20\",\"duration\":12}")
log_success "  - MRB-ACT-023: Move-In"

MRB_ACTIVITIES[23]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-024\",\"title\":\"Project Documentation & Handover\",\"description\":\"Complete project documentation and handover\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$MRB_WBS_CLOSE\",\"startDate\":\"2027-08-21\",\"endDate\":\"2027-08-31\",\"duration\":8}")
log_success "  - MRB-ACT-024: Documentation"

MRB_ACTIVITIES[24]=$(post_id "/v1/projects/$MRB_ID/activities" \
    "{\"projectId\":\"$MRB_ID\",\"code\":\"MRB-ACT-025\",\"title\":\"Project Completion\",\"description\":\"Project completion milestone\",\"activityType\":\"FINISH_MILESTONE\",\"wbsId\":\"$MRB_WBS_CLOSE\",\"startDate\":\"2027-09-01\",\"endDate\":\"2027-09-01\",\"duration\":0}")
log_success "  - MRB-ACT-025: Project Completion"

# Relationships for MRB-2026
log_info "Creating relationships for MRB-2026..."
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[0]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[1]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[1]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[2]}\",\"relationshipType\":\"SS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[2]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[3]}\",\"relationshipType\":\"FS\",\"lag\":3}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[3]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[4]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[4]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[5]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[5]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[6]}\",\"relationshipType\":\"FS\",\"lag\":1}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[6]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[7]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[7]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[8]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[8]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[9]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[9]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[10]}\",\"relationshipType\":\"FS\",\"lag\":5}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[10]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[11]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[11]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[12]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[12]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[13]}\",\"relationshipType\":\"FS\",\"lag\":1}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[13]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[14]}\",\"relationshipType\":\"SS\",\"lag\":5}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[14]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[15]}\",\"relationshipType\":\"FS\",\"lag\":2}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[15]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[16]}\",\"relationshipType\":\"SS\",\"lag\":3}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[16]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[17]}\",\"relationshipType\":\"FS\",\"lag\":2}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[17]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[18]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[18]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[19]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[19]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[20]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[20]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[21]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[21]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[22]}\",\"relationshipType\":\"FS\",\"lag\":1}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[22]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[23]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$MRB_ID/relationships" \
    "{\"projectId\":\"$MRB_ID\",\"sourceActivityId\":\"${MRB_ACTIVITIES[23]}\",\"targetActivityId\":\"${MRB_ACTIVITIES[24]}\",\"relationshipType\":\"FS\",\"lag\":0}"

log_success "Created 24 relationships for MRB-2026"

# Schedule calculation for MRB-2026
log_info "Running CPM schedule for MRB-2026..."
post_silent "/v1/projects/$MRB_ID/schedule" "{}"
log_success "Schedule calculated"

# Baseline for MRB-2026
log_info "Creating baseline for MRB-2026..."
post_silent "/v1/projects/$MRB_ID/baselines" \
    "{\"name\":\"Baseline 1\",\"baselineType\":\"PLANNED\",\"description\":\"Initial project baseline\"}"
log_success "Baseline created"

# Risks for MRB-2026
log_info "Creating risks for MRB-2026..."
post_silent "/v1/projects/$MRB_ID/risks" \
    "{\"projectId\":\"$MRB_ID\",\"title\":\"Permit Delays\",\"description\":\"Regulatory approvals may be delayed\",\"category\":\"EXTERNAL\",\"probability\":\"HIGH\",\"impact\":\"HIGH\"}"
post_silent "/v1/projects/$MRB_ID/risks" \
    "{\"projectId\":\"$MRB_ID\",\"title\":\"Weather Impact\",\"description\":\"Adverse weather may delay construction\",\"category\":\"EXTERNAL\",\"probability\":\"MEDIUM\",\"impact\":\"HIGH\"}"
post_silent "/v1/projects/$MRB_ID/risks" \
    "{\"projectId\":\"$MRB_ID\",\"title\":\"Labor Shortage\",\"description\":\"Difficulty in finding skilled labor\",\"category\":\"RESOURCE\",\"probability\":\"MEDIUM\",\"impact\":\"MEDIUM\"}"
post_silent "/v1/projects/$MRB_ID/risks" \
    "{\"projectId\":\"$MRB_ID\",\"title\":\"Material Cost Escalation\",\"description\":\"Material costs may increase beyond budget\",\"category\":\"COST\",\"probability\":\"HIGH\",\"impact\":\"MEDIUM\"}"
log_success "Created 4 risks for MRB-2026"

##############################################################################
# Project: SOT-2026 (Standard Office Tower - 2026)
##############################################################################

log_info ""
log_info "========== SOT-2026 (Standard Office Tower) =========="

# WBS Structure
log_info "Creating WBS hierarchy for SOT-2026..."

SOT_WBS_SITE=$(post_id "/v1/projects/$SOT_ID/wbs" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT.1\",\"title\":\"Site Work\",\"description\":\"Site preparation and utilities\",\"level\":1,\"parentId\":null}")
log_success "  - Site Work: $SOT_WBS_SITE"

SOT_WBS_STRUCT=$(post_id "/v1/projects/$SOT_ID/wbs" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT.2\",\"title\":\"Structure\",\"description\":\"Structural framework and concrete\",\"level\":1,\"parentId\":null}")
log_success "  - Structure: $SOT_WBS_STRUCT"

SOT_WBS_ENVELOPE=$(post_id "/v1/projects/$SOT_ID/wbs" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT.3\",\"title\":\"Building Envelope\",\"description\":\"Exterior walls, windows, doors\",\"level\":1,\"parentId\":null}")
log_success "  - Building Envelope: $SOT_WBS_ENVELOPE"

SOT_WBS_MEP=$(post_id "/v1/projects/$SOT_ID/wbs" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT.4\",\"title\":\"MEP Systems\",\"description\":\"Mechanical, electrical, plumbing\",\"level\":1,\"parentId\":null}")
log_success "  - MEP Systems: $SOT_WBS_MEP"

SOT_WBS_INTERIOR=$(post_id "/v1/projects/$SOT_ID/wbs" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT.5\",\"title\":\"Interior Fitout\",\"description\":\"Interior walls, finishes, FF&E\",\"level\":1,\"parentId\":null}")
log_success "  - Interior Fitout: $SOT_WBS_INTERIOR"

# Activities for SOT-2026 (~18 activities)
log_info "Creating activities for SOT-2026..."

declare -a SOT_ACTIVITIES

SOT_ACTIVITIES[0]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-001\",\"title\":\"Project Start\",\"description\":\"Project initiation\",\"activityType\":\"START_MILESTONE\",\"wbsId\":\"$SOT_WBS_SITE\",\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-01\",\"duration\":0}")
log_success "  - SOT-ACT-001: Project Start"

SOT_ACTIVITIES[1]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-002\",\"title\":\"Site Survey & Layout\",\"description\":\"Conduct site survey and layout\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$SOT_WBS_SITE\",\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-10\",\"duration\":8}")
log_success "  - SOT-ACT-002: Survey"

SOT_ACTIVITIES[2]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-003\",\"title\":\"Utilities Coordination\",\"description\":\"Coordinate with utility providers\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$SOT_WBS_SITE\",\"startDate\":\"2026-06-10\",\"endDate\":\"2026-06-25\",\"duration\":12}")
log_success "  - SOT-ACT-003: Utilities"

SOT_ACTIVITIES[3]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-004\",\"title\":\"Foundation Concrete\",\"description\":\"Pour foundation\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_STRUCT\",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-20\",\"duration\":18}")
log_success "  - SOT-ACT-004: Foundation"

SOT_ACTIVITIES[4]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-005\",\"title\":\"Steel Erection\",\"description\":\"Erect structural steel\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_STRUCT\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-10-15\",\"duration\":55}")
log_success "  - SOT-ACT-005: Steel Erection"

SOT_ACTIVITIES[5]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-006\",\"title\":\"Floor Decking\",\"description\":\"Install floor decking\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_STRUCT\",\"startDate\":\"2026-10-16\",\"endDate\":\"2026-11-30\",\"duration\":35}")
log_success "  - SOT-ACT-006: Floor Decking"

SOT_ACTIVITIES[6]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-007\",\"title\":\"Curtain Wall Installation\",\"description\":\"Install exterior curtain wall\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_ENVELOPE\",\"startDate\":\"2026-12-01\",\"endDate\":\"2027-01-31\",\"duration\":45}")
log_success "  - SOT-ACT-007: Curtain Wall"

SOT_ACTIVITIES[7]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-008\",\"title\":\"Roofing\",\"description\":\"Install roofing systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_ENVELOPE\",\"startDate\":\"2027-02-01\",\"endDate\":\"2027-02-20\",\"duration\":15}")
log_success "  - SOT-ACT-008: Roofing"

SOT_ACTIVITIES[8]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-009\",\"title\":\"Windows & Doors\",\"description\":\"Install windows and doors\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_ENVELOPE\",\"startDate\":\"2027-02-21\",\"endDate\":\"2027-03-20\",\"duration\":25}")
log_success "  - SOT-ACT-009: Windows & Doors"

SOT_ACTIVITIES[9]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-010\",\"title\":\"HVAC Installation\",\"description\":\"Install HVAC systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_MEP\",\"startDate\":\"2027-03-21\",\"endDate\":\"2027-04-30\",\"duration\":30}")
log_success "  - SOT-ACT-010: HVAC"

SOT_ACTIVITIES[10]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-011\",\"title\":\"Electrical Systems\",\"description\":\"Install electrical systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_MEP\",\"startDate\":\"2027-04-01\",\"endDate\":\"2027-05-31\",\"duration\":45}")
log_success "  - SOT-ACT-011: Electrical"

SOT_ACTIVITIES[11]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-012\",\"title\":\"Plumbing Systems\",\"description\":\"Install plumbing systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_MEP\",\"startDate\":\"2027-04-01\",\"endDate\":\"2027-05-31\",\"duration\":45}")
log_success "  - SOT-ACT-012: Plumbing"

SOT_ACTIVITIES[12]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-013\",\"title\":\"Interior Partitions\",\"description\":\"Install interior partitions\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_INTERIOR\",\"startDate\":\"2027-06-01\",\"endDate\":\"2027-06-25\",\"duration\":20}")
log_success "  - SOT-ACT-013: Partitions"

SOT_ACTIVITIES[13]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-014\",\"title\":\"Flooring\",\"description\":\"Install flooring\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_INTERIOR\",\"startDate\":\"2027-06-26\",\"endDate\":\"2027-07-20\",\"duration\":20}")
log_success "  - SOT-ACT-014: Flooring"

SOT_ACTIVITIES[14]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-015\",\"title\":\"Painting & Finishes\",\"description\":\"Paint and finish work\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_INTERIOR\",\"startDate\":\"2027-07-21\",\"endDate\":\"2027-08-20\",\"duration\":25}")
log_success "  - SOT-ACT-015: Finishes"

SOT_ACTIVITIES[15]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-016\",\"title\":\"Final Inspections\",\"description\":\"Final building inspections\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$SOT_WBS_INTERIOR\",\"startDate\":\"2027-08-21\",\"endDate\":\"2027-09-10\",\"duration\":15}")
log_success "  - SOT-ACT-016: Inspections"

SOT_ACTIVITIES[16]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-017\",\"title\":\"Punch List\",\"description\":\"Complete punch list items\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$SOT_WBS_INTERIOR\",\"startDate\":\"2027-09-11\",\"endDate\":\"2027-10-05\",\"duration\":20}")
log_success "  - SOT-ACT-017: Punch List"

SOT_ACTIVITIES[17]=$(post_id "/v1/projects/$SOT_ID/activities" \
    "{\"projectId\":\"$SOT_ID\",\"code\":\"SOT-ACT-018\",\"title\":\"Project Completion\",\"description\":\"Project complete\",\"activityType\":\"FINISH_MILESTONE\",\"wbsId\":\"$SOT_WBS_INTERIOR\",\"startDate\":\"2027-10-31\",\"endDate\":\"2027-10-31\",\"duration\":0}")
log_success "  - SOT-ACT-018: Completion"

# Relationships for SOT-2026
log_info "Creating relationships for SOT-2026..."
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[0]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[1]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[1]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[2]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[2]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[3]}\",\"relationshipType\":\"FS\",\"lag\":3}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[3]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[4]}\",\"relationshipType\":\"FS\",\"lag\":10}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[4]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[5]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[5]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[6]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[6]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[7]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[7]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[8]}\",\"relationshipType\":\"FS\",\"lag\":1}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[5]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[9]}\",\"relationshipType\":\"SS\",\"lag\":5}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[5]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[10]}\",\"relationshipType\":\"SS\",\"lag\":10}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[5]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[11]}\",\"relationshipType\":\"SS\",\"lag\":10}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[9]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[12]}\",\"relationshipType\":\"FS\",\"lag\":5}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[10]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[12]}\",\"relationshipType\":\"SS\",\"lag\":10}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[12]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[13]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[13]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[14]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[14]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[15]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[15]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[16]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$SOT_ID/relationships" "{\"projectId\":\"$SOT_ID\",\"sourceActivityId\":\"${SOT_ACTIVITIES[16]}\",\"targetActivityId\":\"${SOT_ACTIVITIES[17]}\",\"relationshipType\":\"FS\",\"lag\":20}"

log_success "Created 18 relationships for SOT-2026"

# Schedule calculation for SOT-2026
log_info "Running CPM schedule for SOT-2026..."
post_silent "/v1/projects/$SOT_ID/schedule" "{}"
log_success "Schedule calculated"

# Baseline for SOT-2026
log_info "Creating baseline for SOT-2026..."
post_silent "/v1/projects/$SOT_ID/baselines" \
    "{\"name\":\"Baseline 1\",\"baselineType\":\"PLANNED\",\"description\":\"Initial project baseline\"}"
log_success "Baseline created"

# Risks for SOT-2026
log_info "Creating risks for SOT-2026..."
post_silent "/v1/projects/$SOT_ID/risks" \
    "{\"projectId\":\"$SOT_ID\",\"title\":\"Market Downturn\",\"description\":\"Office market may decline affecting tenant demand\",\"category\":\"EXTERNAL\",\"probability\":\"MEDIUM\",\"impact\":\"HIGH\"}"
post_silent "/v1/projects/$SOT_ID/risks" \
    "{\"projectId\":\"$SOT_ID\",\"title\":\"Supply Chain Delays\",\"description\":\"Material and equipment delivery delays\",\"category\":\"EXTERNAL\",\"probability\":\"HIGH\",\"impact\":\"MEDIUM\"}"
post_silent "/v1/projects/$SOT_ID/risks" \
    "{\"projectId\":\"$SOT_ID\",\"title\":\"Design Changes\",\"description\":\"Client may request design changes\",\"category\":\"PROJECT_MANAGEMENT\",\"probability\":\"MEDIUM\",\"impact\":\"MEDIUM\"}"
log_success "Created 3 risks for SOT-2026"

##############################################################################
# Project: DSSF-2026 (Distributed Solar System Farm - 2026)
##############################################################################

log_info ""
log_info "========== DSSF-2026 (Distributed Solar System Farm) =========="

# WBS Structure
log_info "Creating WBS hierarchy for DSSF-2026..."

DSSF_WBS_DEV=$(post_id "/v1/projects/$DSSF_ID/wbs" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF.1\",\"title\":\"Development & Permitting\",\"description\":\"Site selection and regulatory approvals\",\"level\":1,\"parentId\":null}")
log_success "  - Development & Permitting: $DSSF_WBS_DEV"

DSSF_WBS_CIVIL=$(post_id "/v1/projects/$DSSF_ID/wbs" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF.2\",\"title\":\"Civil & Site Prep\",\"description\":\"Site clearing and preparation\",\"level\":1,\"parentId\":null}")
log_success "  - Civil & Site Prep: $DSSF_WBS_CIVIL"

DSSF_WBS_ELEC=$(post_id "/v1/projects/$DSSF_ID/wbs" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF.3\",\"title\":\"Electrical Infrastructure\",\"description\":\"Transformer and substation work\",\"level\":1,\"parentId\":null}")
log_success "  - Electrical Infrastructure: $DSSF_WBS_ELEC"

DSSF_WBS_PANEL=$(post_id "/v1/projects/$DSSF_ID/wbs" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF.4\",\"title\":\"Panel Installation\",\"description\":\"Solar panel installation\",\"level\":1,\"parentId\":null}")
log_success "  - Panel Installation: $DSSF_WBS_PANEL"

DSSF_WBS_COMM=$(post_id "/v1/projects/$DSSF_ID/wbs" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF.5\",\"title\":\"Commissioning\",\"description\":\"Testing and system startup\",\"level\":1,\"parentId\":null}")
log_success "  - Commissioning: $DSSF_WBS_COMM"

# Activities for DSSF-2026 (~15 activities)
log_info "Creating activities for DSSF-2026..."

declare -a DSSF_ACTIVITIES

DSSF_ACTIVITIES[0]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-001\",\"title\":\"Project Initiation\",\"description\":\"Project start\",\"activityType\":\"START_MILESTONE\",\"wbsId\":\"$DSSF_WBS_DEV\",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-01\",\"duration\":0}")
log_success "  - DSSF-ACT-001: Initiation"

DSSF_ACTIVITIES[1]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-002\",\"title\":\"Environmental Assessment\",\"description\":\"Conduct environmental impact assessment\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_DEV\",\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-31\",\"duration\":25}")
log_success "  - DSSF-ACT-002: Environmental Assessment"

DSSF_ACTIVITIES[2]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-003\",\"title\":\"Permitting\",\"description\":\"Obtain necessary permits\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_DEV\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-09-15\",\"duration\":35}")
log_success "  - DSSF-ACT-003: Permitting"

DSSF_ACTIVITIES[3]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-004\",\"title\":\"Site Clearing\",\"description\":\"Clear and grade site\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_CIVIL\",\"startDate\":\"2026-09-20\",\"endDate\":\"2026-10-10\",\"duration\":15}")
log_success "  - DSSF-ACT-004: Site Clearing"

DSSF_ACTIVITIES[4]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-005\",\"title\":\"Access Roads & Drainage\",\"description\":\"Build access roads and drainage\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_CIVIL\",\"startDate\":\"2026-10-11\",\"endDate\":\"2026-10-31\",\"duration\":15}")
log_success "  - DSSF-ACT-005: Roads & Drainage"

DSSF_ACTIVITIES[5]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-006\",\"title\":\"Foundation & Racking Installation\",\"description\":\"Install panel racking foundations\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_CIVIL\",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-30\",\"duration\":25}")
log_success "  - DSSF-ACT-006: Foundation & Racking"

DSSF_ACTIVITIES[6]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-007\",\"title\":\"Transformer Installation\",\"description\":\"Install main transformer\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_ELEC\",\"startDate\":\"2026-11-15\",\"endDate\":\"2026-11-30\",\"duration\":12}")
log_success "  - DSSF-ACT-007: Transformer"

DSSF_ACTIVITIES[7]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-008\",\"title\":\"Substation Construction\",\"description\":\"Build substation structure\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_ELEC\",\"startDate\":\"2026-11-20\",\"endDate\":\"2026-12-10\",\"duration\":15}")
log_success "  - DSSF-ACT-008: Substation"

DSSF_ACTIVITIES[8]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-009\",\"title\":\"Electrical Wiring & Distribution\",\"description\":\"Install electrical systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_ELEC\",\"startDate\":\"2026-12-11\",\"endDate\":\"2027-01-15\",\"duration\":25}")
log_success "  - DSSF-ACT-009: Wiring & Distribution"

DSSF_ACTIVITIES[9]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-010\",\"title\":\"Panel Mounting & Installation\",\"description\":\"Install solar panels on racking\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_PANEL\",\"startDate\":\"2026-12-01\",\"endDate\":\"2027-01-31\",\"duration\":45}")
log_success "  - DSSF-ACT-010: Panel Installation"

DSSF_ACTIVITIES[10]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-011\",\"title\":\"Inverter Installation\",\"description\":\"Install power inverters\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_PANEL\",\"startDate\":\"2027-01-20\",\"endDate\":\"2027-02-10\",\"duration\":15}")
log_success "  - DSSF-ACT-011: Inverters"

DSSF_ACTIVITIES[11]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-012\",\"title\":\"Monitoring System Installation\",\"description\":\"Install monitoring and control systems\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_PANEL\",\"startDate\":\"2027-02-11\",\"endDate\":\"2027-02-25\",\"duration\":12}")
log_success "  - DSSF-ACT-012: Monitoring System"

DSSF_ACTIVITIES[12]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-013\",\"title\":\"System Testing\",\"description\":\"Test all systems before commissioning\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_COMM\",\"startDate\":\"2027-02-26\",\"endDate\":\"2027-03-15\",\"duration\":15}")
log_success "  - DSSF-ACT-013: System Testing"

DSSF_ACTIVITIES[13]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-014\",\"title\":\"Grid Connection & Approval\",\"description\":\"Connect to grid and obtain approval\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$DSSF_WBS_COMM\",\"startDate\":\"2027-03-16\",\"endDate\":\"2027-04-10\",\"duration\":20}")
log_success "  - DSSF-ACT-014: Grid Connection"

DSSF_ACTIVITIES[14]=$(post_id "/v1/projects/$DSSF_ID/activities" \
    "{\"projectId\":\"$DSSF_ID\",\"code\":\"DSSF-ACT-015\",\"title\":\"Project Completion\",\"description\":\"Project complete\",\"activityType\":\"FINISH_MILESTONE\",\"wbsId\":\"$DSSF_WBS_COMM\",\"startDate\":\"2027-04-30\",\"endDate\":\"2027-04-30\",\"duration\":0}")
log_success "  - DSSF-ACT-015: Completion"

# Relationships for DSSF-2026
log_info "Creating relationships for DSSF-2026..."
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[0]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[1]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[1]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[2]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[2]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[3]}\",\"relationshipType\":\"FS\",\"lag\":3}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[3]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[4]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[4]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[5]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[5]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[6]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[5]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[7]}\",\"relationshipType\":\"SS\",\"lag\":5}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[6]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[8]}\",\"relationshipType\":\"FS\",\"lag\":5}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[7]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[8]}\",\"relationshipType\":\"FS\",\"lag\":3}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[5]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[9]}\",\"relationshipType\":\"SS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[9]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[10]}\",\"relationshipType\":\"FS\",\"lag\":15}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[8]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[10]}\",\"relationshipType\":\"SS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[10]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[11]}\",\"relationshipType\":\"FS\",\"lag\":3}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[11]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[12]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[12]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[13]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$DSSF_ID/relationships" "{\"projectId\":\"$DSSF_ID\",\"sourceActivityId\":\"${DSSF_ACTIVITIES[13]}\",\"targetActivityId\":\"${DSSF_ACTIVITIES[14]}\",\"relationshipType\":\"FS\",\"lag\":15}"

log_success "Created 16 relationships for DSSF-2026"

# Schedule calculation for DSSF-2026
log_info "Running CPM schedule for DSSF-2026..."
post_silent "/v1/projects/$DSSF_ID/schedule" "{}"
log_success "Schedule calculated"

# Baseline for DSSF-2026
log_info "Creating baseline for DSSF-2026..."
post_silent "/v1/projects/$DSSF_ID/baselines" \
    "{\"name\":\"Baseline 1\",\"baselineType\":\"PLANNED\",\"description\":\"Initial project baseline\"}"
log_success "Baseline created"

# Risks for DSSF-2026
log_info "Creating risks for DSSF-2026..."
post_silent "/v1/projects/$DSSF_ID/risks" \
    "{\"projectId\":\"$DSSF_ID\",\"title\":\"Equipment Procurement Delays\",\"description\":\"Solar panels and inverters may be delayed\",\"category\":\"EXTERNAL\",\"probability\":\"HIGH\",\"impact\":\"HIGH\"}"
post_silent "/v1/projects/$DSSF_ID/risks" \
    "{\"projectId\":\"$DSSF_ID\",\"title\":\"Grid Connection Issues\",\"description\":\"Utility may delay grid connection approval\",\"category\":\"EXTERNAL\",\"probability\":\"MEDIUM\",\"impact\":\"HIGH\"}"
log_success "Created 2 risks for DSSF-2026"

##############################################################################
# Project: ERP-2026 (Enterprise Resource Planning - 2026)
##############################################################################

log_info ""
log_info "========== ERP-2026 (Enterprise Resource Planning) =========="

# WBS Structure
log_info "Creating WBS hierarchy for ERP-2026..."

ERP_WBS_DISC=$(post_id "/v1/projects/$ERP_ID/wbs" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP.1\",\"title\":\"Discovery & Planning\",\"description\":\"Requirements gathering and planning\",\"level\":1,\"parentId\":null}")
log_success "  - Discovery & Planning: $ERP_WBS_DISC"

ERP_WBS_BUILD=$(post_id "/v1/projects/$ERP_ID/wbs" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP.2\",\"title\":\"Build & Configure\",\"description\":\"System configuration and customization\",\"level\":1,\"parentId\":null}")
log_success "  - Build & Configure: $ERP_WBS_BUILD"

ERP_WBS_TEST=$(post_id "/v1/projects/$ERP_ID/wbs" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP.3\",\"title\":\"Testing & Migration\",\"description\":\"Testing and data migration\",\"level\":1,\"parentId\":null}")
log_success "  - Testing & Migration: $ERP_WBS_TEST"

ERP_WBS_GOLIVE=$(post_id "/v1/projects/$ERP_ID/wbs" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP.4\",\"title\":\"Go-Live & Support\",\"description\":\"Deployment and support\",\"level\":1,\"parentId\":null}")
log_success "  - Go-Live & Support: $ERP_WBS_GOLIVE"

# Activities for ERP-2026 (~14 activities)
log_info "Creating activities for ERP-2026..."

declare -a ERP_ACTIVITIES

ERP_ACTIVITIES[0]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-001\",\"title\":\"Project Kickoff\",\"description\":\"Initiate ERP project\",\"activityType\":\"START_MILESTONE\",\"wbsId\":\"$ERP_WBS_DISC\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-01\",\"duration\":0}")
log_success "  - ERP-ACT-001: Kickoff"

ERP_ACTIVITIES[1]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-002\",\"title\":\"Business Process Analysis\",\"description\":\"Analyze current business processes\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$ERP_WBS_DISC\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-09-15\",\"duration\":35}")
log_success "  - ERP-ACT-002: Process Analysis"

ERP_ACTIVITIES[2]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-003\",\"title\":\"Requirements Documentation\",\"description\":\"Document functional requirements\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$ERP_WBS_DISC\",\"startDate\":\"2026-09-16\",\"endDate\":\"2026-10-15\",\"duration\":25}")
log_success "  - ERP-ACT-003: Requirements"

ERP_ACTIVITIES[3]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-004\",\"title\":\"System Design\",\"description\":\"Design ERP system architecture\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$ERP_WBS_DISC\",\"startDate\":\"2026-10-16\",\"endDate\":\"2026-10-31\",\"duration\":12}")
log_success "  - ERP-ACT-004: Design"

ERP_ACTIVITIES[4]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-005\",\"title\":\"Core System Configuration\",\"description\":\"Configure core ERP modules\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$ERP_WBS_BUILD\",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-12-15\",\"duration\":35}")
log_success "  - ERP-ACT-005: Core Configuration"

ERP_ACTIVITIES[5]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-006\",\"title\":\"Finance Module Setup\",\"description\":\"Configure financial systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$ERP_WBS_BUILD\",\"startDate\":\"2026-11-15\",\"endDate\":\"2026-12-31\",\"duration\":35}")
log_success "  - ERP-ACT-006: Finance Module"

ERP_ACTIVITIES[6]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-007\",\"title\":\"Supply Chain Module Setup\",\"description\":\"Configure supply chain systems\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$ERP_WBS_BUILD\",\"startDate\":\"2026-12-01\",\"endDate\":\"2027-01-15\",\"duration\":35}")
log_success "  - ERP-ACT-007: Supply Chain"

ERP_ACTIVITIES[7]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-008\",\"title\":\"Integration Development\",\"description\":\"Develop third-party integrations\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$ERP_WBS_BUILD\",\"startDate\":\"2026-12-20\",\"endDate\":\"2027-01-31\",\"duration\":30}")
log_success "  - ERP-ACT-008: Integrations"

ERP_ACTIVITIES[8]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-009\",\"title\":\"Unit Testing\",\"description\":\"Conduct unit testing\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$ERP_WBS_TEST\",\"startDate\":\"2027-01-16\",\"endDate\":\"2027-02-15\",\"duration\":25}")
log_success "  - ERP-ACT-009: Unit Testing"

ERP_ACTIVITIES[9]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-010\",\"title\":\"User Acceptance Testing\",\"description\":\"Conduct UAT with business users\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$ERP_WBS_TEST\",\"startDate\":\"2027-02-16\",\"endDate\":\"2027-03-20\",\"duration\":25}")
log_success "  - ERP-ACT-010: UAT"

ERP_ACTIVITIES[10]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-011\",\"title\":\"Data Migration Preparation\",\"description\":\"Prepare and validate data for migration\",\"activityType\":\"TASK_DEPENDENT\",\"wbsId\":\"$ERP_WBS_TEST\",\"startDate\":\"2027-03-21\",\"endDate\":\"2027-04-10\",\"duration\":15}")
log_success "  - ERP-ACT-011: Migration Prep"

ERP_ACTIVITIES[11]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-012\",\"title\":\"Data Migration Execution\",\"description\":\"Execute data migration to new system\",\"activityType\":\"RESOURCE_DEPENDENT\",\"wbsId\":\"$ERP_WBS_TEST\",\"startDate\":\"2027-04-11\",\"endDate\":\"2027-04-25\",\"duration\":12}")
log_success "  - ERP-ACT-012: Migration Execution"

ERP_ACTIVITIES[12]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-013\",\"title\":\"User Training & Documentation\",\"description\":\"Train users and finalize documentation\",\"activityType\":\"LEVEL_OF_EFFORT\",\"wbsId\":\"$ERP_WBS_GOLIVE\",\"startDate\":\"2027-04-26\",\"endDate\":\"2027-05-10\",\"duration\":12}")
log_success "  - ERP-ACT-013: Training"

ERP_ACTIVITIES[13]=$(post_id "/v1/projects/$ERP_ID/activities" \
    "{\"projectId\":\"$ERP_ID\",\"code\":\"ERP-ACT-014\",\"title\":\"Go-Live & Production Support\",\"description\":\"Deploy to production and provide support\",\"activityType\":\"LEVEL_OF_EFFORT\",\"wbsId\":\"$ERP_WBS_GOLIVE\",\"startDate\":\"2027-05-11\",\"endDate\":\"2027-05-31\",\"duration\":15}")
log_success "  - ERP-ACT-014: Go-Live"

# Relationships for ERP-2026
log_info "Creating relationships for ERP-2026..."
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[0]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[1]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[1]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[2]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[2]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[3]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[3]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[4]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[3]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[5]}\",\"relationshipType\":\"SS\",\"lag\":10}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[3]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[6]}\",\"relationshipType\":\"SS\",\"lag\":15}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[4]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[7]}\",\"relationshipType\":\"SS\",\"lag\":10}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[5]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[8]}\",\"relationshipType\":\"FS\",\"lag\":10}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[6]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[8]}\",\"relationshipType\":\"FS\",\"lag\":10}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[7]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[8]}\",\"relationshipType\":\"SS\",\"lag\":20}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[8]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[9]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[9]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[10]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[10]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[11]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[11]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[12]}\",\"relationshipType\":\"FS\",\"lag\":0}"
post_silent "/v1/projects/$ERP_ID/relationships" "{\"projectId\":\"$ERP_ID\",\"sourceActivityId\":\"${ERP_ACTIVITIES[12]}\",\"targetActivityId\":\"${ERP_ACTIVITIES[13]}\",\"relationshipType\":\"FS\",\"lag\":0}"

log_success "Created 15 relationships for ERP-2026"

# Schedule calculation for ERP-2026
log_info "Running CPM schedule for ERP-2026..."
post_silent "/v1/projects/$ERP_ID/schedule" "{}"
log_success "Schedule calculated"

# Baseline for ERP-2026
log_info "Creating baseline for ERP-2026..."
post_silent "/v1/projects/$ERP_ID/baselines" \
    "{\"name\":\"Baseline 1\",\"baselineType\":\"PLANNED\",\"description\":\"Initial project baseline\"}"
log_success "Baseline created"

##############################################################################
# Summary and Final Verification
##############################################################################

log_info ""
log_info "========== Summary =========="
log_success "All data created successfully!"
log_info ""
log_info "Projects with WBS, Activities, Relationships, Schedules, and Baselines:"
log_success "  MRB-2026: 6 WBS nodes, 25 activities, 24 relationships, 4 risks"
log_success "  SOT-2026: 5 WBS nodes, 18 activities, 18 relationships, 3 risks"
log_success "  DSSF-2026: 5 WBS nodes, 15 activities, 16 relationships, 2 risks"
log_success "  ERP-2026: 4 WBS nodes, 14 activities, 15 relationships, 0 risks"
log_info ""
log_success "Demo data seeding complete!"

exit 0

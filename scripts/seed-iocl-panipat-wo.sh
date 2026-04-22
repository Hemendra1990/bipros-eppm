#!/bin/bash
# =============================================================================
# Bipros EPPM — IOCL Panipat WO 70143247 seeder
# Loads the IOCL "Civil and Mechanical Package for Revamping of Bitumen Filling
# Plant" work order into a running backend via REST.
# Source doc: docs/iocl-panipat-wo.md (extracted from the 123-page SAP YMPR_SPO PDF)
#
# Prereqs:
#   - Backend running on $BASE with `dev` profile (seeded admin user).
#   - pdftotext (poppler) if you want to regenerate the BOQ TSV from the PDF.
#
# Env vars:
#   BASE=http://localhost:8080   backend URL
#   PDF_PATH="$HOME/Downloads/WO IOCL PANIPAT WORK ORDER 70143247 DEs tech.pdf"
#   SEED_FULL_BOQ=1              if set, creates one expense per BOQ line (~679).
#                                Default: aggregates one expense per activity (~25).
# =============================================================================
set -o pipefail

# Track non-fatal errors for the final summary
declare -a FAILED_STEPS=()
fail() { echo "    ✗ FAIL: $*" >&2; FAILED_STEPS+=("$*"); }

BASE="${BASE:-http://localhost:8080}"
USER_NAME="${USER_NAME:-admin}"
USER_PASS="${USER_PASS:-admin123}"
PDF_PATH="${PDF_PATH:-$HOME/Downloads/WO IOCL PANIPAT WORK ORDER 70143247 DEs tech.pdf}"
BOQ_TSV="${BOQ_TSV:-/tmp/iocl_panipat_boq.tsv}"
CT="Content-Type: application/json"

# ── Project-level defaults (from docs/iocl-panipat-wo.md §10) ───────────────
# RUN_ID suffix makes the script idempotent against re-runs (codes must be unique
# in DB). Set RUN_ID="" to use raw codes (only safe on a fresh backend boot).
RUN_ID="${RUN_ID:-$(date +%H%M%S)}"
SFX="${RUN_ID:+-$RUN_ID}"
WO_CODE="WO70143247${SFX}"
WO_NAME="IOCL Panipat - Bitumen Filling Plant Revamp (WO 70143247)${SFX:+ [run $RUN_ID]}"
WO_START="2024-08-01"
WO_FINISH="2025-06-30"     # start + 11 months
WO_DATE="2024-07-19"
CONTRACTOR_NAME="DE'S TECHNICO LIMITED"
CONTRACTOR_CODE="10108488"
CONTRACT_VALUE="189370825.01"

# =============================================================================
# Helpers
# =============================================================================

echo "========================================="
echo "  IOCL Panipat WO 70143247 — EPPM seeder"
echo "  Base: $BASE"
echo "========================================="

# Auth
echo
echo "[0] Login as $USER_NAME..."
LOGIN=$(curl -sfS -X POST "$BASE/v1/auth/login" -H "$CT" \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$USER_PASS\"}")
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
echo "    login OK"

# POST JSON, print .data.id. On error, log + track + print empty (soft-fail).
pj() {
  local url="$1" body="$2" resp code
  resp=$(curl -sS -X POST "$BASE$url" -H "$CT" -H "$AUTH" -d "$body" -w "\n___HTTP:%{http_code}")
  code=$(echo "$resp" | sed -n 's/.*___HTTP://p' | tail -1)
  resp=$(echo "$resp" | sed '$d')
  if [ "$code" -ge 200 ] && [ "$code" -lt 300 ]; then
    echo "$resp" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin); print(d.get('data',{}).get('id') or d.get('id',''))
except: print('')"
  else
    fail "POST $url → HTTP $code : $(echo "$resp" | head -c 200)"
    echo ""
  fi
}

# PUT JSON — soft-fail with diagnostics
puj() {
  local url="$1" body="$2" resp code
  resp=$(curl -sS -X PUT "$BASE$url" -H "$CT" -H "$AUTH" -d "$body" -w "\n___HTTP:%{http_code}")
  code=$(echo "$resp" | sed -n 's/.*___HTTP://p' | tail -1)
  resp=$(echo "$resp" | sed '$d')
  [ "$code" -lt 200 ] || [ "$code" -ge 300 ] && fail "PUT $url → HTTP $code : $(echo "$resp" | head -c 200)"
  return 0
}

# GET JSON body (raw) — soft-fail
gj() { curl -sS -X GET "$BASE$1" -H "$AUTH" 2>/dev/null || echo '{}'; }

# =============================================================================
# 1. EPS hierarchy: IOCL → Panipat → Bitumen Plant → WO
# =============================================================================
echo
echo "[1] EPS hierarchy..."
EPS_IOCL=$(pj /v1/eps "{\"code\":\"IOCL$SFX\",\"name\":\"Indian Oil Corporation Ltd$SFX\"}")
EPS_PNP=$(pj /v1/eps "{\"code\":\"PANIPAT$SFX\",\"name\":\"Panipat Terminal (1121)$SFX\",\"parentId\":\"$EPS_IOCL\"}")
EPS_BIT=$(pj /v1/eps "{\"code\":\"PNP-BIT$SFX\",\"name\":\"Bitumen Plant$SFX\",\"parentId\":\"$EPS_PNP\"}")
echo "    EPS: IOCL=$EPS_IOCL / Panipat=$EPS_PNP / Bitumen=$EPS_BIT"

# =============================================================================
# 2. OBS hierarchy: DSO Engineering (EIC org)
# =============================================================================
echo
echo "[2] OBS hierarchy..."
OBS_DSO=$(pj /v1/obs "{\"code\":\"DSO-ENGG$SFX\",\"name\":\"DSO Engineering (IOCL)$SFX\"}")
OBS_EIC=$(pj /v1/obs "{\"code\":\"EIC$SFX\",\"name\":\"Angom Rajen Singh - EIC DGM\",\"parentId\":\"$OBS_DSO\"}")
OBS_SITE=$(pj /v1/obs "{\"code\":\"SITE-ENG$SFX\",\"name\":\"Indresh Kumar - Site Engineer\",\"parentId\":\"$OBS_EIC\"}")
echo "    OBS: DSO=$OBS_DSO / EIC=$OBS_EIC / Site=$OBS_SITE"

# =============================================================================
# 3. Project
# =============================================================================
echo
echo "[3] Project..."
PROJ=$(pj /v1/projects "{\"code\":\"$WO_CODE\",\"name\":\"$WO_NAME\",\"description\":\"Civil and Mechanical Package for Revamping of Bitumen Filling Plant and augmentation of facilities for bulk TT loading at Panipat Bitumen Plant. SAP PO PT-09/70143247 dated $WO_DATE. Vendor: $CONTRACTOR_NAME (code $CONTRACTOR_CODE). Completion period 11 months.\",\"epsNodeId\":\"$EPS_BIT\",\"obsNodeId\":\"$OBS_SITE\",\"plannedStartDate\":\"$WO_START\",\"plannedFinishDate\":\"$WO_FINISH\",\"priority\":1}")
echo "    PROJ=$PROJ"

# =============================================================================
# 4. Calendar: 6-day work week + India statutory holidays
# =============================================================================
echo
echo "[4] Calendar + work week + holidays..."
CAL=$(pj /v1/calendars "{\"name\":\"IOCL Panipat WO 6-day\",\"description\":\"6-day work week (Mon-Sat), 8 hrs/day, India statutory holidays 2024-25\",\"calendarType\":\"PROJECT\",\"projectId\":\"$PROJ\",\"standardWorkHoursPerDay\":8.0,\"standardWorkDaysPerWeek\":6}")
# 6-day work week: MON-SAT working, SUN non-working
puj "/v1/calendars/$CAL/work-week" '[
  {"dayOfWeek":"MONDAY","dayType":"WORKING","startTime1":"08:00","endTime1":"12:00","startTime2":"13:00","endTime2":"17:00"},
  {"dayOfWeek":"TUESDAY","dayType":"WORKING","startTime1":"08:00","endTime1":"12:00","startTime2":"13:00","endTime2":"17:00"},
  {"dayOfWeek":"WEDNESDAY","dayType":"WORKING","startTime1":"08:00","endTime1":"12:00","startTime2":"13:00","endTime2":"17:00"},
  {"dayOfWeek":"THURSDAY","dayType":"WORKING","startTime1":"08:00","endTime1":"12:00","startTime2":"13:00","endTime2":"17:00"},
  {"dayOfWeek":"FRIDAY","dayType":"WORKING","startTime1":"08:00","endTime1":"12:00","startTime2":"13:00","endTime2":"17:00"},
  {"dayOfWeek":"SATURDAY","dayType":"WORKING","startTime1":"08:00","endTime1":"12:00","startTime2":"13:00","endTime2":"17:00"},
  {"dayOfWeek":"SUNDAY","dayType":"NON_WORKING"}
]'
# Key India statutory holidays within Aug 2024 – Jun 2025
for h in \
  '{"exceptionDate":"2024-08-15","dayType":"NON_WORKING","name":"Independence Day"}' \
  '{"exceptionDate":"2024-10-02","dayType":"NON_WORKING","name":"Gandhi Jayanti"}' \
  '{"exceptionDate":"2024-10-31","dayType":"NON_WORKING","name":"Diwali"}' \
  '{"exceptionDate":"2024-11-01","dayType":"NON_WORKING","name":"Diwali holiday"}' \
  '{"exceptionDate":"2024-12-25","dayType":"NON_WORKING","name":"Christmas"}' \
  '{"exceptionDate":"2025-01-26","dayType":"NON_WORKING","name":"Republic Day"}' \
  '{"exceptionDate":"2025-03-14","dayType":"NON_WORKING","name":"Holi"}' \
  '{"exceptionDate":"2025-05-01","dayType":"NON_WORKING","name":"Labour Day"}' ; do
  pj "/v1/calendars/$CAL/exceptions" "$h" >/dev/null
done
echo "    CAL=$CAL (6-day work week + 8 holidays)"

# =============================================================================
# 5. WBS tree — 5 L1 sections, each with L2 sub-buckets
# =============================================================================
echo
echo "[5] WBS tree..."
wbs() {
  local code="$1" name="$2" parent="${3:-null}"
  local body
  if [ "$parent" = "null" ]; then
    body="{\"code\":\"$code\",\"name\":\"$name\",\"projectId\":\"$PROJ\"}"
  else
    body="{\"code\":\"$code\",\"name\":\"$name\",\"projectId\":\"$PROJ\",\"parentId\":\"$parent\"}"
  fi
  pj "/v1/projects/$PROJ/wbs" "$body"
}

# WBS L1 — the 5 SAP "WORK ORDER ITEM" sections
W_CIV=$(wbs  "WO10$SFX"  "00010 - Civil (TT bulk loading)")
W_MECH=$(wbs "WO20$SFX"  "00020 - Part-B Mechanical Pipeline")
W_VALV=$(wbs "WO30$SFX"  "00030 - Part-C Valves & CS Pipe Supply")
W_STEAM=$(wbs "WO40$SFX" "00040 - Part-D Steam & Condensate Tracing")
W_DMNT=$(wbs  "WO50$SFX" "00050 - Part-E Dismantling")
echo "    WBS L1: $W_CIV $W_MECH $W_VALV $W_STEAM $W_DMNT"

# WBS L2 — sub-groups per section. bash-3 compat: use indirect vars WBS_L2__$key.
WBS_L2_COUNT=0
add_l2() { # $1=var-suffix  $2=code  $3=name  $4=parent
  local id; id=$(wbs "$2" "$3" "$4")
  eval "WBS_L2__$1=\"$id\""
  [ -n "$id" ] && WBS_L2_COUNT=$((WBS_L2_COUNT+1))
}
# $wbs2 key — returns stored WBS L2 id for key
wbs2() { eval "printf '%s' \"\$WBS_L2__$1\""; }
# Civil (10 L2)
add_l2 civ_clear "WO10-01$SFX" "Site Clearing & Grass Removal"        "$W_CIV"
add_l2 civ_earth "WO10-02$SFX" "Earthwork / Excavation / Filling"     "$W_CIV"
add_l2 civ_conc "WO10-03$SFX" "PCC / RCC / Concrete"                 "$W_CIV"
add_l2 civ_masn "WO10-04$SFX" "Masonry"                              "$W_CIV"
add_l2 civ_plas "WO10-05$SFX" "Plaster & Flooring"                   "$W_CIV"
add_l2 civ_wproof "WO10-06$SFX" "Waterproofing & Roofing"              "$W_CIV"
add_l2 civ_doors "WO10-07$SFX" "Doors & Windows"                      "$W_CIV"
add_l2 civ_plmb "WO10-08$SFX" "Sanitary & Plumbing"                  "$W_CIV"
add_l2 civ_paint "WO10-09$SFX" "Painting"                             "$W_CIV"
add_l2 civ_misc "WO10-10$SFX" "Miscellaneous Civil (fencing/drains)" "$W_CIV"
# Mechanical (4)
add_l2 mec_lay "WO20-01$SFX" "Bitumen Pipe Laying & Welding (>=1 in)"  "$W_MECH"
add_l2 mec_hydro "WO20-02$SFX" "Hydrotesting (1.5× DP, 4 hrs min)"    "$W_MECH"
add_l2 mec_dpt "WO20-03$SFX" "DP Testing per QAP"                   "$W_MECH"
add_l2 mec_sup "WO20-04$SFX" "Pipe Supports Installation"           "$W_MECH"
# Valves & supply (4)
add_l2 val_ball "WO30-01$SFX" "Ball/Globe/Check Valve Procurement & Install" "$W_VALV"
add_l2 val_cspipe "WO30-02$SFX" "CS Pipe Supply (ERW/LSAW 100-200 NB)"  "$W_VALV"
add_l2 val_seam "WO30-03$SFX" "Seamless Pipe Supply & Install"       "$W_VALV"
add_l2 val_flng "WO30-04$SFX" "Flange & Gasket Install"              "$W_VALV"
# Steam tracing (4)
add_l2 stm_trace "WO40-01$SFX" "Steam Heat Tracing (≤50 NB)"           "$W_STEAM"
add_l2 stm_sman "WO40-02$SFX" "Steam Manifold (4/8/12-way, ≤20 kg/cm²)" "$W_STEAM"
add_l2 stm_cman "WO40-03$SFX" "Condensate Collection Manifold"       "$W_STEAM"
add_l2 stm_trap "WO40-04$SFX" "Strainers / Traps / Piston Valves"    "$W_STEAM"
# Dismantling (2)
add_l2 dmt_pipe "WO50-01$SFX" "Dismantle Existing Piping (~45,000 m)" "$W_DMNT"
add_l2 dmt_pump "WO50-02$SFX" "Dismantle Existing Bitumen Pumps"     "$W_DMNT"

echo "    WBS L2: $WBS_L2_COUNT nodes"

# =============================================================================
# 6. Resources — labour (LABOR), equipment (NONLABOR), material (MATERIAL)
# =============================================================================
echo
echo "[6] Resources..."
RES_COUNT=0
res() { # $1=var $2=code $3=name $4=type $5=hourly $6=overtime $7=maxPerDay
  local body="{\"code\":\"$2\",\"name\":\"$3\",\"resourceType\":\"$4\",\"calendarId\":\"$CAL\",\"maxUnitsPerDay\":${7:-8},\"status\":\"ACTIVE\",\"hourlyRate\":$5,\"overtimeRate\":$6}"
  local id; id=$(pj /v1/resources "$body")
  eval "RES__$1=\"$id\""
  [ -n "$id" ] && RES_COUNT=$((RES_COUNT+1))
}
# res key — returns stored resource id for key
res_id() { eval "printf '%s' \"\$RES__$1\""; }
# Labour
res mason    "L-SKL-MASON$SFX"  "Skilled mason"                          LABOR   125 187 8
res welder   "L-SKL-WELDER$SFX" "Certified pipe welder (3G/6G)"          LABOR   250 375 8
res fitter   "L-SKL-FITTER$SFX" "Pipe fitter"                            LABOR   200 300 8
res elec     "L-SKL-ELEC$SFX"   "Electrician"                            LABOR   175 262 8
res helper   "L-UNSKL$SFX"      "Unskilled helper"                       LABOR    60  90 8
res supv     "L-SUPV$SFX"       "Site supervisor"                        LABOR   400 600 8
# Equipment (hourly = day-rate/8 as a rough normalisation)
res exc      "E-EXC-20T$SFX"    "Hydraulic excavator 20T"                NONLABOR 1500 2250 1
res crane    "E-CRANE-25T$SFX"  "Mobile crane 25T"                       NONLABOR 1875 2800 1
res weldset  "E-WELD-DC400$SFX" "DC welding set 400A"                    NONLABOR  100  150 1
res hydro    "E-HYDRO-PUMP$SFX" "Hydrotest pump (≤50 kg/cm²)"            NONLABOR  313  470 1
res comp     "E-COMP-185$SFX"   "Air compressor 185 CFM"                 NONLABOR  438  657 1
res scaff    "E-SCAFF-SET$SFX"  "Double scaffolding set"                 NONLABOR   50   75 1
# Material — 0-rate resources used for procurement tracking
res mcspipe  "M-CS-PIPE$SFX"    "CS pipe (free-issue by IOCL, all sizes)" MATERIAL   0   0 1000
res mbitmn   "M-BITUMEN$SFX"    "Bitumen"                                 MATERIAL   0   0 1000
res minsul   "M-INSUL$SFX"      "Pipe insulation + cladding"              MATERIAL   0   0 1000

echo "    Resources: $RES_COUNT (6 labour / 6 equip / 3 material)"

# =============================================================================
# 7. Resource rates — one STANDARD HOURLY rate per labour/equipment resource
# =============================================================================
echo
echo "[7] Resource rates..."
rate() { pj "/v1/resources/$1/rates" "$2" >/dev/null; }
for k in mason welder fitter elec helper supv exc crane weldset hydro comp scaff; do
  id=$(res_id "$k"); [ -z "$id" ] && continue
  # Use hourlyRate from step 6 (echo'd via GET not required — simple flat rate)
  rate "$id" "{\"rateType\":\"STANDARD\",\"pricePerUnit\":1.00,\"effectiveDate\":\"$WO_START\",\"maxUnitsPerTime\":8}"
done
echo "    rates seeded (STANDARD effective $WO_START)"

# =============================================================================
# 8. Cost accounts — one per WBS L1, plus a root
# =============================================================================
echo
echo "[8] Cost accounts..."
CA_ROOT=$(pj /v1/cost-accounts  "{\"code\":\"$WO_CODE-CA\",\"name\":\"WO 70143247 - Root Cost Account\",\"sortOrder\":1}")
CA_CIV=$(pj  /v1/cost-accounts  "{\"code\":\"CA-10$SFX\",\"name\":\"Civil\",\"parentId\":\"$CA_ROOT\",\"sortOrder\":1}")
CA_MECH=$(pj /v1/cost-accounts  "{\"code\":\"CA-20$SFX\",\"name\":\"Mechanical Pipeline\",\"parentId\":\"$CA_ROOT\",\"sortOrder\":2}")
CA_VALV=$(pj /v1/cost-accounts  "{\"code\":\"CA-30$SFX\",\"name\":\"Valves/CS Pipe Supply\",\"parentId\":\"$CA_ROOT\",\"sortOrder\":3}")
CA_STM=$(pj  /v1/cost-accounts  "{\"code\":\"CA-40$SFX\",\"name\":\"Steam/Condensate Tracing\",\"parentId\":\"$CA_ROOT\",\"sortOrder\":4}")
CA_DMT=$(pj  /v1/cost-accounts  "{\"code\":\"CA-50$SFX\",\"name\":\"Dismantling\",\"parentId\":\"$CA_ROOT\",\"sortOrder\":5}")
echo "    CA: root=$CA_ROOT civ=$CA_CIV mech=$CA_MECH valv=$CA_VALV steam=$CA_STM dmt=$CA_DMT"

# =============================================================================
# 9. Activities — one per WBS L2, plus start/finish milestones
# =============================================================================
echo
echo "[9] Activities..."
ACT_COUNT=0
act() { # $1=var $2=code $3=name $4=wbsId $5=type $6=dur $7=start $8=finish
  local body="{\"code\":\"$2\",\"name\":\"$3\",\"projectId\":\"$PROJ\",\"wbsNodeId\":\"$4\",\"activityType\":\"$5\",\"durationType\":\"FIXED_DURATION_AND_UNITS\",\"percentCompleteType\":\"DURATION\",\"originalDuration\":$6,\"plannedStartDate\":\"$7\",\"plannedFinishDate\":\"$8\",\"calendarId\":\"$CAL\"}"
  local id; id=$(pj "/v1/projects/$PROJ/activities" "$body"); eval "ACT__$1=\"$id\""; [ -n "$id" ] && ACT_COUNT=$((ACT_COUNT+1))
}
# act key — returns stored activity id for key
act_id() { eval "printf '%s' \"\$ACT__$1\""; }

# Milestones (duration 0)
act m_start MS-START$SFX "Project Start — LOA received"       "$W_CIV"   START_MILESTONE 0 "$WO_START"   "$WO_START"
act m_end   MS-END$SFX   "Project Completion (11 months)"     "$W_CIV"   FINISH_MILESTONE 0 "$WO_FINISH" "$WO_FINISH"

# Dismantling runs first (~3 weeks)
act a_dmt_pipe A50-01$SFX "Dismantle existing piping (45,000 m)" "$(wbs2 dmt_pipe)" TASK_DEPENDENT 15 2024-08-01 2024-08-19
act a_dmt_pump A50-02$SFX "Dismantle existing bitumen pumps"     "$(wbs2 dmt_pump)" TASK_DEPENDENT  5 2024-08-05 2024-08-10

# Civil (~14 weeks, staggered from Aug 20)
act a_civ_clear  A10-01$SFX "Site clearing & grass removal"       "$(wbs2 civ_clear)"  TASK_DEPENDENT  5  2024-08-20 2024-08-26
act a_civ_earth  A10-02$SFX "Earthwork / excavation / filling"    "$(wbs2 civ_earth)"  TASK_DEPENDENT 20  2024-08-27 2024-09-21
act a_civ_conc   A10-03$SFX "PCC / RCC / concrete works"          "$(wbs2 civ_conc)"   TASK_DEPENDENT 25  2024-09-23 2024-10-24
act a_civ_masn   A10-04$SFX "Masonry"                             "$(wbs2 civ_masn)"   TASK_DEPENDENT 15  2024-10-25 2024-11-13
act a_civ_plas   A10-05$SFX "Plaster & flooring"                  "$(wbs2 civ_plas)"   TASK_DEPENDENT 10  2024-11-14 2024-11-26
act a_civ_wp     A10-06$SFX "Waterproofing & roofing"             "$(wbs2 civ_wproof)" TASK_DEPENDENT 10  2024-11-14 2024-11-26
act a_civ_doors  A10-07$SFX "Doors & windows"                     "$(wbs2 civ_doors)"  TASK_DEPENDENT  7  2024-11-27 2024-12-05
act a_civ_plmb   A10-08$SFX "Sanitary & plumbing"                 "$(wbs2 civ_plmb)"   TASK_DEPENDENT 10  2024-11-27 2024-12-09
act a_civ_paint  A10-09$SFX "Painting"                            "$(wbs2 civ_paint)"  TASK_DEPENDENT  8  2024-12-10 2024-12-19
act a_civ_misc   A10-10$SFX "Civil miscellaneous (fencing/drains)" "$(wbs2 civ_misc)"  TASK_DEPENDENT 15  2024-12-20 2025-01-10

# Valves & supply (~10 weeks; procurement starts early, install overlaps mech)
act a_val_ball   A30-01$SFX "Valve procurement & install (ball/globe/check)" "$(wbs2 val_ball)"   TASK_DEPENDENT 30 2024-09-01 2024-10-07
act a_val_cspipe A30-02$SFX "CS pipe supply (100-200 NB)"        "$(wbs2 val_cspipe)" TASK_DEPENDENT 25 2024-09-01 2024-09-30
act a_val_seam   A30-03$SFX "Seamless pipe supply & install"     "$(wbs2 val_seam)"   TASK_DEPENDENT 20 2024-09-15 2024-10-08
act a_val_flng   A30-04$SFX "Flange & gasket install"            "$(wbs2 val_flng)"   TASK_DEPENDENT 15 2024-10-01 2024-10-18

# Mechanical pipeline (~9 weeks)
act a_mec_lay    A20-01$SFX "Bitumen pipe laying & welding"      "$(wbs2 mec_lay)"    TASK_DEPENDENT 40 2024-10-09 2024-11-25
act a_mec_sup    A20-02$SFX "Pipe supports installation"         "$(wbs2 mec_sup)"    TASK_DEPENDENT 20 2024-10-15 2024-11-07
act a_mec_hydro  A20-03$SFX "Hydrotesting (1.5× DP, 4 hrs)"      "$(wbs2 mec_hydro)"  TASK_DEPENDENT  8 2024-11-26 2024-12-04
act a_mec_dpt    A20-04$SFX "DP testing per QAP"                 "$(wbs2 mec_dpt)"    TASK_DEPENDENT  5 2024-12-05 2024-12-10

# Steam tracing (~12 weeks, after main pipeline)
act a_stm_trace  A40-01$SFX "Steam heat tracing (≤50 NB)"         "$(wbs2 stm_trace)" TASK_DEPENDENT 25 2024-12-11 2025-01-11
act a_stm_sman   A40-02$SFX "Steam manifold install (4/8/12 way)" "$(wbs2 stm_sman)"  TASK_DEPENDENT 15 2025-01-13 2025-01-30
act a_stm_cman   A40-03$SFX "Condensate collection manifold"      "$(wbs2 stm_cman)"  TASK_DEPENDENT 15 2025-02-01 2025-02-19
act a_stm_trap   A40-04$SFX "Strainers / traps / piston valves"   "$(wbs2 stm_trap)"  TASK_DEPENDENT 10 2025-02-20 2025-03-04

echo "    Activities: $ACT_COUNT (incl. 2 milestones)"

# =============================================================================
# 10. Activity relationships — FS with lag 0 unless noted
# =============================================================================
echo
echo "[10] Relationships..."
rel() { # $1=pred $2=succ $3=type (FINISH_TO_START etc) $4=lag
  pj "/v1/projects/$PROJ/relationships" "{\"predecessorActivityId\":\"$1\",\"successorActivityId\":\"$2\",\"relationshipType\":\"${3:-FINISH_TO_START}\",\"lag\":${4:-0}}" >/dev/null
}
FS=FINISH_TO_START
# Start → Dismantling
rel "$(act_id m_start)"    "$(act_id a_dmt_pipe)"   "$FS" 0
rel "$(act_id m_start)"    "$(act_id a_dmt_pump)"   "$FS" 0
# Dismantling → Civil + Procurement parallel
rel "$(act_id a_dmt_pipe)" "$(act_id a_civ_clear)"  "$FS" 0
rel "$(act_id a_dmt_pump)" "$(act_id a_civ_clear)"  "$FS" 0
rel "$(act_id m_start)"    "$(act_id a_val_ball)"   "$FS" 10
rel "$(act_id m_start)"    "$(act_id a_val_cspipe)" "$FS" 10
# Civil chain
rel "$(act_id a_civ_clear)"  "$(act_id a_civ_earth)"  "$FS" 0
rel "$(act_id a_civ_earth)"  "$(act_id a_civ_conc)"   "$FS" 0
rel "$(act_id a_civ_conc)"   "$(act_id a_civ_masn)"   "$FS" 0
rel "$(act_id a_civ_masn)"   "$(act_id a_civ_plas)"   "$FS" 0
rel "$(act_id a_civ_masn)"   "$(act_id a_civ_wp)"     "$FS" 0
rel "$(act_id a_civ_plas)"   "$(act_id a_civ_doors)"  "$FS" 0
rel "$(act_id a_civ_plas)"   "$(act_id a_civ_plmb)"   "$FS" 0
rel "$(act_id a_civ_doors)"  "$(act_id a_civ_paint)"  "$FS" 0
rel "$(act_id a_civ_paint)"  "$(act_id a_civ_misc)"   "$FS" 0
# Valve supply → install → seamless → flanges
rel "$(act_id a_val_cspipe)" "$(act_id a_val_seam)"   "$FS" 0
rel "$(act_id a_val_ball)"   "$(act_id a_val_flng)"   "$FS" 0
rel "$(act_id a_val_seam)"   "$(act_id a_val_flng)"   "$FS" 0
# Mechanical pipeline: depends on CS pipes + flanges
rel "$(act_id a_val_flng)"   "$(act_id a_mec_lay)"    "$FS" 0
rel "$(act_id a_mec_lay)"    "$(act_id a_mec_sup)"    START_TO_START 5
rel "$(act_id a_mec_lay)"    "$(act_id a_mec_hydro)"  "$FS" 0
rel "$(act_id a_mec_hydro)"  "$(act_id a_mec_dpt)"    "$FS" 0
# Steam tracing after pipeline
rel "$(act_id a_mec_dpt)"    "$(act_id a_stm_trace)"  "$FS" 0
rel "$(act_id a_stm_trace)"  "$(act_id a_stm_sman)"   "$FS" 0
rel "$(act_id a_stm_sman)"   "$(act_id a_stm_cman)"   "$FS" 0
rel "$(act_id a_stm_cman)"   "$(act_id a_stm_trap)"   "$FS" 0
# All terminals → finish milestone
for k in a_stm_trap a_civ_misc; do
  rel "$(act_id "$k")" "$(act_id m_end)" "$FS" 0
done
echo "    Relationships seeded"

# =============================================================================
# 11. Resource assignments — key activity ↔ resource links
# =============================================================================
echo
echo "[11] Resource assignments..."
asg() { # $1=actId $2=resId $3=plannedUnits
  pj "/v1/projects/$PROJ/resource-assignments" "{\"activityId\":\"$1\",\"resourceId\":\"$2\",\"projectId\":\"$PROJ\",\"plannedUnits\":$3,\"rateType\":\"STANDARD\"}" >/dev/null
}
# Dismantling — fitters + helpers + crane
asg "$(act_id a_dmt_pipe)" "$(res_id fitter)" 80
asg "$(act_id a_dmt_pipe)" "$(res_id helper)" 160
asg "$(act_id a_dmt_pipe)" "$(res_id crane)"  40
asg "$(act_id a_dmt_pump)" "$(res_id fitter)" 24
asg "$(act_id a_dmt_pump)" "$(res_id crane)"  16
# Civil — masons + helpers + excavator
asg "$(act_id a_civ_earth)" "$(res_id exc)"    120
asg "$(act_id a_civ_earth)" "$(res_id helper)" 320
asg "$(act_id a_civ_conc)"  "$(res_id mason)"  200
asg "$(act_id a_civ_conc)"  "$(res_id helper)" 400
asg "$(act_id a_civ_masn)"  "$(res_id mason)"  120
asg "$(act_id a_civ_paint)" "$(res_id helper)"  64
# Mechanical pipeline — welders + fitters + weld-set + hydro
asg "$(act_id a_mec_lay)"   "$(res_id welder)"  320
asg "$(act_id a_mec_lay)"   "$(res_id fitter)"  320
asg "$(act_id a_mec_lay)"   "$(res_id weldset)" 320
asg "$(act_id a_mec_lay)"   "$(res_id mcspipe)" 1
asg "$(act_id a_mec_hydro)" "$(res_id hydro)"    64
asg "$(act_id a_mec_hydro)" "$(res_id fitter)"   64
# Steam tracing — fitters + insul
asg "$(act_id a_stm_trace)" "$(res_id fitter)" 200
asg "$(act_id a_stm_trace)" "$(res_id minsul)"   1
# Supervision across phases
for k in a_civ_conc a_mec_lay a_stm_trace a_dmt_pipe; do
  asg "$(act_id "$k")" "$(res_id supv)" 80
done
echo "    Assignments seeded"

# =============================================================================
# 12. Activity expenses (aggregated per WBS L2 from PDF section totals)
# =============================================================================
echo
echo "[12] Activity expenses..."
exp() { # $1=actId $2=caId $3=name $4=budget
  pj /v1/projects/$PROJ/expenses "{\"activityId\":\"$1\",\"projectId\":\"$PROJ\",\"costAccountId\":\"$2\",\"name\":\"$3\",\"budgetedCost\":$4,\"expenseCategory\":\"CONSTRUCTION\"}" >/dev/null
}
# Budget totals (per WO NET incl. 8.25 % surcharge, from docs/iocl-panipat-wo.md §3).
# Distributed across L2 activities by indicative weight — a rough split for seed.
# Civil: 58,348,317.97 total — split across 10 L2 activities
exp "$(act_id a_civ_clear)" "$CA_CIV"  "Civil - Site clearing BOQ"      1750000
exp "$(act_id a_civ_earth)" "$CA_CIV"  "Civil - Earthworks BOQ"        10500000
exp "$(act_id a_civ_conc)"  "$CA_CIV"  "Civil - Concrete BOQ"          17500000
exp "$(act_id a_civ_masn)"  "$CA_CIV"  "Civil - Masonry BOQ"            5800000
exp "$(act_id a_civ_plas)"  "$CA_CIV"  "Civil - Plaster/flooring BOQ"   4100000
exp "$(act_id a_civ_wp)"    "$CA_CIV"  "Civil - Waterproofing BOQ"      5200000
exp "$(act_id a_civ_doors)" "$CA_CIV"  "Civil - Doors/windows BOQ"      3500000
exp "$(act_id a_civ_plmb)"  "$CA_CIV"  "Civil - Sanitary/plumbing BOQ"  3400000
exp "$(act_id a_civ_paint)" "$CA_CIV"  "Civil - Painting BOQ"           2900000
exp "$(act_id a_civ_misc)"  "$CA_CIV"  "Civil - Misc (fencing/drains)"  3698317.97
# Mechanical: 36,368,587.96 — across 4 L2
exp "$(act_id a_mec_lay)"   "$CA_MECH" "Mech - Pipe laying/welding BOQ" 22000000
exp "$(act_id a_mec_sup)"   "$CA_MECH" "Mech - Supports BOQ"             5500000
exp "$(act_id a_mec_hydro)" "$CA_MECH" "Mech - Hydrotesting BOQ"         5500000
exp "$(act_id a_mec_dpt)"   "$CA_MECH" "Mech - DP testing BOQ"           3368587.96
# Valves: 40,866,475.05 — across 4
exp "$(act_id a_val_ball)"  "$CA_VALV" "Valve procurement BOQ"          20000000
exp "$(act_id a_val_cspipe)" "$CA_VALV" "CS pipe supply BOQ"            12000000
exp "$(act_id a_val_seam)"  "$CA_VALV" "Seamless pipe BOQ"               5000000
exp "$(act_id a_val_flng)"  "$CA_VALV" "Flange & gasket BOQ"             3866475.05
# Steam: 48,921,606.53 — across 4
exp "$(act_id a_stm_trace)" "$CA_STM"  "Steam heat tracing BOQ"         20000000
exp "$(act_id a_stm_sman)"  "$CA_STM"  "Steam manifold BOQ"             15000000
exp "$(act_id a_stm_cman)"  "$CA_STM"  "Condensate manifold BOQ"        10000000
exp "$(act_id a_stm_trap)"  "$CA_STM"  "Strainers/traps/piston BOQ"      3921606.53
# Dismantling: 4,865,837.50 — across 2
exp "$(act_id a_dmt_pipe)"  "$CA_DMT"  "Piping dismantling BOQ"          4455000
exp "$(act_id a_dmt_pump)"  "$CA_DMT"  "Pump dismantling BOQ"             410837.50
echo "    Expenses seeded (aggregated)"

# Optional full-BOQ seeding from parsed PDF
if [ "${SEED_FULL_BOQ:-0}" = "1" ] && [ -f "$PDF_PATH" ]; then
  echo "    SEED_FULL_BOQ=1 — parsing PDF to $BOQ_TSV ..."
  pdftotext -layout "$PDF_PATH" /tmp/iocl_wo_parse.txt
  python3 - "$BOQ_TSV" <<'PYEOF'
import sys, re
out_path = sys.argv[1]
txt = open('/tmp/iocl_wo_parse.txt').read().splitlines()
section = None; rows = []
line_re = re.compile(r'^(\d{5})\s+(\S+)\s+(\d+\.?\d*)\s+([0-9,]+\.?\d*)\s+(\S+)\s+([0-9,]+\.?\d*)\s+([0-9,]+\.?\d*)\s*$')
for line in txt:
    m = re.match(r'^WORK ORDER ITEM (000\d0)', line)
    if m: section = m.group(1); continue
    m = line_re.match(line.strip())
    if m and section:
        item_no, sap, srno, qty, unit, rate, amount = m.groups()
        rows.append([section, item_no, sap, srno, qty.replace(',',''), unit, rate.replace(',',''), amount.replace(',','')])
with open(out_path, 'w') as f:
    f.write("wo_item\tline_no\tsap_code\tsr_no\tqty\tunit\trate\tamount\n")
    for r in rows: f.write("\t".join(r) + "\n")
print(f"    wrote {len(rows)} BOQ rows")
PYEOF
  echo "    (full-BOQ as individual expenses not wired in this release — see $BOQ_TSV for data)"
fi

# =============================================================================
# 13. Schedule — run CPM
# =============================================================================
echo
echo "[13] Schedule (CPM, RETAINED_LOGIC)..."
pj "/v1/projects/$PROJ/schedule" "{\"projectId\":\"$PROJ\",\"option\":\"RETAINED_LOGIC\"}" >/dev/null
echo "    schedule computed"

# =============================================================================
# 14. Baseline — PRIMARY
# =============================================================================
echo
echo "[14] PRIMARY baseline..."
BL=$(pj "/v1/projects/$PROJ/baselines" '{"name":"WO-approved baseline","baselineType":"PRIMARY","description":"Baseline captured immediately after WO issue (19-Jul-2024) and before site mobilisation. Schedule + cost snapshot."}')
echo "    baseline=$BL"

# =============================================================================
# 15. Contract
# =============================================================================
echo
echo "[15] Contract..."
CTR=$(pj "/v1/projects/$PROJ/contracts" "{\"projectId\":\"$PROJ\",\"contractNumber\":\"70143247$SFX\",\"loaNumber\":\"PT-09/70143247$SFX\",\"contractorName\":\"$CONTRACTOR_NAME\",\"contractorCode\":\"$CONTRACTOR_CODE\",\"contractValue\":$CONTRACT_VALUE,\"loaDate\":\"$WO_DATE\",\"startDate\":\"$WO_START\",\"completionDate\":\"$WO_FINISH\",\"contractType\":\"ITEM_RATE_FIDIC_RED\"}")
echo "    contract=$CTR"

# =============================================================================
# 16. Risk register — 10 entries from docs/iocl-panipat-wo.md §9
# =============================================================================
echo
echo "[16] Risks..."
risk() { pj "/v1/projects/$PROJ/risks" "$1" >/dev/null; }
# Join activity IDs into comma-separated list (format accepted by Monte Carlo
# engine's risk-driver parser).
aff() { local out=""; for k in "$@"; do local id; id=$(act_id "$k"); [ -n "$id" ] && out="${out:+$out,}$id"; done; printf '%s' "$out"; }
A_R01=$(aff a_dmt_pipe a_dmt_pump a_civ_clear)
A_R02=$(aff a_dmt_pipe)
A_R03=$(aff a_dmt_pipe)
A_R04=$(aff a_val_cspipe a_val_seam)
A_R05=$(aff a_mec_hydro a_mec_dpt a_mec_lay)
A_R06=$(aff a_dmt_pipe a_dmt_pump)
A_R07=$(aff a_dmt_pipe a_mec_lay)
A_R08=$(aff a_stm_trap)
A_R09=$(aff a_civ_earth a_civ_clear a_civ_conc)
A_R10=$(aff a_mec_lay a_mec_hydro)
risk "{\"code\":\"R01\",\"title\":\"Phased handover from IOCL ops\",\"description\":\"Facilities cannot be handed over in one go; progressive handover needed per IOCL ops/maintenance/safety sign-off.\",\"category\":\"ORGANIZATIONAL\",\"probability\":\"HIGH\",\"impact\":\"HIGH\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":30,\"affectedActivities\":\"$A_R01\"}"
risk "{\"code\":\"R02\",\"title\":\"Dismantling wastage > 20% penalty\",\"description\":\"Wastage > 20% on dismantled piping material is penalised per contract.\",\"category\":\"COST\",\"probability\":\"MEDIUM\",\"impact\":\"MEDIUM\",\"identifiedDate\":\"2024-07-19\",\"costImpact\":500000,\"affectedActivities\":\"$A_R02\"}"
risk "{\"code\":\"R03\",\"title\":\"Insulation disposal compliance\",\"description\":\"Old pipe insulation must go to recycling agency with certificate submitted to IOCL.\",\"category\":\"STATUTORY_CLEARANCE\",\"probability\":\"MEDIUM\",\"impact\":\"LOW\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":3,\"costImpact\":150000,\"affectedActivities\":\"$A_R03\"}"
risk "{\"code\":\"R04\",\"title\":\"Free-issue CS pipe handling\",\"description\":\"IOCL supplies CS pipes; vendor handles site-wide transport at no extra cost.\",\"category\":\"RESOURCE\",\"probability\":\"LOW\",\"impact\":\"LOW\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":2,\"affectedActivities\":\"$A_R04\"}"
risk "{\"code\":\"R05\",\"title\":\"Hydrotest failure rework\",\"description\":\"Hydrotest at 1.5×DP for 4 hrs + DP on all joints. Any joint failure redoes entire section.\",\"category\":\"QUALITY\",\"probability\":\"MEDIUM\",\"impact\":\"HIGH\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":14,\"costImpact\":1500000,\"affectedActivities\":\"$A_R05\"}"
risk "{\"code\":\"R06\",\"title\":\"IOCL clearance bottleneck\",\"description\":\"Every dismantling step needs clearance from IOCL ops/maint/safety (3 sign-offs).\",\"category\":\"PROJECT_MANAGEMENT\",\"probability\":\"HIGH\",\"impact\":\"MEDIUM\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":10,\"affectedActivities\":\"$A_R06\"}"
risk "{\"code\":\"R07\",\"title\":\"Material ID damage on re-use\",\"description\":\"Material ID marks must be retained on dismantled pipes/fittings; damaged marks mean re-procurement.\",\"category\":\"QUALITY\",\"probability\":\"LOW\",\"impact\":\"MEDIUM\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":5,\"costImpact\":800000,\"affectedActivities\":\"$A_R07\"}"
risk "{\"code\":\"R08\",\"title\":\"Scope exclusions — commissioning/training\",\"description\":\"PDF does not list commissioning support, testing, or operator training. Confirm with EIC.\",\"category\":\"TECHNICAL\",\"probability\":\"MEDIUM\",\"impact\":\"MEDIUM\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":7,\"costImpact\":600000,\"affectedActivities\":\"$A_R08\"}"
risk "{\"code\":\"R09\",\"title\":\"Monsoon impact on civil earthworks\",\"description\":\"August start enters North-India monsoon season (Jul-Sep); civil earthwork productivity hit.\",\"category\":\"MONSOON_IMPACT\",\"probability\":\"HIGH\",\"impact\":\"MEDIUM\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":14,\"affectedActivities\":\"$A_R09\"}"
risk "{\"code\":\"R10\",\"title\":\"Live refinery concurrent ops\",\"description\":\"ISO-accredited running plant; all welding under hot-work permit, safety constraints.\",\"category\":\"EXTERNAL\",\"probability\":\"HIGH\",\"impact\":\"HIGH\",\"identifiedDate\":\"2024-07-19\",\"scheduleImpactDays\":7,\"costImpact\":400000,\"affectedActivities\":\"$A_R10\"}"
echo "    10 risks seeded with affected-activity links"

# =============================================================================
# 17. UDFs for contract gaps (§10)
# =============================================================================
echo
echo "[17] UDFs for contract gaps..."
udf() { pj /v1/udf/fields "$1"; }
UDF_LD=$(udf '{"name":"LD Rate (%)","description":"Liquidated damages rate per week — confirm from main contract","dataType":"NUMBER","subject":"PROJECT","scope":"PROJECT","projectId":"'"$PROJ"'"}')
UDF_DLP=$(udf '{"name":"DLP Months","description":"Defect liability period","dataType":"NUMBER","subject":"PROJECT","scope":"PROJECT","projectId":"'"$PROJ"'"}')
UDF_RET=$(udf '{"name":"Retention %","description":"Retention held from each RA bill","dataType":"NUMBER","subject":"PROJECT","scope":"PROJECT","projectId":"'"$PROJ"'"}')
UDF_PBG=$(udf '{"name":"PBG %","description":"Performance bank guarantee","dataType":"NUMBER","subject":"PROJECT","scope":"PROJECT","projectId":"'"$PROJ"'"}')
UDF_MOB=$(udf '{"name":"Mobilisation Advance %","description":"Mobilisation advance","dataType":"NUMBER","subject":"PROJECT","scope":"PROJECT","projectId":"'"$PROJ"'"}')
UDF_LOA=$(udf '{"name":"LOA Date","description":"Letter of award date","dataType":"DATE","subject":"PROJECT","scope":"PROJECT","projectId":"'"$PROJ"'"}')
# Seed LOA Date with the WO date (known)
puj "/v1/udf/values/$UDF_LOA/$PROJ" "{\"dateValue\":\"$WO_DATE\"}"
echo "    UDFs: LD=$UDF_LD DLP=$UDF_DLP RET=$UDF_RET PBG=$UDF_PBG MOB=$UDF_MOB LOA=$UDF_LOA"

# =============================================================================
# 18. Progress + EVM
# =============================================================================
echo
echo "[18] Seed progress on first activities → compute EVM..."
# Mark the start milestone 100% and dismantling activities 50% as of 2024-08-15
progress() { # $1=actId $2=pct
  # Endpoint expects query params (@RequestParam), not a JSON body.
  puj "/v1/projects/$PROJ/activities/$1/progress?percentComplete=$2" ""
}
progress "$(act_id m_start)"    100
progress "$(act_id a_dmt_pipe)"  50
progress "$(act_id a_dmt_pump)"  75
# Trigger EVM
pj "/v1/projects/$PROJ/evm/calculate" '{"technique":"ACTIVITY_PERCENT_COMPLETE","etcMethod":"CPI_BASED"}' >/dev/null
EVM=$(gj "/v1/projects/$PROJ/evm/summary" || echo '{}')
echo "    EVM summary: $(echo "$EVM" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("data",{}))' 2>/dev/null || echo "$EVM" | head -c 200)"

# =============================================================================
# 19. Exports — P6 XML / MSP / Excel
# =============================================================================
echo
echo "[19] Exports..."
OUT=/tmp/iocl-panipat-exports
mkdir -p "$OUT"
curl -sfS -H "$AUTH" -o "$OUT/WO70143247.p6.xml"   "$BASE/v1/import-export/projects/$PROJ/export/p6xml"  && echo "    wrote $OUT/WO70143247.p6.xml   ($(stat -f%z "$OUT/WO70143247.p6.xml") bytes)"   || echo "    p6xml export skipped"
curl -sfS -H "$AUTH" -o "$OUT/WO70143247.msp.xml" "$BASE/v1/import-export/projects/$PROJ/export/msp"    && echo "    wrote $OUT/WO70143247.msp.xml ($(stat -f%z "$OUT/WO70143247.msp.xml") bytes)" || echo "    msp export skipped"
curl -sfS -H "$AUTH" -o "$OUT/WO70143247.xlsx"    "$BASE/v1/import-export/projects/$PROJ/export/excel"  && echo "    wrote $OUT/WO70143247.xlsx    ($(stat -f%z "$OUT/WO70143247.xlsx") bytes)"   || echo "    excel export skipped"

# =============================================================================
# Summary
# =============================================================================
echo
echo "========================================="
echo "  Seed complete."
echo "========================================="
if [ "${#FAILED_STEPS[@]}" -gt 0 ]; then
  echo "  Failed steps (${#FAILED_STEPS[@]}):" >&2
  for s in "${FAILED_STEPS[@]}"; do echo "    - $s" >&2; done
else
  echo "  No step failures."
fi
echo "========================================="
echo "  Project  : $PROJ  (code $WO_CODE)"
echo "  EPS leaf : $EPS_BIT"
echo "  Calendar : $CAL"
echo "  Contract : $CTR"
echo "  Baseline : $BL"
echo "  WBS nodes: $((5 + WBS_L2_COUNT))  (5 L1 + $WBS_L2_COUNT L2)"
echo "  Activities: $ACT_COUNT"
echo "  Resources: $RES_COUNT"
echo "  Risks    : 10"
echo "  UDFs     : 6"
echo "  Exports  : $OUT/"
echo "  UI URL   : http://localhost:3000/projects/$PROJ"
echo "========================================="

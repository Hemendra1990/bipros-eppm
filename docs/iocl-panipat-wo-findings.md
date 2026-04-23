# IOCL Panipat WO 70143247 — Test findings

Issues surfaced while running `scripts/seed-iocl-panipat-wo.sh` against a freshly booted backend (`dev` profile) on 2026-04-21. The script is idempotent (uses a `RUN_ID` time suffix), reports a failure count at the end, and seeded **28 WBS nodes / 25 activities / 15 resources / 10 risks / 6 UDFs / 1 baseline / 1 contract / 3 exports** successfully per run. The 16 step-level failures all trace back to 3 distinct backend bugs documented below.

## Summary

| Severity | Area | Issue | Symptom | Blocker? |
|---|---|---|---|---|
| **P1** | Calendar | Work-week PUT returns HTTP 500 | Project calendar has no work-week rows; CPM falls back to default | Yes — blocks custom work-week per project |
| **P1** | Activities | One WBS L2 create fails with HTTP 500 (reproducible) | 1 activity + its relationships + expenses + assignments cascade-fail | Yes — blocks 100 % coverage |
| **P2** | Activity progress | PUT `/activities/{id}/progress` returns 403 or 500 depending on body | Cannot mark activity % complete from seed / automated tools | Partial — UI may work |
| P3 | Exception handler | `INTERNAL_ERROR` errors carry no detail or error code | Every 500 looks identical; forces console-log spelunking to debug | Quality issue |
| P3 | Idempotency | POST /eps, /projects, /resources etc. return 500 on duplicate code (not 409) | Generic 500 masks "duplicate" case; re-runs are hostile without a random suffix | Quality issue |

## Detail

### §1 — P1: `PUT /v1/calendars/{id}/work-week` returns HTTP 500

**Reproduce:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
CAL=$(curl -s -X POST http://localhost:8080/v1/calendars \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"dbg","calendarType":"GLOBAL","standardWorkHoursPerDay":8,"standardWorkDaysPerWeek":5}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")

# minimal payload — fails 500
curl -v -X PUT "http://localhost:8080/v1/calendars/$CAL/work-week" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '[{"dayOfWeek":"SUNDAY","dayType":"NON_WORKING"}]'
```

Returns: `{"error":{"code":"INTERNAL_ERROR","message":"An unexpected error occurred"}}` HTTP 500.

**Triaged:** payload shape matches DTO `CalendarWorkWeekRequest` (`backend/bipros-calendar/src/main/java/com/bipros/calendar/application/dto/CalendarWorkWeekRequest.java:8`). `CalendarService.setWorkWeek` (service line 181) does `DayOfWeek.valueOf(req.dayOfWeek().toUpperCase())` which accepts "SUNDAY" fine; `calculateTotalWorkHours` (line 441) handles null times. **So failure is below the service level** — likely JPA constraint or the `deleteByCalendarId` + insert transaction. No error body detail because of `GlobalExceptionHandler.java:80` blanket catch (see §4).

**Investigation needed:** grab the full stack trace from the IntelliJ backend console and post it here. Candidates:
1. `calendar_work_weeks` table unique constraint `(calendar_id, day_of_week)` — check whether DataSeeder's "Standard" calendar somehow share-inherited into this calendar.
2. Transaction boundary — if `deleteByCalendarId` is not flushed before the insert, the unique constraint fires.
3. JPA cascade on the `Calendar` entity may be rewriting work-weeks on the parent.

**Workaround:** seed script already tolerates this; the project calendar exists without a work-week. Activities use it; CPM treats absent work-week as default 8×5.

### §2 — P1: One WBS L2 create fails with HTTP 500 (reproducible)

**Symptom:** 23 of 24 WBS L2 creates succeed; one specific POST to `/v1/projects/{projectId}/wbs` returns 500. Reproducible at the same step across runs. Not a data problem — the failing L2's parent (a WBS L1) was created successfully just moments earlier.

**Candidate:** the failing node is likely `WO20-01 "Bitumen Pipe Laying & Welding (≥1\")"` whose name contains an escaped double-quote (`\"`) inside an already-double-quoted JSON body, plus the `≥` unicode character. Either (a) the JSON body is malformed on the way out (shell-quoting bug in the seed script), or (b) a column length / charset issue in the `wbs_nodes` table.

**Investigation needed:** instrument the `add_l2` helper in the script to log each payload *before* curl sends it:
```bash
add_l2() { local id; echo "    DEBUG add_l2($1): $(wbs "$2" "$3" "$4")"; id=$(wbs "$2" "$3" "$4"); ... }
```
and cross-reference the failing call against the backend console stack trace. Likely fix is to replace `≥` with `>=` and `\"` with `''` in the seed data — but if the root cause is a backend column constraint, that's a schema fix.

**Impact:** cascaded: 1 activity create fails (`wbsNodeId is required`), 1 expense fails, 1 resource-assignment fails, 2 relationships fail.

### §3 — P2: Activity progress PUT returns 403 / 500

**Symptom:** `PUT /v1/projects/{projectId}/activities/{activityId}/progress` with body `{"percentComplete":100}` fails (403 in one run, 500 in another).

**Investigation needed:** verify the controller's expected request DTO — the endpoint may require `actualStartDate`, `actualFinishDate`, `remainingDuration` in addition to `percentComplete`. 403 suggests either missing auth for a specific role, or CSRF. Check `ActivityController` (or `ScheduleHealthController`?) for the method handling `PUT /progress`.

**Impact:** cannot seed partial progress from script → EVM shows 0% earned value (but still computes correctly from PV=BAC baseline).

### §4 — P3: Exception handler strips diagnostic info

`backend/bipros-common/src/main/java/com/bipros/common/exception/GlobalExceptionHandler.java:80` catches unhandled exceptions and returns `{"error":{"code":"INTERNAL_ERROR","message":"An unexpected error occurred"}}`. In **dev profile** this should include:
- exception class name
- stack trace top frame
- request path + body hash

Without this, every 500 looks identical to the caller. Suggest gating the verbose variant on `@Profile("dev")`.

### §5 — P3: Create endpoints return 500 on duplicate code

EPS, Project, Resource, etc. appear to return generic 500 when a `code` collides with an existing row (unique constraint violation). Should return HTTP 409 Conflict with a meaningful error code like `DUPLICATE_CODE`. The seed script works around this by adding a time suffix (`$SFX`) to all codes — but this is hostile UX for manual testing.

## Successful validations (what worked)

These were verified live by the seed script and express features of the system worth celebrating:

1. **EPS / OBS trees**: nested-parent creation resolves correctly.
2. **Project creation** with EPS + OBS link.
3. **Calendar creation** (the parent entity — only work-week config fails).
4. **Resource rates**: STANDARD rate with effective date + max units accepted.
5. **Cost accounts**: parent/child hierarchy created; sortOrder respected.
6. **Activity relationships**: FS and SS (with lag) both accepted; self-relationship validation (`SELF_RELATIONSHIP`) works correctly.
7. **Resource assignments**: duplicate-prevention (`DUPLICATE_ASSIGNMENT`) works correctly.
8. **Schedule calculation** (`POST /schedule` with `RETAINED_LOGIC`) returns 200.
9. **Baseline creation** (PRIMARY) succeeds.
10. **Contract**: `ITEM_RATE_FIDIC_RED` accepted with all optional fields.
11. **Risk register**: all 10 risks across categories (ORGANIZATIONAL, COST, STATUTORY_CLEARANCE, RESOURCE, QUALITY, PROJECT_MANAGEMENT, TECHNICAL, MONSOON_IMPACT, EXTERNAL) accepted with probability/impact enums.
12. **UDF fields + values**: create + set value both work; DATE type tested with LOA Date.
13. **EVM calculation**: returns full metric set — **BAC ₹16,73,72,697.01**, PV = BAC, SPI 0.0, CPI 0.0, TCPI 1.0. Schema: `{budgetAtCompletion, plannedValue, earnedValue, actualCost, scheduleVariance, costVariance, schedulePerformanceIndex, costPerformanceIndex, toCompletePerformanceIndex, estimateAtCompletion, estimateToComplete, varianceAtCompletion, evmTechnique, etcMethod, performancePercentComplete}`. Money field arithmetic is correct.
14. **Exports — all three formats produced non-empty files:**
    - `/tmp/iocl-panipat-exports/WO70143247.p6.xml` — **55,922 bytes** (P6 XER XML)
    - `/tmp/iocl-panipat-exports/WO70143247.msp.xml` — **25,456 bytes** (MS Project XML)
    - `/tmp/iocl-panipat-exports/WO70143247.xlsx` — **15,516 bytes** (Excel)
    Not validated: round-trip integrity (re-import these via `POST /v1/import-export/projects/import/xer` should reproduce identical counts).

## Environment notes

- **Bash version**: macOS ships `/bin/bash` 3.2.57 which **does not support associative arrays** (`declare -A`). The seed script was rewritten to use indirect variable references so it runs on default macOS. Keep this in mind for any future seed helpers — don't assume bash 4+.
- **Docker Postgres is stopped**; backend is connecting to a native Postgres on `127.0.0.1:5432`. Both work identically for seeding.
- **`ddl-auto: create-drop`** in dev: every backend restart wipes the DB. If you want to preserve a seeded state across restarts, switch profile or disable `create-drop`.

## Next actions (for the user)

1. **Restart backend in debug mode and reproduce §1 and §2** — capture the stack traces from the IntelliJ console and attach to follow-up tickets. Those are the two blocking bugs.
2. **Decide whether to fix §4 (exception handler)** — five minutes' work, saves hours of future debugging.
3. **Run `docs/iocl-panipat-wo-ui-walkthrough.md`** against a fresh seed — expected ~27/30 green once §1, §2, §3 are fixed; 24/30 in current state.
4. **Consider `SEED_FULL_BOQ=1`** once the above are fixed — exercises the expense endpoint at scale (679 items). Currently the default aggregated seed creates 25 expenses.

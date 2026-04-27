# Capacity Utilization Reports — Excel-Parity Implementation

**Date:** 2026-04-27
**Source:** `/Volumes/Learning/road_project_test/oman/2. Capacity_Utilization.xlsx`
**Goal:** Reproduce all five sheets of the Oman capacity-utilization workbook from live system data, using existing functionality where it already exists and filling the small set of gaps.

---

## 1. Excel → System mapping

| Sheet | Today's coverage | Gap to close |
|---|---|---|
| **Plant utilization** — equipment × activity, day/month/cum, % util, per-supervisor split, site-norm column | `CapacityUtilizationReportService` + `GET /v1/reports/capacity-utilization` + `/projects/:id/capacity-utilization` page | Per-supervisor split; site-norm column |
| **Manpower utilization** — same structure for Mason/Carpenter/Steel Fixer/Helper | Same service handles `normType=MANPOWER` | Same gaps as Plant; plus full Oman manpower seed |
| **SUMMARY** — one row per equipment-type/category with For-the-Day, For-the-Month, Cumulative, supervisor split, AVERAGE row | None | New `level=SUMMARY` mode on existing endpoint |
| **Daily Deployment** — equipment × 31-day matrix of Worked / Idle / Planned hours | `DailyResourceDeployment` entity (worked, idle); `/resource-deployment` list page | Matrix-shape report; planned-hours entity |
| **DPR** — BOQ items × per-day achieved qty + monthly Projection vs Achieved totals | `BoqItem`, `DailyProgressReport`, `/dpr` list page | Matrix-shape report; per-month BOQ projection entity |

## 2. Data model deltas

Three column additions and two new entities. No other entity changes.

### 2.1 `resource.productivity_norms` — project-scoped overrides

Add nullable column:
```
project_id UUID NULL  -- soft FK to project.projects.id
```
Index: `idx_prod_norm_project (project_id)`.

Resolution chain in `ProductivityNormLookupService` (and `CapacityUtilizationReportService.resolveBudgeted`):

1. project-specific resource norm (`project_id = :p AND resource_id = :r`)
2. project-specific type norm (`project_id = :p AND resource_type_def_id = :t`)
3. global resource norm (`project_id IS NULL AND resource_id = :r`)
4. global type norm (`project_id IS NULL AND resource_type_def_id = :t`)
5. legacy `Resource.standardOutputPerDay`

### 2.2 `project.daily_activity_resource_outputs` — supervisor dimension

Add columns:
```
supervisor_id    UUID         NOT NULL  -- soft FK to security.users.id
supervisor_name  VARCHAR(150) NOT NULL  -- denormalised at write time
```
Index: `idx_dar_supervisor (project_id, supervisor_id, output_date)`.

Backfill existing rows via Liquibase changeset:
- `supervisor_id := created_by`
- `supervisor_name := COALESCE((SELECT TRIM(CONCAT(COALESCE(u.first_name,''),' ',COALESCE(u.last_name,''))) FROM security.users u WHERE u.id = o.created_by), 'Unknown')`

After backfill, ALTER both columns to NOT NULL.

`CreateDailyActivityResourceOutputRequest` adds **two** required fields: `supervisorId` (UUID) and `supervisorName` (string, max 150). The client supplies both — same loosely-coupled pattern `DailyProgressReportService` already uses for its supervisor field. `bipros-project` continues to not depend on `bipros-security` as a Maven module (per the cross-module-deps rule in CLAUDE.md). Frontend pre-fills the name field from the user picker so end-users see the right name without typing.

### 2.3 New entity `project.monthly_boq_projections`

```
id              UUID         PRIMARY KEY
project_id      UUID         NOT NULL
boq_item_no     VARCHAR(20)  NOT NULL  -- soft FK to project.boq_items.item_no within project
year_month      VARCHAR(7)   NOT NULL  -- format YYYY-MM
planned_qty     NUMERIC(18,3) NOT NULL
planned_amount  NUMERIC(19,2) NOT NULL  -- derived: planned_qty × BoqItem.budgetedRate
remarks         VARCHAR(500)

UNIQUE (project_id, boq_item_no, year_month)
INDEX idx_mbp_project_month (project_id, year_month)
```

Service recomputes `planned_amount` on every save by looking up the BoqItem's `budgetedRate` (mirrors how `BoqCalculator` already works for `BoqItem`).

CRUD endpoints under `/v1/projects/{projectId}/boq-projections` (GET list/by-id, POST create, POST `/bulk` for paste-from-Excel, PUT update, DELETE) — follows the same controller pattern as `DailyProgressReportController`.

### 2.4 New entity `project.daily_resource_plans`

```
id                    UUID         PRIMARY KEY
project_id            UUID         NOT NULL
plan_date             DATE         NOT NULL
resource_type_def_id  UUID         NULL  -- soft FK to resource.resource_type_defs.id
resource_id           UUID         NULL  -- soft FK to resource.resources.id
planned_hours         DOUBLE       NOT NULL
remarks               VARCHAR(500)

CHECK ( (resource_type_def_id IS NOT NULL) <> (resource_id IS NOT NULL) )  -- exactly one
UNIQUE (project_id, plan_date, resource_type_def_id, resource_id)
INDEX idx_drp_project_date (project_id, plan_date)
```

CRUD endpoints under `/v1/projects/{projectId}/resource-plans` mirroring the same controller pattern as `DailyResourceDeploymentController`. Bulk endpoint for paste-from-Excel.

## 3. Capacity Utilization report (Sheets 1, 2, 3)

### 3.1 Endpoint signature

```
GET /v1/reports/capacity-utilization
  ?projectId=<uuid>
  &fromDate=YYYY-MM-DD              (default: first of toDate's month)
  &toDate=YYYY-MM-DD                (default: today)
  &groupBy=RESOURCE_TYPE|RESOURCE   (existing; default RESOURCE_TYPE)
  &normType=EQUIPMENT|MANPOWER      (existing; null = both)
  &level=DETAIL|SUMMARY             (NEW; default DETAIL)
  &supervisorId=<uuid>              (NEW; optional filter)
  &bySupervisor=true|false          (NEW; default false)
```

### 3.2 DTO additions (`CapacityUtilizationReport`)

`Row`:
- `kind: enum { DETAIL, SUMMARY, AVERAGE }` — frontend renders AVERAGE row distinctly
- `workActivity` becomes nullable (null on SUMMARY/AVERAGE rows)

`Budgeted`:
- `siteOutputPerDay: BigDecimal` (the project-specific override when present; null otherwise)
- `source` enum extended: `PROJECT_SPECIFIC_RESOURCE | PROJECT_SPECIFIC_TYPE | SPECIFIC_RESOURCE | RESOURCE_TYPE | RESOURCE_LEGACY | NONE`

`Period`:
- `bySupervisor: List<SupervisorPeriod>` populated only when `?bySupervisor=true`
- `SupervisorPeriod` = `{ supervisorId, supervisorName, qty, actualDays, utilizationPct }`

All additions are optional fields (Jackson `NON_NULL`) — existing callers see no change.

### 3.3 Service changes (`CapacityUtilizationReportService`)

- Native query SELECT adds `o.supervisor_id, o.supervisor_name`.
- `Aggregate` gains a `Map<UUID, Aggregate>` sub-bucket per supervisor; populated unconditionally, serialised only when `bySupervisor=true`.
- `resolveBudgeted(workActivityId, resourceId, projectId)` runs the new 5-step chain and returns both `outputPerDay` and `siteOutputPerDay`.
- `level=SUMMARY`: collapse rows by group-key (drop work-activity dimension), then append one AVERAGE row per (groupBy, normType) section computed as the mean of the rolled-up `utilizationPct`s (excluding nulls).
- `supervisorId` filter is pushed into the WHERE clause for performance.

### 3.4 Frontend changes (`/projects/:id/capacity-utilization/page.tsx`)

- Add Detail/Summary toggle (`level`).
- Add "By supervisor" checkbox; when on, render per-supervisor sub-columns under each Period block (the spreadsheet's T SWAMY / NAGARAJAN / A.K. SINGH columns).
- Render second norm column "Site Norm" next to "Norm/Day" when `budgeted.siteOutputPerDay != null`.
- Render AVERAGE row at the end of each section in SUMMARY level (distinct styling).
- CSV export expanded to include all new columns.

## 4. Daily Deployment matrix report (Sheet 4)

### 4.1 Endpoint

```
GET /v1/reports/daily-deployment-matrix
  ?projectId=<uuid>
  &yearMonth=YYYY-MM
  &groupBy=RESOURCE_TYPE|RESOURCE   (default RESOURCE_TYPE)
```

### 4.2 DTO

```
DailyDeploymentMatrixReport {
  projectId, yearMonth, daysInMonth (28..31),
  workedHours:  Matrix,   // from daily_resource_deployments.hours_worked
  idleHours:    Matrix,   // from daily_resource_deployments.idle_hours
  plannedHours: Matrix,   // from daily_resource_plans.planned_hours
}
Matrix {
  rows: [ {
    groupKey: { id, label },
    daily:    BigDecimal[daysInMonth],
    rowTotal: BigDecimal
  } ],
  columnTotals: BigDecimal[daysInMonth],
  grandTotal:   BigDecimal
}
```

### 4.3 Service

New `DailyDeploymentMatrixReportService` in `bipros-reporting.application.service`. Three native queries (one per matrix), each grouping by (group-key, day-of-month). A small helper assembles the dense `daily[]` arrays from the sparse query result. Cells default to `0` when no row exists.

### 4.4 Frontend

New page `daily-deployment-matrix/page.tsx` — three stacked tables (Worked / Idle / Planned), month picker (defaults to current month), group-by toggle, CSV download. Tab added to project layout: "Deployment Matrix".

## 5. DPR matrix report (Sheet 5)

### 5.1 Endpoint

```
GET /v1/reports/dpr-matrix
  ?projectId=<uuid>
  &yearMonth=YYYY-MM
  &chapter=<optional MoRTH chapter filter>
```

### 5.2 DTO

```
DprMatrixReport {
  projectId, yearMonth, daysInMonth,
  rows: [ {
    boqItemNo, description, unit, revisedRate,        // from boq_items
    projection: { qty, amount },                       // from monthly_boq_projections (null if absent)
    achieved:   { qty, amount },                       // sum of dpr in month × actualRate
    daily:      BigDecimal[daysInMonth]                // per-day qty from daily_progress_reports
  } ],
  totals: {
    projection: { qty, amount },
    achieved:   { qty, amount },
    daily:      BigDecimal[daysInMonth]
  }
}
```

### 5.3 Service

New `DprMatrixReportService`. One native query left-joining `boq_items ⨝ monthly_boq_projections ⨝ daily_progress_reports`. Daily array assembled client-side from the date-grouped result. `chapter` filter pushed into WHERE.

### 5.4 Frontend

New page `dpr-matrix/page.tsx` — single wide table with sticky-left BOQ columns, month + chapter filters, CSV download.

## 6. Supporting CRUD pages (frontend)

Two thin pages so users can populate the new entities:

- `boq-projections/page.tsx` — list + bulk-paste form for `MonthlyBoqProjection` rows (drives DPR matrix's Projection columns).
- `resource-plans/page.tsx` — list + bulk-paste form for `DailyResourcePlan` rows (drives Daily-Deployment matrix's Planned block).

Both follow the existing `dpr/page.tsx` and `resource-deployment/page.tsx` patterns. Tabs added to the project layout.

## 7. Seeders — closing the Oman parity gap

Current `scripts/seed-oman-equipment.sh` has only 26 of the ~135 norms in the workbook. Replace with two scripts:

- `scripts/seed-oman-plant-norms.sh` — full Plant utilization sheet → `POST /v1/work-activities` + `POST /v1/productivity-norms` (normType=EQUIPMENT) for every (equipment-type × activity) row.
- `scripts/seed-oman-manpower-norms.sh` — full Manpower utilization sheet for Mason / Carpenter / Steel Fixer / Helper.

Both use a Python helper that reads the source workbook (`openpyxl`, already on the Python install) and emits CSV; the bash script iterates the CSV. No new backend functionality — these scripts use existing endpoints only.

Boot-time seeding (`OmanRoadProjectSeeder.java`) is **out of scope**: Oman is a customer workbook, loaded on demand via the scripts; boot seeders are reserved for the ICPMS demo dataset.

## 8. Test strategy

| Layer | Coverage |
|---|---|
| Norm resolution | Unit tests covering all 5 fallback steps + missing-norm fallthrough |
| `CapacityUtilizationReportService` | Existing tests stay green. New: SUMMARY level rolls correctly, AVERAGE row mean is correct, `bySupervisor` produces per-supervisor sub-buckets, site-norm column appears only when project override exists, `supervisorId` filter narrows correctly |
| `MonthlyBoqProjection` & `DailyResourcePlan` | Repository + service CRUD tests; bulk endpoint accepts paste shape; planned_amount derivation; mutual-exclusion CHECK on `daily_resource_plans` |
| Matrix endpoints | Native-query integration tests via existing `bipros-api/integration` harness (real Postgres). Cover: no rows in month → empty matrix with zeros; partial-month → correct daysInMonth; group-by switch |
| Frontend | Playwright e2e: enter outputs (with supervisor) → enter projections → enter resource plans → render each of the 5 reports → CSV downloads contain expected headers and row count |

## 9. Migration strategy

Dev profile (default) uses `ddl-auto: create-drop` so the schema is rebuilt every boot — no migration needed for local dev.

Prod profile uses Liquibase. Add changesets under `backend/bipros-api/src/main/resources/db/changelog/`:

- `2026-04-27-001-productivity-norm-add-project-id.xml` — add `project_id` column + index
- `2026-04-27-002-dar-add-supervisor.xml` — add `supervisor_id` (NULL initially), `supervisor_name`, backfill from `created_by`, then ALTER NOT NULL on `supervisor_id`
- `2026-04-27-003-monthly-boq-projection.xml` — create table
- `2026-04-27-004-daily-resource-plan.xml` — create table

Each changeset registered in `db.changelog-master.yaml` in chronological order (matches the project's existing convention).

## 10. Boundaries / what's explicitly NOT in scope

- No new authentication / RBAC roles (existing `ADMIN | PROJECT_MANAGER | VIEWER` cover the new endpoints; SITE_SUPERVISOR can write the new entry forms).
- No PDF/Excel server-side rendering — CSV download from the frontend is sufficient (matches existing capacity-utilization page).
- No automated norm derivation from history (Q3 option C) — site norms come from explicit project overrides only.
- No multi-month projection — `MonthlyBoqProjection` is per (BOQ × month); year-level forecasts are out of scope.
- No backwards-compatibility shims for the legacy `outputPerDay` source enum — `CapacityUtilizationReport.Budgeted.source` is just extended with the two new values; existing frontend handles unknown values gracefully (empty pill).
- `OmanRoadProjectSeeder.java` (boot-time seeder) — script-based loading is sufficient.

## 11. File-touch summary

**Backend (Java):**
- `bipros-resource/.../ProductivityNorm.java` — add `projectId`
- `bipros-resource/.../service/ProductivityNormLookupService.java` — extend resolution chain
- `bipros-project/.../DailyActivityResourceOutput.java` — add supervisor fields
- `bipros-project/.../service/DailyActivityResourceOutputService.java` — populate supervisor name
- `bipros-project/.../dto/CreateDailyActivityResourceOutputRequest.java` — require supervisorId
- `bipros-project/.../MonthlyBoqProjection.java` (NEW) + repository, service, DTOs, controller
- `bipros-project/.../DailyResourcePlan.java` (NEW) + repository, service, DTOs, controller
- `bipros-reporting/.../CapacityUtilizationReportService.java` — supervisor split, site-norm, SUMMARY/AVERAGE, supervisor filter
- `bipros-reporting/.../CapacityUtilizationReport.java` — new optional fields
- `bipros-reporting/.../DailyDeploymentMatrixReportService.java` (NEW) + DTO
- `bipros-reporting/.../DprMatrixReportService.java` (NEW) + DTO
- `bipros-reporting/.../ReportController.java` — wire 2 new endpoints + extended params
- `bipros-api/.../db/changelog/` — 4 new changesets

**Frontend (TypeScript):**
- `lib/api/capacityUtilizationApi.ts` — extend
- `lib/api/dailyDeploymentMatrixApi.ts` (NEW)
- `lib/api/dprMatrixApi.ts` (NEW)
- `lib/api/monthlyBoqProjectionApi.ts` (NEW)
- `lib/api/dailyResourcePlanApi.ts` (NEW)
- `lib/api/dailyActivityResourceOutputApi.ts` — add supervisorId to create payload
- `app/(app)/projects/[projectId]/capacity-utilization/page.tsx` — extend
- `app/(app)/projects/[projectId]/daily-deployment-matrix/page.tsx` (NEW)
- `app/(app)/projects/[projectId]/dpr-matrix/page.tsx` (NEW)
- `app/(app)/projects/[projectId]/boq-projections/page.tsx` (NEW)
- `app/(app)/projects/[projectId]/resource-plans/page.tsx` (NEW)
- `app/(app)/projects/[projectId]/layout.tsx` — add 4 new tabs

**Scripts:**
- `scripts/seed-oman-plant-norms.sh` (replaces `seed-oman-equipment.sh`)
- `scripts/seed-oman-manpower-norms.sh` (NEW)
- Python helper(s) under `scripts/lib/` to extract workbook rows

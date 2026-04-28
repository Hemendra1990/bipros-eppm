# Labour Master Module — Design Spec

**Date:** 2026-04-28
**Source brief:** `Labour Master Module Oman with UI Screens.docx` (BIPROS-ORC-LMM-001 v1.0, April 2025)
**Target deployment:** Oman Road Construction Project (initial), reusable across road-construction projects
**Status:** Approved for implementation planning

---

## 1. Goal

Implement a **Labour Master Module**: a catalogue of construction-worker designations (44 in the Oman dataset, across 5 categories) plus a per-project deployment overlay that captures how many workers of each designation a project hires and at what rate. The module exposes 6 UI screens — Dashboard, Cards View, Table View, Detail Modal, Add Form, and a Workforce/Grade reference — backed by a REST API.

This is a **master-data + deployment** module, not an individual-worker HR system. "Worker count" is an aggregate (e.g., `40 General Site Labourers`); the module never tracks individual people.

## 2. Scope and boundaries

**In scope**
- Global catalogue of `LabourDesignation` rows (code, category, trade, grade, skills, certifications, default daily rate).
- Per-project `ProjectLabourDeployment` rows (worker count, optional actual daily rate override, notes).
- Six UI screens for browsing, filtering, viewing detail, and adding new designations.
- KPI roll-ups (total designations, total workforce, daily payroll, nationality mix, by-category sub-totals).
- Seeding the 44 Oman designations + Oman road project deployments.
- OMR-default currency with a `currency` column for future flexibility.

**Out of scope (explicit YAGNI)**
- Individual worker records, attendance, or HR functions.
- Skill/certification dedup as separate master tables (skills/certs are JSON arrays — promote later if a "find all designations needing X cert" query becomes a real requirement).
- Mobilisation/demobilisation date tracking on deployments.
- Multi-currency UI; the column exists but the UI assumes OMR.
- Admin-managed category/grade lookups; both are fixed Java enums.

## 3. Architecture

### 3.1 Module placement

All backend code lives in the existing **`bipros-resource`** Maven module (Postgres schema `resource`). No new Maven module. Layered as:

```
bipros-resource/
└── src/main/java/com/bipros/resource/
    ├── domain/
    │   ├── model/
    │   │   ├── LabourDesignation.java
    │   │   ├── ProjectLabourDeployment.java
    │   │   ├── LabourCategory.java         (enum)
    │   │   ├── LabourGrade.java            (enum)
    │   │   └── NationalityType.java        (enum)
    │   └── repository/
    │       ├── LabourDesignationRepository.java
    │       └── ProjectLabourDeploymentRepository.java
    ├── application/
    │   ├── dto/
    │   │   ├── LabourDesignationRequest.java
    │   │   ├── LabourDesignationResponse.java
    │   │   ├── ProjectLabourDeploymentRequest.java
    │   │   ├── ProjectLabourDeploymentResponse.java
    │   │   ├── LabourCategorySummary.java
    │   │   └── LabourMasterDashboardSummary.java
    │   └── service/
    │       ├── LabourDesignationService.java
    │       └── ProjectLabourDeploymentService.java
    └── presentation/controller/
        ├── LabourDesignationController.java
        └── ProjectLabourDeploymentController.java
```

### 3.2 Frontend placement

New top-level route under `frontend/src/app/(app)/labour-master/`, sibling to `permits/`, `risk/`, `resources/`. The existing `frontend/src/lib/api/labourApi.ts` (daily-return reporting) is **untouched**; a new `labourMasterApi.ts` is added.

### 3.3 Why dedicated entities (not extending `Role`)

The existing `Role` entity in `bipros-resource` serves a different purpose: lightweight resource-typing across all resource categories (Manpower / Equipment / Material) with rate-master extensions feeding the Daily Cost Report. Adding 8+ construction-only columns (trade, grade, nationality, experience, skills, certifications, worker count) onto it would muddy that role. A sibling `LabourDesignation` entity in the same module is cheap and keeps the schema in `resource`.

## 4. Data model

### 4.1 Enums

```java
public enum LabourCategory {
    SITE_MANAGEMENT      ("SM", "Site Management"),
    PLANT_EQUIPMENT      ("PO", "Plant & Equipment Operators"),
    SKILLED_LABOUR       ("SL", "Skilled Labour"),
    SEMI_SKILLED_LABOUR  ("SS", "Semi-Skilled Labour"),
    GENERAL_UNSKILLED    ("GL", "General / Unskilled Labour");

    private final String codePrefix;
    private final String displayName;
}

public enum LabourGrade {
    A, B, C, D, E;
    // band metadata (rate range, classification, description) held as a static map
    // surfaced via /labour-designations/grades for the Grade Reference UI section
}

public enum NationalityType {
    OMANI, EXPAT, OMANI_OR_EXPAT;
}
```

### 4.2 `LabourDesignation` (table `resource.labour_designations`)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | from `BaseEntity` |
| `code` | VARCHAR(20) UNIQUE NOT NULL | `^(SM\|PO\|SL\|SS\|GL)-\d{3}$`, e.g., `SM-001` |
| `designation` | VARCHAR(100) NOT NULL | e.g., `Project Manager` |
| `category` | VARCHAR(30) NOT NULL | `LabourCategory` enum stored as STRING |
| `trade` | VARCHAR(80) NOT NULL | e.g., `Civil Engineering` |
| `grade` | VARCHAR(2) NOT NULL | `LabourGrade` enum |
| `nationality` | VARCHAR(20) NOT NULL | `NationalityType` enum |
| `experience_years_min` | INTEGER NOT NULL | drives the `"15+ yrs"` display |
| `default_daily_rate` | NUMERIC(10,2) NOT NULL | |
| `currency` | VARCHAR(3) NOT NULL DEFAULT `'OMR'` | |
| `skills` | JSONB NOT NULL DEFAULT `'[]'` | `List<String>` via `@JdbcTypeCode(SqlTypes.JSON)` |
| `certifications` | JSONB NOT NULL DEFAULT `'[]'` | same |
| `key_role_summary` | VARCHAR(500) | optional rollup string |
| `status` | VARCHAR(20) NOT NULL DEFAULT `'ACTIVE'` | `ACTIVE` / `INACTIVE` |
| `sort_order` | INTEGER NOT NULL DEFAULT 0 | sequence within category |
| audit columns | from `BaseEntity` | `created_at`, `updated_at`, `created_by`, `updated_by` |

**Indexes**: unique `(code)`, `(category)`, `(grade)`, `(status)`.

### 4.3 `ProjectLabourDeployment` (table `resource.project_labour_deployments`)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `project_id` | UUID NOT NULL | denorm FK to `project.projects` (cross-schema, no DB FK) |
| `designation_id` | UUID NOT NULL | FK to `labour_designations.id` |
| `worker_count` | INTEGER NOT NULL | `>= 0` |
| `actual_daily_rate` | NUMERIC(10,2) | nullable; falls back to designation default |
| `notes` | VARCHAR(500) | nullable |
| audit columns | from `BaseEntity` | |

**Constraints**: unique `(project_id, designation_id)`. Indexes on `(project_id)` and `(designation_id)`.

### 4.4 Computed values

- **Effective rate** for a deployment = `actualDailyRate ?? designation.defaultDailyRate`.
- **Daily cost** for a deployment = `workerCount × effectiveRate`.
- **Category summary**: `Σ workerCount`, `Σ dailyCost`, `min..max grade`, `min..max effective rate`.
- **Dashboard totals**: across all categories for the active project.

### 4.5 Schema-update note (dev profile)

Per the project's `CLAUDE.md`, dev runs `ddl-auto: update`, which only adds columns. New tables and columns introduced here will be created cleanly. Production uses Liquibase; a changelog file under `backend/bipros-api/src/main/resources/db/changelog/` will be added in the implementation plan.

## 5. REST API

All endpoints under `/api/v1/`. Responses wrapped in `ApiResponse<T>`. Security: writes require `ROLE_ADMIN` (mirrors `RoleController`); reads require an authenticated user.

### 5.1 `LabourDesignationController` — global catalogue

| Method | Path | Body / Query | Returns |
|---|---|---|---|
| GET | `/labour-designations` | `category, grade, status, q, page, size, sort` | `PagedResponse<LabourDesignationResponse>` |
| GET | `/labour-designations/{id}` | — | `LabourDesignationResponse` |
| GET | `/labour-designations/by-code/{code}` | — | `LabourDesignationResponse` |
| POST | `/labour-designations` | `LabourDesignationRequest` | `LabourDesignationResponse` |
| PUT | `/labour-designations/{id}` | `LabourDesignationRequest` | `LabourDesignationResponse` |
| DELETE | `/labour-designations/{id}` | — | `204`; soft-delete (`status → INACTIVE`); hard delete only when no deployments reference it |
| GET | `/labour-designations/categories` | — | `[{ code, codePrefix, displayName }]` for filter buttons |
| GET | `/labour-designations/grades` | — | `[{ grade, classification, dailyRateRange, description }]` for the Grade Reference panel |

### 5.2 `ProjectLabourDeploymentController` — per-project overlay

| Method | Path | Body / Query | Returns |
|---|---|---|---|
| GET | `/projects/{projectId}/labour-deployments` | `category, grade, q, page, size` | `PagedResponse<ProjectLabourDeploymentResponse>` (designation joined) |
| GET | `/projects/{projectId}/labour-deployments/dashboard` | — | `LabourMasterDashboardSummary` |
| GET | `/projects/{projectId}/labour-deployments/by-category` | — | `LabourCategorySummary[]` |
| POST | `/projects/{projectId}/labour-deployments` | `ProjectLabourDeploymentRequest` | `ProjectLabourDeploymentResponse` |
| PUT | `/projects/{projectId}/labour-deployments/{id}` | `ProjectLabourDeploymentRequest` | `ProjectLabourDeploymentResponse` |
| DELETE | `/projects/{projectId}/labour-deployments/{id}` | — | `204` |
| POST | `/projects/{projectId}/labour-deployments/bulk` | `{ source: "OMAN_DEFAULT" }` | seeds all 44 designations onto the project at the doc's default counts |

### 5.3 DTO shapes (frontend-facing)

```ts
// Frontend mirror; the Java DTOs are the source of truth
LabourDesignationResponse {
  id: string;
  code: string;
  designation: string;
  category: "SITE_MANAGEMENT" | "PLANT_EQUIPMENT" | "SKILLED_LABOUR" | "SEMI_SKILLED_LABOUR" | "GENERAL_UNSKILLED";
  categoryDisplay: string;
  codePrefix: string;
  trade: string;
  grade: "A" | "B" | "C" | "D" | "E";
  nationality: "OMANI" | "EXPAT" | "OMANI_OR_EXPAT";
  experienceYearsMin: number;
  defaultDailyRate: number;
  currency: string;
  skills: string[];
  certifications: string[];
  keyRoleSummary: string | null;
  status: "ACTIVE" | "INACTIVE";
  sortOrder: number;
  // present only when listed via the per-project endpoint:
  deployment?: {
    id: string;
    workerCount: number;
    actualDailyRate: number | null;
    effectiveRate: number;
    dailyCost: number;
    notes: string | null;
  };
}

LabourCategorySummary {
  category: string;
  categoryDisplay: string;
  designationCount: number;
  workerCount: number;
  dailyCost: number;
  gradeRange: string;          // e.g. "A, B, C"
  dailyRateRange: string;      // e.g. "50 – 125"
  keyRolesSummary: string;     // e.g. "Project Manager, Resident Engineer +6 more"
}

LabourMasterDashboardSummary {
  projectId: string;
  totalDesignations: number;
  totalWorkforce: number;
  dailyPayroll: number;
  currency: string;
  skillCategoryCount: number;
  nationalityMix: { omani: number; expat: number; omaniOrExpat: number };
  byCategory: LabourCategorySummary[];
}
```

### 5.4 Validation

- `code` matches `^(SM|PO|SL|SS|GL)-\d{3}$`; the prefix must agree with `category`.
- `defaultDailyRate >= 0`; `workerCount >= 0`; `experienceYearsMin >= 0`.
- `(projectId, designationId)` unique on deployment — service-layer check first for a clearer error, DB unique constraint as backstop.
- Bean Validation annotations on request DTOs; failures bubble through the existing `GlobalExceptionHandler` in `bipros-common` as `400`.

### 5.5 Error handling

| Condition | HTTP | Source |
|---|---|---|
| Bean validation failure | 400 | `GlobalExceptionHandler` |
| Code/category prefix mismatch | 400 | service throws `IllegalArgumentException` |
| Duplicate `(projectId, designationId)` | 409 | service check, DB constraint backstop |
| Hard-delete designation referenced by deployments | 409 | service check; soft-delete path is recommended |
| Project or designation not found | 404 | repository lookup |

## 6. Frontend

### 6.1 Route layout

```
frontend/src/app/(app)/labour-master/
├── page.tsx                # Screen 1 — Dashboard
├── layout.tsx              # Module shell (header, project context, tabs)
├── cards/page.tsx          # Screens 2 & 3 — Cards View (all 5 categories on one page)
├── table/page.tsx          # Screen 4 — Table View
├── new/page.tsx            # Screen 6 — Add New Designation form
└── [code]/page.tsx         # Designation detail (deep-linkable equivalent of Screen 5 modal)
```

The detail modal (Screen 5) is a client-side modal triggered from Cards / Table / Dashboard. The `[code]/page.tsx` is the same component rendered as a full page for shareable URLs.

### 6.2 API client

`frontend/src/lib/api/labourMasterApi.ts` with two namespaces:

```ts
export const labourMasterApi = {
  designations: {
    list, get, getByCode, create, update, remove,
    listCategories, listGrades,
  },
  deployments: {
    listForProject, getDashboard, getByCategory,
    create, update, remove, bulkSeedOmanDefaults,
  },
};
```

State management via TanStack Query (already used in the app). Cache keys:
- `["labour-designations", filters]`
- `["labour-deployments", projectId, filters]`
- `["labour-deployments-dashboard", projectId]`
- `["labour-deployments-by-category", projectId]`

### 6.3 Component breakdown

Under `frontend/src/components/labour-master/`:

| Component | Used in | Notes |
|---|---|---|
| `KpiTiles` | Dashboard | 5 tiles: Total Designations, Total Workforce, Daily Payroll (OMR), Skill Categories, Nationality Mix |
| `WorkforceCategoryBarChart` | Dashboard | Recharts horizontal bar (already used elsewhere) |
| `CategoryFilterBar` | Cards, Table | 5 category buttons + grade filter + search input |
| `WorkerCard` | Cards | Code, designation, trade, grade badge, count, skill tags, experience, nationality, daily rate |
| `CategoryCardsSection` | Cards | Per-category header (name, totals, sub-total daily cost) wrapping `WorkerCard` grid |
| `WorkerTable` | Table | Compact tabular register, all categories scrollable; columns: Code, Designation, Category, Trade, Grade, Count, Experience, Daily Rate (OMR), Nationality, Status |
| `WorkerDetailModal` | Cards, Table, Dashboard | Screen 5 — full info, skills list, certs, total daily cost calc |
| `AddDesignationForm` | `/new` | Screen 6 — code (prefix auto-filled from category), designation, trade, grade, experience, nationality, count, rate, skills (comma-input → array), certs (comma-input → array), Save / Cancel |
| `WorkforceSummaryTable` | Dashboard footer | Category-by-category roll-up table from the doc |
| `GradeReferenceTable` | Dashboard tab | Grade A–E reference + Oman regulatory notes (read-only, copy from the doc) |

### 6.4 Project context

Active `projectId` comes from the existing project-selector store used by `permits/`, `risk/`, `resources/`. The dashboard renders for the active project; the bulk-seed action is offered when the active project has zero deployments.

### 6.5 Styling

Existing Tailwind + shadcn/ui components, following conventions in `permits/`. Category accents and grade badges are Tailwind tokens defined once at the top of the module.

### 6.6 Next.js 16 caveat

Per `frontend/AGENTS.md`: read the relevant guides under `frontend/node_modules/next/dist/docs/` before writing route files (App Router conventions, server components, caching). Do not rely on Next.js 14/15 conventions from training data.

## 7. Seed data

### 7.1 Boot seeder

`bipros-api/src/main/java/com/bipros/api/config/seeder/OmanLabourMasterSeeder.java`:
- Activates only when the `seed` Spring profile is on (matches `9800652`).
- Idempotent: returns silently if `labour_designations` already has rows.
- Inserts the 44 designations from the source doc with full skill and certification arrays.
- Resolves the Oman road demo project by code/name (existing seed) and inserts 44 `ProjectLabourDeployment` rows with the doc's worker counts and daily rates.

### 7.2 HTTP reset script

`scripts/seed-oman-labour.sh`:
- `admin/admin123` login → `DELETE` deployments → `DELETE` designations → recreate via REST.
- Lets you reset the labour catalogue on a running backend without restart, mirroring `seed-icpms-data.sh` style.

### 7.3 Source-of-truth dataset

The 44 designations and their counts/rates are defined in the source doc (BIPROS-ORC-LMM-001). The implementation plan will translate them into a static dataset (Java record list or JSON resource) used by both the seeder and the bulk-seed endpoint.

## 8. Testing

### 8.1 Backend (JUnit 5 + Spring Boot Test)

- `LabourDesignationServiceTest` — unit: create/update/soft-delete, code-prefix/category validation, rate fallback, list filtering.
- `ProjectLabourDeploymentServiceTest` — unit: deployment CRUD, effective-rate calc, dashboard summary, bulk-seed idempotency.
- `LabourDesignationControllerIT` — `@SpringBootTest` integration: controller surface, security, validation responses, paged listing.
- `ProjectLabourDeploymentControllerIT` — integration: dashboard endpoint, by-category endpoint, bulk seed.
- Repository tests for unique constraints and indexed lookups.

### 8.2 Frontend (Playwright)

`frontend/e2e/labour-master.spec.ts`:
- Dashboard loads with all 5 KPI tiles populated.
- Cards filter by category and grade narrows results correctly.
- Table view renders all 44 rows.
- Detail modal opens with correct data for `SM-001 Project Manager`.
- Add form creates a new designation; it appears in the cards view immediately.

Tests assume the `seed` profile is active (existing pattern).

## 9. Open implementation choices (deferred to plan)

These are intentionally not pinned in this spec — the implementation plan can pick whichever matches local conventions:
- Concrete shape of the static 44-row dataset (Java record list vs `resources/oman-labour-master.json`).
- Whether to surface a "Re-seed Oman defaults" button in the UI for admins, or keep that as the script-only path.
- Concrete placement of the Grade Reference / Oman regulatory notes (Dashboard tab vs separate `/labour-master/reference` page) — the latter may be easier to deep-link for client review.

## 10. Acceptance criteria

- All 6 screens from the source doc are reachable under `/labour-master`, populated from the API, and visually consistent with the screenshots described in the doc.
- Backend seeds 44 Oman designations + 44 deployments for the demo project on first boot under the `seed` profile.
- Adding a new designation via the form persists, validates, and appears in the listing.
- Editing a deployment's `actualDailyRate` updates the dashboard's daily-payroll total.
- Soft-deleting a designation removes it from the active list but does not break existing deployments.
- All backend and Playwright tests pass.

---

**Next step after spec review:** invoke `superpowers:writing-plans` to break this design into ordered, testable implementation steps.

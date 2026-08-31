# Issues Tab Redesign — Design Spec

Date: 2026-06-29
Status: Approved (design), pending implementation plan
Feature area: Project Issues (`DprIssue`), backend `bipros-project` + frontend `issues/*`

## Goal

Fix and improve the Project Issues tab per 8 requested changes: rename a status, wire a real assignee picker, make key fields conditionally mandatory, add a status-change history timeline, polish the edit-form UI/UX, ensure all listed functionality works, and add free-text search to the list.

## Locked decisions

- **"On Hold" rename:** label-only. Display "On Hold" everywhere; stored enum value stays `BLOCKED`. Zero DB migration, zero risk to dashboards/AI/analytics that read the raw literal.
- **Assignee source:** project team (`projectTeamApi.list(projectId)`) — PM/Site Manager/Engineer/Supervisor/QS/Safety org chart. Field issues are owned by on-site staff.
- **Mandatory rules:** conditional (see §3/§5).
- **Text search:** add a debounced search box → new backend `q` param (ILIKE on title + description).
- **History scope:** status-changes only, with actor + optional reason, written synchronously in the service, append-only.

## Grounding (current state, verified at file:line)

- `BLOCKED` is an enum **value** and a display **label**: BE `IssueStatus.java:15`, FE `IssueBadges.tsx:30` label "Blocked", FE union `lib/types/dpr.ts:123`. Persisted as `EnumType.STRING` → DB stores literal `"BLOCKED"`. Hardcoded literal usages: `issues/dashboard/page.tsx:82,181`, `dashboards/project/dashboardDerivations.ts:67`, `ActiveAlertsPanel.tsx:44`, tests `dashboardDerivations.test.ts`, AI `DataGraphCatalog.java:158`, `ListIssuesTool.java:97`.
- List renders status via `row.status.replace("_"," ")` (`issues/page.tsx:192`) — bypasses `STATUS_OPTIONS` labels, shows raw enum text.
- Assigned To is free text only: `issues/new/page.tsx:182-190`, `edit/page.tsx:205-212` bind `assignedToName`. Entity has canonical `assignedToUserId` UUID (`DprIssue.java:97`); PATCH DTO carries it (`UpdateDprIssueRequest.java:31`) but **create DTO does not** — no UI path ever sets it.
- Resolution Notes renders unconditionally on edit form (`edit/page.tsx:216-226`), even for OPEN issues — the dominant confusion. `resolvedAtTerminal()` = {RESOLVED, CLOSED} (`IssueStatus.java:20`); CANCELLED not terminal.
- FE validation is title-only: `new/page.tsx:77`, `edit/page.tsx:101`. BE create requires title/category/severity; patch requires nothing.
- No per-issue status history. Only `DprApprovalHistory` (parent DPR approvals) exists. Status changes emit a generic `AuditLog` (before/after JSON) + a fire-and-forget `DprIssueChangedEvent(projectId, dprId, issueId, oldStatus, newStatus, mutationType)` consumed by analytics ETL — neither is a queryable timeline.
- Filters are server-side (`dprIssueApi.toQuery()` → BE `DprIssueController.list:58-70` → `DprIssueService.list:48-68`, AND-composed). No free-text search param. Date filter targets `reportDate` (`DprIssueService.java:64-65`) but list Date column shows `openedAt` (`page.tsx:217-219`) — a mismatch. Supervisor/activity filters supported in API+BE but have no UI control.

## The 8 changes

### 1. "On Hold" label (label-only)
- `IssueBadges.tsx`: change `STATUS_OPTIONS` entry label `"Blocked"` → `"On Hold"` (value stays `BLOCKED`). Add a `statusLabel(value)` helper mirroring `categoryLabel()`.
- `issues/page.tsx:192`: replace `row.status.replace("_"," ")` with `statusLabel(row.status)` — fixes raw "IN PROGRESS"/"BLOCKED" on the list.
- `issues/dashboard/page.tsx:181`: tile label "Open / Blocked" → "Open / On Hold".
- No BE/enum/migration change. All `=== "BLOCKED"` logic, AI tool strings, analytics untouched.

### 2. Assigned To → project-team picker
- **BE:** add `UUID assignedToUserId` to `CreateDprIssueRequest`; set it on the builder in `DprIssueService.create`. PATCH already supports it.
- **FE types:** add `assignedToUserId` to `CreateDprIssueRequest` in `lib/types/dpr.ts`.
- **FE forms (new + edit):** replace the text input with `SearchableSelect`, options sourced from `projectTeamApi.list(projectId)`. On select, set both `assignedToUserId` (id) and `assignedToName` (display label — kept for the list column and backward-compatible rendering).
- List `ASSIGNED TO` column unchanged (reads `assignedToName`).

### 3 + 5. Conditional mandatory fields + Resolution Notes
- **Status:** always required (assert explicitly; already defaulted).
- **Assigned To:** required when status ∈ {IN_PROGRESS, BLOCKED, RESOLVED, CLOSED}. Not forced on a freshly-Open issue.
- **Resolution Notes:** shown **and** required ONLY when status ∈ {RESOLVED, CLOSED} (terminal set). Hidden otherwise. CANCELLED exempt.
- **FE:** inline per-field error messages on both forms (replace the single top banner). Block submit on violation.
- **BE (authoritative):** enforce cross-field rules in `DprIssueService.create` and `.patch`. After computing resulting status: if terminal and resolution notes blank → throw `BusinessRuleException("DPR_ISSUE_INVALID", …)`; if status requires owner and `assignedToUserId` null → throw. Same pattern as the existing blank-title guard (`DprIssueService.java:81-83`). Cross-field rules live in the service, not bean-validation annotations (they depend on the resulting status).

### 4. Status-change history (new)
- New append-only entity `DprIssueStatusHistory`, modeled on `DprApprovalHistory`:
  - Table `project.dpr_issue_status_history`. Columns: `issue_id` (UUID, indexed), `from_status` (enum string, null on create), `to_status` (enum string), `actor_user_id` (UUID, nullable for system), `reason` (varchar 1000, optional), plus `createdAt`/`createdBy` from `BaseEntity`.
- Write synchronously in the service: one row in `create` (transition `null → initial status`), one row in `patch` whenever `newStatus != oldStatus` (reuse the already-computed old/new at `DprIssueService.java:102`). Synchronous (not via the async event) to preserve actor identity within the transaction.
- Repo method `findByIssueIdOrderByCreatedAtAsc`.
- Endpoint `GET /v1/projects/{projectId}/dpr-issues/{id}/history` → list of history rows (DTO with from/to label, actor name, reason, timestamp).
- **FE:** `dprIssueApi.history(projectId, issueId)` + a timeline panel on the edit page.
- Dev `ddl-auto: update` auto-creates the table; prod needs a Liquibase changeset under `bipros-api/.../db/changelog/`.

### 6. Edit-form UI/UX
Restructure `edit/page.tsx` into sections:
1. **Read-only header strip:** logged-by (`supervisorName`) · report date · opened · resolved.
2. **Editable core:** Title; [Category | Severity | Status]; Description; [Activity | Assigned-To picker].
3. **Resolution block:** appears only for terminal status; required notes.
4. **History timeline** panel (fed by §4 endpoint).
Inline field errors throughout. Apply the same header + picker + inline-error polish to the New form (sections 1–2; no history/resolution on create).

### 7. Functionality verification
Broken/missing items fixed by the above: list status-label rendering (§1), assigned-to user linkage (§2), status history (§4), conditional/mandatory validation (§3/§5). Already-OK and to be regression-checked: list, create, edit, delete-with-confirm, inline status change from list, status/severity/category/date filters, `resolvedAt` auto-stamp on RESOLVED/CLOSED, dashboard KPIs.

### 8. Search + filter correctness
- New BE `q` param on the list endpoint → case-insensitive contains on title + description. FE debounced search box wired into the existing filter object + React Query key.
- Fix the Date-column/filter mismatch: align the list Date column to show `reportDate` (what the date filter acts on).
- Orphaned supervisor/activity filters: leave out of the UI (YAGNI). No removal of the BE params.

## Testing strategy (TDD)

- **BE:** extend `DailyProgressReportServiceIssuesTest`; add `DprIssueService` tests for: conditional-mandatory throws (terminal-without-notes, owner-required-without-assignee), history-row writes on create + status change, `q` filter matching. Run against a real Postgres for the `q`/filter paths (nullable JPQL param cast gotcha — see project memory).
- **FE:** form validation + conditional-render tests (resolution block visibility, assigned-to required threshold), `statusLabel` rendering.
- Write the failing test first for each behavioral change, then implement.

## Out of scope

- Renaming the stored enum value to `ON_HOLD` (label-only chosen).
- Pagination on the list (demo scale; flagged as a future scaling concern).
- Field-level (non-status) edit history.
- Supervisor/activity filter UI.

## Key files

Backend: `bipros-project/.../domain/model/{DprIssue,IssueStatus,DprApprovalHistory}.java`, `application/service/DprIssueService.java`, `application/dto/{Create,Update}DprIssueRequest.java`, `api/DprIssueController.java`, new `domain/model/DprIssueStatusHistory.java` + `domain/repository/DprIssueStatusHistoryRepository.java` + history DTO, `bipros-common/.../event/DprIssueChangedEvent.java`, prod Liquibase changelog under `bipros-api/.../db/changelog/`.
Frontend: `app/(app)/projects/[projectId]/issues/{page,new/page,[issueId]/edit/page,dashboard/page}.tsx`, `components/dpr/IssueBadges.tsx`, `lib/api/dprIssueApi.ts`, `lib/api/projectTeamApi.ts`, `lib/types/dpr.ts`, `components/common/SearchableSelect.tsx` (reuse), `team/page.tsx` AddTeamMemberDialog (reference pattern).

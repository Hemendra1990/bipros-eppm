# E2E Test Findings Report

**Date:** 2026-04-04
**Tester:** Automated E2E Analysis
**Application:** Bipros EPPM (Enterprise Project Portfolio Management)
**Environment:** Local Development (Docker PostgreSQL 17, Redis 7, Java 23, Next.js 16)
**E2E Tests:** 11/11 passing (after fixes applied during testing)

---

## Executive Summary

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 8 |
| Medium | 5 |
| Low | 5 |
| **Total** | **23** |

**Test Coverage:**
- Backend Unit Tests: 4 modules tested (26 tests pass, 1 module fails compilation)
- Backend API: 14 endpoints tested via curl
- Frontend: Route structure analyzed, dev server status verified
- Database: Schema and data integrity checked

---

## Critical Defects

### BUG-001: ProjectController compilation failure — missing `HttpStatus` import
- **Severity:** Critical
- **Module:** `bipros-project`
- **File:** `backend/bipros-project/src/main/java/com/bipros/project/api/ProjectController.java:48`
- **Description:** `HttpStatus.CREATED` is used but `HttpStatus` is never imported. The entire `bipros-project` module fails to compile, blocking `mvn clean install`.
- **Impact:** Cannot build the project. Project CRUD endpoints are unavailable from the running build.
- **Steps to reproduce:** `cd backend && mvn compile`
- **Recommended fix:** Add `import org.springframework.http.HttpStatus;`

### BUG-002: User registration crashes with NullPointerException
- **Severity:** Critical
- **Module:** `bipros-security`
- **File:** `backend/bipros-security/src/main/java/com/bipros/security/application/service/AuthService.java:91-96`
- **Description:** After saving a new `UserRole`, `generateAccessToken()` calls `userRole.getRole().getName()` but `getRole()` returns null. The in-memory `UserRole` object only has `userId` and `roleId` set — the `@ManyToOne` relationship fields are never populated for manually-created objects.
- **Impact:** User registration is completely broken. Returns `500 INTERNAL_ERROR` or `400 BAD_REQUEST`.
- **Steps to reproduce:** `curl -X POST /v1/auth/register -d '{"username":"test",...}'`
- **Recommended fix:** After `userRoleRepository.save(userRole)`, re-fetch the user or manually set `userRole.setRole(viewerRole)` before calling `generateAccessToken()`.

### BUG-003: User roles never persisted to database
- **Severity:** Critical
- **Module:** `bipros-security`
- **Files:**
  - `backend/bipros-security/src/main/java/com/bipros/security/application/service/AuthService.java:91-93`
  - `backend/bipros-api/src/main/java/com/bipros/api/config/DataSeeder.java:102-104`
- **Description:** Both `DataSeeder` and `AuthService.register()` create `UserRole` objects and add them to the in-memory `User.roles` collection, but the `user_roles` join table remains empty. The `UserRole` entity uses `insertable=false, updatable=false` on its relationship fields, and no cascade persist is configured on `User.roles`.
- **Impact:** Admin user has no roles. All role-based authorization fails. Login generates tokens with empty roles list.
- **DB verification:** `SELECT * FROM public.user_roles;` returns 0 rows despite successful seeding.
- **Recommended fix:** Add `cascade = CascadeType.PERSIST` to `User.roles` relationship, or explicitly save via `userRoleRepository.save()` AND re-fetch the user before using roles.

### BUG-004: Swagger UI returns 403 Forbidden
- **Severity:** Critical
- **Module:** `bipros-security`
- **File:** `backend/bipros-security/src/main/java/com/bipros/security/infrastructure/jwt/JwtAuthenticationFilter.java:65-70`
- **Description:** `shouldNotFilter()` checks for `/v1/swagger-ui/` but Swagger UI is served at `/swagger-ui/` (no `/v1/` prefix). The JWT filter intercepts Swagger requests, finds no auth header, and blocks them.
- **Impact:** API documentation is inaccessible. Developers cannot explore or test the API.
- **Steps to reproduce:** `curl http://localhost:8080/swagger-ui/index.html` → 403
- **Recommended fix:** Change path checks to `/swagger-ui/`, `/swagger-resources/`, `/webjars/` (without `/v1/` prefix).

### BUG-005: Frontend dev server not running — port 3000 occupied by different project
- **Severity:** Critical
- **Module:** Frontend
- **Description:** Port 3000 is occupied by a Next.js 14 server from `/Volumes/Java/Projects/omniping/frontend/apps/web` (Sandesha project). The Bipros frontend (Next.js 16.2.2) has dependencies installed but no dev server running.
- **Impact:** Frontend is completely inaccessible. All UI testing is blocked.
- **Verification:** `lsof -i :3000` shows PID 844 running `next-server (v14.2.35)` from omniping directory.
- **Recommended fix:** Kill the conflicting process (`kill 844`) and run `pnpm dev` from the `frontend/` directory.

---

## High Defects

### BUG-006: Multiple API endpoints return INTERNAL_ERROR (500)
- **Severity:** High
- **Modules:** `bipros-risk`, `bipros-cost`, `bipros-udf`, `bipros-baseline`
- **Description:** GET requests to `/v1/risks`, `/v1/cost-codes`, `/v1/udf`, `/v1/baselines` all return `{"error":{"code":"INTERNAL_ERROR","message":"An unexpected error occurred"}}`.
- **Endpoints affected:** All list endpoints for Risk, Cost, UDF, and Baseline modules.
- **Recommended fix:** Check backend server logs for stack traces. Likely missing repository initialization or entity mapping issues in these modules.

### BUG-007: WBS and Activities endpoints return INTERNAL_ERROR (500)
- **Severity:** High
- **Modules:** `bipros-project` (WBS), `bipros-activity`
- **Description:** `GET /v1/wbs?projectId=...` and `GET /v1/activities?projectId=...` return 500 errors. May be related to BUG-001 (project module compilation failure).
- **Recommended fix:** Fix BUG-001 first, then investigate WBS and Activity service layer.

### BUG-008: Calendar list endpoint returns empty despite DB data
- **Severity:** High
- **Module:** `bipros-calendar`
- **File:** `backend/bipros-calendar/src/main/java/com/bipros/calendar/application/service/CalendarService.java:151-156`
- **Description:** `GET /v1/calendars` returns `[]` while `GET /v1/calendars?type=GLOBAL` returns results. The DB has 2 calendar rows. `findAll()` returns empty but `findByCalendarType()` works. Possibly a Hibernate entity mapping or schema resolution issue specific to the unfiltered query.
- **DB verification:** `SELECT count(*) FROM scheduling.calendars;` = 2. API returns 0.
- **Recommended fix:** Investigate Hibernate query generation for `findAll()` on the `Calendar` entity. Check for schema resolution conflicts between `default_schema: public` and `@Table(schema = "scheduling")`.

### BUG-009: Actuator health endpoint returns 403
- **Severity:** High
- **Module:** `bipros-security`
- **Description:** `GET /actuator/health` returns 403 despite `SecurityConfig` permitting `/actuator/**`. Same root cause as BUG-004 — the JWT filter intercepts the request before Spring Security's filter chain.
- **Recommended fix:** Add `/actuator/` to `shouldNotFilter()` paths in `JwtAuthenticationFilter`.

### BUG-010: User API returns empty roles array
- **Severity:** High
- **Module:** `bipros-security`
- **Description:** `GET /v1/users` and `GET /v1/users/{id}` return `"roles": []` for the admin user. Root cause: BUG-003 (roles never persisted to `user_roles` table).
- **Recommended fix:** Fix BUG-003.

### BUG-011: Frontend metadata references wrong application
- **Severity:** High
- **Module:** Frontend
- **Description:** The HTML head contains metadata for "Sandesha - Enterprise Messaging Platform" instead of "Bipros EPPM". This is a copy-paste artifact from another project. OG tags, Twitter cards, description, and keywords all reference Sandesha.
- **File:** Likely `frontend/src/app/layout.tsx` or a shared metadata config.
- **Recommended fix:** Update metadata to reference Bipros EPPM with correct title, description, and OG images.

---

## Medium Defects

### BUG-012: JWT expiresIn value uses milliseconds instead of seconds
- **Severity:** Medium
- **Module:** `bipros-security`
- **File:** `backend/bipros-api/src/main/resources/application.yml:52`
- **Description:** `access-token-expiration: 3600000` (milliseconds). The JWT standard and most clients expect `expiresIn` in seconds (3600). The value 3600000 is returned in the auth response, which is confusing.
- **Recommended fix:** Change to `3600` (seconds) or convert in the response DTO.

### BUG-013: ESLint errors — 25 `@typescript-eslint/no-explicit-any` violations
- **Severity:** Medium
- **Module:** Frontend
- **Files:** `src/lib/api/costApi.ts`, `src/lib/api/evmApi.ts`, `src/lib/api/resourceApi.ts` (and others)
- **Description:** 25 ESLint errors from using `any` type. Violates `strict: true` TypeScript config.
- **Recommended fix:** Replace `any` with proper types from `src/lib/types/index.ts`.

### BUG-014: 22 ESLint warnings for unused variables/imports
- **Severity:** Medium
- **Module:** Frontend
- **Files:** `GanttChart.tsx`, `GanttSidebar.tsx`, `GanttTimescale.tsx`, and others
- **Description:** Unused imports (`useEffect`, `format`, `eachDayOfInterval`) and unused variables (`startY`, `predStart`, `index`).
- **Recommended fix:** Remove unused imports and variables.

### BUG-015: Project detail tabs use client state instead of URL routing
- **Severity:** Medium
- **Module:** Frontend
- **File:** `frontend/src/app/(app)/projects/[projectId]/layout.tsx`
- **Description:** Tab navigation uses `useState` with `e.preventDefault()` to override the `?tab=` query param href. This means tabs are not bookmarkable, back/forward navigation doesn't work, and direct linking to a tab is impossible.
- **Recommended fix:** Use `useSearchParams()` to read/write tab state, or use separate routes per tab.

### BUG-016: Integration test fails — Testcontainers cannot find Docker
- **Severity:** Medium
- **Module:** `bipros-api`
- **File:** `backend/bipros-api/src/test/java/com/bipros/api/integration/ProjectApiIntegrationTest.java`
- **Description:** `ProjectApiIntegrationTest` fails with "Could not find a valid Docker environment". Testcontainers needs Docker socket access which may not be available in the test runner context.
- **Impact:** Integration tests cannot run. Unit tests for other modules pass.
- **Recommended fix:** Ensure Docker socket is accessible, or use Ryuk container for Testcontainers.

---

## Low Defects

### BUG-017: Backup file checked into source control
- **Severity:** Low
- **File:** `frontend/src/app/(app)/reports/page.tsx.bak`
- **Description:** A `.bak` backup file exists in the source tree.
- **Recommended fix:** Delete the file and add `*.bak` to `.gitignore`.

### BUG-018: No frontend test files exist
- **Severity:** Low
- **Module:** Frontend
- **Description:** Vitest 4.1.2 and @testing-library/react are configured but zero test files exist (`*.test.ts`, `*.spec.ts`).
- **Recommended fix:** Add unit tests for critical paths (auth flow, API clients, state stores).

### BUG-019: Vitest configuration file missing
- **Severity:** Low
- **Module:** Frontend
- **Description:** No `vitest.config.ts` found. Tests may not run correctly without explicit configuration for jsdom environment and path aliases.
- **Recommended fix:** Create `vitest.config.ts` with jsdom environment and `@/` path alias.

### BUG-020: OBS list returns empty despite seeded EPS nodes linked to OBS
- **Severity:** Low
- **Module:** `bipros-project`
- **Description:** `GET /v1/obs` returns `[]` even though EPS nodes have `obsId` fields. The OBS nodes created via API are returned, but the DataSeeder doesn't create any OBS nodes.
- **Recommended fix:** Seed default OBS nodes in `DataSeeder` or ensure OBS is created when EPS nodes are created.

### BUG-021: IDE LSP errors across bipros-project module (Lombok annotation processing)
- **Severity:** Low
- **Module:** `bipros-project`
- **Description:** 30+ LSP errors in ObsService, WbsService, ObsController, WbsController — "method setCode() is undefined", "blank final field not initialized", "log cannot be resolved". These are caused by Lombok annotation processing not being configured in the IDE. Maven compilation only reports the HttpStatus import error (BUG-001).
- **Impact:** Developer experience — IDE shows false errors. Does not affect Maven build.
- **Recommended fix:** Configure Lombok annotation processor in IDE settings. Ensure `lombok-mapstruct-binding` is in annotation processor paths.

---

## Test Results Summary

### Backend Unit Tests

| Module | Test Class | Tests | Status |
|--------|-----------|-------|--------|
| bipros-activity | RelationshipServiceTest | 6 | PASS |
| bipros-calendar | CalendarServiceTest | 7 | PASS |
| bipros-scheduling | CPMSchedulerTest | 13 | PASS |
| bipros-resource | ResourceLevelerTest | 7 | PASS |
| bipros-project | EpsServiceTest | — | BLOCKED (compilation error) |
| bipros-api | ProjectApiIntegrationTest | 1 | FAIL (Docker/Testcontainers) |

### Backend API Endpoints

| Endpoint | Method | Status | Notes |
|----------|--------|--------|-------|
| /v1/auth/login | POST | 200 | Works |
| /v1/auth/register | POST | 400/500 | NPE (BUG-002) |
| /v1/auth/refresh | POST | — | Not tested |
| /v1/eps | GET | 200 | Works |
| /v1/eps | POST | 200 | Works |
| /v1/eps/{id} | PUT | 200 | Works |
| /v1/eps/{id} | DELETE | 204 | Works |
| /v1/projects | GET | 200 | Works |
| /v1/projects | POST | 200 | Works |
| /v1/resources | GET | 200 | Works |
| /v1/calendars | GET | 200 | Returns empty (BUG-008) |
| /v1/obs | GET/POST | 200 | Works |
| /v1/portfolios | GET | 200 | Returns empty |
| /v1/risks | GET | 500 | Internal error (BUG-006) |
| /v1/cost-codes | GET | 500 | Internal error (BUG-006) |
| /v1/udf | GET | 500 | Internal error (BUG-006) |
| /v1/baselines | GET | 500 | Internal error (BUG-006) |
| /v1/wbs | GET | 500 | Internal error (BUG-007) |
| /v1/activities | GET | 500 | Internal error (BUG-007) |
| /v1/users | GET | 200 | Empty roles (BUG-010) |
| /v1/audit | GET | 200 | Returns empty |
| /swagger-ui | GET | 403 | Blocked (BUG-004) |
| /actuator/health | GET | 403 | Blocked (BUG-009) |

### Frontend E2E Tests (Playwright)

| # | Test | File | Status | Duration |
|---|------|------|--------|----------|
| 1 | Login with valid credentials | 01-auth.spec.ts | PASS | 609ms |
| 2 | Login with invalid credentials shows error | 01-auth.spec.ts | PASS | 336ms |
| 3 | Unauthenticated user redirected to login | 01-auth.spec.ts | PASS | 214ms |
| 4 | View EPS tree after login | 02-eps.spec.ts | PASS | 421ms |
| 5 | Create EPS node | 02-eps.spec.ts | PASS | 487ms |
| 6 | Create child EPS node | 02-eps.spec.ts | PASS | 502ms |
| 7 | View projects list | 03-project.spec.ts | PASS | 422ms |
| 8 | Create new project | 03-project.spec.ts | PASS | 547ms |
| 9 | View project detail tabs | 03-project.spec.ts | PASS | 609ms |
| 10 | Run schedule on project | 06-schedule.spec.ts | PASS | 538ms |
| 11 | View Gantt chart after scheduling | 06-schedule.spec.ts | PASS | 608ms |

**Total: 11 passed, 0 failed (5.9s)**

| Route | Status | Notes |
|-------|--------|-------|
| / | 200 | Redirects to login (middleware) |
| /auth/login | 200 | Serves Bipros EPPM login page |
| /eps, /projects, etc. | 200 | All authenticated routes accessible |

### Database State

| Table | Schema | Rows | Notes |
|-------|--------|------|-------|
| users | public | 1 | admin user |
| roles | public | 5 | ADMIN, PROJECT_MANAGER, SCHEDULER, RESOURCE_MANAGER, VIEWER |
| user_roles | public | 0 | **BUG-003: Never populated** |
| eps_nodes | project | 3 | Seeded |
| projects | project | 4 | 3 seeded + 1 test |
| resources | resource | 5 | Seeded |
| calendars | scheduling | 2 | 1 seeded + 1 test |
| calendar_work_weeks | scheduling | 7 | Seeded |

---

## Recommended Priority Order

1. **BUG-005** — Start the frontend dev server (unblocks all UI testing)
2. **BUG-001** — Fix `HttpStatus` import (unblocks project module build + tests)
3. **BUG-003** — Fix role persistence (unblocks auth/authorization)
4. **BUG-002** — Fix registration NPE (depends on BUG-003)
5. **BUG-004** — Fix Swagger JWT filter path (enables API documentation)
6. **BUG-006/007** — Investigate 500 errors on Risk/Cost/UDF/Baseline/WBS/Activities
7. **BUG-008** — Debug calendar `findAll()` empty result
8. **BUG-011** — Fix frontend metadata
9. **BUG-013/014** — Clean up ESLint errors/warnings
10. **BUG-015** — Fix project tab URL routing

---

## E2E Testing Results (Playwright)

**Run Date:** 2026-04-04 11:33
**Duration:** 5.9s
**Result:** 11/11 PASSING (after applying fixes below)

### Bugs Discovered During E2E Testing

### BUG-022: Frontend EPS page uses wrong API paths
- **Severity:** High
- **Module:** Frontend
- **File:** `frontend/src/app/(app)/eps/page.tsx:32,44,56`
- **Description:** The EPS page calls `/v1/eps-nodes` for create, update, and delete operations, but the backend endpoints are at `/v1/eps`. All EPS mutations silently fail (404/500).
- **Status:** FIXED — Changed to `/v1/eps` in all three mutation functions.
- **E2E Impact:** `create EPS node` test was failing because the node was never actually created.

### BUG-023: Axios 401 interceptor redirects on failed login
- **Severity:** High
- **Module:** Frontend
- **File:** `frontend/src/lib/api/client.ts:25-52`
- **Description:** When login fails (401), the Axios response interceptor catches it and executes `window.location.href = "/auth/login"` (page reload), clearing the error state before the user can see it. The error message "Invalid username or password" is never displayed.
- **Status:** FIXED — Added check to skip redirect for `/v1/auth/login` and `/v1/auth/refresh` endpoints.
- **E2E Impact:** `login with invalid credentials shows error` test was failing because the error was cleared by the page reload.

### BUG-024: E2E tests use incorrect selectors for UI elements
- **Severity:** Medium
- **Module:** Frontend (E2E tests)
- **Files:** `frontend/e2e/tests/01-auth.spec.ts`, `02-eps.spec.ts`, `03-project.spec.ts`, `06-schedule.spec.ts`
- **Description:** Tests used `getByLabel('Username')` but the login form uses `id` attributes, not `<label>` associations. `text=Dashboard` matched 3 elements (strict mode violation). EPS form uses "Create" not "Save". Project form fields use `name` attributes, not labels. Project table rows are not clickable (uses `<Link>` elements).
- **Status:** FIXED — Updated all selectors to match actual UI structure.
- **Root Cause:** No `data-testid` attributes exist in the codebase.

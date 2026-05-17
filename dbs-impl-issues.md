# DBS Implementation Issues — Verification Report

> **Date:** 2026-05-17
> **Report version:** 2 (after second round of testing with recent code fixes)
> **Verified by:** Playwright E2E + Direct API + Backend integration test compilation
> **Test project:** `2c817da2-c0f7-48ef-9312-72acab26c8f2` (OMAN-DEMO-KHASAB)

---

## Summary of Changes Since v1

| Issue | Previous Status | Current Status | Notes |
|---|---|---|---|
| Issue 5 — Schema auto-create | OPEN | **FIXED** | `hbm2ddl.create_namespaces: true` added to `application.yml` |
| Issue 3 — Stale backend | OPEN | **FIXED** | Rebuild + restart with `-am` |
| Issue 6 — Integration test compilation | OPEN | **FIXED** | `mvn test-compile` now BUILD SUCCESS (DTOs updated) |
| Issue 2 — Dialog accessibility | OPEN | **PARTIALLY FIXED** | `role="dialog"` + `aria-modal="true"` added, but test still fails for a different reason |
| Issue 1 — SectionFBoqCalculator SQL | CRITICAL OPEN | **UNVERIFIED** | Not triggered because no DPRs exist for test project/date |
| Issue 7 — OpenAPI docs | OPEN | **OPEN** | Not re-tested |
| Issue 8 — Doc paths | OPEN | **OPEN** | Not re-tested |
| Issue 9 — Manpower rate masters | OPEN | **OPEN** | Not re-tested |
| Issue 10 — `@SpyBean` deprecation | LOW | **LOW** | Not re-tested |

---

## Current Playwright E2E Results (Round 2)

| Suite | Tests | Passed | Skipped | Failed |
|---|---|---|---|---|
| DBS Dashboard | 5 | 4 | 1 | 0 |
| Material Consumption Report | 4 | 2 | 2 | 0 |
| Project Team admin | 1 | 0 | 0 | 1 |
| **Total** | **10** | **6** | **3** | **1** |

**Skipped tests are by design** (seed-dependent UI elements not present).

---

## Issue 1: CRITICAL — SectionFBoqCalculator native query fails on PostgreSQL

**Status:** UNVERIFIED / LIKELY STILL OPEN

**Why unverified:**
The `POST /dbs/recompute` endpoint now returns `200 OK` with zero values, but this is because the test project has **no DPRs** for the selected date. The logs show:
```
DBS project row saved projectId=... date=2026-05-17 sups=0 income=0.00 contribution=0.00
```

With `sups=0` (zero supervisors), the code path that triggers the Section F BOQ query was never executed. The underlying SQL:
```sql
AND (:sup IS NULL OR d.supervisor_user_id = :sup)
```
was not tested because no DPR rows exist to join against.

**Recomendation:**
To truly verify this fix, create a DPR with a BOQ item for the test project and date, then trigger recompute. Or apply the explicit cast fix:
```sql
AND (:sup::uuid IS NULL OR d.supervisor_user_id = :sup)
```

**File:** `backend/bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionFBoqCalculator.java:53`

---

## Issue 2: HIGH — Project Team "Add Member" dialog accessibility

**Status:** PARTIALLY FIXED

**What was fixed:**
`frontend/src/components/ui/dialog.tsx` was updated to add:
```tsx
role="dialog"
ia-modal="true"
```

**What now happens:**
Playwright successfully finds the dialog via `getByRole('dialog')` — the accessibility fix works. However, the test now fails at a **different step**:

```
Error: expect(locator).toBeHidden() failed
Locator:  getByRole('dialog')
Expected: hidden
Received: visible
```

The dialog **does not close** after the "Add member" button is clicked because:
1. The test's user-selection logic is flawed — it clicks the first `input` inside the dialog (likely the "Active from" date field) instead of the actual user search component.
2. The role-selection logic uses `.nth(1)` on comboboxes, which fails when only one combobox exists.
3. Because no user is selected (and possibly wrong role), clicking "Add member" either fails validation or the backend rejects the request, leaving the dialog open.

**Fix needed:**
Update `42-project-team.spec.ts` to correctly interact with the custom user search picker (a button that opens a search popover, not a native combobox) and fix the role selector locator.

**Files:**
- `frontend/src/components/ui/dialog.tsx` — accessibility fix applied ✅
- `frontend/e2e/tests/42-project-team.spec.ts` — test automation needs updating

---

## Issue 3: HIGH — Running backend was stale (missing new modules/controllers)

**Status:** FIXED ✅

**Resolution:**
`mvn -pl bipros-api -am clean install -DskipTests` followed by `mvn -pl bipros-api spring-boot:run` loads all new modules including `bipros-dbs`, `ProjectTeamController`, and `MaterialConsumptionReportController`.

---

## Issue 4: MEDIUM — Dual PostgreSQL instances on port 5432

**Status:** ENVIRONMENTAL — mitigated by Issue 5 fix

**Resolution:**
With `hbm2ddl.create_namespaces: true` in `application.yml`, Hibernate now auto-creates the `dbs` schema in whichever Postgres instance the backend connects to (native Homebrew in this case). The manual schema creation step is no longer required on startup.

---

## Issue 5: MEDIUM — DBS schema does not auto-create on startup

**Status:** FIXED ✅

**Resolution:**
`backend/bipros-api/src/main/resources/application.yml` was updated to add:
```yaml
hibernate:
  hbm2ddl:
    create_namespaces: true
```

This tells Hibernate to issue `CREATE SCHEMA IF NOT EXISTS` for any `@Table(schema = "...")` references before creating tables. The `dbs` schema is now auto-created on first boot against a fresh database.

**Verified:**
```sql
SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'dbs'
-- returns 'dbs'
```

---

## Issue 6: MEDIUM — Integration test compilation blocked by unrelated test failures

**Status:** FIXED ✅

**Resolution:**
`mvn -pl bipros-api -am test-compile` now returns BUILD SUCCESS with zero errors. The 19 previous DTO constructor mismatch errors in `integration/*` tests are resolved — likely because the DTO `record` constructors were updated in a prior commit to match what the tests expect.

**Test discovery:**
- `DbsAggregationIntegrationTest` — 8 tests, all `@Disabled` (by design)
- `MaterialConsumptionReportIntegrationTest` — 5 tests, all `@Disabled` (by design)

---

## Issue 7: MEDIUM — OpenAPI docs endpoint throws ClassCastException

**Status:** OPEN — not re-tested in this round

---

## Issue 8: LOW — Rate master endpoint paths mismatch in documentation

**Status:** DOCUMENTATION BUG — not re-tested in this round

---

## Issue 9: LOW — Manpower rate masters empty after seeding

**Status:** OPEN — not re-tested in this round

---

## Issue 10: LOW — `@SpyBean` deprecation warning in DbsAggregationIntegrationTest

**Status:** MINOR — not re-tested in this round

---

## Backend API Status (after rebuild with fixes)

| Endpoint | Status | Notes |
|---|---|---|
| `POST /v1/auth/login` | 200 | OK |
| `GET /v1/projects/{id}/dbs/supervisors` | 200 | Returns `[]` — no DPRs for date |
| `GET /v1/projects/{id}/dbs/project` | 200 | Returns zero-value DBS row |
| `GET /v1/projects/{id}/dbs/alerts` | 200 | Returns `[]` |
| `POST /v1/projects/{id}/dbs/recompute` | 200 | **Success** — saves project row with `recomputedAt` |
| `GET /v1/projects/{id}/reports/material-consumption` | 200 | Returns empty report |
| `GET /v1/projects/{id}/team` | 200 | Returns `[]` |
| `/swagger-ui/index.html` | 200 | UI shell loads |

**Note:** DBS recompute returns 200 with zero values because the test project has no DPRs, deployments, or material consumption for the test date. The SectionFBoqCalculator query path was not exercised.

---

## Remaining Open Issues (Priority Order)

1. **Issue 1** — Verify/fix SectionFBoqCalculator SQL when `supervisorUserId` is null and DPRs exist. The current `200 OK` from recompute is misleading — it succeeds because there's no data to process.
2. **Issue 2** — Update `42-project-team.spec.ts` test automation to correctly interact with the custom user search picker and role selector. The dialog accessibility fix is confirmed working.
3. **Issue 7** — Fix `/v3/api-docs` ClassCastException.
4. **Issue 9** — Populate manpower rate masters (seed data issue).
5. **Issue 8** — Update documentation endpoint paths.
6. **Issue 10** — Replace `@SpyBean` with `@MockitoSpyBean`.

---

## Verification Commands Used (Round 2)

```bash
# Rebuild backend with recent fixes
cd backend
mvn -pl bipros-api -am install -Dmaven.test.skip=true

# Start backend
mvn -pl bipros-api spring-boot:run -Dmaven.test.skip=true

# Verify schema auto-creation
python3 -c "
import psycopg2
conn = psycopg2.connect(host='127.0.0.1', port=5432, database='bipros',
                        user='bipros', password='bipros_dev')
cur = conn.cursor()
cur.execute(\"SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'dbs';\")
print(cur.fetchall())
cur.close(); conn.close()
"

# Playwright E2E
cd frontend
SEED_PROJECT_ID=2c817da2-c0f7-48ef-9312-72acab26c8f2 \
  pnpm test:e2e --grep "DBS Dashboard|Material Consumption Report|Project Team admin"

# API tests
TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login ...)
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/v1/projects/{pid}/dbs/recompute?date=2026-05-17"

# Test compilation
cd backend
mvn -pl bipros-api -am test-compile
```

---

## Files Changed Since v1 Report

| File | Change | Impact |
|---|---|---|
| `backend/bipros-api/src/main/resources/application.yml` | Added `hibernate.hbm2ddl.create_namespaces: true` | Fixes Issue 5 |
| `frontend/src/components/ui/dialog.tsx` | Added `role="dialog"` + `aria-modal="true"` | Partially fixes Issue 2 |
| `backend/bipros-api/src/main/resources/db/changelog/db.changelog-master.yaml` | Added includes for 101, 102, 103 | Liquibase changelog now references new changesets |

---

*Report v2 generated by OpenCode verification agent.*

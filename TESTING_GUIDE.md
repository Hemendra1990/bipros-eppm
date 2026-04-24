# Bipros EPPM — End-to-End Testing Guide

**Audience:** QA / Release engineers testing the application from a clean environment for the first time.
**Scope:** Every user-facing feature, in the order a new user would naturally discover them, with sample inputs and expected calculation outputs so you can verify correctness — not just "the page rendered".

> **Use this as a learning path.** Start at the top. Each section builds on the state left by the previous one. Don't jump around on the first pass.

---

## 0 · Environment setup (10 minutes)

### 0.1 Prerequisites

| Tool | Min version | Used for |
|------|-------------|----------|
| Docker Desktop | 24.x | Postgres (PostGIS), Redis, MinIO |
| Java | 23 | Backend (Spring Boot 3.5) |
| Maven | 3.9 | Backend build |
| Node.js | 20 | Frontend |
| pnpm | 9.x | Frontend package manager |

### 0.2 Start infrastructure

```bash
cd /Volumes/Java/Projects/bipros-eppm
docker compose up -d postgresql redis minio
```

Wait for `docker compose ps` to show all three as **healthy**.

- Postgres: `localhost:5432` (user `postgres` / password `postgres_password`, also `bipros`/`bipros_dev`; db `bipros`)
- Redis: `localhost:6379`
- MinIO: `http://localhost:9001` (login `minio` / `minio123`)
- pgAdmin (optional): `http://localhost:5050` (`admin@bipros.local` / `admin`)

### 0.3 Start the backend (dev profile)

```bash
cd backend/bipros-api
mvn spring-boot:run
```

The dev profile uses `ddl-auto: create-drop` and runs the seeders on every boot, so the DB is repeatable. First boot takes ~45 s.

**Health check**
```bash
curl -s http://localhost:8080/actuator/health
# → {"status":"UP"}
```

**Swagger UI** for discovery: <http://localhost:8080/swagger-ui.html>

### 0.4 Start the frontend

```bash
cd frontend
pnpm install          # first time only
pnpm dev
```

Open <http://localhost:3000>. You should be redirected to `/auth/login`.

### 0.5 Login

| Field | Value |
|-------|-------|
| Username | `admin` |
| Password | `admin123` |

After login you should see the Dashboard at `/`.

### 0.6 Seeded data you'll use in this guide

| Project code | Purpose | Activities | BOQ items | Stretches | Sources |
|--------------|---------|-----------|-----------|-----------|---------|
| `DMIC-PROG` | Delhi-Mumbai programme | 34 | — | 5 | 4 |
| `BIPROS/NHAI/RJ/2025/001` | **NH-48 Rajasthan (primary test vehicle)** | 15 | 15 | 5 | 4 |
| `WO70143247` | IOCL Panipat | 26 | — | — | — |

This guide uses **NH-48** throughout. In the UI, open **Projects** and click the one with code `BIPROS/NHAI/RJ/2025/001`.

### 0.7 Production-profile smoke (optional but recommended before deploy)

```bash
JWT_SECRET="$(openssl rand -hex 32)" \
DATABASE_URL="jdbc:postgresql://db-host:5432/bipros_prod" \
DB_USERNAME=prod_user \
DB_PASSWORD='…' \
CORS_ALLOWED_ORIGINS="https://eppm.example.com" \
SPRING_PROFILES_ACTIVE=prod \
java -jar backend/bipros-api/target/bipros-api-0.1.0-SNAPSHOT.jar
```

The app **will refuse to boot** in `prod` if `JWT_SECRET` is still the dev default — this is intentional.

---

## 1 · Authentication (5 min)

### 1.1 Login success path
1. Go to `/auth/login`.
2. Enter `admin` / `admin123` → **Sign in**.
3. Expect redirect to `/` and the **Dashboard** heading.

### 1.2 Login failure path
1. Same form, password `wrong_pw`.
2. Expect inline error *"Invalid username or password"*.

### 1.3 Session protection
1. While logged in, copy the URL `http://localhost:3000/projects`.
2. Open a private window and paste.
3. Expect redirect to `/auth/login`.

### 1.4 API reality check
```bash
curl -s http://localhost:8080/v1/projects
# → 401 (not 403) — defect-log BUG-036 fix

curl -s -X POST http://localhost:8080/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' | jq .data.accessToken
# → long JWT

export TOKEN="<paste the token>"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/auth/me
# → 200, user profile (defect-log BUG-035 fix)
```

---

## 2 · Master Data — 10 screens from the PMS document

You'll walk each PMS MasterData screen in document order. Every screen has **list, create, verify** phases. Sample inputs are given so re-running is deterministic.

### Screen 01 · Project Master

**UI:** Click **Projects** in the sidebar.

**Verify the enriched view:** Click the NH-48 row. The detail page should show:
- **Category**: `HIGHWAY`
- **MoRTH Code**: `NH-48`
- **From/To Chainage**: `145000` → `165000`, equivalent to **Km 145+000 → Km 165+000**
- **Total Length**: `20.000 km` (auto = (165000−145000)/1000)

**Create a new project** (*)
1. **Projects → + New Project**.
2. Sample input:
   - Code: `TEST-NH101`
   - Name: `NH-101 Expansion`
   - EPS Node: pick any from the dropdown
   - Planned Start: `2026-09-01`
   - Planned Finish: `2028-03-31`
   - Priority: `5 – Critical`
   - Category: `EXPRESSWAY`
   - MoRTH Code: `NH-101`
   - From Chainage (m): `0`, To Chainage (m): `45000` → expected **Total Length = 45.000 km** after save
   - Primary Contract: `CT-TEST-001`, type `EPC`, value `2500000000` (₹2.5 Cr), revised `2650000000`, DLP `60`
3. **Create Project** → expected redirect to `/projects/<id>` and the detail page shows the contract summary block.

**Negative test — date order** (defect-log BUG-001)
1. Open `/projects/new`.
2. Planned Finish = `2026-01-01`, Planned Start = `2027-12-31`.
3. Submit → expect **422 / validation error**: *"plannedFinishDate must be on or after plannedStartDate"*.

**Negative test — PAN-like injection in code** (BUG-004)
1. Code: `QA'; DROP TABLE--`.
2. Submit → expect **400**: code pattern rejected.

### Screen 02 · Contractor Master

**UI:** sidebar **Admin → Organisations** (label reads *"Contractor & Organisation Master"*).

**Existing list:** Should show 15 seeded organisations (DMICDC, AECOM-TYPSA, LNT-IDPL, etc.) with Type badges and PAN / GSTIN / Contact columns.

**Create contractor**
1. **+ New Organisation**.
2. Sample:
   - Name: `Larsen & Toubro Ltd`
   - Type: `MAIN_CONTRACTOR`
   - PAN: `AAACL0140P`  → regex-validated
   - GSTIN: `27AAACL0140P1Z8`  → regex-validated
   - City: `Mumbai`, State: `Maharashtra`, Pincode: `400001`
   - Contact: `Site Office Manager`, Mobile `+91 98765 43210`, Email `sm.nh48@lnt.example`
   - Registration Status: `ACTIVE`
3. **Create** → row appears with code auto-generated `CONT-XXX`.

**Negative — bad PAN** (BUG-005-level validation)
- PAN: `XYZ1234` → expected *"PAN must match AAAAA0000A format"*.

### Screen 03 · BOQ & Activity Master

**UI:** open NH-48 project → tab **BOQ & Budget**.

**Expect 15 BOQ rows** seeded from workbook. Columns now include **Chapter** and **Status** (from defect-log extension).

**Calculation check** — pick `1.1 Earthwork Excavation`:
- BOQ Qty × BOQ Rate = BOQ Amount (displayed in footer grand-total row).
- % Complete = Qty Executed ÷ BOQ Qty × 100.
- Status auto-transitions: 0 → `PENDING`; >0 <100 → `ACTIVE`; 100 → `COMPLETED`.

**Expected NH-48 grand totals (from seeder log):**
- BOQ stored = **₹217,885,000**
- Budget stored = **₹223,460,000**
- Actual stored = **₹20,535,950**

### Screen 04 · Resource Master — Equipment

**UI:** sidebar **Resources**.

**Expect 18 NH-48 equipment/material/sub-contract resources** + 32 DMIC resources.

**Create equipment**
1. **+ New Resource**.
2. Sample:
   - Name: `Vibratory Roller 12T`
   - Type: `NONLABOR` (this unlocks the Equipment Specifications panel)
   - Capacity: `12 MT`
   - Make/Model: `JCB VM 115D`
   - Quantity: `2`, Ownership: `OWNED`, Fuel: `12 L/h`, Output/day: `800 Cum`, Unit: `Cum`
   - Hourly rate: `2500`
3. **Create** → code auto-generates to `EQ-NNN` (NONLABOR) / `LAB-NNN` (LABOR) / `MAT-NNN` (MATERIAL).

### Screen 05 · Unit Rate Master

**UI:** sidebar **Admin → Unit Rate Master**.

**Expect 23+ rows** — aggregated from NH-48 BUDGETED + ACTUAL rates, DMIC resources, and manpower roles.

**Calculation verification** — pick any Cement row:
- `variance = actualRate − budgetedRate`
- `variance % = variance ÷ budgetedRate × 100`
- If `|variance %| > 5` the row is highlighted red.

**Create a rate via API** (UI CRUD is read-only by design; writes go through the resource detail endpoint):
```bash
# Pick any resource id
RID=$(curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/resources \
  | jq -r '.data[0].id')
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/v1/unit-rate-master/resources/$RID/rates \
  -d '{"rateType":"STANDARD","category":"EQUIPMENT","budgetedRate":2000,"actualRate":2200,"effectiveDate":"2026-04-01","maxUnitsPerTime":8}' | jq
# → variance=200, variancePercent=10 (will render red in the UI)
```

**Negative — overlapping effective range** (defect-log BUG-028 extension)
Post a second rate with `STANDARD`/`EQUIPMENT`/same resource/effectiveDate `2026-05-01` → expect **422 `OVERLAPPING_RATE`**.

### Screen 06 · Chainage & Stretch Master (NEW)

**UI:** NH-48 project → **Stretches** tab.

**Expect 5 stretches** with chainage math visible:
- `STR-001` km 145..149, length 4000 m
- `STR-002` km 149..153, 4000 m
- …
- `STR-005` km 161..165, 4000 m

**Progress calculation** (weighted BOQ %):
```
% complete = Σ(qtyExecuted × boqRate) / Σ(boqQty × boqRate) × 100
```

Expected values for NH-48 after seed:

| Stretch | Linked BOQ items | Total BOQ (₹) | Executed (₹) | % Complete |
|---------|-----------------|---------------|--------------|-----------:|
| STR-001 | 3 | 32,625,000 | 3,795,250 | **11.63%** |
| STR-002 | 3 | 17,126,000 | 1,379,000 | **8.05%** |
| STR-003 | 3 | 26,346,000 | 1,076,400 | **4.09%** |
| STR-004 | 3 | 98,088,000 | 1,044,000 | **1.06%** |
| STR-005 | 3 | 43,700,000 | 12,724,000 | **29.12%** |

Hover over the progress bar in the UI to verify the percentage per row.

**Create a new stretch**
1. **+ New Stretch**.
2. Sample: name `Zone F (km 165..170)`, from `166+000`, to `167+000`, package `PKG-F`, target date `2027-09-30`.
3. Expect row in the list with **length 1000m** auto-computed.

**Negative — zero-length stretch** (from `StretchService`)
- from `150+000`, to `150+000` → expected `422 INVALID_CHAINAGE_RANGE`.

### Screen 07 · Personnel & Supervisor Master

**UI:** sidebar **Admin → Users**.

**Expect 21 IC-PMS users** (admin + 20 seeded roles). Each now has the personnel fields per defect-log pass:
- Employee Code (auto `EMP-NNN`)
- Department, Mobile, Joining date, Contract end
- Presence status

**Edit a user**
```bash
# Get a non-admin user id
UID=$(curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/users?size=5" \
  | jq -r '.data.content[1].id')
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  http://localhost:8080/v1/users/$UID \
  -d '{"mobile":"+91 98765 11111","department":"CIVIL","presenceStatus":"ON_SITE","joiningDate":"2025-06-01"}' | jq .data
# → returns the updated user with the PMS fields set
```

### Screen 08 · Borrow Area & Source Master (NEW)

**UI:** NH-48 project → **Material Sources** tab.

**Expect 4 sources** (2 borrow areas + 1 quarry + 1 bitumen depot). Each has:
- Auto-coded by type: `BA-NNN`, `QRY-NNN`, `BD-NNN`, `CEM-NNN`
- Denormalised Lab Test Status (`ALL_PASS` / `TESTS_PENDING` / `ONE_OR_MORE_FAIL`) derived from linked tests

**Create a source with lab tests (API)**
```bash
PID=$(curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/v1/projects?size=10" \
  | jq -r '.data.content[] | select(.code|contains("NHAI"))|.id')
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  http://localhost:8080/v1/projects/$PID/material-sources -d '{
    "sourceType":"BORROW_AREA",
    "name":"Beawar Borrow Area",
    "village":"Beawar","district":"Ajmer","state":"Rajasthan",
    "distanceKm":15.8,"approvedQuantity":80000,"approvedQuantityUnit":"CU_M",
    "cbrAveragePercent":19.2,"mddGcc":2.063,
    "labTests":[
      {"testName":"CBR","standardReference":"IS 2720 Pt.16","resultValue":19.2,"resultUnit":"%","passed":true,"testDate":"2026-03-10"},
      {"testName":"Liquid Limit","standardReference":"IS 2720 Pt.5","resultValue":26,"resultUnit":"%","passed":true}
    ]
  }' | jq '.data | {sourceCode, labTestStatus, labTestCount: (.labTests|length)}'
# → sourceCode: "BA-003", labTestStatus: "ALL_PASS", labTestCount: 2
```

### Screen 09a · Material Catalogue (NEW)

**UI:** NH-48 project → **Material Catalogue** tab.

**Create a material and exercise full procurement pipeline:**

```bash
MAT=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  http://localhost:8080/v1/projects/$PID/materials -d '{
    "name":"Cement OPC-43","category":"CEMENT","unit":"MT",
    "specificationGrade":"IS 8112:2013","minStockLevel":100,
    "reorderQuantity":200,"leadTimeDays":5}' | jq -r .data.id)
```

### Screen 09b · Stock & Inventory Register (NEW)

**UI:** NH-48 project → **Stock Register** tab.

**Procurement cycle — step by step with expected outputs:**

**Step 1 — GRN (purchase 500 MT @ ₹8,000/MT)**
```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  http://localhost:8080/v1/projects/$PID/grns -d "{
    \"materialId\":\"$MAT\",\"receivedDate\":\"2026-04-24\",
    \"quantity\":500,\"unitRate\":8000}" | jq .data
# Expected: grnNumber "GRN-YYYYMM-0001", amount = 500×8000 = ₹4,000,000
```

**Step 2 — Issue 100 MT**
```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  http://localhost:8080/v1/projects/$PID/issues -d "{
    \"materialId\":\"$MAT\",\"issueDate\":\"2026-04-24\",
    \"quantity\":100,\"wastageQuantity\":2}" | jq .data
# Expected: challanNumber "ISS-YYYYMM-0001"
```

**Step 3 — Stock Register**
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v1/projects/$PID/stock-register \
  | jq '.data[] | select(.materialId=="'$MAT'")'
# Expected:
#   currentStock = 400
#   stockValue   = 400 × 8000 = ₹3,200,000     ← defect-log fix: updates on issue
#   stockStatusTag = "OK"                      ← because 400 > min 100
#   wastagePercent ≈ 2                         ← 2 / 100 × 100
```

**Step 4 — Issue enough to cross LOW threshold**
Issue another **320 MT** → current stock = 80, min = 100, so `80 < 100` but `80 > 30` (which is 30% of 100).
Expected: `stockStatusTag = "LOW"`

**Step 5 — Cross CRITICAL threshold**
Issue another **60 MT** → current = 20, which is below 30% of min.
Expected: `stockStatusTag = "CRITICAL"`

**Step 6 — Insufficient stock guard**
Try to issue **100 MT** → expected `422 INSUFFICIENT_STOCK "Cannot issue 100 MT — only 20.000 on hand"`.

**Step 7 — DPR bridge (defect-log integration)**
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/v1/projects/$PID/material-consumption?fromDate=2026-04-01&toDate=2026-04-30" \
  | jq '.data[] | select(.logDate=="2026-04-24") | {materialName, consumed, closingStock, wastagePercent}'
# → one row per material issued today, aggregating all issues.
```

**Step 8 — Cost summary reflects material procurement**
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v1/projects/$PID/cost-summary | jq .data
# Expected:
#   materialProcurementCost: 4000000    ← Σ GRN amounts
#   openStockValue:          160000     ← 20 × 8000 (after all issues)
#   materialIssuedCost:      3840000    ← procurement − open stock
#   (if only Step 1 and Step 2 happened: open=3200000, issued=800000)
```

---

## 3 · Scheduling (CPM) & critical path

**UI:** NH-48 project → **Schedule Health** (or the **Gantt** tab).

**Run scheduler (API)**
```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/v1/projects/$PID/schedule -d '{}' | jq .data
```

**Expected output (NH-48):**
- `totalActivities`: **15**
- `criticalActivities`: **15** (all on a single chain given the seeded 14 FS relationships)
- `projectFinishDate`: 2028-07-11 (deterministic given seeded durations)
- `criticalPathLength`: ~1103 working days

**Critical path endpoint** (defect-log BUG-023/044 verification):
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v1/projects/$PID/schedule/critical-path | jq '.data | length'
# Expected: 15 (NOT 0 — the fix)
```

**Float paths**:
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v1/projects/$PID/schedule/float-paths | jq '.data | length'
# Expected: >= 1, each with TF == 0 for the critical chain
```

**Key calculations to verify in the Gantt tab:**
| Metric | Formula | Source |
|--------|---------|--------|
| Total Float (TF) | `lateStart − earlyStart` (working-day delta) | per-activity |
| Free Float (FF) | `succ.earlyStart − pred.earlyFinish` | computed FS-by-FS |
| Critical | `TF <= minTotalFloat` | min-float set |
| Project Finish | `max(earlyFinish)` across all activities | rollup |

---

## 4 · Cost & EVM

**UI:** NH-48 project → **Costs** tab.

### 4.1 Cost Summary cards
Expect six classic cards + **Material Procurement** block (the three new procurement cards) when the project has GRN activity.

### 4.2 EVM calculation
```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/v1/projects/$PID/evm/calculate \
  -d '{"technique":"ACTIVITY_PERCENT_COMPLETE","etcMethod":"CPI_BASED"}' | jq .data
```

Key fields & formulas:
| Metric | Formula |
|--------|---------|
| BAC | Σ `ActivityExpense.budgetedCost` |
| PV (BCWS) | Σ budget × planned % complete to date |
| EV (BCWP) | Σ budget × actual % complete |
| AC (ACWP) | Σ `ActivityExpense.actualCost` |
| CV | EV − AC |
| SV | EV − PV |
| **CPI** | **EV / AC** — null when AC=0 (defect-log BUG-010 fix) |
| SPI | EV / PV |
| EAC (CPI_BASED) | BAC / CPI |
| ETC | EAC − AC |

### 4.3 Cash Flow S-curve
On the Costs tab, pick forecast method `CPI_BASED`, `SPI_BASED` or `SPI_CPI_COMPOSITE` and verify the S-curve reshapes. Composite = divides remaining budget by (CPI × SPI).

---

## 5 · Resource leveling

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/v1/projects/$PID/resource-assignments/level \
  -d '{"mode":"LEVEL_WITHIN_FLOAT"}' | jq .data
```

Valid `mode` values (defect-log BUG-043):
- `LEVEL_WITHIN_FLOAT` (also accepts `AUTOMATIC`)
- `LEVEL_ALL`
- `SMOOTH`

Expect `movedActivities` (count of activities whose ES shifted), `overallocationsRemaining`, `maxDailyDemand` per resource.

---

## 6 · Risks

**UI:** NH-48 → **Risk** tab (and `/risk` for the global register).

- 22 seeded risks across 4 RAG bands.
- `GET /v1/projects/{id}/risks/summary` → aggregate summary (defect-log BUG-040 fix: no longer 500)
- `GET /v1/projects/{id}/risk-matrix` → 5×5 probability × impact matrix (BUG-041)

**RiskProbability accepts** (BUG-039):
- Strings: `VERY_LOW … VERY_HIGH`
- Integers 1-5
- Decimals 0.0-1.0 (bucketed: <0.2 → VERY_LOW, …, ≥0.8 → VERY_HIGH)

---

## 7 · Baselines

1. NH-48 → **Baselines** tab → **Create**.
2. Type `PROJECT`, name `NH-48 Approved Baseline v1`.
3. Expected response (defect-log BUG-037/038): snapshot captures `totalActivities=15`, `totalCost≈₹223,460,000`, `projectStartDate`, `projectFinishDate`.
4. Re-create with the same name → **422 DUPLICATE_CODE** (BUG-037).

---

## 8 · Reports & Dashboards

- `GET /v1/reports` → list of report definitions (BUG-033)
- `GET /v1/dashboards` → list of configured tiers (BUG-033)
- Dashboards: `/dashboards/executive`, `/programme`, `/operational`, `/field`.

---

## 9 · Defect-log regression matrix

Run these in order as a **smoke test** after deploy. Each line maps to one BUG in the original QA log; all should now pass.

| BUG | Check |
|-----|-------|
| 001 | Project create with finish-before-start → 422 |
| 002 | Project priority 9999 → 400 |
| 004 | Project code `QA'; DROP TABLE--` → 400 |
| 007/013/023/044 | `/projects/{id}/schedule/critical-path` returns ≥ 1 activity |
| 010 | Empty project cost summary has `costPerformanceIndex: null` (not 1.0) |
| 011 | Paged responses expose `totalElements` at root |
| 012 | `POST /v1/projects/{id}/schedule` with empty body `{}` works |
| 015 | `PUT /v1/projects/{p}/wbs/{id}` with `parentId=<same>` → 422 |
| 016 | `POST /v1/projects/{p}/wbs` with minimum body → wbsType/status/level auto-defaulted |
| 017 | Activity create with finish-before-start → 422 |
| 018 | Milestone activity create with non-zero duration → normalized to 0 |
| 019 | Activity PUT rename without `percentComplete` → 200 |
| 020 | Invalid enum value → clean "must be one of [A,B,C]" error, no package names |
| 026 | Resource create with `unitOfMeasure:HOURS` → unit persisted (alias) |
| 032 | `/projects/{id}/funding-sources` → 200 |
| 033 | `/v1/reports` and `/v1/dashboards` → 200 |
| 034 | `/v1/udf` returns catalog; `/v1/udf/fields` without `subject` returns all |
| 035 | `/v1/auth/me` with Bearer → 200 |
| 036 | Unauthenticated request → 401 (not 403) |
| 037 | Baseline duplicate name → 422 DUPLICATE_CODE |
| 038 | Baseline snapshot has totalActivities / totalCost / dates populated |
| 040 | `/risks/summary` → 200 (not 500) |
| 041 | `/risk-matrix` → 200 |
| 042 | Risk create accepts `name` OR `title`, and `category` OR `riskCategory` |
| 043 | `LevelingMode: AUTOMATIC` accepted (maps to LEVEL_WITHIN_FLOAT) |
| 046 | `GET /v1/projects/{p}/relationships/{id}` → 200 |

Run the automated version:
```bash
cd frontend
pnpm test:e2e          # Playwright — 18 tests, ~11s
```

Expected: **18 passed**.

---

## 10 · Common calculation quick reference

This is the single source of truth for "what should this number be?"

### Chainage ↔ metres
- `km X+YYY` ↔ `X * 1000 + YYY` metres
- Length (km) = (to − from) / 1000

### BOQ
- BOQ Amount = boqQty × boqRate
- Budgeted Amount = boqQty × budgetedRate
- % Complete = qtyExecuted / boqQty × 100
- Earned Value = qtyExecuted × budgetedRate
- Cost Variance = actualAmount − Earned Value
- Variance % = CV / EV

### Unit Rate Master
- variance = actualRate − budgetedRate
- variance% = variance / budgetedRate × 100
- red highlight when |variance%| > 5

### Stock
- currentStock = openingStock + Σ GRNs − Σ Issues
- stockValue = currentStock × latest GRN unitRate
- Status: ≥ min → OK; < min, ≥ 30% of min → LOW; < 30% of min → CRITICAL
- wastage% (per issue) = wastageQuantity / quantity × 100

### Stretch progress
- cost-weighted % complete = Σ(qtyExecuted × boqRate) / Σ(boqQty × boqRate) × 100
- length = toChainageM − fromChainageM

### Schedule (CPM)
- Early Start / Finish: forward pass, working-day arithmetic with activity calendar
- Late Start / Finish: backward pass from projectFinishDate
- Total Float = LS − ES (in working days; half-open interval)
- Free Float = succ.ES − pred.EF (for FS relationships)
- Critical = TF ≤ min(TF) across all activities

### EVM
- CPI = EV / AC (null when AC=0)
- SPI = EV / PV
- EAC (CPI-based) = BAC / CPI
- ETC = EAC − AC
- Cash Flow forecast (composite) = remainingBudget × (periodBudget / totalFuture) / (CPI × SPI)

### Resource assignment planned cost
- plannedCost = plannedUnits × (budgetedRate ?? pricePerUnit)
- actualCost = actualUnits × (actualRate ?? budgetedRate ?? pricePerUnit)

---

## 11 · What's tested automatically

### Backend unit tests
```bash
cd backend
mvn -DskipTests=false test -pl bipros-project,bipros-scheduling,bipros-resource,bipros-cost,bipros-security,bipros-common,bipros-calendar -am
```
Expected: **80+ tests green**, no failures. Key suites:
- `CPMSchedulerTest` — 13 scheduling scenarios (simple chain, parallel paths, lag/lead, constraints, circular-dependency detection)
- `CalendarServiceTest` — 8 working-day arithmetic tests
- `GlobalExceptionHandlerTest` — 8 tests (malformed JSON, enum errors, type mismatch)
- `FormulaEvaluatorTest` — 83 UDF expression tests
- `WbsServiceTest`, `RelationshipServiceTest`, `BoqCalculatorTest`, `ImanConoverTest`, `DistributionSamplerTest`

### Frontend Playwright
```bash
cd frontend
pnpm test:e2e
```
**18 tests**, ~11 s:
- Authentication (login, invalid creds, protected route)
- EPS tree renders
- Project lifecycle (list, new form, detail tabs)
- Schedule (run CPM, Gantt)
- PMS MasterData (projects, contractors, unit-rate, stretches, material sources, catalogue, stock register, new-stretch end-to-end)

---

## 12 · Operator runbook

### 12.1 Required env vars in prod

| Variable | Example | Notes |
|----------|---------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates `ddl-auto: validate` + Liquibase + error-detail hiding |
| `DATABASE_URL` | `jdbc:postgresql://db:5432/bipros` | |
| `DB_USERNAME` / `DB_PASSWORD` | `bipros_prod` / `…` | Use a secrets manager |
| **`JWT_SECRET`** | **`$(openssl rand -hex 32)`** | **Prod refuses to boot without this; dev default is blocked** |
| `CORS_ALLOWED_ORIGINS` | `https://eppm.example.com` | Comma-separated |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `3600000` | ms (1 h default) |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `86400000` | ms (1 day default) |
| `ANTHROPIC_API_KEY` | `sk-ant-…` | Optional; satellite progress analyzer |
| `SENTINEL_HUB_CLIENT_ID` / `_SECRET` | | Optional; Sentinel ingestion |
| `NEXT_PUBLIC_API_URL` | `https://api.eppm.example.com` | Frontend build-time URL |

### 12.2 First-time prod bootstrap

1. Load schema: Liquibase runs on first boot (validate mode only reads). If starting from scratch, take a dev dump or run entity DDL manually.
2. Seed `admin` user via the `InitialAdminSeeder` or manual SQL insert with bcrypted password.
3. Point the frontend at the prod API via `NEXT_PUBLIC_API_URL`.
4. Confirm `GET /actuator/health` returns `{"status":"UP"}` and Swagger is reachable.
5. Optional: disable Swagger in prod by removing `springdoc` starter or gating behind an IP allowlist.

### 12.3 What rebuilds cleanly vs what migrates

| Profile | Schema behavior | Use case |
|---------|----------------|----------|
| `dev` | `create-drop` every boot; seeders repopulate | Local dev, demos |
| `test` | In-memory / isolated | CI |
| `prod` | `validate` — Hibernate refuses to boot if the live schema drifts from entities; Liquibase applies migrations from `bipros-api/src/main/resources/db/changelog/` | Real deployment |

**Before deploying** a schema change to prod:
1. Write the Liquibase changeset.
2. Apply on a staging DB that mirrors prod.
3. Run `mvn install` with `SPRING_PROFILES_ACTIVE=prod` pointing at staging — any entity/schema mismatch fails fast.

### 12.4 Troubleshooting

| Symptom | Check |
|---------|-------|
| Frontend loops between `/auth/login` and `/` | Access token expired; clear `localStorage` + cookies |
| API returns 403 on every request | JWT signing key mismatch between instances; verify `JWT_SECRET` is identical everywhere |
| `/auth/me` returns 403 | You're hitting an old build before the defect-log pass — redeploy |
| Scheduler returns 404 on GET before any POST | Expected; first GET auto-runs (defect-log BUG-007 fix) |
| Seeders didn't run | Profile is not `dev`; check `spring.profiles.active` |
| Liquibase fails with "Cannot apply changeset" | Prod DB has old schema; roll forward the missing changesets or reset via DBA |

---

**Revision:** 2026-04-24 — covers all 46 original defects, 10 PMS MasterData screens, cost integration, stretch progress, and prod hardening.

# IOCL Panipat WO — UI verification findings

Playwright walkthrough against the seeded project `WO70143247-170908` (UUID `b03fce59-5fbc-445a-a99c-b769154dff86`). 28 screenshots in `docs/iocl-panipat-screenshots/`. Backend hit after the §1–§5 fixes from `docs/iocl-panipat-wo-findings.md`.

## Overall

**Data path:** backend returns correct numbers; EVM backend reports SPI 0.01 / CPI 0.0 / BAC ₹18.94 Cr. **UI path:** values render but with **5 clear UI bugs** (currency symbol, contract-value unit, resource/activity-name hydration, empty rollups, UDF visibility) and **2 new backend bugs** surfaced by the §4 fix (endpoint mismatches returning 500 for what should be 404/405).

**Verdict:** system is usable end-to-end for demo, but every screen that shows money is wrong, and three tabs have stale/blank data that will confuse a customer.

## Summary table

| # | Severity | Area | Issue | Screenshot |
|---|---|---|---|---|
| U1 | **P1** | Resources tab (project) | Resource Name + Activity Name columns **blank** — join not hydrating | `10-project-resources.png` |
| U2 | **P1** | Costs tab | Total Budget / Actual / Remaining / At Completion all show **₹0.00cr** despite 24 expenses totaling ₹16.7 Cr | `11-project-costs.png` |
| U3 | **P1** | Contracts | Contract value `₹189,370,825.01 cr` — suffix "cr" applied to raw rupees (off by 10⁷) | `13-contracts.png` |
| U4 | **P1** | Currency inconsistency | EVM tab + Baselines show `$` (USD); Costs tab shows `₹` (INR correctly). Backend is INR | `12-project-evm.png`, `23-baselines.png` |
| U5 | **P2** | /admin/udf | "No fields defined" for Project subject — lists GLOBAL scope only, hides 7 PROJECT-scoped fields | `17-udf-project.png` |
| B1 | **P1** | Frontend API client | Calls `GET /v1/projects/{id}/critical-path` → **500** (actual endpoint is `/schedule/critical-path`) | `08-project-activities.png` console |
| B2 | **P2** | Frontend API client | Calls `GET /v1/dashboards/PROGRAMME` → **404** on first load; dashboard then renders from a fallback | `20-dashboards-programme.png` |
| B3 | **P3** | Backend exception handler | `NoResourceFoundException` and `HttpRequestMethodNotSupportedException` return HTTP 500, should be 404 / 405 | `/v1/udf/fields/{id}` test |
| M1 | P3 | Projects list | Priority rendering inconsistent — `Critical` (word) vs `P100` (code+number) | `05-projects-list.png` |
| M2 | P3 | Calendars list | No "Project" column on list, can't tell which project owns a PROJECT-type calendar | `16-calendars.png` |
| M3 | P3 | Network diagram | ES/EF/LS/LF/Float labels render but values are dim/hard to read; could overlap when activities are many | `25-network.png` |
| M4 | P3 | Portfolios card | Card body shows raw `projects` text instead of a count; description truncates at `...` without tooltip | `21-portfolios.png` |
| M5 | P3 | Navigation nit | Clicking a sidebar area when the project page is loading can navigate to the sidebar route | observed during session |
| ✅ | — | Login / Dashboard / EPS / OBS / WBS / Activities list / Gantt / Baselines presence / Contracts card / Risk matrix / Resources list / Calendars list / User Access / Dashboards hub / GIS / Network | All render with correct seeded data | rest of screenshots |

---

## Detail

### U1 — P1 · Resources tab: blank Resource Name / Activity Name columns

**Observed** (`10-project-resources.png`): the Resource Assignments table shows 22 rows with `Planned Units`, `Actual Units`, `Rate Type`, `Planned Cost`, `Actual Cost` all populated — but the first two columns (`Resource Name` and `Activity Name`) are **entirely blank** for every row.

**Backend state:** `GET /v1/projects/{id}/resource-assignments` returns `activityId` and `resourceId` UUIDs but does NOT embed the `resourceName` / `activityName`. The frontend needs to either (a) join client-side by looking them up from the activities/resources already fetched, or (b) ask the backend to include the names.

**Fix recommendation:** include `resourceName` and `activityName` in `ResourceAssignmentResponse` on the backend — cheap JPA fetch join, and avoids N+1 on the client.

### U2 — P1 · Costs tab: rollup is ₹0 but expense count is 24

**Observed** (`11-project-costs.png`): Total Budget ₹0.00cr, Total Actual ₹0.00cr, Total Remaining ₹0.00cr, At Completion ₹0.00cr. Cost Variance (CV) shows ₹18.94cr (full contract value). CPI = 1.0000. Expenses = 24.

**Backend state:** `GET /v1/projects/{id}/cost-summary` appears to return 0 for all aggregate fields — the expenses exist (24 rows) but their `budgetedCost` isn't being summed into `totalBudget`. Either the API sums `actualCost` (which is 0 since we have no actuals) into `totalBudget`, or the expenses aren't being picked up by the summary query.

**Fix recommendation:** trace `CostService.getProjectCostSummary` — likely a SUM over the wrong column or the query filters by a status that excludes PLANNED expenses.

### U3 — P1 · Contract value shows "cr" suffix on raw rupees

**Observed** (`13-contracts.png`): `Value: ₹189,370,825.01 cr | Type: ITEM RATE FIDIC RED`. Actual contract value is ₹18,93,70,825.01 (≈ 18.94 Cr) — the backend stores it in rupees, and the frontend is both **printing the raw number** AND **appending " cr"**. Result: 189 million crores = 1.89 × 10¹³ rupees (wildly wrong).

**Fix recommendation:** in the contract card formatter, pick one: either divide by `10^7` before appending "cr", or drop the "cr" suffix and keep full rupees with thousands separator.

### U4 — P1 · Currency symbol inconsistency ($ vs ₹)

**Observed:**
- EVM tab (`12-project-evm.png`): PV `$189,373,977.01`, EV `$2,535,838.13`, AC `$0.00`, SV `$-186,838,138.89`, CV `$2,535,838.13` — all **`$` (USD)**.
- Baselines tab (`23-baselines.png`): Total Cost `$189,373,977.01` — **`$`**.
- Costs tab (`11-project-costs.png`): Total Budget `₹0.00cr` — **`₹`**.
- Contracts (`13-contracts.png`): Value `₹189,370,825.01` — **`₹`**.

Backend currency is INR. Project currency should be driven by a single formatter that reads from either project.currencyCode or a global setting. Currently at least two different formatters are in play.

**Fix recommendation:** audit uses of `Intl.NumberFormat` / `toLocaleString` in the frontend. Introduce a `formatCurrency(value, project?)` helper in `src/lib/utils` and route all money displays through it.

### U5 — P2 · `/admin/udf` hides project-scoped fields

**Observed** (`17-udf-project.png`): clicking through Activity / Resource Assignment / WBS / Project tabs all show "No fields defined", despite 7 PROJECT-scoped UDFs existing in the DB (confirmed via API).

**Root cause:** the admin page calls `GET /v1/udf/fields?subject=PROJECT` without `scope=PROJECT&projectId=...` so the backend returns only GLOBAL-scoped fields. But there are no GLOBAL fields either, so the page shows empty.

**Fix recommendation:** Two equally valid options:
1. Add a project filter at the top of `/admin/udf` so users can see per-project fields.
2. Have the admin page explicitly list GLOBAL fields only, and move PROJECT-scoped management into the project detail page. Note which one the product intent was.

### B1 — P1 · Frontend calls wrong critical-path endpoint

**Observed:** Activities tab loads with 2 console errors (`08-project-activities.png` console log):
```
GET http://localhost:8080/v1/projects/{id}/critical-path → 500
```
The endpoint that actually exists is `GET /v1/projects/{id}/schedule/critical-path` — the `/schedule` segment is missing in the frontend call.

As a side benefit from §4: the 500 body now reads `NoResourceFoundException: No static resource v1/projects/.../critical-path.` — that's the dev-mode diagnostic working as intended.

**Fix recommendation:** in `frontend/src/lib/api/*.ts` grep for `/critical-path` and update to `/schedule/critical-path`. Impact: Gantt critical-path highlighting, Activities tab Run Schedule status.

### B2 — P2 · Dashboards bootstrap 404

**Observed:** `/dashboards/programme` loads and initially logs two 404s from `GET /v1/dashboards/PROGRAMME`. The UI then renders with real project data (EVM metrics for both IOCL runs + DMIC). So the 404 is handled, but the endpoint appears to be a server-side dashboard-config API that isn't implemented.

**Fix recommendation:** either implement `/v1/dashboards/{tier}` or remove the client call.

### B3 — P3 · Non-matching routes return 500 instead of 404/405

**Observed:** calling an endpoint with the wrong HTTP method (`GET /v1/udf/fields/{id}` when only POST/PUT/DELETE exist) returns `500 INTERNAL_ERROR` carrying `HttpRequestMethodNotSupportedException`. Calling a non-existent path returns `500 INTERNAL_ERROR` carrying `NoResourceFoundException`.

Both should be natural Spring mappings (405 and 404). Currently our `@ExceptionHandler(Exception.class)` catches everything before Spring's defaults kick in.

**Fix recommendation:** add two more handlers alongside the three added in §4:
- `@ExceptionHandler(NoResourceFoundException.class)` → 404
- `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` → 405 with an `Allow` header listing supported methods

### M1–M5 — P3 cosmetic / UX

- **M1 priority display:** `05-projects-list.png` shows `Critical` (word) for priority 1 IOCL projects, `P100` (string) for DMIC priority 100. Pick one convention (numeric with label, or just label tokens: Critical/High/Medium/Low).
- **M2 calendar ownership:** list at `/admin/calendars` shows Type (GLOBAL/PROJECT/RESOURCE) but not which project a PROJECT-scoped calendar belongs to — two rows literally named "IOCL Panipat WO 6-day" are indistinguishable from each other.
- **M3 network node readability:** text inside activity nodes (ES/EF/LS/LF/Float) is low-contrast grey — hard to read against the dark card background.
- **M4 portfolio card:** "DMIC Corridor Portfolio" card on `/portfolios` ends with raw text `projects` (probably intended to be "N projects" with a count) and truncated description with no hover-expand.
- **M5 sidebar click-through:** Clicking within the sidebar while a project page is still loading can navigate away to the sidebar route; not reproducible every time but happened once.

---

## What's working well (no issues observed)

1. **Login** — `01-login.png` — clean card, branded header, works with admin/admin123.
2. **Dashboard** — `02-dashboard.png` — Planned/Active/Completed/Resources/Activity counts + Recent Projects table with correct date format.
3. **EPS & OBS trees** — `03-eps.png`, `04-obs.png` — hierarchy correct, both seed runs visible.
4. **Projects list** — `05-projects-list.png` — search + status filter + correct dates.
5. **Project overview** — `06-project-overview.png` — full WO description, correct dates, Activate Project CTA.
6. **WBS tab** — `07-project-wbs.png` — 5 L1 + 24 L2 nodes with indented tree.
7. **Activities list** — `08-project-activities.png` — 26 rows, duration/start/finish populated, NOT_STARTED state correct.
8. **Gantt** — `09-project-gantt.png` — bars with dependency arrows.
9. **EVM tab (numbers only)** — `12-project-evm.png` — PV, EV, AC, SV, CV, SPI, CPI all computed (just rendered in $ not ₹).
10. **Contracts** — `13-contracts.png` — contractor, dates, LOA, FIDIC type all correct (just the value unit is wrong).
11. **Risk register** — `14-risk-selected.png` — 5×5 matrix renders; 10 risks distributed correctly (1 Med/Low, 2 Med/Med, 2 Med/High, 2 Low/Med, 1 High/Med, 2 High/High).
12. **Resources list** — `15-resources.png` — full resource pool visible; my seeded NONLABOR items present.
13. **Calendars** — `16-calendars.png` — project calendar shows 8h / 6 days / PROJECT type.
14. **User Access** — `18-user-access.png` — users, orgs, designations, auth methods table.
15. **Dashboards hub** — `19-dashboards-hub.png` — 4 tier cards (Executive / Programme / Operational / Field).
16. **Programme dashboard** — `20-dashboards-programme-after-wait.png` — all 3 projects' EVM, milestone tracker, contractor performance.
17. **Portfolios** — `21-portfolios.png` — DMIC Corridor Portfolio listed.
18. **Reports** — `22-reports.png` — reports landing with project selector.
19. **Baselines tab** — `23-baselines.png` — WO-approved PRIMARY baseline with Activities=26 and Total Cost.
20. **GIS viewer** — `24-gis.png` — OpenStreetMap tile loads with tabs (Map Viewer / Layers / Satellite / Progress).
21. **Network diagram** — `25-network.png` — PDM renders with FINISH_TO_START arrows.

## Recommended fix ordering

If you want me to continue, these are the cheapest wins first:

1. **B1 critical-path endpoint** (frontend one-liner) — unblocks the Gantt critical-path colouring.
2. **U4 currency formatter audit** (frontend) — fixes 4 screens at once.
3. **U3 contract value unit** (frontend) — one-line fix in the contracts card.
4. **B3 add NoResourceFound / MethodNotSupported handlers** (backend, alongside §4) — stops leaking 500s for routing errors.
5. **U2 cost rollup** (backend) — likely a SQL issue in `cost-summary` endpoint.
6. **U1 resource assignment name hydration** (backend) — JPA fetch join.
7. **U5 UDF admin page** (frontend or product-decision) — add project filter OR move to project page.

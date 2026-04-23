# IOCL Panipat WO 70143247 — UI walkthrough checklist

Manual browser walkthrough to validate that the data seeded by `scripts/seed-iocl-panipat-wo.sh` renders correctly in the EPPM frontend. Execute each row in order, fill pass/fail, capture screenshots for any ❌.

## Setup

1. Backend running (`dev` profile) on `http://localhost:8080`.
2. Frontend running on `http://localhost:3000` (`cd frontend && pnpm dev`).
3. Seed executed: `./scripts/seed-iocl-panipat-wo.sh` — capture the `PROJ=<uuid>` from the summary.
4. Open browser, log in as `admin` / `admin123`.

Fill in below:
- Run ID (`RUN_ID` env or the auto-timestamp suffix): _______
- Project UUID: _______
- Project code: `WO70143247-<RUN_ID>`

## Walkthrough

| # | Screen | Route | What to verify | Pass / Fail | Notes |
|---|---|---|---|---|---|
| 1 | Login | `/auth/login` | admin/admin123 lands on dashboard; header shows username | | |
| 2 | EPS tree | `/eps` | Nodes visible: `IOCL-<sfx>` > `PANIPAT-<sfx>` > `PNP-BIT-<sfx>` in tree view; expand/collapse works | | |
| 3 | OBS tree | `/obs` | Nodes visible: `DSO-ENGG` > `EIC` > `SITE-ENG` | | |
| 4 | Project list | `/projects` | WO 70143247 project appears with correct code + name ("IOCL Panipat - Bitumen Filling Plant Revamp") | | |
| 5 | Project overview | `/projects/<uuid>` | Header shows dates **2024-08-01 → 2025-06-30**, status PLANNED; EPS breadcrumb shows IOCL > Panipat > Bitumen | | |
| 6 | WBS tab | `/projects/<uuid>?tab=wbs` | 5 L1 nodes (WO10 Civil, WO20 Mechanical, WO30 Valves, WO40 Steam, WO50 Dismantling). Expand each — 23 L2 nodes total; tree indentation correct | | |
| 7 | Activities tab | `/projects/<uuid>?tab=activities` | 25 rows (incl. MS-START, MS-END milestones). Critical path highlighted red. Filter by WBS works. Duration column populated | | |
| 8 | Gantt tab | `/projects/<uuid>?tab=gantt` | Bars span Aug 2024 to Mar 2025. Dependency arrows visible (FS links). Milestones render as diamonds. Zoom week/month/quarter works | | |
| 9 | Network / PDM | activity detail page | Pick activity A20-01 (bitumen laying) — open detail; predecessors listed (A30-04 flange install); successors listed (A20-02 supports SS+5 lag, A20-03 hydrotest) | | |
| 10 | Resources (global) | `/resources` | 15 resources listed: 6 LABOR (mason, welder, fitter, elec, helper, supv), 6 NONLABOR (exc, crane, weldset, hydro, comp, scaff), 3 MATERIAL (CS pipe, bitumen, insul). Rates match `docs/iocl-panipat-wo.md` §6 | | |
| 11 | Resource edit | click welder | Hourly rate = 250, overtime = 375, max units/day = 8 | | |
| 12 | Resources tab (project) | `/projects/<uuid>?tab=resources` | Shows assignments — welders + fitters on A20-01 pipe laying, masons on A10-03 concrete, excavator on A10-02 earthwork. Histogram renders | | |
| 13 | Calendar editor | `/admin/calendars` | Shows "IOCL Panipat WO 6-day" calendar. **Open it — expected: Mon–Sat WORKING, Sun NON_WORKING**. **KNOWN ISSUE**: work-week may be empty (backend 500 on PUT). 8 holiday exceptions should be present | | work-week bug — see findings §1 |
| 14 | Baselines tab | `/projects/<uuid>?tab=baselines` | One PRIMARY baseline "WO-approved baseline" dated 2024-07-19. Variance dashboard renders (variance = 0 since current = baseline) | | |
| 15 | Costs tab | `/projects/<uuid>?tab=costs` | 25 expense rows (1 per activity), totalling ~₹16.7 Cr (aggregation of the 5 WO sections). S-curve renders. Cost accounts: 5 children (Civil, Mech, Valves, Steam, Dismantling) under 1 root | | |
| 16 | Cost account drill-down | click "Civil" account | Shows sum of 10 civil expenses ≈ ₹5.83 Cr | | |
| 17 | EVM tab | `/projects/<uuid>?tab=evm` | BAC ≈ ₹16.7 Cr, PV ≈ BAC, EV = 0 (no progress yet), AC = 0, SPI/CPI numerical; performance % = 0 | | |
| 18 | Contracts | `/projects/<uuid>/contracts` | One contract: number `70143247-<sfx>`, contractor `DE'S TECHNICO LIMITED`, value ₹18.94 Cr, dates 2024-07-19 LOA / 2024-08-01 start / 2025-06-30 complete, type ITEM_RATE_FIDIC_RED | | |
| 19 | Risk register | `/projects/<uuid>/risks` or `/risk` | 10 risks: R01 Phased handover HIGH/HIGH, R02 Wastage MEDIUM/MEDIUM, R03–R10 (see docs/iocl-panipat-wo.md §9). 5×5 matrix populated; R01, R09, R10 in red zone | | |
| 20 | UDF editor | `/admin/udf` | 6 project-scoped UDFs under this project: LD Rate (%), DLP Months, Retention %, PBG %, Mobilisation Advance %, LOA Date (=2024-07-19). All DATA types correct (NUMBER/NUMBER/NUMBER/NUMBER/NUMBER/DATE) | | |
| 21 | Portfolio add | `/portfolios` | Create a new portfolio "Panipat refinery programme", add WO70143247 project; portfolio shows 1 project, aggregate value ₹18.94 Cr | | |
| 22 | Dashboards — executive | `/dashboards/executive` | WO70143247 appears in corridor KPIs / tile; top-risk panel shows R01 or R10 | | |
| 23 | Dashboards — programme | `/dashboards/programme` | EVM tile for WO70143247 shows BAC / SPI / CPI | | |
| 24 | Dashboards — operational | `/dashboards/operational` | WBS progress card shows 0% (no progress seeded) | | |
| 25 | GIS viewer | `/projects/<uuid>/gis-viewer` | Map loads (no geo data in WO so polygons empty). Satellite image gallery shows empty state cleanly | | |
| 26 | Admin users | `/admin/users` | Admin user visible with ADMIN role. Role model includes PROJECT_MANAGER, SCHEDULER, RESOURCE_MANAGER, VIEWER | | |
| 27 | Activity detail — progress update | click any activity | Edit **% complete → 25 %**, save. **KNOWN ISSUE**: progress PUT may 500 — see findings §3 | | |
| 28 | Run schedule | Activities tab "Run Schedule" button | Option RETAINED_LOGIC runs; dates recalculate; critical path re-highlighted | | |
| 29 | Export → Excel | costs tab → export | Download matches `/tmp/iocl-panipat-exports/WO70143247.xlsx` (~15 KB) | | |
| 30 | Export → P6 XML | any export option | Download `.xml` roughly 55 KB, opens in P6 (or view in text editor — has `<apibo:Project>` root) | | |

## Completion sign-off

- Executed on: _______  
- Browser / version: _______  
- Backend build SHA: _______  
- Frontend build SHA: _______  
- Green rows: ___ / 30  
- Red rows: ___ / 30  
- Findings captured in: `docs/iocl-panipat-wo-findings.md`

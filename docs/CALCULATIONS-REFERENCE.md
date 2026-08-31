# BIPROS EPPM — Comprehensive Calculation Reference

## Introduction

BIPROS EPPM computes all money as **currency-neutral raw numbers** and applies a strict **relabel-never-convert** currency policy: changing a project's currency only relabels and re-abbreviates figures (symbol, grouping, compact ladder, decimals) — it never multiplies by an FX rate and never alters any business-value calculation. Project-level budgets (`originalBudget`/`currentBudget`, WBS `budgetCrores`) are stored in a currency **"major-unit" scale** — 1 crore (1e7) for INR, 1 million (1e6) for everything else — and are multiplied up to raw currency units (`majorUnitFactor`) only when surfaced alongside the raw-unit ledger totals so EVM/Cost figures stay comparable; on a currency change the stored major-unit value is rescaled by `oldFactor/newFactor` so the raw money is invariant. Recompute is **event-driven** throughout: DPR submission, resource deployment, material consumption, and general-expense logging publish events that synchronously (DPR→BOQ sync, same transaction) or asynchronously refresh the four-tier DBS P&L, BOQ actual-rate weighted averages, resource-assignment actual rollups, and EVM/Cost actuals — and cumulative quantities are recomputed on read (never stored) so back-dated edits stay self-consistent.

---

## 1. DBS (Daily Budget Sheet) — Daily Project Cost/Income Rollup

The DBS module recomputes a four-tier daily P&L (Supervisor → Engineer → CM → Project) from underlying DPR child rows and legacy deployment/consumption logs. Eight section calculators (A–G) each produce per-line amounts and a section subtotal for one (project, supervisor, date) scope using the universal rule **amount = quantity × rate** (with a `line_cost`-preferred path). All money is currency-neutral; recompute is event-driven (DPR submit, resource deployment, material consumption, general-expense logging).

### Section A — Manpower

**Manpower per-line amount (DPR source)**
```
effectiveRate = (unit_rate > 0) ? unit_rate : fallbackRate
amount = (line_cost != 0) ? line_cost : nos * effectiveRate    [HALF_UP, scale 2]
```
- Inputs: `nos` = `dpr_manpower.nos`; `unit_rate` = `dpr_manpower.unit_rate`; `line_cost` = `dpr_manpower.line_cost` (written by DprCostFormulas); `fallbackRate` = COALESCE(`manpower_role_rates.rate` by `manpower_role_rate_id`, top `manpower_rate_masters.rate` for `role_id` where active ORDER BY rate DESC, 0).
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionAManpowerCalculator.java:99-104`
- Notes: `working_hours` is logging-only, NEVER in cost math. `line_cost` preferred over nos×rate. Fallback rate uses scalar subqueries (not JOIN) to cap one row per DPR row, preventing N-multiplication across grade variants. WARN logged if amount=0 and rate unresolvable. `unit` defaults to 'Day'.
- Example: `nos=5, unit_rate=800, line_cost=null → 5 × 800 = 4000.00`

**Manpower legacy DRD per-line amount**
```
amount = nos * rate    [HALF_UP, scale 2]
```
- Inputs: `nos` = `daily_resource_deployments.nos_deployed`; `rate` = top `manpower_rate_masters.rate` for `resource_role_id` where active ORDER BY rate DESC (else 0); filtered `resource_type IN ('LABOR','MANPOWER')`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionAManpowerCalculator.java:157`
- Notes: Only folded in when `supervisorUserId IS NULL` (project-only attribution) because DRD carries no supervisor FK — avoids N-counting at project rollup. Per-supervisor scope excludes DRD entirely.
- Example: `nos_deployed=3, rate=600 → 1800.00`

**Manpower section subtotal**
```
manpowerTotal = Σ(line.amount over all DPR rows + DRD rows)    [HALF_UP, scale 2]
```
- Inputs: All Section A line amounts (DPR + DRD when supervisor null).
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionAManpowerCalculator.java:111,159,167`
- Notes: Returned as `SectionResult.totalAmount`.
- Example: `4000 + 1800 = 5800.00`

### Section B — Admin/Catering

**Admin/Catering per-line amount**
```
amount = nos * rate    [HALF_UP, scale 2]    (section returns empty when supervisorUserId != null)
```
- Inputs: `nos` = `daily_resource_deployments.nos_deployed`; `rate` = COALESCE(`manpower_rate_masters.rate` by `resource_role_id`, 0); filtered `resource_type IN ('ADMIN','CATERING')`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionBAdminCalculator.java:67`
- Notes: No DPR row type exists for ADMIN/CATERING — DRD is the only source. Project-only attribution (`SectionResult.empty()` for any non-null supervisor). `hours_worked` logging-only. `unit` defaults to 'Day'.
- Example: `nos_deployed=2, rate=1500 → 3000.00`

**Admin section subtotal**
```
adminTotal = Σ(nos × rate)    [HALF_UP, scale 2]
```
- Inputs: All Section B line amounts.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionBAdminCalculator.java:69,71`
- Notes: Only nonzero at project scope.

### Section C — Machinery/Equipment

**Machinery per-line amount (DPR source)**
```
effectiveRate = (unit_rate > 0) ? unit_rate : fallbackRate
amount = (line_cost != 0) ? line_cost : nos * effectiveRate    [HALF_UP, scale 2]
```
- Inputs: `nos` = `dpr_equipment.nos`; `unit_rate` = `dpr_equipment.unit_rate`; `line_cost` = `dpr_equipment.line_cost`; `fallbackRate` = COALESCE(`equipment_role_variants.rate` by `equipment_role_variant_id`, `equipment_rate_masters.rate` by `role_id`, 0).
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionCMachineryCalculator.java:72-77`
- Notes: Same line_cost-preferred rule as Section A. `working_hours` never in cost math. `unit` = COALESCE(erv.unit, erm.unit, 'Day'). `equipment_rate_masters` joined via `erm.id = eq.role_id` (role_id holds a rate-master id here).
- Example: `nos=2, unit_rate=5000, line_cost=null → 10000.00`

**Machinery legacy DRD per-line amount**
```
amount = nos * rate    [HALF_UP, scale 2]
```
- Inputs: `nos` = `daily_resource_deployments.nos_deployed`; `rate` = COALESCE(`equipment_rate_masters.rate` where `id = resource_role_id OR id = resource_id`, 0); `resource_type = 'EQUIPMENT'`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionCMachineryCalculator.java:115`
- Notes: Only when `supervisorUserId IS NULL` (project-only). DRD has no supervisor FK.

**Machinery section subtotal**
```
machineryTotal = Σ(line.amount over DPR + DRD rows)    [HALF_UP, scale 2]
```
- Inputs: All Section C line amounts.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionCMachineryCalculator.java:84,117,125`

### Section D — Fuel

**Fuel (primary: litres × project fuel rate)**
```
litres = Σ dpr_equipment.fuel_litres (over scope)
IF litres > 0 AND fuelRate > 0 THEN amount = litres * fuelRate    [HALF_UP, scale 2]
```
- Inputs: `fuel_litres` summed from `dpr_equipment` joined to DPR on (project,date,supervisor); `fuelRate` = `project_costing_config.fuel_rate_per_litre`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionDFuelCalculator.java:71-75`
- Notes: `project_costing_config` probed with `to_regclass` first (returns null, no exception) so a missing table doesn't abort the outer transaction (SQLSTATE 25P02). If rate found, returns immediately with a single 'Fuel (Diesel) — equipment' line (unit 'L'). Table currently not wired up, so this path typically yields null rate and falls to the fallback.
- Example: `litres=500, fuelRate=92 → 46000.00`

**Fuel (fallback: diesel/fuel material rows)**
```
per row: line_cost = COALESCE(dpr_material.line_cost, quantity * unit_rate)
fuelTotal = Σ line_cost    [each HALF_UP, scale 2]
```
- Inputs: `dpr_material` rows joined to DPR on (project,date,supervisor) WHERE `material_name ILIKE '%diesel%' OR '%fuel%' OR '%petrol%' OR '%hsd%'`; `quantity`, `unit_rate`, `line_cost` from `dpr_material`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionDFuelCalculator.java:85-86,107-109`
- Notes: Used when no project fuel rate. Same name filter mirrored (negated) in Section E to prevent double-counting. If total=0 with litres>0, WARN logged; returns `SectionResult.empty()`.
- Example: `qty=400, unit_rate=90, line_cost=null → 400×90 = 36000.00`

### Section E — Material

**Material per-line amount (DPR source)**
```
effectiveRate = (unit_rate > 0) ? unit_rate : fallbackRate
amount = (line_cost != 0) ? line_cost : quantity * effectiveRate    [HALF_UP, scale 2]
```
- Inputs: `quantity` = `dpr_material.quantity`; `unit_rate` = `dpr_material.unit_rate`; `line_cost` = `dpr_material.line_cost`; `fallbackRate` = COALESCE(`material_role_variants.rate` by `material_role_variant_id`, 0).
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionEMaterialCalculator.java:80-86`
- Notes: Bug-5 fix: diesel/fuel/petrol/hsd rows EXCLUDED (`NOT ILIKE`) because Section D books them — prevents double-count in Total Expense. `unit` defaults to ''. WARN if amount=0 and rate unresolvable.
- Example: `quantity=10, unit_rate=4500, line_cost=null → 45000.00`

**Material legacy MCL per-line amount**
```
amount = line_cost    [HALF_UP, scale 2]    (consumed and unit_rate carried for display)
```
- Inputs: `material_consumption_logs.line_cost`, `consumed`, `unit_rate`, `material_name`; same diesel/fuel exclusion.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionEMaterialCalculator.java:128,130`
- Notes: Only when `supervisorUserId IS NULL` — MCL has `received_by_user_id` (receiver, not executing supervisor), so project-only attribution avoids double-count. Uses stored `line_cost` directly (no qty×rate recompute).

**Material section subtotal**
```
materialTotal = Σ(line.amount over DPR + MCL rows)    [HALF_UP, scale 2]
```
- Inputs: All Section E line amounts.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionEMaterialCalculator.java:93,130,138`

### Section F — BOQ (Income side) & Sub-Contractor

**BOQ for-the-day amount (per line)**
```
qtyToday = qty_executed − (supervisor-scope ? Σ dpr_sub_contractor.quantity for the DPR : 0)
todayAmount = qtyToday * boq_rate    [HALF_UP, scale 2]
```
- Inputs: `qty_executed` = `daily_progress_reports.qty_executed`; `boq_rate` = `boq_items.boq_rate`; `sc_qty` = SUM(`dpr_sub_contractor.quantity`) grouped by `dpr_id`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionFBoqCalculator.java:63-66,111`
- Notes: SC-driven qty is netted out only at supervisor scope (`cast(:sup) IS NOT NULL`); at project scope full qty is kept so PM Total Income = full BOQ revenue including SC portion. Income side of the daily P&L.
- Example: `qty_executed=20, sc_qty=5 (supervisor scope), boq_rate=1000 → 15 × 1000 = 15000.00; project scope = 20 × 1000 = 20000.00`

**BOQ for-the-day total + direct/prelim split**
```
forTheDayAmount = Σ todayAmount
prelimBoqAmount = Σ todayAmount where is_preliminary
directBoqAmount = Σ todayAmount where NOT preliminary
(directBoq + prelimBoq ≈ forTheDay)
```
- Inputs: `is_preliminary` = `activities.is_preliminary` (LEFT JOIN on `dpr.activity_id`; null → direct).
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionFBoqCalculator.java:112-117,130-136`
- Notes: Prelim = mobilisation/site-setup/diversions. DPRs predating activity_id link bucket as direct.

**BOQ cumulative planned & achieved (compute, project scope)**
```
planned  = Σ (unique boq_items.boq_amount)
achieved = Σ (unique qty_executed_to_date × boq_rate)    [each HALF_UP, scale 2]
```
- Inputs: `boq_amount`, `qty_executed_to_date`, `boq_rate` from `boq_items`; dedup via `seenBoq` HashSet on `boq_id`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionFBoqCalculator.java:125-128`
- Notes: Returns planned=0/achieved=0 when `supervisorUserId != null` (cumulative are project-state values that double-count across supervisors sharing a BOQ item). Per-tier scopes must call `computeCumulativeForScope`.
- Example: `boq_amount=1,000,000; qty_executed_to_date=300; boq_rate=1000 → planned=1,000,000; achieved=300×1000=300,000`

**BOQ cumulative for a scope (computeCumulativeForScope)**
```
planned  = Σ boq_items.boq_amount
achieved = Σ (qty_executed_to_date × boq_rate)
   over SELECT DISTINCT boq_items touched by any in-scope DPR on (project,date)    [HALF_UP, scale 2]
```
- Inputs: `supervisorIds` collection (null = project-wide; empty = zero; else `supervisor_user_id = ANY(uuid[])`); `boq_items.boq_amount / qty_executed_to_date / boq_rate`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionFBoqCalculator.java:212-223`
- Notes: Each BOQ item counted once regardless of how many in-scope DPRs touched it — this is how engineer/CM/project tiers get deduped cumulative figures. `supervisorIds` passed as Postgres array literal `'{uuid,uuid}'` to avoid Hibernate IN-expansion uuid=varchar errors.

**Sub-Contractor per-line (PM scope)**
```
qty             = Σ dpr_sub_contractor.quantity
scExpense       = Σ (quantity × rate_per_unit)
scImputedIncome = Σ (quantity × COALESCE(boq_rate,0))
scMargin        = scImputedIncome − scExpense    [all HALF_UP, scale 2]
```
- Inputs: `quantity` = `dpr_sub_contractor.quantity`; `rate_per_unit` = `activity_sub_contractor_assignments.rate_per_unit` (locked at plan time); `boq_rate` = `boq_items.boq_rate` (via `dpr.boq_item_id`); grouped by (sc_master_id, sc_code, sc_name, work_type_name, unit, rate_per_unit, boq_rate).
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionFSubContractorCalculator.java:53-55,85-87`
- Notes: Rows with no planned assignment (`a.id IS NULL`) are skipped. `scImputedIncome`/`scMargin` informational only; PM Total Income comes from Section F BOQ at project scope. SC name/code denormalised on `DprSubContractor` so deleted masters keep attribution.
- Example: `quantity=10, rate_per_unit=800, boq_rate=1000 → scExpense=8000, scImputedIncome=10000, scMargin=2000`

**Sub-Contractor section totals**
```
totalExpense       = Σ scExpense
totalImputedIncome = Σ scImputedIncome    [HALF_UP, scale 2]
```
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionFSubContractorCalculator.java:93-94,97-100`
- Notes: `totalExpense` flows into project row `subcontractAmount`; `totalImputedIncome` informational only.

### Section G — General Expenses

**General Expenses per-line daily-prorated amount**
```
monthlyAmount = entry.achievedAmount (null→0)
dailyAmount   = monthlyAmount / daysInMonth    [HALF_UP, scale 2]
```
- Inputs: `achievedAmount` = `general_expense_monthly_entry.achievedAmount`; `daysInMonth` = `YearMonth.from(reportDate).lengthOfMonth()`; plan item joined by `planItemId` for description/unit/rate; `achievedQty` shown as line quantity.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionGGeneralExpensesCalculator.java:59-65`
- Notes: `yearMonthKey = year*100 + month`. Entries matched on (projectId, yearMonthKey). Lines with no plan item skipped. `unit` defaults to 'Month'. PM tier only.
- Example: `achievedAmount=310000, daysInMonth=31 → 10000.00`

**General Expenses daily + monthly totals**
```
monthTotal = Σ monthlyAmount
dailyTotal = monthTotal / daysInMonth    [HALF_UP, scale 2]
```
- Inputs: All Section G entries for the month.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/calculator/SectionGGeneralExpensesCalculator.java:60,67-69`
- Notes: Daily and month-period views reconcile: Σ dailyAmount ≈ monthlyTotal. `dailyTotal → row.generalExpenseAmount`; `monthlyTotal → row.generalExpenseMonthlyTotal`.
- Example: `monthTotal=620000, daysInMonth=31 → 20000.00`

**Insurance/Bank charges formula hint**
```
informational: planAmount ≈ formulaPct × contractValue
   (Insurance 0.000150 = 0.015%, Bank 0.000100 = 0.01%)
```
- Inputs: `formulaPct` from GeneralExpenseDefaults; contract value (not auto-applied).
- File: `bipros-dbs/src/main/java/com/bipros/dbs/overhead/service/GeneralExpenseDefaults.java:46-47`
- Notes: `PCT_CONTRACT_VALUE` is a UI hint only — NOT enforced; actual planAmount comes from PM's explicit input. 20 default plan items seeded per new project with rate=1, planQty=0, planAmount=0.
- Example: `contractValue=100,000,000 → Insurance hint = 0.000150 × 100,000,000 = 15,000`

**General Expense monthly total (service helper)**
```
monthlyTotal = COALESCE(SUM(general_expense_monthly_entry.achievedAmount for project+yearMonth), 0)
```
- Inputs: `entryRepo.sumAchievedAmount(projectId, yearMonth)`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/overhead/service/GeneralExpenseService.java:124-128`
- Notes: Mutating a monthly entry publishes `GeneralExpenseLoggedEvent` → `DbsRecomputeListener` refreshes daily project rollups for the affected month. Default plan items seeded on `ProjectCreatedEvent` (idempotent).

### Tier Aggregation (Supervisor → Engineer → CM → Project)

**Supervisor row — Total Expense**
```
totalExpense = manpowerAmount + adminAmount + machineryAmount
             + fuelAmount + materialAmount + 0 (subcontract)    [HALF_UP, scale 2]
```
- Inputs: Section A/B/C/D/E totals; `subcontractAmount` forced to ZERO at supervisor scope.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:144-145,153`
- Notes: No Section G at supervisor tier. Sub-contractor excluded at supervisor scope (different domain entity, project-only).
- Example: `5800 + 0 + 10000 + 36000 + 45000 + 0 = 96800.00`

**Supervisor/Engineer/Project row — Total Income & Contribution**
```
totalIncome     = boq.forTheDayAmount
contribution    = totalIncome − totalExpense
contributionPct = (totalIncome > 0) ? contribution / totalIncome (scale 4) : 0
```
- Inputs: `boqForTheDayAmount`, `totalExpense`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:148-156,234-238,501-505`
- Notes: Daily P&L income = FOR-THE-DAY BOQ, NOT cumulative achieved-to-date (`boqAchievedAmount` persisted separately for cumulative-income KPI). Supervisor/Engineer/Project store `contributionPct` as a **FRACTION** (÷income, scale 4). CM tier multiplies by 100 — inconsistency between tiers.
- Example: `income=15000, expense=96800 → contribution=−81800; contributionPct=−81800/15000=−5.4533 (fraction)`

**Supervisor row — deduped cumulative BOQ + pctAchieved**
```
boqPlannedAmount  = supCum.planned
boqAchievedAmount = supCum.achieved (computeCumulativeForScope with single-element set, or 0 when supervisor null)
pctAchieved       = achieved × 100 / planned    (scale 4, 0 if planned ≤ 0)
```
- Inputs: `computeCumulativeForScope(projectId, date, {supervisorUserId})`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:128-142,583-587`
- Notes: `compute()` returns 0 for cumulative at supervisor scope by design, so scope-based dedup is used. `directCost`/`prelimCost` from boq split; `totalCostInclPrelims = directCost + prelimCost`.
- Example: `achieved=300000, planned=1000000 → pctAchieved=30.0000`

**Engineer row — section aggregates (sum of supervisor rows)**
```
engineer.<field> = Σ supervisorRow.<field> over supervisors whose engineerUserId matches    [HALF_UP, scale 2]
cumulative BOQ deduped via computeCumulativeForScope(engineer's supervisor set)
```
- Inputs: `DbsDailySupervisor` rows filtered by `engineerUserId`; fields: manpower, admin, machinery, fuel, material, subcontract, boqForTheDay, totalExpense, totalIncome.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:200-221`
- Notes: Plain SUM for expenses/income; BOQ cumulative MUST be deduped (summing supervisor rows double-counts shared BOQ items). `contribution = income − expense`; `contributionPct = contribution/income` (fraction, scale 4). `pctAchieved` recomputed from deduped totals (not averaging supervisor %s).
- Example: `two supervisors manpower 5800 + 4200 = 10000.00`

**CM row — section aggregates + contributionPct (×100)**
```
cm.<field>      = Σ supervisorRow.<field> where construction_manager_user_id = cmUserId
contributionPct = (income − expense) × 100 / income    (scale 4)
pctAchieved     = boqAchieved × 100 / boqPlanned    (scale 4)
```
- Inputs: supRows by denormalised `construction_manager_user_id`; cumulative BOQ via `computeCumulativeForScope(CM's supervisor set)`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:278-319`
- Notes: CM `contributionPct` is multiplied by HUNDRED (a true percentage 0..100), UNLIKE supervisor/engineer/project which store the fraction. `boqPlannedToDate`/`boqAchievedToDate` column names differ from other tiers. `construction_manager_user_id` denormalised on supervisor row at write time (snapshot — not auto-updated on re-org).
- Example: `income=50000, expense=40000 → contributionPct=10000×100/50000=20.0000`

**CM period rollup (computeCmPeriod)**
```
totals.<field>  = Σ dbs_daily_cm.<field> over [from,to]
pctAchieved     = boqAchieved × 100 / boqPlanned    (scale 4)
contributionPct = (boqForDay − directCost) × 100 / boqForDay    (scale 4)
supervisorCount = max(per-row supervisorCount)
```
- Inputs: `dbs_daily_cm` rows in date range; `boqForTheDayAmount`, `directCost`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:361-396`
- Notes: Transient (unsaved) `DbsDailyCm`. Period `contributionPct` uses `(boqForDay − directCost)/boqForDay` — DIFFERENT basis from the daily CM contributionPct which uses `(income−expense)/income`. `boqPlanned/Achieved` summed across rows.

**Project row — Total Expense (includes Section G + SC)**
```
totalExpense = manpowerAmount + adminAmount + machineryAmount + fuelAmount
             + materialAmount + subcontractAmount + generalExpenseAmount    [HALF_UP, scale 2]
```
- Inputs: Per-section project aggregates (sup sums + legacy extras); `subcontractAmount` = SectionF SC totalExpense; `generalExpenseAmount` = SectionG dailyAmount.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:493-499`
- Notes: Project tier is the only one with general expenses and sub-contractor expense. `totalIncome = boqForTheDayAmount` (project scope, full qty incl SC portion).

**Project row — legacy-only portion (avoid double count)**
```
legacyOnly   = max(projectWideLegacyTotal − Σ supervisorRow.<field>, 0)
projectField = Σ supervisorRow.<field> + legacyOnly    (or + projectAdmin for admin)    [scale 2]
```
- Inputs: `projectManpowerLegacy/Machinery/Material` = `calc(projectId, null, date).totalAmount`; sup sums; `projectAdmin` = `adminCalc(null)` total.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:424-448,551-557,569-572`
- Notes: `positiveDiff = max(a−b, 0)`. Null-branch calculators return FULL project-wide DRD/MCL totals, so per-supervisor sums are subtracted to keep only truly unattributed (supervisor-null) legacy rows. Admin added in full (no per-supervisor admin exists). Fuel uses plain sum (no legacy extra).
- Example: `projectManpowerLegacy=12000, sup sum=10000 → legacyOnly=2000; manpowerAmount=10000+2000=12000`

**Project row — sub-contract & BOQ at project scope**
```
subcontractAmount   = SectionF SC totalExpense (computed directly, supervisor SC ignored)
boqForTheDayAmount  = projectBoq.forTheDayAmount
boqPlanned/Achieved = computeCumulativeForScope(null = project-wide)
direct/prelim       = from projectBoq
```
- Inputs: `subContractorCalc.compute(projectId, date)`; `boqCalc.compute(projectId, null, date)`; `boqCalc.computeCumulativeForScope(projectId, date, null)`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:453-479`
- Notes: Supervisor `subcontractAmount` always 0 by design and IGNORED at project tier. BOQ computed once at project scope (full qty, SC not netted) so PM revenue includes SC portion. `totalCostInclPrelims = projectDirect + projectPrelim`. `pctAchieved = achieved×100/planned` (scale 4 via percentage()).

**Project row — DPR/supervisor/engineer counts**
```
supervisorCount = count(distinct non-null supervisorUserId in supRows)
dprCount        = count daily_progress_reports for (project, date)
engineerIds     = distinct non-null engineerUserId joined by comma
```
- Inputs: supRows; `dprRepository.countByProjectIdAndReportDate`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAggregationService.java:507-519`
- Notes: `dprCount` uses the actual DPR ledger, NOT `supRows.size()`, because a phantom supervisor row (supervisorUserId=null) is created on recompute-without-DPRs and would inflate the KPI.

### Register Aggregation

**Equipment/manpower per-bucket accumulation**
```
per (type|trade, shift, cmUserId) bucket:
  countNos     += nos
  workingHours += working_hours
  lineCost     += line_cost
  weightedRate  = Σrate (where rate≠0) / count(nonzero rate samples)    [rate scale 4 HALF_UP]
```
- Inputs: `dpr_equipment / dpr_manpower` rows; `nos`, `working_hours`, `line_cost`; `rate` = COALESCE(unit_rate, role-variant rate, rate-master rate, 0); `shift` COALESCE 'DAY'; `cmUserId` via `ProjectTeamService.resolveCmFor` (memoised).
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/RegisterAggregationService.java:108-109,143-145,474-487`
- Notes: `weightedRate` is an unweighted MEAN of nonzero per-row rates (not nos-weighted). Idempotent: deletes register rows for (project,date) then re-inserts. Manpower rate uses scalar subqueries to avoid grade-variant N-multiplication. Runs at tail of project recompute (REQUIRES_NEW, no extra lock).
- Example: `rows rate 800 and 1000 → weightedRate = 1800/2 = 900.0000`

**Register — pivot day/night totals**
```
per type/trade: dn[NIGHT] += countNos if shift=NIGHT else dn[DAY] += countNos
per CM total = day + night
type total = Σ over CMs (totalDay + totalNight)
```
- Inputs: `DbsEquipmentRegisterRow/DbsManpowerRegisterRow.countNos` and `shift`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/RegisterAggregationService.java:268-292,308-333`
- Notes: Read-side pivot for UI: one row per type/trade with per-CM day/night/total breakdown.
- Example: `day=4, night=2 → total=6`

**Register — cumulative equipment/manpower days**
```
days = Σ count_nos over dbs_equipment_register / dbs_manpower_register
       WHERE report_date ≤ asOfDate (and optional cm_user_id), GROUP BY type/trade
```
- Inputs: `dbs_equipment_register.count_nos / dbs_manpower_register.count_nos`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/RegisterAggregationService.java:348-365`
- Notes: Each row's `count_nos` = one shift's deployment; both shifts count as a deployment-day (matches Excel 'Eqpmnt & MP Days').
- Example: `count_nos over month sums to 120 equipment-days`

### Alerts

**LOW_CONTRIBUTION_PCT**
```
raised when income > 0 AND contributionPct < 0.05 AND contribution ≥ 0
```
- Inputs: `row.contributionPct` (fraction), `totalIncome`, `contribution`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAlertEvaluator.java:96-98`
- Notes: Threshold 0.05 = 5% — compares against the FRACTION form (matches supervisor/engineer/project storage). CM stores ×100 so this evaluator is intended for fraction-tier rows.
- Example: `contributionPct=0.03, income>0, contrib≥0 → alert`

**NEGATIVE_CONTRIBUTION / RUNAWAY_FUEL / MISSING_RATE_DATA**
```
NEGATIVE_CONTRIBUTION: contribution < 0
RUNAWAY_FUEL:          expense > 0 AND fuel > expense × 0.5
MISSING_RATE_DATA:     manpower = 0 AND machinery = 0 AND income > 0
```
- Inputs: `contribution`, `fuelAmount`, `totalExpense`, `manpowerAmount`, `machineryAmount`, `totalIncome`.
- File: `bipros-dbs/src/main/java/com/bipros/dbs/service/DbsAlertEvaluator.java:92-109`
- Notes: RUNAWAY_FUEL ratio 0.5 (fuel >50% of expense). MISSING_RATE_DATA flags work-happened-but-no-rate. Stateless/pure evaluator.
- Example: `fuel=60000, expense=100000 → 60000 > 50000 → RUNAWAY_FUEL`

---

## 2. BOQ (Bill of Quantities) Calculations

On every write, `BoqCalculator.recompute()` derives six persisted columns plus an auto-status. DPR submissions and material-consumption logs feed cumulative qty and recompute `actualRate` as a weighted average via `BoqActualRateRecalcListener` unless `manualOverride` is set. Project summaries roll up amounts and an earned-budget overall %, and the cost module computes P&L margin (revenue = boqRate × qty).

### Per-line derived columns

**BOQ line amount (boqAmount)**
```
boqAmount = round2(nz(boqQty) * nz(boqRate))
```
- Inputs: `boqQty` and `boqRate` (BoqItem contract fields).
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqCalculator.java:39`
- Notes: AMOUNT_SCALE=2, HALF_UP. Nulls → zero via `nz()`. Persisted to `boq_amount` (precision 19, scale 2). Recomputed on every save.
- Example: `boqQty=100, boqRate=250 → 25000.00`

**Budgeted amount (budgetedAmount)**
```
budgetedAmount = round2(nz(boqQty) * nz(budgetedRate))
```
- Inputs: `boqQty` (contract) and `budgetedRate` (project team's internal planned unit rate).
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqCalculator.java:40`
- Notes: Scale 2 HALF_UP. `budgetedRate` independent of `boqRate` (boqRate = client price; budgetedRate = internal plan).
- Example: `boqQty=100, budgetedRate=200 → 20000.00`

**Actual amount (actualAmount)**
```
actualAmount = round2(nz(qtyExecutedToDate) * nz(actualRate))
```
- Inputs: `qtyExecutedToDate` (fed by DPR add/subtractExecutedQty) and `actualRate` (manual or DPR-derived).
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqCalculator.java:41`
- Notes: Scale 2 HALF_UP. Used as 'actualCost' in margin and as actualTotal in summary.
- Example: `qtyExecutedToDate=60, actualRate=230 → 13800.00`

**Percent complete (percentComplete)**
```
percentComplete = (boqQty == 0) ? null : nz(qtyExecutedToDate) / nz(boqQty)    [scale 6, HALF_UP]
```
- Inputs: `qtyExecutedToDate` and `boqQty`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqCalculator.java:44`
- Notes: RATIO_SCALE=6. Stored as a 0..1+ fraction (can exceed 1 on overrun); frontend renders as %. null when boqQty=0. Drives auto-status.
- Example: `qtyExecutedToDate=60, boqQty=100 → 0.600000 (60%)`

**Cost variance (costVariance)**
```
earnedBudget = nz(qtyExecutedToDate) * nz(budgetedRate)
costVariance = round2(actualAmount − earnedBudget)
```
- Inputs: `actualAmount`, `qtyExecutedToDate`, `budgetedRate`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqCalculator.java:42`
- Notes: Scale 2 HALF_UP. Positive = over-budget. `earnedBudget` is the budgeted value of executed work (qty × budgetedRate), NOT `budgetedAmount` (which uses full boqQty).
- Example: `actualAmount=13800, earnedBudget=60×200=12000 → 1800.00 (over budget)`

**Cost variance percent (costVariancePercent)**
```
costVariancePercent = (earnedBudget == 0) ? null : costVariance / earnedBudget    [scale 6, HALF_UP]
```
- Inputs: `costVariance` and `earnedBudget = qtyExecutedToDate × budgetedRate`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqCalculator.java:50`
- Notes: RATIO_SCALE=6. null when earnedBudget=0 ('no earned budget yet => no variance %').
- Example: `costVariance=1800, earnedBudget=12000 → 0.150000 (15% over)`

**BOQ auto-status transition**
```
if status == ON_HOLD                       -> keep
else if qtyExecutedToDate > boqQty         -> OVERRUN
else if percentComplete == null || 0       -> PENDING
else if percentComplete >= 1               -> COMPLETED
else                                       -> ACTIVE
```
- Inputs: `qtyExecutedToDate`, `boqQty`, `percentComplete` (just recomputed), current status.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqService.java:176`
- Notes: Runs after `BoqCalculator.recompute` on every create/update/bulk/VO/DPR path. ON_HOLD is a manual sticky state. OVERRUN takes priority over COMPLETED even at pct≥1 (unbillable-without-VO state).
- Example: `qtyExecutedToDate=120, boqQty=100 → OVERRUN (regardless that pct=1.2)`

### DPR-driven quantity & rate sync

**Executed quantity accumulation from DPR (addExecutedQty)**
```
qtyExecutedToDate = nz(current qtyExecutedToDate) + deltaQty,  then recompute + applyAutoStatus
```
- Inputs: `deltaQty` from DPR daily sync matched by (projectId, itemNo); current stored qty.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqService.java:130`
- Notes: Additive (cumulative), not overwrite. No-op if deltaQty null or zero. Triggers full derived-field recompute.
- Example: `current=60, deltaQty=15 → 75`

**Executed quantity reversal from DPR (subtractExecutedQty)**
```
next = nz(current qtyExecutedToDate) − deltaQty
qtyExecutedToDate = max(next, 0),  then recompute
```
- Inputs: `deltaQty` from DPR delete/edit-down/re-point; current stored qty.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqService.java:148`
- Notes: Floors at zero to defend against legacy data drift. No-op if deltaQty null/zero. Inverse of addExecutedQty.
- Example: `current=75, deltaQty=15 → 60; current=10, deltaQty=15 → −5 floored to 0`

**Actual rate recompute from DPR/MCL rollup (weighted average)**
```
actualRate = round4( sumActualCost(boqItemId) / sumQtyExecuted(boqItemId) ),  then recompute
```
- Inputs: `sumQtyExecuted` = SUM(`daily_progress_reports.qty_executed` WHERE boq_item_id=X); `sumActualCost` = SUM of DPR manpower (nos×assignment.effective_rate) + DPR equipment (nos×effective_rate) + DPR material (quantity×effective_rate) + `material_consumption_logs.line_cost` (for activities of those DPRs) + DPR sub-contractor (quantity×assignment.rate_per_unit).
- File: `bipros-project/src/main/java/com/bipros/project/application/listener/BoqActualRateRecalcListener.java:130`
- Notes: RATE_SCALE=4, HALF_UP. Skipped entirely when `manualOverride=TRUE` (any explicit PATCH of actualRate sets manualOverride=TRUE in `BoqService.updateItem:115`). Skipped when denominator null/zero. Ignores DPR approval status (project-wide average over all recorded executions). Synchronous in DPR write TX; per-row failures logged and skipped. Triggered by `DprSubmittedEvent` (new + old boqItemId) and `MaterialConsumptionLoggedEvent`.
- Example: `sumActualCost 13000; sumQtyExecuted=60 → 216.6667`

### Project summary rollups

**Project BOQ summary rollups (totals)**
```
boqTotal          = Σ nz(boqAmount)
budgetedTotal     = Σ nz(budgetedAmount)
actualTotal       = Σ nz(actualAmount)
earnedBudgetTotal = Σ (nz(qtyExecutedToDate) * nz(budgetedRate))    [each setScale(2,HALF_UP)]
```
- Inputs: All BoqItem rows for projectId.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqService.java:313`
- Notes: `earnedBudgetTotal` recomputed from per-item qty×budgetedRate (not from stored budgetedAmount).
- Example: `boqAmount 25000+30000 → boqTotal=55000.00; actualAmount 13800+14000 → actualTotal=27800.00`

**Project BOQ summary grand variance and overall %**
```
grandVariance    = round2(actualTotal − earnedBudgetTotal)
grandVariancePct = (earnedBudgetTotal == 0) ? null : grandVariance / earnedBudgetTotal    [scale 6]
overallPct       = (budgetedTotal == 0) ? null : earnedBudgetTotal / budgetedTotal    [scale 6]
```
- Inputs: `actualTotal`, `earnedBudgetTotal`, `budgetedTotal`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/BoqService.java:321`
- Notes: RATIO_SCALE=6. `overallPct` = earned budget / total budgeted (project-wide progress against budget).
- Example: `actualTotal=27800, earnedBudgetTotal=24000 → grandVariance=3800.00, grandVariancePct=0.158333; budgetedTotal=50000 → overallPct=0.480000 (48%)`

### Margin (cost module)

**Margin per BOQ item (revenue/margin/marginPct)**
```
revenue    = round2(nz(boqRate) * nz(qtyExecutedToDate))
actualCost = round2(nz(actualAmount))
margin     = revenue − actualCost
marginPct  = (revenue == 0) ? null : margin / revenue    [scale 6]
```
- Inputs: `boqRate`, `qtyExecutedToDate`, `actualAmount` via `MarginCalculator.compute`.
- File: `bipros-cost/src/main/java/com/bipros/cost/application/service/MarginCalculator.java:26`
- Notes: Revenue uses `boqRate` (client-paid contract rate), NOT budgetedRate; cost uses actualAmount. marginPct 0..1.
- Example: `boqRate=250, qty=60 → revenue=15000.00; actualAmount=13800 → margin=1200.00, marginPct=0.080000 (8%)`

**Margin by activity**
```
per DPR row: revenue = nz(qtyExecuted) * nz(boqRate of row.boqItemId); cost = nz(actualCost)
accumulate per activity; margin = Σrevenue − Σcost; marginPct = margin / Σrevenue
```
- Inputs: `DailyCostReportRow.qtyExecuted`, `row.actualCost`, `row.boqItemId → boqRate` map; via `MarginCalculator.fromRevenueAndCost`.
- File: `bipros-cost/src/main/java/com/bipros/cost/application/service/BoqMarginService.java:64`
- Notes: boqRate looked up by row.boqItemId (null rate when no BOQ link). Activity key '—' when null. Both scaled 2dp, pct scale 6, null when revenue=0.
- Example: `revenue 5000+3000=8000, cost 4000+2500=6500 → margin=1500.00, marginPct=0.187500`

**Margin by period (DBS-based)**
```
per day in [minStart,maxEnd] bucketed to period:
  revenue += nz(DbsDailyProject.boqForTheDayAmount)
  cost    += nz(DbsDailyProject.totalExpense)
margin = Σrevenue − Σcost; marginPct = margin / Σrevenue
```
- Inputs: `DbsDailyProject.boqForTheDayAmount` (income) and `totalExpense` (cost) per project-day; FinancialPeriod buckets.
- File: `bipros-cost/src/main/java/com/bipros/cost/application/service/BoqMarginService.java:97`
- Notes: Reuses precomputed DBS daily aggregates. Days outside any period bucket skipped.
- Example: `boqForTheDayAmount=120000, totalExpense=95000 → margin=25000.00, marginPct=0.208333`

**Margin summary (project P&L)**
```
revenue = Σ (nz(boqRate) * nz(qtyExecutedToDate)); cost = Σ nz(actualAmount)
margin = revenue − cost; marginPct = margin / revenue
```
- Inputs: All BoqItem rows: `boqRate`, `qtyExecutedToDate`, `actualAmount`; via `MarginCalculator.fromRevenueAndCost`.
- File: `bipros-cost/src/main/java/com/bipros/cost/application/service/BoqMarginService.java:118`
- Notes: Project-wide P&L. Scale 2, pct scale 6, null when revenue=0.
- Example: `revenue 15000+7500=22500; cost 13800+6000=19800 → margin=2700.00, marginPct=0.120000 (12%)`

### DBS Section F (BOQ income) — see also Domain 1

**Section F BOQ for-the-day amount + prelim/direct split** — `bipros-dbs/.../SectionFBoqCalculator.java:111`; **cumulative planned & achieved (compute, project scope)** — `:125`; **deduped cumulative for scope (computeCumulativeForScope)** — `:212`. Formulas, inputs, notes, and examples identical to the DBS Section F entries above.

---

## 3. DPR (Daily Progress Report) & Daily Cost/Resource Reports

At save time each child row's `line_cost` is snapshotted as **rate × count** (nos for labour/equipment, quantity for material); hours/OT/fuel are logging-only and never enter cost or units. Those line_costs are the authoritative Actual Cost flowing into the resource ledger, role-only assignment actuals, `BoqItem.qtyExecutedToDate`, and EVM/Cost rollups. "% complete consistency" means every surface is driven by the same raw `qtyExecuted` and `line_cost` numbers; cumulative qty is never stored, recomputed on read.

### Line-cost snapshots & units (DprCostFormulas)

**Manpower row line cost**
```
line_cost = unitRate × nos    (HALF_UP, 2 dp); null if unitRate null or nos ≤ 0
```
- Inputs: `unitRate` from client override else assignment effective_rate / role-rate-book (project override → variant default); `nos` from `DprManpowerRow.nos`. `workingHours`, `otHours`, `idleHours` logging-only.
- File: `bipros-project/src/main/java/com/bipros/project/application/util/DprCostFormulas.java:33-37`
- Notes: scale 2 HALF_UP. `basis` string preserved but not consulted. Hours NEVER multiplied into cost.
- Example: `unitRate=800/day, nos=5 → 4000.00. workingHours=9 ignored.`

**Manpower units (ledger)**
```
units = nos    (0 if nos null or ≤ 0)
```
- Inputs: `DprManpower.nos`.
- File: `bipros-project/src/main/java/com/bipros/project/application/util/DprCostFormulas.java:40-43`
- Notes: Fed into `daily_activity_resource_outputs.qty_executed` per (activity, resource). Hours never enter unit counts.
- Example: `nos=5 → units=5`

**Equipment row line cost**
```
line_cost = unitRate × nos    (HALF_UP, 2 dp); null if unitRate null or nos ≤ 0
```
- Inputs: `unitRate` from client/assignment/equipment_role_variant rate book; `nos` from `DprEquipmentRow.nos`. `workingHours`, `idleHours`, `breakdownHours`, `fuelLitres` logging-only.
- File: `bipros-project/src/main/java/com/bipros/project/application/util/DprCostFormulas.java:46-50`
- Notes: Working hours and fuel NOT in cost. Same rate×count rule as manpower.
- Example: `unitRate=3500/day, nos=2 → 7000.00`

**Equipment units (ledger)**
```
units = nos
```
- Inputs: `DprEquipment.nos`.
- File: `bipros-project/src/main/java/com/bipros/project/application/util/DprCostFormulas.java:53-56`
- Notes: Hours excluded.
- Example: `nos=2 → units=2`

**Material row line cost**
```
line_cost = unitRate × quantity    (HALF_UP, 2 dp); null if unitRate null or quantity null
```
- Inputs: `unitRate` from client/assignment/material_role_variant rate book; `quantity` from `DprMaterialRow.quantity`.
- File: `bipros-project/src/main/java/com/bipros/project/application/util/DprCostFormulas.java:59-62`
- Notes: scale 2 HALF_UP.
- Example: `unitRate=6500/MT, quantity=12 → 78000.00`

**Material units (ledger)**
```
units = quantity    (0 if null)
```
- Inputs: `DprMaterial.quantity`.
- File: `bipros-project/src/main/java/com/bipros/project/application/util/DprCostFormulas.java:65-67`
- Example: `quantity=12 → units=12`

### Sub-contractor

**Sub-contractor row actual cost (read-time)**
```
actualCost = SUM(dpr_sub_contractor.quantity × COALESCE(activity_sub_contractor_assignments.rate_per_unit, 0))
```
- Inputs: `DprSubContractor.quantity` (persisted), `rate_per_unit` from `resource.activity_sub_contractor_assignments` (cross-schema join).
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DprActualCostLookup.java:154-179`
- Notes: SC rows store quantity only; rate looked up at query time. Validated at save: SUM(quantity) per DPR cannot exceed `DPR.qtyExecuted` (SC_EXCEEDS_WORKDONE), quantity>0, assignment must belong to DPR activity.
- Example: `quantity=100, rate_per_unit=250 → 25000`

**Sub-contractor assignment actual rollup**
```
actual_units = SUM(quantity across all DPR SC rows for assignment)
actual_cost  = actual_units × rate_per_unit
```
- Inputs: `subContractorRepository.sumQuantityByActivitySubContractorAssignmentId`; `rate_per_unit` from assignment.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyProgressReportService.java:1115-1140`
- Notes: Recomputed for union of old+new assignment ids after every create/update/delete. Writes `resource.activity_sub_contractor_assignments.actual_units/actual_cost`.
- Example: `qty 100+60, rate 250 → actual_units=160, actual_cost=40000`

**DPR Actual Cost per activity (EVM/Cost seam)**
```
AC(activity) = SUM(dpr_manpower.line_cost) + SUM(dpr_equipment.line_cost)
             + SUM(dpr_material.line_cost) + SUM(SC quantity × rate_per_unit),
   grouped by daily_progress_reports.activity_id
```
- Inputs: persisted `line_cost` on the three child tables joined to DPRs; SC quantity×rate.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DprActualCostLookup.java:51-58, 68-75, 114-121`
- Notes: Authoritative AC contribution of supervisor daily reporting; EVM AC and Daily Cost Report both read this so they agree. Legacy rows with null activity_id skipped. Also bucketable by report_date (`sumByProjectGroupedByDate`).
- Example: `4000 + 7000 + 78000 + 25000 → AC=114000`

### Productivity preview

**Manpower expected output (productivity preview)**
```
expectedManpower = Σ(outputPerManPerDay × nos) over manpower rows (per-day basis; hours not used)
```
- Inputs: `outputPerManPerDay` from `resource.productivity_norms` (variant→role-only→generic fallback for the activity's work_activity); `nos` from `DprManpowerRow`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DprProductivityPreviewService.java:65-81, 144-147`
- Notes: Only runs if the Work Activity has a MANPOWER norm. Row skipped if nos≤0; warning if no norm for role. null if no manpower norm matched at all.
- Example: `2.5×5=12.5; +1.0×4=4.0 → 16.5`

**Equipment expected output (productivity preview)**
```
expectedEquipment = Σ(outputPerDay × nos) over equipment rows (per-day basis; HRS excluded)
```
- Inputs: `outputPerDay` from `resource.productivity_norms`; `nos` from `DprEquipmentRow`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DprProductivityPreviewService.java:85-101, 154-157`
- Notes: Runs only if EQUIPMENT norm configured. Hours never influence the math.
- Example: `40×2=80`

**Combined expected output / bottleneck**
```
BOTH: combine(MP, EQ):
  PARALLEL   -> MP + EQ
  SUBSTITUTE -> max(MP, EQ)
  SERIES (default) -> min(MP, EQ)
Else single side's value, else null.
```
- Inputs: `expectedFromManpower`, `expectedFromEquipment`, `norm_combination` from `resource.work_activities.norm_combination`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DprProductivityPreviewService.java:105-138`
- Notes: Null/unknown combination defaults to SERIES (min = historical bottleneck behaviour).
- Example: `MP=16.5, EQ=80, SERIES → 16.5; PARALLEL → 96.5; SUBSTITUTE → 80`

### Cumulative quantity & BOQ sync

**DPR cumulative quantity (per activity)**
```
cumulativeQty = SUM(qtyExecuted) for (projectId, activityName) up to and including reportDate
```
- Inputs: `daily_progress_reports.qty_executed` via `sumQtyExecutedThroughDate`; in list view accumulated in date+id order per `project::lowercase(activityName)` key.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyProgressReportService.java:532-535, 592-613`
- Notes: Never stored — always recomputed on read so back-dated edits stay self-consistent without rewriting later rows. Null sum coalesced to ZERO.
- Example: `Day1 qty=50, Day2 qty=30 → Day2 cumulative=80`

**BOQ qtyExecutedToDate sync from DPR**
```
CREATE:            qtyExecutedToDate += qtyExecuted
UPDATE same item:  += (newQty − oldQty)
UPDATE relinked:   −= oldQty on old item, += newQty on new item
DELETE:            −= oldQty
```
- Inputs: `DprSubmittedEvent.qtyExecuted/oldQty/boqItemNo/oldBoqItemNo` → BoqService add/subtractExecutedQty.
- File: `bipros-project/src/main/java/com/bipros/project/application/listener/DprBoqSyncListener.java:41-77`
- Notes: Synchronous (same TX as DPR write) so a BOQ recompute failure rolls the DPR back. Drives BOQ percentComplete consistency.
- Example: `BOQ 200; DPR qty 50 created → 250; edited to 70 (Δ+20) → 270; deleted → 200`

**BOQ physical % complete** / **BOQ amounts** / **BOQ cost variance & variance %** — `BoqCalculator.java:44-46`, `:39-41`, `:42-52` respectively (same formulas as Domain 2; here noting the DPR-consistency examples):
- `percentComplete = qtyExecutedToDate / boqQty` (scale 6); `qtyExecutedToDate=270, boqQty=1000 → 0.270000 (27%)`
- `actualAmount = qtyExecutedToDate × actualRate`; `270×520 → 140400.00`
- `earnedBudget = qtyExecutedToDate × budgetedRate; costVariance = actualAmount − earnedBudget; costVariancePercent = costVariance/earnedBudget`; `actualAmount=140400, budgetedRate=500, qty=270 → earnedBudget=135000, costVariance=5400.00, costVariancePercent=0.040000 (4% over)`

### Daily Cost Report

**Row budgeted cost**
```
budgetedCost = qty × BoqItem.budgetedRate    (HALF_UP 2 dp); null if no BOQ rate
```
- Inputs: `qty` = `DPR.qtyExecuted`; `budgetedRate` from resolved BoqItem (by boqItemId → boqItemNo → activityName substring of description).
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyCostReportService.java:140-144`
- Notes: null (not zero) when no BOQ link so broken links are visible. AMOUNT_SCALE=2.
- Example: `qty=50, budgetedRate=500 → 25000.00`

**Row actual cost & actual rate**
```
if DPR line_cost sum > 0:
  actualCost = SUM(line_cost across mp+eq+mt for dpr); actualRate = actualCost / qty (null if qty=0)
else fallback:
  actualCost = qty × BoqItem.actualRate; actualRate = BoqItem.actualRate
```
- Inputs: `loadActualCostByDprId` (UNION of `dpr_manpower/equipment/material` line_cost per dpr_id); fallback `BoqItem.actualRate`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyCostReportService.java:148-162, 353-383`
- Notes: Prefers persisted DPR line_cost so report agrees with EVM AC. Derives implied actualRate from cost/qty.
- Example: `DPR line_cost sum=27000, qty=50 → actualCost=27000.00, actualRate=540.00`

**Row variance & variance %**
```
variance    = actualCost − budgetedCost
variancePct = variance / budgetedCost    (RATIO_SCALE=6, only if budgetedCost != 0)
Both null unless both costs present.
```
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyCostReportService.java:163-172`
- Notes: Positive = over budget. Only rows with both costs feed period totals.
- Example: `actualCost=27000, budgetedCost=25000 → variance=2000, variancePct=0.080000 (8%)`

**Row ETC/EAC (EVM snapshot share)**
```
share = rowActualCost / activityActualCostSum    (8 dp)
etc   = activityEtc × share
eac   = activityEac × share    (HALF_UP 2 dp)
```
- Inputs: row actualCost, per-activity SUM of row actualCost, latest `evm.evm_calculations.estimate_to_complete/estimate_at_completion` for the activity.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyCostReportService.java:197-209, 411-431`
- Notes: Distributes the activity's EVM ETC/EAC across DPR rows by each row's share of activity actual cost.
- Example: `rowActual=27000, activityActual=90000 → share=0.30; activityEtc=200000 → row etc=60000.00`

**ETC/EAC fallback 1 (project CPI)**
```
remaining = max(budgetedCost − actualCost, 0)
etc = (cpi > 0) ? remaining / cpi : remaining
eac = actualCost + etc
```
- Inputs: row budgetedCost, actualCost; project-level `evm_calculations.cost_performance_index` (wbs_node_id & activity_id NULL, latest).
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyCostReportService.java:214-231, 392-409`
- Notes: Used when no per-activity EVM snapshot. ETC clamped ≥0.
- Example: `budgetedCost=25000, actualCost=27000 → remaining=0 → etc=0, eac=27000. budgetedCost=40000,actualCost=27000,cpi=0.9 → remaining=13000, etc=14444.44, eac=41444.44`

**ETC/EAC fallback 2 (plain budget remaining)**
```
remaining = max(budgetedCost − actualCost, 0); etc = remaining; eac = actualCost + remaining
```
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyCostReportService.java:235-242`
- Notes: Last resort when no EVM exists at all. Clamped ≥0.
- Example: `budgetedCost=40000, actualCost=27000 → etc=13000, eac=40000`

**Period totals**
```
periodBudgeted   = Σ budgetedCost
periodActual     = Σ actualCost (only rows where both present)
periodVariance   = periodActual − periodBudgeted    (2 dp)
periodVariancePct = periodVariance / periodBudgeted    (6 dp, null if 0)
```
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyCostReportService.java:170-171, 263-266, 283-290`
- Notes: Only rows with both costs contribute.
- Example: `ΣbudgetedCost=100000, ΣactualCost=110000 → periodVariance=10000.00, periodVariancePct=0.100000`

### Ledger & assignment rollups

**Ledger → ResourceAssignment actual_units rollup**
```
actual_units     = SUM(daily_activity_resource_outputs.qty_executed)
actual_start_date = LEAST(existing, MIN(output_date))
remaining_units  = max(planned_units − actual_units, 0)    for (project, activity, resource)
```
- Inputs: `daily_activity_resource_outputs` aggregated per (project, activity, resource); `resource_assignments.planned_units`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyActivityResourceOutputService.java:164-190`
- Notes: Cost rollup intentionally out of scope here (needs effective-dated rates). LEAST ignores NULLs. Manual (source=MANUAL) and DPR (source=DPR) rows both feed it.
- Example: `qty 50+30, planned_units=200 → actual_units=80, remaining_units=120`

**Ledger daysWorked derivation**
```
daysWorked = hoursWorked / 8.0    (DEFAULT_HOURS_PER_DAY) when daysWorked not supplied
```
- Inputs: `request.hoursWorked` or `DprResourceAggregate.hoursWorked`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyActivityResourceOutputService.java:33, 59-62, 234, 244`
- Notes: Only when daysWorked null and hours present. Informational; not used in cost.
- Example: `hoursWorked=12 → daysWorked=1.5`

**Role-only assignment actual rollup (manpower/equipment/material)**
```
actual_units   = SUM(nos) [manpower/equipment] or SUM(quantity) [material] per (activity, role, variant)
actual_cost    = actual_units × effective_rate
remaining_units = max(planned_units − actual_units, 0)
remaining_cost  = max(planned_cost − actual_units × effective_rate, 0)
```
- Inputs: `project.dpr_manpower.nos / dpr_equipment.nos / dpr_material.quantity` grouped by role+variant; `resource_assignments.effective_rate/planned_units/planned_cost`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyProgressReportService.java:1814-1888`
- Notes: Direct DPR→assignment path for role-only assignments where resource_id is null (legacy ledger listener can't match). Equipment hours NOT used. Idempotent. Phantom rows (all 0) swept/deleted afterward.
- Example: `SUM(nos)=5, effective_rate=800, planned_units=10, planned_cost=8000 → actual_units=5, actual_cost=4000, remaining_units=5, remaining_cost=4000`

**Soft overrun warning**
```
candidate = currentActual + Σ(this DPR's nos/quantity for role+variant)
if candidate > planned_units -> excess = candidate − planned_units, emit OVERRUN warning
```
- Inputs: `resource_assignments.actual_units` (currentActual) + `planned_units`; DPR child nos/quantity aggregated by (roleId, variantId).
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyProgressReportService.java:1558-1627`
- Notes: Never throws — supervisor can always save. Equipment uses nos not hours. Unplanned variants skipped.
- Example: `planned=10, currentActual=8, this DPR nos=4 → candidate=12 > 10 → excess=2`

**Planned headcount auto-derivation (Daily Resource Deployment)**
```
nosPlanned = SUM(headcount) if > 0 else SUM(planned_units),
   over resource_assignments where project & role match and
   deploymentDate within [planned_start_date, planned_finish_date]; .intValue()
```
- Inputs: `resource.resource_assignments.headcount/planned_units`; deployment `project_id, resource_role_id, log_date`.
- File: `bipros-project/src/main/java/com/bipros/project/application/util/ResourceAssignmentPlannedHeadcountResolver.java:42-86`
- Notes: Only when user leaves nosPlanned null/0; sets `nosPlannedAuto=TRUE`. Needs a role anchor. Date filter treats null start/finish as open-ended.
- Example: `headcount 3 and 2 covering the date → nosPlanned=5, nosPlannedAuto=true`

### Logging-only fields (never in cost/units)

**Daily Resource Deployment manhours**
```
hoursWorked, idleHours stored as entered; nosDeployed vs nosPlanned compared on the report
```
- Inputs: `CreateDailyResourceDeploymentRequest.nosDeployed/hoursWorked/idleHours/nosPlanned`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyResourceDeploymentService.java:58-73`
- Notes: Pure logging — no cost computed here. Treats nosPlanned 0 as 'not provided'. Publishes `ResourceDeploymentSavedEvent`.

**DPR event aggregate hours/fuel**
```
totalManpowerHours = Σ(workingHours + otHours)
totalEquipmentHours = Σ workingHours
totalFuelLitres = Σ fuelLitres
```
- Inputs: `DprManpower.workingHours/otHours`; `DprEquipment.workingHours/fuelLitres`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyProgressReportService.java:702-712`
- Notes: Carried on `DprSubmittedEvent` for analytics (ClickHouse facts) only — NOT used in any cost or units calculation.
- Example: `8h+1OT and 8h+0OT → totalManpowerHours=17`

**Ledger hours attribution (manpower vs equipment)**
```
manpower:  hoursByResource += (workingHours + otHours) × nos
equipment: hoursByResource += workingHours × nos
```
- Inputs: `DprManpower.workingHours/otHours/nos`; `DprEquipment.workingHours/nos`.
- File: `bipros-project/src/main/java/com/bipros/project/application/service/DailyProgressReportService.java:1501-1513`
- Notes: Stored as `hoursWorked` on `daily_activity_resource_outputs` (informational); does not affect actual_units (which is nos/quantity). unit recorded HR vs DAY from basis.
- Example: `workingHours=8, otHours=1, nos=5 → 45 hrs logged`

---

## 4. Earned Value Management (EVM)

Computes the PMI earned-value triad (PV, EV, AC) and all derived indices per project, per WBS node, and per cost account. Leaf Activities supply BAC, time-phased PV, EV (via a pluggable technique strategy), and AC (actual ActivityExpense + DPR persisted line_cost); these are summed bottom-up. For the project-level row the BAC is overridden by `Project.currentBudget` rescaled from major-unit scale into raw currency units. Indices are computed in `EvmServiceHelper.calculateIndices`, each resolvable through a per-project FormulaEngine with a hard-coded fallback.

### Base quantities

**Activity BAC (Budget At Completion, leaf)**
```
activityBac = SUM(ActivityExpense.budgetedCost)
            + SUM(ResourceAssignment.plannedCost)
            + SUM(ActivitySubContractorAssignment.plannedCost)    (null contributions skipped)
```
- Inputs: budgetedCost / plannedCost grouped by activityId.
- File: `bipros-evm/src/main/java/com/bipros/evm/application/service/EvmRollupService.java:223`
- Notes: Starts at ZERO; each null field skipped. Raw currency units. Used as the bottom-up rollup BAC (project-level path then overrides with Project.currentBudget).

**Project BAC override + major-unit scaling**
```
projectBac = (project.currentBudget != null ? project.currentBudget : project.originalBudget)
if projectBac > 0:
  totalBac = projectBac * majorUnitFactor
  majorUnitFactor = 1e7 (1 crore) if budgetCurrency == 'INR' else 1e6 (1 million)
```
- Inputs: `Project.currentBudget` (fallback `originalBudget`) in major-unit scale; `Project.budgetCurrency`.
- File: `bipros-evm/src/main/java/com/bipros/evm/application/service/EvmService.java:128`
- Notes: Only at the project-level calculateEvm path, only when `projectBac.signum() > 0`; otherwise bottom-up activity-sum totalBac kept. Currency change rescales the stored major-unit value (oldFactor/newFactor) so raw money stays constant.
- Example: `currentBudget=0.02, INR → 0.02 × 1e7 = 200000. OMR currentBudget=20 → 20 × 1e6 = 20000000.`

**Activity PV (Planned Value, time-phased)**
```
if plannedFinishDate == null                                    -> 0
else if plannedFinishDate <= dataDate                           -> activityBac
else if plannedStartDate != null AND plannedStartDate <= dataDate ->
       activityBac * elapsedDays / totalDays
       (totalDays = days(plannedStart..plannedFinish), elapsedDays = days(plannedStart..dataDate);
        if totalDays <= 0 -> activityBac)
else                                                            -> 0
```
- Inputs: `Activity.plannedStartDate/plannedFinishDate`; `activityBac`; project `dataDate`.
- File: `bipros-evm/src/main/java/com/bipros/evm/application/service/EvmRollupService.java:250`
- Notes: ChronoUnit.DAYS; proportional branch divides with SCALE=4, HALF_UP. Returns ZERO for non-computable cases — cost-account rollup separately flags these as null PV so SV/SPI show null.
- Example: `bac=100000, totalDays=30, elapsedDays=15 → PV=50000`

**Activity AC (Actual Cost, leaf)**
```
activityAc = SUM(ActivityExpense.actualCost) + dprAcByActivity[activityId]    (nulls skipped)
```
- Inputs: `ActivityExpense.actualCost` grouped by activityId; DPR persisted line_cost summed per activity via `DprActualCostLookup.sumByActivity`.
- File: `bipros-evm/src/main/java/com/bipros/evm/application/service/EvmRollupService.java:291`
- Notes: `resource_assignments.actual_cost` intentionally EXCLUDED to avoid double-counting the DPR ledger (ResourceAssignmentCostRollupListener keeps that column in lock-step with DPR). DPR sum is the single source of actuals, mirroring CostService.getCostSummary. Starts at ZERO; null contributions skipped.
- Example: `ActivityExpense.actualCost=5000, DPR line_cost=30000 → AC=35000`

### EV technique strategies

**ACTIVITY_PERCENT_COMPLETE**
```
EV = bac * (Activity.percentComplete / 100); if percentComplete null or bac null -> 0
```
- File: `bipros-evm/src/main/java/com/bipros/evm/domain/algorithm/ActivityPercentCompleteStrategy.java:18`
- Notes: Default P6 technique (also DURATION/UNITS). SCALE=4, HALF_UP. Mapping: PHYSICAL→WEIGHTED_STEPS, DURATION/UNITS→ACTIVITY_PERCENT_COMPLETE, null→ACTIVITY_PERCENT_COMPLETE. `refreshPercentCompleteIfStale` recomputes a stale 0 for IN_PROGRESS DURATION activities first.
- Example: `bac=100000, percentComplete=40 → 40000`

**WEIGHTED_STEPS**
```
EV = bac * (pct / 100), pct = Activity.physicalPercentComplete (fallback Activity.percentComplete);
   if bac null or both pct null -> 0
```
- File: `bipros-evm/src/main/java/com/bipros/evm/domain/algorithm/WeightedStepsStrategy.java:21`
- Notes: Step weights not modeled; falls back to field-reported physical %. SCALE=4, HALF_UP. Mapped from PHYSICAL.
- Example: `bac=80000, physicalPercentComplete=25 → 20000`

**FIFTY_FIFTY**
```
if pct >= 100 -> bac
else if actualStartDate != null OR pct > 0 -> bac * 0.5
else -> 0    (bac null -> 0)
```
- File: `bipros-evm/src/main/java/com/bipros/evm/domain/algorithm/FiftyFiftyStrategy.java:19`
- Notes: 50% on start, remaining 50% on completion. The 0.5 branch scale 2 HALF_UP.
- Example: `bac=100000, started but pct=30 → 50000.00`

**ZERO_ONE_HUNDRED**
```
EV = bac if percentComplete >= 100 else 0    (bac null -> 0)
```
- File: `bipros-evm/src/main/java/com/bipros/evm/domain/algorithm/ZeroOneHundredStrategy.java:15`
- Notes: Conservative: no credit until fully complete.
- Example: `bac=50000, percentComplete=99 → 0; at 100 → 50000`

**LEVEL_OF_EFFORT**
```
EV = pv (the activity PV); if pv null -> 0
```
- File: `bipros-evm/src/main/java/com/bipros/evm/domain/algorithm/LevelOfEffortStrategy.java:15`
- Notes: LOE earns value equal to plan, so CV and SV always 0 for pure-LOE work.
- Example: `pv=50000 → EV=50000`

### Derived indices (EvmServiceHelper.calculateIndices)

**SV (Schedule Variance)** — `SV = EV − PV` (code EVM_SV)
- Inputs: EV, PV. File: `EvmServiceHelper.java:42`. Notes: BigDecimal subtract, no guard. Negative = behind. Persisted 19,2. In cost-account rollup SV null when bucket PV null.
- Example: `EV=40000, PV=50000 → SV=−10000`

**CV (Cost Variance)** — `CV = EV − AC` (code EVM_CV)
- Inputs: EV, AC. File: `EvmServiceHelper.java:47`. Notes: Negative = over budget. Persisted 19,2.
- Example: `EV=40000, AC=35000 → CV=5000`

**SPI (Schedule Performance Index)** — `SPI = EV / PV if PV != 0 else 0.0` (code EVM_SPI)
- Inputs: EV, PV. File: `EvmServiceHelper.java:52`. Notes: Guard `PV.compareTo(ZERO) != 0`, else 0.0. SCALE=4, HALF_UP, stored Double. In getActivityEvm/cost-account rollup SPI null when PV zero/null. >1 ahead, <1 behind.
- Example: `EV=40000, PV=50000 → 0.8`

**CPI (Cost Performance Index)** — `CPI = EV / AC if AC != 0 else 0.0` (code EVM_CPI)
- Inputs: EV, AC. File: `EvmServiceHelper.java:59`. Notes: Guard `AC.compareTo(ZERO) != 0`, else 0.0 (helper) / null (getActivityEvm, cost-account rollup). SCALE=4, HALF_UP, stored Double. >1 under budget.
- Example: `EV=40000, AC=35000 → 1.1429`

**EAC — CPI_BASED (default)** — `EAC = BAC / CPI if CPI > 0 else BAC` (code EVM_EAC_CPI)
- Inputs: BAC, CPI. File: `EvmServiceHelper.java:145`. Notes: Guard: if BAC null or 0 → EAC=0 (before switch). etcMethod null defaults CPI_BASED. CPI ≤ 0 → BAC. SCALE=4, HALF_UP. Assumes future spend at current cost efficiency.
- Example: `BAC=200000, CPI=1.1429 → ≈175,003`

**EAC — SPI_BASED** — `EAC = AC + (BAC − EV) / SPI if SPI > 0 else BAC` (code EVM_EAC_SPI)
- Inputs: AC, BAC, EV, SPI. File: `EvmServiceHelper.java:155`. Notes: remaining = (BAC−EV)/SPI at SCALE=4 HALF_UP, then EAC = AC + remaining. SPI ≤ 0 → BAC.
- Example: `AC=35000, BAC=200000, EV=40000, SPI=0.8 → remaining=200000; EAC=235000`

**EAC — CPI_SPI_COMPOSITE** — `EAC = AC + (BAC − EV) / (CPI × SPI) if CPI>0 AND SPI>0 else BAC` (code EVM_EAC_COMPOSITE)
- Inputs: AC, BAC, EV, CPI, SPI. File: `EvmServiceHelper.java:167`. Notes: composite = cpi×spi; remaining = (BAC−EV)/composite at SCALE=4 HALF_UP; EAC = AC + remaining. Non-positive factor → BAC.
- Example: `composite=0.9143; remaining≈175,000; EAC≈210,000`

**EAC — MANUAL / MANAGEMENT_OVERRIDE** — `EAC = BAC`
- Inputs: BAC. File: `EvmServiceHelper.java:180`. Notes: No automatic forecast; placeholder equal to BAC.
- Example: `BAC=200000 → 200000`

**ETC (Estimate To Complete)** — `ETC = EAC − AC` (code EVM_ETC)
- Inputs: EAC, AC. File: `EvmServiceHelper.java:74`. Notes: BigDecimal subtract, no guard. Persisted 19,2.
- Example: `EAC=175003, AC=35000 → 140003`

**TCPI (To-Complete Performance Index)** — `TCPI = (BAC − EV) / (EAC − AC) if (EAC − AC) != 0 else 0.0` (code EVM_TCPI)
- Inputs: BAC, EV, EAC, AC. File: `EvmServiceHelper.java:85`. Notes: Guard `eacMinusAc.compareTo(ZERO) != 0` else 0.0. SCALE=4, HALF_UP, Double. Denominator is EAC−AC (not BAC−AC).
- Example: `→ 160000 / 140003 = 1.1428`

**VAC (Variance At Completion)** — `VAC = BAC − EAC` (code EVM_VAC)
- Inputs: BAC, EAC. File: `EvmServiceHelper.java:99`. Notes: BigDecimal subtract, no guard. Positive = projected under budget. Persisted 19,2.
- Example: `BAC=200000, EAC=175003 → 24997`

**Performance % Complete (EV-based)** — `performancePercentComplete = (EV / BAC) × 100 if BAC != 0 else 0.0` (code EVM_PERF_PCT)
- Inputs: EV, BAC. File: `EvmServiceHelper.java:108`. Notes: Guard `BAC.compareTo(ZERO) != 0` else 0.0. EV/BAC at SCALE=4 HALF_UP, then ×100, stored Double. Distinct from `Activity.percentComplete` (input to EV).
- Example: `EV=40000, BAC=200000 → 20.0`

### Rollup & anchor

**Project / WBS / cost-account rollup aggregation**
```
totalBac = Σ child BAC; totalPv = Σ child PV; totalEv = Σ child EV; totalAc = Σ child AC
then indices (CV,SV,CPI,SPI,EAC,ETC,VAC,TCPI,perf%) recomputed on the summed totals
   via EvmServiceHelper.calculateIndices
```
- Inputs: Leaf activity BAC/PV/EV/AC; WBS hierarchy via `WbsNode.parentId`; cost-account resolution: `Activity.costAccountId` else `WbsNode.costAccountId` else Unassigned.
- File: `bipros-evm/src/main/java/com/bipros/evm/application/service/EvmRollupService.java:108`
- Notes: WBS parents sum child WbsEvmNode totals PLUS activities directly attached to the parent node. Indices NOT summed — recomputed from rolled-up PV/EV/AC/BAC. Each WBS/leaf row persisted as an EvmCalculation with wbsNodeId set. Cost-account bucket's PV (and SV/SPI) null if any contributing activity has non-computable PV; CPI/SPI null when bucket AC/PV is 0; buckets sorted by code ascending with Unassigned last.
- Example: `A (100k,50k,40k,35k) + B (100k,100k,80k,70k) → totals 200k/150k/120k/105k; CPI=1.1429, SPI=0.8`

**dataDate resolution (anchor for PV/EV time-phasing)**
```
dataDate = Project.dataDate if non-null else LocalDate.now()
```
- Inputs: `Project.dataDate`.
- File: `bipros-evm/src/main/java/com/bipros/evm/application/service/EvmService.java:283`
- Notes: When null, anchors to system clock and logs INFO. `EvmRollupService` has equivalent `resolveDataDate` at line 333. Drives time-phased PV proportion and stale-percent refresh.
- Example: `Project.dataDate=2026-06-16 → all PV proportions against 2026-06-16`

---

## 5. Cost / Budget / Margin / Cash-Flow (bipros-cost)

Rolls up project money from three ledgers — manual ActivityExpense rows, ResourceAssignment planned/actual cost (DPR-driven), and ActivitySubContractorAssignment — into per-activity, per-period, and project-level summaries. Project-level budgets are stored in major-unit scale and multiplied up to raw currency units; currency is relabel-only.

### Per-activity rollups (ActivityCostCalculator)

**Activity budgeted cost** — `budgetedCost = Σ expense.budgetedCost + Σ (assignment.budgetedCost ?? assignment.plannedCost)`
- File: `ActivityCostCalculator.java:65-88`. Notes: Original committed value (frozen at assignment creation). Falls back to plannedCost for legacy rows pre-Phase-2. Nulls skipped.
- Example: `expense 10000 + assignment(budgetedCost=null, plannedCost=6500) → 16500`

**Activity planned cost** — `plannedCost = Σ expense.budgetedCost + Σ assignment.plannedCost`
- File: `ActivityCostCalculator.java:95-113`. Notes: Asymmetry: ActivityExpense carries budgetedCost; ResourceAssignment only plannedCost. Nulls skipped.
- Example: `10000 + 6500 → 16500`

**Activity actual cost** — `actualCost = Σ expense.actualCost + Σ assignment.actualCost`
- File: `ActivityCostCalculator.java:119-137`. Notes: assignment.actualCost = rate × actualUnits via ResourceAssignmentCostRollupListener. Nulls skipped.
- Example: `8000 + 7150 → 15150`

**Activity remaining cost** — `remainingCost = Σ expense.remainingCost + Σ assignment.remainingCost`
- File: `ActivityCostCalculator.java:140-158`. Notes: Nulls skipped.
- Example: `2000 + 0 → 2000`

**Activity at-completion cost / EAC** — `atCompletionCost = Σ eacFallback(rec), eacFallback = rec.atCompletionCost ?? (rec.actualCost + rec.remainingCost)`
- File: `ActivityCostCalculator.java:165-209`. Notes: Fallback keeps EAC meaningful pre-at-completion-column. Nulls in fallback treated as zero.
- Example: `(null→7150+500=7650) + 10000 → 17650`

### Project cost summary (CostService)

**Total budget** — `totalBudget = Σ expense.budgetedCost + Σ RA.plannedCost + Σ SC.plannedCost (by project)`
- Inputs: ActivityExpense.budgetedCost; `ResourceAssignmentRepository.sumPlannedCostByProjectId`; `ActivitySubContractorAssignmentRepository.sumPlannedCostByProjectId`.
- File: `CostService.java:598-611`. Notes: FIX-14: must match EVM BAC (both expense.budgetedCost AND assignment.plannedCost). Null sums → zero. Raw currency units.
- Example: `0 + 6500 + 0 → 6500`

**Total actual** — `totalActual = Σ expense.actualCost + dprActualCostLookup.sumByProject(projectId)`
- File: `CostService.java:613-627`. Notes: Deliberately does NOT add `resource_assignments.actual_cost` — RA.actualCost is in lock-step with DPR child rows (same money); adding both double-counts (HIGHWAY-301 bug: 7150+7150=14300).
- Example: `0 + 7150 → 7150`

**Remaining & at-completion** — `totalRemaining = Σ expense.remainingCost; atCompletion = Σ expense.atCompletionCost`
- File: `CostService.java:629-635`. Notes: Project summary's remaining/atCompletion count only expense rows (unlike per-activity calculator). Nulls → zero.
- Example: `2000 + 0 → totalRemaining=2000`

**Material procurement / open stock / material issued**
```
materialProcurement = Σ GoodsReceiptNote.amount
openStock           = Σ MaterialStock.stockValue
materialIssued      = max(0, materialProcurement − openStock)
```
- File: `CostService.java:639-647`. Notes: materialIssued floored at zero.
- Example: `procurement=500000, openStock=120000 → issued=380000`

**Project original / current budget unit scaling (major-unit → raw)**
```
projectOriginalBudget_raw = project.originalBudget × majorUnitFactor
majorUnitFactor = (currency == INR ? 1e7 : 1e6)
If originalBudget null/≤0, fall back to totalBudget (or null).
```
- Inputs: `Project.originalBudget / currentBudget` (major-scale); `Project.budgetCurrency`.
- File: `CostService.java:653-676`. Notes: Mirrors EvmService scaling. Only multiplies when signum>0. Relabel-only, no FX.
- Example: `INR originalBudget=2 → 20,000,000 (₹2 Cr). Same 2 under OMR → 2,000,000.`

### Period bucketing

**Period budget bucketing**
```
periodBudget(p) = Σ{expense.budgetedCost : expense.actualStartDate in [p.start,p.end]}
                + Σ proratePlannedCost(ra) + Σ proratePlannedCost(sc)
```
- Inputs: ActivityExpense.budgetedCost by actualStartDate; RA.plannedCost prorated over planned window (fallback project window); SC.plannedCost prorated over parent activity window.
- File: `CostService.java:760-809`. Notes: Inclusive date comparison both ends. SC assignments lack date columns → use parent Activity dates, else project dates.
- Example: `10000 + RA prorated 3250 → 13250`

**ResourceAssignment planned-cost linear proration into a period**
```
prorated = plannedCost × (overlapDays / activityDays)
activityDays = DAYS(raStart,raFinish)+1
overlapStart = max(raStart,periodStart); overlapEnd = min(raFinish,periodEnd)
overlapDays  = DAYS(overlapStart,overlapEnd)+1
```
- File: `CostService.java:846-858`. Notes: Returns 0 if any input null, activityDays≤0, or no overlap. Result 2dp HALF_UP. Inclusive day counts (+1).
- Example: `plannedCost=9000 over 90 days, overlap 30 → 9000 × 30/90 = 3000.00`

**Period actual bucketing**
```
periodActual(p) = Σ{expense.actualCost : expense.actualStartDate in [p.start,p.end]}
                + Σ{dprDailyCost[d] : d in [p.start,p.end]}
```
- Inputs: ActivityExpense.actualCost by actualStartDate; `dprActualCostLookup.sumByProjectGroupedByDate` by report_date.
- File: `CostService.java:815-839`. Notes: Inclusive both ends; null DPR dates skipped.
- Example: `8000 + (1000+1500) → 10500`

**Period cost variance** — `variance = periodBudget − periodActual`
- File: `CostService.java:720`. Notes: Positive = under budget for the period. EV/PV in the same DTO come from StorePeriodPerformance (manual EVM snapshots), not computed here.
- Example: `13250 − 10500 → 2750`

**Period EV/PV aggregation (in period DTO)** — `ev(p) = Σ StorePeriodPerformance.earnedValueCost; pv(p) = Σ .plannedValueCost` (matching financialPeriodId)
- File: `CostService.java:711-718`. Notes: Nulls → zero. From manually-snapshotted EVM tab data.
- Example: `EV 5000+3000 → ev=8000`

### Margin (cost module)

**Margin from rate × qty (BOQ item P&L vs budgeted rate)**
```
revenue   = rate × qty
margin    = revenue − actualCost
marginPct = (revenue == 0 ? null : margin / revenue)    [0..1]
```
- Inputs: `BoqItem.budgetedRate` (rate), `BoqItem.qtyExecutedToDate` (qty), `BoqItem.actualAmount` (actualCost).
- File: `MarginCalculator.java:26-36`. Notes: revenue & cost 2dp HALF_UP; marginPct 6dp HALF_UP. Null rate/qty/cost treated as zero.
- Example: `rate=500, qty=100 → revenue=50000.00; actualCost=42000 → margin=8000.00, marginPct=0.160000 (16%)`

**Margin from pre-summed revenue & cost** — `margin = revenue − actualCost; marginPct = (revenue==0 ? null : margin/revenue)`
- File: `MarginCalculator.java:38-46`. Notes: Used after summing many rows. Amount 2dp, ratio 6dp.
- Example: `revenue=120000, cost=90000 → margin=30000.00, marginPct=0.250000 (25%)`

**Budgeted margin by BOQ item** — `revenue = budgetedRate × qtyExecutedToDate; margin = revenue − actualAmount; marginPct = margin/revenue`
- File: `BudgetedMarginService.java:40-52`. Notes: Revenue priced at budgeted unit rate (use BoqMarginService for contract BOQ rate). Delegates to MarginCalculator.compute.
- Example: `budgetedRate=500, qty=100, actualAmount=42000 → revenue 50000, margin 8000, 16%`

**Budgeted margin by activity** — `per activity: revenue = Σ(row.qtyExecuted × row.budgetedUnitRate); cost = Σ row.actualCost; margin = revenue − cost; marginPct = margin/revenue`
- File: `BudgetedMarginService.java:54-73`. Notes: Accumulator [revenue,cost] per activity name; null activity → '—'. nz() nulls → zero.
- Example: `revenue 5000+2500=7500; cost 4000+1500=5500; margin 2000; 26.67%`

**Budgeted margin by period** — same as by-activity but bucketed via `PeriodAggregator.bucketFor`; rows with no matching period skipped.
- File: `BudgetedMarginService.java:75-101`.
- Example: `Q2 revenue 7500, cost 5500 → margin 2000, marginPct 0.266667`

**Budgeted margin project summary** — `revenue = Σ(budgetedRate × qtyExecutedToDate); cost = Σ actualAmount; margin = revenue − cost; marginPct = margin/revenue`
- File: `BudgetedMarginService.java:103-113`. Notes: Whole-project P&L at budgeted rates.
- Example: `revenue 50000+70000=120000; cost 42000+48000=90000; margin 30000; 25%`

### Cash-flow forecast (CashFlowForecastEngine)

**CPI & SPI (from cumulative EVM)**
```
cumulativeAC = Σ(actualLaborCost + actualNonlaborCost + actualMaterialCost + actualExpenseCost)
CPI = (cumAC > 0 ? cumEV/cumAC : 1)
SPI = (cumPV > 0 ? cumEV/cumPV : 1)
```
- Inputs: StorePeriodPerformance.earnedValueCost (cumEV), .plannedValueCost (cumPV), four actual-cost components.
- File: `CashFlowForecastEngine.java:46-67`. Notes: CPI/SPI 4dp HALF_UP; default to 1 when denominator zero. Nulls → zero.
- Example: `cumEV=8000, cumAC=10000 → CPI=0.8000; cumPV=9000 → SPI=0.8889`

**Total remaining budget** — `totalRemaining = max(0, Σ periodBudgets − Σ periodActuals)`
- File: `CashFlowForecastEngine.java:39-44`. Notes: Floored at zero.
- Example: `100000 − 40000 → 60000`

**Future remaining budget denominator** — `futureRemainingBudget = Σ{periodBudget(p) : p.endDate not before today}`
- File: `CashFlowForecastEngine.java:79-86`. Notes: Denominator for distributing remaining budget across future periods.
- Example: `30000 + 30000 → 60000`

**Per-period forecast (past = actual)** — `isPast(p) = p.endDate < today; if isPast -> forecast = actual`
- File: `CashFlowForecastEngine.java:93-104`. Notes: Past periods locked to actuals regardless of method.
- Example: `Q1 ended, actual=25000 → forecast=25000`

**LINEAR method** — `forecast(p) = periodBudget(p)`
- File: `CashFlowForecastEngine.java:129-131`. Notes: No EVM adjustment — straight planned budget.
- Example: `30000 → 30000`

**CPI_BASED method** — `forecast(p) = totalRemaining × (periodBudget / futureRemainingBudget) / CPI`
- File: `CashFlowForecastEngine.java:136-143`. Notes: Returns periodBudget unchanged if futureRemainingBudget==0 or CPI==0. proportion 6dp, result 2dp HALF_UP.
- Example: `60000 × 0.5 / 0.8 = 37500.00`

**SPI_CPI_COMPOSITE method** — `forecast(p) = totalRemaining × (periodBudget / futureRemainingBudget) / (CPI × SPI)`
- File: `CashFlowForecastEngine.java:148-159`. Notes: Returns periodBudget if futureRemainingBudget==0 or composite==0. proportion 6dp, result 2dp HALF_UP.
- Example: `60000 × 0.5 / 0.7111 = 42189.57`

**Cumulative curves** — `cumPlanned += planned; cumActual += actual; cumForecast += forecast` (running totals per ordered period)
- File: `CashFlowForecastEngine.java:106-119`. Notes: Periods sorted by startDate. Builds the S-curve.
- Example: `P1 30000 → cum 30000; P2 30000 → cum 60000`

### Performance rollup (PerformanceRollupService)

**Period AC (cost sum)** — `AC(period) = Σ over rows (actualLaborCost + actualNonlaborCost + actualMaterialCost + actualExpenseCost)` grouped by financialPeriodId
- File: `PerformanceRollupService.java:50-78`. Notes: 2dp HALF_UP. Nulls → zero. Activity-level and project-level SPP rows summed together.
- Example: `3000+1000+2000+500 → 6500.00`

**EV, PV, CV, SV, CPI, SPI**
```
EV = Σ earnedValueCost; PV = Σ plannedValueCost
CV = EV − AC; SV = EV − PV
CPI = (AC == 0 ? null : EV/AC); SPI = (PV == 0 ? null : EV/PV)
```
- File: `PerformanceRollupService.java:55-61`. Notes: AC/EV/PV 2dp HALF_UP; CPI/SPI 6dp HALF_UP, null when denominator zero.
- Example: `EV=8000, AC=6500, PV=9000 → CV=1500, SV=−1000, CPI=1.230769, SPI=0.888889`

### RA bills (running-account)

**RA bill draft — per-line amount**
```
delta = qtyExecutedToDate − previousCumulative    (skip if delta ≤ 0)
amount = boqRate × delta
```
- Inputs: `BoqLineSnapshot.qtyExecutedToDate`, `.boqRate`; previousCumulativeByBoqItemId (default 0).
- File: `RaBillDraftCalculator.java:93-113`. Notes: amount 2dp HALF_UP. Rate snapshotted at draft time so later VO revisions don't change saved drafts. Lines with no new quantity skipped.
- Example: `qtyToDate=120, previous=100 → delta=20; boqRate=500 → 10000.00`

**RA bill draft — gross & statutory deductions**
```
grossAmount     = Σ line.amount
mobAdvance      = gross × mobPct
retention       = gross × retentionPct
tds             = gross × tdsPct
gst             = gross × gstPct
totalDeductions = mob + retention + tds + gst
netAmount       = gross − totalDeductions
```
- Inputs: grossAmount; DeductionConfig fractions (defaults: mob=0, retention=0.05, tds=0.02, gst=0.18).
- File: `RaBillDraftCalculator.java:115-129`. Notes: Each pct 2dp HALF_UP; null base/pct → zero. Net 2dp HALF_UP. GST treated here as a deduction from gross.
- Example: `gross=10000: retention=500, tds=200, gst=1800 → totalDeductions=2500; net=7500.00`

**RA bill (persisted) — deduction aggregation**
```
deductions = mobAdvanceRecovery + retention5Pct + tds2Pct + gst18Pct (if total > 0)
           else request.deductions; else 0
```
- File: `CostService.java:453-470`. Notes: Sums the four named deductions; only falls back to a supplied total when individual lines sum to zero. netAmount/grossAmount taken from request as-is.
- Example: `0 + 500 + 200 + 1800 → 2500`

### Budget change log (ProjectBudgetService)

**Initial project budget set** — `originalBudget = amount; currentBudget = amount` (one-time, amount > 0, originalBudget previously null)
- File: `ProjectBudgetService.java:43-70`. Notes: Throws BUDGET_ALREADY_SET / INVALID_BUDGET_AMOUNT. Stored in major-scale.
- Example: `amount=2 → originalBudget=2, currentBudget=2 (₹2 Cr)`

**Current budget recompute from approved changes** — `currentBudget = originalBudget + Σ approved ADDITION.amount − Σ approved REDUCTION.amount` (TRANSFER no net effect)
- File: `ProjectBudgetService.java:226-253`. Notes: Runs on approveChange. TRANSFER redistributes between WBS nodes only. Known limitation: only currentBudget aggregate rescaled on currency change, not individual approved change rows.
- Example: `2 + 0.5 − 0.2 → 2.3`

**Budget summary — pending/approved addition & reduction totals**
```
pendingAdditions   = Σ PENDING ADDITION.amount
pendingReductions  = Σ PENDING REDUCTION.amount
approvedAdditions  = Σ APPROVED ADDITION.amount
approvedReductions = Σ APPROVED REDUCTION.amount
pendingCount       = count(PENDING)
```
- File: `ProjectBudgetService.java:255-294`. Notes: TRANSFER excluded from all four totals. Returns alongside originalBudget, currentBudget, budgetCurrency, budgetUpdatedAt.
- Example: `0.3 + 0.2 → pendingAdditions=0.5, pendingCount=2`

**Budget change request validation** — `TRANSFER requires fromWbsNodeId != toWbsNodeId (both non-null); REDUCTION requires fromWbsNodeId; ADDITION requires toWbsNodeId`
- File: `ProjectBudgetService.java:203-224`. Notes: Pre-condition gate, not a money calc. Budget must be set before any change.
- Example: `TRANSFER with from==to → TRANSFER_SAME_WBS error`

### Financial periods

**Financial period auto-generation (quarter boundaries)**
```
for each calendar quarter overlapping [plannedStart,plannedFinish]:
  firstMonth = (quarter-1)*3 + 1
  start = year-firstMonth-01; end = endOfMonth(firstMonth+2)
  sortOrder = year*10 + quarter
  isClosed = end < today; startQuarter = (month-1)/3 + 1
```
- Inputs: `Project.plannedStartDate, .plannedFinishDate, .code`.
- File: `FinancialPeriodAutoGenerator.java:62-130`. Notes: Idempotent: skips existing sortOrders, never deletes. Name = '<code> Q<n> <year>'. Per-project lock serializes concurrent ensure calls. PeriodType=QUARTERLY.
- Example: `Start 2026-05-10 → Q2 2026: firstMonth=4, start=2026-04-01, end=2026-06-30, sortOrder=20262`

**Period bucket matching (D/W/M dashboards)** — `bucketFor(date) = first period where date in [startDate,endDate]; periodType normalized: starts-with D→D, W→W, M→M, null/empty→'?'`
- File: `PeriodAggregator.java:25-62`. Notes: No auto-bucketing into ISO weeks/calendar months — explicit financial_periods rows required. Unknown type → '?'.
- Example: `2026-05-15 in [2026-04-01,2026-06-30] → that Q2 period`

---

## 6. Scheduling / CPM / PERT (bipros-scheduling)

CPMScheduler topologically sorts the DAG (Kahn), runs a forward pass (early start/finish), backward pass (late start/finish), then derives total float, free float, and the critical path. All date arithmetic is in WORKING DAYS via CalendarCalculator backed by an in-memory CalendarSnapshot. Durations are in days but advanced via working-day calendar math, so calendar-date deltas differ from the numeric duration.

### Duration & PERT

**Activity duration to schedule (durationToUse)**
```
durationToUse = pertExpected (if a PERT estimate exists)
              else (remainingDuration != null ? remainingDuration
                    : (originalDuration != null ? originalDuration : 0.0))
```
- Inputs: `Activity.remainingDuration`, `.originalDuration`; `PertEstimate.expectedDuration` keyed by activityId (pertMap).
- File: `SchedulingService.java:141-146`. Notes: PERT te overrides the activity's own duration when present. Becomes `SchedulableActivity.remainingDuration` driving every forward/backward pass add/subtract.

**PERT expected duration (te)** — `expectedDuration = (o + 4*m + p) / 6.0`
- Inputs: o=optimistic, m=mostLikely, p=pessimistic. File: `PertEstimateService.java:36`. Notes: Beta-distribution weighted mean. Validation (84-92) requires o,m,p > 0 and o ≤ m ≤ p.
- Example: `o=4, m=6, p=14 → (4 + 24 + 14)/6 = 7.0`

**PERT standard deviation** — `standardDeviation = (p − o) / 6.0`
- File: `PertEstimateService.java:37`. Notes: Classic 6-sigma range assumption.
- Example: `o=4, p=14 → 10/6 = 1.667`

**PERT variance** — `variance = ((p − o) / 6.0)^2`
- File: `PertEstimateService.java:38`. Notes: Square of std-dev (Math.pow).
- Example: `o=4, p=14 → 1.667^2 = 2.778`

### Forward pass

**Early Start (ES)**
```
earlyStart = max( projectStartDate,
                  max over predecessors of relationshipContribution(rel, predScheduled, forward),
                  primaryConstraintAdj );
overridden by actualStartDate / dataDate per scheduling option
```
- File: `CPMScheduler.java:279-336`. Notes: Topological order. RETAINED_LOGIC: if actualFinish set, ES=effectiveActualStart & EF=actualFinish (skip); if actualStart set, ES=actualStart; IN_PROGRESS pins ES=dataDate. PROGRESS_OVERRIDE schedules remaining work from dataDate ignoring predecessors. ALAP handled after backward pass.
- Example: `projectStart=Jun01, Pred A EF=Jun03 FS lag 0 → ES = max(Jun01, Jun03) = Jun03`

**Relationship contribution (forward) — predecessor-driven ES candidate**
```
FS -> addWorkingDays(predEarlyFinish, lag)
FF -> addWorkingDays(predEarlyFinish, lag)
SS -> addWorkingDays(predEarlyStart, lag)
SF -> addWorkingDays(predEarlyStart, lag)
default -> predEarlyFinish
```
- File: `CPMScheduler.java:445-465`. Notes: Lag added in working days via `calendarCalculator.addWorkingDays`. Negative lag (lead) routes through subtractWorkingDays (SnapshotCalendarCalculator:73).
- Example: `Pred EF=Jun03, FS lag=2, Sat/Sun off → addWorkingDays(Jun03,2)=Jun05`

**Early Finish (EF)** — `earlyFinish = addWorkingDays(activityCalendar, earlyStart, remainingDuration)`
- File: `CPMScheduler.java:336-342`. Notes: `addWorkingDays(start,0)==start`; result is date AFTER the last consumed working day (half-open). Completed activities EF forced to actualFinishDate.
- Example: `ES=Jun03, dur=3, 5-day week → Mon Jun08`

### Backward pass

**Project finish anchor** — `projectFinishDate = max( (mustFinishByDate != null ? mustFinishByDate : projectStartDate), max over all activities of earlyFinish )`
- File: `CPMScheduler.java:353-361`. Notes: Documented limitation: a deadline earlier than computed finish yields zero/positive float, never negative float.
- Example: `maxEF=Jul20, mustFinishBy=Jul15 → Jul20`

**Late Finish (LF)** — `lateFinish = min( projectFinishDate, min over successors of relationshipContribution(backward), secondaryConstraintAdj )`
- File: `CPMScheduler.java:369-396`. Notes: Reverse topological order. RETAINED_LOGIC + actualFinish: LS=effectiveActualStart, LF=actualFinish (skip).
- Example: `projectFinish=Jul20, Succ B LS=Jul10 FS lag0 → LF = min(Jul20, Jul10) = Jul10`

**Relationship contribution (backward) — successor-driven LF candidate**
```
FS -> subtractWorkingDays(succLateStart, lag)
FF -> subtractWorkingDays(succLateFinish, lag)
SS -> subtractWorkingDays(succLateStart, lag)
SF -> subtractWorkingDays(succLateFinish, lag)
default -> succLateStart
```
- File: `CPMScheduler.java:445-465`. Notes: Mirror of forward contribution; lag subtracted in working days.
- Example: `Succ LS=Jul10, FS lag=2 → subtractWorkingDays(Jul10,2) = Jul08`

**Late Start (LS)** — `lateStart = subtractWorkingDays(activityCalendar, lateFinish, remainingDuration)`
- File: `CPMScheduler.java:396-402`. Notes: Symmetric to EF. RETAINED_LOGIC completed: LS=effectiveActualStart.
- Example: `LF=Jul10, dur=3, 5-day week → Jul07`

### Floats & critical path

**Total Float (TF)** — `totalFloat = computeWorkingDayDelta(earlyStart, lateStart)` = working-day signed distance ES→LS (= countWorkingDays(ES,LS) if ES≤LS, else −countWorkingDays(LS,ES))
- File: `CPMScheduler.java:192-204, 432-443`. Notes: Measured LS−ES (not LF−EF). Same-day = 0. Negative TF emits warning. countWorkingDays half-open [start,end).
- Example: `ES=Jun03, LS=Jun06 no non-working between → TF=3. ES==LS → 0.`

**Free Float (FF)**
```
For each FS relationship: candidate = computeWorkingDayDelta(predEarlyFinish, succEarlyStart)
freeFloat(successor) = min(existing freeFloat, candidate)
Activities never touched -> freeFloat = totalFloat
```
- File: `CPMScheduler.java:216-232`. Notes: Only FS relationships contribute. Initial seeded to Double.MAX_VALUE; if still MAX at end replaced by totalFloat.
- Example: `Pred EF=Jun08, Succ ES=Jun10, Jun09 working → candidate=2 → FF=2`

**Critical path determination**
```
minTotalFloat = min over all activities of totalFloat
criticalFloat = max(minTotalFloat, 0.0)
isCritical    = (totalFloat <= criticalFloat + 1e-6)
```
- File: `CPMScheduler.java:191-213`. Notes: Standard P6 critical = TF≤0, but if no zero-float activity exists the longest path (min TF) is still surfaced. epsilon=1e-6.
- Example: `floats {0,3,5,0} → criticalFloat=0, two zeros critical. {2,5,7} → criticalFloat=2, TF=2 critical.`

**Critical path length** — `criticalPathLength = sum of remainingDuration over activities where isCritical == true`
- File: `SchedulingService.java:237-240`. Notes: Sum of durations (days), not a calendar-date span. Reused as originalDuration baseline in ScheduleCompressionService.
- Example: `5+3+7 → 15`

**Project finish date (result)** — `projectFinishDate = max over all scheduled activities of earlyFinish (fallback projectStartDate)`
- File: `SchedulingService.java:228-231`. Notes: Independent recompute of the backward-pass anchor's max(EF).
- Example: `EFs {Jul10, Jul20, Jul15} → Jul20`

### Calendar math (SnapshotCalendarCalculator)

**Working-day add (addWorkingDays)**
```
remaining = days; while remaining > 0: if isWorkingDay(current) remaining--; current = current.plusDays(1)
return first date after the last consumed working day
```
- File: `SnapshotCalendarCalculator.java:71-94`. Notes: `addWorkingDays(start,0)==start`. Negative days → subtractWorkingDays. MAX_ITER=100000 guard. Non-working days skipped but still advance.
- Example: `start=Fri Jun05, days=2, Sat/Sun off → Tue Jun09`

**Working-day subtract (subtractWorkingDays)**
```
remaining = days; while remaining > 0: current = current.minusDays(1); if isWorkingDay(current) remaining--
return current
```
- File: `SnapshotCalendarCalculator.java:100-123`. Notes: Decrements first then tests, so result lands ON a working day. Negative days → addWorkingDays. MAX_ITER guard.
- Example: `from=Mon Jun08, days=1, Sat/Sun off → Fri Jun05`

**Count working days (countWorkingDays)** — number of working days in half-open [start, end) per the calendar snapshot
- File: `SnapshotCalendarCalculator.java:125-129; CPMScheduler.java:432-443`. Notes: Half-open: same-day returns 0. Underlies total/free float deltas and WBS_SUMMARY duration. Two SQL queries per unique calendar load the snapshot, then all math in-memory.
- Example: `[Mon Jun01, Mon Jun08) with Sat/Sun off = 5`

### WBS summary, constraints, compression, health

**WBS_SUMMARY (hammock) early dates & duration**
```
summary.earlyStart        = min over children of child.earlyStart
summary.earlyFinish       = max over children of child.earlyFinish
summary.remainingDuration = countWorkingDays(defaultCalendar, earliest, latest)
```
- File: `CPMScheduler.java:128-151`. Notes: Late dates similarly: lateStart=min child LS, lateFinish=max child LF (157-178). Summary activities seeded ES=EF=projectStartDate then overwritten.
- Example: `children ES {Jun03,Jun06}, EF {Jun10,Jun14} → summary ES=Jun03, EF=Jun14`

**effectiveActualStart (back-calculated start)**
```
if actualStartDate present -> actualStartDate
else if actualFinishDate & dur > 0 -> subtractWorkingDays(actualFinishDate, dur)
       (dur = originalDuration > 0 ? originalDuration : remainingDuration)
else -> actualFinishDate
```
- File: `CPMScheduler.java:413-426`. Notes: Used for completed activities lacking a logged start so the Gantt bar has width. Zero-width fallback if no duration.
- Example: `actualFinish=Jun10, originalDuration=4, 5-day week → Jun04`

**ALAP shift** — `if primaryConstraintType == AS_LATE_AS_POSSIBLE: earlyStart := lateStart; earlyFinish := lateFinish`
- File: `CPMScheduler.java:180-187`. Notes: Applied after backward pass so ALAP activities sit at latest dates; TF then computes near zero.

**Primary constraint adjustment (forward)**
```
START_ON_OR_AFTER  -> max(constraintDate, ES)
START_ON           -> constraintDate
FINISH_ON_OR_AFTER -> max(subtractWorkingDays(constraintDate, duration), ES)
else               -> ES unchanged
```
- File: `CPMScheduler.java:467-482`. Notes: Applied in forward pass before actuals. ALAP excluded here.
- Example: `ES=Jun03, START_ON_OR_AFTER=Jun10 → Jun10`

**Secondary constraint adjustment (backward)**
```
START_ON_OR_BEFORE  -> (constraintDate < LF ? addWorkingDays(constraintDate, duration) : LF)
FINISH_ON_OR_BEFORE -> min(constraintDate, LF)
FINISH_ON           -> constraintDate
else                -> LF unchanged
```
- File: `CPMScheduler.java:484-498`.
- Example: `LF=Jul20, FINISH_ON_OR_BEFORE=Jul15 → Jul15`

**Fast-track potential overlap (per FS critical relationship)** — `potentialOverlap = predecessorDuration × 0.5`; eligible only when both predecessor and successor have totalFloat == 0 and relationship is FINISH_TO_START
- File: `ScheduleCompressionService.java:74-120`. Notes: Conservative 50%-of-predecessor-duration overlap heuristic (convert FS→SS). durationSaved = potentialOverlap; duration itself unchanged.
- Example: `predecessor originalDuration=10 → 5 days overlap`

**Fast-track compressed project duration** — `compressedDuration = originalDuration − totalSaved; totalSaved = Σ recommendation.durationSaved; originalDuration = criticalPathLength`
- File: `ScheduleCompressionService.java:122-126`. Notes: Stored on CompressionAnalysis (FAST_TRACK).
- Example: `40 − (5+3) → 32`

**Crashing per-activity max reduction & cost (heuristic)**
```
maxCrashableDays   = originalDur × 0.5
crashedDuration    = originalDur − maxCrashableDays
estimatedCostPerDay = originalDur × 10    (arbitrary base)
crashCost          = estimatedCostPerDay × maxCrashableDays = originalDur×10 × (originalDur×0.5)
```
- File: `ScheduleCompressionService.java:182-219`. Notes: Placeholder costing (explicitly 'arbitrary base'). Critical activities sorted by originalDuration desc. cost-per-day = crashCost / maxCrashableDays = originalDur×10. totalAdditionalCost = Σ crashCost.
- Example: `originalDur=10 → maxCrashableDays=5, crashedDuration=5, costPerDay=100, crashCost=500`

**Crashing compressed project duration (heuristic)** — `compressedDuration = originalDuration × 0.70; totalPotentialSavings = originalDuration × 0.30; originalDuration = criticalPathLength`
- File: `ScheduleCompressionService.java:222-224`. Notes: Flat 30% assumed savings (acknowledged simplification). Independent of per-activity crash math.
- Example: `40 → savings=12, compressedDuration=28`

**Schedule health — critical / near-critical percentages**
```
criticalPct     = criticalActivities / totalActivities
nearCriticalPct = nearCriticalActivities / totalActivities
nearCritical    = count(0 < totalFloat <= 5)
```
- File: `ScheduleHealthService.java:57-76`. Notes: Near-critical band (0,5] days. totalFloatAverage = mean of all totalFloat (null→0).
- Example: `10 activities, 2 critical, 3 near-critical → criticalPct=0.2, nearCriticalPct=0.3`

**Schedule health — duration variance component**
```
daysDifference   = ChronoUnit.DAYS.between(projectStartDate, projectFinishDate)
monthCount       = daysDifference / 30.0
durationVariance = max(0, monthCount × 0.02 − 0.05)
```
- File: `ScheduleHealthService.java:129-143`. Notes: 2% penalty per month above a 5% acceptable threshold. Floored at 0.
- Example: `300 days → monthCount=10, durationVariance=max(0, 0.15)=0.15`

**Schedule health score** — `healthScore = 100 − (criticalPct×40) − (nearCriticalPct×20) − max(0, durationVariance×40); clamped to [0,100]`
- File: `ScheduleHealthService.java:79-80`. Notes: Weights: critical 40, near-critical 20, duration-variance up to 40. Clamped via Math.max(0,Math.min(100,...)).
- Example: `0.2,0.3,0.15 → 100 − 8 − 6 − 6 = 80.0`

**Schedule health — risk level** — `>=80 LOW; >=60 MEDIUM; >=40 HIGH; else CRITICAL`
- File: `ScheduleHealthService.java:145-155`.
- Example: `80.0 → LOW; 55 → HIGH; 30 → CRITICAL`

**Schedule health — float distribution buckets** — bucket by totalFloat: `==0 zero; <=5 1to5; <=10 6to10; else 10plus`
- File: `ScheduleHealthService.java:105-127`. Notes: Serialized to JSON on ScheduleHealthIndex.floatDistribution.
- Example: `{0,0,3,8,12} → {zero:2, 1to5:1, 6to10:1, 10plus:1}`

**Float path grouping (multiple float paths)**
```
minFloat = min over activities of totalFloat
an activity seeds a path when not yet processed and totalFloat <= minFloat + 1
path = predecessors traced back via FS/any adjacency
path.totalFloat = seed activity totalFloat
```
- File: `MultipleFloatPathFinder.java:13-86`. Notes: Path #1 is effectively the critical path (lowest float band, +1 tolerance). traceBack walks predecessors recursively, prepending, dedup via visited; activities marked processed so each belongs to one path.
- Example: `floats {0,0,2,2,9}: minFloat=0, band ≤1 picks the two zeros into path 1; TF=2 chain becomes a later path`

---

## 7. Capacity Utilization, Productivity Norms & Resource Utilization

Measures how efficiently manpower and equipment are used versus what the productivity norms say SHOULD be needed. It counts "role-days" (= Σ nos), resolves a productivity norm (output per resource per day) via a variant→role→unscoped fallback, converts executed quantity into "budget days" (= qty / outputPerDay), then computes efficiency = budgetDays / trackedActualDays × 100. The **counted-first display** principle: Actual is the raw counted role-days, and budget/util derive from that same counted basis so efficiency reproduces by eye.

### Counted-first Actual & norm resolution

**Role-days (Actual, counted-first basis)**
```
role_days = SUM(COALESCE(m.nos, 0)) grouped by (role_id, dpr, report_date, work_activity, activity)
   (working_hours deliberately NOT multiplied in)
```
- Inputs: `project.dpr_manpower.nos` (or `dpr_equipment.nos`) per DPR row.
- File: `CapacityUtilizationReportService.java:673`. Notes: The 'counted-first' Actual. DAY-basis rates are paid per person regardless of hours, so a half-day is NOT halved. Equipment uses identical sum-of-nos (line 714). `roleDays.intValue()` becomes NOS for the allocator (264). Accumulated into dayActualDays/monthActualDays/cumActualDays.
- Example: `5 masons + 3 masons same DPR/role → 8. working_hours=4 still gives 8.`

**Productivity norm resolution (output per resource per day) — reporting side**
```
outputPerDay = MANPOWER ? COALESCE(output_per_man_per_day, output_per_day) : output_per_day
resolved in tier order ROLE -> UNSCOPED (NONE if neither)
```
- Inputs: `resource.productivity_norms` (work_activity_id, role_id, norm_type, output_per_man_per_day, output_per_day); roleId from DPR; normType MANPOWER/EQUIPMENT.
- File: `CapacityUtilizationReportService.java:867`. Notes: ROLE tier requires role_id match with category/grade/make/model all NULL, ORDER BY created_at. UNSCOPED tier all NULL. Manpower prefers per-man-per-day; equipment uses per-machine output_per_day. Source tag VARIANT|ROLE|UNSCOPED|MIXED|NONE drives normSource label.
- Example: `(W, MasonRole, MANPOWER) output_per_man_per_day=1.2 → outputPerDay=1.2, source=ROLE`

**Productivity norm resolution — full 6-tier resource chain (resource side)**
```
first non-null of: ROLE -> SPECIFIC_RESOURCE -> RESOURCE_TYPE -> WORK_ACTIVITY(unscoped)
                   -> RESOURCE_LEGACY(standard_output_per_day) -> NONE
value = COALESCE(output_per_man_per_day, output_per_day)
```
- Inputs: `resource.resources.role_id`; `resource.productivity_norms`; `resource.resource_equipment_details.standard_output_per_day`.
- File: `ProductivityNormResolver.java:54`. Notes: `resolveByResource` walks all six tiers. `resolveByResourceType` (133) and `resolveByRoleOrType` (194) are 2-3 tier variants filtering by norm_type. WORK_ACTIVITY tier ORDER BY (output_per_man_per_day IS NULL), created_at. 102 seeded rows live in the unscoped/WORK_ACTIVITY tier.
- Example: `role=Mason, (W,Mason)=1.2 → ROLE. No role/specific/type → unscoped (W)=1.0 → WORK_ACTIVITY.`

**Productivity norm role-keyed 3-tier resolution (domain)**
```
VARIANT (workActivity, role, category/grade or make/model)
 -> ROLE (workActivity, role)
 -> UNSCOPED (workActivity); first present wins
```
- Inputs: `resource.productivity_norms` via ProductivityNormRepository finders; roleId, categoryId, gradeId, make, model, normType.
- File: `RoleProductivityNormResolver.java:63`. Notes: `resolveAsBudgeted` returns NormBudgeted tagging the matched tier. `ProductivityNormLookupService.resolve` (44) wraps this then falls through to legacy SPECIFIC_RESOURCE/RESOURCE_TYPE/UNSCOPED tiers. normType inferred from resource type code.
- Example: `resolveByRole(W, Mason, FineGrade,...): (W,Mason,FineGrade)→VARIANT; else (W,Mason)→ROLE; else (W)→UNSCOPED`

**Derived outputPerDay on norm create (from crew/hours)**
```
MANPOWER:  outputPerDay = outputPerManPerDay × crewSize
EQUIPMENT: outputPerDay = outputPerHour × workingHoursPerDay
   (only when outputPerDay not supplied directly)
```
- Inputs: `CreateProductivityNormRequest.outputPerDay` (if present wins), else outputPerManPerDay & crewSize (manpower) or outputPerHour & workingHoursPerDay (equipment).
- File: `ProductivityNormService.java:196`. Notes: Only auto-derives when outputPerDay null. Scope uniqueness enforced per (workActivity, scope).
- Example: `outputPerManPerDay=0.4, crewSize=5 → 2.0`

**Suggested planned units (days a resource is needed)** — `suggestedPlannedUnits = quantity / outputPerDay (HALF_UP, scale 2)`
- Inputs: caller quantity; `ResolvedNorm.outputPerDay`.
- File: `ProductivityNormService.java:86`. Notes: Returns null + source NONE when no WorkActivity link or no positive-output norm. basis formats 'quantity unit ÷ output/day = N days'.
- Example: `100 / 2.0 = 50.00 days`

### Capacity allocator

**Expected contribution per role (allocator weight)** — `expectedContribution = resolvedNorm × nos (0 if norm null/≤0 or nos≤0)`
- File: `CapacityAllocator.java:29`. Notes: `sideExpected = Σ over roles (norm × nos)` (buildSection:298). Drives the side-share hide decision and per-role qty split.
- Example: `1.2×8=9.6; 0.5×4=2.0; sideExpected=11.6`

**Side share of executed qty (SERIES / PARALLEL / SUBSTITUTE)**
```
single-side: share = qtyDone
PARALLEL:   share = qtyDone × sideExpected / (sideExpected + otherSideExpected)
SERIES:     smaller-expected side wins full qtyDone, larger hidden (null)
SUBSTITUTE: larger-expected side wins full qtyDone, smaller hidden
Tie -> both shown full qty
```
- Inputs: sideExpected, otherSideExpected (loadOtherSideExpectedPerDpr), qtyDone, `norm_combination` from `resource.work_activities.norm_combination` (default SERIES).
- File: `CapacityAllocator.java:86`. Notes: qtyDone≤0 → share 0 (visible). !hasSide → null (hidden). Hidden side credits actual NOS only but no qty/budget. SERIES uses compareTo≤0; SUBSTITUTE compareTo≥0.
- Example: `PARALLEL, MP=11.6, EQ=8.4, qty=20 → MP share = 20×11.6/20.0 = 11.6. SERIES: EQ (8.4 smaller) wins 20, MP hidden.`

**Per-role allocated qty (within visible side)** — `allocatedQty[role] = sideShare × expectedContribution[role] / totalContribution; null for untracked roles`
- File: `CapacityAllocator.java:112`. Notes: totalContrib≤0 → all null+untracked. HALF_UP scale 4. Sets `ara.normResolved=true` when allocated != null.
- Example: `sideShare=11.6, Mason 9.6, Helper 2.0, total=11.6 → Mason 9.6, Helper 2.0`

### Budget days, efficiency, cost

**Budget days (from executed/allocated qty and norm)**
```
budgetDays(period) = SUM over tracked (activity,role) of (allocatedQty_in_period / outputPerDay)
   only when normResolved AND outputPerDay > 0 AND qty > 0
```
- Inputs: `ara.dayQty/monthQty/cumQty`, `NormLookup.outputPerDay`.
- File: `CapacityUtilizationReportService.java:402`. Notes: HALF_UP scale 4. Untracked pairs contribute to actualDaysUntracked (416) and excluded from budget. If no tracked pair, budget/util/cost null → '—'. The BUDGET column next to Actual.
- Example: `9.6 / 1.2 = 8.0 days (equals 8 counted role-days → 100% util)`

**Tracked actual days (efficiency numerator basis)** — `trackedActual = actualDays − actualDaysUntracked − actualDaysOnHiddenSides`
- File: `CapacityUtilizationReportService.java:572`. Notes: Hidden + untracked days subtracted so efficiency isn't artificially dragged down by days that never drove a comparable budget. Hidden days still appear in total Actual (headcount accuracy) but excluded from util denominator.
- Example: `10 − 1 − 1 = 8`

**Capacity Utilization % / Efficiency (counted-first, reproduces by eye)**
```
utilizationPct = budgetDays / trackedActual × 100
   (only when budgetDays present and trackedActual > 0; else null = '—')
```
- File: `CapacityUtilizationReportService.java:582`. Notes: Efficiency = output-implied days / actually-spent days. >100% = more efficient than norm. HALF_UP scale 4 then ×100. Section total uses the same formula on summed columns (629), qty suppressed to avoid double-counting. counted-first (commit 93a90587): Actual = raw counted role-days, Budget = qty/norm, so util = Budget/Actual visually reproducible.
- Example: `8/8×100 = 100.0%. 8/10 → 80% (over-deployed).`

**Actual / Budget / Planned Nos (headcount from days)** — `Nos = days / workDays (HALF_UP scale 4)` applied to actualDays, budgetDays, plannedDays
- Inputs: actual/budget/plannedDays; workDays (default 26).
- File: `CapacityUtilizationReportService.java:575`. Notes: workDays default 26 (defaultWorkDays:1104; effectiveWorkDays clamps ≤0 to 26 at 83). Converts person-days into average headcount. budgetNos suppressed when budgetDays null/zero. Section totals don't carry budgetNos.
- Example: `52 / 26 = 2.0 (avg 2 people)`

**Cost implication (variance days valued at rate)** — `costImplication = (trackedActual − budgetDays) × ratePerDay (scale 2, HALF_UP); null when no norm resolved`
- File: `CapacityUtilizationReportService.java:586`. Notes: Positive = cost overrun. When no budget, cost is undefined not zero — effectiveRate passed null so no fabricated overrun of actual×rate. Section total sums per-row cost (615).
- Example: `(10−8)×500 = 1000`

**DPR-weighted role rate per day (cost denominator)**
```
ratePerDay = SUM(nos × working_hours/8 × COALESCE(unit_rate, variantRate))
           / SUM(nos × working_hours/8)
fallback = AVG(rate) across role variants
```
- Inputs: `dpr_manpower/dpr_equipment` nos, working_hours, unit_rate; `manpower_role_rates.rate / equipment_role_variants.rate` via FK; default 8 hours/day.
- File: `CapacityUtilizationReportService.java:931`. Notes: Here working_hours/8 IS applied (cost weighting), unlike the role-days Actual count. unit_rate stamped on DPR wins over variant table rate. Fallback AVG only for roles with zero DPR rows in window. HALF_UP scale 4. Same as the SC180-classic bottom table.
- Example: `(nos=5,h=8,r=500)+(nos=3,h=4,r=600): days=5+1.5=6.5; cost=2500+900=3400; rate=3400/6.5=523.08`

**Role rate resolution (variant rate, override-first)** — `rate = projectOverride.overrideRate (if active) ELSE variant.rate ELSE null` per resource type MANPOWER/EQUIPMENT/MATERIAL
- Inputs: `Project*RoleRateOverride.overrideRate` (active); `ManpowerRoleRate.rate / EquipmentRoleVariant.rate / MaterialRoleVariant.rate`.
- File: `RoleRateResolver.java:46`. Notes: Two-tier: per-project active override wins over variant default. projectId null skips override tier. Returns null (warning, not failure) when variant missing.
- Example: `variant 500, active override 550 → 550`

### Qty adjustment, planned headcount, time buckets

**Effective company quantity (sub-contractor subtraction)** — `qtyDone = max(0, dpr.qty_executed − SUM(dpr_sub_contractor.quantity per DPR))`
- Inputs: MAX(d.qty_executed) per DPR, COALESCE(SUM(sc.quantity),0) from `project.dpr_sub_contractor`.
- File: `CapacityUtilizationReportService.java:306`. Notes: Sub-contractor portion removed (company didn't do it); clamped ≥0. Honors supervisor filter.
- Example: `20 − 5 = 15. sub=25 → max(0,−5) = 0`

**Planned headcount per bucket**
```
plannedNos(bucket) = SUM over assignments of COALESCE(headcount, quantity, planned_units)
   where activity planned-date-range intersects bucket OR activity has a DPR in bucket window
```
- Inputs: `resource.resource_assignments.headcount/quantity/planned_units, role_id`; `activity.activities planned_start_date/planned_finish_date`; DPR existence per bucket.
- File: `CapacityUtilizationReportService.java:1007`. Notes: Raw nos preferred (headcount) over legacy planned_units (which stored headcount×duration person-days). Null-dated activities always-active. DPR-existence fallback keeps Planned visible for late-running activities. Three buckets: Day=referenceDate single day, Month=calendar month, Cum=[from,to].
- Example: `headcount=10 on activity May 1-31 → May bucket 10; July-only activity → 0 to May`

**Time-bucketed aggregate (weekly/monthly slicing)**
```
window sliced into ISO-week (Mon-Sun, label YYYY-Www) or calendar-month (label YYYY-MM) buckets;
each bucket re-runs buildSection over [bucketStart, bucketEnd] keeping only the cumulative-over-bucket view
```
- Inputs: periodType WEEKLY|MONTHLY (default MONTHLY), fromDate/toDate window.
- File: `CapacityUtilizationReportService.java:127`. Notes: Each bucket reuses single-period buildSection so accumulator logic is single-sourced; `stripToCumulative` (180) nulls Day/Month fields. Buckets non-overlapping, inclusive both ends.
- Example: `2026-05-01..05-31 MONTHLY → one bucket '2026-05'; WEEKLY → 2026-W18..W22`

### Other resource-utilization carriers

**Resource daily utilisation % (ResourceDailyLog)** — `utilisationPercent = (actualUnits / plannedUnits) × 100` (only when plannedUnits>0 and actualUnits non-null)
- Inputs: `recordDaily` args plannedUnits, actualUnits per resource per logDate.
- File: `ResourceUtilisationService.java:36`. Notes: Inverse orientation of capacity-util efficiency: here actual/planned (output ratio), upserted onto ResourceDailyLog. pct null when plannedUnits≤0.
- Example: `(10/8)×100 = 125.0%`

**Resource utilization summary (ResourceUtilizationData DTO)** — `per-row utPct = actualHours/plannedHours×100 (producer-supplied); avgUtilization = mean of row utPct over totalResources`
- Inputs: `ResourceUtilRow.plannedHours, actualHours, utilPct`; aggregate avgUtilization, totalResources.
- File: `ResourceUtilizationData.java:7`. Notes: DTO-only carrier (hours-based, not the role-days SC180 model).
- Example: `plannedHours=160, actualHours=140 → 87.5%; avgUtilization=87.5, totalResources=1`

---

## 8. Variance Reports, %-Complete Reconciliation, DPR Rollup & Equipment-Utilization-Cost

Three P6-style grids and an activity-cost query, all anchored to a baseline. CostVarianceReportService pulls EVM rollups then layers a per-activity baseline-vs-live cost grid. ScheduleVarianceReportService diffs baseline early dates/durations against current planned in calendar days. DprReportService builds a BOQ × day matrix. ActivityCostQueryService computes planned/actual/remaining activity cost. Two AI tools compute equipment hour-utilization and deployment utilization. Note: physical vs financial % complete are NOT reconciled in one formula — physical % drives cost-variance "expected burn"; financial progress is the EV/AC-derived performancePercentComplete carried from EvmCalculation.

### EVM summary passthrough & WBS budget

**Project-level EVM summary (carried over, not recomputed)**
```
Summary = (BAC, PV, EV, AC, SV, CV, SPI, CPI, EAC, VAC, performancePercentComplete)
   from latest project-scoped EvmCalculation (activityId==null && wbsNodeId==null)
```
- Inputs: latest row from `evmCalculationRepository.findByProjectIdOrderByDataDateDesc`; all values precomputed by EvmService.
- File: `CostVarianceReportService.java:126-139`. Notes: Pure passthrough — no math. No project EVM row → emptySummary() all nulls (141-144). Conventions are EvmService's. Currency-neutral; rendering relabels only.

**WBS-node budget (crores to raw money)** — `budget = node.getBudgetCrores() × ONE_CRORE (= 1e7)`
- Inputs: `WbsNode.budgetCrores`; ONE_CRORE constant.
- File: `CostVarianceReportService.java:42,147-149`. Notes: Null budgetCrores → budget null. WBS rows only include nodes with wbsLevel null or ≤2 (88). PV/EV/AC/CV/CPI from latest EvmCalculation for that wbsNodeId (156-160); only budget derived. Hardcoded 1e7 crore divisor is INR-basis (portfolio/aggregate path).
- Example: `budgetCrores=2.5 → 25,000,000`

### Activity cost variance (baseline-vs-live)

**Activity estimate variance (current plan vs baseline plan)**
```
estimateVariance = currentPlanned − baselinePlanned
currentPlanned   = Σ ActivityExpense.budgetedCost + Σ ResourceAssignment.plannedCost (for the activity)
baselinePlanned  = BaselineActivity.plannedCost (or 0 if null)
```
- File: `CostVarianceReportService.java:177-181,205-218`. Notes: Nulls skip/zero in sums. Positive = current plan grew above baseline (scope/estimate creep).
- Example: `baseline 100,000; current 80,000+40,000=120,000 → +20,000`

**Activity burn variance (actual vs expected burn at % complete)**
```
expectedBurn = (percentComplete != null) ? round2(baselinePlanned × (percentComplete/100.0)) : 0
burnVariance = currentActual − expectedBurn
currentActual = Σ ActivityExpense.actualCost + Σ ResourceAssignment.actualCost
```
- Inputs: `Activity.percentComplete` (physical %); `BaselineActivity.plannedCost`; actual sums grouped by activityId.
- File: `CostVarianceReportService.java:183-189,220-233`. Notes: This is the physical-vs-financial reconciliation point — physical progress prorates the baseline budget to an expected spend, compared to real spend. When percentComplete unknown, expectedBurn=0 so burnVariance==currentActual. Positive = overspending relative to physical progress. Rows sorted worst burnVariance first (113-114). round2 = HALF_UP scale 2.
- Example: `baseline 100,000; pct=40 → expectedBurn=40,000; currentActual=55,000 → +15,000`

### Schedule variance (calendar days)

**Activity start variance (days)** — `startVar = ChronoUnit.DAYS.between(BaselineActivity.earlyStart, Activity.plannedStartDate)`
- File: `ScheduleVarianceReportService.java:100-102`. Notes: 0 when either date null or deleted. Positive = later start (slip). Calendar days, not working days.
- Example: `Jan10 → Jan17 = +7`

**Activity finish variance (days) — primary slippage metric** — `finishVar = ChronoUnit.DAYS.between(BaselineActivity.earlyFinish, Activity.plannedFinishDate)`
- File: `ScheduleVarianceReportService.java:103-105`. Notes: 0 when either date null/deleted. Positive = finishing later (slip). Rows sorted by finishVarianceDays descending (69-72). Drives slipped/ahead/onTrack classification.
- Example: `Mar01 → Mar15 = +14 (slipped)`

**Activity duration variance (days)** — `durationVar = Activity.originalDuration − BaselineActivity.originalDuration`
- File: `ScheduleVarianceReportService.java:106-108`. Notes: 0.0 when either null. Positive = stretched. Stored as double.
- Example: `20 − 15 = +5`

**Schedule variance summary aggregates**
```
slipped         = count(finishVar > 0)
ahead           = count(finishVar < 0)
onTrack         = count(finishVar == 0)
criticalSlipped = count(finishVar > 0 && isCritical)
milestoneSlipped = count(finishVar > 0 && isMilestone)
avgStart  = round2(Σ startVar / n)
avgFinish = round2(Σ finishVar / n)
worst     = max(finishVar)
```
- File: `ScheduleVarianceReportService.java:135-172`. Notes: isMilestone = type is START_MILESTONE or FINISH_MILESTONE (111-112). When n==0, avgs=0 and worst forced to 0 with null worstCode/Name. round2 = Math.round(v×100)/100.
- Example: `finishVar [+14,−3,0,+5], n=4 → slipped=2, ahead=1, onTrack=1; avgFinish=4.0; worst=+14`

### DPR matrix

**DPR matrix — achieved amount fallback (qty × rate)**
```
achievedAmount = (persisted actual_amount == 0 && qty_executed_to_date != 0 && boq_rate != 0)
                 ? round2(qty_executed_to_date × boq_rate)
                 : persisted actual_amount
```
- Inputs: `boq_items.actual_amount, qty_executed_to_date, boq_rate` (per BOQ line, project-scoped).
- File: `DprReportService.java:88-96`. Notes: Only fills in when persisted amount is zero but qty and rate exist. round2 = HALF_UP scale 2. projectionQty=boq_qty, projectionAmount=boq_amount, achievedQty=qty_executed_to_date carried as-is.
- Example: `actual_amount=0, qty=120, boq_rate=350 → 42,000.00`

**DPR matrix — per-day executed quantity vector**
```
perDayByItem[itemNo][dayOfMonth-1] += SUM(daily_progress_reports.qty_executed)
   for that boq_item_no on that report_date
```
- Inputs: `daily_progress_reports.boq_item_no` (soft FK to boq_items.item_no), report_date, qty_executed; filtered to month window [from..to] and boq_item_no NOT NULL, grouped by (boq_item_no, report_date).
- File: `DprReportService.java:61-81`. Notes: Array length = daysInMonth; index = reportDate.dayOfMonth−1; nulls until a day has data. DPR rows without a BOQ link excluded (activity-level progress).
- Example: `item 1.2 on 2026-06-05 (10 and 4) → perDay[4] = 14`

### Activity cost query (ActivityCostQueryService)

**Activity total planned cost (canonical)** — `plannedTotal = COALESCE(SUM(resource_assignments.planned_cost),0) WHERE activity_id = :id`
- File: `ActivityCostQueryService.java:240-246`. Notes: Always unfiltered — planned is a snapshot, not sliceable by date/supervisor.
- Example: `30,000 + 20,000 → 50,000`

**Activity total actual cost — unfiltered (canonical rollup)**
```
actualTotal = COALESCE(SUM(resource_assignments.actual_cost),0)
            + Σ material_consumption_logs.line_cost (WHERE activity_id=:id AND line_cost NOT NULL)
```
- Inputs: `resource_assignments.actual_cost` (= effective_rate × actual_units); `material_consumption_logs.line_cost` (store-keeper entries).
- File: `ActivityCostQueryService.java:248-262,285-297`. Notes: Used only when fromDate/toDate/supervisor all null. Consumption logs added because store-keeper entries never flow through the assignment rollup. Matches the activity sidebar Resource Plan 'Actual Cost' column.
- Example: `45,000 + 5,000 → 50,000`

**Activity actual cost — filtered (DPR-contribution × rate)**
```
actual = mp + eq + mt + logs
family = Σ (child.qty × COALESCE(assignment.effective_rate,0))
  mp: dpr_manpower.nos; eq: dpr_equipment.nos; mt: dpr_material.quantity
  joined a.activity_id=d.activity_id AND a.<variantFk>=c.<variantFk>
  only DPRs with approval_status IN (SUBMITTED, APPROVED)
logs = sumConsumptionLogContrib (0 if supervisor filter set)
```
- File: `ActivityCostQueryService.java:263-326`. Notes: Triggered when any of fromDate/toDate/supervisorUserId non-null. DAY-basis assumed; HOUR-basis approximated as nos × rate (no hour multiplier). DPR-line line_cost columns intentionally NOT summed (unpopulated in role-rate model). Supervisor filter drops the consumption-log feed.
- Example: `5×800=4000 + 2×1500=3000 + 10×50=500 + logs 0 → 7,500`

**Activity remaining cost** — `remainingTotal = max(plannedTotal − actualTotal, 0)`
- File: `ActivityCostQueryService.java:185-186`. Notes: Clamped to 0 when actual exceeds planned. Per-role merge applies the same clamp (578).
- Example: `50,000 − 58,000 → max(−8,000,0) = 0`

**Activity cost breakdown by RESOURCE_TYPE (unfiltered split)**
```
mp = SUM(actual_cost WHERE manpower_role_rate_id NOT NULL)
eq = SUM(actual_cost WHERE equipment_role_variant_id NOT NULL)
mt = SUM(actual_cost WHERE material_role_variant_id NOT NULL) + Σ material_consumption_logs.line_cost
```
- File: `ActivityCostQueryService.java:524-560`. Notes: Filtered variant uses the DPR-contribution path instead (530-539), adding consumption logs to material only when supervisor filter absent. Each row reports actual only (planned/remaining = 0).
- Example: `MANPOWER 20,000 / EQUIPMENT 15,000 / MATERIAL 5,000+2,000=7,000`

### AI equipment & deployment utilization tools

**Equipment hour-utilization % (AI tool, ClickHouse)**
```
available_hours     = sum(working_hours) + sum(idle_hours) + sum(breakdown_hours)
utilization_pct     = round(100.0 × sum(working_hours) / nullIf(available_hours, 0), 2)
cost_per_active_hour = null
```
- Inputs: `bipros_analytics.fact_dpr_equipment_daily` (working/idle/breakdown_hours, ownership, equipment_type, fleet_no, equipment_row_id); project_id=:pid AND report_date BETWEEN from AND to; grouped by equipment_row_id.
- File: `AnalyzeEquipmentUtilizationCostTool.java:123-142`. Notes: Default window to=today, from=to−30d. utilization_pct null when available_hours=0. cost_per_active_hour always null (hourly_rate absent). Blank ownership → 'UNKNOWN'. Sorted util desc, LIMIT 500.
- Example: `working=120, idle=40, breakdown=20 → available=180; util = 66.67`

**Equipment ownership summary — avg utilization (AI tool)** — `avg_utilization_pct[ownership] = round(Σ utilization_pct over rows in bucket / count × 100)/100; avg_cost_per_active_hour = null`
- File: `AnalyzeEquipmentUtilizationCostTool.java:208-235`. Notes: Simple arithmetic mean (not hour-weighted). round2 via Math.round(x×100)/100. avg_cost null.
- Example: `OWNED [66.67,80,50] → 65.56`

**Manpower deployment % (AI deployment_utilization tool)**
```
available_person_days = Σ over MANPOWER assignments (headcount × overlapDays(plannedStart,plannedFinish,from,to))
actual_person_days    = Σ dpr_manpower.nos
deployment_pct        = round2(100 × actual_person_days / available_person_days)
idle_pct              = round2(100 − deployment_pct)
```
- Inputs: ResourceAssignment.headcount + planned dates; DprManpower.nos (optionally supervisor-scoped).
- File: `DeploymentUtilizationTool.java:263-326,505-517`. Notes: overlapDays = inclusive day count of intersection (DAYS.between(maxStart,minEnd)+1), −1 if dates missing (assignment skipped & counted), 0 if no overlap. headcount≤0 skipped. pct null when available=0. actual_working_hours = Σ(working_hours × nos); actual_idle_hours = Σ(idle_hours × nos).
- Example: `headcount=10 × 20 days = 200; Σ nos=160 → deployment_pct=80.00; idle_pct=20.00`

**Equipment deployment % (AI deployment_utilization tool)**
```
available_unit_hours = Σ over EQUIPMENT assignments (headcount × overlapDays × 8.0)
actual_working_hours = Σ (dpr_equipment.working_hours × nos)
deployment_pct = round2(100 × actual_working_hours / available_unit_hours)
idle_pct       = round2(100 × actual_idle_hours / available_unit_hours)
breakdown_pct  = round2(100 × actual_breakdown_hours / available_unit_hours)
```
- Inputs: ResourceAssignment.headcount + planned dates; DprEquipment.working/idle/breakdown_hours and nos; HOURS_PER_DAY=8.0.
- File: `DeploymentUtilizationTool.java:58,330-400`. Notes: Each DPR hour bucket multiplied by nos. idle_pct and breakdown_pct measured against the SAME available denominator (so deployment+idle+breakdown may not sum to 100). All pct null when available=0.
- Example: `headcount=2 × 10 days × 8 = 160; Σ working×nos=128 → deployment_pct=80.00`

**Material/BOQ consumption % (AI deployment_utilization tool)**
```
boq_consumption_pct      = round2(100 × boq_executed_to_date_total / boq_planned_total)
per-item consumption_pct = round2(100 × BoqItem.qtyExecutedToDate / BoqItem.boqQty)
```
- Inputs: `BoqItem.boqQty` (planned) and `qtyExecutedToDate` (executed) summed across project BOQ lines; per-line for row detail; dpr_window_consumed_total = Σ DprMaterial.quantity in window.
- File: `DeploymentUtilizationTool.java:404-472`. Notes: ratioPct null when planned total/qty is 0; rows with planned=0 && executed=0 skipped. dpr_window_by_material groups DprMaterial.quantity by material_name (window-scoped, separate from executed-to-date BOQ %). round2 = BigDecimal HALF_UP scale 2.
- Example: `boqQty sum=1,000, qtyExecutedToDate sum=650 → 65.00`

---

## Cross-Cutting Conventions

### Currency — relabel, never convert
All money is **currency-neutral raw numbers** (`units × rate`, variance, CV/CPI/SPI, contribution, efficiency, margin). Changing a project's currency only relabels and re-abbreviates figures — it NEVER multiplies by an FX rate and NEVER touches a business-value calculation. No FX rates are maintained anywhere; `CurrencyService.convert()` is called from zero production money paths. The compact ladder is currency-driven (INR → Cr/L/k grouped en-IN; everything else → B/M/K grouped en-US), so the same stored `30000000` renders `3 Cr` for INR and `30 M` for OMR/USD.

### Crore / major-unit scaling
Project-level budgets (`Project.originalBudget`/`currentBudget`, `WbsNode.budgetCrores`) are stored in a currency **major-unit scale** — **1e7 (1 crore) for INR, 1e6 (1 million) otherwise** — and multiplied up by `majorUnitFactor` to raw currency units only when surfaced alongside raw-unit ledger totals (EvmService.java:128; CostService.java:653-676; CostVarianceReportService ONE_CRORE=1e7). On a currency change the stored major-unit value is rescaled by `oldFactor/newFactor` so the raw money is invariant. Known limitation: only the `currentBudget` aggregate is rescaled, not individual approved budget-change rows. The crore-basis WBS path (`CostVarianceReportService`) is a portfolio/aggregate surface that hardcodes 1e7 (INR-basis).

### Rounding
- **Amounts**: `BigDecimal`, scale 2, `RoundingMode.HALF_UP` (AMOUNT_SCALE=2) — DBS sections, BOQ amounts, line_cost, margins, RA bills, period totals.
- **Rates**: scale 4, HALF_UP (RATE_SCALE=4) — BOQ actualRate weighted average, register weightedRate, DPR-weighted role rate, allocator splits.
- **Ratios / percents**: scale 6, HALF_UP (RATIO_SCALE=6) — BOQ percentComplete, costVariancePct, marginPct, overallPct; EVM indices use SCALE=4 stored as Double; capacity util scale 4 then ×100; some AI/schedule tools use `Math.round(v×100)/100` (2dp).
- **Float shares** in Daily Cost Report ETC/EAC use scale 8; per-role allocator scale 4.

### Null / division-by-zero guards
- **EVM indices**: SPI/CPI/TCPI/perf% return `0.0` (helper) or `null` (getActivityEvm / cost-account rollup) when the denominator (PV/AC/BAC/EAC−AC) is zero; cost-account PV (and SV/SPI) null when any contributing activity has non-computable PV.
- **Margins / variance %**: `null` when revenue / earnedBudget / budgetedCost / denominator is 0 (explicit "no earned budget yet => no variance %").
- **Capacity util / cost**: `null` ('—') when budgetDays absent, trackedActual ≤ 0, or no norm resolved (cost effectiveRate passed null — no fabricated overrun).
- **Cash-flow CPI/SPI**: default to **1** when cumAC/cumPV ≤ 0; forecast returns periodBudget unchanged when futureRemainingBudget==0 or CPI/composite==0.
- **`nz()`** treats nulls as zero in BOQ/cost sums; section calculators start subtotals at ZERO and skip null contributions.
- **Clamps**: `subtractExecutedQty` floors qty at 0; remaining cost `max(planned − actual, 0)`; ETC `max(budgetedCost − actualCost, 0)`; materialIssued / totalRemaining floored at 0; effective company qty `max(0, qty − sc)`.
- **Scheduling**: countWorkingDays half-open (same-day = 0); MAX_ITER=100000 guards calendars with no working days; negative TF emits a warning (deadline-earlier-than-finish yields zero/positive float, never negative).

### Recompute triggers / events
- **`DprSubmittedEvent`** → (a) `DprBoqSyncListener` synchronously updates `BoqItem.qtyExecutedToDate` (same TX — failure rolls the DPR back) → BOQ percentComplete; (b) `BoqActualRateRecalcListener` recomputes actualRate weighted average (new + old boqItemId, skipped if `manualOverride`); (c) role-only/SC assignment actual rollups; (d) `DbsRecomputeListener` refreshes the four-tier DBS P&L; (e) ClickHouse analytics facts (hours/fuel aggregates).
- **`ResourceDeploymentSavedEvent`** (DRD save) → DBS legacy section recompute.
- **`MaterialConsumptionLoggedEvent`** → fans out to all BOQ items touched by DPRs on that activity (actualRate recompute) + DBS Section E refresh.
- **`GeneralExpenseLoggedEvent`** → `DbsRecomputeListener` refreshes daily project rollups for the affected month.
- **`ProjectCreatedEvent`** → seeds 20 default general-expense plan items (idempotent) and financial-period auto-generation.
- **Idempotent rebuilds**: register aggregation deletes (project,date) rows then re-inserts (REQUIRES_NEW); financial-period auto-gen skips existing sortOrders and never deletes; phantom assignment rows (all-zero) are swept after rollup.
- **Read-time recompute**: DPR cumulative quantity is never stored — recomputed on read so back-dated edits stay self-consistent; BoqItem derived columns recompute on every save via `BoqCalculator.recompute()` + `applyAutoStatus`.

---

# Risk Analysis — BIPROS EPPM Platform Reference

This section documents the risk-domain formulas implemented in the `bipros-risk` backend module: qualitative scoring and quantitative exposure, the Monte Carlo distribution samplers, the Monte Carlo simulation engine and its output statistics, and the correlation/sensitivity/quality machinery. All citations are `file:line` into `bipros-risk/src/main/java/com/bipros/risk/...`.

---

## 1. Risk Scoring / Exposure / EMV / RAG

A `Risk` carries an ordinal probability (1–5) and two impact scores (`impactCost` 1–5, `impactSchedule` 1–5). `RiskScoringMatrixService.computeCompositeScore` derives a single impact via the project's `ScoringMethod`, looks up the matrix cell (default cell = P × I) to get `riskScore`, and `Risk.deriveRag` bands it into CRIMSON/RED/AMBER/GREEN/OPPORTUNITY. Separately, `RiskExposureService` rolls up assigned-activity budgeted cost and multiplies by probability% × impact% to get pre/post-response cost exposure (EMV). There is **no** schedule EMV and **no** category-level weighted-score rollup in this module.

### 1.1 Probability ordinal → numeric mapping

```
VERY_LOW=1, LOW=2, MEDIUM=3, HIGH=4, VERY_HIGH=5      (RiskProbability.value)

JSON ingest (fromAny / @JsonCreator):
  if numeric d ∈ [0,1] (decimal bucket):
     d<0.2 → VERY_LOW
     d<0.4 → LOW
     d<0.6 → MEDIUM
     d<0.8 → HIGH
     else  → VERY_HIGH
  else ordinal = round(d) → 1..5     (out of range throws)
```

- **Inputs:** `Risk.probability` / `postResponseProbability` enum; `getValue()` used by `RiskService.calculateScores`. Decimal/ordinal coercion only on inbound JSON deserialization.
- **Citation:** `domain/model/RiskProbability.java:9-51`
- **Notes:** Same scale on `RiskImpact.java:3-8` (VERY_LOW=1 .. VERY_HIGH=5). Decimal bucket boundaries are half-open lower / inclusive: 0.4 → MEDIUM, 0.6 → HIGH. `round()` is HALF_UP via `Math.round`.

### 1.2 Derived impact (cost + schedule → single 1–5)

```
HIGHEST_IMPACT (default): max(cost, schedule)
AVERAGE_IMPACT:           (cost + schedule) / 2     [integer division, truncates]
AVERAGE_INDIVIDUAL:       handled in computeCompositeScore, not here
null impact treated as 0
```

- **Inputs:** `Risk.impactCost`, `Risk.impactSchedule` (Integer 1–5); `RiskScoringConfig.scoringMethod` (default HIGHEST_IMPACT).
- **Citation:** `domain/model/Risk.java:200-210`
- **Notes:** Integer division: cost=3, schedule=2 under AVERAGE_IMPACT → (3+2)/2 = 2 (not 2.5). A null impact counts as 0, dragging the average down (cost=4, schedule=null → (4+0)/2=2 under AVERAGE_IMPACT). The `default` branch == HIGHEST_IMPACT.
- **Example:** cost=4, schedule=2, HIGHEST_IMPACT → max(4,2)=4. Under AVERAGE_IMPACT → (4+2)/2=3.

### 1.3 Matrix cell score lookup

```
score = matrixCell(projectId, probabilityValue, derivedImpact).score
if no cell exists → fallback = probabilityValue × derivedImpact
Default seeded matrix: every cell score = p × i  for p,i ∈ 1..5   (range 1..25)
```

- **Inputs:** `RiskScoringMatrix` rows (`project_id`, `probability_value` 1–5, `impact_value` 1–5, `score`); `probabilityValue` from `RiskProbability.getValue()`; `derivedImpact` from `Risk.deriveImpact`.
- **Citation:** `application/service/RiskScoringMatrixService.java:34-45` (lookup), `:101-125` (default seed p×i)
- **Notes:** Matrix is per-project editable (`updateMatrix` replaces all cells, validates p,i ∈ 1..5, score ≥ 0). Custom matrices can break the ≥20/12/6 RAG thresholds if scores no longer span 1..25. `ensureMatrixExists` seeds on first risk create.
- **Example:** P=HIGH(4), derivedImpact=4 → default cell score = 4×4 = 16.

### 1.4 Composite risk score (3 scoring methods)

```
if probability null → null
if both impacts null → null

AVERAGE_INDIVIDUAL:
   costScore  = matrix(P, impactCost)
   schedScore = matrix(P, impactSchedule)
   if both null → null; if one null → the other;
   else (costScore + schedScore) / 2      [int div]

HIGHEST_IMPACT / AVERAGE_IMPACT:
   derivedImpact = deriveImpact(...)
   score = matrix(P, derivedImpact)
```

- **Inputs:** `projectId`, `probability.getValue()`, `Risk.impactCost`, `Risk.impactSchedule`; `RiskScoringConfig.scoringMethod`.
- **Citation:** `application/service/RiskScoringMatrixService.java:67-85`
- **Notes:** AVERAGE_INDIVIDUAL averages two **separate** matrix lookups (not the impacts), then integer-divides. The other two methods average/max the **impact** first then do one lookup. Called by `RiskService.calculateScores` for both pre- and post-response on every create/update.
- **Example:** P=4, impactCost=5, impactSchedule=3, AVERAGE_INDIVIDUAL on default matrix: costScore=4×5=20, schedScore=4×3=12 → (20+12)/2 = 16.

### 1.5 Pre-response risk score + RAG application

```
if matrixScore != null AND probability != null:
   riskScore = matrixScore   (as double)
   rag       = deriveRag(riskScore, isOpportunity)
```

- **Inputs:** `matrixScore` from `computeCompositeScore`; `Risk.probability`; `Risk.riskType` (isOpportunity = riskType == OPPORTUNITY).
- **Citation:** `domain/model/Risk.java:175-180`; `RiskService.java:473-490` (`calculateScores`)
- **Notes:** `riskScore` stored as Double in `risks.risk_score`. RAG is only recomputed on the pre-response apply path (post-response apply does NOT set rag). Recompute trigger: every `createRisk`/`updateRisk` via `calculateScores`.
- **Example:** matrixScore=16, threat → riskScore=16.0, rag=RED.

### 1.6 Post-response (residual) risk score

```
if matrixScore != null AND postResponseProbability != null:
   postResponseRiskScore = matrixScore   (double)
Same computeCompositeScore but fed
   postResponseProbability, postResponseImpactCost, postResponseImpactSchedule
```

- **Inputs:** `Risk.postResponseProbability`, `postResponseImpactCost`, `postResponseImpactSchedule`; same project matrix/method.
- **Citation:** `domain/model/Risk.java:187-191`; `RiskService.java:482-489`
- **Notes:** Stored in `post_response_risk_score`. The entity field `residualRiskScore` (`residual_risk_score`) is a **separate** column that is never written by `calculateScores` — only `postResponseRiskScore` is computed. `residualRiskScore` appears unused/legacy in the scoring path.
- **Example:** post P=MEDIUM(3), postImpactCost=2, postImpactSchedule=2, HIGHEST_IMPACT default matrix → derivedImpact=2, score=3×2=6 → postResponseRiskScore=6.0.

### 1.7 RAG banding thresholds

```
score null               → null
opportunity (riskType==OPPORTUNITY) → OPPORTUNITY   (regardless of score)
else score >= 20         → CRIMSON
     score >= 12         → RED
     score >= 6          → AMBER
     else                → GREEN
```

- **Inputs:** `riskScore` (Double), opportunity flag (`Risk.isOpportunity` from riskType).
- **Citation:** `domain/model/Risk.java:221-228`
- **Notes:** Thresholds assume a 1..25 default matrix. Bands: GREEN [1,6), AMBER [6,12), RED [12,20), CRIMSON [20,25]. CRIMSON only reachable at P=5,I=4 (20), P=4,I=5 (20), P=5,I=5 (25) on the default matrix. Opportunities never get a threat color.
- **Example:** riskScore=16, threat → RED (12≤16<20). riskScore=25, threat → CRIMSON. riskScore=16, opportunity → OPPORTUNITY.

### 1.8 Probability % (EMV discount factor)

```
VERY_HIGH → 0.95
HIGH      → 0.60
MEDIUM    → 0.40
LOW       → 0.20
VERY_LOW  → 0.05
probability null → exposure cost = 0
```

- **Inputs:** `Risk.probability` (pre) or `Risk.postResponseProbability` (post).
- **Citation:** `application/service/RiskExposureService.java:165-171`
- **Notes:** Hardcoded P6 illustrative defaults (not the matrix, not per-project configurable). These % are **non-linear** and differ from the 1–5 ordinal scale used for scoring (e.g. HIGH=0.60, not 0.80).
- **Example:** probability=HIGH → probabilityPct=0.60.

### 1.9 Impact % (EMV discount factor)

```
derivedImpact = Risk.deriveImpact(impactCost, impactSchedule, method)
5 → 0.40
4 → 0.20
3 → 0.10
2 → 0.05
1 → 0.02
default → 0.05
```

- **Inputs:** `Risk.impactCost`, `Risk.impactSchedule` (pre) or `postResponseImpactCost`/`Schedule` (post); `RiskScoringConfig.scoringMethod`.
- **Citation:** `application/service/RiskExposureService.java:173-183`
- **Notes:** Hardcoded P6 illustrative defaults. The `default` branch (derivedImpact 0, i.e. both impacts null) → 0.05. Same `deriveImpact` method/division semantics as scoring.
- **Example:** impactCost=5, impactSchedule=3, HIGHEST_IMPACT → derivedImpact=5 → impactPct=0.40.

### 1.10 Total budgeted cost of assigned activities

```
totalBudgetedCost = Σ over ActivityExpense (project_id, activity_id ∈ assignedActivityIds)
                       of budgetedCost   (nulls skipped, reduce from ZERO)
if total == 0 → both pre/post exposure cost set to 0
```

- **Inputs:** `RiskActivityAssignment.activityId` list for the risk; `ActivityExpense.budgetedCost` filtered by `Risk.projectId` + activityIds.
- **Citation:** `application/service/RiskExposureService.java:126-138`
- **Notes:** BigDecimal sum. Recompute triggers: `addActivityToRisk` / `removeActivityFromRisk` (`RiskService`) and `recalculateForActivity` (called on Activity/ActivityExpense update). If no assignments at all, all exposure dates + costs are nulled (lines 59-66).
- **Example:** Two activities with budgetedCost 1,000,000 + 500,000 → totalBudgetedCost = 1,500,000.

### 1.11 Exposure cost / EMV (pre and post response)

```
exposureCost = totalBudgetedCost × probabilityPct × impactPct      (scale 2, HALF_UP)

Pre  uses (probability,            impactCost,            impactSchedule)
Post uses (postResponseProbability, postResponseImpactCost, postResponseImpactSchedule)
Stored in pre_response_exposure_cost / post_response_exposure_cost
```

- **Inputs:** `totalBudgetedCost` (sum of `ActivityExpense.budgetedCost`); `probabilityPct`; `impactPct` (derived via project ScoringMethod).
- **Citation:** `application/service/RiskExposureService.java:140-189`
- **Notes:** This is the cost EMV (expected monetary value). Rounded to 2 decimals HALF_UP. No FX conversion (currency-neutral raw number). There is **no** schedule EMV (probability × days) anywhere in the module despite `Risk.scheduleImpactDays` existing — `scheduleImpactDays` feeds nothing computed here.
- **Example:** totalBudgetedCost=1,500,000, probability=HIGH(0.60), derivedImpact=5(0.40) → 1,500,000 × 0.60 × 0.40 = 360,000.00.

### 1.12 Exposure start/finish dates

```
exposureStartDate  = MIN over assigned activities of (plannedStartDate,  else earlyStartDate)
exposureFinishDate = MAX over assigned activities of (plannedFinishDate, else earlyFinishDate)
nulls filtered; no activities/dates → null
```

- **Inputs:** `Activity.plannedStartDate`/`earlyStartDate`, `plannedFinishDate`/`earlyFinishDate` for assigned activities.
- **Citation:** `application/service/RiskExposureService.java:106-124`
- **Notes:** Per-activity falls back to early dates only when the planned date is null. Computed in the same save as costs (`recalculateAll`).
- **Example:** Activities start 2026-03-01 & 2026-04-15, finish 2026-06-30 & 2026-05-10 → exposureStart=2026-03-01, exposureFinish=2026-06-30.

### 1.13 Project-level total risk exposure rollup

```
calculateRiskExposure = Σ preResponseExposureCost
   over risks where status != CLOSED AND != RESOLVED AND preResponseExposureCost != null
   (reduce from ZERO)
```

- **Inputs:** `Risk.preResponseExposureCost`, `Risk.status` for all project risks.
- **Citation:** `application/service/RiskService.java:381-389`
- **Notes:** Only the **pre-response** exposure is summed (post-response/residual not aggregated). Closed/Resolved excluded; other terminal-ish statuses (ACCEPTED, REJECTED, REALISED, CLOSED variants) are **not** excluded.
- **Example:** Open risks with pre-exposure 360,000 + 50,000; one RESOLVED risk 100,000 → total = 410,000.

### 1.14 Risk matrix bucketing (no score aggregation)

```
getRiskMatrix groups risks (probability != null && impactCost != null) into
   Map  key = probability.name() + "_" + impactCost  →  List<RiskSummary>
No counts, no weighted score, no EMV per cell.
```

- **Inputs:** `Risk.probability` (enum name), `Risk.impactCost`.
- **Citation:** `application/service/RiskService.java:365-379`
- **Notes:** This is a heat-map bucketing helper, **not** a scoring rollup. Keyed on `impactCost` only (ignores `impactSchedule` and the configured ScoringMethod). No severity counts / weighted-score math exists in this module; category DTOs (`RiskCategorySummaryDto`, `RiskCategoryTypeSummary`) are flat id/code/name projections with zero computed fields.
- **Example:** A risk with probability=HIGH, impactCost=4 → bucket key `"HIGH_4"`.

### 1.15 Risk trend / severity band (enums, no formula)

```
RiskTrend = {WORSENING, STABLE, IMPROVING}   stored as-is (risks.trend); not computed from any delta
RAG (RiskRag) doubles as severity band {CRIMSON, RED, AMBER, GREEN, OPPORTUNITY} via deriveRag
```

- **Inputs:** `Risk.trend` set directly by caller (no update path in `RiskService` writes it from a computation); RAG from `deriveRag`.
- **Citation:** `domain/model/RiskTrend.java:4-8`; `RiskRag.java:7-13`
- **Notes:** `trend` is a manual classification — no automatic exposure-delta-since-last-review calculation exists in code despite the doc comment. There is no separate numeric severity score beyond `riskScore` + its RAG band.

---

## 2. Distribution Samplers

The Monte Carlo engine samples activity durations / risk impacts from one of seven distribution types (`DistributionType`: TRIANGULAR, BETA_PERT, UNIFORM, NORMAL, LOGNORMAL, TRIGEN, DISCRETE). Every sampler implements `DistributionSampler.sample(RandomGenerator)` and consumes either a single uniform `U = rng.nextDouble() ∈ [0,1]` or a Gaussian `Z = rng.nextGaussian()`. Uniform-consuming samplers also support `sampleFromUniform(u)` so Iman-Conover rank-correlation permutations can re-drive them from a preset U; Gaussian-consuming samplers (Normal, Lognormal) override `sampleFromUniform` to invert the standard-normal CDF via Acklam's closed-form approximation. `DistributionSamplers` is the factory: `threePoint()` maps min/mode/max to BETA_PERT/UNIFORM/TRIANGULAR (NORMAL/LOGNORMAL/TRIGEN/DISCRETE fall back to Triangular when only 3 points are given), and `fallback()` builds a symmetric ±fractionalVariance band (deriving σ = planned·fractionalVariance/3 for Normal/Lognormal).

### 2.1 Uniform inverse-CDF sample

```
x = min + U·(max − min),   U = rng.nextDouble() ∈ [0,1]
mode() = (min + max) / 2
```

- **Inputs:** `min`, `max` passed to `UniformSampler(min,max)`; built by `DistributionSamplers.threePoint(UNIFORM,…)` (mode ignored) or `fallback()` with min=max(0, planned·(1−fv)), max=planned·(1+fv).
- **Citation:** `application/simulation/UniformSampler.java:16-18`
- **Notes:** Constructor requires min < max (else IllegalArgumentException). No clamping of the result. fv = fractionalVariance. Linear inverse-CDF: F(x)=(x−min)/(max−min) ⇒ x=min+U·range.
- **Example:** min=80, max=120, U=0.25 → x = 80 + 0.25·40 = 90. mode()=(80+120)/2=100.

### 2.2 Triangular split inverse-CDF sample

```
Fc = (m − a) / (b − a)                       (left fraction)
if U < Fc:  x = a + √(U·(b−a)·(m−a))
else:       x = b − √((1−U)·(b−a)·(b−m))
U = rng.nextDouble()
```

- **Inputs:** a=min (optimistic), m=mode (most-likely), b=max (pessimistic). Built by `threePoint(TRIANGULAR,…)`, and is the fallback sampler for NORMAL/LOGNORMAL/TRIGEN/DISCRETE in `threePoint` when only 3 points are given.
- **Citation:** `application/simulation/TriangularSampler.java:26-31`
- **Notes:** `Fc` is the CDF value at the mode = probability mass in the left (rising) leg. The two branches are the exact inverse of the piecewise-quadratic triangular CDF. Requires a ≤ m ≤ b and a < b (else IllegalArgumentException). No post-sample clamping. mode()=m.
- **Example:** a=80, m=100, b=140. range=60, Fc=(100−80)/60=0.3333. U=0.20 < 0.3333 → left leg: x = 80 + √(0.20·60·20) = 80 + √240 = 95.49. U=0.80 ≥ Fc → right leg: x = 140 − √((1−0.80)·60·40) = 140 − √480 = 118.09.

### 2.3 Trigen (percentile-bounded triangular) sample

```
Given (p10, m, p90) treated as 10th/90th percentiles, solve for true triangular bounds A,B s.t.
   triangularCdf(p10; A,m,B) = 0.10
   triangularCdf(p90; A,m,B) = 0.90
where triangularCdf(x; a,m,b) = (x−a)² / ((b−a)(m−a))    for x < m
                              = 1 − (b−x)² / ((b−a)(b−m)) otherwise
Then sample = TriangularSampler(A, m, B).sample(rng)
```

- **Inputs:** p10 (lower percentile bound), m=mostLikely (mode), p90 (upper percentile bound). Built by `DistributionSamplers.trigen(p10,mode,p90)` and `fallback(...,TRIGEN)` with p10=min band, p90=max band.
- **Citation:** `application/simulation/TrigenSampler.java:23-48`
- **Notes:** A,B found once at construction by 2 outer passes each running 40-iter bisection (`solveLeftTail` finds A where cdf(p10)=0.10, `solveRightTail` finds B where cdf(p90)=0.90); initial guesses lo=p10−(p90−p10), hi=p90+(p90−p10). Per-sample cost is just the underlying Triangular inverse-CDF. Requires p10 ≤ m ≤ p90 and p10 < p90. The extrapolated A < p10 and B > p90 (true min/max lie outside the stated percentiles). mode()=m.
- **Example:** p10=90, m=100, p90=120 → solver extrapolates roughly A≈79, B≈134 (true bounds outside the 10/90 band); a draw U=0.5 then runs through `TriangularSampler(A,100,B)` inverse-CDF. (Exact A,B come from the bisection; the stated 90 and 120 become interior P10/P90, not the absolute min/max.)

### 2.4 Beta-PERT inverse-CDF sample

```
λ = 4,  range = b − a
α = 1 + λ·(m−a)/range = 1 + 4(m−a)/(b−a)
β = 1 + λ·(b−m)/range = 1 + 4(b−m)/(b−a)
U = rng.nextDouble(), clamp to [1e-12, 1−1e-12]
y = BetaDistribution(α,β).inverseCumulativeProbability(U) ∈ [0,1]
x = a + y·(b−a)
Implied PERT mean μ = (a + 4m + b) / 6
```

- **Inputs:** a=min (optimistic o), m=mode, b=max (pessimistic p). Built by `threePoint(BETA_PERT,…)` / `fallback(...,BETA_PERT)`. Uses Apache commons-math3 `BetaDistribution` (its internal `JDKRandomGenerator` seeded 0 is unused; sampling is by inverse-CDF on the caller's U).
- **Citation:** `application/simulation/BetaPertSampler.java:30-46`
- **Notes:** λ=4 is the classic PERT shape giving expected value (o+4m+p)/6. Sample is the scaled standard-Beta inverse-CDF y∈[0,1] mapped affinely onto [a,b]. U clamped away from 0/1 to avoid ±∞ from `inverseCumulativeProbability`. Requires a ≤ m ≤ b and a < b (a==b rejected). mode()=m. PERT mean μ=(o+λm+p)/(λ+2) with λ=4 = (o+4m+p)/6.
- **Example:** a=80, m=100, b=140. range=60. α=1+4·20/60=2.333, β=1+4·40/60=3.667. PERT mean=(80+4·100+140)/6=620/6=103.33. U=0.5 → y=Beta(2.333,3.667).invCDF(0.5)≈0.382 → x=80+0.382·60≈102.9 (near the PERT mean, as expected for a right-skewed Beta-PERT).

### 2.5 Normal sample (Gaussian draw / inverse-CDF, ±3σ truncated)

```
sample(rng):          Z = rng.nextGaussian(); clamp Z to [−3,+3]; x = μ + Z·σ
sampleFromUniform(u): clamp u to [1e-12, 1−1e-12]; Z = Φ⁻¹(u) via Acklam;
                      clamp Z to [−3,+3]; x = μ + Z·σ
```

- **Inputs:** mean μ, stddev σ. Built by `DistributionSamplers.normal(mean,stddev)`; in `fallback(...,NORMAL)` σ = planned·fractionalVariance/3 so the ±3σ truncation aligns with the ±fractionalVariance band; mode()=μ.
- **Citation:** `application/simulation/NormalSampler.java:19-34`
- **Notes:** Requires σ > 0. Z hard-clamped to ±3 (truncation) to avoid negative durations / extreme tails. `sampleFromUniform` overrides the interface default to use the closed-form inverse normal CDF (Acklam, accuracy <1e-9) so Iman-Conover rank permutations reproduce the same Z from a preset uniform. No clamp on the final x (only on Z).
- **Example:** μ=100, σ=10. u=0.975 → Z=Φ⁻¹(0.975)=1.96 (≤3, no clamp) → x=100+1.96·10=119.6. u=0.99999 → Z would be ~4.26 but clamped to 3 → x=130. sample(rng) with Z=−0.5 → x=95.

### 2.6 Lognormal sample (output-space parameterised, exp(μ+σZ))

```
CV² = (stddev/mean)²
σ²  = ln(1 + CV²)   ⇒   σ = √(ln(1 + (stddev/mean)²))
μ   = ln(mean) − σ²/2

sample(rng):          Z = rng.nextGaussian(); x = exp(μ + σ·Z)
sampleFromUniform(u): clamp u to [1e-12, 1−1e-12]; Z = Φ⁻¹(u) (Acklam); x = exp(μ + σZ)
mode() = exp(μ − σ²)
```

- **Inputs:** mean (output-space arithmetic mean, > 0), stddev (output-space, ≥ 0). Built by `DistributionSamplers.lognormal(mean,stddev)`; in `fallback(...,LOGNORMAL)` stddev = planned·fractionalVariance/3.
- **Citation:** `application/simulation/LognormalSampler.java:22-44`
- **Notes:** Parameters are the desired **output** mean/stddev; the constructor converts to underlying-normal (μ,σ) so E[X]=mean exactly. Always strictly positive (no negative durations). No ±3σ truncation (unlike Normal). `sampleFromUniform` overrides the interface default to invert via Acklam so Iman-Conover works. `expectedMean()` returns the original mean. mode()=exp(μ−σ²) < mean (right-skew).
- **Example:** mean=100, stddev=20. CV²=(20/100)²=0.04. σ=√(ln1.04)=√0.03922=0.1980. μ=ln100 − 0.5·0.03922 = 4.6052 − 0.0196 = 4.5856. u=0.5 → Z=0 → x=exp(4.5856)=98.06 (the median, just below the mean of 100). u=0.975 → Z=1.96 → x=exp(4.5856+0.1980·1.96)=exp(4.9737)=144.6. mode=exp(4.5856−0.03922)=94.3.

### 2.7 Discrete cumulative-weight pick

```
Normalise: cum[i] = (Σ_{k≤i} p_k) / (Σ_all p)
Draw U = rng.nextDouble()
return first value[i] with U ≤ cum[i];  if none (floating-point) → return last value
mode() = value with the largest raw probability
```

- **Inputs:** `List<Outcome(value, probability)>`. Built by `DistributionSamplers.discrete(outcomes)`. Probabilities renormalised on construction (need not sum to 1); negative probability or total ≤ 0 rejected.
- **Citation:** `application/simulation/DiscreteSampler.java:46-52`
- **Notes:** Inverse-CDF over a step function (cumulative-probability lookup, linear scan). Final cum value is 1.0; the U≤cum fallback to last element guards against U=1.0 / rounding. mode is the modal (highest-probability) outcome, set during the normalisation loop (first outcome wins ties since strict > is used).
- **Example:** Outcomes [(10, p=0.2),(20, p=0.5),(30, p=0.3)], total=1.0 → cum=[0.2,0.7,1.0]. U=0.15 → return 10. U=0.65 → return 20. U=0.95 → return 30. mode()=20 (highest probability 0.5).

### 2.8 Interface default `sampleFromUniform` (uniform-consuming samplers)

```
clamped = (u ≤ 0 ? 1e-12 : (u ≥ 1 ? 1−1e-12 : u))
return sample(SingleShotRng(clamped))
   — a RandomGenerator whose nextDouble() always returns the preset uniform
```

- **Inputs:** u ∈ [0,1] (e.g. a rank-permuted uniform from Iman-Conover). Used by Triangular, Trigen, Uniform, BetaPert, Discrete (all single-nextDouble consumers).
- **Citation:** `application/simulation/DistributionSampler.java:21-24`
- **Notes:** Lets correlation logic re-drive any single-uniform sampler from a preset U. `SingleShotRng.nextGaussian()` falls back to Acklam `staticInverseNormal(u)`, but Normal/Lognormal override `sampleFromUniform` directly because one uniform cannot consistently emulate a Gaussian draw across the sampler's internal logic. Clamp avoids 0/1 edge infinities.
- **Example:** `TriangularSampler(80,100,140).sampleFromUniform(0.20)`: SingleShotRng returns 0.20 from nextDouble → identical to sample(rng) with U=0.20 → x=95.49 (matches the Triangular worked example).

### 2.9 Acklam inverse standard-normal CDF Φ⁻¹(p)

```
pLow = 0.02425,  pHigh = 1 − pLow

p < pLow (lower tail):
   q = √(−2 ln p)
   Z = (((((c1·q+c2)q+c3)q+c4)q+c5)q+c6) / ((((d1·q+d2)q+d3)q+d4)q+1)

pLow ≤ p ≤ pHigh (central):
   q = p − 0.5,  r = q²
   Z = (((((a1·r+a2)r+a3)r+a4)r+a5)r+a6)·q / (((((b1·r+b2)r+b3)r+b4)r+b5)r+1)

p > pHigh (upper tail):
   q = √(−2 ln(1−p))
   Z = −[same c/d rational in q]
```

- **Inputs:** p ∈ (0,1) — a uniform (already clamped to [1e-12, 1−1e-12] by callers). Coefficients a1..a6, b1..b5, c1..c6, d1..d4 are Acklam's published constants (hardcoded).
- **Citation:** `application/simulation/DistributionSampler.java:41-69`
- **Notes:** Closed-form inverse normal CDF, accuracy < 1e-9. Shared by `NormalSampler.sampleFromUniform`, `LognormalSampler.sampleFromUniform`, and `SingleShotRng.nextGaussian`. Converts a (possibly rank-correlated) uniform into the Gaussian Z used by Normal/Lognormal so Iman-Conover rank permutations preserve correlation through Gaussian-based distributions.
- **Example:** p=0.5 → middle region, q=0, r=0 → numerator=a6·0=0 → Z=0 (median). p=0.975 → middle region, q=0.475 → Z≈1.95996 ≈ 1.96. p=0.001 → low region q=√(−2 ln0.001)=√13.816=3.717 → Z≈−3.09.

---

## 3. Monte Carlo Engine & Output Statistics

`MonteCarloEngine.run` runs a PRA-style schedule/cost risk simulation over a project's active baseline. Per iteration it samples each activity's duration from a three-point or fallback distribution, optionally injects risk-register driver impacts, runs CPM over the network, and records project duration, project cost, per-activity critical-path membership, ES/EF epochs, milestone finish dates, and linear-in-time monthly cashflow accrual. After all N iterations it sorts each series and extracts percentiles, mean, stddev, criticality, sensitivity, milestone date CDFs, and per-risk occurrence rates. Reproducibility comes from a `SplittableRandom` seeded by `input.randomSeed` (or `System.nanoTime`). **Contingency = P80 − deterministic is not computed in the backend** — the deterministic `baselineDuration` and all percentiles are persisted separately, and the difference is left to the consumer/frontend.

### 3.1 Per-activity sampled duration (three-point sampler selection)

```
sampled_dur_i = max(0, sampler_i.sampleFromUniform(correlatedU[iter][i]))

sampler selection:
   if PERT row valid (O,M,P non-null AND P>O)
        → threePoint(defaultDistribution, O=optimistic, M=mostLikely, P=pessimistic)
   else if originalDuration > 0
        → fallback(originalDuration, fallbackVariancePct, defaultDistribution)
   else → ConstantSampler(0)
```

- **Inputs:** `PertEstimate.optimisticDuration/mostLikelyDuration/pessimisticDuration` (per activity, `pertById`); `Activity.originalDuration`; `MonteCarloInput.defaultDistribution` (default TRIANGULAR); `MonteCarloInput.fallbackVariancePct` (default 0.2); `correlatedU` matrix from ImanConover.
- **Citation:** `application/simulation/MonteCarloEngine.java:128-145, 238-242`
- **Notes:** u clamped to [1e-12, 1−1e-12] inside `sampleFromUniform` (`DistributionSampler.java:21-24`). Negative samples floored at 0. ConstantSampler used for milestones (duration 0). PERT requires P strictly > O or it falls through to the fallback band.

### 3.2 Triangular inverse-CDF sample (engine path)

```
leftFraction = (mode − min) / (max − min)
if u < leftFraction:  x = min + √(u·(max−min)·(mode−min))
else:                 x = max − √((1−u)·(max−min)·(max−mode))
```

- **Inputs:** min/mode/max = O/M/P from `PertEstimate`, or fallback band (planned·(1−var), planned, planned·(1+var)); u = uniform draw (`correlatedU[iter][i]`).
- **Citation:** `application/simulation/TriangularSampler.java:21-31`
- **Notes:** Pure inverse-CDF so Iman-Conover rank permutation is preserved. Requires min ≤ mode ≤ max and min < max (throws otherwise).
- **Example:** O=8, M=10, P=16, u=0.5: range=8, leftFraction=(10−8)/8=0.25; u=0.5 ≥ 0.25 → x = 16 − √((1−0.5)·8·(16−10)) = 16 − √24 = 11.10 days.

### 3.3 Beta-PERT inverse-CDF sample (engine path)

```
α = 1 + 4·(mode−min)/(max−min)
β = 1 + 4·(max−mode)/(max−min)
x = min + (max−min)·BetaDist(α,β).inverseCumulativeProbability(u)
(λ=4 reproduces PERT mean (O+4M+P)/6)
```

- **Inputs:** min/mode/max = O/M/P; u clamped to [1e-12, 1−1e-12]; commons-math3 `BetaDistribution`.
- **Citation:** `application/simulation/BetaPertSampler.java:22-47`
- **Notes:** Selected when `defaultDistribution = BETA_PERT`. Uses inverse-CDF (not internal RG) so caller seeding / Iman-Conover ranks are respected.
- **Example:** O=8, M=10, P=16: α=1+4·2/8=2.0, β=1+4·6/8=4.0; PERT mean=(8+40+16)/6=10.67 days; u=0.5 → median of Beta(2,4) mapped onto [8,16].

### 3.4 Fallback distribution band (no PERT row)

```
min = max(0, planned·(1 − fractionalVariance))
max = planned·(1 + fractionalVariance)

NORMAL    → N(planned, planned·var/3)
LOGNORMAL → LogN(planned, planned·var/3)
TRIGEN    → Trigen(min, planned, max)
UNIFORM   → U(min, max)
else      → Triangular(min, planned, max)
```

- **Inputs:** planned = `Activity.originalDuration`; fractionalVariance = `MonteCarloInput.fallbackVariancePct`; defaultType = `MonteCarloInput.defaultDistribution`.
- **Citation:** `application/simulation/DistributionSamplers.java:48-61`
- **Notes:** stddev derived as planned·var/3 (so ±var ≈ 3σ). DISCRETE falls back to the TRIANGULAR band.
- **Example:** planned=20, var=0.2, TRIANGULAR: min=max(0,16)=16, max=24, mode=20 → symmetric `Triangular(16,20,24)`.

### 3.5 Risk-driver Bernoulli occurrence and impact injection

```
per iteration, per driver d:
   if iterRng.nextDouble() < d.probability → fires:
       scheduleDays = TriangularSampler(impactDays·0.8, impactDays, impactDays·1.2).sample
       costImpact   = TriangularSampler(costImpact·0.8,  costImpact,  costImpact·1.2 ).sample
       for each affected activity a:  sampled[a] += scheduleDays
       iterRiskCost += costImpact
```

- **Inputs:** `Risk.probability` mapped via `toProbability` (VERY_LOW=0.10, LOW=0.25, MEDIUM=0.50, HIGH=0.75, VERY_HIGH=0.90); `Risk.scheduleImpactDays`; `Risk.costImpact`; `Risk.affectedActivities` (codes or UUIDs); only non-CLOSED/non-RESOLVED risks with prob>0, ≥1 resolvable activity, and non-zero impact; gated by `MonteCarloInput.enableRisks`.
- **Citation:** `application/simulation/MonteCarloEngine.java:247-262, 506-543`
- **Notes:** Single-point impacts widened to ±20% triangular. `iterRng = masterRng.split()` per iteration (independent stream). Risk cost added to iterCost after activity costs (line 361-363).
- **Example:** Risk HIGH (p=0.75), impactDays=10, costImpact=50000: nextDouble=0.4<0.75 fires; scheduleDays from Triangular(8,10,12); costImpact from Triangular(40000,50000,60000); +scheduleDays added to each affected activity duration.

### 3.6 Per-iteration project duration (CPM over sampled durations)

```
run CPMScheduler over SchedulableActivities(dur = sampled[a]) + relationships
projectFinish    = max over ScheduledActivity.earlyFinish
projectDuration  = countWorkingDays(defaultCalendar, projectStartDate, projectFinish)
```

- **Inputs:** sampled durations (with risk add-ons); `schedulableRelationships` (predecessor/successor/type/lag); `projectStartDate` (`Baseline.projectStartDate` or min `Activity.plannedStartDate`); `dataDate` (`Baseline.baselineDate`); CPMScheduler with `CachingCalendarCalculator`; `SchedulingOption.RETAINED_LOGIC`.
- **Citation:** `application/simulation/MonteCarloEngine.java:264-310`
- **Notes:** `countWorkingDays` is half-open [start,end) via prefix-sum: prefix[b]−prefix[a] (`CachingCalendarCalculator.java:128-137`). `projectFinish` falls back to `projectStartDate` if no early finishes. Stored to `iterDurations[iter]` and persisted as `MonteCarloResult.projectDuration` (iterationNumber=iter+1).
- **Example:** If latest earlyFinish = projectStart + 240 calendar days spanning weekends, with a 5-day week → countWorkingDays ≈ 172 working days = projectDuration[iter].

### 3.7 Per-iteration project cost (duration-driven proportional model)

```
iterCost = Σ_i activityCost_i + iterRiskCost
activityCost_i = baselineCost_i · (sampled_dur_i / planned_dur_i)   if planned_dur_i > 0
               = baselineCost_i                                     if planned_dur_i = 0 (and baselineCost_i > 0)
```

- **Inputs:** `baselineCost_i` = `BaselineActivity.plannedCost`, or proportional allocation of `Baseline.totalCost` by `originalDuration` weight for activities lacking plannedCost; `sampled_dur_i`; `planned_dur_i` = `Activity.originalDuration`; `iterRiskCost` from fired risk drivers.
- **Citation:** `application/simulation/MonteCarloEngine.java:316-364, 626-660`
- **Notes:** BigDecimal accumulation. Allocation fallback: w_a = originalDuration_a / Σ(originalDuration over uncosted), cost_a = (Baseline.totalCost − explicitTotal)·w_a, scale 4 HALF_UP. Persisted as `MonteCarloResult.projectCost`.
- **Example:** baselineCost=1,000,000, planned=20, sampled=24 → activityCost = 1,000,000·(24/20) = 1,200,000; if a HIGH risk fired adding 50,000 → iterCost includes +50,000.

### 3.8 Monthly cashflow bucket accrual (linear-in-time)

```
for each activity with cost > 0 and scheduled ES/EF:
   span = max(1, finishEpoch − startEpoch)
   for each bucket b with end epoch bEnd:
      frac = 0                              if bEnd ≤ startE
           = 1                              if bEnd ≥ finishE
           = (bEnd − startE) / span         otherwise
      bucketRow[b] += activityCost · frac   (cumulative-to-date contribution)
```

- **Inputs:** `earlyStartEpoch`/`earlyFinishEpoch` (from `ScheduledActivity.getEarlyStart/EarlyFinish` toEpochDay); `activityCost_i`; bucketEnds = month-end dates from projectStart to projectStart+horizonDays; `iterBucketCost[iter][b]`.
- **Citation:** `application/simulation/MonteCarloEngine.java:203-216, 346-359`
- **Notes:** Epoch span in calendar days (not working days). Bucket value is **cumulative** (S-curve), not incremental — frac reaches 1.0 once bucket end passes activity finish. bucketEnds via `TemporalAdjusters.lastDayOfMonth` stepping monthly.
- **Example:** activityCost=1,200,000, startEpoch day0, finishEpoch day60, span=60; bucket end at day30 → frac=30/60=0.5 → contributes 600,000 to that month's cumulative.

### 3.9 Percentile extraction (double series — duration & per-activity)

```
percentile(sorted, pct) = sorted[ idx ]
idx = clamp( round( (pct/100)·(length − 1) ), 0, length − 1 )
```

- **Inputs:** sorted = ascending-sorted `iterDurations` or per-activity duration series; pct ∈ {10,25,50,75,80,90,95,99}; length = N (iterations).
- **Citation:** `application/simulation/MonteCarloEngine.java:747-751, 758-768`
- **Notes:** Uses an **(N−1) basis with `Math.round`** (nearest-rank on 0..N−1 index), **not** ceil(p·N). `PercentileSnapshot` bundles P10/P25/P50/P75/P80/P90/P95/P99 + mean + stddev. Persisted to `MonteCarloSimulation`: `p10Duration`, `confidenceP50Duration`, `confidenceP80Duration`, etc.
- **Example:** N=10000, P80: idx=round(0.80·9999)=round(7999.2)=7999 → sortedDur[7999]. N=10000, P50: idx=round(0.50·9999)=round(4999.5)=5000 → sortedDur[5000].

### 3.10 Cost percentile extraction (BigDecimal series)

```
costPercentile(sorted, pct) = sorted[ clamp( round( (pct/100)·(length − 1) ), 0, length − 1 ) ]
```

- **Inputs:** sorted = ascending-sorted `iterCosts` (BigDecimal::compareTo); pct ∈ {10,25,50,75,80,90,95,99}.
- **Citation:** `application/simulation/MonteCarloEngine.java:797-801, 777-795`
- **Notes:** Same nearest-rank index as duration. Empty array → all zeros. Persisted as `p10Cost`, `confidenceP50Cost`, `confidenceP80Cost`, etc.
- **Example:** N=10000 cost array, P90: idx=round(0.90·9999)=round(8999.1)=8999 → sortedCost[8999].

### 3.11 Mean (duration/cost aggregate snapshot)

```
mean = (Σ x_i) / N
```

- **Inputs:** x = `iterDurations` (Arrays.stream.average) or `iterCosts` (BigDecimal sum / N, scale 4 HALF_UP); N = iterations.
- **Citation:** `application/simulation/MonteCarloEngine.java:759` (duration), `782-784` (cost), `734-738` (per-activity helper)
- **Notes:** Cost mean uses `BigDecimal.divide` scale 4 RoundingMode.HALF_UP. Persisted as `meanDuration` / `meanCost`. Per-activity duration mean uses the same arithmetic mean helper.
- **Example:** durations {200,210,220,230,240}, N=5 → mean = 1100/5 = 220 days.

### 3.12 Standard deviation (sample, n−1 denominator)

```
stddev = √( Σ(x_i − mean)² / (N − 1) );   returns 0 if N < 2
```

- **Inputs:** x = `iterDurations` / `iterCosts`(.doubleValue) / per-activity duration series; mean from above.
- **Citation:** `application/simulation/MonteCarloEngine.java:740-745` (helper), `760-762` (duration), `785-789` (cost)
- **Notes:** Sample stddev (divides by N−1, Bessel's correction). Cost stddev computed in double then `BigDecimal.valueOf(...).setScale(4, HALF_UP)`. Persisted as `stddevDuration` / `stddevCost`.
- **Example:** durations {200,210,220,230,240}, mean=220: Σd²=400+100+0+100+400=1000; var=1000/(5−1)=250; stddev=√250=15.81 days.

### 3.13 Criticality index (per activity)

```
criticalityIndex_i = criticalHits[i] / N
   (criticalHits[i] incremented each iteration ScheduledActivity.isCritical() is true)
```

- **Inputs:** `ScheduledActivity.isCritical()` per iteration (from CPM total-float=0 logic); N = iterations.
- **Citation:** `application/simulation/MonteCarloEngine.java:326, 399, 401-413`
- **Notes:** Stored as `MonteCarloActivityStat.criticalityIndex` (fraction 0..1). Activity stats ordered by `criticalityIndex` DESC when fetched.
- **Example:** activity critical in 8700 of 10000 iterations → criticalityIndex = 0.87 (on the critical path 87% of the time).

### 3.14 Duration sensitivity (Pearson) and cost sensitivity

```
durationSensitivity_i = PearsonsCorrelation.correlation(activityDurations[i][·], iterDurations[·])
                        if sd_i > 1e-9 else 0
costSensitivity_i     = durationSensitivity_i        (identical under duration-driven cost model)
```

- **Inputs:** `activityDurations[i]` = that activity's sampled duration across all N iterations; `iterDurations` = project duration across all N iterations; sd_i = per-activity duration stddev.
- **Citation:** `application/simulation/MonteCarloEngine.java:393-411`
- **Notes:** Pearson r ∈ [−1,1] correlating activity duration vs project duration (schedule sensitivity / tornado). Wrapped in try/catch → 0 on exception. `costSensitivity` deliberately set equal to `durationSensitivity` (Phase 1).
- **Example:** activity with high variance driving project finish → r≈0.85; a near-constant or off-critical activity → r≈0.05.

### 3.15 Cruciality (per activity)

```
cruciality_i = criticalityIndex_i · |durationSensitivity_i|
```

- **Inputs:** `criticalityIndex_i`; `durationSensitivity_i` (Pearson r).
- **Citation:** `application/simulation/MonteCarloEngine.java:412`
- **Notes:** Combined ranking metric = how often critical × how strongly it moves the project. Stored as `MonteCarloActivityStat.cruciality`.
- **Example:** criticality=0.87, sensitivity=0.85 → cruciality = 0.87·0.85 = 0.7395.

### 3.16 Per-activity duration percentiles P10/P90

```
durationP10 = percentile(sortedSeries, 10)
durationP90 = percentile(sortedSeries, 90)
sortedSeries = sorted activityDurations[i]
```

- **Inputs:** `activityDurations[i]` across N iterations (sampled durations including risk add-ons).
- **Citation:** `application/simulation/MonteCarloEngine.java:389-392, 408-409`
- **Notes:** Same round(p·(N−1)) nearest-rank index. Stored as `MonteCarloActivityStat.durationP10/durationP90`.
- **Example:** activity durations sorted, N=10000: P90 idx=round(0.90·9999)=8999 → sortedSeries[8999].

### 3.17 Milestone finish-date percentiles (P50/P80/P90)

```
sort the N finish epochs
p50 = LocalDate.ofEpochDay(finishes[min(N−1, round(0.5·(N−1)))])
p80 =               finishes[min(N−1, round(0.8·(N−1)))]
p90 =               finishes[min(N−1, round(0.9·(N−1)))]
```

- **Inputs:** `milestoneFinishEpoch[m]` = earlyFinishEpoch of each FINISH_MILESTONE/START_MILESTONE activity per iteration; N = iterations.
- **Citation:** `application/simulation/MonteCarloEngine.java:193-201, 366-370, 416-438`
- **Notes:** Milestones identified by `ActivityType.name()` == FINISH_MILESTONE or START_MILESTONE. Epoch stored as int day-of-epoch. Persisted as `MonteCarloMilestoneStat.p50FinishDate/p80FinishDate/p90FinishDate` plus `plannedFinishDate` (`Activity.plannedFinishDate`).
- **Example:** N=10000 milestone finish epochs sorted, P80: idx=round(0.8·9999)=7999 → LocalDate.ofEpochDay(finishes[7999]) e.g. 2027-03-31.

### 3.18 Milestone finish-date CDF (20-point)

```
20 points: for i = 0..19:
   idx   = min(N−1, round( (i/19)·(N−1) ))
   point = { date: LocalDate.ofEpochDay(sortedEpochs[idx]),  p: i/19 (4dp) }
```

- **Inputs:** sorted milestone finish epochs; samples = 20.
- **Citation:** `application/simulation/MonteCarloEngine.java:590-603, 426`
- **Notes:** JSON array string stored in `MonteCarloMilestoneStat.cdfJson`. p ranges 0.0000 (earliest) to 1.0000 (latest). Empty series → `"[]"`.
- **Example:** i=10: p=10/19=0.5263, idx=round(0.5263·9999)=5263 → `{"date":"2027-01-31","p":0.5263}`.

### 3.19 Cashflow bucket percentiles P10/P50/P80/P90 (cumulative)

```
for each bucket b:
   collect iterBucketCost[it][b] over all N iterations into bucketCol, sort ascending
   p10 = bucketCol[round(0.10·(N−1))]
   p50 = bucketCol[round(0.50·(N−1))]
   p80 = bucketCol[round(0.80·(N−1))]
   p90 = bucketCol[round(0.90·(N−1))]
   each setScale(2, HALF_UP)
```

- **Inputs:** `iterBucketCost[iter][b]` = cumulative cost accrued through bucket end per iteration; N = iterations.
- **Citation:** `application/simulation/MonteCarloEngine.java:440-457`
- **Notes:** Uses `round(p·(N−1))` directly (no min clamp, but p ≤ 0.90 so safe). Stored as `MonteCarloCashflowBucket.p10Cumulative/p50Cumulative/p80Cumulative/p90Cumulative` + `periodEndDate`. The `baselineCumulative` field exists but is not populated by the engine.
- **Example:** bucket month-3 cumulative costs sorted, N=10000, P80: idx=round(0.80·9999)=7999 → bucketCol[7999] e.g. 3,600,000.00.

### 3.20 Risk contribution occurrence rate and mean impacts

```
occurrenceRate    = occurrences / max(1, N)
meanDurationImpact = occ > 0 ? riskTotalDuration[d] / occ : 0
meanCostImpact     = occ > 0 ? riskTotalCost[d] / occ  (scale 2 HALF_UP) : 0
```

- **Inputs:** `riskOccurrences[d]` (Bernoulli fire count); `riskTotalDuration[d]` (Σ sampled scheduleDays when fired); `riskTotalCost[d]` (Σ sampled costImpact when fired); N.
- **Citation:** `application/simulation/MonteCarloEngine.java:258-262, 459-479`
- **Notes:** One row per eligible driver even if it never fired (occ=0 → rates/means 0). Stored as `MonteCarloRiskContribution`; fetched ordered by `occurrenceRate` DESC. `occurrenceRate` empirically approximates the mapped probability.
- **Example:** HIGH risk (p=0.75), fired 7480/10000 → occurrenceRate=0.748; if riskTotalDuration=74800 → meanDurationImpact=10.0 days.

### 3.21 Deterministic baseline duration (CPM, no sampling)

```
run CPMScheduler once with each activity dur = Activity.originalDuration (0 for milestones)
deterministicBaselineDuration =
   countWorkingDays(defaultCalendar, projectStartDate, max(ScheduledActivity.earlyFinish))
```

- **Inputs:** `Activity.originalDuration`; `schedulableRelationships`; `projectStartDate`; `dataDate`; `CachingCalendarCalculator`.
- **Citation:** `application/simulation/MonteCarloEngine.java:173-178, 695-722`
- **Notes:** Overrides `Baseline.projectDuration` (which is often a sum) with a CPM-derived value for apples-to-apples comparison vs simulated percentiles. Persisted as `MonteCarloSimulation.baselineDuration`. **Contingency = P80 − deterministic is NOT computed here** — both values are stored separately (`confidenceP80Duration` and `baselineDuration`) and the difference is left to the consumer/frontend.
- **Example:** deterministic CPM finish = projectStart + working-day count 240; P80 sampled = 268 → contingency (if computed downstream) = 268 − 240 = 28 days.

### 3.22 Baseline cost (deterministic) and proportional allocation

```
baselineCost = Baseline.totalCost (or 0)

per-activity baseline cost:
   = BaselineActivity.plannedCost                                   if > 0
   = (Baseline.totalCost − Σ explicit plannedCosts)
        · (originalDuration_a / Σ originalDuration over uncosted)   otherwise   (scale 4 HALF_UP)
```

- **Inputs:** `Baseline.totalCost`; `BaselineActivity.plannedCost` per activity; `Activity.originalDuration` weights.
- **Citation:** `application/simulation/MonteCarloEngine.java:488, 626-660`
- **Notes:** If no activity has explicit cost, the full total is distributed by duration weight. Persisted as `MonteCarloSimulation.baselineCost`. Used as `baselineCost_i` in the per-iteration cost formula.
- **Example:** totalCost=10,000,000, two uncosted activities durations 20 and 30 (Σ=50) → cost_a1 = 10M·(20/50)=4,000,000.0000; cost_a2 = 6,000,000.0000.

### 3.23 RNG seeding and reproducibility

```
seed       = input.randomSeed != null ? randomSeed : System.nanoTime()
masterRng  = new SplittableRandom(seed)
iterRng    = masterRng.split()           (per iteration: risk Bernoulli + impact draws)
correlatedU built with seed = masterRng.nextLong()
```

- **Inputs:** `MonteCarloInput.randomSeed` (optional Long); `System.nanoTime()` fallback.
- **Citation:** `application/simulation/MonteCarloEngine.java:218-224, 234-235`
- **Notes:** Same randomSeed + same project data + same iteration count → identical results. `masterRng.nextLong()` for ImanConover is drawn **before** the per-iteration split loop, so the correlation matrix and iteration streams are deterministic given the seed. Config persisted to `MonteCarloSimulation.configJson` including seed.
- **Example:** randomSeed=42 → SplittableRandom(42); two runs with seed=42 produce byte-identical percentiles. seed=null → nanoTime → non-reproducible.

### 3.24 Iteration count (N) bounds and default

```
N = request.iterations (default 10000 in service)
validated:  100 ≤ N ≤ 100000
fallbackVariancePct validated 0 ≤ var ≤ 0.9
defaultDistribution defaults to TRIANGULAR
```

- **Inputs:** `MonteCarloRunRequest.iterations` / `defaultDistribution` / `fallbackVariancePct` / `enableRisks` / `randomSeed`.
- **Citation:** `application/simulation/MonteCarloInput.java:28-40`; `MonteCarloService.java:53-78`
- **Notes:** Out-of-range N or var throws IllegalArgumentException in the `MonteCarloInput` compact constructor. `iterationsCompleted` set to N on success. Each iteration persists one `MonteCarloResult` row (iterationNumber = i+1).
- **Example:** iterations=10000 → 10000 `MonteCarloResult` rows, iterationNumber 1..10000; iterations=50 → throws (below min 100).

### 3.25 Calendar working-day count (O(1) prefix sum) and horizon sizing

```
countWorkingDays(start, end) = prefix[idx(end)] − prefix[idx(start)]   for half-open [start,end), 0 if end ≤ start
prefix[i+1] = prefix[i] + (isWorkingDay(origin + i) ? 1 : 0)
horizonDays = max(365, baselineProjectDuration · 5)
```

- **Inputs:** `delegate.isWorkingDay` per calendar/day (cached into BitSet); origin = `projectStartDate`; `baselineProjectDuration` = `Baseline.projectDuration` or Σ originalDuration.
- **Citation:** `application/simulation/CachingCalendarCalculator.java:44-59, 128-137`; `MonteCarloEngine.java:147-161`
- **Notes:** Bitmap pre-materialised once per distinct calendar to avoid millions of DB round-trips across N iterations. Out-of-horizon dates fall back to the delegate. The 5× planned horizon gives generous worst-case slack.
- **Example:** baselineProjectDuration=240 → horizonDays=max(365,1200)=1200; countWorkingDays over a 60-calendar-day span with a 5-day week ≈ prefix difference of ~43 working days.

---

## 4. Correlation, Sensitivity & Risk Quality

This sub-area covers (1) the Iman-Conover rank-correlation induction that makes Monte-Carlo activity-duration draws correlated while preserving each marginal distribution, driven by user-entered pairwise `ActivityCorrelation` coefficients; (2) the post-simulation sensitivity/criticality/cruciality stats and per-risk contribution aggregates; and (3) the read-side `RiskQualityService` scoring how completely each Risk has been analysed. Data flow: `ActivityCorrelation` rows → target matrix R → Cholesky(R) + van-der-Waerden scores → ImanConover produces an N×V correlated-uniform matrix → each sampler consumes its column uniform via inverse-CDF → CPM per iteration → Pearson correlation of each activity duration vs project duration gives sensitivity; critical-hit fraction gives criticality; their product gives cruciality. `RiskQualityService` is independent (no simulation).

### 4.1 Pairwise activity correlation coefficient (stored target)

```
coefficient = ρ ∈ (−1, 1),  validated −0.99 ≤ coefficient ≤ 0.99
stored once per canonical pair (min(activityAId,activityBId), max(...))
at simulation time:  coef = max(−0.99, min(0.99, coefficient))
used symmetrically:  R[i][j] = R[j][i] = coef,  diagonal R[i][i] = 1.0
```

- **Inputs:** `ActivityCorrelation.coefficient` (user-entered via `ActivityCorrelationDto`, `@DecimalMin -0.99` / `@DecimalMax 0.99`). Pair canonicalised in `ActivityCorrelationService.upsert` by UUID compareTo. Matrix assembled in `MonteCarloEngine.buildCorrelatedUniforms`.
- **Citation:** `domain/model/ActivityCorrelation.java:44`; clamp+matrix at `application/simulation/MonteCarloEngine.java:684`; validation at `application/dto/ActivityCorrelationDto.java:24`
- **Notes:** Conceptually a **rank** (Spearman-style) correlation (Pertmaster "Duration Correlation"), induced by Iman-Conover, not a Pearson correlation of raw sampled values. There is **no** computed Pearson/Spearman coefficient from data in this path — the coefficient is an **input**. `coefficient` is NOT NULL; self-correlation (a==b) is rejected with `BusinessRuleException SELF_CORRELATION`. (a,b) and (b,a) collapse to one row.
- **Example:** User enters ρ=0.6 between activity A and B. Stored coefficient=0.6 on the canonical pair. At sim time R becomes [[1,0.6],[0.6,1]].

### 4.2 Target correlation matrix R (assembly)

```
R is V×V (V = number of activities)
Initialise R[i][i] = 1.0; R[i][j] = 0 otherwise
For each ActivityCorrelation row with resolvable indices i != j:
   R[i][j] = R[j][i] = clamp(coefficient, −0.99, 0.99)
Activities never mentioned keep an all-zero off-diagonal row (independent)
```

- **Inputs:** `orderedIds` (activity index map), `ActivityCorrelationRepository.findByProjectId(projectId)`, `ActivityCorrelation.coefficient`.
- **Citation:** `application/simulation/MonteCarloEngine.java:669-693`
- **Notes:** Rows whose `activityAId`/`activityBId` are not in the current activity set are skipped silently (i==null||j==null). Logs `'Activity correlations applied: {} of {}'`. If the correlations list is empty, R is pure identity and ImanConover short-circuits to independent uniforms.
- **Example:** 3 activities, one correlation (A,C)=0.4 → R = [[1,0,0.4],[0,1,0],[0.4,0,1]].

### 4.3 Identity short-circuit test

```
isIdentity(R) = true  iff
   ∀i: |R[i][i] − 1.0| ≤ 1e-9   AND   ∀ i<j: |R[i][j]| ≤ 1e-9
if true → return independent uniforms U[i][c] = rng.nextDouble() (no matrix work)
```

- **Inputs:** R (target matrix).
- **Citation:** `application/simulation/ImanConover.java:42-46, 89-98`
- **Notes:** Tolerance 1e-9. This is why projects with no correlations pay zero Iman-Conover cost.
- **Example:** R=[[1,0],[0,1]] → isIdentity true → two independent U(0,1) columns.

### 4.4 van der Waerden scores (rank score vector)

```
for iteration index i ∈ [0, N−1]:
   p_i      = (i + 1) / (N + 1)
   scores[i] = Φ⁻¹(p_i)
(fixed, sorted vector of normal scores symmetric about 0)
```

- **Inputs:** iterations N; `staticInverseNormal` (Acklam) from `DistributionSampler.SingleShotRng`.
- **Citation:** `application/simulation/ImanConover.java:48-53`
- **Notes:** These scores build the Iman-Conover score matrix S; each variable column is an independent random permutation of this same vector (Fisher-Yates with SplittableRandom).
- **Example:** N=4: p = 0.2,0.4,0.6,0.8 → scores = Φ⁻¹(0.2),Φ⁻¹(0.4),Φ⁻¹(0.6),Φ⁻¹(0.8) = −0.8416, −0.2533, 0.2533, 0.8416.

### 4.5 Acklam inverse standard-normal CDF (Φ⁻¹) — correlation path

```
pLow = 0.02425, pHigh = 1 − pLow
Lower tail (p<pLow):   q=√(−2 ln p);   x = (((((c1·q+c2)q+c3)q+c4)q+c5)q+c6)/((((d1·q+d2)q+d3)q+d4)q+1)
Central (pLow≤p≤pHigh): q=p−0.5, r=q²;  x = (((((a1·r+a2)r+a3)r+a4)r+a5)r+a6)·q/(((((b1·r+b2)r+b3)r+b4)r+b5)r+1)
Upper tail (p>pHigh):  q=√(−2 ln(1−p)); x = −(lower-tail polynomial in q)
```

- **Inputs:** p ∈ (0,1). Coefficients a1..a6, b1..b5, c1..c6, d1..d4 are the standard Acklam constants.
- **Citation:** `application/simulation/DistributionSampler.java:41-69`
- **Notes:** Accuracy < 1e-9. Used by van-der-Waerden scoring and by Normal/Lognormal samplers' nextGaussian fallback. p is **not** clamped here — callers (`sampleFromUniform`) clamp to [1e-12, 1−1e-12].
- **Example:** p=0.975 → central branch → x ≈ 1.95996 (true z_{0.975}=1.95996).

### 4.6 Iman-Conover score matrix S and correlated scores Y

```
S is N×V; column c = a random permutation of the van-der-Waerden scores vector
L = Cholesky lower-triangular factor of R   (R = L·Lᵀ)
Y = S · Lᵀ    (N×V; Y's columns carry the target correlation structure)
```

- **Inputs:** scores vector, R, Cholesky decomposition (commons-math3 `CholeskyDecomposition`).
- **Citation:** `application/simulation/ImanConover.java:55-69`
- **Notes:** Standard Iman-Conover uses Y = S·Lᵀ (columns of S being unit-variance-ish normal scores; multiplying by Lᵀ imposes correlation R). Marginals of the final uniforms are preserved exactly because only the **rank** ordering of Y is used (next formula).
- **Example:** V=2, R=[[1,0.6],[0.6,1]], L=[[1,0],[0.6,0.8]], Lᵀ=[[1,0.6],[0,0.8]]. A score row (s1,s2) maps to (s1, 0.6·s1 + 0.8·s2).

### 4.7 Cholesky with PSD regularisation (nearest-PSD fallback)

```
try Cholesky(R) with thresholds (1e-10, 1e-10)
on NonPositiveDefiniteMatrixException, for attempt = 0..7:
   ε = 10^(−6 + attempt);  R[i][i] += ε for all i;  retry
if still failing after 8 attempts → return identity I (no correlation)
```

- **Inputs:** R target matrix.
- **Citation:** `application/simulation/ImanConover.java:106-123`
- **Notes:** Handles user-entered correlations that violate the triangle inequality / are not positive-semidefinite. ε grows 1e-6, 1e-5, …, 1e1. The final identity fallback degrades gracefully to independent sampling rather than failing the whole simulation.
- **Example:** R=[[1,0.9,−0.9],[0.9,1,0.9],[−0.9,0.9,1]] is not PSD → add 1e-6 to diagonal, retry; if needed escalate to 1e-5, etc.

### 4.8 Rank-reordering of uniforms (marginal-preserving induction)

```
for each column c:
   draw N independent uniforms indU, sort ascending
   idx = argsort of Y[:,c] (ascending)
   for rank r:  U[ idx[r] ][c] = indU[r]
(column c stays uniform on [0,1], but its rank ordering matches Y[:,c],
 so its rank-correlation with other columns ≈ R)
```

- **Inputs:** Y (correlated scores), `rng.nextDouble()` uniforms.
- **Citation:** `application/simulation/ImanConover.java:71-86`
- **Notes:** The key "preserve marginals, induce correlation" step. The per-activity distribution (triangular/beta-pert) stays valid because the activity sampler later consumes `U[iter][ai]` through its inverse CDF. Output U is consumed at `MonteCarloEngine.java:240-241` as `sampled = sampler.sampleFromUniform(u)`.
- **Example:** N=4, column Y values ordered so iteration order by Y is [iter2,iter0,iter3,iter1]; indU sorted = [0.1,0.3,0.6,0.9] → U[2]=0.1, U[0]=0.3, U[3]=0.6, U[1]=0.9.

### 4.9 Duration sensitivity (tornado) = Pearson(activity duration, project duration)

```
durationSensitivity_i = Pearson( activityDurations[i][·], iterDurations[·] )
                        if stddev(activityDurations[i]) > 1e-9, else 0
Pearson r = cov(X,Y)/(sd(X)·sd(Y))
          = Σ((X_k−X̄)(Y_k−Ȳ)) / √( Σ(X_k−X̄)² · Σ(Y_k−Ȳ)² )
```

- **Inputs:** `activityDurations[i]` (n sampled durations for activity i), `iterDurations` (project duration per iteration). commons-math3 `PearsonsCorrelation`.
- **Citation:** `application/simulation/MonteCarloEngine.java:383-398, 409-410`
- **Notes:** Guarded by sd > 1e-9 to avoid NaN for constant activities (milestones); any exception → 0.0. `costSensitivity` is set **equal** to `durationSensitivity` (line 411) because Phase-1 cost is purely duration-driven. This is the tornado/sensitivity ranking metric.
- **Example:** if activity i's longer durations consistently push project duration up, r ≈ 0.85. A milestone with constant 0 duration → sd=0 → sensitivity 0.

### 4.10 Criticality index (correlation-path restatement)

```
criticalityIndex_i = criticalHits[i] / n
   (criticalHits[i] = # iterations in which scheduled activity i was on the critical path, sa.isCritical())
```

- **Inputs:** `criticalHits[i]` (incremented at `MonteCarloEngine.java:326` when `sa.isCritical()`); n = `input.iterations()`.
- **Citation:** `application/simulation/MonteCarloEngine.java:399, 326`
- **Notes:** Fraction in [0,1]. CPM per iteration flags critical activities; the standard Monte-Carlo criticality index.
- **Example:** n=1000, activity critical in 720 → criticalityIndex = 0.72.

### 4.11 Cruciality index (correlation-path restatement)

```
cruciality_i = criticalityIndex_i · |durationSensitivity_i|
             = (criticalHits[i]/n) · |Pearson(activityDurations[i], iterDurations)|
```

- **Inputs:** criticality and durSens computed above.
- **Citation:** `application/simulation/MonteCarloEngine.java:412`
- **Notes:** Combines "how often critical" with "how strongly its duration moves the project" — a single ranking of activities that both matter and vary. Range [0,1].
- **Example:** criticality 0.72, durSens 0.85 → cruciality = 0.72·0.85 = 0.612.

### 4.12 Risk occurrence rate (risk contribution)

```
occurrenceRate = occurrences / max(1, n)
   occurrences = riskOccurrences[d] = # iterations where iterRng.nextDouble() < drv.probability() (Bernoulli fire)
```

- **Inputs:** `riskOccurrences[d]` accumulated per iteration (`MonteCarloEngine.java:259`); n = iterations; `drv.probability()` from RiskProbability mapping.
- **Citation:** `application/simulation/MonteCarloEngine.java:463-464, 250, 259`; field at `domain/model/MonteCarloRiskContribution.java:38`
- **Notes:** `max(1,n)` guards divide-by-zero. Probability from enum: VERY_LOW 0.10, LOW 0.25, MEDIUM 0.50, HIGH 0.75, VERY_HIGH 0.90 (`MonteCarloEngine.toProbability` line 534-543). Risks with status CLOSED/RESOLVED, probability ≤ 0, no resolvable affected activity, or zero schedule **and** cost impact are excluded as drivers.
- **Example:** probability MEDIUM=0.50, n=1000, fired 503 times → occurrenceRate = 0.503.

### 4.13 Mean schedule (duration) impact per risk

```
meanDurationImpact = occurrences > 0 ? riskTotalDuration[d] / occurrences : 0.0
each firing adds scheduleDays = TriangularSampler(impactDays·0.8, impactDays, impactDays·1.2).sample(rng)
   and riskTotalDuration[d] accumulates those draws;
   sampled scheduleDays is also added to every affected activity's sampled duration that iteration
```

- **Inputs:** `Risk.scheduleImpactDays` (impactDays), TriangularSampler ±20% band, `riskTotalDuration[d]`.
- **Citation:** `application/simulation/MonteCarloEngine.java:465, 251, 255, 260, 521-523`
- **Notes:** Mean is over iterations **where the risk occurred** (denominator = occurrences, not n). Triangular range is the single-point impact ±20% (min=0.8×, mode=1×, max=1.2×). If impactDays==0 the schedule sampler is ConstantSampler(0).
- **Example:** impactDays=10 → Triangular(8,10,12), mean of draws over 503 occurrences ≈ 10.0 days → meanDurationImpact ≈ 10.0.

### 4.14 Mean cost impact per risk

```
meanCostImpact = occurrences > 0 ? round(riskTotalCost[d] / occurrences, 2, HALF_UP) : 0
each firing adds costImpact = TriangularSampler(impactCost·0.8, impactCost, impactCost·1.2).sample(rng)
   (risk cost also added to iteration total cost, iterRiskCost)
```

- **Inputs:** `Risk.costImpact` (impactCost BigDecimal), TriangularSampler ±20%, `riskTotalCost[d]`.
- **Citation:** `application/simulation/MonteCarloEngine.java:466-468, 252, 258, 261, 524-526`; field at `domain/model/MonteCarloRiskContribution.java:44`
- **Notes:** BigDecimal scale 2, HALF_UP. Denominator = occurrences (only iterations where it fired). Stored in project currency (currency-neutral raw number). impactCost.signum() ≤ 0 → ConstantSampler(0).
- **Example:** impactCost=100000 → Triangular(80000,100000,120000), mean over occurrences ≈ 100000.00 → meanCostImpact = 100000.00.

### 4.15 Risk-quality analysis score (coverage / completeness)

```
score = Σ over 4 boolean criteria of (criterion ? 1 : 0)
   hasOwner       = (ownerId != null)
   hasRating      = (probability != null AND (impactCost != null OR impactSchedule != null))
   hasDescription = (description != null AND trim().length() >= 50)
   hasResponse    = ∃ response r with r.responseType != null AND r.responsibleId != null

level:  score >= 4 → WELL_ANALYSED
        score >= 2 → PARTIALLY_ANALYSED
        else       → NOT_ANALYSED
```

- **Inputs:** `Risk.ownerId`, `Risk.probability`, `Risk.impactCost`, `Risk.impactSchedule`, `Risk.description`, list of `RiskResponse` (responseType, responsibleId). `DESCRIPTION_MIN_LENGTH = 50`.
- **Citation:** `application/service/RiskQualityService.java:33-57`; level thresholds at `application/dto/RiskAnalysisQuality.java:26-30`
- **Notes:** Pure read-side derivation, recomputed on every read, no persistence. Each criterion contributes exactly 1 point (equal weight). "Freshness" is **not** a component — only the four coverage/response-completeness/description criteria exist. `hasRating` requires probability **plus** at least one impact; description must be ≥ 50 chars **after** trim.
- **Example:** Risk with owner set, probability MEDIUM + cost impact set, description 80 chars, but no usable response → hasOwner=1, hasRating=1, hasDescription=1, hasResponse=0 → score=3 → PARTIALLY_ANALYSED.

### 4.16 RiskTrigger evaluation (threshold breach)

```
triggered = currentValue != null AND currentValue >= thresholdValue
rising edge (triggered && !isTriggered):  isTriggered=true,  triggeredAt=now
falling edge:                              isTriggered=false, triggeredAt=null
(Placeholder: currentValue is set equal to thresholdValue, so always triggers)
```

- **Inputs:** `RiskTrigger.currentValue`, `RiskTrigger.thresholdValue`, `RiskTrigger.isTriggered`, `RiskTrigger.triggerType`.
- **Citation:** `application/service/RiskTriggerService.java:111-134`
- **Notes:** Explicitly a placeholder — production would compare actual schedule/cost metrics by `triggerType` (SCHEDULE_DELAY, COST_OVERRUN, MILESTONE_MISSED). Currently `currentValue := thresholdValue` so the `>=` comparison is always true. No statistical formula; a simple threshold compare.
- **Example:** thresholdValue=10, currentValue set to 10 → 10 ≥ 10 → triggered=true.

---

## Risk conventions

- **RNG seeding.** A single `SplittableRandom(seed)` (`masterRng`) drives the whole run; `seed = input.randomSeed ?? System.nanoTime()`. The Iman-Conover correlation matrix is built from `masterRng.nextLong()` **before** the iteration loop, and each iteration gets an independent `masterRng.split()` stream for risk Bernoulli/impact draws. Same `randomSeed` + same data + same N ⇒ byte-identical percentiles; a null seed (nanoTime) is non-reproducible.
- **Iteration count.** Default N = 10000; validated 100 ≤ N ≤ 100000. `fallbackVariancePct` validated 0 ≤ var ≤ 0.9; default distribution TRIANGULAR. One `MonteCarloResult` row persisted per iteration (iterationNumber 1..N).
- **Percentile convention.** Nearest-rank on a zero-based (N−1) basis: `idx = clamp(round((pct/100)·(N−1)), 0, N−1)` — **not** `ceil(p·N)`. Applies uniformly to duration, cost, per-activity, milestone, and cashflow series. Standard percentile set: P10/P25/P50/P75/P80/P90/P95/P99. Sample stddev uses the n−1 (Bessel) denominator.
- **Currency neutrality.** All monetary outputs (exposure/EMV cost, project cost, risk cost impacts, baseline cost) are raw currency-neutral numbers — no FX conversion anywhere in `bipros-risk`. They are relabelled in the project's own currency by the frontend (see the platform currency convention); BigDecimal rounding is HALF_UP (scale 2 for exposure/risk-cost/cashflow, scale 4 for cost allocation/cost-mean).
- **Recompute triggers.** Qualitative scores + RAG recompute on every `createRisk`/`updateRisk` via `calculateScores` (pre-response sets RAG; post-response does not). Cost exposure recomputes on `addActivityToRisk`/`removeActivityFromRisk` and `recalculateForActivity` (Activity/ActivityExpense changes). Risk-quality and RiskTrigger states are derived on read (no persistence). The Monte Carlo simulation is an explicit on-demand run (`MonteCarloService`), not auto-triggered.
- **Notable absences (do not invent).** No schedule EMV (probability × days); no category-level weighted-score rollup; contingency (P80 − deterministic) is left to the consumer/frontend; `residualRiskScore` column is legacy/unwritten; correlation coefficients are user **inputs**, never computed from data; `RiskTrigger` and `costSensitivity` (= durationSensitivity) are Phase-1 placeholders.

---

# AI Insights — Formula Reference (BIPROS EPPM)

This reference documents every numeric formula in the BIPROS AI Insights collectors (`bipros-ai`) and the reporting capacity-insights collector (`bipros-reporting`). Each entry shows the math, inputs, and exact `file:line` citation; worked examples are included where the source data provided one. Pass-through values (computed upstream in `CostService`/`EvmService`/`ContractService`/`RiskService` and forwarded unchanged) are marked as such.

---

## Family 1 — Cost / EVM / Contract / CostAccount

### Cost (`CostInsightsCollector`)

**Cost snapshot pass-through**
```
totalBudget, totalActual, totalRemaining, atCompletion, expenseCount,
costVariance, costPerformanceIndex, materialProcurementCost
  := CostSummaryDto fields (no in-collector math)
```
- Inputs: `costService.getCostSummary(projectId) → CostSummaryDto`; each field computed inside `CostService`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/cost/CostInsightsCollector.java:31-42`
- Notes: Pure pass-through; `costVariance` and CPI are NOT recomputed here. Placed in a `LinkedHashMap` snapshot, serialized via `objectMapper.valueToTree`. No rounding/null handling at collector level.

**Top-5 periods selection (cost)**
```
topPeriods := periods.stream().limit(5)  mapping {periodName, budget, actual, variance}
```
- Inputs: `costService.aggregateByPeriod(projectId) → List<PeriodCostAggregationDto>`; `p.variance()` precomputed upstream (budget − actual).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/cost/CostInsightsCollector.java:44-55`
- Notes: Top-N = first 5 in repo/service order (NOT sorted in collector). `variance` is pass-through. Same `limit(5)` reused for the bar chart at line 75. Fewer than 5 periods → all taken.

**CPI gauge value (cost chart)**
```
gaugeValue = (costPerformanceIndex != null) ? costPerformanceIndex.doubleValue() : 0.0
gauge min = 0, max = 2
```
- Inputs: `summary.costPerformanceIndex()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/cost/CostInsightsCollector.java:114-128`
- Notes: Null CPI coerced to 0.0. Fixed 0..2 scale. Subtitle "CPI > 1.0 is under budget" is descriptive only, not enforced.
- Example: CPI = 0.85 → needle at 0.85 of the 0..2 scale.

**Variance waterfall data (cost chart)**
```
bar1 = [totalBudget, 0, 0]
bar2 = [0, totalActual, 0]
bar3 = [0, 0, costVariance]
categories = [Budget, Actual, Variance]
```
- Inputs: `summary.totalBudget()`, `summary.totalActual()`, `summary.costVariance()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/cost/CostInsightsCollector.java:147-170`
- Notes: Each null coerced to 0.0 via `doubleValue` guard. `costVariance` is pass-through (not recomputed).
- Example: budget = 100, actual = 120, variance = −20 → three stacked bars 100 / 120 / −20.

### Cost Account (`CostAccountInsightsCollector`)

**Cost-account total BAC rollup**
```
totalBac = Σ over rows of (r.bac() != null ? r.bac() : 0)
```
- Inputs: `evmService.getCostAccountRollup(projectId) → List<CostAccountRollupResponse>`; `r.bac()` per account.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/costaccount/CostAccountInsightsCollector.java:34-36`
- Notes: BigDecimal reduce from ZERO; nulls → ZERO. Includes the unassigned (`costAccountId==null`) row.
- Example: bac = [50000, 30000, null, 20000] → totalBac = 100000.

**Cost-account total EV rollup**
```
totalEv = Σ over rows of (r.ev() != null ? r.ev() : 0)
```
- Inputs: `r.ev()` per `CostAccountRollupResponse`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/costaccount/CostAccountInsightsCollector.java:37-39`
- Notes: Nulls → ZERO.
- Example: ev = [40000, 25000, 0, 15000] → totalEv = 80000.

**Cost-account total AC rollup**
```
totalAc = Σ over rows of (r.ac() != null ? r.ac() : 0)
```
- Inputs: `r.ac()` per `CostAccountRollupResponse`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/costaccount/CostAccountInsightsCollector.java:40-42`
- Notes: Nulls → ZERO.
- Example: ac = [45000, 28000, 0, 18000] → totalAc = 91000.

**AC-weighted CPI (cost-account)** — *genuine in-collector derived ratio*
```
weightedCpi = (totalAc > 0) ? totalEv / totalAc  (scale 4, HALF_UP) : null
```
- Inputs: `totalEv`, `totalAc` rollups (lines 37-42).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/costaccount/CostAccountInsightsCollector.java:43-45`
- Notes: Portfolio-level CPI weighted by AC. Division-by-zero guarded → null when `totalAc<=0`. Rounded 4 dp HALF_UP. This aggregate CPI = EV-sum / AC-sum, which differs from averaging per-account `r.cpi()`.
- Example: totalEv = 80000, totalAc = 91000 → 80000 / 91000 = **0.8791**.

**Unassigned activity count (cost-account)**
```
unassignedCount = first row where costAccountId == null → that row's activityCount, else 0
```
- Inputs: rows filtered on `costAccountId==null`; `CostAccountRollupResponse.activityCount()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/costaccount/CostAccountInsightsCollector.java:47-51`
- Notes: Picks the single synthetic "unassigned" bucket's `activityCount` (`findFirst`), default 0. Feeds `promptInstructions` focus on unassigned activities.
- Example: unassigned row activityCount = 7 → unassignedCount = 7.

**Top-10 cost accounts selection**
```
topAccounts := rows.filter(costAccountId != null).limit(10)
  mapping {name, bac, ev, ac, cpi, activityCount}
```
- Inputs: rows with non-null `costAccountId`; per-account fields incl. `r.cpi()` (pass-through).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/costaccount/CostAccountInsightsCollector.java:53-66`
- Notes: Top-N = first 10 in service order (NOT sorted by BAC/CPI). Per-account `cpi` is pass-through from `EvmService`. Excludes unassigned row. Same filter+`limit(10)` reused for charts at lines 89-92.

**Per-account variance bar (BAC−EV)** — *genuine in-collector subtraction*
```
varData[i] = (bac_i != null ? bac_i : 0) - (ev_i != null ? ev_i : 0)   for each of top-10 accounts
```
- Inputs: `r.bac()`, `r.ev()` per account.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/costaccount/CostAccountInsightsCollector.java:129-135`
- Notes: BigDecimal subtract, nulls → ZERO. Positive = under-earned budget remaining; series labelled "Variance (BAC-EV)".
- Example: bac = 50000, ev = 40000 → variance bar = 10000.

**Treemap value (cost-account chart)**
```
item.value = (bac != null ? bac.doubleValue() : 0)   per top-10 account
```
- Inputs: `r.bac()`, `r.costAccountName()` (default "Unknown").
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/costaccount/CostAccountInsightsCollector.java:100-105`
- Notes: Pass-through BAC into treemap sizing; null name → "Unknown", null bac → 0. No new math.

### Contract (`ContractInsightsCollector`)

**Contract count rollup**
```
totalCount = contracts.size()   (page size 1000, page 0)
```
- Inputs: `contractService.listByProject(projectId, PageRequest.of(0,1000)).content()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:36-40`
- Notes: Counts only the first 1000 contracts (single page); >1000 silently truncated.
- Example: 23 contracts → totalCount = 23.

**Total contract value rollup**
```
totalValue = Σ contractValue over contracts where contractValue != null
```
- Inputs: `ContractResponse.contractValue()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:41-44`
- Notes: BigDecimal reduce from ZERO; nulls filtered (not coerced). Emitted as `totalContractValue` via `.doubleValue()`.
- Example: values [1.2e6, 800000, null] → 2,000,000.

**Total revised value rollup**
```
totalRevisedValue = Σ revisedValue over contracts where revisedValue != null
```
- Inputs: `ContractResponse.revisedValue()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:45-48`
- Notes: Nulls filtered. Emitted via `.doubleValue()`. Also recomputed for the value-vs-revised bar chart at lines 126-129.
- Example: revised [1.35e6, 800000] → 2,150,000.

**Contract status breakdown**
```
statusBreakdown = groupingBy(status.name(), counting()) over contracts with status != null
```
- Inputs: `ContractResponse.status()` enum name.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:50-52`
- Notes: `Map<statusName, count>`. Null-status excluded. Reused for the donut chart at lines 115-119.
- Example: `{ACTIVE:12, CLOSED:8, DRAFT:3}`.

**Top-5 revised-value-increase contracts (snapshot)**
```
filter(revisedValue != null && contractValue != null && revisedValue > contractValue)
  → sort by revisedValue DESC → limit 5
```
- Inputs: `ContractResponse.revisedValue()`, `contractValue()`; emits `spi`, `cpi`, `performanceScore` (pass-through).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:54-59`
- Notes: Selection = only contracts that INCREASED (revised > original), ranked by absolute `revisedValue` (NOT by delta). `spi`/`cpi`/`performanceScore` pass-through.

**Top-5 absolute value-variance contracts (chart)**
```
filter(revised != null && contractValue != null && revised != contractValue)
  → sort by |revised - contractValue| DESC → limit 5
varianceValues[i] = revised_i - contractValue_i
```
- Inputs: `ContractResponse.revisedValue()`, `contractValue()`; category = `contractNumber`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:135-150`
- Notes: Distinct from snapshot ranking — chart includes DECREASES too (`!=0`) and ranks by ABSOLUTE delta, plotting the signed value.
- Example: A 1.5M→1.2M (Δ −300k), B 800k→900k (Δ +100k) → A ranked first by |300k|, bar shows **−300000**.

**DLP end date computation**
```
dlpEnd = baseDate.plusMonths(dlpMonths)
baseDate = actualCompletionDate ?? revisedCompletionDate ?? completionDate
         (null if no base date OR dlpMonths null)
```
- Inputs: `ContractResponse.actualCompletionDate()`, `revisedCompletionDate()`, `completionDate()`, `dlpMonths()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:181-193`
- Notes: Coalesce priority actual > revised > planned completion. DLP = Defect Liability Period.
- Example: actualCompletion = 2026-01-31, dlpMonths = 12 → dlpEnd = 2027-01-31.

**Near-DLP threshold flag**
```
isNearDlp = (dlpEnd != null) && (dlpEnd < today.plusMonths(3) || dlpEnd < today)
```
- Inputs: `computeDlpEnd(c)`, `today = LocalDate.now()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:166-172`
- Notes: Threshold = within 3 months (the second OR clause is redundant — past dates are already < today+3mo).
- Example: today = 2026-06-26, dlpEnd = 2026-08-01 → 2026-08-01 < 2026-09-26 → near-DLP = **true**.

**Expired / near-BG threshold flag**
```
isExpiredOrNearBg = (bgExpiry != null) && bgExpiry < today.plusMonths(3)
```
- Inputs: `ContractResponse.bgExpiry()`, `today`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/contract/ContractInsightsCollector.java:174-179`
- Notes: BG = Bank Guarantee. Threshold = expiry within 3 months (also catches already-expired). A contract is added to `contractsNearDlpOrExpiredBg` if `isNearDlp OR isExpiredOrNearBg` (lines 62-64).
- Example: today = 2026-06-26, bgExpiry = 2026-09-01 → < 2026-09-26 → flagged.

### EVM (`EvmInsightsCollector`)

**EVM snapshot pass-through**
```
pv, ev, ac, sv, cv, spi, cpi, eac, etc, vac, tcpi, bac, performancePercentComplete
  := EvmSummaryResponse fields (no in-collector math)
```
- Inputs: `evmService.getSummary(projectId) → EvmSummaryResponse`; all EVM metrics computed inside `EvmService`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/evm/EvmInsightsCollector.java:34-47`
- Notes: Full EVM set computed upstream — `SV=EV−PV`, `CV=EV−AC`, `SPI=EV/PV`, `CPI=EV/AC`, `EAC`, `ETC`, `VAC=BAC−EAC`, `TCPI`. Collector adds NO new ratios. Renamed keys only (e.g. `plannedValue → pv`).

**EVM history trend top-5 selection**
```
historyTrend := history.stream().limit(5)  mapping {dataDate, pv, ev, ac, spi, cpi}
```
- Inputs: `evmService.getEvmHistory(projectId) → List<EvmCalculationResponse>`; per-snapshot `spi`/`cpi` pass-through.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/evm/EvmInsightsCollector.java:49-62`
- Notes: Top-N = first 5 history rows in service order (no re-sort). Same `limit(5)` reused for the PV/EV/AC line chart (`recentHistory`) at line 82. Null `dataDate` → null string.

**SPI/CPI dual-gauge values (evm chart)**
```
spiGauge.value = spi?.doubleValue() : 0.0
cpiGauge.value = cpi?.doubleValue() : 0.0
both gauges min = 0, max = 2
```
- Inputs: `summary.schedulePerformanceIndex()`, `summary.costPerformanceIndex()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/evm/EvmInsightsCollector.java:135-162`
- Notes: Nulls → 0.0. Fixed 0..2 scale. SPI gauge centered at x=25%, CPI at x=75%. Pass-through.
- Example: SPI = 0.92, CPI = 1.05 → needles at 0.92 and 1.05.

**EAC vs BAC bar values (evm chart)**
```
eacData = [ bac?.doubleValue():0.0 , eac?.doubleValue():0.0 ]   categories [BAC, EAC]
```
- Inputs: `summary.budgetAtCompletion()`, `summary.estimateAtCompletion()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/evm/EvmInsightsCollector.java:168-190`
- Notes: Pass-through BAC and EAC, nulls → 0.0. VAC (=BAC−EAC) is NOT computed in the chart but is present in the snapshot pass-through (line 44).
- Example: BAC = 10M, EAC = 11.5M → bars 10000000 / 11500000 (implied overrun 1.5M).

---

## Family 2 — KPI & Capacity & Period-Performance

### Equipment KPI (`EquipmentKpiInsightsCollector`)

**Equipment lookback window**
```
from = today - 30 days; to = today        (DEFAULT_LOOKBACK_DAYS = 30)
logs = EquipmentLog where projectId match AND logDate in [from, today]
```
- Inputs: `DEFAULT_LOOKBACK_DAYS`; `EquipmentLogRepository.findByProjectIdAndLogDateBetween(projectId, from, today)`; `LocalDate.now()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/EquipmentKpiInsightsCollector.java:43`
- Notes: Window inclusive of both bounds. `today` re-evaluated per call (non-deterministic across days). `logRows = logs.size()`; `equipmentTracked` = distinct resourceIds.
- Example: today = 2026-06-26 → from = 2026-05-27; a log dated 2026-06-01 is included, one dated 2026-05-20 excluded.

**Per-equipment hour aggregation**
```
for each resourceId:
  acc[0] = Σ operatingHours
  acc[1] = Σ idleHours
  acc[2] = Σ breakdownHours
  acc[3] = Σ fuelConsumed         (nulls skipped)
```
- Inputs: `EquipmentLog.operatingHours/.idleHours/.breakdownHours/.fuelConsumed` grouped by `resourceId` into `double[4]`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/EquipmentKpiInsightsCollector.java:62`
- Notes: Null components not added (treated as 0). Plain double sums, no rounding.
- Example: EX-01 logs (op=6,idle=1,bd=0,fuel=40) and (op=4,idle=3,bd=1,fuel=30) → acc = [10, 4, 1, 70].

**Equipment utilization %**
```
total = operatingHours + idleHours + breakdownHours
util  = total > 0 ? operatingHours / total : 0
```
- Inputs: aggregated `acc[0]=operating`, `acc[1]=idle`, `acc[2]=breakdown` (breakdown EXCLUDES fuel `acc[3]`).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/EquipmentKpiInsightsCollector.java:79`
- Notes: Stored as a 0–1 fraction in `utilizationPct` (NOT ×100), despite the chart title "Equipment Utilisation %". Div-zero guarded → 0. Fuel hours not in denominator.
- Example: acc = [10,4,1,70] → total = 15, util = 10/15 = **0.6667** (rendered "66.67%" downstream).

**Equipment top-N selection (idle-ranked)**
```
sort equipment by idleHours (acc[1]) DESC, take first 15
```
- Inputs: per-equipment aggregate map entrySet, `Double.compare(y.idle, x.idle)`, `.limit(15)`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/EquipmentKpiInsightsCollector.java:72`
- Notes: Ranking key = cumulative idle hours, not utilisation. Both util and idle charts built from this same idle-ranked set.
- Example: idle totals {EX-01:4, CR-02:9, LD-03:1} → order CR-02, EX-01, LD-03.

**Equipment idle alert (latest-day threshold)**
```
latest = max(logDate)
alert if logDate == latest AND idleHours != null AND idleHours > 2.0
```
- Inputs: `EquipmentLog.logDate` (max), `EquipmentLog.idleHours`; threshold literal 2d.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/EquipmentKpiInsightsCollector.java:97`
- Notes: Per-log (not aggregated) idle hours on the single most recent log date only. Strict `> 2`; exactly 2.0 NOT flagged. Emits `resourceCode` + `idleHours`.
- Example: latest day machine with idleHours = 3.5 flagged; one with 2.0 not.

### Manpower KPI (`ManpowerKpiInsightsCollector`)

**Manpower lookback + labour filter**
```
from = today - 30 days
labour = DAR rows where resourceId → Resource.resourceType.code equalsIgnoreCase 'MANPOWER'
```
- Inputs: `DEFAULT_LOOKBACK_DAYS=30`; `DailyActivityResourceOutputRepository.findByProjectIdAndOutputDateBetween(...)`; `Resource.resourceType.code` vs `LABOR_TYPE_CODE='MANPOWER'`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/ManpowerKpiInsightsCollector.java:52`
- Notes: Non-MANPOWER and null-resource rows dropped. `darRows = labour.size()` (after filter). Window inclusive.
- Example: 50 DAR rows, 30 map to MANPOWER → darRows = 30.

**Manpower project totals**
```
totalLabourHours = Σ DailyActivityResourceOutput.hoursWorked
totalQtyExecuted = Σ DailyActivityResourceOutput.qtyExecuted      (over labour rows, nulls skipped)
```
- Inputs: `DAR.hoursWorked` (Double), `DAR.qtyExecuted` (BigDecimal → double).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/ManpowerKpiInsightsCollector.java:77`
- Notes: `qtyExecuted` via `BigDecimal.doubleValue()`; nulls excluded.
- Example: hoursWorked {8,16,8} qtyExecuted {10,20,5} → totalLabourHours = 32, totalQtyExecuted = 35.

**Per-activity hours/qty aggregation + top-N**
```
for each activityId: acc[0] = Σ hoursWorked, acc[1] = Σ qtyExecuted
sort by qty (acc[1]) DESC, take first 10
```
- Inputs: DAR grouped by `activityId` into `double[2]`; qty-DESC comparator; `.limit(10)`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/ManpowerKpiInsightsCollector.java:90`
- Notes: Null `activityId` skipped. Top-10 ranked by total quantity executed (not hours, not factor).
- Example: qty totals {Excavation:120, Shuttering:80, Rebar:200} → order Rebar, Excavation, Shuttering.

**Actual output per man per day**
```
daysEq = hours > 0 ? hours / 8.0 : 0
actualOutputPerManPerDay = daysEq > 0 ? qty / daysEq : 0
```
- Inputs: aggregated `acc[0]=totalHours`, `acc[1]=totalQty`; constant 8.0 hours = 1 man-day.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/ManpowerKpiInsightsCollector.java:108`
- Notes: 8-hour man-day hardcoded. Two-stage div-zero guard → 0. Equivalent to `8*qty/hours` when hours>0.
- Example: totalHours = 80, totalQty = 200 → daysEq = 10, output = 200/10 = **20.0** units/man-day.

**Productivity norm lookup**
```
norm = first ProductivityNorm.outputPerManPerDay where activityName ~ Activity.name (case-insensitive), else 0
```
- Inputs: `ProductivityNormRepository.findByActivityNameIgnoreCase(Activity.name) → outputPerManPerDay` (BigDecimal → double), `findFirst`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/ManpowerKpiInsightsCollector.java:111`
- Notes: Matched by activity NAME string, not id. Null name or no norm row → norm = 0 (which forces factor = 0).
- Example: "Excavation" matches a norm with outputPerManPerDay = 25 → norm = 25.0.

**Productivity factor (actual / norm)**
```
productivityFactor = norm > 0 ? actualOutputPerManPerDay / norm : 0
```
- Inputs: `actualOutputPerManPerDay`, `norm`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/kpi/ManpowerKpiInsightsCollector.java:120`
- Notes: 1.0 = on norm. Highlight threshold (per promptInstructions / chart caption): factor `< 0.8` = under-performing (strict, so 0.8 itself NOT flagged). Missing norm yields factor 0 (looks under-performing though it is merely unmapped).
- Example: actual = 20, norm = 25 → factor = **0.8** (exactly on boundary, NOT flagged).

### Period Performance (`PeriodPerformanceInsightsCollector`)

**Period selection / ordering (snapshot)**
```
group StorePeriodPerformanceDto by financialPeriodId
sort by FinancialPeriod.sortOrder DESCENDING, take first 5 (topPeriods)
```
- Inputs: `CostService.getProjectPeriodPerformance(projectId)`; `FinancialPeriodRepository.findByProjectIdOrderBySortOrderAsc`; `FinancialPeriod.sortOrder` (null → 0).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/periodperformance/PeriodPerformanceInsightsCollector.java:42`
- Notes: Snapshot uses sortOrder DESC (most recent 5); the chart path uses sortOrder ASC for left-to-right time series. Null sortOrder → 0.
- Example: {Jan:1…Jun:6} → topPeriods = Jun, May, Apr, Mar, Feb.

**Per-period actual cost**
```
actualCost = Σ actualLaborCost + Σ actualNonlaborCost + Σ actualMaterialCost + Σ actualExpenseCost
             (over the period's DTOs, nulls → ZERO)
```
- Inputs: `StorePeriodPerformanceDto.actualLaborCost/.actualNonlaborCost/.actualMaterialCost/.actualExpenseCost`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/periodperformance/PeriodPerformanceInsightsCollector.java:57`
- Notes: BigDecimal arithmetic, no rounding/scale change. Four cost categories summed across all rows in the period. Currency-neutral raw number (relabel-only policy).
- Example: labor=100k, nonlabor=20k, material=50k, expense=10k → actualCost = 180,000.

**Per-period EV / PV / labor units**
```
earnedValue  = Σ earnedValueCost
plannedValue = Σ plannedValueCost
laborUnits   = Σ actualLaborUnits           (per period, nulls → ZERO/0.0)
```
- Inputs: `StorePeriodPerformanceDto.earnedValueCost/.plannedValueCost/.actualLaborUnits`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/periodperformance/PeriodPerformanceInsightsCollector.java:70`
- Notes: EV/PV BigDecimal sums; `laborUnits` double sum. No CV/SV at row level (variance only via cumulative-variance chart).
- Example: earnedValueCost {60k,40k} → EV = 100,000; plannedValueCost {70k,50k} → PV = 120,000.

**Project-wide totals snapshot**
```
totalActualCost   = Σ(all four actual cost fields over ALL records)
totalEarnedValue  = Σ earnedValueCost
totalPlannedValue = Σ plannedValueCost
totalLaborUnits   = Σ actualLaborUnits
```
- Inputs: all `StorePeriodPerformanceDto` records (not just top-5).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/periodperformance/PeriodPerformanceInsightsCollector.java:92`
- Notes: Computed over the full record set, unlike `topPeriods` (limited to 5). Same four-category actual-cost sum.
- Example: labor=500k, nonlabor=80k, material=200k, expense=40k → totalActualCost = 820,000.

**Cumulative variance (period-over-period running sum)**
```
for periods in sortOrder ASC:
  PV = Σ plannedValueCost
  AC = Σ(actualLaborCost + actualNonlaborCost + actualMaterialCost + actualExpenseCost)
  periodVariance = PV - AC
  cumulativeVar += periodVariance        (emit running total per period)
```
- Inputs: `plannedValueCost`; four actual cost fields; iterated over `sortedPeriods` (ASC, limit 5).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/periodperformance/PeriodPerformanceInsightsCollector.java:233`
- Notes: Schedule-Variance-style PV − AC running sum (positive = under budget vs plan). Chart caption "Running sum of PV minus AC across periods". Limited to first 5 periods by sortOrder ascending.
- Example: P1 PV=120k AC=100k → +20k, cum=20k; P2 PV=90k AC=130k → −40k, cum=−20k; P3 PV=110k AC=100k → +10k, cum=−10k. varData = [20000, −20000, −10000].

**Planned-vs-actual line series**
```
per period (sortOrder ASC, limit 5):
  PV point = Σ plannedValueCost
  AC point = Σ(actualLaborCost + actualNonlaborCost + actualMaterialCost + actualExpenseCost)
```
- Inputs: same DTO cost fields; x-axis categories = `FinancialPeriod.name`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/periodperformance/PeriodPerformanceInsightsCollector.java:179`
- Notes: Two line series ("Planned Value", "Actual Cost"); x-axis labels from `FinancialPeriod.name` (null → "").
- Example: Jan/Feb PV {120k,90k}, AC {100k,130k} → pvData=[120000,90000], avData=[100000,130000].

### Capacity Utilization (`CapacityUtilizationInsightsCollector` + upstream service)

**Capacity report invocation + ranking by cumulative qty**
```
report  = build(projectId, null, null, 'RESOURCE_TYPE', null)
topRows = rows sorted by cumulative.qty DESC, limit 10
```
- Inputs: `CapacityUtilizationReportService.build` (groupBy='RESOURCE_TYPE'); `Row.cumulative().qty()` (BigDecimal, null → ZERO).
- File: `bipros-reporting/src/main/java/com/bipros/reporting/insights/CapacityUtilizationInsightsCollector.java:38`
- Notes: Snapshot top-10 ranked by cumulative quantity. Each node emits workActivity code/name, groupLabel, budgeted.outputPerDay, cumulative qty/days/util%, day util%, month util%. groupBy fixed to RESOURCE_TYPE.
- Example: cumulative.qty {RebarFixer:500, Mason:300, Carpenter:800} → order Carpenter, RebarFixer, Mason.

**Cumulative utilization % (upstream, counted-first)**
```
utilizationPct = budgetDays / trackedActual * 100
                 (budgeted output-days over actual tracked days, ×100)
```
- Inputs: `CapacityUtilizationReportService`: `budgetDays`, `trackedActual` (HUNDRED=100); surfaced as `Row.cumulative().utilizationPct()`.
- File: `bipros-reporting/src/main/java/com/bipros/reporting/application/service/CapacityUtilizationReportService.java:583`
- Notes: Upstream value already a percentage (0–150 range used for gauge), unlike the equipment collector's 0–1 fraction. "Counted-first" display so efficiency reproduces by eye (commit 93a90587). Scale 4, HALF_UP. day/month variants computed analogously.
- Example: budgetDays = 8, trackedActual = 10 → 8/10×100 = **80.0%**.

**Under-utilized row count (threshold)**
```
underUtilizedRowCount = count(rows where cumulative.utilizationPct != null AND utilizationPct < 80)
```
- Inputs: `Row.cumulative().utilizationPct()` compared to `BigDecimal.valueOf(80)`.
- File: `bipros-reporting/src/main/java/com/bipros/reporting/insights/CapacityUtilizationInsightsCollector.java:58`
- Notes: Threshold = 80%. Strict `< 80`; exactly 80 not counted. Null util excluded.
- Example: util {72, 80, 95, 60} → underUtilizedRowCount = 2 (72 and 60).

**Capacity top-N utilization chart**
```
top = rows with non-null cumulative.utilizationPct sorted DESC, limit 10
bar of code → utilizationPct
```
- Inputs: `Row.cumulative().utilizationPct()` (null filtered); `Row.workActivity().code()`.
- File: `bipros-reporting/src/main/java/com/bipros/reporting/insights/CapacityUtilizationInsightsCollector.java:79`
- Notes: Chart-path ranking by utilization% DESC (different from snapshot's qty-DESC). Caption "Top 10 work activities by cumulative utilization".
- Example: util {A:120, B:95, C:80} → order A, B, C.

**Average utilization gauge (mean rollup)**
```
avgUtil    = mean(cumulative.utilizationPct over rows, nulls excluded), else 0
gaugeValue = round(avgUtil * 10) / 10.0
```
- Inputs: `Row.cumulative().utilizationPct()` → double average; gauge min=0, max=150.
- File: `bipros-reporting/src/main/java/com/bipros/reporting/insights/CapacityUtilizationInsightsCollector.java:92`
- Notes: Un-weighted arithmetic mean across all non-null rows. Result rounded to 1 dp. Gauge scale 0–150 (utilisation can exceed 100%). Empty/all-null → 0.0.
- Example: util {80, 100, 130} → avg = 103.333…, gauge = round(1033.33)/10 = **103.3**.

---

## Family 3 — Project & Risk

### Daily Outputs (`DailyOutputsInsightsCollector`)

**Pair output aggregation (per activity-resource pair)**
```
totalQtyExecuted = Σ r.qtyExecuted                          (nulls skipped)
totalHoursWorked = Σ (r.hoursWorked ?? 0)
totalDaysWorked  = Σ (r.daysWorked ?? (r.hoursWorked / 8.0) ?? 0)
```
- Inputs: `DailyActivityResourceOutputResponse` rows from `...Service.list(projectId,null,null,null,null)`, grouped by key `activityId + "|" + resourceId`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DailyOutputsInsightsCollector.java:41-65`
- Notes: `qtyExecuted` BigDecimal reduced from ZERO; null qty filtered. `daysWorked` falls back to `hoursWorked/8` (8-hour workday), else 0. No rounding before serialization (`.doubleValue()`).
- Example: Pair A|R 3 rows qty [10,15,5]=30; hours [8,8,4]=20; days all null → 8/8+8/8+4/8 = **2.5**.

**Output per day**
```
outputPerDay = totalDaysWorked > 0 ? totalQtyExecuted / totalDaysWorked : 0.0
```
- Inputs: `totalQty`, `totalDays` for the same pair.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DailyOutputsInsightsCollector.java:66`
- Notes: Div-by-zero guarded → 0.0. This is the productivity rate the prompt asks the LLM to reason about.
- Example: 30 / 2.5 = **12.0** units/day.

**Top-N pairs selection (snapshot)**
```
sort pairs by totalQtyExecuted DESC, take first 10
```
- Inputs: ObjectNode per pair; comparator on `n.get("totalQtyExecuted").asDouble()` reversed.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DailyOutputsInsightsCollector.java:78-80`
- Notes: Only top 10 serialized into `topPairs`; ranking by cumulative quantity, not `outputPerDay`.
- Example: totals [30,22,9,…] → first 10 kept descending; an 11th pair of 5 dropped.

**Overall total quantity (daily outputs)**
```
overallTotalQtyExecuted = Σ r.qtyExecuted over all rows   (nulls skipped)
```
- Inputs: all `DailyActivityResourceOutputResponse` rows.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DailyOutputsInsightsCollector.java:82-86`
- Notes: Project-wide sum independent of pair grouping; BigDecimal reduce then `.doubleValue()`.

**Daily quantity trend (chart, last 30 days)**
```
qtyByDate[date] = Σ r.qtyExecuted where r.outputDate == date
series = last 30 dates of sorted (TreeMap) keys
```
- Inputs: rows filtered `outputDate != null && qtyExecuted != null`, grouped by `outputDate` into TreeMap (chronological).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DailyOutputsInsightsCollector.java:103-117`
- Notes: If >30 distinct dates, sublist keeps the last 30 (most recent). Trend direction implied by chart ordering, not computed. Labels ISO date strings.
- Example: 40 dates exist → only days 11..40 plotted.

**Top-8 pairs by output (chart)**
```
qtyByPair[key] = Σ qtyExecuted        (key = activityId|resourceId)
sort by value DESC, limit 8; x-label = first 8 chars of key
```
- Inputs: rows filtered qty/activityId/resourceId non-null.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DailyOutputsInsightsCollector.java:119-135`
- Notes: Bar chart; label truncated to `substring(0, min(8,len))` so only leading UUID chars show.
- Example: key 'a1b2c3d4-…' shown as 'a1b2c3d4'.

### DPR (`DprInsightsCollector`)

**DPR per-activity aggregation**
```
totalQtyExecuted = Σ qtyExecuted                       (nulls skipped)
avgDailyQty      = list.isEmpty() ? 0 : totalQtyExecuted / list.size()
```
- Inputs: `DailyProgressReportResponse` rows from `...Service.list(projectId,null,null,null)`, grouped by `activityName`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DprInsightsCollector.java:44-57`
- Notes: `avgDailyQty` divides by record count (number of DPR rows), NOT calendar days. Top-10 activities by `totalQtyExecuted` DESC serialized.
- Example: "Excavation" 4 DPRs qty [20,30,25,25]=100 → avgDailyQty = 100/4 = 25.

**DPR top-N activities (snapshot + chart)**
```
snapshot: sort activities by totalQtyExecuted DESC limit 10
chart:    qtyByActivity sorted DESC limit 8
```
- Inputs: activity ObjectNodes (snapshot) / `qtyByActivity` map (chart, filtered activityName & qty non-null).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DprInsightsCollector.java:60-62, 132-141`
- Notes: Two different limits — 10 in JSON snapshot, 8 in the bar chart.

**DPR overall total quantity**
```
overallTotalQtyExecuted = Σ qtyExecuted over all DPR rows   (nulls skipped)
```
- Inputs: all `DailyProgressReportResponse` rows.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DprInsightsCollector.java:64-68`

**DPR date range**
```
dateRangeFrom = min(reportDate); dateRangeTo = max(reportDate)
```
- Inputs: `reportDate` over all rows (only when rows non-empty).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DprInsightsCollector.java:70-85`
- Notes: Natural-order min/max; emitted as ISO strings only if non-null. Skipped entirely when rows empty.

**DPR reporting coverage (last 14 days)**
```
reportedDaysInLast14 = count(distinct reportDate where date in [today-13, today])
expectedDaysInLast14 = 14
coverageRatio = reportedDaysInLast14 / 14.0
gauge value   = round(coverageRatio * 100)
```
- Inputs: `reportDate` filtered to window `fourteenDaysAgo = today.minusDays(13) .. today`; `LocalDate.now()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DprInsightsCollector.java:87-95, 150-160`
- Notes: Window inclusive 14 calendar days. Distinct dates so multiple DPRs same day count once. Gauge percent rounded to nearest int, 0–100 scale.
- Example: 9 distinct reported days → ratio 9/14 = 0.643 → gauge **64** (%).

**DPR weather breakdown (count & share)**
```
for each non-blank weatherCondition: count = number of DPR rows with that condition
```
- Inputs: rows filtered `weatherCondition != null && !blank`, grouped by `weatherCondition`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/project/DprInsightsCollector.java:97-107, 143-148`
- Notes: Raw counts only (no percentage in code); donut chart renders shares visually. Blank/null weather excluded.
- Example: Sunny→18, Rainy→5, Cloudy→3 → "Rainy" share = 5/26 ≈ 19% (computed by chart, not code).

### Risk (`RiskInsightsCollector` + upstream Risk domain)

**Risk total exposure cost**
```
totalExposureCost = Σ risk.preResponseExposureCost
                    over risks where status NOT in {CLOSED, RESOLVED} and cost != null
```
- Inputs: `RiskService.calculateRiskExposure(projectId) → riskRepository.findByProjectId`; `Risk.preResponseExposureCost`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/risk/RiskInsightsCollector.java:34, 63` (calc in `bipros-risk/.../service/RiskService.java:382-389`)
- Notes: Only open (non-terminal) risks contribute. `preResponseExposureCost` is upstream-computed monetary exposure (currency-neutral raw number). 0.0 when null. BigDecimal reduce.
- Example: open exposure [50000, 120000, 30000] → totalExposureCost = 200000.

**Risk open / closed / total counts**
```
totalRiskCount = allRisks.size()
openCount   = count(status != null && !isTerminal)
closedCount = count(status != null && isTerminal)
isTerminal  = status == CLOSED || status == RESOLVED
```
- Inputs: `RiskService.listRisks(projectId,null) → RiskSummary.getStatus()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/risk/RiskInsightsCollector.java:36-42, 135-137`
- Notes: Null-status risks excluded from open/closed but still counted in total. CLOSED and RESOLVED are the two terminal states.
- Example: 12 risks: 7 open, 4 terminal, 1 null-status → total 12, open 7, closed 4.

**Risk score (per risk, upstream)**
```
riskScore = matrixScore( probability, derivedImpact )
derivedImpact = HIGHEST_IMPACT: max(impactCost, impactSchedule)
              = AVERAGE_IMPACT: (impactCost + impactSchedule) / 2   (integer div)
```
- Inputs: `RiskSummary.getRiskScore()` mapped from `Risk.riskScore` (`RiskService.java:512`); `Risk.applyPreResponseScore` / `deriveImpact`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/risk/RiskInsightsCollector.java:72` (def in `bipros-risk/.../domain/model/Risk.java:175-210`)
- Notes: Matrix-derived (P6-style), typically probability(1–5) × impact(1–5) range; collector consumes as-is, no recompute. Used for top-N ranking and RAG banding.
- Example: probability HIGH(4), impactCost 3, impactSchedule 5, HIGHEST_IMPACT → derivedImpact 5; matrix(4,5) → score e.g. 20.

**Top-5 open high-score risks**
```
filter status != null && !isTerminal && riskScore != null
  → sort by riskScore DESC → limit 5
```
- Inputs: `allRisks` (RiskSummary); comparator `Double.compare(b.score, a.score)`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/risk/RiskInsightsCollector.java:44-49, 65-75`
- Notes: Each node carries code, title, probability/impact name, score, status, dueDate (ISO). Overdue detection left to the LLM via dueDate (not computed here).
- Example: scores [20,16,12,9,8,6] → top5 = [20,16,12,9,8].

**Risks by status (count by category)**
```
statusBreakdown[status.name()] = count of risks with that status   (status != null)
```
- Inputs: `RiskSummary.getStatus().name()`, groupingBy + counting.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/risk/RiskInsightsCollector.java:51-53, 77-79, 114-119`
- Notes: Raw counts; donut renders shares. Same map reused for the risk-status donut chart.
- Example: `OPEN→7, MITIGATING→1, CLOSED→3, RESOLVED→1`.

**Risks by RAG (count by category)**
```
ragBreakdown[rag.name()] = count of risks with that RAG   (rag != null)
RAG band from score: ≥20 CRIMSON; ≥12 RED; ≥6 AMBER; <6 GREEN   (opportunities → OPPORTUNITY)
```
- Inputs: `RiskSummary.getRag().name()`; band derived upstream in `Risk.deriveRag(score, isOpportunity)`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/risk/RiskInsightsCollector.java:55-57, 80-81, 107-112` (banding in `bipros-risk/.../domain/model/Risk.java:221-228`)
- Notes: Collector only counts pre-assigned RAG values; thresholds (20/12/6) live in the Risk domain. This is the threshold-driven severity classification.
- Example: scores [20,16,12,9,8,6,4] → CRIMSON 1, RED 2 (16,12), AMBER 3 (9,8,6), GREEN 1 (4).

**Risk probability × impact matrix points (chart)**
```
point = [probability.getValue(), impact.getValue()]   per risk where both non-null
values: VERY_LOW=1 .. VERY_HIGH=5
```
- Inputs: `RiskSummary.getProbability()/getImpact()` enums; `RiskProbability/RiskImpact.getValue()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/risk/RiskInsightsCollector.java:99-105` (enum values in `bipros-risk/.../domain/model/RiskProbability.java:9-24`, `RiskImpact.java:3-18`)
- Notes: Scatter plot; each axis 1–5. No aggregation — one dot per risk. Risks missing probability or impact dropped.
- Example: HIGH probability(4), MEDIUM impact(3) → point [4,3].

---

## Family 4 — GIS & Document & Base Variance Helpers

### GIS (`GisInsightsCollector` + upstream `ConstructionProgressService`)

**GIS: total mapped area**
```
totalMappedAreaSqMeters = Σ polygon.areaInSqMeters over polygons where areaInSqMeters != null
```
- Inputs: `polygons = wbsPolygonService.getByProject(projectId)`; `WbsPolygonResponse.areaInSqMeters` (Double, nullable).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/gis/GisInsightsCollector.java:54-59`
- Notes: Null areas filtered before summing. No rounding (double sum). Units = m². Negative/zero areas NOT excluded here (only nulls) but counted separately as data-quality issues — see `polygonsMissingArea`.
- Example: areas [1200.0, 800.5, null, 500.0] → **2500.5**.

**GIS: polygon / image counts**
```
polygonCount = polygons.size(); satelliteImageCount = images.size()
```
- Inputs: `wbsPolygonService.getByProject(projectId)`, `satelliteImageService.getByProject(projectId)`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/gis/GisInsightsCollector.java:45-46`
- Notes: Raw list sizes, integer counts. No filtering.
- Example: 10 polygons, 4 images → polygonCount=10, satelliteImageCount=4.

**GIS: latest image capture date**
```
latestImageCaptureDate = max(image.captureDate) over images where captureDate != null,
                         as ISO string; null if none
```
- Inputs: `SatelliteImageResponse.captureDate` (LocalDate, nullable).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/gis/GisInsightsCollector.java:48-52`
- Notes: `LocalDate.compareTo` for max; `Optional.orElse(null)`. Drives the LLM's "imagery freshness gap" narrative (the gap `today − latestCapture` is NOT computed in code).
- Example: [2026-01-10, 2026-03-22, null] → "2026-03-22".

**GIS: image source / status breakdown**
```
imageSourceBreakdown[src] = count(images where source.name() == src)
imageStatusBreakdown[st]  = count(images where status.name() == st)
```
- Inputs: `SatelliteImageResponse.source()` / `.status()` enums; `groupingBy + counting()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/gis/GisInsightsCollector.java:61-71`
- Notes: Null source/status rows excluded. Map enum-name → Long count. Source breakdown also feeds the gis-image-source donut chart (`charts()` lines 142-147).
- Example: sources [SENTINEL, SENTINEL, PLANET] → {SENTINEL:2, PLANET:1}.

**GIS: variance status breakdown**
```
varianceStatusBreakdown[s] = count(variances where varianceStatus == s)
```
- Inputs: `variances = constructionProgressService.getProgressVariance(projectId)`; `ProgressVarianceResponse.varianceStatus` (String: ON_TRACK|BEHIND|AHEAD|NO_DATA).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/gis/GisInsightsCollector.java:73-77`
- Notes: Null `varianceStatus` excluded (in practice never null upstream). Feeds gis-variance-status donut chart (lines 123-128).
- Example: [ON_TRACK, BEHIND, BEHIND, NO_DATA] → {ON_TRACK:1, BEHIND:2, NO_DATA:1}.

**GIS: top variances selection (abs-magnitude ranking)**
```
topVariances = variances filtered to variancePercent != null
  → sorted DESC by |variancePercent|
  → limited to 10 (snapshot) / 8 (chart)
```
- Inputs: `ProgressVarianceResponse.variancePercent` (Double); `Comparator.comparingDouble(|v.variancePercent()|).reversed()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/gis/GisInsightsCollector.java:79-94` (limit 10); `charts()` 130-140 (limit 8)
- Notes: Ranks by absolute value (both large AHEAD and large BEHIND surface). Upstream variance = derived − claimed, so positive means derived exceeds claimed. Each node carries wbsCode, wbsName, derivedPercent, claimedPercent, variancePercent, varianceStatus. Interpreting "suspicious" zones left to the LLM.
- Example: variancePercent [+3, −18, +12, null] → after filter sorted by abs DESC = [−18, +12, +3].

**GIS: data-quality counts (orphan polygons, missing area)**
```
polygonsWithoutWbsLink = count(polygons where wbsNodeId == null)
polygonsMissingArea    = count(polygons where areaInSqMeters == null OR areaInSqMeters <= 0)
```
- Inputs: `WbsPolygonResponse.wbsNodeId` (UUID), `.areaInSqMeters` (Double).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/gis/GisInsightsCollector.java:96-104`
- Notes: missing-area predicate treats both null AND non-positive (`<=0`) as missing — broader than the total-area sum filter (null only). Coverage ratio NOT computed; only raw counts emitted.
- Example: 10 polygons, 2 with `wbsNodeId==null`, 1 area=0.0 + 1 area=null → polygonsWithoutWbsLink=2, polygonsMissingArea=2.

**GIS upstream: variance percent (claimed vs imagery-derived)**
```
variancePercent = derivedProgressPercent - contractorClaimedPercent
                  (only when both non-null, else null)
```
- Inputs: `ConstructionProgressSnapshot.derivedProgressPercent`, `.contractorClaimedPercent`; latest snapshot per polygon by captureDate.
- File: `bipros-gis/src/main/java/com/bipros/gis/application/service/ConstructionProgressService.java:38-40` (and 87-95 on update), read at 109-147
- Notes: This is the actual delta the GIS collector surfaces. Sign convention: derived MINUS claimed (positive ⇒ satellite-derived exceeds contractor claim). Persisted on the snapshot; `getProgressVariance()` reads the LATEST snapshot (`snapshots.get(size-1)` after ORDER BY captureDate). No normalization — a raw difference of two percent values, in percentage points.
- Example: derived=42, claimed=60 → variancePercent = 42 − 60 = **−18** (claimed overstated by 18 pts).

**GIS upstream: variance status banding**
```
varianceStatus = NO_DATA  if no snapshots
               = BEHIND   if variancePercent > 10
               = AHEAD    if variancePercent < -5
               = ON_TRACK otherwise
```
- Inputs: `latest.getVariancePercent()` (Double) of the most recent snapshot per polygon.
- File: `bipros-gis/src/main/java/com/bipros/gis/application/service/ConstructionProgressService.java:117-135`
- Notes: Asymmetric thresholds: +10 pts triggers BEHIND, only −5 pts triggers AHEAD. Given variance = derived − claimed, variance>10 means derived far above claimed (under-reporting) yet labeled BEHIND — band is fixed in code, not configurable. Null variancePercent stays ON_TRACK (NOT NO_DATA — NO_DATA only when zero snapshots exist). Feeds `varianceStatusBreakdown` and the donut chart.
- Example: −18 < −5 → AHEAD; 12 > 10 → BEHIND; 3 → ON_TRACK; no snapshots → NO_DATA.

### Document (`DocumentInsightsCollector`)

**DOC: top-level counts (total, approved, superseded, missing-file)**
```
totalDocumentCount = documents.size()
approvedCount      = count(status == APPROVED)
supersededCount    = count(status == SUPERSEDED)
missingFileCount   = count(filePath == null OR filePath.isBlank())
```
- Inputs: `documents = documentService.listDocuments(projectId)`; `DocumentResponse.status` (enum), `.filePath` (String).
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/document/DocumentInsightsCollector.java:41-85`
- Notes: Counts only — no completeness RATIO computed in code (e.g. `approvedCount/total` left to the LLM). missing-file uses null OR blank. These map to `InsightHighlight` values rendered by the model.
- Example: 20 docs: 9 APPROVED, 2 SUPERSEDED, 3 blank/null filePath → 20 / 9 / 2 / 3.

**DOC: status / type / discipline breakdowns**
```
statusBreakdown[s]       = count(status.name() == s)
documentTypeBreakdown[t] = count(documentType.name() == t)
disciplineBreakdown[d]   = count(discipline.name() == d)
```
- Inputs: `DocumentResponse.status / .documentType / .discipline` (each enum, null rows filtered); `groupingBy + counting()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/document/DocumentInsightsCollector.java:43-53` (snapshot), 131-157 (charts)
- Notes: Null-valued dimensions excluded per breakdown. Discipline chart additionally sorts by value DESC and limits to top 8 (lines 141-144). Status & type as donuts, discipline as bar.
- Example: statuses [DRAFT,DRAFT,APPROVED] → {DRAFT:2, APPROVED:1}.

**DOC: stale drafts (age threshold + day-delta)**
```
staleDrafts = docs where status == DRAFT AND updatedAt != null AND daysSince(updatedAt) >= 14
  → sorted ASC by updatedAt → limit 10
daysSinceUpdate = Duration.between(updatedAt, now).toDays()
```
- Inputs: `DocumentResponse.status`, `.updatedAt` (Instant); `STALE_DRAFT_DAYS=14`; `now = Instant.now()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/document/DocumentInsightsCollector.java:30, 55-61, 96-104`
- Notes: `toDays()` truncates toward zero (floor for positive). Threshold `>= 14` whole days. Emits documentNumber, title, daysSinceUpdate, currentVersion. Sorted oldest-first so most overdue appear first.
- Example: DRAFT updated 20 days ago → 20 >= 14 → included (daysSinceUpdate=20); 13.9 days ago → toDays()=13 < 14 → excluded.

**DOC: review-pending (age threshold + day-delta)**
```
reviewPending = docs where status == UNDER_REVIEW AND updatedAt != null AND daysSince(updatedAt) >= 7
  → sorted ASC by updatedAt → limit 10
daysInReview = Duration.between(updatedAt, now).toDays()
```
- Inputs: `DocumentResponse.status`, `.updatedAt` (Instant); `REVIEW_PENDING_DAYS=7`; `now = Instant.now()`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/document/DocumentInsightsCollector.java:31, 63-69, 106-113`
- Notes: Same day-delta mechanics as stale drafts but threshold 7 days and UNDER_REVIEW status. `now=Instant.now()` makes the snapshot time-varying — affects the data hash (cache invalidates as docs cross the day boundary).
- Example: UNDER_REVIEW updated 9 days ago → 9 >= 7 → daysInReview=9, included.

### Shared insights-generation machinery & base variance helpers

**Generic variance / highlight / finding / recommendation (LLM-produced, NOT code-computed)**
```
InsightVariance(name, delta:String, explanation)
InsightHighlight(label, value:String, severity ∈ {info, warning, critical}, trend ∈ {up, down, flat, null})
InsightFinding(label, detail, severity)
InsightRecommendation(title, priority ∈ {low, medium, high}, action, rationale)
```
- Inputs: all fields populated by the LLM from the collector snapshot + `promptInstructions`; parsed via `objectMapper.convertValue` into `InsightsResponse`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/dto/InsightVariance.java:3-7`; `InsightHighlight.java:3-8`; `InsightFinding.java:3-7`; `InsightRecommendation.java:3-8`; `InsightsGenerator.java:66-82`
- Notes: There is NO code-side generic variance %, severity banding, or scoring machinery. `delta` is free-text String (e.g. "+18%", not a computed number); `severity`/`trend`/`priority` are enum-like Strings constrained only by JSON schema + prompt, decided by the model. The only deterministic post-processing is `withCharts()` merging server-built charts over LLM charts.
- Example: model emits `InsightHighlight(label="Missing files", value="3 of 20", severity="warning", trend="flat")` — none of value/severity/trend is arithmetic in code.

**InsightsGenerator: empty-response guard**
```
isEmpty = blank(summary) AND blank(rationale)
          AND empty(highlights) AND empty(variances)
          AND empty(recommendations) AND empty(findings)
        → throw AI_INSIGHT_GENERATION_FAILED
```
- Inputs: parsed `InsightsResponse` fields; `resp.content()` blank-check first.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/InsightsGenerator.java:62-79, 99-108`
- Notes: Boolean AND of all six emptiness checks — a single non-empty section passes. Also throws if raw LLM content is null/blank before parsing. No numeric scoring.
- Example: summary="", rationale=null, all lists empty → isEmpty=true → BusinessRuleException thrown.

**Data hash / cache key derivation**
```
hash     = Base64( SHA-256( objectMapper.writeValueAsString(dataSnapshot) ) )
cacheKey = (projectId, tabKey, hash)
```
- Inputs: `dataSnapshot = collector.collect(projectId)` (the ObjectNode root); `tabKey = collector.tabKey()` ("gis" / "documents" …); `projectId` from path.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/DataHashUtil.java:19-28`; cache lookup in `DocumentInsightsController.java:54-68` (`getCached(projectId, tab, hash)` / save)
- Notes: Hash covers the ENTIRE serialized snapshot JSON (counts, breakdown maps, area sum, topVariances array, staleDrafts/reviewPending arrays incl. `daysSince` values). Because the Document snapshot embeds `now=Instant.now()`-derived day-deltas, the hash drifts as documents age past day boundaries, naturally invalidating cache. Serialization order = JSON insertion order (ObjectNode preserves), so the hash is stable for identical data. `force=true` bypasses the cache. NOT canonical/sorted JSON — relies on deterministic ObjectNode field ordering. SHA-256 → 32 bytes → 44-char Base64.
- Example: snapshot `{"polygonCount":10,...}` → SHA-256 → Base64 "k3Jq…=" ; same project+tab+identical snapshot returns the cached `InsightsResponse` (re-merged with fresh charts via `withCharts`).

**Chart selection: discipline top-N and donut/bar payloads (GIS & DOC)**
```
disciplineEntries = disciplineBreakdown sorted by value DESC, limit 8
donut/bar options built from breakdown maps and topVariances lists
```
- Inputs: breakdown maps (LinkedHashMap), topVariances; `EChartsOptions.donut/bar(objectMapper, labels, seriesName, values)`.
- File: `bipros-ai/src/main/java/com/bipros/ai/insights/document/DocumentInsightsCollector.java:138-157`; `gis/GisInsightsCollector.java:122-149`
- Notes: Charts are deterministic and always server-built; merged over any LLM-supplied charts by `InsightsGenerator.withCharts` (lines 89-97). GIS top-variance bar uses `topVariances` (limit 8, abs-sorted) values = variancePercent; doc discipline bar uses top-8 counts. `charts(null)` returns id/title-only stubs (no option) used to inject chart IDs into the prompt via `chartAwarePromptInstructions()`.
- Example: disciplineBreakdown {CIVIL:12, MECH:5, ELEC:3, …} → bar shows top 8 by count DESC starting CIVIL=12.

---

## Insights conventions

**Variance / direction sign rule.** Variance is computed as a plain subtraction with a consistent "expected − actual" or "derived − claimed" sense per domain; the collectors never normalize it into a percentage of a base (except GIS upstream, which subtracts two percent values yielding *percentage points*, not a ratio):
- Cost / cost-account: `variance = budget − actual`, `BAC − EV` (positive = budget remaining / under-earned).
- EVM (upstream): `SV = EV − PV`, `CV = EV − AC`, `VAC = BAC − EAC`.
- Period performance: `periodVariance = PV − AC` (positive = under budget vs plan), accumulated as a running sum.
- Contract: `revised − original` (signed bar; snapshot ranks by absolute revised value, chart ranks by absolute delta).
- GIS: `variancePercent = derivedProgressPercent − contractorClaimedPercent` (positive ⇒ imagery exceeds claim, in percentage points).
- Generic `InsightVariance.delta` is a free-text String produced by the LLM — there is **no** code-side generic variance-% formula.

**Highlight / finding severity thresholds.** No `InsightHighlight`/`InsightFinding`/`InsightRecommendation` severity is assigned in any collector. Severity (`info|warning|critical`), trend (`up|down|flat`), and priority (`low|medium|high`) are enum-like Strings produced by the LLM from the snapshot + `promptInstructions`, constrained only by the JSON schema. The deterministic numeric thresholds that DO live in code are selection/flag gates, not severity bands:
- Equipment idle alert: latest-day `idleHours > 2.0` (strict).
- Manpower productivity: factor `< 0.8` = under-performing (strict; missing-norm yields factor 0).
- Capacity under-utilized: cumulative `utilizationPct < 80` (strict).
- Contract DLP/BG: within 3 months (`< today.plusMonths(3)`).
- Document staleness: DRAFT `>= 14` days; UNDER_REVIEW `>= 7` days.
- DPR coverage window: last 14 calendar days (`today−13 .. today`).
- Equipment/Manpower lookback: 30 days; daily-output trend chart: last 30 dates.
- Risk RAG banding (upstream Risk domain, not the collector): score `≥20 CRIMSON, ≥12 RED, ≥6 AMBER, <6 GREEN`; opportunities → OPPORTUNITY.
- GIS variance banding (upstream): `>10 BEHIND, <−5 AHEAD, else ON_TRACK, NO_DATA when no snapshots` (asymmetric).
- Top-N caps: 5 (cost periods, EVM history, contract rankings, top open risks), 8 / 10 (chart vs snapshot pairs/activities/disciplines/variances), 10 (cost accounts, capacity, DPR/daily-output snapshots, stale/review docs), 15 (equipment idle-ranked).

**Cache-hash keying.** Every insight is keyed `(projectId, tabKey, hash)` where `hash = Base64(SHA-256(serialized snapshot JSON))` (`DataHashUtil`). The hash covers the entire collector snapshot, so any change to any emitted field (including `Instant.now()`-derived day-deltas in the Document collector) drifts the key and invalidates the cache; identical data yields a stable key because `ObjectNode` preserves insertion order (the JSON is NOT canonicalized/sorted). `force=true` bypasses the cache. On a hit, the cached `InsightsResponse` is returned re-merged with freshly server-built charts via `withCharts`.

**Currency-neutrality.** All monetary figures the collectors emit (BAC/EV/AC, contract values, period costs, risk exposure, weighted CPI numerators) are raw currency-neutral numbers — `units × rate`, sums, and variances with no FX conversion. Per the platform's relabel-only currency policy, the project currency only changes how these numbers are labelled/abbreviated downstream; no collector multiplies by an exchange rate or touches a business-value calculation.

*Time-dependence caveat:* collectors that call `LocalDate.now()` / `Instant.now()` (equipment & manpower 30-day windows, contract DLP/BG flags, DPR coverage, document staleness/review-pending) produce snapshots that vary across day boundaries and are therefore non-deterministic over time — by design for the staleness and freshness narratives.
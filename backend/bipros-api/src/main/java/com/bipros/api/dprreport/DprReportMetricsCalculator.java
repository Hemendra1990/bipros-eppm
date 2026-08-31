package com.bipros.api.dprreport;

import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.text.DecimalFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DprReportMetricsCalculator {

    // Bands per client workbook (Web sheet, Capacity Utilization): >=100 green, 90-99 yellow,
    // <90 red, no norm grey — same boundaries as the Capacity tab's utilBand (CAP-16).
    static String efficiencySeverity(Double pct) {
        if (pct == null) return "info";
        if (pct < 90) return "critical";
        if (pct < 100) return "warning";
        return "info";
    }
    static boolean isCostOverrun(double costImplication) { return costImplication > 0; }
    static String fmtNumber(double v) { return new DecimalFormat("#,##0").format(v); }

    private static void allow(DprReportMetrics m, double v) {
        m.allowedNumbers.add(fmtNumber(v));
        m.allowedNumbers.add(String.valueOf(Math.round(v)));
        m.allowedNumbers.add(String.valueOf(Math.round(Math.abs(v))));
    }

    public DprReportMetrics compute(DprReportSnapshot s) {
        DprReportMetrics m = new DprReportMetrics();
        m.projectName = s.projectName();
        m.currencyCode = s.currencyCode();
        m.totalDprs = s.dprs() == null ? 0 : s.dprs().size();
        m.allowedNumbers.add(String.valueOf(m.totalDprs));

        // --- capacity efficiencies + anomalies (walk manpower + equipment RoleRows, cumulative bucket) ---
        addSection(m, s.capacity() == null ? null : s.capacity().manpower(), "manpower");
        addSection(m, s.capacity() == null ? null : s.capacity().equipment(), "equipment");

        // --- cost variance (from DailyCostReportResponse period totals) ---
        if (s.cost() != null) {
            m.totalActualCost = dbl(s.cost().periodActualCost());
            m.totalBudgetedCost = dbl(s.cost().periodBudgetedCost());
            m.totalCostVariance = dbl(s.cost().periodVariance());
            allow(m, m.totalActualCost);
            allow(m, m.totalBudgetedCost);
            allow(m, m.totalCostVariance);
            if (m.totalCostVariance > 0) {
                m.anomalies.add(new DprReportMetrics.Anomaly("COST_OVERRUN", "Cost overrun for the period",
                    "warning", "Actual exceeds budget by " + fmtNumber(m.totalCostVariance) + " " + m.currencyCode,
                    m.totalCostVariance));
            }
        }

        // --- issues + safety ---
        if (s.issues() != null) {
            m.openIssues = (int) s.issues().stream().filter(i -> isOpen(String.valueOf(i.status()))).count();
            m.criticalIssues = (int) s.issues().stream().filter(i -> "CRITICAL".equals(String.valueOf(i.severity()))).count();
            m.safetyIncidents = (int) s.issues().stream().filter(i -> i.hseIncidentType() != null).count();
            m.allowedNumbers.add(String.valueOf(m.openIssues));
            m.allowedNumbers.add(String.valueOf(m.criticalIssues));
            m.allowedNumbers.add(String.valueOf(m.safetyIncidents));
            if (m.criticalIssues > 0) {
                m.anomalies.add(new DprReportMetrics.Anomaly("CRITICAL_ISSUES", "Critical open issues",
                    "critical", m.criticalIssues + " critical issue(s) in the window", m.criticalIssues));
            }
            if (m.safetyIncidents > 0) {
                m.anomalies.add(new DprReportMetrics.Anomaly("SAFETY", "Safety incidents logged",
                    "critical", m.safetyIncidents + " HSE incident(s) in the window", m.safetyIncidents));
            }
        }

        // ── Consolidated sections (Phase 2, 2026-08-05) ──

        // DBS — day-basis money (canonical DBS day rows; period BOQ columns deliberately unused).
        if (s.dbsDays() != null) {
            for (var d : s.dbsDays()) {
                double income = dbl(d.totalIncome());
                double expense = dbl(d.totalExpense());
                double contribution = dbl(d.contribution());
                m.dbsDays.add(new DprReportMetrics.DayMoney(
                    String.valueOf(d.reportDate()), income, expense, contribution,
                    d.dprCount() == null ? 0 : d.dprCount()));
                m.dbsIncome += income;
                m.dbsExpense += expense;
                m.dbsContribution += contribution;
            }
            allow(m, m.dbsIncome);
            allow(m, m.dbsExpense);
            allow(m, m.dbsContribution);
        }
        if (s.dbsSupervisors() != null) {
            for (var sup : s.dbsSupervisors()) {
                m.dbsSupervisors.add(new DprReportMetrics.SupMoney(sup.name(),
                    dbl(sup.income()), dbl(sup.expense()), dbl(sup.contribution())));
                allow(m, dbl(sup.expense()));
                allow(m, dbl(sup.income()));
            }
        }

        // Supervisor performance + commodity summary (DPR-agent-row additions 2026-08-10) —
        // pass-through of the snapshot's derived rows; no arithmetic beyond double conversion.
        m.referenceDay = s.referenceDay() != null ? s.referenceDay().toString() : null;
        if (s.supervisorPerformance() != null) {
            for (var p : s.supervisorPerformance()) {
                m.supervisorPerformance.add(new DprReportMetrics.SupPerf(p.name(),
                    p.filedDay(), dbl(p.qtyDay()), p.filedWindow(), dbl(p.qtyWindow()),
                    p.contribution() != null ? dbl(p.contribution()) : null));
                allow(m, p.filedDay());
                allow(m, dbl(p.qtyDay()));
                allow(m, p.filedWindow());
                allow(m, dbl(p.qtyWindow()));
                if (p.contribution() != null) allow(m, dbl(p.contribution()));
            }
        }
        if (s.commodityBoq() != null) {
            for (var c : s.commodityBoq()) {
                m.commodityBoq.add(new DprReportMetrics.CommodityLine(c.label(), c.unit(),
                    c.contractedQty() != null ? dbl(c.contractedQty()) : null,
                    dbl(c.qtyMonth()), dbl(c.qtyToDate()),
                    c.pctComplete() != null ? dbl(c.pctComplete()) : null));
                allow(m, dbl(c.qtyMonth()));
                allow(m, dbl(c.qtyToDate()));
            }
        }
        if (s.commodityActivities() != null) {
            for (var c : s.commodityActivities()) {
                m.commodityActivities.add(new DprReportMetrics.CommodityLine(c.label(), c.unit(),
                    null, dbl(c.qtyMonth()), dbl(c.qtyToDate()), null));
            }
        }

        // Activity costing (Costing-agent-row 2026-08-11): aggregate the Daily Cost Report's
        // per-DPR rows by activity name. Actual + budgeted come from the engine's own row figures
        // ("clients should not recompute"); BOQ value = qty × BoqCostRow.boqRate for
        // revenue-counting rows only (split-line non-measurement ops excluded, same as the P&L).
        if (s.cost() != null && s.cost().rows() != null && !s.cost().rows().isEmpty()) {
            Map<String, Double> boqRateByItemNo = new HashMap<>();
            if (s.boqRows() != null) {
                for (var b : s.boqRows()) {
                    if (b.itemNo() != null && b.boqRate() != null) {
                        boqRateByItemNo.putIfAbsent(b.itemNo(), b.boqRate().doubleValue());
                    }
                }
            }
            final class Agg {
                double qty; String unit; double actual;
                double budgeted; boolean anyBudgeted;
                double boqValue; boolean anyBoq;
            }
            Map<String, Agg> byActivity = new LinkedHashMap<>();
            for (var row : s.cost().rows()) {
                String name = row.activity() != null && !row.activity().isBlank()
                    ? row.activity() : "(unnamed)";
                Agg a = byActivity.computeIfAbsent(name, k -> new Agg());
                a.qty += dbl(row.qtyExecuted());
                if (a.unit == null && row.unit() != null) a.unit = row.unit();
                a.actual += dbl(row.actualCost());
                if (row.budgetedCost() != null) { a.budgeted += dbl(row.budgetedCost()); a.anyBudgeted = true; }
                Double boqRate = row.boqItemNo() != null ? boqRateByItemNo.get(row.boqItemNo()) : null;
                if (boqRate != null && row.countsAsRevenue()) {
                    a.boqValue += dbl(row.qtyExecuted()) * boqRate;
                    a.anyBoq = true;
                }
            }
            List<DprReportMetrics.ActivityCost> lines = new ArrayList<>(byActivity.size());
            double tQty = 0, tActual = 0, tBudgeted = 0, tBoq = 0;
            boolean tAnyBudgeted = false, tAnyBoq = false;
            for (var e : byActivity.entrySet()) {
                Agg a = e.getValue();
                Double budgeted = a.anyBudgeted ? a.budgeted : null;
                Double boqVal = a.anyBoq ? a.boqValue : null;
                lines.add(new DprReportMetrics.ActivityCost(e.getKey(), a.qty, a.unit, a.actual,
                    budgeted, boqVal,
                    budgeted != null ? a.actual - budgeted : null,
                    boqVal != null ? a.actual - boqVal : null));
                tQty += a.qty; tActual += a.actual;
                if (a.anyBudgeted) { tBudgeted += a.budgeted; tAnyBudgeted = true; }
                if (a.anyBoq) { tBoq += a.boqValue; tAnyBoq = true; }
            }
            lines.sort((x, y) -> Double.compare(
                Math.abs(y.varVsBudgeted() != null ? y.varVsBudgeted() : y.actualCost()),
                Math.abs(x.varVsBudgeted() != null ? x.varVsBudgeted() : x.actualCost())));
            m.activityCostingCount = lines.size();
            m.activityCosting.addAll(lines.subList(0, Math.min(10, lines.size())));
            m.activityCostingTotal = new DprReportMetrics.ActivityCost("Total", tQty, null, tActual,
                tAnyBudgeted ? tBudgeted : null, tAnyBoq ? tBoq : null,
                tAnyBudgeted ? tActual - tBudgeted : null, tAnyBoq ? tActual - tBoq : null);
            for (var l : m.activityCosting) {
                allow(m, l.actualCost());
                if (l.qty() != null) allow(m, l.qty());
                if (l.budgetedValue() != null) allow(m, l.budgetedValue());
                if (l.boqValue() != null) allow(m, l.boqValue());
                if (l.varVsBudgeted() != null) allow(m, l.varVsBudgeted());
                if (l.varVsBoq() != null) allow(m, l.varVsBoq());
            }
            allow(m, m.activityCostingTotal.actualCost());
            if (m.activityCostingTotal.budgetedValue() != null) allow(m, m.activityCostingTotal.budgetedValue());
            if (m.activityCostingTotal.boqValue() != null) allow(m, m.activityCostingTotal.boqValue());
            long overBudget = lines.stream()
                .filter(l -> l.varVsBudgeted() != null && l.varVsBudgeted() > 0).count();
            if (overBudget > 0) {
                m.anomalies.add(new DprReportMetrics.Anomaly("ACTIVITY_COST_OVERRUN",
                    "Activities above budgeted-rate value", "warning",
                    overBudget + " activit" + (overBudget == 1 ? "y" : "ies")
                        + " cost more than their value at budgeted rates in this window",
                    overBudget));
            }
        }

        // Costing — stored split-corrected BOQ columns; top variances by absolute value.
        if (s.boqRows() != null) {
            m.boqTotalVariance = s.boqRows().stream().mapToDouble(b -> dbl(b.costVariance())).sum();
            allow(m, m.boqTotalVariance);
            s.boqRows().stream()
                .filter(b -> dbl(b.costVariance()) != 0.0)
                .sorted((a, b) -> Double.compare(Math.abs(dbl(b.costVariance())), Math.abs(dbl(a.costVariance()))))
                .limit(5)
                .forEach(b -> {
                    m.boqTopVariances.add(new DprReportMetrics.BoqVar(b.itemNo(),
                        b.description() == null ? b.itemNo() : b.description(),
                        dbl(b.boqRate()), dbl(b.budgetedRate()), dbl(b.actualRate()),
                        dbl(b.qtyExecuted()), dbl(b.percentComplete()), dbl(b.costVariance())));
                    allow(m, dbl(b.costVariance()));
                });
        }

        // Material consumption — aggregated from the window's APPROVED DPR material lines
        // (same rows the cost report prices).
        if (s.dprs() != null) {
            Map<String, double[]> byMaterial = new LinkedHashMap<>();
            Map<String, String> unitByMaterial = new LinkedHashMap<>();
            for (var d : s.dprs()) {
                if (d.materials() == null) continue;
                for (var mat : d.materials()) {
                    String name = mat.materialName() == null ? "(unnamed)" : mat.materialName();
                    double[] t = byMaterial.computeIfAbsent(name, k -> new double[2]);
                    t[0] += dbl(mat.quantity());
                    t[1] += dbl(mat.lineCost());
                    unitByMaterial.putIfAbsent(name, mat.unit());
                }
            }
            byMaterial.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .limit(10)
                .forEach(e -> {
                    m.materials.add(new DprReportMetrics.MaterialUse(e.getKey(),
                        unitByMaterial.get(e.getKey()), e.getValue()[0], e.getValue()[1]));
                    allow(m, e.getValue()[0]);
                    allow(m, e.getValue()[1]);
                });
        }

        // Material availability (store) + supervisor issued-vs-reported — Material-agent-row 2026-08-11.
        if (s.materialAvailability() != null) {
            m.materialTracked = s.materialAvailability().tracked();
            for (var row : s.materialAvailability().rows()) {
                String alert = row.alerts() == null || row.alerts().isEmpty() ? null : row.alerts().get(0);
                m.materialAvailability.add(new DprReportMetrics.MaterialAvail(
                    row.materialName(), row.unit(),
                    dblOrNull(row.receivedWindow()), dblOrNull(row.issuedWindow()), dblOrNull(row.consumedWindow()),
                    dblOrNull(row.storeClosing()), dblOrNull(row.daysOfCover()), alert));
                for (var v : new java.math.BigDecimal[]{row.receivedWindow(), row.issuedWindow(),
                        row.consumedWindow(), row.storeClosing(), row.daysOfCover()}) {
                    if (v != null) allow(m, v.doubleValue());
                }
            }
            long shortSupply = m.materialAvailability.stream()
                .filter(a -> "BELOW_MIN_STOCK".equals(a.alert()) || "LOW_COVER".equals(a.alert()))
                .count();
            if (shortSupply > 0) {
                m.anomalies.add(new DprReportMetrics.Anomaly("MATERIAL_SHORT_SUPPLY",
                    "Materials in short supply", "warning",
                    shortSupply + " material(s) below minimum stock or low days-of-cover", shortSupply));
            }
        }
        if (s.supervisorMaterialVariances() != null) {
            for (var v : s.supervisorMaterialVariances()) {
                m.supervisorMaterialVariances.add(new DprReportMetrics.SupMaterialVar(
                    v.supervisorName(), v.materialName(), v.unit(),
                    dbl(v.issuedToDate()), dbl(v.reportedToDate()), dbl(v.varianceQty()),
                    dblOrNull(v.varianceValue())));
                allow(m, dbl(v.issuedToDate()));
                allow(m, dbl(v.reportedToDate()));
                allow(m, dbl(v.varianceQty()));
                if (v.varianceValue() != null) allow(m, v.varianceValue().doubleValue());
            }
            long overIssued = s.supervisorMaterialVariances().stream()
                .filter(v -> v.varianceQty().signum() > 0).count();
            if (overIssued > 0) {
                m.anomalies.add(new DprReportMetrics.Anomaly("MATERIAL_ISSUE_VARIANCE",
                    "Issued material exceeds supervisor-reported consumption", "warning",
                    overIssued + " supervisor/material line(s) with issued > reported (see §Material)",
                    overIssued));
            }
        }

        // Per-supervisor workdone (raw workdone qty — the labelled tally, not the billable basis)
        // + issue-log category counts, both straight from the window's approved DPRs.
        if (s.dprs() != null) {
            Map<String, Object[]> work = new LinkedHashMap<>();   // name -> [dprCount, Set<activity>, qty]
            for (var d : s.dprs()) {
                String name = d.supervisorName() == null ? "(unnamed)" : d.supervisorName();
                Object[] t = work.computeIfAbsent(name, k -> new Object[]{0, new LinkedHashSet<String>(), 0.0});
                t[0] = ((int) t[0]) + 1;
                @SuppressWarnings("unchecked") Set<String> acts = (Set<String>) t[1];
                if (d.activityName() != null) acts.add(d.activityName());
                t[2] = ((double) t[2]) + dbl(d.qtyExecuted());
            }
            work.forEach((name, t) -> {
                @SuppressWarnings("unchecked") Set<String> acts = (Set<String>) t[1];
                m.supervisorWork.add(new DprReportMetrics.SupWork(name, (int) t[0], acts.size(), (double) t[2]));
                allow(m, (double) t[2]);
            });
            m.supervisorWork.sort((a, b) -> Double.compare(b.qty(), a.qty()));
        }
        if (s.issues() != null && !s.issues().isEmpty()) {
            Map<String, Integer> cats = new LinkedHashMap<>();
            for (var i : s.issues()) {
                cats.merge(String.valueOf(i.category()), 1, Integer::sum);
            }
            cats.forEach((k, v) -> m.issueCategories.add(new DprReportMetrics.CatCount(k, v)));
            m.issueCategories.sort((a, b) -> b.count() - a.count());
        }

        // EVM — canonical CostService figures (the verified Costs/EVM engine).
        if (s.evm() != null) {
            var e = s.evm();
            m.evm = new DprReportMetrics.EvmBlock(
                dbl(e.bac()), dbl(e.plannedValue()), dbl(e.earnedValue()), dbl(e.totalActual()),
                // Nullable pass-through: the engine's null (no AC / no PV) must stay distinct from
                // a genuine 0.0 score, or the key notes mislabel a real overrun as "no data".
                dblOrNull(e.costPerformanceIndex()), dblOrNull(e.schedulePerformanceIndex()),
                dbl(e.estimateAtCompletion()), dbl(e.bac()) - dbl(e.estimateAtCompletion()),
                // costPercentComplete is a FRACTION (0.238) — the tabs multiply by 100; so do we.
                dbl(e.costPercentComplete()) * 100.0,
                // EVM-agent-row 2026-08-11: the engine's own SV/CV/ETC/TCPI, no local arithmetic.
                dbl(e.scheduleVariance()), dbl(e.costVariance()),
                dbl(e.estimateToComplete()), dblOrNull(e.toCompletePerformanceIndex()));
            allow(m, m.evm.bac());
            allow(m, m.evm.pv());
            allow(m, m.evm.ev());
            allow(m, m.evm.ac());
            allow(m, m.evm.eac());
            allow(m, m.evm.vac());
            allow(m, m.evm.sv());
            allow(m, m.evm.cv());
            allow(m, m.evm.etc());
        }
        return m;
    }

    private void addSection(DprReportMetrics m, CapacityUtilizationReport.Section section, String kind) {
        if (section == null || section.rows() == null) return;
        List<DprReportMetrics.CapacityLine> lines =
            "manpower".equals(kind) ? m.capacityManpower : m.capacityEquipment;
        for (var row : section.rows()) {
            var cum = row.cumulative(); // RolePeriod
            if (cum == null) continue;
            Double util = dblOrNull(cum.utilizationPct());
            double impl = dbl(cum.costImplication());
            String sev = efficiencySeverity(util);
            m.roleEfficiencies.add(new DprReportMetrics.RoleEfficiency(row.roleName(), util, impl, sev));
            lines.add(capacityLine(m, row.roleName(), row.forTheDay(), cum));
            if (util != null) allow(m, util);
            allow(m, impl);
            if ("critical".equals(sev) || "warning".equals(sev)) {
                m.anomalies.add(new DprReportMetrics.Anomaly("LOW_EFFICIENCY",
                    row.roleName() + " efficiency " + fmtNumber(util) + "%", sev,
                    kind + " role below productivity norm", util == null ? 0 : util));
            }
            if (isCostOverrun(impl)) {
                m.anomalies.add(new DprReportMetrics.Anomaly("ROLE_COST_OVERRUN",
                    row.roleName() + " cost overrun", "warning",
                    "Overrun " + fmtNumber(impl) + " " + m.currencyCode, impl));
            }
        }
        if (section.totalCumulative() != null) {
            var total = capacityLine(m, "Total", section.totalForTheDay(), section.totalCumulative());
            if ("manpower".equals(kind)) m.capacityManpowerTotal = total;
            else m.capacityEquipmentTotal = total;
        }
    }

    /** Pass-through of one Capacity-tab role row (day + cumulative buckets) — no arithmetic. */
    private DprReportMetrics.CapacityLine capacityLine(
            DprReportMetrics m, String role,
            CapacityUtilizationReport.RolePeriod day, CapacityUtilizationReport.RolePeriod cum) {
        Double dayBudget = day == null ? null : dblOrNull(day.budgetDays());
        Double dayCounted = day == null ? null : dblOrNull(day.actualDays());
        Double dayEff = day == null ? null : dblOrNull(day.utilizationPct());
        Double qty = dblOrNull(cum.qty());
        Double budgetDays = dblOrNull(cum.budgetDays());
        Double countedDays = dblOrNull(cum.actualDays());
        for (Double v : new Double[]{dayBudget, dayCounted, qty, budgetDays, countedDays}) {
            if (v != null) allow(m, v);
        }
        return new DprReportMetrics.CapacityLine(role, dayBudget, dayCounted, dayEff,
            qty, budgetDays, countedDays, dblOrNull(cum.utilizationPct()),
            dblOrNull(cum.costImplication()));
    }

    private static boolean isOpen(String status) {
        return status != null && !Set.of("RESOLVED", "CLOSED", "CANCELLED").contains(status);
    }
    private static double dbl(Object bd) { return bd == null ? 0.0 : ((Number) bd).doubleValue(); }
    private static Double dblOrNull(Object bd) { return bd == null ? null : ((Number) bd).doubleValue(); }
}

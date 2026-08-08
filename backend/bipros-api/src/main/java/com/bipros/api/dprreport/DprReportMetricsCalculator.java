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
        // (same rows the cost report prices). Availability/receipts are excluded until the
        // material availability defects (finding F5) are fixed at source.
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
                dbl(e.costPerformanceIndex()), dbl(e.schedulePerformanceIndex()),
                dbl(e.estimateAtCompletion()), dbl(e.bac()) - dbl(e.estimateAtCompletion()),
                // costPercentComplete is a FRACTION (0.238) — the tabs multiply by 100; so do we.
                dbl(e.costPercentComplete()) * 100.0);
            allow(m, m.evm.bac());
            allow(m, m.evm.pv());
            allow(m, m.evm.ev());
            allow(m, m.evm.ac());
            allow(m, m.evm.eac());
            allow(m, m.evm.vac());
        }
        return m;
    }

    private void addSection(DprReportMetrics m, CapacityUtilizationReport.Section section, String kind) {
        if (section == null || section.rows() == null) return;
        for (var row : section.rows()) {
            var cum = row.cumulative(); // RolePeriod
            if (cum == null) continue;
            Double util = dblOrNull(cum.utilizationPct());
            double impl = dbl(cum.costImplication());
            String sev = efficiencySeverity(util);
            m.roleEfficiencies.add(new DprReportMetrics.RoleEfficiency(row.roleName(), util, impl, sev));
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
    }

    private static boolean isOpen(String status) {
        return status != null && !Set.of("RESOLVED", "CLOSED", "CANCELLED").contains(status);
    }
    private static double dbl(Object bd) { return bd == null ? 0.0 : ((Number) bd).doubleValue(); }
    private static Double dblOrNull(Object bd) { return bd == null ? null : ((Number) bd).doubleValue(); }
}

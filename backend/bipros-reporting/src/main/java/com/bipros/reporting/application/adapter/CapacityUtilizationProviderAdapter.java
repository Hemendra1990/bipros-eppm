package com.bipros.reporting.application.adapter;

import com.bipros.ai.agent.support.CapacityUtilizationProvider;
import com.bipros.reporting.application.dto.CapacityUtilizationReport;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.RolePeriod;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.RoleRow;
import com.bipros.reporting.application.dto.CapacityUtilizationReport.Section;
import com.bipros.reporting.application.dto.SupervisorPerformanceReport;
import com.bipros.reporting.application.service.CapacityUtilizationReportService;
import com.bipros.reporting.application.service.SupervisorPerformanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dependency-inversion adapter: exposes the canonical {@link CapacityUtilizationReportService} to the
 * AI module through the {@link CapacityUtilizationProvider} port (defined in {@code bipros-ai}, which
 * {@code bipros-reporting} already depends on). Pure delegation + mapping — it adds no computation, so
 * the AI agent's per-role efficiency and cost overrun are byte-for-byte the Capacity Util. tab's.
 */
@Component
@RequiredArgsConstructor
public class CapacityUtilizationProviderAdapter implements CapacityUtilizationProvider {

    /** Far-past start so the report's "cumulative" period covers the whole project through today. */
    private static final LocalDate PROJECT_START = LocalDate.of(1970, 1, 1);

    private final CapacityUtilizationReportService reportService;
    private final SupervisorPerformanceReportService supervisorReportService;

    @Override
    public List<SupervisorEfficiency> cumulativeBySupervisor(UUID projectId, List<UUID> supervisorUserIds) {
        List<SupervisorEfficiency> out = new ArrayList<>();
        if (supervisorUserIds == null) {
            return out;
        }
        for (UUID supervisorUserId : supervisorUserIds) {
            if (supervisorUserId == null) {
                continue;
            }
            SupervisorPerformanceReport report = supervisorReportService.build(
                    projectId, supervisorUserId, PROJECT_START, LocalDate.now(), 26);
            SupervisorPerformanceReport.Summary s = report.summary();
            if (s == null) {
                continue;
            }
            Agg manpower = new Agg();
            Agg equipment = new Agg();
            if (s.manpower() != null) {
                for (SupervisorPerformanceReport.TradeRollup t : s.manpower()) {
                    manpower.add(t.budgetedManDays(), t.actualManDays(), t.actualDaysOnHiddenSides(),
                            t.actualDaysUntracked(), t.costImplication());
                }
            }
            if (s.equipment() != null) {
                for (SupervisorPerformanceReport.EquipmentRollup e : s.equipment()) {
                    equipment.add(e.budgetedDays(), e.actualDays(), e.actualDaysOnHiddenSides(),
                            e.actualDaysUntracked(), e.costImplication());
                }
            }
            double budget = manpower.budgetedDays + equipment.budgetedDays;
            double counted = manpower.countedDays + equipment.countedDays;
            out.add(new SupervisorEfficiency(supervisorUserId,
                    manpower.pct(), equipment.pct(), Agg.pct(budget, counted),
                    budget, counted, manpower.cost + equipment.cost));
        }
        return out;
    }

    /**
     * Aggregate-then-divide accumulator. Each row's {@code utilizationPct} is a ratio over its own
     * denominator, so a supervisor's rollup must sum budgeted and counted days and divide ONCE.
     * Averaging the per-row percentages would weight a 3-day trade the same as a 300-day one and
     * would inherit the engine's 999 % cap — both produce a plausible but wrong headline number.
     */
    private static final class Agg {
        private double budgetedDays;
        private double countedDays;
        private double cost;

        void add(BigDecimal budgeted, BigDecimal actual, BigDecimal hiddenSides,
                 BigDecimal untracked, BigDecimal costImplication) {
            // Counted = deployed − measured-under-the-other-side − no-norm, matching the tab exactly.
            double counted = d(actual) - d(hiddenSides) - d(untracked);
            if (budgeted == null || counted <= 0) {
                return;   // no norm resolved, or nothing counted — not comparable, so not pooled
            }
            budgetedDays += budgeted.doubleValue();
            countedDays += counted;
            cost += d(costImplication);
        }

        Double pct() {
            return pct(budgetedDays, countedDays);
        }

        static Double pct(double budgetedDays, double countedDays) {
            return countedDays > 0 ? budgetedDays / countedDays * 100.0 : null;
        }
    }

    @Override
    public List<ActivityEfficiency> cumulativeByActivity(UUID projectId) {
        List<ActivityEfficiency> out = new ArrayList<>();
        for (CapacityUtilizationReportService.ActivityEff a : reportService.cumulativeByActivity(projectId)) {
            out.add(new ActivityEfficiency(a.workActivityId(), a.activityName(), a.resourceType(),
                    a.budgetDays(), a.actualDays(), a.efficiencyPct()));
        }
        return out;
    }

    @Override
    public List<RoleEfficiency> cumulativeByRole(UUID projectId) {
        // groupBy=ROLE, normType=null (both manpower + equipment), supervisorUserId=null (all supervisors),
        // workDays=26 (default). The cumulative RolePeriod on each role row is project-to-date.
        CapacityUtilizationReport report = reportService.build(
                projectId, PROJECT_START, LocalDate.now(), "ROLE", null, null, 26);
        List<RoleEfficiency> out = new ArrayList<>();
        collect(report.manpower(), "MANPOWER", out);
        collect(report.equipment(), "EQUIPMENT", out);
        return out;
    }

    private void collect(Section section, String type, List<RoleEfficiency> out) {
        if (section == null || section.rows() == null) {
            return;
        }
        for (RoleRow row : section.rows()) {
            RolePeriod c = row.cumulative();
            if (c == null) {
                continue;
            }
            // Counted = the tracked resource-days efficiency divides by (deployed − measured-under-the-
            // other-side − no-norm) — the same figure the Capacity Util. tab shows as "Counted".
            double counted = d(c.actualDays()) - d(c.actualDaysOnHiddenSides()) - d(c.actualDaysUntracked());
            out.add(new RoleEfficiency(
                    type, row.roleName(),
                    d(c.budgetDays()), d(c.actualDays()), counted,
                    d(c.utilizationPct()), d(row.ratePerDay()), d(c.costImplication())));
        }
    }

    private static double d(BigDecimal b) {
        return b == null ? 0.0 : b.doubleValue();
    }
}

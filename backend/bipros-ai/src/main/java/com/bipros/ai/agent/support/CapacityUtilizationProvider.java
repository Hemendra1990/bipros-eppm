package com.bipros.ai.agent.support;

import java.util.List;
import java.util.UUID;

/**
 * Port (Dependency Inversion) over the canonical capacity-utilization computation. The heavy per-role
 * efficiency + cost-implication math — productivity-norm resolution plus the SERIES/PARALLEL/SUBSTITUTE
 * allocator — lives in {@code bipros-reporting}'s {@code CapacityUtilizationReportService}, which the AI
 * module cannot depend on (that edge is a cycle: reporting → ai). {@code bipros-reporting} provides an
 * adapter implementing this interface, so the agent reads the EXACT Capacity Util. tab figures without
 * duplicating (and eventually drifting from) the logic. Injected as {@code Optional} — a slim context
 * without the reporting module simply degrades to no efficiency findings.
 */
public interface CapacityUtilizationProvider {

    /** Per-role efficiency + cost implication, cumulative project-to-date, all supervisors. */
    List<RoleEfficiency> cumulativeByRole(UUID projectId);

    /**
     * Per-(work-activity, resource-type) efficiency vs productivity norm, cumulative project-to-date —
     * the SAME allocator + norm resolution the per-role figures use, only regrouped by work-activity.
     * Consumed by the Productivity Analysis agent. Activities with no norm-resolved days are omitted.
     */
    List<ActivityEfficiency> cumulativeByActivity(UUID projectId);

    /**
     * Per-supervisor cumulative resource efficiency, rolled up across every trade and equipment type
     * that supervisor's DPRs touched — the same engine, window and "counted days" denominator the
     * Capacity Util. → Supervisor Performance tab renders. Consumed by the Supervisor Performance
     * agent so its efficiency figures cannot drift from that tab. Supervisors with no norm-resolved
     * days are returned with null percentages rather than a misleading zero.
     */
    List<SupervisorEfficiency> cumulativeBySupervisor(UUID projectId, List<UUID> supervisorUserIds);

    /**
     * One supervisor's cumulative efficiency across all their resources.
     *
     * <p>Percentages are {@code null}, never {@code 0}, when that side resolved no norm — a supervisor
     * who ran no equipment is not "0% efficient on equipment".
     *
     * @param supervisorUserId        the DPR's supervisor user id
     * @param manpowerEfficiencyPct   Σ budgeted man-days ÷ Σ counted man-days × 100 (null when none counted)
     * @param equipmentEfficiencyPct  same for equipment (null when none counted)
     * @param overallEfficiencyPct    both sides pooled — Σ budgeted ÷ Σ counted × 100 (null when none counted)
     * @param budgetedDays            Σ budgeted resource-days behind {@code overallEfficiencyPct}
     * @param countedDays             Σ counted resource-days behind {@code overallEfficiencyPct}
     * @param costImplication         Σ (counted − budgeted) × rate; {@code > 0} = overrun
     */
    record SupervisorEfficiency(UUID supervisorUserId,
                                Double manpowerEfficiencyPct,
                                Double equipmentEfficiencyPct,
                                Double overallEfficiencyPct,
                                double budgetedDays, double countedDays, double costImplication) {
    }

    /**
     * One work-activity's cumulative output-vs-norm for one resource type.
     *
     * @param workActivityId library work-activity id
     * @param activityName   work-activity display name
     * @param resourceType   "MANPOWER" | "EQUIPMENT"
     * @param budgetDays     Σ (allocated qty ÷ productivity norm) — resource-days the output should have needed
     * @param actualDays     tracked (norm-resolved) resource-days actually deployed
     * @param efficiencyPct  budgetDays ÷ actualDays × 100 (output vs the productivity norm)
     */
    record ActivityEfficiency(UUID workActivityId, String activityName, String resourceType,
                              double budgetDays, double actualDays, double efficiencyPct) {
    }

    /**
     * One role's cumulative capacity figures, exactly as the Capacity Util. tab renders them.
     *
     * @param resourceType    "MANPOWER" | "EQUIPMENT"
     * @param roleName        e.g. "Helper", "Excavator"
     * @param budgetedDays    Σ (executed qty ÷ productivity norm) — the resource-days the work should have needed
     * @param actualDays      Σ nos deployed (headcount-days actually on site)
     * @param countedDays     the tracked resource-days efficiency divides by (deployed − measured-under-the-other-side
     *                        − no-norm) — the Capacity Util. tab's "Counted"; efficiencyPct = budgetedDays ÷ countedDays × 100
     * @param efficiencyPct   budgetedDays ÷ countedDays × 100 (output vs the productivity norm)
     * @param ratePerDay      DPR-deployment-weighted day rate
     * @param costImplication (countedDays − budgetedDays) × ratePerDay; {@code > 0} = cost overrun, {@code < 0} = saved
     */
    record RoleEfficiency(String resourceType, String roleName,
                          double budgetedDays, double actualDays, double countedDays,
                          double efficiencyPct, double ratePerDay, double costImplication) {
    }
}

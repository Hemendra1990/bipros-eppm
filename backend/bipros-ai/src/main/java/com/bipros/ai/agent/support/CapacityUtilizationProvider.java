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
     * One role's cumulative capacity figures, exactly as the Capacity Util. tab renders them.
     *
     * @param resourceType    "MANPOWER" | "EQUIPMENT"
     * @param roleName        e.g. "Helper", "Excavator"
     * @param budgetedDays    Σ (executed qty ÷ productivity norm) — the resource-days the work should have needed
     * @param actualDays      Σ nos deployed (headcount-days actually on site)
     * @param efficiencyPct   budgetedDays ÷ tracked actualDays × 100 (output vs the productivity norm)
     * @param ratePerDay      DPR-deployment-weighted day rate
     * @param costImplication (actualDays − budgetedDays) × ratePerDay; {@code > 0} = cost overrun, {@code < 0} = saved
     */
    record RoleEfficiency(String resourceType, String roleName,
                          double budgetedDays, double actualDays,
                          double efficiencyPct, double ratePerDay, double costImplication) {
    }
}

package com.bipros.reporting.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Primavera P6-style cost variance report. Combines:
 * <ul>
 *   <li>Project-level EVM (BAC / EV / AC / CV / CPI / EAC / VAC) from the latest
 *       {@code EvmCalculation} where activityId+wbsNodeId are both null.</li>
 *   <li>Per-WBS rollups from the latest EvmCalculation per wbsNodeId, plus the WBS node's
 *       configured budget for context.</li>
 *   <li>Per-activity baseline-cost variance: baseline planned cost (from BaselineActivity)
 *       vs current planned cost (sum of ResourceAssignment.plannedCost) and actual cost,
 *       with derived "estimate variance" (cur planned − bl planned, scope/re-estimate creep)
 *       and "burn variance" (actual − bl planned × % complete, true overrun).</li>
 * </ul>
 *
 * <p>Sign convention (matches P6): positive ₹ = over budget, negative = under.
 */
public record CostVarianceReport(
    ProjectInfo project,
    BaselineInfo baseline,
    LocalDate dataDate,
    Summary summary,
    List<WbsRow> wbsRows,
    List<ActivityRow> activityRows
) {

  public record ProjectInfo(UUID id, String code, String name) {}

  public record BaselineInfo(UUID id, String name, LocalDate baselineDate) {}

  public record Summary(
      BigDecimal budgetAtCompletion,
      BigDecimal plannedValue,
      BigDecimal earnedValue,
      BigDecimal actualCost,
      BigDecimal scheduleVariance,
      BigDecimal costVariance,
      Double schedulePerformanceIndex,
      Double costPerformanceIndex,
      BigDecimal estimateAtCompletion,
      BigDecimal varianceAtCompletion,
      Double performancePercentComplete) {}

  public record WbsRow(
      UUID wbsNodeId,
      String wbsCode,
      String wbsName,
      Integer wbsLevel,
      BigDecimal budget,             // configured WbsNode.budgetCrores × 1cr (₹)
      BigDecimal plannedValue,
      BigDecimal earnedValue,
      BigDecimal actualCost,
      BigDecimal costVariance,
      Double costPerformanceIndex) {}

  public record ActivityRow(
      UUID activityId,
      String code,
      String name,
      String activityType,
      String status,
      Double percentComplete,
      BigDecimal baselinePlannedCost,
      BigDecimal currentPlannedCost,
      BigDecimal actualCost,
      BigDecimal estimateVariance,    // cur planned - bl planned (scope creep)
      BigDecimal burnVariance) {}     // actual - bl planned × %complete (overrun)
}

package com.bipros.ai.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated performance picture for one supervisor over a date window. Covers
 * activity scope (what they manage), cost rollups, EVM (CPI/SPI), and the DPR /
 * resource-output activity captured during the window.
 *
 * <p>Computed by {@code SupervisorPerformanceCalculator}. Consumed by both
 * {@code SupervisorTool} (single supervisor) and {@code CompareSupervisorsTool}
 * (multi-supervisor table). The metric definitions live in one place so the
 * two tools can never drift.
 */
public record SupervisorPerformance(
    UUID supervisorResourceId,
    String supervisorCode,
    String supervisorName,
    LocalDate dateFrom,
    LocalDate dateTo,
    int teamSize,
    ActivityScope activityScope,
    CostRollup costRollup,
    EvmRollup evmRollup,
    DprRollup dprRollup,
    List<ActivityTopRollup> topActivities,
    List<MemberRollup> topMembers) {

  public record ActivityScope(
      int total,
      int notStarted,
      int inProgress,
      int completed,
      int delayed,
      Double avgPctComplete,
      List<String> topCodes) {}

  /**
   * Cost rollup for the supervisor's activities. {@code overriddenFormulaCodes} lists
   * any formula codes (e.g. {@code RES_ACTUAL_COST}, {@code SUP_COST_VARIANCE_PCT}) that
   * have an active project-level override applied for this project at compute time. Empty
   * — never null — when all values came from default master formulas. The AI tools surface
   * this list so the model can disclose the override to the user.
   */
  public record CostRollup(
      BigDecimal planned,
      BigDecimal actual,
      BigDecimal remaining,
      BigDecimal atCompletion,
      BigDecimal variance,
      Double variancePct,
      List<String> overriddenFormulaCodes) {}

  /**
   * EV source: {@code "evm"} when populated from {@code evm_calculations} (latest
   * per activity), {@code "expense"} when fallen back to {@code cost.activity_expenses}
   * (EV proxy = budgeted_cost × percent_complete/100), {@code "mixed"} when both,
   * {@code "none"} when neither was available.
   */
  public record EvmRollup(
      BigDecimal bac,
      BigDecimal pv,
      BigDecimal ev,
      BigDecimal ac,
      Double cpi,
      Double spi,
      BigDecimal cv,
      BigDecimal sv,
      int activityCountWithEvm,
      String evSource) {}

  public record DprRollup(
      int dprCount,
      int distinctReportDates,
      int distinctActivitiesTouched,
      BigDecimal totalQtyExecuted,
      BigDecimal totalHoursWorkedByTeam,
      BigDecimal totalDaysWorkedByTeam,
      String matchSource /* "activity_id" or "name" or "mixed" */) {}

  public record ActivityTopRollup(
      UUID activityId,
      String activityCode,
      String activityName,
      BigDecimal qtyExecuted,
      Integer dprCount,
      BigDecimal plannedCost,
      BigDecimal actualCost) {}

  public record MemberRollup(
      UUID resourceId,
      String resourceCode,
      String resourceName,
      String roleName,
      BigDecimal qtyExecuted,
      BigDecimal hoursWorked,
      BigDecimal daysWorked,
      Integer activitiesTouched) {}
}

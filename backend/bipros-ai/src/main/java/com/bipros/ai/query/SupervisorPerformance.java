package com.bipros.ai.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated performance picture for one supervisor over a date window.
 * Combines DPR submissions (where {@code supervisor_name} matches the
 * supervisor's name) with daily resource outputs (when the supervisor's
 * subordinates appear as the resource on {@code daily_activity_resource_outputs}).
 */
public record SupervisorPerformance(
    UUID supervisorResourceId,
    String supervisorCode,
    String supervisorName,
    LocalDate dateFrom,
    LocalDate dateTo,
    int teamSize,
    int dprCount,
    int distinctActivities,
    int distinctReportDates,
    BigDecimal totalQtyExecuted,
    BigDecimal totalHoursWorked,
    BigDecimal totalDaysWorked,
    BigDecimal plannedCostByTeam,
    BigDecimal actualCostByTeam,
    BigDecimal costVarianceByTeam,
    List<ActivityRollup> topActivities,
    List<MemberRollup> topMembers) {

  public record ActivityRollup(
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

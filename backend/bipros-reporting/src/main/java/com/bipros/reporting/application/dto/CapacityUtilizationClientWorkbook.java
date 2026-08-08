package com.bipros.reporting.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Data for the client-format "Resource Capacity Utilization Report" workbook (Plant
 * utilization / Manpower utilization / SUMMARY sheets): each resource role with its
 * per-work-activity breakdown, Day + Month buckets only — the layout of the client's
 * Capacity_Utilization.xlsx template.
 *
 * <p>Numbers come from the SAME accumulator pass as the Capacity Util. tab
 * ({@code CapacityUtilizationReportService.accumulateByRole}) — this DTO only regroups
 * them per (role → activity) instead of collapsing to one row per role.
 */
public record CapacityUtilizationClientWorkbook(
    UUID projectId,
    LocalDate fromDate,
    LocalDate toDate,
    /** Anchor for the "Actual (for the day)" columns: To date, or today when the range includes today. */
    LocalDate referenceDate,
    int workDays,
    Section equipment,
    Section manpower) {

  public record Section(List<RoleGroup> groups) {}

  public record RoleGroup(
      UUID roleId,
      String roleName,
      Rollup day,
      Rollup month,
      List<ActivityLine> lines) {}

  /** Group-banner aggregate. Qty is deliberately absent — summing mixed units is meaningless. */
  public record Rollup(BigDecimal budgetDays, BigDecimal actualDays, BigDecimal utilizationPct) {}

  /**
   * One task row under a resource. All-null actuals = catalogue row (the role has a
   * productivity norm for this work activity but no DPR data in the window — rendered
   * blank like the client template). {@code normOutputPerDay} null = untracked line
   * (deployment recorded but no norm → no budget / util, matching the tab's footnote rows).
   */
  public record ActivityLine(
      UUID workActivityId,
      String activityName,
      String unit,
      BigDecimal normOutputPerDay,
      BigDecimal dayQty,
      BigDecimal dayBudgetDays,
      BigDecimal dayActualDays,
      BigDecimal dayUtilizationPct,
      BigDecimal monthQty,
      BigDecimal monthBudgetDays,
      BigDecimal monthActualDays,
      BigDecimal monthUtilizationPct,
      /** Achieved output per tracked day for the month (= qty ÷ tracked actual days) — the
       *  client sheet's "Site conditions (Actual) Prod'vity Norm" / "Actual Prod'vity" column. */
      BigDecimal actualProductivityMonth) {}
}

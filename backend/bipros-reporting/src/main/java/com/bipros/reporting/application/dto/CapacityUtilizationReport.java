package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Capacity Utilization report — mirrors the Excel "Plant utilization" / "Manpower utilization"
 * sheets. Each row represents one (work-activity × resource-group) pair, with budget vs actual
 * metrics for the day, the month, and cumulative.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapacityUtilizationReport(
    UUID projectId,
    LocalDate fromDate,
    LocalDate toDate,
    String groupBy,   // RESOURCE_TYPE | RESOURCE
    String normType,  // MANPOWER | EQUIPMENT | null
    List<Row> rows
) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Row(
      GroupKey groupKey,
      WorkActivityRef workActivity,
      Budgeted budgeted,
      Period forTheDay,
      Period forTheMonth,
      Period cumulative
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record GroupKey(
      UUID resourceTypeDefId,
      UUID resourceId,
      String displayLabel
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record WorkActivityRef(
      UUID id,
      String code,
      String name,
      String defaultUnit
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Budgeted(
      BigDecimal outputPerDay,
      String source  // SPECIFIC_RESOURCE | RESOURCE_TYPE | RESOURCE_LEGACY | NONE
  ) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Period(
      BigDecimal qty,
      BigDecimal budgetedDays,
      BigDecimal actualDays,
      BigDecimal actualOutputPerDay,
      BigDecimal utilizationPct
  ) {}
}

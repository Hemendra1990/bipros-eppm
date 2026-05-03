package com.bipros.reporting.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * BOQ-row pivot for the "DPR" sheet. Each {@link Item} pairs a contract BOQ line with its month-
 * to-date achieved quantity / amount and a per-day quantity vector built from
 * {@code project.daily_progress_reports} grouped by {@code report_date} where {@code boq_item_no}
 * matches the BOQ line.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DprMatrix(
    UUID projectId,
    YearMonth month,
    int daysInMonth,
    String projectName,
    String contractor,
    String engineer,
    String client,
    List<Item> items
) {
  public record Item(
      String itemNo,
      String description,
      String unit,
      BigDecimal revisedRate,
      BigDecimal projectionQty,
      BigDecimal projectionAmount,
      BigDecimal achievedQty,
      BigDecimal achievedAmount,
      BigDecimal[] qtyPerDay
  ) {}
}

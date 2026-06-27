package com.bipros.reporting.materialconsumption;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Top-level response for the Material Consumption Report. {@code totals} holds global sums
 * (actualCost / weighted-average wastagePercent_avg); {@code alertCounts} is keyed by alert code;
 * {@code supervisors} is the distinct set of supervisors resolved across the data (for the filter
 * dropdown), independent of the current supervisor filter.
 */
public record MaterialConsumptionReportResponse(
    LocalDate from,
    LocalDate to,
    String groupBy,
    List<MaterialConsumptionRow> rows,
    Map<String, BigDecimal> totals,
    Map<String, Integer> alertCounts,
    List<SupervisorOption> supervisors) {

  public record SupervisorOption(UUID userId, String name) {}
}

package com.bipros.project.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line of the Daily Cost Report (Section B of the Excel "Daily Cost Report" sheet).
 * All cost / variance fields are derived server-side by the {@code DailyCostReportService};
 * clients should not recompute.
 */
public record DailyCostReportRow(
    UUID dprId,
    LocalDate date,
    String activity,
    BigDecimal qtyExecuted,
    String unit,
    String boqItemNo,
    BigDecimal budgetedUnitRate,
    BigDecimal actualUnitRate,
    BigDecimal budgetedCost,
    BigDecimal actualCost,
    BigDecimal variance,
    BigDecimal variancePercent,
    String supervisor
) {}

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
    UUID boqItemId,
    String boqItemNo,
    BigDecimal budgetedUnitRate,
    BigDecimal actualUnitRate,
    BigDecimal budgetedCost,
    BigDecimal actualCost,
    BigDecimal variance,
    BigDecimal variancePercent,
    /**
     * Estimate to Complete projected onto this row from the activity's most recent
     * {@code EvmCalculation}, proportional to the row's share of the activity's total actual
     * cost. {@code null} when no EvmCalculation exists for the activity or when the share
     * cannot be computed (zero activity actual).
     */
    BigDecimal etc,
    /** Estimate at Completion projected onto this row — see {@link #etc} for the share math. */
    BigDecimal eac,
    String supervisor,
    /**
     * Stage 4 (A8): false when the row is attributed to a non-measurement operation of a
     * WEIGHTED-split BOQ line — its qty is execution progress, not billable revenue, and pricing
     * it at the line rate would double margin income. True for legacy rows (null operation),
     * measurement-operation rows, unsplit lines and QUANTITY_PARTITION children.
     */
    boolean countsAsRevenue
) {}

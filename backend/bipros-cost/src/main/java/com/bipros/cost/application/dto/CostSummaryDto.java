package com.bipros.cost.application.dto;

import java.math.BigDecimal;

/**
 * Project-level cost snapshot. Core fields (totalBudget / totalActual / …) roll up
 * {@code ActivityExpense} rows; the PMS MasterData extension fields ({@code
 * materialProcurementCost}, {@code materialIssuedCost}, {@code openStockValue}) surface
 * material-related amounts from the {@code MaterialStock} / {@code GoodsReceiptNote} /
 * {@code MaterialIssue} entities so the cost summary reflects the whole procurement cycle
 * without having to shoehorn it into the activity-expense ledger.
 */
public record CostSummaryDto(
        BigDecimal totalBudget,
        BigDecimal totalActual,
        BigDecimal totalRemaining,
        BigDecimal atCompletion,
        BigDecimal costVariance,
        BigDecimal costPerformanceIndex,
        int expenseCount,
        /** Σ of {@code GoodsReceiptNote.amount} for the project — money spent on material
         *  procurement to date (inventory + consumed). */
        BigDecimal materialProcurementCost,
        /** Running value of {@code MaterialStock.stockValue} still on the shelf. */
        BigDecimal openStockValue,
        /** Estimated cost of material issued to site (procurement − stock on hand). */
        BigDecimal materialIssuedCost
) {

    public static CostSummaryDto of(BigDecimal totalBudget, BigDecimal totalActual,
                                     BigDecimal totalRemaining, BigDecimal atCompletion,
                                     int expenseCount) {
        return of(totalBudget, totalActual, totalRemaining, atCompletion, expenseCount,
            null, null, null);
    }

    public static CostSummaryDto of(BigDecimal totalBudget, BigDecimal totalActual,
                                     BigDecimal totalRemaining, BigDecimal atCompletion,
                                     int expenseCount,
                                     BigDecimal materialProcurementCost,
                                     BigDecimal openStockValue,
                                     BigDecimal materialIssuedCost) {
        var cv = totalBudget.subtract(totalActual);
        // CPI = EV / AC. When there is no actual cost (AC = 0) CPI is undefined; returning 1.0
        // incorrectly reads as "on budget" on dashboards. Prefer null so consumers can render
        // "N/A" for empty projects (BUG-010).
        BigDecimal cpi = totalActual.compareTo(BigDecimal.ZERO) > 0
                ? totalBudget.divide(totalActual, 4, java.math.RoundingMode.HALF_UP)
                : null;
        return new CostSummaryDto(totalBudget, totalActual, totalRemaining, atCompletion,
            cv, cpi, expenseCount,
            materialProcurementCost, openStockValue, materialIssuedCost);
    }
}

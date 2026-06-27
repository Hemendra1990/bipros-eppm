package com.bipros.cost.application.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        BigDecimal materialIssuedCost,
        /** P6-style project-level original budget (immutable). */
        BigDecimal projectOriginalBudget,
        /** P6-style project-level current budget (original + approved changes). */
        BigDecimal projectCurrentBudget,
        // --- EVM (true earned-value) ---
        BigDecimal bac,
        BigDecimal plannedCost,
        BigDecimal earnedValue,
        BigDecimal plannedValue,
        BigDecimal costPercentComplete,
        BigDecimal scheduleVariance,
        BigDecimal schedulePerformanceIndex,
        BigDecimal estimateAtCompletion,
        BigDecimal estimateToComplete,
        BigDecimal varianceAtCompletion,
        BigDecimal contractValue,
        BigDecimal toCompletePerformanceIndex
) {

    private static final RoundingMode HALF_UP = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * True-EVM factory. CV/CPI/SV/SPI/EAC/ETC/VAC are derived from EV (= BAC × cost%complete) and
     * PV (= BAC × planned%complete). See docs/calculations/costs-tab.md.
     */
    public static CostSummaryDto ofEvm(
            BigDecimal bac, BigDecimal plannedCost, BigDecimal actualCost,
            BigDecimal boqBudgetedTotal, BigDecimal boqEarnedValue,
            BigDecimal plannedPercentComplete, int expenseCount,
            BigDecimal materialProcurementCost, BigDecimal openStockValue, BigDecimal materialIssuedCost,
            BigDecimal projectOriginalBudget, BigDecimal contractValue) {

        BigDecimal safeBac = bac != null ? bac : ZERO;
        BigDecimal ac = actualCost != null ? actualCost : ZERO;
        BigDecimal budgetedTotal = boqBudgetedTotal != null ? boqBudgetedTotal : ZERO;
        BigDecimal earned = boqEarnedValue != null ? boqEarnedValue : ZERO;
        BigDecimal plannedPct = plannedPercentComplete != null ? plannedPercentComplete : ZERO;

        BigDecimal costPct = budgetedTotal.signum() > 0
                ? earned.divide(budgetedTotal, 6, HALF_UP) : ZERO;
        BigDecimal ev = safeBac.multiply(costPct);
        BigDecimal pv = safeBac.multiply(plannedPct);

        BigDecimal cv = ev.subtract(ac);
        BigDecimal cpi = ac.signum() > 0 ? ev.divide(ac, 4, HALF_UP) : null;
        BigDecimal sv = ev.subtract(pv);
        BigDecimal spi = pv.signum() > 0 ? ev.divide(pv, 4, HALF_UP) : null;
        BigDecimal eac = (cpi != null && cpi.signum() > 0) ? safeBac.divide(cpi, 2, HALF_UP) : safeBac;
        BigDecimal etc = eac.subtract(ac);
        BigDecimal vac = safeBac.subtract(eac);
        BigDecimal tcpiDenom = safeBac.subtract(ac);
        BigDecimal tcpi = tcpiDenom.signum() != 0
                ? safeBac.subtract(ev).divide(tcpiDenom, 4, HALF_UP) : null;

        return new CostSummaryDto(
                plannedCost, ac, etc, eac, cv, cpi, expenseCount,
                materialProcurementCost, openStockValue, materialIssuedCost,
                projectOriginalBudget, safeBac,
                safeBac, plannedCost, ev, pv, costPct, sv, spi, eac, etc, vac, contractValue, tcpi);
    }

    public static CostSummaryDto of(BigDecimal totalBudget, BigDecimal totalActual,
                                     BigDecimal totalRemaining, BigDecimal atCompletion,
                                     int expenseCount) {
        return of(totalBudget, totalActual, totalRemaining, atCompletion, expenseCount,
            null, null, null, null, null);
    }

    public static CostSummaryDto of(BigDecimal totalBudget, BigDecimal totalActual,
                                     BigDecimal totalRemaining, BigDecimal atCompletion,
                                     int expenseCount,
                                     BigDecimal materialProcurementCost,
                                     BigDecimal openStockValue,
                                     BigDecimal materialIssuedCost) {
        return of(totalBudget, totalActual, totalRemaining, atCompletion, expenseCount,
            materialProcurementCost, openStockValue, materialIssuedCost, null, null);
    }

    public static CostSummaryDto of(BigDecimal totalBudget, BigDecimal totalActual,
                                     BigDecimal totalRemaining, BigDecimal atCompletion,
                                     int expenseCount,
                                     BigDecimal materialProcurementCost,
                                     BigDecimal openStockValue,
                                     BigDecimal materialIssuedCost,
                                     BigDecimal projectOriginalBudget,
                                     BigDecimal projectCurrentBudget) {
        var cv = totalBudget.subtract(totalActual);
        // CPI = EV / AC. When there is no actual cost (AC = 0) CPI is undefined; returning 1.0
        // incorrectly reads as "on budget" on dashboards. Prefer null so consumers can render
        // "N/A" for empty projects (BUG-010).
        BigDecimal cpi = totalActual.compareTo(BigDecimal.ZERO) > 0
                ? totalBudget.divide(totalActual, 4, java.math.RoundingMode.HALF_UP)
                : null;
        return new CostSummaryDto(totalBudget, totalActual, totalRemaining, atCompletion,
            cv, cpi, expenseCount,
            materialProcurementCost, openStockValue, materialIssuedCost,
            projectOriginalBudget, projectCurrentBudget,
            null, null, null, null, null, null, null, null, null, null, null, null);
    }
}

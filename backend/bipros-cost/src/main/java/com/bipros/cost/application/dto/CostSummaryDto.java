package com.bipros.cost.application.dto;

import java.math.BigDecimal;

public record CostSummaryDto(
        BigDecimal totalBudget,
        BigDecimal totalActual,
        BigDecimal totalRemaining,
        BigDecimal atCompletion,
        BigDecimal costVariance,
        BigDecimal costPerformanceIndex,
        int expenseCount
) {
    public static CostSummaryDto of(BigDecimal totalBudget, BigDecimal totalActual,
                                     BigDecimal totalRemaining, BigDecimal atCompletion,
                                     int expenseCount) {
        var cv = totalBudget.subtract(totalActual);
        var cpi = totalActual.compareTo(BigDecimal.ZERO) > 0
                ? totalBudget.divide(totalActual, 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        return new CostSummaryDto(totalBudget, totalActual, totalRemaining, atCompletion, cv, cpi, expenseCount);
    }
}

package com.bipros.evm.application.dto;

import java.math.BigDecimal;

public record EvmSummaryResponse(
        BigDecimal budgetAtCompletion,
        BigDecimal plannedValue,
        BigDecimal earnedValue,
        BigDecimal actualCost,
        BigDecimal scheduleVariance,
        BigDecimal costVariance,
        Double schedulePerformanceIndex,
        Double costPerformanceIndex,
        Double toCompletePerformanceIndex,
        BigDecimal estimateAtCompletion,
        BigDecimal estimateToComplete,
        BigDecimal varianceAtCompletion,
        String evmTechnique,
        String etcMethod,
        Double performancePercentComplete
) {
}

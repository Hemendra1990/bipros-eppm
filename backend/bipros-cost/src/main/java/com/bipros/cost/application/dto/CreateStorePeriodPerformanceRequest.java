package com.bipros.cost.application.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateStorePeriodPerformanceRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        @NotNull(message = "Financial Period ID is required")
        UUID financialPeriodId,

        UUID activityId,

        BigDecimal actualLaborCost,

        BigDecimal actualNonlaborCost,

        BigDecimal actualMaterialCost,

        BigDecimal actualExpenseCost,

        Double actualLaborUnits,

        Double actualNonlaborUnits,

        Double actualMaterialUnits,

        BigDecimal earnedValueCost,

        BigDecimal plannedValueCost
) {
}

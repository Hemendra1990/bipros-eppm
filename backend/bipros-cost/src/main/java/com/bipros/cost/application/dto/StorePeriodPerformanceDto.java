package com.bipros.cost.application.dto;

import com.bipros.cost.domain.entity.StorePeriodPerformance;

import java.math.BigDecimal;
import java.util.UUID;

public record StorePeriodPerformanceDto(
        UUID id,
        UUID projectId,
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
    public static StorePeriodPerformanceDto from(StorePeriodPerformance entity) {
        return new StorePeriodPerformanceDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getFinancialPeriodId(),
                entity.getActivityId(),
                entity.getActualLaborCost(),
                entity.getActualNonlaborCost(),
                entity.getActualMaterialCost(),
                entity.getActualExpenseCost(),
                entity.getActualLaborUnits(),
                entity.getActualNonlaborUnits(),
                entity.getActualMaterialUnits(),
                entity.getEarnedValueCost(),
                entity.getPlannedValueCost()
        );
    }
}

package com.bipros.evm.application.dto;

import com.bipros.evm.domain.entity.EvmCalculation;
import com.bipros.evm.domain.entity.EtcMethod;
import com.bipros.evm.domain.entity.EvmTechnique;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EvmCalculationResponse(
        UUID id,
        UUID projectId,
        UUID wbsNodeId,
        UUID activityId,
        UUID financialPeriodId,
        LocalDate dataDate,
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
        EvmTechnique evmTechnique,
        EtcMethod etcMethod,
        Double performancePercentComplete
) {
    public static EvmCalculationResponse from(EvmCalculation entity) {
        return new EvmCalculationResponse(
                entity.getId(),
                entity.getProjectId(),
                entity.getWbsNodeId(),
                entity.getActivityId(),
                entity.getFinancialPeriodId(),
                entity.getDataDate(),
                entity.getBudgetAtCompletion(),
                entity.getPlannedValue(),
                entity.getEarnedValue(),
                entity.getActualCost(),
                entity.getScheduleVariance(),
                entity.getCostVariance(),
                entity.getSchedulePerformanceIndex(),
                entity.getCostPerformanceIndex(),
                entity.getToCompletePerformanceIndex(),
                entity.getEstimateAtCompletion(),
                entity.getEstimateToComplete(),
                entity.getVarianceAtCompletion(),
                entity.getEvmTechnique(),
                entity.getEtcMethod(),
                entity.getPerformancePercentComplete()
        );
    }
}

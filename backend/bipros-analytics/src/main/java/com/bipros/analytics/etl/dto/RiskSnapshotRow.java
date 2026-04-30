package com.bipros.analytics.etl.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row shape for {@code fact_risk_snapshot_daily}. Carries every column captured today
 * plus the P6 fields added in the analytics expansion (residual score, exposure dates,
 * post-response impact, owner, category, type, trend, response strategy).
 */
@Builder
public record RiskSnapshotRow(
        UUID projectId,
        UUID riskId,
        LocalDate date,
        Float probability,
        BigDecimal impactCost,
        Integer impactDays,
        String rag,
        String status,
        BigDecimal monteCarloP50,
        BigDecimal monteCarloP80,
        BigDecimal monteCarloP95,
        Double riskScore,
        Double residualRiskScore,
        String riskType,
        UUID ownerId,
        UUID categoryId,
        Float postResponseProbability,
        Integer postResponseImpactCost,
        Integer postResponseImpactSchedule,
        BigDecimal preResponseExposureCost,
        BigDecimal postResponseExposureCost,
        LocalDate exposureStartDate,
        LocalDate exposureFinishDate,
        String responseType,
        String trend,
        LocalDate identifiedDate,
        UUID identifiedById
) {
}

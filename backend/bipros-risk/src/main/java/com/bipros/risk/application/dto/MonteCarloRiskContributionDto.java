package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.MonteCarloRiskContribution;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloRiskContributionDto {
    private UUID id;
    private UUID simulationId;
    private UUID riskId;
    private String riskCode;
    private String riskTitle;
    private Integer occurrences;
    private Double occurrenceRate;
    private Double meanDurationImpact;
    private BigDecimal meanCostImpact;
    private String affectedActivityIds;

    public static MonteCarloRiskContributionDto from(MonteCarloRiskContribution c) {
        return new MonteCarloRiskContributionDto(
            c.getId(), c.getSimulationId(), c.getRiskId(),
            c.getRiskCode(), c.getRiskTitle(),
            c.getOccurrences(), c.getOccurrenceRate(),
            c.getMeanDurationImpact(), c.getMeanCostImpact(),
            c.getAffectedActivityIds());
    }
}

package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.RiskScoringConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for the per-project scoring configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskScoringConfigDto {
    private UUID id;
    private UUID projectId;
    private RiskScoringConfig.ScoringMethod scoringMethod;
    private Boolean active;
}

package com.bipros.risk.application.dto;

import com.bipros.risk.domain.model.DistributionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloRunRequest {

    @Min(100) @Max(100_000)
    private Integer iterations = 10_000;

    private DistributionType defaultDistribution = DistributionType.TRIANGULAR;

    /** 0..0.9 (e.g. 0.2 = ±20% band around originalDuration when no PERT row exists). */
    private Double fallbackVariancePct = 0.2;

    /** Reserved for Phase 3 (risk-register drivers). */
    private Boolean enableRisks = false;

    /** Optional — set for reproducible runs. */
    private Long randomSeed;
}

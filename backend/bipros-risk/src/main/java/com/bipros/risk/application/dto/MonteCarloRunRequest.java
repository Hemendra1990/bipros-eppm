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

    /**
     * When true, the project's open risk register is layered into the simulation as Bernoulli
     * drivers: each open risk with probability &gt; 0, at least one resolvable affected activity
     * (via the RiskActivityAssignment link table or the legacy affectedActivities field), and a
     * non-zero schedule/cost impact fires per iteration, adding its sampled days/cost. Defaults to
     * false, so a default run reflects schedule uncertainty only and excludes the register.
     */
    private Boolean enableRisks = false;

    /** Optional — set for reproducible runs. */
    private Long randomSeed;
}

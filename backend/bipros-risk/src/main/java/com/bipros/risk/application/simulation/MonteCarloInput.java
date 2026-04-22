package com.bipros.risk.application.simulation;

import com.bipros.risk.domain.model.DistributionType;

import java.util.UUID;

/**
 * All user-controlled inputs for a single Monte Carlo run.
 *
 * @param projectId            target project; must have an active Baseline
 * @param iterations           100..100000
 * @param defaultDistribution  distribution used when an activity has no per-activity
 *                             override — applied to both PERT-derived and fallback bands
 * @param fallbackVariancePct  fractional variance (e.g. 0.2 = ±20%) used when neither an
 *                             {@code ActivityDurationDistribution} override nor a
 *                             {@code PertEstimate} row exists for an activity
 * @param enableRisks          reserved for Phase 3; currently ignored
 * @param randomSeed           optional — if non-null, makes the run reproducible
 */
public record MonteCarloInput(
    UUID projectId,
    int iterations,
    DistributionType defaultDistribution,
    double fallbackVariancePct,
    boolean enableRisks,
    Long randomSeed
) {
    public MonteCarloInput {
        if (iterations < 100 || iterations > 100_000) {
            throw new IllegalArgumentException("iterations must be in [100, 100000]: " + iterations);
        }
        if (fallbackVariancePct < 0 || fallbackVariancePct > 0.9) {
            throw new IllegalArgumentException("fallbackVariancePct must be in [0, 0.9]: " + fallbackVariancePct);
        }
        if (defaultDistribution == null) defaultDistribution = DistributionType.TRIANGULAR;
    }

    public static MonteCarloInput defaultsFor(UUID projectId, int iterations) {
        return new MonteCarloInput(projectId, iterations, DistributionType.TRIANGULAR, 0.2, false, null);
    }
}

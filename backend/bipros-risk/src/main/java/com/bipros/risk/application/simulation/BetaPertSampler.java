package com.bipros.risk.application.simulation;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;

/**
 * Beta-PERT sampler: the standard three-point estimate distribution used by
 * Primavera Risk Analysis / Pertmaster. Uses the classic shape parameter 4
 * which reproduces the PERT expected value (optimistic + 4*mostLikely + pessimistic) / 6.
 * <p>
 * α = 1 + λ (m - o) / (p - o),  β = 1 + λ (p - m) / (p - o), with λ = 4.
 */
public final class BetaPertSampler implements DistributionSampler {
    private static final double LAMBDA = 4.0;

    private final double min;
    private final double mode;
    private final double max;
    private final BetaDistribution beta;

    public BetaPertSampler(double min, double mode, double max) {
        if (!(min <= mode && mode <= max) || min == max) {
            throw new IllegalArgumentException(
                "Beta-PERT requires min <= mode <= max with min < max: " + min + "/" + mode + "/" + max);
        }
        this.min = min;
        this.mode = mode;
        this.max = max;
        double range = max - min;
        double alpha = 1 + LAMBDA * (mode - min) / range;
        double betaShape = 1 + LAMBDA * (max - mode) / range;
        this.beta = new BetaDistribution(new JDKRandomGenerator(0), alpha, betaShape);
    }

    @Override
    public double sample(java.util.random.RandomGenerator rng) {
        // Re-seed the commons-math3 RandomGenerator for each call from the caller's source
        // to stay consistent with caller-controlled seeding. Commons-math3 Beta uses its own
        // RG internally; to respect our RG we inverse-transform via CDF^-1(u).
        double u = rng.nextDouble();
        // clamp away from 0/1 to avoid extreme infinities in inverseCumulativeProbability
        if (u <= 0.0) u = 1e-12;
        else if (u >= 1.0) u = 1 - 1e-12;
        double x = beta.inverseCumulativeProbability(u);
        return min + x * (max - min);
    }

    @Override
    public double mode() {
        return mode;
    }

    /** Expose underlying RandomGenerator for tests. */
    static RandomGenerator cmRandom(long seed) {
        return new JDKRandomGenerator((int) seed);
    }
}

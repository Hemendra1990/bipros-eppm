package com.bipros.risk.application.simulation;

import com.bipros.risk.domain.model.DistributionType;

import java.util.List;

/** Factory helpers for building per-activity/per-risk samplers. */
public final class DistributionSamplers {

    private DistributionSamplers() {}

    /**
     * Build a three-point sampler from min/mode/max. Used for Triangular, Beta-PERT,
     * Uniform (mode ignored), and as a fallback when distribution-specific parameters
     * aren't supplied.
     */
    public static DistributionSampler threePoint(DistributionType type, double min, double mode, double max) {
        return switch (type) {
            case BETA_PERT -> new BetaPertSampler(min, mode, max);
            case UNIFORM -> new UniformSampler(min, max);
            case TRIANGULAR -> new TriangularSampler(min, mode, max);
            // Remaining distributions want explicit parameters; fall back to Triangular.
            case NORMAL, LOGNORMAL, TRIGEN, DISCRETE -> new TriangularSampler(min, mode, max);
        };
    }

    public static DistributionSampler normal(double mean, double stddev) {
        return new NormalSampler(mean, stddev);
    }

    public static DistributionSampler lognormal(double mean, double stddev) {
        return new LognormalSampler(mean, stddev);
    }

    public static DistributionSampler trigen(double p10, double mode, double p90) {
        return new TrigenSampler(p10, mode, p90);
    }

    public static DistributionSampler discrete(List<DiscreteSampler.Outcome> outcomes) {
        return new DiscreteSampler(outcomes);
    }

    /**
     * When no distribution override or PERT estimate exists, construct a symmetric
     * band around the planned duration. For Normal/Lognormal we derive a stddev from
     * the variance fraction. For others we use a triangular band.
     */
    public static DistributionSampler fallback(double planned, double fractionalVariance,
                                               DistributionType defaultType) {
        double min = Math.max(0.0, planned * (1.0 - fractionalVariance));
        double max = planned * (1.0 + fractionalVariance);
        return switch (defaultType) {
            case NORMAL -> new NormalSampler(planned, planned * fractionalVariance / 3.0);
            case LOGNORMAL -> new LognormalSampler(planned, planned * fractionalVariance / 3.0);
            case TRIGEN -> new TrigenSampler(min, planned, max);
            case UNIFORM -> new UniformSampler(min, max);
            case DISCRETE, TRIANGULAR, BETA_PERT -> threePoint(
                defaultType == DistributionType.DISCRETE ? DistributionType.TRIANGULAR : defaultType,
                min, planned, max);
        };
    }
}

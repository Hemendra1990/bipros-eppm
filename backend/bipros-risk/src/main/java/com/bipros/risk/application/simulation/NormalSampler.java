package com.bipros.risk.application.simulation;

import com.bipros.risk.application.simulation.DistributionSampler.SingleShotRng;

import java.util.random.RandomGenerator;

/** Normal(μ, σ) truncated at ±3σ to avoid negative durations. */
public final class NormalSampler implements DistributionSampler {
    private final double mean;
    private final double stddev;

    public NormalSampler(double mean, double stddev) {
        if (stddev <= 0) throw new IllegalArgumentException("Normal requires stddev > 0: " + stddev);
        this.mean = mean;
        this.stddev = stddev;
    }

    @Override
    public double sample(RandomGenerator rng) {
        double z = rng.nextGaussian();
        if (z < -3.0) z = -3.0;
        else if (z > 3.0) z = 3.0;
        return mean + z * stddev;
    }

    @Override
    public double sampleFromUniform(double u) {
        double clamped = u <= 0.0 ? 1e-12 : (u >= 1.0 ? 1 - 1e-12 : u);
        // Inverse normal CDF via Acklam's approximation (shared with SingleShotRng).
        double z = SingleShotRng.staticInverseNormal(clamped);
        if (z < -3.0) z = -3.0;
        else if (z > 3.0) z = 3.0;
        return mean + z * stddev;
    }

    @Override
    public double mode() {
        return mean;
    }
}

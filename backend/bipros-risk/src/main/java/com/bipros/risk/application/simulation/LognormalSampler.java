package com.bipros.risk.application.simulation;

import com.bipros.risk.application.simulation.DistributionSampler.SingleShotRng;

import java.util.random.RandomGenerator;

/**
 * Lognormal distribution parameterised by its <em>output-space</em> mean and stddev.
 * Converts to underlying-normal (μ, σ) via:
 *   σ² = ln(1 + (stddev/mean)²)
 *   μ  = ln(mean) − σ²/2
 * Samples as exp(μ + σ·Z), producing strictly positive values.
 */
public final class LognormalSampler implements DistributionSampler {
    private final double mu;
    private final double sigma;
    private final double expectedMean;

    public LognormalSampler(double mean, double stddev) {
        if (mean <= 0) throw new IllegalArgumentException("Lognormal requires mean > 0: " + mean);
        if (stddev < 0) throw new IllegalArgumentException("Lognormal requires stddev >= 0: " + stddev);
        double cv2 = (stddev / mean) * (stddev / mean);
        this.sigma = Math.sqrt(Math.log(1.0 + cv2));
        this.mu = Math.log(mean) - 0.5 * sigma * sigma;
        this.expectedMean = mean;
    }

    @Override
    public double sample(RandomGenerator rng) {
        double z = rng.nextGaussian();
        return Math.exp(mu + sigma * z);
    }

    @Override
    public double sampleFromUniform(double u) {
        double clamped = u <= 0.0 ? 1e-12 : (u >= 1.0 ? 1 - 1e-12 : u);
        double z = SingleShotRng.staticInverseNormal(clamped);
        return Math.exp(mu + sigma * z);
    }

    @Override
    public double mode() {
        return Math.exp(mu - sigma * sigma);
    }

    public double expectedMean() {
        return expectedMean;
    }
}

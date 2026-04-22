package com.bipros.risk.application.simulation;

import java.util.random.RandomGenerator;

public final class UniformSampler implements DistributionSampler {
    private final double min;
    private final double max;

    public UniformSampler(double min, double max) {
        if (min >= max) throw new IllegalArgumentException("Uniform requires min < max: " + min + "/" + max);
        this.min = min;
        this.max = max;
    }

    @Override
    public double sample(RandomGenerator rng) {
        return min + rng.nextDouble() * (max - min);
    }

    @Override
    public double mode() {
        return (min + max) / 2.0;
    }
}

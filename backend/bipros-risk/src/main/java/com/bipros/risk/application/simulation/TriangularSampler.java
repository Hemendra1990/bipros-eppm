package com.bipros.risk.application.simulation;

import java.util.random.RandomGenerator;

public final class TriangularSampler implements DistributionSampler {
    private final double min;
    private final double mode;
    private final double max;
    private final double range;
    private final double leftFraction;

    public TriangularSampler(double min, double mode, double max) {
        if (!(min <= mode && mode <= max) || min == max) {
            throw new IllegalArgumentException(
                "Triangular requires min <= mode <= max with min < max: " + min + "/" + mode + "/" + max);
        }
        this.min = min;
        this.mode = mode;
        this.max = max;
        this.range = max - min;
        this.leftFraction = (mode - min) / range;
    }

    @Override
    public double sample(RandomGenerator rng) {
        double u = rng.nextDouble();
        if (u < leftFraction) {
            return min + Math.sqrt(u * range * (mode - min));
        }
        return max - Math.sqrt((1.0 - u) * range * (max - mode));
    }

    @Override
    public double mode() {
        return mode;
    }
}

package com.bipros.risk.application.simulation;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Discrete distribution: samples from a finite set of (value, probability) pairs.
 * Probabilities are renormalised on construction.
 */
public final class DiscreteSampler implements DistributionSampler {

    private final double[] values;
    private final double[] cumulativeProbabilities;
    private final double mode;

    public DiscreteSampler(List<Outcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            throw new IllegalArgumentException("Discrete requires at least one outcome");
        }
        double total = 0;
        for (Outcome o : outcomes) {
            if (o.probability() < 0) throw new IllegalArgumentException("Negative probability: " + o);
            total += o.probability();
        }
        if (total <= 0) throw new IllegalArgumentException("Total probability must be positive");

        this.values = new double[outcomes.size()];
        this.cumulativeProbabilities = new double[outcomes.size()];
        double running = 0;
        double modalValue = outcomes.get(0).value();
        double modalProb = outcomes.get(0).probability();
        for (int i = 0; i < outcomes.size(); i++) {
            Outcome o = outcomes.get(i);
            values[i] = o.value();
            running += o.probability() / total;
            cumulativeProbabilities[i] = running;
            if (o.probability() > modalProb) {
                modalProb = o.probability();
                modalValue = o.value();
            }
        }
        this.mode = modalValue;
    }

    @Override
    public double sample(RandomGenerator rng) {
        double u = rng.nextDouble();
        for (int i = 0; i < cumulativeProbabilities.length; i++) {
            if (u <= cumulativeProbabilities[i]) return values[i];
        }
        return values[values.length - 1];
    }

    @Override
    public double mode() {
        return mode;
    }

    public record Outcome(double value, double probability) {}
}

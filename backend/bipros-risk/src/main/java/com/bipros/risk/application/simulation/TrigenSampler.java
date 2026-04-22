package com.bipros.risk.application.simulation;

import java.util.random.RandomGenerator;

/**
 * Trigen distribution: a Triangular distribution whose tails are trimmed by P10 / P90.
 * Pertmaster's TRIGEN(a, m, b, 10, 90) reinterprets a/b as the 10th/90th percentile
 * rather than absolute min/max, then extrapolates the true min/max so the enforced
 * percentiles match. Here we use the closed-form extrapolation.
 * <p>
 * For a triangular(A, m, B) with lo = P10 and hi = P90, we solve for A, B:
 *   lo = A + √(0.1 · (B-A) · (m-A))  (when lo is in the lower fraction)
 *   hi = B − √(0.1 · (B-A) · (B-m))  (when hi is in the upper fraction)
 * <p>
 * We use a numeric solver (bisection, tight tolerance, ≤30 iter) since the closed
 * form is messy. This is done once at construction, not per sample — negligible cost.
 */
public final class TrigenSampler implements DistributionSampler {

    private final TriangularSampler underlying;
    private final double mode;

    public TrigenSampler(double p10, double mostLikely, double p90) {
        if (!(p10 <= mostLikely && mostLikely <= p90) || p10 == p90) {
            throw new IllegalArgumentException(
                "Trigen requires p10 <= mode <= p90 with p10 < p90: " + p10 + "/" + mostLikely + "/" + p90);
        }
        this.mode = mostLikely;

        double width = p90 - p10;
        double lo = p10 - width;           // initial guess: extrapolate left tail
        double hi = p90 + width;           // initial guess: extrapolate right tail

        // Bisection: find lo such that triangularCdf(lo, m, hi) at x=p10 = 0.1.
        // We tune only `lo` (holding hi at an approximation), then tune `hi`.
        // Simple two-pass refinement suffices for PRA accuracy.
        for (int pass = 0; pass < 2; pass++) {
            lo = solveLeftTail(lo, mostLikely, hi, p10);
            hi = solveRightTail(lo, mostLikely, hi, p90);
        }

        this.underlying = new TriangularSampler(lo, mostLikely, hi);
    }

    @Override
    public double sample(RandomGenerator rng) {
        return underlying.sample(rng);
    }

    @Override
    public double mode() {
        return mode;
    }

    private static double triangularCdf(double x, double a, double m, double b) {
        if (x <= a) return 0;
        if (x >= b) return 1;
        if (x < m) return ((x - a) * (x - a)) / ((b - a) * (m - a));
        return 1.0 - ((b - x) * (b - x)) / ((b - a) * (b - m));
    }

    private static double solveLeftTail(double loStart, double m, double b, double targetX) {
        // Find `a` such that triangularCdf(targetX, a, m, b) = 0.10.
        double low = Math.min(loStart - 10 * (b - m), targetX - (b - m));
        double high = targetX; // a must be <= targetX
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2.0;
            double cdf = triangularCdf(targetX, mid, m, b);
            if (cdf > 0.10) low = mid; else high = mid;
        }
        return (low + high) / 2.0;
    }

    private static double solveRightTail(double a, double m, double hiStart, double targetX) {
        double low = targetX;
        double high = Math.max(hiStart + 10 * (m - a), targetX + (m - a));
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2.0;
            double cdf = triangularCdf(targetX, a, m, mid);
            if (cdf < 0.90) low = mid; else high = mid;
        }
        return (low + high) / 2.0;
    }
}

package com.bipros.risk.application.simulation;

import java.util.random.RandomGenerator;

/**
 * Samples a single value given a uniform random source. Implementations are
 * stateless after construction (carry only distribution parameters) so the same
 * sampler instance can serve every iteration of a Monte Carlo run.
 */
public interface DistributionSampler {
    double sample(RandomGenerator rng);

    double mode();

    /**
     * Sample from a supplied uniform U∈[0,1]. Default implementation wraps the uniform
     * in a single-shot {@link RandomGenerator}; works for any sampler that consumes a
     * single {@code nextDouble()}. Samplers that consume Gaussian draws (Normal, Lognormal)
     * must override with an inverse-CDF form so Iman-Conover rank permutations work.
     */
    default double sampleFromUniform(double u) {
        double clamped = u <= 0.0 ? 1e-12 : (u >= 1.0 ? 1 - 1e-12 : u);
        return sample(new SingleShotRng(clamped));
    }

    /** A {@link RandomGenerator} that returns the same preset uniform on every nextDouble call. */
    final class SingleShotRng implements RandomGenerator {
        private final double uniform;
        SingleShotRng(double uniform) { this.uniform = uniform; }

        @Override public long nextLong() { return Double.doubleToRawLongBits(uniform); }
        @Override public double nextDouble() { return uniform; }
        @Override public double nextGaussian() {
            // Inverse of standard normal CDF via Acklam's approximation for fallback;
            // samplers using nextGaussian should really override sampleFromUniform directly
            // since a single uniform can't emulate the Gaussian shape consistently.
            return staticInverseNormal(uniform);
        }

        /** Acklam's closed-form inverse standard-normal CDF. Accuracy < 1e-9. */
        static double staticInverseNormal(double p) {
            final double a1 = -3.969683028665376e+01, a2 = 2.209460984245205e+02;
            final double a3 = -2.759285104469687e+02, a4 = 1.383577518672690e+02;
            final double a5 = -3.066479806614716e+01, a6 = 2.506628277459239e+00;
            final double b1 = -5.447609879822406e+01, b2 = 1.615858368580409e+02;
            final double b3 = -1.556989798598866e+02, b4 = 6.680131188771972e+01;
            final double b5 = -1.328068155288572e+01;
            final double c1 = -7.784894002430293e-03, c2 = -3.223964580411365e-01;
            final double c3 = -2.400758277161838e+00, c4 = -2.549732539343734e+00;
            final double c5 = 4.374664141464968e+00, c6 = 2.938163982698783e+00;
            final double d1 = 7.784695709041462e-03, d2 = 3.224671290700398e-01;
            final double d3 = 2.445134137142996e+00, d4 = 3.754408661907416e+00;
            final double pLow = 0.02425, pHigh = 1 - pLow;
            double q, r;
            if (p < pLow) {
                q = Math.sqrt(-2 * Math.log(p));
                return (((((c1*q + c2)*q + c3)*q + c4)*q + c5)*q + c6)
                    / ((((d1*q + d2)*q + d3)*q + d4)*q + 1);
            } else if (p <= pHigh) {
                q = p - 0.5;
                r = q * q;
                return (((((a1*r + a2)*r + a3)*r + a4)*r + a5)*r + a6) * q
                    / (((((b1*r + b2)*r + b3)*r + b4)*r + b5)*r + 1);
            } else {
                q = Math.sqrt(-2 * Math.log(1 - p));
                return -(((((c1*q + c2)*q + c3)*q + c4)*q + c5)*q + c6)
                    / ((((d1*q + d2)*q + d3)*q + d4)*q + 1);
            }
        }
    }
}

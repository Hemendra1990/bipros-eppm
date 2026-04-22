package com.bipros.risk.simulation;

import com.bipros.risk.application.simulation.BetaPertSampler;
import com.bipros.risk.application.simulation.TriangularSampler;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.assertj.core.api.Assertions.assertThat;

class DistributionSamplerTest {

    private static final int N = 200_000;

    private static RandomGenerator fixedRng() {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(42L);
    }

    @Test
    void triangularMatchesAnalyticalMean() {
        // Triangular(a,m,b) mean = (a+m+b)/3; stdDev^2 = (a^2+m^2+b^2-am-ab-mb)/18
        double a = 5, m = 10, b = 20;
        TriangularSampler s = new TriangularSampler(a, m, b);
        RandomGenerator rng = fixedRng();

        double[] xs = new double[N];
        for (int i = 0; i < N; i++) xs[i] = s.sample(rng);

        double mean = meanOf(xs);
        double expected = (a + m + b) / 3.0;
        assertThat(mean).isCloseTo(expected, within(expected, 0.01));

        double var = varianceOf(xs, mean);
        double expectedVar = (a*a + m*m + b*b - a*m - a*b - m*b) / 18.0;
        assertThat(Math.sqrt(var)).isCloseTo(Math.sqrt(expectedVar), within(Math.sqrt(expectedVar), 0.03));
    }

    @Test
    void betaPertMatchesPertExpectedValue() {
        // Beta-PERT classic expected = (a + 4m + b) / 6
        double a = 5, m = 10, b = 20;
        BetaPertSampler s = new BetaPertSampler(a, m, b);
        RandomGenerator rng = fixedRng();

        double[] xs = new double[N];
        for (int i = 0; i < N; i++) xs[i] = s.sample(rng);

        double mean = meanOf(xs);
        double expected = (a + 4 * m + b) / 6.0;
        assertThat(mean).isCloseTo(expected, within(expected, 0.01));
    }

    @Test
    void triangularRejectsDegenerateInputs() {
        try {
            new TriangularSampler(5, 10, 5);
            throw new AssertionError("expected exception");
        } catch (IllegalArgumentException expected) {}

        try {
            new TriangularSampler(10, 5, 20); // mode < min
            throw new AssertionError("expected exception");
        } catch (IllegalArgumentException expected) {}
    }

    private static double meanOf(double[] xs) {
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.length;
    }

    private static double varianceOf(double[] xs, double mean) {
        double s = 0;
        for (double x : xs) { double d = x - mean; s += d * d; }
        return s / (xs.length - 1);
    }

    private static org.assertj.core.data.Offset<Double> within(double v, double fraction) {
        return org.assertj.core.data.Offset.offset(Math.abs(v) * fraction);
    }
}

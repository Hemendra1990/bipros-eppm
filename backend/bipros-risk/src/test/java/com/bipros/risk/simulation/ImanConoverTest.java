package com.bipros.risk.simulation;

import com.bipros.risk.application.simulation.ImanConover;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImanConoverTest {

    @Test
    void inducesRequestedPositiveCorrelation() {
        double target = 0.8;
        double[][] R = {{1.0, target}, {target, 1.0}};
        double[][] U = ImanConover.correlatedUniforms(5000, R, 42L);

        double[] col0 = new double[U.length];
        double[] col1 = new double[U.length];
        for (int i = 0; i < U.length; i++) { col0[i] = U[i][0]; col1[i] = U[i][1]; }

        // Marginals still uniform — mean ≈ 0.5, stddev ≈ sqrt(1/12) ≈ 0.289.
        double mean0 = mean(col0), mean1 = mean(col1);
        assertThat(mean0).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
        assertThat(mean1).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));

        // Pearson correlation of the reshuffled columns should be close to target.
        double actual = new PearsonsCorrelation().correlation(col0, col1);
        assertThat(actual).isCloseTo(target, org.assertj.core.data.Offset.offset(0.03));
    }

    @Test
    void inducesRequestedNegativeCorrelation() {
        double[][] R = {{1.0, -0.6}, {-0.6, 1.0}};
        double[][] U = ImanConover.correlatedUniforms(5000, R, 7L);
        double[] col0 = new double[U.length];
        double[] col1 = new double[U.length];
        for (int i = 0; i < U.length; i++) { col0[i] = U[i][0]; col1[i] = U[i][1]; }
        double actual = new PearsonsCorrelation().correlation(col0, col1);
        assertThat(actual).isCloseTo(-0.6, org.assertj.core.data.Offset.offset(0.03));
    }

    @Test
    void identityMatrixProducesIndependentUniforms() {
        double[][] R = {{1, 0}, {0, 1}};
        double[][] U = ImanConover.correlatedUniforms(5000, R, 1L);
        double[] col0 = new double[U.length];
        double[] col1 = new double[U.length];
        for (int i = 0; i < U.length; i++) { col0[i] = U[i][0]; col1[i] = U[i][1]; }
        double actual = new PearsonsCorrelation().correlation(col0, col1);
        assertThat(Math.abs(actual)).isLessThan(0.05);
    }

    private static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }
}

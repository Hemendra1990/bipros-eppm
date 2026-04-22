package com.bipros.risk.application.simulation;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.NonPositiveDefiniteMatrixException;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Arrays;
import java.util.Comparator;
import java.util.SplittableRandom;

/**
 * Iman-Conover rank-correlation reshuffling. Given a target correlation matrix over
 * {@code V} variables, produces an {@code N × V} matrix of uniforms such that:
 * <ul>
 *   <li>each column's marginal distribution is still uniform on [0,1], and</li>
 *   <li>the rank correlation between any two columns approximates the target.</li>
 * </ul>
 * <p>
 * Pertmaster uses this technique for its "Duration Correlation" feature. Marginals are
 * preserved exactly, so the per-activity distribution (triangular / beta-pert / …) stays
 * valid; only the pairing across iterations is rearranged.
 */
public final class ImanConover {

    private ImanConover() {}

    /**
     * Build an N×V uniform matrix whose columns' rank correlations approximate {@code targetR}.
     * If {@code targetR} is effectively identity (no non-trivial correlations), an independent
     * uniform matrix is returned — no matrix work.
     *
     * @param targetR symmetric V×V matrix with 1 on the diagonal and pair correlations off-diagonal.
     */
    public static double[][] correlatedUniforms(int iterations, double[][] targetR, long seed) {
        int V = targetR.length;
        SplittableRandom rng = new SplittableRandom(seed);
        double[][] U = new double[iterations][V];

        if (V == 0) return U;

        if (isIdentity(targetR)) {
            for (int i = 0; i < iterations; i++)
                for (int c = 0; c < V; c++) U[i][c] = rng.nextDouble();
            return U;
        }

        // van der Waerden scores: normal(rank/(N+1))^(-1) — a fixed sorted vector.
        double[] scores = new double[iterations];
        for (int i = 0; i < iterations; i++) {
            double p = (i + 1.0) / (iterations + 1.0);
            scores[i] = DistributionSampler.SingleShotRng.staticInverseNormal(p);
        }

        // S: one column per variable, each a random permutation of `scores`.
        double[][] S = new double[iterations][V];
        for (int c = 0; c < V; c++) {
            double[] perm = scores.clone();
            shuffle(perm, rng);
            for (int i = 0; i < iterations; i++) S[i][c] = perm[i];
        }

        // Cholesky(targetR) — with regularization fallback if not PSD.
        double[][] L = choleskyOrRegularised(targetR);

        // Y = S · L^T (N×V × V×V = N×V)
        RealMatrix sMat = new Array2DRowRealMatrix(S, false);
        RealMatrix lMat = new Array2DRowRealMatrix(L, false);
        RealMatrix Y = sMat.multiply(lMat.transpose());

        // For each column: sort independent uniforms; place them into U[:,c] in rank order
        // dictated by Y[:,c]. Preserves uniform marginals; induces the target correlation.
        Integer[] idx = new Integer[iterations];
        for (int c = 0; c < V; c++) {
            double[] indU = new double[iterations];
            for (int i = 0; i < iterations; i++) indU[i] = rng.nextDouble();
            Arrays.sort(indU);

            for (int i = 0; i < iterations; i++) idx[i] = i;
            final int col = c;
            Arrays.sort(idx, Comparator.comparingDouble(ii -> Y.getEntry(ii, col)));
            for (int r = 0; r < iterations; r++) {
                U[idx[r]][c] = indU[r];
            }
        }
        return U;
    }

    private static boolean isIdentity(double[][] R) {
        int n = R.length;
        for (int i = 0; i < n; i++) {
            if (Math.abs(R[i][i] - 1.0) > 1e-9) return false;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(R[i][j]) > 1e-9) return false;
            }
        }
        return true;
    }

    /**
     * Attempt Cholesky; if target matrix is not PSD (common when users set large correlations
     * that violate triangle inequality), add a small multiple of the identity until it is.
     * This is the standard "nearest-PSD" trick and is sufficient for Iman-Conover where exact
     * target correlations aren't required — only approximated.
     */
    private static double[][] choleskyOrRegularised(double[][] R) {
        double[][] mat = deepCopy(R);
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                RealMatrix m = new Array2DRowRealMatrix(mat, false);
                CholeskyDecomposition ch = new CholeskyDecomposition(m, 1e-10, 1e-10);
                return ch.getL().getData();
            } catch (NonPositiveDefiniteMatrixException e) {
                double epsilon = Math.pow(10, -6 + attempt);
                for (int i = 0; i < mat.length; i++) mat[i][i] += epsilon;
            }
        }
        // Fallback: identity (no correlation) — safer than failing the whole simulation.
        int n = R.length;
        double[][] I = new double[n][n];
        for (int i = 0; i < n; i++) I[i][i] = 1.0;
        return I;
    }

    private static double[][] deepCopy(double[][] x) {
        double[][] out = new double[x.length][];
        for (int i = 0; i < x.length; i++) out[i] = x[i].clone();
        return out;
    }

    private static void shuffle(double[] array, SplittableRandom rng) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            double t = array[i]; array[i] = array[j]; array[j] = t;
        }
    }
}

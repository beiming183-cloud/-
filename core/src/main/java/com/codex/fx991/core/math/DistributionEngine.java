package com.codex.fx991.core.math;

/** Probability distributions exposed by the fx-999CN CW Distribution application. */
public final class DistributionEngine {
    private DistributionEngine() {}

    public static double binomialPmf(int x, int trials, double probability) {
        validateProbability(probability);
        if (trials < 0) throw new IllegalArgumentException("N must be non-negative");
        if (x < 0 || x > trials) return 0.0;
        if (probability == 0.0) return x == 0 ? 1.0 : 0.0;
        if (probability == 1.0) return x == trials ? 1.0 : 0.0;
        double log = logCombination(trials, x)
                + x * Math.log(probability)
                + (trials - x) * Math.log1p(-probability);
        return Math.exp(log);
    }

    public static double binomialCdf(int x, int trials, double probability) {
        validateProbability(probability);
        if (trials < 0) throw new IllegalArgumentException("N must be non-negative");
        if (x < 0) return 0.0;
        if (x >= trials) return 1.0;
        if (probability == 0.0) return 1.0;
        if (probability == 1.0) return 0.0;

        int lowerTerms = x + 1;
        int upperTerms = trials - x;
        if (lowerTerms <= upperTerms) {
            double logTerm = trials * Math.log1p(-probability);
            double logSum = Double.NEGATIVE_INFINITY;
            for (int k = 0; k <= x; k++) {
                logSum = logAdd(logSum, logTerm);
                if (k < x) {
                    logTerm += Math.log(trials - k) - Math.log(k + 1.0)
                            + Math.log(probability) - Math.log1p(-probability);
                }
            }
            return clampProbability(Math.exp(logSum));
        }

        double logTerm = trials * Math.log(probability);
        double logTail = Double.NEGATIVE_INFINITY;
        for (int k = trials; k > x; k--) {
            logTail = logAdd(logTail, logTerm);
            if (k > x + 1) {
                logTerm += Math.log(k) - Math.log(trials - k + 1.0)
                        + Math.log1p(-probability) - Math.log(probability);
            }
        }
        return clampProbability(-Math.expm1(logTail));
    }

    public static double normalPdf(double x, double mean, double standardDeviation) {
        requirePositive(standardDeviation, "sigma");
        double z = (x - mean) / standardDeviation;
        return Math.exp(-0.5 * z * z) / (standardDeviation * Math.sqrt(2.0 * Math.PI));
    }

    public static double normalCdf(double upper, double mean, double standardDeviation) {
        requirePositive(standardDeviation, "sigma");
        double z = (upper - mean) / (standardDeviation * Math.sqrt(2.0));
        return 0.5 * (1.0 + erf(z));
    }

    public static double normalCdf(double lower, double upper, double mean, double standardDeviation) {
        if (lower > upper) throw new IllegalArgumentException("lower > upper");
        return clampProbability(normalCdf(upper, mean, standardDeviation)
                - normalCdf(lower, mean, standardDeviation));
    }

    public static double inverseNormalCdf(double area, double mean, double standardDeviation) {
        validateProbability(area);
        requirePositive(standardDeviation, "sigma");
        if (area == 0.0) return Double.NEGATIVE_INFINITY;
        if (area == 1.0) return Double.POSITIVE_INFINITY;
        return mean + standardDeviation * inverseStandardNormal(area);
    }

    public static double poissonPmf(int x, double lambda) {
        if (x < 0) return 0.0;
        requireNonNegative(lambda, "lambda");
        if (lambda == 0.0) return x == 0 ? 1.0 : 0.0;
        return Math.exp(-lambda + x * Math.log(lambda) - logFactorial(x));
    }

    public static double poissonCdf(int x, double lambda) {
        requireNonNegative(lambda, "lambda");
        if (x < 0) return 0.0;
        if (lambda == 0.0) return 1.0;
        double logTerm = -lambda;
        double logSum = Double.NEGATIVE_INFINITY;
        double logLambda = Math.log(lambda);
        for (int k = 0; k <= x; k++) {
            logSum = logAdd(logSum, logTerm);
            if (k >= lambda && logTerm < logSum - 40.0) break;
            if (k < x) logTerm += logLambda - Math.log(k + 1.0);
        }
        return clampProbability(Math.exp(logSum));
    }

    /** P(t): lower-tail standard normal probability. */
    public static double standardNormalP(double t) { return normalCdf(t, 0.0, 1.0); }
    /** Q(t): area between zero and |t|. */
    public static double standardNormalQ(double t) { return normalCdf(t, 0.0, 1.0) - 0.5; }
    /** R(t): upper-tail standard normal probability. */
    public static double standardNormalR(double t) { return 1.0 - normalCdf(t, 0.0, 1.0); }

    private static double erf(double x) {
        // Abramowitz-Stegun 7.1.26; max error about 1.5e-7, inside the manual's 6-digit gate.
        if (x == 0.0) return 0.0;
        double sign = Math.copySign(1.0, x);
        double value = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * value);
        double polynomial = (((((1.061405429 * t - 1.453152027) * t)
                + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t;
        return sign * (1.0 - polynomial * Math.exp(-value * value));
    }

    private static double inverseStandardNormal(double probability) {
        // Peter J. Acklam's rational approximation.
        double[] a = {-3.969683028665376e+01, 2.209460984245205e+02,
                -2.759285104469687e+02, 1.383577518672690e+02,
                -3.066479806614716e+01, 2.506628277459239e+00};
        double[] b = {-5.447609879822406e+01, 1.615858368580409e+02,
                -1.556989798598866e+02, 6.680131188771972e+01,
                -1.328068155288572e+01};
        double[] c = {-7.784894002430293e-03, -3.223964580411365e-01,
                -2.400758277161838e+00, -2.549732539343734e+00,
                4.374664141464968e+00, 2.938163982698783e+00};
        double[] d = {7.784695709041462e-03, 3.224671290700398e-01,
                2.445134137142996e+00, 3.754408661907416e+00};
        double low = 0.02425, high = 1.0 - low;
        if (probability < low) {
            double q = Math.sqrt(-2.0 * Math.log(probability));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        }
        if (probability > high) {
            double q = Math.sqrt(-2.0 * Math.log1p(-probability));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        }
        double q = probability - 0.5;
        double r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0);
    }

    private static double logCombination(int n, int r) {
        int terms = Math.min(r, n - r);
        double result = 0.0;
        for (int index = 1; index <= terms; index++) {
            result += Math.log(n - terms + index) - Math.log(index);
        }
        return result;
    }

    private static double logFactorial(int n) {
        double sum = 0.0;
        for (int i = 2; i <= n; i++) sum += Math.log(i);
        return sum;
    }

    private static void validateProbability(double value) {
        if (!(value >= 0.0 && value <= 1.0)) throw new IllegalArgumentException("Probability outside 0..1");
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!(value >= 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static double clampProbability(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double logAdd(double left, double right) {
        if (left == Double.NEGATIVE_INFINITY) return right;
        if (right == Double.NEGATIVE_INFINITY) return left;
        double maximum = Math.max(left, right);
        return maximum + Math.log1p(Math.exp(Math.min(left, right) - maximum));
    }
}

package com.codex.fx991.core.math;

/** Solves the two ratio forms exposed by the CN CW Ratio application. */
public final class RatioEngine {
    private RatioEngine() {}

    /** Solves A:B = X:D. */
    public static double solveAtoBEqualsXtoD(double a, double b, double d) {
        requireCoefficient(a, "A");
        requireCoefficient(b, "B");
        requireCoefficient(d, "D");
        return checkedProductQuotient(a, d, b);
    }

    /** Solves A:B = C:X. */
    public static double solveAtoBEqualsCtoX(double a, double b, double c) {
        requireCoefficient(a, "A");
        requireCoefficient(b, "B");
        requireCoefficient(c, "C");
        return checkedProductQuotient(b, c, a);
    }

    private static double checkedProductQuotient(double left, double right, double divisor) {
        double result = (left / divisor) * right;
        if (!Double.isFinite(result)) throw new ArithmeticException("Math ERROR");
        return result;
    }

    private static void requireCoefficient(double value, String name) {
        if (!Double.isFinite(value) || value == 0.0) {
            throw new ArithmeticException(name + " must be finite and nonzero");
        }
    }
}

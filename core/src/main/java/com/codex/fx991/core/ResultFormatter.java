package com.codex.fx991.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

final class ResultFormatter {
    private static final MathContext DISPLAY_PRECISION = new MathContext(12, RoundingMode.HALF_UP);

    private ResultFormatter() {}

    static String format(double value, ResultMode mode) {
        if (!Double.isFinite(value)) return "Math ERROR";
        if (Math.abs(value) < 5e-15) value = 0.0;
        if (mode == ResultMode.FRACTION) {
            String fraction = approximateFraction(value);
            if (fraction != null) return fraction;
        }
        double abs = Math.abs(value);
        if (abs != 0.0 && (abs >= 1e10 || abs < 1e-9)) {
            return String.format(java.util.Locale.ROOT, "%.9E", value)
                    .replaceAll("0+E", "E").replace("E+", "E");
        }
        return new BigDecimal(value, DISPLAY_PRECISION).stripTrailingZeros().toPlainString();
    }

    private static String approximateFraction(double value) {
        boolean negative = value < 0;
        double target = Math.abs(value);
        long whole = (long) Math.floor(target);
        double fractional = target - whole;
        if (fractional < 1e-12) return null;

        long h0 = 0, h1 = 1, k0 = 1, k1 = 0;
        double x = fractional;
        for (int i = 0; i < 24; i++) {
            long a = (long) Math.floor(x);
            long h2 = a * h1 + h0;
            long k2 = a * k1 + k0;
            if (k2 > 9999 || h2 > 9_999_999) break;
            double approximation = (double) h2 / k2;
            if (Math.abs(approximation - fractional) < 1e-10) {
                long numerator = whole * k2 + h2;
                if (negative) numerator = -numerator;
                return numerator + "/" + k2;
            }
            h0 = h1; h1 = h2; k0 = k1; k1 = k2;
            double remainder = x - a;
            if (remainder < 1e-15) break;
            x = 1.0 / remainder;
        }
        return null;
    }
}

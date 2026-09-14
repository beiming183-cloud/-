package com.codex.fx991.core.math;

import com.codex.fx991.core.AngleUnit;
import com.codex.fx991.core.Compat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independently implemented utility behavior specified by the CN CW manual. */
public final class ManualFunctions {
    private static final Map<String, Double> ENGINEERING_FACTORS;

    static {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("f", 1e-15);
        values.put("p", 1e-12);
        values.put("n", 1e-9);
        values.put("µ", 1e-6);
        values.put("u", 1e-6);
        values.put("m", 1e-3);
        values.put("k", 1e3);
        values.put("M", 1e6);
        values.put("G", 1e9);
        values.put("T", 1e12);
        values.put("P", 1e15);
        values.put("E", 1e18);
        ENGINEERING_FACTORS = Collections.unmodifiableMap(values);
    }

    private ManualFunctions() { }

    /**
     * Performs the manual's ÷R operation. If a positive quotient/remainder
     * pair cannot be represented, {@code remainderMode} is false and the
     * ordinary quotient is returned.
     */
    public static RemainderResult divideWithRemainder(double dividend, double divisor) {
        if (!Double.isFinite(dividend) || !Double.isFinite(divisor) || divisor == 0.0) {
            throw new ArithmeticException("Math ERROR");
        }
        double ordinary = dividend / divisor;
        if (!Double.isFinite(ordinary)) throw new ArithmeticException("Math ERROR");
        double quotient = Math.floor(ordinary);
        double remainder = dividend - quotient * divisor;
        double scale = Math.max(1.0, Math.max(Math.abs(dividend), Math.abs(divisor)));
        if (Math.abs(remainder) <= Math.ulp(scale) * 8.0) remainder = 0.0;
        boolean representable = quotient >= 1.0 && quotient == Math.rint(quotient)
                && remainder >= 0.0 && Double.isFinite(remainder)
                && Math.abs(dividend) < 1e10 && Math.abs(divisor) < 1e10;
        return representable
                ? new RemainderResult(quotient, remainder, true, quotient)
                : new RemainderResult(ordinary, Double.NaN, false, ordinary);
    }

    public static Dms fromDecimalDegrees(double decimalDegrees) {
        if (!Double.isFinite(decimalDegrees)) throw new ArithmeticException("Math ERROR");
        int sign = Double.doubleToRawLongBits(decimalDegrees) < 0 ? -1 : 1;
        double absolute = Math.abs(decimalDegrees);
        long degrees = (long) Math.floor(absolute);
        double minutesValue = (absolute - degrees) * 60.0;
        int minutes = (int) Math.floor(minutesValue);
        double seconds = round(minutesValue * 60.0 - minutes * 60.0, 10);
        if (seconds >= 60.0) {
            seconds = 0.0;
            minutes++;
        }
        if (minutes >= 60) {
            minutes = 0;
            degrees++;
        }
        return new Dms(sign, degrees, minutes, seconds);
    }

    public static double toDecimalDegrees(int sign, long degrees, int minutes, double seconds) {
        if ((sign != -1 && sign != 1) || degrees < 0 || minutes < 0 || minutes >= 60
                || !Double.isFinite(seconds) || seconds < 0.0 || seconds >= 60.0) {
            throw new IllegalArgumentException("DMS range");
        }
        return sign * (degrees + minutes / 60.0 + seconds / 3600.0);
    }

    public static CoordinatePair polar(double x, double y, AngleUnit unit) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) throw new ArithmeticException("Math ERROR");
        double radius = Math.hypot(x, y);
        double radians = Math.atan2(y, x);
        return new CoordinatePair(radius, fromRadians(radians, unit));
    }

    public static CoordinatePair rectangular(double radius, double angle, AngleUnit unit) {
        if (!Double.isFinite(radius) || !Double.isFinite(angle)) {
            throw new ArithmeticException("Math ERROR");
        }
        double radians = toRadians(angle, unit);
        return new CoordinatePair(radius * Math.cos(radians), radius * Math.sin(radians));
    }

    public static Map<String, Double> engineeringFactors() { return Compat.copyMap(ENGINEERING_FACTORS); }

    public static double applyEngineeringSuffix(double value, String suffix) {
        Double factor = ENGINEERING_FACTORS.get(suffix);
        if (factor == null) throw new IllegalArgumentException("Unknown engineering suffix: " + suffix);
        double result = value * factor;
        if (!Double.isFinite(result)) throw new ArithmeticException("Math ERROR");
        return result;
    }

    public static EngineeringValue engineering(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Math ERROR");
        if (value == 0.0) return new EngineeringValue(0.0, 0, "");
        int exponent = (int) Math.floor(Math.log10(Math.abs(value)) / 3.0) * 3;
        exponent = Math.max(-15, Math.min(18, exponent));
        double mantissa = value / Math.pow(10.0, exponent);
        return new EngineeringValue(mantissa, exponent, symbolForExponent(exponent));
    }

    private static String symbolForExponent(int exponent) {
        return switch (exponent) {
            case -15 -> "f"; case -12 -> "p"; case -9 -> "n"; case -6 -> "µ";
            case -3 -> "m"; case 3 -> "k"; case 6 -> "M"; case 9 -> "G";
            case 12 -> "T"; case 15 -> "P"; case 18 -> "E"; default -> "";
        };
    }

    private static double toRadians(double angle, AngleUnit unit) {
        return switch (unit) {
            case DEG -> Math.toRadians(angle);
            case RAD -> angle;
            case GRAD -> angle * Math.PI / 200.0;
        };
    }

    private static double fromRadians(double radians, AngleUnit unit) {
        return switch (unit) {
            case DEG -> Math.toDegrees(radians);
            case RAD -> radians;
            case GRAD -> radians * 200.0 / Math.PI;
        };
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    public record RemainderResult(double quotient, double remainder,
                                  boolean remainderMode, double ansValue) { }

    public record Dms(int sign, long degrees, int minutes, double seconds) {
        public Dms {
            if ((sign != -1 && sign != 1) || degrees < 0 || minutes < 0 || minutes >= 60
                    || !Double.isFinite(seconds) || seconds < 0.0 || seconds >= 60.0) {
                throw new IllegalArgumentException("DMS range");
            }
        }

        public double decimalDegrees() {
            return toDecimalDegrees(sign, degrees, minutes, seconds);
        }

        public String display() {
            return (sign < 0 ? "−" : "") + degrees + "°" + minutes + "′"
                    + trim(seconds) + "″";
        }
    }

    public record CoordinatePair(double first, double second) { }
    public record EngineeringValue(double mantissa, int exponent, String symbol) { }

    private static String trim(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

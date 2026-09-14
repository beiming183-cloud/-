package com.codex.fx991.core.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Exact rational arithmetic for fraction results and finite decimal input. */
public final class Rational implements Comparable<Rational> {
    public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
    public static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);
    private final BigInteger numerator;
    private final BigInteger denominator;

    public Rational(long value) { this(BigInteger.valueOf(value), BigInteger.ONE); }

    public Rational(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0) throw new ArithmeticException("Zero denominator");
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        this.numerator = numerator.divide(gcd);
        this.denominator = denominator.divide(gcd);
    }

    public static Rational parseDecimal(String value) {
        BigDecimal decimal = new BigDecimal(value);
        BigInteger numerator = decimal.unscaledValue();
        int scale = decimal.scale();
        if (scale < 0) return new Rational(numerator.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
        return new Rational(numerator, BigInteger.TEN.pow(scale));
    }

    public static Rational approximate(double value, long maxDenominator, double tolerance) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite value");
        if (maxDenominator < 1) throw new IllegalArgumentException("maxDenominator must be positive");
        if (!(tolerance >= 0.0) || !Double.isFinite(tolerance)) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative");
        }
        if (value == 0.0) return ZERO;
        boolean negative = value < 0.0;
        double x = Math.abs(value);
        BigInteger p0 = BigInteger.ZERO, p1 = BigInteger.ONE;
        BigInteger q0 = BigInteger.ONE, q1 = BigInteger.ZERO;
        BigInteger limit = BigInteger.valueOf(maxDenominator);
        for (int iteration = 0; iteration < 64; iteration++) {
            double floor = Math.floor(x);
            BigInteger a = BigDecimal.valueOf(floor).toBigInteger();
            BigInteger p2 = a.multiply(p1).add(p0);
            BigInteger q2 = a.multiply(q1).add(q0);
            if (q2.compareTo(limit) > 0) break;
            double candidate = p2.doubleValue() / q2.doubleValue();
            if (Math.abs(candidate - Math.abs(value)) <= tolerance) {
                return new Rational(negative ? p2.negate() : p2, q2);
            }
            p0 = p1; p1 = p2; q0 = q1; q1 = q2;
            double remainder = x - floor;
            if (remainder == 0.0) return new Rational(negative ? p1.negate() : p1, q1);
            x = 1.0 / remainder;
            if (!Double.isFinite(x)) break;
        }
        BigInteger multiplier = limit.subtract(q0).divide(q1);
        Rational bounded = new Rational(p0.add(multiplier.multiply(p1)),
                q0.add(multiplier.multiply(q1)));
        Rational convergent = new Rational(p1, q1);
        Rational closest = Math.abs(bounded.toDouble() - Math.abs(value))
                < Math.abs(convergent.toDouble() - Math.abs(value)) ? bounded : convergent;
        return negative ? closest.negate() : closest;
    }

    public BigInteger numerator() { return numerator; }
    public BigInteger denominator() { return denominator; }
    public boolean isInteger() { return denominator.equals(BigInteger.ONE); }

    public Rational add(Rational other) {
        return new Rational(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public Rational subtract(Rational other) {
        return new Rational(numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public Rational multiply(Rational other) {
        return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
    }

    public Rational divide(Rational other) {
        return new Rational(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
    }

    public Rational negate() { return new Rational(numerator.negate(), denominator); }

    public Rational pow(int exponent) {
        if (exponent == 0) return ONE;
        if (exponent < 0) return new Rational(denominator.pow(-exponent), numerator.pow(-exponent));
        return new Rational(numerator.pow(exponent), denominator.pow(exponent));
    }

    public double toDouble() { return numerator.doubleValue() / denominator.doubleValue(); }

    public String improperString() { return numerator + "/" + denominator; }

    public String mixedString() {
        BigInteger[] parts = numerator.abs().divideAndRemainder(denominator);
        String sign = numerator.signum() < 0 ? "-" : "";
        if (parts[1].signum() == 0) return sign + parts[0];
        if (parts[0].signum() == 0) return sign + parts[1] + "/" + denominator;
        return sign + parts[0] + " " + parts[1] + "/" + denominator;
    }

    @Override public int compareTo(Rational other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    @Override public boolean equals(Object object) {
        return object instanceof Rational other
                && numerator.equals(other.numerator) && denominator.equals(other.denominator);
    }

    @Override public int hashCode() { return Objects.hash(numerator, denominator); }
    @Override public String toString() { return isInteger() ? numerator.toString() : improperString(); }
}

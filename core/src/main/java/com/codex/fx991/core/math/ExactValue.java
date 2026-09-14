package com.codex.fx991.core.math;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Small exact-number domain used by the Calculate application.
 *
 * <p>It is intentionally not a general computer algebra system.  It keeps
 * the exact forms the handheld can display: rational numbers, square-root
 * terms, and rational multiples of pi/e. Unsupported algebra simply returns
 * {@code null}, allowing the numerical result to remain authoritative.</p>
 */
public final class ExactValue {
    private static final BigInteger FACTOR_LIMIT = BigInteger.valueOf(1_000_000_000_000L);
    private static final Comparator<Basis> BASIS_ORDER = Comparator
            .comparingInt((Basis value) -> value.isOne() ? 0 : 1)
            .thenComparingInt(Basis::piPower)
            .thenComparingInt(Basis::ePower)
            .thenComparing(Basis::radicand);
    private static final Basis ONE_BASIS = new Basis(BigInteger.ONE, 0, 0);

    public static final ExactValue ZERO = rational(Rational.ZERO);
    public static final ExactValue ONE = rational(Rational.ONE);
    public static final ExactValue PI = monomial(Rational.ONE,
            new Basis(BigInteger.ONE, 1, 0));
    public static final ExactValue E = monomial(Rational.ONE,
            new Basis(BigInteger.ONE, 0, 1));

    private final SortedMap<Basis, Rational> terms;

    private ExactValue(Map<Basis, Rational> source) {
        TreeMap<Basis, Rational> normalized = new TreeMap<>(BASIS_ORDER);
        for (Map.Entry<Basis, Rational> entry : source.entrySet()) {
            if (entry.getValue().numerator().signum() != 0) {
                normalized.merge(entry.getKey(), entry.getValue(), Rational::add);
            }
        }
        normalized.entrySet().removeIf(entry -> entry.getValue().numerator().signum() == 0);
        terms = Collections.unmodifiableSortedMap(normalized);
    }

    public static ExactValue integer(long value) {
        return rational(new Rational(value));
    }

    public static ExactValue rational(Rational value) {
        return monomial(value, ONE_BASIS);
    }

    public static ExactValue decimal(String literal) {
        try {
            return rational(Rational.parseDecimal(literal));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static ExactValue monomial(Rational coefficient, Basis basis) {
        if (coefficient.numerator().signum() == 0) {
            return new ExactValue(Collections.emptyMap());
        }
        return new ExactValue(Collections.singletonMap(basis, coefficient));
    }

    /** Returns an exact square root, or {@code null} when it is outside the display domain. */
    public static ExactValue sqrt(Rational value) {
        if (value.numerator().signum() < 0) return null;
        if (value.numerator().signum() == 0) return ZERO;
        BigInteger combined = value.numerator().multiply(value.denominator());
        SquareParts parts = squareParts(combined);
        if (parts == null) return null;
        Rational coefficient = new Rational(parts.outside(), value.denominator());
        return monomial(coefficient, new Basis(parts.inside(), 0, 0));
    }

    /** Exact nth root for rational perfect powers; square roots also retain a surd. */
    public static ExactValue root(Rational value, int degree) {
        if (degree == 0 || Math.abs(degree) > 31) return null;
        if (degree == 2) return sqrt(value);
        boolean reciprocal = degree < 0;
        int positiveDegree = Math.abs(degree);
        boolean negative = value.numerator().signum() < 0;
        if (negative && (positiveDegree & 1) == 0) return null;
        BigInteger numerator = exactNthRoot(value.numerator().abs(), positiveDegree);
        BigInteger denominator = exactNthRoot(value.denominator(), positiveDegree);
        if (numerator == null || denominator == null) return null;
        if (negative) numerator = numerator.negate();
        ExactValue result = rational(new Rational(numerator, denominator));
        return reciprocal ? ONE.divide(result) : result;
    }

    public ExactValue negate() {
        TreeMap<Basis, Rational> output = new TreeMap<>(BASIS_ORDER);
        for (Map.Entry<Basis, Rational> entry : terms.entrySet()) {
            output.put(entry.getKey(), entry.getValue().negate());
        }
        return new ExactValue(output);
    }

    public ExactValue add(ExactValue other) {
        if (other == null) return null;
        TreeMap<Basis, Rational> output = new TreeMap<>(BASIS_ORDER);
        output.putAll(terms);
        for (Map.Entry<Basis, Rational> entry : other.terms.entrySet()) {
            output.merge(entry.getKey(), entry.getValue(), Rational::add);
        }
        return new ExactValue(output);
    }

    public ExactValue subtract(ExactValue other) {
        return other == null ? null : add(other.negate());
    }

    public ExactValue multiply(ExactValue other) {
        if (other == null) return null;
        if (terms.isEmpty() || other.terms.isEmpty()) return ZERO;
        TreeMap<Basis, Rational> output = new TreeMap<>(BASIS_ORDER);
        for (Map.Entry<Basis, Rational> left : terms.entrySet()) {
            for (Map.Entry<Basis, Rational> right : other.terms.entrySet()) {
                BigInteger product = left.getKey().radicand().multiply(right.getKey().radicand());
                SquareParts square = squareParts(product);
                if (square == null) return null;
                Basis basis = new Basis(square.inside(),
                        left.getKey().piPower() + right.getKey().piPower(),
                        left.getKey().ePower() + right.getKey().ePower());
                if (basis.piPower() > 4 || basis.ePower() > 4) return null;
                Rational coefficient = left.getValue().multiply(right.getValue())
                        .multiply(new Rational(square.outside(), BigInteger.ONE));
                output.merge(basis, coefficient, Rational::add);
            }
        }
        return new ExactValue(output);
    }

    /** Divides by one exact monomial. General rationalization is intentionally unsupported. */
    public ExactValue divide(ExactValue other) {
        if (other == null || other.terms.size() != 1) return null;
        Map.Entry<Basis, Rational> divisor = other.terms.entrySet().iterator().next();
        if (divisor.getValue().numerator().signum() == 0) return null;
        TreeMap<Basis, Rational> output = new TreeMap<>(BASIS_ORDER);
        for (Map.Entry<Basis, Rational> numerator : terms.entrySet()) {
            int piPower = numerator.getKey().piPower() - divisor.getKey().piPower();
            int ePower = numerator.getKey().ePower() - divisor.getKey().ePower();
            if (piPower < 0 || ePower < 0) return null;
            BigInteger radicandProduct = numerator.getKey().radicand()
                    .multiply(divisor.getKey().radicand());
            SquareParts square = squareParts(radicandProduct);
            if (square == null) return null;
            Rational coefficient = numerator.getValue().divide(divisor.getValue())
                    .multiply(new Rational(square.outside(), divisor.getKey().radicand()));
            Basis basis = new Basis(square.inside(), piPower, ePower);
            output.merge(basis, coefficient, Rational::add);
        }
        return new ExactValue(output);
    }

    public ExactValue pow(int exponent) {
        if (Math.abs(exponent) > 32) return null;
        if (exponent == 0) return ONE;
        ExactValue result = ONE;
        ExactValue factor = this;
        int remaining = Math.abs(exponent);
        while (remaining > 0) {
            if ((remaining & 1) != 0) {
                result = result.multiply(factor);
                if (result == null) return null;
            }
            remaining >>>= 1;
            if (remaining > 0) {
                factor = factor.multiply(factor);
                if (factor == null) return null;
            }
        }
        return exponent < 0 ? ONE.divide(result) : result;
    }

    public boolean isRational() {
        return terms.isEmpty() || (terms.size() == 1 && terms.firstKey().isOne());
    }

    public Rational rational() {
        if (terms.isEmpty()) return Rational.ZERO;
        if (!isRational()) return null;
        return terms.get(ONE_BASIS);
    }

    /** Returns c for an exact value c*pi, otherwise {@code null}. */
    public Rational rationalMultipleOfPi() {
        if (terms.isEmpty()) return Rational.ZERO;
        if (terms.size() != 1) return null;
        Map.Entry<Basis, Rational> entry = terms.entrySet().iterator().next();
        Basis basis = entry.getKey();
        return basis.radicand().equals(BigInteger.ONE) && basis.piPower() == 1
                && basis.ePower() == 0 ? entry.getValue() : null;
    }

    public boolean isZero() {
        return terms.isEmpty();
    }

    public double toDouble() {
        double result = 0.0;
        for (Map.Entry<Basis, Rational> entry : terms.entrySet()) {
            Basis basis = entry.getKey();
            double value = entry.getValue().toDouble() * Math.sqrt(basis.radicand().doubleValue());
            if (basis.piPower() != 0) value *= Math.pow(Math.PI, basis.piPower());
            if (basis.ePower() != 0) value *= Math.pow(Math.E, basis.ePower());
            result += value;
        }
        return result;
    }

    /** Conservative fx-991 natural-display bounds for roots and coefficients. */
    public boolean isDisplayable() {
        if (terms.size() > 2) return false;
        for (Map.Entry<Basis, Rational> entry : terms.entrySet()) {
            Basis basis = entry.getKey();
            Rational coefficient = entry.getValue();
            if (basis.piPower() > 1 || basis.ePower() > 0) return false;
            if (basis.radicand().compareTo(BigInteger.valueOf(999)) > 0) return false;
            if (coefficient.numerator().abs().compareTo(BigInteger.valueOf(999)) > 0
                    || coefficient.denominator().compareTo(BigInteger.valueOf(999)) > 0) return false;
        }
        return true;
    }

    public String display() {
        if (terms.isEmpty()) return "0";
        StringBuilder output = new StringBuilder();
        for (Map.Entry<Basis, Rational> entry : terms.entrySet()) {
            Rational coefficient = entry.getValue();
            boolean negative = coefficient.numerator().signum() < 0;
            if (output.length() > 0) output.append(negative ? '−' : '+');
            else if (negative) output.append('−');
            output.append(termDisplay(coefficient, entry.getKey()));
        }
        return output.toString();
    }

    private static String termDisplay(Rational signedCoefficient, Basis basis) {
        BigInteger numerator = signedCoefficient.numerator().abs();
        BigInteger denominator = signedCoefficient.denominator();
        if (basis.isOne()) {
            return denominator.equals(BigInteger.ONE)
                    ? numerator.toString() : numerator + "/" + denominator;
        }
        StringBuilder symbol = new StringBuilder();
        if (!basis.radicand().equals(BigInteger.ONE)) {
            symbol.append('√').append(basis.radicand());
        }
        appendPower(symbol, "π", basis.piPower());
        appendPower(symbol, "e", basis.ePower());
        String coefficient = numerator.equals(BigInteger.ONE) ? "" : numerator.toString();
        String value = coefficient + symbol;
        return denominator.equals(BigInteger.ONE) ? value : value + "/" + denominator;
    }

    private static void appendPower(StringBuilder output, String symbol, int power) {
        if (power <= 0) return;
        output.append(symbol);
        if (power > 1) output.append('^').append(power);
    }

    private static SquareParts squareParts(BigInteger value) {
        if (value.signum() <= 0 || value.compareTo(FACTOR_LIMIT) > 0) return null;
        long remaining = value.longValueExact();
        long outside = 1L;
        long inside = 1L;
        for (long factor = 2L; factor <= remaining / factor; factor += factor == 2L ? 1L : 2L) {
            int count = 0;
            while (remaining % factor == 0L) {
                remaining /= factor;
                count++;
            }
            for (int pair = 0; pair < count / 2; pair++) outside = Math.multiplyExact(outside, factor);
            if ((count & 1) != 0) inside = Math.multiplyExact(inside, factor);
        }
        if (remaining > 1L) inside = Math.multiplyExact(inside, remaining);
        return new SquareParts(BigInteger.valueOf(outside), BigInteger.valueOf(inside));
    }

    private static BigInteger exactNthRoot(BigInteger value, int degree) {
        if (value.signum() == 0) return BigInteger.ZERO;
        if (value.compareTo(FACTOR_LIMIT) > 0) return null;
        long target = value.longValueExact();
        long estimate = Math.max(1L, Math.round(Math.pow(target, 1.0 / degree)));
        for (long candidate = Math.max(1L, estimate - 2L); candidate <= estimate + 2L; candidate++) {
            BigInteger integer = BigInteger.valueOf(candidate);
            if (integer.pow(degree).equals(value)) return integer;
        }
        return null;
    }

    @Override public boolean equals(Object object) {
        return object instanceof ExactValue other && terms.equals(other.terms);
    }

    @Override public int hashCode() {
        return Objects.hash(terms);
    }

    @Override public String toString() {
        return display();
    }

    private record Basis(BigInteger radicand, int piPower, int ePower) {
        private Basis {
            if (radicand == null || radicand.signum() <= 0 || piPower < 0 || ePower < 0) {
                throw new IllegalArgumentException("basis");
            }
        }

        private boolean isOne() {
            return radicand.equals(BigInteger.ONE) && piPower == 0 && ePower == 0;
        }
    }

    private record SquareParts(BigInteger outside, BigInteger inside) { }
}

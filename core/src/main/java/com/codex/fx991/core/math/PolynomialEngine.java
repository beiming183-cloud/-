package com.codex.fx991.core.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Degree 2..4 polynomial equation, extrema, and inequality engine. */
public final class PolynomialEngine {
    private PolynomialEngine() {}

    public enum Relation { GREATER, LESS, GREATER_OR_EQUAL, LESS_OR_EQUAL }

    /** Coefficients are ordered highest degree first. */
    public static List<ComplexValue> roots(double... coefficients) {
        double[] normalized = trimLeadingZeros(coefficients);
        int degree = normalized.length - 1;
        if (degree < 1 || degree > 4) throw new IllegalArgumentException("Degree must be 1..4");
        if (degree == 1) return com.codex.fx991.core.Compat.list(new ComplexValue(-normalized[1] / normalized[0], 0.0));
        if (degree == 2) return quadraticRoots(normalized[0], normalized[1], normalized[2]);

        double leading = normalized[0];
        for (int i = 0; i < normalized.length; i++) normalized[i] /= leading;
        double radius = 1.0;
        for (int i = 1; i < normalized.length; i++) radius = Math.max(radius, 1.0 + Math.abs(normalized[i]));
        ComplexValue[] roots = new ComplexValue[degree];
        double phase = 0.37;
        for (int i = 0; i < degree; i++) {
            roots[i] = ComplexValue.polar(radius, phase + 2.0 * Math.PI * i / degree);
        }
        for (int iteration = 0; iteration < 300; iteration++) {
            double maxDelta = 0.0;
            ComplexValue[] next = Arrays.copyOf(roots, degree);
            for (int i = 0; i < degree; i++) {
                ComplexValue denominator = ComplexValue.ONE;
                for (int j = 0; j < degree; j++) if (i != j) denominator = denominator.multiply(roots[i].subtract(roots[j]));
                if (denominator.abs() < 1e-18) denominator = denominator.add(new ComplexValue(1e-12, 1e-12));
                ComplexValue delta = evaluate(normalized, roots[i]).divide(denominator);
                next[i] = roots[i].subtract(delta);
                maxDelta = Math.max(maxDelta, delta.abs());
            }
            roots = next;
            if (maxDelta < 1e-13) break;
        }
        List<ComplexValue> result = new ArrayList<>();
        for (ComplexValue root : roots) {
            double real = snap(root.real());
            double imaginary = snap(root.imaginary());
            result.add(new ComplexValue(real, imaginary));
        }
        result.sort(Comparator.comparingDouble(ComplexValue::real)
                .thenComparingDouble(ComplexValue::imaginary));
        return result;
    }

    private static List<ComplexValue> quadraticRoots(double a, double b, double c) {
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant >= 0.0) {
            double root = Math.sqrt(discriminant);
            double q = -0.5 * (b + Math.copySign(root, b));
            double first = q / a;
            double second = q == 0.0 ? -b / (2.0 * a) : c / q;
            List<ComplexValue> result = new ArrayList<>(com.codex.fx991.core.Compat.list(
                    new ComplexValue(snap(first), 0.0), new ComplexValue(snap(second), 0.0)));
            result.sort(Comparator.comparingDouble(ComplexValue::real));
            return result;
        }
        double real = -b / (2.0 * a);
        double imaginary = Math.sqrt(-discriminant) / (2.0 * Math.abs(a));
        return com.codex.fx991.core.Compat.list(new ComplexValue(real, -imaginary), new ComplexValue(real, imaginary));
    }

    public static List<Extremum> extrema(double... coefficients) {
        double[] normalized = trimLeadingZeros(coefficients);
        int degree = normalized.length - 1;
        if (degree < 2 || degree > 3) return com.codex.fx991.core.Compat.list();
        double[] derivative = new double[degree];
        for (int i = 0; i < degree; i++) derivative[i] = normalized[i] * (degree - i);
        List<Extremum> result = new ArrayList<>();
        for (ComplexValue root : roots(derivative)) {
            if (Math.abs(root.imaginary()) > 1e-9) continue;
            double x = root.real();
            double secondDerivative = secondDerivative(normalized, x);
            if (Math.abs(secondDerivative) < 1e-12) continue;
            result.add(new Extremum(x, evaluateReal(normalized, x), secondDerivative > 0.0));
        }
        result.sort(Comparator.comparingDouble(Extremum::x));
        return result;
    }

    public static InequalitySolution solveInequality(Relation relation, double... coefficients) {
        double[] normalized = trimLeadingZeros(coefficients);
        List<Double> boundaries = new ArrayList<>();
        for (ComplexValue root : roots(normalized)) {
            if (Math.abs(root.imaginary()) <= 1e-8) {
                double value = snap(root.real());
                if (boundaries.isEmpty() || Math.abs(value - boundaries.get(boundaries.size() - 1)) > 1e-8) {
                    boundaries.add(value);
                }
            }
        }
        boundaries.sort(Double::compare);
        List<Interval> intervals = new ArrayList<>();
        for (int segment = 0; segment <= boundaries.size(); segment++) {
            double lower = segment == 0 ? Double.NEGATIVE_INFINITY : boundaries.get(segment - 1);
            double upper = segment == boundaries.size() ? Double.POSITIVE_INFINITY : boundaries.get(segment);
            double sample;
            if (!Double.isFinite(lower)) sample = upper - Math.max(1.0, Math.abs(upper));
            else if (!Double.isFinite(upper)) sample = lower + Math.max(1.0, Math.abs(lower));
            else sample = lower + (upper - lower) * 0.5;
            if (test(relation, evaluateReal(normalized, sample), false)) {
                boolean includeLower = Double.isFinite(lower) && test(relation, evaluateReal(normalized, lower), true);
                boolean includeUpper = Double.isFinite(upper) && test(relation, evaluateReal(normalized, upper), true);
                intervals.add(new Interval(lower, includeLower, upper, includeUpper));
            }
        }
        // Include isolated even-multiplicity roots for non-strict relations.
        if (relation == Relation.GREATER_OR_EQUAL || relation == Relation.LESS_OR_EQUAL) {
            for (double root : boundaries) {
                if (!contains(intervals, root) && test(relation, evaluateReal(normalized, root), true)) {
                    intervals.add(new Interval(root, true, root, true));
                }
            }
        }
        intervals.sort(Comparator.comparingDouble(Interval::lower));
        return new InequalitySolution(intervals);
    }

    public static double evaluateReal(double[] coefficients, double x) {
        double result = 0.0;
        for (double coefficient : coefficients) {
            result = com.codex.fx991.core.Compat.multiplyAdd(result, x, coefficient);
        }
        return result;
    }

    private static ComplexValue evaluate(double[] coefficients, ComplexValue x) {
        ComplexValue result = ComplexValue.ZERO;
        for (double coefficient : coefficients) result = result.multiply(x).add(new ComplexValue(coefficient, 0.0));
        return result;
    }

    private static double secondDerivative(double[] coefficients, double x) {
        int degree = coefficients.length - 1;
        double result = 0.0;
        for (int i = 0; i < degree - 1; i++) {
            int exponent = degree - i;
            result = com.codex.fx991.core.Compat.multiplyAdd(
                    result, x, coefficients[i] * exponent * (exponent - 1));
        }
        return result;
    }

    private static boolean test(Relation relation, double value, boolean boundary) {
        double epsilon = boundary ? 1e-8 : 0.0;
        return switch (relation) {
            case GREATER -> value > epsilon;
            case LESS -> value < -epsilon;
            case GREATER_OR_EQUAL -> value >= -epsilon;
            case LESS_OR_EQUAL -> value <= epsilon;
        };
    }

    private static boolean contains(List<Interval> intervals, double value) {
        for (Interval interval : intervals) {
            boolean afterLower = value > interval.lower || (value == interval.lower && interval.includeLower);
            boolean beforeUpper = value < interval.upper || (value == interval.upper && interval.includeUpper);
            if (afterLower && beforeUpper) return true;
        }
        return false;
    }

    private static double[] trimLeadingZeros(double[] coefficients) {
        if (coefficients == null || coefficients.length < 2) throw new IllegalArgumentException("No polynomial");
        int first = 0;
        while (first < coefficients.length - 1 && Math.abs(coefficients[first]) < 1e-15) first++;
        return Arrays.copyOfRange(coefficients, first, coefficients.length);
    }

    private static double snap(double value) {
        if (Math.abs(value) < 1e-12) return 0.0;
        double integer = Math.rint(value);
        return Math.abs(value - integer) < 1e-11 ? integer : value;
    }

    public record Extremum(double x, double y, boolean minimum) {}
    public record Interval(double lower, boolean includeLower, double upper, boolean includeUpper) {}
    public record InequalitySolution(List<Interval> intervals) {
        public InequalitySolution { intervals = com.codex.fx991.core.Compat.copyList(intervals); }
        public boolean isNoSolution() { return intervals.isEmpty(); }
        public boolean isAllReals() {
            return intervals.size() == 1
                    && intervals.get(0).lower() == Double.NEGATIVE_INFINITY
                    && intervals.get(0).upper() == Double.POSITIVE_INFINITY;
        }
    }
}

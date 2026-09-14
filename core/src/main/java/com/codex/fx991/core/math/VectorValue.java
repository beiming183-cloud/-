package com.codex.fx991.core.math;

import java.util.Arrays;

/** Immutable 2D or 3D vector. */
public final class VectorValue {
    private final double[] values;

    public VectorValue(double... values) {
        if (values == null || (values.length != 2 && values.length != 3)) {
            throw new IllegalArgumentException("Vector dimension must be 2 or 3");
        }
        this.values = Arrays.copyOf(values, values.length);
    }

    public int dimension() { return values.length; }
    public double get(int index) { return values[index]; }
    public double[] toArray() { return Arrays.copyOf(values, values.length); }

    public VectorValue add(VectorValue other) {
        requireSameDimension(other);
        double[] result = new double[dimension()];
        for (int i = 0; i < dimension(); i++) result[i] = values[i] + other.values[i];
        return new VectorValue(result);
    }

    public VectorValue subtract(VectorValue other) {
        requireSameDimension(other);
        double[] result = new double[dimension()];
        for (int i = 0; i < dimension(); i++) result[i] = values[i] - other.values[i];
        return new VectorValue(result);
    }

    public VectorValue multiply(double scalar) {
        double[] result = toArray();
        for (int i = 0; i < result.length; i++) result[i] *= scalar;
        return new VectorValue(result);
    }

    public double dot(VectorValue other) {
        requireSameDimension(other);
        double sum = 0.0;
        for (int i = 0; i < dimension(); i++) {
            sum = com.codex.fx991.core.Compat.multiplyAdd(values[i], other.values[i], sum);
        }
        return sum;
    }

    public VectorValue cross(VectorValue other) {
        requireSameDimension(other);
        double ax = values[0], ay = values[1], az = dimension() == 3 ? values[2] : 0.0;
        double bx = other.values[0], by = other.values[1], bz = other.dimension() == 3 ? other.values[2] : 0.0;
        double x = ay * bz - az * by;
        double y = az * bx - ax * bz;
        double z = ax * by - ay * bx;
        return new VectorValue(x, y, z);
    }

    public double magnitude() {
        double sum = 0.0;
        for (double value : values) {
            sum = com.codex.fx991.core.Compat.multiplyAdd(value, value, sum);
        }
        return Math.sqrt(sum);
    }

    public VectorValue unit() {
        double magnitude = magnitude();
        if (magnitude == 0.0) throw new ArithmeticException("Zero vector");
        return multiply(1.0 / magnitude);
    }

    public double angleRadians(VectorValue other) {
        double denominator = magnitude() * other.magnitude();
        if (denominator == 0.0) throw new ArithmeticException("Zero vector");
        double cosine = Math.max(-1.0, Math.min(1.0, dot(other) / denominator));
        return Math.acos(cosine);
    }

    private void requireSameDimension(VectorValue other) {
        if (dimension() != other.dimension()) {
            throw new IllegalArgumentException("Vector dimensions differ");
        }
    }
}

package com.codex.fx991.core.math;

import java.util.Arrays;

/** Immutable real matrix, limited by the CN CW profile to at most 4 x 4. */
public final class MatrixValue {
    private final double[][] values;

    public MatrixValue(double[][] source) {
        if (source == null || source.length == 0 || source.length > 4) {
            throw new IllegalArgumentException("Matrix rows must be 1..4");
        }
        int columns = source[0].length;
        if (columns == 0 || columns > 4) throw new IllegalArgumentException("Matrix columns must be 1..4");
        values = new double[source.length][columns];
        for (int row = 0; row < source.length; row++) {
            if (source[row].length != columns) throw new IllegalArgumentException("Ragged matrix");
            for (double value : source[row]) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite matrix value");
            }
            values[row] = Arrays.copyOf(source[row], columns);
        }
    }

    public static MatrixValue identity(int size) {
        if (size < 1 || size > 4) throw new IllegalArgumentException("Identity size must be 1..4");
        double[][] result = new double[size][size];
        for (int i = 0; i < size; i++) result[i][i] = 1.0;
        return new MatrixValue(result);
    }

    public int rows() { return values.length; }
    public int columns() { return values[0].length; }
    public double get(int row, int column) { return values[row][column]; }
    public double[][] toArray() {
        double[][] copy = new double[rows()][columns()];
        for (int row = 0; row < rows(); row++) copy[row] = Arrays.copyOf(values[row], columns());
        return copy;
    }

    public MatrixValue add(MatrixValue other) {
        requireSameDimensions(other);
        double[][] result = new double[rows()][columns()];
        for (int row = 0; row < rows(); row++)
            for (int column = 0; column < columns(); column++)
                result[row][column] = values[row][column] + other.values[row][column];
        return new MatrixValue(result);
    }

    public MatrixValue subtract(MatrixValue other) {
        requireSameDimensions(other);
        double[][] result = new double[rows()][columns()];
        for (int row = 0; row < rows(); row++)
            for (int column = 0; column < columns(); column++)
                result[row][column] = values[row][column] - other.values[row][column];
        return new MatrixValue(result);
    }

    public MatrixValue multiply(MatrixValue other) {
        if (columns() != other.rows()) throw new DimensionException("Matrix multiplication");
        double[][] result = new double[rows()][other.columns()];
        for (int row = 0; row < rows(); row++) {
            for (int column = 0; column < other.columns(); column++) {
                double sum = 0.0;
                for (int index = 0; index < columns(); index++) {
                    sum = com.codex.fx991.core.Compat.multiplyAdd(
                            values[row][index], other.values[index][column], sum);
                }
                result[row][column] = sum;
            }
        }
        return new MatrixValue(result);
    }

    public MatrixValue multiply(double scalar) {
        double[][] result = toArray();
        for (int row = 0; row < rows(); row++)
            for (int column = 0; column < columns(); column++) result[row][column] *= scalar;
        return new MatrixValue(result);
    }

    public MatrixValue transpose() {
        double[][] result = new double[columns()][rows()];
        for (int row = 0; row < rows(); row++)
            for (int column = 0; column < columns(); column++) result[column][row] = values[row][column];
        return new MatrixValue(result);
    }

    public MatrixValue elementAbs() {
        double[][] result = toArray();
        for (int row = 0; row < rows(); row++)
            for (int column = 0; column < columns(); column++) result[row][column] = Math.abs(result[row][column]);
        return new MatrixValue(result);
    }

    public double determinant() {
        requireSquare();
        double[][] work = toArray();
        double determinant = 1.0;
        int sign = 1;
        for (int pivot = 0; pivot < rows(); pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < rows(); row++) {
                if (Math.abs(work[row][pivot]) > Math.abs(work[best][pivot])) best = row;
            }
            if (work[best][pivot] == 0.0 || !Double.isFinite(work[best][pivot])) return 0.0;
            if (best != pivot) {
                double[] swap = work[pivot]; work[pivot] = work[best]; work[best] = swap;
                sign = -sign;
            }
            double value = work[pivot][pivot];
            determinant *= value;
            for (int row = pivot + 1; row < rows(); row++) {
                double factor = work[row][pivot] / value;
                for (int column = pivot + 1; column < columns(); column++) {
                    work[row][column] -= factor * work[pivot][column];
                }
            }
        }
        return sign * determinant;
    }

    public MatrixValue inverse() {
        requireSquare();
        int size = rows();
        double[][] augmented = new double[size][size * 2];
        for (int row = 0; row < size; row++) {
            System.arraycopy(values[row], 0, augmented[row], 0, size);
            augmented[row][size + row] = 1.0;
        }
        for (int pivot = 0; pivot < size; pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < size; row++)
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[best][pivot])) best = row;
            if (augmented[best][pivot] == 0.0 || !Double.isFinite(augmented[best][pivot])) {
                throw new ArithmeticException("Singular matrix");
            }
            double[] swap = augmented[pivot]; augmented[pivot] = augmented[best]; augmented[best] = swap;
            double divisor = augmented[pivot][pivot];
            for (int column = 0; column < size * 2; column++) augmented[pivot][column] /= divisor;
            for (int row = 0; row < size; row++) {
                if (row == pivot) continue;
                double factor = augmented[row][pivot];
                for (int column = 0; column < size * 2; column++) {
                    augmented[row][column] -= factor * augmented[pivot][column];
                }
            }
        }
        double[][] result = new double[size][size];
        for (int row = 0; row < size; row++)
            System.arraycopy(augmented[row], size, result[row], 0, size);
        return new MatrixValue(result);
    }

    public double[] solve(double[] rightHandSide) {
        requireSquare();
        if (rightHandSide.length != rows()) throw new DimensionException("Linear system");
        int size = rows();
        double[][] work = new double[size][size + 1];
        for (int row = 0; row < size; row++) {
            System.arraycopy(values[row], 0, work[row], 0, size);
            work[row][size] = rightHandSide[row];
        }
        for (int pivot = 0; pivot < size; pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < size; row++)
                if (Math.abs(work[row][pivot]) > Math.abs(work[best][pivot])) best = row;
            if (work[best][pivot] == 0.0 || !Double.isFinite(work[best][pivot])) {
                throw new ArithmeticException("No unique solution");
            }
            double[] swap = work[pivot]; work[pivot] = work[best]; work[best] = swap;
            for (int row = pivot + 1; row < size; row++) {
                double factor = work[row][pivot] / work[pivot][pivot];
                for (int column = pivot; column <= size; column++) {
                    work[row][column] -= factor * work[pivot][column];
                }
            }
        }
        double[] solution = new double[size];
        for (int row = size - 1; row >= 0; row--) {
            double sum = work[row][size];
            for (int column = row + 1; column < size; column++) sum -= work[row][column] * solution[column];
            solution[row] = sum / work[row][row];
        }
        return solution;
    }

    private void requireSquare() {
        if (rows() != columns()) throw new DimensionException("Square matrix required");
    }

    private void requireSameDimensions(MatrixValue other) {
        if (rows() != other.rows() || columns() != other.columns()) {
            throw new DimensionException("Matrix dimensions differ");
        }
    }

    public static final class DimensionException extends IllegalArgumentException {
        public DimensionException(String message) { super(message); }
    }
}

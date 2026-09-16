package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.mode.ApplicationMode;

/** Regression coverage for Stage 5 core-owned workflow input specifications. */
public final class CnCwWorkflowSpecSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwWorkflowSpecSuite().run();
    }

    private void run() {
        statisticsSpecs();
        equationSpecs();
        matrixAndVectorSpecs();
        unsupportedWorkflowReturnsNull();
        System.out.println("PASS " + checks + " workflow-spec checks");
    }

    private void statisticsSpecs() {
        var one = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "one");
        equal(CnCwWorkflowSpec.InputLayout.SERIES, one.layout(), "one-variable series layout");
        equal("x", one.fields().get(0).label(), "one-variable x label");
        equal(1, one.minColumns(), "one-variable one column");
        check(one.hasVariableRows(), "one-variable rows are expandable");

        var two = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "two");
        equal(CnCwWorkflowSpec.InputLayout.PAIRED_SERIES, two.layout(), "two-variable paired layout");
        equal(2, two.fields().size(), "two-variable has x/y columns");
        equal("y", two.fields().get(1).label(), "two-variable y label");
        equal(2, two.minRows(), "two-variable requires two observations");

        var regression = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "regression");
        equal("线性回归", regression.title(), "regression title");
        equal(CnCwWorkflowSpec.InputLayout.PAIRED_SERIES, regression.layout(), "regression paired layout");
    }

    private void equationSpecs() {
        var polynomial = CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "polynomial");
        equal(CnCwWorkflowSpec.InputLayout.COEFFICIENTS, polynomial.layout(), "polynomial coefficients layout");
        equal(3, polynomial.minRows(), "quadratic minimum coefficient count");
        equal(5, polynomial.maxRows(), "quartic maximum coefficient count");

        var simultaneous = CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "simultaneous");
        equal(2, simultaneous.minRows(), "simultaneous minimum dimension");
        equal(4, simultaneous.maxRows(), "simultaneous maximum dimension");
        equal("元数", simultaneous.fields().get(0).label(), "simultaneous dimension field");

        var solve = CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "solve");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, solve.layout(), "solve fixed fields");
        equal(2, solve.fields().size(), "solve expression and initial guess");
        equal("f(x)", solve.fields().get(0).label(), "solve expression label");
        equal("初值", solve.fields().get(1).label(), "solve initial label");
    }

    private void matrixAndVectorSpecs() {
        var matrix = CnCwWorkflowSpec.forCommand(ApplicationMode.MATRIX, "calculate");
        equal(CnCwWorkflowSpec.InputLayout.GRID, matrix.layout(), "matrix grid layout");
        equal(1, matrix.minRows(), "matrix minimum rows");
        equal(4, matrix.maxRows(), "matrix maximum rows");
        equal(1, matrix.minColumns(), "matrix minimum columns");
        equal(4, matrix.maxColumns(), "matrix maximum columns");

        var vector = CnCwWorkflowSpec.forCommand(ApplicationMode.VECTOR, "calculate");
        equal(CnCwWorkflowSpec.InputLayout.VECTOR_SET, vector.layout(), "vector-set layout");
        equal(1, vector.minRows(), "one vector minimum");
        equal(2, vector.maxRows(), "two vectors maximum");
        equal(2, vector.minColumns(), "2D minimum");
        equal(3, vector.maxColumns(), "3D maximum");
    }

    private void unsupportedWorkflowReturnsNull() {
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.CALCULATE, "calculate") == null,
                "ordinary calculate has no structured workflow spec");
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "missing") == null,
                "unknown statistics command is rejected");
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private void equal(Object expected, Object actual, String message) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}

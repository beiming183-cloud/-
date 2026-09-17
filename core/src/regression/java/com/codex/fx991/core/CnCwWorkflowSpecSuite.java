package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.mode.ApplicationMode;

/** Regression coverage for core-owned workflow input specifications/state. */
public final class CnCwWorkflowSpecSuite {
    private int checks;
    private final ScalarExpressionEngine.EvaluationContext context =
            ScalarExpressionEngine.EvaluationContext.standard();

    public static void main(String[] args) {
        new CnCwWorkflowSpecSuite().run();
    }

    private void run() {
        statisticsSpecs();
        functionTableSpecs();
        equationSpecs();
        matrixAndVectorSpecs();
        statisticsSessions();
        functionTableSessions();
        equationSessions();
        matrixAndVectorSessions();
        unsupportedWorkflowReturnsNull();
        System.out.println("PASS " + checks + " workflow-spec/session checks");
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

    private void functionTableSpecs() {
        var single = CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "single");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, single.layout(),
                "single function table uses fixed fields");
        equal(4, single.fields().size(), "single function table has four fields");
        equal("f(x)", single.fields().get(0).label(), "single table function label");
        equal("开始", single.fields().get(1).label(), "single table start label");
        equal("结束", single.fields().get(2).label(), "single table end label");
        equal("步长", single.fields().get(3).label(), "single table step label");

        var dual = CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "fg");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, dual.layout(),
                "dual function table uses fixed fields");
        equal(5, dual.fields().size(), "dual function table has five fields");
        equal("g(x)", dual.fields().get(1).label(), "dual table second function label");
        equal(5, dual.minColumns(), "dual function table fixed column count");
        equal(5, dual.maxColumns(), "dual function table max column count");
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

    private void statisticsSessions() {
        var oneSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "one");
        var one = CnCwWorkflowSession.create(oneSpec);
        equal(1, one.rows(), "one-variable session starts with one row");
        equal(1, one.columns(), "one-variable session has one column");
        one.setSelectedCell("1");
        check(one.appendRow(), "one-variable adds second row");
        one.setSelectedCell("2");
        check(one.appendRow(), "one-variable adds third row");
        one.setSelectedCell("3");
        equal("1,2,3", one.legacySource(), "one-variable serializes row-major");
        near(2.0, one.evaluate(context).primaryValue(), 0.0,
                "one-variable session delegates to statistics engine");
        check(one.move(-1, 0), "one-variable selection moves up");
        equal(1, one.selectedRow(), "one-variable focus row after move");
        check(one.removeSelectedRow(), "one-variable removes selected row");
        equal(2, one.rows(), "one-variable row count after remove");

        var pairedSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "regression");
        var paired = CnCwWorkflowSession.create(pairedSpec);
        equal(2, paired.rows(), "paired session starts with required observations");
        equal(2, paired.columns(), "paired session has x/y columns");
        paired.setCell(0, 0, "1");
        paired.setCell(0, 1, "3");
        paired.setCell(1, 0, "2");
        paired.setCell(1, 1, "5");
        check(paired.appendRow(), "paired session can append observation");
        paired.setCell(2, 0, "3");
        paired.setCell(2, 1, "7");
        equal("1,3,2,5,3,7", paired.legacySource(), "paired statistics serialization");
        near(1.0, paired.evaluate(context).primaryValue(), 1e-12,
                "paired session delegates to regression engine");

        var incomplete = CnCwWorkflowSession.create(oneSpec);
        check(!incomplete.isComplete(), "blank statistics row is incomplete");
        boolean failedClosed = false;
        try {
            incomplete.legacySource();
        } catch (IllegalStateException expected) {
            failedClosed = true;
        }
        check(failedClosed, "incomplete statistics session cannot serialize silently");
    }

    private void functionTableSessions() {
        var single = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "single"));
        equal(1, single.rows(), "single table has one field row");
        equal(4, single.columns(), "single table has four columns");
        single.setCell(0, 0, "x^2");
        single.setCell(0, 1, "1");
        single.setCell(0, 2, "3");
        single.setCell(0, 3, "1");
        equal("x^2,1,3,1", single.legacySource(), "single table serializes in evaluator order");
        near(1.0, single.evaluate(context).primaryValue(), 0.0,
                "single table session delegates to function table engine");

        var dual = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "fg"));
        equal(5, dual.columns(), "dual table has five columns");
        dual.setCell(0, 0, "x");
        dual.setCell(0, 1, "2*x");
        dual.setCell(0, 2, "0");
        dual.setCell(0, 3, "2");
        dual.setCell(0, 4, "1");
        equal("x,2*x,0,2,1", dual.legacySource(), "dual table serializes in evaluator order");
        near(0.0, dual.evaluate(context).primaryValue(), 0.0,
                "dual table session delegates to function table engine");
    }

    private void equationSessions() {
        var polynomial = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "polynomial"));
        polynomial.setCell(0, 0, "1");
        polynomial.setCell(1, 0, "2");
        polynomial.setCell(2, 0, "-3");
        equal("1,2,-3", polynomial.legacySource(), "polynomial coefficients serialize directly");
        equal(2, polynomial.evaluate(context).items().size(), "quadratic session returns two roots");
        check(polynomial.resizeGrid(4, 1), "polynomial can resize to cubic coefficient count");
        equal(4, polynomial.rows(), "polynomial resized coefficient rows");

        var simultaneous = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "simultaneous"));
        equal(2, simultaneous.rows(), "simultaneous defaults to two variables");
        equal(3, simultaneous.columns(), "two-variable system has augmented column");
        simultaneous.setCell(0, 0, "1");
        simultaneous.setCell(0, 1, "1");
        simultaneous.setCell(0, 2, "5");
        simultaneous.setCell(1, 0, "2");
        simultaneous.setCell(1, 1, "-1");
        simultaneous.setCell(1, 2, "1");
        equal("2,1,1,5,2,-1,1", simultaneous.legacySource(),
                "simultaneous session prefixes dimension");
        near(2.0, simultaneous.evaluate(context).primaryValue(), 1e-12,
                "simultaneous session delegates to equation engine");
        check(simultaneous.setEquationDimension(3), "simultaneous can switch to three variables");
        equal(3, simultaneous.rows(), "three-variable equation rows");
        equal(4, simultaneous.columns(), "three-variable augmented columns");
        check(!simultaneous.resizeGrid(3, 3), "simultaneous rejects invalid non-augmented shape");

        var solve = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "solve"));
        solve.setCell(0, 0, "x^2-16");
        solve.setCell(0, 1, "1");
        equal("x^2-16,1", solve.legacySource(), "SOLVE fixed fields serialize in order");
        near(4.0, solve.evaluate(context).primaryValue(), 1e-9,
                "SOLVE session delegates to numeric solver");
    }

    private void matrixAndVectorSessions() {
        var matrix = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.MATRIX, "calculate"));
        check(matrix.resizeGrid(2, 2), "matrix can choose 2x2 shape");
        matrix.setCell(0, 0, "2");
        matrix.setCell(0, 1, "1");
        matrix.setCell(1, 0, "1");
        matrix.setCell(1, 1, "1");
        equal("2,2,2,1,1,1", matrix.legacySource(), "matrix serializes dimensions and cells");
        near(1.0, matrix.evaluate(context).primaryValue(), 0.0,
                "matrix session delegates to matrix engine");

        var vector = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.VECTOR, "calculate"));
        equal(1, vector.rows(), "vector defaults to one vector");
        equal(2, vector.columns(), "vector defaults to 2D");
        check(vector.resizeGrid(2, 2), "vector can switch to two 2D vectors");
        vector.setCell(0, 0, "1");
        vector.setCell(0, 1, "2");
        vector.setCell(1, 0, "3");
        vector.setCell(1, 1, "4");
        equal("1,2,3,4", vector.legacySource(), "two-vector row-major serialization");
        near(11.0, vector.evaluate(context).primaryValue(), 0.0,
                "vector session delegates to vector engine");
    }

    private void unsupportedWorkflowReturnsNull() {
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.CALCULATE, "calculate") == null,
                "ordinary calculate has no structured workflow spec");
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS, "missing") == null,
                "unknown statistics command is rejected");
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "missing") == null,
                "unknown function-table command is rejected");
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

    private void near(double expected, double actual, double tolerance, String message) {
        checks++;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}

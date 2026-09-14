package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.mode.ApplicationMode;

/** Manual-shaped checks for structured application bridge workflows. */
public final class CnCwModeEngineSuite {
    private int checks;
    private final ScalarExpressionEngine.EvaluationContext context =
            ScalarExpressionEngine.EvaluationContext.standard();

    public static void main(String[] args) { new CnCwModeEngineSuite().run(); }

    private void run() {
        splitKeepsFunctionArguments();
        statisticsWorkflows();
        distributionWorkflows();
        tableWorkflow();
        equationWorkflows();
        inequalityWorkflow();
        matrixVectorAndRatioWorkflows();
        System.out.println("PASS " + checks + " structured mode checks");
    }

    private void splitKeepsFunctionArguments() {
        var fields = CnCwModeEngine.splitTopLevel("log(2,16),0,2,1");
        equal(4, fields.size(), "top-level field count");
        equal("log(2,16)", fields.get(0), "nested comma retained");
    }

    private void statisticsWorkflows() {
        var one = evaluate(ApplicationMode.STATISTICS, "one", "1,2,3,4");
        near(2.5, one.primaryValue(), 0.0, "one-variable mean");
        check(one.display().contains("n=4"), "one-variable count rendered");

        var regression = evaluate(ApplicationMode.STATISTICS, "regression", "1,3,2,5,3,7");
        near(1.0, regression.primaryValue(), 1e-12, "perfect linear correlation");
        check(regression.display().contains("a=2"), "linear slope rendered");
    }

    private void distributionWorkflows() {
        var binomial = evaluate(ApplicationMode.DISTRIBUTION, "binomial", "2,5,0.5");
        near(0.3125, binomial.primaryValue(), 1e-15, "binomial PMF");
        var normal = evaluate(ApplicationMode.DISTRIBUTION, "normal", "0,0,1");
        near(0.3989422804014327, normal.primaryValue(), 1e-12, "normal PDF");
        var poisson = evaluate(ApplicationMode.DISTRIBUTION, "poisson", "3,2.5");
        near(0.213763017249736, poisson.primaryValue(), 2e-14, "Poisson PMF");
    }

    private void tableWorkflow() {
        var table = evaluate(ApplicationMode.FUNCTION_TABLE, "single", "x^2,-1,1,0.5");
        near(1.0, table.primaryValue(), 0.0, "table first f value");
        check(table.display().contains("rows=5"), "table row limit calculation");
    }

    private void equationWorkflows() {
        var polynomial = evaluate(ApplicationMode.EQUATION, "polynomial", "1,2,-3");
        check(polynomial.display().contains("x1="), "polynomial roots rendered");
        var simultaneous = evaluate(ApplicationMode.EQUATION, "simultaneous",
                "2,1,1,5,2,-1,1");
        near(2.0, simultaneous.primaryValue(), 1e-12, "two-variable system first root");
        check(simultaneous.display().contains("x2=3"), "system second root rendered");
        var solve = evaluate(ApplicationMode.EQUATION, "solve", "x^2-16,1");
        near(4.0, solve.primaryValue(), 1e-9, "SOLVE workflow");
    }

    private void inequalityWorkflow() {
        var result = evaluate(ApplicationMode.INEQUALITY, "quadratic", "3,1,2,-3");
        check(result.display().contains("−3") || result.display().contains("-3"),
                "inequality left boundary");
        check(result.display().contains("1"), "inequality right boundary");
    }

    private void matrixVectorAndRatioWorkflows() {
        var matrix = evaluate(ApplicationMode.MATRIX, "calculate", "2,2,2,1,1,1");
        near(1.0, matrix.primaryValue(), 0.0, "matrix determinant");
        var vector = evaluate(ApplicationMode.VECTOR, "calculate", "1,2,3,4");
        near(11.0, vector.primaryValue(), 0.0, "two-dimensional dot product");
        var ratio = evaluate(ApplicationMode.RATIO, "a:b=x:d", "3,8,12");
        near(4.5, ratio.primaryValue(), 0.0, "ratio unknown");
    }

    private CnCwModeEngine.ModeResult evaluate(ApplicationMode mode, String command, String source) {
        return CnCwModeEngine.evaluate(mode, command, source, context);
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

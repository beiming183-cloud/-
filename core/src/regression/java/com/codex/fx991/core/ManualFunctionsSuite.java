package com.codex.fx991.core;

import com.codex.fx991.core.math.ManualFunctions;
import com.codex.fx991.core.math.CalculationError;
import com.codex.fx991.core.math.CalculationException;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.math.StatementEngine;

import java.util.HashMap;
import java.util.Random;

/** Golden checks for manual utilities not represented by a single scalar value. */
public final class ManualFunctionsSuite {
    private int checks;

    public static void main(String[] args) { new ManualFunctionsSuite().run(); }

    private void run() {
        remainderDivision();
        dmsConversion();
        coordinateConversion();
        engineeringNotation();
        assignmentAndMultiStatements();
        scalarParserExtensions();
        exactScalarResults();
        catalogFunctionsReachScalarEngine();
        manualDomainsAndErrors();
        System.out.println("PASS " + checks + " manual utility checks");
    }

    private void remainderDivision() {
        var result = ManualFunctions.divideWithRemainder(5, 2);
        check(result.remainderMode(), "5 ÷R 2 has pair result");
        near(2, result.quotient(), 0, "quotient");
        near(1, result.remainder(), 0, "remainder");
        near(2, result.ansValue(), 0, "Ans stores quotient only");
        var fallback = ManualFunctions.divideWithRemainder(-5, 2);
        check(!fallback.remainderMode(), "negative quotient falls back to division");
        near(-2.5, fallback.quotient(), 0, "ordinary division fallback");
    }

    private void dmsConversion() {
        var dms = ManualFunctions.fromDecimalDegrees(1.25);
        equal("1°15′0″", dms.display(), "decimal to DMS");
        near(1.25, dms.decimalDegrees(), 0, "DMS round trip");
        near(-12.5, ManualFunctions.toDecimalDegrees(-1, 12, 30, 0), 0,
                "negative DMS input");
    }

    private void coordinateConversion() {
        var polar = ManualFunctions.polar(Math.sqrt(2), Math.sqrt(2), AngleUnit.DEG);
        near(2, polar.first(), 1e-12, "Pol radius");
        near(45, polar.second(), 1e-12, "Pol angle");
        var rectangular = ManualFunctions.rectangular(2, 45, AngleUnit.DEG);
        near(Math.sqrt(2), rectangular.first(), 1e-12, "Rec x");
        near(Math.sqrt(2), rectangular.second(), 1e-12, "Rec y");
    }

    private void engineeringNotation() {
        var engineering = ManualFunctions.engineering(1234);
        near(1.234, engineering.mantissa(), 1e-15, "engineering mantissa");
        equal(3, engineering.exponent(), "engineering exponent multiple of three");
        equal("k", engineering.symbol(), "engineering symbol");
        near(5000, ManualFunctions.applyEngineeringSuffix(5, "k"), 0,
                "engineering input suffix");
    }

    private void assignmentAndMultiStatements() {
        var outcome = StatementEngine.evaluate("5->A:A*2:Ans+1", new HashMap<>(),
                AngleUnit.DEG, 0, new Random(991));
        equal(3, outcome.results().size(), "multi-statement result count");
        near(5, outcome.variables().get("A"), 0, "assignment stores variable");
        near(10, outcome.results().get(1), 0, "next statement reads variable");
        near(11, outcome.ans(), 0, "Ans advances between statements");
        equal(2, StatementEngine.splitStatements("log(2,8):1").size(),
                "colon splitter ignores function comma");
    }

    private void scalarParserExtensions() {
        near(2.0, ScalarExpressionEngine.evaluate("5÷R2",
                ScalarExpressionEngine.EvaluationContext.standard()), 0.0,
                "scalar ÷R quotient");
        near(1.25, ScalarExpressionEngine.evaluate("dms(1,15,0)",
                ScalarExpressionEngine.EvaluationContext.standard()), 0.0,
                "scalar DMS input");
    }

    private void exactScalarResults() {
        var context = ScalarExpressionEngine.EvaluationContext.standard();
        equal("7/6", ScalarExpressionEngine.evaluateDetailed("2/3+1/2", context)
                .exactValue().display(), "fraction arithmetic remains exact");
        equal("3√2", ScalarExpressionEngine.evaluateDetailed("3*sqrt(2)", context)
                .exactValue().display(), "surd arithmetic remains exact");
        equal("π/6", ScalarExpressionEngine.evaluateDetailed("pi/6", context)
                .exactValue().display(), "pi multiple remains exact");
        equal("1/2", ScalarExpressionEngine.evaluateDetailed("sin(30)", context)
                .exactValue().display(), "special-angle trig remains exact");
        near(-2.0, ScalarExpressionEngine.evaluate("(-8)^(1/3)", context), 1e-12,
                "negative base rational exponent");
    }

    private void catalogFunctionsReachScalarEngine() {
        var context = ScalarExpressionEngine.EvaluationContext.standard();
        near(1.968503937007874, ScalarExpressionEngine.evaluate("conv1(5)", context),
                1e-14, "cm to inch conversion path");
        near(90.0, ScalarExpressionEngine.evaluate("rad(pi/2)", context), 1e-12,
                "angle suffix overrides DEG setting");
        near(2.0, ScalarExpressionEngine.evaluate("pol(sqrt(2),sqrt(2))", context),
                1e-12, "Pol is accepted by scalar parser");

        var fix3 = new ScalarExpressionEngine.EvaluationContext(new HashMap<>(),
                new HashMap<>(), AngleUnit.DEG, 0.0, null, null, null,
                new Random(991), new ScalarExpressionEngine.RoundingPolicy(
                ScalarExpressionEngine.RoundingKind.FIX, 3));
        near(9.999, ScalarExpressionEngine.evaluate("rnd(10/3)*3", fix3), 1e-12,
                "Rnd follows Fix precision");
    }

    private void manualDomainsAndErrors() {
        expectError(CalculationError.MATH, () -> ScalarExpressionEngine.evaluate("tan(90)",
                ScalarExpressionEngine.EvaluationContext.standard()), "tan singularity");
        expectError(CalculationError.MATH, () -> ScalarExpressionEngine.evaluate("1E100",
                ScalarExpressionEngine.EvaluationContext.standard()), "manual magnitude range");
        expectError(CalculationError.NO_OPERATOR, () -> ScalarExpressionEngine.verify("1+1",
                ScalarExpressionEngine.EvaluationContext.standard()), "verification relation required");

        var f = ScalarExpressionEngine.compile("g(x)");
        var g = ScalarExpressionEngine.compile("f(x)");
        var circular = new ScalarExpressionEngine.EvaluationContext(new HashMap<>(),
                AngleUnit.DEG, 0.0, f, g, new Random(991));
        expectError(CalculationError.CIRCULAR,
                () -> ScalarExpressionEngine.evaluate("f(1)", circular),
                "function cycle classification");
    }

    private void expectError(CalculationError expected, Runnable operation, String message) {
        checks++;
        try {
            operation.run();
            throw new AssertionError(message + ": expected " + expected);
        } catch (CalculationException error) {
            if (error.error() != expected) throw new AssertionError(message
                    + ": expected " + expected + ", actual " + error.error());
        }
    }

    private void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private void equal(Object expected, Object actual, String message) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(message
                + ": expected " + expected + ", actual " + actual);
    }

    private void near(double expected, double actual, double tolerance, String message) {
        checks++;
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(message
                + ": expected " + expected + ", actual " + actual);
    }
}

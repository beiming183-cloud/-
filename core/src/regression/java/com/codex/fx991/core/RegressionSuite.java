package com.codex.fx991.core;

import com.codex.fx991.core.math.BaseNEngine;
import com.codex.fx991.core.math.ComplexExpressionEngine;
import com.codex.fx991.core.math.ComplexValue;
import com.codex.fx991.core.math.CalculationException;
import com.codex.fx991.core.math.DistributionEngine;
import com.codex.fx991.core.math.FunctionTableEngine;
import com.codex.fx991.core.math.MatrixValue;
import com.codex.fx991.core.math.NumericAnalysis;
import com.codex.fx991.core.math.PolynomialEngine;
import com.codex.fx991.core.math.Rational;
import com.codex.fx991.core.math.RatioEngine;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.math.ScientificConstants;
import com.codex.fx991.core.math.SpreadsheetModel;
import com.codex.fx991.core.math.StatisticsEngine;
import com.codex.fx991.core.math.UnitConverter;
import com.codex.fx991.core.math.VectorValue;
import com.codex.fx991.core.mode.CnCwModel;
import com.codex.fx991.core.mode.CalculatorSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free golden tests, runnable offline with :core:regressionTest. */
public final class RegressionSuite {
    private int checks;

    public static void main(String[] args) {
        RegressionSuite suite = new RegressionSuite();
        suite.run();
    }

    private void run() {
        precedenceAndBasicArithmetic();
        degreeTrigAndInverseShift();
        oneShotModifiersAndSemanticDelete();
        twoKeyRolloverPreservesDownOrder();
        ansContinuationAndAcRetention();
        cursorAndUndoBehavior();
        exactDecimalToggle();
        unaryPrecedence();
        modelProfilesStaySeparate();
        setupPiAndErrorSemantics();
        templatesAndPostfixFunctions();
        cnCwScalarManualExamples();
        cnCwScalarTokenAndRangeEdges();
        cnCwLinearAlgebraAndEquationExamples();
        cnCwComplexAndMatrixNumericalEdges();
        cnCwStatisticsAndDistributionExamples();
        cnCwStatisticsGoldenDetails();
        cnCwSpreadsheetAndTableExamples();
        cnCwSpreadsheetFillAndLimitExamples();
        cnCwCatalogAndProfileBoundaries();
        cnCwCatalogOrderAndSettings();
        editingPathPerformanceBudget();
        System.out.println("PASS " + checks + " regression checks");
    }

    private void precedenceAndBasicArithmetic() {
        CalculatorState state = press(CalculatorState.initial(),
                CalculatorKey.DIGIT_2, CalculatorKey.ADD, CalculatorKey.DIGIT_3,
                CalculatorKey.MULTIPLY, CalculatorKey.DIGIT_4, CalculatorKey.EQUALS);
        equal("14", state.result(), "operator precedence");
        equal(ScreenState.RESULT, state.screenState(), "result screen");
    }

    private void degreeTrigAndInverseShift() {
        CalculatorState sin = press(CalculatorState.initial(),
                CalculatorKey.SIN, CalculatorKey.DIGIT_3, CalculatorKey.DIGIT_0,
                CalculatorKey.CLOSE, CalculatorKey.EQUALS);
        near(0.5, sin.ans(), 1e-12, "sin 30 degrees");

        CalculatorState inverse = press(CalculatorState.initial(),
                CalculatorKey.SHIFT, CalculatorKey.SIN, CalculatorKey.DIGIT_0,
                CalculatorKey.DOT, CalculatorKey.DIGIT_5, CalculatorKey.CLOSE,
                CalculatorKey.EQUALS);
        near(30.0, inverse.ans(), 1e-10, "shift sin is inverse sin");
    }

    private void oneShotModifiersAndSemanticDelete() {
        CalculatorState shifted = press(CalculatorState.initial(), CalculatorKey.SHIFT, CalculatorKey.SIN);
        equal(Modifier.NONE, shifted.modifier(), "shift is one-shot");
        equal("sin⁻¹(│", shifted.displayExpression(), "shifted function token");

        CalculatorState deleted = press(shifted, CalculatorKey.DEL);
        equal("0│", deleted.displayExpression(), "DEL removes a whole function token");
    }

    private void twoKeyRolloverPreservesDownOrder() {
        KeyInputRouter router = new KeyInputRouter();
        CalculatorReducer reducer = new CalculatorReducer(new Fx991ExProfile());
        CalculatorState state = CalculatorState.initial();

        check(router.pointerDown(7, CalculatorKey.SHIFT), "first pointer accepted");
        state = reducer.reduce(state, CalculatorKey.SHIFT);
        check(router.pointerDown(11, CalculatorKey.SIN), "overlapping second pointer accepted");
        state = reducer.reduce(state, CalculatorKey.SIN);

        router.pointerUp(11);
        router.pointerUp(7);
        equal("sin⁻¹(│", state.displayExpression(), "commands follow down order, not up order");
        equal(Modifier.NONE, state.modifier(), "overlap consumes SHIFT exactly once");
        equal(0, router.activePointerCount(), "crossed releases clear both pointers");

        check(router.pointerDown(1, CalculatorKey.DIGIT_1), "new gesture accepted");
        check(!router.pointerDown(1, CalculatorKey.DIGIT_2), "same pointer cannot dispatch twice");
        check(!router.pointerDown(2, CalculatorKey.DIGIT_1), "same physical key cannot be double-pressed");
        check(router.pointerDown(2, CalculatorKey.DIGIT_2), "second distinct key accepted");
        check(!router.pointerDown(3, CalculatorKey.DIGIT_3), "third simultaneous key is rejected");
        router.cancelAll();
    }

    private void ansContinuationAndAcRetention() {
        CalculatorState state = press(CalculatorState.initial(),
                CalculatorKey.DIGIT_3, CalculatorKey.MULTIPLY, CalculatorKey.DIGIT_4,
                CalculatorKey.EQUALS, CalculatorKey.DIVIDE, CalculatorKey.DIGIT_3,
                CalculatorKey.DIGIT_0, CalculatorKey.EQUALS);
        near(0.4, state.ans(), 1e-12, "binary key after result starts with Ans");

        CalculatorState cleared = press(state, CalculatorKey.AC);
        check(cleared.hasAns(), "AC preserves Ans");
        near(0.4, cleared.ans(), 1e-12, "AC preserves Ans value");
        equal("0│", cleared.displayExpression(), "AC clears expression");
    }

    private void cursorAndUndoBehavior() {
        CalculatorState state = press(CalculatorState.initial(),
                CalculatorKey.DIGIT_1, CalculatorKey.DIGIT_2,
                CalculatorKey.LEFT, CalculatorKey.DIGIT_3);
        equal("13│2", state.displayExpression(), "token cursor insertion");

        CalculatorState undone = press(state, CalculatorKey.ALPHA, CalculatorKey.DEL);
        equal("1│2", undone.displayExpression(), "EX ALPHA+DEL undo");
        CalculatorState redone = press(undone, CalculatorKey.ALPHA, CalculatorKey.DEL);
        equal("13│2", redone.displayExpression(), "second undo key toggles redo");
    }

    private void exactDecimalToggle() {
        CalculatorState state = press(CalculatorState.initial(),
                CalculatorKey.DIGIT_1, CalculatorKey.DIVIDE, CalculatorKey.DIGIT_3,
                CalculatorKey.EQUALS, CalculatorKey.S_D);
        equal("1/3", state.result(), "S⇔D fraction approximation");
        CalculatorState decimal = press(state, CalculatorKey.S_D);
        equal("0.333333333333", decimal.result(), "S⇔D returns to decimal");
    }

    private void unaryPrecedence() {
        CalculatorState state = press(CalculatorState.initial(),
                CalculatorKey.NEGATE, CalculatorKey.DIGIT_2,
                CalculatorKey.SQUARE, CalculatorKey.EQUALS);
        near(-4.0, state.ans(), 0.0, "unary minus binds below power");
    }

    private void modelProfilesStaySeparate() {
        ModelProfile ex = new Fx991ExProfile();
        ModelProfile es = new Fx991EsPlusProfile();
        equal("MENU", ex.menuKeyLabel(), "EX menu label");
        equal("MODE", es.menuKeyLabel(), "ES Plus menu label");
        check(ex.supportsUndo() && !es.supportsUndo(), "model-specific undo semantics");
    }

    private void setupPiAndErrorSemantics() {
        CalculatorState setup = press(CalculatorState.initial(), CalculatorKey.SHIFT, CalculatorKey.MODE);
        equal(ScreenState.EDITING, setup.screenState(), "SHIFT+MENU opens setup path, not menu");
        check(setup.status().startsWith("SETUP"), "setup indicator");

        CalculatorState pi = press(CalculatorState.initial(),
                CalculatorKey.SHIFT, CalculatorKey.EXP, CalculatorKey.EQUALS);
        near(Math.PI, pi.ans(), 1e-11, "SHIFT+EXP inserts pi");

        CalculatorState divisionByZero = press(CalculatorState.initial(),
                CalculatorKey.DIGIT_1, CalculatorKey.DIVIDE, CalculatorKey.DIGIT_0,
                CalculatorKey.EQUALS);
        equal("Math ERROR", divisionByZero.result(), "domain errors are distinct from syntax errors");
    }

    private void templatesAndPostfixFunctions() {
        CalculatorState cubeRoot = press(CalculatorState.initial(),
                CalculatorKey.SHIFT, CalculatorKey.POWER,
                CalculatorKey.DIGIT_3,
                CalculatorKey.SHIFT, CalculatorKey.CLOSE,
                CalculatorKey.DIGIT_8, CalculatorKey.CLOSE, CalculatorKey.EQUALS);
        near(2.0, cubeRoot.ans(), 1e-12, "indexed root template serialization");

        CalculatorState factorial = press(CalculatorState.initial(),
                CalculatorKey.DIGIT_5, CalculatorKey.SHIFT,
                CalculatorKey.RECIPROCAL, CalculatorKey.EQUALS);
        near(120.0, factorial.ans(), 0.0, "factorial postfix");

        CalculatorState implicit = press(CalculatorState.initial(),
                CalculatorKey.DIGIT_2, CalculatorKey.SHIFT,
                CalculatorKey.EXP, CalculatorKey.EQUALS);
        near(2.0 * Math.PI, implicit.ans(), 1e-11, "implicit multiplication with pi");
    }

    private void cnCwScalarManualExamples() {
        ScalarExpressionEngine.EvaluationContext degree = scalarContext(AngleUnit.DEG);
        ScalarExpressionEngine.EvaluationContext radian = scalarContext(AngleUnit.RAD);
        near(1.0, ScalarExpressionEngine.evaluate("6/2(1+2)", degree), 1e-12,
                "CW bracket completion precedence");
        near(-4.0, ScalarExpressionEngine.evaluate("-2^2", degree), 0.0,
                "manual unary/power precedence");
        near(0.5, ScalarExpressionEngine.evaluate("sin(30)", degree), 1e-12,
                "CW sin 30 example");
        near(4.0, ScalarExpressionEngine.evaluate("log(2,16)", degree), 1e-12,
                "specified-base logarithm");
        near(40320.0, ScalarExpressionEngine.evaluate("(5+3)!", degree), 0.0,
                "manual factorial example");
        near(210.0, ScalarExpressionEngine.evaluate("10 nCr 4", degree), 0.0,
                "manual combination example");
        near(0.0, ScalarExpressionEngine.evaluate("diff(sin(x),pi/2)", radian), 2e-9,
                "manual derivative example");
        near(1.0, ScalarExpressionEngine.evaluate("integral(ln(x),1,e)", radian), 2e-10,
                "manual Gauss-Kronrod integral example");
        near(20.0, ScalarExpressionEngine.evaluate("sum(x+1,1,5)", degree), 0.0,
                "manual summation example");
        check(ScalarExpressionEngine.verify("1<=1<1+1", degree), "verification chain true");
        check(!ScalarExpressionEngine.verify("pi<3", degree), "verification false");
        equal("13/6",
                new Rational(java.math.BigInteger.valueOf(13),
                        java.math.BigInteger.valueOf(6)).toString(),
                "exact rational");
    }

    private void cnCwScalarTokenAndRangeEdges() {
        ScalarExpressionEngine.EvaluationContext degree = scalarContext(AngleUnit.DEG);
        ScalarExpressionEngine.EvaluationContext grad = scalarContext(AngleUnit.GRAD);
        near(210.0, ScalarExpressionEngine.evaluate("10nCr4", degree), 0.0,
                "combination token does not require spaces");
        near(5040.0, ScalarExpressionEngine.evaluate("10nPr4", degree), 0.0,
                "permutation token does not require spaces");
        near(1.0, ScalarExpressionEngine.evaluate("sin(100)", grad), 1e-12,
                "gradian trigonometry");
        expectThrows(ScalarExpressionEngine.SyntaxException.class,
                () -> ScalarExpressionEngine.verify("4<6!=8", degree),
                "verification rejects inequality/not-equal combinations");
        check(!ScalarExpressionEngine.verify("2+3=5!=2+5=8", degree),
                "verification permits equality/not-equal chains");
        expectThrows(IllegalArgumentException.class,
                () -> ScalarExpressionEngine.evaluate("RanInt(-9999999999,1)", degree),
                "RanInt span must remain below 10^10");
        expectThrows(CalculationException.class,
                () -> ScalarExpressionEngine.evaluate("70!", degree),
                "factorial rejects values above 69");

        Rational bounded = Rational.approximate(Math.PI, 100, 0.0);
        equal("311/99", bounded.toString(), "bounded rational approximation");
        equal("0", Rational.ZERO.mixedString(), "zero mixed fraction formatting");
    }

    private void cnCwLinearAlgebraAndEquationExamples() {
        ComplexValue complex = ComplexExpressionEngine.evaluate("(1+i)^4+(1-i)^2",
                Map.of(), ComplexValue.ZERO, AngleUnit.DEG);
        near(-4.0, complex.real(), 1e-10, "manual complex real part");
        near(-2.0, complex.imaginary(), 1e-10, "manual complex imaginary part");

        MatrixValue a = new MatrixValue(new double[][] {{2, 1}, {1, 1}});
        MatrixValue b = new MatrixValue(new double[][] {{2, -1}, {-1, 2}});
        MatrixValue product = a.multiply(b);
        near(3.0, product.get(0, 0), 0.0, "matrix multiply 00");
        near(0.0, product.get(0, 1), 0.0, "matrix multiply 01");
        near(1.0, a.determinant(), 0.0, "matrix determinant");
        near(1.0, a.inverse().get(0, 0), 1e-12, "matrix inverse");

        VectorValue cross = new VectorValue(1, 2).cross(new VectorValue(3, 4));
        equal(3, cross.dimension(), "2D cross product is displayed in 3D");
        near(-2.0, cross.get(2), 0.0, "manual vector cross example");
        near(11.0, new VectorValue(1, 2).dot(new VectorValue(3, 4)), 0.0,
                "manual vector dot example");

        List<ComplexValue> roots = PolynomialEngine.roots(1, 2, -2);
        near(-1.0 - Math.sqrt(3), roots.get(0).real(), 1e-10, "manual quadratic root 1");
        near(-1.0 + Math.sqrt(3), roots.get(1).real(), 1e-10, "manual quadratic root 2");
        PolynomialEngine.Extremum minimum = PolynomialEngine.extrema(1, 2, -2).get(0);
        near(-1.0, minimum.x(), 1e-12, "quadratic minimum x");
        near(-3.0, minimum.y(), 1e-12, "quadratic minimum y");

        PolynomialEngine.InequalitySolution inequality = PolynomialEngine.solveInequality(
                PolynomialEngine.Relation.GREATER_OR_EQUAL, 1, 2, -3);
        equal(2, inequality.intervals().size(), "manual inequality has two intervals");
        near(-3.0, inequality.intervals().get(0).upper(), 1e-10, "inequality left boundary");
        near(1.0, inequality.intervals().get(1).lower(), 1e-10, "inequality right boundary");

        NumericAnalysis.SolveResult solved = NumericAnalysis.solve(x -> x * x - 16.0,
                1.0, 1e-12, 50);
        check(solved.converged(), "Newton solver converges");
        near(4.0, solved.solution(), 1e-9, "manual SOLVE example");
    }

    private void cnCwComplexAndMatrixNumericalEdges() {
        ComplexValue degreeSin = ComplexExpressionEngine.evaluate("sin(30)",
                Map.of(), ComplexValue.ZERO, AngleUnit.DEG);
        near(0.5, degreeSin.real(), 1e-12, "complex mode honors degree setting");
        near(0.0, degreeSin.imaginary(), 1e-12, "real complex sine remains real");

        ComplexValue imaginaryDegreeSin = ComplexExpressionEngine.evaluate("sin(i)",
                Map.of(), ComplexValue.ZERO, AngleUnit.DEG);
        near(0.0, imaginaryDegreeSin.real(), 1e-12, "sin(i degrees) real part");
        near(Math.sinh(Math.PI / 180.0), imaginaryDegreeSin.imaginary(), 1e-12,
                "complex angle scaling applies to the imaginary component");

        ComplexValue longPower = ComplexExpressionEngine.evaluate("(-1)^3000000000",
                Map.of(), ComplexValue.ZERO, AngleUnit.RAD);
        near(1.0, longPower.real(), 0.0, "large integer complex powers remain exact");
        near(0.0, longPower.imaginary(), 0.0, "large integer power has no phase drift");
        expectThrows(ArithmeticException.class,
                () -> ComplexExpressionEngine.evaluate("i^10000000000",
                        Map.of(), ComplexValue.ZERO, AngleUnit.RAD),
                "complex integer exponent follows the manual range");

        ComplexValue tiny = new ComplexValue(1e-50, -1e-60);
        near(1e-50, tiny.real(), 0.0, "small complex values are not rounded to zero");
        near(-1e-60, tiny.imaginary(), 0.0, "small imaginary values are retained");
        ComplexValue quotient = new ComplexValue(1e300, 1e300)
                .divide(new ComplexValue(1e300, 1e300));
        near(1.0, quotient.real(), 1e-15, "scaled complex division avoids overflow");
        near(0.0, quotient.imaginary(), 1e-15, "scaled complex division imaginary part");

        MatrixValue small = new MatrixValue(new double[][] {{1e-20}});
        near(1e-20, small.determinant(), 0.0, "small nonzero matrix is not singular");
        near(1e20, small.inverse().get(0, 0), 1e5,
                "small nonzero matrix remains invertible");
    }

    private void cnCwStatisticsAndDistributionExamples() {
        double[] x = {1,2,3,4,5,6,7,8,9,10};
        double[] f = {1,2,1,2,2,2,3,4,2,1};
        StatisticsEngine.OneVariableResults one = StatisticsEngine.oneVariable(x, f);
        near(20.0, one.n(), 0.0, "statistics frequency count");
        near(119.0, one.sumX(), 0.0, "statistics weighted sum");
        near(5.95, one.mean(), 1e-12, "statistics weighted mean");

        double[] rx = {1.0,1.2,1.5,1.6,1.9,2.1,2.4,2.5,2.7,3.0};
        double[] ry = {1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,2.0};
        StatisticsEngine.RegressionResult regression = StatisticsEngine.regression(
                StatisticsEngine.RegressionType.LINEAR, rx, ry);
        near(0.480221718316958, regression.a(), 1e-12, "linear regression slope");
        near(0.504358780549254, regression.b(), 1e-12, "linear regression intercept");

        near(0.3125, DistributionEngine.binomialPmf(2, 5, 0.5), 1e-15,
                "binomial probability");
        near(0.5, DistributionEngine.binomialCdf(2, 5, 0.5), 1e-15,
                "binomial cumulative probability");
        near(0.17603266338215, DistributionEngine.normalPdf(36, 35, 2), 2e-14,
                "manual normal density example");
        near(0.5, DistributionEngine.normalCdf(0, 0, 1), 2e-7,
                "standard normal center");
        near(0.0, DistributionEngine.inverseNormalCdf(0.5, 0, 1), 1e-9,
                "inverse normal center");
        near(0.213763017249736, DistributionEngine.poissonPmf(3, 2.5), 2e-14,
                "Poisson probability");
    }

    private void cnCwStatisticsGoldenDetails() {
        double[] x = {1,2,3,4,5,6,7,8,9,10};
        double[] frequency = {1,2,1,2,2,2,3,4,2,1};
        StatisticsEngine.OneVariableResults one = StatisticsEngine.oneVariable(x, frequency);
        near(4.0, one.q1(), 0.0, "manual first quartile");
        near(6.5, one.median(), 0.0, "manual median");
        near(8.0, one.q3(), 0.0, "manual third quartile");
        near(2.605156829, one.sampleStdDev(), 5e-10, "manual sample standard deviation");

        double[] rx = {1.0,1.2,1.5,1.6,1.9,2.1,2.4,2.5,2.7,3.0};
        double[] ry = {1.0,1.1,1.2,1.3,1.4,1.5,1.6,1.7,1.8,2.0};
        StatisticsEngine.RegressionResult linear = StatisticsEngine.regression(
                StatisticsEngine.RegressionType.LINEAR, rx, ry);
        near(0.9952824846, linear.r(), 5e-10, "manual linear correlation");
        near(3.145578231, linear.estimateY(5.5), 5e-10,
                "manual linear estimate at x=5.5");
        StatisticsEngine.RegressionResult quadratic = StatisticsEngine.regression(
                StatisticsEngine.RegressionType.QUADRATIC, rx, ry);
        near(0.0561027415, quadratic.a(), 5e-10, "manual quadratic coefficient a");
        near(0.2576384379, quadratic.b(), 5e-10, "manual quadratic coefficient b");
        near(0.7028598638, quadratic.c(), 5e-10, "manual quadratic coefficient c");
        expectThrows(IllegalArgumentException.class,
                () -> StatisticsEngine.regression(StatisticsEngine.RegressionType.LINEAR,
                        new double[] {1, 2}, new double[] {2, 4}, new double[] {1, -1}),
                "regression rejects negative frequencies");

        near(0.8125, DistributionEngine.binomialCdf(3, 5, 0.5), 1e-15,
                "manual binomial list row x=3");
        near(0.96875, DistributionEngine.binomialCdf(4, 5, 0.5), 1e-15,
                "manual binomial list row x=4");
        near(0.5, DistributionEngine.standardNormalP(0.0), 0.0,
                "standard normal P at zero");
        near(-DistributionEngine.standardNormalQ(1.0),
                DistributionEngine.standardNormalQ(-1.0), 1e-15,
                "standard normal Q is the signed integral from zero");
        expectThrows(IllegalArgumentException.class,
                () -> DistributionEngine.poissonPmf(1, Double.NaN),
                "Poisson lambda must be finite");
        double largePoisson = DistributionEngine.poissonCdf(1000, 1000.0);
        check(largePoisson > 0.50 && largePoisson < 0.52,
                "Poisson CDF remains stable when exp(-lambda) underflows");
        double largeBinomial = DistributionEngine.binomialCdf(5000, 10_000, 0.5);
        check(largeBinomial > 0.50 && largeBinomial < 0.51,
                "binomial CDF remains stable for large trial counts");
    }

    private void cnCwSpreadsheetAndTableExamples() {
        ScalarExpressionEngine.EvaluationContext context = scalarContext(AngleUnit.DEG);
        SpreadsheetModel sheet = new SpreadsheetModel(context);
        sheet.set("A1", "7*5");
        sheet.set("A2", "7*6");
        sheet.set("A3", "A2+7");
        sheet.set("B1", "=A1+7");
        sheet.set("A4", "=Sum(A1:A3)");
        near(35.0, sheet.value("A1"), 0.0, "spreadsheet constant");
        near(49.0, sheet.value("A3"), 0.0, "spreadsheet fixed constant expression");
        near(42.0, sheet.value("B1"), 0.0, "spreadsheet formula");
        near(126.0, sheet.value("A4"), 0.0, "spreadsheet range sum");
        sheet.copy("B1", "C3");
        equal("=B3+7", sheet.input("C3"), "relative reference copy");
        sheet.set("E1", "=$A1+A$1+$A$1");
        sheet.copy("E1", "E2");
        equal("=$A2+A$1+$A$1", sheet.input("E2"), "absolute reference copy");
        boolean circular = false;
        try { sheet.set("D1", "=D1"); } catch (SpreadsheetModel.CellException expected) { circular = true; }
        check(circular, "spreadsheet circular reference error");

        ScalarExpressionEngine.CompiledExpression f = ScalarExpressionEngine.compile("x^2+0.5");
        ScalarExpressionEngine.CompiledExpression g = ScalarExpressionEngine.compile("x^2-0.5");
        List<FunctionTableEngine.Row> rows = FunctionTableEngine.generate(f, g,
                FunctionTableEngine.TableType.F_AND_G, -1, 1, 0.5, context);
        equal(5, rows.size(), "manual function table row count");
        near(1.5, rows.get(0).f(), 0.0, "function table f(-1)");
        near(0.5, rows.get(0).g(), 0.0, "function table g(-1)");
    }

    private void cnCwSpreadsheetFillAndLimitExamples() {
        ScalarExpressionEngine.EvaluationContext context = scalarContext(AngleUnit.DEG);
        SpreadsheetModel sheet = new SpreadsheetModel(context);
        sheet.set("A1", "7*5");
        sheet.set("A2", "7*6");
        sheet.set("A3", "7*7");
        sheet.fillFormula("B1:B3", "2A1-3");
        equal("=2A2-3", sheet.input("B2"), "formula fill adjusts relative rows");
        near(81.0, sheet.value("B2"), 0.0, "manual formula fill value");
        sheet.fillValue("C1:C3", "B1*3");
        near(201.0, sheet.value("C1"), 0.0, "manual value fill first row");
        near(285.0, sheet.value("C3"), 0.0, "manual value fill last row");

        String beforeCut = sheet.input("A1");
        sheet.cut("A1", "A1");
        equal(beforeCut, sheet.input("A1"), "cut and paste to the same cell is a no-op");

        sheet.set("A10", "10000000000");
        sheet.set("D1", "=Sum(A10:A10)");
        near(1e10, sheet.value("D1"), 0.0,
                "range results in scientific notation remain numeric");
        sheet.set("D2", "=1E10");
        sheet.copy("D2", "E2");
        equal("=1E10", sheet.input("E2"), "scientific exponent is not a cell reference");
        near(1e10, sheet.value("E2"), 0.0, "copied scientific literal value");

        SpreadsheetModel limited = new SpreadsheetModel(context);
        limited.setAutoCalculate(false);
        String maximumFormula = "=" + "1".repeat(48);
        expectThrows(SpreadsheetModel.MemoryException.class,
                () -> limited.fillFormula("A1:E45", maximumFormula),
                "formula fill obeys the 1700-byte memory limit");
        equal(0, limited.usedBytes(), "failed formula fill is atomic");
        expectThrows(SpreadsheetModel.MemoryException.class,
                () -> limited.set("A1", "=" + "1".repeat(49)),
                "cell input obeys the 49-byte limit");

        ScalarExpressionEngine.CompiledExpression f = ScalarExpressionEngine.compile("x");
        List<FunctionTableEngine.Row> maximumRows = FunctionTableEngine.generate(f, null,
                FunctionTableEngine.TableType.F_ONLY, 0, 44, 1, context);
        equal(45, maximumRows.size(), "single-function table allows 45 rows");
        expectThrows(FunctionTableEngine.RangeException.class,
                () -> FunctionTableEngine.generate(f, null, FunctionTableEngine.TableType.F_ONLY,
                        0, 45, 1, context),
                "single-function table rejects 46 rows");
        expectThrows(FunctionTableEngine.RangeException.class,
                () -> FunctionTableEngine.generate(f, null, FunctionTableEngine.TableType.F_ONLY,
                        Double.NaN, 1, 1, context),
                "function table bounds must be finite");
    }

    private void cnCwCatalogAndProfileBoundaries() {
        equal(10, CnCwModel.FX_991_CN_CW.applications().size(), "991CN CW has ten applications");
        equal(12, CnCwModel.FX_999_CN_CW.applications().size(), "999CN CW has twelve applications");
        check(!CnCwModel.FX_991_CN_CW.supportsDistribution(), "991 hides Distribution");
        check(CnCwModel.FX_999_CN_CW.supportsSpreadsheet(), "999 exposes Spreadsheet");
        near(12.7, UnitConverter.convert("in→cm", 5), 0.0, "manual 5 inch conversion");
        near(4.5, RatioEngine.solveAtoBEqualsXtoD(3, 8, 12), 0.0,
                "manual ratio A:B=X:D example");
        near(5.0, RatioEngine.solveAtoBEqualsXtoD(1, 2, 10), 0.0,
                "manual ratio calculation example");
        near(16.0, RatioEngine.solveAtoBEqualsCtoX(3, 8, 6), 0.0,
                "ratio A:B=C:X form");
        expectThrows(ArithmeticException.class,
                () -> RatioEngine.solveAtoBEqualsXtoD(1, 0, 2),
                "ratio coefficients cannot be zero");
        equal(32, BaseNEngine.add(BaseNEngine.parse("1F", BaseNEngine.Base.HEXADECIMAL), 1),
                "manual hexadecimal addition");
        equal("FFFFFFFF", BaseNEngine.format(-1, BaseNEngine.Base.HEXADECIMAL),
                "32-bit negative hexadecimal format");
        equal(-1, BaseNEngine.parse("FFFFFFFF", BaseNEngine.Base.HEXADECIMAL),
                "hexadecimal input uses signed 32-bit interpretation");
        equal(1, BaseNEngine.parse("-FFFFFFFF", BaseNEngine.Base.HEXADECIMAL),
                "Neg uses the signed 32-bit hexadecimal value");
        expectThrows(ArithmeticException.class,
                () -> BaseNEngine.parse("-80000000", BaseNEngine.Base.HEXADECIMAL),
                "Neg rejects signed 32-bit overflow");
    }

    private void cnCwCatalogOrderAndSettings() {
        List<String> categories = List.copyOf(ScientificConstants.categories().keySet());
        equal(List.of("通用常数", "电磁常数", "原子与核常数", "物理与化学常数", "采用值", "其他"),
                categories, "scientific constant category order");
        int constantCount = ScientificConstants.categories().values().stream()
                .mapToInt(List::size).sum();
        equal(47, constantCount, "manual scientific constant count");
        List<String> conversions = List.copyOf(UnitConverter.catalog().keySet());
        equal(40, conversions.size(), "manual unit conversion command count");
        equal("in→cm", conversions.get(0), "unit conversion catalog first command");
        equal("°C→°F", conversions.get(conversions.size() - 1),
                "unit conversion catalog last command");

        CalculatorSettings settings = new CalculatorSettings();
        equal(CalculatorSettings.InputOutput.MATH_MATH, settings.inputOutput(),
                "default input/output setting");
        equal(AngleUnit.DEG, settings.angleUnit(), "default angle unit");
        equal(CalculatorSettings.DisplayKind.NORM_1, settings.displayKind(),
                "default number format");
        settings.setDisplay(CalculatorSettings.DisplayKind.FIX, 9);
        equal(9, settings.displayDigits(), "Fix allows nine decimal places");
        expectThrows(IllegalArgumentException.class,
                () -> settings.setDisplay(CalculatorSettings.DisplayKind.FIX, 10),
                "Fix rejects ten decimal places");
        settings.setDisplay(CalculatorSettings.DisplayKind.SCI, 10);
        equal(10, settings.displayDigits(), "Sci allows ten significant digits");
        expectThrows(IllegalArgumentException.class,
                () -> settings.setDisplay(CalculatorSettings.DisplayKind.SCI, 0),
                "Sci rejects zero significant digits");
        settings.resetCalculationSettings();
        equal(CalculatorSettings.DisplayKind.NORM_1, settings.displayKind(),
                "calculation settings reset restores Norm 1");
    }

    private ScalarExpressionEngine.EvaluationContext scalarContext(AngleUnit unit) {
        Map<String, Double> variables = new HashMap<>();
        for (String name : List.of("A","B","C","D","E","F","x","y","z")) variables.put(name, 0.0);
        return new ScalarExpressionEngine.EvaluationContext(variables, unit, 0.0, null, null,
                new Random(991L));
    }

    private void editingPathPerformanceBudget() {
        CalculatorReducer reducer = new CalculatorReducer(new Fx991ExProfile());
        CalculatorState state = CalculatorState.initial();
        long started = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            state = reducer.reduce(state, CalculatorKey.DIGIT_1);
            state = reducer.reduce(state, CalculatorKey.DEL);
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        check(elapsedMs < 750, "20,000 editing transitions stay below 750 ms; was " + elapsedMs + " ms");
    }

    private CalculatorState press(CalculatorState initial, CalculatorKey... keys) {
        CalculatorReducer reducer = new CalculatorReducer(new Fx991ExProfile());
        CalculatorState state = initial;
        for (CalculatorKey key : keys) state = reducer.reduce(state, key);
        return state;
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

    private void expectThrows(Class<? extends Throwable> type, Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError(message + ": expected " + type.getSimpleName()
                    + ", actual " + error.getClass().getSimpleName(), error);
        }
        throw new AssertionError(message + ": expected " + type.getSimpleName());
    }
}

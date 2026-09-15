package com.codex.fx991.core.cw;

import com.codex.fx991.core.mode.CnCwModel;

/**
 * Small dependency-free golden checks for the physical CW key semantics.
 * Run with the regression source set or directly after compiling core.
 */
public final class CnCwSemanticSuite {
    private int checks;

    public static void main(String[] args) {
        CnCwSemanticSuite suite = new CnCwSemanticSuite();
        suite.run();
        System.out.println("PASS " + suite.checks + " CN CW semantic checks");
    }

    private void run() {
        keyVocabularySeparatesExecuteAndRelation();
        homeViewportIsStableFor991();
        shiftSevenIsPiAndOneShot();
        shiftDivideIsDerivative();
        shiftedMixedFractionEvaluates();
        calculateScientificKeysEvaluate();
        calculatePostfixAndCombinatoricKeysEvaluate();
        calculateInverseAndNthRootKeysEvaluate();
        calculateAnalysisKeysEvaluate();
        calculateAnsChainsAndHistory();
        calculateDivisionErrorIsVisible();
        shiftFormatInsertsAns();
        relationDoesNotExecute();
        navigationConsumesShiftAndClosesOverlay();
        pageRockerMovesByViewport();
        resetRequiresConfirmation();
        displayFormatUsesSubmenu();
        randomIntegerUsesScalarSpelling();
    }

    private void keyVocabularySeparatesExecuteAndRelation() {
        check(CnCwKey.EXE.isExecuteKey(), "EXE is execute");
        check(CnCwKey.OK.isExecuteKey() && CnCwKey.ENTER.isExecuteKey(),
                "OK and ENTER are execute aliases");
        check(!CnCwKey.EQUALS.isExecuteKey() && CnCwKey.EQUALS.isExpressionEquals(),
                "EQUALS remains expression relation");
    }

    private void homeViewportIsStableFor991() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        CnCwUiState initial = machine.state();
        equal(10, initial.homeItems().size(), "991 full application list");
        equal(6, initial.homePageSize(), "six-tile viewport");
        equal(2, initial.homePageCount(), "two home pages");
        equal(6, initial.homeVisibleItems().size(), "first viewport size");
        for (int i = 0; i < 6; i++) machine.dispatch(CnCwKey.RIGHT);
        equal(1, machine.state().homePageIndex(), "second page index");
        equal(4, machine.state().homeVisibleItems().size(), "last viewport size");
        equal(6, machine.state().homeViewportStart(), "last viewport start");
    }

    private void shiftSevenIsPiAndOneShot() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.DIGIT_7);
        equal("pi", machine.state().expression(), "SHIFT+7 serializes pi");
        check(!machine.state().shiftArmed(), "SHIFT consumed by 7");
        machine.dispatch(CnCwKey.EXE);
        near(Math.PI, machine.state().ans(), 1e-12, "SHIFT+7 evaluates pi");
    }

    private void shiftDivideIsDerivative() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_4);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.DIVIDE);
        equal("4diff(", machine.state().expression(),
                "SHIFT+divide key inserts derivative command");
        check(machine.state().displayText().contains("d/dx"),
                "derivative keeps natural display legend");
    }

    private void relationDoesNotExecute() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.ADD);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.EQUALS);
        check(!machine.state().resultShown(), "EQUALS does not execute");
        check(machine.state().expression().endsWith("="), "EQUALS is retained in input");
    }

    private void shiftFormatInsertsAns() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.ADD);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.EXE);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.FORMAT);
        equal("Ans", machine.state().expression(), "SHIFT+Format inserts Ans");
        check(!machine.state().shiftArmed(), "SHIFT consumed by Format/Ans");
    }

    private void shiftedMixedFractionEvaluates() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.FRACTION);
        machine.dispatch(CnCwKey.DIGIT_1);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_1);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        near(1.5, machine.state().ans(), 1e-12,
                "SHIFT+fraction evaluates mixed number");
    }

    private void calculateScientificKeysEvaluate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.POWER);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.EXE);
        near(8.0, machine.state().ans(), 1e-12, "power key evaluates");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.SQRT);
        machine.dispatch(CnCwKey.DIGIT_9);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        near(3.0, machine.state().ans(), 1e-12, "square-root key evaluates");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.SIN);
        machine.dispatch(CnCwKey.DIGIT_9);
        machine.dispatch(CnCwKey.DIGIT_0);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        near(1.0, machine.state().ans(), 1e-12, "degree sine key evaluates");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.LOG);
        machine.dispatch(CnCwKey.DIGIT_1);
        machine.dispatch(CnCwKey.DIGIT_0);
        machine.dispatch(CnCwKey.DIGIT_0);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        near(2.0, machine.state().ans(), 1e-12, "log key evaluates");
    }

    private void calculatePostfixAndCombinatoricKeysEvaluate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.FACTORIAL);
        machine.dispatch(CnCwKey.EXE);
        near(120.0, machine.state().ans(), 1e-12, "factorial key evaluates");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.NPR);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.EXE);
        near(20.0, machine.state().ans(), 1e-12, "permutation key evaluates");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.NCR);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.EXE);
        near(10.0, machine.state().ans(), 1e-12, "combination key evaluates");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.PERCENT);
        machine.dispatch(CnCwKey.EXE);
        near(0.25, machine.state().ans(), 1e-12, "percent key evaluates");
    }

    private void calculateInverseAndNthRootKeysEvaluate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.SIN);
        machine.dispatch(CnCwKey.DIGIT_1);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        near(90.0, machine.state().ans(), 1e-12, "inverse sine key evaluates in degrees");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.ROOT);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.DIGIT_7);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        near(3.0, machine.state().ans(), 1e-12, "nth-root key evaluates");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.DIGIT_4);
        machine.dispatch(CnCwKey.RECIPROCAL);
        machine.dispatch(CnCwKey.EXE);
        near(0.25, machine.state().ans(), 1e-12, "reciprocal key evaluates");
    }

    private void calculateAnalysisKeysEvaluate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.MULTIPLY);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.DIGIT_0);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_0);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_1);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        near(0.5, machine.state().ans(), 1e-8, "integral key evaluates");

        machine.dispatch(CnCwKey.AC);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.DIVIDE);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.DIGIT_0);
        machine.dispatch(CnCwKey.POWER);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        near(6.0, machine.state().ans(), 1e-6, "derivative key evaluates");
    }

    private void calculateAnsChainsAndHistory() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.ADD);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.EXE);
        machine.dispatch(CnCwKey.ADD);
        machine.dispatch(CnCwKey.DIGIT_4);
        machine.dispatch(CnCwKey.EXE);
        near(9.0, machine.state().ans(), 1e-12, "binary key chains from Ans");
        machine.dispatch(CnCwKey.UP);
        check(machine.state().displayText().contains("4"),
                "UP recalls the latest calculation");
    }

    private void calculateDivisionErrorIsVisible() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_1);
        machine.dispatch(CnCwKey.DIVIDE);
        machine.dispatch(CnCwKey.DIGIT_0);
        machine.dispatch(CnCwKey.EXE);
        equal("Math ERROR", machine.state().result(),
                "division by zero reports a visible calculation error");
    }

    private void navigationConsumesShiftAndClosesOverlay() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.SETTINGS);
        check(!machine.state().shiftArmed(), "menu key consumes SHIFT");
        machine.dispatch(CnCwKey.CATALOG);
        equal(CnCwScreen.CATALOG, machine.state().screen(), "menu family switches");
        machine.dispatch(CnCwKey.BACK);
        equal(CnCwScreen.CALCULATE, machine.state().screen(),
                "BACK does not reveal stale settings overlay");
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.RIGHT);
        check(!machine.state().shiftArmed(), "direction key consumes SHIFT");
    }

    private void pageRockerMovesByViewport() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.CATALOG);
        machine.dispatch(CnCwKey.PAGE_DOWN);
        equal(6, machine.state().selectedIndex(),
                "side page rocker advances a full menu viewport");
        machine.dispatch(CnCwKey.UP);
        equal(5, machine.state().selectedIndex(),
                "central UP still advances one row");
    }

    private void randomIntegerUsesScalarSpelling() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.CATALOG);
        // CATALOG -> Probability, then select RanInt#( (last row).
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
        for (int i = 0; i < 4; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
        machine.dispatch(CnCwKey.DIGIT_1);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.CLOSE_PAREN);
        machine.dispatch(CnCwKey.EXE);
        check(!"Syntax ERROR".equals(machine.state().result()),
                "RanInt token is accepted by scalar engine");
    }

    private void resetRequiresConfirmation() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.SETTINGS);
        equal(CnCwScreen.SETTINGS, machine.state().screen(),
                "HOME settings opens settings root");
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwScreen.RESET_CONFIRM, machine.state().screen(),
                "reset opens confirmation page");
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwScreen.SETTINGS, machine.state().screen(),
                "cancel returns to settings root");
    }

    private void displayFormatUsesSubmenu() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.SETTINGS);
        machine.dispatch(CnCwKey.OK); // calculation settings
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.DOWN); // display format row
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwScreen.SETTINGS_FORMAT, machine.state().screen(),
                "display format opens a single-select submenu");
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Fix digits submenu
        equal(CnCwScreen.SETTINGS_FIX_DIGITS, machine.state().screen(),
                "Fix opens its 0-9 digit selector");
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.EXE); // Fix 3
        equal(CnCwScreen.SETTINGS_FORMAT, machine.state().screen(),
                "digit selection returns to display-format list");
        equal(3, machine.state().settings().displayDigits(), "Fix stores selected digits");
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

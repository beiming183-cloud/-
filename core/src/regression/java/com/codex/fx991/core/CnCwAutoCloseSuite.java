package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.mode.CnCwModel;

/** Regression checks for EXE auto-closing only missing right parentheses. */
public final class CnCwAutoCloseSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwAutoCloseSuite().run();
    }

    private void run() {
        balancedParenthesesStillEvaluateNormally();
        unfinishedFunctionGetsClosingParenthesis();
        multipleMissingRightParenthesesAreClosed();
        extraRightParenthesisStillFails();
        incompleteOperatorStillFails();
        System.out.println("PASS " + checks + " CN CW auto-close checks");
    }

    private void balancedParenthesesStillEvaluateNormally() {
        CnCwMachine machine = calculateMachine();
        press(machine,
                CnCwKey.OPEN_PAREN,
                CnCwKey.DIGIT_5,
                CnCwKey.ADD,
                CnCwKey.DIGIT_6,
                CnCwKey.CLOSE_PAREN,
                CnCwKey.MULTIPLY,
                CnCwKey.DIGIT_3,
                CnCwKey.EXE);
        near(33.0, machine.state().ans(), 0.0,
                "balanced (5+6)*3 keeps normal meaning");
    }

    private void unfinishedFunctionGetsClosingParenthesis() {
        CnCwMachine machine = calculateMachine();
        press(machine,
                CnCwKey.SIN,
                CnCwKey.DIGIT_3,
                CnCwKey.DIGIT_0,
                CnCwKey.EXE);
        near(0.5, machine.state().ans(), 1e-12,
                "EXE closes missing right parenthesis in sin(30");
    }

    private void multipleMissingRightParenthesesAreClosed() {
        CnCwMachine machine = calculateMachine();
        press(machine,
                CnCwKey.OPEN_PAREN,
                CnCwKey.OPEN_PAREN,
                CnCwKey.DIGIT_2,
                CnCwKey.ADD,
                CnCwKey.DIGIT_3,
                CnCwKey.EXE);
        near(5.0, machine.state().ans(), 0.0,
                "EXE closes multiple trailing missing right parentheses");
    }

    private void extraRightParenthesisStillFails() {
        CnCwMachine machine = calculateMachine();
        press(machine,
                CnCwKey.DIGIT_5,
                CnCwKey.ADD,
                CnCwKey.DIGIT_6,
                CnCwKey.CLOSE_PAREN,
                CnCwKey.MULTIPLY,
                CnCwKey.DIGIT_3,
                CnCwKey.EXE);
        equal("Syntax ERROR", machine.state().result(),
                "extra right parenthesis is not auto-repaired");
    }

    private void incompleteOperatorStillFails() {
        CnCwMachine machine = calculateMachine();
        press(machine,
                CnCwKey.DIGIT_5,
                CnCwKey.ADD,
                CnCwKey.EXE);
        equal("Syntax ERROR", machine.state().result(),
                "missing operand is not auto-repaired");
    }

    private static CnCwMachine calculateMachine() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        return machine;
    }

    private static void press(CnCwMachine machine, CnCwKey... keys) {
        for (CnCwKey key : keys) machine.dispatch(key);
    }

    private void near(double expected, double actual, double tolerance, String message) {
        checks++;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private void equal(String expected, String actual, String message) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}

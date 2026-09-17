package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwCommand;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.mode.ApplicationMode;
import com.codex.fx991.core.mode.CnCwModel;

import java.util.List;

/** Functionality-first guards for user-visible commands that used to be partial. */
public final class CnCwFunctionalitySuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwFunctionalitySuite().run();
    }

    private void run() {
        statisticsMenuExposesAllRegressionFamilies();
        allRegressionFamiliesReachTheirEngines();
        quadraticRegressionRunsThroughMachineWorkflow();
        System.out.println("PASS " + checks + " functionality checks");
    }

    private void statisticsMenuExposesAllRegressionFamilies() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        List<CnCwCommand> commands = machine.state().modeCommands();
        equal(9, commands.size(), "statistics command count");
        String[] expected = {"reg-linear", "reg-quadratic", "reg-logarithmic",
                "reg-e-exponential", "reg-ab-exponential", "reg-power", "reg-inverse"};
        for (int index = 0; index < expected.length; index++) {
            equal(expected[index], commands.get(index + 2).id(),
                    "statistics regression command " + index);
            CnCwWorkflowSpec.WorkflowSpec spec = CnCwWorkflowSpec.forCommand(
                    ApplicationMode.STATISTICS, expected[index]);
            check(spec != null, expected[index] + " owns a structured x/y workflow");
            equal(2, spec.minColumns(), expected[index] + " x/y columns");
        }
        equal(3, CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS,
                "reg-quadratic").minRows(), "quadratic starts with enough rows to solve");
    }

    private void allRegressionFamiliesReachTheirEngines() {
        String[] commands = {"reg-linear", "reg-quadratic", "reg-logarithmic",
                "reg-e-exponential", "reg-ab-exponential", "reg-power", "reg-inverse"};
        String[] titles = {"线性回归", "二次回归", "对数回归", "e 指数回归",
                "ab^x 回归", "幂回归", "逆数回归"};
        String data = "1,2,2,4,3,8,4,16";
        for (int index = 0; index < commands.length; index++) {
            CnCwModeEngine.ModeResult result = CnCwModeEngine.evaluate(
                    ApplicationMode.STATISTICS, commands[index], data,
                    ScalarExpressionEngine.EvaluationContext.standard());
            equal(CnCwModeEngine.ResultLayout.KEY_VALUE, result.layout(),
                    commands[index] + " structured result");
            equal(titles[index], result.title(), commands[index] + " result title");
            equal(3, result.items().size(), commands[index] + " coefficient item count");
            check(!result.display().isBlank(), commands[index] + " visible result");
        }
        equal("线性回归", CnCwModeEngine.evaluate(ApplicationMode.STATISTICS,
                "regression", data, ScalarExpressionEngine.EvaluationContext.standard()).title(),
                "legacy regression alias");
    }

    private void quadraticRegressionRunsThroughMachineWorkflow() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        for (int i = 0; i < 3; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK);
        check(machine.state().hasWorkflowInput(), "quadratic regression opens x/y editor");
        equal(3, machine.state().workflowInput().rows(),
                "quadratic regression begins with three data rows");

        enter(machine, "1"); machine.dispatch(CnCwKey.OK);
        enter(machine, "3"); machine.dispatch(CnCwKey.OK);
        enter(machine, "2"); machine.dispatch(CnCwKey.OK);
        enter(machine, "7"); machine.dispatch(CnCwKey.OK);
        enter(machine, "3"); machine.dispatch(CnCwKey.OK);
        enter(machine, "13"); machine.dispatch(CnCwKey.EXE);

        check(machine.state().resultShown(), "quadratic regression reaches result");
        equal("二次回归", machine.state().applicationResult().title(),
                "quadratic regression result type survives Machine path");
        near(1.0, parse(machine, 0), 1e-10, "quadratic a coefficient");
        near(1.0, parse(machine, 1), 1e-10, "quadratic b coefficient");
        near(1.0, parse(machine, 2), 1e-10, "quadratic c coefficient");
    }

    private static double parse(CnCwMachine machine, int index) {
        return Double.parseDouble(machine.state().applicationResult().items().get(index).value()
                .replace('−', '-'));
    }

    private static void enter(CnCwMachine machine, String digits) {
        for (int i = 0; i < digits.length(); i++) {
            machine.dispatch(switch (digits.charAt(i)) {
                case '0' -> CnCwKey.DIGIT_0;
                case '1' -> CnCwKey.DIGIT_1;
                case '2' -> CnCwKey.DIGIT_2;
                case '3' -> CnCwKey.DIGIT_3;
                case '4' -> CnCwKey.DIGIT_4;
                case '5' -> CnCwKey.DIGIT_5;
                case '6' -> CnCwKey.DIGIT_6;
                case '7' -> CnCwKey.DIGIT_7;
                case '8' -> CnCwKey.DIGIT_8;
                default -> CnCwKey.DIGIT_9;
            });
        }
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private void equal(Object expected, Object actual, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
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

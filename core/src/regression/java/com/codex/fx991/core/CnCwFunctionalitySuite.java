package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwCommand;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.cw.CnCwWorkflowAction;
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
        matrixSlotsPersistAndOperate();
        vectorSlotsPersistAndOperate();
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

    private void matrixSlotsPersistAndOperate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 7, 0); // define MatA
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_ROWS);
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS);
        fillGrid(machine, "1", "2", "3", "4");
        check(machine.state().resultShown(), "MatA definition reaches result");
        equal("MatA 已保存", machine.state().applicationResult().title(), "MatA stored title");

        openCommand(machine, 7, 2); // define MatB
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_ROWS);
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS);
        fillGrid(machine, "1", "0", "0", "1");
        equal("MatB 已保存", machine.state().applicationResult().title(), "MatB stored title");

        openCommand(machine, 7, 5); // det(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, machine.state().applicationResult().layout(),
                "stored determinant result layout");
        near(-2.0, parse(machine, 0), 1e-10, "det(MatA)");

        openCommand(machine, 7, 6); // inverse(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.MATRIX, machine.state().applicationResult().layout(),
                "stored inverse returns matrix");
        equal(4, machine.state().applicationResult().cells().size(), "inverse keeps 2x2 shape");

        openCommand(machine, 7, 7); // transpose(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal("3", machine.state().applicationResult().cells().get(1),
                "transpose swaps off-diagonal cell");

        openBinaryChoiceCommand(machine, 7, 8); // MatA + MatB
        equal("2", machine.state().applicationResult().cells().get(0), "matrix add [1,1]");
        equal("5", machine.state().applicationResult().cells().get(3), "matrix add [2,2]");

        openBinaryChoiceCommand(machine, 7, 9); // MatA - MatB
        equal("0", machine.state().applicationResult().cells().get(0), "matrix subtract [1,1]");
        equal("3", machine.state().applicationResult().cells().get(3), "matrix subtract [2,2]");

        openBinaryChoiceCommand(machine, 7, 10); // MatA * MatB
        equal("1", machine.state().applicationResult().cells().get(0), "matrix multiply identity");
        equal("4", machine.state().applicationResult().cells().get(3), "matrix multiply identity last");
    }

    private void vectorSlotsPersistAndOperate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 8, 0); // define VctA
        fillGrid(machine, "3", "4");
        equal("VctA 已保存", machine.state().applicationResult().title(), "VctA stored title");

        openCommand(machine, 8, 2); // define VctB
        fillGrid(machine, "0", "1");
        equal("VctB 已保存", machine.state().applicationResult().title(), "VctB stored title");

        openCommand(machine, 8, 5); // magnitude A
        machine.dispatch(CnCwKey.EXE);
        near(5.0, parse(machine, 0), 1e-10, "|VctA|");

        openCommand(machine, 8, 6); // unit A
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.VECTOR, machine.state().applicationResult().layout(),
                "unit vector layout");

        openBinaryChoiceCommand(machine, 8, 7); // A+B
        equal("3", machine.state().applicationResult().cells().get(0), "vector add x");
        equal("5", machine.state().applicationResult().cells().get(1), "vector add y");

        openBinaryChoiceCommand(machine, 8, 8); // A-B
        equal("3", machine.state().applicationResult().cells().get(0), "vector subtract x");
        equal("3", machine.state().applicationResult().cells().get(1), "vector subtract y");

        openBinaryChoiceCommand(machine, 8, 9); // dot
        near(4.0, parse(machine, 0), 1e-10, "VctA dot VctB");

        openBinaryChoiceCommand(machine, 8, 10); // cross
        equal(3, machine.state().applicationResult().columns(), "2D cross publishes 3D vector");
        equal("3", machine.state().applicationResult().cells().get(2), "2D cross z component");

        openBinaryChoiceCommand(machine, 8, 11); // angle
        check(parse(machine, 0) > 36.0 && parse(machine, 0) < 37.0,
                "VctA/VctB angle is about 36.87 degrees");
    }

    private static void openCommand(CnCwMachine machine, int homeIndex, int commandIndex) {
        machine.dispatch(CnCwKey.HOME);
        for (int i = 0; i < homeIndex; i++) machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        for (int i = 0; i < commandIndex; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK);
    }

    private static void openBinaryChoiceCommand(CnCwMachine machine,
                                                 int homeIndex, int commandIndex) {
        openCommand(machine, homeIndex, commandIndex);
        machine.dispatch(CnCwKey.OK); // first slot -> second slot
        machine.dispatch(CnCwKey.RIGHT); // A -> B
        machine.dispatch(CnCwKey.EXE);
    }

    private static void fillGrid(CnCwMachine machine, String... values) {
        for (int index = 0; index < values.length; index++) {
            enter(machine, values[index]);
            machine.dispatch(index + 1 == values.length ? CnCwKey.EXE : CnCwKey.OK);
        }
    }

    private static double parse(CnCwMachine machine, int index) {
        return Double.parseDouble(machine.state().applicationResult().items().get(index).value()
                .replace('−', '-').replace("°", ""));
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

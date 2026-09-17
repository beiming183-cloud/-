package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.mode.CnCwModel;

/**
 * Stage 6 end-to-end acceptance guards for the remaining structured workflows.
 *
 * <p>These checks intentionally drive the public machine/key APIs instead of
 * calling the math engines directly. They cover the behaviors that otherwise
 * require repetitive manual acceptance: inequality choice cells, validation
 * focus, result-to-input round trips, ratio structured input and the legacy
 * comma bridge.</p>
 */
public final class CnCwStage6AcceptanceSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwStage6AcceptanceSuite().run();
    }

    private void run() {
        inequalityCommandsExposeStructuredChoice();
        inequalityChoiceRejectsTextAndResets();
        inequalityValidationLocatesBlankAndInvalidCells();
        inequalityResultReturnsToPreservedInput();
        ratioStructuredWorkflowsRoundTrip();
        ratioLegacyCommaBridgeStillWorks();
        dualFunctionWorkflowKeepsInputOnBack();
        System.out.println("PASS " + checks + " Stage 6 acceptance checks");
    }

    private void inequalityCommandsExposeStructuredChoice() {
        for (int command = 0; command < 3; command++) {
            CnCwMachine machine = homeApplication(4); // Inequality.
            for (int i = 0; i < command; i++) machine.dispatch(CnCwKey.DOWN);
            machine.dispatch(CnCwKey.OK);

            check(machine.state().hasWorkflowInput(),
                    "inequality command opens structured workflow");
            equal(4 + command, machine.state().workflowInput().columns(),
                    "quadratic/cubic/quartic field count");
            check(machine.state().workflowInput().isChoiceCell(0, 0),
                    "inequality relation is exposed as a choice cell");
            equal(">", machine.state().workflowInput().displayCell(0, 0),
                    "inequality relation defaults to greater-than");
        }
    }

    private void inequalityChoiceRejectsTextAndResets() {
        CnCwMachine machine = homeApplication(4);
        machine.dispatch(CnCwKey.OK); // quadratic

        machine.dispatch(CnCwKey.DIGIT_2);
        equal("1", machine.state().workflowInput().cell(0, 0),
                "digit cannot overwrite relation choice code");
        check(machine.state().status().contains("方向键选择"),
                "choice cell tells user to use directions");

        machine.dispatch(CnCwKey.RIGHT);
        equal("<", machine.state().workflowInput().displayCell(0, 0),
                "RIGHT cycles to less-than");
        machine.dispatch(CnCwKey.RIGHT);
        equal("≥", machine.state().workflowInput().displayCell(0, 0),
                "RIGHT cycles to greater-or-equal");
        machine.dispatch(CnCwKey.RIGHT);
        equal("≤", machine.state().workflowInput().displayCell(0, 0),
                "RIGHT cycles to less-or-equal");
        machine.dispatch(CnCwKey.RIGHT);
        equal(">", machine.state().workflowInput().displayCell(0, 0),
                "choice wraps after fourth relation");

        machine.dispatch(CnCwKey.LEFT);
        equal("≤", machine.state().workflowInput().displayCell(0, 0),
                "LEFT cycles backward");
        machine.dispatch(CnCwKey.DEL);
        equal(">", machine.state().workflowInput().displayCell(0, 0),
                "DEL resets relation to default");
    }

    private void inequalityValidationLocatesBlankAndInvalidCells() {
        CnCwMachine blank = homeApplication(4);
        blank.dispatch(CnCwKey.OK);
        blank.dispatch(CnCwKey.EXE);
        check(!blank.state().resultShown(),
                "blank inequality does not enter result/error display");
        equal(1, blank.state().workflowInput().selectedColumn(),
                "blank inequality focuses first coefficient");
        check(blank.state().status().contains("空白"),
                "blank inequality reports missing input");

        CnCwMachine invalid = homeApplication(4);
        invalid.dispatch(CnCwKey.OK);
        invalid.dispatch(CnCwKey.OK); // relation -> leading coefficient
        press(invalid, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.EXE);
        check(!invalid.state().resultShown(),
                "invalid workflow expression stays on input page");
        equal(1, invalid.state().workflowInput().selectedColumn(),
                "invalid expression keeps focus on bad coefficient");
        check(invalid.state().status().contains("输入格式错误"),
                "invalid coefficient reports format error");
    }

    private void inequalityResultReturnsToPreservedInput() {
        CnCwMachine machine = homeApplication(4);
        machine.dispatch(CnCwKey.OK); // quadratic
        machine.dispatch(CnCwKey.OK); // relation -> a2
        press(machine, CnCwKey.DIGIT_1, CnCwKey.OK,
                CnCwKey.DIGIT_0, CnCwKey.OK,
                CnCwKey.NEGATE, CnCwKey.DIGIT_1, CnCwKey.EXE);

        check(machine.state().resultShown(), "complete inequality reaches result state");
        check(!machine.state().result().isBlank(), "inequality result is non-empty");
        machine.dispatch(CnCwKey.BACK);
        check(machine.state().hasWorkflowInput() && !machine.state().resultShown(),
                "BACK returns from inequality result to input workflow");
        equal("1", machine.state().workflowInput().cell(0, 1),
                "leading coefficient survives result inspection");
        equal("0", machine.state().workflowInput().cell(0, 2),
                "middle coefficient survives result inspection");
        equal("-1", machine.state().workflowInput().cell(0, 3),
                "constant coefficient survives result inspection");
    }

    private void ratioStructuredWorkflowsRoundTrip() {
        CnCwMachine first = homeApplication(9);
        first.dispatch(CnCwKey.OK); // A:B=X:D
        check(first.state().hasWorkflowInput(), "first ratio opens structured workflow");
        equal("A", first.state().workflowInput().spec().fields().get(0).label(),
                "first ratio A label");
        equal("B", first.state().workflowInput().spec().fields().get(1).label(),
                "first ratio B label");
        equal("D", first.state().workflowInput().spec().fields().get(2).label(),
                "first ratio D label");
        press(first, CnCwKey.DIGIT_2, CnCwKey.OK,
                CnCwKey.DIGIT_4, CnCwKey.OK,
                CnCwKey.DIGIT_1, CnCwKey.DIGIT_0, CnCwKey.EXE);
        equal("X=5", first.state().result(), "A:B=X:D structured result");
        check(first.state().hasStructuredApplicationResult(),
                "first ratio publishes structured application result");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE,
                first.state().applicationResult().layout(),
                "ratio structured result uses KEY_VALUE layout");
        first.dispatch(CnCwKey.BACK);
        equal("10", first.state().workflowInput().cell(0, 2),
                "ratio fields survive BACK from result");

        CnCwMachine second = homeApplication(9);
        second.dispatch(CnCwKey.DOWN);
        second.dispatch(CnCwKey.OK); // A:B=C:X
        equal("C", second.state().workflowInput().spec().fields().get(2).label(),
                "second ratio C label");
        press(second, CnCwKey.DIGIT_2, CnCwKey.OK,
                CnCwKey.DIGIT_4, CnCwKey.OK,
                CnCwKey.DIGIT_3, CnCwKey.EXE);
        equal("X=6", second.state().result(), "A:B=C:X structured result");
    }

    private void ratioLegacyCommaBridgeStillWorks() {
        CnCwMachine machine = homeApplication(9);
        machine.dispatch(CnCwKey.OK); // structured A:B=X:D
        check(machine.state().hasWorkflowInput(), "ratio starts structured before comma bridge");

        press(machine, CnCwKey.DIGIT_2, CnCwKey.COMMA);
        check(!machine.state().hasWorkflowInput(),
                "comma in first ratio field drops to legacy editor");
        press(machine, CnCwKey.DIGIT_4, CnCwKey.COMMA,
                CnCwKey.DIGIT_1, CnCwKey.DIGIT_0, CnCwKey.EXE);
        equal("X=5", machine.state().result(),
                "legacy comma ratio path still evaluates");
    }

    private void dualFunctionWorkflowKeepsInputOnBack() {
        CnCwMachine machine = homeApplication(2); // Function table.
        machine.dispatch(CnCwKey.OK); // f(x), g(x)
        check(machine.state().hasWorkflowInput(),
                "dual function command opens structured workflow");
        equal(5, machine.state().workflowInput().columns(),
                "dual function workflow has five fields");
        press(machine, CnCwKey.VAR_X, CnCwKey.OK,
                CnCwKey.VAR_X, CnCwKey.ADD, CnCwKey.DIGIT_1, CnCwKey.DIGIT_0, CnCwKey.OK,
                CnCwKey.DIGIT_1, CnCwKey.OK,
                CnCwKey.DIGIT_6, CnCwKey.OK,
                CnCwKey.DIGIT_1, CnCwKey.EXE);
        check(machine.state().resultShown(), "dual function reaches table result");
        equal(CnCwModeEngine.ResultLayout.TABLE,
                machine.state().applicationResult().layout(),
                "dual function uses TABLE result layout");
        equal(3, machine.state().applicationResult().columns(),
                "dual function result has x/f/g columns");
        machine.dispatch(CnCwKey.BACK);
        check(machine.state().hasWorkflowInput() && !machine.state().resultShown(),
                "BACK returns to dual function input");
        equal("x+10", machine.state().workflowInput().cell(0, 1),
                "dual function g(x) survives result inspection");
    }

    private CnCwMachine homeApplication(int index) {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        for (int i = 0; i < index; i++) machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        return machine;
    }

    private static void press(CnCwMachine machine, CnCwKey... keys) {
        for (CnCwKey key : keys) machine.dispatch(key);
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
}

package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwCursorPath;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.mode.CnCwModel;

/** Stage 3 bootstrap checks for semantic cursor positions. */
public final class CnCwCursorPathSuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwCursorPathSuite().run();
    }

    private void run() {
        rootBoundaryKeepsLegacyCompatibility();
        nestedPathCarriesStructureWithoutLosingFallback();
        uiStatePublishesSemanticCursorFacade();
        fractionPublishesNestedSlotsAndMovesVertically();
        System.out.println("PASS " + checks + " semantic cursor checks");
    }

    private void rootBoundaryKeepsLegacyCompatibility() {
        CnCwCursorPath path = CnCwCursorPath.rootBoundary(3);
        check(path.isRootBoundary(), "root token boundary is marked as compatibility path");
        equal(CnCwCursorPath.Slot.ROW, path.slot(), "root boundary uses ROW slot");
        equal(3, path.offset(), "root boundary offset follows token boundary");
        equal(3, path.legacyTokenBoundary(), "legacy token boundary is preserved");
        equal(0, path.childPath().size(), "root boundary has no nested child path");
    }

    private void nestedPathCarriesStructureWithoutLosingFallback() {
        CnCwCursorPath first = CnCwCursorPath.nested(
                Compat.list(2, 1), CnCwCursorPath.Slot.FRACTION_DENOMINATOR, 4, 7);
        CnCwCursorPath same = CnCwCursorPath.nested(
                Compat.list(2, 1), CnCwCursorPath.Slot.FRACTION_DENOMINATOR, 4, 7);
        check(!first.isRootBoundary(), "nested semantic position is distinct from token boundary");
        equal(Compat.list(2, 1), first.childPath(), "nested child path is retained");
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR, first.slot(),
                "nested slot records fraction denominator");
        equal(4, first.offset(), "nested slot keeps local insertion offset");
        equal(7, first.legacyTokenBoundary(), "nested slot keeps migration fallback boundary");
        equal(first, same, "semantic cursor path has value equality");
        equal(first.hashCode(), same.hashCode(), "equal semantic paths share hash code");
    }

    private void uiStatePublishesSemanticCursorFacade() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_1);
        machine.dispatch(CnCwKey.DIGIT_2);
        equal(2, machine.state().cursor(), "legacy cursor reaches expression end");
        equal(CnCwCursorPath.rootBoundary(2), machine.state().semanticCursor(),
                "state exposes current token cursor through semantic facade");

        machine.moveCursorTo(1);
        equal(1, machine.state().cursor(), "legacy touch cursor still moves normally");
        equal(CnCwCursorPath.rootBoundary(1), machine.state().semanticCursor(),
                "semantic facade follows existing cursor during migration");
    }

    private void fractionPublishesNestedSlotsAndMovesVertically() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.FRACTION);
        machine.dispatch(CnCwKey.DIGIT_6);

        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "fraction input ends inside denominator slot");
        equal(1, machine.state().semanticCursor().childPath().get(0),
                "fraction semantic path identifies template token");
        equal(1, machine.state().semanticCursor().offset(),
                "denominator cursor keeps local offset");

        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "UP moves denominator cursor into numerator");
        equal(1, machine.state().semanticCursor().offset(),
                "UP preserves nearest local offset");
        check(machine.state().naturalExpression().containsCursor(),
                "numerator semantic position remains visible in natural tree");

        machine.dispatch(CnCwKey.DOWN);
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "DOWN returns numerator cursor to denominator");
        equal(1, machine.state().semanticCursor().offset(),
                "DOWN restores denominator local offset");
        check(machine.state().naturalExpression().containsCursor(),
                "denominator semantic position remains visible in natural tree");

        machine.moveCursorTo(2);
        equal(0, machine.state().semanticCursor().offset(),
                "denominator start has local offset zero");
        machine.dispatch(CnCwKey.UP);
        equal(0, machine.state().semanticCursor().offset(),
                "vertical move also preserves start-of-slot position");
        check(machine.state().naturalExpression().containsCursor(),
                "fraction numerator start keeps cursor inside natural fraction tree");
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

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
        fractionHorizontalEntryExitIsSemantic();
        fractionDeleteNeverBreaksTemplate();
        fractionSelectionReplacementKeepsStructure();
        powerPublishesBaseExponentAndMovesSemantically();
        powerDeleteNeverBreaksTemplate();
        powerSelectionReplacementKeepsStructure();
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

    private void fractionHorizontalEntryExitIsSemantic() {
        CnCwMachine machine = fractionMachine();

        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at denominator end exits to root row");
        equal(3, machine.state().cursor(),
                "fraction exit keeps the same legacy boundary");
        check(machine.state().naturalExpression().containsCursor(),
                "root-after fraction still renders a cursor");

        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "LEFT from root-after re-enters denominator");
        equal(1, machine.state().semanticCursor().offset(),
                "re-entry lands at denominator end");

        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves inside denominator before crossing slots");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "LEFT from denominator start enters numerator end");
        equal(1, machine.state().semanticCursor().offset(),
                "numerator end offset is retained");
        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to numerator start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "LEFT from numerator start exits to root-before");
        equal(0, machine.state().cursor(),
                "root-before fraction shares the numerator-start legacy boundary");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "RIGHT from root-before enters numerator");
        equal(0, machine.state().semanticCursor().offset(),
                "root-before entry starts at numerator offset zero");
    }

    private void fractionDeleteNeverBreaksTemplate() {
        CnCwMachine machine = fractionMachine();
        machine.dispatch(CnCwKey.DEL);
        equal("5/", machine.state().expression(),
                "DEL removes denominator content without deleting fraction template");
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "empty denominator remains a semantic slot");
        equal(0, machine.state().semanticCursor().offset(),
                "empty denominator cursor is at offset zero");
        check(machine.state().naturalExpression().containsCursor(),
                "empty denominator remains renderable");

        machine.dispatch(CnCwKey.DEL);
        equal("5/", machine.state().expression(),
                "DEL at denominator start navigates instead of deleting separator");
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "denominator-start DEL moves to numerator end");

        machine.dispatch(CnCwKey.DEL);
        equal("/", machine.state().expression(),
                "DEL can empty numerator while preserving fraction structure");
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "empty numerator remains a semantic slot");
        check(machine.state().naturalExpression().containsCursor(),
                "empty numerator fraction still renders cursor");

        machine.dispatch(CnCwKey.DEL);
        equal("/", machine.state().expression(),
                "DEL at numerator start exits instead of deleting template");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "numerator-start DEL exits to root row");

        machine = fractionMachine();
        machine.dispatch(CnCwKey.RIGHT); // explicit root-after
        machine.dispatch(CnCwKey.DEL);
        equal("", machine.state().expression(),
                "DEL from root-after removes the entire fraction atomically");
        equal(CnCwCursorPath.rootBoundary(0), machine.state().semanticCursor(),
                "whole-fraction delete returns to root boundary");
    }

    private void fractionSelectionReplacementKeepsStructure() {
        CnCwMachine machine = fractionMachine();
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("6", machine.selectedExpression(),
                "SHIFT+LEFT selects denominator content first");
        machine.dispatch(CnCwKey.DIGIT_9);
        equal("5/9", machine.state().expression(),
                "replacing denominator preserves fraction separator");
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "replacement cursor stays in denominator");

        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("9", machine.selectedExpression(),
                "denominator remains independently selectable after replacement");
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("5/9", machine.selectedExpression(),
                "second semantic extension selects the complete fraction");
        machine.dispatch(CnCwKey.DIGIT_7);
        equal("7", machine.state().expression(),
                "replacing whole fraction removes template and inserts one root token");
        equal(CnCwCursorPath.rootBoundary(1), machine.state().semanticCursor(),
                "whole-fraction replacement returns to root cursor semantics");
    }

    private void powerPublishesBaseExponentAndMovesSemantically() {
        CnCwMachine machine = powerMachine();
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "power input ends inside exponent slot");
        equal(1, machine.state().semanticCursor().childPath().get(0),
                "power semantic path identifies ^ template token");
        equal(1, machine.state().semanticCursor().offset(),
                "exponent cursor keeps local offset");
        check(machine.state().naturalExpression().containsCursor(),
                "power exponent cursor remains visible in natural tree");

        machine.dispatch(CnCwKey.DOWN);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "DOWN moves exponent cursor into base");
        equal(1, machine.state().semanticCursor().offset(),
                "DOWN preserves nearest base offset");
        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "UP returns base cursor to exponent");

        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at exponent end exits power to root row");
        equal(3, machine.state().cursor(),
                "power root-after shares exponent-end legacy boundary");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "LEFT from root-after re-enters exponent end");
        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to exponent start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "LEFT from exponent start enters base end");
        equal(1, machine.state().semanticCursor().offset(),
                "base end keeps local offset");
        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to base start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "LEFT from base start exits to root-before");
        equal(0, machine.state().cursor(),
                "power root-before shares base-start legacy boundary");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "RIGHT from root-before enters power base");
    }

    private void powerDeleteNeverBreaksTemplate() {
        CnCwMachine machine = powerMachine();
        machine.dispatch(CnCwKey.DEL);
        equal("2^", machine.state().expression(),
                "DEL removes exponent content without deleting ^ template");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "empty exponent remains a semantic slot");
        equal(0, machine.state().semanticCursor().offset(),
                "empty exponent cursor stays at offset zero");
        check(machine.state().naturalExpression().containsCursor(),
                "empty exponent remains renderable as superscript slot");

        machine.dispatch(CnCwKey.DEL);
        equal("2^", machine.state().expression(),
                "DEL at exponent start navigates instead of deleting ^");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "exponent-start DEL returns to base end");

        machine.dispatch(CnCwKey.DEL);
        equal("^", machine.state().expression(),
                "DEL can empty power base while preserving ^ structure");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "empty power base remains semantic");
        check(machine.state().naturalExpression().containsCursor(),
                "empty power base remains visible in natural tree");

        machine.dispatch(CnCwKey.DEL);
        equal("^", machine.state().expression(),
                "DEL at base start exits instead of deleting structural ^");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "base-start DEL exits to root row");

        machine = powerMachine();
        machine.dispatch(CnCwKey.RIGHT); // explicit root-after
        machine.dispatch(CnCwKey.DEL);
        equal("", machine.state().expression(),
                "DEL from root-after removes complete power atomically");
        equal(CnCwCursorPath.rootBoundary(0), machine.state().semanticCursor(),
                "whole-power delete returns to root boundary");
    }

    private void powerSelectionReplacementKeepsStructure() {
        CnCwMachine machine = powerMachine();
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("2^3", machine.selectedExpression(),
                "keyboard semantic selection keeps Stage 2 whole-power behavior");
        machine.dispatch(CnCwKey.DIGIT_7);
        equal("7", machine.state().expression(),
                "keyboard replacement still replaces the complete power atomically");
        equal(CnCwCursorPath.rootBoundary(1), machine.state().semanticCursor(),
                "whole-power keyboard replacement returns to root semantics");

        machine = powerMachine();
        machine.beginTouchSelection(2);
        machine.extendTouchSelection(3);
        equal("3", machine.selectedExpression(),
                "touch fine selection can select exponent independently");
        equal(1, machine.pasteExpression("9"),
                "touch-selected exponent accepts semantic replacement");
        equal("2^9", machine.state().expression(),
                "touch exponent replacement preserves ^ template");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "exponent replacement cursor remains in exponent");

        machine = powerMachine();
        machine.beginTouchSelection(0);
        machine.extendTouchSelection(1);
        equal("2", machine.selectedExpression(),
                "touch fine selection can select power base independently");
        equal(1, machine.pasteExpression("4"),
                "base replacement accepts one semantic token");
        equal("4^3", machine.state().expression(),
                "replacing base preserves exponent and ^ template");
    }

    private CnCwMachine powerMachine() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.POWER);
        machine.dispatch(CnCwKey.DIGIT_3);
        return machine;
    }

    private CnCwMachine fractionMachine() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.FRACTION);
        machine.dispatch(CnCwKey.DIGIT_6);
        return machine;
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

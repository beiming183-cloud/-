package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwCursorPath;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwSemanticSpan;
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
        radicalPublishesContentAndMovesSemantically();
        nthRootPublishesIndexContentAndMovesSemantically();
        radicalDeleteNeverBreaksStructure();
        radicalSelectionReplacementKeepsStructure();
        radicalEvaluationStillWorks();
        singleFunctionPublishesArgumentAndMovesSemantically();
        multiFunctionArgumentsMoveWithoutTouchingCommas();
        functionDeleteNeverBreaksStructure();
        functionSelectionReplacementKeepsStructure();
        functionEvaluationStillWorks();
        semanticSelectionPathsCoverEditableSlots();
        crossArgumentSelectionSnapsToWholeFunction();
        semanticSelectionDeleteAndPasteStayInFunctionArgument();
        semanticTouchSpansAndPathsRoundTrip();
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

    private void radicalPublishesContentAndMovesSemantically() {
        CnCwMachine machine = squareRootMachine(false);
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "sqrt input ends inside RADICAL_CONTENT");
        equal(0, machine.state().semanticCursor().childPath().get(0),
                "sqrt semantic path identifies template token");
        equal(1, machine.state().semanticCursor().offset(),
                "sqrt content keeps local offset");
        check(machine.state().naturalExpression().containsCursor(),
                "sqrt content cursor stays visible in natural tree");

        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at sqrt content end exits to root row");
        equal(2, machine.state().cursor(),
                "unclosed sqrt root-after shares content-end legacy boundary");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "LEFT from root-after re-enters sqrt content");
        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to sqrt content start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "LEFT from sqrt content start exits to root-before");
        equal(0, machine.state().cursor(),
                "sqrt root-before is the template boundary");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "RIGHT from root-before re-enters sqrt content start");

        machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.SQRT);
        machine.dispatch(CnCwKey.DIGIT_8);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "fixed cube root publishes ROOT_CONTENT");
        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "fixed cube-root UP is consumed without history recall");
    }

    private void nthRootPublishesIndexContentAndMovesSemantically() {
        CnCwMachine machine = nthRootMachine(false);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "n-th root input ends in ROOT_CONTENT");
        equal(1, machine.state().semanticCursor().offset(),
                "root content keeps local offset");

        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticCursor().slot(),
                "UP moves root content into root index");
        equal(1, machine.state().semanticCursor().offset(),
                "UP preserves nearest index offset");
        machine.dispatch(CnCwKey.DOWN);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "DOWN returns root index to content");

        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to root content start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticCursor().slot(),
                "LEFT from content start enters root index end");
        equal(1, machine.state().semanticCursor().offset(),
                "root index end keeps local offset");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "RIGHT from index end crosses structural comma into content");
        equal(0, machine.state().semanticCursor().offset(),
                "root content entry starts at offset zero");
    }

    private void radicalDeleteNeverBreaksStructure() {
        CnCwMachine machine = squareRootMachine(false);
        machine.dispatch(CnCwKey.DEL);
        equal("sqrt(", machine.state().expression(),
                "DEL removes sqrt content without deleting template");
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "empty sqrt content remains semantic");
        check(machine.state().naturalExpression().containsCursor(),
                "empty sqrt content still renders a cursor");
        machine.dispatch(CnCwKey.DEL);
        equal("sqrt(", machine.state().expression(),
                "DEL at empty sqrt start exits instead of deleting template");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "sqrt content-start DEL exits to root row");

        machine = squareRootMachine(false);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.DEL);
        equal("", machine.state().expression(),
                "DEL from sqrt root-after removes the whole radical atomically");

        machine = nthRootMachine(false);
        machine.dispatch(CnCwKey.DEL);
        equal("root(3,", machine.state().expression(),
                "DEL empties root content without deleting comma");
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "empty root content remains semantic");
        machine.dispatch(CnCwKey.DEL);
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticCursor().slot(),
                "DEL at content start moves to root index end");
        machine.dispatch(CnCwKey.DEL);
        equal("root(,", machine.state().expression(),
                "DEL can empty root index while preserving separator");
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticCursor().slot(),
                "empty root index remains semantic");
        machine.dispatch(CnCwKey.DEL);
        equal("root(,", machine.state().expression(),
                "DEL at empty root index exits instead of deleting root template");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "empty root index exits to root row");
    }

    private void radicalSelectionReplacementKeepsStructure() {
        CnCwMachine machine = squareRootMachine(true);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("sqrt(9)", machine.selectedExpression(),
                "keyboard selection keeps closed sqrt as one structure");
        machine.dispatch(CnCwKey.DIGIT_7);
        equal("7", machine.state().expression(),
                "keyboard replacement replaces the complete sqrt atomically");

        machine = squareRootMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(2);
        equal("9", machine.selectedExpression(),
                "touch fine-selection can select sqrt content only");
        equal(1, machine.pasteExpression("4"),
                "sqrt content accepts semantic replacement");
        equal("sqrt(4)", machine.state().expression(),
                "sqrt content replacement preserves template and close");
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "sqrt replacement cursor remains in radical content");

        machine = nthRootMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(2);
        equal("3", machine.selectedExpression(),
                "touch fine-selection can select root index only");
        equal(1, machine.pasteExpression("4"),
                "root index accepts semantic replacement");
        equal("root(4,8)", machine.state().expression(),
                "root index replacement preserves comma and radicand");

        machine = nthRootMachine(true);
        machine.beginTouchSelection(3);
        machine.extendTouchSelection(4);
        equal("8", machine.selectedExpression(),
                "touch fine-selection can select root content only");
        equal(1, machine.pasteExpression("9"),
                "root content accepts semantic replacement");
        equal("root(3,9)", machine.state().expression(),
                "root content replacement preserves index and separator");
    }

    private void radicalEvaluationStillWorks() {
        CnCwMachine machine = squareRootMachine(true);
        machine.dispatch(CnCwKey.EXE);
        equal("3", machine.state().result(),
                "sqrt semantic editor still evaluates normally");

        machine = nthRootMachine(true);
        machine.dispatch(CnCwKey.EXE);
        equal("2", machine.state().result(),
                "root(index, content) semantic editor still evaluates normally");
    }

    private void singleFunctionPublishesArgumentAndMovesSemantically() {
        CnCwMachine machine = sinMachine(true);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "closed sin starts at root-after");

        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "LEFT from root-after enters function argument");
        equal(0, machine.state().semanticCursor().childPath().get(0),
                "function path identifies opening token");
        equal(0, machine.state().semanticCursor().childPath().get(1),
                "single-argument function publishes argument zero");
        equal(2, machine.state().semanticCursor().offset(),
                "function re-entry lands at argument end");
        check(machine.state().naturalExpression().containsCursor(),
                "function argument cursor remains visible in natural tree");

        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at argument end exits function");

        machine.moveCursorTo(0);
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "RIGHT from root-before enters function argument start");
        equal(0, machine.state().semanticCursor().offset(),
                "root-before function entry starts at offset zero");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "LEFT at first argument start exits to root-before");
    }

    private void multiFunctionArgumentsMoveWithoutTouchingCommas() {
        CnCwMachine machine = sumMachine();
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "LEFT from sum root-after enters last argument");
        equal(2, machine.state().semanticCursor().childPath().get(1),
                "sum publishes third argument index");
        equal(1, machine.state().semanticCursor().offset(),
                "last argument entry lands at its end");

        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to third argument start");
        machine.dispatch(CnCwKey.LEFT);
        equal(1, machine.state().semanticCursor().childPath().get(1),
                "LEFT crosses structural comma into second argument");
        equal(1, machine.state().semanticCursor().offset(),
                "previous argument entry lands at its end");
        machine.dispatch(CnCwKey.RIGHT);
        equal(2, machine.state().semanticCursor().childPath().get(1),
                "RIGHT crosses separator directly into next argument");
        equal(0, machine.state().semanticCursor().offset(),
                "RIGHT separator crossing lands at next argument start");
        equal("sum(x,1,3)", machine.state().expression(),
                "argument navigation never edits function commas");
    }

    private void functionDeleteNeverBreaksStructure() {
        CnCwMachine machine = sinMachine(false);
        machine.dispatch(CnCwKey.DEL);
        equal("sin(3", machine.state().expression(),
                "DEL removes function argument content only");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "function cursor stays in argument after DEL");
        machine.dispatch(CnCwKey.DEL);
        equal("sin(", machine.state().expression(),
                "DEL may empty argument without deleting function token");
        equal(0, machine.state().semanticCursor().offset(),
                "empty function argument remains an editable slot");
        check(machine.state().naturalExpression().containsCursor(),
                "empty function argument still renders a cursor");
        machine.dispatch(CnCwKey.DEL);
        equal("", machine.state().expression(),
                "DEL on a bare empty function preserves Stage 2 whole-token deletion");
        equal(CnCwCursorPath.rootBoundary(0), machine.state().semanticCursor(),
                "bare function deletion returns to root boundary");

        machine = sumMachine();
        machine.moveCursorTo(3); // second argument start, just after first comma
        equal(1, machine.state().semanticCursor().childPath().get(1),
                "touch cursor identifies second sum argument");
        machine.dispatch(CnCwKey.DEL);
        equal("sum(x,1,3)", machine.state().expression(),
                "DEL at argument start never deletes structural comma");
        equal(0, machine.state().semanticCursor().childPath().get(1),
                "argument-start DEL navigates to previous argument");

        machine = sinMachine(true);
        machine.dispatch(CnCwKey.DEL);
        equal("", machine.state().expression(),
                "DEL from closed function root-after removes whole function atomically");
        equal(CnCwCursorPath.rootBoundary(0), machine.state().semanticCursor(),
                "whole-function DEL returns to root boundary");
    }

    private void functionSelectionReplacementKeepsStructure() {
        CnCwMachine machine = sinMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(3);
        equal("30", machine.selectedExpression(),
                "Step 6 touch selection can keep one function argument fine-grained");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticSelectionAnchor().slot(),
                "function selection anchor publishes argument semantics");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticSelectionFocus().slot(),
                "function selection focus publishes argument semantics");
        equal(2, machine.pasteExpression("45"),
                "function argument accepts direct selection replacement");
        equal("sin(45)", machine.state().expression(),
                "function argument replacement preserves function structure");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "replacement cursor remains in function argument");

        machine = sinMachine(false);
        machine.dispatch(CnCwKey.DEL);
        machine.dispatch(CnCwKey.DEL);
        equal("sin(", machine.state().expression(),
                "semantic DEL can empty a single function argument");
        equal(2, machine.pasteExpression("45"),
                "empty function argument accepts semantic paste");
        equal("sin(45", machine.state().expression(),
                "single argument replacement preserves the function template");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "replacement cursor remains in function argument");

        machine = sumMachine();
        machine.moveCursorTo(4); // end of second argument
        machine.dispatch(CnCwKey.DEL);
        equal("sum(x,,3)", machine.state().expression(),
                "DEL can empty a middle argument while preserving both commas");
        equal(1, machine.state().semanticCursor().childPath().get(1),
                "empty middle argument keeps its semantic argument index");
        equal(1, machine.pasteExpression("2"),
                "empty middle argument accepts replacement");
        equal("sum(x,2,3)", machine.state().expression(),
                "middle argument replacement preserves function structure");
    }

    private void functionEvaluationStillWorks() {
        CnCwMachine machine = sinMachine(true);
        machine.dispatch(CnCwKey.EXE);
        equal("1/2", machine.state().result(),
                "sin function semantic editor preserves evaluation");

        machine = sumMachine();
        machine.dispatch(CnCwKey.EXE);
        equal("6", machine.state().result(),
                "multi-argument sum still evaluates after semantic editing support");
    }

    private void semanticTouchSpansAndPathsRoundTrip() {
        CnCwMachine machine = fractionMachine();
        CnCwSemanticSpan denominator = findSpan(machine,
                CnCwCursorPath.Slot.FRACTION_DENOMINATOR, 0);
        check(denominator != null, "fraction denominator publishes semantic touch span");
        equal(1, denominator.length(), "fraction touch span keeps slot token length");
        machine.moveCursorTo(denominator.position(0));
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "semantic touch move enters denominator directly");
        equal(0, machine.state().semanticCursor().offset(),
                "semantic touch move keeps denominator-local offset");

        machine = sinMachine(true);
        CnCwSemanticSpan argument = findSpan(machine,
                CnCwCursorPath.Slot.FUNCTION_ARGUMENT, 0);
        check(argument != null, "function argument publishes semantic touch span");
        equal(Compat.list(0, 0), argument.childPath(),
                "function span identifies template and argument index");
        machine.moveCursorTo(argument.position(1));
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "semantic touch cursor enters function argument");
        equal(1, machine.state().semanticCursor().offset(),
                "function semantic touch keeps local offset");

        machine.beginTouchSelection(argument.position(0));
        machine.extendTouchSelection(argument.position(argument.length()));
        equal("30", machine.selectedExpression(),
                "semantic touch paths select only the function argument");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticSelectionAnchor().slot(),
                "semantic path selection publishes argument anchor");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticSelectionFocus().slot(),
                "semantic path selection publishes argument focus");

        machine = nthRootMachine(true);
        CnCwSemanticSpan rootIndex = findSpan(machine, CnCwCursorPath.Slot.ROOT_INDEX, 0);
        CnCwSemanticSpan rootContent = findSpan(machine, CnCwCursorPath.Slot.ROOT_CONTENT, 0);
        check(rootIndex != null && rootContent != null,
                "nth root publishes independent index/content touch spans");
        machine.moveCursorTo(rootIndex.position(1));
        equal(CnCwCursorPath.Slot.ROOT_INDEX, machine.state().semanticCursor().slot(),
                "semantic touch can land in root index");
        machine.moveCursorTo(rootContent.position(1));
        equal(CnCwCursorPath.Slot.ROOT_CONTENT, machine.state().semanticCursor().slot(),
                "semantic touch can land in root content");
    }

    private CnCwSemanticSpan findSpan(CnCwMachine machine, CnCwCursorPath.Slot slot,
                                      int childTail) {
        for (CnCwSemanticSpan span : machine.state().semanticSpans()) {
            if (span.slot() != slot) continue;
            if (slot == CnCwCursorPath.Slot.FUNCTION_ARGUMENT
                    && (span.childPath().size() < 2
                    || span.childPath().get(1) != childTail)) continue;
            return span;
        }
        return null;
    }

    private CnCwMachine sinMachine(boolean closed) {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SIN);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.DIGIT_0);
        if (closed) machine.dispatch(CnCwKey.CLOSE_PAREN);
        return machine;
    }

    private CnCwMachine sumMachine() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        equal(7, machine.pasteExpression("sum(x,1,3)"),
                "sum helper imports full multi-argument function");
        return machine;
    }

    private void semanticSelectionPathsCoverEditableSlots() {
        CnCwMachine machine = fractionMachine();
        machine.beginTouchSelection(2);
        machine.extendTouchSelection(3);
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticSelectionAnchor().slot(),
                "fraction fine selection anchor uses denominator slot");
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticSelectionFocus().slot(),
                "fraction fine selection focus uses denominator slot");
        equal(0, machine.state().semanticSelectionAnchor().offset(),
                "fraction selection anchor keeps slot-local offset");
        equal(1, machine.state().semanticSelectionFocus().offset(),
                "fraction selection focus keeps slot-local offset");

        machine = powerMachine();
        machine.beginTouchSelection(2);
        machine.extendTouchSelection(3);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticSelectionAnchor().slot(),
                "power selection publishes exponent anchor");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticSelectionFocus().slot(),
                "power selection publishes exponent focus");

        machine = squareRootMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(2);
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticSelectionAnchor().slot(),
                "sqrt selection publishes radical content anchor");
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticSelectionFocus().slot(),
                "sqrt selection publishes radical content focus");

        machine = nthRootMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(2);
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticSelectionAnchor().slot(),
                "nth-root index selection publishes root-index anchor");
        machine.beginTouchSelection(3);
        machine.extendTouchSelection(4);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticSelectionAnchor().slot(),
                "nth-root content selection publishes root-content anchor");

        machine = sinMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(2);
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticSelectionAnchor().slot(),
                "function fine selection publishes argument anchor");
        equal(Compat.list(0, 0), machine.state().semanticSelectionAnchor().childPath(),
                "function selection path identifies template and argument index");
    }

    private void crossArgumentSelectionSnapsToWholeFunction() {
        CnCwMachine machine = sumMachine();
        machine.beginTouchSelection(3);
        machine.extendTouchSelection(4);
        equal("1", machine.selectedExpression(),
                "one sum argument remains independently selectable");
        equal(Compat.list(0, 1), machine.state().semanticSelectionAnchor().childPath(),
                "second argument selection carries argument index one");

        machine.beginTouchSelection(1);
        machine.extendTouchSelection(4);
        equal("sum(x,1,3)", machine.selectedExpression(),
                "selection crossing a structural comma snaps to whole function");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticSelectionAnchor().slot(),
                "whole function selection returns to root semantic boundaries");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticSelectionFocus().slot(),
                "whole function focus is also root semantic boundary");
    }

    private void semanticSelectionDeleteAndPasteStayInFunctionArgument() {
        CnCwMachine machine = sumMachine();
        machine.beginTouchSelection(3);
        machine.extendTouchSelection(4);
        machine.dispatch(CnCwKey.DEL);
        equal("sum(x,,3)", machine.state().expression(),
                "DEL empties only the selected function argument");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "DEL leaves cursor in the emptied argument slot");
        equal(Compat.list(0, 1), machine.state().semanticCursor().childPath(),
                "emptied second argument keeps its semantic path");

        machine = sumMachine();
        machine.beginTouchSelection(3);
        machine.extendTouchSelection(4);
        equal(3, machine.pasteExpression("2+4"),
                "paste replaces one selected argument atomically");
        equal("sum(x,2+4,3)", machine.state().expression(),
                "paste replacement preserves surrounding function arguments");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "paste replacement remains in the same function argument");
        equal(Compat.list(0, 1), machine.state().semanticCursor().childPath(),
                "paste replacement keeps second-argument semantic identity");
    }

    private CnCwMachine squareRootMachine(boolean closed) {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SQRT);
        machine.dispatch(CnCwKey.DIGIT_9);
        if (closed) machine.dispatch(CnCwKey.CLOSE_PAREN);
        return machine;
    }

    private CnCwMachine nthRootMachine(boolean closed) {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.ROOT);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_8);
        if (closed) machine.dispatch(CnCwKey.CLOSE_PAREN);
        return machine;
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

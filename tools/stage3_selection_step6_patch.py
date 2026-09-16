from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
state_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwUiState.java')
cursor_test_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
machine_test_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java')

machine = machine_path.read_text(encoding='utf-8')
state = state_path.read_text(encoding='utf-8')
cursor_tests = cursor_test_path.read_text(encoding='utf-8')
machine_tests = machine_test_path.read_text(encoding='utf-8')


def replace_once(source, old, new, label):
    if old not in source:
        raise SystemExit(f'{label} anchor not found')
    return source.replace(old, new, 1)

# Step 6: touch selection may remain fine-grained while it stays inside one
# semantic editable slot. Crossing a function argument boundary still snaps to
# the whole nested function through enclosingOpen().
machine = replace_once(
    machine,
    '''                } else {\n                    RadicalBounds radical = radicalContainingToken(index);\n                    if (radical == null || !selectionWithinRadicalSlot(start, end, radical)) {\n                        int enclosing = enclosingOpen(index);\n                        if (enclosing >= 0) {\n                            int close = matchingClose(enclosing);\n                            unitStart = enclosing;\n                            unitEnd = close >= 0 ? close + 1 : unitEnd;\n                        }\n                    }\n                }''',
    '''                } else if (!selectionWithinSemanticEditableSlot(start, end, index)) {\n                    int enclosing = enclosingOpen(index);\n                    if (enclosing >= 0) {\n                        int close = matchingClose(enclosing);\n                        unitStart = enclosing;\n                        unitEnd = close >= 0 ? close + 1 : unitEnd;\n                    }\n                }''',
    'unified touch selection normalization')

# Publish semantic selection anchor/focus alongside legacy token ranges.
machine = replace_once(
    machine,
    '''                naturalExpression(), cursor, semanticCursorPath(),\n                selectionStartIndex(), selectionEndIndex(),\n                result, ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,''',
    '''                naturalExpression(), cursor, semanticCursorPath(),\n                selectionStartIndex(), selectionEndIndex(),\n                semanticSelectionPath(selectionAnchor), semanticSelectionPath(selectionFocus),\n                result, ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,''',
    'publish semantic selection paths')

# Add unified selection-scope helpers after the function-argument helper.
anchor = '''    private boolean selectionWithinFunctionArgument(int start, int end, FunctionBounds function) {\n        for (FunctionArgumentBounds argument : function.arguments) {\n            if (start >= argument.start && end <= argument.end) return true;\n        }\n        return false;\n    }\n'''
if anchor not in machine:
    raise SystemExit('function selection helper anchor not found')

helpers = r'''

    /**
     * True when a touch range remains inside the smallest nested editable slot
     * owning tokenIndex. This keeps fine selection inside radical/function
     * content while still snapping across structural commas or parentheses.
     */
    private boolean selectionWithinSemanticEditableSlot(int start, int end, int tokenIndex) {
        RadicalBounds radical = radicalContainingToken(tokenIndex);
        if (radical != null && selectionWithinRadicalSlot(start, end, radical)) return true;
        FunctionBounds function = functionContainingToken(tokenIndex);
        return function != null && selectionWithinFunctionArgument(start, end, function);
    }

    /** Smallest semantic slot containing the complete normalized selection. */
    private SemanticSelectionScope semanticSelectionScope(int start, int end) {
        if (start < 0 || end < 0 || start == end) return null;
        int lo = Math.min(start, end);
        int hi = Math.max(start, end);
        SemanticSelectionScope best = null;

        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds fraction = fractionBounds(template);
            if (fraction != null) {
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                        fraction.numeratorStart, fraction.numeratorEnd));
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                        fraction.denominatorStart, fraction.denominatorEnd));
            }

            PowerBounds power = powerBounds(template);
            if (power != null) {
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                        power.baseStart, power.baseEnd));
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template),
                        CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                        power.exponentStart, power.exponentEnd));
            }

            RadicalBounds radical = radicalBounds(template);
            if (radical != null) {
                if (radical.indexStart >= 0) {
                    best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                            com.codex.fx991.core.Compat.list(template),
                            CnCwCursorPath.Slot.ROOT_INDEX,
                            radical.indexStart, radical.indexEnd));
                }
                CnCwCursorPath.Slot contentSlot = isSquareRootTemplate(tokens.get(template))
                        ? CnCwCursorPath.Slot.RADICAL_CONTENT
                        : CnCwCursorPath.Slot.ROOT_CONTENT;
                best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                        com.codex.fx991.core.Compat.list(template), contentSlot,
                        radical.contentStart, radical.contentEnd));
            }

            FunctionBounds function = functionBounds(template);
            if (function != null) {
                for (int argumentIndex = 0; argumentIndex < function.arguments.size(); argumentIndex++) {
                    FunctionArgumentBounds argument = function.arguments.get(argumentIndex);
                    best = preferSelectionScope(best, containedSelectionScope(lo, hi,
                            com.codex.fx991.core.Compat.list(template, argumentIndex),
                            CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                            argument.start, argument.end));
                }
            }
        }
        return best;
    }

    private SemanticSelectionScope containedSelectionScope(int start, int end,
                                                            List<Integer> childPath,
                                                            CnCwCursorPath.Slot slot,
                                                            int slotStart, int slotEnd) {
        if (slotStart < 0 || slotEnd < slotStart || start < slotStart || end > slotEnd) {
            return null;
        }
        return new SemanticSelectionScope(childPath, slot, slotStart, slotEnd);
    }

    private SemanticSelectionScope preferSelectionScope(SemanticSelectionScope current,
                                                         SemanticSelectionScope candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        int currentSpan = current.slotEnd - current.slotStart;
        int candidateSpan = candidate.slotEnd - candidate.slotStart;
        if (candidateSpan < currentSpan) return candidate;
        if (candidateSpan == currentSpan
                && candidate.childPath.size() > current.childPath.size()) return candidate;
        return current;
    }

    /** Semantic facade for legacy selection anchor/focus boundaries. */
    private CnCwCursorPath semanticSelectionPath(int boundary) {
        if (!selectionActive()) return semanticCursorPath();
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        SemanticSelectionScope scope = semanticSelectionScope(selectionStart(), selectionEnd());
        if (scope == null || safe < scope.slotStart || safe > scope.slotEnd) {
            return CnCwCursorPath.rootBoundary(safe);
        }
        return CnCwCursorPath.nested(scope.childPath, scope.slot,
                safe - scope.slotStart, safe);
    }
'''
machine = machine.replace(anchor, anchor + helpers, 1)

# Add a record describing the semantic slot that owns a normalized selection.
machine = replace_once(
    machine,
    '''    private record FunctionCursor(int templateIndex, int argumentIndex, int offset) { }\n    private record SelectionRange(int start, int end) { }''',
    '''    private record FunctionCursor(int templateIndex, int argumentIndex, int offset) { }\n    private record SemanticSelectionScope(List<Integer> childPath, CnCwCursorPath.Slot slot,\n                                          int slotStart, int slotEnd) { }\n    private record SelectionRange(int start, int end) { }''',
    'semantic selection scope record')

# UiState now exposes semantic selection anchor/focus while keeping token indexes.
state = replace_once(
    state,
    '''    private final int selectionStart;\n    private final int selectionEnd;\n    private final String result;''',
    '''    private final int selectionStart;\n    private final int selectionEnd;\n    /** Stage 3 semantic facade for the selection's directional anchor. */\n    private final CnCwCursorPath semanticSelectionAnchor;\n    /** Stage 3 semantic facade for the selection's active focus. */\n    private final CnCwCursorPath semanticSelectionFocus;\n    private final String result;''',
    'UiState semantic selection fields')
state = replace_once(
    state,
    '''                CnCwCursorPath semanticCursor,\n                int selectionStart,\n                int selectionEnd,\n                String result,''',
    '''                CnCwCursorPath semanticCursor,\n                int selectionStart,\n                int selectionEnd,\n                CnCwCursorPath semanticSelectionAnchor,\n                CnCwCursorPath semanticSelectionFocus,\n                String result,''',
    'UiState semantic selection constructor args')
state = replace_once(
    state,
    '''        this.selectionStart = Math.max(0, selectionStart);\n        this.selectionEnd = Math.max(this.selectionStart, selectionEnd);\n        this.result = result == null ? "" : result;''',
    '''        this.selectionStart = Math.max(0, selectionStart);\n        this.selectionEnd = Math.max(this.selectionStart, selectionEnd);\n        this.semanticSelectionAnchor = Objects.requireNonNull(semanticSelectionAnchor,\n                "semanticSelectionAnchor");\n        this.semanticSelectionFocus = Objects.requireNonNull(semanticSelectionFocus,\n                "semanticSelectionFocus");\n        this.result = result == null ? "" : result;''',
    'UiState semantic selection assignments')
state = replace_once(
    state,
    '''    public boolean hasSelection() { return selectionEnd > selectionStart; }\n    public int selectionStart() { return selectionStart; }\n    public int selectionEnd() { return selectionEnd; }\n    public String result() { return result; }''',
    '''    public boolean hasSelection() { return selectionEnd > selectionStart; }\n    public int selectionStart() { return selectionStart; }\n    public int selectionEnd() { return selectionEnd; }\n    public CnCwCursorPath semanticSelectionAnchor() { return semanticSelectionAnchor; }\n    public CnCwCursorPath semanticSelectionFocus() { return semanticSelectionFocus; }\n    public String result() { return result; }''',
    'UiState semantic selection accessors')

# Step 6 regression coverage.
cursor_tests = replace_once(
    cursor_tests,
    '''        functionSelectionReplacementKeepsStructure();\n        functionEvaluationStillWorks();\n        System.out.println("PASS " + checks + " semantic cursor checks");''',
    '''        functionSelectionReplacementKeepsStructure();\n        functionEvaluationStillWorks();\n        semanticSelectionPathsCoverEditableSlots();\n        crossArgumentSelectionSnapsToWholeFunction();\n        semanticSelectionDeleteAndPasteStayInFunctionArgument();\n        System.out.println("PASS " + checks + " semantic cursor checks");''',
    'register Step 6 tests')

old_function_test = '''    private void functionSelectionReplacementKeepsStructure() {\n        CnCwMachine machine = sinMachine(true);\n        machine.beginTouchSelection(1);\n        machine.extendTouchSelection(3);\n        equal("sin(30)", machine.selectedExpression(),\n                "Stage 2 touch selection still expands partial function drag to whole call");\n\n        // Parameter-level replacement is already possible through the semantic\n        // cursor + DEL path; unified fine-selection is intentionally Step 6.\n        machine = sinMachine(false);'''
new_function_test = '''    private void functionSelectionReplacementKeepsStructure() {\n        CnCwMachine machine = sinMachine(true);\n        machine.beginTouchSelection(1);\n        machine.extendTouchSelection(3);\n        equal("30", machine.selectedExpression(),\n                "Step 6 touch selection can keep one function argument fine-grained");\n        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,\n                machine.state().semanticSelectionAnchor().slot(),\n                "function selection anchor publishes argument semantics");\n        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,\n                machine.state().semanticSelectionFocus().slot(),\n                "function selection focus publishes argument semantics");\n        equal(2, machine.pasteExpression("45"),\n                "function argument accepts direct selection replacement");\n        equal("sin(45)", machine.state().expression(),\n                "function argument replacement preserves function structure");\n        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,\n                machine.state().semanticCursor().slot(),\n                "replacement cursor remains in function argument");\n\n        machine = sinMachine(false);'''
if old_function_test not in cursor_tests:
    raise SystemExit('Step 5 function selection test anchor not found')
cursor_tests = cursor_tests.replace(old_function_test, new_function_test, 1)

insert_anchor = '    private CnCwMachine squareRootMachine(boolean closed) {'
if insert_anchor not in cursor_tests:
    raise SystemExit('Step 6 test insertion anchor not found')
step6_tests = r'''    private void semanticSelectionPathsCoverEditableSlots() {
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

'''
cursor_tests = cursor_tests.replace(insert_anchor, step6_tests + insert_anchor, 1)

# Existing golden touch regression intentionally changes in Step 6: function
# argument content is now a valid fine-grained touch selection.
machine_tests = replace_once(
    machine_tests,
    '''        machine.beginTouchSelection(1);\n        machine.extendTouchSelection(2);\n        equal("sin(2)", machine.selectedExpression(),\n                "touch selection expands a partial function drag to the full call");''',
    '''        machine.beginTouchSelection(1);\n        machine.extendTouchSelection(2);\n        equal("2", machine.selectedExpression(),\n                "touch selection stays fine-grained inside one function argument");''',
    'golden function touch selection contract')

machine_path.write_text(machine, encoding='utf-8')
state_path.write_text(state, encoding='utf-8')
cursor_test_path.write_text(cursor_tests, encoding='utf-8')
machine_test_path.write_text(machine_tests, encoding='utf-8')
print('Step 6 semantic selection patch applied')

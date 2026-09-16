from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
test_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
text = machine_path.read_text(encoding='utf-8')
tests = test_path.read_text(encoding='utf-8')


def replace_once(source, old, new, label):
    if old not in source:
        raise SystemExit(f'{label} anchor not found')
    return source.replace(old, new, 1)

# Route ordinary-function navigation after the more specific fraction/power/root structures.
text = replace_once(
    text,
    '''                    } else if (!moveFractionHorizontal(-1) && !movePowerHorizontal(-1)\n                            && !moveRadicalHorizontal(-1)) {''',
    '''                    } else if (!moveFractionHorizontal(-1) && !movePowerHorizontal(-1)\n                            && !moveRadicalHorizontal(-1) && !moveFunctionHorizontal(-1)) {''',
    'LEFT function routing')
text = replace_once(
    text,
    '''                    } else if (!moveFractionHorizontal(1) && !movePowerHorizontal(1)\n                            && !moveRadicalHorizontal(1)) {''',
    '''                    } else if (!moveFractionHorizontal(1) && !movePowerHorizontal(1)\n                            && !moveRadicalHorizontal(1) && !moveFunctionHorizontal(1)) {''',
    'RIGHT function routing')
text = replace_once(
    text,
    '''        if (deleteFractionSemantic()) return;\n        if (deletePowerSemantic()) return;\n        if (deleteRadicalSemantic()) return;\n        if (cursor <= 0 || tokens.isEmpty()) return;''',
    '''        if (deleteFractionSemantic()) return;\n        if (deletePowerSemantic()) return;\n        if (deleteRadicalSemantic()) return;\n        if (deleteFunctionSemantic()) return;\n        if (cursor <= 0 || tokens.isEmpty()) return;''',
    'function delete routing')

# Fine touch selection may stay inside one function argument. Crossing an argument
# separator still expands to the full function, preserving structural commas.
text = replace_once(
    text,
    '''                } else {\n                    RadicalBounds radical = radicalContainingToken(index);\n                    if (radical == null || !selectionWithinRadicalSlot(start, end, radical)) {\n                        int enclosing = enclosingOpen(index);\n                        if (enclosing >= 0) {\n                            int close = matchingClose(enclosing);\n                            unitStart = enclosing;\n                            unitEnd = close >= 0 ? close + 1 : unitEnd;\n                        }\n                    }\n                }''',
    '''                } else {\n                    RadicalBounds radical = radicalContainingToken(index);\n                    FunctionBounds function = functionContainingToken(index);\n                    boolean withinRadical = radical != null\n                            && selectionWithinRadicalSlot(start, end, radical);\n                    boolean withinFunction = function != null\n                            && selectionWithinFunctionArgument(start, end, function);\n                    if (!withinRadical && !withinFunction) {\n                        int enclosing = enclosingOpen(index);\n                        if (enclosing >= 0) {\n                            int close = matchingClose(enclosing);\n                            unitStart = enclosing;\n                            unitEnd = close >= 0 ? close + 1 : unitEnd;\n                        }\n                    }\n                }''',
    'touch function fine-selection')

# Publish function argument paths only after fraction/power/radical paths have had
# a chance to claim a more specific mathematical structure.
text = replace_once(
    text,
    '''        RadicalCursor radical = radicalCursorAt(cursor);\n        if (radical != null) {\n            return CnCwCursorPath.nested(\n                    com.codex.fx991.core.Compat.list(radical.templateIndex),\n                    radical.slot, radical.offset, cursor);\n        }\n        return CnCwCursorPath.rootBoundary(cursor);''',
    '''        RadicalCursor radical = radicalCursorAt(cursor);\n        if (radical != null) {\n            return CnCwCursorPath.nested(\n                    com.codex.fx991.core.Compat.list(radical.templateIndex),\n                    radical.slot, radical.offset, cursor);\n        }\n        FunctionCursor function = functionCursorAt(cursor);\n        if (function != null) {\n            return CnCwCursorPath.nested(\n                    com.codex.fx991.core.Compat.list(function.templateIndex, function.argumentIndex),\n                    CnCwCursorPath.Slot.FUNCTION_ARGUMENT, function.offset, cursor);\n        }\n        return CnCwCursorPath.rootBoundary(cursor);''',
    'function semantic publication')

# Add ordinary-function semantic helpers after radical semantics and before the
# unrelated spreadsheet snapshot code.
anchor = '    private List<String> spreadsheetCellsSnapshot() {'
if anchor not in text:
    raise SystemExit('function helper insertion anchor not found')

helpers = r'''    /** Ordinary parenthesized function; radicals keep their dedicated Step 4 semantics. */
    private static boolean isFunctionTemplate(Token token) {
        String value = token.evaluation;
        if (!value.endsWith("(") || "(".equals(value) || isRadicalTemplate(token)) return false;
        // These are exponent-entry templates rather than ordinary function calls.
        return !"e^(".equals(value) && !"*10^(".equals(value);
    }

    private FunctionBounds functionBounds(int templateIndex) {
        if (templateIndex < 0 || templateIndex >= tokens.size()
                || !isFunctionTemplate(tokens.get(templateIndex))) return null;
        int close = matchingClose(templateIndex);
        int innerEnd = close >= 0 ? close : tokens.size();
        int endExclusive = close >= 0 ? close + 1 : innerEnd;
        List<FunctionArgumentBounds> arguments = new ArrayList<>();
        int argumentStart = templateIndex + 1;
        int depth = 0;
        for (int index = argumentStart; index < innerEnd; index++) {
            Token token = tokens.get(index);
            if (")".equals(token.evaluation)) {
                if (depth > 0) depth--;
                continue;
            }
            if (depth == 0 && ",".equals(token.evaluation)) {
                arguments.add(new FunctionArgumentBounds(argumentStart, index));
                argumentStart = index + 1;
                continue;
            }
            if (opensParenthesis(token)) depth++;
        }
        // Even an empty function owns one editable argument slot.
        arguments.add(new FunctionArgumentBounds(argumentStart, innerEnd));
        return new FunctionBounds(templateIndex,
                com.codex.fx991.core.Compat.copyList(arguments), close, endExclusive);
    }

    /** Smallest ordinary function owning this insertion boundary. */
    private FunctionCursor functionCursorAt(int boundary) {
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        FunctionCursor best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FunctionBounds bounds = functionBounds(template);
            if (bounds == null) continue;
            for (int argumentIndex = 0; argumentIndex < bounds.arguments.size(); argumentIndex++) {
                FunctionArgumentBounds argument = bounds.arguments.get(argumentIndex);
                if (safe < argument.start || safe > argument.end) continue;
                int span = bounds.endExclusive - bounds.templateIndex;
                if (span < bestSpan) {
                    bestSpan = span;
                    best = new FunctionCursor(template, argumentIndex,
                            safe - argument.start);
                }
            }
        }
        return best;
    }

    private FunctionCursor functionCursorFromPath(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary()
                || path.slot() != CnCwCursorPath.Slot.FUNCTION_ARGUMENT
                || path.childPath().size() < 2) return null;
        int template = path.childPath().get(0);
        int argumentIndex = path.childPath().get(1);
        FunctionBounds bounds = functionBounds(template);
        if (bounds == null || argumentIndex < 0 || argumentIndex >= bounds.arguments.size()) {
            return null;
        }
        FunctionArgumentBounds argument = bounds.arguments.get(argumentIndex);
        int length = argument.end - argument.start;
        return new FunctionCursor(template, argumentIndex,
                Math.max(0, Math.min(length, path.offset())));
    }

    private FunctionBounds functionStartingAtBoundary(int boundary) {
        FunctionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FunctionBounds value = functionBounds(template);
            if (value == null || value.templateIndex != boundary) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private FunctionBounds functionEndingAtBoundary(int boundary) {
        FunctionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FunctionBounds value = functionBounds(template);
            if (value == null || value.endExclusive != boundary) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    /** Smallest function whose concrete argument token contains tokenIndex. */
    private FunctionBounds functionContainingToken(int tokenIndex) {
        FunctionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FunctionBounds value = functionBounds(template);
            if (value == null) continue;
            boolean inArgument = false;
            for (FunctionArgumentBounds argument : value.arguments) {
                if (tokenIndex >= argument.start && tokenIndex < argument.end) {
                    inArgument = true;
                    break;
                }
            }
            if (!inArgument) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private boolean selectionWithinFunctionArgument(int start, int end, FunctionBounds function) {
        for (FunctionArgumentBounds argument : function.arguments) {
            if (start >= argument.start && end <= argument.end) return true;
        }
        return false;
    }

    private void setFunctionCursor(FunctionBounds function, int argumentIndex, int offset) {
        if (function.arguments.isEmpty()) {
            setRootCursor(function.templateIndex);
            return;
        }
        int safeArgument = Math.max(0, Math.min(function.arguments.size() - 1, argumentIndex));
        FunctionArgumentBounds argument = function.arguments.get(safeArgument);
        int length = argument.end - argument.start;
        int local = Math.max(0, Math.min(length, offset));
        cursor = argument.start + local;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(function.templateIndex, safeArgument),
                CnCwCursorPath.Slot.FUNCTION_ARGUMENT, local, cursor);
    }

    /** root-before -> arg0 -> arg1 ... -> root-after, never landing on a separator comma. */
    private boolean moveFunctionHorizontal(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            FunctionBounds target = direction > 0
                    ? functionStartingAtBoundary(cursor) : functionEndingAtBoundary(cursor);
            if (target == null || target.arguments.isEmpty()) return false;
            if (direction > 0) {
                setFunctionCursor(target, 0, 0);
            } else {
                int last = target.arguments.size() - 1;
                FunctionArgumentBounds argument = target.arguments.get(last);
                setFunctionCursor(target, last, argument.end - argument.start);
            }
            finishSemanticCursorMove();
            return true;
        }

        FunctionCursor function = functionCursorFromPath(path);
        if (function == null) return false;
        FunctionBounds bounds = functionBounds(function.templateIndex);
        if (bounds == null || function.argumentIndex >= bounds.arguments.size()) return false;
        FunctionArgumentBounds argument = bounds.arguments.get(function.argumentIndex);
        int length = argument.end - argument.start;
        if (direction < 0) {
            if (function.offset > 0) {
                setFunctionCursor(bounds, function.argumentIndex, function.offset - 1);
            } else if (function.argumentIndex > 0) {
                int previousIndex = function.argumentIndex - 1;
                FunctionArgumentBounds previous = bounds.arguments.get(previousIndex);
                setFunctionCursor(bounds, previousIndex, previous.end - previous.start);
            } else {
                setRootCursor(bounds.templateIndex);
            }
        } else {
            if (function.offset < length) {
                setFunctionCursor(bounds, function.argumentIndex, function.offset + 1);
            } else if (function.argumentIndex + 1 < bounds.arguments.size()) {
                setFunctionCursor(bounds, function.argumentIndex + 1, 0);
            } else {
                setRootCursor(bounds.endExclusive);
            }
        }
        finishSemanticCursorMove();
        return true;
    }

    /**
     * DEL removes only the current function argument content. At an argument
     * boundary it navigates over the structural comma/template instead of
     * deleting it. From root-after a complete function is removed atomically.
     */
    private boolean deleteFunctionSemantic() {
        if (tokens.isEmpty()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            FunctionBounds function = functionEndingAtBoundary(cursor);
            if (function == null) return false;
            rememberUndo();
            tokens.subList(function.templateIndex, function.endExclusive).clear();
            setRootCursor(function.templateIndex);
            finishSemanticEditMutation();
            return true;
        }

        FunctionCursor function = functionCursorFromPath(path);
        if (function == null) return false;
        FunctionBounds bounds = functionBounds(function.templateIndex);
        if (bounds == null || function.argumentIndex >= bounds.arguments.size()) return false;
        FunctionArgumentBounds argument = bounds.arguments.get(function.argumentIndex);
        if (function.offset == 0) {
            if (function.argumentIndex > 0) {
                int previousIndex = function.argumentIndex - 1;
                FunctionArgumentBounds previous = bounds.arguments.get(previousIndex);
                setFunctionCursor(bounds, previousIndex, previous.end - previous.start);
            } else {
                setRootCursor(bounds.templateIndex);
            }
            finishSemanticCursorMove();
            return true;
        }

        int deleteIndex = cursor - 1;
        if (deleteIndex < argument.start || deleteIndex >= argument.end) return false;
        rememberUndo();
        tokens.remove(deleteIndex);
        cursor--;
        FunctionBounds updated = functionBounds(function.templateIndex);
        if (updated == null || function.argumentIndex >= updated.arguments.size()) {
            setRootCursor(Math.min(cursor, tokens.size()));
        } else {
            setFunctionCursor(updated, function.argumentIndex, Math.max(0, function.offset - 1));
        }
        finishSemanticEditMutation();
        return true;
    }

'''
text = text.replace(anchor, helpers + anchor, 1)

# Records for function argument boundaries and cursor positions.
text = replace_once(
    text,
    '''    private record RadicalCursor(int templateIndex, CnCwCursorPath.Slot slot, int offset) { }\n    private record SelectionRange(int start, int end) { }''',
    '''    private record RadicalCursor(int templateIndex, CnCwCursorPath.Slot slot, int offset) { }\n    private record FunctionArgumentBounds(int start, int end) { }\n    private record FunctionBounds(int templateIndex, List<FunctionArgumentBounds> arguments,\n                                  int closeIndex, int endExclusive) { }\n    private record FunctionCursor(int templateIndex, int argumentIndex, int offset) { }\n    private record SelectionRange(int start, int end) { }''',
    'function records')

# Register Step 5 regression methods.
tests = replace_once(
    tests,
    '''        radicalSelectionReplacementKeepsStructure();\n        radicalEvaluationStillWorks();\n        System.out.println("PASS " + checks + " semantic cursor checks");''',
    '''        radicalSelectionReplacementKeepsStructure();\n        radicalEvaluationStillWorks();\n        singleFunctionPublishesArgumentAndMovesSemantically();\n        multiFunctionArgumentsMoveWithoutTouchingCommas();\n        functionDeleteNeverBreaksStructure();\n        functionSelectionReplacementKeepsStructure();\n        functionEvaluationStillWorks();\n        System.out.println("PASS " + checks + " semantic cursor checks");''',
    'function test registration')

# Insert Step 5 tests before existing helper-machine builders.
test_anchor = '    private CnCwMachine squareRootMachine(boolean closed) {'
if test_anchor not in tests:
    raise SystemExit('function test insertion anchor not found')

function_tests = r'''    private void singleFunctionPublishesArgumentAndMovesSemantically() {
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
        equal("sin(", machine.state().expression(),
                "DEL at first argument start exits instead of deleting template");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "argument-start DEL exits function to root-before");

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
                "touch fine-selection can select one function argument only");
        equal(2, machine.pasteExpression("45"),
                "single function argument accepts semantic replacement");
        equal("sin(45)", machine.state().expression(),
                "argument replacement preserves function template and close");
        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,
                machine.state().semanticCursor().slot(),
                "replacement cursor remains in function argument");

        machine = sumMachine();
        machine.beginTouchSelection(3);
        machine.extendTouchSelection(4);
        equal("1", machine.selectedExpression(),
                "touch fine-selection can isolate middle function argument");
        equal(1, machine.pasteExpression("2"),
                "middle function argument accepts replacement");
        equal("sum(x,2,3)", machine.state().expression(),
                "middle argument replacement preserves both commas");
        equal(1, machine.state().semanticCursor().childPath().get(1),
                "replacement remains associated with second argument");

        machine = sumMachine();
        machine.beginTouchSelection(3);
        machine.extendTouchSelection(6);
        equal("sum(x,1,3)", machine.selectedExpression(),
                "touch selection crossing an argument comma expands to whole function");
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

'''
tests = tests.replace(test_anchor, function_tests + test_anchor, 1)

machine_path.write_text(text, encoding='utf-8')
test_path.write_text(tests, encoding='utf-8')
print('Step 5 function semantic patch applied')

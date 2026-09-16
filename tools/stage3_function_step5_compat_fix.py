from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
test_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
text = machine_path.read_text(encoding='utf-8')
tests = test_path.read_text(encoding='utf-8')

# Preserve the Stage 2 rule that a freshly inserted bare function token is
# deleted atomically by DEL.
old = '''        if (function.offset == 0) {\n            if (function.argumentIndex > 0) {\n                int previousIndex = function.argumentIndex - 1;\n                FunctionArgumentBounds previous = bounds.arguments.get(previousIndex);\n                setFunctionCursor(bounds, previousIndex, previous.end - previous.start);\n            } else {\n                setRootCursor(bounds.templateIndex);\n            }\n            finishSemanticCursorMove();\n            return true;\n        }'''
new = '''        if (function.offset == 0) {\n            if (function.argumentIndex > 0) {\n                int previousIndex = function.argumentIndex - 1;\n                FunctionArgumentBounds previous = bounds.arguments.get(previousIndex);\n                setFunctionCursor(bounds, previousIndex, previous.end - previous.start);\n                finishSemanticCursorMove();\n                return true;\n            }\n            // Stage 2 contract: a freshly inserted bare function token such as\n            // sin( is one semantic token, so DEL removes it in one press.\n            if (bounds.closeIndex < 0 && bounds.arguments.size() == 1\n                    && argument.start == argument.end\n                    && bounds.endExclusive == bounds.templateIndex + 1) {\n                rememberUndo();\n                tokens.remove(bounds.templateIndex);\n                setRootCursor(bounds.templateIndex);\n                finishSemanticEditMutation();\n                return true;\n            }\n            setRootCursor(bounds.templateIndex);\n            finishSemanticCursorMove();\n            return true;\n        }'''
if old not in text:
    raise SystemExit('bare function DEL anchor not found')
text = text.replace(old, new, 1)

old_test = '''        machine.dispatch(CnCwKey.DEL);\n        equal("sin(", machine.state().expression(),\n                "DEL at first argument start exits instead of deleting template");\n        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),\n                "argument-start DEL exits function to root-before");'''
new_test = '''        machine.dispatch(CnCwKey.DEL);\n        equal("", machine.state().expression(),\n                "DEL on a bare empty function preserves Stage 2 whole-token deletion");\n        equal(CnCwCursorPath.rootBoundary(0), machine.state().semanticCursor(),\n                "bare function deletion returns to root boundary");'''
if old_test not in tests:
    raise SystemExit('bare function test anchor not found')
tests = tests.replace(old_test, new_test, 1)

# Step 5 must not pre-empt Step 6: touch selection inside any ordinary function
# still expands to the complete call. Parameter-level touch selection is deferred.
new_selection_block = '''                } else {\n                    RadicalBounds radical = radicalContainingToken(index);\n                    FunctionBounds function = functionContainingToken(index);\n                    boolean withinRadical = radical != null\n                            && selectionWithinRadicalSlot(start, end, radical);\n                    boolean withinFunction = function != null\n                            && selectionWithinFunctionArgument(start, end, function);\n                    if (!withinRadical && !withinFunction) {\n                        int enclosing = enclosingOpen(index);\n                        if (enclosing >= 0) {\n                            int close = matchingClose(enclosing);\n                            unitStart = enclosing;\n                            unitEnd = close >= 0 ? close + 1 : unitEnd;\n                        }\n                    }\n                }'''
legacy_selection_block = '''                } else {\n                    RadicalBounds radical = radicalContainingToken(index);\n                    if (radical == null || !selectionWithinRadicalSlot(start, end, radical)) {\n                        int enclosing = enclosingOpen(index);\n                        if (enclosing >= 0) {\n                            int close = matchingClose(enclosing);\n                            unitStart = enclosing;\n                            unitEnd = close >= 0 ? close + 1 : unitEnd;\n                        }\n                    }\n                }'''
if new_selection_block not in text:
    raise SystemExit('function touch-selection compatibility anchor not found')
text = text.replace(new_selection_block, legacy_selection_block, 1)

old_method = r'''    private void functionSelectionReplacementKeepsStructure() {
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
'''
new_method = r'''    private void functionSelectionReplacementKeepsStructure() {
        CnCwMachine machine = sinMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(3);
        equal("sin(30)", machine.selectedExpression(),
                "Stage 2 touch selection still expands partial function drag to whole call");

        // Parameter-level replacement is already possible through the semantic
        // cursor + DEL path; unified fine-selection is intentionally Step 6.
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
'''
if old_method not in tests:
    raise SystemExit('function selection test method anchor not found')
tests = tests.replace(old_method, new_method, 1)

machine_path.write_text(text, encoding='utf-8')
test_path.write_text(tests, encoding='utf-8')
print('Step 5 compatibility fix applied')

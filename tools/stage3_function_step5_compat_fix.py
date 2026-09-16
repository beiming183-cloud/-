from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
test_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
text = machine_path.read_text(encoding='utf-8')
tests = test_path.read_text(encoding='utf-8')

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

machine_path.write_text(text, encoding='utf-8')
test_path.write_text(tests, encoding='utf-8')
print('Step 5 compatibility fix applied')

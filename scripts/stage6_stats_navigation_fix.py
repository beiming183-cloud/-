from pathlib import Path

path = Path('core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java')
text = path.read_text(encoding='utf-8')
old = '''        machine.performWorkflowAction(CnCwWorkflowAction.Type.ADD_ROW);\n        fillGrid(machine, "10", "2", "20", "1");\n'''
new = '''        machine.performWorkflowAction(CnCwWorkflowAction.Type.ADD_ROW);\n        equal(1, machine.state().workflowInput().selectedRow(),\n                "adding a statistics row focuses the new row");\n        machine.selectWorkflowCell(0, 0);\n        fillGrid(machine, "10", "2", "20", "1");\n'''
if old not in text:
    raise SystemExit('target block not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')

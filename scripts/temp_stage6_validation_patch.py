from pathlib import Path

root = Path(__file__).resolve().parents[1]

def replace_once(text, old, new, name):
    if old not in text:
        raise SystemExit(f"target not found: {name}")
    return text.replace(old, new, 1)

# Session: EXECUTE availability follows syntax validation, not blank-only completeness.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSession.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        values.add(action(CnCwWorkflowAction.Type.EXECUTE, "计算", isComplete()));
''',
'''        values.add(action(CnCwWorkflowAction.Type.EXECUTE, "计算",
                CnCwWorkflowValidation.validate(this).ready()));
''', 'session execute readiness')
p.write_text(text, encoding='utf-8')

# Machine: use the action protocol for SHIFT sizing and focus the first invalid cell on EXE.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''            if (!workflowSession.isComplete()) {
                workflowSession.selectFirstBlank();
                loadWorkflowCell();
                status = "还有空白项 · " + workflowStatus();
                return true;
            }
''',
'''            CnCwWorkflowValidation.Report validation =
                    CnCwWorkflowValidation.validate(workflowSession);
            if (!validation.ready()) {
                workflowSession.selectCell(validation.firstProblemRow(),
                        validation.firstProblemColumn());
                loadWorkflowCell();
                CnCwWorkflowValidation.CellState problem = validation.cell(
                        validation.firstProblemRow(), validation.firstProblemColumn(),
                        workflowSession.columns());
                status = problem.status() == CnCwWorkflowValidation.Status.EMPTY
                        ? "还有空白项 · " + workflowStatus()
                        : "输入格式错误 · " + workflowStatus();
                return true;
            }
''', 'machine workflow validation')

old_resize = '''    private boolean resizeWorkflowFromShift(CnCwKey key) {
        CnCwWorkflowSpec.InputLayout layout = workflowSession.spec().layout();
        int rows = workflowSession.rows();
        int columns = workflowSession.columns();
        if (layout == CnCwWorkflowSpec.InputLayout.GRID
                || layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET) {
            if (key == CnCwKey.UP) rows--;
            else if (key == CnCwKey.DOWN) rows++;
            else if (key == CnCwKey.LEFT) columns--;
            else if (key == CnCwKey.RIGHT) columns++;
            return workflowSession.resizeGrid(rows, columns);
        }
        if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS
                && workflowSession.spec().mode() == ApplicationMode.EQUATION) {
            if ("simultaneous".equals(workflowSession.spec().commandId())) {
                if (key == CnCwKey.UP) return workflowSession.setEquationDimension(rows - 1);
                if (key == CnCwKey.DOWN) return workflowSession.setEquationDimension(rows + 1);
                return false;
            }
            if (key == CnCwKey.UP) return workflowSession.resizeGrid(rows - 1, columns);
            if (key == CnCwKey.DOWN) return workflowSession.resizeGrid(rows + 1, columns);
        }
        return false;
    }
'''
new_resize = '''    private boolean resizeWorkflowFromShift(CnCwKey key) {
        CnCwWorkflowSpec.InputLayout layout = workflowSession.spec().layout();
        CnCwWorkflowAction.Type action = null;
        if (layout == CnCwWorkflowSpec.InputLayout.GRID
                || layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET) {
            action = switch (key) {
                case UP -> CnCwWorkflowAction.Type.DECREASE_ROWS;
                case DOWN -> CnCwWorkflowAction.Type.INCREASE_ROWS;
                case LEFT -> CnCwWorkflowAction.Type.DECREASE_COLUMNS;
                case RIGHT -> CnCwWorkflowAction.Type.INCREASE_COLUMNS;
                default -> null;
            };
        } else if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS
                && workflowSession.spec().mode() == ApplicationMode.EQUATION) {
            action = switch (key) {
                case UP -> CnCwWorkflowAction.Type.DECREASE_ROWS;
                case DOWN -> CnCwWorkflowAction.Type.INCREASE_ROWS;
                default -> null;
            };
        }
        return action != null && workflowSession.applyAction(action);
    }
'''
text = replace_once(text, old_resize, new_resize, 'machine resize action bridge')
p.write_text(text, encoding='utf-8')

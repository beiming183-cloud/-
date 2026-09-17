from pathlib import Path

root = Path(__file__).resolve().parents[1]

def replace_once(text, old, new, name):
    if old not in text:
        raise SystemExit(f"target not found: {name}")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# Session snapshot + human-readable shape summary.
# ---------------------------------------------------------------------------
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSession.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        private final boolean complete;
        private final List<CnCwWorkflowAction> actions;

        private Snapshot(CnCwWorkflowSpec.WorkflowSpec spec, int rows, int columns,
                         List<String> cells, int selectedRow, int selectedColumn,
                         boolean complete, List<CnCwWorkflowAction> actions) {
''',
'''        private final boolean complete;
        private final String shapeLabel;
        private final List<CnCwWorkflowAction> actions;

        private Snapshot(CnCwWorkflowSpec.WorkflowSpec spec, int rows, int columns,
                         List<String> cells, int selectedRow, int selectedColumn,
                         boolean complete, String shapeLabel,
                         List<CnCwWorkflowAction> actions) {
''', 'snapshot shape field')
text = replace_once(text,
'''            this.complete = complete;
            this.actions = com.codex.fx991.core.Compat.copyList(actions);
''',
'''            this.complete = complete;
            this.shapeLabel = shapeLabel == null ? "" : shapeLabel;
            this.actions = com.codex.fx991.core.Compat.copyList(actions);
''', 'snapshot shape assignment')
text = replace_once(text,
'''        public boolean complete() { return complete; }
        public List<CnCwWorkflowAction> actions() { return actions; }
''',
'''        public boolean complete() { return complete; }
        public String shapeLabel() { return shapeLabel; }
        public List<CnCwWorkflowAction> actions() { return actions; }
''', 'snapshot shape accessor')
text = replace_once(text,
'''        return new Snapshot(spec, rows, columns, cells,
                selectedRow, selectedColumn, isComplete(), actions());
''',
'''        return new Snapshot(spec, rows, columns, cells,
                selectedRow, selectedColumn, isComplete(), shapeLabel(), actions());
''', 'snapshot shape construction')
text = replace_once(text,
'''    public int selectedColumn() { return selectedColumn; }

    public String cell(int row, int column) {
''',
'''    public int selectedColumn() { return selectedColumn; }

    /** Human-readable current data shape for status bars and action panels. */
    public String shapeLabel() {
        return switch (spec.layout()) {
            case SERIES -> rows + " 条";
            case PAIRED_SERIES -> rows + " 组";
            case FIXED_FIELDS -> columns + " 项";
            case COEFFICIENTS -> isSimultaneous()
                    ? rows + " 元" : polynomialDegreeLabel(rows - 1);
            case GRID -> rows + "×" + columns;
            case VECTOR_SET -> rows + " 个 · " + columns + "D";
        };
    }

    public String cell(int row, int column) {
''', 'session shape method')
text = replace_once(text,
'''    private boolean isSimultaneous() {
        return spec.mode() == ApplicationMode.EQUATION
                && "simultaneous".equals(spec.commandId());
    }

    private void resize(int newRows, int newColumns) {
''',
'''    private boolean isSimultaneous() {
        return spec.mode() == ApplicationMode.EQUATION
                && "simultaneous".equals(spec.commandId());
    }

    private String polynomialDegreeLabel(int degree) {
        return switch (degree) {
            case 2 -> "二次";
            case 3 -> "三次";
            case 4 -> "四次";
            default -> degree + " 次";
        };
    }

    private void resize(int newRows, int newColumns) {
''', 'degree label helper')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Machine: one public mutation entry and status uses core shape label.
# ---------------------------------------------------------------------------
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''    /** Direct-touch entry point for a Stage 5 input-table cell. */
    public CnCwUiState selectWorkflowCell(int row, int column) {
''',
'''    /**
     * Applies one non-execution workflow action from a touch/menu adapter.
     * EXECUTE and BACK stay on the normal key-dispatch path so Android can keep
     * heavy EXE work asynchronous and preserve the established navigation path.
     */
    public CnCwUiState applyWorkflowMutation(CnCwWorkflowAction.Type type) {
        if (workflowSession == null || resultShown || type == null
                || type == CnCwWorkflowAction.Type.EXECUTE
                || type == CnCwWorkflowAction.Type.BACK) return state;
        commitWorkflowCell();
        boolean changed = workflowSession.applyAction(type);
        if (changed) loadWorkflowCell();
        status = changed ? workflowStatus() : "已到输入尺寸边界";
        publish();
        return state;
    }

    /** Direct-touch entry point for a Stage 5 input-table cell. */
    public CnCwUiState selectWorkflowCell(int row, int column) {
''', 'machine public workflow mutation')
text = replace_once(text,
'''        return workflowSession.spec().title() + " · "
                + (workflowSession.selectedRow() + 1) + ","
                + (workflowSession.selectedColumn() + 1);
''',
'''        return workflowSession.spec().title() + " · " + workflowSession.shapeLabel()
                + " · " + (workflowSession.selectedRow() + 1) + ","
                + (workflowSession.selectedColumn() + 1);
''', 'machine workflow status shape')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Workflow spec/session regression: shape labels and mutation updates.
# ---------------------------------------------------------------------------
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwWorkflowSpecSuite.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        check(action(statistics, CnCwWorkflowAction.Type.ADD_ROW).enabled(),
                "statistics publishes add-row action");
''',
'''        equal("1 条", statistics.shapeLabel(), "statistics publishes initial shape label");
        check(action(statistics, CnCwWorkflowAction.Type.ADD_ROW).enabled(),
                "statistics publishes add-row action");
''', 'statistics shape initial')
text = replace_once(text,
'''        equal(2, statistics.rows(), "add-row action increases statistics rows");
        check(action(statistics, CnCwWorkflowAction.Type.REMOVE_ROW).enabled(),
''',
'''        equal(2, statistics.rows(), "add-row action increases statistics rows");
        equal("2 条", statistics.shapeLabel(), "statistics shape follows add-row action");
        check(action(statistics, CnCwWorkflowAction.Type.REMOVE_ROW).enabled(),
''', 'statistics shape update')
text = replace_once(text,
'''        check(!action(simultaneous, CnCwWorkflowAction.Type.DECREASE_ROWS).enabled(),
                "two-variable system disables lower dimension");
''',
'''        equal("2 元", simultaneous.shapeLabel(), "simultaneous publishes equation dimension");
        check(!action(simultaneous, CnCwWorkflowAction.Type.DECREASE_ROWS).enabled(),
                "two-variable system disables lower dimension");
''', 'simultaneous shape initial')
text = replace_once(text,
'''        equal(4, simultaneous.columns(), "simultaneous action preserves augmented shape");

        var matrix = CnCwWorkflowSession.create(
''',
'''        equal(4, simultaneous.columns(), "simultaneous action preserves augmented shape");
        equal("3 元", simultaneous.shapeLabel(), "simultaneous shape follows action");

        var polynomial = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.EQUATION, "polynomial"));
        equal("二次", polynomial.shapeLabel(), "polynomial starts as quadratic");
        check(polynomial.applyAction(CnCwWorkflowAction.Type.INCREASE_ROWS),
                "polynomial can raise order through action protocol");
        equal("三次", polynomial.shapeLabel(), "polynomial shape follows order action");

        var matrix = CnCwWorkflowSession.create(
''', 'polynomial shape')
text = replace_once(text,
'''        equal(2, matrix.columns(), "matrix action column count");
        check(action(matrix, CnCwWorkflowAction.Type.BACK).enabled(),
''',
'''        equal(2, matrix.columns(), "matrix action column count");
        equal("2×2", matrix.shapeLabel(), "matrix publishes current dimensions");
        check(action(matrix, CnCwWorkflowAction.Type.BACK).enabled(),
''', 'matrix shape')
text = replace_once(text,
'''        var snapshot = matrix.snapshot();
        equal(matrix.actions(), snapshot.actions(), "snapshot publishes workflow actions");
''',
'''        var vector = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.VECTOR, "calculate"));
        check(vector.applyAction(CnCwWorkflowAction.Type.INCREASE_ROWS),
                "vector action adds second vector");
        check(vector.applyAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS),
                "vector action raises dimension to 3D");
        equal("2 个 · 3D", vector.shapeLabel(), "vector publishes vector count and dimension");

        var snapshot = matrix.snapshot();
        equal(matrix.actions(), snapshot.actions(), "snapshot publishes workflow actions");
        equal("2×2", snapshot.shapeLabel(), "snapshot freezes workflow shape label");
''', 'vector and snapshot shape')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Machine regression: unified mutation API commits current cell and updates UI.
# ---------------------------------------------------------------------------
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''import com.codex.fx991.core.cw.CnCwUiState;
''',
'''import com.codex.fx991.core.cw.CnCwUiState;
import com.codex.fx991.core.cw.CnCwWorkflowAction;
''', 'machine suite workflow action import')
text = replace_once(text,
'''        structuredWorkflowInputPageRunsEndToEnd();
        spreadsheetCompactWorkflowPersistsCells();
''',
'''        structuredWorkflowInputPageRunsEndToEnd();
        workflowMutationActionsStayCoreOwned();
        spreadsheetCompactWorkflowPersistsCells();
''', 'machine suite run action test')
marker = '''    private void spreadsheetCompactWorkflowPersistsCells() {
'''
method = '''    private void workflowMutationActionsStayCoreOwned() {
        CnCwMachine matrix = homeApplication(CnCwModel.FX_991_CN_CW, 6);
        matrix.dispatch(CnCwKey.OK);
        check(matrix.state().hasWorkflowInput(), "matrix command opens structured workflow");
        equal("1×1", matrix.state().workflowInput().shapeLabel(),
                "matrix UI snapshot starts with core-owned shape label");
        press(matrix, CnCwKey.DIGIT_7);
        matrix.applyWorkflowMutation(CnCwWorkflowAction.Type.INCREASE_ROWS);
        check(matrix.state().hasWorkflowInput(), "matrix remains in workflow after mutation");
        equal("2×1", matrix.state().workflowInput().shapeLabel(),
                "machine mutation updates core-owned shape");
        equal("7", matrix.state().workflowInput().cell(0, 0),
                "machine mutation commits active cell before resizing");
        check(matrix.state().status().contains("2×1"),
                "workflow status exposes human-readable dimensions");

        matrix.applyWorkflowMutation(CnCwWorkflowAction.Type.DECREASE_ROWS);
        equal("1×1", matrix.state().workflowInput().shapeLabel(),
                "machine mutation can reduce dimensions again");
    }

'''
if marker not in text:
    raise SystemExit('machine suite method marker not found')
text = text.replace(marker, method + marker, 1)
p.write_text(text, encoding='utf-8')

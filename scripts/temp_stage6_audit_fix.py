from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_once(path, old, new, name):
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"target not found: {name}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Preserve coefficient meaning when polynomial degree / simultaneous dimension changes.
p = root / "core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSession.java"
replace_once(
    p,
    '''    public boolean resizeGrid(int newRows, int newColumns) {
        if (newRows < spec.minRows() || newRows > spec.maxRows()
                || newColumns < spec.minColumns() || newColumns > spec.maxColumns()) {
            return false;
        }
        if (isSimultaneous() && newColumns != newRows + 1) return false;
        resize(newRows, newColumns);
        selectedRow = Math.min(selectedRow, rows - 1);
        selectedColumn = Math.min(selectedColumn, columns - 1);
        return true;
    }

    /** Changes a 2–4 variable simultaneous system to n rows × (n+1) columns. */
    public boolean setEquationDimension(int dimension) {
        if (!isSimultaneous()) return false;
        return resizeGrid(dimension, dimension + 1);
    }
''',
    '''    public boolean resizeGrid(int newRows, int newColumns) {
        if (newRows < spec.minRows() || newRows > spec.maxRows()
                || newColumns < spec.minColumns() || newColumns > spec.maxColumns()) {
            return false;
        }
        if (isSimultaneous()) {
            if (newColumns != newRows + 1) return false;
            return resizeSimultaneous(newRows);
        }
        if (isPolynomial()) {
            if (newColumns != 1) return false;
            return resizePolynomial(newRows);
        }
        resize(newRows, newColumns);
        selectedRow = Math.min(selectedRow, rows - 1);
        selectedColumn = Math.min(selectedColumn, columns - 1);
        return true;
    }

    /** Changes a 2–4 variable simultaneous system to n rows × (n+1) columns. */
    public boolean setEquationDimension(int dimension) {
        if (!isSimultaneous()) return false;
        return resizeGrid(dimension, dimension + 1);
    }
''',
    "semantic coefficient resize route",
)

marker = '''    private boolean resizeRows(int newRows) {
        if (isSimultaneous()) return setEquationDimension(newRows);
        return resizeGrid(newRows, columns);
    }
'''
helpers = '''    /** Keeps a_k attached to the same power when the polynomial degree changes. */
    private boolean resizePolynomial(int newRows) {
        List<String> previous = new ArrayList<>(cells);
        int oldRows = rows;
        int oldSelectedRow = selectedRow;
        int oldToNewShift = newRows - oldRows;
        cells.clear();
        for (int row = 0; row < newRows; row++) {
            int oldRow = row - oldToNewShift;
            cells.add(oldRow >= 0 && oldRow < oldRows ? previous.get(oldRow) : "");
        }
        rows = newRows;
        columns = 1;
        selectedRow = clamp(oldSelectedRow + oldToNewShift, 0, rows - 1);
        selectedColumn = 0;
        return true;
    }

    /** Keeps each variable coefficient and the augmented RHS column in its semantic slot. */
    private boolean resizeSimultaneous(int newRows) {
        List<String> previous = new ArrayList<>(cells);
        int oldRows = rows;
        int oldColumns = columns;
        int oldSelectedRow = selectedRow;
        int oldSelectedColumn = selectedColumn;
        int newColumns = newRows + 1;
        cells.clear();
        for (int row = 0; row < newRows; row++) {
            for (int column = 0; column < newColumns; column++) {
                String value = "";
                if (row < oldRows) {
                    if (column < Math.min(oldRows, newRows)) {
                        value = previous.get(row * oldColumns + column);
                    } else if (column == newRows) {
                        value = previous.get(row * oldColumns + oldColumns - 1);
                    }
                }
                cells.add(value);
            }
        }
        rows = newRows;
        columns = newColumns;
        selectedRow = clamp(oldSelectedRow, 0, rows - 1);
        selectedColumn = oldSelectedColumn == oldColumns - 1
                ? columns - 1 : clamp(oldSelectedColumn, 0, rows - 1);
        return true;
    }

'''
text = p.read_text(encoding="utf-8")
if marker not in text:
    raise SystemExit("target not found: resize helpers marker")
p.write_text(text.replace(marker, helpers + marker, 1), encoding="utf-8")

replace_once(
    p,
    '''    private boolean isSimultaneous() {
        return spec.mode() == ApplicationMode.EQUATION
                && "simultaneous".equals(spec.commandId());
    }
''',
    '''    private boolean isPolynomial() {
        return spec.mode() == ApplicationMode.EQUATION
                && "polynomial".equals(spec.commandId());
    }

    private boolean isSimultaneous() {
        return spec.mode() == ApplicationMode.EQUATION
                && "simultaneous".equals(spec.commandId());
    }
''',
    "polynomial identity helper",
)

# 2) Lock the semantic-preservation behavior in the session regression suite.
p = root / "core/src/regression/java/com/codex/fx991/core/CnCwWorkflowSpecSuite.java"
replace_once(
    p,
    '''import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
''',
    '''import com.codex.fx991.core.cw.CnCwWorkflowAction;
import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
''',
    "workflow action import",
)
replace_once(
    p,
    '''        check(polynomial.resizeGrid(4, 1), "polynomial can resize to cubic coefficient count");
        equal(4, polynomial.rows(), "polynomial resized coefficient rows");
''',
    '''        check(polynomial.resizeGrid(4, 1), "polynomial can resize to cubic coefficient count");
        equal(4, polynomial.rows(), "polynomial resized coefficient rows");
        equal("", polynomial.cell(0, 0), "raising degree inserts a new leading coefficient");
        equal("1", polynomial.cell(1, 0), "a2 stays attached to x^2 after raising degree");
        equal("2", polynomial.cell(2, 0), "a1 stays attached to x after raising degree");
        equal("-3", polynomial.cell(3, 0), "a0 stays constant after raising degree");
        polynomial.setCell(0, 0, "9");
        check(polynomial.applyAction(CnCwWorkflowAction.Type.DECREASE_ROWS),
                "polynomial action can lower degree");
        equal("1", polynomial.cell(0, 0), "lowering degree drops only the old leading coefficient");
        equal("-3", polynomial.cell(2, 0), "lowering degree preserves the constant term");
''',
    "polynomial semantic resize regression",
)
replace_once(
    p,
    '''        check(simultaneous.setEquationDimension(3), "simultaneous can switch to three variables");
        equal(3, simultaneous.rows(), "three-variable equation rows");
        equal(4, simultaneous.columns(), "three-variable augmented columns");
        check(!simultaneous.resizeGrid(3, 3), "simultaneous rejects invalid non-augmented shape");
''',
    '''        check(simultaneous.setEquationDimension(3), "simultaneous can switch to three variables");
        equal(3, simultaneous.rows(), "three-variable equation rows");
        equal(4, simultaneous.columns(), "three-variable augmented columns");
        equal("1", simultaneous.cell(0, 0), "x1 coefficient survives dimension increase");
        equal("1", simultaneous.cell(0, 1), "x2 coefficient survives dimension increase");
        equal("", simultaneous.cell(0, 2), "new x3 coefficient starts blank");
        equal("5", simultaneous.cell(0, 3), "RHS stays in augmented column after dimension increase");
        equal("1", simultaneous.cell(1, 3), "second RHS stays augmented after dimension increase");
        check(simultaneous.applyAction(CnCwWorkflowAction.Type.DECREASE_ROWS),
                "simultaneous action can reduce dimension");
        equal(2, simultaneous.rows(), "dimension reduction returns to two variables");
        equal(3, simultaneous.columns(), "two-variable augmented width restored");
        equal("5", simultaneous.cell(0, 2), "RHS survives dimension reduction");
        equal("1", simultaneous.cell(1, 2), "second RHS survives dimension reduction");
        check(!simultaneous.resizeGrid(3, 3), "simultaneous rejects invalid non-augmented shape");
''',
    "simultaneous semantic resize regression",
)

# 3) Keep all workflow actions on one larger row instead of 16.5dp two-row targets.
p = root / "app/src/main/java/com/codex/fx991smooth/CalculatorView.java"
replace_once(
    p,
    '''    private RectF workflowGridBounds(RectF lcd) {
        float top = lcd.top + lcd.height() * 0.22f;
        float bottom = lcd.bottom - dp(43f);
        return new RectF(lcd.left + dp(6f), top, lcd.right - dp(6f), bottom);
    }

    private RectF workflowActionBarBounds(RectF lcd) {
        return new RectF(lcd.left + dp(6f), lcd.bottom - dp(38f),
                lcd.right - dp(6f), lcd.bottom - dp(3f));
    }

    private RectF workflowActionBounds(RectF lcd, int count, int index) {
        RectF bar = workflowActionBarBounds(lcd);
        int columns = count <= 2 ? Math.max(1, count) : 3;
        int rows = Math.max(1, (count + columns - 1) / columns);
        int row = index / columns;
        int column = index % columns;
        float gap = dp(2f);
        float width = (bar.width() - gap * (columns - 1)) / columns;
        float height = (bar.height() - gap * (rows - 1)) / rows;
        float left = bar.left + column * (width + gap);
        float top = bar.top + row * (height + gap);
        return new RectF(left, top, left + width, top + height);
    }
''',
    '''    private RectF workflowGridBounds(RectF lcd) {
        float top = lcd.top + lcd.height() * 0.22f;
        float bottom = lcd.bottom - dp(50f);
        return new RectF(lcd.left + dp(6f), top, lcd.right - dp(6f), bottom);
    }

    private RectF workflowActionBarBounds(RectF lcd) {
        return new RectF(lcd.left + dp(6f), lcd.bottom - dp(45f),
                lcd.right - dp(6f), lcd.bottom - dp(3f));
    }

    private RectF workflowActionBounds(RectF lcd, int count, int index) {
        RectF bar = workflowActionBarBounds(lcd);
        int columns = Math.max(1, count);
        float gap = dp(1.5f);
        float width = (bar.width() - gap * (columns - 1)) / columns;
        float left = bar.left + index * (width + gap);
        return new RectF(left, bar.top, left + width, bar.bottom);
    }
''',
    "larger one-row action bar",
)
replace_once(
    p,
    '''            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(8.2f));
            canvas.drawText(ellipsize(action.label(), bounds.width() - dp(5f)),
''',
    '''            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(actions.size() >= 5 ? 7.6f : 8.2f));
            canvas.drawText(ellipsize(action.label(), bounds.width() - dp(4f)),
''',
    "action bar text fit",
)

p.write_text(p.read_text(encoding="utf-8"), encoding="utf-8")

package com.codex.fx991.core.cw;

import com.codex.fx991.core.math.ScalarExpressionEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable core-owned editor state for one structured application workflow.
 *
 * <p>Stage 5 starts with statistics series editors. Later coefficient/grid
 * workflows reuse the same row-major state. Evaluation still delegates to the
 * existing CnCwModeEngine bridge, so numerical behavior is not duplicated.</p>
 */
public final class CnCwWorkflowSession {
    private final CnCwWorkflowSpec.WorkflowSpec spec;
    private int rows;
    private int columns;
    private final List<String> cells = new ArrayList<>();
    private int selectedRow;
    private int selectedColumn;

    private CnCwWorkflowSession(CnCwWorkflowSpec.WorkflowSpec spec,
                                int rows, int columns) {
        if (spec == null) throw new IllegalArgumentException("spec");
        this.spec = spec;
        resize(rows, columns);
    }

    public static CnCwWorkflowSession create(CnCwWorkflowSpec.WorkflowSpec spec) {
        if (spec == null) throw new IllegalArgumentException("spec");
        int rows;
        int columns;
        switch (spec.layout()) {
            case SERIES -> {
                rows = spec.minRows();
                columns = 1;
            }
            case PAIRED_SERIES -> {
                rows = spec.minRows();
                columns = 2;
            }
            case FIXED_FIELDS -> {
                rows = 1;
                columns = spec.fields().size();
            }
            case COEFFICIENTS -> {
                rows = spec.minRows();
                columns = spec.minColumns();
            }
            case GRID, VECTOR_SET -> {
                rows = spec.minRows();
                columns = spec.minColumns();
            }
            default -> throw new IllegalArgumentException("Unsupported layout");
        }
        return new CnCwWorkflowSession(spec, rows, columns);
    }

    public CnCwWorkflowSpec.WorkflowSpec spec() { return spec; }
    public int rows() { return rows; }
    public int columns() { return columns; }
    public int selectedRow() { return selectedRow; }
    public int selectedColumn() { return selectedColumn; }

    public String cell(int row, int column) {
        return cells.get(index(row, column));
    }

    public void setCell(int row, int column, String value) {
        cells.set(index(row, column), value == null ? "" : value.trim());
    }

    public String selectedCell() {
        return cell(selectedRow, selectedColumn);
    }

    public void setSelectedCell(String value) {
        setCell(selectedRow, selectedColumn, value);
    }

    public boolean move(int rowDelta, int columnDelta) {
        int nextRow = clamp(selectedRow + rowDelta, 0, rows - 1);
        int nextColumn = clamp(selectedColumn + columnDelta, 0, columns - 1);
        boolean changed = nextRow != selectedRow || nextColumn != selectedColumn;
        selectedRow = nextRow;
        selectedColumn = nextColumn;
        return changed;
    }

    public boolean appendRow() {
        if (rows >= spec.maxRows()) return false;
        int oldRows = rows;
        resize(rows + 1, columns);
        selectedRow = oldRows;
        selectedColumn = Math.min(selectedColumn, columns - 1);
        return true;
    }

    public boolean removeSelectedRow() {
        if (rows <= spec.minRows()) return false;
        int remove = selectedRow;
        for (int column = columns - 1; column >= 0; column--) {
            cells.remove(remove * columns + column);
        }
        rows--;
        selectedRow = Math.min(selectedRow, rows - 1);
        return true;
    }

    public boolean resizeGrid(int newRows, int newColumns) {
        if (newRows < spec.minRows() || newRows > spec.maxRows()
                || newColumns < spec.minColumns() || newColumns > spec.maxColumns()) {
            return false;
        }
        resize(newRows, newColumns);
        selectedRow = Math.min(selectedRow, rows - 1);
        selectedColumn = Math.min(selectedColumn, columns - 1);
        return true;
    }

    public boolean isComplete() {
        for (String cell : cells) {
            if (com.codex.fx991.core.Compat.isBlank(cell)) return false;
        }
        return true;
    }

    /**
     * Compatibility serialization for the existing evaluator. Statistics and
     * vector data are row-major; matrix dimensions are prefixed automatically.
     */
    public String legacySource() {
        if (!isComplete()) throw new IllegalStateException("Workflow input incomplete");
        List<String> values = new ArrayList<>();
        if (spec.layout() == CnCwWorkflowSpec.InputLayout.GRID) {
            values.add(Integer.toString(rows));
            values.add(Integer.toString(columns));
        }
        values.addAll(cells);
        return com.codex.fx991.core.Compat.join(",", values);
    }

    public CnCwModeEngine.ModeResult evaluate(ScalarExpressionEngine.EvaluationContext context) {
        return CnCwModeEngine.evaluate(spec.mode(), spec.commandId(), legacySource(), context);
    }

    public List<String> cells() {
        return com.codex.fx991.core.Compat.copyList(cells);
    }

    private void resize(int newRows, int newColumns) {
        if (newRows < 1 || newColumns < 1) throw new IllegalArgumentException("shape");
        List<String> previous = new ArrayList<>(cells);
        int oldRows = rows;
        int oldColumns = columns;
        cells.clear();
        for (int row = 0; row < newRows; row++) {
            for (int column = 0; column < newColumns; column++) {
                String value = row < oldRows && column < oldColumns
                        ? previous.get(row * oldColumns + column) : "";
                cells.add(value);
            }
        }
        rows = newRows;
        columns = newColumns;
    }

    private int index(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IndexOutOfBoundsException(row + "," + column);
        }
        return row * columns + column;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

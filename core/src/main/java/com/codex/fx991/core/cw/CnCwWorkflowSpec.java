package com.codex.fx991.core.cw;

import com.codex.fx991.core.mode.ApplicationMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Core-owned input contract for structured application workflows.
 *
 * <p>The legacy comma-separated bridge remains valid, but Android can now ask
 * the core what shape and fields a workflow expects instead of parsing prompt
 * strings.  Later Stage 5 steps attach editable state to these immutable specs.</p>
 */
public final class CnCwWorkflowSpec {
    private CnCwWorkflowSpec() { }

    public enum InputLayout {
        FIXED_FIELDS,
        SERIES,
        PAIRED_SERIES,
        COEFFICIENTS,
        GRID,
        VECTOR_SET
    }

    public enum FieldKind {
        EXPRESSION,
        INTEGER,
        DIMENSION
    }

    public static final class FieldSpec {
        private final String id;
        private final String label;
        private final FieldKind kind;
        private final boolean required;

        public FieldSpec(String id, String label, FieldKind kind, boolean required) {
            if (com.codex.fx991.core.Compat.isBlank(id)) throw new IllegalArgumentException("id");
            if (com.codex.fx991.core.Compat.isBlank(label)) throw new IllegalArgumentException("label");
            if (kind == null) throw new IllegalArgumentException("kind");
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.required = required;
        }

        public String id() { return id; }
        public String label() { return label; }
        public FieldKind kind() { return kind; }
        public boolean required() { return required; }
    }

    public static final class WorkflowSpec {
        private final ApplicationMode mode;
        private final String commandId;
        private final String title;
        private final InputLayout layout;
        private final List<FieldSpec> fields;
        private final int minRows;
        private final int maxRows;
        private final int minColumns;
        private final int maxColumns;

        private WorkflowSpec(ApplicationMode mode, String commandId, String title,
                             InputLayout layout, List<FieldSpec> fields,
                             int minRows, int maxRows, int minColumns, int maxColumns) {
            if (mode == null) throw new IllegalArgumentException("mode");
            if (com.codex.fx991.core.Compat.isBlank(commandId)) throw new IllegalArgumentException("commandId");
            if (com.codex.fx991.core.Compat.isBlank(title)) throw new IllegalArgumentException("title");
            if (layout == null) throw new IllegalArgumentException("layout");
            if (fields == null || fields.isEmpty()) throw new IllegalArgumentException("fields");
            if (minRows < 1 || maxRows < minRows) throw new IllegalArgumentException("rows");
            if (minColumns < 1 || maxColumns < minColumns) throw new IllegalArgumentException("columns");
            this.mode = mode;
            this.commandId = commandId;
            this.title = title;
            this.layout = layout;
            this.fields = com.codex.fx991.core.Compat.copyList(fields);
            this.minRows = minRows;
            this.maxRows = maxRows;
            this.minColumns = minColumns;
            this.maxColumns = maxColumns;
        }

        public ApplicationMode mode() { return mode; }
        public String commandId() { return commandId; }
        public String title() { return title; }
        public InputLayout layout() { return layout; }
        public List<FieldSpec> fields() { return fields; }
        public int minRows() { return minRows; }
        public int maxRows() { return maxRows; }
        public int minColumns() { return minColumns; }
        public int maxColumns() { return maxColumns; }
        public boolean hasVariableRows() { return minRows != maxRows; }
        public boolean hasVariableColumns() { return minColumns != maxColumns; }
    }

    public static WorkflowSpec forCommand(ApplicationMode mode, String commandId) {
        if (mode == null) throw new IllegalArgumentException("mode");
        if (com.codex.fx991.core.Compat.isBlank(commandId)) throw new IllegalArgumentException("commandId");
        return switch (mode) {
            case STATISTICS -> statistics(commandId);
            case FUNCTION_TABLE -> functionTable(commandId);
            case EQUATION -> equation(commandId);
            case MATRIX -> matrix(commandId);
            case VECTOR -> vector(commandId);
            default -> null;
        };
    }

    private static WorkflowSpec statistics(String commandId) {
        if (commandId.equals("one")) {
            return spec(ApplicationMode.STATISTICS, commandId, "一元统计",
                    InputLayout.SERIES, fields(field("x", "x", FieldKind.EXPRESSION)),
                    1, 999, 1, 1);
        }
        if (commandId.equals("two") || commandId.equals("regression")) {
            return spec(ApplicationMode.STATISTICS, commandId,
                    commandId.equals("regression") ? "线性回归" : "双变量统计",
                    InputLayout.PAIRED_SERIES,
                    fields(field("x", "x", FieldKind.EXPRESSION),
                            field("y", "y", FieldKind.EXPRESSION)),
                    2, 999, 2, 2);
        }
        return null;
    }


    private static WorkflowSpec functionTable(String commandId) {
        if (commandId.equals("f")) {
            return spec(ApplicationMode.FUNCTION_TABLE, commandId, "函数表 f(x)",
                    InputLayout.FIXED_FIELDS,
                    fields(field("f", "f(x)", FieldKind.EXPRESSION),
                            field("start", "开始", FieldKind.EXPRESSION),
                            field("end", "结束", FieldKind.EXPRESSION),
                            field("step", "步长", FieldKind.EXPRESSION)),
                    1, 1, 4, 4);
        }
        if (commandId.equals("fg")) {
            return spec(ApplicationMode.FUNCTION_TABLE, commandId, "函数表 f(x), g(x)",
                    InputLayout.FIXED_FIELDS,
                    fields(field("f", "f(x)", FieldKind.EXPRESSION),
                            field("g", "g(x)", FieldKind.EXPRESSION),
                            field("start", "开始", FieldKind.EXPRESSION),
                            field("end", "结束", FieldKind.EXPRESSION),
                            field("step", "步长", FieldKind.EXPRESSION)),
                    1, 1, 5, 5);
        }
        return null;
    }

    private static WorkflowSpec equation(String commandId) {
        if (commandId.equals("polynomial")) {
            return spec(ApplicationMode.EQUATION, commandId, "多项式方程",
                    InputLayout.COEFFICIENTS,
                    fields(field("coefficient", "系数", FieldKind.EXPRESSION)),
                    3, 5, 1, 1);
        }
        if (commandId.equals("simultaneous")) {
            return spec(ApplicationMode.EQUATION, commandId, "联立方程",
                    InputLayout.COEFFICIENTS,
                    fields(field("dimension", "元数", FieldKind.DIMENSION),
                            field("coefficient", "系数", FieldKind.EXPRESSION),
                            field("constant", "常数", FieldKind.EXPRESSION)),
                    2, 4, 3, 5);
        }
        if (commandId.equals("solve")) {
            return spec(ApplicationMode.EQUATION, commandId, "SOLVE",
                    InputLayout.FIXED_FIELDS,
                    fields(field("expression", "f(x)", FieldKind.EXPRESSION),
                            field("initial", "初值", FieldKind.EXPRESSION)),
                    1, 1, 2, 2);
        }
        return null;
    }

    private static WorkflowSpec matrix(String commandId) {
        if (!commandId.equals("calculate")) return null;
        return spec(ApplicationMode.MATRIX, commandId, "矩阵",
                InputLayout.GRID,
                fields(field("rows", "行", FieldKind.DIMENSION),
                        field("columns", "列", FieldKind.DIMENSION),
                        field("cell", "元素", FieldKind.EXPRESSION)),
                1, 4, 1, 4);
    }

    private static WorkflowSpec vector(String commandId) {
        if (!commandId.equals("calculate")) return null;
        return spec(ApplicationMode.VECTOR, commandId, "向量",
                InputLayout.VECTOR_SET,
                fields(field("component", "分量", FieldKind.EXPRESSION)),
                1, 2, 2, 3);
    }

    private static WorkflowSpec spec(ApplicationMode mode, String commandId, String title,
                                     InputLayout layout, List<FieldSpec> fields,
                                     int minRows, int maxRows, int minColumns, int maxColumns) {
        return new WorkflowSpec(mode, commandId, title, layout, fields,
                minRows, maxRows, minColumns, maxColumns);
    }

    private static FieldSpec field(String id, String label, FieldKind kind) {
        return new FieldSpec(id, label, kind, true);
    }

    private static List<FieldSpec> fields(FieldSpec... values) {
        List<FieldSpec> fields = new ArrayList<>();
        for (FieldSpec value : values) fields.add(value);
        return fields;
    }
}

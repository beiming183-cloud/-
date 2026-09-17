from pathlib import Path

root = Path(__file__).resolve().parents[1]

def replace_once(text, old, new, name):
    if old not in text:
        raise SystemExit(f"target not found: {name}")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# WorkflowSpec: reusable CHOICE field + inequality specs.
# ---------------------------------------------------------------------------
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSpec.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''    public enum FieldKind {
        EXPRESSION,
        INTEGER,
        DIMENSION
    }

    public static final class FieldSpec {
''',
'''    public enum FieldKind {
        EXPRESSION,
        INTEGER,
        DIMENSION,
        CHOICE
    }

    /** Serialized evaluator value paired with a human-readable field label. */
    public static final class ChoiceOption {
        private final String value;
        private final String label;

        public ChoiceOption(String value, String label) {
            if (com.codex.fx991.core.Compat.isBlank(value)) throw new IllegalArgumentException("value");
            if (com.codex.fx991.core.Compat.isBlank(label)) throw new IllegalArgumentException("label");
            this.value = value;
            this.label = label;
        }

        public String value() { return value; }
        public String label() { return label; }
    }

    public static final class FieldSpec {
''', 'choice type')
text = replace_once(text,
'''        private final FieldKind kind;
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
''',
'''        private final FieldKind kind;
        private final boolean required;
        private final List<ChoiceOption> choices;

        public FieldSpec(String id, String label, FieldKind kind, boolean required) {
            this(id, label, kind, required, com.codex.fx991.core.Compat.list());
        }

        public FieldSpec(String id, String label, FieldKind kind, boolean required,
                         List<ChoiceOption> choices) {
            if (com.codex.fx991.core.Compat.isBlank(id)) throw new IllegalArgumentException("id");
            if (com.codex.fx991.core.Compat.isBlank(label)) throw new IllegalArgumentException("label");
            if (kind == null) throw new IllegalArgumentException("kind");
            if (choices == null) throw new IllegalArgumentException("choices");
            if (kind == FieldKind.CHOICE && choices.isEmpty()) {
                throw new IllegalArgumentException("choice options");
            }
            if (kind != FieldKind.CHOICE && !choices.isEmpty()) {
                throw new IllegalArgumentException("choices only valid for CHOICE");
            }
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.required = required;
            this.choices = com.codex.fx991.core.Compat.copyList(choices);
        }

        public String id() { return id; }
        public String label() { return label; }
        public FieldKind kind() { return kind; }
        public boolean required() { return required; }
        public List<ChoiceOption> choices() { return choices; }
        public String defaultValue() {
            return kind == FieldKind.CHOICE ? choices.get(0).value() : "";
        }
        public String displayValue(String raw) {
            if (kind != FieldKind.CHOICE) return raw == null ? "" : raw;
            String value = raw == null || raw.isBlank() ? defaultValue() : raw;
            for (ChoiceOption choice : choices) {
                if (choice.value().equals(value)) return choice.label();
            }
            return value;
        }
    }
''', 'field choice metadata')
text = replace_once(text,
'''            case FUNCTION_TABLE -> functionTable(commandId);
            case EQUATION -> equation(commandId);
            case MATRIX -> matrix(commandId);
''',
'''            case FUNCTION_TABLE -> functionTable(commandId);
            case EQUATION -> equation(commandId);
            case INEQUALITY -> inequality(commandId);
            case MATRIX -> matrix(commandId);
''', 'inequality route')
marker = '''    private static WorkflowSpec matrix(String commandId) {
'''
inequality = '''    private static WorkflowSpec inequality(String commandId) {
        int degree = switch (commandId) {
            case "quadratic" -> 2;
            case "cubic" -> 3;
            case "quartic" -> 4;
            default -> 0;
        };
        if (degree == 0) return null;
        List<FieldSpec> values = new ArrayList<>();
        values.add(choiceField("relation", "关系",
                choice("1", ">"), choice("2", "<"),
                choice("3", "≥"), choice("4", "≤")));
        for (int power = degree; power >= 0; power--) {
            String label = power == 0 ? "常数" : power == 1 ? "x" : "x^" + power;
            values.add(field("c" + power, label, FieldKind.EXPRESSION));
        }
        String title = degree == 2 ? "二次不等式" : degree == 3 ? "三次不等式" : "四次不等式";
        int columns = degree + 2;
        return spec(ApplicationMode.INEQUALITY, commandId, title,
                InputLayout.FIXED_FIELDS, values, 1, 1, columns, columns);
    }

'''
if marker not in text: raise SystemExit('inequality marker not found')
text = text.replace(marker, inequality + marker, 1)
text = replace_once(text,
'''    private static FieldSpec field(String id, String label, FieldKind kind) {
        return new FieldSpec(id, label, kind, true);
    }

    private static List<FieldSpec> fields(FieldSpec... values) {
''',
'''    private static FieldSpec field(String id, String label, FieldKind kind) {
        return new FieldSpec(id, label, kind, true);
    }

    private static FieldSpec choiceField(String id, String label, ChoiceOption... options) {
        List<ChoiceOption> values = new ArrayList<>();
        for (ChoiceOption option : options) values.add(option);
        return new FieldSpec(id, label, FieldKind.CHOICE, true, values);
    }

    private static ChoiceOption choice(String value, String label) {
        return new ChoiceOption(value, label);
    }

    private static List<FieldSpec> fields(FieldSpec... values) {
''', 'choice helpers')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# WorkflowSession: initialize/cycle/display CHOICE cells.
# ---------------------------------------------------------------------------
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSession.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        this.spec = spec;
        resize(rows, columns);
    }
''',
'''        this.spec = spec;
        resize(rows, columns);
        initializeChoiceDefaults();
    }
''', 'choice defaults ctor')
text = replace_once(text,
'''        public boolean complete() { return complete; }
        public String cell(int row, int column) {
            if (row < 0 || row >= rows || column < 0 || column >= columns) {
                throw new IndexOutOfBoundsException(row + "," + column);
            }
            return cells.get(row * columns + column);
        }
''',
'''        public boolean complete() { return complete; }
        public String cell(int row, int column) {
            if (row < 0 || row >= rows || column < 0 || column >= columns) {
                throw new IndexOutOfBoundsException(row + "," + column);
            }
            return cells.get(row * columns + column);
        }
        public CnCwWorkflowSpec.FieldSpec field(int row, int column) {
            if (spec.layout() != CnCwWorkflowSpec.InputLayout.FIXED_FIELDS
                    || row != 0 || column < 0 || column >= spec.fields().size()) return null;
            return spec.fields().get(column);
        }
        public boolean isChoiceCell(int row, int column) {
            CnCwWorkflowSpec.FieldSpec field = field(row, column);
            return field != null && field.kind() == CnCwWorkflowSpec.FieldKind.CHOICE;
        }
        public String displayCell(int row, int column) {
            CnCwWorkflowSpec.FieldSpec field = field(row, column);
            String raw = cell(row, column);
            return field == null ? raw : field.displayValue(raw);
        }
''', 'snapshot choice display')
text = replace_once(text,
'''    public void setSelectedCell(String value) {
        setCell(selectedRow, selectedColumn, value);
    }

    public boolean selectCell(int row, int column) {
''',
'''    public void setSelectedCell(String value) {
        setCell(selectedRow, selectedColumn, value);
    }

    public CnCwWorkflowSpec.FieldSpec selectedField() {
        return fieldAt(selectedRow, selectedColumn);
    }

    public boolean selectedIsChoice() {
        CnCwWorkflowSpec.FieldSpec field = selectedField();
        return field != null && field.kind() == CnCwWorkflowSpec.FieldKind.CHOICE;
    }

    public String selectedDisplayCell() {
        CnCwWorkflowSpec.FieldSpec field = selectedField();
        return field == null ? selectedCell() : field.displayValue(selectedCell());
    }

    public boolean cycleSelectedChoice(int delta) {
        CnCwWorkflowSpec.FieldSpec field = selectedField();
        if (field == null || field.kind() != CnCwWorkflowSpec.FieldKind.CHOICE) return false;
        List<CnCwWorkflowSpec.ChoiceOption> choices = field.choices();
        String current = selectedCell();
        int index = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).value().equals(current)) { index = i; break; }
        }
        int next = Math.floorMod(index + delta, choices.size());
        setSelectedCell(choices.get(next).value());
        return next != index;
    }

    public boolean resetSelectedChoice() {
        CnCwWorkflowSpec.FieldSpec field = selectedField();
        if (field == null || field.kind() != CnCwWorkflowSpec.FieldKind.CHOICE) return false;
        setSelectedCell(field.defaultValue());
        return true;
    }

    public boolean selectCell(int row, int column) {
''', 'choice selection API')
insert_marker = '''    private boolean isSimultaneous() {
'''
helpers = '''    private CnCwWorkflowSpec.FieldSpec fieldAt(int row, int column) {
        if (spec.layout() != CnCwWorkflowSpec.InputLayout.FIXED_FIELDS
                || row != 0 || column < 0 || column >= spec.fields().size()) return null;
        return spec.fields().get(column);
    }

    private void initializeChoiceDefaults() {
        if (spec.layout() != CnCwWorkflowSpec.InputLayout.FIXED_FIELDS) return;
        for (int column = 0; column < Math.min(columns, spec.fields().size()); column++) {
            CnCwWorkflowSpec.FieldSpec field = spec.fields().get(column);
            if (field.kind() == CnCwWorkflowSpec.FieldKind.CHOICE
                    && com.codex.fx991.core.Compat.isBlank(cell(0, column))) {
                setCell(0, column, field.defaultValue());
            }
        }
    }

'''
if insert_marker not in text: raise SystemExit('session helper marker not found')
text = text.replace(insert_marker, helpers + insert_marker, 1)
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Machine: choice cells consume directions; never expose numeric relation code.
# ---------------------------------------------------------------------------
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')
route_marker = '''        if (shiftArmed && (key == CnCwKey.UP || key == CnCwKey.DOWN
                || key == CnCwKey.LEFT || key == CnCwKey.RIGHT)) {
'''
choice_route = '''        if (workflowSession.selectedIsChoice() && !shiftArmed) {
            if (key == CnCwKey.LEFT || key == CnCwKey.UP
                    || key == CnCwKey.RIGHT || key == CnCwKey.DOWN) {
                int delta = (key == CnCwKey.LEFT || key == CnCwKey.UP) ? -1 : 1;
                workflowSession.cycleSelectedChoice(delta);
                tokens.clear();
                cursor = 0;
                semanticCursorOverride = null;
                status = workflowStatus();
                return true;
            }
            if (key == CnCwKey.DEL) {
                workflowSession.resetSelectedChoice();
                tokens.clear();
                cursor = 0;
                semanticCursorOverride = null;
                status = workflowStatus();
                return true;
            }
            if (isEntryKey(key)) {
                status = "用方向键选择 · " + workflowStatus();
                return true;
            }
        }

'''
if route_marker not in text: raise SystemExit('machine choice route marker not found')
text = text.replace(route_marker, choice_route + route_marker, 1)
text = replace_once(text,
'''    private void commitWorkflowCell() {
        if (workflowSession == null || resultShown) return;
        String source = evaluationSource().replace("\\u2063", "");
        workflowSession.setSelectedCell(source);
    }
''',
'''    private void commitWorkflowCell() {
        if (workflowSession == null || resultShown || workflowSession.selectedIsChoice()) return;
        String source = evaluationSource().replace("\\u2063", "");
        workflowSession.setSelectedCell(source);
    }
''', 'skip choice commit')
text = replace_once(text,
'''        String source = workflowSession.selectedCell();
        if (!com.codex.fx991.core.Compat.isBlank(source)) {
            List<Token> imported = parsePastedTokens(source);
            if (imported != null) tokens.addAll(imported);
        }
''',
'''        String source = workflowSession.selectedCell();
        if (!workflowSession.selectedIsChoice() && !com.codex.fx991.core.Compat.isBlank(source)) {
            List<Token> imported = parsePastedTokens(source);
            if (imported != null) tokens.addAll(imported);
        }
''', 'skip choice token load')
text = replace_once(text,
'''        return workflowSession.spec().title() + " · "
                + (workflowSession.selectedRow() + 1) + ","
                + (workflowSession.selectedColumn() + 1);
''',
'''        CnCwWorkflowSpec.FieldSpec field = workflowSession.selectedField();
        String fieldLabel = field == null ? ""
                : " · " + field.label()
                + (workflowSession.selectedIsChoice()
                ? "=" + workflowSession.selectedDisplayCell() : "");
        return workflowSession.spec().title() + fieldLabel + " · "
                + (workflowSession.selectedRow() + 1) + ","
                + (workflowSession.selectedColumn() + 1);
''', 'workflow status choice')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Android: show choice label even for selected cell, never raw evaluator code.
# ---------------------------------------------------------------------------
p = root / 'app/src/main/java/com/codex/fx991smooth/CalculatorView.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''                String value = selected ? cleanClipboardText(state.displayText())
                        : input.cell(row, column);
''',
'''                String value = input.isChoiceCell(row, column)
                        ? input.displayCell(row, column)
                        : selected ? cleanClipboardText(state.displayText())
                        : input.cell(row, column);
''', 'choice cell rendering')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Tests: spec/default/cycle/serialization/evaluation.
# ---------------------------------------------------------------------------
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwWorkflowSpecSuite.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        functionTableSpecs();
        equationSpecs();
''',
'''        functionTableSpecs();
        inequalitySpecs();
        equationSpecs();
''', 'test run specs')
text = replace_once(text,
'''        functionTableSessions();
        equationSessions();
''',
'''        functionTableSessions();
        inequalitySessions();
        equationSessions();
''', 'test run sessions')
marker = '''    private void equationSpecs() {
'''
method = '''    private void inequalitySpecs() {
        var quadratic = CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "quadratic");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, quadratic.layout(),
                "quadratic inequality fixed fields");
        equal(4, quadratic.fields().size(), "quadratic relation plus three coefficients");
        equal(CnCwWorkflowSpec.FieldKind.CHOICE, quadratic.fields().get(0).kind(),
                "inequality relation is a choice");
        equal(4, quadratic.fields().get(0).choices().size(), "four inequality relations");
        equal(">", quadratic.fields().get(0).displayValue("1"), "relation 1 display");
        equal("≤", quadratic.fields().get(0).displayValue("4"), "relation 4 display");

        var cubic = CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "cubic");
        equal(5, cubic.fields().size(), "cubic relation plus four coefficients");
        var quartic = CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "quartic");
        equal(6, quartic.fields().size(), "quartic relation plus five coefficients");
        equal("x^4", quartic.fields().get(1).label(), "quartic leading coefficient label");
    }

'''
if marker not in text: raise SystemExit('ineq spec marker not found')
text = text.replace(marker, method + marker, 1)
marker = '''    private void equationSessions() {
'''
method = '''    private void inequalitySessions() {
        var quadratic = CnCwWorkflowSession.create(
                CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "quadratic"));
        equal("1", quadratic.cell(0, 0), "inequality defaults to greater relation code");
        equal(">", quadratic.snapshot().displayCell(0, 0), "inequality displays relation symbol");
        check(quadratic.selectedIsChoice(), "relation cell is a choice");
        check(quadratic.cycleSelectedChoice(1), "relation cycles right");
        equal("2", quadratic.cell(0, 0), "second relation serializes as code 2");
        equal("<", quadratic.selectedDisplayCell(), "second relation displays less-than");
        quadratic.cycleSelectedChoice(1);
        quadratic.cycleSelectedChoice(1);
        equal("4", quadratic.cell(0, 0), "fourth relation code");
        quadratic.cycleSelectedChoice(1);
        equal("1", quadratic.cell(0, 0), "choice cycles around");
        quadratic.setCell(0, 1, "1");
        quadratic.setCell(0, 2, "0");
        quadratic.setCell(0, 3, "-1");
        equal("1,1,0,-1", quadratic.legacySource(),
                "inequality serializes relation code then coefficients");
        check(quadratic.evaluate(context).display().contains("x"),
                "inequality session delegates to polynomial inequality engine");
    }

'''
if marker not in text: raise SystemExit('ineq session marker not found')
text = text.replace(marker, method + marker, 1)
text = replace_once(text,
'''        check(CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "missing") == null,
                "unknown function-table command is rejected");
''',
'''        check(CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "missing") == null,
                "unknown function-table command is rejected");
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "missing") == null,
                "unknown inequality command is rejected");
''', 'unknown inequality test')
p.write_text(text, encoding='utf-8')

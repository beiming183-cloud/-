from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_once(text, old, new, name):
    if old not in text:
        raise SystemExit(f"target not found: {name}")
    return text.replace(old, new, 1)


# WorkflowSpec: label the three known values for the two ratio commands.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSpec.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''            case INEQUALITY -> inequality(commandId);
            case MATRIX -> matrix(commandId);
            case VECTOR -> vector(commandId);
            default -> null;
''',
'''            case INEQUALITY -> inequality(commandId);
            case MATRIX -> matrix(commandId);
            case VECTOR -> vector(commandId);
            case RATIO -> ratio(commandId);
            default -> null;
''', 'ratio route')
marker = '''    private static WorkflowSpec spec(ApplicationMode mode, String commandId, String title,
'''
ratio = '''    private static WorkflowSpec ratio(String commandId) {
        if (commandId.equals("a:b=x:d")) {
            return spec(ApplicationMode.RATIO, commandId, "A:B=X:D",
                    InputLayout.FIXED_FIELDS,
                    fields(field("a", "A", FieldKind.EXPRESSION),
                            field("b", "B", FieldKind.EXPRESSION),
                            field("d", "D", FieldKind.EXPRESSION)),
                    1, 1, 3, 3);
        }
        if (commandId.equals("a:b=c:x")) {
            return spec(ApplicationMode.RATIO, commandId, "A:B=C:X",
                    InputLayout.FIXED_FIELDS,
                    fields(field("a", "A", FieldKind.EXPRESSION),
                            field("b", "B", FieldKind.EXPRESSION),
                            field("c", "C", FieldKind.EXPRESSION)),
                    1, 1, 3, 3);
        }
        return null;
    }

'''
if marker not in text:
    raise SystemExit('ratio spec marker not found')
text = text.replace(marker, ratio + marker, 1)
p.write_text(text, encoding='utf-8')


# ModeEngine: ratio result joins the core-owned structured result protocol.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwModeEngine.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        double answer = command.equals("a:b=x:d")
                ? RatioEngine.solveAtoBEqualsXtoD(values[0], values[1], values[2])
                : RatioEngine.solveAtoBEqualsCtoX(values[0], values[1], values[2]);
        return new ModeResult("X=" + format(answer), answer);
''',
'''        double answer = command.equals("a:b=x:d")
                ? RatioEngine.solveAtoBEqualsXtoD(values[0], values[1], values[2])
                : RatioEngine.solveAtoBEqualsCtoX(values[0], values[1], values[2]);
        return ModeResult.keyValue("比例", "X=" + format(answer), answer,
                item("X", answer));
''', 'structured ratio result')
p.write_text(text, encoding='utf-8')


# Regression: labels, serialization, engine delegation and structured result.
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwWorkflowSpecSuite.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        inequalitySpecs();
        equationSpecs();
''',
'''        inequalitySpecs();
        ratioSpecsAndSessions();
        equationSpecs();
''', 'ratio tests in run')
marker = '''    private void equationSpecs() {
'''
method = '''    private void ratioSpecsAndSessions() {
        var xdSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.RATIO, "a:b=x:d");
        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, xdSpec.layout(),
                "A:B=X:D uses fixed fields");
        equal(3, xdSpec.fields().size(), "A:B=X:D has three known values");
        equal("A", xdSpec.fields().get(0).label(), "ratio A label");
        equal("B", xdSpec.fields().get(1).label(), "ratio B label");
        equal("D", xdSpec.fields().get(2).label(), "ratio D label");
        var xd = CnCwWorkflowSession.create(xdSpec);
        xd.setCell(0, 0, "2");
        xd.setCell(0, 1, "4");
        xd.setCell(0, 2, "10");
        equal("2,4,10", xd.legacySource(), "A:B=X:D serialization");
        var xdResult = xd.evaluate(context);
        near(5.0, xdResult.primaryValue(), 0.0, "A:B=X:D delegates to ratio engine");
        equal(com.codex.fx991.core.cw.CnCwModeEngine.ResultLayout.KEY_VALUE,
                xdResult.layout(), "ratio result is structured key-value");
        equal("X", xdResult.items().get(0).label(), "ratio result X label");

        var cxSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.RATIO, "a:b=c:x");
        equal("C", cxSpec.fields().get(2).label(), "A:B=C:X uses C as third known value");
        var cx = CnCwWorkflowSession.create(cxSpec);
        cx.setCell(0, 0, "2");
        cx.setCell(0, 1, "4");
        cx.setCell(0, 2, "3");
        near(6.0, cx.evaluate(context).primaryValue(), 0.0,
                "A:B=C:X delegates to ratio engine");
    }

'''
if marker not in text:
    raise SystemExit('ratio test marker not found')
text = text.replace(marker, method + marker, 1)
text = replace_once(text,
'''        check(CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "missing") == null,
                "unknown inequality command is rejected");
''',
'''        check(CnCwWorkflowSpec.forCommand(ApplicationMode.INEQUALITY, "missing") == null,
                "unknown inequality command is rejected");
        check(CnCwWorkflowSpec.forCommand(ApplicationMode.RATIO, "missing") == null,
                "unknown ratio command is rejected");
''', 'unknown ratio test')
p.write_text(text, encoding='utf-8')

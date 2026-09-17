from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"target not found: {label}")
    return text.replace(old, new, 1)

# WorkflowSpec: Function Table now uses the Stage 5 fixed-field workflow session.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSpec.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''            case STATISTICS -> statistics(commandId);\n            case EQUATION -> equation(commandId);\n''',
'''            case STATISTICS -> statistics(commandId);\n            case FUNCTION_TABLE -> functionTable(commandId);\n            case EQUATION -> equation(commandId);\n''', 'function-table switch')
insert = '''\n    private static WorkflowSpec functionTable(String commandId) {\n        if (commandId.equals("f")) {\n            return spec(ApplicationMode.FUNCTION_TABLE, commandId, "函数表 f(x)",\n                    InputLayout.FIXED_FIELDS,\n                    fields(field("f", "f(x)", FieldKind.EXPRESSION),\n                            field("start", "开始", FieldKind.EXPRESSION),\n                            field("end", "结束", FieldKind.EXPRESSION),\n                            field("step", "步长", FieldKind.EXPRESSION)),\n                    1, 1, 4, 4);\n        }\n        if (commandId.equals("fg")) {\n            return spec(ApplicationMode.FUNCTION_TABLE, commandId, "函数表 f(x), g(x)",\n                    InputLayout.FIXED_FIELDS,\n                    fields(field("f", "f(x)", FieldKind.EXPRESSION),\n                            field("g", "g(x)", FieldKind.EXPRESSION),\n                            field("start", "开始", FieldKind.EXPRESSION),\n                            field("end", "结束", FieldKind.EXPRESSION),\n                            field("step", "步长", FieldKind.EXPRESSION)),\n                    1, 1, 5, 5);\n        }\n        return null;\n    }\n\n'''
text = replace_once(text, '    private static WorkflowSpec equation(String commandId) {\n',
                    insert + '    private static WorkflowSpec equation(String commandId) {\n',
                    'function-table spec method')
p.write_text(text, encoding='utf-8')

# ModeEngine: return the entire generated table as core-owned row-major cells.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwModeEngine.java'
text = p.read_text(encoding='utf-8')
start = text.index('    private static ModeResult functionTable(')
end = text.index('    private static ModeResult equation(', start)
method = '''    private static ModeResult functionTable(String command,\n                                            List<String> fields,\n                                            ScalarExpressionEngine.EvaluationContext context) {\n        boolean twoFunctions = command.equals("fg");\n        int required = twoFunctions ? 5 : 4;\n        if (fields.size() != required) {\n            throw new IllegalArgumentException(twoFunctions\n                    ? "Enter f(x),g(x),start,end,step" : "Enter f(x),start,end,step");\n        }\n        ScalarExpressionEngine.CompiledExpression f = ScalarExpressionEngine.compile(fields.get(0));\n        ScalarExpressionEngine.CompiledExpression g = twoFunctions\n                ? ScalarExpressionEngine.compile(fields.get(1)) : null;\n        int offset = twoFunctions ? 2 : 1;\n        double startValue = scalar(fields.get(offset), context);\n        double endValue = scalar(fields.get(offset + 1), context);\n        double step = scalar(fields.get(offset + 2), context);\n        List<FunctionTableEngine.Row> rows = FunctionTableEngine.generate(f, g,\n                twoFunctions ? FunctionTableEngine.TableType.F_AND_G\n                        : FunctionTableEngine.TableType.F_ONLY,\n                startValue, endValue, step, context);\n        FunctionTableEngine.Row first = rows.get(0);\n        FunctionTableEngine.Row last = rows.get(rows.size() - 1);\n        String firstText = format(first.x()) + ":" + format(first.f());\n        String lastText = format(last.x()) + ":" + format(last.f());\n        if (twoFunctions) {\n            firstText += "," + format(first.g());\n            lastText += "," + format(last.g());\n        }\n        String display = "rows=" + rows.size() + "  " + firstText + "\\n… " + lastText;\n        List<String> cells = new ArrayList<>();\n        for (FunctionTableEngine.Row row : rows) {\n            cells.add(format(row.x()));\n            cells.add(format(row.f()));\n            if (twoFunctions) cells.add(format(row.g()));\n        }\n        List<ResultItem> headings = new ArrayList<>();\n        headings.add(item("x", "x"));\n        headings.add(item("f(x)", "f(x)"));\n        if (twoFunctions) headings.add(item("g(x)", "g(x)"));\n        return ModeResult.grid(ResultLayout.TABLE, twoFunctions ? "函数表 f,g" : "函数表 f",\n                display, first.f(), rows.size(), twoFunctions ? 3 : 2, cells, headings);\n    }\n\n'''
text = text[:start] + method + text[end:]
p.write_text(text, encoding='utf-8')

# Regression: spec/session shape and full TABLE payload.
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwWorkflowSpecSuite.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        statisticsSpecs();\n        equationSpecs();\n''',
'''        statisticsSpecs();\n        functionTableSpecsAndSessions();\n        equationSpecs();\n''', 'suite run')
method = '''\n    private void functionTableSpecsAndSessions() {\n        var fSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "f");\n        equal(CnCwWorkflowSpec.InputLayout.FIXED_FIELDS, fSpec.layout(),\n                "function-table f uses fixed fields");\n        equal(4, fSpec.fields().size(), "function-table f field count");\n        equal("步长", fSpec.fields().get(3).label(), "function-table step label");\n        var f = CnCwWorkflowSession.create(fSpec);\n        f.setCell(0, 0, "x^2");\n        f.setCell(0, 1, "0");\n        f.setCell(0, 2, "2");\n        f.setCell(0, 3, "1");\n        var fResult = f.evaluate(context);\n        equal(com.codex.fx991.core.cw.CnCwModeEngine.ResultLayout.TABLE, fResult.layout(),\n                "function-table f returns TABLE layout");\n        equal(3, fResult.rows(), "function-table f row count");\n        equal(2, fResult.columns(), "function-table f column count");\n        equal(6, fResult.cells().size(), "function-table f complete cell payload");\n        equal("4", fResult.cells().get(5), "function-table f last value");\n\n        var fgSpec = CnCwWorkflowSpec.forCommand(ApplicationMode.FUNCTION_TABLE, "fg");\n        equal(5, fgSpec.fields().size(), "function-table fg field count");\n        var fg = CnCwWorkflowSession.create(fgSpec);\n        fg.setCell(0, 0, "x");\n        fg.setCell(0, 1, "x+10");\n        fg.setCell(0, 2, "1");\n        fg.setCell(0, 3, "2");\n        fg.setCell(0, 4, "1");\n        var fgResult = fg.evaluate(context);\n        equal(2, fgResult.rows(), "function-table fg row count");\n        equal(3, fgResult.columns(), "function-table fg column count");\n        equal("12", fgResult.cells().get(5), "function-table fg last g value");\n    }\n\n'''
text = replace_once(text, '    private void equationSpecs() {\n', method + '    private void equationSpecs() {\n',
                    'function-table regression')
p.write_text(text, encoding='utf-8')

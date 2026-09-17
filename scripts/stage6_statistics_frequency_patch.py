from pathlib import Path


# --- Statistics menu: append frequency-backed variants without moving existing commands. ---
machine = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java")
text = machine.read_text(encoding="utf-8")
old = '''                    command("reg-ab-exponential", "ab^x 回归", "y=a·b^x"),
                    command("reg-power", "幂回归", "y=a·x^b"),
                    command("reg-inverse", "逆数回归", "y=a+b/x"));'''
new = '''                    command("reg-ab-exponential", "ab^x 回归", "y=a·b^x"),
                    command("reg-power", "幂回归", "y=a·x^b"),
                    command("reg-inverse", "逆数回归", "y=a+b/x"),
                    command("one-freq", "单变量（频数）", "x / 频数"),
                    command("two-freq", "双变量（频数）", "x / y / 频数"),
                    command("reg-linear-freq", "线性回归（频数）", "x / y / 频数"),
                    command("reg-quadratic-freq", "二次回归（频数）", "x / y / 频数"),
                    command("reg-logarithmic-freq", "对数回归（频数）", "x / y / 频数"),
                    command("reg-e-exponential-freq", "e 指数回归（频数）", "x / y / 频数"),
                    command("reg-ab-exponential-freq", "ab^x 回归（频数）", "x / y / 频数"),
                    command("reg-power-freq", "幂回归（频数）", "x / y / 频数"),
                    command("reg-inverse-freq", "逆数回归（频数）", "x / y / 频数"));'''
if old not in text:
    raise RuntimeError("statistics menu tail not found")
text = text.replace(old, new, 1)

old = '''        spreadsheet.clearAll();
        undoTokens = null;'''
new = '''        spreadsheet.clearAll();
        linearAlgebraMemory.clear();
        undoTokens = null;'''
if old not in text:
    raise RuntimeError("reset memory marker not found")
text = text.replace(old, new, 1)
machine.write_text(text, encoding="utf-8")


# --- Linear-algebra memory must obey the machine's all-reset semantics. ---
memory = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwLinearAlgebraMemory.java")
text = memory.read_text(encoding="utf-8")
marker = '''    public void copyFrom(CnCwLinearAlgebraMemory source) {
        matrices.clear();
        matrices.putAll(source.matrices);
        vectors.clear();
        vectors.putAll(source.vectors);
    }
'''
replacement = marker + '''
    public void clear() {
        matrices.clear();
        vectors.clear();
    }
'''
if marker not in text:
    raise RuntimeError("linear algebra copyFrom marker not found")
text = text.replace(marker, replacement, 1)
memory.write_text(text, encoding="utf-8")


# --- Paired-series editors use their field count; this enables x/y/freq without a new renderer. ---
session = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSession.java")
text = session.read_text(encoding="utf-8")
old = '''            case PAIRED_SERIES -> {
                rows = spec.minRows();
                columns = 2;
            }'''
new = '''            case PAIRED_SERIES -> {
                rows = spec.minRows();
                columns = spec.fields().size();
            }'''
if old not in text:
    raise RuntimeError("paired series create block not found")
session.write_text(text.replace(old, new, 1), encoding="utf-8")


# --- Workflow specs for x/freq and x/y/freq variants. ---
spec = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSpec.java")
text = spec.read_text(encoding="utf-8")
start = text.index("    private static WorkflowSpec statistics(String commandId) {")
end = text.index("    private static WorkflowSpec functionTable(String commandId) {", start)
method = '''    private static WorkflowSpec statistics(String commandId) {
        if (commandId.equals("one")) {
            return spec(ApplicationMode.STATISTICS, commandId, "一元统计",
                    InputLayout.SERIES, fields(field("x", "x", FieldKind.EXPRESSION)),
                    1, 999, 1, 1);
        }
        if (commandId.equals("one-freq")) {
            return spec(ApplicationMode.STATISTICS, commandId, "一元统计（频数）",
                    InputLayout.PAIRED_SERIES,
                    fields(field("x", "x", FieldKind.EXPRESSION),
                            field("frequency", "频数", FieldKind.EXPRESSION)),
                    1, 999, 2, 2);
        }
        if (commandId.equals("two-freq")) {
            return spec(ApplicationMode.STATISTICS, commandId, "双变量统计（频数）",
                    InputLayout.PAIRED_SERIES,
                    fields(field("x", "x", FieldKind.EXPRESSION),
                            field("y", "y", FieldKind.EXPRESSION),
                            field("frequency", "频数", FieldKind.EXPRESSION)),
                    2, 999, 3, 3);
        }
        boolean frequency = commandId.endsWith("-freq");
        String baseCommand = frequency
                ? commandId.substring(0, commandId.length() - "-freq".length()) : commandId;
        boolean regression = baseCommand.equals("regression") || baseCommand.startsWith("reg-");
        if (commandId.equals("two") || regression) {
            int minRows = baseCommand.equals("reg-quadratic") ? 3 : 2;
            List<FieldSpec> fields = new ArrayList<>();
            fields.add(field("x", "x", FieldKind.EXPRESSION));
            fields.add(field("y", "y", FieldKind.EXPRESSION));
            if (frequency) fields.add(field("frequency", "频数", FieldKind.EXPRESSION));
            return spec(ApplicationMode.STATISTICS, commandId,
                    commandId.equals("two") ? "双变量统计"
                            : regressionTitle(baseCommand) + (frequency ? "（频数）" : ""),
                    InputLayout.PAIRED_SERIES, fields,
                    minRows, 999, fields.size(), fields.size());
        }
        return null;
    }

    private static String regressionTitle(String commandId) {
        return switch (commandId) {
            case "reg-quadratic" -> "二次回归";
            case "reg-logarithmic" -> "对数回归";
            case "reg-e-exponential" -> "e 指数回归";
            case "reg-ab-exponential" -> "ab^x 回归";
            case "reg-power" -> "幂回归";
            case "reg-inverse" -> "逆数回归";
            default -> "线性回归";
        };
    }

'''
spec.write_text(text[:start] + method + text[end:], encoding="utf-8")


# --- Mode engine: consume weighted rows for one/two-variable stats and all regressions. ---
engine = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwModeEngine.java")
text = engine.read_text(encoding="utf-8")
start = text.index("    private static ModeResult statistics(String command,")
end = text.index("    private static String regressionTitle(StatisticsEngine.RegressionType type) {", start)
method = '''    private static ModeResult statistics(String command,
                                         List<String> fields,
                                         ScalarExpressionEngine.EvaluationContext context) {
        double[] values = evaluateFields(fields, 0, context);
        if (command.equals("one") || command.equals("one-freq")) {
            double[] x;
            double[] frequency = null;
            if (command.equals("one-freq")) {
                if (values.length < 2 || values.length % 2 != 0) {
                    throw new IllegalArgumentException("Enter x1,f1,x2,f2,...");
                }
                x = new double[values.length / 2];
                frequency = new double[x.length];
                for (int i = 0; i < x.length; i++) {
                    x[i] = values[i * 2];
                    frequency[i] = values[i * 2 + 1];
                }
            } else {
                x = values;
            }
            StatisticsEngine.OneVariableResults result = frequency == null
                    ? StatisticsEngine.oneVariable(x) : StatisticsEngine.oneVariable(x, frequency);
            String display = "n=" + format(result.n()) + "  x̄=" + format(result.mean())
                    + "\\nσx=" + format(result.populationStdDev())
                    + "  sx=" + format(result.sampleStdDev());
            return ModeResult.keyValue(command.equals("one-freq") ? "一元统计（频数）" : "一元统计",
                    display, result.mean(),
                    item("n", result.n()),
                    item("x̄", result.mean()),
                    item("σx", result.populationStdDev()),
                    item("sx", result.sampleStdDev()));
        }

        boolean frequencyMode = command.endsWith("-freq") || command.equals("two-freq");
        String baseCommand = command.endsWith("-freq")
                ? command.substring(0, command.length() - "-freq".length()) : command;
        int stride = frequencyMode ? 3 : 2;
        if (values.length < stride * 2 || values.length % stride != 0) {
            throw new IllegalArgumentException(frequencyMode
                    ? "Enter x1,y1,f1,x2,y2,f2,..." : "Enter x1,y1,x2,y2,...");
        }
        int rows = values.length / stride;
        double[] x = new double[rows];
        double[] y = new double[rows];
        double[] frequency = frequencyMode ? new double[rows] : null;
        for (int i = 0; i < rows; i++) {
            x[i] = values[i * stride];
            y[i] = values[i * stride + 1];
            if (frequencyMode) frequency[i] = values[i * stride + 2];
        }

        StatisticsEngine.RegressionType regressionType = regressionType(baseCommand);
        if (regressionType != null) {
            StatisticsEngine.RegressionResult fit = frequencyMode
                    ? StatisticsEngine.regression(regressionType, x, y, frequency)
                    : StatisticsEngine.regression(regressionType, x, y);
            String title = regressionTitle(regressionType) + (frequencyMode ? "（频数）" : "");
            if (regressionType == StatisticsEngine.RegressionType.QUADRATIC) {
                String display = "a=" + format(fit.a()) + "  b=" + format(fit.b())
                        + "\\nc=" + format(fit.c());
                return ModeResult.keyValue(title, display, fit.a(),
                        item("a", fit.a()), item("b", fit.b()), item("c", fit.c()));
            }
            String display = "a=" + format(fit.a()) + "  b=" + format(fit.b())
                    + "\\nr=" + format(fit.r());
            return ModeResult.keyValue(title, display, fit.r(),
                    item("a", fit.a()), item("b", fit.b()), item("r", fit.r()));
        }
        if (!baseCommand.equals("two")) throw new IllegalArgumentException("Unknown statistics command");
        StatisticsEngine.TwoVariableResults result = frequencyMode
                ? StatisticsEngine.twoVariable(x, y, frequency) : StatisticsEngine.twoVariable(x, y);
        String display = "x̄=" + format(result.meanX()) + "  ȳ=" + format(result.meanY())
                + "\\nσx=" + format(result.populationStdDevX())
                + "  σy=" + format(result.populationStdDevY());
        return ModeResult.keyValue(frequencyMode ? "双变量统计（频数）" : "双变量统计",
                display, result.meanX(),
                item("x̄", result.meanX()),
                item("ȳ", result.meanY()),
                item("σx", result.populationStdDevX()),
                item("σy", result.populationStdDevY()));
    }

    private static StatisticsEngine.RegressionType regressionType(String command) {
        return switch (command) {
            case "regression", "reg-linear" -> StatisticsEngine.RegressionType.LINEAR;
            case "reg-quadratic" -> StatisticsEngine.RegressionType.QUADRATIC;
            case "reg-logarithmic" -> StatisticsEngine.RegressionType.LOGARITHMIC;
            case "reg-e-exponential" -> StatisticsEngine.RegressionType.E_EXPONENTIAL;
            case "reg-ab-exponential" -> StatisticsEngine.RegressionType.AB_EXPONENTIAL;
            case "reg-power" -> StatisticsEngine.RegressionType.POWER;
            case "reg-inverse" -> StatisticsEngine.RegressionType.INVERSE;
            default -> null;
        };
    }

'''
engine.write_text(text[:start] + method + text[end:], encoding="utf-8")


# --- Functionality suite: weighted stats, frequency regression, and reset semantics. ---
suite = Path("core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java")
text = suite.read_text(encoding="utf-8")
old = '''        baseNOperationsReachUserWorkflows();
        System.out.println("PASS " + checks + " functionality checks");'''
new = '''        baseNOperationsReachUserWorkflows();
        statisticsFrequencyColumnsReachWeightedEngines();
        resetClearsStoredLinearAlgebra();
        System.out.println("PASS " + checks + " functionality checks");'''
if old not in text:
    raise RuntimeError("functionality run marker not found")
text = text.replace(old, new, 1)

marker = "    private void baseNOperationsReachUserWorkflows() {"
if marker not in text:
    raise RuntimeError("functionality Base-N marker not found")
methods = r'''    private void statisticsFrequencyColumnsReachWeightedEngines() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 1, 9); // one-variable with frequency
        equal(2, machine.state().workflowInput().columns(), "one-freq exposes x/frequency columns");
        machine.performWorkflowAction(CnCwWorkflowAction.Type.ADD_ROW);
        fillGrid(machine, "10", "2", "20", "1");
        equal("3", machine.state().applicationResult().items().get(0).value(),
                "weighted one-variable sample count is sum of frequencies");
        near(40.0 / 3.0, parse(machine, 1), 1e-10, "weighted one-variable mean");

        CnCwModeEngine.ModeResult two = CnCwModeEngine.evaluate(ApplicationMode.STATISTICS,
                "two-freq", "1,2,2,3,4,1", ScalarExpressionEngine.EvaluationContext.standard());
        near(5.0 / 3.0, Double.parseDouble(two.items().get(0).value()), 1e-10,
                "weighted two-variable mean x");

        String[] commands = {"reg-linear-freq", "reg-quadratic-freq", "reg-logarithmic-freq",
                "reg-e-exponential-freq", "reg-ab-exponential-freq",
                "reg-power-freq", "reg-inverse-freq"};
        String weighted = "1,2,2,2,4,1,3,8,1,4,16,1";
        for (String command : commands) {
            CnCwWorkflowSpec.WorkflowSpec spec = CnCwWorkflowSpec.forCommand(
                    ApplicationMode.STATISTICS, command);
            equal(3, spec.minColumns(), command + " exposes x/y/frequency columns");
            CnCwModeEngine.ModeResult result = CnCwModeEngine.evaluate(
                    ApplicationMode.STATISTICS, command, weighted,
                    ScalarExpressionEngine.EvaluationContext.standard());
            equal(CnCwModeEngine.ResultLayout.KEY_VALUE, result.layout(),
                    command + " returns structured weighted regression");
            check(result.title().contains("频数"), command + " is visibly frequency-aware");
        }
    }

    private void resetClearsStoredLinearAlgebra() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 7, 0);
        enter(machine, "5");
        machine.dispatch(CnCwKey.EXE);
        equal("MatA 已保存", machine.state().applicationResult().title(), "MatA stored before reset");
        machine.reset();
        openCommand(machine, 7, 5);
        machine.dispatch(CnCwKey.EXE);
        check(machine.state().calculationState().isError(), "reset clears MatA memory");
    }

'''
text = text.replace(marker, methods + marker, 1)
suite.write_text(text, encoding="utf-8")

from pathlib import Path


def require_replace(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"expected block not found: {path}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1. Statistics menu: expose every regression engine that already exists in core.
machine = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java")
text = machine.read_text(encoding="utf-8")
old = '''            case STATISTICS -> com.codex.fx991.core.Compat.list(command("one", "单变量", "x / 频数"),
                    command("two", "双变量", "x / y / 频数"),
                    command("regression", "回归", "七类回归模型"));'''
new = '''            case STATISTICS -> com.codex.fx991.core.Compat.list(
                    command("one", "单变量", "x 数据"),
                    command("two", "双变量", "x / y 数据"),
                    command("reg-linear", "线性回归", "y=a·x+b"),
                    command("reg-quadratic", "二次回归", "y=a·x²+b·x+c"),
                    command("reg-logarithmic", "对数回归", "y=a+b·ln(x)"),
                    command("reg-e-exponential", "e 指数回归", "y=a·e^(b·x)"),
                    command("reg-ab-exponential", "ab^x 回归", "y=a·b^x"),
                    command("reg-power", "幂回归", "y=a·x^b"),
                    command("reg-inverse", "逆数回归", "y=a+b/x"));'''
if old not in text:
    raise RuntimeError("statistics command block not found")
machine.write_text(text.replace(old, new, 1), encoding="utf-8")


# 2. All regression commands reuse the structured x/y series editor.
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
        boolean regression = commandId.equals("regression") || commandId.startsWith("reg-");
        if (commandId.equals("two") || regression) {
            int minRows = commandId.equals("reg-quadratic") ? 3 : 2;
            return spec(ApplicationMode.STATISTICS, commandId,
                    commandId.equals("two") ? "双变量统计" : regressionTitle(commandId),
                    InputLayout.PAIRED_SERIES,
                    fields(field("x", "x", FieldKind.EXPRESSION),
                            field("y", "y", FieldKind.EXPRESSION)),
                    minRows, 999, 2, 2);
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


# 3. Route each public command to its matching StatisticsEngine regression family.
engine = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwModeEngine.java")
text = engine.read_text(encoding="utf-8")
start = text.index("    private static ModeResult statistics(String command,")
end = text.index("    private static ModeResult distribution(String command,", start)
method = '''    private static ModeResult statistics(String command,
                                         List<String> fields,
                                         ScalarExpressionEngine.EvaluationContext context) {
        double[] values = evaluateFields(fields, 0, context);
        if (command.equals("one")) {
            StatisticsEngine.OneVariableResults result = StatisticsEngine.oneVariable(values);
            String display = "n=" + format(result.n()) + "  x̄=" + format(result.mean())
                    + "\\nσx=" + format(result.populationStdDev())
                    + "  sx=" + format(result.sampleStdDev());
            return ModeResult.keyValue("一元统计", display, result.mean(),
                    item("n", result.n()),
                    item("x̄", result.mean()),
                    item("σx", result.populationStdDev()),
                    item("sx", result.sampleStdDev()));
        }
        if (values.length < 4 || values.length % 2 != 0) {
            throw new IllegalArgumentException("Enter x1,y1,x2,y2,...");
        }
        double[] x = new double[values.length / 2];
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            x[i] = values[i * 2];
            y[i] = values[i * 2 + 1];
        }
        StatisticsEngine.RegressionType regressionType = regressionType(command);
        if (regressionType != null) {
            StatisticsEngine.RegressionResult fit = StatisticsEngine.regression(
                    regressionType, x, y);
            if (regressionType == StatisticsEngine.RegressionType.QUADRATIC) {
                String display = "a=" + format(fit.a()) + "  b=" + format(fit.b())
                        + "\\nc=" + format(fit.c());
                return ModeResult.keyValue(regressionTitle(regressionType), display, fit.a(),
                        item("a", fit.a()), item("b", fit.b()), item("c", fit.c()));
            }
            String display = "a=" + format(fit.a()) + "  b=" + format(fit.b())
                    + "\\nr=" + format(fit.r());
            return ModeResult.keyValue(regressionTitle(regressionType), display, fit.r(),
                    item("a", fit.a()), item("b", fit.b()), item("r", fit.r()));
        }
        if (!command.equals("two")) throw new IllegalArgumentException("Unknown statistics command");
        StatisticsEngine.TwoVariableResults result = StatisticsEngine.twoVariable(x, y);
        String display = "x̄=" + format(result.meanX()) + "  ȳ=" + format(result.meanY())
                + "\\nσx=" + format(result.populationStdDevX())
                + "  σy=" + format(result.populationStdDevY());
        return ModeResult.keyValue("双变量统计", display, result.meanX(),
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

    private static String regressionTitle(StatisticsEngine.RegressionType type) {
        return switch (type) {
            case LINEAR -> "线性回归";
            case QUADRATIC -> "二次回归";
            case LOGARITHMIC -> "对数回归";
            case E_EXPONENTIAL -> "e 指数回归";
            case AB_EXPONENTIAL -> "ab^x 回归";
            case POWER -> "幂回归";
            case INVERSE -> "逆数回归";
        };
    }

'''
engine.write_text(text[:start] + method + text[end:], encoding="utf-8")


# 4. Add a permanent functionality-first suite. It will be extended by later batches.
suite = Path("core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java")
suite.write_text(r'''package com.codex.fx991.core;

import com.codex.fx991.core.cw.CnCwCommand;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.mode.ApplicationMode;
import com.codex.fx991.core.mode.CnCwModel;

import java.util.List;

/** Functionality-first guards for user-visible commands that used to be partial. */
public final class CnCwFunctionalitySuite {
    private int checks;

    public static void main(String[] args) {
        new CnCwFunctionalitySuite().run();
    }

    private void run() {
        statisticsMenuExposesAllRegressionFamilies();
        allRegressionFamiliesReachTheirEngines();
        quadraticRegressionRunsThroughMachineWorkflow();
        System.out.println("PASS " + checks + " functionality checks");
    }

    private void statisticsMenuExposesAllRegressionFamilies() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        List<CnCwCommand> commands = machine.state().modeCommands();
        equal(9, commands.size(), "statistics command count");
        String[] expected = {"reg-linear", "reg-quadratic", "reg-logarithmic",
                "reg-e-exponential", "reg-ab-exponential", "reg-power", "reg-inverse"};
        for (int index = 0; index < expected.length; index++) {
            equal(expected[index], commands.get(index + 2).id(),
                    "statistics regression command " + index);
            CnCwWorkflowSpec.WorkflowSpec spec = CnCwWorkflowSpec.forCommand(
                    ApplicationMode.STATISTICS, expected[index]);
            check(spec != null, expected[index] + " owns a structured x/y workflow");
            equal(2, spec.minColumns(), expected[index] + " x/y columns");
        }
        equal(3, CnCwWorkflowSpec.forCommand(ApplicationMode.STATISTICS,
                "reg-quadratic").minRows(), "quadratic starts with enough rows to solve");
    }

    private void allRegressionFamiliesReachTheirEngines() {
        String[] commands = {"reg-linear", "reg-quadratic", "reg-logarithmic",
                "reg-e-exponential", "reg-ab-exponential", "reg-power", "reg-inverse"};
        String[] titles = {"线性回归", "二次回归", "对数回归", "e 指数回归",
                "ab^x 回归", "幂回归", "逆数回归"};
        String data = "1,2,2,4,3,8,4,16";
        for (int index = 0; index < commands.length; index++) {
            CnCwModeEngine.ModeResult result = CnCwModeEngine.evaluate(
                    ApplicationMode.STATISTICS, commands[index], data,
                    ScalarExpressionEngine.EvaluationContext.standard());
            equal(CnCwModeEngine.ResultLayout.KEY_VALUE, result.layout(),
                    commands[index] + " structured result");
            equal(titles[index], result.title(), commands[index] + " result title");
            equal(3, result.items().size(), commands[index] + " coefficient item count");
            check(!result.display().isBlank(), commands[index] + " visible result");
        }
        equal("线性回归", CnCwModeEngine.evaluate(ApplicationMode.STATISTICS,
                "regression", data, ScalarExpressionEngine.EvaluationContext.standard()).title(),
                "legacy regression alias");
    }

    private void quadraticRegressionRunsThroughMachineWorkflow() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        for (int i = 0; i < 3; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK);
        check(machine.state().hasWorkflowInput(), "quadratic regression opens x/y editor");
        equal(3, machine.state().workflowInput().rows(),
                "quadratic regression begins with three data rows");

        enter(machine, "1"); machine.dispatch(CnCwKey.OK);
        enter(machine, "3"); machine.dispatch(CnCwKey.OK);
        enter(machine, "2"); machine.dispatch(CnCwKey.OK);
        enter(machine, "7"); machine.dispatch(CnCwKey.OK);
        enter(machine, "3"); machine.dispatch(CnCwKey.OK);
        enter(machine, "13"); machine.dispatch(CnCwKey.EXE);

        check(machine.state().resultShown(), "quadratic regression reaches result");
        equal("二次回归", machine.state().applicationResult().title(),
                "quadratic regression result type survives Machine path");
        near(1.0, parse(machine, 0), 1e-10, "quadratic a coefficient");
        near(1.0, parse(machine, 1), 1e-10, "quadratic b coefficient");
        near(1.0, parse(machine, 2), 1e-10, "quadratic c coefficient");
    }

    private static double parse(CnCwMachine machine, int index) {
        return Double.parseDouble(machine.state().applicationResult().items().get(index).value()
                .replace('−', '-'));
    }

    private static void enter(CnCwMachine machine, String digits) {
        for (int i = 0; i < digits.length(); i++) {
            machine.dispatch(switch (digits.charAt(i)) {
                case '0' -> CnCwKey.DIGIT_0;
                case '1' -> CnCwKey.DIGIT_1;
                case '2' -> CnCwKey.DIGIT_2;
                case '3' -> CnCwKey.DIGIT_3;
                case '4' -> CnCwKey.DIGIT_4;
                case '5' -> CnCwKey.DIGIT_5;
                case '6' -> CnCwKey.DIGIT_6;
                case '7' -> CnCwKey.DIGIT_7;
                case '8' -> CnCwKey.DIGIT_8;
                default -> CnCwKey.DIGIT_9;
            });
        }
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private void equal(Object expected, Object actual, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }

    private void near(double expected, double actual, double tolerance, String message) {
        checks++;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
''', encoding="utf-8")


# 5. Wire the new suite into every :core:check.
build = Path("core/build.gradle")
text = build.read_text(encoding="utf-8")
if "cwFunctionalityTest" not in text:
    marker = "tasks.register('manualFunctionsTest', JavaExec) {"
    task = """tasks.register('cwFunctionalityTest', JavaExec) {\n    group = 'verification'\n    description = 'Runs functionality-first guards for formerly partial user-visible commands.'\n    classpath = sourceSets.regression.runtimeClasspath\n    mainClass = 'com.codex.fx991.core.CnCwFunctionalitySuite'\n    dependsOn tasks.named('regressionClasses')\n}\n\n"""
    if marker not in text:
        raise RuntimeError("build.gradle task marker not found")
    text = text.replace(marker, task + marker, 1)
    check_marker = "    dependsOn tasks.named('cwStage6AcceptanceTest')\n"
    if check_marker not in text:
        raise RuntimeError("build.gradle check marker not found")
    text = text.replace(check_marker,
                        check_marker + "    dependsOn tasks.named('cwFunctionalityTest')\n", 1)
    build.write_text(text, encoding="utf-8")

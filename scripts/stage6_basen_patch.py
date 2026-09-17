from pathlib import Path


# --- Machine: expose Base-N operations and route structured Base-N workflows. ---
machine = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java")
text = machine.read_text(encoding="utf-8")
old = '''            case BASE_N -> com.codex.fx991.core.Compat.list(command("decimal", "十进制", "DEC"),
                    command("hex", "十六进制", "HEX"), command("binary", "二进制", "BIN"),
                    command("octal", "八进制", "OCT"));'''
new = '''            case BASE_N -> com.codex.fx991.core.Compat.list(
                    command("decimal", "十进制", "DEC"),
                    command("hex", "十六进制", "HEX"),
                    command("binary", "二进制", "BIN"),
                    command("octal", "八进制", "OCT"),
                    command("base-convert", "进制转换", "DEC / HEX / BIN / OCT"),
                    command("base-add", "加法", "32 位有符号整数"),
                    command("base-subtract", "减法", "32 位有符号整数"),
                    command("base-multiply", "乘法", "32 位有符号整数"),
                    command("base-divide", "除法", "整数商"),
                    command("base-negate", "取负", "NEG"),
                    command("base-not", "NOT", "按位取反"),
                    command("base-and", "AND", "按位与"),
                    command("base-or", "OR", "按位或"),
                    command("base-xor", "XOR", "按位异或"),
                    command("base-xnor", "XNOR", "按位同或"));'''
if old not in text:
    raise RuntimeError("Base-N menu block not found")
text = text.replace(old, new, 1)

marker = '''            } else if (application == ApplicationMode.BASE_N && !com.codex.fx991.core.Compat.isBlank(activeCommandId)) {
                BaseNEngine.Base base = switch (activeCommandId) {'''
insert = '''            } else if (application == ApplicationMode.BASE_N
                    && workflowSession != null
                    && CnCwBaseNWorkflow.handles(activeCommandId)) {
                CnCwModeEngine.ModeResult modeResult = CnCwBaseNWorkflow.evaluate(
                        activeCommandId, workflowSession.snapshot());
                applicationResult = modeResult;
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
            } else if (application == ApplicationMode.BASE_N && !com.codex.fx991.core.Compat.isBlank(activeCommandId)) {
                BaseNEngine.Base base = switch (activeCommandId) {'''
if marker not in text:
    raise RuntimeError("Base-N evaluate marker not found")
text = text.replace(marker, insert, 1)
machine.write_text(text, encoding="utf-8")


# --- Workflow specs: base choice + raw operands. ---
spec = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSpec.java")
text = spec.read_text(encoding="utf-8")
old = '''            case INEQUALITY -> inequality(commandId);
            case MATRIX -> matrix(commandId);'''
new = '''            case INEQUALITY -> inequality(commandId);
            case BASE_N -> baseN(commandId);
            case MATRIX -> matrix(commandId);'''
if old not in text:
    raise RuntimeError("WorkflowSpec switch marker not found")
text = text.replace(old, new, 1)

marker = "    private static WorkflowSpec matrix(String commandId) {"
if marker not in text:
    raise RuntimeError("WorkflowSpec matrix marker not found")
method = '''    private static WorkflowSpec baseN(String commandId) {
        if (commandId.equals("base-convert")) {
            return spec(ApplicationMode.BASE_N, commandId, "进制转换",
                    InputLayout.FIXED_FIELDS,
                    fields(baseChoice("sourceBase", "输入进制"),
                            baseChoice("targetBase", "输出进制"),
                            field("value", "数值", FieldKind.EXPRESSION)),
                    1, 1, 3, 3);
        }
        if (commandId.equals("base-negate") || commandId.equals("base-not")) {
            return spec(ApplicationMode.BASE_N, commandId, baseNTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(baseChoice("base", "进制"),
                            field("value", "数值", FieldKind.EXPRESSION)),
                    1, 1, 2, 2);
        }
        if (commandId.equals("base-add") || commandId.equals("base-subtract")
                || commandId.equals("base-multiply") || commandId.equals("base-divide")
                || commandId.equals("base-and") || commandId.equals("base-or")
                || commandId.equals("base-xor") || commandId.equals("base-xnor")) {
            return spec(ApplicationMode.BASE_N, commandId, baseNTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(baseChoice("base", "进制"),
                            field("left", "左值", FieldKind.EXPRESSION),
                            field("right", "右值", FieldKind.EXPRESSION)),
                    1, 1, 3, 3);
        }
        return null;
    }

    private static FieldSpec baseChoice(String id, String label) {
        return choiceField(id, label,
                choice("10", "DEC"), choice("16", "HEX"),
                choice("2", "BIN"), choice("8", "OCT"));
    }

    private static String baseNTitle(String commandId) {
        return switch (commandId) {
            case "base-add" -> "加法";
            case "base-subtract" -> "减法";
            case "base-multiply" -> "乘法";
            case "base-divide" -> "除法";
            case "base-negate" -> "取负";
            case "base-not" -> "NOT";
            case "base-and" -> "AND";
            case "base-or" -> "OR";
            case "base-xor" -> "XOR";
            case "base-xnor" -> "XNOR";
            default -> "Base-N";
        };
    }

'''
text = text.replace(marker, method + marker, 1)
spec.write_text(text, encoding="utf-8")


# --- Functionality regression: all operation routes + representative real Machine paths. ---
suite = Path("core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java")
text = suite.read_text(encoding="utf-8")
text = text.replace(
        "import com.codex.fx991.core.cw.CnCwCommand;\n",
        "import com.codex.fx991.core.cw.CnCwBaseNWorkflow;\nimport com.codex.fx991.core.cw.CnCwCommand;\n",
        1)
text = text.replace(
        "import com.codex.fx991.core.cw.CnCwWorkflowSpec;\n",
        "import com.codex.fx991.core.cw.CnCwWorkflowSession;\nimport com.codex.fx991.core.cw.CnCwWorkflowSpec;\n",
        1)
old = '''        vectorSlotsPersistAndOperate();
        System.out.println("PASS " + checks + " functionality checks");'''
new = '''        vectorSlotsPersistAndOperate();
        baseNOperationsReachUserWorkflows();
        System.out.println("PASS " + checks + " functionality checks");'''
if old not in text:
    raise RuntimeError("functionality run marker not found")
text = text.replace(old, new, 1)

marker = "    private void matrixSlotsPersistAndOperate() {"
if marker not in text:
    raise RuntimeError("functionality matrix marker not found")
method = r'''    private void baseNOperationsReachUserWorkflows() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 6, 4); // conversion
        machine.dispatch(CnCwKey.OK);    // source DEC -> target
        machine.dispatch(CnCwKey.RIGHT); // target HEX
        machine.dispatch(CnCwKey.OK);    // value
        enter(machine, "255");
        machine.dispatch(CnCwKey.EXE);
        equal("FF", machine.state().applicationResult().items().get(0).value(),
                "DEC 255 converts to HEX FF");
        near(255.0, machine.state().ans(), 0.0, "Base-N conversion stores numeric Ans");

        openCommand(machine, 6, 12); // OR
        machine.dispatch(CnCwKey.RIGHT); // HEX
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.VAR_A);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.EXE);
        equal("F", machine.state().applicationResult().items().get(0).value(),
                "HEX A OR 5 = F through Machine path");

        String[] commands = {"base-add", "base-subtract", "base-multiply", "base-divide",
                "base-negate", "base-not", "base-and", "base-or", "base-xor", "base-xnor"};
        for (String command : commands) {
            CnCwWorkflowSession session = CnCwWorkflowSession.create(
                    CnCwWorkflowSpec.forCommand(ApplicationMode.BASE_N, command));
            session.setCell(0, 0, "10");
            session.setCell(0, 1, "6");
            if (session.columns() == 3) session.setCell(0, 2, "3");
            CnCwModeEngine.ModeResult result = CnCwBaseNWorkflow.evaluate(command,
                    session.snapshot());
            equal(CnCwModeEngine.ResultLayout.KEY_VALUE, result.layout(),
                    command + " structured Base-N result");
            check(!result.items().isEmpty(), command + " has visible formatted value");
        }

        openCommand(machine, 6, 2); // existing BIN mode remains compatible
        enter(machine, "1010");
        machine.dispatch(CnCwKey.EXE);
        equal("1010", machine.state().result(), "legacy BIN parse/format remains reachable");
        near(10.0, machine.state().ans(), 0.0, "legacy BIN numeric value remains 10");
    }

'''
text = text.replace(marker, method + marker, 1)
suite.write_text(text, encoding="utf-8")

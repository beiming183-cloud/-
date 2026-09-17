from pathlib import Path


# --- CnCwMachine: own persistent linear-algebra memory and expose real commands. ---
machine = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java")
text = machine.read_text(encoding="utf-8")

old = """    private final List<HistoryEntry> history = new ArrayList<>();
    private final List<Token> tokens = new ArrayList<>();
    private final SpreadsheetModel spreadsheet;
"""
new = """    private final List<HistoryEntry> history = new ArrayList<>();
    private final List<Token> tokens = new ArrayList<>();
    private final CnCwLinearAlgebraMemory linearAlgebraMemory = new CnCwLinearAlgebraMemory();
    private final SpreadsheetModel spreadsheet;
"""
if old not in text:
    raise RuntimeError("machine memory field marker not found")
text = text.replace(old, new, 1)

old = """        history.addAll(source.history);
        tokens.addAll(source.tokens);

        screen = source.screen;
"""
new = """        history.addAll(source.history);
        tokens.addAll(source.tokens);
        linearAlgebraMemory.copyFrom(source.linearAlgebraMemory);

        screen = source.screen;
"""
if old not in text:
    raise RuntimeError("machine copy marker not found")
text = text.replace(old, new, 1)

old = '''            case MATRIX -> com.codex.fx991.core.Compat.list(command("define", "定义矩阵", "MatA 至 MatD，最大 4×4"),
                    command("calculate", "矩阵计算", "逆、行列式、转置"));
            case VECTOR -> com.codex.fx991.core.Compat.list(command("define", "定义向量", "VctA 至 VctD，2D/3D"),
                    command("calculate", "向量计算", "点积、叉积、夹角"));'''
new = '''            case MATRIX -> com.codex.fx991.core.Compat.list(
                    command("define", "定义 MatA", "最大 4×4"),
                    command("calculate", "直接矩阵", "一次性输入/结果"),
                    command("mat-b", "定义 MatB", "最大 4×4"),
                    command("mat-c", "定义 MatC", "最大 4×4"),
                    command("mat-d", "定义 MatD", "最大 4×4"),
                    command("matrix-det", "行列式", "det(Mat)"),
                    command("matrix-inverse", "逆矩阵", "Mat⁻¹"),
                    command("matrix-transpose", "转置", "Trn(Mat)"),
                    command("matrix-add", "矩阵加法", "Mat+Mat"),
                    command("matrix-subtract", "矩阵减法", "Mat−Mat"),
                    command("matrix-multiply", "矩阵乘法", "Mat×Mat"));
            case VECTOR -> com.codex.fx991.core.Compat.list(
                    command("define", "定义 VctA", "2D/3D"),
                    command("calculate", "直接向量", "一次性输入/结果"),
                    command("vct-b", "定义 VctB", "2D/3D"),
                    command("vct-c", "定义 VctC", "2D/3D"),
                    command("vct-d", "定义 VctD", "2D/3D"),
                    command("vector-magnitude", "向量模", "|Vct|"),
                    command("vector-unit", "单位向量", "Unit(Vct)"),
                    command("vector-add", "向量加法", "Vct+Vct"),
                    command("vector-subtract", "向量减法", "Vct−Vct"),
                    command("vector-dot", "点积", "Vct·Vct"),
                    command("vector-cross", "叉积", "Vct×Vct"),
                    command("vector-angle", "夹角", "Angle(Vct,Vct)"));'''
if old not in text:
    raise RuntimeError("matrix/vector command block not found")
text = text.replace(old, new, 1)

old = '''            } else if (!com.codex.fx991.core.Compat.isBlank(activeCommandId) && isStructuredWorkflow(application)) {
                CnCwModeEngine.ModeResult modeResult = CnCwModeEngine.evaluate(
                        application, activeCommandId, plainSource, evaluationContext());
                applicationResult = modeResult;
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
'''
new = '''            } else if (!com.codex.fx991.core.Compat.isBlank(activeCommandId) && isStructuredWorkflow(application)) {
                CnCwModeEngine.ModeResult modeResult;
                if (linearAlgebraMemory.handles(application, activeCommandId)) {
                    modeResult = linearAlgebraMemory.evaluate(application, activeCommandId,
                            workflowSession == null ? null : workflowSession.snapshot(),
                            evaluationContext());
                } else {
                    modeResult = CnCwModeEngine.evaluate(
                            application, activeCommandId, plainSource, evaluationContext());
                }
                applicationResult = modeResult;
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
'''
if old not in text:
    raise RuntimeError("structured evaluation block not found")
text = text.replace(old, new, 1)
machine.write_text(text, encoding="utf-8")


# --- Workflow specs for MatA..D / VctA..D definitions and stored operations. ---
spec = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwWorkflowSpec.java")
text = spec.read_text(encoding="utf-8")
start = text.index("    private static WorkflowSpec matrix(String commandId) {")
end = text.index("    private static WorkflowSpec ratio(String commandId) {", start)
methods = '''    private static WorkflowSpec matrix(String commandId) {
        if (commandId.equals("calculate")) {
            return spec(ApplicationMode.MATRIX, commandId, "直接矩阵",
                    InputLayout.GRID,
                    fields(field("cell", "元素", FieldKind.EXPRESSION)),
                    1, 4, 1, 4);
        }
        String definition = switch (commandId) {
            case "define" -> "MatA";
            case "mat-b" -> "MatB";
            case "mat-c" -> "MatC";
            case "mat-d" -> "MatD";
            default -> null;
        };
        if (definition != null) {
            return spec(ApplicationMode.MATRIX, commandId, definition,
                    InputLayout.GRID,
                    fields(field("cell", "元素", FieldKind.EXPRESSION)),
                    1, 4, 1, 4);
        }
        if (commandId.equals("matrix-det") || commandId.equals("matrix-inverse")
                || commandId.equals("matrix-transpose")) {
            return spec(ApplicationMode.MATRIX, commandId, matrixOperationTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(linearAlgebraSlot("matrix", "矩阵")),
                    1, 1, 1, 1);
        }
        if (commandId.equals("matrix-add") || commandId.equals("matrix-subtract")
                || commandId.equals("matrix-multiply")) {
            return spec(ApplicationMode.MATRIX, commandId, matrixOperationTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(linearAlgebraSlot("left", "左矩阵"),
                            linearAlgebraSlot("right", "右矩阵")),
                    1, 1, 2, 2);
        }
        return null;
    }

    private static String matrixOperationTitle(String commandId) {
        return switch (commandId) {
            case "matrix-det" -> "行列式";
            case "matrix-inverse" -> "逆矩阵";
            case "matrix-transpose" -> "矩阵转置";
            case "matrix-add" -> "矩阵加法";
            case "matrix-subtract" -> "矩阵减法";
            case "matrix-multiply" -> "矩阵乘法";
            default -> "矩阵";
        };
    }

    private static WorkflowSpec vector(String commandId) {
        if (commandId.equals("calculate")) {
            return spec(ApplicationMode.VECTOR, commandId, "直接向量",
                    InputLayout.VECTOR_SET,
                    fields(field("component", "分量", FieldKind.EXPRESSION)),
                    1, 2, 2, 3);
        }
        String definition = switch (commandId) {
            case "define" -> "VctA";
            case "vct-b" -> "VctB";
            case "vct-c" -> "VctC";
            case "vct-d" -> "VctD";
            default -> null;
        };
        if (definition != null) {
            return spec(ApplicationMode.VECTOR, commandId, definition,
                    InputLayout.VECTOR_SET,
                    fields(field("component", "分量", FieldKind.EXPRESSION)),
                    1, 1, 2, 3);
        }
        if (commandId.equals("vector-magnitude") || commandId.equals("vector-unit")) {
            return spec(ApplicationMode.VECTOR, commandId, vectorOperationTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(linearAlgebraSlot("vector", "向量")),
                    1, 1, 1, 1);
        }
        if (commandId.equals("vector-add") || commandId.equals("vector-subtract")
                || commandId.equals("vector-dot") || commandId.equals("vector-cross")
                || commandId.equals("vector-angle")) {
            return spec(ApplicationMode.VECTOR, commandId, vectorOperationTitle(commandId),
                    InputLayout.FIXED_FIELDS,
                    fields(linearAlgebraSlot("left", "左向量"),
                            linearAlgebraSlot("right", "右向量")),
                    1, 1, 2, 2);
        }
        return null;
    }

    private static String vectorOperationTitle(String commandId) {
        return switch (commandId) {
            case "vector-magnitude" -> "向量模";
            case "vector-unit" -> "单位向量";
            case "vector-add" -> "向量加法";
            case "vector-subtract" -> "向量减法";
            case "vector-dot" -> "向量点积";
            case "vector-cross" -> "向量叉积";
            case "vector-angle" -> "向量夹角";
            default -> "向量";
        };
    }

    private static FieldSpec linearAlgebraSlot(String id, String label) {
        return choiceField(id, label,
                choice("A", label.startsWith("左") || label.startsWith("右") ? "A" : "A"),
                choice("B", "B"), choice("C", "C"), choice("D", "D"));
    }

'''
spec.write_text(text[:start] + methods + text[end:], encoding="utf-8")


# --- Extend the permanent functionality suite with actual stored-object workflows. ---
suite = Path("core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java")
text = suite.read_text(encoding="utf-8")
text = text.replace(
        "import com.codex.fx991.core.cw.CnCwModeEngine;\n",
        "import com.codex.fx991.core.cw.CnCwModeEngine;\nimport com.codex.fx991.core.cw.CnCwWorkflowAction;\n",
        1)
old = '''        quadraticRegressionRunsThroughMachineWorkflow();
        System.out.println("PASS " + checks + " functionality checks");'''
new = '''        quadraticRegressionRunsThroughMachineWorkflow();
        matrixSlotsPersistAndOperate();
        vectorSlotsPersistAndOperate();
        System.out.println("PASS " + checks + " functionality checks");'''
if old not in text:
    raise RuntimeError("functionality run marker not found")
text = text.replace(old, new, 1)
marker = "    private static double parse(CnCwMachine machine, int index) {"
if marker not in text:
    raise RuntimeError("functionality insertion marker not found")
methods = r'''    private void matrixSlotsPersistAndOperate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 7, 0); // define MatA
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_ROWS);
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS);
        fillGrid(machine, "1", "2", "3", "4");
        check(machine.state().resultShown(), "MatA definition reaches result");
        equal("MatA 已保存", machine.state().applicationResult().title(), "MatA stored title");

        openCommand(machine, 7, 2); // define MatB
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_ROWS);
        machine.performWorkflowAction(CnCwWorkflowAction.Type.INCREASE_COLUMNS);
        fillGrid(machine, "1", "0", "0", "1");
        equal("MatB 已保存", machine.state().applicationResult().title(), "MatB stored title");

        openCommand(machine, 7, 5); // det(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, machine.state().applicationResult().layout(),
                "stored determinant result layout");
        near(-2.0, parse(machine, 0), 1e-10, "det(MatA)");

        openCommand(machine, 7, 6); // inverse(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.MATRIX, machine.state().applicationResult().layout(),
                "stored inverse returns matrix");
        equal(4, machine.state().applicationResult().cells().size(), "inverse keeps 2x2 shape");

        openCommand(machine, 7, 7); // transpose(MatA)
        machine.dispatch(CnCwKey.EXE);
        equal("3", machine.state().applicationResult().cells().get(1),
                "transpose swaps off-diagonal cell");

        openBinaryChoiceCommand(machine, 7, 8); // MatA + MatB
        equal("2", machine.state().applicationResult().cells().get(0), "matrix add [1,1]");
        equal("5", machine.state().applicationResult().cells().get(3), "matrix add [2,2]");

        openBinaryChoiceCommand(machine, 7, 9); // MatA - MatB
        equal("0", machine.state().applicationResult().cells().get(0), "matrix subtract [1,1]");
        equal("3", machine.state().applicationResult().cells().get(3), "matrix subtract [2,2]");

        openBinaryChoiceCommand(machine, 7, 10); // MatA * MatB
        equal("1", machine.state().applicationResult().cells().get(0), "matrix multiply identity");
        equal("4", machine.state().applicationResult().cells().get(3), "matrix multiply identity last");
    }

    private void vectorSlotsPersistAndOperate() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        openCommand(machine, 8, 0); // define VctA
        fillGrid(machine, "3", "4");
        equal("VctA 已保存", machine.state().applicationResult().title(), "VctA stored title");

        openCommand(machine, 8, 2); // define VctB
        fillGrid(machine, "0", "1");
        equal("VctB 已保存", machine.state().applicationResult().title(), "VctB stored title");

        openCommand(machine, 8, 5); // magnitude A
        machine.dispatch(CnCwKey.EXE);
        near(5.0, parse(machine, 0), 1e-10, "|VctA|");

        openCommand(machine, 8, 6); // unit A
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwModeEngine.ResultLayout.VECTOR, machine.state().applicationResult().layout(),
                "unit vector layout");

        openBinaryChoiceCommand(machine, 8, 7); // A+B
        equal("3", machine.state().applicationResult().cells().get(0), "vector add x");
        equal("5", machine.state().applicationResult().cells().get(1), "vector add y");

        openBinaryChoiceCommand(machine, 8, 8); // A-B
        equal("3", machine.state().applicationResult().cells().get(0), "vector subtract x");
        equal("3", machine.state().applicationResult().cells().get(1), "vector subtract y");

        openBinaryChoiceCommand(machine, 8, 9); // dot
        near(4.0, parse(machine, 0), 1e-10, "VctA dot VctB");

        openBinaryChoiceCommand(machine, 8, 10); // cross
        equal(3, machine.state().applicationResult().columns(), "2D cross publishes 3D vector");
        equal("3", machine.state().applicationResult().cells().get(2), "2D cross z component");

        openBinaryChoiceCommand(machine, 8, 11); // angle
        check(parse(machine, 0) > 36.0 && parse(machine, 0) < 37.0,
                "VctA/VctB angle is about 36.87 degrees");
    }

    private static void openCommand(CnCwMachine machine, int homeIndex, int commandIndex) {
        machine.dispatch(CnCwKey.HOME);
        for (int i = 0; i < homeIndex; i++) machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.OK);
        for (int i = 0; i < commandIndex; i++) machine.dispatch(CnCwKey.DOWN);
        machine.dispatch(CnCwKey.OK);
    }

    private static void openBinaryChoiceCommand(CnCwMachine machine,
                                                 int homeIndex, int commandIndex) {
        openCommand(machine, homeIndex, commandIndex);
        machine.dispatch(CnCwKey.OK); // first slot -> second slot
        machine.dispatch(CnCwKey.RIGHT); // A -> B
        machine.dispatch(CnCwKey.EXE);
    }

    private static void fillGrid(CnCwMachine machine, String... values) {
        for (int index = 0; index < values.length; index++) {
            enter(machine, values[index]);
            machine.dispatch(index + 1 == values.length ? CnCwKey.EXE : CnCwKey.OK);
        }
    }

'''
text = text.replace(marker, methods + marker, 1)
suite.write_text(text, encoding="utf-8")

from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
text = machine_path.read_text(encoding='utf-8')
replacements = [
    (
        '    private CnCwScreen screen = CnCwScreen.HOME;\n    private ApplicationMode application;\n',
        '    private CnCwScreen screen = CnCwScreen.HOME;\n    private ApplicationMode application;\n    /** App that was active immediately before HOME, used for MatAns/VctAns lifetime rules. */\n    private ApplicationMode applicationBeforeHome;\n'
    ),
    (
        '        screen = source.screen;\n        application = source.application;\n        settings = source.settings;\n',
        '        screen = source.screen;\n        application = source.application;\n        applicationBeforeHome = source.applicationBeforeHome;\n        settings = source.settings;\n'
    ),
    (
        '        screen = CnCwScreen.HOME;\n        application = null;\n        settings = CnCwSettings.defaults();\n',
        '        screen = CnCwScreen.HOME;\n        application = null;\n        applicationBeforeHome = null;\n        settings = CnCwSettings.defaults();\n'
    ),
    (
        '    private void showHome() {\n        workflowSession = null;\n        applicationResult = null;\n        navigation.clear();\n        screen = CnCwScreen.HOME;\n        application = null;\n',
        '    private void showHome() {\n        workflowSession = null;\n        applicationResult = null;\n        navigation.clear();\n        screen = CnCwScreen.HOME;\n        applicationBeforeHome = application;\n        application = null;\n'
    ),
    (
        '    private void openApplication(ApplicationMode mode) {\n        workflowSession = null;\n        applicationResult = null;\n        application = mode;\n',
        '    private void openApplication(ApplicationMode mode) {\n        workflowSession = null;\n        applicationResult = null;\n        if (applicationBeforeHome != null && applicationBeforeHome != mode) {\n            // The original 991CN CW clears MatAns/VctAns when HOME is used to launch\n            // another application, while MatA..MatD/VctA..VctD remain stored.\n            linearAlgebraMemory.clearAnswers();\n        }\n        applicationBeforeHome = null;\n        application = mode;\n'
    ),
]
for old, new in replacements:
    if old not in text:
        raise SystemExit(f'machine target not found: {old!r}')
    text = text.replace(old, new, 1)
machine_path.write_text(text, encoding='utf-8')

test_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java')
test = test_path.read_text(encoding='utf-8')
old_run = '        resetClearsStoredLinearAlgebra();\n        System.out.println("PASS " + checks + " functionality checks");'
new_run = '        resetClearsStoredLinearAlgebra();\n        linearAnswerMemoriesClearWhenLeavingApps();\n        System.out.println("PASS " + checks + " functionality checks");'
if old_run not in test:
    raise SystemExit('run-list target not found')
test = test.replace(old_run, new_run, 1)

marker = '    private static void openCommand(CnCwMachine machine, int homeIndex, int commandIndex) {'
method = '''    private void linearAnswerMemoriesClearWhenLeavingApps() {\n        CnCwMachine matrix = new CnCwMachine(CnCwModel.FX_991_CN_CW);\n        openCommand(matrix, 7, 0);\n        enter(matrix, "5");\n        matrix.dispatch(CnCwKey.EXE);\n        openCommand(matrix, 7, 11); // MatA^2 -> MatAns\n        matrix.dispatch(CnCwKey.EXE);\n        equal("25", matrix.state().applicationResult().cells().get(0),\n                "matrix result populates MatAns before app switch");\n        matrix.dispatch(CnCwKey.HOME);\n        matrix.dispatch(CnCwKey.OK); // launch Calculate: this clears MatAns only\n        openCommand(matrix, 7, 15);\n        check(matrix.state().calculationState().isError(),\n                "launching another app clears MatAns");\n        openCommand(matrix, 7, 5); // MatA itself must persist\n        matrix.dispatch(CnCwKey.EXE);\n        near(5.0, parse(matrix, 0), 1e-10,\n                "launching another app keeps MatA slot data");\n\n        CnCwMachine vector = new CnCwMachine(CnCwModel.FX_991_CN_CW);\n        openCommand(vector, 8, 0);\n        fillGrid(vector, "3", "4");\n        openCommand(vector, 8, 2);\n        fillGrid(vector, "1", "2");\n        openBinaryChoiceCommand(vector, 8, 7); // VctA+VctB -> VctAns\n        equal("4", vector.state().applicationResult().cells().get(0),\n                "vector result populates VctAns before app switch");\n        vector.dispatch(CnCwKey.HOME);\n        vector.dispatch(CnCwKey.OK); // launch Calculate\n        openCommand(vector, 8, 12);\n        check(vector.state().calculationState().isError(),\n                "launching another app clears VctAns");\n        openCommand(vector, 8, 5); // VctA remains available\n        vector.dispatch(CnCwKey.EXE);\n        near(5.0, parse(vector, 0), 1e-10,\n                "launching another app keeps VctA slot data");\n    }\n\n'''
if marker not in test:
    raise SystemExit('test insertion marker not found')
test = test.replace(marker, method + marker, 1)
test_path.write_text(test, encoding='utf-8')

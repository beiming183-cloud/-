from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
text = machine_path.read_text(encoding='utf-8')
old = '''        if (applicationBeforeHome != null && applicationBeforeHome != mode) {
            // The original 991CN CW clears MatAns/VctAns when HOME is used to launch
            // another application, while MatA..MatD/VctA..VctD remain stored.
            linearAlgebraMemory.clearAnswers();
        }
        applicationBeforeHome = null;
        application = mode;
        screen = CnCwScreen.forApplication(mode);
        selectedIndex = 0;
        navigation.clear();
        shiftArmed = false;
        result = "";
        resultShown = false;
        tokens.clear();
'''
new = '''        if (applicationBeforeHome != null && applicationBeforeHome != mode) {
            // The original 991CN CW clears transient answer/verification state when
            // HOME launches another application, while MatA..MatD/VctA..VctD remain stored.
            linearAlgebraMemory.clearAnswers();
            verificationMode = false;
        }
        applicationBeforeHome = null;
        application = mode;
        screen = CnCwScreen.forApplication(mode);
        selectedIndex = 0;
        navigation.clear();
        shiftArmed = false;
        committedCalculationState = CnCwCalculationState.editing();
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        errorCursor = 0;
        tokens.clear();
'''
if old not in text:
    raise SystemExit('openApplication target not found')
text = text.replace(old, new, 1)
machine_path.write_text(text, encoding='utf-8')

test_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java')
test = test_path.read_text(encoding='utf-8')
old_run = '''        resetClearsStoredLinearAlgebra();
        linearAnswerMemoriesClearWhenLeavingApps();
        System.out.println("PASS " + checks + " functionality checks");'''
new_run = '''        resetClearsStoredLinearAlgebra();
        linearAnswerMemoriesClearWhenLeavingApps();
        appSwitchClearsTransientErrorAndVerificationState();
        System.out.println("PASS " + checks + " functionality checks");'''
if old_run not in test:
    raise SystemExit('run list target not found')
test = test.replace(old_run, new_run, 1)

marker = '    private static void openCommand(CnCwMachine machine, int homeIndex, int commandIndex) {'
method = '''    private void appSwitchClearsTransientErrorAndVerificationState() {\n        CnCwMachine verify = new CnCwMachine(CnCwModel.FX_991_CN_CW);\n        verify.dispatch(CnCwKey.OK); // Calculate\n        verify.dispatch(CnCwKey.TOOLS);\n        verify.dispatch(CnCwKey.DOWN);\n        verify.dispatch(CnCwKey.DOWN);\n        verify.dispatch(CnCwKey.OK); // verification on\n        check(verify.state().verificationMode(), "verification can be enabled in Calculate");\n\n        verify.dispatch(CnCwKey.HOME);\n        verify.dispatch(CnCwKey.OK); // re-open the same Calculate app\n        check(verify.state().verificationMode(),\n                "HOME then same app preserves verification mode");\n\n        verify.dispatch(CnCwKey.HOME);\n        verify.dispatch(CnCwKey.RIGHT);\n        verify.dispatch(CnCwKey.OK); // Statistics is a different app\n        check(!verify.state().verificationMode(),\n                "HOME then another app disables verification mode");\n\n        CnCwMachine error = new CnCwMachine(CnCwModel.FX_991_CN_CW);\n        openCommand(error, 7, 15); // MatAns is undefined\n        check(error.state().calculationState().isError(),\n                "undefined MatAns publishes an error before app switch");\n        error.dispatch(CnCwKey.HOME);\n        error.dispatch(CnCwKey.OK); // Calculate\n        check(!error.state().calculationState().isError(),\n                "opening a new app clears the previous error overlay");\n        check(!error.state().resultShown(),\n                "opening a new app returns to a clean editing state");\n    }\n\n'''
if marker not in test:
    raise SystemExit('test insertion marker not found')
test = test.replace(marker, method + marker, 1)
test_path.write_text(test, encoding='utf-8')

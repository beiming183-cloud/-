from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_once(text, old, new, name):
    if old not in text:
        raise SystemExit(f"target not found: {name}")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# Machine: retain the committed typed outcome through history and eval copies.
# ---------------------------------------------------------------------------
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''    /** Stage 5 structured input editor; null keeps the legacy expression bridge. */
    private CnCwWorkflowSession workflowSession;
    private String status = "HOME";
''',
'''    /** Stage 5 structured input editor; null keeps the legacy expression bridge. */
    private CnCwWorkflowSession workflowSession;
    /** Last explicitly committed Stage 6 result/error payload. */
    private CnCwCalculationState committedCalculationState = CnCwCalculationState.editing();
    private String status = "HOME";
''', 'committed state field')
text = replace_once(text,
'''        applicationResult = source.applicationResult;
        workflowSession = source.workflowSession == null ? null : source.workflowSession.copy();
        status = source.status;
''',
'''        applicationResult = source.applicationResult;
        workflowSession = source.workflowSession == null ? null : source.workflowSession.copy();
        committedCalculationState = source.committedCalculationState;
        status = source.status;
''', 'copy committed state')

old_snapshot = '''    private CnCwCalculationState calculationStateSnapshot() {
        if (errorShown) {
            return CnCwCalculationState.error(result, lastError, errorCursor);
        }
        if (!resultShown) return CnCwCalculationState.editing();
        if (applicationResult != null) {
            return CnCwCalculationState.applicationResult(result, applicationResult);
        }
        if (lastExactResult != null) {
            return CnCwCalculationState.exactResult(result, ans, lastExactResult);
        }
        if (hasComplexAns && !originalResult.isEmpty()) {
            return CnCwCalculationState.complexResult(result, complexAns);
        }
        if (hasAns && !originalResult.isEmpty()) {
            return CnCwCalculationState.scalarResult(result, ans);
        }
        return CnCwCalculationState.textResult(result);
    }
'''
new_snapshot = '''    private CnCwCalculationState calculationStateSnapshot() {
        if (!resultShown) return CnCwCalculationState.editing();
        if (committedCalculationState != null
                && committedCalculationState.display().equals(result)) {
            if (errorShown && committedCalculationState.isError()) {
                return committedCalculationState;
            }
            if (!errorShown && committedCalculationState.isResult()) {
                return committedCalculationState;
            }
        }
        if (errorShown) {
            return CnCwCalculationState.error(result, lastError, errorCursor);
        }
        if (applicationResult != null) {
            return CnCwCalculationState.applicationResult(result, applicationResult);
        }
        if (lastExactResult != null) {
            return CnCwCalculationState.exactResult(result, ans, lastExactResult);
        }
        if (hasComplexAns && !originalResult.isEmpty()) {
            return CnCwCalculationState.complexResult(result, complexAns);
        }
        if (hasAns && !originalResult.isEmpty()) {
            return CnCwCalculationState.scalarResult(result, ans);
        }
        return CnCwCalculationState.textResult(result);
    }
'''
text = replace_once(text, old_snapshot, new_snapshot, 'typed snapshot source')

# Direct non-evaluator result surfaces should also identify their payload.
text = replace_once(text,
'''            result = "重新计算完成\\n剩余 " + spreadsheet.remainingBytes() + " bytes";
            resultShown = true;
''',
'''            result = "重新计算完成\\n剩余 " + spreadsheet.remainingBytes() + " bytes";
            resultShown = true;
            committedCalculationState = CnCwCalculationState.textResult(result);
''', 'spreadsheet recalc typed result')
text = replace_once(text,
'''                pendingFunctionDefinition = "";
                resultShown = true;
                return;
''',
'''                pendingFunctionDefinition = "";
                resultShown = true;
                committedCalculationState = CnCwCalculationState.textResult(result);
                return;
''', 'function definition typed result')

# Successful evaluator commit constructs one immutable typed outcome before history.
text = replace_once(text,
'''        engineeringMode = false;
        status = !completionStatus.isEmpty() ? completionStatus
''',
'''        engineeringMode = false;
        committedCalculationState = successfulCalculationState(
                formatted, scalar, exactScalar, complexEvaluation);
        status = !completionStatus.isEmpty() ? completionStatus
''', 'success typed state')
text = replace_once(text,
'''        history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result,
                resultProcessDisplay, applicationResult));
''',
'''        history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result,
                resultProcessDisplay, applicationResult, committedCalculationState));
''', 'history typed state')
marker = '''    private CoordinateCall coordinateCall(String source) {
'''
helper = '''    private CnCwCalculationState successfulCalculationState(String display,
                                                                  double scalar,
                                                                  ExactValue exactScalar,
                                                                  boolean complexEvaluation) {
        if (applicationResult != null) {
            return CnCwCalculationState.applicationResult(display, applicationResult);
        }
        if (complexEvaluation && hasComplexAns) {
            return CnCwCalculationState.complexResult(display, complexAns);
        }
        if (exactScalar != null) {
            return CnCwCalculationState.exactResult(display, scalar, exactScalar);
        }
        return CnCwCalculationState.scalarResult(display, scalar);
    }

    private CnCwCalculationState currentAnswerCalculationState(String display) {
        if (hasComplexAns) return CnCwCalculationState.complexResult(display, complexAns);
        if (exactAns != null) return CnCwCalculationState.exactResult(display, ans, exactAns);
        if (hasAns) return CnCwCalculationState.scalarResult(display, ans);
        return CnCwCalculationState.textResult(display);
    }

'''
if marker not in text:
    raise SystemExit('coordinate marker not found')
text = text.replace(marker, helper + marker, 1)

# Error commit owns its typed error payload.
text = replace_once(text,
'''        result = error.display();
        resultShown = true;
        status = "按 OK、返回或 AC 回到错误位置";
''',
'''        result = error.display();
        resultShown = true;
        committedCalculationState = CnCwCalculationState.error(result, error, errorCursor);
        status = "按 OK、返回或 AC 回到错误位置";
''', 'error typed state')

# History recall restores the historical typed payload but deliberately does not mutate Ans.
text = replace_once(text,
'''        resultProcessDisplay = entry.processDisplay;
        applicationResult = entry.applicationResult;
        resultShown = true;
''',
'''        resultProcessDisplay = entry.processDisplay;
        applicationResult = entry.applicationResult;
        committedCalculationState = entry.calculationState;
        resultShown = true;
''', 'history recall typed state')

# Format transformations are results of the current Ans, so refresh the typed payload display.
text = replace_once(text,
'''        formatConverted = true;
        resultShown = true;
        status = engineeringMode ? "ENG 模式 · 用 ←/→ 移动小数点" : "格式转换";
''',
'''        formatConverted = true;
        resultShown = true;
        committedCalculationState = currentAnswerCalculationState(result);
        status = engineeringMode ? "ENG 模式 · 用 ←/→ 移动小数点" : "格式转换";
''', 'format typed state')
text = replace_once(text,
'''        if (!originalResult.isEmpty()) result = originalResult;
        resultShown = !result.isEmpty();
        status = applicationStatus();
''',
'''        if (!originalResult.isEmpty()) result = originalResult;
        resultShown = !result.isEmpty();
        if (resultShown) committedCalculationState = currentAnswerCalculationState(result);
        status = applicationStatus();
''', 'restore format typed state')
text = replace_once(text,
'''        result = engineeringAtExponent(ans, engineeringExponent);
        resultShown = true;
        status = "ENG · 10^" + engineeringExponent;
''',
'''        result = engineeringAtExponent(ans, engineeringExponent);
        resultShown = true;
        committedCalculationState = currentAnswerCalculationState(result);
        status = "ENG · 10^" + engineeringExponent;
''', 'engineering typed state')

# Major lifecycle resets keep the stored source coherent too.
for old, new, name in [
    ('''    private void clearExpression() {\n        rememberUndo();\n''',
     '''    private void clearExpression() {\n        rememberUndo();\n        committedCalculationState = CnCwCalculationState.editing();\n''', 'clear state'),
    ('''    private void dismissError() {\n        errorShown = false;\n''',
     '''    private void dismissError() {\n        committedCalculationState = CnCwCalculationState.editing();\n        errorShown = false;\n''', 'dismiss state')]:
    text = replace_once(text, old, new, name)

# History record now carries the immutable typed outcome.
text = replace_once(text,
'''    private record HistoryEntry(List<Token> tokens, String result, String processDisplay,
                                CnCwModeEngine.ModeResult applicationResult) { }
''',
'''    private record HistoryEntry(List<Token> tokens, String result, String processDisplay,
                                CnCwModeEngine.ModeResult applicationResult,
                                CnCwCalculationState calculationState) { }
''', 'history record')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Regression: exact/complex history payloads and async copies must stay typed.
# ---------------------------------------------------------------------------
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwCalculationStateSuite.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        applicationResultIsTyped();
        errorMirrorsLegacyState();
        System.out.println("PASS " + checks + " calculation-state checks");
''',
'''        applicationResultIsTyped();
        errorMirrorsLegacyState();
        historyRetainsTypedPayloads();
        evaluationSnapshotRetainsTypedOutcome();
        System.out.println("PASS " + checks + " calculation-state checks");
''', 'test run list')
marker = '''    private CnCwMachine calculateMachine() {
'''
methods = '''    private void historyRetainsTypedPayloads() {
        CnCwMachine machine = calculateMachine();
        machine.pasteExpression("1/3");
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwCalculationState.ResultKind.EXACT, machine.state().calculationState().resultKind(),
                "first history result is exact");

        machine.dispatch(CnCwKey.AC);
        machine.pasteExpression("i");
        machine.dispatch(CnCwKey.EXE);
        equal(CnCwCalculationState.ResultKind.COMPLEX, machine.state().calculationState().resultKind(),
                "second history result is complex");

        machine.dispatch(CnCwKey.UP);
        machine.dispatch(CnCwKey.UP);
        equal(CnCwCalculationState.ResultKind.EXACT, machine.state().calculationState().resultKind(),
                "recalling older history restores exact payload rather than current Ans type");
        check(machine.state().calculationState().exactValue() != null,
                "recalled exact history keeps exact value");

        machine.dispatch(CnCwKey.DOWN);
        equal(CnCwCalculationState.ResultKind.COMPLEX, machine.state().calculationState().resultKind(),
                "moving forward in history restores complex payload");
        check(machine.state().calculationState().complexValue() != null,
                "recalled complex history keeps complex value");
    }

    private void evaluationSnapshotRetainsTypedOutcome() {
        CnCwMachine machine = calculateMachine();
        machine.pasteExpression("i");
        machine.dispatch(CnCwKey.EXE);
        CnCwMachine copy = machine.copyForEvaluation();
        equal(CnCwCalculationState.ResultKind.COMPLEX, copy.state().calculationState().resultKind(),
                "evaluation copy retains committed complex payload");
        equal(machine.state().calculationState().display(), copy.state().calculationState().display(),
                "evaluation copy retains typed display");
    }

'''
if marker not in text:
    raise SystemExit('test insertion marker not found')
text = text.replace(marker, methods + marker, 1)
p.write_text(text, encoding='utf-8')

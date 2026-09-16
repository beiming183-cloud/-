from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_once(text, old, new, name):
    if old not in text:
        raise SystemExit(f"target not found: {name}")
    return text.replace(old, new, 1)

# UiState: publish typed Stage 6 snapshot beside compatibility fields.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwUiState.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''    /** Stage 5 immutable input-table snapshot; null for the legacy editor. */
    private final CnCwWorkflowSession.Snapshot workflowInput;
    private final double ans;
''',
'''    /** Stage 5 immutable input-table snapshot; null for the legacy editor. */
    private final CnCwWorkflowSession.Snapshot workflowInput;
    /** Stage 6 typed calculation phase/result/error snapshot. */
    private final CnCwCalculationState calculationState;
    private final double ans;
''', 'ui field')
text = replace_once(text,
'''                CnCwModeEngine.ModeResult applicationResult,
                CnCwWorkflowSession.Snapshot workflowInput,
                double ans,
''',
'''                CnCwModeEngine.ModeResult applicationResult,
                CnCwWorkflowSession.Snapshot workflowInput,
                CnCwCalculationState calculationState,
                double ans,
''', 'ui ctor parameter')
text = replace_once(text,
'''        this.applicationResult = applicationResult;
        this.workflowInput = workflowInput;
        this.ans = ans;
''',
'''        this.applicationResult = applicationResult;
        this.workflowInput = workflowInput;
        this.calculationState = Objects.requireNonNull(calculationState, "calculationState");
        this.ans = ans;
''', 'ui ctor assignment')
text = replace_once(text,
'''    public CnCwWorkflowSession.Snapshot workflowInput() { return workflowInput; }
    public boolean hasWorkflowInput() { return workflowInput != null; }
''',
'''    public CnCwWorkflowSession.Snapshot workflowInput() { return workflowInput; }
    public boolean hasWorkflowInput() { return workflowInput != null; }
    public CnCwCalculationState calculationState() { return calculationState; }
''', 'ui accessor')
p.write_text(text, encoding='utf-8')

# Machine: derive typed snapshot from the legacy source of truth.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')
marker = '''    private void publish() {
'''
method = '''    /**
     * Stage 6 compatibility projection. The legacy fields remain the write-side
     * source of truth in Step 1; this snapshot must therefore be lossless and
     * side-effect free.
     */
    private CnCwCalculationState calculationStateSnapshot() {
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
if marker not in text:
    raise SystemExit('machine publish marker not found')
text = text.replace(marker, method + marker, 1)
text = replace_once(text,
'''                result, resultShown ? applicationResult : null,
                workflowSession == null ? null : workflowSession.snapshot(),
                ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,
''',
'''                result, resultShown ? applicationResult : null,
                workflowSession == null ? null : workflowSession.snapshot(),
                calculationStateSnapshot(),
                ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,
''', 'machine publish argument')
p.write_text(text, encoding='utf-8')

# Gradle: make the new regression part of :core:check.
p = root / 'core/build.gradle'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''tasks.register('manualFunctionsTest', JavaExec) {
''',
'''tasks.register('cwCalculationStateTest', JavaExec) {
    group = 'verification'
    description = 'Runs Stage 6 typed calculation phase/result/error compatibility checks.'
    classpath = sourceSets.regression.runtimeClasspath
    mainClass = 'com.codex.fx991.core.CnCwCalculationStateSuite'
    dependsOn tasks.named('regressionClasses')
}

tasks.register('manualFunctionsTest', JavaExec) {
''', 'gradle task')
text = replace_once(text,
'''    dependsOn tasks.named('cwWorkflowSpecTest')
    dependsOn tasks.named('manualFunctionsTest')
''',
'''    dependsOn tasks.named('cwWorkflowSpecTest')
    dependsOn tasks.named('cwCalculationStateTest')
    dependsOn tasks.named('manualFunctionsTest')
''', 'gradle check')
p.write_text(text, encoding='utf-8')

from pathlib import Path

p = Path(__file__).resolve().parents[1] / 'core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java'
text = p.read_text(encoding='utf-8')
start = text.index('    private void workflowMutationActionsStayCoreOwned() {')
end = text.index('    private void spreadsheetCompactWorkflowPersistsCells() {', start)
method = '''    private void workflowMutationActionsStayCoreOwned() {
        CnCwMachine statistics = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        statistics.dispatch(CnCwKey.OK);
        check(statistics.state().hasWorkflowInput(),
                "statistics command opens structured workflow");
        equal("1 条", statistics.state().workflowInput().shapeLabel(),
                "statistics UI snapshot starts with core-owned shape label");
        press(statistics, CnCwKey.DIGIT_7);
        statistics.applyWorkflowMutation(CnCwWorkflowAction.Type.ADD_ROW);
        check(statistics.state().hasWorkflowInput(),
                "statistics remains in workflow after mutation");
        equal("2 条", statistics.state().workflowInput().shapeLabel(),
                "machine mutation updates core-owned shape");
        equal("7", statistics.state().workflowInput().cell(0, 0),
                "machine mutation commits active cell before adding row");
        check(statistics.state().status().contains("2 条"),
                "workflow status exposes human-readable shape");
    }

'''
p.write_text(text[:start] + method + text[end:], encoding='utf-8')

from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_once(path, old, new, name):
    text = path.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'target not found: {name}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


# Machine-owned public action entry point.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
replace_once(p,
'''    /** Direct-touch entry point for a Stage 5 input-table cell. */
    public CnCwUiState selectWorkflowCell(int row, int column) {
''',
'''    /** Executes one core-owned workflow action without duplicating layout rules in Android. */
    public CnCwUiState performWorkflowAction(CnCwWorkflowAction.Type type) {
        if (type == null || workflowSession == null || resultShown) return state;
        if (type == CnCwWorkflowAction.Type.EXECUTE) return dispatch(CnCwKey.EXE);
        if (type == CnCwWorkflowAction.Type.BACK) return dispatch(CnCwKey.BACK);
        commitWorkflowCell();
        boolean changed = workflowSession.applyAction(type);
        if (changed) loadWorkflowCell();
        status = changed ? workflowStatus() : "操作不可用 · " + workflowStatus();
        publish();
        return state;
    }

    /** Direct-touch entry point for a Stage 5 input-table cell. */
    public CnCwUiState selectWorkflowCell(int row, int column) {
''', 'machine action entry')

# Machine regression for direct action execution.
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java'
replace_once(p,
'''import com.codex.fx991.core.cw.CnCwUiState;
''',
'''import com.codex.fx991.core.cw.CnCwUiState;
import com.codex.fx991.core.cw.CnCwWorkflowAction;
''', 'workflow action import')
replace_once(p,
'''        structuredWorkflowInputPageRunsEndToEnd();
        spreadsheetCompactWorkflowPersistsCells();
''',
'''        structuredWorkflowInputPageRunsEndToEnd();
        workflowActionsCanBeAppliedFromMachine();
        spreadsheetCompactWorkflowPersistsCells();
''', 'action regression run')
marker = '''    private void spreadsheetCompactWorkflowPersistsCells() {
'''
method = '''    private void workflowActionsCanBeAppliedFromMachine() {
        CnCwMachine statistics = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        statistics.dispatch(CnCwKey.OK);
        check(statistics.state().hasWorkflowInput(), "statistics action test opens workflow");
        equal(1, statistics.state().workflowInput().rows(), "series starts at one row");
        statistics.performWorkflowAction(CnCwWorkflowAction.Type.ADD_ROW);
        equal(2, statistics.state().workflowInput().rows(), "ADD_ROW mutates workflow through machine");
        statistics.performWorkflowAction(CnCwWorkflowAction.Type.REMOVE_ROW);
        equal(1, statistics.state().workflowInput().rows(), "REMOVE_ROW mutates workflow through machine");
        statistics.performWorkflowAction(CnCwWorkflowAction.Type.BACK);
        check(statistics.state().applicationLanding(), "BACK action returns to command landing");
    }

'''
text = p.read_text(encoding='utf-8')
if marker not in text:
    raise SystemExit('machine action test marker not found')
p.write_text(text.replace(marker, method + marker, 1), encoding='utf-8')

# Android: render the action protocol as a two-row touch bar.
p = root / 'app/src/main/java/com/codex/fx991smooth/CalculatorView.java'
replace_once(p,
'''import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
''',
'''import com.codex.fx991.core.cw.CnCwWorkflowAction;
import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
''', 'android action import')
replace_once(p,
'''    private RectF workflowGridBounds(RectF lcd) {
        float top = lcd.top + lcd.height() * 0.22f;
        float bottom = lcd.bottom - dp(18f);
        return new RectF(lcd.left + dp(6f), top, lcd.right - dp(6f), bottom);
    }
''',
'''    private RectF workflowGridBounds(RectF lcd) {
        float top = lcd.top + lcd.height() * 0.22f;
        float bottom = lcd.bottom - dp(43f);
        return new RectF(lcd.left + dp(6f), top, lcd.right - dp(6f), bottom);
    }

    private RectF workflowActionBarBounds(RectF lcd) {
        return new RectF(lcd.left + dp(6f), lcd.bottom - dp(38f),
                lcd.right - dp(6f), lcd.bottom - dp(3f));
    }

    private RectF workflowActionBounds(RectF lcd, int count, int index) {
        RectF bar = workflowActionBarBounds(lcd);
        int columns = count <= 2 ? Math.max(1, count) : 3;
        int rows = Math.max(1, (count + columns - 1) / columns);
        int row = index / columns;
        int column = index % columns;
        float gap = dp(2f);
        float width = (bar.width() - gap * (columns - 1)) / columns;
        float height = (bar.height() - gap * (rows - 1)) / rows;
        float left = bar.left + column * (width + gap);
        float top = bar.top + row * (height + gap);
        return new RectF(left, top, left + width, top + height);
    }
''', 'action bar geometry')

# Insert action drawing before workflow input closes.
old = '''        paint.setStyle(Paint.Style.FILL);
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(sp(8f));
        String hint = workflowShapeAdjustable(input)
                ? "SHIFT+方向 调整尺寸 · OK 下一格 · EXE 计算"
                : "OK 下一格 · EXE 计算";
        canvas.drawText(ellipsize(hint, lcd.width() - dp(12)), lcd.right - dp(6),
                lcd.bottom - dp(3), paint);
    }
'''
new = '''        paint.setStyle(Paint.Style.FILL);
        drawWorkflowActionBar(canvas, lcd, input);
    }

    private void drawWorkflowActionBar(Canvas canvas, RectF lcd,
                                       CnCwWorkflowSession.Snapshot input) {
        List<CnCwWorkflowAction> actions = input.actions();
        for (int index = 0; index < actions.size(); index++) {
            CnCwWorkflowAction action = actions.get(index);
            RectF bounds = workflowActionBounds(lcd, actions.size(), index);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(action.enabled() ? Color.argb(42, 24, 58, 45)
                    : Color.argb(14, 24, 58, 45));
            canvas.drawRoundRect(bounds, dp(2f), dp(2f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(0.65f));
            paint.setColor(action.enabled() ? Color.argb(145, 24, 58, 45)
                    : Color.argb(50, 24, 58, 45));
            canvas.drawRoundRect(bounds, dp(2f), dp(2f), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(action.enabled() ? LCD_INK : Color.argb(105, 24, 58, 45));
            paint.setTypeface(action.type() == CnCwWorkflowAction.Type.EXECUTE
                    ? FACE_BOLD : FACE_NORMAL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(8.2f));
            canvas.drawText(ellipsize(action.label(), bounds.width() - dp(5f)),
                    bounds.centerX(), centeredBaseline(bounds.top, bounds.bottom), paint);
        }
    }
'''
replace_once(p, old, new, 'draw action bar')

# Touch action bar before cell hit-testing.
replace_once(p,
'''                    if (state.hasWorkflowInput() && !state.resultShown()) {
                        selectWorkflowCellAt(event.getX(), event.getY());
                        return true;
                    }
''',
'''                    if (state.hasWorkflowInput() && !state.resultShown()) {
                        if (!selectWorkflowActionAt(event.getX(), event.getY())) {
                            selectWorkflowCellAt(event.getX(), event.getY());
                        }
                        return true;
                    }
''', 'workflow touch route')

marker = '''    /** Renders core-owned application results without parsing display strings. */
'''
helper = '''    private boolean selectWorkflowActionAt(float x, float y) {
        if (!state.hasWorkflowInput() || state.resultShown()) return false;
        CnCwWorkflowSession.Snapshot input = state.workflowInput();
        List<CnCwWorkflowAction> actions = input.actions();
        RectF lcd = displayBounds(getWidth());
        for (int index = 0; index < actions.size(); index++) {
            RectF bounds = workflowActionBounds(lcd, actions.size(), index);
            if (!bounds.contains(x, y)) continue;
            CnCwWorkflowAction action = actions.get(index);
            if (!action.enabled()) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                return true;
            }
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (action.type() == CnCwWorkflowAction.Type.EXECUTE) {
                dispatchKey(CnCwKey.EXE);
            } else if (action.type() == CnCwWorkflowAction.Type.BACK) {
                dispatchKey(CnCwKey.BACK);
            } else {
                inputRevision++;
                cancelPendingEvaluation();
                state = machine.performWorkflowAction(action.type());
                postInvalidateOnAnimation();
            }
            return true;
        }
        return false;
    }

'''
text = p.read_text(encoding='utf-8')
if marker not in text:
    raise SystemExit('workflow action touch marker not found')
p.write_text(text.replace(marker, helper + marker, 1), encoding='utf-8')

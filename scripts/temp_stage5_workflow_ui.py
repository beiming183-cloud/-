from pathlib import Path

root = Path(__file__).resolve().parents[1]

def replace_once(text, old, new, name):
    if old not in text:
        raise SystemExit(f"target not found: {name}")
    return text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# UiState: publish immutable structured-input snapshot.
# ---------------------------------------------------------------------------
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwUiState.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''    /** Structured application result; null for ordinary/text-only results. */
    private final CnCwModeEngine.ModeResult applicationResult;
    private final double ans;
''',
'''    /** Structured application result; null for ordinary/text-only results. */
    private final CnCwModeEngine.ModeResult applicationResult;
    /** Stage 5 immutable input-table snapshot; null for the legacy editor. */
    private final CnCwWorkflowSession.Snapshot workflowInput;
    private final double ans;
''', 'ui field')
text = replace_once(text,
'''                String result,
                CnCwModeEngine.ModeResult applicationResult,
                double ans,
''',
'''                String result,
                CnCwModeEngine.ModeResult applicationResult,
                CnCwWorkflowSession.Snapshot workflowInput,
                double ans,
''', 'ui ctor parameter')
text = replace_once(text,
'''        this.result = result == null ? "" : result;
        this.applicationResult = applicationResult;
        this.ans = ans;
''',
'''        this.result = result == null ? "" : result;
        this.applicationResult = applicationResult;
        this.workflowInput = workflowInput;
        this.ans = ans;
''', 'ui ctor assignment')
text = replace_once(text,
'''    public CnCwModeEngine.ModeResult applicationResult() { return applicationResult; }
    public boolean hasStructuredApplicationResult() {
''',
'''    public CnCwModeEngine.ModeResult applicationResult() { return applicationResult; }
    public CnCwWorkflowSession.Snapshot workflowInput() { return workflowInput; }
    public boolean hasWorkflowInput() { return workflowInput != null; }
    public boolean hasStructuredApplicationResult() {
''', 'ui accessor')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Machine: own session lifecycle, table navigation, and evaluator handoff.
# ---------------------------------------------------------------------------
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''    /** Last core-owned application result; renderer sees it only while resultShown. */
    private CnCwModeEngine.ModeResult applicationResult;
    private String status = "HOME";
''',
'''    /** Last core-owned application result; renderer sees it only while resultShown. */
    private CnCwModeEngine.ModeResult applicationResult;
    /** Stage 5 structured input editor; null keeps the legacy expression bridge. */
    private CnCwWorkflowSession workflowSession;
    private String status = "HOME";
''', 'machine field')
text = replace_once(text,
'''        result = source.result;
        applicationResult = source.applicationResult;
        status = source.status;
''',
'''        result = source.result;
        applicationResult = source.applicationResult;
        workflowSession = source.workflowSession == null ? null : source.workflowSession.copy();
        status = source.status;
''', 'machine copy')

# Lifecycle clears. clearExpression intentionally does NOT destroy the active workflow.
for signature in ['public CnCwUiState reset() {', '    private void showHome() {',
                  '    private void powerOff() {', '    private void openApplication(ApplicationMode mode) {']:
    if signature not in text:
        raise SystemExit(f'lifecycle target not found: {signature}')
    indent = '' if signature.startswith('public') else '    '
    replacement = signature + '\n' + ('        ' if signature.startswith('public') else '        ') + 'workflowSession = null;\n' + ('        ' if signature.startswith('public') else '        ') + 'applicationResult = null;'
    text = text.replace(signature, replacement, 1)

# Make clear-expression drop stale structured result but retain active input session.
text = replace_once(text,
'''    private void clearExpression() {
        rememberUndo();
''',
'''    private void clearExpression() {
        rememberUndo();
        applicationResult = null;
''', 'clear expression app result')

# Structured command entry: command selection opens new table; direct typing remains fallback.
text = replace_once(text,
'''        activeCommandId = command.id();
        status = workflowPrompt(application, command);
        if (application == ApplicationMode.SPREADSHEET && command.id().equals("sheet")) {
''',
'''        activeCommandId = command.id();
        CnCwWorkflowSpec.WorkflowSpec workflowSpec =
                CnCwWorkflowSpec.forCommand(application, command.id());
        workflowSession = workflowSpec == null ? null : CnCwWorkflowSession.create(workflowSpec);
        if (workflowSession != null) {
            status = workflowStatus();
            return;
        }
        status = workflowPrompt(application, command);
        if (application == ApplicationMode.SPREADSHEET && command.id().equals("sheet")) {
''', 'begin mode command')
text = replace_once(text,
'''                    if (isEntryKey(key)) {
                        applicationLanding = false;
                        selectedIndex = 0;
                        insertKey(key);
''',
'''                    if (isEntryKey(key)) {
                        workflowSession = null;
                        applicationLanding = false;
                        selectedIndex = 0;
                        insertKey(key);
''', 'legacy direct entry')

# Route workflow keys before ordinary editor BACK/AC/arrows.
text = replace_once(text,
'''        switch (key) {
            case SETTINGS -> { openPopup(CnCwScreen.SETTINGS); return; }
''',
'''        if (workflowSession != null && handleWorkflowSessionKey(key)) return;

        switch (key) {
            case SETTINGS -> { openPopup(CnCwScreen.SETTINGS); return; }
''', 'workflow key route')

# Publish workflow snapshot.
text = replace_once(text,
'''                result, resultShown ? applicationResult : null,
                ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,
''',
'''                result, resultShown ? applicationResult : null,
                workflowSession == null ? null : workflowSession.snapshot(),
                ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,
''', 'publish snapshot')

# Insert public touch API + workflow editing helpers before spreadsheet movement helpers.
marker = '''    private void moveSpreadsheetCell(int rowDelta, int columnDelta) {
'''
helpers = r'''    /** Direct-touch entry point for a Stage 5 input-table cell. */
    public CnCwUiState selectWorkflowCell(int row, int column) {
        if (workflowSession == null || resultShown) return state;
        commitWorkflowCell();
        if (workflowSession.selectCell(row, column)) loadWorkflowCell();
        status = workflowStatus();
        publish();
        return state;
    }

    private boolean handleWorkflowSessionKey(CnCwKey key) {
        if (workflowSession == null) return false;

        if (key == CnCwKey.BACK) {
            if (resultShown) {
                result = "";
                resultShown = false;
                applicationResult = null;
                loadWorkflowCell();
                status = workflowStatus();
            } else if (!tokens.isEmpty()) {
                tokens.clear();
                cursor = 0;
                semanticCursorOverride = null;
                workflowSession.setSelectedCell("");
                status = workflowStatus();
            } else {
                workflowSession = null;
                activeCommandId = "";
                applicationLanding = true;
                status = applicationStatus();
            }
            return true;
        }

        if (key == CnCwKey.AC) {
            if (shiftArmed) {
                powerOff();
            } else {
                tokens.clear();
                cursor = 0;
                semanticCursorOverride = null;
                workflowSession.setSelectedCell("");
                result = "";
                resultShown = false;
                applicationResult = null;
                status = workflowStatus();
            }
            shiftArmed = false;
            return true;
        }

        if (shiftArmed && (key == CnCwKey.UP || key == CnCwKey.DOWN
                || key == CnCwKey.LEFT || key == CnCwKey.RIGHT)) {
            commitWorkflowCell();
            boolean changed = resizeWorkflowFromShift(key);
            shiftArmed = false;
            if (changed) loadWorkflowCell();
            status = changed ? workflowStatus() : "已到输入尺寸边界";
            return true;
        }

        if (key == CnCwKey.UP || key == CnCwKey.DOWN) {
            commitWorkflowCell();
            if (workflowSession.move(key == CnCwKey.UP ? -1 : 1, 0)) loadWorkflowCell();
            status = workflowStatus();
            return true;
        }

        if (key == CnCwKey.LEFT && !selectionActive() && cursor == 0) {
            commitWorkflowCell();
            if (workflowSession.move(0, -1)) {
                loadWorkflowCell();
                status = workflowStatus();
                return true;
            }
            return false;
        }
        if (key == CnCwKey.RIGHT && !selectionActive() && cursor == tokens.size()) {
            commitWorkflowCell();
            if (workflowSession.move(0, 1)) {
                loadWorkflowCell();
                status = workflowStatus();
                return true;
            }
            return false;
        }

        if (key == CnCwKey.OK || key == CnCwKey.ENTER) {
            commitWorkflowCell();
            advanceWorkflowCell();
            loadWorkflowCell();
            status = workflowStatus();
            return true;
        }

        if (key == CnCwKey.EXE) {
            shiftArmed = false;
            commitWorkflowCell();
            if (!workflowSession.isComplete()) {
                workflowSession.selectFirstBlank();
                loadWorkflowCell();
                status = "还有空白项 · " + workflowStatus();
                return true;
            }
            String source = workflowSession.legacySource();
            List<Token> imported = parsePastedTokens(source);
            if (imported == null) {
                status = "输入包含无法识别的内容";
                return true;
            }
            tokens.clear();
            tokens.addAll(imported);
            cursor = tokens.size();
            semanticCursorOverride = null;
            clearSelection();
            evaluate(false);
            return true;
        }
        return false;
    }

    private void commitWorkflowCell() {
        if (workflowSession == null || resultShown) return;
        String source = evaluationSource().replace("\u2063", "");
        workflowSession.setSelectedCell(source);
    }

    private void loadWorkflowCell() {
        if (workflowSession == null) return;
        tokens.clear();
        semanticCursorOverride = null;
        clearSelection();
        String source = workflowSession.selectedCell();
        if (!com.codex.fx991.core.Compat.isBlank(source)) {
            List<Token> imported = parsePastedTokens(source);
            if (imported != null) tokens.addAll(imported);
        }
        cursor = tokens.size();
        result = "";
        resultShown = false;
        applicationResult = null;
        errorShown = false;
        lastError = null;
    }

    private void advanceWorkflowCell() {
        int row = workflowSession.selectedRow();
        int column = workflowSession.selectedColumn();
        if (column + 1 < workflowSession.columns()) {
            workflowSession.selectCell(row, column + 1);
            return;
        }
        if (row + 1 < workflowSession.rows()) {
            workflowSession.selectCell(row + 1, 0);
            return;
        }
        CnCwWorkflowSpec.InputLayout layout = workflowSession.spec().layout();
        if ((layout == CnCwWorkflowSpec.InputLayout.SERIES
                || layout == CnCwWorkflowSpec.InputLayout.PAIRED_SERIES)
                && workflowSession.appendRow()) {
            workflowSession.selectCell(workflowSession.rows() - 1, 0);
        }
    }

    private boolean resizeWorkflowFromShift(CnCwKey key) {
        CnCwWorkflowSpec.InputLayout layout = workflowSession.spec().layout();
        int rows = workflowSession.rows();
        int columns = workflowSession.columns();
        if (layout == CnCwWorkflowSpec.InputLayout.GRID
                || layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET) {
            if (key == CnCwKey.UP) rows--;
            else if (key == CnCwKey.DOWN) rows++;
            else if (key == CnCwKey.LEFT) columns--;
            else if (key == CnCwKey.RIGHT) columns++;
            return workflowSession.resizeGrid(rows, columns);
        }
        if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS
                && workflowSession.spec().mode() == ApplicationMode.EQUATION) {
            if ("simultaneous".equals(workflowSession.spec().commandId())) {
                if (key == CnCwKey.UP) return workflowSession.setEquationDimension(rows - 1);
                if (key == CnCwKey.DOWN) return workflowSession.setEquationDimension(rows + 1);
                return false;
            }
            if (key == CnCwKey.UP) return workflowSession.resizeGrid(rows - 1, columns);
            if (key == CnCwKey.DOWN) return workflowSession.resizeGrid(rows + 1, columns);
        }
        return false;
    }

    private String workflowStatus() {
        if (workflowSession == null) return applicationStatus();
        return workflowSession.spec().title() + " · "
                + (workflowSession.selectedRow() + 1) + ","
                + (workflowSession.selectedColumn() + 1);
    }

'''
if marker not in text:
    raise SystemExit('workflow helper marker not found')
text = text.replace(marker, helpers + marker, 1)
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Android: dedicated input table and direct cell taps.
# ---------------------------------------------------------------------------
p = root / 'app/src/main/java/com/codex/fx991smooth/CalculatorView.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''import com.codex.fx991.core.cw.CnCwUiState;
import com.codex.fx991.core.mode.CnCwModel;
''',
'''import com.codex.fx991.core.cw.CnCwUiState;
import com.codex.fx991.core.cw.CnCwWorkflowSession;
import com.codex.fx991.core.cw.CnCwWorkflowSpec;
import com.codex.fx991.core.mode.CnCwModel;
''', 'view imports')
text = replace_once(text,
'''        if (state.applicationLanding()) {
            drawModeLanding(canvas, lcd);
            return;
        }
        float contentTop = lcd.top + lcd.height() * 0.145f;
''',
'''        if (state.applicationLanding()) {
            drawModeLanding(canvas, lcd);
            return;
        }
        if (state.hasWorkflowInput() && !state.resultShown()) {
            drawWorkflowInput(canvas, lcd, state.workflowInput());
            return;
        }
        float contentTop = lcd.top + lcd.height() * 0.145f;
''', 'view application branch')

# Table renderer before structured-result renderer.
marker = '''    /** Renders core-owned application results without parsing display strings. */
'''
methods = r'''    /** Draws a Stage 5 core-owned structured input editor. */
    private void drawWorkflowInput(Canvas canvas, RectF lcd,
                                   CnCwWorkflowSession.Snapshot input) {
        RectF grid = workflowGridBounds(lcd);
        int visibleRows = Math.min(5, input.rows());
        int startRow = Math.max(0, Math.min(input.selectedRow() - visibleRows / 2,
                input.rows() - visibleRows));
        float rowHeader = dp(24f);
        float headerHeight = dp(18f);
        float cellWidth = (grid.width() - rowHeader) / input.columns();
        float cellHeight = (grid.height() - headerHeight) / visibleRows;

        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(11f));
        canvas.drawText(input.spec().title(), grid.left, grid.top - dp(6f), paint);

        for (int column = 0; column < input.columns(); column++) {
            float left = grid.left + rowHeader + column * cellWidth;
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(9.5f));
            canvas.drawText(workflowColumnLabel(input, column), left + cellWidth * 0.5f,
                    centeredBaseline(grid.top, grid.top + headerHeight), paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.65f));
        paint.setColor(Color.argb(115, 24, 58, 45));
        for (int vr = 0; vr < visibleRows; vr++) {
            int row = startRow + vr;
            float top = grid.top + headerHeight + vr * cellHeight;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(8.5f));
            canvas.drawText(workflowRowLabel(input, row), grid.left + rowHeader * 0.45f,
                    centeredBaseline(top, top + cellHeight), paint);
            for (int column = 0; column < input.columns(); column++) {
                float left = grid.left + rowHeader + column * cellWidth;
                boolean selected = row == input.selectedRow() && column == input.selectedColumn();
                scratch.set(left, top, left + cellWidth, top + cellHeight);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(selected ? Color.argb(56, 24, 58, 45)
                        : Color.argb(12, 24, 58, 45));
                canvas.drawRect(scratch, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(selected ? 1.1f : 0.55f));
                paint.setColor(Color.argb(selected ? 190 : 85, 24, 58, 45));
                canvas.drawRect(scratch, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(LCD_INK);
                paint.setTypeface(selected ? FACE_MEDIUM : FACE_NORMAL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(sp(10.5f));
                String value = selected ? cleanClipboardText(state.displayText())
                        : input.cell(row, column);
                canvas.drawText(ellipsize(value, cellWidth - dp(5f)),
                        scratch.centerX(), centeredBaseline(top, top + cellHeight), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
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

    private RectF workflowGridBounds(RectF lcd) {
        float top = lcd.top + lcd.height() * 0.22f;
        float bottom = lcd.bottom - dp(18f);
        return new RectF(lcd.left + dp(6f), top, lcd.right - dp(6f), bottom);
    }

    private String workflowColumnLabel(CnCwWorkflowSession.Snapshot input, int column) {
        CnCwWorkflowSpec.InputLayout layout = input.spec().layout();
        if ((layout == CnCwWorkflowSpec.InputLayout.SERIES
                || layout == CnCwWorkflowSpec.InputLayout.PAIRED_SERIES
                || layout == CnCwWorkflowSpec.InputLayout.FIXED_FIELDS)
                && column < input.spec().fields().size()) {
            return input.spec().fields().get(column).label();
        }
        if (layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET) {
            return column == 0 ? "x" : column == 1 ? "y" : "z";
        }
        if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS
                && "simultaneous".equals(input.spec().commandId())) {
            return column == input.columns() - 1 ? "b" : "x" + (column + 1);
        }
        if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS) return "系数";
        return Integer.toString(column + 1);
    }

    private String workflowRowLabel(CnCwWorkflowSession.Snapshot input, int row) {
        CnCwWorkflowSpec.InputLayout layout = input.spec().layout();
        if (layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET) return "v" + (row + 1);
        if (layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS
                && "polynomial".equals(input.spec().commandId())) {
            return "a" + (input.rows() - 1 - row);
        }
        return Integer.toString(row + 1);
    }

    private boolean workflowShapeAdjustable(CnCwWorkflowSession.Snapshot input) {
        CnCwWorkflowSpec.InputLayout layout = input.spec().layout();
        return layout == CnCwWorkflowSpec.InputLayout.GRID
                || layout == CnCwWorkflowSpec.InputLayout.VECTOR_SET
                || layout == CnCwWorkflowSpec.InputLayout.COEFFICIENTS;
    }

    private boolean selectWorkflowCellAt(float x, float y) {
        if (!state.hasWorkflowInput() || state.resultShown()) return false;
        CnCwWorkflowSession.Snapshot input = state.workflowInput();
        RectF lcd = displayBounds(getWidth());
        RectF grid = workflowGridBounds(lcd);
        int visibleRows = Math.min(5, input.rows());
        int startRow = Math.max(0, Math.min(input.selectedRow() - visibleRows / 2,
                input.rows() - visibleRows));
        float rowHeader = dp(24f);
        float headerHeight = dp(18f);
        if (x < grid.left + rowHeader || x > grid.right
                || y < grid.top + headerHeight || y > grid.bottom) return false;
        float cellWidth = (grid.width() - rowHeader) / input.columns();
        float cellHeight = (grid.height() - headerHeight) / visibleRows;
        int column = Math.min(input.columns() - 1,
                Math.max(0, (int) ((x - grid.left - rowHeader) / cellWidth)));
        int visibleRow = Math.min(visibleRows - 1,
                Math.max(0, (int) ((y - grid.top - headerHeight) / cellHeight)));
        int row = startRow + visibleRow;
        state = machine.selectWorkflowCell(row, column);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        postInvalidateOnAnimation();
        return true;
    }

'''
if marker not in text:
    raise SystemExit('view workflow renderer marker not found')
text = text.replace(marker, methods + marker, 1)

# Consume display taps as cell taps while a dedicated workflow page is active.
text = replace_once(text,
'''                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && displayBounds(getWidth()).contains(event.getX(), event.getY())) {
                    displayPressed = true;
''',
'''                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && displayBounds(getWidth()).contains(event.getX(), event.getY())) {
                    if (state.hasWorkflowInput() && !state.resultShown()) {
                        selectWorkflowCellAt(event.getX(), event.getY());
                        return true;
                    }
                    displayPressed = true;
''', 'view workflow touch')
p.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Machine regression: structured path + legacy fallback + snapshot isolation.
# ---------------------------------------------------------------------------
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java'
text = p.read_text(encoding='utf-8')
text = replace_once(text,
'''        structuredModesReachCoreEngines();
        spreadsheetCompactWorkflowPersistsCells();
''',
'''        structuredModesReachCoreEngines();
        structuredWorkflowInputPageRunsEndToEnd();
        spreadsheetCompactWorkflowPersistsCells();
''', 'machine suite run')
marker = '''    private void spreadsheetCompactWorkflowPersistsCells() {
'''
test_method = r'''    private void structuredWorkflowInputPageRunsEndToEnd() {
        CnCwMachine statistics = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        statistics.dispatch(CnCwKey.OK); // One-variable structured command.
        check(statistics.state().hasWorkflowInput(),
                "selecting statistics command opens structured input page");
        equal(1, statistics.state().workflowInput().rows(),
                "one-variable input starts with one row");
        press(statistics, CnCwKey.DIGIT_1, CnCwKey.OK,
                CnCwKey.DIGIT_2, CnCwKey.OK,
                CnCwKey.DIGIT_3, CnCwKey.EXE);
        near(2.0, statistics.state().ans(), 0.0,
                "structured statistics input evaluates through existing engine");
        check(statistics.state().hasStructuredApplicationResult(),
                "structured input keeps Stage 4 result protocol");
        statistics.dispatch(CnCwKey.BACK);
        check(statistics.state().hasWorkflowInput() && !statistics.state().resultShown(),
                "BACK from result restores structured input page");
        equal("3", statistics.state().workflowInput().cell(2, 0),
                "structured input data survives result inspection");

        CnCwMachine touch = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        touch.dispatch(CnCwKey.OK);
        press(touch, CnCwKey.DIGIT_4, CnCwKey.OK, CnCwKey.DIGIT_5);
        touch.selectWorkflowCell(0, 0);
        equal("4", touch.state().displayText().replace("│", ""),
                "direct cell selection restores the selected cell editor");
        CnCwMachine isolated = touch.copyForEvaluation();
        isolated.selectWorkflowCell(1, 0);
        equal(0, touch.state().workflowInput().selectedRow(),
                "evaluation snapshot owns a deep workflow-session copy");

        CnCwMachine legacy = homeApplication(CnCwModel.FX_991_CN_CW, 1);
        legacy.dispatch(CnCwKey.DIGIT_1); // Direct typing from landing is compatibility path.
        check(!legacy.state().hasWorkflowInput(),
                "direct typing from application landing preserves legacy bridge");
    }

'''
if marker not in text:
    raise SystemExit('machine workflow test marker not found')
text = text.replace(marker, test_method + marker, 1)
p.write_text(text, encoding='utf-8')

print('Stage 5 Machine/UiState/Android workflow input patch applied')

from pathlib import Path

root = Path(__file__).resolve().parents[1]

# ---------------------------------------------------------------------------
# Core UI snapshot: expose the core-owned mode result without parsing strings.
# ---------------------------------------------------------------------------
ui_path = root / "core/src/main/java/com/codex/fx991/core/cw/CnCwUiState.java"
text = ui_path.read_text(encoding="utf-8")
old = '''    private final String result;
    private final double ans;
'''
new = '''    private final String result;
    /** Structured application result; null for ordinary/text-only results. */
    private final CnCwModeEngine.ModeResult applicationResult;
    private final double ans;
'''
if old not in text:
    raise SystemExit("CnCwUiState result field target not found")
text = text.replace(old, new, 1)

old = '''                CnCwCursorPath semanticSelectionFocus,
                String result,
                double ans,
'''
new = '''                CnCwCursorPath semanticSelectionFocus,
                String result,
                CnCwModeEngine.ModeResult applicationResult,
                double ans,
'''
if old not in text:
    raise SystemExit("CnCwUiState constructor parameter target not found")
text = text.replace(old, new, 1)

old = '''        this.result = result == null ? "" : result;
        this.ans = ans;
'''
new = '''        this.result = result == null ? "" : result;
        this.applicationResult = applicationResult;
        this.ans = ans;
'''
if old not in text:
    raise SystemExit("CnCwUiState constructor assignment target not found")
text = text.replace(old, new, 1)

old = '''    public String result() { return result; }
    public double ans() { return ans; }
'''
new = '''    public String result() { return result; }
    public CnCwModeEngine.ModeResult applicationResult() { return applicationResult; }
    public boolean hasStructuredApplicationResult() {
        return applicationResult != null
                && applicationResult.layout() != CnCwModeEngine.ResultLayout.TEXT;
    }
    public double ans() { return ans; }
'''
if old not in text:
    raise SystemExit("CnCwUiState result accessor target not found")
ui_path.write_text(text.replace(old, new, 1), encoding="utf-8")

# ---------------------------------------------------------------------------
# Machine: retain the structured result through publish/history/snapshots.
# ---------------------------------------------------------------------------
machine_path = root / "core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java"
text = machine_path.read_text(encoding="utf-8")
old = '''    private String result = "";
    private String status = "HOME";
'''
new = '''    private String result = "";
    /** Last core-owned application result; renderer sees it only while resultShown. */
    private CnCwModeEngine.ModeResult applicationResult;
    private String status = "HOME";
'''
if old not in text:
    raise SystemExit("CnCwMachine result field target not found")
text = text.replace(old, new, 1)

old = '''        result = source.result;
        status = source.status;
'''
new = '''        result = source.result;
        applicationResult = source.applicationResult;
        status = source.status;
'''
if old not in text:
    raise SystemExit("CnCwMachine copy result target not found")
text = text.replace(old, new, 1)

old = '''    private void evaluate(boolean forceDecimal) {
        if (tokens.isEmpty()) return;
        String source = autoCloseParentheses(evaluationSource());
'''
new = '''    private void evaluate(boolean forceDecimal) {
        if (tokens.isEmpty()) return;
        applicationResult = null;
        String source = autoCloseParentheses(evaluationSource());
'''
if old not in text:
    raise SystemExit("CnCwMachine evaluate start target not found")
text = text.replace(old, new, 1)

old = '''                CnCwModeEngine.ModeResult modeResult = spreadsheetWorkflow(plainSource);
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
'''
new = '''                CnCwModeEngine.ModeResult modeResult = spreadsheetWorkflow(plainSource);
                applicationResult = modeResult;
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
'''
if old not in text:
    raise SystemExit("spreadsheet ModeResult target not found")
text = text.replace(old, new, 1)

old = '''                CnCwModeEngine.ModeResult modeResult = CnCwModeEngine.evaluate(
                        application, activeCommandId, plainSource, evaluationContext());
                formatted = modeResult.display();
'''
new = '''                CnCwModeEngine.ModeResult modeResult = CnCwModeEngine.evaluate(
                        application, activeCommandId, plainSource, evaluationContext());
                applicationResult = modeResult;
                formatted = modeResult.display();
'''
if old not in text:
    raise SystemExit("structured ModeResult target not found")
text = text.replace(old, new, 1)

old = '''        result = entry.result;
        resultProcessDisplay = entry.processDisplay;
        resultShown = true;
'''
new = '''        result = entry.result;
        resultProcessDisplay = entry.processDisplay;
        applicationResult = entry.applicationResult;
        resultShown = true;
'''
if old not in text:
    raise SystemExit("history recall target not found")
text = text.replace(old, new, 1)

old = '''            history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result, resultProcessDisplay));
'''
new = '''            history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result,
                    resultProcessDisplay, applicationResult));
'''
if old not in text:
    raise SystemExit("history add target not found")
text = text.replace(old, new, 1)

old = '''                semanticSelectionPath(selectionAnchor), semanticSelectionPath(selectionFocus),
                result, ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,
'''
new = '''                semanticSelectionPath(selectionAnchor), semanticSelectionPath(selectionFocus),
                result, resultShown ? applicationResult : null,
                ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,
'''
if old not in text:
    raise SystemExit("publish CnCwUiState target not found")
text = text.replace(old, new, 1)

old = '''    private record HistoryEntry(List<Token> tokens, String result, String processDisplay) { }
'''
new = '''    private record HistoryEntry(List<Token> tokens, String result, String processDisplay,
                                CnCwModeEngine.ModeResult applicationResult) { }
'''
if old not in text:
    raise SystemExit("HistoryEntry record target not found")
machine_path.write_text(text.replace(old, new, 1), encoding="utf-8")

# ---------------------------------------------------------------------------
# Core regression: UiState must carry structured result, ordinary calculate must not.
# ---------------------------------------------------------------------------
suite_path = root / "core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java"
text = suite_path.read_text(encoding="utf-8")
old = '''import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
'''
new = '''import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
'''
if old not in text:
    raise SystemExit("machine suite import target not found")
text = text.replace(old, new, 1)

old = '''        equal("14", machine.state().result(), "formatted result");

        press(machine, CnCwKey.DIVIDE, CnCwKey.DIGIT_2, CnCwKey.EXE);
'''
new = '''        equal("14", machine.state().result(), "formatted result");
        check(machine.state().applicationResult() == null,
                "ordinary Calculate result does not publish application protocol");

        press(machine, CnCwKey.DIVIDE, CnCwKey.DIGIT_2, CnCwKey.EXE);
'''
if old not in text:
    raise SystemExit("calculate result protocol test target not found")
text = text.replace(old, new, 1)

old = '''        check(statistics.state().result().startsWith("n=3"),
                "statistics workflow returns structured result");

        CnCwMachine distribution = homeApplication(CnCwModel.FX_999_CN_CW, 2);
'''
new = '''        check(statistics.state().result().startsWith("n=3"),
                "statistics workflow returns structured result");
        check(statistics.state().hasStructuredApplicationResult(),
                "statistics UiState carries structured application result");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE,
                statistics.state().applicationResult().layout(),
                "statistics UiState exposes key/value layout");
        equal("一元统计", statistics.state().applicationResult().title(),
                "statistics UiState exposes result title");
        equal("n", statistics.state().applicationResult().items().get(0).label(),
                "statistics UiState preserves ordered result items");

        CnCwMachine distribution = homeApplication(CnCwModel.FX_999_CN_CW, 2);
'''
if old not in text:
    raise SystemExit("structured mode UiState test target not found")
suite_path.write_text(text.replace(old, new, 1), encoding="utf-8")

# ---------------------------------------------------------------------------
# Android: consume the structured protocol for key/value, matrix and vector layouts.
# ---------------------------------------------------------------------------
view_path = root / "app/src/main/java/com/codex/fx991smooth/CalculatorView.java"
text = view_path.read_text(encoding="utf-8")
old = '''import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwScreen;
'''
new = '''import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwModeEngine;
import com.codex.fx991.core.cw.CnCwScreen;
'''
if old not in text:
    raise SystemExit("CalculatorView import target not found")
text = text.replace(old, new, 1)

old = '''        if (state.resultShown() && !state.result().isEmpty()) {
            String[] lines = decimalDisplayResult(state.result()).split("\\\\n", -1);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextAlign(Paint.Align.RIGHT);
            if (lines.length == 1) {
                if (!drawNaturalScientificResult(canvas, lines[0], lcd, contentTop,
                        contentBottom, available)
                        && !drawNaturalFractionResult(canvas, lines[0], lcd, contentTop,
                        contentBottom, available)) {
                    drawFittedResultText(canvas, lines[0], lcd, contentTop,
                            contentBottom, available);
                }
            } else {
                paint.setTextSize(sp(16f));
                int visibleLines = Math.min(2, lines.length);
                for (int index = 0; index < visibleLines; index++) {
                    canvas.drawText(ellipsize(lines[index], available), lcd.right - dp(6),
                            contentTop + (contentBottom - contentTop) * (0.66f + index * 0.24f), paint);
                }
            }
        } else {
'''
new = '''        if (state.resultShown() && state.hasStructuredApplicationResult()) {
            drawStructuredApplicationResult(canvas, state.applicationResult(), lcd,
                    contentTop, contentBottom, available);
        } else if (state.resultShown() && !state.result().isEmpty()) {
            String[] lines = decimalDisplayResult(state.result()).split("\\\\n", -1);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextAlign(Paint.Align.RIGHT);
            if (lines.length == 1) {
                if (!drawNaturalScientificResult(canvas, lines[0], lcd, contentTop,
                        contentBottom, available)
                        && !drawNaturalFractionResult(canvas, lines[0], lcd, contentTop,
                        contentBottom, available)) {
                    drawFittedResultText(canvas, lines[0], lcd, contentTop,
                            contentBottom, available);
                }
            } else {
                paint.setTextSize(sp(16f));
                int visibleLines = Math.min(2, lines.length);
                for (int index = 0; index < visibleLines; index++) {
                    canvas.drawText(ellipsize(lines[index], available), lcd.right - dp(6),
                            contentTop + (contentBottom - contentTop) * (0.66f + index * 0.24f), paint);
                }
            }
        } else {
'''
if old not in text:
    raise SystemExit("CalculatorView result branch target not found")
text = text.replace(old, new, 1)

marker = '''    /** Draws the core-owned expression tree without falling back to caret notation. */
'''
methods = '''    /** Renders core-owned application results without parsing display strings. */
    private void drawStructuredApplicationResult(Canvas canvas, CnCwModeEngine.ModeResult result,
                                                 RectF lcd, float contentTop,
                                                 float contentBottom, float available) {
        if (result == null || result.layout() == CnCwModeEngine.ResultLayout.TEXT) return;
        switch (result.layout()) {
            case KEY_VALUE -> drawKeyValueResult(canvas, result, lcd, contentTop, contentBottom, available);
            case MATRIX, VECTOR -> drawGridResult(canvas, result, lcd, contentTop, contentBottom, available);
            case TABLE -> drawKeyValueResult(canvas, result, lcd, contentTop, contentBottom, available);
            case TEXT -> { }
        }
    }

    private void drawKeyValueResult(Canvas canvas, CnCwModeEngine.ModeResult result,
                                    RectF lcd, float contentTop, float contentBottom,
                                    float available) {
        float resultTop = contentTop + (contentBottom - contentTop) * 0.52f;
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.5f));
        canvas.drawText(ellipsize(result.title(), available * 0.48f),
                lcd.left + dp(6), resultTop, paint);

        List<CnCwModeEngine.ResultItem> items = result.items();
        int count = Math.min(4, items.size());
        if (count == 0) return;
        int columns = count == 1 ? 1 : 2;
        int rows = (count + columns - 1) / columns;
        float top = resultTop + dp(4f);
        float bottom = contentBottom - dp(1f);
        float cellWidth = available / columns;
        float rowHeight = Math.max(dp(14f), (bottom - top) / Math.max(1, rows));
        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int column = index % columns;
            float left = lcd.left + dp(6) + column * cellWidth;
            float centerY = top + row * rowHeight + rowHeight * 0.52f;
            CnCwModeEngine.ResultItem item = items.get(index);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextSize(sp(9.5f));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(item.label() + " =", left, centerY - dp(3f), paint);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp(13.5f));
            canvas.drawText(ellipsize(item.value(), cellWidth - dp(8f)),
                    left, centerY + dp(8f), paint);
        }
    }

    private void drawGridResult(Canvas canvas, CnCwModeEngine.ModeResult result,
                                RectF lcd, float contentTop, float contentBottom,
                                float available) {
        int rows = result.rows();
        int columns = result.columns();
        if (rows <= 0 || columns <= 0 || result.cells().size() != rows * columns) {
            drawKeyValueResult(canvas, result, lcd, contentTop, contentBottom, available);
            return;
        }
        float resultTop = contentTop + (contentBottom - contentTop) * 0.48f;
        float gridBottom = result.items().isEmpty()
                ? contentBottom - dp(2f) : contentBottom - dp(20f);
        float gridLeft = lcd.left + dp(14f);
        float gridRight = lcd.right - dp(14f);
        float gridTop = resultTop + dp(5f);
        float cellWidth = (gridRight - gridLeft) / columns;
        float cellHeight = Math.max(dp(10f), (gridBottom - gridTop) / rows);

        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.5f));
        canvas.drawText(ellipsize(result.title(), available * 0.45f),
                lcd.left + dp(6), resultTop, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.8f));
        float bracket = dp(4f);
        canvas.drawLine(gridLeft - bracket, gridTop, gridLeft, gridTop, paint);
        canvas.drawLine(gridLeft - bracket, gridTop, gridLeft - bracket, gridBottom, paint);
        canvas.drawLine(gridLeft - bracket, gridBottom, gridLeft, gridBottom, paint);
        canvas.drawLine(gridRight, gridTop, gridRight + bracket, gridTop, paint);
        canvas.drawLine(gridRight + bracket, gridTop, gridRight + bracket, gridBottom, paint);
        canvas.drawLine(gridRight, gridBottom, gridRight + bracket, gridBottom, paint);
        paint.setStyle(Paint.Style.FILL);

        float textSize = columns >= 4 || rows >= 4 ? 9.5f : 11.5f;
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextSize(sp(textSize));
        paint.setTextAlign(Paint.Align.CENTER);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                String value = result.cells().get(row * columns + column);
                float cx = gridLeft + (column + 0.5f) * cellWidth;
                float cy = gridTop + (row + 0.5f) * cellHeight;
                canvas.drawText(ellipsize(value, cellWidth - dp(2f)), cx,
                        centeredBaseline(cy - cellHeight * 0.42f, cy + cellHeight * 0.42f), paint);
            }
        }

        if (!result.items().isEmpty()) {
            CnCwModeEngine.ResultItem first = result.items().get(0);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp(11f));
            String summary = first.label() + "=" + first.value();
            if (result.items().size() > 1) {
                CnCwModeEngine.ResultItem second = result.items().get(1);
                summary += "   " + second.label() + "=" + second.value();
            }
            canvas.drawText(ellipsize(summary, available), lcd.right - dp(6),
                    contentBottom - dp(1), paint);
        }
    }

'''
if marker not in text:
    raise SystemExit("CalculatorView structured renderer insertion marker not found")
view_path.write_text(text.replace(marker, methods + marker, 1), encoding="utf-8")

print("Stage 4 Step 4 UI result protocol patch applied")

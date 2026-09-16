package com.codex.fx991smooth;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.codex.fx991.core.cw.CnCwCommand;
import com.codex.fx991.core.cw.CnCwExpressionNode;
import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.cw.CnCwMachine;
import com.codex.fx991.core.cw.CnCwScreen;
import com.codex.fx991.core.cw.CnCwUiState;
import com.codex.fx991.core.mode.CnCwModel;
import com.codex.fx991.core.ui.Cw991LayoutMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.math.BigDecimal;

/**
 * Retained single-canvas Android adapter for the clean-room CN CW state machine.
 *
 * <p>Touch-down commits ordinary keys immediately; EXE uses an isolated
 * background snapshot for heavy evaluation. Pointer-up only releases the
 * pressed visual, so two overlapping keys are committed in down order and can
 * never be duplicated by crossed releases.</p>
 */
public final class CalculatorView extends View {
    private final android.os.Handler gestureHandler = new android.os.Handler();
    private Runnable displayLongPress;
    private Runnable keyRepeat;
    private CnCwKey repeatingKey;
    private int repeatingPointerId = -1;
    private boolean displayPressed;
    private boolean displaySelectionMode;
    private boolean displayLongPressTriggered;
    private float displayDownX;
    private float displayDownY;
    private int lastDragCursor = -1;
    /** -1 = left handle, 0 = choose from drag direction, +1 = right handle. */
    private int selectionDragEdge;
    private boolean selectionTapCandidate;
    private static final int BODY_EDGE = Color.rgb(48, 55, 52);
    /* A warm neutral shell keeps the calculator from looking washed out while
       the cool LCD and ochre function layer remain immediately scannable. */
    private static final int BODY = Color.rgb(242, 243, 237);
    private static final int BODY_SHADOW = Color.rgb(169, 176, 170);
    private static final int LCD_FRAME = Color.rgb(81, 105, 95);
    private static final int LCD = Color.rgb(220, 234, 222);
    private static final int LCD_MID = Color.rgb(190, 211, 198);
    private static final int LCD_DARK = Color.rgb(24, 58, 45);
    private static final int LCD_INK = Color.rgb(13, 38, 28);
    private static final int KEY_FUNCTION = Color.rgb(246, 247, 241);
    private static final int KEY_NUMBER = Color.rgb(255, 255, 250);
    private static final int KEY_OPERATOR = Color.rgb(232, 236, 230);
    private static final int KEY_CONTROL = Color.rgb(226, 231, 226);
    private static final int KEY_NAV = Color.rgb(186, 199, 190);
    private static final int KEY_OK = Color.rgb(165, 184, 173);
    private static final int KEY_SHIFT = Color.rgb(250, 250, 246);
    private static final int KEY_ACTION = Color.rgb(205, 216, 209);
    private static final int KEY_EXECUTE = Color.rgb(163, 185, 173);
    private static final int KEY_BORDER = Color.rgb(163, 173, 166);
    private static final int KEY_HIGHLIGHT = Color.rgb(255, 255, 252);
    private static final int KEY_SHADOW = Color.argb(92, 42, 49, 45);
    private static final int INK_LIGHT = Color.rgb(255, 255, 249);
    private static final int INK_DARK = Color.rgb(18, 30, 24);
    private static final int SHIFT_INK = Color.rgb(137, 102, 13);
    private static final Typeface FACE_NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface FACE_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface FACE_BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final Path iconPath = new Path();
    private final RectF scratch = new RectF();
    private final Rect textBounds = new Rect();
    private final List<KeyHit> hitMap = new ArrayList<>(64);
    /** Geometry/legends are owned by the physical 991 layout, not the painter. */
    private final PhysicalKeyLayout physicalLayout;
    private final CnCwTouchRouter touchRouter = new CnCwTouchRouter();
    private final ExecutorService evaluationExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "cn-cw-evaluator");
        thread.setDaemon(true);
        return thread;
    });
    private CnCwMachine machine;
    private CnCwUiState state;
    private Future<?> pendingEvaluation;
    private long inputRevision;
    private boolean evaluating;

    public CalculatorView(Context context) {
        super(context);
        physicalLayout = new PhysicalKeyLayout(getResources().getDisplayMetrics().density);
        machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        // Launch directly into a blank Calculate work area.  HOME remains
        // available as a deliberate key action, but opening the app must not
        // make a routine calculation pay for a menu transition first.
        state = machine.dispatch(CnCwKey.OK);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setSoundEffectsEnabled(false);
        setHapticFeedbackEnabled(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setKeepScreenOn(false);
        setContentDescription(BuildConfig.MODEL_LABEL
                + " independent scientific calculator, HOME screen");
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        rebuildHitMap(width, height);
    }

    private void rebuildHitMap(int width, int height) {
        hitMap.clear();
        for (PhysicalKeyLayout.Hit hit : physicalLayout.arrange(width, height)) {
            PhysicalKeyLayout.Definition definition = hit.definition;
            hitMap.add(new KeyHit(new KeySpec(definition.key, definition.primary,
                    definition.secondary, adaptKind(definition.kind), definition.circular),
                    new RectF(hit.touchBounds), new RectF(hit.visualBounds), hit.visual));
        }
    }

    private static KeyKind adaptKind(PhysicalKeyLayout.Kind kind) {
        return switch (kind) {
            case NUMBER -> KeyKind.NUMBER;
            case OPERATOR -> KeyKind.OPERATOR;
            case FUNCTION -> KeyKind.FUNCTION;
            case CONTROL, CONTEXT -> KeyKind.STRIP;
            case NAV -> KeyKind.NAV;
            case OK -> KeyKind.OK;
            case SHIFT -> KeyKind.SHIFT;
            case ACTION -> KeyKind.ACTION;
            case EXECUTE -> KeyKind.EQUALS;
        };
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BODY_EDGE);
        paint.setColor(BODY_SHADOW);
        canvas.drawRoundRect(dp(3), dp(5), getWidth() - dp(3), getHeight() + dp(24),
                dp(18), dp(18), paint);
        paint.setColor(BODY);
        canvas.drawRoundRect(dp(4), dp(3), getWidth() - dp(4), getHeight() + dp(18),
                dp(17), dp(17), paint);
        drawDisplay(canvas);
        drawNavigationPlate(canvas);
        drawPageRocker(canvas);
        for (KeyHit hit : hitMap) drawKey(canvas, hit);
    }

    private void drawHeader(Canvas canvas) {
        RectF lcd = displayBounds(getWidth());
        float modelBaseline = Math.max(dp(24), lcd.top - dp(45));
        float subtitleBaseline = modelBaseline + dp(9);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(FACE_BOLD);
        paint.setTextSize(sp(11.5f));
        paint.setColor(Color.rgb(68, 74, 70));
        canvas.drawText("CN-991", dp(18), modelBaseline, paint);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(4.6f));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(99, 105, 101));
        canvas.drawText("SCIENTIFIC CALCULATOR", dp(18), subtitleBaseline, paint);
        float panelWidth = lcd.width() * 0.37f;
        float panelHeight = panelWidth * 0.43f;
        float panelRight = lcd.right - dp(6);
        float panelTop = lcd.top - panelHeight - dp(12);
        scratch.set(panelRight - panelWidth, panelTop, panelRight, panelTop + panelHeight);
        paint.setColor(Color.rgb(91, 96, 87));
        canvas.drawRoundRect(scratch, dp(7), dp(7), paint);
        paint.setColor(Color.rgb(137, 143, 130));
        for (float x = scratch.left + dp(6); x < scratch.right; x += dp(6)) {
            canvas.drawLine(x, scratch.top + dp(3), x + dp(8), scratch.bottom - dp(3), paint);
        }
    }

    private RectF displayBounds(float width) {
        return physicalLayout.displayBounds(width, getHeight());
    }

    private void drawDisplay(Canvas canvas) {
        RectF lcd = displayBounds(getWidth());
        paint.setColor(LCD_FRAME);
        canvas.drawRoundRect(lcd.left - dp(3), lcd.top - dp(3), lcd.right + dp(3),
                lcd.bottom + dp(3), dp(5), dp(5), paint);
        paint.setColor(LCD);
        canvas.drawRoundRect(lcd, dp(2), dp(2), paint);
        canvas.save();
        canvas.clipRect(lcd);
        if (!state.poweredOn()) {
            canvas.restore();
            return;
        }
        if (state.screen() == CnCwScreen.HOME) drawHomeScreen(canvas, lcd);
        else if (state.screen().isPopupMenu()) drawMenuScreen(canvas, lcd);
        else drawApplicationScreen(canvas, lcd);
        canvas.restore();
    }

    private void drawStatusBar(Canvas canvas, RectF lcd, String title) {
        // The physical LCD reserves only a thin indicator band; keeping it
        // short leaves room for the larger natural-display expression below.
        float barHeight = lcd.height() * 0.075f;
        paint.setColor(LCD_MID);
        canvas.drawRect(lcd.left, lcd.top, lcd.right, lcd.top + barHeight, paint);
        if (!title.isEmpty()) {
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_BOLD);
            paint.setTextSize(sp(12f));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(ellipsize(title, lcd.width() * 0.52f),
                    lcd.left + dp(5), centeredBaseline(lcd.top, lcd.top + barHeight), paint);
        }
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(FACE_NORMAL);
        String indicators = (evaluating ? "…  " : "")
                + (state.shiftArmed() ? "S  " : "")
                + (state.verificationMode() ? "✓  " : "")
                + (state.engineeringMode() ? "E  " : "")
                + (state.overwriteMode() ? "O  " : "")
                + (state.statementSequenceActive() ? "▮▮  " : "")
                + (state.historyPrevious() ? "▲" : "")
                + (state.historyNext() ? "▼  " : state.historyPrevious() ? "  " : "")
                + state.settings().angleUnit().name().charAt(0)
                + "  " + compactDisplayMode();
        canvas.drawText(indicators, lcd.right - dp(5),
                centeredBaseline(lcd.top, lcd.top + barHeight), paint);
    }

    private void drawHomeScreen(Canvas canvas, RectF lcd) {
        drawStatusBar(canvas, lcd, "");
        List<CnCwCommand> allItems = state.homeItems();
        List<CnCwCommand> items = state.homeVisibleItems();
        float contentTop = lcd.top + lcd.height() * 0.085f;
        float gap = dp(1);
        int columns = 3;
        int start = state.homeViewportStart();
        int visibleCount = items.size();
        int rows = 2;
        float cellWidth = (lcd.width() - gap * (columns + 1)) / columns;
        float cellHeight = (lcd.bottom - contentTop - gap * (rows + 1)) / rows;
        for (int visibleIndex = 0; visibleIndex < items.size(); visibleIndex++) {
            int index = start + visibleIndex;
            int row = visibleIndex / columns;
            int column = visibleIndex % columns;
            float left = lcd.left + gap + column * (cellWidth + gap);
            float top = contentTop + gap + row * (cellHeight + gap);
            scratch.set(left, top, left + cellWidth, top + cellHeight);
            boolean selected = index == state.selectedIndex();
            if (selected) {
                paint.setColor(LCD_DARK);
                canvas.drawRect(scratch, paint);
            }
            paint.setColor(selected ? LCD : LCD_INK);
            drawApplicationGlyph(canvas, items.get(visibleIndex).id(), scratch,
                    selected ? LCD : LCD_INK);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp(16f));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(items.get(visibleIndex).label(), scratch.centerX(),
                    scratch.bottom - dp(2.8f), paint);
        }
        paint.setColor(Color.argb(92, 22, 37, 31));
        paint.setStrokeWidth(dp(0.55f));
        float gridMidX = lcd.left + lcd.width() / 3f;
        canvas.drawLine(gridMidX, contentTop, gridMidX, lcd.bottom, paint);
        canvas.drawLine(gridMidX * 2f - lcd.left, contentTop,
                gridMidX * 2f - lcd.left, lcd.bottom, paint);
        float gridMidY = contentTop + (lcd.bottom - contentTop) * 0.5f;
        canvas.drawLine(lcd.left, gridMidY, lcd.right, gridMidY, paint);
        if (allItems.size() > visibleCount) {
            drawScrollBar(canvas, lcd, start, visibleCount, allItems.size(), contentTop, lcd.bottom);
        }
    }

    private void drawApplicationGlyph(Canvas canvas, String id, RectF cell, int color) {
        float cx = cell.centerX();
        float cy = cell.top + cell.height() * 0.36f;
        float radius = Math.min(cell.width(), cell.height()) * 0.18f;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        if ("CALCULATE".equals(id)) {
            canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
            canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
            canvas.drawCircle(cx + radius * 1.45f, cy, dp(0.8f), paint);
        } else if ("STATISTICS".equals(id) || "DISTRIBUTION".equals(id)) {
            canvas.drawLine(cx - radius * 1.5f, cy + radius, cx + radius * 1.5f, cy + radius, paint);
            canvas.drawRect(cx - radius * 1.2f, cy, cx - radius * 0.6f, cy + radius, paint);
            canvas.drawRect(cx - radius * 0.3f, cy - radius * 0.7f,
                    cx + radius * 0.3f, cy + radius, paint);
            canvas.drawRect(cx + radius * 0.6f, cy - radius * 0.2f,
                    cx + radius * 1.2f, cy + radius, paint);
        } else if ("SPREADSHEET".equals(id) || "FUNCTION_TABLE".equals(id)) {
            canvas.drawRect(cx - radius * 1.4f, cy - radius, cx + radius * 1.4f, cy + radius, paint);
            canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
            canvas.drawLine(cx - radius * 1.4f, cy, cx + radius * 1.4f, cy, paint);
        } else if ("MATRIX".equals(id) || "VECTOR".equals(id)) {
            iconPath.reset();
            iconPath.moveTo(cx - radius, cy - radius);
            iconPath.lineTo(cx - radius * 1.4f, cy - radius);
            iconPath.lineTo(cx - radius * 1.4f, cy + radius);
            iconPath.lineTo(cx - radius, cy + radius);
            iconPath.moveTo(cx + radius, cy - radius);
            iconPath.lineTo(cx + radius * 1.4f, cy - radius);
            iconPath.lineTo(cx + radius * 1.4f, cy + radius);
            iconPath.lineTo(cx + radius, cy + radius);
            canvas.drawPath(iconPath, paint);
            canvas.drawCircle(cx, cy, dp(1.3f), paint);
        } else {
            canvas.drawCircle(cx, cy, radius * 1.15f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(FACE_BOLD);
            paint.setTextSize(sp(7.5f));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(id.substring(0, 1), cx,
                    centeredBaseline(cy - radius, cy + radius), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawMenuScreen(Canvas canvas, RectF lcd) {
        drawStatusBar(canvas, lcd, state.status());
        List<CnCwCommand> items = state.menuItems();
        if (items.isEmpty()) return;
        float top = lcd.top + lcd.height() * 0.145f;
        float bottom = lcd.bottom - dp(2);
        int visible = Math.min(5, items.size());
        int start = Math.max(0, Math.min(state.selectedIndex() - visible + 1,
                items.size() - visible));
        float rowHeight = (bottom - top) / visible;
        for (int visibleRow = 0; visibleRow < visible; visibleRow++) {
            int index = start + visibleRow;
            float rowTop = top + visibleRow * rowHeight;
            boolean selected = index == state.selectedIndex();
            if (selected) {
                paint.setColor(LCD_DARK);
                canvas.drawRect(lcd.left + dp(2), rowTop,
                        lcd.right - dp(2), rowTop + rowHeight, paint);
            }
            paint.setColor(selected ? LCD : LCD_INK);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp(18f));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(items.get(index).label(), lcd.left + dp(6),
                    centeredBaseline(rowTop, rowTop + rowHeight), paint);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextSize(sp(12f));
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(ellipsize(items.get(index).description(), lcd.width() * 0.47f),
                    lcd.right - dp(7), centeredBaseline(rowTop, rowTop + rowHeight), paint);
        }
        if (items.size() > visible) drawScrollBar(canvas, lcd, start, visible, items.size(), top, bottom);
    }

    private void drawApplicationScreen(Canvas canvas, RectF lcd) {
        String title = state.application() == null ? "计算" : state.application().chineseName();
        drawStatusBar(canvas, lcd, title);
        if (state.spreadsheetGrid()) {
            drawSpreadsheetGrid(canvas, lcd);
            return;
        }
        if (state.applicationLanding()) {
            drawModeLanding(canvas, lcd);
            return;
        }
        float contentTop = lcd.top + lcd.height() * 0.145f;
        float contentBottom = lcd.bottom - dp(3);
        float available = lcd.width() - dp(12);
        drawNaturalExpression(canvas, state.naturalExpression(), lcd, contentTop,
                contentBottom, available);
        if (state.hasSelection()) {
            drawSelectionHandles(canvas, lcd, contentTop, contentBottom);
        }
        if (state.resultShown() && !state.result().isEmpty()) {
            String[] lines = decimalDisplayResult(state.result()).split("\\n", -1);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextAlign(Paint.Align.RIGHT);
            if (lines.length == 1) {
                if (!drawNaturalScientificResult(canvas, lines[0], lcd, contentTop,
                        contentBottom, available)
                        && !drawNaturalFractionResult(canvas, lines[0], lcd, contentTop,
                        contentBottom, available)) {
                    drawFittedResultText(canvas, lines[0], lcd.right - dp(6),
                            contentTop + (contentBottom - contentTop) * 0.86f, available);
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
            paint.setTypeface(FACE_NORMAL);
            paint.setTextSize(sp(14f));
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(ellipsize(state.status(), available * 0.8f), lcd.right - dp(6),
                    contentBottom - dp(1), paint);
        }
    }

    /** Draws the core-owned expression tree without falling back to caret notation. */
    private void drawNaturalExpression(Canvas canvas, CnCwExpressionNode expression,
                                       RectF lcd, float contentTop, float contentBottom,
                                       float available) {
        float baseSize = sp(25f);
        NaturalMetrics metrics = measureNatural(expression, baseSize);
        float viewportLeft = lcd.left + dp(6);
        float cursorOffset = naturalCursorOffset(expression, baseSize);
        float expressionX = viewportLeft;
        if (metrics.width > available) {
            float focus = cursorOffset < 0 ? metrics.width : cursorOffset;
            expressionX = viewportLeft + available * 0.58f - focus;
            expressionX = Math.min(viewportLeft,
                    Math.max(viewportLeft + available - metrics.width, expressionX));
        }
        float baseline = contentTop + metrics.top + dp(2);
        float maximumBaseline = contentTop + (contentBottom - contentTop) * 0.54f;
        drawNaturalNode(canvas, expression, expressionX,
                Math.min(baseline, maximumBaseline), baseSize);
    }

    private NaturalMetrics measureNatural(CnCwExpressionNode node, float textSize) {
        return switch (node.kind()) {
            case TEXT -> naturalTextMetrics(node.text(), textSize);
            case CURSOR -> new NaturalMetrics(Math.max(dp(1.35f), textSize * 0.055f),
                    textSize * 0.78f, textSize * 0.22f);
            case SUPERSCRIPT -> {
                if (node.children().size() < 2) yield measureNaturalRow(node.children(), textSize);
                NaturalMetrics base = measureNatural(node.children().get(0), textSize);
                NaturalMetrics exponent = measureNatural(node.children().get(1), textSize * 0.62f);
                float shift = base.top * 0.54f;
                yield new NaturalMetrics(base.width + exponent.width,
                        Math.max(base.top, shift + exponent.top),
                        Math.max(base.bottom, exponent.bottom - shift));
            }
            case FRACTION -> measureNaturalFraction(node, textSize);
            case ROW, MIXED_FRACTION, RADICAL, NTH_ROOT ->
                    measureNaturalRow(node.children(), textSize);
        };
    }

    private NaturalMetrics measureNaturalFraction(CnCwExpressionNode node, float textSize) {
        if (node.children().size() < 2) return measureNaturalRow(node.children(), textSize);
        float termSize = textSize * 0.70f;
        NaturalMetrics numerator = measureNatural(node.children().get(0), termSize);
        NaturalMetrics denominator = measureNatural(node.children().get(1), termSize);
        float lineOffset = textSize * 0.22f;
        float top = lineOffset + dp(2) + numerator.bottom + numerator.top;
        float bottom = -lineOffset + denominator.top + dp(3) + denominator.bottom;
        return new NaturalMetrics(Math.max(numerator.width, denominator.width) + dp(8),
                top, Math.max(textSize * 0.12f, bottom));
    }

    private NaturalMetrics measureNaturalRow(List<CnCwExpressionNode> children, float textSize) {
        float width = 0f;
        float top = 0f;
        float bottom = 0f;
        for (CnCwExpressionNode child : children) {
            NaturalMetrics childMetrics = measureNatural(child, textSize);
            width += childMetrics.width;
            top = Math.max(top, childMetrics.top);
            bottom = Math.max(bottom, childMetrics.bottom);
        }
        return new NaturalMetrics(width, top, bottom);
    }

    private NaturalMetrics naturalTextMetrics(String text, float textSize) {
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(textSize);
        paint.getFontMetrics(fontMetrics);
        return new NaturalMetrics(paint.measureText(text), -fontMetrics.ascent, fontMetrics.descent);
    }

    private void drawNaturalNode(Canvas canvas, CnCwExpressionNode node,
                                 float left, float baseline, float textSize) {
        switch (node.kind()) {
            case TEXT -> {
                paint.setTypeface(FACE_NORMAL);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setTextSize(textSize);
                if (node.selected()) {
                    paint.getFontMetrics(fontMetrics);
                    float width = paint.measureText(node.text());
                    paint.setColor(LCD_DARK);
                    canvas.drawRect(left - dp(1), baseline + fontMetrics.ascent - dp(2),
                            left + width + dp(1), baseline + fontMetrics.descent + dp(2), paint);
                    paint.setColor(LCD);
                } else {
                    paint.setColor(LCD_INK);
                }
                canvas.drawText(node.text(), left, baseline, paint);
            }
            case CURSOR -> {
                NaturalMetrics metrics = measureNatural(node, textSize);
                paint.setColor(LCD_INK);
                paint.setStrokeWidth(Math.max(dp(1.0f), textSize * 0.045f));
                float cursorX = left + metrics.width * 0.5f;
                canvas.drawLine(cursorX, baseline - metrics.top, cursorX,
                        baseline + metrics.bottom, paint);
            }
            case SUPERSCRIPT -> {
                if (node.children().size() < 2) {
                    drawNaturalRow(canvas, node.children(), left, baseline, textSize);
                    return;
                }
                CnCwExpressionNode baseNode = node.children().get(0);
                CnCwExpressionNode exponentNode = node.children().get(1);
                NaturalMetrics base = measureNatural(baseNode, textSize);
                drawNaturalNode(canvas, baseNode, left, baseline, textSize);
                drawNaturalNode(canvas, exponentNode, left + base.width,
                        baseline - base.top * 0.54f, textSize * 0.62f);
            }
            case FRACTION -> drawNaturalFraction(canvas, node, left, baseline, textSize);
            case ROW, MIXED_FRACTION, RADICAL, NTH_ROOT ->
                    drawNaturalRow(canvas, node.children(), left, baseline, textSize);
        }
    }

    private void drawNaturalFraction(Canvas canvas, CnCwExpressionNode node,
                                     float left, float baseline, float textSize) {
        if (node.children().size() < 2) {
            drawNaturalRow(canvas, node.children(), left, baseline, textSize);
            return;
        }
        float termSize = textSize * 0.70f;
        CnCwExpressionNode numeratorNode = node.children().get(0);
        CnCwExpressionNode denominatorNode = node.children().get(1);
        NaturalMetrics numerator = measureNatural(numeratorNode, termSize);
        NaturalMetrics denominator = measureNatural(denominatorNode, termSize);
        NaturalMetrics fraction = measureNaturalFraction(node, textSize);
        float lineY = baseline - textSize * 0.22f;
        float numeratorLeft = left + (fraction.width - numerator.width) * 0.5f;
        float denominatorLeft = left + (fraction.width - denominator.width) * 0.5f;
        drawNaturalNode(canvas, numeratorNode, numeratorLeft,
                lineY - dp(2) - numerator.bottom, termSize);
        paint.setColor(LCD_INK);
        paint.setStrokeWidth(Math.max(dp(0.8f), textSize * 0.035f));
        canvas.drawLine(left + dp(1.5f), lineY, left + fraction.width - dp(1.5f), lineY, paint);
        drawNaturalNode(canvas, denominatorNode, denominatorLeft,
                lineY + denominator.top + dp(3), termSize);
    }

    private void drawNaturalRow(Canvas canvas, List<CnCwExpressionNode> children,
                                float left, float baseline, float textSize) {
        float x = left;
        for (CnCwExpressionNode child : children) {
            drawNaturalNode(canvas, child, x, baseline, textSize);
            x += measureNatural(child, textSize).width;
        }
    }

    private float naturalCursorOffset(CnCwExpressionNode node, float textSize) {
        if (!node.containsCursor()) return -1f;
        if (node.kind() == CnCwExpressionNode.Kind.CURSOR) return 0f;
        if (node.kind() == CnCwExpressionNode.Kind.SUPERSCRIPT
                && node.children().size() >= 2) {
            CnCwExpressionNode base = node.children().get(0);
            if (base.containsCursor()) return naturalCursorOffset(base, textSize);
            return measureNatural(base, textSize).width
                    + naturalCursorOffset(node.children().get(1), textSize * 0.62f);
        }
        if (node.kind() == CnCwExpressionNode.Kind.FRACTION
                && node.children().size() >= 2) {
            float termSize = textSize * 0.70f;
            CnCwExpressionNode numerator = node.children().get(0);
            CnCwExpressionNode denominator = node.children().get(1);
            NaturalMetrics fraction = measureNaturalFraction(node, textSize);
            if (numerator.containsCursor()) {
                return (fraction.width - measureNatural(numerator, termSize).width) * 0.5f
                        + naturalCursorOffset(numerator, termSize);
            }
            return (fraction.width - measureNatural(denominator, termSize).width) * 0.5f
                    + naturalCursorOffset(denominator, termSize);
        }
        float offset = 0f;
        for (CnCwExpressionNode child : node.children()) {
            if (child.containsCursor()) return offset + naturalCursorOffset(child, textSize);
            offset += measureNatural(child, textSize).width;
        }
        return -1f;
    }

    /** Draws phone-style handles at the two semantic selection boundaries. */
    private void drawSelectionHandles(Canvas canvas, RectF lcd,
                                      float contentTop, float contentBottom) {
        float startX = displayBoundaryX(state.selectionStart());
        float endX = displayBoundaryX(state.selectionEnd());
        float top = contentTop + dp(1.5f);
        float bottom = Math.min(contentBottom - dp(10), contentTop + dp(34));
        paint.setColor(LCD_DARK);
        paint.setStrokeWidth(dp(1.15f));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(startX, top + dp(4), startX, bottom, paint);
        canvas.drawLine(endX, top, endX, bottom - dp(4), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(startX, bottom + dp(2.2f), dp(2.6f), paint);
        canvas.drawCircle(endX, top - dp(2.2f), dp(2.6f), paint);
    }

    /** Draws a result without losing trailing digits to an ellipsis. */
    private void drawFittedResultText(Canvas canvas, String value, float right,
                                      float baseline, float available) {
        String display = value == null ? "" : value;
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextAlign(Paint.Align.RIGHT);

        for (float size = 34f; size >= 16f; size -= 1f) {
            paint.setTextSize(sp(size));
            if (paint.measureText(display) <= available) {
                canvas.drawText(display, right, baseline, paint);
                return;
            }
        }

        String scientific = compactScientificResult(display);
        if (!scientific.equals(display)) {
            display = scientific;
            for (float size = 24f; size >= 14f; size -= 1f) {
                paint.setTextSize(sp(size));
                if (paint.measureText(display) <= available) {
                    canvas.drawText(display, right, baseline, paint);
                    return;
                }
            }
        }

        // Last-resort fit still preserves every character instead of adding an ellipsis.
        for (float size = 13f; size >= 8f; size -= 1f) {
            paint.setTextSize(sp(size));
            if (paint.measureText(display) <= available) {
                canvas.drawText(display, right, baseline, paint);
                return;
            }
        }

        paint.setTextSize(sp(8f));
        float width = Math.max(1f, paint.measureText(display));
        float scale = Math.min(1f, available / width);
        canvas.save();
        canvas.scale(scale, 1f, right, baseline);
        canvas.drawText(display, right, baseline, paint);
        canvas.restore();
    }

    /** Converts only plain numeric results to a compact scientific fallback. */
    private static String compactScientificResult(String value) {
        if (value == null || !value.matches("[−-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][+−-]?\\d+)?")) {
            return value == null ? "" : value;
        }
        try {
            double numeric = Double.parseDouble(value.replace('−', '-'));
            if (!Double.isFinite(numeric) || numeric == 0.0d) return value;
            String scientific = String.format(java.util.Locale.US, "%.9E", numeric);
            int marker = scientific.indexOf('E');
            String mantissa = scientific.substring(0, marker);
            String exponent = scientific.substring(marker + 1);
            while (mantissa.contains(".") && mantissa.endsWith("0")) {
                mantissa = mantissa.substring(0, mantissa.length() - 1);
            }
            if (mantissa.endsWith(".")) mantissa = mantissa.substring(0, mantissa.length() - 1);
            boolean negativeExponent = exponent.startsWith("-");
            exponent = exponent.replace("+", "").replace("-", "");
            while (exponent.length() > 1 && exponent.startsWith("0")) {
                exponent = exponent.substring(1);
            }
            return mantissa + "E" + (negativeExponent ? "-" : "") + exponent;
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    /** Draws engineering and SCI output as a compact mantissa with superscript exponent. */
    private boolean drawNaturalScientificResult(Canvas canvas, String value, RectF lcd,
                                                float contentTop, float contentBottom,
                                                float available) {
        int marker = value.indexOf("\u00d710^");
        int markerLength = 3;
        if (marker < 1) {
            int scientific = value.lastIndexOf('E');
            if (scientific < 1 || scientific + 1 >= value.length()) return false;
            marker = scientific;
            markerLength = 1;
        }
        String mantissa = value.substring(0, marker).trim();
        String exponent = value.substring(marker + markerLength).trim();
        if (exponent.startsWith("(") && exponent.endsWith(")") && exponent.length() > 2) {
            exponent = exponent.substring(1, exponent.length() - 1);
        }
        if (mantissa.isEmpty() || exponent.isEmpty()) return false;

        float baseSize = sp(34f);
        float exponentSize = baseSize * 0.62f;
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextSize(baseSize);
        String base = mantissa + "\u00d710";
        float baseWidth = paint.measureText(base);
        paint.setTextSize(exponentSize);
        float exponentWidth = paint.measureText(exponent);
        if (baseWidth + exponentWidth > available) return false;

        float baseline = contentTop + (contentBottom - contentTop) * 0.86f;
        float left = lcd.right - dp(6) - baseWidth - exponentWidth;
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(baseSize);
        canvas.drawText(base, left, baseline, paint);
        paint.setTextSize(exponentSize);
        canvas.drawText(exponent, left + baseWidth, baseline - baseSize * 0.54f, paint);
        return true;
    }

    /** Draws handheld-style stacked fractions for exact scalar results. */
    private boolean drawNaturalFractionResult(Canvas canvas, String value, RectF lcd,
                                              float contentTop, float contentBottom,
                                              float available) {
        int slash = value.indexOf('/');
        if (slash <= 0 || slash != value.lastIndexOf('/') || slash + 1 >= value.length()) {
            return false;
        }
        String left = value.substring(0, slash).trim();
        String denominator = value.substring(slash + 1).trim();
        if (!isNaturalTerm(denominator)) return false;

        String whole = "";
        String numerator = left;
        int space = left.lastIndexOf(' ');
        if (space > 0) {
            String candidateWhole = left.substring(0, space).trim();
            String candidateNumerator = left.substring(space + 1).trim();
            if (candidateWhole.matches("[−-]?\\d+") && isNaturalTerm(candidateNumerator)) {
                whole = candidateWhole;
                numerator = candidateNumerator;
            }
        }
        if (!isNaturalTerm(numerator)) return false;

        paint.setTypeface(FACE_MEDIUM);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp(24f));
        float numeratorWidth = paint.measureText(numerator);
        float denominatorWidth = paint.measureText(denominator);
        float fractionWidth = Math.max(numeratorWidth, denominatorWidth) + dp(7);
        float wholeWidth = 0f;
        if (!whole.isEmpty()) {
            paint.setTextSize(sp(32f));
            wholeWidth = paint.measureText(whole) + dp(5);
        }
        if (fractionWidth + wholeWidth > available) return false;

        float right = lcd.right - dp(7);
        float fractionCenter = right - fractionWidth * 0.5f;
        float centerY = contentTop + (contentBottom - contentTop) * 0.75f;
        paint.setStrokeWidth(dp(0.8f));
        paint.setColor(LCD_INK);
        canvas.drawLine(fractionCenter - fractionWidth * 0.5f, centerY,
                fractionCenter + fractionWidth * 0.5f, centerY, paint);
        paint.setTextSize(sp(24f));
        canvas.drawText(numerator, fractionCenter, centerY - dp(6f), paint);
        canvas.drawText(denominator, fractionCenter, centerY + dp(19f), paint);
        if (!whole.isEmpty()) {
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(sp(32f));
            canvas.drawText(whole, fractionCenter - fractionWidth * 0.5f - dp(3),
                    centerY + dp(4.8f), paint);
        }
        return true;
    }

    private static boolean isNaturalTerm(String value) {
        return !value.isEmpty() && value.matches("[−-]?[0-9πe√^]+(?:√[0-9]+)?");
    }

    private void drawSpreadsheetGrid(Canvas canvas, RectF lcd) {
        float formulaTop = lcd.top + lcd.height() * 0.145f;
        float formulaBottom = formulaTop + dp(13);
        paint.setColor(Color.argb(42, 20, 27, 23));
        canvas.drawRect(lcd.left, formulaTop, lcd.right, formulaBottom, paint);
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(sp(14f));
        String address = ((char) ('A' + state.spreadsheetColumn()))
                + Integer.toString(state.spreadsheetRow() + 1);
        canvas.drawText(address, lcd.left + dp(3),
                centeredBaseline(formulaTop, formulaBottom), paint);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(13f));
        String formula = state.displayText().equals("│")
                ? state.spreadsheetFormula() : state.displayText();
        if (formula.isEmpty()) formula = state.result();
        canvas.drawText(ellipsize(formula, lcd.width() - dp(28)),
                lcd.left + dp(22), centeredBaseline(formulaTop, formulaBottom), paint);

        float gridTop = formulaBottom + dp(2);
        float gridBottom = lcd.bottom - dp(2);
        float rowHeader = dp(14);
        int visibleRows = 4;
        float rowHeight = (gridBottom - gridTop) / (visibleRows + 1);
        float columnWidth = (lcd.width() - rowHeader) / 5f;
        int rowStart = (state.spreadsheetRow() / visibleRows) * visibleRows;
        List<String> values = state.spreadsheetCells();
        paint.setStrokeWidth(dp(0.6f));
        paint.setTextSize(sp(12f));
        for (int column = 0; column < 5; column++) {
            float left = lcd.left + rowHeader + column * columnWidth;
            paint.setColor(Color.argb(42, 20, 27, 23));
            canvas.drawRect(left, gridTop, left + columnWidth, gridTop + rowHeight, paint);
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(Character.toString((char) ('A' + column)), left + columnWidth * 0.5f,
                    centeredBaseline(gridTop, gridTop + rowHeight), paint);
        }
        for (int visibleRow = 0; visibleRow < visibleRows; visibleRow++) {
            int row = rowStart + visibleRow;
            if (row >= 45) break;
            float top = gridTop + (visibleRow + 1) * rowHeight;
            paint.setColor(Color.argb(42, 20, 27, 23));
            canvas.drawRect(lcd.left, top, lcd.left + rowHeader, top + rowHeight, paint);
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_NORMAL);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(Integer.toString(row + 1), lcd.left + rowHeader * 0.5f,
                    centeredBaseline(top, top + rowHeight), paint);
            for (int column = 0; column < 5; column++) {
                float left = lcd.left + rowHeader + column * columnWidth;
                boolean selected = row == state.spreadsheetRow()
                        && column == state.spreadsheetColumn();
                paint.setColor(selected ? LCD_DARK : Color.argb(24, 20, 27, 23));
                canvas.drawRect(left, top, left + columnWidth, top + rowHeight, paint);
                paint.setColor(selected ? LCD : LCD_INK);
                paint.setTypeface(FACE_NORMAL);
                paint.setTextAlign(Paint.Align.RIGHT);
                String value = values.size() > row * 5 + column
                        ? values.get(row * 5 + column) : "";
                canvas.drawText(ellipsize(value, columnWidth - dp(3)),
                        left + columnWidth - dp(2), centeredBaseline(top, top + rowHeight), paint);
            }
        }
    }

    private void drawModeLanding(Canvas canvas, RectF lcd) {
        List<CnCwCommand> commands = state.modeCommands();
        float top = lcd.top + lcd.height() * 0.145f;
        int visible = Math.min(3, commands.size());
        int start = Math.max(0, Math.min(state.selectedIndex() - visible + 1,
                Math.max(0, commands.size() - visible)));
        float rowHeight = (lcd.bottom - top - dp(2)) / Math.max(1, visible);
        for (int row = 0; row < visible; row++) {
            int index = start + row;
            float rowTop = top + row * rowHeight;
            boolean selected = index == state.selectedIndex();
            if (selected) {
                paint.setColor(LCD_DARK);
                canvas.drawRoundRect(lcd.left + dp(3), rowTop,
                        lcd.right - dp(3), rowTop + rowHeight - dp(1), dp(1.5f), dp(1.5f), paint);
            }
            paint.setColor(selected ? LCD : LCD_INK);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(FACE_MEDIUM);
        paint.setTextSize(sp(18f));
            canvas.drawText(commands.get(index).label(), lcd.left + dp(7),
                    centeredBaseline(rowTop, rowTop + rowHeight), paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(12f));
            canvas.drawText(ellipsize(commands.get(index).description(), lcd.width() * 0.48f),
                    lcd.right - dp(7), centeredBaseline(rowTop, rowTop + rowHeight), paint);
        }
    }

    private void drawScrollBar(Canvas canvas, RectF lcd, int start, int visible,
                               int total, float top, float bottom) {
        float trackLeft = lcd.right - dp(2.3f);
        paint.setColor(Color.argb(70, 20, 27, 23));
        canvas.drawRect(trackLeft, top, lcd.right - dp(1.1f), bottom, paint);
        float thumbHeight = (bottom - top) * visible / total;
        float thumbTop = top + (bottom - top - thumbHeight) * start / Math.max(1, total - visible);
        paint.setColor(LCD_INK);
        canvas.drawRect(trackLeft, thumbTop, lcd.right - dp(1.1f), thumbTop + thumbHeight, paint);
    }

    private void drawPageRocker(Canvas canvas) {
        KeyHit up = findSpec(CnCwKey.PAGE_UP);
        KeyHit down = findSpec(CnCwKey.PAGE_DOWN);
        if (up == null || down == null) return;
        scratch.set(Math.min(up.visualBounds.left, down.visualBounds.left) - dp(2),
                up.visualBounds.top - dp(4),
                Math.max(up.visualBounds.right, down.visualBounds.right) + dp(2),
                down.visualBounds.bottom + dp(4));
        paint.setColor(Color.rgb(213, 221, 214));
        canvas.drawRoundRect(scratch, scratch.width() * 0.5f, scratch.width() * 0.5f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.rgb(111, 126, 117));
        canvas.drawRoundRect(scratch, scratch.width() * 0.5f, scratch.width() * 0.5f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawNavigationPlate(Canvas canvas) {
        KeyHit up = findNavSpec(CnCwKey.UP);
        KeyHit down = findNavSpec(CnCwKey.DOWN);
        KeyHit left = findNavSpec(CnCwKey.LEFT);
        KeyHit right = findNavSpec(CnCwKey.RIGHT);
        if (up == null || down == null || left == null || right == null) return;
        scratch.set(left.visualBounds.left - dp(5), up.visualBounds.top - dp(5),
                right.visualBounds.right + dp(5), down.visualBounds.bottom + dp(5));
        paint.setColor(Color.rgb(218, 224, 217));
        canvas.drawRoundRect(scratch, scratch.height() * 0.33f,
                scratch.height() * 0.33f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.85f));
        paint.setColor(Color.rgb(183, 193, 185));
        canvas.drawRoundRect(scratch, scratch.height() * 0.33f,
                scratch.height() * 0.33f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDirectionGlyph(Canvas canvas, CnCwKey key, RectF visual) {
        float centerX = visual.centerX();
        float centerY = visual.centerY();
        float size = Math.min(visual.width(), visual.height()) * 0.22f;
        iconPath.reset();
        switch (key) {
            case UP -> {
                iconPath.moveTo(centerX, centerY - size);
                iconPath.lineTo(centerX - size * 0.82f, centerY + size * 0.70f);
                iconPath.lineTo(centerX + size * 0.82f, centerY + size * 0.70f);
            }
            case DOWN -> {
                iconPath.moveTo(centerX, centerY + size);
                iconPath.lineTo(centerX - size * 0.82f, centerY - size * 0.70f);
                iconPath.lineTo(centerX + size * 0.82f, centerY - size * 0.70f);
            }
            case LEFT -> {
                iconPath.moveTo(centerX - size, centerY);
                iconPath.lineTo(centerX + size * 0.70f, centerY - size * 0.82f);
                iconPath.lineTo(centerX + size * 0.70f, centerY + size * 0.82f);
            }
            case RIGHT -> {
                iconPath.moveTo(centerX + size, centerY);
                iconPath.lineTo(centerX - size * 0.70f, centerY - size * 0.82f);
                iconPath.lineTo(centerX - size * 0.70f, centerY + size * 0.82f);
            }
            default -> { return; }
        }
        iconPath.close();
        paint.setColor(Color.rgb(35, 63, 50));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(iconPath, paint);
    }

    private void drawPageRockerGlyph(Canvas canvas, CnCwKey key, RectF visual) {
        float centerX = visual.centerX();
        float centerY = visual.centerY();
        float size = Math.min(visual.width(), visual.height()) * 0.20f;
        iconPath.reset();
        if (key == CnCwKey.PAGE_UP) {
            iconPath.moveTo(centerX - size, centerY + size * 0.35f);
            iconPath.lineTo(centerX, centerY - size * 0.65f);
            iconPath.lineTo(centerX + size, centerY + size * 0.35f);
        } else {
            iconPath.moveTo(centerX - size, centerY - size * 0.35f);
            iconPath.lineTo(centerX, centerY + size * 0.65f);
            iconPath.lineTo(centerX + size, centerY - size * 0.35f);
        }
        paint.setColor(Color.rgb(35, 63, 50));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(iconPath, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private KeyHit findNavSpec(CnCwKey key) {
        KeyHit best = null;
        float target = getWidth() * 0.60f;
        float distance = Float.MAX_VALUE;
        for (KeyHit hit : hitMap) {
            if (hit.spec.key != key || hit.spec.kind != KeyKind.NAV) continue;
            float candidate = Math.abs(hit.touchBounds.centerX() - target);
            if (candidate < distance) {
                distance = candidate;
                best = hit;
            }
        }
        return best;
    }

    private void drawKey(Canvas canvas, KeyHit hit) {
        boolean pressed = touchRouter.isPressed(hit.spec.key);
        int base = colorFor(hit.spec.kind);
        int color = pressed ? blend(base, INK_DARK, 0.13f) : base;
        RectF visual = hit.visualBounds;
        float radius = hit.spec.circular ? Math.min(visual.width(), visual.height()) * 0.5f
                : dp(6f);
        boolean pageRocker = hit.spec.key == CnCwKey.PAGE_UP
                || hit.spec.key == CnCwKey.PAGE_DOWN;
        float pressDepth = pressed ? dp(1.7f) : 0f;

        boolean directionKey = hit.spec.key == CnCwKey.UP || hit.spec.key == CnCwKey.DOWN
                || hit.spec.key == CnCwKey.LEFT || hit.spec.key == CnCwKey.RIGHT;
        canvas.save();
        canvas.translate(0, pressDepth);
        if (!pageRocker) {
            float shadowDepth = pressed ? dp(0.8f) : dp(2.45f);
            paint.setColor(KEY_SHADOW);
            canvas.drawRoundRect(visual.left, visual.top + shadowDepth, visual.right,
                    visual.bottom + shadowDepth, radius, radius, paint);
            paint.setColor(color);
            canvas.drawRoundRect(visual, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(0.85f));
            paint.setColor(pressed ? blend(KEY_BORDER, INK_DARK, 0.20f) : KEY_BORDER);
            canvas.drawRoundRect(visual, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        if (directionKey) {
            drawDirectionGlyph(canvas, hit.spec.key, visual);
        } else if (pageRocker) {
            drawPageRockerGlyph(canvas, hit.spec.key, visual);
        } else {
            boolean darkInk = true;
            paint.setColor(darkInk ? INK_DARK : INK_LIGHT);
            paint.setTypeface(FACE_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            if (hit.spec.secondary.isEmpty()) {
                drawFittedCentered(canvas, hit.spec.main, visual.centerX(),
                        visual.top, visual.bottom, hit.visual.mainTextSize(),
                        visual.width() * 0.88f, dp(11), darkInk ? INK_DARK : INK_LIGHT,
                        FACE_NORMAL);
            } else {
                // Secondary legends get a real band inside their own keycap.
                // This keeps them centered and prevents them from colliding with
                // the key above on compact phone layouts.
                drawFittedCentered(canvas, hit.spec.secondary, visual.centerX(),
                        visual.top + dp(1), visual.top + visual.height() * 0.34f,
                        scaledSecondarySize(hit), visual.width() * 0.92f,
                        dp(9), SHIFT_INK, FACE_MEDIUM);
                drawFittedCentered(canvas, hit.spec.main, visual.centerX(),
                        visual.top + visual.height() * 0.27f, visual.bottom,
                        scaledMainSize(hit), visual.width() * 0.90f,
                        dp(11), darkInk ? INK_DARK : INK_LIGHT, FACE_NORMAL);
            }
        }
        canvas.restore();

        if (state.shiftArmed() && hit.spec.key == CnCwKey.SHIFT) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(visual.left + dp(1), visual.top + dp(1),
                    visual.right - dp(1), visual.bottom - dp(1), radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && displayBounds(getWidth()).contains(event.getX(), event.getY())) {
                    displayPressed = true;
                    displaySelectionMode = false;
                    displayLongPressTriggered = false;
                    selectionTapCandidate = false;
                    selectionDragEdge = 0;
                    displayDownX = event.getX();
                    displayDownY = event.getY();
                    lastDragCursor = state.cursor();

                    if (state.hasSelection()) {
                        RectF lcd = displayBounds(getWidth());
                        float contentTop = lcd.top + lcd.height() * 0.145f;
                        float contentBottom = lcd.bottom - dp(3);
                        float handleTop = contentTop + dp(1.5f) - dp(2.2f);
                        float handleBottom = Math.min(contentBottom - dp(10),
                                contentTop + dp(34)) + dp(2.2f);
                        float startX = displayBoundaryX(state.selectionStart());
                        float endX = displayBoundaryX(state.selectionEnd());
                        float startDistance = (float) Math.hypot(
                                event.getX() - startX, event.getY() - handleBottom);
                        float endDistance = (float) Math.hypot(
                                event.getX() - endX, event.getY() - handleTop);
                        float handleSlop = dp(15);
                        if (Math.min(startDistance, endDistance) <= handleSlop) {
                            selectionDragEdge = startDistance <= endDistance ? -1 : 1;
                            displaySelectionMode = true;
                            lastDragCursor = selectionDragEdge < 0
                                    ? state.selectionStart() : state.selectionEnd();
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            return true;
                        }
                        float left = Math.min(startX, endX) - dp(6);
                        float right = Math.max(startX, endX) + dp(6);
                        float textTop = contentTop - dp(4);
                        float textBottom = Math.min(contentBottom, contentTop + dp(38));
                        if (event.getX() >= left && event.getX() <= right
                                && event.getY() >= textTop && event.getY() <= textBottom) {
                            selectionTapCandidate = true;
                            return true;
                        }
                    }

                    displayLongPress = () -> {
                        if (displayPressed) {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            displayLongPressTriggered = true;
                            int anchor = displayCursorPosition(displayDownX);
                            state = machine.selectTouchWord(anchor);
                            lastDragCursor = state.cursor();
                            selectionDragEdge = 0;
                            displaySelectionMode = true;
                            postInvalidateOnAnimation();
                        }
                    };
                    gestureHandler.postDelayed(displayLongPress, 360);
                    return true;
                }
                KeyHit hit = findHit(event.getX(actionIndex), event.getY(actionIndex));
                if (hit != null && touchRouter.pointerDown(pointerId, hit.spec.key)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    dispatchKey(hit.spec.key);
                    scheduleKeyRepeat(pointerId, hit.spec.key);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (displayPressed) {
                    if (displaySelectionMode) {
                        moveSelectionBoundaryToDisplayPosition(event.getX());
                        return true;
                    }
                    float dx = event.getX() - displayDownX;
                    float dy = event.getY() - displayDownY;
                    if (selectionTapCandidate) {
                        if (Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10)) {
                            selectionTapCandidate = false;
                        }
                        return true;
                    }
                    if (Math.abs(dx) > dp(4) && Math.abs(dx) > Math.abs(dy)) {
                        if (displayLongPress != null) gestureHandler.removeCallbacks(displayLongPress);
                        moveCursorToDisplayPosition(event.getX(), true);
                    }
                    return true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (displayPressed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    displayPressed = false;
                    if (displayLongPress != null) gestureHandler.removeCallbacks(displayLongPress);
                    if (displaySelectionMode || displayLongPressTriggered) {
                        displaySelectionMode = false;
                        displayLongPressTriggered = false;
                        selectionDragEdge = 0;
                        selectionTapCandidate = false;
                        // Phone-style behavior: releasing a handle keeps the selection.
                        postInvalidateOnAnimation();
                        return true;
                    }
                    float dx = event.getX() - displayDownX;
                    float dy = event.getY() - displayDownY;
                    if (selectionTapCandidate) {
                        selectionTapCandidate = false;
                        if (Math.abs(dx) < dp(12) && Math.abs(dy) < dp(12)) {
                            showClipboardMenu();
                        }
                        return true;
                    }
                    if (Math.abs(dx) < dp(18) && Math.abs(dy) < dp(18)) {
                        moveCursorToDisplayPosition(event.getX(), false);
                    }
                    return true;
                }
                touchRouter.pointerUp(pointerId);
                if (pointerId == repeatingPointerId) stopKeyRepeat();
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                postInvalidateOnAnimation();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                displayPressed = false;
                displaySelectionMode = false;
                displayLongPressTriggered = false;
                selectionTapCandidate = false;
                selectionDragEdge = 0;
                if (displayLongPress != null) gestureHandler.removeCallbacks(displayLongPress);
                stopKeyRepeat();
                touchRouter.cancelAll();
                postInvalidateOnAnimation();
                return true;
            }
            default -> { return true; }
        }
    }

    /**
     * Basic phone-style repeat for the low-risk editing keys. The first tap
     * has already been committed by pointer-down; repeat only starts after a
     * long-press delay and is cancelled as soon as that pointer is released.
     */
    private void scheduleKeyRepeat(int pointerId, CnCwKey key) {
        if (!isRepeatableKey(key)) return;
        stopKeyRepeat();
        repeatingPointerId = pointerId;
        repeatingKey = key;
        keyRepeat = new Runnable() {
            @Override public void run() {
                if (repeatingKey == null) return;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                dispatchKey(repeatingKey);
                gestureHandler.postDelayed(this, 72);
            }
        };
        gestureHandler.postDelayed(keyRepeat, 420);
    }

    private void stopKeyRepeat() {
        if (keyRepeat != null) gestureHandler.removeCallbacks(keyRepeat);
        keyRepeat = null;
        repeatingKey = null;
        repeatingPointerId = -1;
    }

    private boolean isRepeatableKey(CnCwKey key) {
        return switch (key) {
            case DEL, DOT, DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
                    DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9 -> true;
            default -> false;
        };
    }

    private void moveCursorToDisplayPosition(float x, boolean haptic) {
        moveCursorAtomically(displayCursorPosition(x), haptic);
    }

    /** Converts a display x-coordinate into the nearest semantic boundary. */
    private int displayCursorPosition(float x) {
        List<String> labels = machine.cursorTokenDisplays();
        if (labels.isEmpty()) return 0;
        RectF lcd = displayBounds(getWidth());
        float baseSize = sp(25f);
        NaturalMetrics natural = measureNatural(state.naturalExpression(), baseSize);
        float available = lcd.width() - dp(12);
        float expressionX = lcd.left + dp(6);
        if (natural.width > available) {
            float cursorOffset = naturalCursorOffset(state.naturalExpression(), baseSize);
            float focus = cursorOffset < 0 ? natural.width : cursorOffset;
            expressionX = expressionX + available * 0.58f - focus;
            expressionX = Math.min(lcd.left + dp(6),
                    Math.max(lcd.left + dp(6) + available - natural.width, expressionX));
        }
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(baseSize);
        float rawWidth = 0f;
        for (String label : labels) rawWidth += Math.max(dp(4), paint.measureText(label));
        float scale = rawWidth <= 0 ? 1f : natural.width / rawWidth;
        float boundary = expressionX;
        int target = 0;
        for (int i = 0; i < labels.size(); i++) {
            float width = Math.max(dp(4), paint.measureText(labels.get(i))) * scale;
            if (x >= boundary + width * 0.5f) target = i + 1;
            boundary += width;
        }
        return Math.max(0, Math.min(machine.cursorLimit(), target));
    }

    /** Returns the approximate x-coordinate of a semantic insertion boundary. */
    private float displayBoundaryX(int boundaryIndex) {
        List<String> labels = machine.cursorTokenDisplays();
        RectF lcd = displayBounds(getWidth());
        float baseSize = sp(25f);
        NaturalMetrics natural = measureNatural(state.naturalExpression(), baseSize);
        float available = lcd.width() - dp(12);
        float expressionX = lcd.left + dp(6);
        if (natural.width > available) {
            float cursorOffset = naturalCursorOffset(state.naturalExpression(), baseSize);
            float focus = cursorOffset < 0 ? natural.width : cursorOffset;
            expressionX = expressionX + available * 0.58f - focus;
            expressionX = Math.min(lcd.left + dp(6),
                    Math.max(lcd.left + dp(6) + available - natural.width, expressionX));
        }
        if (labels.isEmpty()) return expressionX;
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(baseSize);
        float rawWidth = 0f;
        for (String label : labels) rawWidth += Math.max(dp(4), paint.measureText(label));
        float scale = rawWidth <= 0 ? 1f : natural.width / rawWidth;
        int clamped = Math.max(0, Math.min(labels.size(), boundaryIndex));
        float x = expressionX;
        for (int i = 0; i < clamped; i++) {
            x += Math.max(dp(4), paint.measureText(labels.get(i))) * scale;
        }
        return x;
    }

    private void moveSelectionBoundaryToDisplayPosition(float x) {
        int target = displayCursorPosition(x);
        if (selectionDragEdge == 0) {
            if (target <= state.selectionStart() || x < displayDownX) selectionDragEdge = -1;
            else if (target >= state.selectionEnd() || x > displayDownX) selectionDragEdge = 1;
            else return;
        }
        if (target == lastDragCursor) return;
        state = selectionDragEdge < 0
                ? machine.moveTouchSelectionStart(target)
                : machine.moveTouchSelectionEnd(target);
        lastDragCursor = selectionDragEdge < 0
                ? state.selectionStart() : state.selectionEnd();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        postInvalidateOnAnimation();
    }

    private void moveCursorAtomically(int target, boolean haptic) {
        int clamped = Math.max(0, Math.min(machine.cursorLimit(), target));
        if (clamped == lastDragCursor && haptic) return;
        state = machine.moveCursorTo(clamped);
        lastDragCursor = clamped;
        if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        postInvalidateOnAnimation();
    }

    private void copyDisplayText() {
        String text = cleanClipboardText(state.displayText());
        if (text == null || text.isBlank() || text.equals("│")) return;
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("计算器", text));
            Toast.makeText(getContext(), "已复制公式", Toast.LENGTH_SHORT).show();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    private void showClipboardMenu() {
        boolean hasSelection = state.hasSelection();
        String[] items = hasSelection
                ? new String[]{"复制选区", "复制计算过程", "复制计算结果", "粘贴"}
                : new String[]{"复制计算过程", "复制计算结果", "粘贴"};
        new AlertDialog.Builder(getContext()).setItems(items, (dialog, which) -> {
            if (hasSelection && which == 0) {
                copyText(cleanClipboardText(machine.selectedExpression()), "已复制选区");
            } else if (which == (hasSelection ? 1 : 0)) {
                copyText(cleanClipboardText(state.expression()), "已复制计算过程");
            } else if (which == (hasSelection ? 2 : 1)) {
                copyText(decimalResult(state.result()), "已复制十进制结果");
            } else {
                pasteClipboardText();
            }
        }).show();
    }

    private String cleanClipboardText(String text) {
        if (text == null) return "";
        return text.replace("│", "").replace("▌", "").trim();
    }

    private String decimalResult(String text) {
        String clean = cleanClipboardText(text);
        if (clean.isEmpty()) return clean;
        try {
            return BigDecimal.valueOf(Double.parseDouble(clean)).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            int slash = clean.indexOf('/');
            if (slash > 0 && slash == clean.lastIndexOf('/')) {
                try {
                    BigDecimal a = new BigDecimal(clean.substring(0, slash).trim());
                    BigDecimal b = new BigDecimal(clean.substring(slash + 1).trim());
                    return a.divide(b, 12, java.math.RoundingMode.HALF_UP)
                            .stripTrailingZeros().toPlainString();
                } catch (ArithmeticException | NumberFormatException ignoredAgain) { }
            }
            return clean;
        }
    }

    private String decimalDisplayResult(String text) {
        if (text == null || text.contains("\n") || text.contains("=")) return text;
        return decimalResult(text);
    }

    private void copyText(String text, String message) {
        if (text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("计算器", text));
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    private void pasteClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(getContext());
        if (value == null) return;
        String normalized = value.toString()
                .replace("×", "*").replace("÷", "/")
                .replace("−", "-").replace("√", "sqrt(")
                .replace("²", "^2").replace("³", "^3");
        int accepted = 0;
        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.startsWith("sqrt(", i)) {
                dispatchKey(CnCwKey.SQRT);
                dispatchKey(CnCwKey.OPEN_PAREN);
                accepted++;
                i += 4;
                continue;
            }
            CnCwKey key = pasteKey(normalized.charAt(i));
            if (key != null) { dispatchKey(key); accepted++; }
        }
        Toast.makeText(getContext(), accepted == 0 ? "没有可识别内容" : "已粘贴", Toast.LENGTH_SHORT).show();
    }

    private CnCwKey pasteKey(char ch) {
        return switch (ch) {
            case '0' -> CnCwKey.DIGIT_0; case '1' -> CnCwKey.DIGIT_1;
            case '2' -> CnCwKey.DIGIT_2; case '3' -> CnCwKey.DIGIT_3;
            case '4' -> CnCwKey.DIGIT_4; case '5' -> CnCwKey.DIGIT_5;
            case '6' -> CnCwKey.DIGIT_6; case '7' -> CnCwKey.DIGIT_7;
            case '8' -> CnCwKey.DIGIT_8; case '9' -> CnCwKey.DIGIT_9;
            case '.' -> CnCwKey.DOT; case '+' -> CnCwKey.ADD;
            case '-' -> CnCwKey.SUBTRACT; case '*' -> CnCwKey.MULTIPLY;
            case '/' -> CnCwKey.DIVIDE; case '(' -> CnCwKey.OPEN_PAREN;
            case ')' -> CnCwKey.CLOSE_PAREN; default -> null;
        };
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        CnCwKey key = mapHardwareKey(keyCode, event);
        if (key == null) return super.onKeyDown(keyCode, event);
        if (event.getRepeatCount() == 0) {
            dispatchKey(key);
        }
        return true;
    }

    private void dispatchKey(CnCwKey key) {
        long revision = ++inputRevision;
        cancelPendingEvaluation();
        if (key == CnCwKey.EXE && state.screen().isApplication()
                && !state.applicationLanding()) {
            CnCwMachine snapshot = machine.copyForEvaluation();
            evaluating = true;
            postInvalidateOnAnimation();
            pendingEvaluation = evaluationExecutor.submit(() -> {
                CnCwUiState next = snapshot.dispatch(key);
                post(() -> {
                    if (revision != inputRevision) return;
                    machine = snapshot;
                    state = next;
                    pendingEvaluation = null;
                    evaluating = false;
                    postInvalidateOnAnimation();
                });
            });
            return;
        }
        state = machine.dispatch(key);
        if (key == CnCwKey.ON && state.screen() == CnCwScreen.HOME) {
            state = machine.dispatch(CnCwKey.OK);
        }
        postInvalidateOnAnimation();
    }

    private void cancelPendingEvaluation() {
        if (pendingEvaluation != null) {
            pendingEvaluation.cancel(true);
            pendingEvaluation = null;
        }
        evaluating = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        inputRevision++;
        cancelPendingEvaluation();
        evaluationExecutor.shutdownNow();
        super.onDetachedFromWindow();
    }

    private CnCwKey mapHardwareKey(int keyCode, KeyEvent event) {
        return switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP -> CnCwKey.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN -> CnCwKey.DOWN;
            case KeyEvent.KEYCODE_PAGE_UP -> CnCwKey.PAGE_UP;
            case KeyEvent.KEYCODE_PAGE_DOWN -> CnCwKey.PAGE_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT -> CnCwKey.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> CnCwKey.RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER -> CnCwKey.OK;
            case KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> CnCwKey.EXE;
            case KeyEvent.KEYCODE_BACK -> CnCwKey.BACK;
            case KeyEvent.KEYCODE_ESCAPE -> CnCwKey.AC;
            case KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> CnCwKey.DEL;
            case KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> CnCwKey.DIGIT_0;
            case KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> CnCwKey.DIGIT_1;
            case KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> CnCwKey.DIGIT_2;
            case KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> CnCwKey.DIGIT_3;
            case KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> CnCwKey.DIGIT_4;
            case KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> CnCwKey.DIGIT_5;
            case KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> CnCwKey.DIGIT_6;
            case KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> CnCwKey.DIGIT_7;
            case KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> CnCwKey.DIGIT_8;
            case KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> CnCwKey.DIGIT_9;
            case KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_NUMPAD_DOT -> CnCwKey.DOT;
            case KeyEvent.KEYCODE_COMMA -> CnCwKey.COMMA;
            case KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_PLUS -> CnCwKey.ADD;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_MINUS -> CnCwKey.SUBTRACT;
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY, KeyEvent.KEYCODE_STAR -> CnCwKey.MULTIPLY;
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE, KeyEvent.KEYCODE_SLASH -> CnCwKey.DIVIDE;
            case KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> CnCwKey.OPEN_PAREN;
            case KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> CnCwKey.CLOSE_PAREN;
            case KeyEvent.KEYCODE_EQUALS -> CnCwKey.EQUALS;
            case KeyEvent.KEYCODE_H -> CnCwKey.HOME;
            default -> null;
        };
    }

    private KeyHit findHit(float x, float y) {
        float extension = dp(2.2f);
        for (KeyHit hit : hitMap) {
            if (x >= hit.touchBounds.left - extension && x <= hit.touchBounds.right + extension
                    && y >= hit.touchBounds.top - extension && y <= hit.touchBounds.bottom + extension) {
                return hit;
            }
        }
        return null;
    }

    private KeyHit findSpec(CnCwKey key) {
        for (KeyHit hit : hitMap) if (hit.spec.key == key) return hit;
        return null;
    }

    private String compactDisplayMode() {
        return switch (state.settings().displayMode()) {
            case FIX -> "FIX" + state.settings().displayDigits();
            case SCI -> "SCI" + state.settings().displayDigits();
            case NORM_1 -> "N1";
            case NORM_2 -> "N2";
        };
    }

    private String ellipsize(String text, float maxWidth) {
        if (text == null || paint.measureText(text) <= maxWidth) return text == null ? "" : text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText(ellipsis) > maxWidth) end--;
        return text.substring(0, end) + ellipsis;
    }

    private float centeredBaseline(float top, float bottom) {
        paint.getFontMetrics(fontMetrics);
        return (top + bottom) * 0.5f - (fontMetrics.ascent + fontMetrics.descent) * 0.5f;
    }

    private void drawFittedCentered(Canvas canvas, String text, float centerX,
                                    float top, float bottom, float requestedSize,
                                    float maxWidth, float minimumSize, int color,
                                    Typeface typeface) {
        paint.setTypeface(typeface);
        paint.setTextAlign(Paint.Align.CENTER);
        float size = requestedSize;
        paint.setTextSize(size);
        while (size > minimumSize && paint.measureText(text) > maxWidth) {
            size -= dp(0.5f);
            paint.setTextSize(size);
        }
        paint.setColor(color);
        canvas.drawText(text, centerX, centeredBaseline(top, bottom), paint);
    }

    private static float textLengthScale(String text) {
        if (text == null) return 1f;
        int length = text.codePointCount(0, text.length());
        if (length <= 2) return 1f;
        if (length == 3) return 0.96f;
        if (length == 4) return 0.90f;
        if (length == 5) return 0.84f;
        return 0.78f;
    }

    private float scaledSecondarySize(KeyHit hit) {
        if (hit.spec.kind == KeyKind.SHIFT) return hit.visual.secondaryTextSize();
        if (hit.spec.kind == KeyKind.STRIP || hit.spec.kind == KeyKind.FUNCTION) {
            return hit.visual.secondaryTextSize() * 0.86f;
        }
        return hit.visual.secondaryTextSize();
    }

    private float scaledMainSize(KeyHit hit) {
        if (hit.spec.kind == KeyKind.STRIP) return hit.visual.mainTextSize() * 0.90f;
        if (hit.spec.kind == KeyKind.FUNCTION) return hit.visual.mainTextSize() * 0.94f;
        return hit.visual.mainTextSize();
    }

    private static int colorFor(KeyKind kind) {
        return switch (kind) {
            case NUMBER -> KEY_NUMBER;
            case OPERATOR -> KEY_OPERATOR;
            case FUNCTION -> KEY_FUNCTION;
            case CONTROL, STRIP -> KEY_CONTROL;
            case NAV -> KEY_NAV;
            case OK -> KEY_OK;
            case SHIFT -> KEY_SHIFT;
            case ACTION -> KEY_ACTION;
            case EQUALS -> KEY_EXECUTE;
        };
    }

    private static int blend(int from, int to, float amount) {
        int red = (int) (Color.red(from) * (1f - amount) + Color.red(to) * amount);
        int green = (int) (Color.green(from) * (1f - amount) + Color.green(to) * amount);
        int blue = (int) (Color.blue(from) * (1f - amount) + Color.blue(to) * amount);
        return Color.rgb(red, green, blue);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }

    private enum KeyKind {
        NUMBER, OPERATOR, FUNCTION, CONTROL, STRIP, NAV, OK, SHIFT, ACTION, EQUALS
    }

    private record KeySpec(CnCwKey key, String main, String secondary, KeyKind kind,
                           boolean circular) { }
    private record KeyHit(KeySpec spec, RectF touchBounds, RectF visualBounds,
                          Cw991LayoutMetrics.KeyVisual visual) { }
    private record NaturalMetrics(float width, float top, float bottom) { }
}

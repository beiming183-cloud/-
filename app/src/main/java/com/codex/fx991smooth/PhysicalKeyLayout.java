package com.codex.fx991smooth;

import android.graphics.RectF;

import com.codex.fx991.core.cw.CnCwKey;
import com.codex.fx991.core.ui.Cw991LayoutMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The physical 991CN CW topology, kept separate from Canvas drawing.
 *
 * <p>The real calculator has three visual layers below the LCD: a compact
 * control deck, a row of context keys, and six-key function/five-key numeric
 * keypad groups.  The
 * old view encoded those semantics in a seven-button strip, which made every
 * target too small and also encouraged duplicate logical keys.  This class is
 * the single source of truth for hit rectangles and key legends; the renderer
 * only decides how a definition looks.</p>
 */
public final class PhysicalKeyLayout {
    public enum Kind {
        NUMBER, OPERATOR, FUNCTION, CONTROL, CONTEXT, NAV, OK, SHIFT, ACTION, EXECUTE
    }

    public static final class Definition {
        public final CnCwKey key;
        public final String primary;
        public final String secondary;
        public final Kind kind;
        public final boolean circular;

        private Definition(CnCwKey key, String primary, String secondary,
                           Kind kind, boolean circular) {
            this.key = key;
            this.primary = primary;
            this.secondary = secondary;
            this.kind = kind;
            this.circular = circular;
        }
    }

    public static final class Hit {
        public final Definition definition;
        public final RectF touchBounds;
        public final RectF visualBounds;
        public final Cw991LayoutMetrics.KeyVisual visual;

        private Hit(Definition definition, RectF touchBounds, RectF visualBounds,
                    Cw991LayoutMetrics.KeyVisual visual) {
            this.definition = definition;
            this.touchBounds = touchBounds;
            this.visualBounds = visualBounds;
            this.visual = visual;
        }
    }

    private static Definition key(CnCwKey key, String primary, String secondary,
                                  Kind kind) {
        // The calculation keyboard follows the compact rectangular key
        // language of the reference app.  The reducer and all key semantics
        // remain the same; only the cap shape changes.
        return new Definition(key, primary, secondary, kind, false);
    }

    private static Definition circle(CnCwKey key, String primary, String secondary,
                                     Kind kind) {
        return new Definition(key, primary, secondary, kind, true);
    }

    /* The top two math rows use six compact keys; the numeric block uses five
       larger keys. π is intentionally the SHIFT legend on 7; there is no
       standalone π key on the physical model. */
    private static final Definition[][] MAIN_ROWS = {
            {
                    key(CnCwKey.VAR_X, "x", "", Kind.FUNCTION),
                    key(CnCwKey.FRACTION, "a/b", "a b/c", Kind.FUNCTION),
                    key(CnCwKey.SQRT, "√", "³√", Kind.FUNCTION),
                    key(CnCwKey.POWER, "xʸ", "x⁻¹", Kind.FUNCTION),
                    key(CnCwKey.SQUARE, "x²", "logₐb", Kind.FUNCTION),
                    key(CnCwKey.LOG, "log", "ln", Kind.FUNCTION)
            },
            {
                    key(CnCwKey.NEGATE, "(−)", "e", Kind.FUNCTION),
                    key(CnCwKey.SIN, "sin", "sin⁻¹", Kind.FUNCTION),
                    key(CnCwKey.COS, "cos", "cos⁻¹", Kind.FUNCTION),
                    key(CnCwKey.TAN, "tan", "tan⁻¹", Kind.FUNCTION),
                    key(CnCwKey.OPEN_PAREN, "(", "=", Kind.FUNCTION),
                    key(CnCwKey.CLOSE_PAREN, ")", ",", Kind.FUNCTION)
            },
            {
                    key(CnCwKey.DIGIT_7, "7", "π", Kind.NUMBER),
                    key(CnCwKey.DIGIT_8, "8", "∠", Kind.NUMBER),
                    key(CnCwKey.DIGIT_9, "9", "i", Kind.NUMBER),
                    key(CnCwKey.DEL, "⌫", "插入", Kind.ACTION),
                    key(CnCwKey.AC, "AC", "OFF", Kind.ACTION)
            },
            {
                    key(CnCwKey.DIGIT_4, "4", "A", Kind.NUMBER),
                    key(CnCwKey.DIGIT_5, "5", "B", Kind.NUMBER),
                    key(CnCwKey.DIGIT_6, "6", "C", Kind.NUMBER),
                    key(CnCwKey.MULTIPLY, "×", "∫", Kind.OPERATOR),
                    key(CnCwKey.DIVIDE, "÷", "d/dx", Kind.OPERATOR)
            },
            {
                    key(CnCwKey.DIGIT_1, "1", "D", Kind.NUMBER),
                    key(CnCwKey.DIGIT_2, "2", "E", Kind.NUMBER),
                    key(CnCwKey.DIGIT_3, "3", "F", Kind.NUMBER),
                    key(CnCwKey.ADD, "+", "nPr", Kind.OPERATOR),
                    key(CnCwKey.SUBTRACT, "−", "nCr", Kind.OPERATOR)
            },
            {
                    key(CnCwKey.DIGIT_0, "0", "x", Kind.NUMBER),
                    key(CnCwKey.DOT, ".", "y", Kind.NUMBER),
                    key(CnCwKey.EXP, "×10ˣ", "z", Kind.NUMBER),
                    key(CnCwKey.FORMAT, "格式", "Ans", Kind.ACTION),
                    key(CnCwKey.EXE, "EXE", "≈", Kind.EXECUTE)
            }
    };

    private final float density;
    private float viewportWidth;

    public PhysicalKeyLayout(float density) {
        this.density = Math.max(0.75f, density);
    }

    public Definition[][] mainRows() {
        return MAIN_ROWS;
    }

    /**
     * The physical screen is noticeably narrower and taller than a phone
     * toolbar.  Its placement is driven by the full calculator body, rather
     * than pinned to the top of a tall handset canvas.
     */
    public RectF displayBounds(float width, float height) {
        float left = Math.max(dp(13), width * 0.04f);
        float top = Math.max(dp(12), height * 0.02f);
        float lcdHeight = height * 0.22f;
        return new RectF(left, top, width - left, top + lcdHeight);
    }

    /**
     * Build all touch targets.  Rectangles are deliberately separated by a
     * few pixels; this keeps multi-touch deterministic and makes every key
     * comfortably larger than the old strip buttons.
     */
    public List<Hit> arrange(float width, float height) {
        viewportWidth = width;
        List<Hit> hits = new ArrayList<>(64);
        RectF lcd = displayBounds(width, height);
        float margin = Math.max(dp(13), width * 0.04f);
        float controlTop = lcd.bottom + width * 0.025f;
        float controlHeight = Math.min(height * 0.15f, width * 0.40f);
        float controlBottom = controlTop + controlHeight;
        // The X100s Pro viewport is wide enough that the old 21dp control
        // radius made ON/HOME and the direction pad look undersized beside
        // the function caps.  Keep the deck compact, but give it the same
        // visual weight as the keypad on a 20:9 phone.
        // Keep the four utility keys visually balanced with the navigation pad.
        // The previous caps were noticeably undersized on the tall X100s Pro viewport.
        float radius = clamp(Math.min(dp(31), width * 0.072f), dp(21), dp(38));

        // Left-side controls on the real unit: power/home above settings/back.
        addCircle(hits, key(CnCwKey.ON, "ON", "", Kind.CONTROL),
                margin + radius, controlTop + controlHeight * 0.29f, radius * 2f);
        addCircle(hits, key(CnCwKey.HOME, "⌂", "主屏", Kind.CONTROL),
                margin + radius * 3.15f, controlTop + controlHeight * 0.29f, radius * 2f);
        addCircle(hits, key(CnCwKey.SETTINGS, "≡", "设置", Kind.CONTROL),
                margin + radius, controlTop + controlHeight * 0.74f, radius * 2f);
        addCircle(hits, key(CnCwKey.BACK, "↩", "返回", Kind.CONTROL),
                margin + radius * 3.15f, controlTop + controlHeight * 0.74f, radius * 2f);

        // Direction pad is the interaction centre, not a tiny afterthought.
        float dpadX = width * 0.50f;
        float dpadY = controlTop + controlHeight * 0.52f;
        float d = radius * 1.42f;
        float offset = d * 1.35f;
        addCircle(hits, circle(CnCwKey.OK, "OK", "", Kind.OK), dpadX, dpadY, d);
        addCircle(hits, circle(CnCwKey.LEFT, "‹", "", Kind.NAV), dpadX - offset, dpadY, d);
        addCircle(hits, circle(CnCwKey.RIGHT, "›", "", Kind.NAV), dpadX + offset, dpadY, d);
        addCircle(hits, circle(CnCwKey.UP, "↑", "", Kind.NAV), dpadX, dpadY - offset, d);
        addCircle(hits, circle(CnCwKey.DOWN, "↓", "", Kind.NAV), dpadX, dpadY + offset, d);

        // The right rocker is for page/list scrolling; directory and tools
        // live in the context row below, as on the physical unit.
        addCircle(hits, circle(CnCwKey.PAGE_UP, "↑", "翻页", Kind.CONTROL),
                width - margin - radius, controlTop + controlHeight * 0.29f, radius * 2f);
        addCircle(hits, circle(CnCwKey.PAGE_DOWN, "↓", "翻页", Kind.CONTROL),
                width - margin - radius, controlTop + controlHeight * 0.74f, radius * 2f);

        // Context row: larger, evenly spaced, and visually distinct from math.
        float contextTop = controlBottom + width * 0.022f;
        float contextHeight = Math.min(height * 0.075f, width * 0.160f);
        float contextGap = dp(7);
        float contextWidth = (width - margin * 2f - contextGap * 4f) / 5f;
        Definition[] context = {
                key(CnCwKey.SHIFT, "↑", "SHIFT", Kind.SHIFT),
                key(CnCwKey.VARIABLE, "⇄x", "变量", Kind.CONTEXT),
                key(CnCwKey.FUNCTION, "f(x)", "功能", Kind.CONTEXT),
                key(CnCwKey.CATALOG, "▤", "目录", Kind.CONTEXT),
                key(CnCwKey.TOOLS, "•••", "工具", Kind.CONTEXT)
        };
        for (int i = 0; i < context.length; i++) {
            float left = margin + i * (contextWidth + contextGap);
            add(hits, context[i], left, contextTop, left + contextWidth,
                    contextTop + contextHeight);
        }

        float keyboardTop = contextTop + contextHeight + width * 0.025f;
        float keyboardBottom = height - dp(12);
        float[] rowCenters = Cw991LayoutMetrics.mainRowCenters(
                keyboardTop, keyboardBottom, width, density);
        for (int row = 0; row < MAIN_ROWS.length; row++) {
            float previousPitch = row == 0
                    ? rowCenters[1] - rowCenters[0]
                    : rowCenters[row] - rowCenters[row - 1];
            float nextPitch = row == MAIN_ROWS.length - 1
                    ? previousPitch
                    : rowCenters[row + 1] - rowCenters[row];
            float top = row == 0 ? rowCenters[row] - nextPitch * 0.5f
                    : (rowCenters[row - 1] + rowCenters[row]) * 0.5f;
            float bottom = row == MAIN_ROWS.length - 1
                    ? rowCenters[row] + nextPitch * 0.5f
                    : (rowCenters[row] + rowCenters[row + 1]) * 0.5f;
            int columnCount = MAIN_ROWS[row].length;
            float keyGap = columnCount == 6
                    ? Math.max(dp(4), width * 0.008f)
                    : Math.max(dp(6), width * 0.011f);
            float keyWidth = (width - margin * 2f - keyGap * (columnCount - 1))
                    / columnCount;
            for (int col = 0; col < MAIN_ROWS[row].length; col++) {
                float left = margin + col * (keyWidth + keyGap);
                add(hits, MAIN_ROWS[row][col], left, top, left + keyWidth, bottom);
            }
        }
        return Collections.unmodifiableList(hits);
    }

    private void addCircle(List<Hit> hits, Definition definition,
                           float centerX, float centerY, float diameter) {
        float radius = diameter * 0.5f;
        add(hits, definition, centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);
    }

    private void add(List<Hit> hits, Definition definition,
                     float left, float top, float right, float bottom) {
        RectF touch = new RectF(left, top, right, bottom);
        Cw991LayoutMetrics.KeyVisual visual = Cw991LayoutMetrics.forKey(
                metricsRole(definition.kind), touch.width(), touch.height(),
                viewportWidth, density, definition.circular);
        float visualWidth = visual.width();
        float visualHeight = visual.height();
        float centerX = touch.centerX();
        float centerY = touch.top + touch.height() * visual.verticalBias();
        if (definition.kind == Kind.NUMBER || definition.kind == Kind.FUNCTION
                || definition.kind == Kind.OPERATOR || definition.kind == Kind.ACTION
                || definition.kind == Kind.EXECUTE) {
            centerY += dp(8);
        }
        centerY = clamp(centerY, touch.top + visualHeight * 0.5f,
                touch.bottom - visualHeight * 0.5f);
        RectF visualBounds = new RectF(centerX - visualWidth * 0.5f,
                centerY - visualHeight * 0.5f,
                centerX + visualWidth * 0.5f,
                centerY + visualHeight * 0.5f);
        hits.add(new Hit(definition, touch, visualBounds, visual));
    }

    private static Cw991LayoutMetrics.Role metricsRole(Kind kind) {
        return switch (kind) {
            case NUMBER -> Cw991LayoutMetrics.Role.NUMBER;
            case OPERATOR -> Cw991LayoutMetrics.Role.OPERATOR;
            case FUNCTION -> Cw991LayoutMetrics.Role.FUNCTION;
            case CONTROL -> Cw991LayoutMetrics.Role.CONTROL;
            case CONTEXT -> Cw991LayoutMetrics.Role.CONTEXT;
            case NAV -> Cw991LayoutMetrics.Role.NAV;
            case OK -> Cw991LayoutMetrics.Role.OK;
            case SHIFT -> Cw991LayoutMetrics.Role.SHIFT;
            case ACTION -> Cw991LayoutMetrics.Role.ACTION;
            case EXECUTE -> Cw991LayoutMetrics.Role.EXECUTE;
        };
    }

    private float dp(float value) { return value * density; }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

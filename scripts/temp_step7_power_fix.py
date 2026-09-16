from pathlib import Path

root = Path(__file__).resolve().parents[1]

machine_path = root / "core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java"
text = machine_path.read_text(encoding="utf-8")
old = '''    /** UP enters the visual exponent; DOWN returns to the base. */
    private boolean movePowerVertical(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        PowerCursor power = powerCursorFromPath(semanticCursorPath());
        if (power == null) return false;
        PowerBounds bounds = powerBounds(power.templateIndex);
        if (bounds == null) return false;

        if (direction < 0 && power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE) {
            setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                    Math.min(power.offset, bounds.exponentEnd - bounds.exponentStart));
        } else if (direction > 0
                && power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT) {
            setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                    Math.min(power.offset, bounds.baseEnd - bounds.baseStart));
        }
        finishSemanticCursorMove();
        return true;
    }
'''
new = '''    /**
     * UP/DOWN follow visual geometry rather than reusing the same local token
     * offset.  A superscript is drawn to the upper-right of the base, so the
     * nearest lower insertion point is the base end; conversely entering the
     * exponent from the base starts at the exponent's left edge.  Reusing the
     * numeric offset made multi-digit bases jump into their middle and could
     * make repeated base/exponent movement feel stuck on-device.
     */
    private boolean movePowerVertical(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        PowerCursor power = powerCursorFromPath(semanticCursorPath());
        if (power == null) return false;
        PowerBounds bounds = powerBounds(power.templateIndex);
        if (bounds == null) return false;

        if (direction < 0 && power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE) {
            setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT, 0);
        } else if (direction > 0
                && power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT) {
            setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                    bounds.baseEnd - bounds.baseStart);
        }
        finishSemanticCursorMove();
        return true;
    }
'''
if old not in text:
    raise SystemExit("movePowerVertical target not found")
machine_path.write_text(text.replace(old, new, 1), encoding="utf-8")

suite_path = root / "core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java"
text = suite_path.read_text(encoding="utf-8")
needle = '''        powerPublishesBaseExponentAndMovesSemantically();
        powerDeleteNeverBreaksTemplate();
'''
replacement = '''        powerPublishesBaseExponentAndMovesSemantically();
        powerVerticalProjectionUsesVisualEdges();
        powerDeleteNeverBreaksTemplate();
'''
if needle not in text:
    raise SystemExit("suite run-list target not found")
text = text.replace(needle, replacement, 1)
marker = '''    private void powerDeleteNeverBreaksTemplate() {
'''
method = '''    private void powerVerticalProjectionUsesVisualEdges() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.POWER);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.DIGIT_6);

        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "multi-digit power input ends in exponent");
        equal(2, machine.state().semanticCursor().offset(),
                "multi-digit exponent ends at offset two");

        machine.dispatch(CnCwKey.DOWN);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "DOWN from exponent enters base");
        equal(3, machine.state().semanticCursor().offset(),
                "DOWN projects to visual base end instead of middle of 555");

        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "UP from base returns to exponent");
        equal(0, machine.state().semanticCursor().offset(),
                "UP projects to visual exponent start");

        machine.dispatch(CnCwKey.RIGHT);
        equal(1, machine.state().semanticCursor().offset(),
                "RIGHT advances inside exponent after vertical transition");
        machine.dispatch(CnCwKey.RIGHT);
        equal(2, machine.state().semanticCursor().offset(),
                "RIGHT reaches exponent end without sticking");
        machine.dispatch(CnCwKey.LEFT);
        equal(1, machine.state().semanticCursor().offset(),
                "LEFT leaves exponent end without sticking");
    }

'''
if marker not in text:
    raise SystemExit("suite insertion marker not found")
text = text.replace(marker, method + marker, 1)
suite_path.write_text(text, encoding="utf-8")

view_path = root / "app/src/main/java/com/codex/fx991smooth/CalculatorView.java"
text = view_path.read_text(encoding="utf-8")
old = '''    /** Horizontal range used by one semantic slot in the current LCD layout. */
    private float[] semanticSpanXRange(CnCwSemanticSpan span) {
        boolean stackedFraction = span.slot() == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                || span.slot() == CnCwCursorPath.Slot.FRACTION_DENOMINATOR;
        int start = stackedFraction ? span.containerStartBoundary() : span.startBoundary();
        int end = stackedFraction ? span.containerEndBoundary() : span.endBoundary();
        float left = displayBoundaryX(start);
        float right = displayBoundaryX(end);
        if (Math.abs(right - left) < dp(4)) {
            left = displayBoundaryX(span.containerStartBoundary());
            right = displayBoundaryX(span.containerEndBoundary());
        }
        return new float[]{left, right};
    }
'''
new = '''    /** Horizontal range used by one semantic slot in the current LCD layout. */
    private float[] semanticSpanXRange(CnCwSemanticSpan span) {
        boolean stackedFraction = span.slot() == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                || span.slot() == CnCwCursorPath.Slot.FRACTION_DENOMINATOR;

        // The structural ^ token is not drawn at full-size between the base and
        // exponent.  Legacy boundary projection nevertheless allocates width to
        // its key label, which shifted exponent hit-testing to the right and made
        // base/exponent dragging feel sticky.  Anchor the exponent at the base's
        // visual end and measure only the visible exponent tokens at 0.62 scale,
        // exactly matching drawNaturalNode(SUPERSCRIPT).
        if (span.slot() == CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT
                && !span.childPath().isEmpty()) {
            int templateBoundary = Math.max(span.containerStartBoundary(),
                    Math.min(span.containerEndBoundary(), span.childPath().get(0)));
            float left = displayBoundaryX(templateBoundary);
            float width = semanticTokenWidth(span.startBoundary(), span.endBoundary(), 0.62f);
            return new float[]{left, left + Math.max(dp(5), width)};
        }

        int start = stackedFraction ? span.containerStartBoundary() : span.startBoundary();
        int end = stackedFraction ? span.containerEndBoundary() : span.endBoundary();
        float left = displayBoundaryX(start);
        float right = displayBoundaryX(end);
        if (Math.abs(right - left) < dp(4)) {
            left = displayBoundaryX(span.containerStartBoundary());
            right = displayBoundaryX(span.containerEndBoundary());
        }
        return new float[]{left, right};
    }

    /** Measures visible token labels for a semantic slot at natural-display scale. */
    private float semanticTokenWidth(int startBoundary, int endBoundary, float textScale) {
        List<String> labels = machine.cursorTokenDisplays();
        int start = Math.max(0, Math.min(labels.size(), startBoundary));
        int end = Math.max(start, Math.min(labels.size(), endBoundary));
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(25f) * textScale);
        float width = 0f;
        for (int index = start; index < end; index++) {
            width += Math.max(dp(3), paint.measureText(labels.get(index)));
        }
        return width;
    }
'''
if old not in text:
    raise SystemExit("semanticSpanXRange target not found")
view_path.write_text(text.replace(old, new, 1), encoding="utf-8")

print("Step 7 power cursor fix applied")

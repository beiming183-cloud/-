from pathlib import Path

root = Path('.')

# -----------------------------------------------------------------------------
# Core: expose independent semantic start/end boundary movement.
# -----------------------------------------------------------------------------
machine_path = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
machine = machine_path.read_text(encoding='utf-8')
machine_anchor = '''    /**
     * Snaps a dragged selection around structures that must stay intact when
     * copied or replaced. Touches may land inside a function, power, or
     * fraction, but the resulting range always contains that complete unit.
     */
    private SelectionRange normalizeTouchSelectionRange(int anchor, int target) {
'''
machine_insert = '''    /** Moves only the left touch-selection handle, preserving the right edge. */
    public CnCwUiState moveTouchSelectionStart(int target) {
        return moveTouchSelectionBoundary(true, target);
    }

    /** Moves only the right touch-selection handle, preserving the left edge. */
    public CnCwUiState moveTouchSelectionEnd(int target) {
        return moveTouchSelectionBoundary(false, target);
    }

    private CnCwUiState moveTouchSelectionBoundary(boolean startBoundary, int target) {
        if (!poweredOn || !screen.isApplication() || applicationLanding || !hasSelection()) {
            return state;
        }
        int currentStart = Math.min(selectionAnchor, selectionFocus);
        int currentEnd = Math.max(selectionAnchor, selectionFocus);
        int clamped = Math.max(0, Math.min(tokens.size(), target));
        SelectionRange range;
        if (startBoundary) {
            clamped = Math.min(clamped, currentEnd - 1);
            range = normalizeTouchSelectionRange(clamped, currentEnd);
            selectionAnchor = range.start;
            selectionFocus = range.end;
            cursor = range.start;
        } else {
            clamped = Math.max(clamped, currentStart + 1);
            range = normalizeTouchSelectionRange(currentStart, clamped);
            selectionAnchor = range.start;
            selectionFocus = range.end;
            cursor = range.end;
        }
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        status = applicationStatus();
        publish();
        return state;
    }

'''
if machine_insert not in machine:
    if machine_anchor not in machine:
        raise SystemExit('machine selection anchor not found')
    machine = machine.replace(machine_anchor, machine_insert + machine_anchor, 1)
machine_path.write_text(machine, encoding='utf-8')

# -----------------------------------------------------------------------------
# Core regression: prove both handles move independently and structures snap.
# -----------------------------------------------------------------------------
suite_path = root / 'core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java'
suite = suite_path.read_text(encoding='utf-8')
run_old = '''        touchSelectionCanAnchorAndExtend();
        directTouchCursorMovesAtomically();
'''
run_new = '''        touchSelectionCanAnchorAndExtend();
        touchSelectionHandlesMoveIndependently();
        directTouchCursorMovesAtomically();
'''
if run_old in suite:
    suite = suite.replace(run_old, run_new, 1)
elif run_new not in suite:
    raise SystemExit('suite run-list anchor not found')

test_anchor = '''    private void shiftedDeleteTogglesOverwriteAndOnIsDistinct() {
'''
test_insert = '''    private void touchSelectionHandlesMoveIndependently() {
        CnCwMachine machine = calculateMachine();
        press(machine, CnCwKey.DIGIT_1, CnCwKey.DIGIT_2, CnCwKey.DIGIT_3,
                CnCwKey.DIGIT_4, CnCwKey.DIGIT_5);
        machine.beginTouchSelection(0);
        machine.extendTouchSelection(5);
        machine.moveTouchSelectionStart(2);
        equal("345", machine.selectedExpression(),
                "left touch handle moves without changing the right boundary");
        equal(2, machine.state().selectionStart(),
                "left touch handle publishes its new boundary");
        equal(5, machine.state().selectionEnd(),
                "left touch handle preserves right boundary");
        machine.moveTouchSelectionEnd(4);
        equal("34", machine.selectedExpression(),
                "right touch handle moves without changing the left boundary");
        equal(2, machine.state().selectionStart(),
                "right touch handle preserves left boundary");
        equal(4, machine.state().selectionEnd(),
                "right touch handle publishes its new boundary");

        machine = calculateMachine();
        press(machine, CnCwKey.SIN, CnCwKey.DIGIT_2, CnCwKey.CLOSE_PAREN);
        machine.beginTouchSelection(0);
        machine.extendTouchSelection(3);
        machine.moveTouchSelectionStart(1);
        equal("sin(2)", machine.selectedExpression(),
                "left handle cannot split an enclosing function call");
        machine.moveTouchSelectionEnd(2);
        equal("sin(2)", machine.selectedExpression(),
                "right handle cannot split an enclosing function call");
    }

'''
if test_insert not in suite:
    if test_anchor not in suite:
        raise SystemExit('suite test anchor not found')
    suite = suite.replace(test_anchor, test_insert + test_anchor, 1)
suite_path.write_text(suite, encoding='utf-8')

# -----------------------------------------------------------------------------
# Android: keep selection after release, expose left/right handles, tap selected
# text to open clipboard menu instead of interrupting the drag on ACTION_UP.
# -----------------------------------------------------------------------------
view_path = root / 'app/src/main/java/com/codex/fx991smooth/CalculatorView.java'
view = view_path.read_text(encoding='utf-8')

field_old = '''    private int lastDragCursor = -1;
    private static final int BODY_EDGE = Color.rgb(48, 55, 52);
'''
field_new = '''    private int lastDragCursor = -1;
    /** -1 = left handle, 0 = choose from drag direction, +1 = right handle. */
    private int selectionDragEdge;
    private boolean selectionTapCandidate;
    private static final int BODY_EDGE = Color.rgb(48, 55, 52);
'''
if field_old in view:
    view = view.replace(field_old, field_new, 1)
elif field_new not in view:
    raise SystemExit('view field anchor not found')

draw_old = '''        drawNaturalExpression(canvas, state.naturalExpression(), lcd, contentTop,
                contentBottom, available);
        if (state.resultShown() && !state.result().isEmpty()) {
'''
draw_new = '''        drawNaturalExpression(canvas, state.naturalExpression(), lcd, contentTop,
                contentBottom, available);
        if (state.hasSelection()) {
            drawSelectionHandles(canvas, lcd, contentTop, contentBottom);
        }
        if (state.resultShown() && !state.result().isEmpty()) {
'''
if draw_old in view:
    view = view.replace(draw_old, draw_new, 1)
elif draw_new not in view:
    raise SystemExit('view draw anchor not found')

handle_anchor = '''    /** Draws a result without losing trailing digits to an ellipsis. */
    private void drawFittedResultText(Canvas canvas, String value, float right,
'''
handle_insert = '''    /** Draws phone-style handles at the two semantic selection boundaries. */
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

'''
if handle_insert not in view:
    if handle_anchor not in view:
        raise SystemExit('view handle anchor not found')
    view = view.replace(handle_anchor, handle_insert + handle_anchor, 1)

old_down = '''                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && displayBounds(getWidth()).contains(event.getX(), event.getY())) {
                    displayPressed = true;
                    displaySelectionMode = false;
                    displayLongPressTriggered = false;
                    displayDownX = event.getX();
                    displayDownY = event.getY();
                    lastDragCursor = state.cursor();
                    displayLongPress = () -> {
                        if (displayPressed) {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            displayLongPressTriggered = true;
                            int anchor = displayCursorPosition(displayDownX);
                            state = machine.selectTouchWord(anchor);
                            lastDragCursor = state.cursor();
                            displaySelectionMode = true;
                            postInvalidateOnAnimation();
                        }
                    };
                    gestureHandler.postDelayed(displayLongPress, 360);
                    return true;
                }
'''
new_down = '''                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
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
                        float startX = displayBoundaryX(state.selectionStart());
                        float endX = displayBoundaryX(state.selectionEnd());
                        float startDistance = Math.abs(event.getX() - startX);
                        float endDistance = Math.abs(event.getX() - endX);
                        float handleSlop = dp(22);
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
                        if (event.getX() >= left && event.getX() <= right) {
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
'''
if old_down in view:
    view = view.replace(old_down, new_down, 1)
elif new_down not in view:
    raise SystemExit('view ACTION_DOWN block not found')

old_move = '''                if (displayPressed) {
                    if (displaySelectionMode) {
                        extendSelectionToDisplayPosition(event.getX());
                        return true;
                    }
                    float dx = event.getX() - displayDownX;
                    float dy = event.getY() - displayDownY;
'''
new_move = '''                if (displayPressed) {
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
'''
if old_move in view:
    view = view.replace(old_move, new_move, 1)
elif new_move not in view:
    raise SystemExit('view ACTION_MOVE block not found')

old_up = '''                    if (displaySelectionMode || displayLongPressTriggered) {
                        displaySelectionMode = false;
                        displayLongPressTriggered = false;
                        showClipboardMenu();
                        return true;
                    }
                    float dx = event.getX() - displayDownX;
                    float dy = event.getY() - displayDownY;
'''
new_up = '''                    if (displaySelectionMode || displayLongPressTriggered) {
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
'''
if old_up in view:
    view = view.replace(old_up, new_up, 1)
elif new_up not in view:
    raise SystemExit('view ACTION_UP block not found')

cancel_old = '''                displayPressed = false;
                displaySelectionMode = false;
                displayLongPressTriggered = false;
                if (displayLongPress != null) gestureHandler.removeCallbacks(displayLongPress);
'''
cancel_new = '''                displayPressed = false;
                displaySelectionMode = false;
                displayLongPressTriggered = false;
                selectionTapCandidate = false;
                selectionDragEdge = 0;
                if (displayLongPress != null) gestureHandler.removeCallbacks(displayLongPress);
'''
if cancel_old in view:
    view = view.replace(cancel_old, cancel_new, 1)
elif cancel_new not in view:
    raise SystemExit('view ACTION_CANCEL block not found')

old_extend = '''    private void extendSelectionToDisplayPosition(float x) {
        int target = displayCursorPosition(x);
        if (target == lastDragCursor) return;
        state = machine.extendTouchSelection(target);
        lastDragCursor = target;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        postInvalidateOnAnimation();
    }
'''
new_extend = '''    private void moveSelectionBoundaryToDisplayPosition(float x) {
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
'''
if old_extend in view:
    view = view.replace(old_extend, new_extend, 1)
elif new_extend not in view:
    raise SystemExit('view selection drag helper not found')

boundary_anchor = '''    private void moveSelectionBoundaryToDisplayPosition(float x) {
'''
boundary_insert = '''    /** Returns the approximate x-coordinate of a semantic insertion boundary. */
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

'''
if boundary_insert not in view:
    if boundary_anchor not in view:
        raise SystemExit('view boundary helper anchor not found')
    view = view.replace(boundary_anchor, boundary_insert + boundary_anchor, 1)

view_path.write_text(view, encoding='utf-8')

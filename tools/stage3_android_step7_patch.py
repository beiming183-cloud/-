from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"anchor not found: {label}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# CnCwUiState: publish semantic editable spans to platform adapters.
# -----------------------------------------------------------------------------
state_path = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwUiState.java")
state = state_path.read_text(encoding="utf-8")
state = replace_once(
    state,
    "    /** Stage 3 compatibility view of the cursor as a semantic editor position. */\n"
    "    private final CnCwCursorPath semanticCursor;\n"
    "    private final int selectionStart;",
    "    /** Stage 3 compatibility view of the cursor as a semantic editor position. */\n"
    "    private final CnCwCursorPath semanticCursor;\n"
    "    /** Editable semantic regions used by geometry-aware platform hit testing. */\n"
    "    private final List<CnCwSemanticSpan> semanticSpans;\n"
    "    private final int selectionStart;",
    "ui state semantic span field",
)
state = replace_once(
    state,
    "                int cursor,\n"
    "                CnCwCursorPath semanticCursor,\n"
    "                int selectionStart,",
    "                int cursor,\n"
    "                CnCwCursorPath semanticCursor,\n"
    "                List<CnCwSemanticSpan> semanticSpans,\n"
    "                int selectionStart,",
    "ui state constructor semantic spans",
)
state = replace_once(
    state,
    "        this.cursor = Math.max(0, cursor);\n"
    "        this.semanticCursor = Objects.requireNonNull(semanticCursor, \"semanticCursor\");\n"
    "        this.selectionStart = Math.max(0, selectionStart);",
    "        this.cursor = Math.max(0, cursor);\n"
    "        this.semanticCursor = Objects.requireNonNull(semanticCursor, \"semanticCursor\");\n"
    "        this.semanticSpans = com.codex.fx991.core.Compat.copyList(semanticSpans);\n"
    "        this.selectionStart = Math.max(0, selectionStart);",
    "ui state semantic span assignment",
)
state = replace_once(
    state,
    "    public CnCwCursorPath semanticCursor() { return semanticCursor; }\n"
    "    public boolean hasSelection()",
    "    public CnCwCursorPath semanticCursor() { return semanticCursor; }\n"
    "    /** Semantic editable regions for platform hit testing; immutable snapshot. */\n"
    "    public List<CnCwSemanticSpan> semanticSpans() { return semanticSpans; }\n"
    "    public boolean hasSelection()",
    "ui state semantic span getter",
)
state_path.write_text(state, encoding="utf-8")


# -----------------------------------------------------------------------------
# CnCwMachine: semantic touch API + semantic span publication.
# -----------------------------------------------------------------------------
machine_path = Path("core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java")
machine = machine_path.read_text(encoding="utf-8")

move_anchor = '''    /** Starts a touch-driven text selection at a semantic insertion boundary. */\n'''
move_api = '''    /**\n     * Semantic touch entry point used by Stage 3 platform adapters. Invalid or stale\n     * paths fail closed to their retained legacy boundary instead of corrupting a\n     * structure.\n     */\n    public CnCwUiState moveCursorTo(CnCwCursorPath target) {\n        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;\n        if (!applySemanticTouchCursor(target)) {\n            semanticCursorOverride = null;\n            cursor = semanticTouchBoundary(target);\n        }\n        clearSelection();\n        shiftArmed = false;\n        result = \"\";\n        resultShown = false;\n        errorShown = false;\n        lastError = null;\n        publish();\n        return state;\n    }\n\n    /** Validates and installs one semantic insertion path. */\n    private boolean applySemanticTouchCursor(CnCwCursorPath target) {\n        if (target == null) return false;\n        if (target.isRootBoundary()) {\n            setRootCursor(target.legacyTokenBoundary());\n            return true;\n        }\n        if (target.childPath().isEmpty()) return false;\n        int template = target.childPath().get(0);\n        switch (target.slot()) {\n            case FRACTION_NUMERATOR, FRACTION_DENOMINATOR -> {\n                FractionBounds bounds = fractionBounds(template);\n                if (bounds == null) return false;\n                setFractionCursor(bounds, target.slot(), target.offset());\n                return true;\n            }\n            case SUPERSCRIPT_BASE, SUPERSCRIPT_EXPONENT -> {\n                PowerBounds bounds = powerBounds(template);\n                if (bounds == null) return false;\n                setPowerCursor(bounds, target.slot(), target.offset());\n                return true;\n            }\n            case RADICAL_CONTENT, ROOT_INDEX, ROOT_CONTENT -> {\n                RadicalBounds bounds = radicalBounds(template);\n                if (bounds == null) return false;\n                Token token = tokens.get(template);\n                if (target.slot() == CnCwCursorPath.Slot.ROOT_INDEX\n                        && !isGenericRootTemplate(token)) return false;\n                if (target.slot() == CnCwCursorPath.Slot.RADICAL_CONTENT\n                        && !isSquareRootTemplate(token)) return false;\n                if (target.slot() == CnCwCursorPath.Slot.ROOT_CONTENT\n                        && isSquareRootTemplate(token)) return false;\n                setRadicalCursor(bounds, target.slot(), target.offset());\n                return true;\n            }\n            case FUNCTION_ARGUMENT -> {\n                if (target.childPath().size() < 2) return false;\n                FunctionBounds bounds = functionBounds(template);\n                int argument = target.childPath().get(1);\n                if (bounds == null || argument < 0 || argument >= bounds.arguments.size()) {\n                    return false;\n                }\n                setFunctionCursor(bounds, argument, target.offset());\n                return true;\n            }\n            case ROW -> {\n                setRootCursor(target.legacyTokenBoundary());\n                return true;\n            }\n        }\n        return false;\n    }\n\n    private int semanticTouchBoundary(CnCwCursorPath target) {\n        if (target == null) return cursor;\n        return Math.max(0, Math.min(tokens.size(), target.legacyTokenBoundary()));\n    }\n\n'''
machine = replace_once(machine, move_anchor, move_api + move_anchor,
                       "machine semantic move API")

selection_wrapper_anchor = '''    /**\n     * Snaps a dragged selection around structures that must stay intact when\n'''
selection_wrappers = '''    /** Semantic-path overloads used by the Android Stage 3 touch adapter. */\n    public CnCwUiState beginTouchSelection(CnCwCursorPath target) {\n        int boundary = semanticTouchBoundary(target);\n        CnCwUiState value = beginTouchSelection(boundary);\n        if (applySemanticTouchCursor(target)) {\n            selectionAnchor = cursor;\n            selectionFocus = cursor;\n            publish();\n            return state;\n        }\n        return value;\n    }\n\n    public CnCwUiState selectTouchWord(CnCwCursorPath target) {\n        return selectTouchWord(semanticTouchBoundary(target));\n    }\n\n    public CnCwUiState extendTouchSelection(CnCwCursorPath target) {\n        return extendTouchSelection(semanticTouchBoundary(target));\n    }\n\n    public CnCwUiState moveTouchSelectionStart(CnCwCursorPath target) {\n        return moveTouchSelectionStart(semanticTouchBoundary(target));\n    }\n\n    public CnCwUiState moveTouchSelectionEnd(CnCwCursorPath target) {\n        return moveTouchSelectionEnd(semanticTouchBoundary(target));\n    }\n\n'''
machine = replace_once(machine, selection_wrapper_anchor,
                       selection_wrappers + selection_wrapper_anchor,
                       "machine semantic selection overloads")

span_anchor = '''    /** Smallest semantic slot containing the complete normalized selection. */\n    private SemanticSelectionScope semanticSelectionScope(int start, int end) {\n'''
span_method = '''    /**\n     * Publishes every currently editable nested slot. The Android adapter uses\n     * these spans for geometry-aware hit testing while legacy token boundaries\n     * remain available as a fallback.\n     */\n    private List<CnCwSemanticSpan> semanticSpans() {\n        List<CnCwSemanticSpan> spans = new ArrayList<>();\n        for (int template = 0; template < tokens.size(); template++) {\n            FractionBounds fraction = fractionBounds(template);\n            if (fraction != null) {\n                spans.add(new CnCwSemanticSpan(\n                        com.codex.fx991.core.Compat.list(template),\n                        CnCwCursorPath.Slot.FRACTION_NUMERATOR,\n                        fraction.numeratorStart, fraction.numeratorEnd,\n                        fraction.numeratorStart, fraction.denominatorEnd));\n                spans.add(new CnCwSemanticSpan(\n                        com.codex.fx991.core.Compat.list(template),\n                        CnCwCursorPath.Slot.FRACTION_DENOMINATOR,\n                        fraction.denominatorStart, fraction.denominatorEnd,\n                        fraction.numeratorStart, fraction.denominatorEnd));\n            }\n\n            PowerBounds power = powerBounds(template);\n            if (power != null) {\n                spans.add(new CnCwSemanticSpan(\n                        com.codex.fx991.core.Compat.list(template),\n                        CnCwCursorPath.Slot.SUPERSCRIPT_BASE,\n                        power.baseStart, power.baseEnd,\n                        power.baseStart, power.exponentEnd));\n                spans.add(new CnCwSemanticSpan(\n                        com.codex.fx991.core.Compat.list(template),\n                        CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,\n                        power.exponentStart, power.exponentEnd,\n                        power.baseStart, power.exponentEnd));\n            }\n\n            RadicalBounds radical = radicalBounds(template);\n            if (radical != null) {\n                Token token = tokens.get(template);\n                if (radical.indexStart >= 0) {\n                    spans.add(new CnCwSemanticSpan(\n                            com.codex.fx991.core.Compat.list(template),\n                            CnCwCursorPath.Slot.ROOT_INDEX,\n                            radical.indexStart, radical.indexEnd,\n                            radical.templateIndex, radical.endExclusive));\n                }\n                CnCwCursorPath.Slot contentSlot = isSquareRootTemplate(token)\n                        ? CnCwCursorPath.Slot.RADICAL_CONTENT\n                        : CnCwCursorPath.Slot.ROOT_CONTENT;\n                spans.add(new CnCwSemanticSpan(\n                        com.codex.fx991.core.Compat.list(template), contentSlot,\n                        radical.contentStart, radical.contentEnd,\n                        radical.templateIndex, radical.endExclusive));\n            }\n\n            FunctionBounds function = functionBounds(template);\n            if (function != null) {\n                for (int argumentIndex = 0; argumentIndex < function.arguments.size();\n                     argumentIndex++) {\n                    FunctionArgumentBounds argument = function.arguments.get(argumentIndex);\n                    spans.add(new CnCwSemanticSpan(\n                            com.codex.fx991.core.Compat.list(template, argumentIndex),\n                            CnCwCursorPath.Slot.FUNCTION_ARGUMENT,\n                            argument.start, argument.end,\n                            function.templateIndex, function.endExclusive));\n                }\n            }\n        }\n        return com.codex.fx991.core.Compat.copyList(spans);\n    }\n\n'''
machine = replace_once(machine, span_anchor, span_method + span_anchor,
                       "machine semantic span publisher")

machine = replace_once(
    machine,
    "                naturalExpression(), cursor, semanticCursorPath(),\n"
    "                selectionStartIndex(), selectionEndIndex(),",
    "                naturalExpression(), cursor, semanticCursorPath(), semanticSpans(),\n"
    "                selectionStartIndex(), selectionEndIndex(),",
    "machine publish semantic spans",
)
machine_path.write_text(machine, encoding="utf-8")


# -----------------------------------------------------------------------------
# Android: use semantic spans + y geometry, keep legacy boundary fallback.
# -----------------------------------------------------------------------------
view_path = Path("app/src/main/java/com/codex/fx991smooth/CalculatorView.java")
view = view_path.read_text(encoding="utf-8")
view = replace_once(
    view,
    "import com.codex.fx991.core.cw.CnCwCommand;\n"
    "import com.codex.fx991.core.cw.CnCwExpressionNode;",
    "import com.codex.fx991.core.cw.CnCwCommand;\n"
    "import com.codex.fx991.core.cw.CnCwCursorPath;\n"
    "import com.codex.fx991.core.cw.CnCwExpressionNode;\n"
    "import com.codex.fx991.core.cw.CnCwSemanticSpan;",
    "Android semantic imports",
)
view = replace_once(
    view,
    "    private int lastDragCursor = -1;\n"
    "    /** -1 = left handle, 0 = choose from drag direction, +1 = right handle. */",
    "    private int lastDragCursor = -1;\n"
    "    private CnCwCursorPath lastDragSemantic;\n"
    "    /** -1 = left handle, 0 = choose from drag direction, +1 = right handle. */",
    "Android last semantic drag field",
)
view = replace_once(
    view,
    "                    lastDragCursor = state.cursor();\n\n"
    "                    if (state.hasSelection()) {",
    "                    lastDragCursor = state.cursor();\n"
    "                    lastDragSemantic = state.semanticCursor();\n\n"
    "                    if (state.hasSelection()) {",
    "touch down semantic drag state",
)
view = replace_once(
    view,
    "                        float startX = displayBoundaryX(state.selectionStart());\n"
    "                        float endX = displayBoundaryX(state.selectionEnd());",
    "                        CnCwCursorPath startPath = semanticSelectionPathForBoundary(\n"
    "                                state.selectionStart());\n"
    "                        CnCwCursorPath endPath = semanticSelectionPathForBoundary(\n"
    "                                state.selectionEnd());\n"
    "                        float startX = displaySemanticBoundaryX(startPath);\n"
    "                        float endX = displaySemanticBoundaryX(endPath);",
    "selection handle semantic x",
)
view = replace_once(
    view,
    "                            lastDragCursor = selectionDragEdge < 0\n"
    "                                    ? state.selectionStart() : state.selectionEnd();",
    "                            lastDragCursor = selectionDragEdge < 0\n"
    "                                    ? state.selectionStart() : state.selectionEnd();\n"
    "                            lastDragSemantic = selectionDragEdge < 0\n"
    "                                    ? startPath : endPath;",
    "selection handle semantic drag state",
)
view = replace_once(
    view,
    "                            int anchor = displayCursorPosition(displayDownX);\n"
    "                            state = machine.selectTouchWord(anchor);\n"
    "                            lastDragCursor = state.cursor();",
    "                            CnCwCursorPath anchor = displaySemanticPosition(\n"
    "                                    displayDownX, displayDownY);\n"
    "                            state = machine.selectTouchWord(anchor);\n"
    "                            lastDragCursor = state.cursor();\n"
    "                            lastDragSemantic = state.semanticCursor();",
    "long press semantic anchor",
)
view = replace_once(
    view,
    "                        moveSelectionBoundaryToDisplayPosition(event.getX());",
    "                        moveSelectionBoundaryToDisplayPosition(event.getX(), event.getY());",
    "selection drag semantic coordinates",
)
view = replace_once(
    view,
    "                        moveCursorToDisplayPosition(event.getX(), true);",
    "                        moveCursorToDisplayPosition(event.getX(), event.getY(), true);",
    "swipe cursor semantic coordinates",
)
view = replace_once(
    view,
    "                        moveCursorToDisplayPosition(event.getX(), false);",
    "                        moveCursorToDisplayPosition(event.getX(), event.getY(), false);",
    "tap cursor semantic coordinates",
)

old_move = '''    private void moveCursorToDisplayPosition(float x, boolean haptic) {\n        moveCursorAtomically(displayCursorPosition(x), haptic);\n    }\n\n'''
new_move = '''    private void moveCursorToDisplayPosition(float x, float y, boolean haptic) {\n        moveCursorAtomically(displaySemanticPosition(x, y), haptic);\n    }\n\n'''
view = replace_once(view, old_move, new_move, "semantic display cursor move")

# Insert semantic hit helpers before the legacy displayCursorPosition fallback.
hit_anchor = '''    /** Converts a display x-coordinate into the nearest semantic boundary. */\n    private int displayCursorPosition(float x) {\n'''
hit_helpers = '''    /**\n     * Maps screen geometry to a semantic editor position. Nested spans win when\n     * the pointer is vertically close to their visual slot; otherwise the old\n     * x-only token boundary remains the safe fallback.\n     */\n    private CnCwCursorPath displaySemanticPosition(float x, float y) {\n        int legacy = displayCursorPosition(x);\n        List<CnCwSemanticSpan> spans = state.semanticSpans();\n        if (spans.isEmpty()) return CnCwCursorPath.rootBoundary(legacy);\n\n        RectF lcd = displayBounds(getWidth());\n        float contentTop = lcd.top + lcd.height() * 0.145f;\n        float contentBottom = lcd.bottom - dp(3);\n        float baseSize = sp(25f);\n        NaturalMetrics metrics = measureNatural(state.naturalExpression(), baseSize);\n        float baseline = Math.min(contentTop + metrics.top + dp(2),\n                contentTop + (contentBottom - contentTop) * 0.54f);\n\n        CnCwSemanticSpan best = null;\n        float bestScore = Float.MAX_VALUE;\n        for (CnCwSemanticSpan span : spans) {\n            float[] range = semanticSpanXRange(span);\n            float left = Math.min(range[0], range[1]);\n            float right = Math.max(range[0], range[1]);\n            float pad = dp(12);\n            if (x < left - pad || x > right + pad) continue;\n            float centerY = semanticSlotCenterY(span.slot(), baseline, baseSize);\n            float tolerance = semanticSlotYTolerance(span.slot(), baseSize);\n            float vertical = Math.abs(y - centerY);\n            if (vertical > tolerance) continue;\n            float horizontal = x < left ? left - x : x > right ? x - right : 0f;\n            float widthPenalty = Math.max(dp(4), right - left) * 0.018f;\n            float depthBonus = span.childPath().size() * dp(1.5f);\n            float score = vertical + horizontal * 1.25f + widthPenalty - depthBonus;\n            if (score < bestScore) {\n                bestScore = score;\n                best = span;\n            }\n        }\n        if (best == null) return CnCwCursorPath.rootBoundary(legacy);\n\n        float[] range = semanticSpanXRange(best);\n        float left = Math.min(range[0], range[1]);\n        float right = Math.max(range[0], range[1]);\n        int length = best.length();\n        int offset;\n        if (length <= 0 || right - left < dp(1)) {\n            offset = 0;\n        } else {\n            float ratio = Math.max(0f, Math.min(1f, (x - left) / (right - left)));\n            offset = Math.round(ratio * length);\n        }\n        return best.position(offset);\n    }\n\n    /** Horizontal range used by one semantic slot in the current LCD layout. */\n    private float[] semanticSpanXRange(CnCwSemanticSpan span) {\n        boolean stackedFraction = span.slot() == CnCwCursorPath.Slot.FRACTION_NUMERATOR\n                || span.slot() == CnCwCursorPath.Slot.FRACTION_DENOMINATOR;\n        int start = stackedFraction ? span.containerStartBoundary() : span.startBoundary();\n        int end = stackedFraction ? span.containerEndBoundary() : span.endBoundary();\n        float left = displayBoundaryX(start);\n        float right = displayBoundaryX(end);\n        if (Math.abs(right - left) < dp(4)) {\n            left = displayBoundaryX(span.containerStartBoundary());\n            right = displayBoundaryX(span.containerEndBoundary());\n        }\n        return new float[]{left, right};\n    }\n\n    private float semanticSlotCenterY(CnCwCursorPath.Slot slot,\n                                      float baseline, float baseSize) {\n        return switch (slot) {\n            case FRACTION_NUMERATOR -> baseline - baseSize * 0.63f;\n            case FRACTION_DENOMINATOR -> baseline + baseSize * 0.30f;\n            case SUPERSCRIPT_EXPONENT, ROOT_INDEX -> baseline - baseSize * 0.55f;\n            case SUPERSCRIPT_BASE, RADICAL_CONTENT, ROOT_CONTENT, FUNCTION_ARGUMENT, ROW ->\n                    baseline - baseSize * 0.18f;\n        };\n    }\n\n    private float semanticSlotYTolerance(CnCwCursorPath.Slot slot, float baseSize) {\n        return switch (slot) {\n            case FRACTION_NUMERATOR, FRACTION_DENOMINATOR -> baseSize * 0.58f;\n            case SUPERSCRIPT_EXPONENT, ROOT_INDEX -> baseSize * 0.48f;\n            default -> baseSize * 0.62f;\n        };\n    }\n\n'''
view = replace_once(view, hit_anchor, hit_helpers + hit_anchor,
                    "semantic hit helpers")

# Insert semantic boundary projection after legacy displayBoundaryX.
boundary_anchor = '''    private void moveSelectionBoundaryToDisplayPosition(float x) {\n'''
boundary_helpers = '''    private CnCwCursorPath semanticSelectionPathForBoundary(int boundary) {\n        CnCwCursorPath anchor = state.semanticSelectionAnchor();\n        CnCwCursorPath focus = state.semanticSelectionFocus();\n        if (anchor != null && anchor.legacyTokenBoundary() == boundary) return anchor;\n        if (focus != null && focus.legacyTokenBoundary() == boundary) return focus;\n        return CnCwCursorPath.rootBoundary(boundary);\n    }\n\n    /** Projects a semantic selection/cursor boundary back onto the LCD x-axis. */\n    private float displaySemanticBoundaryX(CnCwCursorPath path) {\n        if (path == null || path.isRootBoundary()) {\n            return displayBoundaryX(path == null ? state.cursor() : path.legacyTokenBoundary());\n        }\n        for (CnCwSemanticSpan span : state.semanticSpans()) {\n            if (!span.matches(path)) continue;\n            float[] range = semanticSpanXRange(span);\n            float left = range[0];\n            float right = range[1];\n            if (span.length() <= 0) return left;\n            float ratio = Math.max(0f, Math.min(1f,\n                    path.offset() / (float) span.length()));\n            return left + (right - left) * ratio;\n        }\n        return displayBoundaryX(path.legacyTokenBoundary());\n    }\n\n    private void moveSelectionBoundaryToDisplayPosition(float x, float y) {\n'''
view = replace_once(view, boundary_anchor, boundary_helpers,
                    "semantic selection boundary projection")

old_selection_move = '''        int target = displayCursorPosition(x);\n        if (selectionDragEdge == 0) {\n            if (target <= state.selectionStart() || x < displayDownX) selectionDragEdge = -1;\n            else if (target >= state.selectionEnd() || x > displayDownX) selectionDragEdge = 1;\n            else return;\n        }\n        if (target == lastDragCursor) return;\n        state = selectionDragEdge < 0\n                ? machine.moveTouchSelectionStart(target)\n                : machine.moveTouchSelectionEnd(target);\n        lastDragCursor = selectionDragEdge < 0\n                ? state.selectionStart() : state.selectionEnd();\n        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);\n        postInvalidateOnAnimation();\n    }\n\n    private void moveCursorAtomically(int target, boolean haptic) {\n        int clamped = Math.max(0, Math.min(machine.cursorLimit(), target));\n        if (clamped == lastDragCursor && haptic) return;\n        state = machine.moveCursorTo(clamped);\n        lastDragCursor = clamped;\n        if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);\n        postInvalidateOnAnimation();\n    }\n'''
new_selection_move = '''        CnCwCursorPath target = displaySemanticPosition(x, y);\n        int legacy = target.legacyTokenBoundary();\n        if (selectionDragEdge == 0) {\n            if (legacy <= state.selectionStart() || x < displayDownX) selectionDragEdge = -1;\n            else if (legacy >= state.selectionEnd() || x > displayDownX) selectionDragEdge = 1;\n            else return;\n        }\n        if (target.equals(lastDragSemantic)) return;\n        state = selectionDragEdge < 0\n                ? machine.moveTouchSelectionStart(target)\n                : machine.moveTouchSelectionEnd(target);\n        lastDragCursor = selectionDragEdge < 0\n                ? state.selectionStart() : state.selectionEnd();\n        lastDragSemantic = semanticSelectionPathForBoundary(lastDragCursor);\n        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);\n        postInvalidateOnAnimation();\n    }\n\n    private void moveCursorAtomically(CnCwCursorPath target, boolean haptic) {\n        if (target == null) target = CnCwCursorPath.rootBoundary(state.cursor());\n        if (haptic && target.equals(lastDragSemantic)) return;\n        state = machine.moveCursorTo(target);\n        lastDragCursor = state.cursor();\n        lastDragSemantic = state.semanticCursor();\n        if (haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);\n        postInvalidateOnAnimation();\n    }\n'''
view = replace_once(view, old_selection_move, new_selection_move,
                    "semantic selection/cursor movement")

# Selection handles also project semantic endpoints rather than flat token x positions.
view = replace_once(
    view,
    "        float startX = displayBoundaryX(state.selectionStart());\n"
    "        float endX = displayBoundaryX(state.selectionEnd());",
    "        float startX = displaySemanticBoundaryX(\n"
    "                semanticSelectionPathForBoundary(state.selectionStart()));\n"
    "        float endX = displaySemanticBoundaryX(\n"
    "                semanticSelectionPathForBoundary(state.selectionEnd()));",
    "draw semantic selection handles",
)
view_path.write_text(view, encoding="utf-8")


# -----------------------------------------------------------------------------
# Regression: semantic spans + semantic touch APIs must round-trip.
# -----------------------------------------------------------------------------
test_path = Path("core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java")
tests = test_path.read_text(encoding="utf-8")
tests = replace_once(
    tests,
    "import com.codex.fx991.core.cw.CnCwMachine;\n"
    "import com.codex.fx991.core.mode.CnCwModel;",
    "import com.codex.fx991.core.cw.CnCwMachine;\n"
    "import com.codex.fx991.core.cw.CnCwSemanticSpan;\n"
    "import com.codex.fx991.core.mode.CnCwModel;",
    "cursor suite semantic span import",
)
tests = replace_once(
    tests,
    "        semanticSelectionDeleteAndPasteStayInFunctionArgument();\n"
    "        System.out.println(\"PASS \" + checks + \" semantic cursor checks\");",
    "        semanticSelectionDeleteAndPasteStayInFunctionArgument();\n"
    "        semanticTouchSpansAndPathsRoundTrip();\n"
    "        System.out.println(\"PASS \" + checks + \" semantic cursor checks\");",
    "cursor suite Step 7 run hook",
)
helper_anchor = '''    private CnCwMachine sinMachine(boolean closed) {\n'''
new_test = '''    private void semanticTouchSpansAndPathsRoundTrip() {\n        CnCwMachine machine = fractionMachine();\n        CnCwSemanticSpan denominator = findSpan(machine,\n                CnCwCursorPath.Slot.FRACTION_DENOMINATOR, 0);\n        check(denominator != null, \"fraction denominator publishes semantic touch span\");\n        equal(1, denominator.length(), \"fraction touch span keeps slot token length\");\n        machine.moveCursorTo(denominator.position(0));\n        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,\n                machine.state().semanticCursor().slot(),\n                \"semantic touch move enters denominator directly\");\n        equal(0, machine.state().semanticCursor().offset(),\n                \"semantic touch move keeps denominator-local offset\");\n\n        machine = sinMachine(true);\n        CnCwSemanticSpan argument = findSpan(machine,\n                CnCwCursorPath.Slot.FUNCTION_ARGUMENT, 0);\n        check(argument != null, \"function argument publishes semantic touch span\");\n        equal(Compat.list(0, 0), argument.childPath(),\n                \"function span identifies template and argument index\");\n        machine.moveCursorTo(argument.position(1));\n        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,\n                machine.state().semanticCursor().slot(),\n                \"semantic touch cursor enters function argument\");\n        equal(1, machine.state().semanticCursor().offset(),\n                \"function semantic touch keeps local offset\");\n\n        machine.beginTouchSelection(argument.position(0));\n        machine.extendTouchSelection(argument.position(argument.length()));\n        equal(\"30\", machine.selectedExpression(),\n                \"semantic touch paths select only the function argument\");\n        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,\n                machine.state().semanticSelectionAnchor().slot(),\n                \"semantic path selection publishes argument anchor\");\n        equal(CnCwCursorPath.Slot.FUNCTION_ARGUMENT,\n                machine.state().semanticSelectionFocus().slot(),\n                \"semantic path selection publishes argument focus\");\n\n        machine = nthRootMachine(true);\n        CnCwSemanticSpan rootIndex = findSpan(machine, CnCwCursorPath.Slot.ROOT_INDEX, 0);\n        CnCwSemanticSpan rootContent = findSpan(machine, CnCwCursorPath.Slot.ROOT_CONTENT, 0);\n        check(rootIndex != null && rootContent != null,\n                \"nth root publishes independent index/content touch spans\");\n        machine.moveCursorTo(rootIndex.position(1));\n        equal(CnCwCursorPath.Slot.ROOT_INDEX, machine.state().semanticCursor().slot(),\n                \"semantic touch can land in root index\");\n        machine.moveCursorTo(rootContent.position(1));\n        equal(CnCwCursorPath.Slot.ROOT_CONTENT, machine.state().semanticCursor().slot(),\n                \"semantic touch can land in root content\");\n    }\n\n    private CnCwSemanticSpan findSpan(CnCwMachine machine, CnCwCursorPath.Slot slot,\n                                      int childTail) {\n        for (CnCwSemanticSpan span : machine.state().semanticSpans()) {\n            if (span.slot() != slot) continue;\n            if (slot == CnCwCursorPath.Slot.FUNCTION_ARGUMENT\n                    && (span.childPath().size() < 2\n                    || span.childPath().get(1) != childTail)) continue;\n            return span;\n        }\n        return null;\n    }\n\n'''
tests = replace_once(tests, helper_anchor, new_test + helper_anchor,
                     "cursor suite Step 7 tests")
test_path.write_text(tests, encoding="utf-8")

print("Stage 3 Step 7 semantic Android migration patch applied")

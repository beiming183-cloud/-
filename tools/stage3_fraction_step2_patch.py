from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
text = machine_path.read_text(encoding='utf-8')


def replace_once(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f'{label} anchor not found')
    text = text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1. Keep a semantic-position override so the same legacy token boundary can
#    distinguish “inside fraction” from “outside fraction”.
# ---------------------------------------------------------------------------
replace_once(
    '    private int cursor;\n    /** Inclusive anchor and exclusive focus for semantic token selection. */',
    '    private int cursor;\n'
    '    /** Stage 3 nested position override; null falls back to legacy-boundary inference. */\n'
    '    private CnCwCursorPath semanticCursorOverride;\n'
    '    /** Inclusive anchor and exclusive focus for semantic token selection. */',
    'semantic cursor field')

replace_once(
    '        cursor = source.cursor;\n        selectionAnchor = source.selectionAnchor;',
    '        cursor = source.cursor;\n        semanticCursorOverride = source.semanticCursorOverride;\n'
    '        selectionAnchor = source.selectionAnchor;',
    'copy semantic cursor')

replace_once(
    '        cursor = 0;\n        clearSelection();\n        shiftArmed = false;',
    '        cursor = 0;\n        semanticCursorOverride = null;\n        clearSelection();\n        shiftArmed = false;',
    'reset semantic cursor')

# Touch APIs intentionally remain legacy-boundary adapters during Stage 3.
replace_once(
    '        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;\n'
    '        cursor = Math.max(0, Math.min(tokens.size(), target));\n'
    '        clearSelection();',
    '        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;\n'
    '        semanticCursorOverride = null;\n'
    '        cursor = Math.max(0, Math.min(tokens.size(), target));\n'
    '        clearSelection();',
    'moveCursorTo semantic reset')

# beginTouchSelection has the same initial shape after moveCursorTo, so patch
# the next remaining occurrence explicitly.
needle = ('    public CnCwUiState beginTouchSelection(int target) {\n'
          '        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;\n'
          '        cursor = Math.max(0, Math.min(tokens.size(), target));')
replacement = ('    public CnCwUiState beginTouchSelection(int target) {\n'
               '        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;\n'
               '        semanticCursorOverride = null;\n'
               '        cursor = Math.max(0, Math.min(tokens.size(), target));')
replace_once(needle, replacement, 'beginTouchSelection semantic reset')

needle = ('    public CnCwUiState selectTouchWord(int target) {\n'
          '        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;\n'
          '        int boundary = Math.max(0, Math.min(tokens.size(), target));')
replacement = ('    public CnCwUiState selectTouchWord(int target) {\n'
               '        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;\n'
               '        semanticCursorOverride = null;\n'
               '        int boundary = Math.max(0, Math.min(tokens.size(), target));')
replace_once(needle, replacement, 'selectTouchWord semantic reset')

needle = ('    public CnCwUiState extendTouchSelection(int target) {\n'
          '        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;')
replacement = ('    public CnCwUiState extendTouchSelection(int target) {\n'
               '        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;\n'
               '        semanticCursorOverride = null;')
replace_once(needle, replacement, 'extendTouchSelection semantic reset')

needle = ('    private CnCwUiState moveTouchSelectionBoundary(boolean startBoundary, int target) {\n'
          '        if (!poweredOn || !screen.isApplication() || applicationLanding || !hasSelection()) {')
replacement = ('    private CnCwUiState moveTouchSelectionBoundary(boolean startBoundary, int target) {\n'
               '        if (!poweredOn || !screen.isApplication() || applicationLanding || !hasSelection()) {')
# Keep anchor check, then inject after the guard block using a second anchor.
if needle not in text:
    raise SystemExit('moveTouchSelectionBoundary anchor not found')
replace_once(
    '            return state;\n        }\n        int currentStart = Math.min(selectionAnchor, selectionFocus);',
    '            return state;\n        }\n        semanticCursorOverride = null;\n'
    '        int currentStart = Math.min(selectionAnchor, selectionFocus);',
    'moveTouchSelectionBoundary semantic reset')

# ---------------------------------------------------------------------------
# 2. Horizontal arrows now enter/leave fraction slots semantically.  Selection
#    keeps the Stage 2 protocol and clears the nested override.
# ---------------------------------------------------------------------------
old_nav = '''            case LEFT -> {\n                if (shiftArmed) extendSelection(-1);\n                else {\n                    collapseSelection(-1);\n                    cursor = Math.max(0, cursor - 1);\n                }\n                shiftArmed = false;\n                result = "";\n                resultShown = false;\n                errorShown = false;\n                lastError = null;\n            }\n            case RIGHT -> {\n                if (shiftArmed) extendSelection(1);\n                else {\n                    collapseSelection(1);\n                    cursor = Math.min(tokens.size(), cursor + 1);\n                }\n                shiftArmed = false;\n                result = "";\n                resultShown = false;\n                errorShown = false;\n                lastError = null;\n            }'''
new_nav = '''            case LEFT -> {\n                if (shiftArmed) {\n                    semanticCursorOverride = null;\n                    extendSelection(-1);\n                } else {\n                    boolean hadSelection = selectionActive();\n                    collapseSelection(-1);\n                    if (hadSelection) {\n                        semanticCursorOverride = null;\n                        cursor = Math.max(0, cursor - 1);\n                    } else if (!moveFractionHorizontal(-1)) {\n                        semanticCursorOverride = null;\n                        cursor = Math.max(0, cursor - 1);\n                    }\n                }\n                shiftArmed = false;\n                result = "";\n                resultShown = false;\n                errorShown = false;\n                lastError = null;\n            }\n            case RIGHT -> {\n                if (shiftArmed) {\n                    semanticCursorOverride = null;\n                    extendSelection(1);\n                } else {\n                    boolean hadSelection = selectionActive();\n                    collapseSelection(1);\n                    if (hadSelection) {\n                        semanticCursorOverride = null;\n                        cursor = Math.min(tokens.size(), cursor + 1);\n                    } else if (!moveFractionHorizontal(1)) {\n                        semanticCursorOverride = null;\n                        cursor = Math.min(tokens.size(), cursor + 1);\n                    }\n                }\n                shiftArmed = false;\n                result = "";\n                resultShown = false;\n                errorShown = false;\n                lastError = null;\n            }'''
replace_once(old_nav, new_nav, 'application horizontal navigation')

# Error-overlay arrows are still legacy behavior but must not retain a stale nested slot.
replace_once(
    '                case LEFT -> {\n                    dismissError();\n                    cursor = Math.max(0, cursor - 1);',
    '                case LEFT -> {\n                    dismissError();\n                    semanticCursorOverride = null;\n                    cursor = Math.max(0, cursor - 1);',
    'error LEFT semantic reset')
replace_once(
    '                case RIGHT -> {\n                    dismissError();\n                    cursor = Math.min(tokens.size(), cursor + 1);',
    '                case RIGHT -> {\n                    dismissError();\n                    semanticCursorOverride = null;\n                    cursor = Math.min(tokens.size(), cursor + 1);',
    'error RIGHT semantic reset')

# ---------------------------------------------------------------------------
# 3. Semantic editing clears stale overrides, while DEL first gives the
#    fraction editor a chance to preserve/remove the structure atomically.
# ---------------------------------------------------------------------------
replace_once(
    '        if (token == null) return;\n        resetStatementSequence();',
    '        if (token == null) return;\n        semanticCursorOverride = null;\n        resetStatementSequence();',
    'insertKey semantic reset')

replace_once(
    '    private void deleteBeforeCursor() {\n        shiftArmed = false;\n        if (deleteSelectionIfPresent()) return;\n        if (cursor <= 0 || tokens.isEmpty()) return;',
    '    private void deleteBeforeCursor() {\n        shiftArmed = false;\n        if (deleteSelectionIfPresent()) return;\n'
    '        if (deleteFractionSemantic()) return;\n'
    '        if (cursor <= 0 || tokens.isEmpty()) return;\n'
    '        semanticCursorOverride = null;',
    'semantic delete hook')

replace_once(
    '    private void extendSelection(int direction) {\n        if (selectionAnchor < 0) selectionAnchor = cursor;',
    '    private void extendSelection(int direction) {\n        semanticCursorOverride = null;\n'
    '        if (selectionAnchor < 0) selectionAnchor = cursor;',
    'extendSelection semantic reset')

replace_once(
    '        tokens.subList(start, end).clear();\n        cursor = start;\n        clearSelection();',
    '        tokens.subList(start, end).clear();\n        cursor = start;\n        semanticCursorOverride = null;\n        clearSelection();',
    'selection delete semantic reset')

replace_once(
    '        tokens.addAll(undoTokens);\n        cursor = Math.min(undoCursor, tokens.size());',
    '        tokens.addAll(undoTokens);\n        cursor = Math.min(undoCursor, tokens.size());\n        semanticCursorOverride = null;',
    'undo semantic reset')

# Recall and clear are editor state resets too.
replace_once(
    '        tokens.clear();\n        tokens.addAll(entry.tokens);\n        cursor = tokens.size();',
    '        tokens.clear();\n        tokens.addAll(entry.tokens);\n        cursor = tokens.size();\n        semanticCursorOverride = null;',
    'history semantic reset')

replace_once(
    '        tokens.clear();\n        cursor = 0;\n        clearSelection();',
    '        tokens.clear();\n        cursor = 0;\n        semanticCursorOverride = null;\n        clearSelection();',
    'clearExpression semantic reset')

# ---------------------------------------------------------------------------
# 4. Fraction-aware selection helpers: the separator is never exposed as an
#    independent atom, including temporarily empty numerator/denominator slots.
# ---------------------------------------------------------------------------
replace_once(
    '''                if (isFractionTemplate(token)) {\n                    unitStart = semanticAtomStart(index);\n                    unitEnd = semanticAtomEnd(index + 1);\n                } else if ("^".equals(token.evaluation)) {''',
    '''                if (isFractionTemplate(token)) {\n                    FractionBounds fraction = fractionBounds(index);\n                    if (fraction != null) {\n                        unitStart = fraction.numeratorStart;\n                        unitEnd = fraction.denominatorEnd;\n                    }\n                } else if ("^".equals(token.evaluation)) {''',
    'touch selection fraction bounds')

replace_once(
    '''        if (atomEnd < tokens.size() && isFractionTemplate(tokens.get(atomEnd))) {\n            return semanticAtomEnd(atomEnd + 1);\n        }''',
    '''        if (atomEnd < tokens.size() && isFractionTemplate(tokens.get(atomEnd))) {\n            FractionBounds fraction = fractionBounds(atomEnd);\n            return fraction == null ? semanticAtomEnd(atomEnd + 1) : fraction.denominatorEnd;\n        }''',
    'selection target fraction bounds')

replace_once(
    '''        if (isFractionTemplate(previous)) {\n            return semanticAtomStart(safe - 1);\n        }''',
    '''        if (isFractionTemplate(previous)) {\n            return fractionNumeratorStart(safe - 1);\n        }''',
    'semantic atom start fraction')

replace_once(
    '''        if (isFractionTemplate(current)) {\n            return semanticAtomEnd(safe + 1);\n        }''',
    '''        if (isFractionTemplate(current)) {\n            FractionBounds fraction = fractionBounds(safe);\n            return fraction == null ? safe + 1 : fraction.denominatorEnd;\n        }''',
    'semantic atom end fraction')

# ---------------------------------------------------------------------------
# 5. Replace the first-stage fraction helpers with the completed Step 2 core.
# ---------------------------------------------------------------------------
helper_start = text.index('    /**\n     * Returns the best semantic position for the current legacy token boundary.')
helper_end = text.index('    private List<String> spreadsheetCellsSnapshot() {', helper_start)
helpers = r'''    /**
     * Returns the best semantic position for the current legacy token boundary.
     * A Stage 3 override is required at fraction entry/exit boundaries because
     * the same legacy boundary can mean either a nested slot or the root row.
     */
    private CnCwCursorPath semanticCursorPath() {
        if (semanticCursorOverride != null
                && semanticCursorOverride.legacyTokenBoundary() == cursor) {
            return semanticCursorOverride;
        }
        FractionCursor fraction = fractionCursorAt(cursor);
        if (fraction == null) return CnCwCursorPath.rootBoundary(cursor);
        return CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(fraction.templateIndex),
                fraction.slot, fraction.offset, cursor);
    }

    private FractionBounds fractionBounds(int templateIndex) {
        if (templateIndex < 0 || templateIndex >= tokens.size()
                || !isFractionTemplate(tokens.get(templateIndex))) return null;
        int numeratorStart = fractionNumeratorStart(templateIndex);
        int numeratorEnd = templateIndex;
        int denominatorStart = templateIndex + 1;
        int denominatorEnd = fractionDenominatorEnd(denominatorStart, tokens.size());
        return new FractionBounds(templateIndex, numeratorStart, numeratorEnd,
                denominatorStart, denominatorEnd);
    }

    /** Empty numerators remain a valid editor slot instead of absorbing a binary token. */
    private int fractionNumeratorStart(int templateIndex) {
        if (templateIndex <= 0) return Math.max(0, templateIndex);
        Token previous = tokens.get(templateIndex - 1);
        if (previous.binary) return templateIndex;
        return semanticAtomStart(templateIndex);
    }

    /** Empty denominators stop before the following top-level binary operator. */
    private int fractionDenominatorEnd(int start, int limit) {
        int safe = Math.max(0, Math.min(limit, start));
        if (safe >= limit) return safe;
        if (tokens.get(safe).binary) return safe;
        return naturalExponentEnd(safe, limit, false);
    }

    /** Finds the smallest fraction structure owning the requested insertion boundary. */
    private FractionCursor fractionCursorAt(int boundary) {
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        FractionCursor best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds bounds = fractionBounds(template);
            if (bounds == null) continue;
            CnCwCursorPath.Slot slot = null;
            int offset = 0;
            if (safe >= bounds.numeratorStart && safe <= bounds.numeratorEnd) {
                slot = CnCwCursorPath.Slot.FRACTION_NUMERATOR;
                offset = safe - bounds.numeratorStart;
            } else if (safe >= bounds.denominatorStart && safe <= bounds.denominatorEnd) {
                slot = CnCwCursorPath.Slot.FRACTION_DENOMINATOR;
                offset = safe - bounds.denominatorStart;
            }
            if (slot == null) continue;
            int span = bounds.denominatorEnd - bounds.numeratorStart;
            if (span < bestSpan) {
                bestSpan = span;
                best = new FractionCursor(template, bounds.numeratorStart, bounds.numeratorEnd,
                        bounds.denominatorStart, bounds.denominatorEnd, slot, offset);
            }
        }
        return best;
    }

    private FractionCursor fractionCursorFromPath(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary() || path.childPath().isEmpty()) return null;
        CnCwCursorPath.Slot slot = path.slot();
        if (slot != CnCwCursorPath.Slot.FRACTION_NUMERATOR
                && slot != CnCwCursorPath.Slot.FRACTION_DENOMINATOR) return null;
        int template = path.childPath().get(0);
        FractionBounds bounds = fractionBounds(template);
        if (bounds == null) return null;
        int length = slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                ? bounds.numeratorEnd - bounds.numeratorStart
                : bounds.denominatorEnd - bounds.denominatorStart;
        int offset = Math.max(0, Math.min(length, path.offset()));
        return new FractionCursor(template, bounds.numeratorStart, bounds.numeratorEnd,
                bounds.denominatorStart, bounds.denominatorEnd, slot, offset);
    }

    private FractionBounds fractionStartingAtBoundary(int boundary) {
        FractionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds value = fractionBounds(template);
            if (value == null || value.numeratorStart != boundary) continue;
            int span = value.denominatorEnd - value.numeratorStart;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private FractionBounds fractionEndingAtBoundary(int boundary) {
        FractionBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            FractionBounds value = fractionBounds(template);
            if (value == null || value.denominatorEnd != boundary) continue;
            int span = value.denominatorEnd - value.numeratorStart;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private void setFractionCursor(FractionBounds fraction, CnCwCursorPath.Slot slot, int offset) {
        int length = slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                ? fraction.numeratorEnd - fraction.numeratorStart
                : fraction.denominatorEnd - fraction.denominatorStart;
        int local = Math.max(0, Math.min(length, offset));
        cursor = (slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR
                ? fraction.numeratorStart : fraction.denominatorStart) + local;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(fraction.templateIndex), slot, local, cursor);
    }

    private void setRootCursor(int boundary) {
        cursor = Math.max(0, Math.min(tokens.size(), boundary));
        semanticCursorOverride = CnCwCursorPath.rootBoundary(cursor);
    }

    private void finishSemanticCursorMove() {
        clearSelection();
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        status = applicationStatus();
    }

    /**
     * Horizontal navigation has explicit same-boundary entry/exit states:
     * root-before → numerator → denominator → root-after.
     */
    private boolean moveFractionHorizontal(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            FractionBounds target = direction > 0
                    ? fractionStartingAtBoundary(cursor) : fractionEndingAtBoundary(cursor);
            if (target == null) return false;
            if (direction > 0) {
                setFractionCursor(target, CnCwCursorPath.Slot.FRACTION_NUMERATOR, 0);
            } else {
                setFractionCursor(target, CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                        target.denominatorEnd - target.denominatorStart);
            }
            finishSemanticCursorMove();
            return true;
        }

        FractionCursor fraction = fractionCursorFromPath(path);
        if (fraction == null) return false;
        FractionBounds bounds = fractionBounds(fraction.templateIndex);
        if (bounds == null) return false;

        if (fraction.slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR) {
            int length = bounds.numeratorEnd - bounds.numeratorStart;
            if (direction < 0) {
                if (fraction.offset == 0) setRootCursor(bounds.numeratorStart);
                else setFractionCursor(bounds, fraction.slot, fraction.offset - 1);
            } else {
                if (fraction.offset < length) {
                    setFractionCursor(bounds, fraction.slot, fraction.offset + 1);
                } else {
                    setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_DENOMINATOR, 0);
                }
            }
        } else {
            int length = bounds.denominatorEnd - bounds.denominatorStart;
            if (direction < 0) {
                if (fraction.offset > 0) {
                    setFractionCursor(bounds, fraction.slot, fraction.offset - 1);
                } else {
                    setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                            bounds.numeratorEnd - bounds.numeratorStart);
                }
            } else {
                if (fraction.offset < length) {
                    setFractionCursor(bounds, fraction.slot, fraction.offset + 1);
                } else {
                    setRootCursor(bounds.denominatorEnd);
                }
            }
        }
        finishSemanticCursorMove();
        return true;
    }

    /** Moves between numerator and denominator without invoking history recall. */
    private boolean moveFractionVertical(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        FractionCursor fraction = fractionCursorFromPath(path);
        if (fraction == null) return false;
        FractionBounds bounds = fractionBounds(fraction.templateIndex);
        if (bounds == null) return false;

        if (direction < 0 && fraction.slot == CnCwCursorPath.Slot.FRACTION_DENOMINATOR) {
            setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                    Math.min(fraction.offset, bounds.numeratorEnd - bounds.numeratorStart));
        } else if (direction > 0
                && fraction.slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR) {
            setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                    Math.min(fraction.offset, bounds.denominatorEnd - bounds.denominatorStart));
        }
        finishSemanticCursorMove();
        return true;
    }

    /**
     * DEL inside a fraction removes only slot content. At a slot boundary it
     * navigates instead of deleting the structural separator. From root-after,
     * DEL removes the complete fraction atomically.
     */
    private boolean deleteFractionSemantic() {
        if (tokens.isEmpty()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            if (semanticCursorOverride == null) return false;
            FractionBounds fraction = fractionEndingAtBoundary(cursor);
            if (fraction == null) return false;
            rememberUndo();
            tokens.subList(fraction.numeratorStart, fraction.denominatorEnd).clear();
            setRootCursor(fraction.numeratorStart);
            finishSemanticEditMutation();
            return true;
        }

        FractionCursor fraction = fractionCursorFromPath(path);
        if (fraction == null) return false;
        FractionBounds bounds = fractionBounds(fraction.templateIndex);
        if (bounds == null) return false;

        if (fraction.slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR) {
            if (fraction.offset == 0) {
                setRootCursor(bounds.numeratorStart);
                finishSemanticCursorMove();
                return true;
            }
            int deleteIndex = cursor - 1;
            if (deleteIndex < bounds.numeratorStart || deleteIndex >= bounds.numeratorEnd) return false;
            rememberUndo();
            tokens.remove(deleteIndex);
            cursor--;
            int newTemplate = Math.max(0, fraction.templateIndex - 1);
            semanticCursorOverride = CnCwCursorPath.nested(
                    com.codex.fx991.core.Compat.list(newTemplate),
                    CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                    Math.max(0, fraction.offset - 1), cursor);
            finishSemanticEditMutation();
            return true;
        }

        if (fraction.offset == 0) {
            setFractionCursor(bounds, CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                    bounds.numeratorEnd - bounds.numeratorStart);
            finishSemanticCursorMove();
            return true;
        }
        int deleteIndex = cursor - 1;
        if (deleteIndex < bounds.denominatorStart || deleteIndex >= bounds.denominatorEnd) return false;
        rememberUndo();
        tokens.remove(deleteIndex);
        cursor--;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(fraction.templateIndex),
                CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                Math.max(0, fraction.offset - 1), cursor);
        finishSemanticEditMutation();
        return true;
    }

    private void finishSemanticEditMutation() {
        clearSelection();
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        status = applicationStatus();
    }

'''
text = text[:helper_start] + helpers + text[helper_end:]

# ---------------------------------------------------------------------------
# 6. Natural renderer: when the semantic cursor is explicitly on the root row
#    at the same legacy boundary as a fraction edge, render it outside the
#    stacked fraction. Empty slots continue to render as a fraction.
# ---------------------------------------------------------------------------
render_start = text.index('    private CnCwExpressionNode naturalExpression() {')
render_end = text.index('    private static boolean isFractionTemplate(Token token) {', render_start)
renderer = r'''    private CnCwExpressionNode naturalExpression() {
        CnCwCursorPath semantic = semanticCursorPath();
        if (semanticCursorOverride != null && semantic.isRootBoundary()) {
            CnCwExpressionNode before = naturalRow(0, cursor, -1);
            CnCwExpressionNode after = naturalRow(cursor, tokens.size(), -1);
            List<CnCwExpressionNode> children = new ArrayList<>(
                    before.children().size() + after.children().size() + 1);
            children.addAll(before.children());
            children.add(CnCwExpressionNode.cursor());
            children.addAll(after.children());
            return CnCwExpressionNode.row(children);
        }
        return naturalRow(0, tokens.size(), cursor);
    }

    /** Compatibility wrapper for non-Stage-3 callers inside this class. */
    private CnCwExpressionNode naturalRow(int start, int end) {
        return naturalRow(start, end, cursor);
    }

    /** Builds a visual tree without changing the semantic expression tokens. */
    private CnCwExpressionNode naturalRow(int start, int end, int renderCursor) {
        List<CnCwExpressionNode> children = new ArrayList<>(Math.max(1, end - start + 1));
        int cursorHandledAt = -1;
        int index = start;
        while (index < end) {
            if (index == renderCursor && index != cursorHandledAt) {
                children.add(CnCwExpressionNode.cursor());
            }
            Token token = tokens.get(index);
            if ("^".equals(token.evaluation) && index != renderCursor && !children.isEmpty()) {
                int exponentEnd = naturalExponentEnd(index + 1, end, false);
                CnCwExpressionNode base = children.remove(children.size() - 1);
                CnCwExpressionNode exponent = naturalRow(index + 1, exponentEnd, renderCursor);
                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.SUPERSCRIPT,
                        com.codex.fx991.core.Compat.list(base, exponent), false));
                if (exponent.containsCursor() && renderCursor == exponentEnd) {
                    cursorHandledAt = exponentEnd;
                }
                index = exponentEnd;
                continue;
            }
            if (isFractionTemplate(token)) {
                FractionBounds bounds = fractionBounds(index);
                if (bounds != null) {
                    int numeratorStart = Math.max(start, bounds.numeratorStart);
                    int denominatorEnd = Math.min(end, bounds.denominatorEnd);
                    CnCwCursorPath semantic = semanticCursorPath();
                    boolean ownsCursor = !semantic.isRootBoundary()
                            && !semantic.childPath().isEmpty()
                            && semantic.childPath().get(0) == index;
                    int prefixCursor = ownsCursor ? -1 : renderCursor;
                    int numeratorCursor = ownsCursor
                            && semantic.slot() != CnCwCursorPath.Slot.FRACTION_NUMERATOR
                            ? -1 : renderCursor;
                    int denominatorCursor = ownsCursor
                            && semantic.slot() != CnCwCursorPath.Slot.FRACTION_DENOMINATOR
                            ? -1 : renderCursor;
                    CnCwExpressionNode prefix = naturalRow(start, numeratorStart, prefixCursor);
                    CnCwExpressionNode numerator = naturalRow(numeratorStart, index, numeratorCursor);
                    CnCwExpressionNode denominator = naturalRow(index + 1, denominatorEnd,
                            denominatorCursor);
                    children.clear();
                    children.addAll(prefix.children());
                    children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.FRACTION,
                            com.codex.fx991.core.Compat.list(numerator, denominator), false));
                    if (numerator.containsCursor() || denominator.containsCursor()) {
                        cursorHandledAt = renderCursor;
                    }
                    index = denominatorEnd;
                    continue;
                }
            }
            if ("*10^(".equals(token.evaluation) && index != renderCursor) {
                int exponentEnd = naturalExponentEnd(index + 1, end, true);
                CnCwExpressionNode exponent = naturalRow(index + 1, exponentEnd, renderCursor);
                CnCwExpressionNode ten = CnCwExpressionNode.text("\u00d710", false);
                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.SUPERSCRIPT,
                        com.codex.fx991.core.Compat.list(ten, exponent), false));
                if (exponent.containsCursor() && renderCursor == exponentEnd) {
                    cursorHandledAt = exponentEnd;
                }
                index = exponentEnd;
                if (index < end && ")".equals(tokens.get(index).evaluation)) index++;
                continue;
            }
            children.add(CnCwExpressionNode.text(token.display, isTokenSelected(index)));
            index++;
        }
        if (renderCursor == end && renderCursor != cursorHandledAt) {
            children.add(CnCwExpressionNode.cursor());
        }
        return CnCwExpressionNode.row(children);
    }

'''
text = text[:render_start] + renderer + text[render_end:]

# ---------------------------------------------------------------------------
# 7. Private bounds record.
# ---------------------------------------------------------------------------
replace_once(
    '    private record CoordinateCall(boolean polar, String first, String second) { }\n'
    '    private record FractionCursor(int templateIndex, int numeratorStart, int numeratorEnd,',
    '    private record CoordinateCall(boolean polar, String first, String second) { }\n'
    '    private record FractionBounds(int templateIndex, int numeratorStart, int numeratorEnd,\n'
    '                                  int denominatorStart, int denominatorEnd) { }\n'
    '    private record FractionCursor(int templateIndex, int numeratorStart, int numeratorEnd,',
    'fraction bounds record')

machine_path.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Dedicated Stage 3 regression coverage.
# ---------------------------------------------------------------------------
suite_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
suite = suite_path.read_text(encoding='utf-8')

old = '''        fractionPublishesNestedSlotsAndMovesVertically();\n        System.out.println("PASS " + checks + " semantic cursor checks");'''
new = '''        fractionPublishesNestedSlotsAndMovesVertically();\n        fractionHorizontalEntryExitIsSemantic();\n        fractionDeleteNeverBreaksTemplate();\n        fractionSelectionReplacementKeepsStructure();\n        System.out.println("PASS " + checks + " semantic cursor checks");'''
if old not in suite:
    raise SystemExit('suite run anchor not found')
suite = suite.replace(old, new, 1)

anchor = '    private void check(boolean condition, String message) {'
methods = r'''    private void fractionHorizontalEntryExitIsSemantic() {
        CnCwMachine machine = fractionMachine();

        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at denominator end exits to root row");
        equal(3, machine.state().cursor(),
                "fraction exit keeps the same legacy boundary");
        check(machine.state().naturalExpression().containsCursor(),
                "root-after fraction still renders a cursor");

        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "LEFT from root-after re-enters denominator");
        equal(1, machine.state().semanticCursor().offset(),
                "re-entry lands at denominator end");

        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves inside denominator before crossing slots");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "LEFT from denominator start enters numerator end");
        equal(1, machine.state().semanticCursor().offset(),
                "numerator end offset is retained");
        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to numerator start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "LEFT from numerator start exits to root-before");
        equal(0, machine.state().cursor(),
                "root-before fraction shares the numerator-start legacy boundary");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "RIGHT from root-before enters numerator");
        equal(0, machine.state().semanticCursor().offset(),
                "root-before entry starts at numerator offset zero");
    }

    private void fractionDeleteNeverBreaksTemplate() {
        CnCwMachine machine = fractionMachine();
        machine.dispatch(CnCwKey.DEL);
        equal("5/", machine.state().expression(),
                "DEL removes denominator content without deleting fraction template");
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "empty denominator remains a semantic slot");
        equal(0, machine.state().semanticCursor().offset(),
                "empty denominator cursor is at offset zero");
        check(machine.state().naturalExpression().containsCursor(),
                "empty denominator remains renderable");

        machine.dispatch(CnCwKey.DEL);
        equal("5/", machine.state().expression(),
                "DEL at denominator start navigates instead of deleting separator");
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "denominator-start DEL moves to numerator end");

        machine.dispatch(CnCwKey.DEL);
        equal("/", machine.state().expression(),
                "DEL can empty numerator while preserving fraction structure");
        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,
                machine.state().semanticCursor().slot(),
                "empty numerator remains a semantic slot");
        check(machine.state().naturalExpression().containsCursor(),
                "empty numerator fraction still renders cursor");

        machine.dispatch(CnCwKey.DEL);
        equal("/", machine.state().expression(),
                "DEL at numerator start exits instead of deleting template");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "numerator-start DEL exits to root row");

        machine = fractionMachine();
        machine.dispatch(CnCwKey.RIGHT); // explicit root-after
        machine.dispatch(CnCwKey.DEL);
        equal("", machine.state().expression(),
                "DEL from root-after removes the entire fraction atomically");
        equal(CnCwCursorPath.rootBoundary(0), machine.state().semanticCursor(),
                "whole-fraction delete returns to root boundary");
    }

    private void fractionSelectionReplacementKeepsStructure() {
        CnCwMachine machine = fractionMachine();
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("6", machine.selectedExpression(),
                "SHIFT+LEFT selects denominator content first");
        machine.dispatch(CnCwKey.DIGIT_9);
        equal("5/9", machine.state().expression(),
                "replacing denominator preserves fraction separator");
        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,
                machine.state().semanticCursor().slot(),
                "replacement cursor stays in denominator");

        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("9", machine.selectedExpression(),
                "denominator remains independently selectable after replacement");
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("5/9", machine.selectedExpression(),
                "second semantic extension selects the complete fraction");
        machine.dispatch(CnCwKey.DIGIT_7);
        equal("7", machine.state().expression(),
                "replacing whole fraction removes template and inserts one root token");
        equal(CnCwCursorPath.rootBoundary(1), machine.state().semanticCursor(),
                "whole-fraction replacement returns to root cursor semantics");
    }

    private CnCwMachine fractionMachine() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_5);
        machine.dispatch(CnCwKey.FRACTION);
        machine.dispatch(CnCwKey.DIGIT_6);
        return machine;
    }

    private void check(boolean condition, String message) {'''
if anchor not in suite:
    raise SystemExit('suite insertion anchor not found')
suite = suite.replace(anchor, methods, 1)
suite_path.write_text(suite, encoding='utf-8')

# ---------------------------------------------------------------------------
# Relay: Step 2 is complete once this verified patch is committed.
# ---------------------------------------------------------------------------
relay_path = Path('docs/hand-offs/STAGE3_RELAY.md')
relay = relay_path.read_text(encoding='utf-8')
start = relay.index('## Step 2：分数内部移动')
end = relay.index('分数路径稳定后，再依次接入：', start)
replacement = '''## Step 2：分数内部移动（completed）\n\n分数语义编辑已收口：\n\n1. 分子 / 分母分别发布 `FRACTION_NUMERATOR` / `FRACTION_DENOMINATOR`；\n2. `UP` / `DOWN` 在分子与分母之间切换，并尽量保持局部 offset；\n3. 左右移动采用显式语义路径：`root-before → numerator → denominator → root-after`，边界不再靠跳 token 猜测；\n4. 分数边缘允许“同一 legacy boundary、不同 semantic slot”，因此可以区分分数内部和外部；\n5. DEL 在分数内部只删除槽内容；分母起点回到分子、分子起点退出分数，不允许删除结构分隔符；\n6. 从 `root-after` DEL 会原子删除完整分数；\n7. 分子或分母暂时为空时仍保留 FRACTION 自然树和可见光标；\n8. 分母可独立选中并替换，继续扩选可覆盖完整分数，替换后结构保持正确；\n9. 旧 `int cursor`、Stage 2 触摸协议、复制粘贴和固定签名继续保留。\n\n'''
relay = relay[:start] + replacement + relay[end:]
relay = relay.replace('- Step 3：幂指数；', '- Step 3：幂指数（next）；')
relay_path.write_text(relay, encoding='utf-8')

print('Stage 3 Step 2 fraction editor patch applied')

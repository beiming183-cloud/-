from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
text = machine_path.read_text(encoding='utf-8')


def replace_once(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f'{label} anchor not found')
    text = text.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1. Route arrows and DEL through power semantics after the already-stable
#    fraction semantics. Fractions keep precedence for nested structures.
# ---------------------------------------------------------------------------
replace_once(
    '''                    } else if (!moveFractionHorizontal(-1)) {\n                        semanticCursorOverride = null;\n                        cursor = Math.max(0, cursor - 1);\n                    }''',
    '''                    } else if (!moveFractionHorizontal(-1) && !movePowerHorizontal(-1)) {\n                        semanticCursorOverride = null;\n                        cursor = Math.max(0, cursor - 1);\n                    }''',
    'LEFT power routing')

replace_once(
    '''                    } else if (!moveFractionHorizontal(1)) {\n                        semanticCursorOverride = null;\n                        cursor = Math.min(tokens.size(), cursor + 1);\n                    }''',
    '''                    } else if (!moveFractionHorizontal(1) && !movePowerHorizontal(1)) {\n                        semanticCursorOverride = null;\n                        cursor = Math.min(tokens.size(), cursor + 1);\n                    }''',
    'RIGHT power routing')

replace_once(
    '''            case UP -> {\n                if (!moveFractionVertical(-1)) recallHistory(-1);\n            }\n            case DOWN -> {\n                if (!moveFractionVertical(1)) recallHistory(1);\n            }''',
    '''            case UP -> {\n                if (!moveFractionVertical(-1) && !movePowerVertical(-1)) recallHistory(-1);\n            }\n            case DOWN -> {\n                if (!moveFractionVertical(1) && !movePowerVertical(1)) recallHistory(1);\n            }''',
    'vertical power routing')

replace_once(
    '''        if (deleteSelectionIfPresent()) return;\n        if (deleteFractionSemantic()) return;\n        if (cursor <= 0 || tokens.isEmpty()) return;''',
    '''        if (deleteSelectionIfPresent()) return;\n        if (deleteFractionSemantic()) return;\n        if (deletePowerSemantic()) return;\n        if (cursor <= 0 || tokens.isEmpty()) return;''',
    'power delete routing')

# Menu/clipboard edits are still legacy adapters, so discard stale nested overrides.
replace_once(
    '''        rememberUndo();\n        resetStatementSequence();\n        formatConverted = false;\n        engineeringMode = false;\n        originalResult = "";\n\n        if (selectionActive()) {''',
    '''        rememberUndo();\n        semanticCursorOverride = null;\n        resetStatementSequence();\n        formatConverted = false;\n        engineeringMode = false;\n        originalResult = "";\n\n        if (selectionActive()) {''',
    'paste semantic reset')

replace_once(
    '''    private void insertFromMenu(Token token) {\n        if (token == null) return;\n        closeAllPopups();\n        applicationLanding = false;\n        rememberUndo();''',
    '''    private void insertFromMenu(Token token) {\n        if (token == null) return;\n        closeAllPopups();\n        applicationLanding = false;\n        semanticCursorOverride = null;\n        rememberUndo();''',
    'menu insert semantic reset')

# ---------------------------------------------------------------------------
# 2. Selection can target exponent/base content independently. Crossing the
#    structural ^ token expands to the complete power.
# ---------------------------------------------------------------------------
replace_once(
    '''                } else if ("^".equals(token.evaluation)) {\n                    unitStart = semanticAtomStart(index);\n                    unitEnd = semanticAtomEnd(index + 1);\n                } else if (opensParenthesis(token)) {''',
    '''                } else if (isPowerTemplate(token)) {\n                    PowerBounds power = powerBounds(index);\n                    if (power != null) {\n                        unitStart = power.baseStart;\n                        unitEnd = power.exponentEnd;\n                    }\n                } else if (opensParenthesis(token)) {''',
    'touch selection power bounds')

replace_once(
    '''        if (direction < 0) {\n            if (position <= 0) return 0;\n            int atomStart = semanticAtomStart(position);\n            if (atomStart > 0\n                    && "^".equals(tokens.get(atomStart - 1).evaluation)) {\n                return semanticAtomStart(atomStart - 1);\n            }\n            return atomStart;\n        }''',
    '''        if (direction < 0) {\n            if (position <= 0) return 0;\n            return semanticAtomStart(position);\n        }''',
    'left selection power independence')

replace_once(
    '''        if (atomEnd < tokens.size() && "^".equals(tokens.get(atomEnd).evaluation)) {\n            return semanticAtomEnd(atomEnd + 1);\n        }\n        return atomEnd;''',
    '''        return atomEnd;''',
    'right selection power independence')

replace_once(
    '''        if (isFractionTemplate(previous)) {\n            return fractionNumeratorStart(safe - 1);\n        }\n        if (isNumericFragment(previous)) {''',
    '''        if (isFractionTemplate(previous)) {\n            return fractionNumeratorStart(safe - 1);\n        }\n        if (isPowerTemplate(previous)) {\n            return powerBaseStart(safe - 1);\n        }\n        if (isNumericFragment(previous)) {''',
    'semantic atom start power')

replace_once(
    '''        if (isFractionTemplate(current)) {\n            FractionBounds fraction = fractionBounds(safe);\n            return fraction == null ? safe + 1 : fraction.denominatorEnd;\n        }\n        if (isNumericFragment(current)) {''',
    '''        if (isFractionTemplate(current)) {\n            FractionBounds fraction = fractionBounds(safe);\n            return fraction == null ? safe + 1 : fraction.denominatorEnd;\n        }\n        if (isPowerTemplate(current)) {\n            PowerBounds power = powerBounds(safe);\n            return power == null ? safe + 1 : power.exponentEnd;\n        }\n        if (isNumericFragment(current)) {''',
    'semantic atom end power')

# ---------------------------------------------------------------------------
# 3. Publish power base/exponent semantic slots after fraction inference.
# ---------------------------------------------------------------------------
old_semantic = '''    private CnCwCursorPath semanticCursorPath() {\n        if (semanticCursorOverride != null\n                && semanticCursorOverride.legacyTokenBoundary() == cursor) {\n            return semanticCursorOverride;\n        }\n        FractionCursor fraction = fractionCursorAt(cursor);\n        if (fraction == null) return CnCwCursorPath.rootBoundary(cursor);\n        return CnCwCursorPath.nested(\n                com.codex.fx991.core.Compat.list(fraction.templateIndex),\n                fraction.slot, fraction.offset, cursor);\n    }'''
new_semantic = '''    private CnCwCursorPath semanticCursorPath() {\n        if (semanticCursorOverride != null\n                && semanticCursorOverride.legacyTokenBoundary() == cursor) {\n            return semanticCursorOverride;\n        }\n        FractionCursor fraction = fractionCursorAt(cursor);\n        if (fraction != null) {\n            return CnCwCursorPath.nested(\n                    com.codex.fx991.core.Compat.list(fraction.templateIndex),\n                    fraction.slot, fraction.offset, cursor);\n        }\n        PowerCursor power = powerCursorAt(cursor);\n        if (power != null) {\n            return CnCwCursorPath.nested(\n                    com.codex.fx991.core.Compat.list(power.templateIndex),\n                    power.slot, power.offset, cursor);\n        }\n        return CnCwCursorPath.rootBoundary(cursor);\n    }'''
replace_once(old_semantic, new_semantic, 'semantic cursor power publication')

# ---------------------------------------------------------------------------
# 4. Add complete power semantic helpers before spreadsheet snapshots.
# ---------------------------------------------------------------------------
anchor = '    private List<String> spreadsheetCellsSnapshot() {'
if anchor not in text:
    raise SystemExit('spreadsheet helper anchor not found')

power_helpers = r'''    private static boolean isPowerTemplate(Token token) {
        return "^".equals(token.evaluation) && "^".equals(token.display);
    }

    private PowerBounds powerBounds(int templateIndex) {
        if (templateIndex < 0 || templateIndex >= tokens.size()
                || !isPowerTemplate(tokens.get(templateIndex))) return null;
        int baseStart = powerBaseStart(templateIndex);
        int baseEnd = templateIndex;
        int exponentStart = templateIndex + 1;
        int exponentEnd = powerExponentEnd(exponentStart, tokens.size());
        return new PowerBounds(templateIndex, baseStart, baseEnd, exponentStart, exponentEnd);
    }

    /** Keeps a complete fraction as the base of a power when one ends at ^. */
    private int powerBaseStart(int templateIndex) {
        if (templateIndex <= 0) return Math.max(0, templateIndex);
        Token previous = tokens.get(templateIndex - 1);
        if (previous.binary) return templateIndex;
        FractionBounds fraction = fractionEndingAtBoundary(templateIndex);
        if (fraction != null) return fraction.numeratorStart;

        // Chained powers use the complete previous power as the next base.
        PowerBounds previousPower = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < templateIndex; candidate++) {
            if (!isPowerTemplate(tokens.get(candidate))) continue;
            int candidateEnd = powerExponentEnd(candidate + 1, templateIndex);
            if (candidateEnd != templateIndex) continue;
            int candidateStart = candidate <= 0 ? candidate : semanticAtomStart(candidate);
            int span = templateIndex - candidateStart;
            if (span < bestSpan) {
                bestSpan = span;
                previousPower = new PowerBounds(candidate, candidateStart, candidate,
                        candidate + 1, templateIndex);
            }
        }
        if (previousPower != null) return previousPower.baseStart;
        return semanticAtomStart(templateIndex);
    }

    /** Empty exponents stop before the following top-level binary operator. */
    private int powerExponentEnd(int start, int limit) {
        int safe = Math.max(0, Math.min(limit, start));
        if (safe >= limit) return safe;
        if (tokens.get(safe).binary) return safe;
        return naturalExponentEnd(safe, limit, false);
    }

    private PowerCursor powerCursorAt(int boundary) {
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        PowerCursor best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            PowerBounds bounds = powerBounds(template);
            if (bounds == null) continue;
            CnCwCursorPath.Slot slot = null;
            int offset = 0;
            if (safe >= bounds.baseStart && safe <= bounds.baseEnd) {
                slot = CnCwCursorPath.Slot.SUPERSCRIPT_BASE;
                offset = safe - bounds.baseStart;
            } else if (safe >= bounds.exponentStart && safe <= bounds.exponentEnd) {
                slot = CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT;
                offset = safe - bounds.exponentStart;
            }
            if (slot == null) continue;
            int span = bounds.exponentEnd - bounds.baseStart;
            if (span < bestSpan) {
                bestSpan = span;
                best = new PowerCursor(template, bounds.baseStart, bounds.baseEnd,
                        bounds.exponentStart, bounds.exponentEnd, slot, offset);
            }
        }
        return best;
    }

    private PowerCursor powerCursorFromPath(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary() || path.childPath().isEmpty()) return null;
        CnCwCursorPath.Slot slot = path.slot();
        if (slot != CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                && slot != CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT) return null;
        int template = path.childPath().get(0);
        PowerBounds bounds = powerBounds(template);
        if (bounds == null) return null;
        int length = slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                ? bounds.baseEnd - bounds.baseStart
                : bounds.exponentEnd - bounds.exponentStart;
        int offset = Math.max(0, Math.min(length, path.offset()));
        return new PowerCursor(template, bounds.baseStart, bounds.baseEnd,
                bounds.exponentStart, bounds.exponentEnd, slot, offset);
    }

    private PowerBounds powerStartingAtBoundary(int boundary) {
        PowerBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            PowerBounds value = powerBounds(template);
            if (value == null || value.baseStart != boundary) continue;
            int span = value.exponentEnd - value.baseStart;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private PowerBounds powerEndingAtBoundary(int boundary) {
        PowerBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            PowerBounds value = powerBounds(template);
            if (value == null || value.exponentEnd != boundary) continue;
            int span = value.exponentEnd - value.baseStart;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private void setPowerCursor(PowerBounds power, CnCwCursorPath.Slot slot, int offset) {
        int length = slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                ? power.baseEnd - power.baseStart
                : power.exponentEnd - power.exponentStart;
        int local = Math.max(0, Math.min(length, offset));
        cursor = (slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE
                ? power.baseStart : power.exponentStart) + local;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(power.templateIndex), slot, local, cursor);
    }

    /** root-before → base → exponent → root-after. */
    private boolean movePowerHorizontal(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            PowerBounds target = direction > 0
                    ? powerStartingAtBoundary(cursor) : powerEndingAtBoundary(cursor);
            if (target == null) return false;
            if (direction > 0) {
                setPowerCursor(target, CnCwCursorPath.Slot.SUPERSCRIPT_BASE, 0);
            } else {
                setPowerCursor(target, CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                        target.exponentEnd - target.exponentStart);
            }
            finishSemanticCursorMove();
            return true;
        }

        PowerCursor power = powerCursorFromPath(path);
        if (power == null) return false;
        PowerBounds bounds = powerBounds(power.templateIndex);
        if (bounds == null) return false;

        if (power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE) {
            int length = bounds.baseEnd - bounds.baseStart;
            if (direction < 0) {
                if (power.offset == 0) setRootCursor(bounds.baseStart);
                else setPowerCursor(bounds, power.slot, power.offset - 1);
            } else {
                if (power.offset < length) {
                    setPowerCursor(bounds, power.slot, power.offset + 1);
                } else {
                    setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT, 0);
                }
            }
        } else {
            int length = bounds.exponentEnd - bounds.exponentStart;
            if (direction < 0) {
                if (power.offset > 0) {
                    setPowerCursor(bounds, power.slot, power.offset - 1);
                } else {
                    setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                            bounds.baseEnd - bounds.baseStart);
                }
            } else {
                if (power.offset < length) {
                    setPowerCursor(bounds, power.slot, power.offset + 1);
                } else {
                    setRootCursor(bounds.exponentEnd);
                }
            }
        }
        finishSemanticCursorMove();
        return true;
    }

    /** UP enters the visual exponent; DOWN returns to the base. */
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

    /**
     * DEL never removes ^ by itself. Inside a slot it deletes slot content;
     * slot-start DEL navigates to the preceding semantic position. From an
     * explicit root-after position, DEL removes the complete power atomically.
     */
    private boolean deletePowerSemantic() {
        if (tokens.isEmpty()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            if (semanticCursorOverride == null) return false;
            PowerBounds power = powerEndingAtBoundary(cursor);
            if (power == null) return false;
            rememberUndo();
            tokens.subList(power.baseStart, power.exponentEnd).clear();
            setRootCursor(power.baseStart);
            finishSemanticEditMutation();
            return true;
        }

        PowerCursor power = powerCursorFromPath(path);
        if (power == null) return false;
        PowerBounds bounds = powerBounds(power.templateIndex);
        if (bounds == null) return false;

        if (power.slot == CnCwCursorPath.Slot.SUPERSCRIPT_BASE) {
            if (power.offset == 0) {
                setRootCursor(bounds.baseStart);
                finishSemanticCursorMove();
                return true;
            }
            int deleteIndex = cursor - 1;
            if (deleteIndex < bounds.baseStart || deleteIndex >= bounds.baseEnd) return false;
            rememberUndo();
            tokens.remove(deleteIndex);
            cursor--;
            int newTemplate = Math.max(0, power.templateIndex - 1);
            semanticCursorOverride = CnCwCursorPath.nested(
                    com.codex.fx991.core.Compat.list(newTemplate),
                    CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                    Math.max(0, power.offset - 1), cursor);
            finishSemanticEditMutation();
            return true;
        }

        if (power.offset == 0) {
            setPowerCursor(bounds, CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                    bounds.baseEnd - bounds.baseStart);
            finishSemanticCursorMove();
            return true;
        }
        int deleteIndex = cursor - 1;
        if (deleteIndex < bounds.exponentStart || deleteIndex >= bounds.exponentEnd) return false;
        rememberUndo();
        tokens.remove(deleteIndex);
        cursor--;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(power.templateIndex),
                CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                Math.max(0, power.offset - 1), cursor);
        finishSemanticEditMutation();
        return true;
    }

'''
text = text.replace(anchor, power_helpers + anchor, 1)

# ---------------------------------------------------------------------------
# 5. Natural tree keeps empty base/exponent slots inside SUPERSCRIPT instead
#    of falling back to a visible raw ^ token.
# ---------------------------------------------------------------------------
old_power_render = '''            if ("^".equals(token.evaluation) && index != renderCursor && !children.isEmpty()) {\n                int exponentEnd = naturalExponentEnd(index + 1, end, false);\n                CnCwExpressionNode base = children.remove(children.size() - 1);\n                CnCwExpressionNode exponent = naturalRow(index + 1, exponentEnd, renderCursor);\n                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.SUPERSCRIPT,\n                        com.codex.fx991.core.Compat.list(base, exponent), false));\n                if (exponent.containsCursor() && renderCursor == exponentEnd) {\n                    cursorHandledAt = exponentEnd;\n                }\n                index = exponentEnd;\n                continue;\n            }'''
new_power_render = '''            if (isPowerTemplate(token)) {\n                PowerBounds bounds = powerBounds(index);\n                if (bounds != null) {\n                    int baseStart = Math.max(start, bounds.baseStart);\n                    int exponentEnd = Math.min(end, bounds.exponentEnd);\n                    CnCwCursorPath semantic = semanticCursorPath();\n                    boolean ownsCursor = !semantic.isRootBoundary()\n                            && !semantic.childPath().isEmpty()\n                            && semantic.childPath().get(0) == index\n                            && (semantic.slot() == CnCwCursorPath.Slot.SUPERSCRIPT_BASE\n                            || semantic.slot() == CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT);\n                    int prefixCursor = ownsCursor ? -1 : renderCursor;\n                    int baseCursor = ownsCursor\n                            && semantic.slot() != CnCwCursorPath.Slot.SUPERSCRIPT_BASE\n                            ? -1 : renderCursor;\n                    int exponentCursor = ownsCursor\n                            && semantic.slot() != CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT\n                            ? -1 : renderCursor;\n                    CnCwExpressionNode prefix = naturalRow(start, baseStart, prefixCursor);\n                    CnCwExpressionNode base = naturalRow(baseStart, index, baseCursor);\n                    CnCwExpressionNode exponent = naturalRow(index + 1, exponentEnd, exponentCursor);\n                    children.clear();\n                    children.addAll(prefix.children());\n                    children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.SUPERSCRIPT,\n                            com.codex.fx991.core.Compat.list(base, exponent), false));\n                    if (base.containsCursor() || exponent.containsCursor()) {\n                        cursorHandledAt = renderCursor;\n                    }\n                    index = exponentEnd;\n                    continue;\n                }\n            }'''
replace_once(old_power_render, new_power_render, 'power natural renderer')

# Power template helper now lives with other structural predicates; avoid duplicate definition.
# (isPowerTemplate is defined in the semantic helper block above.)

# ---------------------------------------------------------------------------
# 6. Add power records.
# ---------------------------------------------------------------------------
replace_once(
    '''    private record FractionCursor(int templateIndex, int numeratorStart, int numeratorEnd,\n                                  int denominatorStart, int denominatorEnd,\n                                  CnCwCursorPath.Slot slot, int offset) { }\n    private record SelectionRange(int start, int end) { }''',
    '''    private record FractionCursor(int templateIndex, int numeratorStart, int numeratorEnd,\n                                  int denominatorStart, int denominatorEnd,\n                                  CnCwCursorPath.Slot slot, int offset) { }\n    private record PowerBounds(int templateIndex, int baseStart, int baseEnd,\n                               int exponentStart, int exponentEnd) { }\n    private record PowerCursor(int templateIndex, int baseStart, int baseEnd,\n                               int exponentStart, int exponentEnd,\n                               CnCwCursorPath.Slot slot, int offset) { }\n    private record SelectionRange(int start, int end) { }''',
    'power records')

machine_path.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# 7. Extend Stage 3 regression suite.
# ---------------------------------------------------------------------------
suite_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
suite = suite_path.read_text(encoding='utf-8')

old_run = '''        fractionDeleteNeverBreaksTemplate();\n        fractionSelectionReplacementKeepsStructure();\n        System.out.println("PASS " + checks + " semantic cursor checks");'''
new_run = '''        fractionDeleteNeverBreaksTemplate();\n        fractionSelectionReplacementKeepsStructure();\n        powerPublishesBaseExponentAndMovesSemantically();\n        powerDeleteNeverBreaksTemplate();\n        powerSelectionReplacementKeepsStructure();\n        System.out.println("PASS " + checks + " semantic cursor checks");'''
if old_run not in suite:
    raise SystemExit('suite run anchor not found')
suite = suite.replace(old_run, new_run, 1)

anchor = '''    private CnCwMachine fractionMachine() {'''
methods = r'''    private void powerPublishesBaseExponentAndMovesSemantically() {
        CnCwMachine machine = powerMachine();
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "power input ends inside exponent slot");
        equal(1, machine.state().semanticCursor().childPath().get(0),
                "power semantic path identifies ^ template token");
        equal(1, machine.state().semanticCursor().offset(),
                "exponent cursor keeps local offset");
        check(machine.state().naturalExpression().containsCursor(),
                "power exponent cursor remains visible in natural tree");

        machine.dispatch(CnCwKey.DOWN);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "DOWN moves exponent cursor into base");
        equal(1, machine.state().semanticCursor().offset(),
                "DOWN preserves nearest base offset");
        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "UP returns base cursor to exponent");

        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at exponent end exits power to root row");
        equal(3, machine.state().cursor(),
                "power root-after shares exponent-end legacy boundary");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "LEFT from root-after re-enters exponent end");
        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to exponent start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "LEFT from exponent start enters base end");
        equal(1, machine.state().semanticCursor().offset(),
                "base end keeps local offset");
        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to base start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "LEFT from base start exits to root-before");
        equal(0, machine.state().cursor(),
                "power root-before shares base-start legacy boundary");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "RIGHT from root-before enters power base");
    }

    private void powerDeleteNeverBreaksTemplate() {
        CnCwMachine machine = powerMachine();
        machine.dispatch(CnCwKey.DEL);
        equal("2^", machine.state().expression(),
                "DEL removes exponent content without deleting ^ template");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "empty exponent remains a semantic slot");
        equal(0, machine.state().semanticCursor().offset(),
                "empty exponent cursor stays at offset zero");
        check(machine.state().naturalExpression().containsCursor(),
                "empty exponent remains renderable as superscript slot");

        machine.dispatch(CnCwKey.DEL);
        equal("2^", machine.state().expression(),
                "DEL at exponent start navigates instead of deleting ^");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "exponent-start DEL returns to base end");

        machine.dispatch(CnCwKey.DEL);
        equal("^", machine.state().expression(),
                "DEL can empty power base while preserving ^ structure");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_BASE,
                machine.state().semanticCursor().slot(),
                "empty power base remains semantic");
        check(machine.state().naturalExpression().containsCursor(),
                "empty power base remains visible in natural tree");

        machine.dispatch(CnCwKey.DEL);
        equal("^", machine.state().expression(),
                "DEL at base start exits instead of deleting structural ^");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "base-start DEL exits to root row");

        machine = powerMachine();
        machine.dispatch(CnCwKey.RIGHT); // explicit root-after
        machine.dispatch(CnCwKey.DEL);
        equal("", machine.state().expression(),
                "DEL from root-after removes complete power atomically");
        equal(CnCwCursorPath.rootBoundary(0), machine.state().semanticCursor(),
                "whole-power delete returns to root boundary");
    }

    private void powerSelectionReplacementKeepsStructure() {
        CnCwMachine machine = powerMachine();
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("3", machine.selectedExpression(),
                "SHIFT+LEFT selects exponent content before whole power");
        machine.dispatch(CnCwKey.DIGIT_9);
        equal("2^9", machine.state().expression(),
                "replacing exponent preserves power template");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "exponent replacement cursor remains in exponent");

        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("9", machine.selectedExpression(),
                "replacement exponent stays independently selectable");
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("2^9", machine.selectedExpression(),
                "second semantic extension selects complete power");
        machine.dispatch(CnCwKey.DIGIT_7);
        equal("7", machine.state().expression(),
                "replacing whole power removes ^ template atomically");
        equal(CnCwCursorPath.rootBoundary(1), machine.state().semanticCursor(),
                "whole-power replacement returns to root cursor semantics");

        machine = powerMachine();
        machine.beginTouchSelection(0);
        machine.extendTouchSelection(1);
        equal("2", machine.selectedExpression(),
                "power base can be selected independently without crossing ^");
        equal(1, machine.pasteExpression("4"),
                "base replacement accepts one semantic token");
        equal("4^3", machine.state().expression(),
                "replacing base preserves exponent and ^ template");
    }

    private CnCwMachine powerMachine() {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.DIGIT_2);
        machine.dispatch(CnCwKey.POWER);
        machine.dispatch(CnCwKey.DIGIT_3);
        return machine;
    }

    private CnCwMachine fractionMachine() {'''
if anchor not in suite:
    raise SystemExit('suite helper anchor not found')
suite = suite.replace(anchor, methods, 1)
suite_path.write_text(suite, encoding='utf-8')

# ---------------------------------------------------------------------------
# 8. Relay: Step 3 is complete once this one-shot workflow reaches commit.
# ---------------------------------------------------------------------------
relay_path = Path('docs/hand-offs/STAGE3_RELAY.md')
relay = relay_path.read_text(encoding='utf-8')
relay = relay.replace('## Step 1：语义位置模型（进行中）', '## Step 1：语义位置模型（completed）')
old_tail = '''分数路径稳定后，再依次接入：\n\n- Step 3：幂指数（next）；\n- Step 4：根号 / n 次根；\n- Step 5：函数参数；\n- Step 6：语义选区、替换和删除统一；\n- Step 7：Android 命中测试与渲染全面切换到 semantic cursor。'''
new_tail = '''## Step 3：幂指数语义编辑（completed）\n\n幂结构已按与分数一致的迁移原则接入 semantic cursor：\n\n1. `^` 模板发布 `SUPERSCRIPT_BASE` / `SUPERSCRIPT_EXPONENT`；\n2. 左右路径为 `root-before → base → exponent → root-after`；\n3. `UP` 从 base 进入 exponent，`DOWN` 从 exponent 回到 base，并尽量保持局部 offset；\n4. DEL 在 base / exponent 内只删除槽内容，不允许单独删除结构 `^`；\n5. exponent 起点 DEL 回到 base，base 起点 DEL 退出幂；\n6. 从显式 `root-after` DEL 原子删除完整幂；\n7. base 或 exponent 暂时为空时，SUPERSCRIPT 自然树仍保留并显示光标；\n8. exponent 和 base 都可独立选择/替换，跨过 `^` 后选择完整幂；\n9. 分数语义优先级保持不变，旧 `int cursor` 和 Stage 2 触摸协议继续兼容。\n\n下一步：\n\n- Step 4：根号 / n 次根（next）；\n- Step 5：函数参数；\n- Step 6：语义选区、替换和删除统一；\n- Step 7：Android 命中测试与渲染全面切换到 semantic cursor。'''
if old_tail not in relay:
    raise SystemExit('relay tail anchor not found')
relay = relay.replace(old_tail, new_tail, 1)
relay_path.write_text(relay, encoding='utf-8')

print('Stage 3 Step 3 power semantic patch applied')

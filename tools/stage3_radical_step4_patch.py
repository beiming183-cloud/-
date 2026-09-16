from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
text = machine_path.read_text(encoding='utf-8')


def replace_once(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f'{label} anchor not found')
    text = text.replace(old, new, 1)

# Route cursor movement and DEL through radical semantics after fraction/power.
replace_once(
    '} else if (!moveFractionHorizontal(-1) && !movePowerHorizontal(-1)) {',
    '} else if (!moveFractionHorizontal(-1) && !movePowerHorizontal(-1)\n                            && !moveRadicalHorizontal(-1)) {',
    'LEFT radical routing')
replace_once(
    '} else if (!moveFractionHorizontal(1) && !movePowerHorizontal(1)) {',
    '} else if (!moveFractionHorizontal(1) && !movePowerHorizontal(1)\n                            && !moveRadicalHorizontal(1)) {',
    'RIGHT radical routing')
replace_once(
    'if (!moveFractionVertical(-1) && !movePowerVertical(-1)) recallHistory(-1);',
    'if (!moveFractionVertical(-1) && !movePowerVertical(-1)\n                        && !moveRadicalVertical(-1)) recallHistory(-1);',
    'UP radical routing')
replace_once(
    'if (!moveFractionVertical(1) && !movePowerVertical(1)) recallHistory(1);',
    'if (!moveFractionVertical(1) && !movePowerVertical(1)\n                        && !moveRadicalVertical(1)) recallHistory(1);',
    'DOWN radical routing')
replace_once(
    '        if (deleteFractionSemantic()) return;\n        if (deletePowerSemantic()) return;\n        if (cursor <= 0 || tokens.isEmpty()) return;',
    '        if (deleteFractionSemantic()) return;\n        if (deletePowerSemantic()) return;\n        if (deleteRadicalSemantic()) return;\n        if (cursor <= 0 || tokens.isEmpty()) return;',
    'radical delete routing')

# Let touch fine-selection remain inside a radical slot instead of expanding the
# whole function call. Crossing a structural token still expands to the full call.
replace_once(
    '''                } else {\n                    int enclosing = enclosingOpen(index);\n                    if (enclosing >= 0) {\n                        int close = matchingClose(enclosing);\n                        unitStart = enclosing;\n                        unitEnd = close >= 0 ? close + 1 : unitEnd;\n                    }\n                }''',
    '''                } else {\n                    RadicalBounds radical = radicalContainingToken(index);\n                    if (radical == null || !selectionWithinRadicalSlot(start, end, radical)) {\n                        int enclosing = enclosingOpen(index);\n                        if (enclosing >= 0) {\n                            int close = matchingClose(enclosing);\n                            unitStart = enclosing;\n                            unitEnd = close >= 0 ? close + 1 : unitEnd;\n                        }\n                    }\n                }''',
    'touch radical fine-selection')

# Fixed cube-root token root(3, opens a semantic parenthesis even though its
# evaluator spelling does not end in '('.
replace_once(
    '''    private static boolean opensParenthesis(Token token) {\n        return token.evaluation.endsWith("(") || "(".equals(token.evaluation);\n    }''',
    '''    private static boolean opensParenthesis(Token token) {\n        return token.evaluation.endsWith("(") || "(".equals(token.evaluation)\n                || isFixedRootTemplate(token);\n    }''',
    'fixed root parenthesis')

# Publish radical slots after the already-more-specific fraction and power slots.
replace_once(
    '''        PowerCursor power = powerCursorAt(cursor);\n        if (power != null) {\n            return CnCwCursorPath.nested(\n                    com.codex.fx991.core.Compat.list(power.templateIndex),\n                    power.slot, power.offset, cursor);\n        }\n        return CnCwCursorPath.rootBoundary(cursor);''',
    '''        PowerCursor power = powerCursorAt(cursor);\n        if (power != null) {\n            return CnCwCursorPath.nested(\n                    com.codex.fx991.core.Compat.list(power.templateIndex),\n                    power.slot, power.offset, cursor);\n        }\n        RadicalCursor radical = radicalCursorAt(cursor);\n        if (radical != null) {\n            return CnCwCursorPath.nested(\n                    com.codex.fx991.core.Compat.list(radical.templateIndex),\n                    radical.slot, radical.offset, cursor);\n        }\n        return CnCwCursorPath.rootBoundary(cursor);''',
    'radical semantic publication')

# Add radical/root helpers before spreadsheet snapshot code.
anchor = '    private List<String> spreadsheetCellsSnapshot() {'
if anchor not in text:
    raise SystemExit('radical helper anchor not found')

helpers = r'''    private static boolean isSquareRootTemplate(Token token) {
        return "sqrt(".equals(token.evaluation);
    }

    private static boolean isGenericRootTemplate(Token token) {
        return "root(".equals(token.evaluation);
    }

    private static boolean isFixedRootTemplate(Token token) {
        return token.evaluation.startsWith("root(")
                && token.evaluation.endsWith(",")
                && !isGenericRootTemplate(token);
    }

    private static boolean isRadicalTemplate(Token token) {
        return isSquareRootTemplate(token) || isGenericRootTemplate(token)
                || isFixedRootTemplate(token);
    }

    /** Closing parenthesis owned by sqrt/root, or -1 for the live unclosed slot. */
    private int radicalCloseIndex(int templateIndex) {
        int depth = 0;
        for (int index = templateIndex + 1; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (")".equals(token.evaluation)) {
                if (depth == 0) return index;
                depth--;
            } else if (opensParenthesis(token)) {
                depth++;
            }
        }
        return -1;
    }

    /** First top-level comma separating root(index, content). */
    private int rootSeparatorIndex(int templateIndex, int innerEnd) {
        int depth = 0;
        for (int index = templateIndex + 1; index < innerEnd; index++) {
            Token token = tokens.get(index);
            if (")".equals(token.evaluation)) {
                if (depth > 0) depth--;
                continue;
            }
            if (depth == 0 && ",".equals(token.evaluation)) return index;
            if (opensParenthesis(token)) depth++;
        }
        return -1;
    }

    private RadicalBounds radicalBounds(int templateIndex) {
        if (templateIndex < 0 || templateIndex >= tokens.size()) return null;
        Token template = tokens.get(templateIndex);
        if (!isRadicalTemplate(template)) return null;
        int close = radicalCloseIndex(templateIndex);
        int innerEnd = close >= 0 ? close : tokens.size();
        int endExclusive = close >= 0 ? close + 1 : innerEnd;
        if (isGenericRootTemplate(template)) {
            int separator = rootSeparatorIndex(templateIndex, innerEnd);
            if (separator >= 0) {
                return new RadicalBounds(templateIndex, templateIndex + 1, separator,
                        separator, separator + 1, innerEnd, close, endExclusive);
            }
            return new RadicalBounds(templateIndex, templateIndex + 1, innerEnd,
                    -1, innerEnd, innerEnd, close, endExclusive);
        }
        return new RadicalBounds(templateIndex, -1, -1, -1,
                templateIndex + 1, innerEnd, close, endExclusive);
    }

    /** Finds the smallest root/radical slot owning an insertion boundary. */
    private RadicalCursor radicalCursorAt(int boundary) {
        int safe = Math.max(0, Math.min(tokens.size(), boundary));
        RadicalCursor best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            RadicalBounds bounds = radicalBounds(template);
            if (bounds == null) continue;
            Token token = tokens.get(template);
            CnCwCursorPath.Slot slot = null;
            int offset = 0;
            if (isGenericRootTemplate(token)) {
                if (safe >= bounds.indexStart && safe <= bounds.indexEnd) {
                    slot = CnCwCursorPath.Slot.ROOT_INDEX;
                    offset = safe - bounds.indexStart;
                } else if (bounds.separatorIndex >= 0
                        && safe >= bounds.contentStart && safe <= bounds.contentEnd) {
                    slot = CnCwCursorPath.Slot.ROOT_CONTENT;
                    offset = safe - bounds.contentStart;
                }
            } else if (safe >= bounds.contentStart && safe <= bounds.contentEnd) {
                slot = isSquareRootTemplate(token)
                        ? CnCwCursorPath.Slot.RADICAL_CONTENT
                        : CnCwCursorPath.Slot.ROOT_CONTENT;
                offset = safe - bounds.contentStart;
            }
            if (slot == null) continue;
            int span = bounds.endExclusive - bounds.templateIndex;
            if (span < bestSpan) {
                bestSpan = span;
                best = new RadicalCursor(template, slot, offset);
            }
        }
        return best;
    }

    private RadicalCursor radicalCursorFromPath(CnCwCursorPath path) {
        if (path == null || path.isRootBoundary() || path.childPath().isEmpty()) return null;
        CnCwCursorPath.Slot slot = path.slot();
        if (slot != CnCwCursorPath.Slot.RADICAL_CONTENT
                && slot != CnCwCursorPath.Slot.ROOT_INDEX
                && slot != CnCwCursorPath.Slot.ROOT_CONTENT) return null;
        int template = path.childPath().get(0);
        RadicalBounds bounds = radicalBounds(template);
        if (bounds == null) return null;
        Token token = tokens.get(template);
        int length;
        if (slot == CnCwCursorPath.Slot.ROOT_INDEX) {
            if (!isGenericRootTemplate(token)) return null;
            length = bounds.indexEnd - bounds.indexStart;
        } else {
            if (slot == CnCwCursorPath.Slot.RADICAL_CONTENT
                    && !isSquareRootTemplate(token)) return null;
            if (slot == CnCwCursorPath.Slot.ROOT_CONTENT
                    && isSquareRootTemplate(token)) return null;
            if (isGenericRootTemplate(token) && bounds.separatorIndex < 0) return null;
            length = bounds.contentEnd - bounds.contentStart;
        }
        return new RadicalCursor(template, slot,
                Math.max(0, Math.min(length, path.offset())));
    }

    private RadicalBounds radicalStartingAtBoundary(int boundary) {
        RadicalBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            RadicalBounds value = radicalBounds(template);
            if (value == null || value.templateIndex != boundary) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private RadicalBounds radicalEndingAtBoundary(int boundary) {
        RadicalBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            RadicalBounds value = radicalBounds(template);
            if (value == null || value.endExclusive != boundary) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private RadicalBounds radicalContainingToken(int tokenIndex) {
        RadicalBounds best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (int template = 0; template < tokens.size(); template++) {
            RadicalBounds value = radicalBounds(template);
            if (value == null) continue;
            boolean inIndex = value.indexStart >= 0
                    && tokenIndex >= value.indexStart && tokenIndex < value.indexEnd;
            boolean inContent = tokenIndex >= value.contentStart && tokenIndex < value.contentEnd;
            if (!inIndex && !inContent) continue;
            int span = value.endExclusive - value.templateIndex;
            if (span < bestSpan) { best = value; bestSpan = span; }
        }
        return best;
    }

    private boolean selectionWithinRadicalSlot(int start, int end, RadicalBounds bounds) {
        boolean withinIndex = bounds.indexStart >= 0
                && start >= bounds.indexStart && end <= bounds.indexEnd;
        boolean withinContent = start >= bounds.contentStart && end <= bounds.contentEnd;
        return withinIndex || withinContent;
    }

    private void setRadicalCursor(RadicalBounds radical, CnCwCursorPath.Slot slot, int offset) {
        int start;
        int length;
        if (slot == CnCwCursorPath.Slot.ROOT_INDEX) {
            start = radical.indexStart;
            length = radical.indexEnd - radical.indexStart;
        } else {
            start = radical.contentStart;
            length = radical.contentEnd - radical.contentStart;
        }
        int local = Math.max(0, Math.min(length, offset));
        cursor = start + local;
        semanticCursorOverride = CnCwCursorPath.nested(
                com.codex.fx991.core.Compat.list(radical.templateIndex), slot, local, cursor);
    }

    /** root-before -> content, or root-before -> index -> content for n-th root. */
    private boolean moveRadicalHorizontal(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            RadicalBounds target = direction > 0
                    ? radicalStartingAtBoundary(cursor) : radicalEndingAtBoundary(cursor);
            if (target == null) return false;
            Token template = tokens.get(target.templateIndex);
            if (direction > 0) {
                if (isGenericRootTemplate(template)) {
                    setRadicalCursor(target, CnCwCursorPath.Slot.ROOT_INDEX, 0);
                } else {
                    setRadicalCursor(target,
                            isSquareRootTemplate(template)
                                    ? CnCwCursorPath.Slot.RADICAL_CONTENT
                                    : CnCwCursorPath.Slot.ROOT_CONTENT,
                            0);
                }
            } else if (isGenericRootTemplate(template) && target.separatorIndex < 0) {
                setRadicalCursor(target, CnCwCursorPath.Slot.ROOT_INDEX,
                        target.indexEnd - target.indexStart);
            } else {
                setRadicalCursor(target,
                        isSquareRootTemplate(template)
                                ? CnCwCursorPath.Slot.RADICAL_CONTENT
                                : CnCwCursorPath.Slot.ROOT_CONTENT,
                        target.contentEnd - target.contentStart);
            }
            finishSemanticCursorMove();
            return true;
        }

        RadicalCursor radical = radicalCursorFromPath(path);
        if (radical == null) return false;
        RadicalBounds bounds = radicalBounds(radical.templateIndex);
        if (bounds == null) return false;
        Token template = tokens.get(bounds.templateIndex);

        if (radical.slot == CnCwCursorPath.Slot.ROOT_INDEX) {
            int length = bounds.indexEnd - bounds.indexStart;
            if (direction < 0) {
                if (radical.offset == 0) setRootCursor(bounds.templateIndex);
                else setRadicalCursor(bounds, radical.slot, radical.offset - 1);
            } else if (radical.offset < length) {
                setRadicalCursor(bounds, radical.slot, radical.offset + 1);
            } else if (bounds.separatorIndex >= 0) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_CONTENT, 0);
            } else {
                setRootCursor(bounds.endExclusive);
            }
            finishSemanticCursorMove();
            return true;
        }

        int length = bounds.contentEnd - bounds.contentStart;
        if (direction < 0) {
            if (radical.offset > 0) {
                setRadicalCursor(bounds, radical.slot, radical.offset - 1);
            } else if (isGenericRootTemplate(template) && bounds.separatorIndex >= 0) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_INDEX,
                        bounds.indexEnd - bounds.indexStart);
            } else {
                setRootCursor(bounds.templateIndex);
            }
        } else if (radical.offset < length) {
            setRadicalCursor(bounds, radical.slot, radical.offset + 1);
        } else {
            setRootCursor(bounds.endExclusive);
        }
        finishSemanticCursorMove();
        return true;
    }

    /** Root index is visually above content; simple/fixed roots consume arrows in-place. */
    private boolean moveRadicalVertical(int direction) {
        if (resultShown || errorShown || selectionActive()) return false;
        CnCwCursorPath path = semanticCursorPath();
        RadicalCursor radical = radicalCursorFromPath(path);
        if (radical == null) return false;
        RadicalBounds bounds = radicalBounds(radical.templateIndex);
        if (bounds == null) return false;
        Token template = tokens.get(bounds.templateIndex);
        if (isGenericRootTemplate(template) && bounds.separatorIndex >= 0) {
            if (direction < 0 && radical.slot == CnCwCursorPath.Slot.ROOT_CONTENT) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_INDEX,
                        Math.min(radical.offset, bounds.indexEnd - bounds.indexStart));
            } else if (direction > 0 && radical.slot == CnCwCursorPath.Slot.ROOT_INDEX) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_CONTENT,
                        Math.min(radical.offset, bounds.contentEnd - bounds.contentStart));
            }
        }
        finishSemanticCursorMove();
        return true;
    }

    /** Never delete sqrt/root templates, commas, or their closing parenthesis piecemeal. */
    private boolean deleteRadicalSemantic() {
        if (tokens.isEmpty()) return false;
        CnCwCursorPath path = semanticCursorPath();
        if (path.isRootBoundary()) {
            RadicalBounds radical = radicalEndingAtBoundary(cursor);
            if (radical == null) return false;
            rememberUndo();
            tokens.subList(radical.templateIndex, radical.endExclusive).clear();
            setRootCursor(radical.templateIndex);
            finishSemanticEditMutation();
            return true;
        }

        RadicalCursor radical = radicalCursorFromPath(path);
        if (radical == null) return false;
        RadicalBounds bounds = radicalBounds(radical.templateIndex);
        if (bounds == null) return false;

        if (radical.slot == CnCwCursorPath.Slot.ROOT_INDEX) {
            if (radical.offset == 0) {
                setRootCursor(bounds.templateIndex);
                finishSemanticCursorMove();
                return true;
            }
            int deleteIndex = cursor - 1;
            if (deleteIndex < bounds.indexStart || deleteIndex >= bounds.indexEnd) return false;
            rememberUndo();
            tokens.remove(deleteIndex);
            cursor--;
            RadicalBounds updated = radicalBounds(radical.templateIndex);
            if (updated == null) return false;
            setRadicalCursor(updated, CnCwCursorPath.Slot.ROOT_INDEX,
                    Math.max(0, radical.offset - 1));
            finishSemanticEditMutation();
            return true;
        }

        if (radical.offset == 0) {
            Token template = tokens.get(bounds.templateIndex);
            if (isGenericRootTemplate(template) && bounds.separatorIndex >= 0) {
                setRadicalCursor(bounds, CnCwCursorPath.Slot.ROOT_INDEX,
                        bounds.indexEnd - bounds.indexStart);
            } else {
                setRootCursor(bounds.templateIndex);
            }
            finishSemanticCursorMove();
            return true;
        }
        int deleteIndex = cursor - 1;
        if (deleteIndex < bounds.contentStart || deleteIndex >= bounds.contentEnd) return false;
        rememberUndo();
        tokens.remove(deleteIndex);
        cursor--;
        RadicalBounds updated = radicalBounds(radical.templateIndex);
        if (updated == null) return false;
        setRadicalCursor(updated, radical.slot, Math.max(0, radical.offset - 1));
        finishSemanticEditMutation();
        return true;
    }

'''
text = text.replace(anchor, helpers + anchor, 1)

# Add radical records beside fraction/power semantic records.
replace_once(
    '''    private record PowerCursor(int templateIndex, int baseStart, int baseEnd,\n                               int exponentStart, int exponentEnd,\n                               CnCwCursorPath.Slot slot, int offset) { }\n    private record SelectionRange(int start, int end) { }''',
    '''    private record PowerCursor(int templateIndex, int baseStart, int baseEnd,\n                               int exponentStart, int exponentEnd,\n                               CnCwCursorPath.Slot slot, int offset) { }\n    private record RadicalBounds(int templateIndex, int indexStart, int indexEnd,\n                                 int separatorIndex, int contentStart, int contentEnd,\n                                 int closeIndex, int endExclusive) { }\n    private record RadicalCursor(int templateIndex, CnCwCursorPath.Slot slot, int offset) { }\n    private record SelectionRange(int start, int end) { }''',
    'radical record declarations')

machine_path.write_text(text, encoding='utf-8')

# ---------------------------------------------------------------------------
# Regression coverage.
# ---------------------------------------------------------------------------
suite_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
suite = suite_path.read_text(encoding='utf-8')
old_run = '''        powerPublishesBaseExponentAndMovesSemantically();\n        powerDeleteNeverBreaksTemplate();\n        powerSelectionReplacementKeepsStructure();\n        System.out.println("PASS " + checks + " semantic cursor checks");'''
new_run = '''        powerPublishesBaseExponentAndMovesSemantically();\n        powerDeleteNeverBreaksTemplate();\n        powerSelectionReplacementKeepsStructure();\n        radicalPublishesContentAndMovesSemantically();\n        nthRootPublishesIndexContentAndMovesSemantically();\n        radicalDeleteNeverBreaksStructure();\n        radicalSelectionReplacementKeepsStructure();\n        radicalEvaluationStillWorks();\n        System.out.println("PASS " + checks + " semantic cursor checks");'''
if old_run not in suite:
    raise SystemExit('cursor suite run anchor not found')
suite = suite.replace(old_run, new_run, 1)

insert_anchor = '    private CnCwMachine powerMachine() {'
if insert_anchor not in suite:
    raise SystemExit('cursor suite helper anchor not found')

methods = r'''    private void radicalPublishesContentAndMovesSemantically() {
        CnCwMachine machine = squareRootMachine(false);
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "sqrt input ends inside RADICAL_CONTENT");
        equal(0, machine.state().semanticCursor().childPath().get(0),
                "sqrt semantic path identifies template token");
        equal(1, machine.state().semanticCursor().offset(),
                "sqrt content keeps local offset");
        check(machine.state().naturalExpression().containsCursor(),
                "sqrt content cursor stays visible in natural tree");

        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at sqrt content end exits to root row");
        equal(2, machine.state().cursor(),
                "unclosed sqrt root-after shares content-end legacy boundary");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "LEFT from root-after re-enters sqrt content");
        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to sqrt content start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "LEFT from sqrt content start exits to root-before");
        equal(0, machine.state().cursor(),
                "sqrt root-before is the template boundary");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "RIGHT from root-before re-enters sqrt content start");

        machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.SQRT);
        machine.dispatch(CnCwKey.DIGIT_8);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "fixed cube root publishes ROOT_CONTENT");
        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "fixed cube-root UP is consumed without history recall");
    }

    private void nthRootPublishesIndexContentAndMovesSemantically() {
        CnCwMachine machine = nthRootMachine(false);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "n-th root input ends in ROOT_CONTENT");
        equal(1, machine.state().semanticCursor().offset(),
                "root content keeps local offset");

        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticCursor().slot(),
                "UP moves root content into root index");
        equal(1, machine.state().semanticCursor().offset(),
                "UP preserves nearest index offset");
        machine.dispatch(CnCwKey.DOWN);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "DOWN returns root index to content");

        machine.dispatch(CnCwKey.LEFT);
        equal(0, machine.state().semanticCursor().offset(),
                "LEFT moves to root content start");
        machine.dispatch(CnCwKey.LEFT);
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticCursor().slot(),
                "LEFT from content start enters root index end");
        equal(1, machine.state().semanticCursor().offset(),
                "root index end keeps local offset");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "RIGHT from index end crosses structural comma into content");
        equal(0, machine.state().semanticCursor().offset(),
                "root content entry starts at offset zero");
    }

    private void radicalDeleteNeverBreaksStructure() {
        CnCwMachine machine = squareRootMachine(false);
        machine.dispatch(CnCwKey.DEL);
        equal("sqrt(", machine.state().expression(),
                "DEL removes sqrt content without deleting template");
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "empty sqrt content remains semantic");
        check(machine.state().naturalExpression().containsCursor(),
                "empty sqrt content still renders a cursor");
        machine.dispatch(CnCwKey.DEL);
        equal("sqrt(", machine.state().expression(),
                "DEL at empty sqrt start exits instead of deleting template");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "sqrt content-start DEL exits to root row");

        machine = squareRootMachine(false);
        machine.dispatch(CnCwKey.RIGHT);
        machine.dispatch(CnCwKey.DEL);
        equal("", machine.state().expression(),
                "DEL from sqrt root-after removes the whole radical atomically");

        machine = nthRootMachine(false);
        machine.dispatch(CnCwKey.DEL);
        equal("root(3,", machine.state().expression(),
                "DEL empties root content without deleting comma");
        equal(CnCwCursorPath.Slot.ROOT_CONTENT,
                machine.state().semanticCursor().slot(),
                "empty root content remains semantic");
        machine.dispatch(CnCwKey.DEL);
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticCursor().slot(),
                "DEL at content start moves to root index end");
        machine.dispatch(CnCwKey.DEL);
        equal("root(,", machine.state().expression(),
                "DEL can empty root index while preserving separator");
        equal(CnCwCursorPath.Slot.ROOT_INDEX,
                machine.state().semanticCursor().slot(),
                "empty root index remains semantic");
        machine.dispatch(CnCwKey.DEL);
        equal("root(,", machine.state().expression(),
                "DEL at empty root index exits instead of deleting root template");
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "empty root index exits to root row");
    }

    private void radicalSelectionReplacementKeepsStructure() {
        CnCwMachine machine = squareRootMachine(true);
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("sqrt(9)", machine.selectedExpression(),
                "keyboard selection keeps closed sqrt as one structure");
        machine.dispatch(CnCwKey.DIGIT_7);
        equal("7", machine.state().expression(),
                "keyboard replacement replaces the complete sqrt atomically");

        machine = squareRootMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(2);
        equal("9", machine.selectedExpression(),
                "touch fine-selection can select sqrt content only");
        equal(1, machine.pasteExpression("4"),
                "sqrt content accepts semantic replacement");
        equal("sqrt(4)", machine.state().expression(),
                "sqrt content replacement preserves template and close");
        equal(CnCwCursorPath.Slot.RADICAL_CONTENT,
                machine.state().semanticCursor().slot(),
                "sqrt replacement cursor remains in radical content");

        machine = nthRootMachine(true);
        machine.beginTouchSelection(1);
        machine.extendTouchSelection(2);
        equal("3", machine.selectedExpression(),
                "touch fine-selection can select root index only");
        equal(1, machine.pasteExpression("4"),
                "root index accepts semantic replacement");
        equal("root(4,8)", machine.state().expression(),
                "root index replacement preserves comma and radicand");

        machine = nthRootMachine(true);
        machine.beginTouchSelection(3);
        machine.extendTouchSelection(4);
        equal("8", machine.selectedExpression(),
                "touch fine-selection can select root content only");
        equal(1, machine.pasteExpression("9"),
                "root content accepts semantic replacement");
        equal("root(3,9)", machine.state().expression(),
                "root content replacement preserves index and separator");
    }

    private void radicalEvaluationStillWorks() {
        CnCwMachine machine = squareRootMachine(true);
        machine.dispatch(CnCwKey.EXE);
        equal("3", machine.state().result(),
                "sqrt semantic editor still evaluates normally");

        machine = nthRootMachine(true);
        machine.dispatch(CnCwKey.EXE);
        equal("2", machine.state().result(),
                "root(index, content) semantic editor still evaluates normally");
    }

    private CnCwMachine squareRootMachine(boolean closed) {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.SQRT);
        machine.dispatch(CnCwKey.DIGIT_9);
        if (closed) machine.dispatch(CnCwKey.CLOSE_PAREN);
        return machine;
    }

    private CnCwMachine nthRootMachine(boolean closed) {
        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);
        machine.dispatch(CnCwKey.OK);
        machine.dispatch(CnCwKey.ROOT);
        machine.dispatch(CnCwKey.DIGIT_3);
        machine.dispatch(CnCwKey.COMMA);
        machine.dispatch(CnCwKey.DIGIT_8);
        if (closed) machine.dispatch(CnCwKey.CLOSE_PAREN);
        return machine;
    }

'''
suite = suite.replace(insert_anchor, methods + insert_anchor, 1)
suite_path.write_text(suite, encoding='utf-8')

# ---------------------------------------------------------------------------
# Handoff status.
# ---------------------------------------------------------------------------
relay_path = Path('docs/hand-offs/STAGE3_RELAY.md')
relay = relay_path.read_text(encoding='utf-8')
old_tail = '''下一步：\n\n- Step 4：根号 / n 次根（next）；\n- Step 5：函数参数；\n- Step 6：语义选区、替换和删除统一；\n- Step 7：Android 命中测试与渲染全面切换到 semantic cursor。\n'''
new_tail = '''## Step 4：根号 / n 次根语义编辑（completed）\n\n根号结构已进入 semantic cursor：\n\n1. `sqrt(` 发布 `RADICAL_CONTENT`；\n2. 通用 `root(index, content)` 分别发布 `ROOT_INDEX` / `ROOT_CONTENT`；\n3. 固定三次根 `root(3,` 发布 `ROOT_CONTENT`，固定根指数不伪装成可编辑槽；\n4. 左右移动采用 `root-before → content → root-after`，n 次根采用 `root-before → index → content → root-after`；\n5. n 次根 `UP` 从 content 进入 index、`DOWN` 从 index 回到 content；简单根号/固定三次根会消费上下键，避免误触历史回溯；\n6. DEL 只删除 index/content 槽内容，不允许逐个破坏根号模板、参数分隔逗号或闭括号；从 root-after DEL 原子删除完整根结构；\n7. index/content 为空时仍保留语义槽和可见光标；\n8. 键盘结构选区继续把闭合根式视为整体；触摸精细选区可独立替换 radicand 或 n 次根 index；\n9. `sqrt(9)` 与 `root(3,8)` 的求值回归继续通过，未改 evaluator 参数顺序；\n10. 分数、幂语义优先级和 Stage 2 兼容协议保持不变。\n\n下一步：\n\n- Step 5：函数参数（next）；\n- Step 6：语义选区、替换和删除统一；\n- Step 7：Android 命中测试与渲染全面切换到 semantic cursor。\n'''
if old_tail not in relay:
    raise SystemExit('handoff Step 4 anchor not found')
relay = relay.replace(old_tail, new_tail, 1)
relay_path.write_text(relay, encoding='utf-8')

print('Stage 3 Step 4 radical semantic patch applied')

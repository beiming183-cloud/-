from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
text = machine_path.read_text(encoding='utf-8')

# 1) Route vertical navigation through fraction semantics before history recall.
old = '''            case UP -> recallHistory(-1);\n            case DOWN -> recallHistory(1);'''
new = '''            case UP -> {\n                if (!moveFractionVertical(-1)) recallHistory(-1);\n            }\n            case DOWN -> {\n                if (!moveFractionVertical(1)) recallHistory(1);\n            }'''
if old not in text:
    raise SystemExit('UP/DOWN navigation anchor not found')
text = text.replace(old, new, 1)

# 2) Publish the real semantic cursor path instead of deriving a root-only facade in UiState.
old = '''                naturalExpression(), cursor, selectionStartIndex(), selectionEndIndex(),\n                result, ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,'''
new = '''                naturalExpression(), cursor, semanticCursorPath(),\n                selectionStartIndex(), selectionEndIndex(),\n                result, ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,'''
if old not in text:
    raise SystemExit('publish constructor anchor not found')
text = text.replace(old, new, 1)

# 3) Add fraction path classification and vertical movement helpers before spreadsheet snapshots.
anchor = '''    private List<String> spreadsheetCellsSnapshot() {'''
helpers = '''    /**\n     * Returns the best semantic position for the current legacy token boundary.\n     * Stage 3 currently upgrades fraction slots first; all other structures\n     * continue to publish a root boundary until their migration step lands.\n     */\n    private CnCwCursorPath semanticCursorPath() {\n        FractionCursor fraction = fractionCursorAt(cursor);\n        if (fraction == null) return CnCwCursorPath.rootBoundary(cursor);\n        return CnCwCursorPath.nested(\n                com.codex.fx991.core.Compat.list(fraction.templateIndex),\n                fraction.slot, fraction.offset, cursor);\n    }\n\n    /** Finds the smallest fraction structure owning the requested insertion boundary. */\n    private FractionCursor fractionCursorAt(int boundary) {\n        int safe = Math.max(0, Math.min(tokens.size(), boundary));\n        FractionCursor best = null;\n        int bestSpan = Integer.MAX_VALUE;\n        for (int template = 0; template < tokens.size(); template++) {\n            if (!isFractionTemplate(tokens.get(template))) continue;\n            int numeratorStart = semanticAtomStart(template);\n            int numeratorEnd = template;\n            int denominatorStart = template + 1;\n            int denominatorEnd = naturalExponentEnd(denominatorStart, tokens.size(), false);\n            CnCwCursorPath.Slot slot = null;\n            int offset = 0;\n            if (safe >= numeratorStart && safe <= numeratorEnd) {\n                slot = CnCwCursorPath.Slot.FRACTION_NUMERATOR;\n                offset = safe - numeratorStart;\n            } else if (safe >= denominatorStart && safe <= denominatorEnd) {\n                slot = CnCwCursorPath.Slot.FRACTION_DENOMINATOR;\n                offset = safe - denominatorStart;\n            }\n            if (slot == null) continue;\n            int span = denominatorEnd - numeratorStart;\n            if (span < bestSpan) {\n                bestSpan = span;\n                best = new FractionCursor(template, numeratorStart, numeratorEnd,\n                        denominatorStart, denominatorEnd, slot, offset);\n            }\n        }\n        return best;\n    }\n\n    /**\n     * Moves between numerator and denominator without invoking history recall.\n     * Horizontal movement remains on the legacy token boundary for this step,\n     * so existing touch and selection behavior stays compatible.\n     */\n    private boolean moveFractionVertical(int direction) {\n        if (resultShown || errorShown || selectionActive()) return false;\n        FractionCursor fraction = fractionCursorAt(cursor);\n        if (fraction == null) return false;\n\n        int next = cursor;\n        if (direction < 0 && fraction.slot == CnCwCursorPath.Slot.FRACTION_DENOMINATOR) {\n            int length = fraction.numeratorEnd - fraction.numeratorStart;\n            next = fraction.numeratorStart + Math.min(fraction.offset, length);\n        } else if (direction > 0\n                && fraction.slot == CnCwCursorPath.Slot.FRACTION_NUMERATOR) {\n            int length = fraction.denominatorEnd - fraction.denominatorStart;\n            next = fraction.denominatorStart + Math.min(fraction.offset, length);\n        }\n\n        cursor = Math.max(0, Math.min(tokens.size(), next));\n        clearSelection();\n        shiftArmed = false;\n        result = "";\n        resultShown = false;\n        errorShown = false;\n        lastError = null;\n        status = applicationStatus();\n        return true;\n    }\n\n    private List<String> spreadsheetCellsSnapshot() {'''
if anchor not in text:
    raise SystemExit('spreadsheet snapshot anchor not found')
text = text.replace(anchor, helpers, 1)

# 4) Rebuild the numerator from its token range so the cursor stays inside the fraction,
# including at the numerator start/end and for multi-token numbers.
old = '''            if (isFractionTemplate(token) && index != cursor && !children.isEmpty()) {\n                int denominatorEnd = naturalExponentEnd(index + 1, end, false);\n                CnCwExpressionNode numerator = children.remove(children.size() - 1);\n                CnCwExpressionNode denominator = naturalRow(index + 1, denominatorEnd);\n                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.FRACTION,\n                        com.codex.fx991.core.Compat.list(numerator, denominator), false));\n                if (denominator.containsCursor() && cursor == denominatorEnd) {\n                    cursorHandledAt = denominatorEnd;\n                }\n                index = denominatorEnd;\n                continue;\n            }'''
new = '''            if (isFractionTemplate(token) && !children.isEmpty()) {\n                int numeratorStart = semanticAtomStart(index);\n                int denominatorEnd = naturalExponentEnd(index + 1, end, false);\n                CnCwExpressionNode prefix = naturalRow(start, numeratorStart);\n                CnCwExpressionNode numerator = naturalRow(numeratorStart, index);\n                CnCwExpressionNode denominator = naturalRow(index + 1, denominatorEnd);\n                children.clear();\n                children.addAll(prefix.children());\n                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.FRACTION,\n                        com.codex.fx991.core.Compat.list(numerator, denominator), false));\n                if (numerator.containsCursor() || denominator.containsCursor()) {\n                    cursorHandledAt = cursor;\n                }\n                index = denominatorEnd;\n                continue;\n            }'''
if old not in text:
    raise SystemExit('fraction renderer anchor not found')
text = text.replace(old, new, 1)

# 5) Add private fraction cursor record beside other state records.
old = '''    private record CoordinateCall(boolean polar, String first, String second) { }\n    private record SelectionRange(int start, int end) { }'''
new = '''    private record CoordinateCall(boolean polar, String first, String second) { }\n    private record FractionCursor(int templateIndex, int numeratorStart, int numeratorEnd,\n                                  int denominatorStart, int denominatorEnd,\n                                  CnCwCursorPath.Slot slot, int offset) { }\n    private record SelectionRange(int start, int end) { }'''
if old not in text:
    raise SystemExit('record anchor not found')
text = text.replace(old, new, 1)

machine_path.write_text(text, encoding='utf-8')

# UiState now receives the semantic cursor from the core publisher.
ui_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwUiState.java')
ui = ui_path.read_text(encoding='utf-8')
old = '''                CnCwExpressionNode naturalExpression,\n                int cursor,\n                int selectionStart,'''
new = '''                CnCwExpressionNode naturalExpression,\n                int cursor,\n                CnCwCursorPath semanticCursor,\n                int selectionStart,'''
if old not in ui:
    raise SystemExit('UiState constructor signature anchor not found')
ui = ui.replace(old, new, 1)
old = '''        this.cursor = Math.max(0, cursor);\n        this.semanticCursor = CnCwCursorPath.rootBoundary(this.cursor);'''
new = '''        this.cursor = Math.max(0, cursor);\n        this.semanticCursor = Objects.requireNonNull(semanticCursor, "semanticCursor");'''
if old not in ui:
    raise SystemExit('UiState semantic assignment anchor not found')
ui = ui.replace(old, new, 1)
ui_path.write_text(ui, encoding='utf-8')

# Extend the dedicated Stage 3 suite with fraction slot + vertical movement checks.
suite_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
suite = suite_path.read_text(encoding='utf-8')
old = '''        uiStatePublishesSemanticCursorFacade();\n        System.out.println("PASS " + checks + " semantic cursor checks");'''
new = '''        uiStatePublishesSemanticCursorFacade();\n        fractionPublishesNestedSlotsAndMovesVertically();\n        System.out.println("PASS " + checks + " semantic cursor checks");'''
if old not in suite:
    raise SystemExit('suite run anchor not found')
suite = suite.replace(old, new, 1)
anchor = '''    private void check(boolean condition, String message) {'''
method = '''    private void fractionPublishesNestedSlotsAndMovesVertically() {\n        CnCwMachine machine = new CnCwMachine(CnCwModel.FX_991_CN_CW);\n        machine.dispatch(CnCwKey.OK);\n        machine.dispatch(CnCwKey.DIGIT_5);\n        machine.dispatch(CnCwKey.FRACTION);\n        machine.dispatch(CnCwKey.DIGIT_6);\n\n        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,\n                machine.state().semanticCursor().slot(),\n                "fraction input ends inside denominator slot");\n        equal(1, machine.state().semanticCursor().childPath().get(0),\n                "fraction semantic path identifies template token");\n        equal(1, machine.state().semanticCursor().offset(),\n                "denominator cursor keeps local offset");\n\n        machine.dispatch(CnCwKey.UP);\n        equal(CnCwCursorPath.Slot.FRACTION_NUMERATOR,\n                machine.state().semanticCursor().slot(),\n                "UP moves denominator cursor into numerator");\n        equal(1, machine.state().semanticCursor().offset(),\n                "UP preserves nearest local offset");\n        check(machine.state().naturalExpression().containsCursor(),\n                "numerator semantic position remains visible in natural tree");\n\n        machine.dispatch(CnCwKey.DOWN);\n        equal(CnCwCursorPath.Slot.FRACTION_DENOMINATOR,\n                machine.state().semanticCursor().slot(),\n                "DOWN returns numerator cursor to denominator");\n        equal(1, machine.state().semanticCursor().offset(),\n                "DOWN restores denominator local offset");\n        check(machine.state().naturalExpression().containsCursor(),\n                "denominator semantic position remains visible in natural tree");\n\n        machine.moveCursorTo(2);\n        equal(0, machine.state().semanticCursor().offset(),\n                "denominator start has local offset zero");\n        machine.dispatch(CnCwKey.UP);\n        equal(0, machine.state().semanticCursor().offset(),\n                "vertical move also preserves start-of-slot position");\n        check(machine.state().naturalExpression().containsCursor(),\n                "fraction numerator start keeps cursor inside natural fraction tree");\n    }\n\n    private void check(boolean condition, String message) {'''
if anchor not in suite:
    raise SystemExit('suite method anchor not found')
suite = suite.replace(anchor, method, 1)
suite_path.write_text(suite, encoding='utf-8')

# Update relay status while keeping later DEL/selection work explicitly pending.
relay_path = Path('docs/hand-offs/STAGE3_RELAY.md')
relay = relay_path.read_text(encoding='utf-8')
relay = relay.replace('## 下一步：Step 2 分数内部移动', '## Step 2：分数内部移动（已开始）')
relay = relay.replace('Step 1 CI 通过后，下一项只做分数，不同时改幂和根号：',
'''Step 1 CI 已通过。Step 2 已进入第一子阶段，只做分数，不同时改幂和根号。\n\n当前正在接入：分子/分母语义 slot、上下切换和自然树内光标保持。DEL 与语义选区仍属于本 Step 的后续子阶段，不提前宣称完成。\n\n完整 Step 2 目标：''')
relay_path.write_text(relay, encoding='utf-8')

print('Stage 3 fraction semantic movement patch applied')

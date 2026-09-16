from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
text = machine_path.read_text(encoding='utf-8')
original = text

# 1) Keep a human-readable process snapshot for the value currently stored in Ans,
# and a snapshot for the result currently being shown (needed for history recall).
anchor = '''    private double ans;\n    private boolean hasAns;\n    private ExactValue exactAns;'''
replacement = '''    private double ans;\n    private boolean hasAns;\n    /** Human-readable calculation process that produced the current Ans value. */\n    private String ansProcessDisplay = "";\n    /** Human-readable calculation process associated with the currently shown result. */\n    private String resultProcessDisplay = "";\n    private ExactValue exactAns;'''
if anchor in text:
    text = text.replace(anchor, replacement, 1)
elif 'private String ansProcessDisplay' not in text:
    raise SystemExit('Ans field anchor not found')

# 2) Preserve snapshots across evaluation copies.
anchor = '''        ans = source.ans;\n        hasAns = source.hasAns;\n        exactAns = source.exactAns;'''
replacement = '''        ans = source.ans;\n        hasAns = source.hasAns;\n        ansProcessDisplay = source.ansProcessDisplay;\n        resultProcessDisplay = source.resultProcessDisplay;\n        exactAns = source.exactAns;'''
if anchor in text:
    text = text.replace(anchor, replacement, 1)
elif 'ansProcessDisplay = source.ansProcessDisplay;' not in text:
    raise SystemExit('copy constructor anchor not found')

# 3) Public inspection string + recursive expansion helper. Internal evaluator tokens stay unchanged.
anchor = '''    public CnCwUiState state() { return state; }\n\n    /**\n     * Applies exactly one logical key intent'''
replacement = '''    public CnCwUiState state() { return state; }\n\n    /**\n     * Returns the human-readable calculation process for inspection/copying.\n     * Ans is expanded to the already-expanded process that produced its value;\n     * evaluator tokens themselves are never rewritten.\n     */\n    public String calculationProcessDisplay() {\n        if (resultShown && resultProcessDisplay != null && !resultProcessDisplay.isBlank()) {\n            return resultProcessDisplay;\n        }\n        return expandedProcessDisplay(tokens, ansProcessDisplay);\n    }\n\n    private String expandedProcessDisplay(List<Token> source, String ansSource) {\n        StringBuilder out = new StringBuilder(Math.max(8, source.size() * 2));\n        for (Token token : source) {\n            if ("Ans".equals(token.evaluation) && ansSource != null && !ansSource.isBlank()) {\n                out.append('(').append(ansSource).append(')');\n            } else {\n                out.append(token.display);\n            }\n        }\n        return out.toString();\n    }\n\n    /**\n     * Applies exactly one logical key intent'''
if anchor in text:
    text = text.replace(anchor, replacement, 1)
elif 'public String calculationProcessDisplay()' not in text:
    raise SystemExit('public method anchor not found')

# 4) On successful evaluation, snapshot the expanded process BEFORE replacing Ans's source.
anchor = '''            if (storeAnswer && application != ApplicationMode.INEQUALITY) {\n                ans = scalar;\n                hasAns = true;'''
replacement = '''            String evaluatedProcessDisplay = expandedProcessDisplay(tokens, ansProcessDisplay);\n            resultProcessDisplay = evaluatedProcessDisplay;\n            if (storeAnswer && application != ApplicationMode.INEQUALITY) {\n                ansProcessDisplay = evaluatedProcessDisplay;\n                ans = scalar;\n                hasAns = true;'''
if anchor in text:
    text = text.replace(anchor, replacement, 1)
elif 'String evaluatedProcessDisplay = expandedProcessDisplay(tokens, ansProcessDisplay);' not in text:
    raise SystemExit('evaluation success anchor not found')

# 5) History must remember the process belonging to each historical result.
anchor = '            history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result));'
replacement = '            history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result, resultProcessDisplay));'
if anchor in text:
    text = text.replace(anchor, replacement, 1)
elif 'result, resultProcessDisplay));' not in text:
    raise SystemExit('history add anchor not found')

anchor = '''        tokens.addAll(entry.tokens);\n        cursor = tokens.size();\n        result = entry.result;'''
replacement = '''        tokens.addAll(entry.tokens);\n        cursor = tokens.size();\n        result = entry.result;\n        resultProcessDisplay = entry.processDisplay;'''
if anchor in text:
    text = text.replace(anchor, replacement, 1)
elif 'resultProcessDisplay = entry.processDisplay;' not in text:
    raise SystemExit('history recall anchor not found')

anchor = '    private record HistoryEntry(List<Token> tokens, String result) { }'
replacement = '    private record HistoryEntry(List<Token> tokens, String result, String processDisplay) { }'
if anchor in text:
    text = text.replace(anchor, replacement, 1)
elif 'String processDisplay' not in text:
    raise SystemExit('history record anchor not found')

# 6) Clear both process snapshots anywhere Ans itself is reset.
text = text.replace('''        ans = 0.0;\n        hasAns = false;\n        exactAns = null;''', '''        ans = 0.0;\n        hasAns = false;\n        ansProcessDisplay = "";\n        resultProcessDisplay = "";\n        exactAns = null;''')
if 'ansProcessDisplay = "";' not in text:
    raise SystemExit('reset snapshots were not inserted')

machine_path.write_text(text, encoding='utf-8')

# Android: when a finished expression contains Ans, show the expanded process for inspection;
# copy-calculation-process also uses the expanded human-readable string.
view_path = Path('app/src/main/java/com/codex/fx991smooth/CalculatorView.java')
view = view_path.read_text(encoding='utf-8')

anchor = '''        drawNaturalExpression(canvas, state.naturalExpression(), lcd, contentTop,\n                contentBottom, available);\n        if (state.hasSelection()) {'''
replacement = '''        boolean showExpandedAnsProcess = state.resultShown()\n                && state.expression().contains("Ans")\n                && !machine.calculationProcessDisplay().isBlank();\n        if (showExpandedAnsProcess) {\n            drawInspectionProcess(canvas, machine.calculationProcessDisplay(), lcd,\n                    contentTop, contentBottom, available);\n        } else {\n            drawNaturalExpression(canvas, state.naturalExpression(), lcd, contentTop,\n                    contentBottom, available);\n        }\n        if (state.hasSelection()) {'''
if anchor in view:
    view = view.replace(anchor, replacement, 1)
elif 'boolean showExpandedAnsProcess' not in view:
    raise SystemExit('drawApplicationScreen expression anchor not found')

# Insert a compact left-aligned renderer for the expanded inspection process.
anchor = '''    /** Draws phone-style handles at the two semantic selection boundaries. */\n    private void drawSelectionHandles'''
helper = '''    /** Draws an Ans-expanded finished calculation without changing editor tokens. */\n    private void drawInspectionProcess(Canvas canvas, String value, RectF lcd,\n                                       float contentTop, float contentBottom, float available) {\n        String display = value == null ? "" : value;\n        float left = lcd.left + dp(6);\n        float baseline = contentTop + Math.min(dp(31), (contentBottom - contentTop) * 0.32f);\n        paint.setColor(LCD_INK);\n        paint.setTypeface(FACE_NORMAL);\n        paint.setTextAlign(Paint.Align.LEFT);\n        for (float size = 25f; size >= 12f; size -= 1f) {\n            paint.setTextSize(sp(size));\n            if (paint.measureText(display) <= available) {\n                canvas.drawText(display, left, baseline, paint);\n                return;\n            }\n        }\n        paint.setTextSize(sp(12f));\n        float width = Math.max(1f, paint.measureText(display));\n        float scale = Math.min(1f, available / width);\n        canvas.save();\n        canvas.scale(scale, 1f, left, baseline);\n        canvas.drawText(display, left, baseline, paint);\n        canvas.restore();\n    }\n\n    /** Draws phone-style handles at the two semantic selection boundaries. */\n    private void drawSelectionHandles'''
if anchor in view:
    view = view.replace(anchor, helper, 1)
elif 'private void drawInspectionProcess' not in view:
    raise SystemExit('selection handle helper anchor not found')

anchor = '                case "复制计算过程" -> copyText(cleanClipboardText(state.expression()), "已复制计算过程");'
replacement = '                case "复制计算过程" -> copyText(cleanClipboardText(machine.calculationProcessDisplay()), "已复制展开后的计算过程");'
if anchor in view:
    view = view.replace(anchor, replacement, 1)
elif '已复制展开后的计算过程' not in view:
    raise SystemExit('copy calculation process anchor not found')

view_path.write_text(view, encoding='utf-8')

# Regression coverage: Ans remains internal evaluator semantics, inspection chain expands recursively.
suite_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java')
suite = suite_path.read_text(encoding='utf-8')
run_anchor = '''        ansTokenCanBeSelectedAndCopied();\n        calculateKeepsExactStandardResults();'''
run_replacement = '''        ansTokenCanBeSelectedAndCopied();\n        ansProcessExpandsForInspection();\n        calculateKeepsExactStandardResults();'''
if run_anchor in suite:
    suite = suite.replace(run_anchor, run_replacement, 1)
elif 'ansProcessExpandsForInspection();' not in suite:
    raise SystemExit('suite run anchor not found')

method_anchor = '''    private void calculateKeepsExactStandardResults() {'''
method = '''    private void ansProcessExpandsForInspection() {\n        CnCwMachine machine = calculateMachine();\n        press(machine, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3, CnCwKey.EXE);\n        equal("2+3", machine.calculationProcessDisplay(),\n                "first result keeps its readable calculation process");\n\n        press(machine, CnCwKey.MULTIPLY, CnCwKey.DIGIT_2, CnCwKey.EXE);\n        equal("Ans*2", machine.state().expression(),\n                "Ans remains evaluator semantics internally");\n        equal("(2+3)×2", machine.calculationProcessDisplay(),\n                "inspection process expands Ans to previous calculation");\n        equal("10", machine.state().result(), "expanded inspection does not alter result");\n\n        press(machine, CnCwKey.ADD, CnCwKey.DIGIT_1, CnCwKey.EXE);\n        equal("Ans+1", machine.state().expression(),\n                "second continuation still keeps internal Ans token");\n        equal("((2+3)×2)+1", machine.calculationProcessDisplay(),\n                "Ans process expansion is recursive across calculations");\n        equal("11", machine.state().result(), "recursive display expansion does not alter arithmetic");\n    }\n\n    private void calculateKeepsExactStandardResults() {'''
if method_anchor in suite:
    suite = suite.replace(method_anchor, method, 1)
elif 'private void ansProcessExpandsForInspection()' not in suite:
    raise SystemExit('suite method anchor not found')
suite_path.write_text(suite, encoding='utf-8')

# Version bump.
gradle_path = Path('app/build.gradle')
gradle = gradle_path.read_text(encoding='utf-8')
gradle_new = gradle.replace('versionCode 325', 'versionCode 326', 1)
gradle_new = gradle_new.replace("versionName '0.3.11'", "versionName '0.3.12'", 1)
if gradle_new == gradle and ('versionCode 326' not in gradle or "versionName '0.3.12'" not in gradle):
    raise SystemExit('version bump anchor not found')
gradle_path.write_text(gradle_new, encoding='utf-8')

# Strong post-patch assertions.
final_machine = machine_path.read_text(encoding='utf-8')
final_view = view_path.read_text(encoding='utf-8')
final_suite = suite_path.read_text(encoding='utf-8')
checks = [
    (final_machine, 'public String calculationProcessDisplay()'),
    (final_machine, 'ansProcessDisplay = evaluatedProcessDisplay;'),
    (final_machine, 'new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result, resultProcessDisplay)'),
    (final_view, 'boolean showExpandedAnsProcess'),
    (final_view, '已复制展开后的计算过程'),
    (final_suite, 'equal("(2+3)×2", machine.calculationProcessDisplay()'),
    (final_suite, 'equal("((2+3)×2)+1", machine.calculationProcessDisplay()'),
]
for haystack, needle in checks:
    if needle not in haystack:
        raise SystemExit(f'missing post-patch marker: {needle}')

print('patched recursive Ans process expansion while preserving internal Ans semantics')

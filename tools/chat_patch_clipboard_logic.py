from pathlib import Path

root = Path('.')

# -----------------------------------------------------------------------------
# 1) Move clipboard-expression parsing into the core machine.
# -----------------------------------------------------------------------------
machine_path = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = machine_path.read_text(encoding='utf-8')
anchor = '''    public List<String> cursorTokenDisplays() {
        List<String> labels = new ArrayList<>(tokens.size());
        for (Token token : tokens) labels.add(token.display());
        return com.codex.fx991.core.Compat.copyList(labels);
    }

    public CnCwUiState reset() {
'''
insert = '''    public List<String> cursorTokenDisplays() {
        List<String> labels = new ArrayList<>(tokens.size());
        for (Token token : tokens) labels.add(token.display());
        return com.codex.fx991.core.Compat.copyList(labels);
    }

    /**
     * Imports plain text from an external clipboard as semantic calculator tokens.
     *
     * <p>The Android adapter must not silently drop characters while simulating
     * key presses.  Clipboard text is parsed atomically here so display spellings
     * such as {@code ²}, {@code √( )}, {@code π} and evaluator spellings such as
     * {@code ^2}, {@code sqrt(} and {@code pi} round-trip through the same token
     * model used by normal key input.  Unknown non-whitespace characters reject
     * the whole paste instead of corrupting a valid expression by omission.</p>
     *
     * @return number of imported semantic tokens, 0 for blank/no-op, -1 when the
     *         source contains unsupported text.
     */
    public int pasteExpression(String text) {
        if (!poweredOn || !screen.isApplication() || applicationLanding || text == null) return 0;
        List<Token> imported = parsePastedTokens(text);
        if (imported == null) return -1;
        if (imported.isEmpty()) return 0;

        // Treat one system paste as one editor mutation.  This also makes undo
        // restore the entire pre-paste expression instead of only the last char.
        rememberUndo();
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";

        if (selectionActive()) {
            int start = selectionStart();
            int end = selectionEnd();
            tokens.subList(start, end).clear();
            cursor = start;
            clearSelection();
        } else if (resultShown) {
            tokens.clear();
            cursor = 0;
            clearSelection();
        }

        tokens.addAll(cursor, imported);
        cursor += imported.size();
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        status = applicationStatus();
        publish();
        return imported.size();
    }

    /** Returns null rather than partially importing text whose semantics are unknown. */
    private List<Token> parsePastedTokens(String text) {
        String source = text.replace("│", "").replace("▌", "").trim();
        List<Token> imported = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char value = source.charAt(index);
            if (Character.isWhitespace(value)) {
                index++;
                continue;
            }

            PasteMatch match = pasteLexeme(source, index);
            if (match != null) {
                imported.add(match.token);
                index += match.length;
                continue;
            }

            // Preserve scientific E notation as one evaluator token.  Without
            // this, 1E3 would be misread as 1 * variable-E * 3.
            if (Character.isDigit(value) || value == '.') {
                int numberEnd = index;
                boolean hasDigit = false;
                while (numberEnd < source.length()) {
                    char number = source.charAt(numberEnd);
                    if (Character.isDigit(number)) {
                        hasDigit = true;
                        numberEnd++;
                    } else if (number == '.') {
                        numberEnd++;
                    } else {
                        break;
                    }
                }
                if (hasDigit && numberEnd < source.length()
                        && (source.charAt(numberEnd) == 'E' || source.charAt(numberEnd) == 'e')) {
                    int exponentEnd = numberEnd + 1;
                    if (exponentEnd < source.length()
                            && (source.charAt(exponentEnd) == '+'
                            || source.charAt(exponentEnd) == '-'
                            || source.charAt(exponentEnd) == '−')) exponentEnd++;
                    int exponentDigits = exponentEnd;
                    while (exponentEnd < source.length()
                            && Character.isDigit(source.charAt(exponentEnd))) exponentEnd++;
                    if (exponentEnd > exponentDigits) {
                        String literal = source.substring(index, exponentEnd).replace('−', '-');
                        imported.add(token(literal, literal));
                        index = exponentEnd;
                        continue;
                    }
                }
            }

            Token token = switch (value) {
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.' -> token(Character.toString(value));
                case '+' -> token("+", "+", true);
                case '-', '−' -> token("−", "-", true);
                case '*', '×' -> token("×", "*", true);
                case '/', '÷' -> token("÷", "/", true);
                case '^' -> token("^", "^", true);
                case '(' -> token("(");
                case ')' -> token(")");
                case ',' -> token(",");
                case '!' -> token("!");
                case '%' -> token("%", "%");
                case '=' -> token("=", "=", true);
                case '<' -> token("<", "<", true);
                case '>' -> token(">", ">", true);
                case ':' -> token(":", ":", true);
                case 'π' -> token("π", "pi");
                case 'e' -> token("e", "e");
                case 'i' -> token("i", "i");
                case 'x' -> token("x");
                case 'y' -> token("y");
                case 'z' -> token("z");
                case 'A', 'B', 'C', 'D', 'E', 'F' -> token(Character.toString(value));
                case '²' -> token("²", "^2");
                case '³' -> token("³", "^3");
                case '√' -> token("√(", "sqrt(");
                case '∠' -> token("∠", "∠");
                default -> null;
            };
            if (token == null) return null;
            imported.add(token);
            index++;
            // The √ token already owns its opening parenthesis.  A displayed
            // string normally contains √(...), so consume that literal '(' once.
            if (value == '√' && index < source.length() && source.charAt(index) == '(') index++;
        }
        return imported;
    }

    /** Matches multi-character evaluator/display spellings before char fallback. */
    private PasteMatch pasteLexeme(String source, int index) {
        String[][] spellings = {
                {"sinh⁻¹(", "sinh⁻¹(", "asinh("},
                {"cosh⁻¹(", "cosh⁻¹(", "acosh("},
                {"tanh⁻¹(", "tanh⁻¹(", "atanh("},
                {"sin⁻¹(", "sin⁻¹(", "asin("},
                {"cos⁻¹(", "cos⁻¹(", "acos("},
                {"tan⁻¹(", "tan⁻¹(", "atan("},
                {"integral(", "∫(", "integral("},
                {"ranint(", "RanInt#(", "ranint("},
                {"sqrt(", "√(", "sqrt("},
                {"mixed(", "a b/c(", "mixed("},
                {"asinh(", "sinh⁻¹(", "asinh("},
                {"acosh(", "cosh⁻¹(", "acosh("},
                {"atanh(", "tanh⁻¹(", "atanh("},
                {"asin(", "sin⁻¹(", "asin("},
                {"acos(", "cos⁻¹(", "acos("},
                {"atan(", "tan⁻¹(", "atan("},
                {"sinh(", "sinh(", "sinh("},
                {"cosh(", "cosh(", "cosh("},
                {"tanh(", "tanh(", "tanh("},
                {"root(", "√[ ](", "root("},
                {"diff(", "d/dx(", "diff("},
                {"sum(", "Σ(", "sum("},
                {"abs(", "Abs(", "abs("},
                {"dms(", "DMS(", "dms("},
                {"pol(", "Pol(", "pol("},
                {"rec(", "Rec(", "rec("},
                {"ran(", "Ran#", "ran("},
                {"sin(", "sin(", "sin("},
                {"cos(", "cos(", "cos("},
                {"tan(", "tan(", "tan("},
                {"log(", "log(", "log("},
                {"ln(", "ln(", "ln("},
                {"f(", "f(", "f("},
                {"g(", "g(", "g("},
                {"Ans", "Ans", "Ans"},
                {"nPr", "nPr", "nPr"},
                {"nCr", "nCr", "nCr"},
                {"pi", "π", "pi"},
                {"<=", "≤", "<=", "binary"},
                {">=", "≥", ">=", "binary"},
                {"!=", "≠", "!=", "binary"},
                {"->", "→", "->", "binary"},
                {"⁻¹", "⁻¹", "^(-1)"}
        };
        for (String[] spelling : spellings) {
            if (!source.startsWith(spelling[0], index)) continue;
            boolean binary = spelling.length > 3 && "binary".equals(spelling[3]);
            Token token = token(spelling[1], spelling[2], binary);
            return new PasteMatch(token, spelling[0].length());
        }
        return null;
    }

    private static final class PasteMatch {
        private final Token token;
        private final int length;

        private PasteMatch(Token token, int length) {
            this.token = token;
            this.length = length;
        }
    }

    public CnCwUiState reset() {
'''
if 'public int pasteExpression(String text)' not in text:
    if anchor not in text:
        raise SystemExit('CnCwMachine paste anchor not found')
    text = text.replace(anchor, insert, 1)
machine_path.write_text(text, encoding='utf-8')

# -----------------------------------------------------------------------------
# 2) Android: system clipboard only. Core owns semantic parsing.
#    Add haptic at menu-open and menu-action transitions.
# -----------------------------------------------------------------------------
view_path = root / 'app/src/main/java/com/codex/fx991smooth/CalculatorView.java'
text = view_path.read_text(encoding='utf-8')
old_menu = '''    private void showClipboardMenu() {
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
'''
new_menu = '''    private void showClipboardMenu() {
        // Selection itself already ticks on boundary changes; keep the feedback
        // chain continuous when the user explicitly enters clipboard actions.
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        boolean hasSelection = state.hasSelection();
        String[] items = hasSelection
                ? new String[]{"复制选区", "复制计算过程", "复制计算结果", "粘贴"}
                : new String[]{"复制计算过程", "复制计算结果", "粘贴"};
        new AlertDialog.Builder(getContext()).setItems(items, (dialog, which) -> {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
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
'''
if old_menu not in text and new_menu not in text:
    raise SystemExit('clipboard menu block not found')
text = text.replace(old_menu, new_menu, 1)

old_copy = '''    private void copyText(String text, String message) {
        if (text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("计算器", text));
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }
'''
new_copy = '''    private void copyText(String text, String message) {
        if (text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("计算器", text));
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
'''
if old_copy not in text and new_copy not in text:
    raise SystemExit('copyText block not found')
text = text.replace(old_copy, new_copy, 1)

paste_start = text.find('    private void pasteClipboardText() {')
paste_end = text.find('\n    private CnCwKey pasteKey(char ch) {', paste_start)
if paste_start < 0 or paste_end < 0:
    raise SystemExit('pasteClipboardText/pasteKey block not found')
paste_key_end = text.find('\n    @Override\n    public boolean performClick()', paste_end)
if paste_key_end < 0:
    raise SystemExit('pasteKey end anchor not found')
new_paste = '''    private void pasteClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(getContext());
        if (value == null) return;

        int accepted = machine.pasteExpression(value.toString());
        state = machine.state();
        postInvalidateOnAnimation();
        String message = accepted < 0 ? "包含无法识别的符号，未粘贴"
                : accepted == 0 ? "没有可识别内容" : "已粘贴";
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
'''
text = text[:paste_start] + new_paste + text[paste_key_end:]
view_path.write_text(text, encoding='utf-8')

# -----------------------------------------------------------------------------
# 3) Regression: round-trip evaluator/display spellings and reject corruption.
# -----------------------------------------------------------------------------
suite_path = root / 'core/src/regression/java/com/codex/fx991/core/CnCwMachineSuite.java'
text = suite_path.read_text(encoding='utf-8')
call_anchor = '''        semanticTokensDoNotCollapseDuringEvaluation();
        calculateKeepsExactStandardResults();
'''
call_new = '''        semanticTokensDoNotCollapseDuringEvaluation();
        clipboardPastePreservesExpressionSemantics();
        calculateKeepsExactStandardResults();
'''
if 'clipboardPastePreservesExpressionSemantics();' not in text:
    if call_anchor not in text:
        raise SystemExit('suite run anchor not found')
    text = text.replace(call_anchor, call_new, 1)

method_anchor = '''    private void calculateKeepsExactStandardResults() {
'''
method = '''    private void clipboardPastePreservesExpressionSemantics() {
        CnCwMachine source = calculateMachine();
        press(source, CnCwKey.DIGIT_5, CnCwKey.DIGIT_6, CnCwKey.SQUARE);
        equal("56^2", source.state().expression(), "square copy uses evaluator source");

        CnCwMachine pasted = calculateMachine();
        check(pasted.pasteExpression(source.state().expression()) > 0,
                "evaluator source can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("3136", pasted.state().result(), "56^2 round-trips through clipboard");

        pasted = calculateMachine();
        check(pasted.pasteExpression("56²") > 0, "display superscript can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("3136", pasted.state().result(), "56² keeps square semantics");

        pasted = calculateMachine();
        check(pasted.pasteExpression("√(9)") > 0, "display square-root can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("3", pasted.state().result(), "square-root paste does not duplicate parenthesis");

        pasted = calculateMachine();
        check(pasted.pasteExpression("π/2") > 0, "pi display form can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("π/2", pasted.state().result(), "pi paste keeps exact semantics");

        pasted = calculateMachine();
        check(pasted.pasteExpression("i^2") > 0, "imaginary expression can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("-1", pasted.state().result(), "imaginary-unit paste uses complex engine");

        pasted = calculateMachine();
        press(pasted, CnCwKey.DIGIT_2, CnCwKey.ADD, CnCwKey.DIGIT_3, CnCwKey.EXE);
        check(pasted.pasteExpression("Ans+1") > 0, "Ans source can be pasted after result");
        pasted.dispatch(CnCwKey.EXE);
        equal("6", pasted.state().result(), "paste after result starts new expression but keeps Ans");

        pasted = calculateMachine();
        press(pasted, CnCwKey.DIGIT_7);
        equal(-1, pasted.pasteExpression("56@2"), "unsupported paste is rejected atomically");
        equal("7", pasted.state().expression(), "invalid paste cannot silently drop symbols");

        pasted = calculateMachine();
        check(pasted.pasteExpression("1E3") > 0, "scientific E literal can be pasted");
        pasted.dispatch(CnCwKey.EXE);
        equal("1000", pasted.state().result(), "scientific E literal keeps numeric meaning");
    }

'''
if 'private void clipboardPastePreservesExpressionSemantics()' not in text:
    if method_anchor not in text:
        raise SystemExit('suite method anchor not found')
    text = text.replace(method_anchor, method + method_anchor, 1)
suite_path.write_text(text, encoding='utf-8')

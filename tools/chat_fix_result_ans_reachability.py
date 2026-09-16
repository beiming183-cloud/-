from pathlib import Path

view_path = Path('app/src/main/java/com/codex/fx991smooth/CalculatorView.java')
text = view_path.read_text(encoding='utf-8')
original = text

old_call = '''                    drawFittedResultText(canvas, lines[0], lcd.right - dp(6),
                            contentTop + (contentBottom - contentTop) * 0.86f, available);'''
new_call = '''                    drawFittedResultText(canvas, lines[0], lcd, contentTop,
                            contentBottom, available);'''
if old_call in text:
    text = text.replace(old_call, new_call, 1)
elif new_call not in text:
    raise SystemExit('result renderer call shape not found')

old_sig = '''    private void drawFittedResultText(Canvas canvas, String value, float right,
                                      float baseline, float available) {
        String display = value == null ? "" : value;'''
new_sig = '''    private void drawFittedResultText(Canvas canvas, String value, RectF lcd,
                                      float contentTop, float contentBottom, float available) {
        float right = lcd.right - dp(6);
        float baseline = contentTop + (contentBottom - contentTop) * 0.86f;
        String display = value == null ? "" : value;'''
if old_sig in text:
    text = text.replace(old_sig, new_sig, 1)
elif new_sig not in text:
    raise SystemExit('result renderer signature not found')

old_scientific = '''        String scientific = compactScientificResult(display);
        if (!scientific.equals(display)) {
            display = scientific;
            for (float size = 24f; size >= 14f; size -= 1f) {
                paint.setTextSize(sp(size));
                if (paint.measureText(display) <= available) {
                    canvas.drawText(display, right, baseline, paint);
                    return;
                }
            }
        }'''
new_scientific = '''        String scientific = compactScientificResult(display);
        if (!scientific.equals(display)) {
            // The long-number fallback is created only after the first natural-SCI
            // pass, so route that newly-created E notation back through the natural
            // mantissa × 10 + raised exponent renderer instead of ever drawing E.
            if (drawNaturalScientificResult(canvas, scientific, lcd, contentTop,
                    contentBottom, available)) {
                return;
            }
            // Defensive fallback: even if the structured renderer rejects a future
            // scientific spelling, never expose raw E notation to the calculator LCD.
            display = scientific.replace("E", "×10^").replace("e", "×10^");
            for (float size = 24f; size >= 14f; size -= 1f) {
                paint.setTextSize(sp(size));
                if (paint.measureText(display) <= available) {
                    canvas.drawText(display, right, baseline, paint);
                    return;
                }
            }
        }'''
if old_scientific in text:
    text = text.replace(old_scientific, new_scientific, 1)
elif 'drawNaturalScientificResult(canvas, scientific, lcd, contentTop,' not in text:
    raise SystemExit('scientific fallback block not found')

longpress_anchor = '''                            displayLongPressTriggered = true;
                            if (machine.cursorLimit() == 0) {'''
longpress_replacement = '''                            displayLongPressTriggered = true;
                            if (state.resultShown() && state.hasAns()
                                    && isResultBand(displayDownY)) {
                                // A long-press on the lower result line must expose the
                                // clipboard actions directly. Previously it tried to select
                                // the expression above, making “复制 Ans” effectively unreachable.
                                displaySelectionMode = false;
                                selectionDragEdge = 0;
                                postInvalidateOnAnimation();
                                showClipboardMenu();
                                return;
                            }
                            if (machine.cursorLimit() == 0) {'''
if longpress_anchor in text:
    text = text.replace(longpress_anchor, longpress_replacement, 1)
elif '&& isResultBand(displayDownY))' not in text:
    raise SystemExit('display long-press anchor not found')

paste_anchor = '''    private void showPasteOnlyMenu() {
        new AlertDialog.Builder(getContext()).setItems(new String[]{"粘贴"}, (dialog, which) -> {'''
helper = '''    private boolean isResultBand(float y) {
        RectF lcd = displayBounds(getWidth());
        float contentTop = lcd.top + lcd.height() * 0.145f;
        float contentBottom = lcd.bottom - dp(3);
        return y >= contentTop + (contentBottom - contentTop) * 0.58f;
    }

    private void showPasteOnlyMenu() {
        new AlertDialog.Builder(getContext()).setItems(new String[]{"粘贴"}, (dialog, which) -> {'''
if paste_anchor in text:
    text = text.replace(paste_anchor, helper, 1)
elif 'private boolean isResultBand(float y)' not in text:
    raise SystemExit('paste menu anchor not found')

# Preserve exact displayed result when copying Ans after EXE. Converting through
# double here can silently lose digits for large integers such as 5555555^6.
old_ans = '''        if (state.resultShown() && !currentResult.isEmpty()) {
            return decimalResult(currentResult);
        }'''
new_ans = '''        if (state.resultShown() && !currentResult.isEmpty()) {
            return currentResult;
        }'''
if old_ans in text:
    text = text.replace(old_ans, new_ans, 1)
elif new_ans not in text:
    raise SystemExit('Ans clipboard exact-result branch not found')

view_path.write_text(text, encoding='utf-8')

gradle_path = Path('app/build.gradle')
gradle = gradle_path.read_text(encoding='utf-8')
gradle_new = gradle.replace('versionCode 324', 'versionCode 325', 1)
gradle_new = gradle_new.replace("versionName '0.3.10'", "versionName '0.3.11'", 1)
if gradle_new == gradle and ('versionCode 325' not in gradle or "versionName '0.3.11'" not in gradle):
    raise SystemExit('version bump anchors not found')
gradle_path.write_text(gradle_new, encoding='utf-8')

# Fail the patch itself if the two actual user-visible paths are not present.
final = view_path.read_text(encoding='utf-8')
checks = [
    'drawNaturalScientificResult(canvas, scientific, lcd, contentTop,',
    'scientific.replace("E", "×10^").replace("e", "×10^")',
    'state.resultShown() && state.hasAns()',
    '&& isResultBand(displayDownY))',
    'private boolean isResultBand(float y)',
    'return currentResult;',
]
for check in checks:
    if check not in final:
        raise SystemExit(f'missing post-patch check: {check}')

print('patched actual long-number SCI fallback and reachable Ans copy path')

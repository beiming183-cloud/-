from pathlib import Path

path = Path("app/src/main/java/com/codex/fx991smooth/CalculatorView.java")
text = path.read_text(encoding="utf-8")

old_draw = '''                    paint.setTextSize(sp(34f));
                    canvas.drawText(ellipsize(lines[0], available), lcd.right - dp(6),
                            contentTop + (contentBottom - contentTop) * 0.86f, paint);
'''
new_draw = '''                    drawFittedResultText(canvas, lines[0], lcd.right - dp(6),
                            contentTop + (contentBottom - contentTop) * 0.86f, available);
'''
if old_draw in text:
    text = text.replace(old_draw, new_draw, 1)
elif new_draw not in text:
    raise SystemExit("result draw block not found")

anchor = '''    /** Draws engineering and SCI output as a compact mantissa with superscript exponent. */
    private boolean drawNaturalScientificResult(Canvas canvas, String value, RectF lcd,
'''
helper = '''    /** Draws a result without losing trailing digits to an ellipsis. */
    private void drawFittedResultText(Canvas canvas, String value, float right,
                                      float baseline, float available) {
        String display = value == null ? "" : value;
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_MEDIUM);
        paint.setTextAlign(Paint.Align.RIGHT);

        for (float size = 34f; size >= 16f; size -= 1f) {
            paint.setTextSize(sp(size));
            if (paint.measureText(display) <= available) {
                canvas.drawText(display, right, baseline, paint);
                return;
            }
        }

        String scientific = compactScientificResult(display);
        if (!scientific.equals(display)) {
            display = scientific;
            for (float size = 24f; size >= 14f; size -= 1f) {
                paint.setTextSize(sp(size));
                if (paint.measureText(display) <= available) {
                    canvas.drawText(display, right, baseline, paint);
                    return;
                }
            }
        }

        // Last-resort fit still preserves every character instead of adding an ellipsis.
        for (float size = 13f; size >= 8f; size -= 1f) {
            paint.setTextSize(sp(size));
            if (paint.measureText(display) <= available) {
                canvas.drawText(display, right, baseline, paint);
                return;
            }
        }

        paint.setTextSize(sp(8f));
        float width = Math.max(1f, paint.measureText(display));
        float scale = Math.min(1f, available / width);
        canvas.save();
        canvas.scale(scale, 1f, right, baseline);
        canvas.drawText(display, right, baseline, paint);
        canvas.restore();
    }

    /** Converts only plain numeric results to a compact scientific fallback. */
    private static String compactScientificResult(String value) {
        if (value == null || !value.matches("[−-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][+−-]?\\d+)?")) {
            return value == null ? "" : value;
        }
        try {
            double numeric = Double.parseDouble(value.replace('−', '-'));
            if (!Double.isFinite(numeric) || numeric == 0.0d) return value;
            String scientific = String.format(java.util.Locale.US, "%.9E", numeric);
            int marker = scientific.indexOf('E');
            String mantissa = scientific.substring(0, marker);
            String exponent = scientific.substring(marker + 1);
            while (mantissa.contains(".") && mantissa.endsWith("0")) {
                mantissa = mantissa.substring(0, mantissa.length() - 1);
            }
            if (mantissa.endsWith(".")) mantissa = mantissa.substring(0, mantissa.length() - 1);
            boolean negativeExponent = exponent.startsWith("-");
            exponent = exponent.replace("+", "").replace("-", "");
            while (exponent.length() > 1 && exponent.startsWith("0")) {
                exponent = exponent.substring(1);
            }
            return mantissa + "E" + (negativeExponent ? "-" : "") + exponent;
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

'''
if helper not in text:
    if anchor not in text:
        raise SystemExit("scientific renderer anchor not found")
    text = text.replace(anchor, helper + anchor, 1)

# The generated Java string literal must contain doubled backslashes so Java's
# regex engine receives \d / \. rather than illegal Java string escapes.
bad_regex = r'value.matches("[−-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[Ee][+−-]?\d+)?")'
good_regex = r'value.matches("[−-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][+−-]?\\d+)?")'
text = text.replace(bad_regex, good_regex)

path.write_text(text, encoding="utf-8")

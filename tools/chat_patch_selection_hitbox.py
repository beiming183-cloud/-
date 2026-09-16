from pathlib import Path

path = Path('app/src/main/java/com/codex/fx991smooth/CalculatorView.java')
text = path.read_text(encoding='utf-8')
old = '''                    if (state.hasSelection()) {
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
'''
new = '''                    if (state.hasSelection()) {
                        RectF lcd = displayBounds(getWidth());
                        float contentTop = lcd.top + lcd.height() * 0.145f;
                        float contentBottom = lcd.bottom - dp(3);
                        float handleTop = contentTop + dp(1.5f) - dp(2.2f);
                        float handleBottom = Math.min(contentBottom - dp(10),
                                contentTop + dp(34)) + dp(2.2f);
                        float startX = displayBoundaryX(state.selectionStart());
                        float endX = displayBoundaryX(state.selectionEnd());
                        float startDistance = (float) Math.hypot(
                                event.getX() - startX, event.getY() - handleBottom);
                        float endDistance = (float) Math.hypot(
                                event.getX() - endX, event.getY() - handleTop);
                        float handleSlop = dp(15);
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
                        float textTop = contentTop - dp(4);
                        float textBottom = Math.min(contentBottom, contentTop + dp(38));
                        if (event.getX() >= left && event.getX() <= right
                                && event.getY() >= textTop && event.getY() <= textBottom) {
                            selectionTapCandidate = true;
                            return true;
                        }
                    }
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('selection hitbox block not found')
path.write_text(text, encoding='utf-8')

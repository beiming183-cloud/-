from pathlib import Path

root = Path('.')

# -----------------------------------------------------------------------------
# Task 4: enlarge the left 2x2 utility block without enlarging the d-pad/rocker.
# -----------------------------------------------------------------------------
layout_path = root / 'app/src/main/java/com/codex/fx991smooth/PhysicalKeyLayout.java'
layout = layout_path.read_text(encoding='utf-8')
old_controls = '''        // Left-side controls on the real unit: power/home above settings/back.
        addCircle(hits, key(CnCwKey.ON, "ON", "", Kind.CONTROL),
                margin + radius, controlTop + controlHeight * 0.29f, radius * 2f);
        addCircle(hits, key(CnCwKey.HOME, "⌂", "主屏", Kind.CONTROL),
                margin + radius * 3.15f, controlTop + controlHeight * 0.29f, radius * 2f);
        addCircle(hits, key(CnCwKey.SETTINGS, "≡", "设置", Kind.CONTROL),
                margin + radius, controlTop + controlHeight * 0.74f, radius * 2f);
        addCircle(hits, key(CnCwKey.BACK, "↩", "返回", Kind.CONTROL),
                margin + radius * 3.15f, controlTop + controlHeight * 0.74f, radius * 2f);
'''
new_controls = '''        // Left-side controls form one deliberate 2x2 module.  They are about
        // one quarter larger than the previous caps, while the d-pad and
        // right page rocker keep their established proportions.
        float utilityRadius = radius * 1.24f;
        float utilityLeftX = margin + utilityRadius;
        float utilityRightX = margin + utilityRadius * 3.04f;
        addCircle(hits, key(CnCwKey.ON, "ON", "", Kind.CONTROL),
                utilityLeftX, controlTop + controlHeight * 0.29f, utilityRadius * 2f);
        addCircle(hits, key(CnCwKey.HOME, "⌂", "主屏", Kind.CONTROL),
                utilityRightX, controlTop + controlHeight * 0.29f, utilityRadius * 2f);
        addCircle(hits, key(CnCwKey.SETTINGS, "≡", "设置", Kind.CONTROL),
                utilityLeftX, controlTop + controlHeight * 0.74f, utilityRadius * 2f);
        addCircle(hits, key(CnCwKey.BACK, "↩", "返回", Kind.CONTROL),
                utilityRightX, controlTop + controlHeight * 0.74f, utilityRadius * 2f);
'''
if old_controls in layout:
    layout = layout.replace(old_controls, new_controls, 1)
elif new_controls not in layout:
    raise SystemExit('utility control layout block not found')
layout_path.write_text(layout, encoding='utf-8')

metrics_path = root / 'core/src/main/java/com/codex/fx991/core/ui/Cw991LayoutMetrics.java'
metrics = metrics_path.read_text(encoding='utf-8')
old_rect = '''        if (!circular && isKeyboardRole(role)) {
            float keyWidth = width * 0.96f;
            float keyHeight = Math.min(height * 0.82f, width * 0.72f);
            float textHeight = Math.max(safeDensity * 28f, keyHeight);
'''
new_rect = '''        if (!circular && isKeyboardRole(role)) {
            // The left ON/HOME/SETTINGS/BACK block uses wider, deliberately
            // shorter rectangular caps: about 85% of a normal numeric key's
            // width and about 75-80% of its height on phone layouts.
            float keyWidth = width * (role == Role.CONTROL ? 0.98f : 0.96f);
            float keyHeight = role == Role.CONTROL
                    ? Math.min(height * 0.80f, width * 0.66f)
                    : Math.min(height * 0.82f, width * 0.72f);
            float textHeight = Math.max(safeDensity * 28f, keyHeight);
'''
if old_rect in metrics:
    metrics = metrics.replace(old_rect, new_rect, 1)
elif new_rect not in metrics:
    raise SystemExit('rectangular key metric block not found')
metrics_path.write_text(metrics, encoding='utf-8')

suite_path = root / 'core/src/regression/java/com/codex/fx991/core/ui/Cw991LayoutMetricsSuite.java'
suite = suite_path.read_text(encoding='utf-8')
old_vars = '''        Cw991LayoutMetrics.KeyVisual rectangularFunction = Cw991LayoutMetrics.forKey(
                Cw991LayoutMetrics.Role.FUNCTION, 136f, 158f, viewportWidth, density, false);
        Cw991LayoutMetrics.KeyVisual shift = Cw991LayoutMetrics.forKey(
'''
new_vars = '''        Cw991LayoutMetrics.KeyVisual rectangularFunction = Cw991LayoutMetrics.forKey(
                Cw991LayoutMetrics.Role.FUNCTION, 136f, 158f, viewportWidth, density, false);
        Cw991LayoutMetrics.KeyVisual rectangularNumber = Cw991LayoutMetrics.forKey(
                Cw991LayoutMetrics.Role.NUMBER, 216f, 190f, viewportWidth, density, false);
        Cw991LayoutMetrics.KeyVisual utilityControl = Cw991LayoutMetrics.forKey(
                Cw991LayoutMetrics.Role.CONTROL, 185f, 185f, viewportWidth, density, false);
        Cw991LayoutMetrics.KeyVisual shift = Cw991LayoutMetrics.forKey(
'''
if old_vars in suite:
    suite = suite.replace(old_vars, new_vars, 1)
elif new_vars not in suite:
    raise SystemExit('layout suite variable anchor not found')
old_checks = '''        check(!rectangularFunction.circular()
                        && rectangularFunction.width() > rectangularFunction.height(),
                "calculation keyboard exposes a wide rectangular keycap variant");
        check(Math.abs(shift.width() / shift.height() - 1f) <= 0.03f,
'''
new_checks = '''        check(!rectangularFunction.circular()
                        && rectangularFunction.width() > rectangularFunction.height(),
                "calculation keyboard exposes a wide rectangular keycap variant");
        check(utilityControl.width() / rectangularNumber.width() >= 0.80f
                        && utilityControl.width() / rectangularNumber.width() <= 0.90f,
                "left utility controls are 80-90 percent of a numeric key width");
        check(utilityControl.height() / rectangularNumber.height() >= 0.70f
                        && utilityControl.height() / rectangularNumber.height() <= 0.82f,
                "left utility controls are 70-82 percent of a numeric key height");
        check(Math.abs(shift.width() / shift.height() - 1f) <= 0.03f,
'''
if old_checks in suite:
    suite = suite.replace(old_checks, new_checks, 1)
elif new_checks not in suite:
    raise SystemExit('layout suite assertion anchor not found')
suite_path.write_text(suite, encoding='utf-8')

# -----------------------------------------------------------------------------
# Task 5: replace the harsh HOME grid with compact rounded application cards.
# -----------------------------------------------------------------------------
view_path = root / 'app/src/main/java/com/codex/fx991smooth/CalculatorView.java'
view = view_path.read_text(encoding='utf-8')
old_home = '''    private void drawHomeScreen(Canvas canvas, RectF lcd) {
        drawStatusBar(canvas, lcd, "");
        List<CnCwCommand> allItems = state.homeItems();
        List<CnCwCommand> items = state.homeVisibleItems();
        float contentTop = lcd.top + lcd.height() * 0.085f;
        float gap = dp(1);
        int columns = 3;
        int start = state.homeViewportStart();
        int visibleCount = items.size();
        int rows = 2;
        float cellWidth = (lcd.width() - gap * (columns + 1)) / columns;
        float cellHeight = (lcd.bottom - contentTop - gap * (rows + 1)) / rows;
        for (int visibleIndex = 0; visibleIndex < items.size(); visibleIndex++) {
            int index = start + visibleIndex;
            int row = visibleIndex / columns;
            int column = visibleIndex % columns;
            float left = lcd.left + gap + column * (cellWidth + gap);
            float top = contentTop + gap + row * (cellHeight + gap);
            scratch.set(left, top, left + cellWidth, top + cellHeight);
            boolean selected = index == state.selectedIndex();
            if (selected) {
                paint.setColor(LCD_DARK);
                canvas.drawRect(scratch, paint);
            }
            paint.setColor(selected ? LCD : LCD_INK);
            drawApplicationGlyph(canvas, items.get(visibleIndex).id(), scratch,
                    selected ? LCD : LCD_INK);
            paint.setTypeface(FACE_MEDIUM);
            paint.setTextSize(sp(16f));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(items.get(visibleIndex).label(), scratch.centerX(),
                    scratch.bottom - dp(2.8f), paint);
        }
        paint.setColor(Color.argb(92, 22, 37, 31));
        paint.setStrokeWidth(dp(0.55f));
        float gridMidX = lcd.left + lcd.width() / 3f;
        canvas.drawLine(gridMidX, contentTop, gridMidX, lcd.bottom, paint);
        canvas.drawLine(gridMidX * 2f - lcd.left, contentTop,
                gridMidX * 2f - lcd.left, lcd.bottom, paint);
        float gridMidY = contentTop + (lcd.bottom - contentTop) * 0.5f;
        canvas.drawLine(lcd.left, gridMidY, lcd.right, gridMidY, paint);
        if (allItems.size() > visibleCount) {
            drawScrollBar(canvas, lcd, start, visibleCount, allItems.size(), contentTop, lcd.bottom);
        }
    }
'''
new_home = '''    private void drawHomeScreen(Canvas canvas, RectF lcd) {
        drawStatusBar(canvas, lcd, "");
        List<CnCwCommand> allItems = state.homeItems();
        List<CnCwCommand> items = state.homeVisibleItems();
        float contentTop = lcd.top + lcd.height() * 0.115f;
        float outer = dp(4.5f);
        float columnGap = dp(4f);
        float rowGap = dp(5f);
        int columns = 3;
        int rows = 2;
        int start = state.homeViewportStart();
        int visibleCount = items.size();
        float cellWidth = (lcd.width() - outer * 2f - columnGap * (columns - 1)) / columns;
        float cellHeight = (lcd.bottom - contentTop - outer * 2f - rowGap) / rows;
        float radius = dp(4.2f);
        for (int visibleIndex = 0; visibleIndex < items.size(); visibleIndex++) {
            int index = start + visibleIndex;
            int row = visibleIndex / columns;
            int column = visibleIndex % columns;
            float left = lcd.left + outer + column * (cellWidth + columnGap);
            float top = contentTop + outer + row * (cellHeight + rowGap);
            scratch.set(left, top, left + cellWidth, top + cellHeight);
            boolean selected = index == state.selectedIndex();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(selected ? 56 : 14, 22, 37, 31));
            canvas.drawRoundRect(scratch, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(selected ? 1.1f : 0.65f));
            paint.setColor(Color.argb(selected ? 176 : 58, 22, 37, 31));
            canvas.drawRoundRect(scratch, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);

            drawApplicationGlyph(canvas, items.get(visibleIndex).id(), scratch, LCD_INK);
            drawFittedCentered(canvas, items.get(visibleIndex).label(), scratch.centerX(),
                    scratch.bottom - dp(18f), scratch.bottom - dp(2.5f),
                    sp(13.5f), scratch.width() * 0.88f, sp(10.5f), LCD_INK, FACE_MEDIUM);
        }
        if (allItems.size() > visibleCount) {
            drawScrollBar(canvas, lcd, start, visibleCount, allItems.size(),
                    contentTop + outer, lcd.bottom - outer);
        }
    }
'''
if old_home in view:
    view = view.replace(old_home, new_home, 1)
elif new_home not in view:
    raise SystemExit('HOME drawing method not found')
old_glyph = '''        float cx = cell.centerX();
        float cy = cell.top + cell.height() * 0.36f;
        float radius = Math.min(cell.width(), cell.height()) * 0.18f;
'''
new_glyph = '''        float cx = cell.centerX();
        float cy = cell.top + cell.height() * 0.38f;
        float radius = Math.min(cell.width(), cell.height()) * 0.145f;
'''
if old_glyph in view:
    view = view.replace(old_glyph, new_glyph, 1)
elif new_glyph not in view:
    raise SystemExit('HOME glyph proportions not found')
view_path.write_text(view, encoding='utf-8')

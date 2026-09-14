package com.codex.fx991.core.ui;

/** Regression checks for the visible-keycap/touch-target separation. */
public final class Cw991LayoutMetricsSuite {
    private int checks;

    public static void main(String[] args) {
        new Cw991LayoutMetricsSuite().run();
    }

    private void run() {
        float viewportWidth = 922f;
        float density = 2.75f;

        Cw991LayoutMetrics.KeyVisual number = Cw991LayoutMetrics.forKey(
                Cw991LayoutMetrics.Role.NUMBER, 162f, 158f, viewportWidth, density);
        Cw991LayoutMetrics.KeyVisual tallNumber = Cw991LayoutMetrics.forKey(
                Cw991LayoutMetrics.Role.NUMBER, 162f, 230f, viewportWidth, density);
        Cw991LayoutMetrics.KeyVisual function = Cw991LayoutMetrics.forKey(
                Cw991LayoutMetrics.Role.FUNCTION, 136f, 158f, viewportWidth, density);
        Cw991LayoutMetrics.KeyVisual shift = Cw991LayoutMetrics.forKey(
                Cw991LayoutMetrics.Role.SHIFT, 159f, 128f, viewportWidth, density);

        check(number.mainTextSize() / number.height() >= 0.40f,
                "number legend is large enough for its keycap");
        check(function.mainTextSize() / function.height() >= 0.30f,
                "function legend is large enough for its keycap");
        check(number.secondaryTextSize() / number.height() >= 0.15f,
                "shift legend remains readable");
        check(number.area() / (162f * 158f) <= 0.70f,
                "number keycap does not fill its touch target");
        check(function.area() / (136f * 158f) <= 0.70f,
                "function keycap does not fill its touch target");
        check(Math.abs(shift.width() / shift.height() - 1f) <= 0.03f,
                "SHIFT is visually circular");
        check(Math.abs(number.height() - tallNumber.height()) <= 0.01f,
                "keycap height is width-driven, not leftover-height-driven");
        check(number.height() > function.height(),
                "numeric keycaps retain the physical 991 size hierarchy");

        float[] rows = Cw991LayoutMetrics.mainRowCenters(
                900f, 1940f, viewportWidth, density);
        check(rows[1] - rows[0] < rows[3] - rows[2],
                "function rows are tighter than numeric rows");
        check(rows[5] - rows[0] < viewportWidth * 0.88f,
                "keyboard row stack is width-driven and compact");
        check(rows[3] - rows[2] >= viewportWidth * 0.17f,
                "numeric rows reserve a separate shift-legend band");
        check(1940f - rows[5] >= viewportWidth * 0.10f,
                "physical body margin remains below the last key row");

        System.out.println("PASS " + checks + " CW layout metric checks");
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

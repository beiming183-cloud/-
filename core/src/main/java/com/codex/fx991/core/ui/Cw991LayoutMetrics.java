package com.codex.fx991.core.ui;

/**
 * Platform-neutral proportions for the visible 991 keypad.
 *
 * <p>Touch cells may expand to consume a tall phone screen, but the visible
 * keycaps are width-driven like the physical calculator. Keeping these
 * metrics out of the Android painter makes that distinction testable.</p>
 */
public final class Cw991LayoutMetrics {
    public enum Role {
        NUMBER, OPERATOR, FUNCTION, CONTROL, CONTEXT, NAV, OK, SHIFT, ACTION, EXECUTE
    }

    public record KeyVisual(float width, float height, float mainTextSize,
                            float secondaryTextSize, float verticalBias,
                            boolean circular) {
        public float area() {
            return circular
                    ? (float) (Math.PI * width * height * 0.25)
                    : width * height;
        }
    }

    private Cw991LayoutMetrics() { }

    public static KeyVisual forKey(Role role, float touchWidth, float touchHeight,
                                   float viewportWidth, float density) {
        float safeDensity = Math.max(0.75f, density);
        float width = Math.max(safeDensity * 20f, touchWidth);
        float height = Math.max(safeDensity * 20f, touchHeight);
        float viewport = Math.max(width, viewportWidth);

        float diameter;
        float mainRatio;
        float secondaryRatio;
        float verticalBias;
        switch (role) {
            case NUMBER -> {
                // Let the round cap use nearly the whole grid cell.  The
                // earlier 0.86/0.90 cap left a visibly large ring of blank
                // space around the two legends on phone-sized screens.
                diameter = diameter(width, height, viewport, 0.92f, 0.90f, 0.170f);
                // Phone screens leave more visual room than a physical keycap.
                // Give the frequently-used number row a deliberate weight so it
                // reads at arm's length instead of looking like tiny labels in
                // oversized circles.
                mainRatio = 0.63f;
                secondaryRatio = 0.34f;
                verticalBias = 0.50f;
            }
            case OPERATOR -> {
                diameter = diameter(width, height, viewport, 0.92f, 0.90f, 0.170f);
                mainRatio = 0.46f;
                secondaryRatio = 0.30f;
                verticalBias = 0.50f;
            }
            case ACTION, EXECUTE -> {
                diameter = diameter(width, height, viewport, 0.92f, 0.90f, 0.170f);
                mainRatio = role == Role.EXECUTE ? 0.34f : 0.37f;
                secondaryRatio = 0.30f;
                verticalBias = 0.50f;
            }
            case FUNCTION -> {
                diameter = diameter(width, height, viewport, 0.92f, 0.90f, 0.145f);
                mainRatio = 0.56f;
                secondaryRatio = 0.39f;
                verticalBias = 0.50f;
            }
            case CONTEXT, SHIFT -> {
                diameter = diameter(width, height, viewport, 0.82f, 0.92f, 0.135f);
                mainRatio = 0.50f;
                secondaryRatio = 0.36f;
                verticalBias = 0.66f;
            }
            case CONTROL -> {
                diameter = Math.min(width, height);
                mainRatio = 0.40f;
                secondaryRatio = 0.19f;
                verticalBias = 0.50f;
            }
            case NAV -> {
                diameter = Math.min(width, height);
                mainRatio = 0.48f;
                secondaryRatio = 0.16f;
                verticalBias = 0.50f;
            }
            case OK -> {
                diameter = Math.min(width, height);
                mainRatio = 0.35f;
                secondaryRatio = 0.16f;
                verticalBias = 0.50f;
            }
            default -> throw new IllegalStateException("Unhandled key role: " + role);
        }
        float mainSize = diameter * mainRatio;
        if (role == Role.CONTROL) mainSize = Math.max(mainSize, safeDensity * 16f);
        if (role == Role.OK) mainSize = Math.max(mainSize, safeDensity * 14f);
        float secondarySize = Math.max(safeDensity * 15f,
                Math.min(safeDensity * 17f, diameter * secondaryRatio));
        return new KeyVisual(diameter, diameter, mainSize,
                secondarySize, verticalBias, true);
    }

    /**
     * Six physical row centers. Their spacing follows device width instead
     * of stretching to consume every remaining pixel on tall phones.
     */
    public static float[] mainRowCenters(float keyboardTop, float keyboardBottom,
                                         float viewportWidth, float density) {
        float safeDensity = Math.max(0.75f, density);
        float width = Math.max(viewportWidth, safeDensity * 180f);
        float[] desired = {
                width * 0.080f,
                width * 0.240f,
                width * 0.415f,
                width * 0.595f,
                width * 0.775f,
                width * 0.955f
        };
        float bottomInset = Math.max(safeDensity * 22f, width * 0.075f);
        float available = Math.max(safeDensity * 180f,
                keyboardBottom - bottomInset - keyboardTop);
        float scale = Math.min(1f, available / (desired[desired.length - 1] + width * 0.09f));
        float lastHalfPitch = width * 0.0875f * scale;
        float free = Math.max(0f,
                available - desired[desired.length - 1] * scale - lastHalfPitch);
        float leading = free * 0.5f;
        float[] centers = new float[desired.length];
        for (int index = 0; index < desired.length; index++) {
            centers[index] = keyboardTop + leading + desired[index] * scale;
        }
        return centers;
    }

    private static float diameter(float touchWidth, float touchHeight, float viewportWidth,
                                  float widthRatio, float heightRatio, float viewportRatio) {
        return Math.min(touchWidth * widthRatio,
                Math.min(touchHeight * heightRatio, viewportWidth * viewportRatio));
    }
}

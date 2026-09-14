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
                diameter = diameter(width, height, viewport, 0.78f, 0.84f, 0.124f);
                // Phone screens leave more visual room than a physical keycap.
                // Give the frequently-used number row a deliberate weight so it
                // reads at arm's length instead of looking like tiny labels in
                // oversized circles.
                mainRatio = 0.54f;
                secondaryRatio = 0.31f;
                verticalBias = 0.50f;
            }
            case OPERATOR -> {
                diameter = diameter(width, height, viewport, 0.78f, 0.84f, 0.124f);
                mainRatio = 0.46f;
                secondaryRatio = 0.30f;
                verticalBias = 0.50f;
            }
            case ACTION, EXECUTE -> {
                diameter = diameter(width, height, viewport, 0.78f, 0.84f, 0.124f);
                mainRatio = role == Role.EXECUTE ? 0.34f : 0.37f;
                secondaryRatio = 0.30f;
                verticalBias = 0.50f;
            }
            case FUNCTION -> {
                diameter = diameter(width, height, viewport, 0.74f, 0.74f, 0.108f);
                mainRatio = 0.45f;
                secondaryRatio = 0.31f;
                verticalBias = 0.50f;
            }
            case CONTEXT, SHIFT -> {
                diameter = diameter(width, height, viewport, 0.62f, 0.76f, 0.102f);
                mainRatio = role == Role.SHIFT ? 0.49f : 0.35f;
                secondaryRatio = 0.20f;
                verticalBias = 0.62f;
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
        return new KeyVisual(diameter, diameter, diameter * mainRatio,
                diameter * secondaryRatio, verticalBias, true);
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
                width * 0.070f,
                width * 0.220f,
                width * 0.390f,
                width * 0.565f,
                width * 0.740f,
                width * 0.915f
        };
        float bottomInset = Math.max(safeDensity * 22f, width * 0.075f);
        float available = Math.max(safeDensity * 180f,
                keyboardBottom - bottomInset - keyboardTop);
        float scale = Math.min(1f, available / desired[desired.length - 1]);
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

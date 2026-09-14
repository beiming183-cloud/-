package com.codex.fx991.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates touch pointers into physical-key presses.
 *
 * <p>A command is accepted exactly once on pointer-down. Pointer-up only releases the visual
 * press. Keeping this policy outside the View makes overlapping key presses deterministic and
 * models the two-key rollover advertised for the fx-991EX and fx-991ES PLUS.</p>
 */
public final class KeyInputRouter {
    public static final int MAX_ACTIVE_POINTERS = 2;

    private final Map<Integer, CalculatorKey> activePointers = new LinkedHashMap<>(MAX_ACTIVE_POINTERS);

    /** Returns true exactly once when this pointer/key press should be dispatched. */
    public boolean pointerDown(int pointerId, CalculatorKey key) {
        if (activePointers.containsKey(pointerId)
                || activePointers.containsValue(key)
                || activePointers.size() >= MAX_ACTIVE_POINTERS) {
            return false;
        }
        activePointers.put(pointerId, key);
        return true;
    }

    public void pointerUp(int pointerId) {
        activePointers.remove(pointerId);
    }

    public void cancelAll() {
        activePointers.clear();
    }

    public boolean isKeyPressed(CalculatorKey key) {
        return activePointers.containsValue(key);
    }

    public int activePointerCount() {
        return activePointers.size();
    }
}

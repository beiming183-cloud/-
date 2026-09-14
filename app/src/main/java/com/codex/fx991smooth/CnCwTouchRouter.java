package com.codex.fx991smooth;

import android.util.SparseArray;

import com.codex.fx991.core.cw.CnCwKey;

import java.util.EnumSet;

/**
 * Two-key rollover router for the touch adapter.
 * Commands are emitted by the view on pointer-down; release only clears visual state.
 */
final class CnCwTouchRouter {
    private static final int MAX_ACTIVE_KEYS = 2;
    private final SparseArray<CnCwKey> pointers = new SparseArray<>(MAX_ACTIVE_KEYS);
    private final EnumSet<CnCwKey> pressed = EnumSet.noneOf(CnCwKey.class);

    boolean pointerDown(int pointerId, CnCwKey key) {
        if (pointers.indexOfKey(pointerId) >= 0
                || pointers.size() >= MAX_ACTIVE_KEYS
                || pressed.contains(key)) {
            return false;
        }
        pointers.put(pointerId, key);
        pressed.add(key);
        return true;
    }

    void pointerUp(int pointerId) {
        CnCwKey key = pointers.get(pointerId);
        if (key == null) return;
        pointers.remove(pointerId);
        pressed.remove(key);
    }

    void cancelAll() {
        pointers.clear();
        pressed.clear();
    }

    boolean isPressed(CnCwKey key) {
        return pressed.contains(key);
    }
}

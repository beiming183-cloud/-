package com.codex.fx991smooth;

import android.os.Handler;

import com.codex.fx991.core.cw.CnCwKey;

/**
 * Owns the timing/state of press, long-press repeat, release and cancellation.
 * Calculator semantics remain in CnCwMachine; this class only emits typed keys.
 */
final class GestureController {
    interface Listener {
        void onRepeat(CnCwKey key);
    }

    static final long LONG_PRESS_MS = 420L;
    static final long REPEAT_MS = 72L;

    private final Handler handler;
    private final Listener listener;
    private Runnable repeatTask;
    private int pointerId = -1;
    private CnCwKey key;

    GestureController(Handler handler, Listener listener) {
        this.handler = handler;
        this.listener = listener;
    }

    void press(int pointerId, CnCwKey key) {
        if (!isRepeatable(key)) return;
        cancel();
        this.pointerId = pointerId;
        this.key = key;
        repeatTask = new Runnable() {
            @Override public void run() {
                if (GestureController.this.key == null) return;
                listener.onRepeat(GestureController.this.key);
                handler.postDelayed(this, REPEAT_MS);
            }
        };
        handler.postDelayed(repeatTask, LONG_PRESS_MS);
    }

    void release(int pointerId) {
        if (this.pointerId == pointerId) cancel();
    }

    void cancel() {
        if (repeatTask != null) handler.removeCallbacks(repeatTask);
        repeatTask = null;
        pointerId = -1;
        key = null;
    }

    static boolean isRepeatable(CnCwKey key) {
        return switch (key) {
            case DEL, DOT,
                    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
                    DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
                    LEFT, RIGHT, UP, DOWN -> true;
            default -> false;
        };
    }
}

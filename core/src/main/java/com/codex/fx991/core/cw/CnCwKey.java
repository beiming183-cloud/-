package com.codex.fx991.core.cw;

/**
 * Logical keys understood by the CN CW reducer.
 *
 * <p>The Android view may map touch, hardware, accessibility, or keyboard
 * events to this vocabulary. No visual widget state is required by the
 * reducer; a key is simply an intent. A few aliases (PLUS/MINUS, ENTER and
 * OPEN/CLOSE) are intentionally kept so different input adapters can share
 * the same machine.</p>
 */
public enum CnCwKey {
    ON,
    HOME,
    SETTINGS,
    BACK,
    OK,
    ENTER,
    UP,
    DOWN,
    /** Physical right-side page rocker; normalized to UP/DOWN by the machine. */
    PAGE_UP,
    PAGE_DOWN,
    LEFT,
    RIGHT,
    CATALOG,
    TOOLS,
    VARIABLE,
    FUNCTION,
    FORMAT,
    SHIFT,
    AC,
    DEL,

    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,
    DOT,
    COMMA,
    ADD,
    PLUS,
    SUBTRACT,
    MINUS,
    MULTIPLY,
    DIVIDE,
    /** Physical fraction-template key, distinct from arithmetic division. */
    FRACTION,
    POWER,
    OPEN_PAREN,
    OPEN,
    CLOSE_PAREN,
    CLOSE,
    /** Expression relation token (=); it is deliberately not an execute command. */
    EQUALS,
    /** Physical execute/confirm key. OK and ENTER share this execute semantic. */
    EXE,

    SIN,
    COS,
    TAN,
    LOG,
    LN,
    EXP,
    SQRT,
    ROOT,
    ABS,
    RECIPROCAL,
    SQUARE,
    CUBE,
    FACTORIAL,
    PERCENT,
    NPR,
    NCR,
    NEGATE,
    PI,
    E,
    ANS,
    RAN,

    VAR_A,
    VAR_B,
    VAR_C,
    VAR_D,
    VAR_E,
    VAR_F,
    VAR_X,
    VAR_Y,
    VAR_Z;

    /** Returns true only for keys that commit/evaluate the current workflow. */
    public boolean isExecuteKey() {
        return this == OK || this == ENTER || this == EXE;
    }

    /** Returns true for the expression relation token, never for EXE/OK/ENTER. */
    public boolean isExpressionEquals() { return this == EQUALS; }
}

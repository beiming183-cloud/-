package com.codex.fx991.core;

/** Keeps model-specific behavior out of the reducer and prevents EX/ES hybrid semantics. */
public interface ModelProfile {
    String id();
    String menuKeyLabel();
    boolean supportsUndo();
    InputToken shiftedToken(CalculatorKey key);
    InputToken alphaToken(CalculatorKey key);
}

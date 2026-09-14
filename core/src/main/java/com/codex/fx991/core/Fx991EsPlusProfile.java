package com.codex.fx991.core;

/** Compatibility profile kept separate even where the first core shares mappings. */
public final class Fx991EsPlusProfile implements ModelProfile {
    private final Fx991ExProfile shared = new Fx991ExProfile();

    @Override public String id() { return "fx-991ES PLUS"; }
    @Override public String menuKeyLabel() { return "MODE"; }
    @Override public boolean supportsUndo() { return false; }
    @Override public InputToken shiftedToken(CalculatorKey key) { return shared.shiftedToken(key); }
    @Override public InputToken alphaToken(CalculatorKey key) { return shared.alphaToken(key); }
}

package com.codex.fx991.core;

public final class Fx991ExProfile implements ModelProfile {
    @Override public String id() { return "fx-991EX"; }
    @Override public String menuKeyLabel() { return "MENU"; }
    @Override public boolean supportsUndo() { return true; }

    @Override
    public InputToken shiftedToken(CalculatorKey key) {
        return switch (key) {
            case RECIPROCAL -> new InputToken("!", "!");
            case SQUARE -> new InputToken("³", "^3");
            case SQRT -> new InputToken("∛(", "cbrt(");
            case POWER -> new InputToken("ˣ√(", "root(");
            case LOG -> new InputToken("10^(", "10^(");
            case LN -> new InputToken("e^(", "e^(");
            case SIN -> new InputToken("sin⁻¹(", "asin(");
            case COS -> new InputToken("cos⁻¹(", "acos(");
            case TAN -> new InputToken("tan⁻¹(", "atan(");
            case OPEN -> new InputToken("%", "/100");
            case CLOSE -> InputToken.of(",");
            case EXP -> new InputToken("π", "pi");
            case ANS -> new InputToken("π", "pi");
            default -> null;
        };
    }

    @Override
    public InputToken alphaToken(CalculatorKey key) {
        return switch (key) {
            case RECIPROCAL -> InputToken.of("A");
            case SQUARE -> InputToken.of("B");
            case SQRT -> InputToken.of("C");
            case POWER -> InputToken.of("D");
            case LOG -> InputToken.of("E");
            case LN -> InputToken.of("F");
            case NEGATE -> InputToken.of("X");
            case DMS -> InputToken.of("Y");
            case M_PLUS -> InputToken.of("M");
            default -> null;
        };
    }
}

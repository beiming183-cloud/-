package com.codex.fx991.core.mode;

import com.codex.fx991.core.AngleUnit;

import java.util.Objects;

/** Settings listed on manual pages 19-22, independent from Android preferences. */
public final class CalculatorSettings {
    public enum InputOutput { MATH_MATH, MATH_DECIMAL, LINEAR_LINEAR, LINEAR_DECIMAL }
    public enum DisplayKind { FIX, SCI, NORM_1, NORM_2 }
    public enum FractionKind { MIXED, IMPROPER }
    public enum ComplexKind { RECTANGULAR, POLAR }
    public enum DecimalMark { POINT, COMMA }
    public enum MultiLineFont { NORMAL, SMALL }

    private InputOutput inputOutput = InputOutput.MATH_MATH;
    private AngleUnit angleUnit = AngleUnit.DEG;
    private DisplayKind displayKind = DisplayKind.NORM_1;
    private int displayDigits = 9;
    private boolean engineeringSymbols;
    private FractionKind fractionKind = FractionKind.IMPROPER;
    private ComplexKind complexKind = ComplexKind.RECTANGULAR;
    private DecimalMark decimalMark = DecimalMark.POINT;
    private boolean digitSeparator;
    private MultiLineFont multiLineFont = MultiLineFont.NORMAL;

    public InputOutput inputOutput() { return inputOutput; }
    public void setInputOutput(InputOutput value) { inputOutput = Objects.requireNonNull(value); }
    public AngleUnit angleUnit() { return angleUnit; }
    public void setAngleUnit(AngleUnit value) { angleUnit = Objects.requireNonNull(value); }
    public DisplayKind displayKind() { return displayKind; }
    public int displayDigits() { return displayDigits; }
    public void setDisplay(DisplayKind kind, int digits) {
        Objects.requireNonNull(kind);
        if (kind == DisplayKind.FIX && (digits < 0 || digits > 9)) throw new IllegalArgumentException("Fix 0..9");
        if (kind == DisplayKind.SCI && (digits < 1 || digits > 10)) throw new IllegalArgumentException("Sci 1..10");
        displayKind = kind; displayDigits = digits;
    }
    public boolean engineeringSymbols() { return engineeringSymbols; }
    public void setEngineeringSymbols(boolean value) { engineeringSymbols = value; }
    public FractionKind fractionKind() { return fractionKind; }
    public void setFractionKind(FractionKind value) { fractionKind = Objects.requireNonNull(value); }
    public ComplexKind complexKind() { return complexKind; }
    public void setComplexKind(ComplexKind value) { complexKind = Objects.requireNonNull(value); }
    public DecimalMark decimalMark() { return decimalMark; }
    public void setDecimalMark(DecimalMark value) { decimalMark = Objects.requireNonNull(value); }
    public boolean digitSeparator() { return digitSeparator; }
    public void setDigitSeparator(boolean value) { digitSeparator = value; }
    public MultiLineFont multiLineFont() { return multiLineFont; }
    public void setMultiLineFont(MultiLineFont value) { multiLineFont = Objects.requireNonNull(value); }

    public void resetCalculationSettings() {
        inputOutput = InputOutput.MATH_MATH;
        angleUnit = AngleUnit.DEG;
        displayKind = DisplayKind.NORM_1;
        displayDigits = 9;
        engineeringSymbols = false;
        fractionKind = FractionKind.IMPROPER;
        complexKind = ComplexKind.RECTANGULAR;
        decimalMark = DecimalMark.POINT;
        digitSeparator = false;
        multiLineFont = MultiLineFont.NORMAL;
    }
}

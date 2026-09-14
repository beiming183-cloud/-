package com.codex.fx991.core.cw;

import com.codex.fx991.core.AngleUnit;

import java.util.Objects;

/**
 * Immutable calculation/display settings used by the CN CW shell.
 *
 * <p>The older prototype exposed a mutable settings bean.  Keeping a value
 * object in the reducer state makes a back/AC/menu transition deterministic
 * and lets Android render from a snapshot safely.</p>
 */
public final class CnCwSettings {
    public enum InputOutput { MATH_MATH, MATH_DECIMAL, LINEAR_LINEAR, LINEAR_DECIMAL }
    public enum DisplayMode { NORM_1, NORM_2, FIX, SCI }
    public enum FractionMode { MIXED, IMPROPER }
    public enum ComplexMode { RECTANGULAR, POLAR }
    public enum DecimalMark { POINT, COMMA }
    public enum MultiLineFont { NORMAL, SMALL }

    private final InputOutput inputOutput;
    private final AngleUnit angleUnit;
    private final DisplayMode displayMode;
    private final int displayDigits;
    private final boolean engineeringSymbols;
    private final FractionMode fractionMode;
    private final ComplexMode complexMode;
    private final DecimalMark decimalMark;
    private final boolean digitSeparator;
    private final MultiLineFont multiLineFont;

    private CnCwSettings(InputOutput inputOutput,
                         AngleUnit angleUnit,
                         DisplayMode displayMode,
                         int displayDigits,
                         boolean engineeringSymbols,
                         FractionMode fractionMode,
                         ComplexMode complexMode,
                         DecimalMark decimalMark,
                         boolean digitSeparator,
                         MultiLineFont multiLineFont) {
        this.inputOutput = Objects.requireNonNull(inputOutput, "inputOutput");
        this.angleUnit = Objects.requireNonNull(angleUnit, "angleUnit");
        this.displayMode = Objects.requireNonNull(displayMode, "displayMode");
        validateDigits(displayMode, displayDigits);
        this.displayDigits = displayDigits;
        this.engineeringSymbols = engineeringSymbols;
        this.fractionMode = Objects.requireNonNull(fractionMode, "fractionMode");
        this.complexMode = Objects.requireNonNull(complexMode, "complexMode");
        this.decimalMark = Objects.requireNonNull(decimalMark, "decimalMark");
        this.digitSeparator = digitSeparator;
        this.multiLineFont = Objects.requireNonNull(multiLineFont, "multiLineFont");
    }

    public static CnCwSettings defaults() {
        return new CnCwSettings(InputOutput.MATH_MATH, AngleUnit.DEG,
                DisplayMode.NORM_1, 9, false, FractionMode.IMPROPER,
                ComplexMode.RECTANGULAR, DecimalMark.POINT, false,
                MultiLineFont.NORMAL);
    }

    public InputOutput inputOutput() { return inputOutput; }
    public AngleUnit angleUnit() { return angleUnit; }
    public DisplayMode displayMode() { return displayMode; }
    public int displayDigits() { return displayDigits; }
    public boolean engineeringSymbols() { return engineeringSymbols; }
    public FractionMode fractionMode() { return fractionMode; }
    public ComplexMode complexMode() { return complexMode; }
    public DecimalMark decimalMark() { return decimalMark; }
    public boolean digitSeparator() { return digitSeparator; }
    public MultiLineFont multiLineFont() { return multiLineFont; }

    public CnCwSettings withInputOutput(InputOutput value) {
        return copy(value, angleUnit, displayMode, displayDigits, engineeringSymbols,
                fractionMode, complexMode, decimalMark, digitSeparator, multiLineFont);
    }

    public CnCwSettings withAngleUnit(AngleUnit value) {
        return copy(inputOutput, value, displayMode, displayDigits, engineeringSymbols,
                fractionMode, complexMode, decimalMark, digitSeparator, multiLineFont);
    }

    public CnCwSettings withDisplay(DisplayMode mode, int digits) {
        return copy(inputOutput, angleUnit, mode, digits, engineeringSymbols,
                fractionMode, complexMode, decimalMark, digitSeparator, multiLineFont);
    }

    public CnCwSettings withEngineeringSymbols(boolean value) {
        return copy(inputOutput, angleUnit, displayMode, displayDigits, value,
                fractionMode, complexMode, decimalMark, digitSeparator, multiLineFont);
    }

    public CnCwSettings withFractionMode(FractionMode value) {
        return copy(inputOutput, angleUnit, displayMode, displayDigits, engineeringSymbols,
                value, complexMode, decimalMark, digitSeparator, multiLineFont);
    }

    public CnCwSettings withComplexMode(ComplexMode value) {
        return copy(inputOutput, angleUnit, displayMode, displayDigits, engineeringSymbols,
                fractionMode, value, decimalMark, digitSeparator, multiLineFont);
    }

    public CnCwSettings withDecimalMark(DecimalMark value) {
        return copy(inputOutput, angleUnit, displayMode, displayDigits, engineeringSymbols,
                fractionMode, complexMode, value, digitSeparator, multiLineFont);
    }

    public CnCwSettings withDigitSeparator(boolean value) {
        return copy(inputOutput, angleUnit, displayMode, displayDigits, engineeringSymbols,
                fractionMode, complexMode, decimalMark, value, multiLineFont);
    }

    public CnCwSettings withMultiLineFont(MultiLineFont value) {
        return copy(inputOutput, angleUnit, displayMode, displayDigits, engineeringSymbols,
                fractionMode, complexMode, decimalMark, digitSeparator, value);
    }

    public CnCwSettings resetCalculationSettings() { return defaults(); }

    /** Cycles the values shown by the compact settings rows. */
    public CnCwSettings cycleInputOutput() {
        InputOutput[] values = InputOutput.values();
        return withInputOutput(values[(inputOutput.ordinal() + 1) % values.length]);
    }

    public CnCwSettings cycleAngleUnit() { return withAngleUnit(angleUnit.next()); }

    public CnCwSettings cycleDisplayMode() {
        DisplayMode[] values = DisplayMode.values();
        DisplayMode next = values[(displayMode.ordinal() + 1) % values.length];
        int digits = next == DisplayMode.FIX ? Math.min(displayDigits, 9)
                : next == DisplayMode.SCI ? Math.max(1, Math.min(displayDigits, 10)) : displayDigits;
        return withDisplay(next, digits);
    }

    public CnCwSettings cycleFractionMode() {
        FractionMode[] values = FractionMode.values();
        return withFractionMode(values[(fractionMode.ordinal() + 1) % values.length]);
    }

    public CnCwSettings cycleComplexMode() {
        ComplexMode[] values = ComplexMode.values();
        return withComplexMode(values[(complexMode.ordinal() + 1) % values.length]);
    }

    public CnCwSettings cycleDecimalMark() {
        DecimalMark[] values = DecimalMark.values();
        return withDecimalMark(values[(decimalMark.ordinal() + 1) % values.length]);
    }

    public CnCwSettings toggleEngineeringSymbols() {
        return withEngineeringSymbols(!engineeringSymbols);
    }

    public CnCwSettings toggleDigitSeparator() { return withDigitSeparator(!digitSeparator); }

    public CnCwSettings cycleMultiLineFont() {
        MultiLineFont[] values = MultiLineFont.values();
        return withMultiLineFont(values[(multiLineFont.ordinal() + 1) % values.length]);
    }

    private static CnCwSettings copy(InputOutput io, AngleUnit angle, DisplayMode display,
                                    int digits, boolean engineering, FractionMode fraction,
                                    ComplexMode complex, DecimalMark decimal, boolean separator,
                                    MultiLineFont font) {
        return new CnCwSettings(io, angle, display, digits, engineering, fraction,
                complex, decimal, separator, font);
    }

    private static void validateDigits(DisplayMode mode, int digits) {
        if (mode == DisplayMode.FIX && (digits < 0 || digits > 9)) {
            throw new IllegalArgumentException("FIX digits must be 0..9");
        }
        if (mode == DisplayMode.SCI && (digits < 1 || digits > 10)) {
            throw new IllegalArgumentException("SCI digits must be 1..10");
        }
        if (mode != DisplayMode.FIX && mode != DisplayMode.SCI && digits < 0) {
            throw new IllegalArgumentException("digits");
        }
    }

    @Override public boolean equals(Object object) {
        if (!(object instanceof CnCwSettings other)) return false;
        return inputOutput == other.inputOutput && angleUnit == other.angleUnit
                && displayMode == other.displayMode && displayDigits == other.displayDigits
                && engineeringSymbols == other.engineeringSymbols
                && fractionMode == other.fractionMode && complexMode == other.complexMode
                && decimalMark == other.decimalMark && digitSeparator == other.digitSeparator
                && multiLineFont == other.multiLineFont;
    }

    @Override public int hashCode() {
        return Objects.hash(inputOutput, angleUnit, displayMode, displayDigits,
                engineeringSymbols, fractionMode, complexMode, decimalMark,
                digitSeparator, multiLineFont);
    }

    @Override public String toString() {
        return "CnCwSettings{" + inputOutput + ", " + angleUnit + ", " + displayMode
                + "(" + displayDigits + ")" + ", engineering=" + engineeringSymbols
                + ", fraction=" + fractionMode + ", complex=" + complexMode
                + ", decimal=" + decimalMark + ", separator=" + digitSeparator
                + ", font=" + multiLineFont + "}";
    }
}

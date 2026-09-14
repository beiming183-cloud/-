package com.codex.fx991.core.math;

/** Signed 32-bit base-N behavior for binary, octal, decimal, and hexadecimal modes. */
public final class BaseNEngine {
    private BaseNEngine() {}

    public enum Base {
        DECIMAL(10), HEXADECIMAL(16), BINARY(2), OCTAL(8);
        private final int radix;
        Base(int radix) { this.radix = radix; }
        public int radix() { return radix; }
        public Base next() {
            return switch (this) {
                case DECIMAL -> HEXADECIMAL;
                case HEXADECIMAL -> BINARY;
                case BINARY -> OCTAL;
                case OCTAL -> DECIMAL;
            };
        }
    }

    public static int parse(String text, Base base) {
        if (com.codex.fx991.core.Compat.isBlank(text)) throw new NumberFormatException("Empty");
        String normalized = text.trim().replace("_", "");
        boolean negative = normalized.startsWith("-");
        if (negative) normalized = normalized.substring(1);
        int maxDigits = switch (base) {
            case BINARY -> 32;
            case OCTAL -> 11;
            case HEXADECIMAL -> 8;
            case DECIMAL -> 10;
        };
        if (normalized.length() > maxDigits) throw new ArithmeticException("Range error");
        long unsigned = Long.parseUnsignedLong(normalized, base.radix());
        if (base == Base.DECIMAL) {
            long signed = negative ? -unsigned : unsigned;
            if (signed < Integer.MIN_VALUE || signed > Integer.MAX_VALUE) {
                throw new ArithmeticException("Range error");
            }
            return (int) signed;
        }
        if (unsigned > 0xFFFF_FFFFL) throw new ArithmeticException("Range error");
        int value = (int) unsigned;
        return negative ? Math.negateExact(value) : value;
    }

    public static int parsePrefixed(String text) {
        if (text == null || text.length() < 2) throw new NumberFormatException("Missing prefix");
        return switch (Character.toLowerCase(text.charAt(0))) {
            case 'd' -> parse(text.substring(1), Base.DECIMAL);
            case 'h' -> parse(text.substring(1), Base.HEXADECIMAL);
            case 'b' -> parse(text.substring(1), Base.BINARY);
            case 'o' -> parse(text.substring(1), Base.OCTAL);
            default -> throw new NumberFormatException("Unknown base prefix");
        };
    }

    public static String format(int value, Base base) {
        if (base == Base.DECIMAL) return Integer.toString(value);
        return switch (base) {
            case BINARY -> value < 0 ? leftPad(Integer.toBinaryString(value), 32) : Integer.toBinaryString(value);
            case OCTAL -> value < 0 ? leftPad(Integer.toUnsignedString(value, 8), 11) : Integer.toOctalString(value);
            case HEXADECIMAL -> value < 0 ? leftPad(Integer.toUnsignedString(value, 16), 8).toUpperCase()
                    : Integer.toHexString(value).toUpperCase();
            case DECIMAL -> throw new AssertionError();
        };
    }

    public static int add(int left, int right) { return checked((long) left + right); }
    public static int subtract(int left, int right) { return checked((long) left - right); }
    public static int multiply(int left, int right) { return checked((long) left * right); }
    public static int divide(int left, int right) {
        if (right == 0) throw new ArithmeticException("Division by zero");
        if (left == Integer.MIN_VALUE && right == -1) throw new ArithmeticException("Range error");
        return left / right;
    }
    public static int negate(int value) { return Math.negateExact(value); }
    public static int not(int value) { return ~value; }
    public static int and(int left, int right) { return left & right; }
    public static int or(int left, int right) { return left | right; }
    public static int xor(int left, int right) { return left ^ right; }
    public static int xnor(int left, int right) { return ~(left ^ right); }

    private static int checked(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new ArithmeticException("Range error");
        return (int) value;
    }

    private static String leftPad(String value, int length) {
        return com.codex.fx991.core.Compat.repeat("0", Math.max(0, length - value.length())) + value;
    }
}

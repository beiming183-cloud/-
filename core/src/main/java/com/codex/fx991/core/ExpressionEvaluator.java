package com.codex.fx991.core;

import java.util.Locale;
import java.util.Map;

/** Small numeric evaluator for the latency-critical COMP path; no CAS dependency. */
final class ExpressionEvaluator {
    private final String input;
    private final AngleUnit angleUnit;
    private final double ans;
    private final Map<Character, Double> variables;
    private int position;

    private ExpressionEvaluator(String input, AngleUnit angleUnit, double ans, Map<Character, Double> variables) {
        this.input = input;
        this.angleUnit = angleUnit;
        this.ans = ans;
        this.variables = variables;
    }

    static double evaluate(String input, AngleUnit angleUnit, double ans, Map<Character, Double> variables) {
        if (Compat.isBlank(input)) throw new SyntaxException("Empty expression", 0);
        String balanced = balanceParentheses(input);
        ExpressionEvaluator parser = new ExpressionEvaluator(balanced, angleUnit, ans, variables);
        double value = parser.parseExpression();
        parser.skipSpaces();
        if (!parser.atEnd()) throw parser.syntax("Unexpected '" + parser.peek() + "'");
        if (!Double.isFinite(value)) throw new ArithmeticException("Non-finite result");
        return value;
    }

    private static String balanceParentheses(String source) {
        int depth = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (depth < 0) return source;
        }
        return source + Compat.repeat(")", depth);
    }

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipSpaces();
            if (take('+')) value += parseTerm();
            else if (take('-')) value -= parseTerm();
            else break;
        }
        return value;
    }

    private double parseTerm() {
        double value = parseUnary();
        while (true) {
            skipSpaces();
            if (take('*')) value *= parseUnary();
            else if (take('/')) {
                double divisor = parseUnary();
                if (divisor == 0.0) throw new ArithmeticException("Division by zero");
                value /= divisor;
            } else if (startsPrimary()) {
                value *= parseUnary();
            } else break;
        }
        return value;
    }

    private double parseUnary() {
        skipSpaces();
        if (take('+')) return parseUnary();
        if (take('-') || take('~')) return -parseUnary();
        return parsePower();
    }

    private double parsePower() {
        double base = parsePostfix();
        skipSpaces();
        if (take('^')) return Math.pow(base, parseUnary());
        return base;
    }

    private double parsePostfix() {
        double value = parsePrimary();
        while (true) {
            skipSpaces();
            if (!take('!')) return value;
            value = factorial(value);
        }
    }

    private double parsePrimary() {
        skipSpaces();
        if (take('(')) {
            double value = parseExpression();
            expect(')');
            return value;
        }
        if (atEnd()) throw syntax("Expected a value");
        if (Character.isDigit(peek()) || peek() == '.') return parseNumber();
        if (Character.isLetter(peek())) return parseIdentifier();
        throw syntax("Expected a value");
    }

    private double parseNumber() {
        int start = position;
        boolean exponent = false;
        while (!atEnd()) {
            char c = peek();
            if (Character.isDigit(c) || c == '.') position++;
            else if ((c == 'E' || c == 'e') && !exponent) {
                exponent = true;
                position++;
                if (!atEnd() && (peek() == '+' || peek() == '-')) position++;
            } else break;
        }
        try {
            return Double.parseDouble(input.substring(start, position));
        } catch (NumberFormatException error) {
            throw new SyntaxException("Invalid number", start);
        }
    }

    private double parseIdentifier() {
        int start = position;
        while (!atEnd() && Character.isLetter(peek())) position++;
        String name = input.substring(start, position);
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("pi")) return Math.PI;
        if (lower.equals("e")) return Math.E;
        if (lower.equals("ans")) return ans;
        if (name.length() == 1 && Character.isUpperCase(name.charAt(0))) {
            return variables.getOrDefault(name.charAt(0), 0.0);
        }
        expect('(');
        double argument = parseExpression();
        if (lower.equals("root") && take(',')) {
            double degree = parseExpression();
            expect(')');
            return Math.pow(degree, 1.0 / argument);
        }
        expect(')');
        return applyFunction(lower, argument);
    }

    private double applyFunction(String name, double value) {
        return switch (name) {
            case "sin" -> Math.sin(angleUnit.toRadians(value));
            case "cos" -> Math.cos(angleUnit.toRadians(value));
            case "tan" -> Math.tan(angleUnit.toRadians(value));
            case "asin" -> angleUnit.fromRadians(Math.asin(value));
            case "acos" -> angleUnit.fromRadians(Math.acos(value));
            case "atan" -> angleUnit.fromRadians(Math.atan(value));
            case "sqrt" -> Math.sqrt(value);
            case "cbrt" -> Math.cbrt(value);
            case "log" -> Math.log10(value);
            case "ln" -> Math.log(value);
            case "abs" -> Math.abs(value);
            default -> throw new SyntaxException("Unknown function " + name, position);
        };
    }

    private static double factorial(double value) {
        if (value < 0 || value > 170 || value != Math.rint(value)) {
            throw new ArithmeticException("Factorial domain");
        }
        double result = 1.0;
        for (int i = 2; i <= (int) value; i++) result *= i;
        return result;
    }

    private boolean startsPrimary() {
        skipSpaces();
        if (atEnd()) return false;
        char c = peek();
        return c == '(' || Character.isLetter(c);
    }

    private void expect(char expected) {
        skipSpaces();
        if (!take(expected)) throw syntax("Expected '" + expected + "'");
    }

    private boolean take(char expected) {
        if (!atEnd() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private char peek() { return input.charAt(position); }
    private boolean atEnd() { return position >= input.length(); }
    private void skipSpaces() { while (!atEnd() && Character.isWhitespace(peek())) position++; }
    private SyntaxException syntax(String message) { return new SyntaxException(message, position); }

    static final class SyntaxException extends IllegalArgumentException {
        private final int position;
        SyntaxException(String message, int position) { super(message); this.position = position; }
        int position() { return position; }
    }
}

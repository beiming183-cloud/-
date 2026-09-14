package com.codex.fx991.core.math;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 45 x 5 spreadsheet model with relative/absolute references and a 1,700-byte budget. */
public final class SpreadsheetModel {
    public static final int ROWS = 45;
    public static final int COLUMNS = 5;
    public static final int MEMORY_LIMIT = 1700;
    public static final int INPUT_LIMIT = 49;
    private static final Pattern RANGE_FUNCTION = Pattern.compile(
            "(?i)(Min|Max|Mean|Sum)\\(\\s*(\\$?[A-E]\\$?\\d{1,2})\\s*:\\s*(\\$?[A-E]\\$?\\d{1,2})\\s*\\)");
    private static final Pattern CELL_REFERENCE = Pattern.compile("(?i)(\\$?)([A-E])(\\$?)(\\d{1,2})(?!\\d)");

    private final Cell[][] cells = new Cell[ROWS][COLUMNS];
    private boolean autoCalculate = true;
    private boolean displayFormula = true;
    private final ScalarExpressionEngine.EvaluationContext context;

    public SpreadsheetModel(ScalarExpressionEngine.EvaluationContext context) {
        this.context = context;
        for (int row = 0; row < ROWS; row++)
                for (int column = 0; column < COLUMNS; column++) cells[row][column] = Cell.empty();
    }

    /** Deep snapshot used by the cancellable Android evaluation boundary. */
    public SpreadsheetModel copy(ScalarExpressionEngine.EvaluationContext newContext) {
        SpreadsheetModel copy = new SpreadsheetModel(newContext);
        copy.autoCalculate = autoCalculate;
        copy.displayFormula = displayFormula;
        copy.restore(snapshot());
        return copy;
    }

    public boolean autoCalculate() { return autoCalculate; }
    public void setAutoCalculate(boolean autoCalculate) { this.autoCalculate = autoCalculate; }
    public boolean displayFormula() { return displayFormula; }
    public void setDisplayFormula(boolean displayFormula) { this.displayFormula = displayFormula; }

    public void set(String address, String input) {
        Address target = Address.parse(address);
        set(target, input);
    }

    private void set(Address target, String input) {
        if (com.codex.fx991.core.Compat.isBlank(input)) {
            cells[target.row][target.column] = Cell.empty();
            if (autoCalculate) recalculate();
            return;
        }
        if (input.length() > INPUT_LIMIT) throw new MemoryException("Cell input exceeds 49 bytes");
        Cell previous = cells[target.row][target.column];
        boolean formula = input.startsWith("=");
        Cell next;
        if (formula) {
            next = new Cell(input, true, Double.NaN, null);
        } else {
            double value = ScalarExpressionEngine.evaluate(resolveExpression(input, new HashSet<>()), context);
            double rounded = roundConstant(value);
            next = new Cell(Double.toString(rounded), false, rounded, null);
        }
        cells[target.row][target.column] = next;
        if (usedBytes() > MEMORY_LIMIT) {
            cells[target.row][target.column] = previous;
            throw new MemoryException("Spreadsheet exceeds 1700 bytes");
        }
        if (autoCalculate) recalculate();
    }

    public String input(String address) {
        return cell(Address.parse(address)).input;
    }

    public double value(String address) {
        Address target = Address.parse(address);
        Cell cell = cell(target);
        if (cell.error != null) throw new CellException(cell.error);
        if (cell.input.isEmpty()) return 0.0;
        if (cell.formula && Double.isNaN(cell.value)) evaluateCell(target, new HashSet<>());
        return cell(target).value;
    }

    public String display(String address) {
        Cell cell = cell(Address.parse(address));
        if (cell.error != null) return "错误";
        if (cell.formula && displayFormula) return cell.input;
        if (cell.input.isEmpty()) return "";
        try { return Double.toString(value(address)); }
        catch (CellException error) { return "错误"; }
    }

    public void clear(String address) { set(address, ""); }

    public void clearAll() {
        for (int row = 0; row < ROWS; row++)
            for (int column = 0; column < COLUMNS; column++) cells[row][column] = Cell.empty();
    }

    public void recalculate() {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                Cell cell = cells[row][column];
                if (cell.formula) cells[row][column] = new Cell(cell.input, true, Double.NaN, null);
            }
        }
        for (int row = 0; row < ROWS; row++)
            for (int column = 0; column < COLUMNS; column++)
                if (cells[row][column].formula) evaluateCell(new Address(row, column, false, false), new HashSet<>());
    }

    public void copy(String source, String destination) {
        Address from = Address.parse(source), to = Address.parse(destination);
        Cell cell = cell(from);
        if (cell.input.isEmpty()) { clear(destination); return; }
        String adjusted = cell.formula
                ? adjustReferences(cell.input, to.row - from.row, to.column - from.column)
                : cell.input;
        set(to, adjusted);
    }

    public void cut(String source, String destination) {
        Address from = Address.parse(source), to = Address.parse(destination);
        if (from.row == to.row && from.column == to.column) return;
        Cell value = cell(from);
        Cell oldDestination = cell(to);
        cells[to.row][to.column] = value;
        cells[from.row][from.column] = Cell.empty();
        try {
            if (usedBytes() > MEMORY_LIMIT) throw new MemoryException("Spreadsheet exceeds 1700 bytes");
            if (autoCalculate) recalculate();
        } catch (RuntimeException error) {
            cells[from.row][from.column] = value;
            cells[to.row][to.column] = oldDestination;
            throw error;
        }
    }

    public void fillFormula(String range, String formulaWithoutEquals) {
        Range target = Range.parse(range);
        Address origin = target.start;
        String formula = formulaWithoutEquals.startsWith("=") ? formulaWithoutEquals : "=" + formulaWithoutEquals;
        Cell[][] backup = snapshot();
        try {
            for (Address address : target.addresses()) {
                set(address, adjustReferences(formula, address.row - origin.row, address.column - origin.column));
            }
            if (autoCalculate) recalculate();
        } catch (RuntimeException error) {
            restore(backup);
            throw error;
        }
    }

    public void fillValue(String range, String expression) {
        Range target = Range.parse(range);
        Address origin = target.start;
        Cell[][] backup = snapshot();
        try {
            for (Address address : target.addresses()) {
                set(address, adjustReferences(expression,
                        address.row - origin.row, address.column - origin.column));
            }
            if (autoCalculate) recalculate();
        } catch (RuntimeException error) {
            restore(backup);
            throw error;
        }
    }

    public int usedBytes() {
        int total = 0;
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                if (cell.input.isEmpty()) continue;
                total += cell.formula ? cell.input.length() + 15 : 14;
            }
        }
        return total;
    }

    public int remainingBytes() { return MEMORY_LIMIT - usedBytes(); }

    private double evaluateCell(Address address, Set<Address> evaluating) {
        Cell current = cell(address);
        if (!current.formula) return current.input.isEmpty() ? 0.0 : current.value;
        if (!Double.isNaN(current.value) && current.error == null) return current.value;
        Address key = address.withoutAbsolute();
        if (!evaluating.add(key)) {
            cells[address.row][address.column] = new Cell(current.input, true, Double.NaN, "循环引用错误");
            throw new CircularReferenceException(address.label());
        }
        try {
            String resolved = resolveExpression(current.input.substring(1), evaluating);
            double value = ScalarExpressionEngine.evaluate(resolved, context);
            cells[address.row][address.column] = new Cell(current.input, true, value, null);
            return value;
        } catch (CircularReferenceException error) {
            cells[address.row][address.column] = new Cell(current.input, true, Double.NaN, "循环引用错误");
            throw error;
        } catch (RuntimeException error) {
            cells[address.row][address.column] = new Cell(current.input, true, Double.NaN, "错误");
            throw new CellException("Cell " + address.label() + ": " + error.getMessage());
        } finally {
            evaluating.remove(key);
        }
    }

    private String resolveExpression(String expression, Set<Address> evaluating) {
        String withRanges = replaceRanges(expression, evaluating);
        Matcher matcher = CELL_REFERENCE.matcher(withRanges);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            if (isScientificExponent(withRanges, matcher)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            Address reference = Address.parse(matcher.group());
            double value = evaluateCell(reference, evaluating);
            matcher.appendReplacement(output, Matcher.quoteReplacement("(" + numericLiteral(value) + ")"));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String replaceRanges(String expression, Set<Address> evaluating) {
        String current = expression;
        while (true) {
            Matcher matcher = RANGE_FUNCTION.matcher(current);
            if (!matcher.find()) return current;
            StringBuffer output = new StringBuffer();
            do {
                String function = matcher.group(1).toLowerCase(Locale.ROOT);
                Range range = new Range(Address.parse(matcher.group(2)), Address.parse(matcher.group(3)));
                List<Double> values = new ArrayList<>();
                for (Address address : range.addresses()) values.add(evaluateCell(address, evaluating));
                double replacement = switch (function) {
                    case "min" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                    case "max" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                    case "mean" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    case "sum" -> values.stream().mapToDouble(Double::doubleValue).sum();
                    default -> throw new AssertionError(function);
                };
                matcher.appendReplacement(output, Matcher.quoteReplacement("(" + numericLiteral(replacement) + ")"));
            } while (matcher.find());
            matcher.appendTail(output);
            current = output.toString();
        }
    }

    static String adjustReferences(String expression, int rowDelta, int columnDelta) {
        Matcher matcher = CELL_REFERENCE.matcher(expression);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            if (isScientificExponent(expression, matcher)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            boolean absoluteColumn = !matcher.group(1).isEmpty();
            boolean absoluteRow = !matcher.group(3).isEmpty();
            int column = Character.toUpperCase(matcher.group(2).charAt(0)) - 'A';
            int row = Integer.parseInt(matcher.group(4)) - 1;
            if (!absoluteColumn) column += columnDelta;
            if (!absoluteRow) row += rowDelta;
            String replacement;
            if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
                replacement = "?";
            } else {
                replacement = (absoluteColumn ? "$" : "") + (char) ('A' + column)
                        + (absoluteRow ? "$" : "") + (row + 1);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static boolean isScientificExponent(String expression, Matcher matcher) {
        if (!matcher.group(1).isEmpty() || !matcher.group(3).isEmpty()
                || !matcher.group(2).equalsIgnoreCase("E") || matcher.start() == 0) {
            return false;
        }
        char previous = expression.charAt(matcher.start() - 1);
        return Character.isDigit(previous) || previous == '.';
    }

    private static String numericLiteral(double value) {
        String text = Double.toString(value);
        int exponent = text.indexOf('E');
        if (exponent < 0) return text;
        return text.substring(0, exponent) + "*10^(" + text.substring(exponent + 1) + ")";
    }

    private Cell cell(Address address) { return cells[address.row][address.column]; }

    private Cell[][] snapshot() {
        Cell[][] copy = new Cell[ROWS][COLUMNS];
        for (int row = 0; row < ROWS; row++) System.arraycopy(cells[row], 0, copy[row], 0, COLUMNS);
        return copy;
    }

    private void restore(Cell[][] snapshot) {
        for (int row = 0; row < ROWS; row++) System.arraycopy(snapshot[row], 0, cells[row], 0, COLUMNS);
    }

    private static double roundConstant(double value) {
        if (value == 0.0) return 0.0;
        double scale = Math.pow(10.0, 9 - Math.floor(Math.log10(Math.abs(value))));
        return Math.rint(value * scale) / scale;
    }

    private record Cell(String input, boolean formula, double value, String error) {
        static Cell empty() { return new Cell("", false, 0.0, null); }
    }

    public record Address(int row, int column, boolean absoluteRow, boolean absoluteColumn) {
        public Address {
            if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
                throw new RangeException("Cell outside A1:E45");
            }
        }
        public static Address parse(String label) {
            Matcher matcher = CELL_REFERENCE.matcher(label.trim());
            if (!matcher.matches()) throw new RangeException("Invalid cell " + label);
            boolean absoluteColumn = !matcher.group(1).isEmpty();
            int column = Character.toUpperCase(matcher.group(2).charAt(0)) - 'A';
            boolean absoluteRow = !matcher.group(3).isEmpty();
            int row = Integer.parseInt(matcher.group(4)) - 1;
            return new Address(row, column, absoluteRow, absoluteColumn);
        }
        public String label() {
            return (absoluteColumn ? "$" : "") + (char) ('A' + column)
                    + (absoluteRow ? "$" : "") + (row + 1);
        }
        Address withoutAbsolute() { return new Address(row, column, false, false); }
    }

    public record Range(Address start, Address end) {
        public Range {
            int minRow = Math.min(start.row, end.row), maxRow = Math.max(start.row, end.row);
            int minColumn = Math.min(start.column, end.column), maxColumn = Math.max(start.column, end.column);
            start = new Address(minRow, minColumn, start.absoluteRow, start.absoluteColumn);
            end = new Address(maxRow, maxColumn, end.absoluteRow, end.absoluteColumn);
        }
        public static Range parse(String text) {
            String[] parts = text.split(":", -1);
            if (parts.length != 2) throw new RangeException("Expected start:end");
            return new Range(Address.parse(parts[0]), Address.parse(parts[1]));
        }
        public List<Address> addresses() {
            List<Address> result = new ArrayList<>();
            for (int row = start.row; row <= end.row; row++)
                for (int column = start.column; column <= end.column; column++)
                    result.add(new Address(row, column, false, false));
            return result;
        }
    }

    public static class CellException extends IllegalArgumentException {
        public CellException(String message) { super(message); }
    }
    public static final class CircularReferenceException extends CellException {
        public CircularReferenceException(String address) { super("Circular reference at " + address); }
    }
    public static final class RangeException extends CellException {
        public RangeException(String message) { super(message); }
    }
    public static final class MemoryException extends CellException {
        public MemoryException(String message) { super(message); }
    }
}

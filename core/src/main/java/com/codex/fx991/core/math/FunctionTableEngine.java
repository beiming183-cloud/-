package com.codex.fx991.core.math;

import java.util.ArrayList;
import java.util.List;

/** f(x)/g(x) table generation with the manual's 45-row and 30-row limits. */
public final class FunctionTableEngine {
    private FunctionTableEngine() {}

    public enum TableType { F_AND_G, F_ONLY, G_ONLY }

    public static List<Row> generate(
            ScalarExpressionEngine.CompiledExpression f,
            ScalarExpressionEngine.CompiledExpression g,
            TableType type,
            double start,
            double end,
            double step,
            ScalarExpressionEngine.EvaluationContext context) {
        if (!Double.isFinite(start) || !Double.isFinite(end)) {
            throw new RangeException("Start and end must be finite");
        }
        if (step == 0.0 || !Double.isFinite(step)) throw new RangeException("Step cannot be zero");
        if ((end - start) / step < -1e-12) throw new RangeException("Step has wrong direction");
        if ((type == TableType.F_AND_G && (f == null || g == null))
                || (type == TableType.F_ONLY && f == null)
                || (type == TableType.G_ONLY && g == null)) {
            throw new IllegalArgumentException("未定义");
        }
        int maximum = type == TableType.F_AND_G ? 30 : 45;
        long rowCount = (long) Math.floor((end - start) / step + 1e-12) + 1L;
        if (rowCount < 0 || rowCount > maximum) throw new RangeException("Range exceeds " + maximum + " rows");
        List<Row> rows = new ArrayList<>((int) rowCount);
        for (long index = 0; index < rowCount; index++) {
            double x = index == rowCount - 1 && Math.abs(start + index * step - end) < 1e-11
                    ? end : start + index * step;
            ScalarExpressionEngine.EvaluationContext atX = context.withX(x);
            Double fValue = type == TableType.G_ONLY ? null : f.evaluate(atX);
            Double gValue = type == TableType.F_ONLY ? null : g.evaluate(atX);
            rows.add(new Row(x, fValue, gValue));
        }
        return com.codex.fx991.core.Compat.copyList(rows);
    }

    public static boolean verify(double entered, double expected) {
        double tolerance = 1e-9 * Math.max(1.0, Math.abs(expected));
        return Math.abs(entered - expected) <= tolerance;
    }

    public record Row(double x, Double f, Double g) {}
    public static final class RangeException extends IllegalArgumentException {
        public RangeException(String message) { super(message); }
    }
}

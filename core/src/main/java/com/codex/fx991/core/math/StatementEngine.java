package com.codex.fx991.core.math;

import com.codex.fx991.core.AngleUnit;
import com.codex.fx991.core.Compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Multi-statement and variable-assignment layer for the scalar expression engine. */
public final class StatementEngine {
    private static final List<String> VARIABLE_NAMES =
            Compat.list("A", "B", "C", "D", "E", "F", "x", "y", "z");

    private StatementEngine() { }

    public static Outcome evaluate(String source,
                                   Map<String, Double> initialVariables,
                                   AngleUnit angleUnit,
                                   double initialAns,
                                   Random random) {
        if (Compat.isBlank(source)) throw new IllegalArgumentException("Empty input");
        Map<String, Double> variables = new HashMap<>();
        for (String name : VARIABLE_NAMES) variables.put(name, 0.0);
        if (initialVariables != null) {
            for (Map.Entry<String, Double> entry : initialVariables.entrySet()) {
                if (VARIABLE_NAMES.contains(entry.getKey())) variables.put(entry.getKey(), entry.getValue());
            }
        }
        List<String> statements = splitStatements(source);
        List<Double> results = new ArrayList<>(statements.size());
        double ans = initialAns;
        Random generator = random == null ? new Random() : random;
        for (String statement : statements) {
            Assignment assignment = assignment(statement);
            String expression = assignment == null ? statement : assignment.expression;
            ScalarExpressionEngine.EvaluationContext context =
                    new ScalarExpressionEngine.EvaluationContext(variables, angleUnit, ans,
                            null, null, generator);
            double value = ScalarExpressionEngine.evaluate(expression, context);
            if (assignment != null) variables.put(assignment.variable, value);
            results.add(value);
            ans = value;
        }
        return new Outcome(Compat.copyList(results), Compat.copyMap(variables), ans);
    }

    public static List<String> splitStatements(String source) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (value == ':' && depth == 0) {
                result.add(requireStatement(source.substring(start, index)));
                start = index + 1;
            }
            if (depth < 0) throw new IllegalArgumentException("Unbalanced parentheses");
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced parentheses");
        result.add(requireStatement(source.substring(start)));
        return Compat.copyList(result);
    }

    private static Assignment assignment(String statement) {
        int depth = 0;
        for (int index = 0; index < statement.length(); index++) {
            char value = statement.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (depth == 0 && value == '→') {
                return makeAssignment(statement.substring(0, index), statement.substring(index + 1));
            } else if (depth == 0 && value == '-' && index + 1 < statement.length()
                    && statement.charAt(index + 1) == '>') {
                return makeAssignment(statement.substring(0, index), statement.substring(index + 2));
            }
        }
        return null;
    }

    private static Assignment makeAssignment(String expression, String target) {
        String variable = target.trim();
        if (!VARIABLE_NAMES.contains(variable)) {
            throw new IllegalArgumentException("Assignment target must be A-F, x, y, or z");
        }
        return new Assignment(requireStatement(expression), variable);
    }

    private static String requireStatement(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Empty statement");
        return trimmed;
    }

    public record Outcome(List<Double> results, Map<String, Double> variables, double ans) {
        public Outcome {
            results = Compat.copyList(results);
            variables = Compat.copyMap(variables);
        }
    }

    private record Assignment(String expression, String variable) { }
}

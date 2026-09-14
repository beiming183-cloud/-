package com.codex.fx991.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable state; every key press produces one deterministic successor state. */
public final class CalculatorState {
    private final List<InputToken> tokens;
    private final int cursor;
    private final Modifier modifier;
    private final AngleUnit angleUnit;
    private final ResultMode resultMode;
    private final ScreenState screenState;
    private final InputMode inputMode;
    private final double ans;
    private final boolean hasAns;
    private final boolean justEvaluated;
    private final String result;
    private final String status;
    private final List<InputToken> alternateTokens;
    private final int alternateCursor;

    private CalculatorState(
            List<InputToken> tokens,
            int cursor,
            Modifier modifier,
            AngleUnit angleUnit,
            ResultMode resultMode,
            ScreenState screenState,
            InputMode inputMode,
            double ans,
            boolean hasAns,
            boolean justEvaluated,
            String result,
            String status,
            List<InputToken> alternateTokens,
            int alternateCursor) {
        this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
        this.cursor = cursor;
        this.modifier = modifier;
        this.angleUnit = angleUnit;
        this.resultMode = resultMode;
        this.screenState = screenState;
        this.inputMode = inputMode;
        this.ans = ans;
        this.hasAns = hasAns;
        this.justEvaluated = justEvaluated;
        this.result = result;
        this.status = status;
        this.alternateTokens = alternateTokens == null
                ? null : Collections.unmodifiableList(new ArrayList<>(alternateTokens));
        this.alternateCursor = alternateCursor;
    }

    public static CalculatorState initial() {
        return new CalculatorState(
                com.codex.fx991.core.Compat.list(), 0, Modifier.NONE, AngleUnit.DEG, ResultMode.DECIMAL,
                ScreenState.EDITING, InputMode.NATURAL_INSERT,
                0.0, false, false, "", "COMP", null, 0);
    }

    CalculatorState copy(
            List<InputToken> newTokens,
            int newCursor,
            Modifier newModifier,
            AngleUnit newAngleUnit,
            ResultMode newResultMode,
            ScreenState newScreenState,
            InputMode newInputMode,
            double newAns,
            boolean newHasAns,
            boolean newJustEvaluated,
            String newResult,
            String newStatus,
            List<InputToken> newAlternateTokens,
            int newAlternateCursor) {
        return new CalculatorState(
                newTokens, newCursor, newModifier, newAngleUnit, newResultMode,
                newScreenState, newInputMode,
                newAns, newHasAns, newJustEvaluated, newResult, newStatus,
                newAlternateTokens, newAlternateCursor);
    }

    public List<InputToken> tokens() { return tokens; }
    public int cursor() { return cursor; }
    public Modifier modifier() { return modifier; }
    public AngleUnit angleUnit() { return angleUnit; }
    public ResultMode resultMode() { return resultMode; }
    public ScreenState screenState() { return screenState; }
    public InputMode inputMode() { return inputMode; }
    public double ans() { return ans; }
    public boolean hasAns() { return hasAns; }
    public boolean justEvaluated() { return justEvaluated; }
    public String result() { return result; }
    public String status() { return status; }
    List<InputToken> alternateTokens() { return alternateTokens; }
    int alternateCursor() { return alternateCursor; }

    public String evaluationExpression() {
        StringBuilder out = new StringBuilder(tokens.size() * 2);
        for (InputToken token : tokens) out.append(token.evaluation());
        return out.toString();
    }

    public String displayExpression() {
        if (tokens.isEmpty()) return "0│";
        StringBuilder out = new StringBuilder(tokens.size() * 2 + 1);
        for (int i = 0; i <= tokens.size(); i++) {
            if (i == cursor) out.append('│');
            if (i < tokens.size()) out.append(tokens.get(i).display());
        }
        return out.toString();
    }
}

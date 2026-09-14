package com.codex.fx991.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The only component allowed to translate a physical key intent into calculator state.
 * It is Android-free, synchronous for editing, and therefore cheap to regression-test.
 */
public final class CalculatorReducer {
    private static final InputToken ANS_TOKEN = new InputToken("Ans", "Ans");
    private final ModelProfile profile;

    public CalculatorReducer(ModelProfile profile) {
        this.profile = profile;
    }

    public ModelProfile profile() {
        return profile;
    }

    public CalculatorState reduce(CalculatorState state, CalculatorKey key) {
        if (key == CalculatorKey.SHIFT) return toggleModifier(state, Modifier.SHIFT);
        if (key == CalculatorKey.ALPHA) return toggleModifier(state, Modifier.ALPHA);

        if (key == CalculatorKey.MODE && state.modifier() == Modifier.SHIFT) {
            return copy(state, state.tokens(), state.cursor(), Modifier.NONE, state.angleUnit(),
                    state.resultMode(), ScreenState.EDITING, state.inputMode(), state.ans(),
                    state.hasAns(), false, "", "SETUP · use DRG for angle");
        }

        if (key == CalculatorKey.DEL && state.modifier() == Modifier.ALPHA && profile.supportsUndo()) {
            return swapUndo(state);
        }
        if (key == CalculatorKey.DEL && state.modifier() == Modifier.SHIFT) {
            InputMode mode = state.inputMode() == InputMode.LINEAR_OVERWRITE
                    ? InputMode.NATURAL_INSERT : InputMode.LINEAR_OVERWRITE;
            return copy(state, state.tokens(), state.cursor(), Modifier.NONE, state.angleUnit(),
                    state.resultMode(), ScreenState.EDITING, mode, state.ans(), state.hasAns(),
                    false, state.result(), mode == InputMode.LINEAR_OVERWRITE ? "INS" : "COMP");
        }

        return switch (key) {
            case AC, ON -> clear(state);
            case DEL -> deleteBeforeCursor(state);
            case LEFT -> moveCursor(state, -1);
            case RIGHT -> moveCursor(state, 1);
            case ANGLE -> changeAngle(state);
            case S_D -> toggleResultMode(state);
            case EQUALS -> evaluate(state);
            case MODE -> showMenu(state);
            case SETUP -> status(state, "SETUP · use DRG for angle");
            default -> insertMappedToken(state, key);
        };
    }

    private CalculatorState toggleModifier(CalculatorState state, Modifier requested) {
        Modifier next = state.modifier() == requested ? Modifier.NONE : requested;
        return copy(state, state.tokens(), state.cursor(), next, state.angleUnit(), state.resultMode(),
                state.screenState(), state.inputMode(), state.ans(), state.hasAns(),
                state.justEvaluated(), state.result(), state.status());
    }

    private CalculatorState swapUndo(CalculatorState state) {
        if (state.alternateTokens() == null) {
            return copy(state, state.tokens(), state.cursor(), Modifier.NONE, state.angleUnit(),
                    state.resultMode(), state.screenState(), state.inputMode(), state.ans(),
                    state.hasAns(), state.justEvaluated(), state.result(), "Nothing to undo");
        }
        return state.copy(
                state.alternateTokens(), state.alternateCursor(), Modifier.NONE, state.angleUnit(),
                state.resultMode(), ScreenState.EDITING, state.inputMode(), state.ans(), state.hasAns(),
                false, "", "UNDO", state.tokens(), state.cursor());
    }

    private CalculatorState clear(CalculatorState state) {
        return state.copy(
                com.codex.fx991.core.Compat.list(), 0, Modifier.NONE, state.angleUnit(), state.resultMode(),
                ScreenState.EDITING, state.inputMode(), state.ans(), state.hasAns(),
                false, "", "COMP", state.tokens(), state.cursor());
    }

    private CalculatorState deleteBeforeCursor(CalculatorState state) {
        if (state.cursor() == 0 || state.tokens().isEmpty()) {
            return copy(state, state.tokens(), state.cursor(), Modifier.NONE, state.angleUnit(),
                    state.resultMode(), ScreenState.EDITING, state.inputMode(), state.ans(),
                    state.hasAns(), false, "", "COMP");
        }
        List<InputToken> next = new ArrayList<>(state.tokens());
        next.remove(state.cursor() - 1);
        return edited(state, next, state.cursor() - 1, Modifier.NONE);
    }

    private CalculatorState moveCursor(CalculatorState state, int delta) {
        int cursor = Math.max(0, Math.min(state.tokens().size(), state.cursor() + delta));
        ScreenState screen = state.screenState() == ScreenState.ERROR || state.screenState() == ScreenState.RESULT
                ? ScreenState.EDITING : state.screenState();
        return copy(state, state.tokens(), cursor, Modifier.NONE, state.angleUnit(), state.resultMode(),
                screen, state.inputMode(), state.ans(), state.hasAns(), false,
                screen == ScreenState.EDITING ? "" : state.result(), "COMP");
    }

    private CalculatorState changeAngle(CalculatorState state) {
        AngleUnit next = state.angleUnit().next();
        return copy(state, state.tokens(), state.cursor(), Modifier.NONE, next, state.resultMode(),
                state.screenState(), state.inputMode(), state.ans(), state.hasAns(),
                state.justEvaluated(), state.result(), next.name());
    }

    private CalculatorState toggleResultMode(CalculatorState state) {
        ResultMode next = state.resultMode() == ResultMode.DECIMAL
                ? ResultMode.FRACTION : ResultMode.DECIMAL;
        String result = state.hasAns() && state.screenState() == ScreenState.RESULT
                ? ResultFormatter.format(state.ans(), next) : state.result();
        return copy(state, state.tokens(), state.cursor(), Modifier.NONE, state.angleUnit(), next,
                state.screenState(), state.inputMode(), state.ans(), state.hasAns(),
                state.justEvaluated(), result, next == ResultMode.FRACTION ? "MathO" : "LineO");
    }

    private CalculatorState evaluate(CalculatorState state) {
        try {
            double value = ExpressionEvaluator.evaluate(
                    state.evaluationExpression(), state.angleUnit(), state.ans(), com.codex.fx991.core.Compat.emptyMap());
            return state.copy(
                    state.tokens(), state.tokens().size(), Modifier.NONE, state.angleUnit(),
                    state.resultMode(), ScreenState.RESULT, state.inputMode(), value, true,
                    true, ResultFormatter.format(value, state.resultMode()), "COMP",
                    state.alternateTokens(), state.alternateCursor());
        } catch (RuntimeException error) {
            String label = error instanceof ArithmeticException ? "Math ERROR" : "Syntax ERROR";
            return state.copy(
                    state.tokens(), state.cursor(), Modifier.NONE, state.angleUnit(),
                    state.resultMode(), ScreenState.ERROR, state.inputMode(), state.ans(),
                    state.hasAns(), false, label, "Press ← or → to edit",
                    state.alternateTokens(), state.alternateCursor());
        }
    }

    private CalculatorState showMenu(CalculatorState state) {
        ScreenState next = state.screenState() == ScreenState.MENU
                ? ScreenState.EDITING : ScreenState.MENU;
        return copy(state, state.tokens(), state.cursor(), Modifier.NONE, state.angleUnit(),
                state.resultMode(), next, state.inputMode(), state.ans(), state.hasAns(),
                false, next == ScreenState.MENU ? "1 Calculate\n2 Complex\n3 Base-N" : "",
                next == ScreenState.MENU ? profile.id() + " MENU" : "COMP");
    }

    private CalculatorState status(CalculatorState state, String message) {
        return copy(state, state.tokens(), state.cursor(), Modifier.NONE, state.angleUnit(),
                state.resultMode(), state.screenState(), state.inputMode(), state.ans(),
                state.hasAns(), state.justEvaluated(), state.result(), message);
    }

    private CalculatorState insertMappedToken(CalculatorState state, CalculatorKey key) {
        InputToken token = tokenFor(state.modifier(), key);
        if (token == null) return status(state, "Not in COMP core");

        boolean binary = isBinaryOperator(token);
        List<InputToken> base;
        int cursor;
        if (state.screenState() == ScreenState.RESULT || state.justEvaluated()) {
            if (binary && state.hasAns()) {
                base = new ArrayList<>();
                base.add(ANS_TOKEN);
                cursor = 1;
            } else {
                base = new ArrayList<>();
                cursor = 0;
            }
        } else {
            base = new ArrayList<>(state.tokens());
            cursor = state.cursor();
        }

        if (binary && base.isEmpty() && state.hasAns()) {
            base.add(ANS_TOKEN);
            cursor = 1;
        }
        if (binary && cursor > 0 && isBinaryOperator(base.get(cursor - 1))) {
            base.set(cursor - 1, token);
        } else if (state.inputMode() == InputMode.LINEAR_OVERWRITE && cursor < base.size()) {
            base.set(cursor, token);
            cursor++;
        } else {
            base.add(cursor, token);
            cursor++;
        }
        return state.copy(
                base, cursor, Modifier.NONE, state.angleUnit(), state.resultMode(),
                ScreenState.EDITING, state.inputMode(), state.ans(), state.hasAns(),
                false, "", "COMP", state.tokens(), state.cursor());
    }

    private InputToken tokenFor(Modifier modifier, CalculatorKey key) {
        if (modifier == Modifier.SHIFT) {
            InputToken shifted = profile.shiftedToken(key);
            if (shifted != null) return shifted;
        } else if (modifier == Modifier.ALPHA) {
            InputToken alpha = profile.alphaToken(key);
            if (alpha != null) return alpha;
        }
        return switch (key) {
            case DIGIT_0 -> InputToken.of("0");
            case DIGIT_1 -> InputToken.of("1");
            case DIGIT_2 -> InputToken.of("2");
            case DIGIT_3 -> InputToken.of("3");
            case DIGIT_4 -> InputToken.of("4");
            case DIGIT_5 -> InputToken.of("5");
            case DIGIT_6 -> InputToken.of("6");
            case DIGIT_7 -> InputToken.of("7");
            case DIGIT_8 -> InputToken.of("8");
            case DIGIT_9 -> InputToken.of("9");
            case DOT -> InputToken.of(".");
            case ADD -> InputToken.of("+");
            case SUBTRACT -> new InputToken("−", "-");
            case MULTIPLY -> new InputToken("×", "*");
            case DIVIDE, FRACTION -> new InputToken("÷", "/");
            case OPEN -> InputToken.of("(");
            case CLOSE -> InputToken.of(")");
            case NEGATE -> new InputToken("(−)", "~");
            case SIN -> new InputToken("sin(", "sin(");
            case COS -> new InputToken("cos(", "cos(");
            case TAN -> new InputToken("tan(", "tan(");
            case LOG -> new InputToken("log(", "log(");
            case LN -> new InputToken("ln(", "ln(");
            case SQRT -> new InputToken("√(", "sqrt(");
            case SQUARE -> new InputToken("²", "^2");
            case RECIPROCAL -> new InputToken("⁻¹", "^(-1)");
            case POWER -> new InputToken("^(", "^(");
            case ANS -> ANS_TOKEN;
            case PI -> new InputToken("π", "pi");
            case EXP -> new InputToken("×10^", "E");
            default -> null;
        };
    }

    private static boolean isBinaryOperator(InputToken token) {
        String value = token.evaluation();
        return value.equals("+") || value.equals("-") || value.equals("*") || value.equals("/");
    }

    private CalculatorState edited(
            CalculatorState state, List<InputToken> tokens, int cursor, Modifier modifier) {
        return state.copy(tokens, cursor, modifier, state.angleUnit(), state.resultMode(),
                ScreenState.EDITING, state.inputMode(), state.ans(), state.hasAns(), false,
                "", "COMP", state.tokens(), state.cursor());
    }

    private CalculatorState copy(
            CalculatorState state,
            List<InputToken> tokens,
            int cursor,
            Modifier modifier,
            AngleUnit angle,
            ResultMode resultMode,
            ScreenState screen,
            InputMode inputMode,
            double ans,
            boolean hasAns,
            boolean justEvaluated,
            String result,
            String status) {
        return state.copy(tokens, cursor, modifier, angle, resultMode, screen, inputMode,
                ans, hasAns, justEvaluated, result, status,
                state.alternateTokens(), state.alternateCursor());
    }
}

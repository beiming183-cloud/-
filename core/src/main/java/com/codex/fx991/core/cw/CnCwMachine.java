package com.codex.fx991.core.cw;

import com.codex.fx991.core.AngleUnit;
import com.codex.fx991.core.math.BaseNEngine;
import com.codex.fx991.core.math.ComplexExpressionEngine;
import com.codex.fx991.core.math.ComplexValue;
import com.codex.fx991.core.math.CalculationError;
import com.codex.fx991.core.math.CalculationException;
import com.codex.fx991.core.math.ExactValue;
import com.codex.fx991.core.math.ManualFunctions;
import com.codex.fx991.core.math.Rational;
import com.codex.fx991.core.math.ScalarExpressionEngine;
import com.codex.fx991.core.math.ScientificConstants;
import com.codex.fx991.core.math.SpreadsheetModel;
import com.codex.fx991.core.math.StatementEngine;
import com.codex.fx991.core.math.UnitConverter;
import com.codex.fx991.core.mode.ApplicationMode;
import com.codex.fx991.core.mode.CnCwModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Android-free CN CW shell and fast expression controller.
 *
 * <p>Every interaction enters through {@link #dispatch(CnCwKey)}. Menus,
 * HOME navigation, one-shot SHIFT, semantic expression tokens, cursor,
 * history, Ans, settings, and application landing screens therefore have a
 * single owner. The Android canvas is only an input/rendering adapter.</p>
 */
public final class CnCwMachine {
    private static final int HOME_COLUMNS = 3;
    /** Stable home-grid contract for the Android renderer (two rows × three tiles). */
    public static final int HOME_VIEWPORT_SIZE = CnCwUiState.HOME_VIEWPORT_SIZE;
    public static final int HOME_VIEWPORT_COLUMNS = CnCwUiState.HOME_VIEWPORT_COLUMNS;
    private static final List<String> VARIABLE_NAMES =
            com.codex.fx991.core.Compat.list("A", "B", "C", "D", "E", "F", "x", "y", "z");

    private final CnCwModel model;
    private final Random random = new Random();
    private final Map<String, Double> variables = new HashMap<>();
    private final Map<String, ExactValue> exactVariables = new HashMap<>();
    private final Deque<Navigation> navigation = new ArrayDeque<>();
    private final List<HistoryEntry> history = new ArrayList<>();
    private final List<Token> tokens = new ArrayList<>();
    private final SpreadsheetModel spreadsheet;
    private boolean spreadsheetGrid;
    private int spreadsheetRow;
    private int spreadsheetColumn;
    private int catalogCategoryIndex;
    private int conversionCategoryIndex;

    private CnCwScreen screen = CnCwScreen.HOME;
    private ApplicationMode application;
    private CnCwSettings settings = CnCwSettings.defaults();
    private int selectedIndex;
    private int cursor;
    private boolean shiftArmed;
    private boolean poweredOn = true;
    private boolean overwriteMode;
    private boolean applicationLanding;
    private boolean resultShown;
    private boolean verificationMode;
    private boolean manualSimplification;
    private String result = "";
    private String status = "HOME";
    private String activeCommandId = "";
    private double ans;
    private boolean hasAns;
    private ExactValue exactAns;
    private ExactValue lastExactResult;
    private String originalResult = "";
    private boolean formatConverted;
    private boolean engineeringMode;
    private int engineeringExponent;
    private boolean errorShown;
    private CalculationError lastError;
    private int errorCursor;
    private ComplexValue complexAns = ComplexValue.ZERO;
    private boolean hasComplexAns;
    private String functionFSource = "";
    private String functionGSource = "";
    private String pendingFunctionDefinition = "";
    private ScalarExpressionEngine.CompiledExpression functionF;
    private ScalarExpressionEngine.CompiledExpression functionG;
    private int historyIndex;
    private String statementSequenceSource = "";
    private List<String> statementSequence = com.codex.fx991.core.Compat.list();
    private int statementSequenceIndex;
    private List<Token> undoTokens;
    private int undoCursor;
    private CnCwUiState state;

    public CnCwMachine(CnCwModel model) {
        this.model = model;
        for (String name : VARIABLE_NAMES) {
            variables.put(name, 0.0);
            exactVariables.put(name, ExactValue.ZERO);
        }
        spreadsheet = new SpreadsheetModel(evaluationContext());
        publish();
    }

    private CnCwMachine(CnCwMachine source) {
        model = source.model;
        variables.putAll(source.variables);
        exactVariables.putAll(source.exactVariables);
        navigation.addAll(source.navigation);
        history.addAll(source.history);
        tokens.addAll(source.tokens);

        screen = source.screen;
        application = source.application;
        settings = source.settings;
        selectedIndex = source.selectedIndex;
        cursor = source.cursor;
        shiftArmed = source.shiftArmed;
        poweredOn = source.poweredOn;
        overwriteMode = source.overwriteMode;
        applicationLanding = source.applicationLanding;
        resultShown = source.resultShown;
        verificationMode = source.verificationMode;
        manualSimplification = source.manualSimplification;
        result = source.result;
        status = source.status;
        activeCommandId = source.activeCommandId;
        ans = source.ans;
        hasAns = source.hasAns;
        exactAns = source.exactAns;
        lastExactResult = source.lastExactResult;
        originalResult = source.originalResult;
        formatConverted = source.formatConverted;
        engineeringMode = source.engineeringMode;
        engineeringExponent = source.engineeringExponent;
        errorShown = source.errorShown;
        lastError = source.lastError;
        errorCursor = source.errorCursor;
        complexAns = source.complexAns;
        hasComplexAns = source.hasComplexAns;
        functionFSource = source.functionFSource;
        functionGSource = source.functionGSource;
        pendingFunctionDefinition = source.pendingFunctionDefinition;
        functionF = source.functionF;
        functionG = source.functionG;
        spreadsheetGrid = source.spreadsheetGrid;
        spreadsheetRow = source.spreadsheetRow;
        spreadsheetColumn = source.spreadsheetColumn;
        catalogCategoryIndex = source.catalogCategoryIndex;
        conversionCategoryIndex = source.conversionCategoryIndex;
        historyIndex = source.historyIndex;
        statementSequenceSource = source.statementSequenceSource;
        statementSequence = source.statementSequence;
        statementSequenceIndex = source.statementSequenceIndex;
        undoTokens = source.undoTokens == null ? null
                : com.codex.fx991.core.Compat.copyList(source.undoTokens);
        undoCursor = source.undoCursor;
        spreadsheet = source.spreadsheet.copy(evaluationContext());
        publish();
    }

    /**
     * Returns an isolated machine snapshot. Heavy EXE work can mutate this
     * copy off the UI thread and atomically replace the live machine only if
     * its input revision is still current.
     */
    public CnCwMachine copyForEvaluation() {
        return new CnCwMachine(this);
    }

    public CnCwUiState state() { return state; }

    /**
     * Applies exactly one logical key intent and returns the new snapshot.
     *
     * <p>EXE is the physical commit key.  OK and ENTER intentionally share
     * that command path; EQUALS is handled as an expression relation token
     * below and never enters {@link #evaluate()} from this dispatcher.</p>
     */
    public CnCwUiState dispatch(CnCwKey key) {
        if (key == null) return state;
        if (key == CnCwKey.ON) return reset();
        if (!poweredOn) return state;
        boolean consumeShift = key != CnCwKey.SHIFT && shiftArmed;
        if (key == CnCwKey.HOME) {
            showHome();
        } else if (key == CnCwKey.SHIFT) {
            shiftArmed = !shiftArmed;
            status = shiftArmed ? "SHIFT" : applicationStatus();
        } else if (screen == CnCwScreen.HOME) {
            handleHome(key);
        } else if (screen.isPopupMenu()) {
            handlePopup(key);
        } else if (screen.isApplication()) {
            handleApplication(key);
        }
        if (consumeShift && shiftArmed) shiftArmed = false;
        publish();
        return state;
    }

    public CnCwUiState reduce(CnCwKey key) { return dispatch(key); }

    /** Atomically moves the expression insertion point for direct-touch adapters. */
    public CnCwUiState moveCursorTo(int target) {
        if (!poweredOn || !screen.isApplication() || applicationLanding) return state;
        cursor = Math.max(0, Math.min(tokens.size(), target));
        shiftArmed = false;
        resultShown = false;
        publish();
        return state;
    }

    /** Number of semantic insertion slots currently available. */
    public int cursorLimit() { return tokens.size(); }

    /** Display labels for touch hit-testing; one entry per semantic token. */
    public List<String> cursorTokenDisplays() {
        List<String> labels = new ArrayList<>(tokens.size());
        for (Token token : tokens) labels.add(token.display());
        return com.codex.fx991.core.Compat.copyList(labels);
    }

    public CnCwUiState reset() {
        screen = CnCwScreen.HOME;
        application = null;
        settings = CnCwSettings.defaults();
        selectedIndex = 0;
        cursor = 0;
        shiftArmed = false;
        poweredOn = true;
        overwriteMode = false;
        applicationLanding = false;
        resultShown = false;
        verificationMode = false;
        manualSimplification = false;
        result = "";
        status = "HOME";
        activeCommandId = "";
        ans = 0.0;
        hasAns = false;
        exactAns = null;
        lastExactResult = null;
        originalResult = "";
        formatConverted = false;
        engineeringMode = false;
        engineeringExponent = 0;
        errorShown = false;
        lastError = null;
        errorCursor = 0;
        complexAns = ComplexValue.ZERO;
        hasComplexAns = false;
        functionFSource = "";
        functionGSource = "";
        pendingFunctionDefinition = "";
        functionF = null;
        functionG = null;
        spreadsheetGrid = false;
        spreadsheetRow = 0;
        spreadsheetColumn = 0;
        catalogCategoryIndex = 0;
        conversionCategoryIndex = 0;
        tokens.clear();
        history.clear();
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        navigation.clear();
        for (String name : VARIABLE_NAMES) {
            variables.put(name, 0.0);
            exactVariables.put(name, ExactValue.ZERO);
        }
        spreadsheet.clearAll();
        undoTokens = null;
        publish();
        return state;
    }

    private void handleHome(CnCwKey key) {
        int size = model.applications().size();
        selectedIndex = switch (key) {
            case LEFT -> wrap(selectedIndex - 1, size);
            case RIGHT -> wrap(selectedIndex + 1, size);
            case UP -> wrap(selectedIndex - HOME_COLUMNS, size);
            case DOWN -> wrap(selectedIndex + HOME_COLUMNS, size);
            case PAGE_UP -> wrap(selectedIndex - HOME_VIEWPORT_SIZE, size);
            case PAGE_DOWN -> wrap(selectedIndex + HOME_VIEWPORT_SIZE, size);
            default -> selectedIndex;
        };
        switch (key) {
            case OK, ENTER, EXE -> openApplication(model.applications().get(selectedIndex));
            case SETTINGS -> openPopup(CnCwScreen.SETTINGS);
            default -> { }
        }
    }

    private void handlePopup(CnCwKey key) {
        List<CnCwCommand> items = currentMenuItems();
        switch (key) {
            case UP -> selectedIndex = wrap(selectedIndex - 1, items.size());
            case DOWN -> selectedIndex = wrap(selectedIndex + 1, items.size());
            case PAGE_UP -> selectedIndex = wrap(selectedIndex - 6, items.size());
            case PAGE_DOWN -> selectedIndex = wrap(selectedIndex + 6, items.size());
            case LEFT, BACK -> back();
            case RIGHT, OK, ENTER, EXE -> activateMenuItem();
            case AC -> closeAllPopups();
            case SETTINGS -> switchPopup(CnCwScreen.SETTINGS);
            case CATALOG -> switchPopup(CnCwScreen.CATALOG);
            case TOOLS -> switchPopup(CnCwScreen.TOOLS);
            case VARIABLE -> switchPopup(CnCwScreen.VARIABLES);
            case FUNCTION -> switchPopup(CnCwScreen.FUNCTIONS);
            case FORMAT -> switchPopup(CnCwScreen.FORMAT);
            default -> { }
        }
    }

    private void handleApplication(CnCwKey key) {
        if (errorShown) {
            switch (key) {
                case OK, ENTER, BACK, AC -> { dismissError(); return; }
                case LEFT -> {
                    dismissError();
                    cursor = Math.max(0, cursor - 1);
                    return;
                }
                case RIGHT -> {
                    dismissError();
                    cursor = Math.min(tokens.size(), cursor + 1);
                    return;
                }
                default -> { }
            }
        }
        if (engineeringMode) {
            switch (key) {
                case LEFT -> {
                    engineeringExponent -= 3;
                    refreshEngineeringResult();
                    return;
                }
                case RIGHT -> {
                    engineeringExponent += 3;
                    refreshEngineeringResult();
                    return;
                }
                case BACK -> { restoreOriginalFormat(); return; }
                case AC -> {
                    engineeringMode = false;
                    formatConverted = false;
                    clearExpression();
                    return;
                }
                case FORMAT -> { engineeringMode = false; openPopup(CnCwScreen.FORMAT); return; }
                default -> {
                    status = "ENG 模式 · 用 ←/→ 移动小数点";
                    return;
                }
            }
        }
        if (formatConverted && key == CnCwKey.BACK) {
            restoreOriginalFormat();
            return;
        }
        switch (key) {
            case SETTINGS -> { openPopup(CnCwScreen.SETTINGS); return; }
            case CATALOG -> { openPopup(CnCwScreen.CATALOG); return; }
            case TOOLS -> { openPopup(CnCwScreen.TOOLS); return; }
            case VARIABLE -> { openPopup(CnCwScreen.VARIABLES); return; }
            case FUNCTION -> { openPopup(CnCwScreen.FUNCTIONS); return; }
            case FORMAT -> {
                // On the 991 keycap Ans is the SHIFT legend of the format
                // key.  The unshifted key opens the format overlay.
                if (shiftArmed) insertKey(CnCwKey.ANS);
                else openPopup(CnCwScreen.FORMAT);
                return;
            }
            case BACK -> {
                if (!tokens.isEmpty() || resultShown) clearExpression();
                else applicationLanding = true;
                return;
            }
            case AC -> {
                if (shiftArmed) powerOff();
                else clearExpression();
                return;
            }
            default -> { }
        }

        if (spreadsheetGrid && tokens.isEmpty()) {
            switch (key) {
                case LEFT -> { moveSpreadsheetCell(0, -1); return; }
                case RIGHT -> { moveSpreadsheetCell(0, 1); return; }
                case UP -> { moveSpreadsheetCell(-1, 0); return; }
                case DOWN -> { moveSpreadsheetCell(1, 0); return; }
                case DEL -> { clearSpreadsheetCell(); return; }
                default -> { }
            }
        }

        if (applicationLanding) {
            List<CnCwCommand> commands = modeCommands(application);
            switch (key) {
                case UP, LEFT -> selectedIndex = wrap(selectedIndex - 1, commands.size());
                case DOWN, RIGHT -> selectedIndex = wrap(selectedIndex + 1, commands.size());
                case PAGE_UP -> selectedIndex = wrap(selectedIndex - 6, commands.size());
                case PAGE_DOWN -> selectedIndex = wrap(selectedIndex + 6, commands.size());
                case OK, ENTER, EXE -> beginModeCommand(commands.get(selectedIndex));
                default -> {
                    if (isEntryKey(key)) {
                        applicationLanding = false;
                        selectedIndex = 0;
                        insertKey(key);
                    }
                }
            }
            return;
        }

        switch (key) {
            case LEFT -> {
                cursor = Math.max(0, cursor - 1);
                shiftArmed = false;
                resultShown = false;
            }
            case RIGHT -> {
                cursor = Math.min(tokens.size(), cursor + 1);
                shiftArmed = false;
                resultShown = false;
            }
            case UP -> recallHistory(-1);
            case DOWN -> recallHistory(1);
            case PAGE_UP -> recallHistory(-6);
            case PAGE_DOWN -> recallHistory(6);
            case DEL -> {
                if (shiftArmed) {
                    overwriteMode = !overwriteMode;
                    shiftArmed = false;
                    status = overwriteMode ? "覆盖输入" : "插入输入";
                } else {
                    deleteBeforeCursor();
                }
            }
            case OK, ENTER, EXE -> {
                boolean approximate = key == CnCwKey.EXE && shiftArmed;
                shiftArmed = false;
                evaluate(approximate);
            }
            // Keep the keyboard '=' key as a relation token.  In particular,
            // do not fold it into the EXE/OK/ENTER execution branch above.
            case EQUALS -> insertKey(CnCwKey.EQUALS);
            default -> insertKey(key);
        }
    }

    private void beginModeCommand(CnCwCommand command) {
        applicationLanding = false;
        selectedIndex = 0;
        clearExpression();
        applicationLanding = false;
        activeCommandId = command.id();
        status = workflowPrompt(application, command);
        if (application == ApplicationMode.SPREADSHEET && command.id().equals("sheet")) {
            spreadsheetGrid = true;
            spreadsheetRow = 0;
            spreadsheetColumn = 0;
            status = "A1 · 数据表格";
        }
        if (application == ApplicationMode.SPREADSHEET && command.id().equals("recalc")) {
            spreadsheet.recalculate();
            result = "重新计算完成\n剩余 " + spreadsheet.remainingBytes() + " bytes";
            resultShown = true;
        }
    }

    private void moveSpreadsheetCell(int rowDelta, int columnDelta) {
        spreadsheetRow = Math.max(0, Math.min(SpreadsheetModel.ROWS - 1,
                spreadsheetRow + rowDelta));
        spreadsheetColumn = Math.max(0, Math.min(SpreadsheetModel.COLUMNS - 1,
                spreadsheetColumn + columnDelta));
        status = spreadsheetAddress() + " · 数据表格";
    }

    private void clearSpreadsheetCell() {
        try {
            spreadsheet.clear(spreadsheetAddress());
            result = "";
            resultShown = false;
            status = spreadsheetAddress() + " 已清除";
        } catch (RuntimeException error) {
            result = "Math ERROR";
            resultShown = true;
        }
    }

    private String spreadsheetAddress() {
        return (char) ('A' + spreadsheetColumn) + Integer.toString(spreadsheetRow + 1);
    }

    private void insertKey(CnCwKey key) {
        Token token = shiftArmed && key == CnCwKey.PI && application == ApplicationMode.COMPLEX
                ? token("i") : tokenFor(key, shiftArmed);
        shiftArmed = false;
        if (token == null) return;
        resetStatementSequence();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";

        rememberUndo();
        if (prepareContinuousVerification(token)) return;
        if (resultShown) {
            if (token.binary && hasAns) {
                tokens.clear();
                tokens.add(new Token("Ans", "Ans", false));
                cursor = 1;
            } else {
                tokens.clear();
                cursor = 0;
            }
        }
        if (token.binary && cursor == 0 && hasAns) {
            tokens.add(new Token("Ans", "Ans", false));
            cursor = 1;
        }
        if (token.binary && cursor > 0 && tokens.get(cursor - 1).binary) {
            tokens.set(cursor - 1, token);
        } else if (overwriteMode && cursor < tokens.size() && !token.binary) {
            tokens.set(cursor, token);
            cursor++;
        } else {
            tokens.add(cursor, token);
            cursor++;
        }
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        originalResult = "";
        status = applicationStatus();
    }

    private static boolean isRelationToken(Token token) {
        return token.evaluation.equals("=") || token.evaluation.equals("!=")
                || token.evaluation.equals("<") || token.evaluation.equals("<=")
                || token.evaluation.equals(">") || token.evaluation.equals(">=");
    }

    private boolean prepareContinuousVerification(Token token) {
        if (!resultShown || !verificationMode || application != ApplicationMode.CALCULATE
                || !isRelationToken(token)) return false;
        int relation = -1;
        for (int index = 0; index < tokens.size(); index++) {
            if (isRelationToken(tokens.get(index))) relation = index;
        }
        List<Token> rightSide = relation >= 0 && relation + 1 < tokens.size()
                ? new ArrayList<>(tokens.subList(relation + 1, tokens.size()))
                : new ArrayList<>();
        tokens.clear();
        tokens.addAll(rightSide);
        tokens.add(token);
        cursor = tokens.size();
        result = "";
        resultShown = false;
        lastExactResult = null;
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        status = "连续验证";
        return true;
    }

    private void deleteBeforeCursor() {
        shiftArmed = false;
        if (cursor <= 0 || tokens.isEmpty()) return;
        resetStatementSequence();
        rememberUndo();
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        tokens.remove(cursor - 1);
        cursor--;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
    }

    private void rememberUndo() {
        undoTokens = com.codex.fx991.core.Compat.copyList(tokens);
        undoCursor = cursor;
    }

    private void undo() {
        if (undoTokens == null) {
            status = "没有可撤消的操作";
            return;
        }
        resetStatementSequence();
        List<Token> current = com.codex.fx991.core.Compat.copyList(tokens);
        int currentCursor = cursor;
        tokens.clear();
        tokens.addAll(undoTokens);
        cursor = Math.min(undoCursor, tokens.size());
        undoTokens = current;
        undoCursor = currentCursor;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        status = "撤消";
        closeAllPopups();
    }

    private void evaluate(boolean forceDecimal) {
        if (tokens.isEmpty()) return;
        String source = autoCloseParentheses(evaluationSource());
        String plainSource = source.replace("\u2063", "");
        errorShown = false;
        lastError = null;
        try {
            if (!pendingFunctionDefinition.isEmpty()) {
                ScalarExpressionEngine.CompiledExpression definition =
                        ScalarExpressionEngine.compile(source);
                if (pendingFunctionDefinition.equals("f")) {
                    functionF = definition;
                    functionFSource = source;
                } else {
                    functionG = definition;
                    functionGSource = source;
                }
                result = pendingFunctionDefinition + "(x)=" + displayExpressionWithoutCursor();
                status = "已定义 " + pendingFunctionDefinition + "(x)";
                pendingFunctionDefinition = "";
                resultShown = true;
                return;
            }
            String formatted;
            double scalar;
            ExactValue exactScalar = null;
            String completionStatus = "";
            boolean storeAnswer = true;
            if (verificationMode && application == ApplicationMode.CALCULATE) {
                boolean valid = ScalarExpressionEngine.verify(source, evaluationContext());
                formatted = valid ? "True" : "False";
                scalar = valid ? 1.0 : 0.0;
                exactScalar = ExactValue.integer(valid ? 1 : 0);
            } else if (application == ApplicationMode.CALCULATE
                    && hasStatementOperator(source)) {
                String statement = source;
                if (source.indexOf(':') >= 0) {
                    if (!source.equals(statementSequenceSource) || statementSequence.isEmpty()
                            || statementSequenceIndex >= statementSequence.size()) {
                        statementSequenceSource = source;
                        statementSequence = StatementEngine.splitStatements(source);
                        statementSequenceIndex = 0;
                    }
                    statement = statementSequence.get(statementSequenceIndex++);
                }
                StatementEngine.Outcome outcome = StatementEngine.evaluate(statement, variables,
                        settings.angleUnit(), hasAns ? ans : 0.0, random);
                variables.clear();
                variables.putAll(outcome.variables());
                exactVariables.clear();
                scalar = outcome.ans();
                exactScalar = simpleExact(scalar);
                formatted = formatResult(scalar, exactScalar, statement, false);
            } else if (application == ApplicationMode.SPREADSHEET && !com.codex.fx991.core.Compat.isBlank(activeCommandId)) {
                CnCwModeEngine.ModeResult modeResult = spreadsheetWorkflow(plainSource);
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
            } else if (!com.codex.fx991.core.Compat.isBlank(activeCommandId) && isStructuredWorkflow(application)) {
                CnCwModeEngine.ModeResult modeResult = CnCwModeEngine.evaluate(
                        application, activeCommandId, plainSource, evaluationContext());
                formatted = modeResult.display();
                scalar = modeResult.primaryValue() == null
                        ? (hasAns ? ans : 0.0) : modeResult.primaryValue();
            } else if (application == ApplicationMode.BASE_N && !com.codex.fx991.core.Compat.isBlank(activeCommandId)) {
                BaseNEngine.Base base = switch (activeCommandId) {
                    case "hex" -> BaseNEngine.Base.HEXADECIMAL;
                    case "binary" -> BaseNEngine.Base.BINARY;
                    case "octal" -> BaseNEngine.Base.OCTAL;
                    default -> BaseNEngine.Base.DECIMAL;
                };
                int value = BaseNEngine.parse(plainSource, base);
                formatted = BaseNEngine.format(value, base);
                scalar = value;
            } else if (application == ApplicationMode.COMPLEX) {
                Map<String, ComplexValue> complexVariables = new HashMap<>();
                for (Map.Entry<String, Double> entry : variables.entrySet()) {
                    complexVariables.put(entry.getKey(), new ComplexValue(entry.getValue(), 0.0));
                }
                ComplexValue value = ComplexExpressionEngine.evaluate(plainSource, complexVariables,
                        hasComplexAns ? complexAns
                                : hasAns ? new ComplexValue(ans, 0.0) : ComplexValue.ZERO,
                        settings.angleUnit());
                formatted = formatComplex(value);
                scalar = value.real();
                complexAns = value;
                hasComplexAns = true;
            } else {
                SimplificationResult simplification = simplificationCall(plainSource);
                CoordinateCall coordinate = coordinateCall(plainSource);
                int remainderIndex = standaloneRemainderIndex(source);
                if (simplification != null) {
                    scalar = simplification.value();
                    exactScalar = ExactValue.rational(new Rational(
                            java.math.BigInteger.valueOf(simplification.originalNumerator()),
                            java.math.BigInteger.valueOf(simplification.originalDenominator())));
                    formatted = simplification.displayNumerator() + "/"
                            + simplification.displayDenominator();
                    completionStatus = simplification.canContinue()
                            ? "还可继续化简" : "已化简";
                } else if (coordinate != null) {
                    double first = ScalarExpressionEngine.evaluate(coordinate.first(),
                            evaluationContext());
                    double second = ScalarExpressionEngine.evaluate(coordinate.second(),
                            evaluationContext());
                    ManualFunctions.CoordinatePair pair = coordinate.polar()
                            ? ManualFunctions.polar(first, second, settings.angleUnit())
                            : ManualFunctions.rectangular(first, second, settings.angleUnit());
                    variables.put("x", pair.first());
                    variables.put("y", pair.second());
                    ExactValue firstExact = simpleExact(pair.first());
                    ExactValue secondExact = simpleExact(pair.second());
                    if (firstExact == null) exactVariables.remove("x");
                    else exactVariables.put("x", firstExact);
                    if (secondExact == null) exactVariables.remove("y");
                    else exactVariables.put("y", secondExact);
                    scalar = pair.first();
                    exactScalar = firstExact;
                    formatted = coordinate.polar()
                            ? "r=" + formatResult(pair.first(), firstExact, "", true)
                            + "\nθ=" + formatResult(pair.second(), secondExact, "", true)
                            : "x=" + formatResult(pair.first(), firstExact, "", true)
                            + "\ny=" + formatResult(pair.second(), secondExact, "", true);
                } else if (remainderIndex >= 0) {
                    double dividend = ScalarExpressionEngine.evaluate(
                            source.substring(0, remainderIndex), evaluationContext());
                    double divisor = ScalarExpressionEngine.evaluate(
                            source.substring(remainderIndex + 2), evaluationContext());
                    ManualFunctions.RemainderResult division =
                            ManualFunctions.divideWithRemainder(dividend, divisor);
                    scalar = division.ansValue();
                    exactScalar = scalar == Math.rint(scalar) && Math.abs(scalar) <= Long.MAX_VALUE
                            ? ExactValue.integer((long) scalar) : null;
                    formatted = division.remainderMode()
                            ? "商=" + formatNumber(division.quotient())
                            + "\n余数=" + formatNumber(division.remainder())
                            : formatNumber(division.quotient());
                } else {
                    ScalarExpressionEngine.EvaluationResult evaluation =
                            ScalarExpressionEngine.evaluateDetailed(source, evaluationContext());
                    scalar = evaluation.value();
                    exactScalar = evaluation.exactValue();
                    formatted = forceDecimal ? formatNumber(scalar)
                            : formatResult(scalar, exactScalar, source, false);
                }
                if (application == ApplicationMode.BASE_N) {
                    if (!Double.isFinite(scalar) || scalar != Math.rint(scalar)
                            || scalar < Integer.MIN_VALUE || scalar > Integer.MAX_VALUE) {
                        throw new ArithmeticException("32-bit integer required");
                    }
                    formatted = BaseNEngine.format((int) scalar, BaseNEngine.Base.DECIMAL);
                }
            }
            if (storeAnswer && application != ApplicationMode.INEQUALITY) {
                ans = scalar;
                hasAns = true;
                exactAns = exactScalar;
            }
            lastExactResult = exactScalar;
        result = formatted;
        resultShown = true;
        originalResult = formatted;
        formatConverted = false;
        engineeringMode = false;
            status = !completionStatus.isEmpty() ? completionStatus
                    : statementSequence.isEmpty() ? applicationStatus()
                    : "语句 " + Math.min(statementSequenceIndex, statementSequence.size())
                    + "/" + statementSequence.size();
            history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result));
            if (history.size() > 100) history.remove(0);
            historyIndex = history.size();
            if (spreadsheetGrid && application == ApplicationMode.SPREADSHEET
                    && activeCommandId.equals("sheet")) {
                // EXE commits the cell and returns the editor focus to the
                // grid, matching a physical spreadsheet's next-key behavior.
                tokens.clear();
                cursor = 0;
            }
        } catch (CalculationException error) {
            showError(error.error(), error.position());
        } catch (ArithmeticException error) {
            showError(CalculationError.MATH, 0);
        } catch (IllegalArgumentException error) {
            showError(CalculationError.ARGUMENT, 0);
        } catch (RuntimeException error) {
            showError(CalculationError.SYNTAX, 0);
        }
    }

    private CoordinateCall coordinateCall(String source) {
        String trimmed = source.trim();
        boolean polar = trimmed.regionMatches(true, 0, "pol(", 0, 4);
        boolean rectangular = trimmed.regionMatches(true, 0, "rec(", 0, 4);
        if ((!polar && !rectangular) || !trimmed.endsWith(")")) return null;
        List<String> arguments = CnCwModeEngine.splitTopLevel(
                trimmed.substring(4, trimmed.length() - 1));
        if (arguments.size() != 2) return null;
        return new CoordinateCall(polar, arguments.get(0), arguments.get(1));
    }

    private SimplificationResult simplificationCall(String source) {
        String trimmed = source.trim();
        if (!trimmed.regionMatches(true, 0, "simp(", 0, 5)
                || !trimmed.endsWith(")")) return null;
        if (!manualSimplification) {
            throw new CalculationException(CalculationError.ARGUMENT,
                    "Simp requires manual simplification mode", 0);
        }
        List<String> arguments = CnCwModeEngine.splitTopLevel(
                trimmed.substring(5, trimmed.length() - 1));
        if (arguments.isEmpty() || arguments.size() > 2) {
            throw new CalculationException(CalculationError.ARGUMENT,
                    "Simp argument count", 0);
        }
        int slash = topLevelSlash(arguments.get(0));
        if (slash <= 0 || slash + 1 >= arguments.get(0).length()) {
            throw new CalculationException(CalculationError.CANNOT_SIMPLIFY,
                    "A fraction is required", 0);
        }
        long numerator = exactLong(ScalarExpressionEngine.evaluate(
                arguments.get(0).substring(0, slash), evaluationContext()));
        long denominator = exactLong(ScalarExpressionEngine.evaluate(
                arguments.get(0).substring(slash + 1), evaluationContext()));
        if (denominator == 0L) {
            throw new CalculationException(CalculationError.MATH,
                    "Zero denominator", slash + 1);
        }
        if (denominator < 0L) {
            numerator = -numerator;
            denominator = -denominator;
        }
        long gcd = gcd(Math.abs(numerator), denominator);
        if (gcd <= 1L) {
            throw new CalculationException(CalculationError.CANNOT_SIMPLIFY,
                    "Fraction is already irreducible", 0);
        }
        long factor = arguments.size() == 2
                ? exactLong(ScalarExpressionEngine.evaluate(arguments.get(1), evaluationContext()))
                : smallestPrimeFactor(gcd);
        if (factor <= 1L || gcd % factor != 0L) {
            throw new CalculationException(CalculationError.CANNOT_SIMPLIFY,
                    "Factor does not divide numerator and denominator", 0);
        }
        long displayNumerator = numerator / factor;
        long displayDenominator = denominator / factor;
        boolean canContinue = gcd(Math.abs(displayNumerator), displayDenominator) > 1L;
        return new SimplificationResult(numerator, denominator,
                displayNumerator, displayDenominator, canContinue,
                (double) numerator / denominator);
    }

    private static int topLevelSlash(String source) {
        int depth = 0;
        int found = -1;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (depth == 0 && (value == '/' || value == '÷')) {
                if (found >= 0) return -1;
                found = index;
            }
        }
        return depth == 0 ? found : -1;
    }

    private static long exactLong(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw new CalculationException(CalculationError.ARGUMENT,
                    "Integer required", 0);
        }
        return (long) value;
    }

    private static long gcd(long left, long right) {
        long a = left;
        long b = right;
        while (b != 0L) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return Math.abs(a);
    }

    private static long smallestPrimeFactor(long value) {
        if ((value & 1L) == 0L) return 2L;
        for (long factor = 3L; factor <= value / factor; factor += 2L) {
            if (value % factor == 0L) return factor;
        }
        return value;
    }

    private static ExactValue simpleExact(double value) {
        if (!Double.isFinite(value)) return null;
        long nearest = Math.round(value);
        if (Math.abs(value - nearest) <= 1e-11 * Math.max(1.0, Math.abs(value))) {
            return ExactValue.integer(nearest);
        }
        Rational rational = Rational.approximate(value, 10_000L, 1e-12);
        return Math.abs(rational.toDouble() - value) <= 1e-12 ? ExactValue.rational(rational) : null;
    }

    private void showError(CalculationError error, int sourcePosition) {
        lastError = error;
        errorShown = true;
        errorCursor = tokenCursorForSourcePosition(sourcePosition);
        cursor = errorCursor;
        result = error.display();
        resultShown = true;
        status = "按 OK、返回或 AC 回到错误位置";
    }

    private int tokenCursorForSourcePosition(int sourcePosition) {
        int position = Math.max(0, sourcePosition);
        int offset = 0;
        for (int index = 0; index < tokens.size(); index++) {
            if (index > 0 && needsLexicalBoundary(tokens.get(index - 1), tokens.get(index))) {
                offset++;
            }
            int next = offset + tokens.get(index).evaluation.length();
            if (position < next) return index;
            offset = next;
        }
        return tokens.size();
    }

    /**
     * EXE on the physical CW accepts an unfinished right-parenthesis suffix
     * when the only issue is an unmatched opening parenthesis.  Preserve the
     * visible input and repair only that unambiguous structural omission;
     * unmatched closing parentheses and incomplete operators still report a
     * syntax error.
     */
    private static String autoCloseParentheses(String source) {
        int depth = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')' && --depth < 0) return source;
        }
        if (depth <= 0) return source;
        StringBuilder completed = new StringBuilder(source.length() + depth);
        completed.append(source);
        for (int index = 0; index < depth; index++) completed.append(')');
        return completed.toString();
    }

    private static boolean hasStatementOperator(String source) {
        return source.indexOf(':') >= 0 || source.indexOf('→') >= 0
                || source.contains("->");
    }

    private String formatStatementResults(List<Double> values) {
        StringBuilder text = new StringBuilder();
        int start = Math.max(0, values.size() - 2);
        for (int index = start; index < values.size(); index++) {
            if (text.length() > 0) text.append('\n');
            text.append(formatNumber(values.get(index)));
        }
        return text.toString();
    }

    /** Returns the ÷R position only when it is the expression's outer operation. */
    private static int standaloneRemainderIndex(String source) {
        int depth = 0;
        int operator = -1;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (depth == 0 && value == '÷' && index + 1 < source.length()
                    && (source.charAt(index + 1) == 'R' || source.charAt(index + 1) == 'r')) {
                if (operator >= 0) return -1;
                operator = index++;
            }
            if (depth < 0) return -1;
        }
        if (depth != 0 || operator <= 0 || operator + 2 >= source.length()) return -1;
        depth = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (depth == 0 && (value == '+' || value == '-' || value == '−')) {
                boolean unary = index == 0 || index == operator + 2;
                boolean exponentSign = index > 0
                        && (source.charAt(index - 1) == 'E' || source.charAt(index - 1) == 'e');
                if (!unary && !exponentSign) return -1;
            }
        }
        return operator;
    }

    private ScalarExpressionEngine.EvaluationContext evaluationContext() {
        ScalarExpressionEngine.RoundingKind roundingKind = switch (settings.displayMode()) {
            case FIX -> ScalarExpressionEngine.RoundingKind.FIX;
            case SCI -> ScalarExpressionEngine.RoundingKind.SCI;
            case NORM_1, NORM_2 -> ScalarExpressionEngine.RoundingKind.NORM;
        };
        int digits = roundingKind == ScalarExpressionEngine.RoundingKind.NORM
                ? 10 : settings.displayDigits();
        return new ScalarExpressionEngine.EvaluationContext(variables, exactVariables,
                settings.angleUnit(), hasAns ? ans : 0.0, exactAns, functionF, functionG,
                random, new ScalarExpressionEngine.RoundingPolicy(roundingKind, digits));
    }

    private String displayExpressionWithoutCursor() {
        StringBuilder text = new StringBuilder(tokens.size() * 2);
        for (Token token : tokens) text.append(token.display);
        return text.toString();
    }

    private void recallHistory(int delta) {
        if (history.isEmpty()) return;
        historyIndex = Math.max(0, Math.min(history.size() - 1, historyIndex + delta));
        HistoryEntry entry = history.get(historyIndex);
        rememberUndo();
        tokens.clear();
        tokens.addAll(entry.tokens);
        cursor = tokens.size();
        result = entry.result;
        resultShown = true;
        status = "历史 " + (historyIndex + 1) + "/" + history.size();
    }

    private void clearExpression() {
        rememberUndo();
        resetStatementSequence();
        tokens.clear();
        cursor = 0;
        shiftArmed = false;
        result = "";
        resultShown = false;
        errorShown = false;
        lastError = null;
        lastExactResult = null;
        formatConverted = false;
        engineeringMode = false;
        originalResult = "";
        selectedIndex = 0;
        status = applicationStatus();
    }

    private void dismissError() {
        errorShown = false;
        lastError = null;
        result = "";
        resultShown = false;
        cursor = Math.max(0, Math.min(tokens.size(), errorCursor));
        status = applicationStatus();
    }

    private void resetStatementSequence() {
        statementSequenceSource = "";
        statementSequence = com.codex.fx991.core.Compat.list();
        statementSequenceIndex = 0;
    }

    private void showHome() {
        navigation.clear();
        screen = CnCwScreen.HOME;
        application = null;
        activeCommandId = "";
        selectedIndex = 0;
        shiftArmed = false;
        applicationLanding = false;
        spreadsheetGrid = false;
        history.clear();
        historyIndex = 0;
        resetStatementSequence();
        status = "HOME";
    }

    private void powerOff() {
        navigation.clear();
        screen = CnCwScreen.HOME;
        application = null;
        activeCommandId = "";
        selectedIndex = 0;
        shiftArmed = false;
        poweredOn = false;
        overwriteMode = false;
        tokens.clear();
        cursor = 0;
        result = "";
        resultShown = false;
        errorShown = false;
        history.clear();
        historyIndex = 0;
        resetStatementSequence();
        clearFunctionDefinitions();
        status = "OFF";
    }

    private void openApplication(ApplicationMode mode) {
        application = mode;
        screen = CnCwScreen.forApplication(mode);
        selectedIndex = 0;
        navigation.clear();
        shiftArmed = false;
        result = "";
        resultShown = false;
        tokens.clear();
        cursor = 0;
        activeCommandId = "";
        spreadsheetGrid = false;
        applicationLanding = mode != ApplicationMode.CALCULATE
                && mode != ApplicationMode.COMPLEX;
        status = mode.chineseName();
    }

    private void openPopup(CnCwScreen target) {
        if (screen == target) return;
        navigation.push(new Navigation(screen, selectedIndex));
        screen = target;
        selectedIndex = 0;
        shiftArmed = false;
        status = titleFor(target);
    }

    /** Switches menu families without leaving the previous menu on BACK's stack. */
    private void switchPopup(CnCwScreen target) {
        if (screen == target) return;
        Navigation base = navigation.peekLast();
        navigation.clear();
        if (base != null) navigation.push(base);
        else navigation.push(new Navigation(application == null
                ? CnCwScreen.HOME : CnCwScreen.forApplication(application), 0));
        screen = target;
        selectedIndex = 0;
        shiftArmed = false;
        status = titleFor(target);
    }

    private void back() {
        if (navigation.isEmpty()) {
            closeAllPopups();
            return;
        }
        Navigation previous = navigation.pop();
        screen = previous.screen;
        selectedIndex = previous.selectedIndex;
        status = titleFor(screen);
    }

    private void closeAllPopups() {
        navigation.clear();
        screen = application == null ? CnCwScreen.HOME : CnCwScreen.forApplication(application);
        selectedIndex = 0;
        shiftArmed = false;
        status = application == null ? "HOME" : applicationStatus();
    }

    private void activateMenuItem() {
        List<CnCwCommand> items = currentMenuItems();
        if (items.isEmpty()) return;
        int index = Math.min(selectedIndex, items.size() - 1);
        switch (screen) {
            case SETTINGS -> activateSettingsRoot(index);
            case SETTINGS_INPUT_OUTPUT -> activateCalculationSetting(index);
            case SETTINGS_INPUT_OUTPUT_OPTIONS -> activateInputOutputOption(index);
            case SETTINGS_ANGLE_OPTIONS -> activateAngleOption(index);
            case SETTINGS_FORMAT -> activateDisplayFormat(index);
            case SETTINGS_FIX_DIGITS -> activateDisplayDigits(false, index);
            case SETTINGS_SCI_DIGITS -> activateDisplayDigits(true, index);
            case SETTINGS_DISPLAY -> activateSystemSetting(index);
            case RESET_CONFIRM -> activateResetConfirm(index);
            case CATALOG -> {
                openPopup(switch (index) {
                    case 0 -> CnCwScreen.CATALOG_FUNCTIONS;
                    case 1 -> CnCwScreen.CATALOG_PROBABILITY;
                    case 2 -> CnCwScreen.CATALOG_NUMERIC;
                    case 3 -> CnCwScreen.CATALOG_ANGLE;
                    case 4 -> CnCwScreen.CATALOG_TRIG;
                    case 5 -> CnCwScreen.CATALOG_ENGINEERING;
                    case 6 -> CnCwScreen.CATALOG_CONSTANTS;
                    case 7 -> CnCwScreen.CATALOG_CONVERSIONS;
                    default -> CnCwScreen.CATALOG_RELATIONS;
                });
            }
            case CATALOG_FUNCTIONS -> {
                if (index == 4 && !manualSimplification) {
                    status = "先在工具中将化简设为手动";
                } else {
                    insertFromMenu(functionToken(index));
                }
            }
            case CATALOG_NUMERIC -> insertFromMenu(numericToken(index));
            case CATALOG_ANGLE -> insertFromMenu(angleToken(index));
            case CATALOG_TRIG -> insertFromMenu(trigToken(index));
            case CATALOG_ENGINEERING -> insertFromMenu(engineeringToken(index));
            case CATALOG_CONSTANTS -> {
                catalogCategoryIndex = index;
                openPopup(CnCwScreen.CATALOG_CONSTANT_ITEMS);
            }
            case CATALOG_CONSTANT_ITEMS -> insertFromMenu(constantToken(index));
            case CATALOG_CONVERSIONS -> {
                conversionCategoryIndex = index;
                openPopup(CnCwScreen.CATALOG_CONVERSION_ITEMS);
            }
            case CATALOG_CONVERSION_ITEMS -> insertFromMenu(conversionToken(index));
            case CATALOG_PROBABILITY -> insertFromMenu(probabilityToken(index));
            case CATALOG_RELATIONS -> insertFromMenu(relationToken(index));
            case TOOLS -> activateTool(index);
            case TOOLS_CONVERSION -> insertFromMenu(conversionToken(index));
            case VARIABLES -> insertFromMenu(variableToken(index));
            case FUNCTIONS -> activateFunctionItem(index);
            case FORMAT -> activateFormat(index);
            default -> back();
        }
    }

    private void activateSettingsRoot(int index) {
        if (index == 0) openPopup(CnCwScreen.SETTINGS_INPUT_OUTPUT);
        else if (index == 1) openPopup(CnCwScreen.SETTINGS_DISPLAY);
        else openPopup(CnCwScreen.RESET_CONFIRM);
    }

    private void activateResetConfirm(int index) {
        if (index != 0) {
            back();
            return;
        }
        settings = CnCwSettings.defaults();
        for (String name : VARIABLE_NAMES) {
            variables.put(name, 0.0);
            exactVariables.put(name, ExactValue.ZERO);
        }
        ans = 0.0;
        hasAns = false;
        exactAns = null;
        clearFunctionDefinitions();
        history.clear();
        historyIndex = 0;
        resetStatementSequence();
        spreadsheet.clearAll();
        tokens.clear();
        cursor = 0;
        result = "";
        resultShown = false;
        navigation.clear();
        screen = CnCwScreen.HOME;
        application = null;
        selectedIndex = 0;
        status = "已复位设置和数据";
    }

    private void activateCalculationSetting(int index) {
        if (index == 2) {
            openPopup(CnCwScreen.SETTINGS_FORMAT);
            return;
        }
        if (index == 0) {
            openPopup(CnCwScreen.SETTINGS_INPUT_OUTPUT_OPTIONS);
            return;
        }
        if (index == 1) {
            openPopup(CnCwScreen.SETTINGS_ANGLE_OPTIONS);
            return;
        }
        settings = switch (index) {
            case 3 -> settings.toggleEngineeringSymbols();
            case 4 -> settings.cycleFractionMode();
            case 5 -> settings.cycleComplexMode();
            case 6 -> settings.cycleDecimalMark();
            default -> settings.toggleDigitSeparator();
        };
        status = "已更新 · " + settingValue(index);
    }

    private void activateInputOutputOption(int index) {
        CnCwSettings.InputOutput previous = settings.inputOutput();
        CnCwSettings.InputOutput[] values = CnCwSettings.InputOutput.values();
        settings = settings.withInputOutput(values[Math.max(0, Math.min(index, values.length - 1))]);
        if (settings.inputOutput() != previous) {
            history.clear();
            historyIndex = 0;
            resetStatementSequence();
            boolean previousMath = previous == CnCwSettings.InputOutput.MATH_MATH
                    || previous == CnCwSettings.InputOutput.MATH_DECIMAL;
            boolean currentMath = settings.inputOutput() == CnCwSettings.InputOutput.MATH_MATH
                    || settings.inputOutput() == CnCwSettings.InputOutput.MATH_DECIMAL;
            if (previousMath != currentMath) clearFunctionDefinitions();
        }
        back();
        status = "输入/输出 · " + settings.inputOutput().name();
    }

    private void activateAngleOption(int index) {
        AngleUnit[] values = AngleUnit.values();
        settings = settings.withAngleUnit(values[Math.max(0, Math.min(index, values.length - 1))]);
        back();
        status = "角度单位 · " + settings.angleUnit().name();
    }

    private void clearFunctionDefinitions() {
        functionFSource = "";
        functionGSource = "";
        pendingFunctionDefinition = "";
        functionF = null;
        functionG = null;
    }

    private void activateDisplayFormat(int index) {
        int digits = settings.displayDigits();
        if (index == 2) {
            openPopup(CnCwScreen.SETTINGS_FIX_DIGITS);
            return;
        }
        if (index == 3) {
            openPopup(CnCwScreen.SETTINGS_SCI_DIGITS);
            return;
        }
        settings = settings.withDisplay(index == 0
                ? CnCwSettings.DisplayMode.NORM_1 : CnCwSettings.DisplayMode.NORM_2, digits);
        status = "显示格式 · " + settingValue(2);
        back();
    }

    private void activateDisplayDigits(boolean scientific, int index) {
        int digits = scientific ? index + 1 : index;
        settings = settings.withDisplay(scientific ? CnCwSettings.DisplayMode.SCI
                : CnCwSettings.DisplayMode.FIX, digits);
        back();
        status = "显示格式 · " + settingValue(2);
    }

    private void activateSystemSetting(int index) {
        if (index == 3) {
            openPopup(CnCwScreen.CALCULATOR_ID);
            return;
        }
        if (index == 2) settings = settings.cycleMultiLineFont();
        status = switch (index) {
            case 0 -> "对比度由手机显示系统管理";
            case 1 -> "语言 · 中文";
            case 2 -> "多行字体 · " + settings.multiLineFont();
            default -> model.displayName() + " · clean-room core";
        };
    }

    private void activateTool(int index) {
        switch (index) {
            case 0 -> undo();
            case 1 -> {
                manualSimplification = !manualSimplification;
                closeAllPopups();
                status = "化简 · " + (manualSimplification ? "手动" : "自动");
            }
            case 2 -> {
                verificationMode = !verificationMode;
                history.clear();
                historyIndex = 0;
                resetStatementSequence();
                closeAllPopups();
                status = verificationMode
                        ? "运算验证开 · 输入关系式后按 EXE" : "运算验证关";
            }
            default -> { }
        }
    }

    private void activateFunctionItem(int index) {
        if (index >= 2) {
            closeAllPopups();
            clearExpression();
            pendingFunctionDefinition = index == 2 ? "f" : "g";
            status = "定义 " + pendingFunctionDefinition + "(x)";
            return;
        }
        insertFromMenu(index == 0
                ? new Token("f(", "f(", false)
                : new Token("g(", "g(", false));
    }

    private void activateFormat(int index) {
        if (!hasAns) {
            status = "尚无可转换的结果";
            return;
        }
        List<CnCwCommand> commands = currentMenuItems();
        if (commands.isEmpty()) return;
        String id = commands.get(Math.max(0, Math.min(index, commands.size() - 1))).id();
        if (!formatConverted) originalResult = result;
        Rational fraction = exactAns == null ? null : exactAns.rational();
        if (fraction == null) fraction = Rational.approximate(ans, 1_000_000L, 1e-12);
        result = switch (id) {
            case "standard" -> formatResult(ans, exactAns, expression(), true);
            case "decimal" -> formatNumber(ans);
            case "factor" -> primeFactors(ans);
            case "improper" -> fraction.improperString();
            case "mixed" -> fraction.mixedString();
            case "dms" -> ManualFunctions.fromDecimalDegrees(ans).display();
            case "rectangular" -> formatComplex(complexAns, false);
            case "polar" -> formatComplex(complexAns, true);
            case "engineering" -> {
                engineeringExponent = ans == 0.0 ? 0
                        : (int) Math.floor(Math.log10(Math.abs(ans)) / 3.0) * 3;
                engineeringMode = true;
                yield engineeringAtExponent(ans, engineeringExponent);
            }
            default -> formatNumber(ans);
        };
        formatConverted = true;
        resultShown = true;
        status = engineeringMode ? "ENG 模式 · 用 ←/→ 移动小数点" : "格式转换";
        closeAllPopups();
    }

    private void restoreOriginalFormat() {
        engineeringMode = false;
        formatConverted = false;
        if (!originalResult.isEmpty()) result = originalResult;
        resultShown = !result.isEmpty();
        status = applicationStatus();
    }

    private void refreshEngineeringResult() {
        engineeringExponent = Math.max(-99, Math.min(99, engineeringExponent));
        engineeringExponent -= Math.floorMod(engineeringExponent, 3);
        result = engineeringAtExponent(ans, engineeringExponent);
        resultShown = true;
        status = "ENG · 10^" + engineeringExponent;
    }

    private String engineeringAtExponent(double value, int exponent) {
        double mantissa = value / Math.pow(10.0, exponent);
        String text = trimDouble(mantissa);
        if (settings.digitSeparator()) text = separateDigits(text);
        if (settings.decimalMark() == CnCwSettings.DecimalMark.COMMA) {
            text = text.replace('.', ',');
        }
        return text + "×10^" + exponent;
    }

    private void insertFromMenu(Token token) {
        if (token == null) return;
        closeAllPopups();
        applicationLanding = false;
        rememberUndo();
        if (prepareContinuousVerification(token)) return;
        tokens.add(cursor, token);
        cursor++;
        result = "";
        resultShown = false;
    }

    private Token tokenFor(CnCwKey key, boolean shifted) {
        if (shifted) {
            return switch (key) {
                case SIN -> token("sin⁻¹(", "asin(");
                case COS -> token("cos⁻¹(", "acos(");
                case TAN -> token("tan⁻¹(", "atan(");
                case LOG -> token("ln(", "ln(");
                case LN -> token("e^(", "e^(");
                case SQRT -> token("³√(", "root(3,");
                case POWER -> token("⁻¹", "^(-1)");
                case SQUARE -> token("log(", "log(");
                case FRACTION -> token("a b/c(", "mixed(");
                case FACTORIAL -> token("nPr", "nPr", true);
                case PERCENT -> token("nCr", "nCr", true);
                case NPR -> token("nCr", "nCr", true);
                case ADD -> token("nPr", "nPr", true);
                case SUBTRACT -> token("nCr", "nCr", true);
                case ANS -> token("Ans");
                // On the physical CW layout π is the SHIFT function of 7.
                // Keep PI as a compatibility input, but do not make it the
                // source of the SHIFT+7 mapping (the two key intents are
                // intentionally independent).
                case DIGIT_7 -> token("π", "pi");
                case DIGIT_8 -> token("∠", "∠");
                case DIGIT_9 -> token("i", "i");
                case DIGIT_0 -> token("x");
                case DIGIT_1 -> token("D");
                case DIGIT_2 -> token("E");
                case DIGIT_3 -> token("F");
                case DIGIT_4 -> token("A");
                case DIGIT_5 -> token("B");
                case DIGIT_6 -> token("C");
                case DOT -> token("y");
                case EXP -> token("z");
                case NEGATE -> token("e", "e");
                case OPEN_PAREN, OPEN -> token("=", "=", true);
                case CLOSE_PAREN, CLOSE -> token(",", ",");
                case VAR_X -> token("DMS(", "dms(");
                case MULTIPLY -> token("∫(", "integral(");
                case DIVIDE -> token("d/dx(", "diff(");
                case PI -> token("π", "pi");
                case E -> token("e", "e");
                default -> null;
            };
        }
        return switch (key) {
            case DIGIT_0 -> token("0"); case DIGIT_1 -> token("1");
            case DIGIT_2 -> token("2"); case DIGIT_3 -> token("3");
            case DIGIT_4 -> token("4"); case DIGIT_5 -> token("5");
            case DIGIT_6 -> token("6"); case DIGIT_7 -> token("7");
            case DIGIT_8 -> token("8"); case DIGIT_9 -> token("9");
            case DOT -> token("."); case COMMA -> token(",");
            case ADD, PLUS -> token("+", "+", true);
            case SUBTRACT, MINUS -> token("−", "-", true);
            case MULTIPLY -> token("×", "*", true);
            case DIVIDE -> token("÷", "/", true);
            case FRACTION -> token("a/b", "/", true);
            case POWER -> token("^", "^", true);
            case OPEN_PAREN, OPEN -> token("(");
            case CLOSE_PAREN, CLOSE -> token(")");
            case SIN -> token("sin("); case COS -> token("cos(");
            case TAN -> token("tan("); case LOG -> token("log(");
            case LN -> token("ln("); case EXP -> token("×10^(", "*10^(");
            case SQRT -> token("√(", "sqrt(");
            case ROOT -> token("√[ ](", "root(");
            case ABS -> token("Abs(", "abs(");
            case RECIPROCAL -> token("⁻¹", "^(-1)");
            case SQUARE -> token("²", "^2");
            case CUBE -> token("³", "^3");
            case FACTORIAL -> token("!");
            case PERCENT -> token("%", "%");
            case NPR -> token("nPr", "nPr", true);
            case NCR -> token("nCr", "nCr", true);
            case NEGATE -> token("(−)", "-");
            case PI -> token("π", "pi"); case E -> token("e");
            case ANS -> token("Ans"); case RAN -> token("Ran#", "ran()");
            case EQUALS -> token("=", "=", true);
            case VAR_A -> token("A"); case VAR_B -> token("B");
            case VAR_C -> token("C"); case VAR_D -> token("D");
            case VAR_E -> token("E"); case VAR_F -> token("F");
            case VAR_X -> token("x"); case VAR_Y -> token("y");
            case VAR_Z -> token("z");
            default -> null;
        };
    }

    private Token functionToken(int index) {
        return switch (index) {
            case 0 -> token("d/dx(", "diff(");
            case 1 -> token("∫(", "integral(");
            case 2 -> token("Σ(", "sum(");
            case 3 -> token("÷R", "÷R", true);
            case 4 -> token("Simp(", "simp(");
            case 5 -> token("logₐ(", "log(");
            case 6 -> token("log(", "log(");
            default -> token("ln(", "ln(");
        };
    }

    private Token numericToken(int index) {
        return index == 0 ? token("Abs(", "abs(") : token("Rnd(", "rnd(");
    }

    private Token angleToken(int index) {
        return switch (index) {
            case 0 -> token("°(", "deg(");
            case 1 -> token("ʳ(", "rad(");
            case 2 -> token("ᵍ(", "grad(");
            case 3 -> token("Pol(", "pol(");
            case 4 -> token("Rec(", "rec(");
            default -> token("DMS(", "dms(");
        };
    }

    private Token trigToken(int index) {
        return switch (index) {
            case 0 -> token("sinh("); case 1 -> token("cosh(");
            case 2 -> token("tanh("); case 3 -> token("sinh⁻¹(", "asinh(");
            case 4 -> token("cosh⁻¹(", "acosh(");
            case 5 -> token("tanh⁻¹(", "atanh(");
            case 6 -> token("sin("); case 7 -> token("cos(");
            case 8 -> token("tan("); case 9 -> token("sin⁻¹(", "asin(");
            case 10 -> token("cos⁻¹(", "acos(");
            default -> token("tan⁻¹(", "atan(");
        };
    }

    private Token engineeringToken(int index) {
        String[] symbols = {"m", "μ", "n", "p", "f", "k", "M", "G", "T", "P", "E"};
        double[] factors = {1e-3, 1e-6, 1e-9, 1e-12, 1e-15,
                1e3, 1e6, 1e9, 1e12, 1e15, 1e18};
        int safe = Math.max(0, Math.min(index, symbols.length - 1));
        return token(symbols[safe], "*" + Double.toString(factors[safe]), true);
    }

    private Token constantToken(int index) {
        List<List<ScientificConstants.Constant>> groups =
                new ArrayList<>(ScientificConstants.categories().values());
        int group = Math.max(0, Math.min(catalogCategoryIndex, groups.size() - 1));
        List<ScientificConstants.Constant> values = groups.get(group);
        ScientificConstants.Constant value = values.get(Math.max(0, Math.min(index, values.size() - 1)));
        return token(value.symbol(), Double.toString(value.value()));
    }

    private Token probabilityToken(int index) {
        return switch (index) {
            case 0 -> token("%", "%");
            case 1 -> token("!");
            case 2 -> token("nPr", "nPr", true);
            case 3 -> token("nCr", "nCr", true);
            case 4 -> token("Ran#", "ran()");
            // ScalarExpressionEngine's clean-room spelling is ranint(...).
            // The display keeps the manual's RanInt# label.
            default -> token("RanInt#(", "ranint(");
        };
    }

    private Token relationToken(int index) {
        return switch (index) {
            case 0 -> token("=", "=", true);
            case 1 -> token("≠", "!=", true);
            case 2 -> token("<", "<", true);
            case 3 -> token("≤", "<=", true);
            case 4 -> token(">", ">", true);
            case 5 -> token("≥", ">=", true);
            case 6 -> token("→", "->", true);
            case 7 -> token(":", ":", true);
            case 8 -> token("Ans");
            case 9 -> token("π", "pi");
            case 10 -> token("e");
            case 11 -> token("√(", "sqrt(");
            case 12 -> token("√[ ](", "root(");
            case 13 -> token("⁻¹", "^(-1)");
            case 14 -> token("²", "^2");
            case 15 -> token("^", "^");
            case 16 -> token("(−)", "-");
            case 17 -> token(",");
            case 18 -> token("(");
            case 19 -> token(")");
            default -> {
                int variable = Math.max(0, Math.min(index - 20, VARIABLE_NAMES.size() - 1));
                String name = VARIABLE_NAMES.get(variable);
                yield token("→" + name, "->" + name, true);
            }
        };
    }

    private Token variableToken(int index) {
        if (index < VARIABLE_NAMES.size()) return token(VARIABLE_NAMES.get(index));
        return token("Ans");
    }

    private Token conversionToken(int index) {
        List<List<String>> groups = new ArrayList<>(UnitConverter.categories().values());
        int category = Math.max(0, Math.min(conversionCategoryIndex, groups.size() - 1));
        List<String> categoryCommands = groups.get(category);
        int safe = Math.max(0, Math.min(index, categoryCommands.size() - 1));
        String command = categoryCommands.get(safe);
        int global = new ArrayList<>(UnitConverter.catalog().keySet()).indexOf(command);
        return token(command + "(", "conv" + global + "(");
    }

    private List<CnCwCommand> currentMenuItems() {
        return switch (screen) {
            case CALCULATOR_ID -> com.codex.fx991.core.Compat.list(
                    command("id", model.displayName(),
                            model.name() + " · " + sizeLabel(model.applications().size())));
            case SETTINGS -> com.codex.fx991.core.Compat.list(command("calc", "计算设置", "输入、角度与显示"),
                    command("system", "系统设置", "语言、对比度与字体"),
                    command("reset", "复位", "设置与数据"));
            case RESET_CONFIRM -> com.codex.fx991.core.Compat.list(
                    command("yes", "确认复位", "清除设置、变量和数据"),
                    command("no", "取消", "返回设置菜单"));
            case SETTINGS_INPUT_OUTPUT -> com.codex.fx991.core.Compat.list(
                    command("io", "输入/输出", settingValue(0)),
                    command("angle", "角度单位", settingValue(1)),
                    command("display", "显示格式", settingValue(2)),
                    command("engineering", "工程符号", settingValue(3)),
                    command("fraction", "分数结果", settingValue(4)),
                    command("complex", "复数结果", settingValue(5)),
                    command("decimal", "小数点显示", settingValue(6)),
                     command("separator", "数字分隔符", settingValue(7)));
            case SETTINGS_INPUT_OUTPUT_OPTIONS -> com.codex.fx991.core.Compat.list(
                    command("math-math", radio(settings.inputOutput() == CnCwSettings.InputOutput.MATH_MATH,
                            "数学输入/数学输出"), "自然书写与精确结果"),
                    command("math-decimal", radio(settings.inputOutput() == CnCwSettings.InputOutput.MATH_DECIMAL,
                            "数学输入/小数输出"), "自然书写与小数结果"),
                    command("linear-linear", radio(settings.inputOutput() == CnCwSettings.InputOutput.LINEAR_LINEAR,
                            "线性输入/线性输出"), "单行输入与标准结果"),
                    command("linear-decimal", radio(settings.inputOutput() == CnCwSettings.InputOutput.LINEAR_DECIMAL,
                            "线性输入/小数输出"), "单行输入与小数结果"));
            case SETTINGS_ANGLE_OPTIONS -> com.codex.fx991.core.Compat.list(
                    command("degree", radio(settings.angleUnit() == AngleUnit.DEG, "度(D)"), "360°"),
                    command("radian", radio(settings.angleUnit() == AngleUnit.RAD, "弧度(R)"), "2π"),
                    command("grad", radio(settings.angleUnit() == AngleUnit.GRAD, "百分度(G)"), "400g"));
            case SETTINGS_FORMAT -> com.codex.fx991.core.Compat.list(
                    command("norm1", "Norm 1", "常规显示"),
                    command("norm2", "Norm 2", "常规显示"),
                    command("fix", "Fix", "固定小数位 0–9"),
                    command("sci", "Sci", "有效数字 1–10"));
            case SETTINGS_FIX_DIGITS -> digitCommands(false);
            case SETTINGS_SCI_DIGITS -> digitCommands(true);
            case SETTINGS_DISPLAY -> com.codex.fx991.core.Compat.list(
                    command("contrast", "对比度", "跟随手机显示"),
                    command("language", "语言", "中文"),
                    command("font", "多行字体", settings.multiLineFont().name()),
                    command("about", "关于", model.displayName()));
            case CATALOG -> com.codex.fx991.core.Compat.list(
                    command("analysis", "函数与分析", "导数、积分、求和、对数"),
                    command("probability", "概率", "%、阶乘、排列组合、随机数"),
                    command("numeric", "数值计算", "绝对值与四舍五入"),
                    command("angle", "角度/坐标/六十进制", "角度单位、Pol、Rec、DMS"),
                    command("trig", "双曲/反双曲/三角", "12 个函数"),
                    command("engineering", "工程符号", "m、μ、n、p、f、k…"),
                    command("constants", "科学常数", "47 个 CODATA 2018 常数"),
                    command("conversion", "单位换算", "40 个换算命令"),
                    command("other", "其他", "关系、多语句与存储"));
            case CATALOG_FUNCTIONS -> com.codex.fx991.core.Compat.list(
                    command("diff", "d/dx(", "导数"), command("integral", "∫(", "积分"),
                    command("sum", "Σ(", "求和"), command("remainder", "÷R", "商和余数"),
                    command("simp", "Simp(", "分数化简"), command("logbase", "logₐ(", "指定底数"),
                    command("log", "log(", "常用对数"), command("ln", "ln(", "自然对数"));
            case CATALOG_NUMERIC -> com.codex.fx991.core.Compat.list(
                    command("abs", "Abs(", "绝对值"), command("rnd", "Rnd(", "按显示格式四舍五入"));
            case CATALOG_ANGLE -> com.codex.fx991.core.Compat.list(
                    command("degree", "度(°)", "指定度"), command("radian", "弧度(r)", "指定弧度"),
                    command("gradian", "百分度(g)", "指定百分度"),
                    command("pol", "Pol(", "直角坐标转极坐标"),
                    command("rec", "Rec(", "极坐标转直角坐标"),
                    command("dms", "DMS(", "度、分、秒"));
            case CATALOG_TRIG -> com.codex.fx991.core.Compat.list(
                    command("sinh", "sinh(", "双曲正弦"), command("cosh", "cosh(", "双曲余弦"),
                    command("tanh", "tanh(", "双曲正切"), command("asinh", "sinh⁻¹(", "反双曲正弦"),
                    command("acosh", "cosh⁻¹(", "反双曲余弦"), command("atanh", "tanh⁻¹(", "反双曲正切"),
                    command("sin", "sin(", "正弦"), command("cos", "cos(", "余弦"),
                    command("tan", "tan(", "正切"), command("asin", "sin⁻¹(", "反正弦"),
                    command("acos", "cos⁻¹(", "反余弦"), command("atan", "tan⁻¹(", "反正切"));
            case CATALOG_ENGINEERING -> engineeringCommands();
            case CATALOG_CONSTANTS -> constantCategoryCommands();
            case CATALOG_CONSTANT_ITEMS -> constantCommands();
            case CATALOG_CONVERSIONS -> conversionCategoryCommands();
            case CATALOG_CONVERSION_ITEMS -> conversionCommands();
            case CATALOG_PROBABILITY -> com.codex.fx991.core.Compat.list(
                    command("percent", "%", "百分数"), command("factorial", "!", "阶乘"),
                    command("npr", "nPr", "排列"), command("ncr", "nCr", "组合"),
                    command("random", "Ran#", "随机数"),
                    command("randomInt", "RanInt#(", "随机整数"));
            case CATALOG_RELATIONS -> com.codex.fx991.core.Compat.list(
                    command("eq", "=", "等于"), command("ne", "≠", "不等于"),
                    command("lt", "<", "小于"), command("le", "≤", "小于等于"),
                    command("gt", ">", "大于"), command("ge", "≥", "大于等于"),
                    command("store", "→", "赋值到变量"), command("next", ":", "下一语句"),
                    command("ans", "Ans", "上次结果"), command("pi", "π", "圆周率"),
                    command("e", "e", "自然常数"), command("sqrt", "√(", "平方根"),
                    command("root", "ⁿ√(", "n 次根"), command("inverse", "x⁻¹", "倒数"),
                    command("square", "x²", "平方"), command("power", "xʸ", "乘方"),
                    command("negative", "(−)", "负号"), command("comma", ",", "分隔符"),
                    command("open", "(", "左括号"), command("close", ")", "右括号"),
                    command("store-a", "→A", "赋值"), command("store-b", "→B", "赋值"),
                    command("store-c", "→C", "赋值"), command("store-d", "→D", "赋值"),
                    command("store-e", "→E", "赋值"), command("store-f", "→F", "赋值"),
                    command("store-x", "→x", "赋值"), command("store-y", "→y", "赋值"),
                    command("store-z", "→z", "赋值"));
            case TOOLS -> com.codex.fx991.core.Compat.list(command("undo", "撤消", "恢复上次编辑"),
                    command("simplify", "化简", manualSimplification ? "手动" : "自动"),
                    command("verify", "运算验证", verificationMode ? "开" : "关"));
            case TOOLS_CONVERSION -> com.codex.fx991.core.Compat.list(
                    command("in-cm", "in → cm", "×2.54"), command("cm-in", "cm → in", "÷2.54"),
                    command("lb-kg", "lb → kg", "×0.45359237"), command("kg-lb", "kg → lb", "÷0.45359237"),
                    command("ft-m", "ft → m", "×0.3048"), command("m-ft", "m → ft", "÷0.3048"));
            case VARIABLES -> com.codex.fx991.core.Compat.list(
                    variableCommand("A"), variableCommand("B"), variableCommand("C"),
                    variableCommand("D"), variableCommand("E"), variableCommand("F"),
                    variableCommand("x"), variableCommand("y"), variableCommand("z"),
                    command("Ans", "Ans", hasAns ? formatNumber(ans) : "未定义"));
            case FUNCTIONS -> com.codex.fx991.core.Compat.list(
                    command("call-f", "f(", "调用 f(x)"),
                    command("call-g", "g(", "调用 g(x)"),
                    command("define-f", "定义 f(x)", functionFSource.isEmpty() ? "未定义" : functionFSource),
                    command("define-g", "定义 g(x)", functionGSource.isEmpty() ? "未定义" : functionGSource));
            case FORMAT -> formatCommands();
            default -> com.codex.fx991.core.Compat.list();
        };
    }

    private static List<CnCwCommand> engineeringCommands() {
        String[] symbols = {"m", "μ", "n", "p", "f", "k", "M", "G", "T", "P", "E"};
        String[] names = {"毫", "微", "纳", "皮", "飞", "千", "兆", "吉", "太", "拍", "艾"};
        List<CnCwCommand> items = new ArrayList<>(symbols.length);
        for (int index = 0; index < symbols.length; index++) {
            items.add(command("eng-" + index, names[index] + "(" + symbols[index] + ")",
                    "工程符号"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private List<CnCwCommand> digitCommands(boolean scientific) {
        int start = scientific ? 1 : 0;
        int end = scientific ? 10 : 9;
        List<CnCwCommand> items = new ArrayList<>();
        for (int digits = start; digits <= end; digits++) {
            boolean selected = settings.displayMode() == (scientific
                    ? CnCwSettings.DisplayMode.SCI : CnCwSettings.DisplayMode.FIX)
                    && settings.displayDigits() == digits;
            items.add(command("digits-" + digits, radio(selected, Integer.toString(digits)),
                    scientific ? "有效数字" : "小数位"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private static String radio(boolean selected, String label) {
        return (selected ? "● " : "○ ") + label;
    }

    private List<CnCwCommand> formatCommands() {
        if (!hasAns) return com.codex.fx991.core.Compat.list();
        List<CnCwCommand> items = new ArrayList<>();
        items.add(command("standard", "标准", "分数、π、√ 格式"));
        items.add(command("decimal", "小数", "小数结果"));
        if (ans > 0.0 && ans == Math.rint(ans) && ans <= 9_999_999_999L) {
            items.add(command("factor", "质因数分解", "最多 10 位正整数"));
        }
        Rational fraction = exactAns == null ? null : exactAns.rational();
        if (fraction != null) {
            items.add(command("improper", "假分数", "a/b"));
            items.add(command("mixed", "带分数", "a b/c"));
        }
        if (Double.isFinite(ans)) {
            items.add(command("engineering", "工程记数法", "用 ←/→ 移动小数点"));
        }
        if (Math.abs(ans) <= 9_999_999.999999) {
            items.add(command("dms", "六十进制", "度、分、秒"));
        }
        if (application == ApplicationMode.COMPLEX && hasComplexAns) {
            items.add(command("rectangular", "代数形式", "a+bi"));
            items.add(command("polar", "极坐标形式", "r∠θ"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private static List<CnCwCommand> constantCategoryCommands() {
        List<CnCwCommand> items = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<ScientificConstants.Constant>> entry
                : ScientificConstants.categories().entrySet()) {
            items.add(command("constant-group-" + index++, entry.getKey(),
                    entry.getValue().size() + " 个常数"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private List<CnCwCommand> constantCommands() {
        List<List<ScientificConstants.Constant>> groups =
                new ArrayList<>(ScientificConstants.categories().values());
        int group = Math.max(0, Math.min(catalogCategoryIndex, groups.size() - 1));
        List<CnCwCommand> items = new ArrayList<>();
        int index = 0;
        for (ScientificConstants.Constant value : groups.get(group)) {
            items.add(command("constant-" + index++, value.symbol(),
                    value.name() + (value.unit().isEmpty() ? "" : " · " + value.unit())));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private static List<CnCwCommand> conversionCategoryCommands() {
        List<CnCwCommand> items = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<String>> category : UnitConverter.categories().entrySet()) {
            items.add(command("conversion-group-" + index++, category.getKey(),
                    category.getValue().size() + " 个换算"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private List<CnCwCommand> conversionCommands() {
        List<List<String>> groups = new ArrayList<>(UnitConverter.categories().values());
        int category = Math.max(0, Math.min(conversionCategoryIndex, groups.size() - 1));
        List<CnCwCommand> items = new ArrayList<>();
        int index = 0;
        for (String name : groups.get(category)) {
            items.add(command("conversion-" + index++, name, "单位换算"));
        }
        return com.codex.fx991.core.Compat.copyList(items);
    }

    private List<CnCwCommand> homeItems() {
        List<CnCwCommand> items = new ArrayList<>();
        for (ApplicationMode mode : model.applications()) {
            items.add(command(mode.name(), mode.chineseName(), applicationDescription(mode)));
        }
        return items;
    }

    private List<CnCwCommand> modeCommands(ApplicationMode mode) {
        if (mode == null) return com.codex.fx991.core.Compat.list();
        return switch (mode) {
            case CALCULATE -> com.codex.fx991.core.Compat.list(command("calculate", "计算", "输入表达式"));
            case STATISTICS -> com.codex.fx991.core.Compat.list(command("one", "单变量", "x / 频数"),
                    command("two", "双变量", "x / y / 频数"),
                    command("regression", "回归", "七类回归模型"));
            case DISTRIBUTION -> com.codex.fx991.core.Compat.list(command("normal", "正态分布", "PDF / CDF / 逆分布"),
                    command("binomial", "二项分布", "概率 / 累计概率"),
                    command("poisson", "泊松分布", "概率 / 累计概率"));
            case SPREADSHEET -> com.codex.fx991.core.Compat.list(command("sheet", "A1:E45", "45 行 × 5 列"),
                    command("fill", "填充", "公式或数值"), command("recalc", "重新计算", "更新公式"));
            case FUNCTION_TABLE -> com.codex.fx991.core.Compat.list(command("fg", "f(x) 与 g(x)", "最多 30 行"),
                    command("single", "单函数", "最多 45 行"));
            case EQUATION -> com.codex.fx991.core.Compat.list(command("simultaneous", "联立方程", "2 至 4 个未知数"),
                    command("polynomial", "高阶方程", "2 至 4 次"),
                    command("solve", "求解方程", "Newton SOLVE"));
            case INEQUALITY -> com.codex.fx991.core.Compat.list(command("quadratic", "二次不等式", "四种关系"),
                    command("cubic", "三次不等式", "四种关系"),
                    command("quartic", "四次不等式", "四种关系"));
            case COMPLEX -> com.codex.fx991.core.Compat.list(command("complex", "复数计算", "a+bi / r∠θ"));
            case BASE_N -> com.codex.fx991.core.Compat.list(command("decimal", "十进制", "DEC"),
                    command("hex", "十六进制", "HEX"), command("binary", "二进制", "BIN"),
                    command("octal", "八进制", "OCT"));
            case MATRIX -> com.codex.fx991.core.Compat.list(command("define", "定义矩阵", "MatA 至 MatD，最大 4×4"),
                    command("calculate", "矩阵计算", "逆、行列式、转置"));
            case VECTOR -> com.codex.fx991.core.Compat.list(command("define", "定义向量", "VctA 至 VctD，2D/3D"),
                    command("calculate", "向量计算", "点积、叉积、夹角"));
            case RATIO -> com.codex.fx991.core.Compat.list(command("a:b=x:d", "A:B=X:D", "求 X"),
                    command("a:b=c:x", "A:B=C:X", "求 X"));
        };
    }

    private void publish() {
        List<CnCwCommand> menus = currentMenuItems();
        int itemCount = screen == CnCwScreen.HOME ? model.applications().size()
                : screen.isPopupMenu() ? menus.size()
                : applicationLanding ? modeCommands(application).size() : 1;
        if (itemCount > 0) selectedIndex = Math.min(selectedIndex, itemCount - 1);
        state = new CnCwUiState(model, screen, application, selectedIndex,
                menus, homeItems(), modeCommands(application), expression(), displayText(),
                naturalExpression(), cursor,
                result, ans, hasAns, status, settings, shiftArmed, poweredOn, overwriteMode,
                verificationMode, engineeringMode,
                !statementSequence.isEmpty() && statementSequenceIndex < statementSequence.size(),
                !history.isEmpty() && historyIndex > 0,
                !history.isEmpty() && historyIndex < history.size() - 1,
                applicationLanding,
                resultShown, spreadsheetGrid, spreadsheetRow, spreadsheetColumn,
                spreadsheetGrid ? spreadsheetCellsSnapshot() : com.codex.fx991.core.Compat.list(),
                spreadsheetGrid ? spreadsheet.input(spreadsheetAddress()) : "",
                navigationPath());
    }

    private List<String> spreadsheetCellsSnapshot() {
        List<String> values = new ArrayList<>(SpreadsheetModel.ROWS * SpreadsheetModel.COLUMNS);
        for (int row = 0; row < SpreadsheetModel.ROWS; row++) {
            for (int column = 0; column < SpreadsheetModel.COLUMNS; column++) {
                String address = (char) ('A' + column) + Integer.toString(row + 1);
                try {
                    String input = spreadsheet.input(address);
                    values.add(input.isEmpty() ? "" : formatNumber(spreadsheet.value(address)));
                } catch (RuntimeException error) {
                    values.add("错误");
                }
            }
        }
        return values;
    }

    /**
     * Publishes the current semantic-token editor as a renderer-owned tree.
     * Compound templates will replace the individual text leaves as their
     * cursor model lands; the cursor leaf keeps this intermediate form useful
     * to both the legacy string renderer and the natural-display renderer.
     */
    private CnCwExpressionNode naturalExpression() {
        return naturalRow(0, tokens.size());
    }

    /** Builds a visual tree without changing the semantic expression tokens. */
    private CnCwExpressionNode naturalRow(int start, int end) {
        List<CnCwExpressionNode> children = new ArrayList<>(Math.max(1, end - start + 1));
        int cursorHandledAt = -1;
        int index = start;
        while (index < end) {
            if (index == cursor && index != cursorHandledAt) {
                children.add(CnCwExpressionNode.cursor());
            }
            Token token = tokens.get(index);
            if ("^".equals(token.evaluation) && index != cursor && !children.isEmpty()) {
                int exponentEnd = naturalExponentEnd(index + 1, end, false);
                CnCwExpressionNode base = children.remove(children.size() - 1);
                CnCwExpressionNode exponent = naturalRow(index + 1, exponentEnd);
                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.SUPERSCRIPT,
                        com.codex.fx991.core.Compat.list(base, exponent), false));
                if (exponent.containsCursor() && cursor == exponentEnd) {
                    cursorHandledAt = exponentEnd;
                }
                index = exponentEnd;
                continue;
            }
            if (isFractionTemplate(token) && index != cursor && !children.isEmpty()) {
                int denominatorEnd = naturalExponentEnd(index + 1, end, false);
                CnCwExpressionNode numerator = children.remove(children.size() - 1);
                CnCwExpressionNode denominator = naturalRow(index + 1, denominatorEnd);
                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.FRACTION,
                        com.codex.fx991.core.Compat.list(numerator, denominator), false));
                if (denominator.containsCursor() && cursor == denominatorEnd) {
                    cursorHandledAt = denominatorEnd;
                }
                index = denominatorEnd;
                continue;
            }
            if ("*10^(".equals(token.evaluation) && index != cursor) {
                int exponentEnd = naturalExponentEnd(index + 1, end, true);
                CnCwExpressionNode exponent = naturalRow(index + 1, exponentEnd);
                CnCwExpressionNode ten = CnCwExpressionNode.text("\u00d710", false);
                children.add(CnCwExpressionNode.compound(CnCwExpressionNode.Kind.SUPERSCRIPT,
                        com.codex.fx991.core.Compat.list(ten, exponent), false));
                if (exponent.containsCursor() && cursor == exponentEnd) {
                    cursorHandledAt = exponentEnd;
                }
                index = exponentEnd;
                if (index < end && ")".equals(tokens.get(index).evaluation)) index++;
                continue;
            }
            children.add(CnCwExpressionNode.text(token.display, false));
            index++;
        }
        if (cursor == end && cursor != cursorHandledAt) {
            children.add(CnCwExpressionNode.cursor());
        }
        return CnCwExpressionNode.row(children);
    }

    private static boolean isFractionTemplate(Token token) {
        return "/".equals(token.evaluation) && "a/b".equals(token.display);
    }

    /**
     * Finds the visually attached exponent.  Normal powers end at the next
     * top-level binary operator.  The EXP template owns an implicit opening
     * parenthesis, so its matching close key is kept out of the exponent tree.
     */
    private int naturalExponentEnd(int start, int limit, boolean engineeringTemplate) {
        int depth = 0;
        boolean hasContent = false;
        for (int index = start; index < limit; index++) {
            Token token = tokens.get(index);
            String evaluation = token.evaluation;
            if (")".equals(evaluation)) {
                if (depth == 0 && engineeringTemplate) return index;
                if (depth == 0) return index;
                depth--;
                hasContent = true;
                continue;
            }
            if (hasContent && depth == 0 && token.binary) return index;
            if (evaluation.endsWith("(")) depth++;
            if (!token.binary || hasContent) hasContent = true;
        }
        return limit;
    }

    private String expression() {
        StringBuilder text = new StringBuilder(tokens.size() * 2);
        for (Token token : tokens) text.append(token.evaluation);
        return text.toString();
    }

    /**
     * Preserves semantic token boundaries for the scalar lexer.  The
     * invisible separator is ignored as whitespace, but prevents A+B key
     * tokens from collapsing into identifier AB and 1+E from becoming a
     * malformed scientific literal. Consecutive numeric-entry keys remain a
     * single number.
     */
    private String evaluationSource() {
        StringBuilder text = new StringBuilder(tokens.size() * 3);
        for (int index = 0; index < tokens.size(); index++) {
            if (index > 0 && needsLexicalBoundary(tokens.get(index - 1), tokens.get(index))) {
                text.append('\u2063');
            }
            text.append(tokens.get(index).evaluation);
        }
        return text.toString();
    }

    private static boolean needsLexicalBoundary(Token left, Token right) {
        return !(isNumericFragment(left) && isNumericFragment(right));
    }

    private static boolean isNumericFragment(Token token) {
        return token.evaluation.length() == 1
                && (Character.isDigit(token.evaluation.charAt(0))
                || token.evaluation.charAt(0) == '.');
    }

    private String displayText() {
        if (!screen.isApplication()) return "";
        if (tokens.isEmpty()) return "│";
        StringBuilder text = new StringBuilder(tokens.size() * 2 + 1);
        for (int i = 0; i <= tokens.size(); i++) {
            if (i == cursor) text.append('│');
            if (i < tokens.size()) text.append(tokens.get(i).display);
        }
        return text.toString();
    }

    private List<CnCwScreen> navigationPath() {
        List<CnCwScreen> path = new ArrayList<>();
        for (Navigation value : navigation) path.add(0, value.screen);
        path.add(screen);
        return path;
    }

    private String formatResult(double value, ExactValue exactValue, String source,
                                boolean forceExact) {
        boolean exactCandidate = forceExact
                || settings.inputOutput() == CnCwSettings.InputOutput.MATH_MATH
                || settings.inputOutput() == CnCwSettings.InputOutput.LINEAR_LINEAR;
        if (exactCandidate && exactValue != null && exactValue.isDisplayable()) {
            Rational rational = exactValue.rational();
            if (rational != null) {
                if (rational.isInteger()) return rational.numerator().toString();
                return settings.fractionMode() == CnCwSettings.FractionMode.MIXED
                        ? rational.mixedString() : rational.improperString();
            }
            return exactValue.display();
        }
        if (exactCandidate && source.matches("[0-9+\\-*/(). ]+") && source.contains("/")) {
            Rational rational = Rational.approximate(value, 1_000_000L, 1e-12);
            return settings.fractionMode() == CnCwSettings.FractionMode.MIXED
                    ? rational.mixedString() : rational.improperString();
        }
        return formatNumber(value);
    }

    private String formatNumber(double value) {
        if (settings.engineeringSymbols() && value != 0.0 && Double.isFinite(value)) {
            ManualFunctions.EngineeringValue engineering = ManualFunctions.engineering(value);
            if (!engineering.symbol().isEmpty()) {
                return formatPlainNumber(engineering.mantissa()) + engineering.symbol();
            }
        }
        return formatPlainNumber(value);
    }

    private String formatPlainNumber(double value) {
        String text;
        switch (settings.displayMode()) {
            case FIX -> text = String.format(Locale.ROOT, "%." + settings.displayDigits() + "f", value);
            case SCI -> text = String.format(Locale.ROOT, "%." + Math.max(0, settings.displayDigits() - 1) + "E", value);
            case NORM_1, NORM_2 -> {
                double absolute = Math.abs(value);
                double small = settings.displayMode() == CnCwSettings.DisplayMode.NORM_1 ? 1e-2 : 1e-9;
                if (absolute != 0.0 && (absolute < small || absolute >= 1e10)) {
                    text = String.format(Locale.ROOT, "%.9E", value).replaceAll("0+E", "E");
                } else {
                    text = trimDouble(value);
                }
            }
            default -> text = trimDouble(value);
        }
        if (settings.digitSeparator() && !text.contains("E")) text = separateDigits(text);
        if (settings.decimalMark() == CnCwSettings.DecimalMark.COMMA) text = text.replace('.', ',');
        return text;
    }

    private String formatComplex(ComplexValue value) {
        return formatComplex(value, settings.complexMode() == CnCwSettings.ComplexMode.POLAR);
    }

    private String formatComplex(ComplexValue value, boolean polar) {
        if (polar) {
            double angle = switch (settings.angleUnit()) {
                case DEG -> Math.toDegrees(value.argument());
                case GRAD -> value.argument() * 200.0 / Math.PI;
                case RAD -> value.argument();
            };
            return formatNumber(value.abs()) + "∠" + formatNumber(angle);
        }
        if (Math.abs(value.imaginary()) < 1e-14) return formatNumber(value.real());
        if (Math.abs(value.real()) < 1e-14) return formatNumber(value.imaginary()) + "i";
        return formatNumber(value.real()) + (value.imaginary() < 0 ? "−" : "+")
                + formatNumber(Math.abs(value.imaginary())) + "i";
    }

    private String engineeringResult(double value) {
        ManualFunctions.EngineeringValue engineering = ManualFunctions.engineering(value);
        if (engineering.exponent() == 0) return formatNumber(engineering.mantissa());
        return formatNumber(engineering.mantissa()) + "×10^" + engineering.exponent()
                + (engineering.symbol().isEmpty() ? "" : " (" + engineering.symbol() + ")");
    }

    private String primeFactors(double value) {
        if (value != Math.rint(value) || value <= 0.0 || value > 9_999_999_999L) {
            return "Math ERROR";
        }
        long remaining = (long) value;
        if (remaining < 2L) return Long.toString(remaining);
        List<Long> factors = new ArrayList<>();
        for (long factor = 2L; factor <= remaining / factor;
             factor += factor == 2L ? 1L : 2L) {
            while (remaining % factor == 0L) {
                factors.add(factor);
                remaining /= factor;
            }
        }
        if (remaining > 1L) factors.add(remaining);

        int largeCount = 0;
        boolean hasManualLimitFactor = false;
        for (long factor : factors) {
            if (factor > 999L) largeCount++;
            if (factor >= 1_018_081L) hasManualLimitFactor = true;
        }
        long unresolved = 1L;
        Map<Long, Integer> displayFactors = new java.util.LinkedHashMap<>();
        for (long factor : factors) {
            boolean defer = factor >= 1_018_081L || (largeCount >= 2 && factor > 999L);
            if (defer) unresolved *= factor;
            else displayFactors.merge(factor, 1, Integer::sum);
        }
        if (hasManualLimitFactor && unresolved == 1L) {
            unresolved = factors.get(factors.size() - 1);
            displayFactors.remove(unresolved);
        }
        StringBuilder output = new StringBuilder();
        for (Map.Entry<Long, Integer> factor : displayFactors.entrySet()) {
            if (output.length() > 0) output.append('×');
            output.append(factor.getKey());
            if (factor.getValue() > 1) output.append('^').append(factor.getValue());
        }
        if (unresolved > 1L) {
            if (output.length() > 0) output.append('×');
            output.append('(').append(unresolved).append(')');
        }
        return output.toString();
    }

    private String settingValue(int index) {
        return switch (index) {
            case 0 -> settings.inputOutput().name();
            case 1 -> settings.angleUnit().name();
            case 2 -> settings.displayMode().name() + ((settings.displayMode() == CnCwSettings.DisplayMode.FIX
                    || settings.displayMode() == CnCwSettings.DisplayMode.SCI)
                    ? " " + settings.displayDigits() : "");
            case 3 -> settings.engineeringSymbols() ? "开" : "关";
            case 4 -> settings.fractionMode() == CnCwSettings.FractionMode.MIXED ? "带分数" : "假分数";
            case 5 -> settings.complexMode() == CnCwSettings.ComplexMode.RECTANGULAR ? "a+bi" : "r∠θ";
            case 6 -> settings.decimalMark() == CnCwSettings.DecimalMark.POINT ? "句点" : "逗号";
            default -> settings.digitSeparator() ? "开" : "关";
        };
    }

    private String applicationStatus() {
        return application == null ? "HOME" : application.chineseName();
    }

    private static String applicationDescription(ApplicationMode mode) {
        return switch (mode) {
            case CALCULATE -> "基本与高级计算"; case STATISTICS -> "统计与回归";
            case DISTRIBUTION -> "正态、二项、泊松"; case SPREADSHEET -> "A1:E45";
            case FUNCTION_TABLE -> "一个或两个函数"; case EQUATION -> "联立、高阶与 SOLVE";
            case INEQUALITY -> "二至四次"; case COMPLEX -> "复数";
            case BASE_N -> "BIN/OCT/DEC/HEX"; case MATRIX -> "最大 4×4";
            case VECTOR -> "2D/3D"; case RATIO -> "比例式";
        };
    }

    private static String sizeLabel(int count) { return count + " applications"; }

    private static boolean isStructuredWorkflow(ApplicationMode mode) {
        return mode == ApplicationMode.STATISTICS || mode == ApplicationMode.DISTRIBUTION
                || mode == ApplicationMode.FUNCTION_TABLE || mode == ApplicationMode.EQUATION
                || mode == ApplicationMode.INEQUALITY || mode == ApplicationMode.MATRIX
                || mode == ApplicationMode.VECTOR || mode == ApplicationMode.RATIO;
    }

    private CnCwModeEngine.ModeResult spreadsheetWorkflow(String source) {
        List<String> fields = CnCwModeEngine.splitTopLevel(source);
        if (activeCommandId.equals("sheet")) {
            if (fields.size() == 1) {
                String field = fields.get(0);
                if (spreadsheetGrid && !field.matches("(?i)\\$?[A-E]\\$?\\d{1,2}")) {
                    String address = spreadsheetAddress();
                    String input = field;
                    if (!input.startsWith("=")
                            && input.matches(".*[A-Ea-e][1-9][0-9]?.*")) input = "=" + input;
                    spreadsheet.set(address, input);
                    double value = spreadsheet.value(address);
                    return new CnCwModeEngine.ModeResult(address + "=" + formatNumber(value)
                            + "\n剩余 " + spreadsheet.remainingBytes() + " bytes", value);
                }
                double value = spreadsheet.value(field);
                return new CnCwModeEngine.ModeResult(field.toUpperCase(Locale.ROOT)
                        + "=" + formatNumber(value), value);
            }
            String address = fields.get(0).toUpperCase(Locale.ROOT);
            String input = com.codex.fx991.core.Compat.join(",",
                    fields.subList(1, fields.size()));
            if (!input.startsWith("=") && input.matches(".*[A-Ea-e][1-9][0-9]?.*")) input = "=" + input;
            spreadsheet.set(address, input);
            double value = spreadsheet.value(address);
            return new CnCwModeEngine.ModeResult(address + "=" + formatNumber(value)
                    + "\n剩余 " + spreadsheet.remainingBytes() + " bytes", value);
        }
        if (activeCommandId.equals("fill")) {
            if (fields.size() < 3) throw new IllegalArgumentException("Enter start,end,formula");
            String range = fields.get(0).toUpperCase(Locale.ROOT) + ":"
                    + fields.get(1).toUpperCase(Locale.ROOT);
            String formula = com.codex.fx991.core.Compat.join(",",
                    fields.subList(2, fields.size()));
            spreadsheet.fillFormula(range, formula.startsWith("=") ? formula.substring(1) : formula);
            return new CnCwModeEngine.ModeResult(range + " 已填充\n剩余 "
                    + spreadsheet.remainingBytes() + " bytes", null);
        }
        spreadsheet.recalculate();
        return new CnCwModeEngine.ModeResult("重新计算完成\n剩余 "
                + spreadsheet.remainingBytes() + " bytes", null);
    }

    private static String workflowPrompt(ApplicationMode mode, CnCwCommand command) {
        String syntax = switch (mode) {
            case STATISTICS -> command.id().equals("one")
                    ? "输入 x1,x2,…" : "输入 x1,y1,x2,y2,…";
            case DISTRIBUTION -> switch (command.id()) {
                case "normal" -> "PDF: x,μ,σ；CDF: 下限,上限,μ,σ";
                case "binomial" -> "输入 x,n,p";
                default -> "输入 x,λ";
            };
            case FUNCTION_TABLE -> command.id().equals("fg")
                    ? "输入 f(x),g(x),开始,结束,步长" : "输入 f(x),开始,结束,步长";
            case EQUATION -> switch (command.id()) {
                case "polynomial" -> "输入最高次到常数项系数";
                case "simultaneous" -> "输入 n,每行系数与常数";
                default -> "输入 f(x),初值";
            };
            case INEQUALITY -> "输入关系码(1>,2<,3≥,4≤),系数";
            case BASE_N -> "输入 " + command.label() + " 整数";
            case MATRIX -> "输入 行,列,逐行元素";
            case VECTOR -> "输入一个或两个 2D/3D 向量";
            case RATIO -> "输入三个已知数 A,B,D/C";
            case SPREADSHEET -> "输入单元格和公式";
            default -> command.description();
        };
        return command.label() + " · " + syntax;
    }

    private static String titleFor(CnCwScreen value) {
        return switch (value) {
            case HOME -> "HOME"; case CALCULATOR_ID -> "计算器 ID"; case SETTINGS -> "设置";
            case SETTINGS_INPUT_OUTPUT -> "计算设置"; case SETTINGS_FORMAT -> "显示格式";
            case SETTINGS_INPUT_OUTPUT_OPTIONS -> "输入/输出";
            case SETTINGS_ANGLE_OPTIONS -> "角度单位";
            case SETTINGS_FIX_DIGITS -> "Fix 小数位";
            case SETTINGS_SCI_DIGITS -> "Sci 有效数字";
            case SETTINGS_DISPLAY -> "系统设置";
            case RESET_CONFIRM -> "复位确认";
            case CATALOG -> "目录"; case CATALOG_FUNCTIONS -> "函数";
            case CATALOG_NUMERIC -> "数值计算"; case CATALOG_ANGLE -> "角度与坐标";
            case CATALOG_TRIG -> "双曲与三角"; case CATALOG_ENGINEERING -> "工程符号";
            case CATALOG_CONSTANTS -> "科学常数";
            case CATALOG_CONSTANT_ITEMS -> "科学常数";
            case CATALOG_CONVERSIONS -> "单位换算";
            case CATALOG_CONVERSION_ITEMS -> "单位换算";
            case CATALOG_PROBABILITY -> "概率";
            case CATALOG_RELATIONS -> "关系符号";
            case TOOLS -> "工具"; case TOOLS_CONVERSION -> "单位换算";
            case VARIABLES -> "变量"; case FUNCTIONS -> "f(x) / g(x)";
            case FORMAT -> "格式"; default -> value.applicationMode() == null
                    ? value.name() : value.applicationMode().chineseName();
        };
    }

    private CnCwCommand variableCommand(String name) {
        return command(name, name, formatNumber(variables.getOrDefault(name, 0.0)));
    }

    private static CnCwCommand command(String id, String label, String description) {
        return new CnCwCommand(id, label, description);
    }

    private static Token token(String text) { return new Token(text, text, false); }
    private static Token token(String display, String evaluation) {
        return new Token(display, evaluation, false);
    }
    private static Token token(String display, String evaluation, boolean binary) {
        return new Token(display, evaluation, binary);
    }

    private static boolean isEntryKey(CnCwKey key) { return switch (key) {
        case DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6,
                DIGIT_7, DIGIT_8, DIGIT_9, DOT, COMMA, ADD, PLUS, SUBTRACT,
                MINUS, MULTIPLY, DIVIDE, FRACTION, POWER, OPEN_PAREN, OPEN, CLOSE_PAREN,
                CLOSE, SIN, COS, TAN, LOG, LN, EXP, SQRT, ROOT, ABS, RECIPROCAL,
                SQUARE, CUBE, FACTORIAL, PERCENT, NPR, NCR, NEGATE, PI, E, ANS,
                RAN, VAR_A, VAR_B, VAR_C, VAR_D, VAR_E, VAR_F, VAR_X, VAR_Y,
                VAR_Z, EQUALS -> true;
        default -> false;
    }; }

    private static int wrap(int value, int size) {
        if (size <= 0) return 0;
        int result = value % size;
        return result < 0 ? result + size : result;
    }

    private static String trimDouble(double value) {
        if (!Double.isFinite(value)) return Double.toString(value);
        if (value == 0.0) return "0";
        return BigDecimal.valueOf(value).round(new java.math.MathContext(10, RoundingMode.HALF_UP))
                .stripTrailingZeros().toPlainString();
    }

    private static String separateDigits(String input) {
        int dot = input.indexOf('.');
        String whole = dot >= 0 ? input.substring(0, dot) : input;
        String fraction = dot >= 0 ? input.substring(dot) : "";
        boolean negative = whole.startsWith("-");
        String digits = negative ? whole.substring(1) : whole;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
        }
        return (negative ? "-" : "") + out + fraction;
    }

    private record Token(String display, String evaluation, boolean binary) { }
    private record Navigation(CnCwScreen screen, int selectedIndex) { }
    private record HistoryEntry(List<Token> tokens, String result) { }
    private record CoordinateCall(boolean polar, String first, String second) { }
    private record SimplificationResult(long originalNumerator, long originalDenominator,
                                        long displayNumerator, long displayDenominator,
                                        boolean canContinue, double value) { }
}

from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')

old = '''            String evaluatedProcessDisplay = expandedProcessDisplay(tokens, ansProcessDisplay);
            resultProcessDisplay = evaluatedProcessDisplay;
            if (storeAnswer && application != ApplicationMode.INEQUALITY) {
                ansProcessDisplay = evaluatedProcessDisplay;
                ans = scalar;
                hasAns = true;
                exactAns = exactScalar;
                if (!complexEvaluation) {
                    complexAns = new ComplexValue(scalar, 0.0);
                    hasComplexAns = false;
                }
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
            history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result,
                    resultProcessDisplay, applicationResult));
            if (history.size() > 100) history.remove(0);
            historyIndex = history.size();
            if (spreadsheetGrid && application == ApplicationMode.SPREADSHEET
                    && activeCommandId.equals("sheet")) {
                // EXE commits the cell and returns the editor focus to the
                // grid, matching a physical spreadsheet's next-key behavior.
                tokens.clear();
                cursor = 0;
            }
'''
new = '''            commitSuccessfulResult(formatted, scalar, exactScalar, completionStatus,
                    storeAnswer, complexEvaluation);
'''
if old not in text:
    raise SystemExit('success commit block not found')
text = text.replace(old, new, 1)

marker = '''    private CoordinateCall coordinateCall(String source) {
'''
helper = '''    /**
     * Single success-commit gate for EXE evaluation. Numerical branches only
     * compute a payload; answer ownership, visible result state and history are
     * committed here so later Stage 6 steps can replace the legacy fields
     * without duplicating policy.
     */
    private void commitSuccessfulResult(String formatted,
                                        double scalar,
                                        ExactValue exactScalar,
                                        String completionStatus,
                                        boolean storeAnswer,
                                        boolean complexEvaluation) {
        String evaluatedProcessDisplay = expandedProcessDisplay(tokens, ansProcessDisplay);
        resultProcessDisplay = evaluatedProcessDisplay;
        if (storeAnswer && application != ApplicationMode.INEQUALITY) {
            ansProcessDisplay = evaluatedProcessDisplay;
            ans = scalar;
            hasAns = true;
            exactAns = exactScalar;
            if (!complexEvaluation) {
                complexAns = new ComplexValue(scalar, 0.0);
                hasComplexAns = false;
            }
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
        history.add(new HistoryEntry(com.codex.fx991.core.Compat.copyList(tokens), result,
                resultProcessDisplay, applicationResult));
        if (history.size() > 100) history.remove(0);
        historyIndex = history.size();
        if (spreadsheetGrid && application == ApplicationMode.SPREADSHEET
                && activeCommandId.equals("sheet")) {
            // EXE commits the cell and returns the editor focus to the grid.
            tokens.clear();
            cursor = 0;
        }
    }

'''
if marker not in text:
    raise SystemExit('coordinate marker not found')
text = text.replace(marker, helper + marker, 1)
p.write_text(text, encoding='utf-8')

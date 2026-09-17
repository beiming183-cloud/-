from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')

# Main evaluator and any helper callers now use an explicitly named error commit gate.
text = text.replace('showError(', 'commitCalculationError(')

old = '''        } catch (RuntimeException error) {
            result = "Math ERROR";
            resultShown = true;
        }
'''
new = '''        } catch (RuntimeException error) {
            commitCalculationError(CalculationError.MATH, 0);
        }
'''
if old not in text:
    raise SystemExit('spreadsheet direct error path not found')
text = text.replace(old, new, 1)

old = '''        };
        formatConverted = true;
        resultShown = true;
        status = engineeringMode ? "ENG 模式 · 用 ←/→ 移动小数点" : "格式转换";
        closeAllPopups();
'''
new = '''        };
        if ("Math ERROR".equals(result)) {
            closeAllPopups();
            commitCalculationError(CalculationError.MATH, 0);
            return;
        }
        formatConverted = true;
        resultShown = true;
        status = engineeringMode ? "ENG 模式 · 用 ←/→ 移动小数点" : "格式转换";
        closeAllPopups();
'''
if old not in text:
    raise SystemExit('format conversion result block not found')
text = text.replace(old, new, 1)

old = '''    private void commitCalculationError(CalculationError error, int sourcePosition) {
        lastError = error;
'''
new = '''    /** Single error-commit gate for evaluator and mode/tool failures. */
    private void commitCalculationError(CalculationError error, int sourcePosition) {
        applicationResult = null;
        lastExactResult = null;
        lastError = error;
'''
if old not in text:
    raise SystemExit('renamed error method not found')
text = text.replace(old, new, 1)

p.write_text(text, encoding='utf-8')

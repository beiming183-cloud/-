from pathlib import Path

root = Path(__file__).resolve().parents[1]

# Migrate the old ratio result-protocol regression from TEXT to KEY_VALUE.
p = root / 'core/src/regression/java/com/codex/fx991/core/CnCwModeEngineSuite.java'
text = p.read_text(encoding='utf-8')
replacements = [
    ('CnCwModeEngine.ResultLayout.TEXT, ratio.layout()',
     'CnCwModeEngine.ResultLayout.KEY_VALUE, ratio.layout()'),
    ('"unmigrated ratio keeps text compatibility layout"',
     '"ratio publishes structured key-value layout"'),
    ('check(!ratio.hasGrid(), "text result does not expose a grid");',
     'equal("X", ratio.items().get(0).label(), "ratio result label");\n'
     '        equal("4.5", ratio.items().get(0).value(), "ratio result value");'),
]
for old, new in replacements:
    if old not in text:
        raise SystemExit('ratio mode regression target not found: ' + old)
    text = text.replace(old, new, 1)
p.write_text(text, encoding='utf-8')

# Preserve the historical comma bridge. OK/arrow navigation remains structured;
# pressing comma in the first ratio field intentionally returns to legacy input.
p = root / 'core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java'
text = p.read_text(encoding='utf-8')
marker = '        if (workflowSession.selectedIsChoice() && !shiftArmed) {'
fallback = '''        // Preserve the historical comma bridge for ratio mode.
        if (key == CnCwKey.COMMA
                && workflowSession.spec().mode() == ApplicationMode.RATIO
                && workflowSession.selectedRow() == 0
                && workflowSession.selectedColumn() == 0) {
            workflowSession = null;
            status = "比例 · 兼容逗号输入";
            return false;
        }

'''
if marker not in text:
    raise SystemExit('ratio legacy bridge marker not found')
p.write_text(text.replace(marker, fallback + marker, 1), encoding='utf-8')

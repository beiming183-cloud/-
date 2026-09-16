from pathlib import Path

machine_path = Path('core/src/main/java/com/codex/fx991/core/cw/CnCwMachine.java')
text = machine_path.read_text(encoding='utf-8')

old = '''        if (direction < 0) {\n            if (position <= 0) return 0;\n            return semanticAtomStart(position);\n        }'''
new = '''        if (direction < 0) {\n            if (position <= 0) return 0;\n            int atomStart = semanticAtomStart(position);\n            if (atomStart > 0 && isPowerTemplate(tokens.get(atomStart - 1))) {\n                return powerBaseStart(atomStart - 1);\n            }\n            return atomStart;\n        }'''
if old not in text:
    raise SystemExit('left selection compatibility anchor not found')
text = text.replace(old, new, 1)

old = '''        if (atomEnd < tokens.size() && isFractionTemplate(tokens.get(atomEnd))) {\n            FractionBounds fraction = fractionBounds(atomEnd);\n            return fraction == null ? semanticAtomEnd(atomEnd + 1) : fraction.denominatorEnd;\n        }\n        return atomEnd;'''
new = '''        if (atomEnd < tokens.size() && isFractionTemplate(tokens.get(atomEnd))) {\n            FractionBounds fraction = fractionBounds(atomEnd);\n            return fraction == null ? semanticAtomEnd(atomEnd + 1) : fraction.denominatorEnd;\n        }\n        if (atomEnd < tokens.size() && isPowerTemplate(tokens.get(atomEnd))) {\n            PowerBounds power = powerBounds(atomEnd);\n            return power == null ? semanticAtomEnd(atomEnd + 1) : power.exponentEnd;\n        }\n        return atomEnd;'''
if old not in text:
    raise SystemExit('right selection compatibility anchor not found')
text = text.replace(old, new, 1)
machine_path.write_text(text, encoding='utf-8')

suite_path = Path('core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java')
suite = suite_path.read_text(encoding='utf-8')
start = suite.index('    private void powerSelectionReplacementKeepsStructure() {')
end = suite.index('    private CnCwMachine powerMachine() {', start)
replacement = r'''    private void powerSelectionReplacementKeepsStructure() {
        CnCwMachine machine = powerMachine();
        machine.dispatch(CnCwKey.SHIFT);
        machine.dispatch(CnCwKey.LEFT);
        equal("2^3", machine.selectedExpression(),
                "keyboard semantic selection keeps Stage 2 whole-power behavior");
        machine.dispatch(CnCwKey.DIGIT_7);
        equal("7", machine.state().expression(),
                "keyboard replacement still replaces the complete power atomically");
        equal(CnCwCursorPath.rootBoundary(1), machine.state().semanticCursor(),
                "whole-power keyboard replacement returns to root semantics");

        machine = powerMachine();
        machine.beginTouchSelection(2);
        machine.extendTouchSelection(3);
        equal("3", machine.selectedExpression(),
                "touch fine selection can select exponent independently");
        equal(1, machine.pasteExpression("9"),
                "touch-selected exponent accepts semantic replacement");
        equal("2^9", machine.state().expression(),
                "touch exponent replacement preserves ^ template");
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "exponent replacement cursor remains in exponent");

        machine = powerMachine();
        machine.beginTouchSelection(0);
        machine.extendTouchSelection(1);
        equal("2", machine.selectedExpression(),
                "touch fine selection can select power base independently");
        equal(1, machine.pasteExpression("4"),
                "base replacement accepts one semantic token");
        equal("4^3", machine.state().expression(),
                "replacing base preserves exponent and ^ template");
    }

'''
suite = suite[:start] + replacement + suite[end:]
suite_path.write_text(suite, encoding='utf-8')

relay_path = Path('docs/hand-offs/STAGE3_RELAY.md')
relay = relay_path.read_text(encoding='utf-8')
old = '8. exponent 和 base 都可独立选择/替换，跨过 `^` 后选择完整幂；'
new = ('8. 触摸精细选区可独立选择/替换 exponent 或 base；键盘 `SHIFT+方向键` '
       '继续遵守 Stage 2 协议，把幂整体视为一个选择单元；')
if old not in relay:
    raise SystemExit('relay power selection anchor not found')
relay = relay.replace(old, new, 1)
relay_path.write_text(relay, encoding='utf-8')

print('Stage 3 Step 3 selection compatibility patch applied')

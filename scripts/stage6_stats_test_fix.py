from pathlib import Path
p = Path('core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java')
text = p.read_text(encoding='utf-8')
old = '        equal(9, commands.size(), "statistics command count");\n'
new = '        equal(18, commands.size(), "statistics command count including frequency variants");\n'
if old not in text:
    raise RuntimeError('statistics command count assertion not found')
p.write_text(text.replace(old, new, 1), encoding='utf-8')

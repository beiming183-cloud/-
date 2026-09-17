from pathlib import Path

path = Path('core/src/regression/java/com/codex/fx991/core/CnCwFunctionalitySuite.java')
text = path.read_text(encoding='utf-8')
replacements = [
    (
        '        equal("Abs(MatA)", machine.state().applicationResult().title(),\n                "MatAns preserves last matrix result payload");',
        '        equal("MatAns", machine.state().applicationResult().title(),\n                "MatAns uses the manual answer-memory title");\n        equal("3", machine.state().applicationResult().cells().get(2),\n                "MatAns preserves the last matrix payload");'
    ),
    (
        '        equal("VctA+VctB", machine.state().applicationResult().title(),\n                "VctAns preserves last vector result payload");',
        '        equal("VctAns", machine.state().applicationResult().title(),\n                "VctAns uses the manual answer-memory title");'
    ),
]
for old, new in replacements:
    if old not in text:
        raise SystemExit(f'target not found: {old!r}')
    text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')

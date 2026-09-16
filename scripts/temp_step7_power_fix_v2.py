from pathlib import Path
import runpy

root = Path(__file__).resolve().parents[1]
runpy.run_path(str(root / "scripts/temp_step7_power_fix.py"), run_name="__main__")

suite_path = root / "core/src/regression/java/com/codex/fx991/core/CnCwCursorPathSuite.java"
text = suite_path.read_text(encoding="utf-8")
old = '''        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "UP returns base cursor to exponent");

        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at exponent end exits power to root row");
'''
new = '''        machine.dispatch(CnCwKey.UP);
        equal(CnCwCursorPath.Slot.SUPERSCRIPT_EXPONENT,
                machine.state().semanticCursor().slot(),
                "UP returns base cursor to exponent");
        equal(0, machine.state().semanticCursor().offset(),
                "UP enters the visual left edge of the exponent");

        machine.dispatch(CnCwKey.RIGHT);
        equal(1, machine.state().semanticCursor().offset(),
                "RIGHT reaches exponent end after visual-edge UP transition");
        machine.dispatch(CnCwKey.RIGHT);
        equal(CnCwCursorPath.Slot.ROW, machine.state().semanticCursor().slot(),
                "RIGHT at exponent end exits power to root row");
'''
if old not in text:
    raise SystemExit("existing power vertical regression block not found")
suite_path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Adjusted legacy power vertical regression for visual-edge semantics")

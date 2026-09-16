from pathlib import Path

path = Path('app/src/main/java/com/codex/fx991smooth/PhysicalKeyLayout.java')
text = path.read_text(encoding='utf-8')
old = '''        // Direction pad is the interaction centre, not a tiny afterthought.
        // Center the pad in the actual space between the left controls and rocker.
        // The geometric screen center makes the left and right breathing room uneven.
        float dpadX = width * 0.53f;
        float dpadY = controlTop + controlHeight * 0.52f;
        float d = radius * 1.56f;
'''
new = '''        // Direction pad is the interaction centre, not a tiny afterthought.
        // Balance its horizontal breathing room against the actual neighboring
        // touch modules instead of pinning it to a fixed percentage of screen width.
        // Using the midpoint between the 2x2 utility block's right edge and the
        // page rocker's left edge keeps both gaps equal as the viewport changes.
        float utilityModuleRight = utilityRightX + utilityRadius;
        float rockerModuleLeft = width - margin - radius * 2f;
        float dpadX = (utilityModuleRight + rockerModuleLeft) * 0.5f;
        float dpadY = controlTop + controlHeight * 0.52f;
        float d = radius * 1.56f;
'''
if new in text:
    raise SystemExit('dpad balance patch already applied')
if old not in text:
    raise SystemExit('dpad anchor block not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')

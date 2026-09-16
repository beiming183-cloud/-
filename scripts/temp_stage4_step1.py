from pathlib import Path

root = Path(__file__).resolve().parents[1]
engine_path = root / "core/src/main/java/com/codex/fx991/core/cw/CnCwModeEngine.java"
text = engine_path.read_text(encoding="utf-8")

old = '''    public record ModeResult(String display, Double primaryValue) {
        public ModeResult {
            if (com.codex.fx991.core.Compat.isBlank(display)) throw new IllegalArgumentException("display");
        }
    }
'''
new = '''    public enum ResultLayout {
        TEXT,
        KEY_VALUE,
        VECTOR,
        MATRIX,
        TABLE
    }

    /** One ordered label/value entry in a structured application result. */
    public static final class ResultItem {
        private final String label;
        private final String value;

        public ResultItem(String label, String value) {
            if (com.codex.fx991.core.Compat.isBlank(label)) {
                throw new IllegalArgumentException("label");
            }
            if (value == null) throw new IllegalArgumentException("value");
            this.label = label;
            this.value = value;
        }

        public String label() { return label; }
        public String value() { return value; }
    }

    /**
     * Core-owned application result protocol.
     *
     * <p>`display()` and `primaryValue()` remain source-compatible with the
     * Stage 2/3 bridge.  New renderers can instead consume layout/title/items
     * without parsing presentation strings.</p>
     */
    public static final class ModeResult {
        private final String display;
        private final Double primaryValue;
        private final ResultLayout layout;
        private final String title;
        private final List<ResultItem> items;

        public ModeResult(String display, Double primaryValue) {
            this(display, primaryValue, ResultLayout.TEXT, "",
                    com.codex.fx991.core.Compat.list());
        }

        public ModeResult(String display, Double primaryValue,
                          ResultLayout layout, String title, List<ResultItem> items) {
            if (com.codex.fx991.core.Compat.isBlank(display)) {
                throw new IllegalArgumentException("display");
            }
            if (layout == null) throw new IllegalArgumentException("layout");
            this.display = display;
            this.primaryValue = primaryValue;
            this.layout = layout;
            this.title = title == null ? "" : title;
            this.items = com.codex.fx991.core.Compat.copyList(items);
        }

        public static ModeResult keyValue(String title, String display, Double primaryValue,
                                          ResultItem... entries) {
            List<ResultItem> items = new ArrayList<>();
            if (entries != null) {
                for (ResultItem entry : entries) {
                    if (entry == null) throw new IllegalArgumentException("entry");
                    items.add(entry);
                }
            }
            return new ModeResult(display, primaryValue, ResultLayout.KEY_VALUE,
                    title, items);
        }

        public String display() { return display; }
        public Double primaryValue() { return primaryValue; }
        public ResultLayout layout() { return layout; }
        public String title() { return title; }
        public List<ResultItem> items() { return items; }
    }

    private static ResultItem item(String label, double value) {
        return new ResultItem(label, format(value));
    }
'''
if old not in text:
    raise SystemExit("ModeResult record target not found")
text = text.replace(old, new, 1)

old = '''            return new ModeResult("n=" + format(result.n()) + "  x̄=" + format(result.mean())
                    + "\\nσx=" + format(result.populationStdDev())
                    + "  sx=" + format(result.sampleStdDev()), result.mean());
'''
new = '''            String display = "n=" + format(result.n()) + "  x̄=" + format(result.mean())
                    + "\\nσx=" + format(result.populationStdDev())
                    + "  sx=" + format(result.sampleStdDev());
            return ModeResult.keyValue("一元统计", display, result.mean(),
                    item("n", result.n()),
                    item("x̄", result.mean()),
                    item("σx", result.populationStdDev()),
                    item("sx", result.sampleStdDev()));
'''
if old not in text:
    raise SystemExit("one-variable statistics target not found")
text = text.replace(old, new, 1)

old = '''            return new ModeResult("a=" + format(fit.a()) + "  b=" + format(fit.b())
                    + "\\nr=" + format(fit.r()), fit.r());
'''
new = '''            String display = "a=" + format(fit.a()) + "  b=" + format(fit.b())
                    + "\\nr=" + format(fit.r());
            return ModeResult.keyValue("线性回归", display, fit.r(),
                    item("a", fit.a()),
                    item("b", fit.b()),
                    item("r", fit.r()));
'''
if old not in text:
    raise SystemExit("regression statistics target not found")
text = text.replace(old, new, 1)

old = '''        return new ModeResult("x̄=" + format(result.meanX()) + "  ȳ=" + format(result.meanY())
                + "\\nσx=" + format(result.populationStdDevX())
                + "  σy=" + format(result.populationStdDevY()), result.meanX());
'''
new = '''        String display = "x̄=" + format(result.meanX()) + "  ȳ=" + format(result.meanY())
                + "\\nσx=" + format(result.populationStdDevX())
                + "  σy=" + format(result.populationStdDevY());
        return ModeResult.keyValue("双变量统计", display, result.meanX(),
                item("x̄", result.meanX()),
                item("ȳ", result.meanY()),
                item("σx", result.populationStdDevX()),
                item("σy", result.populationStdDevY()));
'''
if old not in text:
    raise SystemExit("two-variable statistics target not found")
text = text.replace(old, new, 1)
engine_path.write_text(text, encoding="utf-8")

suite_path = root / "core/src/regression/java/com/codex/fx991/core/CnCwModeEngineSuite.java"
text = suite_path.read_text(encoding="utf-8")
old = '''        near(2.5, one.primaryValue(), 0.0, "one-variable mean");
        check(one.display().contains("n=4"), "one-variable count rendered");

        var regression = evaluate(ApplicationMode.STATISTICS, "regression", "1,3,2,5,3,7");
        near(1.0, regression.primaryValue(), 1e-12, "perfect linear correlation");
        check(regression.display().contains("a=2"), "linear slope rendered");
'''
new = '''        near(2.5, one.primaryValue(), 0.0, "one-variable mean");
        check(one.display().contains("n=4"), "one-variable count rendered");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, one.layout(),
                "one-variable statistics publishes key/value layout");
        equal("一元统计", one.title(), "one-variable result title");
        equal(4, one.items().size(), "one-variable structured item count");
        equal("n", one.items().get(0).label(), "one-variable first item label");
        equal("4", one.items().get(0).value(), "one-variable count value");
        equal("x̄", one.items().get(1).label(), "one-variable mean item label");
        equal("2.5", one.items().get(1).value(), "one-variable mean item value");

        var regression = evaluate(ApplicationMode.STATISTICS, "regression", "1,3,2,5,3,7");
        near(1.0, regression.primaryValue(), 1e-12, "perfect linear correlation");
        check(regression.display().contains("a=2"), "linear slope rendered");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, regression.layout(),
                "regression publishes key/value layout");
        equal("线性回归", regression.title(), "regression result title");
        equal(3, regression.items().size(), "regression structured item count");
        equal("r", regression.items().get(2).label(), "regression correlation item label");

        var two = evaluate(ApplicationMode.STATISTICS, "two", "1,2,3,4");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, two.layout(),
                "two-variable statistics publishes key/value layout");
        equal("双变量统计", two.title(), "two-variable result title");
        equal(4, two.items().size(), "two-variable structured item count");
        equal("x̄", two.items().get(0).label(), "two-variable x mean label");
        equal("ȳ", two.items().get(1).label(), "two-variable y mean label");
'''
if old not in text:
    raise SystemExit("statistics test target not found")
text = text.replace(old, new, 1)
suite_path.write_text(text, encoding="utf-8")

print("Stage 4 Step 1 result protocol patch applied")

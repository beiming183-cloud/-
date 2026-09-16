from pathlib import Path

root = Path(__file__).resolve().parents[1]
engine_path = root / "core/src/main/java/com/codex/fx991/core/cw/CnCwModeEngine.java"
text = engine_path.read_text(encoding="utf-8")

old = '''        private final ResultLayout layout;
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
'''
new = '''        private final ResultLayout layout;
        private final String title;
        private final List<ResultItem> items;
        /** Optional row-major grid payload used by MATRIX/VECTOR/TABLE layouts. */
        private final int rows;
        private final int columns;
        private final List<String> cells;

        public ModeResult(String display, Double primaryValue) {
            this(display, primaryValue, ResultLayout.TEXT, "",
                    com.codex.fx991.core.Compat.list());
        }

        public ModeResult(String display, Double primaryValue,
                          ResultLayout layout, String title, List<ResultItem> items) {
            this(display, primaryValue, layout, title, items,
                    0, 0, com.codex.fx991.core.Compat.list());
        }

        public ModeResult(String display, Double primaryValue,
                          ResultLayout layout, String title, List<ResultItem> items,
                          int rows, int columns, List<String> cells) {
            if (com.codex.fx991.core.Compat.isBlank(display)) {
                throw new IllegalArgumentException("display");
            }
            if (layout == null) throw new IllegalArgumentException("layout");
            if (items == null) throw new IllegalArgumentException("items");
            if (cells == null) throw new IllegalArgumentException("cells");
            if (rows < 0 || columns < 0) throw new IllegalArgumentException("grid size");
            if ((rows == 0) != (columns == 0)) throw new IllegalArgumentException("grid shape");
            if (rows > 0 && cells.size() != rows * columns) {
                throw new IllegalArgumentException("grid cells");
            }
            if (rows == 0 && !cells.isEmpty()) throw new IllegalArgumentException("grid cells");
            this.display = display;
            this.primaryValue = primaryValue;
            this.layout = layout;
            this.title = title == null ? "" : title;
            this.items = com.codex.fx991.core.Compat.copyList(items);
            this.rows = rows;
            this.columns = columns;
            this.cells = com.codex.fx991.core.Compat.copyList(cells);
        }
'''
if old not in text:
    raise SystemExit("ModeResult constructors target not found")
text = text.replace(old, new, 1)

old = '''        public String display() { return display; }
        public Double primaryValue() { return primaryValue; }
        public ResultLayout layout() { return layout; }
        public String title() { return title; }
        public List<ResultItem> items() { return items; }
    }
'''
new = '''        public static ModeResult grid(ResultLayout layout, String title, String display,
                                      Double primaryValue, int rows, int columns,
                                      List<String> cells, List<ResultItem> items) {
            if (layout != ResultLayout.MATRIX && layout != ResultLayout.VECTOR
                    && layout != ResultLayout.TABLE) {
                throw new IllegalArgumentException("grid layout");
            }
            return new ModeResult(display, primaryValue, layout, title, items,
                    rows, columns, cells);
        }

        public String display() { return display; }
        public Double primaryValue() { return primaryValue; }
        public ResultLayout layout() { return layout; }
        public String title() { return title; }
        public List<ResultItem> items() { return items; }
        public int rows() { return rows; }
        public int columns() { return columns; }
        public List<String> cells() { return cells; }
        public boolean hasGrid() { return rows > 0; }
    }
'''
if old not in text:
    raise SystemExit("ModeResult accessors target not found")
text = text.replace(old, new, 1)

old = '''        MatrixValue matrix = new MatrixValue(data);
        if (rows == columns) {
            double determinant = matrix.determinant();
            return new ModeResult(rows + "×" + columns + "  det=" + format(determinant)
                    + "\\n[1,1]=" + format(matrix.get(0, 0)), determinant);
        }
        return new ModeResult(rows + "×" + columns + " matrix\\n[1,1]="
                + format(matrix.get(0, 0)), matrix.get(0, 0));
'''
new = '''        MatrixValue matrix = new MatrixValue(data);
        List<String> cells = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                cells.add(format(matrix.get(row, column)));
            }
        }
        List<ResultItem> items = new ArrayList<>();
        if (rows == columns) {
            double determinant = matrix.determinant();
            items.add(item("det", determinant));
            String display = rows + "×" + columns + "  det=" + format(determinant)
                    + "\\n[1,1]=" + format(matrix.get(0, 0));
            return ModeResult.grid(ResultLayout.MATRIX, "矩阵", display, determinant,
                    rows, columns, cells, items);
        }
        String display = rows + "×" + columns + " matrix\\n[1,1]="
                + format(matrix.get(0, 0));
        return ModeResult.grid(ResultLayout.MATRIX, "矩阵", display, matrix.get(0, 0),
                rows, columns, cells, items);
'''
if old not in text:
    raise SystemExit("matrix target not found")
text = text.replace(old, new, 1)

old = '''        if (values.length == 2 || values.length == 3) {
            VectorValue vector = new VectorValue(values);
            return new ModeResult("|v|=" + format(vector.magnitude())
                    + "\\nunit[1]=" + format(vector.unit().get(0)), vector.magnitude());
        }
        if (values.length == 4 || values.length == 6) {
            int dimension = values.length / 2;
            double[] left = new double[dimension];
            double[] right = new double[dimension];
            System.arraycopy(values, 0, left, 0, dimension);
            System.arraycopy(values, dimension, right, 0, dimension);
            VectorValue a = new VectorValue(left);
            VectorValue b = new VectorValue(right);
            return new ModeResult("dot=" + format(a.dot(b))
                    + "\\nangle=" + format(Math.toDegrees(a.angleRadians(b))) + "°", a.dot(b));
        }
'''
new = '''        if (values.length == 2 || values.length == 3) {
            VectorValue vector = new VectorValue(values);
            List<String> cells = new ArrayList<>();
            for (double value : values) cells.add(format(value));
            List<ResultItem> items = new ArrayList<>();
            items.add(item("|v|", vector.magnitude()));
            VectorValue unit = vector.unit();
            for (int index = 0; index < values.length; index++) {
                items.add(item("unit[" + (index + 1) + "]", unit.get(index)));
            }
            String display = "|v|=" + format(vector.magnitude())
                    + "\\nunit[1]=" + format(unit.get(0));
            return ModeResult.grid(ResultLayout.VECTOR, "向量", display, vector.magnitude(),
                    1, values.length, cells, items);
        }
        if (values.length == 4 || values.length == 6) {
            int dimension = values.length / 2;
            double[] left = new double[dimension];
            double[] right = new double[dimension];
            System.arraycopy(values, 0, left, 0, dimension);
            System.arraycopy(values, dimension, right, 0, dimension);
            VectorValue a = new VectorValue(left);
            VectorValue b = new VectorValue(right);
            double dot = a.dot(b);
            double angle = Math.toDegrees(a.angleRadians(b));
            List<String> cells = new ArrayList<>();
            for (double value : left) cells.add(format(value));
            for (double value : right) cells.add(format(value));
            List<ResultItem> items = new ArrayList<>();
            items.add(item("dot", dot));
            items.add(item("angle", format(angle) + "°"));
            String display = "dot=" + format(dot) + "\\nangle=" + format(angle) + "°";
            return ModeResult.grid(ResultLayout.VECTOR, "向量运算", display, dot,
                    2, dimension, cells, items);
        }
'''
if old not in text:
    raise SystemExit("vector target not found")
text = text.replace(old, new, 1)
engine_path.write_text(text, encoding="utf-8")

suite_path = root / "core/src/regression/java/com/codex/fx991/core/CnCwModeEngineSuite.java"
text = suite_path.read_text(encoding="utf-8")
old = '''    private void matrixVectorAndRatioWorkflows() {
        var matrix = evaluate(ApplicationMode.MATRIX, "calculate", "2,2,2,1,1,1");
        near(1.0, matrix.primaryValue(), 0.0, "matrix determinant");
        var vector = evaluate(ApplicationMode.VECTOR, "calculate", "1,2,3,4");
        near(11.0, vector.primaryValue(), 0.0, "two-dimensional dot product");
        var ratio = evaluate(ApplicationMode.RATIO, "a:b=x:d", "3,8,12");
        near(4.5, ratio.primaryValue(), 0.0, "ratio unknown");
    }
'''
new = '''    private void matrixVectorAndRatioWorkflows() {
        var matrix = evaluate(ApplicationMode.MATRIX, "calculate", "2,2,2,1,1,1");
        near(1.0, matrix.primaryValue(), 0.0, "matrix determinant");
        equal(CnCwModeEngine.ResultLayout.MATRIX, matrix.layout(),
                "matrix publishes matrix layout");
        equal("矩阵", matrix.title(), "matrix result title");
        equal(2, matrix.rows(), "matrix row count");
        equal(2, matrix.columns(), "matrix column count");
        equal(4, matrix.cells().size(), "matrix cell count");
        equal("2", matrix.cells().get(0), "matrix [1,1] structured cell");
        equal("1", matrix.items().get(0).value(), "matrix determinant structured item");

        var rectangular = evaluate(ApplicationMode.MATRIX, "calculate", "2,3,1,2,3,4,5,6");
        equal(CnCwModeEngine.ResultLayout.MATRIX, rectangular.layout(),
                "rectangular matrix keeps matrix layout");
        equal(2, rectangular.rows(), "rectangular matrix rows");
        equal(3, rectangular.columns(), "rectangular matrix columns");
        equal("6", rectangular.cells().get(5), "rectangular matrix last cell");
        equal(0, rectangular.items().size(), "rectangular matrix has no determinant item");

        var singleVector = evaluate(ApplicationMode.VECTOR, "calculate", "3,4,0");
        near(5.0, singleVector.primaryValue(), 0.0, "single-vector magnitude");
        equal(CnCwModeEngine.ResultLayout.VECTOR, singleVector.layout(),
                "single vector publishes vector layout");
        equal(1, singleVector.rows(), "single vector uses one grid row");
        equal(3, singleVector.columns(), "single vector dimension");
        equal("3", singleVector.cells().get(0), "single vector first component");
        equal("|v|", singleVector.items().get(0).label(), "single vector magnitude label");
        equal("5", singleVector.items().get(0).value(), "single vector magnitude value");

        var vector = evaluate(ApplicationMode.VECTOR, "calculate", "1,2,3,4");
        near(11.0, vector.primaryValue(), 0.0, "two-dimensional dot product");
        equal(CnCwModeEngine.ResultLayout.VECTOR, vector.layout(),
                "two-vector operation publishes vector layout");
        equal(2, vector.rows(), "two-vector operation uses two rows");
        equal(2, vector.columns(), "two-vector operation dimension");
        equal("dot", vector.items().get(0).label(), "dot-product item label");
        equal("11", vector.items().get(0).value(), "dot-product item value");
        equal("angle", vector.items().get(1).label(), "vector-angle item label");
        check(vector.items().get(1).value().endsWith("°"), "vector angle keeps degree unit");

        var ratio = evaluate(ApplicationMode.RATIO, "a:b=x:d", "3,8,12");
        near(4.5, ratio.primaryValue(), 0.0, "ratio unknown");
        equal(CnCwModeEngine.ResultLayout.TEXT, ratio.layout(),
                "unmigrated ratio keeps text compatibility layout");
        check(!ratio.hasGrid(), "text result does not expose a grid");
    }
'''
if old not in text:
    raise SystemExit("matrix/vector test target not found")
suite_path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Stage 4 Step 3 matrix/vector result patch applied")

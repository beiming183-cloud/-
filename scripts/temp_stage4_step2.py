from pathlib import Path

root = Path(__file__).resolve().parents[1]
engine_path = root / "core/src/main/java/com/codex/fx991/core/cw/CnCwModeEngine.java"
text = engine_path.read_text(encoding="utf-8")

old = '''    private static ResultItem item(String label, double value) {
        return new ResultItem(label, format(value));
    }
'''
new = '''    private static ResultItem item(String label, double value) {
        return new ResultItem(label, format(value));
    }

    private static ResultItem item(String label, String value) {
        return new ResultItem(label, value);
    }
'''
if old not in text:
    raise SystemExit("item helper target not found")
text = text.replace(old, new, 1)

old = '''        if (command.equals("polynomial")) {
            double[] coefficients = evaluateFields(fields, 0, context);
            List<ComplexValue> roots = PolynomialEngine.roots(coefficients);
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < roots.size(); i++) {
                if (i > 0) text.append(i == 1 ? "\\n" : "  ");
                text.append("x").append(i + 1).append("=").append(formatComplex(roots.get(i)));
            }
            return new ModeResult(text.toString(), roots.get(0).real());
        }
'''
new = '''        if (command.equals("polynomial")) {
            double[] coefficients = evaluateFields(fields, 0, context);
            List<ComplexValue> roots = PolynomialEngine.roots(coefficients);
            StringBuilder text = new StringBuilder();
            List<ResultItem> items = new ArrayList<>();
            for (int i = 0; i < roots.size(); i++) {
                String label = "x" + (i + 1);
                String value = formatComplex(roots.get(i));
                if (i > 0) text.append(i == 1 ? "\\n" : "  ");
                text.append(label).append("=").append(value);
                items.add(item(label, value));
            }
            return new ModeResult(text.toString(), roots.get(0).real(),
                    ResultLayout.KEY_VALUE, "多项式方程", items);
        }
'''
if old not in text:
    raise SystemExit("polynomial target not found")
text = text.replace(old, new, 1)

old = '''            double[] solution = new MatrixValue(matrix).solve(right);
            return new ModeResult(formatVector("x", solution), solution[0]);
        }
'''
new = '''            double[] solution = new MatrixValue(matrix).solve(right);
            List<ResultItem> items = new ArrayList<>();
            for (int index = 0; index < solution.length; index++) {
                items.add(item("x" + (index + 1), solution[index]));
            }
            return new ModeResult(formatVector("x", solution), solution[0],
                    ResultLayout.KEY_VALUE, "联立方程", items);
        }
'''
if old not in text:
    raise SystemExit("simultaneous target not found")
text = text.replace(old, new, 1)

old = '''        if (!solved.converged()) throw new ArithmeticException("Cannot Solve");
        return new ModeResult("x=" + format(solved.solution())
                + "\\nL-R=" + format(solved.remainder()), solved.solution());
'''
new = '''        if (!solved.converged()) throw new ArithmeticException("Cannot Solve");
        String display = "x=" + format(solved.solution())
                + "\\nL-R=" + format(solved.remainder());
        return ModeResult.keyValue("SOLVE", display, solved.solution(),
                item("x", solved.solution()),
                item("L-R", solved.remainder()));
'''
if old not in text:
    raise SystemExit("solve target not found")
text = text.replace(old, new, 1)
engine_path.write_text(text, encoding="utf-8")

suite_path = root / "core/src/regression/java/com/codex/fx991/core/CnCwModeEngineSuite.java"
text = suite_path.read_text(encoding="utf-8")
old = '''    private void equationWorkflows() {
        var polynomial = evaluate(ApplicationMode.EQUATION, "polynomial", "1,2,-3");
        check(polynomial.display().contains("x1="), "polynomial roots rendered");
        var simultaneous = evaluate(ApplicationMode.EQUATION, "simultaneous",
                "2,1,1,5,2,-1,1");
        near(2.0, simultaneous.primaryValue(), 1e-12, "two-variable system first root");
        check(simultaneous.display().contains("x2=3"), "system second root rendered");
        var solve = evaluate(ApplicationMode.EQUATION, "solve", "x^2-16,1");
        near(4.0, solve.primaryValue(), 1e-9, "SOLVE workflow");
    }
'''
new = '''    private void equationWorkflows() {
        var polynomial = evaluate(ApplicationMode.EQUATION, "polynomial", "1,2,-3");
        check(polynomial.display().contains("x1="), "polynomial roots rendered");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, polynomial.layout(),
                "polynomial publishes key/value layout");
        equal("多项式方程", polynomial.title(), "polynomial result title");
        equal(2, polynomial.items().size(), "quadratic publishes two roots");
        equal("x1", polynomial.items().get(0).label(), "first polynomial root label");
        equal("x2", polynomial.items().get(1).label(), "second polynomial root label");

        var complexPolynomial = evaluate(ApplicationMode.EQUATION, "polynomial", "1,0,1");
        equal(2, complexPolynomial.items().size(), "complex quadratic keeps both roots");
        check(complexPolynomial.items().get(0).value().contains("i"),
                "first complex root remains structured as complex text");
        check(complexPolynomial.items().get(1).value().contains("i"),
                "second complex root remains structured as complex text");

        var simultaneous = evaluate(ApplicationMode.EQUATION, "simultaneous",
                "2,1,1,5,2,-1,1");
        near(2.0, simultaneous.primaryValue(), 1e-12, "two-variable system first root");
        check(simultaneous.display().contains("x2=3"), "system second root rendered");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, simultaneous.layout(),
                "simultaneous equations publish key/value layout");
        equal("联立方程", simultaneous.title(), "simultaneous result title");
        equal(2, simultaneous.items().size(), "two-variable system publishes two entries");
        equal("x1", simultaneous.items().get(0).label(), "system first variable label");
        equal("2", simultaneous.items().get(0).value(), "system first variable value");
        equal("x2", simultaneous.items().get(1).label(), "system second variable label");
        equal("3", simultaneous.items().get(1).value(), "system second variable value");

        var solve = evaluate(ApplicationMode.EQUATION, "solve", "x^2-16,1");
        near(4.0, solve.primaryValue(), 1e-9, "SOLVE workflow");
        equal(CnCwModeEngine.ResultLayout.KEY_VALUE, solve.layout(),
                "SOLVE publishes key/value layout");
        equal("SOLVE", solve.title(), "SOLVE result title");
        equal(2, solve.items().size(), "SOLVE publishes solution and residual");
        equal("x", solve.items().get(0).label(), "SOLVE solution label");
        equal("L-R", solve.items().get(1).label(), "SOLVE residual label");
    }
'''
if old not in text:
    raise SystemExit("equation test target not found")
suite_path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Stage 4 Step 2 equation result patch applied")

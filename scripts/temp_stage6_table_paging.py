from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / 'app/src/main/java/com/codex/fx991smooth/CalculatorView.java'
text = p.read_text(encoding='utf-8')


def replace_once(old, new, name):
    global text
    if old not in text:
        raise SystemExit(f'target not found: {name}')
    text = text.replace(old, new, 1)


replace_once(
'''    private Future<?> pendingEvaluation;
    private long inputRevision;
    private boolean evaluating;
''',
'''    private Future<?> pendingEvaluation;
    private long inputRevision;
    private boolean evaluating;
    /** View-local viewport for core-owned TABLE results; never changes math state. */
    private CnCwModeEngine.ModeResult tableResult;
    private int tableFirstRow;
''', 'table viewport fields')

replace_once(
'''            case MATRIX, VECTOR -> drawGridResult(canvas, result, lcd, contentTop, contentBottom, available);
            case TABLE -> drawKeyValueResult(canvas, result, lcd, contentTop, contentBottom, available);
            case TEXT -> { }
''',
'''            case MATRIX, VECTOR -> drawGridResult(canvas, result, lcd, contentTop, contentBottom, available);
            case TABLE -> drawTableResult(canvas, result, lcd, contentTop, contentBottom, available);
            case TEXT -> { }
''', 'table renderer route')

marker = '''    private void drawGridResult(Canvas canvas, CnCwModeEngine.ModeResult result,
'''
method = r'''    private int tableVisibleRows(CnCwModeEngine.ModeResult result) {
        return Math.max(1, Math.min(4, result == null ? 1 : result.rows()));
    }

    private void drawTableResult(Canvas canvas, CnCwModeEngine.ModeResult result,
                                 RectF lcd, float contentTop, float contentBottom,
                                 float available) {
        int rows = result.rows();
        int columns = result.columns();
        if (rows <= 0 || columns <= 0 || result.cells().size() != rows * columns) {
            drawKeyValueResult(canvas, result, lcd, contentTop, contentBottom, available);
            return;
        }
        if (tableResult != result) {
            tableResult = result;
            tableFirstRow = 0;
        }
        int visibleRows = tableVisibleRows(result);
        int maxStart = Math.max(0, rows - visibleRows);
        tableFirstRow = Math.max(0, Math.min(tableFirstRow, maxStart));

        float resultTop = contentTop + (contentBottom - contentTop) * 0.43f;
        float titleBottom = resultTop + dp(14f);
        float headerHeight = dp(14f);
        float gridTop = titleBottom;
        float gridBottom = contentBottom - dp(11f);
        float gridLeft = lcd.left + dp(7f);
        float gridRight = lcd.right - dp(rows > visibleRows ? 7f : 5f);
        float cellWidth = (gridRight - gridLeft) / columns;
        float rowHeight = Math.max(dp(10f),
                (gridBottom - gridTop - headerHeight) / visibleRows);

        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.5f));
        canvas.drawText(ellipsize(result.title(), available * 0.58f),
                gridLeft, resultTop + dp(10f), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextSize(sp(8.5f));
        String range = (tableFirstRow + 1) + "–"
                + Math.min(rows, tableFirstRow + visibleRows) + "/" + rows;
        canvas.drawText(range, gridRight, resultTop + dp(10f), paint);

        for (int column = 0; column < columns; column++) {
            float left = gridLeft + column * cellWidth;
            paint.setColor(Color.argb(38, 24, 58, 45));
            canvas.drawRect(left, gridTop, left + cellWidth, gridTop + headerHeight, paint);
            paint.setColor(LCD_INK);
            paint.setTypeface(FACE_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(9f));
            String heading = column < result.items().size()
                    ? result.items().get(column).label() : Integer.toString(column + 1);
            canvas.drawText(ellipsize(heading, cellWidth - dp(3f)),
                    left + cellWidth * 0.5f,
                    centeredBaseline(gridTop, gridTop + headerHeight), paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.55f));
        paint.setColor(Color.argb(95, 24, 58, 45));
        for (int vr = 0; vr < visibleRows; vr++) {
            int row = tableFirstRow + vr;
            if (row >= rows) break;
            float top = gridTop + headerHeight + vr * rowHeight;
            for (int column = 0; column < columns; column++) {
                float left = gridLeft + column * cellWidth;
                scratch.set(left, top, left + cellWidth, top + rowHeight);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(vr % 2 == 0
                        ? Color.argb(12, 24, 58, 45) : Color.TRANSPARENT);
                canvas.drawRect(scratch, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.argb(72, 24, 58, 45));
                canvas.drawRect(scratch, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(LCD_INK);
                paint.setTypeface(FACE_MEDIUM);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(sp(columns >= 3 ? 9.2f : 10f));
                String value = result.cells().get(row * columns + column);
                canvas.drawText(ellipsize(value, cellWidth - dp(4f)),
                        scratch.centerX(), centeredBaseline(top, top + rowHeight), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        if (rows > visibleRows) {
            drawScrollBar(canvas, lcd, tableFirstRow, visibleRows, rows,
                    gridTop + headerHeight, gridTop + headerHeight + visibleRows * rowHeight);
        }
        paint.setColor(LCD_INK);
        paint.setTypeface(FACE_NORMAL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(7.8f));
        canvas.drawText("↑↓逐行  Page↑↓翻页", gridLeft, contentBottom - dp(1f), paint);
    }

'''
if marker not in text:
    raise SystemExit('table method marker not found')
text = text.replace(marker, method + marker, 1)

replace_once(
'''    private void dispatchKey(CnCwKey key) {
        long revision = ++inputRevision;
''',
'''    private void dispatchKey(CnCwKey key) {
        if (handleTableResultNavigation(key)) return;
        long revision = ++inputRevision;
''', 'table key interception')

marker = '''    private void cancelPendingEvaluation() {
'''
helper = r'''    private boolean handleTableResultNavigation(CnCwKey key) {
        if (!state.resultShown() || !state.hasStructuredApplicationResult()) return false;
        CnCwModeEngine.ModeResult result = state.applicationResult();
        if (result == null || result.layout() != CnCwModeEngine.ResultLayout.TABLE) return false;
        int visibleRows = tableVisibleRows(result);
        int delta = switch (key) {
            case UP -> -1;
            case DOWN -> 1;
            case PAGE_UP -> -visibleRows;
            case PAGE_DOWN -> visibleRows;
            default -> 0;
        };
        if (delta == 0) return false;
        if (tableResult != result) {
            tableResult = result;
            tableFirstRow = 0;
        }
        int maxStart = Math.max(0, result.rows() - visibleRows);
        int next = Math.max(0, Math.min(maxStart, tableFirstRow + delta));
        if (next != tableFirstRow) {
            tableFirstRow = next;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        postInvalidateOnAnimation();
        return true;
    }

'''
if marker not in text:
    raise SystemExit('table navigation marker not found')
text = text.replace(marker, helper + marker, 1)

p.write_text(text, encoding='utf-8')

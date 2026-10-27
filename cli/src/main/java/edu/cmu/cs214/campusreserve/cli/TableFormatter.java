package edu.cmu.cs214.campusreserve.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lays a listing out in columns: a header row, a rule under it, then the rows.
 *
 * <p>Each column is as wide as the widest thing in it. Columns marked numeric
 * are padded on the left so their digits line up on the right; every other
 * column is padded on the right. Trailing blanks are cut from each line, so the
 * output compares cleanly in a test.
 */
public final class TableFormatter {

    /** Blanks between one column and the next. */
    private static final String GAP = "  ";

    private final List<String> headers;
    private final Set<Integer> numericColumns = new HashSet<>();
    private final List<List<String>> rows = new ArrayList<>();

    /**
     * Starts a table with these column headers.
     *
     * @param headers at least one header
     */
    public TableFormatter(String... headers) {
        if (headers.length == 0) {
            throw new IllegalArgumentException("A table needs at least one column");
        }
        this.headers = List.of(headers);
    }

    /**
     * Marks columns as numeric, which right aligns them.
     *
     * @param columns zero-based column indexes
     */
    public TableFormatter rightAlign(int... columns) {
        for (int column : columns) {
            if (column < 0 || column >= headers.size()) {
                throw new IllegalArgumentException("No column at index " + column);
            }
            numericColumns.add(column);
        }
        return this;
    }

    /**
     * Adds a row.
     *
     * @param cells one value per column, in header order
     */
    public TableFormatter addRow(String... cells) {
        if (cells.length != headers.size()) {
            throw new IllegalArgumentException(
                "Row has " + cells.length + " cells but the table has " + headers.size() + " columns");
        }
        rows.add(List.of(cells));
        return this;
    }

    /** The header, the rule, and every row, in order. */
    public List<String> render() {
        int[] widths = new int[headers.size()];
        for (int column = 0; column < headers.size(); column++) {
            widths[column] = headers.get(column).length();
            for (List<String> row : rows) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }
        List<String> rule = new ArrayList<>(headers.size());
        for (int width : widths) {
            rule.add("-".repeat(width));
        }
        List<String> lines = new ArrayList<>(rows.size() + 2);
        lines.add(line(headers, widths));
        lines.add(line(rule, widths));
        for (List<String> row : rows) {
            lines.add(line(row, widths));
        }
        return List.copyOf(lines);
    }

    private String line(List<String> cells, int[] widths) {
        StringBuilder text = new StringBuilder();
        for (int column = 0; column < cells.size(); column++) {
            String cell = cells.get(column);
            String blanks = " ".repeat(widths[column] - cell.length());
            text.append(column == 0 ? "" : GAP)
                .append(numericColumns.contains(column) ? blanks + cell : cell + blanks);
        }
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        return text.substring(0, end);
    }
}

package io.github.edwardg117.renderable.report;

import java.util.ArrayList;
import java.util.List;

/** Fixed-width plain-text table. Columns are two spaces apart; right-aligned columns are for numbers. */
final class Table {
    private final String[] headers;
    private final boolean[] rightAlign;
    private final List<String[]> rows = new ArrayList<>();

    Table(String[] headers, boolean[] rightAlign) {
        if (headers.length != rightAlign.length) {
            throw new IllegalArgumentException("headers and rightAlign must have the same length");
        }
        this.headers = headers;
        this.rightAlign = rightAlign;
    }

    void row(Object... cells) {
        if (cells.length != headers.length) {
            throw new IllegalArgumentException("expected " + headers.length + " cells, got " + cells.length);
        }
        String[] r = new String[cells.length];
        for (int i = 0; i < cells.length; i++) r[i] = String.valueOf(cells[i]);
        rows.add(r);
    }

    String render(String indent) {
        int[] w = new int[headers.length];
        for (int i = 0; i < headers.length; i++) w[i] = headers[i].length();
        for (String[] r : rows) {
            for (int i = 0; i < r.length; i++) w[i] = Math.max(w[i], r[i].length());
        }

        StringBuilder sb = new StringBuilder();
        line(sb, indent, headers, w);
        sb.append(indent);
        for (int i = 0; i < w.length; i++) {
            if (i > 0) sb.append("  ");
            sb.append("-".repeat(w[i]));
        }
        sb.append('\n');
        for (String[] r : rows) line(sb, indent, r, w);
        return sb.toString();
    }

    private void line(StringBuilder sb, String indent, String[] cells, int[] w) {
        sb.append(indent);
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append("  ");
            String c = cells[i];
            int pad = w[i] - c.length();
            if (rightAlign[i]) {
                sb.append(" ".repeat(pad)).append(c);
            } else {
                sb.append(c).append(" ".repeat(pad));
            }
        }
        sb.append('\n');
    }
}

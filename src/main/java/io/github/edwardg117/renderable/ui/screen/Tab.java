package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.edwardg117.renderable.ui.screen.ColumnSort.NONE;

/** Tabs of the results screen and the column layout each one uses. Eight tabs, four per row on the screen. */
public enum Tab {
    FRAMES("Frames", Columns.METRIC_VALUE),
    HITCHES("Hitches", Columns.HITCH),
    BLOCK_ENTITIES("Block Entities", Columns.RANKED),
    ENTITIES("Entities", Columns.RANKED),
    PARTICLES("Particles", Columns.PARTICLE),
    FLYWHEEL("Flywheel", Columns.FLYWHEEL),
    BLOCKS("Blocks", Columns.BLOCK),
    SECTIONS("Sections", Columns.SECTION);

    private final String title;
    private final Columns columns;

    Tab(String title, Columns columns) {
        this.title = title;
        this.columns = columns;
    }

    public String title() {
        return title;
    }

    public Columns columns() {
        return columns;
    }

    /** True for tabs whose rows are ranked entries the Sort button can reorder. */
    public boolean sortable() {
        return this == BLOCK_ENTITIES || this == ENTITIES || this == PARTICLES;
    }

    /**
     * One column: its header, its share of the row width, whether it holds a right-aligned number, and what
     * clicking its header sorts by.
     */
    public record Column(String name, int weight, boolean rightAlign, ColumnSort sort) {
    }

    /**
     * A tab's columns and the one it opens sorted by. {@link #layout(int)} turns weights into pixel offsets.
     * Kept as a nested class so enum constants can reference the constants without a forward-reference error.
     * Frames and Hitches have no sortable column, so their {@code defaultSortColumn} of 0 means nothing, and
     * the screen only draws a sort arrow on a sortable column.
     */
    public record Columns(List<Column> all, int defaultSortColumn) {
        public Columns {
            all = List.copyOf(all);
            if (all.isEmpty()) throw new IllegalArgumentException("a tab needs at least one column");
            if (defaultSortColumn < 0 || defaultSortColumn >= all.size()) {
                throw new IllegalArgumentException("defaultSortColumn " + defaultSortColumn + " is not a column");
            }
        }

        /** Blank pixels {@link #layout} leaves after every column; {@link #fitNumber} lets a number use all but one. */
        static final int PADDING = 4;

        private static final Pattern DECIMALS = Pattern.compile("\\d+\\.\\d+");

        static final Columns METRIC_VALUE = of(NONE,
                left("metric", 2, NONE),
                left("value", 3, NONE));
        static final Columns HITCH = of(NONE,
                left("frame", 2, NONE),
                right("total ms", 1, NONE),
                left("split", 7, NONE));
        static final Columns RANKED = of(ColumnSort.of(Ranking.Sort.TOTAL),
                left("name", 5, ColumnSort.of(Ranking.Sort.LABEL)),
                right("render us/f", 1, ColumnSort.of(Ranking.Sort.RENDER)),
                right("tick us/f", 1, ColumnSort.of(Ranking.Sort.TICK)),
                right("share", 1, ColumnSort.of(Ranking.Sort.TOTAL)),
                right("peak ms", 1, ColumnSort.of(Ranking.Sort.PEAK)),
                right("gc f", 1, ColumnSort.of(Ranking.Sort.GC_FRAMES)),
                right("shadow", 1, ColumnSort.of(Ranking.Sort.SHADOW)),
                right("verts/f", 1, ColumnSort.of(Ranking.Sort.VERTICES)),
                right("calls", 1, ColumnSort.of(Ranking.Sort.CALLS)));
        static final Columns PARTICLE = of(ColumnSort.of(Ranking.Sort.TOTAL),
                left("class", 5, ColumnSort.of(Ranking.Sort.LABEL)),
                right("render us/f", 1, ColumnSort.of(Ranking.Sort.RENDER)),
                right("tick us/f", 1, ColumnSort.of(Ranking.Sort.TICK)),
                right("share", 1, ColumnSort.of(Ranking.Sort.TOTAL)),
                right("avg live", 1, ColumnSort.of(Ranking.Sort.CALLS)),
                right("verts/f", 1, ColumnSort.of(Ranking.Sort.VERTICES)),
                right("gc f", 1, ColumnSort.of(Ranking.Sort.GC_FRAMES)));
        static final Columns FLYWHEEL = of(ColumnSort.of(Ranking.Sort.TOTAL),
                left("visual class", 6, ColumnSort.of(Ranking.Sort.LABEL)),
                right("avg count", 1, ColumnSort.of(Ranking.Sort.TOTAL)),
                right("share", 1, NONE));
        static final Columns BLOCK = of(ColumnSort.of(Ranking.MeshSort.TIME),
                left("block", 8, ColumnSort.of(Ranking.MeshSort.LABEL)),
                right("mesh ms/s", 2, ColumnSort.of(Ranking.MeshSort.TIME)),
                right("us/call", 2, ColumnSort.of(Ranking.MeshSort.PER_CALL)),
                right("calls", 2, ColumnSort.of(Ranking.MeshSort.CALLS)));
        static final Columns SECTION = of(ColumnSort.of(Ranking.MeshSort.CALLS),
                left("section", 6, ColumnSort.of(Ranking.MeshSort.LABEL)),
                right("rebuilds", 2, ColumnSort.of(Ranking.MeshSort.CALLS)),
                right("rebuilds/s", 2, ColumnSort.of(Ranking.MeshSort.CALLS)),
                right("mesh ms", 2, ColumnSort.of(Ranking.MeshSort.TIME)),
                right("avg ms/rb", 3, ColumnSort.of(Ranking.MeshSort.PER_CALL)));

        /** Columns that open sorted by the first column whose sort is {@code opensBy}. */
        private static Columns of(ColumnSort opensBy, Column... columns) {
            for (int i = 0; i < columns.length; i++) {
                if (columns[i].sort().equals(opensBy)) return new Columns(List.of(columns), i);
            }
            throw new IllegalArgumentException("no column sorts by " + opensBy);
        }

        private static Column left(String name, int weight, ColumnSort sort) {
            return new Column(name, weight, false, sort);
        }

        private static Column right(String name, int weight, ColumnSort sort) {
            return new Column(name, weight, true, sort);
        }

        public int size() {
            return all.size();
        }

        public Column get(int i) {
            return all.get(i);
        }

        /** Returns {x offsets, widths} for a row of the given pixel width. Each column keeps 4 px of padding. */
        public int[][] layout(int rowWidth) {
            int total = 0;
            for (Column c : all) total += c.weight();
            int[] x = new int[all.size()];
            int[] w = new int[all.size()];
            int cursor = 0;
            for (int i = 0; i < all.size(); i++) {
                int px = rowWidth * all.get(i).weight() / total;
                x[i] = cursor;
                w[i] = Math.max(8, px - PADDING);
                cursor += px;
            }
            return new int[][]{x, w};
        }

        /**
         * What to draw for a right-aligned number in a column {@code width} px wide. It may reach up to
         * {@code PADDING - 1} px left of its column: every glyph's advance ends in a blank pixel, so that still leaves
         * two blank pixels before the column on its left, twice the spacing inside a number. Wider than that, its
         * decimals are rounded away, and if it still does not fit it is drawn as #s, as a spreadsheet does. It is
         * never cut short like text, because a truncated number reads as a different one: {@code 125,539} cut to
         * {@code 125,5} (spec addendum §P4.C6 #4).
         */
        static String fitNumber(String cell, int width, ToIntFunction<String> textWidth) {
            int room = width + PADDING - 1;
            if (textWidth.applyAsInt(cell) <= room) return cell;
            Matcher m = DECIMALS.matcher(cell);
            if (m.find()) {
                // Rounded to a whole number, 10780.9 still reads 10781: less precise, never a different number.
                String whole = new BigDecimal(m.group()).setScale(0, RoundingMode.HALF_UP).toPlainString();
                String rounded = cell.substring(0, m.start()) + whole + cell.substring(m.end());
                if (textWidth.applyAsInt(rounded) <= room) return rounded;
            }
            return "#".repeat(Math.max(1, width / textWidth.applyAsInt("#")));
        }
    }
}

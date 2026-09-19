package io.github.edwardg117.renderable.ui.screen;

/** Tabs of the results screen and the column layout each one uses. */
public enum Tab {
    FRAMES("Frames", Columns.METRIC_VALUE),
    HITCHES("Hitches", Columns.HITCH),
    BLOCK_ENTITIES("Block Entities", Columns.RANKED),
    ENTITIES("Entities", Columns.RANKED);

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

    /**
     * Column headers, relative weights and alignment. {@link #layout(int)} turns weights into pixel offsets.
     * Kept as a nested class so enum constants can reference the constants without a forward-reference error.
     */
    public record Columns(String[] headers, int[] weights, boolean[] rightAlign) {
        static final Columns METRIC_VALUE = new Columns(
                new String[]{"metric", "value"},
                new int[]{1, 3},
                new boolean[]{false, false});
        static final Columns HITCH = new Columns(
                new String[]{"frame", "total ms", "split"},
                new int[]{2, 1, 7},
                new boolean[]{false, true, false});
        static final Columns RANKED = new Columns(
                new String[]{"name", "render us/f", "tick us/f", "share", "peak ms", "shadow", "verts/f", "calls"},
                new int[]{5, 1, 1, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true, true, true});

        /** Returns {x offsets, widths} for a row of the given pixel width. Each column keeps 4 px of padding. */
        public int[][] layout(int rowWidth) {
            int total = 0;
            for (int w : weights) total += w;
            int[] x = new int[headers.length];
            int[] w = new int[headers.length];
            int cursor = 0;
            for (int i = 0; i < headers.length; i++) {
                int px = rowWidth * weights[i] / total;
                x[i] = cursor;
                w[i] = Math.max(8, px - 4);
                cursor += px;
            }
            return new int[][]{x, w};
        }
    }
}

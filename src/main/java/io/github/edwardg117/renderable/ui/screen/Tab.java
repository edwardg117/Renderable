package io.github.edwardg117.renderable.ui.screen;

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
     * Column headers, relative weights and alignment. {@link #layout(int)} turns weights into pixel offsets.
     * Kept as a nested class so enum constants can reference the constants without a forward-reference error.
     */
    public record Columns(String[] headers, int[] weights, boolean[] rightAlign) {
        static final Columns METRIC_VALUE = new Columns(
                new String[]{"metric", "value"},
                new int[]{2, 3},
                new boolean[]{false, false});
        static final Columns HITCH = new Columns(
                new String[]{"frame", "total ms", "split"},
                new int[]{2, 1, 7},
                new boolean[]{false, true, false});
        static final Columns RANKED = new Columns(
                new String[]{"name", "render us/f", "tick us/f", "share", "peak ms", "shadow", "verts/f", "calls"},
                new int[]{5, 1, 1, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true, true, true});
        static final Columns PARTICLE = new Columns(
                new String[]{"class", "render us/f", "tick us/f", "share", "avg live", "verts/f"},
                new int[]{5, 1, 1, 1, 1, 1},
                new boolean[]{false, true, true, true, true, true});
        static final Columns FLYWHEEL = new Columns(
                new String[]{"visual class", "avg count", "share"},
                new int[]{6, 1, 1},
                new boolean[]{false, true, true});
        static final Columns BLOCK = new Columns(
                new String[]{"block", "mesh ms/s", "us/call", "calls"},
                new int[]{8, 2, 2, 2},
                new boolean[]{false, true, true, true});
        static final Columns SECTION = new Columns(
                new String[]{"section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms"},
                new int[]{6, 2, 2, 2, 2},
                new boolean[]{false, true, true, true, true});

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

package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.Ranking;

/**
 * What clicking a column header sorts by (spec §5.3). A tab's rows are either ranked entries or mesh entries,
 * so at most one of the two is set; {@link #NONE} marks a column that is not sortable, which is every column
 * of the Frames and Hitches tabs, and the Flywheel tab's {@code share} column.
 *
 * <p>The Flywheel tab's rows are neither: they are class names and average counts. It reuses
 * {@link Ranking.Sort#LABEL} for the class column and {@link Ranking.Sort#TOTAL} for the count, which
 * {@code Rows.flywheel} interprets directly.
 */
public record ColumnSort(Ranking.Sort ranked, Ranking.MeshSort mesh) {

    public ColumnSort {
        if (ranked != null && mesh != null) {
            throw new IllegalArgumentException("a column sorts one kind of row, not both");
        }
    }

    /** A column that cannot be sorted by. */
    public static final ColumnSort NONE = new ColumnSort(null, null);

    public static ColumnSort of(Ranking.Sort sort) {
        return new ColumnSort(sort, null);
    }

    public static ColumnSort of(Ranking.MeshSort sort) {
        return new ColumnSort(null, sort);
    }

    public boolean sortable() {
        return ranked != null || mesh != null;
    }

    /** True for a name column, which reads A to Z on its first click; a number reads largest first. */
    public boolean ascendingFirst() {
        return ranked == Ranking.Sort.LABEL || mesh == Ranking.MeshSort.LABEL;
    }
}

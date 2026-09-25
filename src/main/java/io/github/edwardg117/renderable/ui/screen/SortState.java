package io.github.edwardg117.renderable.ui.screen;

import java.util.EnumMap;

/**
 * Which column each results tab is sorted by, and in which direction (spec §5.3). Pure Java, so the rules a
 * header click follows are unit-tested rather than only checked by eye.
 */
final class SortState {
    private final EnumMap<Tab, Integer> column = new EnumMap<>(Tab.class);
    private final EnumMap<Tab, Boolean> ascending = new EnumMap<>(Tab.class);

    /** The column this tab is sorted by: the last one clicked, or the one the tab opens on. */
    int column(Tab tab) {
        return column.getOrDefault(tab, tab.columns().defaultSortColumn());
    }

    boolean ascending(Tab tab) {
        return ascending.getOrDefault(tab, Boolean.FALSE);
    }

    ColumnSort sort(Tab tab) {
        return tab.columns().get(column(tab)).sort();
    }

    /**
     * A click on column {@code col}'s header. The active column flips direction; a new column starts in its
     * natural direction, A to Z for a name and largest first for a number. A column that does not sort
     * changes nothing and returns false.
     */
    boolean click(Tab tab, int col) {
        ColumnSort s = tab.columns().get(col).sort();
        if (!s.sortable()) return false;
        if (col == column(tab)) {
            ascending.put(tab, !ascending(tab));
        } else {
            column.put(tab, col);
            ascending.put(tab, s.ascendingFirst());
        }
        return true;
    }
}

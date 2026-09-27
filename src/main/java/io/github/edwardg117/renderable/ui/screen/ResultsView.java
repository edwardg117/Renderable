package io.github.edwardg117.renderable.ui.screen;

import java.util.HashSet;
import java.util.Set;

/**
 * Where the reader is on the results screen (spec addendum §P5.D6): the tab, each tab's sort, the open groups,
 * the selected row and how far the list is scrolled. {@code ProfilerController} keeps one for the latest result,
 * so Highlight and Teleport, which close the screen, do not lose the reader's place; a new result gets a new
 * view, which opens on Advice. Render thread only.
 */
public final class ResultsView {
    /** The dimension the result was taken in: an entity row's, since entity keys record none (spec addendum §P5.D7). */
    final String dimension;
    Tab tab = Tab.ADVICE;
    final SortState sorts = new SortState();
    final Set<String> expanded = new HashSet<>();
    Row selected;
    double scroll;

    public ResultsView(String dimension) {
        this.dimension = dimension;
    }
}

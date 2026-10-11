package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.ProfileKey;
import net.minecraft.core.BlockPos;

/**
 * One list row. {@code group} rows are drawn brighter; {@code expandable} rows toggle their members on click;
 * {@code pos} or {@code entityId >= 0} enables Highlight and Teleport.
 */
public record Row(String[] cells,
                  ProfileKey key,
                  String typeId,
                  boolean group,
                  boolean expandable,
                  int indent,
                  BlockPos pos,
                  int entityId) {

    /**
     * What a screen reader says for this row: its non-empty cells, joined with ", ". A contributor's or a split
     * continuation's first cell is empty, so the first cell alone would leave the row unspoken.
     */
    public String narration() {
        if (cells.length == 1) return cells[0];
        StringBuilder sb = new StringBuilder();
        for (String cell : cells) {
            if (cell.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(cell);
        }
        return sb.toString();
    }

    public static Row plain(String... cells) {
        return new Row(cells, null, null, false, false, 0, null, -1);
    }

    public static Row heading(String text) {
        return new Row(new String[]{text}, null, null, true, false, 0, null, -1);
    }
}

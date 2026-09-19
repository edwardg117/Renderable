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

    public static Row plain(String... cells) {
        return new Row(cells, null, null, false, false, 0, null, -1);
    }

    public static Row heading(String text) {
        return new Row(new String[]{text}, null, null, true, false, 0, null, -1);
    }
}

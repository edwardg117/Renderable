package io.github.edwardg117.renderable.ui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Scrollable list of {@link Row}s with column layout supplied by the screen. */
public final class RowList extends ObjectSelectionList<RowList.Entry> {
    private final ResultsScreen screen;
    private Tab tab = Tab.FRAMES;
    private int[] colX = {0};
    private int[] colW = {100};

    public RowList(Minecraft mc, ResultsScreen screen, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
        this.screen = screen;
    }

    public void setLayout(int[] x, int[] w) {
        this.colX = x;
        this.colW = w;
    }

    public void setRows(Tab tab, List<Row> rows) {
        this.tab = tab;
        clearEntries();
        for (Row r : rows) addEntry(new Entry(r));
        setScrollAmount(0);
    }

    /** Selects the row with {@code r}'s key again after the rows were rebuilt; false if it is no longer listed. */
    boolean reselect(Row r) {
        if (r.key() == null) return false;
        for (Entry e : children()) {
            if (r.key().equals(e.row.key())) {
                setSelected(e);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getRowWidth() {
        return width - 20;
    }

    @Override
    protected int getScrollbarPosition() {
        return width - 8;
    }

    public final class Entry extends ObjectSelectionList.Entry<Entry> {
        final Row row;

        Entry(Row row) {
            this.row = row;
        }

        @Override
        public Component getNarration() {
            return Component.literal(row.cells()[0]);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            Font font = minecraft.font;
            int color = row.group() ? 0xFFFFFF : 0xC8C8C8;
            int indentPx = row.indent() * 12;
            Tab.Columns columns = tab.columns();

            String first = row.cells()[0];
            if (row.expandable()) first = (screen.isExpanded(row.typeId()) ? "v " : "> ") + first;
            // A single-cell row (a heading or full-width note) spans the whole row width instead of just column 0.
            int firstWidth = row.cells().length == 1 ? width - 8 - indentPx : colW[0] - indentPx;
            g.drawString(font, font.plainSubstrByWidth(first, firstWidth), left + 4 + indentPx, top + 2, color, false);

            for (int i = 1; i < row.cells().length && i < colX.length && i < columns.size(); i++) {
                int cx = left + 4 + colX[i];
                if (columns.get(i).rightAlign()) {
                    String cell = Tab.Columns.fitNumber(row.cells()[i], colW[i], font::width);
                    g.drawString(font, cell, cx + colW[i] - font.width(cell), top + 2, color, false);
                } else {
                    g.drawString(font, font.plainSubstrByWidth(row.cells()[i], colW[i]), cx, top + 2, color, false);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            screen.onRowClicked(row);
            return true;
        }
    }
}

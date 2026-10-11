package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.ProfilerController;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.ui.WorldOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Spec §5.3. */
public final class ResultsScreen extends Screen {
    private static final int TAB_Y = 4;
    private static final int TABS_PER_ROW = 5;
    private static final int TAB_ROW_HEIGHT = 22;
    private static final int SUMMARY_Y = 52;
    private static final int HEADER_Y = 74;
    private static final int LIST_Y = 86;
    private static final int BOTTOM_BAR = 32;
    private static final int ROW_HEIGHT = 12;

    private final SessionResult result;
    private final Environment environment;
    /** The reader's place, which outlives this screen (spec addendum §P5.D6). */
    private final ResultsView view;

    private RowList list;
    private Button highlightButton;
    private Button teleportButton;
    /** Shown on Highlight and Teleport while they are disabled because the player has logged out since the session. */
    private final Tooltip worldGoneTooltip =
            Tooltip.create(Component.literal("You have left the world this session was taken in"));
    private int[] colX = {0};
    private int[] colW = {100};

    public ResultsScreen(SessionResult result, Environment environment, ResultsView view) {
        super(Component.literal("Renderable results"));
        this.result = result;
        this.environment = environment;
        this.view = view;
    }

    @Override
    protected void init() {
        // A resize runs init again with the list in place; opening the screen starts where the view left off.
        double scroll = list != null ? list.getScrollAmount() : view.scroll;
        int tabPitch = Math.min(88, (width - 8) / TABS_PER_ROW);   // 4 px margin each side
        int tabWidth = tabPitch - 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = 4 + (i % TABS_PER_ROW) * tabPitch;
            int y = TAB_Y + (i / TABS_PER_ROW) * TAB_ROW_HEIGHT;
            Button b = Button.builder(Component.literal(t.title()), btn -> selectTab(t)).bounds(x, y, tabWidth, 20).build();
            b.active = t != view.tab;
            addRenderableWidget(b);
        }
        list = new RowList(minecraft, this, width, height - LIST_Y - BOTTOM_BAR, LIST_Y, ROW_HEIGHT);
        addRenderableWidget(list);

        int by = height - 26;
        int bw = Math.min(90, (width - 24) / 5);   // five buttons, four 4 px gaps, 4 px margins
        int bx = 4;
        addRenderableWidget(Button.builder(Component.literal("Copy report"), b -> ProfilerController.get().copyReport())
                .bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> ProfilerController.get().openFolder())
                .bounds(bx, by, bw, 20).build());
        bx += bw + 4;
        highlightButton = Button.builder(Component.literal("Highlight"), b -> highlightSelected())
                .bounds(bx, by, bw, 20).build();
        bx += bw + 4;
        teleportButton = Button.builder(Component.literal("Teleport"), b -> teleportToSelected())
                .bounds(bx, by, bw, 20).build();
        bx += bw + 4;
        addRenderableWidget(highlightButton);
        addRenderableWidget(teleportButton);
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(bx, by, bw, 20).build());

        rebuild();
        list.setScrollAmount(scroll);
    }

    /** A new tab starts at the top, with nothing selected. */
    private void selectTab(Tab t) {
        view.tab = t;
        view.selected = null;
        view.scroll = 0;
        list = null;   // so init reads the view's scroll, not this list's
        rebuildWidgets();
    }

    /** Keeps the scroll for the next time the screen opens on this result (spec addendum §P5.D6). */
    @Override
    public void removed() {
        if (list != null) view.scroll = list.getScrollAmount();
    }

    private void rebuild() {
        Tab tab = view.tab;
        int arrow = Math.max(font.width(" ^"), font.width(" v"));
        int[][] layout = tab.columns().layout(list.getRowWidth(), font::width, arrow);
        colX = layout[0];
        colW = layout[1];
        list.setLayout(colX, colW);
        // A body line is drawn two indents (24 px) in, and a single-cell row loses 8 px of margin.
        int wrapWidth = Math.max(40, list.getRowWidth() - 8 - 24);
        // The Hitches tab's split is its last column; a long one continues on rows under its hitch.
        int splitWidth = Math.max(40, colW[colW.length - 1]);
        list.setRows(tab, Rows.build(tab, result, environment, view.sorts.sort(tab), view.sorts.ascending(tab),
                view.expanded, text -> TextWrap.wrap(text, wrapWidth, font::width),
                text -> TextWrap.wrapSplit(text, splitWidth, font::width)));
        if (view.selected != null && !list.reselect(view.selected)) view.selected = null;
        updateButtons();
    }

    boolean isExpanded(String typeId) {
        return typeId != null && view.expanded.contains(typeId);
    }

    void onRowClicked(Row row) {
        if (row.expandable()) {
            if (!view.expanded.add(row.typeId())) view.expanded.remove(row.typeId());
            double scroll = list.getScrollAmount();
            rebuild();
            list.setScrollAmount(scroll);   // opening a group should not jump the list back to the top
            return;
        }
        view.selected = row;
        updateButtons();
    }

    /** A click in the header band sorts by that column; see {@link SortState#click}. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= HEADER_Y - 2 && mouseY < HEADER_Y + font.lineHeight) {
            int clicked = columnAt(mouseX);
            if (clicked >= 0 && view.sorts.click(view.tab, clicked)) {
                rebuild();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * The column index under a screen x, or -1 outside the row. A right-aligned header may start up to
     * {@code PADDING - 1} px left of its column, as {@link #render} draws it, so that column's area starts there too.
     */
    private int columnAt(double mouseX) {
        int left = list.getRowLeft() + 4;
        if (mouseX < left || mouseX >= left + list.getRowWidth()) return -1;
        Tab.Columns columns = view.tab.columns();
        for (int i = colX.length - 1; i >= 0; i--) {
            int reach = columns.get(i).rightAlign() ? Tab.Columns.PADDING - 1 : 0;
            if (mouseX >= left + colX[i] - reach) return i;
        }
        return -1;
    }

    /**
     * Highlight and Teleport need a position or an entity, and the selected row's dimension must be the player's
     * (spec addendum §P5.D7). After a logout nothing in the result points at anything in the world.
     */
    private void updateButtons() {
        Row selected = view.selected;
        boolean worldGone = ProfilerController.get().resultWorldGone();
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        String here = minecraft != null && minecraft.level != null ? minecraft.level.dimension().location().toString() : "";
        String there = selected == null ? here : dimensionOf(selected);
        boolean elsewhere = !there.equals(here);
        highlightButton.active = hasPos && !worldGone && !elsewhere;
        teleportButton.active = (hasPos || hasEntity) && !worldGone && !elsewhere;
        Tooltip why = worldGone ? worldGoneTooltip
                : elsewhere && (hasPos || hasEntity)
                ? Tooltip.create(Component.literal("This row is in " + there + "; you are in " + here))
                : null;
        highlightButton.setTooltip(why);
        teleportButton.setTooltip(why);
    }

    /** The dimension a row was measured in: its key's own, or for an entity the session's (spec addendum §P5.D7). */
    private String dimensionOf(Row row) {
        String own = row.key() == null ? null : row.key().dimension();
        return own != null ? own : view.dimension;
    }

    /** Highlights the selected row's block, or the full section for a Sections-tab row, in the row's dimension. */
    private void highlightSelected() {
        Row selected = view.selected;
        if (selected == null || selected.pos() == null) return;
        if (selected.key() instanceof SectionKey k) {
            WorldOverlay.highlight(new BlockPos(k.x() * 16, k.y() * 16, k.z() * 16), 16, k.dimension());
        } else {
            WorldOverlay.highlight(selected.pos(), dimensionOf(selected));
        }
        onClose();
    }

    /**
     * Sends a teleport for the selected row. A section row's stored position is the section's centre block,
     * which is solid ground for anything below the surface, so section rows keep the player's height, raised
     * until the player fits (spec addendum §P4.C3); block entity rows go one block above the block.
     */
    private void teleportToSelected() {
        Row selected = view.selected;
        if (selected == null || minecraft == null || minecraft.getConnection() == null) return;
        if (selected.pos() != null) {
            BlockPos p = selected.pos();
            int y = selected.key() instanceof SectionKey && minecraft.player != null && minecraft.level != null
                    ? standingY(minecraft.level, minecraft.player, p.getX(), p.getZ())
                    : p.getY() + 1;
            minecraft.getConnection().sendCommand("tp @s " + p.getX() + " " + y + " " + p.getZ());
        } else if (selected.entityId() >= 0 && minecraft.level != null) {
            Entity e = minecraft.level.getEntity(selected.entityId());
            if (e == null) {
                ProfilerController.chat("Renderable: that entity is no longer loaded.");
                return;
            }
            Vec3 v = e.position();
            minecraft.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f", v.x, v.y, v.z));
        }
        onClose();
    }

    /**
     * The height a section-row teleport lands at: the player's own, raised to the first height at which the
     * player fits in the section's column, so it never ends inside a hill (spec addendum §P4.C3). {@code /tp}
     * centres whole-number X and Z in their block, so that is where the player's box is tested. A column the
     * client has not loaded has no collision shapes, so it keeps the player's height.
     */
    private static int standingY(ClientLevel level, LocalPlayer player, int x, int z) {
        int y = player.blockPosition().getY();
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(x + 0.5, y, z + 0.5);
        for (int up = 0; y + up < level.getMaxBuildHeight(); up++) {
            if (level.noBlockCollision(player, box.move(0, up, 0))) return y + up;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        String summary = String.format(Locale.ROOT,
                "%d frames | %s s | avg %s ms | p99 %s ms | %d hitches over %s ms | %s",
                result.frameCount(), Fmt.secs(result.stats().totalNanos()), Fmt.ms(result.stats().avgNanos()),
                Fmt.ms(result.stats().p99Nanos()), result.hitches().size(), Fmt.ms(result.hitchThresholdNanos()),
                environment.entries().getOrDefault("gpu", ""));
        g.drawString(font, font.plainSubstrByWidth(summary, width - 8), 4, SUMMARY_Y, 0xE0E0E0, false);

        int left = list.getRowLeft();
        Tab tab = view.tab;
        Tab.Columns columns = tab.columns();
        int active = view.sorts.column(tab);
        for (int i = 0; i < columns.size() && i < colX.length; i++) {
            Tab.Column column = columns.get(i);
            boolean sortable = column.sort().sortable();
            String arrow = i == active && sortable ? (view.sorts.ascending(tab) ? " ^" : " v") : "";
            // A right-aligned header may reach PADDING - 1 px left, as a number may; the layout widened its column
            // for the header and an arrow where it could (spec addendum §P5.D9). Truncate the name, never the arrow:
            // the arrow is the only sign of how the list is sorted.
            int room = colW[i] + (column.rightAlign() ? Tab.Columns.PADDING - 1 : 0);
            String h = font.plainSubstrByWidth(column.name(), Math.max(0, room - font.width(arrow))) + arrow;
            int colour = !sortable ? 0xA0A0A0 : i == active ? 0xFFFFFF : 0xC0C0C0;
            int cx = left + 4 + colX[i];
            if (column.rightAlign()) {
                g.drawString(font, h, cx + colW[i] - font.width(h), HEADER_Y, colour, false);
            } else {
                g.drawString(font, h, cx, HEADER_Y, colour, false);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.ProfilerController;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.ui.WorldOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Spec §5.3. */
public final class ResultsScreen extends Screen {
    private static final int TAB_Y = 4;
    private static final int TABS_PER_ROW = 4;
    private static final int TAB_ROW_HEIGHT = 22;
    private static final int SUMMARY_Y = 52;
    private static final int HEADER_Y = 74;
    private static final int LIST_Y = 86;
    private static final int BOTTOM_BAR = 32;
    private static final int ROW_HEIGHT = 12;

    private final SessionResult result;
    private final Environment environment;

    private Tab tab = Tab.FRAMES;
    private Ranking.Sort sort = Ranking.Sort.TOTAL;
    private final Set<String> expanded = new HashSet<>();
    private Row selected;

    private RowList list;
    private Button sortButton;
    private Button highlightButton;
    private Button teleportButton;
    private int[] colX = {0};
    private int[] colW = {100};

    public ResultsScreen(SessionResult result, Environment environment) {
        super(Component.literal("Renderable results"));
        this.result = result;
        this.environment = environment;
    }

    @Override
    protected void init() {
        int tabPitch = Math.min(88, (width - 122) / TABS_PER_ROW);   // 4 left margin + 4 gap + 110 sort + 4 right margin
        int tabWidth = tabPitch - 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = 4 + (i % TABS_PER_ROW) * tabPitch;
            int y = TAB_Y + (i / TABS_PER_ROW) * TAB_ROW_HEIGHT;
            Button b = Button.builder(Component.literal(t.title()), btn -> selectTab(t)).bounds(x, y, tabWidth, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }
        sortButton = Button.builder(sortLabel(), b -> {
            sort = sort.next();
            b.setMessage(sortLabel());
            rebuild();
        }).bounds(width - 4 - 110, TAB_Y, 110, 20).build();
        addRenderableWidget(sortButton);

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
    }

    private void selectTab(Tab t) {
        tab = t;
        selected = null;
        rebuildWidgets();
    }

    private void rebuild() {
        int[][] layout = tab.columns().layout(list.getRowWidth());
        colX = layout[0];
        colW = layout[1];
        list.setLayout(colX, colW);
        list.setRows(tab, Rows.build(tab, result, sort, expanded));
        updateButtons();
    }

    private Component sortLabel() {
        return Component.literal("Sort: " + sort.name().toLowerCase(Locale.ROOT));
    }

    boolean isExpanded(String typeId) {
        return typeId != null && expanded.contains(typeId);
    }

    void onRowClicked(Row row) {
        if (row.expandable()) {
            if (!expanded.add(row.typeId())) expanded.remove(row.typeId());
            rebuild();
            return;
        }
        selected = row;
        updateButtons();
    }

    private void updateButtons() {
        boolean worldGone = ProfilerController.get().resultWorldGone();
        boolean hasPos = selected != null && selected.pos() != null;
        boolean hasEntity = selected != null && selected.entityId() >= 0;
        highlightButton.active = hasPos && !worldGone;
        teleportButton.active = (hasPos || hasEntity) && !worldGone;
        sortButton.visible = tab.sortable();
    }

    /** Highlights the selected row's block, or the full section for a Sections-tab row. */
    private void highlightSelected() {
        if (selected == null || selected.pos() == null) return;
        if (selected.key() instanceof SectionKey k) {
            WorldOverlay.highlight(new BlockPos(k.x() * 16, k.y() * 16, k.z() * 16), 16);
        } else {
            WorldOverlay.highlight(selected.pos());
        }
        onClose();
    }

    private void teleportToSelected() {
        if (selected == null || minecraft == null || minecraft.getConnection() == null) return;
        if (selected.pos() != null) {
            BlockPos p = selected.pos();
            minecraft.getConnection().sendCommand("tp @s " + p.getX() + " " + (p.getY() + 1) + " " + p.getZ());
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
        String[] headers = tab.columns().headers();
        boolean[] right = tab.columns().rightAlign();
        for (int i = 0; i < headers.length && i < colX.length; i++) {
            int cx = left + 4 + colX[i];
            String h = font.plainSubstrByWidth(headers[i], colW[i]);
            if (right[i]) {
                g.drawString(font, h, cx + colW[i] - font.width(h), HEADER_Y, 0xA0A0A0, false);
            } else {
                g.drawString(font, h, cx, HEADER_Y, 0xA0A0A0, false);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package io.github.edwardg117.renderable.ui;

import io.github.edwardg117.renderable.RenderableConfig;
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.MeshEntry;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cost labels for the last finished session plus a temporary highlight (spec §5.4).
 * <p>
 * Both are projected to screen space and drawn as a GUI layer so they sit on top of everything the world
 * renderer and any shader pipeline produce; an obstructed machine is still findable, as in Observable.
 * The view-projection matrix and camera are captured from the level render each frame (game bus) and
 * consumed by the GUI layer (mod bus).
 */
public final class WorldOverlay {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("renderable", "labels");
    private static final long HIGHLIGHT_MILLIS = 10_000;
    private static final int BACKGROUND = 0x90000000;
    private static final int HIGHLIGHT_COLOR = 0xFFFF4D4D;
    private static final int MIN_HIGHLIGHT = 8;

    /**
     * One label, built once per result. Block entities and sections carry a fixed position; entities are looked up
     * per frame by id. A null dimension means the label is drawn in any dimension (entities, whose ids are resolved
     * live); otherwise it is drawn only in the dimension it was profiled in.
     */
    private record Label(String dimension, double x, double y, double z, int entityId, String text, int color) {
    }

    private static List<Label> labels = List.of();
    private static boolean enabled = true;

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static boolean haveMatrices;
    private static ResourceKey<Level> lastDimensionKey;
    private static String lastDimension = "";

    private static BlockPos highlight;
    private static long highlightUntilMillis;

    private WorldOverlay() {
    }

    /** Mod bus. */
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, WorldOverlay::render);
    }

    public static void setResult(SessionResult r) {
        int n = RenderableConfig.OVERLAY_LABEL_COUNT.get();
        long sessionTotal = r.stats().totalNanos();
        int frames = r.frameCount();
        Set<ProfileKey> flagged = r.hitchFlaggedKeys();
        List<Label> out = new ArrayList<>(3 * n);
        for (RankedEntry e : head(Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), n)) {
            BlockEntityKey k = (BlockEntityKey) e.key();
            out.add(label(e, flagged, frames, sessionTotal, k.dimension(), k.x() + 0.5, k.y() + 1.25, k.z() + 0.5, -1));
        }
        for (RankedEntry e : head(Ranking.byKey(r, ProfileKey.Category.ENTITY), n)) {
            EntityKey k = (EntityKey) e.key();
            out.add(label(e, flagged, frames, sessionTotal, null, 0, 0, 0, k.entityId()));
        }
        for (MeshEntry e : head(Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS), n)) {
            SectionKey k = (SectionKey) e.key();
            String text = String.format(Locale.ROOT, "section %d %d %d: %d rebuilds, %s ms",
                    k.x(), k.y(), k.z(), e.calls(), Fmt.ms(e.nanos()));
            out.add(new Label(k.dimension(), k.centerX() + 0.5, k.centerY() + 0.5, k.centerZ() + 0.5, -1, text, Colors.YELLOW));
        }
        labels = List.copyOf(out);
        enabled = RenderableConfig.OVERLAY_ENABLED.get();
    }

    private static Label label(RankedEntry e, Set<ProfileKey> flagged, int frames, long sessionTotal,
                               String dimension, double x, double y, double z, int entityId) {
        boolean isFlagged = flagged.contains(e.key());
        String text = (isFlagged ? "! " : "") + e.typeId() + " " + Fmt.us(e.usPerFrame(frames)) + " us";
        int color = isFlagged ? Colors.FLAGGED : Colors.heat(e.sharePercent(sessionTotal));
        return new Label(dimension, x, y, z, entityId, text, color);
    }

    public static void clear() {
        labels = List.of();
    }

    /** Entity ids and dimension names restart in the next world, so a result's labels do not outlive the connection. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    /** Draw a red rectangle around the block on screen for ten seconds. */
    public static void highlight(BlockPos pos) {
        highlight = pos.immutable();
        highlightUntilMillis = System.currentTimeMillis() + HIGHLIGHT_MILLIS;
    }

    /** Game bus: captures the camera and view-projection matrix whenever there is something to draw. */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        boolean wantLabels = enabled && !labels.isEmpty();
        if ((!wantLabels && highlight == null) || Minecraft.getInstance().level == null) {
            haveMatrices = false;
            return;
        }
        VIEW_PROJECTION.set(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        cameraPos = event.getCamera().getPosition();
        haveMatrices = true;
    }

    /** GUI layer: projects labels and the highlight to the screen and draws them over everything. */
    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!haveMatrices) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        Vec3 cam = cameraPos;
        int guiW = g.guiWidth();
        int guiH = g.guiHeight();

        if (enabled && !labels.isEmpty()) {
            drawLabels(g, mc, cam, guiW, guiH);
        }
        drawHighlight(g, cam, guiW, guiH);
    }

    private static void drawLabels(GuiGraphics g, Minecraft mc, Vec3 cam, int guiW, int guiH) {
        int maxDist = RenderableConfig.OVERLAY_MAX_DISTANCE.get();
        double maxDistSq = (double) maxDist * maxDist;
        int lineH = mc.font.lineHeight;

        ResourceKey<Level> dimKey = mc.level.dimension();
        if (dimKey != lastDimensionKey) {
            lastDimensionKey = dimKey;
            lastDimension = dimKey.location().toString();
        }

        for (Label l : labels) {
            if (l.dimension() != null && !l.dimension().equals(lastDimension)) continue;
            double x;
            double y;
            double z;
            if (l.entityId() >= 0) {
                Entity ent = mc.level.getEntity(l.entityId());
                if (ent == null || ent == mc.getCameraEntity()) continue;
                Vec3 p = ent.position();
                x = p.x;
                y = p.y + ent.getBbHeight() + 0.5;
                z = p.z;
            } else {
                x = l.x();
                y = l.y();
                z = l.z();
            }
            if (cam.distanceToSqr(x, y, z) > maxDistSq) continue;

            SCRATCH.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0f).mul(VIEW_PROJECTION);
            float w = SCRATCH.w();
            if (w <= 0.001f) continue; // behind the camera
            float nx = SCRATCH.x() / w;
            float ny = SCRATCH.y() / w;
            if (nx < -1.1f || nx > 1.1f || ny < -1.1f || ny > 1.1f) continue;
            int sx = Math.round((nx + 1.0f) * 0.5f * guiW);
            int sy = Math.round((1.0f - ny) * 0.5f * guiH);

            int tw = mc.font.width(l.text());
            int left = sx - tw / 2;
            g.fill(left - 2, sy - 1, left + tw + 2, sy + lineH, BACKGROUND);
            g.drawString(mc.font, l.text(), left, sy, l.color(), false);
        }
    }

    /** Projects the highlighted block's eight corners and outlines their screen bounds, two pixels thick, never smaller than MIN_HIGHLIGHT. */
    private static void drawHighlight(GuiGraphics g, Vec3 cam, int guiW, int guiH) {
        BlockPos p = highlight;
        if (p == null) return;
        if (System.currentTimeMillis() > highlightUntilMillis) {
            highlight = null;
            return;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = p.getX() + ((i & 1) == 0 ? -0.01 : 1.01);
            double y = p.getY() + ((i & 2) == 0 ? -0.01 : 1.01);
            double z = p.getZ() + ((i & 4) == 0 ? -0.01 : 1.01);
            SCRATCH.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0f).mul(VIEW_PROJECTION);
            float w = SCRATCH.w();
            if (w <= 0.001f) return; // a corner is behind the camera; nothing sensible to draw this frame
            float sx = (SCRATCH.x() / w + 1.0f) * 0.5f * guiW;
            float sy = (1.0f - SCRATCH.y() / w) * 0.5f * guiH;
            minX = Math.min(minX, sx);
            maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy);
            maxY = Math.max(maxY, sy);
        }
        // A distant block projects to a few pixels; grow the marker to at least MIN_HIGHLIGHT around its centre.
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float halfW = Math.max((maxX - minX) * 0.5f + 1.0f, MIN_HIGHLIGHT * 0.5f);
        float halfH = Math.max((maxY - minY) * 0.5f + 1.0f, MIN_HIGHLIGHT * 0.5f);
        int x0 = Math.round(cx - halfW);
        int y0 = Math.round(cy - halfH);
        int x1 = Math.round(cx + halfW);
        int y1 = Math.round(cy + halfH);
        if (x1 <= 0 || y1 <= 0 || x0 >= guiW || y0 >= guiH) return; // entirely off screen
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(guiW, x1);
        y1 = Math.min(guiH, y1);
        if (x1 - x0 < 4 || y1 - y0 < 4) return; // only a sliver remains at the screen edge
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, HIGHLIGHT_COLOR);
        g.renderOutline(x0 + 1, y0 + 1, x1 - x0 - 2, y1 - y0 - 2, HIGHLIGHT_COLOR);
    }

    private static <T> List<T> head(List<T> list, int n) {
        return list.size() <= n ? list : List.copyOf(list.subList(0, n));
    }
}

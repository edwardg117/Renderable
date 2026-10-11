package io.github.edwardg117.renderable.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.edwardg117.renderable.RenderableConfig;
import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.MeshEntry;
import io.github.edwardg117.renderable.core.OverlaySwitch;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
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
import org.joml.Vector3f;
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
 * consumed by the GUI layer (mod bus). Labels and the highlight are each drawn only in the dimension they
 * belong to (spec addendum §P5.D7).
 */
public final class WorldOverlay {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("renderable", "labels");
    private static final long HIGHLIGHT_MILLIS = 10_000;
    private static final int BACKGROUND = 0x90000000;
    private static final int HIGHLIGHT_COLOR = 0xFFFF4D4D;
    private static final int MIN_HIGHLIGHT = 8;
    /**
     * GUI depth between one label and the next, so that where two overlap the later one covers the earlier one's
     * text, though every background is drawn before any text (spec addendum §P5.D8). The highlight goes one step
     * above the last label. Seventy-five labels, the default, span under four units; 600, the config's most, span
     * 30. The whole stack must stay inside the 200 units NeoForge gives each GUI layer
     * ({@code GuiLayerManager.Z_SEPARATION}).
     */
    private static final float LABEL_DEPTH_STEP = 0.05f;

    /**
     * One label, built once per result. Block entities and sections carry the position they were profiled
     * at; entities are looked up per frame by id. Every label is drawn only in the dimension it was measured
     * in; for an entity that is the session's dimension, which matters for an entity that keeps its id
     * through a portal, such as another player.
     */
    private record Label(String dimension, double x, double y, double z, int entityId, String text, int color) {
    }

    private static List<Label> labels = List.of();
    /** Where this frame's labels go, in drawing order. Sized with the labels, so a frame allocates nothing. */
    private static int[] drawnLabel = new int[0];
    private static int[] drawnX = new int[0];
    private static int[] drawnY = new int[0];
    /** Labels on or off: the {@code overlayEnabled} setting itself, read every frame (spec addendum §P6.13). */
    private static final OverlaySwitch SWITCH = new OverlaySwitch(new OverlaySwitch.Setting() {
        @Override
        public boolean loaded() {
            return RenderableConfig.SPEC.isLoaded();
        }

        @Override
        public boolean get() {
            return RenderableConfig.OVERLAY_ENABLED.get();
        }

        @Override
        public void set(boolean value) {
            RenderableConfig.OVERLAY_ENABLED.set(value);
            try {
                RenderableConfig.OVERLAY_ENABLED.save();
            } catch (RuntimeException e) {
                // The labels still switch for this run; only the file keeps its old value.
                RenderableMod.LOGGER.warn("Could not save overlayEnabled to renderable-client.toml", e);
            }
        }
    });

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    /** The GUI pose moved to one label's depth; reused every frame, render thread only. */
    private static final Matrix4f LABEL_POSE = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static boolean haveMatrices;
    private static ResourceKey<Level> lastDimensionKey;
    private static String lastDimension = "";

    private static BlockPos highlight;
    private static int highlightSize = 1;
    /** The dimension of the row the highlight came from; it is drawn only there. */
    private static String highlightDimension = "";
    private static long highlightUntilMillis;

    private WorldOverlay() {
    }

    /** Mod bus. */
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, WorldOverlay::render);
    }

    /** {@code dimension} is the dimension the session was taken in; every label is scoped to it. */
    public static void setResult(SessionResult r, String dimension) {
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
            out.add(label(e, flagged, frames, sessionTotal, dimension, 0, 0, 0, k.entityId()));
        }
        for (MeshEntry e : head(Ranking.mesh(r, ProfileKey.Category.SECTION, Ranking.MeshSort.CALLS), n)) {
            SectionKey k = (SectionKey) e.key();
            String text = String.format(Locale.ROOT, "section %d %d %d: %d rebuilds, %s ms",
                    k.x(), k.y(), k.z(), e.calls(), Fmt.ms(e.nanos()));
            out.add(new Label(k.dimension(), k.centerX() + 0.5, k.centerY() + 0.5, k.centerZ() + 0.5, -1, text, Colors.YELLOW));
        }
        labels = List.copyOf(out);
        drawnLabel = new int[labels.size()];
        drawnX = new int[labels.size()];
        drawnY = new int[labels.size()];
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
        highlight = null;
    }

    /**
     * Whether labels are drawn: the {@code overlayEnabled} setting as it is now, so a change in the mod's config
     * screen shows at once (spec addendum §P6.13, replacing §P4.B6's copy taken on first use).
     */
    public static boolean isEnabled() {
        return SWITCH.enabled();
    }

    /** Turns labels on or off by writing {@code overlayEnabled}, so the choice lasts across sessions and restarts. */
    public static void setEnabled(boolean on) {
        SWITCH.set(on);
    }

    public static void toggle() {
        SWITCH.toggle();
    }

    /** Draw a wireframe box around the block, in {@code dimension} only, for ten seconds. */
    public static void highlight(BlockPos pos, String dimension) {
        highlight(pos, 1, dimension);
    }

    /**
     * Draw a wireframe box around the {@code size}-block cube whose minimum corner is {@code min}, for ten seconds,
     * in {@code dimension} only: the dimension of the row it came from (spec addendum §P5.D7).
     */
    public static void highlight(BlockPos min, int size, String dimension) {
        highlight = min.immutable();
        highlightSize = Math.max(1, size);
        highlightDimension = dimension;
        highlightUntilMillis = System.currentTimeMillis() + HIGHLIGHT_MILLIS;
    }

    /** Game bus: captures the camera and view-projection matrix whenever there is something to draw. */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        boolean wantLabels = isEnabled() && !labels.isEmpty();
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
        ResourceKey<Level> dimKey = mc.level.dimension();
        if (dimKey != lastDimensionKey) {
            lastDimensionKey = dimKey;
            lastDimension = dimKey.location().toString();
        }
        Vec3 cam = cameraPos;
        int guiW = g.guiWidth();
        int guiH = g.guiHeight();

        float highlightDepth = 0;
        if (isEnabled() && !labels.isEmpty()) {
            highlightDepth = drawLabels(g, mc, cam, guiW, guiH);
        }
        drawHighlight(g, cam, guiW, guiH, highlightDepth);
    }

    /**
     * Draws the labels in two batches (spec addendum §P5.D8): every background, then every text. Drawn a label at
     * a time, each background and each text was a batch of its own, 150 draws for 75 labels, because the GUI's fill
     * and text render types share one buffer and every switch between them ends a batch; {@code drawManaged} alone
     * does not change that. The buffer source is written to directly, as {@link #drawHighlight} does. Each label
     * is drawn at its own depth, so where two overlap the later one hides the earlier one's text; drawn in turn,
     * its translucent background only dimmed it.
     *
     * @return the depth one step above the last label drawn, for the highlight; 0 when no label is in view
     */
    private static float drawLabels(GuiGraphics g, Minecraft mc, Vec3 cam, int guiW, int guiH) {
        int maxDist = RenderableConfig.OVERLAY_MAX_DISTANCE.get();
        double maxDistSq = (double) maxDist * maxDist;
        int lineH = mc.font.lineHeight;
        Matrix4f pose = g.pose().last().pose();
        List<Label> all = labels;

        VertexConsumer backgrounds = null; // opened at the first label in view, so a frame with none opens nothing
        int drawn = 0;
        for (int i = 0; i < all.size(); i++) {
            Label l = all.get(i);
            if (!lastDimension.equals(l.dimension())) continue;
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
            if (backgrounds == null) backgrounds = g.bufferSource().getBuffer(RenderType.gui());
            quad(backgrounds, pose, left - 2, sy - 1, left + tw + 2, sy + lineH, drawn * LABEL_DEPTH_STEP, BACKGROUND);
            drawnLabel[drawn] = i;
            drawnX[drawn] = left;
            drawnY[drawn] = sy;
            drawn++;
        }
        if (drawn == 0) return 0;
        // Asking for the text's render type ends the backgrounds' batch; the flush below ends the text's.
        for (int d = 0; d < drawn; d++) {
            Label l = all.get(drawnLabel[d]);
            LABEL_POSE.set(pose).translate(0, 0, d * LABEL_DEPTH_STEP);
            mc.font.drawInBatch(l.text(), drawnX[d], drawnY[d], l.color(), false, LABEL_POSE, g.bufferSource(),
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        g.flush();
        return drawn * LABEL_DEPTH_STEP;
    }

    /**
     * Scratch space for {@link #drawHighlight}, reused every frame; render thread only, like {@link #SCRATCH}.
     * A highlight can outlive the session it came from and keep drawing while the next one samples, so
     * Renderable's own code allocates nothing per frame.
     */
    private static final float[] CLIP_X = new float[8];
    private static final float[] CLIP_Y = new float[8];
    private static final float[] CLIP_W = new float[8];
    private static final Vector3f VERTEX = new Vector3f();

    /** The camera's near plane in clip space: a point is in front of it when its w exceeds this. */
    private static final float NEAR_W = GameRenderer.PROJECTION_Z_NEAR;

    /** The twelve edges of a cube, as pairs of corner indices differing in one bit. */
    private static final int[] EDGES = {
            0, 1, 0, 2, 0, 4, 1, 3, 1, 5, 2, 3,
            2, 6, 3, 7, 4, 5, 4, 6, 5, 7, 6, 7};

    /**
     * Projects the highlighted cube's eight corners and draws its twelve edges over everything (spec
     * addendum §P4.C5), in the highlight's dimension only. An edge that crosses the camera's near plane is
     * clipped at that plane, so a box the camera stands inside still shows its edges. Each edge is drawn as one
     * two-pixel-wide quad, and the whole box is one flush. A wholly visible cube that projects smaller than
     * {@link #MIN_HIGHLIGHT} falls back to a small square instead, so a distant block stays findable. It is drawn
     * at {@code depth}, above every label, as when it was drawn after them. Renderable's own code allocates
     * nothing per frame.
     */
    private static void drawHighlight(GuiGraphics g, Vec3 cam, int guiW, int guiH, float depth) {
        BlockPos p = highlight;
        if (p == null) return;
        if (System.currentTimeMillis() > highlightUntilMillis) {
            highlight = null;
            return;
        }
        if (!lastDimension.equals(highlightDimension)) return;
        int inFront = 0;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = p.getX() + ((i & 1) == 0 ? -0.01 : highlightSize + 0.01);
            double y = p.getY() + ((i & 2) == 0 ? -0.01 : highlightSize + 0.01);
            double z = p.getZ() + ((i & 4) == 0 ? -0.01 : highlightSize + 0.01);
            SCRATCH.set((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0f).mul(VIEW_PROJECTION);
            CLIP_X[i] = SCRATCH.x();
            CLIP_Y[i] = SCRATCH.y();
            CLIP_W[i] = SCRATCH.w();
            if (CLIP_W[i] <= NEAR_W) continue;
            inFront++;
            float sx = screenX(CLIP_X[i], CLIP_W[i], guiW);
            float sy = screenY(CLIP_Y[i], CLIP_W[i], guiH);
            minX = Math.min(minX, sx);
            maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy);
            maxY = Math.max(maxY, sy);
        }
        if (inFront == 0) return; // entirely behind the camera

        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f pose = g.pose().last().pose();
        if (inFront == 8 && maxX - minX < MIN_HIGHLIGHT && maxY - minY < MIN_HIGHLIGHT) {
            // Only an unclipped cube can be distant. It projects to a few pixels, so mark it with the
            // MIN_HIGHLIGHT square instead; centre lines 1 px inside, so the 2 px lines end flush with it.
            float cx = (minX + maxX) * 0.5f;
            float cy = (minY + maxY) * 0.5f;
            float h = MIN_HIGHLIGHT * 0.5f - 1.0f;
            line(vc, pose, cx - h, cy - h, cx + h, cy - h, depth);
            line(vc, pose, cx + h, cy - h, cx + h, cy + h, depth);
            line(vc, pose, cx + h, cy + h, cx - h, cy + h, depth);
            line(vc, pose, cx - h, cy + h, cx - h, cy - h, depth);
        } else {
            for (int e = 0; e < EDGES.length; e += 2) {
                edge(vc, pose, EDGES[e], EDGES[e + 1], guiW, guiH, depth);
            }
        }
        g.flush(); // what fill() does after every rectangle, once for the whole box
    }

    /**
     * One edge of the cube. An endpoint behind the near plane is moved along the edge onto it; the move is
     * made in clip space, where the edge is still a straight line, so the part in front is exact.
     */
    private static void edge(VertexConsumer vc, Matrix4f pose, int a, int b, int guiW, int guiH, float depth) {
        float ax = CLIP_X[a];
        float ay = CLIP_Y[a];
        float aw = CLIP_W[a];
        float bx = CLIP_X[b];
        float by = CLIP_Y[b];
        float bw = CLIP_W[b];
        if (aw <= NEAR_W && bw <= NEAR_W) return;
        if (aw <= NEAR_W) {
            float t = (bw - NEAR_W) / (bw - aw);
            ax = bx + t * (ax - bx);
            ay = by + t * (ay - by);
            aw = NEAR_W;
        } else if (bw <= NEAR_W) {
            float t = (aw - NEAR_W) / (aw - bw);
            bx = ax + t * (bx - ax);
            by = ay + t * (by - ay);
            bw = NEAR_W;
        }
        line(vc, pose, screenX(ax, aw, guiW), screenY(ay, aw, guiH), screenX(bx, bw, guiW), screenY(by, bw, guiH),
                depth);
    }

    private static float screenX(float clipX, float w, int guiW) {
        return (clipX / w + 1.0f) * 0.5f * guiW;
    }

    private static float screenY(float clipY, float w, int guiH) {
        return (1.0f - clipY / w) * 0.5f * guiH;
    }

    /**
     * A line two GUI pixels wide with square ends, as one quad in the GUI buffer. The GPU rasterises and clips
     * it, so it costs the same however long it is and however far off screen it runs. The corners go round
     * the same way as {@link GuiGraphics#fill}'s, which the GUI's y-down projection keeps front-facing.
     */
    private static void line(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, float depth) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = 1.0f;
        float uy = 0.0f;
        if (len > 1.0e-3f) {
            ux = dx / len;
            uy = dy / len;
        }
        // One pixel of cap along the line at each end, one pixel of half-width across it.
        float sx = x0 - ux;
        float sy = y0 - uy;
        float ex = x1 + ux;
        float ey = y1 + uy;
        vertex(vc, pose, sx - uy, sy + ux, depth, HIGHLIGHT_COLOR);
        vertex(vc, pose, ex - uy, ey + ux, depth, HIGHLIGHT_COLOR);
        vertex(vc, pose, ex + uy, ey - ux, depth, HIGHLIGHT_COLOR);
        vertex(vc, pose, sx + uy, sy - ux, depth, HIGHLIGHT_COLOR);
    }

    /**
     * A filled rectangle as one quad, its corners in the order {@link GuiGraphics#fill} gives them once it has put
     * them in order. The caller must pass {@code x0 < x1} and {@code y0 < y1}: the GUI's render type culls back
     * faces, so a reversed rectangle would not show.
     */
    private static void quad(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, float z, int color) {
        vertex(vc, pose, x0, y0, z, color);
        vertex(vc, pose, x0, y1, z, color);
        vertex(vc, pose, x1, y1, z, color);
        vertex(vc, pose, x1, y0, z, color);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z, int color) {
        pose.transformPosition(x, y, z, VERTEX);
        vc.addVertex(VERTEX.x, VERTEX.y, VERTEX.z).setColor(color);
    }

    private static <T> List<T> head(List<T> list, int n) {
        return list.size() <= n ? list : List.copyOf(list.subList(0, n));
    }
}

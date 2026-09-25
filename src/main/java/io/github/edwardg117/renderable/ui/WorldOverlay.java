package io.github.edwardg117.renderable.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.renderer.GameRenderer;
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
 * consumed by the GUI layer (mod bus).
 */
public final class WorldOverlay {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("renderable", "labels");
    private static final long HIGHLIGHT_MILLIS = 10_000;
    private static final int BACKGROUND = 0x90000000;
    private static final int HIGHLIGHT_COLOR = 0xFFFF4D4D;
    private static final int MIN_HIGHLIGHT = 8;

    /**
     * One label, built once per result. Block entities and sections carry the position they were profiled
     * at; entities are looked up per frame by id. Every label is drawn only in the dimension it was measured
     * in; for an entity that is the session's dimension, which matters for an entity that keeps its id
     * through a portal, such as another player.
     */
    private record Label(String dimension, double x, double y, double z, int entityId, String text, int color) {
    }

    private static List<Label> labels = List.of();
    /** Null until first read, then whatever the config said or the user last chose. */
    private static Boolean enabled;

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static boolean haveMatrices;
    private static ResourceKey<Level> lastDimensionKey;
    private static String lastDimension = "";

    private static BlockPos highlight;
    private static int highlightSize = 1;
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
     * Whether labels are drawn. Read from the config on first use, not at every session end, so
     * {@code /renderable overlay off} survives the next session (spec addendum §P4.B6).
     */
    public static boolean isEnabled() {
        Boolean e = enabled;
        if (e == null) {
            e = RenderableConfig.OVERLAY_ENABLED.get();
            enabled = e;
        }
        return e;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static void toggle() {
        setEnabled(!isEnabled());
    }

    /** Draw a wireframe box around the block on screen for ten seconds. */
    public static void highlight(BlockPos pos) {
        highlight(pos, 1);
    }

    /** Draw a wireframe box around the {@code size}-block cube whose minimum corner is {@code min}, for ten seconds. */
    public static void highlight(BlockPos min, int size) {
        highlight = min.immutable();
        highlightSize = Math.max(1, size);
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
        Vec3 cam = cameraPos;
        int guiW = g.guiWidth();
        int guiH = g.guiHeight();

        if (isEnabled() && !labels.isEmpty()) {
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
            g.fill(left - 2, sy - 1, left + tw + 2, sy + lineH, BACKGROUND);
            g.drawString(mc.font, l.text(), left, sy, l.color(), false);
        }
    }

    /**
     * Scratch space for {@link #drawHighlight}, reused every frame; render thread only, like {@link #SCRATCH}.
     * A highlight can outlive the session it came from and keep drawing while the next one samples, so it
     * allocates nothing per frame.
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
     * addendum §P4.C5). An edge that crosses the camera's near plane is clipped at that plane, so a box the
     * camera stands inside still shows its edges. Each edge is drawn as one two-pixel-wide quad, and the
     * whole box is one flush. A wholly visible cube that projects smaller than {@link #MIN_HIGHLIGHT} falls
     * back to a small square instead, so a distant block stays findable. Allocates nothing per frame.
     */
    private static void drawHighlight(GuiGraphics g, Vec3 cam, int guiW, int guiH) {
        BlockPos p = highlight;
        if (p == null) return;
        if (System.currentTimeMillis() > highlightUntilMillis) {
            highlight = null;
            return;
        }
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
            line(vc, pose, cx - h, cy - h, cx + h, cy - h);
            line(vc, pose, cx + h, cy - h, cx + h, cy + h);
            line(vc, pose, cx + h, cy + h, cx - h, cy + h);
            line(vc, pose, cx - h, cy + h, cx - h, cy - h);
        } else {
            for (int e = 0; e < EDGES.length; e += 2) {
                edge(vc, pose, EDGES[e], EDGES[e + 1], guiW, guiH);
            }
        }
        g.flush(); // what fill() does after every rectangle, once for the whole box
    }

    /**
     * One edge of the cube. An endpoint behind the near plane is moved along the edge onto it; the move is
     * made in clip space, where the edge is still a straight line, so the part in front is exact.
     */
    private static void edge(VertexConsumer vc, Matrix4f pose, int a, int b, int guiW, int guiH) {
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
        line(vc, pose, screenX(ax, aw, guiW), screenY(ay, aw, guiH), screenX(bx, bw, guiW), screenY(by, bw, guiH));
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
    private static void line(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1) {
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
        vertex(vc, pose, sx - uy, sy + ux);
        vertex(vc, pose, ex - uy, ey + ux);
        vertex(vc, pose, ex + uy, ey - ux);
        vertex(vc, pose, sx + uy, sy - ux);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y) {
        pose.transformPosition(x, y, 0.0f, VERTEX);
        vc.addVertex(VERTEX.x, VERTEX.y, VERTEX.z).setColor(HIGHLIGHT_COLOR);
    }

    private static <T> List<T> head(List<T> list, int n) {
        return list.size() <= n ? list : List.copyOf(list.subList(0, n));
    }
}

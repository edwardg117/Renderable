package io.github.edwardg117.renderable.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.edwardg117.renderable.RenderableConfig;
import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SessionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Set;

/** Floating labels for the last finished session plus a temporary highlight box (spec §5.4). Game bus. */
public final class WorldOverlay {
    private static final long HIGHLIGHT_MILLIS = 10_000;
    private static final float LABEL_SCALE = 0.02f;

    private static SessionResult result;
    private static List<RankedEntry> blockEntityLabels = List.of();
    private static List<RankedEntry> entityLabels = List.of();
    private static Set<ProfileKey> flagged = Set.of();
    private static boolean enabled = true;

    private static BlockPos highlight;
    private static long highlightUntilMillis;

    private WorldOverlay() {
    }

    public static void setResult(SessionResult r) {
        result = r;
        int n = RenderableConfig.OVERLAY_LABEL_COUNT.get();
        blockEntityLabels = head(Ranking.byKey(r, ProfileKey.Category.BLOCK_ENTITY), n);
        entityLabels = head(Ranking.byKey(r, ProfileKey.Category.ENTITY), n);
        flagged = r.hitchFlaggedKeys();
        enabled = RenderableConfig.OVERLAY_ENABLED.get();
    }

    public static void clear() {
        result = null;
        blockEntityLabels = List.of();
        entityLabels = List.of();
        flagged = Set.of();
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

    /** Draw a red outline around the block for ten seconds. */
    public static void highlight(BlockPos pos) {
        highlight = pos.immutable();
        highlightUntilMillis = System.currentTimeMillis() + HIGHLIGHT_MILLIS;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        if (enabled && result != null) {
            int maxDist = RenderableConfig.OVERLAY_MAX_DISTANCE.get();
            double maxDistSq = (double) maxDist * maxDist;
            long sessionTotal = result.stats().totalNanos();
            int frames = result.frameCount();

            for (RankedEntry e : blockEntityLabels) {
                BlockEntityKey k = (BlockEntityKey) e.key();
                double x = k.x() + 0.5, y = k.y() + 1.25, z = k.z() + 0.5;
                if (cam.distanceToSqr(x, y, z) > maxDistSq) continue;
                drawLabel(pose, buffers, e, frames, sessionTotal, x, y, z);
            }
            for (RankedEntry e : entityLabels) {
                EntityKey k = (EntityKey) e.key();
                Entity ent = mc.level.getEntity(k.entityId());
                if (ent == null || ent == mc.getCameraEntity()) continue;
                Vec3 p = ent.position();
                double y = p.y + ent.getBbHeight() + 0.5;
                if (cam.distanceToSqr(p.x, y, p.z) > maxDistSq) continue;
                drawLabel(pose, buffers, e, frames, sessionTotal, p.x, y, p.z);
            }
            buffers.endBatch();
        }

        if (highlight != null) {
            if (System.currentTimeMillis() > highlightUntilMillis) {
                highlight = null;
                return;
            }
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            LevelRenderer.renderLineBox(pose, lines, new AABB(highlight).inflate(0.01), 1.0f, 0.3f, 0.3f, 1.0f);
            pose.popPose();
            buffers.endBatch(RenderType.lines());
        }
    }

    private static void drawLabel(PoseStack pose, MultiBufferSource buffers, RankedEntry e, int frames,
                                  long sessionTotal, double x, double y, double z) {
        boolean isFlagged = flagged.contains(e.key());
        String text = (isFlagged ? "! " : "") + e.typeId() + " " + Fmt.us(e.usPerFrame(frames)) + " us";
        int color = isFlagged ? Colors.FLAGGED : Colors.heat(e.sharePercent(sessionTotal));
        DebugRenderer.renderFloatingText(pose, buffers, text, x, y, z, color, LABEL_SCALE, true, 0.0f, true);
    }

    private static <T> List<T> head(List<T> list, int n) {
        return list.size() <= n ? list : List.copyOf(list.subList(0, n));
    }
}

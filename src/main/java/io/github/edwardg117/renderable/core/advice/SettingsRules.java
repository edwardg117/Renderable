package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.advice.Recommendation.Audience;
import io.github.edwardg117.renderable.core.advice.Recommendation.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Settings the player can change (spec addendum §P5.B2): one method per rule, in the table's order. */
final class SettingsRules {

    private SettingsRules() {
    }

    static void all(Facts f, List<Recommendation> out) {
        entityDistance(f, out);
        particles(f, out);
        shadows(f, out);
        flywheelShaders(f, out);
        chunkRebuilds(f, out);
        frameCap(f, out);
        vsync(f, out);
    }

    static void entityDistance(Facts f, List<Recommendation> out) {
        double entity = f.bucket(Bucket.ENTITY);
        double distance = f.number("entityDistance");
        if (Double.isNaN(distance) || distance <= Thresholds.ENTITY_DISTANCE_LOWEST) return;
        if (entity < Thresholds.ENTITY_RENDER_MS || entity < Thresholds.ENTITY_RENDER_SHARE * f.frameMs()) return;
        out.add(new Recommendation("entity-distance", Kind.FRAME_RATE, Audience.PLAYER, "Entities cost a lot to draw",
                String.format(Locale.ROOT, "Entities take %.1f ms of each %.1f ms frame at %.0f%% entity distance.",
                        entity, f.frameMs(), distance * 100),
                "Lower Entity Distance in Video Settings; the number of entities drawn falls with the square of the distance.",
                entity, 0));
    }

    static void particles(Facts f, List<Recommendation> out) {
        double ms = f.bucket(Bucket.PARTICLE_RENDER) + f.bucket(Bucket.PARTICLE_TICK);
        if (!"ALL".equalsIgnoreCase(f.text("particles")) || ms < Thresholds.PARTICLES_MS) return;
        out.add(new Recommendation("particles", Kind.FRAME_RATE, Audience.PLAYER, "Particles are on All",
                String.format(Locale.ROOT, "Particles take %.1f ms a frame with Particles on All.", ms),
                "Set Particles to Decreased in Video Settings.", ms, 0));
    }

    static void shadows(Facts f, List<Recommendation> out) {
        if (!f.shadersOn() || f.shadowableRenderMs() <= 0) return;
        double share = f.shadowMs() / f.shadowableRenderMs();
        if (f.shadowMs() < Thresholds.SHADOW_MS || share < Thresholds.SHADOW_SHARE) return;
        out.add(new Recommendation("shadows", Kind.FRAME_RATE, Audience.PLAYER, "Shader shadows draw everything twice",
                String.format(Locale.ROOT, "The shader pack's shadow pass is %.0f%% of block entity and entity drawing, %.1f ms a frame.",
                        share * 100, f.shadowMs()),
                "Lower the shader pack's shadow distance or resolution, or turn its shadows off.", f.shadowMs(), 0));
    }

    static void flywheelShaders(Facts f, List<Recommendation> out) {
        double blockEntities = f.bucket(Bucket.BLOCK_ENTITY);
        if (!f.shadersOn() || !"flywheel:off".equals(f.text("flywheelBackend"))
                || blockEntities < Thresholds.FLYWHEEL_OFF_BLOCK_ENTITY_MS) return;
        out.add(new Recommendation("flywheel-shaders", Kind.FRAME_RATE, Audience.PLAYER, "Flywheel is off under this shader pack",
                String.format(Locale.ROOT, "Flywheel is not instancing under this shader pack, so the machines it would instance"
                        + " are drawn one at a time: block entities take %.1f ms a frame.", blockEntities),
                "Turn shaders off to bring instancing back: Flywheel does not instance while any shader pack is in use.", blockEntities, 0));
    }

    static void chunkRebuilds(Facts f, List<Recommendation> out) {
        double upload = f.bucket(Bucket.CHUNK_UPLOAD);
        double blend = f.number("biomeBlend");
        double distance = f.number("renderDistance");
        boolean highBlend = !Double.isNaN(blend) && blend >= Thresholds.BIOME_BLEND_HIGH;
        boolean farView = !Double.isNaN(distance) && distance >= Thresholds.RENDER_DISTANCE_HIGH;
        if (upload < Thresholds.CHUNK_UPLOAD_MS || !(highBlend || farView)) return;

        int led = 0;
        double worst = 0;
        for (Facts.Hitch h : f.hitches()) {
            if (!h.chunkUploadLargest()) continue;
            led++;
            worst = Math.max(worst, h.chunkUploadMs());
        }
        boolean stutter = led >= Thresholds.REPEATED_HITCHES;

        List<String> settings = new ArrayList<>();
        if (!Double.isNaN(distance)) settings.add(String.format(Locale.ROOT, "render distance %.0f", distance));
        if (!Double.isNaN(blend)) settings.add("biome blend " + Words.blend((int) blend));
        String finding = String.format(Locale.ROOT, "Chunk rebuilds take %.1f ms a frame", upload)
                + (stutter ? String.format(Locale.ROOT, " and are the largest part of %d of %d hitches", led, f.hitches().size()) : "")
                + ", at " + String.join(" and ", settings) + ".";
        List<String> actions = new ArrayList<>();
        if (highBlend) actions.add("Lower Biome Blend to " + Words.blend(Thresholds.BIOME_BLEND_HIGH - 1) + " or less.");
        if (farView) actions.add("While travelling, a lower render distance rebuilds fewer sections.");
        out.add(new Recommendation("chunk-rebuilds", stutter ? Kind.STUTTER : Kind.FRAME_RATE, Audience.PLAYER,
                "Chunk rebuilds", finding, String.join(" ", actions), upload, stutter ? worst : 0));
    }

    static void frameCap(Facts f, List<Recommendation> out) {
        if (f.frameMs() <= 0 || f.capMs() < Thresholds.CAP_SHARE * f.frameMs()) return;
        double limit = f.number("fpsLimit");
        String fps = Double.isNaN(limit) ? "the fps limit" : String.format(Locale.ROOT, "the %.0f fps limit", limit);
        out.add(new Recommendation("frame-cap", Kind.NOTE, Audience.PLAYER, "Frames are waiting for the fps limit",
                String.format(Locale.ROOT, "%.1f ms of each %.1f ms frame is spent waiting for %s, so the game has headroom.",
                        f.capMs(), f.frameMs(), fps),
                "Raise Max Framerate if you want more frames.", 0, 0));
    }

    static void vsync(Facts f, List<Recommendation> out) {
        if (!"true".equals(f.text("vsync")) || f.frameMs() <= 0 || f.swapMs() < Thresholds.VSYNC_SWAP_SHARE * f.frameMs()) return;
        out.add(new Recommendation("vsync", Kind.NOTE, Audience.PLAYER, "VSync is waiting for the display",
                String.format(Locale.ROOT, "With VSync on, %.1f ms of each %.1f ms frame is spent waiting for the display.",
                        f.swapMs(), f.frameMs()),
                "Turn VSync off for more frames, at the risk of tearing.", 0, 0));
    }
}

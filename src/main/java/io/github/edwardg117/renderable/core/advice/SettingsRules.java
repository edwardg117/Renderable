package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.SoundPlays;
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
        distantHorizons(f, out);
        soundWait(f, out);
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

    /**
     * Distant Horizons' LOD drawing (spec addendum §P6.9), the one rule that names a mod. {@code lod} is time
     * Renderable measures in DH's own render methods, so it is 0 without DH or when that hook did not apply. The
     * action names Distant Horizons' settings by their captions, the same in 2.4.5-b and 3.3.3; both are on the
     * first page of its settings.
     */
    static void distantHorizons(Facts f, List<Recommendation> out) {
        double lod = f.bucket(Bucket.LOD);
        if (lod < Thresholds.LOD_MS || lod < Thresholds.LOD_SHARE * f.frameMs()) return;
        out.add(new Recommendation("distant-horizons", Kind.FRAME_RATE, Audience.PLAYER,
                "Distant Horizons' far terrain costs a lot to draw",
                String.format(Locale.ROOT, "Distant Horizons draws its far terrain in %.1f ms of each %.1f ms frame.",
                        lod, f.frameMs()),
                "Lower Quality Preset or LOD Chunk Render Distance Radius in Distant Horizons' settings, which its"
                        + " button beside FOV in Options opens.",
                lod, 0));
    }

    /**
     * Sound plays (spec addendum §P6.9): their steady cost, or stutter. Two stutter tests, one finding: one sound row by
     * the mod rules' test ({@link KeyStutter#find}), which names the sound and wins when it passes; otherwise the
     * {@code sound} bucket in repeated hitches, which catches a stall spread over many plays, since queued sounds play
     * inside the sound engine's tick. A play waits for the sound thread to hand it a channel, and returns before asking
     * for one (§P6.2), but only at a volume of exactly 0. So the action says to set a slider to 0%: the stalled or
     * repeated sound's in the row test, the costliest row's otherwise, and none when no sound row was timed. A vanilla
     * sound has no mod to report it to.
     */
    static void soundWait(Facts f, List<Recommendation> out) {
        double ms = f.soundMs();
        boolean costly = ms >= Thresholds.SOUND_MS;
        KeyStutter s = KeyStutter.find(f, k -> k.kind() == ModKey.Kind.SOUND);
        int inBucket = 0;
        double bucketWorst = 0;
        for (Facts.Hitch h : f.hitches()) {
            // The frame's GC pause may have landed in the sound engine's tick: the bucket loses it, as
            // unattributed-stall's parts do.
            double v = Math.max(0, h.soundMs() - h.gcMs());
            if (v < Thresholds.SOUND_BUCKET_SHARE * h.totalMs()) continue;
            inBucket++;
            bucketWorst = Math.max(bucketWorst, v);
        }
        boolean bucket = inBucket >= Thresholds.REPEATED_HITCHES
                && inBucket >= Thresholds.HITCH_REPEAT_SHARE * f.hitches().size();
        if (!costly && s == null && !bucket) return;

        Facts.SoundCost top = f.sounds().isEmpty() ? null : f.sounds().get(0);
        String steady = "Playing sounds kept the game waiting "
                + (ms < 0.05 ? "under 0.1" : String.format(Locale.ROOT, "%.1f", ms)) + " ms a frame";
        String costliest = top == null ? "" : steady + "; " + top.event() + " (" + Words.slider(top.category())
                + ") played " + SoundPlays.perSecond(top.playsPerSecond()) + " times a second.";
        String modId = top == null ? null : top.modId();
        String category = top == null ? null : top.category();
        String finding;
        double worst = 0;
        if (s != null) {
            modId = s.key().modId();
            category = s.key().soundCategory();
            finding = s.finding("Playing " + s.key().soundEvent() + " (" + Words.slider(category) + ")",
                    f.hitches().size()) + (costly ? " " + steady + "." : "");
            worst = s.worstMs();
        } else if (bucket) {
            finding = String.format(Locale.ROOT, "In %d of %d hitches, the sound engine took at least a quarter of the"
                    + " frame (up to %.0f ms).", inBucket, f.hitches().size(), bucketWorst) + (top == null ? "" : " " + costliest);
            worst = bucketWorst;
        } else {
            finding = costliest;
        }
        String slider = category == null ? "the slider for the sounds playing here" : Words.volume(category);
        out.add(new Recommendation("sound-wait", s != null || bucket ? Kind.STUTTER : Kind.FRAME_RATE, Audience.PLAYER,
                "Sounds keep the game waiting", finding,
                "Set " + slider + " to 0% in Music & Sounds: the game then skips those sounds instead of waiting for them."
                        + (modId != null && Thresholds.isMod(modId)
                                ? " A pack author can report the sound's mod with this file." : ""),
                ms, worst));
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

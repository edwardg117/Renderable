package io.github.edwardg117.renderable.core.advice;

/**
 * Every threshold the rules use (spec addendum §P5.B2), in one place. They are first estimates, to be revisited
 * against real reports; a rule reads them from here rather than spelling a number.
 */
final class Thresholds {
    static final double ENTITY_RENDER_MS = 1.0;
    static final double ENTITY_RENDER_SHARE = 0.10;
    /** Entity distance is a scale, 1.0 being 100%; at 50% there is nothing left to lower. */
    static final double ENTITY_DISTANCE_LOWEST = 0.5;
    static final double PARTICLES_MS = 0.5;
    static final double SHADOW_MS = 1.0;
    static final double SHADOW_SHARE = 0.30;
    static final double FLYWHEEL_OFF_BLOCK_ENTITY_MS = 1.0;
    static final double CHUNK_UPLOAD_MS = 1.0;
    /** Biome blend is a radius: 3 is 7x7. */
    static final int BIOME_BLEND_HIGH = 3;
    static final int RENDER_DISTANCE_HIGH = 16;
    static final double CAP_SHARE = 0.25;
    static final double VSYNC_SWAP_SHARE = 0.25;
    static final double TYPE_MS = 1.0;
    static final int TOP_TYPES = 3;
    static final double PARTICLE_CLASS_MS = 0.5;
    static final int REPEATED_HITCHES = 3;
    static final double HITCH_REPEAT_SHARE = 0.25;
    /**
     * A key counts toward a repeated hitch only when it took at least this share of the hitch: every hitch's top
     * list also holds the frame's usual costs (plan 5b, deviation 6).
     */
    static final double HITCH_KEY_SHARE = 0.25;
    static final double RAM_RESERVE_MIB = 4096;
    static final long RAM_STALL_GC_MS = 500;
    static final double LIVE_HEAP_SHARE = 0.80;
    static final double HEAP_GROWTH = 1.6;
    static final long GC_PAUSE_MS = 50;
    static final double STALL_MS = 50;
    static final double STALL_SHARE = 0.5;
    static final double MOD_MS = 0.5;
    static final double LOD_MS = 1.0;
    static final double LOD_SHARE = 0.10;
    static final double SOUND_MS = 0.5;
    /** The share of a hitch the {@code sound} bucket must reach to count toward {@code sound-wait}'s bucket test. */
    static final double SOUND_BUCKET_SHARE = 0.25;
    /** The share of a hitch its unnamed time must reach to count toward {@code unattributed-stall}'s repeat form. */
    static final double UNNAMED_SHARE = 0.25;

    private Thresholds() {
    }

    /**
     * The mod rules skip owners a pack author cannot act on: vanilla's HUD layers and packets, NeoForge's own
     * handlers, and handlers whose mod could not be found (plan 5b, deviation 4).
     */
    static boolean isMod(String modId) {
        return !"minecraft".equals(modId) && !"neoforge".equals(modId) && !"unknown".equals(modId);
    }

    /** True when {@code t} is a cause of hitch {@code h}, not a steady cost or a GC suspect (deviation 6). */
    static boolean causes(Facts.Top t, Facts.Hitch h) {
        return !t.gc() && t.ms() >= HITCH_KEY_SHARE * h.totalMs();
    }
}

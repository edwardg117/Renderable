package io.github.edwardg117.renderable.core;

/**
 * Per-frame time buckets (spec §3.2). Render buckets nest inside render time, tick buckets inside tick time.
 * Phase 1 fills BLOCK_ENTITY and ENTITY; the rest exist so later phases do not change the frame record shape.
 */
public enum Bucket {
    BLOCK_ENTITY(true),
    ENTITY(true),
    PARTICLE_RENDER(true),
    FLYWHEEL(true),
    CHUNK_UPLOAD(true),
    LIGHT(true),
    PARTICLE_TICK(false),
    BE_TICK(false),
    ENTITY_TICK(false);

    public static final Bucket[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final boolean render;

    Bucket(boolean render) {
        this.render = render;
    }

    /** True when this bucket's time is part of render time rather than tick time. */
    public boolean isRender() {
        return render;
    }

    /** Lower-case label with spaces, for reports. */
    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}

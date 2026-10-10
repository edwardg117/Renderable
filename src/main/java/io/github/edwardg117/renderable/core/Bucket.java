package io.github.edwardg117.renderable.core;

/**
 * Per-frame time buckets (spec §3.2). Render buckets nest inside render time, tick buckets inside tick time.
 * Phase 1 fills BLOCK_ENTITY and ENTITY; the rest exist so later phases do not change the frame record shape.
 *
 * <p>The split prints buckets in this order, so terrain and its LODs, drawn first and usually largest, lead the
 * render side (spec addendum §P6.3). Ordinals are never persisted: the reports and the JSON name a bucket by its label.
 */
public enum Bucket {
    /** The chunk layers {@code LevelRenderer.renderLevel} draws, solid through tripwire (spec addendum §P6.3). */
    TERRAIN(true),
    /** Distant Horizons' LOD drawing, in every pass its head injection runs (spec addendum §P6.3). */
    LOD(true),
    BLOCK_ENTITY(true),
    ENTITY(true),
    PARTICLE_RENDER(true),
    FLYWHEEL(true),
    CHUNK_UPLOAD(true),
    LIGHT(true),
    /** {@code Gui.render}: every HUD layer and the HUD's own events (spec addendum §P5.A1). */
    HUD(true),
    PARTICLE_TICK(false),
    BE_TICK(false),
    ENTITY_TICK(false),
    /** {@code SoundManager.tick}: playing sounds' updates and the sounds {@code playDelayed} queued (§P6.3). */
    SOUND(false),
    /** {@code ClientLevel.animateTick}: the random block display effects around the player (§P6.3). */
    ANIMATE_TICK(false);

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

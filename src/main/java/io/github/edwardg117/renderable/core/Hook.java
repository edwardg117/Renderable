package io.github.edwardg117.renderable.core;

/**
 * Every hook the mod can install. A hook has "fired" once it has run while a session was sampling, at least
 * once in this client run, and {@code Probe} records that. The report's environment header lists the hooks
 * installed, fired and that did not apply (spec §4.5, spec addendum §P5.D1).
 *
 * <p>Lives in {@code core} rather than {@code hooks} so the report writers and the results screen can name
 * a hook by its constant instead of retyping its name (spec addendum §P4.D1). The enum has no imports, so
 * it does not endanger {@code core}'s freedom from Minecraft types.
 */
public enum Hook {
    BLOCK_ENTITY_RENDER,
    ENTITY_RENDER,
    PARTICLE_RENDER,
    PARTICLE_TICK,
    BLOCK_ENTITY_TICK,
    ENTITY_TICK,
    LIGHT,
    FLYWHEEL,
    IRIS_SHADOW,
    SODIUM_MESH,
    SODIUM_UPLOAD,
    VERTICES,
    ACCELERATED_VERTICES,
    TASKS,
    FRAME_CAP,
    HUD,
    HUD_LAYERS,
    PACKETS,
    PAYLOADS,
    RIDER_TICK,
    EVENTS
}

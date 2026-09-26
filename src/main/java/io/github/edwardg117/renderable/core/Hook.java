package io.github.edwardg117.renderable.core;

/**
 * Every hook the mod can install. A hook is "applied" once it has fired at least once in this client run;
 * {@code Probe} records that and the report's environment header lists it (spec §4.5).
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
    ACCELERATED_VERTICES
}

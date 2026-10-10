package io.github.edwardg117.renderable.core;

/** Identity of one timed thing. Implementations are value records so they work as map keys. */
public interface ProfileKey {

    /**
     * {@code MOD} holds a mod's event handlers, HUD layers, packets and sounds, whose type id is the mod's id so they
     * roll up into one row per mod (spec addendum §P5.A7, §P6.5).
     */
    enum Category { BLOCK_ENTITY, ENTITY, PARTICLE, BLOCK, SECTION, MOD }

    Category category();

    /** Registry id or class name used to roll individual keys up into a type row. */
    String typeId();

    /** One human-readable line for tables and overlay labels. */
    String label();

    /** Stable string form for JSON (spec §6.2). */
    String serialize();

    /**
     * The dimension the key was measured in, for the kinds that record one: block entities and sections. Null for
     * the rest; an entity's is its session's (spec addendum §P5.D7).
     */
    default String dimension() {
        return null;
    }
}

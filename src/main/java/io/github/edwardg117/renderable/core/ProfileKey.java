package io.github.edwardg117.renderable.core;

/** Identity of one timed thing. Implementations are value records so they work as map keys. */
public interface ProfileKey {

    enum Category { BLOCK_ENTITY, ENTITY, PARTICLE, BLOCK, SECTION, VISUAL }

    Category category();

    /** Registry id or class name used to roll individual keys up into a type row. */
    String typeId();

    /** One human-readable line for tables and overlay labels. */
    String label();

    /** Stable string form for JSON (spec §6.2). */
    String serialize();
}

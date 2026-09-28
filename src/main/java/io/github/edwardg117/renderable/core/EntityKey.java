package io.github.edwardg117.renderable.core;

/**
 * One entity being rendered or ticked. Identity is the type and the entity id **only**: the name is carried
 * for {@link #label()} and the report, but including it in equality would fork the key when a mob is renamed
 * part way through a session, producing two rows with the same {@link #serialize()} form that a reader could
 * not tell apart (spec addendum §P4.D5). The first name seen wins.
 *
 * The name is a non-player entity's custom name, {@link #YOU} on the local player's key, or empty. A player's key
 * never carries a player's name (spec addendum §P5.C3): {@link #player} makes one, and takes no name.
 */
public record EntityKey(String typeId, int entityId, String name) implements ProfileKey {

    /**
     * Stands in for the local player's name, so its row reads {@code minecraft:player #118098 (you)}. No other
     * player's key carries a name.
     */
    public static final String YOU = "you";

    /**
     * A player's key (spec addendum §P5.C3): no name, so a report never carries another player's name, except that
     * the local player's says {@link #YOU}. Reports hold positions, so players share them privately anyway, but
     * the names of everyone near the player are not theirs to share.
     */
    public static EntityKey player(String typeId, int entityId, boolean local) {
        return new EntityKey(typeId, entityId, local ? YOU : "");
    }

    @Override
    public Category category() {
        return Category.ENTITY;
    }

    @Override
    public String label() {
        return typeId + " #" + entityId + (name.isEmpty() ? "" : " (" + name + ")");
    }

    @Override
    public String serialize() {
        return typeId + "#" + entityId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EntityKey k && k.entityId == entityId && k.typeId.equals(typeId);
    }

    @Override
    public int hashCode() {
        return typeId.hashCode() * 31 + entityId;
    }
}

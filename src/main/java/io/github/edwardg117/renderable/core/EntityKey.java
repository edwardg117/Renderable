package io.github.edwardg117.renderable.core;

/**
 * One entity being rendered or ticked. Identity is the type and the entity id **only**: the name is carried
 * for {@link #label()} and the report, but including it in equality would fork the key when a mob is renamed
 * part way through a session, producing two rows with the same {@link #serialize()} form that a reader could
 * not tell apart (spec addendum §P4.D5). The first name seen wins.
 */
public record EntityKey(String typeId, int entityId, String name) implements ProfileKey {

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

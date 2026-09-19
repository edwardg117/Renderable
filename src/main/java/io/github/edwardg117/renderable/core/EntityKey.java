package io.github.edwardg117.renderable.core;

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
}

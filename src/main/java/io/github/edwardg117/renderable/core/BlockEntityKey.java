package io.github.edwardg117.renderable.core;

public record BlockEntityKey(String typeId, String dimension, int x, int y, int z) implements ProfileKey {

    @Override
    public Category category() {
        return Category.BLOCK_ENTITY;
    }

    @Override
    public String label() {
        return typeId + " @ " + x + " " + y + " " + z;
    }

    @Override
    public String serialize() {
        return typeId + "@" + dimension + ":" + x + "," + y + "," + z;
    }
}

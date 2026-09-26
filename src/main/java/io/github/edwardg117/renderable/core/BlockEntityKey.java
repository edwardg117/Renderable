package io.github.edwardg117.renderable.core;

/**
 * One block entity being rendered or ticked, identified by type, dimension and position.
 */
public record BlockEntityKey(String typeId, String dimension, int x, int y, int z) implements ProfileKey {

    @Override
    public Category category() {
        return Category.BLOCK_ENTITY;
    }

    /**
     * Deliberately without the dimension, which {@link #serialize()} carries. A session is confined to one
     * dimension — {@code FrameEvents.onLevelUnload} discards a running session when the client level goes
     * away — and the report's environment header names it, so repeating it on every row would widen the
     * tables for no information (spec addendum §P4.D5).
     */
    @Override
    public String label() {
        return typeId + " @ " + x + " " + y + " " + z;
    }

    @Override
    public String serialize() {
        return typeId + "@" + dimension + ":" + x + "," + y + "," + z;
    }
}

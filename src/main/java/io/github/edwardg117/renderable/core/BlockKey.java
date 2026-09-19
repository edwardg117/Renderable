package io.github.edwardg117.renderable.core;

/** One block type in Sodium's mesh build (spec §3.4). Fluids are keyed by the block they render as. */
public record BlockKey(String blockId) implements ProfileKey {

    @Override
    public Category category() {
        return Category.BLOCK;
    }

    @Override
    public String typeId() {
        return blockId;
    }

    @Override
    public String label() {
        return blockId;
    }

    @Override
    public String serialize() {
        return "block:" + blockId;
    }
}

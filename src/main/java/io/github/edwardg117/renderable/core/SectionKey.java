package io.github.edwardg117.renderable.core;

/** One 16x16x16 render section rebuilt by Sodium (spec §3.4), in section coordinates. */
public record SectionKey(String dimension, int x, int y, int z) implements ProfileKey {

    @Override
    public Category category() {
        return Category.SECTION;
    }

    /** All sections roll up into one type row; the label carries the position. */
    @Override
    public String typeId() {
        return "section";
    }

    @Override
    public String label() {
        return "section " + x + " " + y + " " + z;
    }

    @Override
    public String serialize() {
        return "section@" + dimension + ":" + x + "," + y + "," + z;
    }

    public int centerX() {
        return x * 16 + 8;
    }

    public int centerY() {
        return y * 16 + 8;
    }

    public int centerZ() {
        return z * 16 + 8;
    }
}

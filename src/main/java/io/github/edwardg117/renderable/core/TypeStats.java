package io.github.edwardg117.renderable.core;

/**
 * Per-frame aggregation for one (category, type id), so a type row's peak is the type's worst frame rather
 * than its worst member (spec addendum §P4.A2). Written on the render thread only. Every {@link Accumulator}
 * of the type holds a reference to the same instance, so the per-frame roll-up in {@code Session.closeFrame}
 * reaches a type by one field dereference rather than a map lookup per touched key.
 */
public final class TypeStats {
    public final String typeId;
    public final ProfileKey.Category category;

    /** Largest clean render-plus-tick nanoseconds this type saw in a single frame (spec addendum §P4.A0). */
    public long maxFrameNanos;
    /** Frames in which this type's total was GC-suspect, and so did not update the peak. */
    public long gcFrames;

    /**
     * Render plus tick nanoseconds in the current frame. Reset by Session at each frame boundary. This is
     * the sum of the type's members' inclusive times, so a member nested inside another member of the same
     * type (Create's nested contraption renderers are the realistic case) counts twice, and a type's peak
     * can exceed the frame it was measured in. Inherent to the design, not a bug.
     */
    long frameNanos;
    /** True once this type has been added to the session's touched list for the current frame. */
    boolean inFrame;

    TypeStats(String typeId, ProfileKey.Category category) {
        this.typeId = typeId;
        this.category = category;
    }
}

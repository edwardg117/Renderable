package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;

/**
 * One accumulator slot on a Minecraft object. Implemented onto {@code BlockEntity} and {@code Entity} by
 * mixins so {@link Probe} can cache an accumulator on the object itself rather than in a map that would
 * keep every rendered object alive for the length of a session (spec addendum §P4.B2).
 *
 * <p>The field lives and dies with the object: each object keeps its own cached {@link Accumulator} (and,
 * through it, a {@code ProfileKey} and a shared {@code TypeStats}) for as long as it is alive, including
 * after the session that made it ends. That is fine, because the size of what is kept is bounded by live
 * objects rather than by session length, it is freed the moment the object unloads, and a later session does
 * not add to it: {@link Probe} replaces a stale accumulator on first sight rather than keeping both. It is
 * written and read on the render thread only.
 */
public interface AccumulatorHolder {

    /** The cached accumulator, or null if this object has not been timed yet. */
    Accumulator renderable$accumulator();

    /** Caches an accumulator on this object. */
    void renderable$accumulator(Accumulator accumulator);
}

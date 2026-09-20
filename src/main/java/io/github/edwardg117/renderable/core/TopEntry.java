package io.github.edwardg117.renderable.core;

/**
 * One of a frame's top contributors: the key, its render-plus-tick nanoseconds in that frame, and whether
 * that figure is GC-suspect for the frame it belongs to (spec addendum §P4.A0), meaning most of it may be
 * the collector's pause rather than the code's.
 */
public record TopEntry(ProfileKey key, long nanos, boolean gc) {
}

package io.github.edwardg117.renderable.core;

/** One of a frame's top contributors: the key and its render-plus-tick nanoseconds in that frame. */
public record TopEntry(ProfileKey key, long nanos) {
}

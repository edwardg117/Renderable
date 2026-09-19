package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * One row of a ranked table: either an individual key ({@code key != null}, {@code members == 1})
 * or a type roll-up ({@code key == null}, {@code members} = number of keys summed).
 */
public record RankedEntry(String label,
                          ProfileKey key,
                          String typeId,
                          ProfileKey.Category category,
                          int members,
                          long calls,
                          long tickCalls,
                          long renderNanos,
                          long tickNanos,
                          long shadowNanos,
                          long maxFrameNanos,
                          long vertices) {

    public static RankedEntry of(Accumulator a) {
        return new RankedEntry(a.key.label(), a.key, a.key.typeId(), a.key.category(), 1,
                a.calls, a.tickCalls, a.renderNanos, a.tickNanos, a.shadowNanos, a.maxFrameNanos, a.vertices);
    }

    public static RankedEntry group(String typeId, ProfileKey.Category category, List<Accumulator> members) {
        long calls = 0, tickCalls = 0, render = 0, tick = 0, shadow = 0, peak = 0, vertices = 0;
        for (Accumulator a : members) {
            calls += a.calls;
            tickCalls += a.tickCalls;
            render += a.renderNanos;
            tick += a.tickNanos;
            shadow += a.shadowNanos;
            peak = Math.max(peak, a.maxFrameNanos);
            vertices += a.vertices;
        }
        return new RankedEntry(typeId, null, typeId, category, members.size(),
                calls, tickCalls, render, tick, shadow, peak, vertices);
    }

    public long totalNanos() {
        return renderNanos + tickNanos;
    }

    public double usPerFrame(int frames) {
        return frames == 0 ? 0 : totalNanos() / 1000.0 / frames;
    }

    /** Render calls per frame. For particles this is the average number alive and rendered each frame. */
    public double callsPerFrame(int frames) {
        return frames == 0 ? 0 : (double) calls / frames;
    }

    public double renderUsPerFrame(int frames) {
        return frames == 0 ? 0 : renderNanos / 1000.0 / frames;
    }

    public double tickUsPerFrame(int frames) {
        return frames == 0 ? 0 : tickNanos / 1000.0 / frames;
    }

    public double sharePercent(long sessionTotalNanos) {
        return sessionTotalNanos == 0 ? 0 : 100.0 * totalNanos() / sessionTotalNanos;
    }

    public double shadowPercent() {
        return renderNanos == 0 ? 0 : 100.0 * shadowNanos / renderNanos;
    }

    public double verticesPerFrame(int frames) {
        return frames == 0 ? 0 : (double) vertices / frames;
    }
}

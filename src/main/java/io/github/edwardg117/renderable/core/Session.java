package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Live sampling state for one session (spec §3). All methods are called from the render thread only.
 * The clock is injected so tests can drive it.
 */
public final class Session {
    private final SessionConfig config;
    private final LongSupplier clock;
    private final long wallStartMillis;

    private final ArrayList<FrameRecord> frames = new ArrayList<>(4096);
    private final List<FrameRecord> framesView = Collections.unmodifiableList(frames);

    private long sessionStartNanos = -1;
    private long frameStartNanos = -1;
    private long renderStartNanos;
    private long tickStartNanos = -1;
    private long renderNanos;
    private long tickNanos;
    private final long[] buckets = new long[Bucket.COUNT];
    private long lastGcCount = -1;
    private long lastGcMillis;

    public Session(SessionConfig config, LongSupplier clock, long wallStartMillis) {
        this.config = config;
        this.clock = clock;
        this.wallStartMillis = wallStartMillis;
    }

    public SessionConfig config() {
        return config;
    }

    public long wallStartMillis() {
        return wallStartMillis;
    }

    /** Read-only live view of the frames recorded so far. */
    public List<FrameRecord> framesView() {
        return framesView;
    }

    public int frameCount() {
        return frames.size();
    }

    /** Nanoseconds since the first frame started, or 0 before that. */
    public long elapsedNanos() {
        return sessionStartNanos < 0 ? 0 : clock.getAsLong() - sessionStartNanos;
    }

    /**
     * Called at RenderFrameEvent.Pre with the JVM's cumulative GC count and pause time.
     * Closes the previous frame, if any, and opens a new one.
     *
     * @return true when the configured duration has elapsed and the caller should finish the session
     */
    public boolean onRenderPre(long gcCountTotal, long gcMillisTotal) {
        long now = clock.getAsLong();

        int gcCount = 0;
        long gcMillis = 0;
        if (lastGcCount >= 0) {
            gcCount = (int) Math.max(0, gcCountTotal - lastGcCount);
            gcMillis = Math.max(0, gcMillisTotal - lastGcMillis);
        }
        lastGcCount = gcCountTotal;
        lastGcMillis = gcMillisTotal;

        boolean done = false;
        if (frameStartNanos >= 0) {
            closeFrame(now, gcCount, gcMillis);
            done = now - sessionStartNanos >= config.durationNanos();
        } else {
            sessionStartNanos = now;
        }

        frameStartNanos = now;
        renderStartNanos = now;
        renderNanos = 0;
        tickNanos = 0;
        Arrays.fill(buckets, 0L);
        return done;
    }

    /** Called at RenderFrameEvent.Post. */
    public void onRenderPost() {
        if (frameStartNanos < 0) return;
        renderNanos += clock.getAsLong() - renderStartNanos;
    }

    /** Called at ClientTickEvent.Pre. */
    public void onTickPre() {
        tickStartNanos = clock.getAsLong();
    }

    /** Called at ClientTickEvent.Post. Ticks before the first frame are ignored. */
    public void onTickPost() {
        if (tickStartNanos < 0) return;
        long elapsed = clock.getAsLong() - tickStartNanos;
        tickStartNanos = -1;
        if (frameStartNanos >= 0) {
            tickNanos += elapsed;
        }
    }

    private void closeFrame(long now, int gcCount, long gcMillis) {
        frames.add(new FrameRecord(frames.size(), frameStartNanos - sessionStartNanos, now - frameStartNanos,
                tickNanos, renderNanos, buckets.clone(), gcCount, gcMillis, List.of()));
    }
}

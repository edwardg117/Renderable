package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Maximum nesting depth tracked. Deeper calls are counted but not timed. */
    public static final int MAX_DEPTH = 64;

    private final Map<ProfileKey, Accumulator> accumulators = new HashMap<>(1024);
    private final ArrayList<Accumulator> touched = new ArrayList<>(1024);

    private final Accumulator[] stack = new Accumulator[MAX_DEPTH];
    private final long[] stackStart = new long[MAX_DEPTH];
    private final Bucket[] stackBucket = new Bucket[MAX_DEPTH];
    private int depth;
    private long timedCalls;

    /** Set by the Iris integration (phase 2) while the shadow pass is rendering. */
    public boolean shadowPass;

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

    /** Number of completed begin/end pairs, for the overhead estimate. */
    public long timedCalls() {
        return timedCalls;
    }

    /** Snapshot of all accumulators; safe to call after the session is finished. */
    public Map<ProfileKey, Accumulator> accumulators() {
        return Map.copyOf(accumulators);
    }

    /** Returns the accumulator for a key, creating it on first sight. Hooks cache the result per instance. */
    public Accumulator accumulator(ProfileKey key) {
        return accumulators.computeIfAbsent(key, Accumulator::new);
    }

    /** Start timing a call attributed to {@code a} in {@code bucket}. Must be paired with {@link #end()}. */
    public void begin(Accumulator a, Bucket bucket) {
        long now = clock.getAsLong();
        if (depth < MAX_DEPTH) {
            stack[depth] = a;
            stackStart[depth] = now;
            stackBucket[depth] = bucket;
        }
        depth++;
    }

    /** Finish the innermost call. Extra calls with nothing open are ignored. */
    public void end() {
        if (depth == 0) return;
        depth--;
        if (depth >= MAX_DEPTH) return;

        long elapsed = clock.getAsLong() - stackStart[depth];
        Accumulator a = stack[depth];
        Bucket bucket = stackBucket[depth];
        stack[depth] = null;
        timedCalls++;

        if (bucket.isRender()) {
            a.renderNanos += elapsed;
            a.calls++;
            if (shadowPass) a.shadowNanos += elapsed;
        } else {
            a.tickNanos += elapsed;
            a.tickCalls++;
        }

        if (frameStartNanos >= 0) {
            if (!a.inFrame) {
                a.inFrame = true;
                touched.add(a);
            }
            a.frameNanos += elapsed;
            if (depth == 0) {
                buckets[bucket.ordinal()] += elapsed;
            }
        }
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
        int n = config.topContributorsPerFrame();
        Accumulator[] topAcc = new Accumulator[n];
        int topSize = 0;

        for (int i = 0, size = touched.size(); i < size; i++) {
            Accumulator a = touched.get(i);
            long fn = a.frameNanos;
            if (fn > a.maxFrameNanos) a.maxFrameNanos = fn;

            if (n == 0) continue;
            if (topSize < n) {
                int pos = topSize++;
                while (pos > 0 && topAcc[pos - 1].frameNanos < fn) {
                    topAcc[pos] = topAcc[pos - 1];
                    pos--;
                }
                topAcc[pos] = a;
            } else if (fn > topAcc[n - 1].frameNanos) {
                int pos = n - 1;
                while (pos > 0 && topAcc[pos - 1].frameNanos < fn) {
                    topAcc[pos] = topAcc[pos - 1];
                    pos--;
                }
                topAcc[pos] = a;
            }
        }

        List<TopEntry> top = new ArrayList<>(topSize);
        for (int i = 0; i < topSize; i++) {
            top.add(new TopEntry(topAcc[i].key, topAcc[i].frameNanos));
        }

        for (int i = 0, size = touched.size(); i < size; i++) {
            Accumulator a = touched.get(i);
            a.frameNanos = 0;
            a.inFrame = false;
        }
        touched.clear();

        frames.add(new FrameRecord(frames.size(), frameStartNanos - sessionStartNanos, now - frameStartNanos,
                tickNanos, renderNanos, buckets.clone(), gcCount, gcMillis, List.copyOf(top)));
    }
}

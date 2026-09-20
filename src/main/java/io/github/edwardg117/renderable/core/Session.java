package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    /** Spec addendum §P4.A0: pauses below this are too small to make a peak misleading, so they are ignored. */
    public static final long GC_MIN_NANOS = 2_000_000L;
    /** Spec addendum §P4.A0: the JVM reports pauses in whole milliseconds, so the comparison allows one. */
    public static final long GC_SLACK_NANOS = 1_000_000L;
    /** Maximum nesting depth tracked. Deeper calls are counted but not timed. */
    public static final int MAX_DEPTH = 64;

    private final Map<ProfileKey, Accumulator> accumulators = new HashMap<>(1024);
    private final Function<ProfileKey, Accumulator> accumulatorFactory = k -> new Accumulator(k, typeStats(k));
    private final ArrayList<Accumulator> touched = new ArrayList<>(1024);
    private final Map<String, TypeStats> types = new HashMap<>(256);
    private final ArrayList<TypeStats> touchedTypes = new ArrayList<>(256);
    private final MeshStats mesh = new MeshStats();

    private final Accumulator[] stack = new Accumulator[MAX_DEPTH];
    private final long[] stackStart = new long[MAX_DEPTH];
    private final Bucket[] stackBucket = new Bucket[MAX_DEPTH];
    private int depth;
    private long timedCalls;
    private long vertexCalls;

    /** Set by the Iris integration (phase 2) while the shadow pass is rendering. */
    public boolean shadowPass;

    /** Frames that started with a call still open, because a hook threw past its end(). The stack is reset. */
    private int unbalancedFrames;

    private int visualSamples;
    private long visualBlockEntities;
    private long visualEntities;
    private long visualEffects;
    private final LinkedHashMap<String, Long> visualByClass = new LinkedHashMap<>();

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

    /** Number of vertex-counting calls, for the overhead estimate (spec addendum §P4.A5). */
    public long vertexCalls() {
        return vertexCalls;
    }

    /** Number of frame boundaries at which the attribution stack was found unbalanced and reset. */
    public int unbalancedFrames() {
        return unbalancedFrames;
    }

    /**
     * Records one sample of Flywheel visual counts (spec §4.2). Called about once per second by the
     * controller, so it may allocate. Classes absent from a sample count as zero for that sample.
     */
    public void recordVisualCounts(int blockEntities, int entities, int effects, Map<String, Integer> byClass) {
        visualSamples++;
        visualBlockEntities += blockEntities;
        visualEntities += entities;
        visualEffects += effects;
        for (Map.Entry<String, Integer> e : byClass.entrySet()) {
            visualByClass.merge(e.getKey(), (long) e.getValue(), Long::sum);
        }
    }

    private FlywheelStats flywheelStats() {
        if (visualSamples == 0) return FlywheelStats.none();
        LinkedHashMap<String, Double> avg = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : visualByClass.entrySet()) {
            avg.put(e.getKey(), (double) e.getValue() / visualSamples);
        }
        return new FlywheelStats(visualSamples,
                (double) visualBlockEntities / visualSamples,
                (double) visualEntities / visualSamples,
                (double) visualEffects / visualSamples,
                Map.copyOf(avg));
    }

    /** Snapshot of all accumulators; safe to call after the session is finished. */
    public Map<ProfileKey, Accumulator> accumulators() {
        return Map.copyOf(accumulators);
    }

    /** Returns the accumulator for a key, creating it on first sight. Hooks cache the result per instance. */
    public Accumulator accumulator(ProfileKey key) {
        return accumulators.computeIfAbsent(key, accumulatorFactory);
    }

    /**
     * The aggregation shared by every key of one category and type id. Resolved once, when an accumulator is
     * created, so the per-frame roll-up in {@link #closeFrame} reaches a type by one field dereference rather
     * than a map lookup per touched key (spec addendum §P4.A2).
     */
    private TypeStats typeStats(ProfileKey key) {
        return types.computeIfAbsent(key.category().ordinal() + ":" + key.typeId(),
                k -> new TypeStats(key.typeId(), key.category()));
    }

    /** Mesh-build accumulators, safe to write from any thread (spec §3.4). */
    public MeshStats mesh() {
        return mesh;
    }

    /**
     * Counts one vertex on the innermost open call if it has a key (spec §4.3). Render thread only. A keyless
     * inner timing (bucket-only work) hides the outer key, and vertices emitted deeper than {@link #MAX_DEPTH}
     * are dropped rather than credited to an ancestor, matching {@link #end()}. The call is counted for the
     * overhead estimate whether or not it lands on a key, because it cost the same either way.
     */
    public void vertex() {
        vertexCalls++;
        if (depth == 0 || depth > MAX_DEPTH) return;
        Accumulator a = stack[depth - 1];
        if (a != null) a.vertices++;
    }

    /** Counts {@code n} vertices on the innermost open call if it has a key; same rules as {@link #vertex()}. */
    public void vertices(int n) {
        vertexCalls++;
        if (depth == 0 || depth > MAX_DEPTH) return;
        Accumulator a = stack[depth - 1];
        if (a != null) a.vertices += n;
    }

    /** Freeze the session into a result. The frame currently open is dropped because it has no end. */
    public SessionResult finish() {
        List<FrameRecord> frameList = List.copyOf(frames);
        FrameStats stats = FrameStats.of(frameList);
        long threshold = HitchDetector.threshold(stats.p50Nanos(), config.hitchMedianMultiplier(), config.hitchMinExcessNanos());
        List<FrameRecord> hitches = HitchDetector.detect(frameList, threshold);
        double overheadMs = (timedCalls * config.calibratedCallNanos()
                + vertexCalls * config.calibratedVertexNanos()) / 1_000_000.0;
        return new SessionResult(config, wallStartMillis, frameList, accumulators(), stats, threshold, hitches,
                timedCalls, vertexCalls, overheadMs, unbalancedFrames, flywheelStats(), mesh.snapshot());
    }

    /**
     * Start timing a call attributed to {@code a} in {@code bucket}. Must be paired with {@link #end()}.
     * {@code a} may be null for work that has no key (light updates, Flywheel, chunk upload): the bucket is
     * still credited when the call is outermost, but nothing is added to an accumulator or to the frame's top list.
     */
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

        if (a != null) {
            if (bucket.isRender()) {
                a.renderNanos += elapsed;
                a.calls++;
                if (shadowPass) a.shadowNanos += elapsed;
            } else {
                a.tickNanos += elapsed;
                a.tickCalls++;
            }
        }

        if (frameStartNanos >= 0) {
            if (a != null) {
                if (!a.inFrame) {
                    a.inFrame = true;
                    touched.add(a);
                }
                a.frameNanos += elapsed;
            }
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

        if (depth != 0) {
            unbalancedFrames++;
            depth = 0;
            Arrays.fill(stack, null);
        }

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
        // Spec addendum P4.A0. Long.MAX_VALUE means "nothing in this frame can be suspect".
        long gcNanos = gcMillis * 1_000_000L;
        long suspectAt = gcNanos < GC_MIN_NANOS ? Long.MAX_VALUE : gcNanos - GC_SLACK_NANOS;

        int n = config.topContributorsPerFrame();
        Accumulator[] topAcc = new Accumulator[n];
        int topSize = 0;

        for (int i = 0, size = touched.size(); i < size; i++) {
            Accumulator a = touched.get(i);
            long fn = a.frameNanos;
            if (fn >= suspectAt) {
                a.gcFrames++;
            } else if (fn > a.maxFrameNanos) {
                a.maxFrameNanos = fn;
            }

            // The type's frame total is the sum of its members', gathered here rather than in end() so no
            // work lands inside an enclosing call's timing window.
            TypeStats ts = a.type();
            if (!ts.inFrame) {
                ts.inFrame = true;
                touchedTypes.add(ts);
            }
            ts.frameNanos += fn;

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

        // same rule as the accumulator loop above, at type granularity
        for (int i = 0, size = touchedTypes.size(); i < size; i++) {
            TypeStats ts = touchedTypes.get(i);
            long fn = ts.frameNanos;
            if (fn >= suspectAt) {
                ts.gcFrames++;
            } else if (fn > ts.maxFrameNanos) {
                ts.maxFrameNanos = fn;
            }
            ts.frameNanos = 0;
            ts.inFrame = false;
        }
        touchedTypes.clear();

        List<TopEntry> top = new ArrayList<>(topSize);
        for (int i = 0; i < topSize; i++) {
            long fn = topAcc[i].frameNanos;
            top.add(new TopEntry(topAcc[i].key, fn, fn >= suspectAt));
        }

        // Runs last: the top-entry loop above still needs each accumulator's frameNanos.
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

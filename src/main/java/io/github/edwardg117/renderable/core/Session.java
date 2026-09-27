package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Live sampling state for one session (spec §3). All methods are called from the render thread only.
 * The clock is injected so tests can drive it.
 */
public final class Session {
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private final SessionConfig config;
    private final LongSupplier clock;
    private final long wallStartMillis;
    /** Identifies this session for staleness checks on accumulators cached outside it (spec addendum §P4.B2). */
    private final long id = NEXT_ID.incrementAndGet();

    private final ArrayList<FrameRecord> frames = new ArrayList<>(4096);
    private final List<FrameRecord> framesView = Collections.unmodifiableList(frames);

    private long sessionStartNanos = -1;
    private long frameStartNanos = -1;
    private long renderStartNanos;
    private long tickStartNanos = -1;
    private long renderNanos;
    private long tickNanos;
    private long tasksStartNanos = -1;
    private long capStartNanos = -1;
    /** True from a frame's start to its render end: the render window (spec addendum §P5.A6). */
    private boolean renderOpen;
    private long tasksNanos;
    private long capNanos;
    /**
     * Nanoseconds per {@link Window} that an outermost attribution-only call accounts for in the current frame
     * (spec addendum §P5.A6).
     */
    private final long[] named = new long[Window.COUNT];
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
    private final Function<ProfileKey, Accumulator> accumulatorFactory = k -> new Accumulator(k, typeStats(k), id);
    private final ArrayList<Accumulator> touched = new ArrayList<>(1024);
    private final Map<String, TypeStats> types = new HashMap<>(256);
    private final ArrayList<TypeStats> touchedTypes = new ArrayList<>(256);
    private final MeshStats mesh = new MeshStats();

    private final Accumulator[] stack = new Accumulator[MAX_DEPTH];
    private final long[] stackStart = new long[MAX_DEPTH];
    /** Null marks an attribution-only call (spec addendum §P5.A6). */
    private final Bucket[] stackBucket = new Bucket[MAX_DEPTH];
    /** Where {@link #innermostOrdinary} stood before the call at each index began, for end() to restore. */
    private final int[] stackPrevOrdinary = new int[MAX_DEPTH];
    /** Open tracked calls that own a bucket: every call except attribution-only ones. */
    private int ordinaryDepth;
    /** Stack index of the innermost open call that owns a bucket, or -1: where vertices land. */
    private int innermostOrdinary = -1;
    /** True while an attribution-only call that nothing encloses is open; its time is named time. */
    private boolean namedOpen;
    private int namedWindow;
    private long namedBucketMark;
    /** Bucket nanoseconds credited so far in this frame, so named time can leave out what was bucketed. */
    private long bucketTotal;
    private int depth;
    private long timedCalls;
    private long vertexCalls;
    private long selfNanos;
    private long selfStartNanos = -1;
    private int selfDepth;

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

    /** Identifies this session for staleness checks on accumulators cached outside it (spec addendum §P4.B2). */
    public long id() {
        return id;
    }

    /** True when this accumulator was created by this session; see {@link Accumulator#sessionId()}. */
    public boolean owns(Accumulator a) {
        return a.sessionId() == id;
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

    /**
     * Starts timing work the mod does for itself inside a frame: the sampling HUD line, the Flywheel visual
     * sample (spec addendum §P4.B3) and the once-a-second event-list check (spec addendum §P5.A3). It is not
     * attributed to any key: the HUD line is inside the HUD bucket and the other two are in other render, and
     * the report states the total so a reader can subtract it. Pair with {@link #endSelf()}.
     *
     * <p>Nests: an inner begin/end pair inside an outer one does not truncate the outer interval, matching
     * {@link #begin} and {@link #end}. Everything timed here must be named in the self-time line printed by
     * {@code TextReportWriter} and {@code Rows} ("its HUD line, the Flywheel sample and the event-list check"
     * as of this writing); wrapping another region without updating both labels (and the README) makes that
     * text silently false.
     */
    public void beginSelf() {
        if (selfDepth++ == 0) selfStartNanos = clock.getAsLong();
    }

    /** Ends a self-timing. An end with nothing open is ignored. */
    public void endSelf() {
        if (selfDepth == 0) return;
        if (--selfDepth == 0) {
            selfNanos += clock.getAsLong() - selfStartNanos;
            selfStartNanos = -1;
        }
    }

    /** Nanoseconds the mod spent on its own in-frame work. */
    public long selfNanos() {
        return selfNanos;
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

    /**
     * Snapshot of the accumulators with at least one completed call; safe to call after the session is finished.
     * An accumulator can exist without one: event timing resolves each proxy's accumulator when it builds the
     * proxy (spec addendum §P5.A3), whether or not that handler ever runs on the render thread, and a call that
     * never ends (still open at the finish, or reset after a hook threw) completes nothing. Such a key has no time
     * to report, so it gets no row in any table, report section or JSON array.
     */
    public Map<ProfileKey, Accumulator> accumulators() {
        HashMap<ProfileKey, Accumulator> called = new HashMap<>();
        for (Map.Entry<ProfileKey, Accumulator> e : accumulators.entrySet()) {
            Accumulator a = e.getValue();
            if (a.calls > 0 || a.tickCalls > 0) called.put(e.getKey(), a);
        }
        return Map.copyOf(called);
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
     * Counts one vertex on the innermost open call that owns a bucket, if that call has a key (spec §4.3).
     * Render thread only. Attribution-only calls are skipped (spec addendum §P5.A6), so a handler drawing
     * inside an entity's render adds to the entity's count, as it did before handlers were timed. A keyless
     * inner timing (bucket-only work) hides the outer key, and vertices emitted deeper than {@link #MAX_DEPTH}
     * are dropped rather than credited to an ancestor, matching {@link #end()}. The call is counted for the
     * overhead estimate whether or not it lands on a key, because it cost the same either way.
     */
    public void vertex() {
        vertexCalls++;
        if (depth > MAX_DEPTH || innermostOrdinary < 0) return;
        Accumulator a = stack[innermostOrdinary];
        if (a != null) a.vertices++;
    }

    /** Counts {@code n} vertices on the same call {@link #vertex()} would; same rules. */
    public void vertices(int n) {
        vertexCalls++;
        if (depth > MAX_DEPTH || innermostOrdinary < 0) return;
        Accumulator a = stack[innermostOrdinary];
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
                timedCalls, vertexCalls, selfNanos, overheadMs, unbalancedFrames, flywheelStats(), mesh.snapshot());
    }

    /**
     * Start timing a call attributed to {@code a} in {@code bucket}. Must be paired with {@link #end()}.
     * {@code a} may be null for work that has no key (light updates, Flywheel, chunk upload, the HUD): the
     * bucket is still credited when no other bucket-owning call encloses it, but nothing is added to an
     * accumulator or to the frame's top list.
     */
    public void begin(Accumulator a, Bucket bucket) {
        long now = clock.getAsLong();
        if (depth < MAX_DEPTH) {
            stack[depth] = a;
            stackStart[depth] = now;
            stackBucket[depth] = bucket;
            stackPrevOrdinary[depth] = innermostOrdinary;
            innermostOrdinary = depth;
            ordinaryDepth++;
        }
        depth++;
    }

    /**
     * Start timing an attribution-only call (spec addendum §P5.A6): a mod's event handler, a HUD layer or a
     * packet. It is timed and ranked like any keyed call, but it owns no bucket. A bucket is credited by the
     * outermost call that owns one, so a block entity drawn inside a handler still counts as block entity
     * time, and vertices still land on the innermost call that owns a bucket. An attribution-only call that
     * nothing encloses is named time for the window it runs in. Pair with {@link #end()}.
     */
    public void beginAttribution(Accumulator a) {
        long now = clock.getAsLong();
        if (depth < MAX_DEPTH) {
            stack[depth] = a;
            stackStart[depth] = now;
            stackBucket[depth] = null;
            if (depth == 0 && frameStartNanos >= 0) {
                int w = window();
                if (w >= 0) {
                    namedOpen = true;
                    namedWindow = w;
                    namedBucketMark = bucketTotal;
                }
            }
        }
        depth++;
    }

    /** The window open right now: the tick, tasks or the render window, or -1 outside all three. */
    private int window() {
        if (tickStartNanos >= 0) return Window.TICK.ordinal();
        if (tasksStartNanos >= 0) return Window.TASKS.ordinal();
        if (renderOpen) return Window.RENDER.ordinal();
        return -1;
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
        boolean ownsBucket = bucket != null;
        if (ownsBucket) {
            ordinaryDepth--;
            innermostOrdinary = stackPrevOrdinary[depth];
        }

        if (a != null) {
            // An attribution-only call has no bucket to say which side it is on, so the window decides: a call
            // inside the client tick is tick time, and anything else is on the render side.
            boolean render = ownsBucket ? bucket.isRender() : tickStartNanos < 0;
            if (render) {
                a.renderNanos += elapsed;
                a.calls++;
                if (shadowPass && ownsBucket) a.shadowNanos += elapsed;
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
            if (ownsBucket && ordinaryDepth == 0) {
                buckets[bucket.ordinal()] += elapsed;
                bucketTotal += elapsed;
            }
            if (namedOpen && depth == 0) {
                named[namedWindow] += Math.max(0, elapsed - (bucketTotal - namedBucketMark));
                namedOpen = false;
            }
        }
    }

    /**
     * Called just before NeoForge posts RenderFrameEvent.Pre (spec addendum §P5.A2), with the JVM's cumulative
     * GC count and pause time.
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
            ordinaryDepth = 0;
            innermostOrdinary = -1;
            namedOpen = false;
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
        bucketTotal = 0;
        tasksNanos = 0;
        capNanos = 0;
        Arrays.fill(named, 0L);
        renderOpen = true;
        return done;
    }

    /** Called just after NeoForge posts RenderFrameEvent.Post. */
    public void onRenderPost() {
        if (frameStartNanos < 0) return;
        renderNanos += clock.getAsLong() - renderStartNanos;
        renderOpen = false;
    }

    /** Called just before NeoForge posts ClientTickEvent.Pre. */
    public void onTickPre() {
        tickStartNanos = clock.getAsLong();
    }

    /** Called just after NeoForge posts ClientTickEvent.Post. Ticks before the first frame are ignored. */
    public void onTickPost() {
        if (tickStartNanos < 0) return;
        long elapsed = clock.getAsLong() - tickStartNanos;
        tickStartNanos = -1;
        if (frameStartNanos >= 0) {
            tickNanos += elapsed;
        }
    }

    /** Called just before {@code Minecraft.runAllTasks}: packets and other queued work (spec addendum §P5.A1). */
    public void onTasksBegin() {
        tasksStartNanos = clock.getAsLong();
    }

    /** Called just after {@code Minecraft.runAllTasks}. Tasks before the first frame are ignored. */
    public void onTasksEnd() {
        if (tasksStartNanos < 0) return;
        long elapsed = clock.getAsLong() - tasksStartNanos;
        tasksStartNanos = -1;
        if (frameStartNanos >= 0) tasksNanos += elapsed;
    }

    /** Called just before {@code RenderSystem.limitDisplayFPS}: the frame-rate cap's wait (spec addendum §P5.A1). */
    public void onCapBegin() {
        capStartNanos = clock.getAsLong();
    }

    /** Called just after {@code RenderSystem.limitDisplayFPS}. A wait before the first frame is ignored. */
    public void onCapEnd() {
        if (capStartNanos < 0) return;
        long elapsed = clock.getAsLong() - capStartNanos;
        capStartNanos = -1;
        if (frameStartNanos >= 0) capNanos += elapsed;
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
                tickNanos, renderNanos, tasksNanos, capNanos, buckets.clone(), named.clone(), gcCount, gcMillis,
                List.copyOf(top)));
    }
}

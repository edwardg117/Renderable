package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    static final long MS = 1_000_000L;

    final long[] now = {0};
    final LongSupplier clock = () -> now[0];

    Session session(long durationMs) {
        return new Session(new SessionConfig(durationMs * MS, 2.0, 16 * MS, 5, 0, 0), clock, 1_700_000_000_000L);
    }

    void at(long ms) {
        now[0] = ms * MS;
    }

    @Test
    void firstRenderPreOpensSessionWithoutRecordingAFrame() {
        Session s = session(10_000);
        at(0);
        assertFalse(s.onRenderPre(0, 0));
        assertEquals(0, s.frameCount());
        at(5);
        assertEquals(5 * MS, s.elapsedNanos());
    }

    @Test
    void frameSplitsIntoRenderTickAndSwap() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(4);  s.onRenderPost();
        at(5);  s.onTickPre();
        at(7);  s.onTickPost();
        at(10); s.onRenderPre(0, 0);

        assertEquals(1, s.frameCount());
        FrameRecord f = s.framesView().get(0);
        assertEquals(0, f.index());
        assertEquals(0, f.startNanos());
        assertEquals(10 * MS, f.totalNanos());
        assertEquals(4 * MS, f.renderNanos());
        assertEquals(2 * MS, f.tickNanos());
        assertEquals(4 * MS, f.swapNanos());
        assertEquals(4 * MS, f.otherRenderNanos());
        assertEquals(2 * MS, f.otherTickNanos());
    }

    @Test
    void secondFrameStartsAtOffsetOfFirstFrameEnd() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(10); s.onRenderPre(0, 0);
        at(25); s.onRenderPre(0, 0);
        FrameRecord f1 = s.framesView().get(1);
        assertEquals(1, f1.index());
        assertEquals(10 * MS, f1.startNanos());
        assertEquals(15 * MS, f1.totalNanos());
    }

    @Test
    void tickBeforeFirstFrameIsIgnored() {
        Session s = session(10_000);
        at(0);  s.onTickPre();
        at(2);  s.onTickPost();
        at(3);  s.onRenderPre(0, 0);
        at(4);  s.onRenderPost();
        at(10); s.onRenderPre(0, 0);
        assertEquals(0, s.framesView().get(0).tickNanos());
    }

    @Test
    void finishesWhenDurationElapses() {
        Session s = session(1000);
        at(0);    assertFalse(s.onRenderPre(0, 0));
        at(500);  assertFalse(s.onRenderPre(0, 0));
        at(999);  assertFalse(s.onRenderPre(0, 0));
        at(1000); assertTrue(s.onRenderPre(0, 0));
        assertEquals(3, s.frameCount());
    }

    @Test
    void gcDeltaIsAttributedToTheFrameThatJustClosed() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(3, 100);
        at(10); s.onRenderPre(4, 112);
        at(20); s.onRenderPre(4, 112);
        assertEquals(1, s.framesView().get(0).gcCount());
        assertEquals(12, s.framesView().get(0).gcMillis());
        assertEquals(0, s.framesView().get(1).gcCount());
        assertEquals(0, s.framesView().get(1).gcMillis());
    }

    @Test
    void derivedValuesNeverGoNegative() {
        FrameRecord f = new FrameRecord(0, 0, 5 * MS, 4 * MS, 3 * MS, new long[Bucket.COUNT], 0, 0, List.of());
        assertEquals(0, f.swapNanos());

        long[] b = new long[Bucket.COUNT];
        b[Bucket.BLOCK_ENTITY.ordinal()] = 9 * MS;
        FrameRecord g = new FrameRecord(0, 0, 10 * MS, 0, 5 * MS, b, 0, 0, List.of());
        assertEquals(0, g.otherRenderNanos());
        assertEquals(9 * MS, g.bucket(Bucket.BLOCK_ENTITY));
    }

    @Test
    void configRejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> new SessionConfig(0, 2.0, 0, 5, 0, 0));
        SessionConfig c = SessionConfig.ofSeconds(30, 2.0, 16, 5, 25, 2);
        assertEquals(30_000 * MS, c.durationNanos());
        assertEquals(16 * MS, c.hitchMinExcessNanos());
        assertEquals(30.0, c.durationSeconds(), 1e-9);
    }

    final BlockEntityKey press = new BlockEntityKey("create:mechanical_press", "minecraft:overworld", 1, 2, 3);
    final BlockEntityKey mixer = new BlockEntityKey("create:mechanical_mixer", "minecraft:overworld", 4, 5, 6);
    final EntityKey cow = new EntityKey("minecraft:cow", 7, "");

    @Test
    void callsAttributeToKeyAndOnlyOutermostBucket() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        Accumulator a = s.accumulator(press);
        Accumulator b = s.accumulator(cow);
        at(1);  s.begin(a, Bucket.BLOCK_ENTITY);
        at(3);  s.begin(b, Bucket.ENTITY);      // nested inside the block entity
        at(6);  s.end();                        // cow: 3 ms
        at(11); s.end();                        // press: 10 ms including the cow
        at(12); s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        assertEquals(10 * MS, a.renderNanos);
        assertEquals(1, a.calls);
        assertEquals(3 * MS, b.renderNanos);
        assertEquals(2, s.timedCalls());

        FrameRecord f = s.framesView().get(0);
        assertEquals(10 * MS, f.bucket(Bucket.BLOCK_ENTITY));
        assertEquals(0, f.bucket(Bucket.ENTITY), "inner call must not be counted in a second bucket");
        assertEquals(2 * MS, f.otherRenderNanos());
        assertSame(a, s.accumulator(press), "same key returns the same accumulator");
    }

    @Test
    void tickBucketsGoToTickNanos() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        Accumulator a = s.accumulator(press);
        at(1); s.begin(a, Bucket.BE_TICK);
        at(3); s.end();
        at(10); s.onRenderPre(0, 0);
        assertEquals(2 * MS, a.tickNanos);
        assertEquals(0, a.renderNanos);
        assertEquals(1, a.tickCalls);
        assertEquals(0, a.calls);
        assertEquals(2 * MS, s.framesView().get(0).bucket(Bucket.BE_TICK));
    }

    @Test
    void topContributorsPerFrameAreRankedAndReset() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        Accumulator b = s.accumulator(mixer);
        Accumulator c = s.accumulator(cow);

        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(b, Bucket.BLOCK_ENTITY); at(4); s.end();   // mixer 3
        at(4); s.begin(a, Bucket.BLOCK_ENTITY); at(9); s.end();   // press 5
        at(9); s.begin(c, Bucket.ENTITY);       at(10); s.end();  // cow 1
        at(20); s.onRenderPre(0, 0);

        List<TopEntry> top = s.framesView().get(0).top();
        assertEquals(3, top.size());
        assertEquals(press, top.get(0).key());
        assertEquals(5 * MS, top.get(0).nanos());
        assertEquals(mixer, top.get(1).key());
        assertEquals(cow, top.get(2).key());
        assertEquals(0, a.frameNanos(), "frame scratch must reset at the boundary");

        at(21); s.begin(b, Bucket.BLOCK_ENTITY); at(23); s.end(); // mixer 2, only key this frame
        at(40); s.onRenderPre(0, 0);
        List<TopEntry> top2 = s.framesView().get(1).top();
        assertEquals(1, top2.size());
        assertEquals(mixer, top2.get(0).key());
        assertEquals(2 * MS, top2.get(0).nanos());

        assertEquals(5 * MS, a.maxFrameNanos);
        assertEquals(3 * MS, b.maxFrameNanos, "peak is the biggest single frame, not the total");
    }

    @Test
    void topListIsCappedAtConfiguredSize() {
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 2, 0, 0), clock, 0);
        at(0); s.onRenderPre(0, 0);
        for (int i = 0; i < 5; i++) {
            Accumulator a = s.accumulator(new EntityKey("minecraft:zombie", i, ""));
            at(10 * i); s.begin(a, Bucket.ENTITY); at(10 * i + i + 1); s.end(); // costs 1..5 ms
        }
        at(100); s.onRenderPre(0, 0);
        List<TopEntry> top = s.framesView().get(0).top();
        assertEquals(2, top.size());
        assertEquals(5 * MS, top.get(0).nanos());
        assertEquals(4 * MS, top.get(1).nanos());
    }

    @Test
    void sameKeyTwiceInOneFrameSumsInTop() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(a, Bucket.BLOCK_ENTITY); at(3); s.end();
        at(5); s.begin(a, Bucket.BLOCK_ENTITY); at(9); s.end();
        at(20); s.onRenderPre(0, 0);
        List<TopEntry> top = s.framesView().get(0).top();
        assertEquals(1, top.size());
        assertEquals(6 * MS, top.get(0).nanos());
        assertEquals(2, a.calls);
    }

    @Test
    void shadowPassAddsToShadowNanos() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(a, Bucket.BLOCK_ENTITY); at(3); s.end();
        s.shadowPass = true;
        at(4); s.begin(a, Bucket.BLOCK_ENTITY); at(5); s.end();
        s.shadowPass = false;
        assertEquals(3 * MS, a.renderNanos);
        assertEquals(1 * MS, a.shadowNanos);
    }

    @Test
    void endWithoutBeginIsIgnored() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        assertDoesNotThrow(s::end);
        assertEquals(0, s.timedCalls());
    }

    @Test
    void nestingDeeperThanTheStackIsSafe() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1);
        for (int i = 0; i < 100; i++) s.begin(a, Bucket.BLOCK_ENTITY);
        at(2);
        for (int i = 0; i < 100; i++) s.end();
        at(10); s.onRenderPre(0, 0);
        assertEquals(Session.MAX_DEPTH, a.calls, "calls beyond the stack are dropped, not misattributed");
        assertEquals(1 * MS, s.framesView().get(0).bucket(Bucket.BLOCK_ENTITY));
    }

    @Test
    void callsBeforeFirstFrameCountTowardsKeyButNotFrame() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.begin(a, Bucket.BLOCK_ENTITY); at(2); s.end();
        at(3); s.onRenderPre(0, 0);
        at(10); s.onRenderPre(0, 0);
        assertEquals(2 * MS, a.renderNanos);
        assertEquals(0, s.framesView().get(0).bucket(Bucket.BLOCK_ENTITY));
        assertTrue(s.framesView().get(0).top().isEmpty());
    }

    @Test
    void finishProducesResultWithHitchesAndOverhead() {
        SessionResult r = TestSessions.sample();
        assertEquals(20, r.frameCount());
        assertEquals(384 * MS, r.stats().totalNanos());
        assertEquals(16 * MS, r.stats().p50Nanos());
        assertEquals(32 * MS, r.hitchThresholdNanos());
        assertEquals(1, r.hitches().size());
        assertEquals(10, r.hitches().get(0).index());
        assertEquals(80 * MS, r.hitches().get(0).totalNanos());
        assertEquals(TestSessions.PRESS, r.hitches().get(0).top().get(0).key());
        assertEquals(60 * MS, r.hitches().get(0).top().get(0).nanos());
        assertEquals(120, r.timedCalls());
        assertEquals(0.003, r.overheadEstimateMs(), 1e-9);
        assertEquals(5, r.hitchFlaggedKeys().size());
        assertTrue(r.hitchFlaggedKeys().contains(TestSessions.PRESS));
        assertEquals(98 * MS, r.accumulators().get(TestSessions.PRESS).renderNanos);
        assertEquals(4 * MS, r.accumulators().get(TestSessions.COW).tickNanos);
        assertEquals(1, r.frames().get(15).gcCount());
        assertEquals(5, r.frames().get(15).gcMillis());
        assertEquals(TestSessions.WALL_START, r.wallStartMillis());
    }

    @Test
    void bucketOnlyTimingFillsTheBucketWithoutAKey() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(null, Bucket.LIGHT); at(4); s.end();
        at(5); s.begin(null, Bucket.FLYWHEEL);
        at(6); s.begin(s.accumulator(press), Bucket.BLOCK_ENTITY); at(7); s.end(); // nested inside flywheel
        at(9); s.end();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(3 * MS, f.bucket(Bucket.LIGHT));
        assertEquals(4 * MS, f.bucket(Bucket.FLYWHEEL));
        assertEquals(0, f.bucket(Bucket.BLOCK_ENTITY), "inner keyed call is not counted in a second bucket");
        assertEquals(1 * MS, s.accumulator(press).renderNanos);
        assertEquals(3, s.timedCalls());
        assertEquals(1, f.top().size(), "bucket-only timings never appear in top");
        assertEquals(press, f.top().get(0).key());
        assertTrue(s.accumulators().values().stream().noneMatch(a -> a.key == null));
    }

    @Test
    void unbalancedStackIsResetAtTheFrameBoundary() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(a, Bucket.BLOCK_ENTITY);            // a hook threw past its end()
        at(10); s.onRenderPre(0, 0);
        assertEquals(1, s.unbalancedFrames());

        at(11); s.begin(a, Bucket.BLOCK_ENTITY); at(13); s.end();
        at(20); s.onRenderPre(0, 0);
        assertEquals(2 * MS, s.framesView().get(1).bucket(Bucket.BLOCK_ENTITY), "attribution works again after the reset");
        assertEquals(1, s.unbalancedFrames());
        assertEquals(1, s.finish().unbalancedFrames());
    }

    @Test
    void visualCountsAverageOverSamples() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        s.recordVisualCounts(100, 10, 0, java.util.Map.of("a.PressVisual", 100, "b.FanVisual", 5));
        s.recordVisualCounts(140, 10, 2, java.util.Map.of("a.PressVisual", 140));
        at(10); s.onRenderPre(0, 0);
        FlywheelStats fw = s.finish().flywheel();
        assertEquals(2, fw.samples());
        assertEquals(120.0, fw.avgBlockEntities(), 1e-9);
        assertEquals(10.0, fw.avgEntities(), 1e-9);
        assertEquals(1.0, fw.avgEffects(), 1e-9);
        assertEquals(120.0, fw.avgByClass().get("a.PressVisual"), 1e-9);
        assertEquals(2.5, fw.avgByClass().get("b.FanVisual"), 1e-9, "a class absent from a sample counts as zero");
        assertEquals(131.0, fw.total(), 1e-9);

        assertEquals(0, TestSessions.sample().flywheel().samples());
        assertTrue(TestSessions.sample().flywheel().avgByClass().isEmpty());
    }

    @Test
    void verticesCountOnTheInnermostKeyedCall() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        Accumulator b = s.accumulator(cow);
        at(0); s.onRenderPre(0, 0);
        s.vertex();                                   // outside any call: ignored
        at(1); s.begin(a, Bucket.BLOCK_ENTITY);
        s.vertex(); s.vertex(); s.vertex();
        at(2); s.begin(b, Bucket.ENTITY);
        s.vertex(); s.vertex();
        at(3); s.end();
        s.vertex();
        at(4); s.end();
        at(5); s.begin(null, Bucket.LIGHT);
        s.vertex();                                   // bucket-only timing has no key: ignored
        at(6); s.end();
        at(10); s.onRenderPre(0, 0);
        assertEquals(4, a.vertices);
        assertEquals(2, b.vertices);
        assertEquals(8, s.vertexCalls(), "counted whether or not the call landed on a key");
        assertTrue(s.finish().mesh().isEmpty(), "mesh is empty for a session with no mesh work");
    }

    @Test
    void batchedVerticesFollowTheSameRules() {
        Session s = session(10_000);
        Accumulator a = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        s.vertices(7);                                // outside any call: ignored
        at(1); s.begin(a, Bucket.BLOCK_ENTITY);
        s.vertices(24);
        s.vertex();
        at(2); s.begin(null, Bucket.LIGHT);
        s.vertices(5);                                // keyless inner timing: ignored
        at(3); s.end();
        at(4); s.end();
        at(10); s.onRenderPre(0, 0);
        assertEquals(25, a.vertices);
        assertEquals(4, s.vertexCalls(), "a batched call counts once, keyed or not");
    }

    @Test
    void meshStatsSurviveIntoTheResult() {
        Session s = session(10_000);
        at(0); s.onRenderPre(0, 0);
        s.mesh().get(new BlockKey("minecraft:stone")).add(4 * MS);
        at(10); s.onRenderPre(0, 0);
        List<MeshEntry> mesh = s.finish().mesh();
        assertEquals(1, mesh.size());
        assertEquals(new BlockKey("minecraft:stone"), mesh.get(0).key());
        assertEquals(4 * MS, mesh.get(0).nanos());
    }

    @Test
    void gcSuspectFramesDoNotSetTheKeyPeak() {
        // onRenderPre takes the JVM's CUMULATIVE counters, and the delta is credited to the frame it closes.
        Session s = session(10_000);
        Accumulator a = s.accumulator(TestSessions.PRESS);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(a, Bucket.BLOCK_ENTITY); at(4); s.end();      // 3 ms
        at(16); s.onRenderPre(0, 0);                                   // closes a clean frame: the peak is 3 ms
        at(17); s.begin(a, Bucket.BLOCK_ENTITY); at(57); s.end();      // 40 ms
        at(80); s.onRenderPre(1, 40);                                  // that frame paused for 40 ms: suspect
        at(81); s.begin(a, Bucket.BLOCK_ENTITY); at(83); s.end();      // 2 ms
        at(96); s.onRenderPre(1, 40);                                  // cumulative unchanged, so a clean frame

        assertEquals(3 * MS, a.maxFrameNanos, "the 40 ms frame was GC-suspect and must not set the peak");
        assertEquals(1, a.gcFrames, "only the 40 ms frame was GC-suspect");
    }

    @Test
    void gcSuspicionIsBoundedByTheReportedPause() {
        // p = 10 ms, so the threshold is 9 ms: at 9 ms the frame is suspect and the peak stays at the
        // earlier clean frame's 2 ms; one nanosecond below, the frame is clean and sets the peak itself.
        assertEquals(9 * MS - 1, peakAfterOneFrameOf(9 * MS - 1, 10), "one nanosecond below the threshold is clean");
        assertEquals(2 * MS, peakAfterOneFrameOf(9 * MS, 10), "9 ms against a 10 ms pause is suspect");
        // The floor is inclusive on the suspect side: a 2 ms pause arms the rule, a 1 ms pause does not.
        // Without this, a `<=` typo in the floor comparison would pass every other case here.
        assertEquals(2 * MS, peakAfterOneFrameOf(5 * MS, 2), "a 2 ms pause is at the floor, so it arms the rule");
        // p = 1 ms is below the two-millisecond floor, so nothing in that frame is suspect.
        assertEquals(5 * MS, peakAfterOneFrameOf(5 * MS, 1), "a 1 ms pause is below the floor");
        assertEquals(5 * MS, peakAfterOneFrameOf(5 * MS, 0), "no pause at all");
    }

    /**
     * Runs one 2 ms frame, then one frame costing {@code costNanos} closed with a {@code gcMillis} pause,
     * and returns the key's peak. The 2 ms first frame is the clean fallback the peak falls back to.
     */
    long peakAfterOneFrameOf(long costNanos, long gcMillis) {
        Session s = session(10_000);
        Accumulator a = s.accumulator(TestSessions.PRESS);
        now[0] = 0;              s.onRenderPre(0, 0);
        now[0] = 1 * MS;         s.begin(a, Bucket.BLOCK_ENTITY);
        now[0] = 3 * MS;         s.end();
        now[0] = 100 * MS;       s.onRenderPre(0, 0);
        now[0] = 101 * MS;       s.begin(a, Bucket.BLOCK_ENTITY);
        now[0] = 101 * MS + costNanos; s.end();
        now[0] = 300 * MS;       s.onRenderPre(gcMillis == 0 ? 0 : 1, gcMillis);
        return a.maxFrameNanos;
    }

    @Test
    void typePeakIsTheTypesWorstFrameAndTopEntriesCarryTheGcFlag() {
        Session s = session(10_000);
        Accumulator one = s.accumulator(TestSessions.PRESS);
        Accumulator two = s.accumulator(TestSessions.PRESS2);
        Accumulator cow = s.accumulator(TestSessions.COW);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(one, Bucket.BLOCK_ENTITY); at(3);  s.end();    // 2 ms
        at(3);  s.begin(two, Bucket.BLOCK_ENTITY); at(6);  s.end();    // 3 ms; the type cost 5 ms this frame
        at(16); s.onRenderPre(0, 0);
        at(17); s.begin(one, Bucket.BLOCK_ENTITY); at(21); s.end();    // 4 ms alone: this key's worst clean frame
        at(32); s.onRenderPre(0, 0);
        at(33); s.begin(one, Bucket.BLOCK_ENTITY); at(40); s.end();    // 7 ms, but the frame paused for 7 ms
        now[0] = 41 * MS;               s.begin(cow, Bucket.ENTITY);
        now[0] = 41 * MS + 300_000;     s.end();                       // 0.3 ms, well under the 6 ms threshold
        at(48); s.onRenderPre(1, 7);
        SessionResult r = s.finish();

        assertEquals(4 * MS, one.maxFrameNanos, "the 7 ms frame was GC-suspect and did not count");
        assertEquals(1, one.gcFrames, "only the 7 ms frame was GC-suspect for this key");
        assertEquals(3 * MS, two.maxFrameNanos);
        assertEquals(0, two.gcFrames, "press2 was not touched in the GC frame, so it is unaffected");
        assertEquals(5 * MS, one.type().maxFrameNanos, "the type's worst frame, not its worst member");
        assertSame(one.type(), two.type(), "members of a type share one TypeStats");
        assertEquals(1, one.type().gcFrames, "the type saw one GC-suspect frame, matching its member");

        List<TopEntry> gcFrame = r.frames().get(2).top();
        assertEquals(2, gcFrame.size());
        assertEquals(TestSessions.PRESS, gcFrame.get(0).key());
        assertTrue(gcFrame.get(0).gc(), "7 ms on a frame with a 7 ms pause is GC-suspect");
        assertEquals(TestSessions.COW, gcFrame.get(1).key());
        assertFalse(gcFrame.get(1).gc(), "0.3 ms is well under this frame's 6 ms suspect threshold");
        assertFalse(r.frames().get(1).top().get(0).gc(), "the second frame had no pause");
    }

    @Test
    void overheadCountsVertexCallsAsWellAsTimedCalls() {
        long[] clock = {0};
        // 100 ns per begin/end pair, 7 ns per vertex call.
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 100, 7), () -> clock[0], 0);
        Accumulator a = s.accumulator(TestSessions.PRESS);
        clock[0] = 0; s.onRenderPre(0, 0);
        clock[0] = MS; s.begin(a, Bucket.BLOCK_ENTITY);
        s.vertex();
        s.vertex();
        s.vertices(500);                      // one call, five hundred vertices
        clock[0] = 2 * MS; s.end();
        clock[0] = 16 * MS; s.onRenderPre(0, 0);

        SessionResult r = s.finish();
        assertEquals(1, r.timedCalls());
        assertEquals(3, r.vertexCalls(), "a batched call counts once; the cost is per call");
        assertEquals(502, r.accumulators().get(TestSessions.PRESS).vertices);
        assertEquals((1 * 100 + 3 * 7) / 1_000_000.0, r.overheadEstimateMs(), 1e-12);
    }

    @Test
    void anAccumulatorKnowsTheSessionIdThatMadeIt() {
        Session first = session(10_000);
        Session second = session(10_000);
        Accumulator a = first.accumulator(TestSessions.PRESS);

        assertEquals(first.id(), a.sessionId(), "a cache on a long-lived object uses this to spot a stale accumulator");
        assertSame(a, first.accumulator(TestSessions.PRESS), "the same key gives the same accumulator");
        assertNotSame(a, second.accumulator(TestSessions.PRESS), "a new session makes a new one");
        assertNotEquals(first.id(), second.id(), "each session has its own id");
        assertEquals(0, new Accumulator(TestSessions.PRESS).sessionId(), "a standalone accumulator has no session");
        assertNotEquals(0, first.id(), "ids start above zero so a standalone accumulator is never mistaken for a real one");
    }

    @Test
    void sessionOwnsExactlyTheAccumulatorsItMade() {
        Session first = session(10_000);
        Session second = session(10_000);
        Accumulator a = first.accumulator(TestSessions.PRESS);

        assertTrue(first.owns(a), "the session that created it owns it");
        assertFalse(second.owns(a), "a different session does not own it");
        assertFalse(first.owns(new Accumulator(TestSessions.PRESS)), "a standalone accumulator is owned by no session");
    }

    @Test
    void selfTimeIsMeasuredNotEstimated() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.beginSelf();
        at(3);  s.endSelf();                 // 2 ms of HUD work
        at(4);  s.beginSelf();
        at(4);  s.endSelf();                 // an instant sample costs nothing
        at(5);  s.endSelf();                 // unpaired end is ignored
        at(16); s.onRenderPre(0, 0);

        SessionResult r = s.finish();
        assertEquals(2 * MS, r.selfNanos());
        assertEquals(0, r.timedCalls(), "self time is not a keyed call and must not inflate the estimate");
    }

    @Test
    void nestedBeginEndSelfMeasuresTheFullOuterInterval() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.beginSelf();
        at(2);  s.beginSelf();               // inner begin: does not start a second interval
        at(3);  s.endSelf();                 // inner end: does not close the interval yet
        at(5);  s.endSelf();                 // outer end: closes it
        at(16); s.onRenderPre(0, 0);

        assertEquals(4 * MS, s.finish().selfNanos(), "a balanced nesting must not truncate the outer interval");
    }

    @Test
    void tasksAndTheFrameCapAreFrameComponentsOutsideSwap() {
        Session s = session(10_000);
        at(0);  s.onRenderPre(0, 0);
        at(4);  s.onRenderPost();
        at(5);  s.onCapBegin();
        at(9);  s.onCapEnd();
        at(10); s.onTasksBegin();
        at(13); s.onTasksEnd();
        at(14); s.onTickPre();
        at(16); s.onTickPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(4 * MS, f.renderNanos());
        assertEquals(4 * MS, f.capNanos());
        assertEquals(3 * MS, f.tasksNanos());
        assertEquals(2 * MS, f.tickNanos());
        assertEquals(7 * MS, f.swapNanos(), "20 total - 4 render - 4 cap - 3 tasks - 2 tick");
    }

    @Test
    void tasksAndTheCapOutsideAFrameAreIgnored() {
        Session s = session(10_000);
        at(0); s.onTasksBegin(); at(2); s.onTasksEnd();     // before the first frame
        at(2); s.onCapBegin();   at(3); s.onCapEnd();
        at(3); s.onRenderPre(0, 0);
        at(4); s.onTasksEnd();                              // an end with nothing open
        at(5); s.onCapEnd();
        at(10); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(0, f.tasksNanos());
        assertEquals(0, f.capNanos());
        assertEquals(7 * MS, f.swapNanos());
    }

    @Test
    void attributionOnlyCallsLeaveBucketsToTheCallsTheyEnclose() {
        Session s = session(10_000);
        Accumulator handler = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        Accumulator be = s.accumulator(press);
        at(0); s.onRenderPre(0, 0);
        at(1); s.beginAttribution(handler);
        at(2); s.begin(be, Bucket.BLOCK_ENTITY);    // a block entity the handler draws
        at(5); s.end();
        at(6); s.end();
        at(8); s.onRenderPost();
        at(10); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(3 * MS, f.bucket(Bucket.BLOCK_ENTITY), "still block entity time");
        assertEquals(5 * MS, handler.renderNanos, "the handler's own row is inclusive");
        assertEquals(1, handler.calls);
        assertEquals(3 * MS, be.renderNanos);
        assertEquals(2 * MS, f.named(Window.RENDER), "the handler's 5 ms less the 3 ms bucketed inside it");
        assertEquals(5 * MS, f.otherRenderNanos(), "8 ms of render less the 3 ms bucketed");
    }

    @Test
    void anAttributionOnlyCallInsideABucketOwnerCreditsNoBucketAndIsNotNamed() {
        Session s = session(10_000);
        Accumulator layer = s.accumulator(ModKey.layer("xaerominimap", "xaerominimap:minimap"));
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(null, Bucket.HUD);
        at(2); s.beginAttribution(layer);
        at(4); s.end();
        at(5); s.end();
        at(6); s.onRenderPost();
        at(10); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(4 * MS, f.bucket(Bucket.HUD), "the layer's time stays in the HUD bucket");
        assertEquals(2 * MS, layer.renderNanos);
        assertEquals(0, f.named(Window.RENDER), "a bucket owner encloses it, so it is not named");
        assertEquals(layer.key, f.top().get(0).key(), "an attribution-only key is a top contributor like any other");
    }

    @Test
    void verticesSkipAttributionOnlyCalls() {
        Session s = session(10_000);
        Accumulator cowAcc = s.accumulator(cow);
        Accumulator handler = s.accumulator(ModKey.event("somemod", "a.RenderLivingEvent$Pre"));
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(cowAcc, Bucket.ENTITY);
        at(2); s.beginAttribution(handler);
        s.vertex();
        s.vertices(6);                  // drawn by the handler, inside the cow's render
        at(3); s.end();
        at(4); s.end();
        at(5); s.beginAttribution(handler);
        s.vertex();                     // no call that owns a bucket is open: dropped
        at(6); s.end();
        at(10); s.onRenderPre(0, 0);

        assertEquals(7, cowAcc.vertices);
        assertEquals(0, handler.vertices);
        assertEquals(3, s.vertexCalls(), "counted whether or not the call landed on a key");
        assertEquals(3 * MS, s.framesView().get(0).bucket(Bucket.ENTITY));
    }

    @Test
    void namedTimeFollowsTheWindowItRanIn() {
        Session s = session(10_000);
        Accumulator tickHandler = s.accumulator(ModKey.event("somemod", "a.ClientTickEvent$Post"));
        Accumulator packet = s.accumulator(ModKey.packet("minecraft", "minecraft:level_chunk_with_light"));
        Accumulator inputHandler = s.accumulator(ModKey.event("somemod", "a.InputEvent$Key"));
        at(0);  s.onRenderPre(0, 0);
        at(2);  s.onRenderPost();
        at(3);  s.onCapBegin();
        at(3);  s.beginAttribution(inputHandler);
        at(4);  s.end();
        at(5);  s.onCapEnd();
        at(6);  s.onTasksBegin();
        at(6);  s.beginAttribution(packet);
        at(8);  s.end();
        at(9);  s.onTasksEnd();
        at(10); s.onTickPre();
        at(10); s.beginAttribution(tickHandler);
        at(11); s.end();
        at(12); s.onTickPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(2 * MS, f.named(Window.TASKS));
        assertEquals(1 * MS, f.named(Window.TICK));
        assertEquals(0, f.named(Window.RENDER));
        assertEquals(1 * MS, tickHandler.tickNanos, "a call inside the client tick is tick time");
        assertEquals(1, tickHandler.tickCalls);
        assertEquals(2 * MS, packet.renderNanos, "outside the tick, an attribution-only call is on the render side");
        assertEquals(1 * MS, inputHandler.renderNanos, "a handler during the frame-cap wait still counts for its mod");
        long named = 0;
        for (Window w : Window.VALUES) named += f.named(w);
        assertEquals(3 * MS, named, "the frame-cap wait is in no window, so that handler is not named");
    }

    @Test
    void attributionOnlyKeysGetPeaksAndGcFrames() {
        Session s = session(10_000);
        Accumulator h = s.accumulator(ModKey.event("somemod", "a.ClientTickEvent$Post"));
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.onTickPre(); s.beginAttribution(h);
        at(4);  s.end(); s.onTickPost();                      // 3 ms, clean
        at(10); s.onRenderPre(0, 0);
        at(11); s.onTickPre(); s.beginAttribution(h);
        at(51); s.end(); s.onTickPost();                      // 40 ms
        at(60); s.onRenderPre(1, 40);                         // a 40 ms pause in that frame

        RankedEntry row = Ranking.byType(s.finish(), ProfileKey.Category.MOD).get(0);
        assertEquals("somemod", row.typeId());
        assertEquals(3 * MS, row.maxFrameNanos(), "the 40 ms frame is GC-suspect and does not set the peak");
        assertEquals(1, row.gcFrames());
    }

    @Test
    void anUnbalancedAttributionCallIsResetAtTheFrameBoundary() {
        Session s = session(10_000);
        Accumulator h = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(s.accumulator(press), Bucket.BLOCK_ENTITY);
        at(2);  s.end();                                      // bucketed first, so named time left open would show
        at(3);  s.beginAttribution(h);                        // a handler threw past its end()
        at(10); s.onRenderPre(0, 0);
        at(11); s.begin(s.accumulator(press), Bucket.BLOCK_ENTITY);
        at(13); s.end();
        at(20); s.onRenderPre(0, 0);

        assertEquals(1, s.unbalancedFrames());
        FrameRecord f = s.framesView().get(1);
        assertEquals(2 * MS, f.bucket(Bucket.BLOCK_ENTITY), "the bucket rule works again after the reset");
        assertEquals(0, f.named(Window.RENDER));
    }

    @Test
    void phase5FixtureMatchesItsDescription() {
        SessionResult r = TestSessions.phase5();
        assertEquals(2, r.frameCount());
        FrameRecord f = r.frames().get(0);
        assertEquals(20 * MS, f.totalNanos());
        assertEquals(5 * MS, f.renderNanos());
        assertEquals(3 * MS, f.capNanos());
        assertEquals(2 * MS, f.tasksNanos());
        assertEquals(2 * MS, f.tickNanos());
        assertEquals(8 * MS, f.swapNanos());
        assertEquals(500_000, f.bucket(Bucket.ENTITY));
        assertEquals(1 * MS, f.bucket(Bucket.BLOCK_ENTITY));
        assertEquals(2 * MS, f.bucket(Bucket.HUD));
        assertEquals(1 * MS, f.bucket(Bucket.ENTITY_TICK), "the boat only: the rider is nested inside it");
        assertEquals(1_500_000, f.otherRenderNanos());
        assertEquals(1 * MS, f.otherTickNanos());
        assertEquals(1 * MS, f.named(Window.RENDER));
        assertEquals(400_000, f.named(Window.TICK));
        assertEquals(1 * MS, f.named(Window.TASKS));

        Map<ProfileKey, Accumulator> acc = r.accumulators();
        assertEquals(14, acc.get(TestSessions.COW).vertices, "the living-render handler's vertices stay on the cow");
        assertEquals(0, acc.get(TestSessions.LIVING_HANDLER).vertices);
        assertEquals(20, acc.get(TestSessions.PRESS).vertices);
        assertEquals(800_000, acc.get(TestSessions.PLAYER).tickNanos, "the rider has its own tick");
        assertEquals(2 * MS, acc.get(TestSessions.BOAT).tickNanos, "and the vehicle still includes it");

        List<RankedEntry> mods = Ranking.byType(r, ProfileKey.Category.MOD);
        assertEquals(List.of("create", "minecraft", "xaerominimap", "somemod"),
                mods.stream().map(RankedEntry::typeId).toList(), "by total, ties A to Z");
        assertEquals(2 * MS, mods.get(0).maxFrameNanos());
        assertEquals(600_000, mods.get(3).maxFrameNanos(), "somemod's two handlers in one frame: 0.2 + 0.4");
        assertEquals(20, r.timedCalls());
        assertEquals(4, r.vertexCalls());
    }

    @Test
    void aBucketedCallInsideAHudLayerCountsInTheHud() {
        Session s = session(10_000);
        Accumulator layer = s.accumulator(ModKey.layer("somemod", "somemod:companion"));
        Accumulator cowAcc = s.accumulator(cow);
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(null, Bucket.HUD);
        at(2); s.beginAttribution(layer);
        at(3); s.begin(cowAcc, Bucket.ENTITY);      // an entity model the layer draws
        s.vertices(4);
        at(5); s.end();
        at(6); s.end();
        at(7); s.end();
        at(8); s.onRenderPost();
        at(10); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(6 * MS, f.bucket(Bucket.HUD), "the HUD encloses the layer and everything the layer draws");
        assertEquals(0, f.bucket(Bucket.ENTITY), "an enclosing bucket owner wins, even with a layer in between");
        assertEquals(2 * MS, cowAcc.renderNanos, "the entity keeps its own row");
        assertEquals(4, cowAcc.vertices);
        assertEquals(4 * MS, layer.renderNanos);
    }

    @Test
    void nestedAttributionOnlyCallsAreNamedOnceByTheOutermost() {
        Session s = session(10_000);
        Accumulator outer = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        Accumulator inner = s.accumulator(ModKey.event("othermod", "b.OtherRenderEvent"));
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.beginAttribution(outer);
        at(2);  s.begin(s.accumulator(press), Bucket.BLOCK_ENTITY);
        at(3);  s.end();
        at(4);  s.beginAttribution(inner);                    // a handler of an event the outer handler posts
        at(5);  s.begin(s.accumulator(mixer), Bucket.BLOCK_ENTITY);
        at(7);  s.end();
        at(8);  s.end();
        at(9);  s.end();
        at(10); s.onRenderPost();
        at(20); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        assertEquals(3 * MS, f.bucket(Bucket.BLOCK_ENTITY), "no call that owns a bucket encloses either block entity");
        assertEquals(5 * MS, f.named(Window.RENDER), "the outer handler's 8 ms less all 3 ms bucketed inside it, once");
        assertEquals(8 * MS, outer.renderNanos);
        assertEquals(4 * MS, inner.renderNanos, "the inner handler's row is inclusive, like every row");
    }

    @Test
    void vertexRoutingIsResetAtTheFrameBoundary() {
        Session s = session(10_000);
        Accumulator be = s.accumulator(press);
        Accumulator h = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        at(0);  s.onRenderPre(0, 0);
        at(1);  s.begin(be, Bucket.BLOCK_ENTITY);             // a renderer threw past its end()
        at(10); s.onRenderPre(0, 0);
        at(11); s.beginAttribution(h);
        s.vertex();                                           // nothing that owns a bucket is open: dropped
        at(12); s.end();
        at(20); s.onRenderPre(0, 0);

        assertEquals(1, s.unbalancedFrames());
        assertEquals(0, h.vertices, "vertices never land on an attribution-only call");
        assertEquals(0, be.vertices);
    }

    @Test
    void anAttributionOnlyCallNeverAddsShadowTime() {
        Session s = session(10_000);
        Accumulator h = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        at(0); s.onRenderPre(0, 0);
        s.shadowPass = true;                        // still set by the last entity of the shadow pass
        at(1); s.beginAttribution(h);
        at(3); s.end();
        s.shadowPass = false;
        assertEquals(2 * MS, h.renderNanos);
        assertEquals(0, h.shadowNanos, "the flag belongs to the block entity, entity and particle hooks that set it");
    }

    @Test
    void attributionOnlyCallsDeeperThanTheStackAreSafe() {
        Session s = session(10_000);
        Accumulator be = s.accumulator(press);
        Accumulator h = s.accumulator(ModKey.event("somemod", "a.RenderLevelStageEvent"));
        at(0); s.onRenderPre(0, 0);
        at(1); s.begin(be, Bucket.BLOCK_ENTITY);
        for (int i = 0; i < 100; i++) s.beginAttribution(h);
        s.vertex();                                 // under calls the stack does not track: dropped
        at(2);
        for (int i = 0; i < 100; i++) s.end();
        s.vertex();                                 // back inside the block entity
        at(3); s.end();
        at(10); s.onRenderPre(0, 0);

        assertEquals(Session.MAX_DEPTH - 1, h.calls, "calls beyond the stack are dropped, not misattributed");
        assertEquals(1, be.vertices);
        assertEquals(2 * MS, s.framesView().get(0).bucket(Bucket.BLOCK_ENTITY));
    }

    @Test
    void anAttributionOnlyCallBeforeTheFirstFrameIsNotNamed() {
        Session s = session(10_000);
        Accumulator packet = s.accumulator(ModKey.packet("minecraft", "minecraft:level_chunk_with_light"));
        Accumulator input = s.accumulator(ModKey.event("somemod", "a.InputEvent$Key"));
        at(0);  s.onTasksBegin();                   // the session attached between frames
        at(0);  s.beginAttribution(packet);
        at(1);  s.end();
        at(1);  s.onTasksEnd();
        at(2);  s.onRenderPre(0, 0);
        at(4);  s.onRenderPost();
        at(5);  s.onCapBegin();
        at(5);  s.beginAttribution(input);          // in no window, so it opens no named time of its own
        at(6);  s.end();
        at(7);  s.onCapEnd();
        at(12); s.onRenderPre(0, 0);

        FrameRecord f = s.framesView().get(0);
        long named = 0;
        for (Window w : Window.VALUES) named += f.named(w);
        assertEquals(0, named);
        assertEquals(1 * MS, packet.renderNanos, "it still counts for its key, like any call before the first frame");
    }
}

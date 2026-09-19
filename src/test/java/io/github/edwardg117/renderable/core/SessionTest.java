package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    static final long MS = 1_000_000L;

    final long[] now = {0};
    final LongSupplier clock = () -> now[0];

    Session session(long durationMs) {
        return new Session(new SessionConfig(durationMs * MS, 2.0, 16 * MS, 5, 0), clock, 1_700_000_000_000L);
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
        assertThrows(IllegalArgumentException.class, () -> new SessionConfig(0, 2.0, 0, 5, 0));
        SessionConfig c = SessionConfig.ofSeconds(30, 2.0, 16, 5, 25);
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
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 2, 0), clock, 0);
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
}

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
}

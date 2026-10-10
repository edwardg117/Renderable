package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoundPlaysTest {
    static final long MS = 1_000_000L;

    @Test
    void theLineSumsTheSoundRowsAndNothingElse() {
        SoundPlays sp = SoundPlays.of(TestSessions.phase6());
        assertEquals(List.of(TestSessions.STEAM, TestSessions.ANVIL, TestSessions.CLICK),
                sp.rows().stream().map(RankedEntry::key).toList(), "every sound row, the costliest first");
        assertEquals(3_200_000, sp.nanos(), "steam 2.0 + anvil 0.8 + click 0.4: the packet and the tank are not sound rows");
        assertEquals(8, sp.plays());
        assertEquals(1.6, sp.msPerFrame(), 1e-9);
        assertEquals(200.0, sp.playsPerSecond(), 1e-9);
        assertEquals(100.0, sp.playsPerSecond(sp.rows().get(0)), 1e-9, "steam played 4 times in 0.04 s");
        assertEquals("1.60 ms a frame, 200 plays a second", sp.summary());
        assertEquals("Sound plays: 1.60 ms a frame, 200 plays a second", sp.line());

        SoundPlays none = SoundPlays.of(TestSessions.sample());
        assertFalse(none.any(), "a session with no plays prints no line");
        assertEquals(List.of(), none.rows());
    }

    @Test
    void aRenderSideRowRatesItsPlaysToo() {
        SoundPlays sp = SoundPlays.of(TestSessions.phase6());
        assertEquals(TestSessions.ANVIL, sp.rows().get(1).key());
        assertEquals(50.0, sp.playsPerSecond(sp.rows().get(1)), 1e-9, "anvil's plays are render-side calls");
    }

    @Test
    void aRateTooLowForOneDecimalReadsUnderPointOneNotZero() {
        assertEquals("0.00 ms a frame, under 0.1 plays a second", new SoundPlays(List.of(), 0, 1, 1800, 30.0).summary(),
                "one play in half a minute is not 0.0 plays a second");
        assertEquals("under 0.1", SoundPlays.perSecond(0.049));
        assertEquals("0.1", SoundPlays.perSecond(0.05), "from 0.05 the one decimal rounds up to 0.1");
        assertEquals("0.0", SoundPlays.perSecond(0), "no plays, no line: this is never printed");
    }

    @Test
    void aRateJustUnderTenReadsTenNotTenPointZero() {
        assertEquals("0.00 ms a frame, 10 plays a second", new SoundPlays(List.of(), 0, 299, 1800, 30.0).summary(),
                "9.97 a second rounds to 10, which is written whole like every rate from 10 up");
        assertEquals("10", SoundPlays.perSecond(10));
        assertEquals("10", SoundPlays.perSecond(9.95));
        assertEquals("9.9", SoundPlays.perSecond(9.94));
        assertEquals("85", SoundPlays.perSecond(85.4));
        assertEquals("1.5", SoundPlays.perSecond(1.5));
    }

    @Test
    void aPlayThatCostNothingStillCountsAndTheLineStillPrints() {
        // Review focus 2: at 0% the game skips a sound before asking for a channel, so a muted category's plays
        // cost nothing measurable. They are still plays.
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> now[0], 0);
        Accumulator muted = s.accumulator(ModKey.sound("create:steam", "block"));
        s.onRenderPre(0, 0);
        for (int i = 0; i < 3; i++) {
            s.beginAttribution(muted);
            s.end();                    // the clock did not move
        }
        now[0] = 2_000 * MS;
        s.onRenderPre(0, 0);

        SoundPlays sp = SoundPlays.of(s.finish());
        assertTrue(sp.any());
        assertEquals(0, sp.nanos());
        assertEquals(3, sp.plays());
        assertEquals("Sound plays: 0.00 ms a frame, 1.5 plays a second", sp.line(),
                "printed whenever anything played; under ten a second the rate keeps a decimal");
    }
}

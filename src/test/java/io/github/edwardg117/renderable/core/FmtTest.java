package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FmtTest {

    @Test
    void numFormatsWithOneDecimal() {
        // num is the generic one-decimal formatter: same shape as us(), but for values that are not microseconds.
        assertEquals("4900.0", Fmt.num(4900.0));
        assertEquals("0.0", Fmt.num(0.04));
        assertEquals("1234567.9", Fmt.num(1234567.89));
        assertEquals(Fmt.us(12.34), Fmt.num(12.34), "identical today; if this fails, decide which one changed and why");
    }

    @Test
    void peakMsPrintsDashOnlyWhenEveryFrameWasSuspect() {
        // A peak of zero means "never measured a clean frame" only when some frame was GC-suspect.
        assertEquals("2.50", Fmt.peakMs(2_500_000L, 0));
        assertEquals("2.50", Fmt.peakMs(2_500_000L, 7));
        assertEquals("0.00", Fmt.peakMs(0, 0));
        assertEquals("-", Fmt.peakMs(0, 1));
    }
}

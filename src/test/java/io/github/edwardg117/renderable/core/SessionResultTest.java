package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionResultTest {

    @Test
    void bucketTotalNanosSumsEveryFrame() {
        assertEquals(24_000_000L, TestSessions.phase2().bucketTotalNanos(Bucket.FLYWHEEL));
        assertEquals(0L, TestSessions.sample().bucketTotalNanos(Bucket.FLYWHEEL));
    }
}

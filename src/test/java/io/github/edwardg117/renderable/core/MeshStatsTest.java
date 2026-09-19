package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MeshStatsTest {
    static final long MS = 1_000_000L;
    static final BlockKey STONE = new BlockKey("minecraft:stone");
    static final BlockKey SHAFT = new BlockKey("create:shaft");
    static final SectionKey SEC = new SectionKey("minecraft:overworld", 0, 4, 0);

    @Test
    void sameKeyReturnsSameAccumulator() {
        MeshStats m = new MeshStats();
        assertSame(m.get(STONE), m.get(new BlockKey("minecraft:stone")));
        assertTrue(m.snapshot().isEmpty(), "an accumulator with no calls is not reported");
    }

    @Test
    void snapshotSumsCallsAndNanosMostExpensiveFirst() {
        MeshStats m = new MeshStats();
        m.get(STONE).add(3 * MS);
        m.get(STONE).add(5 * MS);
        m.get(SHAFT).add(10 * MS);
        m.get(SEC).add(9 * MS);
        List<MeshEntry> snap = m.snapshot();
        assertEquals(List.of(SHAFT, SEC, STONE), snap.stream().map(MeshEntry::key).toList());
        MeshEntry stone = snap.get(2);
        assertEquals(2, stone.calls());
        assertEquals(8 * MS, stone.nanos());
        assertEquals(4000.0, stone.usPerCall(), 1e-9);
        assertEquals(20.0, stone.msPerSecond(0.4), 1e-9);
        assertEquals(5.0, stone.callsPerSecond(0.4), 1e-9);
        assertEquals(0.0, stone.msPerSecond(0), 1e-9);
    }

    @Test
    void concurrentAddsAreNotLost() throws InterruptedException {
        MeshStats m = new MeshStats();
        int threads = 8, perThread = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                for (int i = 0; i < perThread; i++) m.get(STONE).add(3);   // concurrent computeIfAbsent on one key
                done.countDown();
            });
        }
        try {
            assertTrue(done.await(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        MeshEntry e = m.snapshot().get(0);
        assertEquals((long) threads * perThread, e.calls());
        assertEquals(3L * threads * perThread, e.nanos());
    }
}

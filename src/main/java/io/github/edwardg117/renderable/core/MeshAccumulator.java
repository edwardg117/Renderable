package io.github.edwardg117.renderable.core;

import java.util.concurrent.atomic.LongAdder;

/**
 * Counters for one mesh key, written from any thread (spec §3.4). Sections count rebuilds as calls.
 * {@code owner} lets a cached accumulator be recognised as belonging to an earlier session.
 */
public final class MeshAccumulator {
    public final MeshStats owner;
    public final ProfileKey key;
    private final LongAdder calls = new LongAdder();
    private final LongAdder nanos = new LongAdder();

    MeshAccumulator(MeshStats owner, ProfileKey key) {
        this.owner = owner;
        this.key = key;
    }

    /**
     * Records one call that took {@code elapsedNanos}. Allocation-free in steady state: LongAdder allocates a
     * striping cell the first time a new thread contends, then never again for that thread.
     */
    public void add(long elapsedNanos) {
        calls.increment();
        nanos.add(elapsedNanos);
    }

    long calls() {
        return calls.sum();
    }

    long nanos() {
        return nanos.sum();
    }
}

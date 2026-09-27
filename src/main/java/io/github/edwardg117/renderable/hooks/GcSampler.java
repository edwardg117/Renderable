package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.GcNames;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Sums collection count and pause time over the collectors' pause beans (spec addendum §P5.B4): all of them
 * under G1, Parallel and Serial, and only the {@code Pauses} beans under ZGC and Shenandoah, whose
 * {@code Cycles} beans time concurrent work. Cheap enough to call once per frame.
 */
public final class GcSampler {
    private final List<GarbageCollectorMXBean> beans;
    private long count;
    private long millis;

    public GcSampler() {
        List<GarbageCollectorMXBean> all = ManagementFactory.getGarbageCollectorMXBeans();
        List<String> counted = GcNames.countedBeans(all.stream().map(GarbageCollectorMXBean::getName).toList());
        beans = all.stream().filter(b -> counted.contains(b.getName())).toList();
    }

    public void sample() {
        long c = 0;
        long m = 0;
        for (GarbageCollectorMXBean b : beans) {
            long cc = b.getCollectionCount();
            long mm = b.getCollectionTime();
            if (cc > 0) c += cc;
            if (mm > 0) m += mm;
        }
        count = c;
        millis = m;
    }

    public long count() {
        return count;
    }

    public long millis() {
        return millis;
    }
}

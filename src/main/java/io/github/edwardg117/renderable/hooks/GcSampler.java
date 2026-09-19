package io.github.edwardg117.renderable.hooks;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/** Sums collection count and pause time over all collectors. Cheap enough to call once per frame. */
public final class GcSampler {
    private final List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
    private long count;
    private long millis;

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

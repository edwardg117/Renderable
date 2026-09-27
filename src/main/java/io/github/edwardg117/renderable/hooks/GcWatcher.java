package io.github.edwardg117.renderable.hooks;

import com.sun.management.GarbageCollectionNotificationInfo;
import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.GcNames;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Records the smallest heap in use after any collection during a session (spec addendum §P5.B3), from the
 * JVM's collection notifications. They arrive on the JMX notification thread, so the render thread never
 * allocates for this. The minimum is an upper bound on live data, because a young collection leaves old
 * garbage in place. Only the beans {@link GcNames#liveHeapBeans} names are watched: a ZGC or Shenandoah pause
 * notification reports no heap in use. Started and stopped by the controller with each session.
 */
public final class GcWatcher {
    private static final List<NotificationEmitter> EMITTERS = new ArrayList<>();
    private static final AtomicLong MIN_AFTER_BYTES = new AtomicLong(Long.MAX_VALUE);
    private static NotificationListener listener;

    private GcWatcher() {
    }

    /**
     * Forgets the last session's minimum and starts listening. Render thread, inside the keybind's tick listener,
     * so a JVM without these beans turns the figure off rather than crashing the client.
     */
    public static synchronized void start() {
        stop();
        MIN_AFTER_BYTES.set(Long.MAX_VALUE);
        try {
            Set<String> heapPools = ManagementFactory.getMemoryPoolMXBeans().stream()
                    .filter(p -> p.getType() == MemoryType.HEAP)
                    .map(MemoryPoolMXBean::getName)
                    .collect(Collectors.toUnmodifiableSet());
            List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
            Set<String> watched = Set.copyOf(GcNames.liveHeapBeans(beans.stream().map(GarbageCollectorMXBean::getName).toList()));
            listener = (Notification n, Object handback) -> {
                if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType())) return;
                GarbageCollectionNotificationInfo info =
                        GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
                long after = 0;
                for (Map.Entry<String, MemoryUsage> e : info.getGcInfo().getMemoryUsageAfterGc().entrySet()) {
                    if (heapPools.contains(e.getKey())) after += e.getValue().getUsed();
                }
                if (after > 0) MIN_AFTER_BYTES.accumulateAndGet(after, Math::min);
            };
            for (GarbageCollectorMXBean bean : beans) {
                if (watched.contains(bean.getName()) && bean instanceof NotificationEmitter emitter) {
                    emitter.addNotificationListener(listener, null, null);
                    EMITTERS.add(emitter);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            RenderableMod.LOGGER.warn("Renderable cannot watch garbage collections; the report will have no live heap figure", e);
            stop();
        }
    }

    /** Stops listening; the minimum so far is kept for {@link #liveHeapAfterGcMiB()}. */
    public static synchronized void stop() {
        for (NotificationEmitter emitter : EMITTERS) {
            try {
                emitter.removeNotificationListener(listener);
            } catch (ListenerNotFoundException ignored) {
                // already gone; nothing to undo
            }
        }
        EMITTERS.clear();
        listener = null;
    }

    /** The smallest heap in use after a collection in the last session, in MiB, or -1 when none ran. */
    public static long liveHeapAfterGcMiB() {
        long b = MIN_AFTER_BYTES.get();
        return b == Long.MAX_VALUE ? -1 : b >> 20;
    }
}

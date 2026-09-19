package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Thread-safe store of mesh accumulators (spec §3.4). Hooks cache the accumulator per Minecraft object. */
public final class MeshStats {
    private final ConcurrentHashMap<ProfileKey, MeshAccumulator> map = new ConcurrentHashMap<>(256);
    private final Function<ProfileKey, MeshAccumulator> factory = k -> new MeshAccumulator(this, k);

    /** Returns the accumulator for a key, creating it on first sight. */
    public MeshAccumulator get(ProfileKey key) {
        return map.computeIfAbsent(key, factory);
    }

    /** Immutable rows for every key with at least one call, most nanoseconds first, ties by label. */
    public List<MeshEntry> snapshot() {
        List<MeshEntry> out = new ArrayList<>(map.size());
        for (MeshAccumulator a : map.values()) {
            long calls = a.calls();
            if (calls > 0) out.add(new MeshEntry(a.key, calls, a.nanos()));
        }
        out.sort(Comparator.comparingLong(MeshEntry::nanos).reversed().thenComparing(e -> e.key().label()));
        return List.copyOf(out);
    }
}

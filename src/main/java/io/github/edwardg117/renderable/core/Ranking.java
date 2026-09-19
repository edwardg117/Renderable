package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds ranked rows from a finished session. */
public final class Ranking {

    public enum Sort {
        TOTAL, RENDER, TICK, PEAK, VERTICES, CALLS;

        public Sort next() {
            Sort[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    /** Orderings for the mesh tables. */
    public enum MeshSort { TIME, CALLS }

    private Ranking() {
    }

    /** One row per key of the category, most expensive first. */
    public static List<RankedEntry> byKey(SessionResult r, ProfileKey.Category category) {
        List<RankedEntry> out = new ArrayList<>();
        for (Accumulator a : r.accumulators().values()) {
            if (a.key.category() == category) out.add(RankedEntry.of(a));
        }
        return sort(out, Sort.TOTAL);
    }

    /** One row per type id of the category, most expensive first. */
    public static List<RankedEntry> byType(SessionResult r, ProfileKey.Category category) {
        Map<String, List<Accumulator>> groups = new LinkedHashMap<>();
        for (Accumulator a : r.accumulators().values()) {
            if (a.key.category() == category) {
                groups.computeIfAbsent(a.key.typeId(), k -> new ArrayList<>()).add(a);
            }
        }
        List<RankedEntry> out = new ArrayList<>(groups.size());
        for (Map.Entry<String, List<Accumulator>> e : groups.entrySet()) {
            out.add(RankedEntry.group(e.getKey(), category, e.getValue()));
        }
        return sort(out, Sort.TOTAL);
    }

    /** Individual rows of one type, most expensive first. */
    public static List<RankedEntry> membersOf(SessionResult r, ProfileKey.Category category, String typeId) {
        List<RankedEntry> out = new ArrayList<>();
        for (Accumulator a : r.accumulators().values()) {
            if (a.key.category() == category && a.key.typeId().equals(typeId)) out.add(RankedEntry.of(a));
        }
        return sort(out, Sort.TOTAL);
    }

    /** Mesh rows of one category, ordered by nanoseconds or by calls, descending, ties by label. */
    public static List<MeshEntry> mesh(SessionResult r, ProfileKey.Category category, MeshSort sort) {
        List<MeshEntry> out = new ArrayList<>();
        for (MeshEntry e : r.mesh()) {
            if (e.key().category() == category) out.add(e);
        }
        Comparator<MeshEntry> by = sort == MeshSort.CALLS
                ? Comparator.comparingLong(MeshEntry::calls)
                : Comparator.comparingLong(MeshEntry::nanos);
        out.sort(by.reversed().thenComparing(e -> e.key().label()));
        return out;
    }

    /** Returns a new list sorted by the chosen column, descending, ties broken by label. */
    public static List<RankedEntry> sort(List<RankedEntry> entries, Sort sort) {
        Comparator<RankedEntry> by = switch (sort) {
            case TOTAL -> Comparator.comparingLong(RankedEntry::totalNanos);
            case RENDER -> Comparator.comparingLong(RankedEntry::renderNanos);
            case TICK -> Comparator.comparingLong(RankedEntry::tickNanos);
            case PEAK -> Comparator.comparingLong(RankedEntry::maxFrameNanos);
            case VERTICES -> Comparator.comparingLong(RankedEntry::vertices);
            case CALLS -> Comparator.comparingLong(RankedEntry::calls);
        };
        List<RankedEntry> copy = new ArrayList<>(entries);
        copy.sort(by.reversed().thenComparing(RankedEntry::label));
        return copy;
    }
}

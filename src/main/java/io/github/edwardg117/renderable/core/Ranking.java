package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds ranked rows from a finished session. */
public final class Ranking {

    /** What a column of a ranked table sorts by (spec §5.3). */
    public enum Sort {
        TOTAL, RENDER, TICK, PEAK, VERTICES, CALLS, ALL_CALLS, SHADOW, GC_FRAMES, LABEL
    }

    /**
     * What a column of the Blocks or Sections table sorts by. PER_CALL is time per call, which for a
     * section is time per rebuild, so the Sections table's average column sorts by it.
     */
    public enum MeshSort { TIME, CALLS, PER_CALL, LABEL }

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

    /**
     * {@link #membersOf} for every type of the category, keyed by type id, in one pass over the session. For a
     * caller that needs the members of many types: calling {@code membersOf} once per type reads every
     * accumulator of the session once per type.
     */
    public static Map<String, List<RankedEntry>> membersByType(SessionResult r, ProfileKey.Category category) {
        Map<String, List<RankedEntry>> out = new LinkedHashMap<>();
        for (Accumulator a : r.accumulators().values()) {
            if (a.key.category() == category) {
                out.computeIfAbsent(a.key.typeId(), k -> new ArrayList<>()).add(RankedEntry.of(a));
            }
        }
        out.replaceAll((typeId, rows) -> sort(rows, Sort.TOTAL));
        return out;
    }

    /** Mesh rows of one category, descending by the chosen column, ties broken by label. */
    public static List<MeshEntry> mesh(SessionResult r, ProfileKey.Category category, MeshSort sort) {
        return mesh(r, category, sort, false);
    }

    /**
     * Mesh rows of one category sorted by the chosen column, smallest first when {@code ascending}. Ties go
     * A to Z by label in both directions, as in {@link #sort(List, Sort, boolean)}.
     * A {@code LABEL} sort compares in {@link NaturalOrder}; ties still go by plain string order
     * (spec addendum §P5.D5).
     */
    public static List<MeshEntry> mesh(SessionResult r, ProfileKey.Category category, MeshSort sort,
                                       boolean ascending) {
        List<MeshEntry> out = new ArrayList<>();
        for (MeshEntry e : r.mesh()) {
            if (e.key().category() == category) out.add(e);
        }
        Comparator<MeshEntry> by = switch (sort) {
            case TIME -> Comparator.comparingLong(MeshEntry::nanos);
            case CALLS -> Comparator.comparingLong(MeshEntry::calls);
            case PER_CALL -> Comparator.comparingDouble(MeshEntry::usPerCall);
            case LABEL -> Comparator.comparing((MeshEntry e) -> e.key().label(), NaturalOrder.INSTANCE);
        };
        out.sort((ascending ? by : by.reversed()).thenComparing(e -> e.key().label()));
        return out;
    }

    /** Returns a new list sorted by the chosen column, descending, ties broken by label. */
    public static List<RankedEntry> sort(List<RankedEntry> entries, Sort sort) {
        return sort(entries, sort, false);
    }

    /**
     * Returns a new list sorted by the chosen column, smallest first when {@code ascending}. Ties go A to Z
     * by label in both directions, so flipping a column never reorders the rows it ties.
     * A {@code LABEL} sort compares in {@link NaturalOrder}; ties still go by plain string order
     * (spec addendum §P5.D5).
     */
    public static List<RankedEntry> sort(List<RankedEntry> entries, Sort sort, boolean ascending) {
        Comparator<RankedEntry> by = switch (sort) {
            case TOTAL -> Comparator.comparingLong(RankedEntry::totalNanos);
            case RENDER -> Comparator.comparingLong(RankedEntry::renderNanos);
            case TICK -> Comparator.comparingLong(RankedEntry::tickNanos);
            case PEAK -> Comparator.comparingLong(RankedEntry::maxFrameNanos);
            case VERTICES -> Comparator.comparingLong(RankedEntry::vertices);
            case CALLS -> Comparator.comparingLong(RankedEntry::calls);
            case ALL_CALLS -> Comparator.comparingLong(e -> e.calls() + e.tickCalls());
            // The shadow column shows shadow time as a share of the row's own render time, not nanoseconds.
            case SHADOW -> Comparator.comparingDouble(RankedEntry::shadowPercent);
            case GC_FRAMES -> Comparator.comparingLong(RankedEntry::gcFrames);
            case LABEL -> Comparator.comparing(RankedEntry::label, NaturalOrder.INSTANCE);
        };
        List<RankedEntry> copy = new ArrayList<>(entries);
        copy.sort((ascending ? by : by.reversed()).thenComparing(RankedEntry::label));
        return copy;
    }
}

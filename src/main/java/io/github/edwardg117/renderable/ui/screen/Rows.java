package io.github.edwardg117.renderable.ui.screen;

import io.github.edwardg117.renderable.core.BlockEntityKey;
import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.EmptyStates;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.EntityKey;
import io.github.edwardg117.renderable.core.FlywheelStats;
import io.github.edwardg117.renderable.core.Fmt;
import io.github.edwardg117.renderable.core.FrameRecord;
import io.github.edwardg117.renderable.core.FrameStats;
import io.github.edwardg117.renderable.core.MeshEntry;
import io.github.edwardg117.renderable.core.MeshTable;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.NaturalOrder;
import io.github.edwardg117.renderable.core.ProfileKey;
import io.github.edwardg117.renderable.core.RankedEntry;
import io.github.edwardg117.renderable.core.RankedTable;
import io.github.edwardg117.renderable.core.Ranking;
import io.github.edwardg117.renderable.core.SectionKey;
import io.github.edwardg117.renderable.core.SessionResult;
import io.github.edwardg117.renderable.core.Splits;
import io.github.edwardg117.renderable.core.TopEntry;
import io.github.edwardg117.renderable.core.Window;
import io.github.edwardg117.renderable.core.advice.Advisor;
import io.github.edwardg117.renderable.core.advice.Recommendation;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Builds the rows for each tab from a finished session. */
public final class Rows {
    private static final long[] HISTOGRAM_EDGES_MS = {8, 16, 33, 50, 100, 250};

    /** Spec addendum §P4.C2: the Sections tab is capped; the JSON has every section, the report only its top 20. */
    static final int MAX_SECTION_ROWS = 200;

    private Rows() {
    }

    /**
     * Rows for one tab. {@code sort} is always the active column's sort ({@link ColumnSort#NONE} on Advice,
     * Frames and Hitches, which ignore it); {@code ascending} its direction. {@code wrap} splits a paragraph
     * into lines that fit the list, for the Advice tab.
     */
    public static List<Row> build(Tab tab, SessionResult r, Environment env, ColumnSort sort, boolean ascending,
                                  Set<String> expanded, Function<String, List<String>> wrap) {
        return switch (tab) {
            case ADVICE -> advice(Advisor.advise(r, env), wrap);
            case FRAMES -> frames(r);
            case HITCHES -> hitches(r);
            case MODS -> mods(r, env, sort, ascending, expanded);
            case BLOCK_ENTITIES -> ranked(r, env, ProfileKey.Category.BLOCK_ENTITY, sort, ascending, expanded);
            case ENTITIES -> ranked(r, env, ProfileKey.Category.ENTITY, sort, ascending, expanded);
            case PARTICLES -> particles(r, env, sort, ascending);
            case FLYWHEEL -> flywheel(r, env, sort, ascending);
            case BLOCKS -> blocks(r, env, sort, ascending);
            case SECTIONS -> sections(r, env, sort, ascending);
        };
    }

    /** The ranked sort a clicked column means, or the report's order, by total time, when there is none. */
    private static Ranking.Sort rankedSort(ColumnSort sort) {
        return sort.ranked() == null ? Ranking.Sort.TOTAL : sort.ranked();
    }

    /** The mesh sort a clicked column means, or {@code fallback}, the report's order for that table. */
    private static Ranking.MeshSort meshSort(ColumnSort sort, Ranking.MeshSort fallback) {
        return sort.mesh() == null ? fallback : sort.mesh();
    }

    /** Spec addendum §P5.B5 Advice tab: every recommendation, the player's first, wrapped to the list's width. */
    static List<Row> advice(List<Recommendation> advice, Function<String, List<String>> wrap) {
        List<Row> rows = new ArrayList<>();
        if (advice.isEmpty()) {
            rows.add(Row.plain(EmptyStates.advice()));
            return rows;
        }
        for (Recommendation.Audience audience : Recommendation.Audience.values()) {
            List<Recommendation> theirs = advice.stream().filter(a -> a.audience() == audience).toList();
            if (theirs.isEmpty()) continue;
            rows.add(Row.heading(audience == Recommendation.Audience.PLAYER ? "For you" : "For the pack author"));
            for (Recommendation a : theirs) {
                String title = a.atStake().isEmpty() ? a.title() : a.title() + "  (" + a.atStake() + ")";
                for (String line : wrap.apply(title)) rows.add(new Row(new String[]{line}, null, null, true, false, 1, null, -1));
                for (String line : wrap.apply(a.finding())) rows.add(new Row(new String[]{line}, null, null, false, false, 2, null, -1));
                for (String line : wrap.apply(a.action())) rows.add(new Row(new String[]{line}, null, null, false, false, 2, null, -1));
                rows.add(Row.plain(""));
            }
        }
        return rows;
    }

    static List<Row> frames(SessionResult r) {
        FrameStats st = r.stats();
        List<Row> rows = new ArrayList<>();
        rows.add(Row.plain("Sampled", String.format(Locale.ROOT, "%.1f s, %d frames", st.durationSeconds(), st.frames())));
        rows.add(Row.plain("Average frame", String.format(Locale.ROOT, "%s ms (%.0f fps)", Fmt.ms(st.avgNanos()), st.avgFps())));
        rows.add(Row.plain("p50 / p95 / p99 / max", Fmt.ms(st.p50Nanos()) + " / " + Fmt.ms(st.p95Nanos()) + " / "
                + Fmt.ms(st.p99Nanos()) + " / " + Fmt.ms(st.maxNanos()) + " ms"));
        rows.add(Row.plain("Hitch threshold", Fmt.ms(r.hitchThresholdNanos()) + " ms"));
        rows.add(Row.plain("Hitches", r.hitches().size() + " (" + Fmt.pct(r.hitchSharePercent()) + " of time)"));
        rows.add(Row.plain("GC", st.gcCount() + " collections, " + st.gcMillis() + " ms paused"));
        if (st.gcCount() > 0) {
            for (String line : RankedTable.GC_COLUMN_NOTE_LINES) rows.add(Row.plain(line));
        }
        rows.add(Row.plain("Renderable overhead", String.format(Locale.ROOT, "%.2f ms over %s timed calls and %s vertex calls",
                r.overheadEstimateMs(), Fmt.count(r.timedCalls()), Fmt.count(r.vertexCalls()))));
        if (r.selfNanos() > 0) {
            rows.add(Row.plain("Renderable self time", Fmt.ms2(r.selfNanos()) + " ms (HUD line, Flywheel sample, event-list check)"));
        }
        if (r.unbalancedFrames() > 0) {
            rows.add(Row.plain("Attribution stack reset", r.unbalancedFrames() + " frames (a hook threw past its end call)"));
        }
        rows.add(Row.plain("", ""));

        rows.add(Row.heading("Average split per frame"));
        rows.add(Row.plain("tick", Fmt.ms2(st.avgTickNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (!b.isRender() && st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("  other tick", Fmt.ms2(st.avgOtherTickNanos()) + " ms"));
        namedRow(rows, "    in the Mods tab", st.avgNamed(Window.TICK));
        rows.add(Row.plain("render", Fmt.ms2(st.avgRenderNanos()) + " ms"));
        for (Bucket b : Bucket.VALUES) {
            if (b.isRender() && st.avgBucket(b) > 0) rows.add(Row.plain("  " + b.label(), Fmt.ms2(st.avgBucket(b)) + " ms"));
        }
        rows.add(Row.plain("  other render", Fmt.ms2(st.avgOtherRenderNanos()) + " ms"));
        namedRow(rows, "    in the Mods tab", st.avgNamed(Window.RENDER));
        rows.add(Row.plain("tasks", Fmt.ms2(st.avgTasksNanos()) + " ms"));
        namedRow(rows, "  in the Mods tab", st.avgNamed(Window.TASKS));
        rows.add(Row.plain("frame cap", Fmt.ms2(st.avgCapNanos()) + " ms"));
        rows.add(Row.plain("swap / wait", Fmt.ms2(st.avgSwapNanos()) + " ms"));
        rows.add(Row.plain("", ""));

        rows.add(Row.heading("Frame time histogram"));
        int[] counts = new int[HISTOGRAM_EDGES_MS.length + 1];
        for (FrameRecord f : r.frames()) {
            double ms = f.totalNanos() / 1e6;
            int bin = 0;
            while (bin < HISTOGRAM_EDGES_MS.length && ms >= HISTOGRAM_EDGES_MS[bin]) bin++;
            counts[bin]++;
        }
        int max = Arrays.stream(counts).max().orElse(0);
        for (int i = 0; i <= HISTOGRAM_EDGES_MS.length; i++) {
            String label;
            if (i == 0) {
                label = "< " + HISTOGRAM_EDGES_MS[0] + " ms";
            } else if (i == HISTOGRAM_EDGES_MS.length) {
                label = ">= " + HISTOGRAM_EDGES_MS[i - 1] + " ms";
            } else {
                label = HISTOGRAM_EDGES_MS[i - 1] + " - " + HISTOGRAM_EDGES_MS[i] + " ms";
            }
            int bar = max == 0 ? 0 : (int) Math.round(30.0 * counts[i] / max);
            rows.add(Row.plain(label, String.format(Locale.ROOT, "%5d  %s", counts[i], "#".repeat(bar))));
        }
        return rows;
    }

    /** A named-time row (spec addendum §P5.A6), shown only when there is some. */
    private static void namedRow(List<Row> rows, String label, long nanos) {
        if (nanos > 0) rows.add(Row.plain(label, Fmt.ms2(nanos) + " ms"));
    }

    static List<Row> hitches(SessionResult r) {
        List<Row> rows = new ArrayList<>();
        if (r.hitches().isEmpty()) {
            rows.add(Row.plain("No hitches", "", "No frame exceeded " + Fmt.ms(r.hitchThresholdNanos()) + " ms"));
            return rows;
        }
        int n = 0;
        for (FrameRecord h : r.hitches()) {
            n++;
            String frame = "#" + n + "  frame " + h.index() + " @ " + Fmt.secs(h.startNanos()) + " s";
            rows.add(new Row(new String[]{frame, Fmt.ms(h.totalNanos()), Splits.frame(h) + " | gc " + h.gcMillis() + " ms"},
                    null, null, true, false, 0, null, -1));
            for (TopEntry t : h.top()) {
                rows.add(new Row(new String[]{t.key().label(), Fmt.ms2(t.nanos()), t.gc() ? "[gc]" : ""},
                        t.key(), t.key().typeId(), false, false, 1, posOf(t.key()), entityIdOf(t.key())));
            }
        }
        return rows;
    }

    static List<Row> ranked(SessionResult r, Environment env, ProfileKey.Category category, ColumnSort sort,
                            boolean ascending, Set<String> expanded) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        Ranking.Sort by = rankedSort(sort);
        List<Row> rows = new ArrayList<>();
        if (category == ProfileKey.Category.BLOCK_ENTITY
                && (r.bucketTotalNanos(Bucket.FLYWHEEL) > 0 || r.flywheel().total() > 0)) {
            rows.add(Row.heading("Flywheel-instanced machines are not timed here; see the Flywheel tab"));
        }
        List<RankedEntry> types = Ranking.sort(Ranking.byType(r, category), by, ascending);
        if (types.isEmpty()) {
            // Under the Flywheel heading too, when there is one: it is not a row of this table (spec addendum §P5.D2).
            rows.add(Row.plain(category == ProfileKey.Category.BLOCK_ENTITY
                    ? EmptyStates.blockEntities(env) : EmptyStates.entities(env)));
            return rows;
        }
        for (RankedEntry t : types) {
            rows.add(new Row(RankedTable.ranked(t.typeId() + "  (" + t.members() + ")", t, frames, total),
                    null, t.typeId(), true, true, 0, null, -1));
            if (expanded.contains(t.typeId())) {
                for (RankedEntry m : Ranking.sort(Ranking.membersOf(r, category, t.typeId()), by, ascending)) {
                    rows.add(new Row(RankedTable.ranked(memberLabel(m.key()), m, frames, total),
                            m.key(), t.typeId(), false, false, 1, posOf(m.key()), entityIdOf(m.key())));
                }
            }
        }
        return rows;
    }

    /** Spec addendum §P5.A7 Mods tab: one row per mod, expandable to its event types, layers and packets. */
    static List<Row> mods(SessionResult r, Environment env, ColumnSort sort, boolean ascending, Set<String> expanded) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        Ranking.Sort by = rankedSort(sort);
        List<RankedEntry> mods = Ranking.sort(Ranking.byType(r, ProfileKey.Category.MOD), by, ascending);
        List<Row> rows = new ArrayList<>();
        if (mods.isEmpty()) {
            rows.add(Row.plain(EmptyStates.mods(env)));
            return rows;
        }
        String missing = EmptyStates.modsHandlersMissing(env);
        if (!missing.isEmpty()) rows.add(Row.plain(missing));
        for (String line : RankedTable.MODS_NOTE_LINES) rows.add(Row.plain(line));
        for (RankedEntry m : mods) {
            rows.add(new Row(RankedTable.mods(m.typeId() + "  (" + m.members() + ")", m, frames, total),
                    null, m.typeId(), true, true, 0, null, -1));
            if (expanded.contains(m.typeId())) {
                for (RankedEntry e : Ranking.sort(Ranking.membersOf(r, ProfileKey.Category.MOD, m.typeId()), by, ascending)) {
                    rows.add(new Row(RankedTable.mods(((ModKey) e.key()).memberLabel(), e, frames, total),
                            e.key(), m.typeId(), false, false, 1, null, -1));
                }
            }
        }
        return rows;
    }

    /** Spec §5.3 Particles tab: one row per particle class. */
    static List<Row> particles(SessionResult r, Environment env, ColumnSort sort, boolean ascending) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        List<Row> rows = new ArrayList<>();
        for (RankedEntry e : Ranking.sort(Ranking.particles(r), rankedSort(sort), ascending)) {
            rows.add(new Row(RankedTable.particle(e, frames, total),
                    e.key(), e.typeId(), false, false, 0, null, -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain(EmptyStates.particles(env)));
        return rows;
    }

    /**
     * Spec §5.3 Flywheel tab: the bucket, then the averaged visual counts by class. The rows are class names
     * and averages rather than ranked entries, so the two sortable columns are read directly: LABEL sorts by
     * class name, anything else by average count (spec addendum §P4.C7).
     */
    static List<Row> flywheel(SessionResult r, Environment env, ColumnSort sort, boolean ascending) {
        int frames = r.frameCount();
        long total = r.stats().totalNanos();
        long bucket = r.bucketTotalNanos(Bucket.FLYWHEEL);
        FlywheelStats fw = r.flywheel();
        String empty = EmptyStates.flywheel(env, fw.samples());
        List<Row> rows = new ArrayList<>();
        if (bucket > 0 || empty.isEmpty()) {
            rows.add(new Row(new String[]{"Flywheel bucket (us/f)",
                    Fmt.us(frames == 0 ? 0 : bucket / 1000.0 / frames),
                    Fmt.pct(total == 0 ? 0 : 100.0 * bucket / total)},
                    null, null, true, false, 0, null, -1));
        }
        if (!empty.isEmpty()) {
            rows.add(Row.plain(empty));
            return rows;
        }
        rows.add(new Row(new String[]{"Visuals, average over " + fw.samples() + " samples",
                Fmt.num(fw.total()), ""},
                null, null, true, false, 0, null, -1));
        if (fw.avgByClass().isEmpty()) {
            // No classes to list; say why when nothing was alive (the totals row above covers the rest).
            if (fw.total() == 0) rows.add(Row.plain(EmptyStates.flywheelNoVisuals(fw.samples())));
            return rows;
        }

        Map<String, String> names = fw.displayNames();
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(fw.byAverageDesc());
        boolean byName = sort.ranked() == Ranking.Sort.LABEL;
        Comparator<Map.Entry<String, Double>> by = byName
                ? Comparator.comparing((Map.Entry<String, Double> e) -> names.get(e.getKey()), NaturalOrder.INSTANCE)
                : Comparator.comparingDouble(Map.Entry::getValue);
        ordered.sort(ascending ? by : by.reversed());

        for (Map.Entry<String, Double> e : ordered) {
            rows.add(new Row(new String[]{names.get(e.getKey()), Fmt.num(e.getValue()), ""},
                    null, null, false, false, 1, null, -1));
        }
        return rows;
    }

    /** Spec §5.3 Blocks tab: one row per block, by mesh time per second unless a header was clicked. */
    static List<Row> blocks(SessionResult r, Environment env, ColumnSort sort, boolean ascending) {
        double seconds = r.stats().durationSeconds();
        List<Row> rows = new ArrayList<>();
        for (MeshEntry e : Ranking.mesh(r, ProfileKey.Category.BLOCK, meshSort(sort, Ranking.MeshSort.TIME), ascending)) {
            rows.add(new Row(MeshTable.block(e, seconds), e.key(), e.key().typeId(), false, false, 0, null, -1));
        }
        if (rows.isEmpty()) rows.add(Row.plain(EmptyStates.blocks(env)));
        return rows;
    }

    /**
     * Spec §5.3 Sections tab: one row per rebuilt section; the row's position is the section centre. A
     * 30 s flight can rebuild a thousand sections, so the list is capped and says how many it left out
     * (spec addendum §P4.C2); the JSON carries every one, the text report only its top 20.
     */
    static List<Row> sections(SessionResult r, Environment env, ColumnSort sort, boolean ascending) {
        double seconds = r.stats().durationSeconds();
        List<MeshEntry> all = Ranking.mesh(r, ProfileKey.Category.SECTION, meshSort(sort, Ranking.MeshSort.CALLS), ascending);
        List<Row> rows = new ArrayList<>();
        for (MeshEntry e : all.subList(0, Math.min(MAX_SECTION_ROWS, all.size()))) {
            SectionKey k = (SectionKey) e.key();
            rows.add(new Row(MeshTable.section(e, seconds), k, k.typeId(), false, false, 0,
                    new BlockPos(k.centerX(), k.centerY(), k.centerZ()), -1));
        }
        if (all.size() > MAX_SECTION_ROWS) {
            int hidden = all.size() - MAX_SECTION_ROWS;
            rows.add(Row.plain("... and " + Fmt.count(hidden) + (hidden == 1 ? " more section" : " more sections")
                    + " (the JSON has all " + Fmt.count(all.size()) + ")"));
        }
        if (rows.isEmpty()) rows.add(Row.plain(EmptyStates.sections(env)));
        return rows;
    }

    static String memberLabel(ProfileKey k) {
        if (k instanceof BlockEntityKey b) return "@ " + b.x() + " " + b.y() + " " + b.z();
        if (k instanceof EntityKey e) return "#" + e.entityId() + (e.name().isEmpty() ? "" : "  " + e.name());
        return k.label();
    }

    static BlockPos posOf(ProfileKey k) {
        return k instanceof BlockEntityKey b ? new BlockPos(b.x(), b.y(), b.z()) : null;
    }

    static int entityIdOf(ProfileKey k) {
        return k instanceof EntityKey e ? e.entityId() : -1;
    }
}

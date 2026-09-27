package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * The columns of the ranked tables (block entities, entities) and of the particle table: their names and the
 * text of each cell, written once for the text report and the results screen. The report takes its headers
 * from here; the screen writes its headers out in {@code Tab}, beside their widths and sorts, and
 * {@code TabTest} asserts they equal {@link #RANKED} and {@link #PARTICLE}. Widths, truncation and
 * sorting stay with each surface. The first cell is the row's name and the rest are numbers, except
 * that a peak with no clean frame reads "-" (see {@link Fmt#peakMs}).
 */
public final class RankedTable {

    /** Headers of the block entity and entity tables. */
    public static final List<String> RANKED = List.of(
            "name", "render us/f", "tick us/f", "share", "peak ms", "gc f", "shadow", "verts/f", "calls");

    /** Headers of the particle table: no peak or shadow of interest, and the average live count instead. */
    public static final List<String> PARTICLE = List.of(
            "class", "render us/f", "tick us/f", "share", "avg live", "verts/f", "gc f");

    /**
     * {@link #RANKED} without the shadow column: the text report's header for a table in which no row has shadow
     * time, as with no shader pack on (spec addendum §P5.D4). The screen and the JSON always keep the column.
     */
    public static final List<String> RANKED_WITHOUT_SHADOW = RANKED.stream().filter(h -> !h.equals("shadow")).toList();

    /** Headers of the Mods table (spec addendum §P5.A7): a mod's time in total, not split by where it ran. */
    public static final List<String> MODS = List.of("mod", "share", "us/f", "peak ms", "gc f", "calls/f");

    /**
     * Printed above the Mods table on both surfaces: the screen prints each line as a row, the report the two
     * joined. Two lines so each fits the results screen at the default GUI scale.
     */
    public static final List<String> MODS_NOTE_LINES = List.of(
            "(a mod's time includes what its handlers call, so shares overlap;",
            "a library that runs other mods' handlers carries their time)");

    /**
     * Explains the gc f column and a "-" peak, as two lines so each fits the results screen at the default GUI
     * scale. The screen prints them under its GC row; the report prints {@link #GC_COLUMN_NOTE}.
     */
    public static final List<String> GC_COLUMN_NOTE_LINES = List.of(
            "(gc f counts frames a pause could account for;",
            "those are left out of peak ms, and \"-\" means none was clean)");

    /** The note as one line, which the report prints under its GC line. */
    public static final String GC_COLUMN_NOTE = String.join(" ", GC_COLUMN_NOTE_LINES);

    private RankedTable() {
    }

    /** One row of a ranked table. The caller supplies the name, because the two surfaces label rows differently. */
    public static String[] ranked(String name, RankedEntry e, int frames, long total) {
        return new String[]{
                name,
                Fmt.us(e.renderUsPerFrame(frames)),
                Fmt.us(e.tickUsPerFrame(frames)),
                Fmt.pct(e.sharePercent(total)),
                Fmt.peakMs(e.maxFrameNanos(), e.gcFrames()),
                Fmt.count(e.gcFrames()),
                Fmt.pct(e.shadowPercent()),
                Fmt.num(e.verticesPerFrame(frames)),
                Fmt.count(e.calls())};
    }

    /** A {@link #ranked} row without its shadow cell, to go under {@link #RANKED_WITHOUT_SHADOW}. */
    public static String[] withoutShadow(String[] cells) {
        int shadow = RANKED.indexOf("shadow");
        String[] out = new String[cells.length - 1];
        System.arraycopy(cells, 0, out, 0, shadow);
        System.arraycopy(cells, shadow + 1, out, shadow, cells.length - shadow - 1);
        return out;
    }

    /** One row of the particle table. */
    public static String[] particle(RankedEntry e, int frames, long total) {
        return new String[]{
                e.label(),
                Fmt.us(e.renderUsPerFrame(frames)),
                Fmt.us(e.tickUsPerFrame(frames)),
                Fmt.pct(e.sharePercent(total)),
                Fmt.num(e.callsPerFrame(frames)),
                Fmt.num(e.verticesPerFrame(frames)),
                Fmt.count(e.gcFrames())};
    }

    /** One row of the Mods table. The caller supplies the name: a mod id, or a member's label. */
    public static String[] mods(String name, RankedEntry e, int frames, long total) {
        return new String[]{
                name,
                Fmt.pct(e.sharePercent(total)),
                Fmt.us(e.usPerFrame(frames)),
                Fmt.peakMs(e.maxFrameNanos(), e.gcFrames()),
                Fmt.count(e.gcFrames()),
                Fmt.num(e.allCallsPerFrame(frames))};
    }
}

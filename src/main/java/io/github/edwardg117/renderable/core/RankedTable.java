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
}

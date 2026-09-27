package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * The columns of the Blocks and Sections tables (spec addendum §P5.D3): their names and the text of each cell,
 * written once for the text report and the results screen, as {@link RankedTable} is for the ranked tables. The
 * screen writes its headers out in {@code Tab}, and {@code TabTest} holds them equal to these.
 */
public final class MeshTable {

    /** Headers of the Blocks table. */
    public static final List<String> BLOCK = List.of("block", "mesh ms/s", "us/call", "calls");

    /** Headers of the report's Sections table. */
    public static final List<String> SECTION = List.of("section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms/rebuild");

    /**
     * Headers of the screen's Sections table. The only difference between the surfaces: the last is shortened to
     * fit its column at the default GUI scale (spec addendum §P4.C6 #1).
     */
    public static final List<String> SECTION_SCREEN = List.of("section", "rebuilds", "rebuilds/s", "mesh ms", "avg ms/rb");

    private MeshTable() {
    }

    /** One row of the Blocks table; {@code seconds} is the session's sampled length. */
    public static String[] block(MeshEntry e, double seconds) {
        return new String[]{e.key().label(), Fmt.num(e.msPerSecond(seconds)), Fmt.us(e.usPerCall()), Fmt.count(e.calls())};
    }

    /** One row of the Sections table. */
    public static String[] section(MeshEntry e, double seconds) {
        return new String[]{e.key().label(), Fmt.count(e.calls()), Fmt.num(e.callsPerSecond(seconds)), Fmt.ms(e.nanos()),
                Fmt.ms2(e.calls() == 0 ? 0 : e.nanos() / e.calls())};
    }
}

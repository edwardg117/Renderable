package io.github.edwardg117.renderable.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The session's sound plays (spec addendum §P6.5): the Mods tab's sound rows, summed for the "Sound plays" line in
 * the report's Frames section and on the Frames tab. Rows are inclusive like every row, so a play made inside another
 * play, by a {@code PlaySoundEvent} handler, counts in both.
 *
 * @param rows    every sound row, the costliest first
 * @param nanos   the rows' time, render and tick sides
 * @param plays   the rows' calls, render and tick sides: every play, a skipped one included
 * @param frames  frames in the session
 * @param seconds the session's length
 */
public record SoundPlays(List<RankedEntry> rows, long nanos, long plays, int frames, double seconds) {

    public static SoundPlays of(SessionResult r) {
        List<RankedEntry> rows = new ArrayList<>();
        long nanos = 0;
        long plays = 0;
        for (RankedEntry e : Ranking.byKey(r, ProfileKey.Category.MOD)) {
            if (!(e.key() instanceof ModKey k) || k.kind() != ModKey.Kind.SOUND) continue;
            rows.add(e);
            nanos += e.totalNanos();
            plays += e.calls() + e.tickCalls();
        }
        return new SoundPlays(List.copyOf(rows), nanos, plays, r.frameCount(), r.stats().durationSeconds());
    }

    /** True when anything played. The line is printed only then. */
    public boolean any() {
        return plays > 0;
    }

    /** The rows' time, milliseconds a frame. */
    public double msPerFrame() {
        return frames == 0 ? 0 : nanos / 1e6 / frames;
    }

    /** Plays a second over the session. */
    public double playsPerSecond() {
        return seconds <= 0 ? 0 : plays / seconds;
    }

    /** One row's plays a second over the session. */
    public double playsPerSecond(RankedEntry row) {
        return seconds <= 0 ? 0 : (row.calls() + row.tickCalls()) / seconds;
    }

    /**
     * A rate a second as the Sound plays line writes it: whole from 9.95 up ("85", and "10" rather than "10.0"),
     * one decimal below ("1.5"), and "under 0.1" for a rate above zero that would otherwise read "0.0".
     */
    public static String perSecond(double rate) {
        if (rate >= 9.95) return String.format(Locale.ROOT, "%.0f", rate);
        if (rate > 0 && rate < 0.05) return "under 0.1";
        return Fmt.num(rate);
    }

    /**
     * {@code 1.21 ms a frame, 180 plays a second}, the Frames tab's value. The rate goes through
     * {@link #perSecond}: whole at ten a second or more, one decimal below, and "under 0.1" for a rate that would
     * otherwise read "0.0", so a line printed only when something played never reads as though nothing did.
     */
    public String summary() {
        return String.format(Locale.ROOT, "%.2f", msPerFrame()) + " ms a frame, " + perSecond(playsPerSecond())
                + " plays a second";
    }

    /** {@code Sound plays: 1.21 ms a frame, 180 plays a second}, the report's line. */
    public String line() {
        return "Sound plays: " + summary();
    }
}

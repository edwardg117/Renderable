package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.SessionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every rule over a finished session and ranks what they find (spec addendum §P5.B1). Stutter comes
 * first, by its worst occurrence, then frame rate, by milliseconds per frame, then notes. Ties go to the
 * player's first, then keep the rules' order. Derived on demand, never stored with the session.
 */
public final class Advisor {
    /** How many the text report prints; the rest are on the Advice tab and in the JSON. */
    public static final int REPORT_LIMIT = 6;

    static final Comparator<Recommendation> RANKING = Comparator
            .comparing(Recommendation::kind)
            .thenComparing((a, b) -> switch (a.kind()) {
                case STUTTER -> Double.compare(b.worstMs(), a.worstMs());
                case FRAME_RATE -> Double.compare(b.msPerFrame(), a.msPerFrame());
                case NOTE -> 0;
            })
            .thenComparing(Recommendation::audience);

    private Advisor() {
    }

    /**
     * The advice for a finished session. A rule that throws costs the advice, not the report: the report is written
     * on the render thread as the session ends, where a throw would end the client and lose the session. The
     * failure becomes one note that names it, so the report still says something went wrong.
     */
    public static List<Recommendation> advise(SessionResult r, Environment env) {
        try {
            return advise(Facts.of(r, env));
        } catch (RuntimeException e) {
            return List.of(failed(e));
        }
    }

    /** The note that stands in for the advice when a rule throws: the exception and where it was thrown. */
    static Recommendation failed(RuntimeException e) {
        StackTraceElement[] at = e.getStackTrace();
        return new Recommendation("advice-failed", Recommendation.Kind.NOTE, Recommendation.Audience.PACK_AUTHOR,
                "Renderable could not work out its recommendations",
                "A rule failed with " + e + (at.length == 0 ? "" : " at " + at[0]) + ".",
                "The rest of the report is complete. Report this to Renderable with this file.", 0, 0);
    }

    static List<Recommendation> advise(Facts f) {
        List<Recommendation> out = new ArrayList<>();
        if (f.frames() == 0) return List.of();
        SettingsRules.all(f, out);
        SceneRules.all(f, out);
        MemoryRules.all(f, out);
        PackRules.all(f, out);
        out.sort(RANKING);   // stable, so ties keep the rules' order
        return List.copyOf(out);
    }
}

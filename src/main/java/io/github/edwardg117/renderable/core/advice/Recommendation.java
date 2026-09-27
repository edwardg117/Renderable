package io.github.edwardg117.renderable.core.advice;

import java.util.Locale;

/**
 * One piece of advice drawn from a session (spec addendum §P5.B1): who it is for, what kind of problem it is,
 * what was measured, what to do, and how much time the finding is about.
 *
 * @param rule       the rule that made it, as spec addendum §P5.B2 names it
 * @param msPerFrame the measured cost the finding is about, averaged over the session. It is an upper bound on
 *                   what the action can save, never a prediction; 0 for a note
 * @param worstMs    the largest single occurrence, for stutter; 0 otherwise
 */
public record Recommendation(String rule, Kind kind, Audience audience, String title, String finding, String action,
                             double msPerFrame, double worstMs) {

    /** Who can act on it. The player's come first when two rank the same. */
    public enum Audience {
        PLAYER("player"), PACK_AUTHOR("pack author");

        private final String label;

        Audience(String label) {
            this.label = label;
        }

        /** How the report tags it: {@code [player]} or {@code [pack author]}. */
        public String label() {
            return label;
        }
    }

    /** In ranking order: stutter first, because a stall averaged over the session hides behind any steady cost. */
    public enum Kind { STUTTER, FRAME_RATE, NOTE }

    /** What is at stake, for a heading: "up to 83 ms", "1.2 ms/frame", or "" for a note. */
    public String atStake() {
        return switch (kind) {
            case STUTTER -> String.format(Locale.ROOT, "up to %.0f ms", worstMs);
            case FRAME_RATE -> msPerFrame < 0.05 ? "under 0.1 ms/frame" : String.format(Locale.ROOT, "%.1f ms/frame", msPerFrame);
            case NOTE -> "";
        };
    }
}

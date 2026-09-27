package io.github.edwardg117.renderable.core;

import java.util.List;

/**
 * The one place the "there is nothing here" sentences live, so the results screen and the text report
 * cannot drift apart on the difference between "that mod is not installed" and "nothing happened"
 * (spec addendum §P4.C1). Callers add their own indentation and line ending.
 */
public final class EmptyStates {

    private EmptyStates() {
    }

    /**
     * A ranked category with no rows. The sentence blames the hook, but the block entity and entity hooks are
     * required vanilla mixins, so in practice an empty list means nothing of that kind was drawn; correcting the
     * sentence changes the report and is deferred (spec addendum §P4.3).
     */
    public static String ranked() {
        return "(no data: hook not active in this build)";
    }

    public static String particles(Environment env) {
        return env.hooksApplied().contains(Hook.PARTICLE_RENDER.name())
                ? "(no particles were rendered)"
                : ranked();
    }

    public static String blocks(Environment env) {
        return mesh(env, "blocks were meshed");
    }

    public static String sections(Environment env) {
        return mesh(env, "sections were rebuilt");
    }

    /**
     * The hook list records hooks that fired while sampling, not hooks that are installed. Sodium's upload hook
     * fires on every frame a session samples, so either Sodium hook in the list shows the hooks are in place, and
     * an empty table then means nothing was meshed or rebuilt while sampling, not that the hooks are missing.
     */
    private static String mesh(Environment env, String nothing) {
        List<String> fired = env.hooksApplied();
        return fired.contains(Hook.SODIUM_MESH.name()) || fired.contains(Hook.SODIUM_UPLOAD.name())
                ? "(no data: no " + nothing + ")"
                : "(no data: Sodium hooks were not active)";
    }

    /**
     * The Flywheel section's empty state, or an empty string when there is data to print. {@code samples}
     * is {@code FlywheelStats.samples()}.
     */
    public static String flywheel(Environment env, int samples) {
        if (!env.has("flywheelBackend")) return "(no data: Flywheel is not installed)";
        if (samples == 0) return "(no data: no Flywheel visualization ran; backend " + env.value("flywheelBackend") + ")";
        return "";
    }

    /** The Mods table's empty state (spec addendum §P5.A7). */
    public static String mods(Environment env) {
        String why = eventTimingUnavailable(env);
        return why != null
                ? "(event timing unavailable: " + why + ")"
                : "(no mod handlers, HUD layers or packets were timed)";
    }

    /**
     * A line to print above a Mods table that has rows but no event handlers because the bus could not be
     * reached, or an empty string when handlers were timed or the report predates event timing.
     */
    public static String modsHandlersMissing(Environment env) {
        String why = eventTimingUnavailable(env);
        return why != null ? "(event handlers were not timed: " + why + ")" : "";
    }

    /** Why event timing could not run, or null. */
    private static String eventTimingUnavailable(Environment env) {
        String status = env.value(Environment.EVENT_TIMING);
        return status.startsWith(Environment.UNAVAILABLE) ? status.substring(Environment.UNAVAILABLE.length()) : null;
    }

    /** Said by the report and the Advice tab when no rule fired (spec addendum §P5.B5). */
    public static String advice() {
        return "No recommendations: nothing measured crossed a threshold.";
    }
}

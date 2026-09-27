package io.github.edwardg117.renderable.core;

/**
 * The one place the "there is nothing here" sentences live, so the results screen and the text report
 * cannot drift apart on the difference between "that mod is not installed" and "nothing happened"
 * (spec addendum §P4.C1). Callers add their own indentation and line ending.
 */
public final class EmptyStates {

    private EmptyStates() {
    }

    private static final String NOT_INSTALLED = "(hook not installed)";

    /**
     * An empty Block Entities table (spec addendum §P5.D2). Its hooks are required vanilla mixins, without which the
     * game does not start, so in practice the table is empty because nothing of the kind ran while sampling.
     */
    public static String blockEntities(Environment env) {
        return ranked(env, Hook.BLOCK_ENTITY_RENDER, Hook.BLOCK_ENTITY_TICK);
    }

    /** An empty Entities table: as {@link #blockEntities}, with the rider hook as well. */
    public static String entities(Environment env) {
        return ranked(env, Hook.ENTITY_RENDER, Hook.ENTITY_TICK, Hook.RIDER_TICK);
    }

    public static String particles(Environment env) {
        return failed(env, Hook.PARTICLE_RENDER, Hook.PARTICLE_TICK) ? NOT_INSTALLED : "(no particles were rendered)";
    }

    public static String blocks(Environment env) {
        return mesh(env, "blocks were meshed");
    }

    public static String sections(Environment env) {
        return mesh(env, "sections were rebuilt");
    }

    /**
     * Sodium's tables tell four cases apart (spec addendum §P5.D1): Sodium is absent; its mesh hook did not apply;
     * the hook is in place and nothing was meshed or rebuilt while sampling; or Sodium is present but no injector
     * of the hook was checked, which in a world means Mixin rejected the whole hook class and logged why.
     */
    private static String mesh(Environment env, String nothing) {
        return switch (env.state(Hook.SODIUM_MESH)) {
            case FAILED -> "(no data: Sodium's mesh hook did not apply)";
            case INSTALLED -> "(no data: no " + nothing + ")";
            case NOT_LOADED -> env.has("sodium")
                    ? "(no data: Sodium's mesh hook never loaded; the game log says why)"
                    : "(no data: Sodium is not installed)";
        };
    }

    private static String ranked(Environment env, Hook... hooks) {
        return failed(env, hooks) ? NOT_INSTALLED : "(nothing of this kind was drawn or ticked)";
    }

    private static boolean failed(Environment env, Hook... hooks) {
        for (Hook h : hooks) {
            if (env.state(h) == Environment.HookState.FAILED) return true;
        }
        return false;
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

    /** A Flywheel table whose samples found no visual classes (spec addendum §P5.D2), in place of an empty table. */
    public static String flywheelNoVisuals(int samples) {
        return "(Flywheel sampled " + (samples == 1 ? "once" : samples + " times") + " and found no visuals alive)";
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

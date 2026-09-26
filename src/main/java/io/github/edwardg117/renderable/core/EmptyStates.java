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
}

package io.github.edwardg117.renderable.core;

import java.util.Locale;

/**
 * The parts of a frame an attribution-only call can run in, for named time (spec addendum §P5.A6): the
 * render window (frame start to render end), the client tick, and {@code Minecraft.runAllTasks}. Anything
 * else, such as a handler run during the frame-cap wait, is in no window.
 */
public enum Window {
    RENDER, TICK, TASKS;

    public static final Window[] VALUES = values();
    public static final int COUNT = VALUES.length;

    /** Lower case, for reports and JSON keys. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}

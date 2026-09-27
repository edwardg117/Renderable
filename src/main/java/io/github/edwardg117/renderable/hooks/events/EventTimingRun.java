package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.core.Session;

/** One session's event timing: its session, its render thread, and whether its proxies still time anything. */
final class EventTimingRun {
    final Session session;
    final Thread renderThread;
    /** Cleared before the arrays are restored, so a post already iterating a proxy array stops timing at once. */
    volatile boolean live = true;

    EventTimingRun(Session session, Thread renderThread) {
        this.session = session;
        this.renderThread = renderThread;
    }
}

package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.core.Accumulator;
import net.neoforged.bus.GeneratedEventListener;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;

/**
 * A listener wrapped for one session (spec addendum §P5.A3). It times the listener it wraps into its owner's
 * accumulator as an attribution-only call. Off the render thread, or once its run has ended, it only calls
 * through. It is not an {@code IWrapperListener}, so the bus never unwraps it.
 *
 * <p>Bus 8.0.5 seals {@code EventListener}, so this extends {@code GeneratedEventListener}, the non-sealed base of
 * the listeners the bus generates. Nothing in the bus tests for that class, so the proxy is treated as any other
 * listener.
 */
final class TimedListener extends GeneratedEventListener {
    private final EventListener delegate;
    private final Accumulator accumulator;
    private final EventTimingRun run;

    TimedListener(EventListener delegate, Accumulator accumulator, EventTimingRun run) {
        this.delegate = delegate;
        this.accumulator = accumulator;
        this.run = run;
    }

    @Override
    public void invoke(Event event) {
        EventTimingRun r = run;
        if (!r.live || Thread.currentThread() != r.renderThread) {
            delegate.invoke(event);
            return;
        }
        r.session.beginAttribution(accumulator);
        try {
            delegate.invoke(event);
        } finally {
            r.session.end();
        }
    }

    /** The bus's exception log names listeners by {@code toString}, so this reads as the listener it wraps. */
    @Override
    public String toString() {
        return delegate.toString();
    }
}

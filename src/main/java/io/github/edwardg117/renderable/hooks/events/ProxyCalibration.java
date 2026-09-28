package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import net.neoforged.bus.ConsumerEventHandler;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;

/**
 * One attribution-only pair to time (spec addendum §P5.C2): a timing proxy around a listener that does nothing,
 * invoked as the bus invokes it. HUD layers and packets pay the same begin and end, plus a map lookup the proxy
 * does not. Render thread only, against calibration's scratch session.
 */
public final class ProxyCalibration {

    private ProxyCalibration() {
    }

    /**
     * A call of the proxy. The delegate is a {@code ConsumerEventHandler}, the listener {@code addListener}
     * registers, around a consumer that does nothing.
     */
    public static Runnable invocation(Session scratch) {
        EventListener nothing = new ConsumerEventHandler(event -> {
        });
        TimedListener proxy = new TimedListener(nothing,
                scratch.accumulator(ModKey.event(RenderableMod.MOD_ID, "calibration")),
                new EventTimingRun(scratch, Thread.currentThread()));
        Event event = new Event() {
        };
        return () -> proxy.invoke(event);
    }
}

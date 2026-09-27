package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.RenderableMod;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.Hook;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.hooks.Probe;
import net.neoforged.bus.api.EventListener;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Times mod event handlers for the length of a session (spec addendum §P5.A3). Every listener list of
 * NeoForge's game bus is swapped for timing proxies when a session starts and swapped back when it ends, so
 * nothing of it is installed between sessions. The bus loads in the boot module layer, where no mixin
 * reaches, which is why this is not a mixin. Render thread only.
 */
public final class EventTiming {
    private static final String ON = "on";

    private static BusInternals bus;
    /** Why the bus could not be reached, or null when it could or has not been tried. */
    private static String unavailable;
    private static EventTimingRun run;
    private static ListenerSwap swap;
    private static ListenerOwners owners;

    private EventTiming() {
    }

    /** "on", or {@link Environment#UNAVAILABLE} and why, for the report's environment header. */
    public static String status() {
        bus();
        return unavailable == null ? ON : Environment.UNAVAILABLE + unavailable;
    }

    /**
     * Wraps every listener list of the game bus for {@code session}. Runs inside a tick listener, so a failure
     * here would crash the client: anything thrown turns event timing off instead (see {@link #fail}).
     */
    public static void start(Session session) {
        stop();
        BusInternals b = bus();
        if (b == null) return;
        try {
            EventTimingRun r = new EventTimingRun(session, Thread.currentThread());
            ListenerOwners o = new ListenerOwners(b::registrations);
            ModOwners mods = ModOwners.fromModList();
            run = r;
            owners = o;
            swap = new ListenerSwap(b, (listener, eventClass) -> wrap(r, o, mods, listener, eventClass));
            o.newRound();
            if (swap.wrapAll() > 0) Probe.markFired(Hook.EVENTS);
        } catch (RuntimeException | LinkageError e) {
            fail("start", e);
        }
    }

    /**
     * Wraps the lists rebuilt or created since the last pass; called about once a second while sampling, from
     * inside the frame loop, so a failure turns event timing off rather than escaping.
     */
    public static void recheck() {
        if (swap == null) return;
        try {
            owners.newRound();
            swap.wrapAll();
        } catch (RuntimeException | LinkageError e) {
            fail("recheck", e);
        }
    }

    /** Stops timing and puts every array back. Safe to call when nothing is running, and never throws. */
    public static void stop() {
        EventTimingRun r = run;
        if (r != null) r.live = false;
        ListenerSwap s = swap;
        run = null;
        swap = null;
        owners = null;
        if (s == null) return;
        try {
            int restored = s.restore();
            RenderableMod.LOGGER.info("Renderable event timing restored {} listener lists; {} still hold a timing proxy",
                    restored, listsHoldingProxies());
        } catch (RuntimeException | LinkageError e) {
            // The run is already dead, so a proxy left behind only calls through; the list drops it when it rebuilds.
            unavailable = describe(e);
            bus = null;
            RenderableMod.LOGGER.error("Renderable could not put every event listener list back: {}", unavailable, e);
        }
    }

    /**
     * The bus was found but did not behave as bus 8.0.5 does (spec addendum §P5.A3's risks): the lookups catch a
     * rename, not a change of behaviour. Event timing is off for the rest of the client run, and the report's
     * {@code eventTiming} entry says why.
     */
    private static void fail(String during, Throwable e) {
        RenderableMod.LOGGER.warn("Renderable event timing failed during {} and is off until restart", during, e);
        stop();
        unavailable = describe(e);
        bus = null;
    }

    private static String describe(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** Lists holding a timing proxy right now; zero outside a session. For {@code /renderable debug events}. */
    public static int listsHoldingProxies() {
        BusInternals b = bus();
        return b == null ? 0 : ListenerSwap.listsHoldingProxies(b);
    }

    private static BusInternals bus() {
        if (bus == null && unavailable == null) {
            try {
                bus = BusInternals.of(NeoForge.EVENT_BUS);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                unavailable = describe(e);
                RenderableMod.LOGGER.warn("Renderable cannot time event handlers: {}", unavailable, e);
            }
        }
        return bus;
    }

    private static EventListener wrap(EventTimingRun r, ListenerOwners o, ModOwners mods, EventListener listener,
                                      Class<?> eventClass) {
        Class<?> owner = o.ownerOf(listener);
        String mod = owner == null ? ModOwners.UNKNOWN : mods.modOf(owner);
        if (RenderableMod.MOD_ID.equals(mod)) return listener;
        return new TimedListener(listener, r.session.accumulator(ModKey.event(mod, eventClass.getName())), r);
    }
}

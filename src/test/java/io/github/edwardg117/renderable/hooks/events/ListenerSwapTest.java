package io.github.edwardg117.renderable.hooks.events;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import net.neoforged.bus.ConsumerEventHandler;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Event timing against a real NeoForge bus (spec addendum §P5.A3). The test classpath carries the same bus
 * version the game runs, so these tests pin the bus behaviour {@link BusInternals} relies on as well as ours.
 */
class ListenerSwapTest {
    static final long MS = 1_000_000L;

    /** Listeners record their name here and advance the fake clock, so each one's time is known. */
    static final List<String> CALLS = new ArrayList<>();
    static final long[] NOW = {0};

    public static class Tick extends Event {
    }

    public static class Stoppable extends Event implements ICancellableEvent {
    }

    public static class InstanceSubscriber {
        @SubscribeEvent
        public void onTick(Tick e) {
            CALLS.add("instance");
            NOW[0] += 1 * MS;
        }
    }

    public static class StaticSubscriber {
        @SubscribeEvent
        public static void onTick(Tick e) {
            CALLS.add("static");
        }
    }

    public static class MethodSubscriber {
        @SubscribeEvent
        public static void onTick(Tick e) {
            CALLS.add("method");
        }
    }

    public static class Stoppers {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void stop(Stoppable e) {
            CALLS.add("stopper");
            e.setCanceled(true);
        }

        @SubscribeEvent
        public static void skipped(Stoppable e) {
            CALLS.add("skipped");
        }

        @SubscribeEvent(receiveCanceled = true)
        public static void seesCancelled(Stoppable e) {
            CALLS.add("sees cancelled");
        }
    }

    IEventBus bus;
    BusInternals internals;
    Session session;
    EventTimingRun run;
    ListenerOwners owners;

    @BeforeEach
    void setUp() throws Exception {
        CALLS.clear();
        NOW[0] = 0;
        bus = BusBuilder.builder().build();
        internals = BusInternals.of(bus);
        session = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> NOW[0], 0);
        run = new EventTimingRun(session, Thread.currentThread());
        owners = new ListenerOwners(internals::registrations);
    }

    /** Wraps every listener, keying it by the simple name of the class that registered it. */
    ListenerSwap swapTimingEverything() {
        return new ListenerSwap(internals, (listener, eventClass) -> {
            Class<?> owner = owners.ownerOf(listener);
            String mod = owner == null ? "unknown" : owner.getSimpleName();
            return new TimedListener(listener, session.accumulator(ModKey.event(mod, eventClass.getName())), run);
        });
    }

    Accumulator timing(String mod, Class<?> event) {
        return session.accumulator(ModKey.event(mod, event.getName()));
    }

    @Test
    void aSwappedBusInvokesTheSameListenersInOrderAndTimesEach() {
        bus.addListener(Tick.class, e -> {
            CALLS.add("lambda");
            NOW[0] += 2 * MS;
        });
        bus.register(new InstanceSubscriber());
        bus.register(StaticSubscriber.class);
        bus.post(new Tick());
        List<String> before = List.copyOf(CALLS);
        CALLS.clear();

        assertEquals(1, swapTimingEverything().wrapAll(), "one list, for Tick");
        session.onRenderPre(0, 0);
        bus.post(new Tick());

        assertEquals(before, CALLS, "the same listeners, in the same order");
        assertEquals(1 * MS, timing("InstanceSubscriber", Tick.class).renderNanos);
        assertEquals(1, timing("StaticSubscriber", Tick.class).calls);
        long lambdaNanos = session.accumulators().entrySet().stream()
                .filter(e -> ((ModKey) e.getKey()).modId().startsWith("ListenerSwapTest"))
                .mapToLong(e -> e.getValue().renderNanos).sum();
        assertEquals(2 * MS, lambdaNanos, "a lambda belongs to the class that wrote it");
    }

    @Test
    void aThrowingListenerReachesTheExceptionHandlerWithItsIndexAndIsRethrown() throws Exception {
        List<String> handled = new ArrayList<>();
        bus = BusBuilder.builder().setExceptionHandler((b, event, listeners, index, throwable) ->
                handled.add(index + " " + listeners[index] + " " + throwable.getMessage())).build();
        internals = BusInternals.of(bus);
        owners = new ListenerOwners(internals::registrations);
        bus.addListener(Tick.class, e -> CALLS.add("first"));
        bus.addListener(Tick.class, e -> {
            throw new IllegalStateException("boom");
        });

        assertEquals(1, swapTimingEverything().wrapAll());
        session.onRenderPre(0, 0);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> bus.post(new Tick()));

        assertEquals("boom", thrown.getMessage());
        assertEquals(List.of("first"), CALLS);
        assertEquals(1, handled.size());
        assertTrue(handled.get(0).startsWith("1 "), handled.get(0));
        assertFalse(handled.get(0).contains("TimedListener"), "the log names the listener, not the proxy: " + handled.get(0));
        NOW[0] = 10 * MS;
        session.onRenderPre(0, 0);
        assertEquals(0, session.unbalancedFrames(), "the proxy ended its call on the way out");
    }

    @Test
    void cancellationStillSkipsListenersThatDoNotReceiveIt() {
        bus.register(Stoppers.class);
        assertEquals(1, swapTimingEverything().wrapAll());
        session.onRenderPre(0, 0);
        bus.post(new Stoppable());
        assertEquals(List.of("stopper", "sees cancelled"), CALLS);
    }

    @Test
    void ownersResolveEveryKindOfRegistrationTarget() throws Exception {
        bus.register(new InstanceSubscriber());
        bus.register(StaticSubscriber.class);
        bus.register(MethodSubscriber.class.getMethod("onTick", Tick.class));
        bus.addListener(Tick.class, e -> CALLS.add("lambda"));
        bus.post(new Tick());    // builds the cached array, unwrapping what the bus unwraps

        ListenerList list = internals.lists().get(Tick.class);
        List<String> owned = new ArrayList<>();
        for (EventListener l : internals.cachedArray(list).get()) {
            Class<?> owner = owners.ownerOf(l);
            assertNotNull(owner, "every listener the bus posts to has an owner: " + l);
            owned.add(owner.getSimpleName());
        }
        assertTrue(owned.contains("InstanceSubscriber"), owned.toString());
        assertTrue(owned.contains("StaticSubscriber"), owned.toString());
        assertTrue(owned.contains("MethodSubscriber"), "a single static method belongs to its class: " + owned);
        assertTrue(owned.stream().anyMatch(o -> o.startsWith("ListenerSwapTest")), owned.toString());
        assertSame(StaticSubscriber.class, ListenerOwners.ownerClass(StaticSubscriber.class), "a class target is itself");
    }

    @Test
    void restorePutsBackEveryArrayItInstalled() {
        bus.register(StaticSubscriber.class);
        bus.post(new Tick());
        bus.post(new Stoppable());
        ListenerList tickList = internals.lists().get(Tick.class);
        EventListener[] original = internals.cachedArray(tickList).get();

        ListenerSwap swap = swapTimingEverything();
        swap.wrapAll();
        assertEquals(1, ListenerSwap.listsHoldingProxies(internals), "Stoppable has no listeners, so nothing to wrap");
        assertNotSame(original, internals.cachedArray(tickList).get());

        assertEquals(1, swap.restore());
        assertSame(original, internals.cachedArray(tickList).get(), "the very array the bus built");
        assertEquals(0, ListenerSwap.listsHoldingProxies(internals));
        bus.post(new Tick());
        assertEquals(List.of("static", "static"), CALLS);
    }

    @Test
    void aListRebuiltMidSessionIsWrappedAgainAndLeftAloneByRestore() {
        bus.register(StaticSubscriber.class);
        ListenerSwap swap = swapTimingEverything();
        assertEquals(1, swap.wrapAll());

        bus.register(new InstanceSubscriber());          // marks the Tick list for a rebuild
        session.onRenderPre(0, 0);
        bus.post(new Tick());                            // the rebuild replaces the proxies with originals
        assertEquals(0, timing("InstanceSubscriber", Tick.class).calls, "not timed until the next pass");
        assertEquals(0, ListenerSwap.listsHoldingProxies(internals));

        owners.newRound();
        assertEquals(1, swap.wrapAll(), "the rebuilt list is wrapped again");
        bus.post(new Tick());
        assertEquals(1, timing("InstanceSubscriber", Tick.class).calls, "a listener registered mid-session is timed");

        bus.register(new InstanceSubscriber());          // a second rebuild, which restore must not undo
        bus.post(new Tick());
        EventListener[] rebuilt = internals.cachedArray(internals.lists().get(Tick.class)).get();
        assertEquals(0, swap.restore(), "the only array it installed has already been replaced");
        assertSame(rebuilt, internals.cachedArray(internals.lists().get(Tick.class)).get());
    }

    @Test
    void registeringAndUnregisteringMidSessionLeavesNoProxyBehind() {
        bus.register(StaticSubscriber.class);
        ListenerSwap swap = swapTimingEverything();
        assertEquals(1, swap.wrapAll());
        InstanceSubscriber late = new InstanceSubscriber();
        bus.register(late);
        owners.newRound();
        assertEquals(1, swap.wrapAll(), "the list rebuilt for the late listener is wrapped again");
        bus.unregister(late);
        assertEquals(1, swap.restore());

        assertEquals(0, ListenerSwap.listsHoldingProxies(internals));
        bus.post(new Tick());
        assertEquals(List.of("static"), CALLS, "the unregistered listener is gone, and nothing is wrapped");
    }

    @Test
    void offTheRenderThreadAProxyOnlyCallsThrough() {
        run = new EventTimingRun(session, new Thread(() -> { }));   // a render thread that is not this one
        bus.register(new InstanceSubscriber());
        assertEquals(1, swapTimingEverything().wrapAll());
        session.onRenderPre(0, 0);
        bus.post(new Tick());
        assertEquals(List.of("instance"), CALLS);
        assertEquals(0, timing("InstanceSubscriber", Tick.class).calls);
    }

    @Test
    void anEndedRunOnlyCallsThrough() {
        bus.register(new InstanceSubscriber());
        assertEquals(1, swapTimingEverything().wrapAll());
        run.live = false;
        session.onRenderPre(0, 0);
        bus.post(new Tick());
        assertEquals(List.of("instance"), CALLS);
        assertEquals(0, timing("InstanceSubscriber", Tick.class).calls);
    }

    @Test
    void aListenerTheWrapperLeavesAloneKeepsItsPlaceAndIsNotTimed() {
        bus.register(StaticSubscriber.class);
        bus.register(new InstanceSubscriber());
        bus.register(Stoppers.class);
        EventListener[] tick = internals.lists().get(Tick.class).getListeners();
        EventListener[] stoppable = internals.lists().get(Stoppable.class).getListeners();
        // EventTiming.wrap leaves Renderable's own listeners alone like this; two test classes stand in for it.
        ListenerSwap swap = new ListenerSwap(internals, (listener, eventClass) -> {
            Class<?> owner = owners.ownerOf(listener);
            if (owner == StaticSubscriber.class || owner == Stoppers.class) return listener;
            return new TimedListener(listener, timing(owner.getSimpleName(), eventClass), run);
        });

        assertEquals(1, swap.wrapAll(), "Tick only: a list with nothing to wrap is not swapped");
        assertSame(stoppable, internals.cachedArray(internals.lists().get(Stoppable.class)).get());
        EventListener[] wrapped = internals.cachedArray(internals.lists().get(Tick.class)).get();
        assertSame(tick[0], wrapped[0], "the listener left alone is itself, in its place");
        assertInstanceOf(TimedListener.class, wrapped[1]);

        session.onRenderPre(0, 0);
        bus.post(new Tick());
        assertEquals(List.of("static", "instance"), CALLS);
        assertEquals(1 * MS, timing("InstanceSubscriber", Tick.class).renderNanos);
        assertEquals(1, swap.restore());
        assertSame(tick, internals.cachedArray(internals.lists().get(Tick.class)).get());
    }

    @Test
    void ownersRebuildTheirIndexAtMostOncePerRound() {
        int[] reads = {0};
        ListenerOwners counted = new ListenerOwners(() -> {
            reads[0]++;
            return internals.registrations();
        });
        EventListener stranger = new ConsumerEventHandler(e -> { });   // registered on no bus

        assertNull(counted.ownerOf(stranger));
        assertNull(counted.ownerOf(stranger));
        assertEquals(2, reads[0], "built once, then rebuilt once, for the first miss of the round");
        counted.newRound();
        assertNull(counted.ownerOf(stranger));
        assertEquals(3, reads[0], "a new round allows one more rebuild");
    }
}

package io.github.edwardg117.renderable.hooks.events;

import net.neoforged.bus.EventBus;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.LockHelper;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.IEventBus;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The private members of NeoForge's event bus 8.0.x that event timing needs (spec addendum §P5.A0 and
 * §P5.A3). The bus module is declared {@code open}, so they are reachable by reflection. Each is looked up
 * once, in {@link #of}, so a bus version that renamed one fails there rather than part way through a session.
 */
final class BusInternals {
    private final EventBus bus;
    private final MethodHandle listenerLists;
    private final MethodHandle readMap;
    private final MethodHandle registrations;
    private final MethodHandle cachedArray;

    private BusInternals(EventBus bus, MethodHandle listenerLists, MethodHandle readMap,
                         MethodHandle registrations, MethodHandle cachedArray) {
        this.bus = bus;
        this.listenerLists = listenerLists;
        this.readMap = readMap;
        this.registrations = registrations;
        this.cachedArray = cachedArray;
    }

    /** Looks the members up for {@code bus}. Fails when the bus is not bus 8.0's {@link EventBus}. */
    static BusInternals of(IEventBus bus) throws ReflectiveOperationException {
        if (!(bus instanceof EventBus eventBus)) {
            throw new ReflectiveOperationException("the bus is a " + bus.getClass().getName() + ", not an "
                    + EventBus.class.getName());
        }
        MethodHandles.Lookup here = MethodHandles.lookup();
        MethodHandles.Lookup inBus = MethodHandles.privateLookupIn(EventBus.class, here);
        MethodHandles.Lookup inLockHelper = MethodHandles.privateLookupIn(LockHelper.class, here);
        MethodHandles.Lookup inList = MethodHandles.privateLookupIn(ListenerList.class, here);
        return new BusInternals(eventBus,
                inBus.findGetter(EventBus.class, "listenerLists", LockHelper.class),
                inLockHelper.findVirtual(LockHelper.class, "getReadMap", MethodType.methodType(Map.class)),
                inBus.findGetter(EventBus.class, "listeners", ConcurrentHashMap.class),
                inList.findGetter(ListenerList.class, "listeners", AtomicReference.class));
    }

    /** Every event class's listener list, as a snapshot that the bus replaces rather than edits. */
    @SuppressWarnings("unchecked")
    Map<Class<?>, ListenerList> lists() {
        try {
            return (Map<Class<?>, ListenerList>) readMap.invoke(listenerLists.invoke(bus));
        } catch (Throwable t) {
            throw new IllegalStateException("could not read the event bus's listener lists", t);
        }
    }

    /** Each registration target (a class, a static method, an object or a consumer) and the listeners it made. */
    @SuppressWarnings("unchecked")
    Map<Object, List<EventListener>> registrations() {
        try {
            return (Map<Object, List<EventListener>>) registrations.invoke(bus);
        } catch (Throwable t) {
            throw new IllegalStateException("could not read the event bus's registrations", t);
        }
    }

    /** The reference holding {@code list}'s cached array, which {@code EventBus.post} reads. */
    @SuppressWarnings("unchecked")
    AtomicReference<EventListener[]> cachedArray(ListenerList list) {
        try {
            return (AtomicReference<EventListener[]>) cachedArray.invoke(list);
        } catch (Throwable t) {
            throw new IllegalStateException("could not read a listener list's cached array", t);
        }
    }
}

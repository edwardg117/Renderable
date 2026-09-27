package io.github.edwardg117.renderable.hooks.events;

import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.EventListener;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Swaps every listener list's cached array on one bus for a wrapped copy, and back (spec addendum §P5.A3).
 * {@code EventBus.post} reads a list's array through an {@code AtomicReference}, so a compare-and-set there
 * changes what later posts invoke and nothing else. Registration, unregistration and a post already running
 * are untouched. Render thread only.
 */
final class ListenerSwap {

    /** What goes in a wrapped array in place of one listener: a proxy, or the listener itself. */
    interface Wrapper {
        EventListener wrap(EventListener listener, Class<?> eventClass);
    }

    private record Swapped(AtomicReference<EventListener[]> ref, EventListener[] original, EventListener[] wrapped) {
    }

    private final BusInternals bus;
    private final Wrapper wrapper;
    private final IdentityHashMap<ListenerList, Swapped> swapped = new IdentityHashMap<>();

    ListenerSwap(BusInternals bus, Wrapper wrapper) {
        this.bus = bus;
        this.wrapper = wrapper;
    }

    /**
     * Wraps every list whose cached array is not one this swap installed. That is all of them the first time;
     * after that, any list rebuilt since (a listener was registered or unregistered) or created since (the first
     * post of a new event class). A list with nothing to wrap is left alone. Returns how many lists it wrapped.
     */
    int wrapAll() {
        int n = 0;
        for (Map.Entry<Class<?>, ListenerList> e : bus.lists().entrySet()) {
            ListenerList list = e.getValue();
            Swapped mine = swapped.get(list);
            EventListener[] current = list.getListeners();   // rebuilds the array first if it is due
            if (mine != null && current == mine.wrapped()) continue;
            EventListener[] wrapped = wrap(current, e.getKey());
            if (wrapped == null) continue;
            AtomicReference<EventListener[]> ref = bus.cachedArray(list);
            if (ref.compareAndSet(current, wrapped)) {
                swapped.put(list, new Swapped(ref, current, wrapped));
                n++;
            }
        }
        return n;
    }

    /** A copy of {@code listeners} with each one the wrapper replaces, or null when it replaces none. */
    private EventListener[] wrap(EventListener[] listeners, Class<?> eventClass) {
        EventListener[] out = null;
        for (int i = 0; i < listeners.length; i++) {
            EventListener w = wrapper.wrap(listeners[i], eventClass);
            if (w != listeners[i]) {
                if (out == null) out = listeners.clone();
                out[i] = w;
            }
        }
        return out;
    }

    /**
     * Puts back every array this swap installed that is still in place; a list rebuilt meanwhile already holds a
     * fresh original array and is left alone. Returns how many it restored.
     */
    int restore() {
        int n = 0;
        for (Swapped s : swapped.values()) {
            if (s.ref().compareAndSet(s.wrapped(), s.original())) n++;
        }
        swapped.clear();
        return n;
    }

    /** Lists on {@code bus} whose cached array holds a {@link TimedListener}; zero outside a session. */
    static int listsHoldingProxies(BusInternals bus) {
        int n = 0;
        for (ListenerList list : bus.lists().values()) {
            EventListener[] array = bus.cachedArray(list).get();
            if (array == null) continue;
            for (EventListener l : array) {
                if (l instanceof TimedListener) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }
}

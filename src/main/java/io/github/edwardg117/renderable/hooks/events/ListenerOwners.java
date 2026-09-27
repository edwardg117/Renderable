package io.github.edwardg117.renderable.hooks.events;

import net.neoforged.bus.IWrapperListener;
import net.neoforged.bus.api.EventListener;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Which class registered each listener on a bus (spec addendum §P5.A3). The bus keeps a map from each
 * registration target to the listeners it made, and this inverts it by identity. A listener's own class says
 * nothing, because the bus defines every {@code @SubscribeEvent} listener as a hidden class in its own lookup.
 */
final class ListenerOwners {
    private final Supplier<Map<Object, List<EventListener>>> registrations;
    private IdentityHashMap<EventListener, Class<?>> index;
    private boolean rebuiltThisRound;

    ListenerOwners(Supplier<Map<Object, List<EventListener>>> registrations) {
        this.registrations = registrations;
        this.index = index(registrations.get());
    }

    /** Allows one more rebuild of the index; called before each wrapping pass. */
    void newRound() {
        rebuiltThisRound = false;
    }

    /**
     * The class that registered {@code listener}, or null when the bus has no record of it. A listener missing
     * from the index was registered after it was built, so the first miss in a round rebuilds it, once.
     */
    Class<?> ownerOf(EventListener listener) {
        Class<?> owner = index.get(listener);
        if (owner == null && !rebuiltThisRound) {
            rebuiltThisRound = true;
            index = index(registrations.get());
            owner = index.get(listener);
        }
        return owner;
    }

    static IdentityHashMap<EventListener, Class<?>> index(Map<Object, List<EventListener>> registrations) {
        IdentityHashMap<EventListener, Class<?>> out = new IdentityHashMap<>();
        for (Map.Entry<Object, List<EventListener>> e : registrations.entrySet()) {
            Class<?> owner = ownerClass(e.getKey());
            // The bus's lists are Collections.synchronizedList, which must not be iterated unlocked; toArray locks.
            for (Object o : e.getValue().toArray()) {
                EventListener listener = (EventListener) o;
                out.put(listener, owner);
                // The array the bus posts to may hold the unwrapped listener instead (ListenerList.unwrapListeners).
                if (listener instanceof IWrapperListener wrapper) out.put(wrapper.getWithoutCheck(), owner);
            }
        }
        return out;
    }

    /** A class target is itself, a single static method is its declaring class, and anything else its own class. */
    static Class<?> ownerClass(Object target) {
        if (target instanceof Class<?> c) return c;
        if (target instanceof Method m) return m.getDeclaringClass();
        return target.getClass();
    }
}

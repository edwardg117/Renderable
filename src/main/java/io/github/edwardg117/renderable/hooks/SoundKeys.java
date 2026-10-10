package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;

import java.util.HashMap;

/**
 * Sound plays' accumulators (spec addendum §P6.5), found without allocating once a key has been seen: by the sound
 * event's id, then by its category's ordinal. It names no Minecraft type, so it is tested without the game: the id is
 * any object with value equality whose {@code toString()} is {@code namespace:path}, as a {@code ResourceLocation}
 * is. Keyed by equality, not identity, because a mod may build a new id for every play. Render thread only.
 */
final class SoundKeys {
    private final String[] categories;
    private final HashMap<Object, Accumulator[]> byId = new HashMap<>(128);

    /** @param categories each category's name by ordinal, as {@code SoundSource.getName()} gives it */
    SoundKeys(String[] categories) {
        this.categories = categories.clone();
    }

    /**
     * The accumulator for a play of event {@code id} in the category with ordinal {@code category}, made on first
     * sight, or remade when it is an earlier session's. Null, with nothing made, when the instance named no event
     * ({@code id} null) or no known category ({@code Probe} passes -1 for a null {@code SoundSource}).
     */
    Accumulator get(Session s, Object id, int category) {
        if (id == null || category < 0 || category >= categories.length) return null;
        Accumulator[] byCategory = byId.get(id);
        if (byCategory == null) {
            byCategory = new Accumulator[categories.length];
            byId.put(id, byCategory);
        }
        Accumulator a = byCategory[category];
        if (a == null || !s.owns(a)) {
            a = s.accumulator(ModKey.sound(id.toString(), categories[category]));
            byCategory[category] = a;
        }
        return a;
    }

    /** Forgets every key. {@code Probe} calls it when a session is attached or detached. */
    void clear() {
        byId.clear();
    }
}

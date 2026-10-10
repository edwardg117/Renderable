package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Accumulator;
import io.github.edwardg117.renderable.core.ModKey;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sound plays' keys (spec addendum §P6.5), without the game: an {@link Id} stands in for a ResourceLocation. */
class SoundKeysTest {
    static final long MS = 1_000_000L;
    /** The first five SoundSource names, in ordinal order. */
    static final String[] CATEGORIES = {"master", "music", "record", "weather", "block"};

    /** Value equality, and toString() is the id, as a ResourceLocation's is. */
    record Id(String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    static Session session() {
        return new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> 0, 0);
    }

    @Test
    void aPlayIsKeyedByItsEventThenItsCategory() {
        Session s = session();
        SoundKeys keys = new SoundKeys(CATEGORIES);
        Accumulator steam = keys.get(s, new Id("create:steam"), 4);
        assertEquals(ModKey.sound("create:steam", "block"), steam.key);
        assertSame(steam, keys.get(s, new Id("create:steam"), 4),
                "an equal id that is another object finds the same row: a mod may build an id for every play");
        assertSame(steam, s.accumulator(ModKey.sound("create:steam", "block")), "the session's own accumulator");
        Accumulator master = keys.get(s, new Id("create:steam"), 0);
        assertNotSame(steam, master, "one event in two categories is two rows");
        assertEquals("sound create:steam (master)", master.key.label());

        Session next = session();
        assertTrue(next.owns(keys.get(next, new Id("create:steam"), 4)), "an earlier session's accumulator is replaced");
        keys.clear();
        assertSame(steam, keys.get(s, new Id("create:steam"), 4), "a cleared cache finds the session's accumulator again");
    }

    @Test
    void anInstanceThatNamesNoEventOrNoCategoryIsNotTimed() {
        // Review focus 5: a mod's own SoundInstance may return null from getLocation() or getSource(). The play is
        // not timed, and nothing here throws: vanilla's play may still cope, or a PlaySoundEvent handler replace it.
        Session s = session();
        SoundKeys keys = new SoundKeys(CATEGORIES);
        assertNull(keys.get(s, null, 4), "no event id");
        assertNull(keys.get(s, new Id("create:steam"), -1), "no category: Probe passes -1 for a null SoundSource");
        assertNull(keys.get(s, new Id("create:steam"), CATEGORIES.length), "an ordinal past the known categories");
        assertNotNull(keys.get(s, new Id("create:steam"), 4), "and the cache still works after them");
    }
}

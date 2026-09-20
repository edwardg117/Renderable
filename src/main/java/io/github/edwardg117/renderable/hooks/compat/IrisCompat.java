package io.github.edwardg117.renderable.hooks.compat;

import io.github.edwardg117.renderable.core.Environment;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.neoforged.fml.ModList;
import org.slf4j.LoggerFactory;

/**
 * Spec §4.2: Iris renders block entities, entities and particles a second time in its shadow pass. The
 * shadow flag is a static read on Iris's side, so a timed call can be tagged as shadow work for the
 * {@code shadowNanos} column. Iris classes are referenced only inside {@link Impl}, which is loaded on first
 * use and only when {@link #PRESENT}.
 *
 * <p>Spec addendum §P4.B4: {@link #shadowActive()} is called on the hot path before a timed call is opened,
 * so it must never throw. A {@code LinkageError} after an Iris version bump would otherwise leave the
 * attribution stack unbalanced for the rest of the frame. One failure disables the shadow column for the
 * rest of the client run, which is spec §4.5's policy: degrade to missing data, never crash.
 *
 * <p>Spec §P4.B5: {@link #shadowActive()} and {@link #packName()} latch independently, the same way
 * {@link FlywheelCompat}'s two lookups do. They read different Iris classes ({@code ShadowRenderingState}
 * versus {@code Iris}) that can rename or move on their own schedule, so a break in the decorative,
 * once-per-session pack name lookup must not switch off the hot-path shadow column that {@code shadowNanos}
 * depends on — and a shadow-state break, unrelated to how pack names are read, must not force the pack name
 * to read "unknown" either.
 */
public final class IrisCompat {
    public static final boolean PRESENT = ModList.get().isLoaded("iris");

    /** Set once Iris's shadow-state read stopped matching; the shadow column then stays off for this client run. */
    private static volatile boolean shadowBroken;

    /** Set once Iris's pack name read stopped matching; the header field then reads "unknown" for this client run. */
    private static volatile boolean packNameBroken;

    private IrisCompat() {
    }

    /** True while Iris is rendering its shadow pass; always false without Iris, or after a failure. */
    public static boolean shadowActive() {
        if (!PRESENT || shadowBroken) return false;
        try {
            return Impl.shadowActive();
        } catch (Throwable t) {
            shadowBroken = true;
            LoggerFactory.getLogger("Renderable")
                    .warn("Iris shadow-state read failed; the shadow column is disabled for this client run", t);
            return false;
        }
    }

    /** The active shader pack name, "none" when shaders are off, "absent" without Iris. */
    public static String packName() {
        if (!PRESENT) return Environment.ABSENT;
        if (packNameBroken) return "unknown";
        try {
            return Impl.packName();
        } catch (Throwable t) {
            packNameBroken = true;
            LoggerFactory.getLogger("Renderable")
                    .warn("Iris pack name lookup failed; disabled for this client run", t);
            return "unknown";
        }
    }

    private static final class Impl {
        static boolean shadowActive() {
            return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
        }

        static String packName() {
            if (!Iris.isPackInUseQuick()) return "none";
            String name = Iris.getCurrentPackName();
            return name == null || name.isEmpty() ? "unknown" : name;
        }
    }
}

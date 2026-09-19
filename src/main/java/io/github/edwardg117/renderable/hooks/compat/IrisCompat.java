package io.github.edwardg117.renderable.hooks.compat;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.neoforged.fml.ModList;

/**
 * Spec §4.2: Iris renders block entities, entities and particles a second time in its shadow pass. The
 * shadow flag is a static read on Iris's side, so a timed call can be tagged as shadow work for the
 * {@code shadowNanos} column. Iris classes are referenced only inside {@link Impl}, which is loaded on first
 * use and only when {@link #PRESENT}.
 */
public final class IrisCompat {
    public static final boolean PRESENT = ModList.get().isLoaded("iris");

    private IrisCompat() {
    }

    /** True while Iris is rendering its shadow pass; always false without Iris. One static call. */
    public static boolean shadowActive() {
        return PRESENT && Impl.shadowActive();
    }

    /** The active shader pack name, "none" when shaders are off, "absent" without Iris. */
    public static String packName() {
        return PRESENT ? Impl.packName() : "absent";
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

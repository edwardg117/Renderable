package io.github.edwardg117.renderable.hooks.compat;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.VisualManager;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Spec §4.2 and §4.4: Flywheel draws Create's machines through instancing, so they never reach the block
 * entity renderer. Renderable reports Flywheel as one bucket (see the Flywheel mixin) plus visual counts,
 * sampled here about once per second. Flywheel classes are referenced only inside {@link Impl}.
 */
public final class FlywheelCompat {
    public static final boolean PRESENT = ModList.get().isLoaded("flywheel");

    /** One sample of live visuals. {@code byClass} maps visual class name to its count in this sample. */
    public record Counts(int blockEntities, int entities, int effects, Map<String, Integer> byClass) {
    }

    private FlywheelCompat() {
    }

    /** Set once Flywheel's internals stopped matching; sampling then stays off for this client run. */
    private static volatile boolean broken;

    /**
     * Counts the level's visuals, or null when Flywheel is absent, not visualizing this level, or its internals
     * no longer match this build (spec §4.5: a version bump degrades to missing data, never a crash). Render thread only.
     */
    public static Counts sample(Level level) {
        if (!PRESENT || broken) return null;
        try {
            return Impl.sample(level);
        } catch (Throwable t) {
            broken = true;
            LoggerFactory.getLogger("Renderable").warn("Flywheel visual sampling failed; disabled for this client run", t);
            return null;
        }
    }

    /** The active backend id such as {@code flywheel:instancing}, or "absent" without Flywheel. */
    public static String backendName() {
        return PRESENT ? Impl.backendName() : "absent";
    }

    private static final class Impl {
        static Counts sample(Level level) {
            VisualizationManagerImpl manager = VisualizationManagerImpl.get(level);
            if (manager == null) return null;
            Map<String, Integer> byClass = new HashMap<>();
            countByClass(manager.blockEntities(), byClass);
            countByClass(manager.entities(), byClass);
            countByClass(manager.effects(), byClass);
            return new Counts(manager.blockEntities().visualCount(), manager.entities().visualCount(),
                    manager.effects().visualCount(), byClass);
        }

        private static void countByClass(VisualManager<?> manager, Map<String, Integer> byClass) {
            if (manager instanceof VisualManagerImpl<?, ?> impl) {
                for (Visual visual : impl.getStorage().getAllVisuals()) {
                    byClass.merge(visual.getClass().getName(), 1, Integer::sum);
                }
            }
        }

        static String backendName() {
            Backend backend = BackendManager.currentBackend();
            ResourceLocation id = Backend.REGISTRY.getId(backend);
            return id == null ? String.valueOf(backend) : id.toString();
        }
    }
}

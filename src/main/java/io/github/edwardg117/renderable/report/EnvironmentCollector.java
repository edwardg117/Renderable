package io.github.edwardg117.renderable.report;

import com.mojang.blaze3d.platform.GlStateManager;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.GcNames;
import io.github.edwardg117.renderable.core.JvmFlags;
import io.github.edwardg117.renderable.hooks.GcWatcher;
import io.github.edwardg117.renderable.hooks.HookInstalls;
import io.github.edwardg117.renderable.hooks.Probe;
import io.github.edwardg117.renderable.hooks.compat.FlywheelCompat;
import io.github.edwardg117.renderable.hooks.compat.IrisCompat;
import io.github.edwardg117.renderable.hooks.events.EventTiming;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;
import org.lwjgl.opengl.GL11;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.function.Supplier;

/** Reads the client's environment for the report header. Must run on the render thread (reads OpenGL strings). */
public final class EnvironmentCollector {

    private EnvironmentCollector() {
    }

    public static Environment collect() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        LinkedHashMap<String, String> m = new LinkedHashMap<>();

        m.put("minecraft", SharedConstants.getCurrentVersion().getName());
        m.put("neoforge", NeoForgeVersion.getVersion());
        m.put("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        m.put("maxHeapMiB", Long.toString(Runtime.getRuntime().maxMemory() >> 20));
        m.put(Environment.INITIAL_HEAP_MIB, safe(() -> mib(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getInit())));
        m.put(Environment.PHYSICAL_MEMORY_MIB, safe(() -> mib(((com.sun.management.OperatingSystemMXBean)
                ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize())));
        m.put(Environment.GC, safe(() -> GcNames.collector(ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName).toList())));
        long live = GcWatcher.liveHeapAfterGcMiB();
        if (live >= 0) m.put(Environment.LIVE_HEAP_AFTER_GC_MIB, Long.toString(live));
        m.put(Environment.JVM_FLAGS, safe(() -> String.join(" ",
                JvmFlags.kept(ManagementFactory.getRuntimeMXBean().getInputArguments()))));
        m.put("mods", Integer.toString(ModList.get().size()));
        m.put("gpu", safe(() -> GlStateManager._getString(GL11.GL_RENDERER)));
        m.put("glVersion", safe(() -> GlStateManager._getString(GL11.GL_VERSION)));

        m.put("renderDistance", String.valueOf(o.renderDistance().get()));
        m.put("simulationDistance", String.valueOf(o.simulationDistance().get()));
        m.put("biomeBlend", String.valueOf(o.biomeBlendRadius().get()));
        m.put("graphics", String.valueOf(o.graphicsMode().get()));
        m.put("vsync", String.valueOf(o.enableVsync().get()));
        m.put("fpsLimit", String.valueOf(o.framerateLimit().get()));
        m.put("particles", String.valueOf(o.particles().get()));
        m.put("entityDistance", String.valueOf(o.entityDistanceScaling().get()));
        m.put("guiScale", String.valueOf(o.guiScale().get()));
        m.put("window", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
                + (mc.getWindow().isFullscreen() ? " fullscreen" : " windowed"));

        m.put("sodium", modVersion("sodium"));
        m.put("iris", modVersion("iris"));
        m.put("shaderPack", safe(IrisCompat::packName));
        m.put("flywheel", modVersion("flywheel"));
        m.put("flywheelBackend", safe(FlywheelCompat::backendName));
        m.put("acceleratedrendering", modVersion("acceleratedrendering"));
        m.put("distanthorizons", modVersion("distanthorizons"));
        m.put(Environment.EVENT_TIMING, EventTiming.status());

        if (mc.level != null) {
            m.put("dimension", mc.level.dimension().location().toString());
            m.put("sections", safe(() -> mc.levelRenderer.getSectionStatistics()));
            m.put("entities", safe(() -> mc.levelRenderer.getEntityStatistics()));
        }
        if (mc.player != null) {
            BlockPos p = mc.player.blockPosition();
            m.put("position", p.getX() + " " + p.getY() + " " + p.getZ());
        }
        return Environment.of(m, Probe.hooksFired(), HookInstalls.installed(), HookInstalls.failed());
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse(Environment.ABSENT);
    }

    /** Bytes as MiB, or "unknown" for the -1 an MXBean reports when it does not know. */
    private static String mib(long bytes) {
        return bytes < 0 ? "unknown" : Long.toString(bytes >> 20);
    }

    private static String safe(Supplier<String> s) {
        try {
            String v = s.get();
            return v == null ? "unknown" : v;
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }
}

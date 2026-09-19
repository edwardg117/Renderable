package io.github.edwardg117.renderable;

import io.github.edwardg117.renderable.hooks.FrameEvents;
import io.github.edwardg117.renderable.hooks.Probe;
import io.github.edwardg117.renderable.ui.Keybinds;
import io.github.edwardg117.renderable.ui.RenderableCommand;
import io.github.edwardg117.renderable.ui.SamplingHud;
import io.github.edwardg117.renderable.ui.WorldOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = RenderableMod.MOD_ID, dist = Dist.CLIENT)
public final class RenderableMod {
    public static final String MOD_ID = "renderable";
    public static final Logger LOGGER = LoggerFactory.getLogger("Renderable");
    /** Set from the mod container in the constructor; "dev" until then. */
    public static String VERSION = "dev";

    public RenderableMod(IEventBus modBus, ModContainer container) {
        VERSION = container.getModInfo().getVersion().toString();
        container.registerConfig(ModConfig.Type.CLIENT, RenderableConfig.SPEC);

        modBus.addListener(Keybinds::register);
        modBus.addListener(SamplingHud::register);
        modBus.addListener(WorldOverlay::register);

        NeoForge.EVENT_BUS.register(FrameEvents.class);
        NeoForge.EVENT_BUS.register(Keybinds.class);
        NeoForge.EVENT_BUS.register(WorldOverlay.class);
        NeoForge.EVENT_BUS.addListener(RenderableCommand::register);

        Probe.setListener(ProfilerController.get());
        LOGGER.info("Renderable {} loaded", VERSION);
    }
}

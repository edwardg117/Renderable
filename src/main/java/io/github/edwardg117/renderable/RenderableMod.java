package io.github.edwardg117.renderable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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
        LOGGER.info("Renderable {} loaded", VERSION);
    }
}

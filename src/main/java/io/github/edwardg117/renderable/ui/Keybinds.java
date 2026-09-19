package io.github.edwardg117.renderable.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.edwardg117.renderable.ProfilerController;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** F7 starts or stops a session; the overlay key is unbound by default (spec §5.1). */
public final class Keybinds {
    public static final KeyMapping TOGGLE = new KeyMapping("key.renderable.toggle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.renderable");
    public static final KeyMapping OVERLAY = new KeyMapping("key.renderable.overlay", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.renderable");

    private Keybinds() {
    }

    /** Mod bus. */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(OVERLAY);
    }

    /** Game bus. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE.consumeClick()) {
            ProfilerController.get().toggle();
        }
        while (OVERLAY.consumeClick()) {
            onOverlayKey();
        }
    }

    private static void onOverlayKey() {
        WorldOverlay.toggle();
        ProfilerController.chat("Renderable: overlay " + (WorldOverlay.isEnabled() ? "on" : "off"));
    }
}

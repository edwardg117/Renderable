package io.github.edwardg117.renderable.ui;

import io.github.edwardg117.renderable.ProfilerController;
import io.github.edwardg117.renderable.core.HitchDetector;
import io.github.edwardg117.renderable.core.Session;
import io.github.edwardg117.renderable.core.SessionConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.Locale;

/** One line at the top centre of the screen while a session is sampling (spec §5.2). */
public final class SamplingHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("renderable", "sampling");
    private static final int ROLLING_WINDOW = 120;

    private SamplingHud() {
    }

    /** Mod bus. */
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, SamplingHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Session s = ProfilerController.get().session();
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        SessionConfig c = s.config();
        int hitches = HitchDetector.countRolling(s.framesView(), ROLLING_WINDOW, c.hitchMedianMultiplier(), c.hitchMinExcessNanos());
        String text = String.format(Locale.ROOT, "Renderable: %.1f / %.0f s | %d frames | %d hitches",
                s.elapsedNanos() / 1e9, c.durationSeconds(), s.frameCount(), hitches);
        int w = mc.font.width(text);
        int x = (g.guiWidth() - w) / 2;
        int y = 4;
        g.fill(x - 3, y - 2, x + w + 3, y + mc.font.lineHeight + 1, 0x90000000);
        g.drawString(mc.font, text, x, y, 0xFFFFFF, true);
    }
}

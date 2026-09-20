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

/**
 * One line at the top centre of the screen while a session is sampling (spec §5.2).
 *
 * <p>The rolling hitch count walks every frame recorded so far, which is thousands by the end of a session,
 * so it is recomputed at most four times a second and cached in between (spec addendum §P4.B3). The whole
 * layer is timed into the session's self time, because it runs inside the frame it is measuring.
 */
public final class SamplingHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath("renderable", "sampling");
    private static final int ROLLING_WINDOW = 120;
    private static final long REFRESH_NANOS = 250_000_000L;

    // A session id, not a Session reference: holding the object itself would keep a finished session's whole
    // frame list reachable from a static field if the layer stopped rendering right after it ended (spec
    // addendum §P4.B3), the same shape of leak Task 2 removed from Probe. Session ids start above zero, so -1
    // means "nothing counted yet".
    private static long countedForId = -1;
    private static long countedAtNanos;
    private static int cachedHitches;

    private SamplingHud() {
    }

    /** Mod bus. */
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, SamplingHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Session s = ProfilerController.get().session();
        if (s == null) {
            countedForId = -1;
            return;
        }
        s.beginSelf();
        try {
            Minecraft mc = Minecraft.getInstance();
            SessionConfig c = s.config();
            String text = String.format(Locale.ROOT, "Renderable: %.1f / %.0f s | %d frames | %d hitches (last %d frames)",
                    s.elapsedNanos() / 1e9, c.durationSeconds(), s.frameCount(), hitches(s, c), ROLLING_WINDOW);
            int w = mc.font.width(text);
            int x = (g.guiWidth() - w) / 2;
            int y = 4;
            g.fill(x - 3, y - 2, x + w + 3, y + mc.font.lineHeight + 1, 0x90000000);
            g.drawString(mc.font, text, x, y, 0xFFFFFF, true);
        } finally {
            s.endSelf();
        }
    }

    /** The cached rolling count, recomputed when the session changed or the refresh interval has passed. */
    private static int hitches(Session s, SessionConfig c) {
        long now = System.nanoTime();
        if (s.id() != countedForId || now - countedAtNanos >= REFRESH_NANOS) {
            countedForId = s.id();
            countedAtNanos = now;
            cachedHitches = HitchDetector.countRolling(s.framesView(), ROLLING_WINDOW,
                    c.hitchMedianMultiplier(), c.hitchMinExcessNanos());
        }
        return cachedHitches;
    }
}

package io.github.edwardg117.renderable.core;

/**
 * Whether the world overlay draws its labels (spec addendum §P6.13). The {@code overlayEnabled} setting is the only
 * state: the overlay reads it every time, and the command and the overlay key write it, so the mod's config screen
 * takes effect at once and {@code /renderable overlay off} lasts until the labels are turned back on. Until the
 * config has loaded, labels count as on, the setting's default, and nothing is written.
 */
public final class OverlaySwitch {

    /** The {@code overlayEnabled} setting, behind an interface so {@code core} stays free of NeoForge. */
    public interface Setting {
        /** False until the config file has loaded; reading or writing before then throws in NeoForge. */
        boolean loaded();

        boolean get();

        /** Sets and saves the value. */
        void set(boolean value);
    }

    private final Setting setting;

    public OverlaySwitch(Setting setting) {
        this.setting = setting;
    }

    /** Whether labels are drawn: the setting as it is now, or on before the config has loaded. */
    public boolean enabled() {
        return !setting.loaded() || setting.get();
    }

    /** Turns labels on or off by writing the setting. Returns false, writing nothing, before the config has loaded. */
    public boolean set(boolean on) {
        if (!setting.loaded()) return false;
        setting.set(on);
        return true;
    }

    /** Flips the setting, and returns whether labels are now on. */
    public boolean toggle() {
        set(!enabled());
        return enabled();
    }
}

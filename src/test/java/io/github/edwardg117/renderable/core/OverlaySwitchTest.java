package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OverlaySwitchTest {

    /** Stands in for the {@code overlayEnabled} config value. */
    private static final class FakeSetting implements OverlaySwitch.Setting {
        boolean loaded;
        boolean value;
        int writes;

        FakeSetting(boolean loaded, boolean value) {
            this.loaded = loaded;
            this.value = value;
        }

        @Override
        public boolean loaded() {
            return loaded;
        }

        @Override
        public boolean get() {
            if (!loaded) throw new IllegalStateException("config not loaded"); // as NeoForge does
            return value;
        }

        @Override
        public void set(boolean value) {
            if (!loaded) throw new IllegalStateException("config not loaded");
            this.value = value;
            writes++;
        }
    }

    @Test
    void readsTheSettingEveryTimeSoAChangeElsewhereShowsAtOnce() {
        // The 0.5.0 bug: the overlay copied the setting on first use, so a change in the config screen did nothing.
        FakeSetting setting = new FakeSetting(true, true);
        OverlaySwitch overlay = new OverlaySwitch(setting);
        assertTrue(overlay.enabled());

        setting.value = false; // the config screen writes the value directly
        assertFalse(overlay.enabled());

        setting.value = true;
        assertTrue(overlay.enabled());
        assertEquals(0, setting.writes, "reading never writes");
    }

    @Test
    void setWritesTheSetting() {
        FakeSetting setting = new FakeSetting(true, true);
        OverlaySwitch overlay = new OverlaySwitch(setting);

        assertTrue(overlay.set(false));
        assertFalse(setting.value);
        assertEquals(1, setting.writes);
        assertFalse(overlay.enabled());

        assertTrue(overlay.set(true));
        assertTrue(setting.value);
        assertEquals(2, setting.writes);
        assertTrue(overlay.enabled());
    }

    @Test
    void toggleFlipsWhatTheSettingSays() {
        FakeSetting setting = new FakeSetting(true, true);
        OverlaySwitch overlay = new OverlaySwitch(setting);

        assertFalse(overlay.toggle());
        assertFalse(setting.value);

        assertTrue(overlay.toggle());
        assertTrue(setting.value);
    }

    @Test
    void beforeTheConfigLoadsLabelsCountAsOnAndNothingIsWritten() {
        // NeoForge throws on reading or writing a setting before its file has loaded.
        FakeSetting setting = new FakeSetting(false, false);
        OverlaySwitch overlay = new OverlaySwitch(setting);

        assertTrue(overlay.enabled(), "the setting's default, and the setting is not read");
        assertFalse(overlay.set(false));
        assertTrue(overlay.toggle(), "still on: nothing could be written");
        assertEquals(0, setting.writes);
        assertFalse(setting.value, "untouched");
    }
}

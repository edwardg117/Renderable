package io.github.edwardg117.renderable.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmptyStatesTest {

    static Environment env(Map<String, String> entries, String... hooks) {
        return Environment.of(new LinkedHashMap<>(entries), List.of(hooks));
    }

    @Test
    void meshAndParticleStatesTellAbsentFromEmpty() {
        Environment none = env(Map.of());
        assertEquals("(no data: Sodium hooks were not active)", EmptyStates.blocks(none));
        assertEquals("(no data: Sodium hooks were not active)", EmptyStates.sections(none));
        assertEquals("(no data: hook not active in this build)", EmptyStates.particles(none));

        Environment sodiumOnly = env(Map.of(), "SODIUM_MESH");
        assertEquals("(no data: no blocks were meshed)", EmptyStates.blocks(sodiumOnly));
        assertEquals("(no data: no sections were rebuilt)", EmptyStates.sections(sodiumOnly));
        assertEquals("(no data: hook not active in this build)", EmptyStates.particles(sodiumOnly));

        Environment particlesOnly = env(Map.of(), "PARTICLE_RENDER");
        assertEquals("(no particles were rendered)", EmptyStates.particles(particlesOnly));
        assertEquals("(no data: Sodium hooks were not active)", EmptyStates.blocks(particlesOnly));
        assertEquals("(no data: Sodium hooks were not active)", EmptyStates.sections(particlesOnly));

        assertEquals("(no data: hook not active in this build)", EmptyStates.ranked());
    }

    @Test
    void sodiumsUploadHookFiresEveryFrameSoItAloneShowsSodiumIsHooked() {
        // A session in which nothing is rebuilt fires Sodium's upload hook on every frame but never its mesh hook.
        // Its empty tables mean nothing was rebuilt, not that Sodium is not hooked.
        Environment uploadOnly = env(Map.of(), "SODIUM_UPLOAD");
        assertEquals("(no data: no blocks were meshed)", EmptyStates.blocks(uploadOnly));
        assertEquals("(no data: no sections were rebuilt)", EmptyStates.sections(uploadOnly));
    }

    @Test
    void flywheelStateNamesTheBackendItFoundOrSaysTheModIsAbsent() {
        assertEquals("(no data: Flywheel is not installed)", EmptyStates.flywheel(env(Map.of()), 0));
        assertEquals("(no data: Flywheel is not installed)",
                EmptyStates.flywheel(env(Map.of("flywheelBackend", Environment.ABSENT)), 3));
        assertEquals("(no data: no Flywheel visualization ran; backend flywheel:off)",
                EmptyStates.flywheel(env(Map.of("flywheelBackend", "flywheel:off")), 0));
        assertEquals("", EmptyStates.flywheel(env(Map.of("flywheelBackend", "flywheel:indirect")), 3),
                "an empty string means there is data to print");
    }

    @Test
    void modsStatesTellUnavailableFromNothing() {
        assertEquals("(no mod handlers, HUD layers or packets were timed)", EmptyStates.mods(env(Map.of())));
        assertEquals("", EmptyStates.modsHandlersMissing(env(Map.of(Environment.EVENT_TIMING, "on"))));
        Environment off = env(Map.of(Environment.EVENT_TIMING, Environment.UNAVAILABLE + "NoSuchFieldException: listenerLists"));
        assertEquals("(event timing unavailable: NoSuchFieldException: listenerLists)", EmptyStates.mods(off));
        assertEquals("(event handlers were not timed: NoSuchFieldException: listenerLists)",
                EmptyStates.modsHandlersMissing(off));
    }
}

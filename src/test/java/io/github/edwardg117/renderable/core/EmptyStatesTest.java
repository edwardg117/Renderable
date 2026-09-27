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

    /** An environment as the plugin reports it: hooks installed and hooks that did not apply, none fired. */
    static Environment checked(Map<String, String> entries, List<String> installed, List<String> failed) {
        return Environment.of(new LinkedHashMap<>(entries), List.of(), installed, failed);
    }

    @Test
    void meshStatesTellAbsentFailedUnloadedAndEmptyApart() {
        Map<String, String> sodium = Map.of("sodium", "0.8.13");
        assertEquals("(no data: Sodium is not installed)", EmptyStates.blocks(env(Map.of())));
        assertEquals("(no data: Sodium's mesh hook did not apply)",
                EmptyStates.blocks(checked(sodium, List.of(), List.of("SODIUM_MESH"))));
        assertEquals("(no data: Sodium's mesh hook never loaded; the game log says why)", EmptyStates.sections(env(sodium)));
        assertEquals("(no data: no blocks were meshed)", EmptyStates.blocks(checked(sodium, List.of("SODIUM_MESH"), List.of())));
        assertEquals("(no data: no sections were rebuilt)", EmptyStates.sections(env(sodium, "SODIUM_MESH")),
                "a hook that fired is in place too");
    }

    @Test
    void rankedAndParticleStatesBlameTheHookOnlyWhenItDidNotApply() {
        assertEquals("(nothing of this kind was drawn or ticked)", EmptyStates.blockEntities(env(Map.of())));
        assertEquals("(hook not installed)", EmptyStates.entities(checked(Map.of(), List.of(), List.of("RIDER_TICK"))));
        assertEquals("(no particles were rendered)", EmptyStates.particles(env(Map.of())));
        assertEquals("(hook not installed)",
                EmptyStates.particles(checked(Map.of(), List.of("PARTICLE_RENDER"), List.of("PARTICLE_TICK"))));
        Environment others = checked(Map.of(), List.of(), List.of("ENTITY_RENDER", "RIDER_TICK", "SODIUM_MESH"));
        assertEquals("(nothing of this kind was drawn or ticked)", EmptyStates.blockEntities(others), "not its hooks");
        assertEquals("(no particles were rendered)", EmptyStates.particles(others), "not its hooks");
        assertEquals("(hook not installed)",
                EmptyStates.blockEntities(checked(Map.of(), List.of(), List.of("BLOCK_ENTITY_TICK"))));
        assertEquals("(hook not installed)",
                EmptyStates.particles(checked(Map.of(), List.of(), List.of("PARTICLE_RENDER"))));
        assertEquals("(Flywheel sampled 3 times and found no visuals alive)", EmptyStates.flywheelNoVisuals(3));
        assertEquals("(Flywheel sampled once and found no visuals alive)", EmptyStates.flywheelNoVisuals(1));
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

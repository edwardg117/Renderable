package io.github.edwardg117.renderable.hooks;

import io.github.edwardg117.renderable.core.Hook;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * Which hooks are in place (spec addendum §P5.D1), as {@link RenderableMixinPlugin} finds them when each hooked
 * class loads. A hook is installed when every checked injector of it found its target, and did not apply when one
 * did not. A hook whose classes never loaded in this client run is in neither list, and neither is a hook of a
 * mixin that Mixin rejected as a whole, which never reaches {@code postApply}, or of one whose check threw; the game
 * log names both. Which hooks fired is {@link Probe#hooksFired()}'s business. Any thread: a class loads wherever the
 * game first uses it.
 */
public final class HookInstalls {

    /**
     * Every injector's hook, by mixin class simple name and handler name. {@code HookInstallsTest} reads every mixin
     * and fails when an injector is missing here, so a new injector cannot go unchecked.
     */
    static final Map<String, Hook> HOOKS = Map.ofEntries(
            entry("BlockEntityRenderDispatcherMixin.renderable$timeRender", Hook.BLOCK_ENTITY_RENDER),
            entry("EntityRenderDispatcherMixin.renderable$timeRender", Hook.ENTITY_RENDER),
            entry("ParticleEngineMixin.renderable$timeParticleRender", Hook.PARTICLE_RENDER),
            entry("ParticleEngineMixin.renderable$timeParticleTick", Hook.PARTICLE_TICK),
            entry("BoundTickingBlockEntityMixin.renderable$timeTick", Hook.BLOCK_ENTITY_TICK),
            entry("LevelMixin.renderable$timeEntityTick", Hook.ENTITY_TICK),
            entry("LevelLightEngineMixin.renderable$timeLightUpdates", Hook.LIGHT),
            entry("BufferBuilderMixin.renderable$countVertex", Hook.VERTICES),
            entry("MinecraftMixin.renderable$timeTasks", Hook.TASKS),
            entry("MinecraftMixin.renderable$timeFrameCap", Hook.FRAME_CAP),
            entry("GuiMixin.renderable$timeHud", Hook.HUD),
            entry("GuiLayerManagerMixin.renderable$timeLayer", Hook.HUD_LAYERS),
            entry("PacketUtilsMixin.renderable$timePacket", Hook.PACKETS),
            entry("MainThreadPayloadHandlerMixin.renderable$timePayload", Hook.PAYLOADS),
            entry("ClientLevelMixin.renderable$timeRiderTick", Hook.RIDER_TICK),
            entry("FlywheelRenderDispatcherMixin.renderable$timeStartLevelRender", Hook.FLYWHEEL),
            entry("FlywheelRenderDispatcherMixin.renderable$timeAfterEntities", Hook.FLYWHEEL),
            entry("FlywheelRenderDispatcherMixin.renderable$timeCrumbling", Hook.FLYWHEEL),
            entry("ChunkBuilderMeshingTaskMixin.renderable$timeSection", Hook.SODIUM_MESH),
            entry("ChunkBuilderMeshingTaskMixin.renderable$timeBlock", Hook.SODIUM_MESH),
            entry("ChunkBuilderMeshingTaskMixin.renderable$timeFluid", Hook.SODIUM_MESH),
            entry("RenderSectionManagerMixin.renderable$timeUpload", Hook.SODIUM_UPLOAD),
            entry("RenderSectionManagerMixin.renderable$timeUpdate", Hook.SODIUM_UPLOAD),
            entry("SodiumBufferBuilderMixin.renderable$countPushedVertices", Hook.VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$countSlowVertex", Hook.ACCELERATED_VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$countFastVertex", Hook.ACCELERATED_VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$countClientMesh", Hook.ACCELERATED_VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$serverMeshBefore", Hook.ACCELERATED_VERTICES),
            entry("AcceleratedBufferBuilderMixin.renderable$serverMeshAfter", Hook.ACCELERATED_VERTICES));

    /** Injectors that are not hooks of their own: the frame and tick boundaries, which every session needs. */
    static final Set<String> BOUNDARIES = Set.of(
            "MinecraftMixin.renderable$frameStart", "MinecraftMixin.renderable$renderEnd",
            "MinecraftMixin.renderable$tickStart", "MinecraftMixin.renderable$tickEnd");

    private static final boolean[] PASSED = new boolean[Hook.values().length];
    private static final boolean[] FAILED = new boolean[Hook.values().length];

    private HookInstalls() {
    }

    /** Checks each injector of {@code mixin} against {@code target}, the class it was just applied to. */
    static void check(String mixinClassName, ClassNode mixin, ClassNode target) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        for (InjectionCheck.Injector injector : InjectionCheck.injectors(mixin)) {
            Hook hook = HOOKS.get(simple + "." + injector.handler());
            if (hook != null) record(hook, InjectionCheck.present(injector, target));
        }
    }

    private static synchronized void record(Hook hook, boolean present) {
        (present ? PASSED : FAILED)[hook.ordinal()] = true;
    }

    /** Hooks every checked injector of which found its target, in enum order. */
    public static synchronized List<String> installed() {
        List<String> out = new ArrayList<>();
        for (Hook h : Hook.values()) {
            if (PASSED[h.ordinal()] && !FAILED[h.ordinal()]) out.add(h.name());
        }
        return out;
    }

    /** Hooks with an injector whose target was missing, in enum order: they did not apply, wholly or in part. */
    public static synchronized List<String> failed() {
        List<String> out = new ArrayList<>();
        for (Hook h : Hook.values()) {
            if (FAILED[h.ordinal()]) out.add(h.name());
        }
        return out;
    }
}

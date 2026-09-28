package io.github.edwardg117.renderable.hooks;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InjectionCheckTest {
    static final String PARTICLE_ENGINE_MIXIN = "io.github.edwardg117.renderable.hooks.mixin.ParticleEngineMixin";
    static final String ACCELERATED_MIXIN =
            "io.github.edwardg117.renderable.hooks.mixin.acceleratedrendering.AcceleratedBufferBuilderMixin";
    static final String GUI_LAYER_MANAGER_MIXIN = "io.github.edwardg117.renderable.hooks.mixin.GuiLayerManagerMixin";
    static final String ENGINE = "net/minecraft/client/particle/ParticleEngine";
    static final String PARTICLE = "net/minecraft/client/particle/Particle";
    /** NeoForge's ParticleEngine.render, with the frustum and predicate it adds. */
    static final String RENDER_DESC = "(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;"
            + "FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V";
    static final String PARTICLE_RENDER_DESC = "(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V";

    static ClassNode target(String name, MethodNode... methods) {
        ClassNode c = new ClassNode();
        c.name = name;
        c.methods.addAll(List.of(methods));
        return c;
    }

    /** A method that makes the given calls, each a fresh node: an ASM node belongs to one instruction list. */
    static MethodNode method(String name, String desc, MethodInsnNode... calls) {
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, name, desc, null, null);
        for (MethodInsnNode call : calls) m.instructions.add(call);
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        return m;
    }

    static MethodInsnNode call(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc, false);
    }

    /** The injector of one of our real mixins with this handler, whatever its place in the class. */
    static InjectionCheck.Injector injector(String mixin, String handler) throws IOException {
        return InjectionCheck.injectors(HookInstallsTest.classNode(mixin)).stream()
                .filter(i -> i.handler().equals(handler)).findFirst().orElseThrow(() -> new AssertionError(handler));
    }

    @Test
    void theReaderFindsEachInjectorsMethodsAndCalls() throws IOException {
        assertEquals(List.of(
                        new InjectionCheck.Injector("renderable$timeParticleRender", List.of("render" + RENDER_DESC),
                                List.of("L" + PARTICLE + ";render" + PARTICLE_RENDER_DESC)),
                        new InjectionCheck.Injector("renderable$timeParticleTick", List.of("tickParticle"),
                                List.of("L" + PARTICLE + ";tick()V"))),
                InjectionCheck.injectors(HookInstallsTest.classNode(PARTICLE_ENGINE_MIXIN)));

        // ModifyVariable's single @At, and HEAD and RETURN points, which name no call.
        assertEquals(new InjectionCheck.Injector("renderable$countFastVertex", List.of("addVertex(FFFIFFIIFFF)V"), List.of()),
                injector(ACCELERATED_MIXIN, "renderable$countFastVertex"));
        assertTrue(InjectionCheck.injectors(HookInstallsTest.classNode(ACCELERATED_MIXIN)).stream()
                .noneMatch(i -> i.handler().equals("getTotalVertexCount")), "the shadowed getter is not an injector");
    }

    @Test
    void anInvokeInjectorNeedsItsMethodAndItsCall() throws IOException {
        InjectionCheck.Injector tick = injector(PARTICLE_ENGINE_MIXIN, "renderable$timeParticleTick");
        String desc = "(Lnet/minecraft/client/particle/Particle;)V";
        assertTrue(InjectionCheck.present(tick, target(ENGINE, method("tickParticle", desc, call(PARTICLE, "tick", "()V")))));
        assertFalse(InjectionCheck.present(tick, target(ENGINE, method("tickParticle", desc, call(PARTICLE, "update", "()V")))),
                "the call was renamed");
        assertFalse(InjectionCheck.present(tick, target(ENGINE, method("tickParticles", desc, call(PARTICLE, "tick", "()V")))),
                "the method was renamed");
    }

    @Test
    void aSelectorWithADescriptorMatchesOnlyThatOverloadAndHeadNeedsOnlyTheMethod() throws IOException {
        InjectionCheck.Injector render = injector(PARTICLE_ENGINE_MIXIN, "renderable$timeParticleRender");
        assertTrue(InjectionCheck.present(render,
                target(ENGINE, method("render", RENDER_DESC, call(PARTICLE, "render", PARTICLE_RENDER_DESC)))));
        assertFalse(InjectionCheck.present(render,
                target(ENGINE, method("render", "(F)V", call(PARTICLE, "render", PARTICLE_RENDER_DESC)))),
                "an overload with another descriptor is not the target");

        InjectionCheck.Injector head = injector(ACCELERATED_MIXIN, "renderable$countFastVertex");
        String builder = "com/github/argon4w/acceleratedrendering/core/buffers/accelerated/builders/AcceleratedBufferBuilder";
        assertTrue(InjectionCheck.present(head, target(builder, method("addVertex", "(FFFIFFIIFFF)V"))));
        assertFalse(InjectionCheck.present(head,
                target(builder, method("addVertex", "(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))));
    }

    @Test
    void anInjectorThatMixinExtrasSugarHasWrappedIsStillRead() throws IOException {
        // The mixin as MixinExtras' preApply leaves a handler with a @Local parameter by the time the plugin's
        // postApply sees it: its injector annotation moved into @SugarWrapper(original = ...). The descriptor is
        // spelled out, not taken from InjectionCheck, so that a wrong one there fails here.
        ClassNode plain = HookInstallsTest.classNode(GUI_LAYER_MANAGER_MIXIN);
        ClassNode wrapped = HookInstallsTest.classNode(GUI_LAYER_MANAGER_MIXIN);
        int wraps = 0;
        for (MethodNode m : wrapped.methods) {
            if (m.visibleAnnotations == null) continue;
            for (int k = 0; k < m.visibleAnnotations.size(); k++) {
                AnnotationNode a = m.visibleAnnotations.get(k);
                if (!InjectionCheck.INJECTORS.contains(a.desc)) continue;
                AnnotationNode wrapper = new AnnotationNode("Lcom/llamalad7/mixinextras/sugar/impl/SugarWrapper;");
                wrapper.visit("original", a);
                wrapper.visit("signature", "");
                wrapper.visit("sugars", List.of());
                m.visibleAnnotations.set(k, wrapper);
                wraps++;
            }
        }
        assertEquals(1, wraps, "the mixin has one injector");
        assertEquals(InjectionCheck.injectors(plain), InjectionCheck.injectors(wrapped));
    }
}

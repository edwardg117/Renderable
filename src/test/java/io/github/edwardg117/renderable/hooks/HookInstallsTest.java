package io.github.edwardg117.renderable.hooks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.edwardg117.renderable.core.Hook;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.edwardg117.renderable.hooks.InjectionCheckTest.*;
import static org.junit.jupiter.api.Assertions.*;

class HookInstallsTest {

    /** A class read as bytes: a mixin class must never be loaded outside Mixin, and there is no Minecraft here. */
    static ClassNode classNode(String className) throws IOException {
        try (InputStream in = HookInstallsTest.class.getResourceAsStream("/" + className.replace('.', '/') + ".class")) {
            assertNotNull(in, className + " is not on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    /** Every mixin both configs list, by its full class name. */
    static List<String> mixins() throws IOException {
        List<String> out = new ArrayList<>();
        for (String config : List.of("renderable.mixins.json", "renderable.thirdparty.mixins.json")) {
            try (InputStream in = HookInstallsTest.class.getResourceAsStream("/" + config)) {
                assertNotNull(in, config);
                JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                String pkg = json.get("package").getAsString();
                for (JsonElement e : json.getAsJsonArray("client")) out.add(pkg + "." + e.getAsString());
            }
        }
        return out;
    }

    @Test
    void everyInjectorIsReadAndMappedAndEveryEntryIsAnInjector() throws IOException {
        Set<String> seen = new HashSet<>();
        Set<String> simpleNames = new HashSet<>();
        for (String mixin : mixins()) {
            String simple = mixin.substring(mixin.lastIndexOf('.') + 1);
            assertTrue(simpleNames.add(simple),
                    mixin + " shares its simple name with another mixin, and HOOKS keys by it");
            ClassNode node = classNode(mixin);
            // An injector of a kind InjectionCheck does not read would go unchecked, so every annotation from an
            // injector package must be one it reads. Group and Surrogate live there but are not injectors.
            for (MethodNode m : node.methods) {
                List<AnnotationNode> annotations = new ArrayList<>();
                if (m.visibleAnnotations != null) annotations.addAll(m.visibleAnnotations);
                if (m.invisibleAnnotations != null) annotations.addAll(m.invisibleAnnotations);
                for (AnnotationNode a : annotations) {
                    boolean injectorPackage = a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                            || a.desc.startsWith("Lcom/llamalad7/mixinextras/injector/");
                    boolean notAnInjector = a.desc.endsWith("/Group;") || a.desc.endsWith("/Surrogate;");
                    assertTrue(!injectorPackage || notAnInjector || InjectionCheck.INJECTORS.contains(a.desc),
                            simple + "." + m.name + " uses " + a.desc + ", which InjectionCheck does not read: add it there");
                }
            }
            for (InjectionCheck.Injector in : InjectionCheck.injectors(node)) {
                String id = simple + "." + in.handler();
                assertTrue(HookInstalls.HOOKS.containsKey(id) || HookInstalls.BOUNDARIES.contains(id),
                        id + " is neither a hook nor a boundary: add it to HookInstalls.HOOKS");
                // InjectionCheck matches a bare name or a name with a descriptor, and a call spelled Lowner;name(desc).
                // Mixin accepts other spellings too, which the check would report as not applied: spell a new one so.
                assertFalse(in.methods().isEmpty(), id + " names no method InjectionCheck can read");
                for (String s : in.methods()) {
                    assertTrue(s.matches("[\\w$<>]+(\\(\\S*)?"),
                            id + " selects " + s + ", which InjectionCheck cannot match");
                }
                for (String c : in.calls()) {
                    assertTrue(c.matches("L[\\w/$]+;[\\w$<>]+\\(\\S*"),
                            id + " targets " + c + ", which InjectionCheck cannot match");
                }
                seen.add(id);
            }
        }
        Set<String> stale = new HashSet<>(HookInstalls.HOOKS.keySet());
        stale.addAll(HookInstalls.BOUNDARIES);
        stale.removeAll(seen);
        assertEquals(Set.of(), stale, "entries that name no injector");
        // Iris's shadow pass and event timing are not mixins; every other hook is some injector's.
        Set<Hook> unmapped = EnumSet.complementOf(EnumSet.of(Hook.IRIS_SHADOW, Hook.EVENTS));
        unmapped.removeAll(HookInstalls.HOOKS.values());
        assertEquals(Set.of(), unmapped, "hooks that no injector is mapped to");
    }

    @Test
    void aHookWithAMissingTargetDidNotApplyAndOneWithEveryTargetIsInstalled() throws IOException {
        // ParticleEngine as it might look after an update that renamed tickParticle but kept render.
        ClassNode updated = target(ENGINE,
                method("render", RENDER_DESC, call(PARTICLE, "render", PARTICLE_RENDER_DESC)),
                method("updateParticle", "(Lnet/minecraft/client/particle/Particle;)V", call(PARTICLE, "tick", "()V")));
        HookInstalls.check(PARTICLE_ENGINE_MIXIN, classNode(PARTICLE_ENGINE_MIXIN), updated);

        assertTrue(HookInstalls.installed().contains(Hook.PARTICLE_RENDER.name()));
        assertTrue(HookInstalls.failed().contains(Hook.PARTICLE_TICK.name()));
        assertFalse(HookInstalls.installed().contains(Hook.PARTICLE_TICK.name()), "a hook is in one list or the other");

        // Accelerated Rendering's builder after an update that dropped addClientMesh: four of the hook's five
        // injectors find their targets and one does not, so the hook applied only in part.
        String builder = "com/github/argon4w/acceleratedrendering/core/buffers/accelerated/builders/AcceleratedBufferBuilder";
        HookInstalls.check(ACCELERATED_MIXIN, classNode(ACCELERATED_MIXIN), target(builder,
                method("addVertex", "(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
                method("addVertex", "(FFFIFFIIFFF)V"),
                method("addServerMesh", "()V")));
        assertTrue(HookInstalls.failed().contains(Hook.ACCELERATED_VERTICES.name()),
                "a hook that applied in part did not apply");
        assertFalse(HookInstalls.installed().contains(Hook.ACCELERATED_VERTICES.name()),
                "one injector that missed its target outweighs the four that found theirs");
    }
}

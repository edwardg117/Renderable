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
import java.nio.file.Files;
import java.nio.file.Path;
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
        List<String> out = new ArrayList<>(mixinsOf("renderable.mixins.json"));
        out.addAll(mixinsOf("renderable.thirdparty.mixins.json"));
        return out;
    }

    /** The mixins one config lists, by full class name. */
    static List<String> mixinsOf(String config) throws IOException {
        JsonObject json = config(config);
        String pkg = json.get("package").getAsString();
        List<String> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("client")) out.add(pkg + "." + e.getAsString());
        return out;
    }

    /** One mixin config, parsed. */
    static JsonObject config(String config) throws IOException {
        try (InputStream in = HookInstallsTest.class.getResourceAsStream("/" + config)) {
            assertNotNull(in, config);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
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
                    // MixinExtras moves an @Expression handler's injector annotation into @ExpressionInjectorWrapper
                    // before postApply, as it moves a sugar handler's into @SugarWrapper, and InjectionCheck unwraps
                    // only the latter: this class file would pass while a real client read no injector.
                    assertFalse(a.desc.startsWith("Lcom/llamalad7/mixinextras/expression/"), simple + "." + m.name
                            + " uses " + a.desc + ": make InjectionCheck unwrap @ExpressionInjectorWrapper first");
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

    @Test
    void thePhase6VanillaInjectorsNameTheirVerifiedTargets() throws IOException {
        // Spec addendum §P6.2, verified with javap against neoforge-21.1.220-merged.jar. These mixins are required, so a
        // misspelt target would stop the game at launch: pin each spelling here.
        String minecraft = "io.github.edwardg117.renderable.hooks.mixin.MinecraftMixin";
        assertEquals(new InjectionCheck.Injector("renderable$timeSound", List.of("tick"),
                        List.of("Lnet/minecraft/client/sounds/SoundManager;tick(Z)V")),
                injector(minecraft, "renderable$timeSound"));
        assertEquals(new InjectionCheck.Injector("renderable$timeAnimateTick", List.of("tick"),
                        List.of("Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V")),
                injector(minecraft, "renderable$timeAnimateTick"));
        assertEquals(new InjectionCheck.Injector("renderable$timeTerrain", List.of("renderLevel"),
                        List.of("Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer("
                                + "Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V")),
                injector("io.github.edwardg117.renderable.hooks.mixin.LevelRendererMixin", "renderable$timeTerrain"));
        assertEquals(new InjectionCheck.Injector("renderable$timeSoundPlay",
                        List.of("play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"), List.of()),
                injector("io.github.edwardg117.renderable.hooks.mixin.SoundEngineMixin", "renderable$timeSoundPlay"));
        // Deviation 4: the RenderType overload, which both renderers call at the end of each layer.
        assertEquals(new InjectionCheck.Injector("renderable$leaveTerrain",
                        List.of("dispatchRenderStage(Lnet/minecraft/client/renderer/RenderType;"
                                + "Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;I"
                                + "Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V"),
                        List.of()),
                injector("io.github.edwardg117.renderable.hooks.mixin.ClientHooksMixin", "renderable$leaveTerrain"));
    }

    static final String CLIENT_API_MIXIN = "io.github.edwardg117.renderable.hooks.mixin.distanthorizons.ClientApiMixin";
    static final String CLIENT_API = "com/seibel/distanthorizons/core/api/internal/ClientApi";

    /** An annotation's value by name, or null. */
    private static Object annotationValue(AnnotationNode a, String name) {
        for (int i = 0; a.values != null && i + 1 < a.values.size(); i += 2) {
            if (name.equals(a.values.get(i))) return a.values.get(i + 1);
        }
        return null;
    }

    @Test
    void theDistantHorizonsMixinIsSkippedWithoutDistantHorizons() throws IOException {
        // Review focus 1. Most players have no DH, so the plugin must decline the mixin: LOD then sits in none of the
        // hook lists and nothing is logged. The plugin gates a mixin by its sub-package; its map is read as source,
        // because the plugin loads FML, which this classpath lacks. Only the optional config may list the mixin.
        assertTrue(CLIENT_API_MIXIN.startsWith("io.github.edwardg117.renderable.hooks.mixin.distanthorizons."));
        assertTrue(mixinsOf("renderable.thirdparty.mixins.json").contains(CLIENT_API_MIXIN));
        assertFalse(mixinsOf("renderable.mixins.json").contains(CLIENT_API_MIXIN), "the required config would fail the game");
        String plugin = Files.readString(Path.of("src/main/java/io/github/edwardg117/renderable/hooks/RenderableMixinPlugin.java"));
        assertTrue(plugin.contains("\"distanthorizons\", \"distanthorizons\""), "REQUIRED_MOD gates the package on DH's mod id");

        int injectors = 0;
        for (MethodNode m : classNode(CLIENT_API_MIXIN).methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (m.visibleAnnotations != null) annotations.addAll(m.visibleAnnotations);
            if (m.invisibleAnnotations != null) annotations.addAll(m.invisibleAnnotations);
            for (AnnotationNode a : annotations) {
                if (!a.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;")) continue;
                assertEquals(0, annotationValue(a, "require"), m.name + " must not fail the game when DH changes");
                injectors++;
            }
        }
        assertEquals(4, injectors, "one per ClientApi render method");
    }

    @Test
    void theThirdPartyConfigIsOptionalSoAMissedTargetOnlyLogs() throws IOException {
        // An optional mod's mixin that misses its target (the mod updated) must log, not crash the game: the config as a
        // whole is not required, and its injectors default to require = 0.
        JsonObject config = config("renderable.thirdparty.mixins.json");
        assertFalse(config.get("required").getAsBoolean(),
                "a required third-party config would stop the game when the mod is absent or changed");
        assertEquals(0, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt(),
                "an injector that misses its target must be skipped and logged, not fail the game");
    }

    @Test
    void aDistantHorizonsRenderMethodThatWasRenamedOrResignedDidNotApply() throws IOException {
        // Review focus 4. ClientApi as DH 3.3.3 and 2.4.5-b have it (javap, 2026-10-10): every injector finds its method.
        ClassNode mixin = classNode(CLIENT_API_MIXIN);
        ClassNode pinned = target(CLIENT_API, method("renderLods", "()V"), method("renderDeferredLodsForShaders", "()V"),
                method("renderFadeOpaque", "()V"), method("renderFadeTransparent", "()V"));
        for (InjectionCheck.Injector in : InjectionCheck.injectors(mixin)) {
            assertTrue(InjectionCheck.present(in, pinned), in.handler() + " finds its method in the pinned ClientApi");
        }

        // A later DH that gives renderLods an argument and renames the transparent fade.
        ClassNode updated = target(CLIENT_API, method("renderLods", "(F)V"), method("renderDeferredLodsForShaders", "()V"),
                method("renderFadeOpaque", "()V"), method("renderFadeTranslucent", "()V"));
        assertFalse(InjectionCheck.present(injector(CLIENT_API_MIXIN, "renderable$timeRenderLods"), updated),
                "the selector carries its descriptor, so a re-signed method is not taken for the one the handler wraps");
        assertFalse(InjectionCheck.present(injector(CLIENT_API_MIXIN, "renderable$timeFadeTransparent"), updated));
        HookInstalls.check(CLIENT_API_MIXIN, mixin, updated);
        assertTrue(HookInstalls.failed().contains(Hook.LOD.name()), "listed under hooks that did not apply, not a silent 0");
        assertFalse(HookInstalls.installed().contains(Hook.LOD.name()));
    }
}

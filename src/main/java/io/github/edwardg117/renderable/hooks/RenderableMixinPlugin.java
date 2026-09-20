package io.github.edwardg117.renderable.hooks;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spec §4.5: mixins in a sub-package named after a mod apply only when that mod is loaded.
 * Vanilla mixins live directly in {@code hooks.mixin} and always apply.
 */
public final class RenderableMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> REQUIRED_MOD = Map.of(
            "sodium", "sodium",
            "iris", "iris",
            "flywheel", "flywheel",
            "acceleratedrendering", "acceleratedrendering");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String pkg = mixinClassName.substring(0, mixinClassName.lastIndexOf('.'));
        String sub = pkg.substring(pkg.lastIndexOf('.') + 1);
        String mod = REQUIRED_MOD.get(sub);
        return mod == null || LoadingModList.get().getModFileById(mod) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

package io.github.edwardg117.renderable.hooks.events;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a class to the id of the mod whose file holds it, through the class's JPMS module (spec addendum
 * §P5.A3). A class in no mod file keeps its module's name, and a class in no named module is "unknown".
 */
final class ModOwners {
    static final String UNKNOWN = "unknown";

    private final Map<String, String> modByModule;
    private final HashMap<Class<?>, String> cache = new HashMap<>();

    private ModOwners(Map<String, String> modByModule) {
        this.modByModule = modByModule;
    }

    /** Built from NeoForge's mod list; a file with several mods is named by its first. */
    static ModOwners fromModList() {
        HashMap<String, String> byModule = new HashMap<>();
        for (IModFileInfo file : ModList.get().getModFiles()) {
            List<IModInfo> mods = file.getMods();
            if (!mods.isEmpty()) byModule.putIfAbsent(file.moduleName(), mods.get(0).getModId());
        }
        return new ModOwners(byModule);
    }

    String modOf(Class<?> c) {
        return cache.computeIfAbsent(c, k -> {
            Module module = k.getModule();
            if (!module.isNamed()) return UNKNOWN;
            return modByModule.getOrDefault(module.getName(), module.getName());
        });
    }
}

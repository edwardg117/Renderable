package io.github.edwardg117.renderable.core;

/** One particle class (spec §3.4). Particles have no identity beyond their class, so the class is the key. */
public record ParticleKey(String className) implements ProfileKey {
    private static final String VANILLA_PREFIX = "net.minecraft.client.particle.";

    @Override
    public Category category() {
        return Category.PARTICLE;
    }

    @Override
    public String typeId() {
        return className;
    }

    /** Vanilla particle classes drop their package for readability; others keep it so mods stay distinguishable. */
    @Override
    public String label() {
        return className.startsWith(VANILLA_PREFIX) ? className.substring(VANILLA_PREFIX.length()) : className;
    }

    @Override
    public String serialize() {
        return "particle:" + className;
    }
}

package io.github.edwardg117.renderable.core.advice;

import io.github.edwardg117.renderable.core.Bucket;
import io.github.edwardg117.renderable.core.Environment;
import io.github.edwardg117.renderable.core.ProfileKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Builds {@link Facts} for rule tests: a quiet session of 1,000 frames of 10 ms, and nothing costly. */
final class FactsBuilder {
    private final LinkedHashMap<String, String> env = new LinkedHashMap<>();
    private int frames = 1000;
    private double frameMs = 10;
    private final double[] bucketMs = new double[Bucket.COUNT];
    private double tasksMs;
    private double capMs;
    private double swapMs = 2;
    private double shadowMs;
    private double shadowableMs;
    private final List<Facts.Cost> blockEntities = new ArrayList<>();
    private final List<Facts.Cost> entities = new ArrayList<>();
    private final List<Facts.Cost> particles = new ArrayList<>();
    private final List<Facts.Hitch> hitches = new ArrayList<>();
    private int gcCount;
    private long gcTotalMs;
    private long maxFrameGcMs;
    private final List<Facts.ModCost> mods = new ArrayList<>();
    private int unbalanced;

    FactsBuilder env(String key, String value) {
        env.put(key, value);
        return this;
    }

    FactsBuilder frameMs(double ms) {
        frameMs = ms;
        return this;
    }

    FactsBuilder bucket(Bucket b, double ms) {
        bucketMs[b.ordinal()] = ms;
        return this;
    }

    FactsBuilder tasks(double ms) {
        tasksMs = ms;
        return this;
    }

    FactsBuilder cap(double ms) {
        capMs = ms;
        return this;
    }

    FactsBuilder swap(double ms) {
        swapMs = ms;
        return this;
    }

    FactsBuilder shadow(double shadow, double shadowable) {
        shadowMs = shadow;
        shadowableMs = shadowable;
        return this;
    }

    FactsBuilder blockEntity(String type, double ms, int members, String where) {
        blockEntities.add(new Facts.Cost(type, ms, members, 0, where));
        return this;
    }

    FactsBuilder entity(String type, double ms, int members) {
        entities.add(new Facts.Cost(type, ms, members, 0, ""));
        return this;
    }

    FactsBuilder particle(String label, double ms, double live) {
        particles.add(new Facts.Cost(label, ms, 1, live, ""));
        return this;
    }

    FactsBuilder hitch(Facts.Hitch h) {
        hitches.add(h);
        return this;
    }

    FactsBuilder gc(int count, long totalMs, long maxFrameMs) {
        gcCount = count;
        gcTotalMs = totalMs;
        maxFrameGcMs = maxFrameMs;
        return this;
    }

    FactsBuilder mod(String id, double handlers, double layers, double packets, String topEvent) {
        mods.add(new Facts.ModCost(id, handlers, layers, packets, topEvent));
        return this;
    }

    FactsBuilder unbalanced(int frames) {
        unbalanced = frames;
        return this;
    }

    Facts build() {
        return new Facts(Environment.of(env, List.of()), frames, frameMs, bucketMs.clone(), tasksMs, capMs, swapMs,
                shadowMs, shadowableMs, List.copyOf(blockEntities), List.copyOf(entities), List.copyOf(particles),
                List.copyOf(hitches), gcCount, gcTotalMs, maxFrameGcMs, List.copyOf(mods), unbalanced);
    }

    /** A hitch with no GC pause, nothing unnamed and nothing leading from chunk upload: only its size and top contributors. */
    static Facts.Hitch hitch(int number, double totalMs, Facts.Top... top) {
        return new Facts.Hitch(number, totalMs, 0, 0, false, 0, 0, 0, List.of(top));
    }

    /** A top contributor that no GC pause could account for. */
    static Facts.Top top(ProfileKey key, double ms) {
        return new Facts.Top(key, ms, false);
    }
}

package io.github.edwardg117.renderable.core;

/**
 * Deterministic session used by several tests.
 *
 * 20 frames. Every frame is 16 ms except frame 10, which is 80 ms because the press spends 60 ms in it.
 * Per frame: 0.1 ms other render, press 2 ms (60 ms in frame 10), press2 1.5 ms, mixer 1 ms, cow 0.5 ms,
 * player 0.3 ms, then 2 ms other render; tick is 1 ms of which cow ticks 0.2 ms. One 5 ms GC lands in frame 15.
 *
 * Totals: press 98 ms render, press2 30, mixer 20, cow 10 render + 4 tick, player 6. Session 384 ms.
 * Median frame 16 ms, hitch threshold 32 ms, one hitch (frame 10). 120 timed calls at 25 ns = 0.003 ms overhead.
 */
public final class TestSessions {
    public static final long MS = 1_000_000L;

    public static final BlockEntityKey PRESS = new BlockEntityKey("create:mechanical_press", "minecraft:overworld", 100, 64, -20);
    public static final BlockEntityKey PRESS2 = new BlockEntityKey("create:mechanical_press", "minecraft:overworld", 103, 64, -20);
    public static final BlockEntityKey MIXER = new BlockEntityKey("create:mechanical_mixer", "minecraft:overworld", 90, 65, -22);
    public static final EntityKey COW = new EntityKey("minecraft:cow", 7, "");
    public static final EntityKey PLAYER = new EntityKey("minecraft:player", 1, "Steve");
    public static final ParticleKey FLAME = new ParticleKey("net.minecraft.client.particle.FlameParticle");
    public static final String PRESS_VISUAL = "com.simibubi.create.content.kinetics.press.PressVisual";
    public static final BlockKey STONE = new BlockKey("minecraft:stone");
    public static final BlockKey SHAFT = new BlockKey("create:shaft");
    public static final BlockKey WATER = new BlockKey("minecraft:water");
    public static final SectionKey SECTION_A = new SectionKey("minecraft:overworld", 0, 4, 0);
    public static final SectionKey SECTION_B = new SectionKey("minecraft:overworld", 1, 4, 0);

    public static final long WALL_START = 1_726_700_000_000L;

    private TestSessions() {
    }

    /** Public because the report tests in another package reuse it. */
    public static SessionResult sample() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25, 2), () -> now[0], WALL_START);
        Accumulator press = s.accumulator(PRESS);
        Accumulator press2 = s.accumulator(PRESS2);
        Accumulator mixer = s.accumulator(MIXER);
        Accumulator cow = s.accumulator(COW);
        Accumulator player = s.accumulator(PLAYER);

        long t = 0;
        for (int i = 0; i <= 20; i++) {
            long gcCount = i >= 16 ? 1 : 0;
            long gcMillis = i >= 16 ? 5 : 0;
            now[0] = t;
            s.onRenderPre(gcCount, gcMillis);
            if (i == 20) break;

            long frameStart = t;
            long pressCost = i == 10 ? 60 * MS : 2 * MS;

            t += 100_000;
            now[0] = t; s.begin(press, Bucket.BLOCK_ENTITY);  t += pressCost;  now[0] = t; s.end();
            now[0] = t; s.begin(press2, Bucket.BLOCK_ENTITY); t += 1_500_000;  now[0] = t; s.end();
            now[0] = t; s.begin(mixer, Bucket.BLOCK_ENTITY);  t += 1 * MS;     now[0] = t; s.end();
            now[0] = t; s.begin(cow, Bucket.ENTITY);          t += 500_000;    now[0] = t; s.end();
            now[0] = t; s.begin(player, Bucket.ENTITY);       t += 300_000;    now[0] = t; s.end();
            t += 2 * MS;
            now[0] = t; s.onRenderPost();

            now[0] = t; s.onTickPre();
            now[0] = t; s.begin(cow, Bucket.ENTITY_TICK);     t += 200_000;    now[0] = t; s.end();
            t += 800_000;
            now[0] = t; s.onTickPost();

            t = frameStart + (i == 10 ? 80 : 16) * MS;
        }
        return s.finish();
    }

    /** Phase 2 fixture; see {@link #build(boolean)}. */
    public static SessionResult phase2() {
        return build(false);
    }

    /**
     * Phase 3 fixture: {@link #phase2()} plus 100 vertices per press render and 20 per mixer render (2000 and
     * 400 over 20 frames), and mesh data: stone 400 calls 8 ms, shaft 50 calls 5 ms, water 20 calls 1 ms,
     * section (0,4,0) 3 rebuilds 9 ms, section (1,4,0) 1 rebuild 2 ms. Over the 0.384 s session that is
     * stone 20.833 ms/s and 20.0 us/call, section (0,4,0) 7.8125 rebuilds/s and 3.00 ms per rebuild.
     */
    public static SessionResult phase3() {
        return build(true);
    }

    /**
     * The phase 2 skeleton: the {@link #sample()} frames plus particles, ticks, light, Flywheel and visual samples.
     *
     * Per frame: 0.1 ms other render, press 2 ms (60 in frame 10), press2 1.5, mixer 1, cow 0.5, player 0.3,
     * flywheel 1.2 (bucket only), three FlameParticle renders of 0.1, light 0.3 (bucket only), 2 ms other render;
     * render 9.2 ms (67.2 in frame 10). Tick: cow 0.2 (entity tick), press 0.4 (be tick), two FlameParticle
     * ticks of 0.05 (particle tick), 0.3 other tick; tick 1.0 ms. Frames are 16 ms (frame 10: 80 ms).
     * Visual counts sampled at frames 5, 10 and 15: 100/120/140 block entities, 10 entities, 0 effects,
     * PRESS_VISUAL 100/120/140. One 5 ms GC in frame 15, deliberately larger than every key's and every
     * type's frame total there, so the fixture stays clean: the press type's total in that frame is
     * 2.4 (press, render + be tick) + 1.5 (press2) = 3.9 ms against the pause's 4 ms threshold, a 0.1 ms
     * margin that Task 2's type-peak tests rely on.
     *
     * Totals: 20 frames, 384 ms; tick avg 1.00 [particle tick 0.10, be tick 0.40, entity tick 0.20];
     * block entity 7.40, entity 0.80, particle render 0.30, flywheel 1.20, light 0.30, other render 2.10,
     * swap 6.10. FlameParticle 6 ms render over 60 calls (3.0 per frame), 2 ms tick over 40 calls.
     * 14 timed calls per frame, 280 total, at 25 ns = 0.007 ms overhead. Flywheel: 3 samples, avg 120/10/0.
     */
    private static SessionResult build(boolean phase3) {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25, 2), () -> now[0], WALL_START);
        Accumulator press = s.accumulator(PRESS);
        Accumulator press2 = s.accumulator(PRESS2);
        Accumulator mixer = s.accumulator(MIXER);
        Accumulator cow = s.accumulator(COW);
        Accumulator player = s.accumulator(PLAYER);
        Accumulator flame = s.accumulator(FLAME);

        long t = 0;
        for (int i = 0; i <= 20; i++) {
            long gcCount = i >= 16 ? 1 : 0;
            long gcMillis = i >= 16 ? 5 : 0;
            now[0] = t;
            s.onRenderPre(gcCount, gcMillis);
            if (i == 20) break;
            if (i == 5 || i == 10 || i == 15) {
                int be = 100 + (i - 5) * 4;
                s.recordVisualCounts(be, 10, 0, java.util.Map.of(PRESS_VISUAL, be));
            }

            long frameStart = t;
            long pressCost = i == 10 ? 60 * MS : 2 * MS;

            t += 100_000;
            now[0] = t; s.begin(press, Bucket.BLOCK_ENTITY);
            if (phase3) for (int v = 0; v < 100; v++) s.vertex();
            t += pressCost; now[0] = t; s.end();
            now[0] = t; s.begin(press2, Bucket.BLOCK_ENTITY); t += 1_500_000;  now[0] = t; s.end();
            now[0] = t; s.begin(mixer, Bucket.BLOCK_ENTITY);
            if (phase3) for (int v = 0; v < 20; v++) s.vertex();
            t += 1 * MS; now[0] = t; s.end();
            now[0] = t; s.begin(cow, Bucket.ENTITY);          t += 500_000;    now[0] = t; s.end();
            now[0] = t; s.begin(player, Bucket.ENTITY);       t += 300_000;    now[0] = t; s.end();
            now[0] = t; s.begin(null, Bucket.FLYWHEEL);       t += 1_200_000;  now[0] = t; s.end();
            for (int p = 0; p < 3; p++) {
                now[0] = t; s.begin(flame, Bucket.PARTICLE_RENDER); t += 100_000; now[0] = t; s.end();
            }
            now[0] = t; s.begin(null, Bucket.LIGHT);          t += 300_000;    now[0] = t; s.end();
            t += 2 * MS;
            now[0] = t; s.onRenderPost();

            now[0] = t; s.onTickPre();
            now[0] = t; s.begin(cow, Bucket.ENTITY_TICK);     t += 200_000;    now[0] = t; s.end();
            now[0] = t; s.begin(press, Bucket.BE_TICK);       t += 400_000;    now[0] = t; s.end();
            for (int p = 0; p < 2; p++) {
                now[0] = t; s.begin(flame, Bucket.PARTICLE_TICK); t += 50_000; now[0] = t; s.end();
            }
            t += 300_000;
            now[0] = t; s.onTickPost();

            t = frameStart + (i == 10 ? 80 : 16) * MS;
        }

        if (phase3) {
            for (int i = 0; i < 400; i++) s.mesh().get(STONE).add(20_000);
            for (int i = 0; i < 50; i++) s.mesh().get(SHAFT).add(100_000);
            for (int i = 0; i < 20; i++) s.mesh().get(WATER).add(50_000);
            for (int i = 0; i < 3; i++) s.mesh().get(SECTION_A).add(3 * MS);
            s.mesh().get(SECTION_B).add(2 * MS);
        }
        return s.finish();
    }
}

package io.github.edwardg117.renderable.core;

/**
 * Deterministic session used by several tests.
 *
 * 20 frames. Every frame is 16 ms except frame 10, which is 80 ms because the press spends 60 ms in it.
 * Per frame: 0.1 ms other render, press 2 ms (60 ms in frame 10), press2 1.5 ms, mixer 1 ms, cow 0.5 ms,
 * player 0.3 ms, then 2 ms other render; tick is 1 ms of which cow ticks 0.2 ms. One 5 ms GC lands in frame 15,
 * deliberately larger than every key's and every type's frame total there, so the fixture stays clean: the
 * press type's total in that frame is 2 (press) + 1.5 (press2) = 3.5 ms against the pause's 4 ms threshold,
 * a 0.5 ms margin that Task 2's type-peak tests rely on.
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
    public static final EntityKey PLAYER = EntityKey.player("minecraft:player", 1, true);
    public static final ParticleKey FLAME = new ParticleKey("net.minecraft.client.particle.FlameParticle");
    public static final String PRESS_VISUAL = "com.simibubi.create.content.kinetics.press.PressVisual";
    public static final BlockKey STONE = new BlockKey("minecraft:stone");
    public static final BlockKey SHAFT = new BlockKey("create:shaft");
    public static final BlockKey WATER = new BlockKey("minecraft:water");
    public static final SectionKey SECTION_A = new SectionKey("minecraft:overworld", 0, 4, 0);
    public static final SectionKey SECTION_B = new SectionKey("minecraft:overworld", 1, 4, 0);
    public static final ModKey STAGE_HANDLER =
            ModKey.event("create", "net.neoforged.neoforge.client.event.RenderLevelStageEvent");
    public static final ModKey LIVING_HANDLER =
            ModKey.event("somemod", "net.neoforged.neoforge.client.event.RenderLivingEvent$Pre");
    public static final ModKey TICK_HANDLER =
            ModKey.event("somemod", "net.neoforged.neoforge.client.event.ClientTickEvent$Post");
    public static final ModKey MINIMAP = ModKey.layer("xaerominimap", "xaerominimap:minimap");
    public static final ModKey CHUNK_PACKET = ModKey.packet("minecraft", "minecraft:level_chunk_with_light");
    public static final EntityKey BOAT = new EntityKey("minecraft:boat", 9, "");

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
     * Two-frame fixture for GC-suspect peaks (spec addendum §P4.A0). Public because the report tests in
     * another package reuse it.
     *
     * Frame 0 (0-16 ms) is clean: press renders 1-3 ms (2 ms), cow renders 3-4 ms (1 ms). Frame 1 (16-32 ms)
     * has the press call straddle the reported pause: press renders 17-57 ms (40 ms), cow renders 57-58 ms
     * (1 ms), nowhere near the pause. The pause (1 GC, 40 ms) is reported at the onRenderPre that opens the
     * third, discarded frame, at 100 ms, and is attributed back to frame 1, the one that just closed, giving
     * a 39 ms suspicion threshold (the pause minus the 1 ms slack): the press's 40 ms frame total clears it
     * by 1 ms and is marked GC-suspect, while the cow's 1 ms is nowhere near it and stays clean.
     */
    public static SessionResult gcSession() {
        long[] clock = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> clock[0], WALL_START);
        Accumulator press = s.accumulator(PRESS);
        Accumulator cow = s.accumulator(COW);
        clock[0] = 0;          s.onRenderPre(0, 0);
        clock[0] = 1 * MS;     s.begin(press, Bucket.BLOCK_ENTITY); clock[0] = 3 * MS;  s.end();
        clock[0] = 3 * MS;     s.begin(cow, Bucket.ENTITY);         clock[0] = 4 * MS;  s.end();
        s.onRenderPost();
        clock[0] = 16 * MS;    s.onRenderPre(0, 0);
        clock[0] = 17 * MS;    s.begin(press, Bucket.BLOCK_ENTITY); clock[0] = 57 * MS; s.end();
        clock[0] = 57 * MS;    s.begin(cow, Bucket.ENTITY);         clock[0] = 58 * MS; s.end();
        s.onRenderPost();
        clock[0] = 100 * MS;   s.onRenderPre(1, 40);
        return s.finish();
    }

    /**
     * One frame whose only cost is a press call that straddles the reported pause (spec addendum §P4.A0), so
     * the press never has a clean frame at all: {@code peakFrameUs} is 0 and {@code gcFrames} is 1, and the
     * zero is distinguishable from "never cost anything" only by that gcFrames count. Public for the same
     * reason as {@link #gcSession()}.
     *
     * Frame 0 (0-16 ms): press renders 1-41 ms (40 ms). The pause (1 GC, 40 ms) is reported at the
     * onRenderPre that opens the second, discarded frame, at 100 ms, giving a 39 ms suspicion threshold (the
     * pause minus the 1 ms slack): the press's 40 ms frame total clears it by 1 ms, so the frame has no clean
     * sample at all.
     */
    public static SessionResult everyFrameGcSuspect() {
        long[] clock = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 0, 0), () -> clock[0], WALL_START);
        Accumulator press = s.accumulator(PRESS);
        clock[0] = 0;        s.onRenderPre(0, 0);
        clock[0] = 1 * MS;   s.begin(press, Bucket.BLOCK_ENTITY); clock[0] = 41 * MS; s.end();
        s.onRenderPost();
        clock[0] = 100 * MS; s.onRenderPre(1, 40);
        return s.finish();
    }

    /**
     * Phase 5 fixture (spec addendum §P5.A1, §P5.A6 and §P5.A8): two identical 20 ms frames that exercise every
     * new frame component and every kind of attribution-only call. Per frame, in milliseconds from its start:
     *
     * render 0-5: cow render 0.5-1.0 (ENTITY) holding the living-render handler 0.6-0.8, which emits 7
     * vertices; the stage handler 1-3 holding a press render 1.5-2.5 (BLOCK_ENTITY) that emits 10 vertices;
     * the HUD 3-5 (bucket only) holding the minimap layer 3.5-4.5. Frame cap 5-8. Tasks 8-10 holding the chunk
     * packet 8.5-9.5. Tick 10-12: the tick handler 10.2-10.6, then the boat's tick 10.6-11.6 (ENTITY_TICK)
     * holding the player's rider tick 11.0-11.4.
     *
     * So per frame: render 5, frame cap 3, tasks 2, tick 2, swap 8. Buckets: entity 0.5, block entity 1,
     * hud 2, entity tick 1 (the boat only; the rider is nested). Other render 1.5, of which 1 is named (the
     * stage handler less the press inside it; the living handler is inside the cow). Other tick 1, of which
     * 0.4 is named. Tasks 2, of which 1 is named. Vertices land on the cow (7) and the press (10). Type peaks:
     * create 2, somemod 0.6, xaerominimap 1, minecraft 1. 10 timed calls and 2 vertex calls a frame.
     */
    public static SessionResult phase5() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25, 2), () -> now[0], WALL_START);
        Accumulator cow = s.accumulator(COW);
        Accumulator press = s.accumulator(PRESS);
        Accumulator player = s.accumulator(PLAYER);
        Accumulator boat = s.accumulator(BOAT);
        Accumulator stage = s.accumulator(STAGE_HANDLER);
        Accumulator living = s.accumulator(LIVING_HANDLER);
        Accumulator tick = s.accumulator(TICK_HANDLER);
        Accumulator minimap = s.accumulator(MINIMAP);
        Accumulator chunk = s.accumulator(CHUNK_PACKET);

        for (int i = 0; i <= 2; i++) {
            long t0 = i * 20 * MS;
            now[0] = t0; s.onRenderPre(0, 0);
            if (i == 2) break;

            now[0] = t0 + 500_000;    s.begin(cow, Bucket.ENTITY);
            now[0] = t0 + 600_000;    s.beginAttribution(living);
            s.vertices(7);
            now[0] = t0 + 800_000;    s.end();
            now[0] = t0 + 1 * MS;     s.end();
            now[0] = t0 + 1 * MS;     s.beginAttribution(stage);
            now[0] = t0 + 1_500_000;  s.begin(press, Bucket.BLOCK_ENTITY);
            s.vertices(10);
            now[0] = t0 + 2_500_000;  s.end();
            now[0] = t0 + 3 * MS;     s.end();
            now[0] = t0 + 3 * MS;     s.begin(null, Bucket.HUD);
            now[0] = t0 + 3_500_000;  s.beginAttribution(minimap);
            now[0] = t0 + 4_500_000;  s.end();
            now[0] = t0 + 5 * MS;     s.end();
            now[0] = t0 + 5 * MS;     s.onRenderPost();

            now[0] = t0 + 5 * MS;     s.onCapBegin();
            now[0] = t0 + 8 * MS;     s.onCapEnd();

            now[0] = t0 + 8 * MS;     s.onTasksBegin();
            now[0] = t0 + 8_500_000;  s.beginAttribution(chunk);
            now[0] = t0 + 9_500_000;  s.end();
            now[0] = t0 + 10 * MS;    s.onTasksEnd();

            now[0] = t0 + 10 * MS;    s.onTickPre();
            now[0] = t0 + 10_200_000; s.beginAttribution(tick);
            now[0] = t0 + 10_600_000; s.end();
            now[0] = t0 + 10_600_000; s.begin(boat, Bucket.ENTITY_TICK);
            now[0] = t0 + 11 * MS;    s.begin(player, Bucket.ENTITY_TICK);    // riding the boat
            now[0] = t0 + 11_400_000; s.end();
            now[0] = t0 + 11_600_000; s.end();
            now[0] = t0 + 12 * MS;    s.onTickPost();
        }
        return s.finish();
    }

    /**
     * One frame with 17 mods of five event handlers each, for the report's top 15 and top 3 cuts, which the JSON
     * must not make. {@code mod01} costs the most and {@code mod17} the least; within every mod, handler
     * {@code a.E1} costs the most and {@code a.E5} the least.
     */
    public static SessionResult manyMods() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25, 2), () -> now[0], WALL_START);
        s.onRenderPre(0, 0);
        for (int m = 1; m <= 17; m++) {
            for (int k = 1; k <= 5; k++) {
                s.beginAttribution(s.accumulator(ModKey.event("mod" + (m < 10 ? "0" : "") + m, "a.E" + k)));
                now[0] += (18 - m) * 100_000L + (6 - k) * 1_000L;
                s.end();
            }
        }
        s.onRenderPost();
        now[0] += MS;
        s.onRenderPre(0, 0);
        return s.finish();
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

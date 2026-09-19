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

    public static final long WALL_START = 1_726_700_000_000L;

    private TestSessions() {
    }

    /** Public because the report tests in another package reuse it. */
    public static SessionResult sample() {
        long[] now = {0};
        Session s = new Session(new SessionConfig(10_000 * MS, 2.0, 16 * MS, 5, 25), () -> now[0], WALL_START);
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
}

package dev.moar.world;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
/*?}*/

/**
 * Backend-only setback detector. Subsystems call isCalm() before
 * sending placement/interaction packets to avoid stacking actions on
 * top of a server-issued teleport-back.
 *
 * Detection: sample player position each client tick; any single-tick
 * delta above SETBACK_THRESHOLD_BLOCKS is treated as a server teleport.
 * Normal walk/sprint stays well under the threshold.
 */
public final class SetbackMonitor {

    /** Single-tick movement above this distance counts as a setback. */
    private static final double SETBACK_THRESHOLD_BLOCKS = 0.6;

    /** Stable ticks required after the last setback before isCalm() returns true. */
    private static final int CALM_WINDOW_TICKS = 12;

    /** Ring buffer length for recentSetbackCount(). */
    private static final int HISTORY_SIZE = 64;

    /** Movement below this per-tick delta counts as stationary. */
    private static final double STATIONARY_DELTA_BLOCKS = 0.025;

    private boolean primed;
    private double lastX, lastY, lastZ;

    /** Ticks elapsed since the last detected setback (capped at CALM_WINDOW_TICKS). */
    private int ticksSinceSetback = CALM_WINDOW_TICKS;

    /** Consecutive low-movement ticks. */
    private int stationaryTicks;

    /** Total setbacks observed since join. */
    private int totalSetbacks;

    /** Tick timestamps of recent setbacks (newest first), capped to HISTORY_SIZE. */
    private final long[] setbackTicks = new long[HISTORY_SIZE];
    private int historyHead;
    private long currentTick;

    /** Singleton — there's only one local player. */
    private static final SetbackMonitor INSTANCE = new SetbackMonitor();

    public static SetbackMonitor get() { return INSTANCE; }

    private SetbackMonitor() {}

    /** Call once per client tick (END_CLIENT_TICK). Safe when no player is loaded. */
    /*? if >=26.1 {*//*
    public void tick(Minecraft mc) {
    *//*?} else {*/
    public void tick(MinecraftClient mc) {
    /*?}*/
        if (mc == null || mc.player == null
                /*? if >=26.1 {*//*|| mc.level == null*//*?} else {*/|| mc.world == null/*?}*/) {
            primed = false;
            ticksSinceSetback = CALM_WINDOW_TICKS;
            return;
        }
        currentTick++;
        /*? if >=26.1 {*//*
        LocalPlayer p = mc.player;
        *//*?} else {*/
        ClientPlayerEntity p = mc.player;
        /*?}*/
        double x = p.getX(), y = p.getY(), z = p.getZ();

        if (!primed) {
            lastX = x; lastY = y; lastZ = z;
            primed = true;
            return;
        }

        double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        double stationarySq = STATIONARY_DELTA_BLOCKS * STATIONARY_DELTA_BLOCKS;
        lastX = x; lastY = y; lastZ = z;

        if (distSq <= stationarySq) {
            stationaryTicks++;
        } else {
            stationaryTicks = 0;
        }

        if (distSq > SETBACK_THRESHOLD_BLOCKS * SETBACK_THRESHOLD_BLOCKS) {
            ticksSinceSetback = 0;
            totalSetbacks++;
            setbackTicks[historyHead] = currentTick;
            historyHead = (historyHead + 1) % HISTORY_SIZE;
        } else if (ticksSinceSetback < CALM_WINDOW_TICKS) {
            ticksSinceSetback++;
        }
    }

    /** True when no setback has occurred in the last CALM_WINDOW_TICKS ticks. */
    public boolean isCalm() {
        return ticksSinceSetback >= CALM_WINDOW_TICKS;
    }

    /** Ticks elapsed since the most recent setback (capped at CALM_WINDOW_TICKS). */
    public int ticksSinceSetback() { return ticksSinceSetback; }

    /** Total setbacks observed this session. */
    public int totalSetbacks() { return totalSetbacks; }

    /** Setbacks within the last {@code windowTicks} client ticks. */
    public int recentSetbackCount(int windowTicks) {
        if (windowTicks <= 0) return 0;
        long cutoff = currentTick - windowTicks;
        int count = 0;
        for (long t : setbackTicks) {
            if (t > cutoff && t > 0) count++;
        }
        return count;
    }

    /** True when movement has stayed below STATIONARY_DELTA_BLOCKS for minTicks. */
    public boolean isStationaryFor(int minTicks) {
        if (minTicks <= 0) return true;
        return stationaryTicks >= minTicks;
    }

    /** Reset state. Call on disconnect/world unload to clear baseline. */
    public void reset() {
        primed = false;
        ticksSinceSetback = CALM_WINDOW_TICKS;
        totalSetbacks = 0;
        stationaryTicks = 0;
        currentTick = 0;
        historyHead = 0;
        for (int i = 0; i < setbackTicks.length; i++) setbackTicks[i] = 0;
    }
}

package dev.moar.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Movement controller that walks the player toward a target position.
 *
 * When Baritone is installed, delegates pathfinding to Baritone's A*
 * engine via reflection — no compile-time Baritone dependency required.
 * When Baritone is absent, falls back to vanilla key simulation with
 * straight-line walking, auto-jumping, and stuck detection.
 */
public final class PathWalker {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/PathWalker");

    private PathWalker() {}

    // ── Baritone detection (cached) ─────────────────────────────────

    private static final boolean BARITONE_AVAILABLE;
    static {
        boolean found;
        try {
            Class.forName("baritone.api.BaritoneAPI");
            found = true;
        } catch (ClassNotFoundException e) {
            found = false;
        }
        BARITONE_AVAILABLE = found;
        if (found) {
            LOGGER.info("Baritone detected — using Baritone for pathfinding");
        } else {
            LOGGER.info("Baritone not found — using vanilla straight-line walker");
        }
    }

    /** Returns {@code true} if Baritone is present at runtime. */
    public static boolean isBaritoneAvailable() {
        return BARITONE_AVAILABLE;
    }

    // ── vanilla config ──────────────────────────────────────────────

    private static final double ARRIVAL_DIST_SQ = 2.5 * 2.5;
    private static final double ARRIVAL_Y_TOLERANCE = 3.0;
    /** Base timeout — extended dynamically by distance (see {@link #getEffectiveTimeout()}). */
    private static final int BASE_MAX_TICKS = 6000;   // 5 min base
    /** Ticks per block of distance added to the base timeout. */
    private static final int TICKS_PER_BLOCK = 5;
    private static final int STUCK_THRESHOLD = 200;
    /** Minimum stuck threshold used for very nearby targets when the
     *  distance-adaptive scaling kicks in (see {@link #effectiveStuckThreshold()}). */
    private static final int MIN_STUCK_THRESHOLD = 60;
    /** Shorter threshold for nearby targets — if Baritone can't compute
     *  a path within this many ticks for a target within
     *  {@link #VANILLA_FALLBACK_DIST_SQ}, fall back to vanilla walking. */
    private static final int VANILLA_FALLBACK_TICKS = 40;  // 2 seconds
    /** Max squared distance for vanilla fallback (10 blocks). */
    private static final double VANILLA_FALLBACK_DIST_SQ = 10.0 * 10.0;
    private static final double MIN_PROGRESS_SQ = 1.0;
    private static final int STUCK_CYCLES_BEFORE_GIVE_UP = 5;

    /** Initial distance from player to target when the walk started.
     *  Used to compute a distance-scaled timeout. */
    private static double initialDistance = 0;

    // ── shared state ────────────────────────────────────────────────

    private static BlockPos target;
    private static boolean active;
    private static boolean arrived;
    private static boolean stuck;
    private static int ticksWalking;
    /** Consecutive ticks where Baritone's process is active (has a goal)
     *  but {@code isPathing()} is false (not executing any path step).
     *  When this exceeds {@link #STUCK_THRESHOLD}, Baritone is stuck
     *  trying to compute an impossible route. */
    private static int noPathTicks;

    // ── vanilla-only state ──────────────────────────────────────────

    private static Vec3d lastProgressPos;
    private static int lastProgressTick;
    private static int stuckCycles;

    /** True when Baritone's allowPlace/allowParkour were enabled for
     *  the current walk.  Restored to previous values on stop/arrival. */
    private static boolean placementEnabled;

    /** GoalNear radius for the current walk — used for the arrival
     *  check so we accept arrival within the same range that Baritone
     *  targets.  Zero means use the tight {@link #ARRIVAL_DIST_SQ}. */
    private static int goalRadius = 0;

    /** When {@code >= -64}, we're navigating to a Y-level rather than
     *  an XZ position.  Arrival is based on Y only. */
    private static int yLevelTarget = Integer.MIN_VALUE;

    // ── mining descent state ────────────────────────────────────────
    /** True when the player is descending by breaking the pillar
     *  beneath their feet — bypasses Baritone entirely. */
    private static boolean miningDescent;
    /** True when we've fallen back from Baritone to vanilla walking
     *  because Baritone couldn't compute a short path. */
    private static boolean vanillaFallback;
    /** Target Y to descend to via pillar mining. */
    private static int miningDescentTargetY = Integer.MIN_VALUE;
    /** Block currently being mined during descent. */
    private static BlockPos currentMiningPos;
    /** Saved player pitch before mining descent (restored on completion). */
    private static float savedPitch;

    /** Waypoint queue — intermediate destinations to reach before the
     *  final target.  Each waypoint is navigated to in order; when the
     *  last waypoint is reached, {@code arrived} is set to true. */
    private static final Deque<BlockPos> waypointQueue = new ArrayDeque<>();
    /** Per-waypoint GoalNear radii.  When non-empty, each element
     *  corresponds to the next waypoint in {@link #waypointQueue}.
     *  If empty, {@link #waypointRadius} is used for all legs. */
    private static final Deque<Integer> waypointRadiusQueue = new ArrayDeque<>();
    /** Radius used for each waypoint leg (GoalNear). */
    private static int waypointRadius = 4;

    /** Items reserved for the schematic build — keyed by Item, value
     *  is the count still needed.  {@link #configureThrowawayFromInventory}
     *  only offers blocks the player has in excess of these
     *  quantities so Baritone doesn't waste building materials as
     *  scaffold. */
    private static java.util.Map<Item, Integer> reservedItems = java.util.Collections.emptyMap();

    /**
     * Tell PathWalker which items (and how many) are reserved for the
     * schematic build.  Call this before any placement-enabled walk so
     * that only surplus blocks are offered to Baritone as throwaway.
     *
     * @param needed  map of Item → count needed for the build, or
     *                {@code null} to clear
     */
    public static void setReservedItems(java.util.Map<Item, Integer> needed) {
        reservedItems = needed != null ? needed : java.util.Collections.emptyMap();
    }

    // ── public API ──────────────────────────────────────────────────

    /**
     * Start walking to the given position.
     * Uses Baritone pathfinding if available, vanilla key simulation otherwise.
     */
    public static void walkTo(BlockPos pos) {
        target = pos.toImmutable();
        active = true;
        arrived = false;
        stuck = false;
        ticksWalking = 0;
        goalRadius = 0;
        yLevelTarget = Integer.MIN_VALUE;
        lastProgressPos = null;
        lastProgressTick = 0;
        stuckCycles = 0;
        recordInitialDistance(pos);
        LOGGER.debug("PathWalker: walking to ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());

        if (BARITONE_AVAILABLE) {
            BaritoneDelegate.walkTo(pos);
        }
    }

    /**
     * Start walking adjacent to the given position (next to it, not on
     * top of it).  Useful for navigating to chests or interactable blocks.
     * Falls back to {@link #walkTo(BlockPos)} when Baritone is not available.
     */
    public static void walkToAdjacent(BlockPos pos) {
        if (BARITONE_AVAILABLE) {
            target = pos.toImmutable();
            active = true;
            arrived = false;
            stuck = false;
            ticksWalking = 0;
            LOGGER.debug("PathWalker: walking adjacent to ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());
            BaritoneDelegate.walkToAdjacent(pos);
        } else {
            walkTo(pos);
        }
    }

    /**
     * Start walking to within {@code radius} blocks of the given position.
     * Uses Baritone's GoalNear to find a standable position nearby without
     * trying to path directly to or onto the target block.
     */
    public static void walkToNearby(BlockPos pos, int radius) {
        if (BARITONE_AVAILABLE) {
            target = pos.toImmutable();
            active = true;
            arrived = false;
            stuck = false;
            ticksWalking = 0;
            goalRadius = radius;
            yLevelTarget = Integer.MIN_VALUE;
            recordInitialDistance(pos);
            LOGGER.debug("PathWalker: walking near ({}, {}, {}) r={}", pos.getX(), pos.getY(), pos.getZ(), radius);
            BaritoneDelegate.walkToNearby(pos, radius);
        } else {
            walkTo(pos);
        }
    }

    /**
     * Walk to within {@code radius} blocks of the given position with
     * Baritone's {@code allowPlace} and {@code allowParkour} enabled.
     *
     * This lets Baritone pillar-up, bridge across gaps, and use
     * parkour jumps to reach elevated or otherwise unreachable targets
     * — replacing the custom scaffolding system entirely.
     *
     * Placement settings are enabled before the path starts and
     * automatically restored when pathing completes (via
     * {@link #tickBaritone()}).
     */
    public static void walkToWithPlacement(BlockPos pos, int radius) {
        walkToWithPlacement(pos, radius, null);
    }

    public static void walkToWithPlacement(BlockPos pos, int radius,
                                           ClientPlayerEntity player) {
        if (BARITONE_AVAILABLE) {
            BaritoneDelegate.enablePlacement();
            if (player != null) {
                BaritoneDelegate.configureThrowawayFromInventory(player);
            }
            placementEnabled = true;
            walkToNearby(pos, radius);
            LOGGER.debug("PathWalker: walking with placement to ({}, {}, {}) r={}",
                    pos.getX(), pos.getY(), pos.getZ(), radius);
        } else {
            walkTo(pos);
        }
    }

    /**
     * Start navigating to a specific Y level, ignoring horizontal
     * position.  Uses Baritone's GoalYLevel — the player just needs
     * to reach the given Y coordinate by any means (staircase, fall,
     * etc.).  Arrival is checked by Y alone.
     *
     * @param y  the target Y level
     */
    public static void walkToYLevel(int y) {
        if (BARITONE_AVAILABLE) {
            target = new BlockPos(0, y, 0);
            active = true;
            arrived = false;
            stuck = false;
            ticksWalking = 0;
            goalRadius = 0;
            yLevelTarget = y;
            lastProgressPos = null;
            lastProgressTick = 0;
            stuckCycles = 0;
            initialDistance = 0;
            LOGGER.debug("PathWalker: walking to Y level {}", y);
            BaritoneDelegate.walkToYLevel(y);
        }
    }

    /**
     * Navigate to a specific Y level with Baritone's placement and
     * parkour enabled — allows Baritone to build a staircase down or
     * up to reach the target Y level.
     *
     * @param y       the target Y level
     * @param player  player entity for configuring throwaway items
     */
    public static void walkToYLevelWithPlacement(int y,
                                                  ClientPlayerEntity player) {
        if (BARITONE_AVAILABLE) {
            BaritoneDelegate.enablePlacement();
            if (player != null) {
                BaritoneDelegate.configureThrowawayFromInventory(player);
            }
            placementEnabled = true;
            walkToYLevel(y);
            LOGGER.debug("PathWalker: walking to Y level {} with placement", y);
        }
    }

    /**
     * Start descending from a pillar by mining the blocks beneath
     * the player's feet.  Each block break causes a safe 1-block
     * fall.  This bypasses Baritone entirely — no pathfinding needed.
     *
     * Continues until the player reaches {@code targetY} or
     * encounters a gap where falling would exceed 3 blocks (unsafe).
     *
     * @param targetY  the Y level to descend to
     */
    public static void startMiningDescent(int targetY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        miningDescent = true;
        miningDescentTargetY = targetY;
        currentMiningPos = null;
        savedPitch = mc.player.getPitch();

        target = mc.player.getBlockPos(); // track current pos
        active = true;
        arrived = false;
        stuck = false;
        ticksWalking = 0;
        goalRadius = 0;
        yLevelTarget = Integer.MIN_VALUE;
        lastProgressPos = null;
        lastProgressTick = 0;
        stuckCycles = 0;
        initialDistance = 0;
        waypointQueue.clear();
        waypointRadiusQueue.clear();

        LOGGER.debug("PathWalker: starting mining descent from Y={} to Y={}",
                mc.player.getBlockPos().getY(), targetY);
    }

    /**
     * Walk to a destination via a list of intermediate waypoints.
     *
     * Baritone navigates to each waypoint in order using GoalNear
     * with the given {@code radius}.  When a waypoint is reached, the
     * next one is automatically started.  When the final waypoint is
     * reached, {@code arrived} is set to {@code true}.
     *
     * If Baritone fails to reach any waypoint, the walker stops
     * normally (arrived=false) so the caller can handle the failure.
     *
     * @param waypoints  ordered list of positions to visit; the last
     *                   entry is the final destination
     * @param radius     GoalNear radius for each leg
     */
    public static void walkToViaWaypoints(List<BlockPos> waypoints, int radius) {
        if (waypoints == null || waypoints.isEmpty()) return;

        waypointQueue.clear();
        waypointRadiusQueue.clear();
        for (BlockPos wp : waypoints) {
            waypointQueue.addLast(wp.toImmutable());
        }
        waypointRadius = radius;

        // Start walking to the first waypoint
        BlockPos first = waypointQueue.pollFirst();
        LOGGER.debug("PathWalker: starting waypoint chain ({} legs), first=({},{},{})",
                waypoints.size(), first.getX(), first.getY(), first.getZ());
        walkToNearby(first, radius);
    }

    /**
     * Walk to destinations via waypoints with per-waypoint GoalNear radii.
     * This allows different legs to have different tolerances — e.g. loose
     * radius for staircase-descent legs, tight radius for the final target.
     *
     * @param waypoints  ordered list of positions to visit
     * @param radii      GoalNear radius for each waypoint (must be same size)
     */
    public static void walkToViaWaypointsWithRadii(List<BlockPos> waypoints,
                                                    List<Integer> radii) {
        if (waypoints == null || waypoints.isEmpty()) return;

        waypointQueue.clear();
        waypointRadiusQueue.clear();
        for (int i = 0; i < waypoints.size(); i++) {
            waypointQueue.addLast(waypoints.get(i).toImmutable());
            waypointRadiusQueue.addLast(radii.get(i));
        }
        waypointRadius = radii.get(0);

        BlockPos first = waypointQueue.pollFirst();
        int firstRadius = waypointRadiusQueue.pollFirst();
        goalRadius = firstRadius;
        LOGGER.debug("PathWalker: starting waypoint chain ({} legs), first=({},{},{}) r={}",
                waypoints.size(), first.getX(), first.getY(), first.getZ(), firstRadius);

        target = first;
        active = true;
        arrived = false;
        stuck = false;
        ticksWalking = 0;
        lastProgressPos = null;
        lastProgressTick = 0;
        stuckCycles = 0;
        recordInitialDistance(first);
        if (BARITONE_AVAILABLE) {
            BaritoneDelegate.walkToNearby(first, firstRadius);
        }
    }

    /**
     * Walk to destinations via waypoints with per-waypoint GoalNear radii,
     * with Baritone's placement and parkour enabled for every leg.
     *
     * @param waypoints  ordered list of positions to visit
     * @param radii      GoalNear radius for each waypoint (must be same size)
     * @param player     player entity for configuring throwaway items
     */
    public static void walkToViaWaypointsWithRadiiAndPlacement(
            List<BlockPos> waypoints, List<Integer> radii,
            ClientPlayerEntity player) {
        if (!BARITONE_AVAILABLE || waypoints == null || waypoints.isEmpty()) {
            walkToViaWaypointsWithRadii(waypoints, radii);
            return;
        }
        BaritoneDelegate.enablePlacement();
        if (player != null) {
            BaritoneDelegate.configureThrowawayFromInventory(player);
        }
        placementEnabled = true;
        walkToViaWaypointsWithRadii(waypoints, radii);
        LOGGER.debug("PathWalker: waypoint chain with placement+radii ({} legs)", waypoints.size());
    }

    /**
     * Walk to a destination via intermediate waypoints with Baritone's
     * {@code allowPlace} and {@code allowParkour} enabled for every leg.
     *
     * This combines the waypoint chain with placement mode so
     * Baritone can bridge gaps, pillar up/down, and parkour-jump on
     * each leg — essential for reaching supply chests at different
     * elevations from the build area.
     *
     * @param waypoints  ordered list of positions to visit
     * @param radius     GoalNear radius for each leg
     */
    public static void walkToViaWaypointsWithPlacement(List<BlockPos> waypoints,
                                                        int radius) {
        walkToViaWaypointsWithPlacement(waypoints, radius, null);
    }

    public static void walkToViaWaypointsWithPlacement(List<BlockPos> waypoints,
                                                        int radius,
                                                        ClientPlayerEntity player) {
        if (!BARITONE_AVAILABLE || waypoints == null || waypoints.isEmpty()) {
            walkToViaWaypoints(waypoints, radius);
            return;
        }
        BaritoneDelegate.enablePlacement();
        if (player != null) {
            BaritoneDelegate.configureThrowawayFromInventory(player);
        }
        placementEnabled = true;
        walkToViaWaypoints(waypoints, radius);
        LOGGER.debug("PathWalker: waypoint chain with placement ({} legs)", waypoints.size());
    }

    /** Stop all pathing and release keys. */
    public static void stop() {
        if (active && (!BARITONE_AVAILABLE || vanillaFallback)) {
            releaseKeys();
        }
        if (BARITONE_AVAILABLE) {
            if (placementEnabled) {
                BaritoneDelegate.restorePlacement();
                placementEnabled = false;
            }
            BaritoneDelegate.stop();
        }
        if (miningDescent) {
            // Restore pitch
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.setPitch(savedPitch);
            miningDescent = false;
            currentMiningPos = null;
        }
        active = false;
        arrived = false;
        stuck = false;
        target = null;
        stuckCycles = 0;
        goalRadius = 0;
        yLevelTarget = Integer.MIN_VALUE;
        initialDistance = 0;
        noPathTicks = 0;
        vanillaFallback = false;
        waypointQueue.clear();
        waypointRadiusQueue.clear();
        LOGGER.debug("PathWalker: stopped");
    }

    public static boolean isActive()   { return active; }
    public static boolean hasArrived() { return arrived; }
    public static boolean isStuck()    { return stuck; }
    public static BlockPos getTarget() { return target; }
    public static int getTicksWalking() { return ticksWalking; }
    public static boolean isPlacementEnabled() { return placementEnabled; }

    /**
     * Returns the set of item IDs that Baritone considers acceptable
     * for throwaway/scaffold placement (e.g. "minecraft:cobblestone").
     * Used for detecting Baritone-placed scaffold blocks in the world.
     */
    public static java.util.Set<String> getThrowawayItemIds() {
        if (!BARITONE_AVAILABLE) return java.util.Collections.emptySet();
        return BaritoneDelegate.getThrowawayItemIds();
    }

    /**
     * Tick the movement controller.  Must be called every client tick while
     * navigation is active.
     */
    public static void tick() {
        if (!active || target == null) return;

        ticksWalking++;

        if (miningDescent) {
            tickMiningDescent();
            return;
        }

        if (vanillaFallback) {
            tickVanilla();
            return;
        }

        if (BARITONE_AVAILABLE) {
            tickBaritone();
        } else {
            tickVanilla();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MINING DESCENT — break pillar beneath feet to descend.
    // ═══════════════════════════════════════════════════════════════════

    private static void tickMiningDescent() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            stop();
            return;
        }

        // Timeout safety — don't mine forever
        if (ticksWalking >= BASE_MAX_TICKS) {
            LOGGER.warn("PathWalker: mining descent timeout after {} ticks", ticksWalking);
            stuck = true;
            stop();
            return;
        }

        int playerFeetY = mc.player.getBlockPos().getY();
        if (playerFeetY <= miningDescentTargetY) {
            // Reached target Y level
            LOGGER.debug("PathWalker: mining descent complete at Y={}", playerFeetY);
            mc.player.setPitch(savedPitch);
            arrived = true;
            active = false;
            miningDescent = false;
            currentMiningPos = null;
            return;
        }

        // Look straight down for anti-cheat compliance
        mc.player.setPitch(90.0f);

        // Find the solid block to mine — the one under the player's feet
        BlockPos feetPos = mc.player.getBlockPos();
        BlockPos below = feetPos.down();

        BlockState belowState = mc.world.getBlockState(below);
        BlockPos toMine = null;

        if (!belowState.isAir() && belowState.getHardness(mc.world, below) >= 0
                && belowState.getHardness(mc.world, below) <= 10.0f) {
            toMine = below;
        } else {
            // Check feet pos itself (player might be inside the block)
            BlockState feetState = mc.world.getBlockState(feetPos);
            if (!feetState.isAir() && feetState.getHardness(mc.world, feetPos) >= 0
                    && feetState.getHardness(mc.world, feetPos) <= 10.0f) {
                toMine = feetPos;
            }
        }

        if (toMine == null) {
            // Both blocks are air — player is falling, wait for landing.
            // If we've been falling for a while (no block below within
            // safe distance), we've left the pillar — stop mining.
            boolean hasSolidBelow = false;
            for (int dy = 1; dy <= 4; dy++) {
                BlockPos check = feetPos.down(dy);
                if (!mc.world.getBlockState(check).isAir()) {
                    hasSolidBelow = true;
                    break;
                }
            }
            if (!hasSolidBelow) {
                // Pillar ended — we're done (might be at ground)
                LOGGER.debug("PathWalker: mining descent — no more pillar below, stopping at Y={}", playerFeetY);
                mc.player.setPitch(savedPitch);
                arrived = true;
                active = false;
                miningDescent = false;
                currentMiningPos = null;
            }
            return;
        }

        // Safety check: make sure there's a solid block within 3 below
        // the one we're about to mine.  If not, mining it would cause
        // an unsafe fall (> 3 blocks).
        boolean safeFall = false;
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos check = toMine.down(dy);
            if (!mc.world.getBlockState(check).isAir()) {
                safeFall = true;
                break;
            }
        }
        if (!safeFall) {
            // Gap in the pillar — unsafe to continue.  Stop here;
            // we're presumably near ground level.
            LOGGER.debug("PathWalker: mining descent — unsafe gap below Y={}, stopping", toMine.getY());
            mc.player.setPitch(savedPitch);
            arrived = true;
            active = false;
            miningDescent = false;
            currentMiningPos = null;
            return;
        }

        // Mine the block
        if (currentMiningPos == null || !currentMiningPos.equals(toMine)) {
            // Start mining a new block
            mc.interactionManager.attackBlock(toMine, Direction.UP);
            currentMiningPos = toMine;
        } else {
            // Continue mining the same block
            mc.interactionManager.updateBlockBreakingProgress(toMine, Direction.UP);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BARITONE PATH — accessed entirely via reflection.
    // ═══════════════════════════════════════════════════════════════════

    private static void tickBaritone() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            stop();
            return;
        }

        // ── grace period for path computation ───────────────────────
        // Baritone needs a few ticks to compute the initial path.
        // Don't check isPathing() too early or we get a false negative.
        if (ticksWalking < 10) return;

        // ── timeout check ───────────────────────────────────────────
        if (ticksWalking >= getEffectiveTimeout()) {
            LOGGER.warn("PathWalker[Baritone]: timeout after {} ticks (limit={})",
                    ticksWalking, getEffectiveTimeout());
            stuck = true;
            active = false;
            waypointQueue.clear();
            waypointRadiusQueue.clear();
            if (placementEnabled) {
                BaritoneDelegate.restorePlacement();
                placementEnabled = false;
            }
            BaritoneDelegate.stop();
            return;
        }

        // ── no-progress stuck detection ────────────────────────────
        // Baritone's process may stay "active" (it has a goal) while
        // failing to find a valid path.  In that case isPathing() stays
        // false because no path segment is being executed.  If this
        // persists for STUCK_THRESHOLD ticks, the route is impossible.
        if (BaritoneDelegate.isProcessActive() && !BaritoneDelegate.isPathing()) {
            noPathTicks++;

            // ── vanilla fallback for short distances ────────────────
            // Baritone's A* can choke on very short paths near platform
            // edges / fences / gaps.  If the target is within ~10 blocks
            // and Baritone hasn't made progress in 2 seconds, stop
            // Baritone and use simple WASD walking instead.
            if (noPathTicks >= VANILLA_FALLBACK_TICKS && target != null) {
                /*? if >=1.21.10 {*//*
                Vec3d pPos = mc.player.getSyncedPos();
                *//*?} else {*/
                Vec3d pPos = mc.player.getPos();
                /*?}*/
                double distSq = pPos.squaredDistanceTo(Vec3d.ofCenter(target));
                double vertDy = Math.abs(pPos.y - target.getY() - 0.5);
                // Only fall back to vanilla if the target is roughly
                // at the same elevation — vanilla walking can't climb
                // pillars or descend scaffolds.
                if (distSq <= VANILLA_FALLBACK_DIST_SQ && vertDy <= 3.0) {
                    LOGGER.info("PathWalker[Baritone]: can't path to nearby target (dist²={}) — falling back to vanilla walking",
                            String.format("%.1f", distSq));
                    // Stop Baritone but keep our state active
                    if (placementEnabled) {
                        BaritoneDelegate.restorePlacement();
                        placementEnabled = false;
                    }
                    BaritoneDelegate.stop();
                    noPathTicks = 0;
                    ticksWalking = 0; // reset so vanilla timeout starts fresh
                    vanillaFallback = true;
                    return;
                }
            }

            int effectiveThreshold = effectiveStuckThreshold();
            if (noPathTicks >= effectiveThreshold) {
                LOGGER.warn("PathWalker[Baritone]: no path progress for {} ticks (threshold={}) — route impossible",
                        noPathTicks, effectiveThreshold);
                stuck = true;
                active = false;
                noPathTicks = 0;
                waypointQueue.clear();
                waypointRadiusQueue.clear();
                if (placementEnabled) {
                    BaritoneDelegate.restorePlacement();
                    placementEnabled = false;
                }
                BaritoneDelegate.stop();
                return;
            }
        } else {
            noPathTicks = 0;
        }

        // ── check if Baritone finished working toward the goal ─────
        // isProcessActive() checks whether the CustomGoalProcess is
        // still in control (computing or walking).  This is reliable
        // across segment recomputation pauses, unlike isPathing()
        // which only checks if a segment is being executed right now.
        if (!BaritoneDelegate.isProcessActive()) {
            // Baritone is no longer working toward the goal —
            // check if we're near the target
            /*? if >=1.21.10 {*//*
            Vec3d playerPos = mc.player.getSyncedPos();
            *//*?} else {*/
            Vec3d playerPos = mc.player.getPos();
            /*?}*/
            Vec3d targetCenter = Vec3d.ofCenter(target);
            double dxz2 = horizontalDistSq(playerPos, targetCenter);
            double dy = Math.abs(playerPos.y - targetCenter.y);

            // Y-level navigation: arrival check ignores XZ entirely.
            boolean arrivedAtGoal;
            if (yLevelTarget != Integer.MIN_VALUE) {
                arrivedAtGoal = Math.abs(playerPos.y - yLevelTarget) <= ARRIVAL_Y_TOLERANCE;
            } else {
                // When using GoalNear(radius), accept arrival within that
                // radius (+1 block tolerance for sub-block positioning).
                // Without a GoalNear radius, use the tight default.
                double arrDistSq = goalRadius > 0
                        ? (goalRadius + 1.0) * (goalRadius + 1.0)
                        : ARRIVAL_DIST_SQ;
                arrivedAtGoal = dxz2 <= arrDistSq && dy <= ARRIVAL_Y_TOLERANCE;
            }
            if (arrivedAtGoal) {
                // Reached current target — check for more waypoints
                if (!waypointQueue.isEmpty()) {
                    BlockPos next = waypointQueue.pollFirst();
                    int nextRadius = waypointRadiusQueue.isEmpty()
                            ? waypointRadius
                            : waypointRadiusQueue.pollFirst();
                    goalRadius = nextRadius;
                    yLevelTarget = Integer.MIN_VALUE;
                    LOGGER.debug("PathWalker[Baritone]: waypoint reached, next=({},{},{}) r={} ({} remaining)",
                            next.getX(), next.getY(), next.getZ(), nextRadius, waypointQueue.size());
                    target = next;
                    ticksWalking = 0;
                    BaritoneDelegate.walkToNearby(next, nextRadius);
                    return;
                }
                LOGGER.debug("PathWalker[Baritone]: arrived after {} ticks", ticksWalking);
                arrived = true;
            } else {
                // Didn't reach current waypoint — if there are remaining
                // waypoints, try to skip ahead to the next one (this
                // waypoint may have been obstructed but the next may be
                // reachable via a different route).
                if (!waypointQueue.isEmpty()) {
                    BlockPos next = waypointQueue.pollFirst();
                    int nextRadius = waypointRadiusQueue.isEmpty()
                            ? waypointRadius
                            : waypointRadiusQueue.pollFirst();
                    goalRadius = nextRadius;
                    yLevelTarget = Integer.MIN_VALUE;
                    LOGGER.debug("PathWalker[Baritone]: waypoint missed, skipping to ({},{},{}) r={} ({} remaining)",
                            next.getX(), next.getY(), next.getZ(), nextRadius, waypointQueue.size());
                    target = next;
                    ticksWalking = 0;
                    BaritoneDelegate.walkToNearby(next, nextRadius);
                    return;
                }
                LOGGER.debug("PathWalker[Baritone]: pathing stopped without arriving (dist²={}, dy={})",
                        String.format("%.1f", dxz2), String.format("%.1f", dy));

                // If the target is nearby, try vanilla walking instead
                // of declaring failure — Baritone may have choked on a
                // short path that simple WASD movement can handle.
                double totalDistSq = dxz2 + dy * dy;
                // Only fall back to vanilla for roughly same-elevation
                // targets — vanilla walking can't handle vertical movement.
                if (totalDistSq <= VANILLA_FALLBACK_DIST_SQ && dy <= 3.0) {
                    LOGGER.info("PathWalker[Baritone]: target nearby (dist²={}) — falling back to vanilla walking",
                            String.format("%.1f", totalDistSq));
                    if (placementEnabled) {
                        BaritoneDelegate.restorePlacement();
                        placementEnabled = false;
                    }
                    noPathTicks = 0;
                    ticksWalking = 0;
                    vanillaFallback = true;
                    return;
                }
            }
            if (placementEnabled) {
                BaritoneDelegate.restorePlacement();
                placementEnabled = false;
            }
            active = false;
        }
    }

    /**
     * Inner class that accesses Baritone's API entirely via reflection.
     * No compile-time dependency on Baritone is required — all class,
     * method, and constructor references are resolved at runtime.
     * Reflection handles are cached in a static initializer for performance.
     * If reflection setup fails (e.g. incompatible Baritone version),
     * {@code ready} is set to {@code false} and all methods become no-ops.
     */
    private static final class BaritoneDelegate {

        private BaritoneDelegate() {}

        // ── reflection handles (resolved once, cached) ──────────────
        private static boolean ready;
        private static Method getProvider;
        private static Method getPrimaryBaritone;
        private static Method getCustomGoalProcess;
        private static Method setGoalAndPath;
        private static Method getPathingBehavior;
        private static Method cancelEverything;
        private static Method isPathingMethod;
        private static Method isActiveMethod;  // IBaritoneProcess.isActive()
        private static Constructor<?> goalBlockCtor;
        private static Constructor<?> goalGetToBlockCtor;
        private static Constructor<?> goalNearCtor;
        private static Constructor<?> goalYLevelCtor;

        // ── settings reflection handles ─────────────────────────────
        private static Method getSettings;
        private static Object settingsInstance;
        private static Object allowPlaceSetting;   // Settings.Setting<Boolean>
        private static Object allowParkourSetting;  // Settings.Setting<Boolean>
        private static Object allowInventorySetting; // Settings.Setting<Boolean>
        private static Object maxFallHeightSetting;   // Settings.Setting<Integer>
        private static Object throwawayItemsSetting; // Settings.Setting<List<Item>>
        private static Method settingGetValue;      // Setting.value field getter
        private static java.lang.reflect.Field settingValueField; // Setting.value direct field
        private static boolean settingsReady;

        /** Saved values before we enabled placement. */
        private static boolean savedAllowPlace;
        private static boolean savedAllowParkour;
        private static boolean savedAllowInventory;
        private static int savedMaxFallHeight;
        private static Object savedThrowawayItems; // List<Item> — saved original list

        static {
            try {
                Class<?> api = Class.forName("baritone.api.BaritoneAPI");
                getProvider = api.getMethod("getProvider");

                Class<?> provider = Class.forName("baritone.api.IBaritoneProvider");
                getPrimaryBaritone = provider.getMethod("getPrimaryBaritone");

                Class<?> iBaritone = Class.forName("baritone.api.IBaritone");
                getCustomGoalProcess = iBaritone.getMethod("getCustomGoalProcess");
                getPathingBehavior = iBaritone.getMethod("getPathingBehavior");

                Class<?> goal = Class.forName("baritone.api.pathing.goals.Goal");
                Class<?> iCustomGoalProcess = Class.forName("baritone.api.process.ICustomGoalProcess");
                setGoalAndPath = iCustomGoalProcess.getMethod("setGoalAndPath", goal);

                // ICustomGoalProcess extends IBaritoneProcess which has isActive()
                Class<?> iBaritoneProcess = Class.forName("baritone.api.process.IBaritoneProcess");
                isActiveMethod = iBaritoneProcess.getMethod("isActive");

                Class<?> iPathingBehavior = Class.forName("baritone.api.behavior.IPathingBehavior");
                cancelEverything = iPathingBehavior.getMethod("cancelEverything");
                isPathingMethod = iPathingBehavior.getMethod("isPathing");

                Class<?> goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock");
                goalBlockCtor = goalBlock.getConstructor(int.class, int.class, int.class);

                Class<?> goalGetToBlock = Class.forName("baritone.api.pathing.goals.GoalGetToBlock");
                goalGetToBlockCtor = goalGetToBlock.getConstructor(BlockPos.class);

                Class<?> goalNear = Class.forName("baritone.api.pathing.goals.GoalNear");
                goalNearCtor = goalNear.getConstructor(BlockPos.class, int.class);

                Class<?> goalYLevel = Class.forName("baritone.api.pathing.goals.GoalYLevel");
                goalYLevelCtor = goalYLevel.getConstructor(int.class);

                ready = true;
            } catch (Exception e) {
                LOGGER.error("PathWalker: Baritone reflection setup failed", e);
                ready = false;
            }

            // ── resolve settings reflection handles ─────────────────
            // These are optional — if they fail, placement-mode walks
            // just fall back to normal pathfinding (no block placement).
            settingsReady = false;
            if (ready) {
                try {
                    Class<?> api = Class.forName("baritone.api.BaritoneAPI");
                    getSettings = api.getMethod("getSettings");
                    settingsInstance = getSettings.invoke(null);

                    // Settings fields are public: Settings.allowPlace, Settings.allowParkour
                    // Each is of type Settings.Setting<Boolean>
                    java.lang.reflect.Field allowPlaceField =
                            settingsInstance.getClass().getField("allowPlace");
                    allowPlaceSetting = allowPlaceField.get(settingsInstance);

                    java.lang.reflect.Field allowParkourField =
                            settingsInstance.getClass().getField("allowParkour");
                    allowParkourSetting = allowParkourField.get(settingsInstance);

                    // allowInventory — lets Baritone search the FULL
                    // inventory (slots 9-35) for throwaway items, not
                    // just the hotbar.  It will swap items to the
                    // hotbar as needed.
                    java.lang.reflect.Field allowInventoryField =
                            settingsInstance.getClass().getField("allowInventory");
                    allowInventorySetting = allowInventoryField.get(settingsInstance);

                    // maxFallHeightNoWater — default is 3 which is too
                    // conservative for scaffolded descents.  We raise it
                    // during placement walks so Baritone can drop down
                    // from elevated bridges.
                    java.lang.reflect.Field maxFallField =
                            settingsInstance.getClass().getField("maxFallHeightNoWater");
                    maxFallHeightSetting = maxFallField.get(settingsInstance);

                    // acceptableThrowawayItems — controls what blocks Baritone
                    // can place for scaffolding (Setting<List<Item>>)
                    java.lang.reflect.Field throwawayField =
                            settingsInstance.getClass().getField("acceptableThrowawayItems");
                    throwawayItemsSetting = throwawayField.get(settingsInstance);

                    // Setting<T> has a public field 'value' of type T
                    settingValueField = allowPlaceSetting.getClass().getField("value");
                    settingValueField.setAccessible(true);

                    settingsReady = true;
                    LOGGER.info("PathWalker: Baritone settings reflection ready "
                            + "(allowPlace/allowParkour control available)");

                    // Always enable allowParkour — gap-jumping is safe
                    // (no block placement, no mining, just movement)
                    // and required for builds with gaps (iron bars,
                    // fences, walls, bridges with 1-block openings).
                    try {
                        settingValueField.set(allowParkourSetting, true);
                        LOGGER.debug("PathWalker: enabled Baritone allowParkour globally");
                    } catch (Exception pe) {
                        LOGGER.warn("PathWalker: failed to enable allowParkour", pe);
                    }
                } catch (Exception e) {
                    LOGGER.warn("PathWalker: Baritone settings reflection failed "
                            + "— placement-mode walks will use default Baritone settings", e);
                }
            }
        }

        static boolean isReady() {
            return ready;
        }

        /**
         * Save the current allowPlace/allowParkour/throwawayItems values
         * and enable placement so Baritone can pillar-up and parkour-jump.
         *
         * Also restricts throwaway items to cheap scaffold materials
         * so Baritone doesn't waste valuable building materials.
         */
        @SuppressWarnings("unchecked")
        static void enablePlacement() {
            if (!settingsReady) return;
            try {
                savedAllowPlace = (Boolean) settingValueField.get(allowPlaceSetting);
                savedAllowParkour = (Boolean) settingValueField.get(allowParkourSetting);
                savedAllowInventory = (Boolean) settingValueField.get(allowInventorySetting);
                savedMaxFallHeight = (Integer) settingValueField.get(maxFallHeightSetting);
                // Save the original throwaway items list
                savedThrowawayItems = settingValueField.get(throwawayItemsSetting);
                settingValueField.set(allowPlaceSetting, true);
                settingValueField.set(allowParkourSetting, true);
                settingValueField.set(allowInventorySetting, true);
                // Keep maxFallHeightNoWater at vanilla-safe levels.
                // Baritone can descend by placing blocks downward
                // (staircase) with allowPlace enabled — no need for
                // dangerous falls.  Value of 3 = zero damage.
                settingValueField.set(maxFallHeightSetting, 3);
                LOGGER.debug("PathWalker: enabled Baritone allowPlace + allowParkour + allowInventory "
                        + "(was place={}, parkour={}, inventory={})",
                        savedAllowPlace, savedAllowParkour, savedAllowInventory);
            } catch (Exception e) {
                LOGGER.warn("PathWalker: failed to enable Baritone placement settings", e);
            }
        }

        /**
         * Scan the player's inventory for block items and add those
         * with surplus quantities to Baritone's
         * {@code acceptableThrowawayItems} list.
         *
         * "Surplus" means the player holds more of that item than
         * what is reserved for the schematic build (set via
         * {@link PathWalker#setReservedItems}).  This prevents
         * Baritone from wasting blocks the printer still needs.
         *
         * Must be called after {@link #enablePlacement()}
         * so that the original list is saved first.
         */
        @SuppressWarnings("unchecked")
        static void configureThrowawayFromInventory(ClientPlayerEntity player) {
            if (!settingsReady || player == null) return;
            try {
                Object currentList = settingValueField.get(throwawayItemsSetting);
                if (!(currentList instanceof java.util.List)) return;

                // Start with the existing throwaway items (Baritone defaults)
                java.util.List<Item> newList = new ArrayList<>(
                        (java.util.List<Item>) currentList);

                // Count total holdings per block item across the ENTIRE
                // inventory (not just hotbar) so the surplus check is
                // accurate — the player might have 128 concrete split
                // across hotbar and main inventory.
                java.util.Map<Item, Integer> holdings = new java.util.HashMap<>();
                PlayerInventory inv = player.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack stack = inv.getStack(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                        holdings.merge(stack.getItem(), stack.getCount(),
                                Integer::sum);
                    }
                }

                // Only add items where the player holds MORE than what
                // the schematic needs.  Even 1 surplus block is enough
                // — Baritone typically only places a handful for a
                // pillar.
                for (var entry : holdings.entrySet()) {
                    Item item = entry.getKey();
                    int have = entry.getValue();
                    int reserved = reservedItems.getOrDefault(item, 0);
                    if (have > reserved && !newList.contains(item)) {
                        newList.add(item);
                    }
                }

                settingValueField.set(throwawayItemsSetting, newList);
                LOGGER.debug("PathWalker: configured {} throwaway items "
                                + "({} from inventory surplus)",
                        newList.size(),
                        newList.size() - ((java.util.List<?>) currentList).size());
            } catch (Exception e) {
                LOGGER.warn("PathWalker: failed to configure throwaway items", e);
            }
        }

        /**
         * Restore allowPlace/allowParkour/throwawayItems to the values
         * saved by {@link #enablePlacement()}.
         */
        static void restorePlacement() {
            if (!settingsReady) return;
            try {
                settingValueField.set(allowPlaceSetting, savedAllowPlace);
                settingValueField.set(allowParkourSetting, savedAllowParkour);
                settingValueField.set(allowInventorySetting, savedAllowInventory);
                settingValueField.set(maxFallHeightSetting, savedMaxFallHeight);
                if (savedThrowawayItems != null) {
                    settingValueField.set(throwawayItemsSetting, savedThrowawayItems);
                    savedThrowawayItems = null;
                }
                LOGGER.debug("PathWalker: restored Baritone settings "
                        + "(place={}, parkour={}, inventory={})",
                        savedAllowPlace, savedAllowParkour, savedAllowInventory);
            } catch (Exception e) {
                LOGGER.warn("PathWalker: failed to restore Baritone placement settings", e);
            }
        }

        /**
         * Returns the current set of items Baritone considers acceptable
         * for scaffold/throwaway placement.  Used by SchematicPrinter to
         * detect which blocks in the world were placed by Baritone.
         *
         * @return set of item IDs (e.g. "minecraft:cobblestone"), or
         *         empty set if reflection failed
         */
        @SuppressWarnings("unchecked")
        static java.util.Set<String> getThrowawayItemIds() {
            java.util.Set<String> result = new java.util.HashSet<>();
            if (!settingsReady) return result;
            try {
                Object list = settingValueField.get(throwawayItemsSetting);
                if (list instanceof java.util.List<?> items) {
                    for (Object item : items) {
                        // item is an Item instance — get its registry ID
                        String id = net.minecraft.registry.Registries.ITEM
                                .getId((net.minecraft.item.Item) item).toString();
                        result.add(id);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("PathWalker: failed to read throwaway items", e);
            }
            return result;
        }

        private static Object getPrimary() throws Exception {
            Object prov = getProvider.invoke(null);
            return getPrimaryBaritone.invoke(prov);
        }

        static void walkTo(BlockPos pos) {
            if (!ready) return;
            try {
                Object process = getCustomGoalProcess.invoke(getPrimary());
                Object goal = goalBlockCtor.newInstance(pos.getX(), pos.getY(), pos.getZ());
                setGoalAndPath.invoke(process, goal);
            } catch (Exception e) {
                LOGGER.error("PathWalker[Baritone]: failed to set goal", e);
            }
        }

        static void walkToAdjacent(BlockPos pos) {
            if (!ready) return;
            try {
                Object process = getCustomGoalProcess.invoke(getPrimary());
                Object goal = goalGetToBlockCtor.newInstance(pos);
                setGoalAndPath.invoke(process, goal);
            } catch (Exception e) {
                LOGGER.error("PathWalker[Baritone]: failed to set adjacent goal", e);
            }
        }

        static void walkToNearby(BlockPos pos, int radius) {
            if (!ready) return;
            try {
                Object process = getCustomGoalProcess.invoke(getPrimary());
                Object goal = goalNearCtor.newInstance(pos, radius);
                setGoalAndPath.invoke(process, goal);
            } catch (Exception e) {
                LOGGER.error("PathWalker[Baritone]: failed to set nearby goal", e);
            }
        }

        /**
         * Walk to a specific Y level, ignoring XZ position entirely.
         * Uses Baritone's GoalYLevel(y) which is satisfied when the
         * player is standing at the given Y coordinate regardless of
         * their horizontal position.
         */
        static void walkToYLevel(int y) {
            if (!ready) return;
            try {
                Object process = getCustomGoalProcess.invoke(getPrimary());
                Object goal = goalYLevelCtor.newInstance(y);
                setGoalAndPath.invoke(process, goal);
            } catch (Exception e) {
                LOGGER.error("PathWalker[Baritone]: failed to set Y-level goal", e);
            }
        }

        static void stop() {
            if (!ready) return;
            try {
                Object behavior = getPathingBehavior.invoke(getPrimary());
                cancelEverything.invoke(behavior);
            } catch (Exception e) {
                LOGGER.error("PathWalker[Baritone]: failed to cancel pathing", e);
            }
        }

        static boolean isPathing() {
            if (!ready) return false;
            try {
                Object behavior = getPathingBehavior.invoke(getPrimary());
                return (boolean) isPathingMethod.invoke(behavior);
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Returns {@code true} if the CustomGoalProcess is still in
         * control — i.e. Baritone is still working toward the goal,
         * whether actively walking a path segment or computing the
         * next one.  This is the correct "is Baritone busy?" check.
         *
         * {@link #isPathing()} only checks if a path segment is
         * being executed right now, which returns false during A*
         * recomputation pauses between segments.
         */
        static boolean isProcessActive() {
            if (!ready) return false;
            try {
                Object process = getCustomGoalProcess.invoke(getPrimary());
                return (boolean) isActiveMethod.invoke(process);
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Fallback
    // ═══════════════════════════════════════════════════════════════════

    private static void tickVanilla() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            stop();
            return;
        }

        ClientPlayerEntity player = mc.player;

        // ── timeout check ───────────────────────────────────────────
        if (ticksWalking >= getEffectiveTimeout()) {
            LOGGER.warn("PathWalker[Vanilla]: timeout after {} ticks", ticksWalking);
            releaseKeys();
            stuck = true;
            active = false;
            return;
        }

        // ── arrival check ───────────────────────────────────────────
        /*? if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dxz2 = horizontalDistSq(playerPos, targetCenter);
        double dy = Math.abs(playerPos.y - targetCenter.y);

        // ── fall detection ──────────────────────────────────────────
        // If the player is now significantly below the target (fell off
        // a pillar/scaffold), vanilla walking can't recover — give up
        // so the caller can re-engage Baritone with placement enabled.
        if (targetCenter.y - playerPos.y > 4.0) {
            LOGGER.warn("PathWalker[Vanilla]: player fell below target (dy={}) — giving up",
                    String.format("%.1f", targetCenter.y - playerPos.y));
            releaseKeys();
            stuck = true;
            active = false;
            return;
        }

        if (dxz2 <= ARRIVAL_DIST_SQ && dy <= ARRIVAL_Y_TOLERANCE) {
            LOGGER.debug("PathWalker[Vanilla]: arrived at target after {} ticks", ticksWalking);
            releaseKeys();
            arrived = true;
            active = false;
            return;
        }

        // ── stuck detection ─────────────────────────────────────────
        if (lastProgressPos == null) {
            lastProgressPos = playerPos;
            lastProgressTick = ticksWalking;
        } else if (ticksWalking - lastProgressTick >= STUCK_THRESHOLD) {
            double progressDist2 = horizontalDistSq(playerPos, lastProgressPos);
            if (progressDist2 < MIN_PROGRESS_SQ) {
                stuckCycles++;
                LOGGER.debug("PathWalker[Vanilla]: stuck cycle {} — trying jump", stuckCycles);
                if (player.isOnGround()) {
                    player.jump();
                }
                if (stuckCycles >= STUCK_CYCLES_BEFORE_GIVE_UP) {
                    LOGGER.warn("PathWalker[Vanilla]: stuck after {} cycles, declaring stuck", stuckCycles);
                    stuck = true;
                }
            } else {
                stuckCycles = 0;
            }
            lastProgressPos = playerPos;
            lastProgressTick = ticksWalking;
        }

        // ── look at target ──────────────────────────────────────────
        double dx = targetCenter.x - playerPos.x;
        double dz = targetCenter.z - playerPos.z;
        float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        float currentYaw = player.getYaw();
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float maxTurn = 15.0f;
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -maxTurn, maxTurn);
        player.setYaw(newYaw);
        player.setPitch(5.0f);

        // ── movement ────────────────────────────────────────────────
        GameOptions options = mc.options;

        options.forwardKey.setPressed(true);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);

        boolean facingTarget = Math.abs(yawDiff) < 30.0f;
        options.sprintKey.setPressed(facingTarget && dxz2 > 9.0);

        // ── proactive obstacle jumping ──────────────────────────────
        if (player.isOnGround()) {
            if (player.horizontalCollision) {
                player.jump();
            } else if (shouldJumpAhead(player, mc.world, targetYaw)) {
                player.jump();
            }
        }
    }

    // ── internals ───────────────────────────────────────────────────────

    private static boolean shouldJumpAhead(ClientPlayerEntity player, World world, float yaw) {
        double rad = Math.toRadians(yaw + 90.0);
        double aheadX = player.getX() + (-Math.sin(rad) * 1.2);
        double aheadZ = player.getZ() + (Math.cos(rad) * 1.2);

        BlockPos ahead = new BlockPos(
                (int) Math.floor(aheadX),
                (int) Math.floor(player.getY()),
                (int) Math.floor(aheadZ));

        BlockState footState  = world.getBlockState(ahead);
        BlockState headState  = world.getBlockState(ahead.up());
        BlockState aboveHead  = world.getBlockState(ahead.up(2));

        boolean footSolid  = !footState.isAir() && !footState.isReplaceable();
        boolean headClear  = headState.isAir() || headState.isReplaceable();
        boolean aboveClear = aboveHead.isAir() || aboveHead.isReplaceable();

        return footSolid && headClear && aboveClear;
    }

    private static void releaseKeys() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        GameOptions options = mc.options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.sprintKey.setPressed(false);
    }

    private static double horizontalDistSq(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    /** Record the distance from the player to the target at walk start. */
    private static void recordInitialDistance(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            initialDistance = Math.sqrt(mc.player.getBlockPos().getSquaredDistance(pos));
        } else {
            initialDistance = 0;
        }
    }

    /**
     * Compute the effective timeout for the current walk.
     * Scales linearly with distance so long walks (500+ blocks)
     * don't hit the ceiling prematurely.
     */
    private static int getEffectiveTimeout() {
        return BASE_MAX_TICKS + (int) (initialDistance * TICKS_PER_BLOCK);
    }

    /**
     * Compute a distance-adaptive stuck threshold for the current walk.
     * Baritone's A* should find a path quickly for nearby targets — no
     * need to wait 10 full seconds.  For targets within 30 blocks, the
     * threshold is scaled linearly down to {@link #MIN_STUCK_THRESHOLD}.
     * Beyond 30 blocks, the full {@link #STUCK_THRESHOLD} is used.
     */
    private static int effectiveStuckThreshold() {
        if (target == null) return STUCK_THRESHOLD;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return STUCK_THRESHOLD;
        /*? if >=1.21.10 {*//*
        Vec3d pPos = mc.player.getSyncedPos();
        *//*?} else {*/
        Vec3d pPos = mc.player.getPos();
        /*?}*/
        double dist = Math.sqrt(pPos.squaredDistanceTo(Vec3d.ofCenter(target)));
        if (dist >= 30.0) return STUCK_THRESHOLD;
        // Linear scale: 0 blocks → MIN_STUCK_THRESHOLD, 30 blocks → STUCK_THRESHOLD
        return Math.max(MIN_STUCK_THRESHOLD,
                (int) (MIN_STUCK_THRESHOLD + (STUCK_THRESHOLD - MIN_STUCK_THRESHOLD) * dist / 30.0));
    }
}

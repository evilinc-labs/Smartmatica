package dev.moar.spawnproof;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.moar.MoarMod;
import dev.moar.stash.StashDatabase;
import dev.moar.util.ChatHelper;
import dev.moar.util.PathWalker;
import dev.moar.util.PlacementEngine;
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.piston.*;
*//*?} else {*/
import net.minecraft.block.*;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.player.LocalPlayer;
*//*?} else {*/
import net.minecraft.client.network.ClientPlayerEntity;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Item;
*//*?} else {*/
import net.minecraft.item.Item;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Items;
*//*?} else {*/
import net.minecraft.item.Items;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.registries.BuiltInRegistries;
*//*?} else {*/
import net.minecraft.registry.Registries;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.resources.Identifier;
*//*?} else {*/
import net.minecraft.util.Identifier;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.Direction;
*//*?} else {*/
import net.minecraft.util.math.Direction;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.LightLayer;
*//*?} else {*/
import net.minecraft.world.LightType;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/

import java.util.*;

// Scans an area for dark spawnable surfaces (block light 0) and places
// light sources to eliminate mob spawn points. Uses a greedy solver for
// optimal placement coverage.
//
// Lifecycle: configure corners + light source, call tick() every client tick.
public class SpawnProofer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpawnProofer.class);

    // State machine

    public enum State {
        IDLE,
        SCANNING,
        WALKING,
        PLACING,
        RESUPPLYING,
        RESTOCKING,
        RETURNING,
        PAUSED,
        DONE
    }

    private State state = State.IDLE;

    // Configuration

    /** Corners of the area to spawnproof (inclusive). */
    private BlockPos corner1;
    private BlockPos corner2;

    /** The light source block to place (default: torch). */
    private Block lightSource = Blocks.TORCH;

    /** The item form of the light source. */
    private Item lightSourceItem = Items.TORCH;

    /** Luminance emitted by the chosen light source. */
    private int lightSourceLuminance = 14;

    /** Replace the ground block with the light source instead of placing on top. */
    private boolean embedInGround = false;

    /** Light sources → luminance. */
    private static final Map<Block, Integer> KNOWN_LIGHT_SOURCES = new LinkedHashMap<>();
    static {
        KNOWN_LIGHT_SOURCES.put(Blocks.TORCH,                14);
        KNOWN_LIGHT_SOURCES.put(Blocks.LANTERN,              15);
        KNOWN_LIGHT_SOURCES.put(Blocks.GLOWSTONE,            15);
        KNOWN_LIGHT_SOURCES.put(Blocks.SEA_LANTERN,          15);
        KNOWN_LIGHT_SOURCES.put(Blocks.SHROOMLIGHT,          15);
        KNOWN_LIGHT_SOURCES.put(Blocks.JACK_O_LANTERN,       15);
        KNOWN_LIGHT_SOURCES.put(Blocks.SOUL_TORCH,           10);
        KNOWN_LIGHT_SOURCES.put(Blocks.SOUL_LANTERN,         10);
        KNOWN_LIGHT_SOURCES.put(Blocks.REDSTONE_LAMP,        15);
        KNOWN_LIGHT_SOURCES.put(Blocks.OCHRE_FROGLIGHT,      15);
        KNOWN_LIGHT_SOURCES.put(Blocks.VERDANT_FROGLIGHT,    15);
        KNOWN_LIGHT_SOURCES.put(Blocks.PEARLESCENT_FROGLIGHT,15);
    }

    // Runtime state

    /** Positions that are dark and spawnable — the remaining work queue. */
    private final List<BlockPos> darkSpots = new ArrayList<>();

    /** Positions where we've placed light sources. */
    private final Set<BlockPos> placedPositions = new HashSet<>();

    /** Best placement positions calculated by the solver. */
    private final Deque<BlockPos> placementQueue = new ArrayDeque<>();

    /** Position to return to after restocking. */
    private BlockPos returnPos;

    /** Queue head currently awaiting server confirmation. */
    private BlockPos pendingPlacementTarget;

    /** Actual world position of the placement awaiting confirmation. */
    private BlockPos pendingPlacementPos;

    /** Desired state for the placement awaiting confirmation. */
    private BlockState pendingPlacementState;

    /** Number of timeout-based retries for the current placement target. */
    private int pendingPlacementTimeouts;

    /** Queue target currently associated with timeout retries. */
    private BlockPos pendingTimeoutTarget;

    /** Timeout-reposition cycles for the current target. */
    private int pendingTimeoutCycles;

    /** Supply chest positions (reuses PrinterDatabase if available). */
    private final List<BlockPos> supplyChests = new ArrayList<>();

    /** Tick counter for throttling. */
    private int tickCounter;

    /** Total light sources placed this session. */
    private int totalPlaced;

    /** Whether the scanner has completed its initial pass. */
    private boolean scanComplete;

    /** Count of consecutive rescans that found the same dark-spot count (loop detection). */
    private int rescanCount;
    private int lastDarkSpotCount;

    /** Cooldown ticks between placements for rate limiting. */
    private static final int WALK_CHECK_INTERVAL = 5;

    /** Maximum reach distance for placement (vanilla: 4.5). */
    private static final double PLACE_REACH = 4.5;

    /** Number of consecutive ticks where placeBlock() returned false
     *  for the current queue head.  After a threshold, skip the position. */
    private int placeRetryTicks;
    private static final int MAX_PLACE_RETRIES = 40;

    /** Cooldown ticks after arriving at a position before the first placement.
     *  Gives the server time to acknowledge our position and reduces
     *  packet bursts that trigger Grim velocity setbacks. */
    private int placementSettleTicks;
    private static final int PLACEMENT_SETTLE_DELAY = 3;

    /** If the server rejects this many consecutive placements, auto-pause.
     *  Likely caused by cross-region rejection on Folia or anti-cheat. */
    private static final int REJECT_PAUSE_THRESHOLD = 6;

    /** Retry silent placement timeouts a couple of times before repositioning. */
    private static final int MAX_TIMEOUT_RETRIES = 2;

    /** Max timeout cycles before skipping target. */
    private static final int MAX_TIMEOUT_CYCLES = 3;

    // Public API

    /** Get current state. */
    public State getState() { return state; }

    /** Whether the proofer is actively running. */
    public boolean isActive() {
        return state != State.IDLE && state != State.DONE && state != State.PAUSED;
    }

    /** Set corner 1 of the area. */
    public void setCorner1(BlockPos pos) {
        this.corner1 = pos;
        saveConfig();
    }

    /** Set corner 2 of the area. */
    public void setCorner2(BlockPos pos) {
        this.corner2 = pos;
        saveConfig();
    }

    /** Get corner 1. */
    public BlockPos getCorner1() { return corner1; }

    /** Get corner 2. */
    public BlockPos getCorner2() { return corner2; }

    /**
     * Set the light source block to use.
     * Returns false if the block is not a recognized light source.
     */
    public boolean setLightSource(String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) return false;

        /*? if >=26.1 {*//*
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        *//*?} else {*/
        Block block = Registries.BLOCK.get(id);
        /*?}*/
        if (block == null || block == Blocks.AIR) return false;

        // Check luminance — must emit at least 1 light
        /*? if >=26.1 {*//*
        int lum = block.defaultBlockState().getLightEmission();
        *//*?} else {*/
        int lum = block.getDefaultState().getLuminance();
        /*?}*/
        if (lum <= 0) return false;

        this.lightSource = block;
        this.lightSourceItem = block.asItem();
        this.lightSourceLuminance = lum;
        // Auto-disable embed mode if new source can't be embedded
        if (embedInGround && !isFullBlockLightSource()) {
            embedInGround = false;
            ChatHelper.info("§eEmbed mode auto-disabled — " + getLightSourceName()
                    + " cannot be embedded.");
        }
        saveConfig();
        return true;
    }

    /**
     * Set light source by Block instance.
     */
    public void setLightSource(Block block) {
        this.lightSource = block;
        this.lightSourceItem = block.asItem();
        /*? if >=26.1 {*//*
        this.lightSourceLuminance = block.defaultBlockState().getLightEmission();
        *//*?} else {*/
        this.lightSourceLuminance = block.getDefaultState().getLuminance();
        /*?}*/
        // Auto-disable embed mode if new source can't be embedded
        if (embedInGround && !isFullBlockLightSource()) {
            embedInGround = false;
            ChatHelper.info("§eEmbed mode auto-disabled — " + getLightSourceName()
                    + " cannot be embedded.");
        }
        saveConfig();
    }

    /** Get the name of the current light source. */
    public String getLightSourceName() {
        /*? if >=26.1 {*//*
        return BuiltInRegistries.BLOCK.getKey(lightSource).getPath();
        *//*?} else {*/
        return Registries.BLOCK.getId(lightSource).getPath();
        /*?}*/
    }

    /** Get count of dark spots remaining. */
    public int getDarkSpotCount() { return darkSpots.size(); }

    /** Get count of placed light sources. */
    public int getTotalPlaced() { return totalPlaced; }

    /** Toggle embed-in-ground mode. */
    public void setEmbedInGround(boolean embed) {
        this.embedInGround = embed;
        saveConfig();
    }

    /** Whether embed-in-ground mode is active. */
    public boolean isEmbedInGround() { return embedInGround; }

    /**
     * Whether the current light source is a full block that can be embedded.
     * Torches and lanterns cannot be embedded.
     */
    public boolean isFullBlockLightSource() {
        return !(lightSource instanceof TorchBlock)
                && !(lightSource instanceof WallTorchBlock)
                && !(lightSource instanceof LanternBlock);
    }

    /** Add a supply chest position. */
    public void addSupplyChest(BlockPos pos) {
        if (!supplyChests.contains(pos)) {
            supplyChests.add(pos);
            saveSupplyChests();
        }
    }

    /** Remove a supply chest position. */
    public void removeSupplyChest(BlockPos pos) {
        if (supplyChests.remove(pos)) {
            saveSupplyChests();
        }
    }

    /** Get supply chest positions. */
    public List<BlockPos> getSupplyChests() {
        return Collections.unmodifiableList(supplyChests);
    }

    // Persistence

    /** Save spawnproofer config to the database. */
    private void saveConfig() {
        StashDatabase db = MoarMod.getDatabase();
        if (!db.isOpen()) return;
        if (corner1 != null) {
            db.setConfig("spawnproofer.corner1",
                    corner1.getX() + "," + corner1.getY() + "," + corner1.getZ());
        }
        if (corner2 != null) {
            db.setConfig("spawnproofer.corner2",
                    corner2.getX() + "," + corner2.getY() + "," + corner2.getZ());
        }
        /*? if >=26.1 {*//*
        db.setConfig("spawnproofer.lightSource",
                BuiltInRegistries.BLOCK.getKey(lightSource).toString());
        *//*?} else {*/
        db.setConfig("spawnproofer.lightSource",
                Registries.BLOCK.getId(lightSource).toString());
        /*?}*/
        db.setConfig("spawnproofer.embedInGround", String.valueOf(embedInGround));
    }

    /** Save spawnproofer supply chests to the database. */
    private void saveSupplyChests() {
        StashDatabase db = MoarMod.getDatabase();
        if (db.isOpen()) db.saveSpawnprooferSupply(supplyChests);
    }

    /** Load spawnproofer config and supply chests from the database. */
    public void loadConfig() {
        StashDatabase db = MoarMod.getDatabase();
        if (!db.isOpen()) return;

        String c1 = db.getConfig("spawnproofer.corner1");
        if (c1 != null) {
            String[] parts = c1.split(",");
            if (parts.length == 3) {
                corner1 = new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
            }
        }

        String c2 = db.getConfig("spawnproofer.corner2");
        if (c2 != null) {
            String[] parts = c2.split(",");
            if (parts.length == 3) {
                corner2 = new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
            }
        }

        String ls = db.getConfig("spawnproofer.lightSource");
        if (ls != null) {
            // Use the string-based setter (validates & sets item + luminance)
            setLightSource(ls);
        }

        String embed = db.getConfig("spawnproofer.embedInGround");
        if (embed != null) {
            this.embedInGround = Boolean.parseBoolean(embed);
        }

        List<BlockPos> saved = db.loadSpawnprooferSupply();
        if (!saved.isEmpty()) {
            supplyChests.clear();
            supplyChests.addAll(saved);
        }
    }

    // Lifecycle

    /**
     * Start spawnproofing the configured area.
     * Requires both corners to be set.
     */
    public boolean start() {
        if (corner1 == null || corner2 == null) {
            ChatHelper.info("§cSet both corners first: /spawnproof pos1 and /spawnproof pos2");
            return false;
        }

        darkSpots.clear();
        placedPositions.clear();
        placementQueue.clear();
        clearPendingPlacement();
        resetPlacementTimeoutTracking();
        PlacementEngine.reset();
        scanComplete = false;
        totalPlaced = 0;
        tickCounter = 0;
        rescanCount = 0;
        lastDarkSpotCount = -1;
        state = State.SCANNING;

        ChatHelper.info("§aSpawnProofer started. Scanning area...");
        return true;
    }

    /** Stop and reset. */
    public void stop() {
        PathWalker.stop();
        PlacementEngine.reset();
        clearPendingPlacement();
        resetPlacementTimeoutTracking();
        state = State.IDLE;
        darkSpots.clear();
        placedPositions.clear();
        placementQueue.clear();
        scanComplete = false;
        ChatHelper.info("§eSpawnProofer stopped.");
    }

    /** Pause — can be resumed. */
    public void pause() {
        if (isActive()) {
            PathWalker.stop();
            clearPendingPlacement();
            resetPlacementTimeoutTracking();
            PlacementEngine.reset();
            state = State.PAUSED;
            ChatHelper.info("§eSpawnProofer paused. " + darkSpots.size() + " dark spots remaining.");
        }
    }

    /** Resume from pause. */
    public void resume() {
        if (state == State.PAUSED) {
            state = scanComplete ? State.WALKING : State.SCANNING;
            ChatHelper.info("§aSpawnProofer resumed.");
        }
    }

    // Tick

    /**
     * Drive the state machine. Call every client tick.
     */
    public void tick() {
        if (state == State.IDLE || state == State.DONE || state == State.PAUSED) return;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc == null || mc.player == null || mc.level == null) return;
        *//*?} else {*/
        if (mc == null || mc.player == null || mc.world == null) return;
        /*?}*/

        tickCounter++;

        // Process the verification queue so rejected placements are tracked
        PlacementEngine.tickVerification();

        // Auto-pause if the server keeps rejecting placements (Folia cross-region, anti-cheat)
        if (PlacementEngine.getConsecutiveFailures() >= REJECT_PAUSE_THRESHOLD) {
            PlacementEngine.resetRejectionCounters();
            ChatHelper.info("§cServer failed to confirm " + REJECT_PAUSE_THRESHOLD
                    + " consecutive placements — pausing spawnproofer");
            pause();
            return;
        }

        switch (state) {
            case SCANNING    -> tickScanning(mc);
            case WALKING     -> tickWalking(mc);
            case PLACING     -> tickPlacing(mc);
            case RESUPPLYING -> tickResupplying(mc);
            case RESTOCKING  -> tickRestocking(mc);
            case RETURNING   -> tickReturning(mc);
            default -> {}
        }
    }

    // State handlers

    /**
     * Scan the area for dark spawnable positions.
     * Done in a single tick since it's just light level queries.
     */
    /*? if >=26.1 {*//*
    private void tickScanning(Minecraft mc) {
    *//*?} else {*/
    private void tickScanning(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY());

        // When the Y range is very small (e.g. both corners at foot level),
        // expand downward by 1 so we catch the solid floor block beneath
        // where the player is standing. isDarkSpawnable checks if 'pos' is
        // a solid surface — if the user placed both corners at their feet,
        // the actual floor is 1 below.
        if (maxY - minY <= 1) {
            minY -= 1;
        }

        BlockPos min = new BlockPos(
                Math.min(corner1.getX(), corner2.getX()),
                minY,
                Math.min(corner1.getZ(), corner2.getZ())
        );
        BlockPos max = new BlockPos(
                Math.max(corner1.getX(), corner2.getX()),
                maxY,
                Math.max(corner1.getZ(), corner2.getZ())
        );

        darkSpots.clear();

        // Scan all positions in the bounding box
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isDarkSpawnable(world, pos)) {
                        darkSpots.add(pos);
                    }
                }
            }
        }

        scanComplete = true;

        if (darkSpots.isEmpty()) {
            ChatHelper.info("§aArea is already fully lit! No dark spawnable spots found.");
            state = State.DONE;
            return;
        }

        // Loop detection: if we keep rescanning and finding the same count,
        // we're stuck (e.g. unreachable spots, no valid placement positions).
        if (darkSpots.size() == lastDarkSpotCount) {
            rescanCount++;
            if (rescanCount >= 3) {
                ChatHelper.info("§cStuck: " + darkSpots.size()
                        + " dark spots remain but no valid placement positions. Stopping.");
                state = State.DONE;
                return;
            }
        } else {
            rescanCount = 0;
            lastDarkSpotCount = darkSpots.size();
        }

        ChatHelper.info("§eFound §f" + darkSpots.size() + "§e dark spawnable spots. Planning placements...");

        // Run the greedy solver to find optimal light source positions
        solvePlacements(world);

        if (placementQueue.isEmpty()) {
            ChatHelper.info("§cCould not find valid positions for light sources.");
            state = State.DONE;
            return;
        }

        ChatHelper.info("§aNeed §f" + placementQueue.size() + "§a light sources. Starting placement...");
        state = State.WALKING;
    }

    /**
     * Walk toward the next placement position.
     */
    /*? if >=26.1 {*//*
    private void tickWalking(Minecraft mc) {
    *//*?} else {*/
    private void tickWalking(MinecraftClient mc) {
    /*?}*/
        if (placementQueue.isEmpty()) {
            // Rescan to check if we missed anything
            state = State.SCANNING;
            return;
        }

        BlockPos target = placementQueue.peek();
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        /*? if >=26.1 {*//*
        double distSq = player.distanceToSqr(
        *//*?} else {*/
        double distSq = player.squaredDistanceTo(
        /*?}*/
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (distSq <= PLACE_REACH * PLACE_REACH) {
            // Close enough to place — transition with settle cooldown
            state = State.PLACING;
            placementSettleTicks = PLACEMENT_SETTLE_DELAY;
            PathWalker.stop();
            return;
        }

        // Start walking if not already
        if (!PathWalker.isActive()) {
            PathWalker.walkToNearby(target, 3);
        }

        // Check arrival
        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            state = State.PLACING;
            placementSettleTicks = PLACEMENT_SETTLE_DELAY;
            return;
        }

        // Check if stuck
        if (PathWalker.isStuck()) {
            PathWalker.stop();
            // Skip this position and try the next one
            ChatHelper.info("§eSkipping unreachable position " + formatPos(target));
            placeRetryTicks = 0;
            placementQueue.poll();
            if (placementQueue.isEmpty()) {
                state = State.SCANNING;
            }
        }

        PathWalker.tick();
    }

    /**
     * Place a light source at the current target.
     *
     * The placementQueue contains final placement positions
     * (where the light source block goes), NOT dark-surface positions.
     * The greedy solver already validated each via canPlaceLightAt.
     */
    /*? if >=26.1 {*//*
    private void tickPlacing(Minecraft mc) {
    *//*?} else {*/
    private void tickPlacing(MinecraftClient mc) {
    /*?}*/
        if (PlacementEngine.isBusy()) {
            PlacementEngine.tick();
            return;
        }

        // Wait for settle cooldown after walking to reduce Grim velocity setbacks
        if (placementSettleTicks > 0) {
            placementSettleTicks--;
            return;
        }

        if (placementQueue.isEmpty()) {
            state = State.SCANNING;
            return;
        }

        BlockPos target = placementQueue.peek();
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        if (pendingPlacementPos != null && pendingPlacementState != null) {
            PlacementEngine.VerificationStatus status =
                    PlacementEngine.getVerificationStatus(pendingPlacementPos, pendingPlacementState);
            if (status == PlacementEngine.VerificationStatus.PENDING) {
                return;
            }
            if (status == PlacementEngine.VerificationStatus.ACCEPTED) {
                onPlacementConfirmed();
                return;
            }
            if (status == PlacementEngine.VerificationStatus.TIMEOUT) {
                onPlacementTimedOut();
                return;
            }
            onPlacementRejected();
            return;
        }

        // Check if this spot already has light (resolved by a nearby placement).
        // target IS the placement position — check light level right there.
        /*? if >=26.1 {*//*
        if (world.getBrightness(LightLayer.BLOCK, target) > 0) {
        *//*?} else {*/
        if (world.getLightLevel(LightType.BLOCK, target) > 0) {
        /*?}*/
            placeRetryTicks = 0;
            placementQueue.poll();
            if (placementQueue.isEmpty()) {
                state = State.SCANNING;
            } else {
                state = State.WALKING;
            }
            return;
        }

        // Check if we have the light source item in inventory
        if (!hasLightSourceInInventory(mc)) {
            if (supplyChests.isEmpty()) {
                ChatHelper.info("§cOut of " + getLightSourceName() + " and no supply chests configured.");
                pause();
                return;
            }
            // Save current position and go restock
            /*? if >=26.1 {*//*
            returnPos = mc.player.blockPosition();
            *//*?} else {*/
            returnPos = mc.player.getBlockPos();
            /*?}*/
            state = State.RESUPPLYING;
            return;
        }

        // Determine the actual placement position.
        // In embed mode with a full-block light source, the target IS the
        // dark surface block — PlacementEngine will break it and place the
        // light source via its correction pipeline.
        BlockPos placePos = target;
        boolean embedding = useEmbedMode();

        if (embedding) {
            // For embed mode: target is the surface block itself.
            BlockState surfaceState = world.getBlockState(placePos);
            if (surfaceState.getBlock() == lightSource) {
                // Already has our light source — skip
                placeRetryTicks = 0;
                placementQueue.poll();
                if (placementQueue.isEmpty()) {
                    state = State.SCANNING;
                } else {
                    state = State.WALKING;
                }
                return;
            }

            // If the block is still solid, we need to break it first.
            // PlacementEngine's correction pipeline will mine it. We do NOT
            // poll the queue — after breaking finishes the position becomes
            // air and the next tick will place the light source normally.
            /*? if >=26.1 {*//*
            if (!surfaceState.isAir() && !surfaceState.canBeReplaced()) {
            *//*?} else {*/
            if (!surfaceState.isAir() && !surfaceState.isReplaceable()) {
            /*?}*/
                /*? if >=26.1 {*//*
                double distSq = mc.player.distanceToSqr(
                *//*?} else {*/
                double distSq = mc.player.squaredDistanceTo(
                /*?}*/
                        placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5);
                if (distSq > PLACE_REACH * PLACE_REACH) {
                    state = State.WALKING;
                    return;
                }
                if (!PlacementEngine.canPlace()) return;
                /*? if >=26.1 {*//*
                BlockState desired = lightSource.defaultBlockState();
                *//*?} else {*/
                BlockState desired = lightSource.getDefaultState();
                /*?}*/
                // Start breaking — placeBlock will enter correction pipeline
                PlacementEngine.placeBlock(placePos, desired, true);
                // Stay in PLACING state; isBusy() gate at top will tick the breaker
                return;
            }
            // Block is now air/replaceable after breaking — fall through to place
        } else {
            // Normal mode: target is the air/replaceable block above the surface.
            if (!canPlaceLightAt(world, placePos)) {
                // Position invalidated — try to find a nearby alternative.
                /*? if >=26.1 {*//*
                placePos = findPlacementPosition(world, target.below());
                *//*?} else {*/
                placePos = findPlacementPosition(world, target.down());
                /*?}*/
                if (placePos == null) {
                    placeRetryTicks = 0;
                    placementQueue.poll();
                    if (placementQueue.isEmpty()) {
                        state = State.SCANNING;
                    } else {
                        state = State.WALKING;
                    }
                    return;
                }
            }
        }

        // Check reach
        /*? if >=26.1 {*//*
        double distSq = mc.player.distanceToSqr(
        *//*?} else {*/
        double distSq = mc.player.squaredDistanceTo(
        /*?}*/
                placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5);
        if (distSq > PLACE_REACH * PLACE_REACH) {
            state = State.WALKING;
            return;
        }

        if (!PlacementEngine.canPlace()) return;

        // Place the light source
        /*? if >=26.1 {*//*
        BlockState desired = lightSource.defaultBlockState();
        *//*?} else {*/
        BlockState desired = lightSource.getDefaultState();
        /*?}*/

        // For torches, determine wall vs floor placement
        if (!embedding && lightSource instanceof TorchBlock && !(lightSource instanceof WallTorchBlock)) {
            desired = determineTorchState(world, placePos);
        }

        if (PlacementEngine.placeBlock(placePos, desired, true)) {
            rememberPendingPlacement(target, placePos, desired);
            placeRetryTicks = 0;
            placementSettleTicks = PLACEMENT_SETTLE_DELAY;
        } else {
            placeRetryTicks++;
            if (placeRetryTicks >= MAX_PLACE_RETRIES) {
                LOGGER.debug("SpawnProofer: placement failed {} times at {}, skipping",
                        placeRetryTicks, placePos);
                placementQueue.poll();
                placeRetryTicks = 0;
                if (placementQueue.isEmpty()) {
                    state = State.SCANNING;
                } else {
                    state = State.WALKING;
                }
            }
        }
    }

    /**
     * Walk to nearest supply chest for more light sources.
     */
    /*? if >=26.1 {*//*
    private void tickResupplying(Minecraft mc) {
    *//*?} else {*/
    private void tickResupplying(MinecraftClient mc) {
    /*?}*/
        if (supplyChests.isEmpty()) {
            pause();
            return;
        }

        // Find nearest supply chest
        /*? if >=26.1 {*//*
        BlockPos nearest = findNearestChest(mc.player.blockPosition());
        *//*?} else {*/
        BlockPos nearest = findNearestChest(mc.player.getBlockPos());
        /*?}*/
        if (nearest == null) {
            ChatHelper.info("§cNo reachable supply chests.");
            pause();
            return;
        }

        if (!PathWalker.isActive()) {
            PathWalker.walkToAdjacent(nearest);
        }

        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            state = State.RESTOCKING;
            // The player needs to open the chest — this will be handled
            // by the restocking state which waits for a screen to open
            return;
        }

        if (PathWalker.isStuck()) {
            PathWalker.stop();
            ChatHelper.info("§eCan't reach supply chest at " + formatPos(nearest));
            supplyChests.remove(nearest);
            if (supplyChests.isEmpty()) {
                pause();
            }
            return;
        }

        PathWalker.tick();
    }

    /**
     * Wait for the player to open the chest and take items.
     * Auto-takes light source items from the chest.
     */
    /*? if >=26.1 {*//*
    private void tickRestocking(Minecraft mc) {
    *//*?} else {*/
    private void tickRestocking(MinecraftClient mc) {
    /*?}*/
        // For now, we just wait a bit and check if the player grabbed items
        // A full auto-restock implementation would interact with the chest screen
        if (tickCounter % 20 == 0) {
            if (hasLightSourceInInventory(mc)) {
                ChatHelper.info("§aRestocked. Returning to work area...");
                state = State.RETURNING;
            }
        }

        // Timeout after 10 seconds
        if (tickCounter % 200 == 0) {
            ChatHelper.info("§eRestock timeout. Please take " + getLightSourceName()
                    + " from the chest manually, or the proofer will resume.");
            if (hasLightSourceInInventory(mc)) {
                state = State.RETURNING;
            }
        }
    }

    /**
     * Return to the build area after restocking.
     */
    /*? if >=26.1 {*//*
    private void tickReturning(Minecraft mc) {
    *//*?} else {*/
    private void tickReturning(MinecraftClient mc) {
    /*?}*/
        if (returnPos == null) {
            state = State.WALKING;
            return;
        }

        if (!PathWalker.isActive()) {
            PathWalker.walkToNearby(returnPos, 3);
        }

        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            state = State.WALKING;
            return;
        }

        if (PathWalker.isStuck()) {
            PathWalker.stop();
            state = State.WALKING;
        }

        PathWalker.tick();
    }

    // Light level analysis

    /**
     * Check if a position is a dark, spawnable surface.
     *
     * A position is dark-spawnable if:
     * 1. The block at pos is solid and opaque (spawnable surface)
     * 2. The block above pos is air (space for mob)
     * 3. The block two above pos is air or passable (headroom)
     * 4. Block light level at pos+1 (where the mob stands) is 0
     */
    /*? if >=26.1 {*//*
    private boolean isDarkSpawnable(Level world, BlockPos pos) {
    *//*?} else {*/
    private boolean isDarkSpawnable(World world, BlockPos pos) {
    /*?}*/
        BlockState surface = world.getBlockState(pos);
        /*? if >=26.1 {*//*
        BlockState above = world.getBlockState(pos.above());
        *//*?} else {*/
        BlockState above = world.getBlockState(pos.up());
        /*?}*/
        /*? if >=26.1 {*//*
        BlockState above2 = world.getBlockState(pos.above(2));
        *//*?} else {*/
        BlockState above2 = world.getBlockState(pos.up(2));
        /*?}*/

        // Surface must be solid and opaque on top
        /*? if >=26.1 {*//*
        if (!surface.isSolidRender()) return false;
        *//*?} else {*/
        if (!surface.isSolidBlock(world, pos)) return false;
        /*?}*/

        // Space above must be empty
        /*? if >=26.1 {*//*
        if (!above.isAir() && above.getCollisionShape(world, pos.above()).isEmpty() == false) return false;
        *//*?} else {*/
        if (!above.isAir() && above.getCollisionShape(world, pos.up()).isEmpty() == false) return false;
        /*?}*/
        /*? if >=26.1 {*//*
        if (!above2.isAir() && above2.getCollisionShape(world, pos.above(2)).isEmpty() == false) return false;
        *//*?} else {*/
        if (!above2.isAir() && above2.getCollisionShape(world, pos.up(2)).isEmpty() == false) return false;
        /*?}*/

        // Check block light level at mob-standing position (one above surface)
        /*? if >=26.1 {*//*
        int blockLight = world.getBrightness(LightLayer.BLOCK, pos.above());
        *//*?} else {*/
        int blockLight = world.getLightLevel(LightType.BLOCK, pos.up());
        /*?}*/

        // In the Overworld since 1.18, monsters spawn only at block light 0
        return blockLight == 0;
    }

    /**
     * Check if a position can receive a light source.
     * The position must be air or replaceable vegetation (short grass, ferns, etc.)
     * and have proper support for the light source type.
     */
    /*? if >=26.1 {*//*
    private boolean canPlaceLightAt(Level world, BlockPos pos) {
    *//*?} else {*/
    private boolean canPlaceLightAt(World world, BlockPos pos) {
    /*?}*/
        BlockState current = world.getBlockState(pos);
        // Accept air or replaceable blocks (short grass, tall grass, ferns, flowers, etc.)
        // Minecraft allows placing blocks where replaceable vegetation exists.
        /*? if >=26.1 {*//*
        if (!current.isAir() && !current.canBeReplaced()) return false;
        *//*?} else {*/
        if (!current.isAir() && !current.isReplaceable()) return false;
        /*?}*/

        // For torches: need solid surface below or to the side
        if (lightSource instanceof TorchBlock) {
            // Floor torch needs solid below
            /*? if >=26.1 {*//*
            BlockState below = world.getBlockState(pos.below());
            *//*?} else {*/
            BlockState below = world.getBlockState(pos.down());
            /*?}*/
            /*? if >=26.1 {*//*
            if (below.isFaceSturdy(world, pos.below(), Direction.UP)) {
            *//*?} else {*/
            if (below.isSideSolidFullSquare(world, pos.down(), Direction.UP)) {
            /*?}*/
                return true;
            }
            // Wall torch needs solid wall adjacent
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH,
                    Direction.EAST, Direction.WEST}) {
                /*? if >=26.1 {*//*
                BlockState neighbor = world.getBlockState(pos.relative(dir));
                *//*?} else {*/
                BlockState neighbor = world.getBlockState(pos.offset(dir));
                /*?}*/
                /*? if >=26.1 {*//*
                if (neighbor.isFaceSturdy(world, pos.relative(dir), dir.getOpposite())) {
                *//*?} else {*/
                if (neighbor.isSideSolidFullSquare(world, pos.offset(dir), dir.getOpposite())) {
                /*?}*/
                    return true;
                }
            }
            return false;
        }

        // For lanterns: can hang from ceiling or sit on floor
        if (lightSource instanceof LanternBlock) {
            /*? if >=26.1 {*//*
            BlockState below = world.getBlockState(pos.below());
            *//*?} else {*/
            BlockState below = world.getBlockState(pos.down());
            /*?}*/
            /*? if >=26.1 {*//*
            BlockState above = world.getBlockState(pos.above());
            *//*?} else {*/
            BlockState above = world.getBlockState(pos.up());
            /*?}*/
            /*? if >=26.1 {*//*
            return below.isFaceSturdy(world, pos.below(), Direction.UP)
            *//*?} else {*/
            return below.isSideSolidFullSquare(world, pos.down(), Direction.UP)
            /*?}*/
                    /*? if >=26.1 {*//*
                    || above.isFaceSturdy(world, pos.above(), Direction.DOWN);
                    *//*?} else {*/
                    || above.isSideSolidFullSquare(world, pos.up(), Direction.DOWN);
                    /*?}*/
        }

        // For full blocks (glowstone, sea lantern, shroomlight, etc.):
        // just needs to be air
        return true;
    }

    /**
     * Find the best position to place a light source to cover a dark spot.
     * Prefers placing ON TOP of the spawnable surface (pos.up()).
     */
    /*? if >=26.1 {*//*
    private BlockPos findPlacementPosition(Level world, BlockPos darkSurface) {
    *//*?} else {*/
    private BlockPos findPlacementPosition(World world, BlockPos darkSurface) {
    /*?}*/
        // Best case: place directly on top of the dark surface
        /*? if >=26.1 {*//*
        BlockPos onTop = darkSurface.above();
        *//*?} else {*/
        BlockPos onTop = darkSurface.up();
        /*?}*/
        if (canPlaceLightAt(world, onTop)) return onTop;

        // Try adjacent positions on the same Y level
        /*? if >=26.1 {*//*
        for (Direction dir : Direction.Plane.HORIZONTAL) {
        *//*?} else {*/
        for (Direction dir : Direction.Type.HORIZONTAL) {
        /*?}*/
            /*? if >=26.1 {*//*
            BlockPos adj = onTop.relative(dir);
            *//*?} else {*/
            BlockPos adj = onTop.offset(dir);
            /*?}*/
            if (canPlaceLightAt(world, adj)) return adj;
        }

        // Try one block up (wall mount, etc.)
        /*? if >=26.1 {*//*
        BlockPos higher = onTop.above();
        *//*?} else {*/
        BlockPos higher = onTop.up();
        /*?}*/
        if (canPlaceLightAt(world, higher)) return higher;

        return null;
    }

    /**
     * Determine the correct torch blockstate (floor vs wall).
     */
    /*? if >=26.1 {*//*
    private BlockState determineTorchState(Level world, BlockPos pos) {
    *//*?} else {*/
    private BlockState determineTorchState(World world, BlockPos pos) {
    /*?}*/
        // Prefer floor placement
        /*? if >=26.1 {*//*
        BlockState below = world.getBlockState(pos.below());
        *//*?} else {*/
        BlockState below = world.getBlockState(pos.down());
        /*?}*/
        /*? if >=26.1 {*//*
        if (below.isFaceSturdy(world, pos.below(), Direction.UP)) {
        *//*?} else {*/
        if (below.isSideSolidFullSquare(world, pos.down(), Direction.UP)) {
        /*?}*/
            /*? if >=26.1 {*//*
            return Blocks.TORCH.defaultBlockState();
            *//*?} else {*/
            return Blocks.TORCH.getDefaultState();
            /*?}*/
        }

        // Try wall placement
        Block wallTorch = (lightSource == Blocks.SOUL_TORCH)
                ? Blocks.SOUL_WALL_TORCH : Blocks.WALL_TORCH;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST}) {
            /*? if >=26.1 {*//*
            BlockState neighbor = world.getBlockState(pos.relative(dir));
            *//*?} else {*/
            BlockState neighbor = world.getBlockState(pos.offset(dir));
            /*?}*/
            /*? if >=26.1 {*//*
            if (neighbor.isFaceSturdy(world, pos.relative(dir), dir.getOpposite())) {
            *//*?} else {*/
            if (neighbor.isSideSolidFullSquare(world, pos.offset(dir), dir.getOpposite())) {
            /*?}*/
                /*? if >=26.1 {*//*
                return wallTorch.defaultBlockState().setValue(WallTorchBlock.FACING, dir.getOpposite());
                *//*?} else {*/
                return wallTorch.getDefaultState().with(WallTorchBlock.FACING, dir.getOpposite());
                /*?}*/
            }
        }

        /*? if >=26.1 {*//*
        return lightSource.defaultBlockState();
        *//*?} else {*/
        return lightSource.getDefaultState();
        /*?}*/
    }

    // Greedy solver

    /**
     * Linear sweep solver: iterate dark spots, place a light source for
     * each uncovered spot, and predict coverage to skip nearby spots.
     *
     * Much faster than greedy set-cover (O(n × p) vs O(n² × p) where
     * n = dark spots, p = placements).  Produces slightly more placements
     * than optimal, but a verification rescan catches any residual dark
     * spots and adds a small follow-up pass.
     */
    /*? if >=26.1 {*//*
    private void solvePlacements(Level world) {
    *//*?} else {*/
    private void solvePlacements(World world) {
    /*?}*/
        placementQueue.clear();

        boolean embedding = useEmbedMode();

        if (darkSpots.isEmpty()) return;

        // Coverage radius: a light source with luminance L keeps block light > 0
        // up to taxicab distance L-1.  Subtract 2 extra as a conservative margin
        // that accounts for 1-2 blocks of terrain occlusion.
        int radius = Math.max(1, lightSourceLuminance - 2);

        // Track which dark spots are predicted to be illuminated by queued
        // placements.  Keyed by packed (x, y, z) long for O(1) lookup.
        Set<Long> covered = new HashSet<>();
        Set<Long> addedPositions = new HashSet<>();

        for (BlockPos dark : darkSpots) {
            if (covered.contains(packPos(dark))) continue;

            // Find a valid placement position
            BlockPos placePos;
            if (embedding) {
                placePos = placedPositions.contains(dark) ? null : dark;
            } else {
                placePos = findPlacementPosition(world, dark);
            }
            if (placePos == null || placedPositions.contains(placePos)) continue;

            // Avoid duplicates (adjacent dark spots can resolve to the same placement)
            long placeKey = packPos(placePos);
            if (!addedPositions.add(placeKey)) continue;

            placementQueue.add(placePos);

            // Predict coverage: mark all dark spots within radius as covered.
            // Instead of iterating all dark spots, enumerate the diamond volume
            // around the placement and mark every position in the covered set.
            // This is O(radius³) per placement (~1500 for radius 12) — much
            // faster than scanning all dark spots.
            for (int dx = -radius + 1; dx < radius; dx++) {
                int rem = radius - 1 - Math.abs(dx);
                for (int dz = -rem; dz <= rem; dz++) {
                    int remY = rem - Math.abs(dz);
                    for (int dy = -remY; dy <= remY; dy++) {
                        // Dark surface is 1 below the standing pos that the
                        // torch illuminates, so offset Y by -1 when embedding
                        // is off (placement is at standing level).
                        int darkY = embedding
                                ? placePos.getY() + dy
                                : placePos.getY() + dy - 1;
                        covered.add(packPos(
                                placePos.getX() + dx,
                                darkY,
                                placePos.getZ() + dz));
                    }
                }
            }
        }

        // Sort the queue by distance from player for efficiency
        /*? if >=26.1 {*//*
        LocalPlayer player = Minecraft.getInstance().player;
        *//*?} else {*/
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        /*?}*/
        if (player != null) {
            List<BlockPos> sorted = new ArrayList<>(placementQueue);
            /*? if >=26.1 {*//*
            BlockPos playerPos = player.blockPosition();
            *//*?} else {*/
            BlockPos playerPos = player.getBlockPos();
            /*?}*/
            /*? if >=26.1 {*//*
            sorted.sort(Comparator.comparingDouble(p -> p.distSqr(playerPos)));
            *//*?} else {*/
            sorted.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(playerPos)));
            /*?}*/
            placementQueue.clear();
            placementQueue.addAll(sorted);
        }
    }

    /** Pack a BlockPos into a long key for set lookups. */
    private static long packPos(BlockPos pos) {
        return packPos(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Pack x/y/z into a long key for set lookups. */
    private static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFF);
    }

    /** Taxicab (Manhattan) distance between two positions. */
    private int taxicabDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    /**
     * Whether embed-in-ground mode is effectively active.
     * True only when the user enabled embed mode AND the current light
     * source is a full block (torches/lanterns can't be embedded).
     */
    private boolean useEmbedMode() {
        return embedInGround && isFullBlockLightSource();
    }

    // Inventory helpers

    /**
     * Check if the player has at least one light source item.
     */
    /*? if >=26.1 {*//*
    private boolean hasLightSourceInInventory(Minecraft mc) {
    *//*?} else {*/
    private boolean hasLightSourceInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return false;
        /*? if >=26.1 {*//*
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
        *//*?} else {*/
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (mc.player.getInventory().getItem(i).getItem() == lightSourceItem) {
            *//*?} else {*/
            if (mc.player.getInventory().getStack(i).getItem() == lightSourceItem) {
            /*?}*/
                return true;
            }
        }
        return false;
    }

    /**
     * Count how many light source items the player has.
     */
    /*? if >=26.1 {*//*
    public int countLightSourceInInventory(Minecraft mc) {
    *//*?} else {*/
    public int countLightSourceInInventory(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return 0;
        int count = 0;
        /*? if >=26.1 {*//*
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
        *//*?} else {*/
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (mc.player.getInventory().getItem(i).getItem() == lightSourceItem) {
            *//*?} else {*/
            if (mc.player.getInventory().getStack(i).getItem() == lightSourceItem) {
            /*?}*/
                /*? if >=26.1 {*//*
                count += mc.player.getInventory().getItem(i).getCount();
                *//*?} else {*/
                count += mc.player.getInventory().getStack(i).getCount();
                /*?}*/
            }
        }
        return count;
    }

    // Supply chest

    /**
     * Find the nearest supply chest.
     */
    private BlockPos findNearestChest(BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos chest : supplyChests) {
            /*? if >=26.1 {*//*
            double dist = chest.distSqr(from);
            *//*?} else {*/
            double dist = chest.getSquaredDistance(from);
            /*?}*/
            if (dist < bestDist) {
                bestDist = dist;
                best = chest;
            }
        }
        return best;
    }

    // Utility

    private void rememberPendingPlacement(BlockPos queueTarget, BlockPos placePos, BlockState desired) {
        if (pendingTimeoutTarget == null || !pendingTimeoutTarget.equals(queueTarget)) {
            resetPlacementTimeoutTracking();
        }
        /*? if >=26.1 {*//*
        pendingPlacementTarget = queueTarget.immutable();
        pendingPlacementPos = placePos.immutable();
        *//*?} else {*/
        pendingPlacementTarget = queueTarget.toImmutable();
        pendingPlacementPos = placePos.toImmutable();
        /*?}*/
        pendingPlacementState = desired;
    }

    private void clearPendingPlacement() {
        if (pendingPlacementPos != null) {
            PlacementEngine.clearVerificationStatus(pendingPlacementPos);
        }
        pendingPlacementTarget = null;
        pendingPlacementPos = null;
        pendingPlacementState = null;
    }

    private void onPlacementConfirmed() {
        BlockPos placedPos = pendingPlacementPos;
        BlockPos queueTarget = pendingPlacementTarget;
        clearPendingPlacement();

        if (placedPos != null) {
            placedPositions.add(placedPos);
        }
        if (queueTarget != null) {
            if (!placementQueue.isEmpty() && queueTarget.equals(placementQueue.peek())) {
                placementQueue.poll();
            } else {
                placementQueue.removeFirstOccurrence(queueTarget);
            }
        }

        totalPlaced++;
        resetPlacementTimeoutTracking();
        placeRetryTicks = 0;
        if (placementQueue.isEmpty()) {
            state = State.SCANNING;
        } else {
            state = State.WALKING;
        }
    }

    private void onPlacementRejected() {
        BlockPos failedPos = pendingPlacementPos;
        BlockPos queueTarget = pendingPlacementTarget;
        clearPendingPlacement();

        resetPlacementTimeoutTracking();
        placementSettleTicks = PLACEMENT_SETTLE_DELAY;
        placeRetryTicks++;
        if (placeRetryTicks >= MAX_PLACE_RETRIES) {
            LOGGER.debug("SpawnProofer: server rejected placement {} times at {}, skipping",
                    placeRetryTicks, failedPos);
            if (queueTarget != null) {
                if (!placementQueue.isEmpty() && queueTarget.equals(placementQueue.peek())) {
                    placementQueue.poll();
                } else {
                    placementQueue.removeFirstOccurrence(queueTarget);
                }
            }
            placeRetryTicks = 0;
            if (placementQueue.isEmpty()) {
                state = State.SCANNING;
            } else {
                state = State.WALKING;
            }
        }
    }

    private void onPlacementTimedOut() {
        BlockPos failedPos = pendingPlacementPos;
        BlockPos queueTarget = pendingPlacementTarget;
        clearPendingPlacement();

        if (queueTarget != null && (pendingTimeoutTarget == null || !pendingTimeoutTarget.equals(queueTarget))) {
            /*? if >=26.1 {*//*
            pendingTimeoutTarget = queueTarget.immutable();
            *//*?} else {*/
            pendingTimeoutTarget = queueTarget.toImmutable();
            /*?}*/
            pendingPlacementTimeouts = 0;
            pendingTimeoutCycles = 0;
        }
        placeRetryTicks = 0;
        pendingPlacementTimeouts++;
        placementSettleTicks = PLACEMENT_SETTLE_DELAY * 2;
        if (pendingPlacementTimeouts < MAX_TIMEOUT_RETRIES) {
            LOGGER.debug("SpawnProofer: placement confirmation timed out at {}, retrying ({}/{})",
                    failedPos, pendingPlacementTimeouts, MAX_TIMEOUT_RETRIES);
            return;
        }

        pendingPlacementTimeouts = 0;
        pendingTimeoutCycles++;
        if (pendingTimeoutCycles >= MAX_TIMEOUT_CYCLES) {
            LOGGER.debug("SpawnProofer: placement confirmation timed out for {} cycles at {}, skipping",
                    pendingTimeoutCycles, failedPos);
            if (queueTarget != null) {
                if (!placementQueue.isEmpty() && queueTarget.equals(placementQueue.peek())) {
                    placementQueue.poll();
                } else {
                    placementQueue.removeFirstOccurrence(queueTarget);
                }
            }
            resetPlacementTimeoutTracking();
            if (placementQueue.isEmpty()) {
                state = State.SCANNING;
            } else {
                state = State.WALKING;
            }
            return;
        }

        LOGGER.debug("SpawnProofer: placement confirmation timed out for cycle {} at {}, repositioning",
                pendingTimeoutCycles, failedPos);
        state = State.WALKING;
    }

    private void resetPlacementTimeoutTracking() {
        pendingPlacementTimeouts = 0;
        pendingTimeoutCycles = 0;
        pendingTimeoutTarget = null;
    }

    /** Format a position for display. */
    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** Get a status summary string. */
    public String getStatus() {
        String embedTag = useEmbedMode() ? " [embed]" : "";
        return switch (state) {
            case IDLE -> "Idle";
            case SCANNING -> "Scanning area...";
            case WALKING -> "Walking to next spot (" + placementQueue.size() + " remaining)" + embedTag;
            case PLACING -> (useEmbedMode() ? "Embedding " : "Placing ") + getLightSourceName()
                    + " (" + placementQueue.size() + " remaining)";
            case RESUPPLYING -> "Walking to supply chest...";
            case RESTOCKING -> "Restocking " + getLightSourceName() + "...";
            case RETURNING -> "Returning to work area...";
            case PAUSED -> "Paused — " + placementQueue.size() + " placements remaining" + embedTag;
            case DONE -> "Done! Placed " + totalPlaced + " light sources.";
        };
    }

    /** Get known light source blocks. */
    public static Set<Block> getKnownLightSources() {
        return Collections.unmodifiableSet(KNOWN_LIGHT_SOURCES.keySet());
    }
}

package dev.smartmatica.spawnproof;

import dev.smartmatica.SmartmaticaMod;
import dev.smartmatica.util.ChatHelper;
import dev.smartmatica.util.PathWalker;
import dev.smartmatica.util.PlacementEngine;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.*;

/**
 * SpawnProofer — scans an area for blocks where hostile mobs can spawn
 * (block light level 0 on a solid, spawnable surface) and automatically
 * places user-selected light sources to eliminate dark spots.
 *
 * Since 1.18, hostile mobs only spawn at block light level 0 in the
 * Overworld. A single torch (luminance 14) covers a 14-block taxicab
 * radius, but occlusion from walls/ceilings reduces effective range.
 * This module queries the client world's lighting engine directly to
 * find dark, spawnable positions and greedily places light sources to
 * cover the largest number of remaining dark spots.
 *
 * Lifecycle: create instance, configure via setters, call {@link #tick()}
 * every client tick while active.
 */
public class SpawnProofer {

    // ── State machine ───────────────────────────────────────────────────

    public enum State {
        /** Idle — not running. */
        IDLE,
        /** Scanning the area for dark spawnable positions. */
        SCANNING,
        /** Walking to next placement target. */
        WALKING,
        /** Placing a light source block. */
        PLACING,
        /** Walking to supply chest for more light sources. */
        RESUPPLYING,
        /** Taking items from the supply chest screen. */
        RESTOCKING,
        /** Returning to the build area after restocking. */
        RETURNING,
        /** Paused — user can resume. */
        PAUSED,
        /** Done — area fully lit. */
        DONE
    }

    private State state = State.IDLE;

    // ── Configuration ───────────────────────────────────────────────────

    /** Corners of the area to spawnproof (inclusive). */
    private BlockPos corner1;
    private BlockPos corner2;

    /** The light source block to place (default: torch). */
    private Block lightSource = Blocks.TORCH;

    /** The item form of the light source. */
    private Item lightSourceItem = Items.TORCH;

    /** Luminance emitted by the chosen light source. */
    private int lightSourceLuminance = 14;

    /**
     * When true and the light source is a full block, replace the dark
     * surface block itself ("embed in ground") rather than placing on top.
     * Useful for glowstone, sea lanterns, froglights, etc. — the light
     * source becomes part of the floor.
     */
    private boolean embedInGround = false;

    /**
     * Light sources and their luminance values.
     * Used for greedy coverage calculation.
     */
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

    // ── Runtime state ───────────────────────────────────────────────────

    /** Positions that are dark and spawnable — the remaining work queue. */
    private final List<BlockPos> darkSpots = new ArrayList<>();

    /** Positions where we've placed light sources. */
    private final Set<BlockPos> placedPositions = new HashSet<>();

    /** Best placement positions calculated by the solver. */
    private final Deque<BlockPos> placementQueue = new ArrayDeque<>();

    /** Position to return to after restocking. */
    private BlockPos returnPos;

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

    // ── Public API ──────────────────────────────────────────────────────

    /** Get current state. */
    public State getState() { return state; }

    /** Whether the proofer is actively running. */
    public boolean isActive() {
        return state != State.IDLE && state != State.DONE && state != State.PAUSED;
    }

    /** Set corner 1 of the area. */
    public void setCorner1(BlockPos pos) { this.corner1 = pos; }

    /** Set corner 2 of the area. */
    public void setCorner2(BlockPos pos) { this.corner2 = pos; }

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

        Block block = Registries.BLOCK.get(id);
        if (block == null || block == Blocks.AIR) return false;

        // Check luminance — must emit at least 1 light
        int lum = block.getDefaultState().getLuminance();
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
        return true;
    }

    /**
     * Set light source by Block instance.
     */
    public void setLightSource(Block block) {
        this.lightSource = block;
        this.lightSourceItem = block.asItem();
        this.lightSourceLuminance = block.getDefaultState().getLuminance();
        // Auto-disable embed mode if new source can't be embedded
        if (embedInGround && !isFullBlockLightSource()) {
            embedInGround = false;
            ChatHelper.info("§eEmbed mode auto-disabled — " + getLightSourceName()
                    + " cannot be embedded.");
        }
    }

    /** Get the name of the current light source. */
    public String getLightSourceName() {
        return Registries.BLOCK.getId(lightSource).getPath();
    }

    /** Get count of dark spots remaining. */
    public int getDarkSpotCount() { return darkSpots.size(); }

    /** Get count of placed light sources. */
    public int getTotalPlaced() { return totalPlaced; }

    /** Toggle embed-in-ground mode. */
    public void setEmbedInGround(boolean embed) { this.embedInGround = embed; }

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
        if (!supplyChests.contains(pos)) supplyChests.add(pos);
    }

    /** Remove a supply chest position. */
    public void removeSupplyChest(BlockPos pos) {
        supplyChests.remove(pos);
    }

    /** Get supply chest positions. */
    public List<BlockPos> getSupplyChests() {
        return Collections.unmodifiableList(supplyChests);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

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

    // ── Tick ────────────────────────────────────────────────────────────

    /**
     * Drive the state machine. Call every client tick.
     */
    public void tick() {
        if (state == State.IDLE || state == State.DONE || state == State.PAUSED) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        tickCounter++;

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

    // ── State handlers ──────────────────────────────────────────────────

    /**
     * Scan the area for dark spawnable positions.
     * Done in a single tick since it's just light level queries.
     */
    private void tickScanning(MinecraftClient mc) {
        World world = mc.world;
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
    private void tickWalking(MinecraftClient mc) {
        if (placementQueue.isEmpty()) {
            // Rescan to check if we missed anything
            state = State.SCANNING;
            return;
        }

        BlockPos target = placementQueue.peek();
        ClientPlayerEntity player = mc.player;

        double distSq = player.squaredDistanceTo(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (distSq <= PLACE_REACH * PLACE_REACH) {
            // Close enough to place — transition
            state = State.PLACING;
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
            return;
        }

        // Check if stuck
        if (PathWalker.isStuck()) {
            PathWalker.stop();
            // Skip this position and try the next one
            ChatHelper.info("§eSkipping unreachable position " + formatPos(target));
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
     * The {@code placementQueue} contains final placement positions
     * (where the light source block goes), NOT dark-surface positions.
     * The greedy solver already validated each via {@link #canPlaceLightAt}.
     */
    private void tickPlacing(MinecraftClient mc) {
        if (PlacementEngine.isBusy()) {
            PlacementEngine.tick();
            return;
        }

        if (placementQueue.isEmpty()) {
            state = State.SCANNING;
            return;
        }

        BlockPos target = placementQueue.peek();
        World world = mc.world;

        // Check if this spot already has light (resolved by a nearby placement).
        // target IS the placement position — check light level right there.
        if (world.getLightLevel(LightType.BLOCK, target) > 0) {
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
            returnPos = mc.player.getBlockPos();
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
            // Verify it's still solid (hasn't already been replaced).
            BlockState surfaceState = world.getBlockState(placePos);
            if (surfaceState.getBlock() == lightSource) {
                // Already has our light source — skip
                placementQueue.poll();
                if (placementQueue.isEmpty()) {
                    state = State.SCANNING;
                } else {
                    state = State.WALKING;
                }
                return;
            }
        } else {
            // Normal mode: target is the air/replaceable block above the surface.
            if (!canPlaceLightAt(world, placePos)) {
                // Position invalidated — try to find a nearby alternative.
                placePos = findPlacementPosition(world, target.down());
                if (placePos == null) {
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
        double distSq = mc.player.squaredDistanceTo(
                placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5);
        if (distSq > PLACE_REACH * PLACE_REACH) {
            state = State.WALKING;
            return;
        }

        if (!PlacementEngine.canPlace()) return;

        // Place the light source
        BlockState desired = lightSource.getDefaultState();

        // For torches, determine wall vs floor placement
        if (!embedding && lightSource instanceof TorchBlock && !(lightSource instanceof WallTorchBlock)) {
            desired = determineTorchState(world, placePos);
        }

        if (PlacementEngine.placeBlock(placePos, desired, true)) {
            placedPositions.add(placePos);
            placementQueue.poll();
            totalPlaced++;
            PlacementEngine.recordPlacement();

            if (placementQueue.isEmpty()) {
                // Do another scan to verify
                state = State.SCANNING;
            } else {
                state = State.WALKING;
            }
        }
    }

    /**
     * Walk to nearest supply chest for more light sources.
     */
    private void tickResupplying(MinecraftClient mc) {
        if (supplyChests.isEmpty()) {
            pause();
            return;
        }

        // Find nearest supply chest
        BlockPos nearest = findNearestChest(mc.player.getBlockPos());
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
    private void tickRestocking(MinecraftClient mc) {
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
    private void tickReturning(MinecraftClient mc) {
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

    // ── Light level analysis ────────────────────────────────────────────

    /**
     * Check if a position is a dark, spawnable surface.
     *
     * A position is dark-spawnable if:
     * 1. The block at pos is solid and opaque (spawnable surface)
     * 2. The block above pos is air (space for mob)
     * 3. The block two above pos is air or passable (headroom)
     * 4. Block light level at pos+1 (where the mob stands) is 0
     */
    private boolean isDarkSpawnable(World world, BlockPos pos) {
        BlockState surface = world.getBlockState(pos);
        BlockState above = world.getBlockState(pos.up());
        BlockState above2 = world.getBlockState(pos.up(2));

        // Surface must be solid and opaque on top
        if (!surface.isSolidBlock(world, pos)) return false;

        // Space above must be empty
        if (!above.isAir() && above.getCollisionShape(world, pos.up()).isEmpty() == false) return false;
        if (!above2.isAir() && above2.getCollisionShape(world, pos.up(2)).isEmpty() == false) return false;

        // Check block light level at mob-standing position (one above surface)
        int blockLight = world.getLightLevel(LightType.BLOCK, pos.up());

        // In the Overworld since 1.18, monsters spawn only at block light 0
        return blockLight == 0;
    }

    /**
     * Check if a position can receive a light source.
     * The position must be air or replaceable vegetation (short grass, ferns, etc.)
     * and have proper support for the light source type.
     */
    private boolean canPlaceLightAt(World world, BlockPos pos) {
        BlockState current = world.getBlockState(pos);
        // Accept air or replaceable blocks (short grass, tall grass, ferns, flowers, etc.)
        // Minecraft allows placing blocks where replaceable vegetation exists.
        if (!current.isAir() && !current.isReplaceable()) return false;

        // For torches: need solid surface below or to the side
        if (lightSource instanceof TorchBlock) {
            // Floor torch needs solid below
            BlockState below = world.getBlockState(pos.down());
            if (below.isSideSolidFullSquare(world, pos.down(), Direction.UP)) {
                return true;
            }
            // Wall torch needs solid wall adjacent
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH,
                    Direction.EAST, Direction.WEST}) {
                BlockState neighbor = world.getBlockState(pos.offset(dir));
                if (neighbor.isSideSolidFullSquare(world, pos.offset(dir), dir.getOpposite())) {
                    return true;
                }
            }
            return false;
        }

        // For lanterns: can hang from ceiling or sit on floor
        if (lightSource instanceof LanternBlock) {
            BlockState below = world.getBlockState(pos.down());
            BlockState above = world.getBlockState(pos.up());
            return below.isSideSolidFullSquare(world, pos.down(), Direction.UP)
                    || above.isSideSolidFullSquare(world, pos.up(), Direction.DOWN);
        }

        // For full blocks (glowstone, sea lantern, shroomlight, etc.):
        // just needs to be air
        return true;
    }

    /**
     * Find the best position to place a light source to cover a dark spot.
     * Prefers placing ON TOP of the spawnable surface (pos.up()).
     */
    private BlockPos findPlacementPosition(World world, BlockPos darkSurface) {
        // Best case: place directly on top of the dark surface
        BlockPos onTop = darkSurface.up();
        if (canPlaceLightAt(world, onTop)) return onTop;

        // Try adjacent positions on the same Y level
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos adj = onTop.offset(dir);
            if (canPlaceLightAt(world, adj)) return adj;
        }

        // Try one block up (wall mount, etc.)
        BlockPos higher = onTop.up();
        if (canPlaceLightAt(world, higher)) return higher;

        return null;
    }

    /**
     * Determine the correct torch blockstate (floor vs wall).
     */
    private BlockState determineTorchState(World world, BlockPos pos) {
        // Prefer floor placement
        BlockState below = world.getBlockState(pos.down());
        if (below.isSideSolidFullSquare(world, pos.down(), Direction.UP)) {
            return Blocks.TORCH.getDefaultState();
        }

        // Try wall placement
        Block wallTorch = (lightSource == Blocks.SOUL_TORCH)
                ? Blocks.SOUL_WALL_TORCH : Blocks.WALL_TORCH;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST}) {
            BlockState neighbor = world.getBlockState(pos.offset(dir));
            if (neighbor.isSideSolidFullSquare(world, pos.offset(dir), dir.getOpposite())) {
                return wallTorch.getDefaultState().with(WallTorchBlock.FACING, dir.getOpposite());
            }
        }

        return lightSource.getDefaultState();
    }

    // ── Greedy solver ───────────────────────────────────────────────────

    /**
     * Greedy set-cover solver: find the minimum set of light source
     * positions that illuminate all dark spots.
     *
     * Algorithm:
     * 1. For each candidate position, compute how many dark spots it would cover
     * 2. Pick the candidate that covers the most dark spots
     * 3. Remove covered spots from the set, repeat
     *
     * A light source at position P with luminance L covers a dark spot D
     * if the taxicab distance |Px-Dx| + |Py-Dy| + |Pz-Dz| is less than L.
     * This is a simplification — actual light propagation is affected by
     * occlusion. We use a conservative radius of L-1 to account for one
     * layer of occlusion.
     */
    private void solvePlacements(World world) {
        placementQueue.clear();

        boolean embedding = useEmbedMode();

        Set<BlockPos> remaining = new HashSet<>(darkSpots);
        Set<BlockPos> candidates = new LinkedHashSet<>();

        if (embedding) {
            // Embed mode: candidates are the dark surface blocks themselves.
            // The light source replaces the ground block.
            for (BlockPos dark : darkSpots) {
                if (!placedPositions.contains(dark)) {
                    candidates.add(dark);
                }
            }
        } else {
            // Normal mode: candidates are air/replaceable positions above or
            // adjacent to the dark surface.
            for (BlockPos dark : darkSpots) {
                BlockPos onTop = dark.up();
                if (!placedPositions.contains(onTop) && canPlaceLightAt(world, onTop))
                    candidates.add(onTop);

                for (Direction dir : Direction.Type.HORIZONTAL) {
                    BlockPos adj = onTop.offset(dir);
                    if (!placedPositions.contains(adj) && canPlaceLightAt(world, adj))
                        candidates.add(adj);
                }
            }
        }

        if (candidates.isEmpty()) return;

        // Conservative coverage radius — real light propagation is reduced by
        // terrain occlusion. Use luminance/2 to avoid over-estimating and
        // placing too few lights.
        int radius = Math.max(1, lightSourceLuminance / 2);

        // Greedy set cover
        while (!remaining.isEmpty() && !candidates.isEmpty()) {
            BlockPos bestCandidate = null;
            int bestCoverage = 0;

            for (BlockPos cand : candidates) {
                int coverage = 0;
                for (BlockPos dark : remaining) {
                    // For embed mode the light source is at the surface level,
                    // but light level is checked at dark.up() (where the mob
                    // stands).  For normal mode the candidate IS at dark.up()
                    // level already.  Either way, compare candidate to the
                    // mob-standing position.
                    BlockPos standPos = dark.up();
                    int dist = taxicabDistance(cand, standPos);
                    if (dist < radius) {
                        coverage++;
                    }
                }
                if (coverage > bestCoverage) {
                    bestCoverage = coverage;
                    bestCandidate = cand;
                }
            }

            if (bestCandidate == null || bestCoverage == 0) break;

            placementQueue.add(bestCandidate);
            candidates.remove(bestCandidate);

            // Remove covered dark spots
            Iterator<BlockPos> it = remaining.iterator();
            while (it.hasNext()) {
                BlockPos dark = it.next();
                if (taxicabDistance(bestCandidate, dark.up()) < radius) {
                    it.remove();
                }
            }
        }

        // Any remaining dark spots that couldn't be covered:
        // add direct placements for them
        for (BlockPos dark : remaining) {
            BlockPos pos;
            if (embedding) {
                pos = placedPositions.contains(dark) ? null : dark;
            } else {
                pos = findPlacementPosition(world, dark);
            }
            if (pos != null) {
                placementQueue.add(pos);
            }
        }

        // Sort the queue by distance from player for efficiency
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            List<BlockPos> sorted = new ArrayList<>(placementQueue);
            BlockPos playerPos = player.getBlockPos();
            sorted.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(playerPos)));
            placementQueue.clear();
            placementQueue.addAll(sorted);
        }
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

    // ── Inventory helpers ───────────────────────────────────────────────

    /**
     * Check if the player has at least one light source item.
     */
    private boolean hasLightSourceInInventory(MinecraftClient mc) {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == lightSourceItem) {
                return true;
            }
        }
        return false;
    }

    /**
     * Count how many light source items the player has.
     */
    public int countLightSourceInInventory(MinecraftClient mc) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == lightSourceItem) {
                count += mc.player.getInventory().getStack(i).getCount();
            }
        }
        return count;
    }

    // ── Supply chest ────────────────────────────────────────────────────

    /**
     * Find the nearest supply chest.
     */
    private BlockPos findNearestChest(BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos chest : supplyChests) {
            double dist = chest.getSquaredDistance(from);
            if (dist < bestDist) {
                bestDist = dist;
                best = chest;
            }
        }
        return best;
    }

    // ── Utility ─────────────────────────────────────────────────────────

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

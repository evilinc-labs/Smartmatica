package dev.moar.stash;

import dev.moar.chest.ChestManager;
import dev.moar.util.ChatHelper;
import dev.moar.util.PathWalker;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Automatic stash inventory scanner.
 *
 * <p>The user defines a stash region with {@code /stash pos1} and
 * {@code /stash pos2}.  The scanner finds all containers (chests,
 * barrels, shulker boxes, hoppers) inside the region, walks to each
 * using Baritone, opens it, reads its contents — including nested
 * shulker box inventories — and builds a full index.</p>
 *
 * <p>If the region is larger than the client's render distance, the
 * scanner breaks the work into <em>zones</em> and uses incremental
 * waypoint walking (the same technique the printer uses for megabase
 * builds) to cover the full area progressively.</p>
 *
 * <h3>State machine</h3>
 * <pre>
 * IDLE → ZONE_SCANNING → WALKING → OPENING → READING → (next, or WALKING_TO_ZONE → repeat)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 *   /stash pos1 [x y z]     — set corner 1 (default: player position)
 *   /stash pos2 [x y z]     — set corner 2
 *   /stash scan              — start scanning the defined region
 *   /stash stop              — abort scanning
 *   /stash status            — show index summary
 *   /stash export            — export CSV
 *   /stash clear             — clear the index
 * </pre>
 */
public final class StashManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Stash");

    // ── State machine ───────────────────────────────────────────────────

    public enum State {
        IDLE,
        /** Scanning loaded chunks in the region for containers. */
        ZONE_SCANNING,
        /** Walking to the next container. */
        WALKING,
        /** Opening a container. */
        OPENING,
        /** Reading the open container's contents. */
        READING,
        /** Walking to the next unscanned zone (incremental). */
        WALKING_TO_ZONE,
        /** All containers in the region have been scanned. */
        DONE
    }

    private State state = State.IDLE;

    // ── Region corners ──────────────────────────────────────────────────

    /** User-set corner 1 of the stash region. */
    private BlockPos corner1;
    /** User-set corner 2 of the stash region. */
    private BlockPos corner2;

    /** Computed inclusive minimum corner of the region. */
    private BlockPos regionMin;
    /** Computed inclusive maximum corner of the region. */
    private BlockPos regionMax;

    // ── Configuration ───────────────────────────────────────────────────

    /** Maximum number of containers to scan per session. */
    private static final int MAX_CONTAINERS = 2048;

    /** Ticks to wait for the chest screen to appear. */
    private static final int OPEN_TIMEOUT_TICKS = 60;

    /** Ticks to stay on the reading screen to ensure contents are loaded. */
    private static final int READ_DELAY_TICKS = 5;

    /** Leg length for linear waypoint interpolation (blocks). */
    private static final int WAYPOINT_LEG_LENGTH = 48;

    // ── Runtime state ───────────────────────────────────────────────────

    /** Discovered container positions (sorted by distance). */
    private final Deque<BlockPos> scanQueue = new ArrayDeque<>();

    /** Positions we've already visited (avoids double-counting). */
    private final Set<BlockPos> visitedPositions = new HashSet<>();

    /** Full stash index: container position → inventory snapshot. */
    private final Map<BlockPos, ContainerEntry> index = new LinkedHashMap<>();

    /** Chunks we've already scanned for containers (packed cx|cz). */
    private final Set<Long> scannedChunks = new HashSet<>();

    /** Current container being visited. */
    private BlockPos currentTarget;

    /** Ticks spent waiting for screen to open. */
    private int openWaitTicks;

    /** Ticks spent on reading screen. */
    private int readWaitTicks;

    /** Total containers found during all zone scans combined. */
    private int totalFound;

    /** Total containers successfully indexed. */
    private int totalIndexed;

    /** Containers skipped (unreachable, failed to open). */
    private int totalSkipped;

    /** Whether a render-distance warning was shown this session. */
    private boolean warnedRenderDistance;

    // ── Data types ──────────────────────────────────────────────────────

    /**
     * An entry in the stash index representing a single container.
     */
    public record ContainerEntry(
            BlockPos pos,
            String blockType,
            boolean isDouble,
            Map<String, Integer> items,
            int shulkerCount,
            List<ShulkerDetail> shulkerDetails,
            long timestamp
    ) {
        public int itemTypeCount() { return items.size(); }
        public int totalItemCount() {
            return items.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Detail record for a single shulker box found inside a container. */
    public record ShulkerDetail(
            String shulkerType,
            Map<String, Integer> contents
    ) {}

    // ── Public API — corners ────────────────────────────────────────────

    public void setCorner1(BlockPos pos) { this.corner1 = pos; }
    public void setCorner2(BlockPos pos) { this.corner2 = pos; }
    public BlockPos getCorner1() { return corner1; }
    public BlockPos getCorner2() { return corner2; }

    // ── Public API — state ──────────────────────────────────────────────

    public State getState() { return state; }

    public boolean isActive() {
        return state != State.IDLE && state != State.DONE;
    }

    public Map<BlockPos, ContainerEntry> getIndex() {
        return Collections.unmodifiableMap(index);
    }

    public int getIndexedCount() { return index.size(); }
    public int getRemainingCount() { return scanQueue.size(); }

    // ── Lifecycle ───────────────────────────────────────────────────────

    /**
     * Start scanning the region defined by corner1 and corner2.
     */
    public boolean start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            ChatHelper.labelled("Stash", "§cNot in a world.");
            return false;
        }
        if (corner1 == null || corner2 == null) {
            ChatHelper.labelled("Stash", "§cSet both corners first: §f/stash pos1 §cand §f/stash pos2");
            return false;
        }

        // Compute inclusive region bounds
        regionMin = new BlockPos(
                Math.min(corner1.getX(), corner2.getX()),
                Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ()));
        regionMax = new BlockPos(
                Math.max(corner1.getX(), corner2.getX()),
                Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ()));

        // Reset runtime state
        scanQueue.clear();
        visitedPositions.clear();
        index.clear();
        scannedChunks.clear();
        currentTarget = null;
        totalFound = 0;
        totalIndexed = 0;
        totalSkipped = 0;
        warnedRenderDistance = false;

        int sizeX = regionMax.getX() - regionMin.getX() + 1;
        int sizeY = regionMax.getY() - regionMin.getY() + 1;
        int sizeZ = regionMax.getZ() - regionMin.getZ() + 1;

        ChatHelper.labelled("Stash", "§aScanning region §f"
                + sizeX + "×" + sizeY + "×" + sizeZ
                + "§a (from §f" + regionMin.getX() + " " + regionMin.getY() + " " + regionMin.getZ()
                + "§a to §f" + regionMax.getX() + " " + regionMax.getY() + " " + regionMax.getZ() + "§a)...");

        // Check for unloaded chunks in the region → warn
        if (hasUnloadedChunks(mc.world)) {
            ChatHelper.labelled("Stash", "§6\u26A0 Region extends beyond render distance. "
                    + "The scanner will walk incrementally to cover all areas, "
                    + "but results may be inaccurate until the full region is visited.");
            warnedRenderDistance = true;
        }

        state = State.ZONE_SCANNING;
        return true;
    }

    /** Stop scanning and reset. */
    public void stop() {
        PathWalker.stop();
        state = State.IDLE;
        scanQueue.clear();
        currentTarget = null;
        ChatHelper.labelled("Stash", "§eScan stopped. "
                + totalIndexed + " containers indexed so far.");
    }

    /** Clear the index entirely. */
    public void clearIndex() {
        index.clear();
        totalIndexed = 0;
        totalSkipped = 0;
        totalFound = 0;
        ChatHelper.labelled("Stash", "§eStash index cleared.");
    }

    // ── Tick ────────────────────────────────────────────────────────────

    public void tick() {
        if (state == State.IDLE || state == State.DONE) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        switch (state) {
            case ZONE_SCANNING   -> tickZoneScanning(mc);
            case WALKING         -> tickWalking(mc);
            case OPENING         -> tickOpening(mc);
            case READING         -> tickReading(mc);
            case WALKING_TO_ZONE -> tickWalkingToZone(mc);
            default -> {}
        }
    }

    // ── State handlers ──────────────────────────────────────────────────

    /**
     * Scan all <em>loaded</em> chunks within the region for containers.
     * Chunks we've already scanned in a previous pass are skipped.
     */
    private void tickZoneScanning(MinecraftClient mc) {
        World world = mc.world;
        List<BlockPos> containers = new ArrayList<>();

        int minCX = regionMin.getX() >> 4;
        int maxCX = regionMax.getX() >> 4;
        int minCZ = regionMin.getZ() >> 4;
        int maxCZ = regionMax.getZ() >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                long packed = packChunk(cx, cz);
                if (scannedChunks.contains(packed)) continue;
                if (!world.isChunkLoaded(cx, cz)) continue;

                scannedChunks.add(packed);

                // Scan this chunk, clamped to region bounds
                int startX = Math.max(cx << 4, regionMin.getX());
                int endX   = Math.min((cx << 4) + 15, regionMax.getX());
                int startZ = Math.max(cz << 4, regionMin.getZ());
                int endZ   = Math.min((cz << 4) + 15, regionMax.getZ());

                for (int x = startX; x <= endX; x++) {
                    for (int z = startZ; z <= endZ; z++) {
                        for (int y = regionMin.getY(); y <= regionMax.getY(); y++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState blockState = world.getBlockState(pos);
                            Block block = blockState.getBlock();

                            if (isContainer(block)) {
                                if (block instanceof ChestBlock) {
                                    BlockPos partner = getDoubleChestPartner(pos, blockState);
                                    if (partner != null && visitedPositions.contains(partner)) {
                                        continue;
                                    }
                                }
                                if (!visitedPositions.contains(pos)) {
                                    containers.add(pos.toImmutable());
                                    visitedPositions.add(pos.toImmutable());
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!containers.isEmpty()) {
            // Sort by distance from player
            BlockPos playerPos = mc.player.getBlockPos();
            containers.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(playerPos)));

            // Cap at MAX
            if (containers.size() > MAX_CONTAINERS - totalFound) {
                containers = containers.subList(0, MAX_CONTAINERS - totalFound);
            }

            scanQueue.addAll(containers);
            totalFound += containers.size();

            ChatHelper.labelled("Stash", "§aFound §f" + containers.size()
                    + "§a containers in loaded chunks"
                    + " (§f" + totalFound + "§a total). Walking to each...");
            advanceToNext();
            return;
        }

        // No new containers in loaded chunks — check for unscanned zones
        BlockPos nextZone = findUnscannedZone(mc);
        if (nextZone != null) {
            startWalkingToZone(mc, nextZone);
        } else {
            finishScan();
        }
    }

    /** Walk toward the current target container. */
    private void tickWalking(MinecraftClient mc) {
        if (currentTarget == null) {
            advanceToNext();
            return;
        }

        ClientPlayerEntity player = mc.player;
        double distSq = player.squaredDistanceTo(
                currentTarget.getX() + 0.5,
                currentTarget.getY() + 0.5,
                currentTarget.getZ() + 0.5);

        if (distSq <= 4.5 * 4.5) {
            PathWalker.stop();
            state = State.OPENING;
            openWaitTicks = 0;
            return;
        }

        if (!PathWalker.isActive()) {
            PathWalker.walkToAdjacent(currentTarget);
        }

        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            state = State.OPENING;
            openWaitTicks = 0;
            return;
        }

        if (PathWalker.isStuck()) {
            PathWalker.stop();
            LOGGER.debug("Skipping unreachable container at {}", currentTarget);
            totalSkipped++;
            advanceToNext();
        }

        PathWalker.tick();
    }

    /** Open the container at currentTarget. */
    private void tickOpening(MinecraftClient mc) {
        openWaitTicks++;

        // Check if a container screen opened (chests/barrels/shulkers or hoppers)
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler instanceof GenericContainerScreenHandler
                || handler instanceof HopperScreenHandler) {
            readWaitTicks = 0;
            state = State.READING;
            return;
        }

        // Send interact on tick 1
        if (openWaitTicks == 1) {
            mc.interactionManager.interactBlock(
                    mc.player,
                    mc.player.getActiveHand(),
                    new net.minecraft.util.hit.BlockHitResult(
                            Vec3d.ofCenter(currentTarget),
                            Direction.UP,
                            currentTarget,
                            false
                    )
            );
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            LOGGER.debug("Timeout opening container at {}", currentTarget);
            totalSkipped++;
            advanceToNext();
        }
    }

    /** Read the contents of the currently open container screen. */
    private void tickReading(MinecraftClient mc) {
        readWaitTicks++;
        if (readWaitTicks < READ_DELAY_TICKS) return;

        ScreenHandler handler = mc.player.currentScreenHandler;

        int containerSlots;
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
            containerSlots = containerHandler.getRows() * 9;
        } else if (handler instanceof HopperScreenHandler) {
            containerSlots = 5;  // hoppers always have 5 slots
        } else {
            // Screen closed unexpectedly
            totalSkipped++;
            advanceToNext();
            return;
        }

        // Read contents
        Map<String, Integer> items = new HashMap<>();
        List<ShulkerDetail> shulkerDetails = new ArrayList<>();
        int shulkerCount = 0;

        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            if (ChestManager.isShulkerBox(stack)) {
                shulkerCount++;
                Map<String, Integer> shulkerContents = ChestManager.readShulkerContents(stack);
                String shulkerType = itemId.replace("minecraft:", "");
                shulkerDetails.add(new ShulkerDetail(shulkerType, Map.copyOf(shulkerContents)));

                for (var entry : shulkerContents.entrySet()) {
                    items.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                items.merge(itemId, stack.getCount(), Integer::sum);
            } else {
                items.merge(itemId, stack.getCount(), Integer::sum);
            }
        }

        // Record container metadata
        BlockState blockState = mc.world.getBlockState(currentTarget);
        String blockType = Registries.BLOCK.getId(blockState.getBlock()).toString();
        boolean isDouble = blockState.getBlock() instanceof ChestBlock
                && blockState.contains(Properties.CHEST_TYPE)
                && blockState.get(Properties.CHEST_TYPE) != ChestType.SINGLE;

        ContainerEntry entry = new ContainerEntry(
                currentTarget, blockType, isDouble,
                Map.copyOf(items), shulkerCount,
                List.copyOf(shulkerDetails),
                System.currentTimeMillis());

        index.put(currentTarget, entry);
        totalIndexed++;

        // Close the screen
        mc.player.closeHandledScreen();

        if (totalIndexed % 10 == 0) {
            ChatHelper.labelled("Stash", "§7Indexed "
                    + totalIndexed + "/" + totalFound + " containers...");
        }

        advanceToNext();
    }

    /**
     * Walk toward the next unscanned zone using incremental waypoints.
     * This mirrors the printer's megabase waypoint pattern.
     */
    private void tickWalkingToZone(MinecraftClient mc) {
        if (PathWalker.hasArrived() || !PathWalker.isActive()) {
            PathWalker.stop();
            // Arrived at zone — rescan for newly loaded containers
            state = State.ZONE_SCANNING;
            return;
        }

        if (PathWalker.isStuck()) {
            PathWalker.stop();
            // Try the next unscanned zone instead
            BlockPos nextZone = findUnscannedZone(mc);
            if (nextZone != null) {
                startWalkingToZone(mc, nextZone);
            } else {
                finishScan();
            }
            return;
        }

        PathWalker.tick();
    }

    // ── Navigation ──────────────────────────────────────────────────────

    /** Advance to the next container, or to the next zone, or finish. */
    private void advanceToNext() {
        if (!scanQueue.isEmpty()) {
            currentTarget = scanQueue.poll();
            state = State.WALKING;
            return;
        }

        // Queue exhausted — check for more unscanned zones
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) {
            finishScan();
            return;
        }

        BlockPos nextZone = findUnscannedZone(mc);
        if (nextZone != null) {
            startWalkingToZone(mc, nextZone);
        } else {
            finishScan();
        }
    }

    /**
     * Compute linear waypoints from the player to the target zone center
     * and start walking via PathWalker.
     */
    private void startWalkingToZone(MinecraftClient mc, BlockPos zoneCenter) {
        BlockPos from = mc.player.getBlockPos();
        List<BlockPos> waypoints = computeLinearWaypoints(from, zoneCenter, WAYPOINT_LEG_LENGTH);

        ChatHelper.labelled("Stash", "§7Walking to unscanned zone at §f"
                + zoneCenter.getX() + " " + zoneCenter.getY() + " " + zoneCenter.getZ()
                + " §7(" + waypoints.size() + " waypoint"
                + (waypoints.size() != 1 ? "s" : "") + ")...");

        PathWalker.walkToViaWaypoints(waypoints, 8);
        state = State.WALKING_TO_ZONE;
    }

    /**
     * Find the nearest chunk in the region that hasn't been scanned yet.
     * Returns the center of that chunk (at region min Y), or null if
     * all chunks have been scanned.
     */
    private BlockPos findUnscannedZone(MinecraftClient mc) {
        if (regionMin == null || regionMax == null) return null;

        int minCX = regionMin.getX() >> 4;
        int maxCX = regionMax.getX() >> 4;
        int minCZ = regionMin.getZ() >> 4;
        int maxCZ = regionMax.getZ() >> 4;

        /*? if >=1.21.10 {*//*
        Vec3d playerPos = mc.player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = mc.player.getPos();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                long packed = packChunk(cx, cz);
                if (scannedChunks.contains(packed)) continue;

                // Center of this chunk, clamped to region Y
                int centerX = (cx << 4) + 8;
                int centerZ = (cz << 4) + 8;
                int centerY = regionMin.getY();

                double dist = playerPos.squaredDistanceTo(
                        centerX + 0.5, centerY + 0.5, centerZ + 0.5);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new BlockPos(centerX, centerY, centerZ);
                }
            }
        }

        return best;
    }

    /** End the scan and print summary. */
    private void finishScan() {
        state = State.DONE;
        ChatHelper.labelled("Stash", "§aScan complete! Indexed §f"
                + totalIndexed + "§a containers ("
                + totalSkipped + " skipped).");

        int totalItems = index.values().stream()
                .mapToInt(ContainerEntry::totalItemCount).sum();
        int totalTypes = index.values().stream()
                .flatMap(e -> e.items().keySet().stream())
                .collect(Collectors.toSet()).size();
        int totalShulkers = index.values().stream()
                .mapToInt(ContainerEntry::shulkerCount).sum();

        ChatHelper.labelled("Stash", "§7Total: §f" + totalItems
                + "§7 items, §f" + totalTypes
                + "§7 types, §f" + totalShulkers + "§7 shulker boxes inspected.");
        ChatHelper.labelled("Stash", "§7Use §f/stash export§7 to save CSV report.");
    }

    // ── Region helpers ──────────────────────────────────────────────────

    /** Check if any chunks in the region are currently unloaded. */
    private boolean hasUnloadedChunks(World world) {
        int minCX = regionMin.getX() >> 4;
        int maxCX = regionMax.getX() >> 4;
        int minCZ = regionMin.getZ() >> 4;
        int maxCZ = regionMax.getZ() >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) return true;
            }
        }
        return false;
    }

    /**
     * Compute linear-interpolation waypoints from → to.
     * Same algorithm as SchematicPrinter's megabase waypoint logic.
     */
    private static List<BlockPos> computeLinearWaypoints(BlockPos from, BlockPos to, int legLength) {
        List<BlockPos> waypoints = new ArrayList<>();
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist <= legLength) {
            waypoints.add(to.toImmutable());
            return waypoints;
        }

        int legs = (int) Math.ceil(dist / legLength);
        for (int i = 1; i <= legs; i++) {
            double t = (double) i / legs;
            int wx = from.getX() + (int) (dx * t);
            int wy = from.getY() + (int) (dy * t);
            int wz = from.getZ() + (int) (dz * t);
            waypoints.add(new BlockPos(wx, wy, wz));
        }
        waypoints.set(waypoints.size() - 1, to.toImmutable());
        return waypoints;
    }

    /** Pack chunk coordinates into a single long for set storage. */
    private static long packChunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // ── CSV export ──────────────────────────────────────────────────────

    /**
     * Export the current stash index to a CSV file.
     *
     * @return the path to the created CSV file, or null on error
     */
    public Path exportCsv() {
        if (index.isEmpty()) {
            ChatHelper.labelled("Stash", "§cNo stash data to export. Run /stash scan first.");
            return null;
        }

        Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir()
                .resolve("moar");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
            return null;
        }

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path csvPath = configDir.resolve("stash_report_" + timestamp + ".csv");

        try (Writer writer = Files.newBufferedWriter(csvPath)) {
            writer.write("Container X,Container Y,Container Z,"
                    + "Block Type,Double Chest,"
                    + "Item ID,Item Name,Quantity,"
                    + "In Shulker,Shulker Color\n");

            for (ContainerEntry container : index.values()) {
                String posX = String.valueOf(container.pos().getX());
                String posY = String.valueOf(container.pos().getY());
                String posZ = String.valueOf(container.pos().getZ());
                String blockType = container.blockType().replace("minecraft:", "");
                String isDouble = container.isDouble() ? "Yes" : "No";

                // Compute shulker-contained totals to separate from direct items
                Map<String, Integer> shulkerAggregate = new HashMap<>();
                for (ShulkerDetail sd : container.shulkerDetails()) {
                    for (var entry : sd.contents().entrySet()) {
                        shulkerAggregate.merge(entry.getKey(), entry.getValue(), Integer::sum);
                    }
                }

                // Direct items
                for (var entry : container.items().entrySet()) {
                    String itemId = entry.getKey();
                    int totalQty = entry.getValue();
                    int shulkerQty = shulkerAggregate.getOrDefault(itemId, 0);
                    int directQty = totalQty - shulkerQty;

                    if (directQty > 0) {
                        String itemName = formatItemName(itemId);
                        writer.write(posX + "," + posY + "," + posZ + ","
                                + csvEscape(blockType) + "," + isDouble + ","
                                + csvEscape(itemId) + "," + csvEscape(itemName) + ","
                                + directQty + ","
                                + "No,\n");
                    }
                }

                // Shulker-contained items
                for (ShulkerDetail sd : container.shulkerDetails()) {
                    for (var entry : sd.contents().entrySet()) {
                        String itemId = entry.getKey();
                        int qty = entry.getValue();
                        String itemName = formatItemName(itemId);
                        writer.write(posX + "," + posY + "," + posZ + ","
                                + csvEscape(blockType) + "," + isDouble + ","
                                + csvEscape(itemId) + "," + csvEscape(itemName) + ","
                                + qty + ","
                                + "Yes," + csvEscape(sd.shulkerType()) + "\n");
                    }
                }
            }

            ChatHelper.labelled("Stash", "§aExported §f" + index.size()
                    + "§a containers to §f" + csvPath.getFileName());
            return csvPath;

        } catch (IOException e) {
            LOGGER.error("Failed to export stash CSV", e);
            ChatHelper.labelled("Stash", "§cFailed to write CSV: " + e.getMessage());
            return null;
        }
    }

    // ── Status ──────────────────────────────────────────────────────────

    public String getStatus() {
        return switch (state) {
            case IDLE -> "Idle";
            case ZONE_SCANNING -> "Scanning loaded chunks for containers...";
            case WALKING -> "Walking to container ("
                    + (totalFound - scanQueue.size()) + "/" + totalFound + ")";
            case OPENING -> "Opening container...";
            case READING -> "Reading contents...";
            case WALKING_TO_ZONE -> "Walking to next unscanned zone...";
            case DONE -> "Done \u2014 " + totalIndexed + " containers indexed"
                    + (totalSkipped > 0 ? " (" + totalSkipped + " skipped)" : "");
        };
    }

    public String getDetailedSummary() {
        if (index.isEmpty()) return "No data. Run /stash scan first.";

        int totalItems = index.values().stream()
                .mapToInt(ContainerEntry::totalItemCount).sum();
        int totalTypes = index.values().stream()
                .flatMap(e -> e.items().keySet().stream())
                .collect(Collectors.toSet()).size();
        int totalShulkers = index.values().stream()
                .mapToInt(ContainerEntry::shulkerCount).sum();
        int doubleChests = (int) index.values().stream()
                .filter(ContainerEntry::isDouble).count();

        return totalIndexed + " containers (" + doubleChests + " double chests), "
                + totalItems + " items, "
                + totalTypes + " types, "
                + totalShulkers + " shulker boxes";
    }

    /** Get a region size string for status display. */
    public String getRegionInfo() {
        if (corner1 == null || corner2 == null) return "No region defined.";
        int sizeX = Math.abs(corner1.getX() - corner2.getX()) + 1;
        int sizeY = Math.abs(corner1.getY() - corner2.getY()) + 1;
        int sizeZ = Math.abs(corner1.getZ() - corner2.getZ()) + 1;
        return sizeX + "x" + sizeY + "x" + sizeZ + " blocks";
    }

    // ── Utility ─────────────────────────────────────────────────────────

    /** Check if a block is a container we should scan. */
    private static boolean isContainer(Block block) {
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof HopperBlock;
    }

    /** For double chests, get the partner position. Returns null for single chests. */
    private static BlockPos getDoubleChestPartner(BlockPos pos, BlockState state) {
        if (!state.contains(Properties.CHEST_TYPE)) return null;
        ChestType type = state.get(Properties.CHEST_TYPE);
        if (type == ChestType.SINGLE) return null;

        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        Direction partnerDir;
        if (type == ChestType.LEFT) {
            partnerDir = facing.rotateYClockwise();
        } else {
            partnerDir = facing.rotateYCounterclockwise();
        }
        return pos.offset(partnerDir);
    }

    /** Format an item ID into a human-readable name. */
    private static String formatItemName(String itemId) {
        String name = itemId.replace("minecraft:", "");
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /** Escape a value for CSV. */
    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

package dev.moar.chest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.moar.util.ChatHelper;
import dev.moar.util.PathWalker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified manager for all chest-related operations in MOAR.
 *
 * Consolidates:
 *   - Supply chest registration and persistence (positions saved to JSON)
 *   - Inventory snapshot caching (in-memory, populated when player opens chests)
 *   - Chest scanning / indexing (reads chest and shulker box contents)
 *   - Best-chest ranking for resupply (match count, distance, indexed vs unindexed)
 *   - Storage chest sorting (deposit planning, type assignments, overflow)
 *
 * This class is a singleton held in MoarMod and ticked every
 * client tick to drive the sorting state machine.
 */
public final class ChestManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/ChestManager");

    // ═══════════════════════════════════════════════════════════════════
    //  SUPPLY CHEST INDEX — positions (persisted) + snapshots (ephemeral)
    // ═══════════════════════════════════════════════════════════════════

    /** Registered supply-chest positions. Persisted to disk. */
    private final Set<BlockPos> supplyPositions = new LinkedHashSet<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SUPPLY_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moar")
            .resolve("printer_supply.json");

    // ── Supply chest registration ───────────────────────────────────────

    /** Register a supply chest position. Returns false if already registered. */
    public boolean addSupplyChest(BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        if (!supplyPositions.add(immutable)) return false;
        saveSupplyChests();
        return true;
    }

    /** Unregister a supply chest position. */
    public boolean removeSupplyChest(BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        boolean removed = supplyPositions.remove(immutable);
        if (removed) {
            snapshots.remove(immutable);
            saveSupplyChests();
        }
        return removed;
    }

    /** Remove all supply chest registrations and their snapshots. */
    public void clearSupplyChests() {
        supplyPositions.clear();
        snapshots.clear();
        saveSupplyChests();
    }

    /** Unmodifiable snapshot of all registered supply-chest positions. */
    public List<BlockPos> getSupplyPositions() {
        return List.copyOf(supplyPositions);
    }

    /** Number of registered supply chests. */
    public int supplyChestCount() {
        return supplyPositions.size();
    }

    // ── Inventory snapshots (in-memory) ─────────────────────────────────

    /**
     * Snapshot of a single supply chest's contents at the time it was last
     * opened.  Includes items directly in the chest and items found inside
     * shulker boxes within it.
     */
    public record ChestSnapshot(
            BlockPos pos,
            /** Item ID to total count (direct + shulker contents). */
            Map<String, Integer> items,
            /** Number of shulker boxes found in this chest. */
            int shulkerCount,
            /** System.currentTimeMillis() when this snapshot was taken. */
            long timestamp
    ) {
        public boolean contains(String itemId) {
            return items.containsKey(itemId);
        }

        public int getCount(String itemId) {
            return items.getOrDefault(itemId, 0);
        }

        /** Seconds since this snapshot was taken. */
        public long ageSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }
    }

    /** BlockPos to last-known inventory snapshot.  Capped at 256 entries. */
    private static final int MAX_SNAPSHOTS = 256;
    private final Map<BlockPos, ChestSnapshot> snapshots =
            new LinkedHashMap<>(32, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BlockPos, ChestSnapshot> eldest) {
                    return size() > MAX_SNAPSHOTS;
                }
            };

    /** Store a snapshot for a chest position. */
    public void putSnapshot(BlockPos pos, ChestSnapshot snapshot) {
        snapshots.put(pos.toImmutable(), snapshot);
    }

    /** Get the cached snapshot for a chest, or null if not scanned. */
    public ChestSnapshot getSnapshot(BlockPos pos) {
        return snapshots.get(pos.toImmutable());
    }

    /** Invalidate the cached snapshot for a chest (e.g. after modifying contents). */
    public void invalidateSnapshot(BlockPos pos) {
        snapshots.remove(pos.toImmutable());
    }

    /** Clear all snapshots (positions are retained). */
    public void clearSnapshots() {
        snapshots.clear();
    }

    // ── Chest scanning / indexing ───────────────────────────────────────

    /**
     * Scans the currently open chest screen and stores the snapshot if the
     * chest position matches a registered supply chest.
     *
     * Call this whenever the player opens a chest that should be indexed.
     *
     * @param chestPos the world position of the chest being opened
     * @param handler  the open container screen handler
     */
    public void scanOpenChest(BlockPos chestPos, GenericContainerScreenHandler handler) {
        if (chestPos == null || handler == null) return;

        BlockPos key = chestPos.toImmutable();
        Map<String, Integer> items = new HashMap<>();
        int shulkerCount = 0;

        int chestSlots = handler.getRows() * 9;
        for (int slot = 0; slot < chestSlots; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            if (isShulkerBox(stack)) {
                shulkerCount++;
                Map<String, Integer> shulkerContents = readShulkerContents(stack);
                for (var entry : shulkerContents.entrySet()) {
                    items.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                items.merge(itemId, stack.getCount(), Integer::sum);
            } else {
                items.merge(itemId, stack.getCount(), Integer::sum);
            }
        }

        ChestSnapshot snapshot = new ChestSnapshot(
                key, Map.copyOf(items), shulkerCount, System.currentTimeMillis());
        putSnapshot(key, snapshot);

        LOGGER.debug("Indexed {} slots at ({}, {}, {}) — {} item types, {} shulkers",
                chestSlots, key.getX(), key.getY(), key.getZ(), items.size(), shulkerCount);
    }

    /**
     * Read a shulker box ItemStack's contents via DataComponentTypes.CONTAINER.
     *
     * @return itemId to count for all items inside the shulker
     */
    public static Map<String, Integer> readShulkerContents(ItemStack shulkerStack) {
        Map<String, Integer> contents = new HashMap<>();
        if (shulkerStack == null || shulkerStack.isEmpty()) return contents;

        ContainerComponent cc = shulkerStack.get(DataComponentTypes.CONTAINER);
        if (cc == null) return contents;

        for (ItemStack inner : cc.iterateNonEmpty()) {
            String innerId = Registries.ITEM.getId(inner.getItem()).toString();
            contents.merge(innerId, inner.getCount(), Integer::sum);
        }
        return contents;
    }

    /** Check if an ItemStack is a shulker box. */
    public static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    // ── Best-chest ranking ──────────────────────────────────────────────

    /**
     * Find the best supply chest for a set of needed item IDs.
     *
     * Ranking: indexed chests with matching items (by match count
     * then distance) then unindexed chests (by distance) then indexed
     * chests with no matches (by distance, snapshot may be stale).
     */
    public BlockPos findBestChest(BlockPos from, Set<String> neededItemIds) {
        return findBestChest(from, neededItemIds, Collections.emptySet());
    }

    /**
     * Same as {@link #findBestChest(BlockPos, Set)} but excludes positions
     * in {@code exclude} (e.g. chests that couldn't be reached).
     */
    public BlockPos findBestChest(BlockPos from, Set<String> neededItemIds,
                                  Set<BlockPos> exclude) {
        if (supplyPositions.isEmpty()) return null;
        if (neededItemIds.isEmpty()) return nearestSupplyChest(from);

        BlockPos bestIndexed = null;
        int bestMatchCount = 0;
        double bestIndexedDist = Double.MAX_VALUE;

        BlockPos bestUnindexed = null;
        double bestUnindexedDist = Double.MAX_VALUE;

        BlockPos bestFallback = null;
        double bestFallbackDist = Double.MAX_VALUE;

        for (BlockPos pos : supplyPositions) {
            if (exclude.contains(pos)) continue;
            double dist = from.getSquaredDistance(pos);
            ChestSnapshot snapshot = snapshots.get(pos);

            if (snapshot == null) {
                if (dist < bestUnindexedDist) {
                    bestUnindexedDist = dist;
                    bestUnindexed = pos;
                }
            } else {
                int matchCount = 0;
                for (String needed : neededItemIds) {
                    if (snapshot.contains(needed)) matchCount++;
                }
                if (matchCount > 0) {
                    if (matchCount > bestMatchCount
                            || (matchCount == bestMatchCount && dist < bestIndexedDist)) {
                        bestMatchCount = matchCount;
                        bestIndexedDist = dist;
                        bestIndexed = pos;
                    }
                } else {
                    if (dist < bestFallbackDist) {
                        bestFallbackDist = dist;
                        bestFallback = pos;
                    }
                }
            }
        }

        if (bestIndexed != null) return bestIndexed;
        if (bestUnindexed != null) return bestUnindexed;
        return bestFallback;
    }

    /** Find the nearest registered supply chest. */
    public BlockPos nearestSupplyChest(BlockPos from) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : supplyPositions) {
            double dist = from.getSquaredDistance(pos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        return nearest;
    }

    /**
     * Combined inventory across all indexed supply chests.
     *
     * @return itemId to total count
     */
    public Map<String, Integer> getCombinedInventory() {
        Map<String, Integer> combined = new HashMap<>();
        for (ChestSnapshot snapshot : snapshots.values()) {
            for (var entry : snapshot.items().entrySet()) {
                combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return combined;
    }

    /**
     * Summary of the chest index state.
     */
    public record ChestIndexSummary(
            int indexedChests,
            int unindexedChests,
            int totalItems,
            int totalItemTypes,
            int totalShulkers
    ) {
        public int totalChests() {
            return indexedChests + unindexedChests;
        }
    }

    /** Build a summary of the chest index. */
    public ChestIndexSummary getIndexSummary() {
        int indexed = 0;
        int unindexed = 0;
        int totalItems = 0;
        int totalShulkers = 0;
        Set<String> allTypes = new HashSet<>();

        for (BlockPos pos : supplyPositions) {
            ChestSnapshot snapshot = snapshots.get(pos);
            if (snapshot != null) {
                indexed++;
                totalShulkers += snapshot.shulkerCount();
                for (var entry : snapshot.items().entrySet()) {
                    totalItems += entry.getValue();
                    allTypes.add(entry.getKey());
                }
            } else {
                unindexed++;
            }
        }
        return new ChestIndexSummary(indexed, unindexed, totalItems, allTypes.size(), totalShulkers);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  STORAGE CHEST SORTING — deposit planning + state machine
    // ═══════════════════════════════════════════════════════════════════

    /** Sorting state machine states. */
    public enum SortState {
        /** Not sorting. */
        IDLE,
        /** Walking to the sorting chest area. */
        WALKING_TO_CHESTS,
        /** Opening a chest to deposit items. */
        OPENING_CHEST,
        /** Depositing items into the open chest. */
        DEPOSITING,
        /** Walking to the next chest for different items. */
        WALKING_TO_NEXT,
        /** Done — all items deposited or no more chests. */
        DONE
    }

    private SortState sortState = SortState.IDLE;

    // ── Storage chest configuration ─────────────────────────────────────

    /** Storage chest positions (order matters — first chest gets first item type). */
    private final List<BlockPos> storageChests = new ArrayList<>();

    /**
     * Chest type assignments: maps chest position to the primary item type.
     * The "type" is the item's registry ID (e.g., "minecraft:cobblestone").
     */
    private final Map<BlockPos, String> chestTypes = new LinkedHashMap<>();

    /**
     * Overflow chest — receives items that don't match any typed chest.
     * If null, items without a matching chest are skipped.
     */
    private BlockPos overflowChest;

    /** Items to keep in inventory (tools, food, light sources). */
    private final Set<Item> keepItems = new HashSet<>();

    // ── Sorting runtime state ───────────────────────────────────────────

    /** Items to deposit, grouped by target chest. */
    private final Map<BlockPos, List<Integer>> depositPlan = new LinkedHashMap<>();

    /** Current target chest for sorting. */
    private BlockPos sortTarget;

    /** Slots to deposit at the current chest. */
    private List<Integer> currentSlots;

    /** Deposit progress index. */
    private int depositIndex;

    /** Tick counter for sorting. */
    private int sortTickCounter;

    /** Cooldown between slot clicks (avoid server-side rate limits). */
    private static final int CLICK_COOLDOWN_TICKS = 3;

    /** Ticks to wait for chest screen to open. */
    private static final int OPEN_TIMEOUT_TICKS = 40;

    /** Counter for open timeout. */
    private int openWaitTicks;

    /** Position to return to after sorting. */
    private BlockPos sortReturnPos;

    // ── Storage chest API ───────────────────────────────────────────────

    /** Get the current sort state. */
    public SortState getSortState() { return sortState; }

    /** Whether the sorter is actively running. */
    public boolean isSorting() {
        return sortState != SortState.IDLE && sortState != SortState.DONE;
    }

    /** Add a storage chest. */
    public void addStorageChest(BlockPos pos) {
        if (!storageChests.contains(pos)) {
            storageChests.add(pos);
        }
    }

    /** Remove a storage chest. */
    public void removeStorageChest(BlockPos pos) {
        storageChests.remove(pos);
        chestTypes.remove(pos);
    }

    /** Get storage chest positions. */
    public List<BlockPos> getStorageChests() {
        return Collections.unmodifiableList(storageChests);
    }

    /** Set the overflow chest. */
    public void setOverflowChest(BlockPos pos) {
        this.overflowChest = pos;
        if (!storageChests.contains(pos)) {
            storageChests.add(pos);
        }
    }

    /** Get the overflow chest, if set. */
    public BlockPos getOverflowChest() { return overflowChest; }

    /** Get chest type assignments. */
    public Map<BlockPos, String> getChestTypes() {
        return Collections.unmodifiableMap(chestTypes);
    }

    /** Manually assign a chest type. */
    public void setChestType(BlockPos pos, String itemId) {
        chestTypes.put(pos, itemId);
    }

    /** Add an item to the keep list (won't be deposited). */
    public void addKeepItem(Item item) {
        keepItems.add(item);
    }

    /** Remove an item from the keep list. */
    public void removeKeepItem(Item item) {
        keepItems.remove(item);
    }

    /** Get keep item set. */
    public Set<Item> getKeepItems() {
        return Collections.unmodifiableSet(keepItems);
    }

    /** Clear all chest type assignments. */
    public void clearTypes() {
        chestTypes.clear();
    }

    // ── Sorting lifecycle ───────────────────────────────────────────────

    /**
     * Check if the player's inventory is full enough to warrant sorting.
     * Returns true if fewer than 4 empty slots remain.
     */
    public boolean isInventoryFull(MinecraftClient mc) {
        if (mc.player == null) return false;
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                empty++;
            }
        }
        return empty < 4;
    }

    /**
     * Start the sorting process.
     * Analyzes inventory and builds a deposit plan.
     */
    public boolean startSort(MinecraftClient mc) {
        if (storageChests.isEmpty()) {
            ChatHelper.info("§cNo storage chests configured. Use /spawnproof chest add");
            return false;
        }

        if (mc.player == null) return false;

        sortReturnPos = mc.player.getBlockPos();
        buildDepositPlan(mc);

        if (depositPlan.isEmpty()) {
            ChatHelper.info("§aNothing to deposit.");
            return false;
        }

        Iterator<Map.Entry<BlockPos, List<Integer>>> it = depositPlan.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<BlockPos, List<Integer>> entry = it.next();
            sortTarget = entry.getKey();
            currentSlots = entry.getValue();
            depositIndex = 0;
        }

        sortState = SortState.WALKING_TO_CHESTS;
        sortTickCounter = 0;

        int totalItems = depositPlan.values().stream().mapToInt(List::size).sum();
        ChatHelper.info("§aSorting " + totalItems + " item stacks into "
                + depositPlan.size() + " chests...");
        return true;
    }

    /** Stop sorting. */
    public void stopSort() {
        PathWalker.stop();
        sortState = SortState.IDLE;
        depositPlan.clear();
        sortTarget = null;
        currentSlots = null;
    }

    /** Get the return position after sorting. */
    public BlockPos getSortReturnPos() { return sortReturnPos; }

    // ── Sorting tick ────────────────────────────────────────────────────

    /**
     * Drive the sorting state machine. Call every client tick.
     */
    public void tick() {
        if (sortState == SortState.IDLE || sortState == SortState.DONE) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        sortTickCounter++;

        switch (sortState) {
            case WALKING_TO_CHESTS, WALKING_TO_NEXT -> tickSortWalking(mc);
            case OPENING_CHEST -> tickSortOpening(mc);
            case DEPOSITING -> tickSortDepositing(mc);
            default -> {}
        }
    }

    // ── Sorting state handlers ──────────────────────────────────────────

    private void tickSortWalking(MinecraftClient mc) {
        if (sortTarget == null) {
            sortState = SortState.DONE;
            return;
        }

        ClientPlayerEntity player = mc.player;
        double distSq = player.squaredDistanceTo(
                sortTarget.getX() + 0.5,
                sortTarget.getY() + 0.5,
                sortTarget.getZ() + 0.5);

        if (distSq <= 4.5 * 4.5) {
            PathWalker.stop();
            sortState = SortState.OPENING_CHEST;
            openWaitTicks = 0;
            return;
        }

        if (!PathWalker.isActive()) {
            PathWalker.walkToAdjacent(sortTarget);
        }

        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            sortState = SortState.OPENING_CHEST;
            openWaitTicks = 0;
            return;
        }

        if (PathWalker.isStuck()) {
            PathWalker.stop();
            ChatHelper.info("§eCan't reach storage chest at "
                    + sortTarget.getX() + " " + sortTarget.getY() + " " + sortTarget.getZ());
            advanceToNextSortChest();
        }

        PathWalker.tick();
    }

    private void tickSortOpening(MinecraftClient mc) {
        openWaitTicks++;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            depositIndex = 0;
            sortState = SortState.DEPOSITING;
            return;
        }

        if (openWaitTicks == 1) {
            BlockState chestState = mc.world.getBlockState(sortTarget);
            if (chestState.getBlock() instanceof ChestBlock
                    || chestState.getBlock() instanceof BarrelBlock
                    || chestState.getBlock() instanceof ShulkerBoxBlock) {
                mc.interactionManager.interactBlock(
                        mc.player,
                        /*? if >=1.21.10 {*//*
                        mc.player.getActiveHand(),
                        *//*?} else {*/
                        mc.player.getActiveHand(),
                        /*?}*/
                        new net.minecraft.util.hit.BlockHitResult(
                                net.minecraft.util.math.Vec3d.ofCenter(sortTarget),
                                net.minecraft.util.math.Direction.UP,
                                sortTarget,
                                false
                        )
                );
            }
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.info("§eCouldn't open chest at "
                    + sortTarget.getX() + " " + sortTarget.getY() + " " + sortTarget.getZ());
            advanceToNextSortChest();
        }
    }

    private void tickSortDepositing(MinecraftClient mc) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
            advanceToNextSortChest();
            return;
        }

        if (sortTickCounter % CLICK_COOLDOWN_TICKS != 0) return;

        if (currentSlots == null || depositIndex >= currentSlots.size()) {
            mc.player.closeHandledScreen();
            assignChestType(sortTarget, mc);
            advanceToNextSortChest();
            return;
        }

        int playerSlot = currentSlots.get(depositIndex);
        ItemStack stack = mc.player.getInventory().getStack(playerSlot);

        if (stack.isEmpty()) {
            depositIndex++;
            return;
        }

        int chestSlotCount = containerHandler.getRows() * 9;
        boolean hasRoom = false;
        for (int i = 0; i < chestSlotCount; i++) {
            ItemStack chestStack = containerHandler.getSlot(i).getStack();
            if (chestStack.isEmpty()) {
                hasRoom = true;
                break;
            }
            if (ItemStack.areItemsEqual(chestStack, stack)
                    && chestStack.getCount() < chestStack.getMaxCount()) {
                hasRoom = true;
                break;
            }
        }

        if (!hasRoom) {
            ChatHelper.info("§eChest full at "
                    + sortTarget.getX() + " " + sortTarget.getY() + " " + sortTarget.getZ());
            mc.player.closeHandledScreen();
            advanceToNextSortChest();
            return;
        }

        int containerSlotIndex;
        if (playerSlot < 9) {
            containerSlotIndex = chestSlotCount + 27 + playerSlot;
        } else {
            containerSlotIndex = chestSlotCount + playerSlot - 9;
        }

        mc.interactionManager.clickSlot(
                containerHandler.syncId,
                containerSlotIndex,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
        );

        depositIndex++;
    }

    // ── Deposit planning ────────────────────────────────────────────────

    private void buildDepositPlan(MinecraftClient mc) {
        depositPlan.clear();

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Map<String, List<Integer>> itemSlots = new LinkedHashMap<>();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (keepItems.contains(stack.getItem())) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            itemSlots.computeIfAbsent(itemId, k -> new ArrayList<>()).add(i);
        }

        if (itemSlots.isEmpty()) return;

        Set<BlockPos> usedChests = new HashSet<>();

        // First pass: items with existing chest type assignments
        for (Map.Entry<String, List<Integer>> entry : itemSlots.entrySet()) {
            String itemId = entry.getKey();
            List<Integer> slots = entry.getValue();

            BlockPos assigned = null;
            for (Map.Entry<BlockPos, String> typeEntry : chestTypes.entrySet()) {
                if (typeEntry.getValue().equals(itemId)
                        && storageChests.contains(typeEntry.getKey())) {
                    assigned = typeEntry.getKey();
                    break;
                }
            }

            if (assigned != null) {
                depositPlan.computeIfAbsent(assigned, k -> new ArrayList<>()).addAll(slots);
                usedChests.add(assigned);
            }
        }

        // Second pass: unassigned items get new chests
        for (Map.Entry<String, List<Integer>> entry : itemSlots.entrySet()) {
            String itemId = entry.getKey();

            if (chestTypes.containsValue(itemId)) {
                boolean alreadyPlanned = depositPlan.values().stream()
                        .anyMatch(slots -> !Collections.disjoint(slots, entry.getValue()));
                if (alreadyPlanned) continue;
            }

            List<Integer> slots = entry.getValue();

            BlockPos freeChest = null;
            for (BlockPos chest : storageChests) {
                if (!usedChests.contains(chest) && !chestTypes.containsKey(chest)) {
                    freeChest = chest;
                    break;
                }
            }

            if (freeChest != null) {
                depositPlan.computeIfAbsent(freeChest, k -> new ArrayList<>()).addAll(slots);
                usedChests.add(freeChest);
            } else if (overflowChest != null) {
                depositPlan.computeIfAbsent(overflowChest, k -> new ArrayList<>()).addAll(slots);
            }
        }
    }

    private void assignChestType(BlockPos chest, MinecraftClient mc) {
        if (chestTypes.containsKey(chest)) return;
        if (Objects.equals(chest, overflowChest)) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
            for (int i = 0; i < containerHandler.getRows() * 9; i++) {
                ItemStack stack = containerHandler.getSlot(i).getStack();
                if (!stack.isEmpty()) {
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    chestTypes.put(chest, itemId);
                    ChatHelper.info("§7Chest assigned type: §f" + itemId);
                    return;
                }
            }
        }
    }

    private void advanceToNextSortChest() {
        if (sortTarget != null) {
            depositPlan.remove(sortTarget);
        }

        if (depositPlan.isEmpty()) {
            sortState = SortState.DONE;
            ChatHelper.info("§aSorting complete.");
            return;
        }

        Iterator<Map.Entry<BlockPos, List<Integer>>> it = depositPlan.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<BlockPos, List<Integer>> entry = it.next();
            sortTarget = entry.getKey();
            currentSlots = entry.getValue();
            depositIndex = 0;
            sortState = SortState.WALKING_TO_NEXT;
        } else {
            sortState = SortState.DONE;
        }
    }

    /** Get a status string for the sorter. */
    public String getSortStatus() {
        return switch (sortState) {
            case IDLE -> "Idle";
            case WALKING_TO_CHESTS, WALKING_TO_NEXT -> "Walking to chest...";
            case OPENING_CHEST -> "Opening chest...";
            case DEPOSITING -> "Depositing items... (" + depositIndex + "/" +
                    (currentSlots != null ? currentSlots.size() : 0) + ")";
            case DONE -> "Sorting complete";
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PERSISTENCE — supply chest positions
    // ═══════════════════════════════════════════════════════════════════

    /** Load supply-chest positions from disk. */
    public void loadSupplyChests() {
        try {
            if (!Files.exists(SUPPLY_FILE)) return;
            try (Reader reader = Files.newBufferedReader(SUPPLY_FILE)) {
                SavedChestData data = GSON.fromJson(reader, SavedChestData.class);
                if (data == null || data.positions == null) return;
                supplyPositions.clear();
                for (int[] pos : data.positions) {
                    if (pos.length >= 3) {
                        supplyPositions.add(new BlockPos(pos[0], pos[1], pos[2]));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load supply chests", e);
        }
    }

    /** Save supply-chest positions to disk. */
    public void saveSupplyChests() {
        try {
            Files.createDirectories(SUPPLY_FILE.getParent());
            SavedChestData data = new SavedChestData();
            data.positions = new ArrayList<>();
            for (BlockPos pos : supplyPositions) {
                data.positions.add(new int[] { pos.getX(), pos.getY(), pos.getZ() });
            }
            try (Writer writer = Files.newBufferedWriter(SUPPLY_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save supply chests", e);
        }
    }

    private static class SavedChestData {
        List<int[]> positions;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  GLOBAL OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Clear all build-session chest data (snapshots only).
     * Supply-chest positions are retained (they're persistent config).
     */
    public void clearSessionData() {
        snapshots.clear();
    }
}

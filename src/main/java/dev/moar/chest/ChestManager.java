package dev.moar.chest;

import dev.moar.MoarMod;
import dev.moar.stash.StashDatabase;
import dev.moar.util.ChatHelper;
import dev.moar.util.PathWalker;
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
import net.minecraft.core.component.DataComponents;
*//*?} else {*/
import net.minecraft.component.DataComponentTypes;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.component.ItemContainerContents;
*//*?} else {*/
import net.minecraft.component.type.ContainerComponent;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.BlockItem;
*//*?} else {*/
import net.minecraft.item.BlockItem;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Item;
*//*?} else {*/
import net.minecraft.item.Item;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.ItemStack;
*//*?} else {*/
import net.minecraft.item.ItemStack;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.registries.BuiltInRegistries;
*//*?} else {*/
import net.minecraft.registry.Registries;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ChestMenu;
*//*?} else {*/
import net.minecraft.screen.GenericContainerScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.AbstractContainerMenu;
*//*?} else {*/
import net.minecraft.screen.ScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ContainerInput;
*//*?} else {*/
import net.minecraft.screen.slot.SlotActionType;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

// Supply-chest registry, inventory snapshots, scanning, ranking, and sorting.
public final class ChestManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/ChestManager");

    // --- SUPPLY CHEST INDEX — positions (persisted) + snapshots (ephemeral)

    /** Registered supply-chest positions. Persisted to database. */
    private final Set<BlockPos> supplyPositions = new LinkedHashSet<>();

    // Supply chest registration

    /** Register a supply chest position. Returns false if already registered. */
    public boolean addSupplyChest(BlockPos pos) {
        /*? if >=26.1 {*//*
        BlockPos immutable = pos.immutable();
        *//*?} else {*/
        BlockPos immutable = pos.toImmutable();
        /*?}*/
        if (!supplyPositions.add(immutable)) return false;
        saveSupplyChests();
        return true;
    }

    /** Unregister a supply chest position. */
    public boolean removeSupplyChest(BlockPos pos) {
        /*? if >=26.1 {*//*
        BlockPos immutable = pos.immutable();
        *//*?} else {*/
        BlockPos immutable = pos.toImmutable();
        /*?}*/
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

    // Inventory snapshots (in-memory)

    // Snapshot of a supply chest's contents (direct + shulker items).
    public record ChestSnapshot(
            BlockPos pos,
            Map<String, Integer> items,
            int shulkerCount,
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
        /*? if >=26.1 {*//*
        snapshots.put(pos.immutable(), snapshot);
        *//*?} else {*/
        snapshots.put(pos.toImmutable(), snapshot);
        /*?}*/
    }

    /** Get the cached snapshot for a chest, or null if not scanned. */
    public ChestSnapshot getSnapshot(BlockPos pos) {
        /*? if >=26.1 {*//*
        return snapshots.get(pos.immutable());
        *//*?} else {*/
        return snapshots.get(pos.toImmutable());
        /*?}*/
    }

    /** Invalidate the cached snapshot for a chest (e.g. after modifying contents). */
    public void invalidateSnapshot(BlockPos pos) {
        /*? if >=26.1 {*//*
        snapshots.remove(pos.immutable());
        *//*?} else {*/
        snapshots.remove(pos.toImmutable());
        /*?}*/
    }

    /** Clear all snapshots (positions are retained). */
    public void clearSnapshots() {
        snapshots.clear();
    }

    // Chest scanning / indexing

    // Scan an open chest and cache its contents.
    /*? if >=26.1 {*//*
    public void scanOpenChest(BlockPos chestPos, ChestMenu handler) {
    *//*?} else {*/
    public void scanOpenChest(BlockPos chestPos, GenericContainerScreenHandler handler) {
    /*?}*/
        if (chestPos == null || handler == null) return;

        /*? if >=26.1 {*//*
        BlockPos key = chestPos.immutable();
        *//*?} else {*/
        BlockPos key = chestPos.toImmutable();
        /*?}*/
        Map<String, Integer> items = new HashMap<>();
        int shulkerCount = 0;

        /*? if >=26.1 {*//*
        int chestSlots = handler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlots = handler.getRows() * 9;
        /*?}*/
        for (int slot = 0; slot < chestSlots; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) continue;

            /*? if >=26.1 {*//*
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            *//*?} else {*/
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            /*?}*/

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

    // Read a shulker box ItemStack's contents via CONTAINER data component.
    public static Map<String, Integer> readShulkerContents(ItemStack shulkerStack) {
        Map<String, Integer> contents = new HashMap<>();
        if (shulkerStack == null || shulkerStack.isEmpty()) return contents;

        /*? if >=26.1 {*//*
        ItemContainerContents cc = shulkerStack.get(DataComponents.CONTAINER);
        *//*?} else {*/
        ContainerComponent cc = shulkerStack.get(DataComponentTypes.CONTAINER);
        /*?}*/
        if (cc == null) return contents;

        /*? if >=26.1 {*//*
        for (ItemStack inner : cc.nonEmptyItemCopyStream().toList()) {
        *//*?} else {*/
        for (ItemStack inner : cc.iterateNonEmpty()) {
        /*?}*/
            /*? if >=26.1 {*//*
            String innerId = BuiltInRegistries.ITEM.getKey(inner.getItem()).toString();
            *//*?} else {*/
            String innerId = Registries.ITEM.getId(inner.getItem()).toString();
            /*?}*/
            contents.merge(innerId, inner.getCount(), Integer::sum);
        }
        return contents;
    }

    /** Check if an ItemStack is a shulker box. */
    public static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    // Best-chest ranking

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

    // Find best supply chest, excluding positions in the exclude set.
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
            /*? if >=26.1 {*//*
            double dist = from.distSqr(pos);
            *//*?} else {*/
            double dist = from.getSquaredDistance(pos);
            /*?}*/
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
            /*? if >=26.1 {*//*
            double dist = from.distSqr(pos);
            *//*?} else {*/
            double dist = from.getSquaredDistance(pos);
            /*?}*/
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        return nearest;
    }

    // Combined inventory across all indexed supply chests (itemId -> total count).
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

    // --- STORAGE CHEST SORTING — deposit planning + state machine

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

    // Storage chest configuration

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

    // Sorting runtime state

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

    // Storage chest API

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
            saveSortingConfig();
        }
    }

    /** Remove a storage chest. */
    public void removeStorageChest(BlockPos pos) {
        storageChests.remove(pos);
        chestTypes.remove(pos);
        saveSortingConfig();
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
        saveSortingConfig();
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
        saveSortingConfig();
    }

    /** Add an item to the keep list (won't be deposited). */
    public void addKeepItem(Item item) {
        keepItems.add(item);
        saveKeepItems();
    }

    /** Remove an item from the keep list. */
    public void removeKeepItem(Item item) {
        keepItems.remove(item);
        saveKeepItems();
    }

    /** Get keep item set. */
    public Set<Item> getKeepItems() {
        return Collections.unmodifiableSet(keepItems);
    }

    /** Clear all chest type assignments. */
    public void clearTypes() {
        chestTypes.clear();
        saveSortingConfig();
    }

    // Sorting persistence

    /** Save storage chest layout (positions, types, overflow) to the database. */
    public void saveSortingConfig() {
        StashDatabase db = MoarMod.getDatabase();
        if (db.isOpen()) db.saveStorageChests(storageChests, chestTypes, overflowChest);
    }

    /** Load storage chest layout from the database. */
    public void loadSortingConfig() {
        StashDatabase db = MoarMod.getDatabase();
        if (!db.isOpen()) return;
        StashDatabase.StorageChestConfig cfg = db.loadStorageChests();
        storageChests.clear();
        storageChests.addAll(cfg.chests());
        chestTypes.clear();
        chestTypes.putAll(cfg.chestTypes());
        overflowChest = cfg.overflowChest();
    }

    /** Save keep-items set to the database. */
    private void saveKeepItems() {
        StashDatabase db = MoarMod.getDatabase();
        if (!db.isOpen()) return;
        Set<String> ids = new LinkedHashSet<>();
        for (Item item : keepItems) {
            /*? if >=26.1 {*//*
            ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
            *//*?} else {*/
            ids.add(Registries.ITEM.getId(item).toString());
            /*?}*/
        }
        db.saveKeepItems(ids);
    }

    /** Load keep-items set from the database. */
    public void loadKeepItems() {
        StashDatabase db = MoarMod.getDatabase();
        if (!db.isOpen()) return;
        Set<String> ids = db.loadKeepItems();
        keepItems.clear();
        for (String id : ids) {
            /*? if >=26.1 {*//*
            var resLoc = net.minecraft.resources.Identifier.tryParse(id);
            if (resLoc != null) {
                Item item = BuiltInRegistries.ITEM.getValue(resLoc);
            *//*?} else {*/
            var identifier = net.minecraft.util.Identifier.tryParse(id);
            if (identifier != null) {
                Item item = Registries.ITEM.get(identifier);
            /*?}*/
                if (item != null) keepItems.add(item);
            }
        }
    }

    // Sorting lifecycle

    /**
     * Check if the player's inventory is full enough to warrant sorting.
     * Returns true if fewer than 4 empty slots remain.
     */
    /*? if >=26.1 {*//*
    public boolean isInventoryFull(Minecraft mc) {
    *//*?} else {*/
    public boolean isInventoryFull(MinecraftClient mc) {
    /*?}*/
        if (mc.player == null) return false;
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            if (mc.player.getInventory().getItem(i).isEmpty()) {
            *//*?} else {*/
            if (mc.player.getInventory().getStack(i).isEmpty()) {
            /*?}*/
                empty++;
            }
        }
        return empty < 4;
    }

    /**
     * Start the sorting process.
     * Analyzes inventory and builds a deposit plan.
     */
    /*? if >=26.1 {*//*
    public boolean startSort(Minecraft mc) {
    *//*?} else {*/
    public boolean startSort(MinecraftClient mc) {
    /*?}*/
        if (storageChests.isEmpty()) {
            ChatHelper.info("§cNo storage chests configured. Use /spawnproof chest add");
            return false;
        }

        if (mc.player == null) return false;

        /*? if >=26.1 {*//*
        sortReturnPos = mc.player.blockPosition();
        *//*?} else {*/
        sortReturnPos = mc.player.getBlockPos();
        /*?}*/
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

    // Sorting tick

    /**
     * Drive the sorting state machine. Call every client tick.
     */
    public void tick() {
        if (sortState == SortState.IDLE || sortState == SortState.DONE) return;

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

        sortTickCounter++;

        switch (sortState) {
            case WALKING_TO_CHESTS, WALKING_TO_NEXT -> tickSortWalking(mc);
            case OPENING_CHEST -> tickSortOpening(mc);
            case DEPOSITING -> tickSortDepositing(mc);
            default -> {}
        }
    }

    // Sorting state handlers

    /*? if >=26.1 {*//*
    private void tickSortWalking(Minecraft mc) {
    *//*?} else {*/
    private void tickSortWalking(MinecraftClient mc) {
    /*?}*/
        if (sortTarget == null) {
            sortState = SortState.DONE;
            return;
        }

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

    /*? if >=26.1 {*//*
    private void tickSortOpening(Minecraft mc) {
    *//*?} else {*/
    private void tickSortOpening(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        if (mc.player.containerMenu instanceof ChestMenu) {
        *//*?} else {*/
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
        /*?}*/
            depositIndex = 0;
            sortState = SortState.DEPOSITING;
            return;
        }

        if (openWaitTicks == 1) {
            /*? if >=26.1 {*//*
            BlockState chestState = mc.level.getBlockState(sortTarget);
            *//*?} else {*/
            BlockState chestState = mc.world.getBlockState(sortTarget);
            /*?}*/
            if (chestState.getBlock() instanceof ChestBlock
                    || chestState.getBlock() instanceof BarrelBlock
                    || chestState.getBlock() instanceof ShulkerBoxBlock) {
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(
                *//*?} else {*/
                mc.interactionManager.interactBlock(
                /*?}*/
                        mc.player,
                        /*? if >=26.1 {*//*
                        mc.player.getUsedItemHand(),
                        *//*?} else if >=1.21.10 {*//*
                        mc.player.getActiveHand(),
                        *//*?} else {*/
                        mc.player.getActiveHand(),
                        /*?}*/
                        /*? if >=26.1 {*//*
                        new net.minecraft.world.phys.BlockHitResult(
                        *//*?} else {*/
                        new net.minecraft.util.hit.BlockHitResult(
                        /*?}*/
                                /*? if >=26.1 {*//*
                                net.minecraft.world.phys.Vec3.atCenterOf(sortTarget),
                                *//*?} else {*/
                                net.minecraft.util.math.Vec3d.ofCenter(sortTarget),
                                /*?}*/
                                /*? if >=26.1 {*//*
                                net.minecraft.core.Direction.UP,
                                *//*?} else {*/
                                net.minecraft.util.math.Direction.UP,
                                /*?}*/
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

    /*? if >=26.1 {*//*
    private void tickSortDepositing(Minecraft mc) {
    *//*?} else {*/
    private void tickSortDepositing(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (!(handler instanceof ChestMenu containerHandler)) {
        *//*?} else {*/
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
        /*?}*/
            advanceToNextSortChest();
            return;
        }

        if (sortTickCounter % CLICK_COOLDOWN_TICKS != 0) return;

        if (currentSlots == null || depositIndex >= currentSlots.size()) {
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
            assignChestType(sortTarget, mc);
            advanceToNextSortChest();
            return;
        }

        int playerSlot = currentSlots.get(depositIndex);
        /*? if >=26.1 {*//*
        ItemStack stack = mc.player.getInventory().getItem(playerSlot);
        *//*?} else {*/
        ItemStack stack = mc.player.getInventory().getStack(playerSlot);
        /*?}*/

        if (stack.isEmpty()) {
            depositIndex++;
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlotCount = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlotCount = containerHandler.getRows() * 9;
        /*?}*/
        boolean hasRoom = false;
        for (int i = 0; i < chestSlotCount; i++) {
            /*? if >=26.1 {*//*
            ItemStack chestStack = containerHandler.getSlot(i).getItem();
            *//*?} else {*/
            ItemStack chestStack = containerHandler.getSlot(i).getStack();
            /*?}*/
            if (chestStack.isEmpty()) {
                hasRoom = true;
                break;
            }
            /*? if >=26.1 {*//*
            if (ItemStack.isSameItem(chestStack, stack)
            *//*?} else {*/
            if (ItemStack.areItemsEqual(chestStack, stack)
            /*?}*/
                    /*? if >=26.1 {*//*
                    && chestStack.getCount() < chestStack.getMaxStackSize()) {
                    *//*?} else {*/
                    && chestStack.getCount() < chestStack.getMaxCount()) {
                    /*?}*/
                hasRoom = true;
                break;
            }
        }

        if (!hasRoom) {
            ChatHelper.info("§eChest full at "
                    + sortTarget.getX() + " " + sortTarget.getY() + " " + sortTarget.getZ());
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
            advanceToNextSortChest();
            return;
        }

        int containerSlotIndex;
        if (playerSlot < 9) {
            containerSlotIndex = chestSlotCount + 27 + playerSlot;
        } else {
            containerSlotIndex = chestSlotCount + playerSlot - 9;
        }

        /*? if >=26.1 {*//*
        mc.gameMode.handleContainerInput(
        *//*?} else {*/
        mc.interactionManager.clickSlot(
        /*?}*/
                /*? if >=26.1 {*//*
                containerHandler.containerId,
                *//*?} else {*/
                containerHandler.syncId,
                /*?}*/
                containerSlotIndex,
                0,
                /*? if >=26.1 {*//*
                ContainerInput.QUICK_MOVE,
                *//*?} else {*/
                SlotActionType.QUICK_MOVE,
                /*?}*/
                mc.player
        );

        depositIndex++;
    }

    // Deposit planning

    /*? if >=26.1 {*//*
    private void buildDepositPlan(Minecraft mc) {
    *//*?} else {*/
    private void buildDepositPlan(MinecraftClient mc) {
    /*?}*/
        depositPlan.clear();

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        if (player == null) return;

        Map<String, List<Integer>> itemSlots = new LinkedHashMap<>();

        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            if (keepItems.contains(stack.getItem())) continue;

            /*? if >=26.1 {*//*
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            *//*?} else {*/
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            /*?}*/
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

    /*? if >=26.1 {*//*
    private void assignChestType(BlockPos chest, Minecraft mc) {
    *//*?} else {*/
    private void assignChestType(BlockPos chest, MinecraftClient mc) {
    /*?}*/
        if (chestTypes.containsKey(chest)) return;
        if (Objects.equals(chest, overflowChest)) return;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu containerHandler) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
        /*?}*/
            /*? if >=26.1 {*//*
            for (int i = 0; i < containerHandler.getRowCount() * 9; i++) {
            *//*?} else {*/
            for (int i = 0; i < containerHandler.getRows() * 9; i++) {
            /*?}*/
                /*? if >=26.1 {*//*
                ItemStack stack = containerHandler.getSlot(i).getItem();
                *//*?} else {*/
                ItemStack stack = containerHandler.getSlot(i).getStack();
                /*?}*/
                if (!stack.isEmpty()) {
                    /*? if >=26.1 {*//*
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    *//*?} else {*/
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    /*?}*/
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

    // --- PERSISTENCE — supply chest positions

    /** Load supply-chest positions from the database. */
    public void loadSupplyChests() {
        StashDatabase db = MoarMod.getDatabase();
        if (!db.isOpen()) return;
        List<BlockPos> loaded = db.loadSupplyChests();
        if (!loaded.isEmpty()) {
            supplyPositions.clear();
            supplyPositions.addAll(loaded);
        }
    }

    /** Save supply-chest positions to the database. */
    public void saveSupplyChests() {
        StashDatabase db = MoarMod.getDatabase();
        if (db.isOpen()) db.saveSupplyChests(supplyPositions);
    }

    // --- DUMP CHEST INDEX — positions for depositing mined items

    /** Registered dump-chest positions (for depositing items during clearing). */
    private final Set<BlockPos> dumpPositions = new LinkedHashSet<>();

    /** Register a dump chest position. Returns false if already registered. */
    public boolean addDumpChest(BlockPos pos) {
        /*? if >=26.1 {*//*
        BlockPos immutable = pos.immutable();
        *//*?} else {*/
        BlockPos immutable = pos.toImmutable();
        /*?}*/
        if (!dumpPositions.add(immutable)) return false;
        saveDumpChests();
        return true;
    }

    /** Unregister a dump chest position. */
    public boolean removeDumpChest(BlockPos pos) {
        /*? if >=26.1 {*//*
        BlockPos immutable = pos.immutable();
        *//*?} else {*/
        BlockPos immutable = pos.toImmutable();
        /*?}*/
        boolean removed = dumpPositions.remove(immutable);
        if (removed) saveDumpChests();
        return removed;
    }

    /** Remove all dump chest registrations. */
    public void clearDumpChests() {
        dumpPositions.clear();
        saveDumpChests();
    }

    /** Unmodifiable snapshot of all registered dump-chest positions. */
    public List<BlockPos> getDumpPositions() {
        return List.copyOf(dumpPositions);
    }

    /** Number of registered dump chests. */
    public int dumpChestCount() {
        return dumpPositions.size();
    }

    /** Find the nearest dump chest to the given position, or null. */
    public BlockPos findNearestDumpChest(BlockPos from) {
        if (dumpPositions.isEmpty()) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : dumpPositions) {
            /*? if >=26.1 {*//*
            double d = from.distSqr(pos);
            *//*?} else {*/
            double d = from.getSquaredDistance(pos);
            /*?}*/
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    /** Load dump-chest positions from the database. */
    public void loadDumpChests() {
        StashDatabase db = MoarMod.getDatabase();
        if (!db.isOpen()) return;
        List<BlockPos> loaded = db.loadDumpChests();
        if (!loaded.isEmpty()) {
            dumpPositions.clear();
            dumpPositions.addAll(loaded);
        }
    }

    /** Save dump-chest positions to the database. */
    public void saveDumpChests() {
        StashDatabase db = MoarMod.getDatabase();
        if (db.isOpen()) db.saveDumpChests(dumpPositions);
    }

    // --- GLOBAL OPERATIONS

    /**
     * Clear all build-session chest data (snapshots only).
     * Supply/dump chest positions are retained (persistent config).
     */
    public void clearSessionData() {
        snapshots.clear();
    }
}

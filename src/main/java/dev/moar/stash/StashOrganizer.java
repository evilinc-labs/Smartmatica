package dev.moar.stash;

import dev.moar.chest.ChestManager;
import dev.moar.stash.StashManager.ContainerEntry;
import dev.moar.stash.StashManager.ShulkerDetail;
import dev.moar.util.ChatHelper;
import dev.moar.util.ItemIdentifier;
import dev.moar.util.PathWalker;
import dev.moar.util.PlacementEngine;
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.BlockState;
*//*?} else {*/
import net.minecraft.block.BlockState;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.ShulkerBoxBlock;
*//*?} else {*/
import net.minecraft.block.ShulkerBoxBlock;
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
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
*//*?} else {*/
import net.minecraft.client.network.ClientPlayerInteractionManager;
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
import net.minecraft.world.inventory.CraftingMenu;
*//*?} else {*/
import net.minecraft.screen.CraftingScreenHandler;
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
import net.minecraft.world.inventory.ShulkerBoxMenu;
*//*?} else {*/
import net.minecraft.screen.ShulkerBoxScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ContainerInput;
*//*?} else {*/
import net.minecraft.screen.slot.SlotActionType;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionHand;
*//*?} else {*/
import net.minecraft.util.Hand;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.BlockHitResult;
*//*?} else {*/
import net.minecraft.util.hit.BlockHitResult;
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
import net.minecraft.util.Mth;
*//*?} else {*/
import net.minecraft.util.math.MathHelper;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.Vec3;
*//*?} else {*/
import net.minecraft.util.math.Vec3d;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// Organizes stash region containers: plan moves, execute transfers,
// pack shulkers, craft if needed, overflow remainder.
public final class StashOrganizer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Organizer");

    // State machine

    public enum State {
        IDLE,
        PLANNING,
        // Container-to-container moves
        WALKING,
        OPENING,
        TAKING,
        DEPOSITING,
        // Shulker packing cycle
        SHULKER_SELECTING,
        SHULKER_PLACING,
        SHULKER_WAIT_PLACE,
        SHULKER_OPENING,
        SHULKER_FILLING,
        SHULKER_CLOSING,
        SHULKER_BREAKING,
        SHULKER_PICKUP,
        SHULKER_STORE_WALK,
        SHULKER_STORE_OPEN,
        SHULKER_STORE_DEPOSIT,
        // Fetch empty shulker from region
        SHULKER_FETCH_WALK,
        SHULKER_FETCH_OPEN,
        SHULKER_FETCH_TAKE,
        // Crafting shulker boxes
        CRAFT_MATERIAL_WALK,
        CRAFT_MATERIAL_OPEN,
        CRAFT_MATERIAL_TAKE,
        CRAFT_WALKING,
        CRAFT_OPENING,
        CRAFT_PLACING,
        CRAFT_TAKING,
        // Overflow
        OVERFLOW_WALKING,
        OVERFLOW_OPENING,
        OVERFLOW_DEPOSITING,
        DONE
    }

    private enum TargetRole { SOURCE, DESTINATION }

    private State state = State.IDLE;
    private TargetRole currentRole = TargetRole.SOURCE;

    // References

    private StashManager stashManager;

    // Task queue

    // Optional shulkerContentFilter limits moves to matching shulkers.
    record MoveTask(BlockPos source, BlockPos destination, String itemId, String shulkerContentFilter) {
        MoveTask(BlockPos source, BlockPos destination, String itemId) {
            this(source, destination, itemId, null);
        }
    }

    private final Deque<MoveTask> taskQueue = new ArrayDeque<>();
    private MoveTask currentTask;

    /**
     * Connected containers forming a vertical column. Sorted top-down
     * so depositing fills from top (bottom stays accessible to user).
     */
    record Column(int id, List<BlockPos> chests) {
        BlockPos bottom() { return chests.get(chests.size() - 1); }
        BlockPos top()    { return chests.get(0); }
    }

    /** Column assignment: item type → column (list of chests top-to-bottom). */
    private Map<String, Column> columnAssignment = new LinkedHashMap<>();

    /** Index for depositing: which column-chest index we're currently filling. */
    private int depositColumnIndex;

    // Configuration

    /** Destination chest(s) for empty shulker boxes after organization. */
    private BlockPos emptyShulkerDest;

    // Timing constants

    private static final int OPEN_TIMEOUT_TICKS = 60;
    private static final int CLICK_COOLDOWN_TICKS = 3;
    private static final int PLACE_DELAY_TICKS = 4;
    private static final int PICKUP_DELAY_TICKS = 20;
    private static final int BREAK_TIMEOUT_TICKS = 100;
    /** Minimum loose item count to justify packing into a shulker box. */
    private static final int CONDENSE_MIN_ITEMS = 1;
    /** Number of hotbar slots to exclude from scanning/depositing. */
    private static final int HOTBAR_SIZE = 9;

    // Runtime state

    private BlockPos walkTarget;
    private int openWaitTicks;
    private int actionSlotIndex;
    private int actionCooldown;

    private int totalTasks;
    private int completedTasks;

    // Shulker packing state

    /** Items in player inventory that need to be packed into a shulker. */
    private String packItemId;
    /** Where to deposit the filled shulker after packing. */
    private BlockPos packDestination;
    /** Block position where the shulker is placed in the world. */
    private BlockPos shulkerPlacePos;
    /** Saved player look direction before shulker interactions. */
    private float savedYaw, savedPitch;
    /** Tick counter for shulker phases. */
    private int shulkerTicks;
    /** Retry counter for shulker placement attempts. */
    private int shulkerPlaceRetries;

    // Crafting state

    /** Position of the crafting table to use. */
    private BlockPos craftingTablePos;
    /** How many shulker boxes we want to craft. */
    private int shulkersToCraft;
    /** Tick counter for crafting phases. */
    private int craftTicks;
    /** Queue of containers to visit for crafting material collection. */
    private final Deque<BlockPos> materialSources = new ArrayDeque<>();
    /** Shells still needed from region containers. */
    private int shellsNeeded;
    /** Chests still needed from region containers. */
    private int chestsNeeded;

    // Consolidation state

    /** Queue of take-only tasks for packing misc items into mixed shulkers. */
    private final Deque<MoveTask> consolidationQueue = new ArrayDeque<>();
    /** True when processing consolidation tasks (mixed-item shulker packing). */
    private boolean consolidationMode = false;

    // Overflow state

    /** Position of the overflow chest. */
    private BlockPos overflowChestPos;
    /** Items that couldn't be organized (itemId → quantity). */
    private final Map<String, Integer> overflowItems = new LinkedHashMap<>();

    // Public API

    public void setStashManager(StashManager mgr) { this.stashManager = mgr; }
    public State getState() { return state; }
    public boolean isActive() { return state != State.IDLE && state != State.DONE; }

    public void setEmptyShulkerDest(BlockPos pos) { this.emptyShulkerDest = pos; }
    public BlockPos getEmptyShulkerDest() { return emptyShulkerDest; }

    public int getTotalTasks() { return totalTasks; }
    public int getCompletedTasks() { return completedTasks; }

    // Lifecycle

    public boolean start() {
        if (stashManager == null) {
            ChatHelper.labelled("Organize", "§cStash manager not available.");
            return false;
        }
        if (stashManager.getCorner1() == null || stashManager.getCorner2() == null) {
            ChatHelper.labelled("Organize", "§cSet region first: §f/stash pos1 §cand §f/stash pos2");
            return false;
        }
        if (stashManager.getIndex().isEmpty()) {
            ChatHelper.labelled("Organize", "§cNo scanned data. Run §f/stash scan §cfirst.");
            return false;
        }

        // Full reset — safe to call even if a previous run is DONE or IDLE
        PathWalker.stop();
        taskQueue.clear();
        consolidationQueue.clear();
        overflowItems.clear();
        currentTask = null;
        walkTarget = null;
        consolidationMode = false;
        completedTasks = 0;
        totalTasks = 0;
        state = State.PLANNING;
        return true;
    }

    public void stop() {
        PathWalker.stop();
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc != null && mc.player != null) {
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
        }
        state = State.IDLE;
        taskQueue.clear();
        consolidationQueue.clear();
        consolidationMode = false;
        currentTask = null;
        overflowItems.clear();
        columnAssignment.clear();
        ChatHelper.labelled("Organize", "§eStopped.");
    }

    // Tick

    public void tick() {
        if (state == State.IDLE || state == State.DONE) return;

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

        switch (state) {
            case PLANNING            -> tickPlanning();
            case WALKING             -> tickWalking(mc);
            case OPENING             -> tickOpening(mc);
            case TAKING              -> tickTaking(mc);
            case DEPOSITING          -> tickDepositing(mc);
            case SHULKER_SELECTING   -> tickShulkerSelecting(mc);
            case SHULKER_PLACING     -> tickShulkerPlacing(mc);
            case SHULKER_WAIT_PLACE  -> tickShulkerWaitPlace(mc);
            case SHULKER_OPENING     -> tickShulkerOpening(mc);
            case SHULKER_FILLING     -> tickShulkerFilling(mc);
            case SHULKER_CLOSING     -> tickShulkerClosing(mc);
            case SHULKER_BREAKING    -> tickShulkerBreaking(mc);
            case SHULKER_PICKUP      -> tickShulkerPickup(mc);
            case SHULKER_STORE_WALK  -> tickWalking(mc);
            case SHULKER_STORE_OPEN  -> tickShulkerStoreOpen(mc);
            case SHULKER_STORE_DEPOSIT -> tickShulkerStoreDeposit(mc);
            case SHULKER_FETCH_WALK  -> tickWalking(mc);
            case SHULKER_FETCH_OPEN  -> tickShulkerFetchOpen(mc);
            case SHULKER_FETCH_TAKE  -> tickShulkerFetchTake(mc);
            case CRAFT_MATERIAL_WALK -> tickWalking(mc);
            case CRAFT_MATERIAL_OPEN -> tickCraftMaterialOpen(mc);
            case CRAFT_MATERIAL_TAKE -> tickCraftMaterialTake(mc);
            case CRAFT_WALKING       -> tickWalking(mc);
            case CRAFT_OPENING       -> tickCraftOpening(mc);
            case CRAFT_PLACING       -> tickCraftPlacing(mc);
            case CRAFT_TAKING        -> tickCraftTaking(mc);
            case OVERFLOW_WALKING    -> tickWalking(mc);
            case OVERFLOW_OPENING    -> tickOverflowOpening(mc);
            case OVERFLOW_DEPOSITING -> tickOverflowDepositing(mc);
            default -> {}
        }
    }

    // PLANNING

    private void tickPlanning() {
        Map<BlockPos, ContainerEntry> region = getRegionContainers();

        if (region.isEmpty()) {
            ChatHelper.labelled("Organize", "§cNo containers in region.");
            ChatHelper.labelled("Organize", "§7Index has §f" + stashManager.getIndex().size()
                    + "§7 containers total. Check that pos1/pos2 cover the scanned area.");
            state = State.DONE;
            return;
        }

        ChatHelper.labelled("Organize", "§7Analyzing §f" + region.size()
                + "§7 containers in region...");

        // Step 1: Detect columns (connected-component grouping).
        List<Column> columns = detectColumns(region.keySet());

        // Build lookup: pos → column
        Map<BlockPos, Column> posToColumn = new HashMap<>();
        for (Column col : columns) {
            for (BlockPos p : col.chests()) posToColumn.put(p, col);
        }

        // Step 2: Map items to locations (accessible items only).
        Map<String, List<ItemLocation>> itemLocations = new LinkedHashMap<>();

        for (var entry : region.entrySet()) {
            BlockPos pos = entry.getKey();
            ContainerEntry container = entry.getValue();

            // Start with all items, then subtract inaccessible ones
            Map<String, Integer> accessible = new HashMap<>(container.items());

            // Remove items that are inside shulker boxes (can't be taken directly)
            for (ShulkerDetail sd : container.shulkerDetails()) {
                for (var sdEntry : sd.contents().entrySet()) {
                    accessible.computeIfPresent(sdEntry.getKey(), (k, v) -> {
                        int remaining = v - sdEntry.getValue();
                        return remaining > 0 ? remaining : null;
                    });
                }
            }

            // Remove shulker box items — managed by the packing/fetch system
            accessible.keySet().removeIf(StashOrganizer::isShulkerBoxItem);

            for (var item : accessible.entrySet()) {
                itemLocations.computeIfAbsent(item.getKey(), k -> new ArrayList<>())
                        .add(new ItemLocation(pos, item.getValue()));
            }
        }

        // Step 2b: Map filled shulker boxes by their primary content type.
        // shulkersByContent: contentItemId → list of (containerPos, shulkerType)
        record ShulkerLoc(BlockPos pos, String shulkerType, String primaryContent) {}
        Map<String, List<ShulkerLoc>> shulkersByContent = new LinkedHashMap<>();

        for (var entry : region.entrySet()) {
            BlockPos pos = entry.getKey();
            ContainerEntry container = entry.getValue();
            for (ShulkerDetail sd : container.shulkerDetails()) {
                String primary = getPrimaryContent(sd.contents());
                if (primary != null) {
                    shulkersByContent.computeIfAbsent(primary, k -> new ArrayList<>())
                            .add(new ShulkerLoc(pos, sd.shulkerType(), primary));
                }
            }
        }

        // Add shulker content weight to item totals so shulker-heavy types rank higher.
        // Each filled shulker of type X adds its total item count toward X's weight.
        for (var entry : shulkersByContent.entrySet()) {
            String contentType = entry.getKey();
            for (ShulkerLoc sl : entry.getValue()) {
                itemLocations.computeIfAbsent(contentType, k -> new ArrayList<>());
                // Don't double-count: weight is for column ranking only (0 quantity placeholder)
            }
        }

        // Step 3: Assign items to columns (largest type gets priority).
        columnAssignment = new LinkedHashMap<>();
        Set<Integer> assignedColumnIds = new HashSet<>();

        List<Map.Entry<String, List<ItemLocation>>> sortedItems =
                new ArrayList<>(itemLocations.entrySet());
        sortedItems.sort((a, b) -> {
            int totalA = a.getValue().stream().mapToInt(ItemLocation::quantity).sum();
            int totalB = b.getValue().stream().mapToInt(ItemLocation::quantity).sum();
            return Integer.compare(totalB, totalA);
        });

        int shared = 0;
        Set<String> sharedItemIds = new HashSet<>();
        for (var entry : sortedItems) {
            String itemId = entry.getKey();
            List<ItemLocation> locations = entry.getValue();

            locations.sort(Comparator.comparingInt(ItemLocation::quantity).reversed());

            // Find the column containing the chest with the most of this item
            Column assigned = null;
            for (ItemLocation loc : locations) {
                Column col = posToColumn.get(loc.pos());
                if (col != null && !assignedColumnIds.contains(col.id())) {
                    assigned = col;
                    break;
                }
            }

            // All columns with this item are taken — find any unassigned column
            if (assigned == null) {
                for (Column col : columns) {
                    if (!assignedColumnIds.contains(col.id())) {
                        assigned = col;
                        break;
                    }
                }
            }

            // More types than columns — share
            if (assigned == null) {
                if (!locations.isEmpty()) {
                    Column col = posToColumn.get(locations.get(0).pos());
                    if (col != null) assigned = col;
                }
                sharedItemIds.add(itemId);
                shared++;
            }

            if (assigned != null) {
                columnAssignment.put(itemId, assigned);
                assignedColumnIds.add(assigned.id());
            }
        }

        // Step 4: Generate move tasks.
        // Items with enough loose quantity are condensed into shulker boxes.
        // Items below the threshold are moved loose to the correct column.
        taskQueue.clear();
        consolidationQueue.clear();

        int condenseTypes = 0;
        for (var entry : columnAssignment.entrySet()) {
            String itemId = entry.getKey();
            Column col = entry.getValue();
            Set<BlockPos> columnChests = new HashSet<>(col.chests());

            List<ItemLocation> locations = itemLocations.get(itemId);
            if (locations == null || locations.isEmpty()) continue;

            int totalLoose = locations.stream().mapToInt(ItemLocation::quantity).sum();

            if (sharedItemIds.contains(itemId)) {
                // Shared items: pack into mixed shulkers (more types than columns)
                for (ItemLocation loc : locations) {
                    consolidationQueue.add(new MoveTask(loc.pos(), col.top(), itemId));
                }
            } else if (totalLoose >= CONDENSE_MIN_ITEMS) {
                // Enough loose items to pack into shulker boxes
                for (ItemLocation loc : locations) {
                    consolidationQueue.add(new MoveTask(loc.pos(), col.top(), itemId));
                }
                condenseTypes++;
            } else {
                // Below threshold: move loose items to correct column as-is
                for (ItemLocation loc : locations) {
                    if (!columnChests.contains(loc.pos())) {
                        taskQueue.add(new MoveTask(loc.pos(), col.top(), itemId));
                    }
                }
            }
        }

        // Sort consolidation queue by item type so same-type tasks run together
        if (!consolidationQueue.isEmpty()) {
            List<MoveTask> sorted = new ArrayList<>(consolidationQueue);
            sorted.sort(Comparator.comparing(MoveTask::itemId));
            consolidationQueue.clear();
            consolidationQueue.addAll(sorted);
        }

        // Step 4b: Move filled shulker boxes to matching content columns.
        int shulkerMoves = 0;
        for (var entry : shulkersByContent.entrySet()) {
            String contentType = entry.getKey();
            Column col = columnAssignment.get(contentType);
            if (col == null) continue;
            Set<BlockPos> columnChests = new HashSet<>(col.chests());

            for (ShulkerLoc sl : entry.getValue()) {
                if (!columnChests.contains(sl.pos())) {
                    // Move this filled shulker to the content type's column
                    taskQueue.add(new MoveTask(sl.pos(), col.top(),
                            "minecraft:" + sl.shulkerType(), contentType));
                    shulkerMoves++;
                }
            }
        }

        // Move empty shulker boxes to destination only if not needed for condensing
        if (emptyShulkerDest != null && condenseTypes == 0) {
            for (var entry : region.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.equals(emptyShulkerDest)) continue;

                ContainerEntry container = entry.getValue();
                for (String itemId : container.items().keySet()) {
                    if (isShulkerBoxItem(itemId) && allShulkersEmpty(container, itemId)) {
                        taskQueue.add(new MoveTask(pos, emptyShulkerDest, itemId));
                    }
                }
            }
        }

        totalTasks = taskQueue.size();
        completedTasks = 0;

        if (taskQueue.isEmpty() && consolidationQueue.isEmpty()) {
            ChatHelper.labelled("Organize", "§aStash is already organized!");
            ChatHelper.labelled("Organize", "§7(" + region.size() + " containers in "
                    + columns.size() + " columns, "
                    + itemLocations.size() + " item types — each type has its own column)");
            state = State.DONE;
            return;
        }

        ChatHelper.labelled("Organize", "§aPlanned §f" + totalTasks
                + "§a moves across §f" + columns.size() + "§a columns "
                + "(§f" + columnAssignment.size() + "§a types"
                + (condenseTypes > 0 ? ", §b" + condenseTypes + " to condense" : "")
                + (shared > 0 ? ", §e" + shared + " to consolidate" : "")
                + (shulkerMoves > 0 ? ", §d" + shulkerMoves + " shulker sorts" : "") + "§a).");
        if (!consolidationQueue.isEmpty()) {
            ChatHelper.labelled("Organize", "§7" + consolidationQueue.size()
                    + " condensing tasks (will pack loose items into shulker boxes).");
        }
        advanceToNextTask();
    }

    /**
     * Detect columns via connected-component flood-fill. Sorted top-down.
     */
    private static List<Column> detectColumns(Set<BlockPos> positions) {
        Set<BlockPos> remaining = new HashSet<>(positions);
        List<Column> columns = new ArrayList<>();
        int nextId = 0;

        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            remaining.remove(seed);

            List<BlockPos> component = new ArrayList<>();
            component.add(seed);

            Deque<BlockPos> frontier = new ArrayDeque<>();
            frontier.add(seed);

            while (!frontier.isEmpty()) {
                BlockPos current = frontier.poll();
                // Check all remaining positions for adjacency
                Iterator<BlockPos> it = remaining.iterator();
                while (it.hasNext()) {
                    BlockPos candidate = it.next();
                    int dx = Math.abs(candidate.getX() - current.getX());
                    int dz = Math.abs(candidate.getZ() - current.getZ());
                    int dy = Math.abs(candidate.getY() - current.getY());
                    // Neighbor: horizontal distance ≤ 1, vertical 1-2
                    if (dx <= 1 && dz <= 1 && dy >= 1 && dy <= 2) {
                        it.remove();
                        component.add(candidate);
                        frontier.add(candidate);
                    }
                }
            }

            // Sort top-down (highest Y first)
            component.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
            columns.add(new Column(nextId++, component));
        }

        return columns;
    }

    private record ItemLocation(BlockPos pos, int quantity) {}

    // WALKING (shared by multiple phases)

    /*? if >=26.1 {*//*
    private void tickWalking(Minecraft mc) {
    *//*?} else {*/
    private void tickWalking(MinecraftClient mc) {
    /*?}*/
        if (walkTarget == null) {
            advanceToNextTask();
            return;
        }

        /*? if >=26.1 {*//*
        double distSq = mc.player.distanceToSqr(
        *//*?} else {*/
        double distSq = mc.player.squaredDistanceTo(
        /*?}*/
                walkTarget.getX() + 0.5,
                walkTarget.getY() + 0.5,
                walkTarget.getZ() + 0.5);

        if (distSq <= 4.5 * 4.5) {
            PathWalker.stop();
            onArrived();
            return;
        }

        if (!PathWalker.isActive()) {
            PathWalker.walkToAdjacent(walkTarget);
        }

        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            onArrived();
            return;
        }

        if (PathWalker.isStuck()) {
            PathWalker.stop();
            ChatHelper.labelled("Organize", "§eCan't reach "
                    + walkTarget.getX() + " " + walkTarget.getY() + " " + walkTarget.getZ()
                    + ", skipping.");
            advanceToNextTask();
        }

        PathWalker.tick();
    }

    /** Called when the walk target is reached — transitions to the appropriate next state. */
    private void onArrived() {
        openWaitTicks = 0;
        switch (state) {
            case WALKING              -> state = State.OPENING;
            case SHULKER_STORE_WALK   -> state = State.SHULKER_STORE_OPEN;
            case SHULKER_FETCH_WALK   -> state = State.SHULKER_FETCH_OPEN;
            case CRAFT_MATERIAL_WALK  -> state = State.CRAFT_MATERIAL_OPEN;
            case CRAFT_WALKING        -> state = State.CRAFT_OPENING;
            case OVERFLOW_WALKING     -> state = State.OVERFLOW_OPENING;
            default -> state = State.OPENING;
        }
    }

    // OPENING (container)

    /*? if >=26.1 {*//*
    private void tickOpening(Minecraft mc) {
    *//*?} else {*/
    private void tickOpening(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler) {
        /*?}*/
            actionSlotIndex = 0;
            actionCooldown = 0;
            state = (currentRole == TargetRole.SOURCE) ? State.TAKING : State.DEPOSITING;
            return;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (openWaitTicks == 1) {
            /*? if >=26.1 {*//*
            lookAt(player, Vec3.atCenterOf(walkTarget));
            *//*?} else {*/
            lookAt(player, Vec3d.ofCenter(walkTarget));
            /*?}*/
        }

        if (openWaitTicks == 3) {
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

            /*? if >=26.1 {*//*
            Vec3 center = Vec3.atCenterOf(walkTarget);
            *//*?} else {*/
            Vec3d center = Vec3d.ofCenter(walkTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toTarget = center.subtract(player.getEyePosition());
            *//*?} else {*/
            Vec3d toTarget = center.subtract(player.getEyePos());
            /*?}*/
            /*? if >=26.1 {*//*
            Direction hitFace = Direction.getApproximateNearest(
            *//*?} else {*/
            Direction hitFace = Direction.getFacing(
            /*?}*/
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);

            /*? if >=26.1 {*//*
            mc.gameMode.useItemOn(
            *//*?} else {*/
            mc.interactionManager.interactBlock(
            /*?}*/
                    player,
                    /*? if >=26.1 {*//*
                    InteractionHand.MAIN_HAND,
                    *//*?} else {*/
                    Hand.MAIN_HAND,
                    /*?}*/
                    new BlockHitResult(center, hitFace, walkTarget, false)
            );

            restoreSneak.run();
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Organize", "§eTimeout opening container, skipping.");
            advanceToNextTask();
        }
    }

    // TAKING (chest → player inventory)

    /*? if >=26.1 {*//*
    private void tickTaking(Minecraft mc) {
    *//*?} else {*/
    private void tickTaking(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

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
            if (consolidationMode) advanceConsolidation();
            else transitionToDestination();
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlots = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlots = containerHandler.getRows() * 9;
        /*?}*/

        while (actionSlotIndex < chestSlots) {
            /*? if >=26.1 {*//*
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getItem();
            *//*?} else {*/
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getStack();
            /*?}*/
            if (!stack.isEmpty()) {
                String itemId = ItemIdentifier.getItemId(stack);
                if (itemId.equals(currentTask.itemId())) {
                    // Content-filtered shulker moves: only take shulkers with matching contents
                    if (currentTask.shulkerContentFilter() != null
                            && !shulkerMatchesContent(stack, currentTask.shulkerContentFilter())) {
                        actionSlotIndex++;
                        continue;
                    }
                    if (!hasInventoryRoom(mc.player)) {
                        break;
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
                            actionSlotIndex,
                            0,
                            /*? if >=26.1 {*//*
                            ContainerInput.QUICK_MOVE,
                            *//*?} else {*/
                            SlotActionType.QUICK_MOVE,
                            /*?}*/
                            mc.player
                    );
                    actionSlotIndex++;
                    actionCooldown = CLICK_COOLDOWN_TICKS;
                    return;
                }
            }
            actionSlotIndex++;
        }

        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        if (consolidationMode) {
            // If inventory was full mid-chest, re-queue so remaining items
            // get collected on a later pass.
            if (!hasInventoryRoom(mc.player) && currentTask != null) {
                consolidationQueue.addFirst(currentTask);
            }
            advanceConsolidation();
        } else {
            transitionToDestination();
        }
    }

    // DEPOSITING (player inventory → chest)

    /*? if >=26.1 {*//*
    private void tickDepositing(Minecraft mc) {
    *//*?} else {*/
    private void tickDepositing(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

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
            advanceToNextTask();
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlotCount = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlotCount = containerHandler.getRows() * 9;
        /*?}*/

        // Skip hotbar slots — player's hotbar is protected
        if (actionSlotIndex < HOTBAR_SIZE) actionSlotIndex = HOTBAR_SIZE;

        while (actionSlotIndex < 36) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(actionSlotIndex);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(actionSlotIndex);
            /*?}*/
            if (!stack.isEmpty()) {
                String itemId = ItemIdentifier.getItemId(stack);
                if (itemId.equals(currentTask.itemId())) {
                    // Content-filtered shulker moves: only deposit matching shulkers
                    if (currentTask.shulkerContentFilter() != null
                            && !shulkerMatchesContent(stack, currentTask.shulkerContentFilter())) {
                        actionSlotIndex++;
                        continue;
                    }
                    if (!hasChestRoom(containerHandler)) {
                        // Chest full — try the next chest down in this column
                        /*? if >=26.1 {*//*
                        mc.player.clientSideCloseContainer();
                        *//*?} else {*/
                        mc.player.closeHandledScreen();
                        /*?}*/
                        if (cascadeToNextInColumn()) return;
                        // No more chests in column — try shulker packing
                        startShulkerPacking(currentTask.itemId(), currentTask.destination());
                        return;
                    }

                    int containerSlotIndex;
                    if (actionSlotIndex < 9) {
                        containerSlotIndex = chestSlotCount + 27 + actionSlotIndex;
                    } else {
                        containerSlotIndex = chestSlotCount + actionSlotIndex - 9;
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
                    actionSlotIndex++;
                    actionCooldown = CLICK_COOLDOWN_TICKS;
                    return;
                }
            }
            actionSlotIndex++;
        }

        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        completedTasks++;

        if (completedTasks % 5 == 0 || completedTasks == totalTasks) {
            ChatHelper.labelled("Organize", "§7Progress: §f"
                    + completedTasks + "/" + totalTasks);
        }

        advanceToNextTask();
    }

    /**
     * When a destination chest is full, try the next chest down in the same
     * column. Returns true if a cascade target was found (will walk there).
     */
    private boolean cascadeToNextInColumn() {
        Column col = columnAssignment.get(currentTask.itemId());
        if (col == null) return false;

        depositColumnIndex++;
        if (depositColumnIndex < col.chests().size()) {
            BlockPos next = col.chests().get(depositColumnIndex);
            walkTarget = next;
            currentRole = TargetRole.DESTINATION;
            actionSlotIndex = 0;
            state = State.WALKING;
            return true;
        }
        return false;
    }

    // --- SHULKER PACKING — place → open → fill → break → store

    // Begin shulker packing (destination full, items remain).
    private void startShulkerPacking(String itemId, BlockPos destination) {
        packItemId = itemId;
        packDestination = destination;

        // Try player inventory first
        /*? if >=26.1 {*//*
        int shulkerSlot = findEmptyShulkerInInventory(Minecraft.getInstance().player);
        *//*?} else {*/
        int shulkerSlot = findEmptyShulkerInInventory(MinecraftClient.getInstance().player);
        /*?}*/
        if (shulkerSlot >= 0) {
            ChatHelper.labelled("Organize", "§7Packing items into shulker box...");
            state = State.SHULKER_SELECTING;
            shulkerTicks = 0;
            return;
        }

        // Try region containers
        BlockPos emptyShulkerSource = findEmptyShulkerInRegion();
        if (emptyShulkerSource != null) {
            ChatHelper.labelled("Organize", "§7Fetching empty shulker from region...");
            walkTarget = emptyShulkerSource;
            state = State.SHULKER_FETCH_WALK;
            openWaitTicks = 0;
            return;
        }

        // No empty shulkers — try crafting
        if (canCraftShulkers()) {
            startCrafting();
            return;
        }

        // No resources — overflow
        startOverflow();
    }

    // SHULKER_SELECTING — swap empty shulker to hotbar

    /*? if >=26.1 {*//*
    private void tickShulkerSelecting(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerSelecting(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        int slot = findEmptyShulkerInInventory(player);
        if (slot < 0) {
            // Try region before crafting
            BlockPos emptyShulkerSource = findEmptyShulkerInRegion();
            if (emptyShulkerSource != null) {
                ChatHelper.labelled("Organize", "§7Fetching empty shulker from region...");
                walkTarget = emptyShulkerSource;
                state = State.SHULKER_FETCH_WALK;
                openWaitTicks = 0;
                return;
            }
            if (canCraftShulkers()) { startCrafting(); return; }
            startOverflow();
            return;
        }

        // Select this specific empty shulker slot (avoid matching non-empty ones)
        if (slot < 9) {
            // Already in hotbar — just select it
            /*? if >=1.21.5 {*//*
            player.getInventory().setSelectedSlot(slot);
            *//*?} else {*/
            player.getInventory().selectedSlot = slot;
            /*?}*/
        } else {
            // Swap from main inventory to current hotbar slot
            /*? if >=1.21.5 {*//*
            int hotbarSlot = player.getInventory().getSelectedSlot();
            *//*?} else {*/
            int hotbarSlot = player.getInventory().selectedSlot;
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(
            *//*?} else {*/
            mc.interactionManager.clickSlot(
            /*?}*/
                    /*? if >=26.1 {*//*
                    player.containerMenu.containerId,
                    *//*?} else {*/
                    player.currentScreenHandler.syncId,
                    /*?}*/
                    slot,
                    hotbarSlot,
                    /*? if >=26.1 {*//*
                    ContainerInput.SWAP,
                    *//*?} else {*/
                    SlotActionType.SWAP,
                    /*?}*/
                    player);
        }

        /*? if >=26.1 {*//*
        savedYaw = player.getYRot();
        *//*?} else {*/
        savedYaw = player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        savedPitch = player.getXRot();
        *//*?} else {*/
        savedPitch = player.getPitch();
        /*?}*/

        // Find a spot to place the shulker
        /*? if >=26.1 {*//*
        shulkerPlacePos = findShulkerPlaceSpot(player, mc.level);
        *//*?} else {*/
        shulkerPlacePos = findShulkerPlaceSpot(player, mc.world);
        /*?}*/
        if (shulkerPlacePos == null) {
            ChatHelper.labelled("Organize", "§eNo space to place shulker nearby.");
            startOverflow();
            return;
        }

        state = State.SHULKER_PLACING;
        shulkerTicks = 0;
    }

    // SHULKER_PLACING — place shulker in world

    /*? if >=26.1 {*//*
    private void tickShulkerPlacing(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerPlacing(MinecraftClient mc) {
    /*?}*/
        shulkerTicks++;
        if (shulkerTicks < 2) return; // wait for slot swap

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        // Verify holding a shulker
        /*? if >=26.1 {*//*
        ItemStack held = player.getInventory().getItem(player.getInventory().getSelectedSlot());
        *//*?} else if >=1.21.5 {*//*
        ItemStack held = player.getInventory().getStack(player.getInventory().getSelectedSlot());
        *//*?} else {*/
        ItemStack held = player.getInventory().getStack(player.getInventory().selectedSlot);
        /*?}*/
        if (!ChestManager.isShulkerBox(held)) {
            ChatHelper.labelled("Organize", "§eNot holding shulker — retrying.");
            state = State.SHULKER_SELECTING;
            return;
        }

        // Rotate to look at placement target
        /*? if >=26.1 {*//*
        lookAt(player, Vec3.atCenterOf(shulkerPlacePos.below()).add(0, 0.5, 0));
        *//*?} else {*/
        lookAt(player, Vec3d.ofCenter(shulkerPlacePos.down()).add(0, 0.5, 0));
        /*?}*/
        if (shulkerTicks < PLACE_DELAY_TICKS) return;

        // Sneak to place rather than interact with container below
        Runnable restoreSneak = PlacementEngine.forceForPlacement(player);

        BlockHitResult hit = new BlockHitResult(
                /*? if >=26.1 {*//*
                Vec3.atCenterOf(shulkerPlacePos.below()).add(0, 0.5, 0),
                *//*?} else {*/
                Vec3d.ofCenter(shulkerPlacePos.down()).add(0, 0.5, 0),
                /*?}*/
                Direction.UP,
                /*? if >=26.1 {*//*
                shulkerPlacePos.below(),
                *//*?} else {*/
                shulkerPlacePos.down(),
                /*?}*/
                false);
        /*? if >=26.1 {*//*
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        *//*?} else {*/
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        /*?}*/

        restoreSneak.run();

        state = State.SHULKER_WAIT_PLACE;
        shulkerTicks = 0;
    }

    // SHULKER_WAIT_PLACE — verify placement

    /*? if >=26.1 {*//*
    private void tickShulkerWaitPlace(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerWaitPlace(MinecraftClient mc) {
    /*?}*/
        shulkerTicks++;
        /*? if >=26.1 {*//*
        BlockState st = mc.level.getBlockState(shulkerPlacePos);
        *//*?} else {*/
        BlockState st = mc.world.getBlockState(shulkerPlacePos);
        /*?}*/
        if (st.getBlock() instanceof ShulkerBoxBlock) {
            shulkerPlaceRetries = 0;
            state = State.SHULKER_OPENING;
            shulkerTicks = 0;
            return;
        }
        if (shulkerTicks > 20) {
            shulkerPlaceRetries++;
            if (shulkerPlaceRetries >= 3) {
                ChatHelper.labelled("Organize", "§eShulker placement failed after 3 attempts.");
                shulkerPlaceRetries = 0;
                startOverflow();
                return;
            }
            ChatHelper.labelled("Organize", "§eShulker placement failed, retrying...");
            state = State.SHULKER_SELECTING;
            shulkerTicks = 0;
        }
    }

    // SHULKER_OPENING — open the placed shulker

    /*? if >=26.1 {*//*
    private void tickShulkerOpening(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerOpening(MinecraftClient mc) {
    /*?}*/
        shulkerTicks++;
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ShulkerBoxMenu) {
        *//*?} else {*/
        if (handler instanceof ShulkerBoxScreenHandler) {
        /*?}*/
            state = State.SHULKER_FILLING;
            actionSlotIndex = 0;
            actionCooldown = 0;
            return;
        }

        if (shulkerTicks == 1) {
            /*? if >=26.1 {*//*
            lookAt(player, Vec3.atCenterOf(shulkerPlacePos));
            *//*?} else {*/
            lookAt(player, Vec3d.ofCenter(shulkerPlacePos));
            /*?}*/
        }

        if (shulkerTicks == 3) {
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);
            Direction hitFace = Direction.UP;
            BlockHitResult hit = new BlockHitResult(
                    /*? if >=26.1 {*//*
                    Vec3.atCenterOf(shulkerPlacePos),
                    *//*?} else {*/
                    Vec3d.ofCenter(shulkerPlacePos),
                    /*?}*/
                    hitFace,
                    shulkerPlacePos,
                    false);
            /*? if >=26.1 {*//*
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            *//*?} else {*/
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
            /*?}*/
            restoreSneak.run();
        }

        if (shulkerTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Organize", "§eTimeout opening shulker — breaking it.");
            state = State.SHULKER_BREAKING;
            shulkerTicks = 0;
        }
    }

    // ── SHULKER_FILLING — put items from inventory into placed shulker ──

    /*? if >=26.1 {*//*
    private void tickShulkerFilling(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerFilling(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (!(handler instanceof ShulkerBoxMenu shulkerHandler)) {
        *//*?} else {*/
        if (!(handler instanceof ShulkerBoxScreenHandler shulkerHandler)) {
        /*?}*/
            // Screen closed — close and break
            state = State.SHULKER_CLOSING;
            shulkerTicks = 0;
            return;
        }

        // Shulker has 27 slots; player inventory starts at slot 27 in the handler
        // Find next player inventory slot with the target item (skip hotbar)
        if (actionSlotIndex < HOTBAR_SIZE) actionSlotIndex = HOTBAR_SIZE;
        while (actionSlotIndex < 36) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(actionSlotIndex);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(actionSlotIndex);
            /*?}*/
            if (!stack.isEmpty()) {
                String itemId = ItemIdentifier.getItemId(stack);
                // Skip shulker boxes (containers, not items to pack)
                if (ChestManager.isShulkerBox(stack)) { actionSlotIndex++; continue; }
                // In consolidation mode (packItemId == null), lock onto the first
                // item type so each shulker gets exactly one type.
                if (packItemId == null) {
                    packItemId = itemId;
                }
                if (itemId.equals(packItemId)) {
                    // Check shulker has room
                    if (!hasShulkerRoom(shulkerHandler)) {
                        break; // shulker full
                    }

                    // In ShulkerBoxScreenHandler: slots 0-26 are shulker, 27-53 main inv, 54-62 hotbar
                    int handlerSlot;
                    if (actionSlotIndex < 9) {
                        handlerSlot = 54 + actionSlotIndex; // hotbar
                    } else {
                        handlerSlot = 27 + actionSlotIndex - 9; // main inventory
                    }

                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                    /*?}*/
                            /*? if >=26.1 {*//*
                            shulkerHandler.containerId,
                            *//*?} else {*/
                            shulkerHandler.syncId,
                            /*?}*/
                            handlerSlot,
                            0,
                            /*? if >=26.1 {*//*
                            ContainerInput.QUICK_MOVE,
                            *//*?} else {*/
                            SlotActionType.QUICK_MOVE,
                            /*?}*/
                            mc.player
                    );
                    actionSlotIndex++;
                    actionCooldown = CLICK_COOLDOWN_TICKS;
                    return;
                }
            }
            actionSlotIndex++;
        }

        // Done filling — close the shulker
        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        state = State.SHULKER_CLOSING;
        shulkerTicks = 0;
    }

    // SHULKER_CLOSING — wait a tick after closing

    /*? if >=26.1 {*//*
    private void tickShulkerClosing(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerClosing(MinecraftClient mc) {
    /*?}*/
        shulkerTicks++;
        if (shulkerTicks >= 2) {
            // Select best tool for breaking the shulker
            /*? if >=26.1 {*//*
            BlockState st = mc.level.getBlockState(shulkerPlacePos);
            *//*?} else {*/
            BlockState st = mc.world.getBlockState(shulkerPlacePos);
            /*?}*/
            PlacementEngine.selectBestTool(mc.player, mc, st);
            state = State.SHULKER_BREAKING;
            shulkerTicks = 0;
        }
    }

    // SHULKER_BREAKING — mine the placed shulker

    /*? if >=26.1 {*//*
    private void tickShulkerBreaking(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerBreaking(MinecraftClient mc) {
    /*?}*/
        shulkerTicks++;
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        /*? if >=26.1 {*//*
        BlockState st = mc.level.getBlockState(shulkerPlacePos);
        *//*?} else {*/
        BlockState st = mc.world.getBlockState(shulkerPlacePos);
        /*?}*/
        if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
            // Already broken
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            /*? if >=26.1 {*//*
            player.setYRot(savedYaw);
            *//*?} else {*/
            player.setYaw(savedYaw);
            /*?}*/
            /*? if >=26.1 {*//*
            player.setXRot(savedPitch);
            *//*?} else {*/
            player.setPitch(savedPitch);
            /*?}*/
            state = State.SHULKER_PICKUP;
            shulkerTicks = 0;
            return;
        }

        if (shulkerTicks > BREAK_TIMEOUT_TICKS) {
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            /*? if >=26.1 {*//*
            player.setYRot(savedYaw);
            *//*?} else {*/
            player.setYaw(savedYaw);
            /*?}*/
            /*? if >=26.1 {*//*
            player.setXRot(savedPitch);
            *//*?} else {*/
            player.setPitch(savedPitch);
            /*?}*/
            ChatHelper.labelled("Organize", "§eShulker break timed out.");
            startOverflow();
            return;
        }

        /*? if >=26.1 {*//*
        lookAt(player, Vec3.atCenterOf(shulkerPlacePos));
        *//*?} else {*/
        lookAt(player, Vec3d.ofCenter(shulkerPlacePos));
        /*?}*/
        /*? if >=26.1 {*//*
        mc.gameMode.continueDestroyBlock(shulkerPlacePos, Direction.UP);
        *//*?} else {*/
        mc.interactionManager.updateBlockBreakingProgress(shulkerPlacePos, Direction.UP);
        /*?}*/
        /*? if >=26.1 {*//*
        player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        player.swingHand(Hand.MAIN_HAND);
        /*?}*/
    }

    // SHULKER_PICKUP — walk to drop and collect item entity

    /*? if >=26.1 {*//*
    private void tickShulkerPickup(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerPickup(MinecraftClient mc) {
    /*?}*/
        shulkerTicks++;
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        // Check if we already picked up the shulker (non-empty shulker in inventory)
        if (hasFilledShulkerInInventory(player)) {
            PathWalker.stop();
            walkTarget = packDestination;
            state = State.SHULKER_STORE_WALK;
            openWaitTicks = 0;
            return;
        }

        // Walk directly onto the block where the shulker was (not just adjacent)
        if (!PathWalker.isActive() && shulkerTicks < PICKUP_DELAY_TICKS * 4) {
            PathWalker.walkTo(shulkerPlacePos);
        }
        if (PathWalker.isActive()) {
            PathWalker.tick();
        }

        // Timeout — proceed regardless (shulker may have landed in inventory already)
        if (shulkerTicks >= PICKUP_DELAY_TICKS * 4) {
            PathWalker.stop();
            if (hasFilledShulkerInInventory(player)) {
                walkTarget = packDestination;
                state = State.SHULKER_STORE_WALK;
                openWaitTicks = 0;
            } else {
                ChatHelper.labelled("Organize", "§eCouldn't pick up shulker drop, skipping.");
                advanceToNextTask();
            }
        }
    }

    /** True if the player has a non-empty shulker box in inventory. */
    /*? if >=26.1 {*//*
    private boolean hasFilledShulkerInInventory(LocalPlayer player) {
    *//*?} else {*/
    private boolean hasFilledShulkerInInventory(ClientPlayerEntity player) {
    /*?}*/
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (ChestManager.isShulkerBox(stack)) {
                /*? if >=26.1 {*//*
                var cc = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                *//*?} else if >=26.1 {*//*
                var cc = stack.get(net.minecraft.component.DataComponents.CONTAINER);
                *//*?} else {*/
                var cc = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                /*?}*/
                /*? if >=26.1 {*//*
                if (cc != null && cc.nonEmptyItems().iterator().hasNext()) {
                *//*?} else {*/
                if (cc != null && cc.iterateNonEmpty().iterator().hasNext()) {
                /*?}*/
                    return true;
                }
            }
        }
        return false;
    }

    // ── SHULKER_STORE_OPEN — open destination chest for shulker deposit ─

    /*? if >=26.1 {*//*
    private void tickShulkerStoreOpen(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerStoreOpen(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler) {
        /*?}*/
            state = State.SHULKER_STORE_DEPOSIT;
            actionSlotIndex = 0;
            actionCooldown = 0;
            return;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (openWaitTicks == 1) {
            /*? if >=26.1 {*//*
            lookAt(player, Vec3.atCenterOf(walkTarget));
            *//*?} else {*/
            lookAt(player, Vec3d.ofCenter(walkTarget));
            /*?}*/
        }

        if (openWaitTicks == 3) {
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

            /*? if >=26.1 {*//*
            Vec3 center = Vec3.atCenterOf(walkTarget);
            *//*?} else {*/
            Vec3d center = Vec3d.ofCenter(walkTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toTarget = center.subtract(player.getEyePosition());
            *//*?} else {*/
            Vec3d toTarget = center.subtract(player.getEyePos());
            /*?}*/
            /*? if >=26.1 {*//*
            Direction hitFace = Direction.getApproximateNearest(
            *//*?} else {*/
            Direction hitFace = Direction.getFacing(
            /*?}*/
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);

            /*? if >=26.1 {*//*
            mc.gameMode.useItemOn(
            *//*?} else {*/
            mc.interactionManager.interactBlock(
            /*?}*/
                    player,
                    /*? if >=26.1 {*//*
                    InteractionHand.MAIN_HAND,
                    *//*?} else {*/
                    Hand.MAIN_HAND,
                    /*?}*/
                    new BlockHitResult(center, hitFace, walkTarget, false)
            );

            restoreSneak.run();
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Organize", "§eTimeout opening destination for shulker deposit.");
            advanceToNextTask();
        }
    }

    // SHULKER_STORE_DEPOSIT — deposit filled shulker into chest

    /*? if >=26.1 {*//*
    private void tickShulkerStoreDeposit(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerStoreDeposit(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

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
            advanceToNextTask();
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlotCount = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlotCount = containerHandler.getRows() * 9;
        /*?}*/

        // Deposit any shulker boxes from player inventory (skip hotbar)
        if (actionSlotIndex < HOTBAR_SIZE) actionSlotIndex = HOTBAR_SIZE;
        while (actionSlotIndex < 36) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(actionSlotIndex);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(actionSlotIndex);
            /*?}*/
            if (!stack.isEmpty() && ChestManager.isShulkerBox(stack)) {
                if (!hasChestRoom(containerHandler)) {
                    ChatHelper.labelled("Organize", "§eDestination full even for shulkers.");
                    break;
                }

                int containerSlotIndex;
                if (actionSlotIndex < 9) {
                    containerSlotIndex = chestSlotCount + 27 + actionSlotIndex;
                } else {
                    containerSlotIndex = chestSlotCount + actionSlotIndex - 9;
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
                actionSlotIndex++;
                actionCooldown = CLICK_COOLDOWN_TICKS;
                return;
            }
            actionSlotIndex++;
        }

        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        completedTasks++;

        if (consolidationMode) {
            // In consolidation mode: check if more items to pack or collect
            if (hasOrganizableItemsInInventory(mc.player)) {
                startShulkerPacking(packItemId, packDestination);
            } else if (!consolidationQueue.isEmpty()) {
                advanceConsolidation();
            } else {
                consolidationMode = false;
                finishOrganization();
            }
        } else if (packItemId != null && countItemInInventory(mc.player, packItemId) > 0) {
            // Still have items of this type — try another shulker
            startShulkerPacking(packItemId, packDestination);
        } else {
            advanceToNextTask();
        }
    }

    // --- SHULKER FETCH — walk to container, take an empty shulker, then pack

    /*? if >=26.1 {*//*
    private void tickShulkerFetchOpen(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerFetchOpen(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler) {
        /*?}*/
            actionSlotIndex = 0;
            actionCooldown = 0;
            state = State.SHULKER_FETCH_TAKE;
            return;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (openWaitTicks == 1) {
            /*? if >=26.1 {*//*
            lookAt(player, Vec3.atCenterOf(walkTarget));
            *//*?} else {*/
            lookAt(player, Vec3d.ofCenter(walkTarget));
            /*?}*/
        }

        if (openWaitTicks == 3) {
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);
            /*? if >=26.1 {*//*
            Vec3 center = Vec3.atCenterOf(walkTarget);
            *//*?} else {*/
            Vec3d center = Vec3d.ofCenter(walkTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toTarget = center.subtract(player.getEyePosition());
            *//*?} else {*/
            Vec3d toTarget = center.subtract(player.getEyePos());
            /*?}*/
            /*? if >=26.1 {*//*
            Direction hitFace = Direction.getApproximateNearest(
            *//*?} else {*/
            Direction hitFace = Direction.getFacing(
            /*?}*/
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);
            /*? if >=26.1 {*//*
            mc.gameMode.useItemOn(
            *//*?} else {*/
            mc.interactionManager.interactBlock(
            /*?}*/
                    /*? if >=26.1 {*//*
                    player, InteractionHand.MAIN_HAND,
                    *//*?} else {*/
                    player, Hand.MAIN_HAND,
                    /*?}*/
                    new BlockHitResult(center, hitFace, walkTarget, false));
            restoreSneak.run();
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Organize", "§eTimeout opening container for shulker fetch.");
            if (canCraftShulkers()) { startCrafting(); } else startOverflow();
        }
    }

    /*? if >=26.1 {*//*
    private void tickShulkerFetchTake(Minecraft mc) {
    *//*?} else {*/
    private void tickShulkerFetchTake(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

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
            // Container closed — did we get a shulker?
            if (findEmptyShulkerInInventory(mc.player) >= 0) {
                if (consolidationMode) {
                    advanceConsolidation();
                } else {
                    state = State.SHULKER_SELECTING;
                    shulkerTicks = 0;
                }
            } else if (canCraftShulkers()) {
                startCrafting();
            } else {
                startOverflow();
            }
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlots = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlots = containerHandler.getRows() * 9;
        /*?}*/

        while (actionSlotIndex < chestSlots) {
            /*? if >=26.1 {*//*
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getItem();
            *//*?} else {*/
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getStack();
            /*?}*/
            if (!stack.isEmpty() && ChestManager.isShulkerBox(stack)) {
                /*? if >=26.1 {*//*
                var cc = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                *//*?} else if >=26.1 {*//*
                var cc = stack.get(net.minecraft.component.DataComponents.CONTAINER);
                *//*?} else {*/
                var cc = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                /*?}*/
                /*? if >=26.1 {*//*
                if (cc == null || !cc.nonEmptyItems().iterator().hasNext()) {
                *//*?} else {*/
                if (cc == null || !cc.iterateNonEmpty().iterator().hasNext()) {
                /*?}*/
                    // Found an empty shulker — take it
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                    /*?}*/
                            /*? if >=26.1 {*//*
                            containerHandler.containerId, actionSlotIndex, 0,
                            *//*?} else {*/
                            containerHandler.syncId, actionSlotIndex, 0,
                            /*?}*/
                            /*? if >=26.1 {*//*
                            ContainerInput.QUICK_MOVE, mc.player);
                            *//*?} else {*/
                            SlotActionType.QUICK_MOVE, mc.player);
                            /*?}*/
                    actionCooldown = CLICK_COOLDOWN_TICKS;
                    /*? if >=26.1 {*//*
                    mc.player.clientSideCloseContainer();
                    *//*?} else {*/
                    mc.player.closeHandledScreen();
                    /*?}*/
                    if (consolidationMode) {
                        advanceConsolidation();
                    } else {
                        state = State.SHULKER_SELECTING;
                        shulkerTicks = 0;
                    }
                    return;
                }
            }
            actionSlotIndex++;
        }

        // No empty shulker found in this container
        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        if (canCraftShulkers()) { startCrafting(); } else startOverflow();
    }

    // --- CRAFTING — walk to crafting table → open → place materials → take

    private void startCrafting() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        /*? if >=26.1 {*//*
        craftingTablePos = findCraftingTable(player, mc.level);
        *//*?} else {*/
        craftingTablePos = findCraftingTable(player, mc.world);
        /*?}*/
        if (craftingTablePos == null) {
            ChatHelper.labelled("Organize", "§eNo crafting table found nearby. Place one within the region.");
            startOverflow();
            return;
        }

        // Count materials available across region + inventory
        int shellsInRegion = countItemInRegion("minecraft:shulker_shell");
        int chestsInRegion = countItemInRegion("minecraft:chest");
        int shellsInInv = countItemInInventory(player, "minecraft:shulker_shell");
        int chestsInInv = countItemInInventory(player, "minecraft:chest");
        int totalShells = shellsInRegion + shellsInInv;
        int totalChests = chestsInRegion + chestsInInv;
        shulkersToCraft = Math.min(totalShells / 2, totalChests);

        // Cap to available inventory space (shulkers don't stack)
        int freeSlots = 0;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            if (player.getInventory().getItem(i).isEmpty()) freeSlots++;
            *//*?} else {*/
            if (player.getInventory().getStack(i).isEmpty()) freeSlots++;
            /*?}*/
        }
        shulkersToCraft = Math.min(shulkersToCraft, Math.max(1, freeSlots - 3));

        if (shulkersToCraft <= 0) {
            startOverflow();
            return;
        }

        ChatHelper.labelled("Organize", "§7Crafting §f" + shulkersToCraft + "§7 shulker boxes...");

        // Check if materials already in player inventory
        if (hasShulkerMaterialsInInventory(player)) {
            walkTarget = craftingTablePos;
            state = State.CRAFT_WALKING;
            openWaitTicks = 0;
            return;
        }

        // Need to collect materials from region containers
        shellsNeeded = shulkersToCraft * 2 - shellsInInv;
        chestsNeeded = shulkersToCraft - chestsInInv;

        materialSources.clear();
        Map<BlockPos, ContainerEntry> region = getRegionContainers();
        for (var entry : region.entrySet()) {
            ContainerEntry c = entry.getValue();
            if (c.items().containsKey("minecraft:shulker_shell")
                    || c.items().containsKey("minecraft:chest")) {
                materialSources.add(entry.getKey());
            }
        }

        if (materialSources.isEmpty()) {
            startOverflow();
            return;
        }

        ChatHelper.labelled("Organize", "§7Collecting crafting materials...");
        walkTarget = materialSources.poll();
        state = State.CRAFT_MATERIAL_WALK;
        openWaitTicks = 0;
    }

    // CRAFT_MATERIAL_OPEN — open container to take shells/chests

    /*? if >=26.1 {*//*
    private void tickCraftMaterialOpen(Minecraft mc) {
    *//*?} else {*/
    private void tickCraftMaterialOpen(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler) {
        /*?}*/
            actionSlotIndex = 0;
            actionCooldown = 0;
            state = State.CRAFT_MATERIAL_TAKE;
            return;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (openWaitTicks == 1) {
            /*? if >=26.1 {*//*
            lookAt(player, Vec3.atCenterOf(walkTarget));
            *//*?} else {*/
            lookAt(player, Vec3d.ofCenter(walkTarget));
            /*?}*/
        }

        if (openWaitTicks == 3) {
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);
            /*? if >=26.1 {*//*
            Vec3 center = Vec3.atCenterOf(walkTarget);
            *//*?} else {*/
            Vec3d center = Vec3d.ofCenter(walkTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toTarget = center.subtract(player.getEyePosition());
            *//*?} else {*/
            Vec3d toTarget = center.subtract(player.getEyePos());
            /*?}*/
            /*? if >=26.1 {*//*
            Direction hitFace = Direction.getApproximateNearest(
            *//*?} else {*/
            Direction hitFace = Direction.getFacing(
            /*?}*/
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);
            /*? if >=26.1 {*//*
            mc.gameMode.useItemOn(
            *//*?} else {*/
            mc.interactionManager.interactBlock(
            /*?}*/
                    /*? if >=26.1 {*//*
                    player, InteractionHand.MAIN_HAND,
                    *//*?} else {*/
                    player, Hand.MAIN_HAND,
                    /*?}*/
                    new BlockHitResult(center, hitFace, walkTarget, false));
            restoreSneak.run();
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Organize", "§eTimeout opening material container.");
            advanceCraftMaterial();
        }
    }

    // CRAFT_MATERIAL_TAKE — take shells and chests from container

    /*? if >=26.1 {*//*
    private void tickCraftMaterialTake(Minecraft mc) {
    *//*?} else {*/
    private void tickCraftMaterialTake(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

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
            advanceCraftMaterial();
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlots = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlots = containerHandler.getRows() * 9;
        /*?}*/

        while (actionSlotIndex < chestSlots) {
            /*? if >=26.1 {*//*
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getItem();
            *//*?} else {*/
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getStack();
            /*?}*/
            if (!stack.isEmpty()) {
                boolean take = false;
                if (stack.getItem() == Items.SHULKER_SHELL && shellsNeeded > 0) {
                    shellsNeeded -= stack.getCount();
                    take = true;
                } else if (stack.getItem() == Items.CHEST && chestsNeeded > 0) {
                    chestsNeeded -= stack.getCount();
                    take = true;
                }

                if (take) {
                    if (!hasInventoryRoom(mc.player)) break;
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                    /*?}*/
                            /*? if >=26.1 {*//*
                            containerHandler.containerId, actionSlotIndex, 0,
                            *//*?} else {*/
                            containerHandler.syncId, actionSlotIndex, 0,
                            /*?}*/
                            /*? if >=26.1 {*//*
                            ContainerInput.QUICK_MOVE, mc.player);
                            *//*?} else {*/
                            SlotActionType.QUICK_MOVE, mc.player);
                            /*?}*/
                    actionSlotIndex++;
                    actionCooldown = CLICK_COOLDOWN_TICKS;
                    return;
                }
            }
            actionSlotIndex++;
        }

        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/

        // Check if we have enough materials now
        if (shellsNeeded <= 0 && chestsNeeded <= 0) {
            walkTarget = craftingTablePos;
            state = State.CRAFT_WALKING;
            openWaitTicks = 0;
        } else {
            advanceCraftMaterial();
        }
    }

    /** Advance to the next material source or proceed to crafting if possible. */
    private void advanceCraftMaterial() {
        if (!materialSources.isEmpty()) {
            walkTarget = materialSources.poll();
            state = State.CRAFT_MATERIAL_WALK;
            openWaitTicks = 0;
            return;
        }

        // No more sources — check if we collected enough for at least one craft
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player != null && hasShulkerMaterialsInInventory(mc.player)) {
            int shellsHave = countItemInInventory(mc.player, "minecraft:shulker_shell");
            int chestsHave = countItemInInventory(mc.player, "minecraft:chest");
            shulkersToCraft = Math.min(shellsHave / 2, chestsHave);
            walkTarget = craftingTablePos;
            state = State.CRAFT_WALKING;
            openWaitTicks = 0;
        } else {
            startOverflow();
        }
    }

    /*? if >=26.1 {*//*
    private void tickCraftOpening(Minecraft mc) {
    *//*?} else {*/
    private void tickCraftOpening(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof CraftingMenu) {
        *//*?} else {*/
        if (handler instanceof CraftingScreenHandler) {
        /*?}*/
            state = State.CRAFT_PLACING;
            craftTicks = 0;
            return;
        }

        if (openWaitTicks == 1) {
            /*? if >=26.1 {*//*
            lookAt(mc.player, Vec3.atCenterOf(craftingTablePos));
            *//*?} else {*/
            lookAt(mc.player, Vec3d.ofCenter(craftingTablePos));
            /*?}*/
        }

        if (openWaitTicks == 3) {
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(mc.player);

            /*? if >=26.1 {*//*
            Vec3 center = Vec3.atCenterOf(craftingTablePos);
            *//*?} else {*/
            Vec3d center = Vec3d.ofCenter(craftingTablePos);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toTarget = center.subtract(mc.player.getEyePosition());
            *//*?} else {*/
            Vec3d toTarget = center.subtract(mc.player.getEyePos());
            /*?}*/
            /*? if >=26.1 {*//*
            Direction hitFace = Direction.getApproximateNearest(
            *//*?} else {*/
            Direction hitFace = Direction.getFacing(
            /*?}*/
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);

            /*? if >=26.1 {*//*
            mc.gameMode.useItemOn(
            *//*?} else {*/
            mc.interactionManager.interactBlock(
            /*?}*/
                    mc.player,
                    /*? if >=26.1 {*//*
                    InteractionHand.MAIN_HAND,
                    *//*?} else {*/
                    Hand.MAIN_HAND,
                    /*?}*/
                    new BlockHitResult(center, hitFace, craftingTablePos, false)
            );
            restoreSneak.run();
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Organize", "§eTimeout opening crafting table.");
            startOverflow();
        }
    }

    // CRAFT_PLACING — place materials in crafting grid

    /*? if >=26.1 {*//*
    private void tickCraftPlacing(Minecraft mc) {
    *//*?} else {*/
    private void tickCraftPlacing(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (!(handler instanceof CraftingMenu craftHandler)) {
        *//*?} else {*/
        if (!(handler instanceof CraftingScreenHandler craftHandler)) {
        /*?}*/
            startOverflow();
            return;
        }

        // Crafting grid slots (1-9): shulker_shell in slot 2 (top-center),
        // chest in slot 5 (middle-center), shulker_shell in slot 8 (bottom-center)
        // But the recipe is shapeless-ish — actually it requires:
        // slot 1=shell, slot 5=chest, slot 9=shell  (using 1-indexed grid mapping)
        // In handler: result=0, grid=1-9, inv=10-36, hotbar=37-45

        // Find a shell stack (needs ≥2 in total inventory) and a chest stack
        int shellSlot = findItemSlotInInventory(mc.player, Items.SHULKER_SHELL, -1);
        /*? if >=26.1 {*//*
        if (shellSlot < 0) { mc.player.clientSideCloseContainer(); startOverflow(); return; }
        *//*?} else {*/
        if (shellSlot < 0) { mc.player.closeHandledScreen(); startOverflow(); return; }
        /*?}*/

        int chestSlot = findItemSlotInInventory(mc.player, Items.CHEST, -1);
        /*? if >=26.1 {*//*
        if (chestSlot < 0) { mc.player.clientSideCloseContainer(); startOverflow(); return; }
        *//*?} else {*/
        if (chestSlot < 0) { mc.player.closeHandledScreen(); startOverflow(); return; }
        /*?}*/

        // In handler: result=0, grid=1-9, inv=10-36, hotbar=37-45
        /*? if >=26.1 {*//*
        int syncId = craftHandler.containerId;
        *//*?} else {*/
        int syncId = craftHandler.syncId;
        /*?}*/
        int hShell = playerSlotToHandlerSlot(shellSlot, 10);
        int hChest = playerSlotToHandlerSlot(chestSlot, 10);

        // Use right-click (button=1) to place exactly 1 item per grid slot.
        // Left-click picks up the whole stack; right-click on an empty
        // grid slot drops exactly 1 from the cursor.
        /*? if >=26.1 {*//*
        ItemStack shellStack = mc.player.getInventory().getItem(shellSlot);
        *//*?} else {*/
        ItemStack shellStack = mc.player.getInventory().getStack(shellSlot);
        /*?}*/
        if (shellStack.getCount() >= 2) {
            // Single stack has ≥2 shells — pick up, right-click place 1 each
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, hShell, 0, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, hShell, 0, SlotActionType.PICKUP, mc.player);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, 2, 1, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, 2, 1, SlotActionType.PICKUP, mc.player);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, 8, 1, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, 8, 1, SlotActionType.PICKUP, mc.player);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, hShell, 0, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, hShell, 0, SlotActionType.PICKUP, mc.player);
            /*?}*/
        } else {
            // Shells split across slots — place one from each stack
            int shellSlot2 = findItemSlotInInventory(mc.player, Items.SHULKER_SHELL, shellSlot);
            /*? if >=26.1 {*//*
            if (shellSlot2 < 0) { mc.player.clientSideCloseContainer(); startOverflow(); return; }
            *//*?} else {*/
            if (shellSlot2 < 0) { mc.player.closeHandledScreen(); startOverflow(); return; }
            /*?}*/
            int hShell2 = playerSlotToHandlerSlot(shellSlot2, 10);
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, hShell, 0, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, hShell, 0, SlotActionType.PICKUP, mc.player);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, 2, 1, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, 2, 1, SlotActionType.PICKUP, mc.player);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, hShell, 0, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, hShell, 0, SlotActionType.PICKUP, mc.player);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, hShell2, 0, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, hShell2, 0, SlotActionType.PICKUP, mc.player);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, 8, 1, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, 8, 1, SlotActionType.PICKUP, mc.player);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, hShell2, 0, ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, hShell2, 0, SlotActionType.PICKUP, mc.player);
            /*?}*/
        }

        // Chest — pick up, right-click place 1 in grid slot 5, return rest
        /*? if >=26.1 {*//*
        mc.gameMode.handleContainerInput(syncId, hChest, 0, ContainerInput.PICKUP, mc.player);
        *//*?} else {*/
        mc.interactionManager.clickSlot(syncId, hChest, 0, SlotActionType.PICKUP, mc.player);
        /*?}*/
        /*? if >=26.1 {*//*
        mc.gameMode.handleContainerInput(syncId, 5, 1, ContainerInput.PICKUP, mc.player);
        *//*?} else {*/
        mc.interactionManager.clickSlot(syncId, 5, 1, SlotActionType.PICKUP, mc.player);
        /*?}*/
        /*? if >=26.1 {*//*
        mc.gameMode.handleContainerInput(syncId, hChest, 0, ContainerInput.PICKUP, mc.player);
        *//*?} else {*/
        mc.interactionManager.clickSlot(syncId, hChest, 0, SlotActionType.PICKUP, mc.player);
        /*?}*/

        state = State.CRAFT_TAKING;
        craftTicks = 0;
        actionCooldown = CLICK_COOLDOWN_TICKS;
    }

    // CRAFT_TAKING — take crafted shulker from output slot

    /*? if >=26.1 {*//*
    private void tickCraftTaking(Minecraft mc) {
    *//*?} else {*/
    private void tickCraftTaking(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }
        craftTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (!(handler instanceof CraftingMenu craftHandler)) {
        *//*?} else {*/
        if (!(handler instanceof CraftingScreenHandler craftHandler)) {
        /*?}*/
            startOverflow();
            return;
        }

        // Wait a tick for server to compute the recipe result
        if (craftTicks < 2) return;

        // Take from output slot 0
        /*? if >=26.1 {*//*
        ItemStack output = craftHandler.getSlot(0).getItem();
        *//*?} else {*/
        ItemStack output = craftHandler.getSlot(0).getStack();
        /*?}*/
        if (output.isEmpty()) {
            // Recipe didn't work — close and overflow
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
            ChatHelper.labelled("Organize", "§eCrafting failed — recipe not recognized.");
            startOverflow();
            return;
        }

        /*? if >=26.1 {*//*
        mc.gameMode.handleContainerInput(craftHandler.containerId, 0, 0,
        *//*?} else {*/
        mc.interactionManager.clickSlot(craftHandler.syncId, 0, 0,
        /*?}*/
                /*? if >=26.1 {*//*
                ContainerInput.QUICK_MOVE, mc.player);
                *//*?} else {*/
                SlotActionType.QUICK_MOVE, mc.player);
                /*?}*/
        shulkersToCraft--;

        if (shulkersToCraft > 0 && hasShulkerMaterialsInInventory(mc.player)
                && hasInventoryRoom(mc.player)) {
            // Craft another
            state = State.CRAFT_PLACING;
            actionCooldown = CLICK_COOLDOWN_TICKS;
        } else {
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
            ChatHelper.labelled("Organize", "§aCrafted shulker boxes.");
            if (consolidationMode) {
                // Return to consolidation — shulker is ready, now collect items
                advanceConsolidation();
            } else {
                // Regular mode — pack items into the shulker now
                state = State.SHULKER_SELECTING;
                shulkerTicks = 0;
            }
        }
    }

    // --- OVERFLOW — deposit remaining items + export report

    private void startOverflow() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        // Record what's left in inventory that we were trying to organize
        recordOverflowFromInventory(player);

        if (overflowItems.isEmpty()) {
            advanceToNextTask();
            return;
        }

        // Find an overflow chest: use emptyShulkerDest if set, otherwise
        // find first chest in region with room
        overflowChestPos = findOverflowChest();
        if (overflowChestPos == null) {
            ChatHelper.labelled("Organize", "§cNo chest available for overflow items!");
            exportOverflowReport();
            advanceToNextTask();
            return;
        }

        ChatHelper.labelled("Organize", "§eOverflow: depositing §f" + overflowItems.size()
                + "§e item types into overflow chest.");
        walkTarget = overflowChestPos;
        state = State.OVERFLOW_WALKING;
        openWaitTicks = 0;
    }

    /*? if >=26.1 {*//*
    private void tickOverflowOpening(Minecraft mc) {
    *//*?} else {*/
    private void tickOverflowOpening(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler) {
        /*?}*/
            state = State.OVERFLOW_DEPOSITING;
            actionSlotIndex = 0;
            actionCooldown = 0;
            return;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (openWaitTicks == 1) {
            /*? if >=26.1 {*//*
            lookAt(player, Vec3.atCenterOf(overflowChestPos));
            *//*?} else {*/
            lookAt(player, Vec3d.ofCenter(overflowChestPos));
            /*?}*/
        }

        if (openWaitTicks == 3) {
            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

            /*? if >=26.1 {*//*
            Vec3 center = Vec3.atCenterOf(overflowChestPos);
            *//*?} else {*/
            Vec3d center = Vec3d.ofCenter(overflowChestPos);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toTarget = center.subtract(player.getEyePosition());
            *//*?} else {*/
            Vec3d toTarget = center.subtract(player.getEyePos());
            /*?}*/
            /*? if >=26.1 {*//*
            Direction hitFace = Direction.getApproximateNearest(
            *//*?} else {*/
            Direction hitFace = Direction.getFacing(
            /*?}*/
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);

            /*? if >=26.1 {*//*
            mc.gameMode.useItemOn(
            *//*?} else {*/
            mc.interactionManager.interactBlock(
            /*?}*/
                    player,
                    /*? if >=26.1 {*//*
                    InteractionHand.MAIN_HAND,
                    *//*?} else {*/
                    Hand.MAIN_HAND,
                    /*?}*/
                    new BlockHitResult(center, hitFace, overflowChestPos, false)
            );

            restoreSneak.run();
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Organize", "§eTimeout opening overflow chest.");
            exportOverflowReport();
            advanceToNextTask();
        }
    }

    /*? if >=26.1 {*//*
    private void tickOverflowDepositing(Minecraft mc) {
    *//*?} else {*/
    private void tickOverflowDepositing(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

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
            exportOverflowReport();
            advanceToNextTask();
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlotCount = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlotCount = containerHandler.getRows() * 9;
        /*?}*/

        // Deposit everything possible from inventory (skip hotbar)
        if (actionSlotIndex < HOTBAR_SIZE) actionSlotIndex = HOTBAR_SIZE;
        while (actionSlotIndex < 36) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(actionSlotIndex);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(actionSlotIndex);
            /*?}*/
            if (!stack.isEmpty()) {
                if (!hasChestRoom(containerHandler)) {
                    ChatHelper.labelled("Organize", "§eOverflow chest full.");
                    break;
                }

                int containerSlotIndex;
                if (actionSlotIndex < 9) {
                    containerSlotIndex = chestSlotCount + 27 + actionSlotIndex;
                } else {
                    containerSlotIndex = chestSlotCount + actionSlotIndex - 9;
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
                actionSlotIndex++;
                actionCooldown = CLICK_COOLDOWN_TICKS;
                return;
            }
            actionSlotIndex++;
        }

        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        exportOverflowReport();
        advanceToNextTask();
    }

    // Consolidation helpers

    /** Advance the consolidation phase: prepare shulker → collect → pack → store. */
    private void advanceConsolidation() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/

        // Phase 1: If we already have items ready to pack, pack them now
        if (hasOrganizableItemsInInventory(mc.player)) {
            startConsolidationPacking();
            return;
        }

        // Phase 2: Nothing left to collect → done
        if (consolidationQueue.isEmpty()) {
            consolidationMode = false;
            finishOrganization();
            return;
        }

        // Phase 3: Prepare an empty shulker BEFORE collecting items.
        // Otherwise inventory fills up and there's no room for crafting materials.
        if (findEmptyShulkerInInventory(mc.player) < 0) {
            MoveTask peek = consolidationQueue.peek();
            packDestination = peek != null ? peek.destination() : findOverflowChest();

            BlockPos shulkerSource = findEmptyShulkerInRegion();
            if (shulkerSource != null) {
                ChatHelper.labelled("Organize", "§7Fetching shulker for consolidation...");
                walkTarget = shulkerSource;
                state = State.SHULKER_FETCH_WALK;
                openWaitTicks = 0;
                return;
            }
            if (canCraftShulkers()) {
                packItemId = null;
                startCrafting();
                return;
            }
            // Can't get shulkers — fall back to organizing loose items into chests
            ChatHelper.labelled("Organize", "§eNo shulkers available — moving loose items to chests instead.");
            while (!consolidationQueue.isEmpty()) {
                MoveTask task = consolidationQueue.poll();
                if (!task.source().equals(task.destination())) {
                    taskQueue.add(task);
                }
            }
            consolidationMode = false;
            if (!taskQueue.isEmpty()) {
                totalTasks += taskQueue.size();
                advanceToNextTask();
            } else {
                finishOrganization();
            }
            return;
        }

        // Phase 4: We have an empty shulker — collect next batch of items
        currentTask = consolidationQueue.poll();
        currentRole = TargetRole.SOURCE;
        walkTarget = currentTask.source();
        actionSlotIndex = 0;
        state = State.WALKING;
    }

    /** Begin packing all misc items in inventory into a mixed shulker box. */
    private void startConsolidationPacking() {
        // Use the destination from the first consolidation task's column
        packDestination = (currentTask != null && currentTask.destination() != null)
                ? currentTask.destination() : findOverflowChest();
        if (packDestination == null) {
            ChatHelper.labelled("Organize", "§eNo destination for consolidated shulker.");
            consolidationMode = false;
            finishOrganization();
            return;
        }

        String itemToPack = (currentTask != null) ? currentTask.itemId() : null;
        ChatHelper.labelled("Organize", "§7Packing " + (itemToPack != null ? itemToPack : "misc") + " into shulker box...");
        startShulkerPacking(itemToPack, packDestination);
    }

    /** True if the player has consolidation-relevant items in inventory. */
    /*? if >=26.1 {*//*
    private boolean hasOrganizableItemsInInventory(LocalPlayer player) {
    *//*?} else {*/
    private boolean hasOrganizableItemsInInventory(ClientPlayerEntity player) {
    /*?}*/
        // Build the set of item types we're actively consolidating
        Set<String> consolidationTypes = new HashSet<>();
        for (MoveTask task : consolidationQueue) {
            consolidationTypes.add(task.itemId());
        }
        if (currentTask != null) consolidationTypes.add(currentTask.itemId());
        if (packItemId != null) consolidationTypes.add(packItemId);

        for (int i = HOTBAR_SIZE; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty()) {
                if (ChestManager.isShulkerBox(stack)) continue;
                String itemId = ItemIdentifier.getItemId(stack);
                if (consolidationTypes.contains(itemId)) return true;
            }
        }
        return false;
    }

    // Navigation helpers

    private void transitionToDestination() {
        if (currentTask == null) {
            advanceToNextTask();
            return;
        }
        currentRole = TargetRole.DESTINATION;
        walkTarget = currentTask.destination();
        actionSlotIndex = 0;
        depositColumnIndex = 0;
        state = State.WALKING;
    }

    private void advanceToNextTask() {
        if (taskQueue.isEmpty()) {
            if (!consolidationMode && !consolidationQueue.isEmpty()) {
                // Switch to consolidation phase
                consolidationMode = true;
                ChatHelper.labelled("Organize",
                        "§7Starting condensing — packing loose items into shulker boxes...");
                advanceConsolidation();
                return;
            }
            consolidationMode = false;
            finishOrganization();
            return;
        }

        currentTask = taskQueue.poll();
        currentRole = TargetRole.SOURCE;
        walkTarget = currentTask.source();
        actionSlotIndex = 0;
        state = State.WALKING;
    }

    private void finishOrganization() {
        PathWalker.stop();
        state = State.DONE;
        ChatHelper.labelled("Organize", "§aOrganization complete! §f"
                + completedTasks + "§a moves executed.");

        if (!overflowItems.isEmpty()) {
            ChatHelper.labelled("Organize", "§e" + overflowItems.size()
                    + " item types overflowed — see report.");
        }

        if (stashManager != null) {
            stashManager.assignLabels();
        }

        ChatHelper.labelled("Organize", "§7Run §f/stash scan §7to refresh the index.");
    }

    // Look helper

    /*? if >=26.1 {*//*
    private static void lookAt(LocalPlayer player, Vec3 target) {
    *//*?} else {*/
    private static void lookAt(ClientPlayerEntity player, Vec3d target) {
    /*?}*/
        /*? if >=26.1 {*//*
        Vec3 eye = player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toTarget = target.subtract(eye);
        *//*?} else {*/
        Vec3d toTarget = target.subtract(eye);
        /*?}*/
        double horizDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        /*? if >=26.1 {*//*
        float yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0f;
        *//*?} else {*/
        float yaw = (float) (MathHelper.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0f;
        /*?}*/
        /*? if >=26.1 {*//*
        float pitch = (float) -(Mth.atan2(toTarget.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float pitch = (float) -(MathHelper.atan2(toTarget.y, horizDist) * (180.0 / Math.PI));
        /*?}*/
        /*? if >=26.1 {*//*
        PlacementEngine.sendLookPacket(player, yaw, Mth.clamp(pitch, -90.0f, 90.0f));
        *//*?} else {*/
        PlacementEngine.sendLookPacket(player, yaw, MathHelper.clamp(pitch, -90.0f, 90.0f));
        /*?}*/
    }

    // Region filtering

    private Map<BlockPos, ContainerEntry> getRegionContainers() {
        BlockPos c1 = stashManager.getCorner1();
        BlockPos c2 = stashManager.getCorner2();
        int minX = Math.min(c1.getX(), c2.getX());
        int minY = Math.min(c1.getY(), c2.getY());
        int minZ = Math.min(c1.getZ(), c2.getZ());
        int maxX = Math.max(c1.getX(), c2.getX());
        int maxY = Math.max(c1.getY(), c2.getY());
        int maxZ = Math.max(c1.getZ(), c2.getZ());

        Map<BlockPos, ContainerEntry> result = new LinkedHashMap<>();
        for (var entry : stashManager.getIndex().entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                result.put(pos, entry.getValue());
            }
        }
        return result;
    }

    // Inventory helpers

    /*? if >=26.1 {*//*
    private boolean hasInventoryRoom(LocalPlayer player) {
    *//*?} else {*/
    private boolean hasInventoryRoom(ClientPlayerEntity player) {
    /*?}*/
        for (int i = HOTBAR_SIZE; i < 36; i++) {
            /*? if >=26.1 {*//*
            if (player.getInventory().getItem(i).isEmpty()) return true;
            *//*?} else {*/
            if (player.getInventory().getStack(i).isEmpty()) return true;
            /*?}*/
        }
        return false;
    }

    /*? if >=26.1 {*//*
    private boolean hasChestRoom(ChestMenu handler) {
    *//*?} else {*/
    private boolean hasChestRoom(GenericContainerScreenHandler handler) {
    /*?}*/
        /*? if >=26.1 {*//*
        int slots = handler.getRowCount() * 9;
        *//*?} else {*/
        int slots = handler.getRows() * 9;
        /*?}*/
        for (int i = 0; i < slots; i++) {
            /*? if >=26.1 {*//*
            if (handler.getSlot(i).getItem().isEmpty()) return true;
            *//*?} else {*/
            if (handler.getSlot(i).getStack().isEmpty()) return true;
            /*?}*/
        }
        return false;
    }

    /*? if >=26.1 {*//*
    private boolean hasShulkerRoom(ShulkerBoxMenu handler) {
    *//*?} else {*/
    private boolean hasShulkerRoom(ShulkerBoxScreenHandler handler) {
    /*?}*/
        for (int i = 0; i < 27; i++) {
            /*? if >=26.1 {*//*
            if (handler.getSlot(i).getItem().isEmpty()) return true;
            *//*?} else {*/
            if (handler.getSlot(i).getStack().isEmpty()) return true;
            /*?}*/
        }
        return false;
    }

    /** Find an empty shulker box in the player's inventory. Returns slot index or -1. */
    /*? if >=26.1 {*//*
    private int findEmptyShulkerInInventory(LocalPlayer player) {
    *//*?} else {*/
    private int findEmptyShulkerInInventory(ClientPlayerEntity player) {
    /*?}*/
        for (int i = HOTBAR_SIZE; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (ChestManager.isShulkerBox(stack)) {
                // Check if shulker is empty (no container component or empty contents)
                /*? if >=26.1 {*//*
                var cc = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
                *//*?} else if >=26.1 {*//*
                var cc = stack.get(net.minecraft.component.DataComponents.CONTAINER);
                *//*?} else {*/
                var cc = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
                /*?}*/
                /*? if >=26.1 {*//*
                if (cc == null || !cc.nonEmptyItems().iterator().hasNext()) {
                *//*?} else {*/
                if (cc == null || !cc.iterateNonEmpty().iterator().hasNext()) {
                /*?}*/
                    return i;
                }
            }
        }
        return -1;
    }

    /** Find a container in the region that has empty shulker boxes. */
    private BlockPos findEmptyShulkerInRegion() {
        Map<BlockPos, ContainerEntry> region = getRegionContainers();
        for (var entry : region.entrySet()) {
            ContainerEntry container = entry.getValue();
            for (String itemId : container.items().keySet()) {
                if (isShulkerBoxItem(itemId) && allShulkersEmpty(container, itemId)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /*? if >=26.1 {*//*
    private int countItemInInventory(LocalPlayer player, String itemId) {
    *//*?} else {*/
    private int countItemInInventory(ClientPlayerEntity player, String itemId) {
    /*?}*/
        int count = 0;
        for (int i = HOTBAR_SIZE; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty() && ItemIdentifier.getItemId(stack).equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int countItemInRegion(String itemId) {
        int total = 0;
        for (var entry : getRegionContainers().values()) {
            total += entry.items().getOrDefault(itemId, 0);
        }
        return total;
    }

    /** Find a specific item in the player's inventory, optionally skipping a slot. */
    /*? if >=26.1 {*//*
    private int findItemSlotInInventory(LocalPlayer player, Item item, int skipSlot) {
    *//*?} else {*/
    private int findItemSlotInInventory(ClientPlayerEntity player, Item item, int skipSlot) {
    /*?}*/
        for (int i = 0; i < 36; i++) {
            if (i == skipSlot) continue;
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    /*? if >=26.1 {*//*
    private boolean hasShulkerMaterialsInInventory(LocalPlayer player) {
    *//*?} else {*/
    private boolean hasShulkerMaterialsInInventory(ClientPlayerEntity player) {
    /*?}*/
        int shells = 0;
        boolean hasChest = false;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            if (stack.getItem() == Items.SHULKER_SHELL) shells += stack.getCount();
            if (stack.getItem() == Items.CHEST) hasChest = true;
        }
        return shells >= 2 && hasChest;
    }

    private boolean canCraftShulkers() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        int shells = countItemInRegion("minecraft:shulker_shell")
                   + countItemInInventory(mc.player, "minecraft:shulker_shell");
        int chests = countItemInRegion("minecraft:chest")
                   + countItemInInventory(mc.player, "minecraft:chest");
        if (shells < 2 || chests < 1) return false;

        /*? if >=26.1 {*//*
        return findCraftingTable(mc.player, mc.level) != null;
        *//*?} else {*/
        return findCraftingTable(mc.player, mc.world) != null;
        /*?}*/
    }

    /** Map a player inventory slot (0-35) to a crafting handler slot. */
    private int playerSlotToHandlerSlot(int invSlot, int handlerInvStart) {
        // handler: invStart..(invStart+26) = main inv (slots 9-35),
        //          (invStart+27)..(invStart+35) = hotbar (slots 0-8)
        if (invSlot < 9) {
            return handlerInvStart + 27 + invSlot; // hotbar
        } else {
            return handlerInvStart + invSlot - 9; // main inventory
        }
    }

    // World helpers

    /*? if >=26.1 {*//*
    private BlockPos findShulkerPlaceSpot(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findShulkerPlaceSpot(ClientPlayerEntity player, World world) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerFeet = player.blockPosition();
        *//*?} else {*/
        BlockPos playerFeet = player.getBlockPos();
        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerHead = playerFeet.above();
        *//*?} else {*/
        BlockPos playerHead = playerFeet.up();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = playerFeet.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = playerFeet.add(dx, dy, dz);
                    /*?}*/
                    // Skip positions at or adjacent to player's feet or head
                    if (pos.equals(playerFeet) || pos.equals(playerHead)) continue;
                    if (pos.equals(playerFeet.north()) || pos.equals(playerFeet.south())
                            || pos.equals(playerFeet.east()) || pos.equals(playerFeet.west())) continue;
                    // Skip the previous failed position
                    if (pos.equals(shulkerPlacePos) && shulkerPlaceRetries > 0) continue;
                    BlockState posState = world.getBlockState(pos);
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
                    if ((posState.isAir() || posState.canBeReplaced())
                    *//*?} else {*/
                    if ((posState.isAir() || posState.isReplaceable())
                    /*?}*/
                            /*? if >=26.1 {*//*
                            && !below.getCollisionShape(world, pos.below()).isEmpty()
                            *//*?} else {*/
                            && !below.getCollisionShape(world, pos.down()).isEmpty()
                            /*?}*/
                            /*? if >=26.1 {*//*
                            && (above.isAir() || above.canBeReplaced())) {
                            *//*?} else {*/
                            && (above.isAir() || above.isReplaceable())) {
                            /*?}*/
                        /*? if >=26.1 {*//*
                        double dist = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                        *//*?} else {*/
                        double dist = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                        /*?}*/
                        // Prefer spots 2+ blocks away to avoid self-placement issues
                        if (dist >= 2.0 * 2.0 && dist <= 4.5 * 4.5 && dist < bestDist) {
                            bestDist = dist;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Find a crafting table within 32 blocks of the player or in the stash region. */
    /*? if >=26.1 {*//*
    private BlockPos findCraftingTable(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findCraftingTable(ClientPlayerEntity player, World world) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos center = player.blockPosition();
        *//*?} else {*/
        BlockPos center = player.getBlockPos();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        // Search a 32-block radius around the player
        for (int dx = -32; dx <= 32; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                for (int dy = -8; dy <= 8; dy++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = center.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = center.add(dx, dy, dz);
                    /*?}*/
                    /*? if >=26.1 {*//*
                    if (world.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.CraftingTableBlock) {
                    *//*?} else {*/
                    if (world.getBlockState(pos).getBlock() instanceof net.minecraft.block.CraftingTableBlock) {
                    /*?}*/
                        /*? if >=26.1 {*//*
                        double dist = player.distanceToSqr(Vec3.atCenterOf(pos));
                        *//*?} else {*/
                        double dist = player.squaredDistanceTo(Vec3d.ofCenter(pos));
                        /*?}*/
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Find a chest in the region (or emptyShulkerDest) that has room for overflow. */
    private BlockPos findOverflowChest() {
        if (emptyShulkerDest != null) return emptyShulkerDest;

        // Find first container in region — we'll use it as overflow
        Map<BlockPos, ContainerEntry> region = getRegionContainers();
        for (var entry : region.entrySet()) {
            // Prefer containers with fewer items (more room)
            if (entry.getValue().totalItemCount() < 27 * 64) {
                return entry.getKey();
            }
        }
        return region.isEmpty() ? null : region.keySet().iterator().next();
    }

    // Overflow report

    /*? if >=26.1 {*//*
    private void recordOverflowFromInventory(LocalPlayer player) {
    *//*?} else {*/
    private void recordOverflowFromInventory(ClientPlayerEntity player) {
    /*?}*/
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty()) {
                String id = ItemIdentifier.getItemId(stack);
                // Only track items we were trying to organize (skip tools, food, etc.)
                if (packItemId != null && id.equals(packItemId)) {
                    overflowItems.merge(id, stack.getCount(), Integer::sum);
                }
            }
        }
    }

    private void exportOverflowReport() {
        if (overflowItems.isEmpty()) return;

        try {
            Path dir = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("moar");
            Files.createDirectories(dir);
            Path reportFile = dir.resolve("overflow_report.csv");

            StringBuilder sb = new StringBuilder();
            sb.append("item_id,quantity,reason\n");
            for (var entry : overflowItems.entrySet()) {
                sb.append(entry.getKey()).append(',')
                  .append(entry.getValue()).append(',')
                  .append("insufficient_shulkers_or_materials\n");
            }

            Files.writeString(reportFile, sb.toString());
            ChatHelper.labelled("Organize", "§eOverflow report: §f" + reportFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to export overflow report", e);
            ChatHelper.labelled("Organize", "§cFailed to write overflow report.");
        }
    }

    // Item type helpers

    private static boolean isShulkerBoxItem(String itemId) {
        return itemId.contains("shulker_box");
    }

    private static boolean allShulkersEmpty(ContainerEntry container, String shulkerItemId) {
        String shulkerType = shulkerItemId.replace("minecraft:", "");
        for (var sd : container.shulkerDetails()) {
            if (sd.shulkerType().equals(shulkerType) && !sd.contents().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // Return the dominant item type in a shulker's contents, or null if empty.
    private static String getPrimaryContent(Map<String, Integer> contents) {
        if (contents == null || contents.isEmpty()) return null;
        return contents.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // Check if a chest slot's shulker box has the given primary content.
    private static boolean shulkerMatchesContent(ItemStack stack, String contentFilter) {
        if (!ChestManager.isShulkerBox(stack)) return false;
        Map<String, Integer> contents = ItemIdentifier.readShulkerContents(stack);
        return contentFilter.equals(getPrimaryContent(contents));
    }

    // Status

    public String getStatus() {
        String detail = switch (state) {
            case IDLE              -> "Idle";
            case PLANNING          -> "Planning...";
            case WALKING           -> "Walking to "
                    + (currentRole == TargetRole.SOURCE ? "source" : "destination") + "...";
            case OPENING           -> "Opening container...";
            case TAKING            -> "Taking items...";
            case DEPOSITING        -> "Depositing items...";
            case SHULKER_SELECTING, SHULKER_PLACING, SHULKER_WAIT_PLACE
                                   -> "Preparing shulker...";
            case SHULKER_OPENING   -> "Opening shulker...";
            case SHULKER_FILLING   -> "Filling shulker...";
            case SHULKER_CLOSING, SHULKER_BREAKING
                                   -> "Breaking shulker...";
            case SHULKER_PICKUP    -> "Picking up shulker...";
            case SHULKER_STORE_WALK, SHULKER_STORE_OPEN, SHULKER_STORE_DEPOSIT
                                   -> "Storing filled shulker...";
            case SHULKER_FETCH_WALK, SHULKER_FETCH_OPEN, SHULKER_FETCH_TAKE
                                   -> "Fetching empty shulker...";
            case CRAFT_MATERIAL_WALK, CRAFT_MATERIAL_OPEN, CRAFT_MATERIAL_TAKE
                                   -> "Collecting crafting materials...";
            case CRAFT_WALKING, CRAFT_OPENING
                                   -> "Walking to crafting table...";
            case CRAFT_PLACING, CRAFT_TAKING
                                   -> "Crafting shulker boxes...";
            case OVERFLOW_WALKING, OVERFLOW_OPENING, OVERFLOW_DEPOSITING
                                   -> "Depositing overflow items...";
            case DONE              -> "Done";
        };
        if (totalTasks > 0) {
            detail += " [" + completedTasks + "/" + totalTasks + "]";
        }
        return detail;
    }
}

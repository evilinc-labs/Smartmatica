package dev.moar.stash;

import dev.moar.MoarMod;
import dev.moar.world.SetbackMonitor;
import dev.moar.chest.ChestManager;
import dev.moar.stash.StashDatabase.SearchResult;
import dev.moar.util.ChatHelper;
import dev.moar.util.ItemIdentifier;
import dev.moar.util.PathWalker;
import dev.moar.util.PlacementEngine;/*? if >=26.1 {*//*
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
import net.minecraft.world.item.ItemStack;
*//*?} else {*/
import net.minecraft.item.ItemStack;
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
import net.minecraft.world.level.block.ShulkerBoxBlock;
*//*?} else {*/
import net.minecraft.block.ShulkerBoxBlock;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.BlockState;
*//*?} else {*/
import net.minecraft.block.BlockState;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.entity.player.Inventory;
*//*?} else {*/
import net.minecraft.entity.player.PlayerInventory;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ShulkerBoxMenu;
*//*?} else {*/
import net.minecraft.screen.ShulkerBoxScreenHandler;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight state machine that walks to containers and retrieves items
 * into the player's inventory.  Driven by /stash get.
 */
public final class StashRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Retriever");

    private static final int OPEN_TIMEOUT_TICKS = 60;
    // Slower pacing to keep under Grim/Folia thresholds.
    private static final int CLICK_COOLDOWN_TICKS = 5;
    private static final int CHEST_SYNC_DELAY = 3;
    private static final int OPEN_RETRY_INTERVAL_TICKS = 14;
    private static final int HOTBAR_SIZE = 9;
    /** Hard ceiling on consecutive container failures before giving up the run. */
    private static final int MAX_CONSECUTIVE_FAILURES = 4;

    public enum State { IDLE, WALKING, OPENING, TAKING, UNLOADING_SHULKER }

    private State state = State.IDLE;

    // Current retrieval target
    private String targetItemId;
    private int targetCount;
    private int takenCount;

    /** Kit-mode: item_id -> quantity still needed. null for single-item mode. */
    private Map<String, Integer> kitRemaining;

    // Walking / opening
    private final Deque<BlockPos> containerQueue = new ArrayDeque<>();
    private BlockPos walkTarget;
    private int openWaitTicks;
    private int syncTicks;
    private int actionSlotIndex;
    private int actionCooldown;

    // Partial-take split state. Used when a source slot has more items than
    // we need; we left-click to pick the stack up, right-click the source
    // (count-needed) times to deposit the excess back, then left-click an
    // empty player slot to drop the kept portion. One click per tick.
    private boolean splitInProgress;
    private int splitSrcSlot = -1;
    private int splitPutbacksLeft;
    private int splitNeeded;
    private boolean splitCursorReady;
    private String splitItemId;

    // Shulker unloading
    private static final int MAX_SHULKER_FAILURES = 2;
    private static final int SHULKER_PHASE_TIMEOUT = 80;
    private static final int SHULKER_PICKUP_DELAY = 10;
    private static final int SHULKER_TOTAL_TIMEOUT = 600;
    private static final int SHULKER_SWAP_SETTLE    = 6;  // ticks after hotbar SWAP before looking at target
    private static final int SHULKER_LOOK_SETTLE    = 4;  // ticks of stable rotation before placement packet
    private static final int MAX_SHULKER_OPEN_RETRIES = 3;
    private static final int SHULKER_PLACE_CALM_TICKS = 18;
    private static final int SHULKER_PLACE_RECENT_WINDOW = 24;
    private static final int SHULKER_PLACE_STATIONARY_TICKS = 5;
    private int shulkerPhase;
    private int shulkerTicks;
    private int shulkerTotalTicks;
    private BlockPos shulkerPos;
    private int shulkerSlot = -1;
    private float savedYaw, savedPitch;
    private int shulkerFailures;
    private int shulkerOpenRetries;
    private Runnable shulkerSneakRestore;

    /** Positions that have failed this run; skipped when re-encountered. */
    private final Set<BlockPos> failedContainers = new LinkedHashSet<>();
    /** Player inventory slots currently holding shulkers fetched by this run. */
    private final Set<Integer> ownedShulkerSlots = new LinkedHashSet<>();
    /** Fingerprint of a shulker we just quick-moved out of a container. */
    private String pendingOwnedShulkerFingerprint;
    /** Empty player slots that could receive the pending quick-moved shulker. */
    private final Set<Integer> pendingOwnedShulkerCandidateSlots = new LinkedHashSet<>();
    /** Consecutive container failures (open timeout, unreachable, shulker fail). */
    private int consecutiveFailures;
    /** True once we've seen a live world+player; used to detect disconnect. */
    private boolean wasInWorld;

    /**
     * Containers managed by ChestManager (supply/dump/storage) must NEVER be
     * touched by /stash get — those are reserved for the printer/sorter and
     * the user has explicitly told us to leave them alone.
     */
    private static Set<BlockPos> getBlacklistedContainerPositions() {
        Set<BlockPos> out = new java.util.HashSet<>();
        ChestManager cm = MoarMod.getChestManager();
        if (cm == null) return out;
        out.addAll(cm.getSupplyPositions());
        out.addAll(cm.getDumpPositions());
        out.addAll(cm.getStorageChests());
        return out;
    }

    // Public API

    public State getState() { return state; }
    public boolean isActive() { return state != State.IDLE; }

    /**
     * Start retrieval: find containers holding the item, queue them
     * closest-first, and begin walking.
     */
    public boolean start(String itemIdFragment, int count) {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) {
            ChatHelper.labelled("Stash", "§cDatabase not available.");
            return false;
        }

        List<SearchResult> results = db.searchItem(itemIdFragment);
        if (results.isEmpty()) {
            ChatHelper.labelled("Stash", "§cNo containers found with '§f" + itemIdFragment + "§c'.");
            return false;
        }

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return false;

        // Resolve the exact item ID from the first result
        // (user may have passed a fragment like "diamond" — we need the full ID)
        String exactId = null;
        for (SearchResult sr : results) {
            for (String id : sr.matchedItems().keySet()) {
                if (id.contains(itemIdFragment)) {
                    exactId = id;
                    break;
                }
            }
            if (exactId != null) break;
        }
        if (exactId == null) exactId = results.getFirst().matchedItems().keySet().iterator().next();

        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/

        // Sort results by distance to player
        String finalExactId = exactId;
        results.sort((a, b) -> {
            int qtyA = a.matchedItems().getOrDefault(finalExactId, 0);
            int qtyB = b.matchedItems().getOrDefault(finalExactId, 0);
            if (qtyA == 0 && qtyB > 0) return 1;
            if (qtyB == 0 && qtyA > 0) return -1;
            /*? if >=26.1 {*//*
            double distA = playerPos.distSqr(a.pos());
            double distB = playerPos.distSqr(b.pos());
            *//*?} else {*/
            double distA = playerPos.getSquaredDistance(a.pos());
            double distB = playerPos.getSquaredDistance(b.pos());
            /*?}*/
            return Double.compare(distA, distB);
        });

        containerQueue.clear();
        Set<BlockPos> blacklist = getBlacklistedContainerPositions();
        int skipped = 0;
        for (SearchResult sr : results) {
            if (sr.matchedItems().containsKey(exactId)) {
                if (blacklist.contains(sr.pos())) { skipped++; continue; }
                containerQueue.add(sr.pos());
            }
        }

        if (containerQueue.isEmpty()) {
            if (skipped > 0) {
                ChatHelper.labelled("Stash", "§cAll " + skipped + " container(s) holding '§f"
                        + exactId + "§c' are blacklisted (supply/dump/storage).");
            } else {
                ChatHelper.labelled("Stash", "§cNo containers hold exact item '§f" + exactId + "§c'.");
            }
            return false;
        }

        targetItemId = exactId;
        targetCount = count;
        takenCount = 0;
        kitRemaining = null;
        failedContainers.clear();
        consecutiveFailures = 0;
        resetTransientState();

        PathWalker.setBreakingAllowed(false);
        advanceToNextContainer();
        String shortId = exactId.startsWith("minecraft:") ? exactId.substring(10) : exactId;
        ChatHelper.labelled("Stash", "§aRetrieving §f" + shortId + " §7x" + count
                + " §afrom " + containerQueue.size() + " container(s)...");
        return true;
    }

    /** Start kit-mode retrieval: find kit items in stash DB, walk to containers, collect. */
    public boolean startKit(String kitName, Map<String, Integer> kitItems) {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) {
            ChatHelper.labelled("Stash", "§cDatabase not available.");
            return false;
        }

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc == null || mc.player == null) return false;

        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/

        // Find containers holding kit items (incl. shulker contents).
        Map<BlockPos, Map<String, Integer>> containerMap = db.findContainersForExactItems(kitItems.keySet());
        Set<BlockPos> blacklist = getBlacklistedContainerPositions();
        Set<BlockPos> containers = new LinkedHashSet<>();
        int skipped = 0;
        for (BlockPos p : containerMap.keySet()) {
            if (blacklist.contains(p)) { skipped++; continue; }
            containers.add(p);
        }

        if (containers.isEmpty()) {
            if (skipped > 0) {
                ChatHelper.labelled("Stash", "§cAll " + skipped
                        + " container(s) for kit '§e" + kitName + "§c' are blacklisted.");
            } else {
                ChatHelper.labelled("Stash", "§cNo containers found holding items from kit '§e" + kitName + "§c'.");
            }
            return false;
        }

        // Sort by distance to player
        List<BlockPos> sorted = new java.util.ArrayList<>(containers);
        sorted.sort((a, b) -> {
            /*? if >=26.1 {*//*
            return Double.compare(playerPos.distSqr(a), playerPos.distSqr(b));
            *//*?} else {*/
            return Double.compare(playerPos.getSquaredDistance(a), playerPos.getSquaredDistance(b));
            /*?}*/
        });

        containerQueue.clear();
        containerQueue.addAll(sorted);

        kitRemaining = new LinkedHashMap<>(kitItems);
        targetItemId = null;
        targetCount = 0;
        takenCount = 0;
        failedContainers.clear();
        consecutiveFailures = 0;
        resetTransientState();

        PathWalker.setBreakingAllowed(false);
        advanceToNextContainer();
        ChatHelper.labelled("Stash", "§aLoading kit '§e" + kitName + "§a' (" + kitItems.size()
                + " items from " + containers.size() + " container(s))...");
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
        resetRunState();
        kitRemaining = null;
        PathWalker.setBreakingAllowed(true);
        ChatHelper.labelled("Stash", "§eRetrieval stopped.");
    }

    // Tick

    public void tick() {
        if (state == State.IDLE) return;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        boolean inWorld = mc != null && mc.player != null && mc.level != null;
        *//*?} else {*/
        boolean inWorld = mc != null && mc.player != null && mc.world != null;
        /*?}*/
        if (!inWorld) {
            // Lost world/player (disconnect, dimension change). Drop active state
            // so we don't resume on rejoin/reload.
            if (wasInWorld) {
                resetRunState();
                PathWalker.setBreakingAllowed(true);
                wasInWorld = false;
            }
            return;
        }
        wasInWorld = true;

        switch (state) {
            case WALKING          -> tickWalking(mc);
            case OPENING          -> tickOpening(mc);
            case TAKING           -> tickTaking(mc);
            case UNLOADING_SHULKER -> tickUnloadingShulker(mc);
        }
    }

    // State handlers

    /*? if >=26.1 {*//*
    private void tickWalking(Minecraft mc) {
    *//*?} else {*/
    private void tickWalking(MinecraftClient mc) {
    /*?}*/
        if (walkTarget == null) { finish(); return; }

        /*? if >=26.1 {*//*
        double distSq = mc.player.position().distanceToSqr(
        *//*?} else {*/
        double distSq = mc.player.squaredDistanceTo(
        /*?}*/
                walkTarget.getX() + 0.5,
                walkTarget.getY() + 0.5,
                walkTarget.getZ() + 0.5);

        if (distSq <= 4.5 * 4.5) {
            PathWalker.stop();
            openWaitTicks = 0;
            state = State.OPENING;
            return;
        }

        if (!PathWalker.isActive()) {
            PathWalker.walkToAdjacent(walkTarget);
        }

        if (PathWalker.hasArrived()) {
            PathWalker.stop();
            openWaitTicks = 0;
            state = State.OPENING;
            return;
        }

        if (PathWalker.isStuck()) {
            PathWalker.stop();
            ChatHelper.labelled("Stash", "§eCan't reach "
                    + walkTarget.getX() + " " + walkTarget.getY() + " " + walkTarget.getZ()
                    + ", trying next...");
            BlockPos failed = walkTarget;
            recordContainerFailure(failed);
            if (state == State.IDLE) return;
            advanceToNextContainer();
        }

        PathWalker.tick();
    }

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
            syncTicks = 0;
            state = State.TAKING;
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

        if (openWaitTicks >= 3
            && (openWaitTicks == 3 || openWaitTicks % OPEN_RETRY_INTERVAL_TICKS == 0)
            && SetbackMonitor.get().isCalm()) {
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
            ChatHelper.labelled("Stash", "§eTimeout opening container, trying next...");
            recordContainerFailure(walkTarget);
            if (state == State.IDLE) return;
            advanceToNextContainer();
        }
    }

    /*? if >=26.1 {*//*
    private void tickTaking(Minecraft mc) {
    *//*?} else {*/
    private void tickTaking(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

        resolvePendingOwnedShulker(mc.player);

        // Drive any in-progress partial-take split first.
        if (splitInProgress) {
            /*? if >=26.1 {*//*
            AbstractContainerMenu h = mc.player.containerMenu;
            *//*?} else {*/
            ScreenHandler h = mc.player.currentScreenHandler;
            /*?}*/
            if (h == null) { resetSplit(); return; }
            if (tickSplitTake(mc, h)) {
                actionCooldown = CLICK_COOLDOWN_TICKS;
                return;
            }
        }

        // Wait for server to sync slot contents
        syncTicks++;
        if (syncTicks <= CHEST_SYNC_DELAY) return;

        // Check if we've taken enough
        if (isDone()) {
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/
            finish();
            return;
        }

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
            advanceToNextContainer();
            return;
        }

        /*? if >=26.1 {*//*
        int chestSlots = containerHandler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlots = containerHandler.getRows() * 9;
        /*?}*/

        // Pass 0 (printer-style): deposit any shulkers in player inventory
        // that no longer hold needed items. After a failed open + break the
        // shulker may still be in our inventory and we want it back in the
        // chest, not stuck on us. Player slots in a chest GUI start at
        // chestSlots and run for 36 slots.
        if (actionSlotIndex == 0) {
            int playerSlotStart = chestSlots;
            int playerSlotEnd = chestSlots + 36;
            for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
                /*? if >=26.1 {*//*
                ItemStack invStack = containerHandler.getSlot(slot).getItem();
                *//*?} else {*/
                ItemStack invStack = containerHandler.getSlot(slot).getStack();
                /*?}*/
                if (invStack.isEmpty()) continue;
                if (!ChestManager.isShulkerBox(invStack)) continue;
                int invSlot = playerInventorySlotFromContainerSlot(chestSlots, slot);
                if (!ownedShulkerSlots.contains(invSlot)) continue;
                // Keep shulkers that still contain items we need
                Map<String, Integer> contents = ItemIdentifier.readShulkerContents(invStack);
                boolean stillNeeded = false;
                for (String innerItem : contents.keySet()) {
                    if (isWanted(innerItem)) { stillNeeded = true; break; }
                }
                if (stillNeeded) continue;
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                /*?}*/
                        /*? if >=26.1 {*//*
                        containerHandler.containerId, slot, 0,
                        *//*?} else {*/
                        containerHandler.syncId, slot, 0,
                        /*?}*/
                        /*? if >=26.1 {*//*
                        ContainerInput.QUICK_MOVE, mc.player);
                        *//*?} else {*/
                        SlotActionType.QUICK_MOVE, mc.player);
                        /*?}*/
                ownedShulkerSlots.remove(invSlot);
                actionCooldown = CLICK_COOLDOWN_TICKS;
                syncTicks = 0;
                return;
            }
        }

        while (actionSlotIndex < chestSlots) {
            /*? if >=26.1 {*//*
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getItem();
            *//*?} else {*/
            ItemStack stack = containerHandler.getSlot(actionSlotIndex).getStack();
            /*?}*/
            if (!stack.isEmpty()) {
                String itemId = ItemIdentifier.getItemId(stack);
                boolean wantedDirectly = isWanted(itemId);
                boolean wantedForContents = false;

                // Check if a shulker contains wanted items
                if (!wantedDirectly && ChestManager.isShulkerBox(stack)) {
                    Map<String, Integer> contents = ItemIdentifier.readShulkerContents(stack);
                    for (String innerItem : contents.keySet()) {
                        if (isWanted(innerItem)) {
                            wantedForContents = true;
                            break;
                        }
                    }
                }

                if (wantedDirectly || wantedForContents) {
                    if (!hasInventoryRoom(mc.player)) {
                        ChatHelper.labelled("Stash", "§eInventory full.");
                        /*? if >=26.1 {*//*
                        mc.player.clientSideCloseContainer();
                        *//*?} else {*/
                        mc.player.closeHandledScreen();
                        /*?}*/
                        resetRunState();
                        PathWalker.setBreakingAllowed(true);
                        kitRemaining = null;
                        return;
                    }

                    // Direct item & stack exceeds need: do a partial-take split.
                    if (wantedDirectly && !wantedForContents) {
                        int need = neededOf(itemId);
                        if (need > 0 && stack.getCount() > need) {
                            beginSplitTake(actionSlotIndex, itemId, stack.getCount(), need);
                            actionSlotIndex++;
                            actionCooldown = CLICK_COOLDOWN_TICKS;
                            return;
                        }
                    }

                    if (wantedForContents) {
                        beginTrackingOwnedShulker(containerHandler, chestSlots, stack);
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

                    if (wantedForContents) {
                        // Took a shulker for its contents — close chest and
                        // enter shulker unloading to extract items.
                        /*? if >=26.1 {*//*
                        mc.player.clientSideCloseContainer();
                        *//*?} else {*/
                        mc.player.closeHandledScreen();
                        /*?}*/
                        // Re-queue this container so we revisit it afterward
                        if (walkTarget != null) {
                            ((ArrayDeque<BlockPos>) containerQueue).addFirst(walkTarget);
                        }
                        shulkerPhase = 0;
                        shulkerTicks = 0;
                        shulkerTotalTicks = 0;
                        shulkerFailures = 0;
                        state = State.UNLOADING_SHULKER;
                        return;
                    }

                    recordTaken(itemId, stack.getCount());

                    actionSlotIndex++;
                    actionCooldown = CLICK_COOLDOWN_TICKS;
                    return;
                }
            }
            actionSlotIndex++;
        }

        // Exhausted this container — close and move on
        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        advanceToNextContainer();
    }

    // Helpers

    private void advanceToNextContainer() {
        Set<BlockPos> blacklist = getBlacklistedContainerPositions();
        BlockPos next;
        while ((next = containerQueue.poll()) != null) {
            if (failedContainers.contains(next)) continue;
            if (blacklist.contains(next)) continue;
            walkTarget = next;
            state = State.WALKING;
            return;
        }
        finish();
    }

    /** Mark a container as failed for this run and bump the global failure counter. */
    private void recordContainerFailure(BlockPos pos) {
        if (pos != null) failedContainers.add(pos);
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            ChatHelper.labelled("Stash", "§cToo many failures (" + consecutiveFailures
                    + "). Aborting retrieval. Use §f/stash get §cto retry.");
            stop();
        }
    }

    private void finish() {
        resetRunState();
        PathWalker.setBreakingAllowed(true);
        if (kitRemaining != null) {
            if (kitRemaining.isEmpty()) {
                ChatHelper.labelled("Stash", "§aKit fully loaded.");
            } else {
                ChatHelper.labelled("Stash", "§eKit partially loaded. Still need:");
                for (var e : kitRemaining.entrySet()) {
                    String shortId = e.getKey().startsWith("minecraft:") ? e.getKey().substring(10) : e.getKey();
                    ChatHelper.labelled("Stash", " §c" + shortId + " §7x" + e.getValue());
                }
            }
            kitRemaining = null;
        } else {
            String shortId = targetItemId.startsWith("minecraft:") ? targetItemId.substring(10) : targetItemId;
            if (takenCount > 0) {
                ChatHelper.labelled("Stash", "§aRetrieved §f" + shortId + " §7x" + takenCount + "§a.");
            } else {
                ChatHelper.labelled("Stash", "§cCould not retrieve any §f" + shortId + "§c.");
            }
        }
    }

    /** True if itemId is still needed in current retrieval. */
    private boolean isWanted(String itemId) {
        if (kitRemaining != null) {
            return kitRemaining.containsKey(itemId) && kitRemaining.get(itemId) > 0;
        }
        return itemId.equals(targetItemId) && takenCount < targetCount;
    }

    /** Record that we took some items. */
    private void recordTaken(String itemId, int count) {
        consecutiveFailures = 0;
        if (kitRemaining != null) {
            int remaining = kitRemaining.getOrDefault(itemId, 0) - count;
            if (remaining <= 0) {
                kitRemaining.remove(itemId);
            } else {
                kitRemaining.put(itemId, remaining);
            }
        } else {
            takenCount += count;
        }
    }

    /** True when all target items have been collected. */
    private boolean isDone() {
        if (kitRemaining != null) {
            return kitRemaining.isEmpty();
        }
        return takenCount >= targetCount;
    }

    /** Placement-safe means long enough calm plus no recent correction burst. */
    private boolean isPlacementWindowSafe() {
        SetbackMonitor monitor = SetbackMonitor.get();
        if (monitor.ticksSinceSetback() < SHULKER_PLACE_CALM_TICKS) return false;
        if (monitor.recentSetbackCount(SHULKER_PLACE_RECENT_WINDOW) > 0) return false;
        return monitor.isStationaryFor(SHULKER_PLACE_STATIONARY_TICKS);
    }

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

    // Shulker unloading state machine

    /**
     * Drives one tick of an in-progress partial-take split. Returns true if a
     * click was issued (caller should set its cooldown and stop). Returns
     * false when the split is complete (state cleared, recordTaken called).
     */
    /*? if >=26.1 {*//*
    private boolean tickSplitTake(Minecraft mc, AbstractContainerMenu handler) {
    *//*?} else {*/
    private boolean tickSplitTake(MinecraftClient mc, ScreenHandler handler) {
    /*?}*/
        /*? if >=26.1 {*//*
        int syncId = handler.containerId;
        *//*?} else {*/
        int syncId = handler.syncId;
        /*?}*/

        // Step 1: pick up the source stack to cursor.
        if (!splitCursorReady) {
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, splitSrcSlot, 0,
                    ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, splitSrcSlot, 0,
                    SlotActionType.PICKUP, mc.player);
            /*?}*/
            splitCursorReady = true;
            return true;
        }

        // Step 2: right-click source to drop excess back, one item per tick.
        if (splitPutbacksLeft > 0) {
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, splitSrcSlot, 1,
                    ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, splitSrcSlot, 1,
                    SlotActionType.PICKUP, mc.player);
            /*?}*/
            splitPutbacksLeft--;
            return true;
        }

        // Step 3: drop the kept portion into an empty player slot.
        int playerStart = handler.slots.size() - 36;
        int dropSlot = -1;
        for (int s = playerStart; s < playerStart + 36; s++) {
            /*? if >=26.1 {*//*
            if (handler.getSlot(s).getItem().isEmpty()) { dropSlot = s; break; }
            *//*?} else {*/
            if (handler.getSlot(s).getStack().isEmpty()) { dropSlot = s; break; }
            /*?}*/
        }
        if (dropSlot == -1) {
            // No room. Put cursor back at source as a fallback and bail.
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(syncId, splitSrcSlot, 0,
                    ContainerInput.PICKUP, mc.player);
            *//*?} else {*/
            mc.interactionManager.clickSlot(syncId, splitSrcSlot, 0,
                    SlotActionType.PICKUP, mc.player);
            /*?}*/
            resetSplit();
            return true;
        }
        /*? if >=26.1 {*//*
        mc.gameMode.handleContainerInput(syncId, dropSlot, 0,
                ContainerInput.PICKUP, mc.player);
        *//*?} else {*/
        mc.interactionManager.clickSlot(syncId, dropSlot, 0,
                SlotActionType.PICKUP, mc.player);
        /*?}*/
        recordTaken(splitItemId, splitNeeded);
        resetSplit();
        return true;
    }

    /** Begin a partial-take split for a slot whose stack exceeds what we need. */
    private void beginSplitTake(int srcSlot, String itemId, int stackCount, int needed) {
        splitInProgress = true;
        splitSrcSlot = srcSlot;
        splitItemId = itemId;
        splitNeeded = Math.max(1, Math.min(needed, stackCount));
        splitPutbacksLeft = stackCount - splitNeeded;
        splitCursorReady = false;
    }

    private void resetSplit() {
        splitInProgress = false;
        splitSrcSlot = -1;
        splitPutbacksLeft = 0;
        splitNeeded = 0;
        splitCursorReady = false;
        splitItemId = null;
    }

    /** How many more of itemId the current run wants. */
    private int neededOf(String itemId) {
        if (kitRemaining != null) {
            Integer v = kitRemaining.get(itemId);
            return v == null ? 0 : v;
        }
        return itemId.equals(targetItemId) ? Math.max(0, targetCount - takenCount) : 0;
    }

    /*? if >=26.1 {*//*
    private void tickUnloadingShulker(Minecraft mc) {
    *//*?} else {*/
    private void tickUnloadingShulker(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        /*?}*/
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        resolvePendingOwnedShulker(player);
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        shulkerTicks++;
        shulkerTotalTicks++;

        if (shulkerTotalTicks >= SHULKER_TOTAL_TIMEOUT) {
            ChatHelper.labelled("Stash", "§cShulker unloading timed out.");
            /*? if >=26.1 {*//*
            if (mc.screen != null) player.clientSideCloseContainer();
            *//*?} else {*/
            if (mc.currentScreen != null) player.closeHandledScreen();
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            recordContainerFailure(walkTarget);
            if (state == State.IDLE) return;
            finishShulkerUnloading();
            return;
        }

        switch (shulkerPhase) {

            // Phase 0: wait for the anti-cheat to be calm, then pick a
            // shulker and a placement spot.
            case 0 -> {
                // Gate on SetbackMonitor: outlasts Grim 3's burst window.
                if (!SetbackMonitor.get().isCalm()) return;
                if (!isPlacementWindowSafe()) return;

                if (shulkerFailures >= MAX_SHULKER_FAILURES) {
                    ChatHelper.labelled("Stash", "§eShulker unloading failed too many times — skipping.");
                    recordContainerFailure(walkTarget);
                    if (state == State.IDLE) return;
                    finishShulkerUnloading();
                    return;
                }
                int slot = findShulkerWithNeededItems(player);
                if (slot < 0) {
                    finishShulkerUnloading();
                    return;
                }
                shulkerSlot = slot;
                shulkerPos = findShulkerPlaceSpot(player, world);
                if (shulkerPos == null) {
                    ChatHelper.labelled("Stash", "§eNo space to place shulker.");
                    finishShulkerUnloading();
                    return;
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
                // Start rotating to the placement target before the swap.
                // Lets Grim see a stable rotation by the time the place packet flies.
                /*? if >=26.1 {*//*
                Vec3 preTarget = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                *//*?} else {*/
                Vec3d preTarget = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                /*?}*/
                lookAt(player, preTarget);
                shulkerPhase = 1;
                shulkerTicks = 0;
                shulkerOpenRetries = 0;
            }

            // Phase 1: hold rotation, then swap shulker into hotbar.
            case 1 -> {
                // Re-send look each tick to keep rotation locked.
                /*? if >=26.1 {*//*
                Vec3 holdTarget = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                *//*?} else {*/
                Vec3d holdTarget = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                /*?}*/
                lookAt(player, holdTarget);

                if (shulkerTicks < SHULKER_LOOK_SETTLE) return;

                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                /*?}*/
                if (shulkerSlot >= 9) {
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
                            shulkerSlot,
                            /*? if >=1.21.5 {*//*
                            inv.getSelectedSlot(),
                            *//*?} else {*/
                            inv.selectedSlot,
                            /*?}*/
                            /*? if >=26.1 {*//*
                            ContainerInput.SWAP,
                            *//*?} else {*/
                            SlotActionType.SWAP,
                            /*?}*/
                            player
                    );
                    /*? if >=1.21.5 {*//*
                    swapOwnedShulkerSlots(shulkerSlot, inv.getSelectedSlot());
                    *//*?} else {*/
                    swapOwnedShulkerSlots(shulkerSlot, inv.selectedSlot);
                    /*?}*/
                } else {
                    /*? if >=1.21.5 {*//*
                    inv.setSelectedSlot(shulkerSlot);
                    *//*?} else {*/
                    inv.selectedSlot = shulkerSlot;
                    /*?}*/
                }
                shulkerPhase = 2;
                shulkerTicks = 0;
            }

            // Phase 2: wait for SWAP ACK, re-affirm look, then place.
            case 2 -> {
                if (shulkerTicks < SHULKER_SWAP_SETTLE) return;

                // Wait out ongoing correction bursts before placing.
                if (!isPlacementWindowSafe()) {
                    if (shulkerTicks >= SHULKER_PHASE_TIMEOUT) {
                        shulkerFailures++;
                        shulkerPhase = 0;
                        shulkerTicks = 0;
                    }
                    return;
                }

                // Bail if a setback landed between the swap and now.
                if (!SetbackMonitor.get().isCalm()) {
                    shulkerFailures++;
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    return;
                }

                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                /*?}*/
                /*? if >=26.1 {*//*
                ItemStack held = inv.getItem(inv.getSelectedSlot());
                *//*?} else if >=1.21.5 {*//*
                ItemStack held = inv.getStack(inv.getSelectedSlot());
                *//*?} else {*/
                ItemStack held = inv.getStack(inv.selectedSlot);
                /*?}*/
                if (!ChestManager.isShulkerBox(held)) {
                    shulkerFailures++;
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    return;
                }

                /*? if >=26.1 {*//*
                Vec3 target = Vec3.atCenterOf(shulkerPos.below()).add(0, 0.5, 0);
                *//*?} else {*/
                Vec3d target = Vec3d.ofCenter(shulkerPos.down()).add(0, 0.5, 0);
                /*?}*/
                // Setback moved us out of reach. Count as a failure and
                // restart from phase 0; otherwise we'd loop forever because
                // phase 3's timeout-bump never fires.
                /*? if >=26.1 {*//*
                if (player.getEyePosition().distanceToSqr(target) > 4.5 * 4.5) {
                *//*?} else {*/
                if (player.getEyePos().squaredDistanceTo(target) > 4.5 * 4.5) {
                /*?}*/
                    shulkerFailures++;
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                    return;
                }
                // Re-affirm rotation in the same tick as the place packet.
                lookAt(player, target);

                Runnable restoreSneak = PlacementEngine.ensureSneakForPlacement(player);
                BlockHitResult hit = new BlockHitResult(
                        target, Direction.UP,
                        /*? if >=26.1 {*//*
                        shulkerPos.below(),
                        *//*?} else {*/
                        shulkerPos.down(),
                        /*?}*/
                        false);
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/
                // Keep sneak for one extra tick to avoid same-tick anti-cheat setbacks.
                shulkerSneakRestore = restoreSneak;
                shulkerPhase = 3;
                shulkerTicks = 0;
            }

            // Phase 3: Wait for placement
            case 3 -> {
                if (shulkerSneakRestore != null && shulkerTicks >= 1) {
                    shulkerSneakRestore.run();
                    shulkerSneakRestore = null;
                }
                BlockState st = world.getBlockState(shulkerPos);
                if (st.getBlock() instanceof ShulkerBoxBlock) {
                    shulkerFailures = 0;
                    shulkerPhase = 4;
                    shulkerTicks = 0;
                    return;
                }
                if (shulkerTicks >= SHULKER_PHASE_TIMEOUT) {
                    shulkerFailures++;
                    if (shulkerSneakRestore != null) {
                        shulkerSneakRestore.run();
                        shulkerSneakRestore = null;
                    }
                    shulkerPhase = 0;
                    shulkerTicks = 0;
                }
            }

            // Phase 4: Open the placed shulker (mirror printer.tryOpenChest:
            // send look + useItemOn in the SAME tick so the server's reach/face
            // checks accept the interaction).
            case 4 -> {
                /*? if >=26.1 {*//*
                Vec3 center = Vec3.atCenterOf(shulkerPos);
                *//*?} else {*/
                Vec3d center = Vec3d.ofCenter(shulkerPos);
                /*?}*/
                lookAt(player, center);

                Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);
                /*? if >=26.1 {*//*
                Vec3 toShulker = center.subtract(player.getEyePosition());
                *//*?} else {*/
                Vec3d toShulker = center.subtract(player.getEyePos());
                /*?}*/
                /*? if >=26.1 {*//*
                Direction hitFace = Direction.getApproximateNearest(
                *//*?} else {*/
                Direction hitFace = Direction.getFacing(
                /*?}*/
                        (float) -toShulker.x, (float) -toShulker.y, (float) -toShulker.z);
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                /*?}*/
                        new BlockHitResult(center, hitFace, shulkerPos, false));
                restoreSneak.run();
                shulkerPhase = 5;
                shulkerTicks = 0;
            }

            // Phase 5: take needed items from shulker, one slot per tick.
            case 5 -> {
                /*? if >=26.1 {*//*
                AbstractContainerMenu handler = player.containerMenu;
                *//*?} else {*/
                ScreenHandler handler = player.currentScreenHandler;
                /*?}*/
                /*? if >=26.1 {*//*
                if (handler instanceof ShulkerBoxMenu shulkerHandler) {
                *//*?} else {*/
                if (handler instanceof ShulkerBoxScreenHandler shulkerHandler) {
                /*?}*/
                    if (shulkerTicks < 3) return; // sync delay

                    // Drive any in-progress split before issuing new clicks.
                    if (splitInProgress) {
                        if (actionCooldown > 0) { actionCooldown--; return; }
                        if (tickSplitTake(mc, shulkerHandler)) {
                            actionCooldown = CLICK_COOLDOWN_TICKS;
                            return;
                        }
                    }
                    if (actionCooldown > 0) { actionCooldown--; return; }

                    int wantedSlot = -1;
                    String wantedItemId = null;
                    int wantedStackCount = 0;
                    for (int slot = 0; slot < 27; slot++) {
                        /*? if >=26.1 {*//*
                        ItemStack stack = shulkerHandler.getSlot(slot).getItem();
                        *//*?} else {*/
                        ItemStack stack = shulkerHandler.getSlot(slot).getStack();
                        /*?}*/
                        if (stack.isEmpty()) continue;
                        String itemId = ItemIdentifier.getItemId(stack);
                        if (!isWanted(itemId)) continue;
                        wantedSlot = slot;
                        wantedItemId = itemId;
                        wantedStackCount = stack.getCount();
                        break;
                    }

                    boolean nothingMore = wantedSlot == -1 || !hasInventoryRoom(player) || isDone();
                    if (nothingMore) {
                        /*? if >=26.1 {*//*
                        player.clientSideCloseContainer();
                        *//*?} else {*/
                        player.closeHandledScreen();
                        /*?}*/
                        shulkerOpenRetries = 0;
                        shulkerPhase = 6;
                        shulkerTicks = 0;
                        return;
                    }

                    int need = neededOf(wantedItemId);
                    if (need > 0 && wantedStackCount > need) {
                        beginSplitTake(wantedSlot, wantedItemId, wantedStackCount, need);
                        actionCooldown = CLICK_COOLDOWN_TICKS;
                        return;
                    }

                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                    /*?}*/
                            /*? if >=26.1 {*//*
                            shulkerHandler.containerId, wantedSlot, 0,
                            *//*?} else {*/
                            shulkerHandler.syncId, wantedSlot, 0,
                            /*?}*/
                            /*? if >=26.1 {*//*
                            ContainerInput.QUICK_MOVE, player);
                            *//*?} else {*/
                            SlotActionType.QUICK_MOVE, player);
                            /*?}*/
                    recordTaken(wantedItemId, Math.min(wantedStackCount, need == 0 ? wantedStackCount : need));
                    actionCooldown = CLICK_COOLDOWN_TICKS;
                    return;
                }

                // Retry open at short intervals (printer-style) instead of
                // waiting the full phase timeout.
                if (shulkerTicks > 0 && shulkerTicks % OPEN_RETRY_INTERVAL_TICKS == 0) {
                    if (shulkerOpenRetries < MAX_SHULKER_OPEN_RETRIES) {
                        shulkerOpenRetries++;
                        shulkerPhase = 4;
                        shulkerTicks = 0;
                        return;
                    }
                }
                if (shulkerTicks >= SHULKER_PHASE_TIMEOUT) {
                    shulkerFailures++;
                    shulkerOpenRetries = 0;
                    shulkerPhase = 6;
                    shulkerTicks = 0;
                }
            }

            // Phase 6: Start breaking the shulker
            case 6 -> {
                /*? if >=26.1 {*//*
                if (mc.screen != null) { player.clientSideCloseContainer(); return; }
                *//*?} else {*/
                if (mc.currentScreen != null) { player.closeHandledScreen(); return; }
                /*?}*/

                BlockState st = world.getBlockState(shulkerPos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    shulkerPhase = 8;
                    shulkerTicks = 0;
                    return;
                }

                PlacementEngine.selectBestTool(player, mc, st);

                /*? if >=26.1 {*//*
                lookAt(player, Vec3.atCenterOf(shulkerPos));
                *//*?} else {*/
                lookAt(player, Vec3d.ofCenter(shulkerPos));
                /*?}*/
                /*? if >=26.1 {*//*
                mc.gameMode.continueDestroyBlock(shulkerPos, Direction.UP);
                *//*?} else {*/
                mc.interactionManager.updateBlockBreakingProgress(shulkerPos, Direction.UP);
                /*?}*/
                /*? if >=26.1 {*//*
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
                shulkerPhase = 7;
                shulkerTicks = 0;
            }

            // Phase 7: Continue breaking
            case 7 -> {
                BlockState st = world.getBlockState(shulkerPos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
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
                    shulkerPhase = 8;
                    shulkerTicks = 0;
                    return;
                }

                if (shulkerTicks >= SHULKER_PHASE_TIMEOUT) {
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    /*?}*/
                    shulkerFailures++;
                    finishShulkerUnloading();
                    return;
                }

                /*? if >=26.1 {*//*
                lookAt(player, Vec3.atCenterOf(shulkerPos));
                *//*?} else {*/
                lookAt(player, Vec3d.ofCenter(shulkerPos));
                /*?}*/
                /*? if >=26.1 {*//*
                mc.gameMode.continueDestroyBlock(shulkerPos, Direction.UP);
                *//*?} else {*/
                mc.interactionManager.updateBlockBreakingProgress(shulkerPos, Direction.UP);
                /*?}*/
                /*? if >=26.1 {*//*
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
            }

            // Phase 8: Wait for pickup, then resume
            case 8 -> {
                if (shulkerTicks >= SHULKER_PICKUP_DELAY) {
                    finishShulkerUnloading();
                }
            }
        }
    }

    private void finishShulkerUnloading() {
        if (shulkerSneakRestore != null) {
            shulkerSneakRestore.run();
            shulkerSneakRestore = null;
        }
        shulkerPhase = 0;
        shulkerPos = null;
        shulkerSlot = -1;
        shulkerTotalTicks = 0;
        shulkerFailures = 0;
        shulkerOpenRetries = 0;
        // If the kit is fully satisfied, skip re-visiting the source chest.
        if (isDone()) {
            finish();
        } else {
            advanceToNextContainer();
        }
    }

    /** Find an inventory slot containing a shulker with items we still need. */
    /*? if >=26.1 {*//*
    private int findShulkerWithNeededItems(LocalPlayer player) {
    *//*?} else {*/
    private int findShulkerWithNeededItems(ClientPlayerEntity player) {
    /*?}*/
        resolvePendingOwnedShulker(player);
        java.util.Iterator<Integer> it = ownedShulkerSlots.iterator();
        while (it.hasNext()) {
            int i = it.next();
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(i);
            /*?}*/
            if (!ChestManager.isShulkerBox(stack)) {
                it.remove();
                continue;
            }
            Map<String, Integer> contents = ItemIdentifier.readShulkerContents(stack);
            for (String item : contents.keySet()) {
                if (isWanted(item)) return i;
            }
        }
        return -1;
    }

    private void resetRunState() {
        PathWalker.stop();
        state = State.IDLE;
        containerQueue.clear();
        failedContainers.clear();
        consecutiveFailures = 0;
        resetTransientState();
    }

    private void resetTransientState() {
        walkTarget = null;
        openWaitTicks = 0;
        syncTicks = 0;
        actionSlotIndex = 0;
        actionCooldown = 0;
        resetSplit();
        if (shulkerSneakRestore != null) {
            shulkerSneakRestore.run();
            shulkerSneakRestore = null;
        }
        shulkerPhase = 0;
        shulkerTicks = 0;
        shulkerTotalTicks = 0;
        shulkerPos = null;
        shulkerSlot = -1;
        shulkerFailures = 0;
        shulkerOpenRetries = 0;
        ownedShulkerSlots.clear();
        pendingOwnedShulkerFingerprint = null;
        pendingOwnedShulkerCandidateSlots.clear();
    }

    /*? if >=26.1 {*//*
    private void beginTrackingOwnedShulker(AbstractContainerMenu handler, int chestSlots, ItemStack stack) {
    *//*?} else {*/
    private void beginTrackingOwnedShulker(ScreenHandler handler, int chestSlots, ItemStack stack) {
    /*?}*/
        pendingOwnedShulkerFingerprint = shulkerFingerprint(stack);
        pendingOwnedShulkerCandidateSlots.clear();
        for (int slot = chestSlots; slot < chestSlots + 36; slot++) {
            /*? if >=26.1 {*//*
            ItemStack invStack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack invStack = handler.getSlot(slot).getStack();
            /*?}*/
            if (!invStack.isEmpty()) continue;
            pendingOwnedShulkerCandidateSlots.add(playerInventorySlotFromContainerSlot(chestSlots, slot));
        }
    }

    /*? if >=26.1 {*//*
    private void resolvePendingOwnedShulker(LocalPlayer player) {
    *//*?} else {*/
    private void resolvePendingOwnedShulker(ClientPlayerEntity player) {
    /*?}*/
        if (pendingOwnedShulkerFingerprint == null || pendingOwnedShulkerCandidateSlots.isEmpty()) return;
        for (int slot : pendingOwnedShulkerCandidateSlots) {
            /*? if >=26.1 {*//*
            ItemStack stack = player.getInventory().getItem(slot);
            *//*?} else {*/
            ItemStack stack = player.getInventory().getStack(slot);
            /*?}*/
            if (!ChestManager.isShulkerBox(stack)) continue;
            if (!pendingOwnedShulkerFingerprint.equals(shulkerFingerprint(stack))) continue;
            ownedShulkerSlots.add(slot);
            pendingOwnedShulkerFingerprint = null;
            pendingOwnedShulkerCandidateSlots.clear();
            return;
        }
    }

    private static int playerInventorySlotFromContainerSlot(int chestSlots, int slot) {
        int relative = slot - chestSlots;
        if (relative < 27) return relative + 9;
        return relative - 27;
    }

    private void swapOwnedShulkerSlots(int slotA, int slotB) {
        boolean ownedA = ownedShulkerSlots.remove(slotA);
        boolean ownedB = ownedShulkerSlots.remove(slotB);
        if (ownedA) ownedShulkerSlots.add(slotB);
        if (ownedB) ownedShulkerSlots.add(slotA);
    }

    private static String shulkerFingerprint(ItemStack stack) {
        // Use item ID + sorted contents so the fingerprint is stable across
        // server round-trips regardless of stack count or HashMap iteration order.
        String id = ItemIdentifier.getItemId(stack);
        Map<String, Integer> contents = ItemIdentifier.readShulkerContents(stack);
        StringBuilder sb = new StringBuilder(id).append('|');
        contents.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append(','));
        return sb.toString();
    }

    /** Find a nearby air block with solid support and space above for shulker placement. */
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
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        boolean bestIsInteractive = true;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = playerFeet.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = playerFeet.add(dx, dy, dz);
                    /*?}*/
                    // Skip positions overlapping player AABB
                    if (px - 0.3 < pos.getX() + 1 && px + 0.3 > pos.getX()
                            && py < pos.getY() + 1 && py + 1.8 > pos.getY()
                            && pz - 0.3 < pos.getZ() + 1 && pz + 0.3 > pos.getZ()) {
                        continue;
                    }
                    BlockState blockState = world.getBlockState(pos);
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
                    if ((blockState.isAir() || blockState.canBeReplaced())
                    *//*?} else {*/
                    if ((blockState.isAir() || blockState.isReplaceable())
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
                        if (dist > 4.5 * 4.5) continue;
                        boolean interactive = PlacementEngine.isInteractive(below.getBlock());
                        if (bestIsInteractive && !interactive) {
                            best = pos;
                            bestDist = dist;
                            bestIsInteractive = false;
                        } else if (interactive == bestIsInteractive && dist < bestDist) {
                            best = pos;
                            bestDist = dist;
                            bestIsInteractive = interactive;
                        }
                    }
                }
            }
        }
        return best;
    }

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
}

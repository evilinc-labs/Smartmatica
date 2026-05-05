package dev.moar.lanes;

import dev.moar.MoarMod;
import dev.moar.util.ChatHelper;
import dev.moar.util.ItemIdentifier;
import dev.moar.util.PathWalker;
import dev.moar.util.PlacementEngine;
import dev.moar.world.SetbackMonitor;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * State machine that deposits matching player-inventory items into their
 * assigned storage lanes.
 *
 * <p>Flow: IDLE → PLANNING → WALKING → OPENING → DEPOSITING → (repeat) → DONE
 *
 * <p>The sorter only processes inventory slots 9–35 (main inventory, hotbar
 * excluded). It skips any positions registered as supply or dump chests.
 */
public final class LaneSorter {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/LaneSorter");

    // ── States ────────────────────────────────────────────────────────────────

    public enum State {
        IDLE,
        PLANNING,
        WALKING,
        OPENING,
        DEPOSITING,
        DONE
    }

    private State state = State.IDLE;

    // ── Timing constants ─────────────────────────────────────────────────────

    private static final int OPEN_TIMEOUT_TICKS = 60;
    private static final int CLICK_COOLDOWN_TICKS = 3;
    /** Number of hotbar slots excluded from inventory scanning. */
    private static final int HOTBAR_SIZE = 9;

    // ── Task model ────────────────────────────────────────────────────────────

    /** A single deposit action: move matching items from player inv to {@code chestPos}. */
    private record DepositTask(BlockPos chestPos, String itemId, int laneId, String laneName) {}

    /** Immutable plan built from the player's current inventory and accepted lanes. */
    private record SortPlan(List<StorageLane> assignedLanes, List<DepositTask> tasks) {}

    private final Deque<DepositTask> taskQueue = new ArrayDeque<>();
    private DepositTask currentTask;

    // ── Runtime state ─────────────────────────────────────────────────────────

    private BlockPos walkTarget;
    private int openWaitTicks;
    private int actionSlotIndex;
    private int actionCooldown;

    private int totalTasks;
    private int completedTasks;

    // ── Public API ────────────────────────────────────────────────────────────

    public State getState() { return state; }
    public boolean isActive() { return state != State.IDLE && state != State.DONE; }
    public int getTotalTasks() { return totalTasks; }
    public int getCompletedTasks() { return completedTasks; }

    /** Preview the current sort plan without walking or moving any items. */
    public boolean preview(List<StorageLane> lanes) {
        SortPlan plan = buildPlan(lanes);
        if (plan == null) return false;

        if (plan.tasks().isEmpty()) {
            ChatHelper.labelled("Lanes", "§aNo matching items in inventory.");
            return false;
        }

        ChatHelper.labelled("Lanes", "§lSort preview (" + plan.tasks().size() + " task"
                + (plan.tasks().size() == 1 ? "" : "s") + "):");

        int shown = 0;
        for (DepositTask task : plan.tasks()) {
            String shortItem = shortItemId(task.itemId());
            ChatHelper.labelled("Lanes", "  §e" + task.laneName() + "§7 ← §f" + shortItem
                    + " §8@ " + task.chestPos().getX() + " "
                    + task.chestPos().getY() + " " + task.chestPos().getZ());
            shown++;
            if (shown >= 12 && plan.tasks().size() > shown) {
                ChatHelper.labelled("Lanes", "  §8... +" + (plan.tasks().size() - shown)
                        + " more task(s)");
                break;
            }
        }

        ChatHelper.labelled("Lanes", "§7Run §f/stash lanes sort §7to execute this plan.");
        return true;
    }

    /** Begin sorting player inventory into the provided accepted lanes. */
    public boolean start(List<StorageLane> lanes) {
        SortPlan plan = buildPlan(lanes);
        if (plan == null) return false;
        if (plan.tasks().isEmpty()) {
            ChatHelper.labelled("Lanes", "§aNo matching items in inventory.");
            return false;
        }

        taskQueue.clear();
        taskQueue.addAll(plan.tasks());
        currentTask = null;
        totalTasks = plan.tasks().size();
        completedTasks = 0;

        ChatHelper.labelled("Lanes", "§aPlanned §f" + totalTasks + "§a sort task(s) into §f"
                + plan.assignedLanes().size() + "§a lane(s).");

        state = State.PLANNING;
        return true;
    }

    private SortPlan buildPlan(List<StorageLane> lanes) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc == null || mc.player == null
                /*? if >=26.1 {*//*|| mc.level == null*//*?} else {*/|| mc.world == null/*?}*/) {
            ChatHelper.labelled("Lanes", "§cNot in a world.");
            return null;
        }

        if (lanes.isEmpty()) {
            ChatHelper.labelled("Lanes", "§cNo accepted lanes. Run §f/stash lanes scan §cand §f/stash lanes accept §cfirst.");
            return null;
        }

        List<StorageLane> assigned = new ArrayList<>();
        for (StorageLane l : lanes) {
            if (l.isAssigned() && !l.getChestPositions().isEmpty()) {
                assigned.add(l);
            }
        }
        if (assigned.isEmpty()) {
            ChatHelper.labelled("Lanes", "§cNo lanes with item assignments found.");
            return null;
        }

        // Build item → lanes map (sorted by priority)
        Map<String, List<StorageLane>> itemToLanes = new LinkedHashMap<>();
        assigned.sort(Comparator.comparingInt(StorageLane::getPriority));
        for (StorageLane l : assigned) {
            itemToLanes.computeIfAbsent(l.getItemId(), k -> new ArrayList<>()).add(l);
        }

        // Scan player inventory and build task queue
        List<DepositTask> tasks = new ArrayList<>();
        Set<BlockPos> protectedPositions = getProtectedPositions();

        for (var entry : itemToLanes.entrySet()) {
            String itemId = entry.getKey();
            List<StorageLane> matchingLanes = entry.getValue();

            // Check if player has any of this item (slots 9–35)
            int playerCount = countPlayerInventory(mc, itemId);
            if (playerCount == 0) continue;

            // Find deposit targets in lane order, skip protected positions
            for (StorageLane lane : matchingLanes) {
                List<BlockPos> targets = getDepositTargets(lane);
                for (BlockPos target : targets) {
                    if (protectedPositions.contains(target)) continue;
                    tasks.add(new DepositTask(target, itemId, lane.getId(), lane.getName()));
                    break; // start with first non-protected chest; cascade handled during deposit
                }
            }
        }

        return new SortPlan(List.copyOf(assigned), List.copyOf(tasks));
    }

    /** Stop and reset. */
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
        currentTask = null;
        ChatHelper.labelled("Lanes", "§eSort stopped.");
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public void tick() {
        if (state == State.IDLE || state == State.DONE) return;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc == null || mc.player == null
                /*? if >=26.1 {*//*|| mc.level == null*//*?} else {*/|| mc.world == null/*?}*/) {
            return;
        }

        switch (state) {
            case PLANNING   -> tickPlanning();
            case WALKING    -> tickWalking(mc);
            case OPENING    -> tickOpening(mc);
            case DEPOSITING -> tickDepositing(mc);
            default -> {}
        }
    }

    // ── PLANNING ─────────────────────────────────────────────────────────────

    private void tickPlanning() {
        if (taskQueue.isEmpty()) {
            ChatHelper.labelled("Lanes", "§aSort complete. Nothing to do.");
            state = State.DONE;
            return;
        }
        advanceToNextTask();
    }

    // ── WALKING ──────────────────────────────────────────────────────────────

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
            ChatHelper.labelled("Lanes", "§eCan't reach "
                    + walkTarget.getX() + " " + walkTarget.getY() + " " + walkTarget.getZ()
                    + ", skipping.");
            advanceToNextTask();
            return;
        }

        PathWalker.tick();
    }

    // ── OPENING ──────────────────────────────────────────────────────────────

    /*? if >=26.1 {*//*
    private void tickOpening(Minecraft mc) {
    *//*?} else {*/
    private void tickOpening(MinecraftClient mc) {
    /*?}*/
        openWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler instanceof ChestMenu) {
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler instanceof GenericContainerScreenHandler) {
        /*?}*/
            actionSlotIndex = HOTBAR_SIZE;
            actionCooldown = 0;
            state = State.DEPOSITING;
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
            if (!SetbackMonitor.get().isCalm()) return;

            Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

            /*? if >=26.1 {*//*
            Vec3 center = Vec3.atCenterOf(walkTarget);
            Vec3 toTarget = center.subtract(player.getEyePosition());
            Direction hitFace = Direction.getApproximateNearest(
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);
            mc.gameMode.useItemOn(
                    player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(center, hitFace, walkTarget, false));
            *//*?} else {*/
            Vec3d center = Vec3d.ofCenter(walkTarget);
            Vec3d toTarget = center.subtract(player.getEyePos());
            Direction hitFace = Direction.getFacing(
                    (float) -toTarget.x, (float) -toTarget.y, (float) -toTarget.z);
            mc.interactionManager.interactBlock(
                    player, Hand.MAIN_HAND,
                    new BlockHitResult(center, hitFace, walkTarget, false));
            /*?}*/

            restoreSneak.run();
        }

        if (openWaitTicks > OPEN_TIMEOUT_TICKS) {
            ChatHelper.labelled("Lanes", "§eTimeout opening container, skipping.");
            advanceToNextTask();
        }
    }

    // ── DEPOSITING ───────────────────────────────────────────────────────────

    /*? if >=26.1 {*//*
    private void tickDepositing(Minecraft mc) {
    *//*?} else {*/
    private void tickDepositing(MinecraftClient mc) {
    /*?}*/
        if (actionCooldown > 0) { actionCooldown--; return; }

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (!(handler instanceof ChestMenu containerHandler)) {
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!(handler instanceof GenericContainerScreenHandler containerHandler)) {
        /*?}*/
            advanceToNextTask();
            return;
        }

        // Scan player main inventory (skip hotbar)
        while (actionSlotIndex < 36) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(actionSlotIndex);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(actionSlotIndex);
            /*?}*/
            if (!stack.isEmpty() && ItemIdentifier.getItemId(stack).equals(currentTask.itemId())) {
                if (!hasChestRoom(containerHandler)) {
                    // Chest is full — close and skip remaining items for this chest
                    /*? if >=26.1 {*//*
                    mc.player.clientSideCloseContainer();
                    *//*?} else {*/
                    mc.player.closeHandledScreen();
                    /*?}*/
                    ChatHelper.labelled("Lanes", "§eChest full at "
                            + currentTask.chestPos().getX() + " "
                            + currentTask.chestPos().getY() + " "
                            + currentTask.chestPos().getZ() + ".");
                    advanceToNextTask();
                    return;
                }

                // Compute the chest-side slot index for this player slot
                /*? if >=26.1 {*//*
                int chestSlots = containerHandler.getRowCount() * 9;
                *//*?} else {*/
                int chestSlots = containerHandler.getRows() * 9;
                /*?}*/
                int containerSlotIndex = (actionSlotIndex < HOTBAR_SIZE)
                        ? chestSlots + 27 + actionSlotIndex
                        : chestSlots + actionSlotIndex - HOTBAR_SIZE;

                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                        containerHandler.containerId,
                        containerSlotIndex,
                        0,
                        ContainerInput.QUICK_MOVE,
                        mc.player);
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                        containerHandler.syncId,
                        containerSlotIndex,
                        0,
                        SlotActionType.QUICK_MOVE,
                        mc.player);
                /*?}*/
                actionSlotIndex++;
                actionCooldown = CLICK_COOLDOWN_TICKS;
                return;
            }
            actionSlotIndex++;
        }

        // All matching items deposited (or player has none left)
        /*? if >=26.1 {*//*
        mc.player.clientSideCloseContainer();
        *//*?} else {*/
        mc.player.closeHandledScreen();
        /*?}*/
        completedTasks++;
        advanceToNextTask();
    }

    // ── Task advancement ─────────────────────────────────────────────────────

    private void advanceToNextTask() {
        if (taskQueue.isEmpty()) {
            if (completedTasks > 0) {
                ChatHelper.labelled("Lanes", "§aSort complete. §f" + completedTasks
                        + "§a task(s) executed.");
            }
            state = State.DONE;
            return;
        }

        currentTask = taskQueue.poll();
        walkTarget = currentTask.chestPos();
        openWaitTicks = 0;
        actionSlotIndex = HOTBAR_SIZE;
        actionCooldown = 0;
        state = State.WALKING;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** All positions that must not be touched during sorting (supply + dump chests). */
    private static Set<BlockPos> getProtectedPositions() {
        Set<BlockPos> protected_ = new HashSet<>();
        protected_.addAll(MoarMod.getChestManager().getSupplyPositions());
        protected_.addAll(MoarMod.getChestManager().getDumpPositions());
        return protected_;
    }

    /** Determine which positions to deposit into based on lane DepositMode. */
    private static List<BlockPos> getDepositTargets(StorageLane lane) {
        List<BlockPos> inputs = lane.getInputPositions();
        List<BlockPos> chests = lane.getChestPositions();
        return switch (lane.getDepositMode()) {
            case INPUT_ONLY -> inputs.isEmpty() ? chests : inputs;
            case HYBRID -> {
                List<BlockPos> combined = new ArrayList<>(inputs);
                combined.addAll(chests);
                yield combined;
            }
            default -> chests; // DIRECT_FILL
        };
    }

    /**
     * Count how many items matching {@code itemId} the player holds in
     * main inventory slots 9–35.
     */
    private static int countPlayerInventory(
            /*? if >=26.1 {*//*
            Minecraft mc,
            *//*?} else {*/
            MinecraftClient mc,
            /*?}*/
            String itemId) {
        int count = 0;
        for (int i = HOTBAR_SIZE; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = mc.player.getInventory().getItem(i);
            *//*?} else {*/
            ItemStack stack = mc.player.getInventory().getStack(i);
            /*?}*/
            if (!stack.isEmpty() && ItemIdentifier.getItemId(stack).equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** True when the open chest has at least one empty slot. */
    private static boolean hasChestRoom(
            /*? if >=26.1 {*//*
            ChestMenu handler
            *//*?} else {*/
            GenericContainerScreenHandler handler
            /*?}*/
    ) {
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

    /** Rotate the player to face a world position. */
    private static void lookAt(
            /*? if >=26.1 {*//*
            LocalPlayer player, Vec3 target
            *//*?} else {*/
            ClientPlayerEntity player, Vec3d target
            /*?}*/
    ) {
        /*? if >=26.1 {*//*
        Vec3 eye = player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = player.getEyePos();
        /*?}*/
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        /*? if >=26.1 {*//*
        player.setYRot(yaw);
        player.setXRot(pitch);
        *//*?} else {*/
        player.setYaw(yaw);
        player.setPitch(pitch);
        /*?}*/
    }

    private static String shortItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return "(unassigned)";
        return itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId;
    }
}

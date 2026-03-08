package dev.litematicaprinter.util;

import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
/*? if >=1.21.8 {*//*
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
*//*?} else {*/
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
/*?}*/
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Core automation engine for block placement tasks.
 */
public final class PlacementEngine {

    private PlacementEngine() {}

    private enum PlacePhase { IDLE, ROTATING, PLACING, FINISHING, BREAKING }

    private static PlacePhase phase = PlacePhase.IDLE;

    private static BlockPos   pendingTarget;
    private static BlockState pendingDesired;
    private static Direction  pendingFace;
    private static boolean    pendingNeedsSneak;
    private static boolean    pendingAirPlace;
    private static boolean    singleTickInProgress;
    private static Item       pendingItem;
    private static float      targetYaw;
    private static float      targetPitch;
    private static float      savedYaw;
    private static float      savedPitch;
    private static int        rotateTicks;

    // self-correction state
    /** Position of a block that was placed with wrong orientation. */
    private static BlockPos   correctionTarget;
    /** The desired state for a correction re-place. */
    private static BlockState correctionDesired;
    private static int        breakingTicks;
    private static final int  MAX_BREAKING_TICKS = 200;
    private static int        postBreakWait;
    private static final int MAX_CORRECTION_ENTRIES = 128;
    private static final Map<BlockPos, Integer> correctionAttempts = new LinkedHashMap<>(32, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Integer> eldest) {
            return size() > MAX_CORRECTION_ENTRIES;
        }
    };
    private static final int MAX_CORRECTION_ATTEMPTS = 2;
    private static final int  MAX_ROTATE_TICKS = 4;
    private static final float CONVERGE_THRESHOLD = 1.0f;
    private static final float MAX_TURN_SPEED = 30.0f;

    private static boolean silentRotation = false;

    public static void setSilentRotation(boolean value) { silentRotation = value; }
    public static boolean isSilentRotation() { return silentRotation; }

    private static final Random JITTER_RNG = new Random();
    private static final int   WINDOW_SIZE = 8;
    private static final long  WINDOW_MS   = 425;

    private static final int  BATCH_MAX         = 9;
    private static final long BATCH_COOLDOWN_MS = 310L;
    private static long       lastBatchMs       = 0L;

    private static final long[] placeHistory = new long[WINDOW_SIZE];
    private static int          historyIdx   = 0;

    /** Per-block cadence floor — the minimum gap (in ns) between any two
     *  consecutive placements.  Derived from the user-facing BPS setting. */
    private static int    bps               = 4;
    private static long   lastPlacementNano = 0;

    public static void setBps(int value) {
        bps = Math.max(1, Math.min(9, value));
    }

    public static int getBps() { return bps; }

    public static boolean canPlace() {
        if (phase != PlacePhase.IDLE) return false;
        long nowNano = System.nanoTime();

        long intervalNano = 1_000_000_000L / bps;
        long jitter = (long) (intervalNano * (JITTER_RNG.nextDouble() * 0.5 - 0.25));
        if ((nowNano - lastPlacementNano) < (intervalNano + jitter)) return false;

        long oldest = placeHistory[historyIdx];
        if (oldest != 0) {
            long windowNano = WINDOW_MS * 1_000_000L;
            if ((nowNano - oldest) < windowNano) return false;
        }

        return true;
    }

    public static boolean isBusy() {
        return phase != PlacePhase.IDLE;
    }

    public static String getPhase() {
        return phase.name();
    }

    public static boolean isCorrecting() {
        return phase == PlacePhase.BREAKING;
    }

    public static void recordPlacement() {
        long now = System.nanoTime();
        lastPlacementNano = now;
        placeHistory[historyIdx] = now;
        historyIdx = (historyIdx + 1) % WINDOW_SIZE;
    }

    public static boolean canBatchPlace() {
        if (phase != PlacePhase.IDLE) return false;
        return System.currentTimeMillis() - lastBatchMs >= BATCH_COOLDOWN_MS;
    }

    public static void reset() {
        if (pendingNeedsSneak && phase == PlacePhase.FINISHING) {
            if (SneakOverride.isForceAbsoluteSneak()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) pressSneakPacket(mc.player);
            } else {
                releaseSneakPacket();
            }
        }
        if (phase == PlacePhase.BREAKING) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }
        }
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingDesired = null;
        pendingFace = null;
        pendingNeedsSneak = false;
        pendingAirPlace = false;
        singleTickInProgress = false;
        pendingItem = null;
        correctionTarget = null;
        correctionDesired = null;
        breakingTicks = 0;
        postBreakWait = 0;
        java.util.Arrays.fill(placeHistory, 0L);
        historyIdx = 0;
        lastBatchMs = 0L;
    }

    public static void clearCorrectionHistory() {
        correctionAttempts.clear();
    }

    public static void pruneCompletedCorrections() {
        correctionAttempts.values().removeIf(v -> v < MAX_CORRECTION_ATTEMPTS);
    }

    private static Map<Item, Integer> cachedInventory = Map.of();
    private static long cachedInventoryTick = -1;

    /**
     * Returns a cached snapshot of the player's inventory contents.
     * The cache is invalidated once per game tick, so multiple callers
     * within the same tick share the same map without re-scanning.
     */
    public static Map<Item, Integer> getInventoryContentsCached() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return Map.of();
        long tick = mc.world.getTime();
        if (tick != cachedInventoryTick) {
            cachedInventory = getInventoryContents();
            cachedInventoryTick = tick;
        }
        return cachedInventory;
    }

    /** @return true when a block was placed */
    public static boolean tick() {
        return switch (phase) {
            case IDLE     -> false;
            case ROTATING -> {

                boolean isLiquid = pendingDesired != null
                        && pendingDesired.getBlock() instanceof FluidBlock;
                yield isLiquid ? tickRotate() : tickPlaceSingleTick();
            }
            case PLACING  -> tickPlace();
            case FINISHING -> tickFinish();
            case BREAKING -> tickBreaking();
        };
    }

    private static boolean tickPlaceSingleTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { reset(); return false; }

        boolean wasSilent = silentRotation;
        silentRotation = true;
        singleTickInProgress = true;

        sendSilentLookPacket(mc.player, targetYaw, targetPitch);
        if (pendingNeedsSneak) {
            pressSneakPacket(mc.player);
        }
        phase = PlacePhase.PLACING;

        boolean placed = tickPlace();
        if (phase == PlacePhase.FINISHING) {
            tickFinish();
        }

        singleTickInProgress = false;
        silentRotation = wasSilent;
        return placed;
    }

    private static boolean tickRotate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { reset(); return false; }

        rotateTicks++;

        if (silentRotation) {
            sendSilentLookPacket(mc.player, targetYaw, targetPitch);

            if (pendingNeedsSneak) {
                pressSneakPacket(mc.player);
            }

            phase = PlacePhase.PLACING;
            return false;
        }

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        float newPitch = currentPitch + MathHelper.clamp(pitchDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        newPitch = MathHelper.clamp(newPitch, -90.0f, 90.0f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        boolean converged = Math.abs(MathHelper.wrapDegrees(targetYaw - newYaw)) < CONVERGE_THRESHOLD
                         && Math.abs(targetPitch - newPitch) < CONVERGE_THRESHOLD;

        if (converged || rotateTicks >= MAX_ROTATE_TICKS) {
            if (converged) {
                mc.player.setYaw(targetYaw);
                mc.player.setPitch(targetPitch);
            }

            sendLookPacket(mc.player, mc.player.getYaw(), mc.player.getPitch());
            if (pendingNeedsSneak) {
                pressSneakPacket(mc.player);
            }

            phase = PlacePhase.PLACING;
        }

        return false;
    }

    private static boolean tickPlace() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            reset();
            return false;
        }

        ClientPlayerEntity player = mc.player;

        if (pendingItem != null) {
            PlayerInventory inv = player.getInventory();
            /*? if >=1.21.5 {*//*
            Item held = inv.getStack(inv.getSelectedSlot()).getItem();
            *//*?} else {*/
            Item held = inv.getStack(inv.selectedSlot).getItem();
            /*?}*/
            if (held != pendingItem) {
                if (!selectItem(player, mc, pendingItem, true)) {
                    if (pendingNeedsSneak) {
                        if (SneakOverride.isForceAbsoluteSneak()) {
                            pressSneakPacket(player);
                        } else {
                            releaseSneakPacket();
                        }
                    }
                    if (!silentRotation) {
                        restoreLook(player);
                    } else {
                        sendSilentLookPacket(player, player.getYaw(), player.getPitch());
                    }
                    phase = PlacePhase.IDLE;
                    pendingTarget = null;
                    pendingDesired = null;
                    return false;
                }
            }
        }

        Vec3d eyePos = player.getEyePos();

        float placeYaw   = silentRotation ? targetYaw   : player.getYaw();
        float placePitch  = silentRotation ? targetPitch : player.getPitch();

        BlockHitResult hitResult;
        if (pendingAirPlace) {
            Direction airFace = Direction.UP;
            Vec3d hitPos = Vec3d.ofCenter(pendingTarget).add(
                    airFace.getOffsetX() * 0.5,
                    airFace.getOffsetY() * 0.5,
                    airFace.getOffsetZ() * 0.5);
            hitPos = adjustHitForAirPlace(hitPos, pendingTarget, pendingDesired);
            hitResult = new BlockHitResult(hitPos, airFace, pendingTarget, false);
        } else {
            BlockPos neighbor = pendingTarget.offset(pendingFace);
            Direction clickSide = pendingFace.getOpposite();
            Vec3d hitPos = computeRayFaceHit(eyePos, placeYaw, placePitch,
                                              neighbor, clickSide, mc.world);
            hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, pendingDesired);
            hitPos = adjustHitForDoorHinge(hitPos, neighbor, pendingDesired);

            hitResult = new BlockHitResult(hitPos, clickSide, neighbor, false);
        }

        {
            double reachSq = 4.5 * 4.5;
            if (eyePos.squaredDistanceTo(hitResult.getPos()) > reachSq) {
                phase = PlacePhase.IDLE;
                pendingTarget = null;
                pendingDesired = null;
                return false;
            }
        }


        if (silentRotation && !singleTickInProgress) {
            sendSilentLookPacket(player, placeYaw, placePitch);
        }

        boolean isLiquidPlacement = pendingDesired.getBlock() instanceof FluidBlock;
        boolean placed;
        if (isLiquidPlacement) {
            ActionResult result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            if (result.isAccepted()) {
                player.swingHand(Hand.MAIN_HAND);
            }
            if (result.isAccepted()) {
                BlockState afterState = mc.world.getBlockState(pendingTarget);
                placed = afterState.getBlock() instanceof FluidBlock
                      && afterState.getFluidState().isStill();
            } else {
                placed = false;
            }
        } else {
            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN, Direction.DOWN));
            player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.OFF_HAND, hitResult,
                    player.currentScreenHandler.getRevision() + 2));
            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN, Direction.DOWN));
            player.swingHand(Hand.MAIN_HAND);
            mc.world.setBlockState(pendingTarget, pendingDesired);
            placed = true;
        }

        recordPlacement();

        if (pendingNeedsSneak) {
            phase = PlacePhase.FINISHING;
        } else {
            if (!singleTickInProgress) {
                if (!silentRotation) {
                    restoreLook(player);
                } else {
                    sendSilentLookPacket(player, player.getYaw(), player.getPitch());
                }
            }
            phase = PlacePhase.IDLE;
            pendingTarget = null;
            pendingItem = null;
        }

        return placed;
    }

    private static boolean tickFinish() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            if (SneakOverride.isForceAbsoluteSneak()) {
                pressSneakPacket(mc.player);
            } else {
                releaseSneakPacket();
            }
            if (!singleTickInProgress) {
                if (!silentRotation) {
                    restoreLook(mc.player);
                } else {
                    sendSilentLookPacket(mc.player, mc.player.getYaw(), mc.player.getPitch());
                }
            }
        }
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingDesired = null;
        pendingNeedsSneak = false;
        pendingItem = null;
        return false;
    }

    private static boolean tickBreaking() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            reset();
            return false;
        }

        if (postBreakWait > 0) {
            postBreakWait--;
            if (postBreakWait <= 0) {
                restoreLook(mc.player);
                correctionTarget = null;
                correctionDesired = null;
                phase = PlacePhase.IDLE;
            }
            return false;
        }

        breakingTicks++;
        if (breakingTicks > MAX_BREAKING_TICKS) {
            mc.interactionManager.cancelBlockBreaking();
            restoreLook(mc.player);
            correctionTarget = null;
            correctionDesired = null;
            phase = PlacePhase.IDLE;
            return false;
        }

        BlockState current = mc.world.getBlockState(correctionTarget);
        if (current.isAir() || current.isReplaceable()) {
            mc.interactionManager.cancelBlockBreaking();
            postBreakWait = 5;
            return false;
        }

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(correctionTarget);
        Vec3d toBlock = blockCenter.subtract(eyePos);
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));

        sendLookPacket(mc.player, breakYaw, breakPitch);

        Direction breakFace = Direction.UP;
        mc.interactionManager.updateBlockBreakingProgress(
                correctionTarget, breakFace);
        mc.player.swingHand(Hand.MAIN_HAND);

        return false;
    }

    public static boolean placeBlock(BlockPos target, BlockState desired, boolean allowSwap) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        if (phase != PlacePhase.IDLE) return false;

        ClientPlayerEntity player = mc.player;
        World world = mc.world;

        // Skip auto-created parts (door upper, bed head, tall plant upper)
        Block desiredBlock = desired.getBlock();
        if (desiredBlock instanceof DoorBlock
                && desired.contains(Properties.DOUBLE_BLOCK_HALF)
                && desired.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return false;
        }
        if (desiredBlock instanceof BedBlock
                && desired.contains(Properties.BED_PART)
                && desired.get(Properties.BED_PART) == BedPart.HEAD) {
            return false;
        }
        if (desiredBlock instanceof TallPlantBlock
                && desired.contains(Properties.DOUBLE_BLOCK_HALF)
                && desired.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return false;
        }

        // Wrong orientation → break and re-place
        BlockState existing = world.getBlockState(target);
        if (!existing.isAir() && !existing.isReplaceable()
                && existing.getBlock() == desired.getBlock()
                && !existing.equals(desired)
                && isOrientationMismatch(existing, desired)) {
            BlockPos immutable = target.toImmutable();
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            return startCorrection(target, desired, player, mc);
        }

        // Scaffold removal
        if (!existing.isAir() && !existing.isReplaceable()
                && existing.getBlock() != desired.getBlock()
                && PrinterDatabase.isScaffold(target)) {
            BlockPos immutable = target.toImmutable();
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            return startCorrection(target, desired, player, mc);
        }

        // Foreign block replacement
        if (!existing.isAir() && !existing.isReplaceable()
                && existing.getBlock() != desired.getBlock()) {
            BlockPos immutable = target.toImmutable();
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            return startCorrection(target, desired, player, mc);
        }

        // 1. find the required item
        Item requiredItem = desired.getBlock().asItem();
        if (requiredItem == Items.AIR) return false;

        if (!selectItem(player, mc, requiredItem, allowSwap)) return false;

        if (!world.canPlace(desired, target,
                net.minecraft.block.ShapeContext.absent())) {
            return false;
        }
        {
            net.minecraft.util.math.Box placeBox =
                    net.minecraft.util.math.Box.from(Vec3d.ofCenter(target));
            List<net.minecraft.entity.Entity> entities =
                    world.getOtherEntities(null, placeBox);
            for (net.minecraft.entity.Entity entity : entities) {
                if (!entity.isSpectator() && entity.isAlive()) {
                    return false;
                }
            }
        }

        Direction face = findOrientedPlacementFace(world, target, desired);

        if (face == null) return false;

        Vec3d eyePos = player.getEyePos();
        float desiredYaw;
        float desiredPitch;
        boolean needsSneak = false;

        {
            BlockPos neighbor = target.offset(face);
            Block neighborBlock = world.getBlockState(neighbor).getBlock();
            needsSneak = isInteractive(neighborBlock);

            Direction clickSide = face.getOpposite();
            Vec3d hitPos = computeRayFaceHit(eyePos, player.getYaw(), player.getPitch(),
                                              neighbor, clickSide, world);
            hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, desired);
            hitPos = adjustHitForDoorHinge(hitPos, neighbor, desired);

            Vec3d toHit = hitPos.subtract(eyePos);
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));

            Float facingYaw = getRequiredYaw(desired);
            if (facingYaw != null) {
                desiredYaw = facingYaw;
                desiredPitch = computePitchToward(eyePos, hitPos);
            }
            Float facingPitch = getRequiredPitch(desired);
            if (facingPitch != null) {
                desiredPitch = facingPitch;
            }
        }

        pendingTarget = target.toImmutable();
        pendingDesired = desired;
        pendingFace = face;
        pendingAirPlace = false;
        pendingNeedsSneak = needsSneak;
        pendingItem = requiredItem;
        targetYaw = snapToMouseGCD(desiredYaw, player.getYaw());
        targetPitch = MathHelper.clamp(
                snapToMouseGCD(desiredPitch, player.getPitch()),
                -90.0f, 90.0f);
        savedYaw = player.getYaw();
        savedPitch = player.getPitch();
        rotateTicks = 0;
        phase = PlacePhase.ROTATING;

        return true;
    }

    public static boolean placeLiquid(BlockPos target, BlockState desired, boolean allowSwap) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        if (phase != PlacePhase.IDLE) return false;

        ClientPlayerEntity player = mc.player;
        World world = mc.world;

        Item bucketItem;
        Block block = desired.getBlock();
        if (block == Blocks.WATER) bucketItem = Items.WATER_BUCKET;
        else if (block == Blocks.LAVA) bucketItem = Items.LAVA_BUCKET;
        else return false;

        BlockState currentState = world.getBlockState(target);
        if (!currentState.isAir() && !currentState.isReplaceable()) {
            if (currentState.getBlock() instanceof FluidBlock) {
                if (currentState.getFluidState().isStill()) {
                    return false;
                }
            } else {
                return false;
            }
        }

        // Eye must be at or above target for correct ray cast
        if (player.getEyePos().y < target.getY()) {
            return false;
        }

        if (!selectItem(player, mc, bucketItem, allowSwap)) return false;

        Direction face = findPlacementFace(world, target);
        if (face == null) return false;

        Vec3d eyePos = player.getEyePos();
        float desiredYaw;
        float desiredPitch;
        boolean needsSneak = false;

        {
            BlockPos neighbor = target.offset(face);
            Block neighborBlock = world.getBlockState(neighbor).getBlock();
            needsSneak = isInteractive(neighborBlock);

            Direction clickSide = face.getOpposite();
            Vec3d faceCenter = Vec3d.ofCenter(neighbor)
                    .add(Vec3d.of(clickSide.getVector()).multiply(0.5));
            Vec3d toHit = faceCenter.subtract(eyePos);
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
        }

        pendingTarget = target.toImmutable();
        pendingDesired = desired;
        pendingFace = face;
        pendingAirPlace = false;  // liquids never air-place
        pendingNeedsSneak = needsSneak;
        pendingItem = bucketItem;
        targetYaw = snapToMouseGCD(desiredYaw, player.getYaw());
        targetPitch = MathHelper.clamp(
                snapToMouseGCD(desiredPitch, player.getPitch()),
                -90.0f, 90.0f);
        savedYaw = player.getYaw();
        savedPitch = player.getPitch();
        rotateTicks = 0;
        phase = PlacePhase.ROTATING;

        return true;
    }


    private record BatchEntry(BlockPos target, BlockState desired,
                              BlockHitResult hitResult) {}


    public static int placeBatch(List<BlockPos> targets, List<BlockState> states,
                                 boolean allowSwap) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null)
            return 0;
        if (phase != PlacePhase.IDLE) return 0;
        if (targets.isEmpty()) return 0;

            long now = System.currentTimeMillis();
        if (now - lastBatchMs < BATCH_COOLDOWN_MS) return 0;

        ClientPlayerEntity player = mc.player;
        World world = mc.world;
        Vec3d eyePos = player.getEyePos();
        double reachSq = 4.5 * 4.5;

        Item requiredItem = states.get(0).getBlock().asItem();
        if (requiredItem == Items.AIR) return 0;
        if (!selectItem(player, mc, requiredItem, allowSwap)) return 0;

        List<BatchEntry> entries = new ArrayList<>(BATCH_MAX);
        boolean needsSneak = false;
        float batchYaw = player.getYaw();
        float batchPitch = player.getPitch();

        for (int i = 0; i < targets.size() && entries.size() < BATCH_MAX; i++) {
            BlockPos target = targets.get(i);
            BlockState desired = states.get(i);

            if (desired.getBlock().asItem() != requiredItem) continue;
            BlockState currentState = world.getBlockState(target);
            if (!currentState.isAir() && !currentState.isReplaceable()) continue;

            if (!world.canPlace(desired, target,
                    net.minecraft.block.ShapeContext.absent())) continue;
            {
                net.minecraft.util.math.Box placeBox =
                        net.minecraft.util.math.Box.from(Vec3d.ofCenter(target));
                boolean blocked = false;
                for (net.minecraft.entity.Entity e : world.getOtherEntities(null, placeBox)) {
                    if (!e.isSpectator() && e.isAlive()) { blocked = true; break; }
                }
                if (blocked) continue;
            }

            Direction face = findPlacementFace(world, target);
            BlockHitResult hitResult;

            if (face != null) {
                BlockPos neighbor = target.offset(face);
                Direction clickSide = face.getOpposite();
                Vec3d hitPos = Vec3d.ofCenter(neighbor)
                        .add(clickSide.getOffsetX() * 0.5,
                             clickSide.getOffsetY() * 0.5,
                             clickSide.getOffsetZ() * 0.5);
                hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, desired);
                hitPos = adjustHitForDoorHinge(hitPos, neighbor, desired);
                hitResult = new BlockHitResult(hitPos, clickSide, neighbor, false);

                if (isInteractive(world.getBlockState(neighbor).getBlock())) {
                    needsSneak = true;
                }
            } else {
                Direction airFace = getFaceTowardPlayer(player, target);
                Vec3d hitPos = Vec3d.ofCenter(target);
                hitPos = adjustHitForAirPlace(hitPos, target, desired);
                hitResult = new BlockHitResult(hitPos, airFace, target, false);
            }

            if (eyePos.squaredDistanceTo(hitResult.getPos()) > reachSq) continue;

            if (entries.isEmpty()) {
                Vec3d toHit = hitResult.getPos().subtract(eyePos);
                double hDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
                batchYaw = (float) (MathHelper.atan2(toHit.z, toHit.x)
                        * (180.0 / Math.PI)) - 90.0f;
                batchPitch = (float) -(MathHelper.atan2(toHit.y, hDist)
                        * (180.0 / Math.PI));
                Float facingYaw = getRequiredYaw(desired);
                if (facingYaw != null) batchYaw = facingYaw;
                Float facingPitch = getRequiredPitch(desired);
                if (facingPitch != null) batchPitch = facingPitch;
                batchYaw = snapToMouseGCD(batchYaw, player.getYaw());
                batchPitch = MathHelper.clamp(
                        snapToMouseGCD(batchPitch, player.getPitch()),
                        -90.0f, 90.0f);
            }

            entries.add(new BatchEntry(target.toImmutable(), desired, hitResult));
            world.setBlockState(target, desired);
        }

        if (entries.isEmpty()) return 0;

        sendSilentLookPacket(player, batchYaw, batchPitch);
        if (needsSneak) pressSneakPacket(player);

        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN, Direction.DOWN));

        int seq = player.currentScreenHandler.getRevision();
        for (BatchEntry entry : entries) {
            player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.OFF_HAND, entry.hitResult, ++seq));
        }

        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN, Direction.DOWN));

        player.swingHand(Hand.MAIN_HAND);

        if (needsSneak) {
            if (SneakOverride.isForceAbsoluteSneak()) {
                pressSneakPacket(player);
            } else {
                releaseSneakPacket();
            }
        }

        lastBatchMs = now;
        for (int i = 0; i < entries.size(); i++) {
            recordPlacement();
        }

        return entries.size();
    }

    private static Direction getFaceTowardPlayer(ClientPlayerEntity player,
                                                 BlockPos pos) {
        Vec3d eye = player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d delta = eye.subtract(center);

        double ax = Math.abs(delta.x);
        double ay = Math.abs(delta.y);
        double az = Math.abs(delta.z);

        if (ay >= ax && ay >= az) return delta.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= ay && ax >= az) return delta.x > 0 ? Direction.EAST : Direction.WEST;
        return delta.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Break the misplaced block for re-placement.
     */
    private static boolean startCorrection(BlockPos target, BlockState desired,
                                           ClientPlayerEntity player,
                                           MinecraftClient mc) {
        correctionTarget = target.toImmutable();
        correctionDesired = desired;
        breakingTicks = 0;
        savedYaw = player.getYaw();
        savedPitch = player.getPitch();

        BlockState existing = mc.world.getBlockState(target);
        selectBestTool(player, mc, existing);

        Vec3d eyePos = player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(target);
        Vec3d toBlock = blockCenter.subtract(eyePos);
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));

        sendLookPacket(player, breakYaw, breakPitch);
        mc.interactionManager.attackBlock(target, Direction.UP);
        phase = PlacePhase.BREAKING;
        return true;
    }

    /** Computes a hit position on the given face via ray cast, falling back to face center. */
    private static Vec3d computeRayFaceHit(Vec3d eyePos, float yaw, float pitch,
                                            BlockPos neighbor, Direction face, World world) {
        // Ray direction from yaw/pitch
        float yawRad  = (float) Math.toRadians(-yaw - 180.0f);
        float pitchRad = (float) Math.toRadians(-pitch);
        float cosP = MathHelper.cos(pitchRad);
        Vec3d lookDir = new Vec3d(
                MathHelper.sin(yawRad) * cosP,
                MathHelper.sin(pitchRad),
                MathHelper.cos(yawRad) * cosP
        );

        // Face plane: the face of the neighbor block
        // The face is on the surface of the neighbor block at `face` direction
        Vec3d faceCenter = Vec3d.ofCenter(neighbor)
                .add(Vec3d.of(face.getVector()).multiply(0.5));

        // Normal of the face
        Vec3d faceNormal = Vec3d.of(face.getVector());

        // Ray-plane intersection: t = dot(faceCenter - eyePos, normal) / dot(lookDir, normal)
        double denom = lookDir.dotProduct(faceNormal);
        if (Math.abs(denom) < 1e-6) {
            // Ray is nearly parallel to face — use face center
            return faceCenter;
        }

        double t = faceCenter.subtract(eyePos).dotProduct(faceNormal) / denom;
        if (t < 0) {
            // Intersection is behind the player — use face center
            return faceCenter;
        }

        Vec3d intersection = eyePos.add(lookDir.multiply(t));

        // Clamp to the face bounds (block goes from neighbor to neighbor+1)
        double hx = clampToFace(intersection.x, neighbor.getX(), face, Direction.Axis.X);
        double hy = clampToFace(intersection.y, neighbor.getY(), face, Direction.Axis.Y);
        double hz = clampToFace(intersection.z, neighbor.getZ(), face, Direction.Axis.Z);

        return new Vec3d(hx, hy, hz);
    }

    /**
     * Clamps a coordinate to the 0–1 range of the block, but fixes it to
     * the face boundary for the axis matching the face direction.
     */
    private static double clampToFace(double value, int blockOrigin, Direction face, Direction.Axis axis) {
        double min = blockOrigin;
        double max = blockOrigin + 1.0;

        if (face.getAxis() == axis) {
            // This axis is fixed to the face surface
            return face.getDirection() == Direction.AxisDirection.POSITIVE ? max : min;
        }

        // Clamp to block bounds with small inset to avoid exact edges
        return MathHelper.clamp(value, min + 0.01, max - 0.01);
    }

    /** Sends a look packet without modifying client-side rotation. */
    private static void sendSilentLookPacket(ClientPlayerEntity player, float yaw, float pitch) {
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        /*? if >=1.21.4 {*//*
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                        player.isOnGround(), player.horizontalCollision));
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                        player.isOnGround()));
        /*?}*/
    }

    /** Sets entity yaw/pitch and sends a matching look packet. */
    public static void sendLookPacket(ClientPlayerEntity player, float yaw, float pitch) {
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        player.setYaw(yaw);
        player.setPitch(pitch);
        /*? if >=1.21.4 {*//*
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                        player.isOnGround(), player.horizontalCollision));
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                        player.isOnGround()));
        /*?}*/
    }

    /** Releases sneak for interaction; returns a Runnable that restores it. */
    public static Runnable releaseForInteraction(ClientPlayerEntity player) {
        boolean wasAbsoluteSneak = SneakOverride.isForceAbsoluteSneak();
        boolean wasForceSneak = SneakOverride.isForceSneak();
        SneakOverride.setForceAbsoluteSneak(false);
        SneakOverride.setForceSneak(false);
        player.setSneaking(false);
        if (wasAbsoluteSneak || wasForceSneak) {
            releaseSneakPacket();
        }
        return () -> {
            if (wasAbsoluteSneak) SneakOverride.setForceAbsoluteSneak(true);
            if (wasForceSneak) SneakOverride.setForceSneak(true);
            if (wasAbsoluteSneak || wasForceSneak) {
                pressSneakPacket(player);
            }
        };
    }

    private static void pressSneakPacket(ClientPlayerEntity player) {
        /*? if >=1.21.8 {*//*
        player.networkHandler.sendPacket(
                new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, true, false)));
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        /*?}*/
    }

    private static void releaseSneakPacket() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        /*? if >=1.21.8 {*//*
        mc.player.networkHandler.sendPacket(
                new PlayerInputC2SPacket(PlayerInput.DEFAULT));
        *//*?} else {*/
        mc.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        /*?}*/
    }

    /** Smoothly restores look direction after placement. */
    private static void restoreLook(ClientPlayerEntity player) {
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(savedYaw - currentYaw);
        float pitchDiff = savedPitch - currentPitch;

        // Lerp 65% back toward saved look
        float newYaw = currentYaw + yawDiff * 0.65f;
        float newPitch = currentPitch + pitchDiff * 0.65f;

        // Snap if close
        if (Math.abs(yawDiff) < 1.5f && Math.abs(pitchDiff) < 1.5f) {
            newYaw = savedYaw;
            newPitch = savedPitch;
        }

        sendLookPacket(player, newYaw, newPitch);
    }

    // inventory helpers

    /** Selects the required item in the hotbar (or swaps from inventory). */
    public static boolean selectItem(ClientPlayerEntity player, MinecraftClient mc,
                                     Item item, boolean allowSwap) {
        PlayerInventory inv = player.getInventory();

        // check current slot first
        /*? if >=1.21.5 {*//*
        if (inv.getStack(inv.getSelectedSlot()).getItem() == item) return true;
        *//*?} else {*/
        if (inv.getStack(inv.selectedSlot).getItem() == item) return true;
        /*?}*/

        // check rest of hotbar
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == item) {
                /*? if >=1.21.5 {*//*
                inv.setSelectedSlot(i);
                *//*?} else {*/
                inv.selectedSlot = i;
                /*?}*/
                return true;
            }
        }

        // check main inventory and swap if allowed
        if (allowSwap) {
            for (int i = 9; i < 36; i++) {
                if (inv.getStack(i).getItem() == item) {
                    mc.interactionManager.clickSlot(
                            player.currentScreenHandler.syncId,
                            i,
                            /*? if >=1.21.5 {*//*
                            inv.getSelectedSlot(),
                            *//*?} else {*/
                            inv.selectedSlot,
                            /*?}*/
                            SlotActionType.SWAP,
                            player
                    );
                    return true;
                }
            }
        }
        return false;
    }

    /** Selects the best tool for breaking the given block. */
    public static void selectBestTool(ClientPlayerEntity player, MinecraftClient mc,
                                       BlockState state) {
        PlayerInventory inv = player.getInventory();
        float bestSpeed = 1.0f; // bare-hand baseline
        int   bestSlot  = -1;

        // Scan entire inventory (0-8 hotbar, 9-35 main)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        if (bestSlot < 0) return; // no tool improves the speed

        if (bestSlot < 9) {
            // Tool is in the hotbar — just switch to it
            /*? if >=1.21.5 {*//*
            inv.setSelectedSlot(bestSlot);
            *//*?} else {*/
            inv.selectedSlot = bestSlot;
            /*?}*/
        } else {
            // Tool is in main inventory — swap into the current hotbar slot
            mc.interactionManager.clickSlot(
                    player.currentScreenHandler.syncId,
                    bestSlot,
                    /*? if >=1.21.5 {*//*
                    inv.getSelectedSlot(),
                    *//*?} else {*/
                    inv.selectedSlot,
                    /*?}*/
                    SlotActionType.SWAP,
                    player
            );
        }
    }

    /**
     * Build a snapshot of all items in the player's inventory.
     *
     * @return map of Item → total count (across all slots)
     */
    public static Map<Item, Integer> getInventoryContents() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return Map.of();
        PlayerInventory inv = mc.player.getInventory();
        Map<Item, Integer> contents = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return contents;
    }

    /** Returns the yaw needed to produce the desired FACING, or null. */
    private static Float getRequiredYaw(BlockState desired) {
        Block block = desired.getBlock();

        // Stairs — FACING is set to the player's look direction (NOT opposite)
        if (block instanceof StairsBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing);
            }
        }

        // Horizontal facing blocks (placed opposite player)
        if (block instanceof GlazedTerracottaBlock
                || block instanceof CarvedPumpkinBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof LoomBlock
                || block instanceof StonecutterBlock
                || block instanceof CraftingTableBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing.getOpposite());
            }
        }

        // Blocks that use FACING (all 6 directions) — dispenser, observer, piston
        if (block instanceof DispenserBlock
                || block instanceof ObserverBlock
                || block instanceof PistonBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
                // UP/DOWN handled by pitch override
            }
        }

        // Anvil — uses HORIZONTAL_FACING
        if (block instanceof AnvilBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing.rotateYClockwise()); // perpendicular
            }
        }

        // Chests, trapped chests, ender chests — HORIZONTAL_FACING
        if (block instanceof AbstractChestBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing.getOpposite());
            }
        }

        // Barrels — FACING (all 6 directions)
        if (block instanceof BarrelBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Shulker boxes — FACING (all 6 directions)
        if (block instanceof ShulkerBoxBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Hoppers — yaw not used (facing from clicked face)

        // End rods, lightning rods
        if (block instanceof EndRodBlock || block instanceof LightningRodBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Rotation-based blocks (banners, signs)
        // yaw = rotation * 22.5 - 180
        if ((block instanceof AbstractBannerBlock && !(block instanceof WallBannerBlock))
                || (block instanceof SignBlock && !(block instanceof WallSignBlock))
                || (block instanceof HangingSignBlock && !(block instanceof WallHangingSignBlock))) {
            if (desired.contains(Properties.ROTATION)) {
                int rotation = desired.get(Properties.ROTATION);
                return rotation * 22.5f - 180.0f;
            }
        }

        // Trapdoors, fence gates — face opposite to the player
        if (block instanceof TrapdoorBlock
                || block instanceof FenceGateBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing.getOpposite());
            }
        }

        // Doors — facing = player look (not opposite)
        if (block instanceof DoorBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing);
            }
        }

        // Beds — facing = foot-to-head direction
        if (block instanceof BedBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing);
            }
        }

        // Catch-all for other HORIZONTAL_FACING blocks
        if (desired.contains(Properties.HORIZONTAL_FACING)) {
            if (block instanceof CampfireBlock
                    || block instanceof BeehiveBlock
                    || block instanceof LecternBlock
                    || block instanceof GrindstoneBlock
                    || block instanceof BellBlock
                    || block instanceof RespawnAnchorBlock) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing.getOpposite());
            }
        }

        return null;
    }

    /** Returns the pitch needed for UP/DOWN facing blocks, or null. */
    private static Float getRequiredPitch(BlockState desired) {
        Block block = desired.getBlock();

        // 6-direction FACING blocks: dispenser, dropper, observer, piston
        if (block instanceof DispenserBlock
                || block instanceof ObserverBlock
                || block instanceof PistonBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing == Direction.UP)   return -90.0f; // look straight up
                if (facing == Direction.DOWN) return 90.0f;  // look straight down
            }
        }

        // Barrels — FACING (all 6 directions)
        if (block instanceof BarrelBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        // Shulker boxes — FACING (all 6 directions)
        if (block instanceof ShulkerBoxBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        // End rods, lightning rods — FACING (all 6)
        if (block instanceof EndRodBlock || block instanceof LightningRodBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        return null;
    }

    /** Adjusts hit Y for correct slab/stair/trapdoor half placement. */
    private static Vec3d adjustHitForHalf(Vec3d hitPos, BlockPos neighbor,
                                          Direction clickSide, BlockState desired) {
        Block block = desired.getBlock();

        // Stairs
        if (block instanceof StairsBlock && desired.contains(Properties.BLOCK_HALF)) {
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            if (clickSide == Direction.UP) {
                // TOP stair on top face → force upper hit
                if (half == BlockHalf.TOP) {
                    return forceHitY(hitPos, neighbor, true);
                }
            } else if (clickSide == Direction.DOWN) {
                if (half == BlockHalf.BOTTOM) {
                    return forceHitY(hitPos, neighbor, false);
                }
            } else {
                // Side face — control via Y position
                return forceHitY(hitPos, neighbor, half == BlockHalf.TOP);
            }
        }

        // Slabs
        if (block instanceof SlabBlock && desired.contains(Properties.SLAB_TYPE)) {
            SlabType type = desired.get(Properties.SLAB_TYPE);
            if (type == SlabType.DOUBLE) return hitPos; // double slab — no half

            boolean wantTop = (type == SlabType.TOP);
            if (clickSide == Direction.UP) {
                if (wantTop) return forceHitY(hitPos, neighbor, true);
            } else if (clickSide == Direction.DOWN) {
                if (!wantTop) return forceHitY(hitPos, neighbor, false);
            } else {
                return forceHitY(hitPos, neighbor, wantTop);
            }
        }

        // Trapdoors
        if (block instanceof TrapdoorBlock && desired.contains(Properties.BLOCK_HALF)) {
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            if (clickSide == Direction.UP) {
                // TOP trapdoor on top face → force upper hit
                if (half == BlockHalf.TOP) {
                    return forceHitY(hitPos, neighbor, true);
                }
            } else if (clickSide == Direction.DOWN) {
                // BOTTOM trapdoor on bottom face → force lower hit
                if (half == BlockHalf.BOTTOM) {
                    return forceHitY(hitPos, neighbor, false);
                }
            } else {
                return forceHitY(hitPos, neighbor, half == BlockHalf.TOP);
            }
        }

        return hitPos;
    }

    /** Forces hit Y to upper or lower quarter of the block face. */
    private static Vec3d forceHitY(Vec3d hitPos, BlockPos neighbor, boolean upper) {
        double y = upper
                ? neighbor.getY() + 0.75   // upper quarter of the block
                : neighbor.getY() + 0.25;  // lower quarter of the block
        return new Vec3d(hitPos.x, y, hitPos.z);
    }

    /** Adjusts hit X/Z to influence door hinge side. */
    private static Vec3d adjustHitForDoorHinge(Vec3d hitPos, BlockPos neighbor,
                                                BlockState desired) {
        if (!(desired.getBlock() instanceof DoorBlock)) return hitPos;
        if (!desired.contains(Properties.DOOR_HINGE)
                || !desired.contains(Properties.HORIZONTAL_FACING)) return hitPos;

        DoorHinge hinge = desired.get(Properties.DOOR_HINGE);
        Direction facing = desired.get(Properties.HORIZONTAL_FACING);
        boolean wantLeft = (hinge == DoorHinge.LEFT);

        double bx = neighbor.getX();
        double bz = neighbor.getZ();

        switch (facing) {
            case NORTH -> {
                // LEFT → hitX ≤ 0.5 → use 0.25, RIGHT → hitX > 0.5 → use 0.75
                double x = wantLeft ? bx + 0.25 : bx + 0.75;
                return new Vec3d(x, hitPos.y, hitPos.z);
            }
            case SOUTH -> {
                // LEFT → hitX ≥ 0.5 → use 0.75, RIGHT → hitX < 0.5 → use 0.25
                double x = wantLeft ? bx + 0.75 : bx + 0.25;
                return new Vec3d(x, hitPos.y, hitPos.z);
            }
            case EAST -> {
                // LEFT → hitZ ≤ 0.5 → use 0.25, RIGHT → hitZ > 0.5 → use 0.75
                double z = wantLeft ? bz + 0.25 : bz + 0.75;
                return new Vec3d(hitPos.x, hitPos.y, z);
            }
            case WEST -> {
                // LEFT → hitZ ≥ 0.5 → use 0.75, RIGHT → hitZ < 0.5 → use 0.25
                double z = wantLeft ? bz + 0.75 : bz + 0.25;
                return new Vec3d(hitPos.x, hitPos.y, z);
            }
            default -> { return hitPos; }
        }
    }

    /** Adjusts hit Y for air-placed half-blocks (TOP → Y+0.75). */
    private static Vec3d adjustHitForAirPlace(Vec3d hitPos, BlockPos target,
                                              BlockState desired) {
        Block block = desired.getBlock();

        if (block instanceof StairsBlock && desired.contains(Properties.BLOCK_HALF)) {
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            if (half == BlockHalf.TOP) {
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
            }
        }

        if (block instanceof SlabBlock && desired.contains(Properties.SLAB_TYPE)) {
            SlabType type = desired.get(Properties.SLAB_TYPE);
            if (type == SlabType.TOP) {
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
            }
        }

        if (block instanceof TrapdoorBlock && desired.contains(Properties.BLOCK_HALF)) {
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            if (half == BlockHalf.TOP) {
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
            }
        }

        return hitPos;
    }

    /** Finds an adjacent face, with orientation awareness for wall/floor/ceiling blocks. */
    private static Direction findOrientedPlacementFace(World world, BlockPos target,
                                                       BlockState desired) {
        Block block = desired.getBlock();

        // Wall-mounted torches
        if (block instanceof WallTorchBlock
                || block instanceof WallRedstoneTorchBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                Direction supportDir = facing.getOpposite();
                return requireSolidFace(world, target, supportDir);
            }
        }

        // Standing torches
        if (block instanceof TorchBlock && !(block instanceof WallTorchBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }
        if (block instanceof RedstoneTorchBlock && !(block instanceof WallRedstoneTorchBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Wall signs
        if (block instanceof WallSignBlock || block instanceof WallHangingSignBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // Standing signs
        if (block instanceof SignBlock && !(block instanceof WallSignBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Hanging signs
        if (block instanceof HangingSignBlock && !(block instanceof WallHangingSignBlock)) {
            return requireSolidFace(world, target, Direction.UP);
        }

        // Standing banners
        if (block instanceof AbstractBannerBlock && !(block instanceof WallBannerBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Wall banners
        if (block instanceof WallBannerBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // Ladders
        if (block instanceof LadderBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // Lanterns
        if (block instanceof LanternBlock) {
            if (desired.contains(Properties.HANGING) && desired.get(Properties.HANGING)) {
                return requireSolidFace(world, target, Direction.UP);
            } else {
                return requireSolidFace(world, target, Direction.DOWN);
            }
        }

        // Buttons (wall / floor / ceiling)
        if (block instanceof ButtonBlock) {
            return resolveWallMountedFace(world, target, desired);
        }

        // Levers (wall / floor / ceiling)
        if (block instanceof LeverBlock) {
            return resolveWallMountedFace(world, target, desired);
        }

        // Skulls / heads on walls
        if (block instanceof WallSkullBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }
        if (block instanceof SkullBlock && !(block instanceof WallSkullBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Trapdoors — BLOCK_HALF determines attachment direction
        if (block instanceof TrapdoorBlock) {
            if (desired.contains(Properties.BLOCK_HALF)) {
                BlockHalf half = desired.get(Properties.BLOCK_HALF);
                if (half == BlockHalf.TOP) {
                    // Top trapdoor — click bottom face of block above
                    return requireSolidFace(world, target, Direction.UP);
                } else {
                    // Bottom trapdoor — click top face of block below
                    return requireSolidFace(world, target, Direction.DOWN);
                }
            }
            return findPlacementFace(world, target);
        }

        // Doors — click floor (lower half only, upper auto-created)
        if (block instanceof DoorBlock) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Beds — click floor (foot part only, head auto-created)
        if (block instanceof BedBlock) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Tall plants — place lower half on floor
        if (block instanceof TallPlantBlock) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Hoppers — facing from clicked face
        if (block instanceof HopperBlock) {
            if (desired.contains(Properties.HOPPER_FACING)) {
                Direction facing = desired.get(Properties.HOPPER_FACING);
                if (facing == Direction.DOWN) {
                    return findPlacementFace(world, target);
                } else {
                    // Click the face in the output direction
                    return requireSolidFace(world, target, facing);
                }
            }
        }

        // Stairs/Slabs/Trapdoors — prefer side faces for half control
        if (block instanceof StairsBlock
                || block instanceof SlabBlock
                || block instanceof TrapdoorBlock) {
            Direction sideFace = findSidePlacementFace(world, target);
            if (sideFace != null) return sideFace;
            // Fall through to generic if no side face available
            return findPlacementFace(world, target);
        }

        // Pillar blocks — click face along desired axis
        if (block instanceof PillarBlock && desired.contains(Properties.AXIS)) {
            Direction.Axis desiredAxis = desired.get(Properties.AXIS);
            Direction preferred = preferFaceForAxis(world, target, desiredAxis);
            if (preferred != null) return preferred;
            // No matching face — air placement fallback
            return null;
        }

        return findPlacementFace(world, target);
    }

    /** Returns dir if there's a solid support block in that direction, else null. */
    private static Direction requireSolidFace(World world, BlockPos target, Direction dir) {
        BlockPos neighbor = target.offset(dir);
        BlockState state = world.getBlockState(neighbor);
        if (!state.isReplaceable()
                && state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
            return dir;
        }
        return null; // required support block not present
    }

    /** Resolves placement face for FLOOR/WALL/CEILING blocks (buttons, levers). */
    private static Direction resolveWallMountedFace(World world, BlockPos target,
                                                     BlockState desired) {
        if (desired.contains(Properties.BLOCK_FACE)) {
            BlockFace face = desired.get(Properties.BLOCK_FACE);
            return switch (face) {
                case FLOOR   -> requireSolidFace(world, target, Direction.DOWN);
                case CEILING -> requireSolidFace(world, target, Direction.UP);
                case WALL    -> {
                    if (desired.contains(Properties.HORIZONTAL_FACING)) {
                        Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                        yield requireSolidFace(world, target, facing.getOpposite());
                    }
                    yield findPlacementFace(world, target);
                }
            };
        }
        return findPlacementFace(world, target);
    }

    /** Finds a face along the desired axis for pillar block placement. */
    private static Direction preferFaceForAxis(World world, BlockPos target,
                                               Direction.Axis desiredAxis) {
        // Faces whose normal matches the axis
        Direction[] axisDirections = switch (desiredAxis) {
            case X -> new Direction[]{ Direction.EAST, Direction.WEST };
            case Y -> new Direction[]{ Direction.UP, Direction.DOWN };
            case Z -> new Direction[]{ Direction.NORTH, Direction.SOUTH };
        };

        for (Direction dir : axisDirections) {
            BlockPos neighbor = target.offset(dir);
            BlockState state = world.getBlockState(neighbor);
            if (!state.isReplaceable()
                    && state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                return dir;
            }
        }
        return null; // no preferred face available — caller falls back to default
    }

    /** Checks if two states of the same block differ in orientation properties. */
    public static boolean isOrientationMismatch(BlockState existing, BlockState desired) {
        if (existing.getBlock() != desired.getBlock()) return false;

        // Check all common orientation properties
        if (desired.contains(Properties.HORIZONTAL_FACING)
                && existing.contains(Properties.HORIZONTAL_FACING)) {
            if (existing.get(Properties.HORIZONTAL_FACING) != desired.get(Properties.HORIZONTAL_FACING)) {
                return true;
            }
        }
        if (desired.contains(Properties.FACING)
                && existing.contains(Properties.FACING)) {
            if (existing.get(Properties.FACING) != desired.get(Properties.FACING)) {
                return true;
            }
        }
        if (desired.contains(Properties.AXIS)
                && existing.contains(Properties.AXIS)) {
            if (existing.get(Properties.AXIS) != desired.get(Properties.AXIS)) {
                return true;
            }
        }
        if (desired.contains(Properties.BLOCK_HALF)
                && existing.contains(Properties.BLOCK_HALF)) {
            if (existing.get(Properties.BLOCK_HALF) != desired.get(Properties.BLOCK_HALF)) {
                return true;
            }
        }
        if (desired.contains(Properties.SLAB_TYPE)
                && existing.contains(Properties.SLAB_TYPE)) {
            if (existing.get(Properties.SLAB_TYPE) != desired.get(Properties.SLAB_TYPE)) {
                return true;
            }
        }
        // Standing banners, standing signs, hanging signs — ROTATION (0-15)
        if (desired.contains(Properties.ROTATION)
                && existing.contains(Properties.ROTATION)) {
            if (!existing.get(Properties.ROTATION).equals(desired.get(Properties.ROTATION))) {
                return true;
            }
        }
        // Hoppers — HOPPER_FACING (DOWN + 4 horizontal)
        if (desired.contains(Properties.HOPPER_FACING)
                && existing.contains(Properties.HOPPER_FACING)) {
            if (existing.get(Properties.HOPPER_FACING) != desired.get(Properties.HOPPER_FACING)) {
                return true;
            }
        }
        return false;
    }

    /** Converts a cardinal Direction to player yaw in degrees. */
    private static float directionToYaw(Direction dir) {
        return switch (dir) {
            case SOUTH -> 0.0f;
            case WEST  -> 90.0f;
            case NORTH -> 180.0f;
            case EAST  -> -90.0f;
            default    -> 0.0f;  // UP/DOWN — irrelevant
        };
    }

    /** Computes pitch angle from eye to target. */
    private static float computePitchToward(Vec3d eye, Vec3d target) {
        Vec3d diff = target.subtract(eye);
        double horizDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        return (float) -(MathHelper.atan2(diff.y, horizDist) * (180.0 / Math.PI));
    }

    private static float snapToMouseGCD(float desired, float serverCurrent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double sens = mc.options.getMouseSensitivity().getValue();
        double gcd = Math.pow(sens * 0.6 + 0.2, 3.0) * 1.2;
        if (gcd < 0.001) return desired;            // zero-sensitivity guard
        return (float) (desired - (desired - serverCurrent) % gcd);
    }

    // placement face finding

    /** Finds the best adjacent solid face, preferring non-interactive neighbors. */
    public static Direction findPlacementFace(World world, BlockPos target) {
        Direction fallback = null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = target.offset(dir);
            BlockState neighborState = world.getBlockState(neighbor);

            if (neighborState.isReplaceable()) continue;
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;

            if (!isInteractive(neighborState.getBlock())) {
                return dir;
            }
            if (fallback == null) fallback = dir;
        }
        return fallback;
    }

    /** Finds a horizontal side face (no UP/DOWN) for half-block placement. */
    private static Direction findSidePlacementFace(World world, BlockPos target) {
        Direction fallback = null;
        for (Direction dir : new Direction[]{ Direction.NORTH, Direction.SOUTH,
                                              Direction.EAST,  Direction.WEST }) {
            BlockPos neighbor = target.offset(dir);
            BlockState neighborState = world.getBlockState(neighbor);
            if (neighborState.isReplaceable()) continue;
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;
            if (!isInteractive(neighborState.getBlock())) {
                return dir;
            }
            if (fallback == null) fallback = dir;
        }
        return fallback;
    }

    /** Finds a non-interactive adjacent face (for chests, etc). */
    private static Direction findNonInteractiveFace(World world, BlockPos target) {
        Direction interactive = null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = target.offset(dir);
            BlockState neighborState = world.getBlockState(neighbor);
            if (neighborState.isReplaceable()) continue;
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;
            if (!isInteractive(neighborState.getBlock())) {
                return dir; // non-interactive — ideal
            }
            if (interactive == null) interactive = dir;
        }
        return interactive; // null if nothing solid adjacent
    }

    /** Whether any adjacent block is solid (supports placement). */
    public static boolean hasAdjacentSolid(World world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            BlockState state = world.getBlockState(neighbor);
            if (!state.isReplaceable() &&
                    state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                return true;
            }
        }
        return false;
    }

    // interactive block detection

    /** Set of block classes that open GUIs / handle interactions on right-click. */
    private static final Set<Class<? extends Block>> INTERACTIVE = Set.of(
            AbstractChestBlock.class,
            AbstractFurnaceBlock.class,
            AnvilBlock.class,
            BarrelBlock.class,
            BeaconBlock.class,
            BedBlock.class,
            BellBlock.class,
            BrewingStandBlock.class,
            ButtonBlock.class,
            CartographyTableBlock.class,
            CakeBlock.class,
            CommandBlock.class,
            ComparatorBlock.class,
            CraftingTableBlock.class,
            DoorBlock.class,
            DispenserBlock.class,
            DropperBlock.class,
            EnchantingTableBlock.class,
            FenceGateBlock.class,
            GrindstoneBlock.class,
            HopperBlock.class,
            JukeboxBlock.class,
            LecternBlock.class,
            LeverBlock.class,
            LoomBlock.class,
            NoteBlock.class,
            RepeaterBlock.class,
            ShulkerBoxBlock.class,
            SmithingTableBlock.class,
            StonecutterBlock.class,
            TrapdoorBlock.class
    );

    /** Returns true if right-clicking this block opens a GUI or toggles state. */
    public static boolean isInteractive(Block block) {
        for (Class<? extends Block> clazz : INTERACTIVE) {
            if (clazz.isInstance(block)) return true;
        }
        return false;
    }
}

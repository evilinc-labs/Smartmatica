package dev.moar.util;

/*? if >=26.1 {*//*
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.piston.*;
*//*?} else {*/
import net.minecraft.block.*;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.BedPart;
*//*?} else {*/
import net.minecraft.block.enums.BedPart;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.AttachFace;
*//*?} else {*/
import net.minecraft.block.enums.BlockFace;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.Half;
*//*?} else {*/
import net.minecraft.block.enums.BlockHalf;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
*//*?} else {*/
import net.minecraft.block.enums.DoorHinge;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
*//*?} else {*/
import net.minecraft.block.enums.DoubleBlockHalf;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.SlabType;
*//*?} else {*/
import net.minecraft.block.enums.SlabType;
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
import net.minecraft.world.entity.player.Inventory;
*//*?} else {*/
import net.minecraft.entity.player.PlayerInventory;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
*//*?} else {*/
import net.minecraft.state.property.Properties;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
*//*?} else if >=1.21.8 {*//*
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
*//*?} else {*/
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
*//*?} else {*/
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
*//*?} else {*/
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
*//*?} else {*/
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ContainerInput;
*//*?} else {*/
import net.minecraft.screen.slot.SlotActionType;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionResult;
*//*?} else {*/
import net.minecraft.util.ActionResult;
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
import net.minecraft.world.phys.shapes.Shapes;
*//*?} else {*/
import net.minecraft.util.shape.VoxelShapes;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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
    private static final int   WINDOW_SIZE = 6;
    private static final long  WINDOW_MS   = 300;

    private static final int  BATCH_MAX         = 20;
    private static final long BATCH_COOLDOWN_MS = 300L;
    private static long       lastBatchMs       = 0L;

    private static final long[] placeHistory = new long[WINDOW_SIZE];
    private static int          historyIdx   = 0;

    /** Per-block cadence floor -- the minimum gap (in ns) between any two
     *  consecutive placements. Derived from the user-facing BPS setting. */
    private static int    bps               = 13;
    private static long   lastPlacementNano = 0;

    // Server-side placement verification — detect anti-cheat rollbacks.
    private static final int VERIFY_DELAY_TICKS = 4;
    private static final int MAX_VERIFY_QUEUE = 32;
    private static int consecutiveRejections = 0;
    private static int totalRejections = 0;

    private record PendingVerification(BlockPos pos, BlockState expected, long placeTick) {}
    private static final ArrayDeque<PendingVerification> verifyQueue = new ArrayDeque<>();

    /** Number of consecutive placements that were rejected by the server. */
    public static int getConsecutiveRejections() { return consecutiveRejections; }
    /** Total placements rejected since last reset. */
    public static int getTotalRejections() { return totalRejections; }
    public static void resetRejectionCounters() {
        consecutiveRejections = 0;
        totalRejections = 0;
    }

    /** Tick the verification queue. Call once per game tick. */
    public static void tickVerification() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.level == null) return;
        *//*?} else {*/
        if (mc.world == null) return;
        /*?}*/
        /*? if >=26.1 {*//*
        long currentTick = mc.level.getGameTime();
        *//*?} else {*/
        long currentTick = mc.world.getTime();
        /*?}*/

        while (!verifyQueue.isEmpty()) {
            PendingVerification pv = verifyQueue.peek();
            if (currentTick - pv.placeTick < VERIFY_DELAY_TICKS) break;
            verifyQueue.poll();

            /*? if >=26.1 {*//*
            BlockState actual = mc.level.getBlockState(pv.pos);
            *//*?} else {*/
            BlockState actual = mc.world.getBlockState(pv.pos);
            /*?}*/
            if (actual.getBlock() == pv.expected.getBlock()) {
                // Confirmed — server accepted the placement
                consecutiveRejections = 0;
            } else {
                // Server rolled back the block
                consecutiveRejections++;
                totalRejections++;
            }
        }
    }

    private static void enqueueVerification(BlockPos pos, BlockState expected) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.level == null) return;
        *//*?} else {*/
        if (mc.world == null) return;
        /*?}*/
        if (verifyQueue.size() >= MAX_VERIFY_QUEUE) {
            verifyQueue.poll(); // drop oldest
        }
        verifyQueue.add(new PendingVerification(
                /*? if >=26.1 {*//*
                pos.immutable(), expected, mc.level.getGameTime()));
                *//*?} else {*/
                pos.toImmutable(), expected, mc.world.getTime()));
                /*?}*/
    }

    public static void setBps(int value) {
        bps = Math.max(1, Math.min(20, value));
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
                /*? if >=26.1 {*//*
                Minecraft mc = Minecraft.getInstance();
                *//*?} else {*/
                MinecraftClient mc = MinecraftClient.getInstance();
                /*?}*/
                if (mc.player != null) pressSneakPacket(mc.player);
            } else {
                releaseSneakPacket();
            }
        }
        if (phase == PlacePhase.BREAKING) {
            /*? if >=26.1 {*//*
            Minecraft mc = Minecraft.getInstance();
            *//*?} else {*/
            MinecraftClient mc = MinecraftClient.getInstance();
            /*?}*/
            /*? if >=26.1 {*//*
            if (mc.gameMode != null) {
            *//*?} else {*/
            if (mc.interactionManager != null) {
            /*?}*/
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
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
        verifyQueue.clear();
        consecutiveRejections = 0;
        totalRejections = 0;
    }

    public static void clearCorrectionHistory() {
        correctionAttempts.clear();
    }

    public static void pruneCompletedCorrections() {
        correctionAttempts.values().removeIf(v -> v < MAX_CORRECTION_ATTEMPTS);
    }

    private static Map<Item, Integer> cachedInventory = Map.of();
    private static long cachedInventoryTick = -1;

    /** Cached inventory (invalidated once per tick). */
    public static Map<Item, Integer> getInventoryContentsCached() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.level == null) return Map.of();
        *//*?} else {*/
        if (mc.world == null) return Map.of();
        /*?}*/
        /*? if >=26.1 {*//*
        long tick = mc.level.getGameTime();
        *//*?} else {*/
        long tick = mc.world.getTime();
        /*?}*/
        if (tick != cachedInventoryTick) {
            cachedInventory = getInventoryContents();
            cachedInventoryTick = tick;
        }
        return cachedInventory;
    }

    /** Tick the placement state machine. Returns true when a block was placed. */
    public static boolean tick() {
        return switch (phase) {
            case IDLE     -> false;
            case ROTATING -> {

                boolean isLiquid = pendingDesired != null
                        /*? if >=26.1 {*//*
                        && pendingDesired.getBlock() instanceof LiquidBlock;
                        *//*?} else {*/
                        && pendingDesired.getBlock() instanceof FluidBlock;
                        /*?}*/
                yield isLiquid ? tickRotate() : tickPlaceSingleTick();
            }
            case PLACING  -> tickPlace();
            case FINISHING -> tickFinish();
            case BREAKING -> tickBreaking();
        };
    }

    private static boolean tickPlaceSingleTick() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
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
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
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

        /*? if >=26.1 {*//*
        float currentYaw = mc.player.getYRot();
        *//*?} else {*/
        float currentYaw = mc.player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        float currentPitch = mc.player.getXRot();
        *//*?} else {*/
        float currentPitch = mc.player.getPitch();
        /*?}*/

        /*? if >=26.1 {*//*
        float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
        *//*?} else {*/
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        /*?}*/
        float pitchDiff = targetPitch - currentPitch;

        /*? if >=26.1 {*//*
        float newYaw = currentYaw + Mth.clamp(yawDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        *//*?} else {*/
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        /*?}*/
        /*? if >=26.1 {*//*
        float newPitch = currentPitch + Mth.clamp(pitchDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        *//*?} else {*/
        float newPitch = currentPitch + MathHelper.clamp(pitchDiff, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        /*?}*/
        /*? if >=26.1 {*//*
        newPitch = Mth.clamp(newPitch, -90.0f, 90.0f);
        *//*?} else {*/
        newPitch = MathHelper.clamp(newPitch, -90.0f, 90.0f);
        /*?}*/

        /*? if >=26.1 {*//*
        mc.player.setYRot(newYaw);
        *//*?} else {*/
        mc.player.setYaw(newYaw);
        /*?}*/
        /*? if >=26.1 {*//*
        mc.player.setXRot(newPitch);
        *//*?} else {*/
        mc.player.setPitch(newPitch);
        /*?}*/

        /*? if >=26.1 {*//*
        boolean converged = Math.abs(Mth.wrapDegrees(targetYaw - newYaw)) < CONVERGE_THRESHOLD
        *//*?} else {*/
        boolean converged = Math.abs(MathHelper.wrapDegrees(targetYaw - newYaw)) < CONVERGE_THRESHOLD
        /*?}*/
                         && Math.abs(targetPitch - newPitch) < CONVERGE_THRESHOLD;

        if (converged || rotateTicks >= MAX_ROTATE_TICKS) {
            if (converged) {
                /*? if >=26.1 {*//*
                mc.player.setYRot(targetYaw);
                *//*?} else {*/
                mc.player.setYaw(targetYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(targetPitch);
                *//*?} else {*/
                mc.player.setPitch(targetPitch);
                /*?}*/
            }

            /*? if >=26.1 {*//*
            sendLookPacket(mc.player, mc.player.getYRot(), mc.player.getXRot());
            *//*?} else {*/
            sendLookPacket(mc.player, mc.player.getYaw(), mc.player.getPitch());
            /*?}*/
            if (pendingNeedsSneak) {
                pressSneakPacket(mc.player);
            }

            phase = PlacePhase.PLACING;
        }

        return false;
    }

    private static boolean tickPlace() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
        /*?}*/
            reset();
            return false;
        }

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/

        if (pendingItem != null) {
            /*? if >=26.1 {*//*
            Inventory inv = player.getInventory();
            *//*?} else {*/
            PlayerInventory inv = player.getInventory();
            /*?}*/
            /*? if >=26.1 {*//*
            Item held = inv.getItem(inv.getSelectedSlot()).getItem();
            *//*?} else if >=1.21.5 {*//*
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
                        /*? if >=26.1 {*//*
                        sendSilentLookPacket(player, player.getYRot(), player.getXRot());
                        *//*?} else {*/
                        sendSilentLookPacket(player, player.getYaw(), player.getPitch());
                        /*?}*/
                    }
                    phase = PlacePhase.IDLE;
                    pendingTarget = null;
                    pendingDesired = null;
                    return false;
                }
            }
        }

        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/

        /*? if >=26.1 {*//*
        float placeYaw   = silentRotation ? targetYaw   : player.getYRot();
        *//*?} else {*/
        float placeYaw   = silentRotation ? targetYaw   : player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        float placePitch  = silentRotation ? targetPitch : player.getXRot();
        *//*?} else {*/
        float placePitch  = silentRotation ? targetPitch : player.getPitch();
        /*?}*/

        BlockHitResult hitResult;
        if (pendingAirPlace) {
            Direction airFace = Direction.UP;
            /*? if >=26.1 {*//*
            Vec3 hitPos = Vec3.atCenterOf(pendingTarget).add(
            *//*?} else {*/
            Vec3d hitPos = Vec3d.ofCenter(pendingTarget).add(
            /*?}*/
                    /*? if >=26.1 {*//*
                    airFace.getStepX() * 0.5,
                    *//*?} else {*/
                    airFace.getOffsetX() * 0.5,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    airFace.getStepY() * 0.5,
                    *//*?} else {*/
                    airFace.getOffsetY() * 0.5,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    airFace.getStepZ() * 0.5);
                    *//*?} else {*/
                    airFace.getOffsetZ() * 0.5);
                    /*?}*/
            hitPos = adjustHitForAirPlace(hitPos, pendingTarget, pendingDesired);
            hitResult = new BlockHitResult(hitPos, airFace, pendingTarget, false);
        } else {
            /*? if >=26.1 {*//*
            BlockPos neighbor = pendingTarget.relative(pendingFace);
            *//*?} else {*/
            BlockPos neighbor = pendingTarget.offset(pendingFace);
            /*?}*/
            Direction clickSide = pendingFace.getOpposite();
            /*? if >=26.1 {*//*
            Vec3 hitPos = computeRayFaceHit(eyePos, placeYaw, placePitch,
            *//*?} else {*/
            Vec3d hitPos = computeRayFaceHit(eyePos, placeYaw, placePitch,
            /*?}*/
                                              /*? if >=26.1 {*//*
                                              neighbor, clickSide, mc.level);
                                              *//*?} else {*/
                                              neighbor, clickSide, mc.world);
                                              /*?}*/
            hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, pendingDesired);
            hitPos = adjustHitForDoorHinge(hitPos, neighbor, pendingDesired);

            hitResult = new BlockHitResult(hitPos, clickSide, neighbor, false);
        }

        {
            double reachSq = 4.5 * 4.5;
            /*? if >=26.1 {*//*
            if (eyePos.distanceToSqr(hitResult.getLocation()) > reachSq) {
            *//*?} else {*/
            if (eyePos.squaredDistanceTo(hitResult.getPos()) > reachSq) {
            /*?}*/
                phase = PlacePhase.IDLE;
                pendingTarget = null;
                pendingDesired = null;
                return false;
            }
        }


        if (silentRotation && !singleTickInProgress) {
            sendSilentLookPacket(player, placeYaw, placePitch);
        }

        /*? if >=26.1 {*//*
        boolean isLiquidPlacement = pendingDesired.getBlock() instanceof LiquidBlock;
        *//*?} else {*/
        boolean isLiquidPlacement = pendingDesired.getBlock() instanceof FluidBlock;
        /*?}*/
        boolean placed;
        if (isLiquidPlacement) {
            /*? if >=26.1 {*//*
            InteractionResult result = mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            *//*?} else {*/
            ActionResult result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            /*?}*/
            /*? if >=26.1 {*//*
            if (result.consumesAction()) {
            *//*?} else {*/
            if (result.isAccepted()) {
            /*?}*/
                /*? if >=26.1 {*//*
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
            }
            /*? if >=26.1 {*//*
            if (result.consumesAction()) {
            *//*?} else {*/
            if (result.isAccepted()) {
            /*?}*/
                /*? if >=26.1 {*//*
                BlockState afterState = mc.level.getBlockState(pendingTarget);
                *//*?} else {*/
                BlockState afterState = mc.world.getBlockState(pendingTarget);
                /*?}*/
                /*? if >=26.1 {*//*
                placed = afterState.getBlock() instanceof LiquidBlock
                *//*?} else {*/
                placed = afterState.getBlock() instanceof FluidBlock
                /*?}*/
                      /*? if >=26.1 {*//*
                      && afterState.getFluidState().isSource();
                      *//*?} else {*/
                      && afterState.getFluidState().isStill();
                      /*?}*/
            } else {
                placed = false;
            }
        } else {
            /*? if >=26.1 {*//*
            player.connection.send(new ServerboundPlayerActionPacket(
            *//*?} else {*/
            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            /*?}*/
                    /*? if >=26.1 {*//*
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    *//*?} else {*/
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    BlockPos.ZERO, Direction.DOWN));
                    *//*?} else {*/
                    BlockPos.ORIGIN, Direction.DOWN));
                    /*?}*/
            /*? if >=26.1 {*//*
            player.connection.send(new ServerboundUseItemOnPacket(
            *//*?} else {*/
            player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            /*?}*/
                    /*? if >=26.1 {*//*
                    InteractionHand.OFF_HAND, hitResult,
                    *//*?} else {*/
                    Hand.OFF_HAND, hitResult,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.containerMenu.getStateId() + 2));
                    *//*?} else {*/
                    player.currentScreenHandler.getRevision() + 2));
                    /*?}*/
            /*? if >=26.1 {*//*
            player.connection.send(new ServerboundPlayerActionPacket(
            *//*?} else {*/
            player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            /*?}*/
                    /*? if >=26.1 {*//*
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    *//*?} else {*/
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    BlockPos.ZERO, Direction.DOWN));
                    *//*?} else {*/
                    BlockPos.ORIGIN, Direction.DOWN));
                    /*?}*/
            /*? if >=26.1 {*//*
            player.swing(InteractionHand.MAIN_HAND);
            *//*?} else {*/
            player.swingHand(Hand.MAIN_HAND);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.level.setBlockAndUpdate(pendingTarget, pendingDesired);
            *//*?} else {*/
            mc.world.setBlockState(pendingTarget, pendingDesired);
            /*?}*/
            enqueueVerification(pendingTarget, pendingDesired);
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
                    /*? if >=26.1 {*//*
                    sendSilentLookPacket(player, player.getYRot(), player.getXRot());
                    *//*?} else {*/
                    sendSilentLookPacket(player, player.getYaw(), player.getPitch());
                    /*?}*/
                }
            }
            phase = PlacePhase.IDLE;
            pendingTarget = null;
            pendingItem = null;
        }

        return placed;
    }

    private static boolean tickFinish() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
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
                    /*? if >=26.1 {*//*
                    sendSilentLookPacket(mc.player, mc.player.getYRot(), mc.player.getXRot());
                    *//*?} else {*/
                    sendSilentLookPacket(mc.player, mc.player.getYaw(), mc.player.getPitch());
                    /*?}*/
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
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
        /*?}*/
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
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            restoreLook(mc.player);
            correctionTarget = null;
            correctionDesired = null;
            phase = PlacePhase.IDLE;
            return false;
        }

        /*? if >=26.1 {*//*
        BlockState current = mc.level.getBlockState(correctionTarget);
        *//*?} else {*/
        BlockState current = mc.world.getBlockState(correctionTarget);
        /*?}*/
        /*? if >=26.1 {*//*
        if (current.isAir() || current.canBeReplaced()) {
        *//*?} else {*/
        if (current.isAir() || current.isReplaceable()) {
        /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            postBreakWait = 5;
            return false;
        }

        /*? if >=26.1 {*//*
        Vec3 eyePos = mc.player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = mc.player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 blockCenter = Vec3.atCenterOf(correctionTarget);
        *//*?} else {*/
        Vec3d blockCenter = Vec3d.ofCenter(correctionTarget);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toBlock = blockCenter.subtract(eyePos);
        *//*?} else {*/
        Vec3d toBlock = blockCenter.subtract(eyePos);
        /*?}*/
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        /*?}*/
        /*? if >=26.1 {*//*
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        /*?}*/

        sendLookPacket(mc.player, breakYaw, breakPitch);

        Direction breakFace = Direction.UP;
        /*? if >=26.1 {*//*
        mc.gameMode.continueDestroyBlock(
        *//*?} else {*/
        mc.interactionManager.updateBlockBreakingProgress(
        /*?}*/
                correctionTarget, breakFace);
        /*? if >=26.1 {*//*
        mc.player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        mc.player.swingHand(Hand.MAIN_HAND);
        /*?}*/

        return false;
    }

    public static boolean placeBlock(BlockPos target, BlockState desired, boolean allowSwap) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) return false;
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        /*?}*/
        if (phase != PlacePhase.IDLE) return false;

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        // Skip auto-created parts (door upper, bed head, tall plant upper)
        Block desiredBlock = desired.getBlock();
        if (desiredBlock instanceof DoorBlock
                /*? if >=26.1 {*//*
                && desired.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && desired.contains(Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && desired.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                *//*?} else {*/
                && desired.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                /*?}*/
            return false;
        }
        if (desiredBlock instanceof BedBlock
                /*? if >=26.1 {*//*
                && desired.hasProperty(BlockStateProperties.BED_PART)
                *//*?} else {*/
                && desired.contains(Properties.BED_PART)
                /*?}*/
                /*? if >=26.1 {*//*
                && desired.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                *//*?} else {*/
                && desired.get(Properties.BED_PART) == BedPart.HEAD) {
                /*?}*/
            return false;
        }
        /*? if >=26.1 {*//*
        if (desiredBlock instanceof DoublePlantBlock
        *//*?} else {*/
        if (desiredBlock instanceof TallPlantBlock
        /*?}*/
                /*? if >=26.1 {*//*
                && desired.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && desired.contains(Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && desired.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                *//*?} else {*/
                && desired.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                /*?}*/
            return false;
        }

        // Wrong orientation → break and re-place
        BlockState existing = world.getBlockState(target);
        /*? if >=26.1 {*//*
        if (!existing.isAir() && !existing.canBeReplaced()
        *//*?} else {*/
        if (!existing.isAir() && !existing.isReplaceable()
        /*?}*/
                && existing.getBlock() == desired.getBlock()
                && !existing.equals(desired)
                && isOrientationMismatch(existing, desired)) {
            /*? if >=26.1 {*//*
            BlockPos immutable = target.immutable();
            *//*?} else {*/
            BlockPos immutable = target.toImmutable();
            /*?}*/
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            return startCorrection(target, desired, player, mc);
        }

        // Scaffold removal
        /*? if >=26.1 {*//*
        if (!existing.isAir() && !existing.canBeReplaced()
        *//*?} else {*/
        if (!existing.isAir() && !existing.isReplaceable()
        /*?}*/
                && existing.getBlock() != desired.getBlock()
                && PrinterDatabase.isScaffold(target)) {
            /*? if >=26.1 {*//*
            BlockPos immutable = target.immutable();
            *//*?} else {*/
            BlockPos immutable = target.toImmutable();
            /*?}*/
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            return startCorrection(target, desired, player, mc);
        }

        // Foreign block replacement
        /*? if >=26.1 {*//*
        if (!existing.isAir() && !existing.canBeReplaced()
        *//*?} else {*/
        if (!existing.isAir() && !existing.isReplaceable()
        /*?}*/
                && existing.getBlock() != desired.getBlock()) {
            /*? if >=26.1 {*//*
            BlockPos immutable = target.immutable();
            *//*?} else {*/
            BlockPos immutable = target.toImmutable();
            /*?}*/
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

        /*? if >=26.1 {*//*
        if (!world.isUnobstructed(desired, target,
                net.minecraft.world.phys.shapes.CollisionContext.empty())) {
        *//*?} else {*/
        if (!world.canPlace(desired, target,
                net.minecraft.block.ShapeContext.absent())) {
        /*?}*/
            return false;
        }
        {
            /*? if >=26.1 {*//*
            net.minecraft.world.phys.AABB placeBox =
            *//*?} else {*/
            net.minecraft.util.math.Box placeBox =
            /*?}*/
                    /*? if >=26.1 {*//*
                    net.minecraft.world.phys.AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(target));
                    *//*?} else {*/
                    net.minecraft.util.math.Box.from(Vec3d.ofCenter(target));
                    /*?}*/
            /*? if >=26.1 {*//*
            List<net.minecraft.world.entity.Entity> entities =
            *//*?} else {*/
            List<net.minecraft.entity.Entity> entities =
            /*?}*/
                    /*? if >=26.1 {*//*
                    world.getEntities((net.minecraft.world.entity.Entity) null, placeBox, e -> true);
                    *//*?} else {*/
                    world.getOtherEntities(null, placeBox);
                    /*?}*/
            /*? if >=26.1 {*//*
            for (net.minecraft.world.entity.Entity entity : entities) {
            *//*?} else {*/
            for (net.minecraft.entity.Entity entity : entities) {
            /*?}*/
                if (!entity.isSpectator() && entity.isAlive()) {
                    return false;
                }
            }
        }

        Direction face = findOrientedPlacementFace(world, target, desired);

        if (face == null) return false;

        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        float desiredYaw;
        float desiredPitch;
        boolean needsSneak = false;

        {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(face);
            *//*?} else {*/
            BlockPos neighbor = target.offset(face);
            /*?}*/
            Block neighborBlock = world.getBlockState(neighbor).getBlock();
            needsSneak = isInteractive(neighborBlock);

            Direction clickSide = face.getOpposite();
            /*? if >=26.1 {*//*
            Vec3 hitPos = computeRayFaceHit(eyePos, player.getYRot(), player.getXRot(),
            *//*?} else {*/
            Vec3d hitPos = computeRayFaceHit(eyePos, player.getYaw(), player.getPitch(),
            /*?}*/
                                              neighbor, clickSide, world);
            hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, desired);
            hitPos = adjustHitForDoorHinge(hitPos, neighbor, desired);

            /*? if >=26.1 {*//*
            Vec3 toHit = hitPos.subtract(eyePos);
            *//*?} else {*/
            Vec3d toHit = hitPos.subtract(eyePos);
            /*?}*/
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            /*? if >=26.1 {*//*
            desiredYaw = (float) (Mth.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            *//*?} else {*/
            desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            /*?}*/
            /*? if >=26.1 {*//*
            desiredPitch = (float) -(Mth.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
            *//*?} else {*/
            desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
            /*?}*/

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

        /*? if >=26.1 {*//*
        pendingTarget = target.immutable();
        *//*?} else {*/
        pendingTarget = target.toImmutable();
        /*?}*/
        pendingDesired = desired;
        pendingFace = face;
        pendingAirPlace = false;
        pendingNeedsSneak = needsSneak;
        pendingItem = requiredItem;
        /*? if >=26.1 {*//*
        targetYaw = snapToMouseGCD(desiredYaw, player.getYRot());
        *//*?} else {*/
        targetYaw = snapToMouseGCD(desiredYaw, player.getYaw());
        /*?}*/
        /*? if >=26.1 {*//*
        targetPitch = Mth.clamp(
        *//*?} else {*/
        targetPitch = MathHelper.clamp(
        /*?}*/
                /*? if >=26.1 {*//*
                snapToMouseGCD(desiredPitch, player.getXRot()),
                *//*?} else {*/
                snapToMouseGCD(desiredPitch, player.getPitch()),
                /*?}*/
                -90.0f, 90.0f);
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
        rotateTicks = 0;
        phase = PlacePhase.ROTATING;

        return true;
    }

    public static boolean placeLiquid(BlockPos target, BlockState desired, boolean allowSwap) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) return false;
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        /*?}*/
        if (phase != PlacePhase.IDLE) return false;

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        Item bucketItem;
        Block block = desired.getBlock();
        if (block == Blocks.WATER) bucketItem = Items.WATER_BUCKET;
        else if (block == Blocks.LAVA) bucketItem = Items.LAVA_BUCKET;
        else return false;

        BlockState currentState = world.getBlockState(target);
        /*? if >=26.1 {*//*
        if (!currentState.isAir() && !currentState.canBeReplaced()) {
        *//*?} else {*/
        if (!currentState.isAir() && !currentState.isReplaceable()) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (currentState.getBlock() instanceof LiquidBlock) {
            *//*?} else {*/
            if (currentState.getBlock() instanceof FluidBlock) {
            /*?}*/
                /*? if >=26.1 {*//*
                if (currentState.getFluidState().isSource()) {
                *//*?} else {*/
                if (currentState.getFluidState().isStill()) {
                /*?}*/
                    return false;
                }
            } else {
                return false;
            }
        }

        // Eye must be at or above target for correct ray cast
        /*? if >=26.1 {*//*
        if (player.getEyePosition().y < target.getY()) {
        *//*?} else {*/
        if (player.getEyePos().y < target.getY()) {
        /*?}*/
            return false;
        }

        if (!selectItem(player, mc, bucketItem, allowSwap)) return false;

        Direction face = findPlacementFace(world, target);
        if (face == null) return false;

        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        float desiredYaw;
        float desiredPitch;
        boolean needsSneak = false;

        {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(face);
            *//*?} else {*/
            BlockPos neighbor = target.offset(face);
            /*?}*/
            Block neighborBlock = world.getBlockState(neighbor).getBlock();
            needsSneak = isInteractive(neighborBlock);

            Direction clickSide = face.getOpposite();
            /*? if >=26.1 {*//*
            Vec3 faceCenter = Vec3.atCenterOf(neighbor)
            *//*?} else {*/
            Vec3d faceCenter = Vec3d.ofCenter(neighbor)
            /*?}*/
                    /*? if >=26.1 {*//*
                    .add(Vec3.atLowerCornerOf(clickSide.getUnitVec3i()).scale(0.5));
                    *//*?} else {*/
                    .add(Vec3d.of(clickSide.getVector()).multiply(0.5));
                    /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toHit = faceCenter.subtract(eyePos);
            *//*?} else {*/
            Vec3d toHit = faceCenter.subtract(eyePos);
            /*?}*/
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            /*? if >=26.1 {*//*
            desiredYaw = (float) (Mth.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            *//*?} else {*/
            desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            /*?}*/
            /*? if >=26.1 {*//*
            desiredPitch = (float) -(Mth.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
            *//*?} else {*/
            desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
            /*?}*/
        }

        /*? if >=26.1 {*//*
        pendingTarget = target.immutable();
        *//*?} else {*/
        pendingTarget = target.toImmutable();
        /*?}*/
        pendingDesired = desired;
        pendingFace = face;
        pendingAirPlace = false;  // liquids never air-place
        pendingNeedsSneak = needsSneak;
        pendingItem = bucketItem;
        /*? if >=26.1 {*//*
        targetYaw = snapToMouseGCD(desiredYaw, player.getYRot());
        *//*?} else {*/
        targetYaw = snapToMouseGCD(desiredYaw, player.getYaw());
        /*?}*/
        /*? if >=26.1 {*//*
        targetPitch = Mth.clamp(
        *//*?} else {*/
        targetPitch = MathHelper.clamp(
        /*?}*/
                /*? if >=26.1 {*//*
                snapToMouseGCD(desiredPitch, player.getXRot()),
                *//*?} else {*/
                snapToMouseGCD(desiredPitch, player.getPitch()),
                /*?}*/
                -90.0f, 90.0f);
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
        rotateTicks = 0;
        phase = PlacePhase.ROTATING;

        return true;
    }


    private record BatchEntry(BlockPos target, BlockState desired,
                              BlockHitResult hitResult) {}


    public static int placeBatch(List<BlockPos> targets, List<BlockState> states,
                                 boolean allowSwap) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null || mc.gameMode == null)
        *//*?} else {*/
        if (mc.player == null || mc.world == null || mc.interactionManager == null)
        /*?}*/
            return 0;
        if (phase != PlacePhase.IDLE) return 0;
        if (targets.isEmpty()) return 0;

            long now = System.currentTimeMillis();
        if (now - lastBatchMs < BATCH_COOLDOWN_MS) return 0;

        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        double reachSq = 4.5 * 4.5;

        Item requiredItem = states.get(0).getBlock().asItem();
        if (requiredItem == Items.AIR) return 0;
        if (!selectItem(player, mc, requiredItem, allowSwap)) return 0;

        List<BatchEntry> entries = new ArrayList<>(BATCH_MAX);
        boolean needsSneak = false;
        /*? if >=26.1 {*//*
        float batchYaw = player.getYRot();
        *//*?} else {*/
        float batchYaw = player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        float batchPitch = player.getXRot();
        *//*?} else {*/
        float batchPitch = player.getPitch();
        /*?}*/

        for (int i = 0; i < targets.size() && entries.size() < BATCH_MAX; i++) {
            BlockPos target = targets.get(i);
            BlockState desired = states.get(i);

            if (desired.getBlock().asItem() != requiredItem) continue;
            BlockState currentState = world.getBlockState(target);
            /*? if >=26.1 {*//*
            if (!currentState.isAir() && !currentState.canBeReplaced()) continue;
            *//*?} else {*/
            if (!currentState.isAir() && !currentState.isReplaceable()) continue;
            /*?}*/

            /*? if >=26.1 {*//*
            if (!world.isUnobstructed(desired, target,
                    net.minecraft.world.phys.shapes.CollisionContext.empty())) continue;
            *//*?} else {*/
            if (!world.canPlace(desired, target,
                    net.minecraft.block.ShapeContext.absent())) continue;
            /*?}*/
            {
                /*? if >=26.1 {*//*
                net.minecraft.world.phys.AABB placeBox =
                *//*?} else {*/
                net.minecraft.util.math.Box placeBox =
                /*?}*/
                        /*? if >=26.1 {*//*
                        net.minecraft.world.phys.AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(target));
                        *//*?} else {*/
                        net.minecraft.util.math.Box.from(Vec3d.ofCenter(target));
                        /*?}*/
                boolean blocked = false;
                /*? if >=26.1 {*//*
                for (net.minecraft.world.entity.Entity e : world.getEntities((net.minecraft.world.entity.Entity) null, placeBox, e -> true)) {
                *//*?} else {*/
                for (net.minecraft.entity.Entity e : world.getOtherEntities(null, placeBox)) {
                /*?}*/
                    if (!e.isSpectator() && e.isAlive()) { blocked = true; break; }
                }
                if (blocked) continue;
            }

            Direction face = findPlacementFace(world, target);
            BlockHitResult hitResult;

            if (face != null) {
                /*? if >=26.1 {*//*
                BlockPos neighbor = target.relative(face);
                *//*?} else {*/
                BlockPos neighbor = target.offset(face);
                /*?}*/
                Direction clickSide = face.getOpposite();
                /*? if >=26.1 {*//*
                Vec3 hitPos = Vec3.atCenterOf(neighbor)
                *//*?} else {*/
                Vec3d hitPos = Vec3d.ofCenter(neighbor)
                /*?}*/
                        /*? if >=26.1 {*//*
                        .add(clickSide.getStepX() * 0.5,
                        *//*?} else {*/
                        .add(clickSide.getOffsetX() * 0.5,
                        /*?}*/
                             /*? if >=26.1 {*//*
                             clickSide.getStepY() * 0.5,
                             *//*?} else {*/
                             clickSide.getOffsetY() * 0.5,
                             /*?}*/
                             /*? if >=26.1 {*//*
                             clickSide.getStepZ() * 0.5);
                             *//*?} else {*/
                             clickSide.getOffsetZ() * 0.5);
                             /*?}*/
                hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, desired);
                hitPos = adjustHitForDoorHinge(hitPos, neighbor, desired);
                hitResult = new BlockHitResult(hitPos, clickSide, neighbor, false);

                if (isInteractive(world.getBlockState(neighbor).getBlock())) {
                    needsSneak = true;
                }
            } else {
                Direction airFace = getFaceTowardPlayer(player, target);
                /*? if >=26.1 {*//*
                Vec3 hitPos = Vec3.atCenterOf(target);
                *//*?} else {*/
                Vec3d hitPos = Vec3d.ofCenter(target);
                /*?}*/
                hitPos = adjustHitForAirPlace(hitPos, target, desired);
                hitResult = new BlockHitResult(hitPos, airFace, target, false);
            }

            /*? if >=26.1 {*//*
            if (eyePos.distanceToSqr(hitResult.getLocation()) > reachSq) continue;
            *//*?} else {*/
            if (eyePos.squaredDistanceTo(hitResult.getPos()) > reachSq) continue;
            /*?}*/

            if (entries.isEmpty()) {
                /*? if >=26.1 {*//*
                Vec3 toHit = hitResult.getLocation().subtract(eyePos);
                *//*?} else {*/
                Vec3d toHit = hitResult.getPos().subtract(eyePos);
                /*?}*/
                double hDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
                /*? if >=26.1 {*//*
                batchYaw = (float) (Mth.atan2(toHit.z, toHit.x)
                *//*?} else {*/
                batchYaw = (float) (MathHelper.atan2(toHit.z, toHit.x)
                /*?}*/
                        * (180.0 / Math.PI)) - 90.0f;
                /*? if >=26.1 {*//*
                batchPitch = (float) -(Mth.atan2(toHit.y, hDist)
                *//*?} else {*/
                batchPitch = (float) -(MathHelper.atan2(toHit.y, hDist)
                /*?}*/
                        * (180.0 / Math.PI));
                Float facingYaw = getRequiredYaw(desired);
                if (facingYaw != null) batchYaw = facingYaw;
                Float facingPitch = getRequiredPitch(desired);
                if (facingPitch != null) batchPitch = facingPitch;
                /*? if >=26.1 {*//*
                batchYaw = snapToMouseGCD(batchYaw, player.getYRot());
                *//*?} else {*/
                batchYaw = snapToMouseGCD(batchYaw, player.getYaw());
                /*?}*/
                /*? if >=26.1 {*//*
                batchPitch = Mth.clamp(
                *//*?} else {*/
                batchPitch = MathHelper.clamp(
                /*?}*/
                        /*? if >=26.1 {*//*
                        snapToMouseGCD(batchPitch, player.getXRot()),
                        *//*?} else {*/
                        snapToMouseGCD(batchPitch, player.getPitch()),
                        /*?}*/
                        -90.0f, 90.0f);
            }

            /*? if >=26.1 {*//*
            entries.add(new BatchEntry(target.immutable(), desired, hitResult));
            *//*?} else {*/
            entries.add(new BatchEntry(target.toImmutable(), desired, hitResult));
            /*?}*/
            /*? if >=26.1 {*//*
            world.setBlockAndUpdate(target, desired);
            *//*?} else {*/
            world.setBlockState(target, desired);
            /*?}*/
        }

        if (entries.isEmpty()) return 0;

        sendSilentLookPacket(player, batchYaw, batchPitch);
        if (needsSneak) pressSneakPacket(player);

        /*? if >=26.1 {*//*
        player.connection.send(new ServerboundPlayerActionPacket(
        *//*?} else {*/
        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
        /*?}*/
                /*? if >=26.1 {*//*
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                *//*?} else {*/
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                /*?}*/
                /*? if >=26.1 {*//*
                BlockPos.ZERO, Direction.DOWN));
                *//*?} else {*/
                BlockPos.ORIGIN, Direction.DOWN));
                /*?}*/

        /*? if >=26.1 {*//*
        int seq = player.containerMenu.getStateId();
        *//*?} else {*/
        int seq = player.currentScreenHandler.getRevision();
        /*?}*/
        for (BatchEntry entry : entries) {
            /*? if >=26.1 {*//*
            player.connection.send(new ServerboundUseItemOnPacket(
            *//*?} else {*/
            player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            /*?}*/
                    /*? if >=26.1 {*//*
                    InteractionHand.OFF_HAND, entry.hitResult, ++seq));
                    *//*?} else {*/
                    Hand.OFF_HAND, entry.hitResult, ++seq));
                    /*?}*/
        }

        /*? if >=26.1 {*//*
        player.connection.send(new ServerboundPlayerActionPacket(
        *//*?} else {*/
        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
        /*?}*/
                /*? if >=26.1 {*//*
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                *//*?} else {*/
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                /*?}*/
                /*? if >=26.1 {*//*
                BlockPos.ZERO, Direction.DOWN));
                *//*?} else {*/
                BlockPos.ORIGIN, Direction.DOWN));
                /*?}*/

        /*? if >=26.1 {*//*
        player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        player.swingHand(Hand.MAIN_HAND);
        /*?}*/

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

    /*? if >=26.1 {*//*
    private static Direction getFaceTowardPlayer(LocalPlayer player,
    *//*?} else {*/
    private static Direction getFaceTowardPlayer(ClientPlayerEntity player,
    /*?}*/
                                                 BlockPos pos) {
        /*? if >=26.1 {*//*
        Vec3 eye = player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 center = Vec3.atCenterOf(pos);
        *//*?} else {*/
        Vec3d center = Vec3d.ofCenter(pos);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 delta = eye.subtract(center);
        *//*?} else {*/
        Vec3d delta = eye.subtract(center);
        /*?}*/

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
                                           /*? if >=26.1 {*//*
                                           LocalPlayer player,
                                           *//*?} else {*/
                                           ClientPlayerEntity player,
                                           /*?}*/
                                           /*? if >=26.1 {*//*
                                           Minecraft mc) {
                                           *//*?} else {*/
                                           MinecraftClient mc) {
                                           /*?}*/
        /*? if >=26.1 {*//*
        correctionTarget = target.immutable();
        *//*?} else {*/
        correctionTarget = target.toImmutable();
        /*?}*/
        correctionDesired = desired;
        breakingTicks = 0;
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

        /*? if >=26.1 {*//*
        BlockState existing = mc.level.getBlockState(target);
        *//*?} else {*/
        BlockState existing = mc.world.getBlockState(target);
        /*?}*/
        selectBestTool(player, mc, existing);

        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 blockCenter = Vec3.atCenterOf(target);
        *//*?} else {*/
        Vec3d blockCenter = Vec3d.ofCenter(target);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toBlock = blockCenter.subtract(eyePos);
        *//*?} else {*/
        Vec3d toBlock = blockCenter.subtract(eyePos);
        /*?}*/
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        /*?}*/
        /*? if >=26.1 {*//*
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        /*?}*/

        sendLookPacket(player, breakYaw, breakPitch);
        /*? if >=26.1 {*//*
        mc.gameMode.startDestroyBlock(target, Direction.UP);
        *//*?} else {*/
        mc.interactionManager.attackBlock(target, Direction.UP);
        /*?}*/
        phase = PlacePhase.BREAKING;
        return true;
    }

    /** Computes a hit position on the given face via ray cast, falling back to face center. */
    /*? if >=26.1 {*//*
    private static Vec3 computeRayFaceHit(Vec3 eyePos, float yaw, float pitch,
    *//*?} else {*/
    private static Vec3d computeRayFaceHit(Vec3d eyePos, float yaw, float pitch,
    /*?}*/
                                            /*? if >=26.1 {*//*
                                            BlockPos neighbor, Direction face, Level world) {
                                            *//*?} else {*/
                                            BlockPos neighbor, Direction face, World world) {
                                            /*?}*/
        // Ray direction from yaw/pitch
        float yawRad  = (float) Math.toRadians(-yaw - 180.0f);
        float pitchRad = (float) Math.toRadians(-pitch);
        /*? if >=26.1 {*//*
        float cosP = Mth.cos(pitchRad);
        *//*?} else {*/
        float cosP = MathHelper.cos(pitchRad);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 lookDir = new Vec3(
        *//*?} else {*/
        Vec3d lookDir = new Vec3d(
        /*?}*/
                /*? if >=26.1 {*//*
                Mth.sin(yawRad) * cosP,
                *//*?} else {*/
                MathHelper.sin(yawRad) * cosP,
                /*?}*/
                /*? if >=26.1 {*//*
                Mth.sin(pitchRad),
                *//*?} else {*/
                MathHelper.sin(pitchRad),
                /*?}*/
                /*? if >=26.1 {*//*
                Mth.cos(yawRad) * cosP
                *//*?} else {*/
                MathHelper.cos(yawRad) * cosP
                /*?}*/
        );

        // Face plane: the face of the neighbor block
        // The face is on the surface of the neighbor block at `face` direction
        /*? if >=26.1 {*//*
        Vec3 faceCenter = Vec3.atCenterOf(neighbor)
        *//*?} else {*/
        Vec3d faceCenter = Vec3d.ofCenter(neighbor)
        /*?}*/
                /*? if >=26.1 {*//*
                .add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
                *//*?} else {*/
                .add(Vec3d.of(face.getVector()).multiply(0.5));
                /*?}*/

        // Normal of the face
        /*? if >=26.1 {*//*
        Vec3 faceNormal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        *//*?} else {*/
        Vec3d faceNormal = Vec3d.of(face.getVector());
        /*?}*/

        // Ray-plane intersection: t = dot(faceCenter - eyePos, normal) / dot(lookDir, normal)
        /*? if >=26.1 {*//*
        double denom = lookDir.dot(faceNormal);
        *//*?} else {*/
        double denom = lookDir.dotProduct(faceNormal);
        /*?}*/
        if (Math.abs(denom) < 1e-6) {
            // Ray is nearly parallel to face — use face center
            return faceCenter;
        }

        /*? if >=26.1 {*//*
        double t = faceCenter.subtract(eyePos).dot(faceNormal) / denom;
        *//*?} else {*/
        double t = faceCenter.subtract(eyePos).dotProduct(faceNormal) / denom;
        /*?}*/
        if (t < 0) {
            // Intersection is behind the player — use face center
            return faceCenter;
        }

        /*? if >=26.1 {*//*
        Vec3 intersection = eyePos.add(lookDir.scale(t));
        *//*?} else {*/
        Vec3d intersection = eyePos.add(lookDir.multiply(t));
        /*?}*/

        // Clamp to the face bounds (block goes from neighbor to neighbor+1)
        double hx = clampToFace(intersection.x, neighbor.getX(), face, Direction.Axis.X);
        double hy = clampToFace(intersection.y, neighbor.getY(), face, Direction.Axis.Y);
        double hz = clampToFace(intersection.z, neighbor.getZ(), face, Direction.Axis.Z);

        /*? if >=26.1 {*//*
        return new Vec3(hx, hy, hz);
        *//*?} else {*/
        return new Vec3d(hx, hy, hz);
        /*?}*/
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
            /*? if >=26.1 {*//*
            return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max : min;
            *//*?} else {*/
            return face.getDirection() == Direction.AxisDirection.POSITIVE ? max : min;
            /*?}*/
        }

        // Clamp to block bounds with small inset to avoid exact edges
        /*? if >=26.1 {*//*
        return Mth.clamp(value, min + 0.01, max - 0.01);
        *//*?} else {*/
        return MathHelper.clamp(value, min + 0.01, max - 0.01);
        /*?}*/
    }

    /** Sends a look packet without modifying client-side rotation. */
    /*? if >=26.1 {*//*
    private static void sendSilentLookPacket(LocalPlayer player, float yaw, float pitch) {
    *//*?} else {*/
    private static void sendSilentLookPacket(ClientPlayerEntity player, float yaw, float pitch) {
    /*?}*/
        /*? if >=26.1 {*//*
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
        *//*?} else {*/
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        /*?}*/
        /*? if >=26.1 {*//*
        player.connection.send(
                new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                        player.onGround(), player.horizontalCollision));
        *//*?} else if >=1.21.4 {*//*
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
    /*? if >=26.1 {*//*
    public static void sendLookPacket(LocalPlayer player, float yaw, float pitch) {
    *//*?} else {*/
    public static void sendLookPacket(ClientPlayerEntity player, float yaw, float pitch) {
    /*?}*/
        /*? if >=26.1 {*//*
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
        *//*?} else {*/
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        /*?}*/
        /*? if >=26.1 {*//*
        player.setYRot(yaw);
        *//*?} else {*/
        player.setYaw(yaw);
        /*?}*/
        /*? if >=26.1 {*//*
        player.setXRot(pitch);
        *//*?} else {*/
        player.setPitch(pitch);
        /*?}*/
        /*? if >=26.1 {*//*
        player.connection.send(
                new ServerboundMovePlayerPacket.Rot(yaw, pitch,
                        player.onGround(), player.horizontalCollision));
        *//*?} else if >=1.21.4 {*//*
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                        player.isOnGround(), player.horizontalCollision));
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                        player.isOnGround()));
        /*?}*/
    }

    /**
     * Sets yaw/pitch with jitter, relying on vanilla's end-of-tick
     * flying packet to avoid anti-cheat duplicate-packet flags.
     */
    /*? if >=26.1 {*//*
    public static void setLookRotation(LocalPlayer player, float yaw, float pitch) {
    *//*?} else {*/
    public static void setLookRotation(ClientPlayerEntity player, float yaw, float pitch) {
    /*?}*/
        float jitter = ThreadLocalRandom.current().nextFloat() * 0.02f - 0.01f;
        /*? if >=26.1 {*//*
        pitch = Mth.clamp(pitch + jitter, -90.0f, 90.0f);
        *//*?} else {*/
        pitch = MathHelper.clamp(pitch + jitter, -90.0f, 90.0f);
        /*?}*/
        yaw += jitter;
        /*? if >=26.1 {*//*
        player.setYRot(yaw);
        *//*?} else {*/
        player.setYaw(yaw);
        /*?}*/
        /*? if >=26.1 {*//*
        player.setXRot(pitch);
        *//*?} else {*/
        player.setPitch(pitch);
        /*?}*/
    }

    /** Releases sneak for interaction; returns a Runnable that restores it. */
    /*? if >=26.1 {*//*
    public static Runnable releaseForInteraction(LocalPlayer player) {
    *//*?} else {*/
    public static Runnable releaseForInteraction(ClientPlayerEntity player) {
    /*?}*/
        boolean wasAbsoluteSneak = SneakOverride.isForceAbsoluteSneak();
        boolean wasForceSneak = SneakOverride.isForceSneak();
        SneakOverride.setForceAbsoluteSneak(false);
        SneakOverride.setForceSneak(false);
        /*? if >=26.1 {*//*
        player.setShiftKeyDown(false);
        *//*?} else {*/
        player.setSneaking(false);
        /*?}*/
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

    /*? if >=26.1 {*//*
    private static void pressSneakPacket(LocalPlayer player) {
    *//*?} else {*/
    private static void pressSneakPacket(ClientPlayerEntity player) {
    /*?}*/
        /*? if >=26.1 {*//*
        player.connection.send(
                new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
        *//*?} else if >=1.21.8 {*//*
        player.networkHandler.sendPacket(
                new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, true, false)));
        *//*?} else {*/
        player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
        /*?}*/
    }

    private static void releaseSneakPacket() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return;
        /*? if >=26.1 {*//*
        mc.player.connection.send(
                new ServerboundPlayerInputPacket(Input.EMPTY));
        *//*?} else if >=1.21.8 {*//*
        mc.player.networkHandler.sendPacket(
                new PlayerInputC2SPacket(PlayerInput.DEFAULT));
        *//*?} else {*/
        mc.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        /*?}*/
    }

    /** Smoothly restores look direction after placement. */
    /*? if >=26.1 {*//*
    private static void restoreLook(LocalPlayer player) {
    *//*?} else {*/
    private static void restoreLook(ClientPlayerEntity player) {
    /*?}*/
        /*? if >=26.1 {*//*
        float currentYaw = player.getYRot();
        *//*?} else {*/
        float currentYaw = player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        float currentPitch = player.getXRot();
        *//*?} else {*/
        float currentPitch = player.getPitch();
        /*?}*/

        /*? if >=26.1 {*//*
        float yawDiff = Mth.wrapDegrees(savedYaw - currentYaw);
        *//*?} else {*/
        float yawDiff = MathHelper.wrapDegrees(savedYaw - currentYaw);
        /*?}*/
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
    /*? if >=26.1 {*//*
    public static boolean selectItem(LocalPlayer player, Minecraft mc,
    *//*?} else {*/
    public static boolean selectItem(ClientPlayerEntity player, MinecraftClient mc,
    /*?}*/
                                     Item item, boolean allowSwap) {
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/

        // check current slot first
        /*? if >=26.1 {*//*
        if (inv.getItem(inv.getSelectedSlot()).getItem() == item) return true;
        *//*?} else if >=1.21.5 {*//*
        if (inv.getStack(inv.getSelectedSlot()).getItem() == item) return true;
        *//*?} else {*/
        if (inv.getStack(inv.selectedSlot).getItem() == item) return true;
        /*?}*/

        // check rest of hotbar
        for (int i = 0; i < 9; i++) {
            /*? if >=26.1 {*//*
            if (inv.getItem(i).getItem() == item) {
            *//*?} else {*/
            if (inv.getStack(i).getItem() == item) {
            /*?}*/
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
                /*? if >=26.1 {*//*
                if (inv.getItem(i).getItem() == item) {
                *//*?} else {*/
                if (inv.getStack(i).getItem() == item) {
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
                            i,
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
                    return true;
                }
            }
        }
        return false;
    }

    /** Selects the best tool for breaking the given block. */
    /*? if >=26.1 {*//*
    public static void selectBestTool(LocalPlayer player, Minecraft mc,
    *//*?} else {*/
    public static void selectBestTool(ClientPlayerEntity player, MinecraftClient mc,
    /*?}*/
                                       BlockState state) {
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/
        float bestSpeed = 1.0f; // bare-hand baseline
        int   bestSlot  = -1;

        // Scan entire inventory (0-8 hotbar, 9-35 main)
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            /*? if >=26.1 {*//*
            float speed = stack.getDestroySpeed(state);
            *//*?} else {*/
            float speed = stack.getMiningSpeedMultiplier(state);
            /*?}*/
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
                    bestSlot,
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
        }
    }

    /** Snapshot of all items in the player's inventory (Item → total count). */
    public static Map<Item, Integer> getInventoryContents() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return Map.of();
        /*? if >=26.1 {*//*
        Inventory inv = mc.player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = mc.player.getInventory();
        /*?}*/
        Map<Item, Integer> contents = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return contents;
    }

    /** Returns the yaw needed to produce the desired FACING, or null. */
    private static Float getRequiredYaw(BlockState desired) {
        Block block = desired.getBlock();

        // Stairs — FACING is set to the player's look direction (NOT opposite)
        /*? if >=26.1 {*//*
        if (block instanceof StairBlock) {
        *//*?} else {*/
        if (block instanceof StairsBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
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
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing.getOpposite());
            }
        }

        // Blocks that use FACING (all 6 directions) — dispenser, observer, piston
        if (block instanceof DispenserBlock
                || block instanceof ObserverBlock
                /*? if >=26.1 {*//*
                || block instanceof PistonBaseBlock) {
                *//*?} else {*/
                || block instanceof PistonBlock) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
                // UP/DOWN handled by pitch override
            }
        }

        // Anvil — uses HORIZONTAL_FACING
        if (block instanceof AnvilBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                /*? if >=26.1 {*//*
                return directionToYaw(facing.getClockWise()); // perpendicular
                *//*?} else {*/
                return directionToYaw(facing.rotateYClockwise()); // perpendicular
                /*?}*/
            }
        }

        // Chests, trapped chests, ender chests — HORIZONTAL_FACING
        if (block instanceof AbstractChestBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing.getOpposite());
            }
        }

        // Barrels — FACING (all 6 directions)
        if (block instanceof BarrelBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Shulker boxes — FACING (all 6 directions)
        if (block instanceof ShulkerBoxBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Hoppers — yaw not used (facing from clicked face)

        // End rods, lightning rods
        if (block instanceof EndRodBlock || block instanceof LightningRodBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
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
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.ROTATION_16)) {
            *//*?} else {*/
            if (desired.contains(Properties.ROTATION)) {
            /*?}*/
                /*? if >=26.1 {*//*
                int rotation = desired.getValue(BlockStateProperties.ROTATION_16);
                *//*?} else {*/
                int rotation = desired.get(Properties.ROTATION);
                /*?}*/
                return rotation * 22.5f - 180.0f;
            }
        }

        // Trapdoors, fence gates — face opposite to the player
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock
        *//*?} else {*/
        if (block instanceof TrapdoorBlock
        /*?}*/
                || block instanceof FenceGateBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing.getOpposite());
            }
        }

        // Doors — facing = player look (not opposite)
        if (block instanceof DoorBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing);
            }
        }

        // Beds — facing = foot-to-head direction
        if (block instanceof BedBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return directionToYaw(facing);
            }
        }

        // Catch-all for other HORIZONTAL_FACING blocks
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
        *//*?} else {*/
        if (desired.contains(Properties.HORIZONTAL_FACING)) {
        /*?}*/
            if (block instanceof CampfireBlock
                    || block instanceof BeehiveBlock
                    || block instanceof LecternBlock
                    || block instanceof GrindstoneBlock
                    || block instanceof BellBlock
                    || block instanceof RespawnAnchorBlock) {
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
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
                /*? if >=26.1 {*//*
                || block instanceof PistonBaseBlock) {
                *//*?} else {*/
                || block instanceof PistonBlock) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing == Direction.UP)   return -90.0f; // look straight up
                if (facing == Direction.DOWN) return 90.0f;  // look straight down
            }
        }

        // Barrels — FACING (all 6 directions)
        if (block instanceof BarrelBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        // Shulker boxes — FACING (all 6 directions)
        if (block instanceof ShulkerBoxBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        // End rods, lightning rods — FACING (all 6)
        if (block instanceof EndRodBlock || block instanceof LightningRodBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.FACING);
                /*?}*/
                if (facing == Direction.UP)   return -90.0f;
                if (facing == Direction.DOWN) return 90.0f;
            }
        }

        return null;
    }

    /** Adjusts hit Y for correct slab/stair/trapdoor half placement. */
    /*? if >=26.1 {*//*
    private static Vec3 adjustHitForHalf(Vec3 hitPos, BlockPos neighbor,
    *//*?} else {*/
    private static Vec3d adjustHitForHalf(Vec3d hitPos, BlockPos neighbor,
    /*?}*/
                                          Direction clickSide, BlockState desired) {
        Block block = desired.getBlock();

        // Stairs
        /*? if >=26.1 {*//*
        if (block instanceof StairBlock && desired.hasProperty(BlockStateProperties.HALF)) {
        *//*?} else {*/
        if (block instanceof StairsBlock && desired.contains(Properties.BLOCK_HALF)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Half half = desired.getValue(BlockStateProperties.HALF);
            *//*?} else {*/
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            /*?}*/
            if (clickSide == Direction.UP) {
                // TOP stair on top face → force upper hit
                /*? if >=26.1 {*//*
                if (half == Half.TOP) {
                *//*?} else {*/
                if (half == BlockHalf.TOP) {
                /*?}*/
                    return forceHitY(hitPos, neighbor, true);
                }
            } else if (clickSide == Direction.DOWN) {
                /*? if >=26.1 {*//*
                if (half == Half.BOTTOM) {
                *//*?} else {*/
                if (half == BlockHalf.BOTTOM) {
                /*?}*/
                    return forceHitY(hitPos, neighbor, false);
                }
            } else {
                // Side face — control via Y position
                /*? if >=26.1 {*//*
                return forceHitY(hitPos, neighbor, half == Half.TOP);
                *//*?} else {*/
                return forceHitY(hitPos, neighbor, half == BlockHalf.TOP);
                /*?}*/
            }
        }

        // Slabs
        /*? if >=26.1 {*//*
        if (block instanceof SlabBlock && desired.hasProperty(BlockStateProperties.SLAB_TYPE)) {
        *//*?} else {*/
        if (block instanceof SlabBlock && desired.contains(Properties.SLAB_TYPE)) {
        /*?}*/
            /*? if >=26.1 {*//*
            SlabType type = desired.getValue(BlockStateProperties.SLAB_TYPE);
            *//*?} else {*/
            SlabType type = desired.get(Properties.SLAB_TYPE);
            /*?}*/
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
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock && desired.hasProperty(BlockStateProperties.HALF)) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock && desired.contains(Properties.BLOCK_HALF)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Half half = desired.getValue(BlockStateProperties.HALF);
            *//*?} else {*/
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            /*?}*/
            if (clickSide == Direction.UP) {
                // TOP trapdoor on top face → force upper hit
                /*? if >=26.1 {*//*
                if (half == Half.TOP) {
                *//*?} else {*/
                if (half == BlockHalf.TOP) {
                /*?}*/
                    return forceHitY(hitPos, neighbor, true);
                }
            } else if (clickSide == Direction.DOWN) {
                // BOTTOM trapdoor on bottom face → force lower hit
                /*? if >=26.1 {*//*
                if (half == Half.BOTTOM) {
                *//*?} else {*/
                if (half == BlockHalf.BOTTOM) {
                /*?}*/
                    return forceHitY(hitPos, neighbor, false);
                }
            } else {
                /*? if >=26.1 {*//*
                return forceHitY(hitPos, neighbor, half == Half.TOP);
                *//*?} else {*/
                return forceHitY(hitPos, neighbor, half == BlockHalf.TOP);
                /*?}*/
            }
        }

        return hitPos;
    }

    /** Forces hit Y to upper or lower quarter of the block face. */
    /*? if >=26.1 {*//*
    private static Vec3 forceHitY(Vec3 hitPos, BlockPos neighbor, boolean upper) {
    *//*?} else {*/
    private static Vec3d forceHitY(Vec3d hitPos, BlockPos neighbor, boolean upper) {
    /*?}*/
        double y = upper
                ? neighbor.getY() + 0.75   // upper quarter of the block
                : neighbor.getY() + 0.25;  // lower quarter of the block
        /*? if >=26.1 {*//*
        return new Vec3(hitPos.x, y, hitPos.z);
        *//*?} else {*/
        return new Vec3d(hitPos.x, y, hitPos.z);
        /*?}*/
    }

    /** Adjusts hit X/Z to influence door hinge side. */
    /*? if >=26.1 {*//*
    private static Vec3 adjustHitForDoorHinge(Vec3 hitPos, BlockPos neighbor,
    *//*?} else {*/
    private static Vec3d adjustHitForDoorHinge(Vec3d hitPos, BlockPos neighbor,
    /*?}*/
                                                BlockState desired) {
        if (!(desired.getBlock() instanceof DoorBlock)) return hitPos;
        /*? if >=26.1 {*//*
        if (!desired.hasProperty(BlockStateProperties.DOOR_HINGE)
        *//*?} else {*/
        if (!desired.contains(Properties.DOOR_HINGE)
        /*?}*/
                /*? if >=26.1 {*//*
                || !desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return hitPos;
                *//*?} else {*/
                || !desired.contains(Properties.HORIZONTAL_FACING)) return hitPos;
                /*?}*/

        /*? if >=26.1 {*//*
        DoorHingeSide hinge = desired.getValue(BlockStateProperties.DOOR_HINGE);
        *//*?} else {*/
        DoorHinge hinge = desired.get(Properties.DOOR_HINGE);
        /*?}*/
        /*? if >=26.1 {*//*
        Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
        *//*?} else {*/
        Direction facing = desired.get(Properties.HORIZONTAL_FACING);
        /*?}*/
        /*? if >=26.1 {*//*
        boolean wantLeft = (hinge == DoorHingeSide.LEFT);
        *//*?} else {*/
        boolean wantLeft = (hinge == DoorHinge.LEFT);
        /*?}*/

        double bx = neighbor.getX();
        double bz = neighbor.getZ();

        switch (facing) {
            case NORTH -> {
                // LEFT → hitX ≤ 0.5 → use 0.25, RIGHT → hitX > 0.5 → use 0.75
                double x = wantLeft ? bx + 0.25 : bx + 0.75;
                /*? if >=26.1 {*//*
                return new Vec3(x, hitPos.y, hitPos.z);
                *//*?} else {*/
                return new Vec3d(x, hitPos.y, hitPos.z);
                /*?}*/
            }
            case SOUTH -> {
                // LEFT → hitX ≥ 0.5 → use 0.75, RIGHT → hitX < 0.5 → use 0.25
                double x = wantLeft ? bx + 0.75 : bx + 0.25;
                /*? if >=26.1 {*//*
                return new Vec3(x, hitPos.y, hitPos.z);
                *//*?} else {*/
                return new Vec3d(x, hitPos.y, hitPos.z);
                /*?}*/
            }
            case EAST -> {
                // LEFT → hitZ ≤ 0.5 → use 0.25, RIGHT → hitZ > 0.5 → use 0.75
                double z = wantLeft ? bz + 0.25 : bz + 0.75;
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, hitPos.y, z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, hitPos.y, z);
                /*?}*/
            }
            case WEST -> {
                // LEFT → hitZ ≥ 0.5 → use 0.75, RIGHT → hitZ < 0.5 → use 0.25
                double z = wantLeft ? bz + 0.75 : bz + 0.25;
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, hitPos.y, z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, hitPos.y, z);
                /*?}*/
            }
            default -> { return hitPos; }
        }
    }

    /** Adjusts hit Y for air-placed half-blocks (TOP → Y+0.75). */
    /*? if >=26.1 {*//*
    private static Vec3 adjustHitForAirPlace(Vec3 hitPos, BlockPos target,
    *//*?} else {*/
    private static Vec3d adjustHitForAirPlace(Vec3d hitPos, BlockPos target,
    /*?}*/
                                              BlockState desired) {
        Block block = desired.getBlock();

        /*? if >=26.1 {*//*
        if (block instanceof StairBlock && desired.hasProperty(BlockStateProperties.HALF)) {
        *//*?} else {*/
        if (block instanceof StairsBlock && desired.contains(Properties.BLOCK_HALF)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Half half = desired.getValue(BlockStateProperties.HALF);
            *//*?} else {*/
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            /*?}*/
            /*? if >=26.1 {*//*
            if (half == Half.TOP) {
            *//*?} else {*/
            if (half == BlockHalf.TOP) {
            /*?}*/
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, target.getY() + 0.75, hitPos.z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
                /*?}*/
            }
        }

        /*? if >=26.1 {*//*
        if (block instanceof SlabBlock && desired.hasProperty(BlockStateProperties.SLAB_TYPE)) {
        *//*?} else {*/
        if (block instanceof SlabBlock && desired.contains(Properties.SLAB_TYPE)) {
        /*?}*/
            /*? if >=26.1 {*//*
            SlabType type = desired.getValue(BlockStateProperties.SLAB_TYPE);
            *//*?} else {*/
            SlabType type = desired.get(Properties.SLAB_TYPE);
            /*?}*/
            if (type == SlabType.TOP) {
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, target.getY() + 0.75, hitPos.z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
                /*?}*/
            }
        }

        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock && desired.hasProperty(BlockStateProperties.HALF)) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock && desired.contains(Properties.BLOCK_HALF)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Half half = desired.getValue(BlockStateProperties.HALF);
            *//*?} else {*/
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            /*?}*/
            /*? if >=26.1 {*//*
            if (half == Half.TOP) {
            *//*?} else {*/
            if (half == BlockHalf.TOP) {
            /*?}*/
                /*? if >=26.1 {*//*
                return new Vec3(hitPos.x, target.getY() + 0.75, hitPos.z);
                *//*?} else {*/
                return new Vec3d(hitPos.x, target.getY() + 0.75, hitPos.z);
                /*?}*/
            }
        }

        return hitPos;
    }

    /** Finds an adjacent face, with orientation awareness for wall/floor/ceiling blocks. */
    /*? if >=26.1 {*//*
    private static Direction findOrientedPlacementFace(Level world, BlockPos target,
    *//*?} else {*/
    private static Direction findOrientedPlacementFace(World world, BlockPos target,
    /*?}*/
                                                       BlockState desired) {
        Block block = desired.getBlock();

        // Wall-mounted torches
        if (block instanceof WallTorchBlock
                /*? if >=26.1 {*//*
                || block instanceof RedstoneWallTorchBlock) {
                *//*?} else {*/
                || block instanceof WallRedstoneTorchBlock) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                Direction supportDir = facing.getOpposite();
                return requireSolidFace(world, target, supportDir);
            }
        }

        // Standing torches
        if (block instanceof TorchBlock && !(block instanceof WallTorchBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }
        /*? if >=26.1 {*//*
        if (block instanceof RedstoneTorchBlock && !(block instanceof RedstoneWallTorchBlock)) {
        *//*?} else {*/
        if (block instanceof RedstoneTorchBlock && !(block instanceof WallRedstoneTorchBlock)) {
        /*?}*/
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Wall signs
        if (block instanceof WallSignBlock || block instanceof WallHangingSignBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
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
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // Ladders
        if (block instanceof LadderBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // Lanterns
        if (block instanceof LanternBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HANGING) && desired.getValue(BlockStateProperties.HANGING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HANGING) && desired.get(Properties.HANGING)) {
            /*?}*/
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
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                /*?}*/
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }
        if (block instanceof SkullBlock && !(block instanceof WallSkullBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Trapdoors — BLOCK_HALF determines attachment direction
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.HALF)) {
            *//*?} else {*/
            if (desired.contains(Properties.BLOCK_HALF)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Half half = desired.getValue(BlockStateProperties.HALF);
                *//*?} else {*/
                BlockHalf half = desired.get(Properties.BLOCK_HALF);
                /*?}*/
                /*? if >=26.1 {*//*
                if (half == Half.TOP) {
                *//*?} else {*/
                if (half == BlockHalf.TOP) {
                /*?}*/
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
        /*? if >=26.1 {*//*
        if (block instanceof DoublePlantBlock) {
        *//*?} else {*/
        if (block instanceof TallPlantBlock) {
        /*?}*/
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Hoppers — facing from clicked face
        if (block instanceof HopperBlock) {
            /*? if >=26.1 {*//*
            if (desired.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            *//*?} else {*/
            if (desired.contains(Properties.HOPPER_FACING)) {
            /*?}*/
                /*? if >=26.1 {*//*
                Direction facing = desired.getValue(BlockStateProperties.FACING_HOPPER);
                *//*?} else {*/
                Direction facing = desired.get(Properties.HOPPER_FACING);
                /*?}*/
                if (facing == Direction.DOWN) {
                    return findPlacementFace(world, target);
                } else {
                    // Click the face in the output direction
                    return requireSolidFace(world, target, facing);
                }
            }
        }

        // Stairs/Slabs/Trapdoors — prefer side faces for half control
        /*? if >=26.1 {*//*
        if (block instanceof StairBlock
        *//*?} else {*/
        if (block instanceof StairsBlock
        /*?}*/
                || block instanceof SlabBlock
                /*? if >=26.1 {*//*
                || block instanceof TrapDoorBlock) {
                *//*?} else {*/
                || block instanceof TrapdoorBlock) {
                /*?}*/
            Direction sideFace = findSidePlacementFace(world, target);
            if (sideFace != null) return sideFace;
            // Fall through to generic if no side face available
            return findPlacementFace(world, target);
        }

        // Pillar blocks — click face along desired axis
        /*? if >=26.1 {*//*
        if (block instanceof RotatedPillarBlock && desired.hasProperty(BlockStateProperties.AXIS)) {
        *//*?} else {*/
        if (block instanceof PillarBlock && desired.contains(Properties.AXIS)) {
        /*?}*/
            /*? if >=26.1 {*//*
            Direction.Axis desiredAxis = desired.getValue(BlockStateProperties.AXIS);
            *//*?} else {*/
            Direction.Axis desiredAxis = desired.get(Properties.AXIS);
            /*?}*/
            Direction preferred = preferFaceForAxis(world, target, desiredAxis);
            if (preferred != null) return preferred;
            // No matching face — air placement fallback
            return null;
        }

        return findPlacementFace(world, target);
    }

    /** Returns dir if there's a solid support block in that direction, else null. */
    /*? if >=26.1 {*//*
    private static Direction requireSolidFace(Level world, BlockPos target, Direction dir) {
    *//*?} else {*/
    private static Direction requireSolidFace(World world, BlockPos target, Direction dir) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos neighbor = target.relative(dir);
        *//*?} else {*/
        BlockPos neighbor = target.offset(dir);
        /*?}*/
        BlockState state = world.getBlockState(neighbor);
        /*? if >=26.1 {*//*
        if (!state.canBeReplaced()
        *//*?} else {*/
        if (!state.isReplaceable()
        /*?}*/
                /*? if >=26.1 {*//*
                && state.getShape(world, neighbor) != Shapes.empty()) {
                *//*?} else {*/
                && state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                /*?}*/
            return dir;
        }
        return null; // required support block not present
    }

    /** Resolves placement face for FLOOR/WALL/CEILING blocks (buttons, levers). */
    /*? if >=26.1 {*//*
    private static Direction resolveWallMountedFace(Level world, BlockPos target,
    *//*?} else {*/
    private static Direction resolveWallMountedFace(World world, BlockPos target,
    /*?}*/
                                                     BlockState desired) {
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.ATTACH_FACE)) {
        *//*?} else {*/
        if (desired.contains(Properties.BLOCK_FACE)) {
        /*?}*/
            /*? if >=26.1 {*//*
            AttachFace face = desired.getValue(BlockStateProperties.ATTACH_FACE);
            *//*?} else {*/
            BlockFace face = desired.get(Properties.BLOCK_FACE);
            /*?}*/
            return switch (face) {
                case FLOOR   -> requireSolidFace(world, target, Direction.DOWN);
                case CEILING -> requireSolidFace(world, target, Direction.UP);
                case WALL    -> {
                    /*? if >=26.1 {*//*
                    if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    *//*?} else {*/
                    if (desired.contains(Properties.HORIZONTAL_FACING)) {
                    /*?}*/
                        /*? if >=26.1 {*//*
                        Direction facing = desired.getValue(BlockStateProperties.HORIZONTAL_FACING);
                        *//*?} else {*/
                        Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                        /*?}*/
                        yield requireSolidFace(world, target, facing.getOpposite());
                    }
                    yield findPlacementFace(world, target);
                }
            };
        }
        return findPlacementFace(world, target);
    }

    /** Finds a face along the desired axis for pillar block placement. */
    /*? if >=26.1 {*//*
    private static Direction preferFaceForAxis(Level world, BlockPos target,
    *//*?} else {*/
    private static Direction preferFaceForAxis(World world, BlockPos target,
    /*?}*/
                                               Direction.Axis desiredAxis) {
        // Faces whose normal matches the axis
        Direction[] axisDirections = switch (desiredAxis) {
            case X -> new Direction[]{ Direction.EAST, Direction.WEST };
            case Y -> new Direction[]{ Direction.UP, Direction.DOWN };
            case Z -> new Direction[]{ Direction.NORTH, Direction.SOUTH };
        };

        for (Direction dir : axisDirections) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = target.offset(dir);
            /*?}*/
            BlockState state = world.getBlockState(neighbor);
            /*? if >=26.1 {*//*
            if (!state.canBeReplaced()
            *//*?} else {*/
            if (!state.isReplaceable()
            /*?}*/
                    /*? if >=26.1 {*//*
                    && state.getShape(world, neighbor) != Shapes.empty()) {
                    *//*?} else {*/
                    && state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                    /*?}*/
                return dir;
            }
        }
        return null; // no preferred face available — caller falls back to default
    }

    /** Checks if two states of the same block differ in orientation properties. */
    public static boolean isOrientationMismatch(BlockState existing, BlockState desired) {
        if (existing.getBlock() != desired.getBlock()) return false;

        // Check all common orientation properties
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
        *//*?} else {*/
        if (desired.contains(Properties.HORIZONTAL_FACING)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                *//*?} else {*/
                && existing.contains(Properties.HORIZONTAL_FACING)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.HORIZONTAL_FACING) != desired.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            *//*?} else {*/
            if (existing.get(Properties.HORIZONTAL_FACING) != desired.get(Properties.HORIZONTAL_FACING)) {
            /*?}*/
                return true;
            }
        }
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.FACING)
        *//*?} else {*/
        if (desired.contains(Properties.FACING)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.FACING)) {
                *//*?} else {*/
                && existing.contains(Properties.FACING)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.FACING) != desired.getValue(BlockStateProperties.FACING)) {
            *//*?} else {*/
            if (existing.get(Properties.FACING) != desired.get(Properties.FACING)) {
            /*?}*/
                return true;
            }
        }
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.AXIS)
        *//*?} else {*/
        if (desired.contains(Properties.AXIS)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.AXIS)) {
                *//*?} else {*/
                && existing.contains(Properties.AXIS)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.AXIS) != desired.getValue(BlockStateProperties.AXIS)) {
            *//*?} else {*/
            if (existing.get(Properties.AXIS) != desired.get(Properties.AXIS)) {
            /*?}*/
                return true;
            }
        }
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.HALF)
        *//*?} else {*/
        if (desired.contains(Properties.BLOCK_HALF)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.HALF)) {
                *//*?} else {*/
                && existing.contains(Properties.BLOCK_HALF)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.HALF) != desired.getValue(BlockStateProperties.HALF)) {
            *//*?} else {*/
            if (existing.get(Properties.BLOCK_HALF) != desired.get(Properties.BLOCK_HALF)) {
            /*?}*/
                return true;
            }
        }
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.SLAB_TYPE)
        *//*?} else {*/
        if (desired.contains(Properties.SLAB_TYPE)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                *//*?} else {*/
                && existing.contains(Properties.SLAB_TYPE)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.SLAB_TYPE) != desired.getValue(BlockStateProperties.SLAB_TYPE)) {
            *//*?} else {*/
            if (existing.get(Properties.SLAB_TYPE) != desired.get(Properties.SLAB_TYPE)) {
            /*?}*/
                return true;
            }
        }
        // Standing banners, standing signs, hanging signs — ROTATION (0-15)
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.ROTATION_16)
        *//*?} else {*/
        if (desired.contains(Properties.ROTATION)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.ROTATION_16)) {
                *//*?} else {*/
                && existing.contains(Properties.ROTATION)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (!existing.getValue(BlockStateProperties.ROTATION_16).equals(desired.getValue(BlockStateProperties.ROTATION_16))) {
            *//*?} else {*/
            if (!existing.get(Properties.ROTATION).equals(desired.get(Properties.ROTATION))) {
            /*?}*/
                return true;
            }
        }
        // Hoppers — HOPPER_FACING (DOWN + 4 horizontal)
        /*? if >=26.1 {*//*
        if (desired.hasProperty(BlockStateProperties.FACING_HOPPER)
        *//*?} else {*/
        if (desired.contains(Properties.HOPPER_FACING)
        /*?}*/
                /*? if >=26.1 {*//*
                && existing.hasProperty(BlockStateProperties.FACING_HOPPER)) {
                *//*?} else {*/
                && existing.contains(Properties.HOPPER_FACING)) {
                /*?}*/
            /*? if >=26.1 {*//*
            if (existing.getValue(BlockStateProperties.FACING_HOPPER) != desired.getValue(BlockStateProperties.FACING_HOPPER)) {
            *//*?} else {*/
            if (existing.get(Properties.HOPPER_FACING) != desired.get(Properties.HOPPER_FACING)) {
            /*?}*/
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
    /*? if >=26.1 {*//*
    private static float computePitchToward(Vec3 eye, Vec3 target) {
    *//*?} else {*/
    private static float computePitchToward(Vec3d eye, Vec3d target) {
    /*?}*/
        /*? if >=26.1 {*//*
        Vec3 diff = target.subtract(eye);
        *//*?} else {*/
        Vec3d diff = target.subtract(eye);
        /*?}*/
        double horizDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        /*? if >=26.1 {*//*
        return (float) -(Mth.atan2(diff.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        return (float) -(MathHelper.atan2(diff.y, horizDist) * (180.0 / Math.PI));
        /*?}*/
    }

    private static float snapToMouseGCD(float desired, float serverCurrent) {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        double sens = mc.options.sensitivity().get();
        *//*?} else {*/
        double sens = mc.options.getMouseSensitivity().getValue();
        /*?}*/
        double gcd = Math.pow(sens * 0.6 + 0.2, 3.0) * 1.2;
        if (gcd < 0.001) return desired;            // zero-sensitivity guard
        return (float) (desired - (desired - serverCurrent) % gcd);
    }

    // placement face finding

    /** Finds the best adjacent solid face, preferring non-interactive neighbors. */
    /*? if >=26.1 {*//*
    public static Direction findPlacementFace(Level world, BlockPos target) {
    *//*?} else {*/
    public static Direction findPlacementFace(World world, BlockPos target) {
    /*?}*/
        Direction fallback = null;
        for (Direction dir : Direction.values()) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = target.offset(dir);
            /*?}*/
            BlockState neighborState = world.getBlockState(neighbor);

            /*? if >=26.1 {*//*
            if (neighborState.canBeReplaced()) continue;
            *//*?} else {*/
            if (neighborState.isReplaceable()) continue;
            /*?}*/
            /*? if >=26.1 {*//*
            if (neighborState.getShape(world, neighbor) == Shapes.empty()) continue;
            *//*?} else {*/
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;
            /*?}*/

            if (!isInteractive(neighborState.getBlock())) {
                return dir;
            }
            if (fallback == null) fallback = dir;
        }
        return fallback;
    }

    /** Finds a horizontal side face (no UP/DOWN) for half-block placement. */
    /*? if >=26.1 {*//*
    private static Direction findSidePlacementFace(Level world, BlockPos target) {
    *//*?} else {*/
    private static Direction findSidePlacementFace(World world, BlockPos target) {
    /*?}*/
        Direction fallback = null;
        for (Direction dir : new Direction[]{ Direction.NORTH, Direction.SOUTH,
                                              Direction.EAST,  Direction.WEST }) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = target.offset(dir);
            /*?}*/
            BlockState neighborState = world.getBlockState(neighbor);
            /*? if >=26.1 {*//*
            if (neighborState.canBeReplaced()) continue;
            *//*?} else {*/
            if (neighborState.isReplaceable()) continue;
            /*?}*/
            /*? if >=26.1 {*//*
            if (neighborState.getShape(world, neighbor) == Shapes.empty()) continue;
            *//*?} else {*/
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;
            /*?}*/
            if (!isInteractive(neighborState.getBlock())) {
                return dir;
            }
            if (fallback == null) fallback = dir;
        }
        return fallback;
    }

    /** Finds a non-interactive adjacent face (for chests, etc). */
    /*? if >=26.1 {*//*
    private static Direction findNonInteractiveFace(Level world, BlockPos target) {
    *//*?} else {*/
    private static Direction findNonInteractiveFace(World world, BlockPos target) {
    /*?}*/
        Direction interactive = null;
        for (Direction dir : Direction.values()) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = target.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = target.offset(dir);
            /*?}*/
            BlockState neighborState = world.getBlockState(neighbor);
            /*? if >=26.1 {*//*
            if (neighborState.canBeReplaced()) continue;
            *//*?} else {*/
            if (neighborState.isReplaceable()) continue;
            /*?}*/
            /*? if >=26.1 {*//*
            if (neighborState.getShape(world, neighbor) == Shapes.empty()) continue;
            *//*?} else {*/
            if (neighborState.getOutlineShape(world, neighbor) == VoxelShapes.empty()) continue;
            /*?}*/
            if (!isInteractive(neighborState.getBlock())) {
                return dir; // non-interactive — ideal
            }
            if (interactive == null) interactive = dir;
        }
        return interactive; // null if nothing solid adjacent
    }

    /** Whether any adjacent block is solid (supports placement). */
    /*? if >=26.1 {*//*
    public static boolean hasAdjacentSolid(Level world, BlockPos pos) {
    *//*?} else {*/
    public static boolean hasAdjacentSolid(World world, BlockPos pos) {
    /*?}*/
        for (Direction dir : Direction.values()) {
            /*? if >=26.1 {*//*
            BlockPos neighbor = pos.relative(dir);
            *//*?} else {*/
            BlockPos neighbor = pos.offset(dir);
            /*?}*/
            BlockState state = world.getBlockState(neighbor);
            /*? if >=26.1 {*//*
            if (!state.canBeReplaced() &&
            *//*?} else {*/
            if (!state.isReplaceable() &&
            /*?}*/
                    /*? if >=26.1 {*//*
                    state.getShape(world, neighbor) != Shapes.empty()) {
                    *//*?} else {*/
                    state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
                    /*?}*/
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
            /*? if >=26.1 {*//*
            TrapDoorBlock.class
            *//*?} else {*/
            TrapdoorBlock.class
            /*?}*/
    );

    /** Returns true if right-clicking this block opens a GUI or toggles state. */
    public static boolean isInteractive(Block block) {
        for (Class<? extends Block> clazz : INTERACTIVE) {
            if (clazz.isInstance(block)) return true;
        }
        return false;
    }

    /**
     * Temporarily forces the player into a sneaking state for block placement.
     * Returns a Runnable that restores the original sneaking state.
     */
    /*? if >=26.1 {*//*
    public static Runnable forceForPlacement(net.minecraft.client.player.LocalPlayer player) {
        boolean wasSneaking = player.isShiftKeyDown();
        player.setShiftKeyDown(true);
        return () -> player.setShiftKeyDown(wasSneaking);
    }
    *//*?} else {*/
    public static Runnable forceForPlacement(net.minecraft.client.network.ClientPlayerEntity player) {
        boolean wasSneaking = player.isSneaking();
        player.setSneaking(true);
        return () -> player.setSneaking(wasSneaking);
    }
    /*?}*/
}

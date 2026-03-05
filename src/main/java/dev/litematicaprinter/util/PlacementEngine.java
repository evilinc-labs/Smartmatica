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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Core automation engine for block placement tasks.
 *
 * <p>Uses a multi-tick pipeline to comply with anticheat placement validation:
 * <ol>
 *   <li><b>Tick 1 (ROTATE):</b> select item, find face, rotate player toward
 *       the hit position, start sneaking if the neighbour is interactive.</li>
 *   <li><b>Tick 2 (PLACE):</b> verify rotation has converged, build a proper
 *       ray-face hit result from the player's eye along the look vector, and
 *       send the interact-block packet.</li>
 *   <li><b>Tick 3 (FINISH):</b> release sneak if it was pressed.</li>
 * </ol>
 *
 * <p>Designed to stay under server placement-rate limits (default 8 BPS,
 * safely under the 9 BPS policy) and pass GrimAC RotationPlace /
 * FabricatedPlace / FarPlace / BadPackets checks.
 */
public final class PlacementEngine {

    private PlacementEngine() {}

    // ── placement pipeline states ───────────────────────────────────────

    private enum PlacePhase { IDLE, ROTATING, PLACING, FINISHING, BREAKING }

    private static PlacePhase phase = PlacePhase.IDLE;

    // ── pipeline context (set in ROTATING, consumed in PLACING/FINISHING) ─

    private static BlockPos   pendingTarget;
    private static BlockState pendingDesired;
    private static Direction  pendingFace;
    private static boolean    pendingNeedsSneak;
    /** When true, place against the target itself (no adjacent block). */
    private static boolean    pendingAirPlace;
    /** The item that must be in the player's hand when the placement
     *  packet is sent.  Verified in tickPlace to prevent wrong-block
     *  bugs when the hotbar slot changes between ROTATING and PLACING. */
    private static Item       pendingItem;
    private static float      targetYaw;
    private static float      targetPitch;
    private static float      savedYaw;
    private static float      savedPitch;
    private static int        rotateTicks;

    // ── self-correction state ───────────────────────────────────────────
    /** Position of a block that was placed with wrong orientation. */
    private static BlockPos   correctionTarget;
    /** The desired state for a correction re-place. */
    private static BlockState correctionDesired;
    /** Ticks spent breaking for correction. */
    private static int        breakingTicks;
    /** Max ticks to spend mining a misplaced block before giving up. */
    private static final int  MAX_BREAKING_TICKS = 200;
    /** Ticks to wait after breaking before re-placing (item pickup). */
    private static int        postBreakWait;
    /** Tracks how many correction attempts have been made per position.
     *  Bounded to 128 entries — oldest are evicted automatically. */
    private static final int MAX_CORRECTION_ENTRIES = 128;
    private static final Map<BlockPos, Integer> correctionAttempts = new LinkedHashMap<>(32, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Integer> eldest) {
            return size() > MAX_CORRECTION_ENTRIES;
        }
    };
    /** Max correction attempts before skipping a position permanently. */
    private static final int MAX_CORRECTION_ATTEMPTS = 2;

    /** Max ticks to spend converging rotation before giving up. */
    private static final int  MAX_ROTATE_TICKS = 4;
    /** Yaw/pitch must be within this many degrees to be considered converged. */
    private static final float CONVERGE_THRESHOLD = 1.0f;
    /** Max rotation speed per tick (degrees). */
    private static final float MAX_TURN_SPEED = 30.0f;

    // ── silent rotation (manual mode) ────────────────────────────────────
    /**
     * When {@code true}, rotation packets are sent to the server without
     * visually modifying the client-side player entity yaw/pitch.  This
     * prevents camera-jerk and conflicting rotation states that cause
     * server-side rubberbanding in manual (AutoBuild OFF) mode.
     */
    private static boolean silentRotation = false;

    public static void setSilentRotation(boolean value) { silentRotation = value; }
    public static boolean isSilentRotation() { return silentRotation; }

    // ── rate limiter ────────────────────────────────────────────────────

    private static final Random JITTER_RNG = new Random();
    private static int    bps               = 8;
    private static long   lastPlacementNano = 0;

    public static void setBps(int value) {
        bps = Math.max(1, Math.min(9, value));
    }

    public static int getBps() { return bps; }

    /**
     * Returns {@code true} if the engine is idle and enough time has passed
     * since the last placement to stay under the configured BPS ceiling.
     */
    public static boolean canPlace() {
        if (phase != PlacePhase.IDLE) return false;
        long nowNano = System.nanoTime();
        long intervalNano = 1_000_000_000L / bps;
        // ±25% jitter so the placement cadence doesn't form a
        // machine-perfect pattern detectable by timing analysis.
        long jitter = (long) (intervalNano * (JITTER_RNG.nextDouble() * 0.5 - 0.25));
        return (nowNano - lastPlacementNano) >= (intervalNano + jitter);
    }

    public static boolean isBusy() {
        return phase != PlacePhase.IDLE;
    }

    /** Returns the current pipeline phase name for debug logging. */
    public static String getPhase() {
        return phase.name();
    }

    public static boolean isCorrecting() {
        return phase == PlacePhase.BREAKING;
    }

    /** Record a placement action (updates the rate-limiter timestamp). */
    public static void recordPlacement() {
        lastPlacementNano = System.nanoTime();
    }

    /** Cancel any in-progress placement or correction and return to idle. */
    public static void reset() {
        if (pendingNeedsSneak && phase == PlacePhase.FINISHING) {
            if (SneakOverride.isForceAbsoluteSneak()) {
                // Edge-walk sneak active — keep server in sync
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
        pendingItem = null;
        correctionTarget = null;
        correctionDesired = null;
        breakingTicks = 0;
        postBreakWait = 0;
    }

    /** Clears the correction attempt history — call when the printer is
     *  toggled on so previously-skipped positions get a fresh chance. */
    public static void clearCorrectionHistory() {
        correctionAttempts.clear();
    }

    /** Prune stale entries from correctionAttempts.
     *  Entries that have reached MAX_CORRECTION_ATTEMPTS are "give-up"
     *  markers and must be KEPT so the engine doesn't re-attempt them.
     *  Only entries below the threshold (i.e. intermediate attempts for
     *  positions that were successfully corrected and later dirtied again)
     *  are eligible for pruning. */
    public static void pruneCompletedCorrections() {
        // Remove entries whose positions now match the schematic (successful
        // corrections), keeping give-up markers intact.
        correctionAttempts.values().removeIf(v -> v < MAX_CORRECTION_ATTEMPTS);
    }

    // ── per-tick inventory cache ────────────────────────────────────────
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

    // ═══════════════════════════════════════════════════════════════════
    //  MULTI-TICK PIPELINE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Tick the placement pipeline.  Must be called every client tick while
     * the printer is active.
     *
     * @return {@code true} on the tick a block was actually placed
     */
    public static boolean tick() {
        return switch (phase) {
            case IDLE     -> false;
            case ROTATING -> tickRotate();
            case PLACING  -> tickPlace();
            case FINISHING -> tickFinish();
            case BREAKING -> tickBreaking();
        };
    }

    // ── phase: ROTATING ─────────────────────────────────────────────────

    private static boolean tickRotate() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { reset(); return false; }

        rotateTicks++;

        if (silentRotation) {
            // ── Silent mode (manual placement): ────────────────────────
            // Send the target rotation to the server without changing the
            // client-side entity yaw/pitch.  The player's camera stays
            // where they left it — no visual jerk, no conflicting state.
            sendSilentLookPacket(mc.player, targetYaw, targetPitch);

            if (pendingNeedsSneak) {
                pressSneakPacket(mc.player);
            }

            phase = PlacePhase.PLACING;
            return false;
        }

        // ── Normal mode (auto-build): smooth interpolation ─────────────
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

            // Send the final rotation to the server so it's in sync
            // before the placement packet arrives next tick.
            sendLookPacket(mc.player, mc.player.getYaw(), mc.player.getPitch());

            // Send sneak packet this tick (will interact next tick)
            if (pendingNeedsSneak) {
                pressSneakPacket(mc.player);
            }

            phase = PlacePhase.PLACING;
        }

        return false;
    }

    // ── phase: PLACING ──────────────────────────────────────────────────

    private static boolean tickPlace() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            reset();
            return false;
        }

        ClientPlayerEntity player = mc.player;

        // Verify correct item is still selected
        if (pendingItem != null) {
            PlayerInventory inv = player.getInventory();
            /*? if >=1.21.5 {*//*
            Item held = inv.getStack(inv.getSelectedSlot()).getItem();
            *//*?} else {*/
            Item held = inv.getStack(inv.selectedSlot).getItem();
            /*?}*/
            if (held != pendingItem) {
                // Try to re-select the correct item
                if (!selectItem(player, mc, pendingItem, true)) {
                    // Item no longer available — abort this placement
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

        // In silent-rotation mode the entity yaw/pitch is the player's
        // real look direction (unchanged), so use the stored target
        // angles for the ray-face hit computation instead.
        float placeYaw   = silentRotation ? targetYaw   : player.getYaw();
        float placePitch  = silentRotation ? targetPitch : player.getPitch();

        BlockHitResult hitResult;
        if (pendingAirPlace) {
            // Air placement: click the target position itself.
            Direction airFace = Direction.UP;
            Vec3d hitPos = Vec3d.ofCenter(pendingTarget).add(
                    airFace.getOffsetX() * 0.5,
                    airFace.getOffsetY() * 0.5,
                    airFace.getOffsetZ() * 0.5);
            // Adjust Y for top-half states placed via air.
            hitPos = adjustHitForAirPlace(hitPos, pendingTarget, pendingDesired);
            hitResult = new BlockHitResult(hitPos, airFace, pendingTarget, false);
        } else {
            // Normal placement: click the neighbour block's face
            BlockPos neighbor = pendingTarget.offset(pendingFace);
            Direction clickSide = pendingFace.getOpposite();
            Vec3d hitPos = computeRayFaceHit(eyePos, placeYaw, placePitch,
                                              neighbor, clickSide, mc.world);
            // Adjust hit for half-placement (stairs/slabs/trapdoors).
            hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, pendingDesired);

            // Apply hinge adjustment for doors
            hitPos = adjustHitForDoorHinge(hitPos, neighbor, pendingDesired);

            hitResult = new BlockHitResult(hitPos, clickSide, neighbor, false);
        }

        // In silent-rotation mode, re-send the server-side look just
        // before the interact packet so the server's view of the player
        // rotation matches the placement direction on the same tick.
        if (silentRotation) {
            sendSilentLookPacket(player, placeYaw, placePitch);
        }

        // Interact
        boolean isLiquidPlacement = pendingDesired.getBlock() instanceof FluidBlock;
        boolean placed;
        if (isLiquidPlacement) {
            // Buckets use Item.use() which does its own ray cast from the
            // player's eye position.  interactBlock → useOnBlock returns PASS
            // for bucket items, so we must call interactItem directly.
            ActionResult result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            if (result.isAccepted()) {
                player.swingHand(Hand.MAIN_HAND);
            }
            // Verify the liquid was actually placed at the intended target.
            // interactItem does its own ray cast which may land on a
            // different position than pendingTarget if the look direction
            // drifted.  Check the world state at pendingTarget to confirm.
            if (result.isAccepted()) {
                BlockState afterState = mc.world.getBlockState(pendingTarget);
                placed = afterState.getBlock() instanceof FluidBlock
                      && afterState.getFluidState().isStill();
            } else {
                placed = false;
            }
        } else {
            // ── Offhand swap trick (GrimAC-safe placement) ──────────
            // Swap the main-hand item to off-hand (server-side only),
            // send the placement packet using OFF_HAND, then swap back
            // — all within the same tick.  GrimAC validates off-hand
            // placements more leniently, preventing false-positive
            // flags that cause ghost-blocking (client-side blocks the
            // server never accepted).
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
            // Client-side prediction: set the block so the position
            // scanner doesn't re-select it before server confirmation.
            mc.world.setBlockState(pendingTarget, pendingDesired);
            placed = true;
        }

        recordPlacement();

        if (pendingNeedsSneak) {
            phase = PlacePhase.FINISHING;
        } else {
            // Restore look direction (skip in silent mode — camera was never moved)
            if (!silentRotation) {
                restoreLook(player);
            } else {
                // Send the player's real rotation back to the server so
                // subsequent movement packets are consistent.
                sendSilentLookPacket(player, player.getYaw(), player.getPitch());
            }
            phase = PlacePhase.IDLE;
            pendingTarget = null;
            pendingItem = null;
        }

        return placed;
    }

    // ── phase: FINISHING (release sneak on a separate tick) ──────────────

    private static boolean tickFinish() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            // If edge-walking is forcing absolute sneak, don't
            // send a release packet — the server must stay in sync with
            // the client's forced sneak state.  Instead re-press so the
            // server keeps the player sneaking.
            if (SneakOverride.isForceAbsoluteSneak()) {
                pressSneakPacket(mc.player);
            } else {
                releaseSneakPacket();
            }
            if (!silentRotation) {
                restoreLook(mc.player);
            } else {
                // Resync the server with the player's real rotation.
                sendSilentLookPacket(mc.player, mc.player.getYaw(), mc.player.getPitch());
            }
        }
        phase = PlacePhase.IDLE;
        pendingTarget = null;
        pendingDesired = null;
        pendingNeedsSneak = false;
        pendingItem = null;
        return false;
    }

    // ── phase: BREAKING (self-correction — mine misplaced block) ────────

    private static boolean tickBreaking() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            reset();
            return false;
        }

        // ── post-break wait: allow dropped item to be picked up ──────
        if (postBreakWait > 0) {
            postBreakWait--;
            if (postBreakWait <= 0) {
                // Done waiting — return to IDLE for re-placement
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

        // Check if the block has been broken
        BlockState current = mc.world.getBlockState(correctionTarget);
        if (current.isAir() || current.isReplaceable()) {
            mc.interactionManager.cancelBlockBreaking();
            // Block broken — wait for item pickup
            postBreakWait = 5;
            return false;
        }

        // ── maintain look direction toward the block every tick ───────
        //   updateBlockBreakingProgress requires the player to be looking
        //   at the block; if the look drifts, break progress resets.
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(correctionTarget);
        Vec3d toBlock = blockCenter.subtract(eyePos);
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));

        sendLookPacket(mc.player, breakYaw, breakPitch);

        // Continue breaking — send break progress packets
        Direction breakFace = Direction.UP;
        mc.interactionManager.updateBlockBreakingProgress(
                correctionTarget, breakFace);
        mc.player.swingHand(Hand.MAIN_HAND);

        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BLOCK PLACEMENT (entry point — begins the pipeline)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Begins a placement attempt.  Does NOT place immediately — instead
     * starts the rotate → place → finish pipeline.  Call {@link #tick()}
     * on subsequent ticks to drive it.
     *
     * <p>For directional blocks (stairs, slabs, pillars, horizontal-facing
     * blocks), the engine reads the desired {@link BlockState}'s properties
     * and computes the correct player yaw, placement face, and hit position
     * to produce the intended orientation.
     *
     * @param target       world position to place at
     * @param desired      target block state (including orientation properties)
     * @param allowSwap    whether to pull items from main inventory to hotbar
     * @return {@code true} if the pipeline was successfully started
     */
    public static boolean placeBlock(BlockPos target, BlockState desired, boolean allowSwap) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        if (phase != PlacePhase.IDLE) return false;

        ClientPlayerEntity player = mc.player;
        World world = mc.world;

        // ── skip auto-created parts (door upper, bed head, tall plant upper) ──
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

        // ── 0. self-correction: wrong orientation already placed? ────────
        BlockState existing = world.getBlockState(target);
        if (!existing.isAir() && !existing.isReplaceable()
                && existing.getBlock() == desired.getBlock()
                && !existing.equals(desired)
                && isOrientationMismatch(existing, desired)) {
            // Check if we've already tried correcting this position too many times.
            // This breaks fail loops where the engine can't produce the right
            // orientation and endlessly breaks + re-places the same block.
            BlockPos immutable = target.toImmutable();
            int attempts = correctionAttempts.getOrDefault(immutable, 0);
            if (attempts >= MAX_CORRECTION_ATTEMPTS) {
                // Give up on this position — treat it as "placed" and move on.
                return false;
            }
            correctionAttempts.put(immutable, attempts + 1);
            // Same block type, wrong rotation — break it first
            return startCorrection(target, desired, player, mc);
        }

        // ── 0b. scaffold removal: Baritone-placed block occupying a ─────
        //    schematic position.  Break it so the correct block can be
        //    placed on the next cycle.
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

        // ── 0c. foreign block replacement: a block that doesn't match ───
        //    the schematic occupies this position.  Break it so the
        //    correct block can be placed on the next cycle.
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

        // ── 1. find the required item ───────────────────────────────────
        Item requiredItem = desired.getBlock().asItem();
        if (requiredItem == Items.AIR) return false;

        if (!selectItem(player, mc, requiredItem, allowSwap)) return false;

        // ── 2. find an adjacent face to click ───────────────────────────
        //  For pillar blocks (logs, quartz pillars, etc.), prefer clicking
        //  a face whose axis matches the desired axis property.
        Direction face = findOrientedPlacementFace(world, target, desired);
        boolean airPlace = (face == null);

        Vec3d eyePos = player.getEyePos();
        float desiredYaw;
        float desiredPitch;
        boolean needsSneak = false;

        if (airPlace) {
            // No adjacent face available — fall back to air placement.
            // Point toward the target block's center and use insideBlock=true.
            face = Direction.UP; // placeholder direction
            Vec3d hitPos = Vec3d.ofCenter(target);
            Vec3d toHit = hitPos.subtract(eyePos);
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));

            // Override yaw for blocks that use the player's horizontal facing
            Float facingYaw = getRequiredYaw(desired);
            if (facingYaw != null) {
                desiredYaw = facingYaw;
                desiredPitch = computePitchToward(eyePos, hitPos);
            }
            // Override pitch for 6-dir blocks facing UP/DOWN
            Float facingPitch = getRequiredPitch(desired);
            if (facingPitch != null) {
                desiredPitch = facingPitch;
            }
        } else {
            BlockPos neighbor = target.offset(face);
            Block neighborBlock = world.getBlockState(neighbor).getBlock();
            needsSneak = isInteractive(neighborBlock);

            // ── 3. compute yaw + hit position for correct orientation ────────
            Direction clickSide = face.getOpposite();

            // Start with a default hit on the face center
            Vec3d hitPos = computeRayFaceHit(eyePos, player.getYaw(), player.getPitch(),
                                              neighbor, clickSide, world);

            // Override hit Y for slab / stair half placement
            hitPos = adjustHitForHalf(hitPos, neighbor, clickSide, desired);

            // Override hit X/Z for door hinge side
            hitPos = adjustHitForDoorHinge(hitPos, neighbor, desired);

            // Compute yaw/pitch from eye to hit position
            Vec3d toHit = hitPos.subtract(eyePos);
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));

            // Override yaw for blocks that use the player's horizontal facing
            Float facingYaw = getRequiredYaw(desired);
            if (facingYaw != null) {
                desiredYaw = facingYaw;
                // Recompute pitch toward the hit position using the forced yaw
                desiredPitch = computePitchToward(eyePos, hitPos);
            }
            // Override pitch for 6-dir blocks facing UP/DOWN
            Float facingPitch = getRequiredPitch(desired);
            if (facingPitch != null) {
                desiredPitch = facingPitch;
            }
        }

        // ── 4. store pipeline state and begin rotation ──────────────────
        pendingTarget = target.toImmutable();
        pendingDesired = desired;
        pendingFace = face;
        pendingAirPlace = airPlace;
        pendingNeedsSneak = needsSneak;
        pendingItem = requiredItem;
        targetYaw = desiredYaw;
        targetPitch = MathHelper.clamp(desiredPitch, -90.0f, 90.0f);
        savedYaw = player.getYaw();
        savedPitch = player.getPitch();
        rotateTicks = 0;
        phase = PlacePhase.ROTATING;

        return true;
    }

    /**
     * Starts the placement pipeline for a liquid source block (water / lava).
     *
     * <p>Liquid placement uses the same multi-tick rotation pipeline as
     * {@link #placeBlock}, but selects a bucket item instead of a
     * {@link BlockItem} and skips orientation overrides (liquids have no
     * directional properties).
     *
     * @param target       world position to place the liquid at
     * @param desired      target block state ({@link FluidBlock} with level 0)
     * @param allowSwap    whether to pull items from main inventory to hotbar
     * @return {@code true} if the pipeline was successfully started
     */
    public static boolean placeLiquid(BlockPos target, BlockState desired, boolean allowSwap) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return false;
        if (phase != PlacePhase.IDLE) return false;

        ClientPlayerEntity player = mc.player;
        World world = mc.world;

        // Determine the bucket item for this fluid
        Item bucketItem;
        Block block = desired.getBlock();
        if (block == Blocks.WATER) bucketItem = Items.WATER_BUCKET;
        else if (block == Blocks.LAVA) bucketItem = Items.LAVA_BUCKET;
        else return false;

        // Pre-check: verify the target position is suitable for liquid.
        // Must be air, replaceable, or flowing same-type fluid.
        // If it's already a source block of the right type, skip.
        // If it's a solid block, the bucket can't place there.
        BlockState currentState = world.getBlockState(target);
        if (!currentState.isAir() && !currentState.isReplaceable()) {
            // Position has a non-replaceable block — check if it's the
            // correct fluid source (already done) or a solid obstacle.
            if (currentState.getBlock() instanceof FluidBlock) {
                if (currentState.getFluidState().isStill()) {
                    return false; // already a source block — nothing to do
                }
                // Flowing fluid — can place a source over it
            } else {
                return false; // solid block — can't place liquid here
            }
        }

        // Buckets use interactItem which does its own ray cast from the
        // player's eye.  When the player is below the target, the ray
        // hits the UNDERSIDE of the adjacent block and places liquid on
        // the wrong face.  Only allow placement when the eye is at or
        // above the target Y so the ray naturally hits the correct face.
        if (player.getEyePos().y < target.getY()) {
            return false;
        }

        if (!selectItem(player, mc, bucketItem, allowSwap)) return false;

        // Find an adjacent solid block to click against.
        // Buckets REQUIRE a solid face — you can't pour liquid into empty air.
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

            // For liquid placement, point the player's look at the center
            // of the neighbor face that borders the target.  BucketItem.use()
            // does its own ray cast from the player's eye — it will hit
            // this face and pour the liquid into the adjacent air/fluid block.
            Direction clickSide = face.getOpposite();
            Vec3d faceCenter = Vec3d.ofCenter(neighbor)
                    .add(Vec3d.of(clickSide.getVector()).multiply(0.5));
            Vec3d toHit = faceCenter.subtract(eyePos);
            double horizDist = Math.sqrt(toHit.x * toHit.x + toHit.z * toHit.z);
            desiredYaw = (float) (MathHelper.atan2(toHit.z, toHit.x) * (180.0 / Math.PI)) - 90.0f;
            desiredPitch = (float) -(MathHelper.atan2(toHit.y, horizDist) * (180.0 / Math.PI));
        }

        // Store pipeline state and begin rotation
        pendingTarget = target.toImmutable();
        pendingDesired = desired;
        pendingFace = face;
        pendingAirPlace = false;  // liquids never air-place
        pendingNeedsSneak = needsSneak;
        pendingItem = bucketItem;
        targetYaw = desiredYaw;
        targetPitch = MathHelper.clamp(desiredPitch, -90.0f, 90.0f);
        savedYaw = player.getYaw();
        savedPitch = player.getPitch();
        rotateTicks = 0;
        phase = PlacePhase.ROTATING;

        return true;
    }

    /**
     * Begin a self-correction: break the misplaced block so it can be
     * re-placed with correct orientation on a subsequent tick.
     */
    private static boolean startCorrection(BlockPos target, BlockState desired,
                                           ClientPlayerEntity player,
                                           MinecraftClient mc) {
        correctionTarget = target.toImmutable();
        correctionDesired = desired;
        breakingTicks = 0;
        savedYaw = player.getYaw();
        savedPitch = player.getPitch();

        // Select the best tool for breaking this block
        BlockState existing = mc.world.getBlockState(target);
        selectBestTool(player, mc, existing);

        // Look at the block to break it
        Vec3d eyePos = player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(target);
        Vec3d toBlock = blockCenter.subtract(eyePos);
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));

        sendLookPacket(player, breakYaw, breakPitch);

        // Start the breaking process
        mc.interactionManager.attackBlock(target, Direction.UP);
        phase = PlacePhase.BREAKING;
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RAY-FACE HIT CALCULATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes a hit position on the given face of {@code neighbor} that
     * lies on a ray from {@code eyePos} along the player's look direction.
     *
     * <p>If the ray doesn't intersect the face cleanly (edge cases with
     * very oblique angles), falls back to the center of the face, which
     * is still valid for anticheat since the hit pos is on the block face.
     */
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

    // ═══════════════════════════════════════════════════════════════════
    //  LOOK PACKET (keeps server rotation in sync with client)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sends a look-only movement packet to the server <b>without</b>
     * modifying the client-side entity rotation.  Used in silent-rotation
     * mode so the server sees the intended look direction while the
     * player's camera remains untouched.
     */
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

    /**
     * Sets the entity yaw/pitch AND sends a look-only movement packet so
     * the server's view of the player rotation matches the client before
     * any interaction/action packets that follow in the same tick.
     */
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

    // ═══════════════════════════════════════════════════════════════════
    //  SNEAK INTERACTION HELPER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Temporarily releases sneak overrides and sends a server-synced
     * release packet so that block-use interactions (opening chests,
     * placing shulkers) are accepted.
     *
     * @return a {@link Runnable} that restores the prior sneak state
     *         and re-sends the press packet if applicable
     */
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

    // ═══════════════════════════════════════════════════════════════════
    //  SNEAK PACKET HELPERS (separated for cross-tick timing)
    // ═══════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════
    //  LOOK RESTORE
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Smoothly restores the player's look direction after placement.
     * Uses interpolation to avoid a jarring snap-back; any subsequent
     * mouse movement from the player will override this immediately.
     */
    private static void restoreLook(ClientPlayerEntity player) {
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(savedYaw - currentYaw);
        float pitchDiff = savedPitch - currentPitch;

        // Interpolate most of the way back in one tick (lerp factor 0.65)
        // — smooth enough to hide jitter, fast enough to feel responsive.
        float newYaw = currentYaw + yawDiff * 0.65f;
        float newPitch = currentPitch + pitchDiff * 0.65f;

        // Snap when close enough to avoid lingering drift
        if (Math.abs(yawDiff) < 1.5f && Math.abs(pitchDiff) < 1.5f) {
            newYaw = savedYaw;
            newPitch = savedPitch;
        }

        sendLookPacket(player, newYaw, newPitch);
    }

    // ── inventory helpers ───────────────────────────────────────────────

    /**
     * Selects the required item in the player's hotbar.
     *
     * @return {@code true} if the item is now in the selected hotbar slot
     */
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

    /**
     * Select the best tool in the player's inventory for breaking the
     * given block state.  Scans the hotbar first, then the main inventory
     * (swapping into the hotbar if necessary).
     *
     * <p>"Best" is the tool with the highest
     * {@link ItemStack#getMiningSpeedMultiplier(BlockState)} — a pickaxe
     * for stone, an axe for wood, a shovel for dirt, etc.
     *
     * <p>If no tool improves on bare-hand speed (1.0), the current slot
     * is kept as-is (bare hands are fine for e.g. glass, leaves).
     *
     * @param player the client player
     * @param mc     the client instance
     * @param state  the block state about to be broken
     */
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

    // ═══════════════════════════════════════════════════════════════════
    //  ORIENTATION HELPERS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * For blocks that derive their facing from the player's horizontal
     * look direction, returns the yaw the player must face to produce
     * the desired {@code FACING} property.
     *
     * <p>Covers stairs, glazed terracotta, dispensers, observers,
     * pistons, carved pumpkins, furnaces, and similar blocks.
     *
     * @return the required yaw in degrees, or {@code null} if the block
     *         doesn't use player-facing orientation
     */
    private static Float getRequiredYaw(BlockState desired) {
        Block block = desired.getBlock();

        // Stairs — FACING is set to the player's look direction (NOT opposite)
        if (block instanceof StairsBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing);
            }
        }

        // Blocks that use HorizontalFacingBlock.FACING (placed opposite player)
        // Excludes wall-mounted variants whose facing is set by the clicked face.
        if (block instanceof GlazedTerracottaBlock
                || block instanceof CarvedPumpkinBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof LoomBlock
                || block instanceof StonecutterBlock
                || block instanceof CraftingTableBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                // These blocks face opposite to the player's look direction
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
                // UP/DOWN — control via pitch, yaw doesn't matter for these
                // The pitch is handled elsewhere (adjustHitForHalf or clickFace)
            }
        }

        // Anvil — uses HORIZONTAL_FACING
        if (block instanceof AnvilBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                // Anvils face perpendicular to placement direction
                return directionToYaw(facing.rotateYClockwise());
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

        // Hoppers — facing is determined by clicked face (handled in
        // findOrientedPlacementFace), NOT by player yaw.  No override needed.

        // End rods, lightning rods — FACING (all 6)
        if (block instanceof EndRodBlock || block instanceof LightningRodBlock) {
            if (desired.contains(Properties.FACING)) {
                Direction facing = desired.get(Properties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    return directionToYaw(facing.getOpposite());
                }
            }
        }

        // Standing banners, standing signs, hanging signs — ROTATION (0-15)
        // rotation = floor((180 + yaw) * 16 / 360 + 0.5) & 15
        // Solving for yaw: yaw = rotation * 22.5 - 180
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

        // Doors — FACING is set to the player's horizontal facing directly
        // (NOT opposite), so we return directionToYaw(facing) without
        // getOpposite().
        if (block instanceof DoorBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing);
            }
        }

        // Beds — FACING = direction from foot to head
        // Player must look in that direction (not opposite)
        if (block instanceof BedBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return directionToYaw(facing);
            }
        }

        // ── Catch-all for HORIZONTAL_FACING blocks not listed above ─────
        // Campfires, beehives, lecterns, grindstones, bells, etc.
        if (desired.contains(Properties.HORIZONTAL_FACING)) {
            // Only apply if not already handled above (PlantBlock, etc.
            // don't have HORIZONTAL_FACING, so no false positives here)
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

    /**
     * For blocks that derive their facing from the player's vertical
     * look direction (pitch), returns the required pitch to produce the
     * desired {@code FACING} property when it is {@link Direction#UP}
     * or {@link Direction#DOWN}.
     *
     * <p>Covers dispensers, droppers, observers, pistons, barrels,
     * shulker boxes, end rods, lightning rods, and similar blocks.
     *
     * @return the required pitch in degrees, or {@code null} if the
     *         block doesn't need a pitch override
     */
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

    /**
     * Adjusts the hit position's Y coordinate so that Minecraft produces
     * the correct top/bottom half for slabs and stairs.
     *
     * <p>When clicking a vertical face:
     * <ul>
     *   <li>Hit Y in the <b>lower half</b> → bottom slab / bottom stair</li>
     *   <li>Hit Y in the <b>upper half</b> → top slab / top stair</li>
     * </ul>
     * When clicking the top face → always bottom; bottom face → always top.
     */
    private static Vec3d adjustHitForHalf(Vec3d hitPos, BlockPos neighbor,
                                          Direction clickSide, BlockState desired) {
        Block block = desired.getBlock();

        // ── Stairs ──────────────────────────────────────────────────────
        if (block instanceof StairsBlock && desired.contains(Properties.BLOCK_HALF)) {
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            if (clickSide == Direction.UP) {
                // Clicking top face always produces BOTTOM stair — correct for BOTTOM
                if (half == BlockHalf.TOP) {
                    // Need to click bottom face or upper portion of side face
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

        // ── Slabs ───────────────────────────────────────────────────────
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

        // ── Trapdoors ───────────────────────────────────────────────────
        if (block instanceof TrapdoorBlock && desired.contains(Properties.BLOCK_HALF)) {
            BlockHalf half = desired.get(Properties.BLOCK_HALF);
            if (clickSide == Direction.UP) {
                // Clicking top face → bottom trapdoor. If we want TOP, wrong face.
                if (half == BlockHalf.TOP) {
                    return forceHitY(hitPos, neighbor, true);
                }
            } else if (clickSide == Direction.DOWN) {
                // Clicking bottom face → top trapdoor. If we want BOTTOM, wrong face.
                if (half == BlockHalf.BOTTOM) {
                    return forceHitY(hitPos, neighbor, false);
                }
            } else {
                // Side face — control via Y position within the face
                return forceHitY(hitPos, neighbor, half == BlockHalf.TOP);
            }
        }

        return hitPos;
    }

    /**
     * Forces the Y component of a hit position to land in the upper or
     * lower half of the neighbor block's face.
     */
    private static Vec3d forceHitY(Vec3d hitPos, BlockPos neighbor, boolean upper) {
        double y = upper
                ? neighbor.getY() + 0.75   // upper quarter of the block
                : neighbor.getY() + 0.25;  // lower quarter of the block
        return new Vec3d(hitPos.x, y, hitPos.z);
    }

    /**
     * Adjusts the hit position's X or Z component to influence the door
     * hinge side.  Minecraft's {@code DoorBlock.getHinge()} uses the hit
     * position as a tiebreaker when neighbouring blocks don't determine
     * the hinge.  By biasing the hit toward the left or right side of the
     * block (relative to the door's facing direction) we can steer the
     * hinge to match the schematic.
     *
     * <p>Hinge determination per facing direction:
     * <ul>
     *   <li>NORTH: hitX ≤ 0.5 → LEFT, hitX &gt; 0.5 → RIGHT</li>
     *   <li>SOUTH: hitX ≥ 0.5 → LEFT, hitX &lt; 0.5 → RIGHT</li>
     *   <li>EAST:  hitZ ≤ 0.5 → LEFT, hitZ &gt; 0.5 → RIGHT</li>
     *   <li>WEST:  hitZ ≥ 0.5 → LEFT, hitZ &lt; 0.5 → RIGHT</li>
     * </ul>
     */
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

    /**
     * Adjusts the hit position for air placement of half-blocks (stairs,
     * slabs, trapdoors).  When air-placing, the hit position defaults to
     * the block center (Y+0.5), which always produces BOTTOM half.
     * This method shifts it to Y+0.75 for TOP half variants.
     */
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

    /**
     * Finds an adjacent face to click, with orientation awareness.
     *
     * <p>For <b>wall-mounted blocks</b> (wall torches, wall signs, ladders,
     * etc.), forces the face toward the specific support block dictated by
     * the desired state's {@code FACING} / {@code HORIZONTAL_FACING}
     * property.  Returns {@code null} if the required support block is
     * missing — the caller should skip this candidate and try later.
     *
     * <p>For <b>floor-standing variants</b> (standing torches, lanterns,
     * standing signs), forces face = DOWN so the engine clicks the top
     * face of the block below.
     *
     * <p>For <b>pillar blocks</b> (logs, quartz pillars, basalt, etc.),
     * prefers a face whose normal matches the desired {@code AXIS} property.
     *
     * <p>For all other blocks, delegates to {@link #findPlacementFace}.
     */
    private static Direction findOrientedPlacementFace(World world, BlockPos target,
                                                       BlockState desired) {
        Block block = desired.getBlock();

        // ── Wall-mounted torches ────────────────────────────────────────
        //  wall_torch, soul_wall_torch, redstone_wall_torch
        //  HORIZONTAL_FACING = direction the torch points outward from the wall
        //  Support block is in the OPPOSITE direction.
        if (block instanceof WallTorchBlock
                || block instanceof WallRedstoneTorchBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                Direction supportDir = facing.getOpposite();
                return requireSolidFace(world, target, supportDir);
            }
        }

        // ── Standing torches ────────────────────────────────────────────
        //  torch, soul_torch — must be placed on top of a block below
        //  RedstoneTorchBlock extends TorchBlock but is NOT a WallTorchBlock
        if (block instanceof TorchBlock && !(block instanceof WallTorchBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }
        if (block instanceof RedstoneTorchBlock && !(block instanceof WallRedstoneTorchBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // ── Wall signs ──────────────────────────────────────────────────
        //  HORIZONTAL_FACING = direction the sign face points (away from wall)
        //  Support block is behind the sign.
        if (block instanceof WallSignBlock || block instanceof WallHangingSignBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // ── Standing signs ──────────────────────────────────────────────
        if (block instanceof SignBlock && !(block instanceof WallSignBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // ── Hanging signs (chain-attached below a block) ────────────────
        if (block instanceof HangingSignBlock && !(block instanceof WallHangingSignBlock)) {
            return requireSolidFace(world, target, Direction.UP);
        }

        // ── Standing banners ────────────────────────────────────────────
        if (block instanceof AbstractBannerBlock && !(block instanceof WallBannerBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // ── Wall banners ────────────────────────────────────────────────
        if (block instanceof WallBannerBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // ── Ladders ─────────────────────────────────────────────────────
        //  FACING = direction the ladder front faces; support block is behind
        if (block instanceof LadderBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }

        // ── Lanterns ────────────────────────────────────────────────────
        //  HANGING = true → attached to block above; false → on block below
        if (block instanceof LanternBlock) {
            if (desired.contains(Properties.HANGING) && desired.get(Properties.HANGING)) {
                return requireSolidFace(world, target, Direction.UP);
            } else {
                return requireSolidFace(world, target, Direction.DOWN);
            }
        }

        // ── Buttons (wall / floor / ceiling) ────────────────────────────
        if (block instanceof ButtonBlock) {
            return resolveWallMountedFace(world, target, desired);
        }

        // ── Levers (wall / floor / ceiling) ─────────────────────────────
        if (block instanceof LeverBlock) {
            return resolveWallMountedFace(world, target, desired);
        }

        // ── Skulls / heads on walls ─────────────────────────────────────
        if (block instanceof WallSkullBlock) {
            if (desired.contains(Properties.HORIZONTAL_FACING)) {
                Direction facing = desired.get(Properties.HORIZONTAL_FACING);
                return requireSolidFace(world, target, facing.getOpposite());
            }
        }
        if (block instanceof SkullBlock && !(block instanceof WallSkullBlock)) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // ── Trapdoors ────────────────────────────────────────────────────
        //  BLOCK_HALF determines attachment: TOP → attached to block above,
        //  BOTTOM → attached to block below.  HORIZONTAL_FACING = direction
        //  the trapdoor hinge side faces.
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

        // ── Doors ───────────────────────────────────────────────────────
        //  Place by clicking the top face of the block below.
        //  Only LOWER half should reach here (UPPER is auto-created).
        if (block instanceof DoorBlock) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // ── Beds ────────────────────────────────────────────────────────
        //  Place by clicking the floor.  FOOT part only (HEAD auto-created).
        //  Player yaw determines head direction (handled by getRequiredYaw).
        if (block instanceof BedBlock) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // ── Tall plants ─────────────────────────────────────────────────
        //  Two-block-tall: place lower half on the floor.
        if (block instanceof TallPlantBlock) {
            return requireSolidFace(world, target, Direction.DOWN);
        }

        // Hoppers — facing is determined by clicked face, NOT player yaw.
        //  HOPPER_FACING = output direction.  Clicking a face places the
        //  hopper with output toward that face (or DOWN for top/bottom).
        if (block instanceof HopperBlock) {
            if (desired.contains(Properties.HOPPER_FACING)) {
                Direction facing = desired.get(Properties.HOPPER_FACING);
                if (facing == Direction.DOWN) {
                    // DOWN hopper — click any horizontal face or below,
                    // NOT the top face (clicking TOP makes it face down
                    // anyway, but prefer a solid neighbor).
                    return findPlacementFace(world, target);
                } else {
                    // Horizontal hopper — must click the face in the
                    // output direction so MC assigns that facing.
                    return requireSolidFace(world, target, facing);
                }
            }
        }

        // ── Stairs / Slabs / Trapdoors — prefer side faces ────────────
        //  When clicking top face → always BOTTOM half; when clicking
        //  bottom face → always TOP half.  Only side faces allow the
        //  hit Y position to control top/bottom, so prefer them.
        if (block instanceof StairsBlock
                || block instanceof SlabBlock
                || block instanceof TrapdoorBlock) {
            Direction sideFace = findSidePlacementFace(world, target);
            if (sideFace != null) return sideFace;
            // Fall through to generic if no side face available
            return findPlacementFace(world, target);
        }

        // ── Pillar blocks (logs, quartz pillars, basalt, etc.) ──────────
        //  Must click a face whose normal matches the desired axis so MC
        //  assigns the correct AXIS property.  If no such face exists,
        //  return null to trigger air placement (preserves axis via packet).
        if (block instanceof PillarBlock && desired.contains(Properties.AXIS)) {
            Direction.Axis desiredAxis = desired.get(Properties.AXIS);
            Direction preferred = preferFaceForAxis(world, target, desiredAxis);
            if (preferred != null) return preferred;
            // No face along the correct axis — return null so the caller
            // uses air placement, which defaults to the Y axis.
            return null;
        }

        return findPlacementFace(world, target);
    }

    /**
     * Returns the given direction if there is a solid support block in
     * that direction from {@code target}.  Returns {@code null} otherwise,
     * signalling that the block cannot be placed yet (support missing).
     */
    private static Direction requireSolidFace(World world, BlockPos target, Direction dir) {
        BlockPos neighbor = target.offset(dir);
        BlockState state = world.getBlockState(neighbor);
        if (!state.isReplaceable()
                && state.getOutlineShape(world, neighbor) != VoxelShapes.empty()) {
            return dir;
        }
        return null; // required support block not present
    }

    /**
     * Resolves the correct placement face for blocks that use the
     * {@code FACE} property ({@code WallMountLocation}: FLOOR / WALL / CEILING)
     * combined with {@code HORIZONTAL_FACING}.
     * <p>Used by buttons and levers.
     */
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

    /**
     * Tries to find an adjacent solid face whose normal is along
     * {@code desiredAxis}.  This makes MC place a pillar block along
     * that axis.
     */
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

    /**
     * Checks whether two block states of the same block type differ in
     * orientation-related properties (facing, axis, half, type, etc.).
     * Used to detect mis-placed blocks that need self-correction.
     */
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

    /**
     * Converts a cardinal {@link Direction} to the player yaw (degrees)
     * that makes the player look in that direction.
     */
    private static float directionToYaw(Direction dir) {
        return switch (dir) {
            case SOUTH -> 0.0f;
            case WEST  -> 90.0f;
            case NORTH -> 180.0f;
            case EAST  -> -90.0f;
            default    -> 0.0f;  // UP/DOWN — irrelevant
        };
    }

    /**
     * Computes the pitch angle from {@code eye} toward {@code target}.
     */
    private static float computePitchToward(Vec3d eye, Vec3d target) {
        Vec3d diff = target.subtract(eye);
        double horizDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        return (float) -(MathHelper.atan2(diff.y, horizDist) * (180.0 / Math.PI));
    }

    // ── placement face finding ──────────────────────────────────────────

    /**
     * Finds the best adjacent direction whose neighbour has a clickable solid
     * shape.  Prefers non-interactive neighbours.
     *
     * @return direction from {@code target} to the solid neighbour, or
     *         {@code null} if none exist
     */
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

    /**
     * Finds an adjacent <b>horizontal</b> (side) face to click, skipping
     * UP and DOWN.  This is critical for stairs, slabs, and trapdoors
     * because clicking a top or bottom face locks the half — only side
     * faces allow the hit Y to control top vs bottom.
     *
     * @return a horizontal direction from {@code target} to a solid
     *         neighbour, or {@code null} if none exist
     */
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

    /**
     * Finds an adjacent face to click, <b>strongly</b> preferring
     * non-interactive blocks.  Falls back to any adjacent solid only
     * if every neighbor is interactive (or air).
     *
     * <p>Used by chests and similar interactive blocks that need to be
     * placed against a neighbor without accidentally triggering a GUI.
     * Prefers blocks that don't require sneaking.
     */
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

    // ── interactive block detection ─────────────────────────────────────

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

    /**
     * Returns {@code true} if right-clicking the given block would open a GUI
     * or toggle state instead of placing against it.
     */
    public static boolean isInteractive(Block block) {
        for (Class<? extends Block> clazz : INTERACTIVE) {
            if (clazz.isInstance(block)) return true;
        }
        return false;
    }
}

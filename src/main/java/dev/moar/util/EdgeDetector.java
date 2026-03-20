package dev.moar.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Scans the blocks around a player's hitbox to determine whether they
 * are near a dangerous platform edge (a position where the
 * ground drops away by 3+ blocks, enough to take fall damage).
 *
 * Used by the sneak system to avoid forcing sneak speed when the
 * player is safely in the middle of a flat platform — sneak is only
 * engaged when the player is within ~0.3 blocks of a ledge where
 * MC's {@code clipAtLedge()} is needed to prevent falling.
 *
 * Algorithm
 * The player hitbox is 0.6×1.8×0.6 (centred on X/Z).
 * We expand the hitbox by a small margin and then iterate over
 * every block column that the expanded bounding box overlaps.
 * If any column has no solid ground within 3 blocks below foot
 * level, the player is near a dangerous edge and should sneak.
 *
 * Unlike the previous 8-point sampling approach, this grid scan
 * has no blind spots — it catches concave inner corners (e.g. cross-
 * or L-shaped platforms) where discrete probe points could miss the
 * gap between two arms.
 *
 * Why 3 blocks?
 * Falls of 1–2 blocks deal no damage and are trivially recoverable.
 * Only falls of 3+ blocks are dangerous (4+ blocks deal damage).
 * Checking 3 levels avoids false positives from small gaps in
 * partially-built structures, stair steps, and slab surfaces while
 * still catching genuine ledges where the player could be harmed.
 */
public final class EdgeDetector {

    private EdgeDetector() {}

    /**
     * Half-width of the player hitbox (0.6 / 2 = 0.3).
     */
    private static final double HALF_WIDTH = 0.3;

    /**
     * How far beyond the hitbox edge to probe for drop-offs.  At our
     * walking speed of 0.10 blocks/tick, 0.30 gives ~3 ticks of lead
     * time for MC's edge clamping to engage before the player falls.
     */
    private static final double PROBE_MARGIN = 0.30;

    // ── per-tick cache (isNearEdge is called per render frame) ───────
    private static long   cachedTick   = Long.MIN_VALUE;
    private static boolean cachedResult = false;

    /**
     * Returns {@code true} if the player is near a dangerous platform
     * edge — i.e. there is at least one block column around the
     * expanded hitbox that has a 3+ block drop beneath it.
     *
     * When this returns {@code false}, the player is safely on solid
     * ground (or only near small 1–2 block step-downs), and sneaking is
     * unnecessary.
     *
     * @param player the client player entity
     * @param world  the client world
     * @return {@code true} if the player is near a dangerous ledge
     */
    public static boolean isNearEdge(ClientPlayerEntity player, World world) {
        // Cache result for the current game tick — this method is called
        // per render frame via mixin but the answer only changes per tick.
        long tick = world.getTime();
        if (tick == cachedTick) return cachedResult;
        cachedTick = tick;

        /*? if >=1.21.10 {*//*
        double px = player.getSyncedPos().x;
        double py = player.getSyncedPos().y;
        double pz = player.getSyncedPos().z;
        *//*?} else {*/
        double px = player.getPos().x;
        double py = player.getPos().y;
        double pz = player.getPos().z;
        /*?}*/

        // Expanded bounding perimeter: hitbox + probe margin
        double extent = HALF_WIDTH + PROBE_MARGIN;

        // Iterate over every block column the expanded bbox overlaps.
        // This catches ALL edge shapes including concave inner corners
        // of cross-shaped, L-shaped, or irregular partially-built platforms.
        int minBx = MathHelper.floor(px - extent);
        int maxBx = MathHelper.floor(px + extent);
        int minBz = MathHelper.floor(pz - extent);
        int maxBz = MathHelper.floor(pz + extent);
        int footY = MathHelper.floor(py);

        for (int bx = minBx; bx <= maxBx; bx++) {
            for (int bz = minBz; bz <= maxBz; bz++) {
                if (!hasSafeGround(world, bx, footY, bz)) {
                    cachedResult = true;
                    return true; // dangerous drop-off found
                }
            }
        }

        cachedResult = false;
        return false; // all columns have safe ground — no need to sneak
    }

    /**
     * Returns {@code true} if there is at least one solid block at the
     * given (x, z) column within 3 blocks below the foot level.  This
     * means a fall of at most 2 blocks (non-damaging) — safe to walk.
     *
     * Checks: footY, footY−1, footY−2.  A 3+ block drop means no
     * solid ground in this range and the player could take fall damage.
     */
    private static boolean hasSafeGround(World world, int x, int y, int z) {
        for (int dy = 0; dy >= -2; dy--) {
            if (isGroundBlock(world, new BlockPos(x, y + dy, z))) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the block at {@code pos} has a non-empty
     * collision shape (i.e. it's something the player can stand on).
     */
    private static boolean isGroundBlock(World world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }
}

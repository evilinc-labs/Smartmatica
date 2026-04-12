package dev.moar.util;

/*? if >=26.1 {*//*
import net.minecraft.client.player.LocalPlayer;
*//*?} else {*/
import net.minecraft.client.network.ClientPlayerEntity;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.util.Mth;
*//*?} else {*/
import net.minecraft.util.math.MathHelper;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/

// Detects 3+ block drops around player hitbox for sneak engagement.
public final class EdgeDetector {

    private EdgeDetector() {}

    private static final double HALF_WIDTH = 0.3; // player hitbox half-width

    private static final double PROBE_MARGIN = 0.30; // margin beyond hitbox for drop-off detection

    // per-tick cache (isNearEdge is called per render frame)
    private static long   cachedTick   = Long.MIN_VALUE;
    private static boolean cachedResult = false;

    // Check for adjacent 3+ block drops.
    /*? if >=26.1 {*//*
    public static boolean isNearEdge(LocalPlayer player, Level world) {
    *//*?} else {*/
    public static boolean isNearEdge(ClientPlayerEntity player, World world) {
    /*?}*/
        // Cache per tick (called per render frame)
        /*? if >=26.1 {*//*
        long tick = world.getGameTime();
        *//*?} else {*/
        long tick = world.getTime();
        /*?}*/
        if (tick == cachedTick) return cachedResult;
        cachedTick = tick;

        /*? if >=26.1 {*//*
        double px = player.position().x;
        double py = player.position().y;
        double pz = player.position().z;
        *//*?} else if >=1.21.10 {*//*
        double px = player.getSyncedPos().x;
        double py = player.getSyncedPos().y;
        double pz = player.getSyncedPos().z;
        *//*?} else {*/
        double px = player.getPos().x;
        double py = player.getPos().y;
        double pz = player.getPos().z;
        /*?}*/

        // Scan all block columns under expanded hitbox
        double extent = HALF_WIDTH + PROBE_MARGIN;
        /*? if >=26.1 {*//*
        int minBx = Mth.floor(px - extent);
        *//*?} else {*/
        int minBx = MathHelper.floor(px - extent);
        /*?}*/
        /*? if >=26.1 {*//*
        int maxBx = Mth.floor(px + extent);
        *//*?} else {*/
        int maxBx = MathHelper.floor(px + extent);
        /*?}*/
        /*? if >=26.1 {*//*
        int minBz = Mth.floor(pz - extent);
        *//*?} else {*/
        int minBz = MathHelper.floor(pz - extent);
        /*?}*/
        /*? if >=26.1 {*//*
        int maxBz = Mth.floor(pz + extent);
        *//*?} else {*/
        int maxBz = MathHelper.floor(pz + extent);
        /*?}*/
        /*? if >=26.1 {*//*
        int footY = Mth.floor(py);
        *//*?} else {*/
        int footY = MathHelper.floor(py);
        /*?}*/

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

    // True if there's solid ground within 3 blocks below foot level at (x, z).
    /*? if >=26.1 {*//*
    private static boolean hasSafeGround(Level world, int x, int y, int z) {
    *//*?} else {*/
    private static boolean hasSafeGround(World world, int x, int y, int z) {
    /*?}*/
        for (int dy = 0; dy >= -2; dy--) {
            if (isGroundBlock(world, new BlockPos(x, y + dy, z))) return true;
        }
        return false;
    }

    // True if the block has a collision shape (standable).
    /*? if >=26.1 {*//*
    private static boolean isGroundBlock(Level world, BlockPos pos) {
    *//*?} else {*/
    private static boolean isGroundBlock(World world, BlockPos pos) {
    /*?}*/
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }
}

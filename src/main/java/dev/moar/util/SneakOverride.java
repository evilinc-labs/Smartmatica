package dev.moar.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Thread-safe flag that forces the sneak input to be active during MC's
 * movement processing, only when the player is near a platform edge.
 *
 * Background
 * Our mod ticks at {@code END_CLIENT_TICK} — after the player's
 * movement has already been processed for that tick.  Setting
 * {@code player.setSneaking(true)} or {@code sneakKey.setPressed(true)}
 * here is pointless: the next tick's {@code KeyboardInput.tick()} reads
 * the physical keyboard state and overwrites our programmatic value to
 * {@code false} before the movement code ever sees it.
 *
 * Solution
 * A mixin on {@code KeyboardInput.tick()} (at {@code @At("TAIL")}) calls
 * {@link #shouldSneak()} after keyboard polling.  This method
 * returns {@code true} only when:
 * 
 *     - {@link #setForceSneak(boolean)} has been set to {@code true}
 *       (the printer wants edge-safe movement), AND
 *     - The player is actually near a platform edge (detected by
 *       {@link EdgeDetector#isNearEdge}).
 * 
 *
 * When the player is safely in the middle of a flat platform, sneak
 * is not engaged, allowing full walking speed for efficiency.
 *
 * Usage in {@code SchematicPrinter}:
 * 
 *   SneakOverride.setForceSneak(true);   // request edge-safe movement
 *   player.setVelocity(...);              // move toward target
 *   // ... later ...
 *   SneakOverride.setForceSneak(false);  // release
 * 
 */
public final class SneakOverride {

    private SneakOverride() {}

    private static volatile boolean forceSneak;

    /**
     * When {@code true}, sneak is forced unconditionally — bypassing
     * the {@link EdgeDetector} check.  Used during edge-work where
     * the position is inherently narrow and dangerous, but the edge
     * detector may see nearby blocks as "safe ground".
     */
    private static volatile boolean forceAbsoluteSneak;

    /** Enable/disable the edge-safe sneak request. */
    public static void setForceSneak(boolean value) {
        forceSneak = value;
    }

    /**
     * Enable/disable unconditional sneak — no edge detection check.
     * Use during edge-walking and bridging where every position is
     * inherently dangerous.
     */
    public static void setForceAbsoluteSneak(boolean value) {
        forceAbsoluteSneak = value;
    }

    /** Raw flag check — {@code true} if the printer wants edge safety. */
    public static boolean isForceSneak() {
        return forceSneak;
    }

    /** Raw flag check — {@code true} if unconditional sneak is active (edge-walking). */
    public static boolean isForceAbsoluteSneak() {
        return forceAbsoluteSneak;
    }

    /**
     * Smart sneak check called by the {@code KeyboardInputMixin}.
 *
     *     - If {@link #forceAbsoluteSneak} — always returns {@code true}
     *       (edge-walking/bridging, no edge check needed).
     *     - If {@link #forceSneak} — returns {@code true} only when the
     *       player is near a platform edge (normal building).
 *
     */
    public static boolean shouldSneak() {
        if (forceAbsoluteSneak) return true;

        if (!forceSneak) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;

        return EdgeDetector.isNearEdge(mc.player, mc.world);
    }
}

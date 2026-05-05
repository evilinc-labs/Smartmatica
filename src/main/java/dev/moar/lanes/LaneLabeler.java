package dev.moar.lanes;

import dev.moar.util.ChatHelper;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.Box;
/*?}*/

import java.util.*;

// Provides preview and run actions for lane labeling.
// Preview reports which lanes are missing labels and which item each frame would show.
// Run is still a stub for future item-frame placement support.
// When run is implemented, it should avoid overwriting filled frames and should
// require the player to carry item frames in main inventory.
public final class LaneLabeler {

    private LaneLabeler() {}

    // Print the labeling actions that would be performed.
    public static void preview(List<StorageLane> lanes) {
        if (lanes.isEmpty()) {
            ChatHelper.labelled("Lanes", "§cNo accepted lanes. Nothing to label.");
            return;
        }

        int missing = 0;
        int alreadyLabeled = 0;
        int unassigned = 0;

        ChatHelper.labelled("Lanes", "§7--- Label preview ---");
        for (StorageLane lane : lanes) {
            if (!lane.isAssigned()) {
                ChatHelper.labelled("Lanes", " §8" + lane.getName() + " §cunassigned — skipped");
                unassigned++;
                continue;
            }

            if (lane.getLabelFramePos() != null) {
                // Already has a frame — check whether it holds the correct item
                String shortItem = lane.getItemId().startsWith("minecraft:")
                        ? lane.getItemId().substring(10) : lane.getItemId();
                ChatHelper.labelled("Lanes", " §a✓ §f" + lane.getName()
                        + " §7— frame at §e"
                        + lane.getLabelFramePos().getX() + " "
                        + lane.getLabelFramePos().getY() + " "
                        + lane.getLabelFramePos().getZ()
                        + " §7(item: §f" + shortItem + "§7)");
                alreadyLabeled++;
            } else {
                // No frame detected — would need to place one
                String shortItem = lane.getItemId().startsWith("minecraft:")
                        ? lane.getItemId().substring(10) : lane.getItemId();
                ChatHelper.labelled("Lanes", " §e⚠ §f" + lane.getName()
                        + " §7— no frame detected, would place on front face with §f"
                        + shortItem);
                missing++;
            }
        }

        ChatHelper.labelled("Lanes", "§7---");
        ChatHelper.labelled("Lanes", "§7" + alreadyLabeled + " already labeled, "
                + missing + " missing, "
                + unassigned + " unassigned.");
        if (missing > 0) {
            ChatHelper.labelled("Lanes", "§7Run §f/stash lanes label run §7to place missing labels.");
            ChatHelper.labelled("Lanes", "§c⚠ Label run is not yet implemented in this version.");
        }
    }

    // Stub for the labeling run phase.
    // Automatic item-frame placement still needs safer server interaction.
    public static void run(List<StorageLane> lanes) {
        ChatHelper.labelled("Lanes",
                "§c/stash lanes label run is not yet implemented. "
                        + "Use §f/stash lanes label preview §cto inspect label state.");
    }
}

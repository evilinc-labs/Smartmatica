package dev.moar.lanes;

/*? if >=26.1 {*//*
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;
*//*?} else {*/
import net.minecraft.block.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Synchronous client-side scanner: reads block states and entities from the
 * already-loaded world to detect storage lanes without requiring the player
 * to walk anywhere.
 *
 * <p>Call {@link #scan(Object, BlockPos, BlockPos)} on the game thread with
 * the client world and region bounds.
 */
public final class LaneScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/LaneScanner");

    private LaneScanner() {}

    /**
     * Scan the given world region and return candidate {@link StorageLane} objects.
     *
     * @param world   the client world ({@code World} or {@code Level} depending on version)
     * @param min     inclusive minimum corner
     * @param max     inclusive maximum corner
     * @return ordered list of inferred lanes, sorted by priority (position in world)
     */
    public static List<StorageLane> scan(
            /*? if >=26.1 {*//*
            Level world,
            *//*?} else {*/
            World world,
            /*?}*/
            BlockPos min,
            BlockPos max) {

        LOGGER.debug("LaneScanner: scanning {} to {}", min, max);

        // ── Step 1: collect chest/barrel and hopper positions ────────────────

        Set<BlockPos> chestSet = new LinkedHashSet<>();
        Set<BlockPos> hopperSet = new LinkedHashSet<>();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();

                    if (block instanceof ChestBlock) {
                        // Canonicalise double chests: keep only one representative pos
                        // by skipping the RIGHT half (the LEFT half becomes the canonical).
                        /*? if >=26.1 {*//*
                        ChestType chestType = state.getValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.CHEST_TYPE);
                        *//*?} else {*/
                        ChestType chestType = state.get(
                                net.minecraft.state.property.Properties.CHEST_TYPE);
                        /*?}*/
                        if (chestType == ChestType.RIGHT) {
                            // Skip – the LEFT half is the canonical representation
                            continue;
                        }
                        /*? if >=26.1 {*//*
                        chestSet.add(pos.immutable());
                        *//*?} else {*/
                        chestSet.add(pos.toImmutable());
                        /*?}*/
                    } else if (block instanceof BarrelBlock) {
                        /*? if >=26.1 {*//*
                        chestSet.add(pos.immutable());
                        *//*?} else {*/
                        chestSet.add(pos.toImmutable());
                        /*?}*/
                    } else if (block instanceof HopperBlock) {
                        /*? if >=26.1 {*//*
                        hopperSet.add(pos.immutable());
                        *//*?} else {*/
                        hopperSet.add(pos.toImmutable());
                        /*?}*/
                    }
                }
            }
        }

        LOGGER.debug("LaneScanner: found {} chest/barrel, {} hopper positions",
                chestSet.size(), hopperSet.size());

        if (chestSet.isEmpty()) {
            LOGGER.debug("LaneScanner: no containers in region");
            return Collections.emptyList();
        }

        // ── Step 2: collect item frames in the region ────────────────────────

        double x1 = min.getX() - 1, y1 = min.getY() - 1, z1 = min.getZ() - 1;
        double x2 = max.getX() + 2, y2 = max.getY() + 2, z2 = max.getZ() + 2;

        /*? if >=26.1 {*//*
        AABB searchBox = new AABB(x1, y1, z1, x2, y2, z2);
        List<ItemFrame> frames = world.getEntitiesOfClass(ItemFrame.class, searchBox);
        *//*?} else {*/
        Box searchBox = new Box(x1, y1, z1, x2, y2, z2);
        List<ItemFrameEntity> frames = world.getEntitiesByClass(
                ItemFrameEntity.class, searchBox, e -> true);
        /*?}*/

        LOGGER.debug("LaneScanner: found {} item frames near region", frames.size());

        // ── Step 3: group chests into lane components ────────────────────────

        List<List<BlockPos>> components = groupIntoLanes(chestSet);

        LOGGER.debug("LaneScanner: grouped into {} lane components", components.size());

        // ── Step 4: build StorageLane objects ────────────────────────────────

        List<StorageLane> lanes = new ArrayList<>();
        int laneIndex = 0;

        for (List<BlockPos> component : components) {
            String laneName = "lane_" + (laneIndex + 1);
            StorageLane lane = new StorageLane(laneName);
            lane.setChests(component);

            // Associate hoppers: any hopper adjacent (≤1 block) to a chest in this component
            for (BlockPos hopperPos : hopperSet) {
                if (isAdjacentToAny(hopperPos, component)) {
                    lane.addInput(hopperPos);
                }
            }

            // Infer deposit mode from hoppers
            if (!lane.getInputPositions().isEmpty()) {
                lane.setDepositMode(StorageLane.DepositMode.HYBRID);
            }

            // Associate item frames: find the closest frame that is adjacent to any chest
            associateItemFrame(lane, frames);

            // Set priority = order encountered (can be overridden later)
            lane.setPriority(laneIndex);
            laneIndex++;

            lanes.add(lane);
        }

        return lanes;
    }

    // ── Lane grouping ────────────────────────────────────────────────────────

    /**
     * Group chest positions into connected components using axis-constrained
     * flood-fill.
     *
     * <p>Two chests are lane-adjacent when they lie along the same X or Z axis
     * within 2 blocks (accommodating double-chest gaps), or form a staircase
     * step (Δy = 1, Δx or Δz = 1).
     */
    private static List<List<BlockPos>> groupIntoLanes(Set<BlockPos> all) {
        Set<BlockPos> remaining = new LinkedHashSet<>(all);
        List<List<BlockPos>> groups = new ArrayList<>();

        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            remaining.remove(seed);

            List<BlockPos> component = new ArrayList<>();
            component.add(seed);

            Deque<BlockPos> frontier = new ArrayDeque<>();
            frontier.add(seed);

            while (!frontier.isEmpty()) {
                BlockPos current = frontier.poll();
                Iterator<BlockPos> it = remaining.iterator();
                while (it.hasNext()) {
                    BlockPos candidate = it.next();
                    if (laneAdjacent(current, candidate)) {
                        it.remove();
                        component.add(candidate);
                        frontier.add(candidate);
                    }
                }
            }

            // Sort component by primary axis for consistent lane ordering
            sortComponent(component);
            groups.add(component);
        }

        // Sort groups by position of their first chest for deterministic naming
        groups.sort(Comparator.comparingInt((List<BlockPos> g) -> g.get(0).getX())
                .thenComparingInt(g -> g.get(0).getY())
                .thenComparingInt(g -> g.get(0).getZ()));

        return groups;
    }

    /**
     * Two chest positions are considered lane-adjacent if they could plausibly
     * be consecutive chests in the same row or staircase.
     *
     * <p>Rules (all conditions must hold):
     * <ul>
     *   <li>X-row: same Y and Z, |ΔX| ≤ 2</li>
     *   <li>Z-row: same X and Y, |ΔZ| ≤ 2</li>
     *   <li>X-staircase: same Z, |ΔY| = 1, |ΔX| ≤ 2</li>
     *   <li>Z-staircase: same X, |ΔY| = 1, |ΔZ| ≤ 2</li>
     * </ul>
     * Diagonal adjacency (ΔX > 0 AND ΔZ > 0) is intentionally excluded to
     * prevent merging parallel lanes.
     */
    private static boolean laneAdjacent(BlockPos a, BlockPos b) {
        int dx = Math.abs(b.getX() - a.getX());
        int dy = Math.abs(b.getY() - a.getY());
        int dz = Math.abs(b.getZ() - a.getZ());

        // Horizontal rows (same Y plane)
        if (dy == 0) {
            if (dz == 0 && dx >= 1 && dx <= 2) return true;  // X-row
            if (dx == 0 && dz >= 1 && dz <= 2) return true;  // Z-row
        }
        // Staircase (one Y step per horizontal step)
        if (dy == 1) {
            if (dz == 0 && dx >= 1 && dx <= 2) return true;  // X-staircase
            if (dx == 0 && dz >= 1 && dz <= 2) return true;  // Z-staircase
        }
        return false;
    }

    /** Sort a lane component along its dominant axis (the one with highest variance). */
    private static void sortComponent(List<BlockPos> component) {
        if (component.size() <= 1) return;

        int xSpan = maxCoord(component, 0) - minCoord(component, 0);
        int ySpan = maxCoord(component, 1) - minCoord(component, 1);
        int zSpan = maxCoord(component, 2) - minCoord(component, 2);

        if (xSpan >= zSpan && xSpan >= ySpan) {
            component.sort(Comparator.<BlockPos>comparingInt(p -> (int) p.getX())
                    .thenComparingInt(p -> (int) p.getY())
                    .thenComparingInt(p -> (int) p.getZ()));
        } else if (zSpan >= xSpan && zSpan >= ySpan) {
            component.sort(Comparator.<BlockPos>comparingInt(p -> (int) p.getZ())
                    .thenComparingInt(p -> (int) p.getX())
                    .thenComparingInt(p -> (int) p.getY()));
        } else {
            // Y-primary (staircase or vertical column)
            component.sort(Comparator.<BlockPos>comparingInt(p -> (int) p.getY())
                    .thenComparingInt(p -> (int) p.getX())
                    .thenComparingInt(p -> (int) p.getZ()));
        }
    }

    private static int minCoord(List<BlockPos> list, int axis) {
        return list.stream()
                .mapToInt(p -> axis == 0 ? (int) p.getX() : axis == 1 ? (int) p.getY() : (int) p.getZ())
                .min().orElse(0);
    }

    private static int maxCoord(List<BlockPos> list, int axis) {
        return list.stream()
                .mapToInt(p -> axis == 0 ? (int) p.getX() : axis == 1 ? (int) p.getY() : (int) p.getZ())
                .max().orElse(0);
    }

    // ── Item frame association ───────────────────────────────────────────────

    /**
     * Find the best item frame for this lane and read its assigned item.
     * A frame is a candidate if its block position is within 1 block of any
     * chest in the lane.
     */
    private static void associateItemFrame(
            StorageLane lane,
            /*? if >=26.1 {*//*
            List<ItemFrame> frames
            *//*?} else {*/
            List<ItemFrameEntity> frames
            /*?}*/
    ) {
        List<BlockPos> chests = lane.getChestPositions();

        /*? if >=26.1 {*//*
        ItemFrame bestFrame = null;
        *//*?} else {*/
        ItemFrameEntity bestFrame = null;
        /*?}*/
        int bestScore = -1;

        for (var frame : frames) {
            /*? if >=26.1 {*//*
            BlockPos fPos = frame.blockPosition();
            net.minecraft.world.item.ItemStack held = frame.getItem();
            *//*?} else {*/
            BlockPos fPos = frame.getBlockPos();
            net.minecraft.item.ItemStack held = frame.getHeldItemStack();
            /*?}*/

            // Check adjacency to any chest in this lane
            boolean adjacent = false;
            for (BlockPos chest : chests) {
                if (blockAdjacent(fPos, chest)) {
                    adjacent = true;
                    break;
                }
            }
            if (!adjacent) continue;

            // Prefer frames that already hold an item
            int score = held.isEmpty() ? 0 : 1;
            if (score > bestScore) {
                bestScore = score;
                bestFrame = frame;
            }
        }

        if (bestFrame != null) {
            /*? if >=26.1 {*//*
            BlockPos fPos = bestFrame.blockPosition();
            net.minecraft.world.item.ItemStack held = bestFrame.getItem();
            *//*?} else {*/
            BlockPos fPos = bestFrame.getBlockPos();
            net.minecraft.item.ItemStack held = bestFrame.getHeldItemStack();
            /*?}*/
            /*? if >=26.1 {*//*
            lane.setLabelFramePos(fPos.immutable());
            *//*?} else {*/
            lane.setLabelFramePos(fPos.toImmutable());
            /*?}*/
            if (!held.isEmpty()) {
                /*? if >=26.1 {*//*
                String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                *//*?} else {*/
                String itemId = Registries.ITEM.getId(held.getItem()).toString();
                /*?}*/
                lane.setItemId(itemId);
            }
        }
    }

    // ── Geometry helpers ─────────────────────────────────────────────────────

    /**
     * True when {@code probe} is within 1 block (Chebyshev distance) of
     * {@code target} on at least one axis while the others are zero or one.
     * This matches the 6 direct faces plus the 12 edge positions.
     */
    private static boolean blockAdjacent(BlockPos probe, BlockPos target) {
        int dx = Math.abs(probe.getX() - target.getX());
        int dy = Math.abs(probe.getY() - target.getY());
        int dz = Math.abs(probe.getZ() - target.getZ());
        return dx <= 1 && dy <= 1 && dz <= 1 && (dx + dy + dz) >= 1;
    }

    /** True when {@code probe} is within 1 block of any position in {@code group}. */
    private static boolean isAdjacentToAny(BlockPos probe, List<BlockPos> group) {
        for (BlockPos p : group) {
            if (blockAdjacent(probe, p)) return true;
        }
        return false;
    }
}

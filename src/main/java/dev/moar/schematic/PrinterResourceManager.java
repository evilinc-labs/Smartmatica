package dev.moar.schematic;

import dev.moar.MoarMod;
import dev.moar.util.PrinterDatabase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Manages supply-chest interactions and materials analysis for AutoBuild.
 *
 * Supply-chest positions and inventory snapshots are managed by
 * {@link dev.moar.chest.ChestManager}.  This class provides
 * higher-level operations like materials analysis that layer on top.
 */
public final class PrinterResourceManager {

    private PrinterResourceManager() {}

    // ── delegated supply-chest API (kept for backward compat) ───────────

    public static boolean addSupplyChest(BlockPos pos) {
        return MoarMod.getChestManager().addSupplyChest(pos);
    }

    public static boolean removeSupplyChest(BlockPos pos) {
        return MoarMod.getChestManager().removeSupplyChest(pos);
    }

    public static void clearSupplyChests() {
        MoarMod.getChestManager().clearSupplyChests();
    }

    public static List<BlockPos> getSupplyChests() {
        return MoarMod.getChestManager().getSupplyPositions();
    }

    public static int supplyChestCount() {
        return MoarMod.getChestManager().supplyChestCount();
    }

    /** How few items trigger a restock run. */
    public static final int MIN_SUPPLY_ITEMS = 16;

    public static BlockPos findBestSupplyChest(
            BlockPos from, Set<String> neededItemIds, World world) {
        return MoarMod.getChestManager().findBestChest(from, neededItemIds);
    }

    /** Load persisted data from disk (supply chest positions + scaffold). */
    public static void load() {
        MoarMod.getChestManager().loadSupplyChests();
        PrinterDatabase.loadScaffold();
    }

    /** Save supply-chest positions to disk. */
    public static void save() {
        MoarMod.getChestManager().saveSupplyChests();
    }

    // ── materials analysis ──────────────────────────────────────────────

    /**
     * Computes the full bill of materials for a schematic: what's needed,
     * what's already placed, and what's missing.
     */
    public static MaterialsReport analyzeMaterials(
            LitematicaSchematic schematic,
            BlockPos anchor,
            World world) {
        if (schematic == null || world == null) return MaterialsReport.EMPTY;

        Map<String, Integer> unknownBlocks = schematic.hasUnknownBlocks()
                ? new HashMap<>(schematic.getUnknownBlocks())
                : Map.of();
        int unknownTotal = unknownBlocks.values().stream().mapToInt(Integer::intValue).sum();

        // 1. Count all required blocks from schematic
        Map<String, Integer> required = new HashMap<>();
        int totalBlocks = 0;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;

                        // Fluid source blocks (water/lava) need buckets
                        if (target.getBlock() instanceof FluidBlock
                                && target.getFluidState().isStill()) {
                            Item bucket = fluidBucketItem(target);
                            if (bucket != null) {
                                String itemId = Registries.ITEM.getId(bucket).toString();
                                required.merge(itemId, 1, Integer::sum);
                                totalBlocks++;
                            }
                            continue;
                        }

                        Item item = target.getBlock().asItem();
                        if (item == Items.AIR) continue;

                        String itemId = Registries.ITEM.getId(item).toString();
                        required.merge(itemId, 1, Integer::sum);
                        totalBlocks++;
                    }
                }
            }
        }

        totalBlocks += unknownTotal;

        // 2. Count already-placed blocks
        Map<String, Integer> placed = new HashMap<>();
        int placedCount = 0;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;

                        BlockState current = world.getBlockState(new BlockPos(wx, wy, wz));

                        // Fluid source blocks — check if same fluid is already present
                        if (target.getBlock() instanceof FluidBlock
                                && target.getFluidState().isStill()) {
                            if (current.getBlock() == target.getBlock()
                                    && current.getFluidState().isStill()) {
                                Item bucket = fluidBucketItem(target);
                                if (bucket != null) {
                                    String itemId = Registries.ITEM.getId(bucket).toString();
                                    placed.merge(itemId, 1, Integer::sum);
                                    placedCount++;
                                }
                            }
                            continue;
                        }

                        // Use block-type matching: same block present counts as placed.
                        // Dynamic/neighbor-computed properties (chest type, stair shape,
                        // fence connections, etc.) don't affect the placed count.
                        if (current.getBlock() == target.getBlock() && !current.isAir()) {
                            Item item = target.getBlock().asItem();
                            if (item != Items.AIR) {
                                String itemId = Registries.ITEM.getId(item).toString();
                                placed.merge(itemId, 1, Integer::sum);
                                placedCount++;
                            }
                        }
                    }
                }
            }
        }

        // 3. Compute supply inventory from indexed chests
        Map<String, Integer> inSupply = MoarMod.getChestManager().getCombinedInventory();

        // 4. Compute missing = required - placed
        Map<String, Integer> missing = new HashMap<>();
        for (var entry : required.entrySet()) {
            int need = entry.getValue();
            int have = placed.getOrDefault(entry.getKey(), 0);
            int diff = need - have;
            if (diff > 0) {
                missing.put(entry.getKey(), diff);
            }
        }

        // 5. Compute stillNeeded = missing - inSupply
        Map<String, Integer> stillNeeded = new HashMap<>();
        for (var entry : missing.entrySet()) {
            int need = entry.getValue();
            int supply = inSupply.getOrDefault(entry.getKey(), 0);
            int diff = need - supply;
            if (diff > 0) {
                stillNeeded.put(entry.getKey(), diff);
            }
        }

        return new MaterialsReport(
                required, placed, missing,
                inSupply, stillNeeded,
                unknownBlocks, totalBlocks, placedCount
        );
    }

    // ── materials report record ─────────────────────────────────────────

    public record MaterialsReport(
            Map<String, Integer> required,
            Map<String, Integer> placed,
            Map<String, Integer> missing,
            Map<String, Integer> inSupply,
            Map<String, Integer> stillNeeded,
            Map<String, Integer> unknownBlocks,
            int totalBlocks,
            int placedBlocks
    ) {
        public static final MaterialsReport EMPTY = new MaterialsReport(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0, 0
        );

        public boolean hasUnknownBlocks() { return !unknownBlocks.isEmpty(); }

        public int unknownCount() {
            return unknownBlocks.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int percentComplete() {
            return totalBlocks > 0 ? (placedBlocks * 100 / totalBlocks) : 0;
        }

        public int missingTypes() {
            return stillNeeded.size();
        }

        public int missingCount() {
            return stillNeeded.values().stream().mapToInt(Integer::intValue).sum();
        }

        /** Prettified item ID: "minecraft:oak_planks" → "Oak Planks" */
        public static String prettyName(String itemId) {
            String path = itemId.contains(":") ? itemId.split(":", 2)[1] : itemId;
            String[] parts = path.split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
            return sb.toString();
        }
    }



    // ── fluid helpers ───────────────────────────────────────────────────

    /**
     * Returns the bucket item needed to place the given fluid block,
     * or {@code null} if the block is not a supported fluid.
     */
    private static Item fluidBucketItem(BlockState state) {
        if (state.getBlock() == Blocks.WATER) return Items.WATER_BUCKET;
        if (state.getBlock() == Blocks.LAVA) return Items.LAVA_BUCKET;
        return null;
    }
}

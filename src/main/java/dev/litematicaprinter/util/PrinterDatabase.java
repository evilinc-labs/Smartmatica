package dev.litematicaprinter.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Centralised database for the Smartmatica mod.
 *
 * <h2>Tables</h2>
 * <ol>
 *   <li><b>Chest index table</b> — registered supply-chest positions
 *       (persisted to disk) and their cached inventory snapshots
 *       (in-memory only, rebuilt each session by opening chests).</li>
 * </ol>
 *
 * <p>Supply-chest positions are persisted to
 * {@code config/smartmatica/printer_supply.json}.
 * Chest snapshots are ephemeral (in-memory only).
 */
public final class PrinterDatabase {

    private PrinterDatabase() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Smartmatica");

    // ═══════════════════════════════════════════════════════════════════
    //  SCAFFOLD TRACKING — blocks placed by Baritone during pathfinding
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Map of scaffold block positions → block item ID (e.g.
     * {@code "minecraft:cobblestone"}).  Baritone places these via
     * {@code allowPlace} pathfinding; they are non-schematic blocks
     * that should be cleaned up after the build completes.
     *
     * <p>Persisted to {@link #SCAFFOLD_FILE} so scaffold data survives
     * game restarts and the cleanup job can resume.
     */
    private static final Map<BlockPos, String> scaffoldTable = new LinkedHashMap<>();

    private static final Path SCAFFOLD_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("smartmatica")
            .resolve("printer_scaffold.json");

    /** True when the scaffold table has been modified since the last
     *  disk write.  Checked by {@link #flushScaffoldIfDirty()}. */
    private static boolean scaffoldDirty;

    /**
     * Record a scaffold block position together with its block item ID.
     *
     * @param pos    world position
     * @param itemId registry ID of the block's item form
     *               (e.g. {@code "minecraft:cobblestone"})
     */
    public static void addScaffold(BlockPos pos, String itemId) {
        scaffoldTable.put(pos.toImmutable(), itemId);
        scaffoldDirty = true;
    }

    public static void removeScaffold(BlockPos pos) {
        if (scaffoldTable.remove(pos) != null) {
            scaffoldDirty = true;
        }
    }

    /** Remove multiple scaffold positions in one batch. */
    public static void removeScaffoldBatch(Collection<BlockPos> positions) {
        boolean changed = false;
        for (BlockPos pos : positions) {
            if (scaffoldTable.remove(pos) != null) changed = true;
        }
        if (changed) scaffoldDirty = true;
    }

    /** Write scaffold data to disk if modified since last write. */
    public static void flushScaffoldIfDirty() {
        if (scaffoldDirty) {
            saveScaffold();
            scaffoldDirty = false;
        }
    }

    public static boolean hasScaffold() {
        return !scaffoldTable.isEmpty();
    }

    public static int scaffoldCount() {
        return scaffoldTable.size();
    }

    public static boolean isScaffold(BlockPos pos) {
        return scaffoldTable.containsKey(pos);
    }

    /**
     * Returns the stored block item ID for a scaffold position, or
     * {@code null} if the position is not tracked.
     */
    public static String getScaffoldBlockId(BlockPos pos) {
        return scaffoldTable.get(pos);
    }

    /** Unmodifiable view of all scaffold entries (position → item ID). */
    public static Map<BlockPos, String> getScaffoldEntries() {
        return Collections.unmodifiableMap(scaffoldTable);
    }

    /** Ordered stream of scaffold positions (insertion order). */
    public static java.util.stream.Stream<BlockPos> scaffoldStream() {
        return scaffoldTable.keySet().stream();
    }

    /** Clear all tracked scaffold positions and delete the file. */
    public static void clearScaffold() {
        scaffoldTable.clear();
        scaffoldDirty = false;
        saveScaffold();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CHEST INDEX (positions + inventory snapshots)
    // ═══════════════════════════════════════════════════════════════════

    // ── 2a: supply chest positions (persisted) ──────────────────────────

    private static final Set<BlockPos> chestPositions = new LinkedHashSet<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CHEST_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("smartmatica")
            .resolve("printer_supply.json");

    /** Register a chest position. Returns false if already registered. */
    public static boolean addChest(BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        if (!chestPositions.add(immutable)) return false;
        saveChests();
        return true;
    }

    public static boolean removeChest(BlockPos pos) {
        BlockPos immutable = pos.toImmutable();
        boolean removed = chestPositions.remove(immutable);
        if (removed) {
            chestSnapshots.remove(immutable);
            saveChests();
        }
        return removed;
    }

    /** Remove all chest registrations and their snapshots. */
    public static void clearChests() {
        chestPositions.clear();
        chestSnapshots.clear();
        saveChests();
    }

    /** Unmodifiable view of all registered chest positions. */
    public static List<BlockPos> getChestPositions() {
        return List.copyOf(chestPositions);
    }

    public static int chestCount() {
        return chestPositions.size();
    }

    // ── 2b: inventory snapshots (in-memory) ─────────────────────────────

    /**
     * Snapshot of a single supply chest's contents at the time it was last
     * opened.  Includes items directly in the chest and items found inside
     * shulker boxes within it.
     */
    public record ChestSnapshot(
            BlockPos pos,
            /** Item ID → total count (direct + shulker contents) */
            Map<String, Integer> items,
            /** Number of shulker boxes found in this chest */
            int shulkerCount,
            /** System.currentTimeMillis() when this snapshot was taken */
            long timestamp
    ) {
        public boolean contains(String itemId) {
            return items.containsKey(itemId);
        }

        public int getCount(String itemId) {
            return items.getOrDefault(itemId, 0);
        }

        /** Seconds since this snapshot was taken. */
        public long ageSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }
    }

    /** BlockPos → last-known inventory snapshot.  Capped at 256 entries
     *  (evicts the oldest snapshot when full). */
    private static final int MAX_SNAPSHOTS = 256;
    private static final Map<BlockPos, ChestSnapshot> chestSnapshots = new LinkedHashMap<>(32, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, ChestSnapshot> eldest) {
            return size() > MAX_SNAPSHOTS;
        }
    };

    public static void putSnapshot(BlockPos pos, ChestSnapshot snapshot) {
        chestSnapshots.put(pos.toImmutable(), snapshot);
    }

    /** Get the cached snapshot for a chest, or null if not scanned. */
    public static ChestSnapshot getSnapshot(BlockPos pos) {
        return chestSnapshots.get(pos.toImmutable());
    }

    public static void invalidateSnapshot(BlockPos pos) {
        chestSnapshots.remove(pos.toImmutable());
    }

    /** Clear all snapshots (positions are retained). */
    public static void clearSnapshots() {
        chestSnapshots.clear();
    }

    /**
     * Find the best supply chest for a set of needed item IDs.
     *
     * <p>Ranking: indexed chests with matching items (by match count
     * then distance) → unindexed chests (by distance) → indexed chests
     * with no matches (by distance, snapshot may be stale).
     */
    public static BlockPos findBestChest(BlockPos from, Set<String> neededItemIds) {
        return findBestChest(from, neededItemIds, Collections.emptySet());
    }

    /**
     * Same as {@link #findBestChest(BlockPos, Set)} but excludes positions
     * in {@code exclude} (e.g. chests that Baritone couldn't reach).
     */
    public static BlockPos findBestChest(BlockPos from, Set<String> neededItemIds,
                                         Set<BlockPos> exclude) {
        if (chestPositions.isEmpty()) return null;
        if (neededItemIds.isEmpty()) return nearestChest(from);

        BlockPos bestIndexed = null;
        int bestMatchCount = 0;
        double bestIndexedDist = Double.MAX_VALUE;

        BlockPos bestUnindexed = null;
        double bestUnindexedDist = Double.MAX_VALUE;

        BlockPos bestFallback = null;
        double bestFallbackDist = Double.MAX_VALUE;

        for (BlockPos pos : chestPositions) {
            if (exclude.contains(pos)) continue;
            double dist = from.getSquaredDistance(pos);
            ChestSnapshot snapshot = chestSnapshots.get(pos);

            if (snapshot == null) {
                if (dist < bestUnindexedDist) {
                    bestUnindexedDist = dist;
                    bestUnindexed = pos;
                }
            } else {
                int matchCount = 0;
                for (String needed : neededItemIds) {
                    if (snapshot.contains(needed)) matchCount++;
                }
                if (matchCount > 0) {
                    if (matchCount > bestMatchCount
                            || (matchCount == bestMatchCount && dist < bestIndexedDist)) {
                        bestMatchCount = matchCount;
                        bestIndexedDist = dist;
                        bestIndexed = pos;
                    }
                } else {
                    if (dist < bestFallbackDist) {
                        bestFallbackDist = dist;
                        bestFallback = pos;
                    }
                }
            }
        }

        if (bestIndexed != null) return bestIndexed;
        if (bestUnindexed != null) return bestUnindexed;
        return bestFallback;
    }

    /**
     * Combined inventory across all indexed supply chests.
     *
     * @return itemId → total count
     */
    public static Map<String, Integer> getCombinedChestInventory() {
        Map<String, Integer> combined = new HashMap<>();
        for (ChestSnapshot snapshot : chestSnapshots.values()) {
            for (var entry : snapshot.items().entrySet()) {
                combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return combined;
    }

    /**
     * Summary of the chest index state.
     */
    public record ChestIndexSummary(
            int indexedChests,
            int unindexedChests,
            int totalItems,
            int totalItemTypes,
            int totalShulkers
    ) {
        public int totalChests() {
            return indexedChests + unindexedChests;
        }
    }

    /** Build a summary of the chest index. */
    public static ChestIndexSummary getChestIndexSummary() {
        int indexed = 0;
        int unindexed = 0;
        int totalItems = 0;
        int totalShulkers = 0;
        Set<String> allTypes = new HashSet<>();

        for (BlockPos pos : chestPositions) {
            ChestSnapshot snapshot = chestSnapshots.get(pos);
            if (snapshot != null) {
                indexed++;
                totalShulkers += snapshot.shulkerCount();
                for (var entry : snapshot.items().entrySet()) {
                    totalItems += entry.getValue();
                    allTypes.add(entry.getKey());
                }
            } else {
                unindexed++;
            }
        }
        return new ChestIndexSummary(indexed, unindexed, totalItems, allTypes.size(), totalShulkers);
    }

    public static BlockPos nearestChest(BlockPos from) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : chestPositions) {
            double dist = from.getSquaredDistance(pos);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        return nearest;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PERSISTENCE — supply chest positions
    // ═══════════════════════════════════════════════════════════════════

    /** Load supply-chest positions from disk. */
    public static void loadChests() {
        try {
            if (!Files.exists(CHEST_FILE)) return;
            try (Reader reader = Files.newBufferedReader(CHEST_FILE)) {
                SavedChestData data = GSON.fromJson(reader, SavedChestData.class);
                if (data == null || data.positions == null) return;
                chestPositions.clear();
                for (int[] pos : data.positions) {
                    if (pos.length >= 3) {
                        chestPositions.add(new BlockPos(pos[0], pos[1], pos[2]));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load supply chests", e);
        }
    }

    /** Save supply-chest positions to disk. */
    public static void saveChests() {
        try {
            Files.createDirectories(CHEST_FILE.getParent());
            SavedChestData data = new SavedChestData();
            data.positions = new ArrayList<>();
            for (BlockPos pos : chestPositions) {
                data.positions.add(new int[] { pos.getX(), pos.getY(), pos.getZ() });
            }
            try (Writer writer = Files.newBufferedWriter(CHEST_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save supply chests", e);
        }
    }

    private static class SavedChestData {
        List<int[]> positions;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PERSISTENCE — scaffold blocks
    // ═══════════════════════════════════════════════════════════════════

    /** Load scaffold entries from disk. */
    public static void loadScaffold() {
        try {
            if (!Files.exists(SCAFFOLD_FILE)) return;
            try (Reader reader = Files.newBufferedReader(SCAFFOLD_FILE)) {
                SavedScaffoldData data = GSON.fromJson(reader, SavedScaffoldData.class);
                if (data == null || data.entries == null) return;
                scaffoldTable.clear();
                for (ScaffoldEntry entry : data.entries) {
                    if (entry.pos != null && entry.pos.length >= 3
                            && entry.blockId != null) {
                        scaffoldTable.put(
                                new BlockPos(entry.pos[0], entry.pos[1], entry.pos[2]),
                                entry.blockId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load scaffold data", e);
        }
    }

    /** Save scaffold entries to disk. */
    public static void saveScaffold() {
        try {
            Files.createDirectories(SCAFFOLD_FILE.getParent());
            SavedScaffoldData data = new SavedScaffoldData();
            data.entries = new ArrayList<>();
            for (var entry : scaffoldTable.entrySet()) {
                ScaffoldEntry se = new ScaffoldEntry();
                se.pos = new int[] {
                        entry.getKey().getX(),
                        entry.getKey().getY(),
                        entry.getKey().getZ()
                };
                se.blockId = entry.getValue();
                data.entries.add(se);
            }
            try (Writer writer = Files.newBufferedWriter(SCAFFOLD_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save scaffold data", e);
        }
    }

    private static class SavedScaffoldData {
        List<ScaffoldEntry> entries;
    }

    private static class ScaffoldEntry {
        int[] pos;
        String blockId;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  GLOBAL OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Dump (clear) all build-session data — scaffold + chest snapshots.
     * Called when a build is completed or the printer is fully reset.
     * Supply-chest positions are <b>not</b> cleared (they're persistent
     * configuration, not per-build data).
     */
    public static void dumpBuildData() {
        clearScaffold();
        chestSnapshots.clear();
    }
}

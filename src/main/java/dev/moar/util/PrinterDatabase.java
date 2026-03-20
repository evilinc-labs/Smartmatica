package dev.moar.util;

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
 * Centralised database for scaffold tracking in the MOAR mod.
 *
 * Scaffold block positions are persisted to
 * {@code config/moar/printer_scaffold.json}.
 *
 * Supply-chest management has been moved to
 * {@link dev.moar.chest.ChestManager}.
 */
public final class PrinterDatabase {

    private PrinterDatabase() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ═══════════════════════════════════════════════════════════════════
    //  SCAFFOLD TRACKING — blocks placed by Baritone during pathfinding
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Map of scaffold block positions → block item ID (e.g.
     * {@code "minecraft:cobblestone"}).  Baritone places these via
     * {@code allowPlace} pathfinding; they are non-schematic blocks
     * that should be cleaned up after the build completes.
     *
     * Persisted to {@link #SCAFFOLD_FILE} so scaffold data survives
     * game restarts and the cleanup job can resume.
     */
    private static final Map<BlockPos, String> scaffoldTable = new LinkedHashMap<>();

    private static final Path SCAFFOLD_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moar")
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
}

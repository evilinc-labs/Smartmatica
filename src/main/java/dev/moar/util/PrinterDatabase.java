package dev.moar.util;

import dev.moar.MoarMod;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

// Scaffold tracking (persisted to SQLite via StashDatabase).
public final class PrinterDatabase {

    private PrinterDatabase() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    // Scaffold tracking — LRU map of blocks placed during pathfinding (4096 cap).
    private static final int MAX_SCAFFOLD_ENTRIES = 4096;
    private static final Map<BlockPos, String> scaffoldTable = new LinkedHashMap<>(256, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, String> eldest) {
            return size() > MAX_SCAFFOLD_ENTRIES;
        }
    };

    /** True when scaffold table modified since last DB write. */
    private static boolean scaffoldDirty;

    /** Record a scaffold block position with its block item ID. */
    public static void addScaffold(BlockPos pos, String itemId) {
        /*? if >=26.1 {*//*
        scaffoldTable.put(pos.immutable(), itemId);
        *//*?} else {*/
        scaffoldTable.put(pos.toImmutable(), itemId);
        /*?}*/
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

    /** Block item ID for a scaffold position, or null. */
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

    /** Clear all tracked scaffold positions and save. */
    public static void clearScaffold() {
        scaffoldTable.clear();
        scaffoldDirty = false;
        saveScaffold();
    }

    // --- PERSISTENCE — scaffold blocks (SQLite via StashDatabase)

    /** Load scaffold entries from the database. */
    public static void loadScaffold() {
        var db = MoarMod.getDatabase();
        if (!db.isOpen()) return;
        Map<BlockPos, String> saved = db.loadScaffold();
        if (!saved.isEmpty()) {
            scaffoldTable.clear();
            scaffoldTable.putAll(saved);
        }
    }

    /** Save scaffold entries to the database. */
    public static void saveScaffold() {
        var db = MoarMod.getDatabase();
        if (db.isOpen()) db.saveScaffold(scaffoldTable);
    }
}

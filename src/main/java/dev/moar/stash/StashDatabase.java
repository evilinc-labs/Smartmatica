package dev.moar.stash;

import dev.moar.lanes.StorageLane;
import dev.moar.stash.StashManager.ContainerEntry;
import dev.moar.stash.StashManager.ShulkerDetail;
import net.fabricmc.loader.api.FabricLoader;
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

// SQLite storage for all mod data (config/moar/stash.db).
public final class StashDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/StashDB");

    private static final Path DB_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moar")
            .resolve("stash.db");

    private Connection connection;

    // Lifecycle

    /** Open (or create) the database and ensure tables exist. */
    public void open() {
        close(); // release any existing connection first
        try {
            // Load SQLite JDBC (Fabric classloader not visible to DriverManager)
            Class.forName("org.sqlite.JDBC");

            Files.createDirectories(DB_PATH.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH.toAbsolutePath());
            connection.setAutoCommit(false);
            createTables();
            LOGGER.info("Stash database opened: {}", DB_PATH);
        } catch (Exception e) {
            LOGGER.error("Failed to open stash database", e);
        }
    }

    /** Close the database connection. */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.error("Failed to close stash database", e);
            }
            connection = null;
        }
    }

    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // Schema

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS containers (
                    x         INTEGER NOT NULL,
                    y         INTEGER NOT NULL,
                    z         INTEGER NOT NULL,
                    block_type TEXT   NOT NULL,
                    is_double  INTEGER NOT NULL DEFAULT 0,
                    shulker_count INTEGER NOT NULL DEFAULT 0,
                    timestamp  INTEGER NOT NULL,
                    label      TEXT    DEFAULT NULL,
                    PRIMARY KEY (x, y, z)
                )
                """);

            // Add label column if upgrading from older schema
            migrateAddLabel(stmt);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS items (
                    x        INTEGER NOT NULL,
                    y        INTEGER NOT NULL,
                    z        INTEGER NOT NULL,
                    item_id  TEXT    NOT NULL,
                    quantity INTEGER NOT NULL,
                    PRIMARY KEY (x, y, z, item_id),
                    FOREIGN KEY (x, y, z) REFERENCES containers(x, y, z) ON DELETE CASCADE
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS shulkers (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    x            INTEGER NOT NULL,
                    y            INTEGER NOT NULL,
                    z            INTEGER NOT NULL,
                    shulker_type TEXT    NOT NULL,
                    FOREIGN KEY (x, y, z) REFERENCES containers(x, y, z) ON DELETE CASCADE
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS shulker_items (
                    shulker_id INTEGER NOT NULL,
                    item_id    TEXT    NOT NULL,
                    quantity   INTEGER NOT NULL,
                    PRIMARY KEY (shulker_id, item_id),
                    FOREIGN KEY (shulker_id) REFERENCES shulkers(id) ON DELETE CASCADE
                )
                """);

            // Index for fast item lookups across all containers
            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_items_item_id ON items(item_id)
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS regions (
                    name       TEXT    PRIMARY KEY,
                    x1         INTEGER NOT NULL,
                    y1         INTEGER NOT NULL,
                    z1         INTEGER NOT NULL,
                    x2         INTEGER NOT NULL,
                    y2         INTEGER NOT NULL,
                    z2         INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);

            // Printer / ChestManager tables

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS supply_chests (
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY (x, y, z)
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS dump_chests (
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY (x, y, z)
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS scaffold (
                    x       INTEGER NOT NULL,
                    y       INTEGER NOT NULL,
                    z       INTEGER NOT NULL,
                    item_id TEXT    NOT NULL,
                    PRIMARY KEY (x, y, z)
                )
                """);

            // Sorting config tables

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS storage_chests (
                    x          INTEGER NOT NULL,
                    y          INTEGER NOT NULL,
                    z          INTEGER NOT NULL,
                    sort_order INTEGER NOT NULL,
                    item_type  TEXT    DEFAULT NULL,
                    is_overflow INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (x, y, z)
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS keep_items (
                    item_id TEXT PRIMARY KEY
                )
                """);

            // SpawnProofer tables

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS spawnproofer_supply (
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    PRIMARY KEY (x, y, z)
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS config (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS kits (
                    name       TEXT PRIMARY KEY,
                    created_at INTEGER NOT NULL
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS kit_items (
                    kit_name TEXT    NOT NULL,
                    item_id  TEXT    NOT NULL,
                    quantity INTEGER NOT NULL,
                    PRIMARY KEY (kit_name, item_id),
                    FOREIGN KEY (kit_name) REFERENCES kits(name) ON DELETE CASCADE
                )
                """);

            // Storage-lane tables
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lane_config (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    name              TEXT    NOT NULL,
                    item_id           TEXT    DEFAULT NULL,
                    frame_x           INTEGER DEFAULT NULL,
                    frame_y           INTEGER DEFAULT NULL,
                    frame_z           INTEGER DEFAULT NULL,
                    front_face        TEXT    DEFAULT NULL,
                    deposit_mode      TEXT    NOT NULL DEFAULT 'DIRECT_FILL',
                    priority          INTEGER NOT NULL DEFAULT 0,
                    overflow_behavior TEXT    NOT NULL DEFAULT 'SKIP',
                    created_at        INTEGER NOT NULL
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lane_chests (
                    lane_id INTEGER NOT NULL,
                    seq     INTEGER NOT NULL,
                    x       INTEGER NOT NULL,
                    y       INTEGER NOT NULL,
                    z       INTEGER NOT NULL,
                    PRIMARY KEY (lane_id, seq),
                    FOREIGN KEY (lane_id) REFERENCES lane_config(id) ON DELETE CASCADE
                )
                """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lane_inputs (
                    lane_id INTEGER NOT NULL,
                    seq     INTEGER NOT NULL,
                    x       INTEGER NOT NULL,
                    y       INTEGER NOT NULL,
                    z       INTEGER NOT NULL,
                    PRIMARY KEY (lane_id, seq),
                    FOREIGN KEY (lane_id) REFERENCES lane_config(id) ON DELETE CASCADE
                )
                """);

            migrateAddLaneFrontFace(stmt);
        }
        connection.commit();
    }

    /** Safely add the label column to an existing containers table. */
    private void migrateAddLabel(Statement stmt) {
        try {
            stmt.executeUpdate("ALTER TABLE containers ADD COLUMN label TEXT DEFAULT NULL");
        } catch (SQLException ignored) {
            // Column already exists — expected on non-first run
        }
    }

    /** Safely add the front_face column to lane_config for existing databases. */
    private void migrateAddLaneFrontFace(Statement stmt) {
        try {
            stmt.executeUpdate("ALTER TABLE lane_config ADD COLUMN front_face TEXT DEFAULT NULL");
        } catch (SQLException ignored) {
            // Column already exists — expected on non-first run
        }
    }

    // Region operations

    /** Save a named region (upsert). */
    public void saveRegion(String name, BlockPos corner1, BlockPos corner2) {
        if (!isOpen()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO regions (name, x1, y1, z1, x2, y2, z2, created_at) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, name);
            ps.setInt(2, corner1.getX());
            ps.setInt(3, corner1.getY());
            ps.setInt(4, corner1.getZ());
            ps.setInt(5, corner2.getX());
            ps.setInt(6, corner2.getY());
            ps.setInt(7, corner2.getZ());
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to save region '{}'", name, e);
            rollback();
        }
    }

    /** Load a named region. Returns [corner1, corner2] or null if not found. */
    public BlockPos[] loadRegion(String name) {
        if (!isOpen()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT x1, y1, z1, x2, y2, z2 FROM regions WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BlockPos c1 = new BlockPos(rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"));
                    BlockPos c2 = new BlockPos(rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2"));
                    return new BlockPos[]{c1, c2};
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load region '{}'", name, e);
        }
        return null;
    }

    /** List all saved region names. */
    public List<String> listRegions() {
        List<String> names = new ArrayList<>();
        if (!isOpen()) return names;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM regions ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to list regions", e);
        }
        return names;
    }

    /** Get all regions with their bounds. */
    public Map<String, BlockPos[]> loadAllRegions() {
        Map<String, BlockPos[]> result = new LinkedHashMap<>();
        if (!isOpen()) return result;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, x1, y1, z1, x2, y2, z2 FROM regions ORDER BY name")) {
            while (rs.next()) {
                BlockPos c1 = new BlockPos(rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"));
                BlockPos c2 = new BlockPos(rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2"));
                result.put(rs.getString("name"), new BlockPos[]{c1, c2});
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load all regions", e);
        }
        return result;
    }

    /** Delete a named region. Returns true if a row was deleted. */
    public boolean deleteRegion(String name) {
        if (!isOpen()) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM regions WHERE name=?")) {
            ps.setString(1, name);
            int rows = ps.executeUpdate();
            connection.commit();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to delete region '{}'", name, e);
            rollback();
            return false;
        }
    }

    // Label operations

    /** Set the label for a container at the given position. */
    public void updateLabel(BlockPos pos, String label) {
        if (!isOpen()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE containers SET label=? WHERE x=? AND y=? AND z=?")) {
            ps.setString(1, label);
            ps.setInt(2, pos.getX());
            ps.setInt(3, pos.getY());
            ps.setInt(4, pos.getZ());
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to update label at {}", pos, e);
            rollback();
        }
    }

    /** Batch-update labels for multiple containers. */
    public void updateLabels(Map<BlockPos, String> labels) {
        if (!isOpen() || labels.isEmpty()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE containers SET label=? WHERE x=? AND y=? AND z=?")) {
            for (var entry : labels.entrySet()) {
                ps.setString(1, entry.getValue());
                ps.setInt(2, entry.getKey().getX());
                ps.setInt(3, entry.getKey().getY());
                ps.setInt(4, entry.getKey().getZ());
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to batch-update labels", e);
            rollback();
        }
    }

    /** Get the label for a container. Returns null if not set. */
    public String getLabel(BlockPos pos) {
        if (!isOpen()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT label FROM containers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("label") : null;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get label at {}", pos, e);
            return null;
        }
    }

    /** Get all labels (position → label) for containers that have one. */
    public Map<BlockPos, String> getAllLabels() {
        Map<BlockPos, String> result = new LinkedHashMap<>();
        if (!isOpen()) return result;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT x, y, z, label FROM containers WHERE label IS NOT NULL")) {
            while (rs.next()) {
                BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                result.put(pos, rs.getString("label"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get all labels", e);
        }
        return result;
    }

    // Write operations

    /**
     * Save a single container entry (upsert). Replaces any existing data
     * at the same position.
     */
    public void saveContainer(ContainerEntry entry) {
        if (!isOpen()) return;
        try {
            deleteContainerAt(entry.pos());
            insertContainer(entry);
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to save container at {}", entry.pos(), e);
            rollback();
        }
    }

    /**
     * Batch-save the entire in-memory index to the database.
     * Existing entries at the same positions are replaced.
     */
    public void saveAll(Map<BlockPos, ContainerEntry> index) {
        if (!isOpen() || index.isEmpty()) return;
        try {
            for (ContainerEntry entry : index.values()) {
                deleteContainerAt(entry.pos());
                insertContainer(entry);
            }
            connection.commit();
            LOGGER.info("Saved {} containers to database", index.size());
        } catch (SQLException e) {
            LOGGER.error("Failed to batch-save containers", e);
            rollback();
        }
    }

    private void insertContainer(ContainerEntry entry) throws SQLException {
        BlockPos pos = entry.pos();

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO containers (x, y, z, block_type, is_double, shulker_count, timestamp) VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.setString(4, entry.blockType());
            ps.setInt(5, entry.isDouble() ? 1 : 0);
            ps.setInt(6, entry.shulkerCount());
            ps.setLong(7, entry.timestamp());
            ps.executeUpdate();
        }

        // Items
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO items (x, y, z, item_id, quantity) VALUES (?,?,?,?,?)")) {
            for (var item : entry.items().entrySet()) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getY());
                ps.setInt(3, pos.getZ());
                ps.setString(4, item.getKey());
                ps.setInt(5, item.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Shulkers
        for (ShulkerDetail sd : entry.shulkerDetails()) {
            long shulkerId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO shulkers (x, y, z, shulker_type) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, pos.getX());
                ps.setInt(2, pos.getY());
                ps.setInt(3, pos.getZ());
                ps.setString(4, sd.shulkerType());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    shulkerId = keys.getLong(1);
                }
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO shulker_items (shulker_id, item_id, quantity) VALUES (?,?,?)")) {
                for (var item : sd.contents().entrySet()) {
                    ps.setLong(1, shulkerId);
                    ps.setString(2, item.getKey());
                    ps.setInt(3, item.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private void deleteContainerAt(BlockPos pos) throws SQLException {
        // SQLite doesn't enforce FK cascades by default — delete manually
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM shulkers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM shulker_items WHERE shulker_id=?")) {
                    while (rs.next()) {
                        del.setLong(1, rs.getLong(1));
                        del.addBatch();
                    }
                    del.executeBatch();
                }
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM shulkers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.executeUpdate();
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM items WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.executeUpdate();
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM containers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            ps.executeUpdate();
        }
    }

    /** Wipe stash container data from the database. */
    public void wipeAll() {
        if (!isOpen()) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM shulker_items");
            stmt.executeUpdate("DELETE FROM shulkers");
            stmt.executeUpdate("DELETE FROM items");
            stmt.executeUpdate("DELETE FROM containers");
            connection.commit();
            LOGGER.info("Stash database wiped");
        } catch (SQLException e) {
            LOGGER.error("Failed to wipe stash database", e);
            rollback();
        }
    }

    // ── Position-set tables (supply_chests, dump_chests, spawnproofer_supply) ──

    /** Save a set of BlockPos to a named table (replaces all rows). */
    private void savePositionSet(String table, Collection<BlockPos> positions) {
        if (!isOpen()) return;
        try (Statement del = connection.createStatement()) {
            del.executeUpdate("DELETE FROM " + table);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + table + " (x, y, z) VALUES (?,?,?)")) {
                for (BlockPos pos : positions) {
                    ps.setInt(1, pos.getX());
                    ps.setInt(2, pos.getY());
                    ps.setInt(3, pos.getZ());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to save {} positions to {}", positions.size(), table, e);
            rollback();
        }
    }

    /** Load all BlockPos from a named table (insertion order preserved). */
    private List<BlockPos> loadPositionSet(String table) {
        List<BlockPos> result = new ArrayList<>();
        if (!isOpen()) return result;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT x, y, z FROM " + table)) {
            while (rs.next()) {
                result.add(new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z")));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load positions from {}", table, e);
        }
        return result;
    }

    public void saveSupplyChests(Collection<BlockPos> positions) {
        savePositionSet("supply_chests", positions);
    }

    public List<BlockPos> loadSupplyChests() {
        return loadPositionSet("supply_chests");
    }

    public void saveDumpChests(Collection<BlockPos> positions) {
        savePositionSet("dump_chests", positions);
    }

    public List<BlockPos> loadDumpChests() {
        return loadPositionSet("dump_chests");
    }

    public void saveSpawnprooferSupply(Collection<BlockPos> positions) {
        savePositionSet("spawnproofer_supply", positions);
    }

    public List<BlockPos> loadSpawnprooferSupply() {
        return loadPositionSet("spawnproofer_supply");
    }

    // Scaffold table

    /** Replace all scaffold entries. */
    public void saveScaffold(Map<BlockPos, String> scaffoldMap) {
        if (!isOpen()) return;
        try (Statement del = connection.createStatement()) {
            del.executeUpdate("DELETE FROM scaffold");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO scaffold (x, y, z, item_id) VALUES (?,?,?,?)")) {
                for (var entry : scaffoldMap.entrySet()) {
                    ps.setInt(1, entry.getKey().getX());
                    ps.setInt(2, entry.getKey().getY());
                    ps.setInt(3, entry.getKey().getZ());
                    ps.setString(4, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to save scaffold data", e);
            rollback();
        }
    }

    /** Load all scaffold entries from the database. */
    public Map<BlockPos, String> loadScaffold() {
        Map<BlockPos, String> result = new LinkedHashMap<>();
        if (!isOpen()) return result;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT x, y, z, item_id FROM scaffold")) {
            while (rs.next()) {
                result.put(
                        new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                        rs.getString("item_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load scaffold data", e);
        }
        return result;
    }

    // Config key-value table

    /** Set a config value (upsert). */
    public void setConfig(String key, String value) {
        if (!isOpen()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO config (key, value) VALUES (?,?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to set config '{}'", key, e);
            rollback();
        }
    }

    /** Get a config value, or null if not set. */
    public String getConfig(String key) {
        if (!isOpen()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM config WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get config '{}'", key, e);
            return null;
        }
    }

    /** Delete a config key. */
    public void deleteConfig(String key) {
        if (!isOpen()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM config WHERE key=?")) {
            ps.setString(1, key);
            ps.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete config '{}'", key, e);
            rollback();
        }
    }

    // Storage chests (sorting config)

    /**
     * Save the full sorting configuration (storage chests, types, overflow).
     * Replaces all rows in storage_chests.
     */
    public void saveStorageChests(List<BlockPos> chests,
                                  Map<BlockPos, String> chestTypes,
                                  BlockPos overflowChest) {
        if (!isOpen()) return;
        try (Statement del = connection.createStatement()) {
            del.executeUpdate("DELETE FROM storage_chests");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO storage_chests (x, y, z, sort_order, item_type, is_overflow) VALUES (?,?,?,?,?,?)")) {
                int order = 0;
                for (BlockPos pos : chests) {
                    ps.setInt(1, pos.getX());
                    ps.setInt(2, pos.getY());
                    ps.setInt(3, pos.getZ());
                    ps.setInt(4, order++);
                    ps.setString(5, chestTypes.get(pos)); // may be null
                    ps.setInt(6, pos.equals(overflowChest) ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to save storage chest config", e);
            rollback();
        }
    }

    /** Result of loading storage chest config. */
    public record StorageChestConfig(
            List<BlockPos> chests,
            Map<BlockPos, String> chestTypes,
            BlockPos overflowChest) {}

    /** Load sorting configuration from the database. */
    public StorageChestConfig loadStorageChests() {
        List<BlockPos> chests = new ArrayList<>();
        Map<BlockPos, String> types = new LinkedHashMap<>();
        BlockPos overflow = null;
        if (!isOpen()) return new StorageChestConfig(chests, types, overflow);
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT x, y, z, item_type, is_overflow FROM storage_chests ORDER BY sort_order")) {
            while (rs.next()) {
                BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                chests.add(pos);
                String itemType = rs.getString("item_type");
                if (itemType != null) types.put(pos, itemType);
                if (rs.getInt("is_overflow") == 1) overflow = pos;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load storage chest config", e);
        }
        return new StorageChestConfig(chests, types, overflow);
    }

    // Keep items

    /** Save the keep-items set (replaces all rows). */
    public void saveKeepItems(Collection<String> itemIds) {
        if (!isOpen()) return;
        try (Statement del = connection.createStatement()) {
            del.executeUpdate("DELETE FROM keep_items");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO keep_items (item_id) VALUES (?)")) {
                for (String id : itemIds) {
                    ps.setString(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to save keep items", e);
            rollback();
        }
    }

    /** Load keep-item IDs from the database. */
    public Set<String> loadKeepItems() {
        Set<String> result = new LinkedHashSet<>();
        if (!isOpen()) return result;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT item_id FROM keep_items")) {
            while (rs.next()) {
                result.add(rs.getString("item_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load keep items", e);
        }
        return result;
    }

    // Read operations

    /**
     * Load all container entries from the database into a map.
     * Returns an empty map on error.
     */
    public Map<BlockPos, ContainerEntry> loadAll() {
        Map<BlockPos, ContainerEntry> result = new LinkedHashMap<>();
        if (!isOpen()) return result;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT x, y, z, block_type, is_double, shulker_count, timestamp FROM containers")) {

            while (rs.next()) {
                BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                String blockType = rs.getString("block_type");
                boolean isDouble = rs.getInt("is_double") == 1;
                int shulkerCount = rs.getInt("shulker_count");
                long timestamp = rs.getLong("timestamp");

                Map<String, Integer> items = loadItems(pos);
                List<ShulkerDetail> shulkerDetails = loadShulkers(pos);

                result.put(pos, new ContainerEntry(
                        pos, blockType, isDouble, items,
                        shulkerCount, shulkerDetails, timestamp));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load containers from database", e);
        }
        return result;
    }

    private Map<String, Integer> loadItems(BlockPos pos) throws SQLException {
        Map<String, Integer> items = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_id, quantity FROM items WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getString("item_id"), rs.getInt("quantity"));
                }
            }
        }
        return Map.copyOf(items);
    }

    private List<ShulkerDetail> loadShulkers(BlockPos pos) throws SQLException {
        List<ShulkerDetail> shulkers = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, shulker_type FROM shulkers WHERE x=? AND y=? AND z=?")) {
            ps.setInt(1, pos.getX());
            ps.setInt(2, pos.getY());
            ps.setInt(3, pos.getZ());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String type = rs.getString("shulker_type");
                    Map<String, Integer> contents = loadShulkerItems(id);
                    shulkers.add(new ShulkerDetail(type, contents));
                }
            }
        }
        return List.copyOf(shulkers);
    }

    private Map<String, Integer> loadShulkerItems(long shulkerId) throws SQLException {
        Map<String, Integer> items = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_id, quantity FROM shulker_items WHERE shulker_id=?")) {
            ps.setLong(1, shulkerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getString("item_id"), rs.getInt("quantity"));
                }
            }
        }
        return Map.copyOf(items);
    }

    /**
     * Search for all containers that hold a specific item.
     * Returns a list of (ContainerEntry, quantity) pairs sorted by quantity descending.
     */
    public List<SearchResult> searchItem(String itemIdFragment) {
        List<SearchResult> results = new ArrayList<>();
        if (!isOpen()) return results;

        // Includes items nested inside shulker boxes
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT x, y, z, item_id, quantity FROM (" +
                "  SELECT i.x, i.y, i.z, i.item_id, i.quantity FROM items i WHERE i.item_id LIKE ?" +
                "  UNION ALL" +
                "  SELECT s.x, s.y, s.z, si.item_id, si.quantity" +
                "    FROM shulker_items si" +
                "    JOIN shulkers s ON s.id = si.shulker_id" +
                "    WHERE si.item_id LIKE ?" +
                ")")) {
            ps.setString(1, "%" + itemIdFragment + "%");
            ps.setString(2, "%" + itemIdFragment + "%");
            try (ResultSet rs = ps.executeQuery()) {
                // group by position
                Map<BlockPos, Map<String, Integer>> posItems = new LinkedHashMap<>();
                while (rs.next()) {
                    BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    posItems.computeIfAbsent(pos, k -> new HashMap<>())
                            .merge(rs.getString("item_id"), rs.getInt("quantity"), Integer::sum);
                }

                for (var entry : posItems.entrySet()) {
                    int totalQty = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
                    results.add(new SearchResult(entry.getKey(), entry.getValue(), totalQty));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to search stash database", e);
        }

        results.sort(Comparator.comparingInt(SearchResult::totalQuantity).reversed());
        return results;
    }

    /** Result of an item search against the database. */
    public record SearchResult(BlockPos pos, Map<String, Integer> matchedItems, int totalQuantity) {}

    /** Find containers holding any of the given item IDs (including shulker contents). */
    public Map<BlockPos, Map<String, Integer>> findContainersForExactItems(Set<String> itemIds) {
        Map<BlockPos, Map<String, Integer>> result = new LinkedHashMap<>();
        if (!isOpen() || itemIds.isEmpty()) return result;

        // Build IN clause: (?, ?, ...)
        String placeholders = String.join(",", itemIds.stream().map(id -> "?").toList());
        String sql = "SELECT x, y, z, item_id, quantity FROM (" +
                "  SELECT i.x, i.y, i.z, i.item_id, i.quantity FROM items i WHERE i.item_id IN (" + placeholders + ")" +
                "  UNION ALL" +
                "  SELECT s.x, s.y, s.z, si.item_id, si.quantity" +
                "    FROM shulker_items si" +
                "    JOIN shulkers s ON s.id = si.shulker_id" +
                "    WHERE si.item_id IN (" + placeholders + ")" +
                ")";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            for (String id : itemIds) {
                ps.setString(idx++, id);
            }
            for (String id : itemIds) {
                ps.setString(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    result.computeIfAbsent(pos, k -> new HashMap<>())
                            .merge(rs.getString("item_id"), rs.getInt("quantity"), Integer::sum);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to find containers for exact items", e);
        }
        return result;
    }

    /** Count total containers stored in the database. */
    public int countContainers() {
        if (!isOpen()) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM containers")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to count containers", e);
            return 0;
        }
    }

    /** Count total unique item types stored in the database. */
    public int countItemTypes() {
        if (!isOpen()) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(DISTINCT item_id) FROM items")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to count item types", e);
            return 0;
        }
    }

    /** Sum total item quantity across all containers. */
    public int countTotalItems() {
        if (!isOpen()) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(quantity), 0) FROM items")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to count total items", e);
            return 0;
        }
    }

    // Kit operations

    /** Maximum item slots in a kit (matches shulker box capacity). */
    public static final int KIT_MAX_SLOTS = 27;

    /** Create a new empty kit. Returns false if the name already exists. */
    public boolean createKit(String name) {
        if (!isOpen()) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO kits (name, created_at) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setLong(2, System.currentTimeMillis());
            int rows = ps.executeUpdate();
            connection.commit();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to create kit '{}'", name, e);
            rollback();
            return false;
        }
    }

    /** Delete a kit and all its items. */
    public boolean deleteKit(String name) {
        if (!isOpen()) return false;
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM kit_items WHERE kit_name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM kits WHERE name = ?")) {
                ps.setString(1, name);
                int rows = ps.executeUpdate();
                connection.commit();
                return rows > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete kit '{}'", name, e);
            rollback();
            return false;
        }
    }

    /** Add or update an item in a kit. Returns false if the kit would exceed 27 slots. */
    public boolean addKitItem(String kitName, String itemId, int quantity) {
        if (!isOpen()) return false;
        try {
            boolean exists;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM kit_items WHERE kit_name = ? AND item_id = ?")) {
                ps.setString(1, kitName);
                ps.setString(2, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (!exists) {
                int currentSlots = countKitSlots(kitName);
                if (currentSlots >= KIT_MAX_SLOTS) return false;
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO kit_items (kit_name, item_id, quantity) VALUES (?, ?, ?) " +
                    "ON CONFLICT(kit_name, item_id) DO UPDATE SET quantity = excluded.quantity")) {
                ps.setString(1, kitName);
                ps.setString(2, itemId);
                ps.setInt(3, quantity);
                ps.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to add item to kit '{}'", kitName, e);
            rollback();
            return false;
        }
    }

    /** Remove an item from a kit. Returns false if the item wasn't in the kit. */
    public boolean removeKitItem(String kitName, String itemId) {
        if (!isOpen()) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM kit_items WHERE kit_name = ? AND item_id = ?")) {
            ps.setString(1, kitName);
            ps.setString(2, itemId);
            int rows = ps.executeUpdate();
            connection.commit();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to remove item from kit '{}'", kitName, e);
            rollback();
            return false;
        }
    }

    /** Save a full kit from a snapshot (replaces all items). Truncates to 27 slots. */
    public boolean snapshotKit(String kitName, Map<String, Integer> items) {
        if (!isOpen()) return false;
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO kits (name, created_at) VALUES (?, ?)")) {
                ps.setString(1, kitName);
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM kit_items WHERE kit_name = ?")) {
                ps.setString(1, kitName);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO kit_items (kit_name, item_id, quantity) VALUES (?, ?, ?)")) {
                int slots = 0;
                for (var entry : items.entrySet()) {
                    if (slots >= KIT_MAX_SLOTS) break;
                    ps.setString(1, kitName);
                    ps.setString(2, entry.getKey());
                    ps.setInt(3, entry.getValue());
                    ps.addBatch();
                    slots++;
                }
                ps.executeBatch();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to snapshot kit '{}'", kitName, e);
            rollback();
            return false;
        }
    }

    /** List all kit names. */
    public List<String> listKits() {
        List<String> names = new ArrayList<>();
        if (!isOpen()) return names;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM kits ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to list kits", e);
        }
        return names;
    }

    /** Check if a kit exists. */
    public boolean kitExists(String name) {
        if (!isOpen()) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM kits WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /** Load all items for a kit. Returns an ordered map of item_id -> quantity. */
    public Map<String, Integer> loadKitItems(String kitName) {
        Map<String, Integer> items = new LinkedHashMap<>();
        if (!isOpen()) return items;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_id, quantity FROM kit_items WHERE kit_name = ? ORDER BY item_id")) {
            ps.setString(1, kitName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getString("item_id"), rs.getInt("quantity"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load kit items for '{}'", kitName, e);
        }
        return items;
    }

    /** Count unique item slots used in a kit. */
    public int countKitSlots(String kitName) {
        if (!isOpen()) return 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM kit_items WHERE kit_name = ?")) {
            ps.setString(1, kitName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    // ── Storage Lane CRUD ──────────────────────────────────────────────────────

    /**
     * Persist a StorageLane (insert-only). Updates lane.id on success.
     * Returns true on success.
     */
    public boolean saveLane(StorageLane lane) {
        if (!isOpen()) return false;
        try {
            long laneId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO lane_config (name, item_id, frame_x, frame_y, frame_z, front_face, "
                            + "deposit_mode, priority, overflow_behavior, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, lane.getName());
                ps.setString(2, lane.getItemId());           // may be null
                if (lane.getLabelFramePos() != null) {
                    ps.setInt(3, lane.getLabelFramePos().getX());
                    ps.setInt(4, lane.getLabelFramePos().getY());
                    ps.setInt(5, lane.getLabelFramePos().getZ());
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                    ps.setNull(4, java.sql.Types.INTEGER);
                    ps.setNull(5, java.sql.Types.INTEGER);
                }
                ps.setString(6, lane.getFrontFace() != null ? lane.getFrontFace().name() : null);
                ps.setString(7, lane.getDepositMode().name());
                ps.setInt(8, lane.getPriority());
                ps.setString(9, lane.getOverflowBehavior().name());
                ps.setLong(10, System.currentTimeMillis());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    laneId = keys.getLong(1);
                }
            }

            insertLanePositions(laneId, "lane_chests", lane.getChestPositions());
            insertLanePositions(laneId, "lane_inputs", lane.getInputPositions());

            connection.commit();
            lane.setId((int) laneId);
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to save lane '{}'", lane.getName(), e);
            rollback();
            return false;
        }
    }

    private void insertLanePositions(long laneId, String table, List<BlockPos> positions)
            throws SQLException {
        if (positions.isEmpty()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + table + " (lane_id, seq, x, y, z) VALUES (?,?,?,?,?)")) {
            int seq = 0;
            for (BlockPos pos : positions) {
                ps.setLong(1, laneId);
                ps.setInt(2, seq++);
                ps.setInt(3, pos.getX());
                ps.setInt(4, pos.getY());
                ps.setInt(5, pos.getZ());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Update the item assignment for an existing lane. */
    public boolean updateLaneItem(int laneId, String itemId) {
        if (!isOpen()) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE lane_config SET item_id=? WHERE id=?")) {
            ps.setString(1, itemId);
            ps.setInt(2, laneId);
            int rows = ps.executeUpdate();
            connection.commit();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.error("Failed to update lane item for id={}", laneId, e);
            rollback();
            return false;
        }
    }

    /** Update an existing lane's metadata and positions in place. */
    public boolean updateLane(StorageLane lane) {
        if (!isOpen() || lane.getId() == 0) return false;
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE lane_config SET name=?, item_id=?, frame_x=?, frame_y=?, frame_z=?, "
                            + "front_face=?, deposit_mode=?, priority=?, overflow_behavior=? WHERE id=?")) {
                ps.setString(1, lane.getName());
                ps.setString(2, lane.getItemId());
                if (lane.getLabelFramePos() != null) {
                    ps.setInt(3, lane.getLabelFramePos().getX());
                    ps.setInt(4, lane.getLabelFramePos().getY());
                    ps.setInt(5, lane.getLabelFramePos().getZ());
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                    ps.setNull(4, java.sql.Types.INTEGER);
                    ps.setNull(5, java.sql.Types.INTEGER);
                }
                ps.setString(6, lane.getFrontFace() != null ? lane.getFrontFace().name() : null);
                ps.setString(7, lane.getDepositMode().name());
                ps.setInt(8, lane.getPriority());
                ps.setString(9, lane.getOverflowBehavior().name());
                ps.setInt(10, lane.getId());
                ps.executeUpdate();
            }

            replaceLanePositions(lane.getId(), "lane_chests", lane.getChestPositions());
            replaceLanePositions(lane.getId(), "lane_inputs", lane.getInputPositions());
            connection.commit();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to update lane '{}'", lane.getName(), e);
            rollback();
            return false;
        }
    }

    private void replaceLanePositions(int laneId, String table, List<BlockPos> positions)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE lane_id=?")) {
            delete.setInt(1, laneId);
            delete.executeUpdate();
        }
        insertLanePositions(laneId, table, positions);
    }

    /** Load all persisted storage lanes. */
    public List<StorageLane> loadAllLanes() {
        List<StorageLane> lanes = new ArrayList<>();
        if (!isOpen()) return lanes;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, name, item_id, frame_x, frame_y, frame_z, "
                             + "front_face, deposit_mode, priority, overflow_behavior "
                             + "FROM lane_config ORDER BY priority ASC, id ASC")) {
            while (rs.next()) {
                StorageLane lane = new StorageLane(rs.getString("name"));
                lane.setId(rs.getInt("id"));
                lane.setItemId(rs.getString("item_id"));
                int fx = rs.getInt("frame_x");
                if (!rs.wasNull()) {
                    lane.setLabelFramePos(new BlockPos(fx, rs.getInt("frame_y"), rs.getInt("frame_z")));
                }
                String frontFace = rs.getString("front_face");
                if (frontFace != null && !frontFace.isBlank()) {
                    try {
                        lane.setFrontFace(Direction.valueOf(frontFace));
                    } catch (IllegalArgumentException ignored) {}
                }
                try {
                    lane.setDepositMode(StorageLane.DepositMode.valueOf(rs.getString("deposit_mode")));
                } catch (IllegalArgumentException ignored) {}
                lane.setPriority(rs.getInt("priority"));
                try {
                    lane.setOverflowBehavior(StorageLane.OverflowBehavior.valueOf(rs.getString("overflow_behavior")));
                } catch (IllegalArgumentException ignored) {}
                lanes.add(lane);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load lanes", e);
            return lanes;
        }

        // Load chest and input positions for each lane
        for (StorageLane lane : lanes) {
            loadLanePositions(lane, "lane_chests", false);
            loadLanePositions(lane, "lane_inputs", true);
        }
        return lanes;
    }

    private void loadLanePositions(StorageLane lane, String table, boolean inputs) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT x, y, z FROM " + table
                        + " WHERE lane_id=? ORDER BY seq ASC")) {
            ps.setInt(1, lane.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BlockPos pos = new BlockPos(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    if (inputs) lane.addInput(pos);
                    else lane.addChest(pos);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load positions from {} for lane {}", table, lane.getId(), e);
        }
    }

    /** Delete a single lane (cascades to lane_chests/lane_inputs). */
    public boolean deleteLane(int laneId) {
        if (!isOpen()) return false;
        try {
            // Manual cascade (SQLite FK enforcement may not be enabled by default)
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM lane_inputs WHERE lane_id=?")) {
                ps.setInt(1, laneId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM lane_chests WHERE lane_id=?")) {
                ps.setInt(1, laneId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM lane_config WHERE id=?")) {
                ps.setInt(1, laneId);
                int rows = ps.executeUpdate();
                connection.commit();
                return rows > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete lane id={}", laneId, e);
            rollback();
            return false;
        }
    }

    /** Delete all persisted storage lanes. */
    public void clearAllLanes() {
        if (!isOpen()) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM lane_inputs");
            stmt.executeUpdate("DELETE FROM lane_chests");
            stmt.executeUpdate("DELETE FROM lane_config");
            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Failed to clear all lanes", e);
            rollback();
        }
    }

    // Helpers

    private void rollback() {
        try {
            if (connection != null) connection.rollback();
        } catch (SQLException e) {
            LOGGER.error("Rollback failed", e);
        }
    }
}

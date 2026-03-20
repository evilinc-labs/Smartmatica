package dev.moar.schematic;

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

/**
 * Persists schematic printer state so printing can resume after a
 * disconnect, crash, or client restart.
 *
 * Data is written to {@code moar/printer_checkpoint.json}
 * and includes the schematic filename, anchor position, session
 * block count, and the player's last known position.
 */
public final class PrinterCheckpoint {

    private PrinterCheckpoint() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moar")
            .resolve("printer_checkpoint.json");

    // ── auto-save interval ──────────────────────────────────────────────

    private static final int SAVE_INTERVAL = 50;
    private static int blocksSinceLastSave = 0;

    // ── current state (in-memory mirror) ────────────────────────────────

    private static CheckpointData current = null;

    // ── public API ──────────────────────────────────────────────────────

    public static void save(String schematicFile, BlockPos anchor, int blocksPlaced, BlockPos playerPos) {
        current = new CheckpointData();
        current.schematicFile = schematicFile;
        if (anchor != null) {
            current.anchorX = anchor.getX();
            current.anchorY = anchor.getY();
            current.anchorZ = anchor.getZ();
        }
        current.blocksPlaced = blocksPlaced;
        if (playerPos != null) {
            current.playerX = playerPos.getX();
            current.playerY = playerPos.getY();
            current.playerZ = playerPos.getZ();
        }
        current.timestamp = System.currentTimeMillis();

        writeToDisk();
    }

    public static void onBlockPlaced(String schematicFile, BlockPos anchor, int blocksPlaced, BlockPos playerPos) {
        blocksSinceLastSave++;
        if (blocksSinceLastSave >= SAVE_INTERVAL) {
            blocksSinceLastSave = 0;
            save(schematicFile, anchor, blocksPlaced, playerPos);
        }
    }

    public static CheckpointData load() {
        try {
            if (!Files.exists(FILE_PATH)) return null;
            try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
                CheckpointData data = GSON.fromJson(reader, CheckpointData.class);
                if (data == null || data.schematicFile == null) return null;
                current = data;
                return data;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load checkpoint", e);
            return null;
        }
    }

    public static void clear() {
        current = null;
        blocksSinceLastSave = 0;
        try {
            Files.deleteIfExists(FILE_PATH);
        } catch (Exception e) {
            LOGGER.error("Failed to delete checkpoint", e);
        }
    }

    public static boolean exists() {
        return Files.exists(FILE_PATH);
    }

    public static CheckpointData getCurrent() {
        return current;
    }

    // ── persistence ─────────────────────────────────────────────────────

    private static void writeToDisk() {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(current, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to write checkpoint", e);
        }
    }

    // ── data class ──────────────────────────────────────────────────────

    public static class CheckpointData {
        public String schematicFile;
        public int anchorX, anchorY, anchorZ;
        public int blocksPlaced;
        public int playerX, playerY, playerZ;
        public long timestamp;

        public BlockPos anchorPos() {
            return new BlockPos(anchorX, anchorY, anchorZ);
        }

        public BlockPos playerPos() {
            return new BlockPos(playerX, playerY, playerZ);
        }

        public String timeSince() {
            long elapsed = System.currentTimeMillis() - timestamp;
            long seconds = elapsed / 1000;
            if (seconds < 60) return seconds + "s ago";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m ago";
            long hours = minutes / 60;
            return hours + "h " + (minutes % 60) + "m ago";
        }
    }
}

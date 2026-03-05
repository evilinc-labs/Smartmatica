package dev.litematicaprinter.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects schematics that Litematica currently has loaded/placed in the world.
 *
 * <p>Litematica persists its active placements in JSON files under
 * {@code config/litematica/}.  The per-dimension files follow the pattern
 * {@code litematica_<profile>_dim_<dimension>.json} and contain an array
 * of placement entries with the schematic file path, world-space origin,
 * and enabled flag.
 *
 * <p>This class scans those files to find enabled placements, allowing the
 * printer to auto-detect what the user has loaded in Litematica
 * without requiring a separate load step.
 */
public final class LitematicaDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("Smartmatica/Detector");

    private LitematicaDetector() {}

    /**
     * A detected Litematica schematic placement.
     *
     * @param schematicPath absolute path to the {@code .litematic} file
     * @param name          human-readable placement name
     * @param originX       world-space X of the placement origin
     * @param originY       world-space Y of the placement origin
     * @param originZ       world-space Z of the placement origin
     */
    public record DetectedPlacement(
            Path schematicPath,
            String name,
            int originX, int originY, int originZ
    ) {}

    /**
     * Scan all Litematica per-dimension config files and return every
     * <b>enabled</b> schematic placement whose {@code .litematic} file
     * still exists on disk.
     *
     * @return list of detected placements (may be empty, never null)
     */
    public static List<DetectedPlacement> detectPlacements() {
        List<DetectedPlacement> results = new ArrayList<>();

        Path configDir = FabricLoader.getInstance().getGameDir()
                .resolve("config").resolve("litematica");

        if (!Files.isDirectory(configDir)) {
            LOGGER.debug("Litematica config directory not found: {}", configDir);
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "litematica_*_dim_*.json")) {
            for (Path jsonFile : stream) {
                results.addAll(parsePlacementFile(jsonFile));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan Litematica config directory", e);
        }

        return results;
    }

    /**
     * Convenience: return the first detected enabled placement, or {@code null}.
     */
    public static DetectedPlacement detectFirst() {
        List<DetectedPlacement> all = detectPlacements();
        return all.isEmpty() ? null : all.get(0);
    }

    // ── internals ───────────────────────────────────────────────────────

    private static List<DetectedPlacement> parsePlacementFile(Path jsonFile) {
        List<DetectedPlacement> results = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (!root.has("placements")) return results;
            JsonObject placementsObj = root.getAsJsonObject("placements");

            if (!placementsObj.has("placements")) return results;
            JsonArray arr = placementsObj.getAsJsonArray("placements");

            for (JsonElement elem : arr) {
                if (!elem.isJsonObject()) continue;
                JsonObject entry = elem.getAsJsonObject();

                if (entry.has("enabled") && !entry.get("enabled").getAsBoolean()) continue;

                if (!entry.has("schematic")) continue;
                String schematicStr = entry.get("schematic").getAsString();
                Path schematicPath = Path.of(schematicStr).normalize();

                // Validate file extension to prevent loading non-schematic files
                if (!schematicPath.toString().endsWith(".litematic")) {
                    LOGGER.warn("Skipping placement — not a .litematic file: {}", schematicStr);
                    continue;
                }

                if (!Files.exists(schematicPath)) {
                    LOGGER.debug("Skipping placement — schematic file not found: {}", schematicStr);
                    continue;
                }

                String name = entry.has("name") ? entry.get("name").getAsString() : "Unknown";

                if (!entry.has("origin")) continue;
                JsonArray origin = entry.getAsJsonArray("origin");
                if (origin.size() < 3) continue;

                int ox = origin.get(0).getAsInt();
                int oy = origin.get(1).getAsInt();
                int oz = origin.get(2).getAsInt();

                results.add(new DetectedPlacement(schematicPath, name, ox, oy, oz));
                LOGGER.debug("Detected Litematica placement: '{}' at ({}, {}, {}) from {}",
                        name, ox, oy, oz, schematicPath.getFileName());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Litematica placement file: {}", jsonFile.getFileName(), e);
        }

        return results;
    }
}

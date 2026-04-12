package dev.moar.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.BlockState;
*//*?} else {*/
import net.minecraft.block.BlockState;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Detects Litematica schematic placements via reflection or JSON configs.
public final class LitematicaDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Detector");

    private LitematicaDetector() {}

    // A detected Litematica schematic placement.
    public record DetectedPlacement(
            Path schematicPath,
            String name,
            int originX, int originY, int originZ
    ) {}

    // Return all enabled placements (reflection first, JSON fallback).
    public static List<DetectedPlacement> detectPlacements() {
        // Primary: read live placements from Litematica's memory.
        // This works even before Litematica saves its config to disk.
        List<DetectedPlacement> live = detectFromMemory();
        if (!live.isEmpty()) return live;

        // Fallback: parse on-disk JSON config files.
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
     * Convenience: return the first detected enabled placement, or null.
     */
    public static DetectedPlacement detectFirst() {
        List<DetectedPlacement> all = detectPlacements();
        return all.isEmpty() ? null : all.get(0);
    }

    // internals

    // Read placements from Litematica memory via reflection.
    private static List<DetectedPlacement> detectFromMemory() {
        List<DetectedPlacement> results = new ArrayList<>();
        try {
            Class<?> dataManager = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object placementMgr = dataManager.getMethod("getSchematicPlacementManager")
                    .invoke(null);
            @SuppressWarnings("unchecked")
            List<?> placements = (List<?>) placementMgr.getClass()
                    .getMethod("getAllSchematicsPlacements")
                    .invoke(placementMgr);

            for (Object p : placements) {
                Class<?> pClass = p.getClass();

                boolean enabled;
                try {
                    enabled = (boolean) pClass.getMethod("isEnabled").invoke(p);
                } catch (NoSuchMethodException e) {
                    enabled = true;
                }
                if (!enabled) continue;

                // getOrigin() → BlockPos
                Object origin = pClass.getMethod("getOrigin").invoke(p);
                int ox = (int) origin.getClass().getMethod("getX").invoke(origin);
                int oy = (int) origin.getClass().getMethod("getY").invoke(origin);
                int oz = (int) origin.getClass().getMethod("getZ").invoke(origin);

                // getSchematicFile() → File
                java.io.File schematicFile = (java.io.File) pClass
                        .getMethod("getSchematicFile").invoke(p);
                if (schematicFile == null) continue;
                Path schematicPath = schematicFile.toPath().normalize();
                if (!schematicPath.toString().endsWith(".litematic")) continue;

                // Don't skip placements whose file is missing on disk —
                // we still need the origin for anchor alignment.  The
                // file may have been loaded from memory or a temp path.
                if (!Files.exists(schematicPath)) {
                    LOGGER.warn("Litematica placement '{}' file not on disk: {} — including for origin only",
                            schematicPath.getFileName(), schematicPath);
                }

                String name;
                try {
                    name = (String) pClass.getMethod("getName").invoke(p);
                } catch (NoSuchMethodException e) {
                    name = schematicPath.getFileName().toString();
                }

                results.add(new DetectedPlacement(schematicPath, name, ox, oy, oz));
                LOGGER.info("Live-detected Litematica placement: '{}' at ({}, {}, {}) file={}",
                        name, ox, oy, oz, schematicPath);
            }
        } catch (ClassNotFoundException e) {
            // Litematica not present — normal for some setups
            LOGGER.debug("Litematica classes not found — reflection detection unavailable");
        } catch (Exception e) {
            LOGGER.debug("Litematica reflection detection failed: {}", e.getMessage());
        }
        return results;
    }

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

    // SchematicWorld anchor correlation

    /**
     * Detect anchor by reading blocks from Litematica's SchematicWorld.
     * Scans near the player, correlates against the schematic to compute
     * the anchor offset. Returns null if detection fails.
     */
    public static BlockPos detectAnchorFromSchematicWorld(LitematicaSchematic schematic) {
        if (schematic == null) return null;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return null;

        // Access Litematica's SchematicWorld via reflection:
        // SchematicWorldHandler.getSchematicWorld() → WorldSchematic
        // WorldSchematic extends World, so getBlockState(BlockPos) works.
        /*? if >=26.1 {*//*
        Level schematicWorld;
        *//*?} else {*/
        World schematicWorld;
        /*?}*/
        try {
            Class<?> swh = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
            Object world = swh.getMethod("getSchematicWorld").invoke(null);
            if (world == null) {
                LOGGER.debug("SchematicWorld is null — no schematic loaded in Litematica");
                return null;
            }
            /*? if >=26.1 {*//*
            if (!(world instanceof Level)) {
            *//*?} else {*/
            if (!(world instanceof World)) {
            /*?}*/
                /*? if >=26.1 {*//*
                LOGGER.warn("SchematicWorld is not a Level instance: {}", world.getClass());
                *//*?} else {*/
                LOGGER.warn("SchematicWorld is not a World instance: {}", world.getClass());
                /*?}*/
                return null;
            }
            /*? if >=26.1 {*//*
            schematicWorld = (Level) world;
            *//*?} else {*/
            schematicWorld = (World) world;
            /*?}*/
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Litematica SchematicWorldHandler not found");
            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to access SchematicWorld: {}", e.getMessage());
            return null;
        }

        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/
        int scanRadius = 64;

        // Phase 1: find non-air blocks in the SchematicWorld near the player.
        // Collect up to a handful — we only need one to compute the anchor,
        // but we'll verify with several.
        List<BlockPos> hologramBlocks = new ArrayList<>();
        List<BlockState> hologramStates = new ArrayList<>();

        for (int dy = -scanRadius; dy <= scanRadius && hologramBlocks.size() < 20; dy++) {
            for (int dx = -scanRadius; dx <= scanRadius && hologramBlocks.size() < 20; dx++) {
                for (int dz = -scanRadius; dz <= scanRadius && hologramBlocks.size() < 20; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos wp = playerPos.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos wp = playerPos.add(dx, dy, dz);
                    /*?}*/
                    BlockState bs = schematicWorld.getBlockState(wp);
                    if (!bs.isAir()) {
                        hologramBlocks.add(wp);
                        hologramStates.add(bs);
                    }
                }
            }
        }

        if (hologramBlocks.isEmpty()) {
            LOGGER.info("No hologram blocks found within {} blocks of player", scanRadius);
            return null;
        }

        LOGGER.info("Found {} hologram blocks near player — correlating with schematic", hologramBlocks.size());

        // Phase 2: for the first hologram block, search the schematic for
        // all positions with a matching block state.  Each match gives a
        // candidate anchor.
        BlockPos firstWorld = hologramBlocks.get(0);
        BlockState firstState = hologramStates.get(0);

        List<BlockPos> candidates = new ArrayList<>();
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState rs = region.getBlockState(x, y, z);
                        if (rs.equals(firstState)) {
                            // schematic-local pos = region.origin + (x, y, z)
                            int sx = region.originX + x;
                            int sy = region.originY + y;
                            int sz = region.originZ + z;
                            // anchor candidate: worldPos - schematicPos
                            candidates.add(new BlockPos(
                                    firstWorld.getX() - sx,
                                    firstWorld.getY() - sy,
                                    firstWorld.getZ() - sz));
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            LOGGER.warn("No schematic position matches hologram block {} at {}",
                    firstState, firstWorld);
            return null;
        }

        // Phase 3: verify each candidate by checking more hologram blocks.
        // The correct anchor will match ALL hologram blocks.
        BlockPos bestAnchor = null;
        int bestScore = 0;

        for (BlockPos candidate : candidates) {
            int score = 0;
            for (int i = 0; i < hologramBlocks.size(); i++) {
                BlockPos wp = hologramBlocks.get(i);
                BlockState expected = hologramStates.get(i);
                int sx = wp.getX() - candidate.getX();
                int sy = wp.getY() - candidate.getY();
                int sz = wp.getZ() - candidate.getZ();
                BlockState schematicState = schematic.getBlockState(sx, sy, sz);
                if (schematicState.equals(expected)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestAnchor = candidate;
            }
        }

        if (bestAnchor != null) {
            LOGGER.info("Anchor correlated from SchematicWorld: {} (score {}/{})",
                    bestAnchor, bestScore, hologramBlocks.size());
        }
        return bestAnchor;
    }
}

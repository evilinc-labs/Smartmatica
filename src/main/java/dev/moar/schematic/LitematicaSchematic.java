package dev.moar.schematic;

import dev.moar.util.NbtCompat;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses Litematica {@code .litematic} files and provides
 * block-state lookups by coordinate.
 *
 * Supports schema versions 5, 6, and 7.  Schematics created in newer
 * Minecraft versions are handled gracefully: unknown blocks are replaced
 * with air and reported, unknown properties fall back to defaults.
 *
 * The file format is gzip-compressed NBT with regions containing palettized,
 * bit-packed block state arrays.  Entries in the packed long array can
 * span two consecutive longs (unlike Minecraft 1.16+ chunk storage).
 */
public final class LitematicaSchematic {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Schematic");

    private final String name;
    private final String author;
    private final int sizeX, sizeY, sizeZ;
    private final int totalNonAir;
    private final List<Region> regions;
    private final int schemaVersion;
    private final int dataVersion;
    private final Map<String, Integer> unknownBlocks;

    /**
     * The offset that was subtracted from all region origins during
     * normalization (i.e. the original global minimum corner).
     *
     * When the anchor comes from Litematica's placement origin
     * (which refers to the schematic's original reference point, not the
     * normalized min corner), callers must add this offset to the anchor
     * so that {@code worldPos - adjustedAnchor} produces normalized
     * coordinates that {@link #getBlockState} expects.
     */
    private final int originOffsetX, originOffsetY, originOffsetZ;

    // ── public API ──────────────────────────────────────────────────────

    /** Load a {@code .litematic} file and normalize all region origins so the
     *  minimum corner of the bounding box sits at (0, 0, 0). */
    /** Max decompressed NBT size — prevents zip-bomb schematics from
     *  exhausting heap.  256 MB is generous for any real build. */
    private static final long MAX_NBT_BYTES = 256L * 1024 * 1024;

    public static LitematicaSchematic load(Path path) throws IOException {
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.of(MAX_NBT_BYTES));

        // ── schema & data version ───────────────────────────────────────
        int schemaVersion = NbtCompat.getInt(root, "Version", -1);

        if (schemaVersion != -1 && (schemaVersion < 4 || schemaVersion > 7)) {
            LOGGER.warn("Litematic schema version {} may not be fully supported (expected 5-7)", schemaVersion);
        }

        // ── metadata ────────────────────────────────────────────────────
        NbtCompound meta = NbtCompat.getCompound(root, "Metadata");
        String name   = NbtCompat.getString(meta, "Name", path.getFileName().toString());
        String author = NbtCompat.getString(meta, "Author", "Unknown");

        int dataVersion = NbtCompat.getInt(meta, "MinecraftDataVersion", -1);

        int currentDataVersion = NbtCompat.currentDataVersion();
        if (dataVersion > currentDataVersion) {
            LOGGER.warn("Schematic '{}' was created in a newer Minecraft version (data version {} vs current {}). "
                    + "Some blocks may not be recognized.", name, dataVersion, currentDataVersion);
        }

        // ── regions ─────────────────────────────────────────────────────
        NbtCompound regionsNbt = NbtCompat.getCompound(root, "Regions");
        Set<String> regionKeys = regionsNbt.getKeys();
        if (regionKeys.isEmpty()) {
            throw new IOException("Schematic contains no regions (schema version "
                    + schemaVersion + "). Only versions 5-7 are supported.");
        }

        Map<String, Integer> unknownBlocks = new LinkedHashMap<>();
        List<Region> regions = new ArrayList<>();
        int nonAir = 0;

        int globalMinX = Integer.MAX_VALUE, globalMinY = Integer.MAX_VALUE, globalMinZ = Integer.MAX_VALUE;
        int globalMaxX = Integer.MIN_VALUE, globalMaxY = Integer.MIN_VALUE, globalMaxZ = Integer.MIN_VALUE;

        for (String key : regionKeys) {
            NbtCompound regionNbt = NbtCompat.getCompound(regionsNbt, key);
            Region region = Region.parse(regionNbt, key, unknownBlocks);
            regions.add(region);
            nonAir += region.countNonAir();

            globalMinX = Math.min(globalMinX, region.originX);
            globalMinY = Math.min(globalMinY, region.originY);
            globalMinZ = Math.min(globalMinZ, region.originZ);
            globalMaxX = Math.max(globalMaxX, region.originX + region.absX);
            globalMaxY = Math.max(globalMaxY, region.originY + region.absY);
            globalMaxZ = Math.max(globalMaxZ, region.originZ + region.absZ);
        }

        for (Region r : regions) {
            r.originX -= globalMinX;
            r.originY -= globalMinY;
            r.originZ -= globalMinZ;
        }

        int sizeX = globalMaxX - globalMinX;
        int sizeY = globalMaxY - globalMinY;
        int sizeZ = globalMaxZ - globalMinZ;

        if (!unknownBlocks.isEmpty()) {
            int totalUnknown = unknownBlocks.values().stream().mapToInt(Integer::intValue).sum();
            LOGGER.warn("Schematic '{}' contains {} unknown block type(s) ({} total blocks replaced with air):",
                    name, unknownBlocks.size(), totalUnknown);
            unknownBlocks.forEach((id, count) ->
                    LOGGER.warn("  - {} (×{})", id, count));
        }

        return new LitematicaSchematic(name, author, sizeX, sizeY, sizeZ, nonAir, regions,
                schemaVersion, dataVersion, unknownBlocks,
                globalMinX, globalMinY, globalMinZ);
    }

    /**
     * Returns the target {@link BlockState} at schematic-local coordinates.
     * Coordinates are relative to the normalized origin (0, 0, 0).
     * Returns {@code Blocks.AIR} default state if the position is outside
     * all regions.
     */
    public BlockState getBlockState(int x, int y, int z) {
        for (Region region : regions) {
            int lx = x - region.originX;
            int ly = y - region.originY;
            int lz = z - region.originZ;
            if (lx >= 0 && lx < region.absX &&
                    ly >= 0 && ly < region.absY &&
                    lz >= 0 && lz < region.absZ) {
                return region.getBlockState(lx, ly, lz);
            }
        }
        return Blocks.AIR.getDefaultState();
    }

    /** Whether the given schematic-local position is inside any region. */
    public boolean contains(int x, int y, int z) {
        for (Region region : regions) {
            int lx = x - region.originX;
            int ly = y - region.originY;
            int lz = z - region.originZ;
            if (lx >= 0 && lx < region.absX &&
                    ly >= 0 && ly < region.absY &&
                    lz >= 0 && lz < region.absZ) {
                return true;
            }
        }
        return false;
    }

    // ── getters ─────────────────────────────────────────────────────────

    public String getName()            { return name; }
    public String getAuthor()          { return author; }
    public int    getSizeX()           { return sizeX; }
    public int    getSizeY()           { return sizeY; }
    public int    getSizeZ()           { return sizeZ; }
    public int    getTotalNonAir()     { return totalNonAir; }
    public List<Region> getRegions()   { return Collections.unmodifiableList(regions); }
    public int    getSchemaVersion()   { return schemaVersion; }
    public int    getDataVersion()     { return dataVersion; }

    public Map<String, Integer> getUnknownBlocks() { return Collections.unmodifiableMap(unknownBlocks); }
    public boolean hasUnknownBlocks() { return !unknownBlocks.isEmpty(); }

    public boolean isFromFuture() {
        if (dataVersion <= 0) return false;
        return dataVersion > NbtCompat.currentDataVersion();
    }

    /**
     * Returns the offset that was subtracted during normalization.
     * Add this to Litematica's placement origin to get the correct
     * anchor for world ↔ schematic coordinate conversion.
     */
    public int getOriginOffsetX() { return originOffsetX; }
    public int getOriginOffsetY() { return originOffsetY; }
    public int getOriginOffsetZ() { return originOffsetZ; }

    // ── private ctor ────────────────────────────────────────────────────

    private LitematicaSchematic(String name, String author, int sizeX, int sizeY, int sizeZ,
                                int totalNonAir, List<Region> regions,
                                int schemaVersion, int dataVersion,
                                Map<String, Integer> unknownBlocks,
                                int originOffsetX, int originOffsetY, int originOffsetZ) {
        this.name = name;
        this.author = author;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.totalNonAir = totalNonAir;
        this.regions = regions;
        this.schemaVersion = schemaVersion;
        this.dataVersion = dataVersion;
        this.unknownBlocks = unknownBlocks;
        this.originOffsetX = originOffsetX;
        this.originOffsetY = originOffsetY;
        this.originOffsetZ = originOffsetZ;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Region
    // ═══════════════════════════════════════════════════════════════════

    /**
     * One named region inside the schematic.  Stores the palette and the
     * bit-packed block-state index array.
     */
    public static final class Region {
        final String regionName;
        public int originX, originY, originZ;
        public final int absX, absY, absZ;
        private final BlockState[] palette;
        private final long[] blockStates;
        private final int bitsPerEntry;
        private final long mask;

        private Region(String regionName,
                       int originX, int originY, int originZ,
                       int absX, int absY, int absZ,
                       BlockState[] palette, long[] blockStates) {
            this.regionName = regionName;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.absX = absX;
            this.absY = absY;
            this.absZ = absZ;
            this.palette = palette;
            this.blockStates = blockStates;
            this.bitsPerEntry = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.length - 1));
            this.mask = (1L << bitsPerEntry) - 1;
        }

        static Region parse(NbtCompound nbt, String regionName, Map<String, Integer> unknownBlocks) {
            NbtCompound posNbt  = NbtCompat.getCompound(nbt, "Position");
            NbtCompound sizeNbt = NbtCompat.getCompound(nbt, "Size");

            int posX  = NbtCompat.getInt(posNbt, "x");
            int posY  = NbtCompat.getInt(posNbt, "y");
            int posZ  = NbtCompat.getInt(posNbt, "z");
            int sizeX = NbtCompat.getInt(sizeNbt, "x");
            int sizeY = NbtCompat.getInt(sizeNbt, "y");
            int sizeZ = NbtCompat.getInt(sizeNbt, "z");

            int originX = sizeX > 0 ? posX : posX + sizeX + 1;
            int originY = sizeY > 0 ? posY : posY + sizeY + 1;
            int originZ = sizeZ > 0 ? posZ : posZ + sizeZ + 1;
            int absX = Math.abs(sizeX);
            int absY = Math.abs(sizeY);
            int absZ = Math.abs(sizeZ);

            NbtList paletteNbt = NbtCompat.getList(nbt, "BlockStatePalette", NbtElement.COMPOUND_TYPE);
            BlockState[] palette = new BlockState[paletteNbt.size()];
            for (int i = 0; i < paletteNbt.size(); i++) {
                /*? if >=1.21.5 {*//*
                palette[i] = parseBlockState(paletteNbt.getCompound(i).orElse(new NbtCompound()), unknownBlocks);
                *//*?} else {*/
                palette[i] = parseBlockState(paletteNbt.getCompound(i), unknownBlocks);
                /*?}*/
            }

            long[] states = NbtCompat.getLongArray(nbt, "BlockStates");

            return new Region(regionName, originX, originY, originZ, absX, absY, absZ, palette, states);
        }

        public BlockState getBlockState(int lx, int ly, int lz) {
            int index = ly * absX * absZ + lz * absX + lx;
            int paletteIndex = readPackedValue(index);
            if (paletteIndex < 0 || paletteIndex >= palette.length) {
                return Blocks.AIR.getDefaultState();
            }
            return palette[paletteIndex];
        }

        int countNonAir() {
            int total = absX * absY * absZ;
            int count = 0;
            for (int i = 0; i < total; i++) {
                int idx = readPackedValue(i);
                if (idx >= 0 && idx < palette.length && !palette[idx].isAir()) {
                    count++;
                }
            }
            return count;
        }

        private int readPackedValue(int index) {
            long bitStart  = (long) index * bitsPerEntry;
            int  longIndex = (int) (bitStart >> 6);
            int  bitOffset = (int) (bitStart & 63);

            if (longIndex >= blockStates.length) return 0;

            long value = (blockStates[longIndex] >>> bitOffset) & mask;

            if (bitOffset + bitsPerEntry > 64 && longIndex + 1 < blockStates.length) {
                value |= (blockStates[longIndex + 1] << (64 - bitOffset)) & mask;
            }
            return (int) value;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Block-state NBT → BlockState
    // ═══════════════════════════════════════════════════════════════════

    private static final Set<String> AIR_IDS = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air"
    );

    private static BlockState parseBlockState(NbtCompound nbt, Map<String, Integer> unknownBlocks) {
        String blockName = NbtCompat.getString(nbt, "Name");

        Identifier blockId = Identifier.tryParse(blockName);
        if (blockId == null) {
            LOGGER.warn("Malformed block identifier '{}' — substituting air", blockName);
            unknownBlocks.merge(blockName, 1, Integer::sum);
            return Blocks.AIR.getDefaultState();
        }

        Block block = Registries.BLOCK.get(blockId);

        if (block == Blocks.AIR && !AIR_IDS.contains(blockName)) {
            unknownBlocks.merge(blockName, 1, Integer::sum);
            return Blocks.AIR.getDefaultState();
        }

        BlockState state = block.getDefaultState();

        if (NbtCompat.contains(nbt, "Properties", NbtElement.COMPOUND_TYPE)) {
            NbtCompound propsNbt = NbtCompat.getCompound(nbt, "Properties");
            StateManager<Block, BlockState> sm = block.getStateManager();
            for (String key : propsNbt.getKeys()) {
                Property<?> property = sm.getProperty(key);
                if (property != null) {
                    String value = NbtCompat.getString(propsNbt, key);
                    BlockState applied = applyProperty(state, property, value);
                    if (applied == state) {
                        LOGGER.debug("Could not apply property '{}={}' for block '{}' — using default",
                                key, value, blockName);
                    }
                    state = applied;
                } else {
                    LOGGER.debug("Block '{}' has no property '{}' in this MC version — skipping",
                            blockName, key);
                }
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(
            BlockState state, Property<T> property, String value) {
        return property.parse(value).map(v -> state.with(property, v)).orElse(state);
    }
}

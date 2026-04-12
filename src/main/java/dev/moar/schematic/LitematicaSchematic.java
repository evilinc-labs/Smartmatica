package dev.moar.schematic;

import dev.moar.util.NbtCompat;
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.Block;
*//*?} else {*/
import net.minecraft.block.Block;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.BlockState;
*//*?} else {*/
import net.minecraft.block.BlockState;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.Blocks;
*//*?} else {*/
import net.minecraft.block.Blocks;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.nbt.CompoundTag;
*//*?} else {*/
import net.minecraft.nbt.NbtCompound;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.nbt.Tag;
*//*?} else {*/
import net.minecraft.nbt.NbtElement;
/*?}*/
import net.minecraft.nbt.NbtIo;
/*? if >=26.1 {*//*
import net.minecraft.nbt.ListTag;
*//*?} else {*/
import net.minecraft.nbt.NbtList;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.nbt.NbtAccounter;
*//*?} else {*/
import net.minecraft.nbt.NbtSizeTracker;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.registries.BuiltInRegistries;
*//*?} else {*/
import net.minecraft.registry.Registries;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.StateDefinition;
*//*?} else {*/
import net.minecraft.state.StateManager;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.Property;
*//*?} else {*/
import net.minecraft.state.property.Property;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.resources.Identifier;
*//*?} else {*/
import net.minecraft.util.Identifier;
/*?}*/
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

// Parses .litematic files. Supports schema v5-7 with unknown-block fallback.
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

    // Offset subtracted during normalization. Add to anchor for correct lookups.
    private final int originOffsetX, originOffsetY, originOffsetZ;

    // public API

    // Load and normalize region origins to (0,0,0).
    // Max decompressed NBT size (zip-bomb guard).
    private static final long MAX_NBT_BYTES = 256L * 1024 * 1024;

    public static LitematicaSchematic load(Path path) throws IOException {
        /*? if >=26.1 {*//*
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(MAX_NBT_BYTES));
        *//*?} else {*/
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.of(MAX_NBT_BYTES));
        /*?}*/

        // schema & data version
        int schemaVersion = NbtCompat.getInt(root, "Version", -1);

        if (schemaVersion != -1 && (schemaVersion < 4 || schemaVersion > 7)) {
            LOGGER.warn("Litematic schema version {} may not be fully supported (expected 5-7)", schemaVersion);
        }

        // metadata
        /*? if >=26.1 {*//*
        CompoundTag meta = NbtCompat.getCompound(root, "Metadata");
        *//*?} else {*/
        NbtCompound meta = NbtCompat.getCompound(root, "Metadata");
        /*?}*/
        String name   = NbtCompat.getString(meta, "Name", path.getFileName().toString());
        String author = NbtCompat.getString(meta, "Author", "Unknown");

        int dataVersion = NbtCompat.getInt(meta, "MinecraftDataVersion", -1);

        int currentDataVersion = NbtCompat.currentDataVersion();
        if (dataVersion > currentDataVersion) {
            LOGGER.warn("Schematic '{}' was created in a newer Minecraft version (data version {} vs current {}). "
                    + "Some blocks may not be recognized.", name, dataVersion, currentDataVersion);
        }

        // regions
        /*? if >=26.1 {*//*
        CompoundTag regionsNbt = NbtCompat.getCompound(root, "Regions");
        *//*?} else {*/
        NbtCompound regionsNbt = NbtCompat.getCompound(root, "Regions");
        /*?}*/
        /*? if >=26.1 {*//*
        Set<String> regionKeys = regionsNbt.keySet();
        *//*?} else {*/
        Set<String> regionKeys = regionsNbt.getKeys();
        /*?}*/
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
            /*? if >=26.1 {*//*
            CompoundTag regionNbt = NbtCompat.getCompound(regionsNbt, key);
            *//*?} else {*/
            NbtCompound regionNbt = NbtCompat.getCompound(regionsNbt, key);
            /*?}*/
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
     * Returns the target BlockState at schematic-local coordinates.
     * Coordinates are relative to the normalized origin (0, 0, 0).
     * Returns Blocks.AIR default state if the position is outside
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
        /*? if >=26.1 {*//*
        return Blocks.AIR.defaultBlockState();
        *//*?} else {*/
        return Blocks.AIR.getDefaultState();
        /*?}*/
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

    // getters

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

    // private ctor

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

    // --- Region

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

        /*? if >=26.1 {*//*
        static Region parse(CompoundTag nbt, String regionName, Map<String, Integer> unknownBlocks) {
        *//*?} else {*/
        static Region parse(NbtCompound nbt, String regionName, Map<String, Integer> unknownBlocks) {
        /*?}*/
            /*? if >=26.1 {*//*
            CompoundTag posNbt  = NbtCompat.getCompound(nbt, "Position");
            *//*?} else {*/
            NbtCompound posNbt  = NbtCompat.getCompound(nbt, "Position");
            /*?}*/
            /*? if >=26.1 {*//*
            CompoundTag sizeNbt = NbtCompat.getCompound(nbt, "Size");
            *//*?} else {*/
            NbtCompound sizeNbt = NbtCompat.getCompound(nbt, "Size");
            /*?}*/

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

            /*? if >=26.1 {*//*
            ListTag paletteNbt = NbtCompat.getList(nbt, "BlockStatePalette", Tag.TAG_COMPOUND);
            *//*?} else {*/
            NbtList paletteNbt = NbtCompat.getList(nbt, "BlockStatePalette", NbtElement.COMPOUND_TYPE);
            /*?}*/
            BlockState[] palette = new BlockState[paletteNbt.size()];
            for (int i = 0; i < paletteNbt.size(); i++) {
                /*? if >=26.1 {*//*
                palette[i] = parseBlockState(paletteNbt.getCompound(i).orElse(new CompoundTag()), unknownBlocks);
                *//*?} else if >=1.21.5 {*//*
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
                /*? if >=26.1 {*//*
                return Blocks.AIR.defaultBlockState();
                *//*?} else {*/
                return Blocks.AIR.getDefaultState();
                /*?}*/
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

    // --- Block-state NBT → BlockState

    private static final Set<String> AIR_IDS = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air"
    );

    /*? if >=26.1 {*//*
    private static BlockState parseBlockState(CompoundTag nbt, Map<String, Integer> unknownBlocks) {
    *//*?} else {*/
    private static BlockState parseBlockState(NbtCompound nbt, Map<String, Integer> unknownBlocks) {
    /*?}*/
        String blockName = NbtCompat.getString(nbt, "Name");

        Identifier blockId = Identifier.tryParse(blockName);
        if (blockId == null) {
            LOGGER.warn("Malformed block identifier '{}' — substituting air", blockName);
            unknownBlocks.merge(blockName, 1, Integer::sum);
            /*? if >=26.1 {*//*
            return Blocks.AIR.defaultBlockState();
            *//*?} else {*/
            return Blocks.AIR.getDefaultState();
            /*?}*/
        }

        /*? if >=26.1 {*//*
        Block block = BuiltInRegistries.BLOCK.getValue(blockId);
        *//*?} else {*/
        Block block = Registries.BLOCK.get(blockId);
        /*?}*/

        if (block == Blocks.AIR && !AIR_IDS.contains(blockName)) {
            unknownBlocks.merge(blockName, 1, Integer::sum);
            /*? if >=26.1 {*//*
            return Blocks.AIR.defaultBlockState();
            *//*?} else {*/
            return Blocks.AIR.getDefaultState();
            /*?}*/
        }

        /*? if >=26.1 {*//*
        BlockState state = block.defaultBlockState();
        *//*?} else {*/
        BlockState state = block.getDefaultState();
        /*?}*/

        /*? if >=26.1 {*//*
        if (NbtCompat.contains(nbt, "Properties", Tag.TAG_COMPOUND)) {
        *//*?} else {*/
        if (NbtCompat.contains(nbt, "Properties", NbtElement.COMPOUND_TYPE)) {
        /*?}*/
            /*? if >=26.1 {*//*
            CompoundTag propsNbt = NbtCompat.getCompound(nbt, "Properties");
            *//*?} else {*/
            NbtCompound propsNbt = NbtCompat.getCompound(nbt, "Properties");
            /*?}*/
            /*? if >=26.1 {*//*
            StateDefinition<Block, BlockState> sm = block.getStateDefinition();
            *//*?} else {*/
            StateManager<Block, BlockState> sm = block.getStateManager();
            /*?}*/
            /*? if >=26.1 {*//*
            for (String key : propsNbt.keySet()) {
            *//*?} else {*/
            for (String key : propsNbt.getKeys()) {
            /*?}*/
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
        /*? if >=26.1 {*//*
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
        *//*?} else {*/
        return property.parse(value).map(v -> state.with(property, v)).orElse(state);
        /*?}*/
    }
}

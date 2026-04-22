package dev.moar.printer;

import dev.moar.chest.ChestManager;
import dev.moar.schematic.LitematicaDetector;
import dev.moar.schematic.LitematicaSchematic;
import dev.moar.schematic.PrinterCheckpoint;
import dev.moar.schematic.PrinterResourceManager;
import dev.moar.util.BlockDependency;
import dev.moar.util.ChatHelper;
import dev.moar.util.PathWalker;
import dev.moar.util.PlacementEngine;
import dev.moar.util.PrinterDatabase;
import dev.moar.MoarMod;
import dev.moar.util.SneakOverride;
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.piston.*;
*//*?} else {*/
import net.minecraft.block.*;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.BedPart;
*//*?} else {*/
import net.minecraft.block.enums.BedPart;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
*//*?} else {*/
import net.minecraft.block.enums.DoubleBlockHalf;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
*//*?} else {*/
import net.minecraft.state.property.Properties;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.block.state.properties.Property;
*//*?} else {*/
import net.minecraft.state.property.Property;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.client.player.LocalPlayer;
*//*?} else {*/
import net.minecraft.client.network.ClientPlayerEntity;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.component.DataComponents;
*//*?} else {*/
import net.minecraft.component.DataComponentTypes;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.component.ItemContainerContents;
*//*?} else {*/
import net.minecraft.component.type.ContainerComponent;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.entity.player.Inventory;
*//*?} else {*/
import net.minecraft.entity.player.PlayerInventory;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.BlockItem;
*//*?} else {*/
import net.minecraft.item.BlockItem;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Item;
*//*?} else {*/
import net.minecraft.item.Item;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.ItemStack;
*//*?} else {*/
import net.minecraft.item.ItemStack;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.item.Items;
*//*?} else {*/
import net.minecraft.item.Items;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.registries.BuiltInRegistries;
*//*?} else {*/
import net.minecraft.registry.Registries;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ChestMenu;
*//*?} else {*/
import net.minecraft.screen.GenericContainerScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.AbstractContainerMenu;
*//*?} else {*/
import net.minecraft.screen.ScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ShulkerBoxMenu;
*//*?} else {*/
import net.minecraft.screen.ShulkerBoxScreenHandler;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.inventory.ContainerInput;
*//*?} else {*/
import net.minecraft.screen.slot.SlotActionType;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionResult;
*//*?} else {*/
import net.minecraft.util.ActionResult;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.InteractionHand;
*//*?} else {*/
import net.minecraft.util.Hand;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.resources.Identifier;
*//*?} else {*/
import net.minecraft.util.Identifier;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.BlockHitResult;
*//*?} else {*/
import net.minecraft.util.hit.BlockHitResult;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.core.Direction;
*//*?} else {*/
import net.minecraft.util.math.Direction;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.util.Mth;
*//*?} else {*/
import net.minecraft.util.math.MathHelper;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.phys.Vec3;
*//*?} else {*/
import net.minecraft.util.math.Vec3d;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.material.FluidState;
*//*?} else {*/
import net.minecraft.fluid.FluidState;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.world.World;
/*?}*/
/*? if >=26.1 {*//*
import net.minecraft.resources.ResourceKey;
*//*?} else {*/
import net.minecraft.registry.RegistryKey;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// Schematic block placer. Manual mode: reach only. AutoBuild: walk, place, resupply.
public class SchematicPrinter {

    // enums

    public enum SortMode {
        NEAREST,
        BOTTOM_UP,
        TOP_DOWN
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR");

    public enum AutoState {
        CLEARING_AREA,
        WALKING_TO_CLEAR,
        WALKING_TO_DUMP,
        DUMPING,
        BUILDING,
        WALKING_TO_BUILD,
        WALKING_TO_SUPPLY,
        RESTOCKING,
        UNLOADING_SHULKER,
        WALKING_BACK,
        CLEANING_SCAFFOLD,
        IDLE
    }

    // settings

    private int bps = 13;
    private double range = 4.2;
    private boolean swapItems = true;
    private boolean printInAir = true;
    private SortMode sortMode = SortMode.BOTTOM_UP;
    private boolean statusMessages = true;
    private boolean autoBuild = true;

    // state

    private boolean enabled = false;

    // schematic state

    private LitematicaSchematic schematic;
    private BlockPos anchor;
    private int blocksPlaced;
    private String schematicFile;
    /** Dimension the schematic was loaded in — pauses on dimension change. */
    /*? if >=26.1 {*//*
    private ResourceKey<Level> buildDimension;
    *//*?} else {*/
    private RegistryKey<World> buildDimension;
    /*?}*/

    // auto-build state

    private AutoState autoState = AutoState.IDLE;
    private BlockPos lastBuildPos;
    private BlockPos supplyTarget;
    private Set<String> neededItems;
    private int restockWaitTicks;
    /** Ticks waiting for server to sync chest contents in RESTOCKING. */
    private int chestSyncDelay;
    /** Consecutive restock failures without grabbing any items. */
    private int restockFailures;
    /** Chests Baritone couldn't reach — LRU-evicted at 64. */
    private final Set<BlockPos> unreachableChests = Collections.newSetFromMap(
            new LinkedHashMap<>(32, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
                    return size() > 64;
                }
            });
    private int idleScanCooldown;
    private int noProgressTicks;
    private Set<Item> lastMissingItems = new HashSet<>();
    private int missingItemMsgCooldown;
    /** Items we've given up restocking — LRU-evicted at 64. */
    private final Set<Item> skippedItems = Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<Item, Boolean> eldest) {
                    return size() > 64;
                }
            });
    private final Set<BlockPos> failedZones = Collections.newSetFromMap(
            new LinkedHashMap<>(32, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
                    return size() > 64;
                }
            });
    /** Consecutive walk failures — triggers GoalNear fallback. */
    private int walkFailCount;
    private static final int MAX_WALK_RETRIES = 3;
    /** True if we already tried walkToWithPlacement and it failed — prevents retry loop. */
    private boolean triedPlacementWalk;
    /** The target zone we last navigated toward — prevent re-targeting. */
    private BlockPos lastWalkTargetZone;
    private int walkAttemptCooldown;
    private int stuckCycles;
    private static final int MAX_STUCK_CYCLES = 10;
    /** Ticks until next skippedItems re-evaluation. */
    private int skippedItemRecheckCooldown;
    private static final int SKIPPED_RECHECK_INTERVAL = 200; // ~10s
    /** Max consecutive server-rejected placements before repositioning. */
    private static final int SERVER_REJECT_THRESHOLD = 6;
    /** True during the redstone placement pass. */
    private boolean redstonePass;
    /** True during the liquid placement pass. */
    private boolean liquidPass;
    /** True if waypoint-based supply retry was already attempted. */
    private boolean triedWaypointRestock;
    /** True if linear-waypoint supply retry was already attempted. */
    private boolean triedLinearRestock;
    /** True if placement-walk supply retry was already attempted. */
    private boolean triedPlacementRestock;

    /** Multi-phase descent: 0=none, 1=horizontal, 2=descend, 3=approach. */
    private int supplyDescentPhase;
    private BlockPos supplyDescentTarget;

    // scaffold tracking state
    private BlockPos scaffoldBreakTarget;
    private int scaffoldBreakTicks;
    private float scaffoldSavedYaw, scaffoldSavedPitch;
    private static final int MAX_SCAFFOLD_BREAK_TICKS = 60;

    // area-clearing state
    private BlockPos clearBreakTarget;
    private int clearBreakTicks;
    private float clearSavedYaw, clearSavedPitch;
    private static final int MAX_CLEAR_BREAK_TICKS = 200;
    private int clearBlocksBroken;
    private boolean clearingDone;
    private int clearCooldownTicks;
    private int scaffoldCooldownTicks;
    private static final int BREAK_COOLDOWN_TICKS = 6;
    /** Consecutive clearing failures — triggers stall recovery on threshold. */
    private int consecutiveClearFailures;
    private static final int MAX_CONSECUTIVE_CLEAR_FAILURES = 20;
    /** Stall-recovery resets done — gives up after MAX. */
    private int clearStallResets;
    private static final int MAX_CLEAR_STALL_RESETS = 3;
    /** Unreachable clearing targets — LRU-evicted at 64. */
    private final Set<BlockPos> failedClearTargets = Collections.newSetFromMap(
            new LinkedHashMap<>(32, 0.75f, false) {
                @Override protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
                    return size() > 64;
                }
            });

    // shulker unloading state
    /** Sub-phase: 0=find, 1=swap, 2=place, 3=wait, 4=open, 5=take, 6=break, 7=breaking, 8=pickup. */
    private int shulkerUnloadPhase;
    private BlockPos shulkerPlacePos;
    private int shulkerUnloadTicks;
    private int shulkerTotalTicks;
    private static final int MAX_SHULKER_PHASE_TICKS = 80;
    private static final int MAX_SHULKER_TOTAL_TICKS = 600;
    private int shulkerHotbarSlot = -1;
    private float shulkerSavedYaw, shulkerSavedPitch;
    private static final int SHULKER_PLACE_DELAY = 10;
    private static final int SHULKER_PICKUP_DELAY = 15;
    private int shulkerSyncDelay;
    private int shulkerUnloadFailures;
    private static final int MAX_SHULKER_FAILURES = 3;
    /** Skip shulker shortcut — walk to supply chest instead. */
    private boolean shulkerNoSpaceSkipped;
    private int platformBuildAttempts;
    private static final int MAX_PLATFORM_ATTEMPTS = 3;
    /** Platform block position — tracked for scaffold cleanup. */
    private BlockPos platformBlockPos;
    private int shulkerOpenRetries;
    private static final int MAX_SHULKER_OPEN_RETRIES = 3;
    private static final int SCAFFOLD_SCAN_INTERVAL = 10;
    private int scaffoldScanCooldown;

    // dump state — depositing mined items during clearing
    /** Position we were clearing before the dump detour. */
    private BlockPos preDumpClearPos;
    /** The dump chest we're walking to / depositing into. */
    private BlockPos dumpTarget;
    /** Ticks waiting for the dump chest screen to open. */
    private int dumpWaitTicks;
    /** Ticks since the dump chest screen first appeared. */
    private int dumpSyncDelay;
    private static final int DUMP_SYNC_DELAY = 3;

    private int placementCheckCooldown;

    /** Ticks until next anchor correlation check (starts at 1). */
    private int anchorCorrelationCooldown;
    /** Interval (ticks) between automatic SchematicWorld anchor checks. */
    private static final int ANCHOR_CORRELATION_INTERVAL = 200;
    /** Whether the anchor has been confirmed via SchematicWorld correlation. */
    private boolean anchorCorrelated;

    /** True when loaded via Litematica auto-detect. */
    private boolean autoDetected;

    private static final int RESTOCK_THRESHOLD = 64;
    private static final int MISSING_MSG_COOLDOWN = 100;
    private static final int CHEST_OPEN_TIMEOUT = 40;
    private static final int IDLE_SCAN_INTERVAL = 200;
    private static final int NO_PROGRESS_TIMEOUT = 100;
    /** Delay for server to sync chest contents after screen opens. */
    private static final int CHEST_SYNC_DELAY = 3;
    private static final int MAX_RESTOCK_FAILURES = 6;

    // cached schematic scan results
    /** TTL for full-schematic remaining-block scans. */
    private static final int REMAINING_CACHE_TTL = 100; // ~5 seconds
    private long remainingCacheTick = Long.MIN_VALUE;
    private int  cachedCountRemaining = -1;
    private long solidsCacheTick = Long.MIN_VALUE;
    private boolean cachedHasSolids;
    private long redstoneCacheTick = Long.MIN_VALUE;
    private boolean cachedHasRedstone;
    private long liquidsCacheTick = Long.MIN_VALUE;
    private boolean cachedHasLiquids;

    // toggle / lifecycle

    public boolean isEnabled() { return enabled; }

    public void toggle() {
        if (enabled) {
            disable();
        } else {
            enable();
        }
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !this.enabled) enable();
        else if (!enabled && this.enabled) disable();
    }

    private void enable() {
        enabled = true;

        // Sync with Litematica: full re-detect if nothing loaded,
        // anchor-only sync for manually loaded schematics.
        boolean litematicaSynced = false;
        if (schematic == null || anchor == null || autoDetected) {
            if (tryAutoDetect()) {
                ChatHelper.labelled("Printer", "§aLoaded §f" + schematic.getName()
                        + " §7(" + schematic.getTotalNonAir() + " blocks)");
                litematicaSynced = true;
            } else if (schematic == null || anchor == null) {
                ChatHelper.info("§cNo schematic loaded. Use /printer load <file> or load one in Litematica.");
            }
        } else {
            // Manual load — resync anchor only (avoid replacing schematic).
            if (trySyncAnchor()) {
                litematicaSynced = true;
            } else {
                ChatHelper.info("§7Anchor unchanged — SchematicWorld correlation "
                        + "will auto-align on next tick.");
            }
        }

        // SchematicWorld correlation will auto-align on next tick if needed.

        if (schematic != null && anchor != null) {
            ChatHelper.info("Printing §a" + schematic.getName()
                    + "§f (" + schematic.getTotalNonAir() + " blocks)");
            ChatHelper.info("Anchor: §e" + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ());
            int x2 = anchor.getX() + schematic.getSizeX() - 1;
            int y2 = anchor.getY() + schematic.getSizeY() - 1;
            int z2 = anchor.getZ() + schematic.getSizeZ() - 1;
            ChatHelper.info("Region: §7("
                    + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ()
                    + ") → (" + x2 + ", " + y2 + ", " + z2 + ")");
            warnIfAnchorSuspicious();
            if (autoBuild) { 
                ChatHelper.info("§bAutoBuild §aenabled §7— clearing illegal blocks, then building.");
            }
        }

        blocksPlaced = 0;
        clearBlocksBroken = 0;
        clearingDone = false;
        clearBreakTarget = null;
        consecutiveClearFailures = 0;
        clearStallResets = 0;
        failedClearTargets.clear();
        autoState = AutoState.CLEARING_AREA;
        noProgressTicks = 0;
        idleScanCooldown = 0;
        failedZones.clear();
        walkFailCount = 0;
        triedPlacementWalk = false;
        lastWalkTargetZone = null;
        walkAttemptCooldown = 0;
        stuckCycles = 0;
        restockFailures = 0;
        unreachableChests.clear();
        skippedItems.clear();
        redstonePass = false;
        liquidPass = false;
        triedWaypointRestock = false;
        triedLinearRestock = false;
        triedPlacementRestock = false;
        shulkerNoSpaceSkipped = false;
        placementCheckCooldown = 0;
        skippedItemRecheckCooldown = SKIPPED_RECHECK_INTERVAL;
        preDumpClearPos = null;
        dumpTarget = null;
        dumpWaitTicks = 0;
        dumpSyncDelay = 0;
        // If Litematica placement data provided the anchor, trust it
        // and skip the immediate hologram correlation — the heuristic
        // scan can produce wrong anchors when many blocks share state.
        if (litematicaSynced) {
            anchorCorrelationCooldown = ANCHOR_CORRELATION_INTERVAL;
            anchorCorrelated = true;
        } else {
            anchorCorrelationCooldown = 1; // hologram scan on next tick
            anchorCorrelated = false;
        }

        PlacementEngine.clearCorrectionHistory();
        PlacementEngine.resetRejectionCounters();

        // If the build site is far away (unloaded chunks), start
        // walking there immediately instead of waiting for the
        // no-progress timeout cascade.
        if (autoBuild && schematic != null && anchor != null) {
            /*? if >=26.1 {*//*
            Minecraft client = Minecraft.getInstance();
            *//*?} else {*/
            MinecraftClient client = MinecraftClient.getInstance();
            /*?}*/
            /*? if >=26.1 {*//*
            if (client.level != null && client.player != null) {
            *//*?} else {*/
            if (client.world != null && client.player != null) {
            /*?}*/
                // Check for illegal blocks first (clearing phase)
                /*? if >=26.1 {*//*
                BlockPos clearTarget = findNextClearTarget(client.player, client.level);
                *//*?} else {*/
                BlockPos clearTarget = findNextClearTarget(client.player, client.world);
                /*?}*/
                if (clearTarget == null) {
                    // No illegal blocks — skip to build phase
                    clearingDone = true;
                }
                /*? if >=26.1 {*//*
                BlockPos zone = findNextBuildZone(client.player, client.level);
                *//*?} else {*/
                BlockPos zone = findNextBuildZone(client.player, client.world);
                /*?}*/
                if (zone == null && clearTarget == null) {
                    // No work in loaded chunks — check unloaded
                    /*? if >=26.1 {*//*
                    BlockPos unloaded = findUnloadedBuildZone(client.player, client.level);
                    *//*?} else {*/
                    BlockPos unloaded = findUnloadedBuildZone(client.player, client.world);
                    /*?}*/
                    if (unloaded != null) {
                        ChatHelper.info("§bBuild site not loaded — walking there...");
                        PathWalker.walkToNearby(unloaded, (int) Math.ceil(range));
                        autoState = AutoState.WALKING_TO_CLEAR;
                    }
                }
            }
        }
    }

    private void disable() {
        enabled = false;
        PrinterDatabase.flushScaffoldIfDirty(); // persist any pending scaffold data
        saveCheckpoint();
        PlacementEngine.reset();
        PathWalker.stop();
        autoState = AutoState.IDLE;
        SneakOverride.setForceSneak(false); // always release mixin sneak
        SneakOverride.setForceAbsoluteSneak(false);

        if (statusMessages) {
            ChatHelper.info("Stopped. §e" + blocksPlaced + "§f blocks placed this session.");
        }
    }

    /** Release all accumulated state on world disconnect to prevent leaks. */
    public void onDisconnect() {
        if (enabled) disable();
        unreachableChests.clear();
        skippedItems.clear();
        failedZones.clear();
        failedClearTargets.clear();
        lastMissingItems.clear();
    }

    // LITEMATICA AUTO-DETECTION

    public boolean tryAutoDetect() {
        List<LitematicaDetector.DetectedPlacement> placements =
                LitematicaDetector.detectPlacements();
        if (placements.isEmpty()) return false;

        int sameFilePlacementCount = 0;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        *//*?} else {*/
        BlockPos playerPos = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
        /*?}*/

        // Pick the closest placement to the player, but skip any with
        // origin (0,0,0) that is far from the player — that's almost
        // always an un-moved default Litematica placement.
        LitematicaDetector.DetectedPlacement best = null;
        double bestDist = Double.MAX_VALUE;
        for (LitematicaDetector.DetectedPlacement p : placements) {
            boolean originIsZero = p.originX() == 0 && p.originY() == 0 && p.originZ() == 0;
            double dx = p.originX() - playerPos.getX();
            double dz = p.originZ() - playerPos.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            // Skip default-origin placements if the player is far from spawn
            if (originIsZero && dist > 100) {
                LOGGER.warn("Skipping placement '{}' — origin is (0,0,0) and player is {} blocks away",
                        p.name(), (int) dist);
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }

        if (best == null) {
            // All placements were skipped (origin-zero, far from player)
            if (!placements.isEmpty()) {
                ChatHelper.info("§e⚠ Found §f" + placements.size()
                        + "§e Litematica placement(s), but all appear to be at world origin."
                        + "\n§7Move your placement in Litematica to the build site,"
                        + " or use §f/printer load <file>§7 and §f/printer here§7.");
            }
            return false;
        }

        LitematicaDetector.DetectedPlacement placement = best;
        for (LitematicaDetector.DetectedPlacement p : placements) {
            if (p.schematicPath().getFileName().equals(placement.schematicPath().getFileName())) {
                sameFilePlacementCount++;
            }
        }

        // If the schematic file doesn't exist on disk, we can't load it
        // — but we can still use the origin to set the anchor, provided
        // a schematic was already loaded via /printer load.
        if (!Files.exists(placement.schematicPath())) {
            if (schematic != null) {
                this.anchor = new BlockPos(
                        placement.originX() + schematic.getOriginOffsetX(),
                        placement.originY() + schematic.getOriginOffsetY(),
                        placement.originZ() + schematic.getOriginOffsetZ());
                this.autoDetected = true;
                warnIfAnchorSuspicious();
                return true;
            }
            ChatHelper.info("§cDetected placement file not found on disk: §7"
                    + placement.schematicPath().getFileName());
            return false;
        }

        try {
            this.schematic = LitematicaSchematic.load(placement.schematicPath());
            // Litematica's placement origin refers to the schematic's original
            // reference point (0,0,0).  After load() normalizes region origins
            // the min corner sits at (0,0,0), so we must shift the anchor by
            // the normalization offset so that (worldPos - anchor) yields the
            // normalized coordinates that getBlockState() expects.
            this.anchor = new BlockPos(
                    placement.originX() + schematic.getOriginOffsetX(),
                    placement.originY() + schematic.getOriginOffsetY(),
                    placement.originZ() + schematic.getOriginOffsetZ());

            // Multiple enabled placements can point to the same .litematic.
            // In that case, "closest to player" is often the old build site.
            // Use SchematicWorld correlation immediately to disambiguate.
            if (sameFilePlacementCount > 1) {
                BlockPos correlated = LitematicaDetector.detectAnchorFromSchematicWorld(schematic);
                if (correlated != null) {
                    this.anchor = correlated;
                    LOGGER.info("Disambiguated {} same-file placements via hologram correlation: {}",
                            sameFilePlacementCount, correlated);
                }
            }
            this.blocksPlaced = 0;
            this.schematicFile = placement.schematicPath().getFileName().toString();
            this.autoDetected = true;
            /*? if >=26.1 {*//*
            this.buildDimension = mc.level != null ? mc.level.dimension() : null;
            *//*?} else {*/
            this.buildDimension = mc.world != null ? mc.world.getRegistryKey() : null;
            /*?}*/
            PrinterDatabase.clearScaffold();
            MoarMod.getChestManager().clearSessionData();
            warnIfAnchorSuspicious();
            return true;
        } catch (IOException e) {
            ChatHelper.info("§cFailed to load detected schematic: " + e.getMessage());
            return false;
        }
    }

    /** Syncs anchor to the matching Litematica placement origin. */
    private boolean trySyncAnchor() {
        if (schematic == null || schematicFile == null) return false;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        List<LitematicaDetector.DetectedPlacement> placements =
                LitematicaDetector.detectPlacements();

        // Collect all filename-matching placements.
        // If there are multiple, this is ambiguous (common when users keep
        // old and new stacks of the same schematic enabled at once), so let
        // SchematicWorld correlation resolve it instead of guessing by distance.
        List<LitematicaDetector.DetectedPlacement> fileMatches = new ArrayList<>();
        for (LitematicaDetector.DetectedPlacement p : placements) {
            String placementFile = p.schematicPath().getFileName().toString();
            if (!placementFile.equals(schematicFile)) continue;
            fileMatches.add(p);
        }

        if (fileMatches.isEmpty()) return false;
        if (fileMatches.size() > 1) {
            LOGGER.debug("Anchor sync ambiguous: {} enabled placements match file '{}'",
                    fileMatches.size(), schematicFile);
            return false;
        }

        LitematicaDetector.DetectedPlacement bestMatch = fileMatches.get(0);

        if (bestMatch == null) return false;

        // Don't sync to a (0,0,0) origin if the player is far from it —
        // that's almost certainly an un-moved default placement.
        if (bestMatch.originX() == 0 && bestMatch.originY() == 0 && bestMatch.originZ() == 0) {
            if (mc.player != null) {
                double dx = bestMatch.originX() - mc.player.getX();
                double dz = bestMatch.originZ() - mc.player.getZ();
                if (Math.sqrt(dx * dx + dz * dz) > 100) {
                    LOGGER.warn("Ignoring anchor sync — placement origin is (0,0,0) and player is far away");
                    return false;
                }
            }
        }

        BlockPos newAnchor = new BlockPos(
                bestMatch.originX() + schematic.getOriginOffsetX(),
                bestMatch.originY() + schematic.getOriginOffsetY(),
                bestMatch.originZ() + schematic.getOriginOffsetZ());

        if (!newAnchor.equals(anchor)) {
            ChatHelper.info("§eSynced anchor with Litematica placement → §e"
                    + newAnchor.getX() + " " + newAnchor.getY() + " " + newAnchor.getZ());
            this.anchor = newAnchor;
        }
        return true;
    }

    /**
     * Check whether the current schematic is still present as an enabled
     * placement in Litematica.  Used by the periodic tick() validation.
     */
    private boolean isPlacementStillActive() {
        if (schematicFile == null) return false;

        for (LitematicaDetector.DetectedPlacement p :
                LitematicaDetector.detectPlacements()) {
            if (p.schematicPath().getFileName().toString().equals(schematicFile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Warn the player if the current anchor looks suspicious — e.g. very
     * far from the player or sitting at world origin (0, y, 0), which is
     * almost always an un-moved default Litematica placement.
     */
    private void warnIfAnchorSuspicious() {
        if (anchor == null) return;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc.player == null) return;

        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/

        // Check for origin-zero anchor (common Litematica default)
        if (anchor.getX() == 0 && anchor.getZ() == 0) {
            ChatHelper.info("§e\u26A0 Anchor is at world origin (0, "
                    + anchor.getY() + ", 0) — this is usually a default"
                    + " Litematica placement that wasn't moved.");
            ChatHelper.info("§7If this is wrong, move the placement in"
                    + " Litematica or use §f/printer here§7 at the build site.");
            return;
        }

        // Check horizontal distance from player to anchor
        double dx = anchor.getX() - playerPos.getX();
        double dz = anchor.getZ() - playerPos.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 500) {
            ChatHelper.info("§e\u26A0 Build site is §f" + (int) horizDist
                    + "§e blocks away! This may be a mis-positioned placement.");
            ChatHelper.info("§7Use §f/printer here§7 to re-anchor at your"
                    + " position, or verify in Litematica.");
        }
    }

    public static List<LitematicaDetector.DetectedPlacement> detectAllPlacements() {
        return LitematicaDetector.detectPlacements();
    }

    // SCHEMATIC MANAGEMENT

    public void loadSchematic(Path path, BlockPos anchor) throws IOException {
        this.schematic = LitematicaSchematic.load(path);
        this.anchor = anchor;
        this.blocksPlaced = 0;
        this.schematicFile = path.getFileName().toString();
        this.autoDetected = false;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        this.buildDimension = mc.level != null ? mc.level.dimension() : null;
        *//*?} else {*/
        this.buildDimension = mc.world != null ? mc.world.getRegistryKey() : null;
        /*?}*/
        PrinterDatabase.clearScaffold();
        MoarMod.getChestManager().clearSessionData();
    }

    public void unload() {
        this.schematic = null;
        this.anchor = null;
        this.blocksPlaced = 0;
        this.schematicFile = null;
        this.autoDetected = false;
        this.buildDimension = null;
        PrinterCheckpoint.clear();
        PrinterDatabase.clearScaffold();
        MoarMod.getChestManager().clearSessionData();
        PathWalker.stop();
        autoState = AutoState.IDLE;
        if (enabled) disable();
    }

    public boolean isLoaded()                         { return schematic != null && anchor != null; }
    public LitematicaSchematic getSchematic()          { return schematic; }
    public BlockPos getAnchor()                       { return anchor; }

    /**
     * Updates the build anchor.  If AutoBuild is actively walking to a
     * build zone, the walk is cancelled and the state machine resets to
     * BUILDING so the next tick re-evaluates from the new anchor.
     */
    public void setAnchor(BlockPos newAnchor) {
        BlockPos old = this.anchor;
        this.anchor = newAnchor;

        if (enabled && autoBuild && old != null && newAnchor != null
                && !old.equals(newAnchor)) {
            PathWalker.stop();
            clearingDone = false;
            clearBreakTarget = null;
            clearBlocksBroken = 0;
            consecutiveClearFailures = 0;
            clearStallResets = 0;
            failedClearTargets.clear();
            autoState = AutoState.CLEARING_AREA;
            noProgressTicks = 0;
            failedZones.clear();
            lastWalkTargetZone = null;
            walkFailCount = 0;
            triedPlacementWalk = false;
        }
    }

    public int getBlocksPlaced()                      { return blocksPlaced; }
    public AutoState getAutoState()                   { return autoState; }

    // settings accessors

    public int getBps()                 { return bps; }
    public void setBps(int bps)        { this.bps = Math.max(1, Math.min(20, bps)); }
    public double getRange()           { return range; }
    public void setRange(double range) { this.range = Math.max(2.0, Math.min(4.5, range)); }
    public boolean isSwapItems()       { return swapItems; }
    public void setSwapItems(boolean v){ this.swapItems = v; }
    public boolean isPrintInAir()      { return printInAir; }
    public void setPrintInAir(boolean v){ this.printInAir = v; }
    public SortMode getSortMode()      { return sortMode; }
    public void setSortMode(SortMode m){ this.sortMode = m; }
    public boolean isStatusMessages()  { return statusMessages; }
    public void setStatusMessages(boolean v){ this.statusMessages = v; }
    public boolean isAutoBuild()       { return autoBuild; }
    public void setAutoBuild(boolean v){
        if (this.autoBuild == v) return;
        this.autoBuild = v;

        // Switching modes while enabled can leave stale Baritone goals or
        // in-flight placement phases running. Reset both sides so manual
        // mode doesn't keep walking and auto mode re-evaluates cleanly.
        if (enabled) {
            PathWalker.stop();
            PlacementEngine.reset();
            noProgressTicks = 0;
            walkFailCount = 0;
            triedPlacementWalk = false;
            lastWalkTargetZone = null;
            walkAttemptCooldown = 0;
            stuckCycles = 0;
            if (this.autoBuild) {
                autoState = clearingDone ? AutoState.BUILDING : AutoState.CLEARING_AREA;
            } else {
                autoState = AutoState.IDLE;
            }
        }
    }

    // TICK (called from mod initializer)

    public void tick() {
        if (!enabled) return;
        if (!isLoaded()) return;

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        /*?}*/

        // Verify Litematica placement is still active (auto-detected only).
        if (autoDetected && schematicFile != null && ++placementCheckCooldown >= 100) {
            placementCheckCooldown = 0;
            if (!isPlacementStillActive()) {
                if (statusMessages) {
                    ChatHelper.info("§cLitematica placement unloaded — printer stopped.");
                }
                disable();
                unload();
                return;
            }
        }

        // In manual mode, use silent rotation to avoid camera jerk and
        // conflicting rotation states that cause server rubberbanding.
        // Forward BPS to the placement engine.
        PlacementEngine.setBps(bps);
        if (autoBuild) {
            PlacementEngine.setSilentRotation(false);
        } else {
            PlacementEngine.setSilentRotation(true);
        }

        // Periodic maintenance (every ~200 ticks ≈ 10 s)
        /*? if >=26.1 {*//*
        if (mc.level.getGameTime() % 200 == 0) {
        *//*?} else {*/
        if (mc.world.getTime() % 200 == 0) {
        /*?}*/
            PrinterDatabase.flushScaffoldIfDirty();
            PlacementEngine.pruneCompletedCorrections();
        }

        // Periodic anchor re-sync.  Prefer authoritative Litematica
        // placement data over the heuristic hologram correlation —
        // the hologram scan can produce wrong anchors when many blocks
        // share the same state (e.g. stone, dirt).
        if (schematic != null && --anchorCorrelationCooldown <= 0) {
            anchorCorrelationCooldown = ANCHOR_CORRELATION_INTERVAL;
            BlockPos prevAnchor = this.anchor;
            boolean synced = trySyncAnchor();
            if (!synced) {
                BlockPos correlated = LitematicaDetector.detectAnchorFromSchematicWorld(schematic);
                if (correlated != null) {
                    if (!correlated.equals(anchor)) {
                        this.anchor = correlated;
                        if (statusMessages) {
                            ChatHelper.info("§aAnchor auto-aligned from hologram blocks → §e"
                                    + correlated.getX() + " " + correlated.getY() + " " + correlated.getZ());
                        }
                    }
                    anchorCorrelated = true;
                }
            } else {
                anchorCorrelated = true;
            }
            // If the anchor changed (by either method), reset walk state
            if (anchor != null && !anchor.equals(prevAnchor)) {
                PathWalker.stop();
                if (autoBuild) {
                    autoState = clearingDone ? AutoState.BUILDING : AutoState.CLEARING_AREA;
                    noProgressTicks = 0;
                    failedZones.clear();
                    lastWalkTargetZone = null;
                }
            }
        }

        // Always drive the multi-tick placement pipeline
        PlacementEngine.tickVerification();
        if (PlacementEngine.isBusy()) {
            boolean placed = PlacementEngine.tick();
            if (placed) {
                blocksPlaced++;
                noProgressTicks = 0;
                // Invalidate cached remaining counts so the next query
                // reflects the newly placed block promptly.
                remainingCacheTick = Long.MIN_VALUE;
                solidsCacheTick = Long.MIN_VALUE;
                liquidsCacheTick = Long.MIN_VALUE;
                if (schematicFile != null) {
                    /*? if >=26.1 {*//*
                    PrinterCheckpoint.onBlockPlaced(schematicFile, anchor, blocksPlaced, mc.player.blockPosition());
                    *//*?} else {*/
                    PrinterCheckpoint.onBlockPlaced(schematicFile, anchor, blocksPlaced, mc.player.getBlockPos());
                    /*?}*/
                }
            }
            return;
        }

        if (autoBuild) {
            tickAutoBuild(mc);
        } else {
            /*? if >=26.1 {*//*
            if (mc.screen != null) return;
            *//*?} else {*/
            if (mc.currentScreen != null) return;
            /*?}*/
            if (!PlacementEngine.canPlace()) return;
            /*? if >=26.1 {*//*
            tryPlaceNextBlock(mc.player, mc.level);
            *//*?} else {*/
            tryPlaceNextBlock(mc.player, mc.world);
            /*?}*/
        }
    }

    // AUTO-BUILD STATE MACHINE

    /*? if >=26.1 {*//*
    private void tickAutoBuild(Minecraft mc) {
    *//*?} else {*/
    private void tickAutoBuild(MinecraftClient mc) {
    /*?}*/
        // safety guards: dead player or wrong dimension
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return;
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player.isDeadOrDying()) {
        *//*?} else {*/
        if (mc.player.isDead()) {
        /*?}*/
            PlacementEngine.reset();
            PathWalker.stop();
            return;
        }
        /*? if >=26.1 {*//*
        if (buildDimension != null && !mc.level.dimension().equals(buildDimension)) {
        *//*?} else {*/
        if (buildDimension != null && !mc.world.getRegistryKey().equals(buildDimension)) {
        /*?}*/
            // Player switched dimensions — pause auto-build silently.
            // It will resume automatically if they return.
            PlacementEngine.reset();
            PathWalker.stop();
            return;
        }

        switch (autoState) {
            case CLEARING_AREA       -> tickClearingArea(mc);
            case WALKING_TO_CLEAR    -> tickWalking(mc, AutoState.CLEARING_AREA);
            case WALKING_TO_DUMP     -> tickWalking(mc, AutoState.DUMPING);
            case DUMPING             -> tickDumping(mc);
            case BUILDING            -> tickBuilding(mc);
            case WALKING_TO_BUILD    -> tickWalking(mc, AutoState.BUILDING);
            case WALKING_BACK        -> tickWalking(mc, AutoState.BUILDING);
            case WALKING_TO_SUPPLY   -> tickWalkingToSupply(mc);
            case RESTOCKING          -> tickRestocking(mc);
            case UNLOADING_SHULKER   -> tickUnloadingShulker(mc);
            case CLEANING_SCAFFOLD   -> tickCleaningScaffold(mc);
            case IDLE                -> tickIdle(mc);
        }
    }

    /*? if >=26.1 {*//*
    private void tickBuilding(Minecraft mc) {
    *//*?} else {*/
    private void tickBuilding(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.screen != null) return;
        *//*?} else {*/
        if (mc.currentScreen != null) return;
        /*?}*/
        if (!PlacementEngine.canPlace()) return;
        if (missingItemMsgCooldown > 0) missingItemMsgCooldown--;

        // entrapment safety
        // If the player has no horizontal exit, stop building and try
        // to navigate to a safe position before continuing.
        /*? if >=26.1 {*//*
        if (isPlayerTrapped(mc.player, mc.level)) {
        *//*?} else {*/
        if (isPlayerTrapped(mc.player, mc.world)) {
        /*?}*/
            if (statusMessages) {
                ChatHelper.info("§c⚠ Blocked in! Finding escape route...");
            }
            PlacementEngine.reset();
            /*? if >=26.1 {*//*
            BlockPos escape = findEscapePosition(mc.player, mc.level);
            *//*?} else {*/
            BlockPos escape = findEscapePosition(mc.player, mc.world);
            /*?}*/
            if (escape != null) {
                PathWalker.walkTo(escape);
                autoState = AutoState.WALKING_TO_BUILD;
            } else {
                // Truly stuck — let Baritone try to path out via mining
                /*? if >=26.1 {*//*
                PathWalker.walkToNearby(mc.player.blockPosition().above(2), 3);
                *//*?} else {*/
                PathWalker.walkToNearby(mc.player.getBlockPos().up(2), 3);
                /*?}*/
                autoState = AutoState.WALKING_TO_BUILD;
            }
            return;
        }

        // Water bailout: if the player is swimming and the build zone
        // is above them, don't try to place — anti-cheat servers reject
        // placements from swimming positions, and the failed attempts
        // reset noProgressTicks so the walk-to-zone logic never triggers.
        // Instead, navigate to dry land or scaffold out first.
        /*? if >=26.1 {*//*
        if (mc.player.isInWater() && !liquidPass) {
        *//*?} else {*/
        if (mc.player.isTouchingWater() && !liquidPass) {
        /*?}*/
            /*? if >=26.1 {*//*
            BlockPos wbZone = findNextBuildZone(mc.player, mc.level);
            *//*?} else {*/
            BlockPos wbZone = findNextBuildZone(mc.player, mc.world);
            /*?}*/
            /*? if >=26.1 {*//*
            if (wbZone == null) wbZone = findHighBuildZone(mc.player, mc.level);
            *//*?} else {*/
            if (wbZone == null) wbZone = findHighBuildZone(mc.player, mc.world);
            /*?}*/
            /*? if >=26.1 {*//*
            if (wbZone != null && wbZone.getY() > mc.player.blockPosition().getY()) {
            *//*?} else {*/
            if (wbZone != null && wbZone.getY() > mc.player.getBlockPos().getY()) {
            /*?}*/
                int wbRadius = (int) Math.ceil(range);
                /*? if >=26.1 {*//*
                BlockPos wbStand = findStandingPosition(wbZone, mc.level, mc.player);
                *//*?} else {*/
                BlockPos wbStand = findStandingPosition(wbZone, mc.world, mc.player);
                /*?}*/
                // Reject standing positions that are also in water
                if (wbStand != null
                        /*? if >=26.1 {*//*
                        && !mc.level.getBlockState(wbStand).getFluidState().isEmpty()) {
                        *//*?} else {*/
                        && !mc.world.getBlockState(wbStand).getFluidState().isEmpty()) {
                        /*?}*/
                    wbStand = null;
                }
                if (wbStand != null) {
                    /*? if >=26.1 {*//*
                    Level w = mc.level;
                    *//*?} else {*/
                    World w = mc.world;
                    /*?}*/
                    if (w != null) {
                        PathWalker.setReservedItems(
                                getNeededItemsNearby(mc.player, w, 200));
                    }
                    PathWalker.walkToWithPlacement(
                            wbStand, wbRadius, mc.player);
                } else {
                    walkToZoneWithPlacement(
                            mc.player, wbZone, wbRadius);
                }
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Player in water — navigating out before building");
                return;
            }
        }

        // Server-rejection bailout: if the anti-cheat has rejected many
        // consecutive placements, the player is in an invalid position
        // (e.g. swimming, falling, wrong angle).  Stop trying to place
        // and navigate to a better position.
        if (PlacementEngine.getConsecutiveFailures() >= SERVER_REJECT_THRESHOLD) {
            PlacementEngine.resetRejectionCounters();
            LOGGER.debug("Server failed to confirm {} placements — repositioning",
                    SERVER_REJECT_THRESHOLD);
            if (!tryWalkToNextZone(mc)) {
                /*? if >=26.1 {*//*
                BlockPos highZone = findHighBuildZone(mc.player, mc.level);
                *//*?} else {*/
                BlockPos highZone = findHighBuildZone(mc.player, mc.world);
                /*?}*/
                if (highZone != null) {
                    int radius = (int) Math.ceil(range);
                    walkToZoneWithPlacement(mc.player, highZone, radius);
                    autoState = AutoState.WALKING_TO_BUILD;
                }
            } else {
                autoState = AutoState.WALKING_TO_BUILD;
            }
            noProgressTicks = 0;
            return;
        }

        // Periodic re-check of skipped items: if the player picked up
        // items that were previously skipped, un-skip them.
        if (!skippedItems.isEmpty()) {
            skippedItemRecheckCooldown--;
            if (skippedItemRecheckCooldown <= 0) {
                skippedItemRecheckCooldown = SKIPPED_RECHECK_INTERVAL;
                Map<Item, Integer> inv = PlacementEngine.getInventoryContentsCached();
                boolean unSkipped = skippedItems.removeIf(
                        item -> inv.getOrDefault(item, 0) > 0);
                if (unSkipped && statusMessages) {
                    ChatHelper.info("§aFound previously-missing items — resuming full build.");
                }
            }
        }

        /*? if >=26.1 {*//*
        boolean started = tryPlaceNextBlock(mc.player, mc.level);
        *//*?} else {*/
        boolean started = tryPlaceNextBlock(mc.player, mc.world);
        /*?}*/
        if (started) {
            // Pipeline started — block will be placed over next ticks
            noProgressTicks = 0;
            failedZones.clear();
            walkFailCount = 0;
            triedPlacementWalk = false;
            stuckCycles = 0;
            // NOTE: do NOT reset restockFailures here — placing a block we
            // already had materials for doesn't mean the supply chests have
            // the items we're still missing.  Resetting here causes an
            // infinite restock loop (place some → restock fail → reset → repeat).
            // restockFailures only resets on *successful* restock (got items).
            lastWalkTargetZone = null; // zone was productive — allow re-targeting
            return;
        }

        // nothing was placed this tick

        // Case 1: blocks exist nearby but we lack the items for ALL of them
        if (!lastMissingItems.isEmpty()) {
            handleMissingItems(mc);
            return;
        }

        // Case 2: no placeable candidates in reach — look for the next zone
        if (walkAttemptCooldown > 0) walkAttemptCooldown--;
        noProgressTicks++;
        if (noProgressTicks < NO_PROGRESS_TIMEOUT) {
            // After ~10 ticks (0.5 s) with no progress, speculatively scan
            // for a nearby zone to walk to.  Don't mark the current area as
            // failed yet — the miss might be transient (player settling after
            // a walk, blocks temporarily out of angle, etc.).
            if (noProgressTicks == 10 && walkAttemptCooldown <= 0) {
                if (!tryWalkToNextZone(mc)) {
                    // No loaded zones — check for unloaded build zones
                    // so we start walking immediately instead of waiting
                    // for the full stuck-cycle cascade.
                    /*? if >=26.1 {*//*
                    BlockPos unloaded = findUnloadedBuildZone(mc.player, mc.level);
                    *//*?} else {*/
                    BlockPos unloaded = findUnloadedBuildZone(mc.player, mc.world);
                    /*?}*/
                    if (unloaded != null) {
                        PathWalker.walkToNearby(unloaded, (int) Math.ceil(range));
                        autoState = AutoState.WALKING_TO_BUILD;
                    }
                }
            }
            return;
        }

        // Timeout reached — exhaustive check before giving up
        noProgressTicks = 0;
        stuckCycles++;

        // If all remaining blocks need skipped (missing) items, don't
        // waste 10 stuck cycles walking around — stop immediately and
        // tell the user which items are needed.
        if (!skippedItems.isEmpty() && stuckCycles >= 2) {
            /*? if >=26.1 {*//*
            int skippedCount = countSkippedBlocks(mc.level);
            *//*?} else {*/
            int skippedCount = countSkippedBlocks(mc.world);
            /*?}*/
            int totalRemaining = countRemaining();
            if (totalRemaining > 0 && skippedCount >= totalRemaining) {
                if (statusMessages) {
                    ChatHelper.info("§eBuild paused — all §f" + totalRemaining
                            + "§e remaining blocks need missing materials:"
                            + "\n§7" + formatMissingItems(skippedItems)
                            + "\n§7Get these items and run §f/printer auto§7 to resume.");
                }
                stuckCycles = 0;
                failedZones.clear();
                autoState = AutoState.IDLE;
                return;
            }
        }

        // If we've been stuck for too many cycles, stop looping and go idle
        if (stuckCycles >= MAX_STUCK_CYCLES) {
            if (statusMessages) {
                ChatHelper.info("§eStuck for " + stuckCycles
                        + " cycles — pausing. Remaining: §f" + countRemaining()
                        + "§e blocks. Check for missing items or unreachable areas.");
            }
            stuckCycles = 0;
            failedZones.clear();
            autoState = AutoState.IDLE;
            return;
        }

        // Before trying to walk to yet another zone, check if the
        // problem is vertical — the nearest unbuilt blocks may be above
        // the player.  Let Baritone handle the vertical movement with
        // allowPlace enabled (pillaring, bridging, parkour).
        // Threshold lowered to pY + 2 so blocks just 3 above the
        // player trigger Baritone scaffolding instead of spinning in
        // the no-progress loop (the previous pY + maxReach threshold
        // left a dead zone where blocks were technically "in range"
        // but couldn't actually be placed from ground level).
        {
            /*? if >=26.1 {*//*
            BlockPos nearbyZone = findNextBuildZone(mc.player, mc.level);
            *//*?} else {*/
            BlockPos nearbyZone = findNextBuildZone(mc.player, mc.world);
            /*?}*/
            if (nearbyZone != null) {
                /*? if >=26.1 {*//*
                int pY = mc.player.blockPosition().getY();
                *//*?} else {*/
                int pY = mc.player.getBlockPos().getY();
                /*?}*/
                /*? if >=26.1 {*//*
                int stuckClimbOffset = mc.player.isInWater() ? 0 : 2;
                *//*?} else {*/
                int stuckClimbOffset = mc.player.isTouchingWater() ? 0 : 2;
                /*?}*/
                if (nearbyZone.getY() > pY + stuckClimbOffset) {
                    int radius = (int) Math.ceil(range);
                    // Prefer standing position on placed structure
                    BlockPos standPos = findStandingPosition(
                            /*? if >=26.1 {*//*
                            nearbyZone, mc.level, mc.player);
                            *//*?} else {*/
                            nearbyZone, mc.world, mc.player);
                            /*?}*/
                    if (standPos != null) {
                        /*? if >=26.1 {*//*
                        Level w = mc.level;
                        *//*?} else {*/
                        World w = mc.world;
                        /*?}*/
                        if (w != null) {
                            PathWalker.setReservedItems(
                                    getNeededItemsNearby(mc.player, w, 200));
                        }
                        PathWalker.walkToWithPlacement(
                                standPos, radius, mc.player);
                    } else {
                        walkToZoneWithPlacement(
                                mc.player, nearbyZone, radius);
                    }
                    autoState = AutoState.WALKING_TO_BUILD;
                    LOGGER.debug("Target above — pathing to {} {} {}",
                            nearbyZone.getX(), nearbyZone.getY(), nearbyZone.getZ());
                    return;
                }
            }
        }

        /*? if >=26.1 {*//*
        failedZones.add(mc.player.blockPosition());
        *//*?} else {*/
        failedZones.add(mc.player.getBlockPos());
        /*?}*/
        if (!tryWalkToNextZone(mc)) {
            // No more reachable zones — clear the failed-zone exclusion
            // and try once more in case support was created elsewhere
            failedZones.clear();
            if (!tryWalkToNextZone(mc)) {
                // Before declaring complete, check for higher zones
                // that need vertical movement (skip during liquid pass)
                if (!liquidPass) {
                    /*? if >=26.1 {*//*
                    BlockPos highZone = findHighBuildZone(mc.player, mc.level);
                    *//*?} else {*/
                    BlockPos highZone = findHighBuildZone(mc.player, mc.world);
                    /*?}*/
                    if (highZone != null) {
                        int radius = (int) Math.ceil(range);
                        // Prefer walking to a standing position on
                        // already-placed structure over blind scaffolding.
                        BlockPos standPos = findStandingPosition(
                                /*? if >=26.1 {*//*
                                highZone, mc.level, mc.player);
                                *//*?} else {*/
                                highZone, mc.world, mc.player);
                                /*?}*/
                        if (standPos != null) {
                            /*? if >=26.1 {*//*
                            Level w = mc.level;
                            *//*?} else {*/
                            World w = mc.world;
                            /*?}*/
                            if (w != null) {
                                PathWalker.setReservedItems(
                                        getNeededItemsNearby(mc.player, w, 200));
                            }
                            PathWalker.walkToWithPlacement(
                                    standPos, radius, mc.player);
                        } else {
                            walkToZoneWithPlacement(
                                    mc.player, highZone, radius);
                        }
                        autoState = AutoState.WALKING_TO_BUILD;
                        LOGGER.debug("Target above — pathing to {} {} {}",
                                highZone.getX(), highZone.getY(),
                                highZone.getZ());
                        return;
                    }
                }
                // Structural done — switch to redstone pass if any remain.
                /*? if >=26.1 {*//*
                if (!redstonePass && !liquidPass && !hasRemainingSolids(mc.level)
                *//*?} else {*/
                if (!redstonePass && !liquidPass && !hasRemainingSolids(mc.world)
                /*?}*/
                        /*? if >=26.1 {*//*
                        && hasRemainingRedstone(mc.level)) {
                        *//*?} else {*/
                        && hasRemainingRedstone(mc.world)) {
                        /*?}*/
                    redstonePass = true;
                    noProgressTicks = 0;
                    stuckCycles = 0;
                    failedZones.clear();
                    if (statusMessages) {
                        ChatHelper.info("§bStructural blocks done — placing redstone components...");
                    }
                    autoState = AutoState.BUILDING;
                    return;
                }
                /*? if >=26.1 {*//*
                if (!liquidPass && !hasRemainingSolids(mc.level) && !hasRemainingRedstone(mc.level)
                *//*?} else {*/
                if (!liquidPass && !hasRemainingSolids(mc.world) && !hasRemainingRedstone(mc.world)
                /*?}*/
                        /*? if >=26.1 {*//*
                        && hasRemainingLiquids(mc.level)) {
                        *//*?} else {*/
                        && hasRemainingLiquids(mc.world)) {
                        /*?}*/
                    liquidPass = true;
                    redstonePass = false;
                    noProgressTicks = 0;
                    stuckCycles = 0;
                    failedZones.clear();
                    PathWalker.stop(); // stop Baritone — it can't path through liquids
                    if (statusMessages) {
                        ChatHelper.info("§bRedstone done — placing liquids...");
                    }
                    autoState = AutoState.BUILDING;
                    return;
                }

                // Chunk-loading awareness
                // All loaded chunks are done, but there may be unbuilt
                // blocks in unloaded parts of the schematic.  Walk
                // toward the nearest unloaded region so it loads.
                /*? if >=26.1 {*//*
                BlockPos unloadedZone = findUnloadedBuildZone(mc.player, mc.level);
                *//*?} else {*/
                BlockPos unloadedZone = findUnloadedBuildZone(mc.player, mc.world);
                /*?}*/
                if (unloadedZone != null) {
                    LOGGER.debug("Walking to unloaded region {} {} {}",
                            unloadedZone.getX(), unloadedZone.getY(), unloadedZone.getZ());
                    PathWalker.walkToNearby(unloadedZone, (int) Math.ceil(range));
                    autoState = AutoState.WALKING_TO_BUILD;
                    return;
                }

                // Build appears complete — check for scaffold to clean up.
                liquidPass = false; // reset for next build
                redstonePass = false;
                MoarMod.getChestManager().clearSnapshots();
                if (PrinterDatabase.hasScaffold()) {
                    autoState = AutoState.CLEANING_SCAFFOLD;
                    if (statusMessages) {
                        ChatHelper.info("§aBuild done! §7Cleaning up §e"
                                + PrinterDatabase.scaffoldCount()
                                + "§7 scaffold blocks...");
                    }
                } else {
                    autoState = AutoState.IDLE;
                    if (statusMessages) {
                        if (skippedItems.isEmpty()) {
                            ChatHelper.info("§aBuild appears complete! §e"
                                    + blocksPlaced + "§a blocks placed.");
                        } else {
                            ChatHelper.info("§aBuild finished with available materials! §e"
                                    + blocksPlaced + "§a blocks placed."
                                    /*? if >=26.1 {*//*
                                    + "\n§cMissing materials (§f" + countSkippedBlocks(mc.level)
                                    *//*?} else {*/
                                    + "\n§cMissing materials (§f" + countSkippedBlocks(mc.world)
                                    /*?}*/
                                    + "§c blocks not placed):"
                                    + "\n§7" + formatMissingItems(skippedItems)
                                    + "\n§7Get these items and run §f/printer auto§7 to resume.");
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles the situation where blocks need placing but none of the
     * required items are in the player's inventory.
     */
    /*? if >=26.1 {*//*
    private void handleMissingItems(Minecraft mc) {
    *//*?} else {*/
    private void handleMissingItems(MinecraftClient mc) {
    /*?}*/
        // If supply chests exist and we haven't exhausted restock attempts,
        // go restock.
        if (MoarMod.getChestManager().supplyChestCount() > 0 && restockFailures < MAX_RESTOCK_FAILURES) {
            if (statusMessages && missingItemMsgCooldown <= 0) {
                ChatHelper.info("§eMissing items — going to restock. Need: "
                        + formatMissingItems(lastMissingItems));
                missingItemMsgCooldown = MISSING_MSG_COOLDOWN;
            }
            noProgressTicks = 0;
            /*? if >=26.1 {*//*
            startRestockRun(mc.player, mc.level);
            *//*?} else {*/
            startRestockRun(mc.player, mc.world);
            /*?}*/
            return;
        }

        // Can't restock (no chests or all attempts exhausted) — skip
        // the missing items and keep building with whatever we have.
        skippedItems.addAll(lastMissingItems);
        if (statusMessages && missingItemMsgCooldown <= 0) {
            ChatHelper.info("§eSkipping unavailable items, building with what we have."
                    + "\n§7Skipped: " + formatMissingItems(skippedItems));
            missingItemMsgCooldown = MISSING_MSG_COOLDOWN;
        }
        lastMissingItems.clear();
        noProgressTicks = 0;
        // Stay in BUILDING — tryPlaceNextBlock will skip these items
    }

    /** Finds the next build zone and starts walking to it. */
    /*? if >=26.1 {*//*
    private boolean tryWalkToNextZone(Minecraft mc) {
    *//*?} else {*/
    private boolean tryWalkToNextZone(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos nextZone = findNextBuildZone(mc.player, mc.level);
        *//*?} else {*/
        BlockPos nextZone = findNextBuildZone(mc.player, mc.world);
        /*?}*/
        if (nextZone == null) {
            // During liquid pass, don't try elevated zones — liquids don't
            // need adjacent support and vertical pathing can conflict.
            if (liquidPass) return false;

            // No reachable zones with adjacent support — check for elevated
            // zones and let Baritone handle the vertical movement.
            /*? if >=26.1 {*//*
            BlockPos highZone = findHighBuildZone(mc.player, mc.level);
            *//*?} else {*/
            BlockPos highZone = findHighBuildZone(mc.player, mc.world);
            /*?}*/
            if (highZone != null) {
                int radius = (int) Math.ceil(range);
                // Prefer walking to a standing position on already-placed
                // structure over blind scaffolding.
                BlockPos highStand = findStandingPosition(
                        /*? if >=26.1 {*//*
                        highZone, mc.level, mc.player);
                        *//*?} else {*/
                        highZone, mc.world, mc.player);
                        /*?}*/
                if (highStand != null) {
                    /*? if >=26.1 {*//*
                    Level w = mc.level;
                    *//*?} else {*/
                    World w = mc.world;
                    /*?}*/
                    if (w != null) {
                        PathWalker.setReservedItems(
                                getNeededItemsNearby(mc.player, w, 200));
                    }
                    PathWalker.walkToWithPlacement(
                            highStand, radius, mc.player);
                } else {
                    walkToZoneWithPlacement(mc.player, highZone, radius);
                }
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Target above — pathing to {} {} {}",
                        highZone.getX(), highZone.getY(), highZone.getZ());
                return true;
            }
            return false;
        }

        // If this is the exact same zone we just tried, mark it failed
        // and search again to avoid an infinite loop
        if (lastWalkTargetZone != null && nextZone.equals(lastWalkTargetZone)) {
            failedZones.add(nextZone);
            /*? if >=26.1 {*//*
            nextZone = findNextBuildZone(mc.player, mc.level);
            *//*?} else {*/
            nextZone = findNextBuildZone(mc.player, mc.world);
            /*?}*/
            if (nextZone == null) return false;
        }

        /*? if >=26.1 {*//*
        lastBuildPos = mc.player.blockPosition();
        *//*?} else {*/
        lastBuildPos = mc.player.getBlockPos();
        /*?}*/
        /*? if >=26.1 {*//*
        lastWalkTargetZone = nextZone.immutable();
        *//*?} else {*/
        lastWalkTargetZone = nextZone.toImmutable();
        /*?}*/

        noProgressTicks = 0;
        walkAttemptCooldown = NO_PROGRESS_TIMEOUT; // don't re-scan immediately if this fails

        // Check vertical reachability from the best standing position
        /*? if >=26.1 {*//*
        int playerY = mc.player.blockPosition().getY();
        *//*?} else {*/
        int playerY = mc.player.getBlockPos().getY();
        /*?}*/
        int targetY = nextZone.getY();
        int maxReach = (int) Math.ceil(range);
        /*? if >=26.1 {*//*
        BlockPos standPos = findStandingPosition(nextZone, mc.level, mc.player);
        *//*?} else {*/
        BlockPos standPos = findStandingPosition(nextZone, mc.world, mc.player);
        /*?}*/
        int effectiveStandY = standPos != null ? standPos.getY() : playerY;

        // Determine if the target is vertically unreachable from ground.
        // When the player is in water, any block above them requires
        // climbing — swimming positions can't reliably place blocks and
        // anti-cheat servers reject placements from water.
        /*? if >=26.1 {*//*
        boolean playerInWater = mc.player.isInWater();
        *//*?} else {*/
        boolean playerInWater = mc.player.isTouchingWater();
        /*?}*/
        int climbOffset = playerInWater ? 0 : 2;
        boolean needsClimbing = false;
        // 1. Target is too far above any reachable standing position
        if (targetY > effectiveStandY + maxReach) {
            needsClimbing = true;
        }
        // 2. The standing position itself is above the player
        else if (standPos != null && standPos.getY() > playerY + climbOffset) {
            needsClimbing = true;
        }
        // 3. No standing position exists and target is above the player
        else if (standPos == null && targetY > playerY + climbOffset) {
            needsClimbing = true;
        }
        // 4. Target is above the player and has no adjacent solid block.
        //    Even if a standing position exists at ground level, the
        //    block can't be placed without an adjacent face to click on.
        //    Baritone's scaffold placement (pillar-up) will provide the
        //    support block automatically.
        else if (targetY > playerY + climbOffset
                /*? if >=26.1 {*//*
                && !PlacementEngine.hasAdjacentSolid(mc.level, nextZone)) {
                *//*?} else {*/
                && !PlacementEngine.hasAdjacentSolid(mc.world, nextZone)) {
                /*?}*/
            needsClimbing = true;
        }

        if (needsClimbing) {
            if (standPos != null) {
                // A valid standing position exists on already-placed structure
                // (e.g. a floor, platform, or staircase).  Walk to that position
                // directly and let Baritone plan a 3D path that uses the existing
                // terrain.  This avoids the horizontal-then-vertical waypoint
                // approach which forces Baritone to pillar instead of using
                // already-built staircases, ramps, or platforms.
                /*? if >=26.1 {*//*
                Level w = mc.level;
                *//*?} else {*/
                World w = mc.world;
                /*?}*/
                if (w != null) {
                    PathWalker.setReservedItems(getNeededItemsNearby(mc.player, w, 200));
                }
                PathWalker.walkToWithPlacement(standPos, maxReach, mc.player);
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Target above — walking to standing position {} {} {} (on placed structure)",
                        standPos.getX(), standPos.getY(), standPos.getZ());
            } else {
                // No standing position found — let Baritone scaffold its own
                // path to the build zone via waypoints.
                walkToZoneWithPlacement(mc.player, nextZone, maxReach);
                autoState = AutoState.WALKING_TO_BUILD;
                LOGGER.debug("Target above — scaffolding to {} {} {}",
                        nextZone.getX(), nextZone.getY(), nextZone.getZ());
            }
            return true;
        }

        // Target is too far below — navigate down.
        if (targetY < effectiveStandY - maxReach) {
            PathWalker.walkToNearby(nextZone, maxReach);
            autoState = AutoState.WALKING_TO_BUILD;
            LOGGER.debug("Target below — navigating to {} {} {}",
                    nextZone.getX(), nextZone.getY(), nextZone.getZ());
            return true;
        }

        if (liquidPass) {
            // During liquid pass, always use walkToNearby with extra radius.
            // Placed water/lava creates flowing currents that push the player
            // off-path; a wider radius lets Baritone route around them.
            int radius = (int) Math.ceil(range) + 2;
            PathWalker.walkToNearby(nextZone, radius);
        } else if (standPos != null && walkFailCount == 0) {
            // If the player is already at (or within 1.5 blocks of) the
            // standing position, skip walking — the build tick can place
            // from here.  Avoids sending Baritone a 0–1 block goal that
            // it can't compute near fences / iron bars / glass edges.
            /*? if >=26.1 {*//*
            double standDist = mc.player.position()
                    .distanceToSqr(Vec3.atCenterOf(standPos));
            *//*?} else if >=1.21.10 {*//*
            double standDist = mc.player.getSyncedPos()
                    .squaredDistanceTo(Vec3d.ofCenter(standPos));
            *//*?} else {*/
            double standDist = mc.player.getPos()
                    .squaredDistanceTo(Vec3d.ofCenter(standPos));
            /*?}*/
            if (standDist <= 2.25) { // 1.5 blocks
                // Already at the standing spot — just build
                autoState = AutoState.BUILDING;
                return true;
            }
            PathWalker.walkTo(standPos);
        } else {
            // No standing position or repeated failures — walk near the target
            // zone directly.  With air-placement enabled, the player doesn't
            // need solid ground directly adjacent to the target.
            int radius = (int) Math.ceil(range) + walkFailCount;
            PathWalker.walkToNearby(nextZone, radius);
        }
        autoState = AutoState.WALKING_TO_BUILD;
        LOGGER.debug("Walking to build zone {} {} {}",
                nextZone.getX(), nextZone.getY(), nextZone.getZ());
        return true;
    }

    /*? if >=26.1 {*//*
    private void tickWalking(Minecraft mc, AutoState arrivalState) {
    *//*?} else {*/
    private void tickWalking(MinecraftClient mc, AutoState arrivalState) {
    /*?}*/
        if (!PathWalker.isActive()) {
            // multi-phase descent continuation
            // walkToZoneWithPlacement may have set up a descent for
            // WALKING_BACK or WALKING_TO_BUILD.  Handle phase transitions
            // the same way tickWalkingToSupply does.
            if (supplyDescentPhase == 1 && supplyDescentTarget != null) {
                supplyDescentPhase = 2;
                LOGGER.debug("Descending to Y={} (walking)", supplyDescentTarget.getY());
                PathWalker.walkToYLevelWithPlacement(
                        supplyDescentTarget.getY(), mc.player);
                return;
            }
            if (supplyDescentPhase == 2 && supplyDescentTarget != null) {
                // Check if GoalYLevel actually brought us close to target Y.
                // If not (still far above), Baritone couldn't find a path
                // down — fall back to mining descent (break pillar blocks
                // under player's feet, 1 block at a time).
                /*? if >=26.1 {*//*
                int playerY = mc.player.blockPosition().getY();
                *//*?} else {*/
                int playerY = mc.player.getBlockPos().getY();
                /*?}*/
                int targetY = supplyDescentTarget.getY();
                if (playerY - targetY > 5) {
                    LOGGER.debug("GoalYLevel failed — mining down from Y={} to Y={}",
                            playerY, targetY);
                    supplyDescentPhase = 2; // stay in phase 2 for re-check
                    PathWalker.startMiningDescent(targetY);
                    return;
                }
                supplyDescentPhase = 3;
                LOGGER.debug("Approaching target...");
                /*? if >=26.1 {*//*
                double dist = Math.sqrt(mc.player.blockPosition()
                *//*?} else {*/
                double dist = Math.sqrt(mc.player.getBlockPos()
                /*?}*/
                        /*? if >=26.1 {*//*
                        .distSqr(supplyDescentTarget));
                        *//*?} else {*/
                        .getSquaredDistance(supplyDescentTarget));
                        /*?}*/
                if (dist > 48) {
                    List<BlockPos> horizLegs = computeLinearWaypoints(
                            /*? if >=26.1 {*//*
                            mc.player.blockPosition(), supplyDescentTarget, 48);
                            *//*?} else {*/
                            mc.player.getBlockPos(), supplyDescentTarget, 48);
                            /*?}*/
                    PathWalker.walkToViaWaypointsWithPlacement(
                            horizLegs, (int) Math.ceil(range), mc.player);
                } else {
                    PathWalker.walkToWithPlacement(
                            supplyDescentTarget, (int) Math.ceil(range), mc.player);
                }
                return;
            }
            // Clear descent state if we were in phase 3 or not descending
            if (supplyDescentPhase > 0) {
                supplyDescentPhase = 0;
                supplyDescentTarget = null;
            }

            if (PathWalker.hasArrived()) {
                walkFailCount = 0;
                triedPlacementWalk = false;
                // Keep lastWalkTargetZone so tryWalkToNextZone can detect
                // if we're re-picking the same zone that produced no placement.
                // It will be cleared when a block IS successfully placed.
                // Player moved to a new position — chests that were
                // unreachable from the old position may be reachable
                // from here (e.g. after climbing to the build zone,
                // the ground-level chest is now a simple descent).
                unreachableChests.clear();
                shulkerNoSpaceSkipped = false;
                LOGGER.debug("Arrived at target");
                autoState = arrivalState;
                noProgressTicks = 0;
            } else {
                walkFailCount++;
                BlockPos walkTarget = PathWalker.getTarget();
                boolean pathImpossible = PathWalker.isStuck();

                // Retry with wider radius.  For clearing, retry even
                // when stuck — the illegal blocks ARE the obstacles and
                // a wider GoalNear gives Baritone room to path around
                // the cluster into open space.
                boolean clearingRetry = arrivalState == AutoState.CLEARING_AREA;
                if ((clearingRetry || !pathImpossible)
                        && walkFailCount < MAX_WALK_RETRIES) {
                    // Retry near the BUILD ZONE (not the standing position
                    // that may be across a gap) with increasing radius.
                    BlockPos retryTarget = lastWalkTargetZone != null
                            ? lastWalkTargetZone : walkTarget;
                    if (retryTarget != null) {
                        int extra = clearingRetry ? 3 * walkFailCount : walkFailCount;
                        int radius = (int) Math.ceil(range) + extra;
                        PathWalker.walkToNearby(retryTarget, radius);
                        LOGGER.debug("Path blocked, trying wider approach (r={})", radius);
                        return; // stay in walking state
                    }
                }
                walkFailCount = 0;

                // close-enough bail-out
                // All walk retries exhausted.  If the player is already
                // close enough to the target, fall back to building
                // from here rather than escalating further.
                if (arrivalState == AutoState.BUILDING
                        || arrivalState == AutoState.CLEARING_AREA) {
                    boolean inRange = false;
                    if (lastWalkTargetZone != null) {
                        /*? if >=26.1 {*//*
                        double d = mc.player.getEyePosition()
                        *//*?} else {*/
                        double d = mc.player.getEyePos()
                        /*?}*/
                                /*? if >=26.1 {*//*
                                .distanceToSqr(Vec3.atCenterOf(lastWalkTargetZone));
                                *//*?} else {*/
                                .squaredDistanceTo(Vec3d.ofCenter(lastWalkTargetZone));
                                /*?}*/
                        if (d <= range * range) inRange = true;
                    }
                    if (!inRange && walkTarget != null) {
                        /*? if >=26.1 {*//*
                        double d = mc.player.position()
                                .distanceToSqr(Vec3.atCenterOf(walkTarget));
                        *//*?} else if >=1.21.10 {*//*
                        double d = mc.player.getSyncedPos()
                                .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                        *//*?} else {*/
                        double d = mc.player.getPos()
                                .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                        /*?}*/
                        if (d <= (range + 1) * (range + 1)) inRange = true;
                    }
                    if (inRange) {
                        triedPlacementWalk = false;
                        if (lastWalkTargetZone != null) failedZones.add(lastWalkTargetZone);
                        if (walkTarget != null) failedZones.add(walkTarget);
                        autoState = arrivalState;
                        return;
                    }
                }

                // For clearing, try vanilla straight-line walk when
                // Baritone can't pathfind — the target is usually on
                // the same Y level and a direct WASD walk can reach it.
                if (!triedPlacementWalk
                        && arrivalState == AutoState.CLEARING_AREA
                        && lastWalkTargetZone != null) {
                    triedPlacementWalk = true;
                    LOGGER.debug("Can't path to clear target — trying vanilla walk");
                    PathWalker.walkToVanilla(lastWalkTargetZone);
                    return;
                }

                // If the target was above (or far from) us and we
                // haven't already tried placement, escalate to
                // waypoint-based placement walk.
                /*? if >=26.1 {*//*
                int playerY = mc.player.blockPosition().getY();
                *//*?} else {*/
                int playerY = mc.player.getBlockPos().getY();
                /*?}*/
                if (!triedPlacementWalk
                        && lastWalkTargetZone != null
                        && (lastWalkTargetZone.getY() > playerY
                            || lastWalkTargetZone.getY() < playerY - 4)) {
                    triedPlacementWalk = true;
                    LOGGER.debug("Can't walk to zone — retrying with placement + waypoints");
                    int radius = (int) Math.ceil(range);
                    walkToZoneWithPlacement(mc.player, lastWalkTargetZone, radius);
                    return;
                }

                triedPlacementWalk = false;
                if (statusMessages) {
                    ChatHelper.info("§eWalking timed out, building from here.");
                }
                // Mark BOTH the walk target and the build target zone as failed
                if (walkTarget != null) failedZones.add(walkTarget);
                if (lastWalkTargetZone != null) failedZones.add(lastWalkTargetZone);
                if (arrivalState == AutoState.CLEARING_AREA && lastWalkTargetZone != null) {
                    failedClearTargets.add(lastWalkTargetZone);
                    consecutiveClearFailures++;
                }
                autoState = arrivalState;
                noProgressTicks = 0;
            }
            return;
        }
        // Opportunistic build check
        // If Baritone has been working for 30+ ticks (1.5 s) and the
        // player is already within reach of the target, Baritone is
        // probably stalled on a short path it can't compute.  Cancel
        // and let the build-tick try placement from the current spot.
        // We give Baritone 30 ticks first so it has a real chance to
        // reposition the player to a better angle.
        if ((arrivalState == AutoState.BUILDING
                || arrivalState == AutoState.CLEARING_AREA)
                && PathWalker.getTicksWalking() >= 30) {
            boolean closeEnough = false;

            // Check 1: player eye is within placement reach of the build zone
            if (lastWalkTargetZone != null) {
                /*? if >=26.1 {*//*
                double distSq = mc.player.getEyePosition()
                *//*?} else {*/
                double distSq = mc.player.getEyePos()
                /*?}*/
                        /*? if >=26.1 {*//*
                        .distanceToSqr(Vec3.atCenterOf(lastWalkTargetZone));
                        *//*?} else {*/
                        .squaredDistanceTo(Vec3d.ofCenter(lastWalkTargetZone));
                        /*?}*/
                if (distSq <= range * range) {
                    closeEnough = true;
                }
            }

            // Check 2: player is within a few blocks of the walk target
            if (!closeEnough) {
                BlockPos walkTarget = PathWalker.getTarget();
                if (walkTarget != null) {
                    /*? if >=26.1 {*//*
                    double walkDistSq = mc.player.position()
                            .distanceToSqr(Vec3.atCenterOf(walkTarget));
                    *//*?} else if >=1.21.10 {*//*
                    double walkDistSq = mc.player.getSyncedPos()
                            .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                    *//*?} else {*/
                    double walkDistSq = mc.player.getPos()
                            .squaredDistanceTo(Vec3d.ofCenter(walkTarget));
                    /*?}*/
                    if (walkDistSq <= (range + 1) * (range + 1)) {
                        closeEnough = true;
                    }
                }
            }

            if (closeEnough) {
                PathWalker.stop();
                walkFailCount = 0;
                triedPlacementWalk = false;
                if (lastWalkTargetZone != null) failedZones.add(lastWalkTargetZone);
                autoState = arrivalState;
                return;
            }
        }

        // While Baritone is walking with placement enabled, periodically
        // scan for scaffold blocks it may have placed.
        if (PathWalker.isPlacementEnabled()) {
            scaffoldScanCooldown--;
            if (scaffoldScanCooldown <= 0) {
                scaffoldScanCooldown = SCAFFOLD_SCAN_INTERVAL;
                /*? if >=26.1 {*//*
                scanForScaffoldBlocks(mc.player, mc.level);
                *//*?} else {*/
                scanForScaffoldBlocks(mc.player, mc.world);
                /*?}*/
            }
        }
        PathWalker.tick();
    }

    /**
     * Records nearby blocks Baritone placed as scaffold (matches throwaway
     * items + schematic expects air). Called during placement walks.
     */
    /*? if >=26.1 {*//*
    private void scanForScaffoldBlocks(LocalPlayer player, Level world) {
    *//*?} else {*/
    private void scanForScaffoldBlocks(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null || anchor == null || schematic == null) return;
        Set<String> throwaways = PathWalker.getThrowawayItemIds();
        if (throwaways.isEmpty()) return;

        /*? if >=26.1 {*//*
        BlockPos center = player.blockPosition();
        *//*?} else {*/
        BlockPos center = player.getBlockPos();
        /*?}*/
        int radius = 5; // scan nearby area
        for (int dy = -2; dy <= 3; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = center.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = center.add(dx, dy, dz);
                    /*?}*/
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;

                    // Check if this block's item is in Baritone's throwaway list
                    Item blockItem = state.getBlock().asItem();
                    if (blockItem == Items.AIR) continue;
                    /*? if >=26.1 {*//*
                    String itemId = BuiltInRegistries.ITEM.getKey(blockItem).toString();
                    *//*?} else {*/
                    String itemId = Registries.ITEM.getId(blockItem).toString();
                    /*?}*/
                    if (!throwaways.contains(itemId)) continue;

                    // Check if the schematic expects something else here
                    int sx = pos.getX() - anchor.getX();
                    int sy = pos.getY() - anchor.getY();
                    int sz = pos.getZ() - anchor.getZ();
                    if (schematic.contains(sx, sy, sz)) {
                        BlockState expected = schematic.getBlockState(sx, sy, sz);
                        if (!expected.isAir()) continue; // schematic wants a real block here
                    }

                    // This is a scaffold block — track it with its item ID
                    if (!PrinterDatabase.isScaffold(pos)) {
                        PrinterDatabase.addScaffold(pos, itemId);
                    }
                }
            }
        }
    }

    // Area clearing
    // Mirrors the building scan: findNextClearTarget is the inverse of
    // findNextBuildZone, and tryClearNextBlock is the inverse of
    // tryPlaceNextBlock.  Building fills air with blocks; clearing
    // fills blocks with air.

    /**
     * Scan blocks within reach and break the nearest illegal one.
     * Mirrors tryPlaceNextBlock() but breaks instead of placing.
     */
    /*? if >=26.1 {*//*
    private boolean tryClearNextBlock(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean tryClearNextBlock(ClientPlayerEntity player, World world) {
    /*?}*/
        double rangeSq = range * range;
        int maxReach = (int) Math.ceil(range);
        Set<BlockPos> protectedStorage = getProtectedStoragePositions();

        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        /*?}*/

        /*? if >=26.1 {*//*
        Vec3 eye = player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = player.getEyePos();
        /*?}*/

        // Top-down demolition: prefer highest Y to avoid gravity drops
        int px = playerPos.getX(), py = playerPos.getY(), pz = playerPos.getZ();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        /*? if >=26.1 {*//*
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        *//*?} else {*/
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        /*?}*/

        for (int dy = maxReach; dy >= -maxReach; dy--) {
            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    int wx = px + dx, wy = py + dy, wz = pz + dz;

                    /*? if >=26.1 {*//*
                    double dist = eye.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                    *//*?} else {*/
                    double dist = eye.squaredDistanceTo(wx + 0.5, wy + 0.5, wz + 0.5);
                    /*?}*/
                    if (dist > rangeSq) continue;

                    int sx = wx - anchor.getX();
                    int sy = wy - anchor.getY();
                    int sz = wz - anchor.getZ();
                    if (!schematic.contains(sx, sy, sz)) continue;

                    mutablePos.set(wx, wy, wz);
                    BlockState target = schematic.getBlockState(sx, sy, sz);
                    BlockState existing = world.getBlockState(mutablePos);

                    if (isEffectivelyPlaced(existing, target)) continue;
                    if (isStorageBlacklistedForClearing(existing, mutablePos, protectedStorage)) continue;
                    if (existing.isAir()) continue;
                    /*? if >=26.1 {*//*
                    if (existing.canBeReplaced()) continue;
                    *//*?} else {*/
                    if (existing.isReplaceable()) continue;
                    /*?}*/
                    if (failedClearTargets.contains(mutablePos)) continue;

                    // Prefer highest-Y, then nearest to eye
                    if (best != null) {
                        if (wy < best.getY()) continue;
                        if (wy == best.getY() && dist >= bestDist) continue;
                    }
                    bestDist = dist;
                    /*? if >=26.1 {*//*
                    best = mutablePos.immutable();
                    *//*?} else {*/
                    best = mutablePos.toImmutable();
                    /*?}*/
                }
            }
        }

        if (best == null) return false;

        // Start breaking
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        clearBreakTarget = best.immutable();
        *//*?} else {*/
        clearBreakTarget = best.toImmutable();
        /*?}*/
        clearBreakTicks = 0;
        /*? if >=26.1 {*//*
        clearSavedYaw = mc.player.getYRot();
        clearSavedPitch = mc.player.getXRot();
        *//*?} else {*/
        clearSavedYaw = mc.player.getYaw();
        clearSavedPitch = mc.player.getPitch();
        /*?}*/

        BlockState clearState = world.getBlockState(clearBreakTarget);
        PlacementEngine.selectBestTool(mc.player, mc, clearState);

        // Look at block center and start breaking
        /*? if >=26.1 {*//*
        Vec3 blockCenter = Vec3.atCenterOf(clearBreakTarget);
        Vec3 toBlock = blockCenter.subtract(eye);
        *//*?} else {*/
        Vec3d blockCenter = Vec3d.ofCenter(clearBreakTarget);
        Vec3d toBlock = blockCenter.subtract(eye);
        /*?}*/
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        /*?}*/
        PlacementEngine.setLookRotation(mc.player, breakYaw,
                /*? if >=26.1 {*//*
                Mth.clamp(breakPitch, -90.0f, 90.0f));
                *//*?} else {*/
                MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                /*?}*/

        /*? if >=26.1 {*//*
        mc.gameMode.startDestroyBlock(clearBreakTarget, Direction.UP);
        mc.player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        mc.interactionManager.attackBlock(clearBreakTarget, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
        /*?}*/

        LOGGER.debug("Clearing illegal block at {} {} {}",
                clearBreakTarget.getX(), clearBreakTarget.getY(),
                clearBreakTarget.getZ());
        return true;
    }

    /**
     * Find the nearest illegal block in the schematic — mirrors
     * findNextBuildZone().  Used as a walk target when nothing is
     * within reach.  Prefers highest Y (top-down clearing), then
     * nearest to player.
     */
    /*? if >=26.1 {*//*
    private BlockPos findNextClearTarget(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findNextClearTarget(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null || schematic == null || anchor == null) return null;
        Set<BlockPos> protectedStorage = getProtectedStoragePositions();

        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        /*? if >=26.1 {*//*
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        *//*?} else {*/
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        /*?}*/

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = region.absY - 1; y >= 0; y--) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/

                        mutablePos.set(wx, wy, wz);
                        BlockState existing = world.getBlockState(mutablePos);

                        if (isEffectivelyPlaced(existing, target)) continue;
                        if (isStorageBlacklistedForClearing(existing, mutablePos, protectedStorage)) continue;
                        if (existing.isAir()) continue;
                        /*? if >=26.1 {*//*
                        if (existing.canBeReplaced()) continue;
                        *//*?} else {*/
                        if (existing.isReplaceable()) continue;
                        /*?}*/
                        if (failedClearTargets.contains(mutablePos)) continue;

                        // Prefer highest Y, then nearest
                        /*? if >=26.1 {*//*
                        double dist = playerPos.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                        *//*?} else {*/
                        double dist = playerPos.squaredDistanceTo(wx + 0.5, wy + 0.5, wz + 0.5);
                        /*?}*/
                        if (best != null) {
                            if (wy < best.getY()) continue;
                            if (wy == best.getY() && dist >= bestDist) continue;
                        }
                        bestDist = dist;
                        /*? if >=26.1 {*//*
                        best = mutablePos.immutable();
                        *//*?} else {*/
                        best = mutablePos.toImmutable();
                        /*?}*/
                    }
                }
            }
        }
        return best;
    }

    /**
     * Clears illegal blocks from the schematic footprint.
     * Mirrors the building tick: try breaking in-reach blocks first
     * (tryClearNextBlock), then walk to the nearest remaining illegal
     * block if nothing is nearby.  Building fills air → blocks;
     * clearing fills blocks → air.
     */
    /*? if >=26.1 {*//*
    private void tickClearingArea(Minecraft mc) {
    *//*?} else {*/
    private void tickClearingArea(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        // ── If PlacementEngine is actively breaking / placing, wait ──
        if (PlacementEngine.isBusy()) return;

        // Continue breaking current target
        if (clearBreakTarget != null) {
            BlockState current = world.getBlockState(clearBreakTarget);
            if (isStorageBlacklistedForClearing(current, clearBreakTarget, getProtectedStoragePositions())) {
                // Storage/container blacklist always wins: cancel this target.
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                clearBreakTarget = null;
                clearBreakTicks = 0;
                return;
            }
            /*? if >=26.1 {*//*
            if (current.isAir() || current.canBeReplaced()) {
            *//*?} else {*/
            if (current.isAir() || current.isReplaceable()) {
            /*?}*/
                // Block broken
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setYRot(clearSavedYaw);
                *//*?} else {*/
                mc.player.setYaw(clearSavedYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(clearSavedPitch);
                *//*?} else {*/
                mc.player.setPitch(clearSavedPitch);
                /*?}*/
                clearBlocksBroken++;
                consecutiveClearFailures = 0;
                clearStallResets = 0;
                clearBreakTarget = null;
                clearBreakTicks = 0;
                clearCooldownTicks = BREAK_COOLDOWN_TICKS;
                return;
            }

            clearBreakTicks++;
            if (clearBreakTicks > MAX_CLEAR_BREAK_TICKS) {
                // Timed out — skip this block
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setYRot(clearSavedYaw);
                *//*?} else {*/
                mc.player.setYaw(clearSavedYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(clearSavedPitch);
                *//*?} else {*/
                mc.player.setPitch(clearSavedPitch);
                /*?}*/
                LOGGER.debug("Clear break timed out at {} {} {}",
                        clearBreakTarget.getX(), clearBreakTarget.getY(),
                        clearBreakTarget.getZ());
                failedClearTargets.add(clearBreakTarget);
                consecutiveClearFailures++;
                clearBreakTarget = null;
                clearBreakTicks = 0;
                clearCooldownTicks = BREAK_COOLDOWN_TICKS;
                return;
            }

            // Continue breaking — maintain look direction
            /*? if >=26.1 {*//*
            Vec3 eyePos = mc.player.getEyePosition();
            *//*?} else {*/
            Vec3d eyePos = mc.player.getEyePos();
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 blockCenter = Vec3.atCenterOf(clearBreakTarget);
            *//*?} else {*/
            Vec3d blockCenter = Vec3d.ofCenter(clearBreakTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toBlock = blockCenter.subtract(eyePos);
            *//*?} else {*/
            Vec3d toBlock = blockCenter.subtract(eyePos);
            /*?}*/
            double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
            /*? if >=26.1 {*//*
            float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
            *//*?} else {*/
            float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
            /*?}*/
            /*? if >=26.1 {*//*
            float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
            *//*?} else {*/
            float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
            /*?}*/
            PlacementEngine.setLookRotation(mc.player, breakYaw,
                    /*? if >=26.1 {*//*
                    Mth.clamp(breakPitch, -90.0f, 90.0f));
                    *//*?} else {*/
                    MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                    /*?}*/

            /*? if >=26.1 {*//*
            mc.gameMode.continueDestroyBlock(clearBreakTarget, Direction.UP);
            *//*?} else {*/
            mc.interactionManager.updateBlockBreakingProgress(clearBreakTarget, Direction.UP);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.player.swing(InteractionHand.MAIN_HAND);
            *//*?} else {*/
            mc.player.swingHand(Hand.MAIN_HAND);
            /*?}*/
            return;
        }

        // Inter-block cooldown to avoid FastBreak detection
        if (clearCooldownTicks > 0) {
            clearCooldownTicks--;
            return;
        }

        // Dump inventory if nearly full
        if (MoarMod.getChestManager().dumpChestCount() > 0
                && MoarMod.getChestManager().isInventoryFull(mc)) {
            startDumpRun(mc);
            return;
        }

        // ── Try breaking an in-range block (mirrors tryPlaceNextBlock) ──
        if (tryClearNextBlock(mc.player, world)) {
            return;
        }

        // Nothing in reach — stall detection
        if (consecutiveClearFailures >= MAX_CONSECUTIVE_CLEAR_FAILURES) {
            clearStallResets++;
            if (clearStallResets > MAX_CLEAR_STALL_RESETS) {
                if (statusMessages) {
                    ChatHelper.info("§eCan't reach remaining illegal blocks — building from here.");
                }
                LOGGER.warn("Clearing gave up after {} stall resets ({} blocks broken)",
                        clearStallResets - 1, clearBlocksBroken);
                clearingDone = true;
                autoState = AutoState.BUILDING;
                noProgressTicks = 0;
                return;
            }
            // Recovery: reset failed targets and retry
            LOGGER.info("Clearing stall recovery {}/{} — resetting {} failed targets",
                    clearStallResets, MAX_CLEAR_STALL_RESETS,
                    failedClearTargets.size());
            if (statusMessages) {
                ChatHelper.info("§eClearing stalled — retrying unreachable blocks ("
                        + clearStallResets + "/" + MAX_CLEAR_STALL_RESETS + ")");
            }
            failedClearTargets.clear();
            consecutiveClearFailures = 0;
            // Fall through to find next target with a clean slate
        }

        // Find walk target (mirrors findNextBuildZone)
        BlockPos target = findNextClearTarget(mc.player, world);

        if (target == null && !failedClearTargets.isEmpty()) {
            clearStallResets++;
            if (clearStallResets <= MAX_CLEAR_STALL_RESETS) {
                if (statusMessages) {
                    ChatHelper.info("§eAll targets skipped — retrying ("
                            + clearStallResets + "/" + MAX_CLEAR_STALL_RESETS + ")");
                }
                failedClearTargets.clear();
                consecutiveClearFailures = 0;
                target = findNextClearTarget(mc.player, world);
            }
        }
        if (target == null) {
            clearingDone = true;
            if (statusMessages) {
                if (clearBlocksBroken > 0) {
                    ChatHelper.info("§aArea cleared! §7Removed §e" + clearBlocksBroken
                            + "§7 illegal block(s). Commencing build...");
                } else {
                    ChatHelper.info("§aNo illegal blocks found. Commencing build...");
                }
            }
            autoState = AutoState.BUILDING;
            noProgressTicks = 0;
            return;
        }

        // Walk to the zone — same pattern as building
        PathWalker.walkToNearby(target, (int) Math.ceil(range) + 2);
        /*? if >=26.1 {*//*
        lastWalkTargetZone = target.immutable();
        *//*?} else {*/
        lastWalkTargetZone = target.toImmutable();
        /*?}*/
        autoState = AutoState.WALKING_TO_CLEAR;
    }

    /** Breaks scaffold blocks one at a time until all are removed. */
    /*? if >=26.1 {*//*
    private void tickCleaningScaffold(Minecraft mc) {
    *//*?} else {*/
    private void tickCleaningScaffold(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;
        /*?}*/

        // walking to a scaffold block
        if (PathWalker.isActive()) {
            PathWalker.tick();
            return;
        }

        // currently breaking a scaffold block
        if (scaffoldBreakTarget != null) {
            /*? if >=26.1 {*//*
            BlockState current = mc.level.getBlockState(scaffoldBreakTarget);
            *//*?} else {*/
            BlockState current = mc.world.getBlockState(scaffoldBreakTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            if (current.isAir() || current.canBeReplaced()) {
            *//*?} else {*/
            if (current.isAir() || current.isReplaceable()) {
            /*?}*/
                // Block broken — clean up
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setYRot(scaffoldSavedYaw);
                *//*?} else {*/
                mc.player.setYaw(scaffoldSavedYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(scaffoldSavedPitch);
                *//*?} else {*/
                mc.player.setPitch(scaffoldSavedPitch);
                /*?}*/
                PrinterDatabase.removeScaffold(scaffoldBreakTarget);
                scaffoldBreakTarget = null;
                scaffoldBreakTicks = 0;
                scaffoldCooldownTicks = BREAK_COOLDOWN_TICKS;
                return; // next tick will pick up next scaffold
            }

            scaffoldBreakTicks++;
            if (scaffoldBreakTicks > MAX_SCAFFOLD_BREAK_TICKS) {
                // Timed out — skip this block
                /*? if >=26.1 {*//*
                mc.gameMode.stopDestroyBlock();
                *//*?} else {*/
                mc.interactionManager.cancelBlockBreaking();
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setYRot(scaffoldSavedYaw);
                *//*?} else {*/
                mc.player.setYaw(scaffoldSavedYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                mc.player.setXRot(scaffoldSavedPitch);
                *//*?} else {*/
                mc.player.setPitch(scaffoldSavedPitch);
                /*?}*/
                PrinterDatabase.removeScaffold(scaffoldBreakTarget);
                scaffoldBreakTarget = null;
                scaffoldBreakTicks = 0;
                scaffoldCooldownTicks = BREAK_COOLDOWN_TICKS;
                return;
            }

            // Maintain look direction + continue breaking
            /*? if >=26.1 {*//*
            Vec3 eyePos = mc.player.getEyePosition();
            *//*?} else {*/
            Vec3d eyePos = mc.player.getEyePos();
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 blockCenter = Vec3.atCenterOf(scaffoldBreakTarget);
            *//*?} else {*/
            Vec3d blockCenter = Vec3d.ofCenter(scaffoldBreakTarget);
            /*?}*/
            /*? if >=26.1 {*//*
            Vec3 toBlock = blockCenter.subtract(eyePos);
            *//*?} else {*/
            Vec3d toBlock = blockCenter.subtract(eyePos);
            /*?}*/
            double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
            /*? if >=26.1 {*//*
            float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
            *//*?} else {*/
            float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
            /*?}*/
            /*? if >=26.1 {*//*
            float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
            *//*?} else {*/
            float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
            /*?}*/
            PlacementEngine.setLookRotation(mc.player, breakYaw,
                    /*? if >=26.1 {*//*
                    Mth.clamp(breakPitch, -90.0f, 90.0f));
                    *//*?} else {*/
                    MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                    /*?}*/

            /*? if >=26.1 {*//*
            mc.gameMode.continueDestroyBlock(scaffoldBreakTarget, Direction.UP);
            *//*?} else {*/
            mc.interactionManager.updateBlockBreakingProgress(scaffoldBreakTarget, Direction.UP);
            /*?}*/
            /*? if >=26.1 {*//*
            mc.player.swing(InteractionHand.MAIN_HAND);
            *//*?} else {*/
            mc.player.swingHand(Hand.MAIN_HAND);
            /*?}*/
            return;
        }

        // Inter-block cooldown to avoid FastBreak detection
        if (scaffoldCooldownTicks > 0) {
            scaffoldCooldownTicks--;
            return;
        }

        // pick next scaffold block
        if (!PrinterDatabase.hasScaffold()) {
            // All scaffold cleaned up
            if (statusMessages) {
                ChatHelper.info("§aScaffold cleanup complete.");
            }
            autoState = AutoState.IDLE;
            return;
        }

        // Prune scaffold entries whose world block is already gone
        // or no longer matches the stored block type (e.g. player broke
        // it manually or placed a different block there).
        List<BlockPos> gone = new ArrayList<>();
        for (var entry : PrinterDatabase.getScaffoldEntries().entrySet()) {
            BlockPos pos = entry.getKey();
            String storedId = entry.getValue();
            /*? if >=26.1 {*//*
            BlockState st = mc.level.getBlockState(pos);
            *//*?} else {*/
            BlockState st = mc.world.getBlockState(pos);
            /*?}*/
            /*? if >=26.1 {*//*
            if (st.isAir() || st.canBeReplaced()) {
            *//*?} else {*/
            if (st.isAir() || st.isReplaceable()) {
            /*?}*/
                gone.add(pos);
            } else {
                // Verify the block at this position still matches the
                // stored scaffold type — if someone placed a different
                // block here, stop tracking it.
                Item blockItem = st.getBlock().asItem();
                /*? if >=26.1 {*//*
                String currentId = BuiltInRegistries.ITEM.getKey(blockItem).toString();
                *//*?} else {*/
                String currentId = Registries.ITEM.getId(blockItem).toString();
                /*?}*/
                if (!currentId.equals(storedId)) {
                    gone.add(pos);
                }
            }
        }
        PrinterDatabase.removeScaffoldBatch(gone);

        if (!PrinterDatabase.hasScaffold()) {
            if (statusMessages) {
                ChatHelper.info("§aScaffold cleanup complete.");
            }
            autoState = AutoState.IDLE;
            return;
        }

        // Find the closest scaffold block
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        /*? if >=26.1 {*//*
        Vec3 eye = mc.player.getEyePosition();
        *//*?} else {*/
        Vec3d eye = mc.player.getEyePos();
        /*?}*/
        for (BlockPos pos : PrinterDatabase.getScaffoldEntries().keySet()) {
            /*? if >=26.1 {*//*
            double d = eye.distanceToSqr(Vec3.atCenterOf(pos));
            *//*?} else {*/
            double d = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
            /*?}*/
            if (d < closestDist) {
                closestDist = d;
                closest = pos;
            }
        }

        if (closest == null) {
            autoState = AutoState.IDLE;
            return;
        }

        double reachSq = range * range;
        if (closestDist > reachSq) {
            // Walk to the scaffold block
            PathWalker.walkToNearby(closest, (int) Math.ceil(range));
            LOGGER.debug("Walking to scaffold at {} {} {} ({} remaining)",
                    closest.getX(), closest.getY(), closest.getZ(),
                    PrinterDatabase.scaffoldCount());
            return;
        }

        // In reach — start breaking
        /*? if >=26.1 {*//*
        scaffoldBreakTarget = closest.immutable();
        *//*?} else {*/
        scaffoldBreakTarget = closest.toImmutable();
        /*?}*/
        scaffoldBreakTicks = 0;
        /*? if >=26.1 {*//*
        scaffoldSavedYaw = mc.player.getYRot();
        *//*?} else {*/
        scaffoldSavedYaw = mc.player.getYaw();
        /*?}*/
        /*? if >=26.1 {*//*
        scaffoldSavedPitch = mc.player.getXRot();
        *//*?} else {*/
        scaffoldSavedPitch = mc.player.getPitch();
        /*?}*/

        // Select the best tool for breaking this scaffold block
        /*? if >=26.1 {*//*
        BlockState scaffoldState = mc.level.getBlockState(scaffoldBreakTarget);
        *//*?} else {*/
        BlockState scaffoldState = mc.world.getBlockState(scaffoldBreakTarget);
        /*?}*/
        PlacementEngine.selectBestTool(mc.player, mc, scaffoldState);

        /*? if >=26.1 {*//*
        Vec3 blockCenter = Vec3.atCenterOf(scaffoldBreakTarget);
        *//*?} else {*/
        Vec3d blockCenter = Vec3d.ofCenter(scaffoldBreakTarget);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toBlock = blockCenter.subtract(eye);
        *//*?} else {*/
        Vec3d toBlock = blockCenter.subtract(eye);
        /*?}*/
        double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
        /*? if >=26.1 {*//*
        float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        *//*?} else {*/
        float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
        /*?}*/
        /*? if >=26.1 {*//*
        float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        *//*?} else {*/
        float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
        /*?}*/
        PlacementEngine.setLookRotation(mc.player, breakYaw,
                /*? if >=26.1 {*//*
                Mth.clamp(breakPitch, -90.0f, 90.0f));
                *//*?} else {*/
                MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                /*?}*/

        /*? if >=26.1 {*//*
        mc.gameMode.startDestroyBlock(scaffoldBreakTarget, Direction.UP);
        *//*?} else {*/
        mc.interactionManager.attackBlock(scaffoldBreakTarget, Direction.UP);
        /*?}*/
        /*? if >=26.1 {*//*
        mc.player.swing(InteractionHand.MAIN_HAND);
        *//*?} else {*/
        mc.player.swingHand(Hand.MAIN_HAND);
        /*?}*/

        LOGGER.debug("Breaking scaffold at {} {} {} ({} remaining)",
                scaffoldBreakTarget.getX(), scaffoldBreakTarget.getY(),
                scaffoldBreakTarget.getZ(), PrinterDatabase.scaffoldCount());
    }

    /*? if >=26.1 {*//*
    private void tickWalkingToSupply(Minecraft mc) {
    *//*?} else {*/
    private void tickWalkingToSupply(MinecraftClient mc) {
    /*?}*/
        if (!PathWalker.isActive()) {
            // Try opening the chest regardless of PathWalker arrival
            // status — the player may be within interaction range even
            // if Baritone stopped slightly outside PathWalker's strict
            // arrival threshold.
            if (supplyTarget != null && tryOpenChest(mc, supplyTarget)) {
                autoState = AutoState.RESTOCKING;
                restockWaitTicks = 0;
                chestSyncDelay = 0;
                triedWaypointRestock = false;
                triedLinearRestock = false;
                triedPlacementRestock = false;
                supplyDescentPhase = 0;
                supplyDescentTarget = null;
                return;
            }

            // multi-phase descent continuation
            // Phase 1 complete (horizontal walk) → start phase 2 (descend)
            if (supplyDescentPhase == 1 && supplyDescentTarget != null) {
                supplyDescentPhase = 2;
                LOGGER.debug("Descending to Y={}", supplyDescentTarget.getY());
                PathWalker.walkToYLevelWithPlacement(
                        supplyDescentTarget.getY(), mc.player);
                return;
            }
            // Phase 2 complete (GoalYLevel descent) → start phase 3 (approach)
            if (supplyDescentPhase == 2 && supplyDescentTarget != null) {
                // Check if GoalYLevel actually brought us close to target Y.
                // If the player is still far above, Baritone couldn't path
                // down the pillar — fall back to mining descent.
                /*? if >=26.1 {*//*
                int playerY = mc.player.blockPosition().getY();
                *//*?} else {*/
                int playerY = mc.player.getBlockPos().getY();
                /*?}*/
                int targetY = supplyDescentTarget.getY();
                if (playerY - targetY > 5) {
                    LOGGER.debug("GoalYLevel failed (supply) — mining down from Y={} to Y={}",
                            playerY, targetY);
                    supplyDescentPhase = 2; // stay in phase 2 for re-check
                    PathWalker.startMiningDescent(targetY);
                    return;
                }
                supplyDescentPhase = 3;
                LOGGER.debug("Walking to chest...");
                // Short horizontal walk from wherever we ended up
                // to the actual chest position.
                /*? if >=26.1 {*//*
                double dist = Math.sqrt(mc.player.blockPosition()
                *//*?} else {*/
                double dist = Math.sqrt(mc.player.getBlockPos()
                /*?}*/
                        /*? if >=26.1 {*//*
                        .distSqr(supplyDescentTarget));
                        *//*?} else {*/
                        .getSquaredDistance(supplyDescentTarget));
                        /*?}*/
                if (dist > 48) {
                    List<BlockPos> horizLegs = computeLinearWaypoints(
                            /*? if >=26.1 {*//*
                            mc.player.blockPosition(), supplyDescentTarget, 48);
                            *//*?} else {*/
                            mc.player.getBlockPos(), supplyDescentTarget, 48);
                            /*?}*/
                    PathWalker.walkToViaWaypointsWithPlacement(
                            horizLegs, 2, mc.player);
                } else {
                    PathWalker.walkToWithPlacement(
                            supplyDescentTarget, 2, mc.player);
                }
                return;
            }
            // Phase 3 complete or not in descent — clear descent state
            supplyDescentPhase = 0;
            supplyDescentTarget = null;

            // ── waypoint retry: compute database-driven waypoints and
            //    try again with placement enabled. Database waypoints
            //    may follow built structure paths (stairs, corridors)
            //    that straight-line legs miss. ─────────────────────────
            if (!triedWaypointRestock && supplyTarget != null) {
                List<BlockPos> waypoints = computeSupplyWaypoints(
                        /*? if >=26.1 {*//*
                        mc.player.blockPosition(), supplyTarget);
                        *//*?} else {*/
                        mc.player.getBlockPos(), supplyTarget);
                        /*?}*/
                if (waypoints.size() > 1) {
                    triedWaypointRestock = true;
                    LOGGER.debug("Retrying via {} database waypoint(s) + placement",
                            waypoints.size() - 1);
                    PathWalker.walkToViaWaypointsWithPlacement(waypoints, 2, mc.player);
                    return;
                }
            }

            // Elevation-aware retry
            if (!triedLinearRestock && supplyTarget != null) {
                triedLinearRestock = true;
                double retryDy = Math.abs(supplyTarget.getY()
                        /*? if >=26.1 {*//*
                        - mc.player.blockPosition().getY());
                        *//*?} else {*/
                        - mc.player.getBlockPos().getY());
                        /*?}*/
                if (retryDy > 8) {
                    LOGGER.debug("Retrying with elevation-aware placement walk");
                    walkToZoneWithPlacement(mc.player, supplyTarget, 2);
                    return;
                } else {
                    // Flat — try shorter legs
                    List<BlockPos> linear = computeLinearWaypoints(
                            /*? if >=26.1 {*//*
                            mc.player.blockPosition(), supplyTarget, 24);
                            *//*?} else {*/
                            mc.player.getBlockPos(), supplyTarget, 24);
                            /*?}*/
                    if (linear.size() > 1) {
                        LOGGER.debug("Retrying with shorter legs ({} x 24-block) + placement",
                                linear.size() - 1);
                        PathWalker.walkToViaWaypointsWithPlacement(linear, 2, mc.player);
                        return;
                    }
                }
            }

            // Direct placement retry
            if (!triedPlacementRestock && supplyTarget != null) {
                triedPlacementRestock = true;
                LOGGER.debug("Retrying with direct placement walk");
                PathWalker.walkToWithPlacement(supplyTarget, 2, mc.player);
                return;
            }

            // Mark this chest as unreachable so startRestockRun skips it
            if (supplyTarget != null) {
                /*? if >=26.1 {*//*
                unreachableChests.add(supplyTarget.immutable());
                *//*?} else {*/
                unreachableChests.add(supplyTarget.toImmutable());
                /*?}*/
            }
            triedWaypointRestock = false;
            triedLinearRestock = false;
            triedPlacementRestock = false;
            restockFailures++;
            if (restockFailures >= MAX_RESTOCK_FAILURES) {
                if (statusMessages) {
                    ChatHelper.info("§cCan't reach supply chests after "
                            + restockFailures + " attempts."
                            + " Skipping missing items, building with what we have."
                            + "\n§7Still need: " + formatNeededItemIds(neededItems));
                }
                // Skip these items and continue building
                skippedItems.addAll(lastMissingItems);
                addNeededToSkipped();
                autoState = AutoState.BUILDING;
            } else {
                if (statusMessages) {
                    ChatHelper.info("§eSupply chest at §f"
                            + supplyTarget.getX() + " " + supplyTarget.getY()
                            + " " + supplyTarget.getZ()
                            + "§e unreachable (attempt " + restockFailures
                            + "/" + MAX_RESTOCK_FAILURES + ") — trying another."
                            + "\n§7Looking for: " + formatNeededItemIds(neededItems));
                }
                autoState = AutoState.BUILDING;
            }
            noProgressTicks = 0;
            return;
        }
        // While Baritone is walking with placement enabled, periodically
        // scan for scaffold blocks it may have placed (bridges, pillars).
        if (PathWalker.isPlacementEnabled()) {
            scaffoldScanCooldown--;
            if (scaffoldScanCooldown <= 0) {
                scaffoldScanCooldown = SCAFFOLD_SCAN_INTERVAL;
                /*? if >=26.1 {*//*
                scanForScaffoldBlocks(mc.player, mc.level);
                *//*?} else {*/
                scanForScaffoldBlocks(mc.player, mc.world);
                /*?}*/
            }
        }
        PathWalker.tick();
    }

    /*? if >=26.1 {*//*
    private void tickRestocking(Minecraft mc) {
    *//*?} else {*/
    private void tickRestocking(MinecraftClient mc) {
    /*?}*/
        restockWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu containerHandler) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
        /*?}*/
            // Wait for the server to sync chest contents — the handler is
            // created by OpenScreenS2CPacket, but slot data arrives via a
            // separate InventoryS2CPacket that may lag by 1-2 ticks.
            chestSyncDelay++;
            if (chestSyncDelay < CHEST_SYNC_DELAY) return;

            // Snapshot inventory before taking items so we can detect failure
            Map<Item, Integer> invBefore = PlacementEngine.getInventoryContents();

            // Index chest contents before taking items (snapshot for future queries)
            if (supplyTarget != null) {
                MoarMod.getChestManager().scanOpenChest(supplyTarget, containerHandler);
            }

            takeNeededItems(mc, mc.player, containerHandler);
            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/

            // Invalidate the snapshot since we just modified the chest
            if (supplyTarget != null) {
                MoarMod.getChestManager().invalidateSnapshot(supplyTarget);
            }

            // Verify that items were actually obtained
            Map<Item, Integer> invAfter = PlacementEngine.getInventoryContents();
            boolean gotSomething = false;
            for (var entry : invAfter.entrySet()) {
                if (entry.getValue() > invBefore.getOrDefault(entry.getKey(), 0)) {
                    gotSomething = true;
                    break;
                }
            }

            if (!gotSomething) {
                restockFailures++;
                if (restockFailures >= MAX_RESTOCK_FAILURES) {
                    if (statusMessages) {
                        ChatHelper.info("§cSupply chests don't have needed items."
                                + " Skipping missing items, building with what we have."
                                + "\n§7Still need: " + formatNeededItemIds(neededItems));
                    }
                    skippedItems.addAll(lastMissingItems);
                    addNeededToSkipped();
                    autoState = AutoState.BUILDING;
                    noProgressTicks = 0;
                    return;
                }
                LOGGER.debug("Chest had no needed items, trying another chest");
                // Try a different chest next time
                autoState = AutoState.BUILDING;
                noProgressTicks = 0;
                return;
            }

            // Success — reset failure counter
            restockFailures = 0;
            // We got items — previously-skipped materials may now be
            // available, so clear the skip list and let tryPlaceNextBlock
            // re-evaluate everything.
            skippedItems.clear();
            // Player is at the supply chest — there's likely flat ground
            // here, so clear the no-space flag for shulker unloading.
            shulkerNoSpaceSkipped = false;
            // Only clear the specific chest we just used from the
            // unreachable list — other chests may genuinely be
            // unreachable from different positions in a large build.
            if (supplyTarget != null) {
                /*? if >=26.1 {*//*
                unreachableChests.remove(supplyTarget.immutable());
                *//*?} else {*/
                unreachableChests.remove(supplyTarget.toImmutable());
                /*?}*/
            }

            // Check if we grabbed any shulker boxes that need unloading.
            // If so, transition to UNLOADING_SHULKER instead of walking
            // back immediately — the build needs loose items, not shulkers.
            if (findShulkerWithNeededItems(mc.player) >= 0) {
                if (statusMessages) {
                    ChatHelper.info("§aRestocked (shulkers found). Unloading shulker boxes…");
                }
                shulkerUnloadPhase = 0;
                shulkerUnloadTicks = 0;
                shulkerTotalTicks = 0;
                shulkerUnloadFailures = 0;
                autoState = AutoState.UNLOADING_SHULKER;
                return;
            }

            if (statusMessages) {
                ChatHelper.info("§aRestocked. Walking back to build.");
            }

            if (lastBuildPos != null) {
                // Use elevation-aware navigation for the return walk.
                // The build zone may be far above the supply chest
                // (e.g. glass platform at Y=-22 vs ground at Y=-60).
                // walkToZoneWithPlacement handles ascent (pillar up in
                // 8-block steps) and descent (3-phase GoalYLevel).
                double returnDy = Math.abs(lastBuildPos.getY()
                        /*? if >=26.1 {*//*
                        - mc.player.blockPosition().getY());
                        *//*?} else {*/
                        - mc.player.getBlockPos().getY());
                        /*?}*/
                int radius = (int) Math.ceil(range);
                if (returnDy > 8) {
                    walkToZoneWithPlacement(mc.player, lastBuildPos, radius);
                } else {
                    PathWalker.walkToNearby(lastBuildPos, radius);
                }
                autoState = AutoState.WALKING_BACK;
            } else {
                autoState = AutoState.BUILDING;
                noProgressTicks = 0;
            }
            return;
        }

        if (restockWaitTicks >= CHEST_OPEN_TIMEOUT) {
            if (statusMessages) {
                ChatHelper.info("§eChest didn't open, resuming build.");
            }
            autoState = AutoState.BUILDING;
            noProgressTicks = 0;
        }
    }

    // DUMP CHEST — deposit mined items to free inventory space during clearing

    /*? if >=26.1 {*//*
    private void tickDumping(Minecraft mc) {
    *//*?} else {*/
    private void tickDumping(MinecraftClient mc) {
    /*?}*/
        dumpWaitTicks++;

        /*? if >=26.1 {*//*
        AbstractContainerMenu handler = mc.player.containerMenu;
        *//*?} else {*/
        ScreenHandler handler = mc.player.currentScreenHandler;
        /*?}*/
        /*? if >=26.1 {*//*
        if (handler instanceof ChestMenu containerHandler) {
        *//*?} else {*/
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
        /*?}*/
            dumpSyncDelay++;
            if (dumpSyncDelay < DUMP_SYNC_DELAY) return;

            /*? if >=26.1 {*//*
            int chestSlots = containerHandler.getRowCount() * 9;
            *//*?} else {*/
            int chestSlots = containerHandler.getRows() * 9;
            /*?}*/
            int playerSlotStart = chestSlots;
            int playerSlotEnd = chestSlots + 36;

            for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
                /*? if >=26.1 {*//*
                ItemStack stack = containerHandler.getSlot(slot).getItem();
                *//*?} else {*/
                ItemStack stack = containerHandler.getSlot(slot).getStack();
                /*?}*/
                if (stack.isEmpty()) continue;
                /*? if >=26.1 {*//*
                if (stack.isDamageableItem()) continue;
                *//*?} else {*/
                if (stack.isDamageable()) continue;
                /*?}*/
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                /*?}*/
                        /*? if >=26.1 {*//*
                        containerHandler.containerId, slot, 0,
                        *//*?} else {*/
                        containerHandler.syncId, slot, 0,
                        /*?}*/
                        /*? if >=26.1 {*//*
                        ContainerInput.QUICK_MOVE, mc.player);
                        *//*?} else {*/
                        SlotActionType.QUICK_MOVE, mc.player);
                        /*?}*/
            }

            /*? if >=26.1 {*//*
            mc.player.clientSideCloseContainer();
            *//*?} else {*/
            mc.player.closeHandledScreen();
            /*?}*/

            if (statusMessages) {
                ChatHelper.info("§aItems deposited. Resuming clearing.");
            }

            if (preDumpClearPos != null) {
                PathWalker.walkToNearby(preDumpClearPos, (int) Math.ceil(range));
                autoState = AutoState.WALKING_TO_CLEAR;
            } else {
                autoState = AutoState.CLEARING_AREA;
            }
            return;
        }

        // Chest hasn't opened yet — try right-clicking it
        if (dumpWaitTicks == 1 && dumpTarget != null) {
            tryOpenChest(mc, dumpTarget);
        }

        if (dumpWaitTicks >= CHEST_OPEN_TIMEOUT) {
            if (statusMessages) {
                ChatHelper.info("§eDump chest didn't open, resuming clearing.");
            }
            autoState = AutoState.CLEARING_AREA;
        }
    }

    // SHULKER UNLOADING — place → open → take items → break → pickup

    /** Finds the first inventory shulker containing needed items, or -1. */
    /*? if >=26.1 {*//*
    private int findShulkerWithNeededItems(LocalPlayer player) {
    *//*?} else {*/
    private int findShulkerWithNeededItems(ClientPlayerEntity player) {
    /*?}*/
        if (neededItems == null || neededItems.isEmpty()) return -1;
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (isShulkerBox(stack) && shulkerContainsNeeded(stack, neededItems)) {
                return i;
            }
        }
        return -1;
    }

    /** Finds a valid nearby position to place a shulker box. */
    /*? if >=26.1 {*//*
    private BlockPos findShulkerPlaceSpot(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findShulkerPlaceSpot(ClientPlayerEntity player, World world) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerFeet = player.blockPosition();
        *//*?} else {*/
        BlockPos playerFeet = player.getBlockPos();
        /*?}*/
        // Skip positions overlapping the player bounding box (0.6×1.8).
        // Prefer non-interactive support blocks to avoid chest/barrel GUI.
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        boolean bestIsInteractive = true;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = playerFeet.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = playerFeet.add(dx, dy, dz);
                    /*?}*/
                    // Skip: player collision
                    if (px - 0.3 < pos.getX() + 1 && px + 0.3 > pos.getX()
                            && py < pos.getY() + 1 && py + 1.8 > pos.getY()
                            && pz - 0.3 < pos.getZ() + 1 && pz + 0.3 > pos.getZ()) {
                        continue;
                    }
                    BlockState state = world.getBlockState(pos);
                    /*? if >=26.1 {*//*
                    BlockState below = world.getBlockState(pos.below());
                    *//*?} else {*/
                    BlockState below = world.getBlockState(pos.down());
                    /*?}*/
                    // Need air/replaceable at the position, solid below,
                    // and air above so the shulker lid can open.
                    /*? if >=26.1 {*//*
                    BlockState above = world.getBlockState(pos.above());
                    *//*?} else {*/
                    BlockState above = world.getBlockState(pos.up());
                    /*?}*/
                    /*? if >=26.1 {*//*
                    if ((state.isAir() || state.canBeReplaced())
                    *//*?} else {*/
                    if ((state.isAir() || state.isReplaceable())
                    /*?}*/
                            /*? if >=26.1 {*//*
                            && !below.getCollisionShape(world, pos.below()).isEmpty()
                            *//*?} else {*/
                            && !below.getCollisionShape(world, pos.down()).isEmpty()
                            /*?}*/
                            /*? if >=26.1 {*//*
                            && (above.isAir() || above.canBeReplaced())) {
                            *//*?} else {*/
                            && (above.isAir() || above.isReplaceable())) {
                            /*?}*/
                        /*? if >=26.1 {*//*
                        double dist = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                        *//*?} else {*/
                        double dist = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                        /*?}*/
                        if (dist > 4.5 * 4.5) continue;
                        boolean interactive = PlacementEngine.isInteractive(below.getBlock());
                        // Prefer non-interactive support, then nearest
                        if (bestIsInteractive && !interactive) {
                            // non-interactive wins
                            best = pos;
                            bestDist = dist;
                            bestIsInteractive = false;
                        } else if (interactive == bestIsInteractive && dist < bestDist) {
                            best = pos;
                            bestDist = dist;
                            bestIsInteractive = interactive;
                        }
                    }
                }
            }
        }
        return best;
    }

    private Set<BlockPos> getProtectedStoragePositions() {
        Set<BlockPos> out = new HashSet<>();
        out.addAll(MoarMod.getChestManager().getSupplyPositions());
        out.addAll(MoarMod.getChestManager().getDumpPositions());
        out.addAll(MoarMod.getChestManager().getStorageChests());
        return out;
    }

    /*? if >=26.1 {*//*
    private boolean isStorageBlacklistedForClearing(BlockState state, BlockPos pos, Set<BlockPos> protectedStorage) {
    *//*?} else {*/
    private boolean isStorageBlacklistedForClearing(BlockState state, BlockPos pos, Set<BlockPos> protectedStorage) {
    /*?}*/
        if (protectedStorage.contains(pos)) return true;
        Block b = state.getBlock();
        return b instanceof AbstractChestBlock
                || b instanceof BarrelBlock
                || b instanceof ShulkerBoxBlock
                || b instanceof HopperBlock;
    }

    /**
     * Places a solid block adjacent to the player to create a shulker platform.
     * Used when findShulkerPlaceSpot returns null (narrow pillar/ledge).
     */
    /*? if >=26.1 {*//*
    private boolean tryBuildShulkerPlatform(LocalPlayer player,
    *//*?} else {*/
    private boolean tryBuildShulkerPlatform(ClientPlayerEntity player,
    /*?}*/
                                            /*? if >=26.1 {*//*
                                            Level world,
                                            *//*?} else {*/
                                            World world,
                                            /*?}*/
                                            /*? if >=26.1 {*//*
                                            Minecraft mc) {
                                            *//*?} else {*/
                                            MinecraftClient mc) {
                                            /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerFeet = player.blockPosition();
        *//*?} else {*/
        BlockPos playerFeet = player.getBlockPos();
        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos standingOn = playerFeet.below();
        *//*?} else {*/
        BlockPos standingOn = playerFeet.down();
        /*?}*/
        /*? if >=26.1 {*//*
        Inventory inv = player.getInventory();
        *//*?} else {*/
        PlayerInventory inv = player.getInventory();
        /*?}*/

        // Find a solid block in inventory to use as platform
        // Prefer cheap/common blocks.  Avoid shulker boxes themselves.
        int blockSlot = -1;
        for (int i = 0; i < 36; i++) {
            /*? if >=26.1 {*//*
            ItemStack stack = inv.getItem(i);
            *//*?} else {*/
            ItemStack stack = inv.getStack(i);
            /*?}*/
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem bi)) continue;
            if (isShulkerBox(stack)) continue;
            Block block = bi.getBlock();
            // Must be a full solid block (not a torch, slab, etc.)
            /*? if >=26.1 {*//*
            if (block.defaultBlockState().isCollisionShapeFullBlock(world, BlockPos.ZERO)) {
            *//*?} else {*/
            if (block.getDefaultState().isFullCube(world, BlockPos.ORIGIN)) {
            /*?}*/
                blockSlot = i;
                break;
            }
        }
        if (blockSlot < 0) return false; // no suitable block

        // Find a placement position
        // Search at multiple Y levels around the player, not just
        // standingOn's Y.  Also search adjacent to any previously
        // placed platform block so multi-attempt extensions work.
        // The placed block creates a surface for the shulker.
        // We prefer positions where an air block exists above the
        // candidate so the shulker can actually be placed there.
        BlockPos placeTarget = null;
        Direction placeFace = null;
        double bestDist = Double.MAX_VALUE;
        // Score: 0 = normal, -1 = has air above (preferred for shulker)
        int bestScore = Integer.MAX_VALUE;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos candidate = standingOn.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos candidate = standingOn.add(dx, dy, dz);
                    /*?}*/
                    // Don't place where the player is or directly below
                    if (candidate.equals(playerFeet) || candidate.equals(standingOn)
                            /*? if >=26.1 {*//*
                            || candidate.equals(playerFeet.above())) continue;
                            *//*?} else {*/
                            || candidate.equals(playerFeet.up())) continue;
                            /*?}*/
                    BlockState candidateState = world.getBlockState(candidate);
                    /*? if >=26.1 {*//*
                    if (!candidateState.isAir() && !candidateState.canBeReplaced()) continue;
                    *//*?} else {*/
                    if (!candidateState.isAir() && !candidateState.isReplaceable()) continue;
                    /*?}*/

                    // Determine if this position would yield a shulker spot:
                    // needs air above for the shulker box to be placed on top
                    /*? if >=26.1 {*//*
                    BlockState aboveState = world.getBlockState(candidate.above());
                    *//*?} else {*/
                    BlockState aboveState = world.getBlockState(candidate.up());
                    /*?}*/
                    /*? if >=26.1 {*//*
                    int score = (aboveState.isAir() || aboveState.canBeReplaced()) ? -1 : 0;
                    *//*?} else {*/
                    int score = (aboveState.isAir() || aboveState.isReplaceable()) ? -1 : 0;
                    /*?}*/

                    // Check if any face of this air block has a solid neighbor
                    // we can place against (including the previously placed
                    // platform block, if any)
                    for (Direction dir : Direction.values()) {
                        /*? if >=26.1 {*//*
                        BlockPos neighbor = candidate.relative(dir);
                        *//*?} else {*/
                        BlockPos neighbor = candidate.offset(dir);
                        /*?}*/
                        BlockState neighborState = world.getBlockState(neighbor);
                        /*? if >=26.1 {*//*
                        if (!neighborState.isAir() && !neighborState.canBeReplaced()
                        *//*?} else {*/
                        if (!neighborState.isAir() && !neighborState.isReplaceable()
                        /*?}*/
                                && !neighborState.getCollisionShape(world, neighbor).isEmpty()) {
                            /*? if >=26.1 {*//*
                            double dist = player.getEyePosition().distanceToSqr(
                            *//*?} else {*/
                            double dist = player.getEyePos().squaredDistanceTo(
                            /*?}*/
                                    /*? if >=26.1 {*//*
                                    Vec3.atCenterOf(candidate));
                                    *//*?} else {*/
                                    Vec3d.ofCenter(candidate));
                                    /*?}*/
                            if (dist <= 4.5 * 4.5
                                    && (score < bestScore
                                        || (score == bestScore && dist < bestDist))) {
                                bestScore = score;
                                bestDist = dist;
                                placeTarget = candidate;
                                // We place against the neighbor, clicking the
                                // face that faces toward the candidate
                                placeFace = dir.getOpposite();
                            }
                            break;
                        }
                    }
                }
            }
        }
        if (placeTarget == null || placeFace == null) return false;

        // Rotate toward the target before placing
        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos clickBlock = placeTarget.relative(placeFace.getOpposite());
        *//*?} else {*/
        BlockPos clickBlock = placeTarget.offset(placeFace.getOpposite());
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 clickCenter = Vec3.atCenterOf(clickBlock);
        *//*?} else {*/
        Vec3d clickCenter = Vec3d.ofCenter(clickBlock);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toClick = clickCenter.subtract(eyePos);
        *//*?} else {*/
        Vec3d toClick = clickCenter.subtract(eyePos);
        /*?}*/
        double horizDist = Math.sqrt(toClick.x * toClick.x + toClick.z * toClick.z);
        /*? if >=26.1 {*//*
        float platYaw = (float) (Mth.atan2(toClick.z, toClick.x)
        *//*?} else {*/
        float platYaw = (float) (MathHelper.atan2(toClick.z, toClick.x)
        /*?}*/
                * (180.0 / Math.PI)) - 90.0f;
        /*? if >=26.1 {*//*
        float platPitch = (float) -(Mth.atan2(toClick.y, horizDist)
        *//*?} else {*/
        float platPitch = (float) -(MathHelper.atan2(toClick.y, horizDist)
        /*?}*/
                * (180.0 / Math.PI));
        PlacementEngine.sendLookPacket(player, platYaw,
                /*? if >=26.1 {*//*
                Mth.clamp(platPitch, -90.0f, 90.0f));
                *//*?} else {*/
                MathHelper.clamp(platPitch, -90.0f, 90.0f));
                /*?}*/

        // Swap the block into the current hotbar slot
        /*? if >=1.21.5 {*//*
        int currentSlot = inv.getSelectedSlot();
        *//*?} else {*/
        int currentSlot = inv.selectedSlot;
        /*?}*/
        if (blockSlot >= 9) {
            // Main inventory → swap into current hotbar slot
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(
            *//*?} else {*/
            mc.interactionManager.clickSlot(
            /*?}*/
                    /*? if >=26.1 {*//*
                    player.containerMenu.containerId,
                    *//*?} else {*/
                    player.currentScreenHandler.syncId,
                    /*?}*/
                    blockSlot, currentSlot,
                    /*? if >=26.1 {*//*
                    ContainerInput.SWAP, player);
                    *//*?} else {*/
                    SlotActionType.SWAP, player);
                    /*?}*/
        } else if (blockSlot != currentSlot) {
            /*? if >=1.21.5 {*//*
            inv.setSelectedSlot(blockSlot);
            *//*?} else {*/
            inv.selectedSlot = blockSlot;
            /*?}*/
        }

        // Place the block
        Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

        BlockHitResult hit = new BlockHitResult(
                clickCenter,
                placeFace,
                clickBlock,
                false);
        /*? if >=26.1 {*//*
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        *//*?} else {*/
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        /*?}*/

        restoreSneak.run();

        // Swap original item back if we displaced it
        if (blockSlot >= 9) {
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(
            *//*?} else {*/
            mc.interactionManager.clickSlot(
            /*?}*/
                    /*? if >=26.1 {*//*
                    player.containerMenu.containerId,
                    *//*?} else {*/
                    player.currentScreenHandler.syncId,
                    /*?}*/
                    blockSlot, currentSlot,
                    /*? if >=26.1 {*//*
                    ContainerInput.SWAP, player);
                    *//*?} else {*/
                    SlotActionType.SWAP, player);
                    /*?}*/
        }

        platformBlockPos = placeTarget;

        // Track as scaffold for cleanup
        /*? if >=26.1 {*//*
        ItemStack placedStack = inv.getItem(blockSlot >= 9 ? currentSlot : blockSlot);
        *//*?} else {*/
        ItemStack placedStack = inv.getStack(blockSlot >= 9 ? currentSlot : blockSlot);
        /*?}*/
        if (!placedStack.isEmpty()) {
            /*? if >=26.1 {*//*
            String itemId = BuiltInRegistries.ITEM.getKey(placedStack.getItem()).toString();
            *//*?} else {*/
            String itemId = Registries.ITEM.getId(placedStack.getItem()).toString();
            /*?}*/
            PrinterDatabase.addScaffold(placeTarget, itemId);
        }

        LOGGER.debug("Built platform block for shulker placement");
        return true;
    }

    /**
     * State machine for unloading shulker boxes grabbed from the supply
     * chest.  Phases:
     *   0 — find next shulker with needed items in inventory
     *   1 — select/swap shulker into hotbar (wait 1 tick for server)
     *   2 — place the shulker on the ground
     *   3 — wait for server to register the placed shulker block
     *   4 — open the shulker (interact with it)
     *   5 — take needed items from the open shulker screen
     *   6 — close screen, start breaking the shulker
     *   7 — continue breaking until it drops
     *   8 — wait for the item entity to be picked up
     */
    /*? if >=26.1 {*//*
    private void tickUnloadingShulker(Minecraft mc) {
    *//*?} else {*/
    private void tickUnloadingShulker(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        *//*?} else {*/
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        /*?}*/
        /*? if >=26.1 {*//*
        LocalPlayer player = mc.player;
        *//*?} else {*/
        ClientPlayerEntity player = mc.player;
        /*?}*/
        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/

        shulkerUnloadTicks++;
        shulkerTotalTicks++;

        // Global safety timeout — prevents unbreakable loops
        if (shulkerTotalTicks >= MAX_SHULKER_TOTAL_TICKS) {
            if (statusMessages) {
                ChatHelper.info("§c⚠ Shulker unloading timed out — aborting.");
            }
            // Clean up: close any open screen, cancel breaking
            /*? if >=26.1 {*//*
            if (mc.screen != null) player.clientSideCloseContainer();
            *//*?} else {*/
            if (mc.currentScreen != null) player.closeHandledScreen();
            /*?}*/
            /*? if >=26.1 {*//*
            mc.gameMode.stopDestroyBlock();
            *//*?} else {*/
            mc.interactionManager.cancelBlockBreaking();
            /*?}*/
            // Prevent immediate re-entry into shulker unloading at
            // the same position that just timed out.
            shulkerNoSpaceSkipped = true;
            finishShulkerUnloading(mc);
            return;
        }

        switch (shulkerUnloadPhase) {

            // Phase 0: Find a shulker in inventory
            case 0 -> {
                if (shulkerUnloadFailures >= MAX_SHULKER_FAILURES) {
                    if (statusMessages) {
                        ChatHelper.info("§eShulker unloading failed too many times — skipping.");
                    }
                    // Mark as no-space so the system doesn't immediately
                    // re-detect the same shulkers and loop at this position.
                    shulkerNoSpaceSkipped = true;
                    finishShulkerUnloading(mc);
                    return;
                }
                int slot = findShulkerWithNeededItems(player);
                if (slot < 0) {
                    // No more shulkers to unload — proceed to walk back
                    finishShulkerUnloading(mc);
                    return;
                }
                shulkerHotbarSlot = slot;
                shulkerPlacePos = findShulkerPlaceSpot(player, world);
                if (shulkerPlacePos == null) {
                    // No placement spot — try building platform blocks.
                    // We allow up to MAX_PLATFORM_ATTEMPTS tries so that
                    // multi-block extensions can create enough surface.
                    if (platformBuildAttempts < MAX_PLATFORM_ATTEMPTS) {
                        platformBuildAttempts++;
                        if (tryBuildShulkerPlatform(player, world, mc)) {
                            // Block placed — wait a few ticks for server
                            // to register, then retry spot search.
                            shulkerUnloadTicks = 0;
                            return; // stay in phase 0
                        }
                    }
                    // We tried building a platform — wait for it to register
                    if (platformBlockPos != null && shulkerUnloadTicks < 8) {
                        return; // still waiting
                    }
                    // Retry spot search after platform wait
                    shulkerPlacePos = findShulkerPlaceSpot(player, world);
                    if (shulkerPlacePos == null) {
                        shulkerNoSpaceSkipped = true;
                        if (statusMessages) {
                            ChatHelper.info("§eNo space to place shulker — will restock from chest instead.");
                        }
                        finishShulkerUnloading(mc);
                        return;
                    }
                }
                /*? if >=26.1 {*//*
                shulkerSavedYaw = player.getYRot();
                *//*?} else {*/
                shulkerSavedYaw = player.getYaw();
                /*?}*/
                /*? if >=26.1 {*//*
                shulkerSavedPitch = player.getXRot();
                *//*?} else {*/
                shulkerSavedPitch = player.getPitch();
                /*?}*/
                shulkerUnloadPhase = 1;
                shulkerUnloadTicks = 0;
            }

            // Phase 1: Select/swap shulker into hotbar
            //    Must be a separate tick from placement so the server
            //    processes the slot change before we try to use it.
            case 1 -> {
                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                /*?}*/
                if (shulkerHotbarSlot >= 9) {
                    // Swap from main inventory to current hotbar slot
                    /*? if >=26.1 {*//*
                    mc.gameMode.handleContainerInput(
                    *//*?} else {*/
                    mc.interactionManager.clickSlot(
                    /*?}*/
                            /*? if >=26.1 {*//*
                            player.containerMenu.containerId,
                            *//*?} else {*/
                            player.currentScreenHandler.syncId,
                            /*?}*/
                            shulkerHotbarSlot,
                            /*? if >=1.21.5 {*//*
                            inv.getSelectedSlot(),
                            *//*?} else {*/
                            inv.selectedSlot,
                            /*?}*/
                            /*? if >=26.1 {*//*
                            ContainerInput.SWAP,
                            *//*?} else {*/
                            SlotActionType.SWAP,
                            /*?}*/
                            player
                    );
                } else {
                    /*? if >=1.21.5 {*//*
                    inv.setSelectedSlot(shulkerHotbarSlot);
                    *//*?} else {*/
                    inv.selectedSlot = shulkerHotbarSlot;
                    /*?}*/
                }
                // Wait 2 ticks for server to process the slot swap
                shulkerUnloadPhase = 2;
                shulkerUnloadTicks = 0;
            }

            // Phase 2: Place the shulker on the ground
            case 2 -> {
                // Wait at least 2 ticks after swap for server sync
                if (shulkerUnloadTicks < 2) return;

                // Verify we're actually holding a shulker box
                /*? if >=26.1 {*//*
                Inventory inv = player.getInventory();
                *//*?} else {*/
                PlayerInventory inv = player.getInventory();
                /*?}*/
                /*? if >=26.1 {*//*
                ItemStack held = inv.getItem(inv.getSelectedSlot());
                *//*?} else if >=1.21.5 {*//*
                ItemStack held = inv.getStack(inv.getSelectedSlot());
                *//*?} else {*/
                ItemStack held = inv.getStack(inv.selectedSlot);
                /*?}*/
                if (!isShulkerBox(held)) {
                    // Swap didn't work — count as failure
                    shulkerUnloadFailures++;
                    LOGGER.debug("Shulker not in hand after swap — retrying");
                    shulkerUnloadPhase = 0;
                    shulkerUnloadTicks = 0;
                    return;
                }

                // Rotate toward the placement target before placing so
                // servers with anti-cheat accept the interaction packet.
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 target = Vec3.atCenterOf(shulkerPlacePos.below())
                *//*?} else {*/
                Vec3d target = Vec3d.ofCenter(shulkerPlacePos.down())
                /*?}*/
                        .add(0, 0.5, 0); // top face of the support block
                /*? if >=26.1 {*//*
                Vec3 toTarget = target.subtract(eyePos);
                *//*?} else {*/
                Vec3d toTarget = target.subtract(eyePos);
                /*?}*/
                double horizDist = Math.sqrt(toTarget.x * toTarget.x
                        + toTarget.z * toTarget.z);
                /*? if >=26.1 {*//*
                float placeYaw = (float) (Mth.atan2(toTarget.z, toTarget.x)
                *//*?} else {*/
                float placeYaw = (float) (MathHelper.atan2(toTarget.z, toTarget.x)
                /*?}*/
                        * (180.0 / Math.PI)) - 90.0f;
                /*? if >=26.1 {*//*
                float placePitch = (float) -(Mth.atan2(toTarget.y, horizDist)
                *//*?} else {*/
                float placePitch = (float) -(MathHelper.atan2(toTarget.y, horizDist)
                /*?}*/
                        * (180.0 / Math.PI));
                PlacementEngine.sendLookPacket(player, placeYaw,
                        /*? if >=26.1 {*//*
                        Mth.clamp(placePitch, -90.0f, 90.0f));
                        *//*?} else {*/
                        MathHelper.clamp(placePitch, -90.0f, 90.0f));
                        /*?}*/

                // Wait for server to process look rotation.
                if (shulkerUnloadTicks < 4) return;

                // Sneak to place on interactive blocks (chests, barrels…)
                Runnable restoreSneak = PlacementEngine.ensureSneakForPlacement(player);

                // Place the shulker on top of the block below the target
                BlockHitResult hit = new BlockHitResult(
                        /*? if >=26.1 {*//*
                        Vec3.atCenterOf(shulkerPlacePos.below())
                        *//*?} else {*/
                        Vec3d.ofCenter(shulkerPlacePos.down())
                        /*?}*/
                                .add(0, 0.5, 0), // hit the top surface
                        Direction.UP,
                        /*? if >=26.1 {*//*
                        shulkerPlacePos.below(),
                        *//*?} else {*/
                        shulkerPlacePos.down(),
                        /*?}*/
                        false);
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/

                restoreSneak.run();

                shulkerUnloadPhase = 3;
                shulkerUnloadTicks = 0;
            }

            // Phase 3: Wait for placement to register
            case 3 -> {
                BlockState st = world.getBlockState(shulkerPlacePos);
                if (st.getBlock() instanceof ShulkerBoxBlock) {
                    // Placed successfully — reset failure counter
                    shulkerUnloadFailures = 0;
                    shulkerUnloadPhase = 4;
                    shulkerUnloadTicks = 0;
                    return;
                }
                if (shulkerUnloadTicks >= SHULKER_PLACE_DELAY) {
                    shulkerUnloadFailures++;
                    LOGGER.debug("Shulker placement failed (attempt {}/{})",
                            shulkerUnloadFailures, MAX_SHULKER_FAILURES);
                    shulkerUnloadPhase = 0;
                    shulkerUnloadTicks = 0;
                }
            }

            // Phase 4: Open the placed shulker
            case 4 -> {
                // Rotate to look at the shulker before interacting so the
                // server's line-of-sight / facing checks accept the request.
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 shulkerCenter = Vec3.atCenterOf(shulkerPlacePos);
                *//*?} else {*/
                Vec3d shulkerCenter = Vec3d.ofCenter(shulkerPlacePos);
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 toShulker = shulkerCenter.subtract(eyePos);
                *//*?} else {*/
                Vec3d toShulker = shulkerCenter.subtract(eyePos);
                /*?}*/
                double horizDist = Math.sqrt(toShulker.x * toShulker.x
                        + toShulker.z * toShulker.z);
                /*? if >=26.1 {*//*
                float openYaw = (float) (Mth.atan2(toShulker.z, toShulker.x)
                *//*?} else {*/
                float openYaw = (float) (MathHelper.atan2(toShulker.z, toShulker.x)
                /*?}*/
                        * (180.0 / Math.PI)) - 90.0f;
                /*? if >=26.1 {*//*
                float openPitch = (float) -(Mth.atan2(toShulker.y, horizDist)
                *//*?} else {*/
                float openPitch = (float) -(MathHelper.atan2(toShulker.y, horizDist)
                /*?}*/
                        * (180.0 / Math.PI));
                PlacementEngine.sendLookPacket(player, openYaw,
                        /*? if >=26.1 {*//*
                        Mth.clamp(openPitch, -90.0f, 90.0f));
                        *//*?} else {*/
                        MathHelper.clamp(openPitch, -90.0f, 90.0f));
                        /*?}*/

                // Wait for rotation to propagate to the server
                if (shulkerUnloadTicks < 3) return;

                Runnable restoreSneak = PlacementEngine.releaseForInteraction(player);

                // Use the face facing the player for a more natural hit
                /*? if >=26.1 {*//*
                Direction hitFace = Direction.getApproximateNearest(
                *//*?} else {*/
                Direction hitFace = Direction.getFacing(
                /*?}*/
                        (float) -toShulker.x, (float) -toShulker.y, (float) -toShulker.z);
                BlockHitResult hit = new BlockHitResult(
                        shulkerCenter,
                        hitFace,
                        shulkerPlacePos,
                        false);
                /*? if >=26.1 {*//*
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                *//*?} else {*/
                mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                /*?}*/

                restoreSneak.run();

                shulkerUnloadPhase = 5;
                shulkerUnloadTicks = 0;
                shulkerSyncDelay = 0;
            }

            // Phase 5: Take needed items from the shulker screen
            case 5 -> {
                /*? if >=26.1 {*//*
                AbstractContainerMenu handler = player.containerMenu;
                *//*?} else {*/
                ScreenHandler handler = player.currentScreenHandler;
                /*?}*/
                // Shulker boxes use ShulkerBoxScreenHandler, NOT GenericContainerScreenHandler
                /*? if >=26.1 {*//*
                if (handler instanceof ShulkerBoxMenu shulkerHandler) {
                *//*?} else {*/
                if (handler instanceof ShulkerBoxScreenHandler shulkerHandler) {
                /*?}*/
                    // Wait for server sync
                    shulkerSyncDelay++;
                    if (shulkerSyncDelay < CHEST_SYNC_DELAY) return;

                    // Count free inventory slots — reserve 1 for the broken
                    // shulker item so it can be picked up after breaking.
                    /*? if >=26.1 {*//*
                    Inventory inv = player.getInventory();
                    *//*?} else {*/
                    PlayerInventory inv = player.getInventory();
                    /*?}*/
                    int freeSlots = 0;
                    for (int i = 0; i < 36; i++) {
                        /*? if >=26.1 {*//*
                        if (inv.getItem(i).isEmpty()) freeSlots++;
                        *//*?} else {*/
                        if (inv.getStack(i).isEmpty()) freeSlots++;
                        /*?}*/
                    }
                    // We need at least 1 slot free for the broken shulker.
                    // QUICK_MOVE stacks with existing items first, so only
                    // count items that would consume a NEW slot.
                    int slotsReserved = 1; // for the shulker item itself

                    // Take needed items — shulker boxes always have 27 slots (3×9)
                    for (int slot = 0; slot < 27; slot++) {
                        /*? if >=26.1 {*//*
                        ItemStack stack = shulkerHandler.getSlot(slot).getItem();
                        *//*?} else {*/
                        ItemStack stack = shulkerHandler.getSlot(slot).getStack();
                        /*?}*/
                        if (stack.isEmpty()) continue;
                        /*? if >=26.1 {*//*
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        *//*?} else {*/
                        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                        /*?}*/
                        if (neededItems.contains(itemId)) {
                            // Check if this item would stack with something
                            // already in the player's inventory.
                            boolean wouldStack = false;
                            for (int pi = 0; pi < 36; pi++) {
                                /*? if >=26.1 {*//*
                                ItemStack piStack = inv.getItem(pi);
                                *//*?} else {*/
                                ItemStack piStack = inv.getStack(pi);
                                /*?}*/
                                if (!piStack.isEmpty()
                                        /*? if >=26.1 {*//*
                                        && ItemStack.isSameItem(piStack, stack)
                                        *//*?} else {*/
                                        && ItemStack.areItemsEqual(piStack, stack)
                                        /*?}*/
                                        /*? if >=26.1 {*//*
                                        && piStack.getCount() < piStack.getMaxStackSize()) {
                                        *//*?} else {*/
                                        && piStack.getCount() < piStack.getMaxCount()) {
                                        /*?}*/
                                    wouldStack = true;
                                    break;
                                }
                            }
                            if (!wouldStack) {
                                // Would consume a new slot
                                if (freeSlots <= slotsReserved) {
                                    // Not enough room — stop taking items
                                    break;
                                }
                                freeSlots--;
                            }
                            /*? if >=26.1 {*//*
                            mc.gameMode.handleContainerInput(
                            *//*?} else {*/
                            mc.interactionManager.clickSlot(
                            /*?}*/
                                    /*? if >=26.1 {*//*
                                    shulkerHandler.containerId, slot, 0,
                                    *//*?} else {*/
                                    shulkerHandler.syncId, slot, 0,
                                    /*?}*/
                                    /*? if >=26.1 {*//*
                                    ContainerInput.QUICK_MOVE, player);
                                    *//*?} else {*/
                                    SlotActionType.QUICK_MOVE, player);
                                    /*?}*/
                        }
                    }

                    // Close the screen
                    /*? if >=26.1 {*//*
                    player.clientSideCloseContainer();
                    *//*?} else {*/
                    player.closeHandledScreen();
                    /*?}*/
                    shulkerOpenRetries = 0;
                    shulkerUnloadPhase = 6;
                    shulkerUnloadTicks = 0;
                    return;
                }

                // Screen not open yet — wait, then retry opening
                if (shulkerUnloadTicks >= MAX_SHULKER_PHASE_TICKS) {
                    /*? if >=26.1 {*//*
                    if (mc.screen != null) player.clientSideCloseContainer();
                    *//*?} else {*/
                    if (mc.currentScreen != null) player.closeHandledScreen();
                    /*?}*/
                    if (shulkerOpenRetries < MAX_SHULKER_OPEN_RETRIES) {
                        shulkerOpenRetries++;
                        LOGGER.debug("Shulker screen didn't open (retry {}/{})",
                                shulkerOpenRetries, MAX_SHULKER_OPEN_RETRIES);
                        // Go back to phase 4 to retry the open interaction
                        shulkerUnloadPhase = 4;
                        shulkerUnloadTicks = 0;
                    } else {
                        // Exhausted retries — break it and move on
                        shulkerUnloadFailures++;
                        shulkerOpenRetries = 0;
                        LOGGER.debug("Shulker screen didn't open after {} retries — breaking it",
                                MAX_SHULKER_OPEN_RETRIES);
                        shulkerUnloadPhase = 6;
                        shulkerUnloadTicks = 0;
                    }
                }
            }

            // Phase 6: Start breaking the placed shulker
            case 6 -> {
                // Make sure screen is closed
                /*? if >=26.1 {*//*
                if (mc.screen != null) {
                *//*?} else {*/
                if (mc.currentScreen != null) {
                /*?}*/
                    /*? if >=26.1 {*//*
                    player.clientSideCloseContainer();
                    *//*?} else {*/
                    player.closeHandledScreen();
                    /*?}*/
                    return;
                }

                BlockState st = world.getBlockState(shulkerPlacePos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    // Already broken — go to pickup
                    shulkerUnloadPhase = 8;
                    shulkerUnloadTicks = 0;
                    return;
                }

                // Look at the shulker and start breaking
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 blockCenter = Vec3.atCenterOf(shulkerPlacePos);
                *//*?} else {*/
                Vec3d blockCenter = Vec3d.ofCenter(shulkerPlacePos);
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 toBlock = blockCenter.subtract(eyePos);
                *//*?} else {*/
                Vec3d toBlock = blockCenter.subtract(eyePos);
                /*?}*/
                double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
                /*? if >=26.1 {*//*
                float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
                *//*?} else {*/
                float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
                /*?}*/
                /*? if >=26.1 {*//*
                float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
                *//*?} else {*/
                float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
                /*?}*/
                /*? if >=26.1 {*//*
                player.setYRot(breakYaw);
                *//*?} else {*/
                player.setYaw(breakYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                player.setXRot(Mth.clamp(breakPitch, -90.0f, 90.0f));
                *//*?} else {*/
                player.setPitch(MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                /*?}*/

                /*? if >=26.1 {*//*
                mc.gameMode.continueDestroyBlock(shulkerPlacePos, Direction.UP);
                *//*?} else {*/
                mc.interactionManager.updateBlockBreakingProgress(shulkerPlacePos, Direction.UP);
                /*?}*/
                /*? if >=26.1 {*//*
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
                shulkerUnloadPhase = 7;
                shulkerUnloadTicks = 0;
            }

            // Phase 7: Continue breaking until shulker drops
            case 7 -> {
                BlockState st = world.getBlockState(shulkerPlacePos);
                if (!(st.getBlock() instanceof ShulkerBoxBlock)) {
                    // Broken!
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.setYRot(shulkerSavedYaw);
                    *//*?} else {*/
                    player.setYaw(shulkerSavedYaw);
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.setXRot(shulkerSavedPitch);
                    *//*?} else {*/
                    player.setPitch(shulkerSavedPitch);
                    /*?}*/
                    shulkerUnloadPhase = 8;
                    shulkerUnloadTicks = 0;
                    return;
                }

                if (shulkerUnloadTicks >= MAX_SHULKER_PHASE_TICKS) {
                    /*? if >=26.1 {*//*
                    mc.gameMode.stopDestroyBlock();
                    *//*?} else {*/
                    mc.interactionManager.cancelBlockBreaking();
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.setYRot(shulkerSavedYaw);
                    *//*?} else {*/
                    player.setYaw(shulkerSavedYaw);
                    /*?}*/
                    /*? if >=26.1 {*//*
                    player.setXRot(shulkerSavedPitch);
                    *//*?} else {*/
                    player.setPitch(shulkerSavedPitch);
                    /*?}*/
                    shulkerUnloadFailures++;
                    if (statusMessages) {
                        ChatHelper.info("§eShulker break timed out — aborting.");
                    }
                    // Don't loop — just finish, items are already taken
                    finishShulkerUnloading(mc);
                    return;
                }

                // Maintain look direction + continue breaking
                /*? if >=26.1 {*//*
                Vec3 eyePos = player.getEyePosition();
                *//*?} else {*/
                Vec3d eyePos = player.getEyePos();
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 blockCenter = Vec3.atCenterOf(shulkerPlacePos);
                *//*?} else {*/
                Vec3d blockCenter = Vec3d.ofCenter(shulkerPlacePos);
                /*?}*/
                /*? if >=26.1 {*//*
                Vec3 toBlock = blockCenter.subtract(eyePos);
                *//*?} else {*/
                Vec3d toBlock = blockCenter.subtract(eyePos);
                /*?}*/
                double horizDist = Math.sqrt(toBlock.x * toBlock.x + toBlock.z * toBlock.z);
                /*? if >=26.1 {*//*
                float breakYaw = (float) (Mth.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
                *//*?} else {*/
                float breakYaw = (float) (MathHelper.atan2(toBlock.z, toBlock.x) * (180.0 / Math.PI)) - 90.0f;
                /*?}*/
                /*? if >=26.1 {*//*
                float breakPitch = (float) -(Mth.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
                *//*?} else {*/
                float breakPitch = (float) -(MathHelper.atan2(toBlock.y, horizDist) * (180.0 / Math.PI));
                /*?}*/
                /*? if >=26.1 {*//*
                player.setYRot(breakYaw);
                *//*?} else {*/
                player.setYaw(breakYaw);
                /*?}*/
                /*? if >=26.1 {*//*
                player.setXRot(Mth.clamp(breakPitch, -90.0f, 90.0f));
                *//*?} else {*/
                player.setPitch(MathHelper.clamp(breakPitch, -90.0f, 90.0f));
                /*?}*/

                /*? if >=26.1 {*//*
                mc.gameMode.continueDestroyBlock(shulkerPlacePos, Direction.UP);
                *//*?} else {*/
                mc.interactionManager.updateBlockBreakingProgress(shulkerPlacePos, Direction.UP);
                /*?}*/
                /*? if >=26.1 {*//*
                player.swing(InteractionHand.MAIN_HAND);
                *//*?} else {*/
                player.swingHand(Hand.MAIN_HAND);
                /*?}*/
            }

            // Phase 8: Wait for item entity pickup
            case 8 -> {
                if (shulkerUnloadTicks >= SHULKER_PICKUP_DELAY) {
                    // Verify inventory has a shulker (i.e. the item was
                    // actually picked up).  If inventory is full the
                    // entity may still be on the ground — wait longer.
                    boolean pickedUp = false;
                    /*? if >=26.1 {*//*
                    Inventory inv = player.getInventory();
                    *//*?} else {*/
                    PlayerInventory inv = player.getInventory();
                    /*?}*/
                    for (int i = 0; i < 36; i++) {
                        /*? if >=26.1 {*//*
                        if (isShulkerBox(inv.getItem(i))) {
                        *//*?} else {*/
                        if (isShulkerBox(inv.getStack(i))) {
                        /*?}*/
                            pickedUp = true;
                            break;
                        }
                    }
                    if (!pickedUp && shulkerUnloadTicks < SHULKER_PICKUP_DELAY + 40) {
                        // Extended wait — inventory may be full.
                        // Keep waiting for space to free up or item to despawn.
                        return;
                    }
                    if (!pickedUp && statusMessages) {
                        ChatHelper.info("§c⚠ Broken shulker may not have been picked up!");
                    }
                    // Finish this cycle — only process ONE shulker per
                    // unload cycle to prevent inventory overflow and
                    // accidental shulker drops.
                    finishShulkerUnloading(mc);
                }
            }
        }
    }

    /**
     * Finishes the shulker unloading process and transitions to the
     * appropriate next state (walk back to build zone or resume building).
     */
    /*? if >=26.1 {*//*
    private void finishShulkerUnloading(Minecraft mc) {
    *//*?} else {*/
    private void finishShulkerUnloading(MinecraftClient mc) {
    /*?}*/
        shulkerUnloadPhase = 0;
        shulkerPlacePos = null;
        shulkerHotbarSlot = -1;
        shulkerTotalTicks = 0;
        shulkerUnloadFailures = 0;
        platformBuildAttempts = 0;
        platformBlockPos = null;
        shulkerOpenRetries = 0;

        if (statusMessages) {
            if (shulkerNoSpaceSkipped) {
                ChatHelper.info("§eNo shulker space — walking to supply chest.");
            } else {
                ChatHelper.info("§aShulker unloading complete. Walking back to build.");
            }
        }

        // If shulker unloading was skipped due to no placement space
        // (e.g. on a 1-block scaffold pillar), fall through to normal
        // supply walk instead of looping back to BUILDING which would
        // re-detect the shulkers and loop forever.
        if (shulkerNoSpaceSkipped) {
            // shulkerNoSpaceSkipped stays true — startRestockRun will
            // see it and skip the shulker-in-inventory shortcut.
            /*? if >=26.1 {*//*
            startRestockRun(mc.player, mc.level);
            *//*?} else {*/
            startRestockRun(mc.player, mc.world);
            /*?}*/
            return;
        }

        if (lastBuildPos != null) {
            double returnDy = Math.abs(lastBuildPos.getY()
                    /*? if >=26.1 {*//*
                    - mc.player.blockPosition().getY());
                    *//*?} else {*/
                    - mc.player.getBlockPos().getY());
                    /*?}*/
            int radius = (int) Math.ceil(range);
            if (returnDy > 8) {
                walkToZoneWithPlacement(mc.player, lastBuildPos, radius);
            } else {
                PathWalker.walkToNearby(lastBuildPos, radius);
            }
            autoState = AutoState.WALKING_BACK;
        } else {
            autoState = AutoState.BUILDING;
            noProgressTicks = 0;
        }
    }

    /*? if >=26.1 {*//*
    private void tickIdle(Minecraft mc) {
    *//*?} else {*/
    private void tickIdle(MinecraftClient mc) {
    /*?}*/
        idleScanCooldown--;
        if (idleScanCooldown > 0) return;
        idleScanCooldown = IDLE_SCAN_INTERVAL;

        // If we went idle because of missing items, check whether the
        // player has obtained them (manually or via a newly added chest).
        if (!lastMissingItems.isEmpty()) {
            // Supply chest was added since we went idle — go restock
            if (MoarMod.getChestManager().supplyChestCount() > 0) {
                if (statusMessages) {
                    ChatHelper.info("§aSupply chest available — resuming build.");
                }
                // Reset unreachable set — the player may have moved
                // since the last failed attempt.
                unreachableChests.clear();
                autoState = AutoState.BUILDING;
                noProgressTicks = 0;
                return;
            }
            // Check if the player picked up any of the missing items
            Map<Item, Integer> inv = PlacementEngine.getInventoryContentsCached();
            boolean hasAny = lastMissingItems.stream().anyMatch(i -> inv.getOrDefault(i, 0) > 0);
            if (hasAny) {
                if (statusMessages) {
                    ChatHelper.info("§aMaterials detected — resuming build.");
                }
                lastMissingItems.clear();
                autoState = AutoState.BUILDING;
                noProgressTicks = 0;
                return;
            }
            return; // still missing, stay idle
        }

        // Phase transitions: structural → redstone → liquid
        /*? if >=26.1 {*//*
        if (!redstonePass && !liquidPass && !hasRemainingSolids(mc.level)
        *//*?} else {*/
        if (!redstonePass && !liquidPass && !hasRemainingSolids(mc.world)
        /*?}*/
                /*? if >=26.1 {*//*
                && hasRemainingRedstone(mc.level)) {
                *//*?} else {*/
                && hasRemainingRedstone(mc.world)) {
                /*?}*/
            redstonePass = true;
            noProgressTicks = 0;
            stuckCycles = 0;
            failedZones.clear();
            if (statusMessages) {
                ChatHelper.info("§bResuming — placing redstone components...");
            }
            autoState = AutoState.BUILDING;
            return;
        }
        /*? if >=26.1 {*//*
        if (!liquidPass && !hasRemainingSolids(mc.level) && !hasRemainingRedstone(mc.level)
        *//*?} else {*/
        if (!liquidPass && !hasRemainingSolids(mc.world) && !hasRemainingRedstone(mc.world)
        /*?}*/
                /*? if >=26.1 {*//*
                && hasRemainingLiquids(mc.level)) {
                *//*?} else {*/
                && hasRemainingLiquids(mc.world)) {
                /*?}*/
            liquidPass = true;
            redstonePass = false;
            noProgressTicks = 0;
            stuckCycles = 0;
            failedZones.clear();
            PathWalker.stop();
            if (statusMessages) {
                ChatHelper.info("§bResuming — placing remaining liquids...");
            }
            autoState = AutoState.BUILDING;
            return;
        }
        /*? if >=26.1 {*//*
        BlockPos nextZone = findNextBuildZone(mc.player, mc.level);
        *//*?} else {*/
        BlockPos nextZone = findNextBuildZone(mc.player, mc.world);
        /*?}*/
        if (nextZone != null) {
            failedZones.clear();
            walkFailCount = 0;
            lastWalkTargetZone = null;
            walkAttemptCooldown = 0;
            /*? if >=26.1 {*//*
            lastBuildPos = mc.player.blockPosition();
            *//*?} else {*/
            lastBuildPos = mc.player.getBlockPos();
            /*?}*/
            /*? if >=26.1 {*//*
            int playerY = mc.player.blockPosition().getY();
            *//*?} else {*/
            int playerY = mc.player.getBlockPos().getY();
            /*?}*/
            int dy = nextZone.getY() - playerY;
            // If the build zone is significantly above or below us,
            // use the waypoint-based placement walk to get there.
            if (Math.abs(dy) > (int) Math.ceil(range) + 2) {
                walkToZoneWithPlacement(mc.player, nextZone, (int) Math.ceil(range));
            } else {
                /*? if >=26.1 {*//*
                BlockPos standPos = findStandingPosition(nextZone, mc.level, mc.player);
                *//*?} else {*/
                BlockPos standPos = findStandingPosition(nextZone, mc.world, mc.player);
                /*?}*/
                if (standPos != null) {
                    PathWalker.walkTo(standPos);
                } else {
                    PathWalker.walkToNearby(nextZone, (int) Math.ceil(range));
                }
            }
            autoState = AutoState.WALKING_TO_BUILD;
            return;
        }

        // No zones in loaded chunks — check unloaded regions
        /*? if >=26.1 {*//*
        BlockPos unloadedZone = findUnloadedBuildZone(mc.player, mc.level);
        *//*?} else {*/
        BlockPos unloadedZone = findUnloadedBuildZone(mc.player, mc.world);
        /*?}*/
        if (unloadedZone != null) {
            failedZones.clear();
            LOGGER.debug("Resuming — walking to unloaded region {} {} {}",
                    unloadedZone.getX(), unloadedZone.getY(), unloadedZone.getZ());
            PathWalker.walkToNearby(unloadedZone, (int) Math.ceil(range));
            autoState = AutoState.WALKING_TO_BUILD;
        }
    }

    /**
     * Returns true if the world block at this position matches
     * the schematic's expected block (i.e. it's correctly placed).
     */
    /*? if >=26.1 {*//*
    private boolean isCorrectSchematicBlock(BlockPos worldPos, Level world) {
    *//*?} else {*/
    private boolean isCorrectSchematicBlock(BlockPos worldPos, World world) {
    /*?}*/
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) return false;
        BlockState expected = schematic.getBlockState(sx, sy, sz);
        if (expected.isAir()) return false;
        return isEffectivelyPlaced(world.getBlockState(worldPos), expected);
    }

    /**
     * Finds the lowest unbuilt block above the player's reach.
     * Among same-Y candidates, prefers nearest by horizontal distance.
     */
    /*? if >=26.1 {*//*
    private BlockPos findHighBuildZone(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findHighBuildZone(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null) return null;

        /*? if >=26.1 {*//*
        int playerY = player.blockPosition().getY();
        *//*?} else {*/
        int playerY = player.getBlockPos().getY();
        /*?}*/
        BlockPos best = null;
        int bestY = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;

        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                int wy = anchor.getY() + region.originY + y;
                // Only look for blocks that are above the player's reach
                if (wy <= playerY + 2) continue;
                // Skip Y-levels higher than the best we've found so far
                // (we want the LOWEST unbuilt layer above the player)
                if (wy > bestY) continue;

                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target)) continue;
                        if (isAutoCreatedPart(target)) continue;

                        // Only target solid blocks.  Liquid source blocks
                        // are placed via buckets from within reach and don't
                        // require vertical pathing.
                        if (isLiquidSource(target)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wz = anchor.getZ() + region.originZ + z;

                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/

                        BlockPos worldPos = new BlockPos(wx, wy, wz);

                        if (isEffectivelyPlaced(world.getBlockState(worldPos), target)) continue;
                        // NOTE: do NOT filter by hasAdjacentSolid here — these
                        // are blocks above the player, and Baritone will place
                        // support blocks as needed to path there.

                        // Prefer lowest Y; among same Y, prefer closest
                        /*? if >=26.1 {*//*
                        double dist = playerPos.distanceToSqr(Vec3.atCenterOf(worldPos));
                        *//*?} else {*/
                        double dist = playerPos.squaredDistanceTo(Vec3d.ofCenter(worldPos));
                        /*?}*/
                        if (wy < bestY || (wy == bestY && dist < bestDist)) {
                            bestY = wy;
                            bestDist = dist;
                            best = worldPos;
                        }
                    }
                }
            }
        }

        // Return the lowest unbuilt block above the player.
        return best;
    }

    // INVENTORY & RESTOCK

    /*? if >=26.1 {*//*
    private boolean shouldRestock(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean shouldRestock(ClientPlayerEntity player, World world) {
    /*?}*/
        if (MoarMod.getChestManager().supplyChestCount() == 0) return false;

        Map<Item, Integer> needed = getNeededItemsNearby(player, world, 200);
        Map<Item, Integer> inventory = PlacementEngine.getInventoryContentsCached();

        for (var entry : needed.entrySet()) {
            int have = inventory.getOrDefault(entry.getKey(), 0);
            if (have < RESTOCK_THRESHOLD && entry.getValue() > have) {
                return true;
            }
        }
        return false;
    }

    // DUMP — deposit mined items into a dump chest when inventory is full

    /*? if >=26.1 {*//*
    private void startDumpRun(Minecraft mc) {
    *//*?} else {*/
    private void startDumpRun(MinecraftClient mc) {
    /*?}*/
        /*? if >=26.1 {*//*
        BlockPos playerPos = mc.player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = mc.player.getBlockPos();
        /*?}*/
        BlockPos nearest = MoarMod.getChestManager().findNearestDumpChest(playerPos);
        if (nearest == null) {
            // No reachable dump chests — continue clearing
            return;
        }
        dumpTarget = nearest;
        /*? if >=26.1 {*//*
        preDumpClearPos = playerPos.immutable();
        *//*?} else {*/
        preDumpClearPos = playerPos.toImmutable();
        /*?}*/
        dumpWaitTicks = 0;
        dumpSyncDelay = 0;

        double dy = Math.abs(nearest.getY() - playerPos.getY());
        double dist = Math.sqrt(
                /*? if >=26.1 {*//*
                playerPos.distSqr(nearest));
                *//*?} else {*/
                playerPos.getSquaredDistance(nearest));
                /*?}*/
        if (dy > 8) {
            walkToZoneWithPlacement(mc.player, nearest, 2);
        } else if (dist > 48) {
            List<BlockPos> legs = computeLinearWaypoints(playerPos, nearest, 48);
            PathWalker.walkToViaWaypoints(legs, 2);
        } else {
            PathWalker.walkToNearby(nearest, 2);
        }
        autoState = AutoState.WALKING_TO_DUMP;

        if (statusMessages) {
            ChatHelper.info("§7Inventory nearly full — dumping items at §e"
                    + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ());
        }
    }

    /*? if >=26.1 {*//*
    private void startRestockRun(LocalPlayer player, Level world) {
    *//*?} else {*/
    private void startRestockRun(ClientPlayerEntity player, World world) {
    /*?}*/
        Map<Item, Integer> needed = getNeededItemsNearby(player, world, 500);
        Map<Item, Integer> inventory = PlacementEngine.getInventoryContentsCached();

        neededItems = new LinkedHashSet<>();
        for (var entry : needed.entrySet()) {
            int have = inventory.getOrDefault(entry.getKey(), 0);
            if (have < entry.getValue()) {
                /*? if >=26.1 {*//*
                neededItems.add(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString());
                *//*?} else {*/
                neededItems.add(Registries.ITEM.getId(entry.getKey()).toString());
                /*?}*/
            }
        }

        // Always include items that tryPlaceNextBlock couldn't find —
        // the 500-block scan uses schematic order (Y/Z/X) which may miss
        // items that are nearby in world space but far in scan order.
        for (Item missing : lastMissingItems) {
            /*? if >=26.1 {*//*
            String id = BuiltInRegistries.ITEM.getKey(missing).toString();
            *//*?} else {*/
            String id = Registries.ITEM.getId(missing).toString();
            /*?}*/
            neededItems.add(id);
        }

        if (neededItems.isEmpty()) {
            autoState = AutoState.BUILDING;
            return;
        }

        // Check if shulkers in inventory already have what we need
        // Before walking to a supply chest, see if the player is already
        // carrying shulker boxes with the required materials.  If so,
        // skip the supply walk entirely and go straight to unloading.
        // BUT: if we already tried and couldn't place a shulker (e.g.
        // standing on a 1-block pillar with no space), don't retry —
        // walk to a supply chest instead where there's flat ground.
        if (!shulkerNoSpaceSkipped && findShulkerWithNeededItems(player) >= 0) {
            /*? if >=26.1 {*//*
            lastBuildPos = player.blockPosition();
            *//*?} else {*/
            lastBuildPos = player.getBlockPos();
            /*?}*/
            if (statusMessages) {
                ChatHelper.info("§aNeeded items found in inventory shulkers — unloading.");
            }
            shulkerUnloadPhase = 0;
            shulkerUnloadTicks = 0;
            shulkerTotalTicks = 0;
            shulkerUnloadFailures = 0;
            autoState = AutoState.UNLOADING_SHULKER;
            return;
        }

        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        BlockPos nearest = MoarMod.getChestManager().findBestChest(
                /*? if >=26.1 {*//*
                player.blockPosition(), neededItems, unreachableChests);
                *//*?} else {*/
                player.getBlockPos(), neededItems, unreachableChests);
                /*?}*/
        if (nearest == null) {
            if (statusMessages) {
                if (!unreachableChests.isEmpty()) {
                    ChatHelper.info("§cAll supply chests unreachable ("
                            + unreachableChests.size() + " skipped). Going idle."
                            + " Move closer or add a reachable chest.");
                } else {
                    ChatHelper.info("§eNo supply chests configured. Use §f/printer supply add");
                }
            }
            autoState = AutoState.IDLE;
            return;
        }

        supplyTarget = nearest;
        /*? if >=26.1 {*//*
        lastBuildPos = player.blockPosition();
        *//*?} else {*/
        lastBuildPos = player.getBlockPos();
        /*?}*/
        triedWaypointRestock = false; // fresh attempt for new target
        triedLinearRestock = false;
        triedPlacementRestock = false;
        supplyDescentPhase = 0;
        supplyDescentTarget = null;
        // Tell PathWalker what items are reserved for the build so
        // Baritone only uses surplus blocks as scaffold material.
        PathWalker.setReservedItems(needed);

        // First attempt: simple walk WITHOUT placement.  Only use
        // placement as a retry strategy — scaffolding toward a chest
        // wastes blocks and leaves a mess when the chest is reachable
        // by normal walking (same elevation, no obstacles).
        /*? if >=26.1 {*//*
        double dy = Math.abs(nearest.getY() - player.blockPosition().getY());
        *//*?} else {*/
        double dy = Math.abs(nearest.getY() - player.getBlockPos().getY());
        /*?}*/
        double dist = Math.sqrt(
                /*? if >=26.1 {*//*
                player.blockPosition().distSqr(nearest));
                *//*?} else {*/
                player.getBlockPos().getSquaredDistance(nearest));
                /*?}*/
        if (dy > 8) {
            // Significant elevation change — use two-phase waypoints
            // with placement from the start.
            walkToZoneWithPlacement(player, nearest, 2);
        } else if (dist > 48) {
            // Long horizontal distance — break into legs but no
            // placement (Baritone walks around obstacles normally).
            List<BlockPos> legs = computeLinearWaypoints(
                    /*? if >=26.1 {*//*
                    player.blockPosition(), nearest, 48);
                    *//*?} else {*/
                    player.getBlockPos(), nearest, 48);
                    /*?}*/
            PathWalker.walkToViaWaypoints(legs, 2);
        } else {
            // Short/moderate distance, same elevation — simple walk.
            PathWalker.walkToNearby(nearest, 2);
        }
        autoState = AutoState.WALKING_TO_SUPPLY;

        if (statusMessages) {
            ChatHelper.info("§7Restocking — walking to supply §e"
                    + nearest.getX() + " " + nearest.getY() + " " + nearest.getZ()
                    + "\n§7Looking for: " + formatNeededItemIds(neededItems));
        }
    }

    /**
     * Builds greedy nearest-neighbour waypoints from player to supply
     * chest using known database positions (other chests + scaffold).
     * Last entry is always the chest itself.
     */
    private List<BlockPos> computeSupplyWaypoints(BlockPos from, BlockPos to) {
        // 1. Collect all known positions from the database
        List<BlockPos> candidates = new ArrayList<>();

        // Other registered chests (not the target itself)
        for (BlockPos chest : MoarMod.getChestManager().getSupplyPositions()) {
            if (!chest.equals(to) && !chest.equals(from)) {
                candidates.add(chest);
            }
        }

        // Scaffold blocks
        for (BlockPos scaffold : PrinterDatabase.getScaffoldEntries().keySet()) {
            if (!scaffold.equals(to) && !scaffold.equals(from)) {
                candidates.add(scaffold);
            }
        }

        if (candidates.isEmpty()) {
            // Nothing in the database to use as waypoints
            /*? if >=26.1 {*//*
            return new ArrayList<>(List.of(to.immutable()));
            *//*?} else {*/
            return new ArrayList<>(List.of(to.toImmutable()));
            /*?}*/
        }

        // 2. Build a greedy nearest-neighbour chain
        //  Starting from the player, repeatedly pick the candidate
        //  that is (a) closest to the current position and (b) makes
        //  forward progress toward the target (closer to target than
        //  the current position is).
        List<BlockPos> waypoints = new ArrayList<>();
        Set<BlockPos> used = new HashSet<>();
        BlockPos current = from;
        /*? if >=26.1 {*//*
        double currentDistToTarget = Math.sqrt(current.distSqr(to));
        *//*?} else {*/
        double currentDistToTarget = Math.sqrt(current.getSquaredDistance(to));
        /*?}*/

        // Minimum distance between waypoints — prevents picking
        // clusters of nearby scaffold blocks as separate legs.
        final double MIN_LEG_DIST_SQ = 8.0 * 8.0;

        while (!candidates.isEmpty()) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;

            for (BlockPos cand : candidates) {
                if (used.contains(cand)) continue;

                /*? if >=26.1 {*//*
                double distFromCurrent = Math.sqrt(current.distSqr(cand));
                *//*?} else {*/
                double distFromCurrent = Math.sqrt(current.getSquaredDistance(cand));
                /*?}*/
                /*? if >=26.1 {*//*
                double distToTarget = Math.sqrt(cand.distSqr(to));
                *//*?} else {*/
                double distToTarget = Math.sqrt(cand.getSquaredDistance(to));
                /*?}*/

                // Must make forward progress — candidate should be
                // closer to the target than we currently are
                if (distToTarget >= currentDistToTarget) continue;

                // Must be a meaningful hop (not too close to current)
                /*? if >=26.1 {*//*
                if (current.distSqr(cand) < MIN_LEG_DIST_SQ) continue;
                *//*?} else {*/
                if (current.getSquaredDistance(cand) < MIN_LEG_DIST_SQ) continue;
                /*?}*/

                // Prefer the candidate closest to our current position
                // so each leg is short enough for Baritone to handle
                if (distFromCurrent < bestDist) {
                    bestDist = distFromCurrent;
                    best = cand;
                }
            }

            if (best == null) break; // no more useful candidates

            waypoints.add(best);
            used.add(best);
            current = best;
            /*? if >=26.1 {*//*
            currentDistToTarget = Math.sqrt(current.distSqr(to));
            *//*?} else {*/
            currentDistToTarget = Math.sqrt(current.getSquaredDistance(to));
            /*?}*/

            // Stop adding waypoints once we're close to the target —
            // let the final leg handle the last stretch directly
            if (currentDistToTarget < 15.0) break;
        }

        // 3. Always end at the chest
        /*? if >=26.1 {*//*
        waypoints.add(to.immutable());
        *//*?} else {*/
        waypoints.add(to.toImmutable());
        /*?}*/

        return waypoints;
    }

    /** Splits a long path into fixed-length legs for Baritone. */
    private List<BlockPos> computeLinearWaypoints(BlockPos from, BlockPos to, int legLength) {
        List<BlockPos> waypoints = new ArrayList<>();
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist <= legLength) {
            /*? if >=26.1 {*//*
            waypoints.add(to.immutable());
            *//*?} else {*/
            waypoints.add(to.toImmutable());
            /*?}*/
            return waypoints;
        }

        int legs = (int) Math.ceil(dist / legLength);
        for (int i = 1; i <= legs; i++) {
            double t = (double) i / legs;
            int wx = from.getX() + (int) (dx * t);
            int wy = from.getY() + (int) (dy * t);
            int wz = from.getZ() + (int) (dz * t);
            waypoints.add(new BlockPos(wx, wy, wz));
        }
        // Ensure the last waypoint is exactly the target
        /*? if >=26.1 {*//*
        waypoints.set(waypoints.size() - 1, to.immutable());
        *//*?} else {*/
        waypoints.set(waypoints.size() - 1, to.toImmutable());
        /*?}*/
        return waypoints;
    }

    /**
     * Walks to a build zone with placement enabled.  For vertical paths,
     * uses horizontal-then-vertical phasing.  For horizontal, uses
     * linear interpolation legs.
     */
    /*? if >=26.1 {*//*
    private void walkToZoneWithPlacement(LocalPlayer player,
    *//*?} else {*/
    private void walkToZoneWithPlacement(ClientPlayerEntity player,
    /*?}*/
                                         BlockPos target, int radius) {
        // Tell PathWalker what items are reserved for the build so
        // Baritone only uses surplus blocks as scaffold material.
        /*? if >=26.1 {*//*
        Level w = Minecraft.getInstance().level;
        *//*?} else {*/
        World w = MinecraftClient.getInstance().world;
        /*?}*/
        if (w != null) {
            PathWalker.setReservedItems(getNeededItemsNearby(player, w, 200));
        }

        /*? if >=26.1 {*//*
        BlockPos from = player.blockPosition();
        *//*?} else {*/
        BlockPos from = player.getBlockPos();
        /*?}*/
        double dx = target.getX() - from.getX();
        double dy = target.getY() - from.getY();
        double dz = target.getZ() - from.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        double vertDist = Math.abs(dy);
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        List<BlockPos> legs = new ArrayList<>();

        if (dy > 2) {
            // ASCENDING: horizontal first, then pillar up.
            // Any ascent > 2 blocks triggers this path — even moderate
            // climbs (3–8 blocks) need explicit vertical legs to ensure
            // Baritone actually pillars up.  Without this, GoalNear's
            // 3D radius causes Baritone to consider the player
            // "arrived" while still at ground level.
            if (horizDist > 4) {
                // Break the horizontal phase into legs if it's long
                BlockPos base = new BlockPos(target.getX(), from.getY(), target.getZ());
                if (horizDist > 48) {
                    List<BlockPos> horizLegs = computeLinearWaypoints(
                            from, base, 48);
                    legs.addAll(horizLegs);
                } else {
                    legs.add(base);
                }
            }

            // Vertical phase — climb in 8-block steps
            int absY = (int) vertDist;
            int vertLeg = 8;
            int currentY = from.getY();
            int fullLegs = absY / vertLeg;
            for (int i = 0; i < fullLegs; i++) {
                currentY += vertLeg;
                legs.add(new BlockPos(target.getX(), currentY, target.getZ()));
            }

            /*? if >=26.1 {*//*
            legs.add(target.immutable());
            *//*?} else {*/
            legs.add(target.toImmutable());
            /*?}*/

            // Use per-waypoint radii: loose for intermediate legs,
            // tight (1 block) for the final target to force Baritone
            // to actually reach the target's Y level.
            List<Integer> radii = new ArrayList<>();
            for (int i = 0; i < legs.size() - 1; i++) {
                radii.add(radius);
            }
            radii.add(Math.min(radius, 1)); // tight on final target
            PathWalker.walkToViaWaypointsWithRadiiAndPlacement(legs, radii, player);
            return;

        } else if (vertDist > 8 && dy < 0) {
            // DESCENDING: 3-phase approach
            // Phase 1: walk horizontally at CURRENT elevation to the
            //   target's XZ column.  The player is on the scaffold /
            //   build platform which provides walkable terrain.
            // Phase 2: GoalYLevel descent — Baritone finds its own
            //   way down (staircase, existing terrain).  No XZ
            //   constraint means the staircase can drift freely.
            // Phase 3: short flat walk from wherever the descent
            //   ended to the actual chest position.
            //
            // Phases 2 & 3 are handled by tickWalkingToSupply when
            // each prior phase completes.
            supplyDescentPhase = 1;
            /*? if >=26.1 {*//*
            supplyDescentTarget = target.immutable();
            *//*?} else {*/
            supplyDescentTarget = target.toImmutable();
            /*?}*/

            if (horizDist > 4) {
                BlockPos aboveTarget = new BlockPos(
                        target.getX(), from.getY(), target.getZ());
                if (horizDist > 48) {
                    List<BlockPos> horizLegs = computeLinearWaypoints(
                            from, aboveTarget, 48);
                    legs.addAll(horizLegs);
                } else {
                    legs.add(aboveTarget);
                }
            } else {
                // Already above the target — skip to phase 2
                supplyDescentPhase = 2;
                PathWalker.walkToYLevelWithPlacement(
                        target.getY(), player);
                return;
            }

        } else {
            // HORIZONTAL / MODERATE: linear interpolation
            int legLength;
            if (totalDist > 80) {
                legLength = 32;
            } else {
                legLength = 48;
            }
            legs = computeLinearWaypoints(from, target, legLength);
        }

        if (legs.size() > 1) {
            PathWalker.walkToViaWaypointsWithPlacement(legs, radius, player);
        } else {
            // Short enough for a direct walk
            PathWalker.walkToWithPlacement(target, radius, player);
        }
    }

    /*? if >=26.1 {*//*
    private Map<Item, Integer> getNeededItemsNearby(LocalPlayer player, Level world, int limit) {
    *//*?} else {*/
    private Map<Item, Integer> getNeededItemsNearby(ClientPlayerEntity player, World world, int limit) {
    /*?}*/
        Map<Item, Integer> needed = new HashMap<>();
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        /*?}*/

        // Collect all unbuilt positions with their distances, then sort
        // by proximity so we scan nearest blocks first — critical for
        // large builds where schematic-order scanning may pick up blocks
        // hundreds of blocks away.
        record Candidate(int wx, int wy, int wz, double distSq, BlockState target) {}
        List<Candidate> candidates = new ArrayList<>();

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (isAutoCreatedPart(target)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) continue;

                        // Three-pass deferral — only count items for the current pass
                        boolean isLiquid = isLiquidSource(target);
                        boolean isRedstone = !isLiquid && BlockDependency.isRedstoneComponent(target);
                        if (liquidPass) {
                            if (!isLiquid) continue;
                        } else if (redstonePass) {
                            if (!isRedstone) continue;
                        } else {
                            if (isLiquid || isRedstone) continue;
                        }

                        /*? if >=26.1 {*//*
                        double distSq = playerPos.distSqr(new BlockPos(wx, wy, wz));
                        *//*?} else {*/
                        double distSq = playerPos.getSquaredDistance(wx, wy, wz);
                        /*?}*/
                        candidates.add(new Candidate(wx, wy, wz, distSq, target));
                    }
                }
            }
        }

        // Sort by distance to player — nearest first
        candidates.sort(Comparator.comparingDouble(c -> c.distSq));

        int count = 0;
        for (Candidate c : candidates) {
            if (count >= limit) break;

            if (isLiquidSource(c.target)) {
                Item bucket = getLiquidBucketItem(c.target);
                if (bucket != null) {
                    needed.merge(bucket, 1, Integer::sum);
                    count++;
                }
                continue;
            }

            Item item = c.target.getBlock().asItem();
            if (item != Items.AIR) {
                needed.merge(item, 1, Integer::sum);
                count++;
            }
        }
        return needed;
    }

    // CHEST INTERACTION

    /*? if >=26.1 {*//*
    private boolean tryOpenChest(Minecraft mc, BlockPos chestPos) {
    *//*?} else {*/
    private boolean tryOpenChest(MinecraftClient mc, BlockPos chestPos) {
    /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.gameMode == null) return false;
        *//*?} else {*/
        if (mc.player == null || mc.interactionManager == null) return false;
        /*?}*/

        /*? if >=26.1 {*//*
        double dist = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(chestPos));
        *//*?} else {*/
        double dist = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(chestPos));
        /*?}*/
        if (dist > 5.0 * 5.0) return false;

        // Verify the block is actually a chest/container — if the chest
        // was broken, moved, or the position is wrong, don't interact.
        /*? if >=26.1 {*//*
        BlockState chestState = mc.level != null ? mc.level.getBlockState(chestPos) : null;
        *//*?} else {*/
        BlockState chestState = mc.world != null ? mc.world.getBlockState(chestPos) : null;
        /*?}*/
        if (chestState == null || !(chestState.getBlock() instanceof ChestBlock
                || chestState.getBlock() instanceof BarrelBlock
                || chestState.getBlock() instanceof ShulkerBoxBlock)) {
            // Not a container — try scanning nearby for a chest
            /*? if >=26.1 {*//*
            BlockPos alt = findNearbyChest(mc.level, chestPos, 3);
            *//*?} else {*/
            BlockPos alt = findNearbyChest(mc.world, chestPos, 3);
            /*?}*/
            if (alt != null) {
                chestPos = alt;
            } else {
                return false;
            }
        }

        // Rotate toward the container so the server accepts the interaction
        /*? if >=26.1 {*//*
        Vec3 eyePos = mc.player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = mc.player.getEyePos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 chestCenter = Vec3.atCenterOf(chestPos);
        *//*?} else {*/
        Vec3d chestCenter = Vec3d.ofCenter(chestPos);
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 toChest = chestCenter.subtract(eyePos);
        *//*?} else {*/
        Vec3d toChest = chestCenter.subtract(eyePos);
        /*?}*/
        double horizDist = Math.sqrt(toChest.x * toChest.x + toChest.z * toChest.z);
        /*? if >=26.1 {*//*
        float chestYaw = (float) (Mth.atan2(toChest.z, toChest.x)
        *//*?} else {*/
        float chestYaw = (float) (MathHelper.atan2(toChest.z, toChest.x)
        /*?}*/
                * (180.0 / Math.PI)) - 90.0f;
        /*? if >=26.1 {*//*
        float chestPitch = (float) -(Mth.atan2(toChest.y, horizDist)
        *//*?} else {*/
        float chestPitch = (float) -(MathHelper.atan2(toChest.y, horizDist)
        /*?}*/
                * (180.0 / Math.PI));
        PlacementEngine.sendLookPacket(mc.player, chestYaw,
                /*? if >=26.1 {*//*
                Mth.clamp(chestPitch, -90.0f, 90.0f));
                *//*?} else {*/
                MathHelper.clamp(chestPitch, -90.0f, 90.0f));
                /*?}*/

        // Release sneak overrides before interacting — if the player is
        // sneaking, interactBlock bypasses block use (chest open) and
        // tries to place the held item instead.
        Runnable restoreSneak = PlacementEngine.releaseForInteraction(mc.player);

        /*? if >=26.1 {*//*
        Direction hitFace = Direction.getApproximateNearest(
        *//*?} else {*/
        Direction hitFace = Direction.getFacing(
        /*?}*/
                (float) -toChest.x, (float) -toChest.y, (float) -toChest.z);
        BlockHitResult hit = new BlockHitResult(
                chestCenter, hitFace, chestPos, false);
        /*? if >=26.1 {*//*
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        *//*?} else {*/
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        /*?}*/

        // Restore sneak overrides if they were active
        restoreSneak.run();

        /*? if >=26.1 {*//*
        return result.consumesAction();
        *//*?} else {*/
        return result.isAccepted();
        /*?}*/
    }

    /** Finds nearby chest/barrel/shulker as fallback when exact pos is stale. */
    /*? if >=26.1 {*//*
    private BlockPos findNearbyChest(Level world, BlockPos center, int radius) {
    *//*?} else {*/
    private BlockPos findNearbyChest(World world, BlockPos center, int radius) {
    /*?}*/
        if (world == null) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos pos = center.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos pos = center.add(dx, dy, dz);
                    /*?}*/
                    Block block = world.getBlockState(pos).getBlock();
                    if (block instanceof ChestBlock
                            || block instanceof BarrelBlock
                            || block instanceof ShulkerBoxBlock) {
                        /*? if >=26.1 {*//*
                        double d = center.distSqr(pos);
                        *//*?} else {*/
                        double d = center.getSquaredDistance(pos);
                        /*?}*/
                        if (d < bestDist) {
                            bestDist = d;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    /*? if >=26.1 {*//*
    private void takeNeededItems(Minecraft mc, LocalPlayer player,
    *//*?} else {*/
    private void takeNeededItems(MinecraftClient mc, ClientPlayerEntity player,
    /*?}*/
                                 /*? if >=26.1 {*//*
                                 ChestMenu handler) {
                                 *//*?} else {*/
                                 GenericContainerScreenHandler handler) {
                                 /*?}*/
        if (neededItems == null || neededItems.isEmpty()) return;

        /*? if >=26.1 {*//*
        int chestSlots = handler.getRowCount() * 9;
        *//*?} else {*/
        int chestSlots = handler.getRows() * 9;
        /*?}*/

        // Pass 0: return unneeded shulkers to the chest
        // Shulker boxes from previous unload cycles may still be in the
        // player's inventory.  Deposit any that no longer contain needed
        // items so they don't waste inventory slots.  In the container
        // screen, player inventory slots start at chestSlots.
        int playerSlotStart = chestSlots;      // main inv (slots 9-35)
        int playerSlotEnd = chestSlots + 36;   // through hotbar (slots 0-8)
        for (int slot = playerSlotStart; slot < playerSlotEnd; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) continue;
            if (!isShulkerBox(stack)) continue;
            // Keep shulkers that still have items we need
            if (shulkerContainsNeeded(stack, neededItems)) continue;
            // Deposit this shulker back into the chest
            /*? if >=26.1 {*//*
            mc.gameMode.handleContainerInput(
            *//*?} else {*/
            mc.interactionManager.clickSlot(
            /*?}*/
                    /*? if >=26.1 {*//*
                    handler.containerId, slot, 0,
                    *//*?} else {*/
                    handler.syncId, slot, 0,
                    /*?}*/
                    /*? if >=26.1 {*//*
                    ContainerInput.QUICK_MOVE, player);
                    *//*?} else {*/
                    SlotActionType.QUICK_MOVE, player);
                    /*?}*/
        }

        // Reserve a slot for the shulker if one is needed from this chest.
        boolean hasNeededShulker = false;
        for (int slot = 0; slot < chestSlots; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (isShulkerBox(stack) && shulkerContainsNeeded(stack, neededItems)) {
                hasNeededShulker = true;
                break;
            }
        }
        int reservedFreeSlots = hasNeededShulker ? 1 : 0;

        // Pass 1: grab all loose (non-shulker) needed items
        for (int slot = 0; slot < chestSlots; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) continue;

            /*? if >=26.1 {*//*
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            *//*?} else {*/
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            /*?}*/
            if (neededItems.contains(itemId) && !isShulkerBox(stack)) {
                boolean wouldStack = false;
                int freeSlots = 0;
                for (int pi = playerSlotStart; pi < playerSlotEnd; pi++) {
                    /*? if >=26.1 {*//*
                    ItemStack invStack = handler.getSlot(pi).getItem();
                    *//*?} else {*/
                    ItemStack invStack = handler.getSlot(pi).getStack();
                    /*?}*/
                    if (invStack.isEmpty()) {
                        freeSlots++;
                        continue;
                    }
                    if (
                            /*? if >=26.1 {*//*
                            ItemStack.isSameItem(invStack, stack)
                            *//*?} else {*/
                            ItemStack.areItemsEqual(invStack, stack)
                            /*?}*/
                            /*? if >=26.1 {*//*
                            && invStack.getCount() < invStack.getMaxStackSize()) {
                            *//*?} else {*/
                            && invStack.getCount() < invStack.getMaxCount()) {
                            /*?}*/
                        wouldStack = true;
                        break;
                    }
                }
                if (!wouldStack && freeSlots <= reservedFreeSlots) {
                    continue;
                }
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                /*?}*/
                        /*? if >=26.1 {*//*
                        handler.containerId, slot, 0,
                        *//*?} else {*/
                        handler.syncId, slot, 0,
                        /*?}*/
                        /*? if >=26.1 {*//*
                        ContainerInput.QUICK_MOVE, player);
                        *//*?} else {*/
                        SlotActionType.QUICK_MOVE, player);
                        /*?}*/
            }
        }

        // Pass 2: grab at most ONE shulker that has needed items
        // Taking only one shulker per chest visit prevents inventory
        // flooding.  The unloading state machine will empty it, then
        // the printer can come back for another shulker if still needed.
        for (int slot = 0; slot < chestSlots; slot++) {
            /*? if >=26.1 {*//*
            ItemStack stack = handler.getSlot(slot).getItem();
            *//*?} else {*/
            ItemStack stack = handler.getSlot(slot).getStack();
            /*?}*/
            if (stack.isEmpty()) continue;

            if (isShulkerBox(stack) && shulkerContainsNeeded(stack, neededItems)) {
                /*? if >=26.1 {*//*
                mc.gameMode.handleContainerInput(
                *//*?} else {*/
                mc.interactionManager.clickSlot(
                /*?}*/
                        /*? if >=26.1 {*//*
                        handler.containerId, slot, 0,
                        *//*?} else {*/
                        handler.syncId, slot, 0,
                        /*?}*/
                        /*? if >=26.1 {*//*
                        ContainerInput.QUICK_MOVE, player);
                        *//*?} else {*/
                        SlotActionType.QUICK_MOVE, player);
                        /*?}*/
                break; // only one shulker per visit
            }
        }
    }

    private boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean shulkerContainsNeeded(ItemStack shulkerStack, Set<String> needSet) {
        /*? if >=26.1 {*//*
        ItemContainerContents cc = shulkerStack.get(DataComponents.CONTAINER);
        *//*?} else {*/
        ContainerComponent cc = shulkerStack.get(DataComponentTypes.CONTAINER);
        /*?}*/
        if (cc == null) return false;

        /*? if >=26.1 {*//*
        for (ItemStack inner : cc.nonEmptyItemCopyStream().toList()) {
        *//*?} else {*/
        for (ItemStack inner : cc.iterateNonEmpty()) {
        /*?}*/
            /*? if >=26.1 {*//*
            String innerId = BuiltInRegistries.ITEM.getKey(inner.getItem()).toString();
            *//*?} else {*/
            String innerId = Registries.ITEM.getId(inner.getItem()).toString();
            /*?}*/
            if (needSet.contains(innerId)) return true;
        }
        return false;
    }

    // NAVIGATION HELPERS

    /*? if >=26.1 {*//*
    private BlockPos findNextBuildZone(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findNextBuildZone(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null) return null;

        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        /*? if >=26.1 {*//*
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        *//*?} else {*/
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        /*?}*/

        // Scan Y-levels bottom-up, skipping failedZones.
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                        if (isAutoCreatedPart(target)) continue;

                        // Three-pass deferral — structural → redstone → liquid
                        boolean isLiquid = isLiquidSource(target);
                        boolean isRedstone = !isLiquid && BlockDependency.isRedstoneComponent(target);
                        if (liquidPass) {
                            if (!isLiquid) continue;
                        } else if (redstonePass) {
                            if (!isRedstone) continue;
                        } else {
                            if (isLiquid || isRedstone) continue;
                        }

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        // Skip positions in unloaded chunks — getBlockState
                        // returns air for unloaded chunks, which would cause
                        // the builder to think every block needs placing.
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/

                        mutablePos.set(wx, wy, wz);

                        if (isEffectivelyPlaced(world.getBlockState(mutablePos), target)) continue;

                        // Prefer blocks at the lowest or highest unbuilt Y-level
                        if (sortMode == SortMode.TOP_DOWN) {
                            if (best != null && wy < best.getY()) continue;
                        } else {
                            if (best != null && wy > best.getY()) continue;
                        }

                        if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, mutablePos)) continue;
                        if (!BlockDependency.isReadyToPlace(world, mutablePos, target)) continue;
                        if (isNearFailedZone(mutablePos)) continue;

                        /*? if >=26.1 {*//*
                        double dist = playerPos.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                        *//*?} else {*/
                        double dist = playerPos.squaredDistanceTo(wx + 0.5, wy + 0.5, wz + 0.5);
                        /*?}*/
                        if (dist < bestDist) {
                            bestDist = dist;
                            /*? if >=26.1 {*//*
                            best = mutablePos.immutable();
                            *//*?} else {*/
                            best = mutablePos.toImmutable();
                            /*?}*/
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Finds the nearest non-air schematic block in unloaded chunks.
     * Walking toward the result loads the chunk for placement.
     */
    /*? if >=26.1 {*//*
    private BlockPos findUnloadedBuildZone(LocalPlayer player, Level world) {
    *//*?} else {*/
    private BlockPos findUnloadedBuildZone(ClientPlayerEntity player, World world) {
    /*?}*/
        if (player == null || world == null) return null;

        /*? if >=26.1 {*//*
        Vec3 playerPos = player.position();
        *//*?} else if >=1.21.10 {*//*
        Vec3d playerPos = player.getSyncedPos();
        *//*?} else {*/
        Vec3d playerPos = player.getPos();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            // Sample every 4th block per axis to keep the scan fast.
            // On a 500×500×100 build this checks ~781K positions instead
            // of 25M, and still finds the nearest unloaded region.
            for (int y = 0; y < region.absY; y += 4) {
                for (int z = 0; z < region.absZ; z += 4) {
                    for (int x = 0; x < region.absX; x += 4) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target) && !isLiquidSource(target)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        // We only care about blocks in UNLOADED chunks
                        /*? if >=26.1 {*//*
                        if (world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/

                        /*? if >=26.1 {*//*
                        double dist = playerPos.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                        *//*?} else {*/
                        double dist = playerPos.squaredDistanceTo(wx + 0.5, wy + 0.5, wz + 0.5);
                        /*?}*/
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = new BlockPos(wx, wy, wz);
                        }
                    }
                }
            }
        }
        return best;
    }

    // PLACEMENT HELPERS

    /** Formats items as a chat string (up to 5 names + overflow). */
    private static String formatMissingItems(Set<Item> items) {
        if (items == null || items.isEmpty()) return "§7(none)";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Item item : items) {
            if (shown > 0) sb.append("§7, ");
            /*? if >=26.1 {*//*
            sb.append("§f").append(item.getName(item.getDefaultInstance()).getString());
            *//*?} else {*/
            sb.append("§f").append(item.getName().getString());
            /*?}*/
            if (++shown >= 5) {
                int more = items.size() - shown;
                if (more > 0) sb.append(" §7+").append(more).append(" more");
                break;
            }
        }
        return sb.toString();
    }

    /** Formats item IDs as a chat string, resolving display names. */
    private static String formatNeededItemIds(Set<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return "§7(none)";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String id : itemIds) {
            /*? if >=26.1 {*//*
            net.minecraft.resources.Identifier itemId = Identifier.tryParse(id);
            *//*?} else {*/
            net.minecraft.util.Identifier itemId = Identifier.tryParse(id);
            /*?}*/
            if (itemId == null) continue;
            /*? if >=26.1 {*//*
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            *//*?} else {*/
            Item item = Registries.ITEM.get(itemId);
            /*?}*/
            if (item == Items.AIR) continue;
            if (shown > 0) sb.append("§7, ");
            /*? if >=26.1 {*//*
            sb.append("§f").append(item.getName(item.getDefaultInstance()).getString());
            *//*?} else {*/
            sb.append("§f").append(item.getName().getString());
            /*?}*/
            if (++shown >= 5) {
                int more = itemIds.size() - shown;
                if (more > 0) sb.append(" §7+").append(more).append(" more");
                break;
            }
        }
        return shown == 0 ? "§7(unknown items)" : sb.toString();
    }

    /** Adds neededItems IDs to skippedItems so tryPlaceNextBlock skips them. */
    private void addNeededToSkipped() {
        for (String id : neededItems) {
            /*? if >=26.1 {*//*
            net.minecraft.resources.Identifier itemId = Identifier.tryParse(id);
            *//*?} else {*/
            net.minecraft.util.Identifier itemId = Identifier.tryParse(id);
            /*?}*/
            if (itemId == null) continue;
            /*? if >=26.1 {*//*
            Item item = BuiltInRegistries.ITEM.getValue(itemId);
            *//*?} else {*/
            Item item = Registries.ITEM.get(itemId);
            /*?}*/
            if (item != Items.AIR) {
                skippedItems.add(item);
            }
        }
    }

    /** Counts unplaced schematic blocks whose item is in skippedItems. */
    /*? if >=26.1 {*//*
    private int countSkippedBlocks(Level world) {
    *//*?} else {*/
    private int countSkippedBlocks(World world) {
    /*?}*/
        if (world == null || schematic == null) return 0;
        int count = 0;
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                        if (isAutoCreatedPart(target)) continue;

                        Item reqItem = isLiquidSource(target)
                                ? getLiquidBucketItem(target)
                                : target.getBlock().asItem();
                        if (reqItem == null || !skippedItems.contains(reqItem)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Returns true if the given block state can actually be placed
     * by a player (i.e. has a corresponding BlockItem).
     * Filters out liquids (water, lava), fire, portals, light blocks, etc.
     */
    private static boolean isPlaceable(BlockState state) {
        /*? if >=26.1 {*//*
        if (state.getBlock() instanceof LiquidBlock) return false;
        *//*?} else {*/
        if (state.getBlock() instanceof FluidBlock) return false;
        /*?}*/
        if (!state.getFluidState().isEmpty()) return false;
        // Accept any block that has a valid item form (not Items.AIR).
        // This covers BlockItem, SignItem, HangingSignItem, and other
        // special item types that can still be placed.
        return state.getBlock().asItem() != Items.AIR;
    }

    /**
     * Returns true if the given block state is a placeable liquid
     * source block (water or lava with level 0).  Flowing liquid (level > 0)
     * is auto-generated and should not be individually placed.
     */
    private static boolean isLiquidSource(BlockState state) {
        /*? if >=26.1 {*//*
        return state.getBlock() instanceof LiquidBlock
        *//*?} else {*/
        return state.getBlock() instanceof FluidBlock
        /*?}*/
            /*? if >=26.1 {*//*
            && state.getFluidState().isSource();
            *//*?} else {*/
            && state.getFluidState().isStill();
            /*?}*/
    }

    /**
     * Returns true if the schematic contains any liquid source blocks
     * that have not yet been placed in the world.
     */
    /*? if >=26.1 {*//*
    private boolean hasRemainingLiquids(Level world) {
    *//*?} else {*/
    private boolean hasRemainingLiquids(World world) {
    /*?}*/
        if (schematic == null || anchor == null) return false;
        /*? if >=26.1 {*//*
        long tick = world.getGameTime();
        *//*?} else {*/
        long tick = world.getTime();
        /*?}*/
        if (tick - liquidsCacheTick < REMAINING_CACHE_TTL) return cachedHasLiquids;
        liquidsCacheTick = tick;
        cachedHasLiquids = hasRemainingLiquidsUncached(world);
        return cachedHasLiquids;
    }

    /*? if >=26.1 {*//*
    private boolean hasRemainingLiquidsUncached(Level world) {
    *//*?} else {*/
    private boolean hasRemainingLiquidsUncached(World world) {
    /*?}*/
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (!isLiquidSource(target)) continue;
                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** True if unplaced solid blocks remain in the schematic (TTL-cached). */
    /*? if >=26.1 {*//*
    private boolean hasRemainingSolids(Level world) {
    *//*?} else {*/
    private boolean hasRemainingSolids(World world) {
    /*?}*/
        if (schematic == null || anchor == null) return false;
        /*? if >=26.1 {*//*
        long tick = world.getGameTime();
        *//*?} else {*/
        long tick = world.getTime();
        /*?}*/
        if (tick - solidsCacheTick < REMAINING_CACHE_TTL) return cachedHasSolids;
        solidsCacheTick = tick;
        cachedHasSolids = hasRemainingSolidsUncached(world);
        return cachedHasSolids;
    }

    /*? if >=26.1 {*//*
    private boolean hasRemainingSolidsUncached(Level world) {
    *//*?} else {*/
    private boolean hasRemainingSolidsUncached(World world) {
    /*?}*/
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target)) continue;
                        if (isAutoCreatedPart(target)) continue;
                        // Structural only — skip liquids and redstone
                        if (isLiquidSource(target)) continue;
                        if (BlockDependency.isRedstoneComponent(target)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** True if unplaced redstone components remain (TTL-cached). */
    /*? if >=26.1 {*//*
    private boolean hasRemainingRedstone(Level world) {
    *//*?} else {*/
    private boolean hasRemainingRedstone(World world) {
    /*?}*/
        if (schematic == null || anchor == null) return false;
        /*? if >=26.1 {*//*
        long tick = world.getGameTime();
        *//*?} else {*/
        long tick = world.getTime();
        /*?}*/
        if (tick - redstoneCacheTick < REMAINING_CACHE_TTL) return cachedHasRedstone;
        redstoneCacheTick = tick;
        cachedHasRedstone = hasRemainingRedstoneUncached(world);
        return cachedHasRedstone;
    }

    /*? if >=26.1 {*//*
    private boolean hasRemainingRedstoneUncached(Level world) {
    *//*?} else {*/
    private boolean hasRemainingRedstoneUncached(World world) {
    /*?}*/
        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target)) continue;
                        if (isAutoCreatedPart(target)) continue;
                        if (!BlockDependency.isRedstoneComponent(target)) continue;
                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;
                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the bucket Item required to place the given fluid
     * block state, or null if the state is not a supported fluid.
     */
    private static Item getLiquidBucketItem(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.WATER) return Items.WATER_BUCKET;
        if (block == Blocks.LAVA) return Items.LAVA_BUCKET;
        return null;
    }

    /**
     * Auto-created block parts that should NOT be individually placed
     * (door upper, bed head, tall plant upper).
     */
    private static boolean isAutoCreatedPart(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock
                /*? if >=26.1 {*//*
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && state.contains(Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                *//*?} else {*/
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                /*?}*/
            return true;
        }
        if (block instanceof BedBlock
                /*? if >=26.1 {*//*
                && state.hasProperty(BlockStateProperties.BED_PART)
                *//*?} else {*/
                && state.contains(Properties.BED_PART)
                /*?}*/
                /*? if >=26.1 {*//*
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
                *//*?} else {*/
                && state.get(Properties.BED_PART) == BedPart.HEAD) {
                /*?}*/
            return true;
        }
        /*? if >=26.1 {*//*
        if (block instanceof DoublePlantBlock
        *//*?} else {*/
        if (block instanceof TallPlantBlock
        /*?}*/
                /*? if >=26.1 {*//*
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && state.contains(Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                *//*?} else {*/
                && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                /*?}*/
            return true;
        }
        return false;
    }

    /**
     * Checks if existing block matches desired, ignoring neighbor-computed
     * dynamic properties (stair shape, fence connections, door open state,
     * redstone power, etc.).
     */
    private static boolean isEffectivelyPlaced(BlockState existing, BlockState desired) {
        if (existing.equals(desired)) return true;
        if (existing.getBlock() != desired.getBlock()) return false;

        Block block = existing.getBlock();

        // Fluid blocks — same fluid type present as a source block
        /*? if >=26.1 {*//*
        if (block instanceof LiquidBlock) {
        *//*?} else {*/
        if (block instanceof FluidBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            return existing.getFluidState().isSource() && desired.getFluidState().isSource();
            *//*?} else {*/
            return existing.getFluidState().isStill() && desired.getFluidState().isStill();
            /*?}*/
        }

        // Stairs — STAIR_SHAPE is neighbor-computed; only FACING + HALF matter
        /*? if >=26.1 {*//*
        if (block instanceof StairBlock) {
        *//*?} else {*/
        if (block instanceof StairsBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING)
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING)
            /*?}*/
                /*? if >=26.1 {*//*
                && propMatches(existing, desired, BlockStateProperties.HALF);
                *//*?} else {*/
                && propMatches(existing, desired, Properties.BLOCK_HALF);
                /*?}*/
        }

        // Doors — OPEN, POWERED are dynamic; FACING + HALF + HINGE matter
        if (block instanceof DoorBlock) {
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING)
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING)
            /*?}*/
                /*? if >=26.1 {*//*
                && propMatches(existing, desired, BlockStateProperties.DOUBLE_BLOCK_HALF)
                *//*?} else {*/
                && propMatches(existing, desired, Properties.DOUBLE_BLOCK_HALF)
                /*?}*/
                /*? if >=26.1 {*//*
                && propMatches(existing, desired, BlockStateProperties.DOOR_HINGE);
                *//*?} else {*/
                && propMatches(existing, desired, Properties.DOOR_HINGE);
                /*?}*/
        }

        // Fences — connection booleans are neighbor-computed
        if (block instanceof FenceBlock) {
            return true;
        }

        // Panes (iron bars, glass panes) — connection booleans are neighbor-computed
        /*? if >=26.1 {*//*
        if (block instanceof IronBarsBlock) {
        *//*?} else {*/
        if (block instanceof PaneBlock) {
        /*?}*/
            return true;
        }

        // Fence gates — OPEN, POWERED, IN_WALL are dynamic; only FACING matters
        if (block instanceof FenceGateBlock) {
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING);
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING);
            /*?}*/
        }

        // Walls — connection heights are neighbor-computed
        if (block instanceof WallBlock) {
            return true;
        }

        // Chests — CHEST_TYPE determined by neighbor chests
        if (block instanceof AbstractChestBlock) {
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING);
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING);
            /*?}*/
        }

        // Trapdoors — OPEN and POWERED are dynamic; only FACING + HALF matter
        /*? if >=26.1 {*//*
        if (block instanceof TrapDoorBlock) {
        *//*?} else {*/
        if (block instanceof TrapdoorBlock) {
        /*?}*/
            /*? if >=26.1 {*//*
            return propMatches(existing, desired, BlockStateProperties.HORIZONTAL_FACING)
            *//*?} else {*/
            return propMatches(existing, desired, Properties.HORIZONTAL_FACING)
            /*?}*/
                /*? if >=26.1 {*//*
                && propMatches(existing, desired, BlockStateProperties.HALF);
                *//*?} else {*/
                && propMatches(existing, desired, Properties.BLOCK_HALF);
                /*?}*/
        }

        // Redstone wire — connections and power are neighbor-computed
        /*? if >=26.1 {*//*
        if (block instanceof RedStoneWireBlock) {
        *//*?} else {*/
        if (block instanceof RedstoneWireBlock) {
        /*?}*/
            return true;
        }

        // Mushroom blocks — face booleans (N/S/E/W/UP/DOWN) are neighbor-computed
        if (block instanceof MushroomBlock) {
            return true;
        }

        // Vines — face attachment booleans are neighbor-computed
        if (block instanceof VineBlock) {
            return true;
        }

        // Chorus plant — connections are neighbor-computed
        if (block instanceof ChorusPlantBlock) {
            return true;
        }

        // Tripwire — ATTACHED, POWERED, DISARMED are dynamic
        /*? if >=26.1 {*//*
        if (block instanceof TripWireBlock) {
        *//*?} else {*/
        if (block instanceof TripwireBlock) {
        /*?}*/
            return true;
        }

        // Tall plants — just needs to be the same block type
        /*? if >=26.1 {*//*
        if (block instanceof DoublePlantBlock) {
        *//*?} else {*/
        if (block instanceof TallPlantBlock) {
        /*?}*/
            return true;
        }

        // Note blocks — INSTRUMENT is neighbor-computed, NOTE and POWERED are dynamic
        if (block instanceof NoteBlock) {
            return true;
        }

        // Shulker boxes — FACING is cosmetic, same color is enough
        if (block instanceof ShulkerBoxBlock) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if both states have the same value for the given
     * property, or if either state does not contain the property.
     */
    private static <T extends Comparable<T>> boolean propMatches(
            BlockState a, BlockState b, Property<T> prop) {
        /*? if >=26.1 {*//*
        if (!a.hasProperty(prop) || !b.hasProperty(prop)) return true;
        *//*?} else {*/
        if (!a.contains(prop) || !b.contains(prop)) return true;
        /*?}*/
        /*? if >=26.1 {*//*
        return a.getValue(prop).equals(b.getValue(prop));
        *//*?} else {*/
        return a.get(prop).equals(b.get(prop));
        /*?}*/
    }

    /**
     * Returns true if the given position is near any previously
     * failed build zone.
     */
    private boolean isNearFailedZone(BlockPos pos) {
        // Use the build reach distance as the exclusion radius.
        // If we can't path to one point, all blocks within our reach
        // distance of that point are likely unreachable too.
        double exclusionDist = Math.ceil(range);
        for (BlockPos fz : failedZones) {
            /*? if >=26.1 {*//*
            if (pos.closerThan(fz, exclusionDist)) return true;
            *//*?} else {*/
            if (pos.isWithinDistance(fz, exclusionDist)) return true;
            /*?}*/
        }
        return false;
    }

    /**
     * Returns true if the player's bounding box overlaps the given
     * block position.  Placing a block here would push the player out.
     */
    /*? if >=26.1 {*//*
    private static boolean playerOverlapsBlock(LocalPlayer player, BlockPos pos) {
    *//*?} else {*/
    private static boolean playerOverlapsBlock(ClientPlayerEntity player, BlockPos pos) {
    /*?}*/
        // Player hitbox: 0.6 wide (±0.3), 1.8 tall, centered on feet X/Z
        /*? if >=26.1 {*//*
        double px = player.position().x;
        double py = player.position().y;
        double pz = player.position().z;
        *//*?} else if >=1.21.10 {*//*
        double px = player.getSyncedPos().x;
        double py = player.getSyncedPos().y;
        double pz = player.getSyncedPos().z;
        *//*?} else {*/
        double px = player.getPos().x;
        double py = player.getPos().y;
        double pz = player.getPos().z;
        /*?}*/
        double halfW = 0.3;
        double height = 1.8;

        // AABB overlap test between player box and the block's unit cube
        return px + halfW > pos.getX() && px - halfW < pos.getX() + 1 &&
               py + height > pos.getY() && py < pos.getY() + 1 &&
               pz + halfW > pos.getZ() && pz - halfW < pos.getZ() + 1;
    }

    /**
     * Finds a safe standing position near target within placement reach.
     * Needs solid ground, clear head room, and outside the build footprint.
     */
    /*? if >=26.1 {*//*
    private BlockPos findStandingPosition(BlockPos target, Level world, LocalPlayer player) {
    *//*?} else {*/
    private BlockPos findStandingPosition(BlockPos target, World world, ClientPlayerEntity player) {
    /*?}*/
        int maxReach = (int) Math.ceil(range);
        // Use a reduced range to account for the player not standing
        // exactly at block center (can be ±0.5 off).  This ensures that
        // returned positions are comfortably within reach, not marginal.
        double bufferedRange = range - 0.3;
        double rangeSq = bufferedRange * bufferedRange;
        /*? if >=26.1 {*//*
        Vec3 targetCenter = Vec3.atCenterOf(target);
        *//*?} else {*/
        Vec3d targetCenter = Vec3d.ofCenter(target);
        /*?}*/

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        // Search at y-offsets from -8 to +8 relative to the target
        // so the player can stand above, below, or at the same level.
        // Wider range lets us find standing positions on lower floors of
        // already-built structures.  The reach check (rangeSq) still
        // filters out-of-reach positions, so the wider scan is safe.
        for (int yOff = -8; yOff <= 8; yOff++) {
            int feetY = target.getY() + yOff;

            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    // Skip the target column itself and immediate neighbours
                    // in the same Y — those are where blocks need to be placed
                    if (dx == 0 && dz == 0) continue;

                    int wx = target.getX() + dx;
                    int wz = target.getZ() + dz;
                    BlockPos feetPos = new BlockPos(wx, feetY, wz);

                    // Must be within placement reach of the target
                    /*? if >=26.1 {*//*
                    double dist = Vec3.atCenterOf(feetPos).add(0, 1.62 - 0.5, 0) // eye height from feet center
                    *//*?} else {*/
                    double dist = Vec3d.ofCenter(feetPos).add(0, 1.62 - 0.5, 0) // eye height from feet center
                    /*?}*/
                            /*? if >=26.1 {*//*
                            .distanceToSqr(targetCenter);
                            *//*?} else {*/
                            .squaredDistanceTo(targetCenter);
                            /*?}*/
                    if (dist > rangeSq) continue;

                    // Ground must support the player (block below feet has
                    // a non-empty collision shape — covers glass, slabs, stairs,
                    // fences, etc.) OR be a correctly-placed schematic block.
                    /*? if >=26.1 {*//*
                    BlockPos groundPos = feetPos.below();
                    *//*?} else {*/
                    BlockPos groundPos = feetPos.down();
                    /*?}*/
                    BlockState groundState = world.getBlockState(groundPos);
                    boolean walkableGround = !groundState.getCollisionShape(world, groundPos).isEmpty();
                    if (!walkableGround) {
                        // Also accept ground that is a correctly-placed schematic block
                        if (!isCorrectSchematicBlock(groundPos, world)) continue;
                    }

                    // Feet and head level must be passable (air/replaceable/fluid).
                    // Reject lava (damage) but allow water (player can swim).
                    BlockState feetState = world.getBlockState(feetPos);
                    /*? if >=26.1 {*//*
                    BlockState headState = world.getBlockState(feetPos.above());
                    *//*?} else {*/
                    BlockState headState = world.getBlockState(feetPos.up());
                    /*?}*/
                    /*? if >=26.1 {*//*
                    if (!feetState.isAir() && !feetState.canBeReplaced()) continue;
                    *//*?} else {*/
                    if (!feetState.isAir() && !feetState.isReplaceable()) continue;
                    /*?}*/
                    /*? if >=26.1 {*//*
                    if (!headState.isAir() && !headState.canBeReplaced()) continue;
                    *//*?} else {*/
                    if (!headState.isAir() && !headState.isReplaceable()) continue;
                    /*?}*/
                    if (feetState.getBlock() == Blocks.LAVA) continue;
                    if (headState.getBlock() == Blocks.LAVA) continue;

                    // Must NOT be an unbuilt schematic position (feet or head)
                    if (isUnbuiltSchematicBlock(feetPos, world)) continue;
                    /*? if >=26.1 {*//*
                    if (isUnbuiltSchematicBlock(feetPos.above(), world)) continue;
                    *//*?} else {*/
                    if (isUnbuiltSchematicBlock(feetPos.up(), world)) continue;
                    /*?}*/

                    // Must have at least one horizontal escape that won't
                    // become a schematic block (so we don't get walled in)
                    if (!hasEscapeRoute(feetPos, world)) continue;

                    // Avoid positions near flowing water during solid building
                    // (Baritone can't navigate through currents).  Skip during
                    // liquid pass — placed water naturally creates flows.
                    if (!liquidPass && hasFlowingWaterNearby(feetPos, world)) continue;

                    // Prefer closer to the player's current position,
                    // with penalties for wet and unreachable positions.
                    /*? if >=26.1 {*//*
                    double playerDist = player.position().distanceToSqr(Vec3.atCenterOf(feetPos));
                    *//*?} else if >=1.21.10 {*//*
                    double playerDist = player.getSyncedPos().squaredDistanceTo(Vec3d.ofCenter(feetPos));
                    *//*?} else {*/
                    double playerDist = player.getPos().squaredDistanceTo(Vec3d.ofCenter(feetPos));
                    /*?}*/
                    boolean inWater = !feetState.getFluidState().isEmpty();
                    // Heavy penalty for water positions — anti-cheat servers
                    // reject placements from swimming, so strongly prefer
                    // dry-land positions.
                    double penalty = inWater ? 500.0 : 0.0;

                    // Penalise positions separated from the player by
                    // an air gap (no continuous ground between them).
                    // This deprioritises spots across chasms, but uses a
                    // moderate penalty so positions on already-placed
                    // elevated structure (stairs, floors, platforms) are
                    // still reachable — Baritone can path to them using
                    // the existing blocks even if the straight-line ground
                    // check fails.
                    /*? if >=26.1 {*//*
                    if (!hasGroundPath(player.blockPosition(), feetPos, world)) {
                    *//*?} else {*/
                    if (!hasGroundPath(player.getBlockPos(), feetPos, world)) {
                    /*?}*/
                        // Lower penalty for positions above the player —
                        // vertical travel via placed structure is expected.
                        /*? if >=26.1 {*//*
                        int yDiff = feetPos.getY() - player.blockPosition().getY();
                        *//*?} else {*/
                        int yDiff = feetPos.getY() - player.getBlockPos().getY();
                        /*?}*/
                        penalty += (Math.abs(yDiff) > 2) ? 200.0 : 10000.0;
                    }

                    if (playerDist + penalty < bestDist) {
                        bestDist = playerDist + penalty;
                        best = feetPos;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Simple ground check: walks a straight line from start to end
     * at destination Y, verifying solid ground at each step.
     */
    /*? if >=26.1 {*//*
    private static boolean hasGroundPath(BlockPos from, BlockPos to, Level world) {
    *//*?} else {*/
    private static boolean hasGroundPath(BlockPos from, BlockPos to, World world) {
    /*?}*/
        int x0 = from.getX(), z0 = from.getZ();
        int x1 = to.getX(), z1 = to.getZ();
        int y = to.getY(); // check ground at destination elevation

        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        // Walk a Bresenham line from (x0,z0) to (x1,z1)
        int steps = 0;
        int maxSteps = dx + dz + 2; // safety limit
        while (steps++ < maxSteps) {
            if (x0 == x1 && z0 == z1) break;
            // Check ground under this column
            BlockPos groundPos = new BlockPos(x0, y - 1, z0);
            BlockState groundState = world.getBlockState(groundPos);
            /*? if >=26.1 {*//*
            if (groundState.isAir() || groundState.canBeReplaced()) {
            *//*?} else {*/
            if (groundState.isAir() || groundState.isReplaceable()) {
            /*?}*/
                return false; // air gap — no ground
            }

            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x0 += sx; }
            if (e2 < dx)  { err += dx; z0 += sz; }
        }
        return true;
    }

    /**
     * Returns true if the given world position corresponds to an
     * unbuilt schematic block (the schematic expects a block there but the
     * world doesn't have it yet).
     */
    /*? if >=26.1 {*//*
    private boolean isUnbuiltSchematicBlock(BlockPos worldPos, Level world) {
    *//*?} else {*/
    private boolean isUnbuiltSchematicBlock(BlockPos worldPos, World world) {
    /*?}*/
        int sx = worldPos.getX() - anchor.getX();
        int sy = worldPos.getY() - anchor.getY();
        int sz = worldPos.getZ() - anchor.getZ();
        if (!schematic.contains(sx, sy, sz)) return false;

        BlockState expected = schematic.getBlockState(sx, sy, sz);
        if (expected.isAir()) return false;
        if (!isPlaceable(expected) && !isLiquidSource(expected)) return false;

        return !isEffectivelyPlaced(world.getBlockState(worldPos), expected);
    }

    // ENTRAPMENT SAFETY

    /** Cardinal directions the player can walk through. */
    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    /**
     * Whether a block state allows safe walking through it.
     * Excludes liquids — water/lava are technically passable but not
     * safe exits (player would swim / take damage).
     */
    private static boolean isPassable(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        /*? if >=26.1 {*//*
        return state.isAir() || state.canBeReplaced() || !state.blocksMotion();
        *//*?} else {*/
        return state.isAir() || state.isReplaceable() || !state.blocksMovement();
        /*?}*/
    }

    /**
     * Whether a block state physically blocks movement.  Unlike
     * isPassable, this treats fluids as non-blocking because
     * the player can swim through them.  Used for entrapment detection
     * where we only care about solid walls, not water.
     */
    private static boolean isMovementBlocking(BlockState state) {
        /*? if >=26.1 {*//*
        if (state.isAir() || state.canBeReplaced()) return false;
        *//*?} else {*/
        if (state.isAir() || state.isReplaceable()) return false;
        /*?}*/
        if (!state.getFluidState().isEmpty()) return false; // can swim
        /*? if >=26.1 {*//*
        return state.blocksMotion();
        *//*?} else {*/
        return state.blocksMovement();
        /*?}*/
    }

    /**
     * Returns true if placing a block at pos would seal
     * the player's last walkable horizontal exit.
     *
     * Only relevant when the candidate block is in the player's
     * "exit ring" — one of the 4 cardinal neighbours at feet or head
     * level.  If placing it would drop the exit count from ≥1 to 0,
     * the placement is vetoed.
     */
    /*? if >=26.1 {*//*
    private static boolean wouldTrapPlayer(LocalPlayer player,
    *//*?} else {*/
    private static boolean wouldTrapPlayer(ClientPlayerEntity player,
    /*?}*/
                                           /*? if >=26.1 {*//*
                                           BlockPos pos, Level world) {
                                           *//*?} else {*/
                                           BlockPos pos, World world) {
                                           /*?}*/
        /*? if >=26.1 {*//*
        BlockPos feetPos = player.blockPosition();
        *//*?} else {*/
        BlockPos feetPos = player.getBlockPos();
        /*?}*/

        // Only check blocks that form the player's exit ring
        int dy = pos.getY() - feetPos.getY();
        if (dy < 0 || dy > 1) return false; // not at feet or head level

        int dx = pos.getX() - feetPos.getX();
        int dz = pos.getZ() - feetPos.getZ();
        if (Math.abs(dx) + Math.abs(dz) != 1) return false; // not cardinal

        // Count exits before and after the hypothetical placement
        int exitsBefore = 0;
        int exitsAfter  = 0;

        for (Direction dir : HORIZONTALS) {
            /*? if >=26.1 {*//*
            BlockPos feetN = feetPos.relative(dir);
            *//*?} else {*/
            BlockPos feetN = feetPos.offset(dir);
            /*?}*/
            /*? if >=26.1 {*//*
            BlockPos headN = feetN.above();
            *//*?} else {*/
            BlockPos headN = feetN.up();
            /*?}*/

            // Use isMovementBlocking instead of isPassable so that
            // water/lava count as valid exits (player can swim through).
            boolean feetOk = !isMovementBlocking(world.getBlockState(feetN));
            boolean headOk = !isMovementBlocking(world.getBlockState(headN));

            if (feetOk && headOk) {
                exitsBefore++;
                // Would this exit survive the placement?
                if (!feetN.equals(pos) && !headN.equals(pos)) {
                    exitsAfter++;
                }
            }
        }

        return exitsBefore > 0 && exitsAfter == 0;
    }

    /**
     * Returns true if the player currently has no
     * walkable horizontal exit (all 4 cardinal directions are blocked
     * at either feet or head level by solid blocks).
     *
     * Fluids do NOT count as entrapment — the player can swim
     * through water/lava even though it's undesirable.  Only solid
     * blocks that physically prevent horizontal movement trigger this.
     */
    /*? if >=26.1 {*//*
    private static boolean isPlayerTrapped(LocalPlayer player,
    *//*?} else {*/
    private static boolean isPlayerTrapped(ClientPlayerEntity player,
    /*?}*/
                                           /*? if >=26.1 {*//*
                                           Level world) {
                                           *//*?} else {*/
                                           World world) {
                                           /*?}*/
        /*? if >=26.1 {*//*
        BlockPos feetPos = player.blockPosition();
        *//*?} else {*/
        BlockPos feetPos = player.getBlockPos();
        /*?}*/
        for (Direction dir : HORIZONTALS) {
            /*? if >=26.1 {*//*
            BlockPos feetN = feetPos.relative(dir);
            *//*?} else {*/
            BlockPos feetN = feetPos.offset(dir);
            /*?}*/
            /*? if >=26.1 {*//*
            BlockPos headN = feetN.above();
            *//*?} else {*/
            BlockPos headN = feetN.up();
            /*?}*/
            if (!isMovementBlocking(world.getBlockState(feetN)) &&
                    !isMovementBlocking(world.getBlockState(headN))) {
                return false; // at least one exit
            }
        }
        return true;
    }

    /**
     * Returns true if the given feet position has at least one
     * horizontal exit direction that is currently clear and
     * will remain clear after the schematic is fully built (i.e. the
     * exit blocks are not unbuilt schematic positions).
     */
    /*? if >=26.1 {*//*
    private boolean hasEscapeRoute(BlockPos feetPos, Level world) {
    *//*?} else {*/
    private boolean hasEscapeRoute(BlockPos feetPos, World world) {
    /*?}*/
        for (Direction dir : HORIZONTALS) {
            /*? if >=26.1 {*//*
            BlockPos feetN = feetPos.relative(dir);
            *//*?} else {*/
            BlockPos feetN = feetPos.offset(dir);
            /*?}*/
            /*? if >=26.1 {*//*
            BlockPos headN = feetN.above();
            *//*?} else {*/
            BlockPos headN = feetN.up();
            /*?}*/

            // Currently passable?  Use isMovementBlocking so water
            // counts as a valid escape direction (player can swim).
            if (isMovementBlocking(world.getBlockState(feetN))) continue;
            if (isMovementBlocking(world.getBlockState(headN))) continue;

            // Won't become a schematic block later?
            if (isUnbuiltSchematicBlock(feetN, world)) continue;
            if (isUnbuiltSchematicBlock(headN, world)) continue;

            return true;
        }
        return false;
    }

    /**
     * Returns true if any block within 2 horizontal blocks of
     * feetPos (at feet or head level) contains flowing water or
     * lava.  Flowing liquids push the player, breaking Baritone's
     * pathfinding — so standing positions near them should be avoided.
     */
    /*? if >=26.1 {*//*
    private static boolean hasFlowingWaterNearby(BlockPos feetPos, Level world) {
    *//*?} else {*/
    private static boolean hasFlowingWaterNearby(BlockPos feetPos, World world) {
    /*?}*/
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    /*? if >=26.1 {*//*
                    BlockPos check = feetPos.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos check = feetPos.add(dx, dy, dz);
                    /*?}*/
                    FluidState fluid = world.getBlockState(check).getFluidState();
                    /*? if >=26.1 {*//*
                    if (!fluid.isEmpty() && !fluid.isSource()) {
                    *//*?} else {*/
                    if (!fluid.isEmpty() && !fluid.isStill()) {
                    /*?}*/
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Finds an escape position outside an enclosed area.  Searches outward
     * up to 8 blocks, accepting water positions unlike findStandingPosition.
     */
    /*? if >=26.1 {*//*
    private BlockPos findEscapePosition(LocalPlayer player,
    *//*?} else {*/
    private BlockPos findEscapePosition(ClientPlayerEntity player,
    /*?}*/
                                        /*? if >=26.1 {*//*
                                        Level world) {
                                        *//*?} else {*/
                                        World world) {
                                        /*?}*/
        /*? if >=26.1 {*//*
        BlockPos origin = player.blockPosition();
        *//*?} else {*/
        BlockPos origin = player.getBlockPos();
        /*?}*/
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // ring only
                    for (int dy = -2; dy <= 2; dy++) {
                        /*? if >=26.1 {*//*
                        BlockPos feetPos = origin.offset(dx, dy, dz);
                        *//*?} else {*/
                        BlockPos feetPos = origin.add(dx, dy, dz);
                        /*?}*/

                        // Ground must be solid (or solid under water)
                        /*? if >=26.1 {*//*
                        BlockPos groundPos = feetPos.below();
                        *//*?} else {*/
                        BlockPos groundPos = feetPos.down();
                        /*?}*/
                        BlockState groundState = world.getBlockState(groundPos);
                        /*? if >=26.1 {*//*
                        if (!groundState.isSolidRender()
                        *//*?} else {*/
                        if (!groundState.isSolidBlock(world, groundPos)
                        /*?}*/
                                && groundState.getFluidState().isEmpty()) continue;

                        // Feet + head must not be solid blocks.
                        // Water is acceptable here — we're escaping
                        // entrapment, not finding a comfortable position.
                        if (isMovementBlocking(world.getBlockState(feetPos))) continue;
                        /*? if >=26.1 {*//*
                        if (isMovementBlocking(world.getBlockState(feetPos.above()))) continue;
                        *//*?} else {*/
                        if (isMovementBlocking(world.getBlockState(feetPos.up()))) continue;
                        /*?}*/

                        // Not inside the schematic's unbuilt footprint
                        if (isUnbuiltSchematicBlock(feetPos, world)) continue;
                        /*? if >=26.1 {*//*
                        if (isUnbuiltSchematicBlock(feetPos.above(), world)) continue;
                        *//*?} else {*/
                        if (isUnbuiltSchematicBlock(feetPos.up(), world)) continue;
                        /*?}*/

                        /*? if >=26.1 {*//*
                        double dist = player.position().distanceToSqr(
                                Vec3.atCenterOf(feetPos));
                        *//*?} else if >=1.21.10 {*//*
                        double dist = player.getSyncedPos().squaredDistanceTo(
                                Vec3d.ofCenter(feetPos));
                        *//*?} else {*/
                        double dist = player.getPos().squaredDistanceTo(
                                Vec3d.ofCenter(feetPos));
                        /*?}*/

                        // Prefer positions NOT in water when possible
                        boolean inWater = !world.getFluidState(feetPos).isEmpty();
                        double penalty = inWater ? 100.0 : 0.0;

                        if (dist + penalty < bestDist) {
                            bestDist = dist + penalty;
                            best = feetPos;
                        }
                    }
                }
            }
            if (best != null) return best; // found one at this radius
        }
        return best;
    }

    // CORE PLACEMENT

    private int placeDebugCooldown = 0;

    /*? if >=26.1 {*//*
    private boolean tryPlaceNextBlock(LocalPlayer player, Level world) {
    *//*?} else {*/
    private boolean tryPlaceNextBlock(ClientPlayerEntity player, World world) {
    /*?}*/
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        double rangeSq = range * range;
        int maxReach = (int) Math.ceil(range);

        List<BlockPos> candidates = new ArrayList<>();
        /*? if >=26.1 {*//*
        BlockPos playerPos = player.blockPosition();
        *//*?} else {*/
        BlockPos playerPos = player.getBlockPos();
        /*?}*/
        /*? if >=26.1 {*//*
        Vec3 eyePos = player.getEyePosition();
        *//*?} else {*/
        Vec3d eyePos = player.getEyePos();
        /*?}*/

        // Debug counters: track why blocks are filtered
        int dbgTotal = 0, dbgRange = 0, dbgOverlap = 0, dbgBounds = 0;
        int dbgAir = 0, dbgPlaceable = 0, dbgAutoCreated = 0, dbgLiquid = 0;
        int dbgPlaced = 0, dbgNoAdj = 0, dbgTrap = 0;
        BlockPos dbgFirstFiltered = null;
        String dbgFirstReason = null;

        for (int dy = -maxReach; dy <= maxReach; dy++) {
            for (int dx = -maxReach; dx <= maxReach; dx++) {
                for (int dz = -maxReach; dz <= maxReach; dz++) {
                    /*? if >=26.1 {*//*
                    BlockPos worldPos = playerPos.offset(dx, dy, dz);
                    *//*?} else {*/
                    BlockPos worldPos = playerPos.add(dx, dy, dz);
                    /*?}*/

                    /*? if >=26.1 {*//*
                    if (eyePos.distanceToSqr(Vec3.atCenterOf(worldPos)) > rangeSq) continue;
                    *//*?} else {*/
                    if (eyePos.squaredDistanceTo(Vec3d.ofCenter(worldPos)) > rangeSq) continue;
                    /*?}*/
                    // Skip blocks the player is physically standing inside
                    // (placing there would push them out).  Player hitbox is
                    // 0.6×1.8×0.6 centered on their feet position.
                    if (playerOverlapsBlock(player, worldPos)) continue;

                    int sx = worldPos.getX() - anchor.getX();
                    int sy = worldPos.getY() - anchor.getY();
                    int sz = worldPos.getZ() - anchor.getZ();
                    if (!schematic.contains(sx, sy, sz)) continue;

                    BlockState target = schematic.getBlockState(sx, sy, sz);
                    if (target.isAir()) continue;
                    if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                    if (isAutoCreatedPart(target)) continue;

                    // Three-pass deferral: structural → redstone → liquid.
                    boolean isLiquid = isLiquidSource(target);
                    boolean isRedstone = !isLiquid && BlockDependency.isRedstoneComponent(target);
                    if (liquidPass) {
                        if (!isLiquid) continue;
                    } else if (redstonePass) {
                        if (!isRedstone) continue;
                    } else {
                        if (isLiquid || isRedstone) continue;
                    }

                    // This block needs placing — count it for debug
                    dbgTotal++;
                    BlockState existingState = world.getBlockState(worldPos);
                    if (isEffectivelyPlaced(existingState, target)) {
                        dbgPlaced++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "already placed (" + existingState.getBlock() + " vs " + target.getBlock() + ")"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "already placed (" + existingState.getBlock() + " vs " + target.getBlock() + ")"; }
                        /*?}*/
                        continue;
                    }

                    if (!printInAir && !PlacementEngine.hasAdjacentSolid(world, worldPos)) {
                        dbgNoAdj++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "no adjacent solid"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "no adjacent solid"; }
                        /*?}*/
                        continue;
                    }

                    // Don't place a block that would wall the player in
                    if (wouldTrapPlayer(player, worldPos, world)) {
                        dbgTrap++;
                        /*? if >=26.1 {*//*
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.immutable(); dbgFirstReason = "would trap player"; }
                        *//*?} else {*/
                        if (dbgFirstFiltered == null) { dbgFirstFiltered = worldPos.toImmutable(); dbgFirstReason = "would trap player"; }
                        /*?}*/
                        continue;
                    }

                    candidates.add(worldPos);
                }
            }
        }

        if (candidates.isEmpty()) {
            // Debug output (rate-limited to once per 5 s / 100 ticks)
            if (LOGGER.isDebugEnabled() && statusMessages && placeDebugCooldown <= 0) {
                placeDebugCooldown = 100;
                StringBuilder sb = new StringBuilder("[PlaceDbg] ");
                sb.append("0 candidates. ").append(dbgTotal).append(" unplaced schematic blocks nearby. ");
                if (dbgTotal > 0) {
                    sb.append("Filtered: ");
                    if (dbgPlaced > 0) sb.append("placed=").append(dbgPlaced).append(" ");
                    if (dbgNoAdj > 0) sb.append("noAdj=").append(dbgNoAdj).append(" ");
                    if (dbgTrap > 0) sb.append("trap=").append(dbgTrap).append(" ");
                    if (dbgFirstFiltered != null) {
                        sb.append(" First: ").append(dbgFirstFiltered.getX())
                          .append(" ").append(dbgFirstFiltered.getY())
                          .append(" ").append(dbgFirstFiltered.getZ())
                          .append(": ").append(dbgFirstReason);
                    }
                } else {
                    sb.append("(no unplaced blocks in scan range from ")
                      .append(playerPos.getX()).append(" ").append(playerPos.getY())
                      .append(" ").append(playerPos.getZ()).append(")");
                }
                LOGGER.debug(sb.toString());
            }
            if (placeDebugCooldown > 0) placeDebugCooldown--;
            lastMissingItems.clear();
            return false;
        }

        // Dependency-aware sorting
        // Freestanding blocks (tier 0) are tried before blocks that need
        // adjacent support (tier 1: torches, flowers, rails, etc.).  This
        // ensures support structures are built before dependent blocks
        // are attempted, reducing wasted placement attempts.
        Comparator<BlockPos> comparator;
        if (sortMode == SortMode.BOTTOM_UP) {
            comparator = Comparator.<BlockPos>comparingInt((BlockPos p) -> {
                        int sx2 = p.getX() - anchor.getX();
                        int sy2 = p.getY() - anchor.getY();
                        int sz2 = p.getZ() - anchor.getZ();
                        return BlockDependency.getTier(schematic.getBlockState(sx2, sy2, sz2));
                    })
                .thenComparingInt(BlockPos::getY)
                /*? if >=26.1 {*//*
                .thenComparingDouble(p -> eyePos.distanceToSqr(Vec3.atCenterOf(p)));
                *//*?} else {*/
                .thenComparingDouble(p -> eyePos.squaredDistanceTo(Vec3d.ofCenter(p)));
                /*?}*/
        } else if (sortMode == SortMode.TOP_DOWN) {
            comparator = Comparator.<BlockPos>comparingInt((BlockPos p) -> {
                        int sx2 = p.getX() - anchor.getX();
                        int sy2 = p.getY() - anchor.getY();
                        int sz2 = p.getZ() - anchor.getZ();
                        return BlockDependency.getTier(schematic.getBlockState(sx2, sy2, sz2));
                    })
                .thenComparingInt(p -> -p.getY())
                /*? if >=26.1 {*//*
                .thenComparingDouble(p -> eyePos.distanceToSqr(Vec3.atCenterOf(p)));
                *//*?} else {*/
                .thenComparingDouble(p -> eyePos.squaredDistanceTo(Vec3d.ofCenter(p)));
                /*?}*/
        } else {
            comparator = Comparator.<BlockPos>comparingInt((BlockPos p) -> {
                        int sx2 = p.getX() - anchor.getX();
                        int sy2 = p.getY() - anchor.getY();
                        int sz2 = p.getZ() - anchor.getZ();
                        return BlockDependency.getTier(schematic.getBlockState(sx2, sy2, sz2));
                    })
                /*? if >=26.1 {*//*
                .thenComparingDouble(p -> eyePos.distanceToSqr(Vec3.atCenterOf(p)));
                *//*?} else {*/
                .thenComparingDouble(p -> eyePos.squaredDistanceTo(Vec3d.ofCenter(p)));
                /*?}*/
        }
        candidates.sort(comparator);

        Set<Item> missing = new HashSet<>();
        int dbgDepSkip = 0, dbgPlaceFail = 0, dbgScaffold = 0;
        BlockPos dbgFirstPlaceFail = null;
        BlockState dbgFirstPlaceFailTarget = null;
        BlockState dbgFirstPlaceFailExisting = null;

        List<BlockPos>   batchTargets = null;
        List<BlockState> batchStates  = null;
        Item             batchItem    = null;

        for (BlockPos worldPos : candidates) {
            int sx = worldPos.getX() - anchor.getX();
            int sy = worldPos.getY() - anchor.getY();
            int sz = worldPos.getZ() - anchor.getZ();
            BlockState target = schematic.getBlockState(sx, sy, sz);

            // Dependency check: skip blocks whose support is missing
            // Torches without a wall, flowers without a floor, rails
            // without ground, etc. are silently skipped.  They will be
            // retried once their support blocks have been placed.
            if (!BlockDependency.isReadyToPlace(world, worldPos, target)) {
                dbgDepSkip++;
                continue;
            }

            // Skip blocks whose materials we already gave up on
            // If restock failed and this item was marked skipped, don't
            // waste time trying — move on to blocks we CAN place.
            if (!skippedItems.isEmpty()) {
                Item reqItem = isLiquidSource(target)
                        ? getLiquidBucketItem(target)
                        : target.getBlock().asItem();
                if (reqItem != null && skippedItems.contains(reqItem)) continue;
            }

            // liquid source block → bucket placement
            if (isLiquidSource(target)) {
                Item bucketItem = getLiquidBucketItem(target);
                if (bucketItem == null) continue;

                // Check if we actually have this bucket in inventory.
                // Unlike solid blocks, don't add to 'missing' on placement
                // failure — placeLiquid can fail due to angle/position issues
                // even when we have buckets, and we want to try other positions.
                Map<Item, Integer> inv = PlacementEngine.getInventoryContentsCached();
                if (inv.getOrDefault(bucketItem, 0) <= 0) {
                    missing.add(bucketItem);
                    continue;
                }
                if (missing.contains(bucketItem)) continue;

                if (PlacementEngine.placeLiquid(worldPos, target, swapItems)) {
                    lastMissingItems.clear();
                    return true;
                }
                // Don't add to missing — placement failed for positional
                // reasons, not because we lack the item.
                continue;
            }

            // normal block placement
            Item requiredItem = target.getBlock().asItem();
            // Skip items we already know are missing (avoid redundant hotbar scans)
            if (missing.contains(requiredItem)) continue;

            // If a scaffold block (placed by Baritone) occupies this
            // position, break it first so the correct block can be placed.
            // PlacementEngine's correction mechanism will break the scaffold,
            // and on the next cycle the position will be air → normal placement.
            BlockState existing = world.getBlockState(worldPos);
            /*? if >=26.1 {*//*
            if (!existing.isAir() && !existing.canBeReplaced()
            *//*?} else {*/
            if (!existing.isAir() && !existing.isReplaceable()
            /*?}*/
                    && existing.getBlock() != target.getBlock()) {
                dbgScaffold++;
                if (PlacementEngine.placeBlock(worldPos, target, swapItems)) {
                    lastMissingItems.clear();
                    return true;
                }
                // PlacementEngine busy or can't start — skip for now
                continue;
            }

            if (batchTargets == null && PlacementEngine.canBatchPlace()) {
                batchTargets = new ArrayList<>(9);
                batchStates  = new ArrayList<>(9);
                batchItem    = requiredItem;
            }
            if (batchTargets != null
                    && batchTargets.size() < 9
                    && requiredItem == batchItem) {
                /*? if >=26.1 {*//*
                batchTargets.add(worldPos.immutable());
                *//*?} else {*/
                batchTargets.add(worldPos.toImmutable());
                /*?}*/
                batchStates.add(target);
                continue;
            }

            if (PlacementEngine.placeBlock(worldPos, target, swapItems)) {
                lastMissingItems.clear();
                placeDebugCooldown = 0;
                return true;
            }

            // Track first placeBlock failure for debug
            dbgPlaceFail++;
            if (dbgFirstPlaceFail == null) {
                /*? if >=26.1 {*//*
                dbgFirstPlaceFail = worldPos.immutable();
                *//*?} else {*/
                dbgFirstPlaceFail = worldPos.toImmutable();
                /*?}*/
                dbgFirstPlaceFailTarget = target;
                dbgFirstPlaceFailExisting = existing;
            }

            // placeBlock can fail for many reasons (angle, reach, no
            // adjacent face, etc.) — only mark as missing if the item
            // genuinely isn't in the player's inventory.
            if (requiredItem != Items.AIR) {
                Map<Item, Integer> currentInv = PlacementEngine.getInventoryContentsCached();
                if (currentInv.getOrDefault(requiredItem, 0) <= 0) {
                    missing.add(requiredItem);
                }
            }
        }

        if (batchTargets != null && !batchTargets.isEmpty()) {
            int placed = PlacementEngine.placeBatch(batchTargets, batchStates, swapItems);
            if (placed > 0) {
                lastMissingItems.clear();
                placeDebugCooldown = 0;
                return true;
            }
        }

        // Debug output when candidates exist but no placement started
        if (LOGGER.isDebugEnabled() && statusMessages && !candidates.isEmpty() && placeDebugCooldown <= 0) {
            placeDebugCooldown = 100;
            StringBuilder sb = new StringBuilder("[PlaceDbg] ");
            sb.append(candidates.size()).append(" candidates, none placed. ");
            if (dbgDepSkip > 0) sb.append("depSkip=").append(dbgDepSkip).append(" ");
            if (dbgPlaceFail > 0) sb.append("placeFail=").append(dbgPlaceFail).append(" ");
            if (dbgScaffold > 0) sb.append("scaffold=").append(dbgScaffold).append(" ");
            sb.append("missing=").append(missing.size());
            if (dbgFirstPlaceFail != null) {
                sb.append(" First fail: ").append(dbgFirstPlaceFail.getX())
                  .append(" ").append(dbgFirstPlaceFail.getY())
                  .append(" ").append(dbgFirstPlaceFail.getZ())
                  .append(" want=").append(dbgFirstPlaceFailTarget.getBlock())
                  .append(" existing=").append(dbgFirstPlaceFailExisting.getBlock())
                  .append(" phase=").append(PlacementEngine.getPhase());
            }
            LOGGER.debug(sb.toString());
        }
        if (placeDebugCooldown > 0) placeDebugCooldown--;

        lastMissingItems = missing;
        return false;
    }

    // UTILITY / STATUS

    public int countRemaining() {
        if (!isLoaded()) return -1;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.player == null || mc.level == null) return -1;
        *//*?} else {*/
        if (mc.player == null || mc.world == null) return -1;
        /*?}*/

        // Return cached value if computed recently
        /*? if >=26.1 {*//*
        long tick = mc.level.getGameTime();
        *//*?} else {*/
        long tick = mc.world.getTime();
        /*?}*/
        if (tick - remainingCacheTick < REMAINING_CACHE_TTL && cachedCountRemaining >= 0) {
            return cachedCountRemaining;
        }

        /*? if >=26.1 {*//*
        Level world = mc.level;
        *//*?} else {*/
        World world = mc.world;
        /*?}*/
        int remaining = 0;

        for (LitematicaSchematic.Region region : schematic.getRegions()) {
            for (int y = 0; y < region.absY; y++) {
                for (int z = 0; z < region.absZ; z++) {
                    for (int x = 0; x < region.absX; x++) {
                        BlockState target = region.getBlockState(x, y, z);
                        if (target.isAir()) continue;
                        if (!isPlaceable(target) && !isLiquidSource(target)) continue;
                        if (isAutoCreatedPart(target)) continue;

                        int wx = anchor.getX() + region.originX + x;
                        int wy = anchor.getY() + region.originY + y;
                        int wz = anchor.getZ() + region.originZ + z;

                        /*? if >=26.1 {*//*
                        if (!world.hasChunk(wx >> 4, wz >> 4)) continue;
                        *//*?} else {*/
                        if (!world.isChunkLoaded(wx >> 4, wz >> 4)) continue;
                        /*?}*/
                        if (!isEffectivelyPlaced(world.getBlockState(new BlockPos(wx, wy, wz)), target)) {
                            remaining++;
                        }
                    }
                }
            }
        }
        remainingCacheTick = tick;
        cachedCountRemaining = remaining;
        return remaining;
    }    public static List<String> listSchematics() {
        List<String> names = new ArrayList<>();
        Path dir = getSchematicsDir();
        if (!Files.isDirectory(dir)) return names;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.litematic")) {
            for (Path entry : stream) {
                String fname = entry.getFileName().toString();
                names.add(fname.substring(0, fname.length() - ".litematic".length()));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to list schematics directory", e);
        }
        return names;
    }

    public static Path getSchematicsDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    // CHECKPOINT / RESUME

    public void saveCheckpoint() {
        if (schematicFile != null && anchor != null) {
            /*? if >=26.1 {*//*
            Minecraft mc = Minecraft.getInstance();
            *//*?} else {*/
            MinecraftClient mc = MinecraftClient.getInstance();
            /*?}*/
            /*? if >=26.1 {*//*
            BlockPos playerPos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
            *//*?} else {*/
            BlockPos playerPos = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
            /*?}*/
            PrinterCheckpoint.save(schematicFile, anchor, blocksPlaced, playerPos);
        }
    }

    public void restoreFromCheckpoint(PrinterCheckpoint.CheckpointData data, Path schematicPath) throws IOException {
        this.schematic = LitematicaSchematic.load(schematicPath);
        this.anchor = data.anchorPos();
        this.blocksPlaced = data.blocksPlaced;
        this.schematicFile = schematicPath.getFileName().toString();
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        this.buildDimension = mc.level != null ? mc.level.dimension() : null;
        *//*?} else {*/
        this.buildDimension = mc.world != null ? mc.world.getRegistryKey() : null;
        /*?}*/
    }

    public String getSchematicFile() { return schematicFile; }

    // MATERIALS REPORT

    public PrinterResourceManager.MaterialsReport analyzeMaterials() {
        if (!isLoaded()) return PrinterResourceManager.MaterialsReport.EMPTY;
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        /*? if >=26.1 {*//*
        if (mc.level == null) return PrinterResourceManager.MaterialsReport.EMPTY;
        *//*?} else {*/
        if (mc.world == null) return PrinterResourceManager.MaterialsReport.EMPTY;
        /*?}*/
        /*? if >=26.1 {*//*
        return PrinterResourceManager.analyzeMaterials(schematic, anchor, mc.level);
        *//*?} else {*/
        return PrinterResourceManager.analyzeMaterials(schematic, anchor, mc.world);
        /*?}*/
    }
}

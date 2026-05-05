package dev.moar.lanes;

import dev.moar.MoarMod;
import dev.moar.stash.StashDatabase;
import dev.moar.util.ChatHelper;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
/*?}*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

// Top-level coordinator for the storage-lane feature.
// Typical flow: set region corners, scan for pending lanes, preview results,
// accept them into the database, then start sorting against accepted lanes.
public final class LaneManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/LaneManager");

    // ── Region corners ────────────────────────────────────────────────────────

    private BlockPos corner1;
    private BlockPos corner2;

    // ── Lane collections ──────────────────────────────────────────────────────

    // Lanes inferred by the last scan but not yet accepted/persisted.
    private final List<StorageLane> pendingLanes = new ArrayList<>();

    // Accepted (persisted) lanes, loaded from DB on startup and updated on accept.
    private final List<StorageLane> acceptedLanes = new ArrayList<>();

    // ── Sub-systems ───────────────────────────────────────────────────────────

    private final LaneSorter sorter = new LaneSorter();

    // ── Public API — corners ──────────────────────────────────────────────────

    public void setCorner1(BlockPos pos) {
        /*? if >=26.1 {*//*
        this.corner1 = pos.immutable();
        *//*?} else {*/
        this.corner1 = pos.toImmutable();
        /*?}*/
    }

    public void setCorner2(BlockPos pos) {
        /*? if >=26.1 {*//*
        this.corner2 = pos.immutable();
        *//*?} else {*/
        this.corner2 = pos.toImmutable();
        /*?}*/
    }

    public BlockPos getCorner1() { return corner1; }
    public BlockPos getCorner2() { return corner2; }

    // ── Public API — scan ─────────────────────────────────────────────────────

    // Synchronously scan the configured region for storage lanes.
    // Replaces any existing pending lanes.
    public void scan() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        Level world = mc != null ? mc.level : null;
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc != null ? mc.world : null;
        /*?}*/
        if (world == null) {
            ChatHelper.labelled("Lanes", "§cNot in a world.");
            return;
        }
        if (corner1 == null || corner2 == null) {
            ChatHelper.labelled("Lanes", "§cSet region corners first: "
                    + "§f/stash lanes region pos1 §cand §f/stash lanes region pos2");
            return;
        }

        BlockPos min = new BlockPos(
                Math.min(corner1.getX(), corner2.getX()),
                Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(corner1.getX(), corner2.getX()),
                Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ()));

        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;

        ChatHelper.labelled("Lanes", "§aScanning §f" + sx + "×" + sy + "×" + sz
                + "§a for storage lanes...");

        pendingLanes.clear();
        pendingLanes.addAll(LaneScanner.scan(world, min, max));

        if (pendingLanes.isEmpty()) {
            ChatHelper.labelled("Lanes", "§cNo chests or barrels found in region.");
        } else {
            ChatHelper.labelled("Lanes", "§aFound §f" + pendingLanes.size()
                    + "§a candidate lane(s). Run §f/stash lanes preview §ato inspect, "
                    + "then §f/stash lanes accept §ato persist.");
        }
    }

    // ── Public API — preview ──────────────────────────────────────────────────

    // Print all pending lanes to chat.
    public void preview() {
        if (pendingLanes.isEmpty()) {
            ChatHelper.labelled("Lanes", "§cNo pending scan results. Run §f/stash lanes scan §cfirst.");
            return;
        }
        ChatHelper.labelled("Lanes", "§lPending lanes (" + pendingLanes.size() + "):");
        for (StorageLane lane : pendingLanes) {
            ChatHelper.labelled("Lanes", "  " + lane.summary());
        }
        ChatHelper.labelled("Lanes", "§7Run §f/stash lanes accept §7to save these lanes.");
    }

    // ── Public API — accept ───────────────────────────────────────────────────

    // Persist all pending lanes to the database and add them to accepted lanes.
    public void accept() {
        if (pendingLanes.isEmpty()) {
            ChatHelper.labelled("Lanes", "§cNo pending lanes to accept.");
            return;
        }

        StashDatabase db = getOpenDatabase();
        if (db == null) return;

        int saved = 0;
        for (StorageLane lane : pendingLanes) {
            if (db.saveLane(lane)) {
                acceptedLanes.add(lane);
                saved++;
            }
        }
        pendingLanes.clear();
        sortAcceptedLanes();

        ChatHelper.labelled("Lanes", "§aSaved §f" + saved + "§a lane(s).");
    }

    // ── Public API — list ─────────────────────────────────────────────────────

    // Print all accepted lanes to chat.
    public void list() {
        if (acceptedLanes.isEmpty()) {
            ChatHelper.labelled("Lanes", "§cNo accepted lanes. Run §f/stash lanes scan§c → "
                    + "§f/stash lanes accept§c.");
            return;
        }
        ChatHelper.labelled("Lanes", "§lAccepted lanes (" + acceptedLanes.size() + "):");
        for (StorageLane lane : acceptedLanes) {
            ChatHelper.labelled("Lanes", "  §8[" + lane.getId() + "]§7 " + lane.summary());
        }
    }

    // ── Public API — clear ────────────────────────────────────────────────────

    // Wipe all accepted lanes from memory and the database.
    public void clearLanes() {
        StashDatabase db = getOpenDatabase();
        if (db != null) db.clearAllLanes();
        acceptedLanes.clear();
        pendingLanes.clear();
        ChatHelper.labelled("Lanes", "§eAll storage lanes cleared.");
    }

    // ── Public API — assign ───────────────────────────────────────────────────

    // Assign an item ID to the lane that owns the given chest position.
    public boolean assignItem(BlockPos pos, String itemId) {
        StorageLane target = findLaneByChest(pos);
        if (target == null) return false;

        target.setItemId(itemId);

        StashDatabase db = getOpenDatabase();
        if (db != null && target.getId() != 0) {
            db.updateLaneItem(target.getId(), itemId);
        }
        return true;
    }

    // Find a pending lane containing the given chest position.
    public StorageLane findPendingLaneByChest(BlockPos pos) {
        for (StorageLane lane : pendingLanes) {
            if (lane.containsChest(pos)) return lane;
        }
        return null;
    }

    // Find an accepted lane containing the given chest position.
    public StorageLane findLaneByChest(BlockPos pos) {
        for (StorageLane lane : acceptedLanes) {
            if (lane.containsChest(pos)) return lane;
        }
        return null;
    }

    // ── Public API — sort ─────────────────────────────────────────────────────

    // Start the lane sorter with all currently accepted lanes.
    public boolean startSort() {
        if (sorter.isActive()) {
            ChatHelper.labelled("Lanes", "§cSort already in progress. Use §f/stash lanes sort stop §cto cancel.");
            return false;
        }
        return sorter.start(acceptedLanes);
    }

    // Preview the current sort plan without executing it.
    public boolean previewSort() {
        return sorter.preview(acceptedLanes);
    }

    // Abort an in-progress sort.
    public void stopSort() {
        if (sorter.isActive()) {
            sorter.stop();
        } else {
            ChatHelper.labelled("Lanes", "§eNo sort in progress.");
        }
    }

    // ── Public API — label ────────────────────────────────────────────────────

    public void labelPreview() {
        LaneLabeler.preview(acceptedLanes);
    }

    public void labelRun() {
        LaneLabeler.run(acceptedLanes);
    }

    // ── Public API — manual lane editing ─────────────────────────────────────

    public boolean createLane(String name, String itemId) {
        if (ensureSortInactive() == false) return false;

        String normalizedName = normalizeLaneName(name);
        if (normalizedName == null) {
            ChatHelper.labelled("Lanes", "§cLane name cannot be empty.");
            return false;
        }
        if (findLaneByNameAny(normalizedName) != null) {
            ChatHelper.labelled("Lanes", "§cLane §f" + normalizedName + "§c already exists.");
            return false;
        }

        StashDatabase db = getOpenDatabase();
        if (db == null) {
            ChatHelper.labelled("Lanes", "§cDatabase not available.");
            return false;
        }

        StorageLane lane = new StorageLane(normalizedName);
        lane.setPriority(nextPriority());
        if (itemId != null && !itemId.isBlank()) {
            lane.setItemId(itemId);
        }

        if (!db.saveLane(lane)) {
            ChatHelper.labelled("Lanes", "§cFailed to create lane §f" + normalizedName + "§c.");
            return false;
        }

        acceptedLanes.add(lane);
        sortAcceptedLanes();

        ChatHelper.labelled("Lanes", "§aCreated lane §f" + lane.getName()
                + "§a" + (lane.isAssigned() ? " for §f" + lane.getItemId() : "") + "§a.");
        return true;
    }

    public boolean addChestToLane(String laneName, BlockPos pos) {
        if (ensureSortInactive() == false) return false;

        StorageLane lane = findLaneByNameAny(laneName);
        if (lane == null) {
            ChatHelper.labelled("Lanes", "§cUnknown lane §f" + laneName + "§c.");
            return false;
        }
        if (lane.containsChest(pos)) {
            ChatHelper.labelled("Lanes", "§eThat chest is already part of §f" + lane.getName() + "§e.");
            return false;
        }

        StorageLane owner = findLaneOwningPosition(pos, lane);
        if (owner != null) {
            ChatHelper.labelled("Lanes", "§cThat position already belongs to lane §f"
                    + owner.getName() + "§c.");
            return false;
        }

        lane.addChest(copyPos(pos));
        if (!persistEditedLane(lane)) {
            removeLast(lane.getChestPositions(), lane::setChests);
            return false;
        }

        ChatHelper.labelled("Lanes", "§aAdded chest to §f" + lane.getName() + "§a at §f"
                + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + (isAcceptedLane(lane) ? "" : " §7(pending only)"));
        return true;
    }

    public boolean addInputToLane(String laneName, BlockPos pos) {
        if (ensureSortInactive() == false) return false;

        StorageLane lane = findLaneByNameAny(laneName);
        if (lane == null) {
            ChatHelper.labelled("Lanes", "§cUnknown lane §f" + laneName + "§c.");
            return false;
        }
        if (lane.containsInput(pos)) {
            ChatHelper.labelled("Lanes", "§eThat input is already part of §f" + lane.getName() + "§e.");
            return false;
        }

        StorageLane owner = findLaneOwningPosition(pos, lane);
        if (owner != null) {
            ChatHelper.labelled("Lanes", "§cThat position already belongs to lane §f"
                    + owner.getName() + "§c.");
            return false;
        }

        lane.addInput(copyPos(pos));
        if (!persistEditedLane(lane)) {
            removeLast(lane.getInputPositions(), lane::setInputs);
            return false;
        }

        ChatHelper.labelled("Lanes", "§aAdded input to §f" + lane.getName() + "§a at §f"
                + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + (isAcceptedLane(lane) ? "" : " §7(pending only)"));
        return true;
    }

    public boolean setLaneMode(String laneName, StorageLane.DepositMode mode) {
        if (ensureSortInactive() == false) return false;

        StorageLane lane = findLaneByNameAny(laneName);
        if (lane == null) {
            ChatHelper.labelled("Lanes", "§cUnknown lane §f" + laneName + "§c.");
            return false;
        }

        StorageLane.DepositMode oldMode = lane.getDepositMode();
        lane.setDepositMode(mode);
        if (!persistEditedLane(lane)) {
            lane.setDepositMode(oldMode);
            return false;
        }

        ChatHelper.labelled("Lanes", "§aLane §f" + lane.getName() + "§a mode set to §f"
                + mode.name() + (isAcceptedLane(lane) ? "" : " §7(pending only)"));
        return true;
    }

    public boolean setLaneFace(String laneName, Direction face) {
        if (ensureSortInactive() == false) return false;

        StorageLane lane = findLaneByNameAny(laneName);
        if (lane == null) {
            ChatHelper.labelled("Lanes", "§cUnknown lane §f" + laneName + "§c.");
            return false;
        }

        Direction oldFace = lane.getFrontFace();
        lane.setFrontFace(face);
        if (!persistEditedLane(lane)) {
            lane.setFrontFace(oldFace);
            return false;
        }

        ChatHelper.labelled("Lanes", "§aLane §f" + lane.getName() + "§a face set to §f"
                + face.name() + (isAcceptedLane(lane) ? "" : " §7(pending only)"));
        return true;
    }

    public boolean removeLane(String laneName) {
        if (ensureSortInactive() == false) return false;

        StorageLane accepted = findAcceptedLaneByName(laneName);
        if (accepted != null) {
            StashDatabase db = getOpenDatabase();
            if (db == null) {
                ChatHelper.labelled("Lanes", "§cDatabase not available.");
                return false;
            }
            if (!db.deleteLane(accepted.getId())) {
                ChatHelper.labelled("Lanes", "§cFailed to delete lane §f" + accepted.getName() + "§c.");
                return false;
            }
            acceptedLanes.remove(accepted);
            ChatHelper.labelled("Lanes", "§eRemoved accepted lane §f" + accepted.getName() + "§e.");
            return true;
        }

        StorageLane pending = findPendingLaneByName(laneName);
        if (pending != null) {
            pendingLanes.remove(pending);
            ChatHelper.labelled("Lanes", "§eRemoved pending lane §f" + pending.getName() + "§e.");
            return true;
        }

        ChatHelper.labelled("Lanes", "§cUnknown lane §f" + laneName + "§c.");
        return false;
    }

    // ── Public API — collections ──────────────────────────────────────────────

    public List<StorageLane> getPendingLanes() {
        return Collections.unmodifiableList(pendingLanes);
    }

    public List<StorageLane> getAcceptedLanes() {
        return Collections.unmodifiableList(acceptedLanes);
    }

    public LaneSorter getSorter() { return sorter; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    // Load persisted lanes from the database.
    // Call once after the database is opened at mod init.
    public void loadFromDatabase() {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null || !db.isOpen()) return;

        List<StorageLane> loaded = db.loadAllLanes();
        acceptedLanes.clear();
        acceptedLanes.addAll(loaded);
        sortAcceptedLanes();
        if (!loaded.isEmpty()) {
            LOGGER.info("LaneManager: loaded {} accepted lanes from database", loaded.size());
        }
    }

    // Tick the sorter state machine. Call from the client tick event.
    public void tick() {
        sorter.tick();
    }

    // Reset all runtime state (call on world disconnect).
    public void stop() {
        if (sorter.isActive()) sorter.stop();
        pendingLanes.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static StashDatabase getOpenDatabase() {
        StashDatabase db = MoarMod.getDatabase();
        if (db == null) return null;
        if (!db.isOpen()) db.open();
        return db.isOpen() ? db : null;
    }

    private boolean ensureSortInactive() {
        if (!sorter.isActive()) return true;
        ChatHelper.labelled("Lanes", "§cStop the active sort before editing lanes.");
        return false;
    }

    private StorageLane findAcceptedLaneByName(String name) {
        String normalized = normalizeLaneName(name);
        if (normalized == null) return null;
        for (StorageLane lane : acceptedLanes) {
            if (lane.getName().equalsIgnoreCase(normalized)) return lane;
        }
        return null;
    }

    private StorageLane findPendingLaneByName(String name) {
        String normalized = normalizeLaneName(name);
        if (normalized == null) return null;
        for (StorageLane lane : pendingLanes) {
            if (lane.getName().equalsIgnoreCase(normalized)) return lane;
        }
        return null;
    }

    private StorageLane findLaneByNameAny(String name) {
        StorageLane accepted = findAcceptedLaneByName(name);
        return accepted != null ? accepted : findPendingLaneByName(name);
    }

    private StorageLane findLaneOwningPosition(BlockPos pos, StorageLane exclude) {
        for (StorageLane lane : acceptedLanes) {
            if (lane != exclude && (lane.containsChest(pos) || lane.containsInput(pos))) return lane;
        }
        for (StorageLane lane : pendingLanes) {
            if (lane != exclude && (lane.containsChest(pos) || lane.containsInput(pos))) return lane;
        }
        return null;
    }

    private boolean persistEditedLane(StorageLane lane) {
        if (!isAcceptedLane(lane) || lane.getId() == 0) return true;

        StashDatabase db = getOpenDatabase();
        if (db == null) {
            ChatHelper.labelled("Lanes", "§cDatabase not available.");
            return false;
        }
        if (!db.updateLane(lane)) {
            ChatHelper.labelled("Lanes", "§cFailed to update lane §f" + lane.getName() + "§c.");
            return false;
        }
        sortAcceptedLanes();
        return true;
    }

    private boolean isAcceptedLane(StorageLane lane) {
        return acceptedLanes.contains(lane);
    }

    private int nextPriority() {
        int max = -1;
        for (StorageLane lane : acceptedLanes) {
            max = Math.max(max, lane.getPriority());
        }
        return max + 1;
    }

    private void sortAcceptedLanes() {
        acceptedLanes.sort(Comparator.comparingInt(StorageLane::getPriority)
                .thenComparing(StorageLane::getId));
    }

    private static String normalizeLaneName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BlockPos copyPos(BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void removeLast(List<BlockPos> positions, java.util.function.Consumer<List<BlockPos>> setter) {
        if (positions.isEmpty()) return;
        List<BlockPos> mutable = new ArrayList<>(positions);
        mutable.remove(mutable.size() - 1);
        setter.accept(mutable);
    }
}

package dev.moar.lanes;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
/*?}*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents one logical storage lane: an ordered sequence of chests
 * (and optional hopper/input positions) assigned to a single item type.
 */
public final class StorageLane {

    public enum DepositMode {
        /** Walk to the first non-full chest in lane order and deposit directly. */
        DIRECT_FILL,
        /** Deposit only into the designated input positions (hoppers / input chests). */
        INPUT_ONLY,
        /** Try input positions first; fall back to DIRECT_FILL if they are absent or full. */
        HYBRID
    }

    public enum OverflowBehavior {
        /** Leave overflowing items in player inventory unchanged. */
        SKIP,
        /** Deposit overflow into the next lane that accepts the same item. */
        NEXT_LANE,
        /** Send overflow to the configured dump chest. */
        DUMP
    }

    // ── Persisted fields ─────────────────────────────────────────────────────

    /** Database-assigned id; 0 = not yet persisted. */
    private int id;
    /** Human-readable display name (e.g. "lane_1"). */
    private String name;
    /** Assigned item identifier (e.g. "minecraft:cobblestone"). Nullable until set. */
    private String itemId;
    /** Position of the item frame that labels this lane. Nullable. */
    private BlockPos labelFramePos;
    /** Preferred outward-facing side for labels / lane interaction hints. Nullable. */
    private Direction frontFace;
    /** Ordered list of chest/barrel positions belonging to this lane. */
    private final List<BlockPos> chestPositions = new ArrayList<>();
    /** Optional hopper or input-chest positions for filter-based systems. */
    private final List<BlockPos> inputPositions = new ArrayList<>();

    private DepositMode depositMode = DepositMode.DIRECT_FILL;
    private int priority = 0;
    private OverflowBehavior overflowBehavior = OverflowBehavior.SKIP;

    // ── Construction ─────────────────────────────────────────────────────────

    public StorageLane(String name) {
        this.name = name;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String n) { this.name = n; }

    public String getItemId() { return itemId; }
    public void setItemId(String id) { this.itemId = id; }

    public BlockPos getLabelFramePos() { return labelFramePos; }
    public void setLabelFramePos(BlockPos pos) { this.labelFramePos = pos; }

    public Direction getFrontFace() { return frontFace; }
    public void setFrontFace(Direction face) { this.frontFace = face; }

    public List<BlockPos> getChestPositions() {
        return Collections.unmodifiableList(chestPositions);
    }
    public void addChest(BlockPos pos) { chestPositions.add(pos); }
    public void setChests(List<BlockPos> positions) {
        chestPositions.clear();
        chestPositions.addAll(positions);
    }

    public List<BlockPos> getInputPositions() {
        return Collections.unmodifiableList(inputPositions);
    }
    public void addInput(BlockPos pos) { inputPositions.add(pos); }
    public void setInputs(List<BlockPos> positions) {
        inputPositions.clear();
        inputPositions.addAll(positions);
    }

    public DepositMode getDepositMode() { return depositMode; }
    public void setDepositMode(DepositMode mode) { this.depositMode = mode; }

    public int getPriority() { return priority; }
    public void setPriority(int p) { this.priority = p; }

    public OverflowBehavior getOverflowBehavior() { return overflowBehavior; }
    public void setOverflowBehavior(OverflowBehavior b) { this.overflowBehavior = b; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True when this lane has been assigned an item type. */
    public boolean isAssigned() { return itemId != null && !itemId.isEmpty(); }

    /**
     * Returns true when {@code pos} is one of this lane's chest positions
     * (or the double-chest partner of one, which shares the same block position).
     */
    public boolean containsChest(BlockPos pos) {
        for (BlockPos p : chestPositions) {
            if (p.equals(pos)) return true;
        }
        return false;
    }

    public boolean containsInput(BlockPos pos) {
        for (BlockPos p : inputPositions) {
            if (p.equals(pos)) return true;
        }
        return false;
    }

    /** One-line chat summary for /stash lanes list and preview. */
    public String summary() {
        String displayItem = (itemId != null && !itemId.isEmpty())
                ? (itemId.startsWith("minecraft:") ? itemId.substring(10) : itemId)
                : "§8(unassigned)";
        String face = frontFace != null ? ", face=" + frontFace.name() : "";
        return "§e" + name + "§7: §f" + displayItem
                + " §8[" + chestPositions.size() + " chest"
                + (chestPositions.size() == 1 ? "" : "s")
                + (inputPositions.isEmpty() ? "" : ", " + inputPositions.size() + " input"
                        + (inputPositions.size() == 1 ? "" : "s"))
                + ", " + depositMode.name() + face
                + "]";
    }

    @Override
    public String toString() {
        return "StorageLane{id=" + id + ", name='" + name + "', item='" + itemId
                + "', chests=" + chestPositions.size() + "}";
    }
}

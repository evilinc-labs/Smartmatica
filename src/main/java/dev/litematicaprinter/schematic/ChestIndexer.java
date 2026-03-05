package dev.litematicaprinter.schematic;

import dev.litematicaprinter.util.PrinterDatabase;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Client-side supply chest scanner that reads the contents of opened chests
 * (and shulker boxes within them) and stores snapshots in
 * {@link PrinterDatabase}.
 *
 * <p>On the client side you cannot read a chest's inventory without opening
 * it.  This class snapshots the contents every time the player opens one of
 * the registered supply chests.  The snapshot includes items held directly
 * in the chest, as well as items inside any shulker boxes found in the
 * chest (read via {@code DataComponentTypes.CONTAINER}).
 *
 * <p>Thread safety: all methods are called on the client/render thread only.
 */
public final class ChestIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Smartmatica/ChestIndexer");

    private ChestIndexer() {}

    // ── public API ──────────────────────────────────────────────────────

    /**
     * Scans the currently open chest screen and stores the snapshot in
     * {@link PrinterDatabase} if the chest position matches a registered
     * supply chest.
     *
     * @param chestPos the world position of the chest being opened
     * @param handler  the open container screen handler
     */
    public static void scanOpenChest(BlockPos chestPos, GenericContainerScreenHandler handler) {
        if (chestPos == null || handler == null) return;

        BlockPos key = chestPos.toImmutable();
        Map<String, Integer> items = new HashMap<>();
        int shulkerCount = 0;

        int chestSlots = handler.getRows() * 9;
        for (int slot = 0; slot < chestSlots; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            // ── check if this is a shulker box ──────────────────────────
            if (isShulkerBox(stack)) {
                shulkerCount++;
                // index the shulker's contents
                Map<String, Integer> shulkerContents = readShulkerContents(stack);
                for (var entry : shulkerContents.entrySet()) {
                    items.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                // also count the shulker box item itself
                items.merge(itemId, stack.getCount(), Integer::sum);
            } else {
                items.merge(itemId, stack.getCount(), Integer::sum);
            }
        }

        PrinterDatabase.ChestSnapshot snapshot = new PrinterDatabase.ChestSnapshot(
                key, Map.copyOf(items), shulkerCount, System.currentTimeMillis());
        PrinterDatabase.putSnapshot(key, snapshot);

        LOGGER.debug("ChestIndexer: indexed {} at ({}, {}, {}) — {} item types, {} shulkers",
                chestSlots, key.getX(), key.getY(), key.getZ(), items.size(), shulkerCount);
    }

    /**
     * Read a shulker box ItemStack's contents via DataComponentTypes.CONTAINER.
     *
     * @return itemId → count for all items inside the shulker
     */
    public static Map<String, Integer> readShulkerContents(ItemStack shulkerStack) {
        Map<String, Integer> contents = new HashMap<>();
        if (shulkerStack == null || shulkerStack.isEmpty()) return contents;

        ContainerComponent cc = shulkerStack.get(DataComponentTypes.CONTAINER);
        if (cc == null) return contents;

        for (ItemStack inner : cc.iterateNonEmpty()) {
            String innerId = Registries.ITEM.getId(inner.getItem()).toString();
            contents.merge(innerId, inner.getCount(), Integer::sum);
        }
        return contents;
    }

    // ── internals ───────────────────────────────────────────────────────

    static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }
}

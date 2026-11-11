package xyz.acrylicstyle.storageBox.utils;

import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

/**
 * Rewrites Storage Boxes saved by older versions (1.15.2 boxes, and boxes written before {@link StorageBox#KEY_FORMAT})
 * into the current format, without the player having to take anything in or out.
 * <p>
 * Rewriting runs the box through {@link StorageBox#getItemStack()} exactly like any use of the box does: the 1.15.2
 * content is converted (and replaced with the current MythicMobs item when it is one), and the box gets the content's
 * model. The type, amount, settings and ID of the box are kept. Each box is rewritten once; boxes inside shulker
 * boxes are included. Main thread only.
 */
public final class StorageBoxUpgrader {
    private static final int MAX_DEPTH = 4;

    private StorageBoxUpgrader() {}

    /** @return the rewritten box, or null if the item is not a box that needs it */
    public static @Nullable ItemStack upgrade(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && isCurrent(meta.getPersistentDataContainer()) && !StorageBox.hasStrayItemTags(item)) return null;
        StorageBox box = StorageBox.getStorageBox(item);
        if (box == null) return null;
        ItemStack upgraded = box.getItemStack();
        upgraded.setAmount(item.getAmount());
        return upgraded;
    }

    /**
     * Whether the box was written by this version and is complete. A box with content always gets its serialized
     * content from {@link StorageBox#getItemStack()}; when it is missing, another plugin restored the box's tags
     * (e.g. EnderChestPlus /ecp storagebox removes the stale component_data), so the box is rewritten again.
     */
    private static boolean isCurrent(PersistentDataContainer pdc) {
        Integer format = pdc.get(StorageBox.KEY_FORMAT, PersistentDataType.INTEGER);
        if (format == null || format < StorageBox.CURRENT_FORMAT) return false;
        String type = pdc.get(StorageBox.KEY_TYPE, PersistentDataType.STRING);
        boolean hasContent = type != null && !type.isEmpty() && !type.equalsIgnoreCase("null");
        return !hasContent || pdc.has(StorageBox.KEY_COMPONENT_DATA, PersistentDataType.STRING);
    }

    /** @return the number of rewritten boxes (including those inside shulker boxes) */
    public static int upgradeInventory(@Nullable Inventory inventory) {
        return upgradeInventory(inventory, 0);
    }

    private static int upgradeInventory(@Nullable Inventory inventory, int depth) {
        if (inventory == null || depth > MAX_DEPTH) return 0;
        int count = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            try {
                ItemStack upgraded = upgrade(item);
                if (upgraded != null) {
                    inventory.setItem(slot, upgraded);
                    count++;
                    continue;
                }
                int nested = upgradeContainerItem(item, depth);
                if (nested > 0) {
                    inventory.setItem(slot, item);
                    count += nested;
                }
            } catch (RuntimeException e) {
                Logger.getLogger("StorageBox").warning("Could not rewrite the Storage Box in slot " + slot + ": " + e);
            }
        }
        return count;
    }

    /** Rewrites the boxes inside a shulker box (or other container item) in place. */
    private static int upgradeContainerItem(ItemStack item, int depth) {
        if (!item.hasItemMeta() || !(item.getItemMeta() instanceof BlockStateMeta)) return 0;
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        if (!meta.hasBlockState()) return 0;
        BlockState state = meta.getBlockState();
        if (!(state instanceof Container)) return 0;
        int count = upgradeInventory(((Container) state).getInventory(), depth + 1);
        if (count > 0) {
            meta.setBlockState(state);
            item.setItemMeta(meta);
        }
        return count;
    }

    /** The player's inventory and ender chest. */
    public static int upgradePlayer(Player player) {
        return upgradeInventory(player.getInventory()) + upgradeInventory(player.getEnderChest());
    }
}

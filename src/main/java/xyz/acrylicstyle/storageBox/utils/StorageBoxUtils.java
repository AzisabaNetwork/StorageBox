package xyz.acrylicstyle.storageBox.utils;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.Map;

public final class StorageBoxUtils {
    private StorageBoxUtils() {}

    public static ItemStack updateStorageBox(ItemStack itemStack) {
        StorageBox storageBox = StorageBox.getStorageBox(itemStack);
        if (storageBox == null) return itemStack;
        return storageBox.getItemStack();
    }

    /**
     * Lightweight API for reading the material and amount of a Storage Box without parsing its content.
     *
     * @param itemStack the item to inspect
     * @return the info, or {@code null} when the item is not a Storage Box
     */
    public static @Nullable StorageBoxItemInfo getStorageBoxItemInfo(@Nullable ItemStack itemStack) {
        return StorageBoxItemInfo.read(itemStack);
    }

    /**
     * Updates the stored item amount of a Storage Box item.
     *
     * @param itemStack the Storage Box item
     * @param amount    the new amount (clamped to {@code >= 0})
     * @return the updated Storage Box item, or {@code null} if {@code itemStack} is not a Storage Box
     */
    public static @Nullable ItemStack setAmount(@Nullable ItemStack itemStack, long amount) {
        if (itemStack == null) return null;
        StorageBox box = StorageBox.getStorageBox(itemStack);
        if (box == null) return null;
        box.setAmount(Math.max(0L, amount));
        return box.getItemStack();
    }

    /**
     * Takes up to {@code amount} items from the Storage Box at {@code slot} in {@code inventory}
     * and writes the updated Storage Box back to that slot.
     *
     * @param inventory the inventory containing the Storage Box
     * @param slot      the slot index in {@code inventory}
     * @param amount    the maximum number of items to take
     * @return the number of items actually taken
     */
    public static long take(@Nullable Inventory inventory, int slot, long amount) {
        if (inventory == null || amount <= 0 || slot < 0 || slot >= inventory.getSize()) return 0L;
        ItemStack item = inventory.getItem(slot);
        if (item == null) return 0L;
        StorageBox box = StorageBox.getStorageBox(item);
        if (box == null || box.isEmpty()) return 0L;
        long taken = Math.min(amount, box.getAmount());
        if (taken <= 0) return 0L;
        box.setAmount(box.getAmount() - taken);
        inventory.setItem(slot, box.getItemStack());
        return taken;
    }

    /**
     * Counts the total number of plain items of {@code material} stored in Storage Boxes in {@code inventory}.
     *
     * @param inventory the inventory to scan
     * @param material  the item material to match
     * @return the total stored amount across matching plain Storage Boxes
     */
    public static long countPlain(@Nullable Inventory inventory, @Nullable Material material) {
        if (inventory == null || material == null || material.isAir()) return 0L;
        long total = 0L;
        for (ItemStack item : inventory.getContents()) {
            StorageBoxItemInfo info = StorageBoxItemInfo.read(item);
            if (info == null || info.isEmpty() || info.getType() != material || !info.isPlainContent()) continue;
            long boxAmount = info.getAmount();
            if (boxAmount > 0) {
                total = Long.MAX_VALUE - total < boxAmount ? Long.MAX_VALUE : total + boxAmount;
            }
        }
        return total;
    }

    /**
     * Takes up to {@code amount} plain items of {@code material} from Storage Boxes in {@code inventory},
     * updating each modified slot.
     *
     * @param inventory the inventory to take from
     * @param material  the item material to match
     * @param amount    the maximum number of items to take
     * @return the total number of items actually taken
     */
    public static long takePlain(@Nullable Inventory inventory, @Nullable Material material, long amount) {
        if (inventory == null || material == null || material.isAir() || amount <= 0) return 0L;
        long remaining = amount;
        long totalTaken = 0L;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            StorageBoxItemInfo info = StorageBoxItemInfo.read(contents[i]);
            if (info == null || info.isEmpty() || info.getType() != material || !info.isPlainContent()) continue;
            long taken = take(inventory, i, remaining);
            remaining -= taken;
            totalTaken += taken;
        }
        return totalTaken;
    }

    public static Map.Entry<Integer, StorageBox> getStorageBoxForType(Inventory inventory, ItemStack item) {
        ItemStack[] c = inventory.getContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null) continue;
            // Reject non-box and non-matching boxes before the expensive full NBT conversion.
            StorageBoxItemInfo info = StorageBoxItemInfo.read(c[i]);
            if (info == null || !info.isAutoCollect()) continue;
            if (info.getType() == null || item == null || info.getType() != item.getType()) continue;
            StorageBox box = StorageBox.getStorageBox(c[i]);
            if (box == null || !box.isComponentItemStackSimilar(item)) continue;
            return new AbstractMap.SimpleEntry<>(i, box);
        }
        return null;
    }
}

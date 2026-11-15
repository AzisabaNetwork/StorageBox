package xyz.acrylicstyle.storageBox.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight information about an item inside a Storage Box.
 * <p>
 * {@link StorageBox#getStorageBox(ItemStack)} has to convert the item through packetevents and may deserialize the
 * stored content, which is far too expensive for code that only needs to know what a box contains and how many of
 * them there are (e.g. scanning a player's inventory on every GUI open). This class reads the small amount of data
 * that is already stored in the box's persistent data container. Boxes written by old versions, which have no PDC
 * data, still fall back to the full parser.
 * <p>
 * Instances are snapshots. {@link #getContentItemStack()} parses the content lazily and caches it for this instance.
 */
public final class StorageBoxItemInfo {
    private static final Method ITEM_PDC_METHOD = findItemPdcMethod();
    private static volatile Method viewGetMethod;

    private final @Nullable ItemStack itemStack;
    private final @Nullable Material type;
    private final long amount;
    private final boolean autoCollect;
    private final boolean autoBuy;
    private final @Nullable UUID id;
    /** Set for legacy boxes, whose data is only available through the full parser. */
    private final @Nullable StorageBox parsedBox;

    private boolean contentLoaded;
    private @Nullable ItemStack content;

    private StorageBoxItemInfo(@Nullable ItemStack itemStack, @Nullable Material type, long amount,
                               boolean autoCollect, boolean autoBuy, @Nullable UUID id, @Nullable StorageBox parsedBox) {
        this.itemStack = itemStack;
        this.type = type == null || type.isAir() ? null : type;
        this.amount = amount;
        this.autoCollect = autoCollect;
        this.autoBuy = autoBuy;
        this.id = id;
        this.parsedBox = parsedBox;
    }

    /**
     * Reads the information of a Storage Box item without fully parsing its content.
     *
     * @return the info, or {@code null} when the item is not a Storage Box
     */
    public static @Nullable StorageBoxItemInfo read(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) return null;

        Object pdc = persistentData(itemStack);
        String typeName = getString(pdc, StorageBox.KEY_TYPE);
        if (typeName == null) {
            // No PDC data: only old (1.15.2) boxes are possible. Do not run the full parser for every item
            // in an inventory, it is the expensive path this class exists to avoid.
            if (!looksLikeStorageBox(itemStack)) return null;
            StorageBox box = StorageBox.getStorageBox(itemStack);
            if (box == null) return null;
            return new StorageBoxItemInfo(itemStack, box.getType(), box.getAmount(), box.isAutoCollect(),
                    box.isAutoBuy(), null, box);
        }

        Long amountValue = getLong(pdc, StorageBox.KEY_AMOUNT);
        long amount = amountValue == null ? 0L : amountValue;
        Byte autoCollectValue = getByte(pdc, StorageBox.KEY_AUTO_COLLECT);
        boolean autoCollect = autoCollectValue == null || autoCollectValue != 0;
        Byte autoBuyValue = getByte(pdc, StorageBox.KEY_AUTO_BUY);
        boolean autoBuy = autoBuyValue != null && autoBuyValue != 0;

        Material type = null;
        try {
            type = LegacyComponentUpgrader.resolveMaterial(typeName);
        } catch (IllegalArgumentException ignored) {
            // unknown material: keep it as an empty/unknown box
        }

        return new StorageBoxItemInfo(itemStack, type, amount, autoCollect, autoBuy,
                parseUUID(getString(pdc, StorageBox.KEY_UUID)), null);
    }

    /** @return the content material, or {@code null} for an empty/undefined box */
    public @Nullable Material getType() {
        return type;
    }

    /** @return how many items are inside the box */
    public long getAmount() {
        return amount;
    }

    public boolean isEmpty() {
        return amount <= 0 || type == null;
    }

    public boolean isAutoCollect() {
        return autoCollect;
    }

    public boolean isAutoBuy() {
        return autoBuy;
    }

    /** @return the box's stable id, or {@code null} when it has none */
    public @Nullable UUID getId() {
        return id;
    }

    /**
     * The stored content item. This is parsed once per instance and cached.
     *
     * @return a copy of the content, or {@code null} for an empty box
     */
    public @Nullable ItemStack getContentItemStack() {
        if (!contentLoaded) {
            contentLoaded = true;
            if (parsedBox != null) {
                content = parsedBox.getComponentItemStack();
            } else if (type != null && itemStack != null) {
                StorageBox box = StorageBox.getStorageBox(itemStack);
                content = box == null ? new ItemStack(type) : box.getComponentItemStack();
            }
        }
        if (content == null || content.getType().isAir()) return null;
        return content.clone();
    }

    /**
     * Whether the box holds the plain item of its material (no custom name, lore, enchantments...).
     * Useful for plugins that spend the box's items as ordinary blocks.
     */
    public boolean isPlainContent() {
        if (type == null) return false;
        ItemStack stored = getContentItemStack();
        return stored != null && stored.getType() == type && ItemNormalizer.isSimilar(stored, new ItemStack(type));
    }

    /** Old boxes store their display name and lore; use that as a cheap pre-check before the full parser. */
    private static boolean looksLikeStorageBox(ItemStack itemStack) {
        if (!itemStack.hasItemMeta()) return false;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return false;
        if (meta.hasDisplayName()) {
            String name = ChatColor.stripColor(meta.getDisplayName());
            if (name != null && name.startsWith("Storage Box")) return true;
        }
        List<String> lore = meta.hasLore() ? meta.getLore() : null;
        if (lore != null) {
            for (String line : lore) {
                String stripped = ChatColor.stripColor(line);
                if (stripped != null && stripped.trim().startsWith("ID:")) return true;
            }
        }
        return false;
    }

    // ---- persistent data access ----
    // ItemStack#getPersistentDataContainer() only exists on Paper/newer Bukkit APIs, and it returns a
    // read-only view (not a PersistentDataContainer). Reflection keeps this class working on 1.15.2 and
    // avoids copying the whole ItemMeta on modern servers.

    private static @Nullable Method findItemPdcMethod() {
        try {
            return ItemStack.class.getMethod("getPersistentDataContainer");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object persistentData(ItemStack itemStack) {
        Method method = ITEM_PDC_METHOD;
        if (method != null) {
            try {
                Object value = method.invoke(itemStack);
                if (value != null) return value;
            } catch (Throwable ignored) {
                // fall back to the ItemMeta view below
            }
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }

    private static @Nullable String getString(@Nullable Object pdc, NamespacedKey key) {
        return get(pdc, key, PersistentDataType.STRING);
    }

    private static @Nullable Long getLong(@Nullable Object pdc, NamespacedKey key) {
        return get(pdc, key, PersistentDataType.LONG);
    }

    private static @Nullable Byte getByte(@Nullable Object pdc, NamespacedKey key) {
        return get(pdc, key, PersistentDataType.BYTE);
    }

    @SuppressWarnings("unchecked")
    private static <T, Z> @Nullable Z get(@Nullable Object pdc, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (pdc == null) return null;
        if (pdc instanceof PersistentDataContainer) {
            return ((PersistentDataContainer) pdc).get(key, type);
        }
        try {
            Method method = viewGetMethod;
            if (method == null || !method.getDeclaringClass().isInstance(pdc)) {
                method = pdc.getClass().getMethod("get", NamespacedKey.class, PersistentDataType.class);
                method.setAccessible(true);
                viewGetMethod = method;
            }
            return (Z) method.invoke(pdc, key, type);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable UUID parseUUID(@Nullable String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

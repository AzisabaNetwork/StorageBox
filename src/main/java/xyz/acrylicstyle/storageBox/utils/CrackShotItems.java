package xyz.acrylicstyle.storageBox.utils;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Repairs CrackShot items stored in a Storage Box that lost their display name.
 * <p>
 * Contents converted from 1.15.2 boxes (and by older versions of this plugin) may keep only the
 * {@code crackshot:weapon_id} tag and the lore: the weapon's name is gone. Such an item is no longer the same
 * as the weapon CrackShot gives out today, so it neither auto-collects nor merges, and players see a plain item.
 * The weapon id still tells which weapon it was, so the box holds the item CrackShot generates for it instead.
 */
final class CrackShotItems {
    @SuppressWarnings("deprecation")
    private static final NamespacedKey KEY_WEAPON_ID = new NamespacedKey("crackshot", "weapon_id");
    private static final String[] UTILITY_CLASSES = {
            "net.azisaba.crackshot.CSUtility",
            "com.shampaggon.crackshot.CSUtility",
    };

    private static volatile @Nullable Plugin cachedPlugin;
    private static volatile @Nullable Object utility;
    private static volatile @Nullable Method generateWeapon;

    private CrackShotItems() {}

    /** The CrackShot weapon id of the item, or null when it has none. */
    static @Nullable String weaponId(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String id = meta.getPersistentDataContainer().get(KEY_WEAPON_ID, PersistentDataType.STRING);
            return id == null || id.trim().isEmpty() ? null : id.trim();
        } catch (RuntimeException e) {
            return null; // stored with another type
        }
    }

    /**
     * The weapon CrackShot generates today for a stored CrackShot item that lost its display name.
     *
     * @return the repaired item (amount 1), or null when the item is not such an item or it cannot be repaired
     *         (CrackShot missing, unknown weapon, not on the main thread...)
     */
    static @Nullable ItemStack repairNameless(@Nullable ItemStack item) {
        String id = weaponId(item);
        if (id == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.hasDisplayName()) return null;
        if (!Bukkit.isPrimaryThread()) return null;
        ItemStack generated = generate(id);
        if (generated == null || generated.getType() != item.getType()) return null;
        ItemMeta generatedMeta = generated.getItemMeta();
        if (generatedMeta == null || !generatedMeta.hasDisplayName()) return null;
        if (!id.equals(weaponId(generated))) return null;
        generated.setAmount(1);
        return generated;
    }

    private static @Nullable ItemStack generate(@NotNull String weaponId) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CrackShot");
        if (plugin == null || !plugin.isEnabled()) return null;
        try {
            Method method = generateWeapon;
            Object util = utility;
            if (plugin != cachedPlugin || method == null || util == null) {
                Class<?> clazz = null;
                for (String name : UTILITY_CLASSES) {
                    try {
                        clazz = Class.forName(name, true, plugin.getClass().getClassLoader());
                        break;
                    } catch (ClassNotFoundException ignored) {}
                }
                if (clazz == null) return null;
                util = clazz.getConstructor().newInstance();
                method = clazz.getMethod("generateWeapon", String.class);
                utility = util;
                generateWeapon = method;
                cachedPlugin = plugin;
            }
            Object result = method.invoke(util, weaponId);
            if (!(result instanceof ItemStack) || ((ItemStack) result).getType().isAir()) return null;
            return ((ItemStack) result).clone();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bukkit.getLogger().warning("[StorageBox] Could not generate the CrackShot weapon " + weaponId + ": " + e);
            return null;
        }
    }
}

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
    private static volatile @Nullable Method updateWeapon;

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

    /**
     * The same weapon rebuilt for CrackShot's current weapon configuration, when the item is a CrackShot weapon made
     * from an older configuration.
     * <p>
     * CrackShot (net.azisaba) rebuilds such weapons in players' inventories and stamps every weapon with a
     * fingerprint of its configuration ({@code crackshot:spec}). The item stored in a box is not rebuilt by CrackShot,
     * so without this it would no longer be similar to the weapons players carry, and they would neither
     * auto-collect nor merge.
     *
     * @return the rebuilt item (amount 1), or null when the item is up to date, not a CrackShot weapon, or CrackShot
     *         cannot rebuild it (CrackShot missing or too old, not on the main thread...)
     */
    static @Nullable ItemStack updateOutdated(@Nullable ItemStack item) {
        if (weaponId(item) == null || !Bukkit.isPrimaryThread()) return null;
        Object util = utility();
        Method method = updateWeapon;
        if (util == null || method == null) return null;
        try {
            Object result = method.invoke(util, item.clone());
            if (!(result instanceof ItemStack) || ((ItemStack) result).getType().isAir()) return null;
            ItemStack updated = ((ItemStack) result).clone();
            updated.setAmount(1);
            return updated;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bukkit.getLogger().warning("[StorageBox] Could not update the CrackShot weapon " + weaponId(item) + ": " + e);
            return null;
        }
    }

    private static @Nullable ItemStack generate(@NotNull String weaponId) {
        Object util = utility();
        Method method = generateWeapon;
        if (util == null || method == null) return null;
        try {
            Object result = method.invoke(util, weaponId);
            if (!(result instanceof ItemStack) || ((ItemStack) result).getType().isAir()) return null;
            return ((ItemStack) result).clone();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bukkit.getLogger().warning("[StorageBox] Could not generate the CrackShot weapon " + weaponId + ": " + e);
            return null;
        }
    }

    /** CrackShot's CSUtility, looking up its methods again when CrackShot was reloaded. Null without CrackShot. */
    private static @Nullable Object utility() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CrackShot");
        if (plugin == null || !plugin.isEnabled()) return null;
        if (plugin == cachedPlugin && utility != null) return utility;
        try {
            Class<?> clazz = null;
            for (String name : UTILITY_CLASSES) {
                try {
                    clazz = Class.forName(name, true, plugin.getClass().getClassLoader());
                    break;
                } catch (ClassNotFoundException ignored) {}
            }
            if (clazz == null) return null;
            Object util = clazz.getConstructor().newInstance();
            generateWeapon = clazz.getMethod("generateWeapon", String.class);
            Method update;
            try {
                update = clazz.getMethod("updateWeapon", ItemStack.class);
            } catch (NoSuchMethodException e) {
                update = null; // CrackShot without automatic weapon updates
            }
            updateWeapon = update;
            utility = util;
            cachedPlugin = plugin;
            return util;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Bukkit.getLogger().warning("[StorageBox] Could not hook into CrackShot: " + e);
            return null;
        }
    }
}

package xyz.acrylicstyle.storageBox.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Upgrades Storage Box data written on 1.15.2.
 * <p>
 * A 1.15.2 box keeps the content item's NBT ({@code storageBoxTag}) in the 1.15.2 item format (display.Name as JSON,
 * Enchantments list, Damage, AttributeModifiers, SkullOwner, MYTHIC_TYPE...). Minecraft's DataFixer never sees it
 * because it is plugin data inside {@code minecraft:custom_data}, and it is neither a component map nor a 1.21
 * custom_data compound, so the content item came out broken. This runs the tag through the server's own DataFixer
 * ({@code ItemStack.deserializeBytes}), exactly as if the item had been stored in a 1.15.2 chunk.
 */
public final class LegacyComponentUpgrader {
    /** Minecraft 1.15.2 */
    private static final int LEGACY_DATA_VERSION = 2230;

    /** Root keys of a 1.15.2 item tag that are vanilla data; a 1.21 custom_data or component map never has them. */
    private static final Set<String> LEGACY_VANILLA_KEYS = Set.of(
            "display", "Enchantments", "StoredEnchantments", "Damage", "RepairCost", "Unbreakable", "HideFlags",
            "AttributeModifiers", "CustomModelData", "SkullOwner", "Potion", "CustomPotionEffects",
            "CustomPotionColor", "BlockEntityTag", "EntityTag", "Fireworks", "Explosion", "pages", "author", "title",
            "generation", "CanDestroy", "CanPlaceOn", "map", "BucketVariantTag", "ChargedProjectiles", "Charged");

    /** 1.15.2 material names that no longer exist (or were renamed) on 1.21.11. */
    private static final Map<String, String> RENAMED_MATERIALS = Map.of(
            "GRASS", "SHORT_GRASS",
            "GRASS_PATH", "DIRT_PATH",
            "SCUTE", "TURTLE_SCUTE",
            "CHAIN", "IRON_CHAIN");

    private static final int CACHE_SIZE = 256;
    private static final Map<CacheKey, ItemStack> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, ItemStack> eldest) {
                    return size() > CACHE_SIZE;
                }
            });
    /** Tags that could not be upgraded; not retried (nor logged again) until restart. */
    private static final Set<CacheKey> FAILED = Collections.synchronizedSet(new HashSet<>());

    private LegacyComponentUpgrader() {}

    /**
     * Resolves a material name stored by any StorageBox version, including 1.15.2 names that were renamed since.
     *
     * @return the material, AIR for "null"/empty
     * @throws IllegalArgumentException if the name is unknown (same as {@link Material#valueOf})
     */
    public static @NotNull Material resolveMaterial(@Nullable String name) {
        if (name == null) return Material.AIR;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("MINECRAFT:")) upper = upper.substring("MINECRAFT:".length());
        if (upper.isEmpty() || upper.equals("NULL")) return Material.AIR;
        Material material = Material.getMaterial(upper);
        if (material != null) return material;
        String renamed = RENAMED_MATERIALS.get(upper);
        if (renamed != null && (material = Material.getMaterial(renamed)) != null) return material;
        material = Material.matchMaterial(upper);
        if (material != null) return material;
        throw new IllegalArgumentException("Unknown material: " + name);
    }

    /** True if the tag is the NBT of a 1.15.2 item. */
    public static boolean isLegacyItemTag(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return false;
        for (String key : tag.keySet()) {
            if (LEGACY_VANILLA_KEYS.contains(key)) return true;
        }
        return false;
    }

    /**
     * Converts the content of a 1.15.2 Storage Box into a current item through the server's DataFixer.
     *
     * @return the upgraded item (amount 1), or null when it cannot be done (the caller then falls back)
     */
    public static @Nullable ItemStack upgrade(@NotNull Material type, @NotNull CompoundTag legacyTag) {
        if (type.isAir()) return null;
        CacheKey key = new CacheKey(type, legacyTag);
        ItemStack cached = CACHE.get(key);
        if (cached != null) return withMythicId(cached.clone());
        if (FAILED.contains(key)) return null;

        CompoundTag tag = legacyTag.copy();
        fixLegacyAttributeNames(tag, 0);
        CompoundTag root = new CompoundTag();
        root.putString("id", type.getKey().toString());
        root.putByte("Count", (byte) 1);
        root.put("tag", tag);
        root.putInt("DataVersion", LEGACY_DATA_VERSION);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, bytes);
            ItemStack item = ItemStack.deserializeBytes(bytes.toByteArray());
            if (item.getType().isAir()) {
                fail(key, type, "the DataFixer returned no item");
                return null;
            }
            item.setAmount(1);
            CACHE.put(key, item.clone());
            return withMythicId(item);
        } catch (IOException | RuntimeException e) {
            fail(key, type, e.toString());
            return null;
        }
    }

    private static void fail(CacheKey key, Material type, String reason) {
        if (FAILED.add(key)) {
            Bukkit.getLogger().warning("[StorageBox] Could not upgrade the 1.15.2 content of a Storage Box of "
                    + type + " (" + reason + ")");
        }
    }

    /**
     * 1.15.2 attribute names were camelCase ("generic.attackDamage"), which Paper 1.21.11's DataFixer rejects together
     * with the whole item. They are rewritten to the 1.16 form the later fixes understand.
     */
    static void fixLegacyAttributeNames(Tag node, int depth) {
        if (depth > 32) return;
        if (node instanceof CompoundTag compound) {
            if (compound.get("AttributeModifiers") instanceof ListTag modifiers) {
                for (Tag element : modifiers) {
                    if (element instanceof CompoundTag modifier) {
                        modifier.getString("AttributeName").ifPresent(name ->
                                modifier.put("AttributeName", StringTag.valueOf(legacyAttributeKey(name))));
                    }
                }
            }
            for (String child : compound.keySet()) fixLegacyAttributeNames(compound.get(child), depth + 1);
        } else if (node instanceof ListTag list) {
            for (Tag value : list) fixLegacyAttributeNames(value, depth + 1);
        }
    }

    static @NotNull String legacyAttributeKey(@NotNull String raw) {
        String trimmed = raw.trim();
        String path = trimmed.substring(trimmed.indexOf(':') + 1);
        return "minecraft:" + path.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /**
     * Lets MythicItemIdCompat (if installed) add the MythicMobs 5 ID next to the MythicMobs 4 MYTHIC_TYPE tag, so the
     * item taken out of the box is a working Mythic item right away. Only on the main thread.
     */
    private static @NotNull ItemStack withMythicId(@NotNull ItemStack item) {
        if (!Bukkit.isPrimaryThread()) return item;
        Plugin compat = Bukkit.getPluginManager().getPlugin("MythicItemIdCompat");
        if (compat == null || !compat.isEnabled()) return item;
        try {
            compat.getClass().getMethod("preserveMythicId", ItemStack.class).invoke(compat, item);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // older MythicItemIdCompat without the API: it converts the item once it is in an inventory
        }
        return item;
    }

    private record CacheKey(Material type, CompoundTag tag) {
        CacheKey(Material type, CompoundTag tag) {
            this.type = type;
            this.tag = tag.copy();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CacheKey other && type == other.type && tag.equals(other.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, tag);
        }
    }
}

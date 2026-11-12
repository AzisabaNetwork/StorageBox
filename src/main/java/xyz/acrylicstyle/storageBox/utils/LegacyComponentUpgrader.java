package xyz.acrylicstyle.storageBox.utils;

import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTNumber;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.serializer.DefaultNBTSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Upgrades Storage Box data written on 1.15.2.
 * <p>
 * A 1.15.2 box keeps the content item's NBT ({@code storageBoxTag}) in the 1.15.2 item format (display.Name as JSON,
 * Enchantments list, Damage, AttributeModifiers, SkullOwner...). Minecraft's DataFixer never sees it because it is
 * plugin data inside {@code minecraft:custom_data}, so the content item used to be rebuilt by hand, which lost
 * everything but the name, lore, damage, model data and enchantments and left the raw 1.15.2 tag in the item's
 * custom_data. This runs the tag through the server's own DataFixer instead ({@code ItemStack.deserializeBytes}),
 * exactly as if the item had been stored in a 1.15.2 chunk.
 */
public final class LegacyComponentUpgrader {
    /** Minecraft 1.15.2 */
    private static final int LEGACY_DATA_VERSION = 2230;

    /** Root keys of a 1.15.2 item tag that are vanilla data; a modern custom_data never contains them. */
    private static final Set<String> LEGACY_VANILLA_KEYS = new HashSet<>(Arrays.asList(
            "display", "Enchantments", "StoredEnchantments", "Damage", "RepairCost", "Unbreakable", "HideFlags",
            "AttributeModifiers", "CustomModelData", "SkullOwner", "Potion", "CustomPotionEffects",
            "CustomPotionColor", "BlockEntityTag", "EntityTag", "Fireworks", "Explosion", "pages", "author", "title",
            "generation", "CanDestroy", "CanPlaceOn", "map", "BucketVariantTag", "ChargedProjectiles", "Charged"));

    /** 1.15.2 material names that no longer exist (or were renamed) on 1.21.x. */
    private static final Map<String, String> RENAMED_MATERIALS;

    static {
        Map<String, String> renamed = new HashMap<>();
        renamed.put("GRASS", "SHORT_GRASS");
        renamed.put("GRASS_PATH", "DIRT_PATH");
        renamed.put("SCUTE", "TURTLE_SCUTE");
        renamed.put("CHAIN", "IRON_CHAIN");
        RENAMED_MATERIALS = Collections.unmodifiableMap(renamed);
    }

    /** 1.15.2 attribute names were camelCase ("generic.attackDamage"); later versions reject them. */
    private static final Pattern CAMEL_CASE = Pattern.compile("([a-z0-9])([A-Z])");

    private static final int CACHE_SIZE = 256;
    private static final Map<CacheKey, ItemStack> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<CacheKey, ItemStack>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, ItemStack> eldest) {
                    return size() > CACHE_SIZE;
                }
            });
    /** Tags that could not be upgraded; not retried (and not logged again) until restart. */
    private static final Set<CacheKey> FAILED = Collections.synchronizedSet(new HashSet<>());

    private static volatile boolean deserializeChecked = false;
    private static volatile @Nullable Method deserializeBytes;

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
        if (renamed != null) {
            material = Material.getMaterial(renamed);
            if (material != null) return material;
        }
        material = Material.matchMaterial(upper);
        if (material != null) return material;
        throw new IllegalArgumentException("Unknown material: " + name);
    }

    /** True if the tag is the NBT of a 1.15.2 item rather than a modern custom_data compound. */
    public static boolean isLegacyItemTag(@Nullable NBTCompound tag) {
        if (tag == null || tag.isEmpty()) return false;
        for (String key : tag.getTagNames()) {
            if (LEGACY_VANILLA_KEYS.contains(key)) return true;
        }
        return false;
    }

    /**
     * Converts the content of a 1.15.2 Storage Box into a current item through the server's DataFixer.
     *
     * @return the upgraded item (amount 1), or null when the server cannot do it (the caller then falls back)
     */
    public static @Nullable ItemStack upgrade(@NotNull Material type, @NotNull NBTCompound legacyTag) {
        if (type.isAir()) return null;
        Method method = deserializeBytesMethod();
        if (method == null) return null;
        CacheKey key = new CacheKey(type, legacyTag);
        ItemStack cached = CACHE.get(key);
        if (cached != null) return ItemNormalizer.normalize(withMythicId(cached.clone()));
        if (FAILED.contains(key)) return null;

        NBTCompound tag = legacyTag.copy();
        fixLegacyAttributeNames(tag, 0);

        NBTCompound root = new NBTCompound();
        root.setTag("id", new NBTString(type.getKey().toString()));
        root.setTag("Count", new NBTByte((byte) 1));
        root.setTag("tag", tag);
        root.setTag("DataVersion", new NBTInt(LEGACY_DATA_VERSION));
        try {
            ItemStack item = (ItemStack) method.invoke(null, (Object) writeCompressed(root));
            if (item == null || item.getType().isAir()) {
                fail(key, type, "the DataFixer returned no item");
                return null;
            }
            // the same 1.15.2 item must come out the same here, in AziSync, EnderChestPlus and ItemStash
            NBT hideFlags = legacyTag.getTagOrNull("HideFlags");
            item = ItemNormalizer.normalize(item, hideFlags instanceof NBTNumber ? ((NBTNumber) hideFlags).getAsInt() : 0);
            item.setAmount(1);
            CACHE.put(key, item.clone());
            return ItemNormalizer.normalize(withMythicId(item)); // again: drops MYTHIC_TYPE once the MMID is set
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
            fail(key, type, cause == null ? String.valueOf(e) : cause.toString());
            return null;
        }
    }

    private static void fail(CacheKey key, Material type, String reason) {
        if (FAILED.add(key)) {
            Bukkit.getLogger().warning("[StorageBox] Could not upgrade the 1.15.2 content of a Storage Box of "
                    + type + " (" + reason + "); using the old conversion.");
        }
    }

    /** 1.15.2 attribute names ("generic.attackDamage") are rewritten to the 1.16 form the later fixes expect. */
    private static void fixLegacyAttributeNames(NBT node, int depth) {
        if (depth > 32) return;
        if (node instanceof NBTCompound) {
            NBTCompound compound = (NBTCompound) node;
            NBT modifiers = compound.getTagOrNull("AttributeModifiers");
            if (modifiers instanceof NBTList) {
                for (Object element : ((NBTList<?>) modifiers).getTags()) {
                    if (!(element instanceof NBTCompound)) continue;
                    NBTCompound modifier = (NBTCompound) element;
                    String name = modifier.getStringTagValueOrNull("AttributeName");
                    if (name != null) modifier.setTag("AttributeName", new NBTString(legacyAttributeKey(name)));
                }
            }
            for (NBT value : compound.getTags().values()) fixLegacyAttributeNames(value, depth + 1);
        } else if (node instanceof NBTList) {
            for (Object value : ((NBTList<?>) node).getTags()) {
                if (value instanceof NBT) fixLegacyAttributeNames((NBT) value, depth + 1);
            }
        }
    }

    static @NotNull String legacyAttributeKey(@NotNull String raw) {
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        String path = colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
        Matcher matcher = CAMEL_CASE.matcher(path);
        return "minecraft:" + matcher.replaceAll("$1_$2").toLowerCase(Locale.ROOT);
    }

    private static byte[] writeCompressed(NBTCompound root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            DefaultNBTSerializer.INSTANCE.serializeTag(out, root, true);
        }
        return bytes.toByteArray();
    }

    private static @Nullable Method deserializeBytesMethod() {
        if (!deserializeChecked) {
            try {
                deserializeBytes = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            } catch (NoSuchMethodException e) {
                deserializeBytes = null; // not Paper
            }
            deserializeChecked = true;
        }
        return deserializeBytes;
    }

    /**
     * Lets MythicItemIdCompat (if installed) add the MythicMobs 5 ID next to the MythicMobs 4 MYTHIC_TYPE tag,
     * so the item taken out of the box is a working Mythic item right away. Only on the main thread.
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

    /** MMIDs resolved per legacy tag ("" when the content is not a MythicMobs item). */
    private static final Map<CacheKey, String> MYTHIC_IDS = Collections.synchronizedMap(
            new LinkedHashMap<CacheKey, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    /**
     * The current MythicMobs item that a 1.15.2 Storage Box content stands for.
     * <p>
     * Many MythicMobs 4 items never carried {@code MYTHIC_TYPE}, so the upgraded content is not a Mythic item at all.
     * MythicItemIdCompat finds its MMID (old tag, or the single 1.15.2 definition with that material, name and lore)
     * and the box then holds the item MythicMobs generates today, so it stacks and auto-collects with new drops.
     *
     * @param legacyTag the 1.15.2 content tag (cache key)
     * @param upgraded  the content as converted by the DataFixer
     * @return the current Mythic item (amount 1), or null when it is not a Mythic item or this cannot be decided
     *         (MythicItemIdCompat 1.5.0+ missing, or not on the main thread)
     */
    public static @Nullable ItemStack currentMythicItem(@NotNull Material type, @NotNull NBTCompound legacyTag,
                                                        @NotNull ItemStack upgraded) {
        if (!Bukkit.isPrimaryThread()) return null;
        Plugin compat = Bukkit.getPluginManager().getPlugin("MythicItemIdCompat");
        if (compat == null || !compat.isEnabled()) return null;
        try {
            CacheKey key = new CacheKey(type, legacyTag);
            String id = MYTHIC_IDS.get(key);
            if (id == null) {
                Object resolved = compat.getClass().getMethod("resolveMythicId", ItemStack.class).invoke(compat, upgraded);
                id = resolved instanceof String ? (String) resolved : "";
                MYTHIC_IDS.put(key, id);
            }
            if (id.isEmpty()) return null;
            Object current = compat.getClass().getMethod("currentMythicItem", String.class).invoke(compat, id);
            if (!(current instanceof ItemStack) || ((ItemStack) current).getType().isAir()) return null;
            ItemStack item = ((ItemStack) current).clone();
            item.setAmount(1);
            return item;
        } catch (NoSuchMethodException e) {
            return null; // MythicItemIdCompat older than 1.5.0
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bukkit.getLogger().warning("[StorageBox] Could not look up the MythicMobs item of a 1.15.2 Storage Box: " + e);
            return null;
        }
    }

    private static final class CacheKey {
        private final Material type;
        private final NBTCompound tag;
        private final int hash;

        CacheKey(Material type, NBTCompound tag) {
            this.type = type;
            this.tag = tag.copy();
            this.hash = Objects.hash(type, this.tag);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;
            CacheKey other = (CacheKey) o;
            return type == other.type && tag.equals(other.tag);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}

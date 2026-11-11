package xyz.acrylicstyle.storageBox.utils;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.nbt.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.acrylicstyle.storageBox.StorageBoxPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class StorageBox {
    @SuppressWarnings("deprecation")
    public static final NamespacedKey KEY_TYPE = new NamespacedKey("storagebox", "type");
    @SuppressWarnings("deprecation")
    public static final NamespacedKey KEY_AMOUNT = new NamespacedKey("storagebox", "amount");
    @SuppressWarnings("deprecation")
    public static final NamespacedKey KEY_AUTO_COLLECT = new NamespacedKey("storagebox", "autocollect");
    @SuppressWarnings("deprecation")
    public static final NamespacedKey KEY_AUTO_BUY = new NamespacedKey("storagebox", "autobuy");
    @SuppressWarnings("deprecation")
    public static final NamespacedKey KEY_UUID = new NamespacedKey("storagebox", "uuid");
    @SuppressWarnings("deprecation")
    public static final NamespacedKey KEY_COMPONENT_DATA = new NamespacedKey("storagebox", "component_data");
    @SuppressWarnings("deprecation")
    private static final NamespacedKey KEY_MYTHIC_TYPE = new NamespacedKey("mythicmobs", "type");
    @SuppressWarnings("deprecation")
    private static final NamespacedKey KEY_SOULBOUND = new NamespacedKey("soulbound", "soulbound");

    private static final Set<Material> opaqueExempt = new HashSet<>(Arrays.asList(
            Material.COAL, Material.CHARCOAL, Material.DIAMOND, Material.EMERALD, Material.STICK, Material.DEBUG_STICK,
            Material.SUGAR, Material.STRING, Material.LAPIS_LAZULI, Material.WHEAT_SEEDS, Material.REDSTONE,
            Material.GLOWSTONE_DUST, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM
    ));

    static {
        opaqueExempt.addAll(Arrays.stream(Material.values()).filter(m -> m.name().endsWith("_DYE")).collect(Collectors.toList()));
        opaqueExempt.addAll(Arrays.stream(Material.values()).filter(m -> m.name().endsWith("_INGOT")).collect(Collectors.toList()));
    }

    private boolean autoCollect;
    private boolean autoBuy;
    private boolean autoBuyConfigured;
    private @Nullable Material type;
    private long amount;
    private @Nullable NBTCompound tag;
    /** serializedComponent has been replaced by the DataFixer conversion of a 1.15.2 tag (see getComponentItemStack) */
    private boolean legacyComponentUpgraded;
    private @Nullable String serializedComponent;
    private final @Nullable UUID randomUUID;

    public StorageBox(@Nullable Material type, long amount) {
        this(type, amount, true, false, false, null, null, null);
    }

    public StorageBox(@Nullable Material type, long amount, boolean autoCollect, @Nullable UUID randomUUID) {
        this(type, amount, autoCollect, false, false, null, null, randomUUID);
    }

    public StorageBox(@Nullable Material type, long amount, boolean autoCollect, boolean autoBuy, boolean autoBuyConfigured, @Nullable NBTCompound tag, @Nullable UUID randomUUID) {
        this(type, amount, autoCollect, autoBuy, autoBuyConfigured, tag, null, randomUUID);
    }

    public StorageBox(@Nullable Material type, long amount, boolean autoCollect, boolean autoBuy, boolean autoBuyConfigured, @Nullable NBTCompound tag, @Nullable String serializedComponent, @Nullable UUID randomUUID) {
        this.type = type;
        this.amount = amount;
        this.autoCollect = autoCollect;
        this.autoBuy = autoBuy;
        this.autoBuyConfigured = autoBuyConfigured;
        this.tag = tag;
        this.serializedComponent = serializedComponent;
        this.randomUUID = randomUUID;
    }

    public static @Nullable StorageBox getStorageBox(@NotNull ItemStack itemStack) {
        try {
            if (itemStack.getType().isAir()) return null;
            if (itemStack.hasItemMeta()) {
                ItemMeta meta = itemStack.getItemMeta();
                if (meta != null) {
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    if (pdc.has(KEY_TYPE, PersistentDataType.STRING)) {
                        String s = pdc.get(KEY_TYPE, PersistentDataType.STRING);
                        Material type = LegacyComponentUpgrader.resolveMaterial(s);

                        Long amountVal = pdc.get(KEY_AMOUNT, PersistentDataType.LONG);
                        long amount = amountVal != null ? amountVal : 0L;

                        Byte autoCollectVal = pdc.get(KEY_AUTO_COLLECT, PersistentDataType.BYTE);
                        boolean autoCollect = autoCollectVal == null || autoCollectVal != 0;

                        boolean autoBuyConfigured = pdc.has(KEY_AUTO_BUY, PersistentDataType.BYTE);
                        boolean autoBuy = false;
                        if (autoBuyConfigured) {
                            Byte autoBuyVal = pdc.get(KEY_AUTO_BUY, PersistentDataType.BYTE);
                            autoBuy = autoBuyVal != null && autoBuyVal != 0;
                        }

                        String uuidStr = pdc.get(KEY_UUID, PersistentDataType.STRING);
                        UUID randomUUID = uuidStr != null ? UUID.fromString(uuidStr) : null;

                        String serializedComponent = pdc.get(KEY_COMPONENT_DATA, PersistentDataType.STRING);

                        NBTCompound storageBoxTag = null;
                        com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(itemStack);
                        if (peItem != null) {
                            NBTCompound peNBT = peItem.getNBT();
                            if (peNBT == null) {
                                try {
                                    peNBT = peItem.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
                                } catch (Throwable ignored) {}
                            }
                            if (peNBT != null) {
                                storageBoxTag = extractStorageBoxTag(peNBT);
                            }
                        }

                        return new StorageBox(type, amount, autoCollect, autoBuy, autoBuyConfigured, storageBoxTag, serializedComponent, randomUUID);
                    }
                }
            }

            com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(itemStack);
            if (peItem == null) return fromLegacyLore(itemStack);
            NBTCompound tag = peItem.getNBT();
            if (tag == null) {
                try {
                    tag = peItem.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
                } catch (Throwable ignored) {}
            }
            if (tag == null) return fromLegacyLore(itemStack);

            NBTCompound rootTag = tag;
            if (tag.getTagOrNull("storageBoxType") == null) {
                NBTCompound customData = tag.getCompoundTagOrNull("minecraft:custom_data");
                if (customData == null) customData = tag.getCompoundTagOrNull("custom_data");
                if (customData != null && customData.getTagOrNull("storageBoxType") != null) {
                    rootTag = customData;
                }
            }

            if (rootTag.getTagOrNull("storageBoxType") == null) {
                return fromLegacyLore(itemStack);
            }

            String s = rootTag.getStringTagValueOrNull("storageBoxType");
            // 1.15.2 boxes may name a material that was renamed since (GRASS -> SHORT_GRASS...)
            Material type = LegacyComponentUpgrader.resolveMaterial(s);

            long amount = 0;
            NBT amountNBT = rootTag.getTagOrNull("storageBoxAmount");
            if (amountNBT instanceof NBTNumber) {
                amount = ((NBTNumber) amountNBT).getAsLong();
            }
            if (amount <= 0 && itemStack.hasItemMeta() && itemStack.getItemMeta() != null && itemStack.getItemMeta().hasDisplayName()) {
                String name = ChatColor.stripColor(itemStack.getItemMeta().getDisplayName());
                if (name != null) {
                    int lastLt = name.lastIndexOf('<');
                    int lastGt = name.lastIndexOf('>');
                    if (lastLt != -1 && lastGt != -1 && lastGt > lastLt) {
                        try {
                            long parsed = Long.parseLong(name.substring(lastLt + 1, lastGt).trim());
                            if (parsed > 0) {
                                amount = parsed;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            boolean autoCollect = true;
            NBT autoCollectNBT = rootTag.getTagOrNull("storageBoxAutoCollect");
            if (autoCollectNBT instanceof NBTNumber) {
                autoCollect = ((NBTNumber) autoCollectNBT).getAsByte() != 0;
            }

            boolean autoBuyConfigured = rootTag.getTagOrNull("storageBoxAutoBuy") != null;
            boolean autoBuy = false;
            if (autoBuyConfigured) {
                NBT autoBuyNBT = rootTag.getTagOrNull("storageBoxAutoBuy");
                if (autoBuyNBT instanceof NBTNumber) {
                    autoBuy = ((NBTNumber) autoBuyNBT).getAsByte() != 0;
                }
            }

            NBTCompound storageBoxTag = extractStorageBoxTag(rootTag);
            String uuidStr = rootTag.getStringTagValueOrNull("randomUUID");
            UUID randomUUID = uuidStr != null ? UUID.fromString(uuidStr) : null;
            return new StorageBox(type, amount, autoCollect, autoBuy, autoBuyConfigured, storageBoxTag, null, randomUUID);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Recovers a Storage Box whose NBT was lost (e.g. old 1.15.2 items migrated without their custom tags)
     * from its display name and lore. Requires the "Storage Box" name and an "ID: &lt;uuid&gt;" lore line,
     * which survival players cannot forge.
     */
    @SuppressWarnings("deprecation")
    private static @Nullable StorageBox fromLegacyLore(@NotNull ItemStack itemStack) {
        if (!itemStack.hasItemMeta()) return null;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) return null;
        String name = ChatColor.stripColor(meta.getDisplayName());
        if (name == null || !name.startsWith("Storage Box")) return null;
        List<String> lore = meta.getLore();
        if (lore == null) return null;

        UUID randomUUID = null;
        Long amount = null;
        Material type = null;
        Boolean opaque = null;
        boolean autoCollect = true;
        boolean autoBuy = false;
        boolean autoBuyConfigured = false;
        for (String rawLine : lore) {
            String line = ChatColor.stripColor(rawLine);
            if (line == null) continue;
            line = line.trim();
            try {
                if (line.startsWith("ID:")) {
                    randomUUID = UUID.fromString(line.substring(3).trim());
                } else if (line.startsWith("数:")) {
                    amount = Long.parseLong(line.substring(2).trim());
                } else if (line.startsWith("自動回収:")) {
                    autoCollect = Boolean.parseBoolean(line.substring(5).trim());
                } else if (line.startsWith("自動購入:")) {
                    autoBuyConfigured = true;
                    autoBuy = Boolean.parseBoolean(line.substring(5).trim());
                } else if (line.startsWith("不透明:")) {
                    // "不透明: <bool> (<box material>)": the material is the box's own item. It equals the
                    // content only when the box is not opaque; an opaque box is always a STICK.
                    String value = line.substring(4).trim();
                    opaque = value.startsWith("true");
                    int lp = line.lastIndexOf('('), rp = line.lastIndexOf(')');
                    if (!opaque && lp != -1 && rp > lp) {
                        type = Material.matchMaterial(line.substring(lp + 1, rp).trim());
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }
        if (randomUUID == null) return null;

        if (amount == null) {
            int lastLt = name.lastIndexOf('<'), lastGt = name.lastIndexOf('>');
            if (lastLt != -1 && lastGt > lastLt) {
                try {
                    amount = Long.parseLong(name.substring(lastLt + 1, lastGt).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        Material boxMaterial = itemStack.getType();
        boolean emptyBox = boxMaterial == Material.BARRIER; // a box without content type is a barrier
        if (type == null && !emptyBox) {
            // a non-opaque box shows its content; a STICK may be either a box of sticks or an opaque box
            boolean boxIsContent = !Boolean.TRUE.equals(opaque) && boxMaterial != Material.STICK;
            type = boxIsContent ? boxMaterial : materialFromBoxName(name);
        }
        if (type == null && !emptyBox) {
            // The content cannot be told from the lore (e.g. a Mythic item with its own name). Do not guess:
            // treating it as a box of sticks would hand out sticks and destroy the real items.
            Bukkit.getLogger().warning("[StorageBox] Could not recover the content type of legacy Storage Box "
                    + randomUUID + " (\"" + name + "\"); it needs its NBT restored.");
            return null;
        }
        return new StorageBox(type, amount == null ? 0 : amount, autoCollect, autoBuy, autoBuyConfigured, null, null, randomUUID);
    }

    /**
     * The content material named in "Storage Box [&lt;name&gt;] &lt;amount&gt;", when exactly one material has that
     * name (its English name as {@link #getComponentItemStackName()} formats it, or its translation).
     * Null for custom item names and for anything ambiguous.
     */
    static @Nullable Material materialFromBoxName(@NotNull String strippedName) {
        int start = strippedName.indexOf('[');
        int end = strippedName.lastIndexOf(']');
        if (start == -1 || end <= start) return null;
        String inner = strippedName.substring(start + 1, end).trim();
        if (inner.isEmpty()) return null;
        Material found = null;
        for (Material material : Material.values()) {
            if (isLegacyMaterial(material) || material.isAir()) continue;
            if (!inner.equalsIgnoreCase(englishName(material)) && !inner.equals(translation(material))) continue;
            if (found != null && found != material) return null; // ambiguous
            found = material;
        }
        return found;
    }

    static @NotNull String englishName(@NotNull Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @SuppressWarnings("deprecation")
    private static boolean isLegacyMaterial(@NotNull Material material) {
        try {
            return material.isLegacy();
        } catch (Throwable ignored) {
            return material.name().startsWith("LEGACY_");
        }
    }

    private static @Nullable String translation(@NotNull Material material) {
        if (StorageBoxPlugin.getInstance() == null) return null;
        try {
            return StorageBoxPlugin.findTranslation(material);
        } catch (RuntimeException ignored) {
            return null; // no translation for this material
        }
    }

    public static StorageBox getNewStorageBox() {
        return getNewStorageBox(null);
    }

    public static StorageBox getNewStorageBox(Material type) {
        return getNewStorageBox(type, 0);
    }

    public static StorageBox getNewStorageBox(Material type, long amount) {
        return new StorageBox(type, amount);
    }

    public static @NotNull StorageBox wrapWithStorageBox(@NotNull ItemStack stack) {
        com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(stack);
        NBTCompound tag = null;
        if (peItem != null) {
            tag = peItem.getNBT();
            if (tag == null) {
                try {
                    tag = peItem.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
                } catch (Throwable ignored) {}
            }
        }
        if (tag != null && tag.isEmpty()) tag = null;
        String serialized = serializeItemStack(stack);
        return new StorageBox(stack.getType(), stack.getAmount(), true, false, false, tag != null ? tag.copy() : null, serialized, null);
    }

    /**
     * Returns the containing item. Amount is always 1.
     * @return the item
     */
    public @Nullable ItemStack getComponentItemStack() {
        if (type == null || type.isAir()) {
            return new ItemStack(Material.AIR);
        }

        if (LegacyComponentUpgrader.isLegacyItemTag(tag)) {
            // Content stored on 1.15.2: let the server's DataFixer convert it. Any serializedComponent of such a
            // box was produced by the old hand-made conversion below, so it is replaced (getItemStack saves it).
            ItemStack upgraded = LegacyComponentUpgrader.upgrade(type, Objects.requireNonNull(tag));
            ItemStack mythic = upgraded == null ? null : LegacyComponentUpgrader.currentMythicItem(type, tag, upgraded);
            if (mythic != null) {
                // A MythicMobs item: the box holds the item MythicMobs generates today from now on
                // (same name and lore; it stacks and auto-collects with new drops). getItemStack saves it.
                adoptComponent(mythic);
                return mythic.clone();
            }
            if (upgraded != null) {
                if (!legacyComponentUpgraded || serializedComponent == null) {
                    this.serializedComponent = serializeItemStack(upgraded);
                    legacyComponentUpgraded = true;
                }
                return upgraded;
            }
        }

        if (serializedComponent != null && !serializedComponent.isEmpty()) {
            ItemStack deserialized = deserializeItemStack(serializedComponent);
            if (deserialized != null) {
                return deserialized;
            }
        }

        ItemStack stack = new ItemStack(type);
        if (tag == null) return stack;

        com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(stack);
        if (peItem != null) {
            peItem.setNBT(tag.copy());
            try {
                peItem.setComponent(ComponentTypes.CUSTOM_DATA, tag.copy());
            } catch (Throwable ignored) {}
            ItemStack converted = SpigotConversionUtil.toBukkitItemStack(peItem);
            if (converted != null) stack = converted;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            NBTCompound display = tag.getCompoundTagOrNull("display");
            if (display != null) {
                String rawName = display.getStringTagValueOrNull("Name");
                if (rawName != null && !meta.hasDisplayName()) {
                    meta.setDisplayName(parseLegacyOrJsonText(rawName));
                }
                NBTList<NBTString> loreList = display.getStringListTagOrNull("Lore");
                if (loreList != null && (!meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty())) {
                    List<String> lore = new ArrayList<>();
                    for (NBTString s : loreList.getTags()) {
                        lore.add(parseLegacyOrJsonText(s.getValue()));
                    }
                    meta.setLore(lore);
                }
            }

            if (tag.getTagOrNull("Damage") instanceof NBTNumber) {
                int dmg = ((NBTNumber) tag.getTagOrNull("Damage")).getAsInt();
                if (meta instanceof Damageable) {
                    ((Damageable) meta).setDamage(dmg);
                }
            }

            if (tag.getTagOrNull("CustomModelData") instanceof NBTNumber) {
                int cmd = ((NBTNumber) tag.getTagOrNull("CustomModelData")).getAsInt();
                meta.setCustomModelData(cmd);
            }

            NBTList<NBTCompound> enchants = tag.getCompoundListTagOrNull("Enchantments");
            if (enchants != null) {
                for (NBTCompound ench : enchants.getTags()) {
                    String id = ench.getStringTagValueOrNull("id");
                    int lvl = 1;
                    if (ench.getTagOrNull("lvl") instanceof NBTNumber) {
                        lvl = ((NBTNumber) ench.getTagOrNull("lvl")).getAsInt();
                    }
                    if (id != null) {
                        id = id.toLowerCase().replace("minecraft:", "");
                        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(id));
                        if (e != null) {
                            meta.addEnchant(e, lvl, true);
                        }
                    }
                }
            }

            restoreSoulboundToPdc(tag, meta);

            stack.setItemMeta(meta);
        }

        if (this.serializedComponent == null) {
            this.serializedComponent = serializeItemStack(stack);
        }

        return stack;
    }

    public @NotNull String getComponentItemStackName() {
        if (type == null || type.isAir()) return "Unknown";
        ItemStack stack = getComponentItemStack();
        if (stack != null && stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()) {
            String displayName = stack.getItemMeta().getDisplayName();
            String stripped = ChatColor.stripColor(displayName);
            if (stripped != null && !stripped.startsWith("Storage Box")) {
                return displayName;
            }
            if (displayName != null) {
                int start = displayName.indexOf('[');
                int end = displayName.lastIndexOf(']');
                if (start != -1 && end != -1 && end > start) {
                    String inner = displayName.substring(start + 1, end).trim();
                    if (!inner.isEmpty() && !ChatColor.stripColor(inner).startsWith("Storage Box")) {
                        return inner;
                    }
                }
            }
        }
        String i18nName = StorageBoxPlugin.findTranslation(type);
        if (i18nName != null) return i18nName;
        String name = type.name().replace("_", " ").toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }

    public @Nullable String getComponentItemStackDisplayName() {
        if (type == null || type.isAir()) return null;
        ItemStack stack = getComponentItemStack();
        if (Objects.requireNonNull(stack).hasItemMeta() && Objects.requireNonNull(stack.getItemMeta()).hasDisplayName()) {
            return Objects.requireNonNull(stack.getItemMeta()).getDisplayName();
        }
        return null;
    }

    public boolean isComponentItemStackSimilar(@Nullable ItemStack stack) {
        if (stack == null) return false;
        ItemStack component = getComponentItemStack();
        if (component == null) return false;
        if (stack.isSimilar(component)) return true;
        return isSameMythicItem(stack, component);
    }

    /**
     * Whether two items are the same MythicMobs item even though their components differ.
     * <p>
     * Contents of 1.15.2 boxes (and items taken out of them) keep leftovers of the old format: the
     * MythicMobs 4 {@code MYTHIC_TYPE} tag in custom_data, the DataFixer's text components, a stored
     * snapshot of an older definition... {@link ItemStack#isSimilar} then tells them apart from the items
     * MythicMobs drops today, so they neither auto-collect nor merge. Items with the same material and
     * MMID are treated as the same item instead, unless they are soulbound to different players or the
     * MMID is in MythicItemIdCompat's {@code excluded-mmids}.
     */
    static boolean isSameMythicItem(@NotNull ItemStack a, @NotNull ItemStack b) {
        if (a.getType().isAir() || a.getType() != b.getType()) return false;
        String id = mythicId(a);
        if (id == null || !id.equals(mythicId(b))) return false;
        if (!Objects.equals(soulbound(a), soulbound(b))) return false;
        return !isMythicIdExcluded(id);
    }

    /** The item's MMID: MythicMobs 5 {@code mythicmobs:type}, else a leftover MythicMobs 4 {@code MYTHIC_TYPE}. */
    static @Nullable String mythicId(@NotNull ItemStack item) {
        if (item.getType().isAir()) return null;
        try {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String id = meta.getPersistentDataContainer().get(KEY_MYTHIC_TYPE, PersistentDataType.STRING);
                    if (id != null && !id.trim().isEmpty()) return id.trim();
                }
            }
            String legacy = ItemUtil.getStringTag(item, "MYTHIC_TYPE").trim();
            return legacy.isEmpty() ? null : legacy;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable String soulbound(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        try {
            return meta.getPersistentDataContainer().get(KEY_SOULBOUND, PersistentDataType.STRING);
        } catch (RuntimeException e) {
            return null; // stored with another type
        }
    }

    private static volatile @Nullable java.lang.reflect.Method isMythicIdExcluded;

    /** MythicItemIdCompat's {@code excluded-mmids}; false when the plugin (1.4.0+) is not installed. */
    private static boolean isMythicIdExcluded(@NotNull String id) {
        org.bukkit.plugin.Plugin compat = Bukkit.getPluginManager().getPlugin("MythicItemIdCompat");
        if (compat == null || !compat.isEnabled()) return false;
        try {
            java.lang.reflect.Method method = isMythicIdExcluded;
            if (method == null || method.getDeclaringClass() != compat.getClass()) {
                method = compat.getClass().getMethod("isMythicIdExcluded", String.class);
                isMythicIdExcluded = method;
            }
            return Boolean.TRUE.equals(method.invoke(compat, id));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public @NotNull ItemStack getItemStack() {
        Material itemType = getType() == null ? Material.BARRIER : getType();
        boolean canPlant = false;
        boolean canEat = false;
        if (StorageBoxPlugin.getInstance() != null) {
            canPlant = StorageBoxPlugin.getInstance().getConfig().getBoolean("extensions.plant", false);
            canEat = StorageBoxPlugin.getInstance().getConfig().getBoolean("extensions.eat", false);
        }

        Set<Material> allowedMaterials = new HashSet<>(opaqueExempt);
        if (canPlant) {
            allowedMaterials.addAll(Arrays.asList(
                    Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
                    Material.POTATO, Material.CARROT, Material.SWEET_BERRIES, Material.BAMBOO, Material.COCOA_BEANS,
                    Material.KELP, Material.SUGAR_CANE, Material.CACTUS, Material.NETHER_WART
            ));
        }
        if (canEat && itemType.isEdible()) {
            allowedMaterials.add(itemType);
        }

        if (!itemType.isBlock() && !allowedMaterials.contains(itemType)) {
            itemType = Material.STICK;
        }
        String id = randomUUID != null ? randomUUID.toString() : UUID.randomUUID().toString();
        ItemStack item = new ItemStack(itemType);
        com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(item);
        NBTCompound tag = null;
        if (peItem != null) {
            tag = peItem.getNBT();
            if (tag == null) {
                try {
                    tag = peItem.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
                } catch (Throwable ignored) {}
            }
        }
        tag = (tag != null) ? tag.copy() : new NBTCompound();

        if (this.tag != null) {
            tag.setTag("storageBoxTag", this.tag.copy());
        }
        tag.removeTag("display");
        tag.setTag("storageBoxType", new NBTString(this.type == null ? "null" : this.type.name()));
        tag.setTag("storageBoxAmount", new NBTLong(this.amount));
        tag.setTag("storageBoxAutoCollect", new NBTByte((byte) (this.autoCollect ? 1 : 0)));
        tag.setTag("storageBoxAutoBuy", new NBTByte((byte) (this.autoBuy ? 1 : 0)));
        tag.setTag("randomUUID", new NBTString(id));

        if (peItem != null) {
            peItem.setNBT(tag);
            try {
                peItem.setComponent(ComponentTypes.CUSTOM_DATA, tag.copy());
            } catch (Throwable ignored) {}
            ItemStack converted = SpigotConversionUtil.toBukkitItemStack(peItem);
            if (converted != null) {
                item = converted;
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new RuntimeException("ItemMeta is null");
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_TYPE, PersistentDataType.STRING, this.type == null ? "null" : this.type.name());
        pdc.set(KEY_AMOUNT, PersistentDataType.LONG, this.amount);
        pdc.set(KEY_AUTO_COLLECT, PersistentDataType.BYTE, (byte) (this.autoCollect ? 1 : 0));
        pdc.set(KEY_AUTO_BUY, PersistentDataType.BYTE, (byte) (this.autoBuy ? 1 : 0));
        pdc.set(KEY_UUID, PersistentDataType.STRING, id);

        if (this.serializedComponent == null && this.type != null && !this.type.isAir()) {
            ItemStack compStack = getComponentItemStack();
            if (compStack != null && !compStack.getType().isAir()) {
                this.serializedComponent = serializeItemStack(compStack);
            }
        }
        if (this.serializedComponent != null) {
            pdc.set(KEY_COMPONENT_DATA, PersistentDataType.STRING, this.serializedComponent);
        }

        meta.setDisplayName(ChatColor.GREEN + "Storage Box " + ChatColor.YELLOW + "[" + ChatColor.WHITE + getComponentItemStackName() + ChatColor.YELLOW + "] " + ChatColor.GRAY + "<" + this.amount + ">");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "数: " + amount,
                ChatColor.GRAY + "自動回収: " + autoCollect,
                ChatColor.GRAY + "自動購入: " + autoBuy,
                ChatColor.GRAY + "NBTタグ: " + (getTag() != null),
                ChatColor.GRAY + "ID: " + id
        ));
        if (type == null || type.isAir()) {
            meta.setCustomModelData(StorageBoxPlugin.customModelData);
        } else {
            // the box looks like its content (PacketListener also shows the content's material)
            Integer contentModel = contentCustomModelData();
            if (contentModel != null) meta.setCustomModelData(contentModel);
        }
        if (amount > 0) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    public void setAmount(long amount) {
        this.amount = amount;
        if (amount <= 0 && type == Material.EMERALD_BLOCK) {
            type = null;
            tag = null;
            serializedComponent = null;
        }
    }

    public void increaseAmount() {
        setAmount(amount + 1);
    }

    public void decreaseAmount() {
        setAmount(amount - 1);
    }

    /**
     * Get material of this storage box.
     * @return Null if undefined, material otherwise.
     */
    public @Nullable Material getType() {
        return type == null || type.isAir() ? null : type;
    }

    /**
     * Set material in this storage box.
     * @param type Null if undefined, material otherwise.
     */
    public void setType(@Nullable Material type) {
        this.type = type;
        if (type == null || type.isAir()) {
            this.tag = null;
            this.serializedComponent = null;
        }
    }

    public long getAmount() {
        return amount;
    }

    public @Nullable NBTCompound getTag() {
        return tag;
    }

    public void setTag(@Nullable NBTCompound tag) {
        if (tag != null && tag.getTagOrNull("storageBoxAmount") != null) {
            throw new IllegalArgumentException("StorageBox cannot contain StorageBox");
        }
        this.tag = tag;
        this.serializedComponent = null;
    }

    public @Nullable String getSerializedComponent() {
        return serializedComponent;
    }

    public void setSerializedComponent(@Nullable String serializedComponent) {
        this.serializedComponent = serializedComponent;
    }

    public boolean isEmpty() {
        return amount <= 0;
    }

    public boolean isAutoCollect() {
        return autoCollect;
    }

    public void setAutoCollect(boolean autoCollect) {
        this.autoCollect = autoCollect;
    }

    public boolean isAutoBuy() {
        return autoBuy;
    }

    public void setAutoBuy(boolean autoBuy) {
        this.autoBuy = autoBuy;
        this.autoBuyConfigured = true;
    }

    public boolean isAutoBuyConfigured() {
        return autoBuyConfigured;
    }

    public void importComponent(@NotNull ItemStack stack) {
        com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(stack);
        NBTCompound tag = null;
        if (peItem != null) {
            tag = peItem.getNBT();
            if (tag == null) {
                try {
                    tag = peItem.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
                } catch (Throwable ignored) {}
            }
        }
        if (tag != null && tag.isEmpty()) tag = null;
        this.setTag(tag != null ? tag.copy() : null);
        this.serializedComponent = serializeItemStack(stack);
        this.type = stack.getType();
        this.amount = stack.getAmount();
    }

    /** The content's custom model data, or null when it has none. */
    private @Nullable Integer contentCustomModelData() {
        try {
            ItemStack content = getComponentItemStack();
            if (content == null || content.getType().isAir() || !content.hasItemMeta()) return null;
            ItemMeta contentMeta = content.getItemMeta();
            if (contentMeta == null || !contentMeta.hasCustomModelData()) return null;
            return contentMeta.getCustomModelData();
        } catch (RuntimeException e) {
            return null; // e.g. a model data without a number (1.21.4+ strings/flags only)
        }
    }

    /** Makes the item the box's content without changing the amount (the content tag is no longer 1.15.2 NBT). */
    private void adoptComponent(@NotNull ItemStack stack) {
        NBTCompound newTag = null;
        com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(stack);
        if (peItem != null) {
            newTag = peItem.getNBT();
            if (newTag == null) {
                try {
                    newTag = peItem.getComponentOr(ComponentTypes.CUSTOM_DATA, null);
                } catch (Throwable ignored) {}
            }
        }
        this.tag = newTag == null || newTag.isEmpty() ? null : newTag.copy();
        this.type = stack.getType();
        this.serializedComponent = serializeItemStack(stack);
        this.legacyComponentUpgraded = true;
    }

    public static @Nullable String serializeItemStack(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", clone);
        return yaml.saveToString();
    }

    public static @Nullable ItemStack deserializeItemStack(@Nullable String serialized) {
        if (serialized == null || serialized.isEmpty()) return null;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(serialized);
            ItemStack item = yaml.getItemStack("item");
            if (item != null) {
                item = item.clone();
                item.setAmount(1);
                return item;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static @Nullable NBTCompound extractStorageBoxTag(@NotNull NBTCompound tag) {
        NBTCompound storageBoxTag = tag.getCompoundTagOrNull("storageBoxTag");
        if (storageBoxTag != null && storageBoxTag.getTagOrNull("storageBoxAmount") != null) {
            throw new IllegalArgumentException("StorageBox cannot contain StorageBox");
        }
        if (storageBoxTag != null && !storageBoxTag.isEmpty()) {
            NBTCompound copy = storageBoxTag.copy();
            cleanStorageBoxDisplayName(copy);
            return copy;
        }
        if (tag.getTagOrNull("display") != null || tag.getTagOrNull("Enchantments") != null || tag.getTagOrNull("soulbound") != null) {
            NBTCompound copy = tag.copy();
            copy.removeTag("storageBoxType");
            copy.removeTag("storageBoxAmount");
            copy.removeTag("storageBoxAutoCollect");
            copy.removeTag("storageBoxAutoBuy");
            copy.removeTag("randomUUID");
            copy.removeTag("PublicBukkitValues");
            cleanStorageBoxDisplayName(copy);
            if (!copy.isEmpty()) return copy;
        }
        return null;
    }

    private static void cleanStorageBoxDisplayName(@NotNull NBTCompound tag) {
        NBTCompound display = tag.getCompoundTagOrNull("display");
        if (display != null) {
            String name = display.getStringTagValueOrNull("Name");
            if (name != null) {
                String stripped = ChatColor.stripColor(parseLegacyOrJsonText(name));
                if (stripped != null && stripped.startsWith("Storage Box")) {
                    tag.removeTag("display");
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void restoreSoulboundToPdc(@NotNull NBTCompound tag, @NotNull ItemMeta meta) {
        String soulboundUuid = null;
        // 1. 直下 "soulbound" タグ
        if (tag.getTagOrNull("soulbound") != null) {
            soulboundUuid = tag.getStringTagValueOrNull("soulbound");
        }
        // 2. PublicBukkitValues タグ
        if (soulboundUuid == null) {
            NBTCompound pbv = tag.getCompoundTagOrNull("PublicBukkitValues");
            if (pbv != null && pbv.getTagOrNull("soulbound:soulbound") != null) {
                soulboundUuid = pbv.getStringTagValueOrNull("soulbound:soulbound");
            }
        }
        // 3. Lore からの抽出フォールバック
        if (soulboundUuid == null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    String stripped = ChatColor.stripColor(line);
                    if (stripped.contains("Soulbound: ")) {
                        soulboundUuid = stripped.substring(stripped.indexOf("Soulbound: ") + 11).trim();
                        if (soulboundUuid.endsWith("]")) soulboundUuid = soulboundUuid.substring(0, soulboundUuid.length() - 1).trim();
                        break;
                    }
                    if (stripped.contains("Soulbound (取引不可) ")) {
                        soulboundUuid = stripped.substring(stripped.indexOf("Soulbound (取引不可) ") + 19).trim();
                        if (soulboundUuid.endsWith("*")) soulboundUuid = soulboundUuid.substring(0, soulboundUuid.length() - 1).trim();
                        break;
                    }
                }
            }
        }
        if (soulboundUuid != null && !soulboundUuid.isEmpty()) {
            try {
                UUID.fromString(soulboundUuid);
                NamespacedKey soulboundKey = new NamespacedKey("soulbound", "soulbound");
                meta.getPersistentDataContainer().set(soulboundKey, PersistentDataType.STRING, soulboundUuid);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static String parseLegacyOrJsonText(String text) {
        if (text == null) return null;
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                JsonElement el = new JsonParser().parse(text);
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    StringBuilder sb = new StringBuilder();
                    if (obj.has("text")) {
                        sb.append(obj.get("text").getAsString());
                    }
                    if (obj.has("extra")) {
                        for (JsonElement extra : obj.getAsJsonArray("extra")) {
                            if (extra.isJsonObject() && extra.getAsJsonObject().has("text")) {
                                sb.append(extra.getAsJsonObject().get("text").getAsString());
                            } else if (extra.isJsonPrimitive()) {
                                sb.append(extra.getAsString());
                            }
                        }
                    }
                    if (sb.length() > 0) return sb.toString();
                }
            } catch (Exception ignored) {}
        }
        return text;
    }
}
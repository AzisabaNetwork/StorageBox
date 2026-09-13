package xyz.acrylicstyle.storageBox.utils;

import com.github.retrooper.packetevents.protocol.nbt.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
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
                        Material type = Material.valueOf(s == null || s.isEmpty() || s.equalsIgnoreCase("null") ? "AIR" : s.toUpperCase());

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
                        if (peItem != null && peItem.getNBT() != null) {
                            storageBoxTag = extractStorageBoxTag(peItem.getNBT());
                        }

                        return new StorageBox(type, amount, autoCollect, autoBuy, autoBuyConfigured, storageBoxTag, serializedComponent, randomUUID);
                    }
                }
            }

            com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(itemStack);
            if (peItem == null) return null;
            NBTCompound tag = peItem.getNBT();
            if (tag == null) return null;

            NBTCompound rootTag = tag;
            if (tag.getTagOrNull("storageBoxType") == null) {
                NBTCompound customData = tag.getCompoundTagOrNull("minecraft:custom_data");
                if (customData == null) customData = tag.getCompoundTagOrNull("custom_data");
                if (customData != null && customData.getTagOrNull("storageBoxType") != null) {
                    rootTag = customData;
                }
            }

            if (rootTag.getTagOrNull("storageBoxType") == null) {
                return null;
            }

            String s = rootTag.getStringTagValueOrNull("storageBoxType");
            Material type = Material.valueOf(s == null || s.isEmpty() || s.equalsIgnoreCase("null") ? "AIR" : s.toUpperCase());

            long amount = 0;
            NBT amountNBT = rootTag.getTagOrNull("storageBoxAmount");
            if (amountNBT instanceof NBTNumber) {
                amount = ((NBTNumber) amountNBT).getAsLong();
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
        NBTCompound tag = peItem != null ? peItem.getNBT() : null;
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
        if (Objects.requireNonNull(stack).hasItemMeta() && Objects.requireNonNull(stack.getItemMeta()).hasDisplayName()) {
            return Objects.requireNonNull(stack.getItemMeta()).getDisplayName();
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
        return stack.isSimilar(getComponentItemStack());
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
        NBTCompound tag = (peItem != null && peItem.getNBT() != null) ? peItem.getNBT().copy() : new NBTCompound();

        if (this.tag != null) {
            tag.setTag("storageBoxTag", this.tag.copy());
        }
        tag.setTag("storageBoxType", new NBTString(this.type == null ? "null" : this.type.name()));
        tag.setTag("storageBoxAmount", new NBTLong(this.amount));
        tag.setTag("storageBoxAutoCollect", new NBTByte((byte) (this.autoCollect ? 1 : 0)));
        tag.setTag("storageBoxAutoBuy", new NBTByte((byte) (this.autoBuy ? 1 : 0)));
        tag.setTag("randomUUID", new NBTString(id));

        if (peItem != null) {
            peItem.setNBT(tag);
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
        NBTCompound tag = peItem != null ? peItem.getNBT() : null;
        if (tag != null && tag.isEmpty()) tag = null;
        this.setTag(tag != null ? tag.copy() : null);
        this.serializedComponent = serializeItemStack(stack);
        this.type = stack.getType();
        this.amount = stack.getAmount();
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
            return storageBoxTag.copy();
        }
        if (tag.getTagOrNull("display") != null || tag.getTagOrNull("Enchantments") != null || tag.getTagOrNull("soulbound") != null) {
            NBTCompound copy = tag.copy();
            copy.removeTag("storageBoxType");
            copy.removeTag("storageBoxAmount");
            copy.removeTag("storageBoxAutoCollect");
            copy.removeTag("storageBoxAutoBuy");
            copy.removeTag("randomUUID");
            copy.removeTag("PublicBukkitValues");
            if (!copy.isEmpty()) return copy;
        }
        return null;
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
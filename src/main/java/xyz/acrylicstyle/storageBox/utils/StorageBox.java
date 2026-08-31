package xyz.acrylicstyle.storageBox.utils;

import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.acrylicstyle.storageBox.StorageBoxPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class StorageBox {
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
    private final @Nullable UUID randomUUID;

    public StorageBox(@Nullable Material type, long amount) {
        this(type, amount, true, false, false, null, null);
    }

    public StorageBox(@Nullable Material type, long amount, boolean autoCollect, @Nullable UUID randomUUID) {
        this(type, amount, autoCollect, false, false, null, randomUUID);
    }

    public StorageBox(@Nullable Material type, long amount, boolean autoCollect, boolean autoBuy, boolean autoBuyConfigured, @Nullable NBTCompound tag, @Nullable UUID randomUUID) {
        this.type = type;
        this.amount = amount;
        this.autoCollect = autoCollect;
        this.autoBuy = autoBuy;
        this.autoBuyConfigured = autoBuyConfigured;
        this.tag = tag;
        this.randomUUID = randomUUID;
    }

    public static @Nullable StorageBox getStorageBox(@NotNull ItemStack itemStack) {
        try {
            com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(itemStack);
            if (peItem == null) return null;
            NBTCompound tag = peItem.getNBT();
            if (tag == null || tag.getTagOrNull("storageBoxType") == null) {
                return null;
            }
            String s = tag.getStringTagValueOrNull("storageBoxType");
            Material type = Material.valueOf(s == null || s.isEmpty() || s.equalsIgnoreCase("null") ? "AIR" : s.toUpperCase());
            
            long amount = 0;
            NBT amountNBT = tag.getTagOrNull("storageBoxAmount");
            if (amountNBT instanceof NBTNumber) {
                amount = ((NBTNumber) amountNBT).getAsLong();
            }

            boolean autoCollect = true;
            NBT autoCollectNBT = tag.getTagOrNull("storageBoxAutoCollect");
            if (autoCollectNBT instanceof NBTNumber) {
                autoCollect = ((NBTNumber) autoCollectNBT).getAsByte() != 0;
            }

            boolean autoBuyConfigured = tag.getTagOrNull("storageBoxAutoBuy") != null;
            boolean autoBuy = false;
            if (autoBuyConfigured) {
                NBT autoBuyNBT = tag.getTagOrNull("storageBoxAutoBuy");
                if (autoBuyNBT instanceof NBTNumber) {
                    autoBuy = ((NBTNumber) autoBuyNBT).getAsByte() != 0;
                }
            }

            NBTCompound storageBoxTag = tag.getCompoundTagOrNull("storageBoxTag");
            if (storageBoxTag != null && storageBoxTag.getTagOrNull("storageBoxAmount") != null) {
                throw new IllegalArgumentException("StorageBox cannot contain StorageBox");
            }
            if (storageBoxTag != null && storageBoxTag.isEmpty()) storageBoxTag = null;
            String uuidStr = tag.getStringTagValueOrNull("randomUUID");
            UUID randomUUID = uuidStr != null ? UUID.fromString(uuidStr) : null;
            return new StorageBox(type, amount, autoCollect, autoBuy, autoBuyConfigured, storageBoxTag, randomUUID);
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
        return new StorageBox(stack.getType(), stack.getAmount(), true, false, false, tag != null ? tag.copy() : null, null);
    }

    /**
     * Returns the containing item. Amount is always 1.
     * @return the item
     */
    public @Nullable ItemStack getComponentItemStack() {
        ItemStack stack = new ItemStack(type == null ? Material.AIR : type);
        if (type == null || type.isAir() || tag == null) return stack;
        com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(stack);
        if (peItem != null) {
            peItem.setNBT(tag.copy());
            ItemStack converted = SpigotConversionUtil.toBukkitItemStack(peItem);
            if (converted != null) return converted;
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
        String name = type.name().replaceAll("_", " ").toLowerCase();
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
            for (Map.Entry<String, NBT> entry : this.tag.getTags().entrySet()) {
                tag.setTag(entry.getKey(), entry.getValue().copy());
            }
            tag.setTag("storageBoxTag", this.tag.copy());
            tag.removeTag("MYTHIC_TYPE");
            tag.removeTag("AttributeModifiers");
            tag.removeTag("display");
            tag.removeTag("Enchantments");
            tag.removeTag("CustomModelData");
            tag.removeTag("LifeItemId");
            tag.removeTag("backup");
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
        this.type = stack.getType();
        this.amount = stack.getAmount();
    }
}
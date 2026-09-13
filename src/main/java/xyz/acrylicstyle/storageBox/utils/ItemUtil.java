package xyz.acrylicstyle.storageBox.utils;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ItemUtil {
    public static @NotNull ItemStack createItem(@NotNull Material material, @NotNull String displayName, @NotNull List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        assert meta != null;
        meta.setDisplayName(displayName);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static @NotNull String getStringTag(@NotNull ItemStack item, @NotNull String key) {
        if (item.getType().isAir()) return "";
        com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(item);
        if (peItem == null) return "";
        NBTCompound tag = peItem.getNBT();
        if (tag == null) {
            try {
                tag = peItem.getComponentOr(com.github.retrooper.packetevents.protocol.component.ComponentTypes.CUSTOM_DATA, null);
            } catch (Throwable ignored) {}
        }
        if (tag == null) return "";
        String value = tag.getStringTagValueOrNull(key);
        if (value != null) return value;
        NBTCompound customData = tag.getCompoundTagOrNull("minecraft:custom_data");
        if (customData == null) customData = tag.getCompoundTagOrNull("custom_data");
        if (customData != null) {
            value = customData.getStringTagValueOrNull(key);
            if (value != null) return value;
        }
        return "";
    }
}
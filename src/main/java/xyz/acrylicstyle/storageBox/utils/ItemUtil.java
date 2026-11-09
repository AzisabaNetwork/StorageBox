package xyz.acrylicstyle.storageBox.utils;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.regex.Pattern;

public class ItemUtil {
    public static @Nullable CompoundTag getCustomData(@Nullable ItemStack bukkitItem) {
        if (bukkitItem == null || bukkitItem.getType().isAir()) {
            return new CompoundTag();
        }

        // 1. Bukkit アイテムを Mojang 内部の NMS ItemStack に変換
        net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(bukkitItem);

        // 2. アイテムが保持している生のデータコンポーネントマップを取得
        DataComponentMap componentMap = nmsItem.getComponents();

        // 3. 内部レジストリのシリアライズ文脈（Context）を作成
        var registryProvider = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        var serializationContext = registryProvider.createSerializationContext(NbtOps.INSTANCE);

        // 4. CODECを使って、コンポーネントマップを純粋な NBT (Tag) にエンコード
        // これによって生成されるのが、/data コマンドで見える "components: { ... }" の中身そのものです
        Tag encodedTag = DataComponentMap.CODEC.encodeStart(serializationContext, componentMap)
                .getOrThrow(); // エラー時は例外をスロー

        // 5. 戻り値は CompoundTag (NBTの{ }の塊) になるのでキャストして返す
        if (encodedTag instanceof CompoundTag compoundTag) {
            return compoundTag;
        }

        return new CompoundTag();
    }

    /** The item's minecraft:custom_data compound (where a Storage Box keeps its own tags), or null. */
    public static @Nullable CompoundTag getRawCustomData(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        CustomData customData = CraftItemStack.asNMSCopy(stack).get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        return customData.copyTag();
    }

    /**
     * The item's components that differ from its type's defaults, or null when there are none.
     * This is what a Storage Box stores as the content's tag.
     */
    public static @Nullable CompoundTag getComponentPatch(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        DataComponentPatch patch = CraftItemStack.asNMSCopy(stack).getComponentsPatch();
        if (patch.isEmpty()) return null;
        var serializationContext = ((CraftServer) Bukkit.getServer()).getServer().registryAccess()
                .createSerializationContext(NbtOps.INSTANCE);
        Tag encoded = DataComponentPatch.CODEC.encodeStart(serializationContext, patch).getOrThrow();
        return encoded instanceof CompoundTag compound && !compound.isEmpty() ? compound : null;
    }

    private static final Pattern COMPONENT_KEY = Pattern.compile("!?[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** True if every key of the tag is a component id ("minecraft:custom_name", "!minecraft:food"...). */
    public static boolean isComponentTag(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return false;
        for (String key : tag.keySet()) {
            if (!COMPONENT_KEY.matcher(key).matches()) return false;
        }
        return true;
    }

    /** Applies a component patch (or a full component map, as written by 1.6.4) to the item. */
    public static net.minecraft.world.item.ItemStack applyComponentPatch(net.minecraft.world.item.ItemStack nmsItem, CompoundTag tag) {
        var serializationContext = ((CraftServer) Bukkit.getServer()).getServer().registryAccess()
                .createSerializationContext(NbtOps.INSTANCE);
        DataComponentPatch patch = DataComponentPatch.CODEC.parse(serializationContext, tag).getOrThrow();
        nmsItem.applyComponents(patch);
        return nmsItem;
    }

    public static net.minecraft.world.item.ItemStack applyRawComponentsTag(net.minecraft.world.item.ItemStack nmsItem, CompoundTag rawComponentsTag) {
        if (nmsItem == null || rawComponentsTag == null) return nmsItem;

        // 1. レジストリコンテキストの作成
        var registryProvider = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        var serializationContext = registryProvider.createSerializationContext(NbtOps.INSTANCE);

        // 2. 保存されていた CompoundTag をパースして、再度 DataComponentMap オブジェクトへ復元
        DataComponentMap restoredComponents = DataComponentMap.CODEC.parse(serializationContext, rawComponentsTag)
                .getOrThrow();

        // 3. ターゲットアイテムのコンポーネントを上書き適用
        nmsItem.applyComponents(restoredComponents);

        // 4. BukkitのItemStack型に戻して返す
        return nmsItem;
    }

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
        CompoundTag tag = getRawCustomData(item);
        if (tag == null) return "";
        return tag.getString(key).orElse("");
    }
}

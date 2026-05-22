package xyz.acrylicstyle.storageBox.utils;

import net.minecraft.core.component.DataComponentMap;
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
        CompoundTag tag = getCustomData(item);
        if (tag == null) return "";
        return tag.getString(key).orElse("");
    }
}

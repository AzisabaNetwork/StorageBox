package xyz.acrylicstyle.storageBox.commands;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.acrylicstyle.storageBox.StorageBoxPlugin;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

public class NewCommand {
    public static void onCommand(Player player, String[] args) {
        if (StorageBoxPlugin.getInstance().getConfig().getBoolean("disable-crafting", false)) {
            player.sendMessage(ChatColor.RED + "このコマンドは無効化されています。");
            return;
        }

        int requestedAmount;
        try {
            requestedAmount = args.length == 0 ? 1 : Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "個数は数字で指定してください。");
            return;
        }
        if (requestedAmount <= 0) {
            player.sendMessage(ChatColor.RED + "1個以上を指定してください。");
            return;
        }

        int creatableAmount = Math.min(requestedAmount, StorageBoxPlugin.getEmptySlots(player));
        if (creatableAmount <= 0) {
            player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
            return;
        }

        boolean bypass = StorageBoxPlugin.bypassingPlayers.contains(player.getUniqueId());
        long totalPrice = 0L;
        if (!bypass) {
            if (hasMaterials(player, creatableAmount)) {
                removeMaterials(player, creatableAmount);
            } else {
                long pricePerBox = StorageBoxPlugin.getInstance().getStorageBoxCreationPrice();
                if (pricePerBox <= 0) {
                    player.sendMessage(ChatColor.RED + "Storage Box の作成価格を計算できませんでした。");
                    return;
                }
                totalPrice = pricePerBox * creatableAmount;
                EconomyResponse response = StorageBoxPlugin.getEconomy().withdrawPlayer(player, totalPrice);
                if (!response.transactionSuccess()) {
                    player.sendMessage(ChatColor.RED + "素材が不足しており、お金での作成にも失敗しました。");
                    player.sendMessage(ChatColor.RED + "必要額: " + totalPrice + "円");
                    return;
                }
            }
        }

        for (int i = 0; i < creatableAmount; i++) {
            player.getInventory().addItem(StorageBox.getNewStorageBox().getItemStack());
        }

        player.sendMessage(ChatColor.GREEN + "新しいStorage Boxを" + ChatColor.YELLOW + creatableAmount + ChatColor.GREEN + "個作成しました。");
        if (creatableAmount < requestedAmount) {
            player.sendMessage(ChatColor.YELLOW + "" + (requestedAmount - creatableAmount) + "個は作成できませんでした。");
        }
        player.sendMessage(ChatColor.GREEN + " - アイテムの種類を設定するには、設定したいものをオフハンドに持ったうえで" + ChatColor.YELLOW + "/sb changetype" + ChatColor.GREEN + "を実行してください。");
        player.sendMessage(ChatColor.GREEN + " - アイテムを取り出すには" + ChatColor.YELLOW + "/sb extract <数>" + ChatColor.GREEN + "を実行してください。");
        player.sendMessage(ChatColor.GREEN + " - 自動収集をオフにするには" + ChatColor.YELLOW + "/sb autocollect" + ChatColor.GREEN + "を実行してください。");
        player.sendMessage(ChatColor.GREEN + " - その他の使い方などは" + ChatColor.YELLOW + "/sb" + ChatColor.GREEN + "を見てください。");
    }

    private static boolean hasMaterials(Player player, int amount) {
        return countMaterial(player, Material.DIAMOND) >= amount * 8L && countMaterial(player, Material.CHEST) >= amount;
    }

    private static long countMaterial(Player player, Material material) {
        long count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static void removeMaterials(Player player, int amount) {
        removeMaterial(player, Material.DIAMOND, amount * 8);
        removeMaterial(player, Material.CHEST, amount);
    }

    private static void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            int consumed = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - consumed);
            remaining -= consumed;
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(i, null);
            } else {
                player.getInventory().setItem(i, item);
            }
        }
    }
}
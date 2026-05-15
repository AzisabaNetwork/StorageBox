package xyz.acrylicstyle.storageBox.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

public class AutoBuyCommand {
    public static void onCommand(Player player) {
        StorageBox storageBox = StorageBox.getStorageBox(player.getInventory().getItemInMainHand());
        if (storageBox == null) {
            player.sendMessage(ChatColor.RED + "現在手に持っているアイテムはStorage Boxではありません。");
            player.sendMessage(ChatColor.RED + "Storage Boxを手に持ってからもう一度試してください。");
            return;
        }
        if (!storageBox.isAutoBuyConfigured()) {
            storageBox.setAutoBuy(true);
            player.getInventory().setItemInMainHand(storageBox.getItemStack());
            player.sendMessage(ChatColor.YELLOW + "このStorage Boxにはauto-buy設定がありません。");
            player.sendMessage(ChatColor.GREEN + "Storage Boxにauto-buy設定が追加されました。");
            return;
        }
        boolean enabled = storageBox.isAutoBuy();
        storageBox.setAutoBuy(!enabled);
        player.getInventory().setItemInMainHand(storageBox.getItemStack());
        player.sendMessage(ChatColor.GREEN + "自動購入を" + ChatColor.YELLOW + (!enabled) + ChatColor.GREEN + "にしました。");
    }
}

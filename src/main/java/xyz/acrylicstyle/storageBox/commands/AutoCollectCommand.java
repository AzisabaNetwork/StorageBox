package xyz.acrylicstyle.storageBox.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

public class AutoCollectCommand {
    public static void onCommand(Player player) {
        StorageBox storageBox = StorageBox.getStorageBox(player.getInventory().getItemInMainHand());
        if (storageBox == null) {
            player.sendMessage(ChatColor.RED + "現在手に持っているアイテムはStorage Boxではありません。");
            player.sendMessage(ChatColor.RED + "Storage Boxを手に持ってからもう一度試してください。");
            return;
        }
        boolean enabled = storageBox.isAutoCollect();
        storageBox.setAutoCollect(!enabled);
        player.getInventory().setItemInMainHand(storageBox.getItemStack());
        player.sendMessage(ChatColor.GREEN + "自動収集を" + ChatColor.YELLOW + (!enabled) + ChatColor.GREEN + "にしました。");
    }
}
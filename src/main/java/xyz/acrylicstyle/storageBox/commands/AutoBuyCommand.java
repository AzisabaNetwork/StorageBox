package xyz.acrylicstyle.storageBox.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

public class AutoBuyCommand {
    public static void onCommand(Player player) {
        StorageBox storageBox = StorageBox.getStorageBox(player.getInventory().getItemInMainHand());
        if (storageBox == null) {
            player.sendMessage(ChatColor.RED + "The item in your main hand is not a Storage Box.");
            player.sendMessage(ChatColor.RED + "Hold a Storage Box and try again.");
            return;
        }
        if (!storageBox.isAutoBuyConfigured()) {
            storageBox.setAutoBuy(true);
            player.getInventory().setItemInMainHand(storageBox.getItemStack());
            player.sendMessage(ChatColor.YELLOW + "This Storage Box did not have an auto-buy setting yet.");
            player.sendMessage(ChatColor.GREEN + "Auto buy has been enabled and its NBT was added.");
            return;
        }
        boolean enabled = storageBox.isAutoBuy();
        storageBox.setAutoBuy(!enabled);
        player.getInventory().setItemInMainHand(storageBox.getItemStack());
        player.sendMessage(ChatColor.GREEN + "Auto buy set to " + ChatColor.YELLOW + (!enabled) + ChatColor.GREEN + ".");
    }
}

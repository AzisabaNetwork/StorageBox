package xyz.acrylicstyle.storageBox.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

public class MergeCommand {
    public static void onCommand(Player player) {
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        StorageBox mainHandBox = StorageBox.getStorageBox(mainHandItem);
        StorageBox offHandBox = StorageBox.getStorageBox(offHandItem);
        if (mainHandBox == null || offHandBox == null) {
            player.sendMessage(ChatColor.RED + "メインハンドとオフハンドの両方にStorage Boxを持ってください。");
            return;
        }
        if (mainHandBox.getType() == null || offHandBox.getType() == null) {
            player.sendMessage(ChatColor.RED + "中身が設定されていないStorage Boxは結合できません。");
            return;
        }
        if (!mainHandBox.isComponentItemStackSimilar(offHandBox.getComponentItemStack())) {
            player.sendMessage(ChatColor.RED + "異なる種類のStorage Boxは結合できません。");
            return;
        }

        mainHandBox.setAmount(mainHandBox.getAmount() + offHandBox.getAmount());
        player.getInventory().setItemInMainHand(mainHandBox.getItemStack());
        player.getInventory().setItemInOffHand(StorageBox.getNewStorageBox().getItemStack());
        player.sendMessage(ChatColor.GREEN + "オフハンドのStorage Boxをメインハンドに結合しました。");
    }
}
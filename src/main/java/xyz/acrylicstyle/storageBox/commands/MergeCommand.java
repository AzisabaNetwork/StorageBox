package xyz.acrylicstyle.storageBox.commands;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

public class MergeCommand {
    public static void onCommand(Player player, String[] args) {
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

        long mergeAmount;
        try {
            mergeAmount = args.length == 0 ? offHandBox.getAmount() : Long.parseLong(args[0]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "個数は数字で指定してください。");
            return;
        }
        if (mergeAmount <= 0) {
            player.sendMessage(ChatColor.RED + "1個以上を指定してください。");
            return;
        }
        if (offHandBox.getAmount() < mergeAmount) {
            player.sendMessage(ChatColor.RED + "オフハンドのStorage Boxにその個数は入っていません。");
            return;
        }

        mainHandBox.setAmount(mainHandBox.getAmount() + mergeAmount);
        offHandBox.setAmount(offHandBox.getAmount() - mergeAmount);
        player.getInventory().setItemInMainHand(mainHandBox.getItemStack());
        player.getInventory().setItemInOffHand(offHandBox.isEmpty() ? StorageBox.getNewStorageBox().getItemStack() : offHandBox.getItemStack());
        player.sendMessage(ChatColor.GREEN + "オフハンドのStorage Boxから" + ChatColor.YELLOW + mergeAmount + ChatColor.GREEN + "個をメインハンドに結合しました。");
    }
}

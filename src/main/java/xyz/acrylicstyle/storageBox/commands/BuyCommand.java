package xyz.acrylicstyle.storageBox.commands;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.acrylicstyle.storageBox.StorageBoxPlugin;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

public class BuyCommand {
    public static void onCommand(Player player, String[] args) {
        StorageBox storageBox = StorageBox.getStorageBox(player.getInventory().getItemInMainHand());
        if (storageBox == null) {
            player.sendMessage(ChatColor.RED + "現在手に持っているアイテムはStorage Boxではありません。");
            player.sendMessage(ChatColor.RED + "Storage Boxを手に持ってからもう一度試してください。");
            return;
        }
        ItemStack componentItemStack = storageBox.getComponentItemStack();
        long price = StorageBoxPlugin.getInstance().findBuyPrice(componentItemStack);
        if (price == 0) {
            player.sendMessage(ChatColor.RED + "このアイテムは購入できません。");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[0]);
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "数値を指定してください。");
            return;
        }
        if (amount < 0) {
            player.sendMessage(ChatColor.RED + "マイナスの値を指定することはできません。");
            return;
        }
        if (storageBox.getAmount() + amount < 0) {
            player.sendMessage(ChatColor.RED + "オーバーフローするため購入できません。");
            return;
        }
        long money = amount * price;
        EconomyResponse response = StorageBoxPlugin.getEconomy().withdrawPlayer(player, money);
        if (response.transactionSuccess()) {
            storageBox.setAmount(storageBox.getAmount() + amount);
            player.getInventory().setItemInMainHand(storageBox.getItemStack());
            player.sendMessage(ChatColor.GREEN + "アイテムを" + ChatColor.RED + amount + ChatColor.GREEN + "個購入しました。");
            player.sendMessage(ChatColor.GREEN + String.valueOf(money) + "円が口座から引き出されました");
        } else {
            player.sendMessage(ChatColor.RED + "購入に失敗しました。お金が足りない可能性があります。(" + response.errorMessage + ")");
        }
    }
}
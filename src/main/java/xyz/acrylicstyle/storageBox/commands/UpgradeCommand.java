package xyz.acrylicstyle.storageBox.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xyz.acrylicstyle.storageBox.utils.StorageBoxUpgrader;

/**
 * /sb upgrade: rewrites the old Storage Boxes (1.15.2...) in the sender's inventory and ender chest.
 * /sb upgrade all: the same for every online player (storagebox.op or console).
 */
public class UpgradeCommand {
    public static void onCommand(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("all")) {
            if (sender instanceof Player && !sender.hasPermission("storagebox.op")) {
                sender.sendMessage(ChatColor.RED + "権限がありません。");
                return;
            }
            int players = 0;
            int boxes = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                int count = StorageBoxUpgrader.upgradePlayer(player);
                if (count > 0) players++;
                boxes += count;
            }
            sender.sendMessage(ChatColor.GREEN + "オンラインプレイヤーの古いStorage Boxを" + boxes + "個更新しました。"
                    + ChatColor.GRAY + "（" + players + "人）");
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("/sb upgrade all");
            return;
        }
        int count = StorageBoxUpgrader.upgradePlayer((Player) sender);
        sender.sendMessage(count > 0
                ? ChatColor.GREEN + "インベントリとエンダーチェストの古いStorage Boxを" + count + "個更新しました。"
                : ChatColor.GRAY + "更新が必要なStorage Boxはありませんでした。");
    }
}

package xyz.acrylicstyle.storageBox;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.acrylicstyle.storageBox.commands.*;
import xyz.acrylicstyle.storageBox.utils.StorageBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RootCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length == 0) {
                sender.sendMessage("/sb give <player>");
                return true;
            }
            if (args[0].equals("give") && args.length >= 2) {
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player != null) {
                    player.getInventory().addItem(StorageBox.getNewStorageBox().getItemStack());
                }
            }
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        List<String> argsList = new ArrayList<>(Arrays.asList(args));
        argsList.remove(0);
        String[] slicedArgs = argsList.toArray(new String[0]);
        if(args[0].equalsIgnoreCase("autobuy")) {
            AutoBuyCommand.onCommand(player);
        } else if (args[0].equalsIgnoreCase("autocollect")) {
            AutoCollectCommand.onCommand(player);
        } else if (args[0].equalsIgnoreCase("bypass") && player.hasPermission("storagebox.op")) {
            BypassCommand.onCommand(player);
        } else if (args[0].equalsIgnoreCase("changetype")) {
            ChangeTypeCommand.onCommand(player);
        } else if (args[0].equalsIgnoreCase("collect")) {
            CollectCommand.onCommand(player);
        } else if (args[0].equalsIgnoreCase("convert")) {
            ConvertStorageBoxCommand.onCommand(player);
        } else if (args[0].equalsIgnoreCase("extract")) {
            ExtractCommand.onCommand(player, slicedArgs);
        } else if (args[0].equalsIgnoreCase("new")) {
            NewCommand.onCommand(player, slicedArgs);
        } else if (args[0].equalsIgnoreCase("setamount") && player.hasPermission("storagebox.op")) {
            SetAmountCommand.onCommand(player, slicedArgs);
        } else if (args[0].equalsIgnoreCase("settype") && player.hasPermission("storagebox.op")) {
            SetTypeCommand.onCommand(player, slicedArgs);
        } else if (args[0].equalsIgnoreCase("sell")) {
            SellCommand.onCommand(player, slicedArgs);
        } else if (args[0].equalsIgnoreCase("buy")) {
            BuyCommand.onCommand(player, slicedArgs);
        } else if (args[0].equalsIgnoreCase("shop")) {
            ShopCommand.onCommand(player);
        } if (args[0].equalsIgnoreCase("gomi")) {
            GomiCommand.onCommand(player, slicedArgs);
        }else if (args[0].equalsIgnoreCase("merge")) {
            MergeCommand.onCommand(player, slicedArgs);
        }else {
            sendHelp(sender);
        }
        return true;
    }

    public static void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "------------------------------");
        sender.sendMessage(help("autobuy", "残量0で設置時，自動購入して設置を継続します。"));
        sender.sendMessage(help("autocollect", "同種アイテムの自動回収を切り替えます。"));
        sender.sendMessage(help("changetype", "オフハンドのアイテムをStorage Boxの中身にします。"));
        sender.sendMessage(help("collect", "足元の同種アイテムをStorage Boxへ回収します。"));
        sender.sendMessage(help("convert", "Storage Boxの見た目を更新します。"));
        sender.sendMessage(help("extract <数>", "Storage Boxから中身を取り出します。"));
        sender.sendMessage(help("new", "新しいStorage Boxを取得します。"));
        sender.sendMessage(help("sell [数]", "Storage Box内のアイテムを売却します。"));
        sender.sendMessage(help("buy [数]", "Storage Box内のアイテムを購入します。"));
        sender.sendMessage(help("shop", "Storage Boxショップを開きます。"));
        sender.sendMessage(help("gomi [数]", "Storage Boxの中身を破棄します。"));
        sender.sendMessage(help("merge [数]", "オフハンドのStorage Boxをメインハンドに結合します。"));
        if (sender.hasPermission("storagebox.op")) {
            sender.sendMessage(help("bypass", "アイテムチェックなどを無視します。[OP]"));
            sender.sendMessage(help("setamount <amount>", "アイテムの数を設定します。[OP]"));
            sender.sendMessage(help("settype <Material>", "アイテムの種類を設定します。[OP]"));
        }
        sender.sendMessage(ChatColor.GOLD + "------------------------------");
    }

    private static String help(String command, String description) {
        return ChatColor.YELLOW + "/storage " + command + ChatColor.GRAY + " - " + ChatColor.AQUA + description;
    }
}

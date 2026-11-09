package xyz.acrylicstyle.storageBox.listener;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import xyz.acrylicstyle.storageBox.utils.StorageBoxUpgrader;

/**
 * Rewrites old Storage Boxes automatically (config {@code legacy-upgrade.auto}): in the player's inventory and ender
 * chest after joining or changing worlds (late enough for inventory sync plugins such as AziSync), and in any
 * inventory the player opens (chests, shulker boxes, EnderChestPlus pages...).
 */
public class LegacyUpgradeListener implements Listener {
    /** Ticks after joining; AziSync applies the synced inventory after the join event. */
    private static final long JOIN_DELAY = 100L;

    private final Plugin plugin;

    public LegacyUpgradeListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        schedule(e.getPlayer(), JOIN_DELAY);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        schedule(e.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!isStorage(e.getInventory().getHolder())) return;
        // next tick: plugins such as EnderChestPlus fill their inventory around the event
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(e.getPlayer() instanceof Player) || !((Player) e.getPlayer()).isOnline()) return;
            log((Player) e.getPlayer(), StorageBoxUpgrader.upgradeInventory(e.getInventory()));
        });
    }

    /**
     * Real storage only: containers, entities (minecarts, players) and plain inventories without a holder (such as
     * EnderChestPlus pages). Plugin GUIs with their own holder (shops...) are left alone, since they may compare the
     * items they show.
     */
    private static boolean isStorage(InventoryHolder holder) {
        return holder == null || holder instanceof BlockState || holder instanceof DoubleChest || holder instanceof Entity;
    }

    private void schedule(Player player, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) log(player, StorageBoxUpgrader.upgradePlayer(player));
        }, delay);
    }

    private void log(Player player, int count) {
        if (count > 0) plugin.getLogger().info("Rewrote " + count + " old Storage Box(es) of " + player.getName());
    }
}

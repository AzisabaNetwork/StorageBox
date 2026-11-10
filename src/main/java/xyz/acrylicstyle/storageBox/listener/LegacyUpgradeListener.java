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
import org.bukkit.scheduler.BukkitRunnable;
import xyz.acrylicstyle.storageBox.utils.StorageBoxUpgrader;

import java.lang.reflect.Method;

/**
 * Rewrites old Storage Boxes automatically (config {@code legacy-upgrade.auto}): in the player's inventory and ender
 * chest after joining or changing worlds (once AziSync has applied the synced data, when it is installed), and in any
 * inventory the player opens (chests, shulker boxes, EnderChestPlus pages...).
 */
public class LegacyUpgradeListener implements Listener {
    /** Ticks after joining when AziSync is not installed. */
    private static final long JOIN_DELAY = 100L;
    /** Ticks between checks whether AziSync has loaded the player's data. */
    private static final long AZISYNC_POLL = 20L;
    /** Checks before giving up; AziSync itself stops waiting (or kicks the player) well before this. */
    private static final int AZISYNC_MAX_POLLS = 120;

    private final Plugin plugin;

    public LegacyUpgradeListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (azisync() == null) {
            schedule(player, JOIN_DELAY);
            return;
        }
        new BukkitRunnable() {
            int polls = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Boolean loaded = isLoadedByAziSync(player);
                if (loaded == null || loaded) {
                    cancel();
                    log(player, StorageBoxUpgrader.upgradePlayer(player));
                } else if (++polls >= AZISYNC_MAX_POLLS) {
                    cancel();
                    plugin.getLogger().info("AziSync did not load the data of " + player.getName()
                            + "; old Storage Boxes are rewritten when they are used or their inventory is opened");
                }
            }
        }.runTaskTimer(plugin, AZISYNC_POLL, AZISYNC_POLL);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        // before AziSync has loaded, the inventory is about to be replaced (the join task handles it)
        if (Boolean.FALSE.equals(isLoadedByAziSync(e.getPlayer()))) return;
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

    private static Plugin azisync() {
        Plugin p = Bukkit.getPluginManager().getPlugin("AziSync");
        return p != null && p.isEnabled() ? p : null;
    }

    /**
     * Whether AziSync has applied the player's synced data ({@code AziSync.syncManager.isLoaded(player)}).
     * Null when AziSync is not installed or its API could not be called.
     */
    private Boolean isLoadedByAziSync(Player player) {
        Plugin azisync = azisync();
        if (azisync == null) return null;
        try {
            Object syncManager = azisync.getClass().getMethod("getSyncManager").invoke(azisync);
            Method isLoaded = syncManager.getClass().getMethod("isLoaded", Player.class);
            return (Boolean) isLoaded.invoke(syncManager, player);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Could not ask AziSync whether " + player.getName() + " is loaded: " + ex);
            return null;
        }
    }
}

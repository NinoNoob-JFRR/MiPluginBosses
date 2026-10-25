package com.tunombre.bossplugin.task;

import com.tunombre.bossplugin.Main;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Tick de pasivas, brujula y refresco del menu de estadisticas.
 */
public class StatsTask implements Runnable {

    private final Main plugin;
    private int counter;

    public StatsTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getAbilityManager().tick();
        counter++;
        int interval = Math.max(1, plugin.getConfigManager().getInt("config.yml",
                "performance.stats-menu-interval-ticks", 20));
        if (counter % interval != 0) {
            return;
        }
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            plugin.getMenuManager().refreshStats(player);
            updateCompass(player);
        }
    }

    private void updateCompass(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (!plugin.getItems().isType(main, "tracking_compass")
                && !plugin.getItems().isType(off, "tracking_compass")) {
            return;
        }
        var egg = plugin.getEggManager().nearest(player);
        if (egg != null && egg.location() != null) {
            player.setCompassTarget(egg.location());
        }
    }
}

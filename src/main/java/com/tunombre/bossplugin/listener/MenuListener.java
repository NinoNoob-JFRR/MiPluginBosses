package com.tunombre.bossplugin.listener;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.manager.AbilityManager;
import com.tunombre.bossplugin.manager.MenuManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Clicks y cierre de menus personalizados.
 */
public class MenuListener implements Listener {

    private final Main plugin;

    public MenuListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MenuManager.MenuHolder) {
            plugin.getMenuManager().handleClick(player, event);
            return;
        }
        if (holder instanceof AbilityManager.WaypointHolder waypoint) {
            plugin.getAbilityManager().handleWaypointClick(player, waypoint, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MenuManager.MenuHolder || holder instanceof AbilityManager.WaypointHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AbilityManager.BagHolder bag) {
            plugin.getAbilityManager().saveBag(bag);
        }
        if (event.getPlayer() instanceof Player player) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.getStatsManager().apply(player));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getMenuManager().hasPendingInput(player)) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin,
                () -> plugin.getMenuManager().handleChatInput(player, message));
    }
}

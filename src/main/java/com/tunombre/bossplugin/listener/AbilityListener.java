package com.tunombre.bossplugin.listener;

import com.tunombre.bossplugin.Main;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Aplicacion de esencias, habilidades activas, brujula y encantamientos.
 */
public class AbilityListener implements Listener {

    private final Main plugin;

    public AbilityListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        if (plugin.getItems().isType(item, "tracking_compass")) {
            Block block = event.getClickedBlock();
            if (block != null && plugin.getEggManager().isEggBlock(block)) {
                return;
            }
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                plugin.getEggManager().useCompass(player);
            }
            return;
        }

        if (plugin.getItems().isType(item, "ability_essence")) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                plugin.getAbilityManager().tryApply(player);
            }
            return;
        }

        if ("active_ability".equals(plugin.getItems().itemType(item))) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR) {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    plugin.getAbilityManager().saveWaypoint(player);
                } else {
                    plugin.getAbilityManager().tryUseActive(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (plugin.getBossManager().isBoss(player)) {
            return;
        }
        plugin.getAbilityManager().onHit(player, victim, event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getAbilityManager().onBlockBreak(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        plugin.getAbilityManager().onItemDamage(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if ("active_ability".equals(plugin.getItems().itemType(event.getItem()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAbilityManager().isRenaming(player)) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        org.bukkit.Bukkit.getScheduler().runTask(plugin,
                () -> plugin.getAbilityManager().handleRename(player, message));
    }
}

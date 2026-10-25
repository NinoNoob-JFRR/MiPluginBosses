package com.tunombre.bossplugin.listener;

import com.tunombre.bossplugin.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Mob Tiers, loot custom y XP de mobs.
 */
public class MobListener implements Listener {

    private final Main plugin;

    public MobListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        plugin.getMobTierManager().handleSpawn(entity);
        plugin.getLootManager().rollOnSpawn(entity);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (plugin.getBossManager().isBoss(entity)) {
            return;
        }
        plugin.getLootManager().dropOnDeath(entity);

        double multiplier = plugin.getMobTierManager().xpMultiplier(entity);
        if (multiplier > 1.0) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * multiplier));
        }

        Player killer = entity.getKiller();
        if (killer != null) {
            plugin.getStatsManager().onKill(killer, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (plugin.getMobTierManager().tier(entity) <= 0) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getMobTierManager().updateName(entity));
    }
}

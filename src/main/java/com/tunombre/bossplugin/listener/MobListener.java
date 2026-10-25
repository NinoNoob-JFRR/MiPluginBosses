package com.tunombre.bossplugin.listener;

import com.tunombre.bossplugin.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayList;
import java.util.List;

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
        // Los jefes y esbirros del plugin se configuran a mano. Evitamos que
        // CreatureSpawnEvent los convierta en mobs con tier/loot normal antes
        // de que BossManager les ponga su PDC.
        if (plugin.getBossManager().isSpawningPluginEntity()) {
            return;
        }
        plugin.getMobTierManager().handleSpawn(entity);
        plugin.getLootManager().rollOnSpawn(entity);
        if (plugin.getMobTierManager().tier(entity) > 0) {
            // Algunos clientes reciben la entidad antes de que el nombre
            // personalizado este disponible. Se reenvia un tick despues.
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getMobTierManager().refresh(entity));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkLoad(ChunkLoadEvent event) {
        List<LivingEntity> tiered = new ArrayList<>();
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity living
                    && plugin.getMobTierManager().tier(living) > 0) {
                tiered.add(living);
            }
        }
        if (tiered.isEmpty()) {
            return;
        }
        // Se hace en el siguiente tick para que el cliente ya tenga la entidad
        // registrada y muestre el nombre con la vida actual.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (LivingEntity living : tiered) {
                plugin.getMobTierManager().refresh(living);
            }
        });
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

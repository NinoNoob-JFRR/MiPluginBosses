package com.tunombre.bossplugin.listener;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.manager.BossManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Danio, muerte y reinicio de los jefes.
 */
public class BossListener implements Listener {

    private final Main plugin;

    public BossListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        BossManager bossManager = plugin.getBossManager();

        if (event.getEntity() instanceof LivingEntity victim && bossManager.isBoss(victim)) {
            Player damager = player(event.getDamager());
            if (damager != null) {
                bossManager.onBossDamaged(event, victim, damager);
            }
        }

        LivingEntity attacker = living(event.getDamager());
        if (attacker != null && bossManager.isBoss(attacker)
                && event.getEntity() instanceof LivingEntity victim) {
            bossManager.onBossDealtDamage(attacker, victim, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        if (plugin.getBossManager().isBoss(event.getEntity())) {
            plugin.getBossManager().onBossDeath(event.getEntity(), event);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.getBossManager().onPlayerDeath(event.getEntity());
    }

    private Player player(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private LivingEntity living(Entity source) {
        if (source instanceof LivingEntity living) {
            return living;
        }
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }
}

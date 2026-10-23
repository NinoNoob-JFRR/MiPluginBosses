package com.tunombre.bossplugin.listener;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.boss.Boss;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.Random;

/**
 * Escucha muertes de jefes para entregar botín y avisar al servidor.
 */
public class BossListener implements Listener {

    private final Main plugin;
    private final Random random = new Random();

    public BossListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (!plugin.getBossManager().isBoss(entity)) {
            return;
        }

        Boss boss = plugin.getBossManager().getActiveBoss(entity);
        if (boss == null) {
            return;
        }

        // Remover registro del jefe activo
        plugin.getBossManager().removeActiveBoss(entity.getUniqueId());

        // Anunciar muerte en el servidor
        Component announce = Component.text("☠️ ")
                .append(Component.text(boss.getDisplayName()))
                .append(Component.text(" ha sido derrotado por un héroe!").color(NamedTextColor.RED));
        plugin.getServer().broadcast(announce);

        // Soltar botín del jefe
        entity.getWorld().dropItemNaturally(entity.getLocation(),
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.EXPERIENCE_BOTTLE, 10)); // bonus de experiencia

        for (org.bukkit.inventory.ItemStack drop : boss.getDrops()) {
            if (random.nextDouble() <= boss.getDropChance()) {
                entity.getWorld().dropItemNaturally(entity.getLocation(), drop);
            }
        }

        // Limpiar drops del evento original para no duplicar (opcional)
        event.getDrops().clear();
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        // Si quieres evitar que jefes normales aparezcan naturalmente, aquí podrías cancelarlo.
        // Por ahora no hacemos nada.
    }
}

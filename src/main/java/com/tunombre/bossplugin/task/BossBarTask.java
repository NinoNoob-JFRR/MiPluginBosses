package com.tunombre.bossplugin.task;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.boss.Boss;
import com.tunombre.bossplugin.boss.BossManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Actualiza las barras de jefe sobre la cabeza de cada jefe activo cada 0.1 segundos.
 */
public class BossBarTask implements Runnable {

    private final Main plugin;
    private BossManager bossManager;
    private final Map<UUID, BossBar> bossBars = new java.util.HashMap<>();

    public BossBarTask(Main plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    public void setBossManager(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @Override
    public void run() {
        // Limpiar barras de entidades que ya no existen o ya no son jefes activos
        bossBars.keySet().removeIf(uuid -> {
            LivingEntity entity = findEntity(uuid);
            return entity == null || entity.isDead() || !bossManager.isBoss(entity);
        });

        // Actualizar barra de cada jefe activo
        bossManager.getActiveBosses().forEach((uuid, boss) -> {
            LivingEntity entity = findEntity(uuid);
            if (entity == null || entity.isDead()) {
                return;
            }

            // Mantener oculta la barra vanilla (Wither/Dragon) para no duplicar
            if (entity instanceof org.bukkit.entity.Boss vanillaBoss) {
                vanillaBoss.getBossBar().setVisible(false);
            }

            BossBar bar = bossBars.computeIfAbsent(uuid, id -> createBar(boss));
            updateBar(entity, bar, boss);
        });
    }

    private BossBar createBar(Boss boss) {
        return plugin.getServer().createBossBar(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().serialize(boss.getDisplayName()),
                BarColor.RED,
                BarStyle.SOLID
        );
    }

    private void updateBar(LivingEntity entity, BossBar bar, Boss boss) {
        double health = entity.getHealth();
        double max = entity.getMaxHealth();
        bar.setProgress(max > 0 ? Math.max(0.0, Math.min(1.0, health / max)) : 0.0);

        // Mostrar vida restante / vida total en el titulo de la barra
        int restante = (int) Math.ceil(Math.max(0.0, health));
        int total = (int) Math.round(max);
        Component titulo = boss.getDisplayName()
                .append(Component.text(" " + restante + "/" + total, NamedTextColor.WHITE));
        String tituloLegacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(titulo);
        if (!tituloLegacy.equals(bar.getTitle())) {
            bar.setTitle(tituloLegacy);
        }

        double maxDist = plugin.getConfig().getInt("bossbar-max-distance", 100);
        double maxDistSq = maxDist * maxDist;
        org.bukkit.Location bossLoc = entity.getLocation();

        // Añadir jugadores cercanos
        entity.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(bossLoc) <= maxDistSq)
                .filter(p -> !bar.getPlayers().contains(p))
                .forEach(bar::addPlayer);

        // Remover jugadores lejanos
        bar.getPlayers().forEach(p -> {
            if (p.getLocation().distanceSquared(bossLoc) > maxDistSq) {
                bar.removePlayer(p);
            }
        });
    }

    /**
     * Registra una barra para un jefe recién spawnado.
     */
    public void registerBar(LivingEntity entity, Boss boss) {
        UUID uuid = entity.getUniqueId();
        if (!bossBars.containsKey(uuid)) {
            BossBar bar = createBar(boss);
            bossBars.put(uuid, bar);
        }
    }

    public void removeBar(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private LivingEntity findEntity(UUID uuid) {
        org.bukkit.entity.Entity entity = plugin.getServer().getEntity(uuid);
        return entity instanceof LivingEntity living ? living : null;
    }
}

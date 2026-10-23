package com.tunombre.bossplugin.boss;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.task.BossBarTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestiona el registro, spawn y limpieza de jefes personalizados.
 */
public class BossManager {

    private final Main plugin;
    private final BossBarTask bossBarTask;
    private final Map<String, Boss> bosses = new HashMap<>();
    private final Map<UUID, Boss> activeBosses = new HashMap<>(); // entidad viva -> jefe

    public BossManager(Main plugin, BossBarTask bossBarTask) {
        this.plugin = plugin;
        this.bossBarTask = bossBarTask;
    }

    /**
     * Registra los jefes predeterminados del plugin.
     */
    public void registerDefaultBosses() {
        // Rey Wither - jefe basado en Wither
        registerBoss(new Boss("rey_wither", "§c§lRey Wither", EntityType.WITHER,
                        800.0, 14.0, 0.22, 8.0, 4.0, 0.75)
                .addRareDrop(org.bukkit.Material.NETHERITE_SWORD, "Espada del Rey Wither",
                        "Forjada en las profundidades del Nether", 1, true)
                .addRareDrop(org.bukkit.Material.NETHERITE_CHESTPLATE, "Coraza del Wither",
                        "Protección contra explosiones", 1, true)
                .addDrop(org.bukkit.Material.NETHER_STAR, "Fragmento de Estrella del Nether", 3));

        // Señor del End - jefe basado en Dragón del End (difícil de domar, usamos Enderman como base visual si es necesario)
        // Nota: ENDER_DRAGON es complejo de spawnear controladamente. Aquí usamos una versión mejorada de Enderman.
        registerBoss(new Boss("senor_end", "§5§lSeñor del End", EntityType.ENDERMAN,
                        600.0, 12.0, 0.30, 10.0, 6.0, 0.80)
                .addRareDrop(org.bukkit.Material.ENDER_PEARL, "Perla del End Campeón",
                        "Teletransportación instantánea", 16, true)
                .addRareDrop(org.bukkit.Material.ELYTRA, "Élitro Vacío", "Vuela entre dimensiones", 1, true)
                .addDrop(org.bukkit.Material.DRAGON_BREATH, "Aliento de Dragón", 4));

        // Emperador Blaze - jefe basado en Blaze
        registerBoss(new Boss("emperador_blaze", "§c§lEmperador Blaze", EntityType.BLAZE,
                        400.0, 10.0, 0.28, 4.0, 2.0, 0.70)
                .addRareDrop(org.bukkit.Material.BLAZE_ROD, "Vara de Fuego Épica",
                        "Lanza bolas de fuego poderosas", 1, true)
                .addRareDrop(org.bukkit.Material.MAGMA_CREAM, "Crema de Magma Pura",
                        "Ingrediente de pociones legendarias", 8, true)
                .addDrop(org.bukkit.Material.GLOWSTONE_DUST, "Polvo de Piedra Luminosa", 16));

        // Guardián Ancestral - jefe basado en Guardián
        registerBoss(new Boss("guardián_ancestral", "§3§lGuardián Ancestral", EntityType.GUARDIAN,
                        500.0, 11.0, 0.20, 6.0, 3.0, 0.65)
                .addRareDrop(org.bukkit.Material.PRISMARINE_SHARD, "Fragmento de Prismarina Antigua",
                        "Reliquia del océano profundo", 4, true)
                .addRareDrop(org.bukkit.Material.HEART_OF_THE_SEA, "Corazón del Océano",
                        "Concede poder sobre las mareas", 1, true)
                .addDrop(org.bukkit.Material.PRISMARINE_CRYSTALS, "Cristales de Prismarina", 8));

        plugin.getLogger().info("Registrados " + bosses.size() + " jefes predeterminados.");
    }

    public void registerBoss(Boss boss) {
        bosses.put(boss.getKey(), boss);
    }

    public Boss getBoss(String key) {
        return bosses.get(key);
    }

    public Map<String, Boss> getBosses() {
        return bosses;
    }

    /**
     * Invoca un jefe en la ubicación del jugador.
     */
    public void spawnBoss(Player player, String bossKey) {
        Boss boss = getBoss(bossKey);
        if (boss == null) {
            player.sendMessage(Component.text("Jefe no encontrado: " + bossKey).color(NamedTextColor.RED));
            return;
        }

        org.bukkit.World world = player.getWorld();
        org.bukkit.Location loc = player.getLocation();

        LivingEntity entity = (LivingEntity) world.spawnEntity(loc, boss.getEntityType());
        entity.setCustomName(boss.getDisplayName());
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);

        // Aplicar atributos
        applyAttributes(entity, boss);

        // Aplicar equipo básico si es humanoide
        applyEquipment(entity, boss);

        // Registrar como jefe activo
        activeBosses.put(entity.getUniqueId(), boss);

        // Crear barra de jefe
        bossBarTask.registerBar(entity, boss);

        plugin.getLogger().info("Jefe invocado: " + boss.getKey() + " (" + entity.getUniqueId() + ")");

        player.sendMessage(Component.text("¡Has invocado a ")
                .append(Component.text(boss.getDisplayName()))
                .append(Component.text("!"));
    }

    private void applyAttributes(LivingEntity entity, Boss boss) {
        if (boss.hasAttribute(Attribute.MAX_HEALTH)) {
            var attr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(boss.getMaxHealth());
            entity.setHealth(entity.getMaxHealth());
        }
        if (boss.hasAttribute(Attribute.ATTACK_DAMAGE)) {
            var attr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attr != null) attr.setBaseValue(boss.getAttackDamage());
        }
        if (boss.hasAttribute(Attribute.MOVEMENT_SPEED)) {
            var attr = entity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attr != null) attr.setBaseValue(boss.getMovementSpeed());
        }
        if (boss.hasAttribute(Attribute.ARMOR)) {
            var attr = entity.getAttribute(Attribute.ARMOR);
            if (attr != null) attr.setBaseValue(boss.getArmor());
        }
        if (boss.hasAttribute(Attribute.ARMOR_TOUGHNESS)) {
            var attr = entity.getAttribute(Attribute.ARMOR_TOUGHNESS);
            if (attr != null) attr.setBaseValue(boss.getArmorToughness());
        }
    }

    private void applyEquipment(LivingEntity entity, Boss boss) {
        EntityEquipment equip = entity.getEquipment();
        if (equip == null) return;

        switch (boss.getEntityType()) {
            case ZOMBIE, HUSK, DROWNED, SKELETON, WITHER_SKELETON, PIGLIN, VINDICATOR -> {
                equip.setHelmet(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHERITE_HELMET));
                equip.setChestplate(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHERITE_CHESTPLATE));
                equip.setLeggings(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHERITE_LEGGINGS));
                equip.setBoots(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHERITE_BOOTS));
            }
            case BLAZE -> {
                // Los blaze no usan armadura, pero se puede añadir item en mano si aplica
            }
            default -> {
                // Otros tipos no reciben armadura automática
            }
        }
    }

    /**
     * Obtiene el jefe asociado a una entidad viva, si existe.
     */
    public Boss getActiveBoss(LivingEntity entity) {
        return activeBosses.get(entity.getUniqueId());
    }

    /**
     * Elimina el registro de un jefe activo (al morir o desaparecer).
     */
    public void removeActiveBoss(UUID entityId) {
        activeBosses.remove(entityId);
        bossBarTask.removeBar(entityId);
    }

    public Map<UUID, Boss> getActiveBosses() {
        return activeBosses;
    }

    public boolean isBoss(LivingEntity entity) {
        return activeBosses.containsKey(entity.getUniqueId());
    }

    public void cleanup() {
        activeBosses.clear();
        bosses.clear();
    }
}

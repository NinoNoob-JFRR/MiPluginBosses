package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.util.Keys;
import com.tunombre.bossplugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Asigna un Mob Tier aleatorio a los mobs y escala sus estadisticas.
 * El tier se calcula una sola vez, al spawnear, y se guarda en el PDC.
 */
public class MobTierManager {

    public record MobStats(double health, double damage, double armor, double toughness, double speed, double xp) {
    }

    private final Main plugin;
    private final ConfigManager config;
    private final Random random = new Random();

    private boolean enabled;
    private final Map<Integer, Integer> distribution = new LinkedHashMap<>();
    private final Map<Integer, MobStats> stats = new LinkedHashMap<>();
    private final Map<Integer, String> colors = new LinkedHashMap<>();
    private final Set<EntityType> extraTypes = EnumSet.noneOf(EntityType.class);
    private final Set<EntityType> excludedTypes = EnumSet.noneOf(EntityType.class);
    private final Set<String> categories = new java.util.LinkedHashSet<>();
    private boolean excludeTamed;
    private boolean alwaysShowName;
    private String nameFormat;
    private int totalWeight;
    private double statsMultiplier = 1.0;

    public MobTierManager(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        distribution.clear();
        stats.clear();
        colors.clear();
        extraTypes.clear();
        excludedTypes.clear();
        categories.clear();

        enabled = config.get("mobs.yml").getBoolean("mobs.enabled", true);
        statsMultiplier = Math.max(0.0, config.get("mobs.yml").getDouble("mobs.stats-multiplier", 1.0));

        ConfigurationSection dist = config.get("mobs.yml").getConfigurationSection("mobs.distribution");
        if (dist != null) {
            for (String key : dist.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    int weight = dist.getInt(key, 0);
                    if (weight > 0) {
                        distribution.put(tier, weight);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        totalWeight = distribution.values().stream().mapToInt(Integer::intValue).sum();

        ConfigurationSection statSection = config.get("mobs.yml").getConfigurationSection("mobs.stats");
        if (statSection != null) {
            for (String key : statSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    ConfigurationSection s = statSection.getConfigurationSection(key);
                    if (s == null) {
                        continue;
                    }
                    stats.put(tier, new MobStats(
                            s.getDouble("health", 1.0) * statsMultiplier,
                            s.getDouble("damage", 1.0) * statsMultiplier,
                            s.getDouble("armor", 0.0) * statsMultiplier,
                            s.getDouble("toughness", 0.0) * statsMultiplier,
                            s.getDouble("speed", 0.0),
                            s.getDouble("xp", 1.0) * statsMultiplier
                    ));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        ConfigurationSection colorSection = config.get("tiers.yml").getConfigurationSection("mob-tier-colors");
        if (colorSection != null) {
            for (String key : colorSection.getKeys(false)) {
                try {
                    colors.put(Integer.parseInt(key), colorSection.getString(key, "&f"));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        for (String name : config.get("mobs.yml").getStringList("mobs.targeting.categories")) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String category = name.toUpperCase(java.util.Locale.ROOT);
            switch (category) {
                case "MONSTER", "GOLEM", "SLIME", "ANIMAL", "WATER_MOB", "AMBIENT", "BOSS" ->
                        categories.add(category);
                default -> plugin.getLogger().warning("Categoria de mob desconocida en mobs.yml: " + name);
            }
        }
        if (categories.isEmpty()) {
            categories.add("MONSTER");
            categories.add("GOLEM");
            categories.add("SLIME");
        }
        for (String name : config.get("mobs.yml").getStringList("mobs.targeting.extra-types")) {
            EntityType type = type(name);
            if (type != null) {
                extraTypes.add(type);
            }
        }
        for (String name : config.get("mobs.yml").getStringList("mobs.targeting.exclude-types")) {
            EntityType type = type(name);
            if (type != null) {
                excludedTypes.add(type);
            }
        }

        excludeTamed = config.get("mobs.yml").getBoolean("mobs.targeting.exclude-tamed", true);
        alwaysShowName = config.get("mobs.yml").getBoolean("mobs.name.always-show", true);
        nameFormat = config.get("mobs.yml").getString("mobs.name.format", "%color%[T%tier%] &f%name% &c%health%&7/&c%max_health%");
    }

    public boolean enabled() {
        return enabled;
    }

    public int tier(Entity entity) {
        if (entity == null) {
            return 0;
        }
        Integer value = entity.getPersistentDataContainer().get(Keys.MOB_TIER, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    public boolean hasTier(Entity entity) {
        return tier(entity) > 0;
    }

    public boolean isEligible(LivingEntity entity) {
        if (!enabled || entity == null || entity instanceof Player) {
            return false;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(Keys.BOSS_ID, PersistentDataType.STRING) || pdc.has(Keys.MINION, PersistentDataType.BYTE)) {
            return false;
        }
        if (excludeTamed && entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        EntityType type = entity.getType();
        if (excludedTypes.contains(type)) {
            return false;
        }
        if (extraTypes.contains(type)) {
            return true;
        }
        return matchesCategory(entity);
    }

    private boolean matchesCategory(LivingEntity entity) {
        return (categories.contains("MONSTER") && entity instanceof Monster)
                || (categories.contains("GOLEM") && entity instanceof Golem)
                || (categories.contains("SLIME") && entity instanceof Slime)
                || (categories.contains("ANIMAL") && entity instanceof Animals)
                || (categories.contains("WATER_MOB") && entity instanceof WaterMob)
                || (categories.contains("AMBIENT") && entity instanceof Ambient)
                || (categories.contains("BOSS") && entity instanceof Boss);
    }

    /**
     * Se llama al spawnear un mob. Calcula el tier y aplica los stats una sola vez.
     */
    public void handleSpawn(LivingEntity entity) {
        if (!isEligible(entity)) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(Keys.MOB_TIER, PersistentDataType.INTEGER)) {
            applyStats(entity);
            return;
        }
        int tier = rollTier();
        if (tier <= 0) {
            return;
        }
        MobStats mobStats = stats.getOrDefault(tier, new MobStats(1, 1, 0, 0, 0, 1));
        pdc.set(Keys.MOB_TIER, PersistentDataType.INTEGER, tier);
        pdc.set(Keys.MOB_BASE_NAME, PersistentDataType.STRING, entity.getName());
        pdc.set(Keys.MOB_XP_MULT, PersistentDataType.DOUBLE, mobStats.xp());
        apply(entity, tier, mobStats);
    }

    public int rollTier() {
        if (totalWeight <= 0) {
            return 0;
        }
        int roll = random.nextInt(totalWeight);
        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return distribution.keySet().stream().findFirst().orElse(0);
    }

    /**
     * Aplica los stats si el mob ya tiene tier pero aun no se aplicaron
     * (por ejemplo, tras cargar un chunk en un arranque nuevo).
     */
    public void applyStats(LivingEntity entity) {
        int tier = tier(entity);
        if (tier <= 0) {
            return;
        }
        apply(entity, tier, stats.getOrDefault(tier, new MobStats(1, 1, 0, 0, 0, 1)));
    }

    private void apply(LivingEntity entity, int tier, MobStats mobStats) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(Keys.MOB_APPLIED, PersistentDataType.BYTE)) {
            updateName(entity);
            return;
        }
        pdc.set(Keys.MOB_APPLIED, PersistentDataType.BYTE, (byte) 1);

        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double base = health.getBaseValue();
            pdc.set(Keys.MOB_BASE_HEALTH, PersistentDataType.DOUBLE, base);
            health.setBaseValue(base * mobStats.health());
            entity.setHealth(health.getValue());
        }
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            double base = damage.getBaseValue();
            pdc.set(Keys.MOB_BASE_DAMAGE, PersistentDataType.DOUBLE, base);
            damage.setBaseValue(base * mobStats.damage());
        }
        AttributeInstance armor = entity.getAttribute(Attribute.ARMOR);
        if (armor != null) {
            double base = armor.getBaseValue();
            pdc.set(Keys.MOB_BASE_ARMOR, PersistentDataType.DOUBLE, base);
            armor.setBaseValue(base + mobStats.armor());
        }
        AttributeInstance toughness = entity.getAttribute(Attribute.ARMOR_TOUGHNESS);
        if (toughness != null) {
            double base = toughness.getBaseValue();
            pdc.set(Keys.MOB_BASE_TOUGHNESS, PersistentDataType.DOUBLE, base);
            toughness.setBaseValue(base + mobStats.toughness());
        }
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            double base = speed.getBaseValue();
            pdc.set(Keys.MOB_BASE_SPEED, PersistentDataType.DOUBLE, base);
            speed.setBaseValue(base * (1.0 + mobStats.speed()));
        }

        updateName(entity);
    }

    /**
     * Revierte los cambios de tier. Se usa con los jefes y esbirros del plugin.
     */
    public void clear(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        restore(entity, Attribute.MAX_HEALTH, pdc, Keys.MOB_BASE_HEALTH);
        restore(entity, Attribute.ATTACK_DAMAGE, pdc, Keys.MOB_BASE_DAMAGE);
        restore(entity, Attribute.ARMOR, pdc, Keys.MOB_BASE_ARMOR);
        restore(entity, Attribute.ARMOR_TOUGHNESS, pdc, Keys.MOB_BASE_TOUGHNESS);
        restore(entity, Attribute.MOVEMENT_SPEED, pdc, Keys.MOB_BASE_SPEED);
        pdc.remove(Keys.MOB_TIER);
        pdc.remove(Keys.MOB_APPLIED);
        pdc.remove(Keys.MOB_BASE_NAME);
        pdc.remove(Keys.MOB_XP_MULT);
        pdc.remove(Keys.MOB_BASE_HEALTH);
        pdc.remove(Keys.MOB_BASE_DAMAGE);
        pdc.remove(Keys.MOB_BASE_ARMOR);
        pdc.remove(Keys.MOB_BASE_TOUGHNESS);
        pdc.remove(Keys.MOB_BASE_SPEED);
        entity.customName(null);
        entity.setCustomNameVisible(false);
    }

    private void restore(LivingEntity entity, Attribute attribute, PersistentDataContainer pdc, org.bukkit.NamespacedKey key) {
        Double value = pdc.get(key, PersistentDataType.DOUBLE);
        if (value == null) {
            return;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Reaplica los stats pendientes y vuelve a enviar el nombre al cliente.
     * Se usa al spawnear (un tick despues) y al cargar chunks, porque el
     * cliente puede recibir el mob antes de que el nombre este listo.
     */
    public void refresh(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        int tier = tier(entity);
        if (tier <= 0) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(Keys.MOB_APPLIED, PersistentDataType.BYTE)) {
            apply(entity, tier, stats.getOrDefault(tier, new MobStats(1, 1, 0, 0, 0, 1)));
        } else {
            updateName(entity);
        }
    }

    public void updateName(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        int tier = tier(entity);
        if (tier <= 0) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String base = pdc.get(Keys.MOB_BASE_NAME, PersistentDataType.STRING);
        if (base == null || base.isBlank()) {
            base = prettify(entity.getType().name());
        }
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = health == null ? entity.getHealth() : health.getValue();
        String name = nameFormat
                .replace("%color%", colors.getOrDefault(tier, "&f"))
                .replace("%tier%", String.valueOf(tier))
                .replace("%name%", base)
                .replace("%health%", Text.format(Math.max(0, entity.getHealth())))
                .replace("%max_health%", Text.format(max));
        entity.customName(Text.c(name));
        entity.setCustomNameVisible(alwaysShowName);
    }

    public double xpMultiplier(LivingEntity entity) {
        Double value = entity.getPersistentDataContainer().get(Keys.MOB_XP_MULT, PersistentDataType.DOUBLE);
        return value == null ? 1.0 : value;
    }

    public String color(int tier) {
        return colors.getOrDefault(tier, "&f");
    }

    private EntityType type(String name) {
        if (name == null) {
            return null;
        }
        try {
            return EntityType.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Tipo de mob desconocido en mobs.yml: " + name);
            return null;
        }
    }

    private String prettify(String raw) {
        String lower = raw.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}

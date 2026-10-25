package com.tunombre.bossplugin.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Definicion de un jefe cargada desde bosses.yml.
 */
public class BossDefinition {

    private final String id;
    private final String name;
    private final int tier;
    private final String archetype;
    private final EntityType entityType;
    private final double health;
    private final double damage;
    private final double armor;
    private final double toughness;
    private final double speed;
    private final Map<String, String> equipment;
    private final List<String> abilities;
    private final int xp;

    public BossDefinition(String id, String name, int tier, String archetype, EntityType entityType,
                          double health, double damage, double armor, double toughness, double speed,
                          Map<String, String> equipment, List<String> abilities, int xp) {
        this.id = id;
        this.name = name;
        this.tier = tier;
        this.archetype = archetype;
        this.entityType = entityType;
        this.health = health;
        this.damage = damage;
        this.armor = armor;
        this.toughness = toughness;
        this.speed = speed;
        this.equipment = equipment;
        this.abilities = abilities;
        this.xp = xp;
    }

    public static BossDefinition fromConfig(String id, ConfigurationSection section) {
        ConfigurationSection stats = section.getConfigurationSection("stats");
        double health = stats == null ? 100 : stats.getDouble("health", 100);
        double damage = stats == null ? 5 : stats.getDouble("damage", 5);
        double armor = stats == null ? 0 : stats.getDouble("armor", 0);
        double toughness = stats == null ? 0 : stats.getDouble("toughness", 0);
        double speed = stats == null ? 0 : stats.getDouble("speed", 0);

        Map<String, String> equipment = new LinkedHashMap<>();
        ConfigurationSection eq = section.getConfigurationSection("equipment");
        if (eq != null) {
            for (String key : eq.getKeys(false)) {
                String value = eq.getString(key);
                if (value != null && !value.isBlank()) {
                    equipment.put(key.toLowerCase(Locale.ROOT), value);
                }
            }
        }

        return new BossDefinition(
                id,
                section.getString("name", id),
                section.getInt("tier", 1),
                section.getString("archetype", "BRUTE"),
                parseEntity(section.getString("entity", "ZOMBIE")),
                health,
                damage,
                armor,
                toughness,
                speed,
                equipment,
                new ArrayList<>(section.getStringList("abilities")),
                section.getInt("xp", 100)
        );
    }

    private static EntityType parseEntity(String name) {
        if (name == null) {
            return EntityType.ZOMBIE;
        }
        try {
            return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EntityType.ZOMBIE;
        }
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int tier() {
        return tier;
    }

    public String archetype() {
        return archetype;
    }

    public EntityType entityType() {
        return entityType;
    }

    public double health() {
        return health;
    }

    public double damage() {
        return damage;
    }

    public double armor() {
        return armor;
    }

    public double toughness() {
        return toughness;
    }

    public double speedBonus() {
        return speed;
    }

    public Map<String, String> equipment() {
        return equipment;
    }

    public List<String> abilities() {
        return abilities;
    }

    public int xp() {
        return xp;
    }
}

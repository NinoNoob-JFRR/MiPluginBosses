package com.tunombre.bossplugin.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Definicion de una habilidad de jugador cargada desde essence_abilities.yml.
 */
public class AbilityDefinition {

    private final String id;
    private final String name;
    private final String type;
    private final String applyTo;
    private final String description;
    private final String icon;
    private final int tier;
    private final int maxLevel;
    private final List<String> compatibleWith;
    private final String trigger;
    private final Map<Integer, Map<String, Object>> levels;
    private final Map<String, Object> cost;
    private final List<String> lore;
    private final List<String> appliedLore;
    private final String recipeId;
    private final Map<String, Object> evolution;
    private final int intervalSeconds;
    private final Map<String, Object> combat;
    private final Map<String, Object> waypoint;

    public AbilityDefinition(String id, String name, String type, String applyTo, String description, String icon,
                             int tier, int maxLevel, List<String> compatibleWith, String trigger,
                             Map<Integer, Map<String, Object>> levels, Map<String, Object> cost,
                             List<String> lore, List<String> appliedLore, String recipeId,
                             Map<String, Object> evolution, int intervalSeconds,
                             Map<String, Object> combat, Map<String, Object> waypoint) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.applyTo = applyTo;
        this.description = description;
        this.icon = icon;
        this.tier = tier;
        this.maxLevel = maxLevel;
        this.compatibleWith = compatibleWith;
        this.trigger = trigger;
        this.levels = levels;
        this.cost = cost;
        this.lore = lore;
        this.appliedLore = appliedLore;
        this.recipeId = recipeId;
        this.evolution = evolution;
        this.intervalSeconds = intervalSeconds;
        this.combat = combat;
        this.waypoint = waypoint;
    }

    public static AbilityDefinition fromConfig(String id, ConfigurationSection section) {
        Map<Integer, Map<String, Object>> levels = new TreeMap<>();
        ConfigurationSection levelSection = section.getConfigurationSection("levels");
        if (levelSection != null) {
            for (String key : levelSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    ConfigurationSection lv = levelSection.getConfigurationSection(key);
                    levels.put(level, lv == null ? new LinkedHashMap<>() : new LinkedHashMap<>(lv.getValues(false)));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Map<String, Object> cost = new LinkedHashMap<>();
        ConfigurationSection costSection = section.getConfigurationSection("cost");
        if (costSection != null) {
            for (String key : costSection.getKeys(false)) {
                ConfigurationSection nested = costSection.getConfigurationSection(key);
                if (nested != null) {
                    cost.put(key, new LinkedHashMap<>(nested.getValues(false)));
                } else {
                    cost.put(key, costSection.get(key));
                }
            }
        }

        Map<String, Object> evolution = new LinkedHashMap<>();
        ConfigurationSection evoSection = section.getConfigurationSection("evolution");
        if (evoSection != null) {
            evolution.putAll(evoSection.getValues(false));
            ConfigurationSection caps = evoSection.getConfigurationSection("caps");
            if (caps != null) {
                evolution.put("caps", new LinkedHashMap<>(caps.getValues(false)));
            }
            ConfigurationSection inc = evoSection.getConfigurationSection("increments");
            if (inc != null) {
                evolution.put("increments", new LinkedHashMap<>(inc.getValues(false)));
            }
            ConfigurationSection weights = evoSection.getConfigurationSection("weights");
            if (weights != null) {
                evolution.put("weights", new LinkedHashMap<>(weights.getValues(false)));
            }
            ConfigurationSection base = evoSection.getConfigurationSection("base_stats");
            if (base != null) {
                evolution.put("base_stats", new LinkedHashMap<>(base.getValues(false)));
            }
        }

        Map<String, Object> combat = new LinkedHashMap<>();
        ConfigurationSection combatSection = section.getConfigurationSection("combat");
        if (combatSection != null) {
            combat.putAll(combatSection.getValues(false));
        }

        Map<String, Object> waypoint = new LinkedHashMap<>();
        ConfigurationSection waypointSection = section.getConfigurationSection("waypoint");
        if (waypointSection != null) {
            waypoint.putAll(waypointSection.getValues(false));
        }

        List<String> compatible = new ArrayList<>();
        for (String value : section.getStringList("compatible_with")) {
            compatible.add(value.toUpperCase(Locale.ROOT));
        }

        return new AbilityDefinition(
                id,
                section.getString("name", id),
                section.getString("type", "PASSIVE").toUpperCase(Locale.ROOT),
                section.getString("apply_to", "GEAR").toUpperCase(Locale.ROOT),
                section.getString("description", ""),
                section.getString("icon", "NETHER_STAR"),
                section.getInt("tier", 1),
                section.getInt("max_level", 1),
                compatible,
                section.getString("trigger", "PASSIVE").toUpperCase(Locale.ROOT),
                levels,
                cost,
                new ArrayList<>(section.getStringList("lore")),
                new ArrayList<>(section.getStringList("applied_lore")),
                section.getString("recipe", id),
                evolution,
                section.getInt("interval_seconds", 5),
                combat,
                waypoint
        );
    }

    public Map<String, Object> level(int level) {
        Map<String, Object> values = levels.get(level);
        if (values != null) {
            return values;
        }
        Map<String, Object> fallback = levels.get(1);
        return fallback == null ? Map.of() : fallback;
    }

    public double num(int level, String key, double def) {
        Object value = level(level).get(key);
        return value instanceof Number n ? n.doubleValue() : def;
    }

    public int integer(int level, String key, int def) {
        Object value = level(level).get(key);
        return value instanceof Number n ? n.intValue() : def;
    }

    public String str(int level, String key, String def) {
        Object value = level(level).get(key);
        return value == null ? def : String.valueOf(value);
    }

    public String costType() {
        Object value = cost.get("type");
        return value == null ? "FOOD" : String.valueOf(value).toUpperCase(Locale.ROOT);
    }

    public int costAmount() {
        Object value = cost.get("amount");
        return value instanceof Number n ? n.intValue() : 0;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String applyTo() {
        return applyTo;
    }

    public String description() {
        return description;
    }

    public String icon() {
        return icon;
    }

    public int tier() {
        return tier;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public List<String> compatibleWith() {
        return compatibleWith;
    }

    public String trigger() {
        return trigger;
    }

    public Map<Integer, Map<String, Object>> levels() {
        return levels;
    }

    public Map<String, Object> cost() {
        return cost;
    }

    public List<String> lore() {
        return lore;
    }

    public List<String> appliedLore() {
        return appliedLore;
    }

    public String recipeId() {
        return recipeId;
    }

    public Map<String, Object> evolution() {
        return evolution;
    }

    public int intervalSeconds() {
        return intervalSeconds;
    }

    public Map<String, Object> combat() {
        return combat;
    }

    public Map<String, Object> waypoint() {
        return waypoint;
    }

    public boolean combatNotify() {
        Object value = combat.get("notify");
        return value instanceof Boolean bool && bool;
    }

    public String combatMessage() {
        Object value = combat.get("message");
        return value == null ? "" : String.valueOf(value);
    }

    public String waypointString(String key, String def) {
        Object value = waypoint.get(key);
        return value == null ? def : String.valueOf(value);
    }

    public int waypointInt(String key, int def) {
        Object value = waypoint.get(key);
        return value instanceof Number n ? n.intValue() : def;
    }

    @SuppressWarnings("unchecked")
    public int nestedCost(String branch, int def) {
        Object value = cost.get(branch);
        if (value instanceof Map<?, ?> map) {
            Object amount = ((Map<String, Object>) map).get("amount");
            if (amount instanceof Number n) {
                return n.intValue();
            }
        }
        return def;
    }
}

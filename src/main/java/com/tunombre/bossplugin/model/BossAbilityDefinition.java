package com.tunombre.bossplugin.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Definicion de una habilidad de jefe cargada desde boss_abilities.yml.
 */
public class BossAbilityDefinition {

    private final String id;
    private final String name;
    private final String trigger;
    private final int intervalSeconds;
    private final int chance;
    private final List<String> archetypes;
    private final List<Double> healthThresholds;
    private final boolean oncePerThreshold;
    private final List<AbilityEffect> effects;

    public BossAbilityDefinition(String id, String name, String trigger, int intervalSeconds, int chance,
                                 List<String> archetypes, List<Double> healthThresholds,
                                 boolean oncePerThreshold, List<AbilityEffect> effects) {
        this.id = id;
        this.name = name;
        this.trigger = trigger;
        this.intervalSeconds = intervalSeconds;
        this.chance = chance;
        this.archetypes = archetypes;
        this.healthThresholds = healthThresholds;
        this.oncePerThreshold = oncePerThreshold;
        this.effects = effects;
    }

    public static BossAbilityDefinition fromConfig(String id, ConfigurationSection section) {
        List<AbilityEffect> effects = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("effects")) {
            Object typeValue = raw.get("type");
            if (typeValue == null) {
                continue;
            }
            Map<String, Object> params = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!"type".equalsIgnoreCase(key)) {
                    params.put(key, entry.getValue());
                }
            }
            effects.add(new AbilityEffect(String.valueOf(typeValue), params));
        }

        List<String> archetypes = new ArrayList<>();
        for (String value : section.getStringList("archetypes")) {
            archetypes.add(value.toUpperCase(Locale.ROOT));
        }

        return new BossAbilityDefinition(
                id,
                section.getString("name", id),
                section.getString("trigger", "TIMER").toUpperCase(Locale.ROOT),
                section.getInt("interval_seconds", 10),
                section.getInt("chance", 100),
                archetypes,
                new ArrayList<>(section.getDoubleList("health_thresholds")),
                section.getBoolean("once_per_threshold", true),
                effects
        );
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String trigger() {
        return trigger;
    }

    public int intervalSeconds() {
        return intervalSeconds;
    }

    public int chance() {
        return chance;
    }

    public List<String> archetypes() {
        return archetypes;
    }

    public List<Double> healthThresholds() {
        return healthThresholds;
    }

    public boolean oncePerThreshold() {
        return oncePerThreshold;
    }

    public List<AbilityEffect> effects() {
        return effects;
    }
}

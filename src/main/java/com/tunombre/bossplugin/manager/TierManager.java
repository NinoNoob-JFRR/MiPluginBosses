package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.model.TierDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carga los 10 tiers desde tiers.yml.
 */
public class TierManager {

    private final ConfigManager config;
    private final Map<Integer, TierDefinition> tiers = new LinkedHashMap<>();

    public TierManager(ConfigManager config) {
        this.config = config;
        reload();
    }

    public void reload() {
        tiers.clear();
        FileConfiguration file = config.get("tiers.yml");
        ConfigurationSection section = file.getConfigurationSection("tiers");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                ConfigurationSection tier = section.getConfigurationSection(key);
                if (tier == null) {
                    continue;
                }
                String name = tier.getString("name", "Tier " + id);
                String display = tier.getString("display", "&f" + name);
                String color = tier.getString("color", "&f");
                double chance = tier.getDouble("essence-drop-chance", 0.1);
                tiers.put(id, new TierDefinition(id, name, display, color, chance));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public TierDefinition get(int tier) {
        TierDefinition definition = tiers.get(tier);
        if (definition != null) {
            return definition;
        }
        return new TierDefinition(tier, "Tier " + tier, "&fTier " + tier, "&f", 0.1);
    }

    public String color(int tier) {
        return get(tier).color();
    }

    public String display(int tier) {
        return get(tier).display();
    }

    public String name(int tier) {
        return get(tier).name();
    }

    public double essenceChance(int tier) {
        return get(tier).essenceDropChance();
    }

    public int maxTier() {
        return tiers.keySet().stream().mapToInt(Integer::intValue).max().orElse(10);
    }
}

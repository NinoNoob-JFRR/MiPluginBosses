package com.tunombre.bossplugin.config;

import com.tunombre.bossplugin.model.AbilityDefinition;
import com.tunombre.bossplugin.model.BossAbilityDefinition;
import com.tunombre.bossplugin.model.BossDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validacion semantica de los YAML. Devuelve una lista de errores legibles.
 */
public class ConfigValidator {

    private final ConfigManager config;
    private final List<String> errors = new ArrayList<>();

    public ConfigValidator(ConfigManager config) {
        this.config = config;
    }

    public List<String> validate() {
        errors.clear();
        validateBosses();
        validateBossAbilities();
        validatePlayerAbilities();
        validateRecipes();
        validateMobs();
        validateLoot();
        validateEggs();
        return new ArrayList<>(errors);
    }

    private void validateBosses() {
        ConfigurationSection bosses = config.get("bosses.yml").getConfigurationSection("bosses");
        if (bosses == null) {
            errors.add("bosses.yml: falta la seccion bosses");
            return;
        }
        int total = 0;
        for (String id : bosses.getKeys(false)) {
            total++;
            ConfigurationSection section = bosses.getConfigurationSection(id);
            if (section == null) {
                errors.add("bosses.yml: " + id + " no es una seccion");
                continue;
            }
            int tier = section.getInt("tier", 0);
            if (tier < 1 || tier > 10) {
                errors.add("bosses.yml: " + id + " tiene tier invalido: " + tier);
            }
            if (section.getDouble("stats.health", 0) <= 0) {
                errors.add("bosses.yml: " + id + " tiene vida invalida");
            }
            if (section.getDouble("stats.damage", 0) <= 0) {
                errors.add("bosses.yml: " + id + " tiene dano invalido");
            }
            ConfigurationSection equipment = section.getConfigurationSection("equipment");
            if (equipment != null) {
                for (String slot : equipment.getKeys(false)) {
                    String material = equipment.getString(slot, "");
                    if (Material.matchMaterial(material) == null) {
                        errors.add("bosses.yml: " + id + " usa material desconocido " + material);
                    }
                }
            }
            for (String ability : section.getStringList("abilities")) {
                if (config.get("boss_abilities.yml").getConfigurationSection("boss_abilities." + ability) == null) {
                    errors.add("bosses.yml: " + id + " referencia habilidad inexistente " + ability);
                }
            }
        }
        if (total != 20) {
            errors.add("bosses.yml: se esperaban 20 jefes, hay " + total);
        }
        for (int tier = 1; tier <= 10; tier++) {
            int count = 0;
            for (String id : bosses.getKeys(false)) {
                if (bosses.getInt(id + ".tier", 0) == tier) {
                    count++;
                }
            }
            if (count != 2) {
                errors.add("bosses.yml: el tier " + tier + " tiene " + count + " jefes (se esperan 2)");
            }
        }
    }

    private void validateBossAbilities() {
        ConfigurationSection abilities = config.get("boss_abilities.yml").getConfigurationSection("boss_abilities");
        if (abilities == null) {
            errors.add("boss_abilities.yml: falta la seccion boss_abilities");
            return;
        }
        if (abilities.getKeys(false).size() != 28) {
            errors.add("boss_abilities.yml: se esperaban 28 habilidades, hay " + abilities.getKeys(false).size());
        }
        for (String id : abilities.getKeys(false)) {
            ConfigurationSection section = abilities.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            String trigger = section.getString("trigger", "").toUpperCase(Locale.ROOT);
            if (!List.of("TIMER", "ON_SPAWN", "ON_DAMAGE_TAKEN", "ON_DAMAGE_DEALT",
                    "ON_HEALTH", "ON_DEATH", "ON_PLAYER_DISTANCE").contains(trigger)) {
                errors.add("boss_abilities.yml: " + id + " tiene trigger invalido " + trigger);
            }
            if (trigger.equals("TIMER") && section.getInt("interval_seconds", 0) <= 0) {
                errors.add("boss_abilities.yml: " + id + " no tiene interval_seconds");
            }
            if (section.getMapList("effects").isEmpty()) {
                errors.add("boss_abilities.yml: " + id + " no tiene efectos");
            }
        }
    }

    private void validatePlayerAbilities() {
        ConfigurationSection abilities = config.get("essence_abilities.yml").getConfigurationSection("abilities");
        if (abilities == null) {
            errors.add("essence_abilities.yml: falta la seccion abilities");
            return;
        }
        if (abilities.getKeys(false).size() != 27) {
            errors.add("essence_abilities.yml: se esperaban 27 habilidades, hay " + abilities.getKeys(false).size());
        }
        for (String id : abilities.getKeys(false)) {
            ConfigurationSection section = abilities.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            AbilityDefinition definition = AbilityDefinition.fromConfig(id, section);
            if (definition.maxLevel() <= 0) {
                errors.add("essence_abilities.yml: " + id + " tiene max_level invalido");
            }
            if (!List.of("PASSIVE", "ACTIVE").contains(definition.type())) {
                errors.add("essence_abilities.yml: " + id + " tiene type invalido " + definition.type());
            }
            if (definition.levels().isEmpty()) {
                errors.add("essence_abilities.yml: " + id + " no tiene niveles");
            }
        }
    }

    private void validateRecipes() {
        ConfigurationSection recipes = config.get("recipes.yml").getConfigurationSection("recipes.abilities");
        if (recipes == null) {
            errors.add("recipes.yml: falta recipes.abilities");
            return;
        }
        ConfigurationSection abilities = config.get("essence_abilities.yml").getConfigurationSection("abilities");
        if (abilities != null) {
            for (String id : abilities.getKeys(false)) {
                if (!recipes.contains(id)) {
                    errors.add("recipes.yml: falta la receta de " + id);
                }
            }
        }
        for (String id : recipes.getKeys(false)) {
            ConfigurationSection recipe = recipes.getConfigurationSection(id);
            if (recipe == null) {
                continue;
            }
            for (String key : List.of("A", "B")) {
                if (Material.matchMaterial(recipe.getString(key, "")) == null) {
                    errors.add("recipes.yml: " + id + " usa material desconocido en " + key);
                }
            }
        }
        ConfigurationSection compass = config.get("recipes.yml").getConfigurationSection("recipes.tracking_compass.ingredients");
        if (compass != null) {
            for (String key : compass.getKeys(false)) {
                if (Material.matchMaterial(compass.getString(key, "")) == null) {
                    errors.add("recipes.yml: brujula usa material desconocido en " + key);
                }
            }
        }
    }

    private void validateMobs() {
        ConfigurationSection distribution = config.get("mobs.yml").getConfigurationSection("mobs.distribution");
        if (distribution == null) {
            errors.add("mobs.yml: falta la distribucion");
            return;
        }
        int total = 0;
        for (String key : distribution.getKeys(false)) {
            total += distribution.getInt(key);
        }
        if (total != 100) {
            errors.add("mobs.yml: la distribucion suma " + total + " (deberia ser 100)");
        }
        if (config.get("mobs.yml").getConfigurationSection("mobs.stats") == null) {
            errors.add("mobs.yml: falta la seccion stats");
        }
    }

    private void validateLoot() {
        ConfigurationSection table = config.get("loot.yml").getConfigurationSection("loot.tier-table");
        if (table == null) {
            errors.add("loot.yml: falta tier-table");
        } else {
            for (int tier = 1; tier <= 10; tier++) {
                if (table.getDoubleList(String.valueOf(tier)).size() != 10) {
                    errors.add("loot.yml: la fila " + tier + " de tier-table no tiene 10 valores");
                }
            }
        }
        ConfigurationSection categories = config.get("loot.yml").getConfigurationSection("loot.categories");
        if (categories != null) {
            int total = 0;
            for (String key : categories.getKeys(false)) {
                total += categories.getInt(key);
            }
            if (total != 100) {
                errors.add("loot.yml: las categorias suman " + total + " (deberian ser 100)");
            }
        }
        ConfigurationSection materials = config.get("loot.yml").getConfigurationSection("loot.materials");
        if (materials != null) {
            for (String key : materials.getKeys(false)) {
                ConfigurationSection row = materials.getConfigurationSection(key);
                if (row == null) {
                    continue;
                }
                int total = 0;
                for (String material : row.getKeys(false)) {
                    total += row.getInt(material);
                }
                if (total != 100) {
                    errors.add("loot.yml: los materiales del tier " + key + " suman " + total);
                }
            }
        }
    }

    private void validateEggs() {
        if (config.get("eggs.yml").getInt("eggs.max-active", 0) <= 0) {
            errors.add("eggs.yml: max-active debe ser mayor que 0");
        }
        if (Material.matchMaterial(config.get("eggs.yml").getString("eggs.block", "DRAGON_EGG")) == null) {
            errors.add("eggs.yml: bloque de huevo invalido");
        }
        if (config.get("eggs.yml").getStringList("eggs.worlds").isEmpty()) {
            errors.add("eggs.yml: no hay mundos configurados");
        }
    }
}

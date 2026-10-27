package com.tunombre.bossplugin.config;

import com.tunombre.bossplugin.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Migracion automatica: anade claves nuevas sin borrar la configuracion
 * existente y crea una copia de seguridad antes de tocar cada archivo.
 * Las migraciones con cambios de valores se controlan con un marcador de
 * version para no repetirse en cada arranque.
 */
public class ConfigMigrator {

    /** Version de configuracion que aplica este plugin. */
    private static final int CURRENT_VERSION = 3;
    private static final String VERSION_FILE = ".migration-version";

    private final Main plugin;
    private final ConfigManager config;

    public ConfigMigrator(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void migrateAll() {
        if (!config.config().getBoolean("migration.enabled", true)
                && new File(plugin.getDataFolder(), "config.yml").exists()) {
            return;
        }
        boolean backup = true;
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists()) {
            backup = YamlConfiguration.loadConfiguration(configFile)
                    .getBoolean("migration.backup", true);
        }

        for (String name : ConfigManager.FILES) {
            InputStream resource = plugin.getResource(name);
            if (resource == null) {
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                File file = new File(plugin.getDataFolder(), name);
                if (!file.exists()) {
                    plugin.saveResource(name, false);
                    continue;
                }
                YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
                if (merge(current, defaults)) {
                    if (backup) {
                        backup(file, name);
                    }
                    current.save(file);
                    plugin.getLogger().info("Configuracion migrada: " + name);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo migrar " + name + ": " + e.getMessage());
            }
        }

        migrateV2(backup);
        migrateV3(backup);
    }

    /**
     * v2: los jefes dejan de soltar loot aleatorio de mob normal, el menu de
     * admin usa submenus funcionales y backstab acepta hachas.
     */
    private void migrateV2(boolean backup) {
        if (readVersion() >= 2) {
            return;
        }
        migrateDropsV2(backup);
        migrateMenusV2(backup);
        migrateEssenceV2(backup);
        writeVersion(2);
        plugin.getLogger().info("Migracion de configuracion v2 aplicada.");
    }

    private void migrateDropsV2(boolean backup) {
        File file = new File(plugin.getDataFolder(), "drops.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String path = "drops.boss.custom-loot.enabled";
        if (yml.getBoolean(path, false)) {
            yml.set(path, false);
            saveMigrated(yml, file, "drops.yml", backup);
        }
    }

    private void migrateMenusV2(boolean backup) {
        File file = new File(plugin.getDataFolder(), "menus.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        changed |= replaceAction(yml, "menus.admin.items.eggs", "OPEN_MENU");
        if ("OPEN_MENU".equalsIgnoreCase(yml.getString("menus.admin.items.eggs.action", ""))) {
            yml.set("menus.admin.items.eggs.menu", "admin_egg");
            changed = true;
        }
        changed |= replaceAction(yml, "menus.admin.items.compass", "ADMIN_PLAYERS:COMPASS");
        changed |= replaceAction(yml, "menus.admin.items.give_essence", "ADMIN_PLAYERS:GIVE");
        changed |= replaceAction(yml, "menus.admin.items.unlock", "ADMIN_PLAYERS:UNLOCK");
        changed |= replaceAction(yml, "menus.admin.items.reset", "ADMIN_PLAYERS:RESET");
        changed |= replaceAction(yml, "menus.admin.items.spawn_boss", "ADMIN_BOSS_LIST");
        if (changed) {
            saveMigrated(yml, file, "menus.yml", backup);
        }
    }

    /**
     * Solo sustituye la accion si sigue siendo la antigua SUGGEST_COMMAND,
     * para no pisar personalizaciones del servidor.
     */
    private boolean replaceAction(YamlConfiguration yml, String itemPath, String newAction) {
        if (!"SUGGEST_COMMAND".equalsIgnoreCase(yml.getString(itemPath + ".action", ""))) {
            return false;
        }
        yml.set(itemPath + ".action", newAction);
        yml.set(itemPath + ".command", null);
        return true;
    }

    private void migrateEssenceV2(boolean backup) {
        File file = new File(plugin.getDataFolder(), "essence_abilities.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String path = "abilities.backstab.compatible_with";
        List<String> current = yml.getStringList(path);
        if (current.size() == 1 && "SWORD".equalsIgnoreCase(current.get(0))) {
            yml.set(path, List.of("SWORD", "AXE"));
            saveMigrated(yml, file, "essence_abilities.yml", backup);
        }
    }

    /**
     * v3: Speed pasa a habilidad activa (libro), Evolution sube sus limites
     * y el lore aplicado muestra el nombre de la habilidad en rojo epico.
     */
    private void migrateV3(boolean backup) {
        if (readVersion() >= 3) {
            return;
        }
        migrateEssenceV3(backup);
        migrateItemsV3(backup);
        writeVersion(3);
        plugin.getLogger().info("Migracion de configuracion v3 aplicada.");
    }

    private void migrateEssenceV3(boolean backup) {
        File file = new File(plugin.getDataFolder(), "essence_abilities.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        // Speed: de pasiva en botas a activa en libro.
        String speed = "abilities.speed";
        if (yml.contains(speed)) {
            changed |= setIfDifferent(yml, speed + ".type", "ACTIVE");
            changed |= setIfDifferent(yml, speed + ".apply_to", "BOOK");
            changed |= setIfDifferent(yml, speed + ".compatible_with", List.of("BOOK"));
            changed |= setIfDifferent(yml, speed + ".trigger", "ACTIVE");
            changed |= setIfDifferent(yml, speed + ".description", "Aumenta tu velocidad al usarla.");
            changed |= setIfDifferent(yml, speed + ".cost.amount", 2);
            if (yml.contains(speed + ".interval_seconds")) {
                yml.set(speed + ".interval_seconds", null);
                changed = true;
            }
            Map<String, Object> levels = new LinkedHashMap<>();
            int[] durations = {20, 25, 30, 35, 45};
            for (int level = 1; level <= 5; level++) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("effect_level", level);
                values.put("duration_seconds", durations[level - 1]);
                levels.put(String.valueOf(level), values);
            }
            changed |= setIfDifferent(yml, speed + ".levels", levels);
            changed |= setIfDifferent(yml, speed + ".lore", List.of(
                    "&7Activa: aumenta tu velocidad.",
                    "&7Nivel: &a%effect_level%",
                    "&7Duracion: &a%duration_seconds%s"));
        }

        // Evolution: limites mas altos y coste de comida plano por activacion.
        String evolution = "abilities.evolution";
        if (yml.contains(evolution)) {
            Map<String, Object> levels = new LinkedHashMap<>();
            levels.put("1", evolutionLevel(15, 50, 1, 3));
            levels.put("2", evolutionLevel(25, 75, 2, 5));
            levels.put("3", evolutionLevel(40, 100, 3, 8));
            changed |= setIfDifferent(yml, evolution + ".levels", levels);

            Map<String, Object> caps = new LinkedHashMap<>();
            caps.put("max_health", 60);
            caps.put("armor", 30);
            caps.put("toughness", 20);
            caps.put("attack_damage", 20);
            caps.put("attack_speed", 1.2);
            caps.put("movement_speed", 0.06);
            caps.put("knockback_resistance", 0.8);
            caps.put("luck", 10);
            changed |= setIfDifferent(yml, evolution + ".evolution.caps", caps);

            Map<String, Object> increments = new LinkedHashMap<>();
            increments.put("max_health", 1);
            increments.put("armor", 1);
            increments.put("toughness", 1);
            increments.put("attack_damage", 1);
            increments.put("attack_speed", 0.1);
            increments.put("movement_speed", 0.005);
            increments.put("knockback_resistance", 0.05);
            increments.put("luck", 1);
            changed |= setIfDifferent(yml, evolution + ".evolution.increments", increments);
            changed |= setIfDifferent(yml, evolution + ".evolution.point-multiplier", 1.0);
            changed |= setIfDifferent(yml, evolution + ".evolution.food-cost", 1);
        }

        // Lore aplicado epico en rojo para todas las habilidades.
        ConfigurationSection abilities = yml.getConfigurationSection("abilities");
        if (abilities != null) {
            String epic = "&4&l✦ &c&l%ability_name% &8&lNv.%level%";
            for (String id : abilities.getKeys(false)) {
                changed |= setIfDifferent(yml, "abilities." + id + ".applied_lore", List.of(epic));
            }
        }

        if (changed) {
            saveMigrated(yml, file, "essence_abilities.yml", backup);
        }
    }

    private Map<String, Object> evolutionLevel(int normalChance, int bossChance,
                                               int normalPoints, int bossPoints) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("normal_mob_chance", normalChance);
        values.put("boss_chance", bossChance);
        values.put("normal_points", normalPoints);
        values.put("boss_points", bossPoints);
        return values;
    }

    private void migrateItemsV3(boolean backup) {
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String path = "items.ability_book.usage";
        String current = yml.getString(path, "");
        if (current.toLowerCase(java.util.Locale.ROOT).contains("en el aire")
                && !current.toLowerCase(java.util.Locale.ROOT).contains("bloque")) {
            yml.set(path, "&8Haz clic derecho en el aire o en un bloque para usar.");
            saveMigrated(yml, file, "items.yml", backup);
        }
    }

    private boolean setIfDifferent(YamlConfiguration yml, String path, Object value) {
        if (java.util.Objects.equals(yml.get(path), value)) {
            return false;
        }
        yml.set(path, value);
        return true;
    }

    private void saveMigrated(YamlConfiguration yml, File file, String name, boolean backup) {
        try {
            if (backup) {
                backup(file, name);
            }
            yml.save(file);
            plugin.getLogger().info("Configuracion actualizada (v" + CURRENT_VERSION + "): " + name);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo actualizar " + name + ": " + e.getMessage());
        }
    }

    private int readVersion() {
        File file = new File(plugin.getDataFolder(), VERSION_FILE);
        if (!file.exists()) {
            return 0;
        }
        try {
            return Integer.parseInt(Files.readString(file.toPath(), StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void writeVersion(int version) {
        try {
            Files.writeString(new File(plugin.getDataFolder(), VERSION_FILE).toPath(),
                    String.valueOf(version), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo guardar la version de configuracion: " + e.getMessage());
        }
    }

    private boolean merge(ConfigurationSection target, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
                continue;
            }
            ConfigurationSection targetChild = target.getConfigurationSection(key);
            ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
            if (targetChild != null && defaultChild != null) {
                changed |= merge(targetChild, defaultChild);
            }
        }
        return changed;
    }

    private void backup(File file, String name) {
        try {
            File folder = new File(plugin.getDataFolder(), "backups");
            if (!folder.exists() && !folder.mkdirs()) {
                return;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String safe = name.replace('/', '_');
            File target = new File(folder, safe + "." + stamp + ".bak");
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo crear la copia de seguridad de " + name + ": " + e.getMessage());
        }
    }
}

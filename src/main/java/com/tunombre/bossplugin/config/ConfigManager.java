package com.tunombre.bossplugin.config;

import com.tunombre.bossplugin.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carga y guarda todos los archivos YAML del plugin.
 */
public class ConfigManager {

    public static final String[] FILES = {
            "config.yml",
            "messages.yml",
            "tiers.yml",
            "bosses.yml",
            "boss_abilities.yml",
            "essence_abilities.yml",
            "drops.yml",
            "recipes.yml",
            "eggs.yml",
            "progression.yml",
            "items.yml",
            "menus.yml",
            "mobs.yml",
            "loot.yml"
    };

    public static final String[] DATA_FILES = {
            "data/progress.yml",
            "data/stats.yml",
            "data/waypoints.yml",
            "data/magic_bags.yml",
            "data/eggs.yml"
    };

    private final Main plugin;
    private final Map<String, FileConfiguration> files = new LinkedHashMap<>();

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        files.clear();
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta de datos.");
        }
        for (String name : FILES) {
            files.put(name, load(name));
        }
        for (String name : DATA_FILES) {
            files.put(name, load(name));
        }
    }

    private FileConfiguration load(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            try {
                plugin.saveResource(name, false);
            } catch (IllegalArgumentException ignored) {
                // El recurso no existe en el jar: se crea vacio.
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration get(String name) {
        FileConfiguration cfg = files.get(name);
        if (cfg == null) {
            cfg = load(name);
            files.put(name, cfg);
        }
        return cfg;
    }

    public FileConfiguration config() {
        return get("config.yml");
    }

    public FileConfiguration messages() {
        return get("messages.yml");
    }

    public void save(String name) {
        FileConfiguration cfg = files.get(name);
        if (cfg == null) {
            return;
        }
        try {
            cfg.save(new File(plugin.getDataFolder(), name));
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar " + name + ": " + e.getMessage());
        }
    }

    public void saveAllData() {
        for (String name : DATA_FILES) {
            save(name);
        }
    }

    public void reloadAll() {
        loadAll();
    }

    public int getInt(String file, String path, int def) {
        return get(file).getInt(path, def);
    }

    public double getDouble(String file, String path, double def) {
        return get(file).getDouble(path, def);
    }

    public String getString(String file, String path, String def) {
        return get(file).getString(path, def);
    }
}

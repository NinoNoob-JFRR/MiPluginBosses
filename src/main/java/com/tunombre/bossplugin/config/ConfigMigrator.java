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

/**
 * Migracion automatica: anade claves nuevas sin borrar la configuracion
 * existente y crea una copia de seguridad antes de tocar cada archivo.
 */
public class ConfigMigrator {

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

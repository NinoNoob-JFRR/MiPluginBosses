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
import java.util.List;

/**
 * Migracion automatica: anade claves nuevas sin borrar la configuracion
 * existente y crea una copia de seguridad antes de tocar cada archivo.
 * Las migraciones con cambios de valores se controlan con un marcador de
 * version para no repetirse en cada arranque.
 */
public class ConfigMigrator {

    /** Version de configuracion que aplica este plugin. */
    private static final int CURRENT_VERSION = 2;
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
        writeVersion(CURRENT_VERSION);
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

    private void saveMigrated(YamlConfiguration yml, File file, String name, boolean backup) {
        try {
            if (backup) {
                backup(file, name);
            }
            yml.save(file);
            plugin.getLogger().info("Configuracion actualizada a v2: " + name);
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

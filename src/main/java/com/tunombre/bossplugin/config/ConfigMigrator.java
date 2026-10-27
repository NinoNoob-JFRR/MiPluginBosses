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
    private static final int CURRENT_VERSION = 5;
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
        migrateV4(backup);
        migrateV5(backup);
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

    /**
     * v4: Evolution da 1 punto por kill, con probabilidad creciente segun el
     * tier del mob, y sube sus limites/incrementos para que se note mas.
     */
    private void migrateV4(boolean backup) {
        if (readVersion() >= 4) {
            return;
        }
        migrateEvolutionV4(backup);
        writeVersion(4);
        plugin.getLogger().info("Migracion de configuracion v4 aplicada.");
    }

    private void migrateEvolutionV4(boolean backup) {
        File file = new File(plugin.getDataFolder(), "essence_abilities.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String evolution = "abilities.evolution";
        if (!yml.contains(evolution)) {
            return;
        }
        boolean changed = false;

        Map<String, Object> levels = new LinkedHashMap<>();
        levels.put("1", evolutionLevel(10, 100, 1, 1));
        levels.put("2", evolutionLevel(20, 100, 1, 1));
        levels.put("3", evolutionLevel(30, 100, 1, 1));
        changed |= setIfDifferent(yml, evolution + ".levels", levels);
        changed |= setIfDifferent(yml, evolution + ".evolution.chance-per-tier", 6);
        changed |= setIfDifferent(yml, evolution + ".evolution.point-multiplier", 1.0);
        changed |= setIfDifferent(yml, evolution + ".evolution.food-cost", 1);

        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("max_health", 100);
        caps.put("armor", 30);
        caps.put("toughness", 20);
        caps.put("attack_damage", 40);
        caps.put("attack_speed", 2.0);
        caps.put("movement_speed", 0.08);
        caps.put("knockback_resistance", 1.0);
        caps.put("luck", 20);
        changed |= setIfDifferent(yml, evolution + ".evolution.caps", caps);

        Map<String, Object> increments = new LinkedHashMap<>();
        increments.put("max_health", 2);
        increments.put("armor", 2);
        increments.put("toughness", 2);
        increments.put("attack_damage", 2);
        increments.put("attack_speed", 0.1);
        increments.put("movement_speed", 0.005);
        increments.put("knockback_resistance", 0.1);
        increments.put("luck", 2);
        changed |= setIfDifferent(yml, evolution + ".evolution.increments", increments);

        changed |= setIfDifferent(yml, evolution + ".lore", List.of(
                "&7Gana &e1 punto&7 por mob o jefe.",
                "&7La probabilidad sube con el tier del mob.",
                "&7Solo funciona mientras esta equipada.",
                "&7Solo cuenta la instancia de mayor nivel."));

        if (changed) {
            saveMigrated(yml, file, "essence_abilities.yml", backup);
        }
    }

    /**
     * v5: Evolution deja de usar niveles de habilidad. La probabilidad
     * depende solo del tier del mob (5% por tier, maximo 50%) y Backstab
     * pasa a llamarse Poder Antiguo y funciona con cualquier golpe.
     */
    private void migrateV5(boolean backup) {
        if (readVersion() >= 5) {
            return;
        }
        migrateEvolutionV5(backup);
        migrateBackstabV5(backup);
        migrateMenusV5(backup);
        writeVersion(5);
        plugin.getLogger().info("Migracion de configuracion v5 aplicada.");
    }

    private void migrateEvolutionV5(boolean backup) {
        File file = new File(plugin.getDataFolder(), "essence_abilities.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String evolution = "abilities.evolution";
        if (!yml.contains(evolution)) {
            return;
        }
        boolean changed = false;
        changed |= setIfDifferent(yml, evolution + ".max_level", 1);

        Map<String, Object> level = new LinkedHashMap<>();
        level.put("normal_points", 1);
        level.put("boss_points", 1);
        Map<String, Object> levels = new LinkedHashMap<>();
        levels.put("1", level);
        changed |= setIfDifferent(yml, evolution + ".levels", levels);

        changed |= setIfDifferent(yml, evolution + ".evolution.chance-per-tier", 5);
        changed |= setIfDifferent(yml, evolution + ".evolution.max-chance", 50);
        changed |= setIfDifferent(yml, evolution + ".evolution.point-multiplier", 1.0);
        changed |= setIfDifferent(yml, evolution + ".lore", List.of(
                "&7Gana &e1 punto&7 por mob o jefe.",
                "&7Probabilidad por tier: &e5%&7 x tier.",
                "&7Tier 1 = &e5%&7, tier 10 = &e50%&7.",
                "&7Los jefes siempre dan el punto.",
                "&7Solo funciona mientras esta equipada."));

        if (changed) {
            saveMigrated(yml, file, "essence_abilities.yml", backup);
        }
    }

    private void migrateBackstabV5(boolean backup) {
        File file = new File(plugin.getDataFolder(), "essence_abilities.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String backstab = "abilities.backstab";
        if (!yml.contains(backstab)) {
            return;
        }
        boolean changed = false;
        changed |= setIfDifferent(yml, backstab + ".name", "Poder Antiguo");
        changed |= setIfDifferent(yml, backstab + ".description",
                "Multiplica el dano de cualquiera de tus golpes.");
        changed |= setIfDifferent(yml, backstab + ".trigger", "ON_HIT");
        changed |= setIfDifferent(yml, backstab + ".combat.message",
                "&aPoder Antiguo: &ex%multiplier%&a.");
        changed |= setIfDifferent(yml, backstab + ".lore", List.of(
                "&7Cada golpe multiplica el dano.",
                "&7No importa por donde golpees.",
                "&7Multiplicador: &ex%multiplier%"));

        if (changed) {
            saveMigrated(yml, file, "essence_abilities.yml", backup);
        }
    }

    private void migrateMenusV5(boolean backup) {
        File file = new File(plugin.getDataFolder(), "menus.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String lorePath = "menus.abilities.item.lore";
        if (!yml.contains(lorePath)) {
            return;
        }
        List<String> lore = List.of(
                "&7%ability_description%",
                "",
                "&7Tipo: &e%type%",
                "&7Tier: &e%tier%",
                "&7Nivel maximo: &e%max_level%",
                "&7Coste: &e%cost%",
                "",
                "&eClick para ver objetos compatibles");
        if (setIfDifferent(yml, lorePath, lore)) {
            saveMigrated(yml, file, "menus.yml", backup);
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

package com.tunombre.bossplugin;

import com.tunombre.bossplugin.command.BossCommand;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.config.ConfigMigrator;
import com.tunombre.bossplugin.config.ConfigValidator;
import com.tunombre.bossplugin.config.Messages;
import com.tunombre.bossplugin.listener.AbilityListener;
import com.tunombre.bossplugin.listener.BossListener;
import com.tunombre.bossplugin.listener.EggListener;
import com.tunombre.bossplugin.listener.MenuListener;
import com.tunombre.bossplugin.listener.MobListener;
import com.tunombre.bossplugin.listener.PlayerListener;
import com.tunombre.bossplugin.manager.AbilityManager;
import com.tunombre.bossplugin.manager.BossManager;
import com.tunombre.bossplugin.manager.EggManager;
import com.tunombre.bossplugin.manager.ItemFactory;
import com.tunombre.bossplugin.manager.LootManager;
import com.tunombre.bossplugin.manager.MenuManager;
import com.tunombre.bossplugin.manager.MobTierManager;
import com.tunombre.bossplugin.manager.ProgressionManager;
import com.tunombre.bossplugin.manager.StatsManager;
import com.tunombre.bossplugin.manager.TierManager;
import com.tunombre.bossplugin.model.AbilityDefinition;
import com.tunombre.bossplugin.task.BossTask;
import com.tunombre.bossplugin.task.EggTask;
import com.tunombre.bossplugin.task.StatsTask;
import com.tunombre.bossplugin.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Punto de entrada de MiPluginBosses.
 */
public final class Main extends JavaPlugin {

    private static Main instance;

    private ConfigManager configManager;
    private Messages messages;
    private ItemFactory items;
    private TierManager tierManager;
    private ProgressionManager progressionManager;
    private StatsManager statsManager;
    private MobTierManager mobTierManager;
    private LootManager lootManager;
    private BossManager bossManager;
    private EggManager eggManager;
    private AbilityManager abilityManager;
    private MenuManager menuManager;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        Keys.init(this);

        configManager = new ConfigManager(this);
        new ConfigMigrator(this, configManager).migrateAll();
        configManager.loadAll();

        messages = new Messages(configManager);
        items = new ItemFactory(this, configManager);
        tierManager = new TierManager(configManager);
        progressionManager = new ProgressionManager(this, configManager);
        statsManager = new StatsManager(this, configManager, messages, items);
        mobTierManager = new MobTierManager(this, configManager);
        lootManager = new LootManager(this, configManager, tierManager, mobTierManager);
        bossManager = new BossManager(this, configManager, messages, items, tierManager,
                mobTierManager, lootManager, progressionManager, statsManager);
        eggManager = new EggManager(this, configManager, messages, bossManager,
                progressionManager, items, tierManager);
        abilityManager = new AbilityManager(this, configManager, messages, items);
        menuManager = new MenuManager(this, configManager, messages, progressionManager,
                statsManager, bossManager, abilityManager, tierManager);

        registerRecipes();
        registerListeners();
        registerCommand();
        startTasks();

        for (Player player : Bukkit.getOnlinePlayers()) {
            statsManager.apply(player);
        }

        getLogger().info("MiPluginBosses activado. Jefes: " + bossManager.bosses().size()
                + " | Habilidades: " + abilityManager.abilities().size()
                + " | Huevos: " + eggManager.activeCount());
    }

    @Override
    public void onDisable() {
        saveData();
        if (bossManager != null) {
            bossManager.despawnAll();
        }
        if (abilityManager != null) {
            abilityManager.saveAll();
        }
        if (eggManager != null) {
            eggManager.save();
        }
        unregisterRecipes();
        getLogger().info("MiPluginBosses desactivado.");
    }

    public void reloadPlugin() {
        saveData();
        configManager.reloadAll();
        messages.reload();
        tierManager.reload();
        mobTierManager.reload();
        lootManager.reload();
        bossManager.reload();
        eggManager.reload();
        eggManager.load();
        abilityManager.reload();
        statsManager.reload();
        statsManager.load();
        progressionManager.load();
        unregisterRecipes();
        registerRecipes();
        for (Player player : Bukkit.getOnlinePlayers()) {
            statsManager.apply(player);
        }
        getLogger().info("Recarga completa: " + bossManager.bosses().size() + " jefes, "
                + abilityManager.abilities().size() + " habilidades, "
                + eggManager.activeCount() + " huevos.");
    }

    private void saveData() {
        if (progressionManager != null) {
            progressionManager.save();
        }
        if (statsManager != null) {
            statsManager.save();
        }
        if (eggManager != null) {
            eggManager.save();
        }
        if (abilityManager != null) {
            abilityManager.saveOpenBags();
            abilityManager.saveAll();
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new BossListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MobListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EggListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AbilityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    private void registerCommand() {
        PluginCommand command = getCommand("boss");
        if (command == null) {
            getLogger().warning("No se encontro el comando /boss en plugin.yml");
            return;
        }
        BossCommand executor = new BossCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void startTasks() {
        Bukkit.getScheduler().runTaskTimer(this, new BossTask(this), 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, new EggTask(this), 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, new StatsTask(this), 20L, 20L);
    }

    private void registerRecipes() {
        ConfigurationSection abilities = configManager.get("essence_abilities.yml")
                .getConfigurationSection("abilities");
        ConfigurationSection recipes = configManager.get("recipes.yml")
                .getConfigurationSection("recipes.abilities");
        List<String> pattern = configManager.get("recipes.yml").getStringList("recipes.pattern");
        if (pattern.size() != 3) {
            pattern = List.of("BAB", "AEA", "BAB");
        }
        if (abilities != null && recipes != null) {
            for (String id : abilities.getKeys(false)) {
                ConfigurationSection abilitySection = abilities.getConfigurationSection(id);
                ConfigurationSection recipeSection = recipes.getConfigurationSection(id);
                if (abilitySection == null || recipeSection == null) {
                    continue;
                }
                AbilityDefinition definition = AbilityDefinition.fromConfig(id, abilitySection);
                Material a = Material.matchMaterial(recipeSection.getString("A", ""));
                Material b = Material.matchMaterial(recipeSection.getString("B", ""));
                if (a == null || b == null) {
                    getLogger().warning("Receta invalida para " + id);
                    continue;
                }
                NamespacedKey key = new NamespacedKey(this, "ability_" + id.toLowerCase(Locale.ROOT));
                ItemStack result = items.abilityEssence(definition, 1);
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape(pattern.get(0), pattern.get(1), pattern.get(2));
                recipe.setIngredient('A', a);
                recipe.setIngredient('B', b);
                recipe.setIngredient('E', new RecipeChoice.ExactChoice(items.legendaryEssence()));
                if (Bukkit.addRecipe(recipe)) {
                    recipeKeys.add(key);
                }
            }
        }

        ConfigurationSection compass = configManager.get("recipes.yml")
                .getConfigurationSection("recipes.tracking_compass");
        if (compass != null) {
            List<String> shape = compass.getStringList("pattern");
            ConfigurationSection ingredients = compass.getConfigurationSection("ingredients");
            if (shape.size() == 3 && ingredients != null) {
                NamespacedKey key = new NamespacedKey(this, "tracking_compass");
                ShapedRecipe recipe = new ShapedRecipe(key, items.trackingCompass());
                recipe.shape(shape.get(0), shape.get(1), shape.get(2));
                boolean valid = true;
                for (String ingredientKey : ingredients.getKeys(false)) {
                    Material material = Material.matchMaterial(ingredients.getString(ingredientKey, ""));
                    if (material == null) {
                        valid = false;
                        break;
                    }
                    recipe.setIngredient(ingredientKey.charAt(0), material);
                }
                if (valid && Bukkit.addRecipe(recipe)) {
                    recipeKeys.add(key);
                }
            }
        }
    }

    private void unregisterRecipes() {
        for (NamespacedKey key : recipeKeys) {
            Bukkit.removeRecipe(key);
        }
        recipeKeys.clear();
    }

    public List<String> validateConfigs() {
        return new ConfigValidator(configManager).validate();
    }

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public ItemFactory getItems() {
        return items;
    }

    public TierManager getTierManager() {
        return tierManager;
    }

    public ProgressionManager getProgressionManager() {
        return progressionManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public MobTierManager getMobTierManager() {
        return mobTierManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public EggManager getEggManager() {
        return eggManager;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }
}

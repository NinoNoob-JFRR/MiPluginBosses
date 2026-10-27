package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.config.Messages;
import com.tunombre.bossplugin.model.AbilityDefinition;
import com.tunombre.bossplugin.model.BossAbilityDefinition;
import com.tunombre.bossplugin.model.BossDefinition;
import com.tunombre.bossplugin.model.PlayerProgress;
import com.tunombre.bossplugin.util.Keys;
import com.tunombre.bossplugin.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Construye y maneja todos los menus definidos en menus.yml.
 */
public class MenuManager {

    private final Main plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ProgressionManager progression;
    private final StatsManager stats;
    private final BossManager bosses;
    private final AbilityManager abilities;
    private final TierManager tiers;
    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();

    public MenuManager(Main plugin, ConfigManager config, Messages messages, ProgressionManager progression,
                       StatsManager stats, BossManager bosses, AbilityManager abilities, TierManager tiers) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.progression = progression;
        this.stats = stats;
        this.bosses = bosses;
        this.abilities = abilities;
        this.tiers = tiers;
    }

    // ------------------------------------------------------------
    // Apertura de menus
    // ------------------------------------------------------------

    public void openMain(Player player) {
        openConfigured(player, "main", basePlaceholders(player));
    }

    public void openAdmin(Player player) {
        openConfigured(player, "admin", basePlaceholders(player));
    }

    public void openHelp(Player player) {
        openConfigured(player, "help", basePlaceholders(player));
    }

    public void openProgress(Player player) {
        Map<String, String> placeholders = basePlaceholders(player);
        PlayerProgress progress = progression.get(player.getUniqueId());
        placeholders.put("tier", String.valueOf(progress.maxTier()));
        placeholders.put("killed", String.valueOf(totalKilled(progress)));
        placeholders.put("total", String.valueOf(bosses.bosses().size()));
        placeholders.put("tier_progress", progress.killedCount(progress.maxTier()) + "/"
                + Math.max(1, bosses.bossesOfTier(progress.maxTier()).size()));
        placeholders.put("boss_list", bossList(progress));
        openConfigured(player, "progress", placeholders);
    }

    public void openStats(Player player) {
        Inventory inventory = buildStats(player);
        if (inventory != null) {
            player.openInventory(inventory);
        }
    }

    private Inventory buildStats(Player player) {
        Map<String, String> placeholders = basePlaceholders(player);
        int level = stats.evolutionLevel(player);
        placeholders.put("evolution_level", String.valueOf(level));
        placeholders.put("evolution_equipped", level > 0
                ? messages.raw("stats.evolution-equipped") : messages.raw("stats.evolution-not-equipped"));
        placeholders.put("points", String.valueOf(stats.totalPoints(player)));
        placeholders.put("max_points", String.valueOf(stats.maxPoints()));
        List<StatsManager.StatLine> lines = stats.snapshot(player);
        placeholders.put("maxed", String.valueOf(lines.stream().filter(StatsManager.StatLine::maxed).count()));
        placeholders.put("evolution_progress", evolutionProgress(lines));

        ConfigurationSection menu = config.get("menus.yml").getConfigurationSection("menus.stats");
        if (menu == null) {
            return null;
        }
        MenuHolder holder = new MenuHolder("stats", 1);
        Inventory inventory = create(holder, menu, 6);
        fill(inventory);

        ConfigurationSection statItems = menu.getConfigurationSection("stats");
        if (statItems != null) {
            for (String key : statItems.getKeys(false)) {
                ConfigurationSection item = statItems.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>(placeholders);
                String statKey = item.getString("stat", key);
                StatsManager.StatLine line = lines.stream()
                        .filter(l -> l.key().equals(statKey)).findFirst().orElse(null);
                if (line != null) {
                    values.put("base", Text.format(line.base()));
                    values.put("equipment", Text.format(line.equipment()));
                    values.put("evolution", Text.format(line.evolution()));
                    values.put("total", Text.format(line.total()));
                    values.put("points", String.valueOf(line.points()));
                    values.put("max_points", String.valueOf(line.maxPoints()));
                    values.put("cap", Text.format(line.cap()));
                    values.put("increment", Text.format(line.increment()));
                }
                setSlot(inventory, item.getInt("slot", 0), buildItem(item, values));
            }
        }
        for (String key : menu.getKeys(false)) {
            if (key.equals("stats")) {
                continue;
            }
            ConfigurationSection item = menu.getConfigurationSection(key);
            if (item == null || !item.contains("slot")) {
                continue;
            }
            setSlot(inventory, item.getInt("slot", 0), buildItem(item, placeholders));
        }
        return inventory;
    }

    public void openRecipes(Player player, int page) {
        List<AbilityDefinition> list = new ArrayList<>(abilities.abilities().values());
        list.sort(Comparator.comparingInt(AbilityDefinition::tier).thenComparing(AbilityDefinition::name));
        List<Entry> entries = new ArrayList<>();
        for (AbilityDefinition definition : list) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("ability_id", definition.id());
            values.put("ability_name", definition.name());
            values.put("tier", String.valueOf(definition.tier()));
            values.put("max_level", String.valueOf(definition.maxLevel()));
            values.put("recipe", recipeText(definition));
            entries.add(new Entry(definition, values, "RECIPE:" + definition.id()));
        }
        openPaginated(player, "recipes", page, entries);
    }

    public void openAbilities(Player player, int page) {
        List<AbilityDefinition> list = new ArrayList<>(abilities.abilities().values());
        list.sort(Comparator.comparingInt(AbilityDefinition::tier).thenComparing(AbilityDefinition::name));
        List<Entry> entries = new ArrayList<>();
        for (AbilityDefinition definition : list) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("ability_name", definition.name());
            values.put("ability_description", definition.description());
            values.put("type", definition.type());
            values.put("tier", String.valueOf(definition.tier()));
            values.put("max_level", String.valueOf(definition.maxLevel()));
            values.put("cost", costText(definition));
            entries.add(new Entry(definition, values, ""));
        }
        openPaginated(player, "abilities", page, entries);
    }

    public void openBosses(Player player, int page) {
        List<BossDefinition> list = new ArrayList<>(bosses.bosses());
        list.sort(Comparator.comparingInt(BossDefinition::tier).thenComparing(BossDefinition::name));
        List<Entry> entries = new ArrayList<>();
        for (BossDefinition definition : list) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("boss_name", definition.name());
            values.put("tier", String.valueOf(definition.tier()));
            values.put("tier_name", tiers.name(definition.tier()));
            values.put("tier_color", tiers.color(definition.tier()));
            values.put("archetype", definition.archetype());
            values.put("abilities", abilityList(definition));
            values.put("health", Text.format(definition.health()));
            values.put("damage", Text.format(definition.damage()));
            entries.add(new Entry(definition, values, ""));
        }
        openPaginated(player, "bosses", page, entries);
    }

    // ------------------------------------------------------------
    // Submenus de administracion
    // ------------------------------------------------------------

    public void openAdminBossList(Player player, int page) {
        List<BossDefinition> list = new ArrayList<>(bosses.bosses());
        list.sort(Comparator.comparingInt(BossDefinition::tier).thenComparing(BossDefinition::name));
        List<Entry> entries = new ArrayList<>();
        for (BossDefinition definition : list) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("boss_id", definition.id());
            values.put("boss_name", definition.name());
            values.put("tier", String.valueOf(definition.tier()));
            values.put("tier_name", tiers.name(definition.tier()));
            values.put("tier_color", tiers.color(definition.tier()));
            values.put("archetype", definition.archetype());
            values.put("health", Text.format(definition.health()));
            values.put("damage", Text.format(definition.damage()));
            entries.add(new Entry(definition, values, "BOSS_PICK:" + definition.id()));
        }
        openPaginated(player, "admin_bosses", page, entries);
    }

    public void openAdminBossLocation(Player player, String bossId) {
        BossDefinition definition = bosses.definition(bossId);
        if (definition == null) {
            messages.send(player, "boss.not-found", "id", bossId);
            return;
        }
        Map<String, String> placeholders = basePlaceholders(player);
        placeholders.put("boss_id", definition.id());
        placeholders.put("boss_name", definition.name());
        placeholders.put("tier", String.valueOf(definition.tier()));
        placeholders.put("tier_color", tiers.color(definition.tier()));
        openConfigured(player, "admin_boss_location", placeholders);
    }

    public void openAdminEgg(Player player) {
        openConfigured(player, "admin_egg", basePlaceholders(player));
    }

    public void openAdminEggTiers(Player player, int page) {
        List<Entry> entries = new ArrayList<>();
        for (int tier = 1; tier <= tiers.maxTier(); tier++) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("tier", String.valueOf(tier));
            values.put("tier_name", tiers.name(tier));
            values.put("tier_color", tiers.color(tier));
            values.put("bosses", String.valueOf(bosses.bossesOfTier(tier).size()));
            entries.add(new Entry(tier, values, "EGG_TIER_PICK:" + tier));
        }
        openPaginated(player, "admin_egg_tiers", page, entries);
    }

    public void openAdminEggBosses(Player player, int page) {
        List<BossDefinition> list = new ArrayList<>(bosses.bosses());
        list.sort(Comparator.comparingInt(BossDefinition::tier).thenComparing(BossDefinition::name));
        List<Entry> entries = new ArrayList<>();
        for (BossDefinition definition : list) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("boss_id", definition.id());
            values.put("boss_name", definition.name());
            values.put("tier", String.valueOf(definition.tier()));
            values.put("tier_name", tiers.name(definition.tier()));
            values.put("tier_color", tiers.color(definition.tier()));
            values.put("archetype", definition.archetype());
            values.put("health", Text.format(definition.health()));
            values.put("damage", Text.format(definition.damage()));
            entries.add(new Entry(definition, values, "EGG_PICK:" + definition.id()));
        }
        openPaginated(player, "admin_egg_bosses", page, entries);
    }

    public void openAdminEggLocation(Player player, int tier, String bossId) {
        Map<String, String> placeholders = basePlaceholders(player);
        BossDefinition definition = bossId == null || bossId.isBlank() ? null : bosses.definition(bossId);
        placeholders.put("egg_tier", String.valueOf(definition != null ? definition.tier() : Math.max(0, tier)));
        placeholders.put("egg_boss", definition == null ? "-" : definition.id());
        if (definition != null) {
            placeholders.put("egg_label", definition.name());
        } else if (tier > 0) {
            placeholders.put("egg_label", "Tier " + tier);
        } else {
            placeholders.put("egg_label", "Aleatorio");
        }
        openConfigured(player, "admin_egg_location", placeholders);
    }

    public void openAdminPlayers(Player player, String mode, int page) {
        List<Entry> entries = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("player", online.getName());
            values.put("tier", String.valueOf(progression.maxTier(online.getUniqueId())));
            values.put("points", String.valueOf(stats.totalPoints(online)));
            entries.add(new Entry(online, values, "ADMIN_PLAYER:" + mode + ":" + online.getName()));
        }
        if (entries.isEmpty()) {
            messages.send(player, "admin.no-players");
            return;
        }
        openPaginated(player, "admin_players", page, entries, mode);
    }

    public void openAdminAbilities(Player player, String targetName, int page) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messages.send(player, "general.player-not-found", "player", targetName);
            return;
        }
        List<AbilityDefinition> list = new ArrayList<>(abilities.abilities().values());
        list.sort(Comparator.comparingInt(AbilityDefinition::tier).thenComparing(AbilityDefinition::name));
        List<Entry> entries = new ArrayList<>();
        for (AbilityDefinition definition : list) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("target", target.getName());
            values.put("ability_id", definition.id());
            values.put("ability_name", definition.name());
            values.put("type", definition.type());
            values.put("tier", String.valueOf(definition.tier()));
            values.put("max_level", String.valueOf(definition.maxLevel()));
            values.put("cost", costText(definition));
            entries.add(new Entry(definition, values,
                    "ADMIN_GIVE:" + target.getName() + ":" + definition.id()));
        }
        openPaginated(player, "admin_abilities", page, entries, target.getName());
    }

    public void openAdminUnlockTiers(Player player, String targetName, int page) {
        if (Bukkit.getPlayerExact(targetName) == null) {
            messages.send(player, "general.player-not-found", "player", targetName);
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (int tier = 1; tier <= tiers.maxTier(); tier++) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("target", targetName);
            values.put("tier", String.valueOf(tier));
            values.put("tier_name", tiers.name(tier));
            values.put("tier_color", tiers.color(tier));
            entries.add(new Entry(tier, values,
                    "ADMIN_UNLOCK:" + targetName + ":" + tier));
        }
        openPaginated(player, "admin_unlock_tiers", page, entries, targetName);
    }

    public void openAdminReset(Player player, String targetName) {
        if (Bukkit.getPlayerExact(targetName) == null) {
            messages.send(player, "general.player-not-found", "player", targetName);
            return;
        }
        Map<String, String> placeholders = basePlaceholders(player);
        placeholders.put("target", targetName);
        openConfigured(player, "admin_reset", placeholders);
    }

    public void openAdminResetTiers(Player player, String targetName, int page) {
        if (Bukkit.getPlayerExact(targetName) == null) {
            messages.send(player, "general.player-not-found", "player", targetName);
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (int tier = 1; tier <= tiers.maxTier(); tier++) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("target", targetName);
            values.put("tier", String.valueOf(tier));
            values.put("tier_name", tiers.name(tier));
            values.put("tier_color", tiers.color(tier));
            entries.add(new Entry(tier, values,
                    "ADMIN_RESET_TIER:" + targetName + ":" + tier));
        }
        openPaginated(player, "admin_reset_tiers", page, entries, targetName);
    }

    // ------------------------------------------------------------
    // Vista previa de receta (mesa de crafteo)
    // ------------------------------------------------------------

    public void openRecipePreview(Player player, String abilityId) {
        AbilityDefinition definition = abilities.ability(abilityId);
        if (definition == null) {
            return;
        }
        ConfigurationSection menu = config.get("menus.yml")
                .getConfigurationSection("menus.recipe_preview");
        if (menu == null) {
            return;
        }
        Map<String, String> placeholders = basePlaceholders(player);
        placeholders.put("ability_id", definition.id());
        placeholders.put("ability_name", definition.name());
        placeholders.put("tier", String.valueOf(definition.tier()));
        placeholders.put("max_level", String.valueOf(definition.maxLevel()));

        MenuHolder holder = new MenuHolder("recipe_preview", 1);
        Inventory inventory = create(holder, menu, 6, placeholders);
        fill(inventory);

        List<String> pattern = config.get("recipes.yml").getStringList("recipes.pattern");
        if (pattern.size() != 3) {
            pattern = List.of("BAB", "AEA", "BAB");
        }
        ConfigurationSection ingredients = config.get("recipes.yml")
                .getConfigurationSection("recipes.abilities." + definition.recipeId());
        Material a = ingredients == null ? null : Material.matchMaterial(ingredients.getString("A", ""));
        Material b = ingredients == null ? null : Material.matchMaterial(ingredients.getString("B", ""));
        ItemStack legendary = plugin.getItems().legendaryEssence();
        List<Integer> slots = menu.getIntegerList("grid-slots");
        if (slots.size() < 9) {
            slots = List.of(10, 11, 12, 19, 20, 21, 28, 29, 30);
        }
        int index = 0;
        for (int row = 0; row < 3; row++) {
            String line = pattern.get(row);
            for (int column = 0; column < 3; column++) {
                char key = column < line.length() ? line.charAt(column) : ' ';
                ItemStack item = switch (key) {
                    case 'A' -> ingredient(a);
                    case 'B' -> ingredient(b);
                    case 'E' -> legendary.clone();
                    default -> new ItemStack(Material.AIR);
                };
                setSlot(inventory, slots.get(index++), item);
            }
        }
        setSlot(inventory, menu.getInt("result-slot", 24),
                plugin.getItems().abilityEssence(definition, 1));

        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item != null) {
                    setSlot(inventory, item.getInt("slot", 0), buildItem(item, placeholders));
                }
            }
        }
        player.openInventory(inventory);
    }

    private ItemStack ingredient(Material material) {
        if (material == null) {
            return new ItemStack(Material.AIR);
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c("&f" + prettify(material.name())));
            meta.lore(List.of(Text.c("&7Cantidad: &e4")));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ------------------------------------------------------------
    // Construccion generica
    // ------------------------------------------------------------

    private void openConfigured(Player player, String kind, Map<String, String> placeholders) {
        ConfigurationSection menu = config.get("menus.yml").getConfigurationSection("menus." + kind);
        if (menu == null) {
            return;
        }
        MenuHolder holder = new MenuHolder(kind, 1);
        Inventory inventory = create(holder, menu, 6, placeholders);
        fill(inventory);
        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) {
                    continue;
                }
                setSlot(inventory, item.getInt("slot", 0), buildItem(item, placeholders));
            }
        }
        player.openInventory(inventory);
    }

    private void openPaginated(Player player, String kind, int page, List<Entry> entries) {
        openPaginated(player, kind, page, entries, "");
    }

    private void openPaginated(Player player, String kind, int page, List<Entry> entries, String metaValue) {
        ConfigurationSection menu = config.get("menus.yml").getConfigurationSection("menus." + kind);
        if (menu == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, menu.getInt("rows", 6)));
        int perPage = Math.max(1, rows * 9 - 9);
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) perPage));
        int current = Math.max(1, Math.min(pages, page));
        MenuHolder holder = new MenuHolder(kind, current, metaValue);
        Map<String, String> titlePlaceholders = new LinkedHashMap<>(basePlaceholders(player));
        if (metaValue != null && !metaValue.isBlank()) {
            titlePlaceholders.put("target", metaValue);
        }
        Inventory inventory = create(holder, menu, 6, titlePlaceholders);
        fill(inventory);

        ConfigurationSection itemSection = menu.getConfigurationSection("item");
        int start = (current - 1) * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            Map<String, String> values = new LinkedHashMap<>(basePlaceholders(player));
            values.putAll(entry.placeholders);
            ItemStack item = buildItem(itemSection, values);
            if (entry.action != null && !entry.action.isBlank()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(Keys.MENU_ACTION,
                            PersistentDataType.STRING, entry.action);
                    item.setItemMeta(meta);
                }
            }
            setSlot(inventory, i, item);
        }

        int prev = menu.getInt("prev-slot", 45);
        int next = menu.getInt("next-slot", 53);
        int close = menu.getInt("close-slot", 49);
        setSlot(inventory, prev, navigation(Material.SPECTRAL_ARROW,
                messages.getRaw("general.prev-page"), "PAGE:PREV:" + kind + ":" + current));
        setSlot(inventory, next, navigation(Material.ARROW,
                messages.getRaw("general.next-page"), "PAGE:NEXT:" + kind + ":" + current));
        setSlot(inventory, close, navigation(Material.BARRIER,
                messages.getRaw("general.menu-close"), "CLOSE"));
        ItemStack pageItem = new ItemStack(Material.PAPER);
        ItemMeta meta = pageItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(Text.apply(messages.raw("general.page"),
                    "page", String.valueOf(current), "pages", String.valueOf(pages))));
            pageItem.setItemMeta(meta);
        }
        int pageSlot = menu.getInt("page-slot", inventory.getSize() - 6);
        setSlot(inventory, pageSlot, pageItem);
        player.openInventory(inventory);
    }

    private Inventory create(MenuHolder holder, ConfigurationSection menu, int defaultRows) {
        return create(holder, menu, defaultRows, Map.of());
    }

    private Inventory create(MenuHolder holder, ConfigurationSection menu, int defaultRows,
                             Map<String, String> placeholders) {
        int rows = Math.max(1, Math.min(6, menu.getInt("rows", defaultRows)));
        String title = Text.apply(menu.getString("title", "&8Menu"), placeholders);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.c(title));
        holder.inventory = inventory;
        return inventory;
    }

    private void fill(Inventory inventory) {
        ConfigurationSection fill = config.get("menus.yml").getConfigurationSection("menus.fill");
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        if (fill != null) {
            Material material = Material.matchMaterial(fill.getString("material", "BLACK_STAINED_GLASS_PANE"));
            if (material != null) {
                item = new ItemStack(material);
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Text.c(fill.getString("name", " ")));
                item.setItemMeta(meta);
            }
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item);
        }
    }

    private ItemStack buildItem(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = section.getString("name", prettify(material.name()));
        meta.displayName(Text.c(Text.apply(name, placeholders)));
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.lore(buildLore(lore, placeholders));
        }
        String action = action(section, placeholders);
        if (!action.isBlank()) {
            meta.getPersistentDataContainer().set(Keys.MENU_ACTION, PersistentDataType.STRING, action);
        }
        meta.setEnchantmentGlintOverride(section.getBoolean("glow", false));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(List<String> lines, Map<String, String> placeholders) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            String applied = Text.apply(line, placeholders);
            for (String part : applied.split("\\n")) {
                lore.add(Text.c(part));
            }
        }
        return lore;
    }

    private String action(ConfigurationSection section, Map<String, String> placeholders) {
        String raw = Text.apply(section.getString("action", ""), placeholders).trim();
        if (raw.isEmpty()) {
            return "";
        }
        String type = raw.contains(":")
                ? raw.substring(0, raw.indexOf(':')).toUpperCase(Locale.ROOT)
                : raw.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "OPEN_MENU" -> "OPEN_MENU:" + section.getString("menu", "main");
            case "CLOSE" -> "CLOSE";
            case "REFRESH" -> "REFRESH";
            case "RUN_COMMAND" -> "RUN:" + Text.apply(section.getString("command", ""), placeholders);
            case "SUGGEST_COMMAND" -> "SUGGEST:" + Text.apply(section.getString("command", ""), placeholders);
            // Acciones dinamicas: se conserva el payload original (nombres de
            // jugador, ids de jefe, etc.) y solo se interpreta el prefijo.
            default -> raw;
        };
    }

    private ItemStack navigation(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            meta.getPersistentDataContainer().set(Keys.MENU_ACTION, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setSlot(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    // ------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------

    public void handleClick(Player player, InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MenuHolder menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(Keys.MENU_ACTION, PersistentDataType.STRING);
        if (action == null || action.isBlank()) {
            return;
        }
        String upper = action.toUpperCase(Locale.ROOT);
        if (upper.equals("CLOSE")) {
            player.closeInventory();
            return;
        }
        if (upper.startsWith("OPEN_MENU:")) {
            String menuName = action.substring("OPEN_MENU:".length());
            switch (menuName) {
                case "main" -> openMain(player);
                case "admin" -> openAdmin(player);
                case "recipes" -> openRecipes(player, 1);
                case "abilities" -> openAbilities(player, 1);
                case "bosses" -> openBosses(player, 1);
                case "progress" -> openProgress(player);
                case "stats" -> openStats(player);
                case "help" -> openHelp(player);
                case "admin_egg" -> openAdminEgg(player);
                default -> {
                }
            }
            return;
        }
        if (upper.equals("REFRESH")) {
            openStats(player);
            return;
        }
        if (upper.startsWith("PAGE:")) {
            String[] parts = action.split(":");
            if (parts.length >= 4) {
                String direction = parts[1].toUpperCase(Locale.ROOT);
                String kind = parts[2];
                int page = Integer.parseInt(parts[3]);
                int target = direction.equals("PREV") ? page - 1 : page + 1;
                switch (kind) {
                    case "recipes" -> openRecipes(player, target);
                    case "abilities" -> openAbilities(player, target);
                    case "bosses" -> openBosses(player, target);
                    case "admin_bosses" -> openAdminBossList(player, target);
                    case "admin_egg_tiers" -> openAdminEggTiers(player, target);
                    case "admin_egg_bosses" -> openAdminEggBosses(player, target);
                    case "admin_players" -> openAdminPlayers(player, currentMode(player), target);
                    case "admin_abilities" -> openAdminAbilities(player, currentTarget(player), target);
                    case "admin_unlock_tiers" -> openAdminUnlockTiers(player, currentTarget(player), target);
                    case "admin_reset_tiers" -> openAdminResetTiers(player, currentTarget(player), target);
                    default -> {
                    }
                }
            }
            return;
        }
        if (upper.startsWith("RUN:")) {
            player.closeInventory();
            player.performCommand(action.substring(4).replaceFirst("^/", ""));
            return;
        }
        if (upper.startsWith("SUGGEST:")) {
            String command = action.substring("SUGGEST:".length());
            Component component = Text.c(Text.apply(messages.raw("general.suggest-command"), "command", command))
                    .clickEvent(ClickEvent.suggestCommand(command));
            player.sendMessage(component);
            return;
        }

        if (upper.startsWith("RECIPE:")) {
            openRecipePreview(player, action.substring("RECIPE:".length()));
            return;
        }
        if (upper.equals("ADMIN_BOSS_LIST")) {
            openAdminBossList(player, 1);
            return;
        }
        if (upper.startsWith("BOSS_PICK:")) {
            openAdminBossLocation(player, action.substring("BOSS_PICK:".length()));
            return;
        }
        if (upper.startsWith("BOSS_SPAWN:")) {
            String[] parts = action.split(":");
            if (parts.length >= 3) {
                spawnBoss(player, parts[1], parts[2]);
            }
            return;
        }
        if (upper.startsWith("BOSS_COORDS:")) {
            promptCoordinates(player, "BOSS", action.substring("BOSS_COORDS:".length()), 0, "");
            return;
        }
        if (upper.equals("ADMIN_EGG")) {
            openAdminEgg(player);
            return;
        }
        if (upper.equals("ADMIN_EGG_TIERS")) {
            openAdminEggTiers(player, 1);
            return;
        }
        if (upper.equals("ADMIN_EGG_BOSSES")) {
            openAdminEggBosses(player, 1);
            return;
        }
        if (upper.equals("EGG_ANY")) {
            openAdminEggLocation(player, 0, "");
            return;
        }
        if (upper.startsWith("EGG_TIER_PICK:")) {
            try {
                openAdminEggLocation(player, Integer.parseInt(action.substring("EGG_TIER_PICK:".length())), "");
            } catch (NumberFormatException ignored) {
            }
            return;
        }
        if (upper.startsWith("EGG_PICK:")) {
            BossDefinition definition = bosses.definition(action.substring("EGG_PICK:".length()));
            if (definition != null) {
                openAdminEggLocation(player, definition.tier(), definition.id());
            }
            return;
        }
        if (upper.startsWith("EGG_SPAWN:")) {
            String[] parts = action.split(":");
            if (parts.length >= 4) {
                try {
                    spawnEgg(player, Integer.parseInt(parts[1]), parts[2], parts[3]);
                } catch (NumberFormatException ignored) {
                }
            }
            return;
        }
        if (upper.startsWith("EGG_COORDS:")) {
            String[] parts = action.split(":");
            if (parts.length >= 3) {
                try {
                    String boss = parts[2].equals("-") ? "" : parts[2];
                    promptCoordinates(player, "EGG", boss, Integer.parseInt(parts[1]), boss);
                } catch (NumberFormatException ignored) {
                }
            }
            return;
        }
        if (upper.startsWith("ADMIN_PLAYERS:")) {
            openAdminPlayers(player, action.substring("ADMIN_PLAYERS:".length()), 1);
            return;
        }
        if (upper.startsWith("ADMIN_PLAYER:")) {
            String[] parts = action.split(":");
            if (parts.length >= 3) {
                handlePlayerAction(player, parts[1], parts[2]);
            }
            return;
        }
        if (upper.startsWith("ADMIN_GIVE:")) {
            String[] parts = action.split(":");
            if (parts.length >= 3) {
                giveEssence(player, parts[1], parts[2]);
            }
            return;
        }
        if (upper.startsWith("ADMIN_UNLOCK:")) {
            String[] parts = action.split(":");
            if (parts.length >= 3) {
                unlockTier(player, parts[1], parts[2]);
            }
            return;
        }
        if (upper.startsWith("ADMIN_RESET_ALL:")) {
            resetProgress(player, action.substring("ADMIN_RESET_ALL:".length()), 0);
            return;
        }
        if (upper.startsWith("ADMIN_RESET_TIERS:")) {
            openAdminResetTiers(player, action.substring("ADMIN_RESET_TIERS:".length()), 1);
            return;
        }
        if (upper.startsWith("ADMIN_RESET_TIER:")) {
            String[] parts = action.split(":");
            if (parts.length >= 3) {
                try {
                    resetProgress(player, parts[1], Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private String currentMode(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof MenuHolder menu && "admin_players".equals(menu.kind())) {
            String mode = menu.meta();
            return mode == null || mode.isBlank() ? "COMPASS" : mode;
        }
        return "COMPASS";
    }

    private String currentTarget(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof MenuHolder menu) {
            String meta = menu.meta();
            if (meta != null && !meta.isBlank()) {
                return meta;
            }
        }
        return player.getName();
    }

    private void spawnBoss(Player player, String bossId, String mode) {
        BossDefinition definition = bosses.definition(bossId);
        if (definition == null) {
            messages.send(player, "boss.not-found", "id", bossId);
            return;
        }
        Location location = mode.equalsIgnoreCase("random") || mode.equalsIgnoreCase("aleatorio")
                ? bosses.randomLocation(player.getLocation(), 16)
                : player.getLocation();
        BossManager.ActiveBoss boss = bosses.spawn(definition, location, player);
        if (boss == null) {
            messages.send(player, "general.config-error", "error", "No se pudo invocar el jefe");
            return;
        }
        player.closeInventory();
        messages.send(player, "admin.boss-spawned",
                "boss", definition.name(),
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()));
    }

    private void spawnEgg(Player player, int tier, String bossId, String mode) {
        String forced = bossId == null || bossId.equals("-") ? "" : bossId;
        List<com.tunombre.bossplugin.model.EggData> eggs =
                mode.equalsIgnoreCase("random") || mode.equalsIgnoreCase("aleatorio")
                        ? plugin.getEggManager().spawnNear(player, tier, 1, forced)
                        : plugin.getEggManager().spawnHere(player, tier, 1, forced);
        if (eggs.isEmpty()) {
            messages.send(player, "egg.max-reached",
                    "max", String.valueOf(plugin.getEggManager().maxActive()));
            return;
        }
        player.closeInventory();
        for (com.tunombre.bossplugin.model.EggData egg : eggs) {
            messages.send(player, "admin.egg-spawned",
                    "x", String.valueOf(egg.x()),
                    "y", String.valueOf(egg.y()),
                    "z", String.valueOf(egg.z()));
        }
    }

    private void handlePlayerAction(Player player, String mode, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messages.send(player, "general.player-not-found", "player", targetName);
            return;
        }
        switch (mode.toUpperCase(Locale.ROOT)) {
            case "COMPASS" -> {
                plugin.getEggManager().giveCompass(target, 1);
                messages.send(player, "admin.compass-given", "player", target.getName());
                messages.send(target, "egg.compass-received");
            }
            case "GIVE" -> openAdminAbilities(player, target.getName(), 1);
            case "UNLOCK" -> openAdminUnlockTiers(player, target.getName(), 1);
            case "RESET" -> openAdminReset(player, target.getName());
            default -> {
            }
        }
    }

    private void giveEssence(Player player, String targetName, String abilityId) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messages.send(player, "general.player-not-found", "player", targetName);
            return;
        }
        if (abilities.giveEssence(target, abilityId, 1)) {
            messages.send(player, "admin.essence-given", "player", target.getName());
            messages.send(target, "essence.received", "amount", "1");
        } else {
            messages.send(player, "boss.not-found", "id", abilityId);
        }
    }

    private void unlockTier(Player player, String targetName, String tierValue) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messages.send(player, "general.player-not-found", "player", targetName);
            return;
        }
        int tier;
        try {
            tier = Math.max(1, Math.min(tiers.maxTier(), Integer.parseInt(tierValue)));
        } catch (NumberFormatException e) {
            messages.send(player, "general.invalid-number", "value", tierValue);
            return;
        }
        progression.unlock(target.getUniqueId(), tier);
        progression.save();
        messages.send(player, "admin.tier-unlocked",
                "tier", String.valueOf(tier), "player", target.getName());
        messages.send(target, "progression.unlocked", "tier", String.valueOf(tier));
    }

    private void resetProgress(Player player, String targetName, int tier) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messages.send(player, "general.player-not-found", "player", targetName);
            return;
        }
        if (tier > 0) {
            progression.resetTier(target.getUniqueId(), tier);
            messages.send(player, "progression.reset-tier",
                    "tier", String.valueOf(tier), "player", target.getName());
        } else {
            progression.reset(target.getUniqueId());
            messages.send(player, "admin.progress-reset", "player", target.getName());
            messages.send(target, "progression.reset", "player", target.getName());
        }
        progression.save();
    }

    // ------------------------------------------------------------
    // Entrada de coordenadas por chat
    // ------------------------------------------------------------

    public boolean hasPendingInput(Player player) {
        return player != null && pendingInputs.containsKey(player.getUniqueId());
    }

    public void clearPendingInput(Player player) {
        if (player != null) {
            pendingInputs.remove(player.getUniqueId());
        }
    }

    private void promptCoordinates(Player player, String type, String bossId, int tier, String eggBoss) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(type, bossId, tier, eggBoss));
        player.closeInventory();
        messages.send(player, "menu.coords-prompt");
    }

    public void handleChatInput(Player player, String message) {
        PendingInput pending = pendingInputs.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if (message == null || message.isBlank()
                || message.equalsIgnoreCase("cancelar") || message.equalsIgnoreCase("cancel")) {
            pendingInputs.remove(player.getUniqueId());
            messages.send(player, "menu.cancelled");
            return;
        }
        String[] parts = message.trim().split("[ ,]+");
        if (parts.length < 3) {
            messages.send(player, "menu.coords-invalid");
            return;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            messages.send(player, "menu.coords-invalid");
            return;
        }
        pendingInputs.remove(player.getUniqueId());
        if ("BOSS".equalsIgnoreCase(pending.type)) {
            BossDefinition definition = bosses.definition(pending.bossId);
            if (definition == null) {
                messages.send(player, "boss.not-found", "id", pending.bossId);
                return;
            }
            Location location = new Location(player.getWorld(), x + 0.5, y, z + 0.5);
            BossManager.ActiveBoss boss = bosses.spawn(definition, location, player);
            if (boss == null) {
                messages.send(player, "general.config-error", "error", "No se pudo invocar el jefe");
                return;
            }
            messages.send(player, "admin.boss-spawned",
                    "boss", definition.name(),
                    "x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z));
        } else {
            List<com.tunombre.bossplugin.model.EggData> eggs = plugin.getEggManager()
                    .spawnAt(player, pending.tier, 1, pending.eggBoss, x, y, z);
            if (eggs.isEmpty()) {
                messages.send(player, "egg.max-reached",
                        "max", String.valueOf(plugin.getEggManager().maxActive()));
                return;
            }
            for (com.tunombre.bossplugin.model.EggData egg : eggs) {
                messages.send(player, "admin.egg-spawned",
                        "x", String.valueOf(egg.x()),
                        "y", String.valueOf(egg.y()),
                        "z", String.valueOf(egg.z()));
            }
        }
    }

    public void refreshStats(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof MenuHolder menu && "stats".equals(menu.kind)) {
            Inventory rebuilt = buildStats(player);
            if (rebuilt != null && rebuilt.getSize() == top.getSize()) {
                top.setContents(rebuilt.getContents());
            }
        }
    }

    // ------------------------------------------------------------
    // Placeholders
    // ------------------------------------------------------------

    private Map<String, String> basePlaceholders(Player player) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("tier", String.valueOf(progression.maxTier(player.getUniqueId())));
        values.put("points", String.valueOf(stats.totalPoints(player)));
        values.put("max_points", String.valueOf(stats.maxPoints()));
        return values;
    }

    private int totalKilled(PlayerProgress progress) {
        int total = 0;
        for (int tier = 1; tier <= 10; tier++) {
            total += progress.killedCount(tier);
        }
        return total;
    }

    private String bossList(PlayerProgress progress) {
        StringBuilder sb = new StringBuilder();
        for (int tier = 1; tier <= 10; tier++) {
            int total = Math.max(1, bosses.bossesOfTier(tier).size());
            int killed = progress.killedCount(tier);
            if (tier > progress.maxTier()) {
                sb.append("&8").append(tiers.name(tier)).append(": bloqueado");
            } else if (killed >= total) {
                sb.append("&a").append(tiers.name(tier)).append(": completado");
            } else {
                sb.append("&e").append(tiers.name(tier)).append(": &f").append(killed).append("/").append(total);
            }
            if (tier < 10) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String evolutionProgress(List<StatsManager.StatLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            StatsManager.StatLine line = lines.get(i);
            String color = line.maxed() ? "&a" : "&e";
            sb.append("&7").append(line.label()).append(": ").append(color)
                    .append("+").append(Text.format(line.evolution()))
                    .append(" &8(").append(line.points()).append("/").append(line.maxPoints()).append(")");
            if (i < lines.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String abilityList(BossDefinition definition) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < definition.abilities().size(); i++) {
            BossAbilityDefinition ability = bosses.ability(definition.abilities().get(i));
            String name = ability == null ? definition.abilities().get(i) : ability.name();
            sb.append("&8- &f").append(name);
            if (i < definition.abilities().size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String costText(AbilityDefinition definition) {
        if (definition.id().equalsIgnoreCase("waypoint")) {
            return "Guardar " + AbilityDefinition.scaleFoodCost(definition.nestedCost("save", 2))
                    + " / TP " + AbilityDefinition.scaleFoodCost(definition.nestedCost("teleport", 5)) + " comida";
        }
        return "Comida x" + definition.baseFoodCost();
    }

    private String recipeText(AbilityDefinition definition) {
        List<String> pattern = config.get("recipes.yml").getStringList("recipes.pattern");
        ConfigurationSection ingredients = config.get("recipes.yml")
                .getConfigurationSection("recipes.abilities." + definition.recipeId());
        StringBuilder sb = new StringBuilder();
        for (String row : pattern) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(row);
        }
        if (ingredients != null) {
            for (String key : ingredients.getKeys(false)) {
                sb.append('\n').append("&e").append(key).append("&7: &f")
                        .append(prettify(ingredients.getString(key, ""))).append(" &8x4");
            }
        }
        sb.append('\n').append("&6E&7: &fEsencia Legendaria");
        return sb.toString();
    }

    private String prettify(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private record Entry(Object source, Map<String, String> placeholders, String action) {
    }

    private record PendingInput(String type, String bossId, int tier, String eggBoss) {
    }

    public static class MenuHolder implements InventoryHolder {
        private final String kind;
        private final int page;
        private final String meta;
        private Inventory inventory;

        public MenuHolder(String kind, int page) {
            this(kind, page, "");
        }

        public MenuHolder(String kind, int page, String meta) {
            this.kind = kind;
            this.page = page;
            this.meta = meta == null ? "" : meta;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public String kind() {
            return kind;
        }

        public int page() {
            return page;
        }

        public String meta() {
            return meta;
        }
    }
}

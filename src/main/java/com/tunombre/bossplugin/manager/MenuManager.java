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
import org.bukkit.Material;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            values.put("ability_name", definition.name());
            values.put("tier", String.valueOf(definition.tier()));
            values.put("max_level", String.valueOf(definition.maxLevel()));
            values.put("recipe", recipeText(definition));
            entries.add(new Entry(definition, values));
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
            entries.add(new Entry(definition, values));
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
            entries.add(new Entry(definition, values));
        }
        openPaginated(player, "bosses", page, entries);
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
        Inventory inventory = create(holder, menu, 6);
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
        ConfigurationSection menu = config.get("menus.yml").getConfigurationSection("menus." + kind);
        if (menu == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, menu.getInt("rows", 6)));
        int perPage = Math.max(1, rows * 9 - 9);
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) perPage));
        int current = Math.max(1, Math.min(pages, page));
        MenuHolder holder = new MenuHolder(kind, current);
        Inventory inventory = create(holder, menu, 6);
        fill(inventory);

        ConfigurationSection itemSection = menu.getConfigurationSection("item");
        int start = (current - 1) * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            Map<String, String> values = new LinkedHashMap<>(basePlaceholders(player));
            values.putAll(entries.get(index).placeholders);
            setSlot(inventory, i, buildItem(itemSection, values));
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
        int rows = Math.max(1, Math.min(6, menu.getInt("rows", defaultRows)));
        String title = menu.getString("title", "&8Menu");
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
        String action = action(section);
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

    private String action(ConfigurationSection section) {
        String action = section.getString("action", "").toUpperCase(Locale.ROOT);
        return switch (action) {
            case "OPEN_MENU" -> "OPEN_MENU:" + section.getString("menu", "main");
            case "CLOSE" -> "CLOSE";
            case "REFRESH" -> "REFRESH";
            case "RUN_COMMAND" -> "RUN:" + section.getString("command", "");
            case "SUGGEST_COMMAND" -> "SUGGEST:" + section.getString("command", "");
            default -> "";
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
        if (action.equals("CLOSE")) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("OPEN_MENU:")) {
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
                default -> {
                }
            }
            return;
        }
        if (action.equals("REFRESH")) {
            openStats(player);
            return;
        }
        if (action.startsWith("PAGE:")) {
            String[] parts = action.split(":");
            if (parts.length >= 4) {
                String direction = parts[1];
                String kind = parts[2];
                int page = Integer.parseInt(parts[3]);
                int target = direction.equals("PREV") ? page - 1 : page + 1;
                switch (kind) {
                    case "recipes" -> openRecipes(player, target);
                    case "abilities" -> openAbilities(player, target);
                    case "bosses" -> openBosses(player, target);
                    default -> {
                    }
                }
            }
            return;
        }
        if (action.startsWith("RUN:")) {
            player.closeInventory();
            player.performCommand(action.substring(4).replaceFirst("^/", ""));
            return;
        }
        if (action.startsWith("SUGGEST:")) {
            String command = action.substring("SUGGEST:".length());
            Component component = Text.c(Text.apply(messages.raw("general.suggest-command"), "command", command))
                    .clickEvent(ClickEvent.suggestCommand(command));
            player.sendMessage(component);
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
            return "Guardar " + definition.nestedCost("save", 2)
                    + " / TP " + definition.nestedCost("teleport", 5) + " comida";
        }
        return "Comida x" + definition.costAmount();
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

    private record Entry(Object source, Map<String, String> placeholders) {
    }

    public static class MenuHolder implements InventoryHolder {
        private final String kind;
        private final int page;
        private Inventory inventory;

        public MenuHolder(String kind, int page) {
            this.kind = kind;
            this.page = page;
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
    }
}

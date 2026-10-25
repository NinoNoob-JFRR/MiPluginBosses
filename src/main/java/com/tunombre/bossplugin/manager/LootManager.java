package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.util.Keys;
import com.tunombre.bossplugin.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Genera y entrega el loot custom de los mobs.
 * El item se decide al spawnear y se guarda serializado en el PDC.
 */
public class LootManager {

    private final Main plugin;
    private final ConfigManager config;
    private final TierManager tiers;
    private final MobTierManager mobTiers;
    private final Random random = new Random();

    private boolean enabled;
    private final Map<Integer, Double> dropChance = new LinkedHashMap<>();
    private final Map<Integer, double[]> tierTable = new LinkedHashMap<>();
    private final Map<String, Integer> categories = new LinkedHashMap<>();
    private final Map<String, List<String>> pieces = new LinkedHashMap<>();
    private final Map<String, List<String>> materialApplicability = new LinkedHashMap<>();
    private final Map<String, String> fixedMaterials = new LinkedHashMap<>();
    private final Map<Integer, Map<String, Integer>> materials = new LinkedHashMap<>();
    private final Map<Integer, int[]> enchantCounts = new LinkedHashMap<>();
    private final Map<Integer, double[]> curseCounts = new LinkedHashMap<>();
    private final List<Enchantment> enchantmentPool = new ArrayList<>();
    private int maxEnchantLevel = 10;
    private List<String> displayLore = new ArrayList<>();
    private String displayName = "%tier_color%%name%";

    public LootManager(Main plugin, ConfigManager config, TierManager tiers, MobTierManager mobTiers) {
        this.plugin = plugin;
        this.config = config;
        this.tiers = tiers;
        this.mobTiers = mobTiers;
        reload();
    }

    public void reload() {
        dropChance.clear();
        tierTable.clear();
        categories.clear();
        pieces.clear();
        materialApplicability.clear();
        fixedMaterials.clear();
        materials.clear();
        enchantCounts.clear();
        curseCounts.clear();
        enchantmentPool.clear();

        enabled = config.get("loot.yml").getBoolean("loot.enabled", true);

        ConfigurationSection chances = config.get("loot.yml").getConfigurationSection("loot.drop-chance-by-mob-tier");
        if (chances != null) {
            for (String key : chances.getKeys(false)) {
                try {
                    dropChance.put(Integer.parseInt(key), chances.getDouble(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        ConfigurationSection table = config.get("loot.yml").getConfigurationSection("loot.tier-table");
        if (table != null) {
            for (String key : table.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    List<Double> values = table.getDoubleList(key);
                    double[] row = new double[10];
                    for (int i = 0; i < row.length; i++) {
                        row[i] = i < values.size() ? values.get(i) : 0;
                    }
                    tierTable.put(tier, row);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        ConfigurationSection catSection = config.get("loot.yml").getConfigurationSection("loot.categories");
        if (catSection != null) {
            for (String key : catSection.getKeys(false)) {
                categories.put(key.toLowerCase(Locale.ROOT), catSection.getInt(key));
            }
        }
        ConfigurationSection pieceSection = config.get("loot.yml").getConfigurationSection("loot.pieces");
        if (pieceSection != null) {
            for (String key : pieceSection.getKeys(false)) {
                pieces.put(key.toLowerCase(Locale.ROOT), pieceSection.getStringList(key));
            }
        }
        ConfigurationSection applicability = config.get("loot.yml").getConfigurationSection("loot.material-applicability");
        if (applicability != null) {
            for (String key : applicability.getKeys(false)) {
                materialApplicability.put(key.toUpperCase(Locale.ROOT), applicability.getStringList(key));
            }
        }
        ConfigurationSection fixed = config.get("loot.yml").getConfigurationSection("loot.fixed-material-items");
        if (fixed != null) {
            for (String key : fixed.getKeys(false)) {
                fixedMaterials.put(key.toUpperCase(Locale.ROOT), fixed.getString(key, "WOOD"));
            }
        }
        ConfigurationSection materialSection = config.get("loot.yml").getConfigurationSection("loot.materials");
        if (materialSection != null) {
            for (String key : materialSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    ConfigurationSection row = materialSection.getConfigurationSection(key);
                    if (row == null) {
                        continue;
                    }
                    Map<String, Integer> weights = new LinkedHashMap<>();
                    for (String material : row.getKeys(false)) {
                        weights.put(material.toUpperCase(Locale.ROOT), row.getInt(material));
                    }
                    materials.put(tier, weights);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        ConfigurationSection enchantSection = config.get("loot.yml").getConfigurationSection("loot.enchantments.count-by-loot-tier");
        if (enchantSection != null) {
            for (String key : enchantSection.getKeys(false)) {
                try {
                    List<Integer> values = enchantSection.getIntegerList(key);
                    int min = values.isEmpty() ? 1 : values.get(0);
                    int max = values.size() < 2 ? min : values.get(1);
                    enchantCounts.put(Integer.parseInt(key), new int[]{min, max});
                } catch (NumberFormatException ignored) {
                }
            }
        }
        maxEnchantLevel = config.get("loot.yml").getInt("loot.enchantments.max-level", 10);

        ConfigurationSection curseSection = config.get("loot.yml").getConfigurationSection("loot.curses.count-by-loot-tier");
        if (curseSection != null) {
            for (String key : curseSection.getKeys(false)) {
                try {
                    ConfigurationSection row = curseSection.getConfigurationSection(key);
                    if (row == null) {
                        continue;
                    }
                    curseCounts.put(Integer.parseInt(key), new double[]{
                            row.getDouble("none", 100),
                            row.getDouble("one", 0),
                            row.getDouble("two", 0)
                    });
                } catch (NumberFormatException ignored) {
                }
            }
        }

        displayName = config.get("loot.yml").getString("loot.display.name", "%tier_color%%name%");
        displayLore = new ArrayList<>(config.get("loot.yml").getStringList("loot.display.lore"));

        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            if (!enchantment.isCursed()) {
                enchantmentPool.add(enchantment);
            }
        }
    }

    /**
     * Se llama al spawnear un mob. Tira el loot y lo guarda en el PDC.
     */
    public void rollOnSpawn(LivingEntity entity) {
        if (!enabled || entity == null) {
            return;
        }
        int mobTier = mobTiers.tier(entity);
        if (mobTier <= 0) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(Keys.LOOT_ROLLED, PersistentDataType.BYTE)) {
            return;
        }
        pdc.set(Keys.LOOT_ROLLED, PersistentDataType.BYTE, (byte) 1);

        double chance = dropChance.getOrDefault(mobTier, 0.0);
        if (random.nextDouble() * 100.0 >= chance) {
            return;
        }
        int lootTier = rollLootTier(mobTier);
        ItemStack item = generate(lootTier);
        try {
            pdc.set(Keys.LOOT_BYTES, PersistentDataType.BYTE_ARRAY, item.serializeAsBytes());
            pdc.set(Keys.LOOT_TIER, PersistentDataType.INTEGER, lootTier);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo guardar el loot custom: " + e.getMessage());
        }
    }

    /**
     * Entrega el loot guardado al morir el mob.
     */
    public void dropOnDeath(LivingEntity entity) {
        if (!enabled || entity == null) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        byte[] bytes = pdc.get(Keys.LOOT_BYTES, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try {
            ItemStack item = ItemStack.deserializeBytes(bytes);
            entity.getWorld().dropItemNaturally(entity.getLocation(), item);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo restaurar el loot custom: " + e.getMessage());
        }
        pdc.remove(Keys.LOOT_BYTES);
    }

    /**
     * Elimina cualquier loot custom pendiente (jefes y esbirros del plugin).
     */
    public void clear(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.remove(Keys.LOOT_BYTES);
        pdc.remove(Keys.LOOT_ROLLED);
        pdc.remove(Keys.LOOT_TIER);
    }

    public int rollLootTier(int mobTier) {
        double[] row = tierTable.get(mobTier);
        if (row == null) {
            return Math.max(1, Math.min(10, mobTier));
        }
        double total = 0;
        for (double value : row) {
            total += value;
        }
        if (total <= 0) {
            return Math.max(1, Math.min(10, mobTier));
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < row.length; i++) {
            roll -= row[i];
            if (roll < 0) {
                return i + 1;
            }
        }
        return 10;
    }

    public ItemStack generate(int lootTier) {
        int tier = Math.max(1, Math.min(10, lootTier));

        String category = weighted(categories);
        if (category == null) {
            category = "sword";
        }
        List<String> categoryPieces = pieces.getOrDefault(category, List.of("SWORD"));
        String piece = categoryPieces.get(random.nextInt(categoryPieces.size())).toUpperCase(Locale.ROOT);

        Material material;
        String materialKey;
        if (fixedMaterials.containsKey(piece)) {
            materialKey = piece;
            material = Material.matchMaterial(piece);
            if (material == null) {
                material = Material.IRON_INGOT;
            }
        } else {
            Map<String, Integer> candidates = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : materials.getOrDefault(tier, Map.of()).entrySet()) {
                List<String> applicable = materialApplicability.getOrDefault(entry.getKey(), List.of());
                if (applicable.contains(piece) && entry.getValue() > 0) {
                    candidates.put(entry.getKey(), entry.getValue());
                }
            }
            materialKey = weighted(candidates);
            if (materialKey == null) {
                materialKey = "IRON";
            }
            material = materialFor(materialKey, piece);
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        applyEnchantments(meta, tier);
        applyCurses(meta, tier);

        String materialLabel = materialKey == null ? prettify(material.name()) : prettify(materialKey);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("tier", String.valueOf(tier));
        placeholders.put("tier_name", tiers.name(tier));
        placeholders.put("tier_color", tiers.color(tier));
        placeholders.put("tier_roman", Text.roman(tier));
        placeholders.put("material", materialLabel);
        placeholders.put("name", prettify(material.name()));

        meta.displayName(Text.c(Text.apply(displayName, placeholders)));
        List<Component> lore = new ArrayList<>();
        for (String line : displayLore) {
            lore.add(Text.c(Text.apply(line, placeholders)));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING, "custom_loot");
        meta.getPersistentDataContainer().set(Keys.LOOT_TIER, PersistentDataType.INTEGER, tier);
        item.setItemMeta(meta);
        return item;
    }

    private void applyEnchantments(ItemMeta meta, int tier) {
        int[] range = enchantCounts.getOrDefault(tier, new int[]{1, 1});
        int count = range[0] + (range[1] > range[0] ? random.nextInt(range[1] - range[0] + 1) : 0);
        List<Enchantment> pool = new ArrayList<>(enchantmentPool);
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            Enchantment enchantment = pool.remove(random.nextInt(pool.size()));
            int level = 1 + random.nextInt(Math.max(1, maxEnchantLevel));
            meta.addEnchant(enchantment, level, true);
        }
    }

    private void applyCurses(ItemMeta meta, int tier) {
        double[] chances = curseCounts.getOrDefault(tier, new double[]{100, 0, 0});
        double total = chances[0] + chances[1] + chances[2];
        if (total <= 0) {
            return;
        }
        double roll = random.nextDouble() * total;
        int count;
        if (roll < chances[0]) {
            count = 0;
        } else if (roll < chances[0] + chances[1]) {
            count = 1;
        } else {
            count = 2;
        }
        List<Enchantment> curses = cursePool();
        for (int i = 0; i < count && !curses.isEmpty(); i++) {
            Enchantment curse = curses.remove(random.nextInt(curses.size()));
            meta.addEnchant(curse, 1, true);
        }
    }

    private List<Enchantment> cursePool() {
        List<Enchantment> curses = new ArrayList<>();
        for (String name : config.get("loot.yml").getStringList("loot.curses.pool")) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            if (enchantment != null) {
                curses.add(enchantment);
            }
        }
        if (curses.isEmpty()) {
            Enchantment vanishing = Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("vanishing_curse"));
            Enchantment binding = Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("binding_curse"));
            if (vanishing != null) {
                curses.add(vanishing);
            }
            if (binding != null) {
                curses.add(binding);
            }
        }
        return curses;
    }

    private Material materialFor(String materialKey, String piece) {
        String prefix = switch (materialKey) {
            case "LEATHER" -> "LEATHER";
            case "CHAINMAIL" -> "CHAINMAIL";
            case "COPPER" -> "COPPER";
            case "GOLD" -> "GOLDEN";
            case "IRON" -> "IRON";
            case "DIAMOND" -> "DIAMOND";
            default -> "IRON";
        };
        Material material = safeMaterial(prefix + "_" + piece);
        if (material != null) {
            return material;
        }
        // Compatibilidad: si el material no existe en esta version, se usa hierro.
        Material fallback = safeMaterial("IRON_" + piece);
        if (fallback != null) {
            return fallback;
        }
        return Material.IRON_INGOT;
    }

    private Material safeMaterial(String name) {
        return Material.matchMaterial(name);
    }

    private <T> T weighted(Map<T, Integer> weights) {
        int total = 0;
        for (int value : weights.values()) {
            if (value > 0) {
                total += value;
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
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
}

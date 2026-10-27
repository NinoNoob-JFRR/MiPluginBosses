package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.config.Messages;
import com.tunombre.bossplugin.model.AbilityDefinition;
import com.tunombre.bossplugin.model.PlayerStats;
import com.tunombre.bossplugin.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Estadisticas y Evolution.
 */
public class StatsManager {

    private static final String[] STATS = {
            "max_health", "armor", "toughness", "attack_damage",
            "attack_speed", "movement_speed", "knockback_resistance", "luck"
    };

    private final Main plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ItemFactory items;
    private final Random random = new Random();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private AbilityDefinition evolution;

    public StatsManager(Main plugin, ConfigManager config, Messages messages, ItemFactory items) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.items = items;
        reload();
        load();
    }

    public void reload() {
        ConfigurationSection section = config.get("essence_abilities.yml")
                .getConfigurationSection("abilities.evolution");
        evolution = section == null ? null : AbilityDefinition.fromConfig("evolution", section);
    }

    public void load() {
        stats.clear();
        FileConfiguration data = config.get("data/stats.yml");
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = players.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                PlayerStats playerStats = new PlayerStats();
                playerStats.spentPoints(section.getInt("spent-points", 0));
                ConfigurationSection points = section.getConfigurationSection("points");
                if (points != null) {
                    for (String stat : points.getKeys(false)) {
                        playerStats.points(stat, points.getInt(stat, 0));
                    }
                }
                stats.put(uuid, playerStats);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        FileConfiguration data = config.get("data/stats.yml");
        data.set("players", null);
        for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
            String base = "players." + entry.getKey() + ".";
            data.set(base + "spent-points", entry.getValue().spentPoints());
            for (Map.Entry<String, Integer> point : entry.getValue().allPoints().entrySet()) {
                data.set(base + "points." + point.getKey(), point.getValue());
            }
        }
        config.save("data/stats.yml");
    }

    public PlayerStats get(UUID uuid) {
        return stats.computeIfAbsent(uuid, id -> new PlayerStats());
    }

    public int evolutionLevel(Player player) {
        int best = 0;
        for (ItemStack item : equipped(player)) {
            if (item == null) {
                continue;
            }
            Integer level = items.abilities(item).get("evolution");
            if (level != null) {
                best = Math.max(best, level);
            }
        }
        return best;
    }

    public boolean hasEvolutionEquipped(Player player) {
        return evolutionLevel(player) > 0;
    }

    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        double currentHealth = player.getHealth();
        remove(player);
        int level = evolutionLevel(player);
        if (level <= 0 || evolution == null) {
            return;
        }
        PlayerStats playerStats = get(player.getUniqueId());
        for (String stat : STATS) {
            Attribute attribute = attribute(stat);
            if (attribute == null) {
                continue;
            }
            double amount = Math.min(playerStats.points(stat) * increment(stat), cap(stat));
            if (amount <= 0) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "evo_" + stat);
            removeModifier(instance, key);
            instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
        // Al quitar y volver a poner el modificador de vida, Minecraft puede
        // recortar la vida actual. Se restaura para que no baje al reequipar.
        if (currentHealth > 0 && !player.isDead()) {
            AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
            double max = health == null ? currentHealth : health.getValue();
            player.setHealth(Math.min(max, Math.max(currentHealth, 1.0)));
        }
    }

    public void remove(Player player) {
        for (String stat : STATS) {
            Attribute attribute = attribute(stat);
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                removeModifier(instance, new NamespacedKey(plugin, "evo_" + stat));
            }
        }
    }

    private void removeModifier(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
            if (modifier.getKey().equals(key)) {
                instance.removeModifier(modifier);
            }
        }
    }

    public boolean addPoint(Player player, String stat, int amount) {
        PlayerStats playerStats = get(player.getUniqueId());
        int current = playerStats.points(stat);
        double increment = increment(stat);
        double cap = cap(stat);
        if (increment <= 0) {
            return false;
        }
        int maxPoints = (int) Math.round(cap / increment);
        int newValue = Math.min(maxPoints, current + amount);
        if (newValue <= current) {
            return false;
        }
        playerStats.points(stat, newValue);
        apply(player);
        return true;
    }

    /**
     * Evolution da 1 punto por mob (o jefe) cuando sale la tirada. La
     * probabilidad depende del nivel de Evolution y del tier del mob:
     * normal_mob_chance es la base para tier 1 y chance-per-tier suma un
     * porcentaje por cada tier adicional. Los jefes siempre cuentan.
     */
    public void onKill(Player player, boolean boss, int mobTier) {
        int level = evolutionLevel(player);
        if (level <= 0 || evolution == null) {
            return;
        }
        boolean bossAlways = evolution.evolutionBool("boss_always_counts", true);
        int chance;
        if (boss) {
            chance = bossAlways ? 100 : evolution.integer(level, "boss_chance", 100);
        } else {
            int base = evolution.integer(level, "normal_mob_chance", 10);
            int perTier = Math.max(0, (int) Math.round(evolution.evolutionNum("chance-per-tier", 6)));
            int tier = Math.max(1, mobTier);
            chance = Math.min(100, base + (tier - 1) * perTier);
        }
        if (random.nextInt(100) >= chance) {
            return;
        }
        // 1 punto por kill. point-multiplier se mantiene por compatibilidad,
        // pero su valor por defecto es 1.0 (sin multiplicar).
        int points = boss ? evolution.integer(level, "boss_points", 1)
                : evolution.integer(level, "normal_points", 1);
        double multiplier = Math.max(0.0, evolution.evolutionNum("point-multiplier", 1.0));
        points = Math.max(1, (int) Math.round(points * multiplier));
        int foodCost = Math.max(0, (int) Math.round(evolution.evolutionNum("food-cost", 1.0)));
        if (player.getFoodLevel() < foodCost) {
            return;
        }
        if (foodCost > 0) {
            player.setFoodLevel(player.getFoodLevel() - foodCost);
        }
        int gained = 0;
        String gainedStat = null;
        for (int i = 0; i < points; i++) {
            String stat = chooseStat(player);
            if (stat == null) {
                break;
            }
            if (addPoint(player, stat, 1)) {
                gained++;
                if (gainedStat == null) {
                    gainedStat = stat;
                }
            }
        }
        if (gained > 0) {
            player.sendActionBar(Text.c(messages.raw("stats.gained-point")
                    .replace("%amount%", String.valueOf(gained))
                    .replace("%stat%", gainedStat == null ? "" : label(gainedStat))));
            player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.2f);
            save();
        }
    }

    private String chooseStat(Player player) {
        Map<String, Double> weights = map("weights");
        Map<String, Double> available = new LinkedHashMap<>();
        double total = 0;
        PlayerStats playerStats = get(player.getUniqueId());
        for (String stat : STATS) {
            double increment = increment(stat);
            if (increment <= 0) {
                continue;
            }
            int maxPoints = (int) Math.round(cap(stat) / increment);
            if (playerStats.points(stat) >= maxPoints) {
                continue;
            }
            double weight = weights.getOrDefault(stat, 1.0);
            if (weight <= 0) {
                continue;
            }
            available.put(stat, weight);
            total += weight;
        }
        if (available.isEmpty()) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (Map.Entry<String, Double> entry : available.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    public double cap(String stat) {
        return num("caps", stat, defaultCap(stat));
    }

    public double increment(String stat) {
        return num("increments", stat, defaultIncrement(stat));
    }

    private double defaultCap(String stat) {
        return switch (stat) {
            case "max_health" -> 100;
            case "armor" -> 30;
            case "toughness" -> 20;
            case "attack_damage" -> 40;
            case "attack_speed" -> 2.0;
            case "movement_speed" -> 0.08;
            case "knockback_resistance" -> 1.0;
            case "luck" -> 20;
            default -> 0;
        };
    }

    private double defaultIncrement(String stat) {
        return switch (stat) {
            case "max_health" -> 2;
            case "armor" -> 2;
            case "toughness" -> 2;
            case "attack_damage" -> 2;
            case "attack_speed" -> 0.1;
            case "movement_speed" -> 0.005;
            case "knockback_resistance" -> 0.1;
            case "luck" -> 2;
            default -> 0;
        };
    }

    private double num(String group, String stat, double def) {
        Map<String, Double> values = map(group);
        Double value = values.get(stat);
        return value == null ? def : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> map(String group) {
        if (evolution == null) {
            return Map.of();
        }
        Object value = evolution.evolution().get(group);
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getValue() instanceof Number n) {
                result.put(String.valueOf(entry.getKey()), n.doubleValue());
            }
        }
        return result;
    }

    public Attribute attribute(String stat) {
        return switch (stat) {
            case "max_health" -> Attribute.MAX_HEALTH;
            case "armor" -> Attribute.ARMOR;
            case "toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "attack_damage" -> Attribute.ATTACK_DAMAGE;
            case "attack_speed" -> Attribute.ATTACK_SPEED;
            case "movement_speed" -> Attribute.MOVEMENT_SPEED;
            case "knockback_resistance" -> Attribute.KNOCKBACK_RESISTANCE;
            case "luck" -> Attribute.LUCK;
            default -> null;
        };
    }

    public int totalPoints(Player player) {
        int total = 0;
        PlayerStats playerStats = get(player.getUniqueId());
        for (String stat : STATS) {
            total += playerStats.points(stat);
        }
        return total;
    }

    public int maxPoints() {
        int total = 0;
        for (String stat : STATS) {
            double increment = increment(stat);
            if (increment > 0) {
                total += (int) Math.round(cap(stat) / increment);
            }
        }
        return total;
    }

    public List<StatLine> snapshot(Player player) {
        List<StatLine> lines = new ArrayList<>();
        PlayerStats playerStats = get(player.getUniqueId());
        boolean evolutionActive = hasEvolutionEquipped(player);
        for (String stat : STATS) {
            Attribute attribute = attribute(stat);
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            double total = instance.getValue();
            double base = instance.getBaseValue();
            double evolutionValue = Math.min(playerStats.points(stat) * increment(stat), cap(stat));
            double equipment = total - base - (evolutionActive ? evolutionValue : 0);
            lines.add(new StatLine(
                    stat,
                    label(stat),
                    base,
                    evolutionValue,
                    equipment,
                    total,
                    playerStats.points(stat),
                    (int) Math.round(cap(stat) / Math.max(0.0001, increment(stat))),
                    cap(stat),
                    increment(stat),
                    playerStats.points(stat) >= (int) Math.round(cap(stat) / Math.max(0.0001, increment(stat)))
            ));
        }
        return lines;
    }

    public String label(String stat) {
        return switch (stat) {
            case "max_health" -> "Vida maxima";
            case "armor" -> "Armadura";
            case "toughness" -> "Dureza";
            case "attack_damage" -> "Dano de ataque";
            case "attack_speed" -> "Velocidad de ataque";
            case "movement_speed" -> "Velocidad";
            case "knockback_resistance" -> "Resistencia al empuje";
            case "luck" -> "Luck";
            default -> stat;
        };
    }

    private List<ItemStack> equipped(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null) {
                items.add(item);
            }
        }
        if (player.getInventory().getItemInMainHand().getType().isAir() == false) {
            items.add(player.getInventory().getItemInMainHand());
        }
        if (player.getInventory().getItemInOffHand().getType().isAir() == false) {
            items.add(player.getInventory().getItemInOffHand());
        }
        return items;
    }

    public record StatLine(String key, String label, double base, double evolution,
                           double equipment, double total, int points, int maxPoints,
                           double cap, double increment, boolean maxed) {
    }
}

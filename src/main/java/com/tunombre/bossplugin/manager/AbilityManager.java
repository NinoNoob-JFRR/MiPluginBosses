package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.config.Messages;
import com.tunombre.bossplugin.model.AbilityDefinition;
import com.tunombre.bossplugin.util.Keys;
import com.tunombre.bossplugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aplica esencias, ejecuta habilidades pasivas y activas y guarda datos
 * de Waypoint y MagicBag.
 */
public class AbilityManager {

    private final Main plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ItemFactory items;

    private final Map<String, AbilityDefinition> abilities = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> timers = new HashMap<>();
    private final Map<UUID, List<Waypoint>> waypoints = new HashMap<>();
    private final Map<UUID, ItemStack[]> bags = new HashMap<>();
    private final Set<UUID> flightEnabled = new HashSet<>();
    private final Set<UUID> hardyApplied = new HashSet<>();
    private final Set<UUID> internalDamage = new HashSet<>();
    private final Set<UUID> mining = new HashSet<>();
    private final Map<UUID, Integer> pendingRename = new HashMap<>();
    private final Map<UUID, Long> renameExpiry = new HashMap<>();

    public AbilityManager(Main plugin, ConfigManager config, Messages messages, ItemFactory items) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.items = items;
        reload();
        loadWaypoints();
        loadBags();
    }

    public void reload() {
        abilities.clear();
        AbilityDefinition.foodCostMultiplier(
                config.getDouble("config.yml", "abilities.food-cost-multiplier", 1.0));
        ConfigurationSection section = config.get("essence_abilities.yml").getConfigurationSection("abilities");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection ability = section.getConfigurationSection(id);
            if (ability != null) {
                abilities.put(id.toLowerCase(Locale.ROOT), AbilityDefinition.fromConfig(id, ability));
            }
        }
        plugin.getLogger().info("Habilidades de jugador cargadas: " + abilities.size());
    }

    public AbilityDefinition ability(String id) {
        return abilities.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    public Map<String, AbilityDefinition> abilities() {
        return abilities;
    }

    // ------------------------------------------------------------
    // Aplicar esencias
    // ------------------------------------------------------------

    /**
     * Se llama al hacer clic derecho en el aire. Un item es la esencia y el
     * otro el objeto destino.
     */
    public void tryApply(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean mainEssence = items.isType(main, "ability_essence");
        boolean offEssence = items.isType(off, "ability_essence");
        if (mainEssence == offEssence) {
            return;
        }
        if (mainEssence) {
            apply(player, off, main, EquipmentSlot.OFF_HAND);
        } else {
            apply(player, main, off, EquipmentSlot.HAND);
        }
    }

    private void apply(Player player, ItemStack target, ItemStack essence, EquipmentSlot targetSlot) {
        if (target == null || target.getType().isAir() || essence == null) {
            return;
        }
        AbilityDefinition definition = ability(items.abilityId(essence));
        if (definition == null) {
            return;
        }
        if (!isValidTarget(definition, target)) {
            messages.send(player, "essence.wrong-item", "ability", definition.name());
            return;
        }
        Map<String, Integer> current = new LinkedHashMap<>(items.abilities(target));
        String abilityKey = definition.id().toLowerCase(Locale.ROOT);
        if ("ACTIVE".equals(definition.type()) && !current.isEmpty() && !current.containsKey(abilityKey)) {
            messages.send(player, "essence.no-slot");
            return;
        }
        int level = current.getOrDefault(abilityKey, 0);
        int needed = definition.maxLevel() - level;
        if (needed <= 0) {
            messages.send(player, "essence.max-level", "ability", definition.name());
            return;
        }
        int consume = Math.min(essence.getAmount(), needed);
        int newLevel = level + consume;
        current.put(abilityKey, newLevel);
        items.abilities(target, current);

        if ("ACTIVE".equals(definition.type())) {
            items.makeAbilityBook(target, definition, newLevel);
        } else {
            items.setAppliedLore(target, definition, newLevel);
        }

        player.getInventory().setItem(targetSlot, target);
        int remaining = essence.getAmount() - consume;
        if (remaining <= 0) {
            if (targetSlot == EquipmentSlot.HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            essence.setAmount(remaining);
            if (targetSlot == EquipmentSlot.HAND) {
                player.getInventory().setItemInOffHand(essence);
            } else {
                player.getInventory().setItemInMainHand(essence);
            }
        }
        messages.send(player, "essence.applied", "ability", definition.name(),
                "level", String.valueOf(newLevel));
        player.playSound(player.getLocation(), "block.enchantment_table.use", 0.7f, 1.4f);
    }

    private boolean isValidTarget(AbilityDefinition definition, ItemStack target) {
        if ("ACTIVE".equals(definition.type())) {
            if (target.getType() != Material.BOOK || !target.getEnchantments().isEmpty()) {
                return false;
            }
            Map<String, Integer> existing = items.abilities(target);
            // Un libro normal (sin habilidad) o un libro que ya tiene esta
            // misma habilidad activa (para subir nivel).
            return existing.isEmpty()
                    || (existing.size() == 1
                    && existing.containsKey(definition.id().toLowerCase(Locale.ROOT)));
        }
        if (target.getType() == Material.BOOK || target.getType() == Material.ENCHANTED_BOOK) {
            return false;
        }
        String category = category(target.getType());
        return definition.compatibleWith().contains(category);
    }

    /**
     * Calcula el resultado de aplicar una esencia en una mesa de crafteo.
     * No modifica el inventario: devuelve el item resultante o null si la
     * combinacion no es valida. count es la cantidad total de esencias
     * disponibles; se consumen tantas como niveles falten hasta el maximo.
     */
    public ItemStack craftResult(ItemStack target, ItemStack essence, int count) {
        CraftPlan plan = craftPlan(target, essence, count);
        if (plan == null) {
            return null;
        }
        ItemStack result = target.clone();
        result.setAmount(1);
        items.abilities(result, plan.abilities());
        if ("ACTIVE".equals(plan.definition().type())) {
            items.makeAbilityBook(result, plan.definition(), plan.level());
        } else {
            items.setAppliedLore(result, plan.definition(), plan.level());
        }
        return result;
    }

    public ItemStack craftResult(ItemStack target, ItemStack essence) {
        return craftResult(target, essence, essence == null ? 1 : essence.getAmount());
    }

    /** Unidades de esencia que consumiria el crafteo (0 si no es valido). */
    public int craftConsume(ItemStack target, ItemStack essence, int count) {
        CraftPlan plan = craftPlan(target, essence, count);
        return plan == null ? 0 : plan.consume();
    }

    private CraftPlan craftPlan(ItemStack target, ItemStack essence, int count) {
        if (target == null || target.getType().isAir() || essence == null) {
            return null;
        }
        if (!items.isType(essence, "ability_essence")) {
            return null;
        }
        AbilityDefinition definition = ability(items.abilityId(essence));
        if (definition == null || !isValidTarget(definition, target)) {
            return null;
        }
        Map<String, Integer> current = new LinkedHashMap<>(items.abilities(target));
        String abilityKey = definition.id().toLowerCase(Locale.ROOT);
        if ("ACTIVE".equals(definition.type()) && !current.isEmpty() && !current.containsKey(abilityKey)) {
            return null;
        }
        int level = current.getOrDefault(abilityKey, 0);
        int needed = definition.maxLevel() - level;
        if (needed <= 0) {
            return null;
        }
        int available = Math.max(1, Math.min(count, essence.getAmount()));
        int consume = Math.min(available, needed);
        int newLevel = level + consume;
        current.put(abilityKey, newLevel);
        return new CraftPlan(definition, current, newLevel, consume);
    }

    private record CraftPlan(AbilityDefinition definition, Map<String, Integer> abilities,
                             int level, int consume) {
    }

    private String category(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) {
            return "HELMET";
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return "CHESTPLATE";
        }
        if (name.endsWith("_LEGGINGS")) {
            return "LEGGINGS";
        }
        if (name.endsWith("_BOOTS")) {
            return "BOOTS";
        }
        if (name.endsWith("_SWORD")) {
            return "SWORD";
        }
        if (name.endsWith("_AXE")) {
            return "AXE";
        }
        if (name.endsWith("_PICKAXE")) {
            return "PICKAXE";
        }
        if (name.endsWith("_SHOVEL")) {
            return "SHOVEL";
        }
        if (name.endsWith("_HOE")) {
            return "HOE";
        }
        if (name.equals("BOW")) {
            return "BOW";
        }
        if (name.equals("CROSSBOW")) {
            return "CROSSBOW";
        }
        if (name.equals("TRIDENT")) {
            return "TRIDENT";
        }
        if (name.equals("SHIELD")) {
            return "SHIELD";
        }
        return name;
    }

    // ------------------------------------------------------------
    // Niveles pasivos equipados
    // ------------------------------------------------------------

    public Map<String, Integer> passiveLevels(Player player) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        List<ItemStack> equipment = new ArrayList<>();
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                equipment.add(item);
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (!main.getType().isAir()) {
            equipment.add(main);
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (!off.getType().isAir()) {
            equipment.add(off);
        }
        for (ItemStack item : equipment) {
            for (Map.Entry<String, Integer> entry : items.abilities(item).entrySet()) {
                AbilityDefinition definition = ability(entry.getKey());
                if (definition == null || !"PASSIVE".equals(definition.type())) {
                    continue;
                }
                levels.merge(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue(), Math::max);
            }
        }
        return levels;
    }

    // ------------------------------------------------------------
    // Tick de pasivas por intervalo
    // ------------------------------------------------------------

    public void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, Integer> levels = passiveLevels(player);
            for (AbilityDefinition definition : abilities.values()) {
                if (!"ON_INTERVAL".equals(definition.trigger())) {
                    continue;
                }
                int level = levels.getOrDefault(definition.id().toLowerCase(Locale.ROOT), 0);
                if (level <= 0) {
                    continue;
                }
                String key = definition.id().toLowerCase(Locale.ROOT);
                int interval = Math.max(1, definition.integer(level, "interval_seconds", definition.intervalSeconds()));
                Map<String, Long> playerTimers = timers.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
                long last = playerTimers.getOrDefault(key, 0L);
                if (now - last < interval * 1000L) {
                    continue;
                }
                int cost = definition.foodCost(level);

                if ("fly".equals(key)) {
                    if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                            || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        continue;
                    }
                    if (!player.getAllowFlight()) {
                        player.setAllowFlight(true);
                        flightEnabled.add(player.getUniqueId());
                    }
                    if (!player.isFlying()) {
                        playerTimers.put(key, now);
                        continue;
                    }
                    if (!pay(player, cost)) {
                        player.setFlying(false);
                        continue;
                    }
                    playerTimers.put(key, now);
                    continue;
                }

                if (!pay(player, cost)) {
                    continue;
                }
                playerTimers.put(key, now);
                switch (key) {
                    case "magnet" -> magnet(player, definition, level);
                    case "durability" -> repairArmor(player, definition, level);
                    case "regeneration" -> potion(player, PotionEffectType.REGENERATION,
                            Math.max(1, definition.integer(level, "effect_level", level)),
                            Math.max(1, definition.integer(level, "duration_seconds", 5)));
                    case "hardy" -> applyHardy(player, definition, level);
                    default -> {
                    }
                }
            }
            if (!levels.containsKey("fly") && flightEnabled.remove(player.getUniqueId())) {
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE
                        && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
            }
            if (!levels.containsKey("hardy")) {
                clearHardy(player);
            }
        }
    }

    /**
     * Hardy: los corazones de absorcion necesitan capacidad en el atributo
     * MAX_ABSORPTION (por defecto 0), si no setAbsorptionAmount se recorta a 0.
     */
    private void applyHardy(Player player, AbilityDefinition definition, int level) {
        double amount = Math.max(0, definition.num(level, "absorption_hearts", 3) * 2.0);
        if (amount <= 0) {
            return;
        }
        AttributeInstance maxAbsorption = player.getAttribute(Attribute.MAX_ABSORPTION);
        if (maxAbsorption != null) {
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "hardy_absorption");
            for (AttributeModifier modifier : new ArrayList<>(maxAbsorption.getModifiers())) {
                if (modifier.getKey().equals(key)) {
                    maxAbsorption.removeModifier(modifier);
                }
            }
            maxAbsorption.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (player.getAbsorptionAmount() < amount) {
            player.setAbsorptionAmount(amount);
        }
        hardyApplied.add(player.getUniqueId());
    }

    private void clearHardy(Player player) {
        if (!hardyApplied.remove(player.getUniqueId())) {
            return;
        }
        AttributeInstance maxAbsorption = player.getAttribute(Attribute.MAX_ABSORPTION);
        if (maxAbsorption != null) {
            for (AttributeModifier modifier : new ArrayList<>(maxAbsorption.getModifiers())) {
                if (modifier.getKey().getKey().equals("hardy_absorption")) {
                    maxAbsorption.removeModifier(modifier);
                }
            }
        }
        if (player.getAbsorptionAmount() > 0) {
            player.setAbsorptionAmount(0);
        }
    }

    private void magnet(Player player, AbilityDefinition definition, int level) {
        double radius = definition.num(level, "radius", 4);
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(
                player.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            if (item.getPickupDelay() > 0 || item.isDead()) {
                continue;
            }
            Location target = player.getLocation().add(0, 0.8, 0);
            item.setVelocity(target.toVector().subtract(item.getLocation().toVector()).normalize().multiply(0.6));
        }
    }

    private void repairArmor(Player player, AbilityDefinition definition, int level) {
        int repair = Math.max(1, definition.integer(level, "repair_per_interval", level));
        for (ItemStack item : player.getInventory().getArmorContents()) {
            repair(item, repair);
        }
    }

    private void repair(ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            return;
        }
        damageable.setDamage(Math.max(0, damageable.getDamage() - amount));
        item.setItemMeta(meta);
    }

    private void potion(Player player, PotionEffectType type, int level, int seconds) {
        player.addPotionEffect(new PotionEffect(type, Math.max(20, seconds * 20),
                Math.max(0, level - 1), true, false, true));
    }

    // ------------------------------------------------------------
    // Ataques
    // ------------------------------------------------------------

    public void onHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        if (internalDamage.contains(victim.getUniqueId())) {
            return;
        }
        Map<String, Integer> levels = passiveLevels(attacker);
        if (levels.isEmpty()) {
            return;
        }

        Integer backstabLevel = levels.get("backstab");
        if (backstabLevel != null && isBehind(attacker, victim)) {
            AbilityDefinition definition = ability("backstab");
            if (definition != null && pay(attacker, definition.foodCost(backstabLevel))) {
                event.setDamage(event.getDamage() * definition.num(backstabLevel, "multiplier", 1.5));
                notifyCombat(attacker, victim, definition, backstabLevel);
            }
        }

        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            AbilityDefinition definition = ability(entry.getKey());
            if (definition == null || !"PASSIVE".equals(definition.type())) {
                continue;
            }
            if (!"ON_HIT".equals(definition.trigger())) {
                continue;
            }
            int level = entry.getValue();
            int cost = definition.foodCost(level);
            switch (entry.getKey()) {
                case "poison" -> {
                    if (pay(attacker, cost)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                                Math.max(1, definition.integer(level, "duration_seconds", 5)) * 20,
                                Math.max(0, definition.integer(level, "poison_level", level) - 1), false, true, true));
                        notifyCombat(attacker, victim, definition, level);
                    }
                }
                case "blinding" -> {
                    if (pay(attacker, cost)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                                Math.max(1, definition.integer(level, "duration_seconds", 3)) * 20,
                                Math.max(0, definition.integer(level, "effect_level", 1) - 1), false, true, true));
                        notifyCombat(attacker, victim, definition, level);
                    }
                }
                case "confuse" -> {
                    if (pay(attacker, cost)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                                Math.max(1, definition.integer(level, "duration_seconds", 5)) * 20,
                                Math.max(0, definition.integer(level, "effect_level", 1) - 1), false, true, true));
                        notifyCombat(attacker, victim, definition, level);
                    }
                }
                case "slow" -> {
                    if (pay(attacker, cost)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                                Math.max(1, definition.integer(level, "duration_seconds", 5)) * 20,
                                Math.max(0, definition.integer(level, "effect_level", 1) - 1), false, true, true));
                        notifyCombat(attacker, victim, definition, level);
                    }
                }
                case "weaken" -> {
                    if (pay(attacker, cost)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                                Math.max(1, definition.integer(level, "duration_seconds", 5)) * 20,
                                Math.max(0, definition.integer(level, "effect_level", 1) - 1), false, true, true));
                        notifyCombat(attacker, victim, definition, level);
                    }
                }
                case "vampiric_regeneration" -> {
                    int chance = definition.integer(level, "chance", 20);
                    if (chance >= 100 || (chance > 0 && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < chance)) {
                        if (pay(attacker, cost)) {
                            double heal = event.getFinalDamage() * definition.num(level, "steal_percent", 5) / 100.0;
                            AttributeInstance health = attacker.getAttribute(Attribute.MAX_HEALTH);
                            double max = health == null ? attacker.getHealth() : health.getValue();
                            attacker.setHealth(Math.min(max, attacker.getHealth() + Math.max(0.5, heal)));
                            notifyCombat(attacker, victim, definition, level,
                                    Map.of("steal", Text.format(heal)));
                        }
                    }
                }
                case "bleed" -> {
                    if (pay(attacker, cost)) {
                        bleed(attacker, victim, definition, level);
                        notifyCombat(attacker, victim, definition, level);
                    }
                }
                case "armor_piercer" -> {
                    if (pay(attacker, cost)) {
                        AttributeInstance armor = victim.getAttribute(Attribute.ARMOR);
                        double armorValue = armor == null ? 0 : armor.getValue();
                        double percent = definition.num(level, "ignore_armor_percent", 10) / 100.0;
                        event.setDamage(event.getDamage() + event.getDamage() * percent * (armorValue / 20.0));
                        notifyCombat(attacker, victim, definition, level);
                    }
                }
                case "call_lighting" -> {
                    int chance = definition.integer(level, "chance", 25);
                    if (chance >= 100 || (chance > 0 && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < chance)) {
                        if (pay(attacker, cost)) {
                            lightning(attacker, victim, definition, level, event);
                            notifyCombat(attacker, victim, definition, level);
                        }
                    }
                }
                case "disarm" -> {
                    int chance = definition.integer(level, "chance", 10);
                    if (chance >= 100 || (chance > 0 && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < chance)) {
                        if (pay(attacker, cost)) {
                            disarm(victim);
                            notifyCombat(attacker, victim, definition, level);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    private boolean isBehind(Player attacker, LivingEntity victim) {
        org.bukkit.util.Vector facing = victim.getLocation().getDirection().setY(0);
        org.bukkit.util.Vector toAttacker = attacker.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() < 0.01 || toAttacker.lengthSquared() < 0.01) {
            return false;
        }
        return facing.normalize().dot(toAttacker.normalize()) < -0.2;
    }

    private void bleed(Player attacker, LivingEntity victim, AbilityDefinition definition, int level) {
        double damage = Math.max(0.5, definition.num(level, "damage_per_second", 1));
        int seconds = Math.max(1, definition.integer(level, "duration_seconds", 3));
        final int[] ticks = {seconds};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (ticks[0]-- <= 0 || victim.isDead() || !victim.isValid() || !attacker.isOnline()) {
                task.cancel();
                return;
            }
            applyInternalDamage(victim, damage, attacker);
        }, 20L, 20L);
    }

    /**
     * Aplica dano sin que las habilidades del jugador vuelvan a dispararse
     * (evita cadenas infinitas de sangrado o rayos).
     */
    private void applyInternalDamage(LivingEntity victim, double amount, Player source) {
        internalDamage.add(victim.getUniqueId());
        try {
            victim.damage(Math.max(0.1, amount), source);
        } finally {
            internalDamage.remove(victim.getUniqueId());
        }
    }

    private void lightning(Player attacker, LivingEntity victim, AbilityDefinition definition, int level,
                           EntityDamageByEntityEvent event) {
        double radius = definition.num(level, "radius", 3);
        int maxTargets = Math.max(1, definition.integer(level, "max_targets", 2));
        double percent = definition.num(level, "damage_percent", 30) / 100.0;
        double damage = Math.max(0.5, event.getFinalDamage() * percent);
        World world = victim.getWorld();
        world.strikeLightningEffect(victim.getLocation());
        int hit = 0;
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(victim.getLocation(), radius, radius, radius)) {
            if (hit >= maxTargets) {
                break;
            }
            if (entity instanceof LivingEntity living && !living.equals(attacker) && !living.equals(victim)) {
                applyInternalDamage(living, damage, attacker);
                hit++;
            }
        }
    }

    private void disarm(LivingEntity victim) {
        if (victim.getEquipment() == null) {
            return;
        }
        ItemStack hand = victim.getEquipment().getItemInMainHand();
        if (hand.getType().isAir()) {
            return;
        }
        victim.getWorld().dropItemNaturally(victim.getLocation(), hand);
        victim.getEquipment().setItemInMainHand(null);
    }

    private void notifyCombat(Player attacker, LivingEntity victim, AbilityDefinition definition, int level) {
        notifyCombat(attacker, victim, definition, level, Map.of());
    }

    private void notifyCombat(Player attacker, LivingEntity victim, AbilityDefinition definition,
                              int level, Map<String, String> extra) {
        if (!definition.combatNotify()) {
            return;
        }
        String message = definition.combatMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        Map<String, String> values = definition.placeholders(level);
        values.put("target", victim.getName());
        values.putAll(extra);
        attacker.sendActionBar(Text.c(Text.apply(message, values)));
    }

    // ------------------------------------------------------------
    // Rotura de bloques
    // ------------------------------------------------------------

    public void onBlockBreak(Player player, Block block, BlockBreakEvent event) {
        Map<String, Integer> levels = passiveLevels(player);
        if (levels.isEmpty()) {
            return;
        }
        Integer veinLevel = levels.get("vein_miner");
        if (veinLevel != null && isOre(block.getType())) {
            AbilityDefinition definition = ability("vein_miner");
            if (definition != null && pay(player, definition.foodCost(veinLevel))) {
                int max = Math.max(2, definition.integer(veinLevel, "max_blocks", 16));
                breakConnected(block, max, material -> material == block.getType(), player);
            }
            return;
        }
        Integer treeLevel = levels.get("tree_feller");
        if (treeLevel != null && isLog(block.getType())) {
            AbilityDefinition definition = ability("tree_feller");
            if (definition != null) {
                int cost = definition.foodCost(treeLevel);
                if (pay(player, cost)) {
                    int max = Math.max(2, definition.integer(treeLevel, "max_logs", 64));
                    breakConnected(block, max, this::isLog, player);
                }
            }
        }
    }

    private void breakConnected(Block origin, int max, java.util.function.Predicate<Material> predicate, Player player) {
        if (!mining.add(player.getUniqueId())) {
            return;
        }
        try {
            ItemStack tool = player.getInventory().getItemInMainHand();
            Deque<Block> queue = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            queue.add(origin);
            visited.add(key(origin));
            int broken = 0;
            while (!queue.isEmpty() && broken < max) {
                Block current = queue.poll();
                for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN,
                        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                    Block next = current.getRelative(face);
                    if (!visited.add(key(next))) {
                        continue;
                    }
                    if (predicate.test(next.getType())) {
                        queue.add(next);
                        if (broken < max) {
                            next.breakNaturally(tool);
                            broken++;
                        }
                    }
                }
            }
        } finally {
            mining.remove(player.getUniqueId());
        }
    }

    private String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
    }

    // ------------------------------------------------------------
    // Durabilidad
    // ------------------------------------------------------------

    public void onItemDamage(Player player, PlayerItemDamageEvent event) {
        Map<String, Integer> levels = passiveLevels(player);
        Integer level = levels.get("durability");
        if (level == null) {
            return;
        }
        AbilityDefinition definition = ability("durability");
        if (definition == null) {
            return;
        }
        int chance = definition.integer(level, "avoid_break_chance", 10);
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < chance) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------
    // Habilidades activas
    // ------------------------------------------------------------

    public boolean tryUseActive(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        ItemStack book = activeBook(main);
        boolean mainHand = true;
        if (book == null) {
            book = activeBook(off);
            mainHand = false;
        }
        if (book == null) {
            return false;
        }
        Map<String, Integer> stored = items.abilities(book);
        if (stored.isEmpty()) {
            return false;
        }
        Map.Entry<String, Integer> entry = stored.entrySet().iterator().next();
        AbilityDefinition definition = ability(entry.getKey());
        if (definition == null || !"ACTIVE".equals(definition.type())) {
            return false;
        }
        int level = entry.getValue();
        switch (definition.id().toLowerCase(Locale.ROOT)) {
            case "waypoint" -> {
                openWaypointMenu(player, definition, level);
                return true;
            }
            case "magic_bag" -> {
                if (pay(player, definition.foodCost(level))) {
                    openBag(player, definition, level);
                }
                return true;
            }
            case "craft_item" -> {
                if (pay(player, definition.foodCost(level))) {
                    player.openWorkbench(player.getLocation(), true);
                }
                return true;
            }
            case "ender_bag" -> {
                if (pay(player, definition.foodCost(level))) {
                    player.openInventory(player.getEnderChest());
                }
                return true;
            }
            case "fertilize" -> {
                if (pay(player, definition.foodCost(level))) {
                    fertilize(player, definition, level);
                }
                return true;
            }
            case "greater_burst" -> {
                if (pay(player, definition.foodCost(level))) {
                    burst(player, definition, level);
                }
                return true;
            }
            case "speed" -> {
                if (pay(player, definition.foodCost(level))) {
                    potion(player, PotionEffectType.SPEED,
                            Math.max(1, definition.integer(level, "effect_level", level)),
                            Math.max(1, definition.integer(level, "duration_seconds", 30)));
                    player.playSound(player.getLocation(), "entity.experience_orb.pickup", 0.6f, 1.6f);
                }
                return true;
            }
            case "repair_item" -> {
                ItemStack target = mainHand ? player.getInventory().getItemInOffHand()
                        : player.getInventory().getItemInMainHand();
                ItemMeta meta = target.getItemMeta();
                if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
                    return true;
                }
                int cost = definition.foodCost(level);
                if (!pay(player, cost)) {
                    return true;
                }
                int max = target.getType().getMaxDurability();
                int repair = (int) Math.ceil(max * definition.num(level, "repair_percent", 10) / 100.0);
                damageable.setDamage(Math.max(0, damageable.getDamage() - repair));
                target.setItemMeta(meta);
                if (mainHand) {
                    player.getInventory().setItemInOffHand(target);
                } else {
                    player.getInventory().setItemInMainHand(target);
                }
                player.playSound(player.getLocation(), "block.anvil.use", 0.6f, 1.4f);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Devuelve el item si es un libro de habilidad activa. Tambien acepta
     * libros antiguos sin ITEM_TYPE siempre que la habilidad guardada sea ACTIVE.
     */
    public boolean isActiveBook(ItemStack item) {
        return activeBook(item) != null;
    }

    private ItemStack activeBook(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        Map<String, Integer> stored = items.abilities(item);
        if (stored.isEmpty()) {
            return null;
        }
        if (!"active_ability".equals(items.itemType(item))) {
            AbilityDefinition definition = ability(stored.keySet().iterator().next());
            if (definition == null || !"ACTIVE".equals(definition.type())) {
                return null;
            }
        }
        return item;
    }

    private void fertilize(Player player, AbilityDefinition definition, int level) {
        int radius = Math.max(1, definition.integer(level, "radius", 3));
        World world = player.getWorld();
        Location center = player.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (Math.random() < 0.35) {
                        block.applyBoneMeal(BlockFace.UP);
                    }
                }
            }
        }
        world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, center.clone().add(0, 1, 0),
                40, radius / 2.0, 0.4, radius / 2.0, 0.01);
    }

    private void burst(Player player, AbilityDefinition definition, int level) {
        double radius = definition.num(level, "radius", 5);
        double force = definition.num(level, "force", 1.5);
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(
                player.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }
            if (!(living instanceof Monster || living instanceof Slime || living instanceof Golem)) {
                continue;
            }
            org.bukkit.util.Vector direction = living.getLocation().toVector()
                    .subtract(player.getLocation().toVector());
            if (direction.lengthSquared() > 0.01) {
                direction.normalize();
            }
            living.setVelocity(living.getVelocity().add(direction.multiply(force).setY(0.45)));
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.GUST, player.getLocation().add(0, 1, 0),
                1, 0, 0, 0, 0);
        player.playSound(player.getLocation(), "entity.breeze.wind_burst", 0.9f, 1.0f);
    }

    // ------------------------------------------------------------
    // Waypoints
    // ------------------------------------------------------------

    public void openWaypointMenu(Player player, AbilityDefinition definition, int level) {
        int maxSlots = Math.min(45, definition.integer(level, "max_slots", 5));
        List<Waypoint> list = waypoints.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        WaypointHolder holder = new WaypointHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.c(messages.raw("waypoint.title")));
        holder.inventory = inventory;
        holder.definition = definition;
        holder.level = level;
        holder.maxSlots = maxSlots;
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Text.c(" "));
            filler.setItemMeta(fillerMeta);
        }
        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        for (int i = 0; i < list.size() && i < maxSlots; i++) {
            Waypoint waypoint = list.get(i);
            ItemStack item = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Text.c(waypoint.name()));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Text.c("&7Mundo: &f" + waypoint.world()));
                lore.add(Text.c("&7X: &f" + waypoint.x() + " &7Y: &f" + waypoint.y() + " &7Z: &f" + waypoint.z()));
                lore.add(Text.c(""));
                lore.add(Text.c("&eClic izquierdo: &7teletransportarse"));
                lore.add(Text.c("&eClic derecho: &7eliminar"));
                lore.add(Text.c("&eShift + clic izquierdo: &7renombrar"));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(Keys.ABILITY_LEVEL, PersistentDataType.INTEGER, i);
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Text.c(messages.raw("general.menu-close")));
            close.setItemMeta(closeMeta);
        }
        inventory.setItem(49, close);
        player.openInventory(inventory);
    }

    /**
     * Guarda un waypoint en el primer hueco libre.
     */
    public void saveWaypoint(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        ItemStack book = "active_ability".equals(items.itemType(main)) ? main
                : ("active_ability".equals(items.itemType(off)) ? off : null);
        if (book == null) {
            return;
        }
        Map<String, Integer> stored = items.abilities(book);
        if (stored.isEmpty()) {
            return;
        }
        Map.Entry<String, Integer> entry = stored.entrySet().iterator().next();
        AbilityDefinition definition = ability(entry.getKey());
        if (definition == null || !"waypoint".equals(definition.id().toLowerCase(Locale.ROOT))) {
            return;
        }
        int level = entry.getValue();
        int maxSlots = Math.min(45, definition.integer(level, "max_slots", 5));
        List<Waypoint> list = waypoints.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        if (list.size() >= maxSlots) {
            messages.send(player, "waypoint.full");
            return;
        }
        int cost = AbilityDefinition.scaleFoodCost(definition.nestedCost("save", 2));
        if (!pay(player, cost)) {
            return;
        }
        int number = list.size() + 1;
        String name = definition.waypointString("default_name", "Waypoint %n%")
                .replace("%n%", String.valueOf(number));
        Location location = player.getLocation();
        list.add(new Waypoint(name, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        saveWaypoints();
        messages.send(player, "waypoint.saved", "name", name);
        player.playSound(player.getLocation(), "block.amethyst_block.place", 0.7f, 1.4f);
    }

    public void handleWaypointClick(Player player, WaypointHolder holder, InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot > 44) {
            return;
        }
        List<Waypoint> list = waypoints.computeIfAbsent(player.getUniqueId(), id -> new ArrayList<>());
        if (slot >= list.size()) {
            return;
        }
        Waypoint waypoint = list.get(slot);
        if (event.isRightClick()) {
            list.remove(slot);
            saveWaypoints();
            messages.send(player, "waypoint.deleted");
            openWaypointMenu(player, holder.definition, holder.level);
            return;
        }
        if (event.isShiftClick() && event.isLeftClick()) {
            pendingRename.put(player.getUniqueId(), slot);
            int timeout = Math.max(5, holder.definition.waypointInt("rename_timeout_seconds", 60));
            renameExpiry.put(player.getUniqueId(), System.currentTimeMillis() + timeout * 1000L);
            player.closeInventory();
            messages.send(player, "waypoint.rename-prompt");
            return;
        }
        if (event.isLeftClick()) {
            int cost = AbilityDefinition.scaleFoodCost(holder.definition.nestedCost("teleport", 5));
            if (!pay(player, cost)) {
                return;
            }
            Location destination = waypoint.location();
            if (destination == null) {
                messages.send(player, "general.world-not-found", "world", waypoint.world());
                return;
            }
            if (holder.definition.waypointInt("safe_teleport", 1) > 0) {
                destination = safe(destination);
            }
            player.closeInventory();
            player.teleport(destination);
            messages.send(player, "waypoint.teleported", "name", waypoint.name());
            player.playSound(player.getLocation(), "entity.enderman.teleport", 0.8f, 1.2f);
        }
    }

    public boolean handleRename(Player player, String message) {
        Integer index = pendingRename.remove(player.getUniqueId());
        Long expiry = renameExpiry.remove(player.getUniqueId());
        if (index == null) {
            return false;
        }
        if (expiry != null && System.currentTimeMillis() > expiry) {
            messages.send(player, "waypoint.rename-cancelled");
            return true;
        }
        if (message.equalsIgnoreCase("cancelar") || message.equalsIgnoreCase("cancel")) {
            messages.send(player, "waypoint.rename-cancelled");
            return true;
        }
        List<Waypoint> list = waypoints.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (index < 0 || index >= list.size()) {
            return true;
        }
        String cleaned = Text.strip(message).trim();
        int maxLength = 32;
        if (cleaned.isEmpty()) {
            messages.send(player, "waypoint.invalid");
            return true;
        }
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        Waypoint old = list.get(index);
        list.set(index, new Waypoint(Text.color(cleaned), old.world(), old.x(), old.y(), old.z(),
                old.yaw(), old.pitch()));
        saveWaypoints();
        messages.send(player, "waypoint.renamed", "name", Text.color(cleaned));
        return true;
    }

    public boolean isRenaming(Player player) {
        Integer pending = pendingRename.get(player.getUniqueId());
        if (pending == null) {
            return false;
        }
        Long expiry = renameExpiry.get(player.getUniqueId());
        if (expiry != null && System.currentTimeMillis() > expiry) {
            pendingRename.remove(player.getUniqueId());
            renameExpiry.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private Location safe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return location;
        }
        Location check = location.clone();
        for (int i = 0; i < 12; i++) {
            Block feet = check.getBlock();
            Block head = feet.getRelative(BlockFace.UP);
            Block below = feet.getRelative(BlockFace.DOWN);
            if ((feet.getType().isAir() || feet.isPassable())
                    && (head.getType().isAir() || head.isPassable())
                    && below.getType().isSolid()) {
                check.setY(check.getY());
                return check;
            }
            check.add(0, 1, 0);
        }
        return location;
    }

    private void loadWaypoints() {
        waypoints.clear();
        FileConfiguration data = config.get("data/waypoints.yml");
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<Waypoint> list = new ArrayList<>();
                for (Map<?, ?> raw : data.getMapList("players." + key + ".waypoints")) {
                    list.add(new Waypoint(
                            String.valueOf(raw.get("name")),
                            String.valueOf(raw.get("world")),
                            doubleNumber(raw.get("x")),
                            doubleNumber(raw.get("y")),
                            doubleNumber(raw.get("z")),
                            (float) doubleNumber(raw.get("yaw")),
                            (float) doubleNumber(raw.get("pitch"))
                    ));
                }
                waypoints.put(uuid, list);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveWaypoints() {
        FileConfiguration data = config.get("data/waypoints.yml");
        data.set("players", null);
        for (Map.Entry<UUID, List<Waypoint>> entry : waypoints.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Waypoint waypoint : entry.getValue()) {
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("name", waypoint.name());
                raw.put("world", waypoint.world());
                raw.put("x", waypoint.x());
                raw.put("y", waypoint.y());
                raw.put("z", waypoint.z());
                raw.put("yaw", waypoint.yaw());
                raw.put("pitch", waypoint.pitch());
                list.add(raw);
            }
            data.set("players." + entry.getKey() + ".waypoints", list);
        }
        config.save("data/waypoints.yml");
    }

    // ------------------------------------------------------------
    // MagicBag
    // ------------------------------------------------------------

    public void openBag(Player player, AbilityDefinition definition, int level) {
        int slots = definition.integer(level, "slots", 27);
        slots = Math.max(9, Math.min(54, (slots / 9) * 9));
        ItemStack[] contents = getBag(player.getUniqueId(), slots);
        BagHolder holder = new BagHolder(player.getUniqueId(), slots);
        Inventory inventory = Bukkit.createInventory(holder, slots, Text.c(messages.raw("bag.title")));
        holder.inventory = inventory;
        inventory.setContents(contents);
        player.openInventory(inventory);
    }

    private ItemStack[] getBag(UUID uuid, int slots) {
        ItemStack[] contents = bags.computeIfAbsent(uuid, id -> new ItemStack[slots]);
        if (contents.length != slots) {
            ItemStack[] resized = new ItemStack[slots];
            System.arraycopy(contents, 0, resized, 0, Math.min(contents.length, slots));
            bags.put(uuid, resized);
            contents = resized;
        }
        return contents;
    }

    public void saveBag(BagHolder holder) {
        if (holder == null || holder.inventory == null) {
            return;
        }
        ItemStack[] contents = holder.inventory.getContents();
        bags.put(holder.owner, contents);
        saveBags();
    }

    private void loadBags() {
        bags.clear();
        FileConfiguration data = config.get("data/magic_bags.yml");
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<String> encoded = data.getStringList("players." + key);
                ItemStack[] contents = new ItemStack[encoded.size()];
                for (int i = 0; i < encoded.size(); i++) {
                    if (encoded.get(i) == null || encoded.get(i).isBlank()) {
                        contents[i] = null;
                        continue;
                    }
                    contents[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded.get(i)));
                }
                bags.put(uuid, contents);
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo cargar la MagicBag de " + key + ": " + e.getMessage());
            }
        }
    }

    public void saveBags() {
        FileConfiguration data = config.get("data/magic_bags.yml");
        data.set("players", null);
        for (Map.Entry<UUID, ItemStack[]> entry : bags.entrySet()) {
            List<String> encoded = new ArrayList<>();
            for (ItemStack item : entry.getValue()) {
                if (item == null || item.getType().isAir()) {
                    encoded.add("");
                } else {
                    encoded.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                }
            }
            data.set("players." + entry.getKey(), encoded);
        }
        config.save("data/magic_bags.yml");
    }

    public void saveOpenBags() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof BagHolder holder) {
                saveBag(holder);
            }
        }
    }

    public void saveAll() {
        saveWaypoints();
        saveBags();
    }

    /**
     * Limpia el vuelo y los temporizadores al salir para no dejar estados
     * persistentes en el jugador.
     */
    public void onQuit(Player player) {
        if (flightEnabled.remove(player.getUniqueId())) {
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
        timers.remove(player.getUniqueId());
        pendingRename.remove(player.getUniqueId());
        renameExpiry.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------

    private boolean pay(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (player.getFoodLevel() < amount) {
            return false;
        }
        player.setFoodLevel(player.getFoodLevel() - amount);
        return true;
    }

    public boolean giveEssence(Player player, String abilityId, int amount) {
        AbilityDefinition definition = ability(abilityId);
        if (definition == null) {
            return false;
        }
        ItemStack item = items.abilityEssence(definition, 1);
        item.setAmount(Math.max(1, Math.min(64, amount)));
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        return true;
    }

    private static double doubleNumber(Object value) {
        return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    public record Waypoint(String name, String world, double x, double y, double z, float yaw, float pitch) {
        public Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    public static class WaypointHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;
        private AbilityDefinition definition;
        private int level;
        private int maxSlots;

        public WaypointHolder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static class BagHolder implements InventoryHolder {
        private final UUID owner;
        private final int slots;
        private Inventory inventory;

        public BagHolder(UUID owner, int slots) {
            this.owner = owner;
            this.slots = slots;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

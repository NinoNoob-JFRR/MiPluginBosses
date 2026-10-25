package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.config.Messages;
import com.tunombre.bossplugin.model.AbilityEffect;
import com.tunombre.bossplugin.model.BossAbilityDefinition;
import com.tunombre.bossplugin.model.BossDefinition;
import com.tunombre.bossplugin.util.Keys;
import com.tunombre.bossplugin.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Gestiona los jefes: spawn, barra, temporizador, habilidades, muerte y reinicio.
 */
public class BossManager {

    private final Main plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final ItemFactory items;
    private final TierManager tiers;
    private final MobTierManager mobTiers;
    private final LootManager loot;
    private final ProgressionManager progression;
    private final StatsManager stats;
    private final Random random = new Random();

    private final Map<String, BossDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, BossAbilityDefinition> abilityCatalog = new LinkedHashMap<>();
    private final Map<UUID, ActiveBoss> active = new HashMap<>();

    public BossManager(Main plugin, ConfigManager config, Messages messages, ItemFactory items,
                       TierManager tiers, MobTierManager mobTiers, LootManager loot,
                       ProgressionManager progression, StatsManager stats) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.items = items;
        this.tiers = tiers;
        this.mobTiers = mobTiers;
        this.loot = loot;
        this.progression = progression;
        this.stats = stats;
        reload();
    }

    public void reload() {
        definitions.clear();
        abilityCatalog.clear();

        ConfigurationSection bosses = config.get("bosses.yml").getConfigurationSection("bosses");
        if (bosses != null) {
            for (String id : bosses.getKeys(false)) {
                ConfigurationSection section = bosses.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                definitions.put(id.toLowerCase(Locale.ROOT), BossDefinition.fromConfig(id, section));
            }
        }

        ConfigurationSection abilities = config.get("boss_abilities.yml").getConfigurationSection("boss_abilities");
        if (abilities != null) {
            for (String id : abilities.getKeys(false)) {
                ConfigurationSection section = abilities.getConfigurationSection(id);
                if (section != null) {
                    abilityCatalog.put(id.toLowerCase(Locale.ROOT), BossAbilityDefinition.fromConfig(id, section));
                }
            }
        }
        plugin.getLogger().info("Jefes cargados: " + definitions.size()
                + " | habilidades de jefe: " + abilityCatalog.size());
    }

    public Collection<BossDefinition> bosses() {
        return definitions.values();
    }

    public BossDefinition definition(String id) {
        return definitions.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    public List<BossDefinition> bossesOfTier(int tier) {
        List<BossDefinition> list = new ArrayList<>();
        for (BossDefinition definition : definitions.values()) {
            if (definition.tier() == tier) {
                list.add(definition);
            }
        }
        return list;
    }

    public List<String> bossIdsOfTier(int tier) {
        List<String> ids = new ArrayList<>();
        for (BossDefinition definition : bossesOfTier(tier)) {
            ids.add(definition.id().toLowerCase(Locale.ROOT));
        }
        return ids;
    }

    public BossAbilityDefinition ability(String id) {
        return abilityCatalog.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
    }

    public Collection<BossAbilityDefinition> abilities() {
        return abilityCatalog.values();
    }

    public boolean isBoss(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getPersistentDataContainer().has(Keys.BOSS_ID, PersistentDataType.STRING);
    }

    public ActiveBoss active(Entity entity) {
        return entity == null ? null : active.get(entity.getUniqueId());
    }

    public Collection<ActiveBoss> activeBosses() {
        return active.values();
    }

    public ActiveBoss spawn(BossDefinition definition, Location location, Player source) {
        if (definition == null || location == null || location.getWorld() == null) {
            return null;
        }
        World world = location.getWorld();
        Entity spawned = world.spawnEntity(location, definition.entityType());
        if (!(spawned instanceof LivingEntity entity)) {
            spawned.remove();
            plugin.getLogger().warning("El jefe " + definition.id() + " no es una entidad viva.");
            return null;
        }

        mobTiers.clear(entity);
        loot.clear(entity);

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(Keys.BOSS_ID, PersistentDataType.STRING, definition.id());

        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setCanPickupItems(false);
        entity.customName(Text.c(tiers.color(definition.tier()) + definition.name()));
        entity.setCustomNameVisible(true);

        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(definition.health());
            entity.setHealth(definition.health());
        }
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(definition.damage());
        }
        AttributeInstance armor = entity.getAttribute(Attribute.ARMOR);
        if (armor != null) {
            armor.setBaseValue(definition.armor());
        }
        AttributeInstance toughness = entity.getAttribute(Attribute.ARMOR_TOUGHNESS);
        if (toughness != null) {
            toughness.setBaseValue(definition.toughness());
        }
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * (1.0 + definition.speedBonus()));
        }

        applyEquipment(entity, definition);

        ActiveBoss boss = new ActiveBoss(entity.getUniqueId(), definition, location.clone(), System.currentTimeMillis());
        active.put(entity.getUniqueId(), boss);

        boss.bar = Bukkit.createBossBar(barTitle(boss), BarColor.RED, BarStyle.SOLID);
        boss.bar.setVisible(true);
        updateBarPlayers(boss);

        triggerAbilities(boss, "ON_SPAWN", null);
        updateBar(boss);
        return boss;
    }

    private void applyEquipment(LivingEntity entity, BossDefinition definition) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.clear();
        for (Map.Entry<String, String> entry : definition.equipment().entrySet()) {
            Material material = Material.matchMaterial(entry.getValue());
            if (material == null) {
                plugin.getLogger().warning("Material desconocido en bosses.yml (" + definition.id() + "): " + entry.getValue());
                continue;
            }
            ItemStack item = new ItemStack(material);
            switch (entry.getKey()) {
                case "main_hand" -> equipment.setItemInMainHand(item, true);
                case "off_hand" -> equipment.setItemInOffHand(item, true);
                case "helmet" -> equipment.setHelmet(item, true);
                case "chestplate" -> equipment.setChestplate(item, true);
                case "leggings" -> equipment.setLeggings(item, true);
                case "boots" -> equipment.setBoots(item, true);
                default -> {
                }
            }
        }
        equipment.setItemInMainHandDropChance(0f);
        equipment.setItemInOffHandDropChance(0f);
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
    }

    private String barTitle(ActiveBoss boss) {
        LivingEntity entity = boss.entity();
        double max = maxHealth(entity);
        double current = entity == null ? 0 : Math.max(0, entity.getHealth());
        return Text.color(tiers.color(boss.definition.tier()) + boss.definition.name()
                + " &7[T" + boss.definition.tier() + "] &c" + Text.format(current) + "&7/&c" + Text.format(max));
    }

    public void updateBar(ActiveBoss boss) {
        LivingEntity entity = boss.entity();
        if (entity == null || boss.bar == null) {
            return;
        }
        double max = maxHealth(entity);
        double progress = max <= 0 ? 0 : Math.max(0, Math.min(1, entity.getHealth() / max));
        boss.bar.setProgress(progress);
        boss.bar.setTitle(barTitle(boss));
    }

    private void updateBarPlayers(ActiveBoss boss) {
        LivingEntity entity = boss.entity();
        if (entity == null || boss.bar == null) {
            return;
        }
        double maxDistance = config.getDouble("config.yml", "bossbar-max-distance", 100);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(entity.getWorld())
                    && player.getLocation().distanceSquared(entity.getLocation()) <= maxDistance * maxDistance) {
                boss.bar.addPlayer(player);
            } else {
                boss.bar.removePlayer(player);
            }
        }
    }

    /**
     * Tick rapido (cada 5 ticks): barra y temporizador de abandono.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        for (ActiveBoss boss : new ArrayList<>(active.values())) {
            LivingEntity entity = boss.entity();
            if (entity == null || entity.isDead() || !entity.isValid()) {
                remove(boss, false);
                continue;
            }
            updateBar(boss);
            updateBarPlayers(boss);
            tickAbandonTimer(boss, now);
        }
    }

    private void tickAbandonTimer(ActiveBoss boss, long now) {
        int limit = config.getInt("progression.yml", "progression.boss-timer.no-players-seconds", 60);
        List<Integer> warnings = config.get("progression.yml").getIntegerList("progression.boss-timer.warnings");
        LivingEntity entity = boss.entity();
        if (entity == null) {
            return;
        }
        boolean hasPlayers = !playersInRadius(entity.getLocation(),
                config.getDouble("progression.yml", "progression.boss-timer.player-radius", 40)).isEmpty();
        if (hasPlayers) {
            boss.noPlayersSince = 0;
            return;
        }
        if (boss.noPlayersSince == 0) {
            boss.noPlayersSince = now;
            return;
        }
        long elapsed = (now - boss.noPlayersSince) / 1000L;
        int remaining = (int) Math.max(0, limit - elapsed);
        if (warnings.contains(remaining) && boss.warnedSeconds.add(remaining)) {
            broadcastNear(entity.getLocation(), messages.get("boss.timer-warning",
                    "boss", boss.definition.name(), "seconds", String.valueOf(remaining)));
        }
        if (elapsed >= limit) {
            broadcastNear(entity.getLocation(), messages.get("boss.despawned",
                    "boss", boss.definition.name()));
            remove(boss, true);
        }
    }

    /**
     * Tick de habilidades (cada 20 ticks).
     */
    public void tickAbilities() {
        long now = System.currentTimeMillis();
        for (ActiveBoss boss : new ArrayList<>(active.values())) {
            LivingEntity entity = boss.entity();
            if (entity == null || entity.isDead() || !entity.isValid()) {
                continue;
            }
            for (BossAbilityDefinition ability : boss.abilities) {
                switch (ability.trigger()) {
                    case "TIMER" -> {
                        long interval = Math.max(1, ability.intervalSeconds()) * 1000L;
                        long last = boss.lastRun.getOrDefault(ability.id(), 0L);
                        if (now - last >= interval) {
                            boss.lastRun.put(ability.id(), now);
                            runAbility(boss, ability, null);
                        }
                    }
                    case "ON_PLAYER_DISTANCE" -> {
                        Player nearest = nearestPlayer(entity.getLocation(), 12);
                        long interval = Math.max(1, ability.intervalSeconds()) * 1000L;
                        long last = boss.lastRun.getOrDefault(ability.id(), 0L);
                        if (nearest != null && now - last >= interval) {
                            boss.lastRun.put(ability.id(), now);
                            runAbility(boss, ability, nearest);
                        }
                    }
                    case "ON_HEALTH" -> checkHealthThresholds(boss, ability, entity);
                    default -> {
                    }
                }
            }
        }
    }

    private void checkHealthThresholds(ActiveBoss boss, BossAbilityDefinition ability, LivingEntity entity) {
        double max = maxHealth(entity);
        if (max <= 0) {
            return;
        }
        double fraction = entity.getHealth() / max;
        for (double threshold : ability.healthThresholds()) {
            if (fraction <= threshold) {
                if (boss.thresholdsFired.add(ability.id() + ":" + threshold)) {
                    runAbility(boss, ability, null);
                }
            } else if (!ability.oncePerThreshold()) {
                boss.thresholdsFired.remove(ability.id() + ":" + threshold);
            }
        }
    }

    public void onBossDamaged(EntityDamageByEntityEvent event, LivingEntity bossEntity, Player damager) {
        ActiveBoss boss = active(bossEntity);
        if (boss == null) {
            return;
        }
        if (damager != null) {
            boss.damage.merge(damager.getUniqueId(), event.getFinalDamage(), Double::sum);
            boss.lastDamager = damager.getUniqueId();
        }
        triggerAbilities(boss, "ON_DAMAGE_TAKEN", damager);
    }

    public void onBossDealtDamage(LivingEntity bossEntity, LivingEntity victim, double damage) {
        ActiveBoss boss = active(bossEntity);
        if (boss == null) {
            return;
        }
        boss.lastVictim = victim.getLocation().clone();
        boss.lastDamageDealt = Math.max(0, damage);
        triggerAbilities(boss, "ON_DAMAGE_DEALT", victim instanceof Player player ? player : null);
    }

    private void triggerAbilities(ActiveBoss boss, String trigger, Player target) {
        for (BossAbilityDefinition ability : boss.abilities) {
            if (!ability.trigger().equals(trigger)) {
                continue;
            }
            if (ability.chance() < 100 && random.nextInt(100) >= ability.chance()) {
                continue;
            }
            runAbility(boss, ability, target);
        }
    }

    private void runAbility(ActiveBoss boss, BossAbilityDefinition ability, Player target) {
        LivingEntity entity = boss.entity();
        if (entity == null || entity.isDead()) {
            return;
        }
        if (config.get("config.yml").getBoolean("debug", false)) {
            plugin.getLogger().info("[Boss] " + boss.definition.id() + " usa " + ability.id());
        }
        for (AbilityEffect effect : ability.effects()) {
            try {
                executeEffect(boss, entity, ability, effect, target);
            } catch (Exception e) {
                plugin.getLogger().warning("Error en habilidad " + ability.id() + ": " + e.getMessage());
            }
        }
    }

    private void executeEffect(ActiveBoss boss, LivingEntity entity, BossAbilityDefinition ability,
                               AbilityEffect effect, Player target) {
        Location center = entity.getLocation();
        double power = attackDamage(entity);
        switch (effect.type()) {
            case "AOE_DAMAGE" -> {
                double radius = effect.d("radius", 5);
                double multiplier = effect.d("damage_multiplier", 1.0);
                for (Player player : playersInRadius(center, radius)) {
                    damagePlayer(player, power * multiplier, entity);
                }
                particle(center, radius);
            }
            case "KNOCKBACK" -> {
                double radius = effect.d("radius", 5);
                double force = effect.d("force", 1.0);
                for (Player player : playersInRadius(center, radius)) {
                    Vector direction = player.getLocation().toVector().subtract(center.toVector());
                    if (direction.lengthSquared() > 0.01) {
                        direction.normalize();
                    }
                    player.setVelocity(player.getVelocity().add(direction.multiply(force).setY(0.4)));
                }
            }
            case "DASH" -> {
                Player nearest = target != null ? target : nearestPlayer(center, effect.d("max_distance", 15));
                if (nearest != null) {
                    Vector direction = nearest.getLocation().toVector().subtract(center.toVector());
                    if (direction.lengthSquared() > 0.01) {
                        direction.normalize();
                    }
                    entity.setVelocity(direction.multiply(effect.d("speed_multiplier", 2.0)).setY(0.35));
                    if (entity.getLocation().distanceSquared(nearest.getLocation())
                            <= Math.pow(effect.d("max_distance", 15), 2)) {
                        damagePlayer(nearest, power * effect.d("damage_multiplier", 1.0), entity);
                        if (effect.d("knockback", 0) > 0) {
                            nearest.setVelocity(direction.multiply(effect.d("knockback", 0)).setY(0.35));
                        }
                    }
                }
            }
            case "SPLASH" -> {
                Location victim = boss.lastVictim != null ? boss.lastVictim : center;
                double radius = effect.d("radius", 3);
                double percent = effect.d("damage_percent", 50) / 100.0;
                for (Player player : playersInRadius(victim, radius)) {
                    damagePlayer(player, power * percent, entity);
                }
            }
            case "POTION" -> {
                double radius = effect.d("radius", 6);
                PotionEffectType type = potion(effect.s("effect", "SLOWNESS"));
                if (type == null) {
                    return;
                }
                int duration = Math.max(1, effect.i("duration_seconds", 5)) * 20;
                int amplifier = Math.max(0, effect.i("level", 1) - 1);
                String targetType = effect.s("target", "PLAYERS");
                if ("PLAYERS".equalsIgnoreCase(targetType)) {
                    for (Player player : playersInRadius(center, radius)) {
                        player.addPotionEffect(new PotionEffect(type, duration, amplifier, false, true, true));
                    }
                } else {
                    for (LivingEntity living : livingInRadius(center, radius)) {
                        if (living.equals(entity)) {
                            continue;
                        }
                        living.addPotionEffect(new PotionEffect(type, duration, amplifier, false, true, true));
                    }
                }
                particle(center, radius);
            }
            case "PULL" -> {
                double radius = effect.d("radius", 12);
                double force = effect.d("force", 1.0);
                for (Player player : playersInRadius(center, radius)) {
                    Vector direction = center.toVector().subtract(player.getLocation().toVector());
                    if (direction.lengthSquared() > 0.01) {
                        direction.normalize();
                    }
                    player.setVelocity(player.getVelocity().add(direction.multiply(force).setY(0.25)));
                }
            }
            case "SUMMON" -> {
                int min = Math.max(1, effect.i("min", 2));
                int max = Math.max(min, effect.i("max", 4));
                int count = min + random.nextInt(max - min + 1);
                EntityType type;
                try {
                    type = EntityType.valueOf(effect.s("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    type = EntityType.ZOMBIE;
                }
                for (int i = 0; i < count && boss.minions.size() < Math.max(1, effect.i("max_minions", 30)); i++) {
                    Location spawn = center.clone().add(random.nextDouble() * 6 - 3, 0,
                            random.nextDouble() * 6 - 3);
                    Entity summoned = entity.getWorld().spawnEntity(spawn, type);
                    if (!(summoned instanceof LivingEntity minion)) {
                        continue;
                    }
                    mobTiers.clear(minion);
                    loot.clear(minion);
                    minion.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.BYTE, (byte) 1);
                    if (effect.b("elite", false)) {
                        AttributeInstance hp = minion.getAttribute(Attribute.MAX_HEALTH);
                        if (hp != null) {
                            hp.setBaseValue(hp.getBaseValue() * 2);
                            minion.setHealth(hp.getValue());
                        }
                        AttributeInstance dmg = minion.getAttribute(Attribute.ATTACK_DAMAGE);
                        if (dmg != null) {
                            dmg.setBaseValue(dmg.getBaseValue() * 1.5);
                        }
                        minion.customName(Text.c(messages.getRaw("boss.minion-name",
                                "boss", boss.definition.name())));
                        minion.setCustomNameVisible(true);
                    }
                    boss.minions.add(minion.getUniqueId());
                }
            }
            case "METEOR" -> {
                int count = Math.max(1, effect.i("count", 3));
                double radius = effect.d("radius", 6);
                int fallTicks = Math.max(1, effect.i("fall_ticks", 20));
                double multiplier = effect.d("damage_multiplier", 1.0);
                double damageRadius = effect.d("damage_radius", 3);
                for (int i = 0; i < count; i++) {
                    double x = center.getX() + (random.nextDouble() * 2 - 1) * radius;
                    double z = center.getZ() + (random.nextDouble() * 2 - 1) * radius;
                    Location impact = new Location(center.getWorld(), x, center.getY(), z);
                    center.getWorld().spawnParticle(Particle.FLAME, impact.clone().add(0, 8, 0), 10, 0.2, 0.2, 0.2, 0.01);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!boss.active) {
                            return;
                        }
                        impact.getWorld().spawnParticle(Particle.FLAME, impact, 40, 0.6, 0.6, 0.6, 0.05);
                        for (Player player : playersInRadius(impact, damageRadius)) {
                            damagePlayer(player, power * multiplier, entity);
                        }
                    }, fallTicks);
                }
            }
            case "SELF_HEAL" -> {
                AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
                double max = health == null ? entity.getHealth() : health.getValue();
                double amount;
                if (effect.params().containsKey("percent_of_damage")) {
                    amount = boss.lastDamageDealt * effect.d("percent_of_damage", 10) / 100.0;
                } else {
                    amount = max * effect.d("percent_max_health", 10) / 100.0;
                }
                entity.setHealth(Math.min(max, entity.getHealth() + Math.max(0, amount)));
            }
            case "TELEPORT" -> {
                Player player = target != null ? target : nearestPlayer(center, effect.d("max_distance", 20));
                if (player != null) {
                    Location destination;
                    if ("RANDOM".equalsIgnoreCase(effect.s("mode", "BEHIND"))) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double distance = 2 + random.nextDouble() * 6;
                        destination = player.getLocation().clone()
                                .add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                    } else {
                        destination = player.getLocation().clone()
                                .subtract(player.getLocation().getDirection().multiply(2));
                    }
                    destination.setY(player.getLocation().getY());
                    entity.teleport(destination);
                    particle(entity.getLocation(), 1);
                }
            }
            case "DEBUFF" -> {
                PotionEffectType type = potion(effect.s("effect", "BLINDNESS"));
                if (type == null) {
                    return;
                }
                int duration = Math.max(1, effect.i("duration_seconds", 3)) * 20;
                int amplifier = Math.max(0, effect.i("level", 1) - 1);
                double radius = effect.d("radius", 6);
                for (Player player : playersInRadius(center, radius)) {
                    player.addPotionEffect(new PotionEffect(type, duration, amplifier, false, true, true));
                }
            }
            case "PROJECTILE" -> {
                int count = Math.max(1, effect.i("count", 3));
                double spread = effect.d("spread", 0.15);
                double multiplier = effect.d("damage_multiplier", 1.0);
                List<Player> targets = playersInRadius(center, effect.d("radius", 24));
                if (targets.isEmpty()) {
                    return;
                }
                for (int i = 0; i < count; i++) {
                    Player player = targets.get(random.nextInt(targets.size()));
                    Vector direction = player.getEyeLocation().toVector()
                            .subtract(entity.getEyeLocation().toVector()).normalize();
                    direction.add(new Vector(
                            (random.nextDouble() * 2 - 1) * spread,
                            (random.nextDouble() * 2 - 1) * spread,
                            (random.nextDouble() * 2 - 1) * spread));
                    Arrow arrow = entity.getWorld().spawnArrow(entity.getEyeLocation(), direction, 1.6f, 0f);
                    arrow.setShooter(entity);
                    arrow.setDamage(power * multiplier);
                }
            }
            case "BUFF" -> {
                if (effect.params().containsKey("speed_percent")) {
                    buff(entity, "speed", Attribute.MOVEMENT_SPEED, effect.d("speed_percent", 20) / 100.0);
                }
                if (effect.params().containsKey("damage_percent")) {
                    buff(entity, "damage", Attribute.ATTACK_DAMAGE, effect.d("damage_percent", 20) / 100.0);
                }
                if (effect.params().containsKey("attack_speed_percent")) {
                    buff(entity, "attack_speed", Attribute.ATTACK_SPEED, effect.d("attack_speed_percent", 20) / 100.0);
                }
            }
            case "CLEAR_DEBUFFS" -> {
                for (PotionEffect active : new ArrayList<>(entity.getActivePotionEffects())) {
                    entity.removePotionEffect(active.getType());
                }
            }
            case "DEATH_EXPLOSION" -> {
                double radius = effect.d("radius", 6);
                double multiplier = effect.d("damage_multiplier", 1.5);
                for (Player player : playersInRadius(center, radius)) {
                    damagePlayer(player, power * multiplier, entity);
                }
                entity.getWorld().spawnParticle(Particle.EXPLOSION, center, 1, 0, 0, 0, 0);
            }
            case "SHIELD" -> {
                AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
                double max = health == null ? entity.getHealth() : health.getValue();
                entity.setAbsorptionAmount(max * effect.d("absorption_percent", 20) / 100.0);
            }
            case "REFLECT" -> {
                Player attacker = target != null ? target : Bukkit.getPlayer(boss.lastDamager);
                if (attacker != null) {
                    damagePlayer(attacker, power * effect.d("percent", 20) / 100.0, entity);
                }
            }
            case "LIGHTNING" -> {
                int maxTargets = Math.max(1, effect.i("targets", 3));
                double multiplier = effect.d("damage_multiplier", 1.0);
                List<Player> targets = playersInRadius(center, effect.d("radius", 20));
                for (int i = 0; i < Math.min(maxTargets, targets.size()); i++) {
                    Player player = targets.get(i);
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    damagePlayer(player, power * multiplier, entity);
                }
            }
            case "FREEZE" -> {
                int duration = Math.max(1, effect.i("duration_seconds", 3)) * 20;
                double radius = effect.d("radius", 6);
                for (Player player : playersInRadius(center, radius)) {
                    player.setFreezeTicks(Math.max(player.getFreezeTicks(), duration));
                }
            }
            default -> {
            }
        }
    }

    private void buff(LivingEntity entity, String name, Attribute attribute, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "boss_buff_" + name);
        for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
            if (modifier.getKey().equals(key)) {
                instance.removeModifier(modifier);
            }
        }
        instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
    }

    private void damagePlayer(Player player, double amount, LivingEntity source) {
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR || player.isDead()) {
            return;
        }
        player.damage(Math.max(0.1, amount), source);
    }

    private void particle(Location center, double radius) {
        center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 0.5, 0),
                Math.max(8, (int) (radius * 6)), radius / 2, 0.3, radius / 2, 0.02);
    }

    private PotionEffectType potion(String name) {
        if (name == null) {
            return null;
        }
        PotionEffectType type = PotionEffectType.getByName(name.toUpperCase(Locale.ROOT));
        if (type != null) {
            return type;
        }
        return Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
    }

    public void onBossDeath(LivingEntity entity, EntityDeathEvent event) {
        ActiveBoss boss = active(entity);
        if (boss == null) {
            return;
        }
        // Las habilidades ON_DEATH deben ejecutarse antes de desactivar el jefe
        // y sin descartarlas por entity.isDead().
        for (BossAbilityDefinition ability : boss.abilities) {
            if (!"ON_DEATH".equals(ability.trigger())) {
                continue;
            }
            if (ability.chance() < 100 && random.nextInt(100) >= ability.chance()) {
                continue;
            }
            for (AbilityEffect effect : ability.effects()) {
                try {
                    executeEffect(boss, entity, ability, effect, null);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error en habilidad de muerte " + ability.id() + ": " + e.getMessage());
                }
            }
        }

        ConfigurationSection drops = config.get("drops.yml").getConfigurationSection("drops.boss");
        boolean clearVanilla = drops == null || drops.getBoolean("clear-vanilla", true);
        if (clearVanilla) {
            event.getDrops().clear();
        }

        boolean essence = true;
        double essenceChance = 1.0;
        if (drops == null || drops.getBoolean("essence.use-tier-chance", true)) {
            essenceChance = tiers.essenceChance(boss.definition.tier());
        }
        if (random.nextDouble() > essenceChance) {
            essence = false;
        }
        if (essence) {
            int amount = 1;
            ConfigurationSection override = config.get("drops.yml")
                    .getConfigurationSection("drops.overrides." + boss.definition.id() + ".essence");
            if (override != null) {
                amount = Math.max(1, override.getInt("amount", 1));
            }
            ItemStack essenceItem = items.legendaryEssence();
            essenceItem.setAmount(amount);
            drop(event, essenceItem);
        }

        boolean customEnabled = drops == null || drops.getBoolean("custom-loot.enabled", true);
        if (customEnabled) {
            int min = drops == null ? 1 : Math.max(1, drops.getInt("custom-loot.min", 1));
            int max = drops == null ? 3 : Math.max(min, drops.getInt("custom-loot.max", 3));
            int count = min + random.nextInt(max - min + 1);
            int lootTier = boss.definition.tier();
            for (int i = 0; i < count; i++) {
                drop(event, loot.generate(lootTier));
            }
        }

        int base = drops == null ? 50 : drops.getInt("xp.base", 50);
        int perTier = drops == null ? 50 : drops.getInt("xp.per-tier", 50);
        int xp = boss.definition.xp() > 0 ? boss.definition.xp() : base + perTier * boss.definition.tier();
        ConfigurationSection xpOverride = config.get("drops.yml")
                .getConfigurationSection("drops.overrides." + boss.definition.id() + ".xp");
        if (xpOverride != null) {
            xp = xpOverride.getInt("base", xp);
        }
        event.setDroppedExp(Math.max(0, xp));

        int despawnSeconds = drops == null ? 300 : drops.getInt("despawn-seconds", 300);
        if (despawnSeconds > 0 && despawnSeconds < 300) {
            final Location dropLocation = entity.getLocation().clone();
            final int ticks = despawnSeconds * 20;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (dropLocation.getWorld() == null) {
                    return;
                }
                for (Entity nearby : dropLocation.getWorld().getNearbyEntities(dropLocation, 5, 5, 5)) {
                    if (!(nearby instanceof Item itemEntity)) {
                        continue;
                    }
                    ItemStack stack = itemEntity.getItemStack();
                    if (!stack.hasItemMeta()) {
                        continue;
                    }
                    ItemMeta meta = stack.getItemMeta();
                    if (meta == null || !meta.getPersistentDataContainer()
                            .has(Keys.ITEM_TYPE, PersistentDataType.STRING)) {
                        continue;
                    }
                    itemEntity.setTicksLived(Math.max(1, 6000 - ticks));
                }
            }, 2L);
        }

        Player killer = entity.getKiller();
        if (killer == null && boss.lastDamager != null) {
            killer = Bukkit.getPlayer(boss.lastDamager);
        }

        List<Player> credited = creditParticipants(boss, entity.getLocation());
        for (Player player : credited) {
            boolean unlocked = progression.markKill(player.getUniqueId(), boss.definition.tier(),
                    boss.definition.id(), bossIdsOfTier(boss.definition.tier()));
            messages.send(player, "boss.killed", "boss", boss.definition.name(),
                    "tier_color", tiers.color(boss.definition.tier()));
            if (unlocked) {
                messages.send(player, "progression.unlocked", "tier", String.valueOf(boss.definition.tier() + 1));
                player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
            }
            stats.onKill(player, true);
        }

        if (killer != null) {
            broadcastNear(entity.getLocation(), messages.get("boss.killed-broadcast",
                    "boss", boss.definition.name(), "player", killer.getName(),
                    "tier_color", tiers.color(boss.definition.tier())));
        }

        if (config.get("config.yml").getBoolean("logging.kills", true)) {
            plugin.getLogger().info("Jefe derrotado: " + boss.definition.id() + " T" + boss.definition.tier()
                    + (killer == null ? "" : " por " + killer.getName())
                    + " | participantes: " + credited.size());
        }

        progression.save();
        stats.save();
        remove(boss, false);
    }

    private void drop(EntityDeathEvent event, ItemStack item) {
        if (item == null) {
            return;
        }
        event.getDrops().add(item);
    }

    private List<Player> creditParticipants(ActiveBoss boss, Location deathLocation) {
        List<Player> credited = new ArrayList<>();
        int minDamage = config.getInt("progression.yml", "progression.credit.min-damage", 1);
        double maxDistance = config.getDouble("progression.yml", "progression.credit.max-distance", 30);
        for (Map.Entry<UUID, Double> entry : boss.damage.entrySet()) {
            if (entry.getValue() < minDamage) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!player.getWorld().equals(deathLocation.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(deathLocation) > maxDistance * maxDistance) {
                continue;
            }
            credited.add(player);
        }
        return credited;
    }

    public void onPlayerDeath(Player player) {
        boolean reset = config.get("progression.yml").getBoolean("progression.reset-on-player-death.boss-health", true);
        if (!reset) {
            return;
        }
        for (ActiveBoss boss : active.values()) {
            if (!boss.damage.containsKey(player.getUniqueId())) {
                continue;
            }
            LivingEntity entity = boss.entity();
            if (entity == null) {
                continue;
            }
            AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                entity.setHealth(health.getValue());
            }
            entity.teleport(boss.spawnLocation);
            entity.setAbsorptionAmount(0);
            for (PotionEffect effect : new ArrayList<>(entity.getActivePotionEffects())) {
                entity.removePotionEffect(effect.getType());
            }
            removeMinions(boss);
            boss.lastRun.clear();
            boss.thresholdsFired.clear();
            boss.noPlayersSince = 0;
            boss.damage.remove(player.getUniqueId());
            for (Player nearby : playersInRadius(entity.getLocation(), 30)) {
                messages.send(nearby, "boss.reset-on-death", "boss", boss.definition.name(),
                        "tier_color", tiers.color(boss.definition.tier()));
            }
        }
    }

    public void remove(ActiveBoss boss, boolean removeEntity) {
        if (boss == null || !boss.active) {
            return;
        }
        boss.active = false;
        active.remove(boss.entityId);
        if (boss.bar != null) {
            boss.bar.removeAll();
            boss.bar.setVisible(false);
        }
        removeMinions(boss);
        LivingEntity entity = boss.entity();
        if (removeEntity && entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private void removeMinions(ActiveBoss boss) {
        for (UUID uuid : new ArrayList<>(boss.minions)) {
            Entity minion = Bukkit.getEntity(uuid);
            if (minion != null && minion.isValid()) {
                minion.remove();
            }
        }
        boss.minions.clear();
    }

    public void despawnAll() {
        for (ActiveBoss boss : new ArrayList<>(active.values())) {
            remove(boss, true);
        }
    }

    public List<Player> playersInRadius(Location center, double radius) {
        List<Player> players = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return players;
        }
        double squared = radius * radius;
        for (Player player : center.getWorld().getPlayers()) {
            if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) <= squared) {
                players.add(player);
            }
        }
        return players;
    }

    public List<LivingEntity> livingInRadius(Location center, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return list;
        }
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living) {
                list.add(living);
            }
        }
        return list;
    }

    public Player nearestPlayer(Location center, double radius) {
        Player nearest = null;
        double best = radius * radius;
        for (Player player : playersInRadius(center, radius)) {
            double distance = player.getLocation().distanceSquared(center);
            if (distance <= best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    public double attackDamage(LivingEntity entity) {
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        return damage == null ? 5 : damage.getValue();
    }

    public double maxHealth(LivingEntity entity) {
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        return health == null ? entity.getHealth() : health.getValue();
    }

    public void broadcastNear(Location location, String message) {
        Component component = Text.c(message);
        if (location == null || location.getWorld() == null) {
            Bukkit.getServer().sendMessage(component);
            return;
        }
        for (Player player : location.getWorld().getPlayers()) {
            player.sendMessage(component);
        }
    }

    public int tierOf(ActiveBoss boss) {
        return boss.definition.tier();
    }

    /**
     * Jefe activo.
     */
    public final class ActiveBoss {
        private final UUID entityId;
        private final BossDefinition definition;
        private final Location spawnLocation;
        private final long spawnedAt;
        private BossBar bar;
        private boolean active = true;
        private final Map<UUID, Double> damage = new LinkedHashMap<>();
        private final Map<String, Long> lastRun = new HashMap<>();
        private final Set<String> thresholdsFired = new HashSet<>();
        private final Set<UUID> minions = new LinkedHashSet<>();
        private final Set<Integer> warnedSeconds = new HashSet<>();
        private final List<BossAbilityDefinition> abilities = new ArrayList<>();
        private long noPlayersSince;
        private UUID lastDamager;
        private Location lastVictim;
        private double lastDamageDealt;

        private ActiveBoss(UUID entityId, BossDefinition definition, Location spawnLocation, long spawnedAt) {
            this.entityId = entityId;
            this.definition = definition;
            this.spawnLocation = spawnLocation;
            this.spawnedAt = spawnedAt;
            for (String abilityId : definition.abilities()) {
                BossAbilityDefinition ability = abilityCatalog.get(abilityId.toLowerCase(Locale.ROOT));
                if (ability == null) {
                    plugin.getLogger().warning("Habilidad de jefe desconocida: " + abilityId
                            + " (jefe " + definition.id() + ")");
                    continue;
                }
                if (!ability.archetypes().isEmpty()
                        && !ability.archetypes().contains(definition.archetype().toUpperCase(Locale.ROOT))) {
                    plugin.getLogger().warning("La habilidad " + abilityId + " no pertenece al arquetipo "
                            + definition.archetype() + " (jefe " + definition.id() + ")");
                }
                abilities.add(ability);
            }
        }

        public LivingEntity entity() {
            Entity entity = Bukkit.getEntity(entityId);
            return entity instanceof LivingEntity living && living.isValid() ? living : null;
        }

        public BossDefinition definition() {
            return definition;
        }

        public Location spawnLocation() {
            return spawnLocation.clone();
        }

        public long spawnedAt() {
            return spawnedAt;
        }

        public Map<UUID, Double> damage() {
            return damage;
        }

        public boolean isActive() {
            return active;
        }

        public BossBar bar() {
            return bar;
        }
    }
}

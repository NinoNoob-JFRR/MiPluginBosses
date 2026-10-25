package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.config.Messages;
import com.tunombre.bossplugin.model.BossDefinition;
import com.tunombre.bossplugin.model.EggData;
import com.tunombre.bossplugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Huevos de jefe: aparicion natural, apertura, desaparicion y proteccion.
 */
public class EggManager {

    private final Main plugin;
    private final ConfigManager config;
    private final Messages messages;
    private final BossManager bosses;
    private final ProgressionManager progression;
    private final ItemFactory items;
    private final TierManager tiers;
    private final Random random = new Random();

    private final Map<String, EggData> eggs = new LinkedHashMap<>();
    private boolean enabled;
    private int maxActive;
    private Material eggMaterial = Material.DRAGON_EGG;
    private Material pedestalMaterial = Material.BEDROCK;
    private List<String> worlds = new ArrayList<>();
    private boolean automatic;
    private int intervalMinutes;
    private int radius;
    private int minDistance;
    private int spawnAttempts = 40;
    private boolean requireSurface;
    private boolean avoidLiquid;
    private int despawnMinutes;
    private boolean announcement;
    private boolean indestructible;
    private boolean particlesEnabled;
    private String particleType;
    private double particleRadius;
    private boolean compassSameWorld;
    private int compassMaxDistance;
    private String tierWeightMode = "linear-descending";
    private final Map<Integer, Double> customTierWeights = new LinkedHashMap<>();
    private long nextNaturalSpawn;

    public EggManager(Main plugin, ConfigManager config, Messages messages, BossManager bosses,
                      ProgressionManager progression, ItemFactory items, TierManager tiers) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.bosses = bosses;
        this.progression = progression;
        this.items = items;
        this.tiers = tiers;
        reload();
        load();
    }

    public void reload() {
        enabled = config.get("eggs.yml").getBoolean("eggs.enabled", true);
        maxActive = Math.max(1, config.get("eggs.yml").getInt("eggs.max-active", 10));
        Material egg = Material.matchMaterial(config.get("eggs.yml").getString("eggs.block", "DRAGON_EGG"));
        if (egg != null) {
            eggMaterial = egg;
        }
        Material pedestal = Material.matchMaterial(config.get("eggs.yml").getString("eggs.pedestal", "BEDROCK"));
        if (pedestal != null) {
            pedestalMaterial = pedestal;
        }
        worlds = new ArrayList<>(config.get("eggs.yml").getStringList("eggs.worlds"));
        if (worlds.isEmpty()) {
            worlds.add("world");
        }
        automatic = config.get("eggs.yml").getBoolean("eggs.spawn.automatic", true);
        intervalMinutes = Math.max(1, config.get("eggs.yml").getInt("eggs.spawn.interval-minutes", 10));
        radius = Math.max(32, config.get("eggs.yml").getInt("eggs.spawn.radius", 750));
        minDistance = Math.max(16, config.get("eggs.yml").getInt("eggs.spawn.min-distance-between-eggs", 100));
        spawnAttempts = Math.max(1, config.get("eggs.yml").getInt("eggs.spawn.attempts", 40));
        requireSurface = config.get("eggs.yml").getBoolean("eggs.spawn.require-surface", true);
        avoidLiquid = config.get("eggs.yml").getBoolean("eggs.spawn.avoid-liquid", true);
        despawnMinutes = Math.max(1, config.get("eggs.yml").getInt("eggs.despawn.minutes", 45));
        announcement = config.get("eggs.yml").getBoolean("eggs.open.announcement", true);
        indestructible = config.get("eggs.yml").getBoolean("eggs.protection.indestructible", true);
        particlesEnabled = config.get("eggs.yml").getBoolean("eggs.particles.enabled", true);
        particleType = config.get("eggs.yml").getString("eggs.particles.type", "END_ROD");
        particleRadius = config.get("eggs.yml").getDouble("eggs.particles.radius", 1.0);
        compassSameWorld = config.get("eggs.yml").getBoolean("eggs.compass.same-world", true);
        compassMaxDistance = config.get("eggs.yml").getInt("eggs.compass.max-distance", -1);
        tierWeightMode = config.get("eggs.yml").getString("eggs.tier-weights.mode", "linear-descending");
        if (tierWeightMode == null) {
            tierWeightMode = "linear-descending";
        }
        tierWeightMode = tierWeightMode.toLowerCase(Locale.ROOT);
        customTierWeights.clear();
        ConfigurationSection customWeights = config.get("eggs.yml")
                .getConfigurationSection("eggs.tier-weights.custom");
        if (customWeights != null) {
            for (String key : customWeights.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    double weight = customWeights.getDouble(key, 0);
                    if (tier >= 1 && weight > 0) {
                        customTierWeights.put(tier, weight);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (nextNaturalSpawn <= 0) {
            nextNaturalSpawn = System.currentTimeMillis() + intervalMinutes * 60_000L;
        }
    }

    public void load() {
        eggs.clear();
        FileConfiguration data = config.get("data/eggs.yml");
        List<Map<?, ?>> list = data.getMapList("eggs");
        for (Map<?, ?> raw : list) {
            try {
                String world = String.valueOf(raw.get("world"));
                int x = number(raw.get("x"));
                int y = number(raw.get("y"));
                int z = number(raw.get("z"));
                long spawnedAt = longNumber(raw.get("spawned-at"), System.currentTimeMillis());
                long despawnAt = longNumber(raw.get("despawn-at"), spawnedAt + despawnMinutes * 60_000L);
                int forced = number(raw.get("forced-tier"));
                String forcedBoss = raw.get("forced-boss") == null ? "" : String.valueOf(raw.get("forced-boss"));
                EggData eggData = new EggData(world, x, y, z, spawnedAt, despawnAt, forced, forcedBoss);
                eggs.put(key(world, x, y, z), eggData);
            } catch (Exception e) {
                plugin.getLogger().warning("Huevo invalido en data/eggs.yml: " + e.getMessage());
            }
        }
        plugin.getLogger().info("Huevos de jefe cargados: " + eggs.size());
    }

    public void save() {
        FileConfiguration data = config.get("data/eggs.yml");
        List<Map<String, Object>> list = new ArrayList<>();
        for (EggData egg : eggs.values()) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("world", egg.worldName());
            raw.put("x", egg.x());
            raw.put("y", egg.y());
            raw.put("z", egg.z());
            raw.put("spawned-at", egg.spawnedAt());
            raw.put("despawn-at", egg.despawnAt());
            raw.put("forced-tier", egg.forcedTier());
            if (!egg.forcedBoss().isBlank()) {
                raw.put("forced-boss", egg.forcedBoss());
            }
            list.add(raw);
        }
        data.set("eggs", list);
        config.save("data/eggs.yml");
    }

    public void tick() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();

        for (EggData egg : new ArrayList<>(eggs.values())) {
            if (egg.isExpired(now)) {
                remove(egg);
                broadcast(messages.get("egg.despawned"));
            }
        }

        if (particlesEnabled) {
            spawnParticles();
        }

        if (automatic && now >= nextNaturalSpawn) {
            nextNaturalSpawn = now + intervalMinutes * 60_000L;
            if (eggs.size() < maxActive) {
                spawnNatural();
            }
        }
    }

    public int activeCount() {
        return eggs.size();
    }

    public int maxActive() {
        return maxActive;
    }

    public List<EggData> eggs() {
        return new ArrayList<>(eggs.values());
    }

    /**
     * Genera un huevo en una ubicacion aleatoria de un mundo configurado.
     */
    public EggData spawnNatural() {
        if (eggs.size() >= maxActive) {
            return null;
        }
        List<World> candidates = new ArrayList<>();
        for (String name : worlds) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                candidates.add(world);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        World world = candidates.get(random.nextInt(candidates.size()));
        Location spawn = world.getSpawnLocation();
        EggData egg = null;
        // Primera pasada: solo chunks ya generados, para no forzar generacion
        // de terreno durante el tick del servidor.
        for (int attempt = 0; attempt < spawnAttempts && egg == null; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 100 + random.nextDouble() * Math.max(1, radius - 100);
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!world.isChunkGenerated(x >> 4, z >> 4)) {
                continue;
            }
            egg = tryPlace(world, x, z);
        }
        // Si no habia ningun chunk generado, se genera un unico chunk nuevo
        // de forma asincrona para no bloquear el hilo principal.
        if (egg == null) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 100 + random.nextDouble() * Math.max(1, radius - 100);
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        EggData placed = tryPlace(world, x, z);
                        if (placed != null) {
                            broadcast(messages.get("egg.spawned"));
                            if (config.get("config.yml").getBoolean("logging.admin-actions", true)) {
                                plugin.getLogger().info("Huevo natural en " + world.getName() + " "
                                        + placed.x() + "," + placed.y() + "," + placed.z());
                            }
                        }
                    }));
        }
        if (egg != null) {
            broadcast(messages.get("egg.spawned"));
            if (config.get("config.yml").getBoolean("logging.admin-actions", true)) {
                plugin.getLogger().info("Huevo natural en " + world.getName() + " "
                        + egg.x() + "," + egg.y() + "," + egg.z());
            }
        }
        return egg;
    }

    private EggData tryPlace(World world, int x, int z) {
        Location surface = surface(world, x, z);
        if (surface == null || tooClose(surface)) {
            return null;
        }
        return place(world, surface.getBlockX(), surface.getBlockY(), surface.getBlockZ(), 0);
    }

    /**
     * Genera huevos en una ubicacion aleatoria cerca del jugador.
     */
    public List<EggData> spawnNear(Player player, int forcedTier, int amount) {
        return spawnNear(player, forcedTier, amount, "");
    }

    public List<EggData> spawnNear(Player player, int forcedTier, int amount, String forcedBoss) {
        return spawnAround(player, forcedTier, amount, forcedBoss, 20);
    }

    /**
     * Genera huevos lo mas cerca posible de la ubicacion actual del jugador.
     */
    public List<EggData> spawnHere(Player player, int forcedTier, int amount, String forcedBoss) {
        return spawnAround(player, forcedTier, amount, forcedBoss, 3);
    }

    private List<EggData> spawnAround(Player player, int forcedTier, int amount,
                                      String forcedBoss, int radius) {
        List<EggData> spawned = new ArrayList<>();
        int count = Math.max(1, amount);
        for (int i = 0; i < count; i++) {
            if (eggs.size() >= maxActive) {
                messages.send(player, "egg.max-reached", "max", String.valueOf(maxActive));
                break;
            }
            Location surface = surfaceNear(player.getLocation(), radius);
            if (surface == null) {
                continue;
            }
            EggData egg = place(surface.getWorld(), surface.getBlockX(), surface.getBlockY(),
                    surface.getBlockZ(), forcedTier, forcedBoss);
            if (egg != null) {
                spawned.add(egg);
            }
        }
        save();
        return spawned;
    }

    /**
     * Genera huevos en unas coordenadas concretas. La Y indicada es la del
     * bloque donde debe aparecer el huevo; el pedestal se coloca debajo.
     */
    public List<EggData> spawnAt(Player player, int forcedTier, int amount,
                                 String forcedBoss, int x, int y, int z) {
        List<EggData> spawned = new ArrayList<>();
        World world = player.getWorld();
        int count = Math.max(1, amount);
        for (int i = 0; i < count; i++) {
            if (eggs.size() >= maxActive) {
                messages.send(player, "egg.max-reached", "max", String.valueOf(maxActive));
                break;
            }
            if (y <= world.getMinHeight() || y >= world.getMaxHeight()) {
                break;
            }
            EggData egg = place(world, x, y - 1, z, forcedTier, forcedBoss);
            if (egg != null) {
                spawned.add(egg);
            }
        }
        save();
        return spawned;
    }

    public EggData place(World world, int x, int y, int z, int forcedTier) {
        return place(world, x, y, z, forcedTier, "");
    }

    public EggData place(World world, int x, int y, int z, int forcedTier, String forcedBoss) {
        if (world == null || eggs.size() >= maxActive) {
            return null;
        }
        world.getChunkAt(x >> 4, z >> 4).load();
        Block pedestal = world.getBlockAt(x, y, z);
        Block eggBlock = world.getBlockAt(x, y + 1, z);
        if (avoidLiquid && (pedestal.isLiquid() || eggBlock.isLiquid())) {
            return null;
        }
        if (!eggBlock.getType().isAir() && !eggBlock.isEmpty()) {
            return null;
        }
        pedestal.setType(pedestalMaterial);
        eggBlock.setType(eggMaterial);
        long now = System.currentTimeMillis();
        EggData data = new EggData(world.getName(), x, y + 1, z, now,
                now + despawnMinutes * 60_000L, forcedTier, forcedBoss);
        eggs.put(key(data.worldName(), data.x(), data.y(), data.z()), data);
        save();
        return data;
    }

    public EggData center(Block block) {
        if (block == null || block.getWorld() == null) {
            return null;
        }
        EggData data = eggs.get(key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        if (data != null) {
            return data;
        }
        return eggs.get(key(block.getWorld().getName(), block.getX(), block.getY() + 1, block.getZ()));
    }

    public boolean isEggBlock(Block block) {
        if (block == null) {
            return false;
        }
        EggData data = center(block);
        if (data == null) {
            return false;
        }
        return block.getType() == eggMaterial || block.getType() == pedestalMaterial;
    }

    public boolean isPedestal(Block block) {
        if (block == null) {
            return false;
        }
        EggData data = eggs.get(key(block.getWorld().getName(), block.getX(), block.getY() + 1, block.getZ()));
        return data != null && block.getType() == pedestalMaterial;
    }

    public boolean protect() {
        return indestructible;
    }

    public void open(Player player, EggData egg) {
        if (egg == null || player == null) {
            return;
        }
        BossDefinition definition = egg.forcedBoss().isBlank()
                ? null : bosses.definition(egg.forcedBoss());
        int tier = definition != null ? definition.tier()
                : (egg.forcedTier() > 0 ? egg.forcedTier() : rollTier(player));
        if (definition == null) {
            definition = pickBoss(tier);
        }
        if (definition == null) {
            messages.send(player, "general.config-error", "error", "No hay jefes para el tier " + tier);
            return;
        }
        World world = Bukkit.getWorld(egg.worldName());
        Location location = world == null ? null : new Location(world, egg.x() + 0.5, egg.y(), egg.z() + 0.5);
        remove(egg);
        if (location == null) {
            return;
        }
        BossManager.ActiveBoss boss = bosses.spawn(definition, location, player);
        if (boss == null) {
            return;
        }
        messages.send(player, "egg.opened", "boss", definition.name(),
                "tier", String.valueOf(definition.tier()));
        if (announcement) {
            String message = messages.get("boss.announced",
                    "boss", definition.name(),
                    "tier", String.valueOf(definition.tier()),
                    "tier_color", tiers.color(definition.tier()),
                    "player", player.getName());
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(Text.c(message));
            }
        }
        player.playSound(player.getLocation(), "entity.ender_dragon.growl", 0.8f, 1.2f);
        world.spawnParticle(Particle.EXPLOSION, location, 1, 0, 0, 0, 0);
    }

    public int rollTier(Player player) {
        int max = Math.max(1, Math.min(tiers.maxTier(), progression.maxTier(player.getUniqueId())));
        if ("custom".equals(tierWeightMode) && !customTierWeights.isEmpty()) {
            double total = 0;
            for (int tier = 1; tier <= max; tier++) {
                total += Math.max(0, customTierWeights.getOrDefault(tier, 0.0));
            }
            if (total > 0) {
                double roll = random.nextDouble() * total;
                for (int tier = max; tier >= 1; tier--) {
                    roll -= Math.max(0, customTierWeights.getOrDefault(tier, 0.0));
                    if (roll < 0) {
                        return tier;
                    }
                }
                return 1;
            }
        }
        int total = max * (max + 1) / 2;
        int roll = random.nextInt(total);
        for (int tier = max; tier >= 1; tier--) {
            roll -= tier;
            if (roll < 0) {
                return tier;
            }
        }
        return 1;
    }

    private BossDefinition pickBoss(int tier) {
        List<BossDefinition> list = bosses.bossesOfTier(tier);
        if (list.isEmpty()) {
            for (int t = tier - 1; t >= 1; t--) {
                list = bosses.bossesOfTier(t);
                if (!list.isEmpty()) {
                    break;
                }
            }
        }
        return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    public void remove(EggData egg) {
        if (egg == null) {
            return;
        }
        eggs.remove(key(egg.worldName(), egg.x(), egg.y(), egg.z()));
        World world = Bukkit.getWorld(egg.worldName());
        if (world != null) {
            Block eggBlock = world.getBlockAt(egg.x(), egg.y(), egg.z());
            if (eggBlock.getType() == eggMaterial) {
                eggBlock.setType(Material.AIR);
            }
            Block pedestal = world.getBlockAt(egg.x(), egg.y() - 1, egg.z());
            if (pedestal.getType() == pedestalMaterial) {
                pedestal.setType(Material.AIR);
            }
        }
        save();
    }

    public EggData nearest(Player player) {
        EggData nearest = null;
        double best = Double.MAX_VALUE;
        for (EggData egg : eggs.values()) {
            if (compassSameWorld && !egg.worldName().equals(player.getWorld().getName())) {
                continue;
            }
            Location location = egg.location();
            if (location == null) {
                continue;
            }
            double distance = location.distanceSquared(player.getLocation());
            if (compassMaxDistance > 0 && distance > (double) compassMaxDistance * compassMaxDistance) {
                continue;
            }
            if (distance < best) {
                best = distance;
                nearest = egg;
            }
        }
        return nearest;
    }

    public void useCompass(Player player) {
        EggData egg = nearest(player);
        if (egg == null) {
            messages.send(player, "egg.compass-no-eggs");
            return;
        }
        Location location = egg.location();
        if (location == null) {
            messages.send(player, "egg.compass-no-eggs");
            return;
        }
        player.setCompassTarget(location);
        messages.send(player, "egg.compass-updated");
        player.playSound(player.getLocation(), "block.amethyst_block.chime", 0.7f, 1.6f);
    }

    public void giveCompass(Player player, int amount) {
        ItemStack compass = items.trackingCompass();
        compass.setAmount(Math.max(1, Math.min(64, amount)));
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(compass);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    private void spawnParticles() {
        Particle particle;
        try {
            particle = Particle.valueOf(particleType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            particle = Particle.END_ROD;
        }
        for (EggData egg : eggs.values()) {
            Location location = egg.location();
            if (location == null) {
                continue;
            }
            location.getWorld().spawnParticle(particle, location.clone().add(0.5, 1.0, 0.5),
                    6, particleRadius, particleRadius, particleRadius, 0.01);
        }
    }

    private Location surface(World world, int x, int z) {
        world.getChunkAt(x >> 4, z >> 4).load();
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block ground = world.getBlockAt(x, y, z);
        if (avoidLiquid && ground.isLiquid()) {
            return null;
        }
        if (!ground.getType().isSolid()) {
            return null;
        }
        if (requireSurface && world.getHighestBlockYAt(x, z) != y) {
            return null;
        }
        Block egg = world.getBlockAt(x, y + 1, z);
        Block above = world.getBlockAt(x, y + 2, z);
        if (!egg.getType().isAir() || !above.getType().isAir()) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    private Location surfaceNear(Location origin) {
        return surfaceNear(origin, 20);
    }

    private Location surfaceNear(Location origin, int radius) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        int bound = Math.max(1, radius);
        for (int attempt = 0; attempt < spawnAttempts; attempt++) {
            int x = origin.getBlockX() + random.nextInt(bound * 2 + 1) - bound;
            int z = origin.getBlockZ() + random.nextInt(bound * 2 + 1) - bound;
            Location surface = surface(world, x, z);
            if (surface != null && !tooClose(surface)) {
                return surface;
            }
        }
        return null;
    }

    private boolean tooClose(Location location) {
        for (EggData egg : eggs.values()) {
            Location other = egg.location();
            if (other == null || !other.getWorld().equals(location.getWorld())) {
                continue;
            }
            if (other.distanceSquared(location) < (double) minDistance * minDistance) {
                return true;
            }
        }
        return false;
    }

    private void broadcast(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Text.c(message));
        }
    }

    private String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private static int number(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static long longNumber(Object value, long def) {
        return value instanceof Number n ? n.longValue() : def;
    }
}

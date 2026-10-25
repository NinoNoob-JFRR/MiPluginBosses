package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.model.PlayerProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Progreso individual por jugador.
 */
public class ProgressionManager {

    private final Main plugin;
    private final ConfigManager config;
    private final Map<UUID, PlayerProgress> progress = new HashMap<>();

    public ProgressionManager(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        load();
    }

    public void load() {
        progress.clear();
        FileConfiguration data = config.get("data/progress.yml");
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
                PlayerProgress playerProgress = new PlayerProgress();
                playerProgress.maxTier(section.getInt("max-tier", 1));
                ConfigurationSection killed = section.getConfigurationSection("killed");
                if (killed != null) {
                    for (String tierKey : killed.getKeys(false)) {
                        int tier;
                        try {
                            tier = Integer.parseInt(tierKey);
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        for (String bossId : killed.getStringList(tierKey)) {
                            playerProgress.markKilled(tier, bossId);
                        }
                    }
                }
                progress.put(uuid, playerProgress);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        FileConfiguration data = config.get("data/progress.yml");
        data.set("players", null);
        for (Map.Entry<UUID, PlayerProgress> entry : progress.entrySet()) {
            String base = "players." + entry.getKey() + ".";
            PlayerProgress value = entry.getValue();
            data.set(base + "max-tier", value.maxTier());
            for (Map.Entry<Integer, java.util.Set<String>> killed : value.allKilled().entrySet()) {
                data.set(base + "killed." + killed.getKey(), new ArrayList<>(killed.getValue()));
            }
        }
        config.save("data/progress.yml");
    }

    public PlayerProgress get(UUID uuid) {
        return progress.computeIfAbsent(uuid, id -> new PlayerProgress());
    }

    public int maxTier(UUID uuid) {
        return get(uuid).maxTier();
    }

    public boolean isUnlocked(UUID uuid, int tier) {
        return tier <= get(uuid).maxTier();
    }

    /**
     * Marca un jefe como derrotado. Devuelve true si el jugador desbloqueo un tier nuevo.
     */
    public boolean markKill(UUID uuid, int tier, String bossId, Collection<String> allBossIdsOfTier) {
        PlayerProgress playerProgress = get(uuid);
        if (playerProgress.hasKilled(tier, bossId)) {
            return false;
        }
        playerProgress.markKilled(tier, bossId);
        if (playerProgress.maxTier() == tier
                && tier < 10
                && playerProgress.killedCount(tier) >= allBossIdsOfTier.size()
                && !allBossIdsOfTier.isEmpty()) {
            playerProgress.maxTier(tier + 1);
            return true;
        }
        return false;
    }

    public void unlock(UUID uuid, int tier) {
        PlayerProgress playerProgress = get(uuid);
        if (tier > playerProgress.maxTier()) {
            playerProgress.maxTier(tier);
        }
    }

    public void reset(UUID uuid) {
        get(uuid).reset();
    }

    public void resetTier(UUID uuid, int tier) {
        get(uuid).resetTier(tier);
    }

    public Collection<UUID> knownPlayers() {
        return new HashSet<>(progress.keySet());
    }
}

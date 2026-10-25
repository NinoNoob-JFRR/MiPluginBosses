package com.tunombre.bossplugin.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Progreso individual de un jugador.
 */
public class PlayerProgress {

    private int maxTier = 1;
    private final Map<Integer, Set<String>> killed = new HashMap<>();

    public int maxTier() {
        return maxTier;
    }

    public void maxTier(int value) {
        this.maxTier = Math.max(1, Math.min(10, value));
    }

    public boolean hasKilled(int tier, String bossId) {
        return killed.getOrDefault(tier, Set.of()).contains(bossId);
    }

    public void markKilled(int tier, String bossId) {
        killed.computeIfAbsent(tier, k -> new HashSet<>()).add(bossId);
    }

    public int killedCount(int tier) {
        return killed.getOrDefault(tier, Set.of()).size();
    }

    public Set<String> killed(int tier) {
        return killed.getOrDefault(tier, Set.of());
    }

    public Map<Integer, Set<String>> allKilled() {
        return killed;
    }

    public void reset() {
        maxTier = 1;
        killed.clear();
    }

    public void resetTier(int tier) {
        killed.remove(tier);
        if (maxTier > tier) {
            maxTier = tier;
        }
    }
}

package com.tunombre.bossplugin.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Puntos de Evolution de un jugador.
 */
public class PlayerStats {

    private final Map<String, Integer> points = new LinkedHashMap<>();
    private int spentPoints;

    public int points(String stat) {
        return points.getOrDefault(stat, 0);
    }

    public void points(String stat, int value) {
        if (value <= 0) {
            points.remove(stat);
        } else {
            points.put(stat, value);
        }
    }

    public Map<String, Integer> allPoints() {
        return points;
    }

    public int spentPoints() {
        return spentPoints;
    }

    public void spentPoints(int value) {
        this.spentPoints = Math.max(0, value);
    }

    public int totalPoints() {
        int total = spentPoints;
        for (int value : points.values()) {
            total += value;
        }
        return total;
    }

    public void reset() {
        points.clear();
        spentPoints = 0;
    }
}

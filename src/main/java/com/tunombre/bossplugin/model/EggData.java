package com.tunombre.bossplugin.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Huevo de jefe activo.
 */
public class EggData {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final long spawnedAt;
    private final long despawnAt;
    private final int forcedTier;

    public EggData(String world, int x, int y, int z, long spawnedAt, long despawnAt) {
        this(world, x, y, z, spawnedAt, despawnAt, 0);
    }

    public EggData(String world, int x, int y, int z, long spawnedAt, long despawnAt, int forcedTier) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnedAt = spawnedAt;
        this.despawnAt = despawnAt;
        this.forcedTier = forcedTier;
    }

    public String worldName() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public long spawnedAt() {
        return spawnedAt;
    }

    public long despawnAt() {
        return despawnAt;
    }

    public int forcedTier() {
        return forcedTier;
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public boolean isExpired(long now) {
        return now >= despawnAt;
    }

    public boolean matches(Location loc) {
        return loc != null
                && loc.getWorld() != null
                && loc.getWorld().getName().equals(world)
                && loc.getBlockX() == x
                && loc.getBlockY() == y
                && loc.getBlockZ() == z;
    }

    public boolean isNear(Location loc, double radius) {
        Location own = location();
        return own != null && loc != null && own.getWorld() == loc.getWorld()
                && own.distanceSquared(loc) <= radius * radius;
    }
}

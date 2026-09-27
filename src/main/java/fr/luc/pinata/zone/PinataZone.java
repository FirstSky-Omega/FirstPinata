package fr.luc.pinata.zone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Random;

public final class PinataZone {

    public enum Kind { SINGLE, BOX, POINTS }

    private final String name;
    private final Kind kind;
    private final String worldName;
    private final Location single;
    private final Location min;
    private final Location max;
    private final List<Location> points;
    private final boolean safeSpawn;
    private final int minPlayers;

    private static final Random RANDOM = new Random();

    public PinataZone(String name, Kind kind, String worldName,
                      Location single, Location min, Location max,
                      List<Location> points, boolean safeSpawn, int minPlayers) {
        this.name = name;
        this.kind = kind;
        this.worldName = worldName;
        this.single = single;
        this.min = min;
        this.max = max;
        this.points = points;
        this.safeSpawn = safeSpawn;
        this.minPlayers = minPlayers;
    }

    public String name() { return name; }
    public Kind kind() { return kind; }
    public String worldName() { return worldName; }
    public boolean safeSpawn() { return safeSpawn; }
    public int minPlayers() { return minPlayers; }

    public World world() { return Bukkit.getWorld(worldName); }

    public Location pickLocation() {
        World w = world();
        if (w == null) return null;
        return switch (kind) {
            case SINGLE -> {
                Location loc = single.clone();
                loc.setWorld(w);
                yield loc;
            }
            case POINTS -> {
                Location loc = points.get(RANDOM.nextInt(points.size())).clone();
                loc.setWorld(w);
                yield loc;
            }
            case BOX -> {
                double x = min.getX() + RANDOM.nextDouble() * (max.getX() - min.getX());
                double y = min.getY() + RANDOM.nextDouble() * (max.getY() - min.getY());
                double z = min.getZ() + RANDOM.nextDouble() * (max.getZ() - min.getZ());
                Location loc = new Location(w, x, y, z);
                if (safeSpawn) yield findSafe(loc);
                yield loc;
            }
        };
    }

    private Location findSafe(Location start) {
        World w = start.getWorld();
        if (w == null) return start;
        int y = w.getHighestBlockYAt(start.getBlockX(), start.getBlockZ());
        return new Location(w, start.getX(), y + 1, start.getZ());
    }
}

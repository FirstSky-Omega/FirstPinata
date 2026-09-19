package fr.luc.pinata.zone;

import fr.luc.pinata.PinataPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ZoneManager {

    private final PinataPlugin plugin;
    private final Map<String, PinataZone> zones = new LinkedHashMap<>();
    private YamlConfiguration cfg;
    private File file;

    public ZoneManager(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        zones.clear();
        this.file = new File(plugin.getDataFolder(), "zones.yml");
        if (!file.exists()) plugin.saveResource("zones.yml", false);
        this.cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = cfg.getConfigurationSection("zones");
        if (root == null) return;

        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) continue;
            try {
                PinataZone zone = parse(name, sec);
                if (zone != null) zones.put(name.toLowerCase(Locale.ROOT), zone);
            } catch (Exception ex) {
                plugin.getLogger().warning("Zone '" + name + "' invalide : " + ex.getMessage());
            }
        }
    }

    private PinataZone parse(String name, ConfigurationSection sec) {
        String world = sec.getString("world");
        if (world == null) return null;
        PinataZone.Kind kind = safeEnum(sec.getString("type", "single"));

        boolean safe = sec.getBoolean("safe-spawn", false);
        int minPlayers = sec.getInt("requirements.min-players", 0);

        return switch (kind) {
            case SINGLE -> {
                Location loc = new Location(null,
                        sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
                yield new PinataZone(name, kind, world, loc, null, null, null, safe, minPlayers);
            }
            case BOX -> {
                ConfigurationSection mn = sec.getConfigurationSection("min");
                ConfigurationSection mx = sec.getConfigurationSection("max");
                if (mn == null || mx == null) yield null;
                Location a = new Location(null, mn.getDouble("x"), mn.getDouble("y"), mn.getDouble("z"));
                Location b = new Location(null, mx.getDouble("x"), mx.getDouble("y"), mx.getDouble("z"));
                yield new PinataZone(name, kind, world, null, a, b, null, safe, minPlayers);
            }
            case POINTS -> {
                List<Map<?, ?>> raw = sec.getMapList("points");
                List<Location> pts = new ArrayList<>();
                for (Map<?, ?> m : raw) {
                    pts.add(new Location(null,
                            fr.luc.pinata.util.MapUtil.d(m, "x", 0),
                            fr.luc.pinata.util.MapUtil.d(m, "y", 0),
                            fr.luc.pinata.util.MapUtil.d(m, "z", 0)));
                }
                if (pts.isEmpty()) yield null;
                yield new PinataZone(name, kind, world, null, null, null, pts, safe, minPlayers);
            }
        };
    }

    private static PinataZone.Kind safeEnum(String s) {
        try { return PinataZone.Kind.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return PinataZone.Kind.SINGLE; }
    }

    public Collection<PinataZone> all() { return zones.values(); }

    public PinataZone byName(String name) {
        if (name == null) return null;
        return zones.get(name.toLowerCase(Locale.ROOT));
    }

    /** Enregistre une zone SINGLE créée en jeu et la persiste. */
    public void createSingle(String name, Location loc) throws IOException {
        String path = "zones." + name;
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".type", "single");
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.save(file);
        reload();
    }

    public boolean remove(String name) throws IOException {
        if (!zones.containsKey(name.toLowerCase(Locale.ROOT))) return false;
        cfg.set("zones." + name, null);
        cfg.save(file);
        reload();
        return true;
    }
}

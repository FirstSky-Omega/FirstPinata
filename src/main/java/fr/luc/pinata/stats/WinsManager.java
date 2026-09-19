package fr.luc.pinata.stats;

import fr.luc.pinata.PinataPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class WinsManager {

    private final PinataPlugin plugin;
    private final Map<UUID, Integer> wins = new LinkedHashMap<>();
    private List<Map.Entry<UUID, Integer>> sortedCache = List.of();

    public WinsManager(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        wins.clear();
        File f = new File(plugin.getDataFolder(), "wins.yml");
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        var sec = cfg.getConfigurationSection("wins");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try { wins.put(UUID.fromString(key), sec.getInt(key)); }
            catch (IllegalArgumentException ignored) { }
        }
        rebuildCache();
    }

    public void save() {
        File f = new File(plugin.getDataFolder(), "wins.yml");
        YamlConfiguration cfg = new YamlConfiguration();
        wins.forEach((uuid, count) -> cfg.set("wins." + uuid, count));
        try { cfg.save(f); }
        catch (Exception e) { plugin.getLogger().warning("wins.yml save: " + e.getMessage()); }
    }

    public void increment(UUID winner) {
        wins.merge(winner, 1, Integer::sum);
        rebuildCache();
        save();
    }

    private void rebuildCache() {
        sortedCache = wins.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();
    }

    public String getTopName(int rank) {
        if (rank < 1 || rank > sortedCache.size()) return "-";
        UUID id = sortedCache.get(rank - 1).getKey();
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : "-";
    }

    public int getTopWins(int rank) {
        if (rank < 1 || rank > sortedCache.size()) return 0;
        return sortedCache.get(rank - 1).getValue();
    }
}

package fr.luc.pinata.stats;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.DamageTracker;
import fr.luc.pinata.pinata.PinataInstance;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stats cumulées de hits par type de piñata, persistées en YAML.
 * Clé : typeId (lowercase) → (playerUUID → totalHits).
 */
public class CumulativeStats {

    private final PinataPlugin plugin;
    private final Map<String, Map<UUID, Integer>> data = new ConcurrentHashMap<>();
    private final Map<String, List<Map.Entry<UUID, Integer>>> rankingCache = new ConcurrentHashMap<>();

    public CumulativeStats(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        data.clear();
        rankingCache.clear();
        File f = file();
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        for (String typeId : cfg.getKeys(false)) {
            ConfigurationSection sec = cfg.getConfigurationSection(typeId);
            if (sec == null) continue;
            Map<UUID, Integer> map = new ConcurrentHashMap<>();
            for (String key : sec.getKeys(false)) {
                try { map.put(UUID.fromString(key), sec.getInt(key)); }
                catch (IllegalArgumentException ignored) { }
            }
            data.put(typeId, map);
            rebuildRanking(typeId);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        data.forEach((typeId, map) ->
                map.forEach((uuid, hits) -> cfg.set(typeId + "." + uuid, hits)));
        try { cfg.save(file()); }
        catch (Exception e) { plugin.getLogger().warning("cumulative-stats.yml save: " + e.getMessage()); }
    }

    public void merge(PinataInstance instance) {
        String typeId = instance.type().id().toLowerCase(Locale.ROOT);
        Map<UUID, Integer> map = data.computeIfAbsent(typeId, k -> new ConcurrentHashMap<>());
        for (Map.Entry<UUID, Double> entry : instance.damageTracker().ranking()) {
            int hits = (int) entry.getValue().doubleValue();
            if (hits > 0) {
                map.merge(entry.getKey(), hits, Integer::sum);
            }
        }
        rebuildRanking(typeId);
        save();
    }

    private void rebuildRanking(String typeId) {
        Map<UUID, Integer> map = data.get(typeId);
        if (map == null) {
            rankingCache.remove(typeId);
            return;
        }
        rankingCache.put(typeId, map.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList());
    }

    public List<Map.Entry<UUID, Integer>> ranking(String typeId) {
        return rankingCache.getOrDefault(typeId.toLowerCase(Locale.ROOT), List.of());
    }

    public int getHits(String typeId, UUID player) {
        Map<UUID, Integer> map = data.get(typeId.toLowerCase(Locale.ROOT));
        return map == null ? 0 : map.getOrDefault(player, 0);
    }

    public String topName(String typeId, int rank) {
        List<Map.Entry<UUID, Integer>> rk = ranking(typeId);
        if (rank < 1 || rank > rk.size()) return "-";
        String name = Bukkit.getOfflinePlayer(rk.get(rank - 1).getKey()).getName();
        return name != null ? name : "-";
    }

    public int topHits(String typeId, int rank) {
        List<Map.Entry<UUID, Integer>> rk = ranking(typeId);
        if (rank < 1 || rank > rk.size()) return 0;
        return rk.get(rank - 1).getValue();
    }

    public int playerRank(String typeId, UUID player) {
        List<Map.Entry<UUID, Integer>> rk = ranking(typeId);
        for (int i = 0; i < rk.size(); i++) {
            if (rk.get(i).getKey().equals(player)) return i + 1;
        }
        return -1;
    }

    public int totalHits(String typeId) {
        Map<UUID, Integer> map = data.get(typeId.toLowerCase(Locale.ROOT));
        if (map == null) return 0;
        return map.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int participants(String typeId) {
        Map<UUID, Integer> map = data.get(typeId.toLowerCase(Locale.ROOT));
        return map == null ? 0 : map.size();
    }

    public int playerTotalHits(UUID player) {
        int sum = 0;
        for (Map<UUID, Integer> map : data.values()) {
            sum += map.getOrDefault(player, 0);
        }
        return sum;
    }

    public int playerBestRank(UUID player) {
        int best = Integer.MAX_VALUE;
        for (String typeId : rankingCache.keySet()) {
            int r = playerRank(typeId, player);
            if (r > 0) best = Math.min(best, r);
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private File file() {
        return new File(plugin.getDataFolder(), "cumulative-stats.yml");
    }
}

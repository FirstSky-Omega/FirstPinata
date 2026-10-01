package fr.luc.pinata.integration;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataInstance;
import fr.luc.pinata.stats.CumulativeStats;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Intégration PlaceholderAPI.
 *  - Applique les placeholders PAPI dans messages et actions.
 *  - Enregistre une expansion "pinata" pour exposer nos propres stats.
 *
 * Placeholders exposés (stats cumulées depuis le début, persistées) :
 *   %pinata_active%                      nombre de piñatas actifs
 *   %pinata_types_loaded%                nombre de types configurés
 *   %pinata_top_name_<type>%             nom du top hitter cumulé
 *   %pinata_top_damage_<type>%           hits cumulés du top hitter
 *   %pinata_top_name_<type>_<rank>%      nom du Nème hitter cumulé
 *   %pinata_top_damage_<type>_<rank>%    hits cumulés du Nème hitter
 *   %pinata_player_hits_<type>%          hits cumulés du joueur sur ce type
 *   %pinata_player_rank_<type>%          rang cumulé du joueur sur ce type
 *   %pinata_player_top_rank%             meilleur rang cumulé du joueur
 *   %pinata_player_total_damage%         hits cumulés du joueur tous types
 *   %pinata_health_<type>%               vie actuelle du piñata (live)
 *   %pinata_max_health_<type>%           vie max du piñata (live)
 *   %pinata_health_percent_<type>%       % de vie restant (live)
 *   %pinata_participants_<type>%         participants (cumulé si inactif)
 *   %pinata_total_damage_<type>%         hits totaux (cumulé si inactif)
 *   %pinata_uptime_<type>%              temps écoulé depuis le spawn (live)
 *   %pinata_display_name_<type>%         nom d'affichage du type
 *   %pinata_exists_<type>%              true/false si piñata actif
 */
public class PapiIntegration {

    private final PinataPlugin plugin;
    private final boolean present;
    private PinataExpansion expansion;

    public PapiIntegration(PinataPlugin plugin) {
        this.plugin = plugin;
        Plugin p = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        this.present = p != null && p.isEnabled() && plugin.config().placeholderApiEnabled();
        if (present) {
            plugin.getLogger().info("PlaceholderAPI détecté : intégration activée.");
            if (plugin.config().placeholderApiRegisterExpansion()) {
                try {
                    expansion = new PinataExpansion();
                    if (expansion.register()) {
                        plugin.getLogger().info("Expansion 'pinata' enregistrée.");
                    } else {
                        plugin.getLogger().warning("Expansion PAPI 'pinata' : register() a retourné false.");
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Enregistrement expansion PAPI : " + t.getMessage());
                }
            }
        }
    }

    public boolean isPresent() {
        return present;
    }

    /**
     * Applique les placeholders PAPI dans le texte. Si PAPI absent, renvoie
     * le texte inchangé.
     */
    public String apply(Player player, String text) {
        if (!present || text == null || text.isEmpty()) return text;
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    public String apply(OfflinePlayer player, String text) {
        if (!present || text == null || text.isEmpty()) return text;
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    public void unregister() {
        if (expansion != null) {
            try { expansion.unregister(); } catch (Throwable ignored) { }
            expansion = null;
        }
    }

    // -----------------------------------------------------------------

    private class PinataExpansion extends PlaceholderExpansion {

        @Override public @NotNull String getIdentifier() { return "pinata"; }
        @Override public @NotNull String getAuthor() { return "lucfkann"; }
        @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
        @Override public boolean persist() { return true; }

        @Override
        public String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
            try {
                String result = resolve(player, params);
                plugin.debug("PAPI onRequest('" + params + "') -> " + result);
                return result;
            } catch (Throwable t) {
                plugin.getLogger().warning("PAPI onRequest('" + params + "') exception: " + t.getMessage());
                return null;
            }
        }

        private String resolve(@Nullable OfflinePlayer player, @NotNull String params) {
            if (plugin.pinataManager() == null) return null;
            String p = params.toLowerCase(Locale.ROOT);

            if (p.equals("active")) return String.valueOf(plugin.pinataManager().active());
            if (p.equals("types_loaded")) return String.valueOf(plugin.pinataConfig().types().size());

            CumulativeStats cs = plugin.cumulativeStats();

            // %pinata_top_name_<type>_<rank>% et %pinata_top_name_<type>%
            if (p.startsWith("top_name_")) {
                String rest = p.substring("top_name_".length());
                int lastUnderscore = rest.lastIndexOf('_');
                if (lastUnderscore > 0) {
                    String maybeType = rest.substring(0, lastUnderscore);
                    String maybeRank = rest.substring(lastUnderscore + 1);
                    int rank = parseRank(maybeRank);
                    if (rank > 0) return cs.topName(maybeType, rank);
                }
                return cs.topName(rest, 1);
            }

            // %pinata_top_damage_<type>_<rank>% et %pinata_top_damage_<type>%
            if (p.startsWith("top_damage_")) {
                String rest = p.substring("top_damage_".length());
                int lastUnderscore = rest.lastIndexOf('_');
                if (lastUnderscore > 0) {
                    String maybeType = rest.substring(0, lastUnderscore);
                    String maybeRank = rest.substring(lastUnderscore + 1);
                    int rank = parseRank(maybeRank);
                    if (rank > 0) return String.valueOf(cs.topHits(maybeType, rank));
                }
                return String.valueOf(cs.topHits(rest, 1));
            }

            // %pinata_player_hits_<type>%
            if (p.startsWith("player_hits_") && player != null) {
                String type = p.substring("player_hits_".length());
                return String.valueOf(cs.getHits(type, player.getUniqueId()));
            }

            // %pinata_player_rank_<type>%
            if (p.startsWith("player_rank_") && player != null) {
                String type = p.substring("player_rank_".length());
                int rank = cs.playerRank(type, player.getUniqueId());
                return rank > 0 ? String.valueOf(rank) : "-";
            }

            // %pinata_player_top_rank%
            if (p.equals("player_top_rank") && player != null) {
                int best = cs.playerBestRank(player.getUniqueId());
                return best > 0 ? String.valueOf(best) : "-";
            }

            // %pinata_player_total_damage%
            if (p.equals("player_total_damage") && player != null) {
                return String.valueOf(cs.playerTotalHits(player.getUniqueId()));
            }

            // %pinata_health_percent_<type>% (avant health_ pour éviter le conflit de préfixe)
            if (p.startsWith("health_percent_")) {
                String type = p.substring("health_percent_".length());
                PinataInstance i = findActive(type);
                if (i == null) return "0";
                double max = i.maxHealth();
                if (max <= 0) return "0";
                return String.valueOf((int) (i.health() / max * 100));
            }

            // %pinata_max_health_<type>%
            if (p.startsWith("max_health_")) {
                String type = p.substring("max_health_".length());
                PinataInstance i = findActive(type);
                if (i == null) return "0";
                return String.valueOf((int) i.maxHealth());
            }

            // %pinata_health_<type>%
            if (p.startsWith("health_")) {
                String type = p.substring("health_".length());
                PinataInstance i = findActive(type);
                if (i == null) return "0";
                return String.valueOf((int) i.health());
            }

            // %pinata_participants_<type>%
            if (p.startsWith("participants_")) {
                String type = p.substring("participants_".length());
                PinataInstance i = findActive(type);
                if (i != null) return String.valueOf(i.damageTracker().participants().size());
                return String.valueOf(cs.participants(type));
            }

            // %pinata_total_damage_<type>%
            if (p.startsWith("total_damage_")) {
                String type = p.substring("total_damage_".length());
                PinataInstance i = findActive(type);
                if (i != null) return String.valueOf((int) i.damageTracker().total());
                return String.valueOf(cs.totalHits(type));
            }

            // %pinata_uptime_<type>%
            if (p.startsWith("uptime_")) {
                String type = p.substring("uptime_".length());
                PinataInstance i = findActive(type);
                if (i == null) return "-";
                return formatDuration(i.uptimeMs());
            }

            // %pinata_display_name_<type>%
            if (p.startsWith("display_name_")) {
                String type = p.substring("display_name_".length());
                fr.luc.pinata.pinata.PinataType pt = plugin.pinataConfig().byId(type);
                if (pt == null) return "-";
                return pt.displayName() != null ? pt.displayName() : pt.id();
            }

            // %pinata_exists_<type>%
            if (p.startsWith("exists_")) {
                String type = p.substring("exists_".length());
                return String.valueOf(findActive(type) != null);
            }

            return null;
        }

        private int parseRank(String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
        }

        private String rankedName(PinataInstance instance, int rank) {
            List<Map.Entry<UUID, Double>> rk = instance.damageTracker().ranking();
            if (rank > rk.size()) return "-";
            return safeName(rk.get(rank - 1).getKey());
        }

        private String rankedDamage(PinataInstance instance, int rank) {
            List<Map.Entry<UUID, Double>> rk = instance.damageTracker().ranking();
            if (rank > rk.size()) return "0";
            return String.valueOf((int) rk.get(rank - 1).getValue().doubleValue());
        }

        private String formatDuration(long ms) {
            long totalSec = ms / 1000;
            long m = totalSec / 60;
            long s = totalSec % 60;
            if (m > 0) return m + "m" + (s > 0 ? s + "s" : "");
            return s + "s";
        }

        private PinataInstance findActive(String typeId) {
            for (PinataInstance i : plugin.pinataManager().all()) {
                if (i.type().id().equalsIgnoreCase(typeId)) {
                    plugin.debug("PAPI find('" + typeId + "') -> active");
                    return i;
                }
            }
            PinataInstance last = plugin.pinataManager().lastByType(typeId);
            if (last != null) {
                plugin.debug("PAPI find('" + typeId + "') -> last result");
                return last;
            }
            plugin.debug("PAPI find('" + typeId + "') -> NOT FOUND");
            return null;
        }

        private String safeName(UUID id) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            return n == null ? "-" : n;
        }
    }
}

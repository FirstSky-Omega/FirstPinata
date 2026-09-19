package fr.luc.pinata.integration;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataInstance;
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
 * Placeholders exposés :
 *   %pinata_active%                nombre de piñatas actifs
 *   %pinata_types_loaded%          nombre de types configurés
 *   %pinata_top_name_<type>%       nom du top damager pour un type actif
 *   %pinata_top_damage_<type>%     dégâts du top damager
 *   %pinata_player_hits_<type>%    dégâts totaux du joueur sur ce type actif
 *   %pinata_player_top_rank%       meilleur rang actuel du joueur sur un piñata actif
 *   %pinata_top_kills_name_N%      Nème joueur du classement persistant de victoires
 *   %pinata_top_kills_count_N%     nombre de victoires du Nème joueur
 *   %pinata_next_time%             temps formaté avant le prochain spawn schedulé
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
                    expansion.register();
                    plugin.getLogger().info("Expansion 'pinata' enregistrée.");
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
            String p = params.toLowerCase(Locale.ROOT);
            if (p.equals("active")) return String.valueOf(plugin.pinataManager().active());
            if (p.equals("types_loaded")) return String.valueOf(plugin.pinataConfig().types().size());
            if (p.startsWith("top_name_")) {
                String type = p.substring("top_name_".length());
                PinataInstance i = findActive(type);
                if (i == null) return "-";
                UUID top = i.damageTracker().topDamager();
                return top == null ? "-" : safeName(top);
            }
            if (p.startsWith("top_damage_")) {
                String type = p.substring("top_damage_".length());
                PinataInstance i = findActive(type);
                if (i == null) return "0";
                UUID top = i.damageTracker().topDamager();
                return top == null ? "0" : String.valueOf((int) i.damageTracker().get(top));
            }
            if (p.startsWith("player_hits_") && player != null) {
                String type = p.substring("player_hits_".length());
                PinataInstance i = findActive(type);
                if (i == null) return "0";
                return String.valueOf((int) i.damageTracker().get(player.getUniqueId()));
            }
            if (p.equals("player_top_rank") && player != null) {
                int best = Integer.MAX_VALUE;
                for (PinataInstance i : plugin.pinataManager().all()) {
                    List<Map.Entry<UUID, Double>> rk = i.damageTracker().ranking();
                    for (int r = 0; r < rk.size(); r++) {
                        if (rk.get(r).getKey().equals(player.getUniqueId())) {
                            best = Math.min(best, r + 1);
                            break;
                        }
                    }
                }
                return best == Integer.MAX_VALUE ? "-" : String.valueOf(best);
            }
            if (p.startsWith("top_kills_name_")) {
                try {
                    int rank = Integer.parseInt(p.substring("top_kills_name_".length()));
                    return plugin.winsManager().getTopName(rank);
                } catch (NumberFormatException ignored) { return null; }
            }
            if (p.startsWith("top_kills_count_")) {
                try {
                    int rank = Integer.parseInt(p.substring("top_kills_count_".length()));
                    return String.valueOf(plugin.winsManager().getTopWins(rank));
                } catch (NumberFormatException ignored) { return null; }
            }
            if (p.equals("next_time")) {
                long nextMs = plugin.scheduleManager().getNextMs();
                if (nextMs < 0) return "-";
                long secs = (nextMs - System.currentTimeMillis()) / 1000L;
                if (secs <= 0) return "En cours";
                return formatSeconds(secs);
            }
            return null;
        }

        private static String formatSeconds(long s) {
            if (s < 60) return s + "s";
            if (s < 3600) return (s / 60) + "min" + (s % 60 == 0 ? "" : " " + (s % 60) + "s");
            long h = s / 3600, m = (s % 3600) / 60;
            return h + "h" + (m == 0 ? "" : " " + m + "min");
        }

        private PinataInstance findActive(String typeId) {
            for (PinataInstance i : plugin.pinataManager().all()) {
                if (i.type().id().equalsIgnoreCase(typeId)) return i;
            }
            return null;
        }

        private String safeName(UUID id) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            return n == null ? "-" : n;
        }
    }
}

package fr.luc.pinata.config;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.util.ConfigMerger;
import fr.luc.pinata.util.MapUtil;
import fr.luc.pinata.util.MessageUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class MessageConfig {

    private final PinataPlugin plugin;
    private FileConfiguration cfg;
    private String prefix = "";
    private final List<HelpEntry> helpEntries = new ArrayList<>();

    public MessageConfig(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(String language, String prefix) {
        this.prefix = prefix == null ? "" : prefix;
        String fileName = "messages_" + (language == null ? "fr" : language.toLowerCase(Locale.ROOT)) + ".yml";

        // Merge récursif : ajoute les nouvelles clés du jar dans le fichier
        // utilisateur sans écraser ses traductions.
        cfg = ConfigMerger.mergeFromResource(plugin, fileName);
        if (cfg == null) {
            // Ressource introuvable : on retombe sur un fichier vide pour éviter les NPE.
            cfg = new org.bukkit.configuration.file.YamlConfiguration();
        }

        helpEntries.clear();
        List<Map<?, ?>> list = cfg.getMapList("help-commands");
        for (Map<?, ?> raw : list) {
            helpEntries.add(new HelpEntry(
                    MapUtil.str(raw, "cmd", ""),
                    MapUtil.str(raw, "args", ""),
                    MapUtil.str(raw, "desc", ""),
                    MapUtil.str(raw, "permission", "")
            ));
        }
    }

    public String raw(String key) {
        return cfg.getString(key, "");
    }

    public List<String> rawList(String key) {
        if (cfg.isList(key)) return cfg.getStringList(key);
        String single = cfg.getString(key);
        return single == null ? Collections.emptyList() : Collections.singletonList(single);
    }

    /** Applique le prefix devant. Vide si la clé est vide. */
    public Component get(String key) {
        String raw = raw(key);
        return raw.isEmpty() ? Component.empty() : MessageUtil.parse(prefix + raw);
    }

    public Component get(String key, Map<String, String> placeholders) {
        String raw = raw(key);
        return raw.isEmpty() ? Component.empty() : MessageUtil.parse(prefix + raw, placeholders);
    }

    /** Version sans prefix (pour les broadcasts customs qui incluent leur propre style). */
    public Component getNoPrefix(String key, Map<String, String> placeholders) {
        String raw = raw(key);
        return raw.isEmpty() ? Component.empty() : MessageUtil.parse(raw, placeholders);
    }

    public void send(Audience to, String key) {
        String raw = raw(key);
        if (raw.isEmpty()) return;
        to.sendMessage(MessageUtil.parse(applyPapi(to, prefix + raw)));
    }

    public void send(Audience to, String key, Map<String, String> placeholders) {
        String raw = raw(key);
        if (raw.isEmpty()) return;
        to.sendMessage(MessageUtil.parse(applyPapi(to, prefix + raw), placeholders));
    }

    public void sendList(Audience to, String key, Map<String, String> placeholders) {
        for (String line : rawList(key)) {
            to.sendMessage(MessageUtil.parse(applyPapi(to, line), placeholders));
        }
    }

    /**
     * Applique PAPI :
     *   - Si l'audience est un Player, résolution personnalisée (placeholders joueur-spécifiques).
     *   - Sinon (broadcast serveur, console), résolution globale (player = null).
     */
    private String applyPapi(Audience audience, String text) {
        if (plugin.papi() == null || !plugin.papi().isPresent()) return text;
        if (audience instanceof Player p) return plugin.papi().apply(p, text);
        return plugin.papi().apply((Player) null, text);
    }

    public List<HelpEntry> getHelpEntries() {
        return helpEntries;
    }

    public String prefix() {
        return prefix;
    }

    public record HelpEntry(String cmd, String args, String desc, String permission) { }
}

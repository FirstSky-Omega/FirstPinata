package fr.luc.pinata.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Merger de config récursif :
 *   - charge la config utilisateur
 *   - la compare à la version par défaut du jar
 *   - ajoute les clés manquantes (sans écraser l'existant)
 *   - copie les commentaires quand l'API le permet (Paper 1.18+)
 *   - sauvegarde uniquement si quelque chose a été ajouté
 */
public final class ConfigMerger {

    private ConfigMerger() { }

    /**
     * Fusionne la ressource JAR {@code resourcePath} dans le fichier
     * utilisateur situé au même chemin relatif au dataFolder.
     *
     * @return la config utilisateur (mise à jour), ou null si la ressource
     *         n'existe pas dans le jar.
     */
    public static FileConfiguration mergeFromResource(JavaPlugin plugin, String resourcePath) {
        File userFile = new File(plugin.getDataFolder(), resourcePath);
        if (!userFile.exists()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ressource jar absente : " + resourcePath);
                return null;
            }
        }

        FileConfiguration userCfg = YamlConfiguration.loadConfiguration(userFile);

        YamlConfiguration defaults;
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return userCfg;
            defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Lecture ressource '" + resourcePath + "' : " + e.getMessage());
            return userCfg;
        }

        int added = mergeSection(userCfg, defaults);
        if (added > 0) {
            try {
                userCfg.save(userFile);
                plugin.getLogger().info("Config '" + resourcePath + "' : "
                        + added + " clé(s) manquante(s) ajoutée(s).");
            } catch (IOException e) {
                plugin.getLogger().warning("Sauvegarde '" + resourcePath + "' : " + e.getMessage());
            }
        }
        return userCfg;
    }

    /**
     * Copie les clés manquantes de {@code defaults} vers {@code user}.
     * Récursif pour les sous-sections.
     *
     * @return nombre de clés ajoutées.
     */
    private static int mergeSection(ConfigurationSection user, ConfigurationSection defaults) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            Object defValue = defaults.get(key);

            if (defValue instanceof ConfigurationSection defSection) {
                ConfigurationSection userSub = user.getConfigurationSection(key);
                if (userSub == null) {
                    // Section entière manquante : on la crée puis on recurse
                    userSub = user.createSection(key);
                    copyComments(user, defaults, key);
                    added++;
                }
                added += mergeSection(userSub, defSection);
            } else if (!user.contains(key, true)) {
                // Feuille manquante (valeur simple ou liste) : on la pose telle quelle
                user.set(key, defValue);
                copyComments(user, defaults, key);
                added++;
            }
        }
        return added;
    }

    /**
     * Copie les commentaires block et inline associés à {@code key} de
     * {@code from} vers {@code to}. Les méthodes get/setComments existent
     * depuis Paper 1.18 ; on catch tout pour rester safe.
     */
    private static void copyComments(ConfigurationSection to, ConfigurationSection from, String key) {
        try {
            List<String> block = from.getComments(key);
            if (block != null && !block.isEmpty()) to.setComments(key, block);
        } catch (Throwable ignored) { }
        try {
            List<String> inline = from.getInlineComments(key);
            if (inline != null && !inline.isEmpty()) to.setInlineComments(key, inline);
        } catch (Throwable ignored) { }
    }
}

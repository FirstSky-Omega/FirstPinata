package fr.luc.pinata.config;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.util.ConfigMerger;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.time.ZoneId;

public class ConfigManager {

    private final PinataPlugin plugin;

    private FileConfiguration mainConfig;

    // Global settings
    private String language;
    private String prefix;
    private boolean debug;
    private int maxConcurrent;
    private int participationRadius;
    private int idleDespawn;
    private ZoneId timezone;

    private boolean mythicEnabled;
    private boolean mythicKeepSkillsDefault;
    private boolean mythicIgnoreSkillDamage;

    private boolean nexoEnabled;

    private boolean modelEngineEnabled;
    private boolean modelEngineHideBaseMob;

    private boolean placeholderApiEnabled;
    private boolean placeholderApiRegisterExpansion;

    private boolean rewardHitMessageEnabled;
    private boolean rewardTierBroadcastEnabled;
    private boolean rewardAsyncCommands;

    private String storageType;
    private int historyPerPlayer;

    public ConfigManager(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();

        // Merge récursif : ajoute les clés manquantes sans écraser les valeurs
        // existantes. La config utilisateur est ensuite chargée depuis le disque.
        mainConfig = ConfigMerger.mergeFromResource(plugin, "config.yml");

        this.language              = mainConfig.getString("language", "fr");
        this.prefix                = mainConfig.getString("prefix", "");
        this.debug                 = mainConfig.getBoolean("debug", false);
        this.maxConcurrent         = mainConfig.getInt("general.max-concurrent", 5);
        this.participationRadius   = mainConfig.getInt("general.participation-radius", 60);
        this.idleDespawn           = mainConfig.getInt("general.idle-despawn", 120);

        String tz = mainConfig.getString("general.timezone", "system");
        if (tz == null || tz.isBlank() || "system".equalsIgnoreCase(tz.trim())) {
            this.timezone = ZoneId.systemDefault();
        } else {
            try {
                this.timezone = ZoneId.of(tz.trim());
            } catch (Exception ex) {
                plugin.getLogger().warning("Fuseau horaire invalide '" + tz + "' : "
                        + ex.getMessage() + " — utilisation du fuseau système.");
                this.timezone = ZoneId.systemDefault();
            }
        }
        plugin.getLogger().info("Fuseau horaire schedules : " + this.timezone
                + " (heure actuelle : " + java.time.LocalTime.now(this.timezone) + ")");

        this.mythicEnabled              = mainConfig.getBoolean("mythicmobs.enabled", true);
        this.mythicKeepSkillsDefault    = mainConfig.getBoolean("mythicmobs.keep-skills-by-default", true);
        this.mythicIgnoreSkillDamage    = mainConfig.getBoolean("mythicmobs.ignore-skill-damage", true);

        this.nexoEnabled = mainConfig.getBoolean("nexo.enabled", true);

        this.modelEngineEnabled     = mainConfig.getBoolean("modelengine.enabled", true);
        this.modelEngineHideBaseMob = mainConfig.getBoolean("modelengine.hide-base-mob", true);

        this.placeholderApiEnabled             = mainConfig.getBoolean("placeholderapi.enabled", true);
        this.placeholderApiRegisterExpansion   = mainConfig.getBoolean("placeholderapi.register-expansion", true);

        this.rewardHitMessageEnabled     = mainConfig.getBoolean("rewards.hit-message-enabled", true);
        this.rewardTierBroadcastEnabled  = mainConfig.getBoolean("rewards.tier-broadcast-enabled", true);
        this.rewardAsyncCommands         = mainConfig.getBoolean("rewards.async-command-execution", false);

        this.storageType         = mainConfig.getString("storage.type", "yaml");
        this.historyPerPlayer    = mainConfig.getInt("storage.history-per-player", 50);

        // Save default zones / schedules / example pinatas si absents
        saveIfMissing("zones.yml");
        saveIfMissing("schedules.yml");
        saveIfMissing("pinatas/example.yml");
        saveIfMissing("pinatas/boss.yml");
        saveIfMissing("messages_fr.yml");
    }

    private void saveIfMissing(String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (!target.exists()) {
            try {
                plugin.saveResource(path, false);
            } catch (IllegalArgumentException ex) {
                // Ressource absente du jar : on ignore.
            }
        }
    }

    public FileConfiguration mainConfig() { return mainConfig; }

    public String language() { return language; }
    public String prefix() { return prefix; }
    public boolean debug() { return debug; }
    public int maxConcurrent() { return maxConcurrent; }
    public int participationRadius() { return participationRadius; }
    public int idleDespawn() { return idleDespawn; }
    public ZoneId timezone() { return timezone; }

    public boolean mythicEnabled() { return mythicEnabled; }
    public boolean mythicKeepSkillsDefault() { return mythicKeepSkillsDefault; }
    public boolean mythicIgnoreSkillDamage() { return mythicIgnoreSkillDamage; }

    public boolean nexoEnabled() { return nexoEnabled; }

    public boolean modelEngineEnabled() { return modelEngineEnabled; }
    public boolean modelEngineHideBaseMob() { return modelEngineHideBaseMob; }

    public boolean placeholderApiEnabled() { return placeholderApiEnabled; }
    public boolean placeholderApiRegisterExpansion() { return placeholderApiRegisterExpansion; }

    public boolean rewardHitMessageEnabled() { return rewardHitMessageEnabled; }
    public boolean rewardTierBroadcastEnabled() { return rewardTierBroadcastEnabled; }
    public boolean rewardAsyncCommands() { return rewardAsyncCommands; }

    public String storageType() { return storageType; }
    public int historyPerPlayer() { return historyPerPlayer; }
}

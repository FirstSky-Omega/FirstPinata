package fr.luc.pinata.config;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataType;
import fr.luc.pinata.pinata.PinataType.*;
import fr.luc.pinata.reward.Reward;
import fr.luc.pinata.reward.RewardTable;
import fr.luc.pinata.reward.RewardTier;
import fr.luc.pinata.util.MapUtil;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.*;

/**
 * Charge tous les fichiers dans pinatas/*.yml en PinataType.
 */
public class PinataConfig {

    private final PinataPlugin plugin;
    private final Map<String, PinataType> types = new LinkedHashMap<>();

    public PinataConfig(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        types.clear();
        File dir = new File(plugin.getDataFolder(), "pinatas");
        if (!dir.exists()) dir.mkdirs();

        File[] files = dir.listFiles((f, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                PinataType type = parse(id, cfg);
                types.put(id.toLowerCase(Locale.ROOT), type);
            } catch (Exception ex) {
                plugin.getLogger().warning("Erreur lecture pinata '" + id + "' : " + ex.getMessage());
            }
        }
    }

    public Map<String, PinataType> types() { return types; }

    public PinataType byId(String id) {
        if (id == null) return null;
        return types.get(id.toLowerCase(Locale.ROOT));
    }

    // -------------------------------------------------------------------

    private PinataType parse(String id, YamlConfiguration cfg) {
        String displayName = cfg.getString("display-name", "<white>Piñata");

        // ---- Mob
        ConfigurationSection mobSec = cfg.getConfigurationSection("mob");
        Mob mob = parseMob(mobSec);

        // ---- Mythic override
        ConfigurationSection mmSec = cfg.getConfigurationSection("mythicmobs");
        MythicOverride mmOverride = parseMythic(mmSec);

        // ---- Stats
        double maxHealth = cfg.getDouble("stats.max-health", 200.0);

        // ---- Boss bar
        BossBar bossBar = parseBossBar(cfg.getConfigurationSection("stats.boss-bar"));

        // ---- Lifetime
        Lifetime lifetime = parseLifetime(cfg.getConfigurationSection("lifetime"));

        // ---- Effects
        Effect spawn = parseEffect(cfg.getConfigurationSection("effects.spawn"));
        Effect hit = parseEffect(cfg.getConfigurationSection("effects.hit"));
        Effect death = parseEffect(cfg.getConfigurationSection("effects.death"));

        // ---- Damage
        DamageRules dmg = parseDamage(cfg.getConfigurationSection("damage"));

        // ---- Rewards
        RewardTable rewards = parseRewards(cfg.getConfigurationSection("rewards"));

        return new PinataType(id, displayName, mob, mmOverride, maxHealth, bossBar,
                lifetime, spawn, hit, death, dmg, rewards);
    }

    private Mob parseMob(ConfigurationSection sec) {
        if (sec == null) {
            return new Mob(MobSource.VANILLA, EntityType.PIG, null, 1,
                    false, true, true, false, false, 0, null);
        }
        String typeStr = sec.getString("type", "vanilla").toLowerCase(Locale.ROOT);
        MobSource src = "mythic".equals(typeStr) ? MobSource.MYTHIC : MobSource.VANILLA;
        EntityType entity = null;
        if (src == MobSource.VANILLA) {
            String e = sec.getString("entity", "PIG");
            try { entity = EntityType.valueOf(e.toUpperCase(Locale.ROOT)); }
            catch (Exception ex) { entity = EntityType.PIG; }
        }
        String mythicId = sec.getString("mythic-id");
        int level = sec.getInt("level", 1);

        // ModelEngine model (optionnel)
        ModelEngineModel meg = null;
        ConfigurationSection megSec = sec.getConfigurationSection("model");
        if (megSec != null && megSec.getString("id") != null) {
            meg = new ModelEngineModel(
                    megSec.getString("id"),
                    megSec.getBoolean("lock-yaw", false),
                    megSec.getDouble("scale", 1.0),
                    megSec.getString("nametag", null)
            );
        }

        return new Mob(
                src,
                entity,
                mythicId,
                level,
                sec.getBoolean("glowing", false),
                sec.getBoolean("gravity", true),
                sec.getBoolean("invulnerable-to-non-players", true),
                sec.getBoolean("ai", false),
                sec.getBoolean("silent", false),
                sec.getInt("no-damage-ticks", 0),
                meg
        );
    }

    private MythicOverride parseMythic(ConfigurationSection sec) {
        if (sec == null) return new MythicOverride(null, Collections.emptyList());
        Boolean keep = sec.contains("keep-skills") ? sec.getBoolean("keep-skills") : null;
        List<ExtraSkill> extras = new ArrayList<>();
        List<Map<?, ?>> list = sec.getMapList("extra-skills");
        for (Map<?, ?> raw : list) {
            String trigger = MapUtil.str(raw, "trigger", "onDamaged");
            String skill = MapUtil.str(raw, "skill", "");
            double chance = MapUtil.d(raw, "chance", 1.0);
            if (!skill.isEmpty()) extras.add(new ExtraSkill(trigger, skill, chance));
        }
        return new MythicOverride(keep, extras);
    }

    private BossBar parseBossBar(ConfigurationSection sec) {
        if (sec == null || !sec.getBoolean("enabled", false)) {
            return new BossBar(false, "", BarColor.PINK, BarStyle.SEGMENTED_10, EnumSet.noneOf(BarFlag.class), 60);
        }
        BarColor color = safeEnum(BarColor.class, sec.getString("color", "PINK"), BarColor.PINK);
        BarStyle style = safeEnum(BarStyle.class, sec.getString("style", "SEGMENTED_10"), BarStyle.SEGMENTED_10);
        Set<BarFlag> flags = EnumSet.noneOf(BarFlag.class);
        for (String f : sec.getStringList("flags")) {
            String norm = f == null ? "" : f.trim().toUpperCase(Locale.ROOT);
            // Aliases pour rester compatible avec la nomenclature Adventure.
            if (norm.equals("DARKEN_SCREEN")) norm = "DARKEN_SKY";
            if (norm.equals("CREATE_WORLD_FOG")) norm = "CREATE_FOG";
            BarFlag flag = safeEnum(BarFlag.class, norm, null);
            if (flag != null) flags.add(flag);
        }
        return new BossBar(true,
                sec.getString("title", "<white><pinata> <red><hp>/<max_hp>"),
                color, style, flags,
                sec.getInt("radius", 60));
    }

    private Lifetime parseLifetime(ConfigurationSection sec) {
        if (sec == null) return new Lifetime(300, 60, "");
        return new Lifetime(
                sec.getInt("max-seconds", 300),
                sec.getInt("idle-seconds", 60),
                sec.getString("timeout-broadcast", "")
        );
    }

    private Effect parseEffect(ConfigurationSection sec) {
        if (sec == null) return new Effect(null, 1, 1, null, 0, "", 0, Collections.emptyList());
        List<ParticleDef> extras = new ArrayList<>();
        if (sec.isList("particles")) {
            for (Map<?, ?> raw : sec.getMapList("particles")) {
                extras.add(new ParticleDef(
                        MapUtil.str(raw, "type", "REDSTONE"),
                        MapUtil.i(raw, "count", 10)
                ));
            }
        }
        return new Effect(
                sec.getString("sound"),
                (float) sec.getDouble("volume", 1.0),
                (float) sec.getDouble("pitch", 1.0),
                sec.getString("particle"),
                sec.getInt("particle-count", 10),
                sec.getString("broadcast", ""),
                sec.getInt("broadcast-radius", 0),
                extras
        );
    }

    private DamageRules parseDamage(ConfigurationSection sec) {
        if (sec == null) return new DamageRules(1.0, 0, 0,
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), 0);
        Set<String> blocked = new HashSet<>(sec.getStringList("blocked-causes"));
        Set<String> allowed = new HashSet<>();
        for (String s : sec.getStringList("allowed-materials")) allowed.add(s.toUpperCase(Locale.ROOT));
        Set<String> denied = new HashSet<>();
        for (String s : sec.getStringList("denied-materials")) denied.add(s.toUpperCase(Locale.ROOT));
        return new DamageRules(
                sec.getDouble("multiplier", 1.0),
                sec.getDouble("per-hit-cap", 0),
                sec.getDouble("per-player-cap", 0),
                blocked, allowed, denied,
                sec.getLong("cooldown-ms", 0)
        );
    }

    private RewardTable parseRewards(ConfigurationSection sec) {
        if (sec == null) return RewardTable.empty();

        double chancePerHit = sec.getDouble("hit.chance-per-hit", 0.0);
        List<Reward> hitRewards = new ArrayList<>();
        if (sec.isList("hit.entries")) {
            for (Map<?, ?> raw : sec.getMapList("hit.entries")) {
                hitRewards.add(new Reward(
                        MapUtil.str(raw, "name", ""),
                        MapUtil.d(raw, "weight", 1.0),
                        MapUtil.strList(raw, "actions")
                ));
            }
        }

        List<RewardTier> tiers = new ArrayList<>();
        if (sec.isList("tiers")) {
            for (Map<?, ?> raw : sec.getMapList("tiers")) {
                int minRank = MapUtil.i(raw, "min-rank", 1);
                int maxRank = MapUtil.i(raw, "max-rank", minRank);
                tiers.add(new RewardTier(
                        MapUtil.str(raw, "name", ""),
                        minRank, maxRank,
                        MapUtil.d(raw, "min-damage", 0),
                        MapUtil.strList(raw, "actions")
                ));
            }
        }

        double participationMin = sec.getDouble("participation.min-damage", 0);
        List<String> participationActions = sec.getStringList("participation.actions");

        return new RewardTable(chancePerHit, hitRewards, tiers, participationMin, participationActions);
    }

    private static <T extends Enum<T>> T safeEnum(Class<T> cls, String name, T fallback) {
        if (name == null) return fallback;
        try { return Enum.valueOf(cls, name.toUpperCase(Locale.ROOT)); }
        catch (Exception ex) { return fallback; }
    }
}

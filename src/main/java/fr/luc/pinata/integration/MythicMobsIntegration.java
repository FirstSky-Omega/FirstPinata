package fr.luc.pinata.integration;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataInstance;
import fr.luc.pinata.pinata.PinataType;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Bridge MythicMobs — safe même si MythicMobs n'est pas installé.
 * Toutes les références à MythicBukkit sont derrière un check isPresent().
 */
public class MythicMobsIntegration {

    private final PinataPlugin plugin;
    private final boolean present;

    public MythicMobsIntegration(PinataPlugin plugin) {
        this.plugin = plugin;
        Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
        this.present = mm != null && mm.isEnabled() && plugin.config().mythicEnabled();
        if (present) plugin.getLogger().info("MythicMobs détecté : intégration activée.");
    }

    public boolean isPresent() {
        return present;
    }

    /** Spawn un MythicMob et retourne son LivingEntity. */
    public LivingEntity spawnMythic(String id, Location loc, int level) {
        if (!present || id == null) return null;
        try {
            Optional<MythicMob> mob = MythicBukkit.inst().getMobManager().getMythicMob(id);
            if (mob.isEmpty()) {
                plugin.getLogger().warning("MythicMob '" + id + "' introuvable.");
                return null;
            }
            ActiveMob active = mob.get().spawn(BukkitAdapter.adapt(loc), (double) level);
            if (active == null) return null;
            Entity ent = active.getEntity().getBukkitEntity();
            return ent instanceof LivingEntity le ? le : null;
        } catch (Throwable t) {
            plugin.getLogger().warning("spawnMythic '" + id + "' : " + t.getMessage());
            return null;
        }
    }

    public ActiveMob activeMobOf(LivingEntity entity) {
        if (!present || entity == null) return null;
        try {
            return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Décide si on doit désactiver les skills natifs du mob.
     * - vanilla : jamais désactivé (pas de skills natifs)
     * - mythic : selon config (override par piñata, sinon défaut global)
     */
    public boolean shouldKeepSkills(PinataType type) {
        if (type.mob().source() != PinataType.MobSource.MYTHIC) return true;
        Boolean override = type.mythicOverride().keepSkills();
        if (override != null) return override;
        return plugin.config().mythicKeepSkillsDefault();
    }

    public boolean ignoreSkillDamage() {
        return present && plugin.config().mythicIgnoreSkillDamage();
    }

    /**
     * Cast un skill mythic sur l'entité (utilisé pour les extra-skills).
     * Utilise l'API stable BukkitAPIHelper.castSkill(entity, name).
     */
    public void triggerExtraSkills(PinataInstance instance, LivingEntity entity, String triggerName) {
        if (!present || entity == null) return;
        try {
            BukkitAPIHelper helper = MythicBukkit.inst().getAPIHelper();
            for (PinataType.ExtraSkill extra : instance.type().mythicOverride().extraSkills()) {
                if (!extra.trigger().equalsIgnoreCase(triggerName)) continue;
                if (Math.random() > extra.chance()) continue;
                try {
                    helper.castSkill(entity, extra.skill());
                } catch (Throwable t) {
                    plugin.getLogger().warning("cast skill '" + extra.skill() + "' : " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("triggerExtraSkills : " + t.getMessage());
        }
    }
}

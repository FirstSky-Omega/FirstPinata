package fr.luc.pinata.pinata;

import fr.luc.pinata.reward.RewardTable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

/**
 * Représente un TYPE de piñata (config immuable), pas une instance.
 */
public final class PinataType {

    public enum MobSource { VANILLA, MYTHIC }

    public record Mob(
            MobSource source,
            EntityType entityType,        // pour VANILLA
            String mythicId,              // pour MYTHIC
            int mythicLevel,
            boolean glowing,
            boolean gravity,
            boolean invulnerableToNonPlayers,
            boolean ai,
            boolean silent,
            int noDamageTicks,
            ModelEngineModel megModel     // null si pas de model MEG
    ) { }

    /**
     * Configuration d'un model ModelEngine attaché au piñata.
     */
    public record ModelEngineModel(
            String modelId,               // id du .bbmodel importé dans MEG
            boolean lockYaw,
            double scale,
            String nametag                // optionnel : bone nametag à afficher
    ) { }

    public record MythicOverride(
            Boolean keepSkills,                  // null = utiliser le default global
            List<ExtraSkill> extraSkills
    ) { }

    public record ExtraSkill(String trigger, String skill, double chance) { }

    public record BossBar(
            boolean enabled,
            String title,
            BarColor color,
            BarStyle style,
            Set<BarFlag> flags,
            int radius
    ) { }

    public record Lifetime(
            int maxSeconds,
            int idleSeconds,
            String timeoutBroadcast
    ) { }

    public record Effect(
            String sound, float volume, float pitch,
            String particle, int particleCount,
            String broadcast, int broadcastRadius,
            List<ParticleDef> extraParticles
    ) { }

    public record ParticleDef(String type, int count) { }

    public record DamageRules(
            double multiplier,
            double perHitCap,
            double perPlayerCap,
            Set<String> blockedCauses,
            Set<String> allowedMaterials,
            Set<String> deniedMaterials,
            long cooldownMs
    ) { }

    private final String id;
    private final String displayName;
    private final Mob mob;
    private final MythicOverride mythicOverride;
    private final double maxHealth;
    private final BossBar bossBar;
    private final Lifetime lifetime;
    private final Effect spawnEffect;
    private final Effect hitEffect;
    private final Effect deathEffect;
    private final DamageRules damageRules;
    private final RewardTable rewards;

    public PinataType(String id, String displayName, Mob mob, MythicOverride mythicOverride,
                      double maxHealth, BossBar bossBar, Lifetime lifetime,
                      Effect spawnEffect, Effect hitEffect, Effect deathEffect,
                      DamageRules damageRules, RewardTable rewards) {
        this.id = id;
        this.displayName = displayName;
        this.mob = mob;
        this.mythicOverride = mythicOverride;
        this.maxHealth = maxHealth;
        this.bossBar = bossBar;
        this.lifetime = lifetime;
        this.spawnEffect = spawnEffect;
        this.hitEffect = hitEffect;
        this.deathEffect = deathEffect;
        this.damageRules = damageRules;
        this.rewards = rewards;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Mob mob() { return mob; }
    public MythicOverride mythicOverride() { return mythicOverride; }
    public double maxHealth() { return maxHealth; }
    public BossBar bossBar() { return bossBar; }
    public Lifetime lifetime() { return lifetime; }
    public Effect spawnEffect() { return spawnEffect; }
    public Effect hitEffect() { return hitEffect; }
    public Effect deathEffect() { return deathEffect; }
    public DamageRules damageRules() { return damageRules; }
    public RewardTable rewards() { return rewards; }
}

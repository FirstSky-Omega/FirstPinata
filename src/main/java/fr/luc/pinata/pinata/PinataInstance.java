package fr.luc.pinata.pinata;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.UUID;

/**
 * Une instance de piñata active dans le monde.
 */
public class PinataInstance {

    private final UUID id;
    private final PinataType type;
    private final LivingEntity entity;
    private final Location spawnLocation;
    private final long spawnedAtMs;
    private final DamageTracker damageTracker;
    private BossBar bossBar;
    private boolean removed;

    public PinataInstance(PinataType type, LivingEntity entity, Location spawnLocation) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.entity = entity;
        this.spawnLocation = spawnLocation;
        this.spawnedAtMs = System.currentTimeMillis();
        this.damageTracker = new DamageTracker();
    }

    public UUID id() { return id; }
    public PinataType type() { return type; }
    public LivingEntity entity() { return entity; }
    public Location spawnLocation() { return spawnLocation; }
    public long spawnedAtMs() { return spawnedAtMs; }
    public DamageTracker damageTracker() { return damageTracker; }
    public BossBar bossBar() { return bossBar; }
    public void setBossBar(BossBar bossBar) { this.bossBar = bossBar; }

    public boolean isRemoved() { return removed; }
    public void markRemoved() { this.removed = true; }

    public double health() {
        return entity != null && entity.isValid() ? entity.getHealth() : 0;
    }

    public double maxHealth() {
        return type.maxHealth();
    }

    public long uptimeMs() {
        return System.currentTimeMillis() - spawnedAtMs;
    }
}

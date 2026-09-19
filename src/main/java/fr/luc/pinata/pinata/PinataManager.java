package fr.luc.pinata.pinata;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.util.MessageUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PinataManager {

    public static final NamespacedKey PINATA_KEY = new NamespacedKey("pinata", "instance");
    public static final NamespacedKey PINATA_TYPE_KEY = new NamespacedKey("pinata", "type");

    private final PinataPlugin plugin;
    private final Map<UUID, PinataInstance> byEntity = new ConcurrentHashMap<>();
    private final Map<UUID, PinataInstance> byId = new ConcurrentHashMap<>();

    public PinataManager(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public int active() { return byId.size(); }
    public Collection<PinataInstance> all() { return Collections.unmodifiableCollection(byId.values()); }

    public PinataInstance byEntity(UUID entityId) { return byEntity.get(entityId); }
    public PinataInstance byId(UUID id) { return byId.get(id); }

    public boolean isPinata(org.bukkit.entity.Entity entity) {
        if (entity == null) return false;
        if (byEntity.containsKey(entity.getUniqueId())) return true;
        return entity.getPersistentDataContainer().has(PINATA_KEY, PersistentDataType.STRING);
    }

    /**
     * Spawn synchro (à appeler sur le region-thread de la location).
     */
    public PinataInstance spawn(PinataType type, Location location) {
        if (byId.size() >= plugin.config().maxConcurrent()) {
            return null;
        }
        Location loc = location.clone().add(0.5, 0, 0.5);

        LivingEntity entity = createEntity(type, loc);
        if (entity == null) {
            plugin.getLogger().warning("Impossible de spawn le mob pour piñata " + type.id());
            return null;
        }

        applyMobOptions(type, entity);

        PinataInstance instance = new PinataInstance(type, entity, loc);
        byId.put(instance.id(), instance);
        byEntity.put(entity.getUniqueId(), instance);

        // Boss bar
        if (type.bossBar().enabled()) {
            BossBar bar = BossBar.bossBar(renderBossBarTitle(type, entity),
                    1.0f, mapColor(type.bossBar().color()), mapStyle(type.bossBar().style()));
            for (org.bukkit.boss.BarFlag f : type.bossBar().flags()) {
                switch (f) {
                    case CREATE_FOG      -> bar.addFlag(BossBar.Flag.CREATE_WORLD_FOG);
                    case DARKEN_SKY      -> bar.addFlag(BossBar.Flag.DARKEN_SCREEN);
                    case PLAY_BOSS_MUSIC -> bar.addFlag(BossBar.Flag.PLAY_BOSS_MUSIC);
                }
            }
            instance.setBossBar(bar);
            // audience initial : joueurs à portée
            updateBossBarAudience(instance);
        }

        // Attache le model MEG si configuré
        if (plugin.modelEngine().isPresent() && type.mob().megModel() != null) {
            plugin.scheduler().runFor(entity,
                    () -> plugin.modelEngine().attachModel(entity, type),
                    () -> { });
        }

        // Extra Mythic skills : onSpawn
        if (plugin.mythic().isPresent() && type.mob().source() == PinataType.MobSource.MYTHIC) {
            plugin.mythic().triggerExtraSkills(instance, entity, "onSpawn");
        }

        // Effet spawn
        playEffect(type.spawnEffect(), loc, instance, null);

        // Lifetime scheduler
        scheduleLifetime(instance);

        return instance;
    }

    private LivingEntity createEntity(PinataType type, Location loc) {
        return switch (type.mob().source()) {
            case VANILLA -> {
                Class<?> cls = type.mob().entityType().getEntityClass();
                if (cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
                    plugin.getLogger().warning("EntityType non-living pour piñata " + type.id()
                            + " : " + type.mob().entityType());
                    yield null;
                }
                yield loc.getWorld().spawn(loc, cls.asSubclass(LivingEntity.class));
            }
            case MYTHIC -> plugin.mythic().spawnMythic(type.mob().mythicId(), loc, type.mob().mythicLevel());
        };
    }

    private void applyMobOptions(PinataType type, LivingEntity entity) {
        try {
            entity.getPersistentDataContainer().set(PINATA_KEY, PersistentDataType.STRING, "1");
            entity.getPersistentDataContainer().set(PINATA_TYPE_KEY, PersistentDataType.STRING, type.id());

            entity.customName(MessageUtil.parse(type.displayName()));
            entity.setCustomNameVisible(true);
            entity.setGlowing(type.mob().glowing());
            entity.setSilent(type.mob().silent());
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            entity.setGravity(type.mob().gravity());

            if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
                entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(type.maxHealth());
                entity.setHealth(type.maxHealth());
            }
            if (type.mob().noDamageTicks() > 0) {
                entity.setMaximumNoDamageTicks(type.mob().noDamageTicks());
            }
            if (!type.mob().ai()) {
                entity.setAI(false);
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("applyMobOptions : " + ex.getMessage());
        }
    }

    private Component renderBossBarTitle(PinataType type, LivingEntity entity) {
        Map<String, String> ph = MessageUtil.placeholders()
                .set("pinata", type.displayName())
                .set("pinata_id", type.id())
                .set("hp", String.valueOf((int) entity.getHealth()))
                .set("max_hp", String.valueOf((int) type.maxHealth()))
                .build();
        return MessageUtil.parse(type.bossBar().title(), ph);
    }

    public void updateBossBar(PinataInstance instance) {
        if (instance.bossBar() == null || instance.entity() == null) return;
        double hp = Math.max(0, instance.entity().getHealth());
        float progress = (float) Math.max(0, Math.min(1, hp / instance.maxHealth()));
        instance.bossBar().progress(progress);
        instance.bossBar().name(renderBossBarTitle(instance.type(), instance.entity()));
        updateBossBarAudience(instance);
    }

    public void updateBossBarAudience(PinataInstance instance) {
        if (instance.bossBar() == null || instance.entity() == null) return;
        Location loc = instance.entity().getLocation();
        int radius = instance.type().bossBar().radius();
        double r2 = radius * radius;
        Set<UUID> toShow = new HashSet<>();
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= r2) {
                toShow.add(p.getUniqueId());
            }
        }
        // add
        for (UUID uid : toShow) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.showBossBar(instance.bossBar());
        }
    }

    public void hideBossBar(PinataInstance instance) {
        if (instance.bossBar() == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(instance.bossBar());
        }
    }

    private void playEffect(PinataType.Effect effect, Location loc, PinataInstance instance, Player specific) {
        if (effect == null) return;
        World world = loc.getWorld();
        if (world == null) return;

        // Sound
        if (effect.sound() != null && !effect.sound().isEmpty()) {
            Sound s = fr.luc.pinata.util.SoundUtil.resolve(effect.sound());
            if (s != null) {
                if (specific != null) specific.playSound(loc, s, effect.volume(), effect.pitch());
                else world.playSound(loc, s, effect.volume(), effect.pitch());
            }
        }

        // Particles (main)
        if (effect.particle() != null && !effect.particle().isEmpty()) {
            spawnParticle(world, loc, effect.particle(), effect.particleCount());
        }
        for (PinataType.ParticleDef def : effect.extraParticles()) {
            spawnParticle(world, loc, def.type(), def.count());
        }

        // Broadcast
        if (effect.broadcast() != null && !effect.broadcast().isEmpty() && instance != null) {
            Component msg = MessageUtil.parse(effect.broadcast(), buildEffectPlaceholders(instance));
            if (effect.broadcastRadius() <= 0) {
                Bukkit.broadcast(msg);
            } else {
                double r2 = effect.broadcastRadius() * effect.broadcastRadius();
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(loc) <= r2) {
                        p.sendMessage(msg);
                    }
                }
            }
        }
    }

    public void playHitEffect(PinataInstance instance, Player attacker) {
        playEffect(instance.type().hitEffect(), instance.entity().getLocation(), instance, attacker);
    }

    public void playDeathEffect(PinataInstance instance) {
        playEffect(instance.type().deathEffect(), instance.entity().getLocation(), instance, null);
    }

    private Map<String, String> buildEffectPlaceholders(PinataInstance i) {
        Location loc = i.entity() != null ? i.entity().getLocation() : i.spawnLocation();
        UUID top = i.damageTracker().topDamager();
        String topName = top != null && Bukkit.getOfflinePlayer(top).getName() != null
                ? Bukkit.getOfflinePlayer(top).getName() : "-";
        return MessageUtil.placeholders()
                .set("pinata", i.type().displayName())
                .set("pinata_id", i.type().id())
                .set("hp", (int) i.health())
                .set("max_hp", (int) i.maxHealth())
                .set("world", loc.getWorld().getName())
                .set("x", loc.getBlockX())
                .set("y", loc.getBlockY())
                .set("z", loc.getBlockZ())
                .set("top", topName)
                .set("participants", i.damageTracker().participants().size())
                .build();
    }

    private void spawnParticle(World world, Location loc, String rawName, int count) {
        try {
            Particle p = Particle.valueOf(rawName.toUpperCase(Locale.ROOT));
            world.spawnParticle(p, loc.clone().add(0, 0.5, 0), count, 0.5, 0.5, 0.5, 0.05);
        } catch (IllegalArgumentException ignored) { }
    }

    // -------------------------------------------------------------------

    public void despawn(PinataInstance instance, String reason) {
        if (instance == null || instance.isRemoved()) return;
        instance.markRemoved();
        byId.remove(instance.id());
        if (instance.entity() != null) byEntity.remove(instance.entity().getUniqueId());

        hideBossBar(instance);

        LivingEntity ent = instance.entity();
        if (ent != null) {
            if (plugin.modelEngine().isPresent() && instance.type().mob().megModel() != null) {
                plugin.scheduler().runFor(ent, () -> plugin.modelEngine().removeModel(ent), () -> { });
            }
            if (!ent.isDead()) {
                plugin.scheduler().runFor(ent, ent::remove, () -> { });
            }
        }
        plugin.debug("Despawned " + instance.type().id() + " (" + reason + ")");
    }

    public void despawnAll(String reason) {
        for (PinataInstance i : new ArrayList<>(byId.values())) {
            despawn(i, reason);
        }
    }

    // -------------------------------------------------------------------

    private void scheduleLifetime(PinataInstance instance) {
        int maxSec = instance.type().lifetime().maxSeconds();
        int idleSec = instance.type().lifetime().idleSeconds();
        LivingEntity ent = instance.entity();

        // Repeating tick pour boss bar + idle check
        plugin.scheduler().runForRepeating(ent,
                () -> tickInstance(instance),
                () -> despawn(instance, "entity-retired"),
                20L, 20L);

        // Max lifetime hard-stop
        if (maxSec > 0) {
            plugin.scheduler().runForDelayed(ent,
                    () -> {
                        if (!instance.isRemoved()) {
                            broadcastTimeout(instance);
                            despawn(instance, "max-lifetime");
                        }
                    },
                    () -> { },
                    maxSec * 20L);
        }

        // Idle despawn (initial check en cas d'absence total)
        int fallback = plugin.config().idleDespawn();
        int idle = idleSec > 0 ? idleSec : fallback;
        if (idle > 0) {
            plugin.scheduler().runForDelayed(ent,
                    () -> checkIdle(instance, idle),
                    () -> { },
                    idle * 20L);
        }
    }

    private void tickInstance(PinataInstance instance) {
        if (instance.isRemoved() || instance.entity() == null || !instance.entity().isValid()) {
            despawn(instance, "invalid-entity");
            return;
        }
        updateBossBar(instance);
    }

    private void checkIdle(PinataInstance instance, int idleSec) {
        if (instance.isRemoved()) return;
        long since = System.currentTimeMillis() - instance.spawnedAtMs();
        boolean anyRecent = instance.damageTracker().participants().stream()
                .anyMatch(u -> (System.currentTimeMillis() - instance.damageTracker().lastHit(u))
                        < idleSec * 1000L);
        if (!anyRecent && since >= idleSec * 1000L) {
            broadcastTimeout(instance);
            despawn(instance, "idle");
        } else {
            plugin.scheduler().runForDelayed(instance.entity(),
                    () -> checkIdle(instance, idleSec),
                    () -> { },
                    idleSec * 20L);
        }
    }

    private void broadcastTimeout(PinataInstance instance) {
        String raw = instance.type().lifetime().timeoutBroadcast();
        if (raw == null || raw.isEmpty()) return;
        Bukkit.broadcast(MessageUtil.parse(raw, buildEffectPlaceholders(instance)));
    }

    // -------------------------------------------------------------------

    private BossBar.Color mapColor(org.bukkit.boss.BarColor color) {
        return switch (color) {
            case PINK -> BossBar.Color.PINK;
            case BLUE -> BossBar.Color.BLUE;
            case RED -> BossBar.Color.RED;
            case GREEN -> BossBar.Color.GREEN;
            case YELLOW -> BossBar.Color.YELLOW;
            case PURPLE -> BossBar.Color.PURPLE;
            case WHITE -> BossBar.Color.WHITE;
        };
    }

    private BossBar.Overlay mapStyle(org.bukkit.boss.BarStyle style) {
        return switch (style) {
            case SEGMENTED_6 -> BossBar.Overlay.NOTCHED_6;
            case SEGMENTED_10 -> BossBar.Overlay.NOTCHED_10;
            case SEGMENTED_12 -> BossBar.Overlay.NOTCHED_12;
            case SEGMENTED_20 -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.PROGRESS;
        };
    }
}

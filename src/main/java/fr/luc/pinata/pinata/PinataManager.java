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
    private final Map<String, PinataInstance> lastByType = new ConcurrentHashMap<>();

    public PinataManager(PinataPlugin plugin) {
        this.plugin = plugin;
        // Safety-net global : force despawn tous les pinatas qui ont dépassé leurs
        // limites, même si leurs schedulers d'entité ont été retirés (Folia)
        // ou perdus après un /reload. Tourne toutes les 5s sur le region scheduler
        // global — indépendant du cycle de vie de chaque entité.
        plugin.scheduler().globalRepeating(this::safetyNet, 100L, 100L);
    }

    /**
     * Passe sur toutes les instances actives et force le despawn si :
     *  - l'entité est morte ou invalide (chunk unload, mob supprimé) ;
     *  - max-seconds dépassé ;
     *  - idle-seconds dépassé sans hit récent.
     * Ce filet compense les cas où l'entity scheduler Folia retire ses tâches
     * silencieusement (retired callbacks vides sur les tasks max/idle).
     */
    private void safetyNet() {
        long now = System.currentTimeMillis();
        int fallback = plugin.config().idleDespawn();
        for (PinataInstance i : new ArrayList<>(byId.values())) {
            if (i.isRemoved()) continue;
            LivingEntity ent = i.entity();
            if (ent == null || !ent.isValid() || ent.isDead()) {
                despawn(i, "safety-invalid");
                continue;
            }
            int maxSec = i.type().lifetime().maxSeconds();
            long uptimeMs = now - i.spawnedAtMs();
            if (maxSec > 0 && uptimeMs >= maxSec * 1000L) {
                broadcastTimeout(i);
                despawn(i, "safety-max");
                continue;
            }
            int idleSec = i.type().lifetime().idleSeconds();
            int idle = idleSec > 0 ? idleSec : fallback;
            if (idle > 0) {
                Long lastHit = i.damageTracker().participants().stream()
                        .map(u -> i.damageTracker().lastHit(u))
                        .max(Long::compareTo).orElse(null);
                long referenceMs = lastHit != null ? lastHit : i.spawnedAtMs();
                if (now - referenceMs >= idle * 1000L) {
                    broadcastTimeout(i);
                    despawn(i, "safety-idle");
                }
            }
        }
    }

    public int active() { return byId.size(); }
    public Collection<PinataInstance> all() { return Collections.unmodifiableCollection(byId.values()); }

    public PinataInstance byEntity(UUID entityId) { return byEntity.get(entityId); }
    public PinataInstance byId(UUID id) { return byId.get(id); }
    public PinataInstance lastByType(String typeId) { return lastByType.get(typeId.toLowerCase(java.util.Locale.ROOT)); }

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

        // Nettoyer l'ancienne bossbar du même type si elle traîne
        PinataInstance prev = lastByType.get(type.id().toLowerCase(Locale.ROOT));
        if (prev != null && prev.bossBar() != null) {
            hideBossBar(prev);
            prev.setBossBar(null);
        }

        // Boss bar
        if (type.bossBar().enabled()) {
            BossBar bar = BossBar.bossBar(renderBossBarTitle(type, entity),
                    1.0f, mapColor(type.bossBar().color()), mapStyle(type.bossBar().style()));
            instance.setBossBar(bar);
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

        // Start commands
        List<String> startCmds = type.startCommands();
        if (!startCmds.isEmpty()) {
            for (String cmd : startCmds) {
                try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd); }
                catch (Throwable t) { plugin.getLogger().warning("start-command échouée : '" + cmd + "' - " + t.getMessage()); }
            }
        }

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
            entity.addScoreboardTag("nostackall");
            entity.setGravity(type.mob().gravity());

            if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
                entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(type.maxHealth());
                entity.setHealth(type.maxHealth());
            }
            entity.setMaximumNoDamageTicks(type.mob().noDamageTicks());
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
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= r2) {
                p.showBossBar(instance.bossBar());
            } else {
                p.hideBossBar(instance.bossBar());
            }
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
        lastByType.put(instance.type().id().toLowerCase(java.util.Locale.ROOT), instance);
        plugin.cumulativeStats().merge(instance);
        instance.markRemoved();
        byId.remove(instance.id());
        if (instance.entity() != null) byEntity.remove(instance.entity().getUniqueId());

        hideBossBar(instance);

        LivingEntity ent = instance.entity();
        if (ent != null) {
            try { ent.setPersistent(false); } catch (Throwable ignored) { }

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
                    () -> despawn(instance, "entity-retired-max"),
                    maxSec * 20L);
        }

        // Idle despawn (initial check en cas d'absence total)
        int fallback = plugin.config().idleDespawn();
        int idle = idleSec > 0 ? idleSec : fallback;
        if (idle > 0) {
            plugin.scheduler().runForDelayed(ent,
                    () -> checkIdle(instance, idle),
                    () -> despawn(instance, "entity-retired-idle"),
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
                    () -> despawn(instance, "entity-retired-idle-recheck"),
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

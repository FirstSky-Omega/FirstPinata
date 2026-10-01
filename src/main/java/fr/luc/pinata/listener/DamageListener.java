package fr.luc.pinata.listener;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataInstance;
import fr.luc.pinata.pinata.PinataType;
import fr.luc.pinata.util.MessageUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.UUID;

public class DamageListener implements Listener {

    private final PinataPlugin plugin;

    public DamageListener(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    /** Bloque les dégâts non-joueur ET les causes filtrées. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        PinataInstance instance = plugin.pinataManager().byEntity(le.getUniqueId());
        if (instance == null) return;

        PinataType.DamageRules rules = instance.type().damageRules();
        if (rules.blockedCauses().contains(e.getCause().name())) {
            e.setCancelled(true);
            return;
        }
        if (instance.type().mob().invulnerableToNonPlayers()
                && !(e instanceof EntityDamageByEntityEvent)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        PinataInstance instance = plugin.pinataManager().byEntity(le.getUniqueId());
        if (instance == null) return;

        Player attacker = resolvePlayer(e);
        if (attacker == null) {
            if (instance.type().mob().invulnerableToNonPlayers()) e.setCancelled(true);
            return;
        }

        PinataType.DamageRules rules = instance.type().damageRules();

        // Matériau
        ItemStack tool = attacker.getInventory().getItemInMainHand();
        String matName = tool.getType().name().toUpperCase(Locale.ROOT);
        if (!rules.allowedMaterials().isEmpty() && !rules.allowedMaterials().contains(matName)) {
            e.setCancelled(true);
            return;
        }
        if (rules.deniedMaterials().contains(matName)) {
            e.setCancelled(true);
            return;
        }

        // Cooldown
        UUID uid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        long last = instance.damageTracker().lastHit(uid);
        if (rules.cooldownMs() > 0 && (now - last) < rules.cooldownMs()) {
            e.setCancelled(true);
            return;
        }

        // Hit-count system: each valid hit = 1 HP
        double currentHits = instance.damageTracker().get(uid);
        if (rules.perPlayerCap() > 0 && currentHits >= rules.perPlayerCap()) {
            e.setCancelled(true);
            return;
        }

        e.setDamage(1.0);
        le.setNoDamageTicks(0);

        double newTotal = instance.damageTracker().add(uid, 1.0);

        double hpAfter = Math.max(0, le.getHealth() - 1.0);
        plugin.messages().send(attacker, "hit-personal", MessageUtil.placeholders()
                .set("pinata", instance.type().displayName())
                .set("damage", 1)
                .set("hp", (int) hpAfter)
                .set("max_hp", (int) instance.maxHealth())
                .build());

        // Effet
        plugin.pinataManager().playHitEffect(instance, attacker);

        // Extra skills onDamaged
        if (plugin.mythic().isPresent()
                && instance.type().mob().source() == PinataType.MobSource.MYTHIC) {
            plugin.mythic().triggerExtraSkills(instance, le, "onDamaged");
        }

        // Rewards par hit
        plugin.rewardManager().onHit(instance, attacker, 1.0, newTotal);
    }

    private Player resolvePlayer(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }
}

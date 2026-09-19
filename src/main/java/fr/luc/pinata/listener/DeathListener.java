package fr.luc.pinata.listener;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataInstance;
import fr.luc.pinata.pinata.PinataType;
import fr.luc.pinata.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

public class DeathListener implements Listener {

    private final PinataPlugin plugin;

    public DeathListener(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity le = e.getEntity();
        PinataInstance instance = plugin.pinataManager().byEntity(le.getUniqueId());
        if (instance == null) return;

        // Drops vanilla off — les rewards sont pilotées par la config.
        e.getDrops().clear();
        e.setDroppedExp(0);

        // Effet death
        plugin.pinataManager().playDeathEffect(instance);

        // Broadcast
        UUID top = instance.damageTracker().topDamager();
        String topName = top != null && Bukkit.getOfflinePlayer(top).getName() != null
                ? Bukkit.getOfflinePlayer(top).getName() : "-";
        String key = top != null ? "death-broadcast" : "death-no-top";
        plugin.messages().send(Bukkit.getServer(), key, MessageUtil.placeholders()
                .set("pinata", instance.type().displayName())
                .set("top", topName)
                .build());

        // Victoire persistante
        if (top != null) plugin.winsManager().increment(top);

        // Extra Mythic onDeath
        if (plugin.mythic().isPresent()
                && instance.type().mob().source() == PinataType.MobSource.MYTHIC) {
            plugin.mythic().triggerExtraSkills(instance, le, "onDeath");
        }

        // Rewards
        plugin.rewardManager().onDeath(instance);

        plugin.pinataManager().despawn(instance, "killed");
    }
}

package fr.luc.pinata.listener;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataInstance;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Réagit aux évènements MythicMobs. Sert principalement de log/hook :
 * la neutralisation dure des skills natifs se fait plutôt au choix du
 * mythic mob configuré (créer un mob mythic sans skill pour le piñata).
 */
public class MythicListener implements Listener {

    private final PinataPlugin plugin;

    public MythicListener(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicSpawn(MythicMobSpawnEvent e) {
        try {
            ActiveMob am = e.getMob();
            if (am == null) return;
            Entity ent = am.getEntity().getBukkitEntity();
            PinataInstance instance = plugin.pinataManager().byEntity(ent.getUniqueId());
            if (instance == null) return;
            plugin.debug("MythicMob piñata spawné : " + instance.type().id()
                    + " (keep-skills=" + plugin.mythic().shouldKeepSkills(instance.type()) + ")");
        } catch (Throwable t) {
            plugin.getLogger().warning("MythicListener.onMythicSpawn : " + t.getMessage());
        }
    }
}

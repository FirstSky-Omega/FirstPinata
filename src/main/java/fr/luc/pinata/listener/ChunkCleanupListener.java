package fr.luc.pinata.listener;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

public class ChunkCleanupListener implements Listener {

    private final PinataPlugin plugin;

    public ChunkCleanupListener(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (!(entity instanceof LivingEntity le)) continue;
            if (!le.getPersistentDataContainer().has(PinataManager.PINATA_KEY, PersistentDataType.STRING)) continue;
            if (plugin.pinataManager().byEntity(le.getUniqueId()) != null) continue;
            le.remove();
            plugin.debug("Removed orphaned piñata entity " + le.getType() + " at " + le.getLocation());
        }
    }
}

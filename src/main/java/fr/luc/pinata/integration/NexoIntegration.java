package fr.luc.pinata.integration;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import fr.luc.pinata.PinataPlugin;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Bridge Nexo — safe si Nexo n'est pas installé.
 */
public class NexoIntegration {

    private final PinataPlugin plugin;
    private final boolean present;

    public NexoIntegration(PinataPlugin plugin) {
        this.plugin = plugin;
        Plugin p = Bukkit.getPluginManager().getPlugin("Nexo");
        this.present = p != null && p.isEnabled() && plugin.config().nexoEnabled();
        if (present) plugin.getLogger().info("Nexo détecté : intégration items activée.");
    }

    public boolean isPresent() {
        return present;
    }

    public ItemStack buildItem(String nexoId, int amount) {
        if (!present || nexoId == null) return null;
        try {
            ItemBuilder builder = NexoItems.itemFromId(nexoId);
            if (builder == null) return null;
            ItemStack stack = builder.build();
            if (stack == null) return null;
            stack.setAmount(Math.max(1, amount));
            return stack;
        } catch (Throwable t) {
            plugin.getLogger().warning("Nexo item '" + nexoId + "' : " + t.getMessage());
            return null;
        }
    }
}

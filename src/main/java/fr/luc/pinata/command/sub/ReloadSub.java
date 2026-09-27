package fr.luc.pinata.command.sub;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class ReloadSub implements SubCommand {

    private final PinataPlugin plugin;

    public ReloadSub(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "reload"; }
    @Override public String permission() { return "pinata.command.reload"; }
    @Override public String usage() { return "/pinata reload"; }
    @Override public String description() { return "Recharge la configuration"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.messages().send(sender, "reload-start");
        try {
            plugin.reloadAll();
            plugin.messages().send(sender, "reload-success", Map.of(
                    "pinatas", String.valueOf(plugin.pinataConfig().types().size()),
                    "zones", String.valueOf(plugin.zoneManager().all().size())
            ));
        } catch (Exception ex) {
            plugin.messages().send(sender, "reload-error", Map.of("error", ex.getMessage()));
            plugin.getLogger().warning("reload : " + ex);
        }
    }
}

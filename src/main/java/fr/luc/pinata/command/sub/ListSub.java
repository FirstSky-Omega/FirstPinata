package fr.luc.pinata.command.sub;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.command.SubCommand;
import fr.luc.pinata.pinata.PinataInstance;
import fr.luc.pinata.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class ListSub implements SubCommand {

    private final PinataPlugin plugin;

    public ListSub(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "list"; }
    @Override public String permission() { return "pinata.command.list"; }
    @Override public String usage() { return "/pinata list"; }
    @Override public String description() { return "Liste les piñatas actifs"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (plugin.pinataManager().active() == 0) {
            plugin.messages().send(sender, "list-empty");
            return;
        }
        plugin.messages().send(sender, "list-header",
                Map.of("count", String.valueOf(plugin.pinataManager().active())));

        for (PinataInstance i : plugin.pinataManager().all()) {
            Location l = i.entity() != null ? i.entity().getLocation() : i.spawnLocation();
            sender.sendMessage(MessageUtil.parse(plugin.messages().raw("list-entry"),
                    MessageUtil.placeholders()
                            .set("pinata", i.type().displayName())
                            .set("world", l.getWorld().getName())
                            .set("x", l.getBlockX())
                            .set("y", l.getBlockY())
                            .set("z", l.getBlockZ())
                            .set("hp", (int) i.health())
                            .set("max_hp", (int) i.maxHealth())
                            .build()));
        }
    }
}

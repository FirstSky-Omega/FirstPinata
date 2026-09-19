package fr.luc.pinata.command.sub;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.command.SubCommand;
import fr.luc.pinata.pinata.PinataInstance;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StopSub implements SubCommand {

    private final PinataPlugin plugin;

    public StopSub(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "stop"; }
    @Override public String permission() { return "pinata.command.stop"; }
    @Override public String usage() { return "/pinata stop [all|<id>]"; }
    @Override public String description() { return "Retire un ou tous les piñatas"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        int count = plugin.pinataManager().active();
        if (count == 0) {
            plugin.messages().send(sender, "stop-none");
            return;
        }
        if (args.length == 0 || "all".equalsIgnoreCase(args[0])) {
            plugin.pinataManager().despawnAll("command");
            plugin.messages().send(sender, "stop-all-success", Map.of("count", String.valueOf(count)));
            return;
        }
        try {
            UUID id = UUID.fromString(args[0]);
            PinataInstance i = plugin.pinataManager().byId(id);
            if (i == null) return;
            plugin.pinataManager().despawn(i, "command");
            plugin.messages().send(sender, "stop-success", Map.of("pinata", i.type().displayName()));
        } catch (IllegalArgumentException e) {
            // Peut-être un id de type ? on despawn tous les instances de ce type
            for (PinataInstance i : new ArrayList<>(plugin.pinataManager().all())) {
                if (i.type().id().equalsIgnoreCase(args[0])) {
                    plugin.pinataManager().despawn(i, "command");
                }
            }
            plugin.messages().send(sender, "stop-success", Map.of("pinata", args[0]));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("all");
            for (PinataInstance i : plugin.pinataManager().all()) out.add(i.type().id());
        }
        return out;
    }
}

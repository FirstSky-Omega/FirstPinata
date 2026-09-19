package fr.luc.pinata.command.sub;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.command.SubCommand;
import fr.luc.pinata.util.MessageUtil;
import fr.luc.pinata.zone.PinataZone;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ZoneSub implements SubCommand {

    private final PinataPlugin plugin;

    public ZoneSub(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "zone"; }
    @Override public String permission() { return "pinata.command.zone"; }
    @Override public String usage() { return "/pinata zone <create|remove|list> [name]"; }
    @Override public String description() { return "Gère les zones de spawn"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "usage", Map.of("usage", usage()));
            return;
        }
        String action = args[0].toLowerCase();
        try {
            switch (action) {
                case "list" -> {
                    plugin.messages().send(sender, "zone-list-header",
                            Map.of("count", String.valueOf(plugin.zoneManager().all().size())));
                    for (PinataZone z : plugin.zoneManager().all()) {
                        String entry = plugin.messages().raw("zone-list-entry");
                        sender.sendMessage(MessageUtil.parse(entry, Map.of(
                                "zone", z.name(),
                                "world", z.worldName(),
                                "x1", "-", "y1", "-", "z1", "-",
                                "x2", "-", "y2", "-", "z2", "-"
                        )));
                    }
                }
                case "create" -> {
                    if (args.length < 2) {
                        plugin.messages().send(sender, "usage", Map.of("usage", "/pinata zone create <name>"));
                        return;
                    }
                    if (!(sender instanceof Player p)) {
                        plugin.messages().send(sender, "player-only");
                        return;
                    }
                    Location loc = p.getLocation();
                    plugin.zoneManager().createSingle(args[1], loc);
                    plugin.messages().send(sender, "zone-created", Map.of("zone", args[1]));
                }
                case "remove" -> {
                    if (args.length < 2) {
                        plugin.messages().send(sender, "usage", Map.of("usage", "/pinata zone remove <name>"));
                        return;
                    }
                    boolean ok = plugin.zoneManager().remove(args[1]);
                    if (ok) plugin.messages().send(sender, "zone-removed", Map.of("zone", args[1]));
                    else plugin.messages().send(sender, "zone-not-found", Map.of("zone", args[1]));
                }
                default -> plugin.messages().send(sender, "usage", Map.of("usage", usage()));
            }
        } catch (Exception ex) {
            sender.sendMessage(MessageUtil.parse("<red>Erreur zone : " + ex.getMessage()));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("create", "remove", "list")) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && "remove".equalsIgnoreCase(args[0])) {
            for (PinataZone z : plugin.zoneManager().all())
                if (z.name().toLowerCase().startsWith(args[1].toLowerCase())) out.add(z.name());
        }
        return out;
    }
}

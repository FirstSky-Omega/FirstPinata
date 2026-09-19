package fr.luc.pinata.command.sub;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.command.SubCommand;
import fr.luc.pinata.pinata.PinataInstance;
import fr.luc.pinata.pinata.PinataType;
import fr.luc.pinata.util.MessageUtil;
import fr.luc.pinata.zone.PinataZone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpawnSub implements SubCommand {

    private final PinataPlugin plugin;

    public SpawnSub(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "spawn"; }
    @Override public String permission() { return "pinata.command.spawn"; }
    @Override public String usage() { return "/pinata spawn <type> [world x y z | zone:<name>]"; }
    @Override public String description() { return "Fait apparaître un piñata"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            plugin.messages().send(sender, "usage", Map.of("usage", usage()));
            return;
        }
        PinataType type = plugin.pinataConfig().byId(args[0]);
        if (type == null) {
            plugin.messages().send(sender, "spawn-fail-unknown", Map.of("id", args[0]));
            return;
        }

        Location loc = resolveLocation(sender, args);
        if (loc == null) {
            plugin.messages().send(sender, "spawn-fail-location", Map.of("reason", "location invalide"));
            return;
        }

        if (plugin.pinataManager().active() >= plugin.config().maxConcurrent()) {
            plugin.messages().send(sender, "spawn-fail-limit",
                    Map.of("max", String.valueOf(plugin.config().maxConcurrent())));
            return;
        }

        Location fLoc = loc;
        plugin.scheduler().runAt(fLoc, () -> {
            PinataInstance instance = plugin.pinataManager().spawn(type, fLoc);
            if (instance != null) {
                plugin.messages().send(sender, "spawn-success", MessageUtil.placeholders()
                        .set("pinata", type.displayName())
                        .set("world", fLoc.getWorld().getName())
                        .set("x", fLoc.getBlockX())
                        .set("y", fLoc.getBlockY())
                        .set("z", fLoc.getBlockZ())
                        .build());
            }
        });
    }

    private Location resolveLocation(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].toLowerCase().startsWith("zone:")) {
            String zoneName = args[1].substring("zone:".length());
            PinataZone zone = plugin.zoneManager().byName(zoneName);
            if (zone == null) return null;
            return zone.pickLocation();
        }
        if (args.length >= 5) {
            World w = Bukkit.getWorld(args[1]);
            if (w == null) return null;
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                return new Location(w, x, y, z);
            } catch (NumberFormatException e) { return null; }
        }
        if (sender instanceof Player p) return p.getLocation();
        return null;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            for (String id : plugin.pinataConfig().types().keySet()) {
                if (id.startsWith(p)) out.add(id);
            }
        } else if (args.length == 2) {
            String p = args[1].toLowerCase();
            for (World w : Bukkit.getWorlds()) if (w.getName().toLowerCase().startsWith(p)) out.add(w.getName());
            for (var z : plugin.zoneManager().all()) {
                String s = "zone:" + z.name();
                if (s.toLowerCase().startsWith(p)) out.add(s);
            }
        }
        return out;
    }
}

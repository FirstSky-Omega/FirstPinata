package fr.luc.pinata.command;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.command.sub.*;
import fr.luc.pinata.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PinataCommand implements CommandExecutor, TabCompleter {

    private final PinataPlugin plugin;
    private final Map<String, SubCommand> subs = new LinkedHashMap<>();

    public PinataCommand(PinataPlugin plugin) {
        this.plugin = plugin;
        register(new HelpSub(plugin, this));
        register(new SpawnSub(plugin));
        register(new StopSub(plugin));
        register(new ListSub(plugin));
        register(new ReloadSub(plugin));
        register(new InfoSub(plugin));
        register(new ZoneSub(plugin));
    }

    private void register(SubCommand sub) {
        subs.put(sub.name().toLowerCase(Locale.ROOT), sub);
    }

    public Collection<SubCommand> subs() { return subs.values(); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            subs.get("help").execute(sender, args);
            return true;
        }
        SubCommand sub = subs.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            plugin.messages().send(sender, "unknown-subcommand");
            return true;
        }
        if (!sub.permission().isEmpty() && !sender.hasPermission(sub.permission())) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        try {
            sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        } catch (Exception ex) {
            sender.sendMessage(MessageUtil.parse("<red>Erreur : " + ex.getMessage()));
            plugin.getLogger().warning("SubCommand " + sub.name() + " : " + ex);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length <= 1) {
            List<String> out = new ArrayList<>();
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            for (SubCommand sub : subs.values()) {
                if (!sub.permission().isEmpty() && !sender.hasPermission(sub.permission())) continue;
                if (sub.name().startsWith(prefix)) out.add(sub.name());
            }
            return out;
        }
        SubCommand sub = subs.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) return Collections.emptyList();
        return sub.complete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}

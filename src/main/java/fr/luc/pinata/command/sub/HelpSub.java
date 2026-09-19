package fr.luc.pinata.command.sub;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.command.PinataCommand;
import fr.luc.pinata.command.SubCommand;
import fr.luc.pinata.config.MessageConfig;
import fr.luc.pinata.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class HelpSub implements SubCommand {

    private final PinataPlugin plugin;
    private final PinataCommand root;

    public HelpSub(PinataPlugin plugin, PinataCommand root) {
        this.plugin = plugin;
        this.root = root;
    }

    @Override public String name() { return "help"; }
    @Override public String permission() { return "pinata.command.help"; }
    @Override public String usage() { return "/pinata help"; }
    @Override public String description() { return "Affiche l'aide"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Map<String, String> versionPh = new HashMap<>();
        versionPh.put("version", plugin.getPluginMeta().getVersion());

        for (String line : plugin.messages().rawList("help-header")) {
            sender.sendMessage(MessageUtil.parse(line, versionPh));
        }

        String template = plugin.messages().raw("help-entry");
        for (MessageConfig.HelpEntry entry : plugin.messages().getHelpEntries()) {
            if (!entry.permission().isEmpty() && !sender.hasPermission(entry.permission())) continue;
            Map<String, String> ph = new HashMap<>();
            ph.put("cmd", entry.cmd());
            ph.put("args", entry.args());
            ph.put("desc", entry.desc());
            sender.sendMessage(MessageUtil.parse(template, ph));
        }

        for (String line : plugin.messages().rawList("help-footer")) {
            sender.sendMessage(MessageUtil.parse(line));
        }
    }
}

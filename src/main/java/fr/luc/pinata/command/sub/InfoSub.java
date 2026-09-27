package fr.luc.pinata.command.sub;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.command.SubCommand;
import fr.luc.pinata.pinata.PinataType;
import fr.luc.pinata.reward.RewardTier;
import fr.luc.pinata.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InfoSub implements SubCommand {

    private final PinataPlugin plugin;

    public InfoSub(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "info"; }
    @Override public String permission() { return "pinata.command.info"; }
    @Override public String usage() { return "/pinata info <type>"; }
    @Override public String description() { return "Détails d'un type de piñata"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            plugin.messages().send(sender, "usage", Map.of("usage", usage()));
            return;
        }
        PinataType t = plugin.pinataConfig().byId(args[0]);
        if (t == null) {
            plugin.messages().send(sender, "spawn-fail-unknown", Map.of("id", args[0]));
            return;
        }
        sender.sendMessage(MessageUtil.parse("<dark_gray>▎ <white>" + t.displayName()
                + " <dark_gray>(<gray>" + t.id() + "<dark_gray>)"));
        sender.sendMessage(MessageUtil.parse("<gray>  Source : <white>" + t.mob().source()
                + (t.mob().source() == PinataType.MobSource.MYTHIC
                        ? " <dark_gray>(" + t.mob().mythicId() + ")" : " <dark_gray>(" + t.mob().entityType() + ")")));
        sender.sendMessage(MessageUtil.parse("<gray>  HP max : <red>" + (int) t.maxHealth()));
        sender.sendMessage(MessageUtil.parse("<gray>  Lifetime : <white>" + t.lifetime().maxSeconds() + "s"
                + " <dark_gray>| idle <white>" + t.lifetime().idleSeconds() + "s"));
        sender.sendMessage(MessageUtil.parse("<gray>  Rewards :"));
        sender.sendMessage(MessageUtil.parse("<gray>   • hit chance : <white>"
                + String.format("%.2f", t.rewards().hitChancePerHit() * 100) + "%"
                + " <dark_gray>(<white>" + t.rewards().hitRewards().size() + "<dark_gray> entrées)"));
        if (!t.rewards().tiers().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (RewardTier tier : t.rewards().tiers()) names.add(tier.name());
            sender.sendMessage(MessageUtil.parse("<gray>   • tiers : <white>" + String.join(", ", names)));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        return plugin.pinataConfig().types().keySet().stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
    }
}

package fr.luc.pinata.reward;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataInstance;
import fr.luc.pinata.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Interprète les strings d'actions dans les configs.
 *
 * Formats supportés :
 *   console: <cmd>
 *   player: <cmd>
 *   op: <cmd>
 *   message: <text>
 *   broadcast: <text>
 *   title: <title>;<sub>;<in>;<stay>;<out>
 *   actionbar: <text>
 *   sound: <SOUND>,<volume>,<pitch>
 *   give: <material> <amount>
 *   give-nexo: <nexo_id> <amount>
 *   effect: <TYPE> <duration_s> <amplifier>
 *   wait: <ticks>
 */
public class ActionExecutor {

    private final PinataPlugin plugin;

    public ActionExecutor(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(List<String> actions, Player player, PinataInstance instance, Map<String, String> extraPlaceholders) {
        if (actions == null || actions.isEmpty()) return;
        runSequential(actions, 0, player, instance, extraPlaceholders);
    }

    private void runSequential(List<String> actions, int index, Player player,
                               PinataInstance instance, Map<String, String> extra) {
        if (index >= actions.size()) return;
        String raw = actions.get(index);
        String[] split = raw.split(":", 2);
        String verb = split[0].trim().toLowerCase(Locale.ROOT);
        String arg = split.length > 1 ? split[1].trim() : "";

        if ("wait".equals(verb)) {
            long ticks;
            try { ticks = Long.parseLong(arg.trim()); } catch (Exception e) { ticks = 20L; }
            if (player != null && player.isOnline()) {
                plugin.scheduler().runForDelayed(player,
                        () -> runSequential(actions, index + 1, player, instance, extra),
                        () -> runSequential(actions, index + 1, player, instance, extra),
                        ticks);
            } else if (instance != null && instance.entity() != null) {
                plugin.scheduler().runForDelayed(instance.entity(),
                        () -> runSequential(actions, index + 1, player, instance, extra),
                        () -> runSequential(actions, index + 1, player, instance, extra),
                        ticks);
            } else {
                plugin.scheduler().globalDelayed(
                        () -> runSequential(actions, index + 1, player, instance, extra), ticks);
            }
            return;
        }

        executeOne(verb, arg, player, instance, extra);
        runSequential(actions, index + 1, player, instance, extra);
    }

    private void executeOne(String verb, String arg, Player player,
                            PinataInstance instance, Map<String, String> extra) {
        String applied = applyPlaceholders(arg, player, instance, extra);

        switch (verb) {
            case "console" -> runConsole(applied);
            case "player" -> {
                if (player != null && player.isOnline()) {
                    plugin.scheduler().runFor(player,
                            () -> player.performCommand(applied),
                            () -> { });
                }
            }
            case "op" -> {
                if (player != null && player.isOnline()) {
                    plugin.scheduler().runFor(player, () -> {
                        boolean wasOp = player.isOp();
                        try {
                            player.setOp(true);
                            player.performCommand(applied);
                        } finally {
                            player.setOp(wasOp);
                        }
                    }, () -> { });
                }
            }
            case "message" -> {
                if (player != null && player.isOnline()) player.sendMessage(MessageUtil.parse(applied));
            }
            case "broadcast" -> Bukkit.broadcast(MessageUtil.parse(applied));
            case "actionbar" -> {
                if (player != null && player.isOnline()) player.sendActionBar(MessageUtil.parse(applied));
            }
            case "title" -> sendTitle(player, applied);
            case "sound" -> playSound(player, instance, applied);
            case "give" -> giveItem(player, applied);
            case "give-nexo" -> giveNexo(player, applied);
            case "effect" -> giveEffect(player, applied);
            default -> plugin.getLogger().warning("Action inconnue : " + verb + " (" + arg + ")");
        }
    }

    private void runConsole(String cmd) {
        Runnable r = () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        if (plugin.config().rewardAsyncCommands()) {
            plugin.scheduler().async(r);
        } else {
            plugin.scheduler().global(r);
        }
    }

    private void sendTitle(Player player, String arg) {
        if (player == null || !player.isOnline()) return;
        String[] parts = arg.split(";", 5);
        String title = parts.length > 0 ? parts[0] : "";
        String sub   = parts.length > 1 ? parts[1] : "";
        int in   = parts.length > 2 ? parseInt(parts[2], 10) : 10;
        int stay = parts.length > 3 ? parseInt(parts[3], 40) : 40;
        int out  = parts.length > 4 ? parseInt(parts[4], 10) : 10;
        Component t = MessageUtil.parse(title);
        Component s = MessageUtil.parse(sub);
        player.showTitle(net.kyori.adventure.title.Title.title(t, s,
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(in * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(out * 50L))));
    }

    private void playSound(Player player, PinataInstance instance, String arg) {
        String[] parts = arg.split(",");
        String name = parts.length > 0 ? parts[0].trim() : "";
        float volume = parts.length > 1 ? parseFloat(parts[1], 1f) : 1f;
        float pitch  = parts.length > 2 ? parseFloat(parts[2], 1f) : 1f;
        Sound s = fr.luc.pinata.util.SoundUtil.resolve(name);
        if (s == null) return;
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), s, volume, pitch);
        } else if (instance != null && instance.entity() != null) {
            Location loc = instance.entity().getLocation();
            if (loc.getWorld() != null) loc.getWorld().playSound(loc, s, volume, pitch);
        }
    }

    private void giveItem(Player player, String arg) {
        if (player == null || !player.isOnline()) return;
        String[] parts = arg.split("\\s+");
        String matName = parts.length > 0 ? parts[0] : "";
        int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
        if (matName.startsWith("minecraft:")) matName = matName.substring("minecraft:".length());
        Material mat = Material.matchMaterial(matName);
        if (mat == null) return;
        ItemStack stack = new ItemStack(mat, Math.max(1, amount));
        plugin.scheduler().runFor(player, () -> {
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack over : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), over);
            }
        }, () -> { });
    }

    private void giveNexo(Player player, String arg) {
        if (player == null || !player.isOnline() || !plugin.nexo().isPresent()) return;
        String[] parts = arg.split("\\s+");
        String id = parts.length > 0 ? parts[0] : "";
        int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
        ItemStack stack = plugin.nexo().buildItem(id, amount);
        if (stack == null) return;
        plugin.scheduler().runFor(player, () -> {
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack over : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), over);
            }
        }, () -> { });
    }

    @SuppressWarnings("deprecation")
    private void giveEffect(Player player, String arg) {
        if (player == null || !player.isOnline()) return;
        String[] parts = arg.split("\\s+");
        if (parts.length < 1) return;
        PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase(Locale.ROOT));
        if (type == null) return;
        int duration = parts.length > 1 ? parseInt(parts[1], 30) : 30;
        int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : 0;
        PotionEffect effect = new PotionEffect(type, duration * 20, amplifier, true, true);
        plugin.scheduler().runFor(player, () -> player.addPotionEffect(effect), () -> { });
    }

    // -------------------------------------------------------------------

    private String applyPlaceholders(String s, Player player, PinataInstance instance, Map<String, String> extra) {
        if (s == null || s.isEmpty()) return s;
        String out = s;
        if (player != null) {
            out = out.replace("<player>", player.getName())
                     .replace("%player%", player.getName());
        }
        if (instance != null) {
            out = out.replace("<pinata>", instance.type().displayName())
                     .replace("<pinata_id>", instance.type().id())
                     .replace("<hp>", String.valueOf((int) instance.health()))
                     .replace("<max_hp>", String.valueOf((int) instance.maxHealth()));
        }
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                out = out.replace("<" + e.getKey() + ">", e.getValue() == null ? "" : e.getValue());
            }
        }
        // PAPI en dernier pour laisser les placeholders internes (<player>) se
        // résoudre avant les %papi% (qui peuvent référencer le nom du joueur).
        if (plugin.papi() != null && plugin.papi().isPresent()) {
            out = plugin.papi().apply(player, out);
        }
        return out;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static float parseFloat(String s, float def) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return def; }
    }
}

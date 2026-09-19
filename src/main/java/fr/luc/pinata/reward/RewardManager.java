package fr.luc.pinata.reward;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataInstance;
import fr.luc.pinata.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class RewardManager {

    private final PinataPlugin plugin;
    private final Random random = new Random();
    private ActionExecutor executor;

    public RewardManager(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.executor = new ActionExecutor(plugin);
    }

    public ActionExecutor executor() { return executor; }

    /**
     * Appelé quand un joueur inflige un hit valide (après tracking).
     */
    public void onHit(PinataInstance instance, Player player, double damageDealt, double totalPlayerDamage) {
        RewardTable table = instance.type().rewards();
        if (table.hitRewards().isEmpty()) return;
        if (random.nextDouble() > table.hitChancePerHit()) return;

        Reward chosen = pickWeighted(table.hitRewards());
        if (chosen == null) return;

        Map<String, String> ph = MessageUtil.placeholders()
                .set("reward", chosen.name())
                .set("damage", (int) damageDealt)
                .set("total_damage", (int) totalPlayerDamage)
                .build();

        executor.execute(chosen.actions(), player, instance, ph);

        if (plugin.config().rewardHitMessageEnabled()) {
            plugin.messages().send(player, "reward-hit", ph);
        }
    }

    /**
     * Distribue les tiers + participations à la mort.
     */
    public void onDeath(PinataInstance instance) {
        RewardTable table = instance.type().rewards();
        List<Map.Entry<UUID, Double>> ranking = instance.damageTracker().ranking();
        Set<UUID> tierRewarded = new HashSet<>();

        for (RewardTier tier : table.tiers()) {
            for (int rank = tier.minRank(); rank <= tier.maxRank() && rank <= ranking.size(); rank++) {
                Map.Entry<UUID, Double> entry = ranking.get(rank - 1);
                if (entry.getValue() < tier.minDamage()) continue;
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) continue;

                tierRewarded.add(entry.getKey());
                Map<String, String> ph = MessageUtil.placeholders()
                        .set("tier", tier.name())
                        .set("total_damage", (int) (double) entry.getValue())
                        .set("pinata", instance.type().displayName())
                        .build();
                executor.execute(tier.actions(), p, instance, ph);
                plugin.messages().send(p, "reward-tier", ph);

                if (plugin.config().rewardTierBroadcastEnabled()) {
                    Map<String, String> b = new HashMap<>(ph);
                    b.put("top", p.getName());
                    plugin.messages().send(Bukkit.getServer(), "reward-tier-broadcast", b);
                }
            }
        }

        // Participation
        if (!table.participationActions().isEmpty()) {
            for (Map.Entry<UUID, Double> entry : ranking) {
                if (tierRewarded.contains(entry.getKey())) continue;
                if (entry.getValue() < table.participationMinDamage()) continue;
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) continue;

                Map<String, String> ph = MessageUtil.placeholders()
                        .set("total_damage", (int) (double) entry.getValue())
                        .set("pinata", instance.type().displayName())
                        .build();
                executor.execute(table.participationActions(), p, instance, ph);
            }
        }
    }

    private Reward pickWeighted(List<Reward> rewards) {
        double sum = 0;
        for (Reward r : rewards) sum += Math.max(0, r.weight());
        if (sum <= 0) return null;
        double roll = random.nextDouble() * sum;
        double acc = 0;
        for (Reward r : rewards) {
            acc += Math.max(0, r.weight());
            if (roll <= acc) return r;
        }
        return rewards.get(rewards.size() - 1);
    }
}

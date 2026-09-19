package fr.luc.pinata.reward;

import java.util.Collections;
import java.util.List;

/**
 * Toutes les récompenses d'un type de piñata : hit / tiers / participation.
 */
public record RewardTable(
        double hitChancePerHit,
        List<Reward> hitRewards,
        List<RewardTier> tiers,
        double participationMinDamage,
        List<String> participationActions
) {
    public static RewardTable empty() {
        return new RewardTable(0, Collections.emptyList(),
                Collections.emptyList(), 0, Collections.emptyList());
    }
}

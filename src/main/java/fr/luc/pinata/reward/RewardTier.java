package fr.luc.pinata.reward;

import java.util.List;

/**
 * Un tier de récompense attribué en fonction du rang du damager.
 */
public record RewardTier(
        String name,
        int minRank,
        int maxRank,
        double minDamage,
        List<String> actions
) { }

package fr.luc.pinata.reward;

import java.util.List;

/**
 * Une récompense pondérée : nom, poids et actions à exécuter.
 */
public record Reward(String name, double weight, List<String> actions) { }

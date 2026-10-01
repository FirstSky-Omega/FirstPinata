package fr.luc.pinata.pinata;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suit les coups infligés par chaque joueur à un piñata.
 * Thread-safe (Folia peut appeler depuis n'importe quelle région).
 */
public class DamageTracker {

    private final Map<UUID, Double> hits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitMs = new ConcurrentHashMap<>();
    private double total = 0;

    public synchronized double add(UUID player, double amount) {
        double current = hits.getOrDefault(player, 0.0);
        double next = current + amount;
        hits.put(player, next);
        total += amount;
        lastHitMs.put(player, System.currentTimeMillis());
        return next;
    }

    public double get(UUID player) {
        return hits.getOrDefault(player, 0.0);
    }

    public long lastHit(UUID player) {
        return lastHitMs.getOrDefault(player, 0L);
    }

    public double total() {
        return total;
    }

    public Set<UUID> participants() {
        return hits.keySet();
    }

    public List<Map.Entry<UUID, Double>> ranking() {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(hits.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    public UUID topDamager() {
        UUID best = null;
        double max = -1;
        for (Map.Entry<UUID, Double> e : hits.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }
}

package fr.luc.pinata.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;

/**
 * Résolution de Sound compatible avec les nouvelles API Paper 1.26+
 * (Registry keyed) et les noms enum-style historiques utilisés dans les
 * configs (ex. ENTITY_FIREWORK_ROCKET_LAUNCH).
 */
public final class SoundUtil {

    private SoundUtil() { }

    public static Sound resolve(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.trim();

        // 1) Clé namespaced directe : minecraft:entity.firework_rocket.launch
        Sound viaKey = tryKey(s);
        if (viaKey != null) return viaKey;

        // 2) Style path-only : entity.firework_rocket.launch
        Sound viaPath = tryKey("minecraft:" + s.toLowerCase(Locale.ROOT));
        if (viaPath != null) return viaPath;

        // 3) Enum-style : ENTITY_FIREWORK_ROCKET_LAUNCH -> minecraft:entity.firework_rocket.launch
        String enumStyle = s.toUpperCase(Locale.ROOT);
        Sound viaEnum = tryEnumStyleToKey(enumStyle);
        if (viaEnum != null) return viaEnum;

        // 4) Fallback ultime : Sound.valueOf (déprécié mais toujours présent).
        return legacyValueOf(enumStyle);
    }

    private static Sound tryKey(String raw) {
        try {
            NamespacedKey key = NamespacedKey.fromString(raw);
            if (key == null) return null;
            return Registry.SOUNDS.get(key);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Convertit ENTITY_FIREWORK_ROCKET_LAUNCH → minecraft:entity.firework_rocket.launch.
     * On considère que la première catégorie (BLOCK/ENTITY/ITEM/UI/MUSIC/AMBIENT/EVENT/WEATHER)
     * est un composant à part, séparé du reste par un ".".
     */
    private static Sound tryEnumStyleToKey(String enumName) {
        String lower = enumName.toLowerCase(Locale.ROOT);
        int firstUnderscore = lower.indexOf('_');
        if (firstUnderscore > 0) {
            String head = lower.substring(0, firstUnderscore);
            String tail = lower.substring(firstUnderscore + 1);
            Sound s = tryKey("minecraft:" + head + "." + tail);
            if (s != null) return s;
        }
        // Bonus : essai brut avec tous les _ transformés en .
        return tryKey("minecraft:" + lower.replace('_', '.'));
    }

    @SuppressWarnings({ "deprecation", "removal" })
    private static Sound legacyValueOf(String enumName) {
        try {
            return Sound.valueOf(enumName);
        } catch (Throwable t) {
            return null;
        }
    }
}

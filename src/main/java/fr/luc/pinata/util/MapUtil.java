package fr.luc.pinata.util;

import java.util.List;
import java.util.Map;

/**
 * Helpers pour extraire des valeurs typées d'un Map<?, ?> sans se battre
 * avec les captures génériques du compilateur.
 */
public final class MapUtil {

    private MapUtil() { }

    public static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public static int i(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return def;
        try { return Integer.parseInt(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    public static double d(Map<?, ?> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return def;
        try { return Double.parseDouble(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    public static boolean b(Map<?, ?> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v == null) return def;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    public static List<String> strList(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> l) return (List<String>) l;
        return List.of();
    }
}

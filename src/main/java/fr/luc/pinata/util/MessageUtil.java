package fr.luc.pinata.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilitaire MiniMessage : parsing, placeholders et broadcast.
 */
public final class MessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MessageUtil() { }

    public static MiniMessage mm() {
        return MM;
    }

    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return MM.deserialize(raw);
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        TagResolver[] resolvers = toResolvers(placeholders);
        return MM.deserialize(raw, resolvers);
    }

    public static void send(Audience audience, String raw) {
        if (audience == null || raw == null || raw.isEmpty()) return;
        audience.sendMessage(parse(raw));
    }

    public static void send(Audience audience, String raw, Map<String, String> placeholders) {
        if (audience == null || raw == null || raw.isEmpty()) return;
        audience.sendMessage(parse(raw, placeholders));
    }

    public static Placeholders placeholders() {
        return new Placeholders();
    }

    private static TagResolver[] toResolvers(Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return new TagResolver[0];
        TagResolver[] out = new TagResolver[placeholders.size()];
        int i = 0;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out[i++] = Placeholder.parsed(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /** Builder pratique pour la map de placeholders. */
    public static final class Placeholders {
        private final Map<String, String> map = new HashMap<>();

        public Placeholders set(String key, String value) {
            map.put(key, value == null ? "" : value);
            return this;
        }

        public Placeholders set(String key, Object value) {
            map.put(key, value == null ? "" : String.valueOf(value));
            return this;
        }

        public Placeholders merge(Map<String, String> other) {
            if (other != null) map.putAll(other);
            return this;
        }

        public Map<String, String> build() {
            return map;
        }
    }
}

package fr.luc.pinata.schedule;

import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataType;
import fr.luc.pinata.util.MessageUtil;
import fr.luc.pinata.zone.PinataZone;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Charge schedules.yml et déclenche les spawns automatiques.
 * Utilise un tick global (async) de 1s, léger.
 */
public class ScheduleManager {

    private final PinataPlugin plugin;
    private final List<Entry> entries = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long lastTickMs;

    /** Heure locale au tick précédent : sert à détecter le franchissement d'un
     *  horaire fixe même quand le tick est légèrement décalé (server lag). */
    private LocalTime lastTickLocalTime;

    private static final DateTimeFormatter[] TIME_FORMATS = {
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm:ss")
    };

    public ScheduleManager(PinataPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        entries.clear();
        File file = new File(plugin.getDataFolder(), "schedules.yml");
        if (!file.exists()) plugin.saveResource("schedules.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = cfg.getConfigurationSection("schedules");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            try {
                Entry e = parse(key, sec);
                if (e != null) entries.add(e);
            } catch (Exception ex) {
                plugin.getLogger().warning("Schedule '" + key + "' invalide : " + ex.getMessage());
            }
        }
    }

    private Entry parse(String key, ConfigurationSection sec) {
        String pinata = sec.getString("pinata");
        String zone = sec.getString("zone");
        String modeStr = sec.getString("mode", "interval").toLowerCase(Locale.ROOT);
        Entry.Mode mode = switch (modeStr) {
            case "fixed-times" -> Entry.Mode.FIXED_TIMES;
            case "cron" -> Entry.Mode.CRON;
            default -> Entry.Mode.INTERVAL;
        };

        long intervalMs = 0;
        if (mode == Entry.Mode.INTERVAL) intervalMs = parseDuration(sec.getString("interval", "1h"));

        List<LocalTime> times = new ArrayList<>();
        if (mode == Entry.Mode.FIXED_TIMES) {
            for (String s : sec.getStringList("times")) {
                LocalTime parsed = parseTime(s);
                if (parsed != null) {
                    times.add(parsed);
                } else {
                    plugin.getLogger().warning("Schedule '" + key + "' : heure invalide '" + s
                            + "' (format attendu HH:mm ou HH:mm:ss)");
                }
            }
            plugin.debug("Schedule '" + key + "' fixed-times chargé : " + times);
        }

        int minPlayers = sec.getInt("requirements.min-players-online", 0);
        List<String> worldWhitelist = sec.getStringList("requirements.worlds");
        int maxActive = sec.getInt("requirements.max-active", 0);

        boolean announceEnabled = sec.getBoolean("announce.enabled", false);
        List<Integer> countdown = sec.getIntegerList("announce.countdown-seconds");
        String announceMsg = sec.getString("announce.message", "");

        return new Entry(key, pinata, zone, mode, intervalMs, times,
                minPlayers, worldWhitelist, maxActive,
                announceEnabled, countdown, announceMsg);
    }

    public void start() {
        if (running.getAndSet(true)) return;
        lastTickMs = System.currentTimeMillis();
        // Global region : safe pour toucher aux listes serveur (players / mondes).
        plugin.scheduler().globalRepeating(this::tick, 20L, 20L);
    }

    public void stop() {
        running.set(false);
    }

    private void tick() {
        if (!running.get()) return;
        long now = System.currentTimeMillis();
        LocalTime nowLocalTime = LocalTime.now(plugin.config().timezone());
        LocalTime prev = this.lastTickLocalTime;
        for (Entry e : entries) {
            switch (e.mode) {
                case INTERVAL -> tickInterval(e, now);
                case FIXED_TIMES -> tickFixedTimes(e, prev, nowLocalTime);
                case CRON -> { /* TODO : parsing cron */ }
            }
        }
        this.lastTickLocalTime = nowLocalTime;
        this.lastTickMs = now;
    }

    private void tickInterval(Entry e, long now) {
        if (e.intervalMs <= 0) return;
        if (e.nextFireMs == 0) {
            e.nextFireMs = now + e.intervalMs;
            return;
        }
        // Annonce countdown
        if (e.announceEnabled) {
            long remaining = e.nextFireMs - now;
            for (Integer sec : e.countdownSeconds) {
                long target = sec * 1000L;
                if (remaining <= target && (remaining + 1000L) > target) {
                    broadcastCountdown(e, sec);
                }
            }
        }
        if (now >= e.nextFireMs) {
            trySpawn(e);
            e.nextFireMs = now + e.intervalMs;
        }
    }

    private void tickFixedTimes(Entry e, LocalTime prev, LocalTime cur) {
        // Premier tick : on n'a pas de borne inférieure fiable, on sort.
        if (prev == null) return;
        for (LocalTime t : e.times) {
            if (crossedBoundary(prev, cur, t)) {
                plugin.debug("Schedule '" + e.key + "' : horaire " + t + " franchi (prev=" + prev + " cur=" + cur + ")");
                trySpawn(e);
            }
        }
    }

    /**
     * Vrai si l'horaire {@code t} tombe dans l'intervalle (prev, cur].
     * Gère le passage à minuit (prev = 23:59, cur = 00:00).
     */
    private static boolean crossedBoundary(LocalTime prev, LocalTime cur, LocalTime t) {
        if (!prev.isAfter(cur)) {
            // Cas normal : intervalle (prev, cur]
            return prev.isBefore(t) && !cur.isBefore(t);
        }
        // Wrap minuit : intervalle (prev, 24h) ∪ [0h, cur]
        return prev.isBefore(t) || !cur.isBefore(t);
    }

    private static LocalTime parseTime(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        // Retire un éventuel "h" style humain (ex "12h30" → "12:30")
        trimmed = trimmed.replace('h', ':').replace('H', ':');
        if (trimmed.endsWith(":")) trimmed = trimmed + "00";
        for (DateTimeFormatter fmt : TIME_FORMATS) {
            try { return LocalTime.parse(trimmed, fmt); }
            catch (Exception ignored) { }
        }
        return null;
    }

    private void broadcastCountdown(Entry e, int seconds) {
        PinataType type = plugin.pinataConfig().byId(e.pinata);
        String name = type != null ? type.displayName() : e.pinata;
        String msg = e.announceMessage
                .replace("<pinata>", name)
                .replace("<pinata_id>", e.pinata)
                .replace("<time>", humanTime(seconds));
        Component parsed = MessageUtil.parse(msg);
        Bukkit.broadcast(parsed);
    }

    private void trySpawn(Entry e) {
        PinataType type = plugin.pinataConfig().byId(e.pinata);
        PinataZone zone = plugin.zoneManager().byName(e.zone);
        if (type == null || zone == null) {
            plugin.debug("Schedule '" + e.key + "' : pinata ou zone manquant");
            return;
        }
        if (Bukkit.getOnlinePlayers().size() < e.minPlayersOnline) return;
        if (!e.worldWhitelist.isEmpty() && !e.worldWhitelist.contains(zone.worldName())) return;
        if (e.maxActive > 0) {
            long active = plugin.pinataManager().all().stream()
                    .filter(p -> p.type().id().equalsIgnoreCase(type.id())).count();
            if (active >= e.maxActive) return;
        }

        Location loc = zone.pickLocation();
        if (loc == null || loc.getWorld() == null) return;

        plugin.scheduler().runAt(loc, () -> plugin.pinataManager().spawn(type, loc));
    }

    public Collection<Entry> entries() { return entries; }

    // -------------------------------------------------------------------

    private static long parseDuration(String s) {
        if (s == null || s.isEmpty()) return 0;
        s = s.trim().toLowerCase(Locale.ROOT);
        try {
            long mul = 1000L;
            char last = s.charAt(s.length() - 1);
            String num = s;
            if (last == 's') { num = s.substring(0, s.length() - 1); }
            else if (last == 'm') { mul = 60_000L; num = s.substring(0, s.length() - 1); }
            else if (last == 'h') { mul = 3_600_000L; num = s.substring(0, s.length() - 1); }
            else if (last == 'd') { mul = 86_400_000L; num = s.substring(0, s.length() - 1); }
            return (long) (Double.parseDouble(num) * mul);
        } catch (Exception e) {
            return 60_000L;
        }
    }

    private static String humanTime(int seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m" + (seconds % 60 == 0 ? "" : " " + (seconds % 60) + "s");
        return (seconds / 3600) + "h" + ((seconds % 3600) / 60 == 0 ? "" : " " + (seconds % 3600) / 60 + "m");
    }

    public static final class Entry {
        public enum Mode { INTERVAL, FIXED_TIMES, CRON }

        public final String key;
        public final String pinata;
        public final String zone;
        public final Mode mode;
        public final long intervalMs;
        public final List<LocalTime> times;
        public final int minPlayersOnline;
        public final List<String> worldWhitelist;
        public final int maxActive;
        public final boolean announceEnabled;
        public final List<Integer> countdownSeconds;
        public final String announceMessage;

        public long nextFireMs = 0;

        Entry(String key, String pinata, String zone, Mode mode, long intervalMs,
              List<LocalTime> times, int minPlayers, List<String> worldWhitelist, int maxActive,
              boolean announceEnabled, List<Integer> countdown, String announceMsg) {
            this.key = key;
            this.pinata = pinata;
            this.zone = zone;
            this.mode = mode;
            this.intervalMs = intervalMs;
            this.times = times;
            this.minPlayersOnline = minPlayers;
            this.worldWhitelist = worldWhitelist;
            this.maxActive = maxActive;
            this.announceEnabled = announceEnabled;
            this.countdownSeconds = countdown;
            this.announceMessage = announceMsg;
        }
    }
}

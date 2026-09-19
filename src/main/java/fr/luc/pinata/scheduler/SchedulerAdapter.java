package fr.luc.pinata.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Adaptateur Folia-safe : toute planification passe par ici pour rester
 * compatible avec Folia (qui n'a pas de scheduler global synchrone).
 *
 * <p>Si le serveur n'est pas Folia (Paper standard), on retombe sur le
 * BukkitScheduler classique.</p>
 */
public final class SchedulerAdapter {

    private final Plugin plugin;
    private final boolean folia;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    public boolean isFolia() {
        return folia;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    // ----- Region-based (safe pour toucher au monde à une location) -----

    public void runAt(Location loc, Runnable task) {
        if (folia) {
            Bukkit.getRegionScheduler().run(plugin, loc, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runAtDelayed(Location loc, Runnable task, long delayTicks) {
        long ticks = Math.max(1L, delayTicks);
        if (folia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, loc, t -> task.run(), ticks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        }
    }

    public void runAtRepeating(Location loc, Runnable task, long initialTicks, long periodTicks) {
        long init = Math.max(1L, initialTicks);
        long period = Math.max(1L, periodTicks);
        if (folia) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, t -> task.run(), init, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, init, period);
        }
    }

    // ----- Entity-based (safe pour toucher à une entité) -----

    public void runFor(Entity entity, Runnable task, Runnable retired) {
        if (folia) {
            entity.getScheduler().run(plugin, t -> task.run(), retired);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runForDelayed(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        long ticks = Math.max(1L, delayTicks);
        if (folia) {
            entity.getScheduler().runDelayed(plugin, t -> task.run(), retired, ticks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        }
    }

    public void runForRepeating(Entity entity, Runnable task, Runnable retired, long initialTicks, long periodTicks) {
        long init = Math.max(1L, initialTicks);
        long period = Math.max(1L, periodTicks);
        if (folia) {
            entity.getScheduler().runAtFixedRate(plugin, t -> task.run(), retired, init, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, init, period);
        }
    }

    // ----- Async (safe partout) -----

    public void async(Runnable task) {
        if (folia) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public void asyncDelayed(Runnable task, long delayMs) {
        if (folia) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), Math.max(1L, delayMs), TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, Math.max(1L, delayMs / 50L));
        }
    }

    public void asyncRepeating(Runnable task, long initialMs, long periodMs) {
        if (folia) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                    Math.max(1L, initialMs), Math.max(1L, periodMs), TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task,
                    Math.max(1L, initialMs / 50L), Math.max(1L, periodMs / 50L));
        }
    }

    // ----- Global (pour opérations vraiment globales : serveur / listes) -----

    public void global(Runnable task) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void globalDelayed(Runnable task, long delayTicks) {
        long ticks = Math.max(1L, delayTicks);
        if (folia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), ticks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        }
    }

    public void globalRepeating(Runnable task, long initialTicks, long periodTicks) {
        long init = Math.max(1L, initialTicks);
        long period = Math.max(1L, periodTicks);
        if (folia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), init, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, init, period);
        }
    }

    public void cancelAll() {
        if (folia) {
            try {
                Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
                Bukkit.getAsyncScheduler().cancelTasks(plugin);
            } catch (Throwable ignored) { }
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }
}

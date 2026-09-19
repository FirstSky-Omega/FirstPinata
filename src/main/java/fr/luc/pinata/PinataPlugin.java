package fr.luc.pinata;

import fr.luc.pinata.command.PinataCommand;
import fr.luc.pinata.config.ConfigManager;
import fr.luc.pinata.config.MessageConfig;
import fr.luc.pinata.config.PinataConfig;
import fr.luc.pinata.integration.ModelEngineIntegration;
import fr.luc.pinata.integration.MythicMobsIntegration;
import fr.luc.pinata.integration.NexoIntegration;
import fr.luc.pinata.integration.PapiIntegration;
import fr.luc.pinata.listener.DamageListener;
import fr.luc.pinata.listener.DeathListener;
import fr.luc.pinata.listener.MythicListener;
import fr.luc.pinata.pinata.PinataManager;
import fr.luc.pinata.reward.RewardManager;
import fr.luc.pinata.schedule.ScheduleManager;
import fr.luc.pinata.scheduler.SchedulerAdapter;
import fr.luc.pinata.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class PinataPlugin extends JavaPlugin {

    private static PinataPlugin instance;

    private SchedulerAdapter schedulerAdapter;
    private ConfigManager configManager;
    private MessageConfig messageConfig;
    private PinataConfig pinataConfig;
    private ZoneManager zoneManager;
    private ScheduleManager scheduleManager;
    private PinataManager pinataManager;
    private RewardManager rewardManager;

    private MythicMobsIntegration mythicIntegration;
    private NexoIntegration nexoIntegration;
    private ModelEngineIntegration modelEngineIntegration;
    private PapiIntegration papiIntegration;

    public static PinataPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        this.schedulerAdapter = new SchedulerAdapter(this);
        getLogger().info("Scheduler: " + (schedulerAdapter.isFolia() ? "Folia" : "Bukkit/Paper"));

        // 1) Config globale d'abord (utilisée par les intégrations)
        this.configManager = new ConfigManager(this);
        this.configManager.reload();

        // 2) Intégrations soft-depend
        this.mythicIntegration = new MythicMobsIntegration(this);
        this.nexoIntegration = new NexoIntegration(this);
        this.modelEngineIntegration = new ModelEngineIntegration(this);
        this.papiIntegration = new PapiIntegration(this);

        // 3) Managers de contenu
        this.messageConfig = new MessageConfig(this);
        this.pinataConfig = new PinataConfig(this);
        this.zoneManager = new ZoneManager(this);
        this.rewardManager = new RewardManager(this);
        this.pinataManager = new PinataManager(this);
        this.scheduleManager = new ScheduleManager(this);

        reloadAll();

        // Listeners
        Bukkit.getPluginManager().registerEvents(new DamageListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        if (mythicIntegration.isPresent()) {
            Bukkit.getPluginManager().registerEvents(new MythicListener(this), this);
        }

        // Commande
        PluginCommand cmd = getCommand("pinata");
        if (cmd != null) {
            PinataCommand handler = new PinataCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        scheduleManager.start();

        getLogger().info("Plugin activé - " + pinataConfig.types().size() + " types de piñata chargés.");
    }

    @Override
    public void onDisable() {
        if (scheduleManager != null) scheduleManager.stop();
        if (pinataManager != null) pinataManager.despawnAll("plugin-shutdown");
        if (papiIntegration != null) papiIntegration.unregister();
        if (schedulerAdapter != null) schedulerAdapter.cancelAll();
    }

    public void reloadAll() {
        configManager.reload();
        messageConfig.reload(configManager.language(), configManager.prefix());
        pinataConfig.reload();
        zoneManager.reload();
        rewardManager.reload();
        scheduleManager.reload();
    }

    // ----- Accessors -----

    public SchedulerAdapter scheduler() { return schedulerAdapter; }
    public ConfigManager config() { return configManager; }
    public MessageConfig messages() { return messageConfig; }
    public PinataConfig pinataConfig() { return pinataConfig; }
    public ZoneManager zoneManager() { return zoneManager; }
    public ScheduleManager scheduleManager() { return scheduleManager; }
    public PinataManager pinataManager() { return pinataManager; }
    public RewardManager rewardManager() { return rewardManager; }

    public MythicMobsIntegration mythic() { return mythicIntegration; }
    public NexoIntegration nexo() { return nexoIntegration; }
    public ModelEngineIntegration modelEngine() { return modelEngineIntegration; }
    public PapiIntegration papi() { return papiIntegration; }

    public void debug(String msg) {
        if (configManager != null && configManager.debug()) {
            getLogger().info("[DEBUG] " + msg);
        }
    }
}

package fr.luc.pinata.integration;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import fr.luc.pinata.PinataPlugin;
import fr.luc.pinata.pinata.PinataType;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;

/**
 * Bridge ModelEngine (MEG). Safe si le plugin n'est pas installé.
 *
 * <p>N'utilise que l'API stable (createModeledEntity / createActiveModel /
 * addModel). Les setters annexes (scale / visibility / destroy) sont
 * appelés par réflexion pour rester compatibles avec les évolutions
 * d'API entre R4.0.x.</p>
 */
public class ModelEngineIntegration {

    private final PinataPlugin plugin;
    private final boolean present;

    public ModelEngineIntegration(PinataPlugin plugin) {
        this.plugin = plugin;
        Plugin p = Bukkit.getPluginManager().getPlugin("ModelEngine");
        this.present = p != null && p.isEnabled() && plugin.config().modelEngineEnabled();
        if (present) plugin.getLogger().info("ModelEngine détecté : intégration activée.");
    }

    public boolean isPresent() {
        return present;
    }

    public void attachModel(LivingEntity base, PinataType type) {
        if (!present || base == null || type.mob().megModel() == null) return;
        PinataType.ModelEngineModel def = type.mob().megModel();
        try {
            ModeledEntity modeled = ModelEngineAPI.createModeledEntity(base);
            if (modeled == null) {
                plugin.getLogger().warning("MEG : ModeledEntity null pour " + type.id());
                return;
            }
            ActiveModel active = ModelEngineAPI.createActiveModel(def.modelId());
            if (active == null) {
                plugin.getLogger().warning("MEG : model id '" + def.modelId() + "' introuvable.");
                return;
            }

            // Scale (méthode variable selon versions)
            invokeSetter(active, "setScale", float.class, (float) def.scale());
            invokeSetter(active, "setModelScale", float.class, (float) def.scale());

            modeled.addModel(active, true);

            // Masque le mob de base si demandé
            if (plugin.config().modelEngineHideBaseMob()) {
                invokeSetter(modeled, "setBaseEntityVisible", boolean.class, false);
                base.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                        Integer.MAX_VALUE, 0, false, false, false));
                base.setCustomNameVisible(false);
            } else if (def.lockYaw()) {
                invokeSetter(modeled, "setBaseEntityVisible", boolean.class, false);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("MEG.attachModel : " + t.getMessage());
        }
    }

    public void removeModel(LivingEntity base) {
        if (!present || base == null) return;
        try {
            Object modeled = findModeled(base);
            if (modeled == null) return;
            try {
                Method destroy = modeled.getClass().getMethod("destroy");
                destroy.invoke(modeled);
            } catch (NoSuchMethodException ex) {
                // méthode absente, on ignore
            }
        } catch (Throwable ignored) { }
    }

    // -------------------------------------------------------------------

    private static void invokeSetter(Object target, String method, Class<?> paramType, Object value) {
        try {
            Method m = target.getClass().getMethod(method, paramType);
            m.invoke(target, value);
        } catch (Throwable ignored) { }
    }

    /** Récupère un ModeledEntity par UUID via l'API la plus proche. */
    private static Object findModeled(LivingEntity base) {
        try {
            Method m = ModelEngineAPI.class.getMethod("getModeledEntity", java.util.UUID.class);
            return m.invoke(null, base.getUniqueId());
        } catch (Throwable ignored) { }
        try {
            Method m = ModelEngineAPI.class.getMethod("getModeledEntity", org.bukkit.entity.Entity.class);
            return m.invoke(null, base);
        } catch (Throwable ignored) { }
        return null;
    }
}

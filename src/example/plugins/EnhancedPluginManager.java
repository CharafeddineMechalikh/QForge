package example.plugins;

import java.util.*;

import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.plugin.Plugin;
import com.mechalikh.qforge.plugin.PluginManager;

/**
 * A plugin manager that records a per-plugin health entry.
 *
 * <p>Registering a plugin stores a {@link PluginHealth} entry keyed by the plugin name. The
 * overridden initialization notifies each plugin with {@code onSimulationStart} and records
 * whether that call succeeded; registration and notification failures are printed to standard
 * error instead of being propagated. The health map is not synchronized.</p>
 */
public class EnhancedPluginManager extends PluginManager {
    private final Map<String, PluginHealth> pluginHealth;
    
    /**
     * Creates an empty manager.
     */
    public EnhancedPluginManager() {
        super();
        this.pluginHealth = new HashMap<>();
    }
    
    /**
     * Registers a plugin and creates a health entry for it.
     *
     * <p>If the superclass registration throws, the error is printed to standard error and no
     * health entry is added.</p>
     *
     * @param plugin the plugin to register
     */
    @Override
    public void registerPlugin(Plugin plugin) {
        try {
            super.registerPlugin(plugin);
            pluginHealth.put(plugin.getName(), new PluginHealth(plugin.getName()));
        } catch (Exception e) {
            System.err.println("Failed to register plugin " + plugin.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Notifies every registered plugin that a simulation starts and records the outcome.
     *
     * <p>This override calls {@code onSimulationStart} with the given context; it does not call
     * {@code initialize} on the plugin. Successes and failures are counted in the health entry of
     * each plugin.</p>
     *
     * @param context the simulation context passed to each plugin
     */
    @Override
    public void initializePlugins(SimulationContext context) {
        for (String pluginName : listPlugins()) {
            try {
                Plugin plugin = getPlugin(pluginName);
                plugin.onSimulationStart(context);
                pluginHealth.get(pluginName).recordSuccess("initialize");
            } catch (Exception e) {
                pluginHealth.get(pluginName).recordFailure("initialize", e);
                System.err.println("Failed to initialize plugin " + pluginName + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Returns a copy of the health report.
     *
     * <p>The copy prevents changes to the returned map from affecting the manager, but the
     * {@link PluginHealth} values themselves are shared.</p>
     *
     * @return a new map from plugin name to health entry
     */
    public Map<String, PluginHealth> getPluginHealthReport() {
        return new HashMap<>(pluginHealth);
    }
    
    /**
     * Counts successes and failures for one plugin.
     */
    public static class PluginHealth {
        private final String pluginName;
        private int successCount = 0;
        private int failureCount = 0;
        private final List<String> errors = new ArrayList<>();
        
        /**
         * Creates an empty health record.
         *
         * @param pluginName the name of the plugin being tracked
         */
        public PluginHealth(String pluginName) {
            this.pluginName = pluginName;
        }
        
        /**
         * Records one successful operation.
         *
         * @param operation the operation name; it is not stored
         */
        public void recordSuccess(String operation) {
            successCount++;
        }
        
        /**
         * Records one failed operation.
         *
         * @param operation the operation name, stored with the error message
         * @param error the error that was raised
         */
        public void recordFailure(String operation, Exception error) {
            failureCount++;
            errors.add(operation + ": " + error.getMessage());
        }
        
        /**
         * Returns the fraction of recorded operations that succeeded.
         *
         * @return the success count divided by the total, or {@code 1.0} when nothing was recorded
         */
        public double getSuccessRate() {
            int total = successCount + failureCount;
            return total > 0 ? (double) successCount / total : 1.0;
        }
        
        /**
         * Returns a one line summary with the plugin name, both counts, and the success rate.
         *
         * @return the summary string
         */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "PluginHealth[name=%s, success=%d, failures=%d, rate=%.1f%%]", 
                pluginName, successCount, failureCount, getSuccessRate() * 100);
        }
    }
}
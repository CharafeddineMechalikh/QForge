package example.plugins;

import java.util.*;

import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.plugin.Plugin;
import com.quantum.simulator.plugin.PluginManager;

public class EnhancedPluginManager extends PluginManager {
    private final Map<String, PluginHealth> pluginHealth;
    
    public EnhancedPluginManager() {
        super();
        this.pluginHealth = new HashMap<>();
    }
    
    @Override
    public void registerPlugin(Plugin plugin) {
        try {
            super.registerPlugin(plugin);
            pluginHealth.put(plugin.getName(), new PluginHealth(plugin.getName()));
        } catch (Exception e) {
            System.err.println("Failed to register plugin " + plugin.getName() + ": " + e.getMessage());
        }
    }
    
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
    
    public Map<String, PluginHealth> getPluginHealthReport() {
        return new HashMap<>(pluginHealth);
    }
    
    public static class PluginHealth {
        private final String pluginName;
        private int successCount = 0;
        private int failureCount = 0;
        private final List<String> errors = new ArrayList<>();
        
        public PluginHealth(String pluginName) {
            this.pluginName = pluginName;
        }
        
        public void recordSuccess(String operation) {
            successCount++;
        }
        
        public void recordFailure(String operation, Exception error) {
            failureCount++;
            errors.add(operation + ": " + error.getMessage());
        }
        
        public double getSuccessRate() {
            int total = successCount + failureCount;
            return total > 0 ? (double) successCount / total : 1.0;
        }
        
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "PluginHealth[name=%s, success=%d, failures=%d, rate=%.1f%%]", 
                pluginName, successCount, failureCount, getSuccessRate() * 100);
        }
    }
}
package example.plugins;

import com.quantum.simulator.plugin.Plugin;
import com.quantum.simulator.plugin.PluginContext;

public abstract class SafePlugin implements Plugin {
    protected PluginContext context;
    private boolean initialized = false;
    
    @Override
    public final void initialize(PluginContext context) {
        this.context = context;
        try {
            safeInitialize(context);
            initialized = true;
            context.logInfo(getName() + " initialized successfully");
        } catch (Exception e) {
            context.logError("Failed to initialize plugin: " + e.getMessage(), e);
            throw new RuntimeException("Plugin initialization failed: " + getName(), e);
        }
    }
    
    @Override
    public final void cleanup() {
        if (initialized) {
            try {
                safeCleanup();
                if (context != null) {
                    context.logInfo(getName() + " cleaned up successfully");
                }
            } catch (Exception e) {
                if (context != null) {
                    context.logError("Error during plugin cleanup: " + e.getMessage(), e);
                }
            }
        }
    }
    
    protected abstract void safeInitialize(PluginContext context);
    protected abstract void safeCleanup();
    
    protected void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Plugin " + getName() + " not initialized");
        }
    }
}
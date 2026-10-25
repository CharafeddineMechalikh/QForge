package example.plugins;

import com.mechalikh.qforge.plugin.Plugin;
import com.mechalikh.qforge.plugin.PluginContext;

/**
 * Base class for plugins that want a checked lifecycle.
 *
 * <p>{@link #initialize(PluginContext)} and {@link #cleanup()} are final and delegate to the
 * subclass hooks. Initialization records the context, calls {@link #safeInitialize(PluginContext)},
 * marks the plugin initialized, and logs success. If the hook throws, the failure is logged and
 * wrapped in a {@link RuntimeException}. Cleanup runs only when initialization completed, and a
 * cleanup failure is logged but not propagated.</p>
 */
public abstract class SafePlugin implements Plugin {
    protected PluginContext context;
    private boolean initialized = false;
    
    /**
     * Initializes the plugin once through the subclass hook. This method cannot be overridden.
     *
     * @param context the context passed to the plugin
     * @throws RuntimeException if {@link #safeInitialize(PluginContext)} throws
     */
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
    
    /**
     * Cleans the plugin up if initialization succeeded. This method cannot be overridden.
     *
     * <p>An exception from {@link #safeCleanup()} is logged through the stored context and not
     * rethrown. Calling cleanup before a successful initialization does nothing.</p>
     */
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
    
    /**
     * Performs subclass specific initialization.
     *
     * @param context the context passed to the plugin
     */
    protected abstract void safeInitialize(PluginContext context);

    /**
     * Performs subclass specific cleanup.
     */
    protected abstract void safeCleanup();
    
    /**
     * Verifies that the plugin has been initialized.
     *
     * @throws IllegalStateException if initialization has not completed
     */
    protected void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Plugin " + getName() + " not initialized");
        }
    }
}
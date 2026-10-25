package com.mechalikh.qforge.plugin;

import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.events.QuantumEvent;

/**
 * Extension contract for plugins managed by {@link PluginManager}.
 *
 * <p>A plugin is identified by {@link #getName()}, which {@link PluginManager} uses as the
 * registration key. The manager calls {@link #initialize(PluginContext)} when the plugin is
 * loaded, {@link #cleanup()} when it is unloaded, and the optional hook methods during a
 * simulation. Hook implementations may throw exceptions; the manager catches them and reports
 * them to standard error.</p>
 */
public interface Plugin {
    /**
     * Initializes the plugin. Called by {@link PluginManager} after the plugin is instantiated and
     * before it is registered.
     *
     * @param context the context for this plugin, providing its name, version, directory, storage,
     *        and logging helpers
     */
    void initialize(PluginContext context);
    
    /**
     * Releases resources held by the plugin. Called by
     * {@link PluginManager#unloadPlugin(String)} before the plugin is removed from the registry.
     */
    void cleanup();
    
    /**
     * Returns the plugin name used as the registration key.
     *
     * <p>Names must be unique among loaded plugins; the manager rejects duplicates.</p>
     *
     * @return the plugin name
     */
    String getName();
    
    /**
     * Returns the plugin version.
     *
     * @return the plugin version
     */
    String getVersion();
    
    /**
     * Returns a short description of the plugin.
     *
     * @return the plugin description
     */
    String getDescription();
    
    /**
     * Called by {@link PluginManager#initializePlugins} at the start of a simulation.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param context the simulation context
     */
    default void onSimulationStart(SimulationContext context) {}
    
    /**
     * Called by {@link PluginManager#cleanupPlugins} at the end of a simulation.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param context the simulation context
     */
    default void onSimulationEnd(SimulationContext context) {}
    
    /**
     * Called by {@link PluginManager#notifyStateUpdate} when a state update occurs.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param state the current state
     * @param event the event associated with the update
     */
    default void onStateUpdate(QuantumState state, QuantumEvent event) {}
    
    /**
     * Called by {@link PluginManager#notifyEventScheduled} when an event is scheduled.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param event the scheduled event
     */
    default void onEventScheduled(QuantumEvent event) {}
    
    /**
     * Called by {@link PluginManager#notifyOperationApplied} after an operation is applied.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param operation the applied operation
     */
    default void onOperationApplied(QuantumOperation operation) {}
    
    /**
     * Called by {@link PluginManager#notifyError} when an error is reported.
     *
     * <p>The default implementation does nothing.</p>
     *
     * @param errorMessage the error message
     * @param error the error cause
     */
    default void onError(String errorMessage, Throwable error) {}
    
    /**
     * Returns whether the plugin provides the named extension point.
     *
     * <p>The default implementation returns {@code false}.</p>
     *
     * @param extensionPoint the extension point name
     * @return {@code true} if the plugin supports the extension point
     */
    default boolean supportsExtension(String extensionPoint) {
        return false;
    }
    
    /**
     * Returns the object for the named extension point.
     *
     * <p>The default implementation returns {@code null}.</p>
     *
     * @param extensionPoint the extension point name
     * @return the extension object, or {@code null} when the plugin does not provide it
     */
    default Object getExtension(String extensionPoint) {
        return null;
    }
 
}



package com.mechalikh.qforge.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-plugin context passed to {@link Plugin#initialize(PluginContext)}.
 *
 * <p>The context exposes the plugin name, version, and directory, a property map, a general
 * purpose data map, and logging helpers. The maps are ordinary {@link HashMap} instances created
 * for each context.</p>
 */
public class PluginContext {
    private final String pluginName;
    private final String pluginVersion;
    private final String pluginDirectory;
    private final Map<String, Object> properties;
    private final Map<String, Object> sharedData;
    
    /**
     * Creates a context for one plugin.
     *
     * @param pluginName the plugin name, may be {@code null}
     * @param pluginVersion the plugin version, may be {@code null}
     * @param pluginDirectory the plugin directory, may be {@code null}
     */
    public PluginContext(String pluginName, String pluginVersion, String pluginDirectory) {
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
        this.pluginDirectory = pluginDirectory;
        this.properties = new HashMap<>();
        this.sharedData = new HashMap<>();
    }
    
    /**
     * Returns the plugin name.
     *
     * @return the plugin name
     */
    public String getPluginName() {
        return pluginName;
    }
    
    /**
     * Returns the plugin version.
     *
     * @return the plugin version
     */
    public String getPluginVersion() {
        return pluginVersion;
    }
    
    /**
     * Returns the plugin directory.
     *
     * @return the plugin directory
     */
    public String getPluginDirectory() {
        return pluginDirectory;
    }
    
    /**
     * Resolves a path relative to the plugin directory.
     *
     * <p>No check is made that the file exists.</p>
     *
     * @param relativePath the path relative to the plugin directory
     * @return a {@link File} for the resolved path
     */
    public File getPluginFile(String relativePath) {
        return new File(pluginDirectory, relativePath);
    }
    
    /**
     * Stores a property in this context, replacing any previous value for the key.
     *
     * @param key the property key
     * @param value the property value, may be {@code null}
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    /**
     * Returns a property value.
     *
     * @param key the property key
     * @return the value, or {@code null} when the key is absent
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * Returns a property value cast to the requested type.
     *
     * @param <T> the expected value type
     * @param key the property key
     * @param type the expected value class
     * @return the value when it is an instance of {@code type}, otherwise {@code null}
     */
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }
    
    /**
     * Stores a value in the data map, replacing any previous value for the key.
     *
     * @param key the data key
     * @param value the value, may be {@code null}
     */
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }
    
    /**
     * Returns a shared data value.
     *
     * @param key the data key
     * @return the value, or {@code null} when the key is absent
     */
    public Object getSharedData(String key) {
        return sharedData.get(key);
    }
    
    /**
     * Returns a copy of all shared data entries.
     *
     * @return a new mutable map containing the current entries
     */
    public Map<String, Object> getSharedData() {
        return new HashMap<>(sharedData);
    }
    
    /**
     * Writes an informational message to standard output, prefixed with the plugin name.
     *
     * @param message the message to log
     */
    public void logInfo(String message) {
        System.out.println("[Plugin:" + pluginName + "] INFO: " + message);
    }
    
    /**
     * Writes a warning message to standard output, prefixed with the plugin name.
     *
     * @param message the message to log
     */
    public void logWarning(String message) {
        System.out.println("[Plugin:" + pluginName + "] WARNING: " + message);
    }
    
    /**
     * Writes an error message to standard error, prefixed with the plugin name.
     *
     * @param message the message to log
     */
    public void logError(String message) {
        System.err.println("[Plugin:" + pluginName + "] ERROR: " + message);
    }
    
    /**
     * Writes an error message to standard error and prints the stack trace of the error.
     *
     * @param message the message to log
     * @param error the error whose stack trace is printed, may be {@code null}
     */
    public void logError(String message, Throwable error) {
        System.err.println("[Plugin:" + pluginName + "] ERROR: " + message);
        if (error != null) {
            error.printStackTrace();
        }
    }
}
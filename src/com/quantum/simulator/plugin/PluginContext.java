
package com.quantum.simulator.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PluginContext {
    private final String pluginName;
    private final String pluginVersion;
    private final String pluginDirectory;
    private final Map<String, Object> properties;
    private final Map<String, Object> sharedData;
    
    public PluginContext(String pluginName, String pluginVersion, String pluginDirectory) {
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
        this.pluginDirectory = pluginDirectory;
        this.properties = new HashMap<>();
        this.sharedData = new HashMap<>();
    }
    
    public String getPluginName() {
        return pluginName;
    }
    
    public String getPluginVersion() {
        return pluginVersion;
    }
    
    public String getPluginDirectory() {
        return pluginDirectory;
    }
    
    public File getPluginFile(String relativePath) {
        return new File(pluginDirectory, relativePath);
    }
    
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }
    
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }
    
    public Object getSharedData(String key) {
        return sharedData.get(key);
    }
    
    public Map<String, Object> getSharedData() {
        return new HashMap<>(sharedData);
    }
    
    public void logInfo(String message) {
        System.out.println("[Plugin:" + pluginName + "] INFO: " + message);
    }
    
    public void logWarning(String message) {
        System.out.println("[Plugin:" + pluginName + "] WARNING: " + message);
    }
    
    public void logError(String message) {
        System.err.println("[Plugin:" + pluginName + "] ERROR: " + message);
    }
    
    public void logError(String message, Throwable error) {
        System.err.println("[Plugin:" + pluginName + "] ERROR: " + message);
        if (error != null) {
            error.printStackTrace();
        }
    }
}
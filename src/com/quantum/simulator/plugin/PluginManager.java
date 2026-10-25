package com.quantum.simulator.plugin;

import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.QuantumState;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.lang.reflect.Constructor;

public class PluginManager {
    private final Map<String, Plugin> loadedPlugins;
    private final List<String> pluginPaths;
    private final List<PluginListener> listeners;
    private ClassLoader pluginClassLoader;
    
    public PluginManager() {
        this.loadedPlugins = new HashMap<>();
        this.pluginPaths = new ArrayList<>();
        this.listeners = new ArrayList<>();
        this.pluginClassLoader = Thread.currentThread().getContextClassLoader();
    }
    
    public boolean loadPlugin(String pluginPath) throws PluginException {
        File pluginFile = new File(pluginPath);
        if (!pluginFile.exists()) {
            throw new PluginException("Plugin file not found: " + pluginPath);
        }
        
        try {
            if (pluginFile.isDirectory()) {
                return loadDirectoryPlugin(pluginFile);
            } else if (pluginFile.getName().endsWith(".jar")) {
                return loadJarPlugin(pluginFile);
            } else {
                throw new PluginException("Unsupported plugin format: " + pluginPath);
            }
        } catch (Exception e) {
            throw new PluginException("Failed to load plugin: " + pluginPath, e);
        }
    }
    
    private boolean loadJarPlugin(File jarFile) throws PluginException {
        try (JarFile jar = new JarFile(jarFile)) {
            // Read plugin metadata
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                throw new PluginException("Plugin JAR has no manifest: " + jarFile.getAbsolutePath());
            }
            Attributes attributes = manifest.getMainAttributes();
            String pluginClass = attributes.getValue("Plugin-Class");
            String pluginName = attributes.getValue("Plugin-Name");
            String pluginVersion = attributes.getValue("Plugin-Version");
            
            if (pluginClass == null) {
                throw new PluginException("No Plugin-Class attribute in manifest");
            }
            
            // Create class loader for this plugin
            URL[] urls = { jarFile.toURI().toURL() };
            URLClassLoader classLoader = new URLClassLoader(urls, pluginClassLoader);
            
            // Load plugin class
            Class<?> clazz = classLoader.loadClass(pluginClass);
            if (!Plugin.class.isAssignableFrom(clazz)) {
                throw new PluginException("Plugin class does not implement Plugin interface: " + pluginClass);
            }
            
            // Instantiate plugin
            Constructor<?> constructor = clazz.getConstructor();
            Plugin plugin = (Plugin) constructor.newInstance();
            
            // Initialize plugin
            plugin.initialize(new PluginContext(pluginName, pluginVersion, jarFile.getParent()));
            
            // Register plugin
            registerLoadedPlugin(plugin);
            
            return true;
            
        } catch (Exception e) {
            throw new PluginException("Failed to load JAR plugin: " + jarFile.getAbsolutePath(), e);
        }
    }
    
    private boolean loadDirectoryPlugin(File directory) throws PluginException {
        // Look for plugin properties file
        File propertiesFile = new File(directory, "plugin.properties");
        if (!propertiesFile.exists()) {
            throw new PluginException("No plugin.properties found in directory: " + directory);
        }
        
        try {
            Properties props = new Properties();
            try (InputStream input = new FileInputStream(propertiesFile)) {
                props.load(input);
            }
            
            String pluginClass = props.getProperty("plugin.class");
            String pluginName = props.getProperty("plugin.name", directory.getName());
            String pluginVersion = props.getProperty("plugin.version", "1.0.0");
            
            if (pluginClass == null) {
                throw new PluginException("No plugin.class property found");
            }
            
            // Add directory to classpath
            URL[] urls = { directory.toURI().toURL() };
            URLClassLoader classLoader = new URLClassLoader(urls, pluginClassLoader);
            
            // Load plugin class
            Class<?> clazz = classLoader.loadClass(pluginClass);
            if (!Plugin.class.isAssignableFrom(clazz)) {
                throw new PluginException("Plugin class does not implement Plugin interface: " + pluginClass);
            }
            
            // Instantiate plugin
            Constructor<?> constructor = clazz.getConstructor();
            Plugin plugin = (Plugin) constructor.newInstance();
            
            // Initialize plugin
            plugin.initialize(new PluginContext(pluginName, pluginVersion, directory.getAbsolutePath()));
            
            // Register plugin
            registerLoadedPlugin(plugin);
            
            return true;
            
        } catch (Exception e) {
            throw new PluginException("Failed to load directory plugin: " + directory.getAbsolutePath(), e);
        }
    }
    
    public void registerPlugin(Plugin plugin) {
        plugin.initialize(new PluginContext(plugin.getName(), plugin.getVersion(), ""));
        registerLoadedPlugin(plugin);
    }
    
    private void registerLoadedPlugin(Plugin plugin) {
        String pluginName = plugin.getName();
        
        if (loadedPlugins.containsKey(pluginName)) {
            throw new IllegalStateException("Plugin already registered: " + pluginName);
        }
        
        loadedPlugins.put(pluginName, plugin);
        
        ProtectionDomain domain = plugin.getClass().getProtectionDomain();
        if (domain != null && domain.getCodeSource() != null
            && domain.getCodeSource().getLocation() != null) {
            pluginPaths.add(domain.getCodeSource().getLocation().getPath());
        }
        
        // Notify listeners
        for (PluginListener listener : listeners) {
            listener.onPluginLoaded(plugin);
        }
    }
    
    public boolean unloadPlugin(String pluginName) {
        Plugin plugin = loadedPlugins.get(pluginName);
        if (plugin == null) {
            return false;
        }
        
        try {
            plugin.cleanup();
            loadedPlugins.remove(pluginName);
            
            // Notify listeners
            for (PluginListener listener : listeners) {
                listener.onPluginUnloaded(plugin);
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error unloading plugin " + pluginName + ": " + e.getMessage());
            return false;
        }
    }
     
    public void unloadAll() {
        List<String> pluginNames = new ArrayList<>(loadedPlugins.keySet());
        for (String pluginName : pluginNames) {
            unloadPlugin(pluginName);
        }
    }
    
    public Plugin getPlugin(String name) {
        return loadedPlugins.get(name);
    }
    
    public <T extends Plugin> T getPlugin(String name, Class<T> pluginClass) {
        Plugin plugin = loadedPlugins.get(name);
        if (plugin != null && pluginClass.isInstance(plugin)) {
            return pluginClass.cast(plugin);
        }
        return null;
    }
    
    public List<String> listPlugins() {
        return new ArrayList<>(loadedPlugins.keySet());
    }
    
    public List<Plugin> getPluginsByType(Class<? extends Plugin> pluginType) {
        List<Plugin> filtered = new ArrayList<>();
        for (Plugin plugin : loadedPlugins.values()) {
            if (pluginType.isInstance(plugin)) {
                filtered.add(plugin);
            }
        }
        return filtered;
    }
    
    public void addPluginListener(PluginListener listener) {
        listeners.add(listener);
    }
    
    public void removePluginListener(PluginListener listener) {
        listeners.remove(listener);
    }
    
    public void initializePlugins(SimulationContext context) {
        for (Plugin plugin : loadedPlugins.values()) {
            try { 
                plugin.onSimulationStart(context);
            } catch (Exception e) {
                System.err.println("Error initializing plugin " + plugin.getName() + ": " + e.getMessage());
            }
        }
    }
    
    public void cleanupPlugins(SimulationContext context) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onSimulationEnd(context);
            } catch (Exception e) {
                System.err.println("Error cleaning up plugin " + plugin.getName() + ": " + e.getMessage());
            }
        }
    }
    
    public void setPluginClassLoader(ClassLoader classLoader) {
        this.pluginClassLoader = classLoader;
    }
    
    public void notifyEventScheduled(QuantumEvent event) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onEventScheduled(event);
            } catch (Exception e) {
                reportPluginError(plugin, e);
            }
        }
    }
    
    public void notifyStateUpdate(QuantumState state, QuantumEvent event) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onStateUpdate(state, event);
            } catch (Exception e) {
                reportPluginError(plugin, e);
            }
        }
    }
    
    public void notifyOperationApplied(QuantumOperation operation) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onOperationApplied(operation);
            } catch (Exception e) {
                reportPluginError(plugin, e);
            }
        }
    }
    
    public void notifyError(String errorMessage, Throwable error) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onError(errorMessage, error);
            } catch (Exception e) {
                reportPluginError(plugin, e);
            }
        }
    }
    
    private void reportPluginError(Plugin plugin, Exception error) {
        System.err.println("Error in plugin " + plugin.getName() + ": " + error.getMessage());
    }
    
    // Exception class
    public static class PluginException extends Exception {
        public PluginException(String message) {
            super(message);
        }
        
        public PluginException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Listener interface
    public interface PluginListener {
        void onPluginLoaded(Plugin plugin);
        void onPluginUnloaded(Plugin plugin);
    }
}
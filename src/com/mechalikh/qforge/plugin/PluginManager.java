package com.mechalikh.qforge.plugin;

import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.state.QuantumState;
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

/**
 * Loads, registers, unloads, and notifies plugins.
 *
 * <p>Plugins can be loaded from a JAR file or from a directory and are stored by
 * {@link Plugin#getName()} in a map. Loading creates a {@link java.net.URLClassLoader} whose parent
 * is the configured plugin class loader, instantiates the declared plugin class through its no-arg
 * constructor, calls {@link Plugin#initialize(PluginContext)}, and registers the result.</p>
 *
 * <p>Notifications iterate over the currently registered plugins. Exceptions thrown by plugin
 * hooks are caught and reported to standard error; they do not propagate to the caller.</p>
 */
public class PluginManager {
    private final Map<String, Plugin> loadedPlugins;
    private final List<String> pluginPaths;
    private final List<PluginListener> listeners;
    private ClassLoader pluginClassLoader;
    
    /**
     * Creates an empty manager.
     *
     * <p>The plugin class loader is initialized to the current thread context class loader.</p>
     */
    public PluginManager() {
        this.loadedPlugins = new HashMap<>();
        this.pluginPaths = new ArrayList<>();
        this.listeners = new ArrayList<>();
        this.pluginClassLoader = Thread.currentThread().getContextClassLoader();
    }
    
    /**
     * Loads and registers a plugin from a JAR file or a directory.
     *
     * <p>A directory must contain {@code plugin.properties} with a {@code plugin.class} entry. The
     * optional {@code plugin.name} entry defaults to the directory name, and
     * {@code plugin.version} defaults to {@code "1.0.0"}. A JAR must have a manifest with a
     * {@code Plugin-Class} entry; the optional {@code Plugin-Name} and {@code Plugin-Version}
     * entries are passed to the context unchanged.</p>
     *
     * @param pluginPath the path to a JAR file or a plugin directory
     * @return {@code true} when the plugin was loaded and registered; failures are reported as
     *         exceptions rather than a {@code false} result
     * @throws PluginException if the path does not exist, the format is unsupported, required
     *         metadata is missing, the class cannot be instantiated or does not implement
     *         {@link Plugin}, or registration fails
     */
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
            boolean registered = false;
            try {
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
                registered = true;

                return true;
            } finally {
                if (!registered) {
                    try {
                        classLoader.close();
                    } catch (IOException e) {
                        // The loader is being discarded, so a close failure has no further effect.
                    }
                }
            }
            
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
            boolean registered = false;
            try {
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
                registered = true;

                return true;
            } finally {
                if (!registered) {
                    try {
                        classLoader.close();
                    } catch (IOException e) {
                        // The loader is being discarded, so a close failure has no further effect.
                    }
                }
            }
            
        } catch (Exception e) {
            throw new PluginException("Failed to load directory plugin: " + directory.getAbsolutePath(), e);
        }
    }
    
    /**
     * Initializes and registers an already constructed plugin.
     *
     * <p>The plugin receives a context built from {@link Plugin#getName()},
     * {@link Plugin#getVersion()}, and an empty directory string.</p>
     *
     * @param plugin the plugin to register
     * @throws IllegalStateException if a plugin with the same name is already registered
     */
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
    
    /**
     * Unloads a plugin by calling {@link Plugin#cleanup()} and removing it from the registry.
     *
     * @param pluginName the registered plugin name
     * @return {@code true} when the plugin was found, cleaned up, and removed; {@code false} when
     *         no plugin has the name or cleanup failed
     */
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
     
    /**
     * Unloads every registered plugin, one at a time.
     *
     * <p>Failures are handled as in {@link #unloadPlugin(String)}.</p>
     */
    public void unloadAll() {
        List<String> pluginNames = new ArrayList<>(loadedPlugins.keySet());
        for (String pluginName : pluginNames) {
            unloadPlugin(pluginName);
        }
    }
    
    /**
     * Returns the registered plugin with the given name.
     *
     * @param name the plugin name
     * @return the plugin, or {@code null} when no plugin has the name
     */
    public Plugin getPlugin(String name) {
        return loadedPlugins.get(name);
    }
    
    /**
     * Returns the registered plugin with the given name when it has the expected type.
     *
     * @param <T> the expected plugin type
     * @param name the plugin name
     * @param pluginClass the expected plugin class
     * @return the plugin cast to {@code pluginClass}, or {@code null} when it is absent or has
     *         another type
     */
    public <T extends Plugin> T getPlugin(String name, Class<T> pluginClass) {
        Plugin plugin = loadedPlugins.get(name);
        if (plugin != null && pluginClass.isInstance(plugin)) {
            return pluginClass.cast(plugin);
        }
        return null;
    }
    
    /**
     * Returns the names of all registered plugins.
     *
     * @return a new list of plugin names, in unspecified order
     */
    public List<String> listPlugins() {
        return new ArrayList<>(loadedPlugins.keySet());
    }
    
    /**
     * Returns the registered plugins that are instances of the given type.
     *
     * @param pluginType the type to match
     * @return a new list of matching plugins, in unspecified order
     */
    public List<Plugin> getPluginsByType(Class<? extends Plugin> pluginType) {
        List<Plugin> filtered = new ArrayList<>();
        for (Plugin plugin : loadedPlugins.values()) {
            if (pluginType.isInstance(plugin)) {
                filtered.add(plugin);
            }
        }
        return filtered;
    }
    
    /**
     * Adds a listener that is notified when plugins are loaded and unloaded.
     *
     * @param listener the listener to add
     */
    public void addPluginListener(PluginListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes one occurrence of the listener from the notification list.
     *
     * @param listener the listener to remove
     */
    public void removePluginListener(PluginListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Calls {@link Plugin#onSimulationStart(SimulationContext)} on every registered plugin.
     *
     * <p>Exceptions thrown by a hook are caught and reported to standard error, and processing
     * continues with the next plugin.</p>
     *
     * @param context the simulation context
     */
    public void initializePlugins(SimulationContext context) {
        for (Plugin plugin : loadedPlugins.values()) {
            try { 
                plugin.onSimulationStart(context);
            } catch (Exception e) {
                System.err.println("Error initializing plugin " + plugin.getName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Calls {@link Plugin#onSimulationEnd(SimulationContext)} on every registered plugin.
     *
     * <p>Exceptions thrown by a hook are caught and reported to standard error, and processing
     * continues with the next plugin.</p>
     *
     * @param context the simulation context
     */
    public void cleanupPlugins(SimulationContext context) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onSimulationEnd(context);
            } catch (Exception e) {
                System.err.println("Error cleaning up plugin " + plugin.getName() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Sets the parent class loader used for plugins loaded afterwards.
     *
     * @param classLoader the parent class loader
     */
    public void setPluginClassLoader(ClassLoader classLoader) {
        this.pluginClassLoader = classLoader;
    }
    
    /**
     * Calls {@link Plugin#onEventScheduled(QuantumEvent)} on every registered plugin.
     *
     * @param event the scheduled event
     */
    public void notifyEventScheduled(QuantumEvent event) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onEventScheduled(event);
            } catch (Exception e) {
                reportPluginError(plugin, e);
            }
        }
    }
    
    /**
     * Calls {@link Plugin#onStateUpdate(QuantumState, QuantumEvent)} on every registered plugin.
     *
     * @param state the current state
     * @param event the event associated with the update
     */
    public void notifyStateUpdate(QuantumState state, QuantumEvent event) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onStateUpdate(state, event);
            } catch (Exception e) {
                reportPluginError(plugin, e);
            }
        }
    }
    
    /**
     * Calls {@link Plugin#onOperationApplied(QuantumOperation)} on every registered plugin.
     *
     * @param operation the applied operation
     */
    public void notifyOperationApplied(QuantumOperation operation) {
        for (Plugin plugin : loadedPlugins.values()) {
            try {
                plugin.onOperationApplied(operation);
            } catch (Exception e) {
                reportPluginError(plugin, e);
            }
        }
    }
    
    /**
     * Calls {@link Plugin#onError(String, Throwable)} on every registered plugin.
     *
     * @param errorMessage the error message
     * @param error the error cause
     */
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
    
    /**
     * Checked exception raised when a plugin cannot be loaded.
     */
    public static class PluginException extends Exception {
        /**
         * Creates an exception with the given message.
         *
         * @param message the detail message
         */
        public PluginException(String message) {
            super(message);
        }
        
        /**
         * Creates an exception with the given message and cause.
         *
         * @param message the detail message
         * @param cause the underlying cause
         */
        public PluginException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Listener for plugin registration changes.
     */
    public interface PluginListener {
        /**
         * Called after a plugin has been registered.
         *
         * @param plugin the registered plugin
         */
        void onPluginLoaded(Plugin plugin);

        /**
         * Called after a plugin has been removed from the registry.
         *
         * @param plugin the unloaded plugin
         */
        void onPluginUnloaded(Plugin plugin);
    }
}
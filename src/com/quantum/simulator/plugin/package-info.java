/**
 * Plugin API and plugin management.
 *
 * <p>{@link Plugin} defines the lifecycle and simulation hook methods. {@link PluginManager} loads
 * plugins from JAR files or directories, registers them by name, and forwards notifications.
 * {@link PluginContext} carries per-plugin metadata, storage, and logging helpers.</p>
 */
package com.quantum.simulator.plugin;

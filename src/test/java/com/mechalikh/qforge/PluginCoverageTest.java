package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.plugin.Plugin;
import com.mechalikh.qforge.plugin.PluginContext;
import com.mechalikh.qforge.plugin.PluginManager;
import com.mechalikh.qforge.plugin.PluginManager.PluginException;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.StateVector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginCoverageTest {

    @BeforeEach
    void resetPluginStatics() {
        DirPlugin.context = null;
        DirPlugin.cleanups = 0;
        JarPlugin.context = null;
        JarPlugin.cleanups = 0;
    }

    @Test
    void pluginContextExposesMetadataPropertiesAndData() {
        PluginContext context = new PluginContext("demo", "2.0", "base");
        assertEquals("demo", context.getPluginName());
        assertEquals("2.0", context.getPluginVersion());
        assertEquals("base", context.getPluginDirectory());
        assertTrue(context.getPluginFile("data.txt").getPath().endsWith("data.txt"));

        context.setProperty("count", 5);
        assertEquals(5, context.getProperty("count"));
        assertEquals(Integer.valueOf(5), context.getProperty("count", Integer.class));
        assertNull(context.getProperty("count", String.class));
        assertNull(context.getProperty("missing", Integer.class));

        context.setSharedData("key", "value");
        assertEquals("value", context.getSharedData("key"));
        Map<String, Object> copy = context.getSharedData();
        copy.put("other", 1);
        assertFalse(context.getSharedData().containsKey("other"));

        assertDoesNotThrow(() -> context.logInfo("info"));
        assertDoesNotThrow(() -> context.logWarning("warning"));
        assertDoesNotThrow(() -> context.logError("error"));
        assertDoesNotThrow(() -> context.logError("error", null));
        assertDoesNotThrow(() -> assertNull(context.getSharedData("absent")));
    }

    @Test
    void pluginInterfaceDefaultsAreConservative() {
        Plugin plugin = new Plugin() {
            @Override
            public void initialize(PluginContext context) {
            }

            @Override
            public void cleanup() {
            }

            @Override
            public String getName() {
                return "defaults";
            }

            @Override
            public String getVersion() {
                return "1.0";
            }

            @Override
            public String getDescription() {
                return "default plugin";
            }
        };

        assertFalse(plugin.supportsExtension("anything"));
        assertNull(plugin.getExtension("anything"));
        assertDoesNotThrow(() -> {
            plugin.onSimulationStart(null);
            plugin.onSimulationEnd(null);
            plugin.onStateUpdate(null, null);
            plugin.onEventScheduled(null);
            plugin.onOperationApplied(null);
            plugin.onError("message", null);
        });
    }

    @Test
    void registerAndUnloadPluginMaintainRegistryAndContext() {
        PluginManager manager = new PluginManager();
        SimplePlugin plugin = new SimplePlugin("p1", "9.9", "test plugin");

        manager.registerPlugin(plugin);

        assertEquals(1, plugin.initializeCalls);
        assertEquals("p1", plugin.context.getPluginName());
        assertEquals("9.9", plugin.context.getPluginVersion());
        assertEquals("", plugin.context.getPluginDirectory());
        assertSame(plugin, manager.getPlugin("p1"));
        assertEquals(List.of("p1"), manager.listPlugins());
        assertEquals(1, manager.getPluginsByType(SimplePlugin.class).size());
        assertTrue(manager.getPluginsByType(OtherPlugin.class).isEmpty());
        assertSame(plugin, manager.getPlugin("p1", SimplePlugin.class));
        assertNull(manager.getPlugin("p1", OtherPlugin.class));
        assertNull(manager.getPlugin("unknown"));
        assertFalse(manager.unloadPlugin("unknown"));

        assertTrue(manager.unloadPlugin("p1"));
        assertEquals(1, plugin.cleanupCalls);
        assertNull(manager.getPlugin("p1"));
        assertFalse(manager.unloadPlugin("p1"));
    }

    @Test
    void duplicateRegistrationAndFailingCleanupAreReported() {
        PluginManager manager = new PluginManager();
        manager.registerPlugin(new SimplePlugin("duplicate", "1.0", "first"));

        assertThrows(IllegalStateException.class,
            () -> manager.registerPlugin(new SimplePlugin("duplicate", "1.0", "second")));

        ThrowingPlugin throwing = new ThrowingPlugin();
        manager.registerPlugin(throwing);
        assertFalse(manager.unloadPlugin("ThrowingPlugin"));
        assertSame(throwing, manager.getPlugin("ThrowingPlugin"));

        SimplePlugin other = new SimplePlugin("unload-all", "1.0", "other");
        manager.registerPlugin(other);
        manager.unloadAll();
        assertEquals(List.of("ThrowingPlugin"), manager.listPlugins());
        assertEquals(1, other.cleanupCalls);
    }

    @Test
    void listenersSeeRegistrationsAndRemovals() {
        PluginManager manager = new PluginManager();
        AtomicInteger loaded = new AtomicInteger();
        AtomicInteger unloaded = new AtomicInteger();
        PluginManager.PluginListener listener = new PluginManager.PluginListener() {
            @Override
            public void onPluginLoaded(Plugin plugin) {
                loaded.incrementAndGet();
            }

            @Override
            public void onPluginUnloaded(Plugin plugin) {
                unloaded.incrementAndGet();
            }
        };

        manager.addPluginListener(listener);
        manager.addPluginListener(listener);
        manager.registerPlugin(new SimplePlugin("listened", "1.0", "listened"));
        assertEquals(2, loaded.get());

        manager.removePluginListener(listener);
        manager.unloadPlugin("listened");
        assertEquals(1, unloaded.get());

        manager.removePluginListener(listener);
        manager.registerPlugin(new SimplePlugin("unlistened", "1.0", "unlistened"));
        assertEquals(2, loaded.get());
    }

    @Test
    void hookNotificationsReachHealthyPluginsAndSwallowFailures() {
        PluginManager manager = new PluginManager();
        SimplePlugin healthy = new SimplePlugin("healthy", "1.0", "healthy");
        manager.registerPlugin(healthy);
        manager.registerPlugin(new ThrowingPlugin());

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        SimulationContext context = new SimulationContext(config);
        Qubit qubit = context.getQubit(0);
        QuantumOperation operation = SingleQubitGate.createHadamard(qubit);
        QuantumEvent event = TestSupport.event("h", operation, qubit);

        assertDoesNotThrow(() -> manager.initializePlugins(context));
        assertDoesNotThrow(() -> manager.cleanupPlugins(context));
        assertDoesNotThrow(() -> manager.notifyEventScheduled(event));
        assertDoesNotThrow(() -> manager.notifyStateUpdate(StateVector.createZeroState(1), event));
        assertDoesNotThrow(() -> manager.notifyOperationApplied(operation));
        assertDoesNotThrow(() -> manager.notifyError("failure", new RuntimeException("failure")));

        assertEquals(1, healthy.startCalls);
        assertEquals(1, healthy.endCalls);
        assertEquals(1, healthy.scheduledCalls);
        assertEquals(1, healthy.updateCalls);
        assertEquals(1, healthy.operationCalls);
        assertEquals(1, healthy.errorCalls);
    }

    @Test
    void directoryPluginLoadsProperties(@TempDir Path directory) throws Exception {
        Path pluginDirectory = Files.createDirectories(directory.resolve("dir-plugin"));
        String properties = "plugin.class=" + DirPlugin.class.getName() + "\n"
            + "plugin.name=Directory Plugin\n"
            + "plugin.version=2.5\n";
        Files.writeString(pluginDirectory.resolve("plugin.properties"), properties,
            StandardCharsets.ISO_8859_1);

        DirPlugin.initializations = 0;
        DirPlugin.cleanups = 0;
        PluginManager manager = new PluginManager();
        assertTrue(manager.loadPlugin(pluginDirectory.toString()));

        Plugin plugin = manager.getPlugin("DirPlugin");
        assertNotNull(plugin);
        assertSame(plugin, manager.getPlugin("DirPlugin", DirPlugin.class));
        assertEquals(1, DirPlugin.initializations);
        assertEquals("Directory Plugin", DirPlugin.context.getPluginName());
        assertEquals("2.5", DirPlugin.context.getPluginVersion());
        assertTrue(DirPlugin.context.getPluginDirectory().endsWith("dir-plugin"));
        assertTrue(manager.unloadPlugin("DirPlugin"));
        assertEquals(1, DirPlugin.cleanups);
    }

    @Test
    void directoryPluginDefaultsNameAndVersion(@TempDir Path directory) throws Exception {
        Path pluginDirectory = Files.createDirectories(directory.resolve("defaulted-plugin"));
        Files.writeString(pluginDirectory.resolve("plugin.properties"),
            "plugin.class=" + DirPlugin.class.getName() + "\n",
            StandardCharsets.ISO_8859_1);

        PluginManager manager = new PluginManager();
        assertTrue(manager.loadPlugin(pluginDirectory.toString()));
        assertEquals("defaulted-plugin", DirPlugin.context.getPluginName());
        assertEquals("1.0.0", DirPlugin.context.getPluginVersion());
    }

    @Test
    void invalidPluginLocationsAndMetadataAreRejected(@TempDir Path directory) throws IOException {
        PluginManager manager = new PluginManager();

        assertThrows(PluginException.class,
            () -> manager.loadPlugin(directory.resolve("missing").toString()));

        Path plainFile = Files.writeString(directory.resolve("plugin.txt"), "not a plugin");
        assertThrows(PluginException.class, () -> manager.loadPlugin(plainFile.toString()));

        Path noProperties = Files.createDirectories(directory.resolve("no-properties"));
        assertThrows(PluginException.class, () -> manager.loadPlugin(noProperties.toString()));

        Path noClass = Files.createDirectories(directory.resolve("no-class"));
        Files.writeString(noClass.resolve("plugin.properties"), "plugin.name=no-class\n");
        assertThrows(PluginException.class, () -> manager.loadPlugin(noClass.toString()));

        Path unknownClass = Files.createDirectories(directory.resolve("unknown-class"));
        Files.writeString(unknownClass.resolve("plugin.properties"),
            "plugin.class=com.example.DoesNotExist\n");
        assertThrows(PluginException.class, () -> manager.loadPlugin(unknownClass.toString()));

        Path notAPlugin = Files.createDirectories(directory.resolve("not-a-plugin"));
        Files.writeString(notAPlugin.resolve("plugin.properties"),
            "plugin.class=java.lang.String\n");
        assertThrows(PluginException.class, () -> manager.loadPlugin(notAPlugin.toString()));
    }

    @Test
    void duplicateDirectoryPluginIsRejected(@TempDir Path directory) throws Exception {
        Path pluginDirectory = Files.createDirectories(directory.resolve("duplicate"));
        Files.writeString(pluginDirectory.resolve("plugin.properties"),
            "plugin.class=" + DirPlugin.class.getName() + "\n");

        PluginManager manager = new PluginManager();
        assertTrue(manager.loadPlugin(pluginDirectory.toString()));
        assertThrows(PluginException.class, () -> manager.loadPlugin(pluginDirectory.toString()));
        assertEquals(1, manager.listPlugins().size());
    }

    @Test
    void jarPluginLoadsFromManifest(@TempDir Path directory) throws Exception {
        Path jar = directory.resolve("plugin.jar");
        writeJar(jar, JarPlugin.class.getName(), "Jar Plugin", "3.1");

        PluginManager manager = new PluginManager();
        assertTrue(manager.loadPlugin(jar.toString()));

        Plugin plugin = manager.getPlugin("JarPlugin");
        assertNotNull(plugin);
        assertEquals("Jar Plugin", JarPlugin.context.getPluginName());
        assertEquals("3.1", JarPlugin.context.getPluginVersion());
        assertEquals(directory.getFileName().toString(),
            Path.of(JarPlugin.context.getPluginDirectory()).getFileName().toString());
        assertTrue(manager.unloadPlugin("JarPlugin"));
        assertEquals(1, JarPlugin.cleanups);
    }

    @Test
    void invalidJarMetadataIsRejected(@TempDir Path directory) throws IOException {
        PluginManager manager = new PluginManager();

        Path noManifest = directory.resolve("no-manifest.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(noManifest))) {
            out.putNextEntry(new JarEntry("marker.txt"));
            out.write('x');
            out.closeEntry();
        }
        assertThrows(PluginException.class, () -> manager.loadPlugin(noManifest.toString()));

        Path noClass = directory.resolve("no-class.jar");
        writeJar(noClass, null, "No Class", "1.0");
        assertThrows(PluginException.class, () -> manager.loadPlugin(noClass.toString()));

        Path wrongClass = directory.resolve("wrong-class.jar");
        writeJar(wrongClass, "java.lang.String", "Wrong", "1.0");
        assertThrows(PluginException.class, () -> manager.loadPlugin(wrongClass.toString()));

        Path missingClass = directory.resolve("missing-class.jar");
        writeJar(missingClass, "com.example.DoesNotExist", "Missing", "1.0");
        assertThrows(PluginException.class, () -> manager.loadPlugin(missingClass.toString()));
    }

    @Test
    void pluginExceptionCarriesMessageAndCause() {
        PluginException plain = new PluginException("plain");
        assertEquals("plain", plain.getMessage());
        assertNull(plain.getCause());

        IllegalStateException cause = new IllegalStateException("cause");
        PluginException wrapped = new PluginException("wrapped", cause);
        assertEquals("wrapped", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
    }

    private static void writeJar(Path jar, String pluginClass, String name, String version)
        throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (pluginClass != null) {
            attributes.putValue("Plugin-Class", pluginClass);
        }
        attributes.putValue("Plugin-Name", name);
        attributes.putValue("Plugin-Version", version);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("marker.txt"));
            out.write('x');
            out.closeEntry();
        }
    }

    public static class SimplePlugin implements Plugin {
        private final String name;
        private final String version;
        private final String description;
        PluginContext context;
        int initializeCalls;
        int cleanupCalls;
        int startCalls;
        int endCalls;
        int scheduledCalls;
        int updateCalls;
        int operationCalls;
        int errorCalls;

        public SimplePlugin() {
            this("SimplePlugin", "1.0", "simple plugin");
        }

        SimplePlugin(String name, String version, String description) {
            this.name = name;
            this.version = version;
            this.description = description;
        }

        @Override
        public void initialize(PluginContext context) {
            this.context = context;
            initializeCalls++;
        }

        @Override
        public void cleanup() {
            cleanupCalls++;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return version;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public void onSimulationStart(SimulationContext context) {
            startCalls++;
        }

        @Override
        public void onSimulationEnd(SimulationContext context) {
            endCalls++;
        }

        @Override
        public void onEventScheduled(QuantumEvent event) {
            scheduledCalls++;
        }

        @Override
        public void onStateUpdate(QuantumState state, QuantumEvent event) {
            updateCalls++;
        }

        @Override
        public void onOperationApplied(QuantumOperation operation) {
            operationCalls++;
        }

        @Override
        public void onError(String errorMessage, Throwable error) {
            errorCalls++;
        }
    }

    public static class OtherPlugin extends SimplePlugin {
        public OtherPlugin() {
            super("OtherPlugin", "1.0", "other plugin");
        }
    }

    public static class ThrowingPlugin extends SimplePlugin {
        public ThrowingPlugin() {
            super("ThrowingPlugin", "1.0", "throwing plugin");
        }

        @Override
        public void cleanup() {
            throw new IllegalStateException("cleanup failed");
        }

        @Override
        public void onSimulationStart(SimulationContext context) {
            throw new IllegalStateException("start failed");
        }

        @Override
        public void onSimulationEnd(SimulationContext context) {
            throw new IllegalStateException("end failed");
        }

        @Override
        public void onEventScheduled(QuantumEvent event) {
            throw new IllegalStateException("schedule failed");
        }

        @Override
        public void onStateUpdate(QuantumState state, QuantumEvent event) {
            throw new IllegalStateException("update failed");
        }

        @Override
        public void onOperationApplied(QuantumOperation operation) {
            throw new IllegalStateException("operation failed");
        }

        @Override
        public void onError(String errorMessage, Throwable error) {
            throw new IllegalStateException("error hook failed");
        }
    }

    public static class DirPlugin implements Plugin {
        static PluginContext context;
        static int initializations;
        static int cleanups;

        public DirPlugin() {
        }

        @Override
        public void initialize(PluginContext context) {
            DirPlugin.context = context;
            initializations++;
        }

        @Override
        public void cleanup() {
            cleanups++;
        }

        @Override
        public String getName() {
            return "DirPlugin";
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public String getDescription() {
            return "directory plugin";
        }
    }

    public static class JarPlugin implements Plugin {
        static PluginContext context;
        static int cleanups;

        public JarPlugin() {
        }

        @Override
        public void initialize(PluginContext context) {
            JarPlugin.context = context;
        }

        @Override
        public void cleanup() {
            cleanups++;
        }

        @Override
        public String getName() {
            return "JarPlugin";
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public String getDescription() {
            return "jar plugin";
        }
    }
}

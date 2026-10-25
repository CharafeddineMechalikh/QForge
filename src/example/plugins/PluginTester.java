package example.plugins;

import com.quantum.simulator.plugin.Plugin;
import java.util.*;

/**
 * Exercises a plugin through a minimal lifecycle: initialize, query supported extensions, and
 * clean up.
 *
 * <p>The test uses an in-memory {@code PluginContext} that prints its log messages to standard
 * output. It reports success unless the plugin throws, and does not check the content of the
 * extensions beyond whether they are null.</p>
 */
public class PluginTester {
    
    /**
     * Runs the lifecycle test for one plugin.
     *
     * <p>Supported extensions are queried from a fixed list of five names, and each available
     * extension is reported as available or null. Errors are printed with a stack trace and are not
     * rethrown.</p>
     *
     * @param plugin the plugin to test
     */
    public static void testPlugin(Plugin plugin) {
        System.out.println("\n=== Testing Plugin: " + plugin.getName() + " ===");
        
        try {
            // Create test context
            TestPluginContext context = new TestPluginContext(plugin.getName());
            
            // Test initialization
            System.out.println("1. Testing initialization...");
            plugin.initialize(context);
            System.out.println("   [OK] Initialization successful");
            
            // Test extensions
            System.out.println("2. Testing extensions...");
            List<String> supportedExtensions = Arrays.asList(
                "noise_models", "gate_decompositions", "error_correction", 
                "performance_metrics", "visualization_tools"
            );
            
            for (String extension : supportedExtensions) {
                if (plugin.supportsExtension(extension)) {
                    Object result = plugin.getExtension(extension);
                    System.out.println("   [OK] " + extension + ": " + 
                        (result != null ? "Available" : "Null"));
                }
            }
            
            // Test cleanup
            System.out.println("3. Testing cleanup...");
            plugin.cleanup();
            System.out.println("   [OK] Cleanup successful");
            
            System.out.println("\n[OK] Plugin " + plugin.getName() + " passed all tests!");
            
        } catch (Exception e) {
            System.err.println("\n[ERROR] Plugin " + plugin.getName() + " failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static class TestPluginContext extends com.quantum.simulator.plugin.PluginContext {
        public TestPluginContext(String pluginName) {
            super(pluginName, "1.0.0", "./test-plugins/");
        }
        
        @Override
        public void logInfo(String message) {
            System.out.println("    [INFO] " + message);
        }
        
        @Override
        public void logWarning(String message) {
            System.out.println("    [WARN] " + message);
        }
        
        @Override
        public void logError(String message) {
            System.out.println("    [ERROR] " + message);
        }
        
        @Override
        public void logError(String message, Throwable error) {
            System.out.println("    [ERROR] " + message);
            if (error != null) {
                System.out.println("           " + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }
    }
}
package example.plugins;
/**
 * Runs each example plugin through {@link PluginTester} and then runs the full
 * {@link PluginSystemExample}.
 */
public class RunPluginExample {
    /**
     * Tests the four example plugins, then delegates to the plugin system example.
     *
     * <p>Arguments are not interpreted here; the array is passed through to
     * {@code PluginSystemExample.main}. The method prints the tester output followed by the output
     * of the example, and prints a stack trace if the example throws.</p>
     *
     * @param args passed unchanged to the plugin system example
     */
    public static void main(String[] args) {
        System.out.println("=== RUNNING FIXED PLUGIN EXAMPLE ===");
        
        // Test individual plugins first
        PluginTester.testPlugin(new PluginSystemExample.AdvancedNoiseModelPlugin());
        PluginTester.testPlugin(new PluginSystemExample.CircuitCompressionPlugin());
        PluginTester.testPlugin(new PluginSystemExample.QuantumErrorCorrectionPlugin());
        PluginTester.testPlugin(new PluginSystemExample.PerformanceMonitorPlugin());
        
        // Then run the full example
        try {
			PluginSystemExample.main(args);
		} catch (Exception e) { 
			e.printStackTrace();
		}
    }
}
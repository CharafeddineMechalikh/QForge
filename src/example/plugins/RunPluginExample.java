package example.plugins;
public class RunPluginExample {
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
package example.plugins;

import com.quantum.simulator.plugin.*;
import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.*;
import com.quantum.simulator.operations.*;
import com.quantum.simulator.noise.*;
import com.quantum.simulator.events.*;
import com.quantum.simulator.state.*;
import java.util.*;

/**
 * Demonstrates registering plugins with the simulator plugin manager, running a circuit while they
 * are active, and querying their extension points.
 *
 * <p>Four package-private plugins are registered: an advanced noise model plugin, a circuit
 * compression plugin, an error correction plugin, and a performance monitor. The plugins perform
 * bookkeeping and describe their capabilities; the noise plugin returns placeholder
 * {@link NoiseModel} instances that do not alter the state, and the compression plugin logs
 * scheduled gates without rewriting the circuit.</p>
 */
public class PluginSystemExample {
    
    /**
     * Runs the plugin demonstration.
     *
     * <p>No command line arguments are read. The method prints the loaded plugins with their
     * versions and descriptions, the final state of a three-qubit circuit, and the extensions each
     * plugin reports for {@code noise_models}, {@code gate_compression_rules},
     * {@code error_correction}, and {@code performance_metrics}. The simulator is shut down in a
     * finally block. Errors are printed with a stack trace.</p>
     *
     * @param args ignored
     * @throws Exception if simulator setup or the simulation fails outside the handled block
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== PLUGIN SYSTEM EXTENSION EXAMPLE ===");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        PluginManager pluginManager = simulator.getPluginManager();
        
        try {
            // 2. Load and register plugins dynamically
            System.out.println("Loading plugins...");
            
            pluginManager.registerPlugin(new AdvancedNoiseModelPlugin());
            pluginManager.registerPlugin(new CircuitCompressionPlugin());
            pluginManager.registerPlugin(new QuantumErrorCorrectionPlugin());
            pluginManager.registerPlugin(new PerformanceMonitorPlugin());
             
            // 3. List loaded plugins
            System.out.println("\nLoaded plugins:");
            for (String pluginName : pluginManager.listPlugins()) {
                Plugin plugin = pluginManager.getPlugin(pluginName);
                System.out.printf(Locale.ROOT, "  %s v%s - %s\n", 
                    plugin.getName(), plugin.getVersion(), plugin.getDescription());
            }
            
            // 4. Run simulation with plugins
            runPluginEnhancedSimulation(simulator);
            
            // 5. Demonstrate plugin extensions
            demonstratePluginExtensions(pluginManager);
            
        } catch (Exception e) {
            System.err.println("Error in plugin system: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 6. Cleanup
            simulator.shutdown();
            System.out.println("\nAll plugins unloaded");
        }
    }
    
    private static void runPluginEnhancedSimulation(QuantumSimulator simulator) throws Exception {
        System.out.println("\n=== RUNNING PLUGIN-ENHANCED SIMULATION ===");
        PluginManager pluginManager = simulator.getPluginManager();
        
        // Run a complex circuit
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        Qubit q2 = new Qubit(2, "q2");
        
        // Toffoli gate implementation using plugins
        System.out.println("Implementing Toffoli gate via plugin optimizations...");
        
        // Plugins can intercept and modify gate applications
        for (Plugin plugin : pluginManager.getPluginsByType(CircuitCompressionPlugin.class)) {
            Object optimizedCircuit = plugin.getExtension("circuit_optimization");
            if (optimizedCircuit != null) {
                System.out.println("Circuit optimized by " + plugin.getName() + ": " + optimizedCircuit);
            }
        }
        
        // Create simple circuit to demonstrate plugins
        System.out.println("\nCreating and running demonstration circuit...");
        
        // Hadamard on all qubits
        QuantumEvent h0 = new QuantumEvent.Builder("H0")
            .withOperation(SingleQubitGate.createHadamard(q0))
            .withTargetQubits(q0)
            .build();
        
        QuantumEvent h1 = new QuantumEvent.Builder("H1")
            .withOperation(SingleQubitGate.createHadamard(q1))
            .withTargetQubits(q1)
            .build();
        
        QuantumEvent h2 = new QuantumEvent.Builder("H2")
            .withOperation(SingleQubitGate.createHadamard(q2))
            .withTargetQubits(q2)
            .build();
        
        // CNOT gates
        QuantumEvent cnot1 = new QuantumEvent.Builder("CNOT0-1")
            .withOperation(MultiQubitGate.createCNOT(q0, q1))
            .withTargetQubits(q0, q1)
            .build();
        
        QuantumEvent cnot2 = new QuantumEvent.Builder("CNOT1-2")
            .withOperation(MultiQubitGate.createCNOT(q1, q2))
            .withTargetQubits(q1, q2)
            .build();
        
        // Schedule events
        simulator.scheduleEvent(h0);
        simulator.scheduleEvent(h1);
        simulator.scheduleEvent(h2);
        simulator.scheduleEvent(cnot1);
        simulator.scheduleEvent(cnot2);
        
        // Run simulation
        SimulationResult result = simulator.run();
        
        System.out.println("\nSimulation completed successfully!");
        System.out.println("Final state: " + result.getFinalState());
        simulator.reset();
    }
    
    private static void demonstratePluginExtensions(PluginManager pluginManager) {
        System.out.println("\n=== PLUGIN EXTENSIONS DEMONSTRATION ===");
        
        // Query plugins for specific extensions
        String[] extensionPoints = {
            "noise_models",
            "gate_compression_rules", 
            "error_correction",
            "performance_metrics", 
        };
        
        for (String extensionPoint : extensionPoints) {
            System.out.println("\nExtensions for: " + extensionPoint);
            boolean foundExtensions = false;
            
            for (String pluginName : pluginManager.listPlugins()) {
                Plugin plugin = pluginManager.getPlugin(pluginName);
                if (plugin.supportsExtension(extensionPoint)) {
                    try {
                        Object extension = plugin.getExtension(extensionPoint);
                        if (extension != null) {
                            System.out.println("  " + plugin.getName() + ": " + 
                                extension.toString().replace("\n", "\n    "));
                            foundExtensions = true;
                        }
                    } catch (Exception e) {
                        System.err.println("  Error getting extension from " + pluginName + ": " + e.getMessage());
                    }
                }
            }
            
            if (!foundExtensions) {
                System.out.println("  No extensions found");
            }
        }
    }
    
    // Example plugin implementations with FIXED initialization issues
    
    static class AdvancedNoiseModelPlugin implements Plugin {
        private PluginContext context;
        private Map<String, NoiseModel> customNoiseModels;
        
        @Override
        public void initialize(PluginContext context) {
            this.context = context;
            // Initialize BEFORE using
            this.customNoiseModels = new HashMap<>();
            
            // Register custom noise models
            customNoiseModels.put("coherent_noise", createCoherentNoiseModel());
            customNoiseModels.put("cross_talk", createCrossTalkNoiseModel());
            customNoiseModels.put("leakage", createLeakageNoiseModel());
            
            context.logInfo("AdvancedNoiseModelPlugin initialized with " + 
                customNoiseModels.size() + " custom noise models");
        }
        
        @Override
        public void cleanup() {
            if (context != null) {
                context.logInfo("AdvancedNoiseModelPlugin cleanup complete");
            }
        }
        
        @Override
        public String getName() {
            return "AdvancedNoiseModel";
        }
        
        @Override
        public String getVersion() {
            return "2.0.0";
        }
        
        @Override
        public String getDescription() {
            return "Provides advanced noise models for realistic quantum simulation";
        }
        
        @Override
        public boolean supportsExtension(String extensionPoint) {
            return extensionPoint.equals("noise_models") || 
                   extensionPoint.equals("custom_noise");
        }
        
        @Override
        public Object getExtension(String extensionPoint) {
            // Check if initialized
            if (customNoiseModels == null) {
                return "Plugin not properly initialized";
            }
            
            if (extensionPoint.equals("noise_models")) {
                return new ArrayList<>(customNoiseModels.keySet());
            } else if (extensionPoint.equals("custom_noise")) {
                return new HashMap<>(customNoiseModels);
            }
            return null;
        }
        
        // Simple placeholder implementations
        private NoiseModel createCoherentNoiseModel() {
            return new SimpleNoiseModel("CoherentNoise", 0.05);
        }
        
        private NoiseModel createCrossTalkNoiseModel() {
            return new SimpleNoiseModel("CrossTalkNoise", 0.02);
        }
        
        private NoiseModel createLeakageNoiseModel() {
            return new SimpleNoiseModel("LeakageNoise", 0.01);
        }
        
        // Simple implementation of NoiseModel for demonstration
        private static class SimpleNoiseModel extends NoiseModel {
            public SimpleNoiseModel(String name, double probability) {
                super(name, probability, new Qubit[0]);
            }
            
            @Override
            public com.quantum.simulator.state.QuantumState apply(com.quantum.simulator.state.QuantumState state) {
                return state;
            }
            
            @Override
            public com.quantum.simulator.math.ComplexMatrix[] getKrausOperators(int numQubits) {
                return new com.quantum.simulator.math.ComplexMatrix[0];
            }
            
            @Override
            public NoiseModel withProbability(double newProbability) {
                return new SimpleNoiseModel(getName(), newProbability);
            }
            
            @Override
            public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
                return this;
            }
        }
    }
    
    static class CircuitCompressionPlugin implements Plugin {
        private PluginContext context;
        
        @Override
        public void initialize(PluginContext context) {
            this.context = context;
            context.logInfo("CircuitCompressionPlugin initialized");
        }
        
        @Override
        public void cleanup() {
            if (context != null) {
                context.logInfo("CircuitCompressionPlugin cleanup complete");
            }
        }
        
        @Override
        public String getName() {
            return "CircuitCompression";
        }
        
        @Override
        public String getVersion() {
            return "1.1.0";
        }
        
        @Override
        public String getDescription() {
            return "Compresses quantum circuits by removing redundant gates";
        }
        
        @Override
        public void onEventScheduled(QuantumEvent event) {
            if (context != null && event.getType() == EventType.GATE_OPERATION) {
                context.logInfo("Gate scheduled: " + event.getId() + 
                    " - " + event.getOperation().getName());
            }
        }
        
        @Override
        public boolean supportsExtension(String extensionPoint) {
            return extensionPoint.equals("circuit_optimization") ||
                   extensionPoint.equals("gate_compression_rules");
        }
        
        @Override
        public Object getExtension(String extensionPoint) {
            if (extensionPoint.equals("circuit_optimization")) {
                return "CNOT ladder compression, single-qubit gate merging, cancellation of inverse gates";
            } else if (extensionPoint.equals("gate_compression_rules")) {
                return Arrays.asList(
                    "H H = I",
                    "X X = I", 
                    "Y Y = I",
                    "Z Z = I",
                    "CNOT(a,b) CNOT(a,b) = I"
                );
            }
            return null;
        }
    }
    
    static class QuantumErrorCorrectionPlugin implements Plugin {
        private PluginContext context;
        
        @Override
        public void initialize(PluginContext context) {
            this.context = context;
            context.logInfo("QuantumErrorCorrectionPlugin initialized");
        }
        
        @Override
        public void cleanup() {
            if (context != null) {
                context.logInfo("QuantumErrorCorrectionPlugin cleanup complete");
            }
        }
        
        @Override
        public String getName() {
            return "QuantumErrorCorrection";
        }
        
        @Override
        public String getVersion() {
            return "1.0.0";
        }
        
        @Override
        public String getDescription() {
            return "Implements quantum error correction codes (Shor, Steane, Surface codes)";
        }
        
        @Override
        public boolean supportsExtension(String extensionPoint) {
            return extensionPoint.equals("error_correction") ||
                   extensionPoint.equals("qec_codes");
        }
        
        @Override
        public Object getExtension(String extensionPoint) {
            if (extensionPoint.equals("error_correction")) {
                return "Supports: Bit-flip code, Phase-flip code, Shor 9-qubit code, Steane 7-qubit code";
            } else if (extensionPoint.equals("qec_codes")) {
                Map<String, String> codes = new HashMap<>();
                codes.put("bit_flip_3", "Corrects single bit flips using 3 qubits");
                codes.put("phase_flip_3", "Corrects single phase flips using 3 qubits");
                codes.put("shor_9", "Corrects arbitrary single-qubit errors using 9 qubits");
                codes.put("steane_7", "CSS code correcting single errors using 7 qubits");
                return codes;
            }
            return null;
        }
    }
    
    static class PerformanceMonitorPlugin implements Plugin {
        private PluginContext context;
        private Map<String, Long> performanceMetrics;
        
        @Override
        public void initialize(PluginContext context) {
            this.context = context;
            // Initialize BEFORE using
            this.performanceMetrics = new HashMap<>();
            context.logInfo("PerformanceMonitorPlugin initialized");
        }
        
        @Override
        public void cleanup() {
            if (context == null || performanceMetrics == null) {
                return;
            }
            
            // Generate performance report
            context.logInfo("Performance Report:");
            for (Map.Entry<String, Long> entry : performanceMetrics.entrySet()) {
                context.logInfo("  " + entry.getKey() + ": " + entry.getValue());
            }
            
            context.logInfo("PerformanceMonitorPlugin cleanup complete");
        }
        
        @Override
        public String getName() {
            return "PerformanceMonitor";
        }
        
        @Override
        public String getVersion() {
            return "1.0.0";
        }
        
        @Override
        public String getDescription() {
            return "Monitors simulation performance and provides optimization suggestions";
        }
        
        @Override
        public void onSimulationStart(SimulationContext simContext) {
            if (performanceMetrics != null) {
                performanceMetrics.put("start_time", System.currentTimeMillis());
                performanceMetrics.put("gate_count", 0L);
            }
        }
        
        @Override
        public void onOperationApplied(QuantumOperation operation) {
            if (performanceMetrics != null) {
                performanceMetrics.merge("gate_count", 1L, Long::sum);
                
                // Track gate-specific performance
                String gateKey = "gate_" + operation.getName();
                performanceMetrics.merge(gateKey, 1L, Long::sum);
            }
        }
        
        @Override
        public void onSimulationEnd(SimulationContext simContext) {
            if (performanceMetrics == null) return;
            
            long endTime = System.currentTimeMillis();
            Long startTime = performanceMetrics.get("start_time");
            if (startTime != null) {
                performanceMetrics.put("total_time_ms", endTime - startTime);
                
                // Calculate gates per second
                Long gateCount = performanceMetrics.get("gate_count");
                if (gateCount != null) {
                    long totalTime = endTime - startTime;
                    if (totalTime > 0) {
                        long gatesPerSecond = gateCount * 1000 / totalTime;
                        performanceMetrics.put("gates_per_second", gatesPerSecond);
                    }
                }
            }
        }
        
        @Override
        public boolean supportsExtension(String extensionPoint) {
            return extensionPoint.equals("performance_metrics") ||
                   extensionPoint.equals("optimization_suggestions");
        }
        
        @Override
        public Object getExtension(String extensionPoint) {
            // Check if initialized
            if (performanceMetrics == null) {
                return "Plugin not properly initialized";
            }
            
            if (extensionPoint.equals("performance_metrics")) {
                return new HashMap<>(performanceMetrics);
            } else if (extensionPoint.equals("optimization_suggestions")) {
                return generateOptimizationSuggestions();
            }
            return null;
        }
        
        private List<String> generateOptimizationSuggestions() {
            List<String> suggestions = new ArrayList<>();
            
            if (performanceMetrics == null) {
                suggestions.add("Plugin not initialized");
                return suggestions;
            }
            
            Long gateCount = performanceMetrics.get("gate_count");
            Long totalTime = performanceMetrics.get("total_time_ms");
            
            if (gateCount != null && totalTime != null && totalTime > 0) {
                double gatesPerMs = gateCount / (double) totalTime;
                
                if (gatesPerMs < 10) {
                    suggestions.add("Consider using parallel execution for gates acting on different qubits");
                }
                
                Long cnotCount = performanceMetrics.get("gate_CNOT");
                if (cnotCount != null && cnotCount > gateCount * 0.5) {
                    suggestions.add("High CNOT count detected. Consider circuit transpilation to reduce two-qubit gates");
                }
            } else {
                suggestions.add("Insufficient performance data collected");
            }
            
            return suggestions;
        }
    }
}
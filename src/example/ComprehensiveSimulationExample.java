package example;

import com.quantum.simulator.core.*;
import com.quantum.simulator.config.*;
import com.quantum.simulator.strategy.*;
import com.quantum.simulator.state.*;
import com.quantum.simulator.operations.*;
import com.quantum.simulator.noise.*;
import com.quantum.simulator.events.*;
import com.quantum.simulator.measurement.*;
import com.quantum.simulator.measurement.Observable;
import com.quantum.simulator.observer.*;
import com.quantum.simulator.classical.*;
import com.quantum.simulator.resources.*;
import com.quantum.simulator.plugin.*;
import com.quantum.simulator.math.*;

import java.time.Instant;
import java.time.Duration;
import java.util.*;

public class ComprehensiveSimulationExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== COMPREHENSIVE QUANTUM SIMULATION ===");
        
        // Run all examples
        runNoisyEntanglementSimulation();
        runQuantumTeleportationWithFeedback();
        runResourceManagedSimulation();
        runPluginBasedSimulation();
        runClassicalHybridAlgorithm();
        
        System.out.println("\n=== All simulations completed successfully ===");
    }
    
    // Example 1: Noise simulation with visualization
    private static void runNoisyEntanglementSimulation() throws Exception {
        System.out.println("\n=== Example 1: Noisy Bell State with Visualization ===");
        
        // 1. Configure simulation with noise
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.DENSITY_MATRIX); // Use density matrix for noise
        config.setMaxSteps(100);
        
        // Configure noise profile
        NoiseProfile noiseProfile = new NoiseProfile("BellStateNoise");
        
        // Add depolarizing noise to both qubits
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        
        noiseProfile.addNoise(q0, new DepolarizingNoise(0.01, q0));
        noiseProfile.addNoise(q1, new DepolarizingNoise(0.02, q1));
        
        // Add amplitude damping noise (simulating energy loss)
        noiseProfile.addNoise(q0, new AmplitudeDampingNoise(0.005, q0));
        
        config.setNoiseProfile(noiseProfile);
        
        // 2. Create simulator with observers
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // 3. Add various observers
        MetricsCollector metrics = new MetricsCollector();
        VisualizationEngine visualizer = new VisualizationEngine(100, "./output/vis_bell_state/");
        
        simulator.addObserver(metrics);
        simulator.addObserver(visualizer);
        
        // 4. Set up resource manager
        ResourceConstraints rc = new ResourceConstraints(
            1024 * 1024 * 100, // 100MB
            2, // 2 CPUs
            0  // No GPU
        );
        config.setResourceConstraints(rc);
        
        // 5. Create Bell state circuit with noisy gates
        Instant currentTime = Instant.now();
        
        // Step 1: Hadamard on q0 with some gate error
        QuantumEvent h0 = createGateEvent("H_q0", 
            SingleQubitGate.createHadamard(q0), 
            currentTime, q0);
        
        currentTime = currentTime.plusMillis(10);
        
        // Step 2: CNOT with q0 control, q1 target
        QuantumEvent cnot = createGateEvent("CNOT_q0_q1",
            MultiQubitGate.createCNOT(q0, q1),
            currentTime, q1); // q1 is target, q0 is control
        
        currentTime = currentTime.plusMillis(10);
        
        // Step 3: Add some idle time (noise accumulation)
        QuantumEvent delay = new QuantumEvent.Builder("delay")
            .withScheduledTime(currentTime)
            .withType(EventType.DELAY)
            .withDescription("Idle time for noise accumulation")
            .build();
        
        currentTime = currentTime.plusMillis(50); // 50ms delay
        
        // Step 4: Measure both qubits
        MeasurementOperator measurement = new MeasurementOperator(
            "BellStateMeasurement",
            Observable.pauliZ(new Qubit[]{q0, q1}),
            1000, // 1000 shots
            true  // Destructive measurement
        );
        
        QuantumEvent measure = new QuantumEvent.Builder("measure_bell")
            .withScheduledTime(currentTime)
            .withOperation(measurement)
            .withTargetQubits(q0, q1)
            .withType(EventType.MEASUREMENT_OPERATION)
            .build();
        
        // Schedule all events
        simulator.scheduleEvent(h0);
        simulator.scheduleEvent(cnot);
        simulator.scheduleEvent(delay);
        simulator.scheduleEvent(measure);
        
        // 6. Run simulation
        System.out.println("Running noisy Bell state simulation...");
        SimulationResult result = simulator.run();
        
        // 7. Analyze results
        System.out.println("\n=== Simulation Results ===");
        System.out.println("Final State Type: " + result.getFinalState().getClass().getSimpleName());
        System.out.println("Purity: " + result.getFinalState().getPurity());
        
        // Get error metrics
        ErrorManager.ErrorReport errorReport = simulator.getContext().getErrorManager().generateErrorReport();
        System.out.println("\n=== Error Report ===");
        System.out.println(errorReport);
        
        // Print measurement results
        List<MeasurementResult> measurements = result.getMeasurements();
        if (!measurements.isEmpty()) {
            MeasurementResult bellMeasurement = measurements.get(0);
            System.out.println("\n=== Measurement Results ===");
            System.out.println(bellMeasurement.toHistogramString(4));
            
            // Calculate correlation
            Map<String, Double> bitProbs = bellMeasurement.getBitStringProbabilities();
            double prob00 = bitProbs.getOrDefault("00", 0.0);
            double prob11 = bitProbs.getOrDefault("11", 0.0);
            double correlation = Math.abs(prob00 - prob11);
            System.out.printf(Locale.ROOT, "Bell state correlation: %.4f (ideal: 0.0)\n", correlation);
        }
        
        // Generate visualizations
        try {
            visualizer.generateSummaryVisualizations(result);
            System.out.println("\nVisualizations saved to: ./output/vis_bell_state/");
        } catch (Exception e) {
            System.err.println("Failed to generate visualizations: " + e.getMessage());
        }
        
        // Print metrics
        System.out.println("\n=== Performance Metrics ===");
        System.out.println(metrics.generateReport().toSummaryString());
        
        simulator.reset();
    }
    
    // Example 2: Quantum teleportation with classical feedback
    private static void runQuantumTeleportationWithFeedback() throws Exception {
        System.out.println("\n=== Example 2: Quantum Teleportation with Classical Feedback ===");
        
        // 1. Configure simulation with classical control
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3); // Alice has 2 qubits, Bob has 1
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.setClassicalControlEnabled(true);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // 2. Set up classical controller
        ClassicalController controller = simulator.getClassicalController();
        if (controller == null) {
            throw new IllegalStateException("Classical control is not enabled");
        }
        
        // Add custom control rules
        controller.addRule(new TeleportationRule());
        
        // Store teleportation protocol in classical memory
        controller.storeInMemory("protocol", "quantum_teleportation");
        controller.storeInMemory("alice_qubits", new int[]{0, 1});
        controller.storeInMemory("bob_qubit", 2);
        
        // 3. Create qubits with labels
        Qubit aliceQubit = new Qubit(0, "Alice_qubit");
        Qubit entangledQubit = new Qubit(1, "Entangled_qubit");
        Qubit bobQubit = new Qubit(2, "Bob_qubit");
        
        // State to teleport: |ψ> = α|0> + β|1>
        double alpha = 0.6;
        double beta = Math.sqrt(1 - alpha * alpha);
        
        // 4. Quantum teleportation protocol
        Instant currentTime = Instant.now();
        
        // Step 1: Create Bell pair between Alice's second qubit and Bob's qubit
        QuantumEvent bell_hadamard = createGateEvent("H_entangled",
            SingleQubitGate.createHadamard(entangledQubit),
            currentTime, entangledQubit);
        
        currentTime = currentTime.plusMillis(10);
        
        QuantumEvent bell_cnot = createGateEvent("CNOT_bell",
            MultiQubitGate.createCNOT(entangledQubit, bobQubit),
            currentTime, bobQubit); // Bob's qubit is target
        
        currentTime = currentTime.plusMillis(10);
        
        // Step 2: Alice prepares her qubit in state |ψ> = α|0> + β|1>
        // Using rotation gates: RY(θ) where cos(θ/2) = α
        double theta = 2 * Math.acos(alpha);
        QuantumEvent prepare_state = createGateEvent("RY_prepare",
            SingleQubitGate.createRY(theta, aliceQubit),
            currentTime, aliceQubit);
        
        currentTime = currentTime.plusMillis(10);
        
        // Step 3: Alice performs Bell measurement
        QuantumEvent alice_cnot = createGateEvent("CNOT_teleport",
            MultiQubitGate.createCNOT(aliceQubit, entangledQubit),
            currentTime, entangledQubit);
        
        currentTime = currentTime.plusMillis(10);
        
        QuantumEvent alice_hadamard = createGateEvent("H_measure",
            SingleQubitGate.createHadamard(aliceQubit),
            currentTime, aliceQubit);
        
        currentTime = currentTime.plusMillis(10);
        
        // Measure Alice's qubits (this triggers classical feedback)
        Observable aliceZ = Observable.pauliZ(new Qubit[]{aliceQubit, entangledQubit});
        MeasurementOperator aliceMeasurement = new MeasurementOperator(
            "Alice_measurement", aliceZ, 1, true);
        
        QuantumEvent measure_alice = new QuantumEvent.Builder("measure_alice")
            .withScheduledTime(currentTime)
            .withOperation(aliceMeasurement)
            .withTargetQubits(aliceQubit, entangledQubit)
            .withType(EventType.MEASUREMENT_OPERATION)
            .withDescription("Alice measures her qubits")
            .build();
        
        // Step 4: Classical feedback determines Bob's operations
        // This will be handled by the ClassicalController
        
        // Schedule events
        simulator.scheduleEvent(bell_hadamard);
        simulator.scheduleEvent(bell_cnot);
        simulator.scheduleEvent(prepare_state);
        simulator.scheduleEvent(alice_cnot);
        simulator.scheduleEvent(alice_hadamard);
        simulator.scheduleEvent(measure_alice);
        
        // 5. Run simulation
        System.out.println("Running quantum teleportation protocol...");
        SimulationResult result = simulator.run();
        
        // 6. Analyze classical decisions
        List<ControlDecision> decisions = controller.getDecisionHistory();
        System.out.println("\n=== Classical Decisions Made ===");
        for (ControlDecision decision : decisions) {
            System.out.println("  " + decision);
        }
        
        // 7. Verify teleportation
        System.out.println("\n=== Teleportation Verification ===");
        System.out.println("Original state: |psi> = " + alpha + "|0> + " + beta + "|1>");
        System.out.println("Final state of Bob's qubit:");
        
        if (result.getFinalState() != null) {
            // Bob's qubit is the third qubit; trace out Alice's two qubits.
            QuantumState finalState = result.getFinalState();
            System.out.println("  " + finalState);
            
            QuantumState bobState = finalState.partialTrace(new int[]{2});
            System.out.printf(Locale.ROOT, "  Probability |0>: %.4f (expected: %.4f)\n", 
                bobState.getProbability(0), alpha * alpha);
            System.out.printf(Locale.ROOT, "  Probability |1>: %.4f (expected: %.4f)\n", 
                bobState.getProbability(1), beta * beta);
        }
        
        simulator.reset();
    }
    
    // Custom rule for teleportation
    private static class TeleportationRule implements ClassicalController.ControlRule {
        private boolean enabled = true;
        
        @Override
        public boolean appliesTo(MeasurementResult measurement) {
            return measurement.getObservableName().contains("Alice_measurement");
        }
        
        @Override
        public ControlDecision evaluate(MeasurementResult measurement, Map<String, Object> memory) {
            System.out.println("\n[TeleportationRule] Processing Alice's measurement...");
            
            // Get the most frequent measured bitstring
            int outcome = measurement.getMostFrequentOutcome();
            String bits = Integer.toBinaryString(outcome);
            bits = String.format(Locale.ROOT, "%2s", bits).replace(' ', '0');
            
            System.out.println("Alice measured: " + bits);
            
            // Based on Alice's measurement, determine Bob's corrections
            int bobQubitIndex = (int) memory.get("bob_qubit");
            Qubit bobQubit = new Qubit(bobQubitIndex, "Bob");
            
            List<QuantumOperation> corrections = new ArrayList<>();
            
            // Alice's first bit (q0) controls Z, second bit (q1) controls X
            if (bits.charAt(0) == '1') {
                corrections.add(SingleQubitGate.createPauliZ(bobQubit));
                System.out.println("  Bob needs Z correction");
            }
            
            if (bits.charAt(1) == '1') {
                corrections.add(SingleQubitGate.createPauliX(bobQubit));
                System.out.println("  Bob needs X correction");
            }
            
            if (corrections.isEmpty()) {
                System.out.println("  No corrections needed for Bob");
                return null;
            }
            
            // Create events for Bob's corrections
            Instant correctionTime = Instant.now().plusSeconds(1);
            List<QuantumEvent> correctionEvents = new ArrayList<>();
            
            for (int i = 0; i < corrections.size(); i++) {
                QuantumOperation op = corrections.get(i);
                QuantumEvent event = new QuantumEvent.Builder("bob_correction_" + i)
                    .withScheduledTime(correctionTime)
                    .withOperation(op)
                    .withTargetQubits(bobQubit)
                    .withDescription("Bob applies correction based on Alice's measurement")
                    .build();
                correctionEvents.add(event);
                correctionTime = correctionTime.plusMillis(10);
            }
            
            // Store in classical memory
            memory.put("alice_measurement", bits);
            memory.put("bob_corrections", corrections.size());
            
            // Return decision to schedule all correction events
            return ControlDecision.createScheduleDecisions(
                "Apply Bob's corrections based on Alice's measurement",
                100,
                correctionEvents
            );
        }
        
        @Override
        public String getName() { return "TeleportationRule"; }
        
        @Override
        public boolean isEnabled() { return enabled; }
        
        @Override
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        @Override
        public int getPriority() { return 100; } // High priority
    }
    
    // Example 3: Resource-managed simulation
    private static void runResourceManagedSimulation() throws Exception {
        System.out.println("\n=== Example 3: Resource-Managed Large Simulation ===");
        
        // 1. Configure with strict resource limits
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(8); // Larger simulation
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        // Set resource constraints (simulate limited environment)
        ResourceConstraints rc = new ResourceConstraints(
            1024 * 1024 * 50, // 50MB memory limit
            2,                 // 2 CPUs
            0                  // No GPU
        );
        rc.setAllowMemoryOvercommit(false);
        rc.setAllowCPUOvercommit(false);
        
        config.setResourceConstraints(rc);
        
        // 2. Create resource manager
        ResourceManager resourceManager = new ResourceManager(rc);
        
        // 3. Request resources for simulation
        ResourceRequest simulationRequest = ResourceRequest.newBuilder("quantum_simulation")
            .withCpuCores(2)
            .withMemoryMB(40) // 40MB
            .withDuration(Duration.ofMinutes(1))
            .withPriority(ResourceRequest.Priority.HIGH)
            .withPurpose("Quantum Fourier Transform simulation")
            .build();
        
        try {
            // 4. Allocate resources
            System.out.println("Requesting resources: " + simulationRequest);
            ResourceAllocation allocation = resourceManager.allocate(simulationRequest);
            System.out.println("Resources allocated: " + allocation);
            
            // 5. Run simulation
            QuantumSimulator simulator = new QuantumSimulator();
            simulator.initialize(config);
            
            // Add metrics observer
            MetricsCollector metrics = new MetricsCollector();
            simulator.addObserver(metrics);
            
            // Create Quantum Fourier Transform circuit
            List<Qubit> qubits = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                qubits.add(new Qubit(i, "q" + i));
            }
            
            Instant currentTime = Instant.now();
            
            // QFT circuit
            for (int i = 0; i < qubits.size(); i++) {
                // Apply Hadamard
                QuantumEvent h = createGateEvent("H" + i,
                    SingleQubitGate.createHadamard(qubits.get(i)),
                    currentTime, qubits.get(i));
                simulator.scheduleEvent(h);
                currentTime = currentTime.plusMillis(5);
                
                // Apply controlled phase rotations
                for (int j = i + 1; j < qubits.size(); j++) {
                    double angle = Math.PI / Math.pow(2, j - i);
                    QuantumEvent cp = createGateEvent("CP" + i + "_" + j,
                        MultiQubitGate.createCPHASE(angle, qubits.get(j), qubits.get(i)),
                        currentTime, qubits.get(i));
                    simulator.scheduleEvent(cp);
                    currentTime = currentTime.plusMillis(5);
                }
            }
            
            System.out.println("Running QFT simulation with resource constraints...");
            SimulationResult result = simulator.run();
            
            // 6. Print resource statistics
            ResourceStatistics stats = resourceManager.getStatistics();
            System.out.println("\n=== Resource Statistics ===");
            System.out.println(stats);
            
            ResourceUtilization util = resourceManager.getUtilization();
            System.out.println("Utilization: " + util);
            
            // 7. Check if we hit resource limits
            if (stats.getFailedAllocations() > 0) {
                System.out.println("Warning: Some resource requests failed!");
            }
            
            // 8. Release resources
            resourceManager.release(allocation);
            System.out.println("Resources released");
            
            // 9. Print simulation metrics
            System.out.println("\n=== Simulation Metrics ===");
            System.out.println("Total operations: " + metrics.getMetric("total_operations"));
            System.out.println("Simulation time: " + result.getTotalRuntimeMs() + "ms");
            
            simulator.reset();
            
        } catch (ResourceManager.ResourceAllocationException e) {
            System.err.println("Failed to allocate resources: " + e.getMessage());
            System.err.println("Available resources: " + e.getAvailableResources());
            
            // Fallback: Run with reduced resources
            System.out.println("Falling back to smaller simulation...");
            runReducedSimulation(resourceManager);
        }
    }
    
    private static void runReducedSimulation(ResourceManager resourceManager) throws Exception {
        // Request fewer resources
        ResourceRequest reducedRequest = ResourceRequest.newBuilder("reduced_simulation")
            .withCpuCores(1)
            .withMemoryMB(20)
            .withDuration(Duration.ofSeconds(30))
            .build();
        
        ResourceAllocation allocation = resourceManager.allocate(reducedRequest);
        
        // Run smaller simulation
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(4); // Reduced from 8
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // ... run simulation ...
        
        resourceManager.release(allocation);
    }
    
    // Example 4: Plugin-based simulation
    private static void runPluginBasedSimulation() throws Exception {
        System.out.println("\n=== Example 4: Plugin-Based Custom Simulation ===");
        
        // 1. Create plugin manager
        PluginManager pluginManager = new PluginManager();
        
        // 2. Register built-in plugins
        pluginManager.registerPlugin(new NoiseAnalysisPlugin());
        pluginManager.registerPlugin(new CircuitOptimizerPlugin());
        
        // 3. Configure simulation
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // 4. Initialize plugins with simulation context
        pluginManager.initializePlugins(simulator.getContext());
        
        // 5. Create a custom circuit
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        Qubit q2 = new Qubit(2, "q2");
        
        // GHZ state preparation: |000> -> (|000> + |111>)/√2
        Instant currentTime = Instant.now();
        
        QuantumEvent h0 = createGateEvent("H0", 
            SingleQubitGate.createHadamard(q0), 
            currentTime, q0);
        
        currentTime = currentTime.plusMillis(10);
        
        QuantumEvent cnot1 = createGateEvent("CNOT0-1",
            MultiQubitGate.createCNOT(q0, q1),
            currentTime, q1);
        
        currentTime = currentTime.plusMillis(10);
        
        QuantumEvent cnot2 = createGateEvent("CNOT1-2",
            MultiQubitGate.createCNOT(q1, q2),
            currentTime, q2);
        
        // Schedule events
        simulator.scheduleEvent(h0);
        simulator.scheduleEvent(cnot1);
        simulator.scheduleEvent(cnot2);
        
        // 6. Run simulation with plugins monitoring
        System.out.println("Running GHZ state preparation with plugins...");
        SimulationResult result = simulator.run();
        
        // 7. Cleanup plugins
        pluginManager.cleanupPlugins(simulator.getContext());
        
        // 8. Get plugin extensions if available
        NoiseAnalysisPlugin noisePlugin = pluginManager.getPlugin(
            "NoiseAnalysis", NoiseAnalysisPlugin.class);
        
        if (noisePlugin != null) {
            Object analysis = noisePlugin.getExtension("noise_analysis");
            System.out.println("\nNoise analysis from plugin: " + analysis);
        }
        
        System.out.println("\nGHZ State created:");
        System.out.println("Final State: " + result.getFinalState());
        
        simulator.reset();
    }
    
    // Example plugin implementations
    private static class NoiseAnalysisPlugin implements Plugin {
        private PluginContext context;
        private List<String> noiseEvents = new ArrayList<>();
        
        @Override
        public void initialize(PluginContext context) {
            this.context = context;
            context.logInfo("NoiseAnalysisPlugin initialized");
        }
        
        @Override
        public void cleanup() {
            context.logInfo("NoiseAnalysisPlugin cleaned up");
        }
        
        @Override
        public String getName() {
            return "NoiseAnalysis";
        }
        
        @Override
        public String getVersion() {
            return "1.0.0";
        }
        
        @Override
        public String getDescription() {
            return "Analyzes noise patterns in quantum circuits";
        }
        
        @Override
        public void onSimulationStart(SimulationContext simContext) {
            noiseEvents.clear();
            context.logInfo("Starting noise analysis for simulation");
        }
        
        @Override
        public void onOperationApplied(QuantumOperation operation) {
            if (operation.isNoise()) {
                noiseEvents.add("Noise applied: " + operation.getName() + 
                    " at " + System.currentTimeMillis());
            }
        }
        
        @Override
        public boolean supportsExtension(String extensionPoint) {
            return extensionPoint.equals("noise_analysis");
        }
        
        @Override
        public Object getExtension(String extensionPoint) {
            if (extensionPoint.equals("noise_analysis")) {
                return "Noise events: " + noiseEvents.size() + 
                    "\nDetails: " + noiseEvents;
            }
            return null;
        }
    }
    
    private static class CircuitOptimizerPlugin implements Plugin {
        private PluginContext context;
        
        @Override
        public void initialize(PluginContext context) {
            this.context = context;
            context.logInfo("CircuitOptimizerPlugin initialized");
        }
        
        @Override
        public void cleanup() {
            context.logInfo("CircuitOptimizerPlugin cleaned up");
        }
        
        @Override
        public String getName() {
            return "CircuitOptimizer";
        }
        
        @Override
        public String getVersion() {
            return "1.0.0";
        }
        
        @Override
        public String getDescription() {
            return "Optimizes quantum circuits by removing redundant gates";
        }
        
        @Override
        public void onEventScheduled(QuantumEvent event) {
            // Could analyze circuit and suggest optimizations
            if (event.getOperation() instanceof SingleQubitGate) {
                context.logInfo("Single-qubit gate scheduled: " + event.getId());
            }
        }
    }
    
    // Example 5: Classical-quantum hybrid algorithm
    private static void runClassicalHybridAlgorithm() throws Exception {
        System.out.println("\n=== Example 5: Variational Quantum Eigensolver (VQE) ===");
        
        // 1. Create Hamiltonian for H2 molecule (simplified)
        // H = aI + bZ0 + cZ1 + dX0X1 + eY0Y1
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        
        // Simple Hamiltonian: H = Z0 + Z1 - 0.5 * X0X1
        Observable z0 = Observable.pauliZ(q0);
        Observable z1 = Observable.pauliZ(q1);
        Observable x0x1 = Observable.pauliX(q0).tensorProduct(Observable.pauliX(q1));
        
        // 2. Create VQE algorithm
        List<Qubit> qubits = Arrays.asList(q0, q1);
        VariationalQuantumEigensolver vqe = new VariationalQuantumEigensolver(
            x0x1, // Using simplified Hamiltonian
            qubits
        );
        
        // 3. Configure simulation
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.setClassicalControlEnabled(true);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // 4. Initialize VQE with parameters
        Map<String, Object> vqeParams = new HashMap<>();
        vqeParams.put("max_iterations", 50);
        vqeParams.put("convergence_threshold", 1e-4);
        vqeParams.put("learning_rate", 0.1);
        
        vqe.initialize(simulator, vqeParams);
        
        // 5. Run VQE optimization
        System.out.println("Running VQE optimization...");
        System.out.println("Iteration | Energy | Parameters");
        System.out.println("----------|--------|------------");
        
        int iteration = 0;
        while (vqe.executeIteration()) {
            iteration++;
            double energy = vqe.getCurrentCost();
            Map<String, Object> params = vqe.getParameters();
            
            if (iteration % 5 == 0) {
                Map<String, Double> currentParameters = vqe.getOptimalParameters();
                System.out.printf(Locale.ROOT, "%9d | %6.4f | theta=%.3f, phi=%.3f\n",
                    iteration, energy,
                    currentParameters.getOrDefault("theta_0_0", 0.0),
                    currentParameters.getOrDefault("phi_0_0", 0.0));
            }
            
            if (vqe.isConverged()) {
                System.out.println("Converged after " + iteration + " iterations");
                break;
            }
        }
        
        // 6. Get results
        SimulationResult result = vqe.getResult();
        Map<String, Double> optimalParams = vqe.getOptimalParameters();
        
        System.out.println("\n=== VQE Results ===");
        System.out.println("Optimal energy: " + vqe.getEnergy());
        System.out.println("Optimal parameters: " + optimalParams);
        System.out.println("Iterations: " + vqe.getIterationCount());
        
        // 7. Plot convergence (simple ASCII plot)
        List<Double> costHistory = vqe.getCostHistory();
        if (!costHistory.isEmpty()) {
            System.out.println("\nConvergence history:");
            double minEnergy = Collections.min(costHistory);
            double maxEnergy = Collections.max(costHistory);
            double range = maxEnergy - minEnergy;
            
            for (int i = 0; i < costHistory.size(); i += 2) {
                double energy = costHistory.get(i);
                int barLength = (int) ((energy - minEnergy) / range * 50);
                System.out.printf(Locale.ROOT, "Iter %3d: %6.4f %s\n", 
                    i, energy, "#".repeat(barLength));
            }
        }
        
        // 8. Run final circuit with optimal parameters
        simulator.initialize(config);
        
        // Get the optimized circuit from VQE
        List<QuantumOperation> optimizedCircuit = vqe.getNextOperations();
        
        // Schedule the optimized circuit
        Instant currentTime = Instant.now();
        int optimizedIndex = 0;
        for (QuantumOperation op : optimizedCircuit) {
            QuantumEvent event = new QuantumEvent.Builder("vqe_optimized_" + (optimizedIndex++))
                .withScheduledTime(currentTime)
                .withOperation(op)
                .withTargetQubits(op.getTargetQubits())
                .build();
            simulator.scheduleEvent(event);
            currentTime = currentTime.plusMillis(10);
        }
        
        // Measure the final state
        MeasurementOperator finalMeasurement = new MeasurementOperator(
            Observable.pauliZ(new Qubit[]{q0, q1}), 1000);
        
        QuantumEvent measure = new QuantumEvent.Builder("measure_vqe")
            .withScheduledTime(currentTime)
            .withOperation(finalMeasurement)
            .withTargetQubits(q0, q1)
            .build();
        
        simulator.scheduleEvent(measure);
        
        SimulationResult finalResult = simulator.run();
        
        System.out.println("\nFinal state with optimized parameters:");
        System.out.println(finalResult.getFinalState());
        
        simulator.reset();
    }
    
    // Helper method to create gate events
    private static QuantumEvent createGateEvent(String name, QuantumOperation operation, 
                                               Instant time, Qubit... targetQubits) {
        Qubit[] targets = (targetQubits != null && targetQubits.length == operation.getTargetQubits().length) ? 
            targetQubits : operation.getTargetQubits();
        return new QuantumEvent.Builder(name)
            .withScheduledTime(time)
            .withOperation(operation)
            .withTargetQubits(targets)
            .withType(EventType.GATE_OPERATION)
            .withDescription("Apply " + operation.getName() + " gate")
            .build();
    }
}
package example;

import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationError;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.operations.*;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.events.QuantumEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URL;
import java.time.Instant;
import java.util.*;

public class CircuitRunner {
    
    // Default file names
    private static final String DEFAULT_CONFIG_DIR = "config";
    private static final String DEFAULT_CIRCUIT_DIR = "circuits";
    private static final String DEFAULT_CONFIG_NAME = "bell_state_config.json";
    private static final String DEFAULT_CIRCUIT_NAME = "bell_state_circuit.json";
    
    // Available demo circuits
    private static final Map<String, String> DEMO_CIRCUITS = Map.of(
        "bell", "bell_state_circuit.json",
        "grover", "grover_search_circuit.json",
        "qft", "qft_circuit.json",
        "teleport", "quantum_teleportation_circuit.json"
    );
    
    // Available configs
    private static final Map<String, String> DEMO_CONFIGS = Map.of(
        "bell", "bell_state_config.json",
        "grover", "grover_search_config.json",
        "qft", "qft_config.json",
        "teleport", "quantum_teleportation_config.json"
    );
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Quantum Circuit Runner ===");
        System.out.println("Usage: CircuitRunner [config_file] [circuit_file]");
        System.out.println("   or: CircuitRunner [demo_name]");
        System.out.println("   or: CircuitRunner (uses default Bell state)\n");
        
        String configFile;
        String circuitFile;
        
        // Handle command line arguments
        if (args.length == 0) {
            // No arguments - use defaults
            System.out.println("No arguments provided, using default Bell state simulation...");
            configFile = findResourceFile(DEFAULT_CONFIG_DIR, DEFAULT_CONFIG_NAME);
            circuitFile = findResourceFile(DEFAULT_CIRCUIT_DIR, DEFAULT_CIRCUIT_NAME);
        } 
        else if (args.length == 1) {
            // Single argument - could be demo name or config file
            String arg = args[0];
            
            // Check if it's a demo name
            if (DEMO_CONFIGS.containsKey(arg.toLowerCase())) {
                System.out.println("Loading " + arg + " demo...");
                configFile = findResourceFile(DEFAULT_CONFIG_DIR, DEMO_CONFIGS.get(arg.toLowerCase()));
                circuitFile = findResourceFile(DEFAULT_CIRCUIT_DIR, DEMO_CIRCUITS.get(arg.toLowerCase()));
            } 
            else {
                // Treat as config file path
                configFile = arg;
                circuitFile = findCircuitForConfig(arg);
            }
        } 
        else if (args.length == 2) {
            // Two arguments - config file and circuit file
            configFile = args[0];
            circuitFile = args[1];
        } 
        else {
            System.out.println("Error: Too many arguments");
            showHelp();
            return;
        }
        
        System.out.println("Config file: " + (configFile != null ? configFile : "DEFAULT"));
        System.out.println("Circuit file: " + (circuitFile != null ? circuitFile : "DEFAULT"));
        
        try {
            // Load configuration from JSON
            SimulationConfig config = loadConfig(configFile);
            
            // Create simulator
            QuantumSimulator simulator = new QuantumSimulator();
            simulator.initialize(config);
            
            // Load and execute circuit
            List<QuantumEvent> circuit = loadCircuit(circuitFile, config.getNumQubits());
            
            System.out.println("Scheduling " + circuit.size() + " quantum events...");
            for (QuantumEvent event : circuit) {
                simulator.scheduleEvent(event);
            }
            
            // Run simulation
            System.out.println("Running simulation...");
            SimulationResult result = simulator.run();
            
            // Print results
            printResults(result);
            
            simulator.reset();
            
        } catch (Exception e) {
            System.err.println("\nSimulation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void showHelp() {
        System.out.println("\nAvailable demos:");
        System.out.println("  bell    - Bell state entanglement (default)");
        System.out.println("  grover  - Grover's search algorithm");
        System.out.println("  qft     - Quantum Fourier Transform");
        System.out.println("  teleport - Quantum teleportation");
        System.out.println("\nExamples:");
        System.out.println("  CircuitRunner                      # Run default Bell state");
        System.out.println("  CircuitRunner grover               # Run Grover's search");
        System.out.println("  CircuitRunner my_config.json       # Use custom config");
        System.out.println("  CircuitRunner config.json circuit.json  # Both custom files");
    }
    
    private static String findResourceFile(String directory, String fileName) {
        // Try multiple locations in order
        
        // 1. Same directory as class (for development)
        String classDir = getClassDirectory();
        File classDirFile = new File(classDir, directory + "/" + fileName);
        if (classDirFile.exists()) {
            return classDirFile.getAbsolutePath();
        }
        
        File classDirExampleFile = new File(classDir, "example/" + directory + "/" + fileName);
        if (classDirExampleFile.exists()) {
            return classDirExampleFile.getAbsolutePath();
        }
        
        // 2. Current working directory
        File cwdFile = new File(directory + "/" + fileName);
        if (cwdFile.exists()) {
            return cwdFile.getAbsolutePath();
        }
        
        File sourceFile = new File("src/example/" + directory + "/" + fileName);
        if (sourceFile.exists()) {
            return sourceFile.getAbsolutePath();
        }
        
        // 3. Direct file path
        File directFile = new File(fileName);
        if (directFile.exists()) {
            return directFile.getAbsolutePath();
        }
        
        // 4. Classpath resources
        try {
            InputStream stream = CircuitRunner.class.getResourceAsStream(
                "/example/" + directory + "/" + fileName);
            if (stream != null) {
                stream.close();
                return CircuitRunner.class.getResource(
                    "/example/" + directory + "/" + fileName).getFile();
            }
            stream = CircuitRunner.class.getResourceAsStream("/" + directory + "/" + fileName);
            if (stream != null) {
                stream.close();
                return CircuitRunner.class.getResource("/" + directory + "/" + fileName).getFile();
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // 5. Try in resources folder
        File resourcesFile = new File("src/main/resources/" + directory + "/" + fileName);
        if (resourcesFile.exists()) {
            return resourcesFile.getAbsolutePath();
        }
        
        System.out.println("Could not find " + fileName + " in " + directory + " directory");
        return null;
    }
    
    private static String getClassDirectory() {
        try {
            URL classUrl = CircuitRunner.class.getResource(CircuitRunner.class.getSimpleName() + ".class");
            if (classUrl != null) {
                String classPath = classUrl.getPath();
                if (classPath.contains("!")) {
                    // In JAR file
                    return new File(".").getAbsolutePath();
                } else {
                    // In file system
                    File classFile = new File(classPath);
                    return classFile.getParentFile().getParentFile().getAbsolutePath();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return ".";
    }
    
    private static String findCircuitForConfig(String configPath) {
        // Try to find corresponding circuit file
        String fileName = new File(configPath).getName();
        
        // Replace "_config.json" with "_circuit.json"
        if (fileName.contains("_config.json")) {
            String circuitName = fileName.replace("_config.json", "_circuit.json");
            
            // Look in same directory
            File sameDir = new File(new File(configPath).getParent(), circuitName);
            if (sameDir.exists()) {
                return sameDir.getAbsolutePath();
            }
            
            // Look in circuits directory
            return findResourceFile(DEFAULT_CIRCUIT_DIR, circuitName);
        }
        
        // Default fallback
        return findResourceFile(DEFAULT_CIRCUIT_DIR, DEFAULT_CIRCUIT_NAME);
    }
    
    private static SimulationConfig loadConfig(String configPath) throws Exception {
        if (configPath == null) {
            System.out.println("No config file found, using default configuration");
            SimulationConfig config = new SimulationConfig();
            config.setNumQubits(2);
            config.setBackend(SimulationBackend.STATE_VECTOR);
            config.setMaxSteps(1000);
            return config;
        }
        
        File configFile = new File(configPath);
        if (!configFile.exists()) {
            throw new FileNotFoundException("Config file not found: " + configPath);
        }
        
        System.out.println("Loading config from: " + configFile.getAbsolutePath());
        SimulationConfig config = new SimulationConfig();
        config.loadFromFile(configPath);
        
        if (!config.validate()) {
            throw new IOException("Invalid simulation configuration: " + configPath);
        }
        
        return config;
    }
    
    private static List<QuantumEvent> loadCircuit(String circuitPath, int numQubits) throws Exception {
        if (circuitPath == null) {
            System.out.println("No circuit file found, creating default Bell state circuit");
            return createDefaultCircuit(numQubits);
        }
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
        
        File circuitFile = new File(circuitPath);
        if (!circuitFile.exists()) {
            throw new FileNotFoundException("Circuit file not found: " + circuitPath);
        }
        
        System.out.println("Loading circuit from: " + circuitFile.getAbsolutePath());
        CircuitDefinition circuitDef = mapper.readValue(circuitFile, CircuitDefinition.class);
        
        if (circuitDef.getGates() == null || circuitDef.getGates().isEmpty()) {
            throw new IOException("Circuit file contains no gates: " + circuitPath);
        }
        
        List<QuantumEvent> events = new ArrayList<>();
        Instant currentTime = Instant.now();
        
        for (GateOperation gateOp : circuitDef.getGates()) {
            if (gateOp.getTargetQubits() == null || gateOp.getTargetQubits().length == 0) {
                throw new IllegalArgumentException(
                    "Gate " + gateOp.getId() + " has no target qubits");
            }
            
            for (int qubitIndex : gateOp.getTargetQubits()) {
                if (qubitIndex < 0 || qubitIndex >= numQubits) {
                    throw new IllegalArgumentException(
                        "Qubit index " + qubitIndex + " out of range [0, " + (numQubits - 1) + "]");
                }
            }
            for (int qubitIndex : gateOp.getControlQubits()) {
                if (qubitIndex < 0 || qubitIndex >= numQubits) {
                    throw new IllegalArgumentException(
                        "Control qubit index " + qubitIndex + " out of range [0, " + (numQubits - 1) + "]");
                }
            }
            
            Qubit[] targets = new Qubit[gateOp.getTargetQubits().length];
            for (int i = 0; i < targets.length; i++) {
                targets[i] = new Qubit(gateOp.getTargetQubits()[i], "q" + gateOp.getTargetQubits()[i]);
            }
            
            QuantumOperation operation;
            try {
                operation = createGateOperation(
                    gateOp.getType(),
                    gateOp.getParameters(),
                    gateOp.getControlQubits(),
                    targets
                );
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    "Error processing gate " + gateOp.getId() + ": " + e.getMessage(), e);
            }
            
            QuantumEvent event = new QuantumEvent.Builder(gateOp.getId())
                .withScheduledTime(currentTime)
                .withOperation(operation)
                .withTargetQubits(targets)
                .withDescription(gateOp.getDescription() != null ? 
                    gateOp.getDescription() : "Apply " + gateOp.getType() + " gate")
                .build();
            
            events.add(event);
            currentTime = currentTime.plusMillis(10);
        }
        
        return events;
    }
    
    private static QuantumOperation createGateOperation(String type, Map<String, Object> params, 
                                                       int[] controlIndices, Qubit... targets) {
        try {
            GateLibrary gateLib = new GateLibrary();
            
            if (params == null) {
                params = Collections.emptyMap();
            }
            
            // Convert control indices to Qubit objects
            Qubit[] controls = null;
            if (controlIndices != null && controlIndices.length > 0) {
                controls = new Qubit[controlIndices.length];
                for (int i = 0; i < controlIndices.length; i++) {
                    controls[i] = new Qubit(controlIndices[i], "c" + controlIndices[i]);
                }
            }
            
            // Combine all qubits for gate creation
            List<Qubit> allQubits = new ArrayList<>();
            if (controls != null) {
                allQubits.addAll(Arrays.asList(controls));
            }
            allQubits.addAll(Arrays.asList(targets));
            
            return gateLib.createGate(type, params, allQubits.toArray(new Qubit[0]));
            
        } catch (Exception e) {
            // Fallback to basic gate creation
            System.err.println("[WARN]  Error creating gate " + type + ": " + e.getMessage());
            System.err.println("Using basic gate implementation");
            
            return createBasicGate(type, params, controlIndices, targets);
        }
    }
    
    private static QuantumOperation createBasicGate(String type, Map<String, Object> params,
                                                   int[] controlIndices, Qubit... targets) {
        // Basic fallback gate creation
        switch (type.toUpperCase()) {
            case "H":
            case "HADAMARD":
                if (targets.length != 1) throw new IllegalArgumentException("Hadamard requires exactly 1 target qubit");
                return SingleQubitGate.createHadamard(targets[0]);
            case "X":
                if (targets.length != 1) throw new IllegalArgumentException("X gate requires exactly 1 target qubit");
                return SingleQubitGate.createPauliX(targets[0]);
            case "Y":
                if (targets.length != 1) throw new IllegalArgumentException("Y gate requires exactly 1 target qubit");
                return SingleQubitGate.createPauliY(targets[0]);
            case "Z":
                if (targets.length != 1) throw new IllegalArgumentException("Z gate requires exactly 1 target qubit");
                return SingleQubitGate.createPauliZ(targets[0]);
            case "CNOT":
                if (controlIndices == null || controlIndices.length != 1 || targets.length != 1) {
                    throw new IllegalArgumentException("CNOT requires exactly 1 control and 1 target qubit");
                }
                Qubit control = new Qubit(controlIndices[0]);
                return MultiQubitGate.createCNOT(control, targets[0]);
            case "CZ":
                if (controlIndices == null || controlIndices.length != 1 || targets.length != 1) {
                    throw new IllegalArgumentException("CZ requires exactly 1 control and 1 target qubit");
                }
                Qubit controlCZ = new Qubit(controlIndices[0]);
                return MultiQubitGate.createCZ(controlCZ, targets[0]);
            case "SWAP":
                if (targets.length != 2) throw new IllegalArgumentException("SWAP requires exactly 2 target qubits");
                return MultiQubitGate.createSWAP(targets[0], targets[1]);
            default:
                throw new IllegalArgumentException("Unsupported gate type: " + type);
        }
    }
    
    private static List<QuantumEvent> createDefaultCircuit(int numQubits) {
        System.out.println("Creating default Bell state circuit for " + numQubits + " qubits");
        
        List<QuantumEvent> circuit = new ArrayList<>();
        List<Qubit> qubits = new ArrayList<>();
        
        for (int i = 0; i < numQubits; i++) {
            qubits.add(new Qubit(i, "q" + i));
        }
        
        Instant currentTime = Instant.now();
        
        // Simple default circuit: Bell state if possible, else just Hadamards
        if (numQubits >= 2) {
            // Create Bell state between first two qubits
            circuit.add(new QuantumEvent.Builder("H_default")
                .withScheduledTime(currentTime)
                .withOperation(SingleQubitGate.createHadamard(qubits.get(0)))
                .withTargetQubits(qubits.get(0))
                .withDescription("Default Hadamard on qubit 0")
                .build());
            
            currentTime = currentTime.plusMillis(10);
            
            circuit.add(new QuantumEvent.Builder("CNOT_default")
                .withScheduledTime(currentTime)
                .withOperation(MultiQubitGate.createCNOT(qubits.get(0), qubits.get(1)))
                .withTargetQubits(qubits.get(1))
                .withDescription("Default CNOT creating Bell state")
                .build());
        } else {
            // Single qubit - just apply Hadamard
            circuit.add(new QuantumEvent.Builder("H_single")
                .withScheduledTime(currentTime)
                .withOperation(SingleQubitGate.createHadamard(qubits.get(0)))
                .withTargetQubits(qubits.get(0))
                .withDescription("Default Hadamard on single qubit")
                .build());
        }
        
        return circuit;
    }
    
    private static void printResults(SimulationResult result) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("[OK] SIMULATION COMPLETED SUCCESSFULLY");
        System.out.println("=".repeat(50));
        
        System.out.println("\n[PLOT] Results:");
        System.out.println("  Runtime: " + result.getTotalRuntimeMs() + " ms");
        
        if (result.getFinalState() != null) {
            System.out.println("\n[LAB] Quantum State:");
            System.out.println("  " + result.getFinalState());
            
            System.out.println("\n[TARGET] Measurement Probabilities:");
            double[] probs = result.getFinalState().getProbabilities();
            int numQubits = result.getFinalState().getNumQubits();
            
            for (int i = 0; i < probs.length; i++) {
                if (probs[i] > 0.001) {
                    String binary = String.format(Locale.ROOT, "%" + numQubits + "s", 
                        Integer.toBinaryString(i)).replace(' ', '0');
                    System.out.printf(Locale.ROOT, "  |%s>: %.4f (%.1f%%)\n", binary, probs[i], probs[i] * 100);
                }
            }
        }
        
        // Print metrics if available
        Map<String, Number> metrics = result.getMetrics();
        if (!metrics.isEmpty()) {
            System.out.println("\n[CHART] Performance Metrics:");
            for (Map.Entry<String, Number> entry : metrics.entrySet()) {
                String key = entry.getKey().replace("_", " ");
                System.out.printf(Locale.ROOT, "  %-25s: %s\n", key, entry.getValue());
            }
        }
        
        System.out.println("\n" + "=".repeat(50));
    }
    
    // Inner classes for JSON parsing
    public static class CircuitDefinition {
        private String name;
        private String description;
        private List<GateOperation> gates;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<GateOperation> getGates() { return gates; }
        public void setGates(List<GateOperation> gates) { this.gates = gates; }
    }
    
    public static class GateOperation {
        private String id;
        private String type;
        private int[] targetQubits;
        private int[] controlQubits;
        private Map<String, Object> parameters;
        private String description;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public int[] getTargetQubits() { return targetQubits != null ? targetQubits : new int[0]; }
        public void setTargetQubits(int[] targetQubits) { this.targetQubits = targetQubits; }
        public int[] getControlQubits() { return controlQubits != null ? controlQubits : new int[0]; }
        public void setControlQubits(int[] controlQubits) { this.controlQubits = controlQubits; }
        public Map<String, Object> getParameters() { return parameters != null ? parameters : new HashMap<>(); }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
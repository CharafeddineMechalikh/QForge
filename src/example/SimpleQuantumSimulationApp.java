package example;

import java.util.Locale;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.observer.MetricsCollector;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.events.QuantumEvent;

/**
 * Minimal demonstration of a single-qubit circuit, a Bell state, and a two-qubit Grover circuit,
 * built with {@link EventBuilderHelper}.
 *
 * <p>All three demonstrations use the state vector backend and print the final state and
 * probabilities. The program performs no verification of the results and writes no files.</p>
 */
public class SimpleQuantumSimulationApp {
    /**
     * Runs the three demonstration methods in sequence.
     *
     * <p>No command line arguments are read. If a demonstration throws, the remaining
     * demonstrations are skipped and the error is printed.</p>
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.println("=== Quantum Simulation Framework ===");
        
        try {
            // Example 1: Basic single-qubit simulation
            runSingleQubitExample();
            
            // Example 2: Entanglement demonstration
            runEntanglementExample();
            
            // Example 3: Grover's search algorithm
            runGroversSearchExample();
            
        } catch (Exception e) {
            System.err.println("Simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void runSingleQubitExample() throws Exception {
        System.out.println("\n=== Example 1: Single Qubit Operations ===");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.setMaxSteps(100);
        
        QuantumSimulator simulator = new QuantumSimulator();
        MetricsCollector metrics = new MetricsCollector();
        simulator.addObserver(metrics);
        simulator.initialize(config);
        
        Qubit q0 = new Qubit(0, "q0");
        
        EventBuilderHelper.resetTimer();
        
        // Apply Hadamard gate
        QuantumEvent hadamardEvent = EventBuilderHelper.createSingleQubitGate(
            "H", SingleQubitGate.createHadamard(q0), q0);
        
        // Apply X gate (bit flip)
        QuantumEvent xGateEvent = EventBuilderHelper.createSingleQubitGate(
            "X", SingleQubitGate.createPauliX(q0), q0);
        
        simulator.scheduleEvent(hadamardEvent);
        simulator.scheduleEvent(xGateEvent);
        
        System.out.println("Running simulation...");
        SimulationResult result = simulator.run();
        
        System.out.println("\nSingle Qubit Simulation Result:");
        System.out.println("Final State: " + result.getFinalState());
        
        simulator.reset();
    }
    
    private static void runEntanglementExample() throws Exception {
        System.out.println("\n=== Example 2: Bell State (Entanglement) ===");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        
        EventBuilderHelper.resetTimer();
        
        // Debug: Check what getTargetQubits() returns for CNOT
        QuantumOperation cnotOp = MultiQubitGate.createCNOT(q0, q1);
        System.out.println("CNOT operation target qubits count: " + cnotOp.getTargetQubits().length);
        
        // Create events - let the helper handle the details
        QuantumEvent h0 = EventBuilderHelper.createSingleQubitGate(
            "H0", SingleQubitGate.createHadamard(q0), q0);
        
        QuantumEvent cnot = EventBuilderHelper.createCNOT("CNOT", q0, q1);
        
        simulator.scheduleEvent(h0);
        simulator.scheduleEvent(cnot);
        
        SimulationResult result = simulator.run();
        
        System.out.println("Bell State Created:");
        System.out.println("Final State: " + result.getFinalState());
        
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            System.out.println("\nProbabilities:");
            System.out.printf(Locale.ROOT, "  |00>: %.4f\n", probs[0]);
            System.out.printf(Locale.ROOT, "  |01>: %.4f\n", probs[1]);
            System.out.printf(Locale.ROOT, "  |10>: %.4f\n", probs[2]);
            System.out.printf(Locale.ROOT, "  |11>: %.4f\n", probs[3]);
        }
        
        simulator.reset();
    }
    
    private static void runGroversSearchExample() throws Exception {
        System.out.println("\n=== Example 3: Grover's Search Algorithm ===");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        
        EventBuilderHelper.resetTimer();
        
        // Step 1: Initialize superposition
        QuantumEvent h0 = EventBuilderHelper.createSingleQubitGate(
            "H0", SingleQubitGate.createHadamard(q0), q0);
        QuantumEvent h1 = EventBuilderHelper.createSingleQubitGate(
            "H1", SingleQubitGate.createHadamard(q1), q1);
        
        // Step 2: Oracle (marks |11> state with CZ)
        QuantumEvent oracle = EventBuilderHelper.createCZ("Oracle", q0, q1);
        
        // Step 3: Diffusion operator
        QuantumEvent diff_h0 = EventBuilderHelper.createSingleQubitGate(
            "DiffH0", SingleQubitGate.createHadamard(q0), q0);
        QuantumEvent diff_h1 = EventBuilderHelper.createSingleQubitGate(
            "DiffH1", SingleQubitGate.createHadamard(q1), q1);
        
        QuantumEvent diff_x0 = EventBuilderHelper.createSingleQubitGate(
            "DiffX0", SingleQubitGate.createPauliX(q0), q0);
        QuantumEvent diff_x1 = EventBuilderHelper.createSingleQubitGate(
            "DiffX1", SingleQubitGate.createPauliX(q1), q1);
        
        QuantumEvent diff_cz = EventBuilderHelper.createCZ("DiffCZ", q0, q1);
        
        QuantumEvent diff_x0_2 = EventBuilderHelper.createSingleQubitGate(
            "DiffX0_2", SingleQubitGate.createPauliX(q0), q0);
        QuantumEvent diff_x1_2 = EventBuilderHelper.createSingleQubitGate(
            "DiffX1_2", SingleQubitGate.createPauliX(q1), q1);
        
        QuantumEvent diff_h0_2 = EventBuilderHelper.createSingleQubitGate(
            "DiffH0_2", SingleQubitGate.createHadamard(q0), q0);
        QuantumEvent diff_h1_2 = EventBuilderHelper.createSingleQubitGate(
            "DiffH1_2", SingleQubitGate.createHadamard(q1), q1);
        
        // Schedule all events
        QuantumEvent[] circuit = {
            h0, h1, oracle, 
            diff_h0, diff_h1, diff_x0, diff_x1, diff_cz,
            diff_x0_2, diff_x1_2, diff_h0_2, diff_h1_2
        };
        
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        SimulationResult result = simulator.run();
        
        System.out.println("Grover's Search Result:");
        System.out.println("Final State: " + result.getFinalState());
        
        if (result.getFinalState() != null) {
            double[] probabilities = result.getFinalState().getProbabilities();
            System.out.println("\nMeasurement Probabilities:");
            for (int i = 0; i < probabilities.length; i++) {
                String binary = String.format(Locale.ROOT, "%2s", Integer.toBinaryString(i)).replace(' ', '0');
                System.out.printf(Locale.ROOT, "|%s>: %.4f\n", binary, probabilities[i]);
            }
        }
        
        simulator.reset();
    }
}
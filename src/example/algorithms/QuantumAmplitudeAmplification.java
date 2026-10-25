package example.algorithms;

import com.quantum.simulator.state.Qubit;

import example.EventBuilderHelper;

import com.quantum.simulator.operations.*;
import com.quantum.simulator.events.QuantumEvent;
import java.util.*;

/**
 * Builds amplitude amplification circuits from a caller supplied initialization circuit and
 * oracle circuit.
 *
 * <p>The multi-controlled Z gate used inside the diffusion step is a simplified decomposition: two
 * qubits use a CZ, three qubits use a short CNOT and RZ sequence, and larger registers use a
 * cascade of CNOTs. These decompositions are marked as simplified in the code and are not
 * claimed to be optimal. The class only assembles events; it does not run a simulation.</p>
 */
public class QuantumAmplitudeAmplification {
    
    // Generalization of Grover's algorithm
    /**
     * Appends amplification iterations to an initialization circuit.
     *
     * <p>Each iteration appends the oracle events followed by a standard diffusion operator. The
     * supplied events are reused as they are, and no validation of the iteration count is
     * performed.</p>
     *
     * @param qubits the qubits used to build the diffusion operator
     * @param initializationCircuit the events that prepare the initial state
     * @param oracleCircuit the events that mark the solutions
     * @param iterations the number of oracle and diffusion repetitions
     * @return the combined events, in the order they should be scheduled
     */
    public static QuantumEvent[] amplify(
        Qubit[] qubits, 
        QuantumEvent[] initializationCircuit,
        QuantumEvent[] oracleCircuit,
        int iterations
    ) {
        List<QuantumEvent> circuit = new ArrayList<>();
        
        // Initialization
        for (QuantumEvent event : initializationCircuit) {
            circuit.add(event);
        }
        
        // Amplification iterations
        for (int i = 0; i < iterations; i++) {
            // Oracle
            for (QuantumEvent event : oracleCircuit) {
                circuit.add(event);
            }
            
            // Diffusion (inversion about average)
            circuit.addAll(createDiffusion(qubits, "Diff_" + i));
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    // Missing method: creates the diffusion operator
    private static List<QuantumEvent> createDiffusion(Qubit[] qubits, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        if (n == 1) {
            // For single qubit: D = H X Z X H
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_H", SingleQubitGate.createHadamard(qubits[0]), qubits[0]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_X", SingleQubitGate.createPauliX(qubits[0]), qubits[0]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_Z", SingleQubitGate.createPauliZ(qubits[0]), qubits[0]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_X2", SingleQubitGate.createPauliX(qubits[0]), qubits[0]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_H2", SingleQubitGate.createHadamard(qubits[0]), qubits[0]));
        } else {
            // For multiple qubits: D = Hxn (2|0><0| - I) Hxn
            // = Hxn Xxn CZxn Xxn Hxn (for n=2)
            // For n>2, need multi-controlled Z
            
            // Apply Hxn
            for (int i = 0; i < n; i++) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    name + "_H" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
            }
            
            // Apply Xxn
            for (int i = 0; i < n; i++) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    name + "_X" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
            }
            
            // Apply multi-controlled Z (conditioned on all qubits being |0>)
            events.addAll(createMultiControlledZ(qubits, name + "_MCZ", false));
            
            // Apply Xxn again
            for (int i = 0; i < n; i++) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    name + "_X2_" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
            }
            
            // Apply Hxn again
            for (int i = 0; i < n; i++) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    name + "_H2_" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
            }
        }
        
        return events;
    }
    
    // Helper method to create multi-controlled Z gate
    private static List<QuantumEvent> createMultiControlledZ(
        Qubit[] qubits, String name, boolean applyToAllQubits) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        if (n == 2) {
            // For 2 qubits: CZ gate
            events.add(EventBuilderHelper.createCZ(name, qubits[0], qubits[1]));
        } else if (n == 3) {
            // For 3 qubits: CCCZ using ancilla or decomposition
            // Simplified decomposition (not optimal)
            events.add(EventBuilderHelper.createCNOT(name + "_CNOT1", qubits[0], qubits[2]));
            events.add(EventBuilderHelper.createCNOT(name + "_CNOT2", qubits[1], qubits[2]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_RZ", SingleQubitGate.createRZ(Math.PI/4, qubits[2]), qubits[2]));
            events.add(EventBuilderHelper.createCNOT(name + "_CNOT3", qubits[1], qubits[2]));
            events.add(EventBuilderHelper.createCNOT(name + "_CNOT4", qubits[0], qubits[2]));
        } else {
            // For more qubits, use a cascade of Toffoli gates
            // This is a simplified implementation
            events.addAll(createCascadeMultiControlledZ(qubits, name));
        }
        
        return events;
    }
    
    private static List<QuantumEvent> createCascadeMultiControlledZ(
        Qubit[] qubits, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        if (n < 2) return events;
        
        // Use ancilla-based approach or decomposition
        // Here's a simplified approach for demonstration
        
        // Apply H to last qubit
        events.add(EventBuilderHelper.createSingleQubitGate(
            name + "_H_last", SingleQubitGate.createHadamard(qubits[n-1]), qubits[n-1]));
        
        // Apply multi-controlled NOT (Toffoli cascade) with last qubit as target
        events.addAll(createMultiControlledX(qubits, name + "_MCX"));
        
        // Apply Z to last qubit
        events.add(EventBuilderHelper.createSingleQubitGate(
            name + "_Z_last", SingleQubitGate.createPauliZ(qubits[n-1]), qubits[n-1]));
        
        // Apply multi-controlled NOT again
        events.addAll(createMultiControlledX(qubits, name + "_MCX2"));
        
        // Apply H to last qubit again
        events.add(EventBuilderHelper.createSingleQubitGate(
            name + "_H_last2", SingleQubitGate.createHadamard(qubits[n-1]), qubits[n-1]));
        
        return events;
    }
    
    private static List<QuantumEvent> createMultiControlledX(
        Qubit[] qubits, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        if (n < 2) return events;
        
        // Cascade of Toffoli gates
        for (int i = 0; i < n - 2; i++) {
            // Use qubits[i] and qubits[i+1] as controls for qubits[i+2]
            // This is a simplified decomposition
            events.add(EventBuilderHelper.createCNOT(
                name + "_CNOT" + i + "_1", qubits[i], qubits[i+1]));
            events.add(EventBuilderHelper.createCNOT(
                name + "_CNOT" + i + "_2", qubits[i+1], qubits[n-1]));
            events.add(EventBuilderHelper.createCNOT(
                name + "_CNOT" + i + "_3", qubits[i], qubits[i+1]));
        }
        
        return events;
    }
    
    // Alternative: Simplified diffusion for NISQ devices
    /**
     * Builds a simplified diffusion operator.
     *
     * <p>For a single qubit the sequence is H, X, H. For several qubits the method recursively
     * applies that single-qubit sequence to each qubit separately, so the result is a tensor
     * product of single-qubit reflections rather than an inversion about the global average.</p>
     *
     * @param qubits the qubits the operator acts on
     * @param name the prefix used for the event names
     * @return the events of the operator
     */
    public static List<QuantumEvent> createSimplifiedDiffusion(
        Qubit[] qubits, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // For near-term devices, use simpler diffusion operators
        if (n == 1) {
            // Single qubit: D = -H X H
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_H", SingleQubitGate.createHadamard(qubits[0]), qubits[0]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_X", SingleQubitGate.createPauliX(qubits[0]), qubits[0]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_H2", SingleQubitGate.createHadamard(qubits[0]), qubits[0]));
        } else {
            // Use tensor product of single-qubit diffusions
            for (int i = 0; i < n; i++) {
                events.addAll(createSimplifiedDiffusion(
                    new Qubit[]{qubits[i]}, name + "_q" + i));
            }
        }
        
        return events;
    }
    
    // Complete amplitude amplification with initialization helper
    /**
     * Builds an initialization circuit, then appends oracle and diffusion iterations.
     *
     * <p>The initialization type is matched case insensitively after conversion to upper case.
     * Recognized values are {@code UNIFORM} and {@code PLUS_STATE} (both produce a Hadamard on
     * every qubit), {@code W_STATE} (a fixed three-qubit preparation that is skipped for fewer
     * than three qubits), and {@code GHZ_STATE}. Any other value falls back to the uniform
     * preparation.</p>
     *
     * @param qubits the qubits of the circuit
     * @param initializationType the name of the initialization to build
     * @param oracleCircuit the events that mark the solutions
     * @param iterations the number of oracle and diffusion repetitions
     * @param useSimplifiedDiffusion {@code true} to use the tensor product diffusion, {@code false}
     *     to use the standard diffusion
     * @return the events of the circuit, in the order they should be scheduled
     */
    public static QuantumEvent[] amplifyWithInitialization(
        Qubit[] qubits,
        String initializationType, // "UNIFORM", "CUSTOM", "PLUS_STATE"
        QuantumEvent[] oracleCircuit,
        int iterations,
        boolean useSimplifiedDiffusion
    ) {
        List<QuantumEvent> circuit = new ArrayList<>();
        
        // Create initialization circuit
        QuantumEvent[] initCircuit = createInitializationCircuit(
            qubits, initializationType);
        
        // Add initialization
        for (QuantumEvent event : initCircuit) {
            circuit.add(event);
        }
        
        // Amplification iterations
        for (int i = 0; i < iterations; i++) {
            // Oracle
            for (QuantumEvent event : oracleCircuit) {
                circuit.add(event);
            }
            
            // Diffusion
            if (useSimplifiedDiffusion) {
                circuit.addAll(createSimplifiedDiffusion(qubits, "SDiff_" + i));
            } else {
                circuit.addAll(createDiffusion(qubits, "Diff_" + i));
            }
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static QuantumEvent[] createInitializationCircuit(
        Qubit[] qubits, String type) {
        List<QuantumEvent> events = new ArrayList<>();
        
        switch (type.toUpperCase()) {
            case "UNIFORM":
            case "PLUS_STATE":
                // |+>xn state
                for (int i = 0; i < qubits.length; i++) {
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "Init_H" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
                }
                break;
                
            case "W_STATE":
                // W state: (|100...0> + |010...0> + ... + |000...1>)/√n
                if (qubits.length >= 3) {
                    // Simplified W state preparation for 3 qubits
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "Init_RY1", SingleQubitGate.createRY(Math.acos(1/Math.sqrt(3)), qubits[0]), qubits[0]));
                    events.add(EventBuilderHelper.createCNOT(
                        "Init_CNOT1", qubits[0], qubits[1]));
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "Init_RY2", SingleQubitGate.createRY(Math.PI/4, qubits[1]), qubits[1]));
                    events.add(EventBuilderHelper.createCNOT(
                        "Init_CNOT2", qubits[1], qubits[2]));
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "Init_X", SingleQubitGate.createPauliX(qubits[2]), qubits[2]));
                }
                break;
                
            case "GHZ_STATE":
                // GHZ state: (|0...0> + |1...1>)/√2
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Init_H", SingleQubitGate.createHadamard(qubits[0]), qubits[0]));
                for (int i = 1; i < qubits.length; i++) {
                    events.add(EventBuilderHelper.createCNOT(
                        "Init_CNOT" + i, qubits[0], qubits[i]));
                }
                break;
                
            default:
                // Default: uniform superposition
                for (int i = 0; i < qubits.length; i++) {
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "Init_H" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
                }
        }
        
        return events.toArray(new QuantumEvent[0]);
    }
    
    // Example oracle circuits for common problems
    /**
     * Builds an oracle that marks one computational basis state.
     *
     * <p>For the all-ones state the method applies the multi-controlled Z directly. For the zero
     * state and for any other pattern it applies X gates to the qubits that must be zero, applies
     * the multi-controlled Z, then undoes the X gates.</p>
     *
     * @param qubits the qubits of the register
     * @param markedState the basis state to mark, read with the first qubit as most significant
     * @param name the prefix used for the event names
     * @return the events of the oracle
     */
    public static QuantumEvent[] createMarkingOracle(
        Qubit[] qubits, int markedState, String name) {
        // Creates oracle that marks a specific computational basis state
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // For |11...1> state: multi-controlled Z
        if (markedState == (1 << n) - 1) {
            // All ones: apply multi-controlled Z
            events.addAll(createMultiControlledZ(qubits, name + "_MCZ", true));
        } 
        // For |00...0> state: Xxn then multi-controlled Z then Xxn
        else if (markedState == 0) {
            // Apply X to all qubits
            for (int i = 0; i < n; i++) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    name + "_X" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
            }
            
            // Apply multi-controlled Z
            events.addAll(createMultiControlledZ(qubits, name + "_MCZ", true));
            
            // Apply X to all qubits again
            for (int i = 0; i < n; i++) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    name + "_X2_" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
            }
        } else {
            // For arbitrary marked state, need to condition on specific bit pattern
            // Convert markedState to binary and apply X where bits are 0
            for (int i = 0; i < n; i++) {
                int bit = (markedState >> (n - 1 - i)) & 1;
                if (bit == 0) {
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        name + "_X" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
                }
            }
            
            // Apply multi-controlled Z
            events.addAll(createMultiControlledZ(qubits, name + "_MCZ", true));
            
            // Undo the X gates
            for (int i = 0; i < n; i++) {
                int bit = (markedState >> (n - 1 - i)) & 1;
                if (bit == 0) {
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        name + "_X2_" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
                }
            }
        }
        
        return events.toArray(new QuantumEvent[0]);
    }
    
    // Demo method showing complete amplitude amplification
    /**
     * Builds and prints an amplitude amplification circuit for the state {@code |101>} on three
     * qubits.
     *
     * <p>The method only assembles the circuit and prints its gate count and the marked state; it
     * does not schedule or run a simulation.</p>
     *
     * @throws Exception declared for symmetry with simulator calls, not thrown by this method
     */
    public static void demoAmplitudeAmplification() throws Exception {
        System.out.println("=== Quantum Amplitude Amplification Demo ===");
        
        // Setup
        int numQubits = 3;
        Qubit[] qubits = new Qubit[numQubits];
        for (int i = 0; i < numQubits; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        
        // Mark state |101> (binary 101 = decimal 5)
        int markedState = 5;
        
        // Create oracle that marks |101>
        QuantumEvent[] oracle = createMarkingOracle(qubits, markedState, "Oracle");
        
        // Run amplitude amplification
        QuantumEvent[] circuit = amplifyWithInitialization(
            qubits,
            "UNIFORM",  // Start in uniform superposition
            oracle,
            2,          // Number of iterations
            false       // Use standard diffusion
        );
        
        System.out.println("Circuit created with " + circuit.length + " gates");
        System.out.println("Marked state: |" + 
            String.format(Locale.ROOT, "%" + numQubits + "s", Integer.toBinaryString(markedState))
                .replace(' ', '0') + ">");
        
        // You would then run this circuit on your simulator
    }
    
    // Utility method to calculate optimal number of Grover iterations
    /**
     * Calculates {@code floor(PI / 4 * sqrt(N / M))}, the usual Grover iteration count for
     * {@code M} solutions in a space of size {@code N}.
     *
     * @param searchSpaceSize the number of basis states, {@code N}
     * @param numSolutions the number of marked states, {@code M}
     * @return the iteration count, truncated toward zero
     * @throws IllegalArgumentException if {@code numSolutions} is not positive or exceeds the
     *     search space size
     */
    public static int calculateOptimalIterations(int searchSpaceSize, int numSolutions) {
        // Optimal number: floor(π/4 * √(N/M))
        if (numSolutions <= 0 || numSolutions > searchSpaceSize) {
            throw new IllegalArgumentException("Invalid number of solutions");
        }
        
        double optimal = (Math.PI / 4.0) * 
            Math.sqrt(searchSpaceSize / (double) numSolutions);
        
        return (int) Math.floor(optimal);
    }
}
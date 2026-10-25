package example;

import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.operations.*;
import com.quantum.simulator.events.QuantumEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds small, fixed quantum circuits as arrays of {@link QuantumEvent} instances.
 *
 * <p>The factory methods return events that are ordered by the shared timer of
 * {@link EventBuilderHelper}, so a caller can schedule them in array order to obtain the
 * intended gate sequence. Events are not scheduled on a simulator here.</p>
 */
public class CircuitFactory {
    
    /**
     * Creates a two-qubit Bell state preparation circuit: a Hadamard on {@code q0} followed by
     * a CNOT controlled by {@code q0} with {@code q1} as target.
     *
     * @param q0 the control qubit, which receives the Hadamard gate
     * @param q1 the target qubit of the CNOT gate
     * @return two events, in the order they should be scheduled
     */
    public static QuantumEvent[] createBellStateCircuit(Qubit q0, Qubit q1) {
        
        return new QuantumEvent[] {
            EventBuilderHelper.createSingleQubitGate("H", 
                SingleQubitGate.createHadamard(q0), q0),
            EventBuilderHelper.createCNOT("CNOT", q0, q1)
        };
    }
    
    /**
     * Creates a hard-coded two-qubit Grover style circuit: a uniform superposition, a CZ oracle,
     * and one diffusion step built from Hadamards, Pauli-X gates, a CZ and the inverse rotations.
     *
     * <p>The oracle marks the all-ones state and the circuit applies a single iteration only, so
     * this is a demonstration of the gate sequence rather than a general search routine.</p>
     *
     * @param q0 the first qubit
     * @param q1 the second qubit
     * @return the events of the circuit, in the order they should be scheduled
     */
    public static QuantumEvent[] createGroversSearchCircuit(Qubit q0, Qubit q1) {
        
        List<QuantumEvent> circuit = new ArrayList<>();
        
        // Initialize superposition
        circuit.add(EventBuilderHelper.createSingleQubitGate("H0", 
            SingleQubitGate.createHadamard(q0), q0));
        circuit.add(EventBuilderHelper.createSingleQubitGate("H1", 
            SingleQubitGate.createHadamard(q1), q1));
        
        // Oracle
        circuit.add(EventBuilderHelper.createCZ("Oracle", q0, q1));
        
        // Diffusion operator
        circuit.add(EventBuilderHelper.createSingleQubitGate("DiffH0", 
            SingleQubitGate.createHadamard(q0), q0));
        circuit.add(EventBuilderHelper.createSingleQubitGate("DiffH1", 
            SingleQubitGate.createHadamard(q1), q1));
        
        circuit.add(EventBuilderHelper.createSingleQubitGate("DiffX0", 
            SingleQubitGate.createPauliX(q0), q0));
        circuit.add(EventBuilderHelper.createSingleQubitGate("DiffX1", 
            SingleQubitGate.createPauliX(q1), q1));
        
        circuit.add(EventBuilderHelper.createCZ("DiffCZ", q0, q1));
        
        circuit.add(EventBuilderHelper.createSingleQubitGate("DiffX0_2", 
            SingleQubitGate.createPauliX(q0), q0));
        circuit.add(EventBuilderHelper.createSingleQubitGate("DiffX1_2", 
            SingleQubitGate.createPauliX(q1), q1));
        
        circuit.add(EventBuilderHelper.createSingleQubitGate("DiffH0_2", 
            SingleQubitGate.createHadamard(q0), q0));
        circuit.add(EventBuilderHelper.createSingleQubitGate("DiffH1_2", 
            SingleQubitGate.createHadamard(q1), q1));
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    /**
     * Creates a Quantum Fourier Transform circuit for the given qubits.
     *
     * <p>The implementation applies a Hadamard to each qubit followed by controlled phase gates
     * with angle {@code PI / 2^(j - i)}, then swaps qubits to reverse their order. No measurement
     * or state preparation is included.</p>
     *
     * @param qubits the qubits to transform, in register order
     * @return the events of the circuit, in the order they should be scheduled
     */
    public static QuantumEvent[] createQuantumFourierTransform(Qubit[] qubits) {
        List<QuantumEvent> circuit = new ArrayList<>();
        int n = qubits.length;
        
        for (int i = 0; i < n; i++) {
            // Apply Hadamard to qubit i
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "H" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
            
            // Apply controlled phase rotations
            for (int j = i + 1; j < n; j++) {
                double angle = Math.PI / Math.pow(2, j - i);
                // Use createCPHASE helper method
                circuit.add(EventBuilderHelper.createCPHASE(
                    "CP" + i + "_" + j, angle, qubits[j], qubits[i]));
            }
        }
        
        // Swap qubits (reverse order)
        for (int i = 0; i < n / 2; i++) {
            circuit.add(EventBuilderHelper.createSWAP(
                "SWAP" + i + "_" + (n - 1 - i), 
                qubits[i], qubits[n - 1 - i]));
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
}
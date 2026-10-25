package example;

import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.operations.*;
import com.quantum.simulator.events.QuantumEvent;
import java.util.ArrayList;
import java.util.List;

public class CircuitFactory {
    
    public static QuantumEvent[] createBellStateCircuit(Qubit q0, Qubit q1) {
        
        return new QuantumEvent[] {
            EventBuilderHelper.createSingleQubitGate("H", 
                SingleQubitGate.createHadamard(q0), q0),
            EventBuilderHelper.createCNOT("CNOT", q0, q1)
        };
    }
    
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
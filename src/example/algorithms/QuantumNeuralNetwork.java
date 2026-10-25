package example.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.Qubit;

import example.EventBuilderHelper;

public class QuantumNeuralNetwork {
    
    public static QuantumEvent[] createQNNCircuit(
        Qubit[] qubits,
        double[] inputData, // Classical input data
        Map<String, Double> weights, // Neural network weights
        int layers
    ) {
        List<QuantumEvent> circuit = new ArrayList<>();
        int n = qubits.length;
        
        // Encode classical data into quantum state
        circuit.addAll(encodeData(qubits, inputData, "Encode"));
        
        // Apply multiple QNN layers
        for (int layer = 0; layer < layers; layer++) {
            circuit.addAll(createQNNLayer(qubits, weights, layer));
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static List<QuantumEvent> encodeData(
        Qubit[] qubits, double[] data, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        
        // Amplitude encoding: |ψ> = Σᵢ xᵢ|i> / ||x||
        // Simplified: use rotation angles proportional to data
        for (int i = 0; i < Math.min(qubits.length, data.length); i++) {
            double angle = data[i] * Math.PI; // Scale data to [0, π]
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_RY" + i, SingleQubitGate.createRY(angle, qubits[i]), qubits[i]));
        }
        
        return events;
    }
    
    private static List<QuantumEvent> createQNNLayer(
        Qubit[] qubits, Map<String, Double> weights, int layer) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // Parameterized single-qubit rotations
        for (int i = 0; i < n; i++) {
            String key = "w" + layer + "_" + i + "_rot";
            double angle = weights.getOrDefault(key, 0.0);
            events.add(EventBuilderHelper.createSingleQubitGate(
                "L" + layer + "_RY" + i, SingleQubitGate.createRY(angle, qubits[i]), qubits[i]));
        }
        
        // Entangling layer (all-to-all or nearest-neighbor)
        for (int i = 0; i < n - 1; i++) {
            for (int j = i + 1; j < n; j++) {
                String key = "w" + layer + "_" + i + "_" + j;
                double angle = weights.getOrDefault(key, 0.0);
                events.add(EventBuilderHelper.createCPHASE(
                    "L" + layer + "_CP" + i + "_" + j, angle, qubits[i], qubits[j]));
            }
        }
        
        return events;
    }
}
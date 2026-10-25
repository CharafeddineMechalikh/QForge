package example.algorithms;

import com.quantum.simulator.state.Qubit;
import example.EventBuilderHelper;
import com.quantum.simulator.operations.*;
import com.quantum.simulator.events.QuantumEvent;
import java.util.*;

public class GroversAlgorithm {
    
    public static QuantumEvent[] createGroversCircuit(Qubit[] qubits, int targetState) {
        int n = qubits.length;
        if (n < 2) {
            throw new IllegalArgumentException("Grover's algorithm requires at least 2 qubits");
        }
        
        int searchSpace = 1 << n;
        if (targetState < 0 || targetState >= searchSpace) {
            throw new IllegalArgumentException(
                "Target state " + targetState + " is outside the " + n + "-qubit search space");
        }
        
        int iterations = Math.max(1, (int) Math.floor(Math.PI / 4.0 * Math.sqrt(searchSpace)));
        
        System.out.printf(Locale.ROOT, "Searching for |%s> in %d-qubit space%n",
            toBinaryString(targetState, n), n);
        System.out.printf(Locale.ROOT,
            "Search space size: N = %d, Grover iterations: %d%n", searchSpace, iterations);
        
        List<QuantumEvent> circuit = new ArrayList<>();
        
        for (int i = 0; i < n; i++) {
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "H" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
        }
        
        for (int iteration = 0; iteration < iterations; iteration++) {
            circuit.addAll(createOracle(qubits, targetState, "Oracle" + iteration));
            circuit.addAll(createDiffusionOperator(qubits, "Diffusion" + iteration));
        }
        
        System.out.printf(Locale.ROOT, "Circuit has %d gates%n", circuit.size());
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static String toBinaryString(int value, int bits) {
        String binary = Integer.toBinaryString(value);
        while (binary.length() < bits) {
            binary = "0" + binary;
        }
        return binary;
    }
    
    private static List<QuantumEvent> createOracle(Qubit[] qubits, int targetState, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        for (int i = 0; i < n; i++) {
            int bit = (targetState >> (n - 1 - i)) & 1;
            if (bit == 0) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    name + "_X" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
            }
        }
        
        events.add(EventBuilderHelper.createEvent(
            name + "_MCZ", MultiQubitGate.createMultiControlledZ(qubits), qubits, new Qubit[0]));
        
        for (int i = 0; i < n; i++) {
            int bit = (targetState >> (n - 1 - i)) & 1;
            if (bit == 0) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    name + "_Xundo" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
            }
        }
        
        return events;
    }
    
    private static List<QuantumEvent> createDiffusionOperator(Qubit[] qubits, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        for (int i = 0; i < n; i++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_H" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
        }
        for (int i = 0; i < n; i++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_X" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
        }
        
        events.add(EventBuilderHelper.createEvent(
            name + "_MCZ", MultiQubitGate.createMultiControlledZ(qubits), qubits, new Qubit[0]));
        
        for (int i = 0; i < n; i++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_Xundo" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
        }
        for (int i = 0; i < n; i++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_Hundo" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
        }
        
        return events;
    }
    
    public static QuantumEvent[] createSimpleGrovers2Qubit(Qubit[] qubits) {
        if (qubits.length != 2) {
            throw new IllegalArgumentException("This method requires exactly 2 qubits");
        }
        return createGroversCircuit(qubits, 3);
    }
    
    public static QuantumEvent[] createDemoCircuit(Qubit[] qubits) {
        int targetState = (1 << qubits.length) - 1;
        return createGroversCircuit(qubits, targetState);
    }
}

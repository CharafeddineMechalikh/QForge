package example.algorithms;

import com.mechalikh.qforge.state.Qubit;

import example.EventBuilderHelper;

import com.mechalikh.qforge.operations.*;
import com.mechalikh.qforge.events.QuantumEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds feature map circuits and a swap-test kernel circuit for a quantum kernel classifier.
 *
 * <p>Three feature maps are available: {@code ZZFeatureMap} (Hadamards, pairwise controlled phase
 * rotations, then Z rotations), {@code PauliFeatureMap} (two layers of Z and Y rotations with
 * pairwise controlled phase rotations), and a default map of RY rotations. The kernel circuit
 * prepares both registers and applies a swap test with an ancilla at index
 * {@code qubits1.length + qubits2.length}.</p>
 */
public class QuantumSVM {
    
    /**
     * Builds a feature map circuit for one data point.
     *
     * <p>The feature map name selects the construction; an unrecognized name produces the default
     * RY encoding.</p>
     *
     * @param qubits the qubits of the register
     * @param dataPoint the classical data, one value per qubit at most
     * @param featureMap {@code ZZFeatureMap}, {@code PauliFeatureMap}, or any other value for the
     *     default map
     * @return the events of the feature map, in the order they should be scheduled
     */
    public static QuantumEvent[] createQSVMFeatureMap(
        Qubit[] qubits,
        double[] dataPoint,
        String featureMap // "ZZFeatureMap", "PauliFeatureMap"
    ) {
        return createFeatureMap(qubits, dataPoint, featureMap).toArray(new QuantumEvent[0]);
    }
    
    private static List<QuantumEvent> createFeatureMap(
        Qubit[] qubits, double[] dataPoint, String featureMap) {
        switch (featureMap) {
            case "ZZFeatureMap":
                return createZZFeatureMap(qubits, dataPoint);
            case "PauliFeatureMap":
                return createPauliFeatureMap(qubits, dataPoint);
            default:
                return createDefaultFeatureMap(qubits, dataPoint);
        }
    }
    
    private static List<QuantumEvent> createZZFeatureMap(
        Qubit[] qubits, double[] x) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // First layer: Hadamard
        for (int i = 0; i < n; i++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "H" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
        }
        
        // Second layer: entangling Z rotations
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double angle = (x[i] * x[j]) * Math.PI; // Quadratic feature
                events.add(EventBuilderHelper.createCPHASE(
                    "ZZ_" + i + "_" + j, 2 * angle, qubits[i], qubits[j]));
            }
        }
        
        // Third layer: single-qubit Z rotations
        for (int i = 0; i < n; i++) {
            double angle = x[i] * Math.PI; // Linear feature
            events.add(EventBuilderHelper.createSingleQubitGate(
                "RZ" + i, SingleQubitGate.createRZ(2 * angle, qubits[i]), qubits[i]));
        }
        
        return events;
    }
    
    private static List<QuantumEvent> createPauliFeatureMap(
        Qubit[] qubits, double[] x) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // Multi-layer Pauli feature map
        for (int layer = 0; layer < 2; layer++) {
            // Pauli-Z rotations
            for (int i = 0; i < n; i++) {
                double angle = x[i] * Math.PI * (layer + 1);
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Z" + layer + "_" + i, SingleQubitGate.createRZ(angle, qubits[i]), qubits[i]));
            }
            
            // Pauli-Y rotations
            for (int i = 0; i < n; i++) {
                double angle = x[i] * Math.PI * (layer + 1);
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Y" + layer + "_" + i, SingleQubitGate.createRY(angle, qubits[i]), qubits[i]));
            }
            
            // Entangling layer (ZZ)
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double angle = (x[i] * x[j]) * Math.PI;
                    events.add(EventBuilderHelper.createCPHASE(
                        "ZZ_" + layer + "_" + i + "_" + j, angle, qubits[i], qubits[j]));
                }
            }
        }
        
        return events;
    }
    
    private static List<QuantumEvent> createDefaultFeatureMap(
        Qubit[] qubits, double[] x) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // Simple amplitude encoding
        for (int i = 0; i < n; i++) {
            if (i < x.length) {
                double angle = x[i] * Math.PI;
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "RY" + i, SingleQubitGate.createRY(angle, qubits[i]), qubits[i]));
            }
        }
        
        return events;
    }
    
    /**
     * Builds a circuit whose ancilla measurement estimates the kernel between two data points.
     *
     * <p>The kernel type is passed to the feature map builder for both registers, so recognized
     * values are the feature map names. The swap test needs an ancilla qubit whose index is
     * {@code qubits1.length + qubits2.length}; the caller must allocate it in the simulator
     * register.</p>
     *
     * @param qubits1 the first register
     * @param qubits2 the second register, which must have the same length
     * @param dataPoint1 the data encoded on the first register
     * @param dataPoint2 the data encoded on the second register
     * @param kernelType the feature map name used for both registers
     * @return the events of the kernel circuit, in the order they should be scheduled
     * @throws IllegalArgumentException if the two registers have different lengths
     */
    public static QuantumEvent[] createQSVMKernelCircuit(
        Qubit[] qubits1,
        Qubit[] qubits2,
        double[] dataPoint1,
        double[] dataPoint2,
        String kernelType // "ZZ", "Pauli"
    ) {
        // Creates circuit to compute kernel: K(x1, x2) = |<Φ(x1)|Φ(x2)>|^2
        List<QuantumEvent> circuit = new ArrayList<>();
        
        // Prepare |Φ(x1)> on first register
        circuit.addAll(createFeatureMap(qubits1, dataPoint1, kernelType));
        
        // Prepare |Φ(x2)> on second register
        circuit.addAll(createFeatureMap(qubits2, dataPoint2, kernelType));
        
        // SWAP test for overlap estimation
        circuit.addAll(createSwapTest(qubits1, qubits2));
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static List<QuantumEvent> createSwapTest(
        Qubit[] reg1, Qubit[] reg2) {
        List<QuantumEvent> events = new ArrayList<>();
        
        if (reg1.length != reg2.length) {
            throw new IllegalArgumentException("Registers must have same size");
        }
        
        Qubit ancilla = new Qubit(reg1.length + reg2.length, "ancilla");
        
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H_ancilla", SingleQubitGate.createHadamard(ancilla), ancilla));
        
        for (int i = 0; i < reg1.length; i++) {
            QuantumOperation controlledSwap = MultiQubitGate.createControlledSwap(
                ancilla, reg1[i], reg2[i]);
            events.add(EventBuilderHelper.createEvent(
                "CSWAP_" + i, controlledSwap,
                controlledSwap.getTargetQubits(), controlledSwap.getControlQubits()));
        }
        
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H_ancilla_final", SingleQubitGate.createHadamard(ancilla), ancilla));
        
        return events;
    }
}
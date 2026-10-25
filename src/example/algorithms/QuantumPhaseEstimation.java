package example.algorithms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.operations.MultiQubitGate;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.Qubit;

import example.EventBuilderHelper;

/**
 * Builds a quantum phase estimation circuit from a supplied unitary circuit.
 *
 * <p>The estimation register is put into a uniform superposition, controlled powers of each gate
 * in the unitary circuit are applied with repetition counts {@code 2^(t - 1 - j)}, and an inverse
 * Quantum Fourier Transform is applied to the estimation register. Controlled powers are formed
 * explicitly by matrix exponentiation, which the code limits to gates acting on at most 10
 * qubits.</p>
 */
public class QuantumPhaseEstimation {
    
    /**
     * Builds the phase estimation circuit.
     *
     * <p>Every operation in {@code unitaryCircuit} must be a {@link QuantumGate} whose target and
     * control qubits all belong to the eigenstate register. The precision in bits must equal the
     * number of estimation qubits.</p>
     *
     * @param estimationQubits the estimation register, which receives the Hadamards and the
     *     inverse transform
     * @param eigenstateQubits the register the unitary acts on
     * @param unitaryCircuit the gates whose controlled powers are applied
     * @param precisionBits the number of estimation bits, which must equal the register length
     * @return the events of the circuit, in the order they should be scheduled
     * @throws IllegalArgumentException if the estimation register is empty, if the precision does
     *     not match its length, if the unitary circuit is empty, if an operation is not a gate, or
     *     if a gate acts outside the eigenstate register
     * @throws UnsupportedOperationException if a controlled power would require a matrix for more
     *     than 10 qubits
     */
    public static QuantumEvent[] createPhaseEstimationCircuit(
        Qubit[] estimationQubits, 
        Qubit[] eigenstateQubits,
        QuantumEvent[] unitaryCircuit,
        int precisionBits
    ) {
        List<QuantumEvent> circuit = new ArrayList<>();
        int t = estimationQubits.length;
        
        if (t <= 0) {
            throw new IllegalArgumentException("At least one estimation qubit is required");
        }
        if (precisionBits != t) {
            throw new IllegalArgumentException(
                "precisionBits (" + precisionBits + ") must match the number of estimation qubits (" + t + ")");
        }
        if (unitaryCircuit == null || unitaryCircuit.length == 0) {
            throw new IllegalArgumentException("A unitary circuit is required for phase estimation");
        }
        
        Set<Integer> allowed = new HashSet<>();
        for (Qubit qubit : eigenstateQubits) {
            allowed.add(qubit.getIndex());
        }
        for (QuantumEvent event : unitaryCircuit) {
            QuantumOperation operation = event.getOperation();
            if (!(operation instanceof QuantumGate)) {
                throw new IllegalArgumentException(
                    "Phase estimation requires gate operations, got: " + operation.getClass().getName());
            }
            QuantumGate gate = (QuantumGate) operation;
            for (Qubit qubit : gate.getTargetQubits()) {
                if (!allowed.contains(qubit.getIndex())) {
                    throw new IllegalArgumentException(
                        "Unitary targets qubit " + qubit.getIndex() + " outside the eigenstate register");
                }
            }
            for (Qubit qubit : gate.getControlQubits()) {
                if (!allowed.contains(qubit.getIndex())) {
                    throw new IllegalArgumentException(
                        "Unitary controls qubit " + qubit.getIndex() + " outside the eigenstate register");
                }
            }
        }
        
        for (int i = 0; i < t; i++) {
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "H" + i, SingleQubitGate.createHadamard(estimationQubits[i]), estimationQubits[i]));
        }
        
        for (int j = 0; j < t; j++) {
            int repetitions = 1 << (t - 1 - j);
            for (int e = 0; e < unitaryCircuit.length; e++) {
                QuantumGate base = (QuantumGate) unitaryCircuit[e].getOperation();
                QuantumGate controlled = createControlledPower(
                    base, estimationQubits[j], repetitions, "CU" + j + "_" + e);
                circuit.add(EventBuilderHelper.createEvent(
                    controlled.getName(), controlled,
                    controlled.getTargetQubits(), controlled.getControlQubits()));
            }
        }
        
        circuit.addAll(createInverseQFT(estimationQubits, "IQFT"));
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static QuantumGate createControlledPower(
        QuantumGate base, Qubit control, int repetitions, String name) {
        if (base.getMatrixQubitIndices().length > 10) {
            throw new UnsupportedOperationException(
                "Controlled powers above 10 qubits require an impractically large matrix");
        }
        
        ComplexMatrix powered = matrixPower(base.getMatrix(), repetitions);
        int baseDim = powered.rows();
        ComplexMatrix controlled = new ComplexMatrix(2 * baseDim, 2 * baseDim);
        
        for (int i = 0; i < baseDim; i++) {
            controlled.set(i, i, Complex.ONE);
        }
        for (int row = 0; row < baseDim; row++) {
            for (int col = 0; col < baseDim; col++) {
                controlled.set(baseDim + row, baseDim + col, powered.get(row, col));
            }
        }
        
        Qubit[] baseControls = base.getControlQubits();
        Qubit[] controls = new Qubit[baseControls.length + 1];
        controls[0] = control;
        System.arraycopy(baseControls, 0, controls, 1, baseControls.length);
        
        return new MultiQubitGate(
            name + "_" + base.getName(), controlled, base.getTargetQubits(), controls, base.getDuration());
    }
    
    private static ComplexMatrix matrixPower(ComplexMatrix base, int exponent) {
        if (exponent < 1) {
            throw new IllegalArgumentException("Exponent must be at least 1");
        }
        ComplexMatrix result = ComplexMatrix.identity(base.rows());
        ComplexMatrix factor = base;
        int remaining = exponent;
        
        while (remaining > 0) {
            if ((remaining & 1) == 1) {
                result = result.multiply(factor);
            }
            remaining >>= 1;
            if (remaining > 0) {
                factor = factor.multiply(factor);
            }
        }
        
        return result;
    }
    
    /**
     * Builds an inverse Quantum Fourier Transform circuit.
     *
     * <p>The circuit first swaps the outer qubits to reverse the register order, then applies
     * controlled phase gates with angle {@code -PI / 2^(k - j)} followed by a Hadamard, walking
     * from the last qubit down to the first.</p>
     *
     * @param qubits the register to transform
     * @param name the prefix used for the event names
     * @return the events of the circuit, in the order they should be scheduled
     */
    public static List<QuantumEvent> createInverseQFT(Qubit[] qubits, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        for (int i = 0; i < n / 2; i++) {
            events.add(EventBuilderHelper.createSWAP(
                name + "_SWAP_" + i + "_" + (n - 1 - i),
                qubits[i], qubits[n - 1 - i]));
        }
        
        for (int j = n - 1; j >= 0; j--) {
            for (int k = n - 1; k > j; k--) {
                double angle = -Math.PI / Math.pow(2, k - j);
                events.add(EventBuilderHelper.createCPHASE(
                    name + "_CP_" + k + "_" + j, angle, qubits[k], qubits[j]));
            }
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_H" + j, SingleQubitGate.createHadamard(qubits[j]), qubits[j]));
        }
        
        return events;
    }
}

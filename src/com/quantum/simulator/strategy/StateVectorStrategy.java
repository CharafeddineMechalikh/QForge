package com.quantum.simulator.strategy;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.noise.NoiseModel;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.measurement.Observable;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.Arrays;
/**
 * Simulation strategy that represents a pure state as a {@link StateVector}.
 *
 * <p>Gates are applied by multiplying the amplitude vector with the gate matrix. Noise is
 * applied by sampling one Kraus operator branch in proportion to its squared norm and then
 * renormalizing. Measurement samples the Pauli Z observable on the target qubits.
 */
public class StateVectorStrategy implements SimulationStrategy {
    private static final double EPSILON = 1e-12;
    
    /**
     * Creates the all-zero state {@code |0...0>} as a state vector.
     *
     * @param numQubits number of qubits in the state
     * @return a state with amplitude one in the zero basis state
     * @throws IllegalArgumentException if the state constructor rejects the qubit count
     */
    @Override
    public QuantumState createInitialState(int numQubits) {
        return StateVector.createZeroState(numQubits);
    }
    

    /**
     * Applies a noise channel to a state vector.
     *
     * <p>The qubit indices are read from {@code targetQubits}, and the Kraus operators are
     * requested for the number of target qubits. The state implementation chooses one Kraus
     * branch with probability proportional to its squared norm and renormalizes the result.
     *
     * @param state state to transform
     * @param noise noise model to apply
     * @param targetQubits qubits affected by the noise
     * @return a new state with the selected Kraus branch applied
     * @throws IllegalArgumentException if {@code state} is not a {@link StateVector}
     */
    @Override
    public QuantumState applyNoise(QuantumState state, NoiseModel noise, Qubit[] targetQubits) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        int[] targetIndices = Arrays.stream(targetQubits).mapToInt(Qubit::getIndex).toArray();
        return state.applyKrausOperators(noise.getKrausOperators(targetQubits.length), targetIndices);
    }
    
    /**
     * Measures the Pauli Z observable on the target qubits.
     *
     * <p>The observable is built as the tensor product of single-qubit Pauli Z operators in
     * the order of {@code targetQubits}, and {@code shots} samples are drawn from the outcome
     * probabilities. The state is not collapsed.
     *
     * @param state state to measure
     * @param targetQubits qubits to measure
     * @param shots number of samples
     * @return the measurement result
     * @throws IllegalArgumentException if {@code state} is not a {@link StateVector}
     */
    @Override
    public MeasurementResult measure(QuantumState state, Qubit[] targetQubits, int shots) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        return Observable.pauliZ(targetQubits).measureMultiple(state, shots);
    }
    
    /**
     * Creates a deep copy of a state vector.
     *
     * <p>Each amplitude is copied into a new array, and a new {@link StateVector} is created
     * with the same number of qubits.
     *
     * @param state state to copy
     * @return an independent copy
     * @throws IllegalArgumentException if {@code state} is not a {@link StateVector}
     */
    @Override
    public QuantumState cloneState(QuantumState state) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        
        StateVector stateVector = (StateVector) state;
        Complex[] amplitudes = stateVector.getAmplitudes();
        Complex[] clonedAmplitudes = new Complex[amplitudes.length];
        
        for (int i = 0; i < amplitudes.length; i++) {
            clonedAmplitudes[i] = new Complex(amplitudes[i]);
        }
        
        return new StateVector(clonedAmplitudes, stateVector.getNumQubits());
    }
    
    /**
     * Computes the squared overlap between two state vectors.
     *
     * <p>The inner product is {@code sum(conj(a[i]) * b[i])}, the fidelity is its squared
     * magnitude, and the result is clamped to {@code [0, 1]}.
     *
     * @param state1 first state
     * @param state2 second state
     * @return the fidelity
     * @throws IllegalArgumentException if either state is not a {@link StateVector} or the
     *         two states have different qubit counts
     */
    @Override
    public double calculateFidelity(QuantumState state1, QuantumState state2) {
        if (!(state1 instanceof StateVector) || !(state2 instanceof StateVector)) {
            throw new IllegalArgumentException("Both states must be StateVector instances");
        }
        
        StateVector sv1 = (StateVector) state1;
        StateVector sv2 = (StateVector) state2;
        
        if (sv1.getNumQubits() != sv2.getNumQubits()) {
            throw new IllegalArgumentException("States must have same number of qubits");
        }
        
        Complex innerProduct = Complex.ZERO;
        Complex[] amps1 = sv1.getAmplitudes();
        Complex[] amps2 = sv2.getAmplitudes();
        
        for (int i = 0; i < amps1.length; i++) {
            innerProduct = innerProduct.add(amps1[i].conjugate().multiply(amps2[i]));
        }
        
        double fidelity = innerProduct.absSquare();
        return Math.min(1.0, Math.max(0.0, fidelity)); // Clamp to [0, 1]
    }
    
    /**
     * Computes the Shannon entropy of the computational-basis probabilities, in bits.
     *
     * <p>Each term is {@code -p * log2(p)} and probabilities at or below {@code 1e-12} are
     * skipped.
     *
     * @param state state to inspect
     * @return the basis entropy in bits, zero for a computational basis state
     * @throws IllegalArgumentException if {@code state} is not a {@link StateVector}
     */
    @Override
    public double calculateEntropy(QuantumState state) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        
        StateVector stateVector = (StateVector) state;
        double entropy = 0.0;
        
        for (Complex amplitude : stateVector.getAmplitudes()) {
            double probability = amplitude.absSquare();
            if (probability > EPSILON) {
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }
        
        return entropy;
    }
    
    /**
     * Reports that this strategy supports noise.
     *
     * @return always {@code true}
     */
    @Override
    public boolean canSimulateNoise() {
        return true;
    }
    
    /**
     * Reports a practical qubit ceiling of 30.
     *
     * <p>The value reflects one complex amplitude per basis state, approximately 16 GiB at 30
     * qubits. It is informational and is not enforced by this class.
     *
     * @return the reported limit, {@code 30}
     */
    @Override
    public int getMaxSupportedQubits() {
        // One complex amplitude per basis state (16 bytes); 2^30 is ~16 GiB
        return 30;
    }
    
    /**
     * Returns the strategy name.
     *
     * @return {@code "StateVectorSimulator"}
     */
    @Override
    public String getStrategyName() {
        return "StateVectorSimulator";
    }
    
    /**
     * Performs no initialization; this strategy has no internal state.
     */
    @Override
    public void initialize() {
        // Nothing to initialize
    }
    
    /**
     * Performs no cleanup; this strategy has no internal state.
     */
    @Override
    public void cleanup() {
        // Nothing to clean up
    }
    
    /**
     * Applies a gate matrix to a state vector.
     *
     * <p>The qubit positions are taken from {@link QuantumGate#getMatrixQubitIndices()}, which
     * lists control qubits first and target qubits after them; the {@code targetQubits}
     * argument is not consulted. Basis states are indexed with qubit {@code 0} as the most
     * significant bit. For each basis state, the amplitude is distributed over the output
     * states according to the gate matrix entries.
     *
     * @param state state to transform
     * @param gate gate whose matrix and qubit indices are used
     * @param targetQubits target qubits reported by the caller, not used
     * @return a new state with the transformed amplitudes
     * @throws IllegalArgumentException if {@code state} is not a {@link StateVector}
     */
    @Override
    public QuantumState applyGate(QuantumState state, QuantumGate gate, Qubit[] targetQubits) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        
        StateVector stateVector = (StateVector) state;
        int[] qubitIndices = gate.getMatrixQubitIndices();
        Complex[] newAmplitudes = applyGateToStateVector(
            stateVector.getAmplitudes(),
            gate.getMatrix(),
            qubitIndices,
            stateVector.getNumQubits()
        );
        
        return new StateVector(newAmplitudes, stateVector.getNumQubits());
    }
    
    private Complex[] applyGateToStateVector(Complex[] amplitudes, ComplexMatrix gateMatrix, 
                                           int[] qubitIndices, int numQubits) {
        int targetSize = qubitIndices.length;
        int stateSize = amplitudes.length;
        
        Complex[] newAmplitudes = new Complex[stateSize];
        for (int i = 0; i < stateSize; i++) {
            newAmplitudes[i] = new Complex(0, 0);
        }
        
        for (int idx = 0; idx < stateSize; idx++) {
            int gateIn = 0;
            for (int t = 0; t < targetSize; t++) {
                int bit = (idx >> (numQubits - 1 - qubitIndices[t])) & 1;
                gateIn |= (bit << (targetSize - 1 - t));
            }
            
            for (int gateOut = 0; gateOut < (1 << targetSize); gateOut++) {
                int outIdx = idx;
                for (int t = 0; t < targetSize; t++) {
                    int oldBit = (gateIn >> (targetSize - 1 - t)) & 1;
                    int newBit = (gateOut >> (targetSize - 1 - t)) & 1;
                    if (oldBit != newBit) {
                        outIdx ^= (1 << (numQubits - 1 - qubitIndices[t]));
                    }
                }
                
                Complex matrixElement = gateMatrix.get(gateOut, gateIn);
                if (matrixElement.absSquare() > 0) {
                    newAmplitudes[outIdx] = newAmplitudes[outIdx]
                        .add(amplitudes[idx].multiply(matrixElement));
                }
            }
        }
        
        return newAmplitudes;
    }
    
}
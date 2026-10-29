package com.mechalikh.qforge.strategy;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.noise.NoiseModel;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.state.MPSState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.QuantumState;

/**
 * Simulation strategy that represents a pure state as an {@link MPSState}.
 *
 * <p>Gate application is delegated to the matrix product state, which contracts the gate
 * into the affected tensors, truncates the bond dimensions to the configured limit, and
 * restores canonical form. Measurement uses the Pauli Z observable, which reads the
 * computational basis probabilities of small states. Noise is not implemented for this
 * backend.</p>
 */
public class TensorNetworkStrategy implements SimulationStrategy {
    private static final double EPSILON = 1e-12;

    private final int maxBondDimension;

    /**
     * Creates a strategy whose states keep at most the given number of Schmidt coefficients
     * per bond.
     *
     * @param maxBondDimension maximum bond dimension, at least one
     * @throws IllegalArgumentException if {@code maxBondDimension} is less than one
     */
    public TensorNetworkStrategy(int maxBondDimension) {
        if (maxBondDimension < 1) {
            throw new IllegalArgumentException("Maximum bond dimension must be at least one");
        }
        this.maxBondDimension = maxBondDimension;
    }

    /**
     * Creates the all-zero state {@code |0...0>} as a matrix product state.
     *
     * @param numQubits number of qubits in the state
     * @return a matrix product state with amplitude one in the zero basis state
     * @throws IllegalArgumentException if the qubit count is not positive
     */
    @Override
    public QuantumState createInitialState(int numQubits) {
        return new MPSState(numQubits, maxBondDimension);
    }

    /**
     * Applies a gate to a matrix product state.
     *
     * <p>The qubit positions are read from {@link QuantumGate#getMatrixQubitIndices()} and
     * the {@code targetQubits} argument is not consulted.</p>
     *
     * @param state state to transform
     * @param gate gate to apply
     * @param targetQubits target qubits reported by the caller, not used
     * @return the resulting state
     * @throws IllegalArgumentException if {@code state} is not an {@link MPSState}
     * @throws UnsupportedOperationException if the gate acts on more than two qubits
     */
    @Override
    public QuantumState applyGate(QuantumState state, QuantumGate gate, Qubit[] targetQubits) {
        if (!(state instanceof MPSState)) {
            throw new IllegalArgumentException(
                "TensorNetworkStrategy requires an MPSState quantum state");
        }
        return ((MPSState) state).applyGate(gate);
    }

    /**
     * Always throws, because noise channels are not implemented for this backend.
     *
     * @param state state to transform
     * @param noise noise model to apply
     * @param targetQubits qubits affected by the noise
     * @return never returns normally
     * @throws UnsupportedOperationException always, with a message pointing to the state
     *         vector backend
     */
    @Override
    public QuantumState applyNoise(QuantumState state, NoiseModel noise, Qubit[] targetQubits) {
        throw new UnsupportedOperationException(
            "Noise is not implemented for the matrix product state backend; "
                + "use the state vector backend instead");
    }

    /**
     * Measures the Pauli Z observable on the target qubits.
     *
     * <p>The observable reads the computational basis probabilities of the state through
     * {@link MPSState#getProbabilities()}, so the state must not exceed twenty four
     * qubits. The state is not collapsed.</p>
     *
     * @param state state to measure
     * @param targetQubits qubits to measure
     * @param shots number of samples
     * @return the measurement result
     * @throws IllegalArgumentException if {@code state} is not an {@link MPSState}
     */
    @Override
    public MeasurementResult measure(QuantumState state, Qubit[] targetQubits, int shots) {
        if (!(state instanceof MPSState)) {
            throw new IllegalArgumentException(
                "TensorNetworkStrategy requires an MPSState quantum state");
        }
        return Observable.pauliZ(targetQubits).measureMultiple(state, shots);
    }

    /**
     * Creates a deep copy of a matrix product state.
     *
     * @param state state to copy
     * @return an independent copy
     * @throws IllegalArgumentException if {@code state} is not an {@link MPSState}
     */
    @Override
    public QuantumState cloneState(QuantumState state) {
        if (!(state instanceof MPSState)) {
            throw new IllegalArgumentException(
                "TensorNetworkStrategy requires an MPSState quantum state");
        }
        return ((MPSState) state).copy();
    }

    /**
     * Computes the squared overlap between two matrix product states.
     *
     * <p>The dense amplitudes are formed for both states, the inner product
     * {@code sum(conj(a[i]) * b[i])} is evaluated, and its squared magnitude is clamped to
     * the range {@code [0, 1]}.</p>
     *
     * @param state1 first state
     * @param state2 second state
     * @return the fidelity
     * @throws IllegalArgumentException if either state is not an {@link MPSState} or the
     *         two states have different qubit counts
     * @throws UnsupportedOperationException if a state has more than twenty four qubits
     */
    @Override
    public double calculateFidelity(QuantumState state1, QuantumState state2) {
        if (!(state1 instanceof MPSState) || !(state2 instanceof MPSState)) {
            throw new IllegalArgumentException("Both states must be MPSState instances");
        }
        MPSState first = (MPSState) state1;
        MPSState second = (MPSState) state2;
        if (first.getNumQubits() != second.getNumQubits()) {
            throw new IllegalArgumentException("States must have same number of qubits");
        }

        Complex[] amplitudes1 = first.getAmplitudes();
        Complex[] amplitudes2 = second.getAmplitudes();
        Complex innerProduct = Complex.ZERO;
        for (int index = 0; index < amplitudes1.length; index++) {
            innerProduct = innerProduct.add(
                amplitudes1[index].conjugate().multiply(amplitudes2[index]));
        }
        double fidelity = innerProduct.absSquare();
        return Math.min(1.0, Math.max(0.0, fidelity));
    }

    /**
     * Computes the Shannon entropy of the computational basis probabilities, in bits.
     *
     * <p>Each term is {@code -p * log2(p)} and probabilities at or below {@code 1e-12}
     * are skipped.</p>
     *
     * @param state state to inspect
     * @return the basis entropy in bits
     * @throws IllegalArgumentException if {@code state} is not an {@link MPSState}
     * @throws UnsupportedOperationException if the state has more than twenty four qubits
     */
    @Override
    public double calculateEntropy(QuantumState state) {
        if (!(state instanceof MPSState)) {
            throw new IllegalArgumentException(
                "TensorNetworkStrategy requires an MPSState quantum state");
        }
        double entropy = 0.0;
        for (double probability : ((MPSState) state).getProbabilities()) {
            if (probability > EPSILON) {
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }
        return entropy;
    }

    /**
     * Reports that this strategy does not support noise.
     *
     * @return always {@code false}
     */
    @Override
    public boolean canSimulateNoise() {
        return false;
    }

    /**
     * Reports the qubit ceiling advertised by this strategy.
     *
     * <p>The value describes the reach of a bond dimension limited representation and is not
     * enforced by this class. Dense accessors and measurement remain limited to twenty four
     * qubits.</p>
     *
     * @return the reported limit, {@code 100}
     */
    @Override
    public int getMaxSupportedQubits() {
        return 100;
    }

    /**
     * Returns the strategy name.
     *
     * @return {@code "TensorNetworkSimulator"}
     */
    @Override
    public String getStrategyName() {
        return "TensorNetworkSimulator";
    }

    /**
     * Performs no initialization; this strategy has no mutable internal state.
     */
    @Override
    public void initialize() {
    }

    /**
     * Performs no cleanup; this strategy has no mutable internal state.
     */
    @Override
    public void cleanup() {
    }
}

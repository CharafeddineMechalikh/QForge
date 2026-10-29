package com.mechalikh.qforge.strategy;

import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.noise.NoiseModel;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StabilizerState;

/**
 * Simulation strategy that represents a pure state as a {@link StabilizerState} tableau.
 *
 * <p>Gates are applied by updating the Clifford tableau, measurement samples the Pauli
 * {@code Z} observable on the target qubits, and cloning copies the tableau. The strategy
 * supports only Clifford gates and Pauli {@code Z} measurements. General Kraus noise
 * channels, fidelity from amplitudes and entropy from amplitudes are rejected because a
 * stabilizer tableau does not store an amplitude vector or a density matrix.</p>
 */
public class StabilizerStrategy implements SimulationStrategy {
    private static final int MAX_QUBITS = 2000;
    private static final int MAX_EXACT_TARGETS = 16;

    /**
     * Creates the all-zero state {@code |0...0>} as a stabilizer tableau.
     *
     * @param numQubits number of qubits in the state
     * @return a stabilizer state with the stabilizers {@code Z_0} to {@code Z_(n-1)}
     * @throws IllegalArgumentException if the state constructor rejects the qubit count
     */
    @Override
    public QuantumState createInitialState(int numQubits) {
        return new StabilizerState(numQubits);
    }

    /**
     * Applies a Clifford gate to a stabilizer state.
     *
     * <p>The qubit positions are taken from the gate control and target arrays inside
     * {@link StabilizerState#applyGate(QuantumGate)}; the {@code targetQubits} argument is not
     * consulted.</p>
     *
     * @param state state to transform
     * @param gate gate to apply
     * @param targetQubits target qubits reported by the caller, not used
     * @return the transformed state, which is the supplied instance
     * @throws IllegalArgumentException if {@code state} is not a {@link StabilizerState}
     * @throws UnsupportedOperationException if the gate is not a Clifford gate
     */
    @Override
    public QuantumState applyGate(QuantumState state, QuantumGate gate, Qubit[] targetQubits) {
        if (!(state instanceof StabilizerState)) {
            throw new IllegalArgumentException("StabilizerStrategy requires StabilizerState quantum state");
        }
        return ((StabilizerState) state).applyGate(gate);
    }

    /**
     * Always throws, because a Clifford tableau cannot represent a general Kraus channel.
     *
     * <p>Stochastic Pauli errors can still be simulated by applying the corresponding
     * Clifford gates, but a channel given as Kraus operators requires an amplitude vector, a
     * density matrix or a tensor network state.</p>
     *
     * @param state state to transform
     * @param noise noise model to apply
     * @param targetQubits qubits affected by the noise
     * @return never returns normally
     * @throws IllegalArgumentException if {@code state} is not a {@link StabilizerState}
     * @throws UnsupportedOperationException always, because Kraus channels require the state
     *         vector, density matrix or tensor network backend
     */
    @Override
    public QuantumState applyNoise(QuantumState state, NoiseModel noise, Qubit[] targetQubits) {
        if (!(state instanceof StabilizerState)) {
            throw new IllegalArgumentException("StabilizerStrategy requires StabilizerState quantum state");
        }
        throw new UnsupportedOperationException(
            "Kraus noise channels require the state vector, density matrix or tensor network "
                + "backend; the stabilizer backend supports only Clifford gates");
    }

    /**
     * Measures the Pauli {@code Z} observable on the target qubits.
     *
     * <p>The outcomes are drawn with {@link StabilizerState#sampleZ(Qubit[], int)}, and the
     * result carries the exact marginal probabilities together with the Pauli eigenvalue of
     * every outcome. The state is not collapsed.</p>
     *
     * @param state state to measure
     * @param targetQubits qubits to measure, at least one and at most sixteen
     * @param shots number of samples
     * @return the sampled measurement statistics
     * @throws IllegalArgumentException if {@code state} is not a {@link StabilizerState}, the
     *         target array is invalid, the target count exceeds sixteen, or {@code shots} is
     *         negative
     */
    @Override
    public MeasurementResult measure(QuantumState state, Qubit[] targetQubits, int shots) {
        if (!(state instanceof StabilizerState)) {
            throw new IllegalArgumentException("StabilizerStrategy requires StabilizerState quantum state");
        }
        if (targetQubits == null || targetQubits.length == 0) {
            throw new IllegalArgumentException("At least one target qubit is required");
        }
        if (targetQubits.length > MAX_EXACT_TARGETS) {
            throw new IllegalArgumentException(
                "MeasurementResult is limited to " + MAX_EXACT_TARGETS
                    + " target qubits, got " + targetQubits.length);
        }
        StabilizerState stabilizer = (StabilizerState) state;
        int[] outcomes = stabilizer.sampleZ(targetQubits, shots);
        double[] probabilities = stabilizer.outcomeProbabilitiesZ(targetQubits);
        int outcomeCount = 1 << targetQubits.length;
        double[] eigenvalues = new double[outcomeCount];
        for (int outcome = 0; outcome < outcomeCount; outcome++) {
            eigenvalues[outcome] = (Integer.bitCount(outcome) & 1) == 0 ? 1.0 : -1.0;
        }
        return new MeasurementResult(observableName(targetQubits), targetQubits, outcomes,
            probabilities, eigenvalues);
    }

    /**
     * Creates an independent copy of a stabilizer state.
     *
     * @param state state to copy
     * @return a copy with an independent tableau
     * @throws IllegalArgumentException if {@code state} is not a {@link StabilizerState}
     */
    @Override
    public QuantumState cloneState(QuantumState state) {
        if (!(state instanceof StabilizerState)) {
            throw new IllegalArgumentException("StabilizerStrategy requires StabilizerState quantum state");
        }
        return ((StabilizerState) state).copy();
    }

    /**
     * Always throws, because a stabilizer tableau does not store amplitudes.
     *
     * @param state1 first state
     * @param state2 second state
     * @return never returns normally
     * @throws UnsupportedOperationException always, because fidelity requires amplitudes or a
     *         density matrix
     */
    @Override
    public double calculateFidelity(QuantumState state1, QuantumState state2) {
        throw new UnsupportedOperationException(
            "Fidelity is not available for StabilizerState; use the state vector or density "
                + "matrix backend");
    }

    /**
     * Always throws, because a stabilizer tableau does not store basis probabilities.
     *
     * @param state state to inspect
     * @return never returns normally
     * @throws UnsupportedOperationException always, because entropy requires basis
     *         probabilities
     */
    @Override
    public double calculateEntropy(QuantumState state) {
        throw new UnsupportedOperationException(
            "Entropy is not available for StabilizerState; sample the state or use the state "
                + "vector or density matrix backend");
    }

    /**
     * Reports that this strategy does not support Kraus noise channels.
     *
     * @return {@code false}
     */
    @Override
    public boolean canSimulateNoise() {
        return false;
    }

    /**
     * Returns the maximum number of qubits this strategy reports it supports.
     *
     * <p>The tableau uses two rows of two thousand bits per qubit, about two mebibytes in
     * total, and the value is informational.</p>
     *
     * @return the reported qubit limit, {@code 2000}
     */
    @Override
    public int getMaxSupportedQubits() {
        return MAX_QUBITS;
    }

    /**
     * Returns the strategy name.
     *
     * @return {@code "StabilizerSimulator"}
     */
    @Override
    public String getStrategyName() {
        return "StabilizerSimulator";
    }

    /**
     * Performs no initialization; this strategy has no internal state.
     */
    @Override
    public void initialize() {
    }

    /**
     * Performs no cleanup; this strategy has no internal state.
     */
    @Override
    public void cleanup() {
    }

    private static String observableName(Qubit[] targetQubits) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < targetQubits.length; i++) {
            if (i > 0) {
                name.append('x');
            }
            name.append("Z_").append(targetQubits[i].getLabel());
        }
        return name.toString();
    }
}

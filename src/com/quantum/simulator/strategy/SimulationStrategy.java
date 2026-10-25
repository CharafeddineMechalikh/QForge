package com.quantum.simulator.strategy;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.noise.NoiseModel;
import com.quantum.simulator.measurement.MeasurementResult;

/**
 * Strategy interface for operating on a quantum state representation.
 *
 * <p>Implementations bind the simulator to a concrete state type. {@link StateVectorStrategy}
 * uses pure states and {@link DensityMatrixStrategy} uses mixed states. Methods that accept a
 * {@link QuantumState} expect the representation supported by the implementation, and the
 * implementations in this package throw {@link IllegalArgumentException} for a mismatch.
 */
public interface SimulationStrategy {
    /**
     * Creates the initial state for the given number of qubits.
     *
     * <p>The implementations in this package return the all-zero computational basis state.
     *
     * @param numQubits number of qubits in the state
     * @return the initial state
     * @throws IllegalArgumentException if the state representation rejects the qubit count
     */
    QuantumState createInitialState(int numQubits);
    
    /**
     * Applies a gate and returns the resulting state.
     *
     * <p>The implementations in this package take the affected qubit positions from
     * {@link QuantumGate#getMatrixQubitIndices()} and do not read the {@code targetQubits}
     * argument.
     *
     * @param state state to transform
     * @param gate gate to apply
     * @param targetQubits target qubits reported by the caller
     * @return the resulting state
     * @throws IllegalArgumentException if {@code state} has the wrong representation type
     */
    QuantumState applyGate(QuantumState state, QuantumGate gate, Qubit[] targetQubits);
    
    /**
     * Applies a noise channel and returns the resulting state.
     *
     * <p>The implementations in this package select the Kraus operators for the number of
     * supplied target qubits and apply them at those qubits.
     *
     * @param state state to transform
     * @param noise noise model to apply
     * @param targetQubits qubits affected by the noise
     * @return the resulting state
     * @throws IllegalArgumentException if {@code state} has the wrong representation type
     */
    QuantumState applyNoise(QuantumState state, NoiseModel noise, Qubit[] targetQubits);
    
    /**
     * Measures the given qubits repeatedly and returns the statistics.
     *
     * <p>The implementations in this package measure the Pauli Z observable on the target
     * qubits for the requested number of shots and do not collapse the state.
     *
     * @param state state to measure
     * @param targetQubits qubits to measure
     * @param shots number of samples
     * @return the measurement result
     * @throws IllegalArgumentException if {@code state} has the wrong representation type
     */
    MeasurementResult measure(QuantumState state, Qubit[] targetQubits, int shots);
    
    /**
     * Returns an independent copy of the given state.
     *
     * @param state state to copy
     * @return a copy that does not share mutable state with the input
     * @throws IllegalArgumentException if {@code state} has the wrong representation type
     */
    QuantumState cloneState(QuantumState state);
    
    /**
     * Computes the fidelity between two states.
     *
     * <p>The implementations in this package require both states to use the same
     * representation and to have the same number of qubits, and they clamp the result to the
     * range {@code [0, 1]}.
     *
     * @param state1 first state
     * @param state2 second state
     * @return the fidelity
     * @throws IllegalArgumentException if the state types or qubit counts differ
     */
    double calculateFidelity(QuantumState state1, QuantumState state2);
    
    /**
     * Computes an entropy for the given state, in bits.
     *
     * @param state state to inspect
     * @return the entropy in bits
     * @throws IllegalArgumentException if {@code state} has the wrong representation type
     */
    double calculateEntropy(QuantumState state);
    
    /**
     * Tests whether this strategy supports noise channels.
     *
     * @return {@code true} if noise can be applied
     */
    boolean canSimulateNoise();
    
    /**
     * Returns the maximum number of qubits this strategy reports it supports.
     *
     * <p>The value is informational; the strategies in this package do not enforce it.
     *
     * @return the reported qubit limit
     */
    int getMaxSupportedQubits();
    
    /**
     * Returns the name of this strategy.
     *
     * @return a display name for the strategy
     */
    String getStrategyName();
    
    /**
     * Prepares the strategy for use.
     */
    void initialize();
    
    /**
     * Releases resources held by the strategy.
     */
    void cleanup();
}
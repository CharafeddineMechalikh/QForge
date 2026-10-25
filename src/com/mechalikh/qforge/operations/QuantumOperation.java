package com.mechalikh.qforge.operations;

import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;

/**
 * A single operation that can be applied during a simulation.
 *
 * <p>Implementations describe gates, noise processes, and measurements. The simulation context
 * dispatches operations by concrete type: gates are applied through the simulation strategy, and
 * other implementations must match the types handled there.</p>
 */
public interface QuantumOperation {
    /**
     * Applies this operation to a state.
     *
     * @param state the state to operate on
     * @return the resulting state
     */
    QuantumState apply(QuantumState state);
    
    /**
     * Returns the operation name.
     *
     * @return the operation name
     */
    String getName();
    
    /**
     * Returns the duration associated with the operation.
     *
     * @return the operation duration
     */
    double getDuration();
    
    /**
     * Returns the target qubits of the operation.
     *
     * <p>Implementations may return a defensive copy.</p>
     *
     * @return the target qubits
     */
    Qubit[] getTargetQubits();
    
    /**
     * Returns the control qubits of the operation.
     *
     * <p>Implementations that have no control qubits may return an empty array.</p>
     *
     * @return the control qubits
     */
    Qubit[] getControlQubits();
    
    /**
     * Returns whether the operation is unitary.
     *
     * @return {@code true} if the operation is unitary
     */
    boolean isUnitary();
    
    /**
     * Returns whether the operation models noise.
     *
     * @return {@code true} if the operation is a noise process
     */
    boolean isNoise();
    
    /**
     * Returns whether the operation performs a measurement.
     *
     * @return {@code true} if the operation is a measurement
     */
    boolean isMeasurement();
    
    /**
     * Returns an operation that reverses the effect of this operation.
     *
     * @return the inverse operation
     */
    QuantumOperation inverse();
}
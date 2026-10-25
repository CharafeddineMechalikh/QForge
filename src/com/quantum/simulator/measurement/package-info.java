/**
 * Measurement of quantum states, both in the computational basis and along the eigenbasis of a
 * Hermitian observable.
 *
 * <p>The main types are:</p>
 * <ul>
 *   <li>{@link com.quantum.simulator.measurement.Observable}, a Hermitian operator over a fixed
 *       set of target qubits that supplies outcome probabilities, outcome eigenvalues, sampling
 *       and collapse</li>
 *   <li>{@link com.quantum.simulator.measurement.MeasurementOperator}, a quantum operation that
 *       wraps an observable with a shot count and a destructive or nondestructive policy</li>
 *   <li>{@link com.quantum.simulator.measurement.MeasurementResult}, the sampled counts, exact
 *       probabilities, outcome eigenvalues, expectation value and variance of a measurement</li>
 *   <li>{@link com.quantum.simulator.measurement.MeasurementHelper}, static shortcuts for
 *       sampling computational basis marginals of a pure state without collapsing it</li>
 * </ul>
 *
 * <p>Computational basis outcomes are indexed by the bit pattern of the target qubits, with the
 * first target as the most significant bit. For a general observable the outcomes are the
 * eigenvalues in ascending order, as produced by the operator's eigen decomposition, and a
 * destructive measurement projects the state onto the matching eigenvector. Nondestructive
 * measurement only samples and leaves the state untouched.</p>
 */
package com.quantum.simulator.measurement;

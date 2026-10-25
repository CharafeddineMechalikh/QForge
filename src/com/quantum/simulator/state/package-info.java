/**
 * State representations and shared state operations for the simulator.
 *
 * <p>The package provides two backends. {@link StateVector} stores a pure state as an
 * array of complex amplitudes. {@link DensityMatrix} stores a mixed state as a density
 * matrix. Both extend {@link QuantumState}, which implements the operations they share:
 * Kraus operator application, the partial trace, measurement collapse, expectation
 * values, and variances.</p>
 *
 * <p>Basis states are integers in {@code [0, 2^numQubits)}. The bit of qubit {@code q}
 * is bit {@code numQubits - 1 - q} of that integer, so qubit {@code 0} is the most
 * significant bit and the last qubit is the least significant bit. A state vector stores
 * amplitudes in this index order, and a density matrix uses the same order for rows and
 * columns.</p>
 *
 * <p>{@link Qubit} holds the index, label, and last measurement of one qubit.
 * {@link ComplexVector} is a general purpose dense complex vector.</p>
 */
package com.quantum.simulator.state;

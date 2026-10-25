/**
 * Gate and operation types used by the simulator.
 *
 * <p>The package defines the {@link QuantumOperation} contract, the matrix-backed
 * {@link QuantumGate} base class with its {@link SingleQubitGate} and {@link MultiQubitGate}
 * subclasses, and the {@link GateLibrary} registry. Gate matrices are ordered with control qubits
 * first, then target qubits, as reported by {@link QuantumGate#getMatrixQubitIndices()}.</p>
 */
package com.quantum.simulator.operations;

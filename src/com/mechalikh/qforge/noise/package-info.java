/**
 * Noise channels expressed as Kraus operator decompositions, plus profiles and error accounting.
 *
 * <p>All channels extend {@link com.mechalikh.qforge.noise.NoiseModel}, which turns a set of
 * Kraus operators into a quantum operation on the model's target qubits. The concrete channels
 * are:</p>
 * <ul>
 *   <li>{@link com.mechalikh.qforge.noise.DepolarizingNoise}, which mixes the identity with every
 *       non identity Pauli string and supports up to four target qubits</li>
 *   <li>{@link com.mechalikh.qforge.noise.DephasingNoise}, a phase damping channel built from the
 *       identity and Pauli {@code Z}, supporting up to eight target qubits</li>
 *   <li>{@link com.mechalikh.qforge.noise.AmplitudeDampingNoise}, a relaxation channel from
 *       {@code |1>} to {@code |0>}, also supporting up to eight target qubits</li>
 * </ul>
 *
 * <p>{@link com.mechalikh.qforge.noise.NoiseProfile} groups models by qubit or globally, and
 * {@link com.mechalikh.qforge.noise.ErrorManager} applies a profile to a state while logging
 * state distances and a running average fidelity. On a state vector backend each channel selects
 * a single Kraus trajectory, so results are sampled; on a density matrix backend channels are
 * applied deterministically.</p>
 */
package com.mechalikh.qforge.noise;

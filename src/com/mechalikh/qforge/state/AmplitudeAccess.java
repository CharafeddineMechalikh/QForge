package com.mechalikh.qforge.state;

import com.mechalikh.qforge.math.Complex;

/**
 * Capability interface for states that expose one entry per computational basis state.
 *
 * <p>{@link StateVector} and {@link DensityMatrix} store an array indexed by basis state, so
 * they implement this interface. A {@link StabilizerState} stores a Clifford tableau with
 * {@code O(n^2)} entries and has no amplitude array, so it does not. Code that needs
 * amplitudes should test for this interface, or call
 * {@link QuantumState#getAmplitude(int)} and handle the
 * {@link UnsupportedOperationException}.</p>
 */
public interface AmplitudeAccess {
    /**
     * Returns the backend representation of one computational basis state.
     *
     * @param basisState basis-state index in {@code [0, 2^numQubits)}
     * @return the amplitude, or the density-matrix analogue of it
     */
    Complex getAmplitude(int basisState);

    /**
     * Returns the population of one computational basis state.
     *
     * @param basisState basis-state index in {@code [0, 2^numQubits)}
     * @return the probability of measuring this basis state
     */
    double getProbability(int basisState);

    /**
     * Returns the populations of all computational basis states.
     *
     * @return an array of length {@code 2^numQubits}, indexed by basis-state index
     */
    double[] getProbabilities();
}

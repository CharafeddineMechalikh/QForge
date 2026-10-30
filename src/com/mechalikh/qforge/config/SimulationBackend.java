package com.mechalikh.qforge.config;

/**
 * Selects the quantum state representation used by a simulation.
 *
 * <p>{@link #STATE_VECTOR} stores one complex amplitude per basis state and represents pure
 * states. {@link #DENSITY_MATRIX} stores a complex matrix and can represent mixed states, at
 * a much higher memory cost per qubit. {@link #STABILIZER} stores a Clifford tableau and
 * scales to thousands of qubits, but only for Clifford gates and Pauli measurements.
 * {@link #TENSOR_NETWORK} stores a matrix product state and truncates entanglement at a
 * configured bond dimension, so it is approximate and reports a discarded weight. The choice
 * determines which {@link com.mechalikh.qforge.strategy.SimulationStrategy} is created
 * during {@link com.mechalikh.qforge.core.QuantumSimulator#initialize}.
 */
public enum SimulationBackend {
    /**
     * State vector backend, described as {@code "State Vector Simulation"}. The recommended
     * qubit limit is 30.
     */
    STATE_VECTOR("State Vector Simulation", 30, true, true),
    /**
     * Density matrix backend, described as {@code "Density Matrix Simulation"}. The
     * recommended qubit limit is 12.
     */
    DENSITY_MATRIX("Density Matrix Simulation", 12, true, true),
    /**
     * Stabilizer backend, described as {@code "Stabilizer Simulation"}. The tableau holds
     * {@code 2n} rows of {@code 2n} bits, so the recommended limit is 2000 qubits. Only
     * Clifford gates and Pauli measurements are supported, and results are exact for that
     * gate set.
     */
    STABILIZER("Stabilizer Simulation", 2000, false, true),
    /**
     * Matrix product state backend, described as {@code "Tensor Network Simulation"}. The
     * recommended limit is 100 qubits, which assumes a small bond dimension. Results are
     * approximate whenever truncation discards Schmidt weight. Computational basis sampling
     * and collapse work at any size, but dense amplitudes, basis-state probabilities, the
     * reduced density matrix, matrix expectation values and non diagonal observables all
     * require 24 qubits or fewer.
     */
    TENSOR_NETWORK("Tensor Network Simulation", 100, false, false);

    private final String description;
    private final int maxRecommendedQubits;
    private final boolean supportsNoise;
    private final boolean exact;

    /**
     * Stores the properties of the backend.
     *
     * @param description description returned by {@link #getDescription()}
     * @param maxRecommendedQubits value returned by {@link #getMaxRecommendedQubits()}
     * @param supportsNoise value returned by {@link #supportsNoise()}
     * @param exact value returned by {@link #isExact()}
     */
    SimulationBackend(String description, int maxRecommendedQubits, boolean supportsNoise,
                      boolean exact) {
        this.description = description;
        this.maxRecommendedQubits = maxRecommendedQubits;
        this.supportsNoise = supportsNoise;
        this.exact = exact;
    }

    /**
     * Returns the human readable description of the backend.
     *
     * @return the description supplied to the constant
     */
    public String getDescription() {
        return description;
    }

    /**
     * Reports whether the backend supports Kraus noise channels.
     *
     * <p>The state vector and density matrix strategies apply Kraus operators. The stabilizer
     * strategy rejects general Kraus channels because a Clifford tableau cannot represent
     * them, and the tensor network strategy rejects them because noise on a matrix product
     * state is not implemented. Both report {@code false} and point callers to the state
     * vector or density matrix backend.</p>
     *
     * @return {@code true} for {@link #STATE_VECTOR} and {@link #DENSITY_MATRIX} only
     */
    public boolean supportsNoise() {
        return supportsNoise;
    }

    /**
     * Reports whether the backend computes exact results.
     *
     * <p>This is {@code true} for the state vector, density matrix and stabilizer backends.
     * A stabilizer state still cannot represent a non-Clifford state, while a density matrix
     * can. The tensor network backend is approximate when its bond-dimension truncation
     * discards weight.</p>
     *
     * @return {@code false} only for {@link #TENSOR_NETWORK}
     */
    public boolean isExact() {
        return exact;
    }

    /**
     * Returns the recommended maximum number of qubits for the backend.
     *
     * <p>{@link #STATE_VECTOR} returns 30, based on {@code 2^30} amplitudes of 16 bytes each
     * (about 16 GiB). {@link #DENSITY_MATRIX} returns 12, based on a {@code 4096 x 4096}
     * matrix with 16 byte elements (about 256 MiB). {@link #STABILIZER} returns 2000, based
     * on a {@code 4000 x 4000} bit tableau (about 2 MiB) plus bookkeeping.
     * {@link #TENSOR_NETWORK} returns 100 for a modest bond dimension.
     * {@link SimulationConfig#validate()} rejects configurations above this value.</p>
     *
     * @return the recommended qubit limit
     */
    public int getMaxRecommendedQubits() {
        return maxRecommendedQubits;
    }
}

package com.mechalikh.qforge.config;

/**
 * Selects the quantum state representation used by a simulation.
 *
 * <p>{@link #STATE_VECTOR} stores one complex amplitude per basis state and represents pure
 * states. {@link #DENSITY_MATRIX} stores a complex matrix and can represent mixed states, at
 * a much higher memory cost per qubit. The choice determines which
 * {@link com.mechalikh.qforge.strategy.SimulationStrategy} is created during
 * {@link com.mechalikh.qforge.core.QuantumSimulator#initialize}.
 */
public enum SimulationBackend {
    /**
     * State vector backend, described as {@code "State Vector Simulation"}. The recommended
     * qubit limit is 30.
     */
    STATE_VECTOR("State Vector Simulation"),
    /**
     * Density matrix backend, described as {@code "Density Matrix Simulation"}. The
     * recommended qubit limit is 12.
     */
    DENSITY_MATRIX("Density Matrix Simulation");
    
    private final String description;
    
    /**
     * Stores the human readable description of the backend.
     *
     * @param description description returned by {@link #getDescription()}
     */
    SimulationBackend(String description) {
        this.description = description;
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
     * Reports whether the backend supports noise.
     *
     * <p>This implementation returns true for both constants; the state vector and density
     * matrix strategies both implement noise application through Kraus operators.
     *
     * @return always true
     */
    public boolean supportsNoise() {
        return true;
    }
    
    /**
     * Reports whether the backend computes exact results.
     *
     * <p>This implementation returns true for both constants. A state vector still cannot
     * represent a mixed state, while a density matrix can.
     *
     * @return always true
     */
    public boolean isExact() {
        return true;
    }
    
    /**
     * Returns the recommended maximum number of qubits for the backend.
     *
     * <p>{@link #STATE_VECTOR} returns 30, based on {@code 2^30} amplitudes of 16 bytes each
     * (about 16 GiB). {@link #DENSITY_MATRIX} returns 12, based on a {@code 4096 x 4096}
     * matrix with 16 byte elements (about 256 MiB). {@link SimulationConfig#validate()}
     * rejects configurations above this value.
     *
     * @return 30 for {@link #STATE_VECTOR}, 12 for {@link #DENSITY_MATRIX}
     */
    public int getMaxRecommendedQubits() {
        switch (this) {
            case STATE_VECTOR:
                return 30; // 2^30 amplitudes at 16 bytes each (~16 GiB)
            case DENSITY_MATRIX:
                return 12; // 4096^2 matrix elements (~256 MiB)
            default:
                return 30;
        }
    }
}

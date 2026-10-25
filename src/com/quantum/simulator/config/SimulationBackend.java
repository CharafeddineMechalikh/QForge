package com.quantum.simulator.config;

public enum SimulationBackend {
    STATE_VECTOR("State Vector Simulation"),
    DENSITY_MATRIX("Density Matrix Simulation");
    
    private final String description;
    
    SimulationBackend(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean supportsNoise() {
        return true;
    }
    
    public boolean isExact() {
        return true;
    }
    
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

package com.quantum.simulator.events;

public enum EventType {
    // Quantum operations
    GATE_OPERATION("Apply quantum gate"),
    MEASUREMENT_OPERATION("Perform measurement"),
    RESET_OPERATION("Reset qubit state"),
    
    // Noise and errors
    NOISE_APPLICATION("Apply noise model"),
    ERROR_INJECTION("Inject error"),
    ERROR_CORRECTION("Apply error correction"),
    
    // State management
    STATE_INITIALIZATION("Initialize quantum state"),
    STATE_TRANSFER("Transfer quantum state"),
    STATE_MEASUREMENT("Measure quantum state"),
    
    // Control and timing
    DELAY("Time delay"),
    BARRIER("Synchronization barrier"),
    CONDITIONAL("Conditional operation"),
    
    // System events
    SIMULATION_START("Start simulation"),
    SIMULATION_STOP("Stop simulation"),
    CHECKPOINT("Create checkpoint"),
    RECOVERY("Recovery operation"),
    
    // Custom/user-defined
    CUSTOM_OPERATION("Custom operation"),
    
    // Meta events
    SCHEDULING("Scheduling directive"),
    OPTIMIZATION("Optimization operation"),
    VALIDATION("Validation check");
    
    private final String description;
    
    EventType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isQuantumOperation() {
        return this == GATE_OPERATION || this == MEASUREMENT_OPERATION || 
               this == RESET_OPERATION || this == STATE_INITIALIZATION;
    }
    
    public boolean isNoiseRelated() {
        return this == NOISE_APPLICATION || this == ERROR_INJECTION || 
               this == ERROR_CORRECTION;
    }
    
    public boolean isControlEvent() {
        return this == DELAY || this == BARRIER || this == CONDITIONAL;
    }
    
    public boolean isSystemEvent() {
        return this == SIMULATION_START || this == SIMULATION_STOP || 
               this == CHECKPOINT || this == RECOVERY;
    }
    
    public static EventType fromString(String str) {
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to match with common patterns
            String lower = str.toLowerCase();
            if (lower.contains("gate")) return GATE_OPERATION;
            if (lower.contains("measure")) return MEASUREMENT_OPERATION;
            if (lower.contains("reset")) return RESET_OPERATION;
            if (lower.contains("noise") || lower.contains("error")) return NOISE_APPLICATION;
            if (lower.contains("delay") || lower.contains("wait")) return DELAY;
            if (lower.contains("barrier")) return BARRIER;
            if (lower.contains("start") || lower.contains("begin")) return SIMULATION_START;
            if (lower.contains("stop") || lower.contains("end")) return SIMULATION_STOP;
            
            return CUSTOM_OPERATION;
        }
    }
}
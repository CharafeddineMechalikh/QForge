package com.mechalikh.qforge.events;

/**
 * Classifies the kind of work represented by a {@link QuantumEvent}.
 *
 * <p>Each constant carries a human readable description, and the predicate methods group
 * related constants for routing and filtering.
 */
public enum EventType {
    // Quantum operations
    /** Application of a quantum gate. */
    GATE_OPERATION("Apply quantum gate"),
    /** Measurement of one or more qubits. */
    MEASUREMENT_OPERATION("Perform measurement"),
    /** Reset of a qubit state. */
    RESET_OPERATION("Reset qubit state"),
    
    // Noise and errors
    /** Application of a noise model. */
    NOISE_APPLICATION("Apply noise model"),
    /** Injection of an error. */
    ERROR_INJECTION("Inject error"),
    /** Application of an error correction step. */
    ERROR_CORRECTION("Apply error correction"),
    
    // State management
    /** Initialization of a quantum state. */
    STATE_INITIALIZATION("Initialize quantum state"),
    /** Transfer of a quantum state. */
    STATE_TRANSFER("Transfer quantum state"),
    /** Measurement of a quantum state. */
    STATE_MEASUREMENT("Measure quantum state"),
    
    // Control and timing
    /** A time delay. */
    DELAY("Time delay"),
    /** A synchronization barrier. */
    BARRIER("Synchronization barrier"),
    /** A conditional operation. */
    CONDITIONAL("Conditional operation"),
    
    // System events
    /** Start of a simulation. */
    SIMULATION_START("Start simulation"),
    /** Stop of a simulation. */
    SIMULATION_STOP("Stop simulation"),
    /** Creation of a checkpoint. */
    CHECKPOINT("Create checkpoint"),
    /** A recovery operation. */
    RECOVERY("Recovery operation"),
    
    // Custom/user-defined
    /** A custom or user defined operation. */
    CUSTOM_OPERATION("Custom operation"),
    
    // Meta events
    /** A scheduling directive. */
    SCHEDULING("Scheduling directive"),
    /** An optimization operation. */
    OPTIMIZATION("Optimization operation"),
    /** A validation check. */
    VALIDATION("Validation check");
    
    private final String description;
    
    EventType(String description) {
        this.description = description;
    }
    
    /**
     * Returns the human readable description of this event type.
     *
     * @return the description supplied when the constant was declared
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Tests whether this type is a quantum operation.
     *
     * <p>This is {@code true} for {@link #GATE_OPERATION}, {@link #MEASUREMENT_OPERATION},
     * {@link #RESET_OPERATION}, and {@link #STATE_INITIALIZATION}. It is {@code false} for
     * {@link #STATE_MEASUREMENT}.
     *
     * @return {@code true} if this type is one of the quantum operation constants
     */
    public boolean isQuantumOperation() {
        return this == GATE_OPERATION || this == MEASUREMENT_OPERATION || 
               this == RESET_OPERATION || this == STATE_INITIALIZATION;
    }
    
    /**
     * Tests whether this type is related to noise or errors.
     *
     * @return {@code true} for {@link #NOISE_APPLICATION}, {@link #ERROR_INJECTION}, and
     *         {@link #ERROR_CORRECTION}
     */
    public boolean isNoiseRelated() {
        return this == NOISE_APPLICATION || this == ERROR_INJECTION || 
               this == ERROR_CORRECTION;
    }
    
    /**
     * Tests whether this type is a control or timing event.
     *
     * @return {@code true} for {@link #DELAY}, {@link #BARRIER}, and {@link #CONDITIONAL}
     */
    public boolean isControlEvent() {
        return this == DELAY || this == BARRIER || this == CONDITIONAL;
    }
    
    /**
     * Tests whether this type is a system event.
     *
     * @return {@code true} for {@link #SIMULATION_START}, {@link #SIMULATION_STOP},
     *         {@link #CHECKPOINT}, and {@link #RECOVERY}
     */
    public boolean isSystemEvent() {
        return this == SIMULATION_START || this == SIMULATION_STOP || 
               this == CHECKPOINT || this == RECOVERY;
    }
    
    /**
     * Parses an event type from its constant name or a descriptive string.
     *
     * <p>The method first tries {@code valueOf} on the upper-cased input. If that fails, it
     * lower-cases the input and returns the first match in this order: a string containing
     * {@code gate}, {@code measure}, {@code reset}, {@code noise} or {@code error},
     * {@code delay} or {@code wait}, {@code barrier}, {@code start} or {@code begin}, and
     * {@code stop} or {@code end}. Unrecognized input returns {@link #CUSTOM_OPERATION}.
     *
     * @param str event type name or description
     * @return the matching event type, or {@link #CUSTOM_OPERATION} when nothing matches
     */
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
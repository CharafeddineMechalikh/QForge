package com.quantum.simulator.core;

import java.util.Locale;
public class SimulationError extends Exception {
    private final ErrorType errorType;
    private final String component;
    private final long timestamp;
    
    public enum ErrorType {
        INITIALIZATION_ERROR,
        CONFIGURATION_ERROR,
        RUNTIME_ERROR,
        RESOURCE_ERROR,
        PLUGIN_ERROR,
        VALIDATION_ERROR,
        UNKNOWN_ERROR
    }
    
    public SimulationError(String message) {
        this(message, ErrorType.UNKNOWN_ERROR, "Unknown", null);
    }
    
    public SimulationError(String message, Throwable cause) {
        this(message, ErrorType.UNKNOWN_ERROR, "Unknown", cause);
    }
    
    public SimulationError(String message, ErrorType errorType, String component) {
        this(message, errorType, component, null);
    }
    
    public SimulationError(String message, ErrorType errorType, String component, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.component = component;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ErrorType getErrorType() {
        return errorType;
    }
    
    public String getComponent() {
        return component;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getDetailedMessage() {
        return String.format(Locale.ROOT, "[%s] %s: %s (Component: %s, Time: %d)",
            errorType.name(),
            getClass().getSimpleName(),
            getMessage(),
            component,
            timestamp);
    }
    
    public static SimulationError initializationError(String message, Throwable cause) {
        return new SimulationError(message, ErrorType.INITIALIZATION_ERROR, "Initialization", cause);
    }
    
    public static SimulationError configurationError(String message) {
        return new SimulationError(message, ErrorType.CONFIGURATION_ERROR, "Configuration");
    }
    
    public static SimulationError runtimeError(String message, String component) {
        return new SimulationError(message, ErrorType.RUNTIME_ERROR, component);
    }
}
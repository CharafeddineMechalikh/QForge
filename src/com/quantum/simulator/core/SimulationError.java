package com.quantum.simulator.core;

import java.util.Locale;
/**
 * Checked exception raised by the simulator API.
 *
 * <p>In addition to a message and an optional cause, every instance carries an
 * {@link ErrorType}, the name of the component that reported the failure (for example
 * {@code "Configuration"} or {@code "Initialization"}), and the creation timestamp in
 * milliseconds since the epoch. The factory methods create instances with predefined type
 * and component values.
 */
public class SimulationError extends Exception {
    private final ErrorType errorType;
    private final String component;
    private final long timestamp;
    
    /**
     * Classifies the failure for callers and for {@link #getDetailedMessage()}.
     */
    public enum ErrorType {
        /**
         * Failure while preparing a simulator, for example while creating its state.
         */
        INITIALIZATION_ERROR,
        /**
         * Failure caused by invalid or inconsistent configuration values.
         */
        CONFIGURATION_ERROR,
        /**
         * Failure during event execution or another part of a simulation run.
         */
        RUNTIME_ERROR,
        /**
         * Failure related to resource allocation or resource limits.
         */
        RESOURCE_ERROR,
        /**
         * Failure related to loading, unloading or running a plugin.
         */
        PLUGIN_ERROR,
        /**
         * Failure reported by a validation step.
         */
        VALIDATION_ERROR,
        /**
         * Failure that could not be classified. This is the default for constructors that
         * do not take a type.
         */
        UNKNOWN_ERROR
    }
    
    /**
     * Creates an error with {@link ErrorType#UNKNOWN_ERROR}, component {@code "Unknown"} and
     * no cause.
     *
     * @param message detail message
     */
    public SimulationError(String message) {
        this(message, ErrorType.UNKNOWN_ERROR, "Unknown", null);
    }
    
    /**
     * Creates an error with a cause, {@link ErrorType#UNKNOWN_ERROR} and component
     * {@code "Unknown"}.
     *
     * @param message detail message
     * @param cause underlying cause, may be null
     */
    public SimulationError(String message, Throwable cause) {
        this(message, ErrorType.UNKNOWN_ERROR, "Unknown", cause);
    }
    
    /**
     * Creates an error without a cause.
     *
     * @param message detail message
     * @param errorType classification of the failure
     * @param component name of the component that reported the failure
     */
    public SimulationError(String message, ErrorType errorType, String component) {
        this(message, errorType, component, null);
    }
    
    /**
     * Creates an error with all fields.
     *
     * <p>The timestamp is taken from the system clock at construction time.
     *
     * @param message detail message
     * @param errorType classification of the failure
     * @param component name of the component that reported the failure
     * @param cause underlying cause, may be null
     */
    public SimulationError(String message, ErrorType errorType, String component, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.component = component;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Returns the classification of this failure.
     *
     * @return the error type, never null
     */
    public ErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * Returns the name of the component that reported the failure.
     *
     * @return the component name, for example {@code "Unknown"}
     */
    public String getComponent() {
        return component;
    }
    
    /**
     * Returns the construction time of this error.
     *
     * @return milliseconds since the epoch
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Returns a formatted description with the error type, concrete class name, message,
     * component and timestamp.
     *
     * @return formatted description such as
     *         {@code [UNKNOWN_ERROR] SimulationError: msg (Component: Unknown, Time: 0)}
     */
    public String getDetailedMessage() {
        return String.format(Locale.ROOT, "[%s] %s: %s (Component: %s, Time: %d)",
            errorType.name(),
            getClass().getSimpleName(),
            getMessage(),
            component,
            timestamp);
    }
    
    /**
     * Creates an initialization error with component {@code "Initialization"}.
     *
     * @param message detail message
     * @param cause underlying cause, may be null
     * @return a new error with type {@link ErrorType#INITIALIZATION_ERROR}
     */
    public static SimulationError initializationError(String message, Throwable cause) {
        return new SimulationError(message, ErrorType.INITIALIZATION_ERROR, "Initialization", cause);
    }
    
    /**
     * Creates a configuration error with component {@code "Configuration"} and no cause.
     *
     * @param message detail message
     * @return a new error with type {@link ErrorType#CONFIGURATION_ERROR}
     */
    public static SimulationError configurationError(String message) {
        return new SimulationError(message, ErrorType.CONFIGURATION_ERROR, "Configuration");
    }
    
    /**
     * Creates a runtime error with the given component and no cause.
     *
     * @param message detail message
     * @param component name of the component that reported the failure
     * @return a new error with type {@link ErrorType#RUNTIME_ERROR}
     */
    public static SimulationError runtimeError(String message, String component) {
        return new SimulationError(message, ErrorType.RUNTIME_ERROR, component);
    }
}
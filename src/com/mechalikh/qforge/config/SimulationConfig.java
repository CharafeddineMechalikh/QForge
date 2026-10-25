package com.mechalikh.qforge.config;

import com.mechalikh.qforge.noise.NoiseProfile;
import com.mechalikh.qforge.operations.GateLibrary;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Configuration for a simulation run.
 *
 * <p>Defaults established by the no argument constructor: name {@code "Simulation"}, empty
 * description, one qubit, backend {@link SimulationBackend#STATE_VECTOR}, time resolution
 * {@link TimeResolution#NANOSECOND}, a default {@link ResourceConstraints}, an empty
 * {@link NoiseProfile}, a default {@link GateLibrary}, no step limit ({@code maxSteps} is
 * 0), no timeout ({@code timeoutSeconds} is 0), classical control disabled, and an empty
 * custom parameter map.
 *
 * <p>{@link #validate()} checks the qubit count, the presence of a backend and a time
 * resolution, the backend qubit limit, the resource constraints, the step and timeout
 * values, and an estimated memory requirement against the memory limit. The estimate is 16
 * bytes per amplitude plus 1024 bytes for the state vector backend, and 16 bytes per density
 * matrix element plus 2048 bytes for the density matrix backend.
 *
 * <p>{@link #loadFromFile(String)} reads a JSON document into this instance, and
 * {@link #saveToFile(String)} writes one. Loading ignores unknown properties, permits
 * comments, and supports the fields listed in {@link #saveToFile(String)}. The gate library
 * is not part of the JSON document.
 */
public class SimulationConfig {
    private String name;
    private String description;
    private int numQubits;
    private SimulationBackend backend;
    private TimeResolution timeResolution;
    private ResourceConstraints resourceConstraints;
    private NoiseProfile noiseProfile;
    private GateLibrary gateLibrary;
    private int maxSteps;
    private double timeoutSeconds;
    private boolean classicalControlEnabled;
    private Map<String, Object> customParameters;
    
    /**
     * Creates a configuration with the defaults described in the class documentation.
     */
    public SimulationConfig() {
        this.name = "Simulation";
        this.description = "";
        this.numQubits = 1;
        this.backend = SimulationBackend.STATE_VECTOR;
        this.timeResolution = TimeResolution.NANOSECOND;
        this.resourceConstraints = new ResourceConstraints();
        this.noiseProfile = new NoiseProfile();
        this.gateLibrary = new GateLibrary();
        this.maxSteps = 0; // Unlimited
        this.timeoutSeconds = 0; // No timeout
        this.classicalControlEnabled = false;
        this.customParameters = new HashMap<>();
    }
    
    /**
     * Validates the configuration without changing it.
     *
     * <p>The method returns false if any of the following holds:
     * <ul>
     *   <li>{@code numQubits} is not positive</li>
     *   <li>the backend is null</li>
     *   <li>{@code numQubits} exceeds {@link SimulationBackend#getMaxRecommendedQubits()}</li>
     *   <li>the time resolution is null</li>
     *   <li>the resource constraints are null or fail
     *       {@link ResourceConstraints#validate()}</li>
     *   <li>{@code maxSteps} or {@code timeoutSeconds} is negative</li>
     *   <li>the estimated memory requirement exceeds the memory limit</li>
     * </ul>
     *
     * <p>The memory estimate is 16 bytes per amplitude plus 1024 bytes for
     * {@link SimulationBackend#STATE_VECTOR}, and 16 bytes per density matrix element plus
     * 2048 bytes for {@link SimulationBackend#DENSITY_MATRIX}. A null backend is rejected
     * before the estimate is computed.
     *
     * @return true if the configuration can be used for a simulation
     */
    public boolean validate() {
        if (numQubits <= 0) {
            return false;
        }
        
        if (backend == null) {
            return false;
        }
        
        if (numQubits > backend.getMaxRecommendedQubits()) {
            return false;
        }
        
        if (timeResolution == null) {
            return false;
        }
        
        if (resourceConstraints == null || !resourceConstraints.validate()) {
            return false;
        }
        
        if (maxSteps < 0) {
            return false;
        }
        
        if (timeoutSeconds < 0) {
            return false;
        }
        
        // Validate resource constraints based on backend
        long requiredMemory = estimateMemoryRequirement();
        if (requiredMemory > resourceConstraints.getMaxMemoryBytes()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Loads configuration values from a JSON file into this instance.
     *
     * <p>Unknown properties are ignored and comments are allowed. Required fields are
     * {@code numQubits}, which must be positive, and {@code backend}, which must be the name
     * of a {@link SimulationBackend} constant in any case. {@code maxSteps} and
     * {@code timeoutSeconds} must not be negative. These checks run before any value is
     * assigned.
     *
     * <p>Optional fields: {@code name} and {@code description} are applied only when non
     * null; {@code timeResolution} is applied when non blank and is matched against
     * {@link TimeResolution} constants in any case; a positive {@code maxMemoryBytes}
     * replaces the resource constraints with a new {@link ResourceConstraints} built from
     * {@code maxMemoryBytes}, {@code maxCPUs} and {@code maxGPUs}; a non null
     * {@code noiseProfile} is passed to {@link NoiseProfile#loadFromMap(java.util.Map)},
     * where an empty map is ignored; {@code customParameters} are merged into the existing
     * map rather than replacing it.
     *
     * <p>{@code classicalControlEnabled} is always assigned, so an absent field sets it to
     * false. {@code maxSteps} and {@code timeoutSeconds} are assigned only when positive, so
     * an absent or zero value keeps the value already in this instance.
     *
     * @param filePath path of the JSON file to read
     * @throws IOException if the file cannot be read or parsed, or if a required field is
     *         missing or invalid
     * @throws IllegalArgumentException if {@code backend} or {@code timeResolution} is not a
     *         known constant name; this unchecked exception is not converted to an
     *         {@code IOException}
     */
    public void loadFromFile(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        ConfigData data = mapper.readValue(new File(filePath), ConfigData.class);
        
        if (data.numQubits <= 0) {
            throw new IOException("Config field 'numQubits' must be positive");
        }
        if (data.backend == null || data.backend.isBlank()) {
            throw new IOException("Config field 'backend' is required");
        }
        if (data.maxSteps < 0) {
            throw new IOException("Config field 'maxSteps' cannot be negative");
        }
        if (data.timeoutSeconds < 0) {
            throw new IOException("Config field 'timeoutSeconds' cannot be negative");
        }
        
        if (data.name != null) {
            this.name = data.name;
        }
        if (data.description != null) {
            this.description = data.description;
        }
        this.numQubits = data.numQubits;
        this.backend = SimulationBackend.valueOf(data.backend.toUpperCase(Locale.ROOT));
        if (data.timeResolution != null && !data.timeResolution.isBlank()) {
            this.timeResolution = TimeResolution.valueOf(data.timeResolution.toUpperCase(Locale.ROOT));
        }
        if (data.maxMemoryBytes > 0) {
            this.resourceConstraints = new ResourceConstraints(
                data.maxMemoryBytes,
                data.maxCPUs,
                data.maxGPUs
            );
        }
        if (data.maxSteps > 0) {
            this.maxSteps = data.maxSteps;
        }
        if (data.timeoutSeconds > 0) {
            this.timeoutSeconds = data.timeoutSeconds;
        }
        this.classicalControlEnabled = data.classicalControlEnabled;
        
        if (data.noiseProfile != null) {
            this.noiseProfile.loadFromMap(data.noiseProfile);
        }
        
        if (data.customParameters != null) {
            this.customParameters.putAll(data.customParameters);
        }
    }
    
    /**
     * Writes the current configuration as indented JSON.
     *
     * <p>The document contains {@code name}, {@code description}, {@code numQubits},
     * {@code backend}, {@code timeResolution}, {@code maxMemoryBytes}, {@code maxCPUs},
     * {@code maxGPUs}, {@code noiseProfile}, {@code maxSteps}, {@code timeoutSeconds},
     * {@code classicalControlEnabled} and {@code customParameters}. The gate library is not
     * saved. An empty noise profile is written as an empty object.
     *
     * <p>The method does not validate the configuration and does not create parent
     * directories.
     *
     * @param filePath path of the target file
     * @throws IOException if the file cannot be written
     * @throws NullPointerException if the noise profile is null, because it is converted to
     *         a map unconditionally
     */
    public void saveToFile(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), toConfigData());
    }
    
    private ConfigData toConfigData() {
        ConfigData data = new ConfigData();
        data.name = name;
        data.description = description;
        data.numQubits = numQubits;
        data.backend = backend.name();
        data.timeResolution = timeResolution.name();
        data.maxMemoryBytes = resourceConstraints.getMaxMemoryBytes();
        data.maxCPUs = resourceConstraints.getMaxCPUs();
        data.maxGPUs = resourceConstraints.getMaxGPUs();
        data.noiseProfile = noiseProfile.toMap();
        data.maxSteps = maxSteps;
        data.timeoutSeconds = timeoutSeconds;
        data.classicalControlEnabled = classicalControlEnabled;
        data.customParameters = new LinkedHashMap<>(customParameters);
        return data;
    }
    
    /**
     * Returns the configuration name.
     *
     * @return the name, {@code "Simulation"} by default
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets the configuration name.
     *
     * @param name new name; may be null
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Returns the configuration description.
     *
     * @return the description, empty by default
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Sets the configuration description.
     *
     * @param description new description; may be null
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    private long estimateMemoryRequirement() {
        // Rough memory estimation for different backends
        switch (backend) {
            case STATE_VECTOR:
                // Complex double per amplitude: 16 bytes
                // 2^numQubits amplitudes
                long vectorSize = (long) Math.pow(2, numQubits);
                return vectorSize * 16L + 1024; // Plus overhead
                
            case DENSITY_MATRIX:
                // Complex double per matrix element: 16 bytes
                // (2^numQubits)^2 elements
                long matrixSize = (long) Math.pow(2, numQubits);
                return matrixSize * matrixSize * 16L + 2048;
                
            default:
                return 1024L * 1024L * 10; // 10MB default
        }
    }
    
    /**
     * Reports whether a noise profile with at least one noise model is configured.
     *
     * @return true when the noise profile is non null and not empty
     */
    public boolean hasNoise() {
        return noiseProfile != null && !noiseProfile.isEmpty();
    }
    
    // Getters and Setters
    /**
     * Returns the number of qubits in the simulated register.
     *
     * @return the qubit count, 1 by default
     */
    public int getNumQubits() {
        return numQubits;
    }
    
    /**
     * Sets the number of qubits. The value is not validated here; call {@link #validate()}.
     *
     * @param numQubits number of qubits
     */
    public void setNumQubits(int numQubits) {
        this.numQubits = numQubits;
    }
    
    /**
     * Returns the selected simulation backend.
     *
     * @return the backend, {@link SimulationBackend#STATE_VECTOR} by default
     */
    public SimulationBackend getBackend() {
        return backend;
    }
    
    /**
     * Sets the simulation backend. The value is not validated here, but a null value makes
     * {@link #validate()} return false.
     *
     * @param backend backend to use
     */
    public void setBackend(SimulationBackend backend) {
        this.backend = backend;
    }
    
    /**
     * Returns the configured time unit.
     *
     * <p>The value is stored with the configuration and validated for presence;
     * {@link com.mechalikh.qforge.events.QuantumEvent} timestamps are {@link java.time.Instant}
     * values and are not converted with this unit by the simulator.
     *
     * @return the time resolution, {@link TimeResolution#NANOSECOND} by default
     */
    public TimeResolution getTimeResolution() {
        return timeResolution;
    }
    
    /**
     * Sets the configured time unit. A null value makes {@link #validate()} return false.
     *
     * @param timeResolution time unit to record
     */
    public void setTimeResolution(TimeResolution timeResolution) {
        this.timeResolution = timeResolution;
    }
    
    /**
     * Returns the resource constraints used by validation and by the resource manager.
     *
     * @return the constraints, never null in a default configuration
     */
    public ResourceConstraints getResourceConstraints() {
        return resourceConstraints;
    }
    
    /**
     * Sets the resource constraints. A null value makes {@link #validate()} return false.
     *
     * @param resourceConstraints constraints to use
     */
    public void setResourceConstraints(ResourceConstraints resourceConstraints) {
        this.resourceConstraints = resourceConstraints;
    }
    
    /**
     * Returns the noise profile applied after gates.
     *
     * @return the noise profile, an empty profile by default; may be null when set so
     */
    public NoiseProfile getNoiseProfile() {
        return noiseProfile;
    }
    
    /**
     * Sets the noise profile applied after gates.
     *
     * <p>The value is stored by reference. A null profile disables configured noise but is
     * not rejected by {@link #validate()}; it does make {@link #saveToFile(String)} fail.
     *
     * @param noiseProfile noise profile to use, may be null
     */
    public void setNoiseProfile(NoiseProfile noiseProfile) {
        this.noiseProfile = noiseProfile;
    }
    
    /**
     * Returns the gate library associated with this configuration.
     *
     * <p>The library is stored in memory only and is not part of the JSON representation.
     *
     * @return the gate library, a default library by default
     */
    public GateLibrary getGateLibrary() {
        return gateLibrary;
    }
    
    /**
     * Sets the gate library. The library is not validated and is not saved to JSON.
     *
     * @param gateLibrary gate library to associate with this configuration
     */
    public void setGateLibrary(GateLibrary gateLibrary) {
        this.gateLibrary = gateLibrary;
    }
    
    /**
     * Returns the maximum number of operations to execute.
     *
     * <p>A value of 0 means no limit. The value is checked by
     * {@link com.mechalikh.qforge.core.SimulationContext#shouldStop()} after each event.
     *
     * @return the step limit, 0 by default
     */
    public int getMaxSteps() {
        return maxSteps;
    }
    
    /**
     * Sets the maximum number of operations to execute.
     *
     * <p>The value is not validated here; {@link #validate()} rejects negative values. A
     * value of 0 means no limit.
     *
     * @param maxSteps step limit
     */
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }
    
    /**
     * Returns the configured timeout in seconds.
     *
     * <p>A value of 0 means no timeout. The value is validated and serialized, but
     * {@link com.mechalikh.qforge.core.QuantumSimulator} does not enforce a timeout.
     *
     * @return the timeout in seconds, 0 by default
     */
    public double getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    /**
     * Sets the timeout in seconds.
     *
     * <p>The value is not validated here; {@link #validate()} rejects negative values. The
     * simulator stores the value but does not enforce it.
     *
     * @param timeoutSeconds timeout in seconds, 0 for no timeout
     */
    public void setTimeoutSeconds(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
    
    /**
     * Reports whether the classical control layer is enabled.
     *
     * <p>When enabled, {@link com.mechalikh.qforge.core.QuantumSimulator} creates a
     * {@link com.mechalikh.qforge.classical.ClassicalController} during initialization.
     *
     * @return true when classical control is enabled, false by default
     */
    public boolean isClassicalControlEnabled() {
        return classicalControlEnabled;
    }
    
    /**
     * Enables or disables the classical control layer.
     *
     * <p>The setting is read during initialization; changing it afterwards does not create
     * or remove an existing controller.
     *
     * @param classicalControlEnabled true to enable classical control
     */
    public void setClassicalControlEnabled(boolean classicalControlEnabled) {
        this.classicalControlEnabled = classicalControlEnabled;
    }
    
    /**
     * Returns an unmodifiable view of the custom parameters.
     *
     * <p>The view is backed by the live map, so later changes made through
     * {@link #setCustomParameter(String, Object)} are visible through it.
     *
     * @return an unmodifiable view of the custom parameter map
     */
    public Map<String, Object> getCustomParameters() {
        return Collections.unmodifiableMap(customParameters);
    }
    
    /**
     * Adds or replaces a custom parameter.
     *
     * @param key parameter name; may be null
     * @param value parameter value; may be null
     */
    public void setCustomParameter(String key, Object value) {
        customParameters.put(key, value);
    }
    
    /**
     * Reads one custom parameter.
     *
     * @param key parameter name
     * @return the value, or null when the key is absent or mapped to null
     */
    public Object getCustomParameter(String key) {
        return customParameters.get(key);
    }
    
    // Inner class for JSON serialization
    private static class ConfigData {
        public String name;
        public String description;
        public int numQubits;
        public String backend;
        public String timeResolution;
        public long maxMemoryBytes;
        public int maxCPUs;
        public int maxGPUs;
        public Map<String, Object> noiseProfile;
        public int maxSteps;
        public double timeoutSeconds;
        public boolean classicalControlEnabled;
        public Map<String, Object> customParameters;
    }
}
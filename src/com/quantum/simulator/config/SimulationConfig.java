package com.quantum.simulator.config;

import com.quantum.simulator.noise.NoiseProfile;
import com.quantum.simulator.operations.GateLibrary;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.*;

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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
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
    
    public boolean hasNoise() {
        return noiseProfile != null && !noiseProfile.isEmpty();
    }
    
    // Getters and Setters
    public int getNumQubits() {
        return numQubits;
    }
    
    public void setNumQubits(int numQubits) {
        this.numQubits = numQubits;
    }
    
    public SimulationBackend getBackend() {
        return backend;
    }
    
    public void setBackend(SimulationBackend backend) {
        this.backend = backend;
    }
    
    public TimeResolution getTimeResolution() {
        return timeResolution;
    }
    
    public void setTimeResolution(TimeResolution timeResolution) {
        this.timeResolution = timeResolution;
    }
    
    public ResourceConstraints getResourceConstraints() {
        return resourceConstraints;
    }
    
    public void setResourceConstraints(ResourceConstraints resourceConstraints) {
        this.resourceConstraints = resourceConstraints;
    }
    
    public NoiseProfile getNoiseProfile() {
        return noiseProfile;
    }
    
    public void setNoiseProfile(NoiseProfile noiseProfile) {
        this.noiseProfile = noiseProfile;
    }
    
    public GateLibrary getGateLibrary() {
        return gateLibrary;
    }
    
    public void setGateLibrary(GateLibrary gateLibrary) {
        this.gateLibrary = gateLibrary;
    }
    
    public int getMaxSteps() {
        return maxSteps;
    }
    
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }
    
    public double getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    public void setTimeoutSeconds(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
    
    public boolean isClassicalControlEnabled() {
        return classicalControlEnabled;
    }
    
    public void setClassicalControlEnabled(boolean classicalControlEnabled) {
        this.classicalControlEnabled = classicalControlEnabled;
    }
    
    public Map<String, Object> getCustomParameters() {
        return Collections.unmodifiableMap(customParameters);
    }
    
    public void setCustomParameter(String key, Object value) {
        customParameters.put(key, value);
    }
    
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
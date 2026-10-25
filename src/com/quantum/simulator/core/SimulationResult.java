package com.quantum.simulator.core;

import java.util.Locale;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.observer.MetricsCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class SimulationResult {
    private final QuantumState finalState;
    private final List<MeasurementResult> measurements;
    private final MetricsCollector metrics;
    private final long totalRuntimeMs;
    private final Map<String, Object> additionalData;
    
    public SimulationResult(QuantumState finalState, 
                          List<MeasurementResult> measurements,
                          MetricsCollector metrics,
                          long totalRuntimeMs) {
        this.finalState = finalState;
        this.measurements = List.copyOf(measurements);
        this.metrics = metrics;
        this.totalRuntimeMs = totalRuntimeMs;
        this.additionalData = new HashMap<>();
    }
    
    public QuantumState getFinalState() {
        return finalState;
    }
    
    public List<MeasurementResult> getMeasurements() {
        return measurements;
    }
    
    public MeasurementResult getMeasurement(String observableName) {
        return measurements.stream()
            .filter(m -> m.getObservableName().equals(observableName))
            .findFirst()
            .orElse(null);
    }
    
    public Map<String, Number> getMetrics() {
        return metrics != null ? metrics.getAllMetrics() : Map.of();
    }
    
    public Number getMetric(String metricName) {
        return metrics != null ? metrics.getMetric(metricName) : null;
    }
    
    public long getTotalRuntimeMs() {
        return totalRuntimeMs;
    }
    
    public void putAdditionalData(String key, Object value) {
        additionalData.put(key, value);
    }
    
    public Object getAdditionalData(String key) {
        return additionalData.get(key);
    }
    
    public Map<String, Object> getAdditionalData() {
        return new HashMap<>(additionalData);
    }
    
    public void saveToFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        Files.createDirectories(path.getParent());
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        ResultData data = new ResultData(this);
        mapper.writeValue(new File(filePath), data);
    }
    
    public String toJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        ResultData data = new ResultData(this);
        return mapper.writeValueAsString(data);
    }
    
    // Inner class for JSON serialization
    private static class ResultData {
        public final String simulationType;
        public final long numQubits;
        public final long runtimeMs;
        public final long stepsExecuted;
        public final Map<String, Number> metrics;
        public final List<MeasurementSummary> measurements;
        public final Map<String, Object> additionalData;
        
        public ResultData(SimulationResult result) {
            this.simulationType = result.finalState.getClass().getSimpleName();
            this.numQubits = result.finalState.getNumQubits();
            this.runtimeMs = result.totalRuntimeMs;
            Number steps = result.metrics != null
                ? result.metrics.getMetric("steps_executed") : null;
            this.stepsExecuted = steps != null ? steps.longValue() : 0L;
            this.metrics = result.metrics != null ? result.metrics.getAllMetrics() : new HashMap<>();
            this.measurements = result.measurements.stream()
                .map(MeasurementSummary::new)
                .toList();
            this.additionalData = result.additionalData;
        }
        
        private static class MeasurementSummary {
            public final String observable;
            public final double expectation;
            public final double variance;
            public final int shots;
            
            public MeasurementSummary(MeasurementResult measurement) {
                this.observable = measurement.getObservableName();
                this.expectation = measurement.getExpectationValue();
                this.variance = measurement.getVariance();
                this.shots = measurement.getTotalShots();
            }
        }
    }
    
    @Override
    public String toString() {
        Number steps = getMetric("steps_executed");
        return String.format(Locale.ROOT, 
            "SimulationResult{qubits=%d, runtime=%dms, steps=%s, measurements=%d}",
            finalState.getNumQubits(),
            totalRuntimeMs,
            steps != null ? steps : 0,
            measurements.size()
        );
    }
}
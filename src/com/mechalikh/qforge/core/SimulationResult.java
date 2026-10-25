package com.mechalikh.qforge.core;

import java.util.Locale;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.observer.MetricsCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Snapshot of a finished simulation.
 *
 * <p>The result holds the final quantum state, an immutable copy of the measurement list,
 * the metrics collector of the run, the runtime measured by {@link QuantumSimulator#run()},
 * and a mutable map of additional data. Additional data can be attached after construction
 * and is included in the JSON output.
 *
 * <p>{@link #toJson()} and {@link #saveToFile(String)} use an internal representation with
 * the fields {@code simulationType}, {@code numQubits}, {@code runtimeMs},
 * {@code stepsExecuted}, {@code metrics}, {@code measurements} and {@code additionalData}.
 * Each measurement is reduced to its observable name, expectation value, variance and shot
 * count. The final state itself, including its amplitudes or matrix elements, is not
 * serialized; {@code simulationType} is the simple class name of the final state class.
 */
public class SimulationResult {
    private final QuantumState finalState;
    private final List<MeasurementResult> measurements;
    private final MetricsCollector metrics;
    private final long totalRuntimeMs;
    private final Map<String, Object> additionalData;
    
    /**
     * Creates a result snapshot.
     *
     * <p>The measurement list is copied with {@code List.copyOf}, so later changes to the
     * supplied list are not reflected and the stored list is immutable. The state and the
     * metrics collector are stored by reference. The additional data map starts empty.
     *
     * @param finalState final state of the simulation; it is read during JSON serialization
     *        and string conversion
     * @param measurements measurements collected during the run; the list must not be null
     *        and must not contain null elements
     * @param metrics metrics collector of the run; may be null
     * @param totalRuntimeMs runtime of the run in milliseconds
     * @throws NullPointerException if {@code measurements} is null or contains a null
     *         element
     */
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
    
    /**
     * Returns the final quantum state.
     *
     * @return the state supplied to the constructor
     */
    public QuantumState getFinalState() {
        return finalState;
    }
    
    /**
     * Returns the immutable measurement list captured by the constructor.
     *
     * @return an unmodifiable list of measurements in recording order
     */
    public List<MeasurementResult> getMeasurements() {
        return measurements;
    }
    
    /**
     * Returns the first measurement whose observable name equals the given name.
     *
     * <p>The comparison is case sensitive and uses {@link String#equals(Object)}.
     *
     * @param observableName observable name to match
     * @return the first matching measurement, or null when no measurement matches
     */
    public MeasurementResult getMeasurement(String observableName) {
        return measurements.stream()
            .filter(m -> m.getObservableName().equals(observableName))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Returns a copy of the metrics recorded during the run.
     *
     * @return a new map of metric names to values, or an empty map when no metrics collector
     *         is available
     */
    public Map<String, Number> getMetrics() {
        return metrics != null ? metrics.getAllMetrics() : Map.of();
    }
    
    /**
     * Returns one metric recorded during the run.
     *
     * @param metricName metric name to look up
     * @return the metric value, or null when the metric is absent or no metrics collector is
     *         available
     */
    public Number getMetric(String metricName) {
        return metrics != null ? metrics.getMetric(metricName) : null;
    }
    
    /**
     * Returns the runtime measured by {@link QuantumSimulator#run()}.
     *
     * @return runtime in milliseconds
     */
    public long getTotalRuntimeMs() {
        return totalRuntimeMs;
    }
    
    /**
     * Adds or replaces an entry in the additional data map. Entries are included in JSON
     * output.
     *
     * @param key entry key; may be null
     * @param value entry value; may be null
     */
    public void putAdditionalData(String key, Object value) {
        additionalData.put(key, value);
    }
    
    /**
     * Reads one entry from the additional data map.
     *
     * @param key entry key
     * @return the stored value, or null when the key is absent or mapped to null
     */
    public Object getAdditionalData(String key) {
        return additionalData.get(key);
    }
    
    /**
     * Returns a shallow copy of the additional data map.
     *
     * @return a new map containing the current entries
     */
    public Map<String, Object> getAdditionalData() {
        return new HashMap<>(additionalData);
    }
    
    /**
     * Writes the JSON representation to a file.
     *
     * <p>The parent directory of the target path is created when missing, and the JSON is
     * written with indentation. The class description lists the fields that are written.
     *
     * @param filePath path of the target file; it must include a parent directory, because
     *        the parent is passed to {@link Files#createDirectories}
     * @throws IOException if the parent directories cannot be created or the file cannot be
     *         written
     * @throws NullPointerException if the path has no parent directory, as is the case for a
     *         bare file name
     */
    public void saveToFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        Files.createDirectories(path.getParent());
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        ResultData data = new ResultData(this);
        mapper.writeValue(new File(filePath), data);
    }
    
    /**
     * Serializes the result to an indented JSON string using the internal representation
     * described in the class documentation.
     *
     * @return the JSON text
     * @throws IOException if serialization fails, for example because an additional data
     *         value cannot be serialized
     */
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
    
    /**
     * Returns a one line summary with the qubit count, runtime in milliseconds, step count
     * and measurement count.
     *
     * <p>The step count comes from the {@code steps_executed} metric and is shown as 0 when
     * that metric is absent.
     *
     * @return summary text
     */
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
package com.quantum.simulator.observer;

import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.measurement.MeasurementResult;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsCollector implements SimulationObserver {
    private final Map<String, Number> metrics;
    private final List<MeasurementRecord> measurementHistory;
    private final List<StateSnapshot> stateHistory;
    private final Map<String, AtomicLong> operationCounts;
    private final Map<String, AtomicLong> errorCounts;
    private Instant simulationStartTime;
    private Instant simulationEndTime;
    private long stateUpdates;
    private long measurements;
    private long eventsScheduled;
    private long errors;
    private double cumulativeFidelity;
    private int fidelitySamples;
    private double cumulativeDistance;
    private int distanceSamples;
    private QuantumState initialState;
    
    public MetricsCollector() {
        this.metrics = new ConcurrentHashMap<>();
        this.measurementHistory = Collections.synchronizedList(new ArrayList<>());
        this.stateHistory = Collections.synchronizedList(new ArrayList<>());
        this.operationCounts = new ConcurrentHashMap<>();
        this.errorCounts = new ConcurrentHashMap<>();
        reset();
    }
     
    @Override
    public void onStateUpdate(QuantumState state, QuantumEvent event) {
        stateUpdates++;
        metrics.put("state_updates", stateUpdates);
        metrics.put("last_state_update", System.currentTimeMillis());
        metrics.putIfAbsent("num_qubits", state.getNumQubits());
        
        if (event != null && event.getOperation() != null) {
            String opName = event.getOperation().getName();
            operationCounts.computeIfAbsent(opName, k -> new AtomicLong(0)).incrementAndGet();
            metrics.put("operation_" + opName + "_count", 
                operationCounts.get(opName).get());
        }
        
        // Record state snapshot periodically
        if (stateUpdates % 100 == 0) {
            recordStateSnapshot(state);
        }
    }
    
    
    @Override
    public void onEventScheduled(QuantumEvent event) {
        eventsScheduled++;
        metrics.put("events_scheduled", eventsScheduled);
        if (simulationStartTime == null) {
            simulationStartTime = Instant.now();
            metrics.put("simulation_start_time", simulationStartTime.toEpochMilli());
        }
    }
    
    @Override
    public void onError(String errorMessage, Throwable error) {
        errors++;
        metrics.put("errors", errors);
        
        String errorType = error != null ? error.getClass().getSimpleName() : "Unknown";
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        metrics.put("error_" + errorType + "_count", errorCounts.get(errorType).get());
        
        metrics.put("last_error_time", System.currentTimeMillis()); 
    }
     
    @Override
    public void onInitialized(SimulationContext context) {
        if (context != null) {
            simulationStartTime = Instant.now();
            initialState = context.getCurrentState();
            metrics.put("simulation_start_time", simulationStartTime.toEpochMilli());
            metrics.put("num_qubits", context.getConfig().getNumQubits());
            metrics.put("backend", context.getConfig().getBackend().ordinal());
        }
    }
    
    @Override
    public void onCompleted(SimulationResult result) {
        simulationEndTime = Instant.now();
        metrics.put("simulation_end_time", simulationEndTime.toEpochMilli());
        
        // Only calculate duration if we have a start time
        if (simulationStartTime != null) {
            Duration duration = Duration.between(simulationStartTime, simulationEndTime);
            metrics.put("simulation_duration_ms", duration.toMillis());
            metrics.put("simulation_duration_seconds", duration.toSeconds());
        } else {
            // Use a default or log a warning
            metrics.put("simulation_duration_ms", 0L);
            metrics.put("simulation_duration_seconds", 0L);
        }
        
        // Calculate final statistics
        calculateFinalStats(result);
    }
    
    @Override
    public void onReset() {
        reset();
    }
    
    public void recordMetric(String name, Number value) {
        metrics.put(name, value);
    }
    
    public void recordOperation(QuantumOperation operation) {
        String opName = operation.getName();
        operationCounts.computeIfAbsent(opName, k -> new AtomicLong(0)).incrementAndGet();
        metrics.put("operation_" + opName + "_count", operationCounts.get(opName).get());
    }
    
    public void recordStartTime(Instant startTime) {
        this.simulationStartTime = startTime;
        metrics.put("simulation_start_time", startTime.toEpochMilli());
    }
    
    public void recordEventScheduled(QuantumEvent event) {
        eventsScheduled++;
        metrics.put("events_scheduled", eventsScheduled);
    }
    
    public void recordStateTransition(QuantumState oldState, QuantumState newState) {
        stateUpdates++;
        metrics.put("state_updates", stateUpdates);
        metrics.put("last_state_update", System.currentTimeMillis());
        
        if (oldState != null && newState != null) {
            double distance = calculateStateDistance(oldState, newState);
            double fidelity = calculateStateFidelity(oldState, newState);
            metrics.put("last_state_distance", distance);
            metrics.put("current_fidelity", fidelity);
            
            cumulativeDistance += distance;
            distanceSamples++;
            cumulativeFidelity += fidelity;
            fidelitySamples++;
            metrics.put("average_state_distance", cumulativeDistance / distanceSamples);
            metrics.put("average_fidelity", cumulativeFidelity / fidelitySamples);
        }
        
        if (stateUpdates % 100 == 0) {
            recordStateSnapshot(newState);
        }
    }
    
    public void recordFidelity(double fidelity) {
        cumulativeFidelity += fidelity;
        fidelitySamples++;
        metrics.put("current_fidelity", fidelity);
        metrics.put("average_fidelity", cumulativeFidelity / fidelitySamples);
    }
    
    public Number getMetric(String name) {
        return metrics.get(name);
    }
    
    public Map<String, Number> getAllMetrics() {
        return new HashMap<>(metrics);
    }
    
    public List<MeasurementRecord> getMeasurementHistory() {
        return new ArrayList<>(measurementHistory);
    }
    
    public List<StateSnapshot> getStateHistory() {
        return new ArrayList<>(stateHistory);
    }
    
    public Map<String, Long> getOperationCounts() {
        Map<String, Long> counts = new HashMap<>();
        operationCounts.forEach((k, v) -> counts.put(k, v.get()));
        return counts;
    }
    
    public Map<String, Long> getErrorCounts() {
        Map<String, Long> counts = new HashMap<>();
        errorCounts.forEach((k, v) -> counts.put(k, v.get()));
        return counts;
    }
    
    public SimulationReport generateReport() {
        return new SimulationReport(
            getAllMetrics(),
            getOperationCounts(),
            getErrorCounts(),
            getMeasurementHistory(),
            simulationStartTime,
            simulationEndTime
        );
    }
    
    public void reset() {
        metrics.clear();
        measurementHistory.clear();
        stateHistory.clear();
        operationCounts.clear();
        errorCounts.clear();
        
        simulationStartTime = null;
        simulationEndTime = null;
        stateUpdates = 0;
        measurements = 0;
        eventsScheduled = 0;
        errors = 0;
        cumulativeFidelity = 0.0;
        fidelitySamples = 0;
        cumulativeDistance = 0.0;
        distanceSamples = 0;
        initialState = null;
        
        // Initialize default metrics
        metrics.put("state_updates", 0);
        metrics.put("measurements", 0);
        metrics.put("events_scheduled", 0);
        metrics.put("errors", 0);
        metrics.put("average_fidelity", 1.0);
    }
    
    private void recordStateSnapshot(QuantumState state) {
        StateSnapshot snapshot = new StateSnapshot(
            Instant.now(),
            state.getNumQubits(),
            state.getPurity(),
            state instanceof com.quantum.simulator.state.StateVector ? "StateVector" : "DensityMatrix"
        );
        stateHistory.add(snapshot);
    }
    
    private void updateMeasurementStats(MeasurementResult result) {
        double currentExp = result.getExpectationValue();
        double currentVar = result.getVariance();
        
        // Update running statistics
        Double prevAvgExp = (Double) metrics.get("average_expectation");
        Double prevAvgVar = (Double) metrics.get("average_variance");
        
        if (prevAvgExp == null) {
            metrics.put("average_expectation", currentExp);
            metrics.put("average_variance", currentVar);
            metrics.put("min_expectation", currentExp);
            metrics.put("max_expectation", currentExp);
            metrics.put("min_variance", currentVar);
            metrics.put("max_variance", currentVar);
        } else {
            long measurementCount = measurements;
            double newAvgExp = (prevAvgExp * (measurementCount - 1) + currentExp) / measurementCount;
            double newAvgVar = (prevAvgVar * (measurementCount - 1) + currentVar) / measurementCount;
            
            metrics.put("average_expectation", newAvgExp);
            metrics.put("average_variance", newAvgVar);
            metrics.put("min_expectation", Math.min((Double) metrics.get("min_expectation"), currentExp));
            metrics.put("max_expectation", Math.max((Double) metrics.get("max_expectation"), currentExp));
            metrics.put("min_variance", Math.min((Double) metrics.get("min_variance"), currentVar));
            metrics.put("max_variance", Math.max((Double) metrics.get("max_variance"), currentVar));
        }
    }
    
    private void calculateFinalStats(SimulationResult result) {
        if (simulationStartTime != null && simulationEndTime != null) {
            Duration duration = Duration.between(simulationStartTime, simulationEndTime);
            metrics.put("total_simulation_time_ms", duration.toMillis());
            
            long durationMillis = duration.toMillis();
            if (stateUpdates > 0 && durationMillis > 0) {
                metrics.put("state_updates_per_ms", stateUpdates / (double) durationMillis);
            }
        }
        
        // Calculate operation rates
        long totalOperations = operationCounts.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
        metrics.put("total_operations", totalOperations);
        
        if (totalOperations > 0 && simulationStartTime != null && simulationEndTime != null) {
            long durationMillis = Duration.between(simulationStartTime, simulationEndTime).toMillis();
            double opsPerSecond = durationMillis > 0
                ? totalOperations / (durationMillis / 1000.0)
                : 0.0;
            metrics.put("operations_per_second", opsPerSecond);
        }
        
        // Calculate error rate
        if (totalOperations > 0) {
            double errorRate = (double) errors / totalOperations;
            metrics.put("error_rate", errorRate);
        }
    }
    
    private double calculateStateFidelity(QuantumState state1, QuantumState state2) {
        if (state1.getNumQubits() != state2.getNumQubits()) {
            return 0.0;
        }
        if (state1 instanceof StateVector && state2 instanceof StateVector) {
            Complex overlap = ((StateVector) state1).innerProduct((StateVector) state2);
            return Math.min(1.0, overlap.absSquare());
        }
        return DensityMatrix.fidelity(toDensityMatrix(state1), toDensityMatrix(state2));
    }
    
    private double calculateStateDistance(QuantumState state1, QuantumState state2) {
        if (state1.getNumQubits() != state2.getNumQubits()) {
            return 1.0;
        }
        if (state1 instanceof StateVector && state2 instanceof StateVector) {
            double fidelity = calculateStateFidelity(state1, state2);
            return Math.sqrt(Math.max(0.0, 1.0 - fidelity));
        }
        return DensityMatrix.traceDistance(toDensityMatrix(state1), toDensityMatrix(state2));
    }
    
    private static DensityMatrix toDensityMatrix(QuantumState state) {
        if (state instanceof DensityMatrix) {
            return (DensityMatrix) state;
        }
        return ((StateVector) state).toDensityMatrix();
    }
    
    // Inner classes
    public static class MeasurementRecord {
        private final Instant timestamp;
        private final String observableName;
        private final double expectationValue;
        private final double variance;
        private final int shots;
        
        public MeasurementRecord(Instant timestamp, String observableName,
                               double expectationValue, double variance, int shots) {
            this.timestamp = timestamp;
            this.observableName = observableName;
            this.expectationValue = expectationValue;
            this.variance = variance;
            this.shots = shots;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public String getObservableName() { return observableName; }
        public double getExpectationValue() { return expectationValue; }
        public double getVariance() { return variance; }
        public int getShots() { return shots; }
        
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "MeasurementRecord[time=%s, obs='%s', exp=%.4f, var=%.4f, shots=%d]",
                timestamp, observableName, expectationValue, variance, shots
            );
        }
    }
    
    public static class StateSnapshot {
        private final Instant timestamp;
        private final int numQubits;
        private final double purity;
        private final String stateType;
        
        public StateSnapshot(Instant timestamp, int numQubits, double purity, String stateType) {
            this.timestamp = timestamp;
            this.numQubits = numQubits;
            this.purity = purity;
            this.stateType = stateType;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public int getNumQubits() { return numQubits; }
        public double getPurity() { return purity; }
        public String getStateType() { return stateType; }
        
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "StateSnapshot[time=%s, qubits=%d, purity=%.4f, type=%s]",
                timestamp, numQubits, purity, stateType
            );
        }
    }
    
    public static class SimulationReport {
        private final Map<String, Number> metrics;
        private final Map<String, Long> operationCounts;
        private final Map<String, Long> errorCounts;
        private final List<MeasurementRecord> measurementHistory;
        private final Instant startTime;
        private final Instant endTime;
        private final Instant reportTime;
        
        public SimulationReport(Map<String, Number> metrics, Map<String, Long> operationCounts,
                              Map<String, Long> errorCounts, List<MeasurementRecord> measurementHistory,
                              Instant startTime, Instant endTime) {
            this.metrics = new HashMap<>(metrics);
            this.operationCounts = new HashMap<>(operationCounts);
            this.errorCounts = new HashMap<>(errorCounts);
            this.measurementHistory = new ArrayList<>(measurementHistory);
            this.startTime = startTime;
            this.endTime = endTime;
            this.reportTime = Instant.now();
        }
        
        public Map<String, Number> getMetrics() { return Collections.unmodifiableMap(metrics); }
        public Map<String, Long> getOperationCounts() { return Collections.unmodifiableMap(operationCounts); }
        public Map<String, Long> getErrorCounts() { return Collections.unmodifiableMap(errorCounts); }
        public List<MeasurementRecord> getMeasurementHistory() { return Collections.unmodifiableList(measurementHistory); }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public Instant getReportTime() { return reportTime; }
        
        public Duration getDuration() {
            if (startTime != null && endTime != null) {
                return Duration.between(startTime, endTime);
            }
            return Duration.ZERO;
        }
        
        public double getThroughput() {
            long totalOps = operationCounts.values().stream().mapToLong(Long::longValue).sum();
            Duration duration = getDuration();
            if (duration.isZero()) return 0.0;
            return totalOps / (duration.toMillis() / 1000.0);
        }
        
        public String toSummaryString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Simulation Report\n");
            sb.append("================\n");
            
            if (startTime != null && endTime != null) {
                sb.append(String.format(Locale.ROOT, "Duration: %s\n", getDuration()));
            }
            
            sb.append(String.format(Locale.ROOT, "Qubits: %s\n", metrics.getOrDefault("num_qubits", 0)));
            sb.append(String.format(Locale.ROOT, "State Updates: %s\n", metrics.getOrDefault("state_updates", 0)));
            sb.append(String.format(Locale.ROOT, "Measurements: %s\n", metrics.getOrDefault("measurements", 0)));
            sb.append(String.format(Locale.ROOT, "Operations: %s\n", metrics.getOrDefault("total_operations", 0)));
            sb.append(String.format(Locale.ROOT, "Errors: %s\n", metrics.getOrDefault("errors", 0)));
            
            if (metrics.containsKey("error_rate")) {
                double errorRate = metrics.get("error_rate").doubleValue();
                sb.append(String.format(Locale.ROOT, "Error Rate: %.4f%%\n", errorRate * 100));
            }
            
            if (metrics.containsKey("average_fidelity")) {
                double fidelity = metrics.get("average_fidelity").doubleValue();
                sb.append(String.format(Locale.ROOT, "Average Fidelity: %.6f\n", fidelity));
            }
            
            if (metrics.containsKey("operations_per_second")) {
                double opsPerSec = metrics.get("operations_per_second").doubleValue();
                sb.append(String.format(Locale.ROOT, "Throughput: %.2f ops/sec\n", opsPerSec));
            }
            
            return sb.toString();
        }
        
        @Override
        public String toString() {
            return toSummaryString();
        }
    }

	public void recordMeasurement(MeasurementResult result) {
        measurements++;
        metrics.put("measurements", measurements);
        metrics.put("last_measurement", System.currentTimeMillis());
        
        MeasurementRecord record = new MeasurementRecord(
            Instant.now(),
            result.getObservableName(),
            result.getExpectationValue(),
            result.getVariance(),
            result.getTotalShots()
        );
        measurementHistory.add(record);
        
        // Update measurement statistics
        updateMeasurementStats(result);
    }
     
    @Override
    public void onMeasurement(MeasurementResult result) {
        // Delegate to recordMeasurement method
        recordMeasurement(result);
    }
     
}
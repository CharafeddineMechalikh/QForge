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

/**
 * Observer that records counts, timings, fidelity statistics, and measurement
 * statistics as name and value pairs.
 *
 * <p>Values are read with {@link #getMetric(String)} or {@link #getAllMetrics()},
 * and {@link #generateReport()} copies them into a {@link SimulationReport}.
 * The following keys are produced:</p>
 *
 * <ul>
 *   <li>{@code state_updates} (long): state update callbacks plus
 *       {@link #recordStateTransition(QuantumState, QuantumState)} calls.</li>
 *   <li>{@code last_state_update} (long): {@link System#currentTimeMillis()} at
 *       the most recent state update.</li>
 *   <li>{@code num_qubits} (int): qubit count, taken from the first state update
 *       or from the context when {@link #onInitialized(SimulationContext)} runs.
 *       The initialization value overwrites an earlier value.</li>
 *   <li>{@code operation_<name>_count} (long): count for each operation name seen
 *       through a state update event or {@link #recordOperation(QuantumOperation)}.</li>
 *   <li>{@code events_scheduled} (long): scheduled event callbacks plus
 *       {@link #recordEventScheduled(QuantumEvent)} calls.</li>
 *   <li>{@code simulation_start_time} (long): epoch milliseconds, set by the
 *       first scheduled event, by initialization, or by
 *       {@link #recordStartTime(Instant)}.</li>
 *   <li>{@code simulation_end_time} (long): epoch milliseconds when
 *       {@link #onCompleted(SimulationResult)} runs.</li>
 *   <li>{@code simulation_duration_ms} and {@code simulation_duration_seconds}
 *       (long): end minus start, or {@code 0} when no start time was recorded.</li>
 *   <li>{@code errors} (long), {@code error_<Type>_count} (long), and
 *       {@code last_error_time} (long): error total, counts per simple throwable
 *       class name ({@code Unknown} when the throwable is {@code null}), and the
 *       time of the most recent error.</li>
 *   <li>{@code backend} (int): ordinal of the configured
 *       {@link com.quantum.simulator.config.SimulationBackend}.</li>
 *   <li>{@code measurements} (long) and {@code last_measurement} (long):
 *       measurement count and the time of the most recent measurement.</li>
 *   <li>{@code last_state_distance}, {@code current_fidelity},
 *       {@code average_state_distance}, and {@code average_fidelity} (double):
 *       distance and fidelity of recorded state transitions and their running
 *       averages.</li>
 *   <li>{@code average_expectation}, {@code average_variance},
 *       {@code min_expectation}, {@code max_expectation}, {@code min_variance}, and
 *       {@code max_variance} (double): running measurement statistics.</li>
 *   <li>{@code total_simulation_time_ms}, {@code state_updates_per_ms},
 *       {@code total_operations}, {@code operations_per_second}, and
 *       {@code error_rate} (number): final statistics written by
 *       {@link #onCompleted(SimulationResult)}. The error rate is the ratio
 *       {@code errors / total_operations}.</li>
 * </ul>
 *
 * <p>After construction and after {@link #reset()}, the map contains
 * {@code state_updates}, {@code measurements}, {@code events_scheduled}, and
 * {@code errors} set to {@code 0}, and {@code average_fidelity} set to
 * {@code 1.0}.</p>
 */
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
    
    /**
     * Creates a collector with an empty metric map, empty histories, zeroed counters,
     * and the default metrics installed by {@link #reset()}.
     */
    public MetricsCollector() {
        this.metrics = new ConcurrentHashMap<>();
        this.measurementHistory = Collections.synchronizedList(new ArrayList<>());
        this.stateHistory = Collections.synchronizedList(new ArrayList<>());
        this.operationCounts = new ConcurrentHashMap<>();
        this.errorCounts = new ConcurrentHashMap<>();
        reset();
    }
     
    /**
     * Records a state update and, when the event carries an operation, increments the
     * operation count. A state snapshot is stored every 100 state updates.
     *
     * @param state the state after the update; its qubit count is stored in
     *              {@code num_qubits} only when that key is absent
     * @param event the event that produced the update, or {@code null}
     */
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
    
    
    /**
     * Records a scheduled event and sets {@code simulation_start_time} when no start
     * time has been set yet.
     *
     * @param event the scheduled event
     */
    @Override
    public void onEventScheduled(QuantumEvent event) {
        eventsScheduled++;
        metrics.put("events_scheduled", eventsScheduled);
        if (simulationStartTime == null) {
            simulationStartTime = Instant.now();
            metrics.put("simulation_start_time", simulationStartTime.toEpochMilli());
        }
    }
    
    /**
     * Records an error, the count for its throwable class, and the time of the error.
     *
     * @param errorMessage the error description; it is not stored as a metric
     * @param error the throwable used to derive the error type; when {@code null} the
     *              type is {@code Unknown}
     */
    @Override
    public void onError(String errorMessage, Throwable error) {
        errors++;
        metrics.put("errors", errors);
        
        String errorType = error != null ? error.getClass().getSimpleName() : "Unknown";
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        metrics.put("error_" + errorType + "_count", errorCounts.get(errorType).get());
        
        metrics.put("last_error_time", System.currentTimeMillis()); 
    }
     
    /**
     * Records the start time, qubit count, and backend ordinal from the context.
     * Nothing is recorded when the context is {@code null}.
     *
     * @param context the simulation context holding the configuration and current
     *                state
     */
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
    
    /**
     * Records the end time, the duration, and the final statistics for a completed
     * simulation.
     *
     * @param result the completed simulation result
     */
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
    
    /**
     * Clears all recorded data and restores the default metrics.
     */
    @Override
    public void onReset() {
        reset();
    }
    
    /**
     * Stores an arbitrary metric, replacing any previous value for the same name.
     *
     * @param name the metric key
     * @param value the metric value
     */
    public void recordMetric(String name, Number value) {
        metrics.put(name, value);
    }
    
    /**
     * Increments the count for an operation name and updates its metric key.
     *
     * @param operation the operation to count
     */
    public void recordOperation(QuantumOperation operation) {
        String opName = operation.getName();
        operationCounts.computeIfAbsent(opName, k -> new AtomicLong(0)).incrementAndGet();
        metrics.put("operation_" + opName + "_count", operationCounts.get(opName).get());
    }
    
    /**
     * Sets the simulation start time used by later duration calculations.
     *
     * @param startTime the start time
     */
    public void recordStartTime(Instant startTime) {
        this.simulationStartTime = startTime;
        metrics.put("simulation_start_time", startTime.toEpochMilli());
    }
    
    /**
     * Increments the scheduled event count without changing the start time.
     *
     * @param event the event being scheduled
     */
    public void recordEventScheduled(QuantumEvent event) {
        eventsScheduled++;
        metrics.put("events_scheduled", eventsScheduled);
    }
    
    /**
     * Records a transition between two states and updates the distance and fidelity
     * metrics.
     *
     * <p>When both states are non-null, the fidelity is the squared magnitude of the
     * state vector inner product (clamped to at most {@code 1.0}) for two state
     * vectors, or the density matrix fidelity otherwise. The distance is
     * {@code sqrt(max(0, 1 - fidelity))} for state vectors and the trace distance for
     * density matrices. States with different qubit counts produce a fidelity of
     * {@code 0.0} and a distance of {@code 1.0}. A snapshot is stored every 100 state
     * updates.</p>
     *
     * @param oldState the state before the transition, or {@code null}
     * @param newState the state after the transition, or {@code null}
     */
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
    
    /**
     * Adds a fidelity sample and updates the current and average fidelity metrics.
     *
     * @param fidelity the fidelity sample
     */
    public void recordFidelity(double fidelity) {
        cumulativeFidelity += fidelity;
        fidelitySamples++;
        metrics.put("current_fidelity", fidelity);
        metrics.put("average_fidelity", cumulativeFidelity / fidelitySamples);
    }
    
    /**
     * Returns a metric value.
     *
     * @param name the metric key
     * @return the recorded value, or {@code null} when the key is absent
     */
    public Number getMetric(String name) {
        return metrics.get(name);
    }
    
    /**
     * Returns a copy of all metrics.
     *
     * @return a new map of metric names to values
     */
    public Map<String, Number> getAllMetrics() {
        return new HashMap<>(metrics);
    }
    
    /**
     * Returns the recorded measurements.
     *
     * @return a new list containing the measurement records
     */
    public List<MeasurementRecord> getMeasurementHistory() {
        return new ArrayList<>(measurementHistory);
    }
    
    /**
     * Returns the recorded state snapshots.
     *
     * @return a new list containing the state snapshots
     */
    public List<StateSnapshot> getStateHistory() {
        return new ArrayList<>(stateHistory);
    }
    
    /**
     * Returns the per operation counts.
     *
     * @return a new map of operation name to count
     */
    public Map<String, Long> getOperationCounts() {
        Map<String, Long> counts = new HashMap<>();
        operationCounts.forEach((k, v) -> counts.put(k, v.get()));
        return counts;
    }
    
    /**
     * Returns the per error type counts.
     *
     * @return a new map of throwable simple class name to count
     */
    public Map<String, Long> getErrorCounts() {
        Map<String, Long> counts = new HashMap<>();
        errorCounts.forEach((k, v) -> counts.put(k, v.get()));
        return counts;
    }
    
    /**
     * Creates a report with copies of the current metrics, operation counts, error
     * counts, and measurement history.
     *
     * @return a new report
     */
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
    
    /**
     * Clears metrics, histories, operation and error counts, timers, and accumulated
     * samples, then installs the default metrics.
     */
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
    
    /**
     * Appends a snapshot with the current time, qubit count, purity, and state type.
     *
     * @param state the state to summarize
     */
    private void recordStateSnapshot(QuantumState state) {
        StateSnapshot snapshot = new StateSnapshot(
            Instant.now(),
            state.getNumQubits(),
            state.getPurity(),
            state instanceof com.quantum.simulator.state.StateVector ? "StateVector" : "DensityMatrix"
        );
        stateHistory.add(snapshot);
    }
    
    /**
     * Updates the running expectation and variance statistics using the measurement
     * count as the sample count. The first sample also initializes the minimum and
     * maximum values.
     *
     * @param result the measurement providing the expectation value and variance
     */
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
    
    /**
     * Writes total operations, operations per second, state updates per millisecond,
     * and the error rate.
     *
     * <p>The supplied result is not read; all values come from the collector state.
     * The error rate is {@code errors / total_operations} and is only written when at
     * least one operation was counted. The rate values that need a duration are only
     * written when a start and an end time are available, and
     * {@code state_updates_per_ms} also requires at least one state update.</p>
     *
     * @param result the completed simulation result
     */
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
    
    /**
     * Computes the fidelity between two states.
     *
     * @param state1 the first state
     * @param state2 the second state
     * @return {@code 0.0} when the qubit counts differ, the squared inner product
     *         magnitude for two state vectors (clamped to at most {@code 1.0}), or the
     *         density matrix fidelity otherwise
     */
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
    
    /**
     * Computes a distance between two states.
     *
     * @param state1 the first state
     * @param state2 the second state
     * @return {@code 1.0} when the qubit counts differ, the square root of
     *         {@code max(0, 1 - fidelity)} for two state vectors, or the trace
     *         distance otherwise
     */
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
    
    /**
     * Returns a state as a density matrix, converting state vectors when needed.
     *
     * @param state the state to convert
     * @return the state itself when it already is a {@link DensityMatrix}, otherwise
     *         its density matrix form
     */
    private static DensityMatrix toDensityMatrix(QuantumState state) {
        if (state instanceof DensityMatrix) {
            return (DensityMatrix) state;
        }
        return ((StateVector) state).toDensityMatrix();
    }
    
    // Inner classes
    /**
     * Record of a single measurement.
     *
     * <p>The record stores the values reported by the {@link MeasurementResult} when
     * {@link MetricsCollector#recordMeasurement(MeasurementResult)} runs and does not
     * reference the result object.</p>
     */
    public static class MeasurementRecord {
        private final Instant timestamp;
        private final String observableName;
        private final double expectationValue;
        private final double variance;
        private final int shots;
        
        /**
         * Creates a measurement record.
         *
         * @param timestamp the measurement time
         * @param observableName the observable name
         * @param expectationValue the expectation value
         * @param variance the variance
         * @param shots the number of shots
         */
        public MeasurementRecord(Instant timestamp, String observableName,
                               double expectationValue, double variance, int shots) {
            this.timestamp = timestamp;
            this.observableName = observableName;
            this.expectationValue = expectationValue;
            this.variance = variance;
            this.shots = shots;
        }
        
        /**
         * Returns the measurement time.
         *
         * @return the timestamp
         */
        public Instant getTimestamp() { return timestamp; }
        /**
         * Returns the observable name.
         *
         * @return the observable name
         */
        public String getObservableName() { return observableName; }
        /**
         * Returns the expectation value.
         *
         * @return the expectation value
         */
        public double getExpectationValue() { return expectationValue; }
        /**
         * Returns the variance.
         *
         * @return the variance
         */
        public double getVariance() { return variance; }
        /**
         * Returns the shot count.
         *
         * @return the number of shots
         */
        public int getShots() { return shots; }
        
        /**
         * Returns a formatted description of the record.
         *
         * @return a string with the timestamp, observable, expectation value, variance,
         *         and shot count
         */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "MeasurementRecord[time=%s, obs='%s', exp=%.4f, var=%.4f, shots=%d]",
                timestamp, observableName, expectationValue, variance, shots
            );
        }
    }
    
    /**
     * Point in time summary of a quantum state, holding its qubit count, purity, and
     * state type name ({@code StateVector} or {@code DensityMatrix}).
     */
    public static class StateSnapshot {
        private final Instant timestamp;
        private final int numQubits;
        private final double purity;
        private final String stateType;
        
        /**
         * Creates a state snapshot.
         *
         * @param timestamp the capture time
         * @param numQubits the number of qubits
         * @param purity the state purity
         * @param stateType the state type name
         */
        public StateSnapshot(Instant timestamp, int numQubits, double purity, String stateType) {
            this.timestamp = timestamp;
            this.numQubits = numQubits;
            this.purity = purity;
            this.stateType = stateType;
        }
        
        /**
         * Returns the capture time.
         *
         * @return the timestamp
         */
        public Instant getTimestamp() { return timestamp; }
        /**
         * Returns the number of qubits.
         *
         * @return the qubit count
         */
        public int getNumQubits() { return numQubits; }
        /**
         * Returns the state purity.
         *
         * @return the purity
         */
        public double getPurity() { return purity; }
        /**
         * Returns the state type name.
         *
         * @return the state type name
         */
        public String getStateType() { return stateType; }
        
        /**
         * Returns a formatted description of the snapshot.
         *
         * @return a string with the timestamp, qubit count, purity, and state type
         */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "StateSnapshot[time=%s, qubits=%d, purity=%.4f, type=%s]",
                timestamp, numQubits, purity, stateType
            );
        }
    }
    
    /**
     * Immutable snapshot of collected metrics and histories, created by
     * {@link MetricsCollector#generateReport()}.
     */
    public static class SimulationReport {
        private final Map<String, Number> metrics;
        private final Map<String, Long> operationCounts;
        private final Map<String, Long> errorCounts;
        private final List<MeasurementRecord> measurementHistory;
        private final Instant startTime;
        private final Instant endTime;
        private final Instant reportTime;
        
        /**
         * Creates a report from copies of the supplied data.
         *
         * @param metrics the metrics to copy
         * @param operationCounts the operation counts to copy
         * @param errorCounts the error counts to copy
         * @param measurementHistory the measurements to copy
         * @param startTime the simulation start time, or {@code null}
         * @param endTime the simulation end time, or {@code null}
         */
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
        
        /**
         * Returns the metrics.
         *
         * @return an unmodifiable map of metric names to values
         */
        public Map<String, Number> getMetrics() { return Collections.unmodifiableMap(metrics); }
        /**
         * Returns the operation counts.
         *
         * @return an unmodifiable map of operation name to count
         */
        public Map<String, Long> getOperationCounts() { return Collections.unmodifiableMap(operationCounts); }
        /**
         * Returns the error counts.
         *
         * @return an unmodifiable map of error type to count
         */
        public Map<String, Long> getErrorCounts() { return Collections.unmodifiableMap(errorCounts); }
        /**
         * Returns the measurements.
         *
         * @return an unmodifiable list of measurement records
         */
        public List<MeasurementRecord> getMeasurementHistory() { return Collections.unmodifiableList(measurementHistory); }
        /**
         * Returns the simulation start time.
         *
         * @return the start time, or {@code null} when it was not recorded
         */
        public Instant getStartTime() { return startTime; }
        /**
         * Returns the simulation end time.
         *
         * @return the end time, or {@code null} when it was not recorded
         */
        public Instant getEndTime() { return endTime; }
        /**
         * Returns the time this report was created.
         *
         * @return the report creation time
         */
        public Instant getReportTime() { return reportTime; }
        
        /**
         * Returns the simulation duration.
         *
         * @return the time between the start and end times, or {@link Duration#ZERO} when
         *         either time is {@code null}
         */
        public Duration getDuration() {
            if (startTime != null && endTime != null) {
                return Duration.between(startTime, endTime);
            }
            return Duration.ZERO;
        }
        
        /**
         * Returns the operation throughput.
         *
         * @return the sum of the operation counts divided by the duration in seconds, or
         *         {@code 0.0} when the duration is zero
         */
        public double getThroughput() {
            long totalOps = operationCounts.values().stream().mapToLong(Long::longValue).sum();
            Duration duration = getDuration();
            if (duration.isZero()) return 0.0;
            return totalOps / (duration.toMillis() / 1000.0);
        }
        
        /**
         * Returns a multi line summary of the report.
         *
         * <p>The summary includes the duration, qubit count, state updates, measurements,
         * operations, errors, and, when present, the error rate as a percentage, the
         * average fidelity, and the throughput in operations per second.</p>
         *
         * @return the summary text
         */
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
        
        /**
         * Returns the same text as {@link #toSummaryString()}.
         *
         * @return the summary text
         */
        @Override
        public String toString() {
            return toSummaryString();
        }
    }

	/**
	 * Records a measurement result, appends a {@link MeasurementRecord}, and updates
	 * the measurement statistics.
	 *
	 * @param result the measurement result to record
	 */
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
     
    /**
     * Delegates to {@link #recordMeasurement(MeasurementResult)}.
     *
     * @param result the measurement result to record
     */
    @Override
    public void onMeasurement(MeasurementResult result) {
        // Delegate to recordMeasurement method
        recordMeasurement(result);
    }
     
}
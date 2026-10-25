package com.mechalikh.qforge.core;

import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.strategy.SimulationStrategy;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.noise.ErrorManager;
import com.mechalikh.qforge.noise.NoiseModel;
import com.mechalikh.qforge.noise.NoiseProfile;
import com.mechalikh.qforge.observer.MetricsCollector;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mutable state and services shared by one simulation.
 *
 * <p>A context owns the configuration, the qubit list, the current quantum state, the
 * measurement list, the error manager, the metrics collector, and a free form data map. It
 * also holds the {@link SimulationStrategy} that determines how states are created and
 * transformed. The current state is null until {@link #initializeState()} is called.
 *
 * <p>{@link #applyOperation(QuantumOperation)} is the single entry point for changing the
 * state: gates are applied by the strategy and followed by configured noise, noise models
 * are applied directly, and measurements collapse the state and append a
 * {@link MeasurementResult}. Each successful operation increments the step counter.
 *
 * <p>Stop conditions are exposed through {@link #shouldStop()}, which combines an explicit
 * stop request with the configured maximum step count. Observer and plugin notifications
 * are issued by {@link QuantumSimulator}; this class only records and exposes state. The
 * class makes no thread safety guarantees.
 */
public class SimulationContext {
    private final SimulationConfig config;
    private SimulationStrategy strategy;
    private QuantumState currentState;
    private final List<Qubit> qubits;
    private final ErrorManager errorManager;
    private final MetricsCollector metricsCollector;
    private final List<MeasurementResult> measurements;
    private final Map<String, Object> contextData;
    private Instant simulationStartTime;
    private long stepsExecuted;
    private boolean stopRequested;
    
    /**
     * Creates a context for the given configuration and one {@link Qubit} per configured
     * qubit, named {@code q0}, {@code q1}, and so on.
     *
     * <p>No quantum state exists yet; a strategy must be installed with
     * {@link #setStrategy(SimulationStrategy)} and {@link #initializeState()} must be called
     * before operations can be applied. The error manager, metrics collector and collections
     * are created empty.
     *
     * @param config configuration used by this context; it is stored by reference and not
     *        copied
     */
    public SimulationContext(SimulationConfig config) {
        this.config = config;
        this.qubits = new ArrayList<>();
        this.errorManager = new ErrorManager();
        this.metricsCollector = new MetricsCollector();
        this.measurements = new CopyOnWriteArrayList<>();
        this.contextData = new HashMap<>();
        this.stepsExecuted = 0;
        this.stopRequested = false;
        initializeQubits();
    }
    
    /**
     * Creates the initial quantum state through the installed strategy and records the
     * simulation start time.
     *
     * <p>The strategy's {@code createInitialState} method receives the configured number of
     * qubits, so the concrete state type depends on the backend. The start time is stored in
     * the context and in the metrics collector.
     *
     * @throws SimulationError if no strategy has been installed
     */
    public void initializeState() throws SimulationError {
        if (strategy == null) {
            throw new SimulationError("Simulation strategy not set");
        }
        
        this.currentState = strategy.createInitialState(config.getNumQubits());
        this.simulationStartTime = Instant.now();
        this.metricsCollector.recordStartTime(simulationStartTime);
    }
    
    /**
     * Replaces the current state and records the transition in the metrics collector.
     *
     * <p>The state is stored without copying and without null checking.
     *
     * @param newState state to install as the current state
     */
    public void updateState(QuantumState newState) {
        QuantumState oldState = this.currentState;
        this.currentState = newState;
        metricsCollector.recordStateTransition(oldState, newState);
    }
    /**
     * Applies one operation to the current state and records it.
     *
     * <p>The operation type selects the path:
     * <ul>
     *   <li>{@link QuantumGate}: applied through
     *       {@link SimulationStrategy#applyGate}, then every noise model of the configured
     *       {@link NoiseProfile} is applied to the resulting state.</li>
     *   <li>{@link NoiseModel}: applied through {@link SimulationStrategy#applyNoise}
     *       without any additional configured noise.</li>
     *   <li>{@link com.mechalikh.qforge.measurement.MeasurementOperator}: measured and
     *       collapsed, the outcome is appended to the measurement list, and the collapsed
     *       state becomes the current state.</li>
     * </ul>
     *
     * <p>For gates, configured noise models are applied in iteration order. A noise model
     * without target qubits is applied once per qubit in this context, each time as a
     * separate single qubit application.
     *
     * <p>On success the step counter is incremented, the {@code steps_executed} metric is
     * updated, and the operation is recorded in the metrics collector. If a step fails, the
     * failure surfaces before the new state is installed and before the step counter is
     * incremented.
     *
     * @param operation operation to apply; must be a gate, a noise model, or a measurement
     *        operator
     * @throws SimulationError if the state is not initialized, if the operation type is not
     *         supported, or if configured noise cannot be applied
     * @throws IllegalArgumentException if the strategy rejects the state representation, for
     *         example when a state vector strategy receives a density matrix; this exception
     *         is not wrapped here
     */
    public void applyOperation(QuantumOperation operation) throws SimulationError {
        if (currentState == null) {
            throw new SimulationError("Quantum state not initialized");
        }
        
        QuantumState newState;
        
        if (operation instanceof QuantumGate) {
            QuantumGate gate = (QuantumGate) operation;
            
            // Use the strategy to apply the gate
            newState = strategy.applyGate(currentState, gate, gate.getTargetQubits());
            newState = applyConfiguredNoise(newState);
            
        } else if (operation instanceof com.mechalikh.qforge.noise.NoiseModel) {
            com.mechalikh.qforge.noise.NoiseModel noise = 
                (com.mechalikh.qforge.noise.NoiseModel) operation;
            
            // Use the strategy to apply noise
            newState = strategy.applyNoise(currentState, noise, noise.getTargetQubits());
            
        } else if (operation instanceof com.mechalikh.qforge.measurement.MeasurementOperator) {
            com.mechalikh.qforge.measurement.MeasurementOperator measurement = 
                (com.mechalikh.qforge.measurement.MeasurementOperator) operation;
            
            com.mechalikh.qforge.measurement.MeasurementOperator.MeasurementOutcome outcome =
                measurement.measureAndCollapse(currentState);
            addMeasurementResult(outcome.getResult());
            newState = outcome.getCollapsedState();
            
        } else {
            throw new SimulationError("Unsupported operation type: " + 
                operation.getClass().getName());
        }
        
        updateState(newState);
        stepsExecuted++;
        metricsCollector.recordMetric("steps_executed", stepsExecuted);
        
        metricsCollector.recordOperation(operation);
    }
    private QuantumState applyConfiguredNoise(QuantumState state) throws SimulationError {
        NoiseProfile profile = config.getNoiseProfile();
        if (profile == null || profile.isEmpty()) {
            return state;
        }
        
        QuantumState noisyState = state;
        try {
            for (NoiseModel noise : profile.getAllNoiseModels()) {
                Qubit[] targets = noise.getTargetQubits();
                if (targets.length == 0) {
                    for (Qubit qubit : qubits) {
                        Qubit[] singleTarget = new Qubit[]{qubit};
                        noisyState = strategy.applyNoise(
                            noisyState, noise.withTargetQubits(singleTarget), singleTarget);
                    }
                } else {
                    noisyState = strategy.applyNoise(noisyState, noise, targets);
                }
            }
        } catch (Exception e) {
            throw new SimulationError("Failed to apply configured noise: " + e.getMessage(), e);
        }
        
        return noisyState;
    }
    
    /**
     * Appends a measurement result to the context and records it in the metrics collector.
     *
     * @param result measurement to store; it is added without copying
     */
    public void addMeasurementResult(MeasurementResult result) {
        measurements.add(result);
        metricsCollector.recordMeasurement(result);
    }
    
    /**
     * Stores a value in the free form context data map.
     *
     * <p>The map is cleared by {@link #reset()}.
     *
     * @param key data key; may be null
     * @param value value to store; may be null
     */
    public void putData(String key, Object value) {
        contextData.put(key, value);
    }
    
    /**
     * Reads a value from the free form context data map.
     *
     * @param key data key
     * @return the stored value, or null when the key is absent or mapped to null
     */
    public Object getData(String key) {
        return contextData.get(key);
    }
    
    /**
     * Requests that the current run stop. The next call to {@link #shouldStop()} returns
     * true, and {@link QuantumSimulator#run()} checks that method after each event.
     */
    public void requestStop() {
        this.stopRequested = true;
    }
    
    /**
     * Reports whether the run loop should stop.
     *
     * <p>A stop is reported when {@link #requestStop()} has been called or when a positive
     * maximum step count is configured and the number of executed steps has reached it. A
     * maximum step count of 0 or less means no step limit.
     *
     * @return true if a stop was requested or the configured step limit is reached
     */
    public boolean shouldStop() {
        return stopRequested || 
               (config.getMaxSteps() > 0 && stepsExecuted >= config.getMaxSteps());
    }
    
    /**
     * Returns the context to its initial condition.
     *
     * <p>When a current state exists, it is replaced by a fresh state from the strategy.
     * Measurements and custom data are cleared, the step counter and stop flag are reset,
     * the start time is set to the current instant, and the metrics collector is reset. The
     * qubit list, the strategy and the configuration are unchanged, and observers are not
     * notified by this method.
     */
    public void reset() {
        if (currentState != null) {
            currentState = strategy.createInitialState(config.getNumQubits());
        }
        measurements.clear();
        contextData.clear();
        stepsExecuted = 0;
        stopRequested = false;
        simulationStartTime = Instant.now();
        metricsCollector.reset();
    }
    
    /**
     * Returns the configuration this context was created with.
     *
     * @return the configuration instance, stored by reference
     */
    public SimulationConfig getConfig() {
        return config;
    }
    
    /**
     * Returns the strategy used to create and transform states.
     *
     * @return the installed strategy, or null when none has been set
     */
    public SimulationStrategy getStrategy() {
        return strategy;
    }
    
    /**
     * Installs the strategy used for state creation, gate application, noise application and
     * reset.
     *
     * <p>The strategy must be set before {@link #initializeState()} is called.
     *
     * @param strategy strategy to install; it is stored without null checking
     */
    public void setStrategy(SimulationStrategy strategy) {
        this.strategy = strategy;
    }
    
    /**
     * Returns the current quantum state.
     *
     * @return the current state, or null before {@link #initializeState()} and after a reset
     *         that had no state to recreate
     */
    public QuantumState getCurrentState() {
        return currentState;
    }
    
    /**
     * Returns a copy of the qubit list.
     *
     * @return a new list with the context qubits in index order
     */
    public List<Qubit> getQubits() {
        return new ArrayList<>(qubits);
    }
    
    /**
     * Returns the qubit with the given index.
     *
     * @param index zero based qubit index
     * @return the qubit at that index
     * @throws IndexOutOfBoundsException if the index is negative or not less than the number
     *         of configured qubits
     */
    public Qubit getQubit(int index) {
        if (index < 0 || index >= qubits.size()) {
            throw new IndexOutOfBoundsException("Qubit index out of bounds: " + index);
        }
        return qubits.get(index);
    }
    
    /**
     * Returns the error manager of this context.
     *
     * @return the error manager created with the context
     */
    public ErrorManager getErrorManager() {
        return errorManager;
    }
    
    /**
     * Returns the metrics collector that records state changes, measurements, operations and
     * step counts for this context.
     *
     * @return the metrics collector created with the context
     */
    public MetricsCollector getMetrics() {
        return metricsCollector;
    }
    
    /**
     * Returns a copy of the measurements recorded in this context.
     *
     * @return a new list containing the measurements in recording order
     */
    public List<MeasurementResult> getMeasurements() {
        return new ArrayList<>(measurements);
    }
    
    /**
     * Returns the number of operations successfully applied since creation or the last
     * {@link #reset()}.
     *
     * @return the executed step count
     */
    public long getStepsExecuted() {
        return stepsExecuted;
    }
    
    /**
     * Returns the start time recorded by {@link #initializeState()} or {@link #reset()}.
     *
     * @return the start instant, or null when neither method has been called
     */
    public Instant getSimulationStartTime() {
        return simulationStartTime;
    }
    
    /**
     * Returns the time elapsed since the recorded start time.
     *
     * <p>Requires that {@link #initializeState()} or {@link #reset()} has been called,
     * because the start time is null before that.
     *
     * @return elapsed milliseconds between the start time and the current time
     */
    public long getElapsedTimeMillis() {
        return Instant.now().toEpochMilli() - simulationStartTime.toEpochMilli();
    }
    
    private void initializeQubits() {
        qubits.clear();
        for (int i = 0; i < config.getNumQubits(); i++) {
            qubits.add(new Qubit(i, "q" + i));
        }
    }
}
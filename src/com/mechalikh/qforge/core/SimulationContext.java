package com.mechalikh.qforge.core;

import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.strategy.SimulationStrategy;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.noise.DeviceNoiseModel;
import com.mechalikh.qforge.noise.ErrorManager;
import com.mechalikh.qforge.noise.NoiseModel;
import com.mechalikh.qforge.noise.NoiseProfile;
import com.mechalikh.qforge.observer.MetricsCollector;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.operations.ConditionalOperation;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.classical.ClassicalRegister;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<String, ClassicalRegister> registers;
    private Instant simulationStartTime;
    private Instant lastOperationTime;
    private Instant pendingEventTime;
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
        this.registers = new LinkedHashMap<>();
        this.stepsExecuted = 0;
        this.stopRequested = false;
        initializeQubits();
        initializeRegisters();
    }
    
    private void initializeRegisters() {
        for (Map.Entry<String, Integer> register : config.getClassicalRegisters().entrySet()) {
            registers.put(register.getKey(), new ClassicalRegister(register.getKey(),
                register.getValue()));
        }
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
        this.lastOperationTime = simulationStartTime;
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
        
        QuantumOperation effective = resolveConditional(operation);
        if (effective == null) {
            return;
        }
        
        Instant operationTime = pendingEventTime != null ? pendingEventTime : Instant.now();
        pendingEventTime = null;
        double elapsedNanos = elapsedNanos(operationTime);
        
        QuantumState newState;
        
        if (effective instanceof QuantumGate) {
            QuantumGate gate = (QuantumGate) effective;
            
            newState = applyIdleNoise(currentState, gate.getTargetQubits(), elapsedNanos);
            newState = strategy.applyGate(newState, gate, gate.getTargetQubits());
            newState = applyConfiguredNoise(newState);
            newState = applyCrosstalk(newState, gate);
            
        } else if (effective instanceof com.mechalikh.qforge.noise.NoiseModel) {
            com.mechalikh.qforge.noise.NoiseModel noise = 
                (com.mechalikh.qforge.noise.NoiseModel) effective;
            
            newState = applyIdleNoise(currentState, noise.getTargetQubits(), elapsedNanos);
            newState = strategy.applyNoise(newState, noise, noise.getTargetQubits());
            
        } else if (effective instanceof com.mechalikh.qforge.measurement.MeasurementOperator) {
            com.mechalikh.qforge.measurement.MeasurementOperator measurement = 
                (com.mechalikh.qforge.measurement.MeasurementOperator) effective;
            
            newState = applyIdleNoise(currentState, measurement.getTargetQubits(), elapsedNanos);
            com.mechalikh.qforge.measurement.MeasurementOperator.MeasurementOutcome outcome =
                measurement.measureAndCollapse(newState, config.getDeviceNoise().getReadout());
            addMeasurementResult(outcome.getResult());
            recordClassicalOutcome(measurement, outcome);
            newState = outcome.getCollapsedState();
            
        } else {
            throw new SimulationError("Unsupported operation type: " + 
                effective.getClass().getName());
        }
        
        lastOperationTime = operationTime;
        updateState(newState);
        stepsExecuted++;
        metricsCollector.recordMetric("steps_executed", stepsExecuted);
        
        metricsCollector.recordOperation(operation);
    }
    
    /**
     * Resolves a conditional operation and returns the operation that should run.
     *
     * @param operation operation to resolve
     * @return the guarded operation when the condition holds, the input operation when it is
     *         not conditional, or {@code null} when the condition is false
     * @throws SimulationError if the condition names a register that does not exist
     */
    private QuantumOperation resolveConditional(QuantumOperation operation) throws SimulationError {
        if (!(operation instanceof ConditionalOperation)) {
            return operation;
        }
        ConditionalOperation conditional = (ConditionalOperation) operation;
        ClassicalRegister register = registers.get(conditional.getRegisterName());
        if (register == null) {
            throw new SimulationError(
                "Unknown classical register: " + conditional.getRegisterName());
        }
        return conditional.test(register.read()) ? conditional.getGuardedOperation() : null;
    }
    
    /**
     * Writes a measurement outcome into its classical register.
     *
     * <p>The value written is the reported outcome, after any readout confusion, because a
     * classical register models what the device read out rather than what the state
     * collapsed to. The collapse itself always uses the true outcome.</p>
     */
    private void recordClassicalOutcome(
            com.mechalikh.qforge.measurement.MeasurementOperator measurement,
            com.mechalikh.qforge.measurement.MeasurementOperator.MeasurementOutcome outcome)
            throws SimulationError {
        String registerName = measurement.getRegisterName();
        if (registerName == null) {
            return;
        }
        ClassicalRegister register = registers.get(registerName);
        if (register == null) {
            throw new SimulationError("Unknown classical register: " + registerName);
        }
        int reported = outcome.getResult().getMostFrequentOutcome();
        if (reported < 0) {
            reported = outcome.getOutcome();
        }
        register.write(measurement.getRegisterOffset(), reported,
            measurement.getTargetQubits().length);
    }
    
    /**
     * Records the logical time at which the next operation executes.
     *
     * <p>Events call this before applying their operation so that idle relaxation is computed
     * from the scheduled time rather than from wall clock time. A direct
     * {@link #applyOperation(QuantumOperation)} call without this hint uses
     * {@link Instant#now()}.</p>
     *
     * @param time logical time of the next operation
     */
    public void advanceTo(Instant time) {
        this.pendingEventTime = time;
    }
    
    private double elapsedNanos(Instant operationTime) {
        if (lastOperationTime == null || operationTime == null) {
            return 0.0;
        }
        long nanos = java.time.Duration.between(lastOperationTime, operationTime).toNanos();
        return Math.max(0.0, (double) nanos);
    }
    
    private QuantumState applyIdleNoise(QuantumState state, Qubit[] activeQubits,
                                        double elapsedNanos) throws SimulationError {
        DeviceNoiseModel device = config.getDeviceNoise();
        if (device == null || !device.hasRelaxation() || elapsedNanos <= 0.0) {
            return state;
        }
        Set<Integer> active = indices(activeQubits);
        QuantumState result = state;
        for (Qubit qubit : qubits) {
            if (active.contains(qubit.getIndex())) {
                continue;
            }
            NoiseModel relaxation = device.relaxationFor(qubit.getIndex(), elapsedNanos);
            if (relaxation != null) {
                Qubit[] target = {qubit};
                result = strategy.applyNoise(result, relaxation.withTargetQubits(target), target);
            }
        }
        return result;
    }
    
    private QuantumState applyCrosstalk(QuantumState state, QuantumGate gate)
            throws SimulationError {
        DeviceNoiseModel device = config.getDeviceNoise();
        if (device == null || device.getCrosstalkRate() <= 0.0) {
            return state;
        }
        Set<Integer> active = indices(gate.getTargetQubits());
        active.addAll(indices(gate.getControlQubits()));
        Set<Integer> neighbours = new LinkedHashSet<>();
        for (int qubit : active) {
            if (qubit > 0) {
                neighbours.add(qubit - 1);
            }
            if (qubit + 1 < qubits.size()) {
                neighbours.add(qubit + 1);
            }
        }
        neighbours.removeAll(active);
        
        QuantumState result = state;
        NoiseModel crosstalk = device.crosstalkForIdle();
        if (crosstalk == null) {
            return result;
        }
        for (int index : neighbours) {
            Qubit[] target = {qubits.get(index)};
            result = strategy.applyNoise(result, crosstalk.withTargetQubits(target), target);
        }
        return result;
    }
    
    private static Set<Integer> indices(Qubit[] qubits) {
        Set<Integer> result = new LinkedHashSet<>();
        for (Qubit qubit : qubits) {
            result.add(qubit.getIndex());
        }
        return result;
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
        for (ClassicalRegister register : registers.values()) {
            register.reset();
        }
        stepsExecuted = 0;
        stopRequested = false;
        simulationStartTime = Instant.now();
        lastOperationTime = simulationStartTime;
        pendingEventTime = null;
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
     * Returns the classical registers of this context, in declaration order.
     *
     * @return a new list of live registers; the register objects are not copies
     */
    public List<ClassicalRegister> getRegisters() {
        return new ArrayList<>(registers.values());
    }
    
    /**
     * Returns one classical register.
     *
     * @param name register name
     * @return the register, or {@code null} when no register has that name
     */
    public ClassicalRegister getRegister(String name) {
        return registers.get(name);
    }
    
    /**
     * Returns a snapshot of the register values.
     *
     * <p>Each value packs the register bits so that register bit {@code i} is bit {@code i}
     * of the returned integer, matching {@link ClassicalRegister#read()}.</p>
     *
     * @return a new map from register name to packed value
     */
    public Map<String, Long> getRegisterValues() {
        Map<String, Long> values = new LinkedHashMap<>();
        for (Map.Entry<String, ClassicalRegister> entry : registers.entrySet()) {
            values.put(entry.getKey(), entry.getValue().read());
        }
        return values;
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
package com.quantum.simulator.core;

import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.strategy.SimulationStrategy;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.noise.ErrorManager;
import com.quantum.simulator.noise.NoiseModel;
import com.quantum.simulator.noise.NoiseProfile;
import com.quantum.simulator.observer.MetricsCollector;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.operations.QuantumOperation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
    
    public void initializeState() throws SimulationError {
        if (strategy == null) {
            throw new SimulationError("Simulation strategy not set");
        }
        
        this.currentState = strategy.createInitialState(config.getNumQubits());
        this.simulationStartTime = Instant.now();
        this.metricsCollector.recordStartTime(simulationStartTime);
    }
    
    public void updateState(QuantumState newState) {
        QuantumState oldState = this.currentState;
        this.currentState = newState;
        metricsCollector.recordStateTransition(oldState, newState);
    }
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
            
        } else if (operation instanceof com.quantum.simulator.noise.NoiseModel) {
            com.quantum.simulator.noise.NoiseModel noise = 
                (com.quantum.simulator.noise.NoiseModel) operation;
            
            // Use the strategy to apply noise
            newState = strategy.applyNoise(currentState, noise, noise.getTargetQubits());
            
        } else if (operation instanceof com.quantum.simulator.measurement.MeasurementOperator) {
            com.quantum.simulator.measurement.MeasurementOperator measurement = 
                (com.quantum.simulator.measurement.MeasurementOperator) operation;
            
            com.quantum.simulator.measurement.MeasurementOperator.MeasurementOutcome outcome =
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
    
    public void addMeasurementResult(MeasurementResult result) {
        measurements.add(result);
        metricsCollector.recordMeasurement(result);
    }
    
    public void putData(String key, Object value) {
        contextData.put(key, value);
    }
    
    public Object getData(String key) {
        return contextData.get(key);
    }
    
    public void requestStop() {
        this.stopRequested = true;
    }
    
    public boolean shouldStop() {
        return stopRequested || 
               (config.getMaxSteps() > 0 && stepsExecuted >= config.getMaxSteps());
    }
    
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
    
    public SimulationConfig getConfig() {
        return config;
    }
    
    public SimulationStrategy getStrategy() {
        return strategy;
    }
    
    public void setStrategy(SimulationStrategy strategy) {
        this.strategy = strategy;
    }
    
    public QuantumState getCurrentState() {
        return currentState;
    }
    
    public List<Qubit> getQubits() {
        return new ArrayList<>(qubits);
    }
    
    public Qubit getQubit(int index) {
        if (index < 0 || index >= qubits.size()) {
            throw new IndexOutOfBoundsException("Qubit index out of bounds: " + index);
        }
        return qubits.get(index);
    }
    
    public ErrorManager getErrorManager() {
        return errorManager;
    }
    
    public MetricsCollector getMetrics() {
        return metricsCollector;
    }
    
    public List<MeasurementResult> getMeasurements() {
        return new ArrayList<>(measurements);
    }
    
    public long getStepsExecuted() {
        return stepsExecuted;
    }
    
    public Instant getSimulationStartTime() {
        return simulationStartTime;
    }
    
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